package com.ironspellgrimoire;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/iron_spell_grimoire-common.toml */
public final class AddonConfig {
    private AddonConfig() {}

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue PROGRESSION;
    public static final ForgeConfigSpec.DoubleValue XP_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SPELL_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue PVP;
    public static final ForgeConfigSpec.BooleanValue WORLD_BOSSES;
    public static final ForgeConfigSpec.BooleanValue RIFTS;
    public static final ForgeConfigSpec.DoubleValue RIFT_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> CEREMONY_EXCLUDED;
    public static final ForgeConfigSpec.BooleanValue TWIN_ECHO;
    public static final ForgeConfigSpec.BooleanValue STARTER_CODEX;
    public static final ForgeConfigSpec.BooleanValue ELITES, THREAT;
    public static final ForgeConfigSpec.DoubleValue ELITE_CHANCE;
    public static final ForgeConfigSpec.IntValue ELITE_MAX_TRAITS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> ELITE_OFF;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("grimoire_mastery");
        PROGRESSION = b.comment("Grimoire Mastery: spells unlock as the book levels up (1-5). Off = every spell unlocked, no level bonuses.",
                        "Can also be switched in game with /grimoire progression on|off")
                .define("enabled", true);
        XP_MULTIPLIER = b.comment("Multiplies all mastery XP gained (1.0 = about one hour of fighting to master a book).")
                .defineInRange("xp_multiplier", 1.0, 0.0, 100.0);
        b.pop();
        b.push("combat");
        SPELL_DAMAGE = b.comment("Multiplies the damage of every grimoire spell (1.0 = as designed).")
                .defineInRange("spell_damage_multiplier", 1.0, 0.0, 10.0);
        PVP = b.comment("Grimoire spells, arms, blades and summons can hurt other players (never teammates, and never when the server's own pvp is off).",
                        "Off = no grimoire can hurt another player. One rule for every grimoire.")
                .define("grimoire_pvp", true);
        b.pop();
        b.push("world");
        WORLD_BOSSES = b.comment("World bosses (the Cinder Sovereign) rise every couple of hours near a chosen mage.").define("world_bosses", true);
        RIFTS = b.comment("Rifts open in the overworld near players.").define("rifts", true);
        RIFT_CHANCE = b.comment("The chance a rift opens near a player every 5 minutes (0.25 = one in four).")
                .defineInRange("rift_chance", 0.25, 0.0, 1.0);
        b.pop();
        b.push("elites");
        ELITES = b.comment("Elite mobs: some hostile mobs spawn with 1-3 traits (Bulwark, Mana Leech, Splitter...) and give more XP and drops.").define("elites", true);
        ELITE_CHANCE = b.comment("The chance a hostile mob is an elite at threat rank 0 (it grows with rank, and doubles in rifts). 0.05 = 5%.")
                .defineInRange("elite_chance", 0.05, 0.0, 1.0);
        ELITE_MAX_TRAITS = b.comment("The most traits one elite can have.").defineInRange("elite_max_traits", 3, 1, 3);
        ELITE_OFF = b.comment("Traits that never roll, by name - for example [\"Tether\", \"Disruptor\"].")
                .defineListAllowEmpty(java.util.List.of("elite_traits_off"), java.util.List::of, o -> o instanceof String);
        THREAT = b.comment("Threat levels: mobs near a mage with a well-mastered grimoire get up to +100% health and +50% damage (rank 0-5).",
                        "Off = every mob is rank 0.").define("threat_levels", true);
        b.pop();
        b.push("grimoires");
        CEREMONY_EXCLUDED = b.comment("Grimoires the altar ceremony never gives, by id - for example [\"twins_grimoire\", \"shadow_grimoire\"].",
                        "They can still be had in creative or with /give.")
                .defineListAllowEmpty(java.util.List.of("ceremony_excluded"), java.util.List::of, o -> o instanceof String);
        TWIN_ECHO = b.comment("The Masked Twins' Echo: the resting twin fights beside you from level 15.").define("twin_echo", true);
        STARTER_CODEX = b.comment("Give each player a Grimoire Codex (the mod's guide) the first time they join.").define("starter_codex", true);
        b.pop();
        SPEC = b.build();
    }

    public static boolean progression() {
        try { return PROGRESSION.get(); } catch (IllegalStateException e) { return true; }   // config not loaded yet
    }

    private static <T> T get(java.util.function.Supplier<T> v, T fallback) {
        try { return v.get(); } catch (IllegalStateException e) { return fallback; }   // config not loaded yet
    }

    public static float spellDamage() { return (float) (double) get(SPELL_DAMAGE::get, 1.0); }
    public static boolean pvp() { return get(PVP::get, true); }
    public static boolean worldBosses() { return get(WORLD_BOSSES::get, true); }
    public static boolean rifts() { return get(RIFTS::get, true); }
    public static double riftChance() { return get(RIFT_CHANCE::get, 0.25); }
    public static boolean twinEcho() { return get(TWIN_ECHO::get, true); }
    public static boolean starterCodex() { return get(STARTER_CODEX::get, true); }
    public static boolean elites() { return get(ELITES::get, true); }
    public static boolean threat() { return get(THREAT::get, true); }
    public static double eliteChance() { return get(ELITE_CHANCE::get, 0.05); }
    public static int eliteMaxTraits() { return get(ELITE_MAX_TRAITS::get, 3); }
    /** The traits turned off in the config, as a bit mask. */
    public static int eliteOff() {
        int m = 0;
        for (String x : get(ELITE_OFF::get, java.util.List.<String>of()))
            for (int i = 0; i < com.ironspellgrimoire.server.EliteMobs.COUNT; i++)
                if (com.ironspellgrimoire.server.EliteMobs.NAME[i].equalsIgnoreCase(x.trim())) m |= 1 << i;
        return m;
    }

    /** Whether the ceremony may give this grimoire (by its item id, with or without the mod's prefix). */
    public static boolean ceremonyAllows(net.minecraft.world.item.Item item) {
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
        if (id == null) return true;
        for (String x : get(CEREMONY_EXCLUDED::get, java.util.List.<String>of()))
            if (x.equals(id.toString()) || x.equals(id.getPath())) return false;
        return true;
    }

    /**
     * The one PvP rule every grimoire follows: may this player's magic hurt that one? Never themselves, never a teammate,
     * never with grimoire PvP off in the config or the server's own pvp off, never someone in creative or spectating.
     */
    public static boolean mayHurt(net.minecraft.world.entity.player.Player caster, net.minecraft.world.entity.player.Player target) {
        if (caster == target || target.isCreative() || target.isSpectator()) return false;
        if (!pvp()) return false;
        if (caster.isAlliedTo(target)) return false;
        return caster.canHarmPlayer(target);
    }

    public static double xpMultiplier() {
        try { return XP_MULTIPLIER.get(); } catch (IllegalStateException e) { return 1.0; }
    }
}
