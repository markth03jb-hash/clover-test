package com.example.arcaneaddon.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;

/**
 * The moves' sounds (assets/arcane_addon/sounds.json): copyright-free (CC0) clips - Kenney's "Sci-Fi Sounds" and
 * StarNinjas' "20 sword sound effects" (see CREDITS.txt) - one event per kind, several takes picked at random.
 */
public final class AddonSounds {
    private AddonSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "arcane_addon");
    private static final Map<String, RegistryObject<SoundEvent>> BY_KIND = new HashMap<>();

    static {
        for (String k : new String[]{"slash", "blast", "whoosh", "impact", "charge", "fire", "thunder", "roar"}) {
            String key = "sfx." + k;
            BY_KIND.put(k, SOUNDS.register(key, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("arcane_addon", key))));
        }
    }

    /** The registered sound for a kind ("slash", "blast"...), or null. */
    public static SoundEvent get(String kind) {
        RegistryObject<SoundEvent> r = BY_KIND.get(kind);
        return r != null && r.isPresent() ? r.get() : null;
    }
}
