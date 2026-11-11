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
 * ink page and the three star sigils for the cover. Click to buy; the server checks and answers with the new state.
 */
public class StarForgeScreen extends Screen {
    private static final int PW = BW / 2 - 30, ROW = 13;
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
    private int sigilY(int i) { return top() + 92 + i * 17; }
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
        for (int i = 0; i < StarForge.SIGIL_NAMES.length; i++) {
            if (data.sigil != i + 1 && in(mx, my, rx, sigilY(i), PW, 15)) { send(2, String.valueOf(i)); return true; }
        }
        return super.mouseClicked(mx, my, btn);
    }

    /** Each sigil's mark, inked small: the Well a cup, Haste a pair of chevrons, the Spring rising drops. */
    private static void glyph(GuiGraphics g, int i, int x, int y, int col, double time) {
        if (i == 0) {   // a well: a cup with water in it
            g.fill(x, y + 3, x + 1, y + 10, col); g.fill(x + 10, y + 3, x + 11, y + 10, col); g.fill(x, y + 10, x + 11, y + 11, col);
            g.fill(x + 2, y + 6, x + 9, y + 9, col & 0x80FFFFFF);
        } else if (i == 1) {   // haste: two chevrons
            for (int k = 0; k < 2; k++) for (int d = 0; d < 5; d++) {
                g.fill(x + k * 5 + d, y + 1 + d, x + k * 5 + d + 2, y + 2 + d, col);
                g.fill(x + k * 5 + d, y + 9 - d, x + k * 5 + d + 2, y + 10 - d, col);
            }
        } else {   // the spring: three drops rising
            for (int k = 0; k < 3; k++) {
                int dy = (int) ((time * 0.3 + k * 4) % 10);
                g.fill(x + 1 + k * 4, y + 9 - dy, x + 3 + k * 4, y + 11 - dy, col);
            }
        }
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

        // right page: the second ink page, the star sigils
        heading(g, font, "Second Ink Page", rx, PW, t);
        text(g, font, "Your grimoire holds two Rift Inks.", rx, t + 13, PW, INK_SOFT);
        if (data.slot2) heading(g, font, "✔ Bound", rx, PW, t + 45);
        else button(g, font, rx + 20, t + 42, PW - 40, 14, "Bind - " + StarForge.SLOT_COST + "★", in(mx, my, rx + 20, t + 42, PW - 40, 14), data.stars >= StarForge.SLOT_COST);
        rule(g, rx, PW, t + 64);
        heading(g, font, "Star Sigil - " + StarForge.SIGIL_COST + "\u2605", rx, PW, t + 70);
        text(g, font, "One on the cover at a time.", rx + (PW - font.width("One on the cover at a time.")) / 2, t + 80, PW, INK_SOFT);
        for (int i = 0; i < StarForge.SIGIL_NAMES.length; i++) {
            int y = sigilY(i);
            boolean set = data.sigil == i + 1, can = !set && data.stars >= StarForge.SIGIL_COST, hot = !set && in(mx, my, rx, y, PW, 15);
            if (hot && can) g.fill(rx, y, rx + PW, y + 15, 0x287A1A12);
            if (set || hot) frame(g, rx, y, PW, 15, set ? RED_INK : can ? INK_SOFT : INK_FAINT);
            glyph(g, i, rx + 3, y + 2, set ? RED_INK : INK, time);
            int col = set ? RED_INK : can ? INK : INK_FAINT;
            String nm = i == 0 ? "Well" : i == 1 ? "Haste" : "Spring";
            g.drawString(font, nm, rx + 17, y + 4, col, false);
            String fx = i == 2 ? "+30% regen" : StarForge.SIGIL_EFFECTS[i];
            g.drawString(font, fx, rx + PW - 3 - font.width(fx), y + 4, set ? RED_INK : INK_SOFT, false);
        }
        if (data.stars <= 0) text(g, font, "Earn stars from bounties and rifts.", rx, t + 146, PW, RED_INK);

        turning(g, width, height, (ticks + pt) / 5F);
        super.render(g, mx, my, pt);
    }
}
