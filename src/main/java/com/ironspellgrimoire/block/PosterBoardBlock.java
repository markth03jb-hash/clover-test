package com.ironspellgrimoire.block;

import com.ironspellgrimoire.registry.AddonBlocks;
import com.ironspellgrimoire.server.Bounties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Poster Board: a 3-wide, 2-tall notice board - two posts, a roof, a framed board with wanted posters pinned to it
 * (assets/.../models/block/poster_board.json, one model drawn from the bottom-middle block). The other five blocks it
 * covers are invisible parts ({@link PosterBoardPartBlock}) so the whole board is solid and clickable. Right-click any
 * of it for a bounty (server/Bounties).
 */
public class PosterBoardBlock extends HorizontalDirectionalBlock {
    static final VoxelShape NS = Block.box(0, 0, 5, 16, 16, 11), EW = Block.box(5, 0, 0, 11, 16, 16);

    public PosterBoardBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    /** The five other blocks a board at {@code pos} facing {@code f} covers. */
    static BlockPos[] parts(BlockPos pos, Direction f) {
        Direction side = f.getClockWise();
        return new BlockPos[]{pos.relative(side), pos.relative(side.getOpposite()), pos.above(), pos.above().relative(side), pos.above().relative(side.getOpposite())};
    }

    /** Every block a board at {@code pos} facing {@code f} needs: the board itself first, then its parts. */
    public static BlockPos[] footprint(BlockPos pos, Direction f) {
        BlockPos[] p = parts(pos, f), out = new BlockPos[p.length + 1];
        out[0] = pos;
        System.arraycopy(p, 0, out, 1, p.length);
        return out;
    }

    /** Puts a whole board in the world (for villages: server/VillageBoards). */
    public static void placeAt(Level level, BlockPos pos, Direction f) { placeAt(level, pos, f, AddonBlocks.POSTER_BOARD.get()); }

    /** The same for any board on this frame (the Quest Board). */
    public static void placeAt(Level level, BlockPos pos, Direction f, Block board) {
        level.setBlock(pos, board.defaultBlockState().setValue(FACING, f), 3);
        for (BlockPos p : parts(pos, f)) level.setBlock(p, AddonBlocks.POSTER_BOARD_PART.get().defaultBlockState().setValue(FACING, f), 3);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction f = ctx.getHorizontalDirection().getOpposite();
        for (BlockPos p : parts(ctx.getClickedPos(), f)) if (!ctx.getLevel().getBlockState(p).canBeReplaced(ctx)) return null;   // no room
        return defaultBlockState().setValue(FACING, f);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity by, ItemStack stack) {
        if (level.isClientSide) return;
        Direction f = state.getValue(FACING);
        for (BlockPos p : parts(pos, f)) level.setBlock(p, AddonBlocks.POSTER_BOARD_PART.get().defaultBlockState().setValue(FACING, f), 3);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (!state.is(now.getBlock())) {
            for (BlockPos p : parts(pos, state.getValue(FACING))) if (level.getBlockState(p).is(AddonBlocks.POSTER_BOARD_PART.get())) level.removeBlock(p, false);
        }
        super.onRemove(state, level, pos, now, moved);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer sp) com.ironspellgrimoire.server.Boards.open(sp, com.ironspellgrimoire.server.Boards.BOUNTY, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return s.getValue(FACING).getAxis() == Direction.Axis.Z ? NS : EW; }
}
