package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The String Grimoire's thread forms, worn by an entity and following it every frame (FxPacket shape 47: id = entity,
 * palette = kind, life = ticks, 0 = off). Crisp glowing lines, not sprites:
 *   0 COCOON  bands of pink-white thread wound round the body, turning slowly, criss-crossed
 *   1 DRESS   a dress of thread: a flared skirt of threads swaying with your steps, ribbons from the shoulders
 *             trailing behind you, a ring of thread at the waist and a crown of thread loops - with pink sparkles
 *   2 FATE    the Red Thread of Fate: a great wheel of red thread turning above the caster, a red thread running
 *             from it down to the hand of everyone it protects, a knot of red thread round each of their hands
 *   3 GUARD   a protected ally: a red thread knot round the hand and a thread rising from it
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class ThreadForms {
    private ThreadForms() {}

    public static final int COCOON = 0, DRESS = 1, FATE = 2, GUARD = 3;
    /** (entity id << 2 | kind) -> {start, end} */
    private static final Map<Long, double[]> FORMS = new HashMap<>();

    public static void set(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long key = (long) p.id << 2 | (p.palette & 3);
        double now = mc.level.getGameTime();
        if (p.life <= 0) { double[] f = FORMS.get(key); if (f != null) f[1] = Math.min(f[1], now + 6); return; }
        double[] f = FORMS.get(key);
        if (f == null || f[1] < now) FORMS.put(key, new double[]{now, now + p.life});
        else f[1] = now + p.life;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { FORMS.clear(); return; }
        double now = mc.level.getGameTime();
        FORMS.values().removeIf(f -> now > f[1] + 8);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Map.Entry<Long, double[]> en : FORMS.entrySet()) {
            int kind = (int) (en.getKey() & 3);
            Entity ent = mc.level.getEntity((int) (en.getKey() >> 2));
            if (ent == null || now > en.getValue()[1]) continue;
            boolean self = ent == mc.player && mc.options.getCameraType().isFirstPerson();
            if (kind == DRESS && !self && r.nextInt(3) == 0) {   // pink sparkles round the dress
                Vec3 at = ent.position().add(r.nextGaussian() * 0.35, r.nextDouble(0.1, 1.2), r.nextGaussian() * 0.35);
                PixelFx.one(16, at, new Vec3(0, 0.015, 0), new Vec3(0, 1, 0), 0.12);
            }
            if ((kind == FATE || kind == GUARD) && r.nextInt(6) == 0) {
                Vec3 at = ent.position().add(r.nextGaussian() * 0.3, r.nextDouble(0.6, 1.4), r.nextGaussian() * 0.3);
                PixelFx.one(18, at, new Vec3(0, 0.02, 0), new Vec3(0, 1, 0), 0.1);
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || FORMS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick();
        double now = mc.level.getGameTime() + pt;
        Vec3 cam = e.getCamera().getPosition();
        Matrix4f m = e.getPoseStack().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // the casters of the red thread, to run threads from their wheel down to everyone they protect
        java.util.List<Vec3> wheels = new java.util.ArrayList<>();
        for (Map.Entry<Long, double[]> en : FORMS.entrySet()) {
            if ((en.getKey() & 3) != FATE) continue;
            Entity ent = mc.level.getEntity((int) (en.getKey() >> 2));
            if (ent != null) wheels.add(ent.getPosition(pt).add(0, ent.getBbHeight() + 2.6, 0));
        }
        for (Map.Entry<Long, double[]> en : FORMS.entrySet()) {
            int kind = (int) (en.getKey() & 3);
            Entity ent = mc.level.getEntity((int) (en.getKey() >> 2));
            if (ent == null) continue;
            double[] f = en.getValue();
            float in = (float) Mth.clamp((now - f[0]) / 6.0, 0.0, 1.0), out = (float) Mth.clamp((f[1] - now) / 6.0, 0.0, 1.0);
            float a = Math.min(in, out);
            if (a <= 0.01F) continue;
            boolean self = ent == mc.player && mc.options.getCameraType().isFirstPerson();
            if (self && kind != FATE) a *= 0.25F;   // barely there in your own view
            Vec3 base = ent.getPosition(pt);
            float h = ent.getBbHeight(), w = Math.max(0.35F, ent.getBbWidth() * 0.7F);
            float yaw = ent instanceof LivingEntity le ? Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot) : ent.getYRot();
            double by = Math.toRadians(yaw);
            Vec3 fw = new Vec3(-Math.sin(by), 0, Math.cos(by)), lf = new Vec3(Math.cos(by), 0, Math.sin(by));
            switch (kind) {
                case COCOON -> cocoon(bb, m, cam, base, h, w, now, a);
                case DRESS -> dress(bb, m, cam, ent, base, h, w, fw, lf, now, pt, a);
                case FATE -> fate(bb, m, cam, base, h, now, a, self);
                default -> {}
            }
            if (kind == FATE || kind == GUARD) {   // the red knot round the hand, and the thread rising from it
                Vec3 hand = base.add(0, h * 0.45, 0).add(lf.scale(-0.38)).add(fw.scale(0.05));
                ring(bb, m, cam, hand, 0.09, 0.09, now * 0.2, 12, RED, a * 0.9F, 0.018F);
                if (kind == GUARD) {
                    Vec3 to = nearest(wheels, hand);
                    if (to != null) line(bb, m, cam, hand, to, 0.02F, RED, a * 0.75F);
                    else line(bb, m, cam, hand, hand.add(0, 1.6, 0), 0.02F, RED, a * 0.6F);
                }
            }
        }
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    private static final float[] PINK = {1.0F, 0.55F, 0.8F}, WHITE = {1.0F, 0.9F, 0.96F}, RED = {1.0F, 0.08F, 0.08F};

    private static Vec3 nearest(java.util.List<Vec3> list, Vec3 q) {
        Vec3 best = null; double bd = 30 * 30;
        for (Vec3 v : list) { double d = v.distanceToSqr(q); if (d < bd) { bd = d; best = v; } }
        return best;
    }

    private static void cocoon(BufferBuilder bb, Matrix4f m, Vec3 cam, Vec3 base, float h, float w, double now, float a) {
        // wound bands, each tilted a little and turning
        for (int i = 0; i < 9; i++) {
            double y = h * (0.06 + i * 0.11);
            double tilt = Math.sin(i * 1.7) * 0.25;
            double spin = now * 0.04 * (i % 2 == 0 ? 1 : -1) + i;
            double rr = w * (0.95 + 0.25 * Math.sin(Math.PI * (y / h)));   // fuller at the middle, like a cocoon
            tiltedRing(bb, m, cam, base.add(0, y, 0), rr, tilt, spin, i % 3 == 0 ? WHITE : PINK, a * 0.85F);
        }
        // long threads criss-crossing from the feet to the head
        for (int k = 0; k < 6; k++) {
            double a0 = k * Math.PI / 3 + now * 0.02;
            Vec3 lo = base.add(Math.cos(a0) * w * 0.8, 0.05, Math.sin(a0) * w * 0.8);
            Vec3 hi = base.add(Math.cos(a0 + 2.0) * w * 0.8, h + 0.05, Math.sin(a0 + 2.0) * w * 0.8);
            line(bb, m, cam, lo, hi, 0.012F, PINK, a * 0.6F);
        }
    }

    private static void dress(BufferBuilder bb, Matrix4f m, Vec3 cam, Entity ent, Vec3 base, float h, float w, Vec3 fw, Vec3 lf, double now, float pt, float a) {
        double speed = Math.min(0.4, Math.sqrt((ent.getX() - ent.xo) * (ent.getX() - ent.xo) + (ent.getZ() - ent.zo) * (ent.getZ() - ent.zo)));
        Vec3 waist = base.add(0, h * 0.5, 0);
        // the waist ring
        ring(bb, m, cam, waist, w * 0.62, w * 0.62, now * 0.03, 24, WHITE, a * 0.9F, 0.02F);
        // the skirt: threads flaring out from the waist, swaying, blown back as you move
        int n = 18;
        Vec3 prev = null, first = null;
        for (int i = 0; i <= n; i++) {
            double ang = i * Math.PI * 2 / n;
            Vec3 out = fw.scale(Math.cos(ang)).add(lf.scale(Math.sin(ang)));
            double sway = Math.sin(now * 0.15 + i * 0.9) * 0.06;
            Vec3 hem = base.add(0, h * 0.14, 0).add(out.scale(w * 1.25 + sway)).subtract(fw.scale(speed * 1.6));
            Vec3 top = waist.add(out.scale(w * 0.62));
            if (i < n) line(bb, m, cam, top, hem, 0.014F, i % 3 == 0 ? WHITE : PINK, a * 0.75F);
            if (prev != null) line(bb, m, cam, prev, hem, 0.016F, PINK, a * 0.9F);   // the hem
            prev = hem;
            if (first == null) first = hem;
        }
        // ribbons from the shoulders, trailing behind
        for (int s = -1; s <= 1; s += 2) {
            Vec3 sh = base.add(0, h * 0.8, 0).add(lf.scale(0.3 * s)).subtract(fw.scale(0.1));
            Vec3 p0 = sh;
            for (int k = 1; k <= 5; k++) {
                double t = k / 5.0;
                Vec3 p1 = sh.subtract(fw.scale(t * (0.5 + speed * 2.5))).add(0, -t * 0.7, 0)
                        .add(lf.scale(Math.sin(now * 0.2 + k + s) * 0.08 * k));
                line(bb, m, cam, p0, p1, 0.035F * (float) (1.1 - t), PINK, a * 0.8F);
                p0 = p1;
            }
        }
        // a crown of thread loops over the head
        Vec3 crown = base.add(0, h + 0.12, 0);
        for (int k = 0; k < 5; k++) {
            double ang = k * Math.PI * 2 / 5 + now * 0.02;
            Vec3 c = crown.add(Math.cos(ang) * 0.24, 0.06, Math.sin(ang) * 0.24);
            ring(bb, m, cam, c, 0.07, 0.11, ang, 10, WHITE, a * 0.8F, 0.012F);
        }
        ring(bb, m, cam, crown, 0.26, 0.26, now * 0.05, 18, PINK, a * 0.9F, 0.015F);
    }

    private static void fate(BufferBuilder bb, Matrix4f m, Vec3 cam, Vec3 base, float h, double now, float a, boolean self) {
        Vec3 c = base.add(0, h + 2.6, 0);
        double spin = now * 0.05;
        double R = 2.6;
        // the wheel: rim, inner rim, spokes, threads hanging from the rim
        ring(bb, m, cam, c, R, R, spin, 40, RED, a, 0.05F);
        ring(bb, m, cam, c, R * 0.55, R * 0.55, -spin * 1.5, 30, RED, a * 0.8F, 0.03F);
        for (int k = 0; k < 8; k++) {
            double ang = spin + k * Math.PI / 4;
            Vec3 rim = c.add(Math.cos(ang) * R, 0, Math.sin(ang) * R);
            line(bb, m, cam, c, rim, 0.025F, RED, a * 0.85F);
            double sway = Math.sin(now * 0.1 + k) * 0.2;
            line(bb, m, cam, rim, rim.add(sway, -1.1 - (k % 2) * 0.5, 0), 0.015F, RED, a * 0.6F);
        }
        if (!self) line(bb, m, cam, c, base.add(0, h * 0.5, 0), 0.03F, RED, a * 0.8F);
    }

    // ------------------------------------------------------------------ line drawing (camera-facing quads)
    private static void line(BufferBuilder bb, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, float w, float[] col, float al) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1.0E-8) return;
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = d.cross(mid.subtract(cam));
        if (side.lengthSqr() < 1.0E-10) return;
        side = side.normalize().scale(w);
        Vec3 a0 = a.subtract(cam), b0 = b.subtract(cam);
        v(bb, m, a0.add(side), col, al); v(bb, m, a0.subtract(side), col, al);
        v(bb, m, b0.subtract(side), col, al); v(bb, m, b0.add(side), col, al);
        // a soft glow round the line
        Vec3 g = side.scale(3.5);
        v(bb, m, a0.add(g), col, 0F); v(bb, m, a0, col, al * 0.25F); v(bb, m, b0, col, al * 0.25F); v(bb, m, b0.add(g), col, 0F);
        v(bb, m, a0.subtract(g), col, 0F); v(bb, m, a0, col, al * 0.25F); v(bb, m, b0, col, al * 0.25F); v(bb, m, b0.subtract(g), col, 0F);
    }

    private static void ring(BufferBuilder bb, Matrix4f m, Vec3 cam, Vec3 c, double rx, double rz, double spin, int seg, float[] col, float al, float w) {
        Vec3 prev = null;
        for (int i = 0; i <= seg; i++) {
            double ang = spin + i * Math.PI * 2 / seg;
            Vec3 p = c.add(Math.cos(ang) * rx, 0, Math.sin(ang) * rz);
            if (prev != null) line(bb, m, cam, prev, p, w, col, al);
            prev = p;
        }
    }

    private static void tiltedRing(BufferBuilder bb, Matrix4f m, Vec3 cam, Vec3 c, double r, double tilt, double spin, float[] col, float al) {
        Vec3 prev = null;
        int seg = 20;
        for (int i = 0; i <= seg; i++) {
            double ang = spin + i * Math.PI * 2 / seg;
            Vec3 p = c.add(Math.cos(ang) * r, Math.sin(ang) * r * tilt, Math.sin(ang) * r);
            if (prev != null) line(bb, m, cam, prev, p, 0.014F, col, al);
            prev = p;
        }
    }

    private static void v(BufferBuilder bb, Matrix4f m, Vec3 p, float[] c, float a) {
        bb.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(c[0], c[1], c[2], a).endVertex();
    }
}
