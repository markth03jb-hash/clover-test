package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.BossKit.*;
import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.entity.MemoryGuardian;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntBiFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Memory Guardians' fights - ink, pages and bindings, in each memory's own colours. Every move is shown first.
 * Nothing breaks blocks.
 * <ul>
 *   <li>All three: Page Storm (a fan of torn pages down five lines), Inkwell (ink bursting up under you, in a spread
 *       of marked circles), Binding Thread (a thread from it to you that, when it pulls tight, cuts and holds you
 *       slow for a moment).</li>
 *   <li>The Copyist's Echo: Copy - it steps to where you stood a moment ago, and cuts.</li>
 *   <li>Taddeus, Warden of the Stair: Hold the Stair (a charge down a line, everything in it struck) and the Door Slam
 *       (a shockwave round him that throws you back).</li>
 *   <li>Elowen, the First Binder: Closing Book (two walls of pages either side of you, slamming shut - step out the ends),
 *       Remember (she marks where you stand; a few seconds later you are pulled back there) and, at the last, Chapter's
 *       End: a great circle of pillars round her with one gap - find it.</li>
 * </ul>
 * The second phase of each adds its stronger moves; Elowen has three.
 */
public final class MemoryGuardianAI {
    private MemoryGuardianAI() {}

    private static final UUID HELD = UUID.fromString("e1a0b0d0-0000-4000-8000-0000000000c1");
    private static final ChatFormatting[] COL = {ChatFormatting.GRAY, ChatFormatting.AQUA, ChatFormatting.GOLD};
    private static final String[] WHO = {"The Copyist's Echo", "Taddeus", "Elowen"};

    public static int palette(int ch) { return ch == 0 ? Fx.YAMI : ch == 1 ? Fx.STEEL : Fx.GOLD; }
    static int accent(int ch) { return ch == 0 ? Fx.SILVER : ch == 1 ? Fx.GOLD : 31; }
    private static float dmg(MemoryGuardian b, float d) { return d * (1f + 0.22f * b.chapter()); }
    private static void line(MemoryGuardian b, String s) { say(b, WHO[b.chapter()], COL[b.chapter()], s); }

    public static void tick(MemoryGuardian b) {
        ServerLevel sl = (ServerLevel) b.level();
        // a memory with no-one to remember it fades (it gathers again when its mage returns)
        Player own = b.owner() == null ? null : sl.getPlayerByUUID(b.owner());
        if (own == null || own.distanceToSqr(b) > 72 * 72) { if (++b.away > 600) { b.discard(); return; } } else b.away = 0;
        Player t = target(b);
        if (t == null) { b.getNavigation().stop(); return; }
        int ch = b.chapter(), pal = palette(ch);
        if (b.tickCount == 20) line(b, switch (ch) {
            case 0 -> "She wrote me to keep the books. You want her pages? Read them off me.";
            case 1 -> "None pass this stair by force. Show me you are not here to take.";
            default -> "So many pages, gathered again. Let me see the one who gathered them.";
        });
        b.getLookControl().setLookAt(t, 30, 30);
        if (b.tickCount % 12 == 0) BossFx.ring(sl, pal, b.position().add(0, 0.05, 0), 0.9f, 1.0f, 0.04f, 12);   // ink pooling round its feet
        if (b.busy > 0) { b.busy--; return; }
        keep(b, t, ch == 1 ? 4 : 8);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<ToIntBiFunction<MemoryGuardian, Player>> pool = new ArrayList<>();
        pool.add(MemoryGuardianAI::pageStorm);
        pool.add(MemoryGuardianAI::inkwell);
        pool.add(MemoryGuardianAI::thread);
        int ph = b.phase();
        switch (ch) {
            case 0 -> { if (ph >= 2 || r.nextInt(3) == 0) pool.add(MemoryGuardianAI::copy); }
            case 1 -> { pool.add(MemoryGuardianAI::charge); if (ph >= 2) { pool.add(MemoryGuardianAI::doorSlam); pool.add(MemoryGuardianAI::charge); } }
            default -> {
                pool.add(MemoryGuardianAI::closingBook);
                if (ph >= 2) pool.add(MemoryGuardianAI::remember);
                if (ph >= 3 && r.nextInt(3) == 0) pool.add(MemoryGuardianAI::chaptersEnd);
            }
        }
        int took = pool.get(r.nextInt(pool.size())).applyAsInt(b, t);
        b.busy = took + (ph == 1 ? 26 : ph == 2 ? 18 : 14);
    }

    /** Page Storm: torn pages down five lines in a fan. */
    static int pageStorm(MemoryGuardian b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        int pal = palette(b.chapter());
        b.setPose(P_CAST);
        b.getNavigation().stop();
        int T = 16;
        BossKit.charge(b, pal, T);
        Vec3 d = dirTo(b, t);
        face(b, d);
        Vec3 from = b.position().add(0, 1.3, 0);
        for (int i = -2; i <= 2; i++) {
            Vec3 di = rotY(d, i * 0.22);
            Vec3 to = from.add(di.scale(18));
            warnLine(sl, from.add(0, -1.2, 0), to.add(0, -1.2, 0), 1.2f, T);
            AstaFx.later(T + Math.abs(i) * 2, () -> {
                if (!ok(b)) return;
                for (int k = 0; k < 6; k++) BossFx.slash(sl, Fx.SILVER, from.add(di.scale(k * 3 + 1)), di, new Vec3(0, 1, 0), 0.5f, 0.6f, 8, 1.4f, 0.5f);
                BossFx.trail(sl, pal, from, to, 0.2f, 8);
                for (Player p : along(b, from, to, 1.1)) hurt(b, p, dmg(b, 6f));
            });
        }
        AstaFx.later(T, () -> sl.playSound(null, b.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.HOSTILE, 1.6f, 0.6f));
        return T + 10;
    }

    /** Inkwell: ink bursting up out of marked circles round you. */
    static int inkwell(MemoryGuardian b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        int pal = palette(b.chapter()), acc = accent(b.chapter());
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int T = 18, n = 5 + b.chapter() * 2;
        BossKit.charge(b, pal, 10);
        for (int i = 0; i < n; i++) {
            Vec3 g = ground(sl, t.position().add(i == 0 ? 0 : r.nextDouble(-6, 6), 0, i == 0 ? 0 : r.nextDouble(-6, 6)));
            int tt = T + i * 3;
            warnCircle(sl, g, 1.9f, tt);
            AstaFx.later(tt, () -> {
                if (!ok(b)) return;
                BossFx.beam(sl, pal, g, g.add(0, 7, 0), 0.8f, 10);
                BossFx.ring(sl, acc, g.add(0, 0.05, 0), 0.3f, 2.2f, 0.06f, 10);
                for (Player p : near(b, g.add(0, 1, 0), 2.0)) hurt(b, p, dmg(b, 7f));
                sl.playSound(null, g.x, g.y, g.z, SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 0.8f, 0.6f);
            });
        }
        return T + n * 3 + 6;
    }

    /** Binding Thread: a thread runs from it to you; when it pulls tight it cuts, and holds you slow. */
    static int thread(MemoryGuardian b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        int acc = accent(b.chapter());
        b.setPose(P_CAST);
        b.getNavigation().stop();
        int T = 18;
        BossKit.charge(b, acc, T);
        Vec3 a = b.position().add(0, 1.2, 0), dir = dirTo(b, t);
        Vec3 c = a.add(dir.scale(Math.min(22, b.distanceTo(t) + 6)));
        face(b, dir);
        warnLine(sl, a.add(0, -1.1, 0), c.add(0, -1.1, 0), 1.6f, T);
        for (int s = 0; s < T; s += 3) AstaFx.later(s, () -> { if (ok(b)) BossFx.trail(sl, acc, a, c, 0.04f, 3); });
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            BossFx.beam(sl, acc, a, c, 0.18f, 10);
            BossFx.trail(sl, palette(b.chapter()), a, c, 0.3f, 8);
            for (Player p : along(b, a, c, 1.5)) {
                hurt(b, p, dmg(b, 8f));
                TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, HELD, "Binding Thread", -0.45, AttributeModifier.Operation.MULTIPLY_TOTAL, 50);
                BossFx.ring(sl, acc, p.position().add(0, 1, 0), 0.7f, 0.8f, 0.05f, 40);
            }
            sl.playSound(null, b.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.4f, 0.6f);
        });
        return T + 8;
    }

    /** The Copyist's Echo - Copy: it steps to where you stood a moment ago, and cuts there. */
    static int copy(MemoryGuardian b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        int pal = palette(0);
        b.setPose(P_DASH);
        b.getNavigation().stop();
        Vec3 mark = t.position();
        int T = 12;
        warnCircle(sl, mark, 2.6f, T);
        Vec3 from = b.position();
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            Vec3 to = SpaceFx.safe(sl, mark, b);
            if (to == null) return;
            BossFx.trail(sl, pal, from.add(0, 1, 0), to.add(0, 1, 0), 0.35f, 8);
            SpaceFx.move(b, to, b.getYRot());
            b.setPose(P_SLASH);
            for (int k = 0; k < 3; k++) {
                Vec3 d = rotY(new Vec3(1, 0, 0), k * Math.PI / 3);
                BossFx.slash(sl, Fx.SILVER, to.add(0, 1, 0), d, new Vec3(0, 1, 0), 2.4f, 0.5f, 6, 1.4f, 0.8f);
            }
            for (Player p : near(b, to.add(0, 1, 0), 2.6)) hurt(b, p, dmg(b, 9f));
            sl.playSound(null, to.x, to.y, to.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.3f, 1.2f);
        });
        return T + 8;
    }

    /** Taddeus - Hold the Stair: he charges down a line, and everything in it is struck. */
    static int charge(MemoryGuardian b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_DASH);
        b.getNavigation().stop();
        int T = 16;
        BossKit.charge(b, Fx.STEEL, T);
        Vec3 d = dirTo(b, t);
        face(b, d);
        Vec3 a = b.position(), c = ground(sl, a.add(d.scale(14)).add(0, 2, 0));
        warnLine(sl, a.add(0, 0.1, 0), c.add(0, 0.1, 0), 2.6f, T);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            Vec3 to = SpaceFx.safe(sl, c, b);
            if (to == null) to = b.position();
            BossFx.trail(sl, Fx.STEEL, a.add(0, 1, 0), to.add(0, 1, 0), 0.9f, 10);
            BossFx.trail(sl, Fx.GOLD, a.add(0, 1, 0), to.add(0, 1, 0), 0.3f, 10);
            for (Player p : along(b, a.add(0, 1, 0), to.add(0, 1, 0), 1.8)) {
                hurt(b, p, dmg(b, 10f));
                p.setDeltaMovement(d.scale(0.9).add(0, 0.35, 0)); p.hurtMarked = true;
            }
            SpaceFx.move(b, to, b.getYRot());
            FxSend.shake(sl, to, 0.5f, 8, 20);
            sl.playSound(null, to.x, to.y, to.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1.5f, 0.7f);
        });
        return T + 10;
    }

    /** Taddeus - the Door Slam: a shockwave round him that throws you back. */
    static int doorSlam(MemoryGuardian b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_ROAR);
        b.getNavigation().stop();
        int T = 18;
        BossKit.charge(b, Fx.GOLD, T);
        Vec3 c = b.position();
        warnCircle(sl, c, 7f, T);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            BossFx.wall(sl, Fx.STEEL, c.add(0, 0.6, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.5f, 7f, -180, 180, 1.4f, 10, 4, 0);
            BossFx.ring(sl, Fx.GOLD, c.add(0, 0.1, 0), 0.5f, 7.5f, 0.12f, 10);
            for (Player p : near(b, c.add(0, 1, 0), 7)) {
                hurt(b, p, dmg(b, 10f));
                Vec3 away = flat(p.position().subtract(c));
                if (away.lengthSqr() > 1e-4) { p.setDeltaMovement(away.normalize().scale(1.3).add(0, 0.45, 0)); p.hurtMarked = true; }
            }
            FxSend.shake(sl, c, 0.7f, 10, 24);
            sl.playSound(null, b.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0f, 0.5f);
        });
        return T + 8;
    }

    /** Elowen - Closing Book: two walls of pages either side of you slam shut. Step out of the ends. */
    static int closingBook(MemoryGuardian b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        int T = 24;
        BossKit.charge(b, Fx.GOLD, T);
        Vec3 c = ground(sl, t.position()), ax = dirTo(b, t), side = new Vec3(-ax.z, 0, ax.x);
        float W = 5f, L = 6f;
        for (int s = -1; s <= 1; s += 2) warnLine(sl, c.add(side.scale(s * W)).add(ax.scale(-L)), c.add(side.scale(s * W)).add(ax.scale(L)), 1.2f, T);
        warnLine(sl, c.add(ax.scale(-L)), c.add(ax.scale(L)), W * 2, T);
        for (int k = 0; k <= 4; k++) {
            final float off = W * (1 - k / 4f);
            AstaFx.later(T - 6 + k * 2, () -> {
                if (!ok(b)) return;
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 a = c.add(side.scale(s * off)).add(0, 0.2, 0);
                    BossFx.beam(sl, Fx.GOLD, a.add(ax.scale(-L)), a.add(ax.scale(L)), 0.3f, 4);
                    BossFx.beam(sl, 31, a.add(ax.scale(-L)).add(0, 2.4, 0), a.add(ax.scale(L)).add(0, 2.4, 0), 0.2f, 4);
                }
            });
        }
        AstaFx.later(T + 2, () -> {
            if (!ok(b)) return;
            BossFx.ring(sl, Fx.GOLD, c.add(0, 1, 0), 0.4f, 3f, 0.08f, 10);
            for (Player p : b.level().getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(c, c).inflate(W + L))) {
                if (!valid(b, p)) continue;
                Vec3 o = p.position().subtract(c);
                if (Math.abs(o.dot(side)) < W && Math.abs(o.dot(ax)) < L && Math.abs(o.y) < 3) hurt(b, p, dmg(b, 13f));
            }
            FxSend.shake(sl, c, 0.6f, 8, 20);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.BOOK_PUT, SoundSource.HOSTILE, 2.0f, 0.5f);
        });
        return T + 10;
    }

    /** Elowen - Remember: she marks where you stand; a few seconds later you're pulled back there. */
    static int remember(MemoryGuardian b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_CAST);
        b.getNavigation().stop();
        Vec3 mark = t.position();
        int T = 60;
        BossKit.charge(b, 31, 12);
        line(b, "Remember where you stood.");
        for (int s = 0; s < T; s += 6) AstaFx.later(s, () -> { if (ok(b)) BossFx.ring(sl, 31, mark.add(0, 0.05, 0), 0.8f, 1.0f, 0.06f, 6); });
        AstaFx.later(T, () -> {
            if (!ok(b) || !t.isAlive() || t.level() != b.level() || t.distanceToSqr(mark) > 40 * 40) return;
            BossFx.trail(sl, Fx.GOLD, t.position().add(0, 1, 0), mark.add(0, 1, 0), 0.3f, 10);
            t.teleportTo(mark.x, mark.y, mark.z);
            hurt(b, t, dmg(b, 4f));
            BossFx.ring(sl, Fx.GOLD, mark.add(0, 0.1, 0), 0.3f, 2.4f, 0.08f, 12);
            sl.playSound(null, mark.x, mark.y, mark.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1f, 0.6f);
        });
        return 20;
    }

    /** Elowen - Chapter's End: a great circle of pillars round her, closing, with one gap. Find it. */
    static int chaptersEnd(MemoryGuardian b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        int T = 40;
        BossKit.charge(b, Fx.GOLD, T);
        line(b, "Every story ends. Find the page that doesn't.");
        Vec3 c = b.position();
        float R = 12f;
        double gap = ThreadLocalRandom.current().nextDouble(Math.PI * 2), half = 0.42;
        warnCircle(sl, c, R, T);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            if (Math.abs(Math.atan2(Math.sin(a - gap), Math.cos(a - gap))) < half) continue;
            for (int s = 0; s < T; s += 5) {
                final float k = s / (float) T;
                AstaFx.later(s, () -> {
                    if (!ok(b)) return;
                    Vec3 at = c.add(Math.cos(a) * R * (1 - k * 0.3), 0, Math.sin(a) * R * (1 - k * 0.3));
                    BossFx.beam(sl, Fx.GOLD, at, at.add(0, 5, 0), 0.25f, 5);
                });
            }
        }
        Vec3 g = c.add(Math.cos(gap) * R, 0, Math.sin(gap) * R);   // the gap, lit white
        for (int s = 0; s < T; s += 5) AstaFx.later(s, () -> { if (ok(b)) BossFx.ring(sl, Fx.SILVER, g.add(0, 0.05, 0), 1.6f, 1.9f, 0.08f, 5); });
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            BossFx.ring(sl, Fx.GOLD, c.add(0, 0.1, 0), 0.5f, R + 1, 0.3f, 14);
            BossFx.beam(sl, Fx.GOLD, c, c.add(0, 28, 0), 1.6f, 14);
            for (Player p : near(b, c.add(0, 1, 0), R + 2)) {
                Vec3 o = flat(p.position().subtract(c));
                double a = Math.atan2(o.z, o.x);
                boolean safe = o.length() > R * 0.55 && Math.abs(Math.atan2(Math.sin(a - gap), Math.cos(a - gap))) < half;
                if (!safe) hurt(b, p, dmg(b, 16f));
            }
            FxSend.impactFrame(sl, c.add(0, 1, 0), 4, FxSend.FRAME_GOLD, 30);
            FxSend.shake(sl, c, 1.0f, 14, 32);
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.4f, 0.7f);
        });
        return T + 14;
    }

    public static void onHurt(MemoryGuardian b, DamageSource src, float amount) {
        if (src.getEntity() instanceof Player p && valid(b, p)) b.focus = p.getUUID();
    }

    public static void phase(MemoryGuardian b, int phase) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setTransforming(40);
        b.setPose(P_ROAR);
        int ch = b.chapter();
        line(b, ch == 0 ? "You're tearing me. That's how she made me - from torn pages." : ch == 1 ? "A night and a day. I can hold longer than this."
                : phase == 2 ? "You remember more of me than I do." : "Then let me write the ending.");
        Vec3 c = b.position();
        AstaFx.later(20, () -> {
            BossFx.ring(sl, palette(ch), c.add(0, 0.1, 0), 0.5f, 10f, 0.3f, 16);
            BossFx.beam(sl, accent(ch), c, c.add(0, 24, 0), 1.2f, 16);
            FxSend.impactFrame(sl, c.add(0, 1.5, 0), 4, FxSend.FRAME_GOLD, 28);
            sl.playSound(null, b.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.HOSTILE, 2f, 0.4f);
        });
    }

    public static void dying(MemoryGuardian b, int t) {
        ServerLevel sl = (ServerLevel) b.level();
        int ch = b.chapter();
        if (t == 1) line(b, ch == 0 ? "...Keep the books. Better than I did." : ch == 1 ? "Go on up. Mind the steps - my name is on them." : "...Thank you for remembering.");
        if (t < 60 && t % 3 == 0) BossFx.slash(sl, Fx.SILVER, b.position().add(0, 0.5 + t / 25.0, 0), rotY(new Vec3(1, 0, 0), t * 0.7), new Vec3(0, 1, 0), 0.4f, 0.5f, 10, 0.6f, 0.4f);
    }
}
