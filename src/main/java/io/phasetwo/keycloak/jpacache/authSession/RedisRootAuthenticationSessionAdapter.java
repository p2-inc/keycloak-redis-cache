package io.phasetwo.keycloak.jpacache.authSession;

import io.phasetwo.keycloak.jpacache.MapEntity;
import com.google.common.collect.ImmutableMap;
import io.phasetwo.keycloak.jpacache.MapEntity;
import java.util.Map;
import java.util.Objects;
import org.keycloak.models.UserLoginFailureModel;
import org.keycloak.common.util.Base64Url;
import org.keycloak.common.util.SecretGenerator;
import org.keycloak.common.util.Time;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.SessionExpiration;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class RedisRootAuthenticationSessionAdapter extends MapEntity<RootAuthenticationSessionKey> implements RootAuthenticationSessionModel {

  public RedisRootAuthenticationSessionAdapter(String realmId, String id) {
    this(realmId, id, null);
  }

  public RedisRootAuthenticationSessionAdapter(
      String realmId, String id, Map<String, String> existingData) {
    super(new RootAuthenticationSessionKey(realmId, id), existingData);
    setField("id", id);
    setField("realmId", realmId);
  }

  @Override
  public Map<String, String> getSecondaryIndexes() {
    ImmutableMap.Builder<String, String> b = ImmutableMap.builder();
    b.put(String.format("root-auth-session:realm-index:%s", getRealmId()), getId());
    return b.build();
  }

  @Override
  public RealmModel getRealm() {
    return null;//need a kc session
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

  @Override
  public Map<String, AuthenticationSessionModel> getAuthenticationSessions() {
    /*
    return rootAuthenticationSession.getAuthenticationSessions().values().stream()
        .map(entityToAdapterFunc(realm))
        .collect(Collectors.toMap(JpaCacheAuthSessionAdapter::getTabId, Function.identity()));
    */
    return null;
  }
  
  @Override
  public AuthenticationSessionModel getAuthenticationSession(ClientModel client, String tabId) {
    log.tracef("getAuthenticationSession tabId=%s clientId=%s", tabId, client.getId());
    if (client == null || tabId == null) {
      return null;
    }

    /*
    TypedQuery<AuthenticationSession> query =
        entityManager.createNamedQuery("findAuthSessionsByCompoundId", AuthenticationSession.class);
    query.setParameter("parentSession", rootAuthenticationSession);
    query.setParameter("tabId", tabId);
    query.setParameter("clientId", client.getId());
    List<AuthenticationSession> authSessions = query.getResultList();
    if (authSessions != null && authSessions.size() > 0) {
      log.tracef(
          "Found %d authSessions for tabId=%s clientId=%s, in rootSession %s",
          authSessions.size(), tabId, client.getClientId(), rootAuthenticationSession);
      return entityToAdapterFunc(realm).apply(authSessions.get(0));
    } else {
      log.tracef(
          "Found NO authSessions for tabId=%s clientId=%s, in rootSession %s",
          tabId, client.getClientId(), rootAuthenticationSession);
      return null;
    }
    */
    return null;
  }

  @Override
  public AuthenticationSessionModel createAuthenticationSession(ClientModel client) {
    Objects.requireNonNull(client, "The provided client can't be null!");

    /*
    TypedQuery<AuthenticationSession> query =
        entityManager.createNamedQuery(
            "findAuthSessionsByRootSession", AuthenticationSession.class);
    query.setParameter("parentSession", rootAuthenticationSession);
    List<AuthenticationSession> authenticationSessions = query.getResultList();
    if (authenticationSessions != null && authenticationSessions.size() >= authSessionsLimit) {
      Optional<AuthenticationSession> oldest =
          authenticationSessions.stream().min(TIMESTAMP_COMPARATOR);
      String tabId = oldest.map(AuthenticationSession::getTabId).orElse(null);

      if (tabId != null && !oldest.isEmpty()) {
        log.debugf(
            "Reached limit (%s) of active authentication sessions per a root authentication session. Removing oldest authentication session with TabId %s.",
            authSessionsLimit, tabId);
        // remove the oldest authentication session
        entityManager.remove(oldest.get());
        entityManager.flush();
      }
    }

    long timestamp = Time.currentTimeMillis();
    int authSessionLifespanSeconds = getAuthSessionLifespan(realm);

    String tabId = generateTabId();
    AuthenticationSession authSession =
        AuthenticationSession.builder()
            .id(KeycloakModelUtils.generateId())
            .parentSession(rootAuthenticationSession)
            .clientId(client.getId())
            .timestamp(timestamp)
            .tabId(tabId)
            .build();
    entityManager.persist(authSession);
    log.tracef("created authSession %s", authSession);
    rootAuthenticationSession.setTimestamp(timestamp);
    rootAuthenticationSession.setExpiration(
        timestamp + TimeAdapter.fromSecondsToMilliseconds(authSessionLifespanSeconds));
    rootAuthenticationSession.getAuthenticationSessions().put(tabId, authSession);

    JpaCacheAuthSessionAdapter jpaCacheAuthSessionAdapter =
        entityToAdapterFunc(realm).apply(authSession);
    session.getContext().setAuthenticationSession(jpaCacheAuthSessionAdapter);

    return jpaCacheAuthSessionAdapter;
    */
    return null;
  }

  @Override
  public void removeAuthenticationSessionByTabId(String tabId) {
    /*
    AuthenticationSession authSession =
        rootAuthenticationSession.getAuthenticationSessions().remove(tabId);
    log.tracef("Removing authSession (%s) %s", tabId, authSession);
    if (authSession != null) {
      entityManager.remove(authSession);
      if (rootAuthenticationSession.getAuthenticationSessions().isEmpty()) {
        entityManager.remove(rootAuthenticationSession);
      } else {
        rootAuthenticationSession.setTimestamp(Time.currentTimeMillis());
      }
      entityManager.flush();
    }
    */
  }

  @Override
  public void restartSession(RealmModel realm) {
    /*
    rootAuthenticationSession.getAuthenticationSessions().clear();
    rootAuthenticationSession.setTimestamp(Time.currentTimeMillis());
    */
  }

  private String generateTabId() {
    return Base64Url.encode(SecretGenerator.getInstance().randomBytes(8));
  }
  
}
