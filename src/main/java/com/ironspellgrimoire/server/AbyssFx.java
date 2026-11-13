package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * The Lightless Abyss Grimoire (an original grimoire): the magic of the deep sea where no light reaches. The more you
 * use it the deeper you sink - Depth (0-100, a gauge on your screen) - and the deeper you are the heavier everything
 * it does becomes, and the darker the edges of your sight grow. What lives down there answers you: lures of cold
 * light, things with too many arms, a leviathan, jaws.
 * Drawn by client/AbyssFxClient (FxPacket shape 93): black-blue water, teal and cyan glowing points, pale lantern light.
 *
 *  1 Crushing Pressure - the deep presses down where you aim: foes there are crushed and slowed (harder with Depth)
 *  2 Angler's Lure     - a lantern of cold light hangs out ahead; foes nearby are drawn to it, then jaws close on it
 *  3 Abyssal Grasp     - arms rise round the foe you aim at, hold it still and squeeze for three seconds
 *  4 Undertow          - a current rolls forward, carrying foes away - then drags them back under you
 *  5 Abyssal Dive      - you sink into black water and come up where you aim, jaws snapping shut round you
 *  6 Depth Charge      - a slow, sinking orb; after a beat it bursts in a crushing ring
 *  7 Leviathan         - a vast shadow breaches out of the ground along a line, throwing foes into the air
 *  8 Hadal Weight      - the foe you aim at is made so heavy it can't jump or be lifted, its armour bowed (6 s)
 *  9 Bioluminescence   - 20 s mode: you glow like the deep's own creatures - Abyss spells 25% harder, every hit
 *    (mode)              feeds you a little, and the dark at the edge of your sight lifts
 * 10 The Trench        - the ground where you aim falls away into a black trench (only to the eye - nothing is
 *                        broken); all near it are dragged down and weighed, and a great maw rises from it and closes.
 *                        It spends all your Depth: the deeper you were, the harder it bites. Surpassing Limits: wider.
 *
 * Passive "Pressure": every Abyss hit sinks you 4 Depth (Depth slowly rises back toward the surface when you stop).
 * Your Abyss spells hit up to 50% harder with Depth, and from Depth 50 the pressure hardens you: 12% less damage.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class AbyssFx {
    private AbyssFx() {}

    public static final String MODE = "bioluminescence";
    private static final String DEPTH_KEY = "isg_abyss_depth";
    private static final UUID CRUSH = UUID.fromString("a6b55e10-2c41-4d8e-9b1f-7c0de3000001");
    private static final UUID GRASP = UUID.fromString("a6b55e10-2c41-4d8e-9b1f-7c0de3000002");
    private static final UUID HEAVY = UUID.fromString("a6b55e10-2c41-4d8e-9b1f-7c0de3000003");
    private static final UUID HEAVY_A = UUID.fromString("a6b55e10-2c41-4d8e-9b1f-7c0de3000004");
    private static final UUID HEAVY_J = UUID.fromString("a6b55e10-2c41-4d8e-9b1f-7c0de3000005");
    private static final UUID TRENCH = UUID.fromString("a6b55e10-2c41-4d8e-9b1f-7c0de3000006");

    // ================================================================== the client shapes (AbyssFxClient, shape 93)
    private static FxPacket pk(int kind, Vec3 c, int life) {
        FxPacket p = new FxPacket();
        p.shape = 93; p.id = FxSend.nextId(); p.h = kind; p.c = c; p.life = life; p.a1 = (float) (Math.random() * 1000);
        return p;
    }
    private static void send(ServerLevel sl, FxPacket p) { FxSend.sendNear(sl, p.c, p); }

    /** 0 the deep pressing down: a column of dark water and rings sinking. */
    static void pressure(ServerLevel sl, Vec3 c, float r, int life) { FxPacket p = pk(0, c, life); p.r = r; send(sl, p); }
    /** 1 the angler's lantern at c, on its stalk from the hand u; jaws close at {@code snap}. */
    static void lure(ServerLevel sl, Vec3 c, Vec3 u, float r, int snap, int life) { FxPacket p = pk(1, c, life); p.u = u; p.r = r; p.sweep = snap; send(sl, p); }
    /** 2 arms rising round a foe. */
    static void grasp(ServerLevel sl, LivingEntity t, int life) { FxPacket p = pk(2, t.position(), life); p.v = new Vec3(t.getId(), 1, 0); p.r = t.getBbWidth(); p.r2 = t.getBbHeight(); send(sl, p); }
    /** 3 the current rolling from c along d; turns back at {@code turn}. */
    static void undertow(ServerLevel sl, Vec3 c, Vec3 d, float len, int turn, int life) { FxPacket p = pk(3, c, life); p.d = d; p.r = len; p.sweep = turn; send(sl, p); }
    /** 4 sinking into black water at c, rising at u. */
    static void dive(ServerLevel sl, Vec3 c, Vec3 u, int life) { FxPacket p = pk(4, c, life); p.u = u; p.sweep = life / 2; send(sl, p); }
    /** 5 the depth charge: thrown from c, sinking toward u, bursting at {@code burst}. */
    static void charge(ServerLevel sl, Vec3 c, Vec3 u, float r, int burst) { FxPacket p = pk(5, c, burst + 16); p.u = u; p.r = r; p.sweep = burst; send(sl, p); }
    /** 6 the leviathan breaching from c to u. */
    static void leviathan(ServerLevel sl, Vec3 c, Vec3 u, int life) { FxPacket p = pk(6, c, life); p.u = u; send(sl, p); }
    /** 7 a foe weighed down. */
    static void weight(ServerLevel sl, LivingEntity t, int life) { FxPacket p = pk(7, t.position(), life); p.v = new Vec3(t.getId(), 1, 0); p.r = t.getBbWidth(); send(sl, p); }
    /** 8 the bioluminescent glow on a player. */
    static void glow(ServerLevel sl, ServerPlayer pl, int life) { FxPacket p = pk(8, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }
    /** 9 the trench and the maw, closing at {@code bite}. */
    static void trench(ServerLevel sl, Vec3 c, float r, int bite, int life) { FxPacket p = pk(9, c, life); p.r = r; p.sweep = bite; send(sl, p); }
    /** 10 jaws snapping shut at c. */
    static void jaws(ServerLevel sl, Vec3 c, float r, Vec3 face) { FxPacket p = pk(10, c, 14); p.r = r; p.d = face; send(sl, p); }
    /** 11 a little light drifting from c into a player (feeding). */
    static void feed(ServerLevel sl, Vec3 c, ServerPlayer pl) { FxPacket p = pk(11, c, 16); p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }

    // ================================================================== Depth
    public static float depth(ServerPlayer p) { return p.getPersistentData().getFloat(DEPTH_KEY); }

    public static void setDepth(ServerPlayer p, float v) {
        float before = depth(p);
        p.getPersistentData().putFloat(DEPTH_KEY, Math.max(0f, Math.min(100f, v)));
        if ((int) before != (int) depth(p)) sync(p);
    }

    static void sink(ServerPlayer p, float v) {
        float before = depth(p);
        setDepth(p, before + v);
        if (before < 100f && depth(p) >= 100f) p.displayClientMessage(Component.literal("You have reached the bottom - the Trench is at its deepest").withStyle(ChatFormatting.DARK_AQUA), true);
    }

    /** The gauge and the dark on the player's screen (AbyssFxClient, kind 99): a0 = Depth, r = 1 if equipped, r2 = 1 while glowing. */
    static void sync(ServerPlayer p) {
        if (p.connection == null) return;
        FxPacket h = new FxPacket();
        h.shape = 93; h.h = 99; h.a0 = depth(p) + 0.001f; h.r = equipped(p) ? 1 : 0; h.r2 = glowing(p) ? 1 : 0; h.life = 1;
        com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), h);
    }

    public static boolean equipped(ServerPlayer p) {
        var b = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
        return b != null && b.is(com.ironspellgrimoire.registry.AddonItemRegistry.ABYSS_GRIMOIRE.get());
    }

    static boolean glowing(ServerPlayer p) { return Moves.inMode(p, MODE); }

    static void play(ServerLevel sl, Vec3 at, String kind, float vol, float pitch) {
        SoundEvent s = AddonSounds.get(kind);
        if (s != null) sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    /** How much harder the deep makes everything: 1.0 at the surface, 1.5 at the bottom. */
    static float deep(ServerPlayer p) { return 1f + depth(p) / 200f; }

    /** An Abyss hit: Pressure sinks you, Bioluminescence feeds you. */
    static void hit(ServerPlayer p, LivingEntity t, float amt, String id) {
        amt *= deep(p);
        if (glowing(p)) amt *= 1.25f;
        AstaFx.damage(p, t, amt, id);
        sink(p, 4f);
        if (glowing(p)) {
            p.heal(Math.min(1.5f, amt * 0.08f));
            if (p.getRandom().nextFloat() < 0.5f) feed(p.serverLevel(), mid(t), p);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && depth(p) >= 50f && equipped(p)) e.setAmount(e.getAmount() * 0.88f);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 40 == 5) {
            if (equipped(p) || depth(p) > 0) sync(p);
            if (depth(p) > 0 && p.serverLevel().getGameTime() - p.getPersistentData().getLong(DEPTH_KEY + "_t") > 100) setDepth(p, depth(p) - 2f);   // rising back up
        }
    }

    private static void cast(ServerPlayer p) { p.getPersistentData().putLong(DEPTH_KEY + "_t", p.serverLevel().getGameTime()); }

    private static Vec3 look(ServerPlayer p) {
        Vec3 l = flat(p.getLookAngle());
        return l.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : l.normalize();
    }

    // ================================================================== 1. Crushing Pressure
    public static void crushingPressure(ServerPlayer p, float power) {
        cast(p);
        ServerLevel sl = p.serverLevel();
        Vec3 g = InkFx.target(p, 22);
        float r = 3.2f + depth(p) / 40f;
        pressure(sl, g, r, 26);
        play(sl, g, "deep_boom", 1.0f, 0.9f);
        AstaFx.later(6, () -> {
            if (!alive(p)) return;
            for (LivingEntity t : Kit.near(sl, p, g.add(0, 1, 0), r)) {
                TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, CRUSH, "Crushed by the deep", -0.4, AttributeModifier.Operation.MULTIPLY_TOTAL, 70);
                t.setDeltaMovement(t.getDeltaMovement().x * 0.3, -0.6, t.getDeltaMovement().z * 0.3);
                t.hurtMarked = true;
                hit(p, t, power * 0.95f, "crushing_pressure");
            }
            FxSend.shake(sl, g, 0.3f, 4, 14);
        });
    }

    // ================================================================== 2. Angler's Lure
    public static void anglersLure(ServerPlayer p, float power) {
        cast(p);
        ServerLevel sl = p.serverLevel();
        Vec3 lk = look(p);
        final Vec3 c = clip(sl, p, p.getEyePosition(), p.getEyePosition().add(lk.scale(6))).subtract(lk.scale(0.4)).add(0, 0.2, 0);
        final int SNAP = 26;
        lure(sl, c, Kit.hand(p), 8f, SNAP, SNAP + 14);
        play(sl, c, "lure_hum", 1.0f, 1.0f);
        for (int i = 1; i <= 4; i++) {   // drawn in toward the light
            final int k = i;
            AstaFx.later(i * 5, () -> {
                if (!alive(p)) return;
                for (LivingEntity t : Kit.near(sl, p, c, 8)) {
                    Vec3 to = c.subtract(mid(t));
                    if (to.length() < 0.8) continue;
                    Vec3 d = to.normalize().scale(0.32 * GravityFx.give(t) * (1 + k * 0.1));
                    t.setDeltaMovement(d.x, Math.max(t.getDeltaMovement().y, d.y * 0.5), d.z);
                    t.hurtMarked = true;
                }
            });
        }
        AstaFx.later(SNAP, () -> {   // and the jaws close on it
            if (!alive(p)) return;
            jaws(sl, c, 2.2f, lk.scale(-1));
            for (LivingEntity t : Kit.near(sl, p, c, 2.6)) hit(p, t, power * 1.3f, "anglers_lure");
            play(sl, c, "jaws", 1.2f, 1.0f);
        });
    }

    // ================================================================== 3. Abyssal Grasp
    public static void abyssalGrasp(ServerPlayer p, float power) {
        cast(p);
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 22, 10);
        if (t == null) { pressure(sl, InkFx.target(p, 10), 1.5f, 14); return; }
        final LivingEntity tt = t;
        final int T = 60;
        grasp(sl, t, T);
        TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, GRASP, "Held by the deep", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, IceFx.boss(t) ? T / 2 : T);
        play(sl, mid(t), "tentacle", 1.0f, 1.0f);
        for (int i = 0; i < 3; i++) {
            AstaFx.later(10 + i * 20, () -> {
                if (!alive(p) || !tt.isAlive()) return;
                hit(p, tt, power * 0.45f, "abyssal_grasp");
                play(sl, mid(tt), "tentacle", 0.7f, 0.8f);
            });
        }
    }

    // ================================================================== 4. Undertow
    public static void undertowSpell(ServerPlayer p, float power) {
        cast(p);
        ServerLevel sl = p.serverLevel();
        final Vec3 lk = look(p), from = p.position();
        final float LEN = 10f;
        final int TURN = 16;
        undertow(sl, from, lk, LEN, TURN, TURN + 18);
        play(sl, from, "current", 1.2f, 1.0f);
        Vec3 a = from.add(0, 1, 0), b = a.add(lk.scale(LEN));
        final java.util.List<LivingEntity> caught = Kit.along(sl, p, a, b, 2.2);
        for (LivingEntity t : caught) {
            t.setDeltaMovement(lk.x * 1.1 * GravityFx.give(t), 0.25, lk.z * 1.1 * GravityFx.give(t));
            t.hurtMarked = true;
            hit(p, t, power * 0.5f, "undertow");
        }
        AstaFx.later(TURN, () -> {   // and back
            if (!alive(p)) return;
            for (LivingEntity t : caught) {
                if (!t.isAlive()) continue;
                Vec3 to = p.position().subtract(t.position());
                if (to.length() > 18) continue;
                Vec3 d = flat(to).normalize();
                t.setDeltaMovement(d.x * 1.3 * GravityFx.give(t), 0.15, d.z * 1.3 * GravityFx.give(t));
                t.hurtMarked = true;
                TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, CRUSH, "Dragged under", -0.35, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
                hit(p, t, power * 0.5f, "undertow");
            }
            play(sl, p.position(), "current", 1.0f, 0.7f);
        });
    }

    // ================================================================== 5. Abyssal Dive
    public static void abyssalDive(ServerPlayer p, float power) {
        cast(p);
        ServerLevel sl = p.serverLevel();
        Vec3 from = p.position();
        Vec3 g = InkFx.target(p, 16);
        Vec3 safe = SpaceFx.safe(sl, g, p);
        if (safe == null) safe = from;
        final Vec3 to = safe;
        dive(sl, from, to, 20);
        p.invulnerableTime = Math.max(p.invulnerableTime, 20);
        play(sl, from, "splash_deep", 1.0f, 0.8f);
        AstaFx.later(8, () -> {
            if (!alive(p)) return;
            SpaceFx.move(p, to, p.getYRot());
            jaws(sl, to.add(0, 1, 0), 2.8f, look(p));
            for (LivingEntity t : Kit.near(sl, p, to.add(0, 1, 0), 3.2)) {
                Kit.push(t, flat(t.position().subtract(to)).normalize(), 0.5, 0.45);
                hit(p, t, power * 1.0f, "abyssal_dive");
            }
            play(sl, to, "splash_deep", 1.2f, 1.1f);
            play(sl, to, "jaws", 1.0f, 0.9f);
        });
    }

    // ================================================================== 6. Depth Charge
    public static void depthCharge(ServerPlayer p, float power) {
        cast(p);
        ServerLevel sl = p.serverLevel();
        final Vec3 from = Kit.hand(p);
        final Vec3 g = InkFx.target(p, 18).add(0, 0.6, 0);
        final float R = 4.5f + depth(p) / 30f;
        final int BURST = 22;
        charge(sl, from, g, R, BURST);
        play(sl, from, "lure_hum", 0.6f, 0.6f);
        AstaFx.later(BURST, () -> {
            if (!alive(p)) return;
            for (LivingEntity t : Kit.near(sl, p, g, R)) {
                Kit.push(t, flat(t.position().subtract(g)).normalize(), 0.7, 0.35);
                TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, CRUSH, "Crushed by the deep", -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL, 50);
                hit(p, t, power * 1.15f, "depth_charge");
            }
            FxSend.shake(sl, g, 0.5f, 6, 20);
            play(sl, g, "deep_boom", 1.5f, 0.7f);
        });
    }

    // ================================================================== 7. Leviathan
    public static void leviathanSpell(ServerPlayer p, float power) {
        cast(p);
        ServerLevel sl = p.serverLevel();
        Vec3 lk = look(p);
        final Vec3 a = p.position().add(lk.scale(1.5)), b = p.position().add(lk.scale(16));
        leviathan(sl, a, b, 34);
        play(sl, a, "leviathan", 1.4f, 1.0f);
        for (int i = 0; i < 4; i++) {   // its length passing through
            final double u0 = i / 4.0, u1 = (i + 1) / 4.0;
            AstaFx.later(4 + i * 4, () -> {
                if (!alive(p)) return;
                Vec3 s = a.add(b.subtract(a).scale(u0)).add(0, 1, 0), e2 = a.add(b.subtract(a).scale(u1)).add(0, 1, 0);
                for (LivingEntity t : Kit.along(sl, p, s, e2, 2.0)) {
                    if (t.invulnerableTime > 10) continue;
                    t.setDeltaMovement(t.getDeltaMovement().x * 0.4 + lk.x * 0.3, 0.9 * GravityFx.give(t), t.getDeltaMovement().z * 0.4 + lk.z * 0.3);
                    t.hurtMarked = true;
                    hit(p, t, power * 1.2f, "leviathan");
                }
            });
        }
        AstaFx.later(8, () -> FxSend.shake(sl, a.add(b).scale(0.5), 0.5f, 8, 24));
    }

    // ================================================================== 8. Hadal Weight
    public static void hadalWeight(ServerPlayer p, float power) {
        cast(p);
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 22, 10);
        if (t == null) { pressure(sl, InkFx.target(p, 10), 1.5f, 14); return; }
        final int T = 120;
        weight(sl, t, T);
        TimedModifiers.apply(t, ForgeMod.ENTITY_GRAVITY.get(), HEAVY, "Hadal weight", 2.0, AttributeModifier.Operation.MULTIPLY_TOTAL, T);
        TimedModifiers.apply(t, Attributes.ARMOR, HEAVY_A, "Bowed by the deep", -0.35, AttributeModifier.Operation.MULTIPLY_TOTAL, T);
        TimedModifiers.apply(t, Attributes.KNOCKBACK_RESISTANCE, HEAVY_J, "Hadal weight", 1.0, AttributeModifier.Operation.ADDITION, T);
        t.setDeltaMovement(t.getDeltaMovement().x, -1.0, t.getDeltaMovement().z);
        t.hurtMarked = true;
        hit(p, t, power * 0.8f, "hadal_weight");
        play(sl, mid(t), "deep_boom", 0.9f, 1.4f);
    }

    // ================================================================== 9. Bioluminescence (mode)
    public static void bioluminescence(ServerPlayer p, float power) {
        cast(p);
        ServerLevel sl = p.serverLevel();
        final int T = 400;
        glow(sl, p, T);
        Moves.mode(p, MODE, T, 20, pl -> {});
        Moves.stance(p, 0, 0, 0.12, 0.2, 0, T);
        sync(p);
        AstaFx.later(T + 2, () -> { if (alive(p)) sync(p); });
        play(sl, p.position(), "lure_hum", 1.2f, 1.3f);
        sound(sl, p.position(), SoundEvents.CONDUIT_ACTIVATE, 0.8f, 0.8f);
    }

    // ================================================================== 10. The Trench
    public static void theTrench(ServerPlayer p, float power) {
        boolean limit = LimitBreak.active(p);
        ServerLevel sl = p.serverLevel();
        float d = depth(p);
        final float k = 1f + d / 100f;
        final float pow = power * k * (limit ? 1.3f : 1f) * (glowing(p) ? 1.25f : 1f);
        setDepth(p, 0);
        sync(p);
        final Vec3 g = InkFx.target(p, 24);
        final float R = (5f + d / 25f) * (limit ? 1.35f : 1f);
        final int BITE = 34;
        trench(sl, g, R, BITE, BITE + 20);
        play(sl, g, "leviathan", 1.6f, 0.6f);
        sound(sl, g, SoundEvents.ELDER_GUARDIAN_CURSE, 0.7f, 0.5f);
        for (int i = 0; i < 6; i++) {   // everything near it dragged in and down
            AstaFx.later(i * 5, () -> {
                if (!alive(p)) return;
                for (LivingEntity t : Kit.near(sl, p, g.add(0, 1, 0), R + 3)) {
                    Vec3 to = flat(g.subtract(t.position()));
                    double l = to.length();
                    Vec3 dd = l < 0.5 ? Vec3.ZERO : to.normalize().scale(Math.min(0.4, l * 0.12) * GravityFx.give(t));
                    t.setDeltaMovement(dd.x, Math.min(t.getDeltaMovement().y, -0.2), dd.z);
                    t.hurtMarked = true;
                    TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, TRENCH, "Sinking into the trench", -0.6, AttributeModifier.Operation.MULTIPLY_TOTAL, 20);
                }
            });
        }
        AstaFx.later(BITE, () -> {
            if (!alive(p)) return;
            for (LivingEntity t : Kit.near(sl, p, g.add(0, 1, 0), R)) {
                AstaFx.damage(p, t, pow * 1.7f, "the_trench");
                t.setDeltaMovement(0, 0.8 * GravityFx.give(t), 0);
                t.hurtMarked = true;
            }
            FxSend.impactFrame(sl, g.add(0, 1, 0), 3, FxSend.FRAME_YAMI, 22);
            FxSend.shake(sl, g, 0.8f, 12, 34);
            play(sl, g, "jaws", 1.8f, 0.5f);
            play(sl, g, "deep_boom", 1.8f, 0.4f);
        });
    }

    /** Surpassing Limits: you plunge - Depth floods in and the deep shows itself round you. */
    public static void awaken(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        sink(p, 40f);
        cast(p);
        pressure(sl, p.position(), 6f, 30);
        glow(sl, p, 40);
        play(sl, p.position(), "leviathan", 1.2f, 0.8f);
    }
}
