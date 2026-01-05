package io.phasetwo.keycloak.jpacache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.jbosslog.JBossLog;
import redis.clients.jedis.JedisPool;

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

  public static void addJedisPoolMetrics(JedisPool jedisPool) {
    // metrics
    Gauge.builder(VENDOR_JEDIS_CONNECTIONS, () -> jedisPool.getNumActive())
        .baseUnit("connections")
        .description("Current Jedis connections to Redis")
        .tag("state", "active")
        .register(Metrics.globalRegistry);
    Gauge.builder(VENDOR_JEDIS_CONNECTIONS, () -> jedisPool.getNumIdle())
        .baseUnit("connections")
        .description("Current Jedis connections to Redis")
        .tag("state", "idle")
        .register(Metrics.globalRegistry);
    Gauge.builder(VENDOR_JEDIS_CONNECTIONS, () -> jedisPool.getNumWaiters())
        .baseUnit("connections")
        .description("Current Jedis connections to Redis")
        .tag("state", "waiters")
        .register(Metrics.globalRegistry);

    Gauge.builder(VENDOR_JEDIS_CONNECTIONS_COUNTS, () -> jedisPool.getBorrowedCount())
        .baseUnit("connections")
        .description("Total connections to Redis")
        .tag("type", "borrowed")
        .register(Metrics.globalRegistry);
    Gauge.builder(VENDOR_JEDIS_CONNECTIONS_COUNTS, () -> jedisPool.getCreatedCount())
        .baseUnit("connections")
        .description("Total connections to Redis")
        .tag("type", "created")
        .register(Metrics.globalRegistry);
    Gauge.builder(VENDOR_JEDIS_CONNECTIONS_COUNTS, () -> jedisPool.getDestroyedCount())
        .baseUnit("connections")
        .description("Total connections to Redis")
        .tag("type", "destroyed")
        .register(Metrics.globalRegistry);

    Gauge.builder(VENDOR_JEDIS_CONNECTIONS_TIME + ".max", () -> jedisPool.getMaxWaitMillis())
        .baseUnit("millisseconds")
        .description("Maximum time in a connection")
        .tag("time", "wait")
        .register(Metrics.globalRegistry);
    Gauge.builder(
            VENDOR_JEDIS_CONNECTIONS_TIME + ".max", () -> jedisPool.getMaxBorrowWaitTimeMillis())
        .baseUnit("millisseconds")
        .description("Maximum time in a connection")
        .tag("time", "borrow-wait")
        .register(Metrics.globalRegistry);

    Gauge.builder(
            VENDOR_JEDIS_CONNECTIONS_TIME + ".mean", () -> jedisPool.getMeanActiveTimeMillis())
        .baseUnit("millisseconds")
        .description("Mean time in a connection")
        .tag("time", "active")
        .register(Metrics.globalRegistry);
    Gauge.builder(VENDOR_JEDIS_CONNECTIONS_TIME + ".mean", () -> jedisPool.getMeanIdleTimeMillis())
        .baseUnit("millisseconds")
        .description("Mean time in a connection")
        .tag("time", "idle")
        .register(Metrics.globalRegistry);
    Gauge.builder(
            VENDOR_JEDIS_CONNECTIONS_TIME + ".mean", () -> jedisPool.getMeanBorrowWaitTimeMillis())
        .baseUnit("millisseconds")
        .description("Mean time in a connection")
        .tag("time", "borrow-wait")
        .register(Metrics.globalRegistry);
  }
}
