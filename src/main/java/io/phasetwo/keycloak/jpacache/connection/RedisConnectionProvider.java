package io.phasetwo.keycloak.jpacache.connection;

import org.keycloak.provider.Provider;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public interface RedisConnectionProvider extends Provider {
  JedisPool getPool();
  Jedis getJedis();
}
