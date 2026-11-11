package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.BossKit.*;
import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.entity.Reflection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Reflection's fight (the Ascension Trial): you, with your own grimoire's magic in its colours.
 * <ul>
 *   <li>Phase 1: Mirror Volley (three lines of bolts in a fan), Mirror Cut (a long slash down a line), Starfall (pillars
 *       of your magic coming down round where you stand), and a step behind you.</li>
 *   <li>Phase 2 (below half): its magic overflows - Nova (a ring of it bursting out round it), and its Awakened Page: a
 *       great circle on the ground where you stand, ringed by pillars, that closes and bursts.</li>
 * </ul>
 * Every move is shown before it lands. Nothing breaks blocks.
 */
public final class ReflectionAI {
    private ReflectionAI() {}

    private static final String WHO = "Reflection";
    private static final ChatFormatting COL = ChatFormatting.WHITE;

    public static void tick(Reflection b) {
        ServerLevel sl = (ServerLevel) b.level();
        Player t = target(b);
        if (t == null) { b.getNavigation().stop(); return; }
        if (b.tickCount == 20) say(b, WHO, COL, "Everything you know, I know. Surpass it - surpass me.");
        b.getLookControl().setLookAt(t, 30, 30);
        if (b.tickCount % 10 == 0) BossFx.ring(sl, b.pal, b.position().add(0, 0.05, 0), 0.9f, 1.0f, 0.04f, 10);   // its magic, always round its feet
        if (b.busy > 0) { b.busy--; return; }
        keep(b, t, 7);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<java.util.function.ToIntBiFunction<Reflection, Player>> pool = new ArrayList<>();
        pool.add(ReflectionAI::volley);
        pool.add(ReflectionAI::cut);
        pool.add(ReflectionAI::starfall);
        if (b.distanceTo(t) > 10 || r.nextInt(4) == 0) pool.add(ReflectionAI::step);
        if (b.phase() >= 2) { pool.add(ReflectionAI::nova); if (r.nextInt(3) == 0) pool.add(ReflectionAI::page); }
        int took = pool.get(r.nextInt(pool.size())).applyAsInt(b, t);
        b.busy = took + (b.phase() == 1 ? 24 : 16);
    }

    static int volley(Reflection b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_CAST);
        b.getNavigation().stop();
        int T = 14;
        charge(b, b.pal, T);
        Vec3 d = dirTo(b, t);
        face(b, d);
        Vec3 from = b.position().add(0, 1.2, 0);
        for (int i = -1; i <= 1; i++) {
            Vec3 di = rotY(d, i * 0.3);
            Vec3 to = from.add(di.scale(20));
            warnLine(sl, from.add(0, -1.1, 0), to.add(0, -1.1, 0), 1.4f, T);
            AstaFx.later(T, () -> {
                if (!ok(b)) return;
                for (int k = 0; k < 5; k++) BossFx.orb(sl, b.pal, from.add(di.scale(k * 0.8)), di.scale(1.6), 0.3f, 12);
                BossFx.trail(sl, b.pal, from, to, 0.25f, 8);
                for (Player p : along(b, from, to, 1.3)) hurt(b, p, 7f);
            });
        }
        AstaFx.later(T, () -> sl.playSound(null, b.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.3f, 1.4f));
        return T + 6;
    }

    static int cut(Reflection b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_SLASH);
        b.getNavigation().stop();
        int T = 16;
        charge(b, b.pal, T);
        Vec3 d = dirTo(b, t);
        face(b, d);
        Vec3 a = b.position().add(0, 1, 0), c = a.add(d.scale(20));
        warnLine(sl, a.add(0, -0.9, 0), c.add(0, -0.9, 0), 2.2f, T);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            for (int k = 0; k < 4; k++) BossFx.slash(sl, b.pal, a.add(d.scale(20 * (k + 0.5) / 4)), d, new Vec3(0, 1, 0), 5f, 1.2f, 8, 1.6f, 1.0f);
            BossFx.trail(sl, Fx.SILVER, a, c, 0.15f, 8);
            for (Player p : along(b, a, c, 1.8)) hurt(b, p, 12f);
            FxSend.shake(sl, a, 0.4f, 6, 18);
            sl.playSound(null, b.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5f, 0.6f);
        });
        return T + 8;
    }

    static int starfall(Reflection b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int T = 18;
        charge(b, b.pal, 10);
        for (int i = 0; i < 6; i++) {
            Vec3 g = ground(sl, t.position().add(i == 0 ? 0 : r.nextDouble(-5, 5), 0, i == 0 ? 0 : r.nextDouble(-5, 5)));
            int tt = T + i * 3;
            warnCircle(sl, g, 1.8f, tt);
            AstaFx.later(tt, () -> {
                if (!ok(b)) return;
                BossFx.beam(sl, b.pal, g.add(0, 16, 0), g, 0.9f, 8);
                BossFx.ring(sl, b.pal, g.add(0, 0.05, 0), 0.3f, 2.2f, 0.08f, 8);
                for (Player p : near(b, g.add(0, 1, 0), 1.9)) hurt(b, p, 8f);
                sl.playSound(null, g.x, g.y, g.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.6f, 1.6f);
            });
        }
        return T + 20;
    }

    static int step(Reflection b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_DASH);
        b.getNavigation().stop();
        int T = 8;
        warnCircle(sl, t.position(), 2.2f, T);
        Vec3 from = b.position();
        AstaFx.later(T, () -> {
            if (!ok(b) || !t.isAlive()) return;
            Vec3 behind = t.position().add(flat(t.getLookAngle()).normalize().scale(-1.6));
            Vec3 to = SpaceFx.safe(sl, behind, b);
            if (to == null) return;
            BossFx.trail(sl, b.pal, from.add(0, 1, 0), to.add(0, 1, 0), 0.35f, 8);
            Vec3 face = flat(t.position().subtract(to));
            SpaceFx.move(b, to, (float) Math.toDegrees(Math.atan2(-face.x, face.z)));
            b.setPose(P_SLASH);
            BossFx.slash(sl, b.pal, t.position().add(0, 1, 0), face.lengthSqr() > 1e-4 ? face.normalize() : new Vec3(1, 0, 0), new Vec3(0, 1, 0), 2.2f, 0.5f, 6, 1.4f, 0.8f);
            for (Player p : near(b, t.position().add(0, 1, 0), 2.2)) hurt(b, p, 8f);
            sl.playSound(null, to.x, to.y, to.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.2f, 1.3f);
        });
        return T + 6;
    }

    static int nova(Reflection b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_ROAR);
        b.getNavigation().stop();
        int T = 18;
        charge(b, b.pal, T);
        Vec3 c = b.position();
        warnCircle(sl, c, 8f, T);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            BossFx.wall(sl, b.pal, c.add(0, 0.6, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.5f, 8f, -180, 180, 0.6f, 10, 4, 0);
            BossFx.ring(sl, Fx.SILVER, c.add(0, 0.1, 0), 0.5f, 8.5f, 0.12f, 10);
            for (Player p : near(b, c.add(0, 1, 0), 8)) { hurt(b, p, 11f); Vec3 away = flat(p.position().subtract(c)); if (away.lengthSqr() > 1e-4) { p.setDeltaMovement(away.normalize().scale(1.0).add(0, 0.4, 0)); p.hurtMarked = true; } }
            FxSend.shake(sl, c, 0.6f, 10, 24);
            sl.playSound(null, b.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.1f, 1.1f);
        });
        return T + 8;
    }

    /** Its Awakened Page: a great circle where you stand, ringed with pillars of your magic, closing - then it bursts. */
    static int page(Reflection b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        int T = 34;
        charge(b, b.pal, T);
        say(b, WHO, COL, "Your strongest page. Mine too.");
        Vec3 c = ground(sl, t.position());
        float R = 9f;
        warnCircle(sl, c, R, T);
        for (int i = 0; i < 10; i++) {
            final double a = i * Math.PI / 5;
            for (int s = 0; s < T; s += 4) {
                final float k = s / (float) T;
                AstaFx.later(s, () -> {
                    if (!ok(b)) return;
                    double rr = R * (1 - k * 0.85);
                    Vec3 at = c.add(Math.cos(a + k) * rr, 0, Math.sin(a + k) * rr);
                    BossFx.beam(sl, b.pal, at, at.add(0, 6, 0), 0.25f, 4);
                });
            }
        }
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            BossFx.beam(sl, b.pal, c, c.add(0, 30, 0), 2.2f, 14);
            BossFx.ring(sl, b.pal, c.add(0, 0.1, 0), 0.5f, R + 1, 0.3f, 14);
            BossFx.ring(sl, Fx.SILVER, c.add(0, 0.2, 0), 0.4f, R, 0.12f, 12);
            for (Player p : near(b, c.add(0, 1, 0), R)) hurt(b, p, 18f);
            FxSend.impactFrame(sl, c.add(0, 1, 0), 4, FxSend.FRAME_GOLD, 34);
            FxSend.shake(sl, c, 1.0f, 14, 32);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.4f, 0.8f);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 1.2f, 0.6f);
        });
        return T + 12;
    }

    public static void onHurt(Reflection b, DamageSource src, float amount) {
        if (src.getEntity() instanceof Player p && valid(b, p)) b.focus = p.getUUID();
    }

    public static void phase(Reflection b, int phase) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setTransforming(40);
        b.setPose(P_ROAR);
        say(b, WHO, COL, "Half? I know your limits. Do you?");
        Vec3 c = b.position();
        AstaFx.later(20, () -> {
            BossFx.ring(sl, b.pal, c.add(0, 0.1, 0), 0.5f, 10f, 0.3f, 16);
            BossFx.beam(sl, b.pal, c, c.add(0, 24, 0), 1.2f, 16);
            FxSend.impactFrame(sl, c.add(0, 1.5, 0), 4, FxSend.FRAME_GOLD, 28);
            sl.playSound(null, b.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.4f, 0.7f);
        });
    }

    public static void dying(Reflection b, int t) {
        ServerLevel sl = (ServerLevel) b.level();
        if (t == 1) say(b, WHO, COL, "...Then go further than I ever could.");
        if (t < 60 && t % 4 == 0) BossFx.orb(sl, b.pal, b.position().add(0, 0.4 + t / 30.0, 0), new Vec3(0, 0.06, 0), 0.3f, 12);
    }
}
