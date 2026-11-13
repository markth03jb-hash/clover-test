package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The Void Grimoire, drawn: only black and white. Holes of solid black with hard white edges, as though the world
 * had been cut out there; things flaking away into black squares that drift off and vanish; white motes of Nothing
 * drifting into you. And on your screen, while the book is equipped, the Nothing it keeps: a ring that fills with
 * white from the middle. Fed by FxPacket shape 90 (server/VoidFx), {@code h} = kind: 0 wedge, 1 null step, 2 unmade,
 * 3 nullify, 4 seam, 5 absence, 6 lance, 7 delete, 8 Emptiness, 9 Origin Zero, 10 absorbed Nothing, 11 erased,
 * 99 the meter.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class VoidFxClient {
    private VoidFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] BLACK = {0, 0, 0}, WHITE = {1, 1, 1}, GREY = {0.55F, 0.55F, 0.6F};

    private static final class L {
        final FxPacket p;
        final double born;
        L(FxPacket p, double born) { this.p = p; this.born = born; }
    }

    private static final List<L> LIVE = new ArrayList<>();
    private static float nothing, shown = -1;
    private static boolean equipped;

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if ((int) p.h == 99) { nothing = p.a0; equipped = p.r > 0.5F; return; }
        if (LIVE.size() > 300) LIVE.remove(0);
        LIVE.add(new L(p, mc.level.getGameTime()));
    }

    static void clear() { LIVE.clear(); }

    /** The Nothing meter, where the Blood Gauge would be: a white ring filling with white from its middle. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onGui(net.minecraftforge.client.event.RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !equipped) return;
        var g = e.getGuiGraphics();
        float f = Mth.clamp(nothing / 100F, 0, 1);
        shown = shown < 0 ? f : shown + (f - shown) * 0.15F;
        int R = 11;
        int cx = g.guiWidth() / 2 - 91 - 29 - R - 8, cy = g.guiHeight() - R - 6;
        double t = mc.level.getGameTime() + e.getPartialTick();
        double fill = Math.sqrt(shown) * (R - 2);
        for (int y = -R; y <= R; y++) {
            int hw = (int) Math.round(Math.sqrt(Math.max(0, R * R - y * y)));
            g.fill(cx - hw, cy + y, cx + hw + 1, cy + y + 1, 0xFF000000);   // the black disc
            int iw = (int) Math.round(Math.sqrt(Math.max(0, fill * fill - y * y)));
            if (Math.abs(y) <= fill) g.fill(cx - iw, cy + y, cx + iw + 1, cy + y + 1, 0xFFFFFFFF);   // Nothing, white from the middle
        }
        for (int i = 0; i < 48; i++) {   // the white rim
            double an = i * TAU / 48;
            int x = cx + (int) Math.round(Math.cos(an) * R), y = cy + (int) Math.round(Math.sin(an) * R);
            g.fill(x, y, x + 1, y + 1, 0xFFFFFFFF);
        }
        if (shown >= 0.999F && (int) (t / 6) % 2 == 0) g.fill(cx - 1, cy - R - 3, cx + 2, cy - R - 2, 0xFFFFFFFF);
        String label = (int) (shown * 100 + 0.5) + "";
        g.drawString(mc.font, label, cx - mc.font.width(label) / 2, cy - R - 11, 0xFFEFEFEF, true);
    }

    static void draw(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, double now, float pt) {
        if (LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { LIVE.clear(); return; }
        List<L> live = new ArrayList<>(LIVE);
        for (L l : live) {
            double age = now - l.born;
            if (age > l.p.life) { LIVE.remove(l); continue; }
            FxPacket p = l.p;
            switch ((int) p.h) {
                case 0 -> wedge(vc, m, n, cam, p, age);
                case 1 -> blink(vc, m, n, cam, p, age);
                case 2 -> unmade(vc, m, n, cam, mc, p, age, now, pt);
                case 3 -> nullRing(vc, m, n, cam, p, age);
                case 4 -> seam(vc, m, n, cam, p, age);
                case 5 -> absence(vc, m, n, cam, mc, p, age, now, pt);
                case 6 -> lance(vc, m, n, cam, p, age, now);
                case 7 -> delete(m, cam, mc, p, age, pt);
                case 8 -> aura(vc, m, n, cam, mc, p, age, now, pt);
                case 9 -> origin(vc, m, n, cam, p, age);
                case 10 -> absorb(m, cam, mc, p, age, pt);
                case 11 -> erased(vc, m, n, cam, p, age);
                default -> LIVE.remove(l);
            }
        }
    }

    // ================================================================== black, edged in white
    /** A black square facing you. */
    static void hole(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double s, float a) {
        Vec3 view = c.subtract(cam);
        if (view.lengthSqr() < 1e-6) return;
        Vec3[] f = frame(view.normalize());
        Vec3 x = f[0].scale(s), y = f[1].scale(s);
        FxBase.quad(vc, m, n, cam, c.subtract(x).subtract(y), c.add(x).subtract(y), c.add(x).add(y), c.subtract(x).add(y), BLACK, a, a, a, a);
    }

    /** A disc of black facing you, edged in white. */
    static void blackDisc(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double r, float a, double edge) {
        Vec3 view = c.subtract(cam);
        if (view.lengthSqr() < 1e-6 || r <= 0.01) return;
        Vec3[] f = frame(view.normalize());
        for (int i = 0; i < 24; i++) {
            double a0 = i * TAU / 24, a1 = (i + 1) * TAU / 24;
            FxBase.quad(vc, m, n, cam, c, c, c.add(f[0].scale(Math.cos(a1) * r)).add(f[1].scale(Math.sin(a1) * r)), c.add(f[0].scale(Math.cos(a0) * r)).add(f[1].scale(Math.sin(a0) * r)), BLACK, a, a, a, a);
        }
        GravityFxClient.bring(m, cam, c, f, r, edge, WHITE, 0.95F * a);
    }

    // ================================================================== 0 wedge
    private static void wedge(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double k = Mth.clamp(age / 4, 0, 1);
        float a = (float) Mth.clamp((p.life - age) / 8, 0, 1);
        Vec3 d = p.d.normalize();
        Vec3 side = d.cross(UP);
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        double R = p.r * k;
        int N = 10;
        Vec3 prev = null;
        for (int i = 0; i <= N; i++) {   // a flat wedge of black, cut clean
            double an = -p.a0 + 2 * p.a0 * i / N;
            Vec3 q = p.c.add(d.scale(Math.cos(an) * R)).add(side.scale(Math.sin(an) * R));
            if (prev != null) {
                FxBase.quad(vc, m, n, cam, p.c, p.c, q, prev, BLACK, 0.85F * a, 0.85F * a, 0.85F * a, 0.85F * a);
                FxBase.quad(vc, m, n, cam, p.c, p.c, prev, q, BLACK, 0.85F * a, 0.85F * a, 0.85F * a, 0.85F * a);
                GravityFxClient.gseg(m, cam, prev, q, 0.03, a, a, WHITE);
            }
            prev = q;
        }
        GravityFxClient.gseg(m, cam, p.c, p.c.add(d.scale(Math.cos(-p.a0) * R)).add(side.scale(Math.sin(-p.a0) * R)), 0.03, a, a, WHITE);
        GravityFxClient.gseg(m, cam, p.c, p.c.add(d.scale(Math.cos(p.a0) * R)).add(side.scale(Math.sin(p.a0) * R)), 0.03, a, a, WHITE);
        if (age > 4) for (int i = 0; i < 12; i++) {   // and it comes apart into nothing
            double an = (FxBase.h2(p.id, i, 1) - 0.5) * 2 * p.a0, rr = R * FxBase.h2(p.id, i, 2);
            Vec3 q = p.c.add(d.scale(Math.cos(an) * rr)).add(side.scale(Math.sin(an) * rr)).add(0, (age - 4) * 0.05, 0);
            hole(vc, m, n, cam, q, 0.08 + 0.08 * FxBase.h2(p.id, i, 3), a);
        }
    }

    // ================================================================== 1 null step
    private static void blink(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        float a = (float) (1 - age / p.life);
        silhouette(vc, m, n, cam, p.c, p.r, a);
        silhouette(vc, m, n, cam, p.u, p.r, (float) Mth.clamp(1 - age / 4, 0, 1));
        GravityFxClient.gseg(m, cam, p.c.add(0, p.r * 0.5, 0), p.u.add(0, p.r * 0.5, 0), 0.02, a, a, WHITE);
    }

    /** A person-shaped hole: black, edged in white. */
    private static void silhouette(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 f, double h, float a) {
        if (a <= 0.01F) return;
        Vec3 view = f.add(0, h / 2, 0).subtract(cam);
        Vec3 right = new Vec3(-view.z, 0, view.x);
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        double[][] parts = {{0, 0.36, 0.13}, {0.36, 0.74, 0.25}, {0.76, 0.98, 0.14}};   // legs, body, head
        for (double[] pr : parts) {
            Vec3 b = f.add(0, h * pr[0], 0), t = f.add(0, h * pr[1], 0), w = right.scale(pr[2] * h / 1.8 * 1.6);
            FxBase.quad(vc, m, n, cam, b.subtract(w), b.add(w), t.add(w), t.subtract(w), BLACK, a, a, a, a);
            FxBase.quad(vc, m, n, cam, b.add(w), b.subtract(w), t.subtract(w), t.add(w), BLACK, a, a, a, a);
            GravityFxClient.gseg(m, cam, b.subtract(w), t.subtract(w), 0.02, a, a, WHITE);
            GravityFxClient.gseg(m, cam, b.add(w), t.add(w), 0.02, a, a, WHITE);
        }
    }

    // ================================================================== 2 unmade: cracks, and flakes of nothing peeling off
    private static void unmade(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        float a = (float) (Mth.clamp(age / 3, 0, 1) * Mth.clamp((p.life - age) / 10, 0, 1));
        double H = e.getBbHeight(), W = e.getBbWidth() * 0.55 + 0.05;
        Vec3 view = f.add(0, H / 2, 0).subtract(cam);
        Vec3 right = new Vec3(-view.z, 0, view.x);
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 toward = view.normalize().scale(-W);
        for (int c = 0; c < 3; c++) {   // white cracks across it
            Vec3 prev = f.add(toward).add(right.scale((FxBase.h2(p.id, c, 4) - 0.5) * W * 1.4)).add(0, H * (0.2 + 0.25 * c), 0);
            for (int j = 0; j < 4; j++) {
                Vec3 q = prev.add(right.scale((FxBase.h2(p.id, c * 9 + j, 5) - 0.5) * 0.5)).add(0, 0.12 + 0.1 * FxBase.h2(p.id, c * 9 + j, 6), 0);
                GravityFxClient.gseg(m, cam, prev, q, 0.025, a, a, WHITE);
                prev = q;
            }
        }
        for (int i = 0; i < 8; i++) {   // flakes peeling off and drifting up
            double ph = (now / 18.0 + FxBase.h2(p.id, i, 7)) % 1.0;
            Vec3 q = f.add(right.scale((FxBase.h2(p.id, i, 8) - 0.5) * W * 2)).add(toward.scale(0.9)).add(0, H * (0.15 + 0.7 * FxBase.h2(p.id, i, 9)) + ph * 0.9, 0);
            hole(vc, m, n, cam, q, 0.07 * (1 - ph * 0.6), a * (float) (1 - ph));
        }
    }

    // ================================================================== 3 nullify
    private static void nullRing(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double k = Mth.clamp(age / p.life, 0, 1), e = 1 - Math.pow(1 - k, 2.5);
        float a = (float) (1 - k);
        double r = p.r * e + 0.2;
        Vec3 g = p.c.add(0, -0.95, 0);
        int seg = 36;
        for (int i = 0; i < seg; i++) {   // a black band rolling out over the ground, white at both edges
            double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            Vec3 i0 = g.add(Math.cos(a0) * (r - 0.4), 0.03, Math.sin(a0) * (r - 0.4)), i1 = g.add(Math.cos(a1) * (r - 0.4), 0.03, Math.sin(a1) * (r - 0.4));
            Vec3 o0 = g.add(Math.cos(a0) * r, 0.03, Math.sin(a0) * r), o1 = g.add(Math.cos(a1) * r, 0.03, Math.sin(a1) * r);
            FxBase.quad(vc, m, n, cam, i0, i1, o1, o0, BLACK, 0.9F * a, 0.9F * a, 0.9F * a, 0.9F * a);
            FxBase.quad(vc, m, n, cam, o0, o1, i1, i0, BLACK, 0.9F * a, 0.9F * a, 0.9F * a, 0.9F * a);
        }
        GravityFxClient.ring(m, cam, g.add(0, 0.04, 0), UP, r, 0.03, WHITE, a);
        GravityFxClient.ring(m, cam, g.add(0, 0.04, 0), UP, r - 0.4, 0.03, WHITE, a);
        Vec3 view = p.c.subtract(cam);
        if (view.lengthSqr() > 1e-6) GravityFxClient.bring(m, cam, p.c, frame(view.normalize()), r, 0.05, WHITE, 0.6F * a);
    }

    // ================================================================== 4 seam
    private static void seam(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double snap = Math.max(1, p.sweep);
        double w = age < snap ? 0.55 * Math.min(1, age / 4) : 0.55 * Math.max(0, 1 - (age - snap) / 2);
        Vec3 ax = p.u.subtract(p.c);
        Vec3 s = ax.cross(p.c.subtract(cam));
        if (s.lengthSqr() < 1e-8) return;
        s = s.normalize().scale(w);
        if (w > 0.01) {   // a slice of nothing, opening
            Vec3 mid = p.c.add(ax.scale(0.5));
            FxBase.quad(vc, m, n, cam, p.c, mid.add(s), p.u, mid.subtract(s), BLACK, 1, 1, 1, 1);
            FxBase.quad(vc, m, n, cam, p.c, mid.subtract(s), p.u, mid.add(s), BLACK, 1, 1, 1, 1);
            GravityFxClient.gseg(m, cam, p.c, mid.add(s), 0.025, 1, 1, WHITE);
            GravityFxClient.gseg(m, cam, mid.add(s), p.u, 0.025, 1, 1, WHITE);
            GravityFxClient.gseg(m, cam, p.c, mid.subtract(s), 0.025, 1, 1, WHITE);
            GravityFxClient.gseg(m, cam, mid.subtract(s), p.u, 0.025, 1, 1, WHITE);
        }
        if (age >= snap) {   // and it snaps shut: a white line, gone
            float a = (float) Mth.clamp(1 - (age - snap) / (p.life - snap), 0, 1);
            GravityFxClient.gseg(m, cam, p.c, p.u, 0.06 + 0.2 * a, a, a, WHITE);
        }
    }

    // ================================================================== 5 absence
    private static void absence(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 c = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()) + 1, Mth.lerp(pt, e.zo, e.getZ()));
        float a = (float) (Mth.clamp(age / 6, 0, 1) * Mth.clamp((p.life - age) / 10, 0, 1));
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        double R = p.r;
        for (int i = 1; i < 6; i++) {
            double lat = -Math.PI / 2 + i * Math.PI / 6;
            GravityFxClient.ring(m, cam, c.add(0, Math.sin(lat) * R, 0), UP, Math.cos(lat) * R, 0.025, WHITE, (self ? 0.25F : 0.55F) * a);
        }
        if (self) return;
        Vec3 view = c.subtract(cam);
        if (view.lengthSqr() > 1e-6) GravityFxClient.bring(m, cam, c, frame(view.normalize()), R, 0.05, WHITE, 0.9F * a);
        for (int i = 0; i < 10; i++) {   // flecks of nothing drifting inside it
            double an = FxBase.h2(p.id, i, 12) * TAU + now * 0.02, rr = R * 0.8 * FxBase.h2(p.id, i, 13);
            hole(vc, m, n, cam, c.add(Math.cos(an) * rr, (FxBase.h2(p.id, i, 14) - 0.5) * R, Math.sin(an) * rr), 0.06, 0.8F * a);
        }
    }

    // ================================================================== 6 lance
    private static void lance(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        Vec3 ax = p.u.subtract(p.c);
        double L = ax.length();
        if (L < 1e-3) return;
        Vec3 d = ax.scale(1 / L);
        float a = (float) Mth.clamp((p.life - age) / 8, 0, 1);
        double reach = Math.min(1, age / 2.0), w = p.r * (age < 2 ? 1 : 1 - 0.5 * (age - 2) / (p.life - 2));
        Vec3 tip = p.c.add(d.scale(L * reach));
        Vec3 s = d.cross(p.c.subtract(cam));
        if (s.lengthSqr() < 1e-8) return;
        s = s.normalize();
        FxBase.quad(vc, m, n, cam, p.c.add(s.scale(w * 0.5)), tip.add(s.scale(w * 0.5)), tip.subtract(s.scale(w * 0.5)), p.c.subtract(s.scale(w * 0.5)), BLACK, a, a, a, a);
        FxBase.quad(vc, m, n, cam, p.c.subtract(s.scale(w * 0.5)), tip.subtract(s.scale(w * 0.5)), tip.add(s.scale(w * 0.5)), p.c.add(s.scale(w * 0.5)), BLACK, a, a, a, a);
        GravityFxClient.gseg(m, cam, p.c.add(s.scale(w * 0.5)), tip.add(s.scale(w * 0.5)), 0.03, a, a, WHITE);
        GravityFxClient.gseg(m, cam, p.c.subtract(s.scale(w * 0.5)), tip.subtract(s.scale(w * 0.5)), 0.03, a, a, WHITE);
        for (int i = 0; i < (int) (L / 3); i++) {   // black rings with white rims sliding down it
            double x = (i * 3 + age * 1.5) % L;
            if (x > L * reach) continue;
            GravityFxClient.ring(m, cam, p.c.add(d.scale(x)), d, w * 1.2, 0.03, WHITE, 0.8F * a);
        }
    }

    // ================================================================== 7 delete: white brackets closing in
    private static void delete(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        double wait = Math.max(1, p.sweep);
        double k = Mth.clamp(age / wait, 0, 1);
        float a = (float) Mth.clamp((p.life - age) / 6, 0, 1) * (age > wait - 10 && (int) (age * 2) % 2 == 0 ? 0.4F : 1F);
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        Vec3 c = f.add(0, e.getBbHeight() / 2, 0);
        Vec3 view = c.subtract(cam);
        if (view.lengthSqr() < 1e-6) return;
        Vec3 right = new Vec3(-view.z, 0, view.x).normalize();
        double s = 1.7 - 0.6 * k;
        double hw = (e.getBbWidth() * 0.5 + 0.25) * s, hh = (e.getBbHeight() * 0.5 + 0.2) * s, arm = 0.3;
        for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) {
            Vec3 corner = c.add(right.scale(sx * hw)).add(0, sy * hh, 0);
            GravityFxClient.gseg(m, cam, corner, corner.add(right.scale(-sx * arm)), 0.035, a, a, WHITE);
            GravityFxClient.gseg(m, cam, corner, corner.add(0, -sy * arm, 0), 0.035, a, a, WHITE);
        }
        if (age > wait) GravityFxClient.gglow(m, cam, c, 1.2 * (1 + (age - wait) / 6), (float) Mth.clamp(1 - (age - wait) / 8, 0, 1) * 0.9F, WHITE);
    }

    // ================================================================== 8 Emptiness
    private static void aura(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        float a = (float) (Mth.clamp(age / 10, 0, 1) * Mth.clamp((p.life - age) / 20, 0, 1));
        GravityFxClient.ring(m, cam, f.add(0, 0.05, 0), UP, 1.0, 0.03, WHITE, 0.8F * a);
        if (e == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        for (int i = 0; i < 12; i++) {   // pieces of you missing, drifting round
            double an = now * 0.04 + i * TAU / 12, rr = 0.55 + 0.15 * Math.sin(now * 0.1 + i);
            Vec3 q = f.add(Math.cos(an) * rr, e.getBbHeight() * (0.15 + 0.75 * FxBase.h2(i, 3, 15)), Math.sin(an) * rr);
            hole(vc, m, n, cam, q, 0.07 + 0.04 * FxBase.h2(i, 4, 15), 0.9F * a);
            GravityFxClient.gglow(m, cam, q, 0.12, 0.25F * a, WHITE);
        }
    }

    // ================================================================== 9 Origin Zero
    private static void origin(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double grow = Math.max(1, p.sweep);
        if (age < grow) {   // Nothing gathering: a black sphere edged in white, swelling
            double k = age / grow;
            blackDisc(vc, m, n, cam, p.c, p.r * 0.5 * k * k + 0.2, 1F, 0.08);
            GravityFxClient.gglow(m, cam, p.c, p.r * 0.8 * k + 0.4, 0.3F, WHITE);
            for (int i = 0; i < 16; i++) {   // the world being drawn into it
                double an = i * TAU / 16 + age * 0.08, rr = p.r * (1.6 - k);
                Vec3 q = p.c.add(Math.cos(an) * rr, (FxBase.h2(p.id, i, 16) - 0.5) * p.r, Math.sin(an) * rr);
                GravityFxClient.gseg(m, cam, q, q.add(p.c.subtract(q).normalize().scale(0.8)), 0.04, 0.9F, 0, WHITE);
            }
            return;
        }
        double k = Mth.clamp((age - grow) / (p.life - grow), 0, 1);   // and it lets go: everything goes white, then black falls back to nothing
        GravityFxClient.gglow(m, cam, p.c, p.r * (1 + 0.3 * k), (float) (1 - k) * 0.95F, WHITE);
        blackDisc(vc, m, n, cam, p.c, p.r * 0.5 * (1 - k), (float) (1 - k), 0.06);
        GravityFxClient.ring(m, cam, p.c.add(0, -0.95, 0), UP, p.r * (0.9 + 0.4 * k), 0.1, WHITE, (float) (1 - k));
    }

    // ================================================================== 10 Nothing drifting into you
    private static void absorb(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 to = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()) + e.getBbHeight() * 0.55, Mth.lerp(pt, e.zo, e.getZ()));
        double k = Mth.clamp(age / p.life, 0, 1);
        for (int i = 0; i < 5; i++) {
            double kk = Mth.clamp(k * 1.3 - i * 0.07, 0, 1);
            Vec3 q = p.c.add(to.subtract(p.c).scale(kk * kk)).add(0, Math.sin(kk * Math.PI) * 0.8, 0);
            GravityFxClient.gglow(m, cam, q, 0.08, (float) (1 - kk) * 0.9F, WHITE);
        }
    }

    // ================================================================== 11 erased: flaking away to nothing
    private static void erased(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        double k = Mth.clamp(age / p.life, 0, 1);
        double H = Math.max(0.3, p.r), W = Math.max(0.3, p.r2);
        int N = (int) Math.min(40, 6 + H * W * 20);
        for (int i = 0; i < N; i++) {
            double start = FxBase.h2(p.id, i, 17) * 0.5;
            double q = Mth.clamp((k - start) / (1 - start), 0, 1);
            Vec3 at = p.c.add((FxBase.h2(p.id, i, 18) - 0.5) * W, FxBase.h2(p.id, i, 19) * H + q * 1.2, (FxBase.h2(p.id, i, 20) - 0.5) * W).add((FxBase.h2(p.id, i, 21) - 0.5) * q, 0, (FxBase.h2(p.id, i, 22) - 0.5) * q);
            hole(vc, m, n, cam, at, 0.09 * (1 - q * 0.7), (float) (1 - q));
            if (q > 0 && q < 0.4) GravityFxClient.gglow(m, cam, at, 0.12, (float) (0.4 - q) * 2F, WHITE);
        }
    }

    private static Vec3[] frame(Vec3 axis) {
        Vec3 e1 = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        e1 = e1.subtract(axis.scale(e1.dot(axis))).normalize();
        return new Vec3[]{e1, axis.cross(e1)};
    }
}
