package io.phasetwo.keycloak.jpacache.userSession;

import io.phasetwo.keycloak.jpacache.Key;

public record AuthenticatedClientSessionKey(String id) implements Key {
  @Override
  public String key() {
    return String.format("authenticated-client:%s", id);
  }
}
