package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import com.ironspellgrimoire.registry.AddonSounds;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Sound Grimoire (an original grimoire): ten spells of sound made solid. Pulses that hit like a wall, echoes of
 * you that burst where you were, a barrier that turns missiles back, a silence that saps the strength out of foes,
 * and a last chord that breaks everything in front of it. Drawn by client/SoundFxClient (FxPacket shape 84):
 * rings and ripples of pale sea-green light.
 *
 *  1 Sonic Pulse      - three pulses of sound roll out in a cone in front of you
 *  2 Echo Step        - you dash ahead; an echo of you stays behind and bursts a second later
 *  3 Tuning Strike    - a ringing strike on the foe you aim at: two Resonance at once
 *  4 Sound Barrier    - 8 s: a shell of sound round you turns missiles back, and you take 30% less damage
 *  5 Silence Field    - 5 s: a circle where sound dies; foes in it hit 40% softer and move slower
 *  6 Thunderclap      - one clap: everything near is knocked off its feet
 *  7 Sonic Lance      - a beam of focused sound that runs 28 blocks through everything in its path
 *  8 Echo Chamber     - four pillars ring your target and ring the sound back in on it for 5 s
 *  9 Crescendo (mode) - 20 s: each Sound spell is louder than the last (+8% damage, up to five times); at the top the
 *                       next spell also breaks out of you in a shockwave
 * 10 Final Chord      - three rising notes, then a blast of sound that breaks everything round the target.
 *                       Surpassing Limits: wider and louder.
 *
 * Passive "Resonance": every Sound hit leaves its foe ringing; the fifth ring within 6 s shatters - a burst of half
 * the spell's power on it and on everything right round it.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class SoundFx {
    private SoundFx() {}

    public static final String MODE = "crescendo", BARRIER = "sound_barrier";
    static final int PAL = Fx.WIND;   // the generic hit marks: sea green
    private static final UUID HUSH_DMG = UUID.fromString("7c2e4d30-1e52-4b9f-8c22-8b3f6d000001");
    private static final UUID HUSH_SLOW = UUID.fromString("7c2e4d30-1e52-4b9f-8c22-8b3f6d000002");
    private static final UUID STUN = UUID.fromString("7c2e4d30-1e52-4b9f-8c22-8b3f6d000003");
    private static final double TAU = Math.PI * 2;

    // ================================================================== the client shapes (SoundFxClient, shape 84)
    private static FxPacket pk(int kind, Vec3 c, int life) {
        FxPacket p = new FxPacket();
        p.shape = 84; p.id = FxSend.nextId(); p.h = kind; p.c = c; p.life = life;
        return p;
    }

    /** 0 a pulse: arcs rolling out from c along d, to r, over a cone of half-angle a0 (radians). */
    static void pulse(ServerLevel sl, Vec3 c, Vec3 d, float r, float half, int life) {
        FxPacket p = pk(0, c, life);
        p.d = d; p.r = r; p.a0 = half;
        FxSend.sendNear(sl, c, p);
    }

    /** 1 a shockwave: rings opening (a0 = 1: closing in) round c to r. */
    static void shock(ServerLevel sl, Vec3 c, float r, int life, boolean in) {
        FxPacket p = pk(1, c, life);
        p.r = r; p.a0 = in ? 1 : 0;
        FxSend.sendNear(sl, c, p);
    }

    /** 2 a lance of sound from c to u, width r. */
    static void lance(ServerLevel sl, Vec3 c, Vec3 u, float r, int life) {
        FxPacket p = pk(2, c, life);
        p.u = u; p.r = r;
        FxSend.sendNear(sl, c, p);
    }

    /** 3 an echo of someone standing at c (height r), bursting at the end of its life. */
    static void echo(ServerLevel sl, Vec3 c, float h, float yaw, int life) {
        FxPacket p = pk(3, c, life);
        p.r = h; p.a1 = yaw;
        FxSend.sendNear(sl, c, p);
    }

    /** 4 the barrier round a player, radius r. */
    static void barrier(ServerLevel sl, ServerPlayer pl, float r, int life) {
        FxPacket p = pk(4, pl.position(), life);
        p.v = new Vec3(pl.getId(), 1, 0); p.r = r;
        FxSend.sendNear(sl, pl.position(), p);
    }

    /** 5 a field of silence on the ground. */
    static void silence(ServerLevel sl, Vec3 c, float r, int life) {
        FxPacket p = pk(5, c, life);
        p.r = r;
        FxSend.sendNear(sl, c, p);
    }

    /** 6 a ringing pillar (a tuning fork) standing at c, h tall. */
    static void pillar(ServerLevel sl, Vec3 c, float h, int life) {
        FxPacket p = pk(6, c, life);
        p.r = h;
        FxSend.sendNear(sl, c, p);
    }

    /** 7 the rings of Resonance on a foe: a0 = how many. */
    static void ringing(ServerLevel sl, LivingEntity t, int n, int life) {
        FxPacket p = pk(7, t.position(), life);
        p.v = new Vec3(t.getId(), 1, 0); p.a0 = n;
        FxSend.sendNear(sl, t.position(), p);
    }

    /** 8 Crescendo's aura on a player: a0 = how loud it has grown (0..5). */
    static void loud(ServerLevel sl, ServerPlayer pl, int tier, int life) {
        FxPacket p = pk(8, pl.position(), life);
        p.v = new Vec3(pl.getId(), 1, 0); p.a0 = tier;
        FxSend.sendNear(sl, pl.position(), p);
    }

    /** 9 the Final Chord at c: three notes rise for {@code rise} ticks, then the blast to r. */
    static void chord(ServerLevel sl, Vec3 c, float r, int rise, int life) {
        FxPacket p = pk(9, c, life);
        p.r = r; p.speed = rise;
        FxSend.sendNear(sl, c, p);
    }

    /** 10 a clap: the flash between the hands and the ring that tears out of it. */
    static void clap(ServerLevel sl, Vec3 c, Vec3 face, float r, int life) {
        FxPacket p = pk(10, c, life);
        p.d = face; p.r = r;
        FxSend.sendNear(sl, c, p);
    }

    // ================================================================== helpers
    static void play(ServerLevel sl, Vec3 at, String kind, float vol, float pitch) {
        SoundEvent s = AddonSounds.get(kind);
        if (s != null) sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    /** Crescendo: how loud the player has grown. */
    private static final Map<UUID, Integer> TIER = new HashMap<>();
    /** Resonance on each foe: {rings, until}. */
    private static final Map<UUID, long[]> RINGS = new HashMap<>();

    static boolean crescendo(ServerPlayer p) { return Moves.inMode(p, MODE); }

    static float pw(ServerPlayer p, float power) {
        return crescendo(p) ? power * (1f + 0.08f * TIER.getOrDefault(p.getUUID(), 0)) : power;
    }

    /** Every Sound spell: under Crescendo it grows louder; at the top the spell breaks out of you in a shockwave too. */
    private static void cast(ServerPlayer p, float power) {
        if (!crescendo(p)) return;
        int t = TIER.merge(p.getUUID(), 1, (a, b) -> Math.min(5, a + b));
        ServerLevel sl = p.serverLevel();
        loud(sl, p, t, 1);   // just the new tier (the aura already runs)
        if (t >= 5) {
            Vec3 c = p.position().add(0, 1, 0);
            shock(sl, c, 6f, 12, false);
            for (LivingEntity e : Kit.near(sl, p, c, 6)) hit(p, e, power * 0.5f, "crescendo", 0);
            play(sl, c, "sonic_boom", 1.0f, 0.8f);
        }
    }

    /** A Sound hit: the damage, then Resonance (the fifth ring shatters). */
    static void hit(ServerPlayer p, LivingEntity t, float amt, String id, int extraRings) {
        AstaFx.damage(p, t, amt, id);
        if (!t.isAlive()) return;
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        long[] r = RINGS.get(t.getUUID());
        int n = (r == null || now > r[1] ? 0 : (int) r[0]) + 1 + extraRings;
        if (n >= 5) {
            RINGS.remove(t.getUUID());
            final float burst = Math.max(2f, amt * 0.5f + 2f);
            AstaFx.later(3, () -> {
                if (!alive(p) || !t.isAlive()) return;
                Vec3 c = mid(t);
                shock(sl, c, 2.8f, 10, false);
                ringing(sl, t, 0, 1);
                AstaFx.damage(p, t, burst, "resonance");
                for (LivingEntity e : Kit.near(sl, p, c, 2.6)) if (e != t) AstaFx.damage(p, e, burst * 0.5f, "resonance");
                play(sl, c, "sound_ping", 1.0f, 1.4f);
                sound(sl, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0f, 1.2f);
            });
        } else {
            RINGS.put(t.getUUID(), new long[]{n, now + 120});
            ringing(sl, t, n, 120);
        }
        if (RINGS.size() > 200) RINGS.entrySet().removeIf(en -> now > en.getValue()[1]);
    }

    private static Vec3 ground(ServerLevel sl, Vec3 near) {
        Vec3 g = FxLib.ground(sl, near.add(0, 2, 0), 10);
        return g == null ? near : g;
    }

    private static Vec3 target(ServerPlayer p, double range) {
        LivingEntity t = aimed(p, range, 12);
        return t != null ? t.position() : ground(p.serverLevel(), Kit.aim(p, range));
    }

    public static boolean equipped(ServerPlayer p) {
        ItemStack b = Utils.getPlayerSpellbookStack(p);
        return b != null && b.is(AddonItemRegistry.SOUND_GRIMOIRE.get());
    }

    /** The Sound Barrier: 30% less damage while it holds. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && Moves.inMode(p, BARRIER)) e.setAmount(e.getAmount() * 0.7f);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (!crescendo(p)) TIER.remove(p.getUUID());
        if (!Moves.inMode(p, BARRIER) || p.tickCount % 2 != 0) return;
        Vec3 c = p.position().add(0, 1, 0);   // the barrier turns missiles back
        for (Projectile pr : p.serverLevel().getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(3.4), x -> x.getOwner() != p)) {
            if (pr.position().distanceTo(c) > 3.4) continue;
            Vec3 out = pr.position().subtract(c);
            Vec3 v = pr.getDeltaMovement();
            if (out.dot(v) > 0) continue;   // already going away
            pr.setDeltaMovement(out.normalize().scale(Math.max(0.8, v.length())));
            pr.setOwner(p);
            pr.hurtMarked = true;
            shock(p.serverLevel(), pr.position(), 0.8f, 6, false);
            sound(p.serverLevel(), pr.position(), SoundEvents.AMETHYST_BLOCK_HIT, 0.8f, 1.6f);
        }
    }

    // ================================================================== 1. Sonic Pulse
    public static void sonicPulse(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = pw(p, power);
        cast(p, power);
        for (int i = 0; i < 3; i++) {
            final int k = i;
            AstaFx.later(1 + i * 4, () -> {
                if (!alive(p)) return;
                Vec3 eye = p.getEyePosition().add(0, -0.3, 0), look = p.getLookAngle();
                pulse(sl, eye, look, 10f, 0.55f, 10);
                for (LivingEntity e : Kit.near(sl, p, eye, 10)) {
                    Vec3 to = mid(e).subtract(eye);
                    if (to.lengthSqr() < 1.0E-4 || to.normalize().dot(look) < Math.cos(0.6)) continue;
                    hit(p, e, pow * 0.3f, "sonic_pulse", 0);
                    Kit.push(e, look, 0.35 * GravityFx.give(e), 0.08);
                }
                play(sl, eye, "sonic_boom", 0.6f, 1.6f + k * 0.15f);
            });
        }
    }

    // ================================================================== 2. Echo Step
    public static void echoStep(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = pw(p, power);
        cast(p, power);
        final Vec3 from = p.position();
        Vec3 look = flat(p.getLookAngle());
        if (look.lengthSqr() < 1.0E-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        Vec3 dest = clip(sl, p, p.getEyePosition(), p.getEyePosition().add(look.scale(8))).subtract(look.scale(0.6)).subtract(0, p.getEyeHeight(), 0);
        Vec3 safe = SpaceFx.safe(sl, dest, p);
        if (safe == null) safe = from;
        for (LivingEntity e : Kit.along(sl, p, from.add(0, 1, 0), safe.add(0, 1, 0), 1.2)) hit(p, e, pow * 0.3f, "echo_step", 0);
        echo(sl, from, p.getBbHeight(), p.getYRot(), 22);
        lance(sl, from.add(0, 1, 0), safe.add(0, 1, 0), 0.35f, 6);
        SpaceFx.move(p, safe, p.getYRot());
        play(sl, from, "sonic_boom", 0.5f, 1.9f);
        AstaFx.later(20, () -> {   // the echo bursts where you stood
            if (!alive(p)) return;
            Vec3 c = from.add(0, 1, 0);
            shock(sl, c, 3.5f, 10, false);
            for (LivingEntity e : Kit.near(sl, p, c, 3.5)) {
                hit(p, e, pow * 0.8f, "echo_step", 0);
                Kit.push(e, mid(e).subtract(c), 0.6 * GravityFx.give(e), 0.25);
            }
            play(sl, c, "sonic_boom", 0.9f, 1.1f);
        });
    }

    // ================================================================== 3. Tuning Strike
    public static void tuningStrike(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = pw(p, power);
        cast(p, power);
        LivingEntity t = aimed(p, 24, 10);
        Vec3 at = t != null ? mid(t) : Kit.aim(p, 24);
        lance(sl, Kit.hand(p), at, 0.12f, 5);
        shock(sl, at, 1.6f, 8, true);
        if (t != null) hit(p, t, pow * 0.5f, "tuning_strike", 1);
        else for (LivingEntity e : Kit.near(sl, p, at, 1.5)) hit(p, e, pow * 0.5f, "tuning_strike", 1);
        play(sl, at, "sound_ping", 1.0f, 1.0f);
    }

    // ================================================================== 4. Sound Barrier
    public static void soundBarrier(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        cast(p, power);
        final int T = 160;
        barrier(sl, p, 3.2f, T);
        Moves.mode(p, BARRIER, T, 20, pl -> {});
        shock(sl, p.position().add(0, 1, 0), 3.4f, 10, false);
        for (LivingEntity e : Kit.near(sl, p, p.position().add(0, 1, 0), 3.4)) {   // the shell forming throws off what's close
            hit(p, e, pw(p, power) * 0.3f, "sound_barrier", 0);
            Kit.push(e, mid(e).subtract(p.position()), 0.8 * GravityFx.give(e), 0.2);
        }
        play(sl, p.position(), "sound_ping", 0.8f, 0.7f);
        sound(sl, p.position(), SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.6f);
    }

    // ================================================================== 5. Silence Field
    public static void silenceField(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = pw(p, power);
        cast(p, power);
        final Vec3 c = target(p, 24);
        final float R = 5.5f;
        final int T = 100;
        silence(sl, c, R, T);
        sound(sl, c, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.4f, 1.6f);
        for (int k = 0; k < T; k += 10) {
            final int kk = k;
            AstaFx.later(k + 1, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : Kit.near(sl, p, c.add(0, 1, 0), R)) {
                    TimedModifiers.apply(e, Attributes.ATTACK_DAMAGE, HUSH_DMG, "Silenced", -0.4, AttributeModifier.Operation.MULTIPLY_TOTAL, 14);
                    TimedModifiers.apply(e, Attributes.MOVEMENT_SPEED, HUSH_SLOW, "Silenced", -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL, 14);
                    if (kk % 20 == 0) hit(p, e, pow * 0.15f, "silence_field", 0);
                }
            });
        }
    }

    // ================================================================== 6. Thunderclap
    public static void thunderclap(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = pw(p, power);
        cast(p, power);
        Vec3 c = p.position().add(0, 1.1, 0);
        clap(sl, Kit.hand(p), p.getLookAngle(), 5.5f, 12);
        shock(sl, c, 5.5f, 12, false);
        for (LivingEntity e : Kit.near(sl, p, c, 5.5)) {
            hit(p, e, pow * 0.9f, "thunderclap", 0);
            Kit.push(e, mid(e).subtract(c), 0.7 * GravityFx.give(e), 0.55 * GravityFx.give(e));
            TimedModifiers.apply(e, Attributes.MOVEMENT_SPEED, STUN, "Deafened", -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
        }
        FxSend.shake(sl, c, 0.4f, 5, 14);
        play(sl, c, "sonic_boom", 1.2f, 0.8f);
    }

    // ================================================================== 7. Sonic Lance
    public static void sonicLance(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = pw(p, power);
        cast(p, power);
        Vec3 from = Kit.hand(p);
        Vec3 to = clip(sl, p, p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(28)));
        lance(sl, from, to, 0.55f, 14);
        for (LivingEntity e : Kit.along(sl, p, from, to, 1.2)) {
            hit(p, e, pow * 1.0f, "sonic_lance", 0);
            Kit.push(e, p.getLookAngle(), 0.4 * GravityFx.give(e), 0.1);
        }
        shock(sl, to, 2f, 8, false);
        play(sl, from, "sonic_boom", 1.0f, 1.3f);
        sound(sl, from, SoundEvents.WARDEN_SONIC_BOOM, 0.35f, 1.6f);
    }

    // ================================================================== 8. Echo Chamber
    public static void echoChamber(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float pow = pw(p, power);
        cast(p, power);
        final Vec3 c = target(p, 24);
        final float R = 4.5f;
        final int T = 100;
        for (int i = 0; i < 4; i++) {
            double an = i * TAU / 4 + 0.785;
            pillar(sl, ground(sl, c.add(Math.cos(an) * R, 0, Math.sin(an) * R)), 2.6f, T);
        }
        play(sl, c, "sound_ping", 0.9f, 0.6f);
        for (int k = 10; k < T; k += 15) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                shock(sl, c.add(0, 1, 0), R + 0.5f, 12, true);
                for (LivingEntity e : Kit.near(sl, p, c.add(0, 1, 0), R + 0.5)) {
                    hit(p, e, pow * 0.3f, "echo_chamber", 0);
                    GravityFx.pull(e, c.add(0, 1, 0), 0.25);
                }
                play(sl, c, "sound_ping", 0.6f, 0.8f + kk * 0.004f);
            });
        }
    }

    // ================================================================== 9. Crescendo (mode)
    public static void crescendoMode(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final int T = 400;
        TIER.put(p.getUUID(), 0);
        Moves.mode(p, MODE, T, 20, pl -> {});
        loud(sl, p, 0, T);
        shock(sl, p.position().add(0, 1, 0), 6f, 14, false);
        Moves.stance(p, 0, 0, 0.15, 0, 0.2, T);
        play(sl, p.position(), "sound_chord", 0.8f, 1.2f);
    }

    // ================================================================== 10. Final Chord
    public static void finalChord(ServerPlayer p, float power) {
        boolean limit = LimitBreak.active(p);
        ServerLevel sl = p.serverLevel();
        final float pow = pw(p, power) * (limit ? 1.3f : 1f);
        cast(p, power);
        final Vec3 g = target(p, 28);
        final float R = limit ? 11f : 8f;
        final int RISE = 22;
        chord(sl, g, R, RISE, RISE + 16);
        play(sl, g, "sound_chord", 1.4f, limit ? 0.85f : 1f);
        for (int k = 0; k < 3; k++) {   // three notes: each pins the foes a little more
            final int kk = k;
            AstaFx.later(4 + k * 6, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : Kit.near(sl, p, g.add(0, 1, 0), R)) {
                    TimedModifiers.apply(e, Attributes.MOVEMENT_SPEED, STUN, "Held by the chord", -0.3 - 0.15 * kk, AttributeModifier.Operation.MULTIPLY_TOTAL, 20);
                    hit(p, e, pow * 0.12f, "final_chord", 0);
                }
            });
        }
        AstaFx.later(RISE, () -> {
            if (!alive(p)) return;
            for (LivingEntity e : Kit.near(sl, p, g.add(0, 1, 0), R)) {
                hit(p, e, pow * 2.0f, "final_chord", 0);
                Kit.push(e, mid(e).subtract(g), 0.9 * GravityFx.give(e), 0.5 * GravityFx.give(e));
            }
            FxSend.impactFrame(sl, g.add(0, 1, 0), 3, FxSend.FRAME_JULIUS, 24);
            FxSend.shake(sl, g, 0.8f, 12, 34);
            sound(sl, g, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.4f);
        });
    }

    /** Surpassing Limits: a single note so loud the air rings round you three times. */
    public static void awaken(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position().add(0, 1, 0);
        for (int i = 0; i < 3; i++) {
            final int k = i;
            AstaFx.later(i * 5, () -> shock(sl, c, 4f + k * 2f, 14, false));
        }
        play(sl, c, "sound_chord", 1.0f, 1.3f);
    }
}
