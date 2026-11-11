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

    /** Vetto, Beast of Despair: a three-phase Beast Magic boss (spawn egg or /summon only). */
    public static final RegistryObject<EntityType<com.ironspellgrimoire.entity.Vetto>> VETTO = ENTITIES.register("vetto",
            () -> EntityType.Builder.<com.ironspellgrimoire.entity.Vetto>of(com.ironspellgrimoire.entity.Vetto::new, MobCategory.MONSTER).sized(0.9F, 2.6F)
                    .fireImmune().clientTrackingRange(10).build("vetto"));
}
