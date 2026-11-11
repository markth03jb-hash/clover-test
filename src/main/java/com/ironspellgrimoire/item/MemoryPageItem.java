package com.ironspellgrimoire.item;

import com.ironspellgrimoire.server.MemoryStory;
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

/** A torn Memory Page (server/MemoryPages): use it to read it into your grimoire's Memories. ("Chapter", "Page") */
public class MemoryPageItem extends Item {
    private static final String[] ROMAN = {"I", "II", "III"};

    public MemoryPageItem() { super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant()); }

    private static int ch(ItemStack s) { return s.hasTag() ? Math.max(0, Math.min(MemoryStory.CH - 1, s.getTag().getInt("Chapter"))) : 0; }
    private static int pg(ItemStack s) { return s.hasTag() ? Math.max(0, Math.min(MemoryStory.PER - 1, s.getTag().getInt("Page"))) : 0; }

    @Override
    public Component getName(ItemStack s) {
        return Component.literal("Torn Memory Page - " + ROMAN[ch(s)] + "." + (pg(s) + 1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            com.ironspellgrimoire.server.MemoryPages.read(sp, ch(s), pg(s));
            if (!sp.isCreative()) s.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Chapter " + MemoryStory.CHAPTERS[ch(s)] + ", page " + (pg(s) + 1)).withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal("A page of the first binder's story, torn away.").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Use it to read it into your grimoire's Memories.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
