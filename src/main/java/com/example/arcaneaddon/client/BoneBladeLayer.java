package com.example.arcaneaddon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Zenon's Bone Sword, held in the right hand and drawn in the arm's own space, so it is one solid piece that follows
 * every frame of the swing: a knobbed bone grip in the fist, a cross-bone guard, and a long sharpened bone blade with
 * a ridge down the middle. It grows out of the hand over a few ticks and crumbles back at the end.
 * Fed by FxPacket shape 37 (id = player, life = ticks, r = blade length, palette 1 = Beelzebub: a violet-black edge).
 */
public class BoneBladeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("arcane_addon", "textures/effect/white.png");
    /** player id -> {start, end, length, union} */
    private static final Map<Integer, double[]> ACTIVE = new HashMap<>();

    public BoneBladeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    public static void set(int id, int life, float length, boolean union) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime();
        if (life <= 0) ACTIVE.remove(id);
        else ACTIVE.put(id, new double[]{now, now + life, length, union ? 1 : 0});
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                       float pt, float age, float netHeadYaw, float headPitch) {
        double[] f = ACTIVE.get(p.getId());
        if (f == null || p.isInvisible()) return;
        double now = p.level().getGameTime() + pt;
        if (now > f[1] + 4) { ACTIVE.remove(p.getId()); return; }
        float grow = (float) Mth.clamp((now - f[0]) / 4.0, 0.0, 1.0) * (float) Mth.clamp((f[1] + 4 - now) / 4.0, 0.0, 1.0);
        if (grow <= 0.01F) return;
        float L = (float) f[2] * grow;
        boolean union = f[3] > 0.5;
        ps.pushPose();
        getParentModel().rightArm.translateAndRotate(ps);
        ps.translate(-1.0F / 16.0F, 0.62F, 0.0F);   // the fist
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        float[] ivory = {0.93F, 0.89F, 0.78F}, dark = {0.62F, 0.57F, 0.47F};
        // grip: a short bone through the fist, knobbed at the bottom
        box(vc, m, n, -0.045F, 0.045F, -0.045F, 0.045F, -0.12F, 0.1F, dark, light);
        box(vc, m, n, -0.07F, 0.07F, -0.07F, 0.07F, 0.1F, 0.2F, ivory, light);
        // guard: a cross-bone with knobs on each end
        box(vc, m, n, -0.26F, 0.26F, -0.05F, 0.05F, -0.2F, -0.11F, ivory, light);
        box(vc, m, n, -0.32F, -0.22F, -0.07F, 0.07F, -0.22F, -0.09F, ivory, light);
        box(vc, m, n, 0.22F, 0.32F, -0.07F, 0.07F, -0.22F, -0.09F, ivory, light);
        // blade: forward from the guard (-z), wide flat bone narrowing to a sharpened point, a ridge along it
        float z0 = -0.2F, z1 = z0 - L;
        int segs = 6;
        for (int i = 0; i < segs; i++) {
            float a = (float) i / segs, b = (float) (i + 1) / segs;
            float wa = 0.075F * (1.0F - a * 0.55F), wb = 0.075F * (1.0F - b * 0.55F);
            float ta = 0.028F * (1.0F - a * 0.4F), tb = 0.028F * (1.0F - b * 0.4F);
            taper(vc, m, n, z0 + (z1 - z0) * a, z0 + (z1 - z0) * b, wa, wb, ta, tb, ivory, light);
            taper(vc, m, n, z0 + (z1 - z0) * a, z0 + (z1 - z0) * b, wa * 0.25F, wb * 0.25F, ta * 1.6F, tb * 1.6F, dark, light);   // the ridge
        }
        // the point
        float pz = z1 - 0.22F * grow;
        tri(vc, m, n, -0.033F, 0, z1, 0.033F, 0, z1, 0, 0, pz, ivory, light);
        tri(vc, m, n, 0, -0.017F, z1, 0, 0.017F, z1, 0, 0, pz, ivory, light);
        if (union) {   // Beelzebub: space itself clings to the edge - a thin violet-black glow along the blade
            VertexConsumer gl = buf.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
            float[] v = {0.35F, 0.08F, 0.55F};
            taper(gl, m, n, z0, pz, 0.11F, 0.02F, 0.004F, 0.004F, v, 15728880, 0.45F);
        }
        ps.popPose();
    }

    private static void box(VertexConsumer vc, Matrix4f m, Matrix3f n, float x0, float x1, float y0, float y1, float z0, float z1, float[] c, int light) {
        quad(vc, m, n, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, c, 0.8F, light, 1F);
        quad(vc, m, n, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, c, 0.8F, light, 1F);
        quad(vc, m, n, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, c, 0.7F, light, 1F);
        quad(vc, m, n, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, c, 0.7F, light, 1F);
        quad(vc, m, n, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, c, 1.0F, light, 1F);
        quad(vc, m, n, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, c, 0.55F, light, 1F);
    }

    private static void taper(VertexConsumer vc, Matrix4f m, Matrix3f n, float za, float zb, float wa, float wb, float ta, float tb, float[] c, int light) {
        taper(vc, m, n, za, zb, wa, wb, ta, tb, c, light, 1F);
    }

    /** A flat segment of blade from za to zb: half width wa -> wb, half thickness ta -> tb (a lozenge cross-section). */
    private static void taper(VertexConsumer vc, Matrix4f m, Matrix3f n, float za, float zb, float wa, float wb, float ta, float tb, float[] c, int light, float alpha) {
        quad(vc, m, n, -wa, 0, za, -wb, 0, zb, 0, -tb, zb, 0, -ta, za, c, 1.0F, light, alpha);
        quad(vc, m, n, 0, -ta, za, 0, -tb, zb, wb, 0, zb, wa, 0, za, c, 0.9F, light, alpha);
        quad(vc, m, n, wa, 0, za, wb, 0, zb, 0, tb, zb, 0, ta, za, c, 0.7F, light, alpha);
        quad(vc, m, n, 0, ta, za, 0, tb, zb, -wb, 0, zb, -wa, 0, za, c, 0.6F, light, alpha);
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
