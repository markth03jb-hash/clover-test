package com.ironspellgrimoire.server;

import com.ironspellgrimoire.AddonConfig;
import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.network.GrimoireOpenPacket;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Grimoire Mastery: every grimoire levels up from 1 to 5 as you fight with its magic, and its stronger spells unlock
 * as it does. Progress is stored on the book itself (item NBT), so a book keeps its level wherever it goes.
 *
 *  XP:      1 per point of damage your book's spells deal (capped at the target's remaining health), +15 per kill,
 *           +300 for a boss, +3 per second while one of the book's modes is active. Passive animals, villagers and
 *           armor stands give nothing. No diminishing returns. Tuned so a book is mastered in about an hour of fighting.
 *  Levels:  each level above 1 also gives +4% spell power, +3% cooldown reduction and +50 max mana while equipped.
 *  Toggle:  config "grimoire_mastery.enabled" or /grimoire progression on|off (off = everything unlocked, no bonuses).
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class GrimoireMastery {
    private GrimoireMastery() {}

    /** The level a grimoire's ultimate unlocks at, and as far as mastery goes before the Magic Knight Exam. */
    public static final int MAX_LEVEL = 5;
    /** After passing the Magic Knight Exam with it (server/KnightExam), a grimoire's mastery goes on to 7. */
    public static final int ASCENDED_MAX = 7;
    /** After its Awakening (server/Awakening), 8. */
    public static final int AWAKENED_MAX = 8;
    private static java.util.Set<String> ULTIMATES;

    /** Whether this damage comes from a grimoire's ultimate (the spell its book unlocks at the top level). Bosses' damage caps don't apply to it. */
    public static boolean isUltimate(net.minecraft.world.damagesource.DamageSource src) {
        if (!(src instanceof SpellDamageSource s) || s.spell() == null) return false;
        String id = s.spell().getSpellId();
        int i = id.indexOf(':');
        return ultimates().contains(i < 0 ? id : id.substring(i + 1)) || Awakening.isAwakenedSpell(id);
    }

    private static java.util.Set<String> ultimates() {
        if (ULTIMATES == null) {
            java.util.Set<String> u = new java.util.HashSet<>();
            for (Book b : BOOKS) for (Map.Entry<String, Integer> e : b.unlocks().entrySet()) if (e.getValue() >= MAX_LEVEL) u.add(e.getKey());
            ULTIMATES = u;
        }
        return ULTIMATES;
    }
    /** Total XP needed to reach level 2, 3, 4, 5. */
    public static final int[] THRESHOLDS = {0, 0, 800, 2400, 5200, 9000, 14000, 20000, 28000};
    private static final String TAG = "arcane_mastery";
    /** The grimoire's mastery tag name (the game tests set a grimoire's mastery directly). */
    static String masteryTag() { return TAG; }
    /** Hooded mages teach more than ordinary mobs: their damage counts triple and a kill adds a bonus. */
    private static final float MAGE_XP_MULT = 3f, MAGE_KILL_XP = 60f;
    private static final float KILL_XP = 15, BOSS_XP = 300, MODE_XP_PER_SECOND = 3;

    private static final UUID POWER_ID = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a100101");
    private static final UUID COOLDOWN_ID = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a100102");
    private static final UUID MANA_ID = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a100103");

    /** One grimoire: its item, colour palette for the level-up flourish, and which level unlocks each spell. */
    public record Book(Supplier<Item> item, int palette, Map<String, Integer> unlocks) {}

    public static final List<Book> BOOKS = new ArrayList<>();

    private static void book(Supplier<Item> item, int pal, Object... idLevel) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < idLevel.length; i += 2) m.put((String) idLevel[i], (Integer) idLevel[i + 1]);
        BOOKS.add(new Book(item, pal, m));
    }

    static {
        book(AddonItemRegistry.BLACK_GRIMOIRE::get, Fx.CRIMSON,
                "summon_greatsword", 1, "piercing_dash", 1, "zetten", 1,
                "ki_sense", 2, "instinct_guard", 2, "dweller_slash", 2,
                "sky_splitter", 3, "anti_magic", 3,
                "borrowed_slash", 4,
                "devil_union", 5);
        book(AddonItemRegistry.DARK_GRIMOIRE::get, Fx.YAMI,
                "black_blade", 1, "dimension_slash", 1, "ki_foresight", 1,
                "avidya_slash", 2, "death_thrust", 2, "black_cocoon", 2,
                "black_moon", 3, "black_hole", 3,
                "mana_zone_dimension_slash", 4,
                "event_horizon", 5);
        book(AddonItemRegistry.WIND_GRIMOIRE::get, Fx.WIND,
                "wind_blade_shower", 1, "sylphs_breath", 1, "gale_white_bow", 1,
                "tornado_fang", 2, "hushed_dance", 2,
                "spirit_of_zephyr", 3, "spirit_of_boreas", 3,
                "spirit_of_euros", 4, "spirit_dive", 4, "mana_zone", 4,
                "spirit_storm", 5);
        book(AddonItemRegistry.MERCURY_GRIMOIRE::get, 11,
                "mercury_bullets", 1, "mercury_spears", 1, "silver_mirror", 1,
                "silver_rain", 2, "storm_of_blades", 2,
                "mercury_eagle", 3, "mercury_sphere", 3,
                "regal_spear", 4, "silver_guardian", 4,
                "mercury_cage", 5);
        book(AddonItemRegistry.FIRE_GRIMOIRE::get, Fx.FIRE,
                "exploding_fireball", 1, "explosive_scattershot", 1, "flaming_bat", 1,
                "flame_knuckles", 2, "ring_of_fire", 2,
                "fireball_barrage", 3, "magna_special", 3,
                "fireball_rain", 4, "soul_chain", 4,
                "fire_cannon", 5);
        book(AddonItemRegistry.LIGHTNING_GRIMOIRE::get, 13,
                "thunder_fist", 1, "lightning_step", 1, "thunder_kick", 1,
                "thunder_orb", 2, "chain_lightning", 2, "mana_sense", 2,
                "thunder_gloves", 3, "thunderfall", 3,
                "thunder_god_mode", 4,
                "thunder_rampage", 5);
        book(AddonItemRegistry.HELLFIRE_GRIMOIRE::get, 14,
                "calidos_brachium", 1, "lioness_charge", 1, "burning_guard", 1,
                "flame_lion_paws", 2, "mana_skin", 2,
                "calidos_brachium_barrage", 3, "ground_punch", 3,
                "lion_mana_zone", 4, "hellfire_incarnate", 4,
                "calidos_brachium_purgatory", 5);
        book(AddonItemRegistry.BONE_GRIMOIRE::get, 15,
                "bone_spear_volley", 1, "bone_sword", 1, "bone_spike_eruption", 1,
                "bone_whips", 2, "marrow_regeneration", 2,
                "ribcage_prison", 3, "bone_thicket", 3,
                "spatial_cube", 4, "bone_spear_storm", 4,
                "beelzebub_union", 5);
        book(AddonItemRegistry.STEEL_GRIMOIRE::get, Fx.STEEL,
                "violent_rotating_lance", 1, "thunder_arrow", 1, "steel_castle_wall", 1,
                "blazing_spiral_lance", 2, "steel_prison", 2,
                "white_steel_horse_cart", 3,
                "knights_armor", 4,
                "acier_silva", 5);
        book(AddonItemRegistry.THREAD_GRIMOIRE::get, 16,
                "binding_threads", 1, "puppet_strings", 1, "thread_net", 1,
                "thread_cocoon", 2, "stitching", 2,
                "battle_dress", 3, "thread_cage", 3,
                "red_thread_of_fate", 5);
        book(AddonItemRegistry.SPACE_GRIMOIRE::get, 17,
                "spatial_gate", 1, "short_hop", 1, "waypoint", 1,
                "sky_drop", 2, "escort_gate", 2,
                "redirect_gate", 3, "evasion_gate", 3,
                "space_deletion", 5);
        book(AddonItemRegistry.WATER_GRIMOIRE::get, Fx.WATER,
                "water_ball", 1, "torrent_lance", 1,
                "sea_dragons_nest", 2, "aqua_barrage", 2,
                "tidal_wave", 3, "sea_serpent", 3,
                "valkyrie_dress", 4,
                "sea_dragons_wrath", 5);
        book(AddonItemRegistry.MIRROR_GRIMOIRE::get, Fx.MIRROR,
                "mirror_shard", 1, "mirror_step", 1,
                "reflect_guard", 2, "prism_beam", 2,
                "mirror_cage", 3, "mirror_barrage", 3,
                "mirror_double", 4,
                "hall_of_mirrors", 5);
        book(AddonItemRegistry.RIFT_GRIMOIRE::get, Fx.VOID,
                "spatial_cut", 1, "rift_blades", 1,
                "blink_strike", 2, "severing_line", 2,
                "gate_barrage", 3, "space_collapse", 3,
                "convergence", 4,
                "dimensional_severance", 5);
        book(AddonItemRegistry.FLAME_SPIRIT_GRIMOIRE::get, Fx.FUEGO,
                "salamander_flare", 1, "spirit_lance", 1,
                "flame_aegis", 2, "salamander_coil", 2,
                "flare_volley", 3, "salamander_rush", 3,
                "salamander_spirit", 4, "great_salamander", 5);
        book(AddonItemRegistry.LION_FLAME_GRIMOIRE::get, Fx.LION,
                "ember_fist", 1, "lion_claw", 1,
                "flame_leap", 2, "roaring_burst", 2,
                "pride_barrage", 3, "prowl_dash", 3,
                "mane_blaze", 4, "king_lion_roar", 5);
        book(AddonItemRegistry.SLASH_GRIMOIRE::get, Fx.RIPPER,
                "phantom_slash", 1, "crescent_cut", 1,
                "shadow_step_slash", 2, "thousand_cuts", 2,
                "cross_reaper", 3, "bleeding_edge", 3,
                "ripper_frenzy", 4, "bloody_massacre", 5);
        book(AddonItemRegistry.EARTH_GRIMOIRE::get, Fx.EARTH,
                "stone_fist", 1, "rock_spike", 1,
                "earth_wall", 2, "boulder_toss", 2,
                "quake_stomp", 3, "stone_golem", 3,
                "bedrock_armor", 4, "mountain_fall", 5);
        book(AddonItemRegistry.TIME_GRIMOIRE::get, Fx.TIME,
                "clock_hand_bolt", 1, "time_stop_point", 1, "time_skip", 1,
                "time_recovery", 2, "chrono_acceleration", 2, "ticking_snare", 2,
                "time_theft", 3, "hourglass_barrage", 3, "rewind_ward", 3,
                "echo_of_the_past", 4, "chronostasis_field", 4, "zenith_hour", 5);
        book(AddonItemRegistry.LIGHT_GRIMOIRE::get, Fx.GOLD,
                "light_sword", 1, "light_speed_step", 1, "arrows_of_judgment", 2,
                "bright_judgment_whip", 2, "healing_ray", 2, "blinding_flash", 3,
                "light_swords_of_judgment", 3, "light_shaft", 4, "demon_light", 4, "imperial_swords_of_conviction", 5);
        book(AddonItemRegistry.BLOOD_GRIMOIRE::get, Fx.CRIMSON,
                "crimson_edge", 1, "hemorrhage", 1, "sanguine_volley", 2,
                "transfusion", 2, "blood_pact", 2, "red_tide", 3,
                "thorned_veins", 3, "crimson_bulwark", 4, "exsanguinate", 4, "scarlet_moon", 5);
        book(AddonItemRegistry.GRAVITY_GRIMOIRE::get, Fx.DARK,
                "gravity_bullet", 1, "weightless_step", 1, "crushing_field", 2,
                "repulse", 2, "gravity_well", 2, "gravity_lift", 3,
                "orbiting_mass", 3, "heavy_descent", 4, "mass_domain", 4, "starfall_collapse", 5);
        book(AddonItemRegistry.SOUND_GRIMOIRE::get, Fx.WIND,
                "sonic_pulse", 1, "echo_step", 1, "tuning_strike", 2,
                "sound_barrier", 2, "silence_field", 2, "thunderclap", 3,
                "sonic_lance", 3, "echo_chamber", 4, "crescendo", 4, "final_chord", 5);
        book(AddonItemRegistry.INK_GRIMOIRE::get, 10,
                "brush_stroke", 1, "ink_splash", 1, "ink_serpent", 2,
                "ink_blot", 2, "seal_script", 2, "ink_beasts", 3,
                "ink_rain", 3, "ink_wave", 4, "masterpiece", 4, "grand_calligraphy", 5);
        book(AddonItemRegistry.ERROR_GRIMOIRE::get, Fx.VOID,
                "glitch_bolt", 1, "frame_skip", 1, "corrupted_threads", 2,
                "lag_spike", 2, "copy_paste", 2, "crash_zone", 3,
                "error_windows", 3, "blue_screen", 4, "fatal_error", 4, "system_crash", 5);
        book(AddonItemRegistry.TREASURE_GRIMOIRE::get, Fx.GOLD,
                "vault_volley", 1, "gilded_spear", 1, "vault_chains", 2,
                "blade_wall", 2, "blade_rain", 2, "seeking_blades", 3,
                "treasure_barrage", 3, "vault_aegis", 4, "kings_vault", 4, "vault_breaker", 5);
        book(AddonItemRegistry.CHAIN_GRIMOIRE::get, Fx.STEEL,
                "chain_lash", 1, "binding_chain", 1, "dowsing_chain", 2,
                "healing_chain", 2, "judgment_chain", 2, "stealing_chain", 3,
                "chain_net", 3, "anchor_chain", 4, "restriction", 4, "thousand_chains", 5);
        book(AddonItemRegistry.HUNTER_GRIMOIRE::get, Fx.CRIMSON,
                "saw_cleave", 1, "trick_switch", 1, "silver_shot", 2,
                "quickstep", 2, "visceral_strike", 2, "hunters_mark", 3,
                "rending_spin", 3, "blood_volley", 4, "blood_moon_hunt", 4, "nightmare_cleave", 5);
        book(AddonItemRegistry.VOID_GRIMOIRE::get, Fx.VOID,
                "erase", 1, "null_step", 1, "unmake", 2,
                "nullify", 2, "seam", 2, "absence", 3,
                "void_lance", 3, "delete", 4, "emptiness", 4, "origin_zero", 5);
        book(AddonItemRegistry.GAMBLER_GRIMOIRE::get, Fx.GOLD,
                "coin_toss", 1, "double_or_nothing", 1, "roulette", 2,
                "chip_shot", 2, "lucky_seven", 2, "slot_machine", 3,
                "loaded_dice", 3, "insurance", 4, "all_in", 4, "jackpot", 5);
        book(AddonItemRegistry.TAROT_GRIMOIRE::get, Fx.MIRROR,
                "draw_card", 1, "the_magician", 1, "the_tower", 2,
                "the_star", 2, "death_card", 2, "wheel_of_fortune", 3,
                "the_hanged_man", 3, "three_card_spread", 4, "the_world", 4, "judgement", 5);
        book(AddonItemRegistry.ABYSS_GRIMOIRE::get, Fx.WATER,
                "crushing_pressure", 1, "anglers_lure", 1, "abyssal_grasp", 2,
                "undertow", 2, "abyssal_dive", 2, "depth_charge", 3,
                "leviathan", 3, "hadal_weight", 4, "bioluminescence", 4, "the_trench", 5);
        book(AddonItemRegistry.MECH_GRIMOIRE::get, Fx.STEEL,
                "rivet_barrage", 1, "thruster_dash", 1, "rocket_fist", 2,
                "piston_stomp", 2, "bulwark_plating", 2, "missile_swarm", 3,
                "overclock", 3, "rail_cannon", 4, "pilot", 4, "titan_buster", 5);
        book(AddonItemRegistry.WEATHER_GRIMOIRE::get, Fx.LIGHTNING,
                "sunbeam", 1, "downpour", 1, "thunder_strike", 2,
                "hailstorm", 2, "gale", 2, "fog_bank", 3,
                "cyclone", 3, "rainbow", 4, "eye_of_the_storm", 4, "cataclysm", 5);
        book(AddonItemRegistry.OVERDRIVE_GRIMOIRE::get, Fx.CRIMSON,
                "draw_heartline", 1, "pulse_cut", 1, "adrenaline_rush", 2,
                "heartbeat", 2, "flatline", 2, "tachycardia", 3,
                "second_wind", 3, "ribbon_bind", 4, "redline", 4, "final_beat", 5);
    }

    // ------------------------------------------------------------------ book data (shared with the client)
    public static Book bookOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (Book b : BOOKS) if (stack.is(b.item().get())) return b;
        return null;
    }

    public static int xp(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t == null ? 0 : t.getCompound(TAG).getInt("xp");
    }

    public static int level(ItemStack stack) { return levelFor(xp(stack), cap(stack)); }

    /** Whether this grimoire has passed the Magic Knight Exam. */
    public static boolean ascended(ItemStack stack) { return stack != null && stack.hasTag() && stack.getTag().getBoolean("Ascended"); }

    /** How far this grimoire's mastery can go: 5, or 7 once it has passed the exam. */
    public static int cap(ItemStack stack) { return Awakening.awakened(stack) ? AWAKENED_MAX : ascended(stack) ? ASCENDED_MAX : MAX_LEVEL; }

    public static int levelFor(int xp) { return levelFor(xp, MAX_LEVEL); }

    public static int levelFor(int xp, int cap) {
        int l = 1;
        while (l < cap && xp >= THRESHOLDS[l + 1]) l++;
        return l;
    }

    /** How much more a grimoire's ultimate hits for at mastery 6 (+25%) and 7 (+50%). */
    public static float ultimateBoost(ItemStack book, String spellId) {
        if (book == null || !ascended(book)) return 1f;
        if (Awakening.isAwakenedSpell(spellId)) return level(book) >= AWAKENED_MAX ? 1.3f : 1f;   // the awakened spell, at mastery 8
        int i = spellId.indexOf(':');
        if (!ultimates().contains(i < 0 ? spellId : spellId.substring(i + 1))) return 1f;
        int l = level(book);
        return l >= 7 ? 1.5f : l >= 6 ? 1.25f : 1f;
    }

    private static void setXp(ItemStack stack, int xp) {
        CompoundTag t = stack.getOrCreateTag();
        CompoundTag m = t.getCompound(TAG);
        m.putInt("xp", Math.max(0, Math.min(xp, THRESHOLDS[cap(stack)])));
        t.put(TAG, m);
    }

    /** Spell id (path) -> the level that unlocks it in this book, or 0 when the book doesn't gate it. */
    public static int required(Book b, String spell) {
        if (b == null) return 0;
        if (Awakening.isAwakenedSpell(spell)) return 6;   // the awakened page sits after everything else
        return b.unlocks().getOrDefault(spell, 0);
    }

    /** True when this spell's XP should go to this book (one of its spells, or a devil spell for Asta's book). */
    private static boolean belongs(Book b, String spell) {
        if (b == null) return false;
        if (b.unlocks().containsKey(spell)) return true;
        return b.item().get() == AddonItemRegistry.BLACK_GRIMOIRE.get() && spell.startsWith("devil_");
    }

    public static List<String> unlockedAt(Book b, int level) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : b.unlocks().entrySet()) if (e.getValue() == level) out.add(e.getKey());
        return out;
    }

    /** The names scribbled out (unreadable, shifting letters): spells you have not unlocked yet stay a secret. */
    public static MutableComponent hiddenNames(List<String> ids) {
        MutableComponent c = Component.empty();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) c.append(", ");
            c.append(Component.translatable("spell.iron_spell_grimoire." + ids.get(i)).withStyle(net.minecraft.ChatFormatting.OBFUSCATED));
        }
        return c;
    }

    /** True if this spell of the grimoire is still locked by mastery (progression on and level too low). */
    public static boolean lockedFor(ItemStack stack, String spell) {
        Book b = bookOf(stack);
        if (b == null) return false;
        int need = required(b, spell);
        return need > 1 && level(stack) < need;
    }

    public static MutableComponent spellNames(List<String> ids) {
        MutableComponent c = Component.empty();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) c.append(", ");
            c.append(Component.translatable("spell.iron_spell_grimoire." + ids.get(i)));
        }
        return c;
    }

    // ------------------------------------------------------------------ gaining XP
    private static ItemStack equipped(Player p) {
        ItemStack s = Utils.getPlayerSpellbookStack(p);
        return s == null ? ItemStack.EMPTY : s;
    }

    /** Adds XP to the equipped book if the spell belongs to it. */
    private static void award(ServerPlayer p, String spell, float amount) {
        if (!AddonConfig.progression() || amount <= 0) return;
        ItemStack book = equipped(p);
        Book b = bookOf(book);
        if (!belongs(b, spell)) return;
        addXp(p, book, b, amount * AddonConfig.xpMultiplier());
    }

    private static final Map<UUID, Float> FRACTION = new java.util.HashMap<>();

    /** Mastery XP for the equipped grimoire from outside a fight (a bounty's reward). */
    public static void bountyXp(ServerPlayer p, int amount) {
        ItemStack book = equipped(p);
        Book b = book.isEmpty() ? null : bookOf(book);
        if (b != null) addXp(p, book, b, amount);
    }

    private static void addXp(ServerPlayer p, ItemStack book, Book b, double amount) {
        float acc = FRACTION.getOrDefault(p.getUUID(), 0f) + (float) amount;
        int whole = (int) acc;
        FRACTION.put(p.getUUID(), acc - whole);
        if (whole <= 0) return;
        int before = xp(book);
        if (before >= THRESHOLDS[cap(book)]) return;
        int oldLevel = levelFor(before, cap(book));
        setXp(book, before + whole);
        int newLevel = level(book);
        for (int l = oldLevel + 1; l <= newLevel; l++) levelUp(p, b, l);
    }

    private static String spellOf(Object source) {
        if (!(source instanceof SpellDamageSource sds)) return null;
        AbstractSpell s = sds.spell();
        if (s == null || !"iron_spell_grimoire".equals(s.getSpellResource().getNamespace())) return null;
        return s.getSpellResource().getPath();
    }

    private static boolean worthXp(LivingEntity t) {
        return !(t instanceof Animal) && !(t instanceof AbstractVillager) && !(t instanceof ArmorStand);
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getEntity() == p) return;
        String spell = spellOf(e.getSource());
        if (spell == null || !worthXp(e.getEntity())) return;
        float dealt = Math.min(e.getAmount(), Math.max(0f, e.getEntity().getHealth()));
        award(p, spell, e.getEntity() instanceof com.ironspellgrimoire.entity.MageEnemy ? dealt * MAGE_XP_MULT : dealt);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getEntity() == p) return;
        String spell = spellOf(e.getSource());
        LivingEntity t = e.getEntity();
        if (spell == null || !worthXp(t)) return;
        boolean boss = t.getType().is(Tags.EntityTypes.BOSSES) || t instanceof Warden;
        award(p, spell, KILL_XP + (boss ? BOSS_XP : 0) + (t instanceof com.ironspellgrimoire.entity.MageEnemy ? MAGE_KILL_XP : 0));
    }

    // ------------------------------------------------------------------ level bonuses, mode XP
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        ItemStack book = equipped(p);
        sortSlots(book);
        if (p.tickCount % 100 == 0) for (ItemStack st : p.getInventory().items) sortSlots(st);
        Book b = bookOf(book);
        boolean on = AddonConfig.progression() && b != null;
        int bonus = on ? level(book) - 1 : 0;
        setMod(p, AttributeRegistry.SPELL_POWER.get(), POWER_ID, "Grimoire Mastery power", 0.04 * bonus, AttributeModifier.Operation.MULTIPLY_BASE);
        setMod(p, AttributeRegistry.COOLDOWN_REDUCTION.get(), COOLDOWN_ID, "Grimoire Mastery haste", 0.03 * bonus, AttributeModifier.Operation.MULTIPLY_BASE);
        setMod(p, AttributeRegistry.MAX_MANA.get(), MANA_ID, "Grimoire Mastery mana", 50.0 * bonus, AttributeModifier.Operation.ADDITION);
        if (!on) return;
        // time spent in one of the book's modes
        for (String mode : ModeHud.active(p)) {
            if (belongs(b, mode)) { addXp(p, book, b, MODE_XP_PER_SECOND * AddonConfig.xpMultiplier()); break; }
        }
    }

    /**
     * Keeps a grimoire's spells in unlock order - its wheel runs from the top clockwise, so a mastery 2 move can
     * never sit after a mastery 3 one. Books made before this (or put together in another order) are re-sorted in
     * place; spells on the same level keep the order they had, and each spell keeps its level and lock.
     */
    public static void sortSlots(ItemStack stack) {
        Book b = bookOf(stack);
        if (b == null || !io.redspace.ironsspellbooks.api.spells.ISpellContainer.isSpellContainer(stack)) return;
        try {
            var c = io.redspace.ironsspellbooks.api.spells.ISpellContainer.get(stack);
            List<io.redspace.ironsspellbooks.api.spells.SpellSlot> slots = new ArrayList<>(c.getActiveSpells());
            List<io.redspace.ironsspellbooks.api.spells.SpellSlot> sorted = new ArrayList<>(slots);
            sorted.sort(java.util.Comparator.comparingInt(sl -> required(b, sl.getSpell().getSpellResource().getPath())));
            boolean same = true;
            for (int i = 0; i < slots.size(); i++) if (slots.get(i) != sorted.get(i)) { same = false; break; }
            if (same) return;
            var fresh = io.redspace.ironsspellbooks.api.spells.ISpellContainer.create(c.getMaxSpellCount(), c.isSpellWheel(), c.mustEquip());
            for (var sl : sorted) fresh.addSpell(sl.getSpell(), sl.getLevel(), sl.isLocked(), stack);
            fresh.save(stack);
        } catch (Throwable ignored) { }
    }

    private static void setMod(Player p, Attribute a, UUID id, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(id);
        if (amount == 0) { if (cur != null) inst.removeModifier(id); return; }
        if (cur != null && cur.getAmount() == amount) return;
        if (cur != null) inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    // ------------------------------------------------------------------ locked spells
    @SubscribeEvent
    public static void onPreCast(SpellPreCastEvent e) {
        if (!AddonConfig.progression() || !(e.getEntity() instanceof ServerPlayer p)) return;
        String id = e.getSpellId();
        if (id == null || !id.startsWith("iron_spell_grimoire:")) return;
        String spell = id.substring("iron_spell_grimoire:".length());
        ItemStack book = equipped(p);
        Book b = bookOf(book);
        int need = required(b, spell);
        if (need <= 1 || level(book) >= need) return;
        if (need == MAX_LEVEL && LimitBreak.active(p)) return;   // surpassing your limits: the ultimate answers anyway
        e.setCanceled(true);
        p.displayClientMessage(Component.translatable("ui.iron_spell_grimoire.mastery.locked",
                Component.translatable("spell.iron_spell_grimoire." + spell), need).withStyle(ChatFormatting.RED), true);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHEST_LOCKED, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    // ------------------------------------------------------------------ level-up flourish
    private static void levelUp(ServerPlayer p, Book b, int level) {
        ServerLevel sl = p.serverLevel();
        Vec3 at = p.position();
        int pal = b.palette();
        // the grimoire flies open, rings of its magic burst out around you and a halo forms overhead
        AddonNetwork.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new GrimoireOpenPacket(p.getId()));
        FxSend.ring(sl, pal, at.add(0, 0.05, 0), 0.3f, 4.5f, 0.12f, 16);
        FxSend.ring(sl, pal, at.add(0, 1.0, 0), 0.2f, 2.6f, 0.08f, 12);
        AstaFx.later(4, () -> FxSend.ring(sl, Fx.SILVER, p.position().add(0, 0.06, 0), 0.5f, 3.2f, 0.05f, 12));
        FxLib.halo(sl, pal, p.position().add(0, p.getBbHeight() + 0.5, 0), 1.0f);
        if (level == MAX_LEVEL) FxSend.shake(sl, at, 0.35f, 10, 12);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 0.8f);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.7f);
        if (level == MAX_LEVEL) sl.playSound(null, at.x, at.y, at.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
        if (level > MAX_LEVEL) FxSend.shake(sl, at, 0.45f, 12, 14);
        List<String> unlocked = unlockedAt(b, level);
        MutableComponent msg = Component.translatable("ui.iron_spell_grimoire.mastery.level_up", level).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        if (level >= AWAKENED_MAX) msg.append(Component.literal(" — your awakened spell hits 30% harder").withStyle(ChatFormatting.YELLOW));
        else if (level > MAX_LEVEL) msg.append(Component.literal(" — your ultimate hits " + (level >= 7 ? "+50%" : "+25%") + " harder").withStyle(ChatFormatting.YELLOW));
        else if (!unlocked.isEmpty()) msg.append(Component.literal(" — ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("ui.iron_spell_grimoire.mastery.unlocked", spellNames(unlocked)).withStyle(ChatFormatting.YELLOW));
        p.displayClientMessage(msg, true);
        p.sendSystemMessage(msg);
    }

    // ------------------------------------------------------------------ the on/off switch, synced to clients
    private static void syncToggle(ServerPlayer p) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 34; pkt.life = AddonConfig.progression() ? 1 : 0;
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), pkt);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) syncToggle(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { FRACTION.remove(e.getEntity().getUUID()); }

    // ------------------------------------------------------------------ commands
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("grimoire").requires(s -> s.hasPermission(2))
                .then(Commands.literal("progression")
                        .then(Commands.literal("on").executes(c -> toggle(c, true)))
                        .then(Commands.literal("off").executes(c -> toggle(c, false)))
                        .executes(c -> {
                            c.getSource().sendSuccess(() -> Component.translatable(AddonConfig.progression() ? "ui.iron_spell_grimoire.mastery.is_on" : "ui.iron_spell_grimoire.mastery.is_off"), false);
                            return 1;
                        }))
                .then(Commands.literal("mastery")
                        .then(Commands.literal("set").then(Commands.argument("level", IntegerArgumentType.integer(1, AWAKENED_MAX))
                                .executes(c -> setLevel(c, c.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> setLevel(c, EntityArgument.getPlayer(c, "player"))))))
                        .then(Commands.literal("add").then(Commands.argument("xp", IntegerArgumentType.integer(1))
                                .executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    ItemStack book = equipped(p);
                                    Book b = bookOf(book);
                                    if (b == null) return noBook(c);
                                    addXp(p, book, b, IntegerArgumentType.getInteger(c, "xp"));
                                    return 1;
                                })))));
    }

    private static int toggle(CommandContext<CommandSourceStack> c, boolean on) {
        AddonConfig.PROGRESSION.set(on);
        AddonConfig.PROGRESSION.save();
        for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) syncToggle(p);
        c.getSource().sendSuccess(() -> Component.translatable(on ? "ui.iron_spell_grimoire.mastery.is_on" : "ui.iron_spell_grimoire.mastery.is_off").withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static int setLevel(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        ItemStack book = equipped(p);
        Book b = bookOf(book);
        if (b == null) return noBook(c);
        int lvl = Math.min(IntegerArgumentType.getInteger(c, "level"), cap(book));
        setXp(book, THRESHOLDS[Math.max(1, lvl)]);
        c.getSource().sendSuccess(() -> Component.translatable("ui.iron_spell_grimoire.mastery.set", p.getDisplayName(), lvl).withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static int noBook(CommandContext<CommandSourceStack> c) {
        c.getSource().sendFailure(Component.translatable("ui.iron_spell_grimoire.mastery.no_book"));
        return 0;
    }
}
