package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.OmenAfterimage;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** The Afterimage: a player's shape in black, rimmed in deep blue, faintly see-through (textures/entity/omen_afterimage.png). */
public class OmenAfterimageRenderer extends HumanoidMobRenderer<OmenAfterimage, PlayerModel<OmenAfterimage>> {
    static final ResourceLocation TEX = new ResourceLocation("iron_spell_grimoire", "textures/entity/omen_afterimage.png");

    public OmenAfterimageRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0f);
    }

    @Override public ResourceLocation getTextureLocation(OmenAfterimage e) { return TEX; }

    @Override
    protected RenderType getRenderType(OmenAfterimage e, boolean visible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(TEX);
    }
}
