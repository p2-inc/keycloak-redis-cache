package io.phasetwo.keycloak.redis;

import java.util.Map;

public interface AdapterSupplier<K extends Key, A extends MapEntity> {
  A newInstance(K k);

  A newInstance(K k, Map<String, String> data);
}
