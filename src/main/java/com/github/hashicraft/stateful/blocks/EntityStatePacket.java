package com.github.hashicraft.stateful.blocks;

import java.nio.ByteBuffer;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;

public record EntityStatePacket(byte[] data) implements CustomPayload {

  public static final CustomPayload.Id<EntityStatePacket> PACKET_ID = new CustomPayload.Id<>(
      Messages.ENTITY_STATE_UPDATED);
  public static final PacketCodec<RegistryByteBuf, EntityStatePacket> PACKET_CODEC = PacketCodecs.BYTE_ARRAY
      .xmap(EntityStatePacket::new, EntityStatePacket::data).cast();

  @Override
  public Id<? extends CustomPayload> getId() {
    return EntityStatePacket.PACKET_ID;
  }
}
