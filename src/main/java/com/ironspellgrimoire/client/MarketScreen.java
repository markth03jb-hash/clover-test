package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.ShadowPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The Market (the Shadow Grimoire's "Market" page; server/HunterAscension): spend Shade Marks on its wares - the
 * Nightfang dagger, Mana Tonics, Shade Salves, Remnant Lures, a stat reset, or a Reckoning on purpose.
 */
public class MarketScreen extends ShadowUiScreen {
    static final String[] NAMES = {"Nightfang", "Mana Tonic", "Shade Salve", "Remnant Lure", "Stat Reset", "Reckoning Key"};
    static final String[] DESC = {"A black dagger - right-click: Thousand Cuts", "Restores 150 mana at once", "Heals 8 hearts over 4 seconds",
            "Your next remnant rises on the first try", "Refunds every stat point to spend again", "Starts a Reckoning now, for its reward"};
    static final int[] PRICES = {500, 40, 60, 120, 800, 300};
    static final int[] ACCENT = {0xFF3C78E0, 0xFF70A8FF, 0xFF70E0A0, 0xFFE8C070, 0xFFC090FF, 0xFFE05050};
    private int marks;

    MarketScreen() { super("MARKET"); }

    public static void show(ShadowPackets.Market p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MarketScreen open) { open.marks = p.marks; return; }
        if (!p.open) return;
        MarketScreen n = new MarketScreen();
        n.marks = p.marks;
        mc.setScreen(n);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        float t = ticks + pt;
        backdrop(g, t, "MARKET");
        int x0 = px() + 14, y0 = py() + 42, w = pw() - 28;
        String mk = marks + " Shade Marks";
        g.drawString(font, mk, x0 + w - font.width(mk), y0, GOLD, false);
        g.drawString(font, "Earned from kills, Daily Trials and the Reckoning", x0, y0, 0xFF5A6A88, false);
        int cols = 2, gap = 8, cw = (w - gap) / cols, ch = 52;
        for (int i = 0; i < NAMES.length; i++) {
            int x = x0 + (i % cols) * (cw + gap), y = y0 + 16 + (i / cols) * (ch + gap);
            boolean afford = marks >= PRICES[i];
            g.fill(x, y, x + cw, y + ch, PANEL);
            frame(g, x, y, cw, ch, afford ? ACCENT[i] : 0x601C3460);
            icon(g, x + 8, y + 10, i, t);
            g.drawString(font, NAMES[i], x + 34, y + 6, TEXT, false);
            g.drawString(font, font.plainSubstrByWidth(DESC[i], cw - 42), x + 34, y + 17, 0xFF7F8EA8, false);
            final int k = i;
            String price = PRICES[i] + " Marks";
            g.drawString(font, price, x + 34, y + 36, afford ? GOLD : 0xFF6A5A40, false);
            button(g, x + cw - 56, y + ch - 22, 48, 16, "BUY", afford, ACCENT[i], mx, my, () -> act(13, k));
        }
    }

    /** A small drawn mark for each ware. */
    private static void icon(GuiGraphics g, int x, int y, int i, float t) {
        switch (i) {
            case 0 -> { for (int k = 0; k < 14; k++) g.fill(x + 4 + k, y + 16 - k, x + 6 + k, y + 18 - k, k > 11 ? 0xFFBEE6FF : 0xFF14161E); g.fill(x + 2, y + 14, x + 8, y + 16, 0xFF5A6080); g.fill(x, y + 18, x + 4, y + 20, 0xFF3C8CFF); }
            case 1, 2 -> {
                int c = i == 1 ? 0xFF3C8CFF : 0xFF40C080;
                g.fill(x + 6, y, x + 12, y + 3, 0xFF8A7050);
                g.fill(x + 4, y + 3, x + 14, y + 18, 0x80A0C0E0);
                g.fill(x + 5, y + 8, x + 13, y + 17, c);
            }
            case 3 -> { g.fill(x + 2, y + 12, x + 16, y + 16, 0xFF10141E); g.fill(x + 7, y + 2, x + 11, y + 12, 0xFF1A2440); g.fill(x + 7, y + 5, x + 11, y + 7, 0xFFE8C070); }
            case 4 -> { for (int k = 0; k < 4; k++) g.fill(x + 2 + k * 4, y + 16 - k * 4, x + 5 + k * 4, y + 18, 0xFFC090FF); }
            default -> { g.fill(x + 3, y + 3, x + 15, y + 15, 0xFF2A0A0E); g.fill(x + 6, y + 6, x + 12, y + 12, (int) (160 + 90 * Math.sin(t * 0.2)) << 24 | 0xE05050); }
        }
    }
}
