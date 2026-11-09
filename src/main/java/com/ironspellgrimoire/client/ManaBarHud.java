package com.ironspellgrimoire.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;

/**
 * The grimoire's own mana bar, in place of Iron's while one of this addon's grimoires is equipped: a frame in the
 * grimoire's trim colour with its clover at the end, the mana inside in the grimoire's colour with a sheen running
 * through it, and the numbers beside it. The bar eases toward the real value instead of jumping.
 * Asta's grimoire has no mana (anti magic): its bar is pitch black with a flickering blood-red edge.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class ManaBarHud {
    private ManaBarHud() {}

    /** {frame/trim, mana fill, fill highlight, dark inside, text} per grimoire (GrimoireMastery.BOOKS order). */
    private static final int[][] COL = {
            {0x8A7A6E, 0x120608, 0xFF2014, 0x050304, 0xFF6A5A},   // Asta (anti magic)
            {0xB4A9C8, 0x6A2BC8, 0xC89BFF, 0x0B0712, 0xD6B8FF},   // Yami
            {0xE7D9A0, 0x1FA86A, 0x9CFFD0, 0x07140F, 0xC8FFE0},   // Yuno
            {0xDCE6F2, 0x8794AA, 0xFFFFFF, 0x0C1018, 0xEEF4FF},   // Nozel
            {0xD9A94C, 0xE0601A, 0xFFD27A, 0x140805, 0xFFC27A},   // Magna
            {0xEDE7C8, 0x2F7FE0, 0xBFE8FF, 0x060C18, 0xBFE4FF},   // Luck
            {0xE8B652, 0xC4201A, 0xFF9A70, 0x160505, 0xFFB08A},   // Mereoleona
            {0xEDE3CC, 0xBBA98C, 0xFFF8E6, 0x100808, 0xF5EBD5},   // Zenon
            {0xD9A94C, 0x5E7896, 0xD8E6F6, 0x101216, 0xE6EEF8},   // Klaus
    };
    private static final int[] LEAVES = {5, 3, 4, 3, 3, 3, 3, 3, 3};

    private static float shown = -1F;
    private static Method getMana;

    /** Iron's own mana bar is hidden while ours is up. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
        var id = e.getOverlay().id();
        if (!"irons_spellbooks".equals(id.getNamespace()) || !id.getPath().contains("mana")) return;
        if (GrimoireWheel.grimoireIndex() >= 0) e.setCanceled(true);
    }

    private static float mana(Minecraft mc) {
        try {
            if (getMana == null) getMana = ClientMagicData.class.getMethod("getPlayerMana");
            return ((Number) getMana.invoke(null)).floatValue();
        } catch (Throwable t) {
            return 0F;
        }
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;
        int book = GrimoireWheel.grimoireIndex();
        if (book < 0) { shown = -1F; return; }
        book = Mth.clamp(book, 0, COL.length - 1);
        try { draw(e.getGuiGraphics(), mc, book, e.getPartialTick()); } catch (Throwable ignored) { }
    }

    private static void draw(GuiGraphics g, Minecraft mc, int book, float pt) {
        int[] c = COL[book];
        boolean anti = book == 0;
        float max = (float) Math.max(1.0, mc.player.getAttributeValue(AttributeRegistry.MAX_MANA.get()));
        float mana = Mth.clamp(mana(mc), 0F, max);
        float frac = mana / max;
        shown = shown < 0 ? frac : shown + (frac - shown) * 0.2F;   // eases toward the real value
        double time = mc.level.getGameTime() + pt;

        int W = g.guiWidth(), H = g.guiHeight();
        int bw = 81, bh = 5;
        int x = W / 2 + 10, y = H - 49 - (mc.player.getArmorValue() > 0 && !mc.player.isCreative() ? 0 : 0);
        if (mc.player.isCreative()) y = H - 32;
        // the frame: trim colour, with a dark inside
        g.fill(x - 2, y - 2, x + bw + 2, y + bh + 2, 0xFF000000 | darker(c[0], 0.45F));
        g.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, 0xFF000000 | c[0]);
        g.fill(x, y, x + bw, y + bh, 0xFF000000 | c[3]);
        if (anti) {
            // anti magic: no mana at all - a black bar with a blood-red edge flickering along it
            for (int i = 0; i < bw; i += 3) {
                float f = (float) (0.5 + 0.5 * Math.sin(time * 0.35 + i * 0.7) * Math.sin(time * 0.13 + i * 0.31));
                int a = (int) (90 + 140 * f);
                g.fill(x + i, y + bh - 1, Math.min(x + bw, x + i + 3), y + bh, (a << 24) | c[2]);
                if (f > 0.75F) g.fill(x + i, y, Math.min(x + bw, x + i + 2), y + 1, ((int) (a * 0.6F) << 24) | c[2]);
            }
        } else {
            int fw = Math.round(bw * Mth.clamp(shown, 0F, 1F));
            if (fw > 0) {
                g.fill(x, y, x + fw, y + bh, 0xFF000000 | c[1]);
                g.fill(x, y, x + fw, y + 1, 0xFF000000 | mix(c[1], c[2], 0.55F));      // light along the top
                g.fill(x, y + bh - 1, x + fw, y + bh, 0xFF000000 | darker(c[1], 0.6F)); // shade along the bottom
                // a sheen sliding through the mana
                int sx = (int) ((time * 1.6) % (bw + 30)) - 15;
                for (int k = 0; k < 10; k++) {
                    int px = x + sx + k;
                    if (px < x || px >= x + fw) continue;
                    int a = (int) (120 * (1F - Math.abs(k - 5) / 5F));
                    g.fill(px, y, px + 1, y + bh, (a << 24) | c[2]);
                }
            }
            // tick marks every quarter
            for (int q = 1; q < 4; q++) g.fill(x + bw * q / 4, y, x + bw * q / 4 + 1, y + bh, 0x50000000);
        }
        // the clover at the left end of the bar
        clover(g, x - 7, y + bh / 2, LEAVES[book], c, anti, time);
        // the numbers (or ANTI MAGIC) just above the right end
        Font f = mc.font;
        String s = anti ? "ANTI MAGIC" : (int) mana + " / " + (int) max;
        g.pose().pushPose();
        g.pose().translate(x + bw, y - 7, 0);
        g.pose().scale(0.5F, 0.5F, 1F);
        g.drawString(f, Component.literal(s), -f.width(s), 0, 0xFF000000 | c[4], true);
        g.pose().popPose();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    /** A small clover badge: its leaves as little squares round a centre, turning slowly. */
    private static void clover(GuiGraphics g, int cx, int cy, int leaves, int[] c, boolean anti, double time) {
        g.fill(cx - 5, cy - 5, cx + 6, cy + 6, 0xFF000000 | darker(c[0], 0.45F));
        g.fill(cx - 4, cy - 4, cx + 5, cy + 5, 0xFF000000 | c[3]);
        int leaf = anti ? 0xFF000000 | 0x0A0A0A : 0xFF000000 | c[1];
        int rim = 0xFF000000 | (anti ? c[2] : c[0]);
        for (int i = 0; i < leaves; i++) {
            double a = i * Math.PI * 2 / leaves + time * 0.02 - Math.PI / 2;
            int lx = cx + (int) Math.round(Math.cos(a) * 2.2), ly = cy + (int) Math.round(Math.sin(a) * 2.2);
            g.fill(lx - 2, ly - 2, lx + 2, ly + 2, rim);
            g.fill(lx - 1, ly - 1, lx + 1, ly + 1, leaf);
        }
        g.fill(cx, cy, cx + 1, cy + 1, rim);
    }

    private static int darker(int rgb, float k) {
        return ((int) ((rgb >> 16 & 255) * k) << 16) | ((int) ((rgb >> 8 & 255) * k) << 8) | (int) ((rgb & 255) * k);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255), gg = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | gg << 8 | bl;
    }
}
