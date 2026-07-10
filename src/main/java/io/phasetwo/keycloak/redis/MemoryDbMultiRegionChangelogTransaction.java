package io.phasetwo.keycloak.redis;

import com.google.common.collect.Lists;
import io.phasetwo.keycloak.common.ExpirableEntity;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.jbosslog.JBossLog;
import redis.clients.jedis.AbstractTransaction;
import redis.clients.jedis.UnifiedJedis;

/**
 * WATCH + MULTI/EXEC based transaction for MemoryDB Multi-Region.
 *
 * <p>Lua scripting and PEXPIREAT are blocked in multi-region mode. This implementation uses:
 * <ul>
 *   <li>WATCH on all affected keys for local-region optimistic concurrency</li>
 *   <li>MULTI/EXEC to batch all writes atomically within a region</li>
 *   <li>HINCRBY for version tracking (no Lua, no PEXPIREAT)</li>
 *   <li>CRDT sub-key LWW to resolve cross-region conflicts</li>
 * </ul>
 * EXEC returns null when a watched key was modified concurrently; the whole batch is retried.
 */
@JBossLog
public class MemoryDbMultiRegionChangelogTransaction<K extends Key, A extends MapEntity<K>>
    extends RedisChangelogTransaction<K, A> {

  MemoryDbMultiRegionChangelogTransaction(
      String cacheName, UnifiedJedis jedis, AdapterSupplier<K, A> adapterSupplier) {
    super(cacheName, jedis, adapterSupplier);
  }

  @Override
  protected void commitImpl() {
    if (cache.isEmpty() && toDelete.isEmpty()) {
      log.trace("nothing to commit. skipping transaction...");
      return;
    }

    // Snapshot before the retry loop so we never mutate the collections mid-retry.
    List<A> cacheModels = Lists.newArrayList(cache.values());
    List<A> deleteModels = Lists.newArrayList(toDelete.values());

    // Collect all keys that need WATCH.
    Set<String> keysToWatch = new HashSet<>();
    for (A model : cacheModels) {
      if (!toDelete.containsKey(model.getKey())) {
        keysToWatch.add(model.getKey().key());
      }
    }
    for (A model : deleteModels) {
      keysToWatch.add(model.getKey().key());
    }

    String[] watchKeys = keysToWatch.toArray(new String[0]);

    for (int attempt = 0; attempt <= MAX_CAS_RETRIES; attempt++) {
      // transaction(false) gives a Transaction where we can call watch() before multi().
      try (AbstractTransaction txn = jedis.transaction(false)) {

        if (watchKeys.length > 0) {
          log.tracef("[redis] WATCH %s", Arrays.toString(watchKeys));
          txn.watch(watchKeys);
          countOperation(WATCH);
        }

        txn.multi();

        for (A model : cacheModels) {
          String key = model.getKey().key();

          if (model.isMarkedForDelete() || toDelete.containsKey(model.getKey())) {
            queueDelete(txn, key, model);
          } else if (model.isDirty()) {
            queueWrite(txn, key, model);
          }
        }

        for (A model : deleteModels) {
          String key = model.getKey().key();
          queueDelete(txn, key, model);
        }

        log.tracef("[redis] EXEC (attempt %d)", attempt + 1);
        List<Object> results = txn.exec();

        if (results != null) {
          return;
        }

        log.warnf(
            "[redis] WATCH triggered abort (attempt %d/%d) — retrying",
            attempt + 1, MAX_CAS_RETRIES + 1);

        if (attempt == MAX_CAS_RETRIES) {
          throw new IllegalStateException(
              "Redis WATCH+MULTI/EXEC aborted after "
                  + (attempt + 1)
                  + " attempts due to concurrent modification on keys "
                  + Arrays.toString(watchKeys));
        }
      }
    }
  }

  private void queueWrite(AbstractTransaction txn, String key, A model) {
    Map<String, String> updates = model.getDirtyFields();
    if (!updates.isEmpty()) {
      log.tracef("[redis] HSET %s %s", key, updates);
      txn.hset(key, updates);
      countOperation(HSET);
    }

    // Increment version — PEXPIREAT is blocked in MemoryDB Multi-Region.
    txn.hincrBy(key, "version", 1);

    Set<String> deletedFields = model.getDeletedFields();
    if (!deletedFields.isEmpty()) {
      String[] del = deletedFields.toArray(new String[0]);
      log.tracef("[redis] HDEL %s %s", key, Arrays.toString(del));
      txn.hdel(key, del);
      countOperation(HDEL);
    }

    for (Map.Entry<String, String> index : model.getSecondaryIndexes().entrySet()) {
      if (index.getKey() != null && index.getValue() != null) {
        log.tracef("[redis] SADD %s %s", index.getKey(), index.getValue());
        txn.sadd(index.getKey(), index.getValue());
        countOperation(SADD);
      }
    }
  }

  private void queueDelete(AbstractTransaction txn, String key, A model) {
    log.tracef("[redis] DEL %s", key);
    txn.del(key);
    countOperation(DEL);
    for (Map.Entry<String, String> index : model.getSecondaryIndexes().entrySet()) {
      log.tracef("[redis] SREM %s %s", index.getKey(), index.getValue());
      txn.srem(index.getKey(), index.getValue());
      countOperation(SREM);
    }
  }

  /**
   * Not used — this subclass overrides commitImpl() directly and batches all writes
   * in a single WATCH + MULTI/EXEC rather than per-model CAS invocations.
   */
  @Override
  protected RedisHashCas.CasInvocation performCasWrite(A model) {
    throw new UnsupportedOperationException("MemoryDbMultiRegionChangelogTransaction uses commitImpl override");
  }

  @Override
  protected boolean supportsAtomicWrites() {
    return false;
  }
}
