package io.phasetwo.keycloak.jpacache.connection;

import org.keycloak.provider.Provider;
import redis.clients.jedis.Jedis;

public interface RedisConnectionProvider extends Provider {

  Jedis getJedis();
}
