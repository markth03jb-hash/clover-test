package com.ironspellgrimoire.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * The Ossuarch's Phylactery: a small reliquary of bone on a chain, worn in the necklace slot. The first blow that
 * would kill you leaves you on half a heart instead; then it must mend for 5 minutes (the bar shows it filling).
 * The cheat-death itself is in {@link com.ironspellgrimoire.server.OssuarchDrops}.
 */
public class PhylacteryItem extends Item implements ICurioItem {
    public static final int RECHARGE = 20 * 60 * 5;
    public static final String READY_AT = "iron_spell_grimoire_phylactery_ready";

    public PhylacteryItem() { super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()); }

    @Override
    public void curioTick(SlotContext ctx, ItemStack stack) {
        if (ctx.entity().level().isClientSide || ctx.entity().tickCount % 20 != 0 || !(ctx.entity() instanceof Player p)) return;
        long ready = p.getPersistentData().getLong(READY_AT);
        long now = p.level().getGameTime();
        int charge = ready <= now ? 100 : (int) Math.max(0, 100 - (ready - now) * 100 / RECHARGE);
        if (stack.getOrCreateTag().getInt("Charge") != charge) stack.getOrCreateTag().putInt("Charge", charge);
    }

    private static int charge(ItemStack s) { return s.hasTag() && s.getTag().contains("Charge") ? s.getTag().getInt("Charge") : 100; }

    @Override public boolean isBarVisible(ItemStack s) { return charge(s) < 100; }
    @Override public int getBarWidth(ItemStack s) { return Math.round(13F * charge(s) / 100F); }
    @Override public int getBarColor(ItemStack s) { return 0xC8B89A; }
    @Override public boolean isFoil(ItemStack s) { return charge(s) >= 100; }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Worn round the neck.").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("The first blow that would kill you leaves you on half a heart instead.").withStyle(ChatFormatting.WHITE));
        tip.add(Component.literal("Then it mends for 5 minutes.").withStyle(ChatFormatting.GRAY));
        int c = charge(s);
        tip.add(c >= 100 ? Component.literal("Ready.").withStyle(ChatFormatting.GREEN) : Component.literal("Mending: " + c + "%").withStyle(ChatFormatting.DARK_RED));
        tip.add(Component.literal("Taken from the Ossuarch.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
