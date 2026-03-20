package io.phasetwo.keycloak.redis.cluster.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

public abstract class RealmRemovedEventMixin {

  @JsonCreator
  public RealmRemovedEventMixin(
      @JsonProperty(value = "id", required = true) @JsonSetter(nulls = Nulls.AS_EMPTY) String id,
      @JsonProperty("realmName") @JsonSetter(nulls = Nulls.AS_EMPTY) String realmName) {}
}
