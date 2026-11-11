package com.ironspellgrimoire.block;

import com.ironspellgrimoire.registry.AddonBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** An invisible piece of a Poster Board: solid, clickable, and breaking it breaks the whole board. */
public class PosterBoardPartBlock extends HorizontalDirectionalBlock {
    public PosterBoardPartBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion().noLootTable());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override @SuppressWarnings("deprecation") public RenderShape getRenderShape(BlockState s) { return RenderShape.INVISIBLE; }

    /** The board's main block (bottom middle), or null. */
    static BlockPos main(BlockGetter level, BlockPos pos, Direction f) {
        Direction side = f.getClockWise();
        for (BlockPos c : new BlockPos[]{pos.relative(side), pos.relative(side.getOpposite()), pos.below(), pos.below().relative(side), pos.below().relative(side.getOpposite())}) {
            BlockState s = level.getBlockState(c);
            if (s.is(AddonBlocks.POSTER_BOARD.get()) && s.getValue(FACING) == f) return c;
        }
        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!state.is(now.getBlock()) && !level.isClientSide) {
            BlockPos m = main(level, pos, state.getValue(FACING));
            if (m != null) level.destroyBlock(m, true);   // the whole board comes down (and drops itself once)
        }
        super.onRemove(state, level, pos, now, moved);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos m = main(level, pos, state.getValue(FACING));
        if (m == null) return InteractionResult.PASS;
        return level.getBlockState(m).use(level, player, hand, hit.withPosition(m));
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        return new ItemStack(AddonBlocks.POSTER_BOARD_ITEM.get());
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return s.getValue(FACING).getAxis() == Direction.Axis.Z ? PosterBoardBlock.NS : PosterBoardBlock.EW; }
}
