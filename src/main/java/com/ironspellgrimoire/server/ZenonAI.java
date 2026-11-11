package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.entity.Zenon;
import com.ironspellgrimoire.network.FxPacket;
import java.util.ArrayList;
import java.util.List;
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
 * Zenon's fight: who he goes for, how he moves, which move he picks, the hazards he wakes in his arena, his
 * transformations, and how he dies.
 * <ul>
 *   <li><b>Telegraphs:</b> every move shows where it will land first - a red ring that fills to its edge, or a red line
 *       - and lands the moment the ring is full. Read it and step out.</li>
 *   <li><b>Phase intros:</b> he stops, speaks, transforms (he can't be hurt meanwhile), the music changes, the arena
 *       wakes something new.</li>
 *   <li><b>Arena hazards:</b> phase 1 - lines of bone spikes; phase 2 - rifts that drag you in; phase 3 - cracks of devil
 *       power splitting the ground. All telegraphed.</li>
 *   <li><b>He learns:</b> keep away and he closes the gap; hug him and he blasts you off and fights from range.</li>
 *   <li><b>Desperation:</b> below 10% in phase 3 he moves faster and his telegraphs are shorter.</li>
 * </ul>
 * Nothing he does breaks blocks; every effect is drawn by the mod's own renderer.
 */
public final class ZenonAI {
    private ZenonAI() {}

    public static final int P_IDLE = 0, P_CAST = 1, P_THROW = 2, P_SLAM = 3, P_SWEEP = 4, P_ROAR = 5, P_RAISE = 6, P_CLAW = 7;
    static final int BONE = 15, WARN = 18, VOID = Fx.VOID, DARK = Fx.DARK, SIL = Fx.SILVER;
    static final double ARENA = 16;

    /** The fight's state (lives on the entity). */
    public static final class State {
        public Vec3 home;
        UUID target;
        int busy;            // ticks until he may choose another move
        int farTicks, closeTicks;
        int hazard = 160;
        int finale = 200;
        boolean introduced, desperate;
        long lastSeen;
    }

    // ================================================================== the tick
    public static void tick(Zenon b) {
        ServerLevel sl = (ServerLevel) b.level();
        State s = b.ai;
        if (s.home == null) s.home = b.position();
        if (b.transforming() > 0) {
            b.setTransforming(b.transforming() - 1);
            b.getNavigation().stop();
            b.setDeltaMovement(0, Math.min(0, b.getDeltaMovement().y), 0);
            if (b.transforming() == 0) b.setPose(P_IDLE);
            return;
        }
        Player t = target(b);
        if (t == null) {
            b.getNavigation().stop();
            if (sl.getGameTime() - s.lastSeen > 600 && b.getHealth() < b.getMaxHealth()) b.heal(0.5f);   // left alone, he mends
            return;
        }
        s.lastSeen = sl.getGameTime();
        if (!s.introduced) {
            s.introduced = true;
            say(b, "Another one wanders into my ossuary. Stay - you'll fit right in.");
            sound(sl, b.position(), SoundEvents.WITHER_SKELETON_AMBIENT, 1.6f, 0.5f);
        }
        b.getLookControl().setLookAt(t, 30f, 30f);
        double dist = b.distanceTo(t);
        // learning: how does this player like to fight him?
        if (dist > 10) s.farTicks++; else s.farTicks = Math.max(0, s.farTicks - 2);
        if (dist < 3.5) s.closeTicks++; else s.closeTicks = Math.max(0, s.closeTicks - 2);
        // desperation
        if (b.phase() == 3 && !s.desperate && b.getHealth() < b.getMaxHealth() * 0.1f) {
            s.desperate = true;
            say(b, "...Then we end this together.");
            TimedModifiers.apply(b, Attributes.MOVEMENT_SPEED, UUID.fromString("5a0c0001-0000-4000-8000-0000000000d1"), "Desperation", 0.35, AttributeModifier.Operation.MULTIPLY_TOTAL, 20 * 600);
            FxSend.ring(sl, WARN, b.position().add(0, 0.1, 0), 0.4f, 8f, 0.2f, 14);
            sound(sl, b.position(), SoundEvents.WITHER_SPAWN, 1.0f, 0.6f);
        }
        hazards(b, sl);
        if (b.phase() == 3 && --s.finale <= 0 && s.busy <= 0) { s.finale = s.desperate ? 500 : 800; ossuaryFall(b, sl, t); return; }
        if (s.busy > 0) {
            s.busy--;
            return;
        }
        // moving: keep a fighting distance (closer in phase 3)
        double want = b.phase() == 3 ? 3.5 : 5.0;
        if (dist > want + 2) b.getNavigation().moveTo(t, 1.0);
        else if (dist < want - 2) {
            Vec3 away = flat(b.position().subtract(t.position()));
            if (away.lengthSqr() > 1e-4) b.getNavigation().moveTo(b.getX() + away.normalize().x * 3, b.getY(), b.getZ() + away.normalize().z * 3, 1.0);
        } else b.getNavigation().stop();
        choose(b, sl, t, dist);
    }

    private static Player target(Zenon b) {
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

    private static boolean valid(Zenon b, Player p) {
        return p != null && p.isAlive() && !p.isCreative() && !p.isSpectator() && p.level() == b.level() && p.distanceToSqr(b) < 48 * 48;
    }

    static void onHurtBy(Zenon b, LivingEntity att) {
        if (att instanceof Player p && valid(b, p)) b.ai.target = p.getUUID();
    }

    public static void onHurt(Zenon b, DamageSource src) {
        if (src.getEntity() instanceof LivingEntity le) onHurtBy(b, le);
    }

    // ================================================================== choosing a move
    private interface Move { int run(Zenon b, ServerLevel sl, Player t); }

    private static void choose(Zenon b, ServerLevel sl, Player t, double dist) {
        State s = b.ai;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int ph = b.phase();
        int took;
        // he learns: kept at range, he closes in; crowded, he blasts you off
        if (s.farTicks > 100) {
            s.farTicks = 0;
            took = ph == 1 ? leap(b, sl, t) : r.nextBoolean() ? teleportStrike(b, sl, t) : spaceFold(b, sl, t);
        } else if (s.closeTicks > 70) {
            s.closeTicks = 0;
            took = pushback(b, sl, t);
        } else {
            List<Move> pool = new ArrayList<>();
            if (ph <= 2) {
                pool.add(ZenonAI::spearVolley);
                pool.add(ZenonAI::spikeField);
                if (dist < 4.5) { pool.add(ZenonAI::claws); pool.add(ZenonAI::claws); }
                if (dist < 11) pool.add(ZenonAI::whips);
                if (r.nextInt(3) == 0) pool.add(ZenonAI::cage);
            }
            if (ph == 2) {
                pool.add(ZenonAI::teleportStrike);
                pool.add(ZenonAI::portalSpears);
                pool.add(ZenonAI::spatialCut);
                if (dist > 6) pool.add(ZenonAI::spaceFold);
            }
            if (ph == 3) {
                pool.add(ZenonAI::grasp);
                pool.add(ZenonAI::shroudRain);
                pool.add(ZenonAI::devilPulse);
                pool.add(ZenonAI::teleportStrike);
                if (dist < 7) { pool.add(ZenonAI::tailSweep); pool.add(ZenonAI::tailSweep); }
            }
            took = pool.get(r.nextInt(pool.size())).run(b, sl, t);
        }
        int rest = ph == 1 ? 34 : ph == 2 ? 26 : 20;
        if (s.desperate) rest = (int) (rest * 0.55);
        s.busy = took + rest;
    }

    // ================================================================== shared pieces
    /** Telegraph length, shorter when he's desperate. */
    private static int tel(Zenon b, int ticks) { return b.ai.desperate ? Math.max(6, (int) (ticks * 0.75)) : ticks; }

    /** A warning: a red ring on the ground at its full size, and a second ring filling out to it - when it's full, it lands. */
    static void warnCircle(ServerLevel sl, Vec3 c, float r, int ticks) {
        Vec3 g = ground(sl, c).add(0, 0.06, 0);
        FxSend.ring(sl, WARN, g, r - 0.1f, r, 0.04f, ticks);
        FxSend.ring(sl, WARN, g, 0.05f, r, 0.025f, ticks);
    }

    /** A warning along a line: a thin red line, and a brighter one over it at the very end. */
    static void warnLine(ServerLevel sl, Vec3 a, Vec3 c, float w, int ticks) {
        FxSend.trail(sl, WARN, a, c, w, ticks);
        AstaFx.later(Math.max(1, ticks - 4), () -> FxSend.trail(sl, WARN, a, c, w * 2f, 4));
    }

    /** The ground under {@code c} (a few blocks down at most). */
    static Vec3 ground(ServerLevel sl, Vec3 c) {
        net.minecraft.core.BlockPos.MutableBlockPos bp = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int i = 0; i < 6; i++) {
            bp.set(c.x, c.y - i - 0.01, c.z);
            if (!sl.getBlockState(bp).getCollisionShape(sl, bp).isEmpty()) return new Vec3(c.x, bp.getY() + 1.0, c.z);
        }
        return c;
    }

    static float scale(Zenon b) {
        Difficulty d = b.level().getDifficulty();
        return d == Difficulty.EASY ? 0.6f : d == Difficulty.HARD ? 1.25f : d == Difficulty.PEACEFUL ? 0.4f : 1f;
    }

    static void hurt(Zenon b, Player p, float dmg) {
        if (!b.isAlive() || !p.isAlive()) return;
        p.hurt(b.damageSources().mobAttack(b), dmg * scale(b));
    }

    static List<Player> near(Zenon b, Vec3 c, double r) {
        List<Player> out = new ArrayList<>();
        for (Player p : b.level().getEntitiesOfClass(Player.class, new AABB(c, c).inflate(r))) {
            if (p.isAlive() && !p.isCreative() && !p.isSpectator() && p.position().add(0, 0.9, 0).distanceTo(c) <= r + 0.6) out.add(p);
        }
        return out;
    }

    static List<Player> along(Zenon b, Vec3 a, Vec3 c, double w) {
        List<Player> out = new ArrayList<>();
        for (Player p : b.level().getEntitiesOfClass(Player.class, new AABB(a, c).inflate(w + 1))) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;
            if (closestOnSegment(a, c, mid(p)).distanceTo(mid(p)) <= w) out.add(p);
        }
        return out;
    }

    static void say(Zenon b, String line) {
        for (Player p : b.level().getEntitiesOfClass(Player.class, b.getBoundingBox().inflate(48))) {
            p.sendSystemMessage(Component.literal("Zenon: ").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD)
                    .append(Component.literal(line).withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC)));
        }
    }

    private static boolean ok(Zenon b) { return b.isAlive() && b.transforming() == 0; }

    private static void spikeUp(ServerLevel sl, Vec3 base, Vec3 dir, float len, float rad, int life) {
        dir = dir.normalize();
        BoneFx.bone(sl, BoneFx.SPIKE, base.add(dir.scale(len * 0.5)), dir, rightOf(dir), len * 0.5f, rad, life, 3, FxPacket.GROUND, 0, Vec3.ZERO, 0);
    }

    // ================================================================== phase 1: bone
    /** Three bone spears thrown in a fan at where you're going; each path is shown first. */
    static int spearVolley(Zenon b, ServerLevel sl, Player t) {
        int T = tel(b, 16);
        b.setPose(P_THROW);
        b.getNavigation().stop();
        Vec3 from = b.position().add(0, 1.6, 0);
        Vec3 aim = mid(t).add(t.getDeltaMovement().multiply(8, 0, 8));
        Vec3 dir = aim.subtract(from).normalize();
        Vec3[] dirs = {dir, rotY(dir, 0.22), rotY(dir, -0.22)};
        for (Vec3 d : dirs) warnLine(sl, from, from.add(d.scale(22)), 0.05f, T);
        sound(sl, from, SoundEvents.SKELETON_AMBIENT, 1.2f, 0.5f);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            sound(sl, from, SoundEvents.TRIDENT_THROW, 1.3f, 0.6f);
            for (Vec3 d : dirs) {
                BoneFx.bone(sl, BoneFx.SPEAR, from, d, rightOf(d), 1.1f, 0.11f, 12, 0, FxPacket.HOLD, 0, d.scale(1.9), 11);
                for (int k = 0; k < 11; k++) {
                    final int kk = k;
                    AstaFx.later(k, () -> {
                        if (!b.isAlive()) return;
                        Vec3 a = from.add(d.scale(1.9 * kk)), c = from.add(d.scale(1.9 * (kk + 1)));
                        for (Player p : along(b, a, c, 0.9)) if (p.invulnerableTime <= 10) { hurt(b, p, 6f); Fx.spark(sl, BONE, mid(p), 0.7f); }
                    });
                }
            }
        });
        return T + 12;
    }

    /** Close in: three rakes of bone claws in front of him. */
    static int claws(Zenon b, ServerLevel sl, Player t) {
        int T = tel(b, 12);
        b.setPose(P_CLAW);
        b.getNavigation().stop();
        Vec3 look = flat(t.position().subtract(b.position())).normalize();
        Vec3 front = b.position().add(look.scale(2.0));
        warnCircle(sl, front, 2.4f, T);
        sound(sl, b.position(), SoundEvents.SKELETON_STEP, 1.2f, 0.5f);
        for (int i = 0; i < 3; i++) {
            final int ii = i;
            AstaFx.later(T + i * 5, () -> {
                if (!ok(b)) return;
                Vec3 rt = rightOf(look);
                Vec3 sweep = rt.scale(ii % 2 == 0 ? 1 : -1).add(0, ii == 2 ? 0.9 : 0.35, 0).normalize();
                FxSend.slash(sl, BONE, b.position().add(0, 1.2, 0).add(look.scale(1.4)), look, sweep, 1.4f, 2.6f, 6, 0.1f, 0.9f);
                sound(sl, b.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.1f, 0.7f);
                for (Player p : near(b, front.add(0, 1, 0), 2.6)) { hurt(b, p, 7f); p.invulnerableTime = 0; }
            });
        }
        return T + 16;
    }

    /** Bone spikes erupt under you, and on either side of you. */
    static int spikeField(Zenon b, ServerLevel sl, Player t) {
        int T = tel(b, 20);
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        Vec3 c = t.position();
        Vec3 side = rightOf(flat(c.subtract(b.position())).normalize());
        Vec3[] spots = {c, c.add(side.scale(3.2)), c.subtract(side.scale(3.2))};
        for (Vec3 g : spots) warnCircle(sl, g, 1.7f, T);
        sound(sl, c, SoundEvents.BONE_BLOCK_PLACE, 1.4f, 0.5f);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (Vec3 g0 : spots) {
                Vec3 g = ground(sl, g0);
                for (int k = 0; k < 6; k++) {
                    double a = k * Math.PI / 3 + r.nextDouble(0.4);
                    spikeUp(sl, g.add(Math.cos(a) * 0.9, 0, Math.sin(a) * 0.9), new Vec3(Math.cos(a) * 0.3, 1, Math.sin(a) * 0.3), 1.8f + r.nextFloat(), 0.16f, 26);
                }
                spikeUp(sl, g, new Vec3(0, 1, 0), 2.8f, 0.24f, 26);
                for (Player p : near(b, g.add(0, 0.8, 0), 1.8)) { hurt(b, p, 8f); p.setDeltaMovement(p.getDeltaMovement().add(0, 0.7, 0)); p.hurtMarked = true; }
            }
            sound(sl, c, SoundEvents.BONE_BLOCK_BREAK, 1.5f, 0.6f);
        });
        return T + 8;
    }

    /** Two whips of linked vertebrae lash at where you stood. */
    static int whips(Zenon b, ServerLevel sl, Player t) {
        int T = tel(b, 12);
        b.setPose(P_SWEEP);
        b.getNavigation().stop();
        Vec3 from = b.position().add(0, 1.3, 0), to = mid(t);
        Vec3 side = rightOf(flat(to.subtract(from)).normalize());
        warnLine(sl, from, to.add(to.subtract(from).normalize().scale(1.5)), 0.08f, T);
        for (int w = 0; w < 2; w++) {
            final int ww = w;
            AstaFx.later(T + w * 4, () -> {
                if (!ok(b)) return;
                Vec3 bend = from.add(to).scale(0.5).add(side.scale(ww == 0 ? 1.8 : -1.8)).add(0, 1.2, 0);
                Vec3 end = to.add(to.subtract(from).normalize().scale(1.5));
                FxSend.trail(sl, BONE, from, bend, 0.14f, 6);
                FxSend.trail(sl, BONE, bend, end, 0.12f, 6);
                for (int k = 0; k < 6; k++) {
                    double u = k / 5.0;
                    Vec3 q = from.scale((1 - u) * (1 - u)).add(bend.scale(2 * u * (1 - u))).add(end.scale(u * u));
                    BoneFx.bone(sl, BoneFx.VERTEBRA, q, end.subtract(from), side, 0.18f, 0.1f, 6, 0, FxPacket.POP, 0, Vec3.ZERO, 0);
                }
                sound(sl, to, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1.3f);
                for (Player p : along(b, from, end, 1.4)) { hurt(b, p, 5f); p.invulnerableTime = 0; }
            });
        }
        return T + 10;
    }

    /** A ring marks the ground round you; still inside when it fills, and you're caged in bone for 2 s. */
    static int cage(Zenon b, ServerLevel sl, Player t) {
        int T = tel(b, 24);
        b.setPose(P_CAST);
        b.getNavigation().stop();
        Vec3 c = ground(sl, t.position());
        warnCircle(sl, c, 2.2f, T);
        sound(sl, c, SoundEvents.BONE_BLOCK_STEP, 1.5f, 0.5f);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            for (int k = 0; k < 10; k++) {
                double a = k * Math.PI / 5;
                BoneFx.bone(sl, BoneFx.LONG, c.add(Math.cos(a) * 2.1, 1.4, Math.sin(a) * 2.1), new Vec3(-Math.cos(a) * 0.25, 1, -Math.sin(a) * 0.25), new Vec3(Math.cos(a), 0, Math.sin(a)), 1.4f, 0.1f, 44, 4, FxPacket.GROUND, 0, Vec3.ZERO, 0);
            }
            for (int k = 0; k < 3; k++) {
                double a = k * Math.PI / 3;
                BoneFx.bone(sl, BoneFx.RIB, c.add(0, 3.0, 0), new Vec3(Math.cos(a), 0, Math.sin(a)), new Vec3(0, 1, 0), 2.1f, 0.08f, 44, 4, FxPacket.POP, 0, Vec3.ZERO, 0);
            }
            sound(sl, c, SoundEvents.BONE_BLOCK_PLACE, 1.5f, 0.4f);
            for (Player p : near(b, c.add(0, 1, 0), 2.2)) {
                hurt(b, p, 4f);
                TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, UUID.fromString("5a0c0001-0000-4000-8000-0000000000c1"), "Bone Cage", -0.9, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
            }
        });
        return T + 10;
    }

    /** Crowded: a burst of bone out of the ground all round him that throws you back. */
    static int pushback(Zenon b, ServerLevel sl, Player t) {
        int T = tel(b, 10);
        b.setPose(P_ROAR);
        b.getNavigation().stop();
        Vec3 c = ground(sl, b.position());
        warnCircle(sl, c, 4.2f, T);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            for (int k = 0; k < 14; k++) {
                double a = k * Math.PI * 2 / 14;
                spikeUp(sl, c.add(Math.cos(a) * 1.4, 0, Math.sin(a) * 1.4), new Vec3(Math.cos(a), 0.7, Math.sin(a)), 2.4f, 0.14f, 18);
            }
            FxSend.ring(sl, BONE, c.add(0, 0.1, 0), 0.5f, 5f, 0.15f, 10);
            FxSend.shake(sl, c, 0.4f, 6, 16);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.2f);
            for (Player p : near(b, c.add(0, 1, 0), 4.3)) {
                hurt(b, p, 5f);
                Vec3 away = flat(p.position().subtract(c));
                if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
                p.setDeltaMovement(away.normalize().scale(1.5).add(0, 0.5, 0));
                p.hurtMarked = true;
            }
        });
        return T + 8;
    }

    /** Kept at range: he bounds into the air and lands where you are, bone bursting out round him. */
    static int leap(Zenon b, ServerLevel sl, Player t) {
        int T = tel(b, 16);
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        Vec3 land = ground(sl, t.position());
        warnCircle(sl, land, 2.8f, T + 8);
        Vec3 from = b.position();
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            sound(sl, from, SoundEvents.RAVAGER_ROAR, 0.6f, 1.4f);
            for (int k = 1; k <= 8; k++) {
                final double u = k / 8.0;
                AstaFx.later(k, () -> {
                    if (!b.isAlive()) return;
                    Vec3 p = from.add(land.subtract(from).scale(u)).add(0, Math.sin(u * Math.PI) * 5, 0);
                    b.moveTo(p.x, p.y, p.z, b.getYRot(), b.getXRot());
                    b.setDeltaMovement(Vec3.ZERO);
                });
            }
            AstaFx.later(9, () -> {
                if (!ok(b)) return;
                b.setPose(P_SLAM);
                for (int k = 0; k < 10; k++) {
                    double a = k * Math.PI / 5;
                    spikeUp(sl, land.add(Math.cos(a) * 1.6, 0, Math.sin(a) * 1.6), new Vec3(Math.cos(a) * 0.5, 1, Math.sin(a) * 0.5), 2.0f, 0.16f, 22);
                }
                FxSend.ring(sl, BONE, land.add(0, 0.1, 0), 0.4f, 4.5f, 0.15f, 12);
                FxSend.shake(sl, land, 0.6f, 8, 20);
                sound(sl, land, SoundEvents.GENERIC_EXPLODE, 1f, 0.8f);
                for (Player p : near(b, land.add(0, 1, 0), 3.0)) { hurt(b, p, 9f); p.setDeltaMovement(p.getDeltaMovement().add(0, 0.6, 0)); p.hurtMarked = true; }
            });
        });
        return T + 14;
    }

    // ================================================================== phase 2: space
    /** He steps through space: a gate opens behind you, he comes out of it, and rakes. */
    static int teleportStrike(Zenon b, ServerLevel sl, Player t) {
        int T = tel(b, 12);
        b.setPose(P_CAST);
        b.getNavigation().stop();
        Vec3 face = flat(t.getLookAngle());
        if (face.lengthSqr() < 1e-4) face = new Vec3(1, 0, 0);
        Vec3 behind = ground(sl, t.position().subtract(face.normalize().scale(2.2)));
        final Vec3 f = face.normalize();
        SpaceFx.gate(sl, b.position().add(0, 1.2, 0), flat(t.position().subtract(b.position())).normalize(), 0.8f, 1.3f, T, 0);
        SpaceFx.gate(sl, behind.add(0, 1.2, 0), f, 0.8f, 1.3f, T + 6, 0);
        warnCircle(sl, behind.add(f.scale(1.8)), 2.0f, T + 4);
        sound(sl, behind, SoundEvents.ENDERMAN_TELEPORT, 1.2f, 0.5f);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            b.moveTo(behind.x, behind.y, behind.z, (float) Math.toDegrees(Math.atan2(-f.x, f.z)), 0);
            b.setPose(P_CLAW);
            FxSend.ring(sl, VOID, behind.add(0, 0.1, 0), 0.3f, 2.4f, 0.1f, 8);
        });
        AstaFx.later(T + 4, () -> {
            if (!ok(b)) return;
            Vec3 at = behind.add(0, 1.2, 0).add(f.scale(1.5));
            FxSend.slash(sl, BONE, at, f, rightOf(f).add(0, 0.5, 0).normalize(), 1.5f, 2.8f, 6, 0.1f, 1.0f);
            FxSend.slash(sl, VOID, at, f, rightOf(f).scale(-1).add(0, 0.5, 0).normalize(), 1.3f, 2.4f, 5, 0.1f, 0.6f);
            sound(sl, at, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.7f);
            for (Player p : near(b, at, 2.2)) hurt(b, p, 9f);
        });
        return T + 12;
    }

    /** Four gates open round you; from each, a bone spear at where you stood. */
    static int portalSpears(Zenon b, ServerLevel sl, Player t) {
        int T = tel(b, 18);
        b.setPose(P_CAST);
        b.getNavigation().stop();
        Vec3 c = mid(t);
        double a0 = ThreadLocalRandom.current().nextDouble(Math.PI);
        List<Vec3> gates = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            double a = a0 + k * Math.PI / 2;
            Vec3 g = c.add(Math.cos(a) * 5, 1.2 + (k % 2) * 0.8, Math.sin(a) * 5);
            gates.add(g);
            SpaceFx.gate(sl, g, c.subtract(g).normalize(), 0.5f, 0.5f, T + 6, 0);
            warnLine(sl, g, c.add(c.subtract(g).normalize().scale(3)), 0.04f, T);
        }
        sound(sl, c, SoundEvents.ENDERMAN_TELEPORT, 1.2f, 0.7f);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            for (Vec3 g : gates) {
                Vec3 d = c.subtract(g).normalize(), end = c.add(d.scale(3));
                BoneFx.bone(sl, BoneFx.SPEAR, g, d, rightOf(d), 1.0f, 0.1f, 6, 0, FxPacket.HOLD, 0, d.scale(1.6), 5);
                FxSend.trail(sl, VOID, g, end, 0.05f, 5);
                for (Player p : along(b, g, end, 1.1)) { hurt(b, p, 6f); p.invulnerableTime = 0; }
            }
            sound(sl, c, SoundEvents.TRIDENT_THROW, 1.3f, 0.7f);
        });
        return T + 8;
    }

    /** A cut through space straight across the arena, through where you stand. */
    static int spatialCut(Zenon b, ServerLevel sl, Player t) {
        int T = tel(b, 22);
        b.setPose(P_SWEEP);
        b.getNavigation().stop();
        Vec3 c = mid(t);
        Vec3 along = flat(c.subtract(b.position())).normalize();
        Vec3 across = rightOf(along);
        double a = ThreadLocalRandom.current().nextDouble(-0.6, 0.6);
        Vec3 dir = along.scale(Math.sin(a)).add(across.scale(Math.cos(a))).normalize();
        Vec3 p0 = c.subtract(dir.scale(12)), p1 = c.add(dir.scale(12));
        warnLine(sl, p0, p1, 0.12f, T);
        sound(sl, c, SoundEvents.BEACON_DEACTIVATE, 1.2f, 1.6f);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            RiftFx.cut(sl, c, dir, new Vec3(0, 1, 0), 12f, 14);
            FxSend.shake(sl, c, 0.4f, 6, 18);
            sound(sl, c, SoundEvents.GLASS_BREAK, 1.4f, 0.5f);
            for (Player p : along(b, p0, p1, 1.4)) hurt(b, p, 11f);
        });
        return T + 10;
    }

    /** Space folds: you're dragged across to him, and his claws are waiting. */
    static int spaceFold(Zenon b, ServerLevel sl, Player t) {
        int T = tel(b, 14);
        b.setPose(P_CAST);
        b.getNavigation().stop();
        FxSend.ring(sl, VOID, t.position().add(0, 0.08, 0), 2.2f, 0.2f, 0.08f, T);
        FxSend.ring(sl, WARN, b.position().add(0, 0.06, 0), 2.4f, 2.6f, 0.04f, T + 12);
        sound(sl, t.position(), SoundEvents.PORTAL_TRIGGER, 0.6f, 1.6f);
        AstaFx.later(T, () -> {
            if (!ok(b) || !t.isAlive()) return;
            for (int k = 0; k < 8; k++) {
                AstaFx.later(k, () -> {
                    if (!b.isAlive() || !t.isAlive()) return;
                    Vec3 to = b.position().subtract(t.position());
                    if (to.length() < 2.2) return;
                    t.setDeltaMovement(to.normalize().scale(1.1).add(0, 0.12, 0));
                    t.hurtMarked = true;
                    FxSend.trail(sl, VOID, mid(t), b.position().add(0, 1.2, 0), 0.04f, 3);
                });
            }
        });
        AstaFx.later(T + 10, () -> {
            if (!ok(b)) return;
            b.setPose(P_CLAW);
            Vec3 look = flat(t.position().subtract(b.position())).normalize();
            FxSend.slash(sl, BONE, b.position().add(0, 1.2, 0).add(look.scale(1.2)), look, rightOf(look).add(0, 0.6, 0).normalize(), 1.4f, 2.6f, 6, 0.1f, 0.9f);
            for (Player p : near(b, b.position().add(0, 1.2, 0).add(look.scale(1.5)), 2.4)) hurt(b, p, 8f);
            sound(sl, b.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
        });
        return T + 14;
    }

    // ================================================================== phase 3: devil union (his alone)
    /** Huge hands of bone burst up under you and close; caught, you're held a moment. */
    static int grasp(Zenon b, ServerLevel sl, Player t) {
        int T = tel(b, 20);
        b.setPose(P_SLAM);
        b.getNavigation().stop();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vec3 c = t.position();
        Vec3[] spots = {c, c.add(r.nextDouble(-3, 3), 0, r.nextDouble(-3, 3)), c.add(r.nextDouble(-3, 3), 0, r.nextDouble(-3, 3))};
        for (Vec3 g : spots) warnCircle(sl, g, 1.9f, T);
        sound(sl, c, SoundEvents.WARDEN_DIG, 1.2f, 0.7f);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            for (Vec3 g0 : spots) {
                Vec3 g = ground(sl, g0);
                for (int fgr = 0; fgr < 5; fgr++) {   // five fingers curling inward over the spot, a palm under them
                    double a = fgr * Math.PI * 2 / 5;
                    Vec3 base = g.add(Math.cos(a) * 1.5, 0, Math.sin(a) * 1.5);
                    Vec3 dir = new Vec3(-Math.cos(a) * 0.55, 1, -Math.sin(a) * 0.55).normalize();
                    BoneFx.bone(sl, BoneFx.LONG, base.add(dir.scale(1.2)), dir, new Vec3(-Math.cos(a), 0, -Math.sin(a)), 1.2f, 0.13f, 34, 4, FxPacket.GROUND, 0, Vec3.ZERO, 0);
                    BoneFx.bone(sl, BoneFx.SPIKE, base.add(dir.scale(2.4)).add(-Math.cos(a) * 0.4, 0.2, -Math.sin(a) * 0.4), new Vec3(-Math.cos(a), 0.2, -Math.sin(a)), new Vec3(0, 1, 0), 0.5f, 0.11f, 34, 6, FxPacket.GROUND, 0, Vec3.ZERO, 0);
                }
                FxSend.ring(sl, DARK, g.add(0, 0.06, 0), 0.3f, 2f, 0.12f, 14);
                for (Player p : near(b, g.add(0, 1, 0), 1.9)) {
                    hurt(b, p, 10f);
                    TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, UUID.fromString("5a0c0001-0000-4000-8000-0000000000c2"), "Grasp", -0.9, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
                }
            }
            sound(sl, c, SoundEvents.BONE_BLOCK_BREAK, 1.6f, 0.4f);
        });
        return T + 10;
    }

    /** His great bone tail sweeps all the way round him. */
    static int tailSweep(Zenon b, ServerLevel sl, Player t) {
        int T = tel(b, 16);
        b.setPose(P_SWEEP);
        b.getNavigation().stop();
        Vec3 c = ground(sl, b.position());
        warnCircle(sl, c, 6f, T);
        sound(sl, c, SoundEvents.RAVAGER_ATTACK, 1.0f, 0.6f);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            for (int k = 0; k < 6; k++) {
                final int kk = k;
                AstaFx.later(k, () -> {
                    double a = kk * Math.PI / 3;
                    Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
                    FxSend.slash(sl, BONE, c.add(0, 0.8, 0).add(d.scale(3)), d, rightOf(d), 1.6f, 3.2f, 6, 0.1f, 0.9f);
                    FxSend.slash(sl, DARK, c.add(0, 0.6, 0).add(d.scale(3)), d, rightOf(d), 1.4f, 3.0f, 5, 0.1f, 0.5f);
                });
            }
            FxSend.ring(sl, BONE, c.add(0, 0.1, 0), 0.5f, 6.5f, 0.2f, 10);
            sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6f, 0.4f);
            for (Player p : near(b, c.add(0, 1, 0), 6.0)) {
                hurt(b, p, 11f);
                Vec3 away = flat(p.position().subtract(c));
                if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
                p.setDeltaMovement(away.normalize().scale(1.3).add(0, 0.45, 0));
                p.hurtMarked = true;
            }
        });
        return T + 10;
    }

    /** His shroud tears: shards of bone rain down all round you, each spot marked first. */
    static int shroudRain(Zenon b, ServerLevel sl, Player t) {
        int T = tel(b, 18);
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Vec3> spots = new ArrayList<>();
        spots.add(t.position());
        for (int k = 0; k < 9; k++) spots.add(t.position().add(r.nextDouble(-6, 6), 0, r.nextDouble(-6, 6)));
        for (int k = 0; k < spots.size(); k++) {
            final Vec3 g = ground(sl, spots.get(k));
            final int delay = T + k * 2;
            AstaFx.later(k * 2, () -> warnCircle(sl, g, 1.3f, T));
            AstaFx.later(delay - 6, () -> BoneFx.bone(sl, BoneFx.SPIKE, g.add(0, 9, 0), new Vec3(0, -1, 0), new Vec3(1, 0, 0), 0.9f, 0.14f, 8, 0, FxPacket.HOLD, 0, new Vec3(0, -1.5, 0), 6));
            AstaFx.later(delay, () -> {
                if (!ok(b)) return;
                FxSend.ring(sl, BONE, g.add(0, 0.08, 0), 0.2f, 1.4f, 0.08f, 6);
                for (Player p : near(b, g.add(0, 0.8, 0), 1.3)) { hurt(b, p, 6f); p.invulnerableTime = 0; }
                sound(sl, g, SoundEvents.BONE_BLOCK_HIT, 1.0f, 0.7f);
            });
        }
        return T + spots.size() * 2 + 4;
    }

    /** He marks the ground round you with devil power; when the mark fills, it bursts. */
    static int devilPulse(Zenon b, ServerLevel sl, Player t) {
        int T = tel(b, 28);
        b.setPose(P_CAST);
        b.getNavigation().stop();
        Vec3 c = ground(sl, t.position());
        warnCircle(sl, c, 4f, T);
        FxSend.ring(sl, DARK, c.add(0, 0.05, 0), 3.9f, 4.1f, 0.06f, T);
        for (int k = 0; k < 3; k++) {
            final int kk = k;
            AstaFx.later(k * (T / 3), () -> { FxSend.ring(sl, Fx.CRIMSON, c.add(0, 0.07, 0), 4f, 0.5f, 0.05f, T / 3); sound(sl, c, SoundEvents.WARDEN_HEARTBEAT, 1.3f, 0.7f + kk * 0.1f); });
        }
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            FxSend.impactFrame(sl, c.add(0, 1, 0), 3, FxSend.FRAME_ZENON, 22);
            FxSend.ring(sl, DARK, c.add(0, 0.1, 0), 0.5f, 5.5f, 0.3f, 14);
            FxSend.ring(sl, Fx.CRIMSON, c.add(0, 0.12, 0), 0.3f, 4.5f, 0.12f, 12);
            FxSend.cracks(sl, Fx.CRIMSON, c.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), 3.5f, 30, false, b.getId());
            for (int k = 0; k < 10; k++) {
                double a = k * Math.PI / 5;
                spikeUp(sl, c.add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5), new Vec3(Math.cos(a) * 0.4, 1, Math.sin(a) * 0.4), 2.4f, 0.15f, 20);
            }
            FxSend.shake(sl, c, 0.7f, 10, 22);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 1.3f, 0.7f);
            for (Player p : near(b, c.add(0, 1, 0), 4.0)) { hurt(b, p, 14f); p.setDeltaMovement(p.getDeltaMovement().add(0, 0.8, 0)); p.hurtMarked = true; }
        });
        return T + 12;
    }

    /** His great move: the sky darkens, a huge ring hunts you, then locks - and a colossus of bone falls on it. */
    static int ossuaryFall(Zenon b, ServerLevel sl, Player t) {
        int follow = tel(b, 24), lock = tel(b, 18);
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        b.ai.busy = follow + lock + 30;
        say(b, ThreadLocalRandom.current().nextBoolean() ? "Kneel under the weight of every bone I've gathered." : "Look up.");
        FxSend.wall(sl, DARK, b.ai.home.add(0, 0.2, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 18f, 18f, -180, 180, 18f, follow + lock + 20, 0, 0);
        sound(sl, b.position(), SoundEvents.WITHER_SPAWN, 1.0f, 0.5f);
        for (int k = 0; k < follow; k += 3) {   // the ring follows you...
            AstaFx.later(k, () -> { if (ok(b) && t.isAlive()) FxSend.ring(sl, WARN, ground(sl, t.position()).add(0, 0.06, 0), 5.0f, 5.2f, 0.05f, 3); });
        }
        final Vec3[] spot = new Vec3[1];
        AstaFx.later(follow, () -> {   // ...then locks
            if (!ok(b)) return;
            spot[0] = ground(sl, t.isAlive() ? t.position() : b.position());
            warnCircle(sl, spot[0], 5.2f, lock);
            sound(sl, spot[0], SoundEvents.BEACON_POWER_SELECT, 1.4f, 0.5f);
            // the colossus forms high above: a huge skull of bone with a ribcage hanging under it
            Vec3 top = spot[0].add(0, 20, 0);
            BoneFx.bone(sl, BoneFx.LONG, top, new Vec3(0, -1, 0), new Vec3(1, 0, 0), 4.0f, 0.9f, lock + 6, 4, FxPacket.HOLD, 0, Vec3.ZERO, 0);
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3;
                BoneFx.bone(sl, BoneFx.RIB, top.add(0, -1.5, 0).add(Math.cos(a) * 1.4, 0, Math.sin(a) * 1.4), new Vec3(Math.cos(a), -0.3, Math.sin(a)), new Vec3(0, -1, 0), 2.6f, 0.2f, lock + 6, 6, FxPacket.HOLD, 0, Vec3.ZERO, 0);
            }
        });
        AstaFx.later(follow + lock - 6, () -> {   // it falls
            if (!ok(b) || spot[0] == null) return;
            Vec3 top = spot[0].add(0, 20, 0);
            BoneFx.bone(sl, BoneFx.LONG, top, new Vec3(0, -1, 0), new Vec3(1, 0, 0), 4.0f, 0.9f, 7, 0, FxPacket.HOLD, 0, new Vec3(0, -3.0, 0), 6);
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3;
                BoneFx.bone(sl, BoneFx.RIB, top.add(0, -1.5, 0).add(Math.cos(a) * 1.4, 0, Math.sin(a) * 1.4), new Vec3(Math.cos(a), -0.3, Math.sin(a)), new Vec3(0, -1, 0), 2.6f, 0.2f, 7, 0, FxPacket.HOLD, 0, new Vec3(0, -3.0, 0), 6);
            }
        });
        AstaFx.later(follow + lock, () -> {
            if (!ok(b) || spot[0] == null) return;
            Vec3 c = spot[0];
            FxSend.impactFrame(sl, c.add(0, 1, 0), 5, FxSend.FRAME_ZENON, 32);
            FxSend.shake(sl, c, 1.1f, 16, 34);
            FxSend.ring(sl, BONE, c.add(0, 0.1, 0), 0.6f, 9f, 0.35f, 16);
            FxSend.ring(sl, DARK, c.add(0, 0.12, 0), 0.5f, 7f, 0.2f, 14);
            FxSend.cracks(sl, BONE, c.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), 5f, 40, false, b.getId() * 3);
            BoneFx.bone(sl, BoneFx.LONG, c.add(0, 3.5, 0), new Vec3(0.15, 1, 0.1), new Vec3(1, 0, 0), 4.0f, 0.9f, 40, 0, FxPacket.POP, 0, Vec3.ZERO, 0);
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8;
                spikeUp(sl, c.add(Math.cos(a) * 3.2, 0, Math.sin(a) * 3.2), new Vec3(Math.cos(a) * 0.6, 1, Math.sin(a) * 0.6), 2.8f, 0.2f, 30);
            }
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 1.6f, 0.5f);
            sound(sl, c, SoundEvents.BONE_BLOCK_BREAK, 1.6f, 0.4f);
            for (Player p : near(b, c.add(0, 1, 0), 5.2)) { hurt(b, p, 20f); p.setDeltaMovement(p.getDeltaMovement().add(0, 1.0, 0)); p.hurtMarked = true; }
        });
        return b.ai.busy;
    }

    // ================================================================== the arena wakes
    private static void hazards(Zenon b, ServerLevel sl) {
        State s = b.ai;
        if (--s.hazard > 0) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vec3 h = s.home;
        switch (b.phase()) {
            case 1 -> {   // lines of bone spikes running out from the middle
                s.hazard = 170;
                for (int l = 0; l < 2; l++) {
                    double a = r.nextDouble(Math.PI * 2);
                    Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 a0 = ground(sl, h), a1 = a0.add(d.scale(ARENA));
                    warnLine(sl, a0.add(0, 0.1, 0), a1.add(0, 0.1, 0), 0.25f, 24);
                    for (int k = 1; k <= 14; k++) {
                        final int kk = k;
                        AstaFx.later(24 + k, () -> {
                            if (!ok(b)) return;
                            Vec3 g = ground(sl, a0.add(d.scale(kk * ARENA / 14)));
                            spikeUp(sl, g, new Vec3(0, 1, 0), 1.8f, 0.17f, 18);
                            for (Player p : near(b, g.add(0, 0.8, 0), 1.0)) { hurt(b, p, 6f); p.setDeltaMovement(p.getDeltaMovement().add(0, 0.6, 0)); p.hurtMarked = true; }
                        });
                    }
                }
            }
            case 2 -> {   // rifts open in the floor and drag you toward them
                s.hazard = 150;
                for (int k = 0; k < 2; k++) {
                    Vec3 g = ground(sl, h.add(r.nextDouble(-ARENA * 0.7, ARENA * 0.7), 0, r.nextDouble(-ARENA * 0.7, ARENA * 0.7)));
                    warnCircle(sl, g, 3f, 22);
                    AstaFx.later(22, () -> {
                        if (!ok(b)) return;
                        RiftFx.cut(sl, g.add(0, 0.2, 0), new Vec3(1, 0, 0.4).normalize(), new Vec3(0, 1, 0), 1.6f, 60);
                        sound(sl, g, SoundEvents.PORTAL_AMBIENT, 1f, 1.6f);
                    });
                    for (int tt = 0; tt < 60; tt += 2) {
                        final int t2 = tt;
                        AstaFx.later(22 + tt, () -> {
                            if (!ok(b)) return;
                            for (Player p : near(b, g.add(0, 0.8, 0), 3.2)) {
                                Vec3 in = g.subtract(p.position());
                                p.setDeltaMovement(p.getDeltaMovement().add(flat(in).normalize().scale(0.09)));
                                p.hurtMarked = true;
                                if (t2 % 10 == 0 && in.length() < 1.3) hurt(b, p, 3f);
                            }
                        });
                    }
                }
            }
            default -> {   // cracks of devil power split the ground outward from the middle
                s.hazard = 130;
                for (int l = 0; l < 3; l++) {
                    double a = r.nextDouble(Math.PI * 2);
                    Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 a0 = ground(sl, h), a1 = a0.add(d.scale(ARENA));
                    warnLine(sl, a0.add(0, 0.1, 0), a1.add(0, 0.1, 0), 0.3f, 20);
                    AstaFx.later(20, () -> {
                        if (!ok(b)) return;
                        FxSend.cracks(sl, Fx.CRIMSON, a0.add(d.scale(ARENA / 2)).add(0, 0.05, 0), d, new Vec3(0, 1, 0), (float) ARENA / 2, 30, false, (int) (a * 1000));
                        FxSend.trail(sl, DARK, a0.add(0, 0.1, 0), a1.add(0, 0.1, 0), 0.4f, 16);
                        for (Player p : along(b, a0.add(0, 0.5, 0), a1.add(0, 0.5, 0), 0.9)) hurt(b, p, 5f);
                        sound(sl, a0, SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 1.6f);
                    });
                }
            }
        }
    }

    // ================================================================== transformations
    public static void transform(Zenon b, int phase) {
        if (b.transforming() > 0 || b.phase() >= phase) return;
        ServerLevel sl = (ServerLevel) b.level();
        b.setPhase(phase);
        b.setTransforming(70);
        b.setPose(P_ROAR);
        b.ai.busy = 0;
        b.ai.hazard = 90;
        b.ai.finale = 140;
        b.getNavigation().stop();
        Vec3 c = b.position();
        for (Player p : near(b, c.add(0, 1, 0), 5)) {   // a breath of power pushes everyone back
            Vec3 away = flat(p.position().subtract(c));
            if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
            p.setDeltaMovement(away.normalize().scale(1.2).add(0, 0.4, 0));
            p.hurtMarked = true;
        }
        if (phase == 2) {
            say(b, "You can scratch bone. Fine - let's see you cut through space.");
            for (int k = 0; k < 12; k++) {
                final int kk = k;
                AstaFx.later(k * 4, () -> {   // bone grows over him, plate by plate, while space splits open round the arena
                    double a = kk * Math.PI / 6;
                    BoneFx.bone(sl, BoneFx.RIB, c.add(Math.cos(a) * 1.2, 1.0 + kk * 0.1, Math.sin(a) * 1.2), new Vec3(-Math.sin(a), 0.3, Math.cos(a)), new Vec3(0, 1, 0), 0.9f, 0.1f, 20, 4, FxPacket.POP, 0, Vec3.ZERO, 0);
                    Vec3 e = b.ai.home.add(Math.cos(a) * ARENA, 1.5, Math.sin(a) * ARENA);
                    RiftFx.cut(sl, e, new Vec3(-Math.sin(a), 0.4, Math.cos(a)).normalize(), new Vec3(Math.cos(a), 0, Math.sin(a)), 1.4f, 50);
                    sound(sl, c, SoundEvents.BONE_BLOCK_PLACE, 1.2f, 0.5f + kk * 0.05f);
                });
            }
            AstaFx.later(60, () -> {
                FxSend.impactFrame(sl, c.add(0, 1.5, 0), 4, FxSend.FRAME_ZENON, 28);
                FxSend.ring(sl, BONE, c.add(0, 0.1, 0), 0.5f, 10f, 0.3f, 16);
                FxSend.ring(sl, VOID, c.add(0, 0.12, 0), 0.5f, 7f, 0.15f, 14);
                FxSend.shake(sl, c, 0.8f, 12, 30);
                sound(sl, c, SoundEvents.WITHER_SKELETON_DEATH, 1.6f, 0.4f);
            });
        } else {
            say(b, "Enough. I'll show you what was waiting under the bone.");
            b.setHealth(Math.min(b.getMaxHealth(), b.getHealth() + b.getMaxHealth() * 0.5f));
            FxSend.wall(sl, DARK, b.ai.home.add(0, 0.2, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 18f, 18f, -180, 180, 18f, 80, 0, 0);   // the sky darkens
            for (int k = 0; k < 14; k++) {
                final int kk = k;
                AstaFx.later(k * 4, () -> {   // devil power floods into him from the edge of the arena
                    double a = kk * Math.PI / 7;
                    Vec3 e = b.ai.home.add(Math.cos(a) * ARENA, 0.2, Math.sin(a) * ARENA);
                    FxSend.trail(sl, DARK, e, c.add(0, 1.2, 0), 0.3f, 12);
                    FxSend.trail(sl, Fx.CRIMSON, e, c.add(0, 1.2, 0), 0.08f, 10);
                    sound(sl, c, SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.6f + kk * 0.03f);
                });
            }
            AstaFx.later(60, () -> {
                FxSend.impactFrame(sl, c.add(0, 1.5, 0), 5, FxSend.FRAME_ZENON, 34);
                FxSend.ring(sl, DARK, c.add(0, 0.1, 0), 0.5f, 12f, 0.4f, 18);
                FxSend.ring(sl, Fx.CRIMSON, c.add(0, 0.12, 0), 0.5f, 9f, 0.15f, 16);
                FxSend.cracks(sl, Fx.CRIMSON, c.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), 6f, 60, false, b.getId() * 7);
                FxSend.shake(sl, c, 1.0f, 16, 40);
                sound(sl, c, SoundEvents.WITHER_SPAWN, 1.4f, 0.4f);
                sound(sl, c, SoundEvents.ENDER_DRAGON_GROWL, 1.0f, 0.6f);
            });
        }
    }

    // ================================================================== death
    public static void dying(Zenon b, int t) {
        ServerLevel sl = (ServerLevel) b.level();
        Vec3 c = b.position();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (t == 1) {
            b.setPose(P_ROAR);
            say(b, "...So the bone remembers you. Hm.");
            sound(sl, c, SoundEvents.WITHER_SKELETON_DEATH, 1.6f, 0.4f);
            FxSend.impactFrame(sl, c.add(0, 1.5, 0), 4, FxSend.FRAME_ZENON, 26);
        }
        if (t < 56 && t % 3 == 0) {   // bones break off him and fall, the darkness lifting off him like smoke
            Vec3 at = c.add(r.nextDouble(-0.5, 0.5), 0.4 + r.nextDouble(1.6), r.nextDouble(-0.5, 0.5));
            BoneFx.bone(sl, r.nextBoolean() ? BoneFx.RIB : BoneFx.VERTEBRA, at, new Vec3(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1)), new Vec3(0, 1, 0), 0.3f, 0.07f, 14, 0, FxPacket.HOLD, 0, new Vec3(r.nextDouble(-0.05, 0.05), -0.12, r.nextDouble(-0.05, 0.05)), 10);
            Kit.orb(sl, DARK, c.add(r.nextDouble(-0.6, 0.6), 0.6 + r.nextDouble(1.4), r.nextDouble(-0.6, 0.6)), new Vec3(0, 0.06, 0), 0.25f, 18);
            if (t % 9 == 0) sound(sl, c, SoundEvents.BONE_BLOCK_BREAK, 1.0f, 0.6f + t * 0.01f);
        }
        if (t == 56) {   // the last of him falls in on itself - and what he leaves is lying in the dust
            ZenonDrops.drop(sl, c);
            FxSend.ring(sl, BONE, c.add(0, 0.1, 0), 0.3f, 5f, 0.2f, 16);
            FxSend.ring(sl, DARK, c.add(0, 0.12, 0), 0.3f, 3.5f, 0.12f, 14);
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                BoneFx.bone(sl, BoneFx.LONG, c.add(Math.cos(a) * 0.8, 0.15, Math.sin(a) * 0.8), new Vec3(-Math.sin(a), 0.05, Math.cos(a)), new Vec3(0, 1, 0), 0.5f, 0.07f, 80, 0, FxPacket.POP, 0, Vec3.ZERO, 0);
            }
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.6f);
            sound(sl, c, SoundEvents.SKELETON_DEATH, 1.4f, 0.4f);
        }
    }
}
