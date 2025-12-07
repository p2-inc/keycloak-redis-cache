package io.phasetwo.keycloak.jpacache.cluster.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.Map;

public abstract class AuthenticationSessionAuthNoteUpdateEventMixin {

  @JsonCreator
  public AuthenticationSessionAuthNoteUpdateEventMixin(
      @JsonProperty(value = "authSessionId", required = true) @JsonSetter(nulls = Nulls.AS_EMPTY)
          String authSessionId,
      @JsonProperty(value = "tabId", required = true) @JsonSetter(nulls = Nulls.AS_EMPTY)
          String tabId,
      @JsonProperty(value = "authNotesFragment", required = true)
          @JsonSetter(nulls = Nulls.AS_EMPTY)
          Map<String, String> authNotesFragment) {}
}
