package io.phasetwo.keycloak.jpacache.userSession;

import io.phasetwo.keycloak.jpacache.AdapterSupplier;
import io.phasetwo.keycloak.jpacache.RedisChangelogTransaction;
import java.util.Map;
import org.keycloak.models.KeycloakSession;
import redis.clients.jedis.Jedis;

public class UserSessionAdapterSupplier
    implements AdapterSupplier<UserSessionKey, RedisUserSessionAdapter> {

  private final KeycloakSession session;
  private final Jedis jedis;
  private final RedisChangelogTransaction<
          AuthenticatedClientSessionKey, RedisAuthenticatedClientSessionAdapter>
      clientSessionTrx;

  public UserSessionAdapterSupplier(
      KeycloakSession session,
      Jedis jedis,
      RedisChangelogTransaction<
              AuthenticatedClientSessionKey, RedisAuthenticatedClientSessionAdapter>
          clientSessionTrx) {
    this.session = session;
    this.jedis = jedis;
    this.clientSessionTrx = clientSessionTrx;
  }

  @Override
  public RedisUserSessionAdapter newInstance(UserSessionKey key) {
    return new RedisUserSessionAdapter(session, jedis, clientSessionTrx, key.id());
  }

  @Override
  public RedisUserSessionAdapter newInstance(UserSessionKey key, Map<String, String> data) {
    return new RedisUserSessionAdapter(session, jedis, clientSessionTrx, key.id(), data);
  }
}
