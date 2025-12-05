package io.phasetwo.keycloak.jpacache.loginFailure;

import io.phasetwo.keycloak.jpacache.AdapterSupplier;
import java.util.Map;
import org.keycloak.models.KeycloakSession;
import redis.clients.jedis.Jedis;

public class UserLoginFailureAdapterSupplier
    implements AdapterSupplier<LoginFailureKey, RedisUserLoginFailureAdapter> {

  private final KeycloakSession session;
  private final Jedis jedis;

  public UserLoginFailureAdapterSupplier(KeycloakSession session, Jedis jedis) {
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
