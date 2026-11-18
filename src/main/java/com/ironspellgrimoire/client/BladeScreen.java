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
    /** The Guide view instead of the tree (the screenshots open on it). */
    public static boolean startGuide = false;
    private boolean guide = startGuide;

    public BladeScreen() { super(Component.literal("The Blade")); }

    void refresh() { }

    @Override public boolean isPauseScreen() { return false; }

    /** Where each sword sits: column (0-7, the two branches of each path side by side) and row (0 base, 1 stage 4, 2 stage 5, 3 stage 6). */
    static int[] cell(int f) {
        if (f < 3) return new int[]{2 + f, 0};   // the base three across the top, in the middle
        if (f >= 23) return new int[]{1 + f - 23, 4};   // the six hybrids along the bottom
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
        // fits a 480 x 270 screen (GUI scale 4 at 1080p): five rows of swords, the four slots and two lines of foot
        int colW = Math.min(56, (width - 20) / 8), nodeW = colW - 6, rowH = Math.max(30, Math.min(46, (height - 8 - 30 - 60) / 5)), nodeH = rowH - 6;
        int slotH = 24, W = colW * 8, x0 = (width - W) / 2, top = 30;
        int H = top + rowH * 5 + 12 + slotH + 4 + 22;
        int y0 = Math.max(4, (height - H) / 2);
        g.fill(x0 - 8, y0, x0 + W + 8, y0 + H, PANEL);
        g.renderOutline(x0 - 8, y0, W + 16, H, EDGE);
        // the header: growth, the hunger and its mood, the bond
        g.drawString(font, "THE BLADE", x0, y0 + 6, 0xFFFF6070, true);
        String gb = guide ? "< Tree" : "? Guide";
        int gbw = font.width(gb) + 8, gbx = x0 + W - gbw;
        boolean gOver = mx >= gbx && mx < gbx + gbw && my >= y0 + 4 && my < y0 + 16;
        g.fill(gbx, y0 + 4, gbx + gbw, y0 + 16, gOver ? 0xFF4A1820 : 0xFF2A0C12);
        g.renderOutline(gbx, y0 + 4, gbw, 12, EDGE);
        g.drawString(font, gb, gbx + 4, y0 + 6, GOLD, false);
        hits.add(new Hit(gbx, y0 + 4, gbw, 12, () -> guide = !guide, null));
        String gr = HungerClient.growth + " Growth";
        g.drawString(font, gr, gbx - 8 - font.width(gr), y0 + 6, GOLD, true);
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

        if (guide) { guide(g, x0, y0 + top, W); return; }
        int hover = forceHover;
        // the links first, then the swords over them
        int ny = y0 + top;
        for (int f = 1; f < HungerFx.FORMS; f++) {
            if (HungerFx.PARENT[f] < 0) continue;   // a hybrid grows from two paths, not one sword
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
            boolean have = HungerClient.has(f), canGrow = HungerFx.canGrow(HungerClient.unlocked, f);
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
        int sy = ny + rowH * 5 + 2, sw = 64, sx0 = width / 2 - (sw * 4 + 18) / 2;
        g.drawString(font, "CARRIED (switch on the G wheel)", x0, sy, MUTED, false);
        sy += 11;
        for (int i = 0; i < 4; i++) {
            int x = sx0 + i * (sw + 6), f = HungerClient.load[i];
            boolean over = mx >= x && mx < x + sw && my >= sy && my < sy + slotH;
            g.fill(x, sy, x + sw, sy + slotH, 0xF0100608);
            g.renderOutline(x, sy, sw, slotH, i == HungerClient.sel ? GOLD : over && picked >= 0 ? 0xFFFFE070 : EDGE);
            g.drawString(font, (i + 1) + "", x + 3, sy + 3, MUTED, false);
            if (f >= 0 && HungerClient.has(f)) {
                g.renderItem(stack(f), x + sw / 2 - 8, sy);
                g.pose().pushPose();
                String nm = shortName(f);
                float sc = Math.min(1F, (sw - 4) / (float) Math.max(1, font.width(nm)));
                g.pose().translate(x + sw / 2F, sy + slotH - 8, 0);
                g.pose().scale(sc, sc, 1);
                g.drawCenteredString(font, nm, 0, 0, TEXT);
                g.pose().popPose();
                if (over) hover = f;
            } else g.drawCenteredString(font, "empty", x + sw / 2, sy + 8, MUTED);
            final int slot = i;
            hits.add(new Hit(x, sy, sw, slotH, () -> { if (picked >= 0) { HungerClient.send(3, slot * 100 + picked); picked = -1; } },
                    () -> HungerClient.send(3, slot * 100 + 99)));
        }
        // the foot: what the pointed-at sword is
        if (hover >= 0) {
            int f = hover;
            foot = HungerFx.NAMES[f] + " - " + HungerFx.STAGE[f] + " " + HungerFx.LINE_NAMES[HungerFx.line(f)] + (HungerFx.line2(f) > 0 ? "+" + HungerFx.LINE_NAMES[HungerFx.line2(f)] : "")
                    + String.format("  %.0f dmg, %.1f/s", HungerFx.DMG[f], 4 + HungerFx.SPEED[f]) + (HungerFx.REACH[f] > 0 ? String.format(", +%.1f reach", HungerFx.REACH[f]) : "");
            String from = HungerFx.PARENT[f] == -2 ? "an Ascended " + HungerFx.LINE_NAMES[HungerFx.line(f)] + " and an Ascended " + HungerFx.LINE_NAMES[HungerFx.line2(f)] + " sword"
                    : HungerFx.PARENT[f] < 0 ? "-" : HungerFx.NAMES[HungerFx.PARENT[f]];
            foot2 = HungerClient.has(f) ? HungerFx.PASSIVE[f] : "Grows from " + from + " for " + HungerFx.COST[HungerFx.STAGE[f]] + " Growth.";
        } else { foot = "Kills with the blade feed it Growth; stage 4 opens four paths."; foot2 = "Carry four swords. You can't change them in a fight."; }
        int fy = sy + slotH + 4;
        g.drawCenteredString(font, foot, width / 2, fy, TEXT);
        List<FormattedCharSequence> lines = font.split(Component.literal(foot2), W);
        if (!lines.isEmpty()) g.drawCenteredString(font, lines.get(0), width / 2, fy + 10, MUTED);
    }

    /** Every mechanic, its controls, and what unlocks the ones not yet open. */
    private void guide(GuiGraphics g, int x, int y, int W) {
        int bl = HungerClient.bondLevel(), mask = HungerClient.unlocked, asc = 0;
        boolean six = false;
        for (int f = 0; f < HungerFx.FORMS; f++) if ((mask & (1 << f)) != 0 && HungerFx.STAGE[f] >= 6) six = true;
        for (int path = 1; path <= 4; path++) { int a = 3 + (path - 1) * 5 + 2; if ((mask & (1 << a)) != 0 || (mask & (1 << (a + 2))) != 0) asc++; }
        Object[][] rows = {
                {"Draw", "Draw page: the blade into your empty hand, or back.", true, ""},
                {"Growth", "Kills with the blade feed it. Spend it here to grow new swords.", true, ""},
                {"Carry & switch", "Click a grown sword, then a slot - up to 4. Press G to switch.", true, ""},
                {"Parry", "Tap right-click just before a hit: 80% less, and it bites back.", true, ""},
                {"Charged strike", "Hold right-click ~1 s, let go: your path's own strike.", true, ""},
                {"Devour", "Sneak and hit a foe under 15% (not a boss): eaten whole, double Growth.", true, ""},
                {"Moods", "Fed: faster swings. Hungry: harder hits, pulls you to prey. Starved: bites.", true, ""},
                {"Guard strike", "Kept fed, it cuts foes that hit you from behind.", bl >= 2, "Bond 2"},
                {"Throw", "Sneak + Gnash: it flies, cuts all it passes, and comes back.", bl >= 3, "Bond 3"},
                {"Twin", "A small twin grows into your empty off hand and bites after each hit.", bl >= 4, "Bond 4"},
                {"Pact & spirit", "Unleash: 10 s of harder hits that heal, and a ghost blade at your side.", bl >= 5, "Bond 5"},
                {"Hunger Domain", "Its page: 20 s of slowed prey; every kill inside fills the blade.", six, "a stage 6 sword"},
                {"Hybrids", "Two paths fused: the bottom row, 300 Growth each.", asc >= 2, "Ascended swords of two paths"}};
        int rowH = Math.max(11, Math.min(16, (height - y - 12) / rows.length));
        for (int i = 0; i < rows.length; i++) {
            boolean open = (Boolean) rows[i][2];
            int yy = y + i * rowH;
            g.drawString(font, (String) rows[i][0], x, yy, open ? 0xFFFF8080 : MUTED, false);
            String txt = open ? (String) rows[i][1] : "Unlocks with " + rows[i][3] + ".";
            int tx = x + 86;
            float sc = Math.min(1F, (W - 86) / (float) font.width(txt));
            g.pose().pushPose();
            g.pose().translate(tx, yy, 0);
            g.pose().scale(sc, sc, 1);
            g.drawString(font, txt, 0, 0, open ? TEXT : 0xFF6A5A60, false);
            g.pose().popPose();
        }
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
