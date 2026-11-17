package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.entity.AddonBoss;
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
    public static final int NONE = 0, REAPER = 1, TALONS = 2, SPINE = 3, TITAN = 4, LANCE = 5;
    public static final String[] FORMS = {"", "Reaper Edge", "Rending Talons", "Spine Launcher", "Titan Fist", "Bone Lance"};
    public static final int HUNTER = 1, BRUTE = 2, HIVE = 3;
    public static final String[] STRAINS = {"", "Hunter", "Brute", "Hive"};
    public static final int STRAIN_AT = 6;   // evolution points spent before a Strain can settle

    // ------------------------------------------------------------------ the evolution tree
    public static final int LEAP = 0, WALL = 1, GLIDE = 2, METEOR = 3, F_SPINE = 4, F_LANCE = 5, F_BULWARK = 6, F_HIDE = 7,
            HONED = 8, GULLET = 9, DIGEST = 10, RAVENOUS = 11, CATACLYSM = 12;
    /** {branch (0 Predator, 1 Arsenal, 2 Hunger), max rank, cost per rank} */
    public static final int[][] NODES = {
            {0, 3, 1}, {0, 2, 2}, {0, 2, 2}, {0, 3, 2},
            {1, 1, 1}, {1, 1, 1}, {1, 1, 2}, {1, 1, 2}, {1, 3, 2},
            {2, 3, 1}, {2, 3, 1}, {2, 3, 2}, {2, 3, 3}};
    public static final String[] NODE_NAMES = {"Leap", "Wall Runner", "Glider", "Meteor Fall",
            "Spine Launcher", "Bone Lance", "Bulwark", "Chitin Hide", "Honed Edges",
            "Deep Gullet", "Rapid Digestion", "Ravenous", "Cataclysm"};
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
            "Ultimates +20% damage, -10% biomass."};
    static final int[] ULT_COST = {25, 30, 40};   // Marrow Barrage, Grasping Field, Spine Storm

    private static final UUID M_DMG = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900001");
    private static final UUID M_SPD = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900002");
    private static final UUID M_REACH = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900003");
    private static final UUID M_ARMOR = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900004");
    private static final UUID M_TOUGH = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900005");
    private static final UUID M_KB = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900006");
    private static final UUID M_MOVE = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900007");
    private static final UUID M_FRENZY = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900008");
    private static final UUID M_HEALTH = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a900009");
    private static final UUID ROOTED = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a90000a");

    /** Per arm form: {added attack damage, added attack speed (vanilla fist: 1 / 4), added reach}. */
    private static final double[][] FORM_STATS = {{0, 0, 0}, {8, -2.4, 1.0}, {4, -1.0, 0}, {2, -1.5, 0}, {11, -3.2, 0}, {6, -2.8, 2.5}};

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

    private static final Map<UUID, Boolean> DIRTY = new HashMap<>();
    static void dirty(Player p) { DIRTY.put(p.getUUID(), true); }

    /** Add biomass (Ravenous and Hive add more); past full, it spills into Frenzy. */
    static void feed(ServerPlayer p, float amount) {
        if (amount <= 0) return;
        amount *= 1f + 0.25f * rank(p, RAVENOUS) + (strain(p) == HIVE ? 0.2f : 0f);
        CompoundTag r = root(p);
        float b = r.getFloat("bio") + amount;
        if (b > 100) {
            if (!frenzied(p)) r.putFloat("frenzy", Math.min(100, r.getFloat("frenzy") + (b - 100) * 1.5f));
            b = 100;
        }
        r.putFloat("bio", b);
        if (r.getFloat("frenzy") >= 100 && !frenzied(p)) frenzyStart(p);
        dirty(p);
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

    static boolean isBoss(LivingEntity t) { return t instanceof AddonBoss || t.getType().is(Tags.EntityTypes.BOSSES); }

    static boolean foe(ServerPlayer p, LivingEntity t) {
        return t != p && t.isAlive() && !(t instanceof Player) && !(t instanceof ShadowSoldier s && p.getUUID().equals(s.ownerUUID()));
    }

    static float ultBoost(ServerPlayer p) { return (1f + 0.2f * rank(p, CATACLYSM)) * (strain(p) == HIVE ? 1.25f : 1f) * (frenzied(p) ? 1.2f : 1f); }

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
        double range = strain(p) == HUNTER ? 8 : 5;
        LivingEntity t = Kit.aimed(p, range, 25);
        if (t == null || !foe(p, t)) {
            tendril(sl, handPos(p), Kit.aim(p, range), 6);
            p.displayClientMessage(Component.literal("Nothing in reach to consume").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Vec3 at = mid(t);
        tendril(sl, handPos(p), at, 8);
        sound(sl, p.position(), SoundEvents.SLIME_ATTACK, 1.0f, 0.6f);
        if (strain(p) == HUNTER && p.distanceTo(t) > 2.5) {   // the Hunter strain throws itself at its prey
            Vec3 d = t.position().subtract(p.position());
            p.setDeltaMovement(d.normalize().scale(Math.min(1.6, d.length() * 0.28)).add(0, 0.25, 0));
            p.hurtMarked = true;
        }
        int gullet = rank(p, GULLET);
        float pct = t.getHealth() / Math.max(1f, t.getMaxHealth());
        boolean devour = !isBoss(t) && (pct <= 0.30f + 0.08f * gullet || t.getHealth() <= 6 + 2 * gullet);
        if (!devour) {
            t.invulnerableTime = 0;
            AstaFx.damage(p, t, power * 1.2f, "consume");
            Fx.impact(sl, RED, at, 0.9f);
            feed(p, 5);
            p.heal(1);
            return;
        }
        // dragged in, wrapped up and devoured
        float maxHp = t.getMaxHealth();
        Kit.push(t, p.position().subtract(t.position()), 0.6, 0.2);
        AstaFx.later(5, () -> {
            if (!t.isAlive() || !alive(p)) return;
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
            int ep = maxHp >= 40 ? 2 : 1;
            r.putInt("ep", r.getInt("ep") + ep);
            float heal = (3 + maxHp * 0.15f) * (1f + 0.3f * rank(p, DIGEST)) + (frenzied(p) ? 4 : 0);
            p.heal(heal);
            feed(p, Math.min(40f, 8f + maxHp * 0.6f));
            p.displayClientMessage(Component.literal("Devoured  +" + ep + " evolution").withStyle(ChatFormatting.RED), true);
            dirty(p);
        });
    }

    // ================================================================== 2. Evolution (the page)
    public static void evolution(ServerPlayer p, float power) { sync(p, true); }

    // ================================================================== 3. Marrow Barrage
    /** Ten bone lances fire from your arm in a fan over a second, each running through everything for 20 blocks. */
    public static void marrowBarrage(ServerPlayer p, float power) {
        if (!spend(p, ultCost(p, 0))) { noBiomass(p, 0); return; }
        ServerLevel sl = p.serverLevel();
        float dmg = power * ultBoost(p);
        sound(sl, p.position(), SoundEvents.TRIDENT_THROW, 1.0f, 0.6f);
        for (int k = 0; k < 10; k++) {
            final int n = k;
            AstaFx.later(k * 2, () -> {
                if (!alive(p)) return;
                Vec3 look = p.getLookAngle();
                double yaw = Math.toRadians(((n % 2 == 0) ? 1 : -1) * (n * 2.2));
                Vec3 dir = rotY(look, yaw).add(0, (n % 3 - 1) * 0.04, 0).normalize();
                Vec3 from = handPos(p);
                Vec3 end = clip(sl, p, from, from.add(dir.scale(20)));
                spineFly(sl, from, dir, end.distanceTo(from), 2.5f, 1.4f);
                sound(sl, from, SoundEvents.ARROW_SHOOT, 0.6f, 0.5f + n * 0.05f);
                for (LivingEntity t : Kit.along(sl, p, from, end, 0.7)) {
                    if (!foe(p, t)) continue;
                    int delay = (int) (mid(t).distanceTo(from) / 2.5);
                    AstaFx.later(delay, () -> {
                        if (!t.isAlive() || !alive(p)) return;
                        t.invulnerableTime = 0;
                        AstaFx.damage(p, t, dmg, "marrow_barrage");
                        Fx.spark(sl, BONE, mid(t), 0.7f);
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
        LivingEntity aimed = Kit.aimed(p, 16, 10);
        Vec3 aim = aimed != null && foe(p, aimed) ? aimed.position().add(0, 0.5, 0) : Kit.aim(p, 16);
        Vec3 c = ground(sl, p, aim);
        float r = 5f, dmg = power * 0.35f * ultBoost(p);
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
                    if (to.horizontalDistance() > 0.8 && !isBoss(t)) Kit.push(t, new Vec3(to.x, 0, to.z), 0.18, 0);
                    tendril(sl, new Vec3(t.getX(), c.y, t.getZ()), mid(t), 6);
                    t.invulnerableTime = 0;
                    AstaFx.damage(p, t, dmg, "grasping_field");
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
     *  8 blocks of you. */
    public static void spineStorm(ServerPlayer p, float power) {
        if (!spend(p, ultCost(p, 2))) { noBiomass(p, 2); return; }
        ServerLevel sl = p.serverLevel();
        float dmg = power * ultBoost(p);
        int waves = strain(p) == HIVE ? 4 : 3;
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
                    AstaFx.damage(p, t, dmg, "spine_storm");
                    Fx.spark(sl, BONE, mid(t), 0.6f);
                }
            });
        }
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
        if (f < NONE || f > LANCE) return;
        CompoundTag r = root(p);
        int cur = r.getInt("form");
        if (!forced && frenzied(p) && f != cur) {
            p.displayClientMessage(Component.literal("The frenzy won't let go of your arm").withStyle(ChatFormatting.DARK_RED), true);
            return;
        }
        if (f == cur) f = NONE;   // picking the form you have retracts it
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
            Vec3 h = handPos(p);
            FxSend.ring(sl, RED | FxPacket.KEEP_SHAPE, h, 0.1f, 0.7f, 0.06f, 6);
            sound(sl, p.position(), SoundEvents.SLIME_SQUISH, 0.9f, 0.6f);
            sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_NETHERITE, 0.6f, 0.6f);
        }
        applyStats(p);
        dirty(p);
    }

    public static void toggleBulwark(ServerPlayer p) {
        CompoundTag r = root(p);
        boolean on = !r.getBoolean("bulwark");
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
        int st = on ? strain(p) : 0;
        boolean fr = on && frenzied(p);
        setMod(p, Attributes.ATTACK_DAMAGE, M_DMG, "Parasite arm", s[0], AttributeModifier.Operation.ADDITION);
        setMod(p, Attributes.ATTACK_SPEED, M_SPD, "Parasite arm", s[1] + (fr ? 0.6 : 0) + (st == HUNTER && (f == TALONS || f == REAPER) ? 0.3 : 0), AttributeModifier.Operation.ADDITION);
        setMod(p, ForgeMod.ENTITY_REACH.get(), M_REACH, "Parasite arm", s[2], AttributeModifier.Operation.ADDITION);
        boolean hd = on && hide(p), bw = on && bulwark(p);
        setMod(p, Attributes.ARMOR, M_ARMOR, "Parasite armour", (hd ? 6 : 0) + (bw ? 2 : 0), AttributeModifier.Operation.ADDITION);
        setMod(p, Attributes.ARMOR_TOUGHNESS, M_TOUGH, "Parasite armour", hd ? 3 : 0, AttributeModifier.Operation.ADDITION);
        setMod(p, Attributes.KNOCKBACK_RESISTANCE, M_KB, "Parasite armour", (hd ? 0.3 : 0) + (st == BRUTE ? 0.2 : 0), AttributeModifier.Operation.ADDITION);
        setMod(p, Attributes.MOVEMENT_SPEED, M_MOVE, "Parasite stride", (hd ? -0.08 : 0) + (st == HUNTER ? 0.2 : 0) + (fr ? 0.25 : 0), AttributeModifier.Operation.MULTIPLY_BASE);
        setMod(p, Attributes.ATTACK_DAMAGE, M_FRENZY, "Frenzy", fr ? 0.4 : 0, AttributeModifier.Operation.MULTIPLY_TOTAL);
        setMod(p, Attributes.MAX_HEALTH, M_HEALTH, "Brute strain", st == BRUTE ? 6 : 0, AttributeModifier.Operation.ADDITION);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    // ================================================================== the client's requests
    public static void handle(ServerPlayer p, int action, int arg) {
        boolean shadow = action >= 20;
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
            case 20, 21, 22, 23, 24 -> shadowCommand(p, action - 20);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ the Spine Launcher
    private static final Map<UUID, Long> SPINE_CD = new HashMap<>();

    static void fireSpine(ServerPlayer p) {
        if (form(p) != SPINE) return;
        long now = p.serverLevel().getGameTime();
        Long next = SPINE_CD.get(p.getUUID());
        if (next != null && now < next) return;
        SPINE_CD.put(p.getUUID(), now + (frenzied(p) ? 5 : 8));
        ServerLevel sl = p.serverLevel();
        float dmg = 6f * (1f + 0.15f * rank(p, HONED)) * (frenzied(p) ? 1.4f : 1f);
        int n = strain(p) == HIVE ? 3 : 1;
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
                t.hurt(p.damageSources().playerAttack(p), dmg);
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
        float radius = (3.5f + (float) Math.min(3, fell * 0.15)) * (strain(p) == BRUTE ? 1.5f : 1f);
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
            t.hurt(p.damageSources().playerAttack(p), dmg);
            Kit.push(t, t.position().subtract(c), 0.9, 0.45);
        }
    }

    // ------------------------------------------------------------------ evolution and strains
    static void buy(ServerPlayer p, int node) {
        if (node < 0 || node >= NODES.length) return;
        int[] rk = ranks(p);
        CompoundTag r = root(p);
        if (rk[node] >= NODES[node][1]) return;
        int cost = NODES[node][2];
        if (r.getInt("ep") < cost) { p.displayClientMessage(Component.literal("Not enough evolution points").withStyle(ChatFormatting.RED), true); return; }
        r.putInt("ep", r.getInt("ep") - cost);
        r.putInt("spent", r.getInt("spent") + cost);
        rk[node]++;
        r.putIntArray("ranks", rk);
        sound(p.serverLevel(), p.position(), SoundEvents.SLIME_SQUISH, 1.0f, 0.5f);
        sound(p.serverLevel(), p.position(), SoundEvents.PLAYER_LEVELUP, 0.5f, 0.6f);
        FxSend.ring(p.serverLevel(), RED | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.3f, 1.6f, 0.06f, 8);
        applyStats(p);
        sync(p, true);
    }

    static void chooseStrain(ServerPlayer p, int s) {
        CompoundTag r = root(p);
        if (s < HUNTER || s > HIVE || r.getInt("strain") != 0 || r.getInt("spent") < STRAIN_AT) return;
        r.putInt("strain", s);
        sound(p.serverLevel(), p.position(), SoundEvents.RAVAGER_ROAR, 0.7f, 0.8f);
        FxSend.ring(p.serverLevel(), RED | FxPacket.KEEP_SHAPE, p.position().add(0, 1, 0), 0.4f, 3.0f, 0.1f, 12);
        p.displayClientMessage(Component.literal("The strain settles: " + STRAINS[s]).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
        applyStats(p);
        sync(p, true);
    }

    // ------------------------------------------------------------------ the shadow wheel (System Grimoire)
    static void shadowCommand(ServerPlayer p, int which) {
        var b = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
        if (b == null || !b.is(AddonItemRegistry.SHADOW_GRIMOIRE.get())) return;
        ServerLevel sl = p.serverLevel();
        List<ShadowSoldier> out = ShadowArmy.out(p);
        switch (which) {
            case 0 -> {   // Hunt: every shadow at your side goes for what you look at
                LivingEntity t = Kit.aimed(p, 28, 12);
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
        if (now % 20 == 0) {
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
        // the Bulwark (and the frenzy's own bite is not blocked)
        if (t instanceof ServerPlayer p && equipped(p) && bulwark(p) && !src.is(DamageTypeTags.BYPASSES_ARMOR) && src.getEntity() != null) {
            Vec3 from = src.getSourcePosition() != null ? src.getSourcePosition() : src.getEntity().position();
            Vec3 to = from.subtract(p.position()).multiply(1, 0, 1);
            boolean front = to.lengthSqr() > 1e-4 && to.normalize().dot(p.getLookAngle().multiply(1, 0, 1).normalize()) > 0.2;
            e.setAmount(e.getAmount() * (front ? (strain(p) == BRUTE ? 0.45f : 0.55f) : 0.85f));
            if (front) {
                FxSend.ring(p.serverLevel(), RED | FxPacket.KEEP_SHAPE, p.getEyePosition().add(p.getLookAngle().scale(0.6)).add(0, -0.4, 0), 0.1f, 0.8f, 0.05f, 6);
                if (src.getDirectEntity() instanceof LivingEntity a && a != p && a.distanceTo(p) < 4) {   // the spines cut whoever struck it
                    AstaFx.later(1, () -> { if (a.isAlive()) { a.invulnerableTime = 0; a.hurt(p.damageSources().thorns(p), 2f); } });
                }
            }
        }
        // the arm forms
        if (!(src.getEntity() instanceof ServerPlayer p) || src.getDirectEntity() != p || !src.is(DamageTypes.PLAYER_ATTACK)) return;
        if (!equipped(p)) return;
        int f = form(p);
        if (f == NONE) return;
        float mult = 1f + 0.15f * rank(p, HONED);
        int st = strain(p);
        if (st == HUNTER && (f == TALONS || f == REAPER)) mult *= 1.25f;
        if (st == BRUTE && (f == TITAN || f == LANCE)) mult *= 1.25f;
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
                    AstaFx.later(1, () -> { if (o.isAlive()) { o.invulnerableTime = 0; o.hurt(p.damageSources().playerAttack(p), dmg * 0.6f); } });
                }
                FxSend.slash(sl, Fx.STEEL, p.getEyePosition().add(dir.scale(1.5)).add(0, -0.4, 0), dir, new Vec3(0, 1, 0), 1.2f, 2.6f, 6, 0);
            }
            case TALONS -> {   // bleeding
                for (int k = 1; k <= 3; k++) {
                    AstaFx.later(k * 20, () -> {
                        if (!t.isAlive() || !alive(p)) return;
                        t.invulnerableTime = 0;
                        t.hurt(p.damageSources().playerAttack(p), Math.max(1f, dmg * 0.15f));
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
                    AstaFx.later(1, () -> { if (o.isAlive()) { o.invulnerableTime = 0; o.hurt(p.damageSources().playerAttack(p), dmg * 0.4f); Kit.push(o, o.position().subtract(c), 0.6, 0.3); } });
                }
            }
            case LANCE -> {   // it runs on through to what stands behind
                Vec3 a = mid(t), b = a.add(dir.scale(3));
                FxSend.spike(sl, BONE, a, dir, 3.0f, 0.12f, 5, 1, FxPacket.EXTEND);
                for (LivingEntity o : Kit.along(sl, p, a, b, 0.8)) {
                    if (o == t || !foe(p, o)) continue;
                    AstaFx.later(1, () -> { if (o.isAlive()) { o.invulnerableTime = 0; o.hurt(p.damageSources().playerAttack(p), dmg * 0.5f); } });
                }
            }
            default -> { }
        }
    }

    /** Kills feed the strain; the Hive's kills burst into spines. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || !equipped(p) || e.getEntity() == p) return;
        LivingEntity t = e.getEntity();
        feed(p, Math.min(10f, 1f + t.getMaxHealth() * 0.2f));
        if (strain(p) == HIVE && !(t instanceof Player)) {
            ServerLevel sl = p.serverLevel();
            Vec3 c = mid(t);
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3;
                spineFly(sl, c, new Vec3(Math.cos(a), 0.15, Math.sin(a)), 4, 1.5f, 0.7f);
            }
            for (LivingEntity o : Kit.near(sl, p, c, 4)) {
                if (!foe(p, o) || o == t) continue;
                AstaFx.later(2, () -> { if (o.isAlive()) { o.invulnerableTime = 0; o.hurt(p.damageSources().playerAttack(p), 4f); } });
            }
        }
    }

    /** Falls hurt far less (Leap more), not at all mid-slam or after a glide. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !equipped(p)) return;
        CompoundTag r = root(p);
        long now = p.serverLevel().getGameTime();
        if (r.contains("slam") || now < r.getLong("glide")) { e.setDistance(0); e.setDamageMultiplier(0); return; }
        e.setDistance(e.getDistance() * (0.5f - 0.1f * rank(p, LEAP)));
    }

    // ================================================================== to the client
    public static void sync(ServerPlayer p, boolean open) {
        CompoundTag r = root(p);
        AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ParasitePackets.Sync(equipped(p), r.getFloat("bio"), r.getFloat("frenzy"),
                frenzied(p), r.getInt("ep"), r.getInt("spent"), r.getInt("devoured"), r.getInt("form"), r.getBoolean("bulwark"), r.getBoolean("hide"),
                r.getInt("strain"), ranks(p), open));
        // everyone round sees the forms on your arms
        AddonNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new ParasitePackets.Look(p.getId(), equipped(p) ? r.getInt("form") : 0, equipped(p) && r.getBoolean("bulwark"), equipped(p) && r.getBoolean("hide"), frenzied(p)));
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
}
