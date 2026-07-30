package com.github.hashicraft.stateful.blocks;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class StatefulBlockEntity extends BlockEntity {
  public static final Logger LOGGER = LoggerFactory.getLogger("stateful");

  public EntityStateData serverState = new EntityStateData();
  private boolean isDirty;
  private Block parent;

  public Block getParent() {
    return parent;
  }

  public static void tick(Level world, BlockPos pos, BlockState state, StatefulBlockEntity be) {
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
    LOGGER.info("Marking for update");
    this.isDirty = true;
  }

  public void sync() {
    this.getLevel().sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(),
        Block.UPDATE_ALL);
  }

  // sets the class properies marked with @Syncable from the state
  public void getPropertiesFromState() {
    SyncableFields.applyStateToFields(this, this.serverState);
  }

  public void setPropertiesToState() {
    SyncableFields.collectFieldsToState(this, this.serverState);
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

    this.setChanged();
    this.sync();
  }

  private void syncWithServer() {
    LOGGER.info("Sending state update to server");

    setPropertiesToState();

    // send the data to the sever so that it can be written to other players
    this.serverState.setBlockPos(this.getBlockPos());
    this.serverState.setRegistryKey(this.level.dimension());

    EntityStatePacket buf = new EntityStatePacket(this.serverState.toBytes());

    ClientPlayNetworking.send(buf);
  }

  @Nullable
  @Override
  public Packet<ClientGamePacketListener> getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
    CompoundTag nbt = saveCustomOnly(registryLookup);

    if (this.serverState != null) {
      setPropertiesToState();
      nbt.putString("serverState", java.util.Base64.getEncoder().encodeToString(this.serverState.toBytes()));
    }
    return nbt;
  }

  // Deserialize the BlockEntity from disk.
  //
  // ValueInput has no byte array accessor (int[] is its only array type), so the state is stored
  // via ExtraCodecs.BASE64_STRING rather than the putByteArray/getByteArray pair the network path
  // still uses.
  @Override
  protected void loadAdditional(ValueInput input) {
    super.loadAdditional(input);
    input.read("serverState", ExtraCodecs.BASE64_STRING).ifPresent(this::applyStateBytes);
  }

  // Serialize the BlockEntity to disk
  @Override
  protected void saveAdditional(ValueOutput output) {
    super.saveAdditional(output);

    if (this.serverState != null) {
      setPropertiesToState();
      output.store("serverState", ExtraCodecs.BASE64_STRING, this.serverState.toBytes());
    }
  }

  public void fromClientTag(CompoundTag tag) {
    tag.getString("serverState").ifPresent(str -> {
      try {
        this.applyStateBytes(java.util.Base64.getDecoder().decode(str));
      } catch (IllegalArgumentException e) {
        LOGGER.error("Failed to decode base64 serverState", e);
      }
    });
  }

  public CompoundTag toClientTag(CompoundTag tag) {
    if (this.serverState != null) {
      setPropertiesToState();
      tag.putByteArray("serverState", this.serverState.toBytes());
    }

    return tag;
  }

  private void applyStateBytes(byte[] stateBytes) {
    EntityStateData nbtState = EntityStateData.fromBytes(stateBytes);
    if (nbtState != null && nbtState.data != null) {
      this.serverState = nbtState;
      this.getPropertiesFromState();
    }
  }
}
