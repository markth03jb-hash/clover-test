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
        SystemSkin.window(g, font, px(), py(), pw(), ph(), t, title);
    }

    /** The window unfolds from a line as it opens (drawn over its contents: SystemSkin.Boot). */
    void boot(GuiGraphics g, float pt) { SystemSkin.boot(g, px(), py(), pw(), ph(), ticks + pt); }

    protected void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean enabled, int accent, int mx, int my, Runnable run) {
        boolean hov = enabled && in(mx, my, x, y, w, h);
        SystemSkin.button(g, font, x, y, w, h, label, enabled, accent, hov);
        if (enabled) hits.add(new Hit(x, y, w, h, run));
    }

    protected static void bar(GuiGraphics g, int x, int y, int w, int h, float f, int c0, int c1) { SystemSkin.bar(g, x, y, w, h, f, c0, c1); }

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
