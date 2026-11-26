package com.ironspellgrimoire.item;

import com.ironspellgrimoire.server.Story;
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

/** The Story Journal (ASHFALL): opens the journal - where the story is, what's next, the party, the pages found (also K). */
public class StoryJournalItem extends Item {
    public StoryJournalItem() { super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        if (p instanceof ServerPlayer sp) Story.sync(sp, "journal");
        return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand), level.isClientSide);
    }

    @Override public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("ASHFALL - your story, your party, your Brand.").withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal("Use it (or press K) to open.").withStyle(ChatFormatting.GRAY));
    }
}
