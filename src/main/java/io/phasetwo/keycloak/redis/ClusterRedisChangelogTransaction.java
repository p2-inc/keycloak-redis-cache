package io.phasetwo.keycloak.redis;

import io.phasetwo.keycloak.common.ExpirableEntity;
import redis.clients.jedis.UnifiedJedis;

/** Lua-based CAS without MULTI/EXEC (multi-key transactions unsupported in cluster mode). */
public class ClusterRedisChangelogTransaction<K extends Key, A extends MapEntity<K>>
    extends RedisChangelogTransaction<K, A> {

  ClusterRedisChangelogTransaction(
      String cacheName, UnifiedJedis jedis, AdapterSupplier<K, A> adapterSupplier) {
    super(cacheName, jedis, adapterSupplier);
  }

  @Override
  protected RedisHashCas.CasInvocation performCasWrite(A model) {
    Long expireAtMs = model instanceof ExpirableEntity ? ((ExpirableEntity) model).getExpiration() : null;
    return new RedisHashCas(jedis).hsetex(
        model.getKey().key(),
        model.getVersion(),
        expireAtMs,
        model.getDirtyFields(),
        model.getDeletedFields());
  }

  @Override
  protected boolean supportsAtomicWrites() {
    return false;
  }
}
