package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.FxPacket;
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
 * Surpassing Limits for the seven newer grimoires: each one's awakening burst (LimitFx.awaken hands over for books 14-20)
 * and the evolved ultimate cast while the state lasts.
 *
 *  Flame Spirit - Salamander Realm       Lion's Flame - Lion King's Domain      World Tree - World Tree Genesis
 *  Slash        - Endless Night          Earth        - Earth Titan             Beast      - Primal Ancestor
 *  Cotton       - Nean Colossus
 */
public final class AwakenFx {
    private AwakenFx() {}
    private static final int G = Fx.GOLD, S = Fx.SILVER;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    // ================================================================== awakening bursts
    public static void awaken(ServerPlayer p, int idx) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        int pal = switch (idx) { case 14 -> Moves.FUEGO; case 15 -> Moves.LION; case 16 -> Moves.RIPPER; default -> Moves.EARTH; };
        switch (idx) {
            case 14 -> {   // a salamander of flame coils up round Fuegoleon and flares out
                for (int k = 0; k < 14; k++) {
                    final int kk = k;
                    AstaFx.later(k, () -> { double a = kk * 0.7; FxSend.trail(sl, pal, c.add(Math.cos(a) * 2.0, kk * 0.25, Math.sin(a) * 2.0), c.add(Math.cos(a + 0.7) * 1.8, kk * 0.25 + 0.3, Math.sin(a + 0.7) * 1.8), 0.4f, 12); });
                }
                sound(sl, c, SoundEvents.FIRECHARGE_USE, 1f, 0.6f);
            }
            case 15 -> {   // a lion's roar: rings of flame rolling out
                for (int k = 0; k < 4; k++) { final int kk = k; AstaFx.later(k * 3, () -> FxSend.ring(sl, pal, c.add(0, 0.1 + kk * 0.5, 0), 0.5f, 4.0f + kk * 2.5f, 0.15f, 10)); }
                sound(sl, c, SoundEvents.RAVAGER_ROAR, 1.4f, 0.6f);
            }
            case 16 -> {   // lines of edge flash across the whole space round Jack
                Random r = new Random(p.getId());
                for (int i = 0; i < 14; i++) { final int ii = i; AstaFx.later(i, () -> SlashFx.cut(sl, c.add((r.nextDouble() - 0.5) * 5, 0.6 + r.nextDouble() * 2, (r.nextDouble() - 0.5) * 5), new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5), 2.2f, 0.07f, 6)); }
                sound(sl, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 1.4f);
            }
            default -> {   // the ground rises in plates round Sol
                for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; FxSend.box(sl, pal, c.add(Math.cos(a) * 3, 1.0, Math.sin(a) * 3), new Vec3(-Math.sin(a), 0, Math.cos(a)), UP, 0.9f, 0.2f, 1.2f, 30, 5, FxPacket.GROUND); }
                sound(sl, c, SoundEvents.STONE_BREAK, 1.2f, 0.4f);
            }
        }
        FxSend.ring(sl, pal, c.add(0, 0.1, 0), 0.5f, 6.0f, 0.2f, 14);
    }

    /** Shared shape of a Surpassing Limits ultimate: a domain around the caster for 3 s that holds and batters, then a great blow. */
    private static void domain(ServerPlayer p, float power, int pal, double R, String id, int frame, java.util.function.BiConsumer<Integer, Vec3> draw, float endMul) {
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, pal, p.position().add(0, 0.05, 0), 0.5f, (float) R, 0.15f, 14);
        for (int t = 0; t < 60; t++) {
            final int tt = t;
            AstaFx.later(t + 1, () -> {
                if (!alive(p)) return;
                Vec3 o = p.position().add(0, 1, 0);
                draw.accept(tt, o);
                for (LivingEntity e : Kit.near(sl, p, o, R)) {
                    Vec3 lift = new Vec3(0, Math.max(-0.1, Math.min(0.1, (o.y + 1.5 - mid(e).y) * 0.07)), 0);
                    e.setDeltaMovement(e.getDeltaMovement().scale(0.45).add(lift));
                    e.hurtMarked = true;
                    if (tt % 8 == 7) Moves.hit(p, e, power * 0.3f, id, pal);
                }
            });
        }
        AstaFx.later(62, () -> {
            if (!alive(p)) return;
            Vec3 o = p.position().add(0, 1, 0);
            boolean any = false;
            for (LivingEntity e : Kit.near(sl, p, o, R)) {
                Moves.hit(p, e, power * endMul, id, pal);
                Kit.push(e, mid(e).subtract(o), 1.3, 0.7);
                any = true;
            }
            if (any) FxSend.impactFrame(sl, o, 4, frame, 40);
            Fx.flash(sl, o, 3.6f);
            FxSend.ring(sl, pal, o.add(0, -0.9, 0), 0.5f, (float) R * 1.6f, 0.4f, 14);
            FxSend.ring(sl, G, o.add(0, -0.7, 0), 0.3f, (float) R * 1.2f, 0.1f, 11);
            FxSend.shake(sl, o, 1.3f, 16, 56);
            sound(sl, o, SoundEvents.GENERIC_EXPLODE, 1.8f, 0.5f);
            sound(sl, o, SoundEvents.TOTEM_USE, 0.8f, 0.7f);
        });
    }

    private static void rings(ServerLevel sl, int pal, Vec3 o, double R, int tt) {
        if (tt % 6 != 0) return;
        for (int r = 0; r < 3; r++) {
            double h = -0.4 + r * 0.45, rad = R * Math.sqrt(Math.max(0.05, 1 - h * h));
            FxSend.ring(sl, pal, o.add(0, h * R * 0.6, 0), (float) rad, (float) rad - 0.2f, 0.1f, 8);
        }
    }

    // ================================================================== Fuegoleon: Salamander Realm
    public static void salamanderRealm(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final double R = 10;
        sound(sl, p.position(), SoundEvents.FIRECHARGE_USE, 1.4f, 0.5f);
        sound(sl, p.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1.0f, 0.6f);
        domain(p, power, Moves.FUEGO, R, "great_salamander", FxSend.FRAME_FUEGOLEON, (tt, o) -> {
            rings(sl, Moves.FUEGO, o, R, tt);
            for (int d = 0; d < 3; d++) {   // three salamanders of flame circling the realm
                double a = tt * 0.24 + d * Math.PI * 2 / 3, a2 = a - 0.5;
                double h1 = Math.sin(tt * 0.15 + d * 2) * 3, h2 = Math.sin((tt - 2) * 0.15 + d * 2) * 3;
                Vec3 q1 = o.add(Math.cos(a) * R * 0.7, h1, Math.sin(a) * R * 0.7), q0 = o.add(Math.cos(a2) * R * 0.7, h2, Math.sin(a2) * R * 0.7);
                FxSend.trail(sl, Moves.FUEGO, q0, q1, 0.8f, 8);
                FxSend.trail(sl, G, q0, q1, 0.14f, 5);
                if (tt % 2 == 0) Kit.orb(sl, Moves.FUEGO, q1, q1.subtract(q0), 0.7f, 2);
            }
        }, 2.4f);
    }

    // ================================================================== Leopold: Lion King's Domain
    public static void lionKingDomain(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final double R = 10;
        sound(sl, p.position(), SoundEvents.RAVAGER_ROAR, 1.8f, 0.5f);
        domain(p, power, Moves.LION, R, "king_lion_roar", FxSend.FRAME_LEOPOLD, (tt, o) -> {
            rings(sl, Moves.LION, o, R, tt);
            for (int l = 0; l < 4; l++) {   // four flame lions' heads round the edge, roaring inward in turn
                double a = l * Math.PI / 2 + 0.4;
                Vec3 c = o.add(Math.cos(a) * R * 0.8, 1.5, Math.sin(a) * R * 0.8);
                Vec3 face = o.subtract(c).normalize(), rt = rightOf(face), up = rt.cross(face).normalize();
                if (tt % 3 == 0) {
                    FxSend.wall(sl, Moves.LION, c, rt, up, 1.0f, 1.4f, -180, 180, 0.25f, 4, 0, 0);
                    for (int i = 0; i < 10; i++) {
                        double b = i * Math.PI / 5;
                        FxSend.trail(sl, Moves.LION, c.add(rt.scale(Math.cos(b) * 1.4)).add(up.scale(Math.sin(b) * 1.4)), c.add(rt.scale(Math.cos(b + 0.2) * 2.2)).add(up.scale(Math.sin(b + 0.2) * 2.2)), 0.16f, 4);
                    }
                }
                if ((tt + l * 10) % 20 == 0) {
                    FxSend.beam(sl, Moves.LION, c, o, 0.8f, 6);
                    FxSend.beam(sl, G, c, o, 0.2f, 5);
                    sound(sl, c, SoundEvents.RAVAGER_ROAR, 0.8f, 0.8f + l * 0.1f);
                }
            }
        }, 2.6f);
    }

    // ================================================================== William: World Tree Genesis

    // ================================================================== Jack: Endless Night
    public static void endlessNight(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final double R = 12;
        sound(sl, p.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1.2f, 1.6f);
        Random rnd = new Random(p.getId() * 77L);
        domain(p, power, Moves.RIPPER, R, "bloody_massacre", FxSend.FRAME_JACK, (tt, o) -> {
            for (int i = 0; i < 4; i++) {   // the air is cut to ribbons: cuts everywhere, every tick
                Vec3 at = o.add((rnd.nextDouble() - 0.5) * R * 1.6, (rnd.nextDouble() - 0.3) * 4, (rnd.nextDouble() - 0.5) * R * 1.6);
                SlashFx.cut(sl, at, new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5), 1.2f + rnd.nextFloat() * 1.6f, 0.06f, 4);
            }
            if (tt % 10 == 0) FxSend.ring(sl, Moves.RIPPER, o.add(0, -0.9, 0), 0.5f, (float) R, 0.04f, 6);
            if (tt % 20 == 0) sound(sl, o, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 1.8f);
        }, 3.0f);
    }

    // ================================================================== Sol: Earth Titan
    public static void earthTitan(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 dir = flat(p.getLookAngle()), rt = rightOf(dir);
        Vec3 at = Kit.aim(p, 28);
        Vec3 g = FxLib.ground(sl, at.add(0, 3, 0), 10);
        Vec3 c = g == null ? at : g;
        sound(sl, c, SoundEvents.WARDEN_SONIC_CHARGE, 1.4f, 0.4f);
        sound(sl, c, SoundEvents.STONE_BREAK, 1.6f, 0.3f);
        Vec3 base = c.subtract(dir.scale(9));
        for (int i = 0; i < 6; i++) FxSend.ring(sl, Moves.EARTH, base.add(0, 0.05, 0), 0.5f + i, 3f + i * 1.4f, 0.1f, 8);
        AstaFx.later(4, () -> {   // the titan stands up behind the target, a hill of rock with legs, arms and a head
            FxSend.box(sl, Moves.EARTH, base.add(rt.scale(1.8)).add(0, 3, 0), dir, UP, 1.6f, 1.5f, 3.0f, 80, 14, FxPacket.GROUND);
            FxSend.box(sl, Moves.EARTH, base.add(rt.scale(-1.8)).add(0, 3, 0), dir, UP, 1.6f, 1.5f, 3.0f, 80, 14, FxPacket.GROUND);
            FxSend.box(sl, Moves.EARTH, base.add(0, 9, 0), dir, UP, 2.6f, 4.2f, 3.2f, 80, 14, FxPacket.GROUND);
            FxSend.box(sl, Moves.EARTH, base.add(0, 14, 0), dir, UP, 1.5f, 1.5f, 1.4f, 80, 14, FxPacket.GROUND);
            FxSend.box(sl, G, base.add(dir.scale(1.5)).add(0, 14.2, 0), dir, UP, 0.1f, 1.2f, 0.18f, 78, 14, FxPacket.GROUND);
        });
        for (int s = 0; s < 3; s++) {
            final int ss = s;
            AstaFx.later(22 + s * 9, () -> {   // three fists driven down
                if (!alive(p)) return;
                Vec3 tgt = c.add(rt.scale((ss - 1) * 4.5));
                Vec3 from = base.add(rt.scale((ss - 1) * 5)).add(0, 14, 0);
                Vec3 v = tgt.add(0, 1, 0).subtract(from);
                FxSend.box(sl, Moves.EARTH, from, v.normalize(), UP, 2.0f, 2.0f, 2.0f, 9, 1, FxPacket.POP, 0, v.scale(1.0 / 6), 6);
                AstaFx.later(6, () -> {
                    if (!alive(p)) return;
                    Moves.nova(p, Moves.EARTH, tgt.add(0, 0.5, 0), 5.0, power * 1.3f, "mountain_fall", 1.0, 0.7);
                    FxLib.spikeRing(sl, Moves.EARTH, tgt, 1.4f);
                    FxSend.shake(sl, tgt, 0.7f, 8, 40);
                    sound(sl, tgt, SoundEvents.GENERIC_EXPLODE, 1.3f, 0.5f);
                });
            });
        }
        AstaFx.later(52, () -> {
            if (!alive(p)) return;
            boolean any = false;
            for (LivingEntity t : Kit.near(sl, p, c.add(0, 1, 0), 10)) {
                Moves.hit(p, t, power * 1.6f, "mountain_fall", Moves.EARTH);
                Kit.push(t, mid(t).subtract(c), 1.2, 0.8);
                any = true;
            }
            if (any) FxSend.impactFrame(sl, c, 4, FxSend.FRAME_SOL, 40);
            Fx.flash(sl, c.add(0, 2, 0), 3.4f);
            FxSend.ring(sl, Moves.EARTH, c.add(0, 0.1, 0), 0.5f, 15f, 0.35f, 14);
            FxSend.shake(sl, c, 1.4f, 16, 56);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 1.8f, 0.4f);
        });
    }

    // ================================================================== BeastOfDespair: Primal Ancestor

    // ================================================================== Charmy: Nean Colossus
}
