package com.ironspellgrimoire.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A fallen star's heart (server/FallingStars). Use it to crack it open: a Mana Core of tier II (or, one time in three, tier III) and 3 merit stars. */
public class StarCoreItem extends Item {
    public StarCoreItem() { super(new Item.Properties().stacksTo(16).rarity(Rarity.RARE).fireResistant()); }

    @Override public boolean isFoil(ItemStack s) { return true; }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Use it to crack it open: a tier II Mana Core (one time in three, tier III) and 3 merit stars.").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            int tier = sp.getRandom().nextInt(3) == 0 ? 3 : 2;
            ItemStack core = com.ironspellgrimoire.server.BossRules.core(tier);
            if (!sp.getInventory().add(core)) sp.drop(core, false);
            com.ironspellgrimoire.server.Bounties.addStars(sp, 3);
            level.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1f, 1.4f);
            sp.displayClientMessage(Component.literal("The star cracks open: a tier " + (tier == 3 ? "III" : "II") + " Mana Core, and 3 merit stars.").withStyle(ChatFormatting.AQUA), true);
            if (!sp.isCreative()) s.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }
}
