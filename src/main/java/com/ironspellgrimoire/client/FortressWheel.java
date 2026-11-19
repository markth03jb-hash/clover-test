package com.ironspellgrimoire.client;

import com.ironspellgrimoire.server.FortressSiege;
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

/**
 * The Architect of Ruin's G wheel: hold G, point, let go. Four slices of dressed stone with brass rims - Hold the Gate
 * (top), Charge (right), Fall Back (bottom) and Overcharge (left: the tower you were looking at when you opened it).
 * The order in force is lit; choosing it again lets the garrison fight as it likes. Let go in the middle to cancel.
 */
public class FortressWheel extends Screen {
    private int hover = -1;
    private float open;
    private long lastNs;

    /** For the screenshots: the slice shown as pointed at (-1: follow the mouse). */
    public static int forceHover = -1;

    static final String[] NAME = {"Hold the Gate", "Charge", "Fall Back", "Overcharge"};
    static final String[] SAYS = {
            "Defenders hold the gate (or fill a breach) and won't chase.",
            "Defenders rush the strongest foe near you, 20% faster.",
            "Defenders run back to you and guard you.",
            "The tower you're looking at fires twice as fast for 5 s (30 mana)."};

    public FortressWheel() { super(Component.literal("Fortress orders")); }

    @Override public boolean isPauseScreen() { return false; }

    private static final float[] STONE = {0.20F, 0.18F, 0.16F}, STONE_LIT = {0.44F, 0.36F, 0.24F}, BRASS = {0.91F, 0.72F, 0.38F}, EMBER = {0.95F, 0.45F, 0.20F};

    /** Slice i stands for order i + 1 (0-2), or the overcharge (3). */
    private static boolean usable(int i) { return i == 3 || FortressClient.defendersOut() > 0; }
    private static boolean current(int i) { return i < 3 && FortressClient.order() == i + 1 && FortressClient.defendersOut() > 0; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long ns = System.nanoTime();
        float dt = lastNs == 0 ? 0 : Math.min(0.1F, (ns - lastNs) / 1e9F);
        lastNs = ns;
        open = Math.min(1F, open + dt * 7F);
        float e = 1 - (1 - open) * (1 - open);
        int cx = width / 2, cy = height / 2;
        double R = Math.min(92, height / 2.0 - 40);
        double r0 = 28 * e, r1 = R * e;
        double dx = mx - cx, dy = my - cy, dist = Math.sqrt(dx * dx + dy * dy);
        double ang = Math.atan2(dx, -dy);
        if (ang < 0) ang += Math.PI * 2;
        hover = dist > 26 ? (int) Math.floor((ang + Math.PI / 4) / (Math.PI / 2)) % 4 : -1;
        if (forceHover >= 0) { hover = forceHover; open = 1F; e = 1F; r0 = 28; r1 = R; }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        double step = Math.PI / 2, gap = 0.035;
        for (int i = 0; i < 4; i++) {
            double a0 = i * step - step / 2 + gap, a1 = (i + 1) * step - step / 2 - gap;
            boolean h = i == hover, ok = usable(i), cur = current(i);
            float k = ok ? 1F : 0.45F;
            float[] c = h && ok ? STONE_LIT : STONE;
            double out = r1 + (h ? 8 : 0);
            ring(bb, m, cx, cy, r0, out, a0, a1, c[0] * 0.7F * k, c[1] * 0.7F * k, c[2] * 0.7F * k, 0.93F, c[0] * k, c[1] * k, c[2] * k, 0.93F, 14);
            // courses of stone across the slice
            for (double rr = r0 + 14; rr < out - 6; rr += 14) ring(bb, m, cx, cy, rr, rr + 1, a0, a1, 0.08F, 0.07F, 0.06F, 0.6F * k, 0.08F, 0.07F, 0.06F, 0.6F * k, 14);
            float[] rim = cur ? EMBER : BRASS;
            ring(bb, m, cx, cy, out - 3, out, a0, a1, rim[0] * k, rim[1] * k, rim[2] * k, 0.96F, rim[0] * k, rim[1] * k, rim[2] * k, 0.96F, 14);
            if (cur) ring(bb, m, cx, cy, r0, r0 + 2, a0, a1, EMBER[0], EMBER[1], EMBER[2], 0.9F, EMBER[0], EMBER[1], EMBER[2], 0.9F, 14);
            // merlons along the rim
            for (int b = 0; b < 5; b++) {
                double ma = a0 + (a1 - a0) * (b + 0.25) / 5, mb = a0 + (a1 - a0) * (b + 0.75) / 5;
                ring(bb, m, cx, cy, out, out + 5 * e, ma, mb, BRASS[0] * 0.7F * k, BRASS[1] * 0.7F * k, BRASS[2] * 0.7F * k, 0.9F, BRASS[0] * 0.7F * k, BRASS[1] * 0.7F * k, BRASS[2] * 0.7F * k, 0.9F, 2);
            }
        }
        boolean centre = hover < 0;
        float glow = centre ? 1F : 0.6F;
        ring(bb, m, cx, cy, 0, 25, 0, Math.PI * 2, 0.16F * glow + 0.06F, 0.13F * glow + 0.05F, 0.10F * glow + 0.04F, 0.97F, 0.10F, 0.09F, 0.07F, 0.97F, 32);
        ring(bb, m, cx, cy, 24, 26, 0, Math.PI * 2, BRASS[0], BRASS[1], BRASS[2], 0.95F, BRASS[0], BRASS[1], BRASS[2], 0.95F, 32);
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.disableBlend();

        for (int i = 0; i < 4; i++) {
            double mid = i * step;
            double rr = r0 + (r1 - r0) * 0.58 + (i == hover ? 4 : 0);
            int tx = (int) (cx + Math.sin(mid) * rr), ty = (int) (cy - Math.cos(mid) * rr);
            boolean ok = usable(i);
            int col = !ok ? 0xFF7A7268 : current(i) ? 0xFFFFB070 : i == hover ? 0xFFFFFFFF : 0xFFF0E2C4;
            String s = NAME[i];
            if (font.width(s) > 56 && s.contains(" ")) {
                String[] w = s.split(" ", 2);
                g.drawCenteredString(font, w[0], tx, ty - 9, col);
                g.drawCenteredString(font, w[1], tx, ty + 1, col);
            } else g.drawCenteredString(font, s, tx, ty - 4, col);
        }
        int ord = FortressClient.order();
        g.drawCenteredString(font, "ORDERS", cx, cy - 8, 0xFFE8B860);
        g.drawCenteredString(font, FortressClient.defendersOut() > 0 ? FortressSiege.ORDER[Math.max(0, Math.min(3, ord))] : "-", cx, cy + 2, 0xFFC8C4BC);
        int y = cy + (int) (r1 + 18);
        if (hover >= 0) {
            boolean ok = usable(hover);
            g.drawCenteredString(font, NAME[hover] + (current(hover) ? "  (in force - choose again to lift it)" : ""), cx, y, 0xFFFFFFFF);
            g.drawCenteredString(font, ok ? SAYS[hover] : "No defenders are out - cast Siege Mode or Call Guard first.", cx, y + 11, ok ? 0xFFC8C0B0 : 0xFFE07A6A);
        } else {
            g.drawCenteredString(font, "Let go here to cancel", cx, y, 0xFFFFFFFF);
            g.drawCenteredString(font, FortressClient.defendersOut() + " defender" + (FortressClient.defendersOut() == 1 ? "" : "s") + " out", cx, y + 11, 0xFFC8C0B0);
        }
    }

    private void pick() {
        int h = hover;
        onClose();
        if (h < 0 || !usable(h)) return;
        if (h == 3) FortressClient.send(13, 0);
        else FortressClient.send(12, h + 1);
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
}
