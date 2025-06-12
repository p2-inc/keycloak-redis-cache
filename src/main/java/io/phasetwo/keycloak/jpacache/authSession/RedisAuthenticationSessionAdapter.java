package io.phasetwo.keycloak.jpacache.authSession;

import com.google.common.collect.ImmutableMap;
import io.phasetwo.keycloak.jpacache.MapEntity;
import java.util.Map;
import java.util.Set;
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

  public RedisAuthenticationSessionAdapter(KeycloakSession session, String tabId) {
    this(session, tabId, null);
  }

  public RedisAuthenticationSessionAdapter(
      KeycloakSession session, String tabId, Map<String, String> existingData) {
    super(new AuthenticationSessionKey(tabId), existingData);
    this.session = session;
    setField("tabId", tabId);
  }

  @Override
  public Map<String, String> getSecondaryIndexes() {
    ImmutableMap.Builder<String, String> b = ImmutableMap.builder();
    // b.put(String.format("auth-session:something-index:%s", getTabId());
    return b.build();
  }

  @Override
  public String getTabId() {
    return getString("tabId");
  }

  @Override
  public RootAuthenticationSessionModel getParentSession() {
    return parent;
  }

  public void setParentSession(RootAuthenticationSessionModel parent) {
    this.parent = parent;
  }

  @Override
  public RealmModel getRealm() {
    return parent.getRealm();
  }

  @Override
  public ClientModel getClient() {
    // return getRealm().getClientById(updater.getEntity().getClientUUID());
    return null;
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
  public Set<String> getClientScopes() {
    // todo
    return null;
  }

  @Override
  public void setClientScopes(Set<String> clientScopes) {
    // todo
  }

  @Override
  public String getProtocol() {
    return getString("protocol");
  }

  @Override
  public void setProtocol(String protocol) {
    setField("protocol", protocol);
  }

  @Override
  public String getClientNote(String name) {
    // todo
    return null;
  }

  @Override
  public void setClientNote(String name, String value) {
    // todo
  }

  @Override
  public void removeClientNote(String name) {
    // todo
  }

  @Override
  public Map<String, String> getClientNotes() {
    // todo
    return null;
  }

  @Override
  public void clearClientNotes() {
    // todo
  }

  @Override
  public String getAuthNote(String name) {
    // todo
    return null;
  }

  @Override
  public void setAuthNote(String name, String value) {
    // todo

  }

  @Override
  public void removeAuthNote(String name) {
    // todo

  }

  @Override
  public void clearAuthNotes() {
    // todo
  }

  @Override
  public void setUserSessionNote(String name, String value) {
    // todo
  }

  @Override
  public Map<String, String> getUserSessionNotes() {
    // todo
    return null;
  }

  @Override
  public void clearUserSessionNotes() {
    // todo

  }

  @Override
  public Set<String> getRequiredActions() {
    // todo
    return null;
  }

  @Override
  public void addRequiredAction(String action) {
    // todo
  }

  @Override
  public void removeRequiredAction(String action) {
    // todo
  }

  @Override
  public void addRequiredAction(UserModel.RequiredAction action) {
    // todo

  }

  @Override
  public void removeRequiredAction(UserModel.RequiredAction action) {
    // todo

  }

  @Override
  public Map<String, AuthenticationSessionModel.ExecutionStatus> getExecutionStatus() {
    // todo
    return null;
  }

  @Override
  public void setExecutionStatus(
      String authenticator, AuthenticationSessionModel.ExecutionStatus status) {
    // todo
  }

  @Override
  public void clearExecutionStatus() {
    // todo
  }

  @Override
  public UserModel getAuthenticatedUser() {
    // todo

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
    return null;
  }

  @Override
  public void setAuthenticatedUser(UserModel user) {
    // todo
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
