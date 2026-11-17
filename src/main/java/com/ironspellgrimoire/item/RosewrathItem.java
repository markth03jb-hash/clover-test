package com.ironspellgrimoire.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Rosewrath (from the user's concept picture): a long black blade crowned with spikes, glowing red cracks across it and
 * a red rose at its root; a spiked guard, a brown leather grip, a clawed pommel. Its model is traced from the picture
 * itself (tools/blender/trace_prep.py + trace_build.py, spec tools/blender/rosewrath_spec.json): the picture's outline
 * puffed into 3D - a V-edged blade, a raised rose - with the picture as its surface; its red parts glow. Bought in the
 * Market. Damage 1 + 3 (diamond) + 4 = 8, speed 4.0 - 2.3 = 1.7.
 * Right-click - Crimson Fracture (server/ShadowFx.crimsonFracture): glowing cracks race 10 blocks along the ground and
 * split open under every foe they reach. 12 s.
 */
public class RosewrathItem extends SwordItem {
    public static final int COOLDOWN = 240;

    public RosewrathItem() {
        super(Tiers.DIAMOND, 4, -2.3F, new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (!level.isClientSide && p instanceof ServerPlayer sp && com.ironspellgrimoire.server.ShadowFx.crimsonFracture(sp))
            p.getCooldowns().addCooldown(this, COOLDOWN);
        p.swing(hand);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override public boolean isDamageable(ItemStack stack) { return false; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.rosewrath.1").withStyle(ChatFormatting.RED));
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.nightfang.2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
