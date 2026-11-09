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
 * Leopold Vermillion's Lion's Flame Magic - the eight spells of the Lion's Flame Grimoire. Wild, close and loud: bright
 * gold-orange flame thrown with the fists and the whole body, a mane of fire, claws, and a lion's roar.
 *
 *  1 Ember Fist       - a flaming fist punched ahead, a shock where it lands
 *  2 Lion Claw        - three claws of flame rake the air in front of you
 *  3 Flame Leap       - you spring high and come down in a crater of fire
 *  4 Roaring Burst    - a cone of flame bursts from your open hands, ring after ring
 *  5 Pride Barrage    - a flurry of fists, ten blows and a heavy last one
 *  6 Prowl Dash       - a fast low rush that ends in a crossing double claw
 *  7 Mane Blaze       - for 20 s a mane of flame burns round your head: faster, harder-hitting, scorching all near
 *  8 King Lion's Roar - a lion's head of flame forms ahead of you and roars a great beam of fire through everything
 *
 * Passive "Fury": every fifth blow lands 60% harder.
 */
public final class LionFlameFx {
    private LionFlameFx() {}
    private static final int L = Moves.LION, S = Fx.SILVER, G = Fx.GOLD;

    // ================================================================== 1. Ember Fist
    public static void emberFist(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        sound(sl, p.position(), SoundEvents.FIRECHARGE_USE, 0.7f, 1.5f);
        FxSend.ring(sl, L, Kit.hand(p), 0.05f, 0.7f, 0.03f, 4);
        AstaFx.later(3, () -> {
            if (!alive(p)) return;
            Vec3 dir = p.getLookAngle(), hand = Kit.hand(p);
            Vec3 tip = hand.add(dir.scale(2.4));
            for (int i = 0; i < 4; i++) {   // the fist travelling out
                Vec3 q = hand.add(dir.scale(0.6 * (i + 1)));
                Kit.orb(sl, L, q, dir.scale(0.6), 0.55f + i * 0.08f, 2);
                FxSend.trail(sl, L, hand.add(dir.scale(0.6 * i)), q, 0.3f, 5);
            }
            FxSend.trail(sl, S, hand, tip, 0.08f, 4);
            Moves.nova(p, L, tip, 2.4, power, "ember_fist", 0.9, 0.2);
            FxLib.starburst(sl, L, tip, dir, 1.1f);
            FxSend.shake(sl, tip, 0.2f, 3, 18);
            sound(sl, tip, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 1.0f);
            sound(sl, tip, SoundEvents.GENERIC_EXPLODE, 0.5f, 1.5f);
        });
    }

    // ================================================================== 2. Lion Claw
    public static void lionClaw(ServerPlayer p, float power) {
        sound(p.serverLevel(), p.position(), SoundEvents.RAVAGER_ATTACK, 0.7f, 1.3f);
        Moves.claws(p, L, 3, 0.55, 3.4f, 3.8, power * 0.75f, "lion_claw", 0.6, 2);
    }

    // ================================================================== 3. Flame Leap
    public static void flameLeap(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 dir = flat(p.getLookAngle());
        FxSend.ring(sl, L, p.position().add(0, 0.05, 0), 0.3f, 2.8f, 0.1f, 8);
        sound(sl, p.position(), SoundEvents.FIRECHARGE_USE, 1.0f, 0.8f);
        AstaFx.later(2, () -> {
            if (!alive(p)) return;
            p.setDeltaMovement(dir.x * 1.0, 1.0, dir.z * 1.0);
            p.hurtMarked = true;
            p.fallDistance = 0;
        });
        Vec3[] prev = {p.position()};
        for (int k = 3; k <= 14; k++) {   // a streak of fire behind the leap
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 now = p.position().add(0, 0.9, 0);
                FxSend.trail(sl, L, prev[0], now, 0.45f, 8);
                Kit.orb(sl, L, now, Vec3.ZERO, 0.5f, 2);
                prev[0] = now;
            });
        }
        AstaFx.later(14, () -> {
            if (!alive(p)) return;
            p.setDeltaMovement(dir.x * 0.4, -1.4, dir.z * 0.4);
            p.hurtMarked = true;
        });
        AstaFx.later(19, () -> {
            if (!alive(p)) return;
            p.fallDistance = 0;
            Vec3 c = p.position().add(0, 0.2, 0);
            Moves.nova(p, L, c, 4.8, power * 1.4f, "flame_leap", 1.0, 0.7);
            FxLib.spikeRing(sl, L, c, 1.4f);
            FxSend.cracks(sl, L, c.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 3.4f, 20, false, p.getId());
            FxSend.ring(sl, G, c, 0.3f, 6.5f, 0.12f, 10);
            FxSend.impactFrame(sl, c, 3, FxSend.FRAME_LEOPOLD, 30);
            FxSend.shake(sl, c, 0.7f, 10, 36);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 1.2f, 0.8f);
        });
    }

    // ================================================================== 4. Roaring Burst
    public static void roaringBurst(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        sound(sl, p.position(), SoundEvents.RAVAGER_ROAR, 0.9f, 1.2f);
        FxSend.ring(sl, L, p.position().add(0, 0.05, 0), 0.3f, 2.4f, 0.1f, 6);
        Set<UUID> hit = new HashSet<>();
        for (int k = 0; k < 6; k++) {
            final int kk = k;
            AstaFx.later(4 + kk * 2, () -> {
                if (!alive(p)) return;
                Vec3 dir = p.getLookAngle(), rt = rightOf(dir), up = rt.cross(dir).normalize();
                Vec3 c = p.getEyePosition().add(dir.scale(1.6 + kk * 1.4)).add(0, -0.3, 0);
                float r = 0.9f + kk * 0.6f;
                FxSend.wall(sl, L, c, rt, up, r * 0.78f, r, -180, 180, 0.22f, 5, 0, 0);
                FxSend.wall(sl, G, c, rt, up, r * 0.5f, r * 0.58f, -180, 180, 0.08f, 4, 0, 0);
                for (int s = 0; s < 6; s++) {   // flame licking forward along the cone's edge
                    double a = s * Math.PI / 3 + kk;
                    Vec3 e0 = c.add(rt.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
                    FxSend.trail(sl, L, e0, e0.add(dir.scale(1.2)), 0.12f, 4);
                }
                for (LivingEntity t : Kit.near(sl, p, c, r + 0.8)) {
                    if (!hit.add(t.getUUID())) continue;
                    Moves.hit(p, t, power * 1.1f, "roaring_burst", L);
                    Kit.push(t, dir, 1.2, 0.3);
                    Fx.impact(sl, L, mid(t), 1.1f);
                }
            });
        }
        AstaFx.later(16, () -> FxSend.shake(sl, p.position(), 0.35f, 6, 24));
    }

    // ================================================================== 5. Pride Barrage
    public static void prideBarrage(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        sound(sl, p.position(), SoundEvents.FIRECHARGE_USE, 0.8f, 1.2f);
        for (int i = 0; i < 10; i++) {
            final int ii = i;
            AstaFx.later(3 + i * 2, () -> {
                if (!alive(p)) return;
                Vec3 dir = p.getLookAngle(), rt = rightOf(dir), up = rt.cross(dir).normalize();
                double a = ii * 2.4;
                Vec3 at = p.getEyePosition().add(dir.scale(2.4)).add(rt.scale(Math.cos(a) * 0.7)).add(up.scale(Math.sin(a) * 0.5 - 0.3));
                Vec3 from = Kit.hand(p);
                FxSend.trail(sl, L, from, at, 0.32f, 4);
                Kit.orb(sl, L, at, dir.scale(0.4), 0.55f, 2);
                for (LivingEntity t : Kit.near(sl, p, at, 1.8)) {
                    Moves.hit(p, t, power * 0.4f, "pride_barrage", L);
                    Kit.push(t, dir, 0.18, 0.05);
                }
                Fx.spark(sl, L, at, 1.0f);
                sound(sl, at, SoundEvents.PLAYER_ATTACK_STRONG, 0.5f, 1.2f + (ii % 3) * 0.1f);
            });
        }
        AstaFx.later(25, () -> {
            if (!alive(p)) return;
            Vec3 dir = p.getLookAngle();
            Vec3 tip = p.getEyePosition().add(dir.scale(2.8));
            FxSend.trail(sl, L, Kit.hand(p), tip, 0.9f, 7);
            Moves.nova(p, L, tip, 3.0, power * 1.3f, "pride_barrage", 1.5, 0.35);
            FxSend.impactFrame(sl, tip, 2, FxSend.FRAME_LEOPOLD, 24);
            FxSend.shake(sl, tip, 0.5f, 6, 24);
            sound(sl, tip, SoundEvents.GENERIC_EXPLODE, 1.0f, 0.9f);
        });
    }

    // ================================================================== 6. Prowl Dash
    public static void prowlDash(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Set<UUID> hit = new HashSet<>();
        sound(sl, p.position(), SoundEvents.RAVAGER_STEP, 1.0f, 1.2f);
        Vec3 dir = flat(p.getLookAngle());
        Moves.dash(p, L, dir, 9, 4, (at, i) -> {
            for (LivingEntity t : Kit.near(sl, p, at.add(0, 1, 0), 2.0)) {
                if (!hit.add(t.getUUID())) continue;
                Moves.hit(p, t, power * 0.8f, "prowl_dash", L);
                Kit.push(t, dir, 0.5, 0.2);
                Fx.impact(sl, L, mid(t), 1.0f);
            }
            if (i == 4) {   // the double claw, crossing, at the end of the rush
                Vec3 c = at.add(0, 1.3, 0).add(dir.scale(1.2)), rt = rightOf(dir);
                FxSend.slash(sl, L, c, dir, rt.add(0, 1, 0).normalize(), 2.8f, 0.7f, 5, 0.3f);
                FxSend.slash(sl, L, c, dir, rt.scale(-1).add(0, 1, 0).normalize(), 2.8f, 0.7f, 5, 0.3f);
                FxSend.slash(sl, S, c, dir, new Vec3(0, 1, 0), 2.2f, 0.3f, 4, 0.3f, 0.5f);
                for (LivingEntity t : Kit.near(sl, p, c, 3.0)) {
                    Moves.hit(p, t, power * 0.9f, "prowl_dash", L);
                    Kit.push(t, dir, 0.7, 0.3);
                }
                sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.9f);
            }
        });
    }

    // ================================================================== 7. Mane Blaze
    public static void maneBlaze(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Moves.stance(p, 3, 1, 0.15, 0.1, 0.25, 400);
        FxSend.ring(sl, L, p.position().add(0, 0.05, 0), 0.3f, 3.6f, 0.12f, 10);
        for (int k = 0; k < 10; k++) {   // the mane flares up round the head
            final int kk = k;
            AstaFx.later(k + 1, () -> {
                if (!alive(p)) return;
                Vec3 h = p.position().add(0, p.getBbHeight() - 0.1, 0);
                double a = kk * 0.65;
                FxSend.trail(sl, L, h.add(Math.cos(a) * 0.3, 0, Math.sin(a) * 0.3), h.add(Math.cos(a) * 1.0, 0.7 + kk * 0.06, Math.sin(a) * 1.0), 0.18f, 6);
            });
        }
        AstaFx.later(11, () -> { if (alive(p)) { Fx.flash(sl, p.position().add(0, 1.6, 0), 1.8f); FxLib.halo(sl, L, p.position().add(0, p.getBbHeight() + 0.5, 0), 1.0f); } });
        sound(sl, p.position(), SoundEvents.RAVAGER_ROAR, 1.0f, 1.3f);
        Moves.mode(p, "mane_blaze", 400, 3, pl -> {
            ServerLevel s2 = pl.serverLevel();
            long now = s2.getGameTime();
            Vec3 h = pl.position().add(0, pl.getBbHeight() - 0.15, 0);
            Vec3 back = flat(pl.getLookAngle()).scale(-1);
            for (int i = 0; i < 5; i++) {   // flame mane streaming back from the head and shoulders
                double a = now * 0.2 + i * 1.26, sway = Math.sin(now * 0.3 + i);
                Vec3 root = h.add(Math.cos(a) * 0.35, -0.1, Math.sin(a) * 0.35);
                FxSend.trail(s2, L, root, root.add(back.scale(0.7 + 0.25 * sway)).add(Math.cos(a) * 0.5, 0.4 + 0.2 * sway, Math.sin(a) * 0.5), 0.1f, 5);
            }
            if (now % 21 == 0) for (LivingEntity e : Kit.near(s2, pl, pl.position(), 2.6)) {
                Moves.hit(pl, e, power * 0.3f, "mane_blaze", L);
                Fx.impact(s2, L, mid(e), 0.8f);
            }
        });
    }

    // ================================================================== 8. King Lion's Roar
    /**
     * A lion's head of flame - face, mane and burning eyes - forms in the air ahead of you, faces you for a breath, turns
     * and roars: ring after ring and a great beam of fire through everything on the line (28 blocks, 3 wide).
     * Surpassing Limits: Lion King's Domain.
     */
    public static void kingLionRoar(ServerPlayer p, float power) {
        if (LimitBreak.active(p)) { AwakenFx.lionKingDomain(p, power); return; }
        ServerLevel sl = p.serverLevel();
        Vec3 dir = p.getLookAngle();
        Vec3 rt = rightOf(dir), up = new Vec3(0, 1, 0);
        Vec3 c = p.getEyePosition().add(dir.scale(5.5)).add(0, 0.5, 0);
        sound(sl, c, SoundEvents.RAVAGER_ROAR, 1.6f, 0.5f);
        sound(sl, c, SoundEvents.WARDEN_SONIC_CHARGE, 0.9f, 0.7f);
        for (int t = 0; t < 22; t++) {
            final int tt = t;
            AstaFx.later(t + 1, () -> {
                if (!alive(p)) return;
                float grow = Math.min(1f, tt / 10f);
                if (tt % 2 == 0) {
                    FxSend.wall(sl, L, c, rt, up, 1.5f * grow, 2.0f * grow, -180, 180, 0.3f, 3, 0, 0);   // the face
                    for (int i = 0; i < 16; i++) {   // the mane: rays flaring off the face, each curling a little
                        double a = i * Math.PI * 2 / 16 + tt * 0.02;
                        double c1 = Math.cos(a), s1 = Math.sin(a), c2 = Math.cos(a + 0.18), s2 = Math.sin(a + 0.18);
                        Vec3 r0 = c.add(rt.scale(c1 * 2.0 * grow)).add(up.scale(s1 * 2.0 * grow));
                        Vec3 r1 = c.add(rt.scale(c2 * (3.1 + 0.4 * Math.sin(tt * 0.5 + i)) * grow)).add(up.scale(s2 * (3.1 + 0.4 * Math.sin(tt * 0.5 + i)) * grow));
                        FxSend.trail(sl, L, r0, r1, 0.2f, 3);
                    }
                    Kit.orb(sl, G, c.add(rt.scale(0.65 * grow)).add(0, 0.4 * grow, 0), Vec3.ZERO, 0.28f, 2);   // the eyes
                    Kit.orb(sl, G, c.add(rt.scale(-0.65 * grow)).add(0, 0.4 * grow, 0), Vec3.ZERO, 0.28f, 2);
                    FxSend.trail(sl, L, c.add(0, 0.1 * grow, 0), c.add(0, -0.6 * grow, 0), 0.1f, 3);        // nose and mouth
                    FxSend.trail(sl, L, c.add(rt.scale(-0.5 * grow)).add(0, -0.6 * grow, 0), c.add(rt.scale(0.5 * grow)).add(0, -0.6 * grow, 0), 0.1f, 3);
                }
            });
        }
        AstaFx.later(24, () -> {   // the roar
            if (!alive(p)) return;
            Vec3 end = clip(sl, p, c, c.add(dir.scale(28)));
            FxSend.beam(sl, L, c, end, 1.7f, 12);
            FxSend.beam(sl, G, c, end, 0.7f, 10);
            FxSend.beam(sl, S, c, end, 0.2f, 8);
            for (int k = 0; k < 8; k++) {
                final int kk = k;
                AstaFx.later(k, () -> {
                    Vec3 q = c.add(dir.scale(2.5 + kk * 3.2));
                    FxSend.wall(sl, L, q, rt, up, 0.7f + kk * 0.2f, 1.2f + kk * 0.3f, -180, 180, 0.25f, 4, 0, 0);
                });
            }
            boolean any = false;
            for (LivingEntity t : Kit.along(sl, p, c, end, 2.0)) {
                Moves.hit(p, t, power * 2.0f, "king_lion_roar", L);
                Kit.push(t, dir, 1.6, 0.5);
                Fx.impact(sl, L, mid(t), 1.6f);
                any = true;
            }
            if (any) FxSend.impactFrame(sl, end, 4, FxSend.FRAME_LEOPOLD, 40);
            Fx.flash(sl, end, 3.0f);
            Moves.nova(p, L, end, 5.0, power * 0.8f, "king_lion_roar", 1.0, 0.6);
            FxSend.shake(sl, c, 1.0f, 14, 48);
            sound(sl, c, SoundEvents.RAVAGER_ROAR, 2.0f, 0.4f);
            sound(sl, end, SoundEvents.GENERIC_EXPLODE, 1.4f, 0.7f);
        });
    }
}
