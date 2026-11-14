package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The Overdrive Grimoire, drawn: red, white and black. Cuts that leave a heartbeat trace hanging in the air, pulses
 * of red rolling out in pairs (lub - dub), afterimages of a dash, a thrust that flattens into a monitor's flat line,
 * a storm of short white cuts, the sword's white ribbon lashing out and wrapping, a red heat on a heart at the
 * Redline, and the Final Beat - a moment of stillness, then one enormous white-and-crimson cut with lines left hanging
 * that burst a beat later. On your own screen: a heart monitor beside the hotbar, its trace racing as your heart does,
 * and at the Redline a faint red pulse at the edges of the screen on every beat (and a soft thump you can hear).
 * Fed by FxPacket shape 96 (server/OverdriveFx), {@code h} = kind: 0 spike cut, 1 pulses, 2 rush, 3 thrust, 4 flurry,
 * 5 ribbon, 6 redline, 7 final beat, 8 the draw, 9 breath, 10 spark, 99 the heart, 98 a mode ended.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class OverdriveFxClient {
    private OverdriveFxClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] RED = {1.0F, 0.1F, 0.16F}, DEEP = {0.6F, 0.02F, 0.06F}, HOTC = {1.0F, 0.45F, 0.2F}, WHITE = {1, 1, 1},
            PINK = {1.0F, 0.6F, 0.68F}, CLOTH = {0.95F, 0.93F, 0.88F};

    private static final class L {
        final FxPacket p;
        final double born;
        boolean burst;
        L(FxPacket p, double born) { this.p = p; this.born = born; }
    }

    private static final List<L> LIVE = new ArrayList<>();
    private static float bpm = 70, shown = 70;
    private static boolean equipped, redMode;
    private static double lastBeat = -100;
    private static final float[] TRACE = new float[96];   // the monitor's trace, newest at the end
    private static int traceTick;

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if ((int) p.h == 98) { LIVE.removeIf(l -> (int) l.p.v.x == (int) p.v.x); if (mc.player != null && (int) p.v.x == mc.player.getId()) redMode = false; return; }
        if ((int) p.h == 99) { bpm = p.a0; equipped = p.r > 0.5F; redMode = p.r2 > 0.5F; return; }
        if ((int) p.h == 6) LIVE.removeIf(l -> (int) l.p.h == 6 && (int) l.p.v.x == (int) p.v.x);
        if (LIVE.size() > 300) LIVE.remove(0);
        LIVE.add(new L(p, mc.level.getGameTime()));
    }

    static void clear() { LIVE.clear(); }

    private static boolean held(Minecraft mc) {
        var b = mc.player == null ? null : io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(mc.player);
        return b != null && b.is(com.ironspellgrimoire.registry.AddonItemRegistry.OVERDRIVE_GRIMOIRE.get());
    }

    static int stateOf(float b) { return b >= 200 ? 2 : b >= 150 ? 1 : 0; }

    // ================================================================== the heart monitor
    /** Advance the trace once a tick: a flat line with a beat (P, QRS spike, T) at the heart's rate; a soft thump when hot. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        shown += (bpm - shown) * 0.2F;
        double now = mc.level.getGameTime();
        double period = 1200.0 / Math.max(40, shown);   // ticks per beat
        System.arraycopy(TRACE, 1, TRACE, 0, TRACE.length - 1);
        double ph = (now - lastBeat) / period;
        if (ph >= 1) {
            lastBeat = now; ph = 0;
            if (equipped && held(mc) && stateOf(shown) >= 1 && mc.player != null)   // you can hear it when it races
                mc.getSoundManager().play(SimpleSoundInstance.forUI(com.ironspellgrimoire.registry.AddonSounds.get("heartbeat"), 1.0F + (shown - 150) / 300F, stateOf(shown) == 2 ? 0.28F : 0.14F));
        }
        float v;   // the shape of one beat
        int k = (int) Math.round(ph * period);
        v = k == 1 ? 0.15F : k == 3 ? -0.25F : k == 4 ? 1.0F : k == 5 ? -0.45F : (k >= 8 && k <= 10) ? 0.22F : 0F;
        TRACE[TRACE.length - 1] = v;
        traceTick++;
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onGui(net.minecraftforge.client.event.RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !equipped || !held(mc)) return;
        var g = e.getGuiGraphics();
        double t = mc.level.getGameTime() + e.getPartialTick();
        int s = stateOf(shown);
        int col = s == 2 ? 0xFFFF3040 : s == 1 ? 0xFFFF9A40 : 0xFF7CFF9A;
        // the Redline: a faint red pulse at the edges of the screen on every beat (only the edges: you can still see)
        if (s == 2) {
            double since = t - lastBeat;
            int a = (int) (60 * Math.max(0, 1 - since / 6));
            if (a > 2) {
                int W = g.guiWidth(), H = g.guiHeight();
                for (int i = 0; i < 12; i++) {
                    int aa = a * (12 - i) / 12, c = (aa << 24) | 0xD0101A;
                    g.fill(i, 0, i + 1, H, c); g.fill(W - i - 1, 0, W - i, H, c);
                    g.fill(0, i, W, i + 1, c); g.fill(0, H - i - 1, W, H - i, c);
                }
            }
        }
        // the monitor, left of the hotbar
        int w = 74, h = 26, x0 = g.guiWidth() / 2 - 91 - 8 - w - 22, y0 = g.guiHeight() - h - 3;
        g.fill(x0 - 1, y0 - 1, x0 + w + 1, y0 + h + 1, s == 2 && (int) (t / 3) % 2 == 0 ? 0xFFB01020 : 0xFF2A2E34);
        g.fill(x0, y0, x0 + w, y0 + h, 0xE0080A0C);
        for (int gx = x0 + 6; gx < x0 + w; gx += 8) g.fill(gx, y0, gx + 1, y0 + h, 0x2030FF60);   // the grid
        for (int gy = y0 + 4; gy < y0 + h; gy += 8) g.fill(x0, gy, x0 + w, gy + 1, 0x2030FF60);
        int mid = y0 + h - 9, n = Math.min(TRACE.length, w - 2), prevY = mid;
        for (int i = 0; i < n; i++) {   // the trace, brightest at its leading edge
            float v = TRACE[TRACE.length - n + i];
            int y = mid - Math.round(v * 12);
            int x = x0 + 1 + i;
            int alpha = 80 + 175 * i / n;
            int c = (alpha << 24) | (col & 0xFFFFFF);
            g.fill(x, Math.min(prevY, y), x + 1, Math.max(prevY, y) + 1, c);
            prevY = y;
        }
        g.fill(x0 + 1 + n - 1, prevY - 1, x0 + 1 + n + 1, prevY + 1, 0xFFFFFFFF);   // the dot
        String b = String.valueOf(Math.round(shown));
        g.drawString(mc.font, b, x0 + w - mc.font.width(b) - 3, y0 + 2, col, true);
        g.drawString(mc.font, s == 2 ? "REDLINE" : s == 1 ? "HOT" : "bpm", x0 + 3, y0 + 2, s == 0 ? 0xFF6A8070 : col, true);
        double beatK = Math.max(0, 1 - (t - lastBeat) / 4);   // a little heart that jumps on the beat
        int hx = x0 + w - mc.font.width(b) - 11, hy = y0 + 5, hs = (int) Math.round(2 + 1.5 * beatK);
        g.fill(hx - hs, hy - hs / 2, hx, hy + hs / 2, col); g.fill(hx, hy - hs / 2, hx + hs, hy + hs / 2, col);
        g.fill(hx - hs + 1, hy + hs / 2, hx + hs - 1, hy + hs / 2 + 2, col);
    }

    // ================================================================== the world
    static void draw(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, double now, float pt) {
        if (LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        for (int i = LIVE.size() - 1; i >= 0; i--) {
            L l = LIVE.get(i);
            double age = now - l.born;
            if (age > l.p.life) { LIVE.remove(i); continue; }
            switch ((int) l.p.h) {
                case 0 -> spike(m, cam, l.p, age, now);
                case 1 -> pulses(m, cam, l.p, age, now);
                case 2 -> rush(m, cam, l.p, age);
                case 3 -> thrust(m, cam, l.p, age, now);
                case 4 -> flurry(m, cam, l.p, age);
                case 5 -> ribbon(m, cam, mc, l.p, age, now, pt);
                case 6 -> redline(m, cam, mc, l.p, age, now, pt);
                case 7 -> finalBeat(m, cam, l, age, now);
                case 8 -> drawOut(m, cam, mc, l.p, age, now, pt);
                case 9 -> breath(m, cam, mc, l.p, age, now, pt);
                case 10 -> spark(m, cam, l.p, age);
                default -> LIVE.remove(i);
            }
        }
    }

    private static Vec3 posOf(Minecraft mc, FxPacket p, float pt) {
        Entity e = p.v == null ? null : mc.level.getEntity((int) p.v.x);
        return e == null ? p.c : new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
    }

    /** Where a player's sword hand is, near enough. */
    private static Vec3 hand(Minecraft mc, Entity e, float pt) {
        Vec3 base = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
        float yaw = (e instanceof net.minecraft.world.entity.LivingEntity le ? Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot) : e.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), rt = new Vec3(-fwd.z, 0, fwd.x);
        return base.add(0, e.getBbHeight() * 0.5, 0).add(rt.scale(-0.38)).add(fwd.scale(0.35));
    }

    private static Vec3[] frame(Vec3 d) {
        Vec3 a = Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : UP;
        Vec3 x = d.cross(a).normalize();
        return new Vec3[]{x, x.cross(d).normalize()};
    }

    /** One beat's shape at u in [0, 1] (flat - P bump - QRS spike - T bump - flat). */
    private static double beat(double u) {
        if (u < 0.3) return 0;
        if (u < 0.36) return 0.12 * Math.sin((u - 0.3) / 0.06 * Math.PI);
        if (u < 0.42) return 0;
        if (u < 0.45) return -0.25 * (u - 0.42) / 0.03;
        if (u < 0.5) return -0.25 + 1.25 * (u - 0.45) / 0.05;
        if (u < 0.55) return 1.0 - 1.5 * (u - 0.5) / 0.05;
        if (u < 0.58) return -0.5 + 0.5 * (u - 0.55) / 0.03;
        if (u < 0.66) return 0;
        if (u < 0.76) return 0.2 * Math.sin((u - 0.66) / 0.1 * Math.PI);
        return 0;
    }

    // ---- 0 a cut that leaves a heartbeat trace in the air
    private static void spike(Matrix4f m, Vec3 cam, FxPacket p, double age, double now) {
        Vec3 d = p.d.normalize();
        Vec3 side = new Vec3(-d.z, 0, d.x);
        double head = Math.min(1, age / 4.0);
        float a = (float) Mth.clamp((p.life - age) / 7.0, 0, 1);
        int N = 48;
        Vec3[] pts = new Vec3[N];
        double[] w = new double[N];
        float[] al = new float[N];
        for (int i = 0; i < N; i++) {
            double u = i / (double) (N - 1) * head;
            pts[i] = p.c.add(d.scale(u * p.r)).add(0, beat(u) * p.r2, 0);
            w[i] = 0.06 + 0.06 * Math.abs(beat(u));
            al[i] = a * (float) (0.4 + 0.6 * (i / (double) (N - 1)));
        }
        GravityFxClient.gstrip(m, cam, pts, w, al, WHITE);
        for (int i = 0; i < N; i++) w[i] *= 3.2;
        GravityFxClient.gstrip(m, cam, pts, w, al, RED);
        // and the slash itself: a wide red crescent swept across the start
        if (age < 6) {
            float sa = (float) (1 - age / 6);
            Vec3 prev = null;
            for (int i = 0; i <= 14; i++) {
                double an = -1.0 + 2.0 * i / 14;
                Vec3 q = p.c.add(d.scale(0.6 + Math.cos(an) * 1.6)).add(side.scale(Math.sin(an) * 2.0)).add(0, -an * 0.25, 0);
                if (prev != null) GravityFxClient.gseg(m, cam, prev, q, 0.22, sa * 0.9F, sa * 0.9F, RED);
                if (prev != null) GravityFxClient.gseg(m, cam, prev, q, 0.06, sa, sa, WHITE);
                prev = q;
            }
        }
    }

    // ---- 1 lub - dub
    private static void pulses(Matrix4f m, Vec3 cam, FxPacket p, double age, double now) {
        Vec3 g = p.c.add(0, 0.06, 0);
        for (int k = 0; k < 2; k++) {
            double t0 = k * 8, q = (age - t0) / 9.0;
            if (q < 0 || q > 1.4) continue;
            double r = p.r * (k == 0 ? 0.75 : 1.0) * FxBase.smooth(Math.min(1, q));
            float a = (float) Math.max(0, 1 - q / 1.4) * (k == 1 ? 1F : 0.75F);
            GravityFxClient.ring(m, cam, g, UP, r, 0.5, RED, a * 0.8F);
            GravityFxClient.ring(m, cam, g, UP, r, 0.12, WHITE, a);
            GravityFxClient.ring(m, cam, g.add(0, 1.0, 0), UP, r * 0.9, 0.2, PINK, a * 0.5F);
            for (int i = 0; i < 16; i++) {   // little heartbeat spikes standing round the ring
                double an = i * TAU / 16;
                Vec3 b = g.add(Math.cos(an) * r, 0, Math.sin(an) * r);
                GravityFxClient.gseg(m, cam, b, b.add(0, 0.6 + 0.4 * (i % 2), 0), 0.05, a, 0, WHITE);
            }
        }
        if (age < 4 || (age > 8 && age < 12)) GravityFxClient.gglow(m, cam, p.c.add(0, 1.0, 0), 1.6, 0.5F, RED);
    }

    // ---- 2 afterimages along a dash
    private static void rush(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        float a = (float) Mth.clamp((p.life - age) / 8.0, 0, 1);
        Vec3 ab = p.u.subtract(p.c);
        int N = 6;
        for (int i = 0; i < N; i++) {
            double u = i / (double) (N - 1);
            Vec3 b = p.c.add(ab.scale(u));
            float fa = a * (float) (0.25 + 0.6 * u);
            GravityFxClient.gseg(m, cam, b.add(0, 0.1, 0), b.add(0, 1.75, 0), 0.32, fa * 0.6F, fa * 0.4F, RED);   // a silhouette
            GravityFxClient.gglow(m, cam, b.add(0, 1.6, 0), 0.22, fa, PINK);
        }
        for (int k = 0; k < 5; k++) {   // speed lines
            double y = 0.3 + k * 0.32;
            GravityFxClient.gseg(m, cam, p.c.add(0, y, 0), p.u.add(0, y, 0), 0.03, 0, a * 0.8F, WHITE);
        }
    }

    // ---- 3 a thrust that flattens into a line
    private static void thrust(Matrix4f m, Vec3 cam, FxPacket p, double age, double now) {
        Vec3 d = p.d.normalize();
        double len = p.r * Math.min(1, age / 3.0);
        float a = (float) Mth.clamp((p.life - age) / 8.0, 0, 1);
        Vec3 end = p.c.add(d.scale(len));
        GravityFxClient.gseg(m, cam, p.c, end, 0.4, a * 0.6F, a * 0.9F, RED);
        GravityFxClient.gseg(m, cam, p.c, end, 0.1, a, a, WHITE);
        GravityFxClient.gglow(m, cam, end, 0.5, a, WHITE);
        if (age > 3) {   // the flat line - one last blip, then nothing
            double u = Mth.clamp((age - 3) / 6, 0, 1);
            Vec3 side = new Vec3(-d.z, 0, d.x);
            Vec3 a0 = p.c.add(d.scale(p.r * 0.2)).add(0, -0.4, 0);
            int N = 30;
            Vec3 prev = null;
            for (int i = 0; i <= N; i++) {
                double v = i / (double) N;
                double blip = (v > 0.2 && v < 0.3) ? Math.sin((v - 0.2) / 0.1 * Math.PI) * 0.5 * (1 - u) : 0;
                Vec3 q = a0.add(d.scale(p.r * 0.75 * v)).add(0, blip, 0);
                if (prev != null) GravityFxClient.gseg(m, cam, prev, q, 0.05, a, a, i * 1.0 / N < u + 0.2 ? WHITE : RED);
                prev = q;
            }
        }
    }

    // ---- 4 a storm of cuts
    private static void flurry(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        int n = (int) p.a0;
        for (int i = 0; i < n; i++) {
            double t0 = i * 2, q = (age - t0) / 5.0;
            if (q < 0 || q > 1) continue;
            double s = p.a1 + i * 7.31;
            double yaw = (s * 1.7) % TAU, tilt = ((s * 0.37) % 1.0 - 0.5) * 2.2;
            Vec3 d = new Vec3(Math.cos(yaw), tilt * 0.4, Math.sin(yaw)).normalize();
            Vec3 c = p.c.add(Math.cos(s) * p.r * 0.5, 1.0 + Math.sin(s * 2.1) * 0.6, Math.sin(s * 1.3) * p.r * 0.5);
            double half = 1.6 * Math.min(1, q * 3);
            float a = (float) (1 - q);
            GravityFxClient.gseg(m, cam, c.subtract(d.scale(half)), c.add(d.scale(half)), 0.25, a * 0.7F, a * 0.7F, RED);
            GravityFxClient.gseg(m, cam, c.subtract(d.scale(half)), c.add(d.scale(half)), 0.05, a, a, WHITE);
        }
        float a = (float) Mth.clamp((p.life - age) / 6.0, 0, 1);
        GravityFxClient.ring(m, cam, p.c.add(0, 0.06, 0), UP, p.r, 0.12, RED, a * 0.5F);
    }

    // ---- 5 the ribbon lashing out and wrapping
    private static void ribbon(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity owner = mc.level.getEntity((int) p.v.x);
        if (owner == null) return;
        Vec3 from = hand(mc, owner, pt);
        Entity t = p.r >= 0 ? mc.level.getEntity((int) p.r) : null;
        Vec3 to = t == null ? p.u : new Vec3(Mth.lerp(pt, t.xo, t.getX()), Mth.lerp(pt, t.yo, t.getY()) + t.getBbHeight() * 0.55, Mth.lerp(pt, t.zo, t.getZ()));
        double reach = Math.min(1, age / 4.0);
        float a = (float) Mth.clamp((p.life - age) / 4.0, 0, 1);
        Vec3 ab = to.subtract(from);
        Vec3[] f = frame(ab.normalize());
        int N = 26;
        Vec3[] pts = new Vec3[N];
        double[] w = new double[N];
        float[] al = new float[N];
        for (int i = 0; i < N; i++) {
            double u = i / (double) (N - 1) * reach;
            double wave = Math.sin(u * 14 - now * 0.9) * 0.25 * (1 - u) * (1 - reach * 0.6);
            pts[i] = from.add(ab.scale(u)).add(f[0].scale(wave)).add(f[1].scale(Math.cos(u * 9 - now * 0.7) * 0.12));
            w[i] = 0.09;
            al[i] = a * 0.95F;
        }
        GravityFxClient.gstrip(m, cam, pts, w, al, CLOTH);
        for (int i = 0; i < N; i++) w[i] = 0.2;
        GravityFxClient.gstrip(m, cam, pts, w, al, PINK);
        if (t != null && age > 4) {   // wrapped round it
            double wr = Math.min(1, (age - 4) / 4);
            Vec3 prev = null;
            for (int i = 0; i <= 30; i++) {
                double u = i / 30.0 * wr;
                double an = u * TAU * 3 + now * 0.2;
                Vec3 q = new Vec3(Mth.lerp(pt, t.xo, t.getX()), Mth.lerp(pt, t.yo, t.getY()), Mth.lerp(pt, t.zo, t.getZ()))
                        .add(Math.cos(an) * (t.getBbWidth() * 0.65), t.getBbHeight() * (0.2 + 0.65 * u), Math.sin(an) * (t.getBbWidth() * 0.65));
                if (prev != null) GravityFxClient.gseg(m, cam, prev, q, 0.08, a, a, CLOTH);
                prev = q;
            }
        }
    }

    // ---- 6 the Redline: red heat on the player and a heartbeat ring round the chest
    private static void redline(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null || e.isInvisible()) return;
        Vec3 f = posOf(mc, p, pt);
        float a = (float) (Mth.clamp(age / 8, 0, 1) * Mth.clamp((p.life - age) / 10, 0, 1));
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        double period = 1200.0 / 205, beatK = Math.max(0, 1 - ((now % period) / period) * 3);
        if (!self) {
            for (int i = 0; i < 8; i++) {   // heat rising off the body
                double s = i * 1.37 + p.id, ph = ((now * 0.06 + i / 8.0) % 1.0);
                double an = s * 2.3;
                Vec3 b = f.add(Math.cos(an) * 0.35, 0.2 + ph * 2.0, Math.sin(an) * 0.35);
                GravityFxClient.gseg(m, cam, b, b.add(0, 0.45, 0), 0.12, a * (float) (1 - ph) * 0.7F, 0, i % 3 == 0 ? HOTC : RED);
            }
            GravityFxClient.gglow(m, cam, f.add(0, 1.25, 0), 0.45 + 0.35 * beatK, a * (0.4F + 0.5F * (float) beatK), RED);   // the heart, beating
        }
        // the heartbeat ring round the chest (only a thin line in first person, down at the waist)
        int N = 64;
        Vec3 prev = null;
        double y0 = self ? 0.6 : 1.2;
        for (int i = 0; i <= N; i++) {
            double u = i / (double) N, an = u * TAU + now * 0.08;
            double rr = (self ? 1.4 : 0.95) + beat((u * 3) % 1.0) * 0.25;
            Vec3 q = f.add(Math.cos(an) * rr, y0, Math.sin(an) * rr);
            if (prev != null) GravityFxClient.gseg(m, cam, prev, q, 0.04, a * 0.85F, a * 0.85F, RED);
            prev = q;
        }
    }

    // ---- 7 the Final Beat: stillness, then one enormous cut, then the lines it left burst
    private static void finalBeat(Matrix4f m, Vec3 cam, L l, double age, double now) {
        FxPacket p = l.p;
        Vec3 d = p.d.normalize();
        Vec3 side = new Vec3(-d.z, 0, d.x);
        double charge = Math.max(1, p.sweep);
        if (age < charge) {   // the heart stops: a flat line across the field, a ring closing in, everything holding its breath
            double q = age / charge;
            float a = (float) Math.min(1, q * 3);
            Vec3 a0 = p.c.add(side.scale(-p.r2)).add(d.scale(2)), a1 = p.c.add(side.scale(p.r2)).add(d.scale(2));
            GravityFxClient.gseg(m, cam, a0, a1, 0.05, a, a, WHITE);
            GravityFxClient.ring(m, cam, p.c.add(0, -0.9, 0), UP, 4.5 * (1 - q) + 0.8, 0.12, RED, a * 0.8F);
            GravityFxClient.gglow(m, cam, p.c, 0.3 + q, 0.6F * (float) q, RED);
            return;
        }
        double s = age - charge;
        if (s < 9) {   // the cut: a wide crescent the length of the field
            float a = (float) (1 - s / 9);
            double reach = Math.min(1, s / 2.0);
            for (int layer = 0; layer < 2; layer++) {
                Vec3 prev = null;
                for (int i = 0; i <= 28; i++) {
                    double v = -1 + 2.0 * i / 28;
                    double along = p.r * (0.15 + 0.85 * reach) * (1 - 0.35 * v * v);
                    Vec3 q = p.c.add(side.scale(v * p.r2)).add(d.scale(along * 0.55)).add(0, -0.15 * v, 0);
                    if (prev != null) GravityFxClient.gseg(m, cam, prev, q, layer == 0 ? 1.1 : 0.22, a * (layer == 0 ? 0.75F : 1F), a * (layer == 0 ? 0.75F : 1F), layer == 0 ? RED : WHITE);
                    prev = q;
                }
            }
            GravityFxClient.gseg(m, cam, p.c, p.c.add(d.scale(p.r)), 0.5, a * 0.8F, a * 0.2F, WHITE);
        }
        double burstAt = 14;
        if (s < burstAt + 10) {   // the cut lines left hanging in the air
            float a = s < burstAt ? 1F : (float) Math.max(0, 1 - (s - burstAt) / 10);
            for (int i = 0; i < 9; i++) {
                double h = p.a1 + i * 3.1;
                Vec3 c = p.c.add(d.scale(2 + (h * 0.73 % 1.0) * (p.r - 3))).add(side.scale(((h * 0.41) % 1.0 - 0.5) * p.r2 * 1.4)).add(0, ((h * 0.29) % 1.0) * 1.5 - 0.5, 0);
                double yaw = h % TAU;
                Vec3 dir = new Vec3(Math.cos(yaw), ((h * 0.17) % 1.0 - 0.5), Math.sin(yaw)).normalize();
                double half = 1.5 + (h * 0.11 % 1.0);
                GravityFxClient.gseg(m, cam, c.subtract(dir.scale(half)), c.add(dir.scale(half)), s < burstAt ? 0.03 : 0.25, a, a, s < burstAt ? WHITE : RED);
                if (s >= burstAt) GravityFxClient.gglow(m, cam, c, 0.8 * (s - burstAt + 1) * 0.3, a, PINK);
            }
        }
    }

    // ---- 8 the sword drawn out of your chest on a ribbon of red light
    private static void drawOut(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Entity e = mc.level.getEntity((int) p.v.x);
        if (e == null) return;
        Vec3 f = posOf(mc, p, pt);
        Vec3 chest = f.add(0, e.getBbHeight() * 0.68, 0), hnd = hand(mc, e, pt);
        double q = Math.min(1, age / 10.0);
        float a = (float) Mth.clamp((p.life - age) / 8.0, 0, 1);
        boolean self = e == mc.player && mc.options.getCameraType().isFirstPerson();
        if (!self) GravityFxClient.gglow(m, cam, chest, 0.35 + 0.3 * Math.max(0, 1 - age / 6), a, RED);
        Vec3 prev = null;
        for (int i = 0; i <= 24; i++) {   // the ribbon of light spiralling out to the hand
            double u = i / 24.0 * q;
            double an = u * TAU * 1.5 + now * 0.2;
            Vec3 pt0 = chest.add(hnd.subtract(chest).scale(u)).add(Math.cos(an) * 0.25 * (1 - u), Math.sin(an) * 0.25 * (1 - u), 0);
            if (prev != null) GravityFxClient.gseg(m, cam, prev, pt0, 0.06, a, a, i % 2 == 0 ? RED : WHITE);
            prev = pt0;
        }
        if (age > 6 && age < 16) GravityFxClient.gglow(m, cam, hnd, 0.4 * (1 - Math.abs(age - 10) / 6), a, WHITE);   // the glint as it lands in your hand
    }

    // ---- 9 a second wind: soft rings breathing out and rising
    private static void breath(Matrix4f m, Vec3 cam, Minecraft mc, FxPacket p, double age, double now, float pt) {
        Vec3 f = posOf(mc, p, pt);
        float a = (float) Mth.clamp((p.life - age) / 10.0, 0, 1);
        for (int k = 0; k < 3; k++) {
            double q = ((age / p.life) * 1.4 - k * 0.2);
            if (q < 0 || q > 1) continue;
            GravityFxClient.ring(m, cam, f.add(0, 0.2 + q * 2.0, 0), UP, 0.6 + q * 1.2, 0.08, k == 1 ? PINK : WHITE, a * (float) (1 - q));
        }
        for (int i = 0; i < 10; i++) {
            double ph = ((age * 0.05 + i / 10.0) % 1.0), an = i * 2.4;
            Vec3 b = f.add(Math.cos(an) * 0.6, ph * 2.2, Math.sin(an) * 0.6);
            GravityFxClient.gglow(m, cam, b, 0.08, a * (float) (1 - ph), WHITE);
        }
    }

    // ---- 10 a cut landing
    private static void spark(Matrix4f m, Vec3 cam, FxPacket p, double age) {
        float a = (float) (1 - age / p.life);
        double s = p.r * (0.4 + age / p.life * 0.6);
        double an = p.a1;
        Vec3 d0 = new Vec3(Math.cos(an), 0.4, Math.sin(an)).normalize(), d1 = new Vec3(-Math.sin(an), -0.5, Math.cos(an)).normalize();
        GravityFxClient.gseg(m, cam, p.c.subtract(d0.scale(s)), p.c.add(d0.scale(s)), 0.06, a, a, WHITE);
        GravityFxClient.gseg(m, cam, p.c.subtract(d1.scale(s * 0.7)), p.c.add(d1.scale(s * 0.7)), 0.05, a, a, RED);
        GravityFxClient.gglow(m, cam, p.c, s * 0.5, a * 0.6F, RED);
    }
}
