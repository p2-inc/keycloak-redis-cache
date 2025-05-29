package io.phasetwo.keycloak.common;

import static io.phasetwo.keycloak.common.CommunityProfiles.isRedisCacheEnabled;

import org.keycloak.Config;
import org.keycloak.provider.EnvironmentDependentProviderFactory;

public interface IsSupported extends EnvironmentDependentProviderFactory {

  default boolean isSupported() {
    return isRedisCacheEnabled();
  }

  @Override
  default boolean isSupported(Config.Scope config) {
    return isRedisCacheEnabled();
  }
}
