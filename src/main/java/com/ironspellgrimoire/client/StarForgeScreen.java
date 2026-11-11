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
    /** The spell being mutated (the right page shows the choice), or null. */
    private String mutating;

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
    private static final int SROW = 13, MROW = 17;
    private int sigilY(int i) { return top() + 57 + i * SROW; }
    private int mutY(int m) { return top() + 32 + (m - 1) * MROW; }
    private int pageX(int k) { return rx() + k * (PW / 2 + 2); }
    private void page() { if (minecraft != null && minecraft.player != null) minecraft.player.playSound(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.1F); }
    private void send(int action, String arg) { AddonNetwork.CHANNEL.sendToServer(new StarForgePackets.Act(action, arg)); }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        int lx = lx(), rx = rx(), t = top();
        for (int i = 0; i < data.spells.size(); i++) {
            StarForgePackets.Spell s = data.spells.get(i);
            if (!in(mx, my, lx + PW - 26, rowY(i) - 1, 26, 11)) continue;
            if (s.level() < StarForge.MAX_BOOST) { send(0, s.id()); return true; }
            if (s.mut() == 0) { mutating = s.id(); page(); return true; }   // fully empowered: open its mutation
        }
        if (mutating != null) {
            for (int m = 1; m < StarForge.MUT_NAMES.length; m++) if (data.cores > 0 && in(mx, my, rx, mutY(m), PW, MROW - 1)) { send(3, mutating + "|" + m); mutating = null; return true; }
            if (in(mx, my, rx + PW - 30, t - 1, 30, 11)) { mutating = null; page(); return true; }
            return super.mouseClicked(mx, my, btn);
        }
        if (!data.slot2 && in(mx, my, pageX(0), t + 24, PW / 2 - 2, 14)) { send(1, ""); return true; }
        if (data.slot2 && !data.slot3 && in(mx, my, pageX(1), t + 24, PW / 2 - 2, 14)) { send(4, ""); return true; }
        for (int i = 0; i < StarForge.SIGIL_NAMES.length; i++) {
            if (data.sigil != i + 1 && in(mx, my, rx, sigilY(i), PW, SROW - 1)) { send(2, String.valueOf(i)); return true; }
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
        } else if (i == 2) {   // the spring: three drops rising
            for (int k = 0; k < 3; k++) {
                int dy = (int) ((time * 0.3 + k * 4) % 10);
                g.fill(x + 1 + k * 4, y + 9 - dy, x + 3 + k * 4, y + 11 - dy, col);
            }
        } else if (i == 3) {   // the hunt: a sight
            frame(g, x + 1, y + 1, 9, 9, col);
            g.fill(x + 5, y, x + 6, y + 11, col); g.fill(x, y + 5, x + 11, y + 6, col);
        } else if (i == 4) {   // the bulwark: a shield
            g.fill(x + 1, y + 1, x + 10, y + 6, col); g.fill(x + 2, y + 6, x + 9, y + 8, col); g.fill(x + 4, y + 8, x + 7, y + 10, col);
            g.fill(x + 5, y + 2, x + 6, y + 8, col & 0x80FFFFFF);
        } else if (i == 6) {   // the phoenix: a heart
            g.fill(x + 1, y + 2, x + 5, y + 5, col); g.fill(x + 6, y + 2, x + 10, y + 5, col); g.fill(x + 2, y + 5, x + 9, y + 7, col); g.fill(x + 4, y + 7, x + 7, y + 9, col);
        } else if (i == 7) {   // the edge: a blade
            for (int k = 0; k < 9; k++) g.fill(x + 1 + k, y + 9 - k, x + 3 + k, y + 10 - k, col);
        } else {   // the wind: three streaks, drifting
            int o = (int) (time * 0.2) % 3;
            for (int k = 0; k < 3; k++) g.fill(x + (k + o) % 3, y + 2 + k * 3, x + 8 + (k + o) % 3, y + 3 + k * 3, col);
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
            if (s.level() >= StarForge.MAX_BOOST) {   // fully empowered: mutate it (once), or show its mutation
                if (s.mut() > 0) g.drawString(font, s.mut() == StarForge.MUT_ECHO ? "Echo" : "Siph", lx + PW - 24, y + 1, RED_INK, false);
                else button(g, font, lx + PW - 26, y - 1, 26, 11, "Mut", in(mx, my, lx + PW - 26, y - 1, 26, 11) || s.id().equals(mutating), true);
            } else {
                int cost = StarForge.boostCost(s.level() + 1);
                button(g, font, lx + PW - 26, y - 1, 26, 11, cost + "★", in(mx, my, lx + PW - 26, y - 1, 26, 11), data.stars >= cost);
            }
        }

        if (mutating != null) {   // right page: mutate the chosen spell
            heading(g, font, "Mutation", rx, PW, t);
            String nm = Component.translatable("spell.iron_spell_grimoire." + mutating).getString();
            if (font.width(nm) > PW) nm = font.plainSubstrByWidth(nm, PW - 8) + "..";
            g.drawString(font, nm, rx + (PW - font.width(nm)) / 2, t + 11, INK, false);
            String cost = "1 Mana Core (you have " + data.cores + ")";
            g.drawString(font, cost, rx + Math.max(0, (PW - font.width(cost)) / 2), t + 21, data.cores > 0 ? INK_SOFT : RED_INK, false);
            for (int m = 1; m < StarForge.MUT_NAMES.length; m++) {   // six mutations, a line each
                int y = mutY(m);
                boolean hot = in(mx, my, rx, y, PW, MROW - 1), can = data.cores > 0;
                if (hot && can) { g.fill(rx, y, rx + PW, y + MROW - 1, 0x287A1A12); frame(g, rx, y, PW, MROW - 1, RED_INK); }
                g.drawString(font, StarForge.MUT_NAMES[m], rx + 3, y + 1, can ? RED_INK : INK_FAINT, false);
                String tx = StarForge.MUT_TEXT[m];
                g.pose().pushPose();
                g.pose().translate(rx + 3, y + 9, 0);
                g.pose().scale(0.75f, 0.75f, 1);
                g.drawString(font, tx, 0, 0, can ? INK : INK_FAINT, false);
                g.pose().popPose();
            }
            button(g, font, rx + PW - 30, t - 1, 30, 11, "Back", in(mx, my, rx + PW - 30, t - 1, 30, 11), true);
            turning(g, width, height, (ticks + pt) / 5F);
            super.render(g, mx, my, pt);
            return;
        }

        // right page: the ink pages, the star sigils
        heading(g, font, "Ink Pages", rx, PW, t);
        g.drawString(font, "Hold more Rift Inks.", rx + (PW - font.width("Hold more Rift Inks.")) / 2, t + 12, INK_SOFT, false);
        if (data.slot2) g.drawString(font, "\u2714 Second", pageX(0) + 8, t + 27, RED_INK, false);
        else button(g, font, pageX(0), t + 24, PW / 2 - 2, 14, "2nd " + StarForge.SLOT_COST + "\u2605", in(mx, my, pageX(0), t + 24, PW / 2 - 2, 14), data.stars >= StarForge.SLOT_COST);
        if (data.slot3) g.drawString(font, "\u2714 Third", pageX(1) + 8, t + 27, RED_INK, false);
        else button(g, font, pageX(1), t + 24, PW / 2 - 2, 14, "3rd " + StarForge.SLOT3_COST + "\u2605", in(mx, my, pageX(1), t + 24, PW / 2 - 2, 14), data.slot2 && data.stars >= StarForge.SLOT3_COST);
        rule(g, rx, PW, t + 42);
        heading(g, font, "Star Sigil - " + StarForge.SIGIL_COST + "\u2605", rx, PW, t + 46);
        for (int i = 0; i < StarForge.SIGIL_NAMES.length; i++) {
            int y = sigilY(i);
            boolean set = data.sigil == i + 1, can = !set && data.stars >= StarForge.SIGIL_COST, hot = !set && in(mx, my, rx, y, PW, SROW - 1);
            if (hot && can) g.fill(rx, y, rx + PW, y + SROW - 1, 0x287A1A12);
            if (set || hot) frame(g, rx, y, PW, SROW - 1, set ? RED_INK : can ? INK_SOFT : INK_FAINT);
            glyph(g, i, rx + 3, y + 1, set ? RED_INK : INK, time);
            int col = set ? RED_INK : can ? INK : INK_FAINT;
            g.drawString(font, StarForge.SIGIL_SHORT[i], rx + 17, y + 2, col, false);
            String fx = StarForge.SIGIL_EFFECTS[i].replace(" mana regen", " regen");
            g.drawString(font, fx, rx + PW - 3 - font.width(fx), y + 2, set ? RED_INK : INK_SOFT, false);
        }
        if (data.stars <= 0) text(g, font, "Earn stars from bounties and quests.", rx, t + 156, PW, RED_INK);

        turning(g, width, height, (ticks + pt) / 5F);
        super.render(g, mx, my, pt);
    }
}
