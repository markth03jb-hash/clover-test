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
 * Fuegoleon Vermillion's Flame Spirit Magic - the eight spells of the Flame Spirit Grimoire. A noble, disciplined fire:
 * deep vermilion with a white-gold heart, drawn in clean lines (lances, coils, rings) with the Salamander spirit running
 * through all of it.
 *
 *  1 Salamander Flare   - a fast spirit-flame that bursts in a crown of flame
 *  2 Spirit Lance       - a white-hot lance of flame, wrapped in a corkscrew, through everything ahead
 *  3 Flame Aegis        - three rings of flame turn round you and throw back anything that nears
 *  4 Salamander Coil    - the spirit itself, a serpent of fire, coils through your enemies
 *  5 Flare Volley       - seven spears of flame fall on the spot you aim at
 *  6 Salamander Rush    - you become the spirit and tear forward through whatever is in the way
 *  7 Salamander Spirit  - for 20 s the spirit wraps you: tougher, faster, scorching all who stand close
 *  8 Great Salamander   - a great fire spirit rushes out, rears up and crashes down in a blast
 *
 * Passive "Scorch": whatever your flames hit keeps burning for three more pulses.
 */
public final class FlameSpiritFx {
    private FlameSpiritFx() {}
    private static final int F = Moves.FUEGO, S = Fx.SILVER, G = Fx.GOLD;

    // ================================================================== 1. Salamander Flare
    public static void salamanderFlare(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        for (int k = 0; k < 6; k += 2) {
            final int kk = k;
            AstaFx.later(k + 1, () -> {
                if (!alive(p)) return;
                Kit.orb(sl, F, Kit.hand(p), Vec3.ZERO, 0.12f + kk * 0.05f, 3);
                FxSend.ring(sl, F, Kit.hand(p), 0.05f, 0.5f + kk * 0.1f, 0.03f, 3);
            });
        }
        sound(sl, p.position(), SoundEvents.FIRECHARGE_USE, 0.8f, 1.3f);
        AstaFx.later(7, () -> {
            if (!alive(p)) return;
            Moves.bolt(p, F, Kit.hand(p), p.getLookAngle(), 1.9f, 0.55f, 34, 3.0, power, "salamander_flare", 0.6, at -> {
                for (int i = 0; i < 6; i++) {   // a crown of flame
                    double a = i * Math.PI / 3;
                    FxSend.spike(sl, F, at.add(Math.cos(a) * 0.5, -0.3, Math.sin(a) * 0.5), new Vec3(Math.cos(a) * 0.5, 1, Math.sin(a) * 0.5), 1.8f, 0.22f, 8, 3, 0);
                }
                sound(sl, at, SoundEvents.GENERIC_EXPLODE, 0.7f, 1.3f);
            });
            sound(sl, p.position(), SoundEvents.BLAZE_SHOOT, 0.8f, 1.1f);
        });
    }

    // ================================================================== 2. Spirit Lance
    public static void spiritLance(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        sound(sl, p.position(), SoundEvents.FIRECHARGE_USE, 0.8f, 0.9f);
        for (int k = 0; k < 8; k += 2) {
            final int kk = k;
            AstaFx.later(k + 1, () -> { if (alive(p)) FxLib.halo(sl, F, Kit.hand(p).add(p.getLookAngle().scale(0.4)), 0.3f + kk * 0.08f); });
        }
        AstaFx.later(9, () -> {
            if (!alive(p)) return;
            Vec3 from = Kit.hand(p), dir = p.getLookAngle();
            Vec3 end = Moves.lance(p, F, 26, 0.95, 0.4f, power * 1.2f, "spirit_lance", 0.9);
            Vec3 rt = rightOf(dir), up = rt.cross(dir).normalize();
            Vec3 prev = from;
            for (int i = 1; i <= 12; i++) {   // the corkscrew of flame round the lance
                double t = i / 12.0, a = t * 18;
                Vec3 q = from.add(end.subtract(from).scale(t)).add(rt.scale(Math.cos(a) * 0.55)).add(up.scale(Math.sin(a) * 0.55));
                FxSend.trail(sl, F, prev, q, 0.1f, 6);
                prev = q;
            }
            FxLib.starburst(sl, F, end, dir, 1.3f);
            FxSend.shake(sl, from, 0.25f, 4, 20);
            sound(sl, from, SoundEvents.BLAZE_SHOOT, 1.0f, 0.7f);
            sound(sl, from, SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.2f);
        });
    }

    // ================================================================== 3. Flame Aegis
    public static void flameAegis(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final double R = 3.6;
        Moves.stance(p, 4, 2, 0, 0.3, 0, 120);
        sound(sl, p.position(), SoundEvents.FIRECHARGE_USE, 1.0f, 0.7f);
        for (int t = 0; t < 100; t++) {
            final int tt = t;
            AstaFx.later(t + 1, () -> {
                if (!alive(p)) return;
                Vec3 c = p.position();
                if (tt % 3 == 0) for (int r = 0; r < 3; r++) {   // three rings of flame turning at different heights
                    double a = tt * 0.3 * (r % 2 == 0 ? 1 : -1) + r * 2.1;
                    double h = 0.4 + r * 0.7, rad = R * (0.7 + 0.12 * r);
                    Vec3 q0 = c.add(Math.cos(a) * rad, h, Math.sin(a) * rad), q1 = c.add(Math.cos(a + 0.9) * rad, h, Math.sin(a + 0.9) * rad);
                    FxSend.trail(sl, F, q0, q1, 0.2f, 5);
                    FxSend.trail(sl, G, q0, q1, 0.05f, 3);
                }
                if (tt % 10 == 0) FxSend.ring(sl, F, c.add(0, 0.05, 0), (float) (R * 0.9), (float) R, 0.04f, 6);
                if (tt % 4 == 0) for (LivingEntity e : Kit.near(sl, p, c, R)) {
                    Vec3 away = e.position().subtract(c);
                    Kit.push(e, away, 0.35, 0.08);
                    if (tt % 20 == 0) { Moves.hit(p, e, power * 0.3f, "flame_aegis", F); Fx.impact(sl, F, mid(e), 0.9f); }
                }
            });
        }
        AstaFx.later(102, () -> { if (alive(p)) Moves.nova(p, F, p.position().add(0, 0.5, 0), R + 1, power * 0.6f, "flame_aegis", 0.9, 0.4); });
    }

    // ================================================================== 4. Salamander Coil
    public static void salamanderCoil(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 dir = p.getLookAngle(), rt = rightOf(dir), up = rt.cross(dir).normalize();
        Vec3 from = Kit.hand(p);
        Set<UUID> hit = new HashSet<>();
        sound(sl, p.position(), SoundEvents.FIRECHARGE_USE, 1.0f, 0.6f);
        Vec3[] prev = {from};
        final int T = 18;
        for (int k = 1; k <= T; k++) {
            final int kk = k;
            AstaFx.later(5 + k, () -> {
                if (!alive(p)) return;
                double s = kk * 1.6, wob = Math.sin(kk * 0.8) * 1.3 * Math.min(1, kk / 4.0);
                Vec3 pos = from.add(dir.scale(s)).add(rt.scale(wob)).add(up.scale(Math.cos(kk * 0.8) * 0.6));
                float w = 0.6f - kk * 0.014f;
                FxSend.trail(sl, F, prev[0], pos, w, 9);
                FxSend.trail(sl, G, prev[0], pos, w * 0.3f, 5);
                Kit.orb(sl, F, pos, dir.scale(1.6), 0.55f, 2);
                if (kk == 1 || kk % 6 == 0) {   // the spirit's mane: two flickers swept back from the head
                    FxSend.trail(sl, F, pos, pos.add(rt.scale(0.9)).subtract(dir.scale(0.8)).add(up.scale(0.4)), 0.12f, 5);
                    FxSend.trail(sl, F, pos, pos.add(rt.scale(-0.9)).subtract(dir.scale(0.8)).add(up.scale(0.4)), 0.12f, 5);
                }
                for (LivingEntity t : Kit.near(sl, p, pos, 1.7)) {
                    if (!hit.add(t.getUUID())) continue;
                    Moves.hit(p, t, power, "salamander_coil", F);
                    Kit.push(t, dir, 0.6, 0.3);
                    Fx.impact(sl, F, mid(t), 1.1f);
                }
                if (kk == T) { Fx.impact(sl, F, pos, 1.8f); FxSend.ring(sl, F, pos, 0.2f, 3f, 0.07f, 6); }
                prev[0] = pos;
            });
        }
    }

    // ================================================================== 5. Flare Volley
    public static void flareVolley(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = Kit.aim(p, 26);
        sound(sl, p.position(), SoundEvents.FIRECHARGE_USE, 0.9f, 0.8f);
        FxSend.ring(sl, F, c.add(0, 0.1, 0), 0.4f, 5.5f, 0.06f, 14);
        Moves.rain(p, F, c, 4.5, 7, 2, 5, power * 0.8f, "flare_volley", 2.6);
    }

    // ================================================================== 6. Salamander Rush
    public static void salamanderRush(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Set<UUID> hit = new HashSet<>();
        FxSend.ring(sl, F, p.position().add(0, 0.05, 0), 0.3f, 2.6f, 0.08f, 8);
        sound(sl, p.position(), SoundEvents.FIRECHARGE_USE, 1.0f, 1.4f);
        AstaFx.later(3, () -> {
            if (!alive(p)) return;
            Vec3 dir = flat(p.getLookAngle());
            Moves.dash(p, F, dir, 11, 6, (at, i) -> {
                Kit.orb(sl, F, at.add(0, 1, 0), dir.scale(1.5), 0.9f, 2);
                for (LivingEntity t : Kit.near(sl, p, at.add(0, 1, 0), 2.2)) {
                    if (!hit.add(t.getUUID())) continue;
                    Moves.hit(p, t, power * 1.3f, "salamander_rush", F);
                    Kit.push(t, dir, 1.0, 0.4);
                    Fx.impact(sl, F, mid(t), 1.2f);
                }
                if (i == 6) { FxSend.ring(sl, F, at.add(0, 0.1, 0), 0.3f, 3.4f, 0.1f, 9); Fx.flash(sl, at.add(0, 1, 0), 1.6f); }
            });
        });
    }

    // ================================================================== 7. Salamander Spirit
    public static void salamanderSpirit(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Moves.stance(p, 6, 2, 0.12, 0.2, 0, 400);
        for (int k = 0; k < 12; k++) {   // the spirit spirals up round you
            final int kk = k;
            AstaFx.later(k + 1, () -> {
                if (!alive(p)) return;
                Vec3 b = p.position();
                for (int s = 0; s < 2; s++) {
                    double a = kk * 0.6 + s * Math.PI;
                    FxSend.trail(sl, F, b.add(Math.cos(a) * 0.9, kk * 0.15, Math.sin(a) * 0.9), b.add(Math.cos(a + 0.6) * 0.8, kk * 0.15 + 0.25, Math.sin(a + 0.6) * 0.8), 0.14f, 6);
                }
            });
        }
        FxSend.ring(sl, F, p.position().add(0, 0.05, 0), 0.3f, 3.4f, 0.1f, 10);
        AstaFx.later(13, () -> { if (alive(p)) { Fx.flash(sl, p.position().add(0, 1, 0), 1.8f); FxLib.halo(sl, F, p.position().add(0, p.getBbHeight() + 0.4, 0), 0.9f); } });
        sound(sl, p.position(), SoundEvents.FIRECHARGE_USE, 1.0f, 0.6f);
        sound(sl, p.position(), SoundEvents.BEACON_ACTIVATE, 0.7f, 1.4f);
        Moves.aura(p, 2, F, 400);   // the ribbons of flame and the little spirit, drawn by the client every frame
        Moves.mode(p, "salamander_spirit", 400, 4, pl -> {
            long now = pl.serverLevel().getGameTime();
            ServerLevel s2 = pl.serverLevel();
            Vec3 c = pl.position();
            if (now % 20 == 0) for (LivingEntity e : Kit.near(s2, pl, c, 2.8)) {
                Moves.hit(pl, e, power * 0.3f, "salamander_spirit", F);
                Fx.impact(s2, F, mid(e), 0.8f);
            }
        });
    }

    // ================================================================== 8. Great Salamander
    /**
     * The spirit made whole: a great salamander of fire runs out along the ground ahead of you, rears up above the spot
     * you aim at and crashes down there - everything within 7 blocks takes a colossal blow.
     * Surpassing Limits: Salamander Realm.
     */
    public static void greatSalamander(ServerPlayer p, float power) {
        if (LimitBreak.active(p)) { AwakenFx.salamanderRealm(p, power); return; }
        ServerLevel sl = p.serverLevel();
        Vec3 target = Kit.aim(p, 32);
        Vec3 from = p.position().add(0, 1.2, 0), to = target.subtract(from);
        final int T = 22;
        sound(sl, from, SoundEvents.FIRECHARGE_USE, 1.4f, 0.5f);
        sound(sl, from, SoundEvents.WARDEN_SONIC_CHARGE, 0.8f, 0.8f);
        for (int g = 0; g < 8; g++) {
            final int gg = g;
            AstaFx.later(g, () -> { if (alive(p)) FxSend.ring(sl, F, p.position().add(0, 0.05, 0), 0.4f + gg * 0.3f, 1.2f + gg * 0.4f, 0.06f, 5); });
        }
        Vec3[] prev = {from};
        for (int k = 1; k <= T; k++) {
            final int kk = k;
            AstaFx.later(8 + k, () -> {
                if (!alive(p)) return;
                double t = kk / (double) T;
                Vec3 flatTo = flat(to), rt = rightOf(flatTo);
                Vec3 pos = from.add(to.scale(t)).add(rt.scale(Math.sin(kk * 0.9) * 1.4 * (1 - t))).add(0, Math.sin(t * Math.PI) * 5.0, 0);
                Vec3 tan = pos.subtract(prev[0]);
                FxSend.trail(sl, F, prev[0], pos, 1.0f - (float) t * 0.2f, 12);
                FxSend.trail(sl, G, prev[0], pos, 0.2f, 8);
                Kit.orb(sl, F, pos, tan, 1.2f, 2);
                // horns and a flowing mane of flame swept back from the head
                Vec3 d = tan.lengthSqr() < 1.0E-4 ? new Vec3(0, -1, 0) : tan.normalize();
                Vec3 r2 = rightOf(d), u2 = r2.cross(d).normalize();
                FxSend.trail(sl, F, pos, pos.add(r2.scale(1.3)).subtract(d.scale(1.5)).add(u2.scale(0.6)), 0.25f, 7);
                FxSend.trail(sl, F, pos, pos.add(r2.scale(-1.3)).subtract(d.scale(1.5)).add(u2.scale(0.6)), 0.25f, 7);
                if (kk % 3 == 0) FxSend.ring(sl, F, pos, 0.2f, 2.0f, 0.05f, 5);
                prev[0] = pos;
            });
        }
        AstaFx.later(8 + T + 1, () -> {
            if (!alive(p)) return;
            boolean any = false;
            for (LivingEntity t : Kit.near(sl, p, target, 7.0)) {
                Moves.hit(p, t, power * (float) (1.8 - 0.6 * Math.min(1, mid(t).distanceTo(target) / 7.0)), "great_salamander", F);
                Kit.push(t, mid(t).subtract(target), 1.1, 0.8);
                any = true;
            }
            if (any) FxSend.impactFrame(sl, target, 4, FxSend.FRAME_FUEGOLEON, 40);
            Fx.flash(sl, target, 3.2f);
            Fx.pillar(sl, F, target, 16f, 2.4f, 18);
            FxSend.ring(sl, F, target.add(0, 0.1, 0), 0.5f, 11f, 0.3f, 12);
            FxSend.ring(sl, G, target.add(0, 0.2, 0), 0.3f, 8f, 0.08f, 10);
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI / 5;
                FxSend.trail(sl, F, target, target.add(Math.cos(a) * 5, 4 + (i % 3), Math.sin(a) * 5), 0.28f, 9);
            }
            FxSend.shake(sl, target, 1.1f, 14, 48);
            sound(sl, target, SoundEvents.GENERIC_EXPLODE, 1.6f, 0.6f);
            sound(sl, target, SoundEvents.DRAGON_FIREBALL_EXPLODE, 1.0f, 0.7f);
        });
    }
}
