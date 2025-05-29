package io.phasetwo.keycloak.common;

public class CommunityProfiles {
  private static final String ENV_REDIS_CACHE_ENABLED = "KC_COMMUNITY_REDIS_CACHE_ENABLED";
  private static final String PROP_REDIS_CACHE_ENABLED = "kc.community.redis.cache.enabled";

  private static final boolean isRedisCacheEnabled;

  static {
    isRedisCacheEnabled =
        Boolean.parseBoolean(System.getenv(ENV_REDIS_CACHE_ENABLED))
            || Boolean.parseBoolean(System.getProperty(PROP_REDIS_CACHE_ENABLED));
  }

  public static boolean isRedisCacheEnabled() {
    return isRedisCacheEnabled;
  }
}
