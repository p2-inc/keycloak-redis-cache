package io.phasetwo.keycloak.jpacache.cluster.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.Map;

public abstract class ClientRemovedEventMixin {

  @JsonCreator
  public ClientRemovedEventMixin(
      @JsonProperty(value = "id", required = true) @JsonSetter(nulls = Nulls.AS_EMPTY) String id,
      @JsonProperty(value = "realmId", required = true) @JsonSetter(nulls = Nulls.AS_EMPTY)
          String realmId,
      @JsonProperty(value = "clientId", required = true) @JsonSetter(nulls = Nulls.AS_EMPTY)
          String clientId,
      @JsonProperty(value = "clientRoles", required = true) @JsonSetter(nulls = Nulls.AS_EMPTY)
          Map<String, String> clientRoles) {}
}
