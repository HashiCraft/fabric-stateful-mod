package com.github.hashicraft.stateful.blocks;

import java.lang.reflect.Type;

import com.google.gson.InstanceCreator;

public class EntityStateDataCreator implements InstanceCreator<EntityStateData> {
  @Override
  public EntityStateData createInstance(Type type) {
    return new EntityStateData();
  }
}