package com.ironspellgrimoire.item;

import com.ironspellgrimoire.server.StoryArc1;
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

/** An Ashen Page (ASHFALL): something Sallow's people carried. Read it (use it): it goes into the Journal. */
public class AshenPageItem extends Item {
    public AshenPageItem() { super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)); }

    public static int page(ItemStack s) { return s.hasTag() ? s.getTag().getInt("Page") : 0; }
    public static ItemStack of(int page) { ItemStack s = new ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.ASHEN_PAGE.get()); s.getOrCreateTag().putInt("Page", page); return s; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        if (p instanceof ServerPlayer sp) StoryArc1.readPage(sp, page(p.getItemInHand(hand)));
        return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand), level.isClientSide);
    }

    @Override public Component getName(ItemStack s) {
        return Component.literal("Ashen Page: " + StoryArc1.pageTitle(page(s)));
    }

    @Override public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("Scorched at the edges. Use it to read.").withStyle(ChatFormatting.GRAY));
    }
}
