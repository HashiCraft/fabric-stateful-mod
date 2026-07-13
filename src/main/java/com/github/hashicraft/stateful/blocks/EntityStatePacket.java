package com.github.hashicraft.stateful.blocks;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record EntityStatePacket(byte[] data) implements CustomPacketPayload {

  public static final CustomPacketPayload.Type<EntityStatePacket> PACKET_ID = new CustomPacketPayload.Type<>(
      Messages.ENTITY_STATE_UPDATED);
  public static final StreamCodec<RegistryFriendlyByteBuf, EntityStatePacket> PACKET_CODEC = ByteBufCodecs.BYTE_ARRAY
      .map(EntityStatePacket::new, EntityStatePacket::data).cast();

  @Override
  public Type<? extends CustomPacketPayload> type() {
    return EntityStatePacket.PACKET_ID;
  }
}
