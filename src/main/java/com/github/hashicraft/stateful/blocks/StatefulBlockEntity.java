package com.github.hashicraft.stateful.blocks;

import java.lang.reflect.Field;
import java.math.BigInteger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class StatefulBlockEntity extends BlockEntity {

  public EntityStateData serverState = new EntityStateData();
  private boolean isDirty;
  private Block parent;

  public Block getParent() {
    return parent;
  }

  public static void tick(World world, BlockPos pos, BlockState state, StatefulBlockEntity be) {
    if (be.isDirty) {
      be.syncWithServer();
      be.isDirty = false;
    }
  }

  public StatefulBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Block block) {
    super(type, pos, state);
    this.parent = block;
  }

  public void markForUpdate() {
    this.isDirty = true;
  }

  public void sync() {
    this.getWorld().updateListeners(this.getPos(), this.getCachedState(), this.getCachedState(), Block.NOTIFY_ALL);
  }

  // sets the class properies marked with @Syncable from the state
  public void getPropertiesFromState() {
    Class<?> clazz = this.getClass();
    for (Field field : clazz.getDeclaredFields()) {
      field.setAccessible(true);
      if (field.isAnnotationPresent(Syncable.class)) {
        try {
          if (this.serverState == null || this.serverState.data == null) {
            return;
          }

          Object value = serverState.data.get(field.getName());

          // Hashmaps when serialzed from JSON will store the value as double,
          // to set this to the field it must be cast back into its original type
          Class<?> fieldType = field.getType();

          GsonBuilder gsonBuilder = new GsonBuilder();
          gsonBuilder.registerTypeAdapter(BigInteger.class, new BigIntegerTypeAdapter());

          Gson gson = gsonBuilder.create();
          String json = gson.toJson(value);
          value = gson.fromJson(json, fieldType);

          field.set(this, value);

        } catch (IllegalArgumentException e) {
          e.printStackTrace();
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
    }

  }

  public void setPropertiesToState() {
    Class<?> clazz = this.getClass();
    for (Field field : clazz.getDeclaredFields()) {
      field.setAccessible(true);
      if (field.isAnnotationPresent(Syncable.class)) {
        try {
          if (field.get(this) != null) {
            this.serverState.data.put(field.getName(), field.get(this));
          }
        } catch (IllegalArgumentException e) {
          e.printStackTrace();
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
    }
  }

  // stateStateUpdate is called by the server whenever the entity retrieves new
  // state from a client, can be overriden in the entity block, but super should
  // always be called.
  //
  //
  public void serverStateUpdated(EntityStateData data) {
    if (data == null) {
      return;
    }

    this.serverState = data;
    getPropertiesFromState();

    this.markDirty();
    this.sync();
  }

  private void syncWithServer() {
    setPropertiesToState();

    // send the data to the sever so that it can be written to other players
    this.serverState.setBlockPos(this.getPos());
    this.serverState.setRegistryKey(this.world.getRegistryKey());

    PacketByteBuf buf = PacketByteBufs.create();
    buf.writeByteArray(this.serverState.toBytes());

    ClientPlayNetworking.send(Messages.ENTITY_STATE_UPDATED, buf);
  }

  // New vanilla method for client side syncing of data
  @Override
  public Packet<ClientPlayPacketListener> toUpdatePacket() {
    return BlockEntityUpdateS2CPacket.create(this, be -> be.createNbt());
  }

  @Override
  public NbtCompound toInitialChunkDataNbt() {
    NbtCompound nbt = super.toInitialChunkDataNbt();
    toClientTag(nbt);

    return nbt;
  }

  // Deserialize the BlockEntity
  @Override
  public void readNbt(NbtCompound tag) {
    super.readNbt(tag);
    fromClientTag(tag);
  }

  // Serialize the BlockEntity
  @Override
  public void writeNbt(NbtCompound tag) {
    super.writeNbt(tag);
    toClientTag(tag);
  }

  public void fromClientTag(NbtCompound tag) {
    EntityStateData nbtState = EntityStateData.fromBytes(tag.getByteArray("serverState"));
    if (nbtState != null && nbtState.data != null) {
      this.serverState = nbtState;
      this.getPropertiesFromState();
    }
  }

  public NbtCompound toClientTag(NbtCompound tag) {
    if (this.serverState != null) {
      setPropertiesToState();
      tag.putByteArray("serverState", this.serverState.toBytes());
    }

    return tag;
  }
}