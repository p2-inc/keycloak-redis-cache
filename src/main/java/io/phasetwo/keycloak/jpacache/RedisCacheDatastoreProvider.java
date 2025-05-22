package io.phasetwo.keycloak.jpacache;

import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.*;
import org.keycloak.sessions.AuthenticationSessionProvider;
import org.keycloak.storage.datastore.DefaultDatastoreProvider;

@JBossLog
public class RedisCacheDatastoreProvider extends DefaultDatastoreProvider {
  private final KeycloakSession session;

  public RedisCacheDatastoreProvider(
          RedisCacheDatastoreProviderFactory factory, KeycloakSession session) {
    super(factory, session);
    this.session = session;
  }

  @Override
  public SingleUseObjectProvider singleUseObjects() {
    return session.getProvider(SingleUseObjectProvider.class);
  }
}
