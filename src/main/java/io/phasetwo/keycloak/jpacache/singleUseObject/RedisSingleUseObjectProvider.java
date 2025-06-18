package io.phasetwo.keycloak.jpacache.singleUseObject;

import com.google.common.collect.Maps;
import io.phasetwo.keycloak.jpacache.RedisChangelogTransaction;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.util.Time;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.SingleUseObjectProvider;
import redis.clients.jedis.Jedis;

@JBossLog
public class RedisSingleUseObjectProvider implements SingleUseObjectProvider {
  private final KeycloakSession session;
  private final Jedis jedis;
  private final RedisChangelogTransaction<SingleUseObjectKey, RedisSingleUseObjectAdapter> sloTrx;

  private static final String NULL_SENTINEL = "<null>";

  public RedisSingleUseObjectProvider(KeycloakSession session, Jedis jedis) {
    this.jedis = jedis;
    this.session = session;
    this.sloTrx =
        new RedisChangelogTransaction<>(jedis, new SingleUseObjectAdapterSupplier(session, jedis));
    session.getTransactionManager().enlistAfterCompletion(sloTrx);
  }

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
    // log.debugf("[redis] HSET %s %s", key, notes);
    // jedis.hset(key, stripNulls(notes));
    RedisSingleUseObjectAdapter a = sloTrx.get(new SingleUseObjectKey(key));
    a.setExpiration(Time.currentTimeMillis() + (lifespanSeconds * 1000L));
    replaceNotes(a, notes);
  }

  private void replaceNotes(RedisSingleUseObjectAdapter adapter, Map<String, String> notes) {
    Map<String, String> ns = adapter.getNotes();
    ns.clear();
    for (Map.Entry<String, String> note : notes.entrySet()) {
      ns.put(note.getKey(), note.getValue() == null ? NULL_SENTINEL : note.getValue());
    }
  }

  @Override
  public Map<String, String> get(String key) {
    RedisSingleUseObjectAdapter a = sloTrx.getIfPresent(new SingleUseObjectKey(key));
    if (a == null) {
      return null;
    } else {
      return convertNulls(a.getNotes());
    }
  }

  @Override
  public Map<String, String> remove(String key) {
    RedisSingleUseObjectAdapter a = sloTrx.getIfPresent(new SingleUseObjectKey(key));
    if (a == null) return null;
    Map<String, String> notes = a.getNotes();
    Map<String, String> ns = notes == null ? null : convertNulls(notes);
    sloTrx.addForDelete(a);
    return ns;
  }

  @Override
  public boolean replace(String key, Map<String, String> notes) {
    RedisSingleUseObjectAdapter a = sloTrx.getIfPresent(new SingleUseObjectKey(key));
    if (a == null) return false;
    replaceNotes(a, notes);
    return true;
  }

  @Override
  public boolean putIfAbsent(String key, long lifespanSeconds) {
    SingleUseObjectKey k = new SingleUseObjectKey(key);
    RedisSingleUseObjectAdapter a = sloTrx.getIfPresent(k);
    if (a != null) {
      return false;
    } else {
      a = sloTrx.get(k);
      a.setExpiration(Time.currentTimeMillis() + (lifespanSeconds * 1000L));
      return true;
    }
  }

  @Override
  public boolean contains(String key) {
    RedisSingleUseObjectAdapter a = sloTrx.getIfPresent(new SingleUseObjectKey(key));
    return (a != null);
  }

  @Override
  public void close() {}
}
