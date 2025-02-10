package com.github.hashicraft.stateful.blocks;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.serialization.MapCodec;

import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.github.hashicraft.stateful.blocks.StatefulBlockEntity;

public class StatefulBlock extends BlockWithEntity {
  public static BlockEntityType<StatefulBlockEntity> STATEFUL_BLOCK_ENTITY;

  public static final Logger LOGGER = LoggerFactory.getLogger("stateful");

  public StatefulBlock(Settings settings) {
    super(settings);
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
      BlockEntityType<T> type) {

    LOGGER.info("Getting ticker");
    if (world.isClient()) {
      return checkType(type, StatefulBlockEntity::tick);
    }

    return null;
  }

  @Override
  public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
    return null;
  }

  @Override
  protected MapCodec<? extends BlockWithEntity> getCodec() {
    return createCodec(StatefulBlock::new);
  }

  private <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> checkType(BlockEntityType<A> givenType,
      BlockEntityTicker<? super E> ticker) {
    return (BlockEntityTicker<A>) ticker;
  }
}