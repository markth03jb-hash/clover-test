package com.ironspellgrimoire.client;

import com.ironspellgrimoire.item.HungerBladeItem;
import com.ironspellgrimoire.server.HungerFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Blade (the Hunger Blade Grimoire's page): the growth tree of all 23 swords - the three base stages across the
 * top, then each path's stage-4 sword over its two branches (stage 5 above stage 6) - and the four swords you carry.
 * Click a sword you can grow to grow it; click a grown one to pick it up, then a slot to carry it there (right-click a
 * slot to empty it). Pointing at a sword shows what it does along the foot.
 */
public class BladeScreen extends Screen {
    static final int PANEL = 0xE0180A0E, EDGE = 0xFF8C1C28, EDGE_DIM = 0xFF3A1218, TEXT = 0xFFF2E4E4, MUTED = 0xFF9A8088, GOLD = 0xFFFFC060;
    static final int[] LINE_COL = {0xFFE04050, 0xFFE8E0D0, 0xFFFF70A8, 0xFF5090FF, 0xFFFF9030};

    private record Hit(int x, int y, int w, int h, Runnable run, Runnable right) { boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; } }
    private final List<Hit> hits = new ArrayList<>();
    private int picked = -1;
    private String foot = "", foot2 = "";
    /** The screenshots point at a sword. */
    public static int forceHover = -1;

    public BladeScreen() { super(Component.literal("The Blade")); }

    void refresh() { }

    @Override public boolean isPauseScreen() { return false; }

    /** Where each sword sits: column (0-7, the two branches of each path side by side) and row (0 base, 1 stage 4, 2 stage 5, 3 stage 6). */
    static int[] cell(int f) {
        if (f < 3) return new int[]{2 + f, 0};   // the base three across the top, in the middle
        int path = (f - 3) / 5, k = (f - 3) % 5;
        if (k == 0) return new int[]{path * 2, 1};   // the stage-4 sword over its branches (drawn centred on both)
        return new int[]{path * 2 + (k <= 2 ? 0 : 1), k == 1 || k == 3 ? 2 : 3};
    }

    private static ItemStack stack(int f) { return HungerBladeItem.of(com.ironspellgrimoire.registry.AddonItemRegistry.HUNGER_BLADE.get(), f); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        hits.clear();
        Minecraft mc = Minecraft.getInstance();
        int colW = Math.min(56, (width - 20) / 8), nodeW = colW - 6, nodeH = Math.min(38, Math.max(30, (height - 120) / 4)), rowH = nodeH + 8;
        int W = colW * 8, x0 = (width - W) / 2, top = 34;
        int H = top + rowH * 4 + 56;
        int y0 = Math.max(4, (height - H) / 2);
        g.fill(x0 - 8, y0, x0 + W + 8, y0 + H, PANEL);
        g.renderOutline(x0 - 8, y0, W + 16, H, EDGE);
        // the header: growth, the hunger and its mood, the bond
        g.drawString(font, "THE BLADE", x0, y0 + 6, 0xFFFF6070, true);
        String gr = HungerClient.growth + " Growth";
        g.drawString(font, gr, x0 + W - font.width(gr), y0 + 6, GOLD, true);
        int m = HungerFx.mood(HungerClient.hunger);
        int bx = x0 + 70, bw = 90;
        g.fill(bx - 1, y0 + 7, bx + bw + 1, y0 + 14, 0xFF100306);
        g.fill(bx, y0 + 8, bx + Math.round(bw * HungerClient.hunger / 100F), y0 + 13, 0xFFB01828);
        g.drawString(font, HungerClient.MOODS[m], bx + bw + 6, y0 + 6, HungerClient.MOOD_COL[m], true);
        int bl = HungerClient.bondLevel();
        String bs = "Bond " + bl + (bl < 5 ? " (" + HungerClient.bond + "/" + HungerFx.BOND_AT[bl + 1] + ")" : " MAX");
        g.drawString(font, bs, x0, y0 + 18, 0xFFE0A0B0, true);
        String tip = picked >= 0 ? "Now click a slot to carry " + HungerFx.NAMES[picked] : "Grow a sword, or pick a grown one to carry";
        g.drawString(font, tip, x0 + W - font.width(tip), y0 + 18, MUTED, false);

        int hover = forceHover;
        // the links first, then the swords over them
        int ny = y0 + top;
        for (int f = 1; f < HungerFx.FORMS; f++) {
            int[] a = centre(HungerFx.PARENT[f], x0, ny, colW, rowH, nodeH), b = centre(f, x0, ny, colW, rowH, nodeH);
            int col = HungerClient.has(f) ? LINE_COL[HungerFx.line(f)] : EDGE_DIM;
            if (cell(f)[1] == 0) g.fill(a[0] + nodeW / 2, a[1], b[0] - nodeW / 2, a[1] + 1, col);   // the base stages, side by side
            else if (cell(f)[1] == 1) {   // from the Maw Blade down to each path: across, then down
                g.fill(Math.min(a[0], b[0]), a[1] + nodeH / 2 + 3, Math.max(a[0], b[0]) + 1, a[1] + nodeH / 2 + 4, col);
                g.fill(b[0], a[1] + nodeH / 2 + 3, b[0] + 1, b[1] - nodeH / 2, col);
            } else if (a[0] == b[0]) g.fill(a[0], a[1] + nodeH / 2, a[0] + 1, b[1] - nodeH / 2, col);
            else {
                int midY = (a[1] + nodeH / 2 + b[1] - nodeH / 2) / 2;
                g.fill(a[0], a[1] + nodeH / 2, a[0] + 1, midY, col);
                g.fill(Math.min(a[0], b[0]), midY, Math.max(a[0], b[0]) + 1, midY + 1, col);
                g.fill(b[0], midY, b[0] + 1, b[1] - nodeH / 2, col);
            }
        }
        for (int f = 0; f < HungerFx.FORMS; f++) {
            int[] c = centre(f, x0, ny, colW, rowH, nodeH);
            int x = c[0] - nodeW / 2, y = c[1] - nodeH / 2;
            boolean have = HungerClient.has(f), canGrow = !have && (HungerFx.PARENT[f] < 0 || HungerClient.has(HungerFx.PARENT[f]));
            boolean afford = canGrow && HungerClient.growth >= HungerFx.COST[HungerFx.STAGE[f]];
            boolean carried = false;
            for (int v : HungerClient.load) carried |= v == f;
            boolean over = mx >= x && mx < x + nodeW && my >= y && my < y + nodeH;
            if (over) hover = f;
            int ln = HungerFx.line(f);
            g.fill(x, y, x + nodeW, y + nodeH, have ? 0xF0281016 : 0xF0100608);
            g.renderOutline(x, y, nodeW, nodeH, f == picked ? GOLD : have ? LINE_COL[ln] : afford ? 0xFFFFE070 : EDGE_DIM);
            if (have || canGrow) {
                g.pose().pushPose();
                g.renderItem(stack(f), c[0] - 8, y + 3);
                g.pose().popPose();
                if (!have) g.fill(c[0] - 9, y + 2, c[0] + 9, y + 20, 0x90100608);   // not grown yet: dimmed
            } else g.drawCenteredString(font, "?", c[0], y + 8, EDGE_DIM);
            String nm = have || canGrow ? shortName(f) : "";
            g.pose().pushPose();
            g.pose().translate(c[0], y + nodeH - 9, 0);
            float sc = Math.min(1F, (nodeW - 2) / (float) Math.max(1, font.width(nm)));
            g.pose().scale(sc, sc, 1);
            g.drawCenteredString(font, nm, 0, 0, have ? TEXT : MUTED);
            g.pose().popPose();
            if (!have && canGrow) {
                String cost = HungerFx.COST[HungerFx.STAGE[f]] + "";
                g.drawString(font, cost, x + nodeW - font.width(cost) - 2, y + 2, afford ? GOLD : 0xFF805050, false);
            }
            if (carried) g.fill(x + 2, y + 2, x + 6, y + 6, GOLD);
            final int ff = f;
            hits.add(new Hit(x, y, nodeW, nodeH, () -> {
                if (HungerClient.has(ff)) picked = picked == ff ? -1 : ff;
                else HungerClient.send(1, ff);
            }, null));
        }
        // the four carried swords
        int sy = ny + rowH * 4 + 4, sw = 64, sx0 = width / 2 - (sw * 4 + 18) / 2;
        g.drawString(font, "CARRIED (switch on the G wheel)", x0, sy - 2, MUTED, false);
        sy += 9;
        for (int i = 0; i < 4; i++) {
            int x = sx0 + i * (sw + 6), f = HungerClient.load[i];
            boolean over = mx >= x && mx < x + sw && my >= sy && my < sy + 28;
            g.fill(x, sy, x + sw, sy + 28, 0xF0100608);
            g.renderOutline(x, sy, sw, 28, i == HungerClient.sel ? GOLD : over && picked >= 0 ? 0xFFFFE070 : EDGE);
            g.drawString(font, (i + 1) + "", x + 3, sy + 3, MUTED, false);
            if (f >= 0 && HungerClient.has(f)) {
                g.renderItem(stack(f), x + sw / 2 - 8, sy + 2);
                g.pose().pushPose();
                String nm = shortName(f);
                float sc = Math.min(1F, (sw - 4) / (float) Math.max(1, font.width(nm)));
                g.pose().translate(x + sw / 2F, sy + 19, 0);
                g.pose().scale(sc, sc, 1);
                g.drawCenteredString(font, nm, 0, 0, TEXT);
                g.pose().popPose();
                if (over) hover = f;
            } else g.drawCenteredString(font, "empty", x + sw / 2, sy + 10, MUTED);
            final int slot = i;
            hits.add(new Hit(x, sy, sw, 28, () -> { if (picked >= 0) { HungerClient.send(3, slot * 100 + picked); picked = -1; } },
                    () -> HungerClient.send(3, slot * 100 + 99)));
        }
        // the foot: what the pointed-at sword is
        if (hover >= 0) {
            int f = hover;
            foot = HungerFx.NAMES[f] + " - stage " + HungerFx.STAGE[f] + " " + HungerFx.LINE_NAMES[HungerFx.line(f)]
                    + String.format("   %.0f damage, %.1f swings/s", HungerFx.DMG[f], 4 + HungerFx.SPEED[f]) + (HungerFx.REACH[f] > 0 ? String.format(", +%.1f reach", HungerFx.REACH[f]) : "");
            foot2 = HungerFx.PASSIVE[f] + (HungerClient.has(f) ? "" : "  Grows from " + (HungerFx.PARENT[f] < 0 ? "-" : HungerFx.NAMES[HungerFx.PARENT[f]]) + " for " + HungerFx.COST[HungerFx.STAGE[f]] + " Growth.");
        } else { foot = "Kills made with the blade feed it Growth. Stage 4 opens four paths; each splits two ways."; foot2 = "Carry up to four swords at a time. You can't change them in a fight."; }
        int fy = sy + 34;
        g.drawCenteredString(font, foot, width / 2, fy, TEXT);
        List<FormattedCharSequence> lines = font.split(Component.literal(foot2), W);
        for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawCenteredString(font, lines.get(i), width / 2, fy + 10 + 9 * i, MUTED);
    }

    private static String shortName(int f) {
        String n = HungerFx.NAMES[f];
        return n.replace(" Greatsword", "").replace(" Colossus", " Col.");
    }

    /** A sword's middle on the page (a stage-4 sword sits between its two branch columns). */
    private static int[] centre(int f, int x0, int ny, int colW, int rowH, int nodeH) {
        int[] c = cell(f);
        double col = c[1] == 1 || c[1] == 0 ? c[0] + 0.5 : c[0];
        return new int[]{x0 + (int) Math.round(col * colW + colW / 2.0), ny + c[1] * rowH + nodeH / 2};
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Hit h : hits) if (h.in(mx, my)) {
            if (button == 0) h.run().run();
            else if (button == 1 && h.right() != null) h.right().run();
            return true;
        }
        if (button == 1) picked = -1;
        return super.mouseClicked(mx, my, button);
    }
}
