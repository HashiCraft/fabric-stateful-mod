package com.github.hashicraft.stateful.blocks;

import java.lang.reflect.Field;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class EntityServerState {
  public static boolean registered = false;
  public static final Logger LOGGER = LoggerFactory.getLogger("stateful");

  public static void RegisterStateUpdates() {
    LOGGER.info("Regsitring state updates");
    if (registered) {
      return;
    }

    PayloadTypeRegistry.serverboundPlay().register(EntityStatePacket.PACKET_ID, EntityStatePacket.PACKET_CODEC);

    ServerPlayNetworking.registerGlobalReceiver(EntityStatePacket.PACKET_ID, (payload, context) -> {
      LOGGER.info("Received state update from client");

      EntityStateData state = EntityStateData.fromBytes(payload.data());
      MinecraftServer server = context.player().level().getServer();

      server.execute(() -> {
        if (state == null) {
          System.out.println("Unable to deserialize client state");
          return;
        }

        BlockPos pos = new BlockPos(state.x, state.y, state.z);

        Iterable<ServerLevel> worlds = server.getAllLevels();
        for (ServerLevel world : worlds) {
          Identifier id = Identifier.parse(state.world);
          ResourceKey<Level> key = world.dimension();

          if (key.identifier().equals(id)) {
            StatefulBlockEntity be = (StatefulBlockEntity) world.getBlockEntity(pos);

            if (be == null) {
              return;
            }

            // update the internal state so that it is sent to other clients
            be.serverStateUpdated(state);

            // update any client state properties
            BlockState blockState = be.getBlockState();
            boolean blockStateChanged = false;

            for (Field field : be.getClass().getDeclaredFields()) {
              if (field.isAnnotationPresent(Syncable.class)) {
                Syncable annotation = field.getAnnotation(Syncable.class);
                if (annotation.property().isEmpty()) {
                  continue;
                }

                try {
                  if (annotation.type() == BooleanProperty.class) {
                    BooleanProperty prop = BooleanProperty.create(annotation.property());
                    boolean value = (boolean) field.get(be);

                    blockStateChanged = true;
                    blockState = blockState.setValue(prop, value);
                  } else if (annotation.type() == IntegerProperty.class) {
                    IntegerProperty prop = IntegerProperty.create(annotation.property(), Integer.MIN_VALUE,
                        Integer.MAX_VALUE);
                    int value = (int) field.get(be);

                    blockStateChanged = true;
                    blockState = blockState.setValue(prop, value);
                  }

                } catch (IllegalArgumentException e) {
                  e.printStackTrace();
                } catch (IllegalAccessException e) {
                  e.printStackTrace();
                }
              }
            }

            if (blockStateChanged) {
              world.setBlock(pos, blockState, Block.UPDATE_ALL);
            }

            // update the neighbors
            world.updateNeighborsAt(pos, be.getParent());
          }
        }
      });
    });

    // set the registered state to ensure only one insance of this method is
    // registered
    EntityServerState.registered = true;
  }
}
