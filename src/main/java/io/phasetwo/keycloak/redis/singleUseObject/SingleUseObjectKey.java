package io.phasetwo.keycloak.redis.singleUseObject;

import io.phasetwo.keycloak.redis.Key;
import io.phasetwo.keycloak.redis.connection.DefaultRedisConnectionProviderFactory;

public record SingleUseObjectKey(String name) implements Key {
  @Override
  public String key() {
      if (DefaultRedisConnectionProviderFactory.isCluster()) {
          return String.format("suo:{%s}", name);
      } else {
          return String.format("suo:%s", name);
      }

  }
}
