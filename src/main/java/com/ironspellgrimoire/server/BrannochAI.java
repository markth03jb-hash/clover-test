package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.BossKit.*;
import static com.ironspellgrimoire.server.FxUtil.*;
import static com.ironspellgrimoire.server.Rivals.*;

import com.ironspellgrimoire.entity.BrannochTor;
import com.ironspellgrimoire.entity.TorLegionnaire;
import com.ironspellgrimoire.registry.AddonEntities;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Brannoch Tor, the Siege-Lord - a commander, not a duelist.
 * <ul>
 *   <li>Cleave: a wide greatsword sweep.</li>
 *   <li>Shield Wall: a line of Legionnaires steps up between you. Their shields turn blows from the front: go round.</li>
 *   <li>Warhorn: a blast that throws you back and drives his soldiers on (faster, harder) for a while.</li>
 *   <li>The Standard (phase 2+): a bearer raises his banner at the far side of the ring. While it stands he takes
 *       half damage - bring the bearer down.</li>
 *   <li>Legion waves (phase 2+): soldiers march in from the ring's edge.</li>
 *   <li>Siege Walls (last phase): ghost ramparts slam down in lines across the ring.</li>
 *   <li>Rank 1 Shield Bash (a charge), rank 2 Testudo (soldiers close round him and soak his wounds), rank 3 Muster
 *       (the last phase opens with two waves and a fresh standard).</li>
 * </ul>
 */
public final class BrannochAI {
    private BrannochAI() {}

    static final int WAR = Fx.CRIMSON, IRON = Fx.STEEL, BRASS = Fx.GOLD;
    static final UUID HORN_SPD = UUID.fromString("7a1e0000-0000-4000-8000-0000000000b1"), HORN_DMG = UUID.fromString("7a1e0000-0000-4000-8000-0000000000b2");

    public static void tick(BrannochTor b) {
        ServerLevel sl = (ServerLevel) b.level();
        long now = sl.getGameTime();
        Player t = target(b);
        if (t == null) return;
        b.getLookControl().setLookAt(t, 30, 30);
        if (b.testudoUntil > now && now % 10 == 0) BossFx.ring(sl, BRASS, b.position().add(0, 0.1, 0), 2.6f, 3f, 0.06f, 12);
        if (b.busy > 0) { b.busy--; return; }
        keep(b, t, 3);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<TorLegionnaire> legion = legion(b);
        int cap = b.phase() == 1 ? 3 : b.phase() == 2 ? 5 : 7;
        double d = b.distanceTo(t);
        int took;
        if (b.phase() >= 2 && now >= b.waveAt) { b.waveAt = now + 520 - 60L * b.rank; took = wave(b, t, 3); }
        else if (b.phase() >= 2 && !bearerUp(b) && r.nextInt(3) == 0) took = standard(b, t);
        else if (b.phase() >= 3 && r.nextInt(3) == 0) took = walls(b, t);
        else if (b.rank >= 1 && d > 5 && r.nextInt(3) == 0) took = bash(b, t);
        else if (b.rank >= 2 && legion.size() >= 2 && b.testudoUntil < now && r.nextInt(5) == 0) took = testudo(b);
        else if (legion.size() < cap && r.nextInt(3) == 0) took = shieldWall(b, t, Math.min(cap - legion.size(), b.phase() == 1 ? 3 : 4));
        else if (legion.size() >= 2 && r.nextInt(4) == 0) took = warhorn(b);
        else took = d < 5.5 ? cleave(b, t) : r.nextBoolean() ? bash(b, t) : shieldWall(b, t, Math.min(Math.max(0, cap - legion.size()), 3));
        b.busy = took + (b.phase() == 1 ? 28 : 20) - 2 * b.rank;
    }

    public static void phase(BrannochTor b, int phase) {
        Player t = target(b);
        if (t == null) return;
        if (phase == 2) { standard(b, t); b.waveAt = b.level().getGameTime() + 200; }
        if (phase == 3 && b.rank >= 3) {   // Muster
            wave(b, t, 3);
            AstaFx.later(30, () -> { if (ok(b) && !b.yielded) wave(b, t, 3); });
            if (!bearerUp(b)) standard(b, t);
        }
    }

    public static float guard(BrannochTor b, DamageSource src) {
        float m = 1f;
        if (bearerUp(b)) m *= 0.5f;
        if (b.testudoUntil > b.level().getGameTime()) {
            int n = 0;
            for (TorLegionnaire l : legion(b)) if (l.distanceTo(b) < 5) n++;
            m *= Math.max(0.2f, 1f - 0.2f * n);
        }
        return m;
    }

    // ------------------------------------------------------------------ the Legion
    static List<TorLegionnaire> legion(BrannochTor b) {
        return b.level().getEntitiesOfClass(TorLegionnaire.class, b.getBoundingBox().inflate(RING * 2 + 8), l -> l.isAlive() && b.getUUID().equals(l.lord));
    }

    static boolean bearerUp(BrannochTor b) {
        Entity e = b.bearer < 0 ? null : b.level().getEntity(b.bearer);
        return e instanceof TorLegionnaire l && l.isAlive() && l.bearer();
    }

    static TorLegionnaire soldier(BrannochTor b, Vec3 at, Player t, boolean bearer) {
        ServerLevel sl = (ServerLevel) b.level();
        TorLegionnaire l = AddonEntities.TOR_LEGIONNAIRE.get().create(sl);
        if (l == null) return null;
        Vec3 g = spot(sl, at, b.getY());
        l.moveTo(g.x, g.y, g.z, b.getYRot(), 0);
        l.lord = b.getUUID();
        l.setBearer(bearer);
        var hp = l.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(30 + 10 * b.rank + (bearer ? 20 : 0));
        l.setHealth(l.getMaxHealth());
        sl.addFreshEntity(l);
        if (t != null) l.setTarget(t);
        BossFx.ring(sl, BRASS, g.add(0, 0.08, 0), 1.2f, 0.2f, 0.06f, 8);
        return l;
    }

    /** Spawns soldiers at points, after a short telegraph at each. */
    static void muster(BrannochTor b, List<Vec3> at, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        for (Vec3 p : at) BossFx.ring(sl, BRASS, ground(sl, p.add(0, 1, 0)).add(0, 0.07, 0), 0.9f, 1.1f, 0.06f, 12);
        AstaFx.later(12, () -> { if (!ok(b) || b.yielded) return; for (Vec3 p : at) soldier(b, p, t, false); });
    }

    public static float shield(TorLegionnaire l, DamageSource src, float amount) {
        if (l.bearer() || src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
        Entity a = src.getEntity();
        Vec3 from = src.getSourcePosition() != null ? src.getSourcePosition() : a != null ? a.position() : null;
        if (from == null) return amount;
        Vec3 to = flat(from.subtract(l.position()));
        if (to.lengthSqr() < 1e-4) return amount;
        if (to.normalize().dot(flat(l.getLookAngle()).normalize()) < 0.35) return amount;   // from the side or behind
        if (l.tickCount % 4 == 0) snd((ServerLevel) l.level(), l.position(), "rival_shieldwall", 0.4f, 1.8f);
        return amount * 0.3f;
    }

    public static void legionTick(TorLegionnaire l) {
        ServerLevel sl = (ServerLevel) l.level();
        Entity lord = l.lord == null ? null : sl.getEntity(l.lord);
        if (!(lord instanceof BrannochTor b) || !b.isAlive() || b.over) {
            BossFx.ring(sl, BRASS, l.position().add(0, 0.1, 0), 0.2f, 1.2f, 0.06f, 6);
            l.discard();
            return;
        }
        if (l.bearer()) {
            BossFx.trail(sl, BRASS, l.position().add(0, 2.4, 0), b.position().add(0, 2.2, 0), 0.06f, 22);
            Player near = sl.getNearestPlayer(l, 7);
            if (near != null) {
                Vec3 away = flat(l.position().subtract(near.position()));
                if (away.lengthSqr() > 1e-4) {
                    Vec3 to = l.position().add(away.normalize().scale(4));
                    if (b.arena == null || flatDist(to, b.arena) < RING - 1) l.getNavigation().moveTo(to.x, to.y, to.z, 1.1);
                }
            }
        } else if (b.testudoUntil > sl.getGameTime() && l.distanceTo(b) > 3) l.getNavigation().moveTo(b, 1.2);
    }

    public static boolean legionHit(TorLegionnaire l, Entity t) {
        Entity lord = l.lord == null ? null : l.level().getEntity(l.lord);
        return !(lord instanceof BrannochTor b) || inRing(b, t);
    }

    public static void dismiss(BrannochTor b) { for (TorLegionnaire l : legion(b)) l.discard(); }

    // ------------------------------------------------------------------ moves
    /** Cleave: a wide, heavy sweep. */
    static int cleave(BrannochTor b, Player t) {
        charge(b, WAR, 14);
        AstaFx.later(14, () -> {
            if (!ok(b) || b.yielded) return;
            b.setPose(P_SLASH);
            Vec3 d = dirTo(b, t);
            face(b, d);
            arc(b, WAR, d, 4.6f, 150, 9f);
            snd(b, "rival_cleave", 1.3f, 0.9f);
        });
        return 24;
    }

    /** Shield Wall: a line of soldiers between you. */
    static int shieldWall(BrannochTor b, Player t, int n) {
        if (n <= 0) return cleave(b, t);
        b.setPose(P_CAST);
        Vec3 d = dirTo(b, t), side = rightOf(d);
        Vec3 mid = b.position().add(d.scale(Math.min(3.5, b.distanceTo(t) * 0.5)));
        List<Vec3> at = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) at.add(mid.add(side.scale((i - (n - 1) / 2.0) * 1.5)));
        muster(b, at, t);
        AstaFx.later(12, () -> snd(b, "rival_shieldwall", 1.2f, 1f));
        return 20;
    }

    /** Warhorn: a blast that throws you back and drives the Legion on. */
    static int warhorn(BrannochTor b) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        snd(b, "rival_horn", 1.6f, 1f);
        warnCircle(sl, b.position(), 6f, 18);
        AstaFx.later(18, () -> {
            if (!ok(b) || b.yielded) return;
            BossFx.ring(sl, WAR, b.position().add(0, 0.3, 0), 0.5f, 6.5f, 0.15f, 10);
            for (Player p : near(b, b.position().add(0, 0.9, 0), 6)) {
                hit(b, p, 6f);
                Vec3 k = flat(p.position().subtract(b.position()));
                if (k.lengthSqr() > 1e-4) { p.setDeltaMovement(k.normalize().scale(1.1).add(0, 0.35, 0)); p.hurtMarked = true; }
            }
            for (TorLegionnaire l : legion(b)) {
                TimedModifiers.apply(l, Attributes.MOVEMENT_SPEED, HORN_SPD, "Warhorn", 0.3, AttributeModifier.Operation.MULTIPLY_TOTAL, 160);
                TimedModifiers.apply(l, Attributes.ATTACK_DAMAGE, HORN_DMG, "Warhorn", 0.3, AttributeModifier.Operation.MULTIPLY_TOTAL, 160);
                BossFx.ring(sl, BRASS, l.position().add(0, 0.1, 0), 0.4f, 1.0f, 0.05f, 10);
            }
        });
        return 30;
    }

    /** The Standard: a bearer raises his banner across the ring. While it stands, he takes half damage. */
    static int standard(BrannochTor b, Player t) {
        if (bearerUp(b)) return 10;
        Vec3 away = flat(b.arena.subtract(t.position()));
        if (away.lengthSqr() < 1e-4) away = dirTo(b, t).reverse();
        TorLegionnaire l = soldier(b, b.arena.add(away.normalize().scale(RING * 0.6)), null, true);
        if (l == null) return 10;
        b.bearer = l.getId();
        b.setPose(P_RAISE);
        snd(b, "rival_horn", 1.2f, 1.3f);
        for (Player p : near(b, b.arena, RING + 1))
            p.displayClientMessage(net.minecraft.network.chat.Component.literal("The standard is raised - bring down its bearer!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        return 20;
    }

    /** A wave: soldiers march in from the ring's edge, either side of you. */
    static int wave(BrannochTor b, Player t, int n) {
        Vec3 out = flat(t.position().subtract(b.arena));
        if (out.lengthSqr() < 1e-4) out = new Vec3(1, 0, 0);
        out = out.normalize();
        List<Vec3> at = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) at.add(b.arena.add(rotY(out, Math.toRadians(-60 + 60 * i)).scale(RING - 2)));
        muster(b, at, t);
        snd(b, "rival_horn", 1f, 1.5f);
        return 16;
    }

    /** Siege Walls: ghost ramparts slam down in lines across the ring. */
    static int walls(BrannochTor b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        Vec3 d = dirTo(b, t), side = rightOf(d);
        for (int i = 0; i < 3; i++) {
            Vec3 mid = t.position().add(d.scale((i - 1) * 4));
            Vec3 a = ground(sl, mid.subtract(side.scale(8)).add(0, 1.5, 0)).add(0, 0.1, 0), c = ground(sl, mid.add(side.scale(8)).add(0, 1.5, 0)).add(0, 0.1, 0);
            final int delay = i * 8;
            AstaFx.later(delay, () -> warnLine(sl, a, c, 2.2f, 24));
            AstaFx.later(delay + 24, () -> {
                if (!b.isAlive() || b.yielded) return;
                Vec3 step = c.subtract(a);
                int n = (int) Math.max(2, step.length() / 1.6);
                for (int k = 0; k <= n; k++) {
                    Vec3 p = a.add(step.scale(k / (double) n));
                    BossFx.beam(sl, IRON, p, p.add(0, 2.6, 0), 0.4f, 10);
                }
                BossFx.trail(sl, IRON, a.add(0, 2.6, 0), c.add(0, 2.6, 0), 0.3f, 10);
                snd(sl, a.add(step.scale(0.5)), "rival_rampart", 1.3f, 1f);
                for (Player p : along(b, a.add(0, 0.9, 0), c.add(0, 0.9, 0), 1.2)) hit(b, p, 10f);
            });
        }
        return 50;
    }

    /** Shield Bash (rank 1): a charge along a line. */
    static int bash(BrannochTor b, Player t) {
        if (b.rank < 1) return cleave(b, t);
        ServerLevel sl = (ServerLevel) b.level();
        Vec3 d = dirTo(b, t);
        face(b, d);
        Vec3 a = b.position().add(0, 0.9, 0);
        Vec3 c = clip(sl, b, a, a.add(d.scale(Math.min(11, b.distanceTo(t) + 3))));
        warnLine(sl, a, c, 1.8f, 16);
        b.setPose(P_DASH);
        b.getNavigation().stop();
        AstaFx.later(16, () -> {
            if (!ok(b) || b.yielded) return;
            BossFx.trail(sl, WAR, a, c, 0.35f, 8);
            for (Player p : along(b, a, c, 1.1)) {
                hit(b, p, 8f);
                p.setDeltaMovement(d.scale(0.9).add(0, 0.3, 0)); p.hurtMarked = true;
            }
            Vec3 end = c.subtract(d.scale(0.8));
            b.teleportTo(end.x, end.y - 0.9, end.z);
            snd(b, "rival_shieldwall", 1.2f, 0.7f);
        });
        return 24;
    }

    /** Testudo (rank 2): his soldiers close round him; each one near soaks a fifth of his wounds. */
    static int testudo(BrannochTor b) {
        b.testudoUntil = b.level().getGameTime() + 160;
        b.setPose(P_CAST);
        snd(b, "rival_shieldwall", 1.4f, 0.8f);
        say(b, "Brannoch Tor", ChatFormatting.RED, "Testudo! On me!");
        return 20;
    }
}
