package com.ironspellgrimoire.registry;

import com.ironspellgrimoire.entity.MagicBolt;
import com.ironspellgrimoire.entity.MageEnemy;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class AddonEntities {
    private AddonEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES, "iron_spell_grimoire");

    public static final RegistryObject<EntityType<MagicBolt>> MAGIC_BOLT = ENTITIES.register("magic_bolt",
            () -> EntityType.Builder.<MagicBolt>of(MagicBolt::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .clientTrackingRange(6).updateInterval(1).noSave().fireImmune().build("magic_bolt"));

    public static final RegistryObject<EntityType<MageEnemy>> MAGE = ENTITIES.register("hooded_mage",
            () -> EntityType.Builder.<MageEnemy>of(MageEnemy::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .clientTrackingRange(8).build("hooded_mage"));

    /** Zenon: a three-phase boss of bone, space and devil power (spawn egg or /summon only). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.Zenon>> ZENON = ENTITIES.register("zenon",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.Zenon>of(com.ironspellgrimoire.entity.Zenon::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .fireImmune().clientTrackingRange(10).build("zenon"));

    /** BeastOfDespair, Beast of Despair: a three-phase Beast Magic boss (spawn egg or /summon only). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.BeastOfDespair>> BEAST_OF_DESPAIR = ENTITIES.register("beast_of_despair",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.BeastOfDespair>of(com.ironspellgrimoire.entity.BeastOfDespair::new, MobCategory.MONSTER).sized(0.9F, 2.6F)
                    .fireImmune().clientTrackingRange(10).build("beast_of_despair"));

    /** Rhya the Disloyal: a three-phase Copy Magic boss that fights with your own grimoire (spawn egg or /summon only). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.Rhya>> RHYA = ENTITIES.register("rhya",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.Rhya>of(com.ironspellgrimoire.entity.Rhya::new, MobCategory.MONSTER).sized(0.6F, 2.0F)
                    .fireImmune().clientTrackingRange(10).build("rhya"));


    /** Licht, Revenant and CurseWitch (entity/AddonBoss). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.Licht>> LICHT = ENTITIES.register("licht",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.Licht>of(com.ironspellgrimoire.entity.Licht::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .fireImmune().clientTrackingRange(10).build("licht"));

    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.Revenant>> REVENANT = ENTITIES.register("revenant",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.Revenant>of(com.ironspellgrimoire.entity.Revenant::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .fireImmune().clientTrackingRange(10).build("revenant"));
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.Dante>> DANTE = ENTITIES.register("dante",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.Dante>of(com.ironspellgrimoire.entity.Dante::new, MobCategory.MONSTER).sized(0.6F, 1.95F)
                    .fireImmune().clientTrackingRange(10).build("dante"));

    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.CurseWitch>> CURSE_WITCH = ENTITIES.register("curse_witch",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.CurseWitch>of(com.ironspellgrimoire.entity.CurseWitch::new, MobCategory.MONSTER).sized(0.6F, 1.9F)
                    .fireImmune().clientTrackingRange(10).build("curse_witch"));

    /** The Cinder Sovereign, the world boss (entity/CinderSovereign, server/WorldBoss). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.CinderSovereign>> CINDER_SOVEREIGN = ENTITIES.register("cinder_sovereign",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.CinderSovereign>of(com.ironspellgrimoire.entity.CinderSovereign::new, MobCategory.MONSTER).sized(4.5F, 12.5F)
                    .fireImmune().clientTrackingRange(16).build("cinder_sovereign"));

    /** A Memory Guardian (entity/MemoryGuardian, server/MemoryPages). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.MemoryGuardian>> MEMORY_GUARDIAN = ENTITIES.register("memory_guardian",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.MemoryGuardian>of(com.ironspellgrimoire.entity.MemoryGuardian::new, MobCategory.MONSTER).sized(0.6F, 1.8F)
                    .fireImmune().clientTrackingRange(10).build("memory_guardian"));

    /** A mana familiar (entity/FamiliarEntity, server/Familiars). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.FamiliarEntity>> FAMILIAR = ENTITIES.register("familiar",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.FamiliarEntity>of(com.ironspellgrimoire.entity.FamiliarEntity::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .fireImmune().clientTrackingRange(8).updateInterval(2).build("familiar"));

    /** Pack Call's infected brutes (entity/InfectedBrute). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.InfectedBrute>> INFECTED_BRUTE = ENTITIES.register("infected_brute",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.InfectedBrute>of(com.ironspellgrimoire.entity.InfectedBrute::new, MobCategory.MISC).sized(0.6F, 1.95F)
                    .clientTrackingRange(10).build("infected_brute"));

    /** The Hunger Blade thrown (entity/ThrownHungerBlade). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.ThrownHungerBlade>> THROWN_HUNGER_BLADE = ENTITIES.register("thrown_hunger_blade",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.ThrownHungerBlade>of(com.ironspellgrimoire.entity.ThrownHungerBlade::new, MobCategory.MISC).sized(0.8F, 0.8F)
                    .fireImmune().clientTrackingRange(8).updateInterval(1).build("thrown_hunger_blade"));

    /** The Hunger Blade's spirit (entity/BladeSpirit). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.BladeSpirit>> BLADE_SPIRIT = ENTITIES.register("blade_spirit",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.BladeSpirit>of(com.ironspellgrimoire.entity.BladeSpirit::new, MobCategory.MISC).sized(0.8F, 0.8F)
                    .fireImmune().clientTrackingRange(8).updateInterval(1).build("blade_spirit"));

    /** The Masked Twins' trial: the Dawn Warden and the Dusk Warden (entity/TwinWarden). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.TwinWarden>> TWIN_WARDEN = ENTITIES.register("twin_warden",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.TwinWarden>of(com.ironspellgrimoire.entity.TwinWarden::new, MobCategory.MONSTER).sized(1.0F, 2.6F)
                    .fireImmune().clientTrackingRange(10).build("twin_warden"));

    /** The Masked Twins' Echo: the resting twin's mask, fighting beside you (entity/TwinEcho). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.TwinEcho>> TWIN_ECHO = ENTITIES.register("twin_echo",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.TwinEcho>of(com.ironspellgrimoire.entity.TwinEcho::new, MobCategory.MISC).sized(0.6F, 0.6F)
                    .fireImmune().clientTrackingRange(8).updateInterval(1).build("twin_echo"));

    /** The Parasite Grimoire's molted husk (entity/MoltHusk). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.MoltHusk>> MOLT_HUSK = ENTITIES.register("molt_husk",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.MoltHusk>of(com.ironspellgrimoire.entity.MoltHusk::new, MobCategory.MISC).sized(0.6F, 1.8F)
                    .clientTrackingRange(10).build("molt_husk"));

    /** The Parasite Grimoire's Hive Nest (entity/HiveNest). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.HiveNest>> HIVE_NEST = ENTITIES.register("hive_nest",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.HiveNest>of(com.ironspellgrimoire.entity.HiveNest::new, MobCategory.MISC).sized(1.6F, 0.8F)
                    .fireImmune().clientTrackingRange(10).updateInterval(10).build("hive_nest"));

    /** A Magic Broom, ridden (entity/BroomEntity). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.BroomEntity>> BROOM = ENTITIES.register("broom",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.BroomEntity>of(com.ironspellgrimoire.entity.BroomEntity::new, MobCategory.MISC).sized(0.9F, 0.4F)
                    .fireImmune().clientTrackingRange(10).updateInterval(1).build("broom"));

    /** The Ascension Trial's examiner: you, in your own skin (server/Ascension). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.Reflection>> REFLECTION = ENTITIES.register("reflection",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.Reflection>of(com.ironspellgrimoire.entity.Reflection::new, MobCategory.MONSTER).sized(0.6F, 1.8F)
                    .fireImmune().clientTrackingRange(10).build("reflection"));

    /** A shadow soldier of the Shadow Grimoire: the shadow of a mob its master slew and raised (server/ShadowArmy). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.ShadowSoldier>> SHADOW_SOLDIER = ENTITIES.register("shadow_soldier",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.ShadowSoldier>of(com.ironspellgrimoire.entity.ShadowSoldier::new, MobCategory.MISC).sized(0.6F, 1.9F)
                    .fireImmune().clientTrackingRange(10).build("shadow_soldier"));

    /** The Shadow Grimoire's exclusive shadow, the Oathbound Knight (an original design). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.OathboundKnight>> OATHBOUND_KNIGHT = ENTITIES.register("oathbound_knight",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.OathboundKnight>of(com.ironspellgrimoire.entity.OathboundKnight::new, MobCategory.MISC).sized(0.9F, 2.9F)
                    .fireImmune().clientTrackingRange(10).build("oathbound_knight"));
    /** The Shadow Grimoire's second exclusive shadow, the Ravener (an original design). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.ShadowRavener>> SHADOW_RAVENER = ENTITIES.register("ravener",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.ShadowRavener>of(com.ironspellgrimoire.entity.ShadowRavener::new, MobCategory.MISC).sized(0.9F, 2.8F)
                    .fireImmune().clientTrackingRange(10).build("ravener"));
}
