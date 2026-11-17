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
 * Bloodrend (from the user's concept picture): a broad blade of glowing blood red with a black core - one edge dead
 * straight, the other bowing out and sweeping round to the point, both hacked with notches - a winged gold crossguard
 * with a red-gemmed medallion, a black wrapped grip and a chunky gold pommel. Modelled in Blender
 * (tools/blender/bloodrend.py), drawn by Forge's OBJ loader; its blade glows (lit by its material). Bought in the
 * Market. Damage 1 + 3 (diamond) + 4 = 8, speed 4.0 - 2.4 = 1.6.
 * Right-click - Crimson Rend (server/ShadowFx.crimsonRend): a crescent of blood flies 8 blocks forward through every
 * foe; harder the more hurt you are (+60% near death). 12 s.
 */
public class BloodrendItem extends SwordItem {
    public static final int COOLDOWN = 240;

    public BloodrendItem() {
        super(Tiers.DIAMOND, 4, -2.4F, new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (!level.isClientSide && p instanceof ServerPlayer sp && com.ironspellgrimoire.server.ShadowFx.crimsonRend(sp))
            p.getCooldowns().addCooldown(this, COOLDOWN);
        p.swing(hand);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override public boolean isDamageable(ItemStack stack) { return false; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.bloodrend.1").withStyle(ChatFormatting.DARK_RED));
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.nightfang.2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
