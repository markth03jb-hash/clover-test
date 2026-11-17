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
 * The Nightfang (an original design): a short black dagger with a thin blue line of light along its edge, bought in the
 * Shadow Grimoire's Market. Quick: damage 1 + 3 (diamond) + 2 = 6, speed 4.0 - 1.6 = 2.4.
 * Right-click - Thousand Cuts (server/ShadowFx.thousandCuts): blink between up to five foes, cutting each, and land back
 * where you started. 15 s to recover.
 */
public class NightfangItem extends SwordItem {
    public static final int COOLDOWN = 300;

    public NightfangItem() {
        super(Tiers.DIAMOND, 2, -1.6F, new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (!level.isClientSide && p instanceof ServerPlayer sp) {
            if (com.ironspellgrimoire.server.ShadowFx.thousandCuts(sp)) p.getCooldowns().addCooldown(this, COOLDOWN);
        }
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override public boolean isDamageable(ItemStack stack) { return false; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tip, flag);
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.nightfang.1").withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("tooltip.iron_spell_grimoire.nightfang.2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
