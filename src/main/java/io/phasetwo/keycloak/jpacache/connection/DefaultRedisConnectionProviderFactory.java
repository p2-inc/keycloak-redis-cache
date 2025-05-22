package io.phasetwo.keycloak.jpacache.connection;

import com.google.auto.service.AutoService;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@JBossLog
@AutoService(RedisConnectionProviderFactory.class)
public class DefaultRedisConnectionProviderFactory
        implements RedisConnectionProviderFactory<RedisConnectionProvider>,
                EnvironmentDependentProviderFactory {
    public static final String PROVIDER_ID = "default";

    private JedisCluster jedisCluster;

    @Override
    public RedisConnectionProvider create(KeycloakSession session) {
        return new RedisConnectionProvider() {

            @Override
            public JedisCluster geJedisCluster() {
                return jedisCluster;
            }

            @Override
            public void close() {}
        };
    }

    @Override
    public void init(Config.Scope scope) {

        String contactPoints = scope.get("contactPoints");
        log.infov("Init CassandraProviderFactory with contactPoints {0}", contactPoints);

        int port = Integer.parseInt(scope.get("port"));
        String username = scope.get("username");
        String password = scope.get("password");

        Set<HostAndPort> nodes = Arrays.stream(contactPoints.split(","))
                .map(cp -> new HostAndPort(cp, port))
                .collect(Collectors.toSet());
        jedisCluster = new JedisCluster(nodes, username, password);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        return true;
    }

    @Override
    public void close() {
        jedisCluster.close();
    }
}
