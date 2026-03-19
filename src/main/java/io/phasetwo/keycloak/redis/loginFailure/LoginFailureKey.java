package io.phasetwo.keycloak.redis.loginFailure;

import io.phasetwo.keycloak.redis.Key;

public record LoginFailureKey(String realmId, String userId) implements Key {
  @Override
  public String key() {
    return String.format("login-failure:%s:%s", realmId, userId);
  }
}
