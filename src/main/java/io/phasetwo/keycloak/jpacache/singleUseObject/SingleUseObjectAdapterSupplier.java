package io.phasetwo.keycloak.jpacache.singleUseObject;

import io.phasetwo.keycloak.jpacache.AdapterSupplier;
import java.util.Map;
import org.keycloak.models.KeycloakSession;
import redis.clients.jedis.Jedis;

public class SingleUseObjectAdapterSupplier
    implements AdapterSupplier<SingleUseObjectKey, RedisSingleUseObjectAdapter> {

  private final KeycloakSession session;
  private final Jedis jedis;

  public SingleUseObjectAdapterSupplier(KeycloakSession session, Jedis jedis) {
    this.session = session;
    this.jedis = jedis;
  }

  @Override
  public RedisSingleUseObjectAdapter newInstance(SingleUseObjectKey key) {
    return new RedisSingleUseObjectAdapter(session, key.name());
  }

  @Override
  public RedisSingleUseObjectAdapter newInstance(SingleUseObjectKey key, Map<String, String> data) {
    return new RedisSingleUseObjectAdapter(session, key.name(), data);
  }
}
