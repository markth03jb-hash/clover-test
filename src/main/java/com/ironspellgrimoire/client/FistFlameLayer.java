package com.ironspellgrimoire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
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
 * Fists on fire, attached to the arms themselves so the flames follow every punch and swing: Magna's Flame Knuckles
 * (orange) and Mereoleona's Hellfire Incarnate (crimson). Drawn in the arm's own space - a flickering ball of flame
 * around each hand, with a glow around it.
 */
public class FistFlameLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation SMOKE = ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/effect/dark_smoke.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/effect/dark_glow.png");

    public FistFlameLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                       float pt, float age, float netHeadYaw, float headPitch) {
        if (p.isInvisible()) return;
        double now = p.level().getGameTime() + pt;
        int kind = DarkSmoke.fistFire(p.getId(), now);   // 0 none, 1 orange (Magna), 2 crimson (Mereoleona)
        if (kind == 0) return;
        float[] hot = kind == 2 ? new float[]{1.0F, 0.55F, 0.35F} : new float[]{1.0F, 0.85F, 0.4F};
        float[] edge = kind == 2 ? new float[]{0.85F, 0.08F, 0.05F} : new float[]{1.0F, 0.42F, 0.05F};
        PlayerModel<AbstractClientPlayer> m = getParentModel();
        hand(ps, buf, m.rightArm, -1.0F / 16.0F, now, hot, edge, p.getId());
        hand(ps, buf, m.leftArm, 1.0F / 16.0F, now + 7.3, hot, edge, p.getId() + 3);
    }

    private static void hand(PoseStack ps, MultiBufferSource buf, ModelPart arm, float x, double now, float[] hot, float[] edge, int seed) {
        ps.pushPose();
        arm.translateAndRotate(ps);
        ps.translate(x, 0.6F, 0.0F);   // the fist, at the end of the arm
        Matrix4f mat = ps.last().pose();
        Matrix3f nor = ps.last().normal();
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(SMOKE));
        for (int k = 0; k < 3; k++) {   // three crossed flickering planes of flame around the fist
            double a = Math.PI * k / 3 + now * 0.2;
            float s = 0.2F + 0.05F * (float) Math.sin(now * 1.3 + k * 2.1 + seed);
            float lift = 0.12F + 0.05F * (float) Math.sin(now * 0.9 + k);
            quad(vc, mat, nor, (float) Math.cos(a), (float) Math.sin(a), s, -lift, edge, 0.85F);
            quad(vc, mat, nor, (float) Math.cos(a + 0.5), (float) Math.sin(a + 0.5), s * 0.6F, -lift * 0.6F, hot, 0.9F);
        }
        VertexConsumer gl = buf.getBuffer(RenderType.eyes(GLOW));
        float g = 0.55F + 0.15F * (float) Math.sin(now * 1.7 + seed);
        quad(gl, mat, nor, 1, 0, 0.42F, 0, new float[]{edge[0] * g, edge[1] * g, edge[2] * g}, 1F);
        quad(gl, mat, nor, 0, 1, 0.42F, 0, new float[]{edge[0] * g, edge[1] * g, edge[2] * g}, 1F);
        ps.popPose();
    }

    /** A square of half size s in the vertical plane along (cx, 0, cz), shifted along the arm by dy (negative = past the knuckles). */
    private static void quad(VertexConsumer vc, Matrix4f mat, Matrix3f nor, float cx, float cz, float s, float dy, float[] col, float a) {
        float ux = cx * s, uz = cz * s;
        v(vc, mat, nor, -ux, dy - s, -uz, col, a, 0, 1);
        v(vc, mat, nor, ux, dy - s, uz, col, a, 1, 1);
        v(vc, mat, nor, ux, dy + s, uz, col, a, 1, 0);
        v(vc, mat, nor, -ux, dy + s, -uz, col, a, 0, 0);
    }

    private static void v(VertexConsumer vc, Matrix4f mat, Matrix3f nor, float x, float y, float z, float[] c, float a, float u, float w) {
        vc.vertex(mat, x, y, z).color(c[0], c[1], c[2], a).uv(u, w).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(nor, 0, 1, 0).endVertex();
    }
}
