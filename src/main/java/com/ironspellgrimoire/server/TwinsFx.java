package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;
import static com.ironspellgrimoire.server.TwinsArts.*;
import static com.ironspellgrimoire.server.TwinsTrial.*;

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
    /** Each mask's three sockets, and the three cuts a stone can take in each: [twin][socket][cut]. */
    public static final String[][] SOCKET_PLACE = {{"Brow", "Left cheek", "Right cheek"}, {"Brow", "Tear", "Crescent"}};
    public static final String[][][] SOCKET_NAME = {
            {{"Heartfire", "Kindling", "Sunwell"}, {"Ember", "Blazing Rush", "Pyre Heart"}, {"Blaze", "Wide Flare", "Daystar"}},
            {{"Stillwater", "Moonglass", "Hush"}, {"Moonlit Tear", "Silver Step", "Night Veil"}, {"Tide", "Crescent Edge", "Starsight"}}};
    public static final String[][][] SOCKET_DESC = {
            {{"+10% damage from Sol's arts", "Sol's arts set foes burning", "Sol's arts cost 25% less mana"},
             {"Rush lasts 2 s longer", "Sol's Rush: +35% damage", "Kills with Sol worn heal 2 hearts"},
             {"Sol's Resonance burst +30%", "The sun flare reaches 2 further", "+8% attack damage with Sol worn"}},
            {{"Nyx's arts cool down 15% faster", "+10% damage from Nyx's arts", "Nyx's slows are 15% stronger"},
             {"Moonstep and Veil heal 2 hearts", "Moonstep reaches 10 blocks", "After a Moonstep, a blow misses"},
             {"Nyx charges Resonance 30% faster", "Nyx's crescent wave +30%", "+8% speed with Nyx worn"}}};
    /** Art mastery: casts for Mastery I (+15% damage), II (20% shorter cooldown) and III (the Perfected form). */
    public static final int[] MASTERY_AT = {30, 100, 250};
    public static final String[][] ART_PERF = {
            {"Perfected: the burn lasts 5 s.", "Perfected: five bolts.", "Perfected: a wider beam that burns.", "Perfected: bounces between eight foes.",
             "Perfected: 12 s, and a wider ring.", "Perfected: a second blast after the slam.", "Perfected: a second, wider wave.", "Perfected: twelve spears."},
            {"Perfected: frost bursts where you leave and land.", "Perfected: slows by half.", "Perfected: seven crescents.", "Perfected: five blows strike twice.",
             "Perfected: two blows miss.", "Perfected: eighteen stars.", "Perfected: holds for 8 s.", "Perfected: a wider tear."}};
    public static final int ULT_LV = 40, NOON = 200, EVENTIDE = 160;
    public static final String[] ULT_NAME = {"High Noon", "Eventide"};
    public static final String[] ULT_DESC = {"10 s: a blazing aura, Sol's cooldowns cleared, every hit burns.",
            "8 s: foes near you crawl, you run 40% faster, three blows miss."};
    public static final int ECHO_LV = 15;
    public static final String[] TITLES = {"Dawnchild", "Nightborn", "the Balanced", "Eclipse-Touched"};
    public static final String[] TITLE_HOW = {"Sol reaches level 25", "Nyx reaches level 25", "Both twins reach level 20", "Beat the Twin Wardens"};
    public static final String[] TITLE_GIVES = {"+3% attack damage", "+3% speed", "+25 max mana", "+2 armour"};
    public static final int SWAP_CD = 60, RUSH = 80, ECLIPSE = 400, TRIAL = 4800;
    static final int PAL_SOL = Fx.GOLD, PAL_SOL2 = Fx.FIRE, PAL_NYX = Fx.SILVER, PAL_NYX2 = Fx.ICE;

    // ------------------------------------------------------------------ the synced values (TwinsPackets.Sync, client/TwinsClient)
    public static final int V_EQ = 0, V_ACTIVE = 1, V_LV = 2, V_XP = 4, V_NEED = 6, V_RES = 8, V_SWAP = 9, V_RUSH = 10, V_LOAD = 11, V_SEL = 19,
            V_CD = 20, V_SUN = 36, V_MOON = 37, V_SOCK = 38, V_TRIAL = 40, V_TRIAL_LEFT = 41, V_ECLIPSE = 42, V_TITLES = 43, V_TITLE = 44, V_DAY = 45,
            V_CORONA = 46, V_VEIL = 47, V_DOUBLE = 48, V_MIRROR = 49, V_USES = 50, V_CUT = 66, V_NOON = 72, V_EVENTIDE = 73, V_ECHO = 74, SIZE = 75;

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
    /** The cut set in a socket (0-2; a stone set before cuts existed is cut 0). */
    static int cut(Player p, int twin, int s) { int[] c = root(p).getIntArray("cut" + twin); return c.length == 3 ? Math.max(0, Math.min(2, c[s])) : 0; }
    /** A socket's perk is active: the stone set, in that cut. */
    static boolean perk(Player p, int twin, int s, int c) { return socket(p, twin, s) && cut(p, twin, s) == c; }
    static int uses(Player p, int twin, int art) { int[] u = root(p).getIntArray("uses"); return u.length == 16 ? u[twin * 8 + art] : 0; }
    /** An art's mastery (0-3) from how often it's been cast. */
    public static int mastery(int uses) { return uses >= MASTERY_AT[2] ? 3 : uses >= MASTERY_AT[1] ? 2 : uses >= MASTERY_AT[0] ? 1 : 0; }
    static boolean noon(Player p) { return root(p).getLong("noon") > now(p); }
    static boolean eventide(Player p) { return root(p).getLong("eventide") > now(p); }
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
        for (int s = 0; s < 3; s++) if (SOCKET_LV[s] == lv) p.sendSystemMessage(Component.literal("  A socket opened in " + TWIN[twin] + "'s mask: the " + SOCKET_PLACE[twin][s].toLowerCase() + " (three cuts to choose from)").withStyle(ChatFormatting.GRAY));
        if (lv == ECHO_LV) p.sendSystemMessage(Component.literal("  " + TWIN[twin] + "'s Echo: while it rests, it leaves your hip to fight beside you").withStyle(ChatFormatting.GRAY));
        if (lv == ULT_LV) p.sendSystemMessage(Component.literal("  " + TWIN[twin] + "'s ultimate: " + ULT_NAME[twin] + " - " + ULT_DESC[twin]).withStyle(ChatFormatting.GRAY));
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
        if (twin == SOL && perk(p, SOL, 0, 0)) m *= 1.1f;
        if (twin == NYX && perk(p, NYX, 0, 1)) m *= 1.1f;
        if (eclipse(p)) m *= 1.15f;
        return m;
    }

    /** An art's hit: damage (the twin learns a little from it), never eaten by the target's hurt-frames. */
    static void hit(ServerPlayer p, LivingEntity t, float dmg, int twin) {
        if (!foe(p, t)) return;
        t.invulnerableTime = 0;
        AstaFx.damage(p, t, dmg, "twin_arts");
        addXp(p, twin, dmg * 0.1f * weigh(p, twin));
        if (twin == SOL && !burning && (perk(p, SOL, 0, 1) || noon(p))) burn(p, t, 2, dmg * 0.15f);   // Kindling, High Noon: Sol's hits burn
    }
    static boolean burning;

    static boolean foe(ServerPlayer p, LivingEntity t) { return t != p && t.isAlive() && !(t instanceof Player op && !com.ironspellgrimoire.AddonConfig.mayHurt(p, op)) && !t.isAlliedTo(p); }

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
        int rush = RUSH + (in == SOL && perk(p, SOL, 1, 0) ? 40 : 0);
        r.putLong("rush", t + rush);
        if (in == SOL) TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, RUSH_DMG, "Sol's Rush", perk(p, SOL, 1, 1) ? 0.35 : 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, rush);
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
            float rad = 3 + 3 * k + (perk(p, SOL, 2, 1) ? 2 : 0), dmg = (4 + 14 * k) * m * (perk(p, SOL, 2, 0) ? 1.3f : 1f);
            FxSend.ring(sl, PAL_SOL, p.position().add(0, 0.1, 0), 0.4f, rad, 0.5f, 12);
            FxSend.ring(sl, PAL_SOL2, p.position().add(0, 0.6, 0), 0.2f, rad * 0.8f, 0.25f, 10);
            Fx.pillar(sl, PAL_SOL, p.position(), 3f + 2 * k, 1.2f, 12);
            for (LivingEntity e : enemies(sl, p, p.getBoundingBox().inflate(rad))) if (e.distanceTo(p) <= rad) hit(p, e, dmg, SOL);
            OverdriveFx.play(sl, p.position(), "sun_flare", 1f, 1f);
        } else {
            Vec3 d = flat(p.getLookAngle()), up = new Vec3(0, 1, 0);
            float reach = 6 + 4 * k, dmg = (4 + 12 * k) * m * (perk(p, NYX, 2, 1) ? 1.3f : 1f);
            for (int i = -1; i <= 1; i++) {
                Vec3 dir = rotY(d, i * 0.45);
                FxSend.crescent(sl, PAL_NYX, p.position().add(0, 1, 0).add(dir.scale(1.2)), dir, rightOf(dir), 1.6f, 2.4f, 10, reach / 10f);
            }
            for (LivingEntity e : enemies(sl, p, p.getBoundingBox().inflate(reach))) {
                Vec3 to = e.position().subtract(p.position());
                if (to.length() <= reach && flat(to).dot(d) > 0.4) {
                    hit(p, e, dmg, NYX);
                    slow(p, e, -0.3, 60);
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
            SLOW = UUID.fromString("7a3e2c10-5b1d-4c6e-9f02-1b2c7a9100a9"), STONE_ID = UUID.fromString("7a3e2c10-5b1d-4c6e-9f02-1b2c7a9100aa"),
            EVE_SPD = UUID.fromString("7a3e2c10-5b1d-4c6e-9f02-1b2c7a9100ab"), EVE_SLOW = UUID.fromString("7a3e2c10-5b1d-4c6e-9f02-1b2c7a9100ac");

    /** A Nyx slow (Hush makes it stronger). */
    static void slow(ServerPlayer p, LivingEntity e, double by, int ticks) { slow(e, by - (perk(p, NYX, 0, 2) ? 0.15 : 0), ticks); }

    static void slow(LivingEntity e, double by, int ticks) {
        TimedModifiers.apply(e, Attributes.MOVEMENT_SPEED, SLOW, "Twins' frost", by, AttributeModifier.Operation.MULTIPLY_TOTAL, ticks);
    }

    // ------------------------------------------------------------------ the ultimates
    public static boolean canNoon(Player p) { return active(p) == SOL && level(p, SOL) >= ULT_LV; }
    public static boolean canEventide(Player p) { return active(p) == NYX && level(p, NYX) >= ULT_LV; }

    /** Sol's High Noon: 10 s of a blazing aura; Sol's cooldowns cleared (and halved while it lasts); every Sol hit burns. */
    public static void highNoon(ServerPlayer p, float power) {
        if (!canNoon(p)) { tell(p, "Wear Sol's mask, at level " + ULT_LV); return; }
        ServerLevel sl = p.serverLevel();
        CompoundTag r = root(p);
        r.putLong("noon", now(p) + NOON);
        long[] cd = cds(p);
        for (int i = 0; i < 8; i++) cd[i] = 0;
        r.putLongArray("cds", cd);
        Vec3 at = p.position();
        Fx.pillar(sl, PAL_SOL, at, 7f, 2.2f, 20);
        FxSend.ring(sl, PAL_SOL, at.add(0, 0.1, 0), 0.5f, 7f, 0.8f, 18);
        FxSend.ring(sl, PAL_SOL2, at.add(0, 0.3, 0), 0.3f, 5.5f, 0.4f, 16);
        FxSend.shake(sl, at, 0.5f, 8, 14);
        OverdriveFx.play(sl, at, "sun_flare", 1.5f, 0.6f);
        say(p, SOL, "Look at me.");
        sync(p, false);
    }

    /** Nyx's Eventide: 8 s - foes near you crawl, you run 40% faster, and three blows aimed at you miss. */
    public static void eventide(ServerPlayer p, float power) {
        if (!canEventide(p)) { tell(p, "Wear Nyx's mask, at level " + ULT_LV); return; }
        ServerLevel sl = p.serverLevel();
        CompoundTag r = root(p);
        r.putLong("eventide", now(p) + EVENTIDE);
        r.putLong("veil", now(p) + EVENTIDE);
        r.putInt("veilHits", 3);
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, EVE_SPD, "Eventide", 0.4, AttributeModifier.Operation.MULTIPLY_TOTAL, EVENTIDE);
        Vec3 at = p.position();
        FxSend.ring(sl, Fx.VOID, at.add(0, 0.1, 0), 0.5f, 8f, 0.6f, 20);
        FxSend.ring(sl, PAL_NYX, at.add(0, 0.2, 0), 0.4f, 8f, 1.4f, 20);
        Fx.pillar(sl, PAL_NYX, at, 5f, 1.6f, 18);
        OverdriveFx.play(sl, at, "eclipse_toll", 1.2f, 1.4f);
        say(p, NYX, "Hush now. Everything is slow.");
        sync(p, false);
    }

    /** Every tick while an ultimate lasts. */
    static void ultTick(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        if (noon(p) && p.tickCount % 20 == 0) {
            float m = mult(p, SOL);
            FxSend.ring(sl, PAL_SOL, p.position().add(0, 0.1, 0), 4.6f, 5f, 0.7f, 14);
            FxSend.spike(sl, PAL_SOL, p.getEyePosition().add(0, 1.4, 0), new Vec3(0, 1, 0), 0.9f, 0.6f, 20, 0, FxPacket.HOLD);
            for (LivingEntity x : enemies(sl, p, p.getBoundingBox().inflate(5))) if (x.distanceTo(p) <= 5.2) hit(p, x, 6 * m, SOL);
        }
        if (eventide(p) && p.tickCount % 10 == 0) {
            FxSend.ring(sl, PAL_NYX, p.position().add(0, 0.1, 0), 7.6f, 8f, 0.3f, 10);
            for (LivingEntity x : enemies(sl, p, p.getBoundingBox().inflate(8))) if (x.distanceTo(p) <= 8.2)
                TimedModifiers.apply(x, Attributes.MOVEMENT_SPEED, EVE_SLOW, "Eventide", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL, 15);
        }
    }

    // ------------------------------------------------------------------ the Echo: the resting twin, fighting beside you
    static final java.util.Map<UUID, Integer> ECHOES = new java.util.HashMap<>();

    /** Each second: the resting twin's Echo comes out in a fight (level 15+, not fused), and goes back to the hip after. */
    static void echo(ServerPlayer p) {
        CompoundTag r = root(p);
        int rest = 1 - active(p);
        boolean want = com.ironspellgrimoire.AddonConfig.twinEcho() && equipped(p) && !eclipse(p) && level(p, rest) >= ECHO_LV && now(p) - r.getLong("combat") < 100 && p.isAlive();
        ServerLevel sl = p.serverLevel();
        Integer id = ECHOES.get(p.getUUID());
        var e = id == null ? null : sl.getEntity(id);
        com.ironspellgrimoire.entity.TwinEcho echo = e instanceof com.ironspellgrimoire.entity.TwinEcho te && te.isAlive() ? te : null;
        if (want && (echo == null || echo.form() != maskForm(p, rest))) {
            if (echo != null) echo.discard();
            echo = new com.ironspellgrimoire.entity.TwinEcho(sl, p, maskForm(p, rest));
            sl.addFreshEntity(echo);
            ECHOES.put(p.getUUID(), echo.getId());
            Fx.spark(sl, rest == SOL ? PAL_SOL : PAL_NYX, echo.position(), 0.7f);
            if (!r.getBoolean("echoOut")) teach(p, 7, rest, "Let me out - I'll fight too.", "the resting twin's Echo fights beside you in a fight (level 15+), then goes back to your hip");
        } else if (!want && echo != null) {
            Fx.spark(sl, rest == SOL ? PAL_SOL : PAL_NYX, echo.position(), 0.5f);
            echo.discard();
            ECHOES.remove(p.getUUID());
        }
        boolean out = want;
        if (r.getBoolean("echoOut") != out) { r.putBoolean("echoOut", out); look(p, true); }
    }

    /** The Echo's shot: Sol's a bolt of fire, Nyx's a frost needle that slows. Stronger with the resting twin's level. */
    public static void echoShot(ServerPlayer p, com.ironspellgrimoire.entity.TwinEcho echo, LivingEntity t) {
        ServerLevel sl = p.serverLevel();
        int rest = 1 - active(p);
        float dmg = (3 + 0.15f * level(p, rest)) * (float) p.getAttributeValue(AttributeRegistry.SPELL_POWER.get());
        Vec3 from = echo.position(), to = mid(t);
        FxSend.beam(sl, rest == SOL ? PAL_SOL2 : PAL_NYX2, from, to, 0.12f, 5);
        Fx.spark(sl, rest == SOL ? PAL_SOL : PAL_NYX, to, 0.5f);
        hit(p, t, dmg, rest);
        if (rest == NYX) slow(p, t, -0.2, 40);
        OverdriveFx.play(sl, from, rest == SOL ? "sun_flare" : "moon_shimmer", 0.35f, 1.9f);
    }

    /** The Echo's target: the nearest foe within 12 blocks that it can see. */
    public static LivingEntity echoTarget(ServerPlayer p, com.ironspellgrimoire.entity.TwinEcho echo) {
        LivingEntity best = null; double bd = 144;
        for (LivingEntity t : enemies(p.serverLevel(), p, echo.getBoundingBox().inflate(12))) {
            if (!(t instanceof net.minecraft.world.entity.monster.Enemy) && !(t instanceof Mob m && m.getTarget() == p)) continue;
            double dd = t.distanceToSqr(echo);
            if (dd < bd) { bd = dd; best = t; }
        }
        return best;
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
            burning = true;
            try { hit(p, t, per, SOL); } finally { burning = false; }
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
            case 4 -> setStone(p, arg / 100, (arg / 10) % 10, arg % 10);
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

    /** A stone set in a socket in one of its three cuts - or the socket re-cut (one more stone). */
    static void setStone(ServerPlayer p, int twin, int s, int c) {
        if (twin < 0 || twin > 1 || s < 0 || s > 2 || c < 0 || c > 2) return;
        if (socket(p, twin, s) && cut(p, twin, s) == c) return;
        CompoundTag r = root(p);
        String key = twin == SOL ? "sun" : "moon";
        if (level(p, twin) < SOCKET_LV[s] || r.getInt(key) < 1) return;
        r.putInt(key, r.getInt(key) - 1);
        r.putInt("sock" + twin, r.getInt("sock" + twin) | (1 << s));
        int[] cuts = r.getIntArray("cut" + twin);
        if (cuts.length != 3) cuts = new int[3];
        cuts[s] = c;
        r.putIntArray("cut" + twin, cuts);
        ServerLevel sl = p.serverLevel();
        Fx.spark(sl, twin == SOL ? PAL_SOL : PAL_NYX, p.getEyePosition(), 1f);
        OverdriveFx.play(sl, p.position(), twin == SOL ? "twin_rise" : "twin_fall", 0.7f, 1.5f);
        say(p, twin, SOCKET_NAME[twin][s][c] + " is set. " + SOCKET_DESC[twin][s][c] + ".");
        look(p, true);
        sync(p, false);
    }

    static void wear(ServerPlayer p, int t) {
        if (t >= 0 && (t > 3 || (root(p).getInt("titles") & (1 << t)) == 0)) return;
        root(p).putInt("title", t);
        sync(p, false);
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
        float gain = amt * 0.5f * (active(p) == SOL && perk(p, NYX, 2, 0) ? 1.3f : 1f);
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
        int left = Math.max(1, r.getInt("veilHits")) - 1;
        r.putInt("veilHits", left);
        if (left <= 0) r.putLong("veil", 0);
        ServerLevel sl = p.serverLevel();
        if (eventide(p)) {   // Eventide: the blow passes through, no step back
            Fx.flash(sl, p.position().add(0, 1, 0), 0.6f);
            OverdriveFx.play(sl, p.position(), "moon_shimmer", 0.7f, 1.6f);
            tell(p, "Eventide: missed (" + left + " left)");
            return;
        }
        Vec3 back = flat(p.getLookAngle()).scale(-3);
        Vec3 to = clip(sl, p, p.position().add(0, 0.6, 0), p.position().add(0, 0.6, 0).add(back)).add(0, -0.6, 0);
        Fx.flash(sl, p.position().add(0, 1, 0), 0.8f);
        Fx.pillar(sl, PAL_NYX, p.position(), 2.2f, 0.7f, 20);
        p.teleportTo(to.x, to.y, to.z);
        if (perk(p, NYX, 1, 0)) p.heal(4f);
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
        if (active(p) == SOL && perk(p, SOL, 1, 2)) { p.heal(4f); Fx.spark(p.serverLevel(), PAL_SOL2, p.getEyePosition(), 0.6f); }   // Pyre Heart
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
        if (!equipped(p)) { if (p.tickCount % 10 == 0) { look(p, false); if (ECHOES.containsKey(p.getUUID())) echo(p); } return; }
        CompoundTag r = root(p);
        long t = now(p);
        ServerLevel sl = p.serverLevel();
        if (p.tickCount % 10 == 0) look(p, false);
        if (r.getLong("corona") > t && p.tickCount % 20 == 0) {   // Corona: a burning ring each second
            float rad = r.getBoolean("coronaWide") ? 5.5f : 4f;
            FxSend.ring(sl, PAL_SOL, p.position().add(0, 0.1, 0), rad - 0.4f, rad, 0.5f, 12);
            float m = mult(p, SOL);
            for (LivingEntity x : enemies(sl, p, p.getBoundingBox().inflate(rad))) if (x.distanceTo(p) <= rad + 0.2) hit(p, x, 4 * m, SOL);
        }
        ultTick(p);
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
        if (a == SOL && perk(p, SOL, 2, 2)) TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, STONE_ID, "Daystar", 0.08, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
        if (a == NYX && perk(p, NYX, 2, 2)) TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, STONE_ID, "Starsight", 0.08, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
        echo(p);
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
            float gain = 0.5f * (a == SOL && perk(p, NYX, 2, 0) ? 1.3f : 1f);
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
            else { face = maskForm(p, active(p)); hip = r.getBoolean("echoOut") ? -1 : maskForm(p, 1 - active(p)); }
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
        int[] u = r.getIntArray("uses");
        for (int i = 0; i < 16; i++) v[V_USES + i] = u.length == 16 ? u[i] : 0;
        for (int k = 0; k < 2; k++) for (int s = 0; s < 3; s++) v[V_CUT + k * 3 + s] = cut(p, k, s);
        v[V_NOON] = (int) Math.max(0, r.getLong("noon") - t);
        v[V_EVENTIDE] = (int) Math.max(0, r.getLong("eventide") - t);
        v[V_ECHO] = r.getBoolean("echoOut") ? 1 : 0;
        AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new TwinsPackets.Sync(v, open));
    }

    // ------------------------------------------------------------------ test helpers (server/GameTests, UiShotsServer)
    public static void testReset(ServerPlayer p) {
        CompoundTag pd = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        pd.remove(ROOT);
        for (TwinWarden w : p.serverLevel().getEntitiesOfClass(TwinWarden.class, p.getBoundingBox().inflate(96))) w.discard();
        for (var w : p.serverLevel().getEntitiesOfClass(com.ironspellgrimoire.entity.TwinEcho.class, p.getBoundingBox().inflate(96))) w.discard();
        ECHOES.remove(p.getUUID());
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
    public static void testUses(ServerPlayer p, int twin, int art, int n) { int[] u = root(p).getIntArray("uses"); if (u.length != 16) u = new int[16]; u[twin * 8 + art] = n; root(p).putIntArray("uses", u); }
    public static int testUsesOf(ServerPlayer p, int twin, int art) { return uses(p, twin, art); }
    public static int testCut(ServerPlayer p, int twin, int s) { return cut(p, twin, s); }
    public static boolean testPerk(ServerPlayer p, int twin, int s, int c) { return perk(p, twin, s, c); }
    public static void testCombat(ServerPlayer p) { root(p).putLong("combat", now(p)); }
    public static boolean testEchoOut(ServerPlayer p) { return root(p).getBoolean("echoOut"); }
    public static void testEcho(ServerPlayer p) { echo(p); }
    public static boolean testNoon(ServerPlayer p) { return noon(p); }
    public static boolean testEventide(ServerPlayer p) { return eventide(p); }
    public static boolean testVeilUp(ServerPlayer p) { return root(p).getLong("veil") > now(p); }
    public static boolean testDoubleUp(ServerPlayer p) { return root(p).getLong("double") > now(p); }
    public static long testSwapCd(ServerPlayer p) { return root(p).getLong("swapCd") - now(p); }
}
