package io.phasetwo.keycloak.jpacache;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.jbosslog.JBossLog;
import redis.clients.jedis.AbstractTransaction;
import redis.clients.jedis.Response;
import redis.clients.jedis.UnifiedJedis;

/** */
@JBossLog
public final class RedisHashCas {

  public static final long NON_NUMERIC_RESPONSE_CODE = Long.MIN_VALUE;

  private static final String LUA_SCRIPT =
"""
-- KEYS[1] = hash key
-- ARGV[1] = expected version
-- ARGV[2] = expiration timestamp in ms (0 or empty = no expiration)
-- ARGV[3..n] = field/value pairs

local currentVersion = redis.call("HGET", KEYS[1], "version")

-- CREATE path
if not currentVersion then
   if ARGV[1] ~= "0" then
      return -1 -- cannot create unless expectedVersion == 0
   end
   currentVersion = "0" -- normalize to string
end

-- CAS check
if currentVersion ~= ARGV[1] then
   return 0 -- version mismatch
end

-- Must have at least one field/value pair
if (#ARGV - 2) < 2 then
   return -2
end

-- Apply updates
redis.call("HSET", KEYS[1], unpack(ARGV, 3))

-- Increment version
redis.call("HINCRBY", KEYS[1], "version", 1)

-- Optional expiration
local expireAt = ARGV[2]
if expireAt and expireAt ~= "" and expireAt ~= "0" then
   -- redis.call("PEXPIREAT", KEYS[1], tonumber(expireAt))
end

return 1
        """;

  private static volatile String scriptSha;

  private final AbstractTransaction txn;

  public static final class CasInvocation {
    private final Response<Object> response;
    private final String key;
    private final long expectedVersion;
    private final Long expireAtMs;
    private final Map<String, String> updates;

    private CasInvocation(
        Response<Object> response,
        String key,
        long expectedVersion,
        Long expireAtMs,
        Map<String, String> updates) {
      this.response = response;
      this.key = key;
      this.expectedVersion = expectedVersion;
      this.expireAtMs = expireAtMs;
      this.updates = new LinkedHashMap<>(updates);
    }

    public long getResponseCode() {
      Object result = getRawResultSafely();
      if (result instanceof Number) {
        return ((Number) result).longValue();
      }
      return NON_NUMERIC_RESPONSE_CODE;
    }

    @Override
    public String toString() {
      return String.format(
          "key=%s expectedVersion=%d expireAtMs=%s updates=%s rawResult=%s",
          key, expectedVersion, expireAtMs, updates, getRawResultSafely());
    }

    private Object getRawResultSafely() {
      try {
        return response.get();
      } catch (RuntimeException ex) {
        return "error reading response: " + ex.getMessage();
      }
    }
  }

  /** Used inside an existing MULTI/EXEC block. */
  public RedisHashCas(AbstractTransaction txn) {
    this.txn = Objects.requireNonNull(txn, "transaction");
  }

  /** Initializes the Lua script and caches the SHA. Call once at startup. */
  public static void initialize(UnifiedJedis client) {
    Objects.requireNonNull(client, "client");
    scriptSha = client.scriptLoad(LUA_SCRIPT);
    log.debugf("script initialized: %s", scriptSha);
  }

  /**
   * CAS + HSETEX
   *
   * @param key Redis hash key
   * @param expectedVersion expected version for CAS
   * @param expireAtMs expiration timestamp (ms since epoch), or null
   * @param updates map of field/value updates
   */
  public CasInvocation hsetex(
      String key, long expectedVersion, Long expireAtMs, Map<String, String> updates) {
    if (scriptSha == null) {
      throw new IllegalStateException("RedisHashCas.initialize() not called");
    }

    List<String> keys = List.of(key);
    List<String> args = new ArrayList<>();

    args.add(String.valueOf(expectedVersion));
    args.add(expireAtMs != null ? String.valueOf(expireAtMs) : "0");

    updates.forEach(
        (field, value) -> {
          args.add(field);
          args.add(value == null ? MapEntity. NULL_SENTINEL :value);
        });

    // Queue Lua execution inside the transaction
    log.tracef(
        "[redis] (lua CAS version:%d) (exp:%d) HSET %s %s",
        expectedVersion, expireAtMs, key, updates);
    Response<Object> response = txn.evalsha(scriptSha, keys, args);
    return new CasInvocation(response, key, expectedVersion, expireAtMs, updates);
  }
}
