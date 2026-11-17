package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.ShadowPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The Market (the Shadow Grimoire's "Market" page; server/HunterAscension): spend Shade Marks on its wares - the
 * knives (Nightfang, Dragon Fang, Duskglass Kris, Gilded Thorn, Bloodrend, Rosewrath, the Frostwing greatsword), Mana Tonics, Shade Salves, Remnant Lures, a stat reset, or a Reckoning on purpose.
 */
public class MarketScreen extends ShadowUiScreen {
    static final String[] NAMES = {"Nightfang", "Dragon Fang", "Duskglass Kris", "Gilded Thorn", "Bloodrend", "Rosewrath", "Frostwing", "Mana Tonic", "Shade Salve", "Remnant Lure", "Stat Reset", "Reckoning Key"};
    static final String[] DESC = {"Dagger - Thousand Cuts", "Dragon's tooth - Wyrmfang Lunge", "Wavy blade - Phantom Edges", "Thorned gold - Thornbite", "Blood blade - Crimson Rend", "Black rose - Crimson Fracture", "Greatsword - Sovereign's Verdict",
            "Restores 150 mana", "Heals 8 hearts over 4 s", "Next remnant rises first try", "Refunds every stat point", "Starts a Reckoning now"};
    /** The ware each card buys (the server's numbering, HunterAscension.PRICES) - the knives first. */
    static final int[] WARE = {0, 6, 7, 8, 9, 10, 11, 1, 2, 3, 4, 5};
    static final int[] PRICES = {500, 1000, 900, 950, 1100, 1050, 1600, 40, 60, 120, 800, 300};
    static final int[] ACCENT = {0xFF3C78E0, 0xFFE0503C, 0xFF9AB8FF, 0xFFD8A040, 0xFFE02838, 0xFFB01830, 0xFF40E0FF, 0xFF70A8FF, 0xFF70E0A0, 0xFFE8C070, 0xFFC090FF, 0xFFE05050};
    /** Which drawn mark each card shows (9: the Gilded Thorn, drawn as the item itself). */
    static final int[] ICON = {0, 1, 2, 9, 10, 11, 12, 3, 4, 5, 6, 7};
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
        int cols = 3, rows = (NAMES.length + cols - 1) / cols, gap = 6, cw = (w - gap * (cols - 1)) / cols,
                ch = Math.min(64, (ph() - 42 - 16 - 14 - gap * (rows - 1)) / rows);
        for (int i = 0; i < NAMES.length; i++) {
            int x = x0 + (i % cols) * (cw + gap), y = y0 + 16 + (i / cols) * (ch + gap);
            boolean afford = marks >= PRICES[i];
            g.fill(x, y, x + cw, y + ch, PANEL);
            frame(g, x, y, cw, ch, afford ? ACCENT[i] : 0x601C3460);
            icon(g, x + 6, y + 6, ICON[i], t);
            g.drawString(font, font.plainSubstrByWidth(NAMES[i], cw - 32), x + 28, y + 6, TEXT, false);
            int dw = y + 27 > y + ch - 20 ? cw - 32 - 46 : cw - 32;   // a short card: stop the line before the BUY button
            g.drawString(font, font.plainSubstrByWidth(DESC[i], dw), x + 28, y + 17, 0xFF7F8EA8, false);
            final int k = WARE[i];
            g.drawString(font, PRICES[i] + " Marks", x + 6, y + ch - 14, afford ? GOLD : 0xFF6A5A40, false);
            button(g, x + cw - 42, y + ch - 20, 36, 15, "BUY", afford, ACCENT[i], mx, my, () -> act(13, k));
        }
    }

    /** A small drawn mark for each ware. */
    private static void icon(GuiGraphics g, int x, int y, int i, float t) {
        switch (i) {
            case 1 -> {   // the Dragon Fang: a curved tooth, ivory to crimson, over a gold guard
                for (int k = 0; k < 14; k++) {
                    int c = k < 4 ? 0xFFECE2C6 : k < 9 ? 0xFFBE1E22 : 0xFF600810, bend = k * k / 40;
                    g.fill(x + 5 + k - bend, y + 15 - k, x + 8 + k - bend - k / 6, y + 17 - k, c);
                }
                g.fill(x + 2, y + 14, x + 9, y + 17, 0xFFA87A2E); g.fill(x, y + 17, x + 4, y + 20, 0xFFFF6E28);
            }
            case 2 -> {   // the Duskglass Kris: a wavy dark blade
                for (int k = 0; k < 13; k++) {
                    int off = (int) Math.round(Math.sin(k * 0.9));
                    g.fill(x + 4 + k + off, y + 16 - k - off, x + 6 + k + off, y + 18 - k - off, k % 3 == 0 ? 0xFF6EBEFF : 0xFF141A30);
                }
                g.fill(x + 1, y + 16, x + 6, y + 19, 0xFF5A6080);
            }
            case 0 -> { for (int k = 0; k < 14; k++) g.fill(x + 4 + k, y + 16 - k, x + 6 + k, y + 18 - k, k > 11 ? 0xFFBEE6FF : 0xFF14161E); g.fill(x + 2, y + 14, x + 8, y + 16, 0xFF5A6080); g.fill(x, y + 18, x + 4, y + 20, 0xFF3C8CFF); }
            case 9 -> g.renderItem(new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.GILDED_THORN.get()), x, y);
            case 10 -> g.renderItem(new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.BLOODREND.get()), x, y);
            case 11 -> g.renderItem(new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.ROSEWRATH.get()), x, y);
            case 12 -> g.renderItem(new net.minecraft.world.item.ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.FROSTWING.get()), x, y);
            case 3, 4 -> {
                int c = i == 3 ? 0xFF3C8CFF : 0xFF40C080;
                g.fill(x + 6, y, x + 12, y + 3, 0xFF8A7050);
                g.fill(x + 4, y + 3, x + 14, y + 18, 0x80A0C0E0);
                g.fill(x + 5, y + 8, x + 13, y + 17, c);
            }
            case 5 -> { g.fill(x + 2, y + 12, x + 16, y + 16, 0xFF10141E); g.fill(x + 7, y + 2, x + 11, y + 12, 0xFF1A2440); g.fill(x + 7, y + 5, x + 11, y + 7, 0xFFE8C070); }
            case 6 -> { for (int k = 0; k < 4; k++) g.fill(x + 2 + k * 4, y + 16 - k * 4, x + 5 + k * 4, y + 18, 0xFFC090FF); }
            default -> { g.fill(x + 3, y + 3, x + 15, y + 15, 0xFF2A0A0E); g.fill(x + 6, y + 6, x + 12, y + 12, (int) (160 + 90 * Math.sin(t * 0.2)) << 24 | 0xE05050); }
        }
    }
}
