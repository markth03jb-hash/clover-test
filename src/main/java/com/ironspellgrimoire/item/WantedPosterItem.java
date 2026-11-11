package com.ironspellgrimoire.item;

import com.ironspellgrimoire.server.Bounties;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A Wanted Poster. Everything about the bounty is written on it (the boss, the modifiers, where he was last seen, how
 * long is left); right-click to read it out. A finished one is stamped SUBJUGATED with the hunter's name - a trophy.
 */
public class WantedPosterItem extends Item {
    public WantedPosterItem() { super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)); }

    private static CompoundTag bounty(ItemStack s) { return s.hasTag() ? s.getTag().getCompound("Bounty") : new CompoundTag(); }

    @Override
    public Component getName(ItemStack s) {
        CompoundTag b = bounty(s);
        if (b.isEmpty()) return super.getName(s);
        boolean done = b.getInt("State") == Bounties.STATE_DONE;
        return Component.literal(done ? "Subjugated: " : "Wanted: ").withStyle(done ? ChatFormatting.GREEN : ChatFormatting.DARK_RED)
                .append(Component.literal(Bounties.NAMES[Math.max(0, Math.min(2, b.getInt("Boss")))]).withStyle(ChatFormatting.GOLD));
    }

    @Override public boolean isFoil(ItemStack s) { return bounty(s).getInt("State") == Bounties.STATE_DONE; }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        CompoundTag b = bounty(s);
        if (b.isEmpty()) { tip.add(Component.literal("Ring a village bell for a bounty.").withStyle(ChatFormatting.GRAY)); return; }
        tip.add(Bounties.modsText(b.getInt("Mods")));
        if (b.getInt("State") == Bounties.STATE_DONE) {
            tip.add(Component.literal("Brought down by " + b.getString("Hunter")).withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
            return;
        }
        tip.add(Component.literal("Last sighted near " + b.getInt("X") + ", " + b.getInt("Z")).withStyle(ChatFormatting.GRAY));
        if (level != null) {
            long left = Math.max(0, b.getLong("Deadline") - level.getGameTime());
            tip.add(Component.literal("Time left: " + time(left)).withStyle(left < 12000 ? ChatFormatting.RED : ChatFormatting.GRAY));
        }
        tip.add(Component.literal("Follow the compass at the top of your screen.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    public static String time(long ticks) {
        long d = ticks / 24000, h = (ticks % 24000) / 1000;
        return d > 0 ? d + "d " + h + "h" : h + "h " + (ticks % 1000) * 60 / 1000 + "m";
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        CompoundTag b = bounty(s);
        if (!level.isClientSide && !b.isEmpty() && b.getInt("State") < Bounties.STATE_DONE) {
            double dx = b.getInt("X") - p.getX(), dz = b.getInt("Z") - p.getZ();
            p.displayClientMessage(Component.literal(Bounties.NAMES[b.getInt("Boss")] + ": " + (int) Math.hypot(dx, dz) + " blocks "
                    + Bounties.direction(dx, dz) + "  -  " + time(Math.max(0, b.getLong("Deadline") - level.getGameTime())) + " left").withStyle(ChatFormatting.GOLD), true);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }
}
