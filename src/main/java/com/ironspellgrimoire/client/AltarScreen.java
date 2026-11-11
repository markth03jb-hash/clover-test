package com.ironspellgrimoire.client;

import static com.ironspellgrimoire.client.BookUi.*;

import com.ironspellgrimoire.item.RiftInkItem;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.AltarPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * The Grimoire Altar's menu, written into an open book in the equipped grimoire's colours (the same look as the
 * grimoire screen). Left page: receive a grimoire, the Star Forge, the Magic Knight Exam. Right page: forge a Devil
 * Shard, write a Rift Ink.
 */
public class AltarScreen extends Screen {
    private static final int PW = BW / 2 - 30, CH = 46, LCH = 38;
    private AltarPackets.Open data;
    private int ticks;

    private AltarScreen(AltarPackets.Open d) { super(Component.literal("Grimoire Altar")); this.data = d; }

    public static void show(AltarPackets.Open d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AltarScreen s) s.data = d;
        else {
            mc.setScreen(new AltarScreen(d));
            if (mc.player != null) mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.9F);
        }
    }

    @Override public void tick() { ticks++; }
    @Override public boolean isPauseScreen() { return false; }

    private boolean hasBook() { return !data.book.isEmpty(); }
    private int lx() { return x0(width) + 16; }
    private int rx() { return x0(width) + BW / 2 + 14; }
    private int top() { return y0(height) + 14; }
    /** Left page rows 0-2 (grimoire, forge, exam); right page rows 2-3 (shard, ink). */
    private int choiceY(int i) { return i < 2 ? top() + 30 + i * (LCH + 4) : top() + 14 + (i - 2) * (CH + 8); }
    private int ascY() { return top() + 14 + 2 * (CH + 8) + 6; }
    private String ascLine() {
        return switch (data.ascend) {
            case 0 -> "Awaken your grimoire first.";
            case 1 -> "Reach mastery 8 first.";
            case 3 -> "Your grimoire has ascended.";
            default -> "Face your Reflection.";
        };
    }
    private int examY() { return top() + 30 + 2 * (LCH + 4); }
    private String examLine() {
        return switch (data.exam) {
            case 0 -> "Equip a grimoire first.";
            case 1 -> "Reach mastery 5 first.";
            case 3 -> "Passed: mastery goes to 7.";
            default -> "Pass to raise mastery to 7.";
        };
    }
    private static final int IW = PW / 4;
    private int inkX(int r) { return rx() + (r % 4) * IW; }
    private int inkY(int r) { return choiceY(3) + 13 + (r / 4) * 12; }
    private void send(int action, int arg) { AddonNetwork.CHANNEL.sendToServer(new AltarPackets.Act(data.pos, action, arg)); }
    private void click() { if (minecraft != null && minecraft.player != null) minecraft.player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.2F); }

    private boolean onRibbon(double mx, double my) { return in(mx, my, x0(width) + BW + 7, y0(height) + 24, 50, 16); }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            if (onRibbon(mx, my)) { click(); send(6, 0); return true; }
            if (in(mx, my, lx(), choiceY(0), PW, LCH)) { click(); send(0, 0); onClose(); return true; }
            if (in(mx, my, lx(), choiceY(1), PW, LCH) && hasBook()) { click(); send(1, 0); return true; }
            if (in(mx, my, lx(), examY(), PW, LCH) && data.exam == 2) { click(); send(4, 0); onClose(); return true; }
            if (in(mx, my, rx(), choiceY(2), PW, CH) && hasBook() && data.shards > 0) { click(); send(2, 0); return true; }
            if (in(mx, my, rx(), ascY(), PW, 36) && data.ascend == 2) { click(); send(5, 0); onClose(); return true; }
            for (int r = 0; r < data.inks.length; r++) {
                if (in(mx, my, inkX(r), inkY(r), IW - 1, 11) && hasBook() && data.inks[r] > 0) { click(); send(3, r); return true; }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int[] c = colours();
        book(g, font, width, height, c, Component.literal("Grimoire Altar"), "★ " + data.stars + " merit stars");
        int lx = lx(), rx = rx(), t = top();

        // left page: whose grimoire, then what the altar can give
        heading(g, font, "The Altar", lx, PW, t);
        String who = hasBook() ? data.book : "No grimoire equipped";
        if (font.width(who) > PW) who = font.plainSubstrByWidth(who, PW - 8) + "..";
        g.drawString(font, who, lx + (PW - font.width(who)) / 2, t + 12, hasBook() ? INK : INK_SOFT, false);
        rule(g, lx, PW, t + 24);
        choice(g, font, lx, choiceY(0), PW, LCH, "Receive a Grimoire", "Climb the tower and be chosen.", in(mx, my, lx, choiceY(0), PW, LCH), true);
        choice(g, font, lx, choiceY(1), PW, LCH, "\u2605 Star Forge", hasBook() ? "Spend merit stars on your grimoire." : "Equip a grimoire first.",
                in(mx, my, lx, choiceY(1), PW, LCH), hasBook());
        choice(g, font, lx, examY(), PW, LCH, data.exam == 3 ? "\u2694 Exam Passed" : "\u2694 Knight Exam", examLine(), in(mx, my, lx, examY(), PW, LCH), data.exam == 2);

        // right page: what you bring to it
        heading(g, font, "Offerings", rx, PW, t);
        boolean shard = hasBook() && data.shards > 0;
        String sl = !hasBook() ? "Equip a grimoire first." : data.shards <= 0 ? "You carry no upgrades. Bound: " + data.bookShards + "."
                : "Carrying " + data.shards + ". Bound: " + data.bookShards + ".";
        choice(g, font, rx, choiceY(2), PW, CH, "Bind Upgrades", sl, in(mx, my, rx, choiceY(2), PW, CH), shard);
        int anyInk = 0;
        for (int v : data.inks) anyInk += v;
        choice(g, font, rx, choiceY(3), PW, CH + 2, "Write a Rift Ink", null, false, hasBook() && anyInk > 0);
        int hover = -1;
        for (int r = 0; r < data.inks.length && r < RiftInkItem.COUNT; r++) {   // every ink: a drop of its colour and how many you carry
            int x = inkX(r), y = inkY(r), w = IW - 1;
            boolean can = hasBook() && data.inks[r] > 0, hot = in(mx, my, x, y, w, 11);
            if (hot) hover = r;
            g.fill(x + 3, y + 3, x + 8, y + 9, (can ? 0xFF000000 : 0x50000000) | RiftInkItem.COLORS[r]);
            g.fill(x + 4, y + 2, x + 7, y + 3, (can ? 0xFF000000 : 0x50000000) | RiftInkItem.COLORS[r]);
            if (hot && can) { g.fill(x, y, x + w, y + 11, 0x287A1A12); frame(g, x, y, w, 11, RED_INK); }
            g.drawString(font, "" + data.inks[r], x + 11, y + 2, can ? (hot ? RED_INK : INK) : INK_FAINT, false);
        }
        final int hv = hover;
        if (hv >= 0) g.renderTooltip(font, java.util.List.of(Component.literal(RiftInkItem.NAMES[hv]).withStyle(s -> s.withColor(RiftInkItem.COLORS[hv])),
                Component.literal(RiftInkItem.TEXT[hv]).withStyle(net.minecraft.ChatFormatting.GRAY)), java.util.Optional.empty(), mx, my);
        choice(g, font, rx, ascY(), PW, 36, data.ascend == 3 ? "\u2727 Ascended" : "\u2727 Ascension", ascLine(), in(mx, my, rx, ascY(), PW, 36), data.ascend == 2);

        // the Library ribbon on the book's edge
        boolean rh = onRibbon(mx, my);
        int bx = x0(width) + BW, by = y0(height);
        g.fill(bx + 5, by + 24, bx + 58, by + 40, 0xFF000000 | dark(c[2], 0.35F));
        g.fill(bx + 6, by + 25, bx + 57, by + 39, 0xFF000000 | (rh ? c[2] : c[4]));
        g.drawString(font, "Library", bx + 32 - font.width("Library") / 2, by + 29, rh ? 0xFF000000 | c[4] : 0xFF000000 | c[5], false);
        turning(g, width, height, (ticks + pt) / 5F);
        super.render(g, mx, my, pt);
    }
}
