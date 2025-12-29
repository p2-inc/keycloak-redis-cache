package io.phasetwo.keycloak.jpacache.loginFailure;

import io.phasetwo.keycloak.jpacache.RedisChangelogTransaction;
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
  private final RedisChangelogTransaction<LoginFailureKey, RedisUserLoginFailureAdapter>
      loginFailureTrx;

  public RedisUserLoginFailureProvider(KeycloakSession session, Jedis jedis) {
    this.jedis = jedis;
    this.session = session;
    this.loginFailureTrx =
        new RedisChangelogTransaction<>("loginFailure", jedis, new UserLoginFailureAdapterSupplier(session, jedis));
    ;
    session.getTransactionManager().enlistAfterCompletion(loginFailureTrx);
  }

  @Override
  public UserLoginFailureModel getUserLoginFailure(RealmModel realm, String userId) {
    return loginFailureTrx.getIfPresent(new LoginFailureKey(realm.getId(), userId));
  }

  @Override
  public UserLoginFailureModel addUserLoginFailure(RealmModel realm, String userId) {
    return loginFailureTrx.get(new LoginFailureKey(realm.getId(), userId));
  }

  @Override
  public void removeUserLoginFailure(RealmModel realm, String userId) {
    UserLoginFailureModel lf = getUserLoginFailure(realm, userId);
    if (lf != null) {
      loginFailureTrx.addForDelete((RedisUserLoginFailureAdapter) lf);
    }
  }

  @Override
  public void removeAllUserLoginFailures(RealmModel realm) {
    String indexKey = "login-failure:index:" + realm.getId();
    log.tracef("[redis] SMEMBERS %s", indexKey);
    Set<String> userIds = jedis.smembers(indexKey);
    log.tracef("found %d login failures for realm %s", userIds.size(), realm.getId());
    for (String userId : userIds) {
      log.tracef("removing login failure for realm %s user %s", realm.getId(), userId);
      removeUserLoginFailure(realm, userId);
    }
    loginFailureTrx.cachedToDelete(); // todo do this somehow per realm?
  }

  @Override
  public void close() {}
}
