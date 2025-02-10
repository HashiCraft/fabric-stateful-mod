package com.github.hashicraft.stateful.blocks;

import java.lang.reflect.Field;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class EntityServerState {
  public static boolean registered = false;
  public static final Logger LOGGER = LoggerFactory.getLogger("stateful");

  public static void RegisterStateUpdates() {
    LOGGER.info("Regsitring state updates");
    if (registered) {
      return;
    }

    PayloadTypeRegistry.playC2S().register(EntityStatePacket.PACKET_ID, EntityStatePacket.PACKET_CODEC);

    ServerPlayNetworking.registerGlobalReceiver(EntityStatePacket.PACKET_ID, (payload, context) -> {
      LOGGER.info("Received state update from client");

      EntityStateData state = EntityStateData.fromBytes(payload.data());
      MinecraftServer server = context.player().getServer();

      server.execute(() -> {
        if (state == null) {
          System.out.println("Unable to deserialize client state");
          return;
        }

        BlockPos pos = new BlockPos(state.x, state.y, state.z);

        Iterable<ServerWorld> worlds = server.getWorlds();
        for (ServerWorld world : worlds) {
          Identifier id = Identifier.of(state.world);
          RegistryKey key = world.getRegistryKey();

          if (key.getValue().equals(id)) {
            StatefulBlockEntity be = (StatefulBlockEntity) world.getBlockEntity(pos);

            if (be == null) {
              return;
            }

            // update the internal state so that it is sent to other clients
            be.serverStateUpdated(state);

            // update any client state properties
            BlockState blockState = be.getCachedState();
            boolean blockStateChanged = false;

            for (Field field : be.getClass().getDeclaredFields()) {
              if (field.isAnnotationPresent(Syncable.class)) {
                Syncable annotation = field.getAnnotation(Syncable.class);
                if (annotation.property() == "") {
                  continue;
                }

                try {
                  if (annotation.type() == BooleanProperty.class) {
                    BooleanProperty prop = BooleanProperty.of(annotation.property());
                    boolean value = (boolean) field.get(be);

                    blockStateChanged = true;
                    blockState = blockState.with(prop, value);
                  } else if (annotation.type() == IntProperty.class) {
                    IntProperty prop = IntProperty.of(annotation.property(), Integer.MIN_VALUE, Integer.MAX_VALUE);
                    int value = (int) field.get(be);

                    blockStateChanged = true;
                    blockState = blockState.with(prop, value);
                  }

                } catch (IllegalArgumentException e) {
                  e.printStackTrace();
                } catch (IllegalAccessException e) {
                  e.printStackTrace();
                }
              }
            }

            if (blockStateChanged) {
              world.setBlockState(pos, blockState);
            }

            // update the neighbors
            world.updateNeighbors(pos, be.getParent());
          }
        }
      });
    });

    // set the registered state to ensure only one insance of this method is
    // registered
    EntityServerState.registered = true;
  }
}
