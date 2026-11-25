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
        e.registerEntityRenderer(AddonEntities.ELITE_RIFT.get(), EliteRenderers.RiftRenderer::new);
        e.registerEntityRenderer(AddonEntities.CURSED_CHEST.get(), EliteRenderers.ChestRenderer::new);
        e.registerEntityRenderer(AddonEntities.ZENON.get(), ZenonRenderer::new);
        e.registerEntityRenderer(AddonEntities.BEAST_OF_DESPAIR.get(), BeastRenderer::new);
        e.registerEntityRenderer(AddonEntities.RHYA.get(), RhyaRenderer::new);
        e.registerEntityRenderer(AddonEntities.LICHT.get(), c -> new BossRenderer<>(c, "licht", true, 1.05F));
        e.registerEntityRenderer(AddonEntities.REVENANT.get(), c -> new BossRenderer<>(c, "revenant", false, 1.0F));
        e.registerEntityRenderer(AddonEntities.DANTE.get(), c -> { BossRenderer<com.ironspellgrimoire.entity.Dante> r = new BossRenderer<>(c, UiShots.ON ? "dante_mannequin" : "dante", true, 1.05F); return r.with(new DanteCloakLayer(r)); });   // his skin (slim arms) is supplied by the player; the cloak is drawn over it
        e.registerEntityRenderer(AddonEntities.CURSE_WITCH.get(), c -> new BossRenderer<>(c, "curse_witch", true, 0.95F));
        e.registerEntityRenderer(AddonEntities.SEREN_VALE.get(), c -> new RivalRenderer<>(c, "rival_seren", true, 1.0F));
        e.registerEntityRenderer(AddonEntities.BRANNOCH_TOR.get(), c -> new RivalRenderer<>(c, "rival_brannoch", false, 1.22F));
        e.registerEntityRenderer(AddonEntities.ALDER_BELLMAR.get(), c -> new RivalRenderer<>(c, "rival_alder", false, 1.0F));
        e.registerEntityRenderer(AddonEntities.WREN_BELLMAR.get(), c -> new RivalRenderer<>(c, "rival_wren", true, 1.0F));
        e.registerEntityRenderer(AddonEntities.MIRA_HOLLOWELL.get(), c -> new RivalRenderer<>(c, "rival_mira", true, 1.0F));
        e.registerEntityRenderer(AddonEntities.TOR_LEGIONNAIRE.get(), c -> new RivalRenderer<>(c, "rival_legion", false, 1.0F));
        e.registerEntityRenderer(AddonEntities.REFLECTION.get(), ReflectionRenderer::new);
        e.registerEntityRenderer(AddonEntities.OMEN_AFTERIMAGE.get(), OmenAfterimageRenderer::new);
        e.registerEntityRenderer(AddonEntities.FORESEEN_DOUBLE.get(), ForeseenDoubleRenderer::new);
        e.registerEntityRenderer(AddonEntities.BROOM.get(), BroomRenderer::new);
        e.registerEntityRenderer(AddonEntities.HIVE_NEST.get(), HiveNestRenderer::new);
        e.registerEntityRenderer(AddonEntities.MOLT_HUSK.get(), MoltHuskRenderer::new);
        e.registerEntityRenderer(AddonEntities.THROWN_HUNGER_BLADE.get(), ThrownBladeRenderer::new);
        e.registerEntityRenderer(AddonEntities.BLADE_SPIRIT.get(), SpiritRenderer::new);
        e.registerEntityRenderer(AddonEntities.TWIN_WARDEN.get(), TwinsMasks.WardenRenderer::new);
        e.registerEntityRenderer(AddonEntities.FORTRESS_WALL.get(), FortressRenderers.WallRenderer::new);
        e.registerEntityRenderer(AddonEntities.FORTRESS_TOWER.get(), FortressRenderers.TowerRenderer::new);
        e.registerEntityRenderer(AddonEntities.FORTRESS_DEFENDER.get(), FortressRenderers.DefenderRenderer::new);
        e.registerEntityRenderer(AddonEntities.TWIN_ECHO.get(), TwinsMasks.EchoRenderer::new);
        e.registerEntityRenderer(AddonEntities.INFECTED_BRUTE.get(), InfectedBruteRenderer::new);
        e.registerEntityRenderer(AddonEntities.FAMILIAR.get(), FamiliarRenderer::new);
        e.registerEntityRenderer(AddonEntities.MEMORY_GUARDIAN.get(), MemoryGuardianRenderer::new);
        e.registerEntityRenderer(AddonEntities.CINDER_SOVEREIGN.get(), CinderSovereignRenderer::new);
        e.registerEntityRenderer(AddonEntities.SHADOW_SOLDIER.get(), ShadowSoldierRenderer::new);
        e.registerEntityRenderer(AddonEntities.OATHBOUND_KNIGHT.get(), OathboundKnightRenderer::new);
        e.registerEntityRenderer(AddonEntities.SHADOW_RAVENER.get(), ShadowRavenerRenderer::new);
    }
}
