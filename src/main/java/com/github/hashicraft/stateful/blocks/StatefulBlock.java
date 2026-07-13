package com.github.hashicraft.stateful.blocks;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class StatefulBlock extends BaseEntityBlock {
  public static BlockEntityType<StatefulBlockEntity> STATEFUL_BLOCK_ENTITY;

  public static final Logger LOGGER = LoggerFactory.getLogger("stateful");

  public StatefulBlock(Properties settings) {
    super(settings);
  }

  @Override
  @Nullable
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
      BlockEntityType<T> type) {

    LOGGER.info("Getting ticker");
    if (world.isClientSide()) {
      return checkType(type, StatefulBlockEntity::tick);
    }

    return null;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return null;
  }

  @Override
  protected MapCodec<? extends BaseEntityBlock> codec() {
    return simpleCodec(StatefulBlock::new);
  }

  // Deliberately does not use BaseEntityBlock.createTickerHelper: subclasses assign their own
  // BlockEntityType, so there is no expected type here to match against and the helper would
  // return null, leaving the block entity without a ticker.
  @SuppressWarnings("unchecked")
  private <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> checkType(BlockEntityType<A> givenType,
      BlockEntityTicker<? super E> ticker) {
    return (BlockEntityTicker<A>) ticker;
  }
}