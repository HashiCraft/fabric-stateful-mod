package com.github.hashicraft.stateful.blocks;

import java.math.BigInteger;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;

public class TestBlockEntity extends StatefulBlockEntity {
  public static BlockEntityType<TestBlockEntity> TEST_BLOCK_ENTITY;

  @Syncable
  public int intValue = 1;

  @Syncable
  public Integer IntegerValue = 8;

  @Syncable
  public double doubleValue = 1.3;

  @Syncable
  public Double DoubleValue = 2.2;

  @Syncable
  public float floatValue = 2.4f;

  @Syncable
  public Float FloatValue = 8.3f;

  @Syncable
  public long longValue = 7L;

  @Syncable
  public Long LongValue = 3L;

  @Syncable
  public MyType ClassType = new MyType();

  @Syncable
  public BigInteger BigIntegerValue = new BigInteger("7");

  public TestBlockEntity() {
    super(TEST_BLOCK_ENTITY, new BlockPos(1, 2, 3), null, null);
  }
}
