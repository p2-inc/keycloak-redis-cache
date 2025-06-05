package io.phasetwo.keycloak.jpacache.loginFailure;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Set;
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
  private final Map<String, RedisUserLoginFailureAdapter> cache = Maps.newHashMap();
  private final RedisUserLoginFailureTransaction tx;

  public RedisUserLoginFailureProvider(KeycloakSession session, Jedis jedis) {
    this.jedis = jedis;
    this.session = session;
    this.tx = new RedisUserLoginFailureTransaction(jedis);
    session.getTransactionManager().enlistAfterCompletion(tx);
  }

  @Override
  public UserLoginFailureModel getUserLoginFailure(RealmModel realm, String userId) {
    return tx.get(realm.getId(), userId);
  }

  @Override
  public UserLoginFailureModel addUserLoginFailure(RealmModel realm, String userId) {
    RedisUserLoginFailureAdapter model = new RedisUserLoginFailureAdapter(realm.getId(), userId);
    tx.addForSave(model);
    return model;
  }

  @Override
  public void removeUserLoginFailure(RealmModel realm, String userId) {
    tx.addForDelete(new LoginFailureKey(realm.getId(), userId));
  }

  @Override
  public void removeAllUserLoginFailures(RealmModel realm) {
    String indexKey = "login-failure:index:" + realm.getId();
    log.debugf("[redis] SMEMBERS %s", indexKey);
    Set<String> userIds = jedis.smembers(indexKey);
    for (String userId : userIds) {
      tx.addForDelete(new LoginFailureKey(realm.getId(), userId));
    }
  }

  @Override
  public void close() {
    // Jedis managed outside
  }
}
