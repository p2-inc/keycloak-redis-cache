package io.phasetwo.keycloak.jpacache.authSession;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;
import static org.keycloak.models.utils.SessionExpiration.getAuthSessionLifespan;

import io.phasetwo.keycloak.jpacache.RedisChangelogTransaction;
import java.util.Map;
import java.util.Objects;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.util.Time;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.sessions.AuthenticationSessionCompoundId;
import org.keycloak.sessions.AuthenticationSessionProvider;
import org.keycloak.sessions.RootAuthenticationSessionModel;
import redis.clients.jedis.Jedis;

@JBossLog
public class RedisAuthenticationSessionProvider implements AuthenticationSessionProvider {

  private final KeycloakSession session;
  private final Jedis jedis;
  private final int authSessionsLimit;

  private final RedisChangelogTransaction<
          RootAuthenticationSessionKey, RedisRootAuthenticationSessionAdapter>
      rootSessionTrx;
  private final RedisChangelogTransaction<
          AuthenticationSessionKey, RedisAuthenticationSessionAdapter>
      authSessionTrx;

  public RedisAuthenticationSessionProvider(
      KeycloakSession session, Jedis jedis, int authSessionsLimit) {
    this.session = session;
    this.jedis = jedis;
    this.authSessionsLimit = authSessionsLimit;

    this.authSessionTrx =
        new RedisChangelogTransaction<>(
            jedis, new AuthenticationSessionAdapterSupplier(session, jedis));
    this.rootSessionTrx =
        new RedisChangelogTransaction<>(
            jedis,
            new RootAuthenticationSessionAdapterSupplier(session, jedis, this.authSessionTrx));
    session.getTransactionManager().enlistAfterCompletion(this.authSessionTrx);
    session.getTransactionManager().enlistAfterCompletion(this.rootSessionTrx);
  }

  @Override
  public RootAuthenticationSessionModel createRootAuthenticationSession(RealmModel realm) {
    return createRootAuthenticationSession(realm, null);
  }

  @Override
  public RootAuthenticationSessionModel createRootAuthenticationSession(
      RealmModel realm, String id) {
    Objects.requireNonNull(realm, "The provided realm can't be null!");
    log.tracef("createRootAuthenticationSession(%s)%s", realm.getName(), getShortStackTrace());

    int timestamp = Time.currentTime();
    int authSessionLifespanSeconds = getAuthSessionLifespan(realm);

    RedisRootAuthenticationSessionAdapter adapter =
        new RedisRootAuthenticationSessionAdapter(
            session,
            jedis,
            authSessionTrx,
            realm.getId(),
            id == null ? KeycloakModelUtils.generateId() : id);
    adapter.setTimestamp(timestamp);
    long exp = (timestamp + authSessionLifespanSeconds) * 1000L;
    adapter.setExpiration(exp);

    rootSessionTrx.addForSave(adapter);

    return adapter;
  }

  @Override
  public RootAuthenticationSessionModel getRootAuthenticationSession(
      RealmModel realm, String authenticationSessionId) {
    Objects.requireNonNull(realm, "The provided realm can't be null!");
    if (authenticationSessionId == null) {
      return null;
    }
    return rootSessionTrx.get(
        new RootAuthenticationSessionKey(realm.getId(), authenticationSessionId));
  }

  @Override
  public void removeRootAuthenticationSession(
      RealmModel realm, RootAuthenticationSessionModel authenticationSession) {
    Objects.requireNonNull(
        authenticationSession, "The provided root authentication session can't be null!");

    RedisRootAuthenticationSessionAdapter adapter;
    if (authenticationSession instanceof RedisRootAuthenticationSessionAdapter) {
      adapter = (RedisRootAuthenticationSessionAdapter) authenticationSession;
    } else {
      adapter =
          rootSessionTrx.get(
              new RootAuthenticationSessionKey(realm.getId(), authenticationSession.getId()));
    }
    rootSessionTrx.addForDelete(adapter);
  }

  @Override
  public void removeAllExpired() {
    log.tracef("removeAllExpired()%s", getShortStackTrace());
    log.warnf(
        "[deprecated] Clearing expired entities should not be triggered manually. It is responsibility of the store to clear these.");
  }

  @Override
  public void removeExpired(RealmModel realm) {
    log.tracef("removeExpired(%s)%s", realm, getShortStackTrace());
    log.warnf(
        "[deprecated] Clearing expired entities should not be triggered manually. It is responsibility of the store to clear these.");
  }

  @Override
  public void onRealmRemoved(RealmModel realm) {
    log.tracef("onRealmRemoved(%s)%s", realm, getShortStackTrace());
    // Just let them expire...
  }

  @Override
  public void onClientRemoved(RealmModel realm, ClientModel client) {
    log.tracef("onClientRemoved(%s-%s)%s", realm, client, getShortStackTrace());
    // Just let them expire...
  }

  @Override
  public void updateNonlocalSessionAuthNotes(
      AuthenticationSessionCompoundId compoundId, Map<String, String> authNotesFragment) {
    if (compoundId == null) {
      return;
    }
    Objects.requireNonNull(
        authNotesFragment, "The provided authentication's notes map can't be null!");

    log.tracef("updateNonlocalSessionAuthNotes(%s)%s", compoundId, getShortStackTrace());

    // TODO implement

    // how do I get the realm to make the key?

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
