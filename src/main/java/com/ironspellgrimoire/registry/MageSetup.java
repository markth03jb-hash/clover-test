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
        e.put(AddonEntities.MOLT_HUSK.get(), com.ironspellgrimoire.entity.MoltHusk.attributes().build());
        e.put(AddonEntities.TWIN_WARDEN.get(), com.ironspellgrimoire.entity.TwinWarden.attributes().build());
        e.put(AddonEntities.INFECTED_BRUTE.get(), com.ironspellgrimoire.entity.InfectedBrute.attributes().build());
        e.put(AddonEntities.ZENON.get(), com.ironspellgrimoire.entity.Zenon.attributes().build());
        e.put(AddonEntities.BEAST_OF_DESPAIR.get(), com.ironspellgrimoire.entity.BeastOfDespair.attributes().build());
        e.put(AddonEntities.RHYA.get(), com.ironspellgrimoire.entity.Rhya.attributes().build());
        e.put(AddonEntities.LICHT.get(), com.ironspellgrimoire.entity.Licht.attributes().build());
        e.put(AddonEntities.REVENANT.get(), com.ironspellgrimoire.entity.Revenant.attributes().build());
        e.put(AddonEntities.DANTE.get(), com.ironspellgrimoire.entity.Dante.attributes().build());
        e.put(AddonEntities.SEREN_VALE.get(), com.ironspellgrimoire.entity.SerenVale.attributes().build());
        e.put(AddonEntities.BRANNOCH_TOR.get(), com.ironspellgrimoire.entity.BrannochTor.attributes().build());
        e.put(AddonEntities.ALDER_BELLMAR.get(), com.ironspellgrimoire.entity.AlderBellmar.attributes().build());
        e.put(AddonEntities.WREN_BELLMAR.get(), com.ironspellgrimoire.entity.WrenBellmar.attributes().build());
        e.put(AddonEntities.MIRA_HOLLOWELL.get(), com.ironspellgrimoire.entity.MiraHollowell.attributes().build());
        e.put(AddonEntities.TOR_LEGIONNAIRE.get(), com.ironspellgrimoire.entity.TorLegionnaire.attributes().build());
        e.put(AddonEntities.CURSE_WITCH.get(), com.ironspellgrimoire.entity.CurseWitch.attributes().build());
        e.put(AddonEntities.REFLECTION.get(), com.ironspellgrimoire.entity.Reflection.attributes().build());
        e.put(AddonEntities.MEMORY_GUARDIAN.get(), com.ironspellgrimoire.entity.MemoryGuardian.attributes().build());
        e.put(AddonEntities.CINDER_SOVEREIGN.get(), com.ironspellgrimoire.entity.CinderSovereign.attributes().build());
        e.put(AddonEntities.FORTRESS_DEFENDER.get(), com.ironspellgrimoire.entity.FortressDefender.attributes().build());
        e.put(AddonEntities.FORESEEN_DOUBLE.get(), com.ironspellgrimoire.entity.ForeseenDouble.attributes().build());
        e.put(AddonEntities.OMEN_AFTERIMAGE.get(), com.ironspellgrimoire.entity.OmenAfterimage.attributes().build());
        e.put(AddonEntities.SHADOW_SOLDIER.get(), com.ironspellgrimoire.entity.ShadowSoldier.attributes().build());
        e.put(AddonEntities.OATHBOUND_KNIGHT.get(), com.ironspellgrimoire.entity.ShadowSoldier.attributes().build());
        e.put(AddonEntities.SHADOW_RAVENER.get(), com.ironspellgrimoire.entity.ShadowSoldier.attributes().build());
        for (var en : AddonEntities.FOLK.entrySet()) e.put(en.getValue().get(), en.getKey().attributes().build());
        e.put(AddonEntities.TWIN_HALDRIC.get(), com.ironspellgrimoire.entity.IronboundTwin.attributes().build());
        e.put(AddonEntities.TWIN_OSWIN.get(), com.ironspellgrimoire.entity.IronboundTwin.attributes().build());
        e.put(AddonEntities.STORY_CORVIN.get(), com.ironspellgrimoire.entity.StoryNpc.attributes().build());
        e.put(AddonEntities.STORY_ILSA.get(), com.ironspellgrimoire.entity.StoryNpc.attributes().build());
        e.put(AddonEntities.PENITENT.get(), com.ironspellgrimoire.entity.Penitent.attributes().build());
        e.put(AddonEntities.FATHER_SALLOW.get(), com.ironspellgrimoire.entity.FatherSallow.attributes().build());
        e.put(AddonEntities.STORY_TAMSIN.get(), com.ironspellgrimoire.entity.StoryNpc.attributes().build());
        e.put(AddonEntities.STORY_VEY.get(), com.ironspellgrimoire.entity.StoryNpc.attributes().build());
        e.put(AddonEntities.TAMSIN_DUELIST.get(), com.ironspellgrimoire.entity.TamsinDuelist.attributes().build());
        e.put(AddonEntities.MAGISTRATE_VEY.get(), com.ironspellgrimoire.entity.MagistrateVey.attributes().build());
        e.put(AddonEntities.WARDEN.get(), com.ironspellgrimoire.entity.Warden.attributes().build());
        e.put(AddonEntities.STORY_MAREN.get(), com.ironspellgrimoire.entity.StoryNpc.attributes().build());
        e.put(AddonEntities.STORY_SEDGE.get(), com.ironspellgrimoire.entity.StoryNpc.attributes().build());
        e.put(AddonEntities.STORY_REFUGEE.get(), com.ironspellgrimoire.entity.StoryNpc.attributes().build());
        e.put(AddonEntities.MILITIA.get(), com.ironspellgrimoire.entity.Militia.attributes().build());
        e.put(AddonEntities.MOTHER_BELOW.get(), com.ironspellgrimoire.entity.MotherBelow.attributes().build());
    }

    @SubscribeEvent
    public static void spawns(SpawnPlacementRegisterEvent e) {
        e.register(AddonEntities.MAGE.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        for (var t : AddonEntities.FOLK.values())
            e.register(t.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
}
