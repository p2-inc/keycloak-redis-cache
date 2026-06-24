package io.phasetwo.keycloak.redis;

import io.phasetwo.keycloak.redis.connection.RedisMode;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;
import redis.clients.jedis.UnifiedJedis;

/**
 * Commit strategy for servers that do <strong>not</strong> enforce hash slots (a plain single-node
 * Redis, reached over a STANDALONE or SENTINEL connection): writes, deletes, and all secondary-index
 * add/remove operations execute in a single atomic Lua {@code eval}. Selected by {@link
 * RedisChangelogTransaction#create} for the non-slot-enforcing modes {@link
 * io.phasetwo.keycloak.redis.connection.RedisMode#STANDALONE} and {@link
 * io.phasetwo.keycloak.redis.connection.RedisMode#SENTINEL}; a slot-enforcing server (Cluster or
 * MemoryDB) would reject the cross-slot {@code eval} with {@code CROSSSLOT} and uses {@link
 * ClusterRedisChangelogTransaction} instead.
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
