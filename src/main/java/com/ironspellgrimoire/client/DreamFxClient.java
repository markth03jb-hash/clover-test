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
 * The Dream Grimoire, drawn: a soft pastel night - coral pink, peacock teal and lavender, cream-white fluff and
 * gold stars. Star motes on curving paths, sheep-shaped clouds bounding past, a dome of pillow clouds, a sleeping
 * double lying where you stood, a floating clock whose hands run backwards, a parade of plush toys (bear, bunny,
 * lamb, star) waddling out, a dark dream lunging from behind, alarm rings, sleep marks over sleepers, a crescent
 * moon and stars drifting over you when Half Asleep, and the Dream World: a pastel dome of clouds and stars with a
 * big sleepy moon that comes down at the end. Kept light and see-through so you can always see the fight.
 * On your own screen: a little moon left of the hotbar that fills as you doze off.
 * Fed by FxPacket shape 97 (server/DreamFx), {@code h} = kind: 0 mote, 1 sheep, 2 pillow dome, 3 sleeping double,
 * 4 clock, 5 toy parade, 6 nightmare, 7 alarm, 8 sleep marks, 9 Half Asleep, 10 Dream World, 11 puff, 99 the doze
 * meter, 98 an entity's marks / mode ended.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class DreamFxClient {
    private DreamFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] CORAL = {1.0F, 0.55F, 0.58F}, TEAL = {0.2F, 0.85F, 0.8F}, LAV = {0.72F, 0.62F, 1.0F}, CREAM = {1.0F, 0.97F, 0.9F},
            NIGHT = {0.22F, 0.16F, 0.42F}, GOLD = {1.0F, 0.86F, 0.45F}, FLUFF = {0.97F, 0.95F, 0.98F}, PINKF = {1.0F, 0.84F, 0.88F},
            BEAR = {0.72F, 0.5F, 0.36F}, DARK = {0.12F, 0.08F, 0.2F}, MOON = {1.0F, 0.95F, 0.78F};

    private static final class L {
        final FxPacket p;
        final double born;
        L(FxPacket p, double born) { this.p = p; this.born = born; }
    }

    private static final List<L> LIVE = new ArrayList<>();
    private static float doze, shownDoze;
    private static boolean equipped, halfMode;

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if ((int) p.h == 98) {
            int id = p.v == null ? -1 : (int) p.v.x;
            LIVE.removeIf(l -> l.p.v != null && (int) l.p.v.x == id && ((int) l.p.h == 8 || (int) l.p.h == 9));
            if (mc.player != null && id == mc.player.getId()) halfMode = false;
            return;
        }
        if ((int) p.h == 99) { doze = p.a0; equipped = p.r > 0.5F; halfMode = p.r2 > 0.5F; return; }
        if ((int) p.h == 8 || (int) p.h == 9) LIVE.removeIf(l -> (int) l.p.h == (int) p.h && l.p.v != null && (int) l.p.v.x == (int) p.v.x);
        if (LIVE.size() > 300) LIVE.remove(0);
        LIVE.add(new L(p, mc.level.getGameTime()));
    }

    static void clear() { LIVE.clear(); }

    private static boolean held(Minecraft mc) {
        var b = mc.player == null ? null : io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(mc.player);
        return b != null && b.is(com.ironspellgrimoire.registry.AddonItemRegistry.DREAM_GRIMOIRE.get());
    }

    // ================================================================== the doze meter
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onGui(net.minecraftforge.client.event.RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !equipped || !held(mc)) return;
        var g = e.getGuiGraphics();
        double t = mc.level.getGameTime() + e.getPartialTick();
        shownDoze += (doze - shownDoze) * 0.15F;
        int R = 11, cx = g.guiWidth() / 2 - 91 - 8 - 22 - R - 4, cy = g.guiHeight() - R - 5;
        // a night-blue disc with a crescent moon that fills up from the bottom as you doze
        for (int y = -R; y <= R; y++) {
            int w = (int) Math.round(Math.sqrt(R * R - y * y));
            g.fill(cx - w, cy + y, cx + w + 1, cy + y + 1, 0xE0181030);
        }
        int fillTo = (int) Math.round(R - 2 * R * shownDoze);
        for (int y = -R + 2; y <= R - 2; y++) {
            int w = (int) Math.round(Math.sqrt((R - 2) * (R - 2) - y * y));
            int cut = (int) Math.round(Math.sqrt(Math.max(0, (R - 3) * (R - 3) - (y + 2) * (y + 2))));   // the crescent's bite
            int x0 = cx - w, x1 = cx + w;
            int bx0 = cx + 3 - cut, bx1 = cx + 3 + cut;
            int col = y >= fillTo ? 0xFFFFE9B0 : 0x60FFE9B0;
            for (int x = x0; x <= x1; x++) if (cut == 0 || x < bx0 || x > bx1) g.fill(x, cy + y, x + 1, cy + y + 1, col);
        }
        if (shownDoze > 0.98F || halfMode) {   // asleep (but watching): little Zs drifting up
            for (int k = 0; k < 2; k++) {
                double ph = ((t * 0.03) + k * 0.5) % 1.0;
                int zx = cx + R + 2 + (int) (ph * 6), zy = cy - (int) (ph * 14);
                int a = (int) (255 * Math.sin(Math.PI * ph));
                g.drawString(mc.font, "z", zx, zy, (Math.max(4, a) << 24) | 0xC8B8FF, false);
            }
        }
        if (halfMode) g.drawString(mc.font, "half asleep", cx - mc.font.width("half asleep") / 2, cy - R - 10, 0xFFB8A8FF, true);
    }

    // ================================================================== the world
    static void draw(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, double now, float pt) {
        if (LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        for (int i = LIVE.size() - 1; i >= 0; i--) {
            L l = LIVE.get(i);
            double age = now - l.born + pt;
            if (age > l.p.life) { LIVE.remove(i); continue; }
            switch ((int) l.p.h) {
                case 0 -> mote(m, cam, l.p, age);
                case 1 -> sheep(vc, m, n, cam, l.p, age);
                case 2 -> pillow(vc, m, n, cam, l.p, age, now);
                case 3 -> sleeper(vc, m, n, cam, l.p, age, now);
                case 4 -> clock(m, cam, l.p, age, now);
                case 5 -> parade(vc, m, n, cam, l.p, age);
                case 6 -> nightmare(vc, m, n, cam, l.p, age);
                case 7 -> alarm(m, cam, l.p, age);
                case 8 -> zzz(m, cam, mc, l.p, age, pt);
                case 9 -> halfAsleep(m, cam, mc, l.p, age, pt);
                case 10 -> world(vc, m, n, cam, l.p, age, now);
                case 11 -> puff(m, cam, l.p, age);
                default -> LIVE.remove(i);
            }
        }
    }

    private static Vec3 posOf(Minecraft mc, FxPacket p, float pt) {
        Entity e = p.v == null ? null : mc.level.getEntity((int) p.v.x);
        return e == null ? p.c : new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
    }

    private static float fade(FxPacket p, double age, double in, double out) {
        return (float) Mth.clamp(Math.min(age / in, (p.life - age) / out), 0, 1);
    }

    /** A camera-facing solid disc (a puff of cloud, a toy's head). */
    static void blob(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double r, float[] col, float a) {
        Vec3 view = c.subtract(cam);
        if (view.lengthSqr() < 1e-6 || a <= 0.01F) return;
        Vec3 d = view.normalize();
        Vec3 x = d.cross(Math.abs(d.y) > 0.95 ? new Vec3(1, 0, 0) : UP).normalize(), y = x.cross(d).normalize();
        int seg = 12;
        for (int i = 0; i < seg; i++) {
            double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            Vec3 p0 = c.add(x.scale(Math.cos(a0) * r)).add(y.scale(Math.sin(a0) * r)), p1 = c.add(x.scale(Math.cos(a1) * r)).add(y.scale(Math.sin(a1) * r));
            FxBase.quad(vc, m, n, cam, c, c, p1, p0, col, a, a, a, a);
        }
    }

    /** A four-pointed star, glowing. */
    static void star(Matrix4f m, Vec3 cam, Vec3 c, double r, float[] col, float a) {
        Vec3 d = c.subtract(cam).normalize();
        Vec3 x = d.cross(Math.abs(d.y) > 0.95 ? new Vec3(1, 0, 0) : UP).normalize(), y = x.cross(d).normalize();
        GravityFxClient.gseg(m, cam, c.subtract(x.scale(r)), c.add(x.scale(r)), r * 0.16, a, a, col);
        GravityFxClient.gseg(m, cam, c.subtract(y.scale(r)), c.add(y.scale(r)), r * 0.16, a, a, col);
        GravityFxClient.gglow(m, cam, c, r * 0.6, a * 0.6F, col);
    }

    /** A crescent moon facing the camera. */
    static void crescent(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double r, float a) {
        GravityFxClient.gglow(m, cam, c, r * 1.8, a * 0.35F, MOON);
        blob(vc, m, n, cam, c, r, MOON, a);
        Vec3 d = c.subtract(cam).normalize();
        Vec3 x = d.cross(Math.abs(d.y) > 0.95 ? new Vec3(1, 0, 0) : UP).normalize(), y = x.cross(d).normalize();
        blob(vc, m, n, cam, c.add(x.scale(r * 0.45)).add(y.scale(r * 0.25)).subtract(d.scale(0.02)), r * 0.85, NIGHT, a);   // the bite out of it
    }

    // ---- 0 a star mote on a curving path
    private static void mote(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        double u = Mth.clamp(age / p.life, 0, 1);
        Vec3 ab = p.u.subtract(p.c);
        Vec3 side = ab.cross(UP);
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        for (int k = 0; k < 6; k++) {   // the head and a sparkling tail
            double v = Math.max(0, u - k * 0.05);
            Vec3 at = p.c.add(ab.scale(v)).add(side.scale(Math.sin(v * Math.PI) * 0.8)).add(0, Math.sin(v * Math.PI) * 0.5, 0);
            star(m, cam, at, 0.22 * (1 - k * 0.14), k % 2 == 0 ? GOLD : CORAL, (float) (1 - k * 0.16));
        }
    }

    // ---- 1 sheep bounding along a line
    private static void sheep(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        Vec3 d = p.d.normalize();
        int N = Math.max(1, (int) p.a0);
        float a = fade(p, age, 3, 8) * 0.95F;
        for (int k = 0; k < N; k++) {
            double u = (age - k * 4) / (p.life - 12.0);
            if (u < 0 || u > 1) continue;
            double hop = Math.abs(Math.sin(u * Math.PI * 5)) * 0.8;
            Vec3 c = p.c.add(d.scale(u * p.r)).add(0, 0.55 + hop, 0);
            for (int b = 0; b < 4; b++) blob(vc, m, n, cam, c.add(Math.cos(b * 1.6) * 0.18, Math.sin(b * 2.3) * 0.08, Math.sin(b * 1.6) * 0.18), 0.28, FLUFF, a);
            blob(vc, m, n, cam, c.add(d.scale(0.38)).add(0, 0.06, 0), 0.14, DARK, a);   // the face
            for (int s = -1; s <= 1; s += 2) {   // little legs
                Vec3 leg = c.add(d.cross(UP).normalize().scale(0.1 * s)).add(0, -0.18, 0);
                GravityFxClient.gseg(m, cam, leg, leg.add(0, -0.28, 0), 0.04, a * 0.6F, a * 0.6F, LAV);
            }
            GravityFxClient.gglow(m, cam, c, 0.7, a * 0.25F, PINKF);
        }
    }

    // ---- 2 a dome of pillow clouds
    private static void pillow(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        float a = fade(p, age, 6, 10);
        double r = p.r;
        for (int ring = 0; ring < 3; ring++) {
            double h = ring * r * 0.38, rr = r * Math.cos(ring * 0.5);
            int N = ring == 2 ? 6 : 14;
            for (int i = 0; i < N; i++) {
                double an = i * TAU / N + ring * 0.3 + Math.sin(now * 0.02 + i) * 0.03;
                Vec3 c = p.c.add(Math.cos(an) * rr, 0.4 + h + 0.08 * Math.sin(now * 0.05 + i), Math.sin(an) * rr);
                blob(vc, m, n, cam, c, 0.55 - ring * 0.08, i % 3 == 0 ? PINKF : FLUFF, a * 0.32F);   // see-through: you can see past them
            }
        }
        GravityFxClient.ring(m, cam, p.c.add(0, 0.05, 0), UP, r, 0.4, LAV, a * 0.5F);
    }

    // ---- 3 a sleeping double where you stood, and the blink away
    private static void sleeper(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        float a = fade(p, age, 2, 6);
        double yaw = Math.toRadians(p.r);
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 head = p.c.add(fwd.scale(0.8)).add(0, 0.25, 0), feet = p.c.subtract(fwd.scale(0.8)).add(0, 0.2, 0);
        GravityFxClient.gseg(m, cam, feet, head, 0.32, a * 0.55F, a * 0.55F, LAV);   // a lying, glowing silhouette
        blob(vc, m, n, cam, head, 0.24, LAV, a * 0.55F);
        GravityFxClient.gglow(m, cam, p.c.add(0, 0.3, 0), 1.2, a * 0.25F, CORAL);
        for (int k = 0; k < 3; k++) {   // its sleep marks
            double ph = ((now * 0.03) + k / 3.0) % 1.0;
            zmark(m, cam, head.add(0.15 + ph * 0.3, 0.3 + ph * 1.0, 0), 0.12 + ph * 0.08, LAV, a * (float) Math.sin(Math.PI * ph));
        }
        if (age < 8) {   // the blink
            float b = (float) (1 - age / 8);
            GravityFxClient.gseg(m, cam, p.c.add(0, 1, 0), p.u.add(0, 1, 0), 0.25, b * 0.6F, b, TEAL);
            for (int k = 0; k < 5; k++) star(m, cam, p.c.add(p.u.subtract(p.c).scale(k / 4.0)).add(0, 1.2, 0), 0.18, GOLD, b);
        }
    }

    /** A "z" drawn in three strokes, facing the camera. */
    static void zmark(Matrix4f m, Vec3 cam, Vec3 c, double s, float[] col, float a) {
        if (a <= 0.01F) return;
        Vec3 d = c.subtract(cam).normalize();
        Vec3 x = d.cross(Math.abs(d.y) > 0.95 ? new Vec3(1, 0, 0) : UP).normalize(), y = x.cross(d).normalize();
        Vec3 tl = c.subtract(x.scale(s)).add(y.scale(s)), tr = c.add(x.scale(s)).add(y.scale(s)), bl = c.subtract(x.scale(s)).subtract(y.scale(s)), br = c.add(x.scale(s)).subtract(y.scale(s));
        GravityFxClient.gseg(m, cam, tl, tr, s * 0.18, a, a, col);
        GravityFxClient.gseg(m, cam, tr, bl, s * 0.18, a, a, col);
        GravityFxClient.gseg(m, cam, bl, br, s * 0.18, a, a, col);
    }

    // ---- 4 a floating clock, running backwards
    private static void clock(Matrix4f m, Vec3 cam, FxPacket p, double age, double now) {
        float a = fade(p, age, 6, 10);
        Vec3 c = p.c.add(0, 3.2 + 0.15 * Math.sin(now * 0.05), 0);
        Vec3 d = c.subtract(cam);
        d = new Vec3(d.x, 0, d.z).lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : new Vec3(d.x, 0, d.z).normalize();   // stands upright, turned to you
        Vec3 x = d.cross(UP).normalize();
        Vec3[] f = {x, UP};
        double R = 1.3;
        GravityFxClient.bring(m, cam, c, f, R, 0.1, GOLD, a);
        GravityFxClient.bring(m, cam, c, f, R * 0.88, 0.05, LAV, a * 0.6F);
        for (int k = 0; k < 12; k++) {
            double an = k * TAU / 12;
            Vec3 dir = x.scale(Math.cos(an)).add(UP.scale(Math.sin(an)));
            GravityFxClient.gseg(m, cam, c.add(dir.scale(R * (k % 3 == 0 ? 0.72 : 0.8))), c.add(dir.scale(R * 0.9)), 0.05, a, a, CREAM);
        }
        double hm = Math.PI / 2 + now * 0.08, hh = Math.PI / 2 + now * 0.012;   // backwards: anticlockwise
        GravityFxClient.gseg(m, cam, c, c.add(x.scale(Math.cos(hm) * R * 0.75)).add(UP.scale(Math.sin(hm) * R * 0.75)), 0.06, a, a, CORAL);
        GravityFxClient.gseg(m, cam, c, c.add(x.scale(Math.cos(hh) * R * 0.5)).add(UP.scale(Math.sin(hh) * R * 0.5)), 0.09, a, a, TEAL);
        GravityFxClient.gglow(m, cam, c, 0.2, a, CREAM);
        GravityFxClient.ring(m, cam, p.c.add(0, 0.05, 0), UP, p.r, 0.35, LAV, a * 0.55F);   // the slowed ground
        for (int k = 0; k < 8; k++) {   // drifting ticks of light falling slowly
            double ph = ((now * 0.006) + k / 8.0) % 1.0;
            double an = k * 2.1;
            star(m, cam, p.c.add(Math.cos(an) * p.r * 0.7, 3.0 - ph * 3.0, Math.sin(an) * p.r * 0.7), 0.12, GOLD, a * (float) Math.sin(Math.PI * ph));
        }
    }

    // ---- 5 a parade of plush toys
    private static void parade(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        Vec3 d = p.d.normalize();
        boolean drop = d.y < -0.5;   // a toy dropping on someone
        float a = fade(p, age, 3, 6);
        int N = drop ? 1 : 4;
        Vec3 side = drop ? new Vec3(1, 0, 0) : d.cross(UP).normalize();
        for (int k = 0; k < N; k++) {
            double u = drop ? Math.min(1, age / (p.life * 0.6)) : Mth.clamp((age - k * 3) / (p.life - 10.0), 0, 1);
            Vec3 c = p.c.add(d.scale(u * p.r)).add(side.scale((k - 1.5) * 0.7));
            double bob = drop ? 0 : Math.abs(Math.sin(age * 0.6 + k)) * 0.15;
            c = c.add(0, (drop ? 0.4 : 0.55) + bob, 0);
            toy(vc, m, n, cam, c, drop ? (int) (p.a1 % 4) : k, a);
        }
    }

    /** One plush toy: 0 bear, 1 bunny, 2 lamb, 3 star. */
    static void toy(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, int kind, float a) {
        switch (kind) {
            case 0 -> {   // a bear: round body, round head, round ears
                blob(vc, m, n, cam, c, 0.32, BEAR, a);
                blob(vc, m, n, cam, c.add(0, 0.42, 0), 0.24, BEAR, a);
                blob(vc, m, n, cam, c.add(-0.17, 0.62, 0), 0.08, BEAR, a);
                blob(vc, m, n, cam, c.add(0.17, 0.62, 0), 0.08, BEAR, a);
                blob(vc, m, n, cam, c.add(0, 0.38, 0), 0.09, CREAM, a);
            }
            case 1 -> {   // a bunny: long ears
                blob(vc, m, n, cam, c, 0.3, CREAM, a);
                blob(vc, m, n, cam, c.add(0, 0.4, 0), 0.22, CREAM, a);
                GravityFxClient.gseg(m, cam, c.add(-0.08, 0.55, 0), c.add(-0.12, 0.95, 0), 0.07, a, a, PINKF);
                GravityFxClient.gseg(m, cam, c.add(0.08, 0.55, 0), c.add(0.12, 0.95, 0), 0.07, a, a, PINKF);
                blob(vc, m, n, cam, c.add(0, 0.36, 0), 0.05, CORAL, a);
            }
            case 2 -> {   // a lamb: all fluff
                for (int b = 0; b < 5; b++) blob(vc, m, n, cam, c.add(Math.cos(b * 1.3) * 0.16, Math.sin(b * 2.1) * 0.1, 0), 0.24, FLUFF, a);
                blob(vc, m, n, cam, c.add(0, 0.34, 0), 0.15, LAV, a);
            }
            default -> {   // a star-shaped plush
                blob(vc, m, n, cam, c, 0.3, GOLD, a);
                star(m, cam, c, 0.55, GOLD, a);
                blob(vc, m, n, cam, c.add(0, 0.04, 0), 0.06, DARK, a);
            }
        }
        GravityFxClient.gglow(m, cam, c, 0.7, a * 0.25F, kind == 0 ? CORAL : TEAL);
    }

    // ---- 6 a dark dream lunging from behind
    private static void nightmare(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age) {
        float a = fade(p, age, 3, 8);
        double u = Mth.clamp((age - 2) / 5.0, 0, 1);
        Vec3 at = p.c.add(p.u.subtract(p.c).scale(u * 0.6));
        GravityFxClient.gseg(m, cam, at.add(0, 0.1, 0), at.add(0, 1.8, 0), 0.4, a * 0.7F, a * 0.5F, NIGHT);   // a dark shape of the foe
        blob(vc, m, n, cam, at.add(0, 1.6, 0), 0.28, DARK, a * 0.8F);
        blob(vc, m, n, cam, at.add(0.1, 1.65, 0), 0.04, CORAL, a);   // two glowing eyes
        blob(vc, m, n, cam, at.add(-0.1, 1.65, 0), 0.04, CORAL, a);
        if (age > 5 && age < 14) {   // the strike: a violet crescent across the foe
            float s = (float) (1 - (age - 5) / 9);
            Vec3 c = p.u.add(0, 1.0, 0), dir = flat(p.u.subtract(p.c)), side = dir.cross(UP).normalize();
            Vec3 prev = null;
            for (int i = 0; i <= 12; i++) {
                double an = -1.1 + 2.2 * i / 12;
                Vec3 q = c.add(side.scale(Math.sin(an) * 1.4)).add(0, Math.cos(an) * 0.8 - 0.4, 0);
                if (prev != null) { GravityFxClient.gseg(m, cam, prev, q, 0.22, s, s, LAV); GravityFxClient.gseg(m, cam, prev, q, 0.06, s, s, CREAM); }
                prev = q;
            }
        }
    }

    private static Vec3 flat(Vec3 v) { Vec3 f = new Vec3(v.x, 0, v.z); return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize(); }

    // ---- 7 alarm rings
    private static void alarm(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        for (int k = 0; k < 3; k++) {
            double q = (age - k * 3) / (p.life - 6.0);
            if (q < 0 || q > 1) continue;
            float a = (float) (1 - q);
            GravityFxClient.ring(m, cam, p.c.add(0, 0.2 + k * 0.4, 0), UP, p.r * q, 0.25, k % 2 == 0 ? CORAL : GOLD, a);
        }
        if (age < 8) GravityFxClient.gglow(m, cam, p.c.add(0, 1.2, 0), 1.0, (float) (1 - age / 8), GOLD);
    }

    // ---- 8 sleep marks over a sleeper
    private static void zzz(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, float pt) {
        Vec3 top = posOf(mc, p, pt).add(0, p.r + 0.3, 0);
        float a = fade(p, age, 3, 4);
        for (int k = 0; k < 3; k++) {
            double ph = ((age * 0.03) + k / 3.0) % 1.0;
            zmark(m, cam, top.add(0.2 + ph * 0.35, ph * 1.0, 0), 0.1 + ph * 0.08, k == 1 ? CORAL : LAV, a * (float) Math.sin(Math.PI * ph));
        }
        GravityFxClient.ring(m, cam, top.subtract(0, 0.15, 0), UP, 0.35, 0.06, LAV, a * 0.5F);
    }

    // ---- 9 Half Asleep: a crescent moon and stars drifting over you
    private static void halfAsleep(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, float pt) {
        Vec3 c = posOf(mc, p, pt).add(0, 2.5 + 0.1 * Math.sin(age * 0.06), 0);
        float a = fade(p, age, 8, 10);
        if (mc.player != null && (int) p.v.x == mc.player.getId() && mc.options.getCameraType().isFirstPerson()) a *= 0.25F;   // stays out of your view
        GravityFxClient.gglow(m, cam, c, 0.5, a * 0.4F, MOON);
        double R = 0.28;   // a glowing crescent (drawn as an arc)
        Vec3 prev = null;
        for (int i = 0; i <= 12; i++) {
            double an = Math.PI * 0.25 + i / 12.0 * Math.PI * 1.5;
            Vec3 q = c.add(Math.cos(an) * R, Math.sin(an) * R, 0);
            if (prev != null) GravityFxClient.gseg(m, cam, prev, q, 0.07, a, a, MOON);
            prev = q;
        }
        for (int k = 0; k < 5; k++) {
            double an = age * 0.04 + k * TAU / 5;
            star(m, cam, c.add(Math.cos(an) * 0.9, -0.6 + 0.2 * Math.sin(age * 0.07 + k), Math.sin(an) * 0.9), 0.1, k % 2 == 0 ? GOLD : TEAL, a);
        }
        GravityFxClient.ring(m, cam, posOf(mc, p, pt).add(0, 0.05, 0), UP, 0.9, 0.25, LAV, a * 0.4F);
    }

    // ---- 10 the Dream World
    private static void world(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, double now) {
        float a = fade(p, age, 10, 12);
        double r = p.r * FxBase.smooth(Math.min(1, age / 14.0));
        GravityFxClient.ring(m, cam, p.c.add(0, 0.05, 0), UP, r, 0.6, CORAL, a * 0.7F);
        GravityFxClient.ring(m, cam, p.c.add(0, 0.05, 0), UP, r * 0.96, 0.25, TEAL, a * 0.6F);
        for (int i = 0; i < 18; i++) {   // a rim of pastel clouds
            double an = i * TAU / 18 + now * 0.004;
            Vec3 c = p.c.add(Math.cos(an) * r, 1.0 + 0.6 * Math.sin(i * 1.7 + now * 0.03), Math.sin(an) * r);
            blob(vc, m, n, cam, c, 0.9, i % 3 == 0 ? PINKF : FLUFF, a * 0.35F);
        }
        for (int k = 0; k < 24; k++) {   // stars hanging in the air
            double an = k * 2.4, rr = r * (0.2 + 0.75 * ((k * 0.37) % 1.0));
            star(m, cam, p.c.add(Math.cos(an) * rr, 2 + (k % 5) * 1.2 + 0.2 * Math.sin(now * 0.05 + k), Math.sin(an) * rr), 0.16, k % 3 == 0 ? CORAL : k % 3 == 1 ? GOLD : TEAL, a * 0.85F);
        }
        double land = p.sweep;
        double q = Mth.clamp(age / land, 0, 1);
        Vec3 moon = p.c.add(0, 22 - 20 * q * q, 0);   // the sleepy moon, coming down faster and faster
        if (age < land) {
            crescent(vc, m, n, cam, moon, 2.4, a);
            Vec3 eye = moon.add(-0.6, 0.2, 0);   // a closed, sleeping eye on it
            GravityFxClient.gseg(m, cam, eye.add(-0.35, 0, 0), eye.add(0.35, -0.1, 0), 0.08, a, a, DARK);
        } else {   // and lands: a burst of stars and rings
            double b = (age - land) / (p.life - land);
            float ba = (float) (1 - b);
            GravityFxClient.gglow(m, cam, p.c.add(0, 1.5, 0), 4 + 6 * b, ba * 0.8F, MOON);
            GravityFxClient.ring(m, cam, p.c.add(0, 0.3, 0), UP, p.r * b, 0.8, GOLD, ba);
            for (int k = 0; k < 12; k++) {
                double an = k * TAU / 12;
                star(m, cam, p.c.add(Math.cos(an) * p.r * b * 0.8, 1 + 3 * b, Math.sin(an) * p.r * b * 0.8), 0.3, k % 2 == 0 ? CORAL : TEAL, ba);
            }
        }
    }

    // ---- 11 a little pastel puff
    private static void puff(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        float a = (float) (1 - age / p.life);
        GravityFxClient.gglow(m, cam, p.c, p.r * (0.5 + age / p.life), a * 0.7F, PINKF);
        for (int k = 0; k < 4; k++) {
            double an = k * TAU / 4 + p.a1;
            star(m, cam, p.c.add(Math.cos(an) * p.r * age / p.life, Math.sin(an) * p.r * age / p.life, 0), 0.1, k % 2 == 0 ? GOLD : TEAL, a);
        }
    }
}
