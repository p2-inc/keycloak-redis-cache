package io.phasetwo.keycloak.jpacache.loginFailure;

import io.phasetwo.keycloak.jpacache.Key;

// public record LoginFailureKey(RealmModel realm, String userId) implements Key {
public record LoginFailureKey(String realmId, String userId) implements Key {
  @Override
  public String key() {
    // return String.format("login-failure:%s:%s" + realm.getId() + ":" + userId;
    return String.format("login-failure:%s:%s", realmId, userId);
  }
}
