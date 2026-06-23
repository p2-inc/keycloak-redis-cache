package io.phasetwo.keycloak.redis;

import static io.phasetwo.keycloak.redis.RedisMetrics.*;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.phasetwo.keycloak.common.ExpirableEntity;
import io.phasetwo.keycloak.common.ExpirationUtils;
import io.phasetwo.keycloak.redis.connection.RedisMode;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.AbstractKeycloakTransaction;
import redis.clients.jedis.AbstractPipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.UnifiedJedis;

/**
 * Buffers reads and mutations for a single Keycloak transaction and flushes them to Redis on commit
 * as dynamically assembled Lua scripts (see {@link LuaCommitScriptBuilder}).
 *
 * <p>This base owns all shared state, the read/mutate API, the {@link #commitImpl()} template, and
 * the all-or-nothing rebase-and-retry loop. The two concrete subclasses differ only in how the
 * commit is grouped into scripts: {@link StandaloneRedisChangelogTransaction} emits one script for
 * the whole commit, while {@link ClusterRedisChangelogTransaction} groups by hash slot.
 */
@JBossLog
public abstract class RedisChangelogTransaction<K extends Key, A extends MapEntity<K>>
    extends AbstractKeycloakTransaction {

  protected final Map<K, A> cache = Maps.newHashMap();
  protected final Map<K, A> toDelete = Maps.newHashMap();
  protected final AdapterSupplier<K, A> adapterSupplier;
  protected final UnifiedJedis jedis;
  protected final RedisMode redisMode;
  protected final String cacheName;
  protected final LuaCommitScriptBuilder<K, A> scriptBuilder;
  private final Meter.MeterProvider<Counter> counterProvider;
  protected static final int MAX_CAS_RETRIES = 3;

  protected RedisChangelogTransaction(
      String cacheName,
      UnifiedJedis jedis,
      RedisMode redisMode,
      AdapterSupplier<K, A> adapterSupplier) {
    this.cacheName = cacheName;
    this.jedis = jedis;
    this.redisMode = redisMode;
    this.adapterSupplier = adapterSupplier;
    this.counterProvider = getCacheCounterProvider();
    this.scriptBuilder = new LuaCommitScriptBuilder<>(this::countOperation);
  }

  /** Creates the transaction implementation appropriate for the given Redis mode. */
  public static <K extends Key, A extends MapEntity<K>> RedisChangelogTransaction<K, A> create(
      String cacheName,
      UnifiedJedis jedis,
      RedisMode redisMode,
      AdapterSupplier<K, A> adapterSupplier) {
    if (redisMode == RedisMode.CLUSTER) {
      return new ClusterRedisChangelogTransaction<>(cacheName, jedis, redisMode, adapterSupplier);
    }
    return new StandaloneRedisChangelogTransaction<>(cacheName, jedis, redisMode, adapterSupplier);
  }

  public static <K extends Key, A extends MapEntity<K>> RedisChangelogTransaction<K, A> create(
      String cacheName, UnifiedJedis jedis, AdapterSupplier<K, A> adapterSupplier) {
    return create(cacheName, jedis, RedisMode.STANDALONE, adapterSupplier);
  }

  /** Count an operation in metrics */
  void countOperation(String op) {
    List<Tag> tags = Lists.newArrayList();
    tags.add(Tag.of(CACHE_TAG, cacheName));
    tags.add(Tag.of(OPERATION_TAG, op));
    counterProvider.withTags(tags).increment();
  }

  public static final String HGETALL = "HGETALL";
  public static final String HSETEX = "HSETEX";
  public static final String HSET = "HSET";
  public static final String SADD = "SADD";
  public static final String HDEL = "HDEL";
  public static final String SREM = "SREM";
  public static final String DEL = "DEL";
  public static final String WATCH = "WATCH";

  /**
   * Gets the value if present at the key. Creates a new instance and registers it for saving using
   * the adapter supplier if none is present at the key.
   */
  public A get(K k) {
    A model = getIfPresent(k);
    if (model == null) {
      model = adapterSupplier.newInstance(k);
      cache.put(k, model);
    }
    return model;
  }

  /** Gets the value only if present at the key. Returns null otherwise. */
  public A getIfPresent(K k) {
    if (k == null) return null;
    if (toDelete.containsKey(k)) return null; // this is wrong
    A model = cache.get(k);
    if (model != null && !expired(k, model)) return model;
    String key = k.key();
    log.tracef("[redis] HGETALL %s", key);
    Map<String, String> data = jedis.hgetAll(key);
    countOperation(HGETALL);
    if (data != null && !data.isEmpty()) {
      log.tracef("found data for %s %s", key, data);
      model = adapterSupplier.newInstance(k, data);
      if (!expired(k, model)) {
        cache.put(k, model);
        return model;
      }
    }
    return null;
  }

  /** Lazy removal. Check to see if an entity is expired. return true if it was. add it toDelete. */
  private boolean expired(K k, A a) {
    if (a instanceof ExpirableEntity) {
      ExpirableEntity e = (ExpirableEntity) a;
      if (ExpirationUtils.isExpired(e, true)) {
        log.tracef("Entity at %s expired %s. Lazy removing.", k, ExpirationUtils.fromNow(e));
        addForDelete(a);
        return true;
      } else {
        log.tracef("Entity at %s active. Expires in %s.", k, ExpirationUtils.fromNow(e));
        return false;
      }
    }
    log.tracef("Entity at %s is not an expirable entity.", k);
    return false;
  }

  /**
   * Gets the a map of value if present at the given keys. Return value is a map of the key to the
   * value. May be fewer results if some keys don't have values.
   */
  public Map<K, A> getAll(Collection<K> keys) {
    if (keys == null || keys.isEmpty()) return Maps.newLinkedHashMap();
    AbstractPipeline pipeline = jedis.pipelined();
    Map<K, Response<Map<String, String>>> responses = Maps.newLinkedHashMap();
    Map<K, A> result = Maps.newLinkedHashMap();

    // Queue all HGETALLs
    for (K key : keys) {
      if (toDelete.containsKey(key)) continue;
      A model = cache.get(key);
      if (model != null) {
        result.put(key, model);
      } else {
        log.tracef("[redis] HGETALL %s", key.key());
        responses.put(key, pipeline.hgetAll(key.key()));
      }
    }
    if (!responses.isEmpty()) { // only execute if some were not cached
      pipeline.sync(); // flush and read all in one round-trip
      // Build result map
      for (Map.Entry<K, Response<Map<String, String>>> entry : responses.entrySet()) {
        K key = entry.getKey();
        Map<String, String> data = entry.getValue().get();
        if (data != null && !data.isEmpty()) {
          A model = adapterSupplier.newInstance(key, data);
          if (!expired(key, model)) {
            result.put(key, model);
            cache.put(key, model);
          }
        }
      }
    }
    return result;
  }

  public void addForSave(A model) {
    cache.put(model.getKey(), model);
  }

  public void addForDelete(A model) {
    toDelete.put(model.getKey(), model);
  }

  public void cachedToDelete() {
    for (A model : cache.values()) {
      addForDelete(model);
    }
  }

  @Override
  protected void commitImpl() {
    if (cache.isEmpty() && toDelete.isEmpty()) {
      log.trace("nothing to commit. skipping transaction...");
      return;
    }

    // Partition into writes (dirty, not deleted) and deletes (marked, or queued in toDelete),
    // deduped by key. Matches the selection of the previous two commit loops.
    Map<K, A> writes = Maps.newLinkedHashMap();
    Map<K, A> deletes = Maps.newLinkedHashMap();
    for (A model : Lists.newArrayList(cache.values())) {
      K key = model.getKey();
      if (model.isMarkedForDelete() || toDelete.containsKey(key)) {
        deletes.put(key, model);
      } else if (model.isDirty()) {
        writes.put(key, model);
      }
    }
    for (A model : Lists.newArrayList(toDelete.values())) {
      deletes.putIfAbsent(model.getKey(), model);
    }

    if (writes.isEmpty() && deletes.isEmpty()) {
      log.trace("no dirty entities to commit. skipping transaction...");
      return;
    }

    flushCommit(Lists.newArrayList(writes.values()), Lists.newArrayList(deletes.values()));
    toDelete.clear();
  }

  /** Builds and executes the commit scripts for the given partition. */
  protected abstract void flushCommit(List<A> writes, List<A> deletes);

  /**
   * Evaluates each CAS-protected script with all-or-nothing semantics, rebasing and rebuilding any
   * script that reports a version conflict until it succeeds or {@link #MAX_CAS_RETRIES} is hit.
   *
   * <p>Because each script applies nothing on conflict, a successful script is dropped and never
   * re-run, so already-incremented versions can never cause a false conflict.
   */
  protected final void runWithRetries(List<LuaCommitScriptBuilder.BuiltScript<K, A>> initial) {
    List<LuaCommitScriptBuilder.BuiltScript<K, A>> scripts = initial;
    for (int attempt = 0; ; attempt++) {
      List<LuaCommitScriptBuilder.BuiltScript<K, A>> pending = Lists.newArrayList();
      for (LuaCommitScriptBuilder.BuiltScript<K, A> s : scripts) {
        List<String> conflicts = evalConflicts(s);
        if (conflicts.isEmpty()) {
          continue;
        }
        if (attempt == MAX_CAS_RETRIES) {
          throw new IllegalStateException(
              String.format(
                  "Redis CAS failed for keys %s after %d attempts", conflicts, attempt + 1));
        }
        log.warnf(
            "[redis] CAS conflict for keys %s (attempt %d). rebasing and retrying.",
            conflicts, attempt + 1);
        Set<String> conflictKeys = Sets.newHashSet(conflicts);
        List<A> rebased = Lists.newArrayList();
        for (A w : s.writeEntities()) {
          rebased.add(conflictKeys.contains(w.getKey().key()) ? rebaseModel(w) : w);
        }
        pending.add(scriptBuilder.render(rebased, s.deleteEntities(), s.foldSlot()));
      }
      if (pending.isEmpty()) {
        return;
      }
      scripts = pending;
    }
  }

  /** Evaluates scripts that are not CAS-protected (e.g. cross-slot index updates) once each. */
  protected final void evalAll(List<LuaCommitScriptBuilder.BuiltScript<K, A>> scripts) {
    for (LuaCommitScriptBuilder.BuiltScript<K, A> s : scripts) {
      evalConflicts(s);
    }
  }

  @SuppressWarnings("unchecked")
  private List<String> evalConflicts(LuaCommitScriptBuilder.BuiltScript<K, A> s) {
    log.tracef("[redis] EVAL keys=%s args=%s", s.keys(), s.args());
    Object result = jedis.eval(s.lua(), s.keys(), s.args());
    if (!(result instanceof List)) {
      return List.of();
    }
    List<String> conflicts = Lists.newArrayList();
    for (Object o : (List<Object>) result) {
      if (o instanceof byte[]) {
        conflicts.add(new String((byte[]) o, StandardCharsets.UTF_8));
      } else if (o != null) {
        conflicts.add(String.valueOf(o));
      }
    }
    return conflicts;
  }

  private A rebaseModel(A originalModel) {
    K key = originalModel.getKey();
    String redisKey = key.key();
    log.tracef("[redis] HGETALL %s (rebase)", redisKey);
    Map<String, String> latestData = jedis.hgetAll(redisKey);
    countOperation(HGETALL);

    A rebasedModel =
        latestData == null || latestData.isEmpty()
            ? adapterSupplier.newInstance(key)
            : adapterSupplier.newInstance(key, latestData);
    originalModel.replayPendingChangesOnto(rebasedModel);
    return rebasedModel;
  }

  @Override
  protected void rollbackImpl() {
    // No action needed on rollback for this use case
  }
}
