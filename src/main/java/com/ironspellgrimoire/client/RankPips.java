package com.ironspellgrimoire.client;

import com.ironspellgrimoire.server.GrimoireMastery;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Grimoire ranks on the item icon: a row of small pips along the bottom of the slot, one per rank, in the rank's
 * colour (grey, white, gold, bright gold, clover green). A Legendary book's pips shimmer, and its icon gets a thin
 * gold frame.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RankPips {
    private RankPips() {}

    private static final IItemDecorator PIPS = (GuiGraphics g, Font font, ItemStack stack, int x, int y) -> {
        if (!MasteryClient.enabled() || GrimoireMastery.bookOf(stack) == null) return false;
        int lvl = GrimoireMastery.level(stack);
        if (lvl <= 1) return false;
        int col = GrimoireMastery.RANK_COLORS[lvl];
        boolean legend = lvl >= GrimoireMastery.MAX_LEVEL;
        long t = net.minecraft.Util.getMillis();
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        int w = lvl * 3 - 1, x0 = x + 1;
        g.fill(x0 - 1, y + 13, x0 + w + 1, y + 16, 0xC0000000);
        for (int i = 0; i < lvl; i++) {
            int c = col;
            if (legend) {   // a shimmer running along the pips
                float k = (float) (0.5 + 0.5 * Math.sin(t / 180.0 - i * 0.9));
                int r = (int) (((c >> 16) & 255) * (1 - k) + 255 * k), gg = (int) (((c >> 8) & 255) * (1 - k) + 255 * k), b = (int) ((c & 255) * (1 - k) + 255 * k);
                c = r << 16 | gg << 8 | b;
            }
            g.fill(x0 + i * 3, y + 14, x0 + i * 3 + 2, y + 15, 0xFF000000 | c);
        }
        if (legend) {
            int a = (int) (110 + 80 * Math.sin(t / 300.0));
            g.renderOutline(x, y, 16, 16, a << 24 | 0xE8B84A);
        }
        g.pose().popPose();
        return true;
    };

    @SubscribeEvent
    public static void onRegister(RegisterItemDecorationsEvent e) {
        for (GrimoireMastery.Book b : GrimoireMastery.BOOKS) e.register(b.item().get(), PIPS);
    }
}
