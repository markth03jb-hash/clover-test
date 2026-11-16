package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.ShadowPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The Shadow Grimoire's hand-drawn windows (Status, Market): no Minecraft widgets - an ink panel with a breathing blue
 * rim over a dark vignette with smoke drifting up behind it, a lit title, and drawn buttons.
 */
abstract class ShadowUiScreen extends Screen {
    static final int INK = 0xF0070910, PANEL = 0xD00C1220, EDGE = 0xFF3C78E0, EDGE_DIM = 0xFF1C3460, TEXT = 0xFFE6F0FF, MUTED = 0xFF7F8EA8, GOLD = 0xFFE8C070;
    protected int ticks;
    protected final List<Hit> hits = new ArrayList<>();

    protected record Hit(int x, int y, int w, int h, Runnable run) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    ShadowUiScreen(String title) { super(Component.literal(title)); }

    static void act(int action, int id) { AddonNetwork.CHANNEL.sendToServer(new ShadowPackets.Act(action, id, "")); }

    @Override public void tick() { ticks++; }
    @Override public boolean isPauseScreen() { return false; }

    protected int pw() { return Math.min(440, width - 24); }
    protected int ph() { return Math.min(300, height - 24); }
    protected int px() { return (width - pw()) / 2; }
    protected int py() { return (height - ph()) / 2; }

    /** The dark, the smoke, the panel and the title; returns nothing - subclasses draw their contents after it. */
    protected void backdrop(GuiGraphics g, float t, String title) {
        hits.clear();
        g.fillGradient(0, 0, width, height, 0xC0020308, 0xE0050A18);
        for (int k = 0; k < 12; k++) {
            float ph = k * 1.9f;
            int sx = (int) (width * ((k * 0.151f + 0.07f) % 1f) + Mth.sin(t * 0.02f + ph) * 18);
            int sy = (int) (height - ((t * (0.25f + k % 3 * 0.1f) + k * 53) % (height + 80)));
            int a = (int) (40 + 30 * Mth.sin(t * 0.05f + ph));
            g.fillGradient(sx - 10, sy - 60, sx + 10, sy + 20, 0x002050C0, (a << 24) | 0x1A3C90);
        }
        int x0 = px(), y0 = py(), w = pw(), h = ph();
        g.fill(x0, y0, x0 + w, y0 + h, INK);
        frame(g, x0, y0, w, h, lerpCol(EDGE_DIM, EDGE, 0.5f + 0.5f * Mth.sin(t * 0.08f)));
        frame(g, x0 + 3, y0 + 3, w - 6, h - 6, 0x402A50A0);
        for (int[] c : new int[][]{{x0, y0}, {x0 + w - 10, y0}, {x0, y0 + h - 10}, {x0 + w - 10, y0 + h - 10}}) g.fill(c[0] + 3, c[1] + 3, c[0] + 7, c[1] + 7, 0xFF8CC8FF);
        int tw = font.width(title) * 2;
        g.pose().pushPose();
        g.pose().translate(x0 + w / 2f - tw / 2f, y0 + 10, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, title, 0, 0, 0xFF9AD0FF, true);
        g.pose().popPose();
        g.fill(x0 + 10, y0 + 32, x0 + w - 10, y0 + 33, 0x803C78E0);
    }

    protected void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean enabled, int accent, int mx, int my, Runnable run) {
        boolean hov = enabled && in(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, !enabled ? 0x60101622 : hov ? 0xE0183060 : 0xC00E1830);
        frame(g, x, y, w, h, !enabled ? 0x401C3460 : hov ? lerpCol(accent, 0xFFFFFFFF, 0.35f) : accent);
        if (hov) g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x60FFFFFF);
        String l = font.plainSubstrByWidth(label, w - 4);
        g.drawString(font, l, x + (w - font.width(l)) / 2, y + (h - 8) / 2, !enabled ? 0xFF3A4660 : hov ? 0xFFFFFFFF : 0xFFCDE6FF, false);
        if (enabled) hits.add(new Hit(x, y, w, h, run));
    }

    protected static void bar(GuiGraphics g, int x, int y, int w, int h, float f, int c0, int c1) {
        g.fill(x, y, x + w, y + h, 0xFF101828);
        g.fillGradient(x, y, x + (int) (w * Mth.clamp(f, 0, 1)), y + h, c0, c1);
    }

    static void frame(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c); g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c); g.fill(x + w - 1, y, x + w, y + h, c);
    }

    static boolean in(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    static int lerpCol(int a, int b, float f) {
        int r = 0;
        for (int s = 0; s < 32; s += 8) r |= ((int) Mth.lerp(f, (a >>> s) & 255, (b >>> s) & 255) & 255) << s;
        return r;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        for (Hit h : List.copyOf(hits)) if (h.in(mx, my)) {
            h.run().run();
            if (minecraft != null) minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT, 0.7f, 0.6f));
            return true;
        }
        return false;
    }
}
