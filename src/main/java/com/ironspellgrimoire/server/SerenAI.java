package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.BossKit.*;
import static com.ironspellgrimoire.server.FxUtil.*;
import static com.ironspellgrimoire.server.Rivals.*;

import com.ironspellgrimoire.entity.SerenVale;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Seren Vale, the Glass Duelist - light and glass, close and fast.
 * <ul>
 *   <li>Sidestep: the first blow every few seconds never lands - she steps through the light beside it.</li>
 *   <li>Glass Cut: two quick cuts in front of her.</li>
 *   <li>Refraction Step: she steps behind you and cuts; the afterimage she leaves shatters a moment later.</li>
 *   <li>Spectrum Blades: a fan of light blades, one of each colour (3, then 5, then 7).</li>
 *   <li>Mirror Parry: her blade glows - hit her now and she turns it back on you. Wait it out.</li>
 *   <li>White Noon (last phase): the ring fills with light; only the shade of her three prisms is safe.</li>
 *   <li>Rank 1 Prism Cage (blades close in round you - one gap), rank 2 Afterimage (the afterimage fires blades
 *       too), rank 3 Noon's Edge (she cuts through the ring during White Noon).</li>
 * </ul>
 */
public final class SerenAI {
    private SerenAI() {}

    public static final int PARRY = 1, NOON = 2;
    static final int GLASS = Fx.MIRROR, LIGHT = Fx.SILVER;
    static final int[] SPECTRUM = {Fx.CRIMSON, Fx.FIRE, Fx.GOLD, Fx.TREE, Fx.WATER, Fx.ICE, Fx.VOID};

    public static void tick(SerenVale b) {
        ServerLevel sl = (ServerLevel) b.level();
        long now = sl.getGameTime();
        Player t = target(b);
        if (t == null) return;
        b.getLookControl().setLookAt(t, 30, 30);
        if (b.parryUntil > now) {
            b.getNavigation().stop();
            face(b, dirTo(b, t));
            if (now % 4 == 0) BossFx.ring(sl, GLASS, b.position().add(0, 1.1, 0), 0.9f, 1.05f, 0.05f, 5);
            return;
        }
        if ((b.flag() & PARRY) != 0) { b.setFlag(b.flag() & ~PARRY); b.setPose(P_IDLE); }
        if (b.phase() >= 3 && now >= b.noonAt && b.busy <= 0) { b.busy = whiteNoon(b, t); return; }
        if (b.busy > 0) { b.busy--; return; }
        keep(b, t, 4);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double d = b.distanceTo(t);
        int took;
        if (b.rank >= 1 && r.nextInt(6) == 0) took = prismCage(b, t);
        else if (d > 7) took = r.nextBoolean() ? step(b, t) : blades(b, t);
        else {
            boolean pressed = now - b.lastHurt < 40;
            took = switch (r.nextInt(pressed ? 6 : 5)) {
                case 0, 1 -> cut(b, t);
                case 2 -> step(b, t);
                case 3 -> blades(b, t);
                default -> parry(b);
            };
        }
        b.busy = took + (b.phase() == 1 ? 22 : b.phase() == 2 ? 16 : 12) - 2 * b.rank;
    }

    public static void phase(SerenVale b, int phase) {
        if (phase >= 3) b.noonAt = b.level().getGameTime() + 40;
    }

    /** Her guards: Mirror Parry turns a blow back; otherwise the first blow every few seconds is sidestepped. */
    public static float guard(SerenVale b, DamageSource src) {
        long now = b.level().getGameTime();
        if (!(src.getEntity() instanceof LivingEntity a) || a == b) return 1f;
        if (b.parryUntil > now) {
            b.parryUntil = 0;
            counter(b, a);
            return 0f;
        }
        if (now >= b.dodgeAt && (b.flag() & NOON) == 0) {
            b.dodgeAt = now + 160 - 30L * (b.phase() - 1) - 10L * b.rank;
            Vec3 away = flat(b.position().subtract(a.position()));
            if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
            Vec3 side = rightOf(away.normalize()).scale(ThreadLocalRandom.current().nextBoolean() ? 3 : -3);
            blink(b, b.position().add(side));
            snd(b, "rival_glass_step", 1f, 1.2f);
            if (a instanceof Player p) p.displayClientMessage(Component.literal("Seren steps through the light - missed.").withStyle(ChatFormatting.AQUA), true);
            return 0f;
        }
        return 1f;
    }

    // ------------------------------------------------------------------ moves
    /** Glass Cut: two quick cuts in front of her. */
    static int cut(SerenVale b, Player t) {
        charge(b, GLASS, 10);
        for (int i = 0; i < 2; i++) {
            final int k = i;
            AstaFx.later(10 + i * 10, () -> {
                if (!ok(b) || b.yielded) return;
                b.setPose(P_SLASH);
                Vec3 d = dirTo(b, t);
                face(b, d);
                arc(b, k == 0 ? GLASS : LIGHT, d, 3.6f, 130, k == 0 ? 6f : 5f);
                snd(b, "rival_parry", 0.6f, 1.6f + 0.2f * k);
            });
        }
        return 26;
    }

    /** Refraction Step: behind you, a cut; her afterimage shatters where she stood. */
    static int step(SerenVale b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        Vec3 old = b.position();
        Vec3 back = flat(t.getLookAngle());
        if (back.lengthSqr() < 1e-4) back = dirTo(b, t);
        Vec3 dest = t.position().subtract(back.normalize().scale(2.6)).add(rightOf(back.normalize()).scale(ThreadLocalRandom.current().nextDouble(-1.5, 1.5)));
        BossFx.orb(sl, GLASS, dest.add(0, 1, 0), Vec3.ZERO, 0.45f, 8);
        warnCircle(sl, old, 2.5f, 22);
        AstaFx.later(8, () -> {
            if (!ok(b) || b.yielded) return;
            blink(b, dest);
            face(b, dirTo(b, t));
            snd(b, "rival_glass_step", 1.2f, 1f);
            if (b.rank >= 2) afterimageFan(b, old, t);
        });
        AstaFx.later(22, () -> {   // the afterimage shatters
            if (!b.isAlive() || b.yielded) return;
            BossFx.ring(sl, GLASS, old.add(0, 0.2, 0), 0.3f, 2.5f, 0.12f, 8);
            for (int i = 0; i < 6; i++) BossFx.orb(sl, LIGHT, old.add(0, 1, 0), rotY(new Vec3(0.35, 0.15, 0), i * Math.PI / 3), 0.18f, 6);
            snd(sl, old, "rival_glass_step", 1f, 0.7f);
            for (Player p : near(b, old.add(0, 0.9, 0), 2.5)) hit(b, p, 6f);
        });
        AstaFx.later(8, () -> charge(b, GLASS, 10));
        AstaFx.later(18, () -> {
            if (!ok(b) || b.yielded) return;
            b.setPose(P_SLASH);
            Vec3 d = dirTo(b, t);
            face(b, d);
            arc(b, GLASS, d, 3.3f, 120, 6f);
        });
        return 30;
    }

    static void afterimageFan(SerenVale b, Vec3 from, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        Vec3 d0 = flat(t.position().subtract(from));
        if (d0.lengthSqr() < 1e-4) return;
        Vec3 a = from.add(0, 1.2, 0);
        for (int i = -1; i <= 1; i++) {
            Vec3 d = rotY(d0.normalize(), Math.toRadians(i * 16));
            warnLine(sl, a, a.add(d.scale(16)), 0.9f, 12);
            final int k = i;
            AstaFx.later(12, () -> bolt(b, SPECTRUM[(k + 3) % SPECTRUM.length], a, d, 1.3, 13, 0.6, 4f, null));
        }
    }

    /** Spectrum Blades: a fan of light, one blade of each colour. */
    static int blades(SerenVale b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_CAST);
        b.getNavigation().stop();
        int n = b.phase() == 1 ? 3 : b.phase() == 2 ? 5 : 7;
        Vec3 d0 = dirTo(b, t);
        face(b, d0);
        Vec3 a = b.position().add(0, 1.2, 0);
        for (int i = 0; i < n; i++) {
            Vec3 d = rotY(d0, Math.toRadians((i - (n - 1) / 2.0) * 13));
            warnLine(sl, a, a.add(d.scale(18)), 0.9f, 14);
            final int k = i;
            AstaFx.later(14, () -> {
                if (!ok(b) || b.yielded) return;
                bolt(b, SPECTRUM[k % SPECTRUM.length], b.position().add(0, 1.2, 0), d, 1.3, 15, 0.6, 5f, null);
            });
        }
        AstaFx.later(14, () -> snd(b, "rival_blade_fan", 1.2f, 1f));
        return 24;
    }

    /** Mirror Parry: her blade raised and glowing - strike it and she strikes back. */
    static int parry(SerenVale b) {
        b.parryUntil = b.level().getGameTime() + 40;
        b.setFlag(b.flag() | PARRY);
        b.setPose(P_CAST);
        snd(b, "rival_parry", 0.7f, 2f);
        return 42;
    }

    static void counter(SerenVale b, LivingEntity a) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setFlag(b.flag() & ~PARRY);
        snd(b, "rival_parry", 1.4f, 1f);
        BossFx.ring(sl, GLASS, b.position().add(0, 1.1, 0), 0.4f, 2.2f, 0.1f, 8);
        if (a instanceof Player p) p.displayClientMessage(Component.literal("Parried!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
        AstaFx.later(6, () -> {
            if (!ok(b) || b.yielded || !a.isAlive() || !inRing(b, a)) return;
            Vec3 d = flat(a.position().subtract(b.position()));
            if (d.lengthSqr() > 9) blink(b, a.position().subtract(d.normalize().scale(1.8)));
            Vec3 dir = dirTo(b, a instanceof Player p ? p : target(b));
            face(b, dir);
            b.setPose(P_SLASH);
            arc(b, LIGHT, dir, 3.4f, 140, 7f);
        });
        b.busy = Math.max(b.busy, 16);
    }

    /** White Noon: light fills the ring; the shade of her three prisms is the only safe ground. */
    static int whiteNoon(SerenVale b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        long now = sl.getGameTime();
        b.noonAt = now + 520 - 40L * b.rank;
        b.noons++;
        b.setFlag(b.flag() | NOON);
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        snd(b, "rival_noon", 1.6f, 1f);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Vec3> shade = new ArrayList<>();
        for (int tries = 0; tries < 40 && shade.size() < 3; tries++) {
            double ang = r.nextDouble(Math.PI * 2), dist = r.nextDouble(4, 11);
            Vec3 p = ground(sl, b.arena.add(Math.cos(ang) * dist, 2, Math.sin(ang) * dist));
            boolean ok = true;
            for (Vec3 q : shade) if (flatDist(p, q) < 6) ok = false;
            if (ok) shade.add(p);
        }
        final int T = 56;
        for (Vec3 p : shade) {
            BossFx.beam(sl, GLASS, p, p.add(0, 3.2, 0), 0.32f, T);
            BossFx.ring(sl, Fx.WATER, p.add(0, 0.07, 0), 2.25f, 2.45f, 0.08f, T);
            BossFx.ring(sl, Fx.YAMI, p.add(0, 0.06, 0), 0.1f, 2.4f, 0.02f, T);
        }
        Vec3 g = ground(sl, b.arena.add(0, 1, 0));
        BossFx.ring(sl, WARN, g.add(0, 0.05, 0), RING - 0.3f, RING, 0.1f, T);
        for (int k = 0; k < T; k += 8) {
            final float f = k / (float) T;
            AstaFx.later(k, () -> BossFx.ring(sl, LIGHT, g.add(0, 0.05, 0), RING * (1 - f) - 0.4f, RING * (1 - f), 0.04f, 9));
        }
        say(b, "Seren Vale", ChatFormatting.AQUA, "White Noon.");
        if (b.rank >= 3) for (int k : new int[]{14, 32}) AstaFx.later(k, () -> noonsEdge(b));
        AstaFx.later(T, () -> {
            b.setFlag(b.flag() & ~NOON);
            if (!b.isAlive() || b.yielded) return;
            BossFx.ring(sl, LIGHT, g.add(0, 0.3, 0), 0.3f, RING, 0.12f, 8);
            snd(b, "rival_glass_step", 1.4f, 0.5f);
            for (Player p : near(b, b.arena, RING + 1)) {
                boolean safe = false;
                for (Vec3 s : shade) if (flatDist(p.position(), s) <= 2.6) safe = true;
                if (!safe) hit(b, p, 14f);
            }
            b.setPose(P_IDLE);
        });
        return T + 10;
    }

    /** Noon's Edge (rank 3): she cuts across the ring through you while the light builds. */
    static void noonsEdge(SerenVale b) {
        if (!ok(b) || b.yielded) return;
        ServerLevel sl = (ServerLevel) b.level();
        Player t = target(b);
        if (t == null) return;
        Vec3 d = dirTo(b, t);
        Vec3 a = b.position().add(0, 0.9, 0), c = t.position().add(d.scale(3)).add(0, 0.9, 0);
        warnLine(sl, a, c, 1.4f, 12);
        AstaFx.later(12, () -> {
            if (!ok(b) || b.yielded) return;
            BossFx.trail(sl, GLASS, a, c, 0.3f, 8);
            for (Player p : along(b, a, c, 1.0)) hit(b, p, 7f);
            blink(b, c);
        });
    }

    /** Prism Cage (rank 1): blades ring you, then close in - one gap. */
    static int prismCage(SerenVale b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_CAST);
        b.getNavigation().stop();
        Vec3 c = t.position().add(0, 1, 0);
        int gap = ThreadLocalRandom.current().nextInt(8);
        for (int i = 0; i < 8; i++) {
            if (i == gap || i == (gap + 1) % 8) continue;
            double ang = i * Math.PI / 4;
            Vec3 at = c.add(Math.cos(ang) * 5, 0, Math.sin(ang) * 5);
            BossFx.orb(sl, SPECTRUM[i % SPECTRUM.length], at, Vec3.ZERO, 0.3f, 26);
            warnLine(sl, at, c, 0.8f, 26);
            Vec3 d = c.subtract(at).normalize();
            final int k = i;
            AstaFx.later(26, () -> bolt(b, SPECTRUM[k % SPECTRUM.length], at, d, 0.9, 11, 0.6, 6f, null));
        }
        AstaFx.later(26, () -> snd(sl, c, "rival_blade_fan", 1.2f, 0.8f));
        return 32;
    }
}
