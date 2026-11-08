package com.example.arcaneaddon.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;

/**
 * The addon's own sounds: an original set per grimoire (assets/arcane_addon/sounds.json), made from scratch -
 * slash, blast, whoosh, impact, charge, fire, thunder and roar, two takes each, picked at random when played.
 */
public final class AddonSounds {
    private AddonSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "arcane_addon");
    static final Map<String, RegistryObject<SoundEvent>> BY_KEY = new HashMap<>();

    private static final String[][] SETS = {
            {"asta", "blast", "charge", "impact", "roar", "slash", "thunder", "whoosh"},
            {"devil", "blast", "charge", "slash", "whoosh"},
            {"yami", "blast", "charge", "impact", "slash", "whoosh"},
            {"yuno", "blast", "charge", "impact", "slash", "whoosh"},
            {"nozel", "blast", "charge", "impact", "slash", "whoosh"},
            {"magna", "blast", "fire", "impact", "slash"},
            {"luck", "charge", "slash", "thunder"},
            {"mereoleona", "blast", "charge", "fire", "roar", "slash"},
            {"zenon", "blast", "charge", "impact", "roar", "slash", "whoosh"},
            {"klaus", "blast", "charge", "fire", "impact", "slash", "thunder", "whoosh"},
    };

    static {
        for (String[] set : SETS) {
            for (int i = 1; i < set.length; i++) {
                String key = set[0] + "." + set[i];
                BY_KEY.put(key, SOUNDS.register(key, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("arcane_addon", key))));
            }
        }
    }

    /** The registered sound for "grimoire.category", or null. */
    public static SoundEvent get(String key) {
        RegistryObject<SoundEvent> r = BY_KEY.get(key);
        return r != null && r.isPresent() ? r.get() : null;
    }
}
