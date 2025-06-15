package io.phasetwo.keycloak.jpacache.userSession;


import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.phasetwo.keycloak.jpacache.MapEntity;
import io.phasetwo.keycloak.jpacache.RedisChangelogTransaction;
import java.util.Collection;
import java.util.Map;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import redis.clients.jedis.Jedis;

@JBossLog
public class RedisUserSessionAdapter extends MapEntity<UserSessionKey> implements UserSessionModel {

  private final KeycloakSession session;
  private final Jedis jedis;
  private final RedisChangelogTransaction<
          AuthenticatedClientSessionKey, RedisAuthenticatedClientSessionAdapter>
      clientSessionTrx;

  private Map<String, RedisAuthenticatedClientSessionAdapter> clientSessions = Maps.newHashMap();
  private boolean clientSessionsInitialized = false;
  private Map<String, String> notes;

  public RedisUserSessionAdapter(
      KeycloakSession session,
      Jedis jedis,
      RedisChangelogTransaction<
              AuthenticatedClientSessionKey, RedisAuthenticatedClientSessionAdapter>
          clientSessionTrx,
      String id) {
    this(session, jedis, clientSessionTrx, id, null);
  }

  public RedisUserSessionAdapter(
      KeycloakSession session,
      Jedis jedis,
      RedisChangelogTransaction<
              AuthenticatedClientSessionKey, RedisAuthenticatedClientSessionAdapter>
          clientSessionTrx,
      String id,
      Map<String, String> existingData) {
    super(new UserSessionKey(id), existingData);
    this.session = session;
    this.jedis = jedis;
    this.clientSessionTrx = clientSessionTrx;
    setField("id", id);
  }

  @Override
  public Map<String, String> getSecondaryIndexes() {
    ImmutableMap.Builder<String, String> b = ImmutableMap.builder();
    b.put(String.format("user-session:realm-index:%s", getRealmId()), getKey().key());
    b.put(String.format("user-session:user-index:%s", getUserId()), getKey().key());
    b.put(String.format("user-session:broker-user-index:%s", getBrokerUserId()), getKey().key());
    b.put(
        String.format("user-session:broker-session-index:%s", getBrokerSessionId()),
        getKey().key());
    return b.build();
  }

  @Override
  public Map<String, AuthenticatedClientSessionModel> getAuthenticatedClientSessions() {
    // todo
    return null;
  }

  @Override
  public int getStarted() {
    return getInt("started", 0);
  }

  public void setStarted(int started) {
    setField("started", started);
  }

  @Override
  public boolean isOffline() {
    return getBool("offline", false);
  }

  public void setOffline(boolean offline) {
    setField("offline", offline);
  }

  @Override
  public boolean isRememberMe() {
    return getBool("rememberMe", false);
  }

  public void setRememberMe(boolean rememberMe) {
    setField("rememberMe", rememberMe);
  }

  @Override
  public UserModel getUser() {
    return session.users().getUserById(getRealm(), getUserId());
  }

  public String getUserId() {
    return getString("userId");
  }

  public void setUserId(String userId) {
    setField("userId", userId);
  }

  @Override
  public RealmModel getRealm() {
    return session.realms().getRealm(getRealmId());
  }

  public String getRealmId() {
    return getString("realmId");
  }

  public void setRealmId(String realmId) {
    setField("realmId", realmId);
  }

  @Override
  public String getId() {
    return getString("id");
  }

  @Override
  public String getAuthMethod() {
    return getString("authMethod");
  }

  public void setAuthMethod(String authMethod) {
    setField("authMethod", authMethod);
  }

  @Override
  public String getBrokerSessionId() {
    return getString("brokerSessionId");
  }

  public void setBrokerSessionId(String brokerSessionId) {
    setField("brokerSessionId", brokerSessionId);
  }

  @Override
  public String getBrokerUserId() {
    return getString("brokerUserId");
  }

  public void setBrokerUserId(String brokerUserId) {
    setField("brokerUserId", brokerUserId);
  }

  @Override
  public String getIpAddress() {
    return getString("ipAddress");
  }

  public void setIpAddress(String ipAddress) {
    setField("ipAddress", ipAddress);
  }

  @Override
  public String getLoginUsername() {
    return getString("loginUsername");
  }

  public void setLoginUsername(String loginUsername) {
    setField("loginUsername", loginUsername);
  }

  @Override
  public void setLastSessionRefresh(int lastSessionRefresh) {
    setField("lastSessionRefresh", lastSessionRefresh);
  }

  @Override
  public int getLastSessionRefresh() {
    return getInt("lastSessionRefresh", 0);
  }

  public void setExpiration(int expiration) {
    setField("expiration", expiration);
  }

  public int getExpiration() {
    return getInt("expiration", 0);
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
  public UserSessionModel.SessionPersistenceState getPersistenceState() {
    String psStr = getString("persistenceState");
    if (psStr != null) return UserSessionModel.SessionPersistenceState.valueOf(psStr);
    else return null;
  }

  @Override
  public UserSessionModel.State getState() {
    String stateStr = getString("state");
    if (stateStr != null) return UserSessionModel.State.valueOf(stateStr);
    else return null;
  }

  @Override
  public void setState(UserSessionModel.State state) {
    setField("state", state.name());
  }

  @Override
  public void restartSession(
      RealmModel realm,
      UserModel user,
      String loginUsername,
      String ipAddress,
      String authMethod,
      boolean rememberMe,
      String brokerSessionId,
      String brokerUserId) {
    // todo
  }

  @Override
  public void removeAuthenticatedClientSessions(Collection<String> removedClientUUIDS) {
    // todo
  }
}
