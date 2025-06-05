package io.phasetwo.keycloak.jpacache.loginFailure;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.AbstractKeycloakTransaction;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

@JBossLog
public class RedisUserLoginFailureTransaction extends AbstractKeycloakTransaction {

  private final Map<LoginFailureKey, RedisUserLoginFailureAdapter> cache = Maps.newHashMap();
  private final Set<LoginFailureKey> toDelete = Sets.newHashSet();
  private final Jedis jedis;

  public RedisUserLoginFailureTransaction(Jedis jedis) {
    this.jedis = jedis;
  }

  public RedisUserLoginFailureAdapter get(String realmId, String userId) {
    RedisUserLoginFailureAdapter model = cache.get(userId);
    if (model != null) return model;
    String key = "login-failure:" + realmId + ":" + userId;
    log.debugf("[redis] HGETALL %s", key);
    Map<String, String> data = jedis.hgetAll(key);
    if (data == null || data.isEmpty()) return null;
    log.debugf("found data for %s %s", key, data);
    model = new RedisUserLoginFailureAdapter(realmId, userId, data);
    cache.put(model.getKey(), model);
    return model;
  }

  public void addForSave(RedisUserLoginFailureAdapter model) {
    cache.put(model.getKey(), model);
  }

  public void addForDelete(LoginFailureKey key) {
    toDelete.add(key);
  }

  @Override
  protected void commitImpl() {
    Set<String> keysToWatch = Sets.newHashSet();

    // Keys to watch: all affected session keys + index
    for (RedisUserLoginFailureAdapter model : cache.values()) {
      log.debugf("adding key to WATCH %s", model.getKey().key());
      keysToWatch.add(model.getKey().key());
    }
    for (LoginFailureKey key : toDelete) {
      log.debugf("adding key to WATCH %s", key.key());
      keysToWatch.add(key.key());
    }

    try {
      String[] kw = keysToWatch.toArray(new String[0]);
      if (kw == null || kw.length == 0) {
        log.debug("nothing to WATCH. skipping transaction...");
        return; // nothing to do?
      } else {
        // log.debugf("[redis] WATCH %s", kw);
        // jedis.watch(kw);
      }

      log.debugf("[redis] MULTI");
      Transaction txn = jedis.multi();

      for (RedisUserLoginFailureAdapter model : cache.values()) {
        String key = model.getKey().key();

        if (model.isMarkedForDelete() || toDelete.contains(model.getKey())) {
          log.debugf("[redis] DEL %s", key);
          txn.del(key);
          for (Map.Entry<String, String> index : model.getSecondaryIndexes().entrySet()) {
            log.debugf("[redis] SREM %s %s", index.getKey(), index.getValue());
            txn.srem(index.getKey(), index.getValue());
            toDelete.remove(model.getKey());
          }
        } else if (model.isDirty()) {
          Map<String, String> updates = model.getDirtyFields();
          log.debugf("[redis] HSET %s %s", key, updates);
          txn.hset(key, updates);
          for (Map.Entry<String, String> index : model.getSecondaryIndexes().entrySet()) {
            log.debugf("[redis] SADD %s %s", index.getKey(), index.getValue());
            txn.sadd(index.getKey(), index.getValue());
          }
          for (String deletedField : model.getDeletedFields()) {
            log.debugf("[redis] HDEL %s %s", key, deletedField);
            txn.hdel(key, deletedField);
          }
        }
      }
      // will this ever run?
      for (LoginFailureKey k : toDelete) {
        String key = k.key();
        // how to get this without a model
        String indexKey = String.format("login-failure:index:%s", k.realmId(), k.userId());
        log.debugf("[redis] DEL %s", key);
        txn.del(key);
        log.debugf("[redis] SREM %s %s", indexKey, k.userId());
        txn.srem(indexKey, k.userId());
      }

      log.debugf("[redis] EXEC");
      List<Object> results = txn.exec();
      if (results == null) {
        throw new IllegalStateException("Redis transaction aborted due to concurrent modification");
      }
    } finally {
      // log.debugf("[redis] UNWATCH");
      // jedis.unwatch(); // Always unwatch even if exec fails
    }
  }

  @Override
  protected void rollbackImpl() {
    // No action needed on rollback for this use case
  }
}
