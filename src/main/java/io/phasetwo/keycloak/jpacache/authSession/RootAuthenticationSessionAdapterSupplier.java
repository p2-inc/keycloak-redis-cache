package io.phasetwo.keycloak.jpacache.authSession;

import io.phasetwo.keycloak.jpacache.AdapterSupplier;
import io.phasetwo.keycloak.jpacache.RedisChangelogTransaction;
import java.util.Map;
import org.keycloak.models.KeycloakSession;
import redis.clients.jedis.Jedis;

public class RootAuthenticationSessionAdapterSupplier
    implements AdapterSupplier<
        RootAuthenticationSessionKey, RedisRootAuthenticationSessionAdapter> {

  private final KeycloakSession session;
  private final Jedis jedis;
  private final int authSessionsLimit;
  private final RedisChangelogTransaction<
          AuthenticationSessionKey, RedisAuthenticationSessionAdapter>
      authSessionTrx;

  public RootAuthenticationSessionAdapterSupplier(
      KeycloakSession session,
      Jedis jedis,
      int authSessionsLimit,
      RedisChangelogTransaction<AuthenticationSessionKey, RedisAuthenticationSessionAdapter>
          authSessionTrx) {
    this.session = session;
    this.jedis = jedis;
    this.authSessionsLimit = authSessionsLimit;
    this.authSessionTrx = authSessionTrx;
  }

  @Override
  public RedisRootAuthenticationSessionAdapter newInstance(RootAuthenticationSessionKey key) {
    return new RedisRootAuthenticationSessionAdapter(
        session, jedis, authSessionsLimit, authSessionTrx, key.realmId(), key.id());
  }

  @Override
  public RedisRootAuthenticationSessionAdapter newInstance(
      RootAuthenticationSessionKey key, Map<String, String> data) {
    return new RedisRootAuthenticationSessionAdapter(
        session, jedis, authSessionsLimit, authSessionTrx, key.realmId(), key.id(), data);
  }
}
