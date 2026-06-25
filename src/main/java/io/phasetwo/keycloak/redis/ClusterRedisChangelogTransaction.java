package io.phasetwo.keycloak.redis;

import io.phasetwo.keycloak.redis.connection.RedisMode;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;
import redis.clients.jedis.UnifiedJedis;

/**
 * Commit strategy for servers that enforce hash slots — a Redis Cluster, or AWS MemoryDB even when
 * reached over a single standalone endpoint. Operations are grouped by hash slot, since a cross-slot
 * {@code EVAL} is rejected with {@code CROSSSLOT}. Selected by {@link
 * RedisChangelogTransaction#create} for the slot-enforcing modes {@link
 * io.phasetwo.keycloak.redis.connection.RedisMode#CLUSTER} and {@link
 * io.phasetwo.keycloak.redis.connection.RedisMode#MEMORY_DB}.
 *
 * <p>Phase A runs one all-or-nothing CAS script per entity slot (with any same-slot index ops
 * folded in). Phase B runs the remaining index ops — those whose key maps to a different slot than
 * their entity — as one Lua script per index-key slot, after Phase A succeeds, so an {@code SADD}
 * only happens for a confirmed write. Atomicity is per-slot; a succeeded slot is never redone.
 */
@JBossLog
public class ClusterRedisChangelogTransaction<K extends Key, A extends MapEntity<K>>
    extends RedisChangelogTransaction<K, A> {

  ClusterRedisChangelogTransaction(
      String cacheName,
      UnifiedJedis jedis,
      RedisMode redisMode,
      AdapterSupplier<K, A> adapterSupplier) {
    super(cacheName, jedis, redisMode, adapterSupplier);
  }

  @Override
  protected void flushCommit(List<A> writes, List<A> deletes) {
    LuaCommitScriptBuilder.SlotScripts<K, A> scripts = scriptBuilder.buildPerSlot(writes, deletes);
    runWithRetries(scripts.commitScripts());
    evalAll(scripts.indexScripts());
  }
}
