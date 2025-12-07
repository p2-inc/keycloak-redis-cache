package io.phasetwo.keycloak.jpacache.cluster;

import static io.phasetwo.keycloak.common.Constants.PROVIDER_PRIORITY;
import static io.phasetwo.keycloak.common.ProviderHelpers.createProviderCached;

import com.google.auto.service.AutoService;
import io.phasetwo.keycloak.common.IsSupported;
import io.phasetwo.keycloak.jpacache.connection.RedisConnectionProvider;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.cluster.ClusterProvider;
import org.keycloak.cluster.ClusterProviderFactory;
import org.keycloak.common.util.Time;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

@JBossLog
@SuppressWarnings("rawtypes")
@AutoService(ClusterProviderFactory.class)
public class RedisPubsubClusterProviderFactory implements ClusterProviderFactory, IsSupported {

  private static final String CLUSTER_START_KEY = "kc:cluster:startTime";

  private volatile ClusterProvider clusterProvider;

  private Jedis publisher;
  private Jedis subscriber;

  private final ExecutorService localExecutor =
      Executors.newCachedThreadPool(
          r -> {
            Thread thread = Executors.defaultThreadFactory().newThread(r);
            thread.setName(this.getClass().getName() + "-" + thread.getName());
            return thread;
          });

  @Override
  public ClusterProvider create(KeycloakSession session) {
    return lazyInit(session);
  }

  private synchronized ClusterProvider lazyInit(KeycloakSession session) {
    if (clusterProvider != null) return clusterProvider;
    
    RedisConnectionProvider redisConnectionProvider =
        createProviderCached(session, RedisConnectionProvider.class);
    publisher = redisConnectionProvider.getPool().getResource();
    subscriber = redisConnectionProvider.getPool().getResource();

    int clusterStartTime = initClusterStartTime(session, publisher);

    // TODO what does this do?
    // We need CacheEntryListener for communication within current DC
    // workCache.addListener(cp.new CacheEntryListener());
    // logger.debugf("Added listener for infinispan cache: %s", workCache.getName());
    
    clusterProvider =
        new RedisPubsubClusterProvider(
            session, publisher, subscriber, clusterStartTime, localExecutor);
    return clusterProvider;
  }

  protected int initClusterStartTime(KeycloakSession session, Jedis jedis) {
    String existing = jedis.get(CLUSTER_START_KEY);
    if (existing != null) {
      int existingClusterStartTime = Integer.parseInt(existing);
      log.debugf("Loaded cluster start time: %s", Time.toDate(existingClusterStartTime).toString());
      return existingClusterStartTime;
    } else {
      int serverStartTime =
          (int) (session.getKeycloakSessionFactory().getServerStartupTimestamp() / 1000);
      String result =
          jedis.set(CLUSTER_START_KEY, String.valueOf(serverStartTime), SetParams.setParams().nx());
      if ("OK".equals(result)) {
        return serverStartTime;
      } else {
        return Integer.parseInt(jedis.get(CLUSTER_START_KEY));
      }
    }
  }

  @Override
  public void init(Config.Scope config) {}

  @Override
  public void postInit(KeycloakSessionFactory factory) {}

  @Override
  public void close() {
    try {
      if (subscriber != null) {
        subscriber.close();
      }
    } catch (Exception ignore) {}
    try {
      if (publisher != null) {
        publisher.close();
      }
    } catch (Exception ignore) {}
  }

  @Override
  public String getId() {
    return "infinispan"; // use same name as infinispan provider to override it
  }

  @Override
  public int order() {
    return PROVIDER_PRIORITY + 1;
  }
}
