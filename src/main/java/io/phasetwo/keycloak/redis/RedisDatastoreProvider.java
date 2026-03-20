package io.phasetwo.keycloak.redis;

import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.*;
import org.keycloak.sessions.AuthenticationSessionProvider;
import org.keycloak.storage.datastore.DefaultDatastoreProvider;

@JBossLog
public class RedisDatastoreProvider extends DefaultDatastoreProvider {
  private final KeycloakSession session;

  public RedisDatastoreProvider(RedisDatastoreProviderFactory factory, KeycloakSession session) {
    super(factory, session);
    this.session = session;
  }

  @Override
  public UserLoginFailureProvider loginFailures() {
    return session.getProvider(UserLoginFailureProvider.class);
  }

  @Override
  public SingleUseObjectProvider singleUseObjects() {
    return session.getProvider(SingleUseObjectProvider.class);
  }

  @Override
  public AuthenticationSessionProvider authSessions() {
    return session.getProvider(AuthenticationSessionProvider.class);
  }

  @Override
  public UserSessionProvider userSessions() {
    return session.getProvider(UserSessionProvider.class);
  }
}
