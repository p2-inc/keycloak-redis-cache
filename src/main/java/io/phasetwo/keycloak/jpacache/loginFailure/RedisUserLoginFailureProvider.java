package io.phasetwo.keycloak.jpacache.loginFailure;


import java.util.Map;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserLoginFailureModel;
import org.keycloak.models.UserLoginFailureProvider;
import redis.clients.jedis.Jedis;

@JBossLog
public class RedisUserLoginFailureProvider implements UserLoginFailureProvider {

  private final Jedis jedis;
  private final KeycloakSession session;
  private final Map<String, RedisUserLoginFailureAdapter> cache = new HashMap<>();
  private final RedisLoginFailureTransaction tx;

  public RedisUserLoginFailureProvider(Jedis jedis, KeycloakSession session) {
    this.jedis = jedis;
    this.session = session;
    this.tx = new RedisLoginFailureTransaction(jedis, realmId);
    session.getTransactionManager().enlistAfterCompletion(tx);
  }

  @Override
  public UserLoginFailureModel getUserLoginFailure(RealmModel realm, String userId) {
    String key = "login-failure:" + realm.getId() + ":" + userId;
    Map<String, String> data = jedis.hgetAll(key);
    if (data == null || data.isEmpty()) return null;

    RedisUserLoginFailureAdapter model = new RedisUserLoginFailureAdapter(realm, userId, data);
    cache.put(userId, model);
    return model;
  }

  @Override
  public UserLoginFailureModel addUserLoginFailure(RealmModel realm, String userId) {
    RedisUserLoginFailureAdapter model =
        new RedisUserLoginFailureAdapter(realm, userId, new HashMap<>());
    cache.put(userId, model);
    tx.addForSave(model);
    return model;
  }

  @Override
  public void removeUserLoginFailure(RealmModel realm, String userId) {
    RedisUserLoginFailureAdapter model =
        new RedisUserLoginFailureAdapter(realm, userId, new HashMap<>());
    model.markForDelete();
    tx.addForSave(model);
  }

  @Override
  public void removeAllUserLoginFailures(RealmModel realm) {
    String indexKey = "login-failure:index:" + realm.getId();
    Set<String> userIds = jedis.smembers(indexKey);
    for (String userId : userIds) {
      tx.addForDelete(userId);
    }
  }

  @Override
  public void close() {
    // Jedis managed outside
  }
}
