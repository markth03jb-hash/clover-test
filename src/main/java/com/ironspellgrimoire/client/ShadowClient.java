package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.network.ShadowPackets;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The Shadow Grimoire, drawn: black smoke and blue fire. Pools of shadow that soldiers climb out of and sink into,
 * a smoky dash, an unseen grip (dark fingers of smoke closing round its catch), a hunt mark, the veil round its
 * caster, Sovereign's Domain (a dark crown of blue fire on the ground round its master), the Night Legion's tide,
 * a swap of places, a slam, shadows' blows, a rank-up flare, a remnant resisting, the Gate of Shade.
 * And the remnants: a black, blue-eyed copy of what you slew, crouched in a pool of shadow, with its tries left
 * shown as blue sparks over it - drawn only for you (the server sends them to their maker alone).
 * Fed by FxPacket shape 98 (server/ShadowFx), {@code h} = kind 1-14.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class ShadowClient {
    private ShadowClient() {}

    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    static final float[] BLUE = {0.25F, 0.6F, 1.0F}, PALE = {0.6F, 0.85F, 1.0F}, SMOKE = {0.02F, 0.02F, 0.05F}, DEEP = {0.05F, 0.12F, 0.35F};

    private static final class L {
        final FxPacket p;
        final double born;
        L(FxPacket p, double born) { this.p = p; this.born = born; }
    }

    private static final List<L> LIVE = new ArrayList<>();

    /** A remnant only this player can see. */
    private record Rem(ShadowPackets.Remnant p, double born) {}
    private static final Map<Integer, Rem> REMNANTS = new HashMap<>();
    private static final Map<Integer, LivingEntity> PUPPETS = new HashMap<>();

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (LIVE.size() > 300) LIVE.remove(0);
        LIVE.add(new L(p, mc.level.getGameTime()));
    }

    public static void remnant(ShadowPackets.Remnant p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (p.life <= 0) { REMNANTS.remove(p.id); PUPPETS.remove(p.id); return; }
        Rem old = REMNANTS.get(p.id);
        REMNANTS.put(p.id, new Rem(p, old != null ? old.born() : mc.level.getGameTime()));
    }

    static void clear() { LIVE.clear(); REMNANTS.clear(); PUPPETS.clear(); }

    private static float fade(FxPacket p, double age, double in, double out) {
        return (float) Mth.clamp(Math.min(age / in, (p.life - age) / out), 0, 1);
    }

    private static Vec3 posOf(Minecraft mc, FxPacket p, float pt) {
        Entity e = p.v == null ? null : mc.level.getEntity((int) p.v.x);
        return e == null ? p.c : new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
    }

    /** A flat dark pool on the ground, its rim burning blue. */
    static void pool(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double r, float a) {
        Vec3 ctr = c.add(0, 0.03, 0);
        int seg = 20;
        for (int i = 0; i < seg; i++) {
            double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            double r0 = r * (1 + 0.08 * Math.sin(a0 * 5)), r1 = r * (1 + 0.08 * Math.sin(a1 * 5));
            Vec3 p0 = ctr.add(Math.cos(a0) * r0, 0, Math.sin(a0) * r0), p1 = ctr.add(Math.cos(a1) * r1, 0, Math.sin(a1) * r1);
            FxBase.quad(vc, m, n, cam, ctr, ctr, p1, p0, SMOKE, a * 0.85F, a * 0.85F, a * 0.85F, a * 0.85F);
        }
        GravityFxClient.ring(m, cam, ctr.add(0, 0.01, 0), UP, r, 0.12, BLUE, a * 0.8F);
    }

    /** A rising wisp of smoke with a blue tip. */
    static void wisp(Matrix4f m, Vec3 cam, Vec3 base, double h, double sway, float a) {
        Vec3 prev = base;
        for (int i = 1; i <= 5; i++) {
            double u = i / 5.0;
            Vec3 q = base.add(Math.sin(u * 3 + sway) * 0.12, u * h, Math.cos(u * 2.4 + sway) * 0.12);
            GravityFxClient.gseg(m, cam, prev, q, 0.14 * (1 - u * 0.7), a * (float) (1 - u), a * (float) (1 - u) * 0.6F, u > 0.6 ? BLUE : DEEP);
            prev = q;
        }
    }

    static void draw(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, double now, float pt) {
        Minecraft mc = Minecraft.getInstance();
        for (Rem r : REMNANTS.values()) {   // the pools under the remnants and their tries left
            Vec3 c = r.p().pos;
            float a = (float) Mth.clamp((now - r.born()) / 10.0, 0, 1) * (float) Mth.clamp((r.p().life - (now - r.born())) / 40.0, 0.2, 1);
            pool(vc, m, n, cam, c, 0.9, a);
            for (int k = 0; k < 3; k++) wisp(m, cam, c.add(Math.cos(k * 2.1 + now * 0.02) * 0.5, 0, Math.sin(k * 2.1 + now * 0.02) * 0.5), 1.0, now * 0.1 + k, a * 0.7F);
            for (int k = 0; k < r.p().tries; k++) {   // its tries left, as blue sparks over it
                Vec3 s = c.add((k - (r.p().tries - 1) / 2.0) * 0.3, 2.3 + 0.05 * Math.sin(now * 0.15 + k), 0);
                GravityFxClient.gglow(m, cam, s, 0.12, a, PALE);
            }
        }
        if (LIVE.isEmpty()) return;
        for (int i = LIVE.size() - 1; i >= 0; i--) {
            L l = LIVE.get(i);
            double age = now - l.born + pt;
            if (age > l.p.life) { LIVE.remove(i); continue; }
            FxPacket p = l.p;
            switch ((int) p.h) {
                case 1, 2 -> {   // a pool a shadow climbs out of / sinks into
                    float a = fade(p, age, 4, 10);
                    pool(vc, m, n, cam, p.c, p.r * (p.h == 1 ? Mth.clamp(age / 6.0, 0.3, 1) : 1), a);
                    for (int k = 0; k < 6; k++) wisp(m, cam, p.c.add(Math.cos(k * 1.05 + p.a1) * p.r * 0.7, 0, Math.sin(k * 1.05 + p.a1) * p.r * 0.7), 1.4 + (p.h == 1 ? 0.8 : 0), age * 0.12 + k, a);
                }
                case 3 -> {   // a dash of shadow
                    float a = (float) (1 - age / p.life);
                    for (int k = 0; k < 4; k++) {
                        Vec3 off = new Vec3(0, 0.4 + k * 0.4, 0);
                        GravityFxClient.gseg(m, cam, p.c.add(off), p.u.add(off), 0.35, a * 0.2F, a * 0.8F, k % 2 == 0 ? DEEP : BLUE);
                    }
                }
                case 4 -> {   // an unseen grip: dark fingers of smoke closing round its catch
                    Vec3 c = posOf(mc, p, pt).add(0, p.r * 0.5, 0);
                    float a = fade(p, age, 2, 4);
                    double close = 1.4 - Mth.clamp(age / 6.0, 0, 1) * 0.7;
                    for (int k = 0; k < 5; k++) {
                        double an = k * TAU / 5 + p.a1;
                        Vec3 base = c.add(Math.cos(an) * close * 1.4, -p.r * 0.6, Math.sin(an) * close * 1.4);
                        Vec3 tip = c.add(Math.cos(an) * close * 0.5, p.r * 0.5, Math.sin(an) * close * 0.5);
                        GravityFxClient.gseg(m, cam, base, tip, 0.25, a * 0.9F, a * 0.5F, DEEP);
                        GravityFxClient.gglow(m, cam, tip, 0.15, a, BLUE);
                    }
                }
                case 5 -> {   // a hunt mark: a burning blue sigil over the hunted
                    Vec3 c = posOf(mc, p, pt).add(0, p.r + 0.7, 0);
                    float a = fade(p, age, 4, 10);
                    GravityFxClient.ring(m, cam, c, UP, 0.45, 0.06, BLUE, a);
                    for (int k = 0; k < 3; k++) {
                        double an = age * 0.08 + k * TAU / 3;
                        GravityFxClient.gseg(m, cam, c.add(Math.cos(an) * 0.45, 0, Math.sin(an) * 0.45), c.add(0, -0.4, 0), 0.05, a, a * 0.3F, PALE);
                    }
                }
                case 6 -> {   // the veil: smoke clinging round its caster
                    Vec3 c = posOf(mc, p, pt);
                    float a = fade(p, age, 4, 10) * 0.6F;
                    if (mc.player != null && (int) p.v.x == mc.player.getId() && mc.options.getCameraType().isFirstPerson()) a *= 0.3F;
                    for (int k = 0; k < 8; k++) wisp(m, cam, c.add(Math.cos(k * 0.8 + age * 0.05) * 0.5, 0, Math.sin(k * 0.8 + age * 0.05) * 0.5), 1.8, age * 0.1 + k, a);
                }
                case 7 -> {   // Sovereign's Domain: a dark crown of blue fire on the ground round its master
                    Vec3 c = posOf(mc, p, pt);
                    float a = fade(p, age, 10, 20);
                    GravityFxClient.ring(m, cam, c.add(0, 0.05, 0), UP, 3.2, 0.25, BLUE, a * 0.7F);
                    GravityFxClient.ring(m, cam, c.add(0, 0.05, 0), UP, 2.6, 0.08, PALE, a * 0.5F);
                    for (int k = 0; k < 10; k++) {
                        double an = k * TAU / 10 + age * 0.01;
                        Vec3 b = c.add(Math.cos(an) * 3.2, 0.05, Math.sin(an) * 3.2);
                        double h = 0.6 + 0.35 * Math.sin(age * 0.2 + k * 1.7);
                        GravityFxClient.gseg(m, cam, b, b.add(0, h, 0), 0.18, a, 0, BLUE);   // spikes of blue fire
                    }
                }
                case 8 -> {   // the Night Legion's tide of shadow rolling ahead
                    Vec3 d = p.d.normalize(), side = d.cross(UP).normalize();
                    float a = fade(p, age, 4, 14);
                    double front = Mth.clamp(age / (p.life * 0.6), 0, 1) * p.r;
                    for (int k = -4; k <= 4; k++) {
                        Vec3 b = p.c.add(side.scale(k * 0.9));
                        GravityFxClient.gseg(m, cam, b.add(0, 0.2, 0), b.add(d.scale(front)).add(0, 0.2, 0), 0.6, a * 0.2F, a * 0.8F, DEEP);
                        wisp(m, cam, b.add(d.scale(front)), 1.6, age * 0.15 + k, a);
                    }
                }
                case 9 -> {   // two places swapping
                    float a = (float) (1 - age / p.life);
                    pool(vc, m, n, cam, p.c, 1.0, a);
                    pool(vc, m, n, cam, p.u, 1.0, a);
                    GravityFxClient.gseg(m, cam, p.c.add(0, 1, 0), p.u.add(0, 1, 0), 0.12, a, a, BLUE);
                }
                case 10 -> {   // a slam of shadow
                    float a = (float) (1 - age / p.life);
                    double q = age / p.life;
                    GravityFxClient.ring(m, cam, p.c.add(0, 0.1, 0), UP, p.r * q, 0.4, BLUE, a);
                    pool(vc, m, n, cam, p.c, p.r * 0.7, a);
                    for (int k = 0; k < 8; k++) {
                        double an = k * TAU / 8 + p.a1;
                        GravityFxClient.gseg(m, cam, p.c.add(0, 0.1, 0), p.c.add(Math.cos(an) * p.r * q, 0.6 * (1 - q), Math.sin(an) * p.r * q), 0.15, a, 0, DEEP);
                    }
                }
                case 11 -> {   // a shadow's blow
                    float a = (float) (1 - age / p.life);
                    double s = 0.4 + 0.15 * p.r;
                    Vec3 side = new Vec3(Math.cos(p.a1), 0.6, Math.sin(p.a1)).normalize();
                    GravityFxClient.gseg(m, cam, p.c.subtract(side.scale(s)), p.c.add(side.scale(s)), 0.08 + 0.02 * p.r, a, a, PALE);
                    GravityFxClient.gglow(m, cam, p.c, s * 0.8, a * 0.6F, BLUE);
                }
                case 12 -> {   // a rank-up flare: a column of blue fire
                    float a = fade(p, age, 2, 14);
                    GravityFxClient.gseg(m, cam, p.c, p.c.add(0, p.r + 2.5, 0), 0.6 * a, a, 0, BLUE);
                    GravityFxClient.ring(m, cam, p.c.add(0, 0.1, 0), UP, 1.2 + age * 0.05, 0.2, PALE, a);
                }
                case 13 -> {   // a remnant resisting: it shudders, sparks flying off
                    float a = (float) (1 - age / p.life);
                    for (int k = 0; k < 6; k++) {
                        double an = k * TAU / 6 + age * 0.3;
                        GravityFxClient.gseg(m, cam, p.c.add(0, 0.8, 0), p.c.add(Math.cos(an) * (0.4 + age * 0.06), 0.8 + age * 0.04, Math.sin(an) * (0.4 + age * 0.06)), 0.05, a, 0, PALE);
                    }
                }
                case 14 -> {   // the Gate of Shade: a ring of shadow opening (a0 = 1 storing them, 0 calling them out)
                    float a = fade(p, age, 3, 10);
                    double q = p.a0 > 0.5F ? 1 - age / p.life : age / p.life;
                    pool(vc, m, n, cam, p.c, p.r * (0.3 + 0.7 * q), a);
                    for (int k = 0; k < 10; k++) wisp(m, cam, p.c.add(Math.cos(k * 0.63) * p.r * q, 0, Math.sin(k * 0.63) * p.r * q), 1.5, age * 0.1 + k, a);
                }
                default -> LIVE.remove(i);
            }
        }
    }

    /** The remnants themselves: black, blue-eyed copies of what you slew, crouched low in their pools (only for you). */
    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || REMNANTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { REMNANTS.clear(); return; }
        double now = mc.level.getGameTime() + e.getPartialTick();
        REMNANTS.values().removeIf(r -> now - r.born() > r.p().life);
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        var buf = mc.renderBuffers().bufferSource();
        for (var en : REMNANTS.entrySet()) {
            ShadowPackets.Remnant r = en.getValue().p();
            LivingEntity d = ShadowSoldierRenderer.puppet(PUPPETS, en.getKey(), r.type, mc.level);
            d.setPos(r.pos.x, r.pos.y, r.pos.z);
            d.xo = r.pos.x; d.yo = r.pos.y; d.zo = r.pos.z;
            d.setYRot(r.yaw); d.yRotO = r.yaw; d.yBodyRot = r.yaw; d.yBodyRotO = r.yaw; d.yHeadRot = r.yaw; d.yHeadRotO = r.yaw;
            d.tickCount = (int) now;
            ps.pushPose();
            ps.translate(r.pos.x - cam.x, r.pos.y - cam.y - d.getBbHeight() * 0.25 + 0.04 * Math.sin(now * 0.1 + en.getKey()), r.pos.z - cam.z);   // sunk into its pool
            ps.scale(1, 0.85F, 1);   // crouched
            var renderer = mc.getEntityRenderDispatcher().getRenderer(d);
            renderer.render(d, r.yaw, e.getPartialTick(), ps, new ShadowSoldierRenderer.Tint(buf, 0.06F, 0.07F, 0.14F, 0), LightTexture.FULL_BRIGHT);
            ps.mulPose(Axis.YP.rotationDegrees(-r.yaw));
            ps.translate(0, d.getEyeHeight() + 0.02F, d.getBbWidth() * 0.5F + 0.02F);
            ShadowSoldierRenderer.eyes(ps, buf, 0.09F, 0.6F);
            ps.popPose();
        }
        buf.endBatch();
    }
}
