package io.phasetwo.keycloak.redis.loginFailure;

import static io.phasetwo.keycloak.common.Constants.PROVIDER_PRIORITY;
import static io.phasetwo.keycloak.common.ProviderHelpers.createProviderCached;

import com.google.auto.service.AutoService;
import io.phasetwo.keycloak.common.IsSupported;
import io.phasetwo.keycloak.redis.connection.RedisConnectionProvider;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.UserLoginFailureProviderFactory;

@SuppressWarnings("rawtypes")
@AutoService(UserLoginFailureProviderFactory.class)
public class RedisUserLoginFailureProviderFactory
    implements UserLoginFailureProviderFactory<RedisUserLoginFailureProvider>, IsSupported {

  @Override
  public RedisUserLoginFailureProvider create(KeycloakSession session) {
    RedisConnectionProvider redisConnectionProvider =
        createProviderCached(session, RedisConnectionProvider.class);
    return new RedisUserLoginFailureProvider(
        session, redisConnectionProvider.getJedis(), redisConnectionProvider.getRedisMode());
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
}
