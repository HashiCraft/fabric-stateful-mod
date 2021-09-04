package com.github.hashicraft.stateful.blocks;

import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class StatefulBlock extends BlockWithEntity {
  public static BlockEntityType<StatefulBlockEntity> STATEFUL_BLOCK_ENTITY;

  public StatefulBlock(Settings settings) {
    super(settings);
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
      BlockEntityType<T> type) {

    // only tick on the client
    if (world.isClient()) {
      return checkType(type, StatefulBlockEntity::tick);
    }

    return null;
  }

  private <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> checkType(BlockEntityType<A> givenType,
      BlockEntityTicker<? super E> ticker) {
    return (BlockEntityTicker<A>) ticker;
  }

  @Override
  public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
    return null;
  }
}