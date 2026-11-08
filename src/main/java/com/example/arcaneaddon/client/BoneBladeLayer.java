package com.example.arcaneaddon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Zenon's Bone Sword. Third person: held in the right hand, drawn in the arm's own space so it is one solid piece
 * that follows every frame of the swing. First person: drawn in your view, cutting with the same three swings.
 * The sword: a pommel knob, a grip of stacked vertebrae, a guard of two curved rib-prongs, and a long bone blade -
 * a lozenge cross-section with a ridge, a row of jagged teeth along the back edge and a sharp point. It grows out of
 * the hand over a few ticks and crumbles back at the end.
 * Fed by FxPacket shape 37 (id = player, life = ticks, r = blade length, palette 1 = Beelzebub: a violet-black edge).
 */
@EventBusSubscriber(modid = "arcane_addon", value = {Dist.CLIENT})
public class BoneBladeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("arcane_addon", "textures/effect/white.png");
    /** player id -> {start, end, length, union} */
    private static final Map<Integer, double[]> ACTIVE = new HashMap<>();
    private static final float[] IVORY = {0.93F, 0.89F, 0.78F}, SHADOW = {0.66F, 0.6F, 0.49F}, KNOB = {0.85F, 0.8F, 0.68F};
    private static final Vector3f LIGHT = new Vector3f(0.3F, -0.8F, -0.5F).normalize();   // the arm's +y points down

    public BoneBladeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    public static void set(int id, int life, float length, boolean union) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime();
        if (life <= 0) ACTIVE.remove(id);
        else ACTIVE.put(id, new double[]{now, now + life, length, union ? 1 : 0});
    }

    private static float grow(double[] f, double now) {
        return (float) Mth.clamp((now - f[0]) / 4.0, 0.0, 1.0) * (float) Mth.clamp((f[1] + 4 - now) / 4.0, 0.0, 1.0);
    }

    // ------------------------------------------------------------------ third person: on the arm
    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                       float pt, float age, float netHeadYaw, float headPitch) {
        double[] f = ACTIVE.get(p.getId());
        if (f == null || p.isInvisible()) return;
        double now = p.level().getGameTime() + pt;
        if (now > f[1] + 4) { ACTIVE.remove(p.getId()); return; }
        float g = grow(f, now);
        if (g <= 0.01F) return;
        ps.pushPose();
        getParentModel().rightArm.translateAndRotate(ps);
        ps.translate(-1.0F / 16.0F, 0.6F, -0.03F);   // in the fist
        drawSword(ps, buf, light, (float) f[2] * g, g, f[3] > 0.5);
        ps.popPose();
    }

    // ------------------------------------------------------------------ first person: in your view
    /** {tick, yaw, pitch, roll}: wind up right, sweep left, backhand right, overhead chop - the same three cuts. */
    private static final float[][] FP_KEYS = {{0, 10, 25, 0}, {3, 55, 15, -55}, {5, -60, 0, -70}, {9, -70, 8, 60}, {12, 55, 0, 70},
            {16, 0, 75, 0}, {20, 0, -45, 0}, {26, 10, 25, 0}};

    private static float[] fpAt(double t) {
        if (t <= FP_KEYS[0][0]) return FP_KEYS[0];
        for (int i = 1; i < FP_KEYS.length; i++) {
            if (t <= FP_KEYS[i][0]) {
                float a = (float) ((t - FP_KEYS[i - 1][0]) / (FP_KEYS[i][0] - FP_KEYS[i - 1][0]));
                a = a * a * (3 - 2 * a);
                float[] o = new float[4];
                for (int k = 1; k < 4; k++) o[k] = FP_KEYS[i - 1][k] + (FP_KEYS[i][k] - FP_KEYS[i - 1][k]) * a;
                return o;
            }
        }
        return FP_KEYS[FP_KEYS.length - 1];
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent e) {
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        double[] f = ACTIVE.get(mc.player.getId());
        if (f == null) return;
        double now = mc.level.getGameTime() + e.getPartialTick();
        if (now > f[1] + 4) return;
        float g = grow(f, now);
        if (g <= 0.01F) return;
        float[] k = fpAt(now - f[0]);
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(0.56F, -0.5F, -0.7F);                // right where your hand is in first person
        ps.mulPose(Axis.YP.rotationDegrees(-k[1]));
        ps.mulPose(Axis.XP.rotationDegrees(-k[2]));
        ps.mulPose(Axis.ZP.rotationDegrees(k[3]));
        ps.mulPose(Axis.XP.rotationDegrees(180.0F));       // arm space: +y down, blade toward -z
        ps.scale(0.85F, 0.85F, 0.85F);
        drawSword(ps, e.getMultiBufferSource(), e.getPackedLight(), (float) f[2] * g, g, f[3] > 0.5);
        ps.popPose();
    }

    // ------------------------------------------------------------------ the sword itself (blade along -z, +y = down the arm)
    private static void drawSword(PoseStack ps, MultiBufferSource buf, int light, float L, float g, boolean union) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        // everything lies along the blade's line (-z), through the fist: pommel behind it, grip in it, guard in front
        prism(vc, m, n, 0, 0, 0.12F, 0, 0, 0.18F, 0.05F, 0.065F, 8, KNOB, light);      // pommel knob, just behind the fist
        prism(vc, m, n, 0, 0, 0.18F, 0, 0, 0.22F, 0.065F, 0.025F, 8, KNOB, light);
        for (int i = 0; i < 3; i++) {   // grip: stacked vertebrae, inside the fist
            float z0 = 0.12F - i * 0.085F, z1 = z0 - 0.065F;
            prism(vc, m, n, 0, 0, z0, 0, 0, z1, 0.042F, 0.042F, 8, IVORY, light);
            prism(vc, m, n, 0, 0, z1, 0, 0, z1 - 0.02F, 0.055F, 0.055F, 8, SHADOW, light);
        }
        for (int s = -1; s <= 1; s += 2) {   // guard: two rib-prongs curving forward along the blade
            float px = 0, pz = -0.16F;
            for (int i = 1; i <= 5; i++) {
                double a = i / 5.0 * Math.PI * 0.5;
                float nx = s * (float) Math.sin(a) * 0.24F, nz = -0.16F - (1.0F - (float) Math.cos(a)) * 0.16F;
                prism(vc, m, n, px, 0, pz, nx, 0, nz, 0.035F - i * 0.004F, 0.03F - i * 0.004F, 6, IVORY, light);
                px = nx; pz = nz;
            }
        }
        prism(vc, m, n, 0, 0, -0.13F, 0, 0, -0.24F, 0.06F, 0.055F, 8, KNOB, light);   // the collar the blade grows from
        // blade: a long bone, lozenge in section, narrowing to the point, with a dark ridge and teeth down the back
        float z0 = -0.24F, z1 = z0 - L;
        int segs = 8;
        for (int i = 0; i < segs; i++) {
            float a = (float) i / segs, b = (float) (i + 1) / segs;
            float wa = 0.075F * (1.0F - a * 0.5F), wb = 0.075F * (1.0F - b * 0.5F);
            float ta = 0.03F * (1.0F - a * 0.4F), tb = 0.03F * (1.0F - b * 0.4F);
            float za = z0 + (z1 - z0) * a, zb = z0 + (z1 - z0) * b;
            lozenge(vc, m, n, za, zb, wa, wb, ta, tb, IVORY, light, 0F);
            lozenge(vc, m, n, za, zb, wa * 0.22F, wb * 0.22F, ta * 1.5F, tb * 1.5F, SHADOW, light, 0F);   // the ridge
            if (i < segs - 1) {   // a jagged tooth on the back edge
                float zt = (za + zb) * 0.5F;
                tri(vc, m, n, -wa * 0.95F, 0, za, -wa * 0.95F, 0, zt, -wa * 1.55F, 0, za - (zb - za) * 0.15F, IVORY, light);
            }
        }
        float pz = z1 - 0.26F * g;   // the point
        tri(vc, m, n, -0.0375F, 0, z1, 0.0375F, 0, z1, 0, 0, pz, IVORY, light);
        tri(vc, m, n, 0, -0.018F, z1, 0, 0.018F, z1, 0, 0, pz, SHADOW, light);
        if (union) {   // Beelzebub: space itself clings to the edge - a thin violet-black glow along the blade
            VertexConsumer gl = buf.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
            float[] v = {0.35F, 0.08F, 0.55F};
            lozengeA(gl, m, n, z0, pz, 0.12F, 0.02F, 0.004F, 0.004F, v, LightTexture.FULL_BRIGHT, 0.45F, 0F);
        }
    }

    /** A tapered n-sided prism from (x0,y0,z0) to (x1,y1,z1), radius r0 -> r1, simply shaded. */
    private static void prism(VertexConsumer vc, Matrix4f m, Matrix3f n, float x0, float y0, float z0, float x1, float y1, float z1,
                              float r0, float r1, int sides, float[] c, int light) {
        Vector3f ax = new Vector3f(x1 - x0, y1 - y0, z1 - z0);
        if (ax.lengthSquared() < 1.0E-8F) return;
        ax.normalize();
        Vector3f ref = Math.abs(ax.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f u = new Vector3f(ax).cross(ref).normalize(), w = new Vector3f(ax).cross(u).normalize();
        for (int i = 0; i < sides; i++) {
            double a0 = Math.PI * 2 * i / sides, a1 = Math.PI * 2 * (i + 1) / sides;
            Vector3f d0 = new Vector3f(u).mul((float) Math.cos(a0)).add(new Vector3f(w).mul((float) Math.sin(a0)));
            Vector3f d1 = new Vector3f(u).mul((float) Math.cos(a1)).add(new Vector3f(w).mul((float) Math.sin(a1)));
            Vector3f nm = new Vector3f(d0).add(d1).normalize();
            float sh = 0.62F + 0.38F * Math.max(0.0F, -nm.dot(LIGHT));
            quad(vc, m, n, x0 + d0.x * r0, y0 + d0.y * r0, z0 + d0.z * r0, x1 + d0.x * r1, y1 + d0.y * r1, z1 + d0.z * r1,
                    x1 + d1.x * r1, y1 + d1.y * r1, z1 + d1.z * r1, x0 + d1.x * r0, y0 + d1.y * r0, z0 + d1.z * r0, c, sh, light, 1F);
        }
    }

    private static void lozenge(VertexConsumer vc, Matrix4f m, Matrix3f n, float za, float zb, float wa, float wb, float ta, float tb, float[] c, int light, float y) {
        lozengeA(vc, m, n, za, zb, wa, wb, ta, tb, c, light, 1F, y);
    }

    /** A flat segment of blade from za to zb: half width wa -> wb, half thickness ta -> tb (a lozenge cross-section). */
    private static void lozengeA(VertexConsumer vc, Matrix4f m, Matrix3f n, float za, float zb, float wa, float wb, float ta, float tb, float[] c, int light, float alpha, float y) {
        quad(vc, m, n, -wa, y, za, -wb, y, zb, 0, y - tb, zb, 0, y - ta, za, c, 1.0F, light, alpha);
        quad(vc, m, n, 0, y - ta, za, 0, y - tb, zb, wb, y, zb, wa, y, za, c, 0.88F, light, alpha);
        quad(vc, m, n, wa, y, za, wb, y, zb, 0, y + tb, zb, 0, y + ta, za, c, 0.68F, light, alpha);
        quad(vc, m, n, 0, y + ta, za, 0, y + tb, zb, -wb, y, zb, -wa, y, za, c, 0.6F, light, alpha);
    }

    private static void tri(VertexConsumer vc, Matrix4f m, Matrix3f n, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float[] c, int light) {
        quad(vc, m, n, ax, ay, az, bx, by, bz, cx, cy, cz, cx, cy, cz, c, 0.85F, light, 1F);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, float[] c, float shade, int light, float a) {
        v(vc, m, n, x0, y0, z0, c, shade, light, a, 0, 0);
        v(vc, m, n, x1, y1, z1, c, shade, light, a, 1, 0);
        v(vc, m, n, x2, y2, z2, c, shade, light, a, 1, 1);
        v(vc, m, n, x3, y3, z3, c, shade, light, a, 0, 1);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float[] c, float shade, int light, float a, float u, float w) {
        vc.vertex(m, x, y, z).color(c[0] * shade, c[1] * shade, c[2] * shade, a).uv(u, w).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
    }
}
