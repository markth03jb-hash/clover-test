package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.CinderSovereign;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** The Cinder Sovereign, drawn 2.2 times its model's size (about fifteen blocks tall), its molten cracks, eyes, heart and braziers glowing. */
public class CinderSovereignRenderer extends GeoEntityRenderer<CinderSovereign> {
    public CinderSovereignRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        withScale(2.2f);
        this.shadowRadius = 3.5f;
    }

    /** It doesn't topple sideways when it dies - its own death plays instead. */
    @Override protected float getDeathMaxRotation(CinderSovereign e) { return 0f; }

    static class Model extends GeoModel<CinderSovereign> {
        private static final ResourceLocation GEO = new ResourceLocation("iron_spell_grimoire", "geo/cinder_sovereign.geo.json");
        private static final ResourceLocation TEX = new ResourceLocation("iron_spell_grimoire", "textures/entity/cinder_sovereign.png");
        private static final ResourceLocation ANIM = new ResourceLocation("iron_spell_grimoire", "animations/cinder_sovereign.animation.json");

        @Override public ResourceLocation getModelResource(CinderSovereign e) { return GEO; }
        @Override public ResourceLocation getTextureResource(CinderSovereign e) { return TEX; }
        @Override public ResourceLocation getAnimationResource(CinderSovereign e) { return ANIM; }

        /** Its head turns to follow whoever it's watching (on top of its animations). */
        @Override
        public void setCustomAnimations(CinderSovereign e, long id, AnimationState<CinderSovereign> state) {
            super.setCustomAnimations(e, id, state);
            CoreGeoBone head = getAnimationProcessor().getBone("head");
            EntityModelData d = state.getData(DataTickets.ENTITY_MODEL_DATA);
            if (head != null && d != null && !e.isDeadOrDying()) {
                head.setRotY(head.getRotY() + Mth.clamp(d.netHeadYaw(), -35f, 35f) * Mth.DEG_TO_RAD);
                head.setRotX(head.getRotX() + Mth.clamp(d.headPitch(), -20f, 20f) * Mth.DEG_TO_RAD * 0.5f);
            }
        }
    }
}
