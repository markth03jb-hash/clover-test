package com.ironspellgrimoire.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import static com.ironspellgrimoire.server.TwinsFx.*;

/**
 * The Masked Twins' G wheel. Tap G (or let go in the middle) to swap masks; hold it and point at one of the worn twin's
 * four arts to cast it. Sol's wheel is warm gold, Nyx's cold silver on night blue. In Eclipse Union it opens out into two
 * rings - all eight of Sol's arts inside, all eight of Nyx's outside - with no cooldowns.
 */
public class TwinsWheel extends Screen {
    private int hover = -1;
    private boolean centre;
    private float open;
    private long lastNs;
    private int ticks;

    public TwinsWheel() { super(Component.literal("Masked Twins")); }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() { ticks++; }

    private static final float[][] GOLD = {{0.20F, 0.13F, 0.04F}, {0.95F, 0.70F, 0.22F}, {1.0F, 0.86F, 0.45F}};   // base, lit, rim
    private static final float[][] SILV = {{0.05F, 0.06F, 0.14F}, {0.55F, 0.66F, 0.95F}, {0.84F, 0.89F, 1.0F}};

    private boolean usable(int twin, int art) {
        if (art < 0 || !TwinsClient.knows(twin, art)) return false;
        return TwinsClient.eclipse() || TwinsClient.cd(twin, art) == 0;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long ns = System.nanoTime();
        float dt = lastNs == 0 ? 0 : Math.min(0.1F, (ns - lastNs) / 1e9F);
        lastNs = ns;
        open = Math.min(1F, open + dt * 7F);
        float e = 1 - (1 - open) * (1 - open);
        boolean ecl = TwinsClient.eclipse();
        int a = TwinsClient.active(), cx = width / 2, cy = height / 2;
        double R = Math.min(ecl ? 104 : 96, height / 2.0 - 36);
        double r0 = 30 * e, rm = (ecl ? R * 0.6 : R) * e, r1 = R * e;
        double dx = mx - cx, dy = my - cy, dist = Math.sqrt(dx * dx + dy * dy);
        double ang = Math.atan2(dx, -dy);
        if (ang < 0) ang += Math.PI * 2;
        centre = dist <= 26;
        if (ecl) {
            if (dist > 26 && dist <= rm) hover = (int) Math.floor((ang + Math.PI / 8) / (Math.PI / 4)) % 8;
            else if (dist > rm) hover = 8 + (int) Math.floor((ang) / (Math.PI / 4)) % 8;
            else hover = -1;
        } else hover = dist > 26 ? (int) Math.floor((ang + Math.PI / 4) / (Math.PI / 2)) % 4 : -1;
        if (forceHover >= 0) { hover = forceHover; centre = false; open = 1F; e = 1F; r0 = 30; rm = ecl ? R * 0.6 : R; r1 = R; }
        if (forceHover == -2) { hover = -1; centre = true; open = 1F; e = 1F; r0 = 30; rm = ecl ? R * 0.6 : R; r1 = R; }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        double gap = 0.03;
        if (ecl) {
            for (int i = 0; i < 16; i++) {
                int twin = i < 8 ? SOL : NYX, art = i % 8;
                float[][] c = twin == SOL ? GOLD : SILV;
                double step = Math.PI / 4, mid = art * step + (twin == NYX ? step / 2 : 0);
                double a0 = mid - step / 2 + gap, a1 = mid + step / 2 - gap;
                double in = twin == SOL ? r0 : rm + 2, out = (twin == SOL ? rm - 2 : r1) + (i == hover ? 6 : 0);
                boolean ok = usable(twin, art), h = i == hover;
                float k = ok ? 1F : 0.4F;
                float[] col = h && ok ? c[1] : c[0];
                ring(bb, m, cx, cy, in, out, a0, a1, col[0] * 0.7F * k, col[1] * 0.7F * k, col[2] * 0.7F * k, 0.92F, col[0] * k, col[1] * k, col[2] * k, 0.92F, 8);
                ring(bb, m, cx, cy, out - 2, out, a0, a1, c[2][0] * k, c[2][1] * k, c[2][2] * k, 0.95F, c[2][0] * k, c[2][1] * k, c[2][2] * k, 0.95F, 8);
            }
        } else {
            float[][] c = a == SOL ? GOLD : SILV;
            double step = Math.PI / 2;
            for (int i = 0; i < 4; i++) {
                double a0 = i * step - step / 2 + gap, a1 = (i + 1) * step - step / 2 - gap;
                int art = TwinsClient.load(a, i);
                boolean h = i == hover, ok = usable(a, art);
                double out = r1 + (h ? 8 : 0);
                float k = ok ? 1F : 0.45F;
                float[] col = h && ok ? c[1] : c[0];
                ring(bb, m, cx, cy, r0, out, a0, a1, col[0] * 0.6F * k, col[1] * 0.6F * k, col[2] * 0.6F * k, 0.92F, col[0] * k, col[1] * k, col[2] * k, 0.92F, 14);
                ring(bb, m, cx, cy, out - 3, out, a0, a1, c[2][0] * k, c[2][1] * k, c[2][2] * k, 0.95F, c[2][0] * k, c[2][1] * k, c[2][2] * k, 0.95F, 14);
                if (a == SOL) for (int b = 1; b <= 3; b++) {   // Sol's rays along the rim
                    double ba = a0 + (a1 - a0) * b / 4.0;
                    tri(bb, m, cx, cy, ba - 0.05, out, ba + 0.05, out, ba, out + (b == 2 ? 10 : 6) * e, c[2][0] * k, c[2][1] * k, c[2][2] * k, 0.9F);
                }
            }
            if (a == NYX) ring(bb, m, cx, cy, r1 + 6 * e, r1 + 10 * e, Math.PI * 1.1, Math.PI * 1.9, 0.84F, 0.89F, 1F, 0.9F, 0.84F, 0.89F, 1F, 0.2F, 24);   // Nyx's crescent
        }
        // the middle: half gold, half silver - where you let go to swap
        float glow = centre ? 1F : 0.55F;
        ring(bb, m, cx, cy, 0, 26, Math.PI, Math.PI * 2, 0.95F * glow, 0.7F * glow, 0.22F * glow, 0.96F, 0.5F * glow, 0.35F * glow, 0.1F * glow, 0.96F, 16);
        ring(bb, m, cx, cy, 0, 26, 0, Math.PI, 0.55F * glow, 0.66F * glow, 0.95F * glow, 0.96F, 0.2F * glow, 0.24F * glow, 0.45F * glow, 0.96F, 16);
        ring(bb, m, cx, cy, 25, 27, 0, Math.PI * 2, 0.04F, 0.04F, 0.08F, 1F, 0.04F, 0.04F, 0.08F, 1F, 32);
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.disableBlend();

        // the labels
        if (ecl) {
            for (int i = 0; i < 16; i++) {
                int twin = i < 8 ? SOL : NYX, art = i % 8;
                double step = Math.PI / 4, mid = art * step + (twin == NYX ? step / 2 : 0);
                double rr = twin == SOL ? (r0 + rm) / 2 : (rm + r1) / 2 + 2;
                int tx = (int) (cx + Math.sin(mid) * rr), ty = (int) (cy - Math.cos(mid) * rr);
                boolean ok = usable(twin, art);
                g.drawCenteredString(font, SHORT[twin][art], tx, ty - 4, !ok ? 0xFF6A6A7A : i == hover ? 0xFFFFFFFF : twin == SOL ? 0xFFFFE6A8 : 0xFFE6EEFF);
            }
        } else {
            for (int i = 0; i < 4; i++) {
                double mid = i * Math.PI / 2;
                double rr = r0 + (r1 - r0) * 0.62 + (i == hover ? 4 : 0);
                int tx = (int) (cx + Math.sin(mid) * rr), ty = (int) (cy - Math.cos(mid) * rr);
                int art = TwinsClient.load(a, i);
                boolean ok = usable(a, art);
                int col = !ok ? 0xFF7A7484 : i == hover ? 0xFFFFFFFF : a == SOL ? 0xFFFFE6A8 : 0xFFE6EEFF;
                String s = art < 0 ? "Empty" : ARTS[a][art];
                int lines;
                if (font.width(s) > 60 && s.contains(" ")) {
                    String[] w = s.split(" ", 2);
                    g.drawCenteredString(font, w[0], tx, ty - 9, col);
                    g.drawCenteredString(font, w[1], tx, ty + 1, col);
                    lines = 2;
                } else { g.drawCenteredString(font, s, tx, ty - 4, col); lines = 1; }
                if (art >= 0 && TwinsClient.cd(a, art) > 0) g.drawCenteredString(font, (TwinsClient.cd(a, art) + 19) / 20 + "s", tx, ty + (lines == 2 ? 11 : 6), 0xFFB0B0C0);
            }
        }
        // the middle's words
        if (ecl) {
            g.drawCenteredString(font, "ECLIPSE", cx, cy - 8, 0xFFFFFFFF);
            g.drawCenteredString(font, (TwinsClient.v[V_ECLIPSE] + 19) / 20 + "s", cx, cy + 2, 0xFFE0D8FF);
        } else {
            g.drawCenteredString(font, TWIN[a], cx, cy - 8, a == SOL ? 0xFFFFE6A8 : 0xFFE6EEFF);
            g.drawCenteredString(font, "Lv " + TwinsClient.lv(a), cx, cy + 2, 0xFFD0C8D8);
        }
        // the line under the wheel
        int y = cy + (int) (r1 + 16);
        if (hover >= 0) {
            int twin = ecl ? (hover < 8 ? SOL : NYX) : a, art = ecl ? hover % 8 : TwinsClient.load(a, hover);
            if (art < 0) {
                g.drawCenteredString(font, "Empty slot", cx, y, 0xFFFFFFFF);
                g.drawCenteredString(font, "Choose an art for it on The Masks page.", cx, y + 11, 0xFFB8B8C8);
            } else {
                String d = !TwinsClient.knows(twin, art) ? "Learned at " + TWIN[twin] + " level " + ART_LV[art] + "."
                        : !ecl && TwinsClient.cd(twin, art) > 0 ? "Ready in " + (TwinsClient.cd(twin, art) + 19) / 20 + " s." : ART_DESC[twin][art];
                g.drawCenteredString(font, ARTS[twin][art] + "  (" + ART_MANA[twin][art] + " mana)", cx, y, 0xFFFFFFFF);
                g.drawCenteredString(font, d, cx, y + 11, 0xFFB8B8C8);
            }
        } else if (centre) {
            int rest = 1 - a;
            g.drawCenteredString(font, TwinsClient.v[V_SWAP] > 0 ? "Swap ready in " + (TwinsClient.v[V_SWAP] + 19) / 20 + " s" : "Let go to swap to " + TWIN[rest], cx, y, 0xFFFFFFFF);
            g.drawCenteredString(font, TWIN[rest] + "'s Resonance " + TwinsClient.v[V_RES] + "% bursts out as it takes your face.", cx, y + 11, 0xFFB8B8C8);
        }
    }

    private void pick() {
        int h = hover;
        boolean c = centre;
        onClose();
        boolean ecl = TwinsClient.eclipse();
        if (h >= 0) {
            if (ecl) TwinsClient.send(2, 100 + (h < 8 ? 0 : 8) + h % 8);
            else if (TwinsClient.load(TwinsClient.active(), h) >= 0) TwinsClient.send(2, h);
        } else if (c || ticks < 4) TwinsClient.send(1, 0);   // a tap, or let go in the middle: swap
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

    // ------------------------------------------------------------------ drawing helpers
    private static float px(int cx, double a, double r) { return (float) (cx + Math.sin(a) * r); }
    private static float py(int cy, double a, double r) { return (float) (cy - Math.cos(a) * r); }
    private static void v(BufferBuilder bb, Matrix4f m, float x, float y, float r, float g, float b, float a) { bb.vertex(m, x, y, 0).color(r, g, b, a).endVertex(); }

    private static void ring(BufferBuilder bb, Matrix4f m, int cx, int cy, double r0, double r1, double a0, double a1,
                             float ir, float ig, float ib, float ia, float or, float og, float ob, float oa, int seg) {
        for (int s = 0; s < seg; s++) {
            double b0 = a0 + (a1 - a0) * s / seg, b1 = a0 + (a1 - a0) * (s + 1) / seg;
            float x00 = px(cx, b0, r0), y00 = py(cy, b0, r0), x01 = px(cx, b0, r1), y01 = py(cy, b0, r1);
            float x10 = px(cx, b1, r0), y10 = py(cy, b1, r0), x11 = px(cx, b1, r1), y11 = py(cy, b1, r1);
            v(bb, m, x00, y00, ir, ig, ib, ia); v(bb, m, x11, y11, or, og, ob, oa); v(bb, m, x01, y01, or, og, ob, oa);
            v(bb, m, x00, y00, ir, ig, ib, ia); v(bb, m, x10, y10, ir, ig, ib, ia); v(bb, m, x11, y11, or, og, ob, oa);
        }
    }

    private static void tri(BufferBuilder bb, Matrix4f m, int cx, int cy, double a0, double r0, double a1, double r1, double a2, double r2, float r, float g, float b, float al) {
        v(bb, m, px(cx, a0, r0), py(cy, a0, r0), r, g, b, al);
        v(bb, m, px(cx, a2, r2), py(cy, a2, r2), r, g, b, al);
        v(bb, m, px(cx, a1, r1), py(cy, a1, r1), r, g, b, al);
    }

    /** For the screenshots: the slice shown as pointed at (-1: follow the mouse, -2: the middle). */
    public static int forceHover = -1;
}
