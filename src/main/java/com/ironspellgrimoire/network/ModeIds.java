package com.ironspellgrimoire.network;

/** The modes that show a timer on the HUD (index = what goes over the wire). Icon and name come from the spell id. */
public final class ModeIds {
    private ModeIds() {}

    public static final String[] MODES = {
            "devil_union", "mana_zone_dimension_slash", "black_blade", "black_cocoon", "ki_foresight",
            "knights_armor", "spirit_dive", "hushed_dance", "silver_guardian", "silver_mirror",
            "flame_knuckles", "soul_chain", "thunder_god_mode", "thunder_gloves", "mana_skin",
            "lion_mana_zone", "hellfire_incarnate", "marrow_regeneration", "beelzebub_union",
            "surpassing_limits", "thread_cocoon", "battle_dress", "red_thread_of_fate", "evasion_gate", "waypoint",
            "spatial_gate", "thread_cage", "thread_net", "mana_zone", "valkyrie_dress", "reflect_guard",
            "salamander_spirit", "mane_blaze", "world_tree_mantle", "ripper_frenzy", "bedrock_armor", "beast_instinct", "nean_armor", "chronostasis_field", "instinct_guard", "vampire_mode", "hardblood_barricade", "prescript_received", "golden_shell", "demon_light", "scarlet_moon", "mass_domain", "sound_barrier", "crescendo", "masterpiece", "fatal_error", "kings_vault", "vault_aegis", "restriction", "blood_moon_hunt", "emptiness", "absence", "all_in", "insurance", "the_world", "bioluminescence", "pilot", "bulwark_plating", "eye_of_the_storm", "fog_bank"
    };

    public static int index(String id) {
        for (int i = 0; i < MODES.length; i++) if (MODES[i].equals(id)) return i;
        return -1;
    }
}
