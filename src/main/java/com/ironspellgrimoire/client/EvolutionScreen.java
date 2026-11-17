package com.ironspellgrimoire.client;

import com.ironspellgrimoire.server.ParasiteFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Parasite Grimoire's Evolution page: three branches of the strain - Predator (moving), Arsenal (the forms) and
 * Hunger (biomass) - bought with evolution points (one for every creature devoured). After 6 points spent, the
 * strain settles: three Strain cards appear, and the choice is for good.
 */
public class EvolutionScreen extends Screen {
    static final int INK = 0xF00A0507, PANEL = 0xE0180A0E, EDGE = 0xFF8C1C28, EDGE_DIM = 0xFF3A1218, TEXT = 0xFFF2E4E4, MUTED = 0xFF9A8088, GOLD = 0xFFFFC060, BONE = 0xFFC8C8CC;
    static final String[] BRANCH = {"PREDATOR", "ARSENAL", "HUNGER"};
    static final String[][] STRAIN_TEXT = {
            {"Hunter", "+20% speed. Talons and blade hit harder and faster. Consume reaches further and lunges."},
            {"Brute", "+6 health. Fist and lance hit harder; the Bulwark blocks more; wider slams."},
            {"Hive", "+20% biomass. Kills burst into spines; three spines a shot; stronger ultimates."}};

    private record Hit(int x, int y, int w, int h, Runnable run) { boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; } }
    private final List<Hit> hits = new ArrayList<>();

    EvolutionScreen() { super(Component.literal("Evolution")); }

    static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EvolutionScreen s) s.refresh();
        else mc.setScreen(new EvolutionScreen());
    }

    void refresh() { }

    @Override public boolean isPauseScreen() { return false; }

    private boolean strainPick() { return ParasiteClient.strain == 0 && ParasiteClient.spent >= ParasiteFx.STRAIN_AT; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        hits.clear();
        int W = Math.min(420, width - 16), H = Math.min(strainPick() ? 344 : 262, height - 12);
        int x0 = (width - W) / 2, y0 = (height - H) / 2;
        g.fill(x0, y0, x0 + W, y0 + H, INK);
        g.fill(x0 + 2, y0 + 2, x0 + W - 2, y0 + H - 2, PANEL);
        g.renderOutline(x0, y0, W, H, EDGE);
        // the head: the strain, points, biomass, prey
        g.drawString(font, "EVOLUTION", x0 + 10, y0 + 8, 0xFFFF6070, true);
        String st = ParasiteClient.strain > 0 ? "Strain: " + ParasiteFx.STRAINS[ParasiteClient.strain] : "No strain yet";
        g.drawString(font, st, x0 + 10, y0 + 20, ParasiteClient.strain > 0 ? 0xFFFF9090 : MUTED, false);
        String pts = ParasiteClient.ep + " evolution points";
        g.drawString(font, pts, x0 + W - 10 - font.width(pts), y0 + 8, GOLD, true);
        String sub = "Biomass " + Math.round(ParasiteClient.biomass) + "   Devoured " + ParasiteClient.devoured;
        g.drawString(font, sub, x0 + W - 10 - font.width(sub), y0 + 20, MUTED, false);
        g.fill(x0 + 8, y0 + 32, x0 + W - 8, y0 + 33, EDGE_DIM);
        // the three branches
        int colW = (W - 24) / 3, top = y0 + 40;
        for (int b = 0; b < 3; b++) {
            int cx = x0 + 8 + b * (colW + 4);
            g.drawCenteredString(font, BRANCH[b], cx + colW / 2, top, BONE);
            int y = top + 12;
            for (int i = 0; i < ParasiteFx.NODES.length; i++) {
                if (ParasiteFx.NODES[i][0] != b) continue;
                int rk = ParasiteClient.rank(i), max = ParasiteFx.NODES[i][1], cost = ParasiteFx.NODES[i][2];
                boolean full = rk >= max, afford = ParasiteClient.ep >= cost;
                int h = 34;
                boolean over = mx >= cx && mx < cx + colW && my >= y && my < y + h;
                g.fill(cx, y, cx + colW, y + h, full ? 0xC0301018 : over && afford ? 0xC0501820 : 0xC0200C10);
                g.renderOutline(cx, y, colW, h, full ? 0xFFB03040 : afford ? EDGE : EDGE_DIM);
                g.drawString(font, ParasiteFx.NODE_NAMES[i], cx + 5, y + 4, full ? 0xFFFFB0B8 : TEXT, false);
                for (int k = 0; k < max; k++) {   // rank pips
                    int px = cx + colW - 8 - (max - k) * 7;
                    g.fill(px, y + 5, px + 5, y + 10, k < rk ? 0xFFFF4050 : 0xFF40262A);
                }
                List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(ParasiteFx.NODE_DESC[i]), colW - 34);
                for (int k = 0; k < Math.min(2, lines.size()); k++) g.drawString(font, lines.get(k), cx + 5, y + 14 + k * 9, MUTED, false);
                if (!full) {
                    String c = cost + " EP";
                    g.drawString(font, c, cx + colW - 5 - font.width(c), y + h - 10 + 0, afford ? GOLD : 0xFF7A5A40, false);
                    final int node = i;
                    hits.add(new Hit(cx, y, colW, h, () -> ParasiteClient.send(7, node)));
                }
                y += h + 3;
            }
        }
        // the strains
        if (strainPick()) {
            int sy = y0 + H - 92;
            g.fill(x0 + 8, sy - 6, x0 + W - 8, sy - 5, EDGE_DIM);
            g.drawCenteredString(font, "The strain is ready to settle - choose for good", x0 + W / 2, sy - 2, 0xFFFF8090);
            int cw = (W - 24) / 3;
            for (int s = 0; s < 3; s++) {
                int cx = x0 + 8 + s * (cw + 4), cy = sy + 10;
                boolean over = mx >= cx && mx < cx + cw && my >= cy && my < cy + 70;
                g.fill(cx, cy, cx + cw, cy + 70, over ? 0xD0501820 : 0xD0200C10);
                g.renderOutline(cx, cy, cw, 70, over ? 0xFFFF5060 : EDGE);
                g.drawCenteredString(font, STRAIN_TEXT[s][0], cx + cw / 2, cy + 4, 0xFFFF7080);
                List<net.minecraft.util.FormattedCharSequence> ls = font.split(Component.literal(STRAIN_TEXT[s][1]), cw - 8);
                for (int k = 0; k < Math.min(5, ls.size()); k++) g.drawString(font, ls.get(k), cx + 4, cy + 16 + k * 10, MUTED, false);
                final int pick = s + 1;
                hits.add(new Hit(cx, cy, cw, 70, () -> ParasiteClient.send(8, pick)));
            }
        } else if (ParasiteClient.strain == 0) {
            String n = "Spend " + Math.max(0, ParasiteFx.STRAIN_AT - ParasiteClient.spent) + " more to settle a Strain";
            g.drawCenteredString(font, n, x0 + W / 2, y0 + H - 12, MUTED);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) for (Hit h : hits) if (h.in(mx, my)) { h.run().run(); return true; }
        return super.mouseClicked(mx, my, button);
    }
}
