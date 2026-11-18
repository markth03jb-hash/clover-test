package com.ironspellgrimoire.client;

import com.ironspellgrimoire.item.TwinMaskItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

import static com.ironspellgrimoire.server.TwinsFx.*;

/**
 * The Masks (the Masked Twins Grimoire's menu): one window split down the middle - warm gold parchment with faint sun
 * rays on the left, night blue with stars on the right, and a seam between them that glows toward the twin you wear.
 * Tabs: Sol and Nyx (level, the eight arts, the four on the wheel), Bond (Twin Strike, Mirror Swap, the trial, Eclipse
 * Union; the titles), Masks (the two masks as they are now, their stones and sockets) and Guide.
 */
public class TwinsScreen extends Screen {
    public static int startTab = -1;
    /** For the screenshots: the art shown as pointed at on a twin's tab (-1: follow the mouse). */
    public static int forceHover = -1;
    private int tab, slot, ticks;
    private final List<int[]> hits = new ArrayList<>();   // {x0, y0, x1, y1, action, arg}
    static final String[] TABS = {"SOL", "NYX", "BOND", "MASKS", "GUIDE"};
    static final int GOLD = 0xFFFFD36E, GOLD_D = 0xFFB07818, SILVER = 0xFFD6E2FF, SILVER_D = 0xFF6A7AB8, TEXT = 0xFFE8E4F0, DIM = 0xFF8C8698;

    public TwinsScreen() { super(Component.literal("The Masks")); tab = startTab >= 0 ? startTab : TwinsClient.active(); }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() { ticks++; }

    private int W() { return Math.min(420, width - 16); }
    private int H() { return Math.min(250, height - 12); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        hits.clear();
        int W = W(), H = H(), x0 = (width - W) / 2, y0 = (height - H) / 2, mid = x0 + W / 2;
        float t = ticks + pt;
        g.fillGradient(0, 0, width, height, 0xB0040408, 0xD0060612);
        // the window: one dark base, a gold half and a night half over it, the seam between
        g.fill(x0, y0, x0 + W, y0 + H, 0xF00C0A12);
        g.fillGradient(x0 + 2, y0 + 2, mid, y0 + H - 2, 0xA0583A14, 0xA0281A08);
        g.fillGradient(mid, y0 + 2, x0 + W - 2, y0 + H - 2, 0xA0182454, 0xA0080C20);
        for (int k = 0; k < 9; k++) {   // faint sun rays fanning from the top left
            double a = 0.15 + k * 0.16;
            for (int s = 10; s < Math.min(W / 2 - 6, 200); s += 3) {
                int px = x0 + 4 + (int) (Math.cos(a) * s), py = y0 + 4 + (int) (Math.sin(a) * s * 0.9);
                if (px < mid - 2 && py < y0 + H - 3) g.fill(px, py, px + 1, py + 1, 0x18FFD36E);
            }
        }
        for (int k = 0; k < 46; k++) {   // stars on the night half, twinkling
            int sx = mid + 4 + (k * 73 + 17) % (W / 2 - 8), sy = y0 + 4 + (k * 131 + 29) % (H - 8);
            int al = (int) (40 + 60 * (0.5 + 0.5 * Mth.sin(t * 0.07F + k)));
            g.fill(sx, sy, sx + 1, sy + 1, al << 24 | 0xE6EEFF);
        }
        int act = TwinsClient.active();
        for (int k = 1; k <= 6; k++) {   // the seam's glow, toward the worn twin
            int al = (int) ((0x50 - k * 7) * (0.8 + 0.2 * Mth.sin(t * 0.12F)));
            int col = act == SOL ? 0xFFD36E : 0xD6E2FF;
            int x = act == SOL ? mid - k : mid + k - 1;
            g.fill(x, y0 + 2, x + 1, y0 + H - 2, Math.max(0, al) << 24 | col);
        }
        g.fill(mid - 1, y0 + 2, mid + 1, y0 + H - 2, 0xFF040408);
        frame(g, x0, y0, W, H, mid);
        // the title and the tabs
        g.drawCenteredString(font, TwinsClient.eclipse() ? "ECLIPSE UNION" : "THE MASKS", x0 + W / 2, y0 + 6, TwinsClient.eclipse() ? 0xFFFFFFFF : TEXT);
        int tw = 62, tx0 = x0 + (W - tw * 5 - 4 * 4) / 2;
        for (int i = 0; i < 5; i++) {
            int tx = tx0 + i * (tw + 4), ty = y0 + 19;
            boolean on = i == tab, hov = mx >= tx && mx < tx + tw && my >= ty && my < ty + 13;
            int accent = i == 0 ? GOLD : i == 1 ? SILVER : i == 2 ? 0xFFF0E0FF : i == 3 ? 0xFFE8D8B8 : 0xFFC8C8D8;
            g.fill(tx, ty, tx + tw, ty + 13, on ? 0xE0302848 : hov ? 0xC0201C30 : 0xB0141020);
            g.fill(tx, ty + 12, tx + tw, ty + 13, on ? accent : 0x60FFFFFF & accent);
            g.drawCenteredString(font, TABS[i], tx + tw / 2, ty + 3, on ? accent : DIM);
            hits.add(new int[]{tx, ty, tx + tw, ty + 13, 0, i});
        }
        int top = y0 + 38;
        switch (tab) {
            case 0, 1 -> twin(g, tab, x0, top, W, H - 38, mid, mx, my);
            case 2 -> bond(g, x0, top, W, mid, mx, my);
            case 3 -> masks(g, x0, top, W, mid, mx, my);
            default -> guide(g, x0, top, W);
        }
    }

    private void frame(GuiGraphics g, int x0, int y0, int W, int H, int mid) {
        g.fill(x0, y0, mid, y0 + 1, GOLD); g.fill(x0, y0 + H - 1, mid, y0 + H, GOLD_D); g.fill(x0, y0, x0 + 1, y0 + H, GOLD);
        g.fill(mid, y0, x0 + W, y0 + 1, SILVER); g.fill(mid, y0 + H - 1, x0 + W, y0 + H, SILVER_D); g.fill(x0 + W - 1, y0, x0 + W, y0 + H, SILVER);
        for (int k = 0; k < 4; k++) {   // corner ornaments: a ray at each gold corner, a crescent tip at each silver one
            g.fill(x0 + 2 + k * 2, y0 + 2, x0 + 3 + k * 2, y0 + 3 + k, GOLD);
            g.fill(x0 + 2 + k * 2, y0 + H - 3 - k, x0 + 3 + k * 2, y0 + H - 2, GOLD_D);
            g.fill(x0 + W - 3 - k * 2, y0 + 2, x0 + W - 2 - k * 2, y0 + 3 + (3 - k), SILVER);
            g.fill(x0 + W - 3 - k * 2, y0 + H - 3 - (3 - k), x0 + W - 2 - k * 2, y0 + H - 2, SILVER_D);
        }
    }

    private boolean button(GuiGraphics g, int x, int y, int w, String label, boolean on, int accent, int mx, int my, int action, int arg) {
        boolean hov = on && mx >= x && mx < x + w && my >= y && my < y + 13;
        g.fill(x, y, x + w, y + 13, !on ? 0x60202028 : hov ? 0xE0383050 : 0xC0201A30);
        int c = on ? accent : 0xFF4A4658;
        g.fill(x, y, x + 1, y + 13, c); g.fill(x + w - 1, y, x + w, y + 13, c);
        g.fill(x, y, x + 3, y + 1, c); g.fill(x, y + 12, x + 3, y + 13, c); g.fill(x + w - 3, y, x + w, y + 1, c); g.fill(x + w - 3, y + 12, x + w, y + 13, c);
        g.drawCenteredString(font, label, x + w / 2, y + 3, on ? (hov ? 0xFFFFFFFF : accent) : 0xFF6A6678);
        if (on) hits.add(new int[]{x, y, x + w, y + 13, action, arg});
        return hov;
    }

    // ------------------------------------------------------------------ a twin's tab: its level, its eight arts, its wheel
    private void twin(GuiGraphics g, int t, int x0, int y, int W, int H, int mid, int mx, int my) {
        int col = t == SOL ? GOLD : SILVER, lv = TwinsClient.lv(t);
        boolean worn = TwinsClient.active() == t;
        g.drawString(font, TWIN[t] + "  Level " + lv + (worn ? "  - worn" : "  - resting"), x0 + 10, y, col, true);
        int xp = TwinsClient.v[V_XP + t], need = TwinsClient.v[V_NEED + t];
        String xs = lv >= MAX_LV ? "MAX" : xp + " / " + need + " XP";
        g.drawString(font, xs, x0 + W - 10 - font.width(xs), y, TEXT, true);
        int bw = W - 20;
        g.fill(x0 + 10, y + 11, x0 + 10 + bw, y + 15, 0xFF08080E);
        int fw = lv >= MAX_LV ? bw : Math.round(bw * Mth.clamp(xp / (float) Math.max(1, need), 0, 1));
        g.fillGradient(x0 + 10, y + 11, x0 + 10 + fw, y + 15, col, t == SOL ? GOLD_D : SILVER_D);
        String gift = t == SOL ? String.format("Worn: +%.1f%% attack damage.  Learns 25%% faster by day.", 0.5 * lv)
                : String.format("Worn: +%.1f%% speed, +%.1f%% spell power.  Learns faster by night.", 0.3 * lv, 0.4 * lv);
        g.drawString(font, gift, x0 + 10, y + 20, DIM, false);
        // the eight arts (left)
        int ly = y + 34, rowH = Math.max(14, Math.min(18, (H - 34 - 8) / 8)), lx = x0 + 8, lw = mid - 6 - lx;
        int hover = forceHover;
        for (int a = 0; a < 8; a++) {
            int ry = ly + a * rowH;
            boolean known = TwinsClient.knows(t, a), hov = mx >= lx && mx < lx + lw && my >= ry && my < ry + rowH - 2;
            if (hov) hover = a;
            int at = -1;
            for (int s = 0; s < 4; s++) if (TwinsClient.load(t, s) == a) at = s;
            g.fill(lx, ry, lx + lw, ry + rowH - 2, !known ? 0x50000000 : hover == a ? 0xC0403458 : at >= 0 ? 0x90302440 : 0x70181424);
            if (at >= 0) g.fill(lx, ry, lx + 2, ry + rowH - 2, col);
            g.drawString(font, ARTS[t][a], lx + 6, ry + (rowH - 10) / 2, known ? TEXT : 0xFF5E5A6A, false);
            int ms = com.ironspellgrimoire.server.TwinsFx.mastery(TwinsClient.uses(t, a));
            for (int k = 0; k < 3; k++) if (known) g.fill(lx + lw - 46 + k * 4, ry + rowH / 2 - 2, lx + lw - 44 + k * 4, ry + rowH / 2 + 1, k < ms ? col : 0x50FFFFFF);   // mastery pips
            String tag = !known ? "Lv " + ART_LV[a] : at >= 0 ? "slot " + (at + 1) : "";
            if (!tag.isEmpty()) g.drawString(font, tag, lx + lw - 4 - font.width(tag), ry + (rowH - 10) / 2, !known ? 0xFF6E6A7A : col, false);
            if (known) hits.add(new int[]{lx, ry, lx + lw, ry + rowH - 2, 1, t * 10 + a});
        }
        // the art pointed at (right), or the one in the chosen slot
        int rx = mid + 8, rw = x0 + W - 10 - rx;
        int show = hover >= 0 ? hover : Math.max(0, TwinsClient.load(t, slot));
        boolean known = TwinsClient.knows(t, show);
        g.drawString(font, ARTS[t][show], rx, ly, known ? col : DIM, true);
        String facts = "Lv " + ART_LV[show] + "  -  " + ART_CD[t][show] / 20 + " s  -  " + ART_MANA[t][show] + " mana";
        g.drawString(font, facts, rx, ly + 11, DIM, false);
        int u = TwinsClient.uses(t, show), ms = com.ironspellgrimoire.server.TwinsFx.mastery(u);
        String mline = ms >= 3 ? ART_PERF[t][show] : "Mastery " + (ms == 0 ? "-" : "I".repeat(ms)) + ": " + u + " / " + MASTERY_AT[ms] + " casts";
        g.drawString(font, font.plainSubstrByWidth(mline, rw), rx, ly + 22, ms >= 3 ? col : 0xFFB0A8C0, false);
        List<FormattedCharSequence> lines = font.split(Component.literal(ART_DESC[t][show]), rw);
        for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), rx, ly + 34 + i * 10, TEXT, false);
        // the wheel's four slots
        int sy = ly + 66;
        g.drawString(font, "G wheel: pick a slot, then an art", rx, sy, DIM, false);
        int sw = (rw - 4) / 2;
        for (int s = 0; s < 4; s++) {
            int bx = rx + (s % 2) * (sw + 4), by = sy + 12 + (s / 2) * 18;
            boolean sel = s == slot, hov = mx >= bx && mx < bx + sw && my >= by && my < by + 15;
            int art = TwinsClient.load(t, s);
            g.fill(bx, by, bx + sw, by + 15, sel ? 0xE0403458 : hov ? 0xC0302840 : 0xB0181424);
            g.fill(bx, by + 14, bx + sw, by + 15, sel ? col : 0x60FFFFFF & col);
            String nm = (s + 1) + "  " + (art >= 0 ? ARTS[t][art] : "empty");
            g.drawString(font, font.plainSubstrByWidth(nm, sw - 6), bx + 4, by + 4, art >= 0 ? TEXT : DIM, false);
            hits.add(new int[]{bx, by, bx + sw, by + 15, 2, s});
        }
        g.drawString(font, "Right-click a slot to empty it.", rx, sy + 50, 0xFF6E6A7A, false);
        boolean ult = lv >= ULT_LV;
        g.drawString(font, font.plainSubstrByWidth("Ultimate: " + ULT_NAME[t] + (ult ? " (its page)" : " at level " + ULT_LV), rw), rx, sy + 62, ult ? col : DIM, false);
        g.drawString(font, font.plainSubstrByWidth(lv >= ECHO_LV ? "Echo: fights beside you while resting" : "Echo: at level " + ECHO_LV, rw), rx, sy + 74, lv >= ECHO_LV ? TEXT : DIM, false);
    }

    // ------------------------------------------------------------------ the bond (left) and the titles (right)
    private void bond(GuiGraphics g, int x0, int y, int W, int mid, int mx, int my) {
        int ls = TwinsClient.lv(SOL), ln = TwinsClient.lv(NYX), lx = x0 + 10, lr = mid - 8;
        g.drawString(font, "THE BOND", lx, y, 0xFFF0E0FF, true);
        String[] names = {"Twin Strike", "Mirror Swap", "Eclipse Union"};
        String[] what = {"Both masks cut at once (a page).", "Sneak as you swap: step back.", "20 s fused, all 16 arts (a page)."};
        int[] at = {BOND_STRIKE, BOND_MIRROR, BOND_ECLIPSE};
        for (int i = 0; i < 3; i++) {
            int ry = y + 14 + i * 32;
            boolean open = ls >= at[i] && ln >= at[i] && (i < 2 || TwinsClient.v[V_TRIAL] == 1);
            g.fill(lx - 2, ry - 2, lr, ry + 28, open ? 0x60403050 : 0x40000000);
            g.drawString(font, names[i], lx, ry, open ? 0xFFFFFFFF : DIM, false);
            String st = open ? "OPEN" : "LOCKED";
            g.drawString(font, st, lr - 4 - font.width(st), ry, open ? 0xFFA8F0A0 : 0xFF8A6A6A, false);
            g.drawString(font, "Both level " + at[i] + (i == 2 ? " + the trial" : "") + "  (" + ls + " / " + ln + ")", lx, ry + 10, DIM, false);
            g.drawString(font, what[i], lx, ry + 19, TEXT, false);
        }
        // the trial
        int ty = y + 14 + 3 * 32 + 2;
        g.drawString(font, "THE TWIN WARDENS", lx, ty, 0xFFF0E0FF, true);
        boolean ready = ls >= BOND_ECLIPSE && ln >= BOND_ECLIPSE, won = TwinsClient.v[V_TRIAL] == 1;
        int left = TwinsClient.v[V_TRIAL_LEFT];
        String st = left > 0 ? String.format("Under way: %d:%02d left", left / 60, left % 60) : won ? "Won - Eclipse Union is open." : ready ? "Beat both wardens in 4 minutes." : "Both twins must reach level " + BOND_ECLIPSE + ".";
        g.drawString(font, st, lx, ty + 11, won ? 0xFFA8F0A0 : TEXT, false);
        button(g, lx, ty + 24, 110, won ? "FIGHT THEM AGAIN" : "BEGIN THE TRIAL", ready && left == 0, 0xFFF0E0FF, mx, my, 4, 0);
        // the titles
        int rx = mid + 8, rr = x0 + W - 10;
        g.drawString(font, "TITLES", rx, y, SILVER, true);
        int titles = TwinsClient.v[V_TITLES], worn = TwinsClient.v[V_TITLE];
        for (int i = 0; i < 4; i++) {
            int ry = y + 14 + i * 32;
            boolean has = (titles & (1 << i)) != 0, on = worn == i;
            g.fill(rx - 2, ry - 2, rr, ry + 28, on ? 0x70403A60 : 0x40000000);
            g.drawString(font, TITLES[i], rx, ry, has ? (on ? GOLD : 0xFFFFFFFF) : DIM, false);
            if (has) button(g, rr - 62, ry - 1, 58, on ? "TAKE OFF" : "WEAR", true, on ? GOLD : SILVER, mx, my, 5, on ? 99 : i);
            else g.drawString(font, "locked", rr - 4 - font.width("locked"), ry, 0xFF6E6A7A, false);
            g.drawString(font, TITLE_HOW[i], rx, ry + 10, DIM, false);
            g.drawString(font, TITLE_GIVES[i], rx, ry + 19, TEXT, false);
        }
    }

    // ------------------------------------------------------------------ the masks as they are, their stones and sockets
    private void masks(GuiGraphics g, int x0, int y, int W, int mid, int mx, int my) {
        for (int t = 0; t < 2; t++) {
            int cx0 = t == SOL ? x0 + 10 : mid + 8, cx1 = t == SOL ? mid - 8 : x0 + W - 10, cx = (cx0 + cx1) / 2;
            int col = t == SOL ? GOLD : SILVER;
            String stones = (t == SOL ? "Sunstones: " + TwinsClient.v[V_SUN] : "Moonstones: " + TwinsClient.v[V_MOON]);
            g.drawCenteredString(font, stones, cx, y, col);
            // the mask itself
            g.pose().pushPose();
            float bob = Mth.sin((ticks + t * 20) * 0.08F) * 1.5F;
            g.pose().translate(cx - 28, y + 12 + bob, 0);
            g.pose().scale(3.5F, 3.5F, 1F);
            g.renderItem(TwinsMasks.stack(TwinsClient.maskForm(t)), 0, 0);
            g.pose().popPose();
            int sock = TwinsClient.v[V_SOCK + t];
            g.drawCenteredString(font, TWIN[t] + " - stage " + com.ironspellgrimoire.server.TwinsFx.stage(sock) + " of 3", cx, y + 70, TEXT);
            int have = t == SOL ? TwinsClient.v[V_SUN] : TwinsClient.v[V_MOON];
            for (int s = 0; s < 3; s++) {
                int ry = y + 84 + s * 38;
                boolean set = TwinsClient.socket(t, s), open = TwinsClient.lv(t) >= SOCKET_LV[s];
                int cur = set ? TwinsClient.cut(t, s) : -1, show = cur;
                g.fill(cx0 - 2, ry - 2, cx1, ry + 34, set ? 0x70403050 : 0x40000000);
                g.drawString(font, SOCKET_PLACE[t][s], cx0, ry, set ? col : open ? 0xFFFFFFFF : DIM, false);
                String st = !open ? "Lv " + SOCKET_LV[s] : set ? "re-cut: 1 stone" : "1 stone";
                g.drawString(font, st, cx1 - 4 - font.width(st), ry, !open ? 0xFF6E6A7A : DIM, false);
                int bw = (cx1 - cx0 - 8) / 3;
                for (int c = 0; c < 3; c++) {   // the three cuts: the set one lit, the others to choose
                    int bx = cx0 + c * (bw + 3);
                    boolean on = c == cur, can = open && !on && have > 0;
                    if (open && mx >= bx && mx < bx + bw && my >= ry + 10 && my < ry + 23) show = c;
                    if (on) {
                        g.fill(bx, ry + 10, bx + bw, ry + 23, 0xE0403458);
                        g.fill(bx, ry + 22, bx + bw, ry + 23, col);
                        g.drawCenteredString(font, font.plainSubstrByWidth(SOCKET_NAME[t][s][c], bw - 4), bx + bw / 2, ry + 13, col);
                    } else button(g, bx, ry + 10, bw, font.plainSubstrByWidth(SOCKET_NAME[t][s][c], bw - 6), can, col, mx, my, 3, t * 100 + s * 10 + c);
                }
                String desc = show >= 0 ? SOCKET_DESC[t][s][show] : open ? "Choose a cut." : "Opens at " + TWIN[t] + " level " + SOCKET_LV[s] + ".";
                g.drawString(font, font.plainSubstrByWidth(desc, cx1 - cx0 - 4), cx0, ry + 25, open ? TEXT : 0xFF6E6A7A, false);
            }
        }
    }

    // ------------------------------------------------------------------ the guide
    static final int[] GOLD_LABEL = {GOLD, SILVER};
    static final String[][] GUIDE = {
            {"Swap", "Tap G, or let go in the middle of the wheel. 3 s between swaps."},
            {"Arts", "Hold G and point at one of the worn twin's four arts."},
            {"Resonance", "The resting twin charges as you fight, and bursts out as it swaps in."},
            {"Rush", "After a swap: Sol hits harder, Nyx runs faster (4 s)."},
            {"Levels", "Each twin levels on its own from kills and its arts' hits, to 50."},
            {"Day & night", "Sol learns 25% faster by day, Nyx by night."},
            {"Balance", "A twin 10 or more levels ahead of the other learns at half speed."},
            {"Stones", "Sunstones drop by day, Moonstones by night - bosses always drop one."},
            {"Sockets", "Each mask opens a socket at levels 5, 15 and 30; each is a perk."},
            {"Bond", "Both 10: Twin Strike. Both 25: Mirror Swap. Both 40: the trial."},
            {"Eclipse", "Beat both wardens, then fuse for 20 s: all 16 arts, no cooldowns."},
            {"Mastery", "Every art grows with use: +15% at 30 casts, -20% cooldown at 100, Perfected at 250."},
            {"Cuts", "Each socket takes one of three cuts; re-cut it any time for another stone."},
            {"Echo", "From level 15, the resting twin leaves your hip in a fight and shoots beside you."},
            {"Ultimates", "Level 40: Sol's High Noon and Nyx's Eventide, each its own page (wear that twin)."},
            {"Titles", "Four to earn; wear one on the Bond tab."}};

    private void guide(GuiGraphics g, int x0, int y, int W) {
        int lw = 70, vx = x0 + 10 + lw, vw = W - 20 - lw;
        int row = 0;
        for (String[] e : GUIDE) {
            List<FormattedCharSequence> lines = font.split(Component.literal(e[1]), vw);
            if ((row + lines.size()) * 10 > H() - 44) break;
            g.drawString(font, e[0], x0 + 10, y + row * 10, GOLD_LABEL[(int) java.util.Arrays.asList(GUIDE).indexOf(e) % 2], false);
            for (FormattedCharSequence l : lines) { g.drawString(font, l, vx, y + row * 10, TEXT, false); row++; }
        }
    }

    // ------------------------------------------------------------------ clicks
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int[] h : new ArrayList<>(hits)) {
            if (mx < h[0] || mx >= h[2] || my < h[1] || my >= h[3]) continue;
            switch (h[4]) {
                case 0 -> tab = h[5];
                case 1 -> { if (button == 0) TwinsClient.send(3, (h[5] / 10) * 100 + slot * 10 + h[5] % 10); }
                case 2 -> { if (button == 1) TwinsClient.send(3, tab * 100 + h[5] * 10 + 9); else slot = h[5]; }
                case 3 -> TwinsClient.send(4, h[5]);
                case 4 -> TwinsClient.send(6, 0);
                case 5 -> TwinsClient.send(5, h[5]);
                default -> {}
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) { tab = (tab + 1) % 5; return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override public void onClose() { forceHover = -1; super.onClose(); }
}
