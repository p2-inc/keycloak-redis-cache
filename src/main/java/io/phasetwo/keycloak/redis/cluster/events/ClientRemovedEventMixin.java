package io.phasetwo.keycloak.redis.cluster.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.Map;

public abstract class ClientRemovedEventMixin {

  @JsonCreator
  public ClientRemovedEventMixin(
      @JsonProperty(value = "id", required = true) @JsonSetter(nulls = Nulls.AS_EMPTY) String id,
      @JsonProperty("realmId") @JsonSetter(nulls = Nulls.AS_EMPTY) String realmId,
      @JsonProperty("clientId") @JsonSetter(nulls = Nulls.AS_EMPTY) String clientId,
      @JsonProperty("clientRoles") @JsonSetter(nulls = Nulls.AS_EMPTY)
          Map<String, String> clientRoles) {}
}
