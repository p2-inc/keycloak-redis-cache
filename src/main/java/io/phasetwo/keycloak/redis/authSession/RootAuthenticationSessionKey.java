package io.phasetwo.keycloak.redis.authSession;

import io.phasetwo.keycloak.redis.Key;

public record RootAuthenticationSessionKey(String realmId, String id) implements Key {
  @Override
  public String key() {
    return String.format("root-auth-session:%s:%s", realmId, id);
  }

  public static RootAuthenticationSessionKey fromString(String strKey) {
    if (strKey == null || !strKey.startsWith("root-auth-session:")) {
      throw new IllegalArgumentException("Invalid key format: " + strKey);
    }

    String[] parts = strKey.split(":", 3);
    if (parts.length != 3) {
      throw new IllegalArgumentException(
          "Expected format: root-auth-session:<realmId>:<id>, got: " + strKey);
    }

    String realmId = parts[1];
    String id = parts[2];

    return new RootAuthenticationSessionKey(realmId, id);
  }
}
