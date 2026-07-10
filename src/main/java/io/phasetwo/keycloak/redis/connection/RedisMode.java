package io.phasetwo.keycloak.redis.connection;

public enum RedisMode {
  STANDALONE,
  SENTINEL,
  CLUSTER,
  MEMORYDB_MULTIREGION
}
