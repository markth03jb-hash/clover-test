package com.ironspellgrimoire.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The open-book look of the grimoire screen, shared by the Altar and the Star Forge: a cover in the equipped
 * grimoire's own colours with its trim, two parchment pages, ink text and inked boxes for choices.
 */
final class BookUi {
    private BookUi() {}

    static final int BW = 300, BH = 190;
    static final ResourceLocation PAGE_L = ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/gui/grimoire_page_left.png");
    static final ResourceLocation PAGE_R = ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/gui/grimoire_page_right.png");
    static final int INK = 0xFF2A1C10, INK_SOFT = 0xFF5A4630, INK_FAINT = 0xFF8A7656, RED_INK = 0xFF7A1A12;
    /** With no grimoire equipped: a plain leather book. pageIn, pageOut, trim, accent, panel, title. */
    private static final int[] LEATHER = {0x2A1A10, 0x4A3020, 0xB08A50, 0xE0B060, 0x4A2E1A, 0xF0E0B0};

    /** The equipped grimoire's colours (as the grimoire screen uses them). */
    static int[] colours() {
        int i = GrimoireWheel.grimoireIndex();
        return i < 0 ? LEATHER : GrimoireWheel.themeColors(i);
    }

    static int x0(int width) { return (width - BW) / 2; }
    static int y0(int height) { return (height - BH) / 2; }

    /** The cover, its trim and both pages; the title above the book, like the grimoire screen. */
    static void book(GuiGraphics g, Font f, int width, int height, int[] c, Component title, String right) {
        int x0 = x0(width), y0 = y0(height);
        g.fill(x0 - 9, y0 - 8, x0 + BW + 9, y0 + BH + 8, 0xFF000000 | dark(c[2], 0.35F));
        g.fill(x0 - 7, y0 - 6, x0 + BW + 7, y0 + BH + 6, 0xFF000000 | c[4]);
        frame(g, x0 - 5, y0 - 4, BW + 10, BH + 8, 0xFF000000 | c[2]);
        g.blit(PAGE_L, x0, y0, BW / 2, BH, 0, 0, 256, 320, 256, 320);
        g.blit(PAGE_R, x0 + BW / 2, y0, BW / 2, BH, 0, 0, 256, 320, 256, 320);
        g.drawString(f, title, x0, y0 - 15, 0xFF000000 | c[5], true);
        if (right != null) g.drawString(f, right, x0 + BW - f.width(right), y0 - 15, 0xFFFFD27A, true);
    }

    /** A page-turn: the page sweeping over from the right as the book opens. */
    static void turning(GuiGraphics g, int width, int height, float k) {
        if (k >= 1F) return;
        int x0 = x0(width), y0 = y0(height), mid = x0 + BW / 2, half = BW / 2;
        int w = (int) (half * Math.abs(Mth.cos(k * Mth.PI)));
        boolean first = k < 0.5F;
        if (w > 0) g.blit(first ? PAGE_R : PAGE_L, first ? mid : mid - w, y0, w, BH, 0, 0, 256, 320, 256, 320);
    }

    /** A centred heading in red ink. */
    static void heading(GuiGraphics g, Font f, String s, int x, int w, int y) {
        Component t = Component.literal(s).withStyle(ChatFormatting.BOLD);
        g.drawString(f, t, x + (w - f.width(t)) / 2, y, RED_INK, false);
    }

    /** An inked rule across the page. */
    static void rule(GuiGraphics g, int x, int w, int y) { g.fill(x + 10, y, x + w - 10, y + 1, 0x60000000 | (INK & 0xFFFFFF)); }

    /** Wrapped ink text; returns the y below it. */
    static int text(GuiGraphics g, Font f, String s, int x, int y, int w, int col) {
        for (FormattedCharSequence l : f.split(Component.literal(s), w)) { g.drawString(f, l, x, y, col, false); y += 9; }
        return y;
    }

    /** A choice written on the page: an inked box with a red title and a line under it. Greyed and faint when you can't. */
    static void choice(GuiGraphics g, Font f, int x, int y, int w, int h, String title, String line, boolean hot, boolean can) {
        if (hot && can) g.fill(x, y, x + w, y + h, 0x287A1A12);
        frame(g, x, y, w, h, can ? (hot ? RED_INK : INK_SOFT) : 0x80000000 | (INK_FAINT & 0xFFFFFF));
        if (can) { g.fill(x - 1, y - 1, x + 2, y + 2, INK); g.fill(x + w - 2, y + h - 2, x + w + 1, y + h + 1, INK); }   // inked corner ticks
        Component t = Component.literal(title).withStyle(can ? Style.EMPTY.withBold(true) : Style.EMPTY);
        g.drawString(f, t, x + 5, y + 4, can ? RED_INK : INK_FAINT, false);
        if (line != null) text(g, f, line, x + 5, y + 15, w - 10, can ? INK : INK_FAINT);
    }

    /** A small inked button (a cost, a page to bind). */
    static void button(GuiGraphics g, Font f, int x, int y, int w, int h, String s, boolean hot, boolean can) {
        if (hot && can) g.fill(x, y, x + w, y + h, 0x307A1A12);
        frame(g, x, y, w, h, can ? (hot ? RED_INK : INK) : 0x80000000 | (INK_FAINT & 0xFFFFFF));
        g.drawString(f, s, x + (w - f.width(s)) / 2, y + (h - 8) / 2 + 1, can ? (hot ? RED_INK : INK) : INK_FAINT, false);
    }

    static boolean in(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    static void frame(GuiGraphics g, int x, int y, int w, int h, int col) {
        g.fill(x, y, x + w, y + 1, col); g.fill(x, y + h - 1, x + w, y + h, col);
        g.fill(x, y, x + 1, y + h, col); g.fill(x + w - 1, y, x + w, y + h, col);
    }

    static int dark(int rgb, float k) {
        return ((int) ((rgb >> 16 & 255) * k) << 16) | ((int) ((rgb >> 8 & 255) * k) << 8) | (int) ((rgb & 255) * k);
    }
}
