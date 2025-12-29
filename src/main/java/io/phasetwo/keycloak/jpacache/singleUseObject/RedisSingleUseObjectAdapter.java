package io.phasetwo.keycloak.jpacache.singleUseObject;

import com.google.common.collect.ImmutableMap;
import io.phasetwo.keycloak.common.ExpirableEntity;
import io.phasetwo.keycloak.jpacache.MapEntity;
import java.util.Map;
import org.keycloak.models.KeycloakSession;

public class RedisSingleUseObjectAdapter extends MapEntity<SingleUseObjectKey>
    implements ExpirableEntity {

  private final KeycloakSession session;

  public RedisSingleUseObjectAdapter(KeycloakSession session, String name) {
    this(session, name, null);
  }

  public RedisSingleUseObjectAdapter(
      KeycloakSession session, String name, Map<String, String> existingData) {
    super(new SingleUseObjectKey(name), existingData);
    this.session = session;
    setField("name", name);
  }

  @Override
  public Map<String, String> getSecondaryIndexes() {
    // ImmutableMap.Builder<String, String> b = ImmutableMap.builder();
    // siPut(b, , getKey().key());
    // b.put(String.format("login-failure:index:%s", getRealmId()), getUserId());
    // return b.build();
    return ImmutableMap.of();
  }

  public String getName() {
    return getString("name");
  }

  public Map<String, String> getNotes() {
    return getMap("notes");
  }

  public void replaceNotes(Map<String, String> notes) {
    Map<String, String> ns = getNotes();
    ns.clear();
    for (Map.Entry<String, String> note : notes.entrySet()) {
      ns.put(note.getKey(), note.getValue() == null ? NULL_SENTINEL : note.getValue());
    }
  }

  @Override
  public Long getExpiration() {
    if (isNull("expiration")) return null;
    return getLong("expiration", 0L);
  }

  @Override
  public void setExpiration(Long expiration) {
    setField("expiration", expiration);
  }
}
