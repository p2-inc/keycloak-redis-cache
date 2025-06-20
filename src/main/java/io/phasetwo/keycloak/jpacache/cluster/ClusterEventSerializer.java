package io.phasetwo.keycloak.jpacache.cluster;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.keycloak.cluster.ClusterEvent;
import org.keycloak.cluster.ClusterProvider.DCNotify;

public class ClusterEventSerializer {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  static {
    objectMapper.activateDefaultTyping(
        objectMapper.getPolymorphicTypeValidator(),
        ObjectMapper.DefaultTyping.NON_FINAL,
        JsonTypeInfo.As.PROPERTY);
  }

  public static String serialize(
      String eventKey, List<ClusterEvent> events, boolean ignoreSender, DCNotify dcNotify)
      throws JsonProcessingException {
    ClusterMessage message = new ClusterMessage(eventKey, events, ignoreSender, dcNotify);
    return objectMapper.writeValueAsString(message);
  }

  public static ClusterMessage deserialize(String json) throws JsonProcessingException {
    return objectMapper.readValue(json, ClusterMessage.class);
  }

  public static class ClusterMessage {
    private String eventKey;
    private List<ClusterEvent> events;
    private boolean ignoreSender;
    private DCNotify dcNotify;

    public ClusterMessage() {}

    public ClusterMessage(
        String eventKey, List<ClusterEvent> events, boolean ignoreSender, DCNotify dcNotify) {
      this.eventKey = eventKey;
      this.events = events;
      this.ignoreSender = ignoreSender;
      this.dcNotify = dcNotify;
    }

    public String getEventKey() {
      return eventKey;
    }

    public List<ClusterEvent> getEvents() {
      return events;
    }

    public boolean getIgnoreSender() {
      return ignoreSender;
    }

    public DCNotify getDcNotify() {
      return dcNotify;
    }

    public void setEventKey(String eventKey) {
      this.eventKey = eventKey;
    }

    public void setEvents(List<ClusterEvent> events) {
      this.events = events;
    }

    public void setIgnoreSender(boolean ignoreSender) {
      this.ignoreSender = ignoreSender;
    }

    public void setDcNotify(DCNotify dcNotify) {
      this.dcNotify = dcNotify;
    }
  }
}
