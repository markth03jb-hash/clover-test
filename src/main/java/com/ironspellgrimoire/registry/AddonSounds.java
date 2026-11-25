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
        for (String k : new String[]{"slash", "blast", "whoosh", "impact", "charge", "fire", "thunder", "roar", "familiar", "board_note", "sovereign_step", "ice_crack", "ice_shatter", "heartbeat", "freeze", "blood_splash", "gravity_hum", "gravity_crush", "sonic_boom", "sound_ping", "sound_chord", "brush", "ink_splash", "glitch", "error_beep", "portal_open", "blade_fly", "chain_rattle", "saw", "trick", "gunshot", "quickstep", "erase", "coin", "roulette", "slot", "dice", "jackpot", "card", "current", "deep_boom", "jaws", "leviathan", "lure_hum", "splash_deep", "tentacle", "clank", "missile", "railgun", "rivet", "rocket", "servo", "stomp", "hail", "rain", "sun_chime", "wind", "blade_draw", "rush", "flatline", "breath", "ribbon", "dream_chime", "dream_boing", "dream_hush", "dream_tick", "dream_alarm", "shadow_rise", "shadow_whisper", "shadow_slash", "shadow_gate", "twin_rise", "twin_fall", "sun_flare", "moon_shimmer", "eclipse_toll", "fort_horn", "fort_build", "fort_wall", "fort_gate", "fort_collapse", "fort_crumble", "fort_arrow", "fort_ley", "fort_fire", "fort_bolt", "fort_blades", "fort_page", "fort_bell", "fort_flask", "fort_hammer", "fort_flood", "fort_coins", "fort_launch", "fort_hold", "fort_charge", "fort_fallback", "fort_hum", "fort_alarm", "fort_prestige", "fort_amb_wind", "fort_amb_works", "fort_amb_forge", "fort_amb_bell", "elite_crack", "elite_windup", "elite_slam", "elite_orb", "elite_dagger", "elite_leech", "elite_disrupt", "elite_reflect", "elite_chain", "elite_dash", "elite_split", "elite_tick", "elite_boom", "elite_bite", "elite_chest", "elite_wail", "elite_rise", "elite_portal", "rival_glass_step", "rival_blade_fan", "rival_parry", "rival_noon", "rival_horn", "rival_shieldwall", "rival_cleave", "rival_rampart", "rival_bell", "rival_swap", "rival_bond", "rival_grief", "rival_coins", "rival_deal", "rival_stamp", "rival_foreclose", "rival_yield", "rival_arena", "meridian_star", "meridian_flare", "meridian_wave", "meridian_reaver", "meridian_bite", "meridian_zenith", "meridian_borrow", "meridian_lamp", "meridian_warm", "meridian_coal", "meridian_dawn", "meridian_pride", "omen_step", "omen_fall", "omen_trip", "omen_rebound", "omen_shard", "omen_needles", "omen_token", "omen_afterimage", "omen_domino", "omen_still", "omen_ledger", "omen_law", "tree_learn", "vec_reflect", "vec_slam", "vec_redirect", "vec_step", "vec_return", "vec_gust", "vec_pulse", "vec_wind", "vec_charge", "vec_plasma", "vec_absolute", "vec_lance", "fuse_arm", "fuse_blast", "fuse_click", "fuse_seeker", "fuse_bubble", "fuse_coin", "fuse_loop", "fore_warn", "fore_dodge", "fore_step", "fore_read", "fore_punish", "fore_double", "fore_rewind", "fore_strike", "fore_rewrite", "bd_whisper", "bd_takeover", "bd_draw", "bd_salt", "bd_seraph", "bd_trumpet", "bd_parry", "bd_cut", "bd_bond", "bd_mark"}) {
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

    /** The rivals' music, one loop each (tools/rival_music.py). */
    public static final java.util.List<RegistryObject<SoundEvent>> RIVAL_SEREN_MUSIC = java.util.List.of(
            SOUNDS.register("music.rival_seren_1", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.rival_seren_1"))));
    public static final java.util.List<RegistryObject<SoundEvent>> RIVAL_BRANNOCH_MUSIC = java.util.List.of(
            SOUNDS.register("music.rival_brannoch_1", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.rival_brannoch_1"))));
    public static final java.util.List<RegistryObject<SoundEvent>> RIVAL_TWINS_MUSIC = java.util.List.of(
            SOUNDS.register("music.rival_twins_1", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.rival_twins_1"))));
    public static final java.util.List<RegistryObject<SoundEvent>> RIVAL_MIRA_MUSIC = java.util.List.of(
            SOUNDS.register("music.rival_mira_1", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "music.rival_mira_1"))));

    /** The registered sound for a kind ("slash", "blast"...), or null. */
    public static SoundEvent get(String kind) {
        RegistryObject<SoundEvent> r = BY_KIND.get(kind);
        return r != null && r.isPresent() ? r.get() : null;
    }
}
