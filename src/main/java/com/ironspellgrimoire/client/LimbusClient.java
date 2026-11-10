package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * The client side of the two Limbus-inspired grimoires: the resource bars above the mana bar (Don Quixote's Bloodfeast
 * and Hardblood; Rien's Fortune and his current Prescript, with its timer and progress) and who is in Vampire Mode.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class LimbusClient {
    private LimbusClient() {}

    private static final String[] ORDERS = {
            "Strike three different foes", "Stand perfectly still", "Hit with a thrown or fired form",
            "Strike the same foe four times", "Take no damage", "Slay a foe"};

    private static int kind = -1;           // 0 Don Quixote, 1 Rien
    private static float a, b, c, d;        // DQ: bloodfeast, hardblood, vampire | Rien: fortune, order, progress, received
    private static int timer;
    private static long seen;
    private static float shownA = 0;
    private static final Map<Integer, double[]> VAMP = new HashMap<>();   // player id -> {start, end}

    static void hud(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        kind = p.palette; a = p.h; b = p.r; c = p.r2; d = p.a0; timer = p.life;
        seen = mc.level.getGameTime();
    }

    static void vampire(int id, int life) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (life <= 0) VAMP.remove(id);
        else VAMP.put(id, new double[]{mc.level.getGameTime(), mc.level.getGameTime() + life});
    }

    /** 0..1: how strongly this player is in Vampire Mode right now (fades in and out). */
    static float vampireAmount(int id, double now) {
        double[] v = VAMP.get(id);
        if (v == null) return 0F;
        if (now > v[1] + 6) { VAMP.remove(id); return 0F; }
        return (float) (Mth.clamp((now - v[0]) / 8.0, 0, 1) * Mth.clamp((v[1] + 6 - now) / 6.0, 0, 1));
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || kind < 0) return;
        if (mc.level.getGameTime() - seen > 30) { kind = -1; return; }
        GuiGraphics g = e.getGuiGraphics();
        Font f = mc.font;
        int W = g.guiWidth(), H = g.guiHeight();
        int bw = 90, x = W / 2 - bw / 2, y = H - 52;
        double time = mc.level.getGameTime() + e.getPartialTick();
        if (kind == 0) {
            // Bloodfeast: a thin crimson bar that seems to drip; the number in old gold. Hardblood: three rows of ten studs.
            float frac = Mth.clamp(a / 999F, 0F, 1F);
            shownA += (frac - shownA) * 0.2F;
            g.fill(x - 1, y - 1, x + bw + 1, y + 4, 0xFF000000);
            g.fill(x, y, x + bw, y + 3, 0xFF1A0206);
            int fw = Math.round(bw * shownA);
            g.fill(x, y, x + fw, y + 3, c > 0.5F ? 0xFFFF1A30 : 0xFFB0101E);
            g.fill(x, y, x + fw, y + 1, 0xFFFF6A74);
            for (int i = 0; i < fw; i += 7) {   // drops hanging off the bar
                int len = (int) (1 + 2 * (0.5 + 0.5 * Math.sin(time * 0.15 + i * 1.7)));
                g.fill(x + i + 2, y + 3, x + i + 3, y + 3 + len, 0xC0B0101E);
            }
            int hb = (int) b;
            for (int i = 0; i < 30; i++) {
                int row = i / 10, col = i % 10;
                int sx = x + bw + 6 + col * 3, sy = y - 6 + row * 3;
                g.fill(sx, sy, sx + 2, sy + 2, i < hb ? (row == 2 ? 0xFFFFD0A0 : row == 1 ? 0xFFFF4050 : 0xFFC0101E) : 0x60300408);
            }
            String s = "BLOODFEAST " + (int) a + (c > 0.5F ? "   VAMPIRE" : "");
            text(g, f, s, x, y - 8, c > 0.5F ? 0xFFFF6A74 : 0xFFD8B060);
        } else if (kind == 1) {
            // Fortune: a gold bar with a light running through it. Above it, the Prescript as a white card.
            float frac = Mth.clamp(a / 100F, 0F, 1F);
            shownA += (frac - shownA) * 0.2F;
            g.fill(x - 1, y - 1, x + bw + 1, y + 4, 0xFF000000);
            g.fill(x, y, x + bw, y + 3, 0xFF14120C);
            int fw = Math.round(bw * shownA);
            g.fill(x, y, x + fw, y + 3, 0xFFD9A020);
            g.fill(x, y, x + fw, y + 1, 0xFFFFF0B0);
            int sx = x + (int) ((time * 2) % (bw + 20)) - 10;
            for (int k = 0; k < 4; k++) if (sx + k >= x && sx + k < x + fw) g.fill(sx + k, y, sx + k + 1, y + 3, 0xA0FFFFFF);
            text(g, f, "FORTUNE " + (int) a + (a >= 100 ? "  - FURIOSO READY" : "") + (d > 0.5F ? "  [RECEIVED]" : ""), x, y - 8, a >= 100 ? 0xFFFFF0B0 : 0xFFF2CC5A);
            int order = (int) b;
            if (order >= 0 && order < ORDERS.length) {
                String o = "PRESCRIPT: " + ORDERS[order];
                int tw = (int) (f.width(o) * 0.5F) + 8;
                int px = W / 2 - tw / 2, py = y - 24;
                g.fill(px - 1, py - 1, px + tw + 1, py + 11, 0xFF000000);
                g.fill(px, py, px + tw, py + 10, 0xF0EDE8DC);
                g.fill(px, py + 8, px + Math.round(tw * Mth.clamp(c, 0F, 1F)), py + 10, 0xFFD9A020);   // progress
                g.fill(px, py, px + Math.round(tw * Mth.clamp(timer / 240F, 0F, 1F)), py + 1, 0xFF8A2020);   // time left
                g.pose().pushPose();
                g.pose().translate(px + 4, py + 2.5F, 0);
                g.pose().scale(0.5F, 0.5F, 1F);
                g.drawString(f, Component.literal(o), 0, 0, 0xFF141210, false);
                g.pose().popPose();
            }
        }
    }

    private static void text(GuiGraphics g, Font f, String s, int x, int y, int col) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.5F, 0.5F, 1F);
        g.drawString(f, Component.literal(s), 0, 0, col, true);
        g.pose().popPose();
    }
}
