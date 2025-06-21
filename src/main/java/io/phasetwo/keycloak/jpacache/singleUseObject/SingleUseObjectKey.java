package io.phasetwo.keycloak.jpacache.singleUseObject;

import io.phasetwo.keycloak.jpacache.Key;

public record SingleUseObjectKey(String name) implements Key {
  @Override
  public String key() {
    return String.format("suo:%s", name);
  }
}
