package io.phasetwo.keycloak.jpacache.connection;

import com.google.auto.service.AutoService;
import io.phasetwo.keycloak.common.IsSupported;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;

@JBossLog
@AutoService(RedisConnectionProviderFactory.class)
public class DefaultRedisConnectionProviderFactory
        implements RedisConnectionProviderFactory<RedisConnectionProvider>,
                EnvironmentDependentProviderFactory, IsSupported {
    public static final String PROVIDER_ID = "default";

    private Jedis jedis;

    @Override
    public RedisConnectionProvider create(KeycloakSession session) {
        return new RedisConnectionProvider() {

            @Override
            public Jedis getJedis() {
                return jedis;
            }

            @Override
            public void close() {}
        };
    }

    @Override
    public void init(Config.Scope scope) {

        log.trace("contactPoint: " + scope.get("contactPoint"));
        String contactPoints = scope.get("contactPoint");

        log.trace("port: " + scope.get("port"));
        int port = Integer.parseInt(scope.get("port"));
        String username = scope.get("username");
        String password = scope.get("password");

        jedis = new Jedis(new HostAndPort(contactPoints, port));
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public void close() {
        jedis.close();
    }
}
