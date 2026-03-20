package io.phasetwo.keycloak.redis.singleUseObject;

import io.phasetwo.keycloak.redis.Key;

public record SingleUseObjectKey(String name) implements Key {
  @Override
  public String key() {
    return String.format("suo:%s", name);
  }
}
