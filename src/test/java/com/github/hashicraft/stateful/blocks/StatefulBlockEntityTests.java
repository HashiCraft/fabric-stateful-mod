package com.github.hashicraft.stateful.blocks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;

class StatefulBlockEntityTests {

  @BeforeEach
  void init() {
  }

  @Test
  void testStateDeserializesCorrectly() {
    TestBlockEntity entity = new TestBlockEntity();
    entity.setPropertiesToState();

    // simulate the data being updated and serialzied
    EntityStateData state = entity.serverState;
    byte[] data = state.toBytes();

    TestBlockEntity newEntity = new TestBlockEntity();

    // rehydrate and update the properties
    EntityStateData hydratedState = EntityStateData.fromBytes(data);
    newEntity.serverState = hydratedState;
    newEntity.getPropertiesFromState();

    assertEquals(1, newEntity.intValue);
    assertEquals(8, newEntity.IntegerValue);
    assertEquals(1.3, newEntity.doubleValue);
    assertEquals(2.2, newEntity.DoubleValue);
    assertEquals(2.4f, newEntity.floatValue);
    assertEquals(8.3f, newEntity.FloatValue);
    assertEquals(7L, newEntity.longValue);
    assertEquals(3L, newEntity.LongValue);
    assertEquals(new BigInteger("7"), newEntity.BigIntegerValue);
    assertEquals("mytype", newEntity.ClassType.stringType);
    assertEquals(new BigInteger("2"), newEntity.ClassType.bigIntegerValue);
    assertEquals("mytype2", newEntity.ClassType.classValue.stringType);
  }
}
