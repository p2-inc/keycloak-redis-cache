package io.phasetwo.keycloak.jpacache.cluster.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

public abstract class RoleRemovedEventMixin {

  @JsonCreator
  public RoleRemovedEventMixin(
      @JsonProperty(value = "id", required = true) @JsonSetter(nulls = Nulls.AS_EMPTY) String id,
      @JsonProperty(value = "containerId", required = true) @JsonSetter(nulls = Nulls.AS_EMPTY)
          String containerId,
      @JsonProperty(value = "roleName", required = true) @JsonSetter(nulls = Nulls.AS_EMPTY)
          String roleName) {}
}
