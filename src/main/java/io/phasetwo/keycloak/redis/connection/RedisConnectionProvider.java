package io.phasetwo.keycloak.redis.connection;

import org.keycloak.provider.Provider;
import redis.clients.jedis.UnifiedJedis;

public interface RedisConnectionProvider extends Provider {
  UnifiedJedis getJedis();

  UnifiedJedis createClient();
}
