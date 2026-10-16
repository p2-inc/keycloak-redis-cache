> :bug: **This is alpha software** Do not use this in production.

# Keycloak Redis cache 

<!-- phasetwo:banner:start - managed by p2-inc/shared-github-actions, edit there -->
[![license](https://img.shields.io/badge/license-ELv2-blue)](COPYING) [![docs](https://img.shields.io/badge/docs-phasetwo.io-blue)](https://phasetwo.io/docs/introduction/)

📚 [Documentation](https://phasetwo.io/docs/introduction/) • 🚀 [Hosted Keycloak](https://dash.phasetwo.io/?utm_source=github&utm_medium=readme&utm_campaign=keycloak-redis-cache) • 🤖 [Keycloak Skills](https://github.com/p2-inc/keycloak-skills) • 💬 [Support](mailto:support@phasetwo.io)

> [!TIP]
> **Run this in our cloud, no setup needed.** Phase Two hosts Keycloak with this extension and the rest of ours built in. [Try it free with your own cluster →](https://dash.phasetwo.io/?utm_source=github&utm_medium=readme&utm_campaign=keycloak-redis-cache)
>
> **Using a coding agent?** Add the Phase Two Keycloak skills ([launch post](https://phasetwo.io/blog/keycloak-skills-mcp-launch)) to Claude Code, Cursor or Codex so your agent can set up passwordless login, SSO, organizations and clusters for you:
>
> ```sh
> # Install the skills (connects to our hosted MCP server)
> claude plugin marketplace add p2-inc/keycloak-skills
> claude plugin install phasetwo@keycloak-skills
> # Or connect to the MCP server directly
> claude mcp add --transport http keycloak https://mcp.phasetwo.io/mcp
> ```
<!-- phasetwo:banner:end -->

Uses [Redis](https://redis.io/) or [Valkey](https://valkey.io/) instead of [Infinispan](https://infinispan.org/) for distributed caches. Overrides the [`DatastoreProvider`](https://www.keycloak.org/docs-api/latest/javadocs/org/keycloak/storage/DatastoreProvider.html).

Requires [Keycloak](https://keycloak.org) >= `26.7`, with the `stateless` preview feature enabled.

Heavily inspired by [keycloak-cassandra-extension](https://github.com/opdt/keycloak-cassandra-extension). Enormous thanks to these amazing engineers. Also a big thanks to the the creators of the "map-store". That project was overly ambitious, but yielded some great decisions that made this possible. Thanks for keeping the `DatastoreProvider`.

@rtufisi and @xgp presented this work at [Keycloak DevDay 2026](https://www.keycloak-day.dev/) in a talked titled [Replacing Keycloak's Infinispan Caches with Redis/Valkey](https://docs.google.com/presentation/d/1rPwE4VQYs8Ji303M8da14rbh04c3ZN-vfK5YUIBNmHM/edit?usp=sharing).

## Why

Put simply, the largest operational problem we have with Keycloak among all of our customers is Infinispan. Most have said that if they understood the complexities that Infinispan brings with it, and the operational costs, they never would have chosen Keycloak.

Furthermore, we've found that running Keycloak at medium to high scale requires, at minimum, using external Infinispan. An Infinispan cluster is hard to operate, even with all of the awesome operators and tools that team has built. And, this configuration for Keycloak and Infinispan is poorly documented and highly complex to get right. Furthermore, upgrading Infinispan itself is a daunting and error-prone task.

Most customers are already using Redis or Valkey, or one of the many cloud provider managed solutions. I once asked an interview question that went something like, "Can you suggest the best infrastructure choices to solve ...?". A particularly wise candidate replied, "The infrastructure you're already running".

This extension attempts to solve the problems of:
- Failed restarts/updates of Keycloak with embedded Infinispan because of JGroups incompatibility or other shennanigans.
- Slow startup because of JGroups/Infinispan discovery and rebalance. 
- Split brain because of network partitions or other Infinispan unknowns.
- Use of managed cached backends such as AWS ElastiCache.
- No downtime upgrades.
  
We've also been working on a multi-region, active-active story for Keycloak since we ported it to run on [CockroachDB](https://quay.io/repository/phasetwo/keycloak-crdb). We think that a combination of that and a flexible cache replacement like Redis could be a great solution.

## How to use

### Setup

Applies to any deployment type:

- Enable the `stateless` feature: `KC_FEATURES=stateless` (or `--features=stateless`)
- Select the Redis datastore: `KC_SPI_DATASTORE_PROVIDER=redis` (or `--spi-datastore--provider=redis`)
- Set `KC_CACHE=local`

Selecting the datastore is what activates the extension. Leaving it unselected keeps the jar on the classpath but dormant, so it never shadows a deployment that did not ask for it.

`stateless` is a Keycloak preview feature added in 26.7. It moves authentication sessions, action tokens and brute-force counters out of Infinispan, which is what lets this extension drop the Infinispan overrides it used to need. The extension refuses to start without it.

When the Redis datastore is selected, the extension disables Keycloak's authorization cache for you (it reaches for the Infinispan store factory directly, which this extension does not provide). The realm cache is deliberately left enabled — it is node-local and its invalidations travel over the Redis `PUBSUB` `ClusterProvider`.

> :warning: **Upgrading from a pre-26.7 version:** `KC_COMMUNITY_REDIS_CACHE_ENABLED=true` still works as a deprecated alias for the datastore selection and logs a warning. It will be removed in a future release.

### Configuration properties

| Property | Description | Example |
| --- | --- | --- |
| `KC_FEATURES` | Must include `stateless` (required). | `stateless` |
| `KC_SPI_DATASTORE_PROVIDER` | Select this extension's datastore (required). | `redis` |
| `KC_COMMUNITY_REDIS_CACHE_ENABLED` | Deprecated alias for `KC_SPI_DATASTORE_PROVIDER=redis`. | `true` |
| `KC_SPI_REDIS_CONNECTION_DEFAULT_MODE` | Redis topology mode: `standalone`, `sentinel`, or `cluster`. | `standalone` |
| `KC_SPI_REDIS_CONNECTION_DEFAULT_NODES` | Comma-delimited list of `host:port` nodes. For sentinel mode, these are the sentinel instances. | `redis-1:6379,redis-2:6379` |
| `KC_SPI_REDIS_CONNECTION_DEFAULT_MASTER_NAME` | Sentinel master name (required when mode is `sentinel`). | `mymaster` |
| `KC_SPI_REDIS_CONNECTION_DEFAULT_SSL` | If it is an SSL connection. | `false` |
| `KC_SPI_REDIS_CONNECTION_DEFAULT_USERNAME` | Redis username (if required). | `someuser` |
| `KC_SPI_REDIS_CONNECTION_DEFAULT_PASSWORD` | Redis password (if required). | `passw0rd` |
| `KC_SPI_REDIS_CONNECTION_DEFAULT_TIMEOUT` | Connection/socket timeout (e.g. `2000`, `2s`, `500ms`). | `2s` |

Examples:

```bash
KC_CACHE=local
KC_FEATURES=stateless
KC_SPI_DATASTORE_PROVIDER=redis
# Standalone
KC_SPI_REDIS_CONNECTION_DEFAULT_MODE=standalone
KC_SPI_REDIS_CONNECTION_DEFAULT_NODES=redis:6379
# Sentinel
KC_SPI_REDIS_CONNECTION_DEFAULT_MODE=sentinel
KC_SPI_REDIS_CONNECTION_DEFAULT_NODES=sentinel-1:26379,sentinel-2:26379,sentinel-3:26379
KC_SPI_REDIS_CONNECTION_DEFAULT_MASTER_NAME=mymaster
# Cluster
KC_SPI_REDIS_CONNECTION_DEFAULT_MODE=cluster
KC_SPI_REDIS_CONNECTION_DEFAULT_NODES=redis-1:6379,redis-2:6379,redis-3:6379
```

### Build and install

- Build the jar with `mvn clean install -DskipTests`
- Put the `target/keycloak-redis-cache-<version>.jar` in your Keycloak `providers/` directory

### Docker

You can currently try after building with the included `docker-compose.yml` file. Just run `docker compose up` after building the extension with Maven. There are a few permutations of docker-compose files that are used for testing different Keycloak and Redis topologies.

A pre-built docker image is also available at (https://quay.io/repository/phasetwo/keycloak-redis).

## Details and known issues

- Local caches (e.g. `user`, `realm`, etc.) still use Infinispan internally. Only the distributed caches are replaced. Under `stateless` Keycloak removes its own clustered caches, so embedded Infinispan is reduced to node-local caches.
- ~~We use a job to expire entries rather than using Redis native TTL. This is because we want it to work with implementations that don't support multi-region expiration (e.g. AWS MemoryDB).~~
- No migration of existing sessions is done.
- We store both normal and "offline" sessions in the cache. No database persistence is used.
- We don't store revoked tokens in the database. Keycloak 26.7 split them out into a `RevokedTokenProvider` SPI whose only built-in `stateless` implementation is JPA; we serve it from Redis instead, so we still suggest you use Redis persistence.
- `ClusterProvider` implementation uses Redis `PUBSUB`. In the future, we need to add SNS (AWS) or Pub/Sub (GCP) for multi-region.
- Keycloak Authorization probably won't work (Keycloak tries to use `InfinispanStoreFactory` direclty in a lot of places). Selecting the Redis datastore disables the authorization cache automatically.
- Some tests are still skipped or failing. We need to understand if this is because the test fails to do everything in a single transaction (Keycloak doesn't do this internally) or if there is something we are missing.
- ~~Hasn't been benchmarked to look for issues under load.~~
- ~~You should probably enable sticky sessions on your load balancer, although we need to substantiate this with testing.~~
- Our CAS retry behavior is naive, rebasing the entity on version mismatch. This rebase is field-level, not operation-level. It preserves partial note/map edits, but it does not recreate logical operations like “increment from latest value” for things such as incrementFailures(). We should look at how the Keycloak [`Updater`](https://www.keycloak.org/docs-api/latest/javadocs/org/keycloak/models/sessions/infinispan/changes/remote/updater/Updater.html) works, and do something similar in a per-entity Lua function.

### Index consistency and self-healing

Session entities (`user-session:*`, `authenticated-client:*`) are hashes, and each is referenced by one or more **secondary-index Sets** (`user-session:user-index:*`, `authenticated-client:client-index:*`, `…:parent-index:*`, broker indexes, etc.). A session belongs to several indexes at once, and in Redis Cluster those keys hash to different slots — so a single `MULTI`/`EXEC` (or Lua) touching a session hash **and** all its index members is impossible (it would raise `CROSSSLOT`), and hash tags cannot co-locate an index with every member it fans in to. Full multi-key atomicity is therefore unavailable in cluster mode.

Rather than fight this, the extension adopts the **same eventual-consistency contract Keycloak's own Infinispan caches already provide**. The index Sets are treated as a self-healing hint, not a source of truth. This is not a cluster-only concern: the leak below is driven by native-TTL expiry, which never runs the explicit delete path in *any* mode, so the self-healing applies everywhere. `redisMode` only decides **how** index writes are issued — batched in a `MULTI`/`EXEC` for standalone/sentinel, per-key for cluster — never **whether** they happen.

- **Entity-before-index on write, index-after-delete.** The session hash is written (single-key CAS, cluster-safe) before its index members are added; on delete the hash is removed before the members. A reader may briefly see an index member whose entity is not yet visible, or vice-versa — never a corrupt entity.
- **Reads drop dangling members and self-reconcile.** A by-index lookup ignores members whose entity has expired/vanished (they are filtered from the result) and registers each such member to be `SREM`'d from the Set it was read from **when the transaction commits** — never on the read path itself (no Redis writes outside `commitImpl`). Reads thus act as the garbage collector for stale index entries, in every mode.
- **TTL backstop on index Sets.** Because a session hash can expire via native TTL without going through the delete path, index writes give each index Set a TTL derived from the referencing session's expiration, applied grow-only (`PEXPIREAT … GT`, established with `NX`) so the Set always outlives its longest-lived member. This bounds index growth even for Sets that reads never revisit — and it matters on standalone too, where a set with no TTL leaves `volatile-lru` with no eviction candidates.
- **Bulk operations (session-limit enforcement, logout-all) are eventual and fail-open** in every mode.

Standalone and sentinel keep their **atomic `MULTI`/`EXEC` index maintenance** — the SADD, its TTL backstop, and any commit-time reap all ride inside the transaction; cluster issues the equivalent commands per-key. The all-mode behavior is verified by `RedisIndexBackstopAllModesTest` (TTL backstop present, and reconciliation deferred to commit rather than written on read) and the cluster wiring end-to-end by `RedisClusterIndexReconciliationTest`.

## Support

If you have been testing this and found an issue, please file an [issue](https://github.com/p2-inc/keycloak-redis-cache/issues) and a [pull request](https://github.com/p2-inc/keycloak-redis-cache/pulls) if you are able to identify and remediate the bug.

Commercial support for existing customers can be accessed through the normal support ticket system. Others with interest in commercial support for Keycloak, in the cloud or on-prem should contact [sales@phasetwo.io](mailto:sales@phasetwo.io).

-----

## Legal

### Copyright

Portions of the code are taken from [keycloak](https://github.com/keycloak/keycloak) and the [keycloak-cassandra-extension](https://github.com/opdt/keycloak-cassandra-extension) and those copyrights are held by their respective owners. 

No permission is granted to reproduce, scrape, text-and-data mine, use for training machine learning or generative AI systems, or supply as input/context to such systems, except with the express written consent of the copyright owner.

All other documentation, source code and other files in this repository are Copyright 2026 Phase Two, Inc.

### License

Currently the source code is licensed under the [Elastic License 2.0](COPYING), in line with the rest of our extensions. If there is a legitimate interest on the part of the Keycloak maintainer team to bring this into core, we are willing to modify the license to Apache License 2.0.

Licensee may not use the Licensed Material to train, fine-tune, or improve any AI or machine learning system, or as prompt/context, retrieval, or grounding material for such a system, without Licensor’s prior written consent.

If you are a commercial entity that would like to discuss alternate licensing, please contact us at [sales@phasetwo.io](mailto:sales@phasetwo.io).

### Contributing

All contributions submitted to this project by pull request become the property of Phase Two, Inc. By submitting a contribution, you represent and warrant that you have the legal right to make the contribution and that the contributed code and any accompanying documentation were created solely by you. Contributions produced with the assistance of AI coding agents or other automated tools will not be accepted.
