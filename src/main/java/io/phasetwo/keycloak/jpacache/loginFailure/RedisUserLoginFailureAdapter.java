package io.phasetwo.keycloak.jpacache.loginFailure;

import java.util.*;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserLoginFailureModel;

public class RedisUserLoginFailureAdapter implements UserLoginFailureModel {

  private final RealmModel realm;
  private final String userId;
  private final Map<String, String> data;
  private final Set<String> dirtyFields = new HashSet<>();
  private boolean markedForDelete = false;

  public RedisUserLoginFailureAdapter(
      RealmModel realm, String userId, Map<String, String> existingData) {
    this.realm = realm;
    this.userId = userId;
    this.data = new HashMap<>(existingData);
  }

  private void setField(String key, Object value) {
    String strVal = value == null ? null : String.valueOf(value);
    String current = data.get(key);
    if (!Objects.equals(current, strVal)) {
      if (strVal == null) {
        data.remove(key);
      } else {
        data.put(key, strVal);
      }
      dirtyFields.add(key);
    }
  }

  private int getInt(String key, int defaultValue) {
    String val = data.get(key);
    return val != null ? Integer.parseInt(val) : defaultValue;
  }

  private Long getLong(String key) {
    String val = data.get(key);
    return val != null ? Long.parseLong(val) : null;
  }

  private String getString(String key) {
    return data.get(key);
  }

  public boolean isDirty() {
    return !dirtyFields.isEmpty();
  }

  public boolean isMarkedForDelete() {
    return markedForDelete;
  }

  public Map<String, String> getDirtyFields() {
    Map<String, String> dirty = new HashMap<>();
    for (String k : dirtyFields) {
      dirty.put(k, data.get(k));
    }
    return dirty;
  }

  public void markForDelete() {
    markedForDelete = true;
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
    return getInt("failedLoginNotBefore") != null ? getInt("failedLoginNotBefore") : 0;
  }

  @Override
  public void setLastFailure(long timestamp) {
    setField("lastFailure", timestamp);
  }

  @Override
  public long getLastFailure() {
    return getLong("lastFailure") != null ? getLong("lastFailure") : 0;
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
