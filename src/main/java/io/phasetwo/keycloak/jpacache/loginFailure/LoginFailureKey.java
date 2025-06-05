package io.phasetwo.keycloak.jpacache.loginFailure;

import io.phasetwo.keycloak.jpacache.Key;

public record LoginFailureKey(String realmId, String userId) implements Key {
  @Override
  public String key() {
    return String.format("login-failure:%s:%s", realmId, userId);
  }
}
