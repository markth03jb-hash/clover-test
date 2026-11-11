package com.ironspellgrimoire.block;

import com.ironspellgrimoire.server.Quests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Quest Board: the Poster Board's frame (3 wide, 2 tall, the same invisible parts), its banner blue and its notes
 * villagers' requests - a map, a supply list, a sword notice, a checklist (tools/quest_board.py). Right-click it for
 * a quest, or (sneaking) to give up your quest (server/Quests).
 */
public class QuestBoardBlock extends PosterBoardBlock {
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer sp) Quests.fromBoard(sp, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
