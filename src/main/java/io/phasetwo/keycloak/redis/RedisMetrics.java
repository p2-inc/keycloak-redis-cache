package io.phasetwo.keycloak.redis;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import java.util.function.ToDoubleFunction;
import lombok.extern.jbosslog.JBossLog;
import redis.clients.jedis.util.Pool;

/** Helper for metrics */
@JBossLog
public class RedisMetrics {

  private static final String VENDOR_JEDIS = "vendor.jedis.";
  private static final String VENDOR_JEDIS_CONNECTIONS = VENDOR_JEDIS + "connections";
  private static final String VENDOR_JEDIS_CONNECTIONS_COUNTS = VENDOR_JEDIS + "connections.counts";
  private static final String VENDOR_JEDIS_CONNECTIONS_TIME = VENDOR_JEDIS + "connections.time";
  private static final String VENDOR_JEDIS_CACHE = VENDOR_JEDIS + "cache.";

  public static final String CACHE_TAG = "cache";
  public static final String OPERATION_TAG = "op";

  private static final Meter.MeterProvider<Counter> counterProvider =
      Counter.builder(VENDOR_JEDIS_CACHE)
          .description("Cache operation counters")
          .baseUnit("operations")
          .withRegistry(Metrics.globalRegistry);

  public static Meter.MeterProvider<Counter> getCacheCounterProvider() {
    return counterProvider;
  }

  public static void addJedisPoolMetrics(Pool<?> jedisPool) {
    addJedisPoolMetrics(jedisPool, null);
  }

  public static void addJedisPoolMetrics(Pool<?> jedisPool, String nodeTag) {
    // metrics
    gauge(VENDOR_JEDIS_CONNECTIONS, jedisPool, Pool::getNumActive, nodeTag)
        .baseUnit("connections")
        .description("Current Jedis connections to Redis")
        .tag("state", "active")
        .register(Metrics.globalRegistry);
    gauge(VENDOR_JEDIS_CONNECTIONS, jedisPool, Pool::getNumIdle, nodeTag)
        .baseUnit("connections")
        .description("Current Jedis connections to Redis")
        .tag("state", "idle")
        .register(Metrics.globalRegistry);
    gauge(VENDOR_JEDIS_CONNECTIONS, jedisPool, Pool::getNumWaiters, nodeTag)
        .baseUnit("connections")
        .description("Current Jedis connections to Redis")
        .tag("state", "waiters")
        .register(Metrics.globalRegistry);

    gauge(VENDOR_JEDIS_CONNECTIONS_COUNTS, jedisPool, Pool::getBorrowedCount, nodeTag)
        .baseUnit("connections")
        .description("Total connections to Redis")
        .tag("type", "borrowed")
        .register(Metrics.globalRegistry);
    gauge(VENDOR_JEDIS_CONNECTIONS_COUNTS, jedisPool, Pool::getCreatedCount, nodeTag)
        .baseUnit("connections")
        .description("Total connections to Redis")
        .tag("type", "created")
        .register(Metrics.globalRegistry);
    gauge(VENDOR_JEDIS_CONNECTIONS_COUNTS, jedisPool, Pool::getDestroyedCount, nodeTag)
        .baseUnit("connections")
        .description("Total connections to Redis")
        .tag("type", "destroyed")
        .register(Metrics.globalRegistry);

    gauge(VENDOR_JEDIS_CONNECTIONS_TIME + ".max", jedisPool, Pool::getMaxWaitMillis, nodeTag)
        .baseUnit("millisseconds")
        .description("Maximum time in a connection")
        .tag("time", "wait")
        .register(Metrics.globalRegistry);
    gauge(
            VENDOR_JEDIS_CONNECTIONS_TIME + ".max",
            jedisPool,
            Pool::getMaxBorrowWaitTimeMillis,
            nodeTag)
        .baseUnit("millisseconds")
        .description("Maximum time in a connection")
        .tag("time", "borrow-wait")
        .register(Metrics.globalRegistry);

    gauge(
            VENDOR_JEDIS_CONNECTIONS_TIME + ".mean",
            jedisPool,
            Pool::getMeanActiveTimeMillis,
            nodeTag)
        .baseUnit("millisseconds")
        .description("Mean time in a connection")
        .tag("time", "active")
        .register(Metrics.globalRegistry);
    gauge(VENDOR_JEDIS_CONNECTIONS_TIME + ".mean", jedisPool, Pool::getMeanIdleTimeMillis, nodeTag)
        .baseUnit("millisseconds")
        .description("Mean time in a connection")
        .tag("time", "idle")
        .register(Metrics.globalRegistry);
    gauge(
            VENDOR_JEDIS_CONNECTIONS_TIME + ".mean",
            jedisPool,
            Pool::getMeanBorrowWaitTimeMillis,
            nodeTag)
        .baseUnit("millisseconds")
        .description("Mean time in a connection")
        .tag("time", "borrow-wait")
        .register(Metrics.globalRegistry);
  }

  private static Gauge.Builder<Pool<?>> gauge(
      String name, Pool<?> pool, ToDoubleFunction<Pool<?>> valueFunction, String nodeTag) {
    Gauge.Builder<Pool<?>> builder = Gauge.builder(name, pool, valueFunction);
    if (nodeTag != null && !nodeTag.isBlank()) {
      builder.tag("node", nodeTag);
    }
    return builder;
  }
}
