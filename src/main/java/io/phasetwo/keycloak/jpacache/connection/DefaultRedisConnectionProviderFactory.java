package io.phasetwo.keycloak.jpacache.connection;

import com.google.auto.service.AutoService;
import io.phasetwo.keycloak.common.IsSupported;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@JBossLog
@AutoService(RedisConnectionProviderFactory.class)
public class DefaultRedisConnectionProviderFactory
        implements RedisConnectionProviderFactory<RedisConnectionProvider>,
                EnvironmentDependentProviderFactory, IsSupported {
    public static final String PROVIDER_ID = "default";

    private static JedisPool jedisPool;

    @Override
    public RedisConnectionProvider create(KeycloakSession session) {
        return new RedisConnectionProvider() {

            @Override
            public Jedis getJedis() {
                return jedisPool.getResource();
            }

            @Override
            public void close() {}
        };
    }

    @Override
    public void init(Config.Scope scope) {

        log.trace("contactPoint: " + scope.get("contactPoint"));
        String contactPoints = scope.get("contactPoint");

        log.trace("port: " + scope.get("port"));
        int port = Integer.parseInt(scope.get("port"));
        String username = scope.get("username");
        String password = scope.get("password");

        int redisTimeout = 2000; // Connection timeout in milliseconds

        initializePool(contactPoints, port, redisTimeout);
    }

    private static JedisPoolConfig buildPoolConfig() {
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(100);
        poolConfig.setMaxIdle(50);
        poolConfig.setMinIdle(10);
        poolConfig.setMaxWaitMillis(3000);
        poolConfig.setBlockWhenExhausted(true);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setTimeBetweenEvictionRunsMillis(60000);
        poolConfig.setMinEvictableIdleTimeMillis(300000);
        poolConfig.setNumTestsPerEvictionRun(-1);
        return poolConfig;
    }

    // Method to get a Jedis instance from the pool
    public static Jedis getJedis() {
        if (jedisPool == null) {
            throw new IllegalStateException("JedisPool not initialized. Call initializePool() first.");
        }
        return jedisPool.getResource();
    }

    public static void initializePool(String host, int port, int timeout) {
        if (jedisPool == null) {
            JedisPoolConfig poolConfig = buildPoolConfig();
            jedisPool = new JedisPool(poolConfig, host, port, timeout);
            log.info("JedisPool initialized successfully for Redis at " + host + ":" + port);
        }
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public void close() {
        if (jedisPool != null) {
            jedisPool.close();
            jedisPool = null;
        }
    }
}
