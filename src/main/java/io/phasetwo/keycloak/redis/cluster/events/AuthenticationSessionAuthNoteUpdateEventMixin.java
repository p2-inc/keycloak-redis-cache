package io.phasetwo.keycloak.redis.cluster.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.Map;

public abstract class AuthenticationSessionAuthNoteUpdateEventMixin {

  @JsonCreator
  public AuthenticationSessionAuthNoteUpdateEventMixin(
      @JsonProperty("authSessionId") @JsonSetter(nulls = Nulls.AS_EMPTY) String authSessionId,
      @JsonProperty("tabId") @JsonSetter(nulls = Nulls.AS_EMPTY) String tabId,
      @JsonProperty("authNotesFragment") @JsonSetter(nulls = Nulls.AS_EMPTY)
          Map<String, String> authNotesFragment) {}
}
