package io.phasetwo.keycloak.jpacache.connection;

import org.keycloak.provider.ProviderFactory;

public interface RedisConnectionProviderFactory<T extends RedisConnectionProvider>
    extends ProviderFactory<T> {}
