package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.BossKit.*;
import static com.ironspellgrimoire.server.FxUtil.*;
import static com.ironspellgrimoire.server.Rivals.*;

import com.ironspellgrimoire.entity.AlderBellmar;
import com.ironspellgrimoire.entity.Rival;
import com.ironspellgrimoire.entity.WrenBellmar;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Bellmar twins - Alder (sword, red scarf) and Wren (bells, blue scarf). Two bodies, one fight.
 * <ul>
 *   <li>Alder: Twin Cut (two-handed sweep), Lunge (a straight dash).</li>
 *   <li>Wren: Bell Shot (three ringing bolts), Peal (a bell tolls under you).</li>
 *   <li>Swap: a bell, and they trade places - Alder cuts as he lands.</li>
 *   <li>Bond Shot: Wren shoots through Alder; the bolt splits in three as it passes him.</li>
 *   <li>Grief: when one goes down, the other fights harder (+40%) - and if you can't bring the second down within
 *       30 seconds, the first gets back up.</li>
 *   <li>Two as One (last phase): a tether between them, and they circle you from either side. Don't cross it.</li>
 *   <li>Rank 1 Echo Bell (each Peal tolls twice), rank 2 Back to Back (close together they take 40% less), rank 3 Last
 *       Bell (Two as One ends with both of them cutting through you from either side).</li>
 * </ul>
 */
public final class TwinsAI {
    private TwinsAI() {}

    static final int ALDER = Fx.CRIMSON, WREN = Fx.WATER, BELL = Fx.GOLD;
    /** Wren's flag: the tether is up. */
    public static final int TETHER = 1;
    static final int GRIEF_TICKS = 600;

    public static Rival partner(Rival b) {
        if (b.partner == null || !(b.level() instanceof ServerLevel sl)) return null;
        Entity e = sl.getEntity(b.partner);
        return e instanceof Rival r && r.isAlive() ? r : null;
    }

    static boolean up(Rival r) { return r != null && r.isAlive() && !r.yielded; }
    static long griefUntil(Rival b) { return b instanceof AlderBellmar a ? a.griefUntil : b instanceof WrenBellmar w ? w.griefUntil : 0; }
    static void setGrief(Rival b, long t) { if (b instanceof AlderBellmar a) a.griefUntil = t; else if (b instanceof WrenBellmar w) w.griefUntil = t; }
    static boolean grieving(Rival b) { return griefUntil(b) > b.level().getGameTime(); }
    /** Grief makes their blows 40% harder. */
    static float mult(Rival b) { return grieving(b) ? 1.4f : 1f; }

    public static boolean bothDown(Rival b) { Rival o = partner(b); return o == null || o.yielded; }

    /** One twin is down; the other grieves. */
    public static void down(Rival b) {
        Rival o = partner(b);
        b.yieldT = 0;
        if (o == null) return;
        setGrief(o, b.level().getGameTime() + GRIEF_TICKS);
        line(o, b instanceof AlderBellmar ? "Wren|ALDER!" : "Alder|WREN!");
        snd(b, "rival_grief", 1.5f, 1f);
        o.busy = 0;
    }

    /** While one twin is down: if the other is still standing after 30 s, the first gets back up. */
    public static void downTick(Rival b) {
        Rival o = partner(b);
        if (o == null) { b.over = true; return; }
        b.yieldT++;
        if (b.yieldT % 40 == 0) BossFx.ring((ServerLevel) b.level(), BELL, b.position().add(0, 0.1, 0), 0.6f, 0.8f, 0.05f, 30);
        if (b.yieldT < GRIEF_TICKS || !up(o)) return;
        b.yielded = false;
        b.yieldT = 0;
        b.setFlag(b.flag() & ~Rival.YIELDED);
        b.setTransforming(1);
        b.setPhase(3);
        b.setHealth(b.getMaxHealth() * 0.3f);
        setGrief(o, 0);
        line(b, b instanceof AlderBellmar ? "Alder|Not yet - not while Wren's standing!" : "Wren|I'm up - I'm up!");
        snd(b, "rival_bell", 1.4f, 1.2f);
    }

    public static void tick(Rival b) {
        ServerLevel sl = (ServerLevel) b.level();
        Player t = target(b);
        if (t == null) return;
        b.getLookControl().setLookAt(t, 30, 30);
        if (grieving(b) && sl.getGameTime() % 10 == 0) BossFx.ring(sl, b instanceof AlderBellmar ? ALDER : WREN, b.position().add(0, 0.1, 0), 0.9f, 1.1f, 0.05f, 10);
        if (b instanceof WrenBellmar w && (w.flag() & TETHER) != 0) { tether(w, t); return; }
        if (b.busy > 0) { b.busy -= grieving(b) ? 2 : 1; return; }
        int took = b instanceof AlderBellmar a ? alder(a, t) : wren((WrenBellmar) b, t);
        b.busy = took + (b.phase() == 1 ? 24 : 18) - 2 * b.rank;
    }

    public static void phase(Rival b, int phase) {
        if (phase >= 3 && b instanceof WrenBellmar w) w.lastMove = 0;
    }

    public static float guard(Rival b, DamageSource src) {
        Rival o = partner(b);
        if (b.rank >= 2 && up(o) && o.distanceTo(b) < 5) return 0.6f;   // Back to Back
        return 1f;
    }

    // ------------------------------------------------------------------ Alder
    static int alder(AlderBellmar b, Player t) {
        keep(b, t, 2.5);
        double d = b.distanceTo(t);
        return d > 6 || ThreadLocalRandom.current().nextInt(4) == 0 ? lunge(b, t) : twinCut(b, t);
    }

    static int twinCut(Rival b, Player t) {
        charge(b, ALDER, 10);
        AstaFx.later(10, () -> {
            if (!ok(b) || b.yielded) return;
            b.setPose(P_SLASH);
            Vec3 d = dirTo(b, t);
            face(b, d);
            arc(b, ALDER, d, 3.6f, 120, 6f);
            snd(b, "slash", 1f, 0.9f);
        });
        return 18;
    }

    static int lunge(Rival b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        Vec3 d = dirTo(b, t);
        face(b, d);
        Vec3 a = b.position().add(0, 0.9, 0);
        Vec3 c = clip(sl, b, a, a.add(d.scale(Math.min(10, b.distanceTo(t) + 2))));
        warnLine(sl, a, c, 1.3f, 14);
        b.setPose(P_DASH);
        b.getNavigation().stop();
        AstaFx.later(14, () -> {
            if (!ok(b) || b.yielded) return;
            BossFx.trail(sl, ALDER, a, c, 0.25f, 7);
            for (Player p : along(b, a, c, 1.0)) hit(b, p, 7f);
            Vec3 end = c.subtract(d.scale(0.8));
            b.teleportTo(end.x, end.y - 0.9, end.z);
            snd(b, "rival_bell", 0.6f, 0.8f);
        });
        return 22;
    }

    // ------------------------------------------------------------------ Wren
    static int wren(WrenBellmar b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        long now = sl.getGameTime();
        keep(b, t, 9);
        Rival o = partner(b);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (up(o) && b.phase() >= 3 && now - b.lastMove > 400) { b.lastMove = now; return twoAsOne(b, t); }
        if (up(o) && now - b.lastMove > 240 && r.nextInt(3) == 0) { b.lastMove = now; return swap(b, (AlderBellmar) o, t); }
        if (up(o) && r.nextInt(3) == 0) return bond(b, o, t);
        return r.nextBoolean() ? bellShot(b, t) : peal(b, t);
    }

    static int bellShot(Rival b, Player t) {
        b.setPose(P_CAST);
        b.getNavigation().stop();
        for (int i = 0; i < 3; i++) {
            AstaFx.later(6 + i * 6, () -> {
                if (!ok(b) || b.yielded || !t.isAlive()) return;
                Vec3 from = b.position().add(0, 1.4, 0);
                Vec3 d = mid(t).subtract(from);
                if (d.lengthSqr() < 1e-4) return;
                BossFx.trail((ServerLevel) b.level(), WARN, from, from.add(d.normalize().scale(3)), 0.05f, 4);
                bolt(b, WREN, from, d.normalize(), 1.1, 22, 0.7, 4f, null);
                snd(b, "rival_bell", 0.7f, 1.3f);
            });
        }
        return 24;
    }

    static int peal(Rival b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_RAISE);
        Vec3 at = t.position();
        int n = b.rank >= 1 ? 2 : 1;
        for (int i = 0; i < n; i++) {
            final int delay = i * 20;
            AstaFx.later(delay, () -> warnCircle(sl, at, 3f, 20));
            AstaFx.later(delay + 20, () -> {
                if (!b.isAlive() || b.yielded) return;
                BossFx.ring(sl, BELL, at.add(0, 0.2, 0), 0.3f, 3.2f, 0.15f, 8);
                BossFx.wall(sl, WREN, at.add(0, 1, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), 2.4f, 3f, -180, 180, 1.6f, 8, 0, 0);
                snd(sl, at, "rival_bell", 1.3f, 0.8f);
                for (Player p : near(b, at.add(0, 0.9, 0), 3)) hit(b, p, 7f);
            });
        }
        return 22 + 20 * (n - 1);
    }

    /** Swap: a bell - and they've traded places. Alder cuts as he lands. */
    static int swap(WrenBellmar w, AlderBellmar a, Player t) {
        ServerLevel sl = (ServerLevel) w.level();
        snd(w, "rival_swap", 1.4f, 1f);
        BossFx.orb(sl, BELL, w.position().add(0, 1.2, 0), Vec3.ZERO, 0.5f, 8);
        BossFx.orb(sl, BELL, a.position().add(0, 1.2, 0), Vec3.ZERO, 0.5f, 8);
        AstaFx.later(8, () -> {
            if (!up(w) || !up(a)) return;
            Vec3 pw = w.position(), pa = a.position();
            BossFx.trail(sl, BELL, pw.add(0, 1, 0), pa.add(0, 1, 0), 0.08f, 6);
            w.teleportTo(pa.x, pa.y, pa.z);
            a.teleportTo(pw.x, pw.y, pw.z);
            if (a.distanceTo(t) < 6) { a.busy = 0; a.busy = twinCut(a, t) + 6; }
        });
        return 20;
    }

    /** Bond Shot: through Alder - and out the other side as three. */
    static int bond(WrenBellmar w, Rival a, Player t) {
        ServerLevel sl = (ServerLevel) w.level();
        w.setPose(P_CAST);
        w.getNavigation().stop();
        Vec3 from = w.position().add(0, 1.3, 0), via = a.position().add(0, 1.3, 0);
        Vec3 d = via.subtract(from);
        if (d.lengthSqr() < 1) return bellShot(w, t);
        Vec3 on = mid(t).subtract(via);
        Vec3 dir = flat(on).lengthSqr() < 1e-4 ? flat(d).normalize() : flat(on).normalize();
        warnLine(sl, from, via, 0.9f, 16);
        for (int i = -1; i <= 1; i++) warnLine(sl, via, via.add(rotY(dir, Math.toRadians(i * 18)).scale(14)), 0.9f, 16 + (int) (d.length() / 1.4));
        AstaFx.later(16, () -> {
            if (!ok(w) || w.yielded) return;
            snd(w, "rival_bond", 1.3f, 1f);
            BossFx.trail(sl, WREN, from, via, 0.12f, 6);
            for (Player p : along(w, from, via, 0.7)) hit(w, p, 5f);
            AstaFx.later((int) (d.length() / 1.4), () -> {
                if (!w.isAlive() || w.yielded) return;
                BossFx.ring(sl, BELL, via, 0.2f, 1.2f, 0.1f, 6);
                for (int i = -1; i <= 1; i++) bolt(w, i == 0 ? BELL : WREN, via, rotY(dir, Math.toRadians(i * 18)), 1.3, 11, 0.7, 5f, null);
            });
        });
        return 26;
    }

    /** Two as One: a tether between them while they circle you. */
    static int twoAsOne(WrenBellmar w, Player t) {
        w.setFlag(w.flag() | TETHER);
        w.yieldT = 0;
        w.lastMove = w.level().getGameTime();
        line(w, "Wren|Two as one!");
        snd(w, "rival_bell", 1.5f, 1f);
        return 10;
    }

    static void tether(WrenBellmar w, Player t) {
        ServerLevel sl = (ServerLevel) w.level();
        long now = sl.getGameTime();
        Rival a = partner(w);
        int age = (int) (now - w.lastMove);
        if (!up(a) || age > 170) {
            w.setFlag(w.flag() & ~TETHER);
            w.busy = 20;
            if (up(a) && w.rank >= 3) lastBell(w, a, t);
            return;
        }
        double ang = age * 0.045;
        Vec3 c = t.position();
        Vec3 pw = c.add(Math.cos(ang) * 6, 0, Math.sin(ang) * 6), pa = c.add(-Math.cos(ang) * 6, 0, -Math.sin(ang) * 6);
        if (age % 5 == 0) {
            w.getNavigation().moveTo(pw.x, pw.y, pw.z, 1.3);
            a.getNavigation().moveTo(pa.x, pa.y, pa.z, 1.3);
            a.busy = Math.max(a.busy, 8);
            BossFx.trail(sl, BELL, w.position().add(0, 1.1, 0), a.position().add(0, 1.1, 0), 0.1f, 7);
        }
        if (age > 20 && age % 10 == 0)
            for (Player p : along(w, w.position().add(0, 1.0, 0), a.position().add(0, 1.0, 0), 0.6)) hit(w, p, 4f);
    }

    /** Last Bell (rank 3): both cut through where you stand, from either side. */
    static void lastBell(WrenBellmar w, Rival a, Player t) {
        ServerLevel sl = (ServerLevel) w.level();
        Vec3 c = t.position().add(0, 0.9, 0);
        for (Rival r : new Rival[]{w, a}) {
            Vec3 from = r.position().add(0, 0.9, 0);
            Vec3 to = c.add(c.subtract(from).normalize().scale(3));
            warnLine(sl, from, to, 1.3f, 14);
            AstaFx.later(14, () -> {
                if (!r.isAlive() || r.yielded) return;
                BossFx.trail(sl, r == w ? WREN : ALDER, from, to, 0.25f, 7);
                for (Player p : along(r, from, to, 1.0)) hit(r, p, 8f);
                r.teleportTo(to.x, to.y - 0.9, to.z);
            });
        }
        AstaFx.later(14, () -> snd(w, "rival_bell", 1.6f, 0.6f));
    }
}
