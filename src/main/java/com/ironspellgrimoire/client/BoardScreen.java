package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.BoardPackets;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

/**
 * A village board's menu (server/Boards): a board of dark planks with three notes pinned to it - wanted posters on the
 * Poster Board, quest notes on the Quest Board (the day's special pinned in gold, a nameless poster in black) - each
 * saying what it is, its twists, roughly where, and what it pays, with a Take button. While a hunt or quest is under way
 * the board shows it instead, with Give Up.
 */
public class BoardScreen extends Screen {
    private static final int W = 420, H = 254, NW = 126, NH = 200;
    private static final int PLANK = 0xFF4A3120, PLANK_D = 0xFF2E1E14, PLANK_L = 0xFF5E4030, PAPER = 0xFFEADCB8, PAPER_D = 0xFFD4C49C,
            INK = 0xFF2A1C10, INK_SOFT = 0xFF6A5638, RED = 0xFF8A1A12, GOLD = 0xFFD8AE50;
    private final BoardPackets.Open data;
    private int ticks;

    private BoardScreen(BoardPackets.Open d) { super(Component.literal(d.kind == 0 ? "Poster Board" : "Quest Board")); this.data = d; }

    public static void show(BoardPackets.Open d) { Minecraft.getInstance().setScreen(new BoardScreen(d)); }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() { ticks++; }
    private int x0() { return (width - W) / 2; }
    private int y0() { return (height - H) / 2; }
    private int noteX(int i) { return x0() + 10 + i * (NW + 9); }
    private int noteY() { return y0() + 32; }
    private static boolean in(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private int[] button(int i) { return data.active.isEmpty() ? new int[]{noteX(i) + 18, noteY() + NH - 22, NW - 36, 15} : new int[]{x0() + W / 2 - 45, noteY() + NH - 22, 90, 15}; }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        Minecraft mc = Minecraft.getInstance();
        if (!data.active.isEmpty()) {
            int[] b = button(0);
            if (in(mx, my, b[0], b[1], b[2], b[3])) { send(-1); return true; }
        } else if (data.wait.isEmpty()) {
            for (int i = 0; i < data.notes.size(); i++) {
                int[] b = button(i);
                if (in(mx, my, b[0], b[1], b[2], b[3])) { send(i); return true; }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    private void send(int index) {
        AddonNetwork.CHANNEL.sendToServer(new BoardPackets.Pick(data.kind, index));
        Minecraft.getInstance().player.playSound(SoundEvents.BOOK_PAGE_TURN, 1F, index < 0 ? 0.6F : 1.0F);
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x0 = x0(), y0 = y0();
        // the board: dark planks in a frame
        g.fill(x0 - 6, y0 - 6, x0 + W + 6, y0 + H + 6, PLANK_D);
        for (int k = 0; k < H; k += 22) {
            g.fill(x0, y0 + k, x0 + W, y0 + Math.min(H, k + 21), (k / 22) % 2 == 0 ? PLANK : 0xFF452D1D);
            g.fill(x0, y0 + k + 21, x0 + W, y0 + k + 22, PLANK_D);
            for (int n = (k * 7) % 53; n < W; n += 97) g.fill(x0 + n, y0 + k + 6, x0 + n + 2, y0 + k + 8, PLANK_D);   // knots
        }
        g.fill(x0 - 6, y0 - 6, x0 + W + 6, y0 - 3, PLANK_L); g.fill(x0 - 6, y0 + H + 3, x0 + W + 6, y0 + H + 6, PLANK_L);
        String title = data.kind == 0 ? "POSTER BOARD - WANTED" : "QUEST BOARD";
        g.drawString(font, Component.literal(title).withStyle(ChatFormatting.BOLD), x0 + 10, y0 + 10, GOLD, true);
        String st = "★ " + data.stars + " merit stars";
        g.drawString(font, st, x0 + W - 10 - font.width(st), y0 + 10, 0xFFE8D090, true);

        if (!data.active.isEmpty()) {   // the one under way
            note(g, x0 + (W - NW) / 2 - 0, -1, null, mx, my);
        } else {
            for (int i = 0; i < data.notes.size(); i++) note(g, noteX(i), i, data.notes.get(i), mx, my);
            if (!data.wait.isEmpty()) {
                int ww = font.width(data.wait) + 12;
                g.fill(x0 + (W - ww) / 2, y0 + H - 18, x0 + (W + ww) / 2, y0 + H - 4, 0xC0180E08);
                g.drawString(font, data.wait, x0 + (W - font.width(data.wait)) / 2, y0 + H - 15, 0xFFE8D090, false);
            }
        }
        super.render(g, mx, my, pt);
    }

    private void note(GuiGraphics g, int x, int i, BoardPackets.Note n, int mx, int my) {
        int y = noteY();
        float tilt = i < 0 ? 0F : (i - 1) * 1.6F;
        g.pose().pushPose();
        g.pose().translate(x + NW / 2F, y + 4, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(tilt));
        g.pose().translate(-(x + NW / 2F), -(y + 4), 0);
        boolean secret = n != null && n.special == 2, daily = n != null && n.special == 1;
        int paper = secret ? 0xFF2A2226 : PAPER, ink = secret ? 0xFFE8DCC8 : INK, soft = secret ? 0xFFB0A090 : INK_SOFT;
        g.fill(x + 3, y + 3, x + NW + 3, y + NH + 3, 0x60000000);   // its shadow
        g.fill(x, y, x + NW, y + NH, paper);
        g.fill(x, y + NH - 3, x + NW, y + NH, secret ? 0xFF1A1418 : PAPER_D);
        for (int k = 4; k < NW; k += 11) g.fill(x + k, y + NH - 1, x + k + 5, y + NH, 0x30000000);   // a torn edge
        int pin = daily ? GOLD : secret ? 0xFF606060 : 0xFFB02020;
        g.fill(x + NW / 2 - 3, y - 2, x + NW / 2 + 3, y + 4, pin);
        g.fill(x + NW / 2 - 1, y - 1, x + NW / 2 + 1, y + 1, 0xFFFFFFFF);
        int tx = x + 7, w = NW - 14, ty = y + 10;
        if (n == null) {
            ty = text(g, Component.literal(data.kind == 0 ? "YOUR HUNT" : "YOUR QUEST").withStyle(ChatFormatting.BOLD), tx, ty, w, RED) + 4;
            ty = text(g, Component.literal(data.active).withStyle(ChatFormatting.BOLD), tx, ty, w, ink) + 6;
            ty = text(g, Component.literal("Follow the tracker at the top right of your screen."), tx, ty, w, soft) + 4;
            text(g, Component.literal(data.kind == 0 ? "Giving up has no cost, and the board has new posters sooner." : "Giving up has no cost."), tx, ty, w, soft);
            buttonDraw(g, button(0), "Give Up", mx, my, true, RED);
            g.pose().popPose();
            return;
        }
        if (data.kind == 0) ty = text(g, Component.literal("WANTED").withStyle(ChatFormatting.BOLD), x + (NW - font.width("WANTED") - 6) / 2, ty, w, secret ? 0xFFC0A0A0 : RED) + 4;
        else if (daily) ty = text(g, Component.literal("✦ DAILY SPECIAL").withStyle(ChatFormatting.BOLD), tx, ty, w, 0xFF9A6A10) + 3;
        ty = text(g, Component.literal(n.title).withStyle(ChatFormatting.BOLD), tx, ty, w, ink) + 3;
        if (!n.twist.isEmpty()) ty = text(g, Component.literal(n.twist), tx, ty, w, daily ? 0xFF9A6A10 : secret ? 0xFFE08080 : RED) + 3;
        ty = text(g, Component.literal(n.text), tx, ty, w, soft) + 4;
        g.fill(tx, ty, tx + w, ty + 1, secret ? 0x40FFFFFF : 0x40000000);
        ty += 4;
        ty = text(g, Component.literal(n.where), tx, ty, w, soft) + 2;
        text(g, Component.literal("Pays: " + n.pay), tx, ty, w, ink);
        buttonDraw(g, button(i), "Take", mx, my, data.wait.isEmpty(), secret ? 0xFF505050 : 0xFF2A6A20);
        g.pose().popPose();
    }

    private int text(GuiGraphics g, Component c, int x, int y, int w, int col) {
        for (FormattedCharSequence l : font.split(c, w)) { g.drawString(font, l, x, y, col, false); y += 9; }
        return y;
    }

    private void buttonDraw(GuiGraphics g, int[] b, String label, int mx, int my, boolean on, int col) {
        boolean hot = on && in(mx, my, b[0], b[1], b[2], b[3]);
        g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], on ? (hot ? col : 0xFF000000 | ((col >> 1) & 0x7F7F7F)) : 0xFF8A8070);
        g.fill(b[0], b[1] + b[3] - 1, b[0] + b[2], b[1] + b[3], 0x60000000);
        g.drawString(font, label, b[0] + (b[2] - font.width(label)) / 2, b[1] + 4, on ? 0xFFF4ECD8 : 0xFFC8C0B0, false);
    }
}
