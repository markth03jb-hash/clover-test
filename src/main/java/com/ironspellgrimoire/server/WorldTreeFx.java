package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.FxPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * William Vangeance's World Tree Magic - the eight spells of the World Tree Grimoire. Calm and vast: green growth with a
 * thread of gold sunlight in it - thorns, vines, roots and leaves, ending in a tree that fills the sky. All of it is
 * drawn, none of it is real: nothing grows in the world.
 *
 *  1 Thorn Bolt       - a seed of thorns that bursts into spikes
 *  2 Vine Lash        - a vine whips out, wraps what it hits and drags it to you
 *  3 Root Snare       - roots burst from the ground where you aim and hold everyone in them
 *  4 Leaf Storm       - a ring of razor leaves turns round you, cutting all that come near
 *  5 Verdant Bloom    - flowers open round you: you mend, and the petals cut what stands close
 *  6 Treant Guardian  - a guardian of living wood brings its fists down three times ahead of you
 *  7 World Tree Mantle- for 20 s bark and leaves cover you: tougher, steadier, slowly mending
 *  8 World Tree's Judgement - a colossal tree rises where you aim, its branches skewer and its crown falls
 *
 * Passive "Bloom": every blow of yours mends you a little.
 */
public final class WorldTreeFx {
    private WorldTreeFx() {}
    private static final int T = Moves.TREE, S = Fx.SILVER, G = Fx.GOLD;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static Vec3 floorAt(ServerLevel sl, Vec3 at) {
        Vec3 g = FxLib.ground(sl, at.add(0, 3, 0), 10);
        return g == null ? at : g;
    }

    // ================================================================== 1. Thorn Bolt
    public static void thornBolt(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        for (int k = 0; k < 6; k += 2) {
            final int kk = k;
            AstaFx.later(k + 1, () -> { if (alive(p)) Kit.orb(sl, T, Kit.hand(p), Vec3.ZERO, 0.1f + kk * 0.04f, 3); });
        }
        sound(sl, p.position(), SoundEvents.AZALEA_LEAVES_BREAK, 0.9f, 1.3f);
        AstaFx.later(6, () -> {
            if (!alive(p)) return;
            Moves.bolt(p, T, Kit.hand(p), p.getLookAngle(), 1.8f, 0.4f, 34, 2.8, power, "thorn_bolt", 0.4, at -> {
                for (int i = 0; i < 8; i++) {   // thorns bursting out
                    double a = i * Math.PI / 4;
                    Vec3 d = new Vec3(Math.cos(a), 0.6 + (i % 2) * 0.4, Math.sin(a));
                    FxSend.spike(sl, Fx.BARK, at, d, 1.5f, 0.14f, 7, 2, 0);
                }
                sound(sl, at, SoundEvents.GRASS_BREAK, 1.0f, 0.8f);
            });
            sound(sl, p.position(), SoundEvents.GRASS_BREAK, 0.8f, 1.5f);
        });
    }

    // ================================================================== 2. Vine Lash
    public static void vineLash(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 16, 20);
        Vec3 from = Kit.hand(p);
        Vec3 end = t != null ? mid(t) : Kit.aim(p, 12);
        Vec3 ax = end.subtract(from);
        Vec3 n = ax.lengthSqr() < 1.0E-4 ? p.getLookAngle() : ax.normalize();
        Vec3 rt = rightOf(n), up = rt.cross(n).normalize();
        sound(sl, p.position(), SoundEvents.GRASS_BREAK, 1.0f, 0.7f);
        for (int s = 1; s <= 6; s++) {   // the vine grows out in a curl
            final int ss = s;
            AstaFx.later(s, () -> {
                if (!alive(p)) return;
                for (int i = 0; i < 6; i++) {
                    double u0 = Math.min(1, (ss - 1 + i / 6.0) / 6.0), u1 = Math.min(1, (ss - 1 + (i + 1) / 6.0) / 6.0);
                    Vec3 q0 = from.add(ax.scale(u0)).add(rt.scale(Math.sin(u0 * 9) * 0.5 * (1 - u0))).add(up.scale(Math.cos(u0 * 9) * 0.4 * (1 - u0)));
                    Vec3 q1 = from.add(ax.scale(u1)).add(rt.scale(Math.sin(u1 * 9) * 0.5 * (1 - u1))).add(up.scale(Math.cos(u1 * 9) * 0.4 * (1 - u1)));
                    FxSend.trail(sl, T, q0, q1, 0.18f, 5);
                    if (i == 3) FxSend.trail(sl, G, q0, q0.add(rt.scale(0.3)).add(up.scale(0.25)), 0.06f, 4);   // a leaf at each node
                }
            });
        }
        AstaFx.later(7, () -> {
            if (!alive(p)) return;
            if (t != null && t.isAlive()) {
                Moves.hit(p, t, power * 1.1f, "vine_lash", T);
                Vec3 pull = p.position().subtract(t.position());
                Kit.push(t, pull, 1.0, 0.25);
                for (int w = 0; w < 3; w++) FxSend.trail(sl, T, t.position().add(0, 0.2 + w * 0.5, 0), t.position().add(Math.cos(w * 2) * 0.6, 0.6 + w * 0.5, Math.sin(w * 2) * 0.6), 0.15f, 8);
                Fx.impact(sl, T, mid(t), 1.2f);
            } else Moves.nova(p, T, end, 2.0, power * 0.7f, "vine_lash", 0.4, 0.2);
            sound(sl, end, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.8f);
        });
    }

    // ================================================================== 3. Root Snare
    /** One gnarled root as a chain of tapering blocks along a curve (a quadratic bezier), growing out of the ground block by block. */
    private static void root(ServerLevel sl, Vec3 p0, Vec3 p1, Vec3 p2, float thick, int life, int segs, int delay, boolean knots) {
        Vec3 prev = p0;
        for (int i = 1; i <= segs; i++) {
            double t = i / (double) segs, u = 1 - t;
            Vec3 pt = p0.scale(u * u).add(p1.scale(2 * u * t)).add(p2.scale(t * t));
            final Vec3 a = prev, b = pt;
            final float th = thick * (1f - 0.7f * (float) ((i - 1) / (double) segs));
            final int ii = i;
            AstaFx.later(delay + i, () -> {
                Vec3 d = b.subtract(a);
                double len = d.length();
                if (len < 0.05) return;
                Vec3 dn = d.normalize();
                Vec3 sd = dn.cross(UP);
                sd = sd.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : sd.normalize();
                Vec3 up = sd.cross(dn).normalize();
                FxSend.box(sl, Fx.BARK, a.add(b).scale(0.5), dn, up, (float) len * 0.5f + 0.06f, th, th * 0.9f, Math.max(10, life - ii), 2, FxPacket.EXTEND);
                if (knots && ii % 2 == 0) {   // a knot where it bends, and a rootlet curling off it
                    FxSend.box(sl, Fx.BARK, b, dn, up, th * 1.2f, th * 1.25f, th * 1.15f, Math.max(10, life - ii), 2, FxPacket.POP);
                    Vec3 off = sd.scale(ii % 4 == 0 ? 1 : -1).add(up.scale(-0.3)).normalize();
                    FxSend.box(sl, Fx.BARK, b.add(off.scale(0.3)), off, up, 0.3f, th * 0.4f, th * 0.35f, Math.max(8, life - ii - 2), 2, FxPacket.EXTEND);
                }
            });
            prev = pt;
        }
    }

    public static void rootSnare(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = floorAt(sl, Kit.aim(p, 22));
        final double R = 4.2;
        final java.util.Random rnd = new java.util.Random(p.getId() * 31L + sl.getGameTime());
        FxSend.ring(sl, T, c.add(0, 0.1, 0), 0.3f, (float) R, 0.08f, 10);
        FxSend.shake(sl, c, 0.3f, 8, 30);
        sound(sl, c, SoundEvents.ROOTED_DIRT_BREAK, 1.2f, 0.6f);
        sound(sl, c, SoundEvents.AZALEA_BREAK, 1.0f, 0.5f);
        // roots tear out of the ground round the ring, rise, and arch in over the middle like a cage of knuckled wood
        int n = 9;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + rnd.nextDouble() * 0.3;
            double d = R * (0.8 + 0.2 * rnd.nextDouble());
            Vec3 b = c.add(Math.cos(a) * d, -0.25, Math.sin(a) * d);
            Vec3 inward = new Vec3(-Math.cos(a), 0, -Math.sin(a));
            Vec3 mid = b.add(0, 2.6 + rnd.nextDouble() * 1.0, 0).add(inward.scale(R * 0.25)).add(-Math.sin(a) * 0.8 * (rnd.nextBoolean() ? 1 : -1), 0, Math.cos(a) * 0.5);
            Vec3 end = c.add(inward.scale(-0.0)).add(Math.cos(a + 0.9) * R * 0.25, 0.5 + rnd.nextDouble() * 0.6, Math.sin(a + 0.9) * R * 0.25);
            root(sl, b, mid, end, 0.3f + rnd.nextFloat() * 0.1f, 46, 7, 1 + i / 3, true);
        }
        for (int i = 0; i < 6; i++) {   // thin roots clawing along the ground between them
            double a = Math.PI * 2 * i / 6 + 0.5, d = R * 0.9;
            Vec3 b = c.add(Math.cos(a) * d, 0.0, Math.sin(a) * d);
            Vec3 e = c.add(Math.cos(a + 0.5) * R * 0.35, 0.1, Math.sin(a + 0.5) * R * 0.35);
            root(sl, b, b.add(e.subtract(b).scale(0.5)).add(0, 0.7, 0), e, 0.14f, 40, 5, 2 + i / 3, false);
        }
        AstaFx.later(8, () -> {
            if (!alive(p)) return;
            for (LivingEntity t : Kit.near(sl, p, c.add(0, 1, 0), R)) {
                Moves.hit(p, t, power, "root_snare", T);
                Moves.slow(t, 70, -0.8);
                // two roots coil up the body in a helix, thick at the ankle, thinner toward the chest
                Vec3 f0 = t.position();
                for (int w = 0; w < 2; w++) {
                    Vec3 prev = f0.add(Math.cos(w * Math.PI) * 0.5, 0.05, Math.sin(w * Math.PI) * 0.5);
                    for (int k = 1; k <= 7; k++) {
                        double ang = w * Math.PI + k * 0.95;
                        double h = t.getBbHeight() * 0.85 * k / 7.0;
                        Vec3 nxt = f0.add(Math.cos(ang) * (0.55 - 0.03 * k), h, Math.sin(ang) * (0.55 - 0.03 * k));
                        final Vec3 aa = prev, bb = nxt;
                        final float th = 0.11f - 0.01f * k;
                        AstaFx.later(k, () -> {
                            Vec3 d = bb.subtract(aa), dn = d.normalize();
                            Vec3 sd = dn.cross(UP);
                            sd = sd.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : sd.normalize();
                            FxSend.box(sl, Fx.BARK, aa.add(bb).scale(0.5), dn, sd.cross(dn).normalize(), (float) d.length() * 0.5f + 0.04f, th, th, 56, 2, FxPacket.EXTEND);
                        });
                        prev = nxt;
                    }
                }
                Fx.impact(sl, T, mid(t), 1.0f);
            }
            sound(sl, c, SoundEvents.WOOD_BREAK, 1.0f, 0.7f);
        });
    }

    // ================================================================== 4. Leaf Storm
    public static void leafStorm(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        sound(sl, p.position(), SoundEvents.AZALEA_LEAVES_BREAK, 1.2f, 0.8f);
        final double R = 3.6;
        for (int t = 0; t < 40; t++) {
            final int tt = t;
            AstaFx.later(t + 1, () -> {
                if (!alive(p)) return;
                Vec3 c = p.position().add(0, 1.0, 0);
                for (int s = 0; s < 4; s++) {   // leaves spinning round, rising and falling
                    double a = tt * 0.45 + s * Math.PI / 2, h = Math.sin(tt * 0.2 + s * 1.7) * 0.9;
                    Vec3 q = c.add(Math.cos(a) * R, h, Math.sin(a) * R), q0 = c.add(Math.cos(a - 0.5) * R, Math.sin((tt - 1) * 0.2 + s * 1.7) * 0.9, Math.sin(a - 0.5) * R);
                    FxSend.trail(sl, T, q0, q, 0.16f, 4);
                    if (tt % 2 == 0) FxSend.slash(sl, T, q, new Vec3(-Math.sin(a), 0, Math.cos(a)), UP, 0.5f, 0.15f, 3, 0.2f);
                }
                if (tt % 10 == 0) FxSend.ring(sl, G, c.add(0, -0.9, 0), (float) (R * 0.8), (float) R, 0.03f, 5);
                if (tt % 5 == 4) for (LivingEntity e : Kit.near(sl, p, c, R + 0.6)) {
                    Moves.hit(p, e, power * 0.28f, "leaf_storm", T);
                    Kit.push(e, e.position().subtract(c), 0.25, 0.1);
                    Fx.spark(sl, T, mid(e), 0.8f);
                }
            });
        }
    }

    // ================================================================== 5. Verdant Bloom
    public static void verdantBloom(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
        sound(sl, p.position(), SoundEvents.GRASS_BREAK, 0.8f, 1.4f);
        p.heal(8.0f);
        for (int f = 0; f < 6; f++) {   // flowers opening round you, one after another
            final int ff = f;
            AstaFx.later(2 + f * 2, () -> {
                if (!alive(p)) return;
                double a = ff * Math.PI / 3, d = 2.4;
                Vec3 b = floorAt(sl, p.position().add(Math.cos(a) * d, 0, Math.sin(a) * d));
                FxSend.trail(sl, T, b, b.add(0, 1.1, 0), 0.08f, 22);
                Vec3 top = b.add(0, 1.1, 0);
                for (int pt = 0; pt < 5; pt++) {   // five petals
                    double pa = pt * Math.PI * 2 / 5;
                    FxSend.trail(sl, G, top, top.add(Math.cos(pa) * 0.5, 0.15, Math.sin(pa) * 0.5), 0.14f, 16);
                }
                Kit.orb(sl, G, top, Vec3.ZERO, 0.22f, 14);
            });
        }
        AstaFx.later(16, () -> {
            if (!alive(p)) return;
            Moves.nova(p, T, p.position().add(0, 0.5, 0), 4.0, power * 0.9f, "verdant_bloom", 0.7, 0.3);
            FxLib.halo(sl, G, p.position().add(0, p.getBbHeight() + 0.4, 0), 1.0f);
            Fx.flash(sl, p.position().add(0, 1, 0), 1.6f);
            sound(sl, p.position(), SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
        });
    }

    // ================================================================== 6. Treant Guardian
    public static void treantGuardian(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 dir = flat(p.getLookAngle());
        sound(sl, p.position(), SoundEvents.WOOD_BREAK, 1.0f, 0.5f);
        for (int s = 0; s < 3; s++) {
            final int ss = s;
            AstaFx.later(6 + s * 8, () -> {
                if (!alive(p)) return;
                Vec3 at = floorAt(sl, p.position().add(dir.scale(3.5 + ss * 3.0)));
                FxSend.ring(sl, T, at.add(0, 0.05, 0), 0.2f, 3.0f, 0.04f, 6);
                FxSend.spike(sl, Fx.BARK, at.add(0.5, 9, 0.3), new Vec3(0, -1, 0), 9.6f, 1.0f, 8, 4, 2);   // the fist: a thick limb driven down
                FxSend.spike(sl, G, at.add(0.5, 9, 0.3), new Vec3(0, -1, 0), 9.6f, 0.2f, 8, 4, 2);
                AstaFx.later(4, () -> {
                    if (!alive(p)) return;
                    Moves.nova(p, T, at.add(0, 0.4, 0), 3.2, power * 0.95f, "treant_guardian", 0.9, 0.5);
                    FxLib.spikeRing(sl, T, at, 0.9f);
                    FxSend.shake(sl, at, 0.4f, 6, 28);
                    sound(sl, at, SoundEvents.WOOD_BREAK, 1.3f, 0.5f);
                    sound(sl, at, SoundEvents.IRON_GOLEM_ATTACK, 0.8f, 0.6f);
                });
            });
        }
    }

    // ================================================================== 7. World Tree Mantle
    public static void worldTreeMantle(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Moves.stance(p, 7, 3, 0.05, 0.3, 0, 400);
        FxSend.ring(sl, T, p.position().add(0, 0.05, 0), 0.3f, 3.2f, 0.1f, 10);
        for (int k = 0; k < 14; k++) {   // bark and vine climb the body
            final int kk = k;
            AstaFx.later(k + 1, () -> {
                if (!alive(p)) return;
                Vec3 b = p.position();
                for (int s = 0; s < 2; s++) {
                    double a = kk * 0.55 + s * Math.PI;
                    FxSend.trail(sl, T, b.add(Math.cos(a) * 0.7, kk * 0.13, Math.sin(a) * 0.7), b.add(Math.cos(a + 0.6) * 0.6, kk * 0.13 + 0.2, Math.sin(a + 0.6) * 0.6), 0.1f, 8);
                }
            });
        }
        AstaFx.later(15, () -> { if (alive(p)) { Fx.flash(sl, p.position().add(0, 1, 0), 1.6f); FxLib.halo(sl, G, p.position().add(0, p.getBbHeight() + 0.5, 0), 0.9f); } });
        sound(sl, p.position(), SoundEvents.WOOD_BREAK, 0.9f, 0.8f);
        sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 0.7f);
        Moves.aura(p, 4, T, 400);   // the leaves and the vine, drawn by the client every frame
        Moves.mode(p, "world_tree_mantle", 400, 5, pl -> {
            ServerLevel s2 = pl.serverLevel();
            long now = s2.getGameTime();
            Vec3 c = pl.position();
            if (now % 40 == 0) { pl.heal(1.0f); FxLib.halo(s2, G, c.add(0, pl.getBbHeight() + 0.3, 0), 0.4f); }
        });
    }

    // ================================================================== 8. World Tree's Judgement
    /**
     * A colossal tree rises from the ground where you aim (up to 30 blocks): the trunk climbs, the great boughs spread and
     * drive down through everything within 8 blocks, then the crown comes down in a wave of leaves.
     * Surpassing Limits: World Tree Genesis.
     */
    public static void worldTreeJudgement(ServerPlayer p, float power) {
        if (LimitBreak.active(p)) { AwakenFx.worldTreeGenesis(p, power); return; }
        ServerLevel sl = p.serverLevel();
        Vec3 c = floorAt(sl, Kit.aim(p, 30));
        sound(sl, c, SoundEvents.ROOTED_DIRT_BREAK, 1.6f, 0.4f);
        sound(sl, c, SoundEvents.WARDEN_SONIC_CHARGE, 0.9f, 0.6f);
        for (int g = 0; g < 8; g++) {
            final int gg = g;
            AstaFx.later(g, () -> FxSend.ring(sl, T, c.add(0, 0.05, 0), 0.4f + gg * 0.3f, 1.5f + gg * 0.7f, 0.06f, 5));
        }
        for (int r = 0; r < 8; r++) {   // roots first
            double a = r * Math.PI / 4;
            FxSend.spike(sl, Fx.BARK, c.add(Math.cos(a) * 1.6, -0.3, Math.sin(a) * 1.6), new Vec3(Math.cos(a), 0.15, Math.sin(a)), 5.5f, 0.55f, 50, 6, 1);
        }
        Sig.tree(sl, c, 18f, 56, p.getId() * 131L + sl.getGameTime(), 4);   // a whole tree climbs: trunk, roots, boughs and a crown of leaves
        for (int b = 0; b < 8; b++) {   // the boughs spread from the top, then drive down
            final int bb = b;
            AstaFx.later(20, () -> {
                double a = bb * Math.PI / 4 + 0.2;
                Vec3 top = c.add(0, 16.5, 0);
                Vec3 out = new Vec3(Math.cos(a), -0.35, Math.sin(a));
                FxSend.spike(sl, Fx.BARK, top, out, 11f, 0.6f, 26, 8, 2);
            });
        }
        AstaFx.later(26, () -> {
            if (!alive(p)) return;
            boolean any = false;
            for (LivingEntity t : Kit.near(sl, p, c.add(0, 1, 0), 8.0)) {   // the boughs come down through them
                float k = (float) (1.8 - 0.6 * Math.min(1, mid(t).distanceTo(c) / 8.0));
                Moves.hit(p, t, power * k, "world_tree_judgement", T);
                Kit.push(t, mid(t).subtract(c), 0.8, 0.6);
                FxSend.spike(sl, Fx.BARK, t.position().add(0, 6, 0), new Vec3(0, -1, 0), 7.5f, 0.32f, 10, 3, 2);
                any = true;
            }
            if (any) FxSend.impactFrame(sl, c, 4, FxSend.FRAME_WILLIAM, 40);
            Fx.flash(sl, c.add(0, 2, 0), 3.2f);
            FxSend.ring(sl, T, c.add(0, 0.1, 0), 0.5f, 12f, 0.3f, 12);
            FxSend.ring(sl, G, c.add(0, 0.3, 0), 0.3f, 9f, 0.08f, 10);
            for (int i = 0; i < 14; i++) {   // the crown comes down as leaves
                double a = i * Math.PI * 2 / 14;
                FxSend.trail(sl, T, c.add(Math.cos(a) * 2, 15, Math.sin(a) * 2), c.add(Math.cos(a) * 7, 1, Math.sin(a) * 7), 0.3f, 12);
            }
            FxSend.shake(sl, c, 1.1f, 14, 48);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 1.4f, 0.6f);
            sound(sl, c, SoundEvents.WOOD_BREAK, 1.6f, 0.4f);
        });
    }
}
