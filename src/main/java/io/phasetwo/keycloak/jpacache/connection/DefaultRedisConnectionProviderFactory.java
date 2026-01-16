package io.phasetwo.keycloak.jpacache.connection;

import static io.phasetwo.keycloak.jpacache.RedisMetrics.*;

import com.google.auto.service.AutoService;
import io.phasetwo.keycloak.common.IsSupported;
import io.phasetwo.keycloak.jpacache.RedisHashCas;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.RedisClusterClient;

@JBossLog
@AutoService(RedisConnectionProviderFactory.class)
public class DefaultRedisConnectionProviderFactory
    implements RedisConnectionProviderFactory<RedisConnectionProvider>,
        EnvironmentDependentProviderFactory,
        IsSupported {
  public static final String PROVIDER_ID = "default";

  private static String host;
  private static int port;

  private static JedisPool jedisPool;

  @Override
  public RedisConnectionProvider create(KeycloakSession session) {
    return new RedisConnectionProvider() {

      private Jedis resource;

      @Override
      public JedisPool getPool() {
        return jedisPool;
      }

      @Override
      public Jedis getJedis() {
        resource = jedisPool.getResource();
        return resource;
      }

      @Override
      public void close() {
        try {
          if (resource != null) {
            resource.close();
          }
        } catch (Exception e) {
          log.warn("Error closing jedis resource", e);
        }
      }
    };
  }

  @Override
  public void init(Config.Scope scope) {
    log.trace("contactPoint: " + scope.get("contactPoint"));
    host = scope.get("contactPoint");

    log.trace("port: " + scope.get("port"));
    port = Integer.parseInt(scope.get("port"));
    String username = scope.get("username");
    String password = scope.get("password");

    int redisTimeout = 2000; // Connection timeout in milliseconds

    initializePool(host, port, redisTimeout);

    RedisHashCas.initialize(jedisPool);

    addJedisPoolMetrics(jedisPool);
  }

  private static JedisPoolConfig buildPoolConfig() {
    final JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxTotal(100);
    poolConfig.setMaxIdle(50);
    poolConfig.setMinIdle(10);
    poolConfig.setMaxWaitMillis(3000);
    poolConfig.setBlockWhenExhausted(true);
    // may be the issue with the subscriber
    // poolConfig.setTestOnBorrow(true);
    poolConfig.setTestOnBorrow(false);
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
