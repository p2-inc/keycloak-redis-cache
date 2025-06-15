package io.phasetwo.keycloak.jpacache.userSession;

import io.phasetwo.keycloak.jpacache.AdapterSupplier;
import java.util.Map;
import org.keycloak.models.KeycloakSession;
import redis.clients.jedis.Jedis;

public class AuthenticatedClientSessionAdapterSupplier
    implements AdapterSupplier<
        AuthenticatedClientSessionKey, RedisAuthenticatedClientSessionAdapter> {

  private final KeycloakSession session;
  private final Jedis jedis;

  public AuthenticatedClientSessionAdapterSupplier(KeycloakSession session, Jedis jedis) {
    this.session = session;
    this.jedis = jedis;
  }

  @Override
  public RedisAuthenticatedClientSessionAdapter newInstance(AuthenticatedClientSessionKey key) {
    return new RedisAuthenticatedClientSessionAdapter(session, key.id());
  }

  @Override
  public RedisAuthenticatedClientSessionAdapter newInstance(
      AuthenticatedClientSessionKey key, Map<String, String> data) {
    return new RedisAuthenticatedClientSessionAdapter(session, key.id(), data);
  }
}
