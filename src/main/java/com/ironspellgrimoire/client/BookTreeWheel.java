package com.ironspellgrimoire.client;

import com.ironspellgrimoire.server.BookTree;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The G wheel of the Vector, Quiet Fuse and Foreseen grimoires: four abilities round the edge - the first always
 * there, the other three opened by their wheel nodes in the tree - and the tree itself in the middle. Hold G, point,
 * let go (or click). Each book draws it its own way: Vector's slices end in arrowheads round a turning compass, the
 * Quiet Fuse's centre is a lit charge, the Foreseen's an eye that follows the pointer.
 */
public class BookTreeWheel extends Screen {
    /** For the screenshots: the slice shown as pointed at (-1: follow the mouse, -2: the middle). */
    public static int forceHover = -1;
    private int hover = -1, ticks;
    private boolean centre;
    private float open;
    private long lastNs;

    public BookTreeWheel() { super(Component.literal("Grimoire Wheel")); }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() { ticks++; if (BookTreeClient.book < 0) onClose(); }

    /** What the middle says under the book's name: its stance, mode or state (from the server's extra numbers). */
    static String state(int b) {
        int[] x = BookTreeClient.extra;
        if (b == BookTree.VECTOR) return x.length > 0 ? new String[]{"Reflect", "Return", "Flow"}[Math.max(0, Math.min(2, x[0]))] + " stance" : "";
        if (b == BookTree.FUSE) return x.length > 2 ? x[1] + "/" + x[2] + " armed  -  " + new String[]{"on Detonate", "3 s timer", "proximity"}[Math.max(0, Math.min(2, x[0]))] : "";
        if (b == BookTree.BOUND) {
            if (x.length < 8) return "";
            String mode = new String[]{"Balanced", "Restrain", "Release"}[Math.max(0, Math.min(2, x[5]))];
            return x[1] > 0 ? "TAKEN OVER" : x[3] > 0 ? "Awake - " + (x[3] + 19) / 20 + " s" : "Possession " + x[0] + "%  -  " + mode;
        }
        return x.length > 0 ? String.format("%.2f s warning", x[0] / 20f) : "";
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int b = BookTreeClient.book;
        if (b < 0) return;
        long ns = System.nanoTime();
        float dt = lastNs == 0 ? 0 : Math.min(0.1F, (ns - lastNs) / 1e9F);
        lastNs = ns;
        open = Math.min(1F, open + dt * 7F);
        float e = 1 - (1 - open) * (1 - open), t = (ticks + pt) / 20f;
        int[] th = BookTreeClient.THEME[b];
        int BG = th[0], LINE = th[2], ACC = th[3], BRIGHT = th[4], DIM = th[5];
        int cx = width / 2, cy = height / 2;
        double R = Math.min(108, height / 2.0 - 40), r0 = 30 * e, r1 = R * e;
        double dx = mx - cx, dy = my - cy, dist = Math.sqrt(dx * dx + dy * dy);
        double ang = Math.atan2(dx, -dy);
        if (ang < 0) ang += Math.PI * 2;
        centre = dist <= 28;
        hover = dist > 28 ? (int) Math.floor((ang + Math.PI / 4) / (Math.PI / 2)) % 4 : -1;
        if (forceHover >= 0) { hover = forceHover; centre = false; e = 1; r0 = 30; r1 = R; }
        if (forceHover == -2) { hover = -1; centre = true; e = 1; r0 = 30; r1 = R; }

        Shapes.begin(g);
        for (int i = 0; i < 4; i++) {
            double mid = i * Math.PI / 2, a0 = mid - Math.PI / 4 + 0.04, a1 = mid + Math.PI / 4 - 0.04;
            boolean ok = BookTreeClient.wheelOpen(i), h = i == hover;
            double out = r1 + (h ? 8 : 0);
            int fill = h && ok ? Shapes.alpha(ACC, 0.55f) : Shapes.alpha(BG, 0.92f);
            // screen angles: 0 is up, clockwise; Shapes.arc wants maths angles
            Shapes.arc(cx, cy, r0, out, a0 - Math.PI / 2, a1 - Math.PI / 2, fill);
            Shapes.arc(cx, cy, out - 2, out, a0 - Math.PI / 2, a1 - Math.PI / 2, ok ? ACC : LINE);
            if (b == BookTree.VECTOR && ok) {   // an arrowhead on the rim
                double sx = cx + Math.sin(mid) * (out + 4), sy = cy - Math.cos(mid) * (out + 4);
                Shapes.arrowHead(sx, sy, mid - Math.PI / 2, 5, ACC);
            }
            if (BookTreeClient.cd.length > i && BookTreeClient.cd[i] > 0) {   // the cooldown as a shadow sweeping back
                float f = Math.min(1f, BookTreeClient.cd[i] / (float) BookTree.WHEEL_COOLDOWN[b][i]);
                Shapes.arc(cx, cy, r0, out - 2, a0 - Math.PI / 2, a0 - Math.PI / 2 + (a1 - a0) * f, 0x88000000);
            }
        }
        // the middle
        int mc = centre ? ACC : LINE;
        Shapes.disc(cx, cy, 27, Shapes.alpha(BG, 0.96f));
        Shapes.ring(cx, cy, 26, 28, mc);
        if (b == BookTree.VECTOR) {
            for (int k = 0; k < 8; k++) {
                double a = t * 0.8 + k * Math.PI / 4;
                Shapes.arrowHead(cx + Math.cos(a) * 21, cy + Math.sin(a) * 21, a + Math.PI / 2, 2.5, Shapes.alpha(ACC, 0.6f));
            }
        } else if (b == BookTree.BOUND) {   // the demon's eye, Possession filling it
            float poss = BookTreeClient.extra.length > 0 ? Math.min(1f, BookTreeClient.extra[0] / 100f) : 0f;
            Shapes.eye(cx, cy - 10, 14, 0.3 + 0.6 * poss, 0xEE140608, ACC);
            Shapes.disc(cx, cy - 10, 2.5 + 2.5 * poss, ACC);
            Shapes.disc(cx, cy - 10, 1.1, BG);
        } else if (b == BookTree.FUSE) {
            Shapes.line(cx + 14, cy - 14, cx + 20, cy - 21, 2, mc);
            Shapes.disc(cx + 21, cy - 22, 2 + Math.sin(t * 11), BRIGHT);
        } else {
            double ex = Math.max(-4, Math.min(4, dx / 30)), ey = Math.max(-2, Math.min(2, dy / 40));
            Shapes.eye(cx, cy - 10, 14, 0.8, Shapes.alpha(ACC, 0.25f), ACC);
            Shapes.disc(cx + ex, cy - 10 + ey, 3.4, ACC);
            Shapes.disc(cx + ex, cy - 10 + ey, 1.4, BG);
        }
        Shapes.end();

        for (int i = 0; i < 4; i++) {
            double mid = i * Math.PI / 2, rr = r0 + (r1 - r0) * 0.62 + (i == hover ? 4 : 0);
            int tx = (int) (cx + Math.sin(mid) * rr), ty = (int) (cy - Math.cos(mid) * rr);
            boolean ok = BookTreeClient.wheelOpen(i);
            g.setColor(1f, 1f, 1f, ok ? 1f : 0.35f);
            g.blit(BookTreeScreen.icon(BookTree.WHEEL_ICONS[b][i]), tx - 8, ty - 14, 16, 16, 0, 0, 64, 64, 64, 64);
            g.setColor(1f, 1f, 1f, 1f);
            String name = BookTree.WHEEL_NAMES[b][i];
            g.drawCenteredString(font, name, tx, ty + 4, !ok ? DIM : i == hover ? 0xFFFFFFFF : BRIGHT);
            if (ok && BookTreeClient.cd.length > i && BookTreeClient.cd[i] > 0) g.drawCenteredString(font, (BookTreeClient.cd[i] + 19) / 20 + "s", tx, ty + 14, DIM);
        }
        boolean eyeMid = b == BookTree.FORESEEN || b == BookTree.BOUND;
        g.drawCenteredString(font, eyeMid ? "" : "Tree", cx, cy - 4, centre ? 0xFFFFFFFF : BRIGHT);
        if (eyeMid) g.drawCenteredString(font, "Tree", cx, cy + 6, centre ? 0xFFFFFFFF : BRIGHT);
        if (BookTreeClient.free() > 0) g.drawCenteredString(font, "+" + BookTreeClient.free(), cx, cy + (eyeMid ? 16 : 6), ACC);

        int y = cy + (int) (R + 18);
        g.drawCenteredString(font, state(b), cx, cy - (int) R - 22, ACC);
        if (hover >= 0) {
            boolean ok = BookTreeClient.wheelOpen(hover);
            g.drawCenteredString(font, BookTree.WHEEL_NAMES[b][hover], cx, y, 0xFFFFFFFF);
            String d = ok ? BookTree.WHEEL_DESC[b][hover] : "Learn " + BookTree.node(b, BookTree.WHEEL_IDS[b][hover]).name() + " in the tree to use this.";
            g.drawCenteredString(font, d, cx, y + 11, DIM);
        } else if (centre) {
            g.drawCenteredString(font, BookTree.TREE[b], cx, y, 0xFFFFFFFF);
            g.drawCenteredString(font, "Let go here to open the tree" + (BookTreeClient.free() > 0 ? " - " + BookTreeClient.free() + " points to spend." : "."), cx, y + 11, DIM);
        }
    }

    private void pick() {
        int h = hover;
        boolean c = centre;
        onClose();
        if (h >= 0 && BookTreeClient.wheelOpen(h)) BookTreeClient.send(3, h, null);
        else if (c || ticks < 4) BookTreeClient.send(0, 0, null);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (ParasiteClient.KEY.matches(key, scan)) { pick(); return true; }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) { pick(); return true; }
        if (button == 1) { onClose(); return true; }
        return super.mouseClicked(mx, my, button);
    }
}
