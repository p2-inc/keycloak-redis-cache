package io.phasetwo.keycloak.jpacache.loginFailure;

import java.util.*;
import org.keycloak.models.AbstractKeycloakTransaction;
import redis.clients.jedis.Jedis;

public class RedisUserLoginFailureTransaction extends AbstractKeycloakTransaction {

  private final List<RedisUserLoginFailureAdapter> toPersist = new ArrayList<>();
  private final List<String> toDelete = new ArrayList<>();
  private final Jedis jedis;
  private final String realmId;

  public RedisUserLoginFailureTransaction(Jedis jedis, String realmId) {
    this.jedis = jedis;
    this.realmId = realmId;
  }

  public void addForSave(RedisUserLoginFailureAdapter model) {
    toPersist.add(model);
  }

  public void addForDelete(String userId) {
    toDelete.add(userId);
  }

  @Override
  protected void commitImpl() {
    String indexKey = "login-failure:index:" + realmId;

    for (RedisUserLoginFailureAdapter model : toPersist) {
      String key = "login-failure:" + realmId + ":" + model.getUserId();

      if (model.isMarkedForDelete()) {
        jedis.del(key);
        jedis.srem(indexKey, model.getUserId());
      } else if (model.isDirty()) {
        Map<String, String> updates = model.getDirtyFields();
        jedis.hset(key, updates);
        jedis.sadd(indexKey, model.getUserId());
      }
    }

    for (String userId : toDelete) {
      String key = "login-failure:" + realmId + ":" + userId;
      jedis.del(key);
      jedis.srem(indexKey, userId);
    }
  }

  @Override
  protected void rollbackImpl() {
    // No action needed on rollback for this use case
  }
}
