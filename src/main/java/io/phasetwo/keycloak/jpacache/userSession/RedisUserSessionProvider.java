package io.phasetwo.keycloak.jpacache.userSession;

import static io.phasetwo.keycloak.jpacache.userSession.expiration.RedisSessionExpiration.*;
import static org.keycloak.common.util.StackUtil.getShortStackTrace;
import static org.keycloak.models.UserSessionModel.SessionPersistenceState.TRANSIENT;

import com.google.common.collect.Sets;
import io.phasetwo.keycloak.jpacache.RedisChangelogTransaction;
import io.phasetwo.keycloak.jpacache.userSession.expiration.SessionExpirationData;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.util.Time;
import org.keycloak.device.DeviceActivityManager;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;
import redis.clients.jedis.Jedis;

@JBossLog
public class RedisUserSessionProvider implements UserSessionProvider {
  private final KeycloakSession session;
  private final Jedis jedis;

  private final RedisChangelogTransaction<UserSessionKey, RedisUserSessionAdapter> userSessionTrx;
  private final RedisChangelogTransaction<
          AuthenticatedClientSessionKey, RedisAuthenticatedClientSessionAdapter>
      clientSessionTrx;

  private final Map<String, RedisUserSessionAdapter> transientUserSessions = new HashMap<>();

  public RedisUserSessionProvider(KeycloakSession session, Jedis jedis) {
    this.session = session;
    this.jedis = jedis;

    this.clientSessionTrx =
        new RedisChangelogTransaction<>(
            jedis, new AuthenticatedClientSessionAdapterSupplier(session, jedis));
    this.userSessionTrx =
        new RedisChangelogTransaction<>(
            jedis, new UserSessionAdapterSupplier(session, jedis, this.clientSessionTrx));
    session.getTransactionManager().enlistAfterCompletion(this.userSessionTrx);
    session.getTransactionManager().enlistAfterCompletion(this.clientSessionTrx);
  }

  @Override
  public KeycloakSession getKeycloakSession() {
    return session;
  }

  @Override
  public AuthenticatedClientSessionModel createClientSession(
      RealmModel realm, ClientModel client, UserSessionModel userSession) {
    log.tracef(
        "createClientSession(%s, %s, %s)%s", realm, client, userSession, getShortStackTrace());

    if (userSession == null) {
      throw new IllegalStateException("User session is null.");
    }

    RedisAuthenticatedClientSessionAdapter entity =
        createAuthenticatedClientSessionEntityInstance(null, client.getId(), false);

    // TODO started?
    // String started = entity.getTimestamp() != null
    //                  ?
    // String.valueOf(TimeAdapter.fromMilliSecondsToSeconds(entity.getTimestamp()))
    //                  : String.valueOf(0);
    // entity.getNotes().put(AuthenticatedClientSessionModel.STARTED_AT_NOTE, started);

    RedisUserSessionAdapter userSessionEntity = getUserSessionAdapter(userSession);
    if (userSessionEntity == null) {
      throw new IllegalStateException("User session entity does not exist: " + userSession.getId());
    }

    setClientSessionExpiration(
        entity, SessionExpirationData.builder().realm(realm).build(), client);

    userSessionEntity.getAuthenticatedClientSessions().put(client.getId(), entity);
    return entity;
  }

  /** Convert the UserSessionModel to a RedisUserSessionAdapter or load it from the transaction */
  private RedisUserSessionAdapter getUserSessionAdapter(UserSessionModel userSession) {
    RedisUserSessionAdapter userSessionEntity;
    if (userSession instanceof RedisUserSessionAdapter) {
      userSessionEntity = (RedisUserSessionAdapter) userSession;
    } else {
      userSessionEntity = userSessionTrx.get(new UserSessionKey(userSession.getId()));
    }
    return userSessionEntity;
  }

  /**
   * Convert the AuthenticatedClientSessionModel to a RedisAuthenticatedClientSessionAdapter or load
   * it from the transaction
   */
  private RedisAuthenticatedClientSessionAdapter getAuthenticatedClientSessionAdapter(
      AuthenticatedClientSessionModel authenticatedClientSession) {
    RedisAuthenticatedClientSessionAdapter authenticatedClientSessionEntity;
    if (authenticatedClientSession instanceof RedisAuthenticatedClientSessionAdapter) {
      authenticatedClientSessionEntity =
          (RedisAuthenticatedClientSessionAdapter) authenticatedClientSession;
    } else {
      authenticatedClientSessionEntity =
          clientSessionTrx.get(
              new AuthenticatedClientSessionKey(authenticatedClientSession.getId()));
    }
    return authenticatedClientSessionEntity;
  }

  @Override
  public AuthenticatedClientSessionModel getClientSession(
      UserSessionModel userSession, ClientModel client, String clientSessionId, boolean offline) {
    log.tracef(
        "getClientSession(%s, %s, %s, %s)%s",
        userSession, client, clientSessionId, offline, getShortStackTrace());

    if (userSession == null) {
      return null;
    }

    RedisAuthenticatedClientSessionAdapter entity =
        clientSessionTrx.get(new AuthenticatedClientSessionKey(clientSessionId));

    if (entity != null
        && entity.getParentId().equals(userSession.getId())
        && entity.getClientUuid().equals(client.getId())
        && entity.getUserSession().isOffline() == offline) {
      return entity;
    }

    return null;
  }

  @Override
  public UserSessionModel createUserSession(
      RealmModel realm,
      UserModel user,
      String loginUsername,
      String ipAddress,
      String authMethod,
      boolean rememberMe,
      String brokerSessionId,
      String brokerUserId) {
    return createUserSession(
        null,
        realm,
        user,
        loginUsername,
        ipAddress,
        authMethod,
        rememberMe,
        brokerSessionId,
        brokerUserId,
        UserSessionModel.SessionPersistenceState.PERSISTENT);
  }

  @Override
  public UserSessionModel createUserSession(
      String id,
      RealmModel realm,
      UserModel user,
      String loginUsername,
      String ipAddress,
      String authMethod,
      boolean rememberMe,
      String brokerSessionId,
      String brokerUserId,
      UserSessionModel.SessionPersistenceState persistenceState) {
    id = id == null ? KeycloakModelUtils.generateId() : id;
    log.tracef(
        "createUserSession(%s, %s, %s, %s)%s",
        id, realm, loginUsername, persistenceState, getShortStackTrace());

    RedisUserSessionAdapter entity =
        createUserSessionEntityInstance(
            id,
            realm.getId(),
            user.getId(),
            loginUsername,
            ipAddress,
            authMethod,
            rememberMe,
            brokerSessionId,
            brokerUserId,
            false);

    entity.setPersistenceState(persistenceState);
    setUserSessionExpiration(entity, SessionExpirationData.builder().realm(realm).build());
    if (TRANSIENT == persistenceState) {
      transientUserSessions.put(entity.getId(), entity);
    } else {
      if (userSessionTrx.get(new UserSessionKey(id)) != null) {
        throw new ModelDuplicateException("User session exists: " + id);
      }
      userSessionTrx.addForSave(entity);
    }

    DeviceActivityManager.attachDevice(entity, session);

    return entity;
  }

  @Override
  public RedisUserSessionAdapter getUserSession(RealmModel realm, String id) {
    Objects.requireNonNull(realm, "The provided realm can't be null!");
    log.tracef("getUserSession(%s, %s)%s", realm, id, getShortStackTrace());
    if (id == null) return null;
    return userSessionTrx.get(new UserSessionKey(id));
  }

  private Stream<UserSessionModel> getUserSessionsStreamByIndexKey(
      String indexKey, RealmModel realm, boolean offline) {
    log.debugf("[redis] SMEMBERS %s", indexKey);
    Set<String> strIds = jedis.smembers(indexKey);
    if (strIds != null && !strIds.isEmpty()) {
      return strIds.stream()
          .map(str -> UserSessionKey.fromString(str))
          .map(k -> userSessionTrx.get(k))
          .filter(s -> s.getRealmId().equals(realm.getId()))
          .filter(s -> offline == s.isOffline())
          .map(s -> (UserSessionModel) s);
    } else {
      return Stream.empty();
    }
  }

  @Override
  public Stream<UserSessionModel> getUserSessionsStream(RealmModel realm, UserModel user) {
    log.tracef("getUserSessionsStream(%s, %s)%s", realm, user, getShortStackTrace());

    String indexKey = String.format("user-session:user-index:%s", user.getId());
    return getUserSessionsStreamByIndexKey(indexKey, realm, false);
  }

  @Override
  public Stream<UserSessionModel> getUserSessionsStream(RealmModel realm, ClientModel client) {
    log.tracef("getUserSessionsStream(%s, %s)%s", realm, client, getShortStackTrace());

    String indexKey = String.format("authenticated-client:client-index:%s", client.getId());
    log.debugf("[redis] SMEMBERS %s", indexKey);
    Set<String> strIds = Sets.newTreeSet(jedis.smembers(indexKey)); // for consistent sorting
    if (strIds != null && !strIds.isEmpty()) {
      return strIds.stream()
          .map(str -> AuthenticatedClientSessionKey.fromString(str))
          .map(k -> clientSessionTrx.get(k))
          .filter(c -> c.getRealmId().equals(realm.getId()))
          .filter(c -> c.getClientUuid().equals(client.getId()))
          .map(c -> c.getUserSession());
    } else {
      return Stream.empty();
    }
  }

  @Override
  public Stream<UserSessionModel> getUserSessionsStream(
      RealmModel realm, ClientModel client, Integer firstResult, Integer maxResults) {
    log.tracef(
        "getUserSessionsStream(%s, %s, %s, %s)%s",
        realm, client, firstResult, maxResults, getShortStackTrace());

    return getUserSessionsStream(realm, client)
        .filter(s -> !s.isOffline())
        .skip(firstResult != null && firstResult > 0 ? firstResult : 0)
        .limit(maxResults != null && maxResults > 0 ? maxResults : Long.MAX_VALUE);
  }

  @Override
  public Stream<UserSessionModel> getUserSessionByBrokerUserIdStream(
      RealmModel realm, String brokerUserId) {
    log.tracef(
        "getUserSessionByBrokerUserIdStream(%s, %s)%s", realm, brokerUserId, getShortStackTrace());

    String indexKey = String.format("user-session:broker-user-index:%s", brokerUserId);
    return getUserSessionsStreamByIndexKey(indexKey, realm, false);
  }

  @Override
  public UserSessionModel getUserSessionByBrokerSessionId(
      RealmModel realm, String brokerSessionId) {
    log.tracef(
        "getUserSessionByBrokerSessionId(%s, %s)%s", realm, brokerSessionId, getShortStackTrace());

    String indexKey = String.format("user-session:broker-session-index:%s", brokerSessionId);
    return getUserSessionsStreamByIndexKey(indexKey, realm, false).findFirst().orElse(null);
  }

  @Override
  public UserSessionModel getUserSessionWithPredicate(
      RealmModel realm, String id, boolean offline, Predicate<UserSessionModel> predicate) {
    log.tracef(
        "getUserSessionWithPredicate(%s, %s, %s)%s", realm, id, offline, getShortStackTrace());

    RedisUserSessionAdapter a = userSessionTrx.get(new UserSessionKey(id));
    if (!realm.getId().equals(a.getRealmId())) return null;
    if (offline != a.isOffline()) return null;
    return Stream.of(a).filter(predicate).findFirst().orElse(null);
  }

  @Override
  public long getActiveUserSessions(RealmModel realm, ClientModel client) {
    log.tracef("getActiveUserSessions(%s, %s)%s", realm, client, getShortStackTrace());

    // TODO a more efficient way?
    return getUserSessionsStream(realm, client).count();
  }

  @Override
  public Map<String, Long> getActiveClientSessionStats(RealmModel realm, boolean offline) {
    log.tracef("getActiveClientSessionStats(%s, %s)%s", realm, offline, getShortStackTrace());

    /* TODO
        return userSessionRepository.findAll().stream()
                .filter(s -> s.getRealmId().equals(realm.getId()))
                .filter(s -> s.getOffline() == offline)
                .map(entityToAdapterFunc(realm))
                .filter(Objects::nonNull)
                .map(UserSessionModel::getAuthenticatedClientSessions)
                .map(Map::keySet)
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    */
    return null;
  }

  @Override
  public void removeUserSession(RealmModel realm, UserSessionModel session) {
    Objects.requireNonNull(session, "The provided user session can't be null!");

    log.tracef("removeUserSession(%s, %s)%s", realm, session, getShortStackTrace());

    RedisUserSessionAdapter a;
    if (session instanceof RedisUserSessionAdapter) {
      a = (RedisUserSessionAdapter) session;
    } else {
      a = userSessionTrx.get(new UserSessionKey(session.getId()));
    }
    userSessionTrx.addForDelete(a);
  }

  @Override
  public void removeUserSessions(RealmModel realm, UserModel user) {
    log.tracef("removeUserSessions(%s, %s)%s", realm, user, getShortStackTrace());

    getUserSessionsStream(realm, user)
        .forEach(
            a -> {
              removeSession(a);
            });
  }

  private void removeSession(UserSessionModel a) {
    for (AuthenticatedClientSessionModel c : a.getAuthenticatedClientSessions().values()) {
      if (c instanceof RedisAuthenticatedClientSessionAdapter) {
        RedisAuthenticatedClientSessionAdapter rc = (RedisAuthenticatedClientSessionAdapter) c;
        clientSessionTrx.addForDelete(rc);
      } else {
        // TODO?
      }
    }
    if (a instanceof RedisUserSessionAdapter) {
      RedisUserSessionAdapter ra = (RedisUserSessionAdapter) a;
      userSessionTrx.addForDelete(ra);
    } else {
      // TODO?
    }
  }

  @Override
  public void removeAllExpired() {
    log.tracef("removeAllExpired()%s", getShortStackTrace());
    // store ttl
  }

  @Override
  public void removeExpired(RealmModel realm) {
    log.tracef("removeExpired(%s)%s", realm, getShortStackTrace());
    // store ttl
  }

  @Override
  public void removeUserSessions(RealmModel realm) {
    log.tracef("removeUserSessions(%s)%s", realm, getShortStackTrace());
    // TODO make this efficient. maybe with a SCAN/COUNT?

    String indexKey = String.format("user-session:realm-index:%s", realm.getId());
    getUserSessionsStreamByIndexKey(indexKey, realm, false).forEach(a -> removeSession(a));
    // TODO better way to do offline/online
    getUserSessionsStreamByIndexKey(indexKey, realm, true).forEach(a -> removeSession(a));
  }

  @Override
  public void onRealmRemoved(RealmModel realm) {
    log.tracef("onRealmRemoved(%s)%s", realm, getShortStackTrace());
    removeUserSessions(realm);
  }

  @Override
  public void onClientRemoved(RealmModel realm, ClientModel client) {
    /* TODO
    List<UserSession> relevantSessions = userSessionRepository.findAll().stream()
                .filter(s -> s.getClientSessions().containsKey(client.getId()))
                .collect(Collectors.toList());

        for (UserSession session : relevantSessions) {
            session.getClientSessions().remove(client.getId());
            if (session.getClientSessions().isEmpty()) {
                userSessionRepository.deleteUserSession(session);
                CassandraUserSessionAdapter model = sessionModels.get(session.getId());
                if (model != null) {
                    model.markAsDeleted();
                }

                sessionModels.remove(session.getId());
            } else {
                userSessionRepository.update(session);
            }
        }
    */
  }

  @Override
  public UserSessionModel createOfflineUserSession(UserSessionModel userSession) {
    log.tracef("createOfflineUserSession(%s)%s", userSession, getShortStackTrace());

    /* TODO
        if (userSession.getNote(CORRESPONDING_SESSION_ID) != null) {
            return getUserSession(userSession.getRealm(), userSession.getNote(CORRESPONDING_SESSION_ID));
        }

        UserSession offlineUserSession = createUserSessionEntityInstance(userSession, true);
        long currentTime = Time.currentTimeMillis();
        offlineUserSession.setTimestamp(currentTime);
        offlineUserSession.setLastSessionRefresh(currentTime);
        setUserSessionExpiration(
                offlineUserSession,
                SessionExpirationData.builder().realm(userSession.getRealm()).build());

        CassandraUserSessionAdapter offlineSessionAdapter =
                entityToAdapterFunc(userSession.getRealm()).apply(offlineUserSession);
        offlineSessionAdapter.setNote(CORRESPONDING_SESSION_ID, userSession.getId());
        userSessionRepository.insert(userSession.getRealm(), offlineUserSession);

        // set a reference for the offline user session to the original online user session
        CassandraUserSessionAdapter orgUserSessionAdapter = getUserSession(userSession.getRealm(), userSession.getId());
        orgUserSessionAdapter.setNote(CORRESPONDING_SESSION_ID, offlineUserSession.getId());
        userSessionRepository.insert(
                userSession.getRealm(),
                orgUserSessionAdapter
                        .getUserSessionEntity()); // Hack to set CORRESPONDING_SESSION_ID, which normally is
        // only set during insert

        return offlineSessionAdapter;
    */
    return null;
  }

  @Override
  public UserSessionModel getOfflineUserSession(RealmModel realm, String userSessionId) {
    log.tracef("getOfflineUserSession(%s, %s)%s", realm, userSessionId, getShortStackTrace());

    Predicate<UserSessionModel> allowAll = t -> true;
    return getUserSessionWithPredicate(realm, userSessionId, true, allowAll);

    /* TODO why do they do this CORRESPONDING_SESSION_ID?
      return getOfflineUserSessionEntityStream(realm, userSessionId)
      .filter(Objects::nonNull)
      .findFirst()
                .map(entityToAdapterFunc(realm))
                .orElse(entityToAdapterFunc(realm)
                        .apply(userSessionRepository.findFirstUserSessionByAttribute(
                                CORRESPONDING_SESSION_ID, userSessionId)));
    */
  }

  @Override
  public void removeOfflineUserSession(RealmModel realm, UserSessionModel userSession) {
    Objects.requireNonNull(userSession, "The provided user session can't be null!");

    log.tracef("removeOfflineUserSession(%s, %s)%s", realm, userSession, getShortStackTrace());

    /* TODO
        UserSession userSessionEntity = userSessionRepository.findUserSessionById(userSession.getId());
        if (userSessionEntity.getOffline() != null && userSessionEntity.getOffline()) {
            userSessionRepository.deleteUserSession(userSessionEntity);
            CassandraUserSessionAdapter model = sessionModels.get(userSessionEntity.getId());
            if (model != null) {
                model.markAsDeleted();
            }
            sessionModels.remove(userSessionEntity.getId());
        } else if (userSessionEntity.hasCorrespondingSession()) {
            String correspondingSessionId = userSessionEntity.getNotes().get(CORRESPONDING_SESSION_ID);
            userSessionRepository.deleteCorrespondingUserSession(userSessionEntity);
            CassandraUserSessionAdapter model = sessionModels.get(correspondingSessionId);
            if (model != null) {
                model.markAsDeleted();
            }
            sessionModels.remove(correspondingSessionId);
        }
    */

  }

  @Override
  public AuthenticatedClientSessionModel createOfflineClientSession(
      AuthenticatedClientSessionModel clientSession, UserSessionModel offlineUserSession) {
    log.tracef(
        "createOfflineClientSession(%s, %s)%s",
        clientSession, offlineUserSession, getShortStackTrace());

    /* TODO
        AuthenticatedClientSessionValue clientSessionEntity =
                createAuthenticatedClientSessionInstance(clientSession, true);
        int currentTime = Time.currentTime();
        clientSessionEntity
                .getNotes()
                .put(AuthenticatedClientSessionModel.STARTED_AT_NOTE, String.valueOf(currentTime));
        clientSessionEntity.setTimestamp(Time.currentTimeMillis());
        RealmModel realm = clientSession.getRealm();
        setClientSessionExpiration(
                clientSessionEntity,
                SessionExpirationData.builder().realm(realm).build(),
                clientSession.getClient());

        Optional<UserSession> userSessionEntity = getOfflineUserSessionEntityStream(realm, offlineUserSession.getId())
                .findFirst();
        if (userSessionEntity.isPresent()) {
            UserSession userSession = userSessionEntity.get();
            String clientId = clientSession.getClient().getId();

            CassandraUserSessionAdapter userSessionModel =
                    entityToAdapterFunc(realm).apply(userSession);

            userSessionRepository.addClientSession(realm, userSessionModel.getUserSessionEntity(), clientSessionEntity);

            return userSessionModel.getAuthenticatedClientSessionByClient(clientId);
        }
    */
    return null;
  }

  @Override
  public Stream<UserSessionModel> getOfflineUserSessionsStream(RealmModel realm, UserModel user) {
    log.tracef("getOfflineUserSessionsStream(%s, %s)%s", realm, user, getShortStackTrace());

    String indexKey = String.format("user-session:user-index:%s", user.getId());
    return getUserSessionsStreamByIndexKey(indexKey, realm, true);
  }

  @Override
  public Stream<UserSessionModel> getOfflineUserSessionByBrokerUserIdStream(
      RealmModel realm, String brokerUserId) {
    log.tracef(
        "getOfflineUserSessionByBrokerUserIdStream(%s, %s)%s",
        realm, brokerUserId, getShortStackTrace());

    String indexKey = String.format("user-session:broker-user-index:%s", brokerUserId);
    return getUserSessionsStreamByIndexKey(indexKey, realm, true);
  }

  @Override
  public long getOfflineSessionsCount(RealmModel realm, ClientModel client) {
    log.tracef("getOfflineSessionsCount(%s, %s)%s", realm, client, getShortStackTrace());

    /* TODO
        return userSessionRepository.findAll().stream()
                .filter(s -> s.getRealmId().equals(realm.getId()))
                .filter(s -> s.getOffline() != null && s.getOffline())
                .flatMap(s -> s.getClientSessions().values().stream())
                .filter(s -> s.getClientId().equals(client.getId()))
                .count();
    */
    return 0;
  }

  @Override
  public Stream<UserSessionModel> getOfflineUserSessionsStream(
      RealmModel realm, ClientModel client, Integer firstResult, Integer maxResults) {
    log.tracef(
        "getOfflineUserSessionsStream(%s, %s, %s, %s)%s",
        realm, client, firstResult, maxResults, getShortStackTrace());

    /* TODO
        return userSessionRepository.findAll().stream()
                .filter(s -> s.getRealmId().equals(realm.getId()))
                .filter(s -> s.getOffline() != null && s.getOffline())
                .filter(s -> s.getClientSessions().containsKey(client.getId()))
                .skip(firstResult == null || firstResult < 0 ? 0 : firstResult)
                .limit(maxResults == null || maxResults < 0 ? Long.MAX_VALUE : maxResults)
                .sorted(Comparator.comparing(UserSession::getLastSessionRefresh))
                .map(entityToAdapterFunc(realm));
    */
    return null;
  }

  @Override
  public void importUserSessions(
      Collection<UserSessionModel> persistentUserSessions, boolean offline) {
    if (persistentUserSessions == null || persistentUserSessions.isEmpty()) {
      return;
    }
    /*
        persistentUserSessions.stream()
                .map(pus -> {
                    UserSession userSessionEntity = createUserSessionEntityInstance(
                            null,
                            pus.getRealm().getId(),
                            pus.getUser().getId(),
                            pus.getLoginUsername(),
                            pus.getIpAddress(),
                            pus.getAuthMethod(),
                            pus.isRememberMe(),
                            pus.getBrokerSessionId(),
                            pus.getBrokerUserId(),
                            offline);
                    userSessionEntity.setPersistenceState(UserSessionModel.SessionPersistenceState.PERSISTENT);

                    for (Map.Entry<String, AuthenticatedClientSessionModel> entry :
                            pus.getAuthenticatedClientSessions().entrySet()) {
                        AuthenticatedClientSessionValue clientSession =
                                createAuthenticatedClientSessionInstance(entry.getValue(), offline);

                        // Update timestamp to same value as userSession. LastSessionRefresh of userSession
                        // from DB will have correct value
                        clientSession.setTimestamp(userSessionEntity.getLastSessionRefresh());

                        RealmModel realm = session.realms().getRealm(userSessionEntity.getRealmId());
                        userSessionRepository.insert(realm, userSessionEntity);
                        userSessionRepository.addClientSession(realm, userSessionEntity, clientSession);

                        CassandraUserSessionAdapter adapter =
                                entityToAdapterFunc(pus.getRealm()).apply(userSessionEntity);
                        sessionModels.put(adapter.getId(), adapter);
                    }

                    return userSessionEntity;
                })
                .forEach(userSession ->
                        userSessionRepository.insert(session.realms().getRealm(userSession.getRealmId()), userSession));
    */

  }

  @Override
  public void close() {
    // Nothing to do
  }

  @Override
  public int getStartupTime(RealmModel realm) {
    return realm.getNotBefore();
  }

  /*
  private Stream<UserSession> getOfflineUserSessionEntityStream(RealmModel realm, String userSessionId) {
    if (userSessionId == null) {
      return Stream.empty();
    }

        // first get a user entity by ID
        // check if it's an offline user session
        UserSession userSessionEntity = getUserSessionById(userSessionId);
        if (userSessionEntity != null) {
            if (Boolean.TRUE.equals(userSessionEntity.getOffline())) {
                return Stream.of(userSessionEntity);
            }
        } else {
            // no session found by the given ID, try to find by corresponding session ID
            return userSessionRepository.findUserSessionsByAttribute(CORRESPONDING_SESSION_ID, userSessionId).stream();
        }

        // it's online user session so lookup offline user session by corresponding session id reference
        CassandraUserSessionAdapter sessionModel = sessionModels.get(userSessionId);
        String offlineUserSessionId = sessionModel != null
                ? sessionModel.getNote(CORRESPONDING_SESSION_ID)
                : userSessionEntity.getNotes().get(CORRESPONDING_SESSION_ID);
        if (offlineUserSessionId != null) {
            return Stream.ofNullable(getUserSessionById(offlineUserSessionId));
        }

        return Stream.empty();
    }

    private UserSession getUserSessionById(String id) {
        if (id == null) return null;

        UserSession userSessionEntity = transientUserSessions.get(id);

        if (userSessionEntity == null) {
            return userSessionRepository.findUserSessionById(id);
        }
        return userSessionEntity;
    }
  */

  private RedisUserSessionAdapter createUserSessionEntityInstance(
      UserSessionModel userSession, boolean offline) {
    RedisUserSessionAdapter entity =
        createUserSessionEntityInstance(
            null,
            userSession.getRealm().getId(),
            userSession.getUser().getId(),
            userSession.getLoginUsername(),
            userSession.getIpAddress(),
            userSession.getAuthMethod(),
            userSession.isRememberMe(),
            userSession.getBrokerSessionId(),
            userSession.getBrokerUserId(),
            offline);

    entity.setNotes(userSession.getNotes());
    entity.setState(userSession.getState());
    // entity.setTimestamp(userSession.getStarted()); TODO
    entity.setLastSessionRefresh(userSession.getLastSessionRefresh());
    return entity;
  }

  private RedisUserSessionAdapter createUserSessionEntityInstance(
      String id,
      String realmId,
      String userId,
      String loginUsername,
      String ipAddress,
      String authMethod,
      boolean rememberMe,
      String brokerSessionId,
      String brokerUserId,
      boolean offline) {
    int timestamp = Time.currentTime();
    id = id == null ? KeycloakModelUtils.generateId() : id;
    RedisUserSessionAdapter entity = userSessionTrx.get(new UserSessionKey(id));
    entity.setUserId(userId);
    entity.setRealmId(realmId);
    entity.setLoginUsername(loginUsername);
    entity.setIpAddress(ipAddress);
    entity.setAuthMethod(authMethod);
    entity.setRememberMe(rememberMe);
    entity.setBrokerSessionId(brokerSessionId);
    entity.setBrokerUserId(brokerUserId);
    entity.setOffline(offline);
    // entity.setTimestamp(timestamp); TODO
    entity.setLastSessionRefresh(timestamp);
    return entity;
  }

  private RedisAuthenticatedClientSessionAdapter createAuthenticatedClientSessionEntityInstance(
      String id, String clientId, boolean offline) {
    int timestamp = Time.currentTime();
    id = id == null ? KeycloakModelUtils.generateId() : id;
    RedisAuthenticatedClientSessionAdapter entity =
        clientSessionTrx.get(new AuthenticatedClientSessionKey(id));
    // TODO offline?
    entity.setClientUuid(clientId);
    entity.setTimestamp(timestamp);
    return entity;
  }

  private RedisAuthenticatedClientSessionAdapter createAuthenticatedClientSessionInstance(
      AuthenticatedClientSessionModel clientSession, boolean offline) {
    RedisAuthenticatedClientSessionAdapter entity =
        createAuthenticatedClientSessionEntityInstance(
            null, clientSession.getClient().getId(), offline);
    entity.setAction(clientSession.getAction());
    entity.setProtocol(clientSession.getProtocol());
    entity.setNotes(clientSession.getNotes());
    entity.setRedirectUri(clientSession.getRedirectUri());
    entity.setTimestamp(clientSession.getTimestamp());
    return entity;
  }
}
