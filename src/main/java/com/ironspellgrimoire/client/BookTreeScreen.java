package com.ironspellgrimoire.client;

import com.ironspellgrimoire.server.BookTree;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * The exclusive tree of the Vector, Quiet Fuse or Foreseen grimoire (server/BookTree), each drawn its own way:
 *   The Vector Field  white and cyan on slate: a field of small arrows that turn toward the pointer; nodes are diamonds,
 *                     the paths between them arrows.
 *   The Fuse Board    rose on charcoal plum: bubbles drift up behind; nodes are round charges with a fuse, lit when
 *                     learned, and a spark runs along every learned path.
 *   The Sightlines    ice-white on deep navy: a star chart turning slowly behind; nodes are eyes - shut, half-open
 *                     when you can learn them, open when learned; the paths are dotted.
 * Point at a node to read it, click to learn it. The header has the growth level, its bar and the points to spend; the
 * panel has the node, and at its foot Unlearn All (free, once every five minutes - click twice).
 */
public class BookTreeScreen extends Screen {
    /** For the screenshots: the node shown as pointed at (null: follow the mouse). */
    public static String forceHover;
    private int ticks;
    private String hover, sel;
    private boolean confirm;
    private int x0, y0, W, H, bx0, by0, bw, bh, cellW, cellH;

    public BookTreeScreen() { super(Component.literal("Grimoire Tree")); }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() { ticks++; if (BookTreeClient.book < 0) onClose(); }

    static ResourceLocation icon(String key) { return new ResourceLocation("iron_spell_grimoire", "textures/gui/spell_icons/" + key + ".png"); }

    private void layout() {
        W = Math.min(452, width - 12); H = Math.min(262, height - 10);
        x0 = (width - W) / 2; y0 = (height - H) / 2;
        bx0 = x0 + 8; by0 = y0 + 34; bw = W - 150 - 16; bh = H - 34 - 10;
        cellW = bw / 9; cellH = bh / 7;
    }
    private double nx(BookTree.Node n) { return bx0 + cellW * (n.x() + 0.5); }
    private double ny(BookTree.Node n) { return by0 + cellH * (n.y() + 0.5); }
    private double rad(BookTree.Node n) { return n.kind() == BookTree.ROOT ? 12 : n.kind() == BookTree.KEY ? 11 : 9; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int b = BookTreeClient.book;
        if (b < 0) return;
        layout();
        int[] th = BookTreeClient.THEME[b];
        int BG = th[0], PANEL = th[1], LINE = th[2], ACC = th[3], BRIGHT = th[4], DIM = th[5];
        float t = (ticks + pt) / 20f;
        List<BookTree.Node> nodes = BookTree.TREES.get(b);
        hover = null;
        for (BookTree.Node n : nodes) {
            double dx = mx - nx(n), dy = my - ny(n);
            if (dx * dx + dy * dy <= (rad(n) + 3) * (rad(n) + 3)) hover = n.id();
        }
        if (forceHover != null) hover = forceHover;
        String show = hover != null ? hover : sel;

        g.fill(0, 0, width, height, 0x88000000);
        Shapes.begin(g);
        Shapes.rect(x0, y0, x0 + W, y0 + H, BG);
        background(b, t, mx, my, ACC, BRIGHT);
        // the frame
        Shapes.rect(x0, y0, x0 + W, y0 + 1.5, ACC); Shapes.rect(x0, y0 + H - 1.5, x0 + W, y0 + H, ACC);
        Shapes.rect(x0, y0, x0 + 1.5, y0 + H, ACC); Shapes.rect(x0 + W - 1.5, y0, x0 + W, y0 + H, ACC);
        Shapes.rect(x0 + W - 150, y0 + 4, x0 + W - 4, y0 + H - 4, PANEL);
        Shapes.rect(x0 + 4, y0 + 4, x0 + W - 154, y0 + 30, PANEL);
        // the growth bar
        float[] nx = BookTree.toNext(BookTreeClient.xp);
        double barX = x0 + 120, barW = W - 154 - 126;
        Shapes.rect(barX, y0 + 20, barX + barW, y0 + 24, Shapes.alpha(LINE, 1f));
        Shapes.rect(barX, y0 + 20, barX + barW * Math.min(1, nx[0] / Math.max(1, nx[1])), y0 + 24, ACC);
        // the paths
        for (BookTree.Node n : nodes)
            for (String q : n.req()) {
                BookTree.Node p = BookTree.node(b, q);
                if (p == null) continue;
                boolean lit = BookTreeClient.rank(n.id()) > 0 && BookTreeClient.rank(p.id()) > 0;
                path(b, nx(p), ny(p), nx(n), ny(n), rad(p), rad(n), lit, t, lit ? ACC : LINE, BRIGHT);
            }
        // the keystone pairs: a bar between the two sides
        for (BookTree.Node n : nodes)
            if (n.kind() == BookTree.KEY && n.excl() != null && n.id().compareTo(n.excl()) < 0) {
                BookTree.Node o = BookTree.node(b, n.excl());
                Shapes.dashed(nx(n) + rad(n) + 2, ny(n), nx(o) - rad(o) - 2, ny(o), 1, 3, Shapes.alpha(DIM, 0.8f));
            }
        // the nodes
        for (BookTree.Node n : nodes) node(b, n, t, n.id().equals(show), BG, LINE, ACC, BRIGHT, DIM);
        Shapes.end();

        // icons and ranks
        for (BookTree.Node n : nodes) {
            int r = BookTreeClient.rank(n.id());
            if (!n.icon().isEmpty() && (n.kind() == BookTree.SKILL || n.kind() == BookTree.WHEEL || n.kind() == BookTree.ROOT)) {
                int s = n.kind() == BookTree.ROOT ? 14 : 10;
                if (b == BookTree.FORESEEN && r == 0) continue;   // a shut eye shows nothing
                g.setColor(1f, 1f, 1f, r > 0 ? 1f : 0.45f);
                g.blit(icon(n.icon()), (int) nx(n) - s / 2, (int) ny(n) - s / 2, s, s, 0, 0, 64, 64, 64, 64);
                g.setColor(1f, 1f, 1f, 1f);
            }
            if (n.ranks() > 1) g.drawCenteredString(font, r + "/" + n.ranks(), (int) nx(n), (int) (ny(n) + rad(n) + 1), r > 0 ? BRIGHT : DIM);
        }

        // the header
        g.drawString(font, BookTree.TREE[b], x0 + 10, y0 + 8, BRIGHT);
        g.drawString(font, "Level " + BookTreeClient.level() + (BookTreeClient.level() >= BookTree.MAX_LEVEL ? " (max)" : ""), x0 + 10, y0 + 19, ACC);
        g.drawString(font, BookTree.GROWTH[b] + ": " + (int) BookTreeClient.xp, (int) barX, y0 + 8, DIM);
        String pts = BookTreeClient.free() + " / " + BookTreeClient.points + " points";
        g.drawString(font, pts, x0 + W - 158 - font.width(pts), y0 + 8, BookTreeClient.free() > 0 ? BRIGHT : DIM);

        // the panel
        int px = x0 + W - 144, py = y0 + 10, pw = 136;
        BookTree.Node n = show == null ? null : BookTree.node(b, show);
        if (n == null) {
            py = para(g, px, py, pw, "Point at a node to read it; click to learn it.", DIM);
            py = para(g, px, py + 6, pw, "Points come from your " + BookTree.TREE[b] + " level (" + BookTree.GROWTH[b].toLowerCase() + ") and two for each mastery level.", DIM);
            py = para(g, px, py + 6, pw, "Keystones come in pairs: take one side, never both.", DIM);
            para(g, px, py + 6, pw, "G opens the wheel; its centre opens this tree.", DIM);
        } else {
            int r = BookTreeClient.rank(n.id());
            g.drawString(font, n.name(), px, py, BRIGHT); py += 11;
            String kind = switch (n.kind()) { case BookTree.STAT -> "Stat"; case BookTree.PASSIVE -> "Passive"; case BookTree.SKILL -> "Page upgrade";
                case BookTree.WHEEL -> "G-wheel ability"; case BookTree.KEY -> "Keystone"; default -> "The root"; };
            g.drawString(font, kind + (n.ranks() > 1 ? "  -  rank " + r + "/" + n.ranks() : "") + (n.kind() == BookTree.ROOT ? "" : "  -  " + n.cost() + " pt" + (n.cost() > 1 ? "s" : "")), px, py, ACC); py += 13;
            py = para(g, px, py, pw, n.desc(), BRIGHT);
            if (n.excl() != null) py = para(g, px, py + 4, pw, "Shuts: " + BookTree.node(b, n.excl()).name(), DIM);
            String why = BookTreeClient.blocked(n);
            para(g, px, py + 6, pw, why == null ? "Click to learn." : why, why == null ? ACC : DIM);
        }
        // Unlearn All
        int ux = px, uy = y0 + H - 22;
        boolean onU = mx >= ux && mx <= ux + pw && my >= uy && my <= uy + 14;
        g.fill(ux, uy, ux + pw, uy + 14, onU ? Shapes.alpha(ACC, 0.35f) : Shapes.alpha(LINE, 0.6f));
        String ul = BookTreeClient.respec > 0 ? "Unlearn All in " + (BookTreeClient.respec + 19) / 20 + " s" : confirm ? "Click again: unlearn all" : "Unlearn All (free)";
        g.drawCenteredString(font, ul, ux + pw / 2, uy + 3, BookTreeClient.respec > 0 ? DIM : BRIGHT);
    }

    private int para(GuiGraphics g, int x, int y, int w, String s, int col) {
        for (FormattedCharSequence l : font.split(Component.literal(s), w)) { g.drawString(font, l, x, y, col); y += 10; }
        return y;
    }

    // ------------------------------------------------------------------ each book's look
    private void background(int b, float t, int mx, int my, int acc, int bright) {
        if (b == BookTree.VECTOR) {
            for (int x = bx0 + 6; x < bx0 + bw; x += 18)
                for (int y = by0 + 4; y < by0 + bh; y += 18) {
                    double a = Math.atan2(my - y, mx - x) + Math.sin(t * 1.3 + x * 0.04 + y * 0.03) * 0.35;
                    Shapes.line(x - Math.cos(a) * 4, y - Math.sin(a) * 4, x + Math.cos(a) * 3, y + Math.sin(a) * 3, 0.8, Shapes.alpha(acc, 0.18f));
                    Shapes.arrowHead(x + Math.cos(a) * 4, y + Math.sin(a) * 4, a, 2.2, Shapes.alpha(acc, 0.22f));
                }
        } else if (b == BookTree.FUSE) {
            Random r = new Random(5);
            for (int i = 0; i < 26; i++) {
                double bx = bx0 + r.nextDouble() * bw, sp = 6 + r.nextDouble() * 10, rr = 2 + r.nextDouble() * 5;
                double by = by0 + bh - ((r.nextDouble() * bh + t * sp) % bh);
                bx += Math.sin(t + i) * 3;
                Shapes.ring(bx, by, rr - 0.8, rr, Shapes.alpha(acc, 0.25f));
                Shapes.disc(bx - rr * 0.35, by - rr * 0.35, Math.max(0.6, rr * 0.18), Shapes.alpha(bright, 0.3f));
            }
        } else {
            Random r = new Random(9);
            double cx = bx0 + bw / 2.0, cy = by0 + bh / 2.0, rot = t * 0.03;
            double[][] st = new double[40][2];
            for (int i = 0; i < st.length; i++) {
                double a = r.nextDouble() * Math.PI * 2 + rot, d = Math.sqrt(r.nextDouble()) * Math.max(bw, bh) * 0.6;
                st[i][0] = cx + Math.cos(a) * d; st[i][1] = cy + Math.sin(a) * d * 0.8;
            }
            for (int i = 0; i + 1 < st.length; i += 3) {
                if (inBoard(st[i]) && inBoard(st[i + 1]) && Math.hypot(st[i][0] - st[i + 1][0], st[i][1] - st[i + 1][1]) < 70)
                    Shapes.line(st[i][0], st[i][1], st[i + 1][0], st[i + 1][1], 0.6, Shapes.alpha(acc, 0.18f));
            }
            for (int i = 0; i < st.length; i++)
                if (inBoard(st[i])) Shapes.disc(st[i][0], st[i][1], 0.9 + (i % 3) * 0.3, Shapes.alpha(bright, 0.35f + 0.3f * (float) Math.sin(t * 2 + i)));
        }
    }
    private boolean inBoard(double[] p) { return p[0] > bx0 && p[0] < bx0 + bw && p[1] > by0 && p[1] < by0 + bh; }

    private void path(int b, double x0, double y0, double x1, double y1, double r0, double r1, boolean lit, float t, int col, int bright) {
        double dx = x1 - x0, dy = y1 - y0, l = Math.sqrt(dx * dx + dy * dy);
        if (l < 1) return;
        double ux = dx / l, uy = dy / l;
        double ax = x0 + ux * (r0 + 1), ay = y0 + uy * (r0 + 1), bx = x1 - ux * (r1 + 1), by = y1 - uy * (r1 + 1);
        if (b == BookTree.VECTOR) {
            Shapes.line(ax, ay, bx, by, lit ? 1.6 : 1, col);
            Shapes.arrowHead((ax + bx) / 2 + ux * 2, (ay + by) / 2 + uy * 2, Math.atan2(uy, ux), lit ? 3.5 : 2.6, col);
        } else if (b == BookTree.FUSE) {
            Shapes.line(ax, ay, bx, by, lit ? 1.8 : 1.2, col);
            if (lit) {
                double f = (t * 0.6 + (x0 + y1) * 0.01) % 1.0;
                Shapes.disc(ax + (bx - ax) * f, ay + (by - ay) * f, 1.8, bright);
            }
        } else Shapes.dashed(ax, ay, bx, by, lit ? 1.4 : 1, 2.5, col);
    }

    private void node(int b, BookTree.Node n, float t, boolean hot, int bg, int line, int acc, int bright, int dim) {
        double x = nx(n), y = ny(n), r = rad(n);
        int rank = BookTreeClient.rank(n.id());
        boolean learned = rank > 0, can = BookTreeClient.blocked(n) == null;
        float pulse = can ? 0.55f + 0.45f * (float) Math.sin(t * 5) : 1f;
        int rim = learned ? acc : can ? Shapes.alpha(bright, pulse) : dim;
        if (hot) Shapes.ring(x, y, r + 2.5, r + 4, Shapes.alpha(bright, 0.8f));
        if (b == BookTree.VECTOR) {
            Shapes.diamond(x, y, r + 1, rim);
            Shapes.diamond(x, y, r - 0.8, learned ? Shapes.alpha(acc, 0.45f) : bg);
            if (n.kind() == BookTree.KEY) Shapes.diamondRing(x, y, r + 4, 1, rim);
            if (n.kind() == BookTree.STAT || n.kind() == BookTree.PASSIVE) {
                Shapes.arrowHead(x + 1, y, 0, n.kind() == BookTree.STAT ? 3.2 : 4, learned ? bright : dim);
                if (n.kind() == BookTree.PASSIVE) Shapes.line(x - 4, y, x, y, 1.2, learned ? bright : dim);
            }
        } else if (b == BookTree.FUSE) {
            Shapes.line(x + r * 0.6, y - r * 0.6, x + r * 1.05, y - r * 1.15, 1.5, rim);
            Shapes.disc(x, y, r + 0.8, rim);
            Shapes.disc(x, y, r - 0.6, learned ? Shapes.alpha(acc, 0.4f) : bg);
            if (learned) Shapes.disc(x + r * 1.1, y - r * 1.2, 1.6 + 0.6 * Math.sin(t * 9 + x), bright);
            if (n.kind() == BookTree.KEY) Shapes.ring(x, y, r + 3.5, r + 4.5, rim);
            if (n.kind() == BookTree.STAT) { Shapes.rect(x - 3, y - 0.6, x + 3, y + 0.6, learned ? bright : dim); Shapes.rect(x - 0.6, y - 3, x + 0.6, y + 3, learned ? bright : dim); }
            if (n.kind() == BookTree.PASSIVE) Shapes.ring(x, y, 2, 3, learned ? bright : dim);
        } else {
            double open = learned ? 1 : can ? 0.35 + 0.15 * Math.sin(t * 3) : 0;
            Shapes.eye(x, y, r + 2, Math.max(open, 0.08), learned ? Shapes.alpha(acc, 0.35f) : bg, rim);
            if (learned && n.kind() != BookTree.SKILL && n.kind() != BookTree.WHEEL && n.kind() != BookTree.ROOT) {
                Shapes.disc(x, y, 3.2, acc); Shapes.disc(x, y, 1.4, bg);
            }
            if (n.kind() == BookTree.KEY) { Shapes.arc(x, y, r + 3, r + 4, Math.PI * 1.1, Math.PI * 1.9, rim); Shapes.arc(x, y, r + 3, r + 4, Math.PI * 0.1, Math.PI * 0.9, rim); }
        }
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        layout();
        int px = x0 + W - 144, uy = y0 + H - 22;
        if (button == 0 && mx >= px && mx <= px + 136 && my >= uy && my <= uy + 14) {
            if (BookTreeClient.respec > 0) return true;
            if (confirm) { BookTreeClient.send(2, 0, null); confirm = false; } else confirm = true;
            return true;
        }
        confirm = false;
        if (button == 0 && hover != null) {
            sel = hover;
            BookTree.Node n = BookTree.node(BookTreeClient.book, hover);
            if (n != null && BookTreeClient.blocked(n) == null) BookTreeClient.send(1, 0, n.id());
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}
