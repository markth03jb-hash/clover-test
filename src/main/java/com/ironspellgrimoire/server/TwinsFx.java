package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.entity.AddonBoss;
import com.ironspellgrimoire.entity.TwinWarden;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.network.TwinsPackets;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Masked Twins Grimoire: two masks that share one soul. Sol, the sun twin (fire, fierce up close), and Nyx, the
 * moon twin (frost and void, quick and slippery). You wear one at a time on your face; the other hangs at your hip.
 *
 * Each twin has its own level (1-50) and learns eight arts as it grows; four of each are chosen for the G wheel. Tap G
 * (or let go in the wheel's middle) to swap: the resting twin, which has been storing Resonance as you fight, bursts out
 * (Sol: a sun flare round you; Nyx: a crescent wave ahead), and you get a short Rush. Sol learns faster by day, Nyx by
 * night, and a twin more than ten levels ahead learns at half speed. Sunstones (day kills) and Moonstones (night kills)
 * are set in each mask's three sockets - each a perk - and the masks grow with them. Raised together the twins bond:
 * Twin Strike (both 10), Mirror Swap (both 25: sneak as you swap to step back to where you last swapped) and, after
 * beating the Dawn and Dusk Wardens in their trial (both 40), Eclipse Union - 20 s fused, every art on the wheel, no art
 * cooldowns. Four titles, one worn at a time.
 *
 * Pages: Swap, Twin Arts (the art last chosen on the wheel), Twin Strike, Eclipse Union, The Masks (the menu).
 * No status effects (attribute modifiers only), nothing breaks or changes blocks, our own particles only.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class TwinsFx {
    private TwinsFx() {}

    static final String ROOT = "isg_twins";
    public static final int SOL = 0, NYX = 1, MAX_LV = 50;
    public static final String[] TWIN = {"Sol", "Nyx"};
    public static final String[][] ARTS = {
            {"Sunstrike", "Flare Shot", "Solar Lance", "Helios Wheel", "Corona", "Zenith Leap", "Dawnbreaker", "Sunspear Barrage"},
            {"Moonstep", "Frost Needle", "Crescent Fan", "Mirror Double", "Lunar Veil", "Starfall Rain", "Tidelock", "Void Rift"}};
    /** Short names for the wheel. */
    public static final String[][] SHORT = {
            {"Strike", "Flare", "Lance", "Wheel", "Corona", "Leap", "Dawn", "Spears"},
            {"Step", "Needle", "Fan", "Double", "Veil", "Starfall", "Tide", "Rift"}};
    public static final String[][] ART_DESC = {
            {"Dash ahead, cutting and burning all in your way.",
             "Three quick bolts of fire at what you aim at.",
             "A piercing beam of sunlight through every foe in a line.",
             "A spinning sun disc that bounces between up to five foes.",
             "8 s: a burning aura scorches every foe near you each second.",
             "Leap high, then slam down in a burst of fire.",
             "Strike the ground: a shockwave of light rolls out and throws foes back.",
             "Eight golden spears fanned out ahead."},
            {"Blink ahead; foes turn on the afterimage you leave behind.",
             "A fast frost shard through a line of foes; it slows them.",
             "Five silver crescents thrown in a fan.",
             "8 s: your next three blows strike twice.",
             "6 s: the next blow aimed at you misses completely.",
             "Silver stars rain down where you aim for 3 s.",
             "An orb where you aim pulls foes in and slows them for 5 s.",
             "A tear in the world where you aim cuts all inside it for 4 s."}};
    public static final int[] ART_LV = {1, 5, 10, 15, 20, 28, 35, 45};
    /** Cooldowns (ticks) and mana costs of each art. */
    public static final int[][] ART_CD = {{80, 60, 160, 200, 400, 240, 360, 500}, {80, 40, 140, 300, 320, 280, 360, 500}};
    public static final int[][] ART_MANA = {{15, 10, 30, 35, 50, 40, 60, 80}, {15, 8, 25, 40, 35, 45, 55, 80}};
    public static final int BOND_STRIKE = 10, BOND_MIRROR = 25, BOND_ECLIPSE = 40;
    public static final int[] SOCKET_LV = {5, 15, 30};
    public static final String[][] SOCKET_NAME = {{"Heartfire", "Ember", "Blaze"}, {"Stillwater", "Moonlit Tear", "Tide"}};
    public static final String[][] SOCKET_DESC = {{"+10% damage from Sol's arts", "Rush lasts 2 s longer", "Sol's Resonance burst +30%"},
            {"Nyx's arts cool down 15% faster", "Moonstep and Veil heal 2 hearts", "Nyx charges Resonance 30% faster"}};
    public static final String[] TITLES = {"Dawnchild", "Nightborn", "the Balanced", "Eclipse-Touched"};
    public static final String[] TITLE_HOW = {"Sol reaches level 25", "Nyx reaches level 25", "Both twins reach level 20", "Beat the Twin Wardens"};
    public static final String[] TITLE_GIVES = {"+3% attack damage", "+3% speed", "+25 max mana", "+2 armour"};
    public static final int SWAP_CD = 60, RUSH = 80, ECLIPSE = 400, TRIAL = 4800;
    static final int PAL_SOL = Fx.GOLD, PAL_SOL2 = Fx.FIRE, PAL_NYX = Fx.SILVER, PAL_NYX2 = Fx.ICE;

    // ------------------------------------------------------------------ the synced values (TwinsPackets.Sync, client/TwinsClient)
    public static final int V_EQ = 0, V_ACTIVE = 1, V_LV = 2, V_XP = 4, V_NEED = 6, V_RES = 8, V_SWAP = 9, V_RUSH = 10, V_LOAD = 11, V_SEL = 19,
            V_CD = 20, V_SUN = 36, V_MOON = 37, V_SOCK = 38, V_TRIAL = 40, V_TRIAL_LEFT = 41, V_ECLIPSE = 42, V_TITLES = 43, V_TITLE = 44, V_DAY = 45,
            V_CORONA = 46, V_VEIL = 47, V_DOUBLE = 48, V_MIRROR = 49, SIZE = 50;

    // ------------------------------------------------------------------ state
    static CompoundTag root(Player p) {
        CompoundTag pd = p.getPersistentData();
        if (!pd.contains(Player.PERSISTED_NBT_TAG)) pd.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag per = pd.getCompound(Player.PERSISTED_NBT_TAG);
        if (!per.contains(ROOT)) {
            CompoundTag r = new CompoundTag();
            r.putInt("lv0", 1); r.putInt("lv1", 1);
            r.putIntArray("eq0", new int[]{0, -1, -1, -1}); r.putIntArray("eq1", new int[]{0, -1, -1, -1});
            r.putInt("title", -1);
            per.put(ROOT, r);
        }
        return per.getCompound(ROOT);
    }

    public static boolean equipped(Player p) {
        var b = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
        return b != null && b.is(AddonItemRegistry.TWINS_GRIMOIRE.get());
    }

    static long now(Player p) { return p.level().getGameTime(); }
    public static int active(Player p) { return root(p).getInt("active") == NYX ? NYX : SOL; }
    public static int level(Player p, int twin) { return Math.max(1, Math.min(MAX_LV, root(p).getInt("lv" + twin))); }
    public static float xp(Player p, int twin) { return root(p).getFloat("xp" + twin); }
    /** XP from one level to the next. */
    public static int need(int lv) { return 15 + 6 * lv; }
    public static float resonance(Player p) { return root(p).getFloat("res"); }
    public static boolean eclipse(Player p) { return root(p).getLong("eclipse") > now(p); }
    public static boolean trialWon(Player p) { return root(p).getBoolean("trial"); }
    static boolean trialRunning(Player p) { return root(p).getLong("trialEnd") > now(p); }
    public static boolean knows(Player p, int twin, int art) { return art >= 0 && art < 8 && level(p, twin) >= ART_LV[art]; }
    static boolean socket(Player p, int twin, int s) { return (root(p).getInt("sock" + twin) & (1 << s)) != 0; }
    /** A mask's stage (1-3): bare, a stone set, all three set. */
    public static int stage(int sockets) { return sockets == 7 ? 3 : sockets != 0 ? 2 : 1; }
    public static int maskForm(Player p, int twin) { return (twin == SOL ? 0 : 3) + stage(root(p).getInt("sock" + twin)) - 1; }
    static int[] load(Player p, int twin) {
        int[] l = root(p).getIntArray("eq" + twin);
        if (l.length != 4) return new int[]{0, -1, -1, -1};
        for (int s = 0; s < 4; s++) if (l[s] < -1 || l[s] > 7) l[s] = -1;   // never an art that doesn't exist
        return l;
    }
    static long[] cds(Player p) {
        long[] c = root(p).getLongArray("cds");
        return c.length == 16 ? c : new long[16];
    }
    static boolean day(Player p) { return !p.level().dimensionType().hasFixedTime() && p.level().isDay(); }
    static boolean night(Player p) { return !p.level().dimensionType().hasFixedTime() && !p.level().isDay(); }
    static boolean bonded(Player p, int at) { return level(p, SOL) >= at && level(p, NYX) >= at; }

    static void tell(ServerPlayer p, String s) { p.displayClientMessage(Component.literal(s).withStyle(ChatFormatting.GRAY), true); }

    static void say(ServerPlayer p, int twin, String s) {
        p.sendSystemMessage(Component.literal(TWIN[twin] + ": ").withStyle(twin == SOL ? ChatFormatting.GOLD : ChatFormatting.AQUA)
                .append(Component.literal(s).withStyle(twin == SOL ? ChatFormatting.YELLOW : ChatFormatting.WHITE, ChatFormatting.ITALIC)));
    }

    /** Once per player: a twin explains a mechanic the first time it matters, the controls after it. */
    static void teach(ServerPlayer p, int bit, int twin, String line, String how) {
        CompoundTag r = root(p);
        if ((r.getInt("taught") & (1 << bit)) != 0) return;
        r.putInt("taught", r.getInt("taught") | (1 << bit));
        p.sendSystemMessage(Component.literal(TWIN[twin] + ": ").withStyle(twin == SOL ? ChatFormatting.GOLD : ChatFormatting.AQUA)
                .append(Component.literal(line).withStyle(twin == SOL ? ChatFormatting.YELLOW : ChatFormatting.WHITE, ChatFormatting.ITALIC))
                .append(Component.literal("  [" + how + "]").withStyle(ChatFormatting.GRAY)));
    }

    /** Whatever's newly possible, taught once (called when things change). */
    static void lessons(ServerPlayer p) {
        CompoundTag r = root(p);
        teach(p, 0, SOL, "Two of us, one face at a time.", "tap G to swap masks - hold G for the wheel of your four arts; The Masks page has it all");
        if (level(p, SOL) >= 5 || level(p, NYX) >= 5) teach(p, 1, active(p), "I've learned something new.", "The Masks page: choose four arts of each twin for the G wheel");
        if (level(p, SOL) >= SOCKET_LV[0] || level(p, NYX) >= SOCKET_LV[0]) teach(p, 2, NYX, "There's room in the mask for a stone now.",
                "Sunstones drop by day, Moonstones by night - set them on the Masks tab");
        if (bonded(p, BOND_STRIKE)) teach(p, 3, SOL, "We can strike together now.", "the Twin Strike page");
        if (bonded(p, BOND_MIRROR)) teach(p, 4, NYX, "I remember where we last changed faces.", "Mirror Swap: sneak as you swap to step back there");
        if (bonded(p, BOND_ECLIPSE) && !trialWon(p)) teach(p, 5, SOL, "Two wardens guard the way to the eclipse.", "the Bond tab: begin the trial - beat both wardens in 4 minutes");
        if (r.getFloat("res") >= 100) teach(p, 6, 1 - active(p), "I'm full. Let me out.", "swap now: the resting twin bursts out with what it stored");
    }

    // ------------------------------------------------------------------ levels
    /** XP to a twin (already weighed by day/night and balance); levels it up, teaches, awards titles. */
    static void addXp(ServerPlayer p, int twin, float amt) {
        if (amt <= 0) return;
        CompoundTag r = root(p);
        int lv = level(p, twin);
        if (lv >= MAX_LV) return;
        float x = r.getFloat("xp" + twin) + amt;
        boolean up = false;
        while (lv < MAX_LV && x >= need(lv)) { x -= need(lv); lv++; up = true; }
        r.putInt("lv" + twin, lv);
        r.putFloat("xp" + twin, lv >= MAX_LV ? 0 : x);
        if (!up) return;
        ServerLevel sl = p.serverLevel();
        say(p, twin, TWIN[twin] + " reached level " + lv + ".");
        for (int a = 0; a < 8; a++) if (ART_LV[a] == lv) p.sendSystemMessage(Component.literal("  New art: " + ARTS[twin][a] + " - " + ART_DESC[twin][a]).withStyle(ChatFormatting.GRAY));
        for (int s = 0; s < 3; s++) if (SOCKET_LV[s] == lv) p.sendSystemMessage(Component.literal("  A socket opened in " + TWIN[twin] + "'s mask: " + SOCKET_NAME[twin][s]).withStyle(ChatFormatting.GRAY));
        Fx.spark(sl, twin == SOL ? PAL_SOL : PAL_NYX, p.getEyePosition(), 1.2f);
        FxSend.ring(sl, twin == SOL ? PAL_SOL : PAL_NYX, p.position().add(0, 0.1, 0), 0.3f, 2.2f, 0.12f, 14);
        OverdriveFx.play(sl, p.position(), twin == SOL ? "twin_rise" : "twin_fall", 0.9f, 1.2f);
        if (level(p, SOL) >= 25) award(p, 0);
        if (level(p, NYX) >= 25) award(p, 1);
        if (bonded(p, 20)) award(p, 2);
        lessons(p);
    }

    static void award(ServerPlayer p, int t) {
        CompoundTag r = root(p);
        if ((r.getInt("titles") & (1 << t)) != 0) return;
        r.putInt("titles", r.getInt("titles") | (1 << t));
        p.sendSystemMessage(Component.literal("Title earned: " + TITLES[t] + " (" + TITLE_GIVES[t] + ") - wear it on the Bond tab").withStyle(ChatFormatting.GOLD));
    }

    /** How hard a twin's arts hit: its level, your spell power, and Heartfire for Sol. */
    static float mult(ServerPlayer p, int twin) {
        float m = (1 + 0.02f * level(p, twin)) * (float) p.getAttributeValue(AttributeRegistry.SPELL_POWER.get());
        if (twin == SOL && socket(p, SOL, 0)) m *= 1.1f;
        if (eclipse(p)) m *= 1.15f;
        return m;
    }

    /** An art's hit: damage (the twin learns a little from it), never eaten by the target's hurt-frames. */
    static void hit(ServerPlayer p, LivingEntity t, float dmg, int twin) {
        if (!foe(p, t)) return;
        t.invulnerableTime = 0;
        AstaFx.damage(p, t, dmg, "twin_arts");
        addXp(p, twin, dmg * 0.1f * weigh(p, twin));
    }

    static boolean foe(ServerPlayer p, LivingEntity t) { return t != p && t.isAlive() && !(t instanceof Player) && !t.isAlliedTo(p); }

    /** Day and night, and the balance between the two. */
    static float weigh(ServerPlayer p, int twin) {
        float k = 1f;
        if (twin == SOL && day(p) || twin == NYX && night(p)) k *= 1.25f;
        if (level(p, twin) > level(p, 1 - twin) + 10) k *= 0.5f;
        return k;
    }

    // ------------------------------------------------------------------ the pages
    public static void swapPage(ServerPlayer p, float power) { swap(p); }
    public static void artsPage(ServerPlayer p, float power) {
        int twin = active(p), slot = Math.max(0, Math.min(3, root(p).getInt("sel")));
        int art = load(p, twin)[slot];
        if (art < 0) { tell(p, "Choose an art for that slot on The Masks page"); return; }
        cast(p, twin, art);
    }
    public static void theMasks(ServerPlayer p, float power) { sync(p, true); }
    public static boolean canStrike(Player p) { return bonded(p, BOND_STRIKE); }
    public static boolean canEclipse(Player p) { return bonded(p, BOND_ECLIPSE) && trialWon(p); }

    // ------------------------------------------------------------------ swapping
    /** The resting twin takes the face: its Resonance bursts out, a Rush, and (sneaking, bonded) a step back to the last swap. */
    public static void swap(ServerPlayer p) {
        CompoundTag r = root(p);
        long t = now(p);
        if (r.getLong("swapCd") > t) { tell(p, "Swap ready in " + (int) Math.ceil((r.getLong("swapCd") - t) / 20.0) + " s"); return; }
        ServerLevel sl = p.serverLevel();
        int in = 1 - active(p);
        r.putInt("active", in);
        r.putLong("swapCd", t + (eclipse(p) ? SWAP_CD / 2 : SWAP_CD));
        float k = r.getFloat("res") / 100f;
        r.putFloat("res", 0);
        Vec3 here = p.position();
        // Mirror Swap: back to where you last swapped
        if (p.isShiftKeyDown() && bonded(p, BOND_MIRROR) && r.getBoolean("hasMirror") && r.getString("mirDim").equals(sl.dimension().location().toString())) {
            Vec3 m = new Vec3(r.getDouble("mirX"), r.getDouble("mirY"), r.getDouble("mirZ"));
            if (m.distanceTo(here) < 48) {
                FxSend.ring(sl, in == SOL ? PAL_SOL : PAL_NYX, here.add(0, 0.1, 0), 1.4f, 0.2f, 0.12f, 10);
                p.teleportTo(m.x, m.y, m.z);
                p.fallDistance = 0;
                FxSend.ring(sl, in == SOL ? PAL_SOL : PAL_NYX, m.add(0, 0.1, 0), 0.2f, 1.6f, 0.12f, 12);
                Fx.flash(sl, m.add(0, 1, 0), 1f);
            }
        }
        r.putBoolean("hasMirror", true);
        r.putDouble("mirX", here.x); r.putDouble("mirY", here.y); r.putDouble("mirZ", here.z);
        r.putString("mirDim", sl.dimension().location().toString());
        // the Rush
        int rush = RUSH + (in == SOL && socket(p, SOL, 1) ? 40 : 0);
        r.putLong("rush", t + rush);
        if (in == SOL) TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, RUSH_DMG, "Sol's Rush", 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, rush);
        else TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, RUSH_SPD, "Nyx's Rush", 0.25, AttributeModifier.Operation.MULTIPLY_TOTAL, rush);
        // the burst (its own hits don't charge the twin that just went to rest)
        if (k > 0.05f) { bursting = true; try { burst(p, in, k); } finally { bursting = false; } r.putFloat("res", 0); }
        // the swap itself: the masks turn over
        Vec3 face = p.getEyePosition().add(p.getLookAngle().scale(0.4));
        Fx.spark(sl, in == SOL ? PAL_SOL : PAL_NYX, face, 0.8f);
        FxSend.ring(sl, in == SOL ? PAL_SOL : PAL_NYX, p.position().add(0, 1.0, 0), 0.2f, 1.2f, 0.08f, 8);
        OverdriveFx.play(sl, p.position(), in == SOL ? "twin_rise" : "twin_fall", 0.8f, 1f);
        look(p, true);
        sync(p, false);
    }

    /** What the resting twin stored, let out: Sol's sun flare all round, or Nyx's crescent wave ahead. */
    static void burst(ServerPlayer p, int twin, float k) {
        ServerLevel sl = p.serverLevel();
        float m = mult(p, twin);
        if (twin == SOL) {
            float rad = 3 + 3 * k, dmg = (4 + 14 * k) * m * (socket(p, SOL, 2) ? 1.3f : 1f);
            FxSend.ring(sl, PAL_SOL, p.position().add(0, 0.1, 0), 0.4f, rad, 0.5f, 12);
            FxSend.ring(sl, PAL_SOL2, p.position().add(0, 0.6, 0), 0.2f, rad * 0.8f, 0.25f, 10);
            Fx.pillar(sl, PAL_SOL, p.position(), 3f + 2 * k, 1.2f, 12);
            for (LivingEntity e : enemies(sl, p, p.getBoundingBox().inflate(rad))) if (e.distanceTo(p) <= rad) hit(p, e, dmg, SOL);
            OverdriveFx.play(sl, p.position(), "sun_flare", 1f, 1f);
        } else {
            Vec3 d = flat(p.getLookAngle()), up = new Vec3(0, 1, 0);
            float reach = 6 + 4 * k, dmg = (4 + 12 * k) * m;
            for (int i = -1; i <= 1; i++) {
                Vec3 dir = rotY(d, i * 0.45);
                FxSend.crescent(sl, PAL_NYX, p.position().add(0, 1, 0).add(dir.scale(1.2)), dir, rightOf(dir), 1.6f, 2.4f, 10, reach / 10f);
            }
            for (LivingEntity e : enemies(sl, p, p.getBoundingBox().inflate(reach))) {
                Vec3 to = e.position().subtract(p.position());
                if (to.length() <= reach && flat(to).dot(d) > 0.4) {
                    hit(p, e, dmg, NYX);
                    slow(e, -0.3, 60);
                }
            }
            OverdriveFx.play(sl, p.position(), "moon_shimmer", 1f, 1f);
        }
        Fx.flash(sl, p.getEyePosition(), 0.6f + k);
    }

    static final UUID RUSH_DMG = UUID.fromString("7a3e2c10-5b1d-4c6e-9f02-1b2c7a9100a1"), RUSH_SPD = UUID.fromString("7a3e2c10-5b1d-4c6e-9f02-1b2c7a9100a2"),
            SOL_DMG = UUID.fromString("7a3e2c10-5b1d-4c6e-9f02-1b2c7a9100a3"), NYX_SPD = UUID.fromString("7a3e2c10-5b1d-4c6e-9f02-1b2c7a9100a4"),
            NYX_POW = UUID.fromString("7a3e2c10-5b1d-4c6e-9f02-1b2c7a9100a5"), ECL_DMG = UUID.fromString("7a3e2c10-5b1d-4c6e-9f02-1b2c7a9100a6"),
            ECL_SPD = UUID.fromString("7a3e2c10-5b1d-4c6e-9f02-1b2c7a9100a7"), TITLE_ID = UUID.fromString("7a3e2c10-5b1d-4c6e-9f02-1b2c7a9100a8"),
            SLOW = UUID.fromString("7a3e2c10-5b1d-4c6e-9f02-1b2c7a9100a9");

    static void slow(LivingEntity e, double by, int ticks) {
        TimedModifiers.apply(e, Attributes.MOVEMENT_SPEED, SLOW, "Twins' frost", by, AttributeModifier.Operation.MULTIPLY_TOTAL, ticks);
    }

    // ------------------------------------------------------------------ the arts
    /** Casts an art of a twin (the wheel, or the Twin Arts page): known, chosen (or the eclipse), off cooldown, paid for. */
    public static boolean cast(ServerPlayer p, int twin, int art) {
        if (!equipped(p) || twin < 0 || twin > 1 || art < 0 || art > 7) return false;
        CompoundTag r = root(p);
        boolean ecl = eclipse(p);
        if (!knows(p, twin, art)) { tell(p, ARTS[twin][art] + " is learned at " + TWIN[twin] + " level " + ART_LV[art]); return false; }
        if (!ecl && twin != active(p)) { tell(p, "Wear " + TWIN[twin] + "'s mask to use its arts"); return false; }
        if (!ecl) {
            boolean chosen = false;
            for (int a : load(p, twin)) if (a == art) chosen = true;
            if (!chosen) { tell(p, ARTS[twin][art] + " isn't on your wheel"); return false; }
        }
        long t = now(p);
        long[] cd = cds(p);
        int i = twin * 8 + art;
        if (!ecl && cd[i] > t) { tell(p, ARTS[twin][art] + " ready in " + (int) Math.ceil((cd[i] - t) / 20.0) + " s"); return false; }
        MagicData md = MagicData.getPlayerMagicData(p);
        float cost = ART_MANA[twin][art];
        if (!p.isCreative() && md.getMana() < cost) { tell(p, "Not enough mana"); return false; }
        if (!p.isCreative()) md.setMana(md.getMana() - cost);
        int c = ART_CD[twin][art];
        if (twin == NYX && socket(p, NYX, 0)) c = Math.round(c * 0.85f);
        cd[i] = t + c;
        r.putLongArray("cds", cd);
        if (twin == active(p)) for (int s = 0; s < 4; s++) if (load(p, twin)[s] == art) r.putInt("sel", s);
        r.putLong("combat", t);
        if (twin == SOL) solArt(p, art); else nyxArt(p, art);
        sync(p, false);
        return true;
    }

    static void solArt(ServerPlayer p, int art) {
        ServerLevel sl = p.serverLevel();
        float m = mult(p, SOL);
        Vec3 d = flat(p.getLookAngle()), eye = p.getEyePosition(), look = p.getLookAngle();
        switch (art) {
            case 0 -> {   // Sunstrike: a dash that cuts and burns
                p.setDeltaMovement(d.scale(1.8).add(0, 0.15, 0));
                p.hurtMarked = true;
                Set<Integer> got = new HashSet<>();
                Vec3 start = p.position();
                for (int k = 1; k <= 7; k++) {
                    final int kk = k;
                    AstaFx.later(k, () -> {
                        if (!alive(p)) return;
                        for (LivingEntity e : enemies(sl, p, p.getBoundingBox().inflate(1.6))) {
                            if (!got.add(e.getId())) continue;
                            hit(p, e, 8 * m, SOL);
                            burn(p, e, 3, 1.5f * m);
                            FxSend.slash(sl, PAL_SOL, mid(e), d, new Vec3(0, 1, 0), 1.0f, 1.6f, 5, 0);
                        }
                        if (kk % 2 == 0) FxSend.ring(sl, PAL_SOL2, p.position().add(0, 0.05, 0), 0.2f, 0.7f, 0.05f, 30);   // the burning marks left behind
                    });
                }
                FxSend.trail(sl, PAL_SOL, start.add(0, 1, 0), start.add(0, 1, 0).add(d.scale(6)), 0.5f, 8);
                OverdriveFx.play(sl, p.position(), "rush", 0.9f, 1.2f);
            }
            case 1 -> {   // Flare Shot: three bolts
                for (int k = 0; k < 3; k++) AstaFx.later(1 + k * 4, () -> {
                    if (!alive(p)) return;
                    Vec3 e0 = p.getEyePosition(), l = p.getLookAngle();
                    LivingEntity tg = aimed(p, 24, 10);
                    Vec3 end = tg != null ? mid(tg) : clip(sl, p, e0, e0.add(l.scale(24)));
                    if (tg == null) { LivingEntity first = firstOnLine(p, e0, end, 0.9); if (first != null) { tg = first; end = mid(first); } }
                    FxSend.beam(sl, PAL_SOL2, e0.add(l.scale(0.6)).add(0, -0.15, 0), end, 0.16f, 5);
                    Fx.spark(sl, PAL_SOL, end, 0.6f);
                    if (tg != null) { hit(p, tg, 5 * m, SOL); }
                    OverdriveFx.play(sl, e0, "sun_flare", 0.4f, 1.8f);
                });
            }
            case 2 -> {   // Solar Lance: through every foe in a line
                Vec3 end = clip(sl, p, eye, eye.add(look.scale(24)));
                FxSend.beam(sl, PAL_SOL, eye.add(look.scale(0.6)).add(0, -0.2, 0), end, 0.55f, 10);
                FxSend.beam(sl, PAL_SOL2, eye.add(look.scale(0.6)).add(0, -0.2, 0), end, 0.2f, 8);
                Fx.flash(sl, end, 1f);
                for (LivingEntity e : onLine(p, eye, end, 1.2)) hit(p, e, 14 * m, SOL);
                OverdriveFx.play(sl, eye, "sun_flare", 1f, 1.3f);
            }
            case 3 -> {   // Helios Wheel: a disc bouncing between foes
                LivingEntity first = aimed(p, 16, 25);
                if (first == null) {
                    Vec3 end = clip(sl, p, eye, eye.add(d.scale(12)));
                    FxSend.trail(sl, PAL_SOL, eye.add(0, -0.4, 0), end, 0.5f, 10);
                    return;
                }
                List<LivingEntity> chain = new ArrayList<>();
                chain.add(first);
                Set<Integer> got = new HashSet<>(); got.add(first.getId());
                while (chain.size() < 5) {
                    LivingEntity last = chain.get(chain.size() - 1), next = null; double best = 36;
                    for (LivingEntity e : enemies(sl, p, last.getBoundingBox().inflate(6))) {
                        if (got.contains(e.getId())) continue;
                        double dd = e.distanceToSqr(last);
                        if (dd < best) { best = dd; next = e; }
                    }
                    if (next == null) break;
                    chain.add(next); got.add(next.getId());
                }
                Vec3[] from = {eye.add(0, -0.4, 0)};
                for (int k = 0; k < chain.size(); k++) {
                    LivingEntity e = chain.get(k);
                    AstaFx.later(1 + k * 5, () -> {
                        if (!alive(p) || !e.isAlive()) return;
                        Vec3 to = mid(e);
                        FxSend.trail(sl, PAL_SOL, from[0], to, 0.45f, 8);
                        FxSend.ring(sl, PAL_SOL | FxPacket.KEEP_SHAPE, to, 0.2f, 0.9f, 0.05f, 6);
                        hit(p, e, 9 * m, SOL);
                        from[0] = to;
                    });
                }
                OverdriveFx.play(sl, eye, "blade_fly", 0.8f, 1.3f);
            }
            case 4 -> {   // Corona: 8 s of a burning aura
                root(p).putLong("corona", now(p) + 160);
                FxSend.ring(sl, PAL_SOL, p.position().add(0, 0.1, 0), 0.5f, 4f, 0.4f, 14);
                OverdriveFx.play(sl, p.position(), "sun_flare", 0.9f, 0.8f);
            }
            case 5 -> {   // Zenith Leap: up, then down in fire
                p.setDeltaMovement(d.scale(0.5).add(0, 1.25, 0));
                p.hurtMarked = true;
                root(p).putLong("noFall", now(p) + 80);
                FxSend.ring(sl, PAL_SOL, p.position().add(0, 0.1, 0), 0.3f, 1.8f, 0.2f, 10);
                AstaFx.later(12, () -> {
                    if (!alive(p)) return;
                    p.setDeltaMovement(p.getDeltaMovement().multiply(0.3, 0, 0.3).add(0, -2.6, 0));
                    p.hurtMarked = true;
                    final boolean[] landed = {false};
                    for (int k = 1; k <= 30; k++) {
                        final int kk = k;
                        AstaFx.later(k, () -> {
                            if (landed[0] || !alive(p)) return;
                            if (!p.onGround() && kk < 30) return;
                            landed[0] = true;
                            Vec3 at = p.position();
                            FxSend.ring(sl, PAL_SOL, at.add(0, 0.1, 0), 0.4f, 4.5f, 0.6f, 12);
                            FxSend.ring(sl, PAL_SOL2, at.add(0, 0.2, 0), 0.2f, 3.5f, 0.3f, 10);
                            Fx.pillar(sl, PAL_SOL, at, 3f, 1.4f, 10);
                            FxSend.shake(sl, at, 0.5f, 6, 10);
                            for (LivingEntity e : enemies(sl, p, p.getBoundingBox().inflate(4))) if (e.distanceTo(p) <= 4.5) {
                                hit(p, e, 16 * m, SOL);
                                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.4, 0)); e.hurtMarked = true;
                            }
                            OverdriveFx.play(sl, at, "stomp", 1f, 0.9f);
                        });
                    }
                });
            }
            case 6 -> {   // Dawnbreaker: a shockwave of light rolling out
                Vec3 c = p.position().add(d.scale(2.0));
                Fx.pillar(sl, PAL_SOL, c, 4f, 1.6f, 12);
                FxSend.ring(sl, PAL_SOL, c.add(0, 0.1, 0), 0.5f, 7f, 0.8f, 14);
                FxSend.ring(sl, PAL_SOL2, c.add(0, 0.3, 0), 0.3f, 6f, 0.4f, 12);
                FxSend.shake(sl, c, 0.7f, 8, 14);
                Set<Integer> got = new HashSet<>();
                for (int k = 0; k <= 6; k++) {
                    final double rr = 1 + k;
                    AstaFx.later(1 + k, () -> {
                        if (!alive(p)) return;
                        for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(rr + 1, 3, rr + 1))) {
                            double dd = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
                            if (dd > rr || !got.add(e.getId())) continue;
                            hit(p, e, 20 * m, SOL);
                            Vec3 away = flat(e.position().subtract(c));
                            e.setDeltaMovement(e.getDeltaMovement().add(away.scale(1.1)).add(0, 0.35, 0)); e.hurtMarked = true;
                        }
                    });
                }
                OverdriveFx.play(sl, c, "sun_flare", 1.3f, 0.7f);
            }
            case 7 -> {   // Sunspear Barrage: eight spears in a fan
                for (int i = 0; i < 8; i++) {
                    final double a = Math.toRadians(-30 + i * 60.0 / 7);
                    AstaFx.later(1 + i, () -> {
                        if (!alive(p)) return;
                        Vec3 e0 = p.getEyePosition().add(0, -0.2, 0), dir = rotY(flat(p.getLookAngle()), a).add(0, p.getLookAngle().y, 0).normalize();
                        Vec3 end = clip(sl, p, e0, e0.add(dir.scale(18)));
                        LivingEntity first = firstOnLine(p, e0, end, 0.9);
                        if (first != null) end = mid(first);
                        FxSend.spike(sl, PAL_SOL, e0.add(dir.scale(0.8)), dir, 1.6f, 0.12f, 6, 0, FxPacket.HOLD, 0, dir.scale(Math.max(1, e0.distanceTo(end) / 5)), 5);
                        FxSend.beam(sl, PAL_SOL2, e0, end, 0.06f, 4);
                        if (first != null) { hit(p, first, 12 * m, SOL); Fx.spark(sl, PAL_SOL, end, 0.6f); }
                    });
                }
                OverdriveFx.play(sl, eye, "blade_fly", 1f, 1.1f);
            }
            default -> {}
        }
    }

    static void nyxArt(ServerPlayer p, int art) {
        ServerLevel sl = p.serverLevel();
        float m = mult(p, NYX);
        Vec3 d = flat(p.getLookAngle()), eye = p.getEyePosition(), look = p.getLookAngle();
        switch (art) {
            case 0 -> {   // Moonstep: blink ahead; foes turn on the afterimage
                Vec3 from = p.position();
                Vec3 to = clip(sl, p, from.add(0, 0.6, 0), from.add(0, 0.6, 0).add(d.scale(7)));
                to = to.subtract(d.scale(0.6)).add(0, -0.6, 0);
                Fx.flash(sl, from.add(0, 1, 0), 0.8f);
                Fx.pillar(sl, PAL_NYX, from, 2.2f, 0.7f, 30);
                FxSend.trail(sl, PAL_NYX, from.add(0, 1, 0), to.add(0, 1, 0), 0.35f, 8);
                p.teleportTo(to.x, to.y, to.z);
                p.fallDistance = 0;
                for (Mob mob : sl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(12), x -> x.getTarget() == p)) {
                    mob.setTarget(null);
                    mob.getNavigation().moveTo(from.x, from.y, from.z, 1.0);
                }
                if (socket(p, NYX, 1)) p.heal(4f);
                OverdriveFx.play(sl, to, "moon_shimmer", 0.8f, 1.3f);
            }
            case 1 -> {   // Frost Needle: through a line; slows
                Vec3 end = clip(sl, p, eye, eye.add(look.scale(20)));
                FxSend.beam(sl, PAL_NYX2, eye.add(look.scale(0.6)).add(0, -0.15, 0), end, 0.12f, 6);
                FxSend.spike(sl, PAL_NYX, eye.add(look.scale(1.0)), look, 1.2f, 0.08f, 5, 0, FxPacket.HOLD, 0, look.scale(4), 4);
                for (LivingEntity e : onLine(p, eye, end, 0.8)) { hit(p, e, 6 * m, NYX); slow(e, -0.3, 60); }
                OverdriveFx.play(sl, eye, "moon_shimmer", 0.6f, 1.8f);
            }
            case 2 -> {   // Crescent Fan: five crescents
                Set<Integer> got = new HashSet<>();
                for (int i = 0; i < 5; i++) {
                    Vec3 dir = rotY(d, Math.toRadians(-30 + i * 15));
                    Vec3 c0 = p.position().add(0, 1.1, 0).add(dir.scale(1.0));
                    FxSend.crescent(sl, PAL_NYX, c0, dir, rightOf(dir), 0.8f, 1.2f, 10, 1.2f);
                    Vec3 end = clip(sl, p, c0, c0.add(dir.scale(12)));
                    LivingEntity first = firstOnLine(p, c0, end, 1.2);
                    if (first != null && got.add(first.getId())) {
                        final LivingEntity f = first;
                        AstaFx.later(Math.max(1, (int) (f.distanceTo(p) / 1.2)), () -> { if (alive(p)) hit(p, f, 7 * m, NYX); });
                    }
                }
                OverdriveFx.play(sl, eye, "moon_shimmer", 0.8f, 1.1f);
            }
            case 3 -> {   // Mirror Double: the next three blows strike twice
                CompoundTag r = root(p);
                r.putLong("double", now(p) + 160);
                r.putInt("doubleLeft", 3);
                Fx.pillar(sl, PAL_NYX, p.position(), 2.4f, 0.8f, 12);
                FxSend.ring(sl, PAL_NYX, p.position().add(0, 1.0, 0), 0.3f, 1.4f, 0.1f, 10);
                OverdriveFx.play(sl, p.position(), "moon_shimmer", 0.8f, 0.9f);
            }
            case 4 -> {   // Lunar Veil: the next blow misses
                root(p).putLong("veil", now(p) + 120);
                FxSend.ring(sl, PAL_NYX, p.position().add(0, 1.0, 0), 1.2f, 1.3f, 1.2f, 16);
                OverdriveFx.play(sl, p.position(), "moon_shimmer", 0.9f, 0.7f);
            }
            case 5 -> {   // Starfall Rain: stars falling where you aim
                Vec3 c = aimPoint(p, 20);
                for (int k = 0; k < 12; k++) {
                    final int kk = k;
                    AstaFx.later(2 + k * 5, () -> {
                        if (!alive(p)) return;
                        ThreadLocalRandom rnd = ThreadLocalRandom.current();
                        Vec3 at = kk % 6 == 0 ? c : c.add(rnd.nextDouble(-4, 4), 0, rnd.nextDouble(-4, 4));   // the first lands right where you aimed
                        Vec3 top = at.add(0, 8, 0);
                        FxSend.spike(sl, kk % 2 == 0 ? PAL_NYX : PAL_NYX2, top, new Vec3(0, -1, 0), 1.2f, 0.18f, 6, 0, FxPacket.HOLD, 0, new Vec3(0, -1.6, 0), 5);
                        AstaFx.later(5, () -> {
                            if (!alive(p)) return;
                            FxSend.ring(sl, PAL_NYX, at.add(0, 0.1, 0), 0.2f, 1.6f, 0.15f, 8);
                            for (LivingEntity e : enemies(sl, p, new AABB(at, at).inflate(1.6, 2.5, 1.6))) hit(p, e, 5 * m, NYX);
                        });
                    });
                }
                OverdriveFx.play(sl, c, "moon_shimmer", 1f, 0.8f);
            }
            case 6 -> {   // Tidelock: an orb that pulls and slows
                Vec3 c = aimPoint(p, 18).add(0, 1, 0);
                for (int k = 0; k < 20; k++) {
                    final int kk = k;
                    AstaFx.later(1 + k * 5, () -> {
                        if (!alive(p)) return;
                        if (kk % 2 == 0) FxSend.ring(sl, PAL_NYX2, c.add(0, -0.9, 0), 6f, 0.5f, 0.2f, 10);
                        FxSend.spike(sl, PAL_NYX, c.add(0, -0.4, 0), new Vec3(0, 1, 0), 0.8f, 0.4f, 6, 0, FxPacket.HOLD);
                        for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(6))) {
                            if (e.position().distanceTo(c) > 6.5) continue;
                            Vec3 pull = c.subtract(e.position()).normalize().scale(0.18);
                            e.setDeltaMovement(e.getDeltaMovement().add(pull.x, 0, pull.z)); e.hurtMarked = true;
                            slow(e, -0.4, 20);
                            if (kk % 4 == 0) hit(p, e, 2 * m, NYX);
                        }
                    });
                }
                OverdriveFx.play(sl, c, "moon_shimmer", 1f, 0.6f);
            }
            case 7 -> {   // Void Rift: a tear that cuts all inside it
                Vec3 c = aimPoint(p, 18).add(0, 1, 0);
                FxSend.cracks(sl, Fx.VOID, c, d, new Vec3(0, 1, 0), 2.2f, 80, true, ThreadLocalRandom.current().nextInt());
                for (int k = 0; k < 16; k++) {
                    final int kk = k;
                    AstaFx.later(1 + k * 5, () -> {
                        if (!alive(p)) return;
                        if (kk % 3 == 0) FxSend.ring(sl, Fx.VOID, c.add(0, -0.9, 0), 0.4f, 3.5f, 0.3f, 12);
                        FxSend.slash(sl, PAL_NYX, c.add(ThreadLocalRandom.current().nextDouble(-1.5, 1.5), ThreadLocalRandom.current().nextDouble(-0.8, 0.8), ThreadLocalRandom.current().nextDouble(-1.5, 1.5)),
                                rotY(d, kk * 1.3), new Vec3(0, 1, 0), 0.8f, 1.4f, 4, 0);
                        for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(3.5))) if (e.position().distanceTo(c) <= 4) hit(p, e, 3 * m, NYX);
                    });
                }
                OverdriveFx.play(sl, c, "eclipse_toll", 0.6f, 1.6f);
            }
            default -> {}
        }
    }

    /** Twin Strike: the gold mask and the silver mask cut at once. */
    public static void twinStrike(ServerPlayer p, float power) {
        if (!canStrike(p)) { tell(p, "Both twins must reach level " + BOND_STRIKE); return; }
        ServerLevel sl = p.serverLevel();
        Vec3 d = flat(p.getLookAngle()), c = p.position().add(0, 1.1, 0).add(d.scale(1.6));
        float m = (mult(p, SOL) + mult(p, NYX)) / 2f;
        FxSend.slash(sl, PAL_SOL, c, d, new Vec3(1, 1, 0).normalize(), 2.2f, 3.2f, 6, 0);
        AstaFx.later(4, () -> FxSend.slash(sl, PAL_NYX, c, d, new Vec3(-1, 1, 0).normalize(), 2.2f, 3.2f, 6, 0));
        for (int k = 0; k < 2; k++) {
            final int twin = k == 0 ? SOL : NYX;
            AstaFx.later(1 + k * 4, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : enemies(sl, p, p.getBoundingBox().inflate(4.5))) {
                    Vec3 to = e.position().subtract(p.position());
                    if (to.length() <= 4.5 && flat(to).dot(d) > 0.2) hit(p, e, 9 * m, twin);
                }
            });
        }
        CompoundTag r = root(p);
        r.putFloat("res", Math.min(100f, r.getFloat("res") + 30f));
        OverdriveFx.play(sl, c, "slash", 1f, 1.1f);
        sync(p, false);
    }

    /** Eclipse Union: 20 s fused. */
    public static void eclipseUnion(ServerPlayer p, float power) {
        if (!canEclipse(p)) { tell(p, "Both twins at level " + BOND_ECLIPSE + ", and the Twin Wardens beaten"); return; }
        ServerLevel sl = p.serverLevel();
        root(p).putLong("eclipse", now(p) + ECLIPSE);
        Vec3 at = p.position();
        FxSend.ring(sl, Fx.VOID, at.add(0, 0.1, 0), 0.4f, 6f, 0.8f, 20);
        FxSend.ring(sl, PAL_SOL, at.add(0, 0.2, 0), 0.3f, 5f, 0.3f, 16);
        FxSend.ring(sl, PAL_NYX, at.add(0, 0.3, 0), 0.3f, 5.5f, 0.3f, 18);
        Fx.pillar(sl, Fx.VOID, at, 5f, 1.6f, 16);
        FxSend.shake(sl, at, 0.5f, 10, 14);
        OverdriveFx.play(sl, at, "eclipse_toll", 1.4f, 1f);
        say(p, SOL, "Together, then.");
        look(p, true);
        sync(p, false);
    }

    // ------------------------------------------------------------------ helpers for aiming
    static List<LivingEntity> onLine(ServerPlayer p, Vec3 a, Vec3 b, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : enemies(p.serverLevel(), p, new AABB(a, b).inflate(r + 1)))
            if (closestOnSegment(a, b, mid(e)).distanceTo(mid(e)) <= r + e.getBbWidth() * 0.5) out.add(e);
        return out;
    }

    static LivingEntity firstOnLine(ServerPlayer p, Vec3 a, Vec3 b, double r) {
        LivingEntity best = null; double bd = Double.MAX_VALUE;
        for (LivingEntity e : onLine(p, a, b, r)) { double dd = e.distanceToSqr(a); if (dd < bd) { bd = dd; best = e; } }
        return best;
    }

    /** Where an area art lands: the foe you aim at, or the ground where you look. */
    static Vec3 aimPoint(ServerPlayer p, double range) {
        LivingEntity t = aimed(p, range, 10);
        if (t != null) return t.position();
        Vec3 eye = p.getEyePosition(), end = clip(p.serverLevel(), p, eye, eye.add(p.getLookAngle().scale(range)));
        Vec3 down = clip(p.serverLevel(), p, end.add(0, 0.2, 0), end.add(0, -12, 0));
        return down;
    }

    static void burn(ServerPlayer p, LivingEntity t, int seconds, float per) {
        for (int s = 1; s <= seconds; s++) AstaFx.later(s * 20, () -> {
            if (!t.isAlive() || !alive(p)) return;
            hit(p, t, per, SOL);
            Fx.spark(p.serverLevel(), PAL_SOL2, mid(t), 0.4f);
        });
    }

    // ------------------------------------------------------------------ the menu's actions
    public static void handle(ServerPlayer p, int action, int arg) {
        if (!equipped(p)) return;
        switch (action) {
            case 1 -> swap(p);
            case 2 -> {   // cast: a wheel slot of the worn twin, or (100 + twin * 8 + art) in the eclipse
                if (arg >= 100) cast(p, (arg - 100) / 8, (arg - 100) % 8);
                else if (arg >= 0 && arg < 4) {
                    int art = load(p, active(p))[arg];
                    if (art >= 0) cast(p, active(p), art);
                }
            }
            case 3 -> equip(p, arg / 100, (arg / 10) % 10, arg % 10);
            case 4 -> setStone(p, arg / 10, arg % 10);
            case 5 -> wear(p, arg == 99 ? -1 : arg);
            case 6 -> startTrial(p);
            case 7 -> sync(p, true);
            default -> {}
        }
    }

    /** An art into a wheel slot (art 9: empty it). An art already on the wheel trades places. */
    static void equip(ServerPlayer p, int twin, int slot, int art) {
        if (twin < 0 || twin > 1 || slot < 0 || slot > 3) return;
        int[] l = load(p, twin);
        if (art == 9) l[slot] = -1;
        else {
            if (!knows(p, twin, art)) return;
            for (int s = 0; s < 4; s++) if (l[s] == art) l[s] = l[slot];
            l[slot] = art;
        }
        root(p).putIntArray("eq" + twin, l);
        sync(p, false);
    }

    static void setStone(ServerPlayer p, int twin, int s) {
        if (twin < 0 || twin > 1 || s < 0 || s > 2 || socket(p, twin, s)) return;
        CompoundTag r = root(p);
        String key = twin == SOL ? "sun" : "moon";
        if (level(p, twin) < SOCKET_LV[s] || r.getInt(key) < 1) return;
        r.putInt(key, r.getInt(key) - 1);
        r.putInt("sock" + twin, r.getInt("sock" + twin) | (1 << s));
        ServerLevel sl = p.serverLevel();
        Fx.spark(sl, twin == SOL ? PAL_SOL : PAL_NYX, p.getEyePosition(), 1f);
        OverdriveFx.play(sl, p.position(), twin == SOL ? "twin_rise" : "twin_fall", 0.7f, 1.5f);
        say(p, twin, SOCKET_NAME[twin][s] + " is set. " + SOCKET_DESC[twin][s] + ".");
        look(p, true);
        sync(p, false);
    }

    static void wear(ServerPlayer p, int t) {
        if (t >= 0 && (t > 3 || (root(p).getInt("titles") & (1 << t)) == 0)) return;
        root(p).putInt("title", t);
        sync(p, false);
    }

    // ------------------------------------------------------------------ the trial: the Dawn Warden and the Dusk Warden
    static void startTrial(ServerPlayer p) {
        if (!bonded(p, BOND_ECLIPSE)) { tell(p, "Both twins must reach level " + BOND_ECLIPSE); return; }
        if (trialRunning(p)) { tell(p, "The trial has already begun"); return; }
        ServerLevel sl = p.serverLevel();
        CompoundTag r = root(p);
        r.putLong("trialEnd", now(p) + TRIAL);
        r.putInt("downs", 0);
        Vec3 d = flat(p.getLookAngle()), side = rightOf(d);
        for (int k = 0; k < 2; k++) {
            TwinWarden w = new TwinWarden(sl, p, k == 1);
            Vec3 at = p.position().add(d.scale(6)).add(side.scale(k == 0 ? -3 : 3)).add(0, 1.5, 0);
            w.moveTo(at.x, at.y, at.z, p.getYRot() + 180, 0);
            sl.addFreshEntity(w);
            Fx.pillar(sl, k == 0 ? PAL_SOL : PAL_NYX, at.add(0, -1.5, 0), 4f, 1.4f, 16);
        }
        OverdriveFx.play(sl, p.position(), "eclipse_toll", 1.2f, 0.8f);
        say(p, NYX, "They are what we were before we were us. Beat them both.");
        p.closeContainer();
        sync(p, false);
    }

    /** A warden falls; both down, the trial is won. */
    public static void wardenDown(TwinWarden w, ServerPlayer p) {
        CompoundTag r = root(p);
        if (!trialRunning(p)) return;
        r.putInt("downs", r.getInt("downs") + 1);
        ServerLevel sl = p.serverLevel();
        Fx.flash(sl, w.position().add(0, 1.2, 0), 1.6f);
        Fx.pillar(sl, w.dusk() ? PAL_NYX : PAL_SOL, w.position(), 5f, 1.4f, 20);
        if (r.getInt("downs") < 2) { say(p, w.dusk() ? NYX : SOL, "One more."); return; }
        r.putLong("trialEnd", 0);
        r.putBoolean("trial", true);
        award(p, 3);
        say(p, SOL, "The eclipse is ours.");
        p.sendSystemMessage(Component.literal("Eclipse Union is open: the Eclipse Union page.").withStyle(ChatFormatting.GOLD));
        OverdriveFx.play(sl, p.position(), "eclipse_toll", 1.4f, 1.1f);
        sync(p, false);
    }

    static void endTrial(ServerPlayer p, String why) {
        root(p).putLong("trialEnd", 0);
        for (TwinWarden w : p.serverLevel().getEntitiesOfClass(TwinWarden.class, p.getBoundingBox().inflate(96), x -> p.getUUID().equals(x.owner()))) w.discard();
        tell(p, why);
        sync(p, false);
    }

    /** A warden's turn: the Dawn Warden's fire, the Dusk Warden's frost and blinks. */
    public static void wardenAttack(TwinWarden w, ServerPlayer p, int turn) {
        ServerLevel sl = (ServerLevel) w.level();
        Vec3 from = w.position().add(0, 1.6, 0), to = p.getEyePosition().add(0, -0.3, 0);
        boolean low = w.getHealth() < w.getMaxHealth() * 0.5f;
        var src = w.damageSources().mobAttack(w);
        if (!w.dusk()) {
            if (low && turn % 3 == 2) {   // Dawnbreaker
                Vec3 c = w.position();
                FxSend.ring(sl, PAL_SOL, c.add(0, 0.1, 0), 0.5f, 7f, 0.8f, 14);
                Fx.pillar(sl, PAL_SOL, c, 4f, 1.6f, 12);
                if (p.distanceTo(w) < 7.5) p.hurt(src, 9f);
                OverdriveFx.play(sl, c, "sun_flare", 1.2f, 0.7f);
            } else if (p.distanceTo(w) < 4.5) {   // a flare all round
                FxSend.ring(sl, PAL_SOL, w.position().add(0, 0.5, 0), 0.4f, 4.5f, 0.4f, 12);
                p.hurt(src, 7f);
                OverdriveFx.play(sl, w.position(), "sun_flare", 1f, 1f);
            } else {   // a bolt of fire
                FxSend.beam(sl, PAL_SOL2, from, to, 0.2f, 6);
                if (w.hasLineOfSight(p)) p.hurt(src, 5f);
                OverdriveFx.play(sl, from, "sun_flare", 0.6f, 1.6f);
            }
        } else {
            if (turn % 3 == 1) {   // a blink to the far side
                Vec3 behind = p.position().add(p.position().subtract(w.position()).multiply(1, 0, 1).normalize().scale(5)).add(0, 1.5, 0);
                Fx.flash(sl, w.position().add(0, 1.2, 0), 0.8f);
                w.teleportTo(behind.x, behind.y, behind.z);
                OverdriveFx.play(sl, behind, "moon_shimmer", 0.8f, 1.3f);
            } else if (low && turn % 3 == 2) {   // a few stars on the player
                Vec3 at = p.position();
                for (int k = 0; k < 3; k++) {
                    final int kk = k;
                    AstaFx.later(k * 6, () -> {
                        if (!p.isAlive() || !w.isAlive()) return;
                        FxSend.spike(sl, PAL_NYX, at.add(kk - 1, 8, 0), new Vec3(0, -1, 0), 1.2f, 0.18f, 6, 0, FxPacket.HOLD, 0, new Vec3(0, -1.6, 0), 5);
                        AstaFx.later(5, () -> { if (p.isAlive() && p.position().distanceTo(at) < 2.2) p.hurt(src, 4f); });
                    });
                }
            } else {   // a frost needle
                FxSend.beam(sl, PAL_NYX2, from, to, 0.12f, 5);
                if (w.hasLineOfSight(p)) { p.hurt(src, 5f); slow(p, -0.25, 40); }
                OverdriveFx.play(sl, from, "moon_shimmer", 0.6f, 1.8f);
            }
        }
    }

    // ------------------------------------------------------------------ hits, kills, misses and falls
    static boolean extra, bursting;

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim instanceof ServerPlayer vp && equipped(vp)) root(vp).putLong("combat", now(vp));
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || !equipped(p) || victim == p) return;
        CompoundTag r = root(p);
        r.putLong("combat", now(p));
        float amt = e.getAmount();
        // the resting twin stores Resonance from every blow (not from a burst's)
        if (bursting) return;
        float gain = amt * 0.5f * (active(p) == SOL && socket(p, NYX, 2) ? 1.3f : 1f);
        r.putFloat("res", Math.min(100f, r.getFloat("res") + gain));
        // Mirror Double: a melee blow strikes again
        if (extra || e.getSource().getDirectEntity() != p || !e.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)) return;
        if (r.getLong("double") > now(p) && r.getInt("doubleLeft") > 0) {
            r.putInt("doubleLeft", r.getInt("doubleLeft") - 1);
            ServerLevel sl = p.serverLevel();
            AstaFx.later(6, () -> {
                if (!victim.isAlive() || !alive(p)) return;
                extra = true;
                try { hit(p, victim, amt * 0.6f, NYX); } finally { extra = false; }
                FxSend.slash(sl, PAL_NYX, mid(victim), flat(p.getLookAngle()), new Vec3(-1, 1, 0).normalize(), 1.0f, 1.6f, 5, 0);
            });
        }
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !equipped(p)) return;
        CompoundTag r = root(p);
        if (r.getLong("veil") <= now(p) || e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (e.getSource().getEntity() == null && !e.getSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) return;
        e.setCanceled(true);
        r.putLong("veil", 0);
        ServerLevel sl = p.serverLevel();
        Vec3 back = flat(p.getLookAngle()).scale(-3);
        Vec3 to = clip(sl, p, p.position().add(0, 0.6, 0), p.position().add(0, 0.6, 0).add(back)).add(0, -0.6, 0);
        Fx.flash(sl, p.position().add(0, 1, 0), 0.8f);
        Fx.pillar(sl, PAL_NYX, p.position(), 2.2f, 0.7f, 20);
        p.teleportTo(to.x, to.y, to.z);
        if (socket(p, NYX, 1)) p.heal(4f);
        OverdriveFx.play(sl, to, "moon_shimmer", 0.9f, 1.4f);
        tell(p, "Lunar Veil: missed");
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && root(p).getLong("noFall") > now(p)) { e.setDistance(0); e.setCanceled(true); }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer dead && trialRunning(dead)) endTrial(dead, "The trial is lost - begin it again from the Bond tab");
        if (e.getEntity() instanceof TwinWarden) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || !equipped(p) || e.getEntity() == p) return;
        LivingEntity t = e.getEntity();
        boolean boss = t instanceof AddonBoss || t.getType().is(Tags.EntityTypes.BOSSES);
        float xp = Math.max(2f, t.getMaxHealth() / 2f) * (boss ? 4 : 1);
        if (eclipse(p)) { addXp(p, SOL, xp * 0.6f * weigh(p, SOL)); addXp(p, NYX, xp * 0.6f * weigh(p, NYX)); }
        else addXp(p, active(p), xp * weigh(p, active(p)));
        // stones: Sunstones by day, Moonstones by night
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        CompoundTag r = root(p);
        if (day(p) && (boss || rnd.nextFloat() < 0.05f)) { r.putInt("sun", r.getInt("sun") + 1); p.displayClientMessage(Component.literal("+1 Sunstone").withStyle(ChatFormatting.GOLD), true); }
        else if (night(p) && (boss || rnd.nextFloat() < 0.05f)) { r.putInt("moon", r.getInt("moon") + 1); p.displayClientMessage(Component.literal("+1 Moonstone").withStyle(ChatFormatting.AQUA), true); }
        sync(p, false);
    }

    // ------------------------------------------------------------------ every tick: modifiers, Corona, the trial, the masks others see
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (!equipped(p)) { if (p.tickCount % 10 == 0) look(p, false); return; }
        CompoundTag r = root(p);
        long t = now(p);
        ServerLevel sl = p.serverLevel();
        if (p.tickCount % 10 == 0) look(p, false);
        if (r.getLong("corona") > t && p.tickCount % 20 == 0) {   // Corona: a burning ring each second
            FxSend.ring(sl, PAL_SOL, p.position().add(0, 0.1, 0), 3.6f, 4f, 0.5f, 12);
            float m = mult(p, SOL);
            for (LivingEntity x : enemies(sl, p, p.getBoundingBox().inflate(4))) if (x.distanceTo(p) <= 4.2) hit(p, x, 4 * m, SOL);
        }
        if (r.getLong("veil") > t && p.tickCount % 10 == 0) FxSend.ring(sl, PAL_NYX, p.position().add(0, 1.0, 0), 1.1f, 1.2f, 1.0f, 10);
        if (eclipse(p) && p.tickCount % 10 == 0) FxSend.ring(sl, Fx.VOID, p.position().add(0, 0.1, 0), 0.6f, 1.6f, 0.1f, 10);
        if (r.getLong("trialEnd") > 0 && t > r.getLong("trialEnd")) endTrial(p, "The trial's time ran out - begin it again from the Bond tab");
        if (p.tickCount % 20 != 0) return;
        // each second: the worn twin's gift, the eclipse, the title
        int a = active(p);
        if (a == SOL) TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, SOL_DMG, "Sol's mask", 0.005 * level(p, SOL), AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
        else {
            TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, NYX_SPD, "Nyx's mask", 0.003 * level(p, NYX), AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
            TimedModifiers.apply(p, AttributeRegistry.SPELL_POWER.get(), NYX_POW, "Nyx's mask", 0.004 * level(p, NYX), AttributeModifier.Operation.MULTIPLY_BASE, 30);
        }
        if (eclipse(p)) {
            TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, ECL_DMG, "Eclipse Union", 0.15, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
            TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, ECL_SPD, "Eclipse Union", 0.15, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
        }
        int title = r.getInt("title");
        if (title >= 0 && (r.getInt("titles") & (1 << title)) != 0) switch (title) {
            case 0 -> TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, TITLE_ID, "Dawnchild", 0.03, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
            case 1 -> TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, TITLE_ID, "Nightborn", 0.03, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
            case 2 -> TimedModifiers.apply(p, AttributeRegistry.MAX_MANA.get(), TITLE_ID, "the Balanced", 25, AttributeModifier.Operation.ADDITION, 30);
            case 3 -> TimedModifiers.apply(p, Attributes.ARMOR, TITLE_ID, "Eclipse-Touched", 2, AttributeModifier.Operation.ADDITION, 30);
            default -> {}
        }
        // the resting twin charges while you fight
        if (t - r.getLong("combat") < 100) {
            float gain = 0.5f * (a == SOL && socket(p, NYX, 2) ? 1.3f : 1f);
            r.putFloat("res", Math.min(100f, r.getFloat("res") + gain));
        }
        if (p.tickCount % 60 == 0) { lessons(p); sync(p, false); }
    }

    /** Which masks others see on this player (face, hip; -1 none), sent when it changes (or when forced). */
    static void look(ServerPlayer p, boolean force) {
        CompoundTag r = root(p);
        int face = -1, hip = -1;
        if (equipped(p)) {
            if (eclipse(p)) face = 6;
            else { face = maskForm(p, active(p)); hip = maskForm(p, 1 - active(p)); }
        }
        int code = (face + 2) * 16 + hip + 2;
        if (!force && r.getInt("look") == code) return;
        r.putInt("look", code);
        AddonNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new TwinsPackets.Look(p.getId(), face, hip));
    }

    @SubscribeEvent
    public static void onTrack(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking e) {
        if (!(e.getTarget() instanceof ServerPlayer t) || !(e.getEntity() instanceof ServerPlayer w)) return;
        int code = root(t).getInt("look");
        if (code == 0) return;
        int face = code / 16 - 2, hip = code % 16 - 2;
        if (face >= 0) AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> w), new TwinsPackets.Look(t.getId(), face, hip));
    }

    public static void sync(ServerPlayer p, boolean open) {
        CompoundTag r = root(p);
        long t = now(p);
        int[] v = new int[SIZE];
        v[V_EQ] = equipped(p) ? 1 : 0;
        v[V_ACTIVE] = active(p);
        for (int k = 0; k < 2; k++) {
            v[V_LV + k] = level(p, k);
            v[V_XP + k] = (int) xp(p, k);
            v[V_NEED + k] = need(level(p, k));
            v[V_SOCK + k] = r.getInt("sock" + k);
            int[] l = load(p, k);
            for (int s = 0; s < 4; s++) v[V_LOAD + k * 4 + s] = l[s];
        }
        v[V_RES] = Math.round(r.getFloat("res"));
        v[V_SWAP] = (int) Math.max(0, r.getLong("swapCd") - t);
        v[V_RUSH] = (int) Math.max(0, r.getLong("rush") - t);
        v[V_SEL] = r.getInt("sel");
        long[] cd = cds(p);
        for (int i = 0; i < 16; i++) v[V_CD + i] = (int) Math.max(0, cd[i] - t);
        v[V_SUN] = r.getInt("sun"); v[V_MOON] = r.getInt("moon");
        v[V_TRIAL] = trialWon(p) ? 1 : 0;
        v[V_TRIAL_LEFT] = (int) Math.max(0, (r.getLong("trialEnd") - t) / 20);
        v[V_ECLIPSE] = (int) Math.max(0, r.getLong("eclipse") - t);
        v[V_TITLES] = r.getInt("titles"); v[V_TITLE] = r.getInt("title");
        v[V_DAY] = day(p) ? 1 : night(p) ? 0 : 2;
        v[V_CORONA] = (int) Math.max(0, r.getLong("corona") - t);
        v[V_VEIL] = r.getLong("veil") > t ? 1 : 0;
        v[V_DOUBLE] = r.getLong("double") > t ? r.getInt("doubleLeft") : 0;
        v[V_MIRROR] = r.getBoolean("hasMirror") ? 1 : 0;
        AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new TwinsPackets.Sync(v, open));
    }

    // ------------------------------------------------------------------ test helpers (server/GameTests, UiShotsServer)
    public static void testReset(ServerPlayer p) {
        CompoundTag pd = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        pd.remove(ROOT);
        for (TwinWarden w : p.serverLevel().getEntitiesOfClass(TwinWarden.class, p.getBoundingBox().inflate(96))) w.discard();
        root(p);
    }
    public static void testLevels(ServerPlayer p, int sol, int nyx) { root(p).putInt("lv0", sol); root(p).putInt("lv1", nyx); }
    public static void testActive(ServerPlayer p, int twin) { root(p).putInt("active", twin); }
    public static void testLoad(ServerPlayer p, int twin, int a, int b, int c, int d) { root(p).putIntArray("eq" + twin, new int[]{a, b, c, d}); }
    public static void testRes(ServerPlayer p, float res) { root(p).putFloat("res", res); }
    public static void testStones(ServerPlayer p, int sun, int moon) { root(p).putInt("sun", sun); root(p).putInt("moon", moon); }
    public static void testSockets(ServerPlayer p, int sol, int nyx) { root(p).putInt("sock0", sol); root(p).putInt("sock1", nyx); }
    public static void testCooldowns(ServerPlayer p) { root(p).putLongArray("cds", new long[16]); root(p).putLong("swapCd", 0); }
    public static void testTrialWon(ServerPlayer p) { root(p).putBoolean("trial", true); }
    public static void testTitles(ServerPlayer p, int mask) { root(p).putInt("titles", mask); }
    public static int testTaught(ServerPlayer p) { return root(p).getInt("taught"); }
    public static int testLookCode(ServerPlayer p) { return root(p).getInt("look"); }
    public static int testSlot(ServerPlayer p, int twin, int s) { return load(p, twin)[s]; }
    public static int testStone(ServerPlayer p, int twin) { return root(p).getInt(twin == SOL ? "sun" : "moon"); }
    public static int testSocketMask(ServerPlayer p, int twin) { return root(p).getInt("sock" + twin); }
    public static boolean testTrialRunning(ServerPlayer p) { return trialRunning(p); }
    public static void testXp(ServerPlayer p, int twin, float amt) { addXp(p, twin, amt); }
    public static void testEndEclipse(ServerPlayer p) { root(p).putLong("eclipse", 0); }
    public static boolean testVeilUp(ServerPlayer p) { return root(p).getLong("veil") > now(p); }
    public static boolean testDoubleUp(ServerPlayer p) { return root(p).getLong("double") > now(p); }
    public static long testSwapCd(ServerPlayer p) { return root(p).getLong("swapCd") - now(p); }
}
