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
 * Don Quixote's Vampire Mode on the player: a long cloak of blood with a torn, dripping hem that sways and streams
 * behind as you move, thin threads of blood rising round you, and two red eyes glowing in the face.
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

        // ---- the cape: a long cloak of blood from the shoulders. It sways, streams out behind you with speed, has a
        //      glossy wine-red outside and a near-black lining, and its hem tears into points that drip.
        ps.pushPose();
        m.body.translateAndRotate(ps);
        Matrix4f mat = ps.last().pose();
        Matrix3f nor = ps.last().normal();
        float speed = (float) Math.min(1.0, p.getDeltaMovement().horizontalDistance() * 5.0) + limbSwingAmount * 0.5F;
        int strips = 14, rows = 10;
        float half = 0.34F, len = 1.5F * k;
        for (int s = 0; s < strips; s++) {
            float u0 = s / (float) strips, u1 = (s + 1) / (float) strips, um = (u0 + u1) * 0.5F;
            float rag = ragged(s);
            for (int r = 0; r < rows; r++) {
                float v0 = r / (float) rows, v1 = (r + 1) / (float) rows;
                boolean hem = r == rows - 1;
                // the last row of every strip narrows to a point: a torn, pointed hem
                float ua1 = hem ? um - (um - u0) * 0.15F : u0, ub1 = hem ? um + (u1 - um) * 0.15F : u1;
                float[] a = capePt(u0, v0, half, len * rag, speed, now, s), b = capePt(u1, v0, half, len * rag, speed, now, s);
                float[] c = capePt(ub1, v1, half, len * rag, speed, now, s), d = capePt(ua1, v1, half, len * rag, speed, now, s);
                // a sheen running down the folds
                float fold = 0.5F + 0.5F * (float) Math.sin(um * 22 + now * 0.15);
                float sheen = (float) Math.pow(fold, 6) * 0.5F;
                float[] col = {0.32F + 0.18F * fold + sheen, 0.0F + sheen * 0.25F, 0.03F + sheen * 0.25F};
                float al = 0.95F * k;
                quad(vc, mat, nor, a, b, c, d, col, al);
                quad(vc, mat, nor, d, c, b, a, new float[]{0.06F, 0.0F, 0.01F}, al);   // the lining
            }
            // a drop gathering at the hem's point and falling away
            float[] tip = capePt(um, 1F, half, len * rag, speed, now, s);
            double ph = (now * 0.06 + s * 0.37) % 1.0;
            if (s % 2 == 0) {
                float y = tip[1] + (float) (ph * ph) * 0.9F, sz = 0.025F + 0.015F * (float) Math.min(1, ph * 4);
                float fa = (float) (1.0 - ph) * k;
                quad(vc, mat, nor, new float[]{tip[0] - sz, y - sz * 1.6F, tip[2]}, new float[]{tip[0] + sz, y - sz * 1.6F, tip[2]},
                        new float[]{tip[0] + sz, y + sz, tip[2]}, new float[]{tip[0] - sz, y + sz, tip[2]}, new float[]{0.55F, 0.0F, 0.04F}, fa);
            }
        }
        // ---- a subtle aura: thin threads of blood rising round you, curling, fading as they climb
        for (int w = 0; w < 9; w++) {
            double ph = (now * 0.025 + w * 0.29) % 1.0;
            double ang = w * 2.4 + now * 0.03 + ph * 2.0;
            float rr = 0.42F + 0.08F * (float) Math.sin(w * 1.7);
            float x = (float) Math.cos(ang) * rr, z = (float) Math.sin(ang) * rr * 0.7F;
            float y = 0.9F - (float) ph * 1.9F;
            float h = 0.16F, ww = 0.012F;
            float fa = (float) Math.sin(ph * Math.PI) * 0.45F * k;
            quad(vc, mat, nor, new float[]{x - ww, y, z}, new float[]{x + ww, y, z}, new float[]{x + ww * 0.3F, y - h, z}, new float[]{x - ww * 0.3F, y - h, z},
                    new float[]{0.7F, 0.02F, 0.06F}, fa);
            quad(vc, mat, nor, new float[]{x, y, z - ww}, new float[]{x, y, z + ww}, new float[]{x, y - h, z + ww * 0.3F}, new float[]{x, y - h, z - ww * 0.3F},
                    new float[]{0.7F, 0.02F, 0.06F}, fa);
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
                    new float[]{0.9F, 0.0F, 0.05F}, 0.18F * k * pulse);
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
