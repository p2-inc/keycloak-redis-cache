package io.phasetwo.keycloak.jpacache.singleUseObject;

import static org.keycloak.common.util.StackUtil.getShortStackTrace;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.SingleUseObjectProvider;
import redis.clients.jedis.Jedis;

@JBossLog
@RequiredArgsConstructor
public class RedisCacheSingleUseObjectProvider implements SingleUseObjectProvider {
  private final KeycloakSession session;
  private final Jedis jedis;
  private final ObjectMapper objectMapper = new ObjectMapper(); // Reuse ObjectMapper

  private byte[] serializeMapToJsonBytes(Map<String, String> map) throws JsonProcessingException {
    return objectMapper.writeValueAsBytes(map);
  }
  @SuppressWarnings("unchecked")
  private Map<String, String> deserializeMap(byte[] bytes) throws IOException, ClassNotFoundException {
    return objectMapper.readValue(bytes, new TypeReference<>() {
    });
  }

  @Override
  public void put(String key, long lifespanSeconds, Map<String, String> notes) {
    log.tracef("put(%s)%s", key, notes);
    try {
      byte[] notesBytes = serializeMapToJsonBytes(notes);
      jedis.set(key.getBytes(), notesBytes); // Use the byte[] version of set

      if (lifespanSeconds > 0) {
        jedis.expire(key.getBytes(), lifespanSeconds);
      }
    } catch (JsonProcessingException e) {
      log.errorf("Failed to serialize notes to JSON for key %s: %s", key, e.getMessage());
      // Handle exception
    }
  }


  @Override
  public Map<String, String> get(String key) {
    log.tracef("get(%s)%s", key, getShortStackTrace());

    try {
      var notesByte = jedis.get(key.getBytes());
      return deserializeMap(notesByte);
    } catch (IOException | ClassNotFoundException e) {
      log.errorf("Failed to deserialize notes for key %s: %s", key, e.getMessage());
      // Handle exception
    }

    return null;
  }

  @Override
  public Map<String, String> remove(String key) {
    try {
      var notesByte = jedis.get(key.getBytes());
      jedis.del(key.getBytes());
      return deserializeMap(notesByte);
    } catch (IOException | ClassNotFoundException e) {
      log.errorf("Failed to deserialize notes for key %s: %s", key, e.getMessage());
      // Handle exception
    }

    return null;
  }

  @Override
  public boolean replace(String key, Map<String, String> notes) {
    log.tracef("replace(%s)%s", key, getShortStackTrace());
    try {
      var notesByte = jedis.get(key.getBytes());
      var existingMap = deserializeMap(notesByte);
      existingMap.putAll(notes);

      return true;
    } catch (IOException | ClassNotFoundException e) {
      log.errorf("Failed to deserialize notes for key %s: %s", key, e.getMessage());
      // Handle exception
    }

    return false;
  }

  @Override
  public boolean putIfAbsent(String key, long lifespanSeconds) {
    log.tracef("putIfAbsent(%s)%s", key, getShortStackTrace());
    if (jedis.exists(key.getBytes())) {
      jedis.expire(key.getBytes(), lifespanSeconds);
      return true;
    }

    return false;
  }

  @Override
  public boolean contains(String key) {
    return jedis.exists(key.getBytes());
  }

  @Override
  public void close() {
    // Nothing to do
  }
}
