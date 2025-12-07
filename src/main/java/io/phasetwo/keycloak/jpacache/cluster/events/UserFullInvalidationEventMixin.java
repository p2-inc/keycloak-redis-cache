package io.phasetwo.keycloak.jpacache.cluster.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.Map;

public abstract class UserFullInvalidationEventMixin {

  @JsonCreator
  public UserFullInvalidationEventMixin(
      @JsonProperty(value = "id", required = true) @JsonSetter(nulls = Nulls.AS_EMPTY) String id,
      @JsonProperty(value = "username", required = true) @JsonSetter(nulls = Nulls.AS_EMPTY)
          String username,
      @JsonProperty("email") String email,
      @JsonProperty(value = "realmId", required = true) @JsonSetter(nulls = Nulls.AS_EMPTY)
          String realmId,
      @JsonProperty("identityFederationEnabled") boolean identityFederationEnabled,
      @JsonProperty("federatedIdentities") Map<String, String> federatedIdentities) {}
}
