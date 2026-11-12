package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.BeastOfDespair;
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

/**
 * The Beast of Despair on screen (GeckoLib model and moves made by tools/beast_boss.py): a hunched beast-man in charcoal
 * fur under a bone mask, his eyes and the roots of his claws glowing. The Rhinoceros Armour's plates and horn show only
 * while he's armoured, his third eye only from Mythical Beast on, and in Despair the glow turns red.
 */
public class BeastRenderer extends GeoEntityRenderer<BeastOfDespair> {
    public BeastRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        withScale(1.1f);
        this.shadowRadius = 1.2f;
    }

    /** He doesn't topple sideways when he dies - his own death plays instead. */
    @Override protected float getDeathMaxRotation(BeastOfDespair e) { return 0f; }

    static class Model extends GeoModel<BeastOfDespair> {
        private static final ResourceLocation GEO = new ResourceLocation("iron_spell_grimoire", "geo/beast_of_despair.geo.json");
        private static final ResourceLocation TEX = new ResourceLocation("iron_spell_grimoire", "textures/entity/beast_of_despair_geo.png");
        private static final ResourceLocation TEX_DESPAIR = new ResourceLocation("iron_spell_grimoire", "textures/entity/beast_of_despair_despair.png");
        private static final ResourceLocation ANIM = new ResourceLocation("iron_spell_grimoire", "animations/beast_of_despair.animation.json");

        @Override public ResourceLocation getModelResource(BeastOfDespair e) { return GEO; }
        @Override public ResourceLocation getTextureResource(BeastOfDespair e) { return e.phase() >= 3 ? TEX_DESPAIR : TEX; }
        @Override public ResourceLocation getAnimationResource(BeastOfDespair e) { return ANIM; }

        @Override
        public void setCustomAnimations(BeastOfDespair e, long id, AnimationState<BeastOfDespair> state) {
            super.setCustomAnimations(e, id, state);
            CoreGeoBone armor = getAnimationProcessor().getBone("armor"), horn = getAnimationProcessor().getBone("horn"),
                    eye = getAnimationProcessor().getBone("third_eye"), head = getAnimationProcessor().getBone("head");
            boolean armored = e.armored() > 0;
            if (armor != null) armor.setHidden(!armored);
            if (horn != null) horn.setHidden(!armored);
            if (eye != null) eye.setHidden(e.phase() < 2);
            EntityModelData d = state.getData(DataTickets.ENTITY_MODEL_DATA);
            if (head != null && d != null && !e.isDeadOrDying() && e.transforming() == 0) {   // he watches whoever he's after
                head.setRotY(head.getRotY() + Mth.clamp(d.netHeadYaw(), -40f, 40f) * Mth.DEG_TO_RAD);
                head.setRotX(head.getRotX() + Mth.clamp(d.headPitch(), -25f, 25f) * Mth.DEG_TO_RAD * 0.5f);
            }
        }
    }
}
