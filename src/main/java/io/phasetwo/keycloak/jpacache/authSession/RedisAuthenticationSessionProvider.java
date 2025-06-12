package io.phasetwo.keycloak.jpacache.authSession;

import static org.keycloak.models.utils.SessionExpiration.getAuthSessionLifespan;
import static org.keycloak.common.util.StackUtil.getShortStackTrace;
import static io.phasetwo.keycloak.common.ExpirationUtils.isExpired;

import org.keycloak.common.util.Time;
import io.phasetwo.keycloak.common.TimeAdapter;
import org.keycloak.sessions.AuthenticationSessionProvider;
import org.keycloak.models.KeycloakSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.keycloak.sessions.AuthenticationSessionCompoundId;
import org.keycloak.sessions.AuthenticationSessionProvider;
import org.keycloak.sessions.RootAuthenticationSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.RealmModel;
import redis.clients.jedis.Jedis;

@JBossLog
@RequiredArgsConstructor
public class RedisAuthenticationSessionProvider implements AuthenticationSessionProvider {
  
  private final KeycloakSession session;
  private final Jedis jedis;
  private final int authSessionsLimit;

  @Override
  public RootAuthenticationSessionModel createRootAuthenticationSession(RealmModel realm) {
    return createRootAuthenticationSession(realm, null);
  }

  @Override
  public RootAuthenticationSessionModel createRootAuthenticationSession(
      RealmModel realm, String id) {
    Objects.requireNonNull(realm, "The provided realm can't be null!");
    log.tracef("createRootAuthenticationSession(%s)%s", realm.getName(), getShortStackTrace());

    long timestamp = Time.currentTimeMillis();
    int authSessionLifespanSeconds = getAuthSessionLifespan(realm);
    /*

    RootAuthenticationSession entity =
        RootAuthenticationSession.builder()
            .id(id == null ? KeycloakModelUtils.generateId() : id)
            .realmId(realm.getId())
            .timestamp(timestamp)
            .expiration(
                timestamp + TimeAdapter.fromSecondsToMilliseconds(authSessionLifespanSeconds))
            .build();

    entityManager.persist(entity);
    entityManager.flush();

    return entityToAdapterFunc(realm).apply(entity);
    */
    return null;
  }

  @Override
  public RootAuthenticationSessionModel getRootAuthenticationSession(
      RealmModel realm, String authenticationSessionId) {
    Objects.requireNonNull(realm, "The provided realm can't be null!");
    if (authenticationSessionId == null) {
      return null;
    }
    /*
    log.tracef(
        "getRootAuthenticationSession(%s, %s)%s",
        realm.getName(), authenticationSessionId, getShortStackTrace());

    return findRootAuthSession(realm, authenticationSessionId)
        .map(entityToAdapterFunc(realm))
        .orElse(null);
    */
    return null;
  }

  /*
  private Optional<RootAuthenticationSession> findRootAuthSession(RealmModel realm, String id) {
    TypedQuery<RootAuthenticationSession> query =
        entityManager.createNamedQuery("findRootAuthSession", RootAuthenticationSession.class);
    query.setParameter("realmId", realm.getId());
    query.setParameter("id", id);
    return query.getResultList().stream().findFirst();
  }
  */
  
  @Override
  public void removeRootAuthenticationSession(
      RealmModel realm, RootAuthenticationSessionModel authenticationSession) {
    Objects.requireNonNull(
        authenticationSession, "The provided root authentication session can't be null!");
    /*
    entityManager
        .createNamedQuery("deleteRootAuthSession")
        .setParameter("realmId", realm.getId())
        .setParameter("id", authenticationSession.getId())
        .executeUpdate();
    */
  }

  @Override
  public void removeAllExpired() {
    log.tracef("removeAllExpired()%s", getShortStackTrace());
    log.warnf(
        "Clearing expired entities should not be triggered manually. It is responsibility of the store to clear these.");
  }

  @Override
  public void removeExpired(RealmModel realm) {
    log.tracef("removeExpired(%s)%s", realm, getShortStackTrace());
    log.warnf(
        "Clearing expired entities should not be triggered manually. It is responsibility of the store to clear these.");
  }

  @Override
  public void onRealmRemoved(RealmModel realm) {
    // Just let them expire...
  }

  @Override
  public void onClientRemoved(RealmModel realm, ClientModel client) {
    // Just let them expire...
  }

  // xgp TODO
  @Override
  public void updateNonlocalSessionAuthNotes(
      AuthenticationSessionCompoundId compoundId, Map<String, String> authNotesFragment) {
    if (compoundId == null) {
      return;
    }
    Objects.requireNonNull(
        authNotesFragment, "The provided authentication's notes map can't be null!");

    /*
    TypedQuery<AuthenticationSession> query =
        entityManager.createNamedQuery("findAuthSessionsByCompoundId", AuthenticationSession.class);
    query.setParameter("parentSessionId", compoundId.getRootSessionId());
    query.setParameter("tabId", compoundId.getTabId());
    query.setParameter("clientId", compoundId.getClientUUID());
    //    return query.getResultList().stream().findFirst();

    AuthenticationSession authenticationSession = query.getSingleResult();
    if (authenticationSession != null) {
      authenticationSession.setAuthNotes(authNotesFragment);
    }
    */
  }

  @Override
  public void close() {
    // Nothing to do
  }
}
