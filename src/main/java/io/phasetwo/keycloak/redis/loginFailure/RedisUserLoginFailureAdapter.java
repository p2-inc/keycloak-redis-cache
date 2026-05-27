package io.phasetwo.keycloak.redis.loginFailure;

import com.google.common.collect.ImmutableMap;
import io.phasetwo.keycloak.redis.MapEntity;
import java.util.Map;
import org.keycloak.models.UserLoginFailureModel;

public class RedisUserLoginFailureAdapter extends MapEntity<LoginFailureKey>
    implements UserLoginFailureModel {

  public RedisUserLoginFailureAdapter(String realmId, String userId) {
    this(realmId, userId, null);
  }

  public RedisUserLoginFailureAdapter(
      String realmId, String userId, Map<String, String> existingData) {
    super(new LoginFailureKey(realmId, userId), existingData);
    setField("userId", userId);
    setField("realmId", realmId);
  }

  @Override
  public Map<String, String> getSecondaryIndexes() {
    ImmutableMap.Builder<String, String> b = ImmutableMap.builder();
    siPut(b, "login-failure:index:%s", getRealmId(), getUserId());
    return b.build();
  }

  public String getRealmId() {
    return getString("realmId");
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
  public int getNumSecondaryAuthFailures() {
    return getInt("numSecondaryAuthFailures", 0);
  }

  @Override
  public void incrementSecondaryAuthFailures() {
    setField("numSecondaryAuthFailures", getNumSecondaryAuthFailures() + 1);
  }

  @Override
  public void clearFailures() {
    removeField("numFailures");
    removeField("lastFailure");
    removeField("lastIPFailure");
    removeField("failedLoginNotBefore");
  }

  @Override
  public void clearPrimaryAndSecondaryAuthFailures() {
    clearFailures();
    removeField("numSecondaryAuthFailures");
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
