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

    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.CurseWitch>> CURSE_WITCH = ENTITIES.register("curse_witch",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.CurseWitch>of(com.ironspellgrimoire.entity.CurseWitch::new, MobCategory.MONSTER).sized(0.6F, 1.9F)
                    .fireImmune().clientTrackingRange(10).build("curse_witch"));

    /** The Ascension Trial's examiner: you, in your own skin (server/Ascension). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.Reflection>> REFLECTION = ENTITIES.register("reflection",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.Reflection>of(com.ironspellgrimoire.entity.Reflection::new, MobCategory.MONSTER).sized(0.6F, 1.8F)
                    .fireImmune().clientTrackingRange(10).build("reflection"));
}
