package io.phasetwo.keycloak.redis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import redis.clients.jedis.util.JedisClusterCRC16;

/** Redis-free unit tests for the dynamic Lua assembly in {@link LuaCommitScriptBuilder}. */
public class LuaCommitScriptBuilderTest {

  private LuaCommitScriptBuilder<TestKey, TestEntity> builder() {
    return new LuaCommitScriptBuilder<>(op -> {});
  }

  @Test
  public void standaloneWriteEmitsVerificationGuardAndApply() {
    TestEntity w = new TestEntity("ent:1");
    w.set("name", "alice");

    LuaCommitScriptBuilder.BuiltScript<TestKey, TestEntity> s =
        builder().buildSingle(List.of(w), List.of());

    assertEquals(List.of("ent:1"), s.keys());
    String lua = s.lua();
    assertTrue(lua.startsWith("local conflicts = {}"));
    assertTrue(lua.contains("redis.call('HGET', KEYS[1], 'version')"));
    assertTrue(lua.contains("if #conflicts > 0 then return conflicts end"));
    assertTrue(lua.contains("redis.call('HSET', KEYS[1], unpack(ARGV,"));
    assertTrue(lua.contains("redis.call('HINCRBY', KEYS[1], 'version', 1)"));
    assertTrue(lua.trim().endsWith("return conflicts"));
    // a brand new entity expects version "0" (the create path)
    assertEquals("0", s.args().get(0));
  }

  @Test
  public void deleteEmitsDelAndIndexSrem() {
    TestEntity d = new TestEntity("ent:2");
    d.index("idx:realm", "ent:2");

    LuaCommitScriptBuilder.BuiltScript<TestKey, TestEntity> s =
        builder().buildSingle(List.of(), List.of(d));

    String lua = s.lua();
    assertTrue(lua.contains("redis.call('DEL', KEYS["));
    assertTrue(lua.contains("redis.call('SREM', KEYS["));
    assertTrue(s.keys().contains("ent:2"));
    assertTrue(s.keys().contains("idx:realm"));
    // no verification when there are no writes
    assertFalse(lua.contains("HGET"));
  }

  @Test
  public void clusterFoldsSameSlotIndexAndSplitsCrossSlot() {
    // same hash tag => same slot => index SADD folded into the commit script
    TestEntity same = new TestEntity("{x}ent");
    same.set("name", "a");
    same.index("{x}idx", "{x}ent");

    LuaCommitScriptBuilder.SlotScripts<TestKey, TestEntity> folded =
        builder().buildPerSlot(List.of(same), List.of());
    assertEquals(1, folded.commitScripts().size());
    assertTrue(folded.indexScripts().isEmpty());
    assertTrue(folded.commitScripts().get(0).lua().contains("redis.call('SADD', KEYS["));

    // different slots => index SADD must run as a separate phase-B script
    assertTrue(JedisClusterCRC16.getSlot("{a}ent") != JedisClusterCRC16.getSlot("{b}idx"));
    TestEntity cross = new TestEntity("{a}ent");
    cross.set("name", "b");
    cross.index("{b}idx", "{a}ent");

    LuaCommitScriptBuilder.SlotScripts<TestKey, TestEntity> split =
        builder().buildPerSlot(List.of(cross), List.of());
    assertEquals(1, split.commitScripts().size());
    assertEquals(1, split.indexScripts().size());
    assertFalse(split.commitScripts().get(0).lua().contains("SADD"));
    assertTrue(split.indexScripts().get(0).lua().contains("redis.call('SADD', KEYS["));
  }

  @Test
  public void allOrNothingGuardPrecedesEveryApply() {
    TestEntity w1 = new TestEntity("ent:a");
    w1.set("x", "1");
    TestEntity w2 = new TestEntity("ent:b");
    w2.set("y", "2");

    LuaCommitScriptBuilder.BuiltScript<TestKey, TestEntity> s =
        builder().buildSingle(List.of(w1, w2), List.of());
    String lua = s.lua();
    int guard = lua.indexOf("if #conflicts > 0 then return conflicts end");
    assertNotNull(lua);
    assertTrue(guard > 0);
    // both apply blocks come after the single guard
    assertTrue(lua.indexOf("HSET") > guard);
    assertEquals(2, s.writeEntities().size());
  }

  static final class TestKey implements Key {
    private final String key;

    TestKey(String key) {
      this.key = key;
    }

    @Override
    public String key() {
      return key;
    }
  }

  static final class TestEntity extends MapEntity<TestKey> {
    private final java.util.Map<String, String> indexes = new java.util.LinkedHashMap<>();

    TestEntity(String key) {
      super(new TestKey(key), null);
    }

    void set(String field, String value) {
      setField(field, value);
    }

    void index(String indexKey, String member) {
      indexes.put(indexKey, member);
    }

    @Override
    public Map<String, String> getSecondaryIndexes() {
      return indexes;
    }
  }
}
