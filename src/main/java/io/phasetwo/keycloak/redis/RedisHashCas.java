package io.phasetwo.keycloak.redis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.jbosslog.JBossLog;
import redis.clients.jedis.AbstractTransaction;
import redis.clients.jedis.Response;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.exceptions.JedisNoScriptException;

/** */
@JBossLog
public final class RedisHashCas {

  public static final long NON_NUMERIC_RESPONSE_CODE = Long.MIN_VALUE;

  private static final String LUA_SCRIPT =
"""
-- KEYS[1] = hash key
-- ARGV[1] = expected version
-- ARGV[2] = expiration timestamp in ms (0 or empty = no expiration)
-- ARGV[3] = number of field/value pair arguments
-- ARGV[4..n] = field/value pairs, then delete count, then delete fields

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
if tonumber(ARGV[3]) == nil then
   return -2
end

local updateArgCount = tonumber(ARGV[3])
local updateStart = 4
local updateEnd = updateStart + updateArgCount - 1

-- Apply updates
if updateArgCount > 0 then
   redis.call("HSET", KEYS[1], unpack(ARGV, updateStart, updateEnd))
end

local deleteCountIndex = updateEnd + 1
local deleteCount = tonumber(ARGV[deleteCountIndex])
if deleteCount == nil then
   return -2
end

if deleteCount > 0 then
   local deleteStart = deleteCountIndex + 1
   local deleteEnd = deleteStart + deleteCount - 1
   redis.call("HDEL", KEYS[1], unpack(ARGV, deleteStart, deleteEnd))
end

-- Increment version
redis.call("HINCRBY", KEYS[1], "version", 1)

-- Optional expiration
local expireAt = ARGV[2]
if expireAt and expireAt ~= "" and expireAt ~= "0" then
   redis.call("PEXPIREAT", KEYS[1], tonumber(expireAt))
end

return 1
        """;

  private static volatile String scriptSha;

  private final AbstractTransaction txn;
  private final UnifiedJedis client;

  public static final class CasInvocation {
    private final Response<Object> response;
    private final Object immediateResult;
    private final String key;
    private final long expectedVersion;
    private final Long expireAtMs;
    private final Map<String, String> updates;

    private CasInvocation(
        Response<Object> response,
        Object immediateResult,
        String key,
        long expectedVersion,
        Long expireAtMs,
        Map<String, String> updates) {
      this.response = response;
      this.immediateResult = immediateResult;
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
      if (response == null) {
        return immediateResult;
      }
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
    this.client = null;
  }

  /** Used for immediate execution outside a MULTI/EXEC block. */
  public RedisHashCas(UnifiedJedis client) {
    this.txn = null;
    this.client = Objects.requireNonNull(client, "client");
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
   * @param deletedFields hash fields to delete atomically with the write
   */
  public CasInvocation hsetex(
      String key,
      long expectedVersion,
      Long expireAtMs,
      Map<String, String> updates,
      Set<String> deletedFields) {
    if (scriptSha == null) {
      throw new IllegalStateException("RedisHashCas.initialize() not called");
    }

    List<String> keys = List.of(key);
    List<String> args = new ArrayList<>();

    args.add(String.valueOf(expectedVersion));
    args.add(expireAtMs != null ? String.valueOf(expireAtMs) : "0");
    args.add(String.valueOf(updates.size() * 2));

    updates.forEach(
        (field, value) -> {
          args.add(field);
          args.add(value == null ? MapEntity.NULL_SENTINEL : value);
        });
    args.add(String.valueOf(deletedFields.size()));
    args.addAll(deletedFields);

    log.tracef(
        "[redis] (lua CAS version:%d) (exp:%d) HSET/HDEL %s updates=%s deletes=%s",
        expectedVersion, expireAtMs, key, updates, deletedFields);

    if (txn != null) {
      Response<Object> response = txn.evalsha(scriptSha, keys, args);
      return new CasInvocation(response, null, key, expectedVersion, expireAtMs, updates);
    }

    Object result = evalshaResilient(keys, args);
    return new CasInvocation(null, result, key, expectedVersion, expireAtMs, updates);
  }

  /**
   * Runs the CAS script via EVALSHA, recovering from an empty script cache.
   *
   * <p>Redis does not replicate the script cache (SCRIPT LOAD) to replicas, only the effects of a
   * script's execution. After a Sentinel failover the promoted master therefore has no cached
   * script and EVALSHA fails with NOSCRIPT. We fall back to EVAL with the full body: it executes
   * and caches the script on the current master. Because the cache key is SHA1(body), it is cached
   * under the same {@link #scriptSha} we already hold, so subsequent EVALSHA calls succeed without
   * reloading.
   */
  private Object evalshaResilient(List<String> keys, List<String> args) {
    try {
      return client.evalsha(scriptSha, keys, args);
    } catch (JedisNoScriptException e) {
      log.warn(
          "[redis] EVALSHA returned NOSCRIPT (script cache empty, likely after a Sentinel"
              + " failover). Falling back to EVAL to reload the script on the current master.");
      return client.eval(LUA_SCRIPT, keys, args);
    }
  }

  public CasInvocation hsetex(
      String key, long expectedVersion, Long expireAtMs, Map<String, String> updates) {
    return hsetex(key, expectedVersion, expireAtMs, updates, Set.of());
  }
}
