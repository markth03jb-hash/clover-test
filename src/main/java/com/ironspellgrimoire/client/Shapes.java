package com.ironspellgrimoire.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/** Flat 2D shapes for the menus (triangles in one batch): lines, rings, discs, diamonds, eyes, arrowheads. Colours are ARGB. */
final class Shapes {
    private Shapes() {}

    private static BufferBuilder bb;
    private static Matrix4f m;

    static void begin(GuiGraphics g) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        m = g.pose().last().pose();
        bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }

    static void end() {
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.disableBlend();
    }

    private static void v(double x, double y, int c) {
        bb.vertex(m, (float) x, (float) y, 0).color((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >>> 24) & 255).endVertex();
    }

    static void tri(double x0, double y0, double x1, double y1, double x2, double y2, int c) { v(x0, y0, c); v(x2, y2, c); v(x1, y1, c); v(x0, y0, c); v(x1, y1, c); v(x2, y2, c); }

    static void quad(double x0, double y0, double x1, double y1, double x2, double y2, double x3, double y3, int c) {
        tri(x0, y0, x1, y1, x2, y2, c); tri(x0, y0, x2, y2, x3, y3, c);
    }

    static void line(double x0, double y0, double x1, double y1, double w, int c) {
        double dx = x1 - x0, dy = y1 - y0, l = Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-3) return;
        double nx = -dy / l * w / 2, ny = dx / l * w / 2;
        quad(x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny, c);
    }

    static void dashed(double x0, double y0, double x1, double y1, double w, double dash, int c) {
        double dx = x1 - x0, dy = y1 - y0, l = Math.sqrt(dx * dx + dy * dy);
        for (double s = 0; s < l; s += dash * 2) {
            double e = Math.min(l, s + dash);
            line(x0 + dx * s / l, y0 + dy * s / l, x0 + dx * e / l, y0 + dy * e / l, w, c);
        }
    }

    static void disc(double cx, double cy, double r, int c) {
        int seg = Math.max(10, (int) (r * 1.6));
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            tri(cx, cy, cx + Math.cos(a0) * r, cy + Math.sin(a0) * r, cx + Math.cos(a1) * r, cy + Math.sin(a1) * r, c);
        }
    }

    static void ring(double cx, double cy, double r0, double r1, int c) { arc(cx, cy, r0, r1, 0, Math.PI * 2, c); }

    static void arc(double cx, double cy, double r0, double r1, double a0, double a1, int c) {
        int seg = Math.max(8, (int) (Math.abs(a1 - a0) * r1 / 3));
        for (int i = 0; i < seg; i++) {
            double b0 = a0 + (a1 - a0) * i / seg, b1 = a0 + (a1 - a0) * (i + 1) / seg;
            quad(cx + Math.cos(b0) * r0, cy + Math.sin(b0) * r0, cx + Math.cos(b0) * r1, cy + Math.sin(b0) * r1,
                    cx + Math.cos(b1) * r1, cy + Math.sin(b1) * r1, cx + Math.cos(b1) * r0, cy + Math.sin(b1) * r0, c);
        }
    }

    static void diamond(double cx, double cy, double r, int c) { quad(cx, cy - r, cx + r, cy, cx, cy + r, cx - r, cy, c); }

    static void diamondRing(double cx, double cy, double r, double w, int c) {
        line(cx, cy - r, cx + r, cy, w, c); line(cx + r, cy, cx, cy + r, w, c);
        line(cx, cy + r, cx - r, cy, w, c); line(cx - r, cy, cx, cy - r, w, c);
    }

    /** An almond eye: open 0 (a closed lid's line) .. 1 (wide open). */
    static void eye(double cx, double cy, double w, double open, int fill, int rim) {
        int seg = 16;
        double h = w * 0.55 * open;
        for (int i = 0; i < seg; i++) {
            double t0 = (double) i / seg, t1 = (double) (i + 1) / seg;
            double x0 = cx - w + 2 * w * t0, x1 = cx - w + 2 * w * t1;
            double y0 = Math.sin(Math.PI * t0) * h, y1 = Math.sin(Math.PI * t1) * h;
            if (open > 0.05) quad(x0, cy - y0, x1, cy - y1, x1, cy + y1, x0, cy + y0, fill);
            line(x0, cy - y0, x1, cy - y1, 1.2, rim);
            line(x0, cy + y0, x1, cy + y1, 1.2, rim);
        }
    }

    static void arrowHead(double x, double y, double ang, double s, int c) {
        tri(x + Math.cos(ang) * s, y + Math.sin(ang) * s, x + Math.cos(ang + 2.5) * s, y + Math.sin(ang + 2.5) * s,
                x + Math.cos(ang - 2.5) * s, y + Math.sin(ang - 2.5) * s, c);
    }

    static void rect(double x0, double y0, double x1, double y1, int c) { quad(x0, y0, x1, y0, x1, y1, x0, y1, c); }

    static int alpha(int c, float a) { return (Math.round(((c >>> 24) & 255) * Math.max(0, Math.min(1, a))) << 24) | (c & 0xFFFFFF); }
}
