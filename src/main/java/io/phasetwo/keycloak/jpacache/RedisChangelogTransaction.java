package io.phasetwo.keycloak.jpacache;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.phasetwo.keycloak.common.ExpirableEntity;
import io.phasetwo.keycloak.common.ExpirationUtils;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.AbstractKeycloakTransaction;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.params.HSetExParams;

@JBossLog
public class RedisChangelogTransaction<K extends Key, A extends MapEntity<K>>
    extends AbstractKeycloakTransaction {

  private final Map<K, A> cache = Maps.newHashMap();
  private final Map<K, A> toDelete = Maps.newHashMap();
  private final AdapterSupplier<K, A> adapterSupplier;
  private final Jedis jedis;

  public RedisChangelogTransaction(Jedis jedis, AdapterSupplier<K, A> adapterSupplier) {
    this.jedis = jedis;
    this.adapterSupplier = adapterSupplier;
  }

  /**
   * Gets the value if present at the key. Creates a new instance and registers it for saving using
   * the adapter supplier if none is present at the key.
   */
  public A get(K k) {
    A model = getIfPresent(k);
    if (model == null) {
      model = adapterSupplier.newInstance(k);
      cache.put(k, model);
    }
    return model;
  }

  /** Gets the value only if present at the key. Returns null otherwise. */
  public A getIfPresent(K k) {
    if (k == null) return null;
    if (toDelete.containsKey(k)) return null; // this is wrong
    A model = cache.get(k);
    if (model != null && !expired(k, model)) return model;
    String key = k.key();
    log.tracef("[redis] HGETALL %s", key);
    Map<String, String> data = jedis.hgetAll(key);
    if (data != null && !data.isEmpty()) {
      log.tracef("found data for %s %s", key, data);
      model = adapterSupplier.newInstance(k, data);
      if (!expired(k, model)) {
        cache.put(k, model);
        return model;
      }
    }
    return null;
  }

  /** Lazy removal. Check to see if an entity is expired. return true if it was. add it toDelete. */
  private boolean expired(K k, A a) {
    if (a instanceof ExpirableEntity) {
      ExpirableEntity e = (ExpirableEntity) a;
      if (ExpirationUtils.isExpired(e, true)) {
        log.tracef("Entity at %s expired %s. Lazy removing.", k, ExpirationUtils.fromNow(e));
        addForDelete(a);
        return true;
      } else {
        log.tracef("Entity at %s active. Expires in %s.", k, ExpirationUtils.fromNow(e));
        return false;
      }
    }
    log.tracef("Entity at %s is not an expirable entity.", k);
    return false;
  }

  /**
   * Gets the a map of value if present at the given keys. Return value is a map of the key to the
   * value. May be fewer results if some keys don't have values.
   */
  public Map<K, A> getAll(Collection<K> keys) {
    if (keys == null || keys.isEmpty()) return Maps.newLinkedHashMap();
    Pipeline pipeline = jedis.pipelined();
    Map<K, Response<Map<String, String>>> responses = Maps.newLinkedHashMap();
    Map<K, A> result = Maps.newLinkedHashMap();

    // Queue all HGETALLs
    for (K key : keys) {
      if (toDelete.containsKey(key)) continue;
      A model = cache.get(key);
      if (model != null) {
        result.put(key, model);
      } else {
        log.tracef("[redis] HGETALL %s", key.key());
        responses.put(key, pipeline.hgetAll(key.key()));
      }
    }
    if (!responses.isEmpty()) { // only execute if some were not cached
      pipeline.sync(); // flush and read all in one round-trip
      // Build result map
      for (Map.Entry<K, Response<Map<String, String>>> entry : responses.entrySet()) {
        K key = entry.getKey();
        Map<String, String> data = entry.getValue().get();
        if (data != null && !data.isEmpty()) {
          A model = adapterSupplier.newInstance(key, data);
          if (!expired(key, model)) {
            result.put(key, model);
            cache.put(key, model);
          }
        }
      }
    }
    return result;
  }

  public void addForSave(A model) {
    cache.put(model.getKey(), model);
  }

  public void addForDelete(A model) {
    toDelete.put(model.getKey(), model);
  }

  public void cachedToDelete() {
    for (A model : cache.values()) {
      addForDelete(model);
    }
  }

  @Override
  protected void commitImpl() {
    Set<String> keysToWatch = Sets.newHashSet();

    // Keys to watch: all affected session keys + index
    for (A model : cache.values()) {
      if (!toDelete.containsKey(model.getKey())) {
        log.tracef("adding key to WATCH %s", model.getKey().key());
        keysToWatch.add(model.getKey().key());
      }
    }
    for (A model : toDelete.values()) {
      log.tracef("adding key to WATCH %s", model.getKey().key());
      keysToWatch.add(model.getKey().key());
    }

    try {
      String[] kw = keysToWatch.toArray(new String[0]);
      if (kw == null || kw.length == 0) {
        log.trace("nothing to WATCH. skipping transaction...");
        return; // nothing to do?
      } else {
        log.tracef("[redis] WATCH %s", kw);
        jedis.watch(kw);
      }

      // Jedis automatically batches MULTI/EXEC transactions like a pipeline, so you do not need a
      // separate Pipeline to reduce round trips inside a MULTI.
      log.tracef("[redis] MULTI");
      Transaction txn = jedis.multi();

      for (A model : cache.values()) {
        String key = model.getKey().key();

        if (model.isMarkedForDelete() || toDelete.containsKey(model.getKey())) {
          log.tracef("[redis] DEL %s", key);
          txn.del(key);
          for (Map.Entry<String, String> index : model.getSecondaryIndexes().entrySet()) {
            log.tracef("[redis] SREM %s %s", index.getKey(), index.getValue());
            txn.srem(index.getKey(), index.getValue());
            toDelete.remove(model.getKey());
          }
        } else if (model.isDirty()) {
          Map<String, String> updates = model.getDirtyFields();
          if (!updates.isEmpty()) {
            // hset the new/changed values
            if (model instanceof ExpirableEntity) {
              ExpirableEntity e = (ExpirableEntity) model;
              log.tracef("[redis] (exp:%s) HSET %s %s", ExpirationUtils.fromNow(e), key, updates);
              txn.hsetex(
                  key,
                  HSetExParams.hSetExParams().pxAt(e.getExpiration()),
                  updates); // todo need to check for expiration null
            } else {
              log.tracef("[redis] HSET %s %s", key, updates);
              txn.hset(key, updates);
            }
          }
          // sadd the secondary indexes
          for (Map.Entry<String, String> index : model.getSecondaryIndexes().entrySet()) {
            log.tracef("[redis] SADD %s %s", index.getKey(), index.getValue());
            txn.sadd(index.getKey(), index.getValue());
          }
          // hdel the values that were unset
          Set<String> deletedFields = model.getDeletedFields();
          String[] del = deletedFields.toArray(new String[0]);
          if (del != null && del.length > 0) {
            log.tracef("[redis] HDEL %s %s", key, Arrays.toString(del));
            txn.hdel(key, del);
          }
        }
      }
      // will this ever run?
      log.tracef("toDelete still has %d entries", toDelete.size());
      for (A model : toDelete.values()) {
        String key = model.getKey().key();
        log.tracef("[redis] DEL %s", key);
        txn.del(key);
        for (Map.Entry<String, String> index : model.getSecondaryIndexes().entrySet()) {
          log.tracef("[redis] SREM %s %s", index.getKey(), index.getValue());
          txn.srem(index.getKey(), index.getValue());
        }
      }

      log.tracef("[redis] EXEC");
      List<Object> results = txn.exec();
      if (results == null) {
        throw new IllegalStateException("Redis transaction aborted due to concurrent modification");
      }
    } finally {
      // anything to clean up?
    }
  }

  @Override
  protected void rollbackImpl() {
    // No action needed on rollback for this use case
  }
}
