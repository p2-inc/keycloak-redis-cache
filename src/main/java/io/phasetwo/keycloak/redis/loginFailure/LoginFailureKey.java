package io.phasetwo.keycloak.redis.loginFailure;

import io.phasetwo.keycloak.redis.Key;
import io.phasetwo.keycloak.redis.connection.DefaultRedisConnectionProviderFactory;

public record LoginFailureKey(String realmId, String userId) implements Key {
  @Override
  public String key() {
      if (DefaultRedisConnectionProviderFactory.isCluster()) {
          return String.format("login-failure:{%s:%s}", realmId, userId);
      } else {
          return String.format("login-failure:%s:%s", realmId, userId);
      }
  }
}
