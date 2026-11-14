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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The Mech Grimoire (an original grimoire): the magic of building a war machine out of mana, one part at a time.
 * Every spell bolts its part onto the frame - eight parts in all, each staying for 45 seconds - and the frame is
 * drawn on your screen as a blueprint filling in. Climb in (Pilot) and the machine stands round you with whatever
 * parts it has; launch the Titan Buster and everything you've built goes into one blow.
 * Drawn by client/MechFxClient (FxPacket shape 94): gunmetal plates, hazard-yellow trim, orange visor light, blue
 * thruster flame, sparks and smoke rings.
 *
 *  1 Rivet Barrage   (Arms)      - six rivets punched at what you aim at, one after another
 *  2 Thruster Dash   (Thrusters) - a burst of thrust forward, ramming through everything in the way
 *  3 Rocket Fist     (Fists)     - your fist flies out on a rocket, smashes what it meets and comes back
 *  4 Piston Stomp    (Legs)      - a piston-driven stomp: the ground round you jolts and throws foes up
 *  5 Bulwark Plating (Plating)   - 6 s: armour plates lock round you - you take 40% less
 *  6 Missile Swarm   (Launchers) - eight little missiles curl out and find the nearest foes
 *  7 Overclock       (Core)      - 8 s: the core runs hot - faster, quicker hands, and it patches you up a little
 *  8 Rail Cannon     (Cannon)    - a moment's charge, then a slug straight through everything in a long line
 *  9 Pilot (mode)    - 20 s: the machine stands round you, each part giving its strength - Plating armour, Legs
 *                      footing, Thrusters speed, Core quickness, Fists and Arms force - and Mech spells hit 20% harder
 * 10 Titan Buster    - a giant fist of the machine drops out of the sky where you aim. Every part you've built goes
 *                      into it: the more parts, the bigger and harder it lands - and the parts are spent.
 *                      Surpassing Limits: bigger still.
 *
 * Passive "Assembly": with all eight parts installed at once the machine is complete - Mech spells hit 25% harder.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class MechFx {
    private MechFx() {}

    public static final String MODE = "pilot", PLATING = "bulwark_plating";
    public static final int ARMS = 0, THRUSTERS = 1, FISTS = 2, LEGS = 3, PLATES = 4, LAUNCHERS = 5, CORE = 6, CANNON = 7;
    static final String[] PART_NAMES = {"Arms", "Thrusters", "Fists", "Legs", "Plating", "Launchers", "Core", "Cannon"};
    private static final String PART_KEY = "isg_mech_part_";
    private static final int PART_TIME = 900;
    private static final UUID OC_SPEED = UUID.fromString("e3c0be11-6a2f-4c1d-8e07-5b9a10000001");
    private static final UUID OC_ASPEED = UUID.fromString("e3c0be11-6a2f-4c1d-8e07-5b9a10000002");
    private static final UUID STAGGER = UUID.fromString("e3c0be11-6a2f-4c1d-8e07-5b9a10000003");

    // ================================================================== the client shapes (MechFxClient, shape 94)
    private static FxPacket pk(int kind, Vec3 c, int life) {
        FxPacket p = new FxPacket();
        p.shape = 94; p.id = FxSend.nextId(); p.h = kind; p.c = c; p.life = life; p.a1 = (float) (Math.random() * 1000);
        return p;
    }
    private static void send(ServerLevel sl, FxPacket p) { FxSend.sendNear(sl, p.c, p); }

    /** 0 a rivet: tracer from c to u, sparks where it lands. */
    static void rivet(ServerLevel sl, Vec3 c, Vec3 u) { FxPacket p = pk(0, c, 10); p.u = u; send(sl, p); }
    /** 1 thrust: flames out of the player's back from c to u. */
    static void thrust(ServerLevel sl, ServerPlayer pl, Vec3 c, Vec3 u, int life) { FxPacket p = pk(1, c, life); p.u = u; p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }
    /** 2 the rocket fist: from c out to u and back, out at {@code out} ticks; v = the caster. */
    static void fist(ServerLevel sl, ServerPlayer pl, Vec3 c, Vec3 u, int out, float size) { FxPacket p = pk(2, c, out * 2 + 4); p.u = u; p.sweep = out; p.r = size; p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }
    /** 3 the stomp: pistons and a jolt through the ground. */
    static void stomp(ServerLevel sl, Vec3 c, float r) { FxPacket p = pk(3, c, 22); p.r = r; send(sl, p); }
    /** 4 plates locked round a player. */
    static void plates(ServerLevel sl, ServerPlayer pl, int life) { FxPacket p = pk(4, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }
    /** 5 a missile from c curling to u, landing at {@code fly}. */
    static void missile(ServerLevel sl, Vec3 c, Vec3 u, int fly, float seed) { FxPacket p = pk(5, c, fly + 12); p.u = u; p.sweep = fly; p.a0 = seed; send(sl, p); }
    /** 6 overclock: gears and heat round a player. */
    static void overclock(ServerLevel sl, ServerPlayer pl, int life) { FxPacket p = pk(6, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }
    /** 7 the rail cannon: charge for {@code charge} ticks at the player, then the shot from c to u. */
    static void rail(ServerLevel sl, ServerPlayer pl, Vec3 c, Vec3 u, int charge) { FxPacket p = pk(7, c, charge + 14); p.u = u; p.sweep = charge; p.v = new Vec3(pl.getId(), 1, 0); send(sl, p); }
    /** 8 the machine round its pilot; a0 = part bitmask. */
    static void pilot(ServerLevel sl, ServerPlayer pl, int mask, int life) { FxPacket p = pk(8, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); p.a0 = mask; send(sl, p); }
    /** 9 the titan fist falling on c, landing at {@code fall}. */
    static void titan(ServerLevel sl, Vec3 c, float r, int fall, int parts) { FxPacket p = pk(9, c, fall + 26); p.r = r; p.sweep = fall; p.a0 = parts; send(sl, p); }
    /** 10 a part bolted on: blueprint lines snapping together at the player; a0 = which part. */
    static void installed(ServerLevel sl, ServerPlayer pl, int part) { FxPacket p = pk(10, pl.position(), 16); p.v = new Vec3(pl.getId(), 1, 0); p.a0 = part; send(sl, p); }
    /** 11 a burst of sparks and a smoke ring at c. */
    static void burst(ServerLevel sl, Vec3 c, float r) { FxPacket p = pk(11, c, 14); p.r = r; send(sl, p); }

    // ================================================================== parts
    static boolean has(ServerPlayer p, int part) { return p.getPersistentData().getLong(PART_KEY + part) > p.serverLevel().getGameTime(); }

    public static int mask(ServerPlayer p) {
        int m = 0;
        for (int i = 0; i < 8; i++) if (has(p, i)) m |= 1 << i;
        return m;
    }

    static int count(ServerPlayer p) { return Integer.bitCount(mask(p)); }

    static void install(ServerPlayer p, int part) {
        boolean was = has(p, part), full = count(p) == 8;
        p.getPersistentData().putLong(PART_KEY + part, p.serverLevel().getGameTime() + PART_TIME);
        if (!was) installed(p.serverLevel(), p, part);
        sync(p);
        if (!full && count(p) == 8) {
            p.displayClientMessage(Component.literal("ASSEMBLY COMPLETE - all systems online").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            play(p.serverLevel(), p.position(), "servo", 1.2f, 0.7f);
            sound(p.serverLevel(), p.position(), SoundEvents.ANVIL_USE, 0.6f, 1.4f);
        }
    }

    static void clearParts(ServerPlayer p) {
        for (int i = 0; i < 8; i++) p.getPersistentData().remove(PART_KEY + i);
        sync(p);
    }

    /** The blueprint on the player's screen (MechFxClient, kind 99): a0 = part bitmask, r = 1 if equipped, r2 = 1 while piloting. */
    static void sync(ServerPlayer p) {
        if (p.connection == null) return;
        FxPacket h = new FxPacket();
        h.shape = 94; h.h = 99; h.a0 = mask(p) + 0.001f; h.r = equipped(p) ? 1 : 0; h.r2 = piloting(p) ? 1 : 0; h.life = 1;
        com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), h);
    }

    public static boolean equipped(ServerPlayer p) {
        var b = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
        return b != null && b.is(com.ironspellgrimoire.registry.AddonItemRegistry.MECH_GRIMOIRE.get());
    }

    static boolean piloting(ServerPlayer p) { return Moves.inMode(p, MODE); }

    static void play(ServerLevel sl, Vec3 at, String kind, float vol, float pitch) {
        SoundEvent s = AddonSounds.get(kind);
        if (s != null) sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    /** A Mech hit. */
    static void hit(ServerPlayer p, LivingEntity t, float amt, String id) {
        if (piloting(p)) amt *= 1.2f;
        if (count(p) == 8) amt *= 1.25f;
        AstaFx.damage(p, t, amt, id);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && Moves.inMode(p, PLATING)) e.setAmount(e.getAmount() * 0.6f);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 40 == 7 && (equipped(p) || mask(p) != 0)) sync(p);
        if (p.tickCount % 5 == 0 && !equipped(p)) {   // switched to another grimoire: the machine goes with the book
            if (Moves.inMode(p, MODE)) { Moves.ends(p, MODE); sync(p); }
            if (Moves.inMode(p, PLATING)) Moves.ends(p, PLATING);
        }
    }

    private static Vec3 look(ServerPlayer p) {
        Vec3 l = flat(p.getLookAngle());
        return l.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : l.normalize();
    }

    // ================================================================== 1. Rivet Barrage
    public static void rivetBarrage(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        install(p, ARMS);
        for (int i = 0; i < 6; i++) {
            final int k = i;
            AstaFx.later(i * 2, () -> {
                if (!alive(p)) return;
                Vec3 from = Kit.hand(p).add(rightOf(p.getLookAngle()).scale(k % 2 == 0 ? 0.15 : -0.35));
                LivingEntity t = aimed(p, 24, 8);
                Vec3 to = t != null ? mid(t).add((p.getRandom().nextDouble() - 0.5) * 0.4, (p.getRandom().nextDouble() - 0.5) * 0.4, (p.getRandom().nextDouble() - 0.5) * 0.4)
                        : clip(sl, p, p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(24)));
                rivet(sl, from, to);
                for (LivingEntity h : Kit.along(sl, p, from, to, 0.6)) { hit(p, h, power * 0.25f, "rivet_barrage"); h.invulnerableTime = 0; }
                play(sl, from, "rivet", 0.8f, 0.9f + k * 0.05f);
            });
        }
    }

    // ================================================================== 2. Thruster Dash
    public static void thrusterDash(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        install(p, THRUSTERS);
        Vec3 lk = look(p), from = p.position();
        Vec3 end = clip(sl, p, from.add(0, 0.6, 0), from.add(0, 0.6, 0).add(lk.scale(9))).subtract(0, 0.6, 0);
        thrust(sl, p, from, end, 14);
        p.setDeltaMovement(lk.x * 2.4, 0.2, lk.z * 2.4);
        p.hurtMarked = true;
        p.invulnerableTime = Math.max(p.invulnerableTime, 10);
        for (LivingEntity t : Kit.along(sl, p, from.add(0, 1, 0), end.add(0, 1, 0), 1.4)) {
            Kit.push(t, rightOf(lk).scale(t.position().subtract(from).dot(rightOf(lk)) >= 0 ? 1 : -1).add(lk.scale(0.5)).normalize(), 0.9, 0.35);
            hit(p, t, power * 0.85f, "thruster_dash");
        }
        play(sl, from, "rocket", 1.0f, 1.2f);
    }

    // ================================================================== 3. Rocket Fist
    public static void rocketFist(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        install(p, FISTS);
        Vec3 from = Kit.hand(p);
        Vec3 to = clip(sl, p, p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(20)));
        double dist = from.distanceTo(to);
        final int OUT = Math.max(4, (int) (dist / 1.5));
        fist(sl, p, from, to, OUT, piloting(p) ? 1.6f : 1f);
        play(sl, from, "rocket", 1.0f, 1.0f);
        for (LivingEntity t : Kit.along(sl, p, from, to, piloting(p) ? 1.6 : 1.1)) {
            int when = (int) Math.max(1, mid(t).distanceTo(from) / 1.5);
            final LivingEntity tt = t;
            AstaFx.later(when, () -> {
                if (!alive(p) || !tt.isAlive()) return;
                Kit.push(tt, p.getLookAngle(), 1.1, 0.3);
                TimedModifiers.apply(tt, Attributes.MOVEMENT_SPEED, STAGGER, "Punched", -0.4, AttributeModifier.Operation.MULTIPLY_TOTAL, 20);
                hit(p, tt, power * 1.15f, "rocket_fist");
                burst(sl, mid(tt), 1.2f);
                play(sl, mid(tt), "clank", 1.0f, 0.8f);
            });
        }
        AstaFx.later(OUT, () -> { if (alive(p)) burst(sl, to, 0.8f); });
    }

    // ================================================================== 4. Piston Stomp
    public static void pistonStomp(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        install(p, LEGS);
        Vec3 c = p.position();
        float r = piloting(p) ? 6.5f : 5f;
        stomp(sl, c, r);
        for (LivingEntity t : Kit.near(sl, p, c.add(0, 0.5, 0), r)) {
            t.setDeltaMovement(t.getDeltaMovement().x * 0.3, 0.75 * GravityFx.give(t), t.getDeltaMovement().z * 0.3);
            t.hurtMarked = true;
            hit(p, t, power * 0.9f, "piston_stomp");
        }
        FxSend.shake(sl, c, 0.45f, 6, 18);
        play(sl, c, "stomp", 1.2f, 1.0f);
    }

    // ================================================================== 5. Bulwark Plating
    public static void bulwarkPlating(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        install(p, PLATES);
        final int T = 120;
        plates(sl, p, T);
        Moves.mode(p, PLATING, T, 20, pl -> {});
        play(sl, p.position(), "clank", 1.0f, 1.0f);
        sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_NETHERITE, 1.0f, 0.8f);
    }

    // ================================================================== 6. Missile Swarm
    public static void missileSwarm(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        install(p, LAUNCHERS);
        Vec3 c = p.position().add(0, 1, 0);
        List<LivingEntity> foes = new ArrayList<>(Kit.near(sl, p, c, 18));
        foes.sort(Comparator.comparingDouble(t -> t.distanceToSqr(p)));
        Vec3 aim = Kit.aim(p, 20);
        play(sl, c, "missile", 1.0f, 1.0f);
        for (int i = 0; i < 8; i++) {
            final LivingEntity t = foes.isEmpty() ? null : foes.get(i % foes.size());
            final Vec3 g = t != null ? mid(t) : aim.add((p.getRandom().nextDouble() - 0.5) * 5, 0, (p.getRandom().nextDouble() - 0.5) * 5);
            final Vec3 from = c.add(0, 0.6, 0).add(rightOf(p.getLookAngle()).scale(i % 2 == 0 ? 0.5 : -0.5));
            final int FLY = 12 + (int) (from.distanceTo(g) / 1.4) + i;
            missile(sl, from, g, FLY, i);
            AstaFx.later(FLY, () -> {
                if (!alive(p)) return;
                Vec3 at = t != null && t.isAlive() ? mid(t) : g;
                burst(sl, at, 1.6f);
                for (LivingEntity h : Kit.near(sl, p, at, 2.2)) { hit(p, h, power * 0.32f, "missile_swarm"); h.invulnerableTime = 0; }
                play(sl, at, "clank", 0.5f, 1.4f);
                sound(sl, at, SoundEvents.GENERIC_EXPLODE, 0.35f, 1.6f);
            });
        }
    }

    // ================================================================== 7. Overclock
    public static void overclockSpell(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        install(p, CORE);
        final int T = 160;
        overclock(sl, p, T);
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, OC_SPEED, "Overclocked", 0.3, AttributeModifier.Operation.MULTIPLY_TOTAL, T);
        TimedModifiers.apply(p, Attributes.ATTACK_SPEED, OC_ASPEED, "Overclocked", 0.4, AttributeModifier.Operation.MULTIPLY_TOTAL, T);
        p.heal(3f + count(p) * 0.5f);
        play(sl, p.position(), "servo", 1.0f, 1.5f);
    }

    // ================================================================== 8. Rail Cannon
    public static void railCannon(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        install(p, CANNON);
        final int CHARGE = 14;
        Vec3 from0 = Kit.hand(p);
        Vec3 to0 = clip(sl, p, p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(36)));
        rail(sl, p, from0, to0, CHARGE);
        play(sl, from0, "servo", 0.9f, 2.0f);
        AstaFx.later(CHARGE, () -> {
            if (!alive(p)) return;
            Vec3 from = Kit.hand(p);
            Vec3 to = clip(sl, p, p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(36)));
            rail(sl, p, from, to, 0);
            for (LivingEntity t : Kit.along(sl, p, from, to, piloting(p) ? 1.4 : 1.0)) {
                Kit.push(t, p.getLookAngle(), 0.8, 0.15);
                hit(p, t, power * 1.6f, "rail_cannon");
            }
            burst(sl, to, 1.4f);
            Kit.push(p, p.getLookAngle().scale(-1), 0.35, 0.05);
            play(sl, from, "railgun", 1.4f, 1.0f);
        });
    }

    // ================================================================== 9. Pilot (mode)
    public static void pilotMode(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final int T = 400;
        int m = mask(p);
        pilot(sl, p, m, T);
        Moves.mode(p, MODE, T, 40, pl -> pilot(pl.serverLevel(), pl, mask(pl), 42));
        double armor = (has(p, PLATES) ? 0.35 : 0.1), kb = has(p, LEGS) ? 0.6 : 0.2, speed = has(p, THRUSTERS) ? 0.15 : 0, as = has(p, CORE) ? 0.25 : 0;
        Moves.stance(p, armor, has(p, PLATES) ? 0.2 : 0, speed, kb, as, T);
        sync(p);
        AstaFx.later(T + 2, () -> { if (alive(p)) sync(p); });
        FxSend.impactFrame(sl, p.position().add(0, 1, 0), 2, FxSend.FRAME_ASTA, 14);
        play(sl, p.position(), "servo", 1.4f, 0.6f);
        sound(sl, p.position(), SoundEvents.IRON_GOLEM_REPAIR, 1.0f, 0.7f);
        p.displayClientMessage(Component.literal("PILOT ONLINE - " + Integer.bitCount(m) + "/8 parts").withStyle(ChatFormatting.GOLD), true);
    }

    // ================================================================== 10. Titan Buster
    public static void titanBuster(ServerPlayer p, float power) {
        boolean limit = LimitBreak.active(p);
        ServerLevel sl = p.serverLevel();
        int parts = count(p);
        final float pow = power * (1f + parts / 8f) * (limit ? 1.3f : 1f) * (piloting(p) ? 1.2f : 1f) * (parts == 8 ? 1.25f : 1f);
        clearParts(p);
        final Vec3 g = InkFx.target(p, 26);
        final float R = (3.5f + parts * 0.45f) * (limit ? 1.35f : 1f);
        final int FALL = 22;
        titan(sl, g, R, FALL, parts);
        play(sl, g, "rocket", 1.6f, 0.5f);
        AstaFx.later(FALL, () -> {
            if (!alive(p)) return;
            for (LivingEntity t : Kit.near(sl, p, g.add(0, 1, 0), R)) {
                AstaFx.damage(p, t, pow * 1.8f, "titan_buster");
                t.setDeltaMovement(t.getDeltaMovement().x, 0.6 * GravityFx.give(t), t.getDeltaMovement().z);
                t.hurtMarked = true;
            }
            FxSend.impactFrame(sl, g.add(0, 1, 0), 3, FxSend.FRAME_ASTA_SLASH, 22);
            FxSend.shake(sl, g, 0.9f, 12, 36);
            play(sl, g, "stomp", 1.8f, 0.5f);
            sound(sl, g, SoundEvents.GENERIC_EXPLODE, 1.0f, 0.7f);
        });
    }

    /** Surpassing Limits: the whole frame assembles at once. */
    public static void awaken(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        for (int i = 0; i < 8; i++) install(p, i);
        stomp(sl, p.position(), 5f);
        play(sl, p.position(), "servo", 1.4f, 0.8f);
    }
}
