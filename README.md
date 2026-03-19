> :bug: **This is alpha software** Do not use this in production.

# Keycloak Redis cache 

Uses [Redis](https://redis.io/) or [Valkey](https://valkey.io/) instead of [Infinispan](https://infinispan.org/) for distributed caches. Overrides the [`DatastoreProvider`](https://www.keycloak.org/docs-api/latest/javadocs/org/keycloak/storage/DatastoreProvider.html).

Requires [Keycloak](https://keycloak.org) >= `26`.

Heavily inspired by [keycloak-cassandra-extension](https://github.com/opdt/keycloak-cassandra-extension). Enormous thanks to these amazing engineers. Also a big thanks to the the creators of the "map-store". That project was overly ambitious, but yielded some great decisions that made this possible. Thanks for keeping the `DatastoreProvider`.

@rtufisi and @xgp presented this work at [Keycloak DevDay 2026](https://www.keycloak-day.dev/) in a talked titled [Replacing Keycloak's Infinispan Caches with Redis/Valkey](https://docs.google.com/presentation/d/1rPwE4VQYs8Ji303M8da14rbh04c3ZN-vfK5YUIBNmHM/edit?usp=sharing).

## Why

Put simply, the largest operational problem we have with Keycloak among all of our customers is Infinispan. Most have said that if they understood the complexities that Infinispan brings with it, and the operational costs, they never would have chosen Keycloak.

Furthermore, we've found that running Keycloak at medium to high scale requires, at minimum, using external Infinispan. An Infinispan cluster is hard to operate, even with all of the awesome operators and tools that team has built. And, this configuration for Keycloak and Infinispan is poorly documented and highly complex to get right. Furthermore, upgrading Infinispan itself is a daunting and error-prone task.

This extension attempts to solve the problems of:
- Failed restarts/updates of Keycloak with embedded Infinispan because of JGroups incompatibility or other shennanigans.
- Slow startup because of JGroups/Infinispan discovery and rebalance. 

We've also been working on a multi-region, active-active story for Keycloak over the last 3 years since we ported it to run on [CockroachDB](https://quay.io/repository/phasetwo/keycloak-crdb). We think that a combination of that and a flexible cache replacement like Redis could be a great solution.

Most customers are already using Redis or Valkey, or one of the many cloud provider managed solutions. I once asked an interview question that went something like, "Can you suggest the best infrastructure choices to solve ...?". A particularly wise candidate replied, "The infrastructure you're already running".

## How to use

### Setup

Applies to any deployment type:

- Set `KC_COMMUNITY_REDIS_CACHE_ENABLED=true`
- Set `KC_CACHE=local`

### Configuration properties

| Property | Description | Example |
| --- | --- | --- |
| `KC_COMMUNITY_REDIS_CACHE_ENABLED` | Enable this extension (required). | `true` |
| `KC_SPI_REDIS_CONNECTION_DEFAULT_MODE` | Redis topology mode: `standalone` or `cluster`. | `standalone` |
| `KC_SPI_REDIS_CONNECTION_DEFAULT_NODES` | Comma-delimited list of `host:port` nodes. | `redis-1:6379,redis-2:6379` |
| `KC_SPI_REDIS_CONNECTION_DEFAULT_SSL` | If it is an SSL connection. | `false` |
| `KC_SPI_REDIS_CONNECTION_DEFAULT_USERNAME` | Redis username (if required). | `someuser` |
| `KC_SPI_REDIS_CONNECTION_DEFAULT_PASSWORD` | Redis password (if required). | `passw0rd` |
| `KC_SPI_REDIS_CONNECTION_DEFAULT_TIMEOUT` | Connection/socket timeout (e.g. `2000`, `2s`, `500ms`). | `2s` |

Examples:

```bash
KC_CACHE=local
KC_COMMUNITY_REDIS_CACHE_ENABLED=true
# Standalone
KC_SPI_REDIS_CONNECTION_DEFAULT_MODE=standalone
KC_SPI_REDIS_CONNECTION_DEFAULT_NODES=redis:6379
# Cluster
KC_SPI_REDIS_CONNECTION_DEFAULT_MODE=cluster
KC_SPI_REDIS_CONNECTION_DEFAULT_NODES=redis-1:6379,redis-2:6379,redis-3:6379
```

### Build and install

- Build the jar with `mvn clean install -DskipTests`
- Put the `target/keycloak-redis-cache-<version>.jar` in your Keycloak `providers/` directory

### Docker

You can currently try after building with the included `docker-compose.yml` file. Just run `docker compose up` after building the extension with Maven. There are a few permutations of docker-compose files that are used for testing different Keycloak and Redis topologies.

A pre-build docker image is also available at (https://quay.io/repository/phasetwo/keycloak-redis).

## Details and known issues

- Local caches (e.g. `user`, `realm`, etc.) still use Infinispan internally. Only the distributed caches are replaced.
- ~~We use a job to expire entries rather than using Redis native TTL. This is because we want it to work with implementations that don't support multi-region expiration (e.g. AWS MemoryDB).~~
- No migration of existing sessions is done.
- We store both normal and "offline" sessions in the cache. No database persistence is used.
- We don't store revoked tokens in the database. This is an implementation detail in the Keycloak default implementation we _might_ adopt, but for now we suggest you use Redis persistence.
- `ClusterProvider` implementation uses Redis `PUBSUB`. In the future, we need to add SNS (AWS) or Pub/Sub (GCP) for multi-region.
- Keycloak Authorization probably won't work (Keycloak tries to use `InfinispanStoreFactory` direclty in a lot of places).
- Some tests are still skipped or failing. We need to understand if this is because the test fails to do everything in a single transaction (Keycloak doesn't do this internally) or if there is something we are missing.
- ~~Hasn't been benchmarked to look for issues under load.~~
- ~~You should probably enable sticky sessions on your load balancer, although we need to substantiate this with testing.~~

-----

## Legal

### Copyright

Portions of the code are taken from [keycloak](https://github.com/keycloak/keycloak) and the [keycloak-cassandra-extension](https://github.com/opdt/keycloak-cassandra-extension) and those copyrights are held by their respective owners. 

All other documentation, source code and other files in this repository are Copyright 2026 Phase Two, Inc.

### License

Currently the source code is licensed under the [Elastic License 2.0](COPYING), in line with the rest of our extensions. If there is a legitimate interest on the part of the Keycloak maintainer team to bring this into core, we are willing to modify the license to Apache License 2.0.

If you are a commercial entity that would like to discuss alternate licensing, please contact us at [sales@phasetwo.io](mailto:sales@phasetwo.io).
