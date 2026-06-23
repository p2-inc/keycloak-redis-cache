package io.phasetwo.keycloak.redis;

import io.phasetwo.keycloak.redis.connection.RedisMode;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;
import redis.clients.jedis.UnifiedJedis;

/**
 * Commit strategy for STANDALONE and SENTINEL modes: writes, deletes, and all secondary-index
 * add/remove operations execute in a single atomic Lua {@code eval}.
 */
@JBossLog
public class StandaloneRedisChangelogTransaction<K extends Key, A extends MapEntity<K>>
    extends RedisChangelogTransaction<K, A> {

  StandaloneRedisChangelogTransaction(
      String cacheName,
      UnifiedJedis jedis,
      RedisMode redisMode,
      AdapterSupplier<K, A> adapterSupplier) {
    super(cacheName, jedis, redisMode, adapterSupplier);
  }

  @Override
  protected void flushCommit(List<A> writes, List<A> deletes) {
    runWithRetries(List.of(scriptBuilder.buildSingle(writes, deletes)));
  }
}
