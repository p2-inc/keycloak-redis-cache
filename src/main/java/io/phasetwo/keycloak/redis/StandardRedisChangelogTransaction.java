package io.phasetwo.keycloak.redis;

import io.phasetwo.keycloak.common.ExpirableEntity;
import redis.clients.jedis.UnifiedJedis;

/** Lua-based CAS with MULTI/EXEC index updates. Used for STANDALONE and SENTINEL modes. */
public class StandardRedisChangelogTransaction<K extends Key, A extends MapEntity<K>>
    extends RedisChangelogTransaction<K, A> {

  StandardRedisChangelogTransaction(
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
    return true;
  }
}
