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
            "spatial_gate", "thread_cage", "thread_net", "mana_zone"
    };

    public static int index(String id) {
        for (int i = 0; i < MODES.length; i++) if (MODES[i].equals(id)) return i;
        return -1;
    }
}
