package io.phasetwo.keycloak.redis.authSession;

import io.phasetwo.keycloak.redis.Key;
import io.phasetwo.keycloak.redis.connection.DefaultRedisConnectionProviderFactory;

public record AuthenticationSessionKey(String clientId, String tabId) implements Key {
  @Override
  public String key() {
      if (DefaultRedisConnectionProviderFactory.isCluster()) {
          return String.format("auth-session:{%s:%s}", clientId, tabId);
      } else {
          return String.format("auth-session:%s:%s", clientId, tabId);
      }
  }

  public static AuthenticationSessionKey fromString(String strKey) {
    if (strKey == null || !strKey.startsWith("auth-session:")) {
      throw new IllegalArgumentException("Invalid key format: " + strKey);
    }

    String[] parts = strKey.split(":", 3); // limit = 3 to handle extra colons in tabId safely
    if (parts.length != 3) {
      throw new IllegalArgumentException(
          "Expected format: auth-session:<clientId>:<tabId>, got: " + strKey);
    }

    String clientId = parts[1];
    String tabId = parts[2];

    return new AuthenticationSessionKey(clientId, tabId);
  }
}
