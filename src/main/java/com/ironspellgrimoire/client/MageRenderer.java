package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.MageEnemy;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** The hooded mage: a robed humanoid; the glow of its eyes and trim shows which element it casts. */
public class MageRenderer extends HumanoidMobRenderer<MageEnemy, HumanoidModel<MageEnemy>> {
    private static final ResourceLocation[] TEX = {
            new ResourceLocation("iron_spell_grimoire", "textures/entity/hooded_mage_fire.png"),
            new ResourceLocation("iron_spell_grimoire", "textures/entity/hooded_mage_wind.png"),
            new ResourceLocation("iron_spell_grimoire", "textures/entity/hooded_mage_lightning.png"),
            new ResourceLocation("iron_spell_grimoire", "textures/entity/hooded_mage_water.png")};

    public MageRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override public ResourceLocation getTextureLocation(MageEnemy m) { return TEX[Math.floorMod(m.element(), 4)]; }
}
