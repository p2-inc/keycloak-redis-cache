package io.phasetwo.keycloak.redis.connection;

public enum RedisMode {
  STANDALONE,
  SENTINEL,
  CLUSTER,
  /**
   * AWS MemoryDB: a cluster-enabled server that enforces hash slots, but is reached over a single
   * standalone endpoint (e.g. an SSM tunnel) rather than via cluster discovery. Connects like {@link
   * #STANDALONE} but commits per-slot like {@link #CLUSTER}.
   */
  MEMORY_DB
}
