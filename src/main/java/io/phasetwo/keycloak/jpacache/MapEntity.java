package io.phasetwo.keycloak.jpacache;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;

public abstract class MapEntity {

  private final Map<String, String> data;
  private final Set<String> dirtyFields = Sets.newHashSet();
  private boolean markedForDelete = false;
  private boolean isNew = true;

  public MapEntity(Map<String, String> existingData) {
    this.data = Maps.newHashMap(existingData);
    if (existingData != null && !existingData.isEmpty()) {
      isNew = false;
    }
  }

  protected void setField(String key, Object value) {
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

  protected int getInt(String key, int defaultValue) {
    String val = data.get(key);
    return val != null ? Integer.parseInt(val) : defaultValue;
  }

  protected Long getLong(String key) {
    String val = data.get(key);
    return val != null ? Long.parseLong(val) : null;
  }

  protected String getString(String key) {
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
}
