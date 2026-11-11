package com.ironspellgrimoire.client;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.FamiliarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** A familiar: its 32px sprite (textures/entity/familiar/<kind>.png) facing you, glowing, bobbing - bigger as it grows, with a halo at stage 3 and a flare as it casts. */
public class FamiliarRenderer extends EntityRenderer<FamiliarEntity> {
    private static final String[] KINDS = {"salamander", "sylph", "wisp", "moth", "golem", "droplet", "spark", "clockwork", "crystal", "bone"};
    private static final ResourceLocation[] TEX = new ResourceLocation[KINDS.length];
    private static final ResourceLocation GLOW = new ResourceLocation(IronSpellGrimoire.MODID, "textures/entity/familiar/glow.png");
    static { for (int i = 0; i < KINDS.length; i++) TEX[i] = new ResourceLocation(IronSpellGrimoire.MODID, "textures/entity/familiar/" + KINDS[i] + ".png"); }

    public FamiliarRenderer(EntityRendererProvider.Context ctx) { super(ctx); this.shadowRadius = 0f; }

    @Override
    public void render(FamiliarEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        int kind = Mth.clamp(e.kind(), 0, KINDS.length - 1), stage = Mth.clamp(e.stage(), 1, 3);
        float t = e.tickCount + pt;
        float since = e.tickCount - e.castAt() + pt;
        float flare = e.castAt() > 0 && since >= 0 && since < 8 ? 1f - since / 8f : 0f;
        float size = (0.42f + stage * 0.12f) * (1f + flare * 0.25f);
        ps.pushPose();
        ps.translate(0, 0.25 + Mth.sin(t * 0.15f) * 0.05f, 0);
        ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
        ps.mulPose(Axis.YP.rotationDegrees(180f));
        if (stage >= 3 || flare > 0) {   // the halo behind it
            ps.pushPose();
            ps.translate(0, 0, 0.02);
            ps.mulPose(Axis.ZP.rotationDegrees(t * 2f));
            float g = size * (stage >= 3 ? 1.7f : 1.3f) * (1f + flare * 0.4f);
            quad(ps, buf.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), g, stage >= 3 ? 0.55f + flare * 0.45f : flare * 0.8f);
            ps.popPose();
        }
        quad(ps, buf.getBuffer(RenderType.entityTranslucentEmissive(TEX[kind])), size, 1f);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    private static void quad(PoseStack ps, VertexConsumer vc, float s, float a) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        int al = (int) (Mth.clamp(a, 0f, 1f) * 255);
        v(vc, m, n, -s, -s, 0, 1, al); v(vc, m, n, s, -s, 1, 1, al); v(vc, m, n, s, s, 1, 0, al); v(vc, m, n, -s, s, 0, 0, al);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float w, int a) {
        vc.vertex(m, x, y, 0).color(255, 255, 255, a).uv(u, w).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
    }

    @Override public ResourceLocation getTextureLocation(FamiliarEntity e) { return TEX[Mth.clamp(e.kind(), 0, KINDS.length - 1)]; }

    @Override protected boolean shouldShowName(FamiliarEntity e) { return e.hasCustomName() && e.stage() >= 2 && this.entityRenderDispatcher.distanceToSqr(e) < 64; }
}
