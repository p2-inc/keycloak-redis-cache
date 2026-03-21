package io.phasetwo.keycloak.redis.cluster;

import java.util.List;
import java.util.concurrent.*;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.cluster.ClusterEvent;
import org.keycloak.cluster.ClusterListener;
import org.keycloak.cluster.ClusterProvider;
import org.keycloak.cluster.ClusterProvider.DCNotify;
import org.keycloak.cluster.ExecutionResult;
import org.keycloak.cluster.infinispan.TaskCallback;
import org.keycloak.common.util.ConcurrentMultivaluedHashMap;
import org.keycloak.models.*;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.utils.KeycloakModelUtils;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.SetParams;

@JBossLog
public class RedisPubsubClusterProvider implements ClusterProvider {

  public static final String TASK_KEY_PREFIX = "task::";

  private final KeycloakSession session;
  private final UnifiedJedis publisher;
  private final UnifiedJedis subscriber;
  private final int clusterStartupTime;
  private final ExecutorService executor;
  private final ConcurrentMultivaluedHashMap<String, ClusterListener> listeners =
      new ConcurrentMultivaluedHashMap<>();
  private final ConcurrentMap<String, TaskCallback> taskCallbacks = new ConcurrentHashMap<>();
  private final ClusterEventListener clusterPubsub;

  private static final String CHANNEL_NAME = "keycloak-cluster";

  public RedisPubsubClusterProvider(
      KeycloakSession session,
      UnifiedJedis publisher,
      UnifiedJedis subscriber,
      int clusterStartupTime,
      ExecutorService executor) {
    this.session = session;
    this.publisher = publisher;
    this.subscriber = subscriber;
    this.clusterStartupTime = clusterStartupTime;
    this.executor = executor;
    this.clusterPubsub = new ClusterEventListener();

    executor.submit(
        () -> {
          try {
            log.debugf("creating redis pubsub subscriber for %s", CHANNEL_NAME);
            subscriber.subscribe(clusterPubsub, CHANNEL_NAME);
            log.debugf("redis pubsub subscribe method exited for %s", CHANNEL_NAME);
          } catch (Exception e) {
            log.error("Failed to subscribe to Redis channel", e);
          }
        });
  }

  private class ClusterEventListener extends JedisPubSub {
    @Override
    public void onMessage(String channel, String message) {
      log.tracef("received pubsub message on %s: %s", channel, message);
      if (CHANNEL_NAME.equals(channel)) {
        handleMessage(message);
      }
    }
  }

  @Override
  public int getClusterStartupTime() {
    return clusterStartupTime;
  }

  // @Override
  // public void notify(String taskKey, Collection<? extends ClusterEvent> events, boolean
  // ignoreSender, DCNotify dcNotify) {

  @Override
  public void notify(String taskKey, ClusterEvent event, boolean ignoreSender, DCNotify dcNotify) {
    try {
      String serialized =
          ClusterEventSerializer.serialize(taskKey, List.of(event), ignoreSender, dcNotify);
      log.debugf("notify %s: %s", taskKey, serialized);
      var response = publisher.publish(CHANNEL_NAME, serialized);
      log.debugf("notify respinded. Subscribers no.: %s", response);
    } catch (Exception e) {
      log.errorf(e, "Failed to publish cluster event %s", taskKey);
    }
  }

  private void handleMessage(String message) {
    try {
      ClusterEventSerializer.ClusterMessage deserialized =
          ClusterEventSerializer.deserialize(message);
      log.debugf("handleMessage %s %s", deserialized.getEventKey(), deserialized.getEvents());

      String eventKey = deserialized.getEventKey();
      List<ClusterListener> cls = listeners.get(eventKey);
      if (cls != null) {
        for (var e : deserialized.getEvents()) {
          cls.forEach(e);
        }
      }

    } catch (Exception e) {
      log.error("Failed to handle Redis cluster event", e);
    }
  }

  @Override
  public void registerListener(String taskKey, ClusterListener task) {
    log.debugf("registering cluster listener for %s", taskKey);
    this.listeners.add(taskKey, task);
  }

  @Override
  public <T> ExecutionResult<T> executeIfNotExecuted(
      String taskKey, int lifespanSeconds, Callable<T> task) {
    String lockKey = "kc:cluster:lock:" + taskKey;
    String taskId = KeycloakModelUtils.generateId();

    try {
      String lockResult =
          publisher.set(lockKey, taskId, SetParams.setParams().nx().ex(lifespanSeconds));
      if ("OK".equals(lockResult)) {
        try {
          try {
            T result = task.call();
            return ExecutionResult.executed(result);
          } catch (RuntimeException re) {
            throw re;
          } catch (Exception e) {
            throw new RuntimeException("Unexpected exception when executed task " + taskKey, e);
          }
        } finally {
          publisher.del(lockKey);
        }
      }
    } catch (Exception e) {
      log.warn("Error getting publisher instance", e);
    }
    return ExecutionResult.notExecuted();
  }

  @Override
  public Future<Boolean> executeIfNotExecutedAsync(
      String taskKey, int taskTimeoutInSeconds, Callable task) {
    TaskCallback newCallback = new TaskCallback();
    TaskCallback callback = registerTaskCallback(TASK_KEY_PREFIX + taskKey, newCallback);

    // We successfully submitted our task
    if (newCallback == callback) {
      Callable<Boolean> wrappedTask =
          () -> {
            boolean executed =
                executeIfNotExecuted(taskKey, taskTimeoutInSeconds, task).isExecuted();

            if (!executed) {
              log.infof(
                  "Task already in progress on other cluster node. Will wait until it's finished");
            }

            callback.getTaskCompletedLatch().await(taskTimeoutInSeconds, TimeUnit.SECONDS);
            return callback.isSuccess();
          };

      Future<Boolean> future = executor.submit(wrappedTask);
      callback.setFuture(future);
    } else {
      log.infof("Task already in progress on this cluster node. Will wait until it's finished");
    }

    return callback.getFuture();
  }

  TaskCallback registerTaskCallback(String taskKey, TaskCallback callback) {
    TaskCallback existing = taskCallbacks.putIfAbsent(taskKey, callback);
    return existing == null ? callback : existing;
  }

  @Override
  public void close() {
    /*
      if (clusterPubsub != null && clusterPubsub.isSubscribed()) {
      log.debugf("Unsubscribing from pubsub %s", CHANNEL_NAME);
      try {
        clusterPubsub.unsubscribe();
      } catch (Exception e) {
        log.warn("Error unsubscribing from cluster pubsub", e);
      }
    }
    */
  }
}
