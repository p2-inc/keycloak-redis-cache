package io.phasetwo.keycloak.jpacache.connection;

import org.keycloak.provider.Provider;
import redis.clients.jedis.JedisCluster;

public interface RedisConnectionProvider extends Provider {

    JedisCluster geJedisCluster();
}
