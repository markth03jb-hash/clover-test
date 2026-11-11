package com.ironspellgrimoire.item;

import com.ironspellgrimoire.server.RivalAI;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Your rival-turned-ally's whistle: blow it and they come to fight beside you for 30 s (once every 5 minutes). */
public class RivalWhistleItem extends Item {
    public RivalWhistleItem() { super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        if (!level.isClientSide && p instanceof ServerPlayer sp) RivalAI.whistle(sp);
        return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Calls your ally to fight beside you for 30 s.").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Once every 5 minutes.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
