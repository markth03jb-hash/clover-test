package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.BossKit.*;
import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.entity.CinderSovereign;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Cinder Sovereign's fight. Every move is shown before it lands (marked ground, and the body winding up for it), and
 * nothing it does breaks a block. Its moves, each with its own animation:
 * <ul>
 *   <li>Slam: both fists overhead, then driven into the ground before it - a crater of fire, a shockwave you can jump,
 *       and lines of magma cracking out from the blow.</li>
 *   <li>Swipe: one arm torn across its front: a wide arc of claws.</li>
 *   <li>Wing Gust: a beat of the wings that hurls everyone in front of it away.</li>
 *   <li>Pyre Volley: the five braziers on its back each fling a burning coal where you're heading.</li>
 *   <li>Ember Rain (a roar): fire falls from the sky in marked circles round every mage near it.</li>
 *   <li>Heart Beam (phase 2+, a roar): the furnace in its chest opens and sweeps a beam across the ground.</li>
 *   <li>Descent: far away or stuck, it beats its wings and drops out of the sky beside you.</li>
 * </ul>
 * Phases at two-thirds and one-third: a roar that throws everyone back, and quicker, crueller moves.
 */
public final class CinderSovereignAI {
    private CinderSovereignAI() {}

    /** Blocks per model pixel (the model is drawn 2.2 times over). */
    static final double PX = 2.2 / 16.0;
    private static final UUID SCORCH = UUID.fromString("c1de0000-0000-4000-8000-0000000000a1");
    private static final ChatFormatting COL = ChatFormatting.RED;

    private static void line(CinderSovereign b, String s) { say(b, "The Cinder Sovereign", COL, s); }
    private static Vec3 fwd(CinderSovereign b) { float y = b.getYRot() * (float) Math.PI / 180f; return new Vec3(-Math.sin(y), 0, Math.cos(y)); }
    private static Vec3 rightOf(Vec3 f) { return new Vec3(-f.z, 0, f.x); }
    private static float dmg(CinderSovereign b, float d) { return d * (1f + 0.15f * (b.phase() - 1)); }

    public static void tick(CinderSovereign b) {
        ServerLevel sl = (ServerLevel) b.level();
        Player t = target(b);
        if (b.tickCount % 10 == 0) {   // the ground smoulders round its feet
            BossFx.ring(sl, Fx.FIRE, b.position().add(0, 0.1, 0), 3.4f, 3.8f, 0.05f, 12);
        }
        if (b.tickCount % 13 == 0 && b.onGround() && b.getDeltaMovement().horizontalDistanceSqr() > 0.0016) {   // its own heavy tread
            net.minecraft.sounds.SoundEvent step = com.ironspellgrimoire.registry.AddonSounds.get("sovereign_step");
            if (step != null) sl.playSound(null, b.getX(), b.getY(), b.getZ(), step, SoundSource.HOSTILE, 2.2f, 0.9f + b.getRandom().nextFloat() * 0.2f);
        }
        if (t == null) { b.getNavigation().stop(); b.idleFor++; return; }
        b.idleFor = 0;
        Vec3 dir = dirTo(b, t);
        double d = Math.sqrt(t.distanceToSqr(b.getX(), t.getY(), b.getZ()));
        if (b.busy > 0) { b.busy--; return; }
        face(b, dir);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        // too far, or stuck on the land: it comes down out of the sky beside you
        if (d > 36 || b.stuck > 50) { b.stuck = 0; b.busy = descent(b, t); return; }
        if (d > 13) {
            Vec3 before = b.position();
            b.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), 1.0);
            if (b.tickCount % 10 == 0 && b.position().distanceToSqr(before) < 0.0004) b.stuck += 10;
            if (r.nextInt(70) == 0) b.busy = volley(b, t);   // it throws coals as it comes
            return;
        }
        b.getNavigation().stop();
        b.stuck = 0;
        List<java.util.function.ToIntBiFunction<CinderSovereign, Player>> pool = new ArrayList<>();
        if (d < 11) { pool.add(CinderSovereignAI::slam); pool.add(CinderSovereignAI::slam); pool.add(CinderSovereignAI::swipe); }
        pool.add(CinderSovereignAI::gust);
        pool.add(CinderSovereignAI::volley);
        pool.add(CinderSovereignAI::emberRain);
        if (b.phase() >= 2) pool.add(CinderSovereignAI::heartBeam);
        int took = pool.get(r.nextInt(pool.size())).applyAsInt(b, t);
        b.busy = took + (b.phase() == 1 ? 24 : b.phase() == 2 ? 16 : 10);
        if (b.tickCount > 12000) {   // ten minutes and still standing: it burns hotter, and faster
            if (!b.getPersistentData().getBoolean("isg_enraged")) {
                b.getPersistentData().putBoolean("isg_enraged", true);
                line(b, "You have kept me waiting long enough.");
                BossFx.ring(sl, Fx.FIRE, b.position().add(0, 0.2, 0), 1f, 14f, 0.5f, 18);
            }
            b.busy = Math.max(6, b.busy * 2 / 3);
        }
    }

    /** Slam: a crater before it, a shockwave to jump, and magma cracking out. */
    static int slam(CinderSovereign b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.play("slam");
        int T = 23;
        Vec3 f = fwd(b);
        Vec3 c = ZenonAI.ground(sl, b.position().add(f.scale(5.2)).add(0, 3, 0));
        warnCircle(sl, c, 6.5f, T);
        sl.playSound(null, b.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2f, 0.6f);
        double[] cracks = new double[b.phase() >= 2 ? 8 : 6];
        double a0 = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        for (int i = 0; i < cracks.length; i++) {
            cracks[i] = a0 + i * Math.PI * 2 / cracks.length;
            Vec3 e = c.add(Math.cos(cracks[i]) * 16, 0, Math.sin(cracks[i]) * 16);
            warnLine(sl, c.add(0, 0.05, 0), e.add(0, 0.05, 0), 1.4f, T + 4);
        }
        AstaFx.later(T, () -> {
            if (!b.isAlive()) return;
            BossFx.ring(sl, Fx.FIRE, c.add(0, 0.1, 0), 0.5f, 7f, 0.3f, 14);
            BossFx.beam(sl, Fx.FUEGO, c, c.add(0, 9, 0), 2.4f, 12);
            FxSend.impactFrame(sl, c.add(0, 1, 0), 4, FxSend.FRAME_GOLD, 40);
            FxSend.shake(sl, c, 1.4f, 18, 48);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.5f, 0.5f);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5f, 0.4f);
            for (Player p : near(b, c.add(0, 1, 0), 6.5)) {
                hurt(b, p, dmg(b, 22f));
                p.setDeltaMovement(p.getDeltaMovement().add(0, 0.7, 0)); p.hurtMarked = true;
            }
            for (double a : cracks) {   // magma cracking out from the blow
                Vec3 e = c.add(Math.cos(a) * 16, 0, Math.sin(a) * 16);
                AstaFx.later(4, () -> {
                    if (!b.isAlive()) return;
                    BossFx.trail(sl, Fx.FIRE, c.add(0, 0.2, 0), e.add(0, 0.2, 0), 0.9f, 20);
                    for (Player p : along(b, c.add(0, 1, 0), e.add(0, 1, 0), 1.3)) hurt(b, p, dmg(b, 10f));
                });
            }
            for (int k = 1; k <= 3; k++) {   // the shockwave: jump it
                final float rad = 6.5f + k * 4.5f;
                AstaFx.later(k * 3, () -> {
                    if (!b.isAlive()) return;
                    BossFx.ring(sl, Fx.FUEGO, c.add(0, 0.2, 0), rad - 1.5f, rad, 0.5f, 6);
                    for (Player p : near(b, c.add(0, 1, 0), rad)) {
                        double dd = Math.sqrt(p.distanceToSqr(c.x, p.getY(), c.z));
                        if (dd > rad - 4.5 && p.onGround()) hurt(b, p, dmg(b, 9f));
                    }
                });
            }
        });
        return 56;
    }

    /** Swipe: a wide arc of claws across its front. */
    static int swipe(CinderSovereign b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.play("swipe");
        int T = 14;
        Vec3 f = fwd(b), c = b.position();
        for (int i = -2; i <= 2; i++) warnLine(sl, c.add(0, 0.05, 0), c.add(rotY(f, i * 0.42).scale(11)).add(0, 0.05, 0), 2.2f, T);
        sl.playSound(null, b.blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 2f, 0.5f);
        AstaFx.later(T, () -> {
            if (!b.isAlive()) return;
            BossFx.slash(sl, Fx.FIRE, c.add(0, 3, 0), f, new Vec3(0, 1, 0), 11f, 0.9f, 10, 2.5f, 1.4f);
            BossFx.slash(sl, Fx.CRIMSON, c.add(0, 2.4, 0), f, new Vec3(0, 1, 0), 9.5f, 0.8f, 10, 2.5f, 1.0f);
            sl.playSound(null, b.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2f, 0.5f);
            for (Player p : near(b, c.add(0, 1, 0), 11.5)) {
                Vec3 to = flat(p.position().subtract(c));
                if (to.lengthSqr() < 1e-4 || to.normalize().dot(f) < Math.cos(1.0)) continue;
                hurt(b, p, dmg(b, 16f));
                Vec3 side = rightOf(f).scale(-1);
                p.setDeltaMovement(side.scale(1.3).add(0, 0.45, 0)); p.hurtMarked = true;
            }
        });
        return 36;
    }

    /** Wing Gust: everyone before it is hurled away. */
    static int gust(CinderSovereign b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.play("wing_gust");
        int T = 23;
        Vec3 f = fwd(b), c = b.position();
        for (int i = -3; i <= 3; i++) warnLine(sl, c.add(0, 0.05, 0), c.add(rotY(f, i * 0.33).scale(20)).add(0, 0.05, 0), 1.6f, T);
        AstaFx.later(T, () -> {
            if (!b.isAlive()) return;
            for (int k = 0; k < 4; k++) {
                final int kk = k;
                AstaFx.later(k * 2, () -> BossFx.wall(sl, Fx.CRIMSON, c.add(f.scale(3 + kk * 4)).add(0, 1.5, 0), f, new Vec3(0, 1, 0), 3 + kk * 4, 4 + kk * 4, -60, 60, 3f, 8, 0, 0));
            }
            sl.playSound(null, b.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 3f, 0.5f);
            FxSend.shake(sl, c, 0.6f, 10, 40);
            for (Player p : near(b, c.add(0, 1, 0), 22)) {
                Vec3 to = flat(p.position().subtract(c));
                if (to.lengthSqr() < 1e-4 || to.normalize().dot(f) < Math.cos(1.05)) continue;
                hurt(b, p, dmg(b, 10f));
                p.setDeltaMovement(to.normalize().scale(2.4).add(0, 0.6, 0)); p.hurtMarked = true;
                TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, SCORCH, "Scorched", -0.25, AttributeModifier.Operation.MULTIPLY_TOTAL, 60);
            }
        });
        return 48;
    }

    /** Pyre Volley: each brazier on its back flings a burning coal where you're heading. */
    static int volley(CinderSovereign b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        Vec3 f = fwd(b), rt = rightOf(f);
        Vec3 lead = t.position().add(t.getDeltaMovement().multiply(14, 0, 14));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 5; i++) {
            Vec3 from = b.position().add(rt.scale((i - 2) * 1.8)).add(f.scale(-1.6)).add(0, 14 - Math.abs(i - 2) * 0.9, 0);
            Vec3 at = ZenonAI.ground(sl, lead.add(r.nextDouble(-4, 4), 2, r.nextDouble(-4, 4)));
            int fly = 22 + i * 3;
            warnCircle(sl, at, 2.4f, fly);
            for (int s = 0; s < fly; s += 3) {
                final double k = s / (double) fly;
                AstaFx.later(s, () -> {
                    if (!b.isAlive()) return;
                    Vec3 p = from.lerp(at, k).add(0, Math.sin(k * Math.PI) * 6, 0);
                    BossFx.orb(sl, Fx.FIRE, p, Vec3.ZERO, 0.7f, 4);
                });
            }
            AstaFx.later(fly, () -> {
                if (!b.isAlive()) return;
                BossFx.ring(sl, Fx.FIRE, at.add(0, 0.1, 0), 0.3f, 2.6f, 0.12f, 10);
                BossFx.beam(sl, Fx.FUEGO, at, at.add(0, 3, 0), 0.7f, 8);
                for (Player p : near(b, at.add(0, 1, 0), 2.4)) hurt(b, p, dmg(b, 9f));
                sl.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.2f, 0.7f);
            });
        }
        sl.playSound(null, b.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2f, 0.5f);
        return 34;
    }

    /** Ember Rain: it roars, and fire falls in marked circles round every mage near it. */
    static int emberRain(CinderSovereign b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.play("roar");
        sl.playSound(null, b.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3f, 0.5f);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int n = 4 + b.phase();
        for (Player p : near(b, b.position().add(0, 1, 0), 40)) {
            for (int i = 0; i < n; i++) {
                int at = 20 + i * 7;
                AstaFx.later(at - 18, () -> {
                    if (!b.isAlive() || !p.isAlive()) return;
                    Vec3 g = ZenonAI.ground(sl, p.position().add(r.nextDouble(-5, 5), 3, r.nextDouble(-5, 5)));
                    warnCircle(sl, g, 2.6f, 18);
                    AstaFx.later(18, () -> {
                        if (!b.isAlive()) return;
                        BossFx.beam(sl, Fx.FIRE, g.add(0, 24, 0), g, 1.2f, 10);
                        BossFx.ring(sl, Fx.FUEGO, g.add(0, 0.1, 0), 0.3f, 2.8f, 0.14f, 10);
                        for (Player q : near(b, g.add(0, 1, 0), 2.6)) hurt(b, q, dmg(b, 12f));
                        sl.playSound(null, g.x, g.y, g.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.9f, 1.1f);
                    });
                });
            }
        }
        return 64;
    }

    /** Heart Beam: the furnace in its chest opens and sweeps a beam across the ground. */
    static int heartBeam(CinderSovereign b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.play("roar");
        int W = 20, S = 32;
        Vec3 f = fwd(b), rt = rightOf(f);
        Vec3 heart = b.position().add(f.scale(1.5)).add(0, 53 * PX, 0);
        boolean leftFirst = ThreadLocalRandom.current().nextBoolean();
        Vec3 a0 = b.position().add(f.scale(16)).add(rt.scale(leftFirst ? -13 : 13)), a1 = b.position().add(f.scale(16)).add(rt.scale(leftFirst ? 13 : -13));
        warnLine(sl, ZenonAI.ground(sl, a0.add(0, 3, 0)), ZenonAI.ground(sl, a1.add(0, 3, 0)), 2.5f, W + S);
        sl.playSound(null, b.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2f, 0.5f);
        for (int s = 0; s <= S; s += 2) {
            final double k = s / (double) S;
            AstaFx.later(W + s, () -> {
                if (!b.isAlive()) return;
                Vec3 g = ZenonAI.ground(sl, a0.lerp(a1, k).add(0, 3, 0));
                BossFx.beam(sl, Fx.FUEGO, heart, g, 0.9f, 3);
                BossFx.ring(sl, Fx.FIRE, g.add(0, 0.1, 0), 0.4f, 1.8f, 0.1f, 6);
                if ((int) (k * S) % 4 == 0) for (Player p : along(b, heart, g.add(0, 1, 0), 1.6)) hurt(b, p, dmg(b, 7f));
            });
        }
        return W + S + 8;
    }

    /** Descent: it beats its wings, rises out of sight, and drops beside you. */
    static int descent(CinderSovereign b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.play("wing_gust");
        Vec3 from = b.position();
        Vec3 off = flat(from.subtract(t.position()));
        off = off.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : off.normalize();
        Vec3 land = ZenonAI.ground(sl, t.position().add(off.scale(9)).add(0, 6, 0));
        int T = 22;
        warnCircle(sl, land, 7f, T + 12);
        AstaFx.later(T, () -> {
            if (!b.isAlive()) return;
            BossFx.ring(sl, Fx.CRIMSON, from.add(0, 0.2, 0), 1f, 9f, 0.2f, 12);
            BossFx.beam(sl, Fx.FIRE, from, from.add(0, 30, 0), 2f, 10);
            sl.playSound(null, from.x, from.y, from.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 3f, 0.4f);
        });
        AstaFx.later(T + 12, () -> {
            if (!b.isAlive()) return;
            b.teleportTo(land.x, land.y, land.z);
            b.play("slam");
            BossFx.beam(sl, Fx.FIRE, land.add(0, 30, 0), land, 2.4f, 10);
            BossFx.ring(sl, Fx.FIRE, land.add(0, 0.1, 0), 1f, 9f, 0.4f, 14);
            FxSend.shake(sl, land, 1.2f, 16, 48);
            sl.playSound(null, land.x, land.y, land.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.5f, 0.5f);
            for (Player p : near(b, land.add(0, 1, 0), 7)) {
                hurt(b, p, dmg(b, 14f));
                Vec3 away = flat(p.position().subtract(land));
                if (away.lengthSqr() > 1e-4) { p.setDeltaMovement(away.normalize().scale(1.6).add(0, 0.5, 0)); p.hurtMarked = true; }
            }
        });
        return T + 12 + 40;
    }

    /** The roar between phases: everyone thrown back, and its fires burn higher. */
    public static void phase(CinderSovereign b, int phase) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setTransforming(50);
        b.play("roar");
        line(b, phase == 2 ? "You think fire fears the rain? Burn." : "ENOUGH. The world beneath you is mine.");
        Vec3 c = b.position();
        sl.playSound(null, b.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4f, 0.4f);
        AstaFx.later(20, () -> {
            BossFx.ring(sl, Fx.FIRE, c.add(0, 0.2, 0), 1f, 16f, 0.6f, 18);
            BossFx.beam(sl, Fx.FUEGO, c, c.add(0, 40, 0), 3f, 18);
            FxSend.impactFrame(sl, c.add(0, 6, 0), 5, FxSend.FRAME_GOLD, 60);
            FxSend.shake(sl, c, 1.5f, 24, 60);
            for (Player p : near(b, c.add(0, 1, 0), 15)) {
                hurt(b, p, dmg(b, 8f));
                Vec3 away = flat(p.position().subtract(c));
                if (away.lengthSqr() > 1e-4) { p.setDeltaMovement(away.normalize().scale(2.2).add(0, 0.7, 0)); p.hurtMarked = true; }
            }
        });
    }

    /** Its death, in time with the animation: the stagger, the roar and the bursting braziers, the wings crumbling to ash, the fall. */
    public static void dying(CinderSovereign b, int t) {
        ServerLevel sl = (ServerLevel) b.level();
        Vec3 c = b.position(), f = fwd(b), rt = rightOf(f);
        if (t == 1) {
            line(b, "No... the fire... it was mine...");
            sl.playSound(null, b.blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 3f, 0.5f);
            WorldBoss.defeated(b);
        }
        if (t == 44) {   // the roar to the sky - and the braziers burst
            sl.playSound(null, b.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4f, 0.35f);
            for (int i = 0; i < 5; i++) {
                Vec3 br = c.add(rt.scale((i - 2) * 1.8)).add(f.scale(-1.6)).add(0, 14 - Math.abs(i - 2) * 0.9, 0);
                int ii = i;
                AstaFx.later(ii, () -> {
                    BossFx.ring(sl, Fx.FIRE, br, 0.3f, 3.5f, 0.2f, 12);
                    BossFx.beam(sl, Fx.FUEGO, br, br.add(0, 8, 0), 1.2f, 10);
                });
            }
            FxSend.shake(sl, c, 1f, 14, 48);
        }
        if (t > 60 && t < 110 && t % 3 == 0) {   // the wings crumbling to ash
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int s = -1; s <= 1; s += 2) {
                Vec3 w = c.add(rt.scale(s * r.nextDouble(4, 11))).add(f.scale(-1.5)).add(0, r.nextDouble(6, 13), 0);
                BossFx.orb(sl, r.nextBoolean() ? Fx.FIRE : Fx.SILVER, w, new Vec3(0, 0.12, 0), 0.5f, 18);
            }
        }
        if (t == 110) sl.playSound(null, b.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 3f, 0.5f);
        if (t == 116) {   // the husk hits the ground
            Vec3 g = c.add(f.scale(5));
            BossFx.ring(sl, Fx.SILVER, g.add(0, 0.2, 0), 1f, 10f, 0.3f, 16);
            FxSend.shake(sl, g, 1.4f, 20, 48);
            sl.playSound(null, g.x, g.y, g.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.5f, 0.4f);
        }
        if (t == 140) BossFx.ring(sl, Fx.SILVER, c.add(0, 0.5, 0), 6f, 0.5f, 0.2f, 14);
    }
}
