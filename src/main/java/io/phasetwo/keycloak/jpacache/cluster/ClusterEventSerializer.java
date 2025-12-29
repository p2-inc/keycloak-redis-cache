package io.phasetwo.keycloak.jpacache.cluster;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.phasetwo.keycloak.jpacache.cluster.events.*;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.cluster.ClusterEvent;
import org.keycloak.cluster.ClusterProvider.DCNotify;
import org.keycloak.models.cache.infinispan.events.*;

@JBossLog
public class ClusterEventSerializer {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  static {
    objectMapper.activateDefaultTyping(
        objectMapper.getPolymorphicTypeValidator(),
        ObjectMapper.DefaultTyping.NON_FINAL,
        JsonTypeInfo.As.PROPERTY);

    objectMapper.addMixIn(InvalidationEvent.class, InvalidationEventMixin.class);
    objectMapper.addMixIn(CacheKeyInvalidatedEvent.class, CacheKeyInvalidatedEventMixin.class);
    objectMapper.addMixIn(ClientAddedEvent.class, ClientAddedEventMixin.class);
    objectMapper.addMixIn(ClientRemovedEvent.class, ClientRemovedEventMixin.class);
    objectMapper.addMixIn(ClientScopeAddedEvent.class, ClientScopeAddedEventMixin.class);
    objectMapper.addMixIn(ClientScopeRemovedEvent.class, ClientScopeRemovedEventMixin.class);
    objectMapper.addMixIn(ClientUpdatedEvent.class, ClientUpdatedEventMixin.class);
    objectMapper.addMixIn(GroupAddedEvent.class, GroupAddedEventMixin.class);
    objectMapper.addMixIn(GroupMovedEvent.class, GroupMovedEventMixin.class);
    objectMapper.addMixIn(GroupRemovedEvent.class, GroupRemovedEventMixin.class);
    objectMapper.addMixIn(GroupUpdatedEvent.class, GroupUpdatedEventMixin.class);
    objectMapper.addMixIn(RealmRemovedEvent.class, RealmRemovedEventMixin.class);
    objectMapper.addMixIn(RealmUpdatedEvent.class, RealmUpdatedEventMixin.class);
    objectMapper.addMixIn(RoleAddedEvent.class, RoleAddedEventMixin.class);
    objectMapper.addMixIn(RoleRemovedEvent.class, RoleRemovedEventMixin.class);
    objectMapper.addMixIn(RoleUpdatedEvent.class, RoleUpdatedEventMixin.class);
    objectMapper.addMixIn(
        UserCacheRealmInvalidationEvent.class, UserCacheRealmInvalidationEventMixin.class);
    objectMapper.addMixIn(UserConsentsUpdatedEvent.class, UserConsentsUpdatedEventMixin.class);
    objectMapper.addMixIn(
        UserFederationLinkRemovedEvent.class, UserFederationLinkRemovedEventMixin.class);
    objectMapper.addMixIn(
        UserFederationLinkUpdatedEvent.class, UserFederationLinkUpdatedEventMixin.class);
    objectMapper.addMixIn(UserFullInvalidationEvent.class, UserFullInvalidationEventMixin.class);
    objectMapper.addMixIn(UserUpdatedEvent.class, UserUpdatedEventMixin.class);
    objectMapper.addMixIn(
        AuthenticationSessionAuthNoteUpdateEvent.class,
        AuthenticationSessionAuthNoteUpdateEventMixin.class);

    /*
    SimpleModule module = new SimpleModule();
    module.addDeserializer(UserUpdatedEvent.class, new UserUpdatedEventDeserializer());
    objectMapper.registerModule(module);
    */
  }

  public static String serialize(
      String eventKey, List<ClusterEvent> events, boolean ignoreSender, DCNotify dcNotify)
      throws JsonProcessingException {
    ClusterMessage message = new ClusterMessage(eventKey, events, ignoreSender, dcNotify);
    String json = objectMapper.writeValueAsString(message);
    log.debugf("Serialized JSON: %s", json);
    return json;
  }

  public static ClusterMessage deserialize(String json) throws JsonProcessingException {
    log.debugf("Deserializing JSON: %s", json);
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
