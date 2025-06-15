package io.phasetwo.keycloak.jpacache.userSession;


import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.phasetwo.keycloak.jpacache.MapEntity;
import java.util.Map;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserSessionModel;

@JBossLog
public class RedisAuthenticatedClientSessionAdapter extends MapEntity<AuthenticatedClientSessionKey>
    implements AuthenticatedClientSessionModel {

  private final KeycloakSession session;
  //  private final Jedis jedis;

  private Map<String, String> notes;

  public RedisAuthenticatedClientSessionAdapter(KeycloakSession session, String id) {
    this(session, id, null);
  }

  public RedisAuthenticatedClientSessionAdapter(
      KeycloakSession session, String id, Map<String, String> existingData) {
    super(new AuthenticatedClientSessionKey(id), existingData);
    this.session = session;
    setField("id", id);
  }

  @Override
  public Map<String, String> getSecondaryIndexes() {
    ImmutableMap.Builder<String, String> b = ImmutableMap.builder();
    b.put(String.format("authenticated-client:client-index:%s", getClientUuid()), getKey().key());
    return b.build();
  }

  @Override
  public void detachFromUserSession() {
    // todo
  }

  @Override
  public UserSessionModel getUserSession() {
    // todo
    return null;
  }

  @Override
  public String getId() {
    return getString("id");
  }

  @Override
  public void setNote(String name, String value) {
    getNotes().put(name, value);
    mapToField(notes, "notes");
  }

  public void setNotes(Map<String, String> notes) {
    this.notes = Maps.newHashMap(notes);
    mapToField(notes, "notes");
  }

  @Override
  public Map<String, String> getNotes() {
    if (notes == null) {
      notes = mapFromField("notes");
    }
    return notes;
  }

  @Override
  public String getNote(String name) {
    return getNotes().get(name);
  }

  @Override
  public void removeNote(String name) {
    getNotes().remove(name);
    mapToField(notes.isEmpty() ? null : notes, "notes");
  }

  @Override
  public void setTimestamp(int timestamp) {
    setField("timestamp", timestamp);
  }

  @Override
  public int getTimestamp() {
    return getInt("timestamp", 0);
  }

  // Re: refresh token fields
  // might be good to store these as fields, instead of using defaults, which use notes
  // as they will get updated frequently and will benefit from partial field update, rather
  // than having to serialize the whole note map

  @Override
  public void setRefreshToken(String reuseId, String refreshToken) {
    setField(refreshTokenKey("refreshToken", reuseId), refreshToken);
  }

  @Override
  public String getRefreshToken(String reuseId) {
    return getString(refreshTokenKey("refreshToken", reuseId));
  }

  @Override
  public void setRefreshTokenUseCount(String reuseId, int refreshTokenUseCount) {
    setField(refreshTokenKey("refreshTokenUseCount", reuseId), refreshTokenUseCount);
  }

  @Override
  public int getRefreshTokenUseCount(String reuseId) {
    return getInt(refreshTokenKey("refreshTokenUseCount", reuseId), 0);
  }

  @Override
  public void setRefreshTokenLastRefresh(String reuseId, int refreshTokenLastRefresh) {
    setField(refreshTokenKey("refreshTokenLastRefresh", reuseId), refreshTokenLastRefresh);
  }

  @Override
  public int getRefreshTokenLastRefresh(String reuseId) {
    return getInt(refreshTokenKey("refreshTokenLastRefresh", reuseId), 0);
  }

  static String refreshTokenKey(String key, String reuseId) {
    return String.format("%s:%s", key, reuseId);
  }

  // end: refresh token fields

  @Override
  public RealmModel getRealm() {
    return session.realms().getRealm(getString("realmId"));
  }

  public void setRealmId(String realmId) {
    setField("realmId", realmId);
  }

  @Override
  public ClientModel getClient() {
    return getRealm().getClientById(getClientUuid());
  }

  public void setClientUuid(String uuid) {
    setField("clientUuid", uuid);
  }

  public String getClientUuid() {
    return getString("clientUuid");
  }

  @Override
  public String getRedirectUri() {
    return getString("redirectUri");
  }

  @Override
  public void setRedirectUri(String uri) {
    setField("redirectUri", uri);
  }

  @Override
  public String getAction() {
    return getString("action");
  }

  @Override
  public void setAction(String action) {
    setField("action", action);
  }

  @Override
  public String getProtocol() {
    return getString("protocol");
  }

  @Override
  public void setProtocol(String protocol) {
    setField("protocol", protocol);
  }
}
