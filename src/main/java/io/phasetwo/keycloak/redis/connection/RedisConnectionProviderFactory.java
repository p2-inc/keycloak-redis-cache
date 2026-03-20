package io.phasetwo.keycloak.redis.connection;

import org.keycloak.provider.ProviderFactory;

public interface RedisConnectionProviderFactory<T extends RedisConnectionProvider>
    extends ProviderFactory<T> {}
