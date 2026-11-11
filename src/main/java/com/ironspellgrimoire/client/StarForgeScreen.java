package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.StarForgePackets;
import com.ironspellgrimoire.server.StarForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The Star Forge, drawn in black and gold: your merit stars at the top; on the left every spell of the equipped
 * grimoire with its empowerment (three pips, +10% each) and what the next one costs; on the right the second ink
 * page and the six cover gems. Click to buy; the server checks and answers with the new state.
 */
public class StarForgeScreen extends Screen {
    private static final int W = 316, ROW = 15;
    private static final int GOLD = 0xFFE8B84A, GOLD_LT = 0xFFFFE6A0, INK = 0xF00C0904, TEXT = 0xFFF2E8D0, DIM = 0xFFA89870, RED = 0xFFE05050;
    private static final int[] GEM_RGB = {0xE0303C, 0x3C8CFF, 0x3CD86A, 0xA060FF, 0xFFC040, 0xE8F4FF};
    private StarForgePackets.Open data;
    private long opened = -1;

    private StarForgeScreen(StarForgePackets.Open d) { super(Component.literal("Star Forge")); this.data = d; }

    public static void show(StarForgePackets.Open d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof StarForgeScreen s) s.data = d;
        else mc.setScreen(new StarForgeScreen(d));
    }

    private int h() { return 44 + Math.max(8, data.spells.size()) * ROW + 14; }
    private int left() { return (width - W) / 2; }
    private int top() { return (height - h()) / 2; }
    private static boolean in(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private void send(int action, String arg) { AddonNetwork.CHANNEL.sendToServer(new StarForgePackets.Act(action, arg)); }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        int l = left(), t = top();
        for (int i = 0; i < data.spells.size(); i++) {
            StarForgePackets.Spell s = data.spells.get(i);
            int y = t + 40 + i * ROW;
            if (s.level() < StarForge.MAX_BOOST && in(mx, my, l + 160, y - 1, 36, 12)) { send(0, s.id()); return true; }
        }
        int rx = l + 210;
        if (!data.slot2 && in(mx, my, rx, t + 70, 96, 16)) { send(1, ""); return true; }
        for (int g = 0; g < GEM_RGB.length; g++) {
            int gx = rx + (g % 3) * 32, gy = t + 128 + (g / 3) * 32;
            if (in(mx, my, gx, gy, 24, 24)) { send(2, String.valueOf(g)); return true; }
        }
        return super.mouseClicked(mx, my, btn);
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, INK);
        g.renderOutline(x, y, w, h, GOLD);
        g.renderOutline(x + 2, y + 2, w - 4, h - 4, 0xFF6A5420);
        for (int[] c : new int[][]{{x, y}, {x + w, y}, {x, y + h}, {x + w, y + h}}) {
            g.fill(c[0] - 2, c[1] - 1, c[0] + 3, c[1] + 2, GOLD);
            g.fill(c[0] - 1, c[1] - 2, c[0] + 2, c[1] + 3, GOLD);
        }
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String s, boolean hot, boolean can) {
        g.fill(x, y, x + w, y + h, can ? (hot ? 0xE0806020 : 0xC0503A10) : 0x80302010);
        g.renderOutline(x, y, w, h, can ? (hot ? GOLD_LT : GOLD) : 0xFF6A5420);
        g.drawCenteredString(font, s, x + w / 2, y + (h - 8) / 2, can ? (hot ? 0xFFFFFFFF : GOLD_LT) : DIM);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        double time = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() + pt : 0;
        if (opened < 0) opened = (long) time;
        float open = Mth.clamp((float) ((time - opened) / 6.0), 0F, 1F);
        g.fill(0, 0, width, height, (int) (0x90 * open) << 24 | 0x080502);
        int l = left(), t = top(), h = h();
        for (int i = 0; i < 24; i++) {   // drifting stars behind the panel
            double a = i * 2.399 + time * 0.01;
            int x = l - 8 + (int) ((Math.sin(a * 1.3 + i) * 0.5 + 0.5) * (W + 16)), y = t - 4 + (int) ((time * (0.1 + i % 4 * 0.04) + i * 37) % (h + 8));
            int al = (int) (70 + 60 * Math.sin(time * 0.1 + i));
            g.fill(x, y, x + 1, y + 1, al << 24 | 0xFFE6A0);
        }
        frame(g, l - 8, t - 4, W + 16, h + 8);
        g.drawCenteredString(font, "★  S T A R   F O R G E  ★", width / 2, t + 2, GOLD);
        g.drawCenteredString(font, data.book, width / 2, t + 13, DIM);
        String st = "★ " + data.stars + " merit stars";
        g.drawString(font, st, l + W - font.width(st), t + 2, GOLD_LT, true);
        // empower a spell
        g.drawString(font, "Empower a spell (+10% each)", l, t + 27, GOLD, false);
        for (int i = 0; i < data.spells.size(); i++) {
            StarForgePackets.Spell s = data.spells.get(i);
            int y = t + 40 + i * ROW;
            String name = Component.translatable("spell.iron_spell_grimoire." + s.id()).getString();
            if (font.width(name) > 112) name = font.plainSubstrByWidth(name, 106) + "..";
            g.drawString(font, name, l, y + 1, TEXT, false);
            for (int k = 0; k < StarForge.MAX_BOOST; k++) {   // three pips
                int px = l + 118 + k * 9;
                g.fill(px, y + 2, px + 6, y + 8, k < s.level() ? GOLD : 0xFF3A2C10);
                g.renderOutline(px, y + 2, 6, 6, 0xFF6A5420);
            }
            if (s.level() >= StarForge.MAX_BOOST) g.drawString(font, "MAX", l + 166, y + 1, GOLD, false);
            else {
                int cost = StarForge.boostCost(s.level() + 1);
                button(g, l + 160, y - 1, 36, 12, cost + "★", in(mx, my, l + 160, y - 1, 36, 12), data.stars >= cost);
            }
        }
        // the second ink page
        int rx = l + 210;
        g.drawString(font, "Second ink page", rx, t + 27, GOLD, false);
        g.drawString(font, "Hold two Rift Inks.", rx, t + 40, DIM, false);
        g.drawString(font, "", rx, t + 52, DIM, false);
        if (data.slot2) g.drawString(font, "✔ Bound", rx + 4, t + 74, 0xFF80E080, false);
        else button(g, rx, t + 70, 96, 16, "Bind - " + StarForge.SLOT_COST + "★", in(mx, my, rx, t + 70, 96, 16), data.stars >= StarForge.SLOT_COST);
        // cover gems
        g.drawString(font, "Cover gem - " + StarForge.GEM_COST + "★", rx, t + 102, GOLD, false);
        g.drawString(font, "Colours the book's aura.", rx, t + 114, DIM, false);
        String hover = null;
        for (int gi = 0; gi < GEM_RGB.length; gi++) {
            int gx = rx + (gi % 3) * 32, gy = t + 128 + (gi / 3) * 32;
            boolean hot = in(mx, my, gx, gy, 24, 24), set = data.gem == gi + 1;
            float pulse = 0.85F + 0.15F * (float) Math.sin(time * 0.2 + gi);
            int c = GEM_RGB[gi];
            int r = (int) ((c >> 16 & 255) * pulse), gg = (int) ((c >> 8 & 255) * pulse), b = (int) ((c & 255) * pulse);
            g.fill(gx, gy, gx + 24, gy + 24, 0xC0100804);
            g.fill(gx + 6, gy + 3, gx + 18, gy + 21, 0xFF000000 | r << 16 | gg << 8 | b);   // a cut gem: a long diamond
            g.fill(gx + 3, gy + 6, gx + 21, gy + 18, 0xFF000000 | r << 16 | gg << 8 | b);
            g.fill(gx + 8, gy + 6, gx + 11, gy + 9, 0xC0FFFFFF);                              // its glint
            g.renderOutline(gx, gy, 24, 24, set ? 0xFFFFFFFF : hot ? GOLD_LT : 0xFF6A5420);
            if (hot) hover = StarForge.GEM_NAMES[gi] + (set ? " (set)" : "");
        }
        if (hover != null) g.drawCenteredString(font, hover, rx + 46, t + 196, TEXT);
        if (data.stars <= 0) g.drawCenteredString(font, "Earn merit stars from bounties and rifts.", width / 2, t + h - 10, RED);
        super.render(g, mx, my, pt);
    }
}
