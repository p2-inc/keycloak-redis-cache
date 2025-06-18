package io.phasetwo.keycloak.jpacache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.util.JsonSerialization;

@JBossLog
public abstract class MapEntity<K extends Key> {

  private final K key;
  private final Map<String, String> data;
  private final Set<String> dirtyFields = Sets.newHashSet();
  private final Set<String> deletedFields = Sets.newHashSet();
  private boolean markedForDelete = false;
  private boolean isNew = true;

  protected MapEntity(K key, Map<String, String> existingData) {
    this.key = key;
    if (existingData != null && !existingData.isEmpty()) {
      isNew = false;
      this.data = Maps.newHashMap(existingData);
    } else {
      this.data = Maps.newHashMap();
    }
  }

  // maybe the data map is a Map<String,Function<Object,String>> so that if we have
  // serialized data (e.g. "notes" map fields) we can update these during the
  // session but only serialize them once if the field is dirty

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

  protected void mapToField(Map<String, String> map, String fieldName) {
    try {
      if (map == null) setField(fieldName, null);
      else setField(fieldName, JsonSerialization.writeValueAsString(map));
    } catch (Exception ignore) {
    }
  }

  protected Map<String, String> mapFromField(String fieldName) {
    String f = getString(fieldName);
    if (f != null) {
      try {
        return JsonSerialization.readValue(f, new TypeReference<Map<String, String>>() {});
      } catch (Exception ignore) {
      }
    }
    return Maps.newHashMap();
  }

  protected Set<String> setFromField(String fieldName) {
    String f = getString(fieldName);
    if (f != null) {
      try {
        return JsonSerialization.readValue(f, new TypeReference<Set<String>>() {});
      } catch (Exception ignore) {
      }
    }
    return Sets.newHashSet();
  }

  protected void setToField(Set<String> set, String fieldName) {
    try {
      if (set == null) setField(fieldName, null);
      else setField(fieldName, JsonSerialization.writeValueAsString(set));
    } catch (Exception ignore) {
    }
  }

  protected boolean getBool(String key, boolean defaultValue) {
    String val = data.get(key);
    return val != null ? Boolean.parseBoolean(val) : defaultValue;
  }

  protected int getInt(String key, int defaultValue) {
    String val = data.get(key);
    return val != null ? Integer.parseInt(val) : defaultValue;
  }

  protected long getLong(String key, long defaultValue) {
    String val = data.get(key);
    return val != null ? Long.parseLong(val) : defaultValue;
  }

  protected String getString(String key) {
    return data.get(key);
  }

  protected boolean isNull(String key) {
    return data.get(key) == null;
  }

  public Map<String, String> getMap(String prefix) {
    final String prefixWithColon = prefix + ":";

    return new AbstractMap<String, String>() {
      @Override
      public Set<Entry<String, String>> entrySet() {
        Set<Entry<String, String>> result = Sets.newHashSet();
        for (Map.Entry<String, String> entry : data.entrySet()) {
          if (entry.getKey().startsWith(prefixWithColon)) {
            String subKey = entry.getKey().substring(prefixWithColon.length());
            result.add(new AbstractMap.SimpleEntry<>(subKey, entry.getValue()));
          }
        }
        return result;
      }

      @Override
      public String get(Object key) {
        if (!(key instanceof String)) return null;
        return data.get(prefixWithColon + key);
      }

      @Override
      public String put(String key, String value) {
        String fullKey = prefixWithColon + key;
        String oldValue = data.get(fullKey);
        setField(fullKey, value);
        return oldValue;
      }

      @Override
      public String remove(Object key) {
        if (!(key instanceof String)) return null;
        String fullKey = prefixWithColon + key;
        String oldValue = data.get(fullKey);
        setField(fullKey, null);
        return oldValue;
      }

      @Override
      public boolean containsKey(Object key) {
        if (!(key instanceof String)) return false;
        return data.containsKey(prefixWithColon + key);
      }

      @Override
      public void clear() {
        Set<String> keysToRemove = Sets.newHashSet();
        for (String k : data.keySet()) {
          if (k.startsWith(prefixWithColon)) {
            keysToRemove.add(k);
          }
        }
        for (String k : keysToRemove) {
          setField(k, null);
        }
      }

      @Override
      public int size() {
        int count = 0;
        for (String k : data.keySet()) {
          if (k.startsWith(prefixWithColon)) {
            count++;
          }
        }
        return count;
      }
    };
  }

  public Set<String> getSet(String prefix) {
    final String prefixWithColon = prefix + ":";

    return new AbstractSet<String>() {

      private Set<String> computeSet() {
        Set<String> result = Sets.newHashSet();
        for (String key : data.keySet()) {
          if (key.startsWith(prefixWithColon)) {
            result.add(key.substring(prefixWithColon.length()));
          }
        }
        return result;
      }

      private String toKey(String item) {
        return prefixWithColon + item;
      }

      @Override
      public boolean add(String value) {
        String fullKey = toKey(value);
        if (data.containsKey(fullKey)) return false;
        setField(fullKey, ""); // Use empty string as value
        return true;
      }

      @Override
      public boolean remove(Object o) {
        if (!(o instanceof String)) return false;
        String fullKey = toKey((String) o);
        if (!data.containsKey(fullKey)) return false;
        setField(fullKey, null);
        return true;
      }

      @Override
      public boolean contains(Object o) {
        if (!(o instanceof String)) return false;
        return data.containsKey(toKey((String) o));
      }

      @Override
      public Iterator<String> iterator() {
        Iterator<String> base = computeSet().iterator();
        return new Iterator<String>() {
          private String current = null;

          @Override
          public boolean hasNext() {
            return base.hasNext();
          }

          @Override
          public String next() {
            current = base.next();
            return current;
          }

          @Override
          public void remove() {
            if (current != null) {
              setField(toKey(current), null);
            }
          }
        };
      }

      @Override
      public int size() {
        return computeSet().size();
      }

      @Override
      public void clear() {
        Set<String> keysToRemove = computeSet();
        for (String val : keysToRemove) {
          setField(toKey(val), null);
        }
      }
    };
  }

  // make this <Key,String>?
  public abstract Map<String, String> getSecondaryIndexes();

  public K getKey() {
    return this.key;
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
