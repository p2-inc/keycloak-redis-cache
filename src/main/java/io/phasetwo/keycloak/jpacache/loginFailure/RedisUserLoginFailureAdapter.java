package io.phasetwo.keycloak.jpacache.loginFailure;

import io.phasetwo.keycloak.jpacache.MapEntity;
import java.util.*;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserLoginFailureModel;

public class RedisUserLoginFailureAdapter extends MapEntity implements UserLoginFailureModel {

  private final RealmModel realm;
  private final String userId;

  public RedisUserLoginFailureAdapter(
      RealmModel realm, String userId, Map<String, String> existingData) {
    super(existingData);
    this.realm = realm;
    this.userId = userId;
  }

  @Override
  public String getId() {
    return getString("id");
  }

  @Override
  public String getUserId() {
    return getString("userId");
  }

  @Override
  public int getNumFailures() {
    return getInt("numFailures", 0);
  }

  @Override
  public int getNumTemporaryLockouts() {
    return getInt("numTemporaryLockouts", 0);
  }

  @Override
  public void incrementFailures() {
    setField("numFailures", getNumFailures() + 1);
  }

  @Override
  public void incrementTemporaryLockouts() {
    setField("numTemporaryLockouts", getNumTemporaryLockouts() + 1);
  }

  @Override
  public void clearFailures() {
    setField("numFailures", 0);
    setField("lastFailure", null);
    setField("lastIPFailure", null);
  }

  @Override
  public void setFailedLoginNotBefore(int notBefore) {
    setField("failedLoginNotBefore", notBefore);
  }

  @Override
  public int getFailedLoginNotBefore() {
    return getInt("failedLoginNotBefore", 0);
  }

  @Override
  public void setLastFailure(long timestamp) {
    setField("lastFailure", timestamp);
  }

  @Override
  public long getLastFailure() {
    return getLong("lastFailure", 0);
  }

  @Override
  public void setLastIPFailure(String ip) {
    setField("lastIPFailure", ip);
  }

  @Override
  public String getLastIPFailure() {
    return getString("lastIPFailure");
  }
}
