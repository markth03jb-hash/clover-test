package com.ironspellgrimoire.client;

import com.ironspellgrimoire.server.ParasiteFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The Parasite Grimoire's Evolution page, in three tabs:
 *  TREE       four branches - Predator (moving), Arsenal (the forms, and the two hybrids), Hunger (biomass) and
 *             Ultimates (each ultimate takes one of two paths for good) - bought with evolution points. A node's
 *             description shows along the foot of the page when you point at it.
 *  MUTATIONS  the eight mutations (the ones not found yet hidden), and the three slots to wear them in.
 *  STRAIN     after 6 points spent, the choice of Strain (for good); then the strain's awakening (every node evolved),
 *             its signature move and the Final Form.
 */
public class EvolutionScreen extends Screen {
    static final int INK = 0xF00A0507, PANEL = 0xE0180A0E, EDGE = 0xFF8C1C28, EDGE_DIM = 0xFF3A1218, TEXT = 0xFFF2E4E4, MUTED = 0xFF9A8088,
            GOLD = 0xFFFFC060, BONE = 0xFFC8C8CC;
    static final String[] BRANCH = {"PREDATOR", "ARSENAL", "HUNGER", "ULTIMATES"};
    static final String[] TABS = {"Tree", "Mutations", "Organs", "Strain"};
    static final String[][] STRAIN_TEXT = {
            {"Hunter", "+20% speed. Talons and blade hit harder and faster. Consume reaches further and lunges."},
            {"Brute", "+6 health. Fist and lance hit harder; the Bulwark blocks more; wider slams."},
            {"Hive", "+20% biomass. Kills burst into spines; three spines a shot; stronger ultimates."}};
    static final String[] ART_TEXT = {"",
            "Apex Chain: flash from foe to foe - up to five - cutting each.",
            "Colossus: 15 s bigger, harder-hitting, armoured and unstoppable.",
            "Swarm: 20 s of parasite motes darting at every foe round you."};

    /** The tab a fresh page opens on (the screenshots set it). */
    public static int startTab = 0;
    private int tab = startTab;
    private String foot = "";

    private record Hit(int x, int y, int w, int h, Runnable run) { boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; } }
    private final List<Hit> hits = new ArrayList<>();

    EvolutionScreen() { super(Component.literal("Evolution")); }

    static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EvolutionScreen s) s.refresh();
        else mc.setScreen(new EvolutionScreen());
    }

    void refresh() { }

    /** Switch the open page to a tab (the screenshots use it). */
    public static void showTab(int t) { if (Minecraft.getInstance().screen instanceof EvolutionScreen s) s.tab = t; }

    @Override public boolean isPauseScreen() { return false; }

    private static boolean over(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        hits.clear();
        foot = "";
        int W = Math.min(480, width - 16), H = Math.min(262, height - 12);
        int x0 = (width - W) / 2, y0 = (height - H) / 2;
        g.fill(x0, y0, x0 + W, y0 + H, INK);
        g.fill(x0 + 2, y0 + 2, x0 + W - 2, y0 + H - 2, PANEL);
        g.renderOutline(x0, y0, W, H, EDGE);
        // the head: title, tabs, points
        g.drawString(font, "EVOLUTION", x0 + 8, y0 + 7, 0xFFFF6070, true);
        int tx = x0 + 8 + font.width("EVOLUTION") + 12;
        for (int t = 0; t < TABS.length; t++) {
            int tw = font.width(TABS[t]) + 10;
            boolean on = t == tab, hv = over(mx, my, tx, y0 + 4, tw, 14);
            g.fill(tx, y0 + 4, tx + tw, y0 + 18, on ? 0xFF501820 : hv ? 0xC0301018 : 0xC0200C10);
            g.renderOutline(tx, y0 + 4, tw, 14, on ? EDGE : EDGE_DIM);
            g.drawString(font, TABS[t], tx + 5, y0 + 7, on ? TEXT : MUTED, false);
            final int which = t;
            hits.add(new Hit(tx, y0 + 4, tw, 14, () -> tab = which));
            tx += tw + 3;
        }
        String pts = ParasiteClient.ep + " EP";
        g.drawString(font, pts, x0 + W - 8 - font.width(pts), y0 + 7, GOLD, true);
        g.fill(x0 + 6, y0 + 21, x0 + W - 6, y0 + 22, EDGE_DIM);
        int top = y0 + 26, bottom = y0 + H - 16;
        switch (tab) {
            case 1 -> mutations(g, mx, my, x0, top, W, bottom);
            case 2 -> organs(g, mx, my, x0, top, W, bottom);
            case 3 -> strain(g, mx, my, x0, top, W, bottom);
            default -> tree(g, mx, my, x0, top, W, bottom);
        }
        // the foot: what's pointed at
        g.fill(x0 + 6, y0 + H - 15, x0 + W - 6, y0 + H - 14, EDGE_DIM);
        String f = foot.isEmpty() ? "Biomass " + Math.round(ParasiteClient.biomass) + "   Devoured " + ParasiteClient.devoured
                + (ParasiteClient.strain > 0 ? "   Strain: " + ParasiteFx.STRAINS[ParasiteClient.strain] : "") : foot;
        if (font.width(f) > W - 16) f = font.plainSubstrByWidth(f, W - 22) + "...";
        g.drawString(font, f, x0 + 8, y0 + H - 11, foot.isEmpty() ? MUTED : TEXT, false);
        super.render(g, mx, my, pt);
    }

    // ------------------------------------------------------------------ the tree
    private void tree(GuiGraphics g, int mx, int my, int x0, int top, int W, int bottom) {
        int colW = (W - 12 - 9) / 4;
        int rows = 7, gap = 2;
        int h = Math.max(18, Math.min(24, (bottom - top - 12) / rows - gap));
        for (int b = 0; b < 4; b++) {
            int cx = x0 + 6 + b * (colW + 3);
            g.drawCenteredString(font, BRANCH[b], cx + colW / 2, top, BONE);
            int y = top + 11;
            for (int i = 0; i < ParasiteFx.NODES.length; i++) {
                if (ParasiteFx.NODES[i][0] != b) continue;
                int rk = ParasiteClient.rank(i), max = ParasiteFx.NODES[i][1], cost = ParasiteFx.NODES[i][2];
                boolean path = b == 3;
                boolean full = rk >= max, afford = ParasiteClient.ep >= cost;
                int need = i == ParasiteFx.F_WHIP ? ParasiteFx.F_LANCE : i == ParasiteFx.F_RAM ? ParasiteFx.F_BULWARK : -1;
                boolean locked = need >= 0 && ParasiteClient.rank(need) <= 0;
                boolean hv = over(mx, my, cx, y, colW, h);
                g.fill(cx, y, cx + colW, y + h, full ? 0xC0301018 : hv && afford && !locked ? 0xC0501820 : 0xC0200C10);
                g.renderOutline(cx, y, colW, h, full ? 0xFFB03040 : afford && !locked ? EDGE : EDGE_DIM);
                String name = ParasiteFx.NODE_NAMES[i];
                if (font.width(name) > colW - 8) name = font.plainSubstrByWidth(name, colW - 10);
                g.drawString(font, name, cx + 4, y + 3, locked ? 0xFF6A5A5A : full ? 0xFFFFB0B8 : TEXT, false);
                int ly = y + h - 9;
                if (path) {   // the two paths, as buttons
                    String[] two = ParasiteFx.PATHS[i - ParasiteFx.U_STORM];
                    int bx = cx + 4;
                    for (int k = 0; k < 2; k++) {
                        int bw = font.width(two[k]) + 6;
                        boolean chosen = rk == k + 1, hb = over(mx, my, bx, ly - 2, bw, 10);
                        if (chosen || (!full && hb)) g.fill(bx, ly - 2, bx + bw, ly + 8, chosen ? 0xFF8C1C28 : 0xC0501820);
                        g.drawString(font, two[k], bx + 3, ly - 1, chosen ? 0xFFFFE0E0 : full ? 0xFF6A5A5A : afford ? GOLD : MUTED, false);
                        if (hb) foot = two[k] + ": " + ParasiteFx.PATH_DESC[i - ParasiteFx.U_STORM][k] + (full ? "" : "  (" + cost + " EP, for good)");
                        if (!full) { final int arg = i + 100 * (k + 1); hits.add(new Hit(bx, ly - 2, bw, 10, () -> ParasiteClient.send(7, arg))); }
                        bx += bw + 4;
                    }
                } else {
                    for (int k = 0; k < max; k++) {   // rank pips
                        int px = cx + 4 + k * 7;
                        g.fill(px, ly, px + 5, ly + 5, k < rk ? 0xFFFF4050 : 0xFF40262A);
                    }
                    if (!full) {
                        String c = cost + " EP";
                        g.drawString(font, c, cx + colW - 4 - font.width(c), ly - 2, locked ? 0xFF6A5A5A : afford ? GOLD : 0xFF7A5A40, false);
                        final int node = i;
                        hits.add(new Hit(cx, y, colW, h, () -> ParasiteClient.send(7, node)));
                    }
                    if (hv) foot = ParasiteFx.NODE_NAMES[i] + ": " + ParasiteFx.NODE_DESC[i];
                }
                y += h + gap;
            }
        }
    }

    // ------------------------------------------------------------------ mutations
    private void mutations(GuiGraphics g, int mx, int my, int x0, int top, int W, int bottom) {
        int worn = 0;
        for (int sl : ParasiteClient.slots) if (sl >= 0) worn++;
        g.drawCenteredString(font, "Worn " + worn + " / " + ParasiteFx.SLOTS + "  -  found by devouring big prey, always from bosses", x0 + W / 2, top, BONE);
        int cols = 4, cw = (W - 12 - 3 * (cols - 1)) / cols, ch = Math.min(56, (bottom - top - 14) / 2 - 3);
        for (int m = 0; m < ParasiteFx.MUTATIONS; m++) {
            int cx = x0 + 6 + (m % cols) * (cw + 3), cy = top + 12 + (m / cols) * (ch + 3);
            boolean have = (ParasiteClient.muts & (1 << m)) != 0, wear = ParasiteClient.wears(m), hv = over(mx, my, cx, cy, cw, ch);
            g.fill(cx, cy, cx + cw, cy + ch, wear ? 0xD0501820 : hv && have ? 0xC0301018 : 0xC0200C10);
            g.renderOutline(cx, cy, cw, ch, wear ? 0xFFFF6070 : have ? EDGE : EDGE_DIM);
            if (!have) { g.drawCenteredString(font, "???", cx + cw / 2, cy + ch / 2 - 4, 0xFF5A4448); continue; }
            g.drawString(font, ParasiteFx.MUT_NAMES[m], cx + 4, cy + 4, wear ? 0xFFFFE070 : TEXT, false);
            List<FormattedCharSequence> ls = font.split(Component.literal(ParasiteFx.MUT_DESC[m]), cw - 8);
            int maxLines = Math.max(0, (ch - 16) / 9);
            for (int k = 0; k < Math.min(maxLines, ls.size()); k++) g.drawString(font, ls.get(k), cx + 4, cy + 15 + k * 9, MUTED, false);
            if (hv) foot = ParasiteFx.MUT_NAMES[m] + ": " + ParasiteFx.MUT_DESC[m] + (wear ? "  (click to take it off)" : "  (click to wear it)");
            final int which = m;
            hits.add(new Hit(cx, cy, cw, ch, () -> ParasiteClient.send(10, which)));
        }
    }

    // ------------------------------------------------------------------ organs
    private void organs(GuiGraphics g, int mx, int my, int x0, int top, int W, int bottom) {
        g.drawCenteredString(font, "Grow one organ in each slot (" + ParasiteFx.ORGAN_COST + " EP the first time) - swap freely after", x0 + W / 2, top, BONE);
        int rowH = Math.min(58, (bottom - top - 12) / 3 - 3), labelW = 44, cw = (W - 12 - labelW - 6) / 3;
        for (int slot = 0; slot < 3; slot++) {
            int y = top + 12 + slot * (rowH + 3);
            int cur = ParasiteClient.organs[slot];
            g.drawString(font, ParasiteFx.ORGAN_SLOTS[slot], x0 + 8, y + rowH / 2 - 4, cur > 0 ? 0xFFFF7080 : MUTED, false);
            for (int c = 1; c <= 3; c++) {
                int cx = x0 + 6 + labelW + (c - 1) * (cw + 3);
                boolean on = cur == c, hv = over(mx, my, cx, y, cw, rowH);
                g.fill(cx, y, cx + cw, y + rowH, on ? 0xD0501820 : hv ? 0xC0301018 : 0xC0200C10);
                g.renderOutline(cx, y, cw, rowH, on ? 0xFFFF6070 : EDGE);
                g.drawString(font, ParasiteFx.ORGANS[slot][c - 1], cx + 4, cy(y), on ? 0xFFFFE070 : TEXT, false);
                List<FormattedCharSequence> ls = font.split(Component.literal(ParasiteFx.ORGAN_DESC[slot][c - 1]), cw - 8);
                for (int k = 0; k < Math.min(Math.max(0, (rowH - 16) / 9), ls.size()); k++) g.drawString(font, ls.get(k), cx + 4, y + 15 + k * 9, MUTED, false);
                if (hv) foot = ParasiteFx.ORGANS[slot][c - 1] + ": " + ParasiteFx.ORGAN_DESC[slot][c - 1] + (on ? "" : cur == 0 ? "  (grow it: " + ParasiteFx.ORGAN_COST + " EP)" : "  (swap to it)");
                if (!on) { final int arg = slot * 10 + c; hits.add(new Hit(cx, y, cw, rowH, () -> ParasiteClient.send(17, arg))); }
            }
        }
    }

    private static int cy(int y) { return y + 4; }

    // ------------------------------------------------------------------ the strain, its awakening and the Final Form
    private void strain(GuiGraphics g, int mx, int my, int x0, int top, int W, int bottom) {
        int st = ParasiteClient.strain;
        if (st == 0) {
            boolean ready = ParasiteClient.spent >= ParasiteFx.STRAIN_AT;
            g.drawCenteredString(font, ready ? "The strain is ready to settle - choose for good"
                    : "Spend " + (ParasiteFx.STRAIN_AT - ParasiteClient.spent) + " more evolution points to settle a Strain", x0 + W / 2, top, ready ? 0xFFFF8090 : MUTED);
            int cw = (W - 12 - 6) / 3, chh = Math.min(80, bottom - top - 16);
            for (int s = 0; s < 3; s++) {
                int cx = x0 + 6 + s * (cw + 3), cy = top + 12;
                boolean hv = ready && over(mx, my, cx, cy, cw, chh);
                g.fill(cx, cy, cx + cw, cy + chh, hv ? 0xD0501820 : 0xD0200C10);
                g.renderOutline(cx, cy, cw, chh, hv ? 0xFFFF5060 : ready ? EDGE : EDGE_DIM);
                g.drawCenteredString(font, STRAIN_TEXT[s][0], cx + cw / 2, cy + 4, ready ? 0xFFFF7080 : MUTED);
                List<FormattedCharSequence> ls = font.split(Component.literal(STRAIN_TEXT[s][1]), cw - 8);
                for (int k = 0; k < Math.min((chh - 18) / 10, ls.size()); k++) g.drawString(font, ls.get(k), cx + 4, cy + 16 + k * 10, MUTED, false);
                if (ready) { final int pick = s + 1; hits.add(new Hit(cx, cy, cw, chh, () -> ParasiteClient.send(8, pick))); }
            }
            return;
        }
        int total = 0, have = 0;
        for (int i = 0; i < ParasiteFx.NODES.length; i++) { total += ParasiteFx.NODES[i][1]; have += Math.min(ParasiteFx.NODES[i][1], ParasiteClient.rank(i)); }
        int y = top;
        g.drawString(font, "Strain: " + ParasiteFx.STRAINS[st], x0 + 10, y, 0xFFFF7080, true);
        y += 13;
        for (FormattedCharSequence l : font.split(Component.literal(STRAIN_TEXT[st - 1][1]), W - 20)) { g.drawString(font, l, x0 + 10, y, MUTED, false); y += 10; }
        y += 6;
        g.drawString(font, ParasiteClient.awakened ? "AWAKENED" : "Awakening: " + have + " / " + total + " evolved", x0 + 10, y, ParasiteClient.awakened ? 0xFFFF5040 : TEXT, true);
        y += 12;
        int bw = W - 20;   // a bar of how far the strain has come
        g.fill(x0 + 10, y, x0 + 10 + bw, y + 4, 0xFF301018);
        g.fill(x0 + 10, y, x0 + 10 + bw * have / Math.max(1, total), y + 4, ParasiteClient.awakened ? 0xFFFF5040 : 0xFFB01828);
        y += 10;
        String art = ART_TEXT[st] + (ParasiteClient.awakened ? (ParasiteClient.artCd > 0 ? "  (ready in " + ParasiteClient.artCd + " s)" : "  (ready - on your wheel)") : "");
        for (FormattedCharSequence l : font.split(Component.literal(art), W - 20)) { g.drawString(font, l, x0 + 10, y, ParasiteClient.awakened ? TEXT : MUTED, false); y += 10; }
        y += 6;
        String fin = "Final Form: 30 s as the strain itself - plated, bigger, both arms changed, spines bursting out of you and Consume "
                + "devouring everything weak round you. Costs 100 biomass; 5 min to regrow."
                + (ParasiteClient.awakened ? (ParasiteClient.finalCd > 0 ? "  (ready in " + ParasiteClient.finalCd + " s)" : "  (ready - on your wheel)") : "  Needs the awakening.");
        for (FormattedCharSequence l : font.split(Component.literal(fin), W - 20)) { if (y > bottom - 10) break; g.drawString(font, l, x0 + 10, y, ParasiteClient.awakened ? TEXT : MUTED, false); y += 10; }
        y += 4;
        // the second strain: grafted once awakened, at half strength
        if (ParasiteClient.awakened && ParasiteClient.strain2 == 0 && y + 32 < bottom) {
            g.drawString(font, "Graft a second strain (half strength, for good):", x0 + 10, y, 0xFFFF8090, false);
            y += 11;
            int bx = x0 + 10;
            for (int s2 = 1; s2 <= 3; s2++) {
                if (s2 == st) continue;
                String nm = ParasiteFx.STRAINS[s2];
                int bw2 = font.width(nm) + 12;
                boolean hv = over(mx, my, bx, y, bw2, 14);
                g.fill(bx, y, bx + bw2, y + 14, hv ? 0xD0501820 : 0xC0200C10);
                g.renderOutline(bx, y, bw2, 14, hv ? 0xFFFF5060 : EDGE);
                g.drawString(font, nm, bx + 6, y + 3, TEXT, false);
                if (hv) foot = "Graft " + nm + ": " + STRAIN_TEXT[s2 - 1][1] + " (all at half)";
                final int pick = s2;
                hits.add(new Hit(bx, y, bw2, 14, () -> ParasiteClient.send(16, pick)));
                bx += bw2 + 4;
            }
            y += 18;
        } else if (ParasiteClient.strain2 > 0 && y + 10 < bottom) {
            g.drawString(font, "Grafted: " + ParasiteFx.STRAINS[ParasiteClient.strain2] + " (half strength)", x0 + 10, y, 0xFFFF9090, false);
            y += 12;
        }
        // adaptation: what the strain has learned to shrug off
        if (ParasiteClient.rank(ParasiteFx.ADAPT) > 0 && y + 12 < bottom) {
            g.drawString(font, "Adapted:", x0 + 10, y, BONE, false);
            int ax = x0 + 10 + font.width("Adapted:") + 6;
            for (int h = 0; h < ParasiteFx.HARMS.length; h++) {
                String a = ParasiteFx.HARMS[h] + " " + ParasiteClient.adapt[h] + "%";
                g.drawString(font, a, ax, y, ParasiteClient.adapt[h] > 0 ? 0xFF9AE08A : MUTED, false);
                ax += font.width(a) + 8;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) for (Hit h : hits) if (h.in(mx, my)) { h.run().run(); return true; }
        return super.mouseClicked(mx, my, button);
    }
}
