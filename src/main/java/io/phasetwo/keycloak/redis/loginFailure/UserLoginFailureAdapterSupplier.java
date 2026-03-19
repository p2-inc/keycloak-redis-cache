package io.phasetwo.keycloak.redis.loginFailure;

import io.phasetwo.keycloak.redis.AdapterSupplier;
import java.util.Map;
import org.keycloak.models.KeycloakSession;
import redis.clients.jedis.UnifiedJedis;

public class UserLoginFailureAdapterSupplier
    implements AdapterSupplier<LoginFailureKey, RedisUserLoginFailureAdapter> {

  private final KeycloakSession session;
  private final UnifiedJedis jedis;

  public UserLoginFailureAdapterSupplier(KeycloakSession session, UnifiedJedis jedis) {
    this.session = session;
    this.jedis = jedis;
  }

  @Override
  public RedisUserLoginFailureAdapter newInstance(LoginFailureKey key) {
    return new RedisUserLoginFailureAdapter(key.realmId(), key.userId());
  }

  @Override
  public RedisUserLoginFailureAdapter newInstance(LoginFailureKey key, Map<String, String> data) {
    return new RedisUserLoginFailureAdapter(key.realmId(), key.userId(), data);
  }
}
