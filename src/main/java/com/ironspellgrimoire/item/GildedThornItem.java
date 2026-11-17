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
 * The Gilded Thorn (from the user's own concept sketch): a broad dagger of dark gunmetal - one edge dead straight, the
 * other bowing out and sweeping round to the point - with a guard of thorny antique gold set with red stones. Its model
 * is a real mesh built in Blender (tools/blender/gilded_thorn.py), drawn by Forge's OBJ loader (models/item/gilded_thorn.json).
 * Bought in the Shadow Grimoire's Market. Damage 1 + 3 (diamond) + 3 = 7, speed 4.0 - 2.3 = 1.7.
 * Right-click - Thornbite (server/ShadowFx.thornbite): a wide slash in front of you; each foe cut bleeds for 4 s. 12 s.
 */
public class GildedThornItem extends SwordItem {
    public static final int COOLDOWN = 240;

    public GildedThornItem() {
        super(Tiers.DIAMOND, 3, -2.3F, new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (!level.isClientSide && p instanceof ServerPlayer sp && com.ironspellgrimoire.server.ShadowFx.thornbite(sp))
            p.getCooldowns().addCooldown(this, COOLDOWN);
        p.swing(hand);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override public boolean isDamageable(ItemStack stack) { return false; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.gilded_thorn.1").withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.nightfang.2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
