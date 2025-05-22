package io.phasetwo.keycloak.jpacache.singleUseObject;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;

import java.util.Map;

import io.phasetwo.keycloak.jpacache.connection.RedisConnectionProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.SingleUseObjectProvider;
import redis.clients.jedis.JedisCluster;

@JBossLog
@RequiredArgsConstructor
public class RedisCacheSingleUseObjectProvider implements SingleUseObjectProvider {
  private final KeycloakSession session;
  private final JedisCluster jedisClusterCluster;

  @Override
  public void put(String key, long lifespanSeconds, Map<String, String> notes) {
    log.tracef("put(%s)%s", key, getShortStackTrace());

  }


  @Override
  public Map<String, String> get(String key) {
    return null;
  }

  @Override
  public Map<String, String> remove(String key) {
    return null;
  }

  @Override
  public boolean replace(String key, Map<String, String> notes) {
    log.tracef("replace(%s)%s", key, getShortStackTrace());
    return false;
  }

  @Override
  public boolean putIfAbsent(String key, long lifespanSeconds) {
    log.tracef("putIfAbsent(%s)%s", key, getShortStackTrace());
    return false;
  }

  @Override
  public boolean contains(String key) {
    return false;
  }

  @Override
  public void close() {
    // Nothing to do
  }
}
