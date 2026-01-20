package io.phasetwo.keycloak.jpacache.authSession;

import io.phasetwo.keycloak.jpacache.AdapterSupplier;
import java.util.Map;
import org.keycloak.models.KeycloakSession;
import redis.clients.jedis.UnifiedJedis;

public class AuthenticationSessionAdapterSupplier
    implements AdapterSupplier<AuthenticationSessionKey, RedisAuthenticationSessionAdapter> {

  private final KeycloakSession session;
  private final UnifiedJedis jedis;

  public AuthenticationSessionAdapterSupplier(KeycloakSession session, UnifiedJedis jedis) {
    this.session = session;
    this.jedis = jedis;
  }

  @Override
  public RedisAuthenticationSessionAdapter newInstance(AuthenticationSessionKey key) {
    return new RedisAuthenticationSessionAdapter(session, key.clientId(), key.tabId());
  }

  @Override
  public RedisAuthenticationSessionAdapter newInstance(
      AuthenticationSessionKey key, Map<String, String> data) {
    return new RedisAuthenticationSessionAdapter(session, key.clientId(), key.tabId(), data);
  }
}
