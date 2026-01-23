/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.phasetwo.keycloak.compatibility;

import static io.phasetwo.keycloak.common.Constants.PROVIDER_PRIORITY;
import static io.phasetwo.keycloak.common.ProviderHelpers.createProviderCached;

import com.google.auto.service.AutoService;
import io.phasetwo.keycloak.common.IsSupported;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.sessions.StickySessionEncoderProvider;
import org.keycloak.sessions.StickySessionEncoderProviderFactory;

import java.util.Objects;

/**
 * Identical with "disabled"-provider from map storage days but without environment dependent
 * activation
 */
@AutoService(StickySessionEncoderProviderFactory.class)
public class DisabledStickySessionEncoderProvider
    implements StickySessionEncoderProviderFactory, StickySessionEncoderProvider, IsSupported {

    private static final char SEPARATOR = '.';

  @Override
  public StickySessionEncoderProvider create(KeycloakSession session) {
    return createProviderCached(session, StickySessionEncoderProvider.class, () -> this);
  }

  @Override
  public String encodeSessionId(String message, String sessionId) {
      Objects.requireNonNull(message);
      String route = sessionIdRoute(sessionId);
      return route == null ? message : message + SEPARATOR + route;
  }

  @Override
  public SessionIdAndRoute decodeSessionIdAndRoute(String encodedSessionId) {
      int index = encodedSessionId.indexOf(SEPARATOR);
      int length = encodedSessionId.length();
      if (index == -1 || index == (length - 1)) {
          //route not present
          return new SessionIdAndRoute(encodedSessionId, null);
      }
      return new SessionIdAndRoute(encodedSessionId.substring(0, index), encodedSessionId.substring(index + 1, length));
  }

  @Override
  public boolean shouldAttachRoute() {
    return false;
  }

  @Override
  public String sessionIdRoute(String s) {
      return null;
  }

  @Override
  public void setShouldAttachRoute(boolean shouldAttachRoute) {}

  @Override
  public void init(Config.Scope config) {}

  @Override
  public void postInit(KeycloakSessionFactory factory) {}

  @Override
  public void close() {}

  @Override
  public String getId() {
    return "infinispan"; // use same name as infinispan provider to override it
  }

  @Override
  public int order() {
    return PROVIDER_PRIORITY + 1;
  }
}
