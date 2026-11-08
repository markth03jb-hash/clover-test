package com.example.arcaneaddon.client;

import com.example.arcaneaddon.network.FxPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A second effect style, built from pixel-art sprites instead of solid shapes: hundreds of flat 2D sprites - long
 * flame streaks, diamond shards, square embers - each turned toward the camera but stretched along its own direction
 * of travel, laid out along a 3D arc. Up close every piece is a flat pixel sprite; together they read as a solid
 * three-dimensional slash, like an anime / pixel-art fire effect.
 *
 * First use (a test): Asta's Sky Splitter with the Demon Slayer - a towering crescent of black anti magic edged with
 * white-hot and ember-orange flame streaks, shedding sparks as it flies.
 *
 * FxPacket shape 41: id (so a kill packet can end it), c = start, d = direction of travel, u = the arc's "up",
 * r -> r2 = radius growing over the flight, speed = blocks per tick, life = ticks, palette = colour scheme.
 */
public final class PixelFx {
    private PixelFx() {}

    private static final ResourceLocation STREAK = tex("streak"), SHARD = tex("shard"), EMBER = tex("ember"), TONGUE = tex("tongue");

    private static ResourceLocation tex(String n) { return ResourceLocation.fromNamespaceAndPath("arcane_addon", "textures/effect/pixel/" + n + ".png"); }

    /** A moving pixel slash emitter. */
    private static final class Slash {
        int id; Vec3 c, d, u, side; double r0, r1, speed; int life, pal; double born; boolean dead;
    }

    /** One sprite. kind: 0 streak, 1 shard, 2 ember, 3 tongue. dark = drawn black (anti magic), else glowing. */
    private static final class Spr {
        Vec3 pos, prev, vel, axis; float len, wid; int life, age, kind; boolean dark; float heat; float drag;
        boolean red; Vec3 curl; float curlRate; float darkAlpha = 0.92F;
    }

    private static final List<Slash> SLASHES = new ArrayList<>();
    private static final List<Spr> SPRITES = new ArrayList<>();
    private static final int MAX = 3000;

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Slash s = new Slash();
        s.id = p.id; s.c = p.c; s.d = p.d.normalize(); s.u = p.u.normalize(); s.side = s.d.cross(s.u).normalize();
        s.r0 = p.r; s.r1 = p.r2 > 0 ? p.r2 : p.r; s.speed = p.speed; s.life = Math.max(1, p.life); s.pal = p.palette;
        s.born = mc.level.getGameTime();
        SLASHES.add(s);
        emit(s, 0, true);   // the first frame is already a full blade
    }

    public static void kill(int id) {
        for (Slash s : SLASHES) if (s.id == id) s.dead = true;
    }

    // ------------------------------------------------------------------ colours (by heat: 1 = white-hot ... 0 = dying)
    /** Anti magic's glow: crimson, from a hot pink-white down to a deep blood red. */
    private static float[] crimson(float heat) {
        if (heat > 0.8F) return new float[]{1.0F, 0.75F, 0.7F};
        if (heat > 0.55F) return new float[]{1.0F, 0.22F, 0.16F};
        if (heat > 0.3F) return new float[]{0.75F, 0.04F, 0.04F};
        return new float[]{0.35F, 0.0F, 0.02F};
    }

    private static float[] fire(float heat) {
        if (heat > 0.8F) return new float[]{1.0F, 0.97F, 0.85F};
        if (heat > 0.6F) return new float[]{1.0F, 0.86F, 0.35F};
        if (heat > 0.4F) return new float[]{1.0F, 0.55F, 0.12F};
        if (heat > 0.2F) return new float[]{0.9F, 0.22F, 0.05F};
        return new float[]{0.45F, 0.06F, 0.03F};
    }

    // ------------------------------------------------------------------ emitting
    private static Vec3 arcPoint(Slash s, double centre, double a, double R) {
        // a crescent: the outer edge curves back at the tips; a = -1..1 along the arc
        double ang = a * Math.toRadians(78);
        return s.c.add(s.d.scale(centre)).add(s.u.scale(Math.sin(ang) * R)).add(s.d.scale((Math.cos(ang) - 1.0) * R * 0.5));
    }

    private static Vec3 arcTangent(Slash s, double a, double R) {
        double ang = a * Math.toRadians(78);
        return s.u.scale(Math.cos(ang)).add(s.d.scale(-Math.sin(ang) * 0.5)).normalize();
    }

    private static Spr spr(Vec3 at, Vec3 vel, Vec3 axis, int kind, float len, float wid, int life) {
        Spr p = new Spr();
        p.pos = at; p.prev = at; p.vel = vel; p.axis = axis.lengthSqr() > 1.0E-8 ? axis.normalize() : new Vec3(0, 1, 0);
        p.kind = kind; p.len = len; p.wid = wid; p.life = life; p.drag = 0.86F;
        return p;
    }

    /**
     * Asta's anti magic as the anime draws it: a thick crescent of pure black, a thin white-hot cutting edge, a
     * blood-red glow bleeding off it, black lightning crackling out from the blade, black smoke curling in its wake,
     * and red embers and black flakes falling away. The moment it's released, a ring of shards bursts outward.
     */
    private static void emit(Slash s, double age, boolean burst) {
        if (s.pal != 0) { emitFlame(s, age, burst); return; }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double grow = 1.0 - Math.pow(1.0 - Mth.clamp(age / (s.life * 0.5), 0.0, 1.0), 2.0);
        double R = s.r0 + (s.r1 - s.r0) * grow;
        double centre = s.speed * age;
        Vec3 mid = s.c.add(s.d.scale(centre - R * 0.5));
        // the black blade: thick, chunky streaks packed along the arc
        for (int i = 0; i < (burst ? 170 : 100); i++) {
            double a = r.nextDouble(-1, 1), edge = Math.pow(Math.cos(a * Math.PI * 0.5), 1.2);
            Vec3 tan = arcTangent(s, a, R);
            double depth = R * (0.03 + r.nextDouble() * 0.3) * edge;
            Vec3 at = arcPoint(s, centre, a, R).subtract(s.d.scale(depth)).add(s.side.scale(r.nextGaussian() * 0.04 * R * edge));
            Spr p = spr(at, s.d.scale(s.speed * 0.93), tan.subtract(s.d.scale(0.1)), r.nextFloat() < 0.75F ? 0 : 3,
                    (float) (R * r.nextDouble(0.18, 0.34)), (float) (0.14 + 0.2 * edge), r.nextInt(3, 6));
            p.dark = true;
            add(p);
        }
        // the blood-red glow bleeding off it (wide, faint, a little behind and to the sides)
        for (int i = 0; i < (burst ? 50 : 32); i++) {
            double a = r.nextDouble(-1, 1), edge = Math.pow(Math.cos(a * Math.PI * 0.5), 1.2);
            Vec3 tan = arcTangent(s, a, R);
            double depth = R * (0.1 + r.nextDouble() * 0.3) * edge;
            Vec3 at = arcPoint(s, centre, a, R).subtract(s.d.scale(depth)).add(s.side.scale((r.nextBoolean() ? 1 : -1) * R * 0.1 * edge));
            Spr p = spr(at, s.d.scale(s.speed * 0.85), tan.subtract(s.d.scale(0.3)), 0,
                    (float) (R * r.nextDouble(0.2, 0.36)), (float) (0.25 + 0.25 * edge), r.nextInt(2, 5));
            p.red = true; p.heat = 0.6F;
            add(p);
        }
        // the white-hot cutting edge: thin and crisp, right on the arc
        for (int i = 0; i < (burst ? 40 : 28); i++) {
            double a = r.nextDouble(-1, 1), edge = Math.pow(Math.cos(a * Math.PI * 0.5), 1.2);
            Vec3 tan = arcTangent(s, a, R);
            Vec3 at = arcPoint(s, centre, a, R).add(s.d.scale(r.nextDouble() * R * 0.02));
            Spr p = spr(at, s.d.scale(s.speed * 0.97), tan, 0, (float) (R * r.nextDouble(0.12, 0.2) * (0.5 + 0.5 * edge)),
                    (float) (0.05 + 0.04 * edge), r.nextInt(2, 4));
            p.heat = 1.0F;
            add(p);
        }
        // black lightning crackling out of the blade, each bolt with a red glow
        for (int b = 0; b < (burst ? 6 : 2); b++) {
            double a = r.nextDouble(-0.9, 0.9);
            Vec3 at = arcPoint(s, centre, a, R).subtract(s.d.scale(R * 0.1));
            Vec3 dir = at.subtract(mid).normalize().add(s.side.scale(r.nextGaussian() * 0.6)).normalize();
            int segs = r.nextInt(4, 8);
            for (int k = 0; k < segs; k++) {
                Vec3 next = at.add(dir.scale(R * r.nextDouble(0.08, 0.16))).add(new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).scale(R * 0.05));
                Vec3 m0 = at.add(next).scale(0.5), ax = next.subtract(at);
                Spr bolt = spr(m0, s.d.scale(s.speed * 0.9), ax, 0, (float) ax.length() * 1.1F, 0.05F, r.nextInt(2, 4));
                bolt.dark = true; bolt.darkAlpha = 1.0F;
                add(bolt);
                Spr glow = spr(m0, s.d.scale(s.speed * 0.9), ax, 0, (float) ax.length() * 1.2F, 0.16F, 2);
                glow.red = true; glow.heat = 0.7F;
                add(glow);
                at = next;
                if (r.nextInt(4) == 0) dir = dir.add(new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).scale(0.5)).normalize();
            }
        }
        // black smoke curling round the path behind it
        for (int i = 0; i < (burst ? 14 : 8); i++) {
            double a = r.nextDouble(-1, 1);
            Vec3 at = arcPoint(s, centre, a, R * 0.85).subtract(s.d.scale(R * r.nextDouble(0.2, 0.5)));
            Vec3 radial = at.subtract(mid).normalize();
            Spr p = spr(at, s.d.scale(s.speed * 0.2).add(radial.scale(0.05)), radial.cross(s.d), 3,
                    (float) r.nextDouble(0.5, 1.0), (float) r.nextDouble(0.3, 0.55), r.nextInt(10, 17));
            p.dark = true; p.darkAlpha = 0.7F; p.curl = s.d; p.curlRate = (float) r.nextDouble(0.2, 0.4) * (r.nextBoolean() ? 1 : -1); p.drag = 0.9F;
            add(p);
        }
        // red embers and black flakes falling away
        for (int i = 0; i < (burst ? 22 : 12); i++) {
            double a = r.nextDouble(-1, 1);
            Vec3 at = arcPoint(s, centre, a, R);
            Vec3 out = at.subtract(mid).normalize();
            Vec3 vel = out.scale(r.nextDouble(0.06, 0.25)).add(s.d.scale(s.speed * 0.35)).add(0, r.nextDouble(0.0, 0.06), 0);
            float sz = (float) r.nextDouble(0.06, 0.15);
            Spr p = spr(at, vel, vel, r.nextFloat() < 0.6F ? 2 : 1, sz, sz, r.nextInt(8, 16));
            if (r.nextBoolean()) { p.red = true; p.heat = 0.9F; } else { p.dark = true; p.darkAlpha = 1.0F; }
            p.drag = 0.92F;
            add(p);
        }
        if (burst) {   // released: a ring of shards bursting outward round the blade, and white flash streaks
            for (int i = 0; i < 40; i++) {
                double ang = Math.PI * 2 * i / 40;
                Vec3 out = s.u.scale(Math.cos(ang)).add(s.side.scale(Math.sin(ang)));
                Spr p = spr(s.c.add(out.scale(0.6)), out.scale(r.nextDouble(0.45, 0.7)), out, 0, 0.45F, 0.08F, r.nextInt(4, 7));
                p.heat = 1.0F; p.drag = 0.8F;
                if (i % 2 == 0) { p.red = true; p.heat = 0.85F; }
                add(p);
            }
            for (int i = 0; i < 12; i++) {
                Vec3 out = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                Spr p = spr(s.c, out.scale(0.25), out, 0, 1.2F, 0.1F, 3);
                p.heat = 1.0F;
                add(p);
            }
        }
    }

    /** The plain flame version (other schemes). */
    private static void emitFlame(Slash s, double age, boolean burst) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double grow = 1.0 - Math.pow(1.0 - Mth.clamp(age / (s.life * 0.5), 0.0, 1.0), 2.0);
        double R = s.r0 + (s.r1 - s.r0) * grow;
        double centre = s.speed * age;
        int n = burst ? 150 : 90;
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble(-1, 1);
            double edge = Math.pow(Math.cos(a * Math.PI * 0.5), 1.3);   // the blade is thickest in the middle
            Vec3 tan = arcTangent(s, a, R);
            double q = r.nextDouble(), depth;
            int sgn = r.nextBoolean() ? 1 : -1;
            Spr p = new Spr();
            if (q < 0.4) {          // the cutting edge: white-hot streaks lying exactly along the arc, moving with it
                depth = r.nextDouble() * R * 0.04;
                p.kind = 0; p.dark = false; p.heat = 1.0F;
                p.axis = tan.subtract(s.d.scale(0.12)).normalize();
                p.vel = s.d.scale(s.speed * 0.95);
                p.len = (float) (R * r.nextDouble(0.12, 0.22) * (0.5 + 0.5 * edge)); p.wid = (float) (0.07 + 0.05 * edge);
                p.life = r.nextInt(3, 5);
            } else if (q < 0.7) {   // the core: black anti magic just behind the edge
                depth = R * (0.06 + r.nextDouble() * 0.16) * edge;
                p.kind = r.nextFloat() < 0.8F ? 0 : 3; p.dark = s.pal == 0; p.heat = 0.55F;
                p.axis = tan.subtract(s.d.scale(0.1)).normalize();
                p.vel = s.d.scale(s.speed * 0.9);
                p.len = (float) (R * r.nextDouble(0.14, 0.26)); p.wid = (float) (0.1 + 0.12 * edge);
                p.life = r.nextInt(3, 6);
            } else {                // flames swept back off the blade, cooling as they fall behind
                depth = R * r.nextDouble() * 0.25 * edge;
                p.kind = r.nextFloat() < 0.6F ? 0 : 3; p.dark = false; p.heat = 0.75F;
                p.axis = tan.scale(sgn * 0.6).subtract(s.d.scale(0.8)).normalize();
                p.vel = s.d.scale(s.speed * r.nextDouble(0.3, 0.55));
                p.len = (float) (R * r.nextDouble(0.1, 0.22) * (0.6 + 0.4 * edge)); p.wid = (float) (0.06 + 0.06 * edge);
                p.life = r.nextInt(6, 11);
            }
            Vec3 at = arcPoint(s, centre, a, R).subtract(s.d.scale(depth)).add(s.side.scale(r.nextGaussian() * 0.05 * R * edge));
            p.pos = at; p.prev = at;
            p.drag = 0.86F;
            add(p);
        }
        // sparks and shards flung off the edge
        for (int i = 0; i < (burst ? 20 : 10); i++) {
            double a = r.nextDouble(-1, 1);
            Vec3 at = arcPoint(s, centre, a, R);
            Vec3 out = at.subtract(s.c.add(s.d.scale(centre - R * 0.5))).normalize();
            Spr p = new Spr();
            p.pos = at; p.prev = at;
            p.kind = r.nextFloat() < 0.6F ? 2 : 1; p.dark = false; p.heat = r.nextFloat() * 0.5F + 0.5F;
            p.vel = out.scale(r.nextDouble(0.08, 0.3)).add(s.d.scale(s.speed * 0.4)).add(0, r.nextDouble(0.0, 0.08), 0);
            p.axis = p.vel.lengthSqr() > 1.0E-6 ? p.vel.normalize() : s.d;
            float sz = (float) r.nextDouble(0.06, 0.16);
            p.len = sz; p.wid = sz;
            p.life = r.nextInt(8, 16);
            p.drag = 0.92F;
            add(p);
        }
    }

    private static void add(Spr p) {
        if (SPRITES.size() >= MAX) SPRITES.remove(0);
        SPRITES.add(p);
    }

    // ------------------------------------------------------------------ ticking
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { SLASHES.clear(); SPRITES.clear(); return; }
        double now = mc.level.getGameTime();
        for (Iterator<Slash> it = SLASHES.iterator(); it.hasNext(); ) {
            Slash s = it.next();
            double age = now - s.born;
            if (s.dead || age > s.life) { it.remove(); continue; }
            emit(s, age, false);
        }
        for (Iterator<Spr> it = SPRITES.iterator(); it.hasNext(); ) {
            Spr p = it.next();
            if (++p.age > p.life) { it.remove(); continue; }
            p.prev = p.pos;
            p.pos = p.pos.add(p.vel);
            p.vel = p.vel.scale(p.drag);
            if (p.kind == 2 || p.kind == 1) p.vel = p.vel.add(0, -0.012, 0);   // sparks fall a little
            if (p.curl != null) {   // smoke curling round the path: turn its velocity about the travel axis
                double ca = Math.cos(p.curlRate), sa = Math.sin(p.curlRate);
                Vec3 k = p.curl, v = p.vel;
                p.vel = v.scale(ca).add(k.cross(v).scale(sa)).add(k.scale(k.dot(v) * (1 - ca)));
                p.axis = p.vel.lengthSqr() > 1.0E-8 ? p.vel.normalize() : p.axis;
            }
            if (!p.dark) p.heat = Math.max(0.0F, p.heat - 0.9F / p.life);
        }
    }

    // ------------------------------------------------------------------ drawing
    public static void render(PoseStack ps, Camera camera, MultiBufferSource.BufferSource buf, float pt) {
        if (SPRITES.isEmpty()) return;
        Vec3 cam = camera.getPosition();
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float opacity = com.example.arcaneaddon.AddonClientConfig.effectOpacity();
        // the black core first (normal blending), then the glowing flame on top (additive: eyes ignores alpha,
        // so the fade is done by darkening the colour)
        ResourceLocation[] texs = {STREAK, SHARD, EMBER, TONGUE};
        for (int pass = 0; pass < 2; pass++) {
            for (int kind = 0; kind < 4; kind++) {
                RenderType rt = pass == 0 ? RenderType.entityTranslucentEmissive(texs[kind]) : RenderType.eyes(texs[kind]);
                VertexConsumer vc = null;
                for (Spr p : SPRITES) {
                    if (p.kind != kind || p.dark != (pass == 0)) continue;
                    if (vc == null) vc = buf.getBuffer(rt);
                    draw(vc, m, n, cam, p, pt, pass == 0, opacity);
                }
                if (vc != null) buf.endBatch(rt);
            }
        }
    }

    private static void draw(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Spr p, float pt, boolean dark, float opacity) {
        Vec3 pos = p.prev.add(p.pos.subtract(p.prev).scale(pt));
        float life = (p.age + pt) / (float) p.life;
        float fade = Mth.clamp((1.0F - life) * 2.2F, 0.0F, 1.0F) * opacity;
        if (fade <= 0.01F) return;
        // stretched along its own direction, turned to face the camera around that direction
        Vec3 toCam = cam.subtract(pos);
        Vec3 ax = p.axis;
        Vec3 side = ax.cross(toCam);
        if (side.lengthSqr() < 1.0E-8) side = ax.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-8) side = new Vec3(1, 0, 0);
        side = side.normalize();
        float len = p.len * (p.kind == 0 || p.kind == 3 ? (0.7F + 0.6F * (1.0F - life)) : 1.0F);
        Vec3 a = ax.scale(len * 0.5), b = side.scale(p.wid * 0.5);
        float r, g, bl, al;
        if (dark) { r = 0.02F; g = 0.0F; bl = 0.01F; al = p.darkAlpha * fade; }
        else {
            float[] c = p.red ? crimson(p.heat) : fire(p.heat);
            r = c[0] * fade; g = c[1] * fade; bl = c[2] * fade; al = 1.0F;
        }
        v(vc, m, n, cam, pos.subtract(a).subtract(b), r, g, bl, al, 0, 1);
        v(vc, m, n, cam, pos.subtract(a).add(b), r, g, bl, al, 1, 1);
        v(vc, m, n, cam, pos.add(a).add(b), r, g, bl, al, 1, 0);
        v(vc, m, n, cam, pos.add(a).subtract(b), r, g, bl, al, 0, 0);
        // and the back face, so no render state can cull it away
        v(vc, m, n, cam, pos.add(a).subtract(b), r, g, bl, al, 0, 0);
        v(vc, m, n, cam, pos.add(a).add(b), r, g, bl, al, 1, 0);
        v(vc, m, n, cam, pos.subtract(a).add(b), r, g, bl, al, 1, 1);
        v(vc, m, n, cam, pos.subtract(a).subtract(b), r, g, bl, al, 0, 1);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 w, float r, float g, float b, float a, float u, float t) {
        vc.vertex(m, (float) (w.x - cam.x), (float) (w.y - cam.y), (float) (w.z - cam.z)).color(r, g, b, a).uv(u, t)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
    }
}
