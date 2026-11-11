package com.ironspellgrimoire.client;

import static com.ironspellgrimoire.client.BookUi.*;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.StarForgePackets;
import com.ironspellgrimoire.server.StarForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * The Star Forge, written into the equipped grimoire itself (the grimoire screen's open book, in its colours). Left
 * page: every spell with its empowerment (three inked pips, +10% each) and what the next costs. Right page: the second
 * ink page and the six cover gems. Click to buy; the server checks and answers with the new state.
 */
public class StarForgeScreen extends Screen {
    private static final int PW = BW / 2 - 30, ROW = 13;
    private static final int[] GEM_RGB = {0xE0303C, 0x3C8CFF, 0x3CD86A, 0xA060FF, 0xFFC040, 0xE8F4FF};
    private StarForgePackets.Open data;
    private int ticks;

    private StarForgeScreen(StarForgePackets.Open d) { super(Component.literal("Star Forge")); this.data = d; }

    public static void show(StarForgePackets.Open d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof StarForgeScreen s) s.data = d;
        else {
            mc.setScreen(new StarForgeScreen(d));
            if (mc.player != null) mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.9F);
        }
    }

    @Override public void tick() { ticks++; }
    @Override public boolean isPauseScreen() { return false; }

    private int lx() { return x0(width) + 16; }
    private int rx() { return x0(width) + BW / 2 + 14; }
    private int top() { return y0(height) + 14; }
    private int rowY(int i) { return top() + 16 + i * ROW; }
    private int gemX(int gi) { return rx() + 8 + (gi % 3) * 36; }
    private int gemY(int gi) { return top() + 92 + (gi / 3) * 24; }
    private void send(int action, String arg) { AddonNetwork.CHANNEL.sendToServer(new StarForgePackets.Act(action, arg)); }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        int lx = lx(), rx = rx(), t = top();
        for (int i = 0; i < data.spells.size(); i++) {
            StarForgePackets.Spell s = data.spells.get(i);
            if (s.level() < StarForge.MAX_BOOST && in(mx, my, lx + PW - 26, rowY(i) - 1, 26, 11)) { send(0, s.id()); return true; }
        }
        if (!data.slot2 && in(mx, my, rx + 20, t + 42, PW - 40, 14)) { send(1, ""); return true; }
        for (int gi = 0; gi < GEM_RGB.length; gi++) {
            if (in(mx, my, gemX(gi), gemY(gi), 20, 20)) { send(2, String.valueOf(gi)); return true; }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        double time = ticks + pt;
        book(g, font, width, height, colours(), Component.literal("★ Star Forge — " + data.book), "★ " + data.stars + " merit stars");
        int lx = lx(), rx = rx(), t = top();

        // left page: empower a spell
        heading(g, font, "Empower (+10% each)", lx, PW, t);
        for (int i = 0; i < data.spells.size(); i++) {
            StarForgePackets.Spell s = data.spells.get(i);
            int y = rowY(i);
            String name = Component.translatable("spell.iron_spell_grimoire." + s.id()).getString();
            if (font.width(name) > 64) name = font.plainSubstrByWidth(name, 58) + "..";
            g.drawString(font, name, lx, y + 1, s.level() > 0 ? RED_INK : INK, false);
            for (int k = 0; k < StarForge.MAX_BOOST; k++) {   // three inked pips
                int px = lx + 66 + k * 8;
                frame(g, px, y + 2, 6, 6, INK_SOFT);
                if (k < s.level()) g.fill(px + 1, y + 3, px + 5, y + 7, RED_INK);
            }
            if (s.level() >= StarForge.MAX_BOOST) g.drawString(font, "max", lx + PW - 22, y + 1, INK_SOFT, false);
            else {
                int cost = StarForge.boostCost(s.level() + 1);
                button(g, font, lx + PW - 26, y - 1, 26, 11, cost + "★", in(mx, my, lx + PW - 26, y - 1, 26, 11), data.stars >= cost);
            }
        }

        // right page: the second ink page, the cover gems
        heading(g, font, "Second Ink Page", rx, PW, t);
        text(g, font, "Your grimoire holds two Rift Inks.", rx, t + 13, PW, INK_SOFT);
        if (data.slot2) heading(g, font, "✔ Bound", rx, PW, t + 45);
        else button(g, font, rx + 20, t + 42, PW - 40, 14, "Bind - " + StarForge.SLOT_COST + "★", in(mx, my, rx + 20, t + 42, PW - 40, 14), data.stars >= StarForge.SLOT_COST);
        rule(g, rx, PW, t + 64);
        heading(g, font, "Cover Gem - " + StarForge.GEM_COST + "★", rx, PW, t + 70);
        text(g, font, "Colours the aura.", rx + (PW - font.width("Colours the aura.")) / 2, t + 80, PW, INK_SOFT);
        String hover = null;
        for (int gi = 0; gi < GEM_RGB.length; gi++) {
            int gx = gemX(gi), gy = gemY(gi);
            boolean hot = in(mx, my, gx, gy, 20, 20), set = data.gem == gi + 1;
            float pulse = 0.85F + 0.15F * (float) Math.sin(time * 0.2 + gi);
            int cc = GEM_RGB[gi];
            int col = 0xFF000000 | (int) ((cc >> 16 & 255) * pulse) << 16 | (int) ((cc >> 8 & 255) * pulse) << 8 | (int) ((cc & 255) * pulse);
            g.fill(gx + 5, gy + 2, gx + 15, gy + 18, col);   // a cut gem set into the page
            g.fill(gx + 2, gy + 5, gx + 18, gy + 15, col);
            g.fill(gx + 7, gy + 5, gx + 10, gy + 8, 0xC0FFFFFF);
            if (set || hot) frame(g, gx, gy, 20, 20, set ? RED_INK : INK_SOFT);
            if (hot) hover = StarForge.GEM_NAMES[gi] + (set ? " (set)" : "");
        }
        if (hover != null) heading(g, font, hover, rx, PW, t + 142);
        else if (data.stars <= 0) text(g, font, "Earn stars from bounties and rifts.", rx, t + 138, PW, RED_INK);

        turning(g, width, height, (ticks + pt) / 5F);
        super.render(g, mx, my, pt);
    }
}
