package io.phasetwo.keycloak.jpacache.singleUseObject;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.SingleUseObjectProvider;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;

@JBossLog
@RequiredArgsConstructor
public class RedisCacheSingleUseObjectProvider implements SingleUseObjectProvider {
  private final KeycloakSession session;
  private final Jedis jedis;

  @Override
  public void put(String key, long lifespanSeconds, Map<String, String> notes) {
    log.tracef("put(%s)%s", key, notes);
    jedis.hset(key, notes);
  }


  @Override
  public Map<String, String> get(String key) {
    log.tracef("get(%s)%s", key, getShortStackTrace());
    return jedis.hgetAll(key);
  }

  @Override
  public Map<String, String> remove(String key) {
    var map = jedis.hgetAll(key);
    jedis.del(key);
    return map;
  }

  @Override
  public boolean replace(String key, Map<String, String> notes) {
    log.tracef("replace(%s)%s", key, getShortStackTrace());
    jedis.del(key);
    var result = jedis.hset(key, notes);

    return result > 0;
  }

  @Override
  public boolean putIfAbsent(String key, long lifespanSeconds) {
    log.tracef("putIfAbsent(%s)%s", key, getShortStackTrace());
    if (jedis.exists(key)) {
      return false;
    }
 // not available yet
    return true;
  }

  @Override
  public boolean contains(String key) {
    return jedis.exists(key);
  }

  @Override
  public void close() {
    // Nothing to do
  }
}
