package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Julius Novachrono's Time Magic - the twelve spells of the Time Grimoire. Drawn like a manga page: white pages with
 * ink edges and gold, clock faces with Roman numerals, gold clock hands. The heart of it is the page column (see
 * client/TimeFxClient): the grimoire's pages fan out into a spiralling wall round whatever time is about to stop for.
 *
 *  1 Clock Hand Bolt     - a gold clock hand flies true and slows what it strikes (3 stored s: two hands)
 *  2 Time Stop Point     - a little cage of pages snaps shut round one foe: frozen for 1.5 s (2 stored s: +1 s)
 *  3 Time Skip           - skip a few seconds: you are 8 blocks ahead, your golden silhouette left standing behind
 *  4 Time Recovery       - your body rewinds to how it was 4 s ago; your past selves flicker back along the way
 *                          (5 stored s: to your best moment of the last 6 s)
 *  5 Chrono Acceleration - 6 s of faster time: speed, attack speed, and every cooldown runs twice as fast
 *  6 Ticking Snare       - a clock trap on the ground ticks three times; whoever is still on it is frozen
 *  7 Time Theft          - gold threads pull the time out of everything ahead: slowed, and 2 stored s each
 *  8 Hourglass Barrage   - a clock opens over the target; its hands rain down one per tick (4 stored s: more)
 *  9 Rewind Ward         - for 3 s, half of every hurt is written down - and then rewound
 * 10 Echo of the Past    - a gold afterimage stays behind and recasts your last spell from there, at half power
 * 11 Chronostasis Field  - mode: a column of pages surrounds you for 15 s; inside it, foes crawl, arrows hang in
 *                          the air and your cooldowns run twice as fast
 * 12 Zenith Hour         - a vast column of pages bursts up round the field and a great clock opens in the sky:
 *                          for 4 s time stops for every foe in it. Your blows hang in the air and land all at once
 *                          when it starts again. Surpassing Limits: Eternal Sovereign.
 *
 * Passive "Stored Time": every hit you land banks a second, up to ten (the arc under your crosshair).
 * Combos: Hands of the Hour (Bolt -> Stop Point: the frozen target shatters out for bonus damage), Borrowed Time
 * (Theft -> Acceleration: the stolen seconds come off your cooldowns), Between Seconds (Skip -> Stop Point: the
 * freeze lasts twice as long).
 */
public final class TimeFx {
    private TimeFx() {}

    private static final int G = Fx.TIME, S = Fx.SILVER;
    public static final String MODE = "chronostasis_field";
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double TAU = Math.PI * 2;

    // ================================================================== where a spell is cast from (an Echo recasts from its own spot)
    record Src(Vec3 eye, Vec3 look, Vec3 feet, float yaw) {
        static Src of(ServerPlayer p) { return new Src(p.getEyePosition(), p.getLookAngle(), p.position(), p.getYRot()); }
        Vec3 hand() { return eye.add(look.scale(0.7)).add(rightOf(look).scale(0.3)).add(0, -0.3, 0); }
        Vec3 aim(ServerLevel sl, ServerPlayer p, double range) { return clip(sl, p, eye, eye.add(look.scale(range))); }
    }

    @FunctionalInterface
    interface Op { void run(ServerPlayer p, Src s, float power, boolean echo); }

    private record Last(Op op, String id, float power) {}

    private static final Map<UUID, Last> LAST = new HashMap<>();               // the last spell an Echo can replay
    private static final Map<UUID, Object[]> PREV = new HashMap<>();           // {spell id, game time}: for the combos
    private static final Map<UUID, Long> BOLTED = new HashMap<>();             // target -> when a Clock Hand hit it
    private static final Map<UUID, Integer> STOLEN = new HashMap<>();          // caster -> seconds taken by Time Theft
    private static final Map<UUID, List<Integer>> HELD = new HashMap<>();      // target -> the hands hanging in the air at it

    /** Remembers this cast for the combos; returns the spell cast just before it (within 4 s), or "". */
    private static String cast(ServerPlayer p, String id) {
        long now = p.serverLevel().getGameTime();
        Object[] prev = PREV.put(p.getUUID(), new Object[]{id, now});
        return prev != null && now - (Long) prev[1] <= ComboChain.WINDOW ? (String) prev[0] : "";
    }

    private static void remember(ServerPlayer p, String id, Op op, float power) { LAST.put(p.getUUID(), new Last(op, id, power)); }

    // ================================================================== the client shapes (TimeFxClient)
    static int column(ServerLevel sl, Vec3 c, float r, float h, int life, int open, int style, Entity follow, float flutter, float spin) {
        FxPacket p = new FxPacket();
        p.shape = 49; p.id = FxSend.nextId(); p.c = c; p.r = r; p.r2 = h; p.life = life; p.sweep = open; p.h = style; p.a0 = flutter; p.speed = spin;
        if (follow != null) p.v = new Vec3(follow.getId(), 1, 0);
        FxSend.sendNear(sl, c, p);
        return p.id;
    }

    static void freezeColumn(ServerLevel sl, int id, Vec3 near, int ticks) {
        FxPacket p = new FxPacket();
        p.shape = 50; p.id = id; p.life = ticks;
        FxSend.sendNear(sl, near, p);
    }

    static int clock(ServerLevel sl, Vec3 c, Vec3 face, Vec3 up, float r, int life, int style, float speed, int step, int marks,
                     float stopAt, float stopFor, Entity follow, double followY) {
        FxPacket p = new FxPacket();
        p.shape = 51; p.id = FxSend.nextId(); p.c = c; p.d = face; p.u = up; p.r = r; p.life = life; p.h = style; p.speed = speed;
        p.sweep = step; p.a0 = marks; p.a1 = stopAt; p.r2 = stopFor;
        if (follow != null) p.v = new Vec3(follow.getId(), 1, followY);
        FxSend.sendNear(sl, c, p);
        return p.id;
    }

    static int clock(ServerLevel sl, Vec3 c, Vec3 face, Vec3 up, float r, int life, int style, float speed) {
        return clock(sl, c, face, up, r, life, style, speed, 0, 0, 0, 0, null, 0);
    }

    static int hand(ServerLevel sl, Vec3 from, Vec3 vel, Vec3 dir, float len, int life, int style) {
        FxPacket p = new FxPacket();
        p.shape = 52; p.id = FxSend.nextId(); p.c = from; p.v = vel; p.d = dir; p.r = len; p.life = life; p.h = style;
        FxSend.sendNear(sl, from, p);
        return p.id;
    }

    static void ghost(ServerLevel sl, ServerPlayer owner, Vec3 at, float yaw, int life, int style, int pose, float alpha) {
        FxPacket p = new FxPacket();
        p.shape = 53; p.id = FxSend.nextId(); p.c = at; p.a0 = yaw; p.life = life; p.h = style; p.sweep = pose; p.a1 = alpha;
        p.v = new Vec3(owner.getId(), 0, 0);
        FxSend.sendNear(sl, at, p);
    }

    public static void frozenMark(ServerLevel sl, LivingEntity t, int ticks) {
        FxPacket p = new FxPacket();
        p.shape = 54; p.id = t.getId(); p.life = ticks;
        FxSend.sendNear(sl, t.position(), p);
    }

    /** The Ticking Snare's time bubble round one trapped target. */
    public static void bubble(ServerLevel sl, LivingEntity t, int ticks) {
        FxPacket p = new FxPacket();
        p.shape = 57; p.id = t.getId(); p.life = ticks;
        FxSend.sendNear(sl, t.position(), p);
    }

    public static void hud(ServerPlayer p, int seconds, boolean show) {
        if (p.connection == null) return;
        FxPacket pk = new FxPacket();
        pk.shape = 55; pk.life = seconds; pk.sweep = show ? 1 : 0;
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), pk);
    }

    static void screen(ServerPlayer p, int ticks, int style) {
        if (p.connection == null) return;
        FxPacket pk = new FxPacket();
        pk.shape = 56; pk.life = ticks; pk.h = style;
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), pk);
    }

    static void screenNear(ServerLevel sl, Vec3 c, double r, int ticks, int style) {
        for (ServerPlayer pl : sl.players()) if (pl.position().distanceToSqr(c) <= r * r) screen(pl, ticks, style);
    }

    // ================================================================== small helpers
    private static void hit(ServerPlayer p, LivingEntity t, float amt, String id) { AstaFx.damage(p, t, amt, id); }

    /** The foe nearest the middle of a cone from {@code s}. */
    private static LivingEntity aimed(ServerLevel sl, ServerPlayer p, Src s, double range, double deg) {
        LivingEntity best = null;
        double bd = Math.cos(Math.toRadians(deg));
        for (LivingEntity t : Kit.near(sl, p, s.eye(), range)) {
            Vec3 to = mid(t).subtract(s.eye());
            if (to.lengthSqr() < 1.0E-4) continue;
            double d = to.normalize().dot(s.look());
            if (d > bd) { bd = d; best = t; }
        }
        return best;
    }

    /** Every foe within a cone. */
    private static List<LivingEntity> cone(ServerLevel sl, ServerPlayer p, Src s, double range, double deg) {
        List<LivingEntity> out = new ArrayList<>();
        double c = Math.cos(Math.toRadians(deg));
        for (LivingEntity t : Kit.near(sl, p, s.eye(), range)) {
            Vec3 to = mid(t).subtract(s.eye());
            if (to.lengthSqr() < 1.0E-4 || to.normalize().dot(s.look()) >= c) out.add(t);
        }
        return out;
    }

    private static Vec3 groundAt(ServerLevel sl, Vec3 near) {
        Vec3 g = FxLib.ground(sl, near.add(0, 2, 0), 8);
        return g == null ? near : g;
    }

    /** The cooldowns of every spell tick down {@code ticks} more (time runs faster for you). */
    private static void hasten(ServerPlayer p, int ticks, boolean sync) {
        try {
            var cd = MagicData.getPlayerMagicData(p).getPlayerCooldowns();
            if (ticks > 0) cd.tick(ticks);
            if (sync) cd.syncToPlayer(p);
        } catch (Throwable ignored) {}
    }

    /** A clock hand flying from {@code from} at {@code vel}: stops at the first wall, strikes the first foe it meets. */
    private static void shoot(ServerPlayer p, Vec3 from, Vec3 vel, int maxTicks, double w, float len,
                              BiConsumer<LivingEntity, Vec3> onHit, Consumer<Vec3> onEnd) {
        ServerLevel sl = p.serverLevel();
        int id = hand(sl, from, vel, vel, len, maxTicks, 0);
        step(p, sl, id, from, vel, 0, maxTicks, w, onHit, onEnd);
    }

    private static void step(ServerPlayer p, ServerLevel sl, int id, Vec3 at, Vec3 vel, int t, int max, double w,
                             BiConsumer<LivingEntity, Vec3> onHit, Consumer<Vec3> onEnd) {
        AstaFx.later(1, () -> {
            Vec3 next = at.add(vel);
            Vec3 wall = clip(sl, p, at, next);
            Vec3 seg = wall.distanceToSqr(next) > 1.0E-4 ? wall : next;
            for (LivingEntity e : Kit.along(sl, p, at, seg, w)) {
                FxSend.kill(sl, id, seg);
                onHit.accept(e, mid(e));
                return;
            }
            if (seg != next || t + 1 >= max) {
                FxSend.kill(sl, id, seg);
                if (onEnd != null) onEnd.accept(seg);
                return;
            }
            step(p, sl, id, next, vel, t + 1, max, w, onHit, onEnd);
        });
    }

    // ================================================================== 1. Clock Hand Bolt
    public static void clockHandBolt(ServerPlayer p, float power) {
        cast(p, "clock_hand_bolt");
        remember(p, "clock_hand_bolt", TimeFx::bolt, power);
        bolt(p, Src.of(p), power, false);
    }

    private static void bolt(ServerPlayer p, Src s, float power, boolean echo) {
        ServerLevel sl = p.serverLevel();
        boolean two = !echo && TimeStop.spend(p, 3);
        Vec3 hand = s.hand();
        clock(sl, hand.add(s.look().scale(0.2)), s.look(), UP, 0.32f, 9, 2, 0.6f);   // a small clock spins up in the hand
        sound(sl, hand, SoundEvents.COMPARATOR_CLICK, 0.8f, 1.7f);
        AstaFx.later(6, () -> {   // the flick
            if (!alive(p)) return;
            Vec3 from = s.hand(), dir = s.look();
            int n = two ? 2 : 1;
            for (int i = 0; i < n; i++) {
                Vec3 d = n == 1 ? dir : rotY(dir, (i == 0 ? -1 : 1) * 0.07);
                shoot(p, from, d.scale(1.7), 20, 0.55, 1.15f, (t, at) -> {
                    hit(p, t, power, "clock_hand_bolt");
                    Moves.slow(t, 40, -0.35);
                    BOLTED.put(t.getUUID(), sl.getGameTime());
                    clock(sl, at, s.eye().subtract(at), UP, 0.6f, 12, 2, -0.5f);   // its time catches for a moment
                    Fx.spark(sl, G, at, 0.9f);
                    sound(sl, at, SoundEvents.AMETHYST_BLOCK_HIT, 1.0f, 1.2f);
                    sound(sl, at, SoundEvents.COMPARATOR_CLICK, 0.8f, 0.7f);
                }, end -> Fx.spark(sl, G, end, 0.6f));
            }
            sound(sl, from, SoundEvents.TRIDENT_THROW, 0.7f, 1.6f);
            sound(sl, from, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.5f);
        });
    }

    // ================================================================== 2. Time Stop Point
    public static void timeStopPoint(ServerPlayer p, float power) {
        String prev = cast(p, "time_stop_point");
        remember(p, "time_stop_point", (pl, s, pw, e) -> stopPoint(pl, s, pw, e, ""), power);
        stopPoint(p, Src.of(p), power, false, prev);
    }

    private static void stopPoint(ServerPlayer p, Src s, float power, boolean echo, String prev) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(sl, p, s, 24, 14);
        int ticks = 30;
        boolean between = "time_skip".equals(prev);
        if (between) ticks *= 2;                                              // Between Seconds
        if (!echo && TimeStop.spend(p, 2)) ticks += 20;
        if (echo) ticks = 20;
        sound(sl, s.feet(), SoundEvents.COMPARATOR_CLICK, 1.0f, 1.2f);
        if (t == null) {
            Vec3 at = s.aim(sl, p, 16);
            clock(sl, at, s.eye().subtract(at), UP, 0.9f, 14, 2, 0.4f);
            return;
        }
        final int T = ticks;
        final LivingEntity tt = t;
        final boolean shatter = "clock_hand_bolt".equals(prev) || (BOLTED.containsKey(t.getUUID()) && sl.getGameTime() - BOLTED.get(t.getUUID()) < 80);
        float R = (float) (t.getBbWidth() * 0.75 + 0.4), H = t.getBbHeight() + 0.45f;
        int cage = column(sl, t.position(), R, H, T + 14, 6, 2, t, 1.0f, 0.02f);   // the cage of pages fans shut round it
        clock(sl, mid(t), s.eye().subtract(mid(t)), UP, Math.max(0.9f, H * 0.45f), T + 12, 2, (float) (TAU / 12 / 4), 4, 0, 7, T, null, 0);
        FxSend.trail(sl, G, s.hand(), mid(t), 0.03f, 7);
        AstaFx.later(7, () -> {   // the snap: time stops
            if (!tt.isAlive()) return;
            boolean stopped = TimeStop.freeze(p, tt, T, false, shatter ? () -> shatter(p, tt, power) : null);
            freezeColumn(sl, cage, tt.position(), stopped ? T : 10);
            hit(p, tt, power * 0.5f, "time_stop_point");
            FxSend.impactFrame(sl, mid(tt), 2, FxSend.FRAME_JULIUS, 24);
            sound(sl, mid(tt), SoundEvents.BEACON_DEACTIVATE, 0.9f, 1.6f);
            sound(sl, mid(tt), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.8f);
            sound(sl, mid(tt), SoundEvents.COMPARATOR_CLICK, 1.0f, 0.5f);
        });
    }

    /** Hands of the Hour: the frozen target is torn out of the stopped moment. */
    private static void shatter(ServerPlayer p, LivingEntity t, float power) {
        if (!t.isAlive() || !alive(p)) return;
        ServerLevel sl = p.serverLevel();
        Vec3 m = mid(t);
        hit(p, t, power * 1.6f, "time_stop_point");
        for (int i = 0; i < 6; i++) {   // six clock hands burst out of it
            double a = TAU * i / 6;
            Vec3 d = new Vec3(Math.cos(a), 0.35, Math.sin(a)).normalize();
            hand(sl, m, d.scale(0.6), d, 0.9f, 6, 0);
        }
        Fx.impact(sl, G, m, 1.4f);
        FxSend.ring(sl, G, t.position().add(0, 0.05, 0), 0.3f, 2.6f, 0.06f, 8);
        FxSend.impactFrame(sl, m, 2, FxSend.FRAME_JULIUS, 24);
        sound(sl, m, SoundEvents.GLASS_BREAK, 1.0f, 0.8f);
        sound(sl, m, SoundEvents.BELL_BLOCK, 0.6f, 1.6f);
        BOLTED.remove(t.getUUID());
    }

    // ================================================================== 3. Time Skip
    public static void timeSkip(ServerPlayer p, float power) {
        cast(p, "time_skip");
        remember(p, "time_skip", TimeFx::skip, power);
        skip(p, Src.of(p), power, false);
    }

    private static void skip(ServerPlayer p, Src s, float power, boolean echo) {
        ServerLevel sl = p.serverLevel();
        Vec3 start = p.position();
        ghost(sl, p, start, p.getYRot(), 30, 0, 3, 0.65f);   // your golden silhouette, left behind mid-stride
        clock(sl, start.add(0, 0.05, 0), UP, flat(s.look()), 0.9f, 12, 2, 1.2f);
        AstaFx.later(3, () -> {
            if (!alive(p)) return;
            Vec3 d = flat(s.look());
            Vec3 dest = null;
            for (double dist : new double[]{8, 6, 4}) {
                dest = SpaceFx.safe(sl, s.feet().add(d.scale(dist)), p);
                if (dest != null) break;
            }
            if (dest == null) return;
            Vec3 from = p.position();
            SpaceFx.move(p, dest, p.getYRot());
            p.invulnerableTime = Math.max(p.invulnerableTime, 10);
            FxSend.trail(sl, G, from.add(0, 1, 0), dest.add(0, 1, 0), 0.05f, 6);
            FxSend.trail(sl, S, from.add(0, 1, 0), dest.add(0, 1, 0), 0.015f, 4);
            column(sl, dest, 0.9f, 2.3f, 6, 2, 2, null, 1.6f, 0.15f);   // the pages you skipped flutter apart round you
            clock(sl, dest.add(0, 0.05, 0), UP, d, 1.3f, 10, 2, -1.5f);
            sound(sl, dest, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.9f, 1.4f);
            sound(sl, dest, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.3f);
        });
    }

    // ================================================================== 4. Time Recovery
    public static void timeRecovery(ServerPlayer p, float power) {
        cast(p, "time_recovery");
        ServerLevel sl = p.serverLevel();
        boolean best = TimeStop.spend(p, 5);
        List<double[]> path = TimeStop.path(p, best ? 120 : 80);
        clock(sl, p.position().add(0, 0.05, 0), UP, flat(p.getLookAngle()), 1.7f, 26, 3, -0.35f);
        screen(p, 22, 1);
        sound(sl, p.position(), SoundEvents.TRIDENT_RETURN, 1.0f, 0.6f);
        // your past selves flicker back, newest first: time running backwards
        int n = Math.min(5, path.size());
        for (int i = 0; i < n; i++) {
            final double[] q = path.get(path.size() - 1 - (int) ((i + 1) * (path.size() - 1) / (double) Math.max(1, n)));
            final float al = 0.5f - 0.06f * i;
            AstaFx.later(1 + i * 2, () -> { if (alive(p)) ghost(sl, p, new Vec3(q[2], q[3], q[4]), (float) q[5], 9, 0, 0, al); });
        }
        AstaFx.later(11, () -> {
            if (!alive(p)) return;
            double target = 0;
            if (best) { for (double[] q : path) target = Math.max(target, q[1]); }
            else { double[] q = TimeStop.past(p, 80); if (q != null) target = q[1]; }
            float before = p.getHealth();
            if (target > before) p.setHealth((float) Math.min(p.getMaxHealth(), target));
            p.clearFire();
            p.fallDistance = 0;
            column(sl, p.position(), 1.15f, 2.4f, 14, 4, 2, p, 1.2f, -0.12f);
            FxSend.ring(sl, G, p.position().add(0, 0.05, 0), 0.3f, 2.4f, 0.05f, 10);
            Fx.flash(sl, p.position().add(0, 1, 0), 1.2f);
            sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.7f);
            sound(sl, p.position(), SoundEvents.BELL_RESONATE, 0.6f, 1.4f);
            if (p.getHealth() > before + 0.5f)
                p.displayClientMessage(Component.literal("Rewound " + Math.round(p.getHealth() - before) / 2.0 + " hearts").withStyle(ChatFormatting.AQUA), true);
        });
    }

    // ================================================================== 5. Chrono Acceleration
    public static void chronoAcceleration(ServerPlayer p, float power) {
        String prev = cast(p, "chrono_acceleration");
        ServerLevel sl = p.serverLevel();
        final int T = 120;
        AstaFx.later(7, () -> {
            if (!alive(p)) return;
            Moves.stance(p, 0, 0, 0.45, 0, 0.4, T);
            clock(sl, p.position().add(0, 0.05, 0), UP, flat(p.getLookAngle()), 1.4f, 14, 2, 1.6f);
            FxSend.ring(sl, G, p.position().add(0, 0.1, 0), 0.3f, 3.0f, 0.05f, 8);
            sound(sl, p.position(), SoundEvents.BEACON_POWER_SELECT, 0.9f, 1.8f);
            for (int k = 0; k < 3; k++) AstaFx.later(k * 2, () -> sound(sl, p.position(), SoundEvents.COMPARATOR_CLICK, 0.8f, 1.9f));
            if ("time_theft".equals(prev)) {   // Borrowed Time: the stolen seconds come off every cooldown
                int sec = Math.min(8, STOLEN.getOrDefault(p.getUUID(), 2));
                hasten(p, sec * 20, true);
                p.displayClientMessage(Component.literal("Borrowed Time: -" + sec + "s on every cooldown").withStyle(ChatFormatting.AQUA), true);
                sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.8f);
            }
            for (int t = 1; t <= T; t++) {   // every cooldown runs twice as fast; afterimages trail behind you
                final int tt = t;
                AstaFx.later(t, () -> {
                    if (!alive(p)) return;
                    hasten(p, 1, tt % 10 == 0);
                    if (tt % 3 == 0 && p.getDeltaMovement().horizontalDistanceSqr() > 0.004)
                        ghost(sl, p, p.position(), p.getYRot(), 8, 0, 3, 0.32f);
                });
            }
        });
    }

    // ================================================================== 6. Ticking Snare
    public static void tickingSnare(ServerPlayer p, float power) {
        cast(p, "ticking_snare");
        remember(p, "ticking_snare", TimeFx::snare, power);
        snare(p, Src.of(p), power, false);
    }

    private static void snare(ServerPlayer p, Src s, float power, boolean echo) {
        ServerLevel sl = p.serverLevel();
        final int STEP = 15;
        final float R = echo ? 2.0f : 2.6f;
        AstaFx.later(7, () -> {
            if (!alive(p)) return;
            Vec3 c = groundAt(sl, s.aim(sl, p, 18)).add(0, 0.04, 0);
            int clk = clock(sl, c, UP, flat(s.look()), R, STEP * 3 + 12, 1, (float) (TAU / 12 / STEP), STEP, 3, 0, 0, null, 0);
            FxSend.ring(sl, G, c, 0.2f, R, 0.04f, 8);
            sound(sl, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.8f);
            for (int t = 1; t < STEP * 3; t++) {   // whoever walks on it already feels time thicken
                final int tt = t;
                AstaFx.later(t, () -> {
                    if (tt % 5 == 0) for (LivingEntity e : Kit.near(sl, p, c.add(0, 0.8, 0), R)) Moves.slow(e, 8, -0.25);
                });
            }
            AstaFx.later(STEP * 2, () -> column(sl, c.add(0, -0.04, 0), R, 2.0f, 54, 6, 0, null, 1.2f, 0.01f));
            AstaFx.later(STEP * 3, () -> {   // the third tick: everyone still on it stops
                if (!alive(p)) return;
                sound(sl, c, SoundEvents.BELL_BLOCK, 1.0f, 1.2f);
                FxSend.impactFrame(sl, c, 2, FxSend.FRAME_JULIUS, 20);
                for (LivingEntity e : Kit.near(sl, p, c.add(0, 0.8, 0), R + 0.3)) {
                    hit(p, e, power, "ticking_snare");
                    TimeStop.freeze(p, e, echo ? 25 : 40, false, null);
                    bubble(sl, e, echo ? 25 : 40);
                }
                Fx.flash(sl, c.add(0, 0.5, 0), 1.4f);
            });
            AstaFx.later(STEP * 3 + 2, () -> FxSend.kill(sl, clk, c));
        });
    }

    // ================================================================== 7. Time Theft
    public static void timeTheft(ServerPlayer p, float power) {
        cast(p, "time_theft");
        remember(p, "time_theft", TimeFx::theft, power);
        theft(p, Src.of(p), power, false);
    }

    private static void theft(ServerPlayer p, Src s, float power, boolean echo) {
        ServerLevel sl = p.serverLevel();
        AstaFx.later(5, () -> {   // the reach: threads of gold to everyone ahead
            if (!alive(p)) return;
            List<LivingEntity> ts = cone(sl, p, s, 11, 34);
            Vec3 h = s.hand();
            for (LivingEntity t : ts) {
                FxSend.trail(sl, G, h, mid(t), 0.035f, 8);
                FxSend.trail(sl, S, h, mid(t), 0.01f, 6);
                clock(sl, mid(t), h.subtract(mid(t)), UP, 0.55f, 14, 3, -0.7f);   // its clock runs backwards
            }
            sound(sl, h, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9f, 1.4f);
            AstaFx.later(5, () -> {   // the pull: the time comes back to your fist
                if (!alive(p)) return;
                int got = 0;
                Vec3 h2 = echo ? s.hand() : Kit.hand(p);
                for (LivingEntity t : ts) {
                    if (!t.isAlive()) continue;
                    hit(p, t, power * 0.6f, "time_theft");
                    Moves.slow(t, 100, -0.45);
                    FxSend.trail(sl, G, mid(t), h2, 0.05f, 4);
                    Kit.orb(sl, G, mid(t), h2.subtract(mid(t)).scale(0.25), 0.18f, 4);
                    got++;
                }
                if (got > 0) {
                    if (!echo) TimeStop.addStored(p, got * 2);
                    STOLEN.put(p.getUUID(), got * 2);
                    Fx.flash(sl, h2, 0.6f);
                    sound(sl, h2, SoundEvents.TRIDENT_RETURN, 0.9f, 1.6f);
                }
            });
        });
    }

    // ================================================================== 8. Hourglass Barrage
    public static void hourglassBarrage(ServerPlayer p, float power) {
        cast(p, "hourglass_barrage");
        remember(p, "hourglass_barrage", TimeFx::barrage, power);
        barrage(p, Src.of(p), power, false);
    }

    private static void barrage(ServerPlayer p, Src s, float power, boolean echo) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(sl, p, s, 28, 16);
        Vec3 c = groundAt(sl, t != null ? t.position() : s.aim(sl, p, 24));
        int n = echo ? 6 : TimeStop.spend(p, 4) ? 18 : 12;
        Vec3 top = c.add(0, 7.5, 0);
        int clk = clock(sl, top, new Vec3(0, -1, 0), flat(s.look()), 3.0f, 12 + n * 2 + 8, 0, 0.25f);   // a clock opens over them
        sound(sl, top, SoundEvents.BELL_RESONATE, 0.8f, 1.2f);
        Random r = new Random(sl.getGameTime() ^ p.getId());
        for (int i = 0; i < n; i++) {
            final int ii = i;
            double a = TAU * i / n + r.nextDouble() * 0.3;
            double ra = Math.sqrt(r.nextDouble()) * 3.4, ta = r.nextDouble() * TAU;
            Vec3 from = top.add(Math.cos(a) * 2.6, 0, Math.sin(a) * 2.6);
            Vec3 land = groundAt(sl, c.add(Math.cos(ta) * ra, 0, Math.sin(ta) * ra));
            Vec3 vel = land.subtract(from).scale(1 / 6.0);
            AstaFx.later(8 + i * 2, () -> {
                if (!alive(p)) return;
                hand(sl, from, vel, vel, 1.4f, 6, 0);
                sound(sl, from, SoundEvents.COMPARATOR_CLICK, 0.7f, 1.5f + (ii % 4) * 0.1f);
            });
            AstaFx.later(14 + i * 2, () -> {
                if (!alive(p)) return;
                Vec3 dir = vel.normalize();
                hand(sl, land.subtract(dir.scale(0.45)), Vec3.ZERO, dir, 1.4f, 18, 0);   // it stays planted, then fades
                for (LivingEntity e : Kit.near(sl, p, land.add(0, 0.6, 0), 1.4)) {
                    hit(p, e, power, "hourglass_barrage");
                    Kit.push(e, mid(e).subtract(land), 0.15, 0.1);
                }
                Fx.spark(sl, G, land.add(0, 0.2, 0), 0.7f);
                FxSend.ring(sl, G, land.add(0, 0.05, 0), 0.1f, 1.1f, 0.03f, 5);
                sound(sl, land, SoundEvents.AMETHYST_BLOCK_HIT, 0.9f, 1.0f + (ii % 3) * 0.15f);
            });
        }
        AstaFx.later(14 + n * 2 + 2, () -> FxSend.kill(sl, clk, top));
    }

    // ================================================================== 9. Rewind Ward
    public static void rewindWard(ServerPlayer p, float power) {
        cast(p, "rewind_ward");
        ServerLevel sl = p.serverLevel();
        final int T = 60;
        AstaFx.later(6, () -> {
            if (!alive(p)) return;
            TimeStop.ward(p, T);
            column(sl, p.position(), 1.15f, 2.4f, T, 4, 2, p, 0.9f, -0.03f);
            clock(sl, p.position(), UP, flat(p.getLookAngle()), 1.5f, T, 3, -0.3f, 0, 0, 0, 0, p, 0.05);
            sound(sl, p.position(), SoundEvents.BEACON_ACTIVATE, 0.7f, 1.8f);
            sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.9f);
        });
        AstaFx.later(6 + T + 1, () -> {
            if (!alive(p)) return;
            float back = TimeStop.endWard(p);
            if (back <= 0.05f) return;
            p.heal(back);
            screen(p, 10, 1);
            FxSend.ring(sl, G, p.position().add(0, 0.05, 0), 2.4f, 0.2f, 0.06f, 10);   // the ring rolls back in
            Fx.flash(sl, p.position().add(0, 1, 0), 1.0f);
            sound(sl, p.position(), SoundEvents.TRIDENT_RETURN, 1.0f, 1.0f);
            sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
            p.displayClientMessage(Component.literal("Rewound " + Math.round(back) / 2.0 + " hearts").withStyle(ChatFormatting.AQUA), true);
        });
    }

    /** A blow soaked by the ward: a page flicks out and a tick sounds. */
    public static void wardSoak(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, G, p.position().add(0, 1.0, 0), 0.2f, 1.3f, 0.03f, 5);
        sound(sl, p.position(), SoundEvents.COMPARATOR_CLICK, 0.9f, 0.8f);
    }

    // ================================================================== 10. Echo of the Past
    public static void echoOfThePast(ServerPlayer p, float power) {
        cast(p, "echo_of_the_past");
        ServerLevel sl = p.serverLevel();
        final Src s = Src.of(p);
        final Last last = LAST.get(p.getUUID());
        AstaFx.later(9, () -> {
            if (!alive(p)) return;
            ghost(sl, p, s.feet(), s.yaw(), 48, 1, 1, 0.75f);   // you, left behind in the moment
            clock(sl, s.feet().add(0, 0.05, 0), UP, flat(s.look()), 1.0f, 46, 2, 0.35f);
            column(sl, s.feet(), 0.85f, 2.2f, 46, 6, 2, null, 0.6f, 0.03f);
            sound(sl, s.feet(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.9f, 1.2f);
        });
        AstaFx.later(49, () -> {   // the echo recasts
            if (!alive(p)) return;
            Fx.flash(sl, s.feet().add(0, 1, 0), 1.0f);
            FxSend.trail(sl, G, s.feet().add(0, 1, 0), p.position().add(0, 1, 0), 0.025f, 8);   // the thread between you and it
            sound(sl, s.feet(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.9f);
            if (last != null) {
                last.op().run(p, s, last.power() * 0.5f, true);
            } else {
                for (LivingEntity e : Kit.near(sl, p, s.feet().add(0, 1, 0), 3.5)) { Moves.slow(e, 60, -0.4); hit(p, e, power * 0.4f, "echo_of_the_past"); }
                FxSend.ring(sl, G, s.feet().add(0, 0.05, 0), 0.3f, 3.5f, 0.05f, 8);
            }
        });
    }

    // ================================================================== 11. Chronostasis Field (mode)
    public static void chronostasisField(ServerPlayer p, float power) {
        cast(p, MODE);
        ServerLevel sl = p.serverLevel();
        final int T = 300;
        final double R = 12;
        AstaFx.later(12, () -> {
            if (!alive(p)) return;
            int col = column(sl, p.position(), (float) R, 7f, T, 10, 0, p, 0.6f, 0.004f);
            FxSend.impactFrame(sl, p.position().add(0, 1, 0), 2, FxSend.FRAME_JULIUS, 28);
            sound(sl, p.position(), SoundEvents.BEACON_ACTIVATE, 1.0f, 0.7f);
            sound(sl, p.position(), SoundEvents.BELL_RESONATE, 0.7f, 1.0f);
            final long until = sl.getGameTime() + T;
            Moves.mode(p, MODE, T, 1, pl -> {
                long now = sl.getGameTime();
                if (now > until) return;
                Vec3 c = pl.position();
                hasten(pl, 1, now % 10 == 0);                    // your cooldowns run twice as fast
                if (now % 10 == 0) {
                    for (LivingEntity e : Kit.near(sl, pl, c.add(0, 1, 0), R)) Moves.slow(e, 14, -0.65);   // foes crawl
                    TimeStop.freezeShots(sl, c.add(0, 1, 0), R, 12, pl);                                     // arrows hang
                }
                if (now % 40 == 0) for (LivingEntity e : Kit.near(sl, pl, c.add(0, 1, 0), R)) {
                    hit(pl, e, power * 0.5f, MODE);
                    clock(sl, mid(e), c.add(0, 1.5, 0).subtract(mid(e)), UP, 0.5f, 12, 3, -0.6f);
                }
            });
            AstaFx.later(T, () -> FxSend.kill(sl, col, p.position()));
        });
    }

    // ================================================================== 12. Zenith Hour
    public static void zenithHour(ServerPlayer p, float power) {
        cast(p, "zenith_hour");
        if (LimitBreak.active(p)) { eternalSovereign(p, power); return; }
        stopTheWorld(p, power, false);
    }

    /** Zenith Hour and Eternal Sovereign share this: the column, the clock in the sky, time stopping, time resuming. */
    private static void stopTheWorld(ServerPlayer p, float power, boolean sovereign) {
        ServerLevel sl = p.serverLevel();
        final Vec3 c = p.position();
        final float R = sovereign ? 16f : 15f, H = sovereign ? 28f : 14f;
        final int STOP = sovereign ? 120 : 80, SNAP = 18;
        final Vec3 fl = flat(p.getLookAngle());
        final int col = column(sl, c, R, H, SNAP + STOP + 4, 12, sovereign ? 1 : 0, null, 0.8f, 0.003f);
        sound(sl, c, SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.5f);
        AstaFx.later(6, () -> {   // the great clock opens in the sky
            if (!alive(p)) return;
            clock(sl, c.add(0, H + 3, 0), new Vec3(0, -1, 0), fl, sovereign ? 10f : 8f, SNAP + STOP + 4, 0, 0.18f, 0, 0, SNAP - 6, STOP, null, 0);
            sound(sl, c, SoundEvents.BELL_RESONATE, 1.4f, 0.6f);
        });
        if (sovereign) AstaFx.later(8, () -> tower(p, c, fl, SNAP + STOP));
        AstaFx.later(SNAP, () -> {   // the snap of his fingers: time stops
            if (!alive(p)) return;
            freezeColumn(sl, col, c, STOP);
            int stopped = 0;
            for (LivingEntity e : Kit.near(sl, p, c.add(0, 1, 0), R)) if (TimeStop.freeze(p, e, STOP, true, null)) stopped++;
            TimeStop.freezeShots(sl, c.add(0, 2, 0), R + 2, STOP, p);
            screenNear(sl, c, R + 4, STOP, 0);
            FxSend.impactFrame(sl, c.add(0, 1, 0), 4, FxSend.FRAME_JULIUS, R + 24);
            FxSend.shake(sl, c, 0.6f, 6, R + 20);
            sound(sl, c, SoundEvents.BEACON_DEACTIVATE, 1.6f, 0.5f);
            sound(sl, c, SoundEvents.BELL_BLOCK, 1.6f, 0.5f);
            sound(sl, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 0.5f);
            p.displayClientMessage(Component.literal(sovereign ? "Eternal Sovereign" : "Zenith Hour").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
            if (stopped == 0) Fx.flash(sl, c.add(0, 1, 0), 2f);
            if (sovereign) for (int i = 0; i < 12; i++) {   // twelve giant clock hands strike the stopped field, one by one
                final int ii = i;
                AstaFx.later(14 + i * 5, () -> giantHand(p, c, R, power, ii));
            }
        });
        AstaFx.later(SNAP + STOP, () -> {   // time starts again: every held blow lands, the pages scatter
            if (!alive(p)) return;
            FxSend.kill(sl, col, c);
            for (LivingEntity e : Kit.near(sl, p, c.add(0, 1, 0), R + 2)) {
                hit(p, e, power * (sovereign ? 0.8f : 0.5f), "zenith_hour");
                Kit.push(e, mid(e).subtract(c), 0.5, 0.25);
            }
            FxSend.ring(sl, G, c.add(0, 0.1, 0), 0.5f, R, 0.1f, 12);
            FxSend.impactFrame(sl, c.add(0, 1, 0), 3, FxSend.FRAME_JULIUS, R + 24);
            FxSend.shake(sl, c, 0.8f, 10, R + 20);
            sound(sl, c, SoundEvents.BELL_BLOCK, 1.8f, 0.7f);
            AstaFx.later(4, () -> sound(sl, c, SoundEvents.BELL_BLOCK, 1.4f, 0.5f));
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.4f);
            if (sovereign) {   // and you are rewound to your best
                p.setHealth(p.getMaxHealth());
                p.clearFire();
                screen(p, 16, 1);
                column(sl, p.position(), 1.2f, 2.5f, 16, 4, 2, p, 1.4f, -0.15f);
                p.displayClientMessage(Component.literal("Time rewinds for you").withStyle(ChatFormatting.AQUA), true);
            }
        });
    }

    /** The Sovereign's clock tower: a narrow column of gold-edged pages with a clock face on top, at the far side. */
    private static void tower(ServerPlayer p, Vec3 c, Vec3 fl, int life) {
        ServerLevel sl = p.serverLevel();
        Vec3 base = groundAt(sl, c.add(fl.scale(11)));
        column(sl, base, 2.6f, 22f, life, 16, 1, null, 0.3f, 0.01f);
        clock(sl, base.add(0, 25, 0).subtract(fl.scale(2.8)), fl.scale(-1), UP, 4.4f, life, 0, 0.1f, 0, 0, 10, life, null, 0);
        FxSend.spike(sl, G, base.add(0, 22, 0), UP, 6f, 1.2f, life, 8, 0);
        sound(sl, base, SoundEvents.BELL_RESONATE, 1.6f, 0.4f);
    }

    /** One of the Sovereign's twelve hands: it falls from the clock onto a foe (its blow is held like the rest). */
    private static void giantHand(ServerPlayer p, Vec3 c, float R, float power, int i) {
        if (!alive(p)) return;
        ServerLevel sl = p.serverLevel();
        List<LivingEntity> ts = Kit.near(sl, p, c.add(0, 1, 0), R);
        Vec3 land;
        LivingEntity t = ts.isEmpty() ? null : ts.get(i % ts.size());
        if (t != null) land = t.position();
        else { double a = TAU * i / 12; land = groundAt(sl, c.add(Math.cos(a) * R * 0.6, 0, Math.sin(a) * R * 0.6)); }
        Vec3 from = land.add(0, 20, 0), vel = new Vec3(0, -2.5, 0);
        hand(sl, from, vel, vel, 6.5f, 8, 1);
        sound(sl, land, SoundEvents.BELL_BLOCK, 1.0f, 0.8f + i * 0.05f);
        AstaFx.later(8, () -> {
            if (!alive(p)) return;
            hand(sl, land.add(0, 2.2, 0), Vec3.ZERO, new Vec3(0, -1, 0), 6.5f, 30, 1);
            for (LivingEntity e : Kit.near(sl, p, land.add(0, 1, 0), 2.6)) hit(p, e, power * 0.6f, "zenith_hour");
            FxSend.ring(sl, G, land.add(0, 0.05, 0), 0.3f, 2.6f, 0.06f, 8);
            FxSend.shake(sl, land, 0.25f, 4, 30);
            sound(sl, land, SoundEvents.ANVIL_LAND, 0.6f, 1.4f);
        });
    }

    // ================================================================== Surpassing Limits
    private static void eternalSovereign(ServerPlayer p, float power) { stopTheWorld(p, power * 1.3f, true); }

    /** The Time Grimoire's awakening: pages fan up round you and a clock opens overhead. */
    public static void awaken(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        column(sl, c, 5.5f, 7f, 44, 8, 1, null, 1.0f, 0.02f);
        clock(sl, c.add(0, 9.5, 0), new Vec3(0, -1, 0), flat(p.getLookAngle()), 4f, 44, 0, 0.5f);
        FxSend.ring(sl, G, c.add(0, 0.1, 0), 0.5f, 6.0f, 0.2f, 14);
        sound(sl, c, SoundEvents.BELL_BLOCK, 1.2f, 0.6f);
    }

    // ================================================================== held blows (Zenith Hour) and the stored-time bell
    /** A blow landed on a held target: a gold hand hangs in the air, pointing at it, until time starts again. */
    public static void heldBlow(ServerPlayer by, LivingEntity t, float bank) {
        ServerLevel sl = by.serverLevel();
        List<Integer> ids = HELD.computeIfAbsent(t.getUUID(), k -> new ArrayList<>());
        int n = ids.size() + 1;
        if (n > 14) { sound(sl, mid(t), SoundEvents.COMPARATOR_CLICK, 0.9f, 1.4f); return; }
        Random r = new Random(t.getId() * 31L + n);
        Vec3 m = mid(t);
        Vec3 off = new Vec3(r.nextDouble() - 0.5, r.nextDouble() * 0.6 - 0.1, r.nextDouble() - 0.5).normalize().scale(1.1 + t.getBbWidth() * 0.5);
        ids.add(hand(sl, m.add(off), Vec3.ZERO, off.scale(-1), 0.9f, 200, 0));
        sound(sl, m, SoundEvents.COMPARATOR_CLICK, 0.9f, 0.6f + n * 0.05f);
    }

    /** Time starts again on a held target: everything it was dealt lands at once, a little harder. */
    public static void releaseHeld(ServerPlayer by, LivingEntity t, float bank) {
        ServerLevel sl = by.serverLevel();
        Vec3 m = mid(t);
        List<Integer> ids = HELD.remove(t.getUUID());
        if (ids != null) for (int id : ids) FxSend.kill(sl, id, m);   // the hanging hands strike home
        hit(by, t, bank * 1.2f, "zenith_hour");
        Fx.impact(sl, G, m, Math.min(2.4f, 0.8f + bank * 0.05f));
        FxSend.ring(sl, G, t.position().add(0, 0.05, 0), 0.2f, 2.0f, 0.05f, 8);
        sound(sl, m, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
    }

    /** A held target died or vanished before time started again: its hanging hands go. */
    public static void dropHeld(ServerLevel sl, LivingEntity t) {
        List<Integer> ids = HELD.remove(t.getUUID());
        if (ids != null) for (int id : ids) FxSend.kill(sl, id, t.position());
    }

    /** The tenth stored second: a soft chime. */
    public static void fullStore(ServerPlayer p) {
        sound(p.serverLevel(), p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.6f);
    }

    public static void clearAll() { LAST.clear(); PREV.clear(); BOLTED.clear(); STOLEN.clear(); HELD.clear(); }
}
