package com.ironspellgrimoire.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;

/**
 * The moves' sounds (assets/iron_spell_grimoire/sounds.json): copyright-free (CC0) clips - Kenney's "Sci-Fi Sounds" and
 * StarNinjas' "20 sword sound effects" (see CREDITS.txt) - one event per kind, several takes picked at random.
 */
public final class AddonSounds {
    private AddonSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "iron_spell_grimoire");
    private static final Map<String, RegistryObject<SoundEvent>> BY_KIND = new HashMap<>();

    static {
        for (String k : new String[]{"slash", "blast", "whoosh", "impact", "charge", "fire", "thunder", "roar"}) {
            String key = "sfx." + k;
            BY_KIND.put(k, SOUNDS.register(key, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", key))));
        }
    }

    /** The Ossuarch's boss music, one loop per phase (synthesised for this mod: tools/boss_music.py). */
    public static final java.util.List<RegistryObject<SoundEvent>> BOSS_MUSIC = java.util.List.of(
            SOUNDS.register("music.ossuarch_1", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.ossuarch_1"))),
            SOUNDS.register("music.ossuarch_2", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.ossuarch_2"))),
            SOUNDS.register("music.ossuarch_3", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.ossuarch_3"))));

    /** Vetto's boss music, one loop per phase (tools/vetto_music.py). */
    public static final java.util.List<RegistryObject<SoundEvent>> VETTO_MUSIC = java.util.List.of(
            SOUNDS.register("music.vetto_1", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.vetto_1"))),
            SOUNDS.register("music.vetto_2", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.vetto_2"))),
            SOUNDS.register("music.vetto_3", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.vetto_3"))));

    /** The registered sound for a kind ("slash", "blast"...), or null. */
    public static SoundEvent get(String kind) {
        RegistryObject<SoundEvent> r = BY_KIND.get(kind);
        return r != null && r.isPresent() ? r.get() : null;
    }
}
