package io.phasetwo.keycloak.redis.userSession;

import io.phasetwo.keycloak.redis.Key;

public record AuthenticatedClientSessionKey(String id) implements Key {
  @Override
  public String key() {
    return String.format("authenticated-client:%s", id);
  }

  public static AuthenticatedClientSessionKey fromString(String strKey) {
    if (strKey == null || !strKey.startsWith("authenticated-client:")) {
      throw new IllegalArgumentException("Invalid key format: " + strKey);
    }

    String[] parts = strKey.split(":", 2);
    if (parts.length != 2) {
      throw new IllegalArgumentException(
          "Expected format: authenticated-client:<id>, got: " + strKey);
    }

    String id = parts[1];

    return new AuthenticatedClientSessionKey(id);
  }
}
