package com.ironspellgrimoire;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * config/iron_spell_grimoire-client.toml - how strong the addon's effects look on YOUR screen. Each player sets their own;
 * nothing here changes what a spell does. The defaults are the full effects.
 */
public final class AddonClientConfig {
    private AddonClientConfig() {}

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue PARTICLE_DENSITY;
    public static final ForgeConfigSpec.DoubleValue EFFECT_OPACITY;
    public static final ForgeConfigSpec.DoubleValue SCREEN_SHAKE;
    public static final ForgeConfigSpec.BooleanValue IMPACT_FRAMES;
    public static final ForgeConfigSpec.EnumValue<Quality> EFFECT_QUALITY;

    /** Overall effect quality: how many sprites and particles there may be, and how heavy the extras are. */
    public enum Quality { LOW, MEDIUM, HIGH }

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("effects");
        EFFECT_QUALITY = b.comment("Effect quality: LOW (fastest - fewest sprites, no glow pass, 1-tick impact frames, lighter shake),",
                        "MEDIUM (balanced) or HIGH (everything). Also changeable in game from the grimoire book (sneak + use your grimoire).")
                .defineEnum("effect_quality", Quality.HIGH);
        PARTICLE_DENSITY = b.comment("How many smoke / flame / spark / dust particles spells make. 1.0 = all of them, 0.5 = half, 0 = none.")
                .defineInRange("particle_density", 1.0, 0.0, 1.0);
        EFFECT_OPACITY = b.comment("How solid the 3D spell effects (slashes, walls, beams, auras, domes) look. 1.0 = full, lower = more see-through.")
                .defineInRange("effect_opacity", 1.0, 0.1, 1.0);
        SCREEN_SHAKE = b.comment("Screen shake strength. 1.0 = normal, 0 = no shake at all, up to 2.0 for more.")
                .defineInRange("screen_shake", 1.0, 0.0, 2.0);
        IMPACT_FRAMES = b.comment("Anime impact frames (the flashing white/black frames on big hits). false = never shown.")
                .define("impact_frames", true);
        b.pop();
        SPEC = b.build();
    }

    // cached values, refreshed when the config (re)loads, so the hot render paths never touch the config system
    private static float density = 1F, opacity = 1F, shake = 1F;
    private static boolean frames = true;
    private static Quality quality = Quality.HIGH;

    public static void refresh() {
        try {
            density = PARTICLE_DENSITY.get().floatValue();
            opacity = EFFECT_OPACITY.get().floatValue();
            shake = SCREEN_SHAKE.get().floatValue();
            frames = IMPACT_FRAMES.get();
            quality = EFFECT_QUALITY.get();
        } catch (IllegalStateException ignored) { }   // not loaded yet: keep the defaults
    }

    public static float particleDensity() { return density * (quality == Quality.LOW ? 0.35F : quality == Quality.MEDIUM ? 0.65F : 1F); }
    public static Quality quality() { return quality; }
    /** Most pixel sprites alive at once. */
    public static int spriteCap() { return quality == Quality.LOW ? 800 : quality == Quality.MEDIUM ? 1600 : 2400; }
    /** Most smoke / flame puffs alive at once. */
    public static int smokeCap() { return quality == Quality.LOW ? 300 : quality == Quality.MEDIUM ? 600 : 900; }
    /** The soft additive glow pass under sprites and flames. */
    public static boolean glowPass() { return quality != Quality.LOW; }
    /** Longest impact frame, in ticks. */
    public static int maxFrameTicks() { return quality == Quality.LOW ? 1 : quality == Quality.MEDIUM ? 3 : 4; }

    /** Change the quality from in game (saved to the config file). */
    public static void setQuality(Quality q) {
        quality = q;
        try { EFFECT_QUALITY.set(q); SPEC.save(); } catch (Exception ignored) { }
    }
    public static float effectOpacity() { return opacity; }
    public static float screenShake() { return shake * (quality == Quality.LOW ? 0.6F : 1F); }
    public static boolean impactFrames() { return frames; }
}
