package io.phasetwo.keycloak.jpacache.authSession;

import com.google.common.collect.ImmutableMap;
import io.phasetwo.keycloak.jpacache.MapEntity;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;

@JBossLog
public class RedisAuthenticationSessionAdapter extends MapEntity<AuthenticationSessionKey>
    implements AuthenticationSessionModel {

  private final KeycloakSession session;

  private RootAuthenticationSessionModel parent;
  private Map<String, String> authNotes;
  private Map<String, String> clientNotes;
  private Set<String> clientScopes;
  private Map<String, String> executionStatus;
  private Set<String> requiredActions;
  private Map<String, String> userSessionNotes;

  public RedisAuthenticationSessionAdapter(KeycloakSession session, String clientId, String tabId) {
    this(session, clientId, tabId, null);
  }

  public RedisAuthenticationSessionAdapter(
      KeycloakSession session, String clientId, String tabId, Map<String, String> existingData) {

    super(new AuthenticationSessionKey(clientId, tabId), existingData);
    this.session = session;
    setField("clientUuid", clientId);
    setField("tabId", tabId);
  }

  @Override
  public Map<String, String> getSecondaryIndexes() {
    ImmutableMap.Builder<String, String> b = ImmutableMap.builder();
    b.put(String.format("auth-session:parent:%s", getString("parentId")), getKey().key());
    return b.build();
  }

  @Override
  public String getTabId() {
    return getString("tabId");
  }

  @Override
  public RootAuthenticationSessionModel getParentSession() {
    if (parent == null) {
      this.parent =
          session
              .authenticationSessions()
              .getRootAuthenticationSession(getRealm(), getString("parentId"));
    }
    return this.parent;
  }

  public void setParentSession(RootAuthenticationSessionModel parent) {
    setField("parentId", parent.getId());
    setField("realmId", parent.getRealm().getId());
    this.parent = parent;
  }

  @Override
  public RealmModel getRealm() {
    return session.realms().getRealm(getString("realmId"));
  }

  @Override
  public ClientModel getClient() {
    return getRealm().getClientById(getString("clientUuid"));
  }

  public void setClientUuid(String uuid) {
    setField("clientUuid", uuid);
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

  public String getUserId() {
    return getString("userId");
  }

  public void setUserId(String userId) {
    setField("userId", userId);
  }

  @Override
  public Set<String> getClientScopes() {
    if (clientScopes == null) {
      clientScopes = setFromField("clientScopes");
    }
    return clientScopes;
  }

  @Override
  public void setClientScopes(Set<String> clientScopes) {
    this.clientScopes = clientScopes;
    setToField(clientScopes.isEmpty() ? null : clientScopes, "clientScopes");
  }

  @Override
  public Map<String, String> getClientNotes() {
    if (clientNotes == null) {
      clientNotes = mapFromField("clientNotes");
    }
    return clientNotes;
  }

  @Override
  public String getClientNote(String name) {
    return getClientNotes().get(name);
  }

  @Override
  public void setClientNote(String name, String value) {
    getClientNotes().put(name, value);
    mapToField(clientNotes, "clientNotes");
  }

  @Override
  public void removeClientNote(String name) {
    getClientNotes().remove(name);
    mapToField(clientNotes.isEmpty() ? null : clientNotes, "clientNotes");
  }

  @Override
  public void clearClientNotes() {
    getClientNotes().clear();
    mapToField(null, "clientNotes");
  }

  public Map<String, String> getAuthNotes() {
    if (authNotes == null) {
      authNotes = mapFromField("authNotes");
    }
    return authNotes;
  }

  @Override
  public String getAuthNote(String name) {
    return getAuthNotes().get(name);
  }

  @Override
  public void setAuthNote(String name, String value) {
    getAuthNotes().put(name, value);
    mapToField(authNotes.isEmpty() ? null : authNotes, "authNotes");
  }

  @Override
  public void removeAuthNote(String name) {
    getAuthNotes().remove(name);
    mapToField(authNotes.isEmpty() ? null : authNotes, "authNotes");
  }

  @Override
  public void clearAuthNotes() {
    getAuthNotes().clear();
    mapToField(null, "authNotes");
  }

  @Override
  public void setUserSessionNote(String name, String value) {
    getUserSessionNotes().put(name, value);
    mapToField(userSessionNotes, "userSessionNotes");
  }

  @Override
  public Map<String, String> getUserSessionNotes() {
    if (userSessionNotes == null) {
      userSessionNotes = mapFromField("userSessionNotes");
    }
    return userSessionNotes;
  }

  @Override
  public void clearUserSessionNotes() {
    getUserSessionNotes().clear();
    mapToField(null, "userSessionNotes");
  }

  @Override
  public Set<String> getRequiredActions() {
    if (requiredActions == null) {
      requiredActions = setFromField("requiredActions");
    }
    return requiredActions;
  }

  @Override
  public void addRequiredAction(String action) {
    getRequiredActions().add(action);
    setToField(requiredActions, "requiredActions");
  }

  @Override
  public void removeRequiredAction(String action) {
    getRequiredActions().remove(action);
    setToField(requiredActions.isEmpty() ? null : requiredActions, "requiredActions");
  }

  @Override
  public void addRequiredAction(UserModel.RequiredAction action) {
    addRequiredAction(action.name());
  }

  @Override
  public void removeRequiredAction(UserModel.RequiredAction action) {
    removeRequiredAction(action.name());
  }

  public Map<String, String> getExecStatus() {
    if (executionStatus == null) {
      executionStatus = mapFromField("executionStatus");
    }
    return executionStatus;
  }

  @Override
  public Map<String, AuthenticationSessionModel.ExecutionStatus> getExecutionStatus() {
    return getExecStatus().entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entry -> AuthenticationSessionModel.ExecutionStatus.valueOf(entry.getValue())));
  }

  @Override
  public void setExecutionStatus(
      String authenticator, AuthenticationSessionModel.ExecutionStatus status) {
    getExecStatus().put(authenticator, status.name());
    mapToField(executionStatus, "executionStatus");
  }

  @Override
  public void clearExecutionStatus() {
    getExecStatus().clear();
    mapToField(null, "executionStatus");
  }

  @Override
  public UserModel getAuthenticatedUser() {
    // todo
    log.trace("getAuthenticatedUser");
    if (getUserId() == null) return null;
    return session.users().getUserById(getRealm(), getUserId());

    /*
    if (updater.getEntity().getAuthUserId() == null) {
      return null;
    }

    if (Profile.isFeatureEnabled(Feature.TRANSIENT_USERS) && getUserSessionNotes().containsKey(SESSION_NOTE_LIGHTWEIGHT_USER)) {
      LightweightUserAdapter cachedUser = session.getAttribute("authSession.user." + parent.getId(), LightweightUserAdapter.class);

      if (cachedUser != null) {
        return cachedUser;
      }

      LightweightUserAdapter lua = LightweightUserAdapter.fromString(session, parent.getRealm(), getUserSessionNotes().get(SESSION_NOTE_LIGHTWEIGHT_USER));
      session.setAttribute("authSession.user." + parent.getId(), lua);
      lua.setUpdateHandler(lua1 -> {
          if (lua == lua1) {  // Ensure there is no conflicting user model, only the latest lightweight user can be used
            setUserSessionNote(SESSION_NOTE_LIGHTWEIGHT_USER, lua1.serialize());
          }
        });

      return lua;
    } else {
      return session.users().getUserById(getRealm(), updater.getEntity().getAuthUserId());
    }
    */
  }

  @Override
  public void setAuthenticatedUser(UserModel user) {
    // todo
    log.tracef("setAuthenticatedUser %s", user);
    if (user == null) {
      setUserId(null);
    } else {
      setUserId(user.getId());
    }

    /*
    if (user == null) {
      updater.getEntity().setAuthUserId(null);
      setUserSessionNote(SESSION_NOTE_LIGHTWEIGHT_USER, null);
    } else {
      updater.getEntity().setAuthUserId(user.getId());

      if (isLightweightUser(user)) {
        LightweightUserAdapter lua = (LightweightUserAdapter) user;
        setUserSessionNote(SESSION_NOTE_LIGHTWEIGHT_USER, lua.serialize());
        lua.setUpdateHandler(lua1 -> {
            if (lua == lua1) {  // Ensure there is no conflicting user model, only the latest lightweight user can be used
              setUserSessionNote(SESSION_NOTE_LIGHTWEIGHT_USER, lua1.serialize());
            }
          });
      }
    }
    update();
    */
  }

  @Override
  public boolean equals(Object o) {
    return this == o
        || o instanceof AuthenticationSessionModel that && that.getTabId().equals(getTabId());
  }

  @Override
  public int hashCode() {
    return getTabId().hashCode();
  }
}
