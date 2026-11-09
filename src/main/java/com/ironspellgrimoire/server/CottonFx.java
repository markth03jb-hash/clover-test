package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Charmy Pappitson's Cotton and Food Magic - the eight spells of the Cotton Grimoire. Soft, pink-white and cheerful on the
 * outside, and heavier than it looks: cotton that sticks and smothers, Nean the sheep who charges, cream puffs, a feast
 * that mends you, and one gigantic cake. Nothing in the world is eaten, only drawn.
 *
 *  1 Cotton Ball        - a puff of cotton that bursts into a sticky cloud
 *  2 Cotton Net         - a lattice of cotton flung ahead of you, gluing everything it covers
 *  3 Nean's Charge      - Nean, the cotton sheep, lowers her head and runs straight through your foes
 *  4 Cream Puff Barrage - a volley of pastries lobbed in a fan, each bursting in cream
 *  5 Sweet Feast        - you eat: you mend, your hunger fills and a sugar rush carries you for a while
 *  6 Cotton Cloud       - a great cloud of cotton settles where you aim and holds everything in it
 *  7 Nean Armor         - for 20 s you are wrapped in Nean's fleece: far tougher, steadier, slowly mending
 *  8 Grand Banquet      - a cake as big as a house falls on the spot you aim at, and Nean runs it down
 *
 * Passive "Sweet Tooth": each blow feeds you a little, and leaves the target stuck in cotton for a moment.
 */
public final class CottonFx {
    private CottonFx() {}
    private static final int C = Moves.COTTON, S = Fx.SILVER, G = Fx.GOLD, P = 16;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static Vec3 floorAt(ServerLevel sl, Vec3 at) {
        Vec3 g = FxLib.ground(sl, at.add(0, 3, 0), 10);
        return g == null ? at : g;
    }

    /** A puff of cotton: a cluster of soft balls round a point. */
    static void puff(ServerLevel sl, Vec3 c, float r, int life) {
        Kit.orb(sl, C, c, Vec3.ZERO, r, life);
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2 / 5 + c.x;
            Kit.orb(sl, C, c.add(Math.cos(a) * r * 0.7, Math.sin(a * 2) * r * 0.3, Math.sin(a) * r * 0.7), Vec3.ZERO, r * 0.65f, life);
        }
    }

    /** Nean, the cotton sheep, one frame of her run: a fleecy body, a face with curled horns, and four stubby legs. */
    static void nean(ServerLevel sl, Vec3 pos, Vec3 dir, float size, int step) {
        Vec3 d = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 rt = rightOf(d);
        Kit.orb(sl, C, pos, d, size, 2);
        Kit.orb(sl, C, pos.subtract(d.scale(size * 0.7)).add(0, size * 0.2, 0), d, size * 0.85f, 2);
        Kit.orb(sl, C, pos.add(d.scale(size * 0.9)).add(0, size * 0.1, 0), d, size * 0.45f, 2);                      // the face
        Kit.orb(sl, P, pos.add(d.scale(size * 1.2)).add(0, size * 0.05, 0), Vec3.ZERO, size * 0.12f, 2);              // her nose
        for (int s = -1; s <= 1; s += 2) {
            Vec3 h0 = pos.add(d.scale(size * 0.9)).add(rt.scale(s * size * 0.3)).add(0, size * 0.45, 0);
            FxSend.trail(sl, G, h0, h0.add(rt.scale(s * size * 0.45)).add(0, size * 0.1, 0), size * 0.12f, 3);        // horns curling out
            FxSend.trail(sl, G, h0.add(rt.scale(s * size * 0.45)).add(0, size * 0.1, 0), h0.add(rt.scale(s * size * 0.5)).add(d.scale(size * 0.3)).add(0, size * -0.2, 0), size * 0.1f, 3);
            Vec3 f0 = pos.add(rt.scale(s * size * 0.4)).add(0, -size * 0.7, 0);
            double sw = Math.sin(step * 1.6 + (s > 0 ? 0 : Math.PI)) * size * 0.4;
            FxSend.trail(sl, S, f0, f0.add(d.scale(sw)).add(0, -size * 0.5, 0), size * 0.14f, 3);                    // legs
        }
    }

    // ================================================================== 1. Cotton Ball
    public static void cottonBall(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        for (int k = 0; k < 6; k += 2) {
            final int kk = k;
            AstaFx.later(k + 1, () -> { if (alive(p)) puff(sl, Kit.hand(p), 0.1f + kk * 0.05f, 3); });
        }
        sound(sl, p.position(), SoundEvents.WOOL_PLACE, 0.9f, 1.4f);
        AstaFx.later(6, () -> {
            if (!alive(p)) return;
            Moves.bolt(p, C, Kit.hand(p), p.getLookAngle(), 1.5f, 0.6f, 36, 3.4, power, "cotton_ball", 0.5, at -> {
                for (int i = 0; i < 7; i++) {   // it bursts into a cloud of cotton tufts
                    double a = i * Math.PI * 2 / 7;
                    puff(sl, at.add(Math.cos(a) * 1.4, 0.3 + (i % 3) * 0.4, Math.sin(a) * 1.4), 0.45f, 22);
                }
                puff(sl, at.add(0, 0.6, 0), 0.9f, 26);
                sound(sl, at, SoundEvents.WOOL_BREAK, 1.2f, 0.9f);
            });
            sound(sl, p.position(), SoundEvents.SLIME_SQUISH, 0.6f, 1.5f);
        });
    }

    // ================================================================== 2. Cotton Net
    public static void cottonNet(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 dir = p.getLookAngle(), rt = rightOf(dir), up = rt.cross(dir).normalize();
        Vec3 from = p.getEyePosition().add(0, -0.3, 0);
        sound(sl, p.position(), SoundEvents.WOOL_PLACE, 1.0f, 0.7f);
        final int STR = 7;
        for (int k = 1; k <= 8; k++) {   // the net opens as it flies: rings widening, strands joining them
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                double d = kk * 1.1, w = 0.4 + kk * 0.5;
                Vec3 centre = from.add(dir.scale(d));
                Vec3 prevPt = null;
                for (int i = 0; i <= STR; i++) {
                    double a = i * Math.PI * 2 / STR;
                    Vec3 q = centre.add(rt.scale(Math.cos(a) * w)).add(up.scale(Math.sin(a) * w * 0.7));
                    if (prevPt != null) FxSend.trail(sl, C, prevPt, q, 0.07f, 14);
                    if (kk > 1) FxSend.trail(sl, C, q, q.subtract(dir.scale(1.1)).subtract(rt.scale(Math.cos(a) * 0.5)).subtract(up.scale(Math.sin(a) * 0.35)), 0.06f, 14);
                    prevPt = q;
                }
            });
        }
        AstaFx.later(6, () -> {
            if (!alive(p)) return;
            boolean any = false;
            for (LivingEntity t : Kit.near(sl, p, from.add(dir.scale(5.5)), 5.5)) {
                Vec3 to = mid(t).subtract(from).normalize();
                if (to.dot(dir) < 0.7) continue;
                Moves.hit(p, t, power * 0.8f, "cotton_net", C);
                Moves.slow(t, 80, -0.75);
                puff(sl, mid(t), 0.8f, 40);
                any = true;
            }
            if (any) sound(sl, from.add(dir.scale(5)), SoundEvents.WOOL_BREAK, 1.0f, 0.6f);
        });
    }

    // ================================================================== 3. Nean's Charge
    public static void neansCharge(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 dir = flat(p.getLookAngle());
        Vec3 start = p.position().add(0, 1.0, 0).add(dir.scale(1.0));
        Set<UUID> hit = new HashSet<>();
        sound(sl, p.position(), SoundEvents.SHEEP_AMBIENT, 1.2f, 1.3f);
        puff(sl, start, 0.9f, 8);
        for (int k = 1; k <= 10; k++) {
            final int kk = k;
            AstaFx.later(3 + k, () -> {
                if (!alive(p)) return;
                Vec3 pos = floorAt(sl, start.add(dir.scale(kk * 1.6))).add(0, 1.0 + Math.abs(Math.sin(kk * 1.5)) * 0.3, 0);
                nean(sl, pos, dir, 0.75f, kk);
                if (kk % 2 == 0) puff(sl, pos.subtract(dir.scale(1.0)).add(0, -0.4, 0), 0.35f, 8);   // dust of fluff behind her
                for (LivingEntity t : Kit.near(sl, p, pos, 2.0)) {
                    if (!hit.add(t.getUUID())) continue;
                    Moves.hit(p, t, power * 1.35f, "neans_charge", C);
                    Kit.push(t, dir, 1.4, 0.5);
                    Fx.impact(sl, C, mid(t), 1.3f);
                    sound(sl, mid(t), SoundEvents.GOAT_RAM_IMPACT, 1.0f, 0.9f);
                }
                if (kk == 10) { puff(sl, pos, 1.2f, 14); FxSend.ring(sl, C, pos.add(0, -0.9, 0), 0.3f, 3.0f, 0.1f, 8); }
            });
        }
    }

    // ================================================================== 4. Cream Puff Barrage
    public static void creamPuffBarrage(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        sound(sl, p.position(), SoundEvents.SLIME_SQUISH, 0.9f, 1.3f);
        for (int i = 0; i < 7; i++) {
            final int ii = i;
            AstaFx.later(5 + i * 2, () -> {
                if (!alive(p)) return;
                Vec3 look = p.getLookAngle();
                Vec3 dir = rotY(look, (ii - 3) * 0.1).add(0, 0.12, 0);
                int col = ii % 3 == 1 ? G : (ii % 3 == 2 ? P : C);   // cream, gold glaze, pink icing
                Moves.bolt(p, col, Kit.hand(p), dir, 1.5f, 0.38f, 30, 2.2, power * 0.5f, "cream_puff_barrage", 0.5, at -> {
                    puff(sl, at.add(0, 0.3, 0), 0.6f, 14);
                    sound(sl, at, SoundEvents.SLIME_SQUISH, 0.8f, 1.1f);
                });
                sound(sl, p.position(), SoundEvents.LLAMA_SPIT, 0.5f, 1.4f + ii * 0.05f);
            });
        }
    }

    // ================================================================== 5. Sweet Feast
    public static void sweetFeast(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        p.heal(7.0f);
        p.getFoodData().eat(8, 0.8f);
        Moves.stance(p, 0, 0, 0.2, 0, 0.2, 160);
        sound(sl, p.position(), SoundEvents.GENERIC_EAT, 1.0f, 1.0f);
        sound(sl, p.position(), SoundEvents.PLAYER_BURP, 0.9f, 1.1f);
        Random rnd = new Random(p.getId() * 13L);
        for (int i = 0; i < 8; i++) {   // treats appear round you and fly into your mouth
            final int ii = i;
            AstaFx.later(2 + i * 2, () -> {
                if (!alive(p)) return;
                double a = ii * Math.PI / 4 + rnd.nextDouble() * 0.3;
                Vec3 from = p.position().add(Math.cos(a) * 2.4, 0.3 + rnd.nextDouble() * 1.2, Math.sin(a) * 2.4);
                Vec3 to = p.getEyePosition().add(0, -0.2, 0);
                int col = ii % 3 == 1 ? G : (ii % 3 == 2 ? P : C);
                for (int s = 0; s < 3; s++) {
                    Vec3 q = from.add(to.subtract(from).scale(s / 3.0));
                    Kit.orb(sl, col, q, Vec3.ZERO, 0.28f, 3);
                    FxSend.trail(sl, col, q, from.add(to.subtract(from).scale((s + 1) / 3.0)), 0.1f, 4);
                }
            });
        }
        AstaFx.later(18, () -> {
            if (!alive(p)) return;
            Moves.nova(p, C, p.position().add(0, 0.6, 0), 4.0, power * 0.7f, "sweet_feast", 0.8, 0.4);   // the sugar rush throws everyone back
            FxLib.halo(sl, C, p.position().add(0, p.getBbHeight() + 0.4, 0), 1.0f);
            Fx.flash(sl, p.position().add(0, 1, 0), 1.8f);
            sound(sl, p.position(), SoundEvents.PLAYER_LEVELUP, 0.7f, 1.8f);
        });
    }

    // ================================================================== 6. Cotton Cloud
    public static void cottonCloud(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = Kit.aim(p, 22);
        final double R = 5.0;
        FxSend.ring(sl, C, c.add(0, 0.1, 0), 0.3f, (float) R, 0.08f, 12);
        sound(sl, c, SoundEvents.WOOL_PLACE, 1.4f, 0.5f);
        Random rnd = new Random(c.hashCode());
        for (int t = 0; t < 60; t++) {
            final int tt = t;
            AstaFx.later(t + 1, () -> {
                if (!alive(p)) return;
                if (tt % 2 == 0) for (int i = 0; i < 3; i++) {   // a great cloud billowing over the ground
                    double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * R * 0.9;
                    puff(sl, c.add(Math.cos(a) * d, 0.6 + rnd.nextDouble() * 2.0, Math.sin(a) * d), 0.7f + rnd.nextFloat() * 0.5f, 6);
                }
                if (tt % 6 == 0) for (LivingEntity e : Kit.near(sl, p, c.add(0, 1, 0), R)) {
                    Moves.slow(e, 40, -0.6);
                    Kit.push(e, new Vec3(0, 1, 0), 0, 0.05);
                    if (tt % 12 == 0) Moves.hit(p, e, power * 0.3f, "cotton_cloud", C);
                }
            });
        }
        AstaFx.later(62, () -> {
            if (!alive(p)) return;
            Moves.nova(p, C, c.add(0, 1, 0), R, power * 0.8f, "cotton_cloud", 0.8, 0.6);
            sound(sl, c, SoundEvents.WOOL_BREAK, 1.3f, 0.6f);
        });
    }

    // ================================================================== 7. Nean Armor
    public static void neanArmor(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Moves.stance(p, 8, 3, -0.02, 0.8, 0, 400);
        FxSend.ring(sl, C, p.position().add(0, 0.05, 0), 0.3f, 3.0f, 0.1f, 10);
        for (int k = 0; k < 10; k++) {   // fleece wraps round you from the feet up
            final int kk = k;
            AstaFx.later(k + 1, () -> {
                if (!alive(p)) return;
                double a = kk * 0.8;
                puff(sl, p.position().add(Math.cos(a) * 0.7, kk * 0.17, Math.sin(a) * 0.7), 0.32f, 10);
            });
        }
        AstaFx.later(11, () -> { if (alive(p)) { Fx.flash(sl, p.position().add(0, 1, 0), 1.6f); FxLib.halo(sl, C, p.position().add(0, p.getBbHeight() + 0.4, 0), 0.8f); } });
        sound(sl, p.position(), SoundEvents.SHEEP_AMBIENT, 1.0f, 0.8f);
        sound(sl, p.position(), SoundEvents.WOOL_PLACE, 1.0f, 0.7f);
        Moves.mode(p, "nean_armor", 400, 5, pl -> {
            ServerLevel s2 = pl.serverLevel();
            long now = s2.getGameTime();
            Vec3 c = pl.position();
            for (int i = 0; i < 4; i++) {   // fluffs of fleece bobbing round the body; a pair of curled horns on the head
                double a = now * 0.1 + i * Math.PI / 2;
                Kit.orb(s2, C, c.add(Math.cos(a) * 0.75, 0.6 + 0.5 * Math.sin(now * 0.12 + i), Math.sin(a) * 0.75), Vec3.ZERO, 0.3f, 2);
            }
            Vec3 h = c.add(0, pl.getBbHeight() + 0.05, 0);
            for (int s = -1; s <= 1; s += 2) FxSend.trail(s2, G, h.add(s * 0.2, 0, 0), h.add(s * 0.5, 0.15, 0.1), 0.07f, 6);
            if (now % 40 == 0) { pl.heal(1.0f); FxLib.halo(s2, C, h.add(0, 0.3, 0), 0.4f); }
        });
    }

    // ================================================================== 8. Grand Banquet
    /**
     * A cake as big as a house - three tiers, pink icing, a cherry on top - falls on the spot you aim at (up to 30 blocks);
     * everything within 7 blocks is crushed under it, and Nean the sheep then runs the survivors down (a second, wider blow).
     * Surpassing Limits: Nean Colossus.
     */
    public static void grandBanquet(ServerPlayer p, float power) {
        if (LimitBreak.active(p)) { AwakenFx.neanColossus(p, power); return; }
        ServerLevel sl = p.serverLevel();
        Vec3 c = floorAt(sl, Kit.aim(p, 30));
        sound(sl, c, SoundEvents.WARDEN_SONIC_CHARGE, 1.0f, 1.0f);
        sound(sl, p.position(), SoundEvents.SHEEP_AMBIENT, 1.4f, 0.6f);
        for (int g = 0; g < 8; g++) {   // a pink ring on the floor, tightening
            final int gg = g;
            AstaFx.later(g * 2, () -> FxSend.ring(sl, C, c.add(0, 0.08, 0), 7.6f - gg * 0.2f, 7.8f - gg * 0.2f, 0.04f, 4));
        }
        final int FALL = 10;
        for (int k = 1; k <= FALL; k++) {   // the cake falls, drawn tier by tier: rings of icing, a body between, the cherry on top
            final int kk = k;
            AstaFx.later(6 + k, () -> {
                double u = kk / (double) FALL, drop = 26 * (1 - u * u);
                float[] radii = {5.6f, 4.0f, 2.5f};
                for (int tier = 0; tier < 3; tier++) {
                    Vec3 tc = c.add(0, drop + tier * 2.2 + 0.6, 0);
                    FxSend.wall(sl, tier == 1 ? P : C, tc, new Vec3(1, 0, 0), new Vec3(0, 0, 1), radii[tier] * 0.88f, radii[tier], -180, 180, 0.5f, 3, 0, 0);
                    FxSend.wall(sl, G, tc.add(0, 0.9, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), radii[tier] * 0.95f, radii[tier], -180, 180, 0.12f, 3, 0, 0);
                    for (int s = 0; s < 8; s++) {   // the sides of the tier
                        double a = s * Math.PI / 4;
                        FxSend.trail(sl, tier == 1 ? P : C, tc.add(Math.cos(a) * radii[tier], 0, Math.sin(a) * radii[tier]), tc.add(Math.cos(a) * radii[tier], 1.8, Math.sin(a) * radii[tier]), 0.5f, 3);
                    }
                }
                Kit.orb(sl, P, c.add(0, drop + 7.4, 0), new Vec3(0, -1, 0), 0.8f, 2);   // the cherry
            });
        }
        AstaFx.later(6 + FALL + 1, () -> {
            if (!alive(p)) return;
            boolean any = false;
            for (LivingEntity t : Kit.near(sl, p, c.add(0, 1, 0), 7.0)) {
                float k = (float) (1.8 - 0.6 * Math.min(1, mid(t).distanceTo(c) / 7.0));
                Moves.hit(p, t, power * k, "grand_banquet", C);
                Kit.push(t, mid(t).subtract(c), 0.9, 0.6);
                any = true;
            }
            if (any) FxSend.impactFrame(sl, c, 4, FxSend.FRAME_CHARMY, 40);
            Fx.flash(sl, c.add(0, 2, 0), 3.2f);
            FxSend.ring(sl, C, c.add(0, 0.1, 0), 0.5f, 12f, 0.3f, 12);
            FxSend.ring(sl, P, c.add(0, 0.3, 0), 0.3f, 9f, 0.1f, 10);
            Random rnd = new Random(c.hashCode());
            for (int i = 0; i < 16; i++) {   // icing and cotton thrown everywhere
                double a = rnd.nextDouble() * Math.PI * 2, d = 2 + rnd.nextDouble() * 6;
                puff(sl, c.add(Math.cos(a) * d, 0.5 + rnd.nextDouble() * 4, Math.sin(a) * d), 0.6f + rnd.nextFloat() * 0.5f, 18);
            }
            FxSend.shake(sl, c, 1.1f, 14, 48);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 1.4f, 0.7f);
            sound(sl, c, SoundEvents.SLIME_SQUISH, 1.6f, 0.4f);
        });
        AstaFx.later(6 + FALL + 10, () -> {   // Nean runs across the wreckage
            if (!alive(p)) return;
            Vec3 dir = flat(c.subtract(p.position()));
            Vec3 from = c.subtract(dir.scale(10));
            Set<UUID> hit = new HashSet<>();
            for (int k = 1; k <= 12; k++) {
                final int kk = k;
                AstaFx.later(k, () -> {
                    Vec3 pos = floorAt(sl, from.add(dir.scale(kk * 1.7))).add(0, 1.9, 0);
                    nean(sl, pos, dir, 1.6f, kk);
                    for (LivingEntity t : Kit.near(sl, p, pos, 3.4)) {
                        if (!hit.add(t.getUUID())) continue;
                        Moves.hit(p, t, power * 1.0f, "grand_banquet", C);
                        Kit.push(t, dir, 1.4, 0.5);
                        Fx.impact(sl, C, mid(t), 1.4f);
                    }
                    if (kk % 3 == 0) FxSend.shake(sl, pos, 0.25f, 3, 24);
                });
            }
            sound(sl, c, SoundEvents.GOAT_RAM_IMPACT, 1.4f, 0.5f);
        });
    }
}
