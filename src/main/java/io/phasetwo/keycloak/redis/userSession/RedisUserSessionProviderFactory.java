package io.phasetwo.keycloak.redis.userSession;

import static io.phasetwo.keycloak.common.Constants.PROVIDER_PRIORITY;
import static io.phasetwo.keycloak.common.ProviderHelpers.createProviderCached;

import com.google.auto.service.AutoService;
import io.phasetwo.keycloak.common.IsSupported;
import io.phasetwo.keycloak.redis.connection.RedisConnectionProvider;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.UserSessionProviderFactory;

@JBossLog
@SuppressWarnings("rawtypes")
@AutoService(UserSessionProviderFactory.class)
public class RedisUserSessionProviderFactory
    implements UserSessionProviderFactory<RedisUserSessionProvider>, IsSupported {

  @Override
  public RedisUserSessionProvider create(KeycloakSession session) {
    RedisConnectionProvider redisConnectionProvider =
        createProviderCached(session, RedisConnectionProvider.class);
    return new RedisUserSessionProvider(session, redisConnectionProvider.getJedis());
  }

  @Override
  public void init(Config.Scope config) {}

  @Override
  public void postInit(KeycloakSessionFactory factory) {}

  @Override
  public void close() {}

  @Override
  public String getId() {
    return "infinispan"; // use same name as infinispan provider to override it
  }

  @Override
  public int order() {
    return PROVIDER_PRIORITY + 1;
  }

  @SuppressWarnings("deprecation")
  @Override
  public void loadPersistentSessions(
      KeycloakSessionFactory sessionFactory, int maxErrors, int sessionsPerSegment) {
    log.tracef("loadPersistentSessions %d %d", maxErrors, sessionsPerSegment);
  }
}
