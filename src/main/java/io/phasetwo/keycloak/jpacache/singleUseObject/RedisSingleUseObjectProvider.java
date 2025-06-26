package io.phasetwo.keycloak.jpacache.singleUseObject;

import com.google.common.collect.Maps;
import io.phasetwo.keycloak.jpacache.RedisChangelogTransaction;
import java.util.Map;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.util.Time;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.SingleUseObjectProvider;
import redis.clients.jedis.Jedis;

@JBossLog
public class RedisSingleUseObjectProvider implements SingleUseObjectProvider {
  private final KeycloakSession session;
  private final Jedis jedis;
  private final RedisChangelogTransaction<SingleUseObjectKey, RedisSingleUseObjectAdapter> suoTrx;

  public RedisSingleUseObjectProvider(KeycloakSession session, Jedis jedis) {
    this.jedis = jedis;
    this.session = session;
    this.suoTrx =
        new RedisChangelogTransaction<>(jedis, new SingleUseObjectAdapterSupplier(session, jedis));
    session.getTransactionManager().enlistAfterCompletion(suoTrx);
  }

  @Override
  public void put(String key, long lifespanSeconds, Map<String, String> notes) {
    RedisSingleUseObjectAdapter a = suoTrx.get(new SingleUseObjectKey(key));
    a.setExpiration(Time.currentTimeMillis() + (lifespanSeconds * 1000L));
    replaceNotes(a, notes);
  }

  private void replaceNotes(RedisSingleUseObjectAdapter adapter, Map<String, String> notes) {
    log.debugf("replacing notes for %s with %s", adapter.getName(), notes);
    adapter.replaceNotes(notes);
  }

  @Override
  public Map<String, String> get(String key) {
    RedisSingleUseObjectAdapter a = suoTrx.getIfPresent(new SingleUseObjectKey(key));
    if (a == null) {
      return null;
    } else {
      return a.getNotes();
    }
  }

  @Override
  public Map<String, String> remove(String key) {
    RedisSingleUseObjectAdapter a = suoTrx.getIfPresent(new SingleUseObjectKey(key));
    if (a == null) return null;
    Map<String, String> notes = Maps.newHashMap(a.getNotes()); // TODO need to clone?
    suoTrx.addForDelete(a);
    return notes;
  }

  @Override
  public boolean replace(String key, Map<String, String> notes) {
    RedisSingleUseObjectAdapter a = suoTrx.getIfPresent(new SingleUseObjectKey(key));
    if (a == null) return false;
    replaceNotes(a, notes);
    return true;
  }

  @Override
  public boolean putIfAbsent(String key, long lifespanSeconds) {
    SingleUseObjectKey k = new SingleUseObjectKey(key);
    RedisSingleUseObjectAdapter a = suoTrx.getIfPresent(k);
    if (a != null) {
      return false;
    } else {
      a = suoTrx.get(k);
      a.setExpiration(Time.currentTimeMillis() + (lifespanSeconds * 1000L));
      return true;
    }
  }

  @Override
  public boolean contains(String key) {
    RedisSingleUseObjectAdapter a = suoTrx.getIfPresent(new SingleUseObjectKey(key));
    return (a != null);
  }

  @Override
  public void close() {}
}
