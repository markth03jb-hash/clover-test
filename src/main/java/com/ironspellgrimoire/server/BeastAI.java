package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.entity.BeastOfDespair;
import com.ironspellgrimoire.network.FxPacket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * BeastOfDespair's fight. Beast Magic first (claws, the cheetah's charge, the rhinoceros's armour, slams and a roar), then his
 * third eye opens: Mythical Beast Magic (he regrows when you stop hitting him, giant jaws of fangs snap shut along a
 * line, claws thrown across the arena, pounces), then Despair - unbridled rage, and at the very end he gathers all his
 * mana to blow himself up. Break it with damage in time, or get out of the circle.
 * <p>
 * Every move shows where it lands first (red rings and lines). Nothing breaks blocks; every effect is the mod's own.
 */
public final class BeastAI {
    private BeastAI() {}

    public static final int P_IDLE = 0, P_CLAW = 1, P_CHARGE = 2, P_ROAR = 3, P_SLAM = 4, P_GUARD = 5, P_THROW = 6, P_BITE = 7,
            P_GATHER = 8, P_BROKEN = 9;
    static final int BEAST = Fx.WIND, FANG = 15, WARN = 18;
    private static final UUID SLOW = UUID.fromString("7e770001-0000-4000-8000-0000000000a1");
    private static final UUID WEAK = UUID.fromString("7e770001-0000-4000-8000-0000000000a2");
    private static final UUID RAGE = UUID.fromString("7e770001-0000-4000-8000-0000000000a3");
    private static final int FINAL_T = 120;

    public static final class State {
        public Vec3 home;
        UUID target;
        int busy;
        int farTicks, closeTicks;
        boolean introduced, finalDone;
        long lastSeen, lastHurt, lastShove, brokenUntil;
        float breakDmg;
    }

    // ================================================================== the tick
    public static void tick(BeastOfDespair b) {
        ServerLevel sl = (ServerLevel) b.level();
        State s = b.ai;
        long now = sl.getGameTime();
        if (s.home == null) s.home = b.position();
        if (b.armored() > 0) b.setArmored(b.armored() - 1);
        if (b.transforming() > 0) {
            b.setTransforming(b.transforming() - 1);
            b.getNavigation().stop();
            b.setDeltaMovement(0, Math.min(0, b.getDeltaMovement().y), 0);
            if (b.transforming() == 0) b.setPose(P_IDLE);
            return;
        }
        if (b.charging() > 0) { finalDespair(b, sl); return; }
        if (now < s.brokenUntil) {   // he lies broken after you stopped the Final Despair
            b.getNavigation().stop();
            if (b.pose() != P_BROKEN) b.setPose(P_BROKEN);
            return;
        }
        Player t = target(b);
        if (t == null) {
            b.getNavigation().stop();
            if (now - s.lastSeen > 600 && now % 200 == 0 && b.getHealth() < b.getMaxHealth()) b.heal(1f);   // left alone: 1 health every 10 s
            return;
        }
        s.lastSeen = now;
        if (!s.introduced) {
            s.introduced = true;
            say(b, "Hope? I'll tear it out of you. Despair is the only thing that's real.");
            sound(sl, b.position(), SoundEvents.RAVAGER_ROAR, 1.6f, 0.7f);
        }
        // Mythical Beast Magic: left alone for three seconds, he regrows (never past the top of his phase)
        if (b.phase() >= 2 && now - s.lastHurt > 60 && now % 10 == 0) {
            float cap = b.getMaxHealth() * (b.phase() == 2 ? 0.6f : 0.3f);
            if (b.getHealth() < cap) {
                b.heal(Math.min(1.0f, cap - b.getHealth()));
                if (now % 30 == 0) FxSend.ring(sl, BEAST, b.position().add(0, 0.1, 0), 0.4f, 1.6f, 0.06f, 10);
            }
        }
        // the last of him: everything he has left, gathered to blow
        if (b.phase() == 3 && !s.finalDone && b.getHealth() < b.getMaxHealth() * 0.12f && s.busy <= 0) { startFinal(b, sl); return; }
        b.getLookControl().setLookAt(t, 30f, 30f);
        double dist = b.distanceTo(t);
        if (dist > 10) s.farTicks++; else s.farTicks = Math.max(0, s.farTicks - 2);
        if (dist < 3) s.closeTicks++; else s.closeTicks = Math.max(0, s.closeTicks - 2);
        if (s.busy > 0) { s.busy--; return; }
        double want = b.phase() == 2 ? 6 : 2.6;   // a brawler; at range only when the third eye is open
        if (dist > want + 1.5) b.getNavigation().moveTo(t, b.phase() == 3 ? 1.25 : 1.05);
        else b.getNavigation().stop();
        choose(b, sl, t, dist);
    }

    private static Player target(BeastOfDespair b) {
        State s = b.ai;
        ServerLevel sl = (ServerLevel) b.level();
        if (s.target != null) {
            Player p = sl.getPlayerByUUID(s.target);
            if (valid(b, p)) return p;
            s.target = null;
        }
        Player best = null;
        double bd = 40 * 40;
        for (Player p : sl.players()) {
            if (!valid(b, p)) continue;
            double d = p.distanceToSqr(b);
            if (d < bd) { bd = d; best = p; }
        }
        if (best != null) s.target = best.getUUID();
        return best;
    }

    private static boolean valid(BeastOfDespair b, Player p) {
        return p != null && p.isAlive() && !p.isCreative() && !p.isSpectator() && p.level() == b.level() && p.distanceToSqr(b) < 48 * 48;
    }

    public static void onHurt(BeastOfDespair b, DamageSource src, float amount) {
        State s = b.ai;
        ServerLevel sl = (ServerLevel) b.level();
        long now = sl.getGameTime();
        s.lastHurt = now;
        if (src.getEntity() instanceof Player p && valid(b, p)) s.target = p.getUUID();
        if (b.charging() > 0) {   // every blow chips at the gathering mana
            s.breakDmg += amount;
            if (s.breakDmg >= breakNeed(b)) breakFinal(b, sl);
            return;
        }
        // Rhinoceros Armor: hit him up close and he throws you off
        if (b.armored() > 0 && src.getEntity() instanceof Player p && p.distanceTo(b) < 4.5 && now - s.lastShove > 20) {
            s.lastShove = now;
            Vec3 away = flat(p.position().subtract(b.position()));
            if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
            p.setDeltaMovement(away.normalize().scale(1.3).add(0, 0.45, 0));
            p.hurtMarked = true;
            hurt(b, p, 4f);
            FxSend.ring(sl, BEAST, b.position().add(0, 1.0, 0), 0.5f, 3f, 0.12f, 8);
            sound(sl, b.position(), SoundEvents.SHIELD_BLOCK, 1.2f, 0.6f);
        }
    }

    /** How much more he takes while lying broken. */
    public static float damageTaken(BeastOfDespair b) {
        return b.level().getGameTime() < b.ai.brokenUntil ? 1.5f : 1f;
    }

    // ================================================================== choosing a move
    private interface Move { int run(BeastOfDespair b, ServerLevel sl, Player t); }

    private static void choose(BeastOfDespair b, ServerLevel sl, Player t, double dist) {
        State s = b.ai;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int ph = b.phase();
        int took;
        if (s.farTicks > 90) {   // kept at range: he closes it
            s.farTicks = 0;
            took = ph == 1 ? cheetahCharge(b, sl, t) : r.nextBoolean() ? pounce(b, sl, t) : clawWave(b, sl, t);
        } else if (s.closeTicks > 60) {   // crowded: he roars you off, or hardens
            s.closeTicks = 0;
            took = r.nextBoolean() ? roar(b, sl, t) : rhinoArmor(b, sl, t);
        } else {
            List<Move> pool = new ArrayList<>();
            if (dist < 4.5) { pool.add(BeastAI::bearClaw); pool.add(BeastAI::bearClaw); }
            pool.add(BeastAI::cheetahCharge);
            pool.add(BeastAI::beastSlam);
            if (b.armored() <= 0 && r.nextInt(3) == 0) pool.add(BeastAI::rhinoArmor);
            if (r.nextInt(3) == 0) pool.add(BeastAI::roar);
            if (ph >= 2) {
                pool.add(BeastAI::despairFang);
                pool.add(BeastAI::clawWave);
                if (dist > 7) pool.add(BeastAI::pounce);
            }
            if (ph == 3) { pool.add(BeastAI::rampage); pool.add(BeastAI::rampage); }
            took = pool.get(r.nextInt(pool.size())).run(b, sl, t);
        }
        int rest = ph == 1 ? 30 : ph == 2 ? 24 : 14;
        s.busy = took + rest;
    }

    // ================================================================== shared pieces
    private static int tel(BeastOfDespair b, int ticks) { return b.phase() == 3 ? Math.max(6, (int) (ticks * 0.75)) : ticks; }

    static void warnCircle(ServerLevel sl, Vec3 c, float r, int ticks) {
        Vec3 g = ZenonAI.ground(sl, c).add(0, 0.06, 0);
        FxSend.ring(sl, WARN, g, r - 0.1f, r, 0.04f, ticks);
        FxSend.ring(sl, WARN, g, 0.05f, r, 0.025f, ticks);
    }

    static void warnLine(ServerLevel sl, Vec3 a, Vec3 c, float w, int ticks) { ZenonAI.warnLine(sl, a, c, w, ticks); }

    static float scale(BeastOfDespair b) {
        Difficulty d = b.level().getDifficulty();
        return (d == Difficulty.EASY ? 0.6f : d == Difficulty.HARD ? 1.25f : d == Difficulty.PEACEFUL ? 0.4f : 1f) * Bounties.damageMult(b);
    }

    static void hurt(BeastOfDespair b, Player p, float dmg) {
        if (!b.isAlive() || !p.isAlive()) return;
        p.hurt(b.damageSources().mobAttack(b), dmg * scale(b));
    }

    static List<Player> near(BeastOfDespair b, Vec3 c, double r) {
        List<Player> out = new ArrayList<>();
        for (Player p : b.level().getEntitiesOfClass(Player.class, new AABB(c, c).inflate(r))) {
            if (p.isAlive() && !p.isCreative() && !p.isSpectator() && p.position().add(0, 0.9, 0).distanceTo(c) <= r + 0.6) out.add(p);
        }
        return out;
    }

    static List<Player> along(BeastOfDespair b, Vec3 a, Vec3 c, double w) {
        List<Player> out = new ArrayList<>();
        for (Player p : b.level().getEntitiesOfClass(Player.class, new AABB(a, c).inflate(w + 1))) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;
            if (closestOnSegment(a, c, mid(p)).distanceTo(mid(p)) <= w) out.add(p);
        }
        return out;
    }

    static void say(BeastOfDespair b, String line) {
        for (Player p : b.level().getEntitiesOfClass(Player.class, b.getBoundingBox().inflate(48))) {
            p.sendSystemMessage(Component.literal("The Beast of Despair: ").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD)
                    .append(Component.literal(line).withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC)));
        }
    }

    private static boolean ok(BeastOfDespair b) { return b.isAlive() && b.transforming() == 0 && b.charging() == 0 && b.level().getGameTime() >= b.ai.brokenUntil; }

    private static Vec3 facing(BeastOfDespair b, Player t) {
        Vec3 d = flat(t.position().subtract(b.position()));
        return d.lengthSqr() < 1e-4 ? flat(b.getLookAngle()).normalize() : d.normalize();
    }

    private static void face(BeastOfDespair b, Vec3 dir) {
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        b.setYRot(yaw); b.setYHeadRot(yaw); b.yBodyRot = yaw;
    }

    /** Three claw marks at once: one long glowing rake per finger, side by side. */
    private static void rake(ServerLevel sl, Vec3 c, Vec3 fwd, double tilt, float r, int pal) {
        Vec3 side = rightOf(fwd);
        Vec3 up = new Vec3(0, 1, 0).scale(Math.cos(tilt)).add(side.scale(Math.sin(tilt))).normalize();
        for (int k = -1; k <= 1; k++) FxSend.slash(sl, pal, c.add(up.scale(k * 0.32)), fwd, up, r, 0.25f, 7, 1.6f, 0.6f);
        FxSend.slash(sl, FANG, c, fwd, up, r * 0.9f, 0.2f, 5, 1.6f, 0.3f);
    }

    // ================================================================== phase 1: Beast Magic
    /** Bear Claw: he lunges and rakes three times, the last throwing you back. */
    static int bearClaw(BeastOfDespair b, ServerLevel sl, Player t) {
        int T = tel(b, 10);
        b.setPose(P_CLAW);
        b.getNavigation().stop();
        Vec3 fwd = facing(b, t);
        face(b, fwd);
        warnLine(sl, b.position().add(0, 0.1, 0), b.position().add(fwd.scale(4.5)).add(0, 0.1, 0), 1.6f, T);
        sound(sl, b.position(), SoundEvents.POLAR_BEAR_WARNING, 1.4f, 0.6f);
        for (int i = 0; i < 3; i++) {
            final int k = i;
            AstaFx.later(T + i * 6, () -> {
                if (!ok(b)) return;
                Vec3 f = fwd;
                Player tt = target(b);
                if (tt != null) { f = facing(b, tt); face(b, f); }
                b.setDeltaMovement(f.scale(0.55).add(0, 0.05, 0));
                b.hurtMarked = true;
                Vec3 c = b.position().add(0, 1.4, 0).add(f.scale(1.5));
                rake(sl, c, f, k == 1 ? -0.7 : 0.7, 2.6f, BEAST);
                sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.6f + k * 0.1f);
                sound(sl, c, SoundEvents.RAVAGER_ATTACK, 0.9f, 1.1f);
                for (Player p : near(b, c, 2.6)) {
                    hurt(b, p, k == 2 ? 9f : 6f);
                    if (k == 2) { p.setDeltaMovement(f.scale(1.1).add(0, 0.35, 0)); p.hurtMarked = true; }
                }
            });
        }
        return T + 18;
    }

    /** Cheetah Charge: a red line across the arena, then he is down it in a blur. */
    static int cheetahCharge(BeastOfDespair b, ServerLevel sl, Player t) {
        int T = tel(b, 18);
        b.setPose(P_CHARGE);
        b.getNavigation().stop();
        Vec3 fwd = facing(b, t);
        face(b, fwd);
        Vec3 a = b.position();
        Vec3 end = clip(sl, b, a.add(0, 1, 0), a.add(0, 1, 0).add(fwd.scale(Math.max(10, b.distanceTo(t) + 5)).add(fwd.scale(2)))).subtract(0, 1, 0).subtract(fwd.scale(0.8));
        warnLine(sl, a.add(0, 0.1, 0), end.add(0, 0.1, 0), 1.4f, T);
        sound(sl, a, SoundEvents.RAVAGER_ROAR, 1.2f, 1.3f);
        final int STEPS = 7;
        Set<UUID> hit = new HashSet<>();
        for (int k = 1; k <= STEPS; k++) {
            final double u = k / (double) STEPS, u0 = (k - 1) / (double) STEPS;
            AstaFx.later(T + k, () -> {
                if (!ok(b)) return;
                Vec3 p0 = a.add(end.subtract(a).scale(u0)), p1 = a.add(end.subtract(a).scale(u));
                b.moveTo(p1.x, p1.y, p1.z, b.getYRot(), b.getXRot());
                b.setDeltaMovement(Vec3.ZERO);
                FxSend.trail(sl, BEAST, p0.add(0, 1.2, 0), p1.add(0, 1.2, 0), 0.9f, 8);
                FxSend.trail(sl, BEAST, p0.add(0, 0.4, 0), p1.add(0, 0.4, 0), 0.4f, 6);
                for (Player p : along(b, p0.add(0, 1, 0), p1.add(0, 1, 0), 1.5)) {
                    if (!hit.add(p.getUUID())) continue;
                    hurt(b, p, 11f);
                    Vec3 sideways = rightOf(fwd).scale(rightOf(fwd).dot(p.position().subtract(p0)) >= 0 ? 1 : -1);
                    p.setDeltaMovement(sideways.scale(1.0).add(fwd.scale(0.6)).add(0, 0.5, 0));
                    p.hurtMarked = true;
                }
            });
        }
        AstaFx.later(T + 1, () -> sound(sl, a, SoundEvents.RAVAGER_STEP, 1.5f, 1.4f));
        AstaFx.later(T + STEPS + 1, () -> {
            if (!b.isAlive()) return;
            FxSend.ring(sl, BEAST, end.add(0, 0.1, 0), 0.3f, 3f, 0.1f, 10);
            FxSend.shake(sl, end, 0.4f, 6, 16);
            sound(sl, end, SoundEvents.RAVAGER_STEP, 1.6f, 0.7f);
        });
        return T + STEPS + 6;
    }

    /** Rhinoceros Armor: plates close over him - 70% less damage for 5 s, and hitting him up close gets you thrown. */
    static int rhinoArmor(BeastOfDespair b, ServerLevel sl, Player t) {
        b.setPose(P_GUARD);
        b.getNavigation().stop();
        b.setArmored(100);
        Vec3 c = b.position();
        FxSend.ring(sl, BEAST, c.add(0, 0.1, 0), 0.5f, 3.2f, 0.12f, 12);
        FxSend.ring(sl, FANG, c.add(0, 1.2, 0), 1.6f, 0.6f, 0.08f, 10);
        sound(sl, c, SoundEvents.ARMOR_EQUIP_NETHERITE, 1.6f, 0.6f);
        sound(sl, c, SoundEvents.IRON_GOLEM_REPAIR, 1.2f, 0.7f);
        return 24;
    }

    /** Beast Slam: he leaps onto where you stand and smashes the ground. */
    static int beastSlam(BeastOfDespair b, ServerLevel sl, Player t) { return leap(b, sl, t, tel(b, 16), 3.2f, 12f); }

    private static int leap(BeastOfDespair b, ServerLevel sl, Player t, int T, float R, float dmg) {
        b.setPose(P_SLAM);
        b.getNavigation().stop();
        Vec3 land = ZenonAI.ground(sl, t.position());
        warnCircle(sl, land, R, T + 9);
        Vec3 from = b.position();
        face(b, flat(land.subtract(from)).lengthSqr() < 1e-4 ? flat(b.getLookAngle()) : flat(land.subtract(from)).normalize());
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            sound(sl, from, SoundEvents.RAVAGER_ROAR, 0.8f, 1.2f);
            double height = 3 + from.distanceTo(land) * 0.25;
            for (int k = 1; k <= 8; k++) {
                final double u = k / 8.0;
                AstaFx.later(k, () -> {
                    if (!b.isAlive()) return;
                    Vec3 p = from.add(land.subtract(from).scale(u)).add(0, Math.sin(u * Math.PI) * height, 0);
                    b.moveTo(p.x, p.y, p.z, b.getYRot(), b.getXRot());
                    b.setDeltaMovement(Vec3.ZERO);
                });
            }
            AstaFx.later(9, () -> {
                if (!b.isAlive()) return;
                FxSend.ring(sl, BEAST, land.add(0, 0.1, 0), 0.4f, R + 1.5f, 0.18f, 12);
                FxSend.ring(sl, FANG, land.add(0, 0.12, 0), 0.3f, R, 0.06f, 10);
                FxSend.cracks(sl, BEAST, land.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), R, 40, false, b.getId() * 13 + (int) sl.getGameTime());
                FxSend.shake(sl, land, 0.7f, 10, 22);
                sound(sl, land, SoundEvents.GENERIC_EXPLODE, 1f, 0.7f);
                for (Player p : near(b, land.add(0, 1, 0), R)) { hurt(b, p, dmg); p.setDeltaMovement(p.getDeltaMovement().add(0, 0.7, 0)); p.hurtMarked = true; }
            });
        });
        return T + 14;
    }

    /** A roar of despair: rings of force push you back; for a while you are slower and hit softer. */
    static int roar(BeastOfDespair b, ServerLevel sl, Player t) {
        int T = tel(b, 12);
        b.setPose(P_ROAR);
        b.getNavigation().stop();
        Vec3 c = b.position();
        warnCircle(sl, c, 9f, T);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            for (int i = 0; i < 3; i++) {
                final int k = i;
                AstaFx.later(i * 3, () -> FxSend.ring(sl, k == 1 ? FANG : BEAST, c.add(0, 1.2 + k * 0.3, 0), 0.6f, 9.5f, 0.1f, 10));
            }
            FxSend.impactFrame(sl, c.add(0, 1.6, 0), 2, FxSend.FRAME_FANGS, 14);
            FxSend.shake(sl, c, 0.5f, 14, 20);
            sound(sl, c, SoundEvents.RAVAGER_ROAR, 2.0f, 0.5f);
            sound(sl, c, SoundEvents.ENDER_DRAGON_GROWL, 0.8f, 0.6f);
            for (Player p : near(b, c.add(0, 1, 0), 9)) {
                Vec3 away = flat(p.position().subtract(c));
                if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
                p.setDeltaMovement(away.normalize().scale(1.0).add(0, 0.3, 0));
                p.hurtMarked = true;
                hurt(b, p, 3f);
                TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, SLOW, "The Beast's roar", -0.35, AttributeModifier.Operation.MULTIPLY_TOTAL, 60);
                TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, WEAK, "Despair", -0.25, AttributeModifier.Operation.MULTIPLY_TOTAL, 100);
            }
        });
        return T + 14;
    }

    // ================================================================== phase 2: Mythical Beast Magic
    /** Despair Fang: two rows of huge fangs burst up along a line and snap shut on it like jaws. */
    static int despairFang(BeastOfDespair b, ServerLevel sl, Player t) {
        int T = tel(b, 18);
        b.setPose(P_BITE);
        b.getNavigation().stop();
        Vec3 fwd = facing(b, t);
        face(b, fwd);
        Vec3 side = rightOf(fwd);
        Vec3 a = b.position().add(fwd.scale(1.5));
        double len = 15;
        Vec3 end = a.add(fwd.scale(len));
        warnLine(sl, a.add(0, 0.1, 0), end.add(0, 0.1, 0), 2.4f, T);
        sound(sl, b.position(), SoundEvents.EVOKER_PREPARE_ATTACK, 1.2f, 0.6f);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            sound(sl, a, SoundEvents.EVOKER_FANGS_ATTACK, 1.6f, 0.6f);
            int n = 10;
            for (int k = 0; k < n; k++) {
                final double u = (k + 0.5) / n;
                final int kk = k;
                AstaFx.later(k, () -> {
                    if (!b.isAlive()) return;
                    Vec3 c = ZenonAI.ground(sl, a.add(fwd.scale(len * u)).add(0, 1, 0));
                    for (int s = -1; s <= 1; s += 2) {   // the jaws: fangs leaning in from both sides
                        Vec3 base = c.add(side.scale(s * 1.7));
                        Vec3 dir = side.scale(-s * 0.75).add(0, 1, 0).normalize();
                        BoneFx.bone(sl, BoneFx.SPIKE, base.add(dir.scale(1.4)), dir, fwd, 1.4f, 0.28f, 20, 3, FxPacket.GROUND, 0, Vec3.ZERO, 0);
                    }
                    if (kk % 3 == 0) sound(sl, c, SoundEvents.EVOKER_FANGS_ATTACK, 1.0f, 0.8f);
                });
            }
            AstaFx.later(4, () -> {
                if (!b.isAlive()) return;
                for (Player p : along(b, a.add(0, 1, 0), end.add(0, 1, 0), 1.9)) {
                    hurt(b, p, 13f);
                    TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, SLOW, "Despair Fang", -0.4, AttributeModifier.Operation.MULTIPLY_TOTAL, 50);
                }
                FxSend.impactFrame(sl, a.add(fwd.scale(len * 0.5)).add(0, 1, 0), 2, FxSend.FRAME_FANGS, 18);
            });
        });
        return T + 14;
    }

    /** Claws thrown across the arena: three long rakes of beast mana fly out in a fan. */
    static int clawWave(BeastOfDespair b, ServerLevel sl, Player t) {
        int T = tel(b, 14);
        b.setPose(P_THROW);
        b.getNavigation().stop();
        Vec3 fwd = facing(b, t);
        face(b, fwd);
        Vec3 from = b.position().add(0, 1.3, 0);
        double range = 24, speed = 1.3;
        List<Vec3> dirs = List.of(rotY(fwd, -0.3), fwd, rotY(fwd, 0.3));
        for (Vec3 d : dirs) warnLine(sl, from.add(0, -1.2, 0), from.add(d.scale(range)).add(0, -1.2, 0), 0.9f, T);
        sound(sl, from, SoundEvents.POLAR_BEAR_WARNING, 1.4f, 0.5f);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            sound(sl, from, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.5f);
            for (Vec3 d : dirs) fly(b, sl, from, d, speed, (int) (range / speed), new boolean[1]);
        });
        return T + 10;
    }

    private static void fly(BeastOfDespair b, ServerLevel sl, Vec3 at, Vec3 d, double speed, int left, boolean[] done) {
        if (left <= 0 || done[0] || !b.isAlive()) return;
        AstaFx.later(1, () -> {
            Vec3 next = at.add(d.scale(speed));
            Vec3 wall = clip(sl, b, at, next);
            rake(sl, at, d, 0, 1.5f, BEAST);
            for (Player p : along(b, at, next, 1.3)) { hurt(b, p, 9f); done[0] = true; }
            if (done[0] || wall.distanceToSqr(next) > 1e-4) { FxSend.ring(sl, BEAST, wall, 0.2f, 1.6f, 0.08f, 8); return; }
            fly(b, sl, next, d, speed, left - 1, done);
        });
    }

    /** A pounce from far off onto the player furthest from him. */
    static int pounce(BeastOfDespair b, ServerLevel sl, Player t) {
        Player far = t;
        double fd = 0;
        for (Player p : near(b, b.position(), 30)) { double d = p.distanceTo(b); if (d > fd) { fd = d; far = p; } }
        return leap(b, sl, far, tel(b, 14), 3.6f, 14f);
    }

    // ================================================================== phase 3: Despair
    /** Unbridled rage: claws, straight into a charge, straight into a slam. */
    static int rampage(BeastOfDespair b, ServerLevel sl, Player t) {
        int a = bearClaw(b, sl, t);
        AstaFx.later(a, () -> {
            if (!ok(b)) return;
            Player tt = target(b);
            if (tt == null) return;
            int c = cheetahCharge(b, sl, tt);
            AstaFx.later(c, () -> {
                if (!ok(b)) return;
                Player t3 = target(b);
                if (t3 != null) beastSlam(b, sl, t3);
            });
        });
        return a + tel(b, 18) + 13 + tel(b, 16) + 14;
    }

    private static float breakNeed(BeastOfDespair b) { return 100f; }

    private static void startFinal(BeastOfDespair b, ServerLevel sl) {
        State s = b.ai;
        s.finalDone = true;
        s.breakDmg = 0;
        b.setCharging(1);
        b.setPose(P_GATHER);
        b.getNavigation().stop();
        Vec3 c = b.position();
        say(b, "If I'm going down, I'm taking every one of you with me!");
        warnCircle(sl, c, 14f, FINAL_T);
        FxSend.impactFrame(sl, c.add(0, 1.6, 0), 3, FxSend.FRAME_FANGS, 30);
        sound(sl, c, SoundEvents.WARDEN_SONIC_CHARGE, 2.0f, 0.5f);
        for (Player p : near(b, c, 40)) p.displayClientMessage(Component.literal("Break his Final Despair - or get clear!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
    }

    private static void finalDespair(BeastOfDespair b, ServerLevel sl) {
        State s = b.ai;
        int k = b.charging();
        b.setCharging(k + 1);
        b.getNavigation().stop();
        b.setDeltaMovement(0, Math.min(0, b.getDeltaMovement().y), 0);
        Vec3 c = b.position();
        if (k % 6 == 0) {   // mana pulled in from the edge, tighter and faster as it builds
            float r = 14f - 10f * k / FINAL_T;
            FxSend.ring(sl, k % 12 == 0 ? WARN : BEAST, c.add(0, 0.2 + k * 0.01, 0), r, 0.6f, 0.1f, 8);
            Kit.orb(sl, BEAST, c.add(0, 1.5, 0), Vec3.ZERO, 0.4f + 1.4f * k / FINAL_T, 7);
            sound(sl, c, SoundEvents.WARDEN_HEARTBEAT, 1.8f, 0.6f + k * 0.006f);
        }
        if (k % 20 == 0) {
            int left = (FINAL_T - k) / 20;
            float need = breakNeed(b);
            for (Player p : near(b, c, 40)) p.displayClientMessage(Component.literal("Final Despair: " + left + " s   -   break it: "
                    + (int) Math.min(100, s.breakDmg / need * 100) + "%").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        }
        if (k >= FINAL_T) explode(b, sl);
    }

    private static void breakFinal(BeastOfDespair b, ServerLevel sl) {
        State s = b.ai;
        b.setCharging(0);
        s.brokenUntil = sl.getGameTime() + 100;
        b.setPose(P_BROKEN);
        Vec3 c = b.position();
        say(b, "No... how... how is there still hope in you...?");
        FxSend.ring(sl, BEAST, c.add(0, 1, 0), 0.5f, 6f, 0.15f, 12);
        FxSend.impactFrame(sl, c.add(0, 1.4, 0), 3, FxSend.FRAME_ASTA, 24);
        sound(sl, c, SoundEvents.GLASS_BREAK, 2.0f, 0.5f);
        sound(sl, c, SoundEvents.RAVAGER_STUNNED, 1.6f, 0.7f);
        for (Player p : near(b, c, 40)) p.displayClientMessage(Component.literal("Broken! He takes 50% more for 5 s").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
    }

    private static void explode(BeastOfDespair b, ServerLevel sl) {
        State s = b.ai;
        b.setCharging(0);
        Vec3 c = b.position();
        FxSend.ring(sl, WARN, c.add(0, 0.2, 0), 0.5f, 16f, 0.5f, 18);
        FxSend.ring(sl, BEAST, c.add(0, 1.5, 0), 0.5f, 14f, 0.3f, 16);
        FxSend.wall(sl, BEAST, c, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 1f, 14f, -180, 180, 10f, 14, 0, 0);
        Fx.flash(sl, c.add(0, 1.5, 0), 3f);
        FxSend.impactFrame(sl, c.add(0, 1.5, 0), 6, FxSend.FRAME_FANGS, 40);
        FxSend.shake(sl, c, 1.4f, 24, 48);
        sound(sl, c, SoundEvents.GENERIC_EXPLODE, 3.0f, 0.5f);
        sound(sl, c, SoundEvents.WITHER_BREAK_BLOCK, 1.5f, 0.6f);
        for (Player p : near(b, c.add(0, 1, 0), 14)) {
            p.hurt(b.damageSources().mobAttack(b), p.getMaxHealth() * 0.7f * Math.min(1f, scale(b)));
            Vec3 away = flat(p.position().subtract(c));
            if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
            p.setDeltaMovement(away.normalize().scale(1.6).add(0, 0.7, 0));
            p.hurtMarked = true;
        }
        s.brokenUntil = sl.getGameTime() + 60;   // spent: it took almost everything he had
        AstaFx.later(20, () -> { if (b.isAlive()) say(b, "...Still breathing? Then drown in despair a little longer."); });
    }

    // ================================================================== transformations
    public static void transform(BeastOfDespair b, int phase) {
        if (b.transforming() > 0 || b.phase() >= phase) return;
        ServerLevel sl = (ServerLevel) b.level();
        b.setPhase(phase);
        b.setTransforming(60);
        b.setPose(P_ROAR);
        b.setArmored(0);
        b.ai.busy = 0;
        b.getNavigation().stop();
        Vec3 c = b.position();
        for (Player p : near(b, c.add(0, 1, 0), 5)) {
            Vec3 away = flat(p.position().subtract(c));
            if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
            p.setDeltaMovement(away.normalize().scale(1.2).add(0, 0.4, 0));
            p.hurtMarked = true;
        }
        if (phase == 2) {
            say(b, "You made me open it... Now see what a real beast looks like.");
            b.heal(b.getMaxHealth() * 0.08f);
            for (int k = 0; k < 10; k++) {
                final int kk = k;
                AstaFx.later(k * 5, () -> {   // beast mana pours up out of the ground into him
                    double a = kk * Math.PI / 5;
                    Vec3 e = c.add(Math.cos(a) * 7, 0.2, Math.sin(a) * 7);
                    FxSend.trail(sl, BEAST, e, c.add(0, 2.2, 0), 0.25f, 10);
                    sound(sl, c, SoundEvents.WOLF_GROWL, 1.2f, 0.5f + kk * 0.04f);
                });
            }
            AstaFx.later(50, () -> {
                FxSend.impactFrame(sl, c.add(0, 2.0, 0), 4, FxSend.FRAME_FANGS, 28);
                FxSend.ring(sl, BEAST, c.add(0, 0.1, 0), 0.5f, 10f, 0.3f, 16);
                Fx.flash(sl, c.add(0, 2.2, 0), 1.6f);
                FxSend.shake(sl, c, 0.8f, 12, 30);
                sound(sl, c, SoundEvents.RAVAGER_ROAR, 2.0f, 0.4f);
                sound(sl, c, SoundEvents.BEACON_ACTIVATE, 1.0f, 0.5f);
            });
        } else {
            say(b, "Struggle all you want. Everything gets swallowed by despair in the end.");
            TimedModifiers.apply(b, Attributes.MOVEMENT_SPEED, RAGE, "Unbridled rage", 0.3, AttributeModifier.Operation.MULTIPLY_TOTAL, 20 * 1200);
            for (int k = 0; k < 12; k++) {
                final int kk = k;
                AstaFx.later(k * 4, () -> {
                    double a = kk * Math.PI / 6;
                    FxSend.trail(sl, WARN, c.add(0, 1.2, 0), c.add(Math.cos(a) * 6, 0.3, Math.sin(a) * 6), 0.2f, 10);
                    sound(sl, c, SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.6f + kk * 0.03f);
                });
            }
            AstaFx.later(50, () -> {
                FxSend.impactFrame(sl, c.add(0, 2.0, 0), 5, FxSend.FRAME_FANGS, 34);
                FxSend.ring(sl, WARN, c.add(0, 0.1, 0), 0.5f, 12f, 0.4f, 18);
                FxSend.ring(sl, BEAST, c.add(0, 0.12, 0), 0.5f, 9f, 0.15f, 16);
                FxSend.cracks(sl, WARN, c.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), 6f, 60, false, b.getId() * 7);
                FxSend.shake(sl, c, 1.0f, 16, 40);
                sound(sl, c, SoundEvents.RAVAGER_ROAR, 2.0f, 0.3f);
                sound(sl, c, SoundEvents.ENDER_DRAGON_GROWL, 1.0f, 0.5f);
            });
        }
    }

    // ================================================================== death
    public static void dying(BeastOfDespair b, int t) {
        ServerLevel sl = (ServerLevel) b.level();
        Vec3 c = b.position();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (t == 1) {
            b.setPose(P_BROKEN);
            b.setArmored(0);
            b.setCharging(0);
            say(b, "Hope... is only... a longer road to despair...");
            sound(sl, c, SoundEvents.RAVAGER_DEATH, 1.6f, 0.5f);
            FxSend.impactFrame(sl, c.add(0, 1.5, 0), 4, FxSend.FRAME_FANGS, 26);
        }
        if (t < 60 && t % 3 == 0) {   // the beast mana leaves him like breath on a cold day
            Kit.orb(sl, BEAST, c.add(r.nextDouble(-0.7, 0.7), 0.6 + r.nextDouble(1.8), r.nextDouble(-0.7, 0.7)), new Vec3(0, 0.07, 0), 0.25f, 18);
            if (t % 12 == 0) sound(sl, c, SoundEvents.WOLF_WHINE, 0.8f, 0.5f);
        }
        if (t == 60) {
            FxSend.ring(sl, BEAST, c.add(0, 0.1, 0), 0.3f, 4f, 0.1f, 14);
            sound(sl, c, SoundEvents.RAVAGER_STEP, 1.2f, 0.5f);
        }
    }
}
