package io.phasetwo.keycloak.jpacache;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public abstract class MapEntity {

  private final Map<String, String> data;
  private final Set<String> dirtyFields = Sets.newHashSet();
  private final Set<String> deletedFields = Sets.newHashSet();
  private boolean markedForDelete = false;
  private boolean isNew = true;

  public MapEntity(Map<String, String> existingData) {
    if (existingData != null && !existingData.isEmpty()) {
      isNew = false;
      this.data = Maps.newHashMap(existingData);
    } else {
      this.data = Maps.newHashMap();
    }
  }

  protected void setField(String key, Object value) {
    log.debugf("setField %s %s", key, value);
    String strVal = value == null ? null : String.valueOf(value);
    String current = data.get(key);
    if (!Objects.equals(current, strVal)) {
      if (strVal == null) {
        data.remove(key);
        deletedFields.add(key);
      } else {
        data.put(key, strVal);
        dirtyFields.add(key);
        deletedFields.remove(key);
      }
    } else {
      log.debugf("field isn't different. skipping. %s %s", key, value);
    }
  }

  protected int getInt(String key, int defaultValue) {
    String val = data.get(key);
    return val != null ? Integer.parseInt(val) : defaultValue;
  }

  protected long getLong(String key, int defaultValue) {
    String val = data.get(key);
    return val != null ? Long.parseLong(val) : defaultValue;
  }

  protected String getString(String key) {
    return data.get(key);
  }

  public boolean isDirty() {
    return !dirtyFields.isEmpty() || !deletedFields.isEmpty();
  }

  public boolean isMarkedForDelete() {
    return markedForDelete;
  }

  public Map<String, String> getDirtyFields() {
    Map<String, String> dirty = Maps.newHashMap();
    for (String k : dirtyFields) {
      dirty.put(k, data.get(k));
    }
    return dirty;
  }

  public Set<String> getDeletedFields() {
    return deletedFields;
  }

  public void markForDelete() {
    markedForDelete = true;
  }
}
