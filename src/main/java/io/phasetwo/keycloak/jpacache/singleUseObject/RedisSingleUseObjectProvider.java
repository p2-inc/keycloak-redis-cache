package io.phasetwo.keycloak.jpacache.singleUseObject;


import com.google.common.collect.Maps;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.SingleUseObjectProvider;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.HSetExParams;

@JBossLog
@RequiredArgsConstructor
public class RedisSingleUseObjectProvider implements SingleUseObjectProvider {
  private final KeycloakSession session;

  private final Jedis jedis;

  private static final String NULL_SENTINEL = "<null>";

  /** Replace null values in the input map with a sentinel string. */
  public static Map<String, String> stripNulls(Map<String, String> input) {
    if (input == null || input.isEmpty()) return input;
    return input.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, e -> e.getValue() == null ? NULL_SENTINEL : e.getValue()));
  }

  /** Replace sentinel values in the map with actual nulls. */
  public static Map<String, String> convertNulls(Map<String, String> input) {
    if (input == null || input.isEmpty()) return input;
    Map<String, String> output = Maps.newHashMap();
    for (Map.Entry<String, String> entry : input.entrySet()) {
      if (entry.getValue() == null) {
        output.put(entry.getKey(), null); // null is allowed here
      } else if ("<null>".equals(entry.getValue())) {
        output.put(entry.getKey(), null);
      } else {
        output.put(entry.getKey(), entry.getValue());
      }
    }
    return output;
  }

  @Override
  public void put(String key, long lifespanSeconds, Map<String, String> notes) {
    log.debugf("[redis] HSET %s %s", key, notes);
    jedis.hset(key, stripNulls(notes));
  }

  @Override
  public Map<String, String> get(String key) {
    log.debugf("[redis] HGETALL %s", key);
    return convertNulls(jedis.hgetAll(key));
  }

  @Override
  public Map<String, String> remove(String key) {
    log.debugf("[redis] HGETALL %s", key);
    var m = jedis.hgetAll(key);
    log.debugf("[redis] DEL %s", key);
    jedis.del(key);
    return convertNulls(m);
  }

  @Override
  public boolean replace(String key, Map<String, String> notes) {
    log.debugf("[redis] HSETEX FXX %s %s", key, notes);
    long result = jedis.hsetex(key, HSetExParams.hSetExParams().fxx(), stripNulls(notes));
    return result > 0;
  }

  @Override
  public boolean putIfAbsent(String key, long lifespanSeconds) {
    log.debugf("[redis] HSETEX FNX %s", key);
    long result = jedis.hsetex(key, HSetExParams.hSetExParams().fnx(), Map.of());
    return result > 0;
  }

  @Override
  public boolean contains(String key) {
    log.debugf("[redis] EXISTS %s", key);
    return jedis.exists(key);
  }

  @Override
  public void close() {
    // jedis.close(); needs to be closed in order to return to the pool.
    // Check RedisConnectionProvider.close() method
  }
}
