package com.ironspellgrimoire.client;

import com.ironspellgrimoire.registry.AddonEntities;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The magic bolt draws nothing itself - its move paints it with the addon's own effects. */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientEntities {
    private ClientEntities() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(AddonEntities.MAGIC_BOLT.get(), NoopRenderer::new);
        e.registerEntityRenderer(AddonEntities.MAGE.get(), MageRenderer::new);
        e.registerEntityRenderer(AddonEntities.ZENON.get(), ZenonRenderer::new);
        e.registerEntityRenderer(AddonEntities.VETTO.get(), VettoRenderer::new);
        e.registerEntityRenderer(AddonEntities.RHYA.get(), RhyaRenderer::new);
        e.registerEntityRenderer(AddonEntities.RIVAL.get(), RivalRenderer::new);
    }
}
