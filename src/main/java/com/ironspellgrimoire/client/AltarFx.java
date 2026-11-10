package com.ironspellgrimoire.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The Grimoire Altar at rest: it is never still. A ring of runes turns slowly on the bowl, a second ring turns
 * against it, three small stars circle above, and a thin column of light stands over it. The block reports itself
 * (animateTick) while a player is near, and only those altars are drawn.
 */
final class AltarFx {
    private AltarFx() {}

    private static final double TAU = Math.PI * 2;
    private static final Map<Long, double[]> ALTARS = new HashMap<>();   // pos -> {x, y, z, last seen}

    /** Called by the block, on the client, while a player is near it. */
    static void seen(BlockPos pos, double now) {
        ALTARS.put(pos.asLong(), new double[]{pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, now});
    }

    static void clear() { ALTARS.clear(); }

    private static void ring(Matrix4f m, Vec3 cam, Vec3 c, double r, int seg, double spin, double dash, float w, int pal, float a) {
        for (int i = 0; i < seg; i++) {
            double t0 = TAU * i / seg + spin, t1 = t0 + TAU / seg * dash;
            Vec3 p0 = c.add(Math.cos(t0) * r, 0, Math.sin(t0) * r), p1 = c.add(Math.cos(t1) * r, 0, Math.sin(t1) * r);
            FxBase.gline(m, cam, p0, p1, w, pal, a);
        }
    }

    /** A small rune: a short stroke with two ticks, standing on the ring. */
    private static void rune(Matrix4f m, Vec3 cam, Vec3 c, double ang, double r, int kind, int pal, float a) {
        Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang)), side = new Vec3(-Math.sin(ang), 0, Math.cos(ang));
        Vec3 p = c.add(out.scale(r));
        double l = 0.07;
        FxBase.gline(m, cam, p.subtract(out.scale(l)), p.add(out.scale(l)), 0.012F, pal, a);
        if (kind % 3 == 0) FxBase.gline(m, cam, p.add(out.scale(l * 0.2)).subtract(side.scale(l)), p.add(out.scale(l * 0.2)).add(side.scale(l)), 0.012F, pal, a);
        if (kind % 3 == 1) { FxBase.gline(m, cam, p.add(out.scale(l)), p.add(side.scale(l)), 0.012F, pal, a); FxBase.gline(m, cam, p.add(out.scale(l)), p.subtract(side.scale(l)), 0.012F, pal, a); }
        if (kind % 3 == 2) FxBase.gline(m, cam, p.subtract(out.scale(l)).add(side.scale(l * 0.8)), p.add(out.scale(l)).subtract(side.scale(l * 0.8)), 0.012F, pal, a);
    }

    static void draw(VertexConsumer vc, Matrix4f m, Vec3 cam, double now, float pt) {
        double t = now;
        for (Iterator<double[]> it = ALTARS.values().iterator(); it.hasNext(); ) {
            double[] a = it.next();
            if (t - a[3] > 160) { it.remove(); continue; }
            Vec3 c = new Vec3(a[0], a[1] + 0.78, a[2]);
            if (c.distanceToSqr(cam) > 48 * 48) continue;
            float breathe = 0.7F + 0.3F * (float) Math.sin(t * 0.07);
            // the lit bowl: two rings of runes turning against each other
            ring(m, cam, c, 0.5, 24, t * 0.02, 0.55, 0.02F, 8, 0.8F * breathe);
            ring(m, cam, c.add(0, 0.02, 0), 0.32, 18, -t * 0.03, 0.5, 0.016F, 10, 0.7F * breathe);
            for (int i = 0; i < 12; i++) rune(m, cam, c.add(0, 0.01, 0), TAU * i / 12 + t * 0.02, 0.62, i, 8, 0.85F);
            // three stars circling above, each trailing a short tail
            for (int k = 0; k < 3; k++) {
                for (int s = 0; s < 6; s++) {
                    double ang = t * 0.045 + k * TAU / 3 - s * 0.07;
                    double y = 1.1 + 0.18 * Math.sin(t * 0.06 + k * 2.1 - s * 0.05);
                    Vec3 p = c.add(Math.cos(ang) * 0.75, y, Math.sin(ang) * 0.75);
                    double ang2 = ang - 0.07;
                    Vec3 q = c.add(Math.cos(ang2) * 0.75, 1.1 + 0.18 * Math.sin(t * 0.06 + k * 2.1 - (s + 1) * 0.05), Math.sin(ang2) * 0.75);
                    FxBase.gline(m, cam, p, q, 0.05F * (1F - s / 6F), k == 1 ? 10 : 8, 0.9F * (1F - s / 6F));
                }
                double ang = t * 0.045 + k * TAU / 3;
                Vec3 p = c.add(Math.cos(ang) * 0.75, 1.1 + 0.18 * Math.sin(t * 0.06 + k * 2.1), Math.sin(ang) * 0.75);
                FxBase.gline(m, cam, p.add(0, -0.1, 0), p.add(0, 0.1, 0), 0.04F, 8, 1F);
                FxBase.gline(m, cam, p.add(-0.1, 0, 0), p.add(0.1, 0, 0), 0.04F, 8, 1F);
            }
            // a thin column of light, brightest low down
            FxBase.gline(m, cam, c, c.add(0, 4.0, 0), 0.03F, 8, 0.35F * breathe);
            FxBase.gline(m, cam, c, c.add(0, 1.8, 0), 0.07F, 10, 0.25F * breathe);
        }
    }
}
