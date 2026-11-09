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
}
