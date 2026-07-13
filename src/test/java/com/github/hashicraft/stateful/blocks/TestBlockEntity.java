package com.github.hashicraft.stateful.blocks;

import java.math.BigInteger;

// Mirrors the @Syncable fields of a real stateful block entity without extending
// StatefulBlockEntity: since MC 26.2, constructing a BlockEntity validates its BlockEntityType
// against the registry, which cannot be built outside a bootstrapped game. The sync logic under
// test lives in SyncableFields and is entirely independent of BlockEntity.
public class TestBlockEntity {
  public EntityStateData serverState = new EntityStateData();

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

  public void setPropertiesToState() {
    SyncableFields.collectFieldsToState(this, this.serverState);
  }

  public void getPropertiesFromState() {
    SyncableFields.applyStateToFields(this, this.serverState);
  }
}
