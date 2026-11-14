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
        for (String k : new String[]{"slash", "blast", "whoosh", "impact", "charge", "fire", "thunder", "roar", "familiar", "board_note", "sovereign_step", "ice_crack", "ice_shatter", "heartbeat", "freeze", "blood_splash", "gravity_hum", "gravity_crush", "sonic_boom", "sound_ping", "sound_chord", "brush", "ink_splash", "glitch", "error_beep", "portal_open", "blade_fly", "chain_rattle", "saw", "trick", "gunshot", "quickstep", "erase", "coin", "roulette", "slot", "dice", "jackpot", "card", "current", "deep_boom", "jaws", "leviathan", "lure_hum", "splash_deep", "tentacle", "clank", "missile", "railgun", "rivet", "rocket", "servo", "stomp", "hail", "rain", "sun_chime", "wind", "blade_draw", "rush", "flatline", "breath", "ribbon", "dream_chime", "dream_boing", "dream_hush", "dream_tick", "dream_alarm"}) {
            String key = "sfx." + k;
            BY_KIND.put(k, SOUNDS.register(key, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", key))));
        }
    }

    /** Zenon's boss music, one loop per phase (synthesised for this mod: tools/boss_music.py). */
    public static final java.util.List<RegistryObject<SoundEvent>> BOSS_MUSIC = java.util.List.of(
            SOUNDS.register("music.zenon_1", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.zenon_1"))),
            SOUNDS.register("music.zenon_2", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.zenon_2"))),
            SOUNDS.register("music.zenon_3", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.zenon_3"))));

    /** BeastOfDespair's boss music, one loop per phase (tools/vetto_music.py). */
    public static final java.util.List<RegistryObject<SoundEvent>> BEAST_MUSIC = java.util.List.of(
            SOUNDS.register("music.beast_of_despair_1", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.beast_of_despair_1"))),
            SOUNDS.register("music.beast_of_despair_2", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.beast_of_despair_2"))),
            SOUNDS.register("music.beast_of_despair_3", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.beast_of_despair_3"))));

    /** Rhya's boss music, one loop per phase (tools/rhya_music.py). */
    public static final java.util.List<RegistryObject<SoundEvent>> RHYA_MUSIC = java.util.List.of(
            SOUNDS.register("music.rhya_1", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.rhya_1"))),
            SOUNDS.register("music.rhya_2", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.rhya_2"))),
            SOUNDS.register("music.rhya_3", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.rhya_3"))));

    /** Licht's, Revenant' and CurseWitch's music (tools/boss3_music.py). */
    public static final java.util.List<RegistryObject<SoundEvent>> LICHT_MUSIC = java.util.List.of(
            SOUNDS.register("music.licht_1", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.licht_1"))),
            SOUNDS.register("music.licht_2", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.licht_2"))),
            SOUNDS.register("music.licht_3", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.licht_3"))));
    public static final java.util.List<RegistryObject<SoundEvent>> REVENANT_MUSIC = java.util.List.of(
            SOUNDS.register("music.revenant_1", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.revenant_1"))));
    public static final java.util.List<RegistryObject<SoundEvent>> CURSE_WITCH_MUSIC = java.util.List.of(
            SOUNDS.register("music.curse_witch_1", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.curse_witch_1"))));

    /** The registered sound for a kind ("slash", "blast"...), or null. */
    public static SoundEvent get(String kind) {
        RegistryObject<SoundEvent> r = BY_KIND.get(kind);
        return r != null && r.isPresent() ? r.get() : null;
    }
}
