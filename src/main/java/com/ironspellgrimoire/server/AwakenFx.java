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
        int pal = switch (idx) { case 14 -> Moves.FUEGO; case 15 -> Moves.LION; case 16 -> Moves.TREE; case 17 -> Moves.RIPPER; case 18 -> Moves.EARTH; case 19 -> Moves.BEAST; default -> Moves.COTTON; };
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
            case 16 -> {   // roots and vines tear up from the ground all round
                for (int i = 0; i < 10; i++) { double a = i * Math.PI / 5; FxSend.spike(sl, pal, c.add(Math.cos(a) * 3, -0.2, Math.sin(a) * 3), new Vec3(Math.cos(a) * 0.3, 1, Math.sin(a) * 0.3), 4.5f, 0.3f, 30, 6, 1); }
                sound(sl, c, SoundEvents.ROOTED_DIRT_BREAK, 1.2f, 0.5f);
            }
            case 17 -> {   // lines of edge flash across the whole space round Jack
                Random r = new Random(p.getId());
                for (int i = 0; i < 14; i++) { final int ii = i; AstaFx.later(i, () -> SlashFx.cut(sl, c.add((r.nextDouble() - 0.5) * 5, 0.6 + r.nextDouble() * 2, (r.nextDouble() - 0.5) * 5), new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5), 2.2f, 0.07f, 6)); }
                sound(sl, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 1.4f);
            }
            case 18 -> {   // the ground rises in plates round Sol
                for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; FxSend.box(sl, pal, c.add(Math.cos(a) * 3, 1.0, Math.sin(a) * 3), new Vec3(-Math.sin(a), 0, Math.cos(a)), UP, 0.9f, 0.2f, 1.2f, 30, 5, FxPacket.GROUND); }
                sound(sl, c, SoundEvents.STONE_BREAK, 1.2f, 0.4f);
            }
            case 19 -> {   // a howl, and five spirits flash past in a circle
                for (int b = 0; b < 5; b++) { final int bb = b; for (int k = 0; k < 6; k++) { final int kk = k; AstaFx.later(k + bb, () -> { double a = bb * 1.256 + kk * 0.35; BeastFx.spirit(sl, c.add(Math.cos(a) * 4.5, 0.9, Math.sin(a) * 4.5), new Vec3(-Math.sin(a), 0, Math.cos(a)), 0.7f, bb, 4); }); } }
                sound(sl, c, SoundEvents.WOLF_HOWL, 1.4f, 0.7f);
            }
            default -> {   // a flood of cotton billows out
                Random r = new Random(p.getId());
                for (int i = 0; i < 12; i++) { double a = r.nextDouble() * Math.PI * 2, d = 1 + r.nextDouble() * 4; CottonFx.puff(sl, c.add(Math.cos(a) * d, 0.4 + r.nextDouble() * 2.5, Math.sin(a) * d), 0.8f, 18); }
                sound(sl, c, SoundEvents.SHEEP_AMBIENT, 1.2f, 0.6f);
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
    public static void worldTreeGenesis(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final double R = 11;
        Vec3 c0 = p.position();
        sound(sl, c0, SoundEvents.ROOTED_DIRT_BREAK, 1.6f, 0.4f);
        for (int t = 0; t < 8; t++) {   // a ring of great trees stands up round the edge
            double a = t * Math.PI / 4;
            Vec3 b = c0.add(Math.cos(a) * R * 0.8, -0.3, Math.sin(a) * R * 0.8);
            final int tt = t;
            AstaFx.later(tt, () -> {
                FxSend.spike(sl, Moves.TREE, b, UP, 11f, 1.0f, 66, 10, FxPacket.GROUND);
                FxSend.spike(sl, G, b, UP, 11f, 0.18f, 64, 10, FxPacket.GROUND);
                for (int br = 0; br < 5; br++) {
                    double ba = br * Math.PI * 2 / 5;
                    FxSend.spike(sl, Moves.TREE, b.add(0, 10.5, 0), new Vec3(Math.cos(ba), 0.1, Math.sin(ba)), 5f, 0.3f, 60, 8, FxPacket.EXTEND);
                }
            });
        }
        domain(p, power, Moves.TREE, R, "world_tree_judgement", FxSend.FRAME_WILLIAM, (tt, o) -> {
            rings(sl, Moves.TREE, o, R, tt);
            for (int v = 0; v < 4; v++) {   // vines reaching in from the trees to the centre and lifting
                double a = tt * 0.12 + v * Math.PI / 2;
                Vec3 q0 = o.add(Math.cos(a) * R * 0.8, -0.8, Math.sin(a) * R * 0.8), q1 = o.add(Math.cos(a + 0.6) * R * 0.3, 1.5 + Math.sin(tt * 0.2 + v), Math.sin(a + 0.6) * R * 0.3);
                FxSend.trail(sl, Moves.TREE, q0, q1, 0.3f, 4);
            }
        }, 2.6f);
    }

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

    // ================================================================== Vetto: Primal Ancestor
    public static void primalAncestor(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final double R = 12;
        sound(sl, p.position(), SoundEvents.WOLF_HOWL, 1.8f, 0.5f);
        sound(sl, p.position(), SoundEvents.RAVAGER_ROAR, 1.4f, 0.6f);
        domain(p, power, Moves.BEAST, R, "primal_king", FxSend.FRAME_VETTO, (tt, o) -> {
            rings(sl, Moves.BEAST, o, R, tt);
            for (int b = 0; b < 5; b++) {   // five giant spirits circling, each lunging inward now and then
                double a = tt * 0.16 + b * Math.PI * 2 / 5;
                double lunge = Math.max(0, Math.sin((tt + b * 11) * 0.16)) * 0.35;
                double rad = R * (0.75 - lunge);
                Vec3 pos = o.add(Math.cos(a) * rad, 0.3 + (b == 1 ? 3.5 : 0.8) + Math.sin(tt * 0.3 + b) * 0.4, Math.sin(a) * rad);
                BeastFx.spirit(sl, pos, new Vec3(-Math.sin(a), 0, Math.cos(a)), 1.5f, b, 5);
            }
        }, 2.6f);
    }

    // ================================================================== Charmy: Nean Colossus
    public static void neanColossus(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 dir = flat(p.getLookAngle());
        Vec3 from = p.position().add(dir.scale(2));
        Set<UUID> hit = new HashSet<>();
        sound(sl, p.position(), SoundEvents.SHEEP_AMBIENT, 2.0f, 0.4f);
        sound(sl, p.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1.0f, 1.0f);
        FxSend.ring(sl, Moves.COTTON, p.position().add(0, 0.05, 0), 0.3f, 4.5f, 0.15f, 12);
        for (int k = 1; k <= 22; k++) {   // a colossal Nean thunders forward, 3 times her size
            final int kk = k;
            AstaFx.later(6 + k, () -> {
                if (!alive(p)) return;
                Vec3 pos = from.add(dir.scale(kk * 1.9)).add(0, 3.0, 0);
                CottonFx.nean(sl, pos, dir, 2.8f, kk);
                if (kk % 2 == 0) CottonFx.puff(sl, pos.subtract(dir.scale(3)).add(0, -2, 0), 1.0f, 10);
                for (LivingEntity t : Kit.near(sl, p, pos, 5.0)) {
                    if (!hit.add(t.getUUID())) continue;
                    Moves.hit(p, t, power * 1.8f, "grand_banquet", Moves.COTTON);
                    Kit.push(t, dir, 1.5, 0.7);
                    Fx.impact(sl, Moves.COTTON, mid(t), 1.6f);
                }
                if (kk % 3 == 0) FxSend.shake(sl, pos, 0.4f, 4, 36);
            });
        }
        Vec3 end = from.add(dir.scale(22 * 1.9));
        AstaFx.later(6 + 23, () -> {   // at the end of the run, a rain of cakes
            if (!alive(p)) return;
            Moves.rain(p, 16, end, 8.0, 10, 2, 5, power * 0.9f, "grand_banquet", 3.2);
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI / 5;
                CottonFx.puff(sl, end.add(Math.cos(a) * 5, 1, Math.sin(a) * 5), 1.0f, 20);
            }
            FxSend.impactFrame(sl, end, 4, FxSend.FRAME_CHARMY, 40);
            Fx.flash(sl, end.add(0, 1, 0), 3.4f);
            FxSend.ring(sl, Moves.COTTON, end.add(0, 0.1, 0), 0.5f, 13f, 0.35f, 14);
            sound(sl, end, SoundEvents.GENERIC_EXPLODE, 1.6f, 0.6f);
        });
    }
}
