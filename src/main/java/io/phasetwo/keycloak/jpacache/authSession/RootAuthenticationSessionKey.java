package io.phasetwo.keycloak.jpacache.authSession;

import io.phasetwo.keycloak.jpacache.Key;

public record RootAuthenticationSessionKey(String realmId, String id) implements Key {
  @Override
  public String key() {
    return String.format("root-auth-session:%s:%s", realmId, id);
  }
}
