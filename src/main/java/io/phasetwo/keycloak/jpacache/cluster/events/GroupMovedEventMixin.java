package io.phasetwo.keycloak.jpacache.cluster.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

public abstract class GroupMovedEventMixin {

  @JsonCreator
  public GroupMovedEventMixin(
      @JsonProperty(value = "id", required = true) @JsonSetter(nulls = Nulls.AS_EMPTY) String id,
      @JsonProperty("newParentId") String newParentId,
      @JsonProperty("oldParentId") String oldParentId,
      @JsonProperty(value = "realmId", required = true) @JsonSetter(nulls = Nulls.AS_EMPTY)
          String realmId) {}
}
