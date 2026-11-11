package com.ironspellgrimoire.client;

import com.ironspellgrimoire.item.RiftInkItem;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.AltarPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The Grimoire Altar's menu, in the altar's black and gold: big buttons for each thing the altar does, each with a line
 * saying what it does and what it needs, and the four Rift Inks you carry to choose from.
 */
public class AltarScreen extends Screen {
    private static final int W = 260, BH = 34, GAP = 6;
    private static final int GOLD = 0xFFE8B84A, GOLD_LT = 0xFFFFE6A0, INK = 0xF00C0904, TEXT = 0xFFF2E8D0, DIM = 0xFFA89870;
    private AltarPackets.Open data;
    private long opened = -1;

    private AltarScreen(AltarPackets.Open d) { super(Component.literal("Grimoire Altar")); this.data = d; }

    public static void show(AltarPackets.Open d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AltarScreen s) s.data = d;
        else mc.setScreen(new AltarScreen(d));
    }

    private boolean hasBook() { return !data.book.isEmpty(); }
    private int left() { return (width - W) / 2; }
    private int top() { return height / 2 - 108; }
    private int rowY(int i) { return top() + 30 + i * (BH + GAP); }
    private static boolean in(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private void send(int action, int arg) { AddonNetwork.CHANNEL.sendToServer(new AltarPackets.Act(data.pos, action, arg)); }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            int l = left();
            if (in(mx, my, l, rowY(0), W, BH)) { send(0, 0); onClose(); return true; }
            if (in(mx, my, l, rowY(1), W, BH) && hasBook()) { send(1, 0); return true; }
            if (in(mx, my, l, rowY(2), W, BH) && hasBook() && data.shards > 0) { send(2, 0); return true; }
            for (int r = 0; r < 4; r++) {
                int x = l + 6 + r * 63, y = rowY(3) + 14;
                if (in(mx, my, x, y, 58, 16) && hasBook() && data.inks[r] > 0) { send(3, r); return true; }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    private void option(GuiGraphics g, int i, String title, String line, boolean can, int mx, int my) {
        int l = left(), y = rowY(i);
        boolean hot = can && in(mx, my, l, y, W, BH);
        g.fill(l, y, l + W, y + BH, hot ? 0xE0402C0C : 0xC0180F06);
        g.renderOutline(l, y, W, BH, hot ? GOLD_LT : can ? GOLD : 0xFF4A3A18);
        g.fill(l, y, l + 3, y + BH, can ? GOLD : 0xFF4A3A18);
        g.drawString(font, title, l + 10, y + 6, can ? (hot ? 0xFFFFFFFF : GOLD_LT) : DIM, false);
        g.drawString(font, line, l + 10, y + 19, can ? TEXT : 0xFF706048, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        double time = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() + pt : 0;
        if (opened < 0) opened = (long) time;
        float open = Mth.clamp((float) ((time - opened) / 6.0), 0F, 1F);
        g.fill(0, 0, width, height, (int) (0x90 * open) << 24 | 0x080502);
        int l = left(), t = top(), h = 30 + 4 * (BH + GAP) + 10;
        g.fill(l - 8, t - 4, l + W + 8, t + h, INK);
        g.renderOutline(l - 8, t - 4, W + 16, h + 4, GOLD);
        g.renderOutline(l - 6, t - 2, W + 12, h, 0xFF6A5420);
        g.drawCenteredString(font, "G R I M O I R E   A L T A R", width / 2, t + 2, GOLD);
        g.drawCenteredString(font, hasBook() ? data.book + "   ★ " + data.stars : "No grimoire equipped   ★ " + data.stars, width / 2, t + 14, DIM);
        option(g, 0, "Receive a Grimoire", "Climb the tower and let a grimoire choose you.", true, mx, my);
        option(g, 1, "★ Star Forge", hasBook() ? "Spend merit stars: empower spells, an ink page, a gem." : "Equip a grimoire first.", hasBook(), mx, my);
        option(g, 2, "Forge a Devil Shard", !hasBook() ? "Equip a grimoire first." : data.shards <= 0 ? "You carry no Devil Shards." : "You carry " + data.shards + ". In the grimoire: " + data.bookShards + " / " + com.ironspellgrimoire.server.ZenonDrops.MAX_SHARDS + ".", hasBook() && data.shards > 0, mx, my);
        // write a Rift Ink: one small button per ink
        int y = rowY(3);
        boolean anyInk = data.inks[0] + data.inks[1] + data.inks[2] + data.inks[3] > 0;
        g.fill(l, y, l + W, y + BH, 0xC0180F06);
        g.renderOutline(l, y, W, BH, hasBook() && anyInk ? GOLD : 0xFF4A3A18);
        g.fill(l, y, l + 3, y + BH, hasBook() && anyInk ? GOLD : 0xFF4A3A18);
        g.drawString(font, "Write a Rift Ink", l + 10, y + 4, hasBook() && anyInk ? GOLD_LT : DIM, false);
        for (int r = 0; r < 4; r++) {
            int x = l + 6 + r * 63, by = y + 14;
            boolean can = hasBook() && data.inks[r] > 0, hot = can && in(mx, my, x, by, 58, 16);
            int col = RiftInkItem.COLORS[r];
            g.fill(x, by, x + 58, by + 16, can ? (hot ? 0xE0000000 | col : 0x90000000 | col) : 0x40202020);
            g.renderOutline(x, by, 58, 16, hot ? 0xFFFFFFFF : can ? 0xFF000000 | col : 0xFF3A3020);
            String n = RiftInkItem.NAMES[r].replace(" Ink", "") + " x" + data.inks[r];
            g.drawCenteredString(font, n, x + 29, by + 4, can ? 0xFFFFFFFF : 0xFF706048);
        }
        super.render(g, mx, my, pt);
    }
}
