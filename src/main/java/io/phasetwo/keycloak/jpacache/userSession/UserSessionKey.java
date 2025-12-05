package io.phasetwo.keycloak.jpacache.userSession;

import io.phasetwo.keycloak.jpacache.Key;

public record UserSessionKey(String id) implements Key {
  @Override
  public String key() {
    return String.format("user-session:%s", id);
  }

  public static UserSessionKey fromString(String strKey) {
    if (strKey == null || !strKey.startsWith("user-session:")) {
      throw new IllegalArgumentException("Invalid key format: " + strKey);
    }

    String[] parts = strKey.split(":", 2);
    if (parts.length != 2) {
      throw new IllegalArgumentException("Expected format: user-client:<id>, got: " + strKey);
    }

    String id = parts[1];

    return new UserSessionKey(id);
  }
}
