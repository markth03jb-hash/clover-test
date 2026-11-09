package com.ironspellgrimoire;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/iron_spell_grimoire-common.toml */
public final class AddonConfig {
    private AddonConfig() {}

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue PROGRESSION;
    public static final ForgeConfigSpec.DoubleValue XP_MULTIPLIER;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("grimoire_mastery");
        PROGRESSION = b.comment("Grimoire Mastery: spells unlock as the book levels up (1-5). Off = every spell unlocked, no level bonuses.",
                        "Can also be switched in game with /grimoire progression on|off")
                .define("enabled", true);
        XP_MULTIPLIER = b.comment("Multiplies all mastery XP gained (1.0 = about one hour of fighting to master a book).")
                .defineInRange("xp_multiplier", 1.0, 0.0, 100.0);
        b.pop();
        SPEC = b.build();
    }

    public static boolean progression() {
        try { return PROGRESSION.get(); } catch (IllegalStateException e) { return true; }   // config not loaded yet
    }

    public static double xpMultiplier() {
        try { return XP_MULTIPLIER.get(); } catch (IllegalStateException e) { return 1.0; }
    }
}
