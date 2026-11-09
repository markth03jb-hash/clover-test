package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Finral Roulacase's gates of Spatial Magic (FxPacket shape 46: id, c = centre, d = the way it faces, u = its up,
 * r = half width, r2 = half height, life, h = style: 0 a gate, 1 Space Deletion's tear):
 *   a gate opens out from a point into an oval - a bright gold-white rim with a soft violet glow round it, and inside
 *   deep violet-blue space with faint ripples rolling in toward the centre and tiny stars turning slowly - then
 *   closes back to a point. Sparks of light flick off the rim.
 *   The tear of Space Deletion is black inside, edged with gold cracks that split outward.
 * A kill packet (shape 3) with the gate's id closes it early.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class PortalRenderer {
    private PortalRenderer() {}

    private static final class Gate { int id, style, life; Vec3 c, n, u, s; float rw, rh; double born; double closeAt = -1; }
    private static final List<Gate> GATES = new ArrayList<>();

    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || p.c == null) return;
        Gate g = new Gate();
        g.id = p.id; g.style = (int) p.h; g.life = Math.max(4, p.life);
        g.c = p.c;
        g.n = p.d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : p.d.normalize();
        Vec3 up = p.u.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : p.u.normalize();
        g.s = g.n.cross(up);
        if (g.s.lengthSqr() < 1.0E-6) g.s = new Vec3(1, 0, 0);
        g.s = g.s.normalize();
        g.u = g.s.cross(g.n).normalize();
        g.rw = Math.max(0.2F, p.r); g.rh = Math.max(0.2F, p.r2 > 0 ? p.r2 : p.r);
        g.born = mc.level.getGameTime();
        GATES.add(g);
    }

    public static boolean kill(int id) {
        Minecraft mc = Minecraft.getInstance();
        boolean any = false;
        for (Gate g : GATES) if (g.id == id && g.closeAt < 0 && mc.level != null) { g.closeAt = mc.level.getGameTime(); any = true; }
        return any;
    }

    /** 0..1: how open the gate is now (snaps open with a little overshoot, closes smoothly). */
    private static float openness(Gate g, double now) {
        double age = now - g.born;
        double end = g.closeAt >= 0 ? Math.min(g.born + g.life, g.closeAt + 5) : g.born + g.life;
        double k = Mth.clamp(age / 5.0, 0.0, 1.0) - 1.0;
        float in = (float) (1.0 + 2.2 * k * k * k + 1.2 * k * k);
        float out = (float) Mth.clamp((end - now) / 5.0, 0.0, 1.0);
        out = out * out * (3F - 2F * out);
        return Math.max(0F, Math.min(in, out));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { GATES.clear(); return; }
        double now = mc.level.getGameTime();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Iterator<Gate> it = GATES.iterator(); it.hasNext(); ) {
            Gate g = it.next();
            double end = g.closeAt >= 0 ? Math.min(g.born + g.life, g.closeAt + 5) : g.born + g.life;
            if (now > end + 1) { it.remove(); continue; }
            float o = openness(g, now);
            if (o < 0.2F) continue;
            // sparks flicking off the rim
            for (int i = 0; i < 2; i++) {
                double a = r.nextDouble(Math.PI * 2);
                Vec3 at = g.c.add(g.s.scale(Math.cos(a) * g.rw * o)).add(g.u.scale(Math.sin(a) * g.rh * o));
                Vec3 out = g.s.scale(Math.cos(a)).add(g.u.scale(Math.sin(a)));
                PixelFx.one(g.style == 1 ? 8 : 17, at, out.scale(0.04).add(g.n.scale(r.nextDouble(-0.02, 0.02))), out, 0.16);
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || GATES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime() + e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        Matrix4f m = ps.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        for (Gate g : GATES) {
            float o = openness(g, now);
            if (o <= 0.01F) continue;
            draw(m, cam, g, o, now);
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    private static void draw(Matrix4f m, Vec3 cam, Gate g, float o, double now) {
        boolean tear = g.style == 1;
        int seg = 48;
        float rw = g.rw * o, rh = g.rh * o;
        Vec3 c = g.c.subtract(cam);
        // ---- inside: deep space, darker toward the middle
        RenderSystem.defaultBlendFunc();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float[] inC = tear ? new float[]{0.0F, 0.0F, 0.0F} : new float[]{0.03F, 0.01F, 0.12F};
        float[] edC = tear ? new float[]{0.06F, 0.02F, 0.0F} : new float[]{0.22F, 0.12F, 0.5F};
        for (int i = 0; i < seg; i++) {
            Vec3 a = ell(c, g, rw, rh, i * Math.PI * 2 / seg, 1.0), b = ell(c, g, rw, rh, (i + 1) * Math.PI * 2 / seg, 1.0);
            v(bb, m, c, inC, 0.96F); v(bb, m, a, edC, 0.92F); v(bb, m, b, edC, 0.92F);
        }
        BufferUploader.drawWithShader(bb.end());
        // ---- ripples rolling in toward the centre, stars turning (light, additive)
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        if (!tear) {
            for (int k = 0; k < 3; k++) {
                double f = 1.0 - ((now * 0.03 + k / 3.0) % 1.0);   // shrinking toward the middle
                float a = (float) (0.35 * Math.sin(f * Math.PI));
                band(bb, m, c, g, rw, rh, f * 0.92, f * 0.92 + 0.05, new float[]{0.55F, 0.4F, 1.0F}, a, a * 0.2F, seg);
            }
            Random sr = new Random(g.id * 31L + 7);
            for (int i = 0; i < 22; i++) {
                double ang = sr.nextDouble() * Math.PI * 2 + now * 0.01 * (sr.nextBoolean() ? 1 : -1);
                double rad = Math.sqrt(sr.nextDouble()) * 0.85;
                Vec3 p = ell(c, g, rw, rh, ang, rad).add(g.n.scale(0.01));
                float tw = (float) (0.5 + 0.5 * Math.sin(now * 0.3 + i * 1.7));
                float s = 0.025F + 0.02F * tw;
                quad(bb, m, p, g.s.scale(s), g.u.scale(s), new float[]{0.9F, 0.85F, 1.0F}, 0.9F * tw);
            }
        } else {
            // gold cracks splitting outward from the edge of the tear
            Random sr = new Random(g.id * 17L + 3);
            for (int i = 0; i < 9; i++) {
                double ang = sr.nextDouble() * Math.PI * 2;
                double len = (0.4 + sr.nextDouble() * 0.6) * o;
                Vec3 p0 = ell(c, g, rw, rh, ang, 1.0);
                Vec3 p1 = ell(c, g, rw * (float) (1 + len), rh * (float) (1 + len), ang + (sr.nextDouble() - 0.5) * 0.3, 1.0);
                line(bb, m, p0, p1, g.n, 0.03F, new float[]{1.0F, 0.8F, 0.35F}, 0.9F);
            }
        }
        // ---- the rim: a bright gold-white edge with a violet glow outside it
        float[] rimC = tear ? new float[]{1.0F, 0.75F, 0.3F} : new float[]{1.0F, 0.92F, 0.62F};
        float[] glowC = tear ? new float[]{0.8F, 0.4F, 0.1F} : new float[]{0.55F, 0.35F, 1.0F};
        float pulse = (float) (0.85 + 0.15 * Math.sin(now * 0.4));
        band(bb, m, c, g, rw, rh, 0.94, 1.02, rimC, 0.95F * pulse, 0.95F * pulse, seg);
        band(bb, m, c, g, rw, rh, 1.02, 1.22, glowC, 0.55F * pulse, 0.0F, seg);
        band(bb, m, c, g, rw, rh, 0.80, 0.94, glowC, 0.0F, 0.4F * pulse, seg);
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.defaultBlendFunc();
    }

    private static Vec3 ell(Vec3 c, Gate g, float rw, float rh, double a, double k) {
        return c.add(g.s.scale(Math.cos(a) * rw * k)).add(g.u.scale(Math.sin(a) * rh * k));
    }

    private static void band(BufferBuilder bb, Matrix4f m, Vec3 c, Gate g, float rw, float rh, double k0, double k1, float[] col, float a0, float a1, int seg) {
        for (int i = 0; i < seg; i++) {
            double t0 = i * Math.PI * 2 / seg, t1 = (i + 1) * Math.PI * 2 / seg;
            Vec3 p00 = ell(c, g, rw, rh, t0, k0), p01 = ell(c, g, rw, rh, t1, k0), p10 = ell(c, g, rw, rh, t0, k1), p11 = ell(c, g, rw, rh, t1, k1);
            v(bb, m, p00, col, a0); v(bb, m, p10, col, a1); v(bb, m, p11, col, a1);
            v(bb, m, p00, col, a0); v(bb, m, p11, col, a1); v(bb, m, p01, col, a0);
        }
    }

    private static void quad(BufferBuilder bb, Matrix4f m, Vec3 p, Vec3 sx, Vec3 sy, float[] col, float a) {
        Vec3 a0 = p.subtract(sx).subtract(sy), a1 = p.add(sx).subtract(sy), a2 = p.add(sx).add(sy), a3 = p.subtract(sx).add(sy);
        v(bb, m, a0, col, a); v(bb, m, a1, col, a); v(bb, m, a2, col, a);
        v(bb, m, a0, col, a); v(bb, m, a2, col, a); v(bb, m, a3, col, a);
    }

    private static void line(BufferBuilder bb, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 n, float w, float[] col, float a) {
        Vec3 d = p1.subtract(p0);
        Vec3 side = d.cross(n);
        if (side.lengthSqr() < 1.0E-8) return;
        side = side.normalize().scale(w);
        v(bb, m, p0.subtract(side), col, a); v(bb, m, p0.add(side), col, a); v(bb, m, p1, col, 0F);
    }

    private static void v(BufferBuilder bb, Matrix4f m, Vec3 p, float[] c, float a) {
        bb.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(c[0], c[1], c[2], a).endVertex();
    }
}
