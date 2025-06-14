package io.phasetwo.keycloak.jpacache.authSession;

import static org.keycloak.models.utils.SessionExpiration.getAuthSessionLifespan;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.phasetwo.keycloak.jpacache.MapEntity;
import io.phasetwo.keycloak.jpacache.RedisChangelogTransaction;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.util.Base64Url;
import org.keycloak.common.util.SecretGenerator;
import org.keycloak.common.util.Time;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;
import redis.clients.jedis.Jedis;

@JBossLog
public class RedisRootAuthenticationSessionAdapter extends MapEntity<RootAuthenticationSessionKey>
    implements RootAuthenticationSessionModel {

  private final KeycloakSession session;
  private final Jedis jedis;
  private final RedisChangelogTransaction<
          AuthenticationSessionKey, RedisAuthenticationSessionAdapter>
      authSessionTrx;

  private Map<String, AuthenticationSessionModel> authSessions = Maps.newHashMap();
  private boolean authSessionsInitialized = false;

  public RedisRootAuthenticationSessionAdapter(
      KeycloakSession session,
      Jedis jedis,
      RedisChangelogTransaction<AuthenticationSessionKey, RedisAuthenticationSessionAdapter>
          authSessionTrx,
      String realmId,
      String id) {
    this(session, jedis, authSessionTrx, realmId, id, null);
  }

  public RedisRootAuthenticationSessionAdapter(
      KeycloakSession session,
      Jedis jedis,
      RedisChangelogTransaction<AuthenticationSessionKey, RedisAuthenticationSessionAdapter>
          authSessionTrx,
      String realmId,
      String id,
      Map<String, String> existingData) {
    super(new RootAuthenticationSessionKey(realmId, id), existingData);
    this.session = session;
    this.jedis = jedis;
    this.authSessionTrx = authSessionTrx;
    setField("id", id);
    setField("realmId", realmId);
  }

  @Override
  public Map<String, String> getSecondaryIndexes() {
    ImmutableMap.Builder<String, String> b = ImmutableMap.builder();
    b.put(String.format("root-auth-session:realm-index:%s", getRealmId()), getKey().key());
    return b.build();
  }

  @Override
  public RealmModel getRealm() {
    return session.realms().getRealm(getRealmId());
  }

  public String getRealmId() {
    return getString("realmId");
  }

  @Override
  public String getId() {
    return getString("id");
  }

  @Override
  public void setTimestamp(int timestamp) {
    setField("timestamp", timestamp);
  }

  @Override
  public int getTimestamp() {
    return getInt("timestamp", 0);
  }

  public void setExpiration(int expiration) {
    setField("expiration", expiration);
  }

  public int getExpiration() {
    return getInt("expiration", 0);
  }

  // TODO add expiration - figure out how to expire entries with a job or
  // the redis internal mechanism (doesn't work on multi-region)

  @Override
  public Map<String, AuthenticationSessionModel> getAuthenticationSessions() {
    if (authSessionsInitialized) return authSessions;

    String indexKey = String.format("auth-session:parent:%s", getId());
    log.debugf("[redis] SMEMBERS %s", indexKey);
    Set<String> strIds = jedis.smembers(indexKey);
    if (strIds != null && !strIds.isEmpty()) {
      Set<AuthenticationSessionKey> asIds =
          strIds.stream().map(AuthenticationSessionKey::fromString).collect(Collectors.toSet());

      // todo
      // - does anyone mutate the map directly? do we need to support put/putAll/remove/clear?

      if (asIds != null) {
        authSessions =
            asIds.stream()
                .collect(Collectors.toMap(AuthenticationSessionKey::tabId, authSessionTrx::get));
      }
    }
    authSessionsInitialized = true;
    return authSessions;
  }

  @Override
  public AuthenticationSessionModel getAuthenticationSession(ClientModel client, String tabId) {
    log.tracef("getAuthenticationSession tabId=%s clientId=%s", tabId, client.getId());
    if (client == null || tabId == null) {
      return null;
    }
    return authSessionTrx.get(new AuthenticationSessionKey(client.getId(), tabId));
  }

  @Override
  public AuthenticationSessionModel createAuthenticationSession(ClientModel client) {
    Objects.requireNonNull(client, "The provided client can't be null!");

    int timestamp = Time.currentTime();
    int authSessionLifespanSeconds = getAuthSessionLifespan(client.getRealm());

    String tabId = generateTabId();
    RedisAuthenticationSessionAdapter adapter =
        new RedisAuthenticationSessionAdapter(session, client.getId(), tabId);
    adapter.setClientUuid(client.getId());
    adapter.setParentSession(this);
    authSessionTrx.addForSave(adapter);
    log.tracef("created authSession %s", adapter);

    setTimestamp(timestamp);
    setExpiration(timestamp + authSessionLifespanSeconds);

    getAuthenticationSessions().put(tabId, adapter);

    session.getContext().setAuthenticationSession(adapter);
    return adapter;
  }

  @Override
  public void removeAuthenticationSessionByTabId(String tabId) {
    AuthenticationSessionModel as = getAuthenticationSessions().get(tabId);
    removeAuthenticationSession(as);
    getAuthenticationSessions().remove(tabId);
    setTimestamp(Time.currentTime());
  }

  private void removeAuthenticationSession(AuthenticationSessionModel authSession) {
    if (authSession != null && authSession instanceof RedisAuthenticationSessionAdapter) {
      RedisAuthenticationSessionAdapter adapter = (RedisAuthenticationSessionAdapter) authSession;
      authSessionTrx.addForDelete(adapter);
    } else {
      log.tracef(
          "No authentication session found for %s",
          authSession == null ? null : authSession.getTabId());
    }
  }

  @Override
  public void restartSession(RealmModel realm) {
    Iterator<Map.Entry<String, AuthenticationSessionModel>> iterator =
        getAuthenticationSessions().entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, AuthenticationSessionModel> entry = iterator.next();
      removeAuthenticationSession(entry.getValue());
      iterator.remove();
    }
    setTimestamp(Time.currentTime());
  }

  private String generateTabId() {
    return Base64Url.encode(SecretGenerator.getInstance().randomBytes(8));
  }
}
