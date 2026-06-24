package io.phasetwo.keycloak.redis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.extern.jbosslog.JBossLog;
import redis.clients.jedis.util.JedisClusterCRC16;

/**
 * Assembles a Lua script dynamically from the write and delete scenarios of a single commit, so an
 * entire transaction can be applied with one {@code EVAL} (per hash slot in cluster mode) instead
 * of a round-trip per entity.
 *
 * <p>Only {@code KEYS[n]} / {@code ARGV[n]} numeric references and command names are baked into the
 * script text; every dynamic string (keys, fields, values, versions, index members) flows through
 * KEYS/ARGV, so there is no injection or escaping risk. The create/CAS rules mirror the previous
 * {@code RedisHashCas} script: a {@code version} of {@code "0"} is the create path and nulls map to
 * {@link MapEntity#NULL_SENTINEL}. Key expiration is not handled here.
 */
@JBossLog
public final class LuaCommitScriptBuilder<K extends Key, A extends MapEntity<K>> {

  private final Consumer<String> opCounter;

  public LuaCommitScriptBuilder(Consumer<String> opCounter) {
    this.opCounter = opCounter;
  }

  /**
   * A rendered script plus the entities it covers (needed to rebase and rebuild on CAS conflict).
   */
  public static final class BuiltScript<K extends Key, A extends MapEntity<K>> {
    private final String lua;
    private final List<String> keys;
    private final List<String> args;
    private final List<A> writeEntities;
    private final List<A> deleteEntities;
    private final Integer foldSlot;

    private BuiltScript(
        String lua,
        List<String> keys,
        List<String> args,
        List<A> writeEntities,
        List<A> deleteEntities,
        Integer foldSlot) {
      this.lua = lua;
      this.keys = keys;
      this.args = args;
      this.writeEntities = writeEntities;
      this.deleteEntities = deleteEntities;
      this.foldSlot = foldSlot;
    }

    public String lua() {
      return lua;
    }

    public List<String> keys() {
      return keys;
    }

    public List<String> args() {
      return args;
    }

    public List<A> writeEntities() {
      return writeEntities;
    }

    public List<A> deleteEntities() {
      return deleteEntities;
    }

    public Integer foldSlot() {
      return foldSlot;
    }
  }

  /**
   * The two passes of a cluster commit: per-slot commit scripts and the cross-slot index scripts.
   */
  public static final class SlotScripts<K extends Key, A extends MapEntity<K>> {
    private final List<BuiltScript<K, A>> commitScripts;
    private final List<BuiltScript<K, A>> indexScripts;

    private SlotScripts(
        List<BuiltScript<K, A>> commitScripts, List<BuiltScript<K, A>> indexScripts) {
      this.commitScripts = commitScripts;
      this.indexScripts = indexScripts;
    }

    public List<BuiltScript<K, A>> commitScripts() {
      return commitScripts;
    }

    public List<BuiltScript<K, A>> indexScripts() {
      return indexScripts;
    }
  }

  /** Standalone/sentinel: a single all-or-nothing script for the whole commit. */
  public BuiltScript<K, A> buildSingle(List<A> writes, List<A> deletes) {
    return render(writes, deletes, null);
  }

  /** Cluster: per-slot commit scripts plus per-slot scripts for cross-slot index members. */
  public SlotScripts<K, A> buildPerSlot(List<A> writes, List<A> deletes) {
    Map<Integer, List<A>> writesBySlot = new LinkedHashMap<>();
    Map<Integer, List<A>> deletesBySlot = new LinkedHashMap<>();
    for (A w : writes) {
      writesBySlot.computeIfAbsent(slot(w.getKey().key()), s -> new ArrayList<>()).add(w);
    }
    for (A d : deletes) {
      deletesBySlot.computeIfAbsent(slot(d.getKey().key()), s -> new ArrayList<>()).add(d);
    }

    Set<Integer> commitSlots = new LinkedHashSet<>();
    commitSlots.addAll(writesBySlot.keySet());
    commitSlots.addAll(deletesBySlot.keySet());
    List<BuiltScript<K, A>> commitScripts = new ArrayList<>();
    for (Integer s : commitSlots) {
      commitScripts.add(
          render(
              writesBySlot.getOrDefault(s, List.of()),
              deletesBySlot.getOrDefault(s, List.of()),
              s));
    }

    // index members whose key lives in a different slot than the entity run as a second pass
    Map<Integer, List<IndexCall>> sadds = new LinkedHashMap<>();
    Map<Integer, List<IndexCall>> srems = new LinkedHashMap<>();
    for (A w : writes) {
      int es = slot(w.getKey().key());
      for (Map.Entry<String, String> idx : validIndexes(w)) {
        int is = slot(idx.getKey());
        if (is != es) {
          sadds
              .computeIfAbsent(is, x -> new ArrayList<>())
              .add(new IndexCall(idx.getKey(), idx.getValue()));
        }
      }
    }
    for (A d : deletes) {
      int es = slot(d.getKey().key());
      for (Map.Entry<String, String> idx : validIndexes(d)) {
        int is = slot(idx.getKey());
        if (is != es) {
          srems
              .computeIfAbsent(is, x -> new ArrayList<>())
              .add(new IndexCall(idx.getKey(), idx.getValue()));
        }
      }
    }

    Set<Integer> indexSlots = new LinkedHashSet<>();
    indexSlots.addAll(sadds.keySet());
    indexSlots.addAll(srems.keySet());
    List<BuiltScript<K, A>> indexScripts = new ArrayList<>();
    for (Integer s : indexSlots) {
      indexScripts.add(
          renderIndexScript(sadds.getOrDefault(s, List.of()), srems.getOrDefault(s, List.of())));
    }

    return new SlotScripts<>(commitScripts, indexScripts);
  }

  /**
   * Renders a single all-or-nothing CAS script covering the given writes and deletes.
   *
   * @param foldSlot when non-null (cluster) only index members whose key maps to this slot are
   *     folded into the script; when null (standalone) every index member is included.
   */
  public BuiltScript<K, A> render(List<A> writes, List<A> deletes, Integer foldSlot) {
    List<String> keys = new ArrayList<>();
    Map<String, Integer> keyIdx = new LinkedHashMap<>();
    List<String> args = new ArrayList<>();
    StringBuilder lua = new StringBuilder();

    lua.append("local conflicts = {}\n");

    // pass 1: version verification for every write, before anything is applied
    int[] writeKeyRef = new int[writes.size()];
    for (int i = 0; i < writes.size(); i++) {
      A w = writes.get(i);
      int k = keyRef(keys, keyIdx, w.getKey().key());
      writeKeyRef[i] = k;
      args.add(String.valueOf(w.getVersion()));
      int ve = args.size();
      lua.append("do local cur = redis.call('HGET', KEYS[").append(k).append("], 'version')\n");
      lua.append("if not cur then if ARGV[")
          .append(ve)
          .append("] ~= '0' then conflicts[#conflicts+1] = KEYS[")
          .append(k)
          .append("] end\n");
      lua.append("elseif cur ~= ARGV[")
          .append(ve)
          .append("] then conflicts[#conflicts+1] = KEYS[")
          .append(k)
          .append("] end end\n");
    }
    if (!writes.isEmpty()) {
      lua.append("if #conflicts > 0 then return conflicts end\n");
    }

    // pass 2: apply every write
    for (int i = 0; i < writes.size(); i++) {
      A w = writes.get(i);
      int k = writeKeyRef[i];
      opCounter.accept(RedisChangelogTransaction.HSETEX);

      Map<String, String> dirty = w.getDirtyFields();
      if (!dirty.isEmpty()) {
        int start = args.size() + 1;
        for (Map.Entry<String, String> e : dirty.entrySet()) {
          args.add(e.getKey());
          args.add(e.getValue() == null ? MapEntity.NULL_SENTINEL : e.getValue());
        }
        int end = args.size();
        lua.append("redis.call('HSET', KEYS[")
            .append(k)
            .append("], unpack(ARGV,")
            .append(start)
            .append(",")
            .append(end)
            .append("))\n");
      }

      Set<String> deletedFields = w.getDeletedFields();
      if (!deletedFields.isEmpty()) {
        int start = args.size() + 1;
        for (String f : deletedFields) {
          args.add(f);
        }
        int end = args.size();
        lua.append("redis.call('HDEL', KEYS[")
            .append(k)
            .append("], unpack(ARGV,")
            .append(start)
            .append(",")
            .append(end)
            .append("))\n");
        opCounter.accept(RedisChangelogTransaction.HDEL);
      }

      lua.append("redis.call('HINCRBY', KEYS[").append(k).append("], 'version', 1)\n");
    }

    // unconditional deletes (matches the previous deleteEntity)
    for (A d : deletes) {
      int k = keyRef(keys, keyIdx, d.getKey().key());
      lua.append("redis.call('DEL', KEYS[").append(k).append("])\n");
      opCounter.accept(RedisChangelogTransaction.DEL);
    }

    // secondary indexes, in the same script (all in standalone; same-slot only in cluster)
    for (A w : writes) {
      for (Map.Entry<String, String> idx : validIndexes(w)) {
        if (includeIndex(idx.getKey(), foldSlot)) {
          int k = keyRef(keys, keyIdx, idx.getKey());
          args.add(idx.getValue());
          int m = args.size();
          lua.append("redis.call('SADD', KEYS[")
              .append(k)
              .append("], ARGV[")
              .append(m)
              .append("])\n");
          opCounter.accept(RedisChangelogTransaction.SADD);
        }
      }
    }
    for (A d : deletes) {
      for (Map.Entry<String, String> idx : validIndexes(d)) {
        if (includeIndex(idx.getKey(), foldSlot)) {
          int k = keyRef(keys, keyIdx, idx.getKey());
          args.add(idx.getValue());
          int m = args.size();
          lua.append("redis.call('SREM', KEYS[")
              .append(k)
              .append("], ARGV[")
              .append(m)
              .append("])\n");
          opCounter.accept(RedisChangelogTransaction.SREM);
        }
      }
    }

    lua.append("return conflicts\n");

    return new BuiltScript<>(lua.toString(), keys, args, writes, deletes, foldSlot);
  }

  private BuiltScript<K, A> renderIndexScript(List<IndexCall> sadds, List<IndexCall> srems) {
    List<String> keys = new ArrayList<>();
    Map<String, Integer> keyIdx = new LinkedHashMap<>();
    List<String> args = new ArrayList<>();
    StringBuilder lua = new StringBuilder();

    for (IndexCall c : sadds) {
      int k = keyRef(keys, keyIdx, c.key);
      args.add(c.member);
      int m = args.size();
      lua.append("redis.call('SADD', KEYS[").append(k).append("], ARGV[").append(m).append("])\n");
      opCounter.accept(RedisChangelogTransaction.SADD);
    }
    for (IndexCall c : srems) {
      int k = keyRef(keys, keyIdx, c.key);
      args.add(c.member);
      int m = args.size();
      lua.append("redis.call('SREM', KEYS[").append(k).append("], ARGV[").append(m).append("])\n");
      opCounter.accept(RedisChangelogTransaction.SREM);
    }
    lua.append("return 0\n");

    return new BuiltScript<>(lua.toString(), keys, args, List.of(), List.of(), null);
  }

  private boolean includeIndex(String indexKey, Integer foldSlot) {
    return foldSlot == null || slot(indexKey) == foldSlot;
  }

  private List<Map.Entry<String, String>> validIndexes(A entity) {
    List<Map.Entry<String, String>> out = new ArrayList<>();
    for (Map.Entry<String, String> e : entity.getSecondaryIndexes().entrySet()) {
      if (e.getKey() != null && e.getValue() != null) {
        out.add(e);
      }
    }
    return out;
  }

  private static int keyRef(List<String> keys, Map<String, Integer> keyIdx, String key) {
    Integer i = keyIdx.get(key);
    if (i == null) {
      keys.add(key);
      i = keys.size();
      keyIdx.put(key, i);
    }
    return i;
  }

  private static int slot(String key) {
    return JedisClusterCRC16.getSlot(key);
  }

  private static final class IndexCall {
    private final String key;
    private final String member;

    private IndexCall(String key, String member) {
      this.key = key;
      this.member = member;
    }
  }
}
