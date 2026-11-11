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
        e.put(AddonEntities.ZENON.get(), com.ironspellgrimoire.entity.Zenon.attributes().build());
        e.put(AddonEntities.BEAST_OF_DESPAIR.get(), com.ironspellgrimoire.entity.BeastOfDespair.attributes().build());
        e.put(AddonEntities.RHYA.get(), com.ironspellgrimoire.entity.Rhya.attributes().build());
        e.put(AddonEntities.LICHT.get(), com.ironspellgrimoire.entity.Licht.attributes().build());
        e.put(AddonEntities.REVENANT.get(), com.ironspellgrimoire.entity.Revenant.attributes().build());
        e.put(AddonEntities.CURSE_WITCH.get(), com.ironspellgrimoire.entity.CurseWitch.attributes().build());
        e.put(AddonEntities.REFLECTION.get(), com.ironspellgrimoire.entity.Reflection.attributes().build());
        e.put(AddonEntities.MEMORY_GUARDIAN.get(), com.ironspellgrimoire.entity.MemoryGuardian.attributes().build());
    }

    @SubscribeEvent
    public static void spawns(SpawnPlacementRegisterEvent e) {
        e.register(AddonEntities.MAGE.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
}
