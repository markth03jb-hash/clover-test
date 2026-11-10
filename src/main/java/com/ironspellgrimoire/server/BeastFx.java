package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Vetto's Beast Magic - the eight spells of the Beast Grimoire. Spirits of the wild in feral violet and gold: a wolf that
 * runs its prey down, a hawk that dives, a bear's paw, a panther's pounce, a stampede, a coiling serpent, and in the end
 * all of them at once.
 *
 *  1 Wolf Fang        - a spirit wolf bounds ahead and bites the first thing it reaches
 *  2 Hawk Dive        - a hawk circles above your mark, then drops on it
 *  3 Bear Swipe       - a bear's paw rakes three huge claws across the front of you
 *  4 Panther Pounce   - you spring through the air onto your mark and rake it
 *  5 Stampede         - a herd of beasts runs ahead of you and tramples everything in its path
 *  6 Serpent Snare    - a serpent coils up the legs of everything near your mark and holds it
 *  7 Beast Instinct   - for 20 s the wolf's spirit runs through you: far faster, and you bite what comes near
 *  8 Primal King      - five great spirits close on the spot you aim at from every side
 *
 * Passive "Predator": the wounded (under half health) take 25% more from every blow.
 */
public final class BeastFx {
    private BeastFx() {}
    private static final int B = Moves.BEAST, G = Fx.GOLD, S = Fx.SILVER;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    /**
     * One frame of a running spirit beast: a glowing head, a body streaming back, ears and a tail. kind: 0 wolf, 1 hawk
     * (wings instead of legs), 2 bear (heavy), 3 panther (long and low), 4 stag (antlers).
     */
    static void spirit(ServerLevel sl, Vec3 pos, Vec3 dir, float size, int kind, int life) {
        Vec3 d = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 rt = rightOf(d), up = rt.cross(d).normalize();
        float len = size * (kind == 3 ? 3.2f : 2.4f);
        Kit.orb(sl, B, pos, d.scale(0.5), size * (kind == 2 ? 1.0f : 0.7f), 2);                       // the head
        FxSend.trail(sl, B, pos, pos.subtract(d.scale(len)), size * (kind == 2 ? 1.0f : 0.7f), life);   // the body
        FxSend.trail(sl, G, pos, pos.subtract(d.scale(len * 0.9)), size * 0.18f, Math.max(2, life - 1));
        if (kind == 1) {   // wings
            for (int s = -1; s <= 1; s += 2) {
                Vec3 tip = pos.subtract(d.scale(size * 1.0)).add(rt.scale(s * size * 2.6)).add(up.scale(size * 0.4));
                FxSend.trail(sl, B, pos.subtract(d.scale(size * 0.3)), tip, size * 0.35f, life);
                FxSend.trail(sl, G, tip, tip.subtract(d.scale(size * 1.3)).add(rt.scale(s * size * 0.6)), size * 0.12f, life);
            }
        } else {   // ears or antlers, and a tail
            for (int s = -1; s <= 1; s += 2) {
                Vec3 e0 = pos.add(up.scale(size * 0.5)).add(rt.scale(s * size * 0.3));
                FxSend.trail(sl, B, e0, e0.add(up.scale(size * (kind == 4 ? 1.4 : 0.6))).add(rt.scale(s * size * (kind == 4 ? 0.6 : 0.1))).subtract(d.scale(size * 0.2)), size * 0.14f, life);
            }
            Vec3 t0 = pos.subtract(d.scale(len)), t1 = t0.subtract(d.scale(size * 1.0)).add(up.scale(size * 0.7));
            FxSend.trail(sl, B, t0, t1, size * 0.22f, life);
        }
        Kit.orb(sl, G, pos.add(rt.scale(size * 0.25)).add(up.scale(size * 0.2)), Vec3.ZERO, size * 0.12f, 2);   // eyes
        Kit.orb(sl, G, pos.add(rt.scale(-size * 0.25)).add(up.scale(size * 0.2)), Vec3.ZERO, size * 0.12f, 2);
    }

    // ================================================================== 1. Wolf Fang
    public static void wolfFang(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 dir = flat(p.getLookAngle());
        Vec3 start = p.position().add(0, 0.9, 0).add(dir.scale(1.0));
        sound(sl, p.position(), SoundEvents.FOX_SCREECH, 1.0f, 0.6f);
        boolean[] done = {false};
        for (int k = 1; k <= 8; k++) {
            final int kk = k;
            AstaFx.later(3 + k, () -> {
                if (!alive(p) || done[0]) return;
                Vec3 pos = start.add(dir.scale(kk * 1.7)).add(0, Math.abs(Math.sin(kk * 1.3)) * 0.5, 0);
                spirit(sl, pos, dir, 0.55f, 0, 5);
                for (LivingEntity t : Kit.near(sl, p, pos, 1.7)) {
                    done[0] = true;
                    Moves.hit(p, t, power * 1.4f, "wolf_fang", B);
                    Kit.push(t, dir, 0.7, 0.3);
                    Vec3 m = mid(t);
                    FxSend.spike(sl, B, m.add(0, 1.0, 0.5), new Vec3(0, -1, -0.4), 1.4f, 0.16f, 8, 2, 0);   // the jaws closing
                    FxSend.spike(sl, B, m.add(0, -1.0, 0.5), new Vec3(0, 1, -0.4), 1.4f, 0.16f, 8, 2, 0);
                    Fx.impact(sl, B, m, 1.4f);
                    sound(sl, m, SoundEvents.PANDA_BITE, 1.0f, 0.8f);
                    sound(sl, m, SoundEvents.PLAYER_ATTACK_CRIT, 0.7f, 1.2f);
                    break;
                }
                if (kk == 8 && !done[0]) Fx.impact(sl, B, pos, 1.0f);
            });
        }
    }

    // ================================================================== 2. Hawk Dive
    public static void hawkDive(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 28, 20);
        Vec3 target = t != null ? t.position() : Kit.aim(p, 24);
        Vec3 high = target.add(0, 11, 0);
        sound(sl, p.position(), SoundEvents.ENDER_DRAGON_FLAP, 0.7f, 1.4f);
        Vec3[] prev = {high.add(5, 0, 0)};
        for (int k = 1; k <= 8; k++) {   // one slow circle above
            final int kk = k;
            AstaFx.later(k, () -> {
                double a = kk * 0.8;
                Vec3 pos = high.add(Math.cos(a) * 5.0, Math.sin(kk * 0.4) * 0.6, Math.sin(a) * 5.0);
                spirit(sl, pos, pos.subtract(prev[0]), 0.7f, 1, 4);
                prev[0] = pos;
            });
        }
        AstaFx.later(10, () -> {   // the dive
            if (!alive(p)) return;
            Vec3 end = t != null && t.isAlive() ? t.position().add(0, 1, 0) : target;
            for (int s = 1; s <= 4; s++) {
                final int ss = s;
                AstaFx.later(s, () -> {
                    Vec3 q0 = prev[0], q1 = high.add(end.subtract(high).scale(ss / 4.0));
                    spirit(sl, q1, q1.subtract(q0), 0.85f, 1, 5);
                    FxSend.trail(sl, S, q0, q1, 0.1f, 4);
                    prev[0] = q1;
                });
            }
            AstaFx.later(5, () -> {
                if (!alive(p)) return;
                Moves.nova(p, B, end, 2.6, power * 1.5f, "hawk_dive", 0.8, 0.5);
                FxLib.starburst(sl, B, end, UP, 1.4f);
                FxSend.impactFrame(sl, end, 2, FxSend.FRAME_VETTO, 24);
                sound(sl, end, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
                sound(sl, end, SoundEvents.TRIDENT_RIPTIDE_1, 1.0f, 1.6f);
            });
        });
    }

    // ================================================================== 3. Bear Swipe
    public static void bearSwipe(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        sound(sl, p.position(), SoundEvents.POLAR_BEAR_WARNING, 1.2f, 0.6f);
        FxSend.ring(sl, B, p.position().add(0, 0.05, 0), 0.3f, 2.2f, 0.06f, 6);
        Moves.claws(p, B, 3, 0.4, 4.2f, 4.0, power * 0.95f, "bear_swipe", 1.1, 3);
        AstaFx.later(8, () -> {   // the paw's print, left on the ground where the claws fell
            if (!alive(p)) return;
            Vec3 c = p.position().add(flat(p.getLookAngle()).scale(3.4));
            FxSend.ring(sl, B, c.add(0, 0.05, 0), 0.2f, 1.5f, 0.05f, 8);
            for (int i = -1; i <= 1; i++) {
                Vec3 toe = c.add(rightOf(flat(p.getLookAngle())).scale(i * 0.9)).add(flat(p.getLookAngle()).scale(1.2));
                FxSend.ring(sl, B, toe.add(0, 0.05, 0), 0.05f, 0.4f, 0.04f, 8);
            }
            FxSend.shake(sl, c, 0.3f, 5, 20);
        });
    }

    // ================================================================== 4. Panther Pounce
    public static void pantherPounce(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 16, 25);
        Vec3 dir = t != null ? flat(t.position().subtract(p.position())) : flat(p.getLookAngle());
        double dist = t != null ? Math.max(2.0, t.position().distanceTo(p.position()) - 1.3) : 9;
        sound(sl, p.position(), SoundEvents.FOX_AGGRO, 1.0f, 0.5f);
        FxSend.ring(sl, B, p.position().add(0, 0.05, 0), 0.2f, 2.0f, 0.06f, 6);
        AstaFx.later(3, () -> {
            if (!alive(p)) return;
            Moves.dash(p, B, dir, dist, 5, (at, i) -> {
                Vec3 pos = at.add(0, 1.0, 0);
                spirit(sl, pos, dir, 0.5f, 3, 6);
                if (i == 5) {   // the landing: a crossing rake
                    Vec3 c = pos.add(dir.scale(1.0)), rt = rightOf(dir);
                    FxSend.slash(sl, B, c, dir, rt.add(0, 1, 0).normalize(), 2.8f, 0.7f, 5, 0.3f);
                    FxSend.slash(sl, B, c, dir, rt.scale(-1).add(0, 1, 0).normalize(), 2.8f, 0.7f, 5, 0.3f);
                    boolean any = false;
                    for (LivingEntity e : Kit.near(sl, p, c, 3.0)) {
                        Moves.hit(p, e, power * 1.5f, "panther_pounce", B);
                        Kit.push(e, dir, 0.8, 0.35);
                        Fx.impact(sl, B, mid(e), 1.2f);
                        any = true;
                    }
                    if (any) FxSend.impactFrame(sl, c, 2, FxSend.FRAME_VETTO, 24);
                    sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.9f);
                }
            });
        });
    }

    // ================================================================== 5. Stampede
    public static void stampede(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 dir = flat(p.getLookAngle()), rt = rightOf(dir);
        sound(sl, p.position(), SoundEvents.HOGLIN_ANGRY, 1.4f, 0.6f);
        sound(sl, p.position(), SoundEvents.LLAMA_ANGRY, 1.2f, 0.6f);
        Moves.front(p, B, dir, 14, 1.7, 3.0f, power * 0.9f, "stampede", 1.3, f -> {
            for (int i = -2; i <= 2; i++) {   // five beasts abreast, each a different kind
                double bob = Math.abs(Math.sin(f.x * 1.1 + f.z * 0.9 + i)) * 0.5;
                Vec3 pos = f.add(rt.scale(i * 1.2)).add(0, 0.8 + bob, 0);
                spirit(sl, pos, dir, 0.45f, Math.floorMod(i + 2, 5) == 1 ? 2 : (Math.floorMod(i + 2, 5) == 3 ? 3 : (i == 0 ? 4 : 0)), 3);
            }
            FxSend.shake(sl, f, 0.12f, 2, 14);
        });
    }

    // ================================================================== 6. Serpent Snare
    public static void serpentSnare(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = Kit.aim(p, 20);
        FxSend.ring(sl, B, c.add(0, 0.1, 0), 0.3f, 5.0f, 0.06f, 12);
        sound(sl, c, SoundEvents.SPIDER_AMBIENT, 0.8f, 0.6f);
        AstaFx.later(4, () -> {
            if (!alive(p)) return;
            for (LivingEntity t : Kit.near(sl, p, c.add(0, 1, 0), 5.0)) {
                Moves.slow(t, 80, -0.9);
                Moves.hit(p, t, power * 0.5f, "serpent_snare", B);
                Vec3 base = t.position();
                Vec3[] prev = {base.add(1.0, 0, 0)};
                for (int s = 1; s <= 10; s++) {   // the serpent winds up the body
                    final int ss = s;
                    AstaFx.later(s, () -> {
                        double a = ss * 0.9, h = ss * (t.getBbHeight() / 10.0);
                        Vec3 q = t.position().add(Math.cos(a) * 0.65, h, Math.sin(a) * 0.65);
                        FxSend.trail(sl, B, prev[0], q, 0.2f, 60);
                        FxSend.trail(sl, G, prev[0], q, 0.05f, 58);
                        prev[0] = q;
                        if (ss == 10) {
                            Kit.orb(sl, B, q.add(0, 0.3, 0), Vec3.ZERO, 0.4f, 40);   // the head, raised over it
                            FxSend.spike(sl, G, q.add(0.2, 0.2, 0.1), new Vec3(0.3, 1, 0), 0.5f, 0.06f, 40, 1, 0);
                        }
                    });
                }
                for (int pulse = 1; pulse <= 3; pulse++) {
                    AstaFx.later(15 * pulse, () -> {
                        if (!t.isAlive() || !alive(p)) return;
                        Moves.hit(p, t, power * 0.35f, "serpent_snare", B);
                        Fx.spark(sl, B, mid(t), 0.8f);
                    });
                }
            }
            sound(sl, c, SoundEvents.HONEY_BLOCK_SLIDE, 1.0f, 0.6f);
        });
    }

    // ================================================================== 7. Beast Instinct
    public static void beastInstinct(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Moves.stance(p, 2, 0, 0.3, 0.1, 0.35, 400);
        FxSend.ring(sl, B, p.position().add(0, 0.05, 0), 0.3f, 3.2f, 0.1f, 10);
        spirit(sl, p.position().add(0, 1.0, 0).add(flat(p.getLookAngle()).scale(1.6)), flat(p.getLookAngle()).scale(-1), 0.9f, 0, 14);   // the wolf rears round you
        AstaFx.later(12, () -> { if (alive(p)) { Fx.flash(sl, p.position().add(0, 1, 0), 1.6f); FxLib.halo(sl, B, p.position().add(0, p.getBbHeight() + 0.4, 0), 0.9f); } });
        sound(sl, p.position(), SoundEvents.WARDEN_HEARTBEAT, 1.4f, 1.0f);
        Moves.mode(p, "beast_instinct", 400, 3, pl -> {
            ServerLevel s2 = pl.serverLevel();
            long now = s2.getGameTime();
            Vec3 c = pl.position();
            Vec3 face = flat(pl.getLookAngle());
            Vec3 rt = rightOf(face);
            for (int s = -1; s <= 1; s += 2) {   // ears and a tail of spirit, and eyes that glow
                Vec3 e0 = c.add(0, pl.getBbHeight() - 0.1, 0).add(rt.scale(s * 0.22));
                FxSend.trail(s2, B, e0, e0.add(rt.scale(s * 0.08)).add(0, 0.5, 0), 0.1f, 5);
                Kit.orb(s2, G, c.add(0, pl.getEyeHeight(), 0).add(face.scale(0.3)).add(rt.scale(s * 0.12)), Vec3.ZERO, 0.08f, 2);
            }
            Vec3 t0 = c.add(0, 0.9, 0).subtract(face.scale(0.3));
            FxSend.trail(s2, B, t0, t0.subtract(face.scale(0.9)).add(rt.scale(Math.sin(now * 0.3) * 0.4)).add(0, 0.5, 0), 0.14f, 5);
            if (now % 15 == 0) for (LivingEntity e : Kit.near(s2, pl, c, 3.2)) {
                Moves.hit(pl, e, power * 0.4f, "beast_instinct", B);
                Vec3 m = mid(e);
                FxSend.spike(s2, B, m.add(0, 0.8, 0), new Vec3(0, -1, 0), 1.0f, 0.12f, 6, 2, 0);
                FxSend.spike(s2, B, m.add(0, -0.8, 0), UP, 1.0f, 0.12f, 6, 2, 0);
                break;
            }
        });
    }

    // ================================================================== 8. Primal King
    /**
     * Five great spirits - wolf, hawk, bear, panther and stag - close on the spot you aim at (up to 30 blocks) from every
     * side, each in its own arc, and meet there in one great blow within 7 blocks.
     * Surpassing Limits: Primal Ancestor.
     */
    public static void primalKing(ServerPlayer p, float power) {
        if (LimitBreak.active(p)) { AwakenFx.primalAncestor(p, power); return; }
        ServerLevel sl = p.serverLevel();
        Vec3 c = Kit.aim(p, 30);
        sound(sl, p.position(), SoundEvents.WOLF_HOWL, 1.6f, 0.6f);
        sound(sl, p.position(), SoundEvents.RAVAGER_ROAR, 1.2f, 0.7f);
        sound(sl, c, SoundEvents.WARDEN_SONIC_CHARGE, 1.0f, 0.8f);
        FxSend.ring(sl, B, c.add(0, 0.1, 0), 0.4f, 7.0f, 0.1f, 18);
        final int T = 14;
        int[] kinds = {0, 1, 2, 3, 4};
        for (int b = 0; b < 5; b++) {
            final int bb = b;
            double a0 = b * Math.PI * 2 / 5 + 0.3;
            Vec3 from = c.add(Math.cos(a0) * 15, bb == 1 ? 12 : 1.0, Math.sin(a0) * 15);
            Vec3[] prev = {from};
            for (int k = 1; k <= T; k++) {
                final int kk = k;
                AstaFx.later(4 + k, () -> {
                    if (!alive(p)) return;
                    double u = kk / (double) T;
                    double a = a0 + u * 1.6 * (bb % 2 == 0 ? 1 : -1);
                    double rad = 15 * (1 - u);
                    double h = (bb == 1 ? 12 * (1 - u) : 0.8 + Math.abs(Math.sin(kk * 1.2)) * 0.6);
                    Vec3 pos = c.add(Math.cos(a) * rad, h + 0.6, Math.sin(a) * rad);
                    spirit(sl, pos, pos.subtract(prev[0]), 0.95f, kinds[bb], 6);
                    prev[0] = pos;
                });
            }
        }
        AstaFx.later(4 + T + 1, () -> {
            if (!alive(p)) return;
            boolean any = false;
            for (LivingEntity t : Kit.near(sl, p, c.add(0, 1, 0), 7.0)) {
                float k = (float) (1.8 - 0.6 * Math.min(1, mid(t).distanceTo(c) / 7.0));
                Moves.hit(p, t, power * k, "primal_king", B);
                Kit.push(t, mid(t).subtract(c), 1.2, 0.7);
                any = true;
            }
            if (any) FxSend.impactFrame(sl, c, 4, FxSend.FRAME_VETTO, 40);
            Fx.flash(sl, c.add(0, 1, 0), 3.2f);
            FxSend.ring(sl, B, c.add(0, 0.1, 0), 0.5f, 11f, 0.3f, 12);
            FxSend.ring(sl, G, c.add(0, 0.2, 0), 0.3f, 8f, 0.08f, 10);
            FxLib.starburst(sl, B, c.add(0, 1, 0), UP, 2.4f);
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6;
                FxSend.trail(sl, B, c.add(0, 0.5, 0), c.add(Math.cos(a) * 6, 2 + (i % 3), Math.sin(a) * 6), 0.3f, 9);
            }
            FxSend.shake(sl, c, 1.1f, 14, 48);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 1.5f, 0.7f);
            sound(sl, c, SoundEvents.WOLF_HOWL, 1.4f, 0.5f);
        });
    }
}
