package com.ironspellgrimoire.registry;

import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.level.levelgen.Heightmap;

@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MageSetup {
    private MageSetup() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent e) {
        e.put(AddonEntities.MAGE.get(), com.ironspellgrimoire.entity.MageEnemy.attributes().build());
        e.put(AddonEntities.OSSUARCH.get(), com.ironspellgrimoire.entity.Ossuarch.attributes().build());
    }

    @SubscribeEvent
    public static void spawns(SpawnPlacementRegisterEvent e) {
        e.register(AddonEntities.MAGE.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
}
