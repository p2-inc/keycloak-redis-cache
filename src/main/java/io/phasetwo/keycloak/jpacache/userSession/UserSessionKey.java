package io.phasetwo.keycloak.jpacache.userSession;

import io.phasetwo.keycloak.jpacache.Key;

public record UserSessionKey(String id) implements Key {
  @Override
  public String key() {
    return String.format("user-session:%s", id);
  }
}
