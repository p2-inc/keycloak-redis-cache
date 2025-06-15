> :bug: **This is alpha software** Do not use this in production.

# Keycloak Redis cache 

Uses [Redis](https://redis.io/) or [Valkey](https://valkey.io/) instead of [Infinispan](https://infinispan.org/) for distributed caches. Overrides the [`DatastoreProvider`](https://www.keycloak.org/docs-api/latest/javadocs/org/keycloak/storage/DatastoreProvider.html).

Requires [Keycloak](https://keycloak.org) >= `26`.

Heavily inspired by [keycloak-cassandra-extension](https://github.com/opdt/keycloak-cassandra-extension). Enormous thanks to these amazing engineers. Also a big thanks to the the creators of the "map-store". That project was overly ambitious, but yielded some great decisions that made this possible. Thanks for keeping the `DatastoreProvider`.

## Why

Put simply, the largest operational problem we have with Keycloak among all of our customers is Infinispan. Most have said that if they understood the complexities that Infinispan brings with it, and the operational costs, they never would have chosen Keycloak.

Furthermore, we've found that running Keycloak at medium to high scale requires, at minimum, using external Infinispan. An Infinispan cluster is hard to operate, even with all of the awesome operators and tools that team has built. And, this configuration for Keycloak and Infinispan is poorly documented and highly complex to get right. Furthermore, upgrading Infinispan itself is a daunting and error-prone task.

We've also been working on a multi-region, active-active story for Keycloak over the last 3 years since we ported it to run on [CockroachDB](https://quay.io/repository/phasetwo/keycloak-crdb). We think that a combination of that and a flexible cache replacement like Redis could be a great solution.

Most customers are already using Redis or Valkey, or one of the many cloud provider managed solutions. I once asked an interview question that went something like, "Can you suggest the best infrastructure choices to solve ...?". A particularly wise candidate replied, "The infrastructure you're already running".

## How to use

### Setup

Applies to any deployment type:

- Set `KC_COMMUNITY_REDIS_CACHE_ENABLED=true`
- Set `KC_SPI_REDIS_CONNECTION_DEFAULT_CONTACT_POINT: "redis"`
- Set `KC_SPI_REDIS_CONNECTION_DEFAULT_PORT: "6379"`
- Set `KC_CACHE=local`

### Build and install

- Build the jar with `mvn clean install -DskipTests`
- Put the `target/keycloak-redis-cache-<version>.jar` in your Keycloak `providers/` directory

### Docker

You can currently try after building with the included `docker-compose.yml` file. Just run `docker compose up`.

TODO build and publish a docker image so it's easier to try.

## Details and known issues

- Local caches (e.g. `user`, `realm`, etc.) still use Infinispan internally. Only the distributed caches are replaced.
- We use a job to expire entries rather than using Redis native TTL. This is because we want it to work with implementations that don't support multi-region expiration (e.g. AWS MemoryDB).
- No migration of existing sessions is done.
- We store both normal and "offline" sessions in the cache. No database persistence is used.
- `ClusterProvider` implementation still relies on the replicated `work` cache using Infinispan. This is used to propagate evictions to the Realm or User local caches between instances. TODO replace this with Redis `PUBSUB`, or SNS (AWS) or Pub/Sub (GCP) for multi-region. .
- Keycloak Authorization probably won't work (Keycloak tries to use `InfinispanStoreFactory` direclty in a lot of places).
- Some tests are still skipped or failing. We need to understand if this is because the test fails to do everything in a single transaction (Keycloak doesn't do this internally) or if there is something we are missing.
- Hasn't been benchmarked to look for issues under load.
- You should probably enable sticky sessions on your load balancer, although we need to substantiate this with testing.

-----

Portions of the code are taken from [keycloak](https://github.com/keycloak/keycloak) and the [keycloak-cassandra-extension](https://github.com/opdt/keycloak-cassandra-extension) and those copyrights are held by their respective owners. 

All other documentation, source code and other files in this repository are Copyright 2025 Phase Two, Inc., and are made available under the terms of the included [license](COPYING).
