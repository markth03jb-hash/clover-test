package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.FxPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Sol Marron's Earth Magic - the eight spells of the Earth Grimoire. Heavy, patient and honest: slabs, spikes and boulders
 * in ochre and grey that rise, strike and crumble. Every stone is conjured and drawn - no real block is ever moved or
 * broken, and the ground is left exactly as it was.
 *
 *  1 Stone Fist        - a fist of rock rises ahead of you and drives forward
 *  2 Rock Spike        - a line of spikes tears up the ground in front of you, one after another
 *  3 Earth Wall        - a wall of rock stands up ahead for 4 s and shoves everything that touches it away
 *  4 Boulder Toss      - a great boulder, thrown in an arc, shatters where it lands
 *  5 Quake Stomp       - you stamp: three shock rings roll out, the ground cracks and everything near is flung up
 *  6 Stone Golem       - a golem of rock stands up behind you and punches your foes five times
 *  7 Bedrock Armor     - for 20 s plates of rock circle you: far tougher, impossible to knock back
 *  8 Mountain Fall     - a mountain's worth of rock falls from the sky on the spot you aim at
 *
 * Passive "Weighed": whatever your stone hits moves a fifth slower for two seconds.
 */
public final class EarthFx {
    private EarthFx() {}
    private static final int E = Moves.EARTH, G = Fx.GOLD, S = Fx.SILVER;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static Vec3 floorAt(ServerLevel sl, Vec3 at) {
        Vec3 g = FxLib.ground(sl, at.add(0, 3, 0), 10);
        return g == null ? at : g;
    }

    // ================================================================== 1. Stone Fist
    public static void stoneFist(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 dir = flat(p.getLookAngle());
        Vec3 base = floorAt(sl, p.position().add(dir.scale(2.4)));
        sound(sl, p.position(), SoundEvents.STONE_BREAK, 1.0f, 0.6f);
        FxSend.ring(sl, E, base.add(0, 0.05, 0), 0.2f, 1.8f, 0.05f, 6);
        FxSend.box(sl, E, base.add(0, 0.8, 0), dir, UP, 0.7f, 0.7f, 0.7f, 18, 4, FxPacket.GROUND);   // the fist rises...
        AstaFx.later(5, () -> {
            if (!alive(p)) return;
            Vec3 fist = base.add(0, 1.0, 0);
            FxSend.box(sl, E, fist, dir, UP, 0.9f, 0.9f, 0.9f, 10, 1, FxPacket.POP, 0, dir.scale(0.9), 6);   // ...and drives forward
            FxSend.box(sl, G, fist, dir, UP, 0.5f, 0.5f, 0.5f, 8, 1, FxPacket.POP, 0, dir.scale(0.9), 6);
            Set<UUID> hit = new HashSet<>();
            for (int s = 0; s < 6; s++) {
                final int ss = s;
                AstaFx.later(s + 1, () -> {
                    if (!alive(p)) return;
                    Vec3 q = fist.add(dir.scale(0.9 * (ss + 1)));
                    for (LivingEntity t : Kit.near(sl, p, q, 1.8)) {
                        if (!hit.add(t.getUUID())) continue;
                        Moves.hit(p, t, power * 1.3f, "stone_fist", E);
                        Kit.push(t, dir, 1.3, 0.35);
                        Fx.impact(sl, E, mid(t), 1.3f);
                    }
                    if (ss == 5) {
                        Fx.impact(sl, E, q, 1.6f);
                        FxLib.spikeBurst(sl, E, q, 0.9f);
                        FxSend.shake(sl, q, 0.35f, 5, 22);
                        sound(sl, q, SoundEvents.GENERIC_EXPLODE, 0.6f, 1.2f);
                        sound(sl, q, SoundEvents.STONE_BREAK, 1.2f, 0.5f);
                    }
                });
            }
        });
    }

    // ================================================================== 2. Rock Spike
    public static void rockSpike(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 dir = flat(p.getLookAngle());
        Vec3 start = p.position().add(dir.scale(1.5));
        sound(sl, p.position(), SoundEvents.STONE_BREAK, 1.0f, 0.7f);
        for (int i = 0; i < 9; i++) {
            final int ii = i;
            AstaFx.later(2 + i, () -> {
                if (!alive(p)) return;
                Vec3 at = floorAt(sl, start.add(dir.scale(ii * 1.8)));
                FxSend.spike(sl, E, at.add(0, -0.2, 0), new Vec3(dir.x * 0.15, 1, dir.z * 0.15), 2.8f + (ii % 3) * 0.6f, 0.4f, 24, 3, FxPacket.GROUND);
                FxSend.spike(sl, E, at.add(0.7, -0.2, -0.4), new Vec3(0.2, 1, 0), 1.7f, 0.25f, 22, 3, FxPacket.GROUND);
                for (LivingEntity t : Kit.near(sl, p, at.add(0, 1, 0), 1.7)) {
                    Moves.hit(p, t, power * 0.85f, "rock_spike", E);
                    Kit.push(t, new Vec3(0, 1, 0), 0, 0.7);
                    Fx.impact(sl, E, mid(t), 1.0f);
                }
                if (ii % 2 == 0) sound(sl, at, SoundEvents.STONE_BREAK, 0.7f, 0.6f + ii * 0.04f);
            });
        }
    }

    // ================================================================== 3. Earth Wall
    public static void earthWall(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 dir = flat(p.getLookAngle()), rt = rightOf(dir);
        Vec3 centre = p.position().add(dir.scale(4.0));
        sound(sl, centre, SoundEvents.STONE_BREAK, 1.3f, 0.5f);
        for (int s = -3; s <= 3; s++) {   // seven slabs side by side
            final int ss = s;
            AstaFx.later(1 + Math.abs(s), () -> {
                Vec3 at = floorAt(sl, centre.add(rt.scale(ss * 1.2)));
                FxSend.box(sl, E, at.add(0, 1.5, 0), rt, UP, 0.62f, 0.4f, 1.6f, 80, 5, FxPacket.GROUND);
                FxSend.box(sl, G, at.add(0, 3.1, 0), rt, UP, 0.5f, 0.28f, 0.14f, 78, 5, FxPacket.GROUND);   // a lighter cap on each slab
            });
        }
        FxSend.ring(sl, E, centre.add(0, 0.05, 0), 0.3f, 4.5f, 0.08f, 10);
        for (LivingEntity t : Kit.near(sl, p, centre.add(0, 1, 0), 4.6)) {
            Moves.hit(p, t, power * 0.6f, "earth_wall", E);
            Kit.push(t, dir, 0.9, 0.5);
        }
        for (int k = 1; k <= 20; k++) {   // for four seconds, anything that touches the wall is shoved off it
            AstaFx.later(k * 4, () -> {
                if (!alive(p)) return;
                for (LivingEntity t : Kit.near(sl, p, centre.add(0, 1, 0), 4.4)) {
                    Vec3 rel = t.position().subtract(centre);
                    if (Math.abs(rel.dot(dir)) < 1.0 && Math.abs(rel.dot(rt)) < 4.0) {
                        Kit.push(t, dir.scale(Math.signum(rel.dot(dir) == 0 ? 1 : rel.dot(dir))), 0.55, 0.1);
                        Fx.spark(sl, E, mid(t), 0.8f);
                    }
                }
            });
        }
    }

    // ================================================================== 4. Boulder Toss
    public static void boulderToss(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 dir = p.getLookAngle();
        Vec3 up = floorAt(sl, p.position().add(flat(dir).scale(1.5)));
        sound(sl, p.position(), SoundEvents.STONE_BREAK, 1.0f, 0.6f);
        FxSend.box(sl, E, up.add(0, 1.0, 0), flat(dir), UP, 0.8f, 0.8f, 0.8f, 8, 4, FxPacket.GROUND);
        AstaFx.later(6, () -> {
            if (!alive(p)) return;
            Moves.bolt(p, E, p.getEyePosition().add(dir.scale(1.2)).add(0, -0.2, 0), dir, 1.4f, 0.9f, 44, 3.6, power * 1.5f, "boulder_toss", 1.0, at -> {
                for (int i = 0; i < 9; i++) {   // the boulder breaks into slabs
                    double a = i * Math.PI * 2 / 9;
                    FxSend.spike(sl, E, at.add(0, -0.2, 0), new Vec3(Math.cos(a), 0.9, Math.sin(a)), 1.7f, 0.28f, 12, 2, FxPacket.POP);
                }
                FxSend.shake(sl, at, 0.45f, 6, 26);
                sound(sl, at, SoundEvents.STONE_BREAK, 1.4f, 0.5f);
                sound(sl, at, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.9f);
            });
            sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 0.7f);
        });
    }

    // ================================================================== 5. Quake Stomp
    public static void quakeStomp(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        sound(sl, c, SoundEvents.IRON_GOLEM_ATTACK, 1.0f, 0.5f);
        FxSend.ring(sl, E, c.add(0, 0.05, 0), 0.2f, 1.8f, 0.08f, 6);
        AstaFx.later(6, () -> {
            if (!alive(p)) return;
            Vec3 at = p.position();
            for (int w = 0; w < 3; w++) {
                final int ww = w;
                AstaFx.later(w * 3, () -> {
                    FxSend.ring(sl, E, at.add(0, 0.08, 0), 0.4f + ww, 3.5f + ww * 2.4f, 0.12f, 9);
                    FxSend.ring(sl, G, at.add(0, 0.2, 0), 0.2f + ww, 2.8f + ww * 2.0f, 0.04f, 7);
                    for (LivingEntity t : Kit.near(sl, p, at, 3.4 + ww * 2.4)) {
                        if (mid(t).distanceTo(at) < 1.0 + ww * 2.4) continue;   // each ring only takes the band it reaches
                        Moves.hit(p, t, power * 0.5f, "quake_stomp", E);
                        Kit.push(t, mid(t).subtract(at), 0.5, 0.6);
                    }
                });
            }
            for (int r = 0; r < 6; r++) {   // cracks running out along the ground
                double a = r * Math.PI / 3 + 0.3;
                Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
                FxSend.cracks(sl, E, at.add(d.scale(3.0)).add(0, 0.05, 0), d, rightOf(d), 2.8f, 22, false, r * 17 + p.getId());
            }
            FxLib.spikeRing(sl, E, at, 1.2f);
            Fx.impact(sl, E, at.add(0, 0.3, 0), 2.0f);
            FxSend.impactFrame(sl, at, 3, FxSend.FRAME_SOL, 30);
            FxSend.shake(sl, at, 0.9f, 12, 40);
            sound(sl, at, SoundEvents.GENERIC_EXPLODE, 1.2f, 0.6f);
            sound(sl, at, SoundEvents.STONE_BREAK, 1.4f, 0.4f);
        });
    }

    // ================================================================== 6. Stone Golem
    public static void stoneGolem(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 dir = flat(p.getLookAngle()), rt = rightOf(dir);
        Vec3 back = floorAt(sl, p.position().subtract(dir.scale(2.6)));
        sound(sl, back, SoundEvents.STONE_BREAK, 1.3f, 0.4f);
        FxSend.ring(sl, E, back.add(0, 0.05, 0), 0.3f, 3.0f, 0.1f, 10);
        FxSend.box(sl, E, back.add(rt.scale(0.7)).add(0, 0.8, 0), dir, UP, 0.5f, 0.45f, 0.8f, 56, 4, FxPacket.GROUND);   // legs
        FxSend.box(sl, E, back.add(rt.scale(-0.7)).add(0, 0.8, 0), dir, UP, 0.5f, 0.45f, 0.8f, 56, 4, FxPacket.GROUND);
        FxSend.box(sl, E, back.add(0, 2.5, 0), dir, UP, 0.8f, 1.3f, 1.0f, 56, 6, FxPacket.GROUND);                       // torso
        FxSend.box(sl, E, back.add(0, 3.9, 0), dir, UP, 0.5f, 0.5f, 0.45f, 56, 6, FxPacket.GROUND);                      // head
        FxSend.box(sl, G, back.add(dir.scale(0.45)).add(0, 3.95, 0), dir, UP, 0.06f, 0.4f, 0.08f, 54, 6, FxPacket.GROUND);  // glowing eye-slit
        for (int i = 0; i < 5; i++) {
            final int ii = i;
            AstaFx.later(12 + i * 6, () -> {
                if (!alive(p)) return;
                Vec3 centre = p.position().add(p.getLookAngle().scale(5));
                LivingEntity t = null;
                double best = 12;
                for (LivingEntity e : Kit.near(sl, p, p.position(), 10)) {
                    double d = e.position().distanceTo(p.position());
                    if (d < best) { best = d; t = e; }
                }
                Vec3 at = t != null ? t.position() : centre;
                Vec3 fistFrom = back.add(0, 3.0, 0).add(rt.scale(ii % 2 == 0 ? 1.6 : -1.6));
                Vec3 to = at.add(0, 1.0, 0);
                Vec3 v = to.subtract(fistFrom);
                FxSend.box(sl, E, fistFrom, v.normalize(), UP, 0.8f, 0.8f, 0.8f, 9, 1, FxPacket.POP, 0, v.scale(1.0 / 5), 5);
                FxSend.trail(sl, E, fistFrom, to, 0.5f, 5);
                final Vec3 hitAt = at;
                AstaFx.later(5, () -> {
                    if (!alive(p)) return;
                    Moves.nova(p, E, hitAt.add(0, 0.5, 0), 2.6, power * 0.95f, "stone_golem", 0.9, 0.45);
                    FxLib.spikeBurst(sl, E, hitAt.add(0, 0.3, 0), 0.7f);
                    FxSend.shake(sl, hitAt, 0.3f, 4, 22);
                    sound(sl, hitAt, SoundEvents.IRON_GOLEM_ATTACK, 1.0f, 0.5f);
                    sound(sl, hitAt, SoundEvents.STONE_BREAK, 1.0f, 0.6f);
                });
            });
        }
    }

    // ================================================================== 7. Bedrock Armor
    public static void bedrockArmor(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Moves.stance(p, 10, 5, -0.04, 1.0, 0, 400);
        FxSend.ring(sl, E, p.position().add(0, 0.05, 0), 0.3f, 3.2f, 0.1f, 10);
        for (int k = 0; k < 8; k++) {   // plates tear up out of the ground and fly to you
            final int kk = k;
            AstaFx.later(k + 1, () -> {
                if (!alive(p)) return;
                double a = kk * Math.PI / 4;
                Vec3 from = p.position().add(Math.cos(a) * 2.6, 0.2, Math.sin(a) * 2.6);
                Vec3 to = p.position().add(Math.cos(a) * 0.95, 0.4 + (kk % 3) * 0.55, Math.sin(a) * 0.95);
                FxSend.trail(sl, E, from, to, 0.25f, 6);
                FxSend.box(sl, E, to, new Vec3(-Math.sin(a), 0, Math.cos(a)), UP, 0.3f, 0.07f, 0.3f, 10, 1, FxPacket.POP);
            });
        }
        AstaFx.later(10, () -> { if (alive(p)) { Fx.flash(sl, p.position().add(0, 1, 0), 1.6f); FxLib.halo(sl, G, p.position().add(0, p.getBbHeight() + 0.4, 0), 0.8f); } });
        sound(sl, p.position(), SoundEvents.STONE_BREAK, 1.2f, 0.5f);
        sound(sl, p.position(), SoundEvents.ANVIL_LAND, 0.6f, 0.6f);
        Moves.mode(p, "bedrock_armor", 400, 4, pl -> {
            ServerLevel s2 = pl.serverLevel();
            long now = s2.getGameTime();
            Vec3 c = pl.position();
            for (int i = 0; i < 4; i++) {   // plates circling the body, bobbing a little
                double a = now * 0.12 + i * Math.PI / 2;
                Vec3 q = c.add(Math.cos(a) * 1.0, 0.5 + 0.6 * Math.sin(now * 0.1 + i * 1.3) + (i % 2) * 0.5, Math.sin(a) * 1.0);
                FxSend.box(s2, E, q, new Vec3(-Math.sin(a), 0, Math.cos(a)), UP, 0.22f, 0.06f, 0.25f, 5, 1, FxPacket.HOLD);
            }
            if (now % 20 == 0) FxSend.ring(s2, E, c.add(0, 0.05, 0), 0.8f, 1.3f, 0.03f, 6);
        });
    }

    // ================================================================== 8. Mountain Fall
    /**
     * A mountain's worth of rock falls from the sky on the spot you aim at (up to 32 blocks): a ring on the ground marks it
     * for a second, then the great slabs come down, and everything within 8 blocks is crushed and thrown aside.
     * Surpassing Limits: Earth Titan.
     */
    public static void mountainFall(ServerPlayer p, float power) {
        if (LimitBreak.active(p)) { AwakenFx.earthTitan(p, power); return; }
        ServerLevel sl = p.serverLevel();
        Vec3 c = floorAt(sl, Kit.aim(p, 32));
        sound(sl, c, SoundEvents.WARDEN_SONIC_CHARGE, 1.2f, 0.5f);
        sound(sl, c, SoundEvents.STONE_BREAK, 1.2f, 0.4f);
        for (int g = 0; g < 10; g++) {   // the marked ground, tightening
            final int gg = g;
            AstaFx.later(g * 2, () -> { FxSend.ring(sl, E, c.add(0, 0.08, 0), 8.2f - gg * 0.2f, 8.4f - gg * 0.2f, 0.04f, 4); });
        }
        AstaFx.later(8, () -> {
            for (int s = 0; s < 6; s++) {   // slabs come down one after another, the big one last
                final int ss = s;
                AstaFx.later(s * 2, () -> {
                    double a = ss * Math.PI / 3;
                    Vec3 tgt = ss == 5 ? c : c.add(Math.cos(a) * 4.0, 0, Math.sin(a) * 4.0);
                    float size = ss == 5 ? 3.0f : 1.8f;
                    Vec3 top = tgt.add(0, 24, 0);
                    FxSend.box(sl, E, top, new Vec3(Math.cos(a), 0, Math.sin(a)), UP, size, size, size * 1.2f, 12, 1, FxPacket.POP, 4f, new Vec3(0, -(24 - size) / 8.0, 0), 8);
                    FxSend.trail(sl, E, top, tgt.add(0, size, 0), size * 0.5f, 8);
                    AstaFx.later(8, () -> {
                        FxLib.spikeBurst(sl, E, tgt.add(0, 0.4, 0), size * 0.5f);
                        FxSend.ring(sl, E, tgt.add(0, 0.1, 0), 0.3f, 3.5f + size, 0.15f, 8);
                        FxSend.shake(sl, tgt, 0.4f + size * 0.1f, 6, 40);
                        sound(sl, tgt, SoundEvents.GENERIC_EXPLODE, 0.8f + size * 0.2f, 0.6f);
                        sound(sl, tgt, SoundEvents.STONE_BREAK, 1.3f, 0.4f);
                    });
                });
            }
        });
        AstaFx.later(8 + 10 + 8, () -> {
            if (!alive(p)) return;
            boolean any = false;
            for (LivingEntity t : Kit.near(sl, p, c.add(0, 1, 0), 8.0)) {
                float k = (float) (1.9 - 0.7 * Math.min(1, mid(t).distanceTo(c) / 8.0));
                Moves.hit(p, t, power * k, "mountain_fall", E);
                Kit.push(t, mid(t).subtract(c), 1.0, 0.7);
                any = true;
            }
            if (any) FxSend.impactFrame(sl, c, 4, FxSend.FRAME_SOL, 40);
            Fx.flash(sl, c.add(0, 1, 0), 3.2f);
            FxSend.ring(sl, E, c.add(0, 0.1, 0), 0.5f, 12f, 0.3f, 12);
            FxSend.ring(sl, G, c.add(0, 0.2, 0), 0.3f, 9f, 0.08f, 10);
            FxLib.spikeRing(sl, E, c, 1.8f);
            FxSend.shake(sl, c, 1.2f, 14, 48);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 1.6f, 0.5f);
            sound(sl, c, SoundEvents.STONE_BREAK, 1.6f, 0.3f);
        });
    }
}
