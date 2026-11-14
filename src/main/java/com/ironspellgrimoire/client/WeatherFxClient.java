package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The Weather Grimoire, drawn: your own little sky. Soft clouds built of puffs, rain in pale streaks with rings where
 * it lands, jagged white lightning with blue glow, hard gold sunbeams, hailstones bouncing, ribbons of wind, a funnel
 * of cyclone, a rainbow, and a ring of storm round a calm eye. Over the head of anyone carrying the book hangs a small
 * cloud of their forecast; on their own screen a forecast symbol sits beside the hotbar.
 * Fed by FxPacket shape 95 (server/WeatherFx), {@code h} = kind: 0 sunbeam, 1 raincloud, 2 bolt, 3 hail, 4 gale,
 * 5 fog, 6 cyclone, 7 rainbow, 8 eye of the storm, 9 cataclysm, 10 personal cloud, 11 splash, 99 the forecast.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class WeatherFxClient {
    private WeatherFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] CLOUD = {0.92F, 0.94F, 0.97F}, GREY = {0.55F, 0.58F, 0.64F}, DARK = {0.25F, 0.27F, 0.33F}, RAINC = {0.6F, 0.78F, 1.0F},
            BOLT = {0.75F, 0.85F, 1.0F}, WHITE = {1, 1, 1}, SUN = {1.0F, 0.82F, 0.35F}, SUN_W = {1.0F, 0.96F, 0.8F}, ICE = {0.85F, 0.95F, 1.0F},
            WIND = {0.85F, 1.0F, 0.92F}, FOGC = {0.82F, 0.84F, 0.88F};
    static final float[][] BOW = {{1, 0.25F, 0.25F}, {1, 0.6F, 0.15F}, {1, 0.95F, 0.3F}, {0.35F, 0.95F, 0.4F}, {0.3F, 0.6F, 1}, {0.6F, 0.35F, 1}};

    private static final class L {
        final FxPacket p;
        final double born;
        L(FxPacket p, double born) { this.p = p; this.born = born; }
    }

    private static final List<L> LIVE = new ArrayList<>();
    private static int forecast;
    private static boolean equipped;
    private static double changed = -100;

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if ((int) p.h == 98) { LIVE.removeIf(l -> (int) l.p.v.x == (int) p.v.x); return; }   // a mode was ended early: drop what hangs on that player
        if ((int) p.h == 99) {
            int f = Mth.clamp((int) p.a0, 0, 5);
            if (f != forecast) changed = mc.level.getGameTime();
            forecast = f; equipped = p.r > 0.5F;
            return;
        }
        if ((int) p.h == 10) LIVE.removeIf(l -> (int) l.p.h == 10 && (int) l.p.v.x == (int) p.v.x);   // one little sky each
        if (LIVE.size() > 300) LIVE.remove(0);
        LIVE.add(new L(p, mc.level.getGameTime()));
    }

    /** Checked on the client every frame, so the HUD goes the moment you switch to another grimoire (the server's sync can lag up to 2 s). */
    private static boolean held(Minecraft mc) {
        var b = mc.player == null ? null : io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(mc.player);
        return b != null && b.is(com.ironspellgrimoire.registry.AddonItemRegistry.WEATHER_GRIMOIRE.get());
    }

    static void clear() { LIVE.clear(); }

    // ================================================================== the forecast beside the hotbar
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onGui(net.minecraftforge.client.event.RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !equipped || !held(mc)) return;
        var g = e.getGuiGraphics();
        double t = mc.level.getGameTime() + e.getPartialTick();
        int cx = g.guiWidth() / 2 - 91 - 29 - 18, cy = g.guiHeight() - 16;
        double pop = Mth.clamp((t - changed) / 8, 0, 1);
        g.fill(cx - 13, cy - 13, cx + 14, cy + 14, 0x66101820);
        switch (forecast) {
            case 0 -> {   // sun: a disc and turning rays
                disc(g, cx, cy, 5, 0xFFFFD34A);
                for (int i = 0; i < 8; i++) {
                    double an = i * TAU / 8 + t * 0.03;
                    for (int r = 7; r <= 10; r++) px(g, cx + (int) Math.round(Math.cos(an) * r), cy + (int) Math.round(Math.sin(an) * r), 0xFFFFC23A);
                }
            }
            case 1 -> { cloud(g, cx, cy - 3, 0xFFE8EEF5); for (int i = 0; i < 4; i++) { int x = cx - 6 + i * 4, y = cy + 3 + (int) ((t * 0.6 + i * 3) % 7); g.fill(x, y, x + 1, y + 3, 0xFF7FB8FF); } }
            case 2 -> {
                cloud(g, cx, cy - 4, 0xFF4A5060);
                boolean flash = (int) (t / 4) % 6 == 0;
                int c = flash ? 0xFFFFFFFF : 0xFFFFE04A;
                g.fill(cx, cy, cx + 2, cy + 4, c); g.fill(cx - 2, cy + 3, cx + 1, cy + 5, c); g.fill(cx - 3, cy + 5, cx - 1, cy + 10, c);
            }
            case 3 -> { cloud(g, cx, cy - 3, 0xFFB8C2D0); for (int i = 0; i < 5; i++) { int x = cx - 7 + i * 3 + (i % 2), y = cy + 3 + (int) ((t * 0.5 + i * 5) % 8); g.fill(x, y, x + 2, y + 2, 0xFFFFFFFF); } }
            case 4 -> {
                for (int k = 0; k < 3; k++) {
                    int y = cy - 6 + k * 6, off = (int) ((t * 0.6 + k * 4) % 6) - 3;
                    for (int x = -9; x <= 7 - k * 2; x++) px(g, cx + x + off, y + (int) Math.round(Math.sin(x * 0.4) * 1.2), 0xFFD8FFE8);
                    px(g, cx + 8 - k * 2 + off, y - 1, 0xFFD8FFE8); px(g, cx + 8 - k * 2 + off, y - 2, 0xFFD8FFE8);
                }
            }
            default -> { for (int k = 0; k < 4; k++) { int off = (int) (Math.sin(t * 0.05 + k) * 2); g.fill(cx - 9 + off + k % 2 * 2, cy - 7 + k * 4, cx + 9 + off - k % 2 * 2, cy - 5 + k * 4, 0xCCC8CCD4); } }
        }
        String label = new String[]{"Sun", "Rain", "Storm", "Hail", "Wind", "Fog"}[forecast];
        int col = new int[]{0xFFFFD34A, 0xFF7FB8FF, 0xFFFFE04A, 0xFFDDF2FF, 0xFFB8FFD0, 0xFFC8CCD4}[forecast];
        if (pop < 1 && (int) (t / 2) % 2 == 0) col = 0xFFFFFFFF;
        g.drawString(mc.font, label, cx - mc.font.width(label) / 2, cy - 25, col, true);
    }

    private static void px(net.minecraft.client.gui.GuiGraphics g, int x, int y, int c) { g.fill(x, y, x + 1, y + 1, c); }

    private static void disc(net.minecraft.client.gui.GuiGraphics g, int cx, int cy, int r, int c) {
        for (int y = -r; y <= r; y++) { int w = (int) Math.round(Math.sqrt(r * r - y * y)); g.fill(cx - w, cy + y, cx + w + 1, cy + y + 1, c); }
    }

    private static void cloud(net.minecraft.client.gui.GuiGraphics g, int cx, int cy, int c) {
        disc(g, cx - 4, cy + 1, 4, c); disc(g, cx + 1, cy - 1, 5, c); disc(g, cx + 6, cy + 1, 4, c); g.fill(cx - 7, cy + 1, cx + 10, cy + 5, c);
    }

    // ================================================================== the world
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
                case 0 -> sunbeam(m, cam, p.c, p.r, age, p.life, p.id, now);
                case 1 -> rain(vc, m, n, cam, p.c, p.r, age, p.life, p.id, now, new float[]{0.74F, 0.77F, 0.83F}, true);
                case 2 -> bolt(m, cam, p, age);
                case 3 -> hail(vc, m, n, cam, p.c, p.r, age, p.life, p.id, now);
                case 4 -> gale(m, cam, p, age, now);
                case 5 -> fog(vc, m, n, cam, mc, p, age, now, pt);
                case 6 -> cyclone(vc, m, n, cam, p, age, now);
                case 7 -> rainbow(m, cam, p, age);
                case 8 -> eye(vc, m, n, cam, mc, p, age, now, pt);
                case 9 -> cataclysm(vc, m, n, cam, p, age, now);
                case 10 -> personal(vc, m, n, cam, mc, p, age, now, pt);
                case 11 -> splash(m, cam, p, age);
                default -> LIVE.remove(l);
            }
        }
    }

    // ================================================================== pieces of sky
    private static Vec3[] frame(Vec3 axis) {
        Vec3 e1 = Math.abs(axis.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        e1 = e1.subtract(axis.scale(e1.dot(axis))).normalize();
        return new Vec3[]{e1, axis.cross(e1)};
    }

    /** A soft puff of cloud facing you: a solid centre fading to its rim. */
    static void puff(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double r, float[] col, float a) {
        Vec3 view = c.subtract(cam);
        if (view.lengthSqr() < 1e-6 || a <= 0.01F) return;
        Vec3[] f = frame(view.normalize());
        int N = 14;
        for (int i = 0; i < N; i++) {
            double a0 = i * TAU / N, a1 = (i + 1) * TAU / N;
            Vec3 p0 = c.add(f[0].scale(Math.cos(a0) * r)).add(f[1].scale(Math.sin(a0) * r)), p1 = c.add(f[0].scale(Math.cos(a1) * r)).add(f[1].scale(Math.sin(a1) * r));
            Vec3 q0 = c.add(p0.subtract(c).scale(0.6)), q1 = c.add(p1.subtract(c).scale(0.6));
            FxBase.quad(vc, m, n, cam, c, c, q1, q0, col, a, a, a, a);
            FxBase.quad(vc, m, n, cam, q0, q1, p1, p0, col, a, a, 0, 0);
        }
    }

    /** A cloud of puffs over c, r wide. */
    static void cloudAt(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double r, int id, double now, float[] col, float a, int count) {
        for (int i = 0; i < count; i++) {
            double an = FxBase.h2(id, i, 20) * TAU, rr = r * Math.sqrt(FxBase.h2(id, i, 21));
            double drift = 0.15 * Math.sin(now * 0.03 + i);
            Vec3 q = c.add(Math.cos(an) * rr + drift, (FxBase.h2(id, i, 22) - 0.3) * r * 0.25, Math.sin(an) * rr);
            if (q.distanceToSqr(cam) < 4) continue;   // never a puff right in your eyes
            float[] shade = i % 3 == 0 ? new float[]{col[0] * 0.85F, col[1] * 0.85F, col[2] * 0.88F} : col;
            puff(vc, m, n, cam, q, r * (0.35 + 0.25 * FxBase.h2(id, i, 23)), shade, a);
        }
    }

    /** A jagged bolt from the sky onto c. */
    static void boltLine(Matrix4f m, Vec3 cam, Vec3 c, double height, int seed, double size, float a) {
        Vec3 prev = c.add(0, height, 0);
        int N = 9;
        for (int i = 1; i <= N; i++) {
            Vec3 q = i == N ? c : c.add((FxBase.h2(seed, i, 1) - 0.5) * 2.2 * size, height * (1 - i / (double) N), (FxBase.h2(seed, i, 2) - 0.5) * 2.2 * size);
            GravityFxClient.gseg(m, cam, prev, q, 0.35 * size, 0.45F * a, 0.45F * a, BOLT);
            GravityFxClient.gseg(m, cam, prev, q, 0.08 * size, a, a, WHITE);
            if (i == 3 || i == 6) {   // a branch
                Vec3 b = q.add((FxBase.h2(seed, i, 3) - 0.5) * 3 * size, -height * 0.15, (FxBase.h2(seed, i, 4) - 0.5) * 3 * size);
                GravityFxClient.gseg(m, cam, q, b, 0.04 * size, a * 0.8F, 0, WHITE);
                GravityFxClient.gseg(m, cam, q, b, 0.18 * size, a * 0.3F, 0, BOLT);
            }
            prev = q;
        }
    }

    private static Vec3 pos(Entity e, float pt) { return new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ())); }

    // ================================================================== 0 sunbeam
    private static void sunbeam(Matrix4f m, Vec3 cam, Vec3 c, double r, double age, double life, int id, double now) {
        float a = (float) (Mth.clamp(age / 2, 0, 1) * Mth.clamp((life - age) / 3, 0, 1));
        GravityFxClient.gseg(m, cam, c.add(0, 22, 0), c, r * 0.9, 0.35F * a, 0.6F * a, SUN);
        GravityFxClient.gseg(m, cam, c.add(0, 22, 0), c, r * 0.3, 0.6F * a, a, SUN_W);
        GravityFxClient.ring(m, cam, c.add(0, 0.06, 0), UP, r, 0.25, SUN, 0.8F * a);
        GravityFxClient.gglow(m, cam, c.add(0, 0.3, 0), r * 1.1, 0.4F * a, SUN);
        for (int i = 0; i < 6; i++) {   // heat motes rising out of it
            double ph = ((now * 0.08 + FxBase.h2(id, i, 5))) % 1.0, an = FxBase.h2(id, i, 6) * TAU;
            Vec3 q = c.add(Math.cos(an) * r * 0.7, ph * 2.5, Math.sin(an) * r * 0.7);
            GravityFxClient.gglow(m, cam, q, 0.08, a * (float) (1 - ph), SUN_W);
        }
    }

    // ================================================================== 1 raincloud
    private static void rain(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double r, double age, double life, int id, double now, float[] col, boolean rings) {
        float a = (float) (Mth.clamp(age / 8, 0, 1) * Mth.clamp((life - age) / 10, 0, 1));
        double H = 5.5;
        cloudAt(vc, m, n, cam, c.add(0, H, 0), r, id, now, col, 0.85F * a, 14);
        for (int i = 0; i < 26; i++) {   // the rain
            double an = FxBase.h2(id, i, 7) * TAU, rr = r * Math.sqrt(FxBase.h2(id, i, 8));
            double ph = ((now / 9.0 + FxBase.h2(id, i, 9))) % 1.0;
            Vec3 q = c.add(Math.cos(an) * rr, H * (1 - ph), Math.sin(an) * rr);
            GravityFxClient.gseg(m, cam, q, q.add(0.04, 0.7, 0), 0.025, 0.7F * a, 0, RAINC);
            if (rings && ph > 0.85) GravityFxClient.ring(m, cam, c.add(Math.cos(an) * rr, 0.05, Math.sin(an) * rr), UP, 0.1 + (ph - 0.85) * 2, 0.03, RAINC, a * (float) (1 - ph) * 5);
        }
    }

    // ================================================================== 2 lightning
    private static void bolt(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        float a = age < 2 ? 1 : (float) Mth.clamp(1 - (age - 2) / (p.life - 2), 0, 1);
        if (age > 3 && age < 5) a *= 0.3F;   // the flicker
        boltLine(m, cam, p.c, 18, p.id, p.r, a);
        GravityFxClient.gglow(m, cam, p.c.add(0, 0.4, 0), 2.2 * p.r, 0.7F * a, BOLT);
        GravityFxClient.ring(m, cam, p.c.add(0, 0.06, 0), UP, 0.5 + age * 0.3, 0.15, WHITE, a);
    }

    // ================================================================== 3 hail
    private static void hail(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double r, double age, double life, int id, double now) {
        float a = (float) (Mth.clamp(age / 6, 0, 1) * Mth.clamp((life - age) / 8, 0, 1));
        double H = 6;
        cloudAt(vc, m, n, cam, c.add(0, H, 0), r, id, now, new float[]{0.7F, 0.74F, 0.82F}, 0.85F * a, 14);
        for (int i = 0; i < 22; i++) {
            double an = FxBase.h2(id, i, 10) * TAU, rr = r * Math.sqrt(FxBase.h2(id, i, 11));
            double per = 10 + 4 * FxBase.h2(id, i, 12), ph = ((now / per + FxBase.h2(id, i, 13))) % 1.2;
            double y = ph < 1 ? H * (1 - ph * ph) : 0.4 * Math.sin((ph - 1) / 0.2 * Math.PI);   // falling, then a little bounce
            Vec3 q = c.add(Math.cos(an) * rr + (ph > 1 ? (ph - 1) * 2 : 0), Math.max(0.05, y), Math.sin(an) * rr);
            MechFxClient.box(vc, m, n, cam, q, new Vec3(1, 0, 0), UP, new Vec3(0, 0, 1), 0.07, 0.07, 0.07, ICE, WHITE, a);
            if (ph < 1) GravityFxClient.gseg(m, cam, q, q.add(0, 0.5, 0), 0.03, 0.4F * a, 0, ICE);
        }
    }

    // ================================================================== 4 gale
    private static void gale(Matrix4f m, Vec3 cam, FxPacket p, double age, double now) {
        float a = (float) (Mth.clamp(age / 3, 0, 1) * Mth.clamp((p.life - age) / 6, 0, 1));
        Vec3 d = p.d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.d.normalize(), side = UP.cross(d).normalize();
        for (int i = 0; i < 14; i++) {   // ribbons of wind racing forward, curling at their heads
            double u = FxBase.h2(p.id, i, 14) * 2 - 1, y = 0.3 + 2.2 * FxBase.h2(p.id, i, 15);
            double ph = ((age / 12.0 + FxBase.h2(p.id, i, 16))) % 1.0;
            int N = 8;
            Vec3[] pts = new Vec3[N];
            double[] w = new double[N];
            float[] al = new float[N];
            for (int k = 0; k < N; k++) {
                double s = ph * p.r - k * 0.35;
                pts[k] = p.c.add(d.scale(Math.max(0, s))).add(side.scale(u * 2.4 + 0.2 * Math.sin(s * 1.3 + i))).add(0, y + 0.15 * Math.sin(s * 2 + i), 0);
                w[k] = 0.05 * (1 - k / (double) N);
                al[k] = a * (1 - k / (float) N) * (float) Math.sin(ph * Math.PI);
            }
            GravityFxClient.gstrip(m, cam, pts, w, al, WIND);
        }
    }

    // ================================================================== 5 fog bank
    private static void fog(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = pos(e, pt);
        float a = (float) (Mth.clamp(age / 15, 0, 1) * Mth.clamp((p.life - age) / 20, 0, 1));
        for (int i = 0; i < 26; i++) {   // low banks of fog rolling round you - thin, so you can still see through
            double an = FxBase.h2(p.id, i, 17) * TAU + now * 0.004 * (i % 2 == 0 ? 1 : -1), rr = 1.5 + (p.r - 1.5) * Math.sqrt(FxBase.h2(p.id, i, 18));
            Vec3 q = f.add(Math.cos(an) * rr, 0.4 + 1.2 * FxBase.h2(p.id, i, 19), Math.sin(an) * rr);
            if (q.distanceToSqr(cam) < 6) continue;
            puff(vc, m, n, cam, q, 1.0 + 0.6 * FxBase.h2(p.id, i, 20), FOGC, 0.22F * a);
        }
    }

    // ================================================================== 6 cyclone
    static void funnel(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double r, double H, int id, double now, float a) {
        int rings = 9;
        for (int i = 0; i < rings; i++) {
            double u = i / (double) (rings - 1), y = H * u, rr = r * (0.25 + 0.9 * u * u);
            Vec3 cc = c.add(0.25 * Math.sin(now * 0.1 + u * 3) * u * r, y, 0.25 * Math.cos(now * 0.1 + u * 3) * u * r);
            for (int k = 0; k < 3; k++) {   // streaks whirling round each band
                double an = now * (0.5 - u * 0.25) + k * TAU / 3 + i;
                Vec3 s0 = cc.add(Math.cos(an) * rr, 0, Math.sin(an) * rr), s1 = cc.add(Math.cos(an + 1.2) * rr, 0.25, Math.sin(an + 1.2) * rr);
                GravityFxClient.gseg(m, cam, s0, s1, 0.08 + 0.1 * u, 0, 0.7F * a, WIND);
            }
            GravityFxClient.ring(m, cam, cc, UP, rr, 0.12, GREY, 0.3F * a);
        }
        for (int i = 0; i < 10; i++) {   // dust and leaves caught in it
            double ph = ((now * 0.03 + FxBase.h2(id, i, 24))) % 1.0, an = now * 0.4 + i * 2.1;
            double rr = r * (0.3 + 0.8 * ph * ph);
            GravityFxClient.gglow(m, cam, c.add(Math.cos(an) * rr, H * ph, Math.sin(an) * rr), 0.1, a * 0.8F, new float[]{0.65F, 0.6F, 0.45F});
        }
        cloudAt(vc, m, n, cam, c.add(0, H + 0.4, 0), r * 1.3, id, now, GREY, 0.75F * a, 8);
    }

    private static void cyclone(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        double k = Mth.clamp(age / p.life, 0, 1);
        float a = (float) (Mth.clamp(age / 5, 0, 1) * Mth.clamp((p.life - age) / 6, 0, 1));
        Vec3 c = p.c.add(p.u.subtract(p.c).scale(k));
        funnel(vc, m, n, cam, c, p.r, 6, p.id, now, a);
        GravityFxClient.ring(m, cam, c.add(0, 0.06, 0), UP, p.r * 1.2, 0.3, WIND, 0.5F * a);
    }

    // ================================================================== 7 rainbow
    private static void rainbow(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        double grow = FxBase.smooth(Mth.clamp(age / 12, 0, 1));
        float a = (float) Mth.clamp((p.life - age) / 15, 0, 1);
        Vec3 d = p.d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.d.normalize(), side = UP.cross(d).normalize();
        int N = 24;
        for (int b = 0; b < 6; b++) {
            double R = p.r - b * 0.22;
            Vec3[] pts = new Vec3[N];
            double[] w = new double[N];
            float[] al = new float[N];
            for (int i = 0; i < N; i++) {
                double th = Math.PI * (i / (double) (N - 1)) * grow;   // it draws itself across the sky
                pts[i] = p.c.add(side.scale(-Math.cos(th) * R)).add(0, Math.sin(th) * R * 0.75, 0);
                w[i] = 0.12;
                al[i] = 0.7F * a * (float) Math.min(1, Math.min(i, N - 1 - i) / 3.0 + 0.2);
            }
            GravityFxClient.gstrip(m, cam, pts, w, al, BOW[b]);
        }
        if (age < 16) GravityFxClient.gglow(m, cam, p.c.add(side.scale(-Math.cos(Math.PI * grow) * p.r)).add(0, Math.sin(Math.PI * grow) * p.r * 0.75, 0), 0.6, a, SUN_W);
    }

    // ================================================================== 8 the eye of the storm
    private static void eye(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = pos(e, pt);
        float a = (float) (Mth.clamp(age / 15, 0, 1) * Mth.clamp((p.life - age) / 20, 0, 1));
        double R = p.r;
        for (int i = 0; i < 14; i++) {   // the wall of cloud turning round you
            double an = i * TAU / 14 + now * 0.02;
            Vec3 q = f.add(Math.cos(an) * R, 3.5 + 0.4 * Math.sin(now * 0.05 + i), Math.sin(an) * R);
            if (q.distanceToSqr(cam) < 4) continue;
            puff(vc, m, n, cam, q, 1.5, i % 2 == 0 ? DARK : GREY, 0.75F * a);
            for (int k = 0; k < 2; k++) {   // its rain
                double ph = ((now / 8.0 + FxBase.h2(p.id, i, k + 25))) % 1.0;
                Vec3 r = f.add(Math.cos(an + k * 0.2) * (R + (k - 0.5)), 3.2 * (1 - ph), Math.sin(an + k * 0.2) * (R + (k - 0.5)));
                GravityFxClient.gseg(m, cam, r, r.add(0, 0.7, 0), 0.025, 0.6F * a, 0, RAINC);
            }
        }
        if ((int) (now / 3) % 17 == (p.id % 17)) GravityFxClient.gglow(m, cam, f.add(Math.cos(now) * R, 3.6, Math.sin(now) * R), 2.5, 0.5F * a, BOLT);   // flashes inside it
        GravityFxClient.ring(m, cam, f.add(0, 0.05, 0), UP, 1.1, 0.12, SUN_W, 0.5F * a);   // the calm in the middle
        GravityFxClient.ring(m, cam, f.add(0, 0.05, 0), UP, R, 0.3, RAINC, 0.35F * a);
    }

    // ================================================================== 9 cataclysm
    private static void cataclysm(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        float a = (float) (Mth.clamp(age / 8, 0, 1) * Mth.clamp((p.life - age) / 12, 0, 1));
        double R = p.r;
        Vec3 c = p.c;
        GravityFxClient.ring(m, cam, c.add(0, 0.06, 0), UP, R, 0.35, new float[][]{SUN, RAINC, BOLT, ICE, WIND, FOGC}[Mth.clamp((int) p.a0, 0, 5)], 0.8F * a);
        switch ((int) p.a0) {
            case 0 -> {   // heatwave: a second sun low overhead, and its heat pulsing down
                Vec3 s = c.add(0, 12, 0);
                GravityFxClient.gglow(m, cam, s, 3.2, a, SUN_W);
                GravityFxClient.gglow(m, cam, s, 6, 0.5F * a, SUN);
                sunbeam(m, cam, c, R * 0.8, age, p.life, p.id, now);
                for (int i = 0; i < 3; i++) {
                    double q = (age - 12 - i * 14) / 14;
                    if (q > 0 && q < 1) GravityFxClient.ring(m, cam, c.add(0, 0.2, 0), UP, R * q, 0.6, SUN, (float) (1 - q) * a);
                }
            }
            case 1 -> {   // deluge: a black cloud, then a wall of water crashing outward
                rain(vc, m, n, cam, c, R, age, p.life, p.id, now, DARK, true);
                double q = (age - 18) / 16;
                if (q > 0 && q < 1) {
                    double rr = R * FxBase.smooth(q), h = 2.6 * (1 - q) + 0.3;
                    int N = 32;
                    for (int i = 0; i < N; i++) {
                        double a0 = i * TAU / N, a1 = (i + 1) * TAU / N;
                        Vec3 b0 = c.add(Math.cos(a0) * rr, 0, Math.sin(a0) * rr), b1 = c.add(Math.cos(a1) * rr, 0, Math.sin(a1) * rr);
                        FxBase.quad(vc, m, n, cam, b0, b1, b1.add(0, h, 0), b0.add(0, h, 0), new float[]{0.2F, 0.45F, 0.75F}, 0.6F * a, 0.6F * a, 0.3F * a, 0.3F * a);
                        FxBase.quad(vc, m, n, cam, b0.add(0, h, 0), b1.add(0, h, 0), b1, b0, new float[]{0.2F, 0.45F, 0.75F}, 0.3F * a, 0.3F * a, 0.6F * a, 0.6F * a);
                        GravityFxClient.gseg(m, cam, b0.add(0, h, 0), b1.add(0, h, 0), 0.12, a, a, WHITE);
                    }
                }
            }
            case 2 -> {   // thunderstorm: the bolts come in their own packets; this is the cloud deck and its flicker
                cloudAt(vc, m, n, cam, c.add(0, 9, 0), R * 1.1, p.id, now, DARK, 0.9F * a, 22);
                if ((int) (now / 2) % 5 == 0) GravityFxClient.gglow(m, cam, c.add(0, 8.5, 0), R, 0.35F * a, BOLT);
                rain(vc, m, n, cam, c, R, age, p.life, p.id + 7, now, DARK, false);
            }
            case 3 -> {   // blizzard: hail and snow driven sideways in a swirl
                hail(vc, m, n, cam, c, R, age, p.life, p.id, now);
                for (int i = 0; i < 30; i++) {
                    double an = now * 0.15 + FxBase.h2(p.id, i, 26) * TAU, rr = R * Math.sqrt(FxBase.h2(p.id, i, 27)), y = 0.3 + 3 * FxBase.h2(p.id, i, 28);
                    Vec3 q = c.add(Math.cos(an) * rr, y, Math.sin(an) * rr);
                    GravityFxClient.gseg(m, cam, q, q.add(-Math.sin(an) * 0.6, -0.1, Math.cos(an) * 0.6), 0.03, 0, 0.8F * a, WHITE);
                }
            }
            case 4 -> funnel(vc, m, n, cam, c, R * 0.55, 13, p.id, now, a);   // a great tornado
            default -> {   // whiteout: the place goes white
                for (int i = 0; i < 30; i++) {
                    double an = FxBase.h2(p.id, i, 29) * TAU + now * 0.01, rr = R * Math.sqrt(FxBase.h2(p.id, i, 30));
                    Vec3 q = c.add(Math.cos(an) * rr, 0.5 + 2.5 * FxBase.h2(p.id, i, 31), Math.sin(an) * rr);
                    if (q.distanceToSqr(cam) < 9) continue;
                    puff(vc, m, n, cam, q, 1.8, WHITE, 0.4F * a);
                }
            }
        }
    }

    // ================================================================== 10 your own little sky
    private static void personal(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        if (self) return;   // it's over your head - you'd only see its underside
        Vec3 c = pos(e, pt).add(0, e.getBbHeight() + 0.9, 0);
        float a = (float) Mth.clamp(age / 4, 0, 1);
        int f = Mth.clamp((int) p.a0, 0, 5);
        int id = (int) p.v.x;
        switch (f) {
            case 0 -> {   // sun: a small sun with turning rays
                GravityFxClient.gglow(m, cam, c, 0.28, a, SUN_W);
                GravityFxClient.gglow(m, cam, c, 0.55, 0.5F * a, SUN);
                Vec3[] fr = frame(c.subtract(cam).normalize());
                for (int i = 0; i < 8; i++) {
                    double an = i * TAU / 8 + now * 0.05;
                    Vec3 d = fr[0].scale(Math.cos(an)).add(fr[1].scale(Math.sin(an)));
                    GravityFxClient.gseg(m, cam, c.add(d.scale(0.35)), c.add(d.scale(0.55)), 0.04, a, 0, SUN);
                }
            }
            case 1, 2, 3 -> {
                float[] col = f == 2 ? DARK : f == 3 ? new float[]{0.72F, 0.76F, 0.84F} : CLOUD;
                cloudAt(vc, m, n, cam, c, 0.45, id, now, col, 0.9F * a, 5);
                for (int i = 0; i < 6; i++) {
                    double ph = ((now / (f == 3 ? 12.0 : 7.0) + FxBase.h2(id, i, 32))) % 1.0, an = FxBase.h2(id, i, 33) * TAU, rr = 0.35 * Math.sqrt(FxBase.h2(id, i, 34));
                    Vec3 q = c.add(Math.cos(an) * rr, -0.15 - ph * 0.9, Math.sin(an) * rr);
                    if (f == 3) GravityFxClient.gglow(m, cam, q, 0.05, a * (float) (1 - ph), WHITE);
                    else GravityFxClient.gseg(m, cam, q, q.add(0, 0.2, 0), 0.015, a * (float) (1 - ph), 0, RAINC);
                }
                if (f == 2 && (int) (now / 3 + id) % 13 == 0) boltLine(m, cam, c.add(0, -1.0, 0), 0.9, (int) now, 0.12, a);
            }
            case 4 -> {   // wind: little ribbons circling
                for (int i = 0; i < 3; i++) {
                    double an = now * 0.25 + i * TAU / 3;
                    Vec3 s0 = c.add(Math.cos(an) * 0.45, 0.1 * Math.sin(now * 0.2 + i), Math.sin(an) * 0.45);
                    Vec3 s1 = c.add(Math.cos(an + 1.3) * 0.45, 0.1 * Math.sin(now * 0.2 + i + 1), Math.sin(an + 1.3) * 0.45);
                    GravityFxClient.gseg(m, cam, s0, s1, 0.04, 0, 0.9F * a, WIND);
                }
            }
            default -> cloudAt(vc, m, n, cam, c.add(0, -0.2, 0), 0.5, id, now, FOGC, 0.45F * a, 5);   // fog: a soft grey wisp
        }
    }

    // ================================================================== 11 splash
    private static void splash(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        double q = Mth.clamp(age / p.life, 0, 1);
        GravityFxClient.ring(m, cam, p.c.add(0, 0.06, 0), UP, p.r * (0.3 + q), 0.08, RAINC, (float) (1 - q));
        for (int i = 0; i < 8; i++) {
            double an = i * TAU / 8;
            Vec3 s = p.c.add(Math.cos(an) * p.r * q, 1.2 * q * (1 - q) * 2, Math.sin(an) * p.r * q);
            GravityFxClient.gglow(m, cam, s, 0.06, (float) (1 - q), RAINC);
        }
    }
}
