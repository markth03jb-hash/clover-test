package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.ForeseenDouble;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** The False Future double: a player's shape in pale ice-white, rimmed in soft blue, see-through (textures/entity/foreseen_double.png). */
public class ForeseenDoubleRenderer extends HumanoidMobRenderer<ForeseenDouble, PlayerModel<ForeseenDouble>> {
    static final ResourceLocation TEX = new ResourceLocation("iron_spell_grimoire", "textures/entity/foreseen_double.png");
    public ForeseenDoubleRenderer(EntityRendererProvider.Context ctx) { super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0f); }
    @Override public ResourceLocation getTextureLocation(ForeseenDouble e) { return TEX; }
    @Override protected RenderType getRenderType(ForeseenDouble e, boolean visible, boolean translucent, boolean glowing) { return RenderType.entityTranslucent(TEX); }
}
