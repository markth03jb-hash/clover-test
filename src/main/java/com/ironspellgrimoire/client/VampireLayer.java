package com.ironspellgrimoire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Don Quixote's Vampire Mode on the player: a long tattered cape of blood hanging from the shoulders that sways and
 * streams behind as you move, and two red eyes glowing in the face.
 */
public class VampireLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation WHITE = new ResourceLocation("iron_spell_grimoire", "textures/effect/white.png");

    public VampireLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                       float pt, float age, float netHeadYaw, float headPitch) {
        double now = p.level().getGameTime() + pt;
        float k = LimbusClient.vampireAmount(p.getId(), now);
        if (k <= 0.01F || p.isInvisible()) return;
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        PlayerModel<AbstractClientPlayer> m = getParentModel();

        // ---- the cape: strips hanging from the shoulders, swaying, streaming back with speed, ragged at the hem
        ps.pushPose();
        m.body.translateAndRotate(ps);
        Matrix4f mat = ps.last().pose();
        Matrix3f nor = ps.last().normal();
        float speed = (float) Math.min(1.0, p.getDeltaMovement().horizontalDistance() * 5.0) + limbSwingAmount * 0.5F;
        int strips = 10, rows = 8;
        float half = 0.36F, len = 1.25F * k;
        for (int s = 0; s < strips; s++) {
            float u0 = s / (float) strips, u1 = (s + 1) / (float) strips;
            float rag0 = ragged(s), rag1 = ragged(s + 1);
            for (int r = 0; r < rows; r++) {
                float v0 = r / (float) rows, v1 = (r + 1) / (float) rows;
                float[] a = capePt(u0, v0, half, len * rag0, speed, now, s), b = capePt(u1, v0, half, len * rag1, speed, now, s + 1);
                float[] c = capePt(u1, v1, half, len * rag1, speed, now, s + 1), d = capePt(u0, v1, half, len * rag0, speed, now, s);
                float shade = 0.55F + 0.45F * (float) Math.sin(u0 * 9 + now * 0.2) * 0.3F + 0.25F * (1 - v0);
                float[] col = {0.45F * shade + 0.05F, 0.01F, 0.04F * shade};
                float al = 0.92F * k * (r == rows - 1 ? 0.6F : 1F);
                quad(vc, mat, nor, a, b, c, d, col, al);
                quad(vc, mat, nor, d, c, b, a, new float[]{col[0] * 0.6F, col[1], col[2] * 0.6F}, al);   // the inside, darker
            }
        }
        // the collar: a stiff high ring of hardblood round the neck
        for (int s = 0; s < 8; s++) {
            double a0 = Math.PI * (0.1 + 0.8 * s / 8.0), a1 = Math.PI * (0.1 + 0.8 * (s + 1) / 8.0);
            float[] p0 = {(float) Math.cos(a0) * 0.3F, 0.02F, (float) Math.sin(a0) * 0.2F + 0.02F}, p1 = {(float) Math.cos(a1) * 0.3F, 0.02F, (float) Math.sin(a1) * 0.2F + 0.02F};
            float[] q0 = {p0[0] * 1.2F, -0.22F, p0[2] * 1.3F}, q1 = {p1[0] * 1.2F, -0.22F, p1[2] * 1.3F};
            quad(vc, mat, nor, p0, p1, q1, q0, new float[]{0.6F, 0.02F, 0.06F}, 0.95F * k);
            quad(vc, mat, nor, q0, q1, p1, p0, new float[]{0.3F, 0.0F, 0.03F}, 0.95F * k);
        }
        ps.popPose();

        // ---- the eyes
        ps.pushPose();
        m.head.translateAndRotate(ps);
        Matrix4f hm = ps.last().pose();
        Matrix3f hn = ps.last().normal();
        float pulse = 0.75F + 0.25F * (float) Math.sin(now * 0.3);
        for (int side = -1; side <= 1; side += 2) {
            float cx = side * 0.125F, cy = -0.25F, z = -0.252F, w = 0.06F, h = 0.025F;
            quad(vc, hm, hn, new float[]{cx - w, cy - h, z}, new float[]{cx + w, cy - h, z}, new float[]{cx + w, cy + h, z}, new float[]{cx - w, cy + h, z},
                    new float[]{1.0F, 0.12F, 0.15F}, k * pulse);
            float gw = w * 2.2F, gh = h * 3F;   // a soft glow round each eye
            quad(vc, hm, hn, new float[]{cx - gw, cy - gh, z - 0.002F}, new float[]{cx + gw, cy - gh, z - 0.002F}, new float[]{cx + gw, cy + gh, z - 0.002F}, new float[]{cx - gw, cy + gh, z - 0.002F},
                    new float[]{0.9F, 0.0F, 0.05F}, 0.3F * k * pulse);
        }
        ps.popPose();
    }

    /** How far each strip of the cape reaches (a torn hem). */
    private static float ragged(int s) {
        return 0.78F + 0.22F * (float) (0.5 + 0.5 * Math.sin(s * 2.7 + 1.3));
    }

    /** A point on the cape: u across the shoulders, v down its length; it swings back with speed and ripples. */
    private static float[] capePt(float u, float v, float half, float len, float speed, double now, int s) {
        float x = -half + u * half * 2F;
        x *= 1F + v * 0.45F;   // it flares toward the hem
        float y = 0.02F + v * len;
        float back = 0.16F + v * v * (0.25F + speed * 0.9F);
        float wave = (float) Math.sin(now * 0.25 - v * 4.0 + u * 3.0) * 0.06F * v * (0.5F + speed);
        float lift = -v * v * speed * 0.35F;
        return new float[]{x, y + lift, back + wave};
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float[] a, float[] b, float[] c, float[] d, float[] col, float al) {
        v(vc, m, n, a, col, al); v(vc, m, n, b, col, al); v(vc, m, n, c, col, al); v(vc, m, n, d, col, al);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float[] p, float[] c, float al) {
        vc.vertex(m, p[0], p[1], p[2]).color(c[0], c[1], c[2], al).uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(n, 0, 0, 1).endVertex();
    }
}
