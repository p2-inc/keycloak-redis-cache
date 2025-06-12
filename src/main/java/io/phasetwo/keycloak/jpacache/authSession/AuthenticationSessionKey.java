package io.phasetwo.keycloak.jpacache.authSession;

import io.phasetwo.keycloak.jpacache.Key;

public record AuthenticationSessionKey(String tabId) implements Key {
  @Override
  public String key() {
    return String.format("auth-session:%s", tabId);
  }
}
