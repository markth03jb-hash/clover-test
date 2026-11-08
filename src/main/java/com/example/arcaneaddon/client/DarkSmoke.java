package com.example.arcaneaddon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Yami's darkness: the addon's own soft, billowing smoke (not Minecraft particles). Puffs of black smoke curl off every
 * Dark Grimoire effect, swell and thin out as they rise, and small violet motes drift up through them.
 * Also drives Black Blade's subtle aura: violet motes rising off the body (FxPacket shape 16).
 */
public final class DarkSmoke {
    private DarkSmoke() {}

    private static final ResourceLocation SMOKE = ResourceLocation.fromNamespaceAndPath("arcane_addon", "textures/effect/dark_smoke.png");
    private static final ResourceLocation MOTE = ResourceLocation.fromNamespaceAndPath("arcane_addon", "textures/effect/dark_mote.png");
    private static final int MAX = 900;

    private static final class Puff {
        double x, y, z, vx, vy, vz, born, life;
        float s0, s1, rot, spin;
        boolean mote, streak, bead, flame, tinted;
        float tr, tg, tb;
        float shade;
    }

    private static final List<Puff> PUFFS = new ArrayList<>();
    /** Black Blade motes: entity id -> end tick. */
    private static final Map<Integer, Double> BODY = new HashMap<>();

    /** One puff of black smoke at {@code at}, drifting with {@code vel} (blocks per tick). */
    public static void smoke(Vec3 at, Vec3 vel, float size, int life) {
        add(at, vel, size * 0.6F, size * 1.5F, life, false);
    }

    /** One small violet mote. */
    public static void mote(Vec3 at, Vec3 vel, float size, int life) {
        add(at, vel, size, size * 0.4F, life, true);
    }

    /** A tongue of fire: white-yellow at first, then orange, red, and finally a wisp of dark smoke, rising. */
    public static void flame(Vec3 at, Vec3 vel, float size, int life) {
        int before = PUFFS.size();
        add(at, vel, size * 0.7F, size * 1.3F, life, false);
        if (!PUFFS.isEmpty() && (PUFFS.size() != before || before >= MAX)) PUFFS.get(PUFFS.size() - 1).flame = true;
    }

    /** A tongue of blue flame (Calidos Brachium Purgatory): white-cyan, then deep blue, then gone. */
    public static void blueFlame(Vec3 at, Vec3 vel, float size, int life) {
        int before = PUFFS.size();
        add(at, vel, size * 0.7F, size * 1.3F, life, false);
        if (!PUFFS.isEmpty() && (PUFFS.size() != before || before >= MAX)) { Puff p = PUFFS.get(PUFFS.size() - 1); p.flame = true; p.tinted = true; }
    }

    /** Hellfire Incarnate: entity id -> {start, end}. */
    private static final Map<Integer, double[]> HELLFIRE = new HashMap<>();

    public static void hellfire(int id, int life) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime();
        if (life <= 0) HELLFIRE.remove(id);
        else { double[] f = HELLFIRE.get(id); if (f == null) HELLFIRE.put(id, new double[]{now, now + life}); else f[1] = now + life; }
    }

    /** Something burning (Magna's fire): entity id -> end tick. */
    private static final Map<Integer, Double> BURNING = new HashMap<>();
    /** Flame Knuckles: entity id -> end tick. */
    private static final Map<Integer, Double> KNUCKLES = new HashMap<>();

    public static void burning(int id, int life) { track(BURNING, id, life); }
    public static void knuckles(int id, int life) { track(KNUCKLES, id, life); }

    private static void track(Map<Integer, Double> map, int id, int life) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (life <= 0) map.remove(id); else map.put(id, mc.level.getGameTime() + (double) life);
    }

    /** A spark of lightning: a bright yellow-white fleck that jumps and falls. */
    public static void spark(Vec3 at, Vec3 vel, float size, int life) {
        int before = PUFFS.size();
        add(at, vel, size, size * 0.5F, life, true);
        if (!PUFFS.isEmpty() && (PUFFS.size() != before || before >= MAX)) {
            Puff p = PUFFS.get(PUFFS.size() - 1);
            p.bead = true; p.tinted = true; p.tr = 1.0F; p.tg = 0.95F; p.tb = 0.55F;
        }
    }

    /** A streak in a chosen colour. */
    public static void streakTinted(Vec3 at, Vec3 vel, float size, int life, float r, float g, float b) {
        int before = PUFFS.size();
        add(at, vel, size, size * 0.6F, life, true);
        if (!PUFFS.isEmpty() && (PUFFS.size() != before || before >= MAX)) {
            Puff p = PUFFS.get(PUFFS.size() - 1);
            p.streak = true; p.tinted = true; p.tr = r; p.tg = g; p.tb = b;
        }
    }

    /** A bead of mercury: a small silver droplet that falls. */
    public static void bead(Vec3 at, Vec3 vel, float size, int life) {
        int before = PUFFS.size();
        add(at, vel, size, size, life, true);
        if (!PUFFS.isEmpty() && (PUFFS.size() != before || before >= MAX)) PUFFS.get(PUFFS.size() - 1).bead = true;
    }

    /** A wind streak: a thin mint-white line of light stretched along its velocity (Yuno's wind). */
    public static void streak(Vec3 at, Vec3 vel, float size, int life) {
        int before = PUFFS.size();
        add(at, vel, size, size * 0.6F, life, true);
        if (!PUFFS.isEmpty() && (PUFFS.size() != before || before >= MAX)) PUFFS.get(PUFFS.size() - 1).streak = true;
    }

    private static void add(Vec3 at, Vec3 vel, float s0, float s1, int life, boolean mote) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (PUFFS.size() >= MAX) PUFFS.remove(0);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Puff p = new Puff();
        p.x = at.x; p.y = at.y; p.z = at.z; p.vx = vel.x; p.vy = vel.y; p.vz = vel.z;
        p.born = mc.level.getGameTime(); p.life = life; p.s0 = s0; p.s1 = s1;
        p.rot = r.nextFloat() * 6.283F; p.spin = (r.nextFloat() - 0.5F) * 0.12F;
        p.mote = mote; p.shade = r.nextFloat();
        PUFFS.add(p);
    }

    /** Black Blade's aura: violet motes rising off the player for {@code life} ticks (0 = stop). */
    public static void body(int id, int life) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime();
        if (life <= 0) { BODY.remove(id); return; }
        boolean fresh = !BODY.containsKey(id);
        BODY.put(id, now + life);
        Entity e = mc.level.getEntity(id);
        if (fresh && e != null) for (int k = 0; k < 14; k++) bodyMote(e, 1.6);   // a soft burst as the blade is coated
    }

    private static void bodyMote(Entity e, double push) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double w = e.getBbWidth() * 0.5;
        double a = r.nextDouble(Math.PI * 2);
        Vec3 at = e.position().add(Math.cos(a) * w * r.nextDouble(0.6, 1.1), r.nextDouble(0.1, e.getBbHeight() * 0.95), Math.sin(a) * w * r.nextDouble(0.6, 1.1));
        Vec3 v = new Vec3(Math.cos(a) * 0.012 * push, r.nextDouble(0.018, 0.045), Math.sin(a) * 0.012 * push);
        mote(at, v, (float) r.nextDouble(0.05, 0.1), r.nextInt(18, 34));
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { PUFFS.clear(); BODY.clear(); BURNING.clear(); KNUCKLES.clear(); HELLFIRE.clear(); return; }
        double now = mc.level.getGameTime();
        Iterator<Puff> it = PUFFS.iterator();
        while (it.hasNext()) {
            Puff p = it.next();
            if (now - p.born > p.life) { it.remove(); continue; }
            p.x += p.vx; p.y += p.vy; p.z += p.vz;
            double drag = p.bead ? 0.98 : p.streak ? 0.93 : p.mote ? 0.96 : 0.88;
            p.vx *= drag; p.vz *= drag; p.vy = p.vy * drag + (p.bead ? -0.04 : p.streak ? 0.0 : p.mote ? 0.0015 : p.flame ? 0.012 : 0.004);   // darkness rises slowly, mercury falls
            p.rot += p.spin;
        }
        ThreadLocalRandom rr = ThreadLocalRandom.current();
        Iterator<Map.Entry<Integer, Double>> fi = BURNING.entrySet().iterator();
        while (fi.hasNext()) {   // flames licking up the body
            Map.Entry<Integer, Double> b = fi.next();
            Entity e = mc.level.getEntity(b.getKey());
            if (e == null || now > b.getValue() || !e.isAlive()) { fi.remove(); continue; }
            boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
            for (int k = 0; k < (self ? 1 : 2); k++) {
                double a = rr.nextDouble(Math.PI * 2), w = e.getBbWidth() * 0.45;
                Vec3 at = e.position().add(Math.cos(a) * w, rr.nextDouble(0.0, e.getBbHeight() * (self ? 0.5 : 0.9)), Math.sin(a) * w);
                flame(at, new Vec3(0, 0.03, 0), (float) rr.nextDouble(0.12, 0.22), rr.nextInt(8, 14));
            }
        }
        Iterator<Map.Entry<Integer, double[]>> hi = HELLFIRE.entrySet().iterator();
        while (hi.hasNext()) {   // Hellfire Incarnate: the body pours fire, more and bigger the longer it lasts
            Map.Entry<Integer, double[]> b = hi.next();
            Entity e = mc.level.getEntity(b.getKey());
            if (e == null || now > b.getValue()[1]) { hi.remove(); continue; }
            double grow = Math.min(1.0, (now - b.getValue()[0]) / 400.0);
            boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
            int n = self ? 1 : 3 + (int) (grow * 3);
            for (int k = 0; k < n; k++) {
                double a = rr.nextDouble(Math.PI * 2), w = e.getBbWidth() * 0.5;
                Vec3 at = e.position().add(Math.cos(a) * w, rr.nextDouble(0.0, e.getBbHeight() * (self ? 0.4 : 1.05)), Math.sin(a) * w);
                flame(at, new Vec3(Math.cos(a) * 0.02, 0.05 + grow * 0.03, Math.sin(a) * 0.02), (float) (rr.nextDouble(0.2, 0.32) * (1 + grow * 0.6)), rr.nextInt(8, 16));
            }
            if (rr.nextInt(2) == 0) spark(e.position().add(rr.nextDouble(-0.5, 0.5), rr.nextDouble(0.2, 2.0), rr.nextDouble(-0.5, 0.5)),
                    new Vec3(rr.nextDouble(-0.05, 0.05), rr.nextDouble(0.1, 0.2), rr.nextDouble(-0.05, 0.05)), 0.03F, rr.nextInt(10, 18));
        }
        Iterator<Map.Entry<Integer, Double>> ki = KNUCKLES.entrySet().iterator();
        while (ki.hasNext()) {   // fists of fire
            Map.Entry<Integer, Double> b = ki.next();
            Entity e = mc.level.getEntity(b.getKey());
            if (e == null || now > b.getValue()) { ki.remove(); continue; }
            if (e == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            double yaw = Math.toRadians(e instanceof net.minecraft.world.entity.LivingEntity le ? le.yBodyRot : e.getYRot());
            Vec3 fw = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), lf = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
            for (int s = -1; s <= 1; s += 2) {
                Vec3 hand = e.position().add(0, e.getBbHeight() * 0.42, 0).add(lf.scale(0.38 * s)).add(fw.scale(0.1));
                flame(hand.add(rr.nextDouble(-0.06, 0.06), 0, rr.nextDouble(-0.06, 0.06)), new Vec3(0, 0.025, 0), 0.12F, rr.nextInt(6, 10));
            }
        }
        Iterator<Map.Entry<Integer, Double>> bi = BODY.entrySet().iterator();
        while (bi.hasNext()) {
            Map.Entry<Integer, Double> b = bi.next();
            Entity e = mc.level.getEntity(b.getKey());
            if (e == null || now > b.getValue()) { bi.remove(); continue; }
            if (e.isInvisible()) continue;
            if (e == mc.player && mc.options.getCameraType().isFirstPerson() && ThreadLocalRandom.current().nextInt(3) != 0) continue;
            bodyMote(e, 1.0);
            if (ThreadLocalRandom.current().nextInt(4) == 0) bodyMote(e, 1.0);
        }
    }

    public static void render(PoseStack ps, Camera camera, MultiBufferSource.BufferSource buf, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || PUFFS.isEmpty()) return;
        Vec3 cam = camera.getPosition();
        double now = mc.level.getGameTime() + pt;
        Vector3f lf = camera.getLeftVector(), up = camera.getUpVector();
        Vec3 L = new Vec3(lf.x(), lf.y(), lf.z()), U = new Vec3(up.x(), up.y(), up.z());
        List<Puff> sorted = new ArrayList<>(PUFFS);
        sorted.sort((a, b) -> Double.compare(d2(b, cam), d2(a, cam)));   // far to near
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        RenderType st = RenderType.entityTranslucentEmissive(SMOKE), mt = RenderType.entityTranslucentEmissive(MOTE);
        VertexConsumer vs = buf.getBuffer(st);
        for (Puff p : sorted) {
            if (p.mote) continue;
            draw(vs, m, n, cam, L, U, p, now, pt);
        }
        buf.endBatch(st);
        VertexConsumer vm = buf.getBuffer(mt);
        for (Puff p : sorted) {
            if (!p.mote) continue;
            draw(vm, m, n, cam, L, U, p, now, pt);
        }
        buf.endBatch(mt);
    }

    private static double d2(Puff p, Vec3 cam) {
        double dx = p.x - cam.x, dy = p.y - cam.y, dz = p.z - cam.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static void draw(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 L, Vec3 U, Puff p, double now, float pt) {
        double age = now - p.born;
        if (age < 0 || age > p.life) return;
        double t = age / p.life;
        Vec3 c = new Vec3(p.x + p.vx * pt, p.y + p.vy * pt, p.z + p.vz * pt);
        double dist = c.distanceTo(cam);
        float near = (float) Mth.clamp((dist - 0.6) / 1.8, 0.0, 1.0);   // never smother the camera
        if (near <= 0.01F) return;
        float size = (float) (p.s0 + (p.s1 - p.s0) * (1.0 - Math.pow(1.0 - t, 2.0)));
        float a, cr, cg, cb;
        if (p.streak) {
            a = (float) (Mth.clamp(age / 2.0, 0.0, 1.0) * Math.pow(1.0 - t, 1.2)) * 0.7F * near;
            if (a <= 0.01F) return;
            Vec3 vel = new Vec3(p.vx, p.vy, p.vz);
            if (vel.lengthSqr() < 1.0E-6) vel = new Vec3(0, 0.01, 0);
            Vec3 ax = vel.normalize().scale(size * 3.5 + vel.length() * 2.5);
            Vec3 side = ax.cross(c.subtract(cam));
            if (side.lengthSqr() < 1.0E-8) return;
            side = side.normalize().scale(size * 0.35);
            float sr = p.tinted ? p.tr : 0.78F + 0.2F * p.shade, sg = p.tinted ? p.tg : 1.0F, sb = p.tinted ? p.tb : 0.88F + 0.1F * p.shade;
            v(vc, m, n, cam, c.subtract(ax).subtract(side), sr, sg, sb, a, 0, 1);
            v(vc, m, n, cam, c.add(ax).subtract(side), sr, sg, sb, a, 1, 1);
            v(vc, m, n, cam, c.add(ax).add(side), sr, sg, sb, a, 1, 0);
            v(vc, m, n, cam, c.subtract(ax).add(side), sr, sg, sb, a, 0, 0);
            return;
        }
        if (p.flame) {
            a = (float) (Mth.clamp(age / 1.5, 0.0, 1.0) * Math.pow(1.0 - t, 0.9)) * 0.9F;
            if (p.tinted) {   // blue flame
                if (t < 0.35) { float k = (float) (t / 0.35); cr = 0.85F - 0.6F * k; cg = 0.97F - 0.37F * k; cb = 1.0F; }
                else { float k = (float) ((t - 0.35) / 0.65); cr = 0.25F - 0.23F * k; cg = 0.6F - 0.55F * k; cb = 1.0F - 0.7F * k; a *= 1.0F - 0.5F * k; }
            } else
            if (t < 0.3) { float k = (float) (t / 0.3); cr = 1.0F; cg = 0.95F - 0.4F * k; cb = 0.6F - 0.48F * k; }
            else if (t < 0.65) { float k = (float) ((t - 0.3) / 0.35); cr = 1.0F - 0.45F * k; cg = 0.55F - 0.45F * k; cb = 0.12F - 0.1F * k; }
            else { float k = (float) ((t - 0.65) / 0.35); cr = 0.55F - 0.47F * k; cg = 0.1F - 0.04F * k; cb = 0.02F + 0.03F * k; a *= 0.75F; }
        } else if (p.bead) {
            a = (float) Math.pow(1.0 - t, 0.5) * 0.95F;
            float sh = 0.75F + 0.25F * (float) Math.sin(now * 0.9 + p.shade * 7);
            cr = 0.82F * sh; cg = 0.86F * sh; cb = 0.95F * sh;
            if (p.tinted) { cr = p.tr; cg = p.tg; cb = p.tb; a = (float) Math.pow(1.0 - t, 0.8); }
        } else if (p.mote) {
            a = (float) (Mth.clamp(age / 3.0, 0.0, 1.0) * Math.pow(1.0 - t, 0.7)) * (0.75F + 0.25F * (float) Math.sin(now * 0.8 + p.shade * 9));
            cr = 0.62F + 0.3F * p.shade; cg = 0.22F + 0.2F * p.shade; cb = 1.0F;
        } else {
            a = (float) (Mth.clamp(age / 2.5, 0.0, 1.0) * Math.pow(1.0 - t, 1.3)) * 0.85F;
            // near-black with a faint violet tint in the youngest puffs
            float tint = (float) Math.max(0.0, 1.0 - t * 2.5) * 0.14F;
            cr = 0.015F + tint * 0.55F; cg = 0.0F + tint * 0.12F; cb = 0.035F + tint;
        }
        a *= near;
        if (a <= 0.01F) return;
        float cs = Mth.cos(p.rot), sn = Mth.sin(p.rot);
        Vec3 ax = L.scale(cs).add(U.scale(sn)).scale(size), ay = L.scale(-sn).add(U.scale(cs)).scale(size);
        v(vc, m, n, cam, c.subtract(ax).subtract(ay), cr, cg, cb, a, 0, 1);
        v(vc, m, n, cam, c.add(ax).subtract(ay), cr, cg, cb, a, 1, 1);
        v(vc, m, n, cam, c.add(ax).add(ay), cr, cg, cb, a, 1, 0);
        v(vc, m, n, cam, c.subtract(ax).add(ay), cr, cg, cb, a, 0, 0);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 w, float r, float g, float b, float a, float u, float vv) {
        vc.vertex(m, (float) (w.x - cam.x), (float) (w.y - cam.y), (float) (w.z - cam.z))
                .color(r, g, b, Mth.clamp(a, 0.0F, 1.0F)).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(15728880).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }
}
