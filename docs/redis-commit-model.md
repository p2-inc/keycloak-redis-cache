# Redis commit & atomicity model

How a transaction's pending writes/deletes get flushed to Redis, and exactly
what is and isn't atomic. Source of truth: `LuaCommitScriptBuilder`,
`RedisChangelogTransaction` and its two subclasses.

## Modes and which commit strategy each uses

The strategy is chosen by whether the **server** enforces hash slots — i.e.
rejects a cross-slot `EVAL` with `CROSSSLOT` — not by how the client connects.

| Mode | Connects via | Server enforces slots? | Strategy |
|------|--------------|------------------------|----------|
| `STANDALONE` / `SENTINEL` | single endpoint | no | `StandaloneRedisChangelogTransaction` → `buildSingle` |
| `CLUSTER` | cluster discovery | yes | `ClusterRedisChangelogTransaction` → `buildPerSlot` |
| `MEMORY_DB` | **single endpoint** (e.g. SSM tunnel) | **yes** | `ClusterRedisChangelogTransaction` → `buildPerSlot` |

`RedisChangelogTransaction.slotEnforced()` returns true for `CLUSTER` and
`MEMORY_DB`. MemoryDB connects like standalone but commits per-slot like cluster,
because the server still rejects cross-slot `EVAL` with `CROSSSLOT`.

## LuaCommitScriptBuilder

Assembles Lua script text from a commit's writes/deletes so the whole batch runs
server-side under one `EVAL` instead of a round-trip per entity. Only `KEYS[n]`
/ `ARGV[n]` references and command names are baked into the script string; every
dynamic value (keys, fields, values, versions, index members) flows through
KEYS/ARGV — so there's no injection/escaping risk.

- `buildSingle(writes, deletes)` → one all-or-nothing `BuiltScript` for the
  entire commit, every index member folded in (`foldSlot == null`).
- `buildPerSlot(writes, deletes)` → `SlotScripts`: a list of per-slot
  `commitScripts` plus a list of `indexScripts` for cross-slot index members.

### `render` (the CAS script)

1. **Verify-all-then-apply.** Pass 1 emits `HGET version` checks for every write,
   appends mismatches to a Lua `conflicts` table, and `return conflicts` **before
   any mutation** if non-empty. Expected version `"0"` = create path; nulls map
   to `MapEntity.NULL_SENTINEL`.
2. **Apply writes** — `HSET` dirty fields, `HDEL` deleted fields, `HINCRBY
   version 1`.
3. **Deletes** — unconditional `DEL`.
4. **Secondary indexes** — `SADD`/`SREM`. `foldSlot == null` (standalone) folds
   in *all* index members; non-null (cluster) folds in only members whose key
   hashes to that slot.

`slot()` = `JedisClusterCRC16.getSlot(key)` (CRC16 mod 16384), computed
client-side so the builder can group keys without asking the server.

## Atomicity in `buildPerSlot` — per-slot, NOT per-commit

There is **no single transaction spanning the whole flush.** A cluster `EVAL`
may legally touch only one hash slot, so the largest possible atomic unit *is*
one slot.

`ClusterRedisChangelogTransaction.flushCommit` runs two phases sequentially:

```java
runWithRetries(scripts.commitScripts()); // Phase A — one EVAL per entity slot
evalAll(scripts.indexScripts());         // Phase B — one EVAL per cross-slot index slot
```

**What is atomic:** each `BuiltScript` is one `EVAL`, and Redis runs a Lua script
atomically (no interleaving). So each per-slot commit script is all-or-nothing
*within its slot*.

**What is NOT atomic:**
- *Across slots in Phase A* — each slot is a separate `EVAL`. If slot X commits
  and slot Y then fails (or the JVM dies), slot X stays committed. No cross-slot
  rollback — `rollbackImpl` is a no-op.
- *Between Phase A and Phase B* — cross-slot index `SADD`/`SREM`s are plain,
  non-CAS scripts (`renderIndexScript` ends `return 0`). A crash between A and B
  leaves the entity written but its cross-slot index entry missing.

**How the design copes:**
1. **CAS-first ordering** — Phase B index ops run only after the Phase A CAS
   commits succeed, so an `SADD` never happens for a write that didn't commit.
   The failure mode is a *missing* index entry, never a dangling one.
2. **Per-slot retry without redo** — `runWithRetries` re-runs only the slots that
   reported a conflict; a conflicting script applies nothing and a succeeded
   script is dropped and never re-run, so an already-incremented version can't
   cause a false conflict on retry. Each conflicting slot rebases
   (`rebaseModel`) and retries up to `MAX_CAS_RETRIES`.

Standalone mode (`buildSingle`) gets whole-commit atomicity because the entire
commit is one `EVAL`. Cluster/MemoryDB trade that for per-slot atomicity — an
unavoidable consequence of the `CROSSSLOT` restriction, not a design choice. The
eventual-consistency gap (cross-slot index lag, partial multi-slot commits on
hard failure) is the price of running across slots.
