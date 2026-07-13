package com.github.hashicraft.stateful.blocks;

import java.lang.reflect.Field;
import java.math.BigInteger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

// Reflection over @Syncable fields. Kept free of any Minecraft API so it can be exercised
// without bootstrapping the game: BlockEntity's registry initialisation makes that expensive.
public final class SyncableFields {

  private SyncableFields() {
  }

  private static Gson gson() {
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapter(BigInteger.class, new BigIntegerTypeAdapter());
    return gsonBuilder.create();
  }

  // sets the fields marked with @Syncable on target from the state
  public static void applyStateToFields(Object target, EntityStateData serverState) {
    if (serverState == null || serverState.data == null) {
      return;
    }

    for (Field field : target.getClass().getDeclaredFields()) {
      field.setAccessible(true);
      if (!field.isAnnotationPresent(Syncable.class)) {
        continue;
      }

      try {
        Object value = serverState.data.get(field.getName());

        // Hashtables when deserialized from JSON store the value as a double, so it must be
        // converted back into the field's original type before being set.
        Gson gson = gson();
        value = gson.fromJson(gson.toJson(value), field.getType());

        field.set(target, value);
      } catch (IllegalArgumentException | IllegalAccessException e) {
        e.printStackTrace();
      }
    }
  }

  // copies the fields marked with @Syncable on target into the state
  public static void collectFieldsToState(Object target, EntityStateData serverState) {
    if (serverState == null || serverState.data == null) {
      return;
    }

    for (Field field : target.getClass().getDeclaredFields()) {
      field.setAccessible(true);
      if (!field.isAnnotationPresent(Syncable.class)) {
        continue;
      }

      try {
        Object value = field.get(target);
        if (value != null) {
          serverState.data.put(field.getName(), value);
        }
      } catch (IllegalArgumentException | IllegalAccessException e) {
        e.printStackTrace();
      }
    }
  }
}
