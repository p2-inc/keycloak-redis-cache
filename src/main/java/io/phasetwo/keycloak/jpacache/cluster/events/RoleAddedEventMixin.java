package io.phasetwo.keycloak.jpacache.cluster.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

public abstract class RoleAddedEventMixin {

  @JsonCreator
  public RoleAddedEventMixin(
      @JsonProperty(value = "id", required = true) @JsonSetter(nulls = Nulls.AS_EMPTY) String id,
      @JsonProperty("containerId") @JsonSetter(nulls = Nulls.AS_EMPTY) String containerId,
      @JsonProperty("roleName") String roleName) {}
}
