package com.ironspellgrimoire.client;

import com.ironspellgrimoire.item.DragonFangItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** The Dragon Fang, drawn from its GeckoLib model; its ember veins, eyes and orb glow (dragon_fang_glowmask). */
public class DragonFangRenderer extends GeoItemRenderer<DragonFangItem> {
    public DragonFangRenderer() {
        super(new GeoModel<>() {
            private final ResourceLocation geo = new ResourceLocation("iron_spell_grimoire", "geo/item/dragon_fang.geo.json");
            private final ResourceLocation tex = new ResourceLocation("iron_spell_grimoire", "textures/item/dragon_fang.png");
            private final ResourceLocation anim = new ResourceLocation("iron_spell_grimoire", "animations/item/dragon_fang.animation.json");
            @Override public ResourceLocation getModelResource(DragonFangItem a) { return geo; }
            @Override public ResourceLocation getTextureResource(DragonFangItem a) { return tex; }
            @Override public ResourceLocation getAnimationResource(DragonFangItem a) { return anim; }
        });
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
