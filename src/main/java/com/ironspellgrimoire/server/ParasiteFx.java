package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.entity.AddonBoss;
import com.ironspellgrimoire.entity.HiveNest;
import com.ironspellgrimoire.entity.ShadowSoldier;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.network.ParasitePackets;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Parasite Grimoire - a living strain that rewrites its host (inspired by shapeshifting-virus games; every name and
 * look here is original). Red armoured flesh, grey bone and braided tendrils.
 *
 * Biomass (0-100): what the strain feeds on - Consume devours a weakened foe whole (and heals you), every kill and
 * every blow of an arm form adds a little. The ultimates spend it.
 * Frenzy: feeding past a full gut spills into Frenzy; full, you go Frenzied for 12 s - +40% damage, +25% speed - but
 * the strain eats you (1 health a second) and the arm locks into its talons.
 *
 * Arm forms (the G wheel; the main hand must be free - whatever is in it moves into your pack and comes back after):
 *   Reaper Edge     a great hooked blade: heavy cuts that sweep everything in front of you
 *   Rending Talons  fast hooked claws: every cut bleeds
 *   Spine Launcher  a bone barrel: swing to fire spines
 *   Titan Fist      a huge armoured fist: slow, crushing blows with a shockwave
 *   Bone Lance      a long bone spike: long reach, it runs through to what stands behind
 *   Bulwark         (the other arm) a spined shield: blows from the front lose 45%, and whoever hits it is cut
 *   Chitin Hide     (the body) armour plates over the skin: tougher, a little slower; it eats biomass while worn
 *
 * Movement: sprint-jumps leap high and falls hurt far less; Wall Runner (run up walls), Glider (hold jump falling),
 * Meteor Fall (sneak in mid-air: slam down in a shockwave). Evolution points (one per creature devoured) buy all of
 * this on the Evolution page, and after 6 spent the strain settles into one of three Strains for good:
 * Hunter (speed, the talons and blade), Brute (health, the fist, lance and shield), Hive (spines and ultimates).
 *
 * Pages: 1 Consume, 2 Evolution, 3 Marrow Barrage, 4 Grasping Field, 5 Spine Storm.
 * Client: client/ParasiteClient (HUD, wheel, movement), client/ParasiteArms (the forms on the arm), EvolutionScreen.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class ParasiteFx {
    private ParasiteFx() {}

    static final String ROOT = "isg_parasite";
    /** True while an arm form's own follow-up cut lands (a bleed, a sweep), so it can't trigger the forms again. */
    static boolean armFollow;
    /** An arm form's follow-up cut: a player blow, but one that sets off nothing more. */
    static void armHit(ServerPlayer p, LivingEntity t, float dmg) {
        armFollow = true;
        try { t.hurt(p.damageSources().playerAttack(p), dmg); } finally { armFollow = false; }
    }

    public static final int NONE = 0, REAPER = 1, TALONS = 2, SPINE = 3, TITAN = 4, LANCE = 5, WHIP = 6, RAM = 7, LAST_FORM = 7;
    public static final String[] FORMS = {"", "Reaper Edge", "Rending Talons", "Spine Launcher", "Titan Fist", "Bone Lance", "Whip-Spear", "Battering Ram"};
    public static final int HUNTER = 1, BRUTE = 2, HIVE = 3;
    public static final String[] STRAINS = {"", "Hunter", "Brute", "Hive"};
    public static final int STRAIN_AT = 6;   // evolution points spent before a Strain can settle

    // ------------------------------------------------------------------ the evolution tree
    public static final int LEAP = 0, WALL = 1, GLIDE = 2, METEOR = 3, F_SPINE = 4, F_LANCE = 5, F_BULWARK = 6, F_HIDE = 7,
            HONED = 8, GULLET = 9, DIGEST = 10, RAVENOUS = 11, CATACLYSM = 12, F_WHIP = 13, F_RAM = 14, U_STORM = 15, U_FIELD = 16, U_BARRAGE = 17,
            TENDRIL = 18, BURROW = 19, CLING = 20, ADAPT = 21, SIGHT = 22, MOLT = 23, NETWORK = 24;
    /** {branch (0 Predator, 1 Arsenal, 2 Hunger, 3 Ultimates), max rank, cost per rank}. The Ultimates' nodes are a
     *  choice of path, not ranks: their "rank" is the path taken (1 or 2). */
    public static final int[][] NODES = {
            {0, 3, 1}, {0, 2, 2}, {0, 2, 2}, {0, 3, 2},
            {1, 1, 1}, {1, 1, 1}, {1, 1, 2}, {1, 1, 2}, {1, 3, 2},
            {2, 3, 1}, {2, 3, 1}, {2, 3, 2}, {2, 3, 3},
            {1, 1, 3}, {1, 1, 3},
            {3, 1, 3}, {3, 1, 3}, {3, 1, 3},
            {0, 1, 2}, {0, 1, 3}, {0, 1, 2}, {2, 1, 3}, {2, 1, 2}, {2, 1, 3}, {3, 1, 3}};
    /** The nodes the awakening asks for (the later ones - Tendril, Burrow, Wall Cling, Adaptation - came after it). */
    static final int AWAKEN_NODES = 18;
    public static final String[] NODE_NAMES = {"Leap", "Wall Runner", "Glider", "Meteor Fall",
            "Spine Launcher", "Bone Lance", "Bulwark", "Chitin Hide", "Honed Edges",
            "Deep Gullet", "Rapid Digestion", "Ravenous", "Cataclysm", "Whip-Spear", "Battering Ram",
            "Spine Storm", "Grasping Field", "Marrow Barrage", "Tendril Grapple", "Burrow", "Wall Cling", "Adaptation", "Predator Sight", "Molting", "Hive Network"};
    /** The two paths of each Ultimates node. */
    public static final String[][] PATHS = {{"Tempest", "Impaler"}, {"Maw", "Vortex"}, {"Seeker", "Bomb"}};
    public static final String[][] PATH_DESC = {
            {"Six waves of spines that move with you.", "One giant spine bursts up under your target."},
            {"Whatever dies in the field is devoured.", "Pulls everything in hard, then crushes it."},
            {"Every lance homes on a foe.", "Four lances that burst where they land."}};
    /** Which node must be evolved first. */
    static int needs(int node) { return node == F_WHIP ? F_LANCE : node == F_RAM ? F_BULWARK : -1; }
    static boolean isPath(int node) { return node >= U_STORM && node <= U_BARRAGE; }
    public static final String[] NODE_DESC = {
            "Sprint-jumps leap higher; falls hurt less.",
            "Run up walls (longer at rank 2).",
            "Hold jump while falling to glide.",
            "Sneak in mid-air to slam down: +40% per rank.",
            "Unlocks the Spine Launcher arm.",
            "Unlocks the Bone Lance arm.",
            "Unlocks the Bulwark shield arm.",
            "Unlocks the Chitin Hide armour.",
            "+15% arm form damage per rank.",
            "Devour foes at higher health.",
            "Devouring heals 30% more per rank.",
            "+25% biomass from everything.",
            "Ultimates +20% damage, -10% biomass.",
            "Talons + Lance: a long hooking spine (needs the Bone Lance).",
            "Fist + Bulwark: a guarding ram (needs the Bulwark).",
            "", "", "",
            "Z: pull yourself to what you aim at, or yank light foes to you.",
            "Sneak + Z: sink out of sight for 4 s, then burst up in spines.",
            "Hold sneak against a wall mid-air to cling; jump to pounce.",
            "What hurts you, you grow to resist (up to 40% of each kind).",
            "B: see every creature through walls; the weak marked (drains biomass).",
            "At the brink, shed a husk that draws foes off; +40% speed 3 s.",
            "Grow up to 3 nests and travel between them."};

    // ------------------------------------------------------------------ the Chitin Hide's looks
    public static final String[] LOOKS = {"Bare", "Spiked", "Smooth", "Plated"};
    public static final String[] LOOK_DESC = {"Just the plated skin.", "Spines: melee attackers take 2.", "Sleek: +15% speed instead of slower.", "Heavy plates: +4 armour."};

    // ------------------------------------------------------------------ mutations (rare, from devouring; up to three worn)
    public static final int ACID = 0, TWIN = 1, ECHO = 2, HOLLOW = 3, BARBED = 4, THICK = 5, REGROW = 6, KEEN = 7, MUTATIONS = 8, SLOTS = 3;
    public static final String[] MUT_NAMES = {"Acid Blood", "Twin Hearts", "Echo Hunger", "Hollow Bones", "Barbed Marrow", "Thick Gut", "Regrowth", "Keen Maw"};
    public static final String[] MUT_DESC = {
            "Whoever strikes you up close is burned.",
            "Cheat death once every 5 minutes.",
            "Every kill gives 5 more biomass.",
            "Higher jumps; no fall damage.",
            "Spines hit 25% harder.",
            "Hold up to 150 biomass.",
            "Heal while fed (costs a little biomass).",
            "Consume reaches 3 further, devours at +10%."};

    // ------------------------------------------------------------------ organs (three slots, three choices each)
    public static final int HEART = 0, GUT = 1, GLAND = 2, ORGAN_COST = 4;
    public static final String[] ORGAN_SLOTS = {"Heart", "Gut", "Gland"};
    public static final String[][] ORGANS = {{"Iron Heart", "Racing Heart", "Leech Heart"}, {"Double Stomach", "Slow Gut", "Ripping Gut"},
            {"Acid Gland", "Marrow Forge", "Spore Gland"}};
    public static final String[][] ORGAN_DESC = {
            {"+4 max health.", "+10% attack and move speed.", "Arm-form hits heal 1."},
            {"+40% biomass; Hunger drains twice as fast.", "Hunger drains at half speed; -10% biomass.", "Consume's rip hits 50% harder."},
            {"Spines strip 3 armour for 5 s.", "The Bone Lance also throws a bone spear.", "Grasping Field is 2 blocks wider."}};

    // ------------------------------------------------------------------ adaptation: the kinds of harm the strain learns
    public static final String[] HARMS = {"fire", "fall", "arrows", "blasts", "magic"};
    static final float ADAPT_MAX = 40f;

    // ------------------------------------------------------------------ the strain's signature moves and the Final Form
    public static final String[] ARTS = {"", "Apex Chain", "Colossus", "Swarm"};
    static final int ART_CD = 1200, FINAL_CD = 6000, FINAL_LEN = 600;
    static final int[] ULT_COST = {25, 30, 40, 35, 40};   // Marrow Barrage, Grasping Field, Spine Storm, Collapse Core, Pack Call

    private static final UUID M_DMG = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900001"), M_DMG2 = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000081");
    private static final UUID M_SPD = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900002");
    private static final UUID M_REACH = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900003");
    private static final UUID M_ARMOR = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900004");
    private static final UUID M_TOUGH = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900005");
    private static final UUID M_KB = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900006");
    private static final UUID M_MOVE = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900007");
    private static final UUID M_FRENZY = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900008");
    private static final UUID M_HEALTH = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900009");
    private static final UUID ROOTED = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a90000a");
    private static final UUID M_HUNGER = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a90000b");
    private static final UUID M_POWER = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a90000c");
    private static final UUID M_RANK_HP = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a90000e");
    private static final UUID M_RANK_DMG = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a90000f");
    private static final UUID STUN_MOVE = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900010");
    private static final UUID STUN_HIT = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900011");

    /** Per arm form: {added attack damage, added attack speed (vanilla fist: 1 / 4), added reach}. */
    private static final double[][] FORM_STATS = {{0, 0, 0}, {8, -2.4, 1.0}, {4, -1.0, 0}, {2, -1.5, 0}, {11, -3.2, 0}, {6, -2.8, 2.5}, {6, -2.4, 3.5}, {10, -3.0, 0}};

    // ================================================================== the saved state
    static CompoundTag root(Player p) {
        CompoundTag pd = p.getPersistentData();
        if (!pd.contains(Player.PERSISTED_NBT_TAG)) pd.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag per = pd.getCompound(Player.PERSISTED_NBT_TAG);
        if (!per.contains(ROOT)) per.put(ROOT, new CompoundTag());
        return per.getCompound(ROOT);
    }

    public static boolean equipped(Player p) {
        var b = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
        return b != null && b.is(AddonItemRegistry.PARASITE_GRIMOIRE.get());
    }

    public static float biomass(Player p) { return root(p).getFloat("bio"); }
    public static float frenzy(Player p) { return root(p).getFloat("frenzy"); }
    public static int form(Player p) { return root(p).getInt("form"); }
    public static boolean bulwark(Player p) { return root(p).getBoolean("bulwark"); }
    public static boolean hide(Player p) { return root(p).getBoolean("hide"); }
    public static int strain(Player p) { return root(p).getInt("strain"); }
    public static int points(Player p) { return root(p).getInt("ep"); }
    public static int spent(Player p) { return root(p).getInt("spent"); }
    public static int devoured(Player p) { return root(p).getInt("devoured"); }
    public static int rank(Player p, int node) {
        int[] r = root(p).getIntArray("ranks");
        return node < r.length ? r[node] : 0;
    }
    static int[] ranks(Player p) {
        int[] r = root(p).getIntArray("ranks");
        if (r.length < NODES.length) { int[] n = new int[NODES.length]; System.arraycopy(r, 0, n, 0, r.length); r = n; }
        return r;
    }
    public static boolean frenzied(ServerPlayer p) { return p.serverLevel().getGameTime() < root(p).getLong("frenzyEnd"); }
    public static int path(Player p, int node) { return rank(p, node); }
    public static int owned(Player p) { return root(p).getInt("muts"); }
    public static int[] slots(Player p) {
        int[] s = root(p).getIntArray("mslots");
        if (s.length != SLOTS) { s = new int[SLOTS]; java.util.Arrays.fill(s, -1); }
        return s;
    }
    public static boolean wears(Player p, int mut) { for (int m : slots(p)) if (m == mut) return true; return false; }
    public static float cap(Player p) { return wears(p, THICK) ? 150f : 100f; }
    public static boolean finalForm(ServerPlayer p) { return p.serverLevel().getGameTime() < root(p).getLong("ffEnd"); }
    public static boolean colossus(ServerPlayer p) { return p.serverLevel().getGameTime() < root(p).getLong("colossus"); }
    public static boolean swarm(ServerPlayer p) { return p.serverLevel().getGameTime() < root(p).getLong("swarm"); }
    public static boolean starving(Player p) { return root(p).getInt("empty") >= 30; }
    public static int strain2(Player p) { return root(p).getInt("strain2"); }
    /** How much of a strain you carry: 1 your strain, 1/2 the grafted second one. */
    public static float sw(Player p, int s) { return strain(p) == s ? 1f : strain2(p) == s ? 0.5f : 0f; }
    /** The organ grown in a slot (1-3), 0 none. */
    public static int organ(Player p, int slot) { int[] o = root(p).getIntArray("organs"); return slot < o.length ? o[slot] : 0; }
    public static float adapted(Player p, int harm) { return root(p).getCompound("adapt").getFloat("r" + harm); }
    public static boolean burrowed(ServerPlayer p) { return p.serverLevel().getGameTime() < root(p).getLong("burrow"); }
    public static boolean pounced(ServerPlayer p) { return p.serverLevel().getGameTime() < root(p).getLong("pounce"); }
    public static boolean sight(Player p) { return root(p).getBoolean("sight"); }
    public static int look(Player p) { return root(p).getInt("look"); }
    public static boolean parrying(ServerPlayer p) { return p.serverLevel().getGameTime() < root(p).getLong("parry"); }
    /** The arm torn off and regrowing (Regenerating Limbs): nothing can change on it till then. */
    public static boolean limbGone(ServerPlayer p) { return p.serverLevel().getGameTime() < root(p).getLong("limbCd"); }
    public static float limbWear(Player p) { return root(p).getFloat("limb"); }
    static final float LIMB_MAX = 30f;

    // ------------------------------------------------------------------ Evolution Ranks: by everything ever eaten
    public static final String[] RANKS = {"Larva", "Stalker", "Predator", "Apex", "Sovereign"};
    public static final int[] RANK_AT = {0, 500, 1500, 4000, 10000};
    public static float eaten(Player p) { return root(p).getFloat("eaten"); }
    public static int evoRank(Player p) { int r = 0; for (int i = 0; i < RANK_AT.length; i++) if (eaten(p) >= RANK_AT[i]) r = i; return r; }
    /** Well fed: +5% damage a minute, up to +25%. */
    public static float satedBonus(Player p) { return Math.min(0.25f, root(p).getInt("sated") / 60 * 0.05f); }

    /** Every node at its top rank and every ultimate's path taken. */
    public static boolean maxed(Player p) {
        int[] rk = ranks(p);
        for (int i = 0; i < AWAKEN_NODES; i++) if (rk[i] < NODES[i][1]) return false;
        return true;
    }
    /** The strain awakens once it has settled and every node is evolved. */
    public static boolean awakened(Player p) { return strain(p) != 0 && maxed(p); }

    private static final Map<UUID, Boolean> DIRTY = new HashMap<>();
    static void dirty(Player p) { DIRTY.put(p.getUUID(), true); }

    /** Add biomass (Ravenous and Hive add more); past full, it spills into Frenzy. */
    static void feed(ServerPlayer p, float amount) {
        if (amount <= 0) return;
        amount *= 1f + 0.25f * rank(p, RAVENOUS) + 0.2f * sw(p, HIVE);
        int gut = organ(p, GUT);
        if (gut == 1) amount *= 1.4f; else if (gut == 2) amount *= 0.9f;
        CompoundTag r = root(p);
        int rank0 = evoRank(p);
        r.putFloat("eaten", r.getFloat("eaten") + amount);
        if (evoRank(p) > rank0) rankUp(p);
        float b = r.getFloat("bio") + amount, cap = cap(p);
        if (b > cap) {
            HiveNest nest = nestOf(p);
            if (nest != null && nest.stored() < HiveNest.MAX) nest.setStored(nest.stored() + (b - cap));   // the nest keeps the spill
            else if (!frenzied(p) && !finalForm(p)) r.putFloat("frenzy", Math.min(100, r.getFloat("frenzy") + (b - cap) * 1.5f));
            b = cap;
        }
        r.putFloat("bio", b);
        if (r.getFloat("frenzy") >= 100 && !frenzied(p)) frenzyStart(p);
        dirty(p);
    }

    static void rankUp(ServerPlayer p) {
        int k = evoRank(p);
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.4f, 4.0f, 0.12f, 14);
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            spineFly(sl, p.position().add(0, 1.2, 0), new Vec3(Math.cos(a), 0.4, Math.sin(a)), 4, 1.2f, 0.8f);
        }
        sound(sl, p.position(), SoundEvents.PLAYER_LEVELUP, 1.0f, 0.6f);
        p.displayClientMessage(Component.literal("Your strain grows: " + RANKS[k]).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        applyStats(p);
    }

    static boolean spend(ServerPlayer p, float amount) {
        CompoundTag r = root(p);
        if (r.getFloat("bio") < amount - 0.01f) return false;
        r.putFloat("bio", Math.max(0, r.getFloat("bio") - amount));
        dirty(p);
        return true;
    }

    static int ultCost(Player p, int which) { return Math.round(ULT_COST[which] * (1f - 0.1f * rank(p, CATACLYSM))); }

    /** BookSpell gates: an ultimate needs its biomass (checked where the server knows it). */
    public static boolean canUlt(Player p, int which) { return !(p instanceof ServerPlayer sp) || biomass(sp) >= ultCost(sp, which) - 0.01f; }
    public static boolean canBarrage(Player p) { return canUlt(p, 0); }
    public static boolean canField(Player p) { return canUlt(p, 1); }
    public static boolean canStorm(Player p) { return canUlt(p, 2); }
    public static boolean canCollapse(Player p) { return canUlt(p, 3); }
    public static boolean canPack(Player p) { return !(p instanceof ServerPlayer sp) || biomass(sp) >= ULT_COST[4] - 0.01f; }

    static boolean isBoss(LivingEntity t) { return t instanceof AddonBoss || t.getType().is(Tags.EntityTypes.BOSSES); }

    static boolean foe(ServerPlayer p, LivingEntity t) {
        return t != p && t.isAlive() && !(t instanceof Player op && !com.ironspellgrimoire.AddonConfig.mayHurt(p, op)) && !(t instanceof ShadowSoldier s && p.getUUID().equals(s.ownerUUID()));
    }

    static float ultBoost(ServerPlayer p) { return (1f + 0.2f * rank(p, CATACLYSM)) * (1f + 0.25f * sw(p, HIVE)) * (frenzied(p) ? 1.2f : 1f) * (finalForm(p) ? 1.3f : 1f); }
    static float spineBoost(ServerPlayer p) { return wears(p, BARBED) ? 1.25f : 1f; }
    private static final UUID ACID_STRIP = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a90000d");
    /** A spine landed: the Acid Gland eats at its armour. */
    static void spineLanded(ServerPlayer p, LivingEntity t) {
        if (organ(p, GLAND) == 1 && t.isAlive()) TimedModifiers.apply(t, Attributes.ARMOR, ACID_STRIP, "Acid", -3, AttributeModifier.Operation.ADDITION, 100);
    }

    // ================================================================== the looks (only the addon's own shapes)
    static final int RED = Fx.CRIMSON, BONE = Fx.SILVER;

    /** A tendril lashing from a to b: a red spike growing along it, with a pale bone tip. */
    static void tendril(ServerLevel sl, Vec3 a, Vec3 b, int life) {
        Vec3 d = b.subtract(a);
        float len = (float) d.length();
        if (len < 0.1f) return;
        FxSend.spike(sl, RED, a, d, len, 0.09f, life, 3, FxPacket.EXTEND);
        FxSend.spike(sl, BONE, a.add(d.scale(0.85)), d, len * 0.18f, 0.06f, life, 3, FxPacket.EXTEND);
    }

    /** A bone spine flying from a along dir for dist blocks at speed blocks a tick. */
    static void spineFly(ServerLevel sl, Vec3 a, Vec3 dir, double dist, float speed, float len) {
        int life = Math.max(2, (int) Math.ceil(dist / speed));
        FxSend.spike(sl, BONE, a, dir, len, len * 0.1f, life, 0, FxPacket.POP, 0, dir.normalize().scale(speed), life);
    }

    /** Spines bursting up out of the ground at c. */
    static void groundSpines(ServerLevel sl, Vec3 c, float r, int n, int life) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble(Math.PI * 2), d = r * Math.sqrt(rnd.nextDouble());
            Vec3 base = c.add(Math.cos(a) * d, 0.02, Math.sin(a) * d);
            Vec3 dir = new Vec3(rnd.nextDouble(-0.35, 0.35), 1, rnd.nextDouble(-0.35, 0.35));
            FxSend.spike(sl, i % 3 == 0 ? BONE : RED, base, dir, (float) rnd.nextDouble(0.8, 1.8), 0.12f, life, 3, FxPacket.GROUND);
        }
    }

    static Vec3 handPos(ServerPlayer p) { return Kit.hand(p); }

    // ================================================================== 1. Consume
    /** Lash a tendril into the foe you aim at (5 blocks; Hunter 8, and you lunge to it). Weak enough - under 30% health
     *  (+8% a rank of Deep Gullet), or nearly dead - and not a boss, it is dragged in and devoured whole: biomass by its
     *  size, health back, one evolution point (two for big prey). Otherwise the tendril rips a chunk out of it. */
    public static void consume(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean keen = wears(p, KEEN);
        double range = 5 + 3 * sw(p, HUNTER) + (keen ? 3 : 0);
        if (finalForm(p)) { devourAll(p, power, range + 3); return; }
        LivingEntity t = aimed(p, range, 25);
        if (t == null || !foe(p, t)) {
            tendril(sl, handPos(p), Kit.aim(p, range), 6);
            p.displayClientMessage(Component.literal("Nothing in reach to consume").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Vec3 at = mid(t);
        tendril(sl, handPos(p), at, 8);
        sound(sl, p.position(), SoundEvents.SLIME_ATTACK, 1.0f, 0.6f);
        if (sw(p, HUNTER) > 0 && p.distanceTo(t) > 2.5) {   // the Hunter strain throws itself at its prey
            Vec3 d = t.position().subtract(p.position());
            p.setDeltaMovement(d.normalize().scale(Math.min(1.6, d.length() * 0.28)).add(0, 0.25, 0));
            p.hurtMarked = true;
        }
        if (!devourable(p, t, 0f)) {
            t.invulnerableTime = 0;
            AstaFx.damage(p, t, power * 1.2f * (organ(p, GUT) == 3 ? 1.5f : 1f), "consume");   // Ripping Gut: harder
            Fx.impact(sl, RED, at, 0.9f);
            feed(p, 5);
            p.heal(1);
            return;
        }
        Kit.push(t, p.position().subtract(t.position()), 0.6, 0.2);   // dragged in, wrapped up and devoured
        AstaFx.later(5, () -> { if (t.isAlive() && alive(p)) devour(p, t); });
    }

    /** Weak enough to be devoured: under 30% health (+8% a rank of Deep Gullet, +10% Keen Maw, + extra), or nearly
     *  dead - and never a boss. */
    static boolean devourable(ServerPlayer p, LivingEntity t, float extra) {
        if (isBoss(t)) return false;
        int gullet = rank(p, GULLET);
        float pct = t.getHealth() / Math.max(1f, t.getMaxHealth());
        return pct <= 0.30f + 0.08f * gullet + (wears(p, KEEN) ? 0.1f : 0f) + extra || t.getHealth() <= 6 + 2 * gullet;
    }

    /** Wrap it up and devour it: biomass by its size, health back, evolution, and now and then a mutation. */
    static void devour(ServerPlayer p, LivingEntity t) {
        ServerLevel sl = p.serverLevel();
        float maxHp = t.getMaxHealth();
        Vec3 c = mid(t);
        for (int k = 0; k < 5; k++) {   // tendrils wrap it from every side
            double a = k * Math.PI * 2 / 5;
            Vec3 o = c.add(Math.cos(a) * 1.2, -0.6 + k * 0.25, Math.sin(a) * 1.2);
            tendril(sl, o, c, 6);
        }
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, c, 0.2f, 1.4f, 0.1f, 8);
        sound(sl, c, SoundEvents.GENERIC_EAT, 1.2f, 0.6f);
        sound(sl, c, SoundEvents.SLIME_SQUISH, 1.0f, 0.5f);
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().playerAttack(p), t.getHealth() + t.getAbsorptionAmount() + 1000f);
        CompoundTag r = root(p);
        r.putInt("devoured", r.getInt("devoured") + 1);
        ParasiteExtras.onDevour(p, t);
        int ep = maxHp >= 40 ? 2 : 1;
        r.putInt("ep", r.getInt("ep") + ep);
        float heal = (3 + maxHp * 0.15f) * (1f + 0.3f * rank(p, DIGEST)) + (frenzied(p) ? 4 : 0);
        p.heal(heal);
        feed(p, Math.min(40f, 8f + maxHp * 0.6f));
        p.displayClientMessage(Component.literal("Devoured  +" + ep + " evolution").withStyle(ChatFormatting.RED), true);
        rollMutation(p, maxHp >= 40 ? 0.25f : 0.03f);
        dirty(p);
    }

    /** The Final Form's Consume: tendrils lash out at everything round you - the weak devoured, the rest torn. */
    static void devourAll(ServerPlayer p, float power, double range) {
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.5f, (float) range, 0.1f, 8);
        sound(sl, p.position(), SoundEvents.RAVAGER_ROAR, 0.8f, 1.4f);
        for (LivingEntity t : Kit.near(sl, p, p.position().add(0, 1, 0), range)) {
            if (!foe(p, t)) continue;
            tendril(sl, handPos(p), mid(t), 8);
            if (devourable(p, t, 0.15f)) { Kit.push(t, p.position().subtract(t.position()), 0.6, 0.2); AstaFx.later(5, () -> { if (t.isAlive() && alive(p)) devour(p, t); }); }
            else { t.invulnerableTime = 0; AstaFx.damage(p, t, power * 0.8f, "consume"); feed(p, 2); }
        }
    }

    /** Now and then a mutation grows out of what was eaten (one you don't have yet). */
    static void rollMutation(ServerPlayer p, float chance) {
        if (ThreadLocalRandom.current().nextFloat() >= chance) return;
        int have = owned(p);
        List<Integer> free = new ArrayList<>();
        for (int m = 0; m < MUTATIONS; m++) if ((have & (1 << m)) == 0) free.add(m);
        if (free.isEmpty()) return;
        int m = free.get(ThreadLocalRandom.current().nextInt(free.size()));
        root(p).putInt("muts", have | (1 << m));
        FxSend.ring(p.serverLevel(), Fx.GOLD | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.3f, 2.0f, 0.06f, 12);
        sound(p.serverLevel(), p.position(), SoundEvents.PLAYER_LEVELUP, 0.8f, 0.5f);
        p.displayClientMessage(Component.literal("Mutation: " + MUT_NAMES[m] + " - wear it on the Evolution page").withStyle(ChatFormatting.GOLD), false);
        dirty(p);
    }

    static void toggleMutation(ServerPlayer p, int m) {
        if (m < 0 || m >= MUTATIONS || (owned(p) & (1 << m)) == 0) return;
        int[] sl = slots(p);
        boolean was = false;
        for (int i = 0; i < SLOTS; i++) if (sl[i] == m) { sl[i] = -1; was = true; }
        if (!was) {
            int free = -1;
            for (int i = 0; i < SLOTS && free < 0; i++) if (sl[i] < 0) free = i;
            if (free < 0) { p.displayClientMessage(Component.literal("All three mutation slots are full").withStyle(ChatFormatting.GRAY), true); return; }
            sl[free] = m;
        }
        root(p).putIntArray("mslots", sl);
        if (!wears(p, THICK) && biomass(p) > 100) root(p).putFloat("bio", 100);
        sound(p.serverLevel(), p.position(), SoundEvents.SLIME_SQUISH, 0.8f, was ? 1.2f : 0.7f);
        sync(p, false);
    }

    // ================================================================== 2. Evolution (the page)
    public static void evolution(ServerPlayer p, float power) { sync(p, true); }

    // ================================================================== 3. Marrow Barrage
    /** Ten bone lances fire from your arm in a fan over a second, each running through everything for 20 blocks.
     *  Seeker: every lance homes on a foe ahead. Bomb: four lances that burst where they land. */
    public static void marrowBarrage(ServerPlayer p, float power) {
        if (!spend(p, ultCost(p, 0))) { noBiomass(p, 0); return; }
        ServerLevel sl = p.serverLevel();
        float dmg = power * ultBoost(p) * spineBoost(p);
        int path = path(p, U_BARRAGE);
        sound(sl, p.position(), SoundEvents.TRIDENT_THROW, 1.0f, 0.6f);
        int n0 = path == 2 ? 4 : 10;
        for (int k = 0; k < n0; k++) {
            final int n = k;
            AstaFx.later(k * (path == 2 ? 4 : 2), () -> {
                if (!alive(p)) return;
                Vec3 look = p.getLookAngle();
                Vec3 from = handPos(p);
                double yaw = Math.toRadians(((n % 2 == 0) ? 1 : -1) * (n * (path == 2 ? 5 : 2.2)));
                Vec3 dir = rotY(look, yaw).add(0, (n % 3 - 1) * 0.04, 0).normalize();
                if (path == 1) {   // Seeker: on the foes ahead, in turn
                    List<LivingEntity> ahead = new ArrayList<>(Kit.near(sl, p, p.position().add(look.scale(10)), 12));
                    ahead.removeIf(t -> !foe(p, t) || mid(t).subtract(from).normalize().dot(look) < 0.3);
                    ahead.sort(Comparator.comparingDouble(t -> t.distanceToSqr(p)));
                    if (!ahead.isEmpty()) dir = mid(ahead.get(n % ahead.size())).subtract(from).normalize();
                }
                Vec3 end = clip(sl, p, from, from.add(dir.scale(20)));
                spineFly(sl, from, dir, end.distanceTo(from), 2.5f, path == 2 ? 2.0f : 1.4f);
                sound(sl, from, SoundEvents.ARROW_SHOOT, 0.6f, 0.5f + n * 0.05f);
                for (LivingEntity t : Kit.along(sl, p, from, end, path == 1 ? 1.0 : 0.7)) {
                    if (!foe(p, t)) continue;
                    int delay = (int) (mid(t).distanceTo(from) / 2.5);
                    AstaFx.later(delay, () -> {
                        if (!t.isAlive() || !alive(p)) return;
                        t.invulnerableTime = 0;
                        AstaFx.damage(p, t, dmg, "marrow_barrage");
                        spineLanded(p, t);
                        Fx.spark(sl, BONE, mid(t), 0.7f);
                    });
                }
                if (path == 2) {   // Bomb: it bursts where it lands
                    final Vec3 at = end;
                    AstaFx.later((int) (end.distanceTo(from) / 2.5) + 1, () -> {
                        if (!alive(p)) return;
                        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, at, 0.3f, 3.0f, 0.12f, 8);
                        groundSpines(sl, ground(sl, p, at), 2.5f, 8, 10);
                        sound(sl, at, SoundEvents.GENERIC_EXPLODE, 0.6f, 1.2f);
                        for (LivingEntity t : Kit.near(sl, p, at, 3.0)) {
                            if (!foe(p, t)) continue;
                            t.invulnerableTime = 0;
                            AstaFx.damage(p, t, dmg * 1.8f, "marrow_barrage");
                        }
                    });
                }
            });
        }
    }

    // ================================================================== 4. Grasping Field
    /** Tendrils and spines burst up over a 5-block field where you aim (16 blocks): everything in it is held fast and
     *  dragged toward the middle, torn at every half second for 4 s. */
    public static void graspingField(ServerPlayer p, float power) {
        if (!spend(p, ultCost(p, 1))) { noBiomass(p, 1); return; }
        ServerLevel sl = p.serverLevel();
        LivingEntity aimed = aimed(p, 16, 10);
        Vec3 aim = aimed != null && foe(p, aimed) ? aimed.position().add(0, 0.5, 0) : Kit.aim(p, 16);
        Vec3 c = ground(sl, p, aim);
        int path = path(p, U_FIELD);
        float r = organ(p, GLAND) == 3 ? 7f : 5f, dmg = power * 0.35f * ultBoost(p);   // Spore Gland: wider
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, c.add(0, 0.05, 0), r * 0.9f, r, 0.08f, 80);
        sound(sl, c, SoundEvents.EVOKER_FANGS_ATTACK, 1.2f, 0.6f);
        for (int w = 0; w < 8; w++) {
            final int wave = w;
            AstaFx.later(w * 10, () -> {
                if (!alive(p)) return;
                groundSpines(sl, c, r, wave == 0 ? 16 : 7, 12);
                if (wave % 2 == 0) sound(sl, c, SoundEvents.SLIME_SQUISH, 0.8f, 0.6f);
                for (LivingEntity t : Kit.near(sl, p, c.add(0, 1, 0), r + 0.5)) {
                    if (!foe(p, t)) continue;
                    TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, ROOTED, "Grasped", -0.9, AttributeModifier.Operation.MULTIPLY_TOTAL, 14);
                    Vec3 to = c.subtract(t.position());
                    if (to.horizontalDistance() > 0.8 && !isBoss(t)) Kit.push(t, new Vec3(to.x, 0, to.z), path == 2 ? 0.45 : 0.18, 0);   // Vortex: hard
                    tendril(sl, new Vec3(t.getX(), c.y, t.getZ()), mid(t), 6);
                    t.invulnerableTime = 0;
                    boolean crush = path == 2 && wave == 7;   // Vortex: the last wave crushes
                    AstaFx.damage(p, t, crush ? dmg * 4f : dmg, "grasping_field");
                    if (crush) { FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, mid(t), 0.2f, 1.6f, 0.1f, 8); sound(sl, mid(t), SoundEvents.ANVIL_LAND, 0.5f, 0.6f); }
                    if (path == 1 && t.isAlive() && devourable(p, t, -0.15f)) devour(p, t);   // Maw: what weakens in it is eaten
                }
            });
        }
    }

    /** The floor under a point (a few blocks down at most). */
    static Vec3 ground(ServerLevel sl, Entity p, Vec3 at) {
        Vec3 down = clip(sl, p, at.add(0, 0.5, 0), at.add(0, -6, 0));
        return down;
    }

    // ================================================================== 5. Spine Storm
    /** Your body splits open into a storm of bone: three waves of spines (four for the Hive) shred everything within
     *  8 blocks of you. Tempest: six lighter waves that move with you. Impaler: one giant spine bursts up under the
     *  foe you aim at. */
    public static void spineStorm(ServerPlayer p, float power) {
        if (!spend(p, ultCost(p, 2))) { noBiomass(p, 2); return; }
        ServerLevel sl = p.serverLevel();
        float dmg = power * ultBoost(p) * spineBoost(p);
        int path = path(p, U_STORM);
        if (path == 2) { impaler(p, dmg); return; }
        int waves = (path == 1 ? 6 : 3) + (sw(p, HIVE) >= 1 ? 1 : 0);
        float each = path == 1 ? dmg * 0.7f : dmg;
        sound(sl, p.position(), SoundEvents.WARDEN_SONIC_CHARGE, 0.8f, 1.4f);
        FxSend.shake(sl, p.position(), 0.5f, 10, 16);
        for (int w = 0; w < waves; w++) {
            final int wave = w;
            AstaFx.later(4 + w * 8, () -> {
                if (!alive(p)) return;
                Vec3 c = p.position().add(0, 1.0, 0);
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                for (int i = 0; i < 22; i++) {
                    double a = (i + wave * 0.5) * Math.PI * 2 / 22 + rnd.nextDouble(-0.08, 0.08);
                    Vec3 dir = new Vec3(Math.cos(a), rnd.nextDouble(-0.25, 0.45), Math.sin(a)).normalize();
                    spineFly(sl, c.add(dir.scale(0.6)), dir, 8, 2.0f, 1.1f);
                }
                FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, c, 0.4f, 3.0f, 0.08f, 6);
                sound(sl, c, SoundEvents.ARROW_SHOOT, 1.2f, 0.4f + wave * 0.15f);
                sound(sl, c, SoundEvents.SLIME_SQUISH, 1.0f, 0.4f);
                for (LivingEntity t : Kit.near(sl, p, c, 8)) {
                    if (!foe(p, t)) continue;
                    t.invulnerableTime = 0;
                    AstaFx.damage(p, t, each, "spine_storm");
                    spineLanded(p, t);
                    Fx.spark(sl, BONE, mid(t), 0.6f);
                }
            });
        }
    }

    /** Impaler: the ground cracks under the foe you aim at (or 10 blocks ahead) and one giant spine bursts up. */
    static void impaler(ServerPlayer p, float dmg) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t0 = aimed(p, 20, 12);
        Vec3 at = t0 != null && foe(p, t0) ? t0.position() : ground(sl, p, Kit.aim(p, 10));
        groundSpines(sl, at, 1.5f, 6, 10);
        sound(sl, at, SoundEvents.EVOKER_FANGS_ATTACK, 1.2f, 0.5f);
        AstaFx.later(8, () -> {
            if (!alive(p)) return;
            FxSend.spike(sl, BONE, at.add(0, -0.5, 0), new Vec3(0, 1, 0), 6.5f, 1.1f, 30, 4, FxPacket.GROUND);
            FxSend.spike(sl, RED, at.add(0, -0.5, 0), new Vec3(0.15, 1, 0.1), 4.0f, 0.7f, 30, 4, FxPacket.GROUND);
            FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, at.add(0, 0.1, 0), 0.5f, 3.0f, 0.15f, 10);
            FxSend.shake(sl, at, 0.7f, 8, 16);
            sound(sl, at, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.7f);
            for (LivingEntity t : Kit.near(sl, p, at.add(0, 1, 0), 2.8)) {
                if (!foe(p, t)) continue;
                t.invulnerableTime = 0;
                AstaFx.damage(p, t, t == t0 ? dmg * 4f : dmg * 1.5f, "spine_storm");
                if (!isBoss(t)) { t.setDeltaMovement(t.getDeltaMovement().add(0, 1.1, 0)); t.hurtMarked = true; }
            }
        });
    }

    static void noBiomass(ServerPlayer p, int which) {
        p.displayClientMessage(Component.literal("Needs " + ultCost(p, which) + " biomass").withStyle(ChatFormatting.RED), true);
    }

    // ================================================================== frenzy
    static void frenzyStart(ServerPlayer p) {
        CompoundTag r = root(p);
        r.putLong("frenzyEnd", p.serverLevel().getGameTime() + 240);
        r.putFloat("frenzy", 100);
        if (form(p) != TALONS && p.getMainHandItem().isEmpty()) setForm(p, TALONS, true);
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.4f, 3.5f, 0.1f, 10);
        sound(sl, p.position(), SoundEvents.RAVAGER_ROAR, 0.9f, 1.3f);
        p.displayClientMessage(Component.literal("FRENZY - the strain is eating you").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        dirty(p);
    }

    static void frenzyEnd(ServerPlayer p) {
        CompoundTag r = root(p);
        r.putLong("frenzyEnd", 0);
        r.putFloat("frenzy", 0);
        r.putFloat("bio", Math.min(r.getFloat("bio"), 50));
        p.displayClientMessage(Component.literal("The frenzy passes").withStyle(ChatFormatting.GRAY), true);
        dirty(p);
    }

    // ================================================================== forms (the G wheel)
    static boolean formUnlocked(Player p, int f) {
        return switch (f) {
            case SPINE -> rank(p, F_SPINE) > 0;
            case LANCE -> rank(p, F_LANCE) > 0;
            case WHIP -> rank(p, F_WHIP) > 0;
            case RAM -> rank(p, F_RAM) > 0;
            default -> true;
        };
    }

    /** Move what's in a hand into the pack so the arm can change; false if there's nowhere to put it. */
    private static boolean freeHand(ServerPlayer p, boolean main, String key) {
        ItemStack held = main ? p.getMainHandItem() : p.getOffhandItem();
        if (held.isEmpty()) return true;
        var inv = p.getInventory();
        int slot = -1;
        for (int i = 9; i < 36 && slot < 0; i++) if (inv.getItem(i).isEmpty()) slot = i;
        for (int i = 0; i < 9 && slot < 0; i++) if (i != inv.selected && inv.getItem(i).isEmpty()) slot = i;
        if (slot < 0) {
            p.displayClientMessage(Component.literal("Your hand and your pack are both full").withStyle(ChatFormatting.RED), true);
            return false;
        }
        inv.setItem(slot, held.copy());
        if (main) inv.setItem(inv.selected, ItemStack.EMPTY); else inv.offhand.set(0, ItemStack.EMPTY);
        CompoundTag r = root(p);
        r.putInt(key, slot + 1);
        r.putInt(key + "_from", main ? inv.selected : -1);
        return true;
    }

    /** Give back what freeHand put away (if the hand is still empty and it's still where it was put). */
    private static void restoreHand(ServerPlayer p, boolean main, String key) {
        CompoundTag r = root(p);
        int slot = r.getInt(key) - 1;
        int from = r.getInt(key + "_from");
        r.remove(key); r.remove(key + "_from");
        if (slot < 0) return;
        var inv = p.getInventory();
        ItemStack s = inv.getItem(slot);
        if (s.isEmpty()) return;
        if (main) {
            if (from < 0 || from > 8 || !inv.getItem(from).isEmpty()) return;
            inv.setItem(from, s.copy());
        } else {
            if (!inv.offhand.get(0).isEmpty()) return;
            inv.offhand.set(0, s.copy());
        }
        inv.setItem(slot, ItemStack.EMPTY);
    }

    public static void setForm(ServerPlayer p, int f, boolean forced) {
        if (f < NONE || f > LAST_FORM) return;
        CompoundTag r = root(p);
        int cur = r.getInt("form");
        if (!forced && limbGone(p)) {
            p.displayClientMessage(Component.literal("Your arm is still regrowing").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!forced && frenzied(p) && f != cur) {
            p.displayClientMessage(Component.literal("The frenzy won't let go of your arm").withStyle(ChatFormatting.DARK_RED), true);
            return;
        }
        if (f == cur) f = NONE;   // picking the form you have retracts it
        if (ParasiteExtras.apex(p) == 2) r.putInt("form2", f == NONE ? NONE : cur != NONE && cur != f ? cur : r.getInt("form2") == f ? NONE : r.getInt("form2"));   // Living Armory: the last form to the other arm
        if (f != NONE && !formUnlocked(p, f)) {
            p.displayClientMessage(Component.literal("Evolve " + FORMS[f] + " first").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (f != NONE && cur == NONE && !freeHand(p, true, "stash")) return;
        r.putInt("form", f);
        ServerLevel sl = p.serverLevel();
        if (f == NONE) {
            restoreHand(p, true, "stash");
            sound(sl, p.position(), SoundEvents.SLIME_SQUISH_SMALL, 0.8f, 0.7f);
        } else {
            ParasiteExtras.transformFx(p, f);
            sound(sl, p.position(), SoundEvents.SLIME_SQUISH, 0.9f, 0.6f);
            sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_NETHERITE, 0.6f, 0.6f);
        }
        applyStats(p);
        dirty(p);
    }

    public static void toggleBulwark(ServerPlayer p) {
        CompoundTag r = root(p);
        boolean on = !r.getBoolean("bulwark");
        if (limbGone(p)) { p.displayClientMessage(Component.literal("Your arm is still regrowing").withStyle(ChatFormatting.GRAY), true); return; }
        if (on && rank(p, F_BULWARK) <= 0) { p.displayClientMessage(Component.literal("Evolve the Bulwark first").withStyle(ChatFormatting.GRAY), true); return; }
        if (on && !freeHand(p, false, "stashOff")) return;
        r.putBoolean("bulwark", on);
        if (!on) restoreHand(p, false, "stashOff");
        sound(p.serverLevel(), p.position(), on ? SoundEvents.ARMOR_EQUIP_NETHERITE : SoundEvents.SLIME_SQUISH_SMALL, 0.8f, 0.6f);
        applyStats(p);
        dirty(p);
    }

    public static void toggleHide(ServerPlayer p) {
        CompoundTag r = root(p);
        boolean on = !r.getBoolean("hide");
        if (on && rank(p, F_HIDE) <= 0) { p.displayClientMessage(Component.literal("Evolve the Chitin Hide first").withStyle(ChatFormatting.GRAY), true); return; }
        if (on && biomass(p) < 5) { p.displayClientMessage(Component.literal("Not enough biomass to grow a hide").withStyle(ChatFormatting.RED), true); return; }
        r.putBoolean("hide", on);
        sound(p.serverLevel(), p.position(), on ? SoundEvents.ARMOR_EQUIP_NETHERITE : SoundEvents.SLIME_SQUISH_SMALL, 0.8f, 0.5f);
        applyStats(p);
        dirty(p);
    }

    /** Drop every form (the grimoire was put away): hands given back. */
    static void retractAll(ServerPlayer p) {
        CompoundTag r = root(p);
        boolean any = r.getInt("form") != NONE || r.getBoolean("bulwark") || r.getBoolean("hide");
        if (r.getInt("form") != NONE) { r.putInt("form", NONE); restoreHand(p, true, "stash"); }
        r.putInt("form2", NONE);
        if (r.getBoolean("bulwark")) { r.putBoolean("bulwark", false); restoreHand(p, false, "stashOff"); }
        r.putBoolean("hide", false);
        if (any) dirty(p);
        applyStats(p);
    }

    // ------------------------------------------------------------------ attributes for the current shape
    private static void setMod(LivingEntity e, Attribute a, UUID id, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance ai = e.getAttribute(a);
        if (ai == null) return;
        AttributeModifier cur = ai.getModifier(id);
        if (amount == 0) { if (cur != null) ai.removeModifier(id); return; }
        if (cur != null && cur.getAmount() == amount && cur.getOperation() == op) return;
        if (cur != null) ai.removeModifier(id);
        ai.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    static void applyStats(ServerPlayer p) {
        boolean on = equipped(p);
        int f = on ? form(p) : NONE;
        double[] s = FORM_STATS[f];
        float hu = on ? sw(p, HUNTER) : 0, br = on ? sw(p, BRUTE) : 0;
        int heart = on ? organ(p, HEART) : 0;
        boolean fr = on && frenzied(p);
        setMod(p, Attributes.ATTACK_DAMAGE, M_DMG, "Parasite arm", s[0], AttributeModifier.Operation.ADDITION);
        setMod(p, Attributes.ATTACK_SPEED, M_SPD, "Parasite arm", s[1] + (fr ? 0.6 : 0) + ((f == TALONS || f == REAPER) ? 0.3 * hu : 0) + (heart == 2 ? 0.4 : 0), AttributeModifier.Operation.ADDITION);
        setMod(p, ForgeMod.ENTITY_REACH.get(), M_REACH, "Parasite arm", s[2], AttributeModifier.Operation.ADDITION);
        int f2 = on ? ParasiteExtras.form2(p) : NONE;   // Living Armory: half the other arm's strength
        setMod(p, Attributes.ATTACK_DAMAGE, M_DMG2, "Parasite off arm", f2 > NONE ? 0.5 * FORM_STATS[f2][0] : 0, AttributeModifier.Operation.ADDITION);
        boolean hd = on && hide(p), bw = on && bulwark(p);
        boolean colA = on && colossus(p), finA = on && finalForm(p);
        int lk = hd ? look(p) : 0;
        setMod(p, Attributes.ARMOR, M_ARMOR, "Parasite armour", (hd ? 6 : 0) + (lk == 3 ? 4 : 0) + (bw ? 2 : 0) + (colA ? 10 : 0) + (finA ? 8 : 0), AttributeModifier.Operation.ADDITION);
        setMod(p, Attributes.ARMOR_TOUGHNESS, M_TOUGH, "Parasite armour", hd ? 3 : 0, AttributeModifier.Operation.ADDITION);
        setMod(p, Attributes.KNOCKBACK_RESISTANCE, M_KB, "Parasite armour", colA || finA ? 1.0 : (hd ? 0.3 : 0) + 0.2 * br, AttributeModifier.Operation.ADDITION);
        setMod(p, Attributes.MOVEMENT_SPEED, M_MOVE, "Parasite stride", (hd ? (lk == 2 ? 0.15 : -0.08) : 0) + 0.2 * hu + (heart == 2 ? 0.1 : 0) + (on && burrowed(p) ? 0.4 : 0) + (fr ? 0.25 : 0) + (colA ? -0.1 : 0) + (finA ? 0.3 : 0), AttributeModifier.Operation.MULTIPLY_BASE);
        setMod(p, Attributes.ATTACK_DAMAGE, M_FRENZY, "Frenzy", fr ? 0.4 : 0, AttributeModifier.Operation.MULTIPLY_TOTAL);
        setMod(p, Attributes.MAX_HEALTH, M_HEALTH, "Brute strain", 6 * br + (heart == 1 ? 4 : 0), AttributeModifier.Operation.ADDITION);
        int ek = on ? evoRank(p) : 0;
        setMod(p, Attributes.MAX_HEALTH, M_RANK_HP, "Evolution rank", ek, AttributeModifier.Operation.ADDITION);
        setMod(p, Attributes.ATTACK_DAMAGE, M_RANK_DMG, "Evolution rank", 0.03 * ek, AttributeModifier.Operation.MULTIPLY_TOTAL);
        setMod(p, Attributes.ATTACK_DAMAGE, M_HUNGER, "Hunger", on ? (starving(p) ? -0.15 : satedBonus(p)) : 0, AttributeModifier.Operation.MULTIPLY_TOTAL);
        boolean col = on && colossus(p), fin = on && finalForm(p);
        setMod(p, Attributes.ATTACK_DAMAGE, M_POWER, "Awakened", col || fin ? 0.5 : 0, AttributeModifier.Operation.MULTIPLY_TOTAL);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    // ================================================================== the client's requests
    public static void handle(ServerPlayer p, int action, int arg) {
        boolean shadow = action >= 20 && action <= 26 || action == 40;
        if (!shadow && !equipped(p)) return;
        switch (action) {
            case 1 -> setForm(p, arg, false);
            case 2 -> toggleBulwark(p);
            case 3 -> toggleHide(p);
            case 4 -> fireSpine(p);
            case 5 -> slamStart(p, arg / 10f);
            case 6 -> root(p).putLong("glide", arg > 0 ? p.serverLevel().getGameTime() + 400 : p.serverLevel().getGameTime() + 10);
            case 7 -> buy(p, arg);
            case 8 -> chooseStrain(p, arg);
            case 9 -> sync(p, true);
            case 10 -> toggleMutation(p, arg);
            case 11 -> strainArt(p);
            case 12 -> finalFormStart(p);
            case 13 -> grapple(p);
            case 14 -> burrow(p);
            case 15 -> { if (rank(p, CLING) > 0) { root(p).putLong("pounce", p.serverLevel().getGameTime() + 40); root(p).putLong("glide", p.serverLevel().getGameTime() + 60); } }
            case 16 -> secondStrain(p, arg);
            case 17 -> growOrgan(p, arg / 10, arg % 10);
            case 18 -> parry(p);
            case 19 -> growNest(p);
            case 27 -> toggleSight(p);
            case 28 -> nestTravel(p, arg);
            case 30 -> airDash(p);
            case 31 -> { ParasiteExtras.chooseApex(p, arg); sync(p, false); }
            case 32 -> { ParasiteExtras.pin(p, arg / 10, arg % 10); sync(p, false); }
            case 29 -> { if (arg >= 0 && arg < LOOKS.length) { root(p).putInt("look", arg); applyStats(p); sound(p.serverLevel(), p.position(), SoundEvents.ARMOR_EQUIP_NETHERITE, 0.8f, 0.7f); sync(p, false); } }
            case 20, 21, 22, 23, 24, 25, 26 -> shadowCommand(p, action - 20);
            case 40 -> { var b = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p); if (b != null && b.is(AddonItemRegistry.SHADOW_GRIMOIRE.get())) ShadowArmy.cycleLoadout(p); }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ the Spine Launcher
    private static final Map<UUID, Long> SPINE_CD = new HashMap<>();

    static void fireSpine(ServerPlayer p) {
        boolean spear = form(p) == LANCE && organ(p, GLAND) == 2;   // the Marrow Forge: the lance throws a bone spear
        if (form(p) != SPINE && !spear) return;
        long now = p.serverLevel().getGameTime();
        Long next = SPINE_CD.get(p.getUUID());
        if (next != null && now < next) return;
        SPINE_CD.put(p.getUUID(), now + (spear ? 20 : frenzied(p) ? 5 : 8));
        ServerLevel sl = p.serverLevel();
        float dmg = (spear ? 9f : 6f) * (1f + 0.15f * rank(p, HONED)) * (frenzied(p) ? 1.4f : 1f) * spineBoost(p);
        int n = spear ? 1 : 1 + Math.round(2 * sw(p, HIVE));
        sound(sl, p.position(), SoundEvents.ARROW_SHOOT, 0.9f, 0.6f);
        for (int k = 0; k < n; k++) {
            Vec3 dir = rotY(p.getLookAngle(), Math.toRadians((k - (n - 1) / 2.0) * 6)).normalize();
            Vec3 from = handPos(p);
            Vec3 end = clip(sl, p, from, from.add(dir.scale(24)));
            List<LivingEntity> hit = new ArrayList<>(Kit.along(sl, p, from, end, 0.6));
            hit.removeIf(t -> !foe(p, t));
            hit.sort(Comparator.comparingDouble(t -> t.distanceToSqr(from)));
            if (!hit.isEmpty()) end = mid(hit.get(0));
            spineFly(sl, from, dir, end.distanceTo(from), 3f, 0.9f);
            if (hit.isEmpty()) continue;
            LivingEntity t = hit.get(0);
            int delay = (int) (end.distanceTo(from) / 3);
            AstaFx.later(Math.max(1, delay), () -> {
                if (!t.isAlive() || !alive(p)) return;
                t.invulnerableTime = 0;
                armHit(p, t, dmg);
                spineLanded(p, t);
                Fx.spark(sl, BONE, mid(t), 0.5f);
                feed(p, 1);
            });
        }
    }

    // ------------------------------------------------------------------ Meteor Fall
    static void slamStart(ServerPlayer p, float height) {
        if (p.onGround()) return;
        CompoundTag r = root(p);
        r.putLong("slam", p.serverLevel().getGameTime() + 60);
        r.putDouble("slamY", p.getY());
        FxSend.spike(p.serverLevel(), RED, p.position().add(0, 2.2, 0), new Vec3(0, -1, 0), 2.0f, 0.3f, 6, 0, FxPacket.POP);
        sound(p.serverLevel(), p.position(), SoundEvents.PHANTOM_SWOOP, 0.8f, 0.6f);
    }

    static void slamLand(ServerPlayer p) {
        CompoundTag r = root(p);
        double fell = Math.max(0, r.getDouble("slamY") - p.getY());
        r.remove("slam"); r.remove("slamY");
        ServerLevel sl = p.serverLevel();
        float radius = (3.5f + (float) Math.min(3, fell * 0.15)) * (1f + 0.5f * sw(p, BRUTE));
        float dmg = (4f + (float) Math.min(20, fell * 0.6)) * (1f + 0.4f * rank(p, METEOR)) * (form(p) == TITAN ? 1.5f : 1f) * (frenzied(p) ? 1.4f : 1f);
        Vec3 c = p.position();
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, c.add(0, 0.05, 0), 0.3f, radius, 0.15f, 10);
        groundSpines(sl, c, radius * 0.8f, 12, 10);
        FxSend.shake(sl, c, 0.6f, 8, 14);
        sound(sl, c, SoundEvents.GENERIC_EXPLODE, 0.7f, 0.8f);
        sound(sl, c, SoundEvents.ANVIL_LAND, 0.5f, 0.5f);
        for (LivingEntity t : Kit.near(sl, p, c.add(0, 0.5, 0), radius)) {
            if (!foe(p, t)) continue;
            t.invulnerableTime = 0;
            armHit(p, t, dmg);
            Kit.push(t, t.position().subtract(c), 0.9, 0.45);
        }
    }

    // ------------------------------------------------------------------ evolution and strains
    /** Evolve a node (arg = node; for an Ultimates node, node + 100 x the path, 1 or 2). */
    static void buy(ServerPlayer p, int arg) {
        int node = arg % 100, way = arg / 100;
        if (node < 0 || node >= NODES.length) return;
        int[] rk = ranks(p);
        CompoundTag r = root(p);
        if (rk[node] >= NODES[node][1]) return;
        if (isPath(node) && way != 1 && way != 2) return;
        if (needs(node) >= 0 && rk[needs(node)] <= 0) {
            p.displayClientMessage(Component.literal("Evolve " + NODE_NAMES[needs(node)] + " first").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int cost = NODES[node][2];
        if (r.getInt("ep") < cost) { p.displayClientMessage(Component.literal("Not enough evolution points").withStyle(ChatFormatting.RED), true); return; }
        r.putInt("ep", r.getInt("ep") - cost);
        r.putInt("spent", r.getInt("spent") + cost);
        boolean wasAwake = awakened(p);
        rk[node] = isPath(node) ? way : rk[node] + 1;
        r.putIntArray("ranks", rk);
        sound(p.serverLevel(), p.position(), SoundEvents.SLIME_SQUISH, 1.0f, 0.5f);
        sound(p.serverLevel(), p.position(), SoundEvents.PLAYER_LEVELUP, 0.5f, 0.6f);
        FxSend.ring(p.serverLevel(), RED | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.3f, 1.6f, 0.06f, 8);
        if (!wasAwake && awakened(p)) awaken(p);
        applyStats(p);
        sync(p, false);
    }

    static void awaken(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.5f, 5.0f, 0.15f, 16);
        FxSend.shake(sl, p.position(), 0.5f, 10, 12);
        sound(sl, p.position(), SoundEvents.RAVAGER_ROAR, 1.0f, 0.6f);
        p.displayClientMessage(Component.literal("The " + STRAINS[strain(p)] + " strain AWAKENS - " + ARTS[strain(p)] + " and the Final Form are on your wheel")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
    }

    static void chooseStrain(ServerPlayer p, int s) {
        CompoundTag r = root(p);
        if (s < HUNTER || s > HIVE || r.getInt("strain") != 0 || r.getInt("spent") < STRAIN_AT) return;
        r.putInt("strain", s);
        if (awakened(p)) AstaFx.later(20, () -> awaken(p));
        sound(p.serverLevel(), p.position(), SoundEvents.RAVAGER_ROAR, 0.7f, 0.8f);
        FxSend.ring(p.serverLevel(), RED | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.4f, 3.0f, 0.1f, 12);
        p.displayClientMessage(Component.literal("The strain settles: " + STRAINS[s]).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
        applyStats(p);
        sync(p, false);
    }

    // ------------------------------------------------------------------ the awakened strain's signature move
    static int left(ServerPlayer p, String key) { return (int) Math.max(0, (root(p).getLong(key) - p.serverLevel().getGameTime()) / 20); }

    public static void strainArt(ServerPlayer p) {
        if (!awakened(p)) { p.displayClientMessage(Component.literal("Evolve every node to awaken your strain").withStyle(ChatFormatting.GRAY), true); return; }
        CompoundTag r = root(p);
        long now = p.serverLevel().getGameTime();
        if (now < r.getLong("artCd")) { p.displayClientMessage(Component.literal(ARTS[strain(p)] + " is ready in " + left(p, "artCd") + " s").withStyle(ChatFormatting.GRAY), true); return; }
        r.putLong("artCd", now + ART_CD);
        ServerLevel sl = p.serverLevel();
        switch (strain(p)) {
            case HUNTER -> apexChain(p);
            case BRUTE -> {   // Colossus: 15 s bigger, harder-hitting, armoured, unstoppable
                r.putLong("colossus", now + 300);
                FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, p.position().add(0, 0.1, 0), 0.5f, 4.0f, 0.15f, 12);
                FxSend.shake(sl, p.position(), 0.6f, 10, 14);
                sound(sl, p.position(), SoundEvents.RAVAGER_ROAR, 1.0f, 0.5f);
            }
            case HIVE -> {   // Swarm: 20 s of parasite motes darting at every foe round you
                r.putLong("swarm", now + 400);
                sound(sl, p.position(), SoundEvents.BEEHIVE_EXIT, 1.0f, 0.5f);
            }
            default -> { }
        }
        applyStats(p);
        dirty(p);
    }

    /** Apex Chain: you flash from foe to foe - up to five within 12 blocks - cutting each as you pass. */
    static void apexChain(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        List<LivingEntity> prey = new ArrayList<>(Kit.near(sl, p, p.position().add(0, 1, 0), 12));
        prey.removeIf(t -> !foe(p, t));
        prey.sort(Comparator.comparingDouble(t -> t.distanceToSqr(p)));
        if (prey.size() > 5) prey = new ArrayList<>(prey.subList(0, 5));
        if (prey.isEmpty()) { p.displayClientMessage(Component.literal("No prey in reach").withStyle(ChatFormatting.GRAY), true); root(p).putLong("artCd", 0); return; }
        float dmg = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5f + 6f;
        sound(sl, p.position(), SoundEvents.PHANTOM_SWOOP, 1.0f, 1.2f);
        for (int k = 0; k < prey.size(); k++) {
            final LivingEntity t = prey.get(k);
            AstaFx.later(k * 4, () -> {
                if (!alive(p) || !t.isAlive()) return;
                Vec3 from = p.position();
                Vec3 side = t.position().subtract(from).multiply(1, 0, 1);
                side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
                Vec3 to = t.position().add(side.scale(t.getBbWidth() * 0.5 + 0.8));
                FxSend.slash(sl, RED, from.add(0, 1, 0).add(to.subtract(from).scale(0.5)), side, new Vec3(0, 1, 0), 0.4f, (float) from.distanceTo(to) * 0.5f + 0.5f, 5, 0);
                p.teleportTo(to.x, t.getY(), to.z);
                p.fallDistance = 0;
                t.invulnerableTime = 0;
                armHit(p, t, dmg);
                Fx.impact(sl, RED, mid(t), 1.0f);
                sound(sl, to, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
            });
        }
    }

    // ------------------------------------------------------------------ the Final Form
    public static void finalFormStart(ServerPlayer p) {
        if (!awakened(p)) { p.displayClientMessage(Component.literal("The Final Form needs an awakened strain").withStyle(ChatFormatting.GRAY), true); return; }
        CompoundTag r = root(p);
        long now = p.serverLevel().getGameTime();
        if (now < r.getLong("ffCd")) { p.displayClientMessage(Component.literal("The Final Form is ready in " + left(p, "ffCd") + " s").withStyle(ChatFormatting.GRAY), true); return; }
        if (biomass(p) < 100) { p.displayClientMessage(Component.literal("The Final Form needs a full gut (100 biomass)").withStyle(ChatFormatting.RED), true); return; }
        r.putFloat("bio", biomass(p) - 100);
        r.putLong("ffEnd", now + FINAL_LEN);
        r.putLong("ffCd", now + FINAL_CD);
        r.putLong("frenzyEnd", 0); r.putFloat("frenzy", 0);
        if (form(p) == NONE && p.getMainHandItem().isEmpty()) setForm(p, REAPER, true);
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, p.position().add(0, 0.1, 0), 0.5f, 6.0f, 0.2f, 16);
        groundSpines(sl, p.position(), 4f, 18, 16);
        FxSend.shake(sl, p.position(), 0.8f, 14, 20);
        FxSend.impactFrame(sl, p.position(), 4, false, 24);
        sound(sl, p.position(), SoundEvents.RAVAGER_ROAR, 1.2f, 0.4f);
        sound(sl, p.position(), SoundEvents.WARDEN_SONIC_CHARGE, 0.8f, 0.6f);
        p.displayClientMessage(Component.literal("FINAL FORM").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
        applyStats(p);
        dirty(p);
    }

    /** While in the Final Form: spines burst out of you every 2 s. */
    static void finalPulse(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position().add(0, 1.2, 0);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12 + p.tickCount * 0.1;
            spineFly(sl, c, new Vec3(Math.cos(a), 0.1, Math.sin(a)), 5, 1.8f, 0.9f);
        }
        sound(sl, c, SoundEvents.ARROW_SHOOT, 0.8f, 0.5f);
        for (LivingEntity t : Kit.near(sl, p, c, 5)) {
            if (!foe(p, t)) continue;
            t.invulnerableTime = 0;
            armHit(p, t, 4f * spineBoost(p));
        }
    }

    /** The Hive's Swarm: every half second, motes dart at up to four foes within 8 blocks. */
    static void swarmPulse(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position().add(0, 1.4, 0);
        double t0 = p.tickCount * 0.25;
        for (int i = 0; i < 6; i++) {   // the motes circling you
            double a = t0 + i * Math.PI / 3;
            Fx.spark(sl, RED, c.add(Math.cos(a) * 1.6, 0.4 * Math.sin(a * 2), Math.sin(a) * 1.6), 0.25f);
        }
        List<LivingEntity> near = new ArrayList<>(Kit.near(sl, p, c, 8));
        near.removeIf(t -> !foe(p, t));
        near.sort(Comparator.comparingDouble(t -> t.distanceToSqr(p)));
        for (int k = 0; k < Math.min(4, near.size()); k++) {
            LivingEntity t = near.get(k);
            Vec3 d = mid(t).subtract(c);
            spineFly(sl, c, d, d.length(), 1.6f, 0.4f);
            AstaFx.later(Math.max(1, (int) (d.length() / 1.6)), () -> {
                if (!t.isAlive() || !alive(p)) return;
                t.invulnerableTime = 0;
                armHit(p, t, 2f);
            });
        }
    }

    // ------------------------------------------------------------------ Tendril Grapple and Burrow
    /** Z: a tendril to what you aim at - light foes are yanked to you, anything else pulls you to it. */
    public static void grapple(ServerPlayer p) {
        if (rank(p, TENDRIL) <= 0) return;
        CompoundTag r = root(p);
        long now = p.serverLevel().getGameTime();
        if (now < r.getLong("grappleCd") || burrowed(p)) return;
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 24, 6);
        Vec3 eye = p.getEyePosition(), hand = handPos(p);
        if (t != null && foe(p, t) && !isBoss(t) && t.getMaxHealth() < 40) {   // a light foe: yanked in
            Vec3 d = p.position().subtract(t.position());
            Kit.push(t, d, Math.min(2.0, d.length() * 0.25), 0.3);
            tendril(sl, hand, mid(t), 8);
            sound(sl, t.position(), SoundEvents.FISHING_BOBBER_RETRIEVE, 1.0f, 0.6f);
        } else {   // anything else: you're pulled to it
            Vec3 to = t != null ? mid(t) : Kit.aim(p, 24);
            if (to.distanceTo(eye) > 23.5 && t == null) { p.displayClientMessage(Component.literal("Nothing in reach").withStyle(ChatFormatting.GRAY), true); return; }
            Vec3 d = to.subtract(p.position());
            p.setDeltaMovement(d.normalize().scale(Math.min(2.2, d.length() * 0.22)).add(0, 0.35, 0));
            p.hurtMarked = true;
            r.putLong("glide", now + 60);   // no fall damage from the swing
            tendril(sl, hand, to, 8);
            sound(sl, p.position(), SoundEvents.FISHING_BOBBER_THROW, 1.0f, 0.5f);
        }
        r.putLong("grappleCd", now + 30);
    }

    /** Sneak + Z: sink out of sight for 4 s (hidden, faster, foes lose you), then burst up in spines. */
    public static void burrow(ServerPlayer p) {
        if (rank(p, BURROW) <= 0) return;
        CompoundTag r = root(p);
        long now = p.serverLevel().getGameTime();
        if (burrowed(p)) { erupt(p); return; }   // pressed again: up now
        if (now < r.getLong("burrowCd") || !p.onGround()) return;
        r.putLong("burrow", now + 80);
        r.putLong("burrowCd", now + 200);
        ServerLevel sl = p.serverLevel();
        groundSpines(sl, p.position(), 1.2f, 6, 8);
        sound(sl, p.position(), SoundEvents.ROOTED_DIRT_BREAK, 1.0f, 0.6f);
        sound(sl, p.position(), SoundEvents.SLIME_SQUISH, 0.8f, 0.5f);
        for (net.minecraft.world.entity.Mob m : sl.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(32), m -> m.getTarget() == p)) m.setTarget(null);
        applyStats(p);
        dirty(p);
    }

    static void erupt(ServerPlayer p) {
        CompoundTag r = root(p);
        r.putLong("burrow", 0);
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        groundSpines(sl, c, 3.0f, 16, 12);
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, c.add(0, 0.1, 0), 0.3f, 3.5f, 0.15f, 10);
        FxSend.shake(sl, c, 0.5f, 6, 12);
        sound(sl, c, SoundEvents.EVOKER_FANGS_ATTACK, 1.2f, 0.6f);
        for (LivingEntity t : Kit.near(sl, p, c.add(0, 0.5, 0), 3.5)) {
            if (!foe(p, t)) continue;
            t.invulnerableTime = 0;
            armHit(p, t, 8f * spineBoost(p));
            spineLanded(p, t);
            if (!isBoss(t)) { t.setDeltaMovement(t.getDeltaMovement().add(0, 0.7, 0)); t.hurtMarked = true; }
        }
        applyStats(p);
        dirty(p);
    }

    /** Foes can't take aim at someone burrowed. */
    @SubscribeEvent
    public static void onTarget(net.minecraftforge.event.entity.living.LivingChangeTargetEvent e) {
        if (e.getNewTarget() instanceof ServerPlayer p && burrowed(p)) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ the second strain and organs
    static void secondStrain(ServerPlayer p, int s) {
        CompoundTag r = root(p);
        if (!awakened(p) || r.getInt("strain2") != 0 || s < HUNTER || s > HIVE || s == strain(p)) return;
        r.putInt("strain2", s);
        FxSend.ring(p.serverLevel(), RED | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.4f, 3.0f, 0.1f, 12);
        sound(p.serverLevel(), p.position(), SoundEvents.RAVAGER_ROAR, 0.7f, 1.0f);
        p.displayClientMessage(Component.literal("Grafted: the " + STRAINS[s] + " strain, at half strength").withStyle(ChatFormatting.RED), false);
        applyStats(p);
        sync(p, false);
    }

    /** Grow an organ in a slot (4 EP the first time a slot is grown; swapping after that is free). */
    static void growOrgan(ServerPlayer p, int slot, int choice) {
        if (slot < 0 || slot > 2 || choice < 1 || choice > 3) return;
        CompoundTag r = root(p);
        int[] o = r.getIntArray("organs");
        if (o.length < 3) o = new int[3];
        if (o[slot] == choice) return;
        if (o[slot] == 0) {
            if (r.getInt("ep") < ORGAN_COST) { p.displayClientMessage(Component.literal("Growing an organ takes " + ORGAN_COST + " evolution points").withStyle(ChatFormatting.RED), true); return; }
            r.putInt("ep", r.getInt("ep") - ORGAN_COST);
        }
        o[slot] = choice;
        r.putIntArray("organs", o);
        sound(p.serverLevel(), p.position(), SoundEvents.SLIME_SQUISH, 1.0f, 0.6f);
        sound(p.serverLevel(), p.position(), SoundEvents.WARDEN_HEARTBEAT, 1.0f, 1.0f);
        applyStats(p);
        sync(p, false);
    }

    // ------------------------------------------------------------------ Collapse Core
    /** A dense sphere of biomass where you aim (20 blocks): for 3 s it drags every foe within 9 blocks into its middle,
     *  tearing at them; then it bursts in tendrils. Bosses are slowed, not pulled. */
    public static void collapseCore(ServerPlayer p, float power) {
        if (!spend(p, ultCost(p, 3))) { noBiomass(p, 3); return; }
        ServerLevel sl = p.serverLevel();
        LivingEntity aimedAt = aimed(p, 20, 10);
        Vec3 c = (aimedAt != null && foe(p, aimedAt) ? mid(aimedAt) : Kit.aim(p, 20)).add(0, 0.6, 0);
        float dmg = power * ultBoost(p);
        sound(sl, c, SoundEvents.WARDEN_SONIC_CHARGE, 1.0f, 0.5f);
        for (int k = 0; k < 12; k++) {
            final int tick = k;
            AstaFx.later(k * 5, () -> {
                if (!alive(p)) return;
                Kit.orb(sl, RED, c, Vec3.ZERO, 0.9f + 0.05f * tick, 6);   // the core, swelling
                FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, c, 9f - tick * 0.7f, 9.2f - tick * 0.7f, 0.06f, 6);   // the pull, closing in
                if (tick % 2 == 0) sound(sl, c, SoundEvents.SLIME_SQUISH, 0.7f, 0.4f + tick * 0.05f);
                for (LivingEntity t : Kit.near(sl, p, c, 9)) {
                    if (!foe(p, t)) continue;
                    Vec3 to = c.subtract(mid(t));
                    if (isBoss(t)) TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, ROOTED, "Pulled", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL, 8);
                    else if (to.length() > 0.8) { t.setDeltaMovement(to.normalize().scale(Math.min(0.9, 0.25 + to.length() * 0.08))); t.hurtMarked = true; }
                    if (tick % 3 == 0) { t.invulnerableTime = 0; AstaFx.damage(p, t, dmg * 0.25f, "collapse_core"); tendril(sl, c, mid(t), 4); }
                }
            });
        }
        AstaFx.later(62, () -> {   // the burst
            if (!alive(p)) return;
            FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, c, 0.5f, 7f, 0.2f, 12);
            FxSend.shake(sl, c, 0.8f, 10, 20);
            FxSend.impactFrame(sl, c, 3, false, 24);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 1.0f, 0.6f);
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (int i = 0; i < 18; i++) {
                Vec3 d = new Vec3(rnd.nextDouble(-1, 1), rnd.nextDouble(-0.3, 0.8), rnd.nextDouble(-1, 1)).normalize();
                tendril(sl, c, c.add(d.scale(rnd.nextDouble(3, 6.5))), 10);
            }
            for (LivingEntity t : Kit.near(sl, p, c, 6.5)) {
                if (!foe(p, t)) continue;
                t.invulnerableTime = 0;
                AstaFx.damage(p, t, dmg * 2.2f, "collapse_core");
                if (!isBoss(t)) Kit.push(t, mid(t).subtract(c), 1.2, 0.5);
            }
        });
    }

    // ------------------------------------------------------------------ Pack Call
    /** Three infected brutes burst up beside you, shaped by your Strain, to fight for 30 s. */
    public static void packCall(ServerPlayer p, float power) {
        if (!spend(p, ULT_COST[4])) { p.displayClientMessage(Component.literal("Needs " + ULT_COST[4] + " biomass").withStyle(ChatFormatting.RED), true); return; }
        ServerLevel sl = p.serverLevel();
        for (com.ironspellgrimoire.entity.InfectedBrute old : sl.getEntitiesOfClass(com.ironspellgrimoire.entity.InfectedBrute.class, p.getBoundingBox().inflate(64), b -> p.getUUID().equals(b.owner()))) old.life = 1;
        for (int k = 0; k < 3; k++) {
            double a = Math.toRadians(p.getYRot() + 90) + (k - 1) * 0.9;
            Vec3 at = p.position().add(-Math.sin(a + Math.PI / 2) * 2.2, 0, Math.cos(a + Math.PI / 2) * 2.2);
            at = ground(sl, p, at.add(0, 1.5, 0));
            com.ironspellgrimoire.entity.InfectedBrute b = com.ironspellgrimoire.registry.AddonEntities.INFECTED_BRUTE.get().create(sl);
            if (b == null) continue;
            b.moveTo(at.x, at.y, at.z, p.getYRot(), 0);
            b.setOwner(p.getUUID());
            b.setKind(strain(p));
            sl.addFreshEntity(b);
            groundSpines(sl, at, 1.2f, 6, 10);
        }
        sound(sl, p.position(), SoundEvents.RAVAGER_ROAR, 0.8f, 1.2f);
        sound(sl, p.position(), SoundEvents.ROOTED_DIRT_BREAK, 1.0f, 0.5f);
    }

    /** Every tick, for each brute: wither when its time's up or its master is gone; follow its master; go for what
     *  its master fights; the Hive's spitters shoot spines from range. */
    public static void bruteTick(com.ironspellgrimoire.entity.InfectedBrute b) {
        if (!(b.level() instanceof ServerLevel sl)) return;
        ServerPlayer o = b.getOwnerEntity();
        if (o == null || --b.life <= 0 || o.level() != b.level()) {
            groundSpines(sl, b.position(), 0.8f, 4, 8);
            sound(sl, b.position(), SoundEvents.SLIME_SQUISH, 0.8f, 0.5f);
            b.discard();
            return;
        }
        LivingEntity t = b.getTarget();
        if (t != null && (!t.isAlive() || !b.canAttack(t))) { b.setTarget(null); t = null; }
        if (b.tickCount % 10 == 0) {   // what its master fights, or what fights its master
            LivingEntity want = o.getLastHurtMob() != null && o.getLastHurtMob().isAlive() && o.tickCount - o.getLastHurtMobTimestamp() < 200 ? o.getLastHurtMob() : o.getLastHurtByMob();
            if (want != null && want.isAlive() && b.canAttack(want) && want.distanceTo(b) < 24) b.setTarget(want);
            else if (t == null) {
                List<net.minecraft.world.entity.Mob> near = sl.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, b.getBoundingBox().inflate(12),
                        m -> m instanceof net.minecraft.world.entity.monster.Enemy && m.isAlive() && b.canAttack(m));
                near.sort(Comparator.comparingDouble(m -> m.distanceToSqr(b)));
                if (!near.isEmpty()) b.setTarget(near.get(0));
            }
        }
        t = b.getTarget();
        if (t == null) {   // follow its master
            double d = b.distanceTo(o);
            if (d > 24) b.teleportTo(o.getX(), o.getY(), o.getZ());
            else if (d > 5) b.getNavigation().moveTo(o, 1.2);
        } else if (b.kind() == 3) {   // the spitter keeps its distance and shoots
            double d = b.distanceTo(t);
            if (d > 12) b.getNavigation().moveTo(t, 1.0); else b.getNavigation().stop();
            b.getLookControl().setLookAt(t);
            if (--b.spitCd <= 0 && d < 14 && b.hasLineOfSight(t)) {
                b.spitCd = 25;
                Vec3 from = b.getEyePosition(), to = mid(t);
                spineFly(sl, from, to.subtract(from), from.distanceTo(to), 1.6f, 0.6f);
                sound(sl, from, SoundEvents.LLAMA_SPIT, 0.8f, 0.6f);
                final LivingEntity tt = t;
                AstaFx.later(Math.max(1, (int) (from.distanceTo(to) / 1.6)), () -> {
                    if (!tt.isAlive() || !b.isAlive()) return;
                    tt.invulnerableTime = 0;
                    tt.hurt(b.damageSources().mobAttack(b), 4f);
                });
            }
        }
    }

    // ------------------------------------------------------------------ Air Dash
    /** A dash in mid-air (the client moved; this charges it and keeps the fall harmless). */
    static void airDash(ServerPlayer p) {
        if (p.onGround()) return;
        CompoundTag r = root(p);
        r.putFloat("bio", Math.max(0, r.getFloat("bio") - 2f));
        r.putLong("glide", p.serverLevel().getGameTime() + 40);
        FxSend.ring(p.serverLevel(), RED | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.2f, 1.2f, 0.06f, 6);
        sound(p.serverLevel(), p.position(), SoundEvents.PHANTOM_FLAP, 0.7f, 1.3f);
        dirty(p);
    }

    // ------------------------------------------------------------------ Predator Sight and Molting
    static void toggleSight(ServerPlayer p) {
        if (rank(p, SIGHT) <= 0) { p.displayClientMessage(Component.literal("Evolve Predator Sight first").withStyle(ChatFormatting.GRAY), true); return; }
        CompoundTag r = root(p);
        boolean on = !r.getBoolean("sight");
        if (on && r.getFloat("bio") < 1) { p.displayClientMessage(Component.literal("No biomass to see with").withStyle(ChatFormatting.GRAY), true); return; }
        r.putBoolean("sight", on);
        sound(p.serverLevel(), p.position(), on ? SoundEvents.WARDEN_HEARTBEAT : SoundEvents.SLIME_SQUISH_SMALL, 0.6f, on ? 1.4f : 0.8f);
        dirty(p);
    }

    private static final UUID MOLT_SPEED = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900012");

    /** After a blow lands: under 30% health, shed a husk - mobs turn on it while you get away. 2 min. */
    static void molt(ServerPlayer p) {
        if (!p.isAlive() || p.getHealth() >= p.getMaxHealth() * 0.3f) return;
        CompoundTag r = root(p);
        long now = p.serverLevel().getGameTime();
        if (now < r.getLong("moltCd")) return;
        r.putLong("moltCd", now + 2400);
        ServerLevel sl = p.serverLevel();
        com.ironspellgrimoire.entity.MoltHusk h = com.ironspellgrimoire.registry.AddonEntities.MOLT_HUSK.get().create(sl);
        if (h != null) {
            h.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0);
            h.yBodyRot = p.yBodyRot; h.yHeadRot = p.yHeadRot;
            h.setOwner(p.getUUID());
            sl.addFreshEntity(h);
            for (net.minecraft.world.entity.Mob m : sl.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(24), m -> m.getTarget() == p)) m.setTarget(h);
        }
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, MOLT_SPEED, "Molted", 0.4, AttributeModifier.Operation.MULTIPLY_BASE, 60);
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.3f, 2.0f, 0.1f, 10);
        sound(sl, p.position(), SoundEvents.SLIME_SQUISH, 1.2f, 0.4f);
        sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_LEATHER, 1.0f, 0.5f);
        p.displayClientMessage(Component.literal("You molt - the husk draws them off").withStyle(ChatFormatting.DARK_RED), true);
    }

    /** The husk falls apart in a burst of spines. */
    public static void huskCrumbles(com.ironspellgrimoire.entity.MoltHusk h) {
        if (!(h.level() instanceof ServerLevel sl) || h.isRemoved()) return;
        Vec3 c = h.position().add(0, 1, 0);
        ServerPlayer owner = h.owner() == null ? null : (ServerPlayer) sl.getPlayerByUUID(h.owner());
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5;
            spineFly(sl, c, new Vec3(Math.cos(a), 0.2, Math.sin(a)), 4, 1.4f, 0.6f);
        }
        sound(sl, c, SoundEvents.SKELETON_HURT, 1.0f, 0.5f);
        if (owner != null) for (LivingEntity t : Kit.near(sl, owner, c, 3.5)) {
            if (!foe(owner, t) || t == h) continue;
            t.invulnerableTime = 0;
            t.hurt(owner.damageSources().playerAttack(owner), 5f);
        }
        h.discard();
    }

    // ------------------------------------------------------------------ the Hive Nest
    static final int NEST_COST = 50;

    /** Your nests: {id, dim, x, y, z, cd} each (an older save's single nest moved into the list). */
    static net.minecraft.nbt.ListTag nests(Player p) {
        CompoundTag r = root(p);
        if (r.hasUUID("nest") && !r.contains("nests")) {
            CompoundTag e = new CompoundTag();
            e.putUUID("id", r.getUUID("nest")); e.putString("dim", r.getString("nestDim"));
            e.putDouble("x", r.getDouble("nestX")); e.putDouble("y", r.getDouble("nestY")); e.putDouble("z", r.getDouble("nestZ"));
            net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag(); l.add(e); r.put("nests", l);
        }
        r.remove("nest");
        if (!r.contains("nests")) r.put("nests", new net.minecraft.nbt.ListTag());
        return r.getList("nests", 10);
    }

    static boolean ownsNest(Player p, java.util.UUID id) {
        for (net.minecraft.nbt.Tag t : nests(p)) if (((CompoundTag) t).getUUID("id").equals(id)) return true;
        return false;
    }

    /** The nearest of your nests that's loaded where you are. */
    static HiveNest nestOf(ServerPlayer p) {
        HiveNest best = null;
        for (net.minecraft.nbt.Tag t : nests(p)) {
            Entity e = p.serverLevel().getEntity(((CompoundTag) t).getUUID("id"));
            if (e instanceof HiveNest n && n.isAlive() && (best == null || n.distanceToSqr(p) < best.distanceToSqr(p))) best = n;
        }
        return best;
    }

    static int maxNests(Player p) { return rank(p, NETWORK) > 0 ? 3 : 1; }

    /** Grow a nest where you stand (50 biomass): one, or three with Hive Network - past that the oldest withers. */
    public static void growNest(ServerPlayer p) {
        if (!p.onGround()) { p.displayClientMessage(Component.literal("Stand on the ground to grow a nest").withStyle(ChatFormatting.GRAY), true); return; }
        if (!spend(p, NEST_COST)) { p.displayClientMessage(Component.literal("Growing a nest takes " + NEST_COST + " biomass").withStyle(ChatFormatting.RED), true); return; }
        ServerLevel sl = p.serverLevel();
        net.minecraft.nbt.ListTag list = nests(p);
        float kept = 0;
        while (list.size() >= maxNests(p)) {   // the oldest withers (its biomass carried over)
            CompoundTag old = list.getCompound(0);
            list.remove(0);
            Entity e = sl.getEntity(old.getUUID("id"));
            if (e instanceof HiveNest n) { kept += n.stored(); n.discard(); }
        }
        HiveNest n = com.ironspellgrimoire.registry.AddonEntities.HIVE_NEST.get().create(sl);
        if (n == null) return;
        n.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0);
        n.setOwner(p.getUUID());
        n.setStored(kept);
        sl.addFreshEntity(n);
        CompoundTag e = new CompoundTag();
        e.putUUID("id", n.getUUID()); e.putString("dim", sl.dimension().location().toString());
        e.putDouble("x", n.getX()); e.putDouble("y", n.getY()); e.putDouble("z", n.getZ());
        list.add(e);
        root(p).put("nests", list);
        groundSpines(sl, n.position(), 2.0f, 12, 12);
        sound(sl, n.position(), SoundEvents.SLIME_SQUISH, 1.2f, 0.4f);
        sound(sl, n.position(), SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.8f);
        p.displayClientMessage(Component.literal("A Hive Nest takes root (" + list.size() + "/" + maxNests(p) + ")").withStyle(ChatFormatting.RED), true);
        dirty(p);
    }

    /** Hive Network: from beside one of your nests, sink and burst out at another (5 min a destination). */
    static void nestTravel(ServerPlayer p, int index) {
        net.minecraft.nbt.ListTag list = nests(p);
        if (rank(p, NETWORK) <= 0 || index < 0 || index >= list.size()) return;
        HiveNest here = nestOf(p);
        if (here == null || here.distanceTo(p) > 4) { p.displayClientMessage(Component.literal("Stand by one of your nests").withStyle(ChatFormatting.GRAY), true); return; }
        CompoundTag to = list.getCompound(index);
        if (to.getUUID("id").equals(here.getUUID())) return;
        long now = p.serverLevel().getGameTime();
        if (now < to.getLong("cd")) { p.displayClientMessage(Component.literal("That nest is still knitting: " + (to.getLong("cd") - now) / 20 + " s").withStyle(ChatFormatting.GRAY), true); return; }
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new net.minecraft.resources.ResourceLocation(to.getString("dim")));
        ServerLevel there = p.server.getLevel(key);
        if (there == null) return;
        to.putLong("cd", now + 6000);
        root(p).put("nests", list);
        ServerLevel sl = p.serverLevel();
        groundSpines(sl, p.position(), 1.5f, 10, 10);
        sound(sl, p.position(), SoundEvents.ROOTED_DIRT_BREAK, 1.0f, 0.5f);
        p.teleportTo(there, to.getDouble("x"), to.getDouble("y") + 0.3, to.getDouble("z"), p.getYRot(), p.getXRot());
        groundSpines(there, p.position(), 2.0f, 14, 12);
        sound(there, p.position(), SoundEvents.EVOKER_FANGS_ATTACK, 1.0f, 0.6f);
        root(p).putLong("glide", there.getGameTime() + 40);
        dirty(p);
    }

    /** Every 2 s: a nest that's no longer its owner's withers; its owner, near it, is healed. */
    public static void nestTick(HiveNest n) {
        if (!(n.level() instanceof ServerLevel sl)) return;
        if (n.owner() == null) { n.discard(); return; }
        if (!(sl.getPlayerByUUID(n.owner()) instanceof ServerPlayer p)) return;
        CompoundTag r = root(p);
        if (!ownsNest(p, n.getUUID())) { n.discard(); return; }
        if (p.distanceTo(n) < 6 && p.getHealth() < p.getMaxHealth()) {
            p.heal(1f);
            tendril(sl, n.position().add(0, 0.6, 0), mid(p), 6);
        }
    }

    /** Its owner takes the stored biomass back (up to a full gut). */
    public static void nestUsed(ServerPlayer p, HiveNest n) {
        if (!p.getUUID().equals(n.owner())) { p.displayClientMessage(Component.literal("Someone else's nest").withStyle(ChatFormatting.GRAY), true); return; }
        CompoundTag r = root(p);
        float room = cap(p) - r.getFloat("bio"), take = Math.min(room, n.stored());
        if (rank(p, NETWORK) > 0 && nests(p).size() > 1 && p.isShiftKeyDown()) { sendNests(p); return; }   // sneak + right-click: travel
        if (take <= 0) { p.displayClientMessage(Component.literal("Nest: " + Math.round(n.stored()) + " biomass stored" + (rank(p, NETWORK) > 0 && nests(p).size() > 1 ? "  (sneak + right-click to travel)" : "")).withStyle(ChatFormatting.RED), true); return; }
        n.setStored(n.stored() - take);
        r.putFloat("bio", r.getFloat("bio") + take);
        tendril(p.serverLevel(), n.position().add(0, 0.6, 0), mid(p), 8);
        sound(p.serverLevel(), n.position(), SoundEvents.GENERIC_DRINK, 1.0f, 0.6f);
        p.displayClientMessage(Component.literal("+" + Math.round(take) + " biomass from your nest (" + Math.round(n.stored()) + " left)").withStyle(ChatFormatting.RED), true);
        dirty(p);
    }

    /** The travel menu: every nest (where, its biomass if loaded, how long till it can be reached). */
    static void sendNests(ServerPlayer p) {
        net.minecraft.nbt.ListTag list = nests(p);
        long now = p.serverLevel().getGameTime();
        HiveNest here = nestOf(p);
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            String dim = e.getString("dim"); dim = dim.substring(dim.indexOf(':') + 1).replace('_', ' ');
            boolean isHere = here != null && e.getUUID("id").equals(here.getUUID());
            long cd = Math.max(0, (e.getLong("cd") - now) / 20);
            rows.add(dim + "|" + (int) e.getDouble("x") + "|" + (int) e.getDouble("y") + "|" + (int) e.getDouble("z") + "|" + cd + "|" + (isHere ? 1 : 0));
        }
        AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ParasitePackets.Nests(rows));
    }

    /** Once a day, death brings you back at your nest. */
    @SubscribeEvent
    public static void onRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent e) {
        if (e.isEndConquered() || !(e.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag r = root(p);
        net.minecraft.nbt.ListTag list = nests(p);
        if (list.isEmpty()) return;
        long now = p.serverLevel().getGameTime();
        if (now < r.getLong("nestRespawn")) return;
        String died = r.getString("diedDim");
        CompoundTag pick = list.getCompound(0);
        double best = Double.MAX_VALUE;
        for (net.minecraft.nbt.Tag t : list) {
            CompoundTag c = (CompoundTag) t;
            if (!c.getString("dim").equals(died)) continue;
            double d = Math.pow(c.getDouble("x") - r.getDouble("diedX"), 2) + Math.pow(c.getDouble("z") - r.getDouble("diedZ"), 2);
            if (d < best) { best = d; pick = c; }
        }
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new net.minecraft.resources.ResourceLocation(pick.getString("dim")));
        ServerLevel there = p.server.getLevel(key);
        if (there == null) return;
        r.putLong("nestRespawn", now + 24000);
        p.teleportTo(there, pick.getDouble("x"), pick.getDouble("y") + 0.2, pick.getDouble("z"), p.getYRot(), p.getXRot());
        p.displayClientMessage(Component.literal("You re-form at your Hive Nest").withStyle(ChatFormatting.RED), true);
    }

    /** Where you died (for the nearest nest). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDiedWhere(LivingDeathEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag r = root(p);
        r.putString("diedDim", p.serverLevel().dimension().location().toString());
        r.putDouble("diedX", p.getX()); r.putDouble("diedZ", p.getZ());
    }

    // ------------------------------------------------------------------ the shadow wheel (System Grimoire)
    static void shadowCommand(ServerPlayer p, int which) {
        var b = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
        if (b == null || !b.is(AddonItemRegistry.SHADOW_GRIMOIRE.get())) return;
        ServerLevel sl = p.serverLevel();
        List<ShadowSoldier> out = ShadowArmy.out(p);
        switch (which) {
            case 0 -> {   // Hunt: every shadow at your side goes for what you look at
                LivingEntity t = aimed(p, 28, 12);
                if (t == null || !foe(p, t)) { p.displayClientMessage(Component.literal("Look at what they should hunt").withStyle(ChatFormatting.GRAY), true); return; }
                for (ShadowSoldier s : out) { s.setTarget(t); s.hunting = 160; }
                FxSend.ring(sl, Fx.YAMI | FxPacket.KEEP_SHAPE, mid(t), 0.2f, 1.2f, 0.06f, 10);
                p.displayClientMessage(Component.literal("Hunt").withStyle(ChatFormatting.AQUA), true);
            }
            case 1 -> {   // Rally: they come back to you at once
                int k = 0;
                for (ShadowSoldier s : out) {
                    double a = k++ * Math.PI * 2 / Math.max(1, out.size());
                    Vec3 to = p.position().add(Math.cos(a) * 2.2, 0, Math.sin(a) * 2.2);
                    s.setTarget(null); s.hunting = 0;
                    s.teleportTo(to.x, to.y, to.z);
                    FxSend.ring(sl, Fx.YAMI | FxPacket.KEEP_SHAPE, to.add(0, 0.05, 0), 0.2f, 0.9f, 0.05f, 10);
                }
                p.displayClientMessage(Component.literal("Rally").withStyle(ChatFormatting.AQUA), true);
            }
            case 2 -> {   // Stand down: forget their targets
                for (ShadowSoldier s : out) { s.setTarget(null); s.hunting = 0; }
                p.displayClientMessage(Component.literal("Stand down").withStyle(ChatFormatting.AQUA), true);
            }
            case 3 -> ShadowFx.gateOfShade(p, 0);   // sink into your shadow / rise again
            case 4 -> ShadowArmy.open(p);
            case 5 -> HunterAscension.openStatus(p, 0);
            case 6 -> HunterAscension.openMarket(p, 0);
            default -> { }
        }
    }

    // ================================================================== the events
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        CompoundTag r = root(p);
        long now = p.serverLevel().getGameTime();
        boolean on = equipped(p);
        if (!on) {
            if (r.getInt("form") != NONE || r.getBoolean("bulwark") || r.getBoolean("hide") || r.getBoolean("wasOn")) {
                retractAll(p);
                r.putBoolean("wasOn", false);
                sync(p, false);
            }
            return;
        }
        if (!r.getBoolean("wasOn")) { r.putBoolean("wasOn", true); applyStats(p); dirty(p); }
        // something picked up into a changed hand ends that form
        if (r.getInt("form") != NONE && !p.getMainHandItem().isEmpty()) { r.putInt("form", NONE); r.remove("stash"); applyStats(p); dirty(p); }
        if (r.getBoolean("bulwark") && !p.getOffhandItem().isEmpty()) { r.putBoolean("bulwark", false); r.remove("stashOff"); applyStats(p); dirty(p); }
        // Meteor Fall: landing
        if (r.contains("slam")) {
            if (p.onGround() || p.isInWater()) slamLand(p);
            else if (now > r.getLong("slam")) { r.remove("slam"); r.remove("slamY"); }
        }
        if (finalForm(p)) {
            r.putBoolean("ffOn", true);
            if (now % 40 == 0) finalPulse(p);
        } else if (r.getBoolean("ffOn")) {
            r.putBoolean("ffOn", false);
            p.displayClientMessage(Component.literal("The Final Form sloughs away").withStyle(ChatFormatting.GRAY), true);
            applyStats(p); dirty(p);
        }
        if (swarm(p) && now % 10 == 0) swarmPulse(p);
        if (r.contains("regrow") && !limbGone(p)) regrow(p);
        if (now % 100 == 0 && r.getFloat("limb") > 0) { r.putFloat("limb", Math.max(0, r.getFloat("limb") - 2f)); dirty(p); }   // wear mends slowly
        if (r.getLong("burrow") != 0) {
            if (!burrowed(p)) erupt(p);
            else if (now % 3 == 0) {   // the mound of flesh moving under the ground
                FxSend.ring(p.serverLevel(), RED | FxPacket.KEEP_SHAPE, p.position().add(0, 0.05, 0), 0.1f, 0.9f, 0.25f, 4);
                if (now % 9 == 0) groundSpines(p.serverLevel(), p.position(), 0.6f, 2, 6);
            }
        }
        if (r.getInt("form") == RAM && p.isSprinting() && now >= r.getLong("ramCd")) {   // the Battering Ram: run into them
            Vec3 ahead = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.2)).add(0, 1, 0);
            boolean hit = false;
            for (LivingEntity t : Kit.near(p.serverLevel(), p, ahead, 1.4)) {
                if (!foe(p, t)) continue;
                t.invulnerableTime = 0;
                armHit(p, t, 8f * (1f + 0.15f * rank(p, HONED)));
                Kit.push(t, p.getLookAngle(), 1.4, 0.4);
                hit = true;
            }
            if (hit) {
                r.putLong("ramCd", now + 20);
                FxSend.ring(p.serverLevel(), RED | FxPacket.KEEP_SHAPE, ahead, 0.2f, 1.8f, 0.1f, 8);
                FxSend.shake(p.serverLevel(), ahead, 0.4f, 5, 8);
                sound(p.serverLevel(), ahead, SoundEvents.ANVIL_LAND, 0.6f, 0.7f);
            }
        }
        if (now % 60 == 0 && wears(p, REGROW) && r.getFloat("bio") > 25 && p.getHealth() < p.getMaxHealth()) {   // Regrowth
            p.heal(1f);
            r.putFloat("bio", r.getFloat("bio") - 0.5f);
            dirty(p);
        }
        if (now % 20 == 0) {
            // Hunger: the strain burns biomass. Well fed it grows stronger by the minute; empty for 30 s, it starves.
            int gut = organ(p, GUT);
            float bio = Math.max(0, r.getFloat("bio") - 0.2f * (gut == 1 ? 2f : gut == 2 ? 0.5f : 1f));
            r.putFloat("bio", bio);
            if (bio >= 60) r.putInt("sated", Math.min(300, r.getInt("sated") + 1));
            else r.putInt("sated", Math.max(0, r.getInt("sated") - 3));
            if (bio <= 0.01f) r.putInt("empty", r.getInt("empty") + 1); else r.putInt("empty", 0);
            if (starving(p) && now % 100 == 0 && p.getHealth() > 2) p.hurt(p.damageSources().starve(), 1f);
            if (r.getInt("empty") == 30) p.displayClientMessage(Component.literal("The strain is STARVING - feed it").withStyle(ChatFormatting.DARK_RED), true);
            dirty(p);
            if (now % 600 == 0 && r.contains("adapt")) {   // what the strain hasn't met in a while, it slowly forgets
                CompoundTag a = r.getCompound("adapt");
                for (int h = 0; h < HARMS.length; h++)
                    if (now - a.getLong("t" + h) > 600 && a.getFloat("r" + h) > 0) a.putFloat("r" + h, Math.max(0, a.getFloat("r" + h) - 2f));
                r.put("adapt", a);
            }
            if (r.getBoolean("sight")) {   // Predator Sight eats biomass
                float b = r.getFloat("bio") - 0.5f;
                if (b <= 0) { r.putFloat("bio", 0); r.putBoolean("sight", false); p.displayClientMessage(Component.literal("Your sight dims - no biomass left").withStyle(ChatFormatting.GRAY), true); }
                else r.putFloat("bio", b);
                dirty(p);
            }
            if (r.getBoolean("hide")) {   // the hide eats biomass
                float b = r.getFloat("bio") - 0.25f;
                if (b <= 0) { r.putFloat("bio", 0); r.putBoolean("hide", false); applyStats(p); p.displayClientMessage(Component.literal("Your hide sloughs off - no biomass left").withStyle(ChatFormatting.GRAY), true); }
                else r.putFloat("bio", b);
                dirty(p);
            }
            if (frenzied(p)) {
                if (p.getHealth() > 2) p.hurt(p.damageSources().magic(), 1f);
                FxSend.ring(p.serverLevel(), RED | FxPacket.KEEP_SHAPE, p.position().add(0, 0.1, 0), 0.3f, 1.1f, 0.05f, 10);
                dirty(p);
            } else if (r.getLong("frenzyEnd") != 0) {
                frenzyEnd(p);
            } else if (r.getFloat("frenzy") > 0 && r.getFloat("bio") < 100) {
                r.putFloat("frenzy", Math.max(0, r.getFloat("frenzy") - 1.5f));
                dirty(p);
            }
            applyStats(p);
        }
        if (Boolean.TRUE.equals(DIRTY.remove(p.getUUID())) || now % 40 == 0) sync(p, false);
    }

    /** Arm-form blows, the Bulwark, and the forms' side effects. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent e) {
        DamageSource src = e.getSource();
        LivingEntity t = e.getEntity();
        if (t instanceof ServerPlayer ad && equipped(ad) && rank(ad, ADAPT) > 0) adapt(ad, e);
        // the Bulwark (and the frenzy's own bite is not blocked)
        if (t instanceof ServerPlayer pa && equipped(pa) && wears(pa, ACID) && src.getDirectEntity() instanceof LivingEntity a && a != pa && a.distanceTo(pa) < 4) {   // Acid Blood
            AstaFx.later(1, () -> { if (a.isAlive()) { a.invulnerableTime = 0; a.hurt(pa.damageSources().magic(), 3f); FxSend.ring(pa.serverLevel(), Fx.LIGHTNING | FxPacket.KEEP_SHAPE, a.position().add(0, a.getBbHeight() * 0.5, 0), 0.1f, 0.7f, 0.04f, 6); } });
        }
        if (t instanceof ServerPlayer ps && equipped(ps) && hide(ps) && look(ps) == 1 && src.getDirectEntity() instanceof LivingEntity a && a != ps && a.distanceTo(ps) < 4) {   // Spiked
            AstaFx.later(1, () -> { if (a.isAlive()) { a.invulnerableTime = 0; a.hurt(ps.damageSources().thorns(ps), 2f); } });
        }
        if (t instanceof ServerPlayer pm && equipped(pm) && rank(pm, MOLT) > 0) AstaFx.later(0, () -> molt(pm));
        if (t instanceof ServerPlayer p && equipped(p) && (bulwark(p) || form(p) == RAM) && !src.is(DamageTypeTags.BYPASSES_ARMOR) && src.getEntity() != null) {
            Vec3 from = src.getSourcePosition() != null ? src.getSourcePosition() : src.getEntity().position();
            boolean front = fromFront(p, from);
            float before = e.getAmount();
            e.setAmount(before * bulwarkFactor(p, from));
            wear(p, before - e.getAmount());
            if (front) {
                FxSend.ring(p.serverLevel(), RED | FxPacket.KEEP_SHAPE, p.getEyePosition().add(p.getLookAngle().scale(0.6)).add(0, -0.4, 0), 0.1f, 0.8f, 0.05f, 6);
                if (src.getDirectEntity() instanceof LivingEntity a && a != p && a.distanceTo(p) < 4) {   // the spines cut whoever struck it
                    AstaFx.later(1, () -> { if (a.isAlive()) { a.invulnerableTime = 0; a.hurt(p.damageSources().thorns(p), 2f); } });
                }
            }
        }
        // the arm forms (not for their own follow-up cuts - a bleed or a sweep must never set off another)
        if (armFollow || !(src.getEntity() instanceof ServerPlayer p) || src.getDirectEntity() != p || !src.is(DamageTypes.PLAYER_ATTACK)) return;
        if (!equipped(p)) return;
        int f = form(p);
        if (f == NONE) return;
        float mult = 1f + 0.15f * rank(p, HONED);
        if (f == TALONS || f == REAPER) mult *= 1f + 0.25f * sw(p, HUNTER);
        if (f == TITAN || f == LANCE) mult *= 1f + 0.25f * sw(p, BRUTE);
        if (pounced(p)) { mult *= 1.6f; root(p).putLong("pounce", 0); Fx.impact(p.serverLevel(), RED, mid(t), 1.2f); }   // Wall Cling's pounce
        if (organ(p, HEART) == 3) p.heal(1f);   // Leech Heart
        e.setAmount(e.getAmount() * mult);
        float dmg = e.getAmount();
        ServerLevel sl = p.serverLevel();
        feed(p, 1);
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
        final Vec3 dir = look;
        switch (f) {
            case REAPER -> {   // the sweep takes everything in front
                for (LivingEntity o : Kit.near(sl, p, p.position().add(dir.scale(2)).add(0, 1, 0), 3.0)) {
                    if (o == t || !foe(p, o) || o.position().subtract(p.position()).multiply(1, 0, 1).normalize().dot(dir) < 0.2) continue;
                    AstaFx.later(1, () -> { if (o.isAlive()) { o.invulnerableTime = 0; armHit(p, o, dmg * 0.6f); } });
                }
                FxSend.slash(sl, Fx.STEEL, p.getEyePosition().add(dir.scale(1.5)).add(0, -0.4, 0), dir, new Vec3(0, 1, 0), 1.2f, 2.6f, 6, 0);
            }
            case TALONS -> {   // bleeding
                for (int k = 1; k <= 3; k++) {
                    AstaFx.later(k * 20, () -> {
                        if (!t.isAlive() || !alive(p)) return;
                        t.invulnerableTime = 0;
                        armHit(p, t, Math.max(1f, dmg * 0.15f));
                        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, mid(t), 0.15f, 0.6f, 0.03f, 6);
                    });
                }
                FxSend.slash(sl, RED, mid(t), dir, new Vec3(0.3, 1, 0), 0.4f, 1.0f, 5, 0);
            }
            case TITAN -> {   // a shockwave round what it crushes
                Vec3 c = t.position();
                FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, c.add(0, 0.1, 0), 0.3f, 2.5f, 0.1f, 8);
                FxSend.shake(sl, c, 0.3f, 4, 8);
                Kit.push(t, dir, 1.1, 0.35);
                for (LivingEntity o : Kit.near(sl, p, c.add(0, 0.5, 0), 2.5)) {
                    if (o == t || !foe(p, o)) continue;
                    AstaFx.later(1, () -> { if (o.isAlive()) { o.invulnerableTime = 0; armHit(p, o, dmg * 0.4f); Kit.push(o, o.position().subtract(c), 0.6, 0.3); } });
                }
            }
            case WHIP -> {   // the hooks drag it in, and the spine lashes through what's beside it
                if (!isBoss(t)) Kit.push(t, p.position().subtract(t.position()), 0.7, 0.15);
                Vec3 a = p.getEyePosition(), b = mid(t);
                FxSend.spike(sl, BONE, a.add(0, -0.4, 0), b.subtract(a), (float) a.distanceTo(b), 0.1f, 5, 1, FxPacket.EXTEND);
                for (LivingEntity o : Kit.along(sl, p, a, b.add(b.subtract(a).normalize().scale(2)), 1.0)) {
                    if (o == t || !foe(p, o)) continue;
                    AstaFx.later(1, () -> { if (o.isAlive()) { o.invulnerableTime = 0; armHit(p, o, dmg * 0.5f); } });
                }
            }
            case RAM -> {   // it caves in what it hits and hurls it back
                Kit.push(t, dir, 1.5, 0.45);
                FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, mid(t), 0.2f, 1.8f, 0.1f, 8);
                FxSend.shake(sl, t.position(), 0.35f, 5, 8);
            }
            case LANCE -> {   // it runs on through to what stands behind
                Vec3 a = mid(t), b = a.add(dir.scale(3));
                FxSend.spike(sl, BONE, a, dir, 3.0f, 0.12f, 5, 1, FxPacket.EXTEND);
                for (LivingEntity o : Kit.along(sl, p, a, b, 0.8)) {
                    if (o == t || !foe(p, o)) continue;
                    AstaFx.later(1, () -> { if (o.isAlive()) { o.invulnerableTime = 0; armHit(p, o, dmg * 0.5f); } });
                }
            }
            default -> { }
        }
    }

    static boolean fromFront(ServerPlayer p, Vec3 from) {
        Vec3 to = from.subtract(p.position()).multiply(1, 0, 1);
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        return to.lengthSqr() > 1e-4 && look.lengthSqr() > 1e-4 && to.normalize().dot(look.normalize()) > 0.2;
    }

    /** Regenerating Limbs: what the guarding arm soaked up wears it down; worn through, it tears off in shrapnel. */
    static void wear(ServerPlayer p, float soaked) {
        if (soaked <= 0) return;
        CompoundTag r = root(p);
        float w = r.getFloat("limb") + soaked;
        if (w < LIMB_MAX) { r.putFloat("limb", w); dirty(p); return; }
        r.putFloat("limb", 0);
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position().add(0, 1.2, 0);
        boolean shield = r.getBoolean("bulwark");
        int lost = shield ? -1 : form(p);
        if (shield) r.putBoolean("bulwark", false); else r.putInt("form", NONE);
        r.putInt("regrow", lost);
        r.putLong("limbCd", sl.getGameTime() + 100);
        for (int i = 0; i < 14; i++) {   // bone shrapnel
            double a = i * Math.PI * 2 / 14;
            spineFly(sl, c, new Vec3(Math.cos(a), 0.2 + 0.3 * Math.sin(a * 3), Math.sin(a)), 5, 1.6f, 0.6f);
        }
        FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, c, 0.2f, 2.5f, 0.1f, 10);
        sound(sl, c, SoundEvents.SKELETON_HURT, 1.2f, 0.5f);
        sound(sl, c, SoundEvents.SLIME_SQUISH, 1.0f, 0.4f);
        for (LivingEntity t : Kit.near(sl, p, c, 4)) {
            if (!foe(p, t)) continue;
            t.invulnerableTime = 0;
            armHit(p, t, 6f * spineBoost(p));
        }
        p.displayClientMessage(Component.literal("Your arm tears off - it's regrowing").withStyle(ChatFormatting.DARK_RED), true);
        applyStats(p);
        dirty(p);
    }

    /** The torn arm grows back as it was (if that hand is still free). */
    static void regrow(ServerPlayer p) {
        CompoundTag r = root(p);
        int f = r.getInt("regrow");
        r.remove("regrow");
        if (f == -1) { if (p.getOffhandItem().isEmpty()) r.putBoolean("bulwark", true); }
        else if (f > NONE && p.getMainHandItem().isEmpty()) r.putInt("form", f);
        sound(p.serverLevel(), p.position(), SoundEvents.SLIME_SQUISH, 0.9f, 0.6f);
        FxSend.ring(p.serverLevel(), RED | FxPacket.KEEP_SHAPE, handPos(p), 0.1f, 0.8f, 0.06f, 8);
        applyStats(p);
        dirty(p);
    }

    /** Parry Shell: with the Bulwark or the Battering Ram out, use raises a parry for a moment. */
    static void parry(ServerPlayer p) {
        if (!(bulwark(p) || form(p) == RAM) || limbGone(p)) return;
        CompoundTag r = root(p);
        long now = p.serverLevel().getGameTime();
        if (now < r.getLong("parryCd")) return;
        r.putLong("parry", now + 7);
        r.putLong("parryCd", now + 30);
        sound(p.serverLevel(), p.position(), SoundEvents.SHIELD_BLOCK, 0.6f, 1.4f);
    }

    /** A blow (or a shot) into a raised parry: none of it lands, the striker is stunned, the shot flies back. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onParry(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !equipped(p) || !parrying(p)) return;
        DamageSource src = e.getSource();
        if (src.getEntity() == null) return;
        Vec3 from = src.getSourcePosition() != null ? src.getSourcePosition() : src.getEntity().position();
        if (!fromFront(p, from)) return;
        e.setCanceled(true);
        ServerLevel sl = p.serverLevel();
        Vec3 at = p.getEyePosition().add(p.getLookAngle().scale(0.8)).add(0, -0.3, 0);
        FxSend.ring(sl, Fx.SILVER | FxPacket.KEEP_SHAPE, at, 0.2f, 1.4f, 0.08f, 8);
        Fx.flash(sl, at, 1.2f);
        sound(sl, at, SoundEvents.ANVIL_PLACE, 0.6f, 1.6f);
        if (src.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile shot) {   // back the way it came
            AstaFx.later(1, () -> {
                if (!shot.isAlive()) return;
                Vec3 back = src.getEntity().getEyePosition().subtract(shot.position());
                shot.setOwner(p);
                shot.setDeltaMovement(back.normalize().scale(1.8));
                shot.hurtMarked = true;
            });
        }
        if (src.getEntity() instanceof LivingEntity a && a != p && !(a instanceof Player)) {   // stunned: it can't move or hit for 2 s
            TimedModifiers.apply(a, Attributes.MOVEMENT_SPEED, STUN_MOVE, "Stunned", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
            TimedModifiers.apply(a, Attributes.ATTACK_DAMAGE, STUN_HIT, "Stunned", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
            FxSend.ring(sl, Fx.SILVER | FxPacket.KEEP_SHAPE, a.position().add(0, a.getBbHeight() + 0.3, 0), 0.2f, 0.6f, 0.04f, 40);
        }
        root(p).putLong("parry", 0);
    }

    /** What share of a blow from {@code from} gets past the Bulwark (1 when it isn't out). */
    public static float bulwarkFactor(ServerPlayer p, Vec3 from) {
        if (!equipped(p)) return 1f;
        boolean front = fromFront(p, from);
        float f = !bulwark(p) ? 1f : front ? 0.55f - 0.1f * sw(p, BRUTE) : 0.85f;
        if (form(p) == RAM && front) f *= 0.7f;   // the Battering Ram guards too
        return f;
    }

    /** Kills feed the strain; the Hive's kills burst into spines. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || !equipped(p) || e.getEntity() == p) return;
        LivingEntity t = e.getEntity();
        feed(p, Math.min(10f, 1f + t.getMaxHealth() * 0.2f) + (wears(p, ECHO) ? 5f : 0f));
        if (isBoss(t)) rollMutation(p, 1f);   // a boss always leaves a mutation
        if (sw(p, HIVE) > 0 && ThreadLocalRandom.current().nextFloat() < sw(p, HIVE) && !(t instanceof Player)) {
            ServerLevel sl = p.serverLevel();
            Vec3 c = mid(t);
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3;
                spineFly(sl, c, new Vec3(Math.cos(a), 0.15, Math.sin(a)), 4, 1.5f, 0.7f);
            }
            for (LivingEntity o : Kit.near(sl, p, c, 4)) {
                if (!foe(p, o) || o == t) continue;
                AstaFx.later(2, () -> { if (o.isAlive()) { o.invulnerableTime = 0; armHit(p, o, 4f * spineBoost(p)); } });
            }
        }
    }

    /** Which kind of harm a blow is (HARMS), -1 none the strain learns from. */
    static int harmOf(DamageSource s) {
        if (s.is(DamageTypeTags.IS_FIRE)) return 0;
        if (s.is(DamageTypeTags.IS_FALL)) return 1;
        if (s.is(DamageTypeTags.IS_PROJECTILE)) return 2;
        if (s.is(DamageTypeTags.IS_EXPLOSION)) return 3;
        if (s.is(DamageTypeTags.WITCH_RESISTANT_TO)) return 4;
        return -1;
    }

    /** Adaptation: the blow is softened by what's been learned of its kind, and teaches a little more. */
    static void adapt(ServerPlayer p, LivingHurtEvent e) {
        int h = harmOf(e.getSource());
        if (h < 0 || e.getAmount() <= 0) return;
        CompoundTag a = root(p).getCompound("adapt");
        float res = a.getFloat("r" + h);
        float amount = e.getAmount();
        e.setAmount(amount * (1f - res / 100f));
        a.putFloat("r" + h, Math.min(ADAPT_MAX, res + amount));   // a point of resistance for each point of harm
        a.putLong("t" + h, p.serverLevel().getGameTime());
        root(p).put("adapt", a);
        dirty(p);
    }

    /** Twin Hearts: the strain refuses to die - once every 5 minutes. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onOwnDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !equipped(p) || !wears(p, TWIN)) return;
        CompoundTag r = root(p);
        long now = p.serverLevel().getGameTime();
        if (now < r.getLong("twinCd")) return;
        e.setCanceled(true);
        r.putLong("twinCd", now + 6000);
        p.setHealth(Math.min(p.getMaxHealth(), 6f));
        FxSend.ring(p.serverLevel(), RED | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.3f, 3.0f, 0.12f, 14);
        sound(p.serverLevel(), p.position(), SoundEvents.TOTEM_USE, 0.6f, 0.6f);
        p.displayClientMessage(Component.literal("Twin Hearts - the second heart takes over").withStyle(ChatFormatting.DARK_RED), true);
        dirty(p);
    }

    /** Falls hurt far less (Leap more), not at all mid-slam or after a glide. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !equipped(p)) return;
        CompoundTag r = root(p);
        long now = p.serverLevel().getGameTime();
        if (r.contains("slam") || now < r.getLong("glide") || wears(p, HOLLOW)) { e.setDistance(0); e.setDamageMultiplier(0); return; }
        e.setDistance(e.getDistance() * (0.5f - 0.1f * rank(p, LEAP)));
    }

    // ================================================================== to the client
    public static void sync(ServerPlayer p, boolean open) {
        CompoundTag r = root(p);
        AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ParasitePackets.Sync(equipped(p), r.getFloat("bio"), r.getFloat("frenzy"),
                frenzied(p), r.getInt("ep"), r.getInt("spent"), r.getInt("devoured"), r.getInt("form"), r.getBoolean("bulwark"), r.getBoolean("hide"),
                r.getInt("strain"), ranks(p), open, extra(p)));
        // everyone round sees the forms on your arms (and how big you've grown)
        boolean on = equipped(p);
        int flags = (on && colossus(p) ? 1 : 0) | (on && finalForm(p) ? 2 : 0) | (on && burrowed(p) ? 4 : 0) | ((on ? look(p) : 0) << 3)
                | ((on && r.getInt("form") != NONE ? ParasiteExtras.form2(p) : 0) << 6);
        AddonNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new ParasitePackets.Look(p.getId(), on ? r.getInt("form") : 0, on && r.getBoolean("bulwark"), on && (r.getBoolean("hide") || finalForm(p)), frenzied(p), flags));
    }

    /** {owned mutations, slot 1, slot 2, slot 3, awakened, art cooldown s, Final Form cooldown s, Final Form left s,
     *   sated bonus %, starving, biomass cap, art active s} */
    static int[] extra(ServerPlayer p) {
        int[] sl = slots(p);
        int art = Math.max(left(p, "colossus"), left(p, "swarm"));
        return new int[]{owned(p), sl[0], sl[1], sl[2], awakened(p) ? 1 : 0, left(p, "artCd"), left(p, "ffCd"), left(p, "ffEnd"),
                Math.round(satedBonus(p) * 100), starving(p) ? 1 : 0, (int) cap(p), art,
                strain2(p), organ(p, HEART), organ(p, GUT), organ(p, GLAND),
                Math.round(adapted(p, 0)), Math.round(adapted(p, 1)), Math.round(adapted(p, 2)), Math.round(adapted(p, 3)), Math.round(adapted(p, 4)),
                left(p, "burrow"), evoRank(p), Math.round(eaten(p)), Math.round(limbWear(p)), limbGone(p) ? 1 : 0, nestOf(p) != null ? Math.round(nestOf(p).stored()) : -1,
                sight(p) ? 1 : 0, look(p), nests(p).size(), maxNests(p),
                ParasiteExtras.apex(p), ParasiteExtras.pins(p)[0], ParasiteExtras.pins(p)[1], ParasiteExtras.pins(p)[2], ParasiteExtras.pins(p)[3], ParasiteExtras.form2(p)};
    }

    // ================================================================== tests and screenshots
    public static void testFill(ServerPlayer p) {
        CompoundTag r = root(p);
        r.putFloat("bio", 100); r.putInt("ep", 40); r.putFloat("frenzy", 0); r.putLong("frenzyEnd", 0);
        dirty(p);
    }
    public static void testUnlockAll(ServerPlayer p) {
        CompoundTag r = root(p);
        int[] rk = new int[NODES.length];
        for (int i = 0; i < NODES.length; i++) rk[i] = NODES[i][1];
        r.putIntArray("ranks", rk);
        dirty(p);
    }
    public static void testReset(ServerPlayer p) {
        retractAll(p);
        CompoundTag pd = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        pd.remove(ROOT);
        applyStats(p);
        dirty(p);
    }
    public static void testStrain(ServerPlayer p, int s) { root(p).putInt("strain", s); applyStats(p); dirty(p); }
    public static void testSpent(ServerPlayer p, int n) { root(p).putInt("spent", n); dirty(p); }
    public static void testFrenzy(ServerPlayer p) { root(p).putFloat("bio", 100); frenzyStart(p); }
    public static void testMutations(ServerPlayer p) { root(p).putInt("muts", (1 << MUTATIONS) - 1); dirty(p); }
    public static void testPath(ServerPlayer p, int node, int way) { int[] rk = ranks(p); rk[node] = way; root(p).putIntArray("ranks", rk); }
    public static void testCooldowns(ServerPlayer p) { root(p).putLong("artCd", 0); root(p).putLong("ffCd", 0); }
    public static void testSecond(ServerPlayer p, int s) { root(p).putInt("strain2", s); applyStats(p); dirty(p); }
    public static void testOrgans(ServerPlayer p, int heart, int gut, int gland) { root(p).putIntArray("organs", new int[]{heart, gut, gland}); applyStats(p); dirty(p); }
    public static void testEaten(ServerPlayer p, float f) { root(p).putFloat("eaten", f); applyStats(p); dirty(p); }
    public static void testStarve(ServerPlayer p) { root(p).putFloat("bio", 0); root(p).putInt("empty", 30); applyStats(p); dirty(p); }
}
