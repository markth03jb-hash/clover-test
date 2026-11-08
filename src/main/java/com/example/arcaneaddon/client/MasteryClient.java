package com.example.arcaneaddon.client;

import com.example.arcaneaddon.server.GrimoireMastery;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Grimoire Mastery on the client: the on/off state from the server and the grimoire tooltip (level, XP bar, unlocks). */
@EventBusSubscriber(modid = "arcane_addon", value = {Dist.CLIENT})
public final class MasteryClient {
    private MasteryClient() {}

    private static boolean enabled = true;

    public static boolean enabled() { return enabled; }

    public static void setEnabled(boolean on) { enabled = on; }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent e) {
        ItemStack stack = e.getItemStack();
        GrimoireMastery.Book b = GrimoireMastery.bookOf(stack);
        if (b == null) return;
        List<Component> tip = e.getToolTip();
        if (!enabled) {
            tip.add(Component.translatable("tooltip.arcane_addon.mastery.off").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        int xp = GrimoireMastery.xp(stack), lvl = GrimoireMastery.levelFor(xp);
        int[] th = GrimoireMastery.THRESHOLDS;
        boolean max = lvl >= GrimoireMastery.MAX_LEVEL;
        tip.add(Component.translatable("tooltip.arcane_addon.mastery.level", lvl, GrimoireMastery.MAX_LEVEL).withStyle(ChatFormatting.GOLD));
        // a text XP bar
        float frac = max ? 1f : (xp - th[lvl]) / (float) Math.max(1, th[lvl + 1] - th[lvl]);
        int filled = Math.round(frac * 20);
        tip.add(Component.literal("|".repeat(filled)).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("|".repeat(20 - filled)).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(max ? "  MASTERED" : "  " + (xp - th[lvl]) + " / " + (th[lvl + 1] - th[lvl]) + " XP").withStyle(ChatFormatting.GRAY)));
        if (lvl > 1) tip.add(Component.translatable("tooltip.arcane_addon.mastery.bonus", 4 * (lvl - 1), 3 * (lvl - 1), 50 * (lvl - 1)).withStyle(ChatFormatting.DARK_GREEN));
        if (!max) {
            List<String> next = GrimoireMastery.unlockedAt(b, lvl + 1);
            if (!next.isEmpty()) tip.add(Component.translatable("tooltip.arcane_addon.mastery.next", lvl + 1, GrimoireMastery.spellNames(next)).withStyle(ChatFormatting.AQUA));
            List<String> locked = new ArrayList<>();
            for (Map.Entry<String, Integer> en : b.unlocks().entrySet()) if (en.getValue() > lvl) locked.add(en.getKey());
            if (!locked.isEmpty()) tip.add(Component.translatable("tooltip.arcane_addon.mastery.locked", GrimoireMastery.spellNames(locked)).withStyle(ChatFormatting.RED));
        }
    }
}
