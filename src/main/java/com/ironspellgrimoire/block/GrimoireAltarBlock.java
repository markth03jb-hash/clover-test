package com.ironspellgrimoire.block;

import com.ironspellgrimoire.server.GrimoireCeremony;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Grimoire Altar: the top of a grimoire tower. Use it and the grimoires rise and circle above it until one
 * chooses you (GrimoireCeremony). Each player is chosen once in survival; after that the altar calls your own
 * grimoire back to you if you have lost it.
 */
public class GrimoireAltarBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public GrimoireAltarBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.5F, 1200F).sound(SoundType.DEEPSLATE_BRICKS)
                .lightLevel(s -> 7).requiresCorrectToolForDrops().noOcclusion());
    }

    /** Client only: tell the altar renderer this altar is near (it draws the turning runes and stars). */
    @Override
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource rnd) {
        com.ironspellgrimoire.client.AltarClient.seen(pos, level.getGameTime());
        if (rnd.nextInt(2) == 0) com.ironspellgrimoire.client.AltarClient.mote(pos, rnd);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPE; }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
        if (held.is(com.ironspellgrimoire.registry.AddonItemRegistry.DEVIL_SHARD.get())) {   // forge a Devil Shard into your grimoire
            if (!level.isClientSide && player instanceof ServerPlayer sp) com.ironspellgrimoire.server.ZenonDrops.forge(sp, held);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp) GrimoireCeremony.begin(sp, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
