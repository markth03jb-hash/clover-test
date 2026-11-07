package com.example.arcaneaddon.client;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.network.FxPacket;
import com.example.arcaneaddon.registry.AddonItemRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Real 3D slash effects: translucent dark meshes with a bright rim, drawn in the world (not particles).
 * Shapes: WALL (curved wall = swings, rings, auras), CRESCENT (flying blade), BEAM (crossed ribbons).
 * Also draws the outline-only floating grimoire and the dark auras around the swords.
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID, value = Dist.CLIENT)
public class FxRenderer {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(ArcaneAddon.MODID, "textures/effect/white.png");

    // body (dark fill) and rim (bright edge) colours per palette, 0..1
    private static final float[][] BODY = {
            {0.06f, 0.00f, 0.04f}, {0.03f, 0.00f, 0.08f}, {0.22f, 0.05f, 0.00f}, {0.02f, 0.10f, 0.16f},
            {0.12f, 0.11f, 0.00f}, {0.04f, 0.13f, 0.09f}, {0.07f, 0.07f, 0.09f}};
    private static final float[][] RIM = {
            {1.00f, 0.16f, 0.24f}, {0.67f, 0.24f, 1.00f}, {1.00f, 0.60f, 0.12f}, {0.60f, 0.88f, 1.00f},
            {1.00f, 0.96f, 0.44f}, {0.75f, 1.00f, 0.84f}, {0.95f, 0.95f, 1.00f}};

    private static class Fx {
        FxPacket p; double born; double killAge = -1;
    }
    private static final List<Fx> FX = new ArrayList<>();

    // things queued by GrimoireFloatRenderer during the same frame
    private record BookDraw(Vec3 pos, float yaw, float tilt, float scale, float alpha, float flipCycle) {}
    private record AuraDraw(Vec3 a, Vec3 b, float width, int pal, float alpha) {}
    private static final List<BookDraw> BOOKS = new ArrayList<>();
    private static final List<AuraDraw> AURAS = new ArrayList<>();

    public static void queueBook(Vec3 pos, float yawS, float tiltDeg, float scale, float alpha, float flipCycle) { BOOKS.add(new BookDraw(pos, yawS, tiltDeg, scale, alpha, flipCycle)); }
    public static void queueAura(Vec3 a, Vec3 b, float width, int pal, float alpha) { AURAS.add(new AuraDraw(a, b, width, pal, alpha)); }

    // ------------------------------------------------------------------ lifecycle
    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (p.shape == FxPacket.KILL) {
            for (Fx f : FX) if (f.p.id == p.id && f.killAge < 0) f.killAge = mc.level.getGameTime() - f.born;
            return;
        }
        Fx f = new Fx(); f.p = p; f.born = mc.level.getGameTime();
        FX.add(f);
    }

    /** Client-only ring (used for the summon flash). */
    public static void localRing(Vec3 c, int pal, float r0, float r1, float h, int life) {
        FxPacket p = new FxPacket();
        p.shape = FxPacket.WALL; p.palette = (byte) pal; p.life = life; p.c = c; p.d = new Vec3(1, 0, 0); p.u = new Vec3(0, 0, 1);
        p.r = r0; p.r2 = r1; p.a0 = -180; p.a1 = 180; p.h = h;
        spawn(p);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { FX.clear(); return; }
        double now = mc.level.getGameTime();
        for (Iterator<Fx> it = FX.iterator(); it.hasNext(); ) {
            Fx f = it.next();
            double life = f.killAge >= 0 ? f.killAge + 4 : f.p.life;
            if (now - f.born > life + 1) it.remove();
        }
    }

    // ------------------------------------------------------------------ vertex helpers
    private static void vtx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 w, float[] col, float a) {
        vc.vertex(m, (float) (w.x - cam.x), (float) (w.y - cam.y), (float) (w.z - cam.z))
                .color(col[0], col[1], col[2], Mth.clamp(a, 0f, 1f)).uv(0.5f, 0.5f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 1, 0).endVertex();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float[] col, float aa, float ab, float ac, float ad) {
        vtx(vc, m, n, cam, a, col, aa); vtx(vc, m, n, cam, b, col, ab); vtx(vc, m, n, cam, c, col, ac); vtx(vc, m, n, cam, d, col, ad);
    }

    // ------------------------------------------------------------------ render
    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { BOOKS.clear(); AURAS.clear(); return; }
        float pt = e.getPartialTick();
        PoseStack ps = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        var buf = mc.renderBuffers().bufferSource();
        RenderType rt = RenderType.entityTranslucentEmissive(WHITE);
        VertexConsumer vc = buf.getBuffer(rt);
        double now = mc.level.getGameTime() + pt;

        Matrix4f m = ps.last().pose(); Matrix3f n = ps.last().normal();
        for (Fx f : FX) {
            double age = now - f.born;
            double life = f.killAge >= 0 ? f.killAge + 4 : f.p.life;
            if (age < 0 || age > life) continue;
            float env = (float) (Mth.clamp(age / 1.5, 0, 1) * Mth.clamp((life - age) / Math.max(2.0, life * 0.45), 0, 1));
            switch (f.p.shape) {
                case FxPacket.WALL -> wall(vc, m, n, cam, f.p, age, env);
                case FxPacket.CRESCENT -> crescentFx(vc, m, n, cam, f.p, age, env);
                case FxPacket.BEAM -> beam(vc, m, n, cam, f.p.c, f.p.c.add(f.p.d), f.p.r * (1f - 0.55f * (float) (age / life)), f.p.palette, env, 0.35);
                default -> { }
            }
        }
        // sword auras for players
        float pulse = 0.75f + 0.25f * (float) Math.sin(now * 0.35);
        for (Player p : mc.level.players()) {
            if (p.isSpectator() || p.isInvisible()) continue;
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            heldAura(vc, m, n, cam, p, pt, pulse, p.getMainHandItem().getItem(), true);
            heldAura(vc, m, n, cam, p, pt, pulse, p.getOffhandItem().getItem(), false);
        }
        for (AuraDraw a : AURAS) beam(vc, m, n, cam, a.a, a.b, a.width, a.pal, a.alpha * pulse, 0.5);
        AURAS.clear();
        buf.endBatch(rt);

        // floating grimoire (outline only)
        if (!BOOKS.isEmpty()) {
            VertexConsumer bv = buf.getBuffer(rt);
            for (BookDraw b : BOOKS) book(ps, bv, cam, b);
            buf.endBatch(rt);
            BOOKS.clear();
        }
    }

    private static void heldAura(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Player p, float pt, float pulse, net.minecraft.world.item.Item item, boolean main) {
        int pal; float len, width;
        if (item == AddonItemRegistry.DEMON_SLAYER_SWORD.get()) { pal = 0; len = 3.0f; width = 0.34f; }
        else if (item == AddonItemRegistry.DEMON_DWELLER_SWORD.get()) { pal = 1; len = 2.1f; width = 0.24f; }
        else return;
        double by = Math.toRadians(Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot));
        Vec3 fw = new Vec3(-Math.sin(by), 0, Math.cos(by)), lf = new Vec3(Math.cos(by), 0, Math.sin(by));
        boolean rightHand = (p.getMainArm() == HumanoidArm.RIGHT) == main;
        Vec3 base = new Vec3(Mth.lerp(pt, p.xo, p.getX()), Mth.lerp(pt, p.yo, p.getY()), Mth.lerp(pt, p.zo, p.getZ()));
        Vec3 hand = base.add(0, p.getEyeHeight() * 0.62, 0).add(fw.scale(0.35)).add(lf.scale(rightHand ? -0.42 : 0.42));
        boolean dash = p.isAutoSpinAttack();
        Vec3 dir = dash ? p.getViewVector(pt).normalize() : fw.scale(0.5).add(0, 0.85, 0).normalize();
        float a = (dash ? 1.0f : 0.8f) * pulse;
        beam(vc, m, n, cam, hand.subtract(dir.scale(0.25)), hand.add(dir.scale(len)), width * (dash ? 1.5f : 1f), pal, a, 0.5);
    }

    // ------------------------------------------------------------------ WALL: curved wall with soft top/bottom and a bright core line
    private static void wall(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, float env) {
        double lifeFrac = Mth.clamp(age / Math.max(1, p.life), 0, 1);
        float prog = p.sweep > 0 ? (float) Mth.clamp(age / p.sweep, 0, 1) : 1f;
        prog = prog * prog * (3 - 2 * prog);
        float R = p.r + (p.r2 - p.r) * (float) lifeFrac;
        Vec3 c = p.c.add(p.d.scale(p.speed * age));
        Vec3 h = p.d.cross(p.u).normalize();
        float a0 = p.a0, a1 = p.a0 + (p.a1 - p.a0) * prog;
        int segs = Math.max(4, (int) (Math.abs(a1 - a0) / 7));
        float[] body = BODY[p.palette], rim = RIM[p.palette];
        float[] mul = {0.92f, 1.0f, 1.09f}, am = {0.45f, 1f, 0.35f};
        for (int layer = 0; layer < 3; layer++) {
            double rl = R * mul[layer];
            for (int i = 0; i < segs; i++) {
                double t0 = (double) i / segs, t1 = (double) (i + 1) / segs;
                double th0 = Math.toRadians(a0 + (a1 - a0) * t0), th1 = Math.toRadians(a0 + (a1 - a0) * t1);
                Vec3 p0 = c.add(p.d.scale(Math.cos(th0) * rl)).add(p.u.scale(Math.sin(th0) * rl));
                Vec3 p1 = c.add(p.d.scale(Math.cos(th1) * rl)).add(p.u.scale(Math.sin(th1) * rl));
                double hh0 = p.h * (0.3 + 0.7 * Math.pow(Math.sin(Math.PI * (0.04 + 0.92 * t0)), 0.6));
                double hh1 = p.h * (0.3 + 0.7 * Math.pow(Math.sin(Math.PI * (0.04 + 0.92 * t1)), 0.6));
                float lead0 = 0.45f + 0.55f * (float) t0, lead1 = 0.45f + 0.55f * (float) t1;   // leading edge is the strongest
                float A0 = 0.62f * env * am[layer] * lead0, A1 = 0.62f * env * am[layer] * lead1;
                quad(vc, m, n, cam, p0.add(h.scale(hh0)), p1.add(h.scale(hh1)), p1, p0, body, 0, 0, A1, A0);
                quad(vc, m, n, cam, p0, p1, p1.subtract(h.scale(hh1)), p0.subtract(h.scale(hh0)), body, A0, A1, 0, 0);
                if (layer == 1) {   // bright core line
                    double lw = p.h * 0.05;
                    quad(vc, m, n, cam, p0.add(h.scale(lw)), p1.add(h.scale(lw)), p1.subtract(h.scale(lw)), p0.subtract(h.scale(lw)),
                            rim, 0.95f * env * lead0, 0.95f * env * lead1, 0.95f * env * lead1, 0.95f * env * lead0);
                }
            }
        }
        // glowing rims along the top and bottom edges
        for (int i = 0; i < segs; i++) {
            double t0 = (double) i / segs, t1 = (double) (i + 1) / segs;
            double th0 = Math.toRadians(a0 + (a1 - a0) * t0), th1 = Math.toRadians(a0 + (a1 - a0) * t1);
            Vec3 p0 = c.add(p.d.scale(Math.cos(th0) * R)).add(p.u.scale(Math.sin(th0) * R));
            Vec3 p1 = c.add(p.d.scale(Math.cos(th1) * R)).add(p.u.scale(Math.sin(th1) * R));
            double hh0 = p.h * (0.3 + 0.7 * Math.pow(Math.sin(Math.PI * (0.04 + 0.92 * t0)), 0.6));
            double hh1 = p.h * (0.3 + 0.7 * Math.pow(Math.sin(Math.PI * (0.04 + 0.92 * t1)), 0.6));
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                Vec3 e0 = p0.add(h.scale(hh0 * sgn)), e1 = p1.add(h.scale(hh1 * sgn));
                Vec3 i0 = p0.add(h.scale(hh0 * sgn * 0.9)), i1 = p1.add(h.scale(hh1 * sgn * 0.9));
                quad(vc, m, n, cam, e0, e1, i1, i0, rim, 0.55f * env, 0.55f * env, 0.1f * env, 0.1f * env);
            }
        }
    }

    // ------------------------------------------------------------------ CRESCENT: flying blade with trailing afterimages
    private static void crescentFx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, float env) {
        double lifeFrac = Mth.clamp(age / Math.max(1, p.life), 0, 1);
        float R = p.r + (p.r2 - p.r) * (float) lifeFrac;
        Vec3 c = p.c.add(p.d.scale(p.speed * age));
        Vec3 h = p.d.cross(p.u).normalize();
        for (int j = 0; j < 5; j++) {   // afterimages
            float a = env * (1f - 0.2f * j);
            crescent(vc, m, n, cam, c.subtract(p.d.scale(0.55 * j)), p.d, p.u, R * (1f - 0.09f * j), p.palette, a);
        }
        crescent(vc, m, n, cam, c.add(p.d.scale(0.05)), p.d, h, R * 0.5f, p.palette, env * 0.55f);   // crossing sheet for volume
    }

    private static void crescent(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 d, Vec3 u, double R, int pal, float env) {
        float[] body = BODY[pal], rim = RIM[pal];
        int N = 16;
        for (int i = 0; i < N; i++) {
            double f0 = -78 + 156.0 * i / N, f1 = -78 + 156.0 * (i + 1) / N;
            double r0 = Math.toRadians(f0), r1 = Math.toRadians(f1);
            Vec3 o0 = c.add(u.scale(Math.sin(r0) * R)).add(d.scale((Math.cos(r0) - 1) * R * 0.55));
            Vec3 o1 = c.add(u.scale(Math.sin(r1) * R)).add(d.scale((Math.cos(r1) - 1) * R * 0.55));
            double t0 = 0.5 * R * Math.cos(Math.toRadians(f0 / 78 * 90)), t1 = 0.5 * R * Math.cos(Math.toRadians(f1 / 78 * 90));
            Vec3 i0 = o0.subtract(d.scale(t0)), i1 = o1.subtract(d.scale(t1));
            quad(vc, m, n, cam, o0, o1, i1, i0, body, 0.85f * env, 0.85f * env, 0.15f * env, 0.15f * env);
            double w0 = 0.09 * R * Math.cos(Math.toRadians(f0 / 78 * 90)), w1 = 0.09 * R * Math.cos(Math.toRadians(f1 / 78 * 90));
            quad(vc, m, n, cam, o0.add(d.scale(w0 * 0.3)), o1.add(d.scale(w1 * 0.3)), o1.subtract(d.scale(w1)), o0.subtract(d.scale(w0)), rim, 0.98f * env, 0.98f * env, 0.7f * env, 0.7f * env);
        }
    }

    // ------------------------------------------------------------------ BEAM: two crossed ribbons, soft edges, bright core
    private static void beam(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 p0, Vec3 p1, float width, int pal, float env, double endTaper) {
        Vec3 axis = p1.subtract(p0);
        double len = axis.length();
        if (len < 1.0E-4) return;
        Vec3 d = axis.scale(1.0 / len);
        Vec3 ref = Math.abs(d.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 n1 = d.cross(ref).normalize(), n2 = d.cross(n1).normalize();
        float[] body = BODY[pal], rim = RIM[pal];
        int K = Math.max(4, Math.min(24, (int) (len * 2)));
        for (Vec3 nn : new Vec3[]{n1, n2}) {
            for (int k = 0; k < K; k++) {
                double t0 = (double) k / K, t1 = (double) (k + 1) / K;
                double w0 = width * Math.pow(Math.sin(Math.PI * (0.03 + 0.94 * t0)), endTaper), w1 = width * Math.pow(Math.sin(Math.PI * (0.03 + 0.94 * t1)), endTaper);
                Vec3 a = p0.add(axis.scale(t0)), b = p0.add(axis.scale(t1));
                float A = 0.7f * env;
                quad(vc, m, n, cam, a.add(nn.scale(w0)), b.add(nn.scale(w1)), b, a, body, 0, 0, A, A);
                quad(vc, m, n, cam, a, b, b.subtract(nn.scale(w1)), a.subtract(nn.scale(w0)), body, A, A, 0, 0);
                double cw0 = w0 * 0.12, cw1 = w1 * 0.12;
                quad(vc, m, n, cam, a.add(nn.scale(cw0)), b.add(nn.scale(cw1)), b.subtract(nn.scale(cw1)), a.subtract(nn.scale(cw0)), rim, 0.95f * env, 0.95f * env, 0.95f * env, 0.95f * env);
            }
        }
    }

    // ------------------------------------------------------------------ the floating grimoire: dark outline hologram, see-through so it never blocks the view
    private static void book(PoseStack ps, VertexConsumer vc, Vec3 cam, BookDraw b) {
        ps.pushPose();
        ps.translate(b.pos.x - cam.x, b.pos.y - cam.y, b.pos.z - cam.z);
        ps.mulPose(Axis.YP.rotationDegrees(-b.yaw));
        ps.mulPose(Axis.YP.rotationDegrees(180f));
        ps.mulPose(Axis.XP.rotationDegrees(-b.tilt));
        ps.scale(b.scale, b.scale, b.scale);
        Matrix4f m = ps.last().pose(); Matrix3f n = ps.last().normal();
        final float HW = 0.5f, HH = 0.36f, V = (float) Math.toRadians(12);
        float al = b.alpha;
        // the two halves of the open book
        for (int s = -1; s <= 1; s += 2) panel(vc, m, n, s * (float) Math.cos(V), (float) Math.sin(V), 0f, HW, HH, al);
        // flipping sheets (right half -> left half), only when open
        if (b.flipCycle >= 0) {
            for (int i = 0; i < 3; i++) {
                float sP = (b.flipCycle - (4 + i * 5)) / 14f;
                if (sP <= 0f || sP >= 1f) continue;
                float mm = 0.5f - 0.5f * (float) Math.cos(Math.PI * sP);
                double alpha = Math.toRadians(-22.5f - 135f * mm);
                float lift = 0.015f * (1f - 2f * mm);
                panel(vc, m, n, (float) Math.cos(alpha), (float) -Math.sin(alpha), lift, HW * 0.96f, HH * 0.96f, al * 0.9f);
            }
        }
        ps.popPose();
    }

    /** One page: a faint dark fill, text-line hints, and a dark border with a thin inner glow. dirX/dirZ = direction of the page from the spine. */
    private static void panel(VertexConsumer vc, Matrix4f m, Matrix3f n, float dx, float dz, float lift, float w, float h, float al) {
        float[] dark = {0.02f, 0.0f, 0.04f}, glow = {0.55f, 0.06f, 0.16f}, line = {0.05f, 0.0f, 0.08f};
        // vertices: (u along page 0..w, y -h..h)
        Vec3 a = pt(dx, dz, lift, 0, -h), b = pt(dx, dz, lift, w, -h), c = pt(dx, dz, lift, w, h), d = pt(dx, dz, lift, 0, h);
        Vec3 z = Vec3.ZERO;
        quad(vc, m, n, z, a, b, c, d, dark, 0.16f * al, 0.16f * al, 0.16f * al, 0.16f * al);
        for (int i = 1; i <= 6; i++) {   // text lines
            float y = -h + 2 * h * i / 7f;
            float len = w * (i % 3 == 0 ? 0.55f : 0.82f);
            quad(vc, m, n, z, pt(dx, dz, lift, w * 0.1f, y - 0.006f), pt(dx, dz, lift, w * 0.1f + len, y - 0.006f), pt(dx, dz, lift, w * 0.1f + len, y + 0.006f), pt(dx, dz, lift, w * 0.1f, y + 0.006f), line, 0.35f * al, 0.35f * al, 0.35f * al, 0.35f * al);
        }
        float t = 0.028f;
        edge(vc, m, n, a, b, pt(dx, dz, lift, w, -h + t), pt(dx, dz, lift, 0, -h + t), dark, al, glow);
        edge(vc, m, n, d, c, pt(dx, dz, lift, w, h - t), pt(dx, dz, lift, 0, h - t), dark, al, glow);
        edge(vc, m, n, a, d, pt(dx, dz, lift, t, h), pt(dx, dz, lift, t, -h), dark, al, glow);
        edge(vc, m, n, b, c, pt(dx, dz, lift, w - t, h), pt(dx, dz, lift, w - t, -h), dark, al, glow);
    }

    private static void edge(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 o0, Vec3 o1, Vec3 i1, Vec3 i0, float[] dark, float al, float[] glow) {
        quad(vc, m, n, Vec3.ZERO, o0, o1, i1, i0, dark, 0.95f * al, 0.95f * al, 0.95f * al, 0.95f * al);
        quad(vc, m, n, Vec3.ZERO, i0, i1, i1.add(i1.subtract(o1).scale(0.25)), i0.add(i0.subtract(o0).scale(0.25)), glow, 0.65f * al, 0.65f * al, 0f, 0f);
    }

    private static Vec3 pt(float dx, float dz, float lift, float u, float y) { return new Vec3(dx * u, y, dz * u + lift); }
}
