package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.ShadowRavener;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The Ravener (an original design): black shadow lit by the world, its burning eyes and the smoke boiling off it drawn
 * again on top, full bright, from the _glowmask; a little bigger with each rank. Model and texture: tools/shadow_models.py.
 */
public class ShadowRavenerRenderer extends GeoEntityRenderer<ShadowRavener> {
    private static final ResourceLocation GLOW = new ResourceLocation("iron_spell_grimoire", "textures/geo/shadow_ravener_glowmask.png");
    private static final ResourceLocation GLOW_LIVING = new ResourceLocation("iron_spell_grimoire", "textures/geo/shadow_ravener_living_glowmask.png");

    public ShadowRavenerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        this.shadowRadius = 0.6f;
        addRenderLayer(new GeoRenderLayer<>(this) {
            @Override
            public void render(PoseStack ps, ShadowRavener a, BakedGeoModel model, RenderType rt, MultiBufferSource buf, VertexConsumer vc, float pt, int light, int overlay) {
                RenderType g = RenderType.entityTranslucentEmissive(a.living() ? GLOW_LIVING : GLOW);   // a Challenge: its living colours
                getRenderer().reRender(model, ps, buf, a, g, buf.getBuffer(g), pt, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            }
        });
    }

    /** Cut-out, both sides drawn: the smoke wisps show through. */
    @Override
    public RenderType getRenderType(ShadowRavener a, ResourceLocation texture, MultiBufferSource buf, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public void preRender(PoseStack ps, ShadowRavener a, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        if (!isReRender) {
            float sc = a.rankScale();
            ps.scale(sc, sc, sc);
        }
        super.preRender(ps, a, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /** It doesn't topple when it falls - it sinks back into its master's shadow. */
    @Override protected float getDeathMaxRotation(ShadowRavener e) { return 0f; }

    static class Model extends GeoModel<ShadowRavener> {
        private static final ResourceLocation GEO = new ResourceLocation("iron_spell_grimoire", "geo/shadow_ravener.geo.json");
        private static final ResourceLocation TEX = new ResourceLocation("iron_spell_grimoire", "textures/geo/shadow_ravener.png");
        private static final ResourceLocation TEX_LIVING = new ResourceLocation("iron_spell_grimoire", "textures/geo/shadow_ravener_living.png");
        private static final ResourceLocation ANIM = new ResourceLocation("iron_spell_grimoire", "animations/shadow_ravener.animation.json");

        @Override public ResourceLocation getModelResource(ShadowRavener e) { return GEO; }
        @Override public ResourceLocation getTextureResource(ShadowRavener e) { return e.living() ? TEX_LIVING : TEX; }
        @Override public ResourceLocation getAnimationResource(ShadowRavener e) { return ANIM; }

        /** Its head turns toward what it watches, on top of its animations. */
        @Override
        public void setCustomAnimations(ShadowRavener e, long id, AnimationState<ShadowRavener> state) {
            super.setCustomAnimations(e, id, state);
            CoreGeoBone head = getAnimationProcessor().getBone("head");
            EntityModelData d = state.getData(DataTickets.ENTITY_MODEL_DATA);
            if (head != null && d != null) {
                head.setRotY(head.getRotY() + Mth.clamp(d.netHeadYaw(), -40f, 40f) * Mth.DEG_TO_RAD);
                head.setRotX(head.getRotX() + Mth.clamp(d.headPitch(), -25f, 25f) * Mth.DEG_TO_RAD * 0.6f);
            }
        }
    }
}
