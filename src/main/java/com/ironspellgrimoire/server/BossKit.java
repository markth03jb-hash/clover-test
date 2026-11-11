package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.entity.AddonBoss;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** What the later bosses share: who to fight, telegraphs, hitting players fairly, speaking. */
public final class BossKit {
    private BossKit() {}

    public static final int P_IDLE = 0, P_CAST = 1, P_SLASH = 2, P_DASH = 3, P_RAISE = 4, P_ROAR = 5, P_SPIN = 6, P_LAUGH = 7, P_THROW = 8;
    static final int WARN = 18;

    static boolean valid(AddonBoss b, Player p) {
        return p != null && p.isAlive() && !p.isCreative() && !p.isSpectator() && p.level() == b.level() && p.distanceToSqr(b) < 48 * 48;
    }

    /** The player it is fighting: whoever hit it last, else the nearest. */
    static Player target(AddonBoss b) {
        ServerLevel sl = (ServerLevel) b.level();
        if (b.focus != null) {
            Player p = sl.getPlayerByUUID(b.focus);
            if (valid(b, p)) return p;
            b.focus = null;
        }
        Player best = null;
        double bd = 40 * 40;
        for (Player p : sl.players()) {
            if (!valid(b, p)) continue;
            double d = p.distanceToSqr(b);
            if (d < bd) { bd = d; best = p; }
        }
        if (best != null) b.focus = best.getUUID();
        return best;
    }

    static boolean ok(AddonBoss b) { return b.isAlive() && b.transforming() == 0; }

    static float scale(AddonBoss b) {
        Difficulty d = b.level().getDifficulty();
        return (d == Difficulty.EASY ? 0.6f : d == Difficulty.HARD ? 1.25f : d == Difficulty.PEACEFUL ? 0.4f : 1f) * Bounties.damageMult(b);
    }

    static void hurt(AddonBoss b, Player p, float dmg) {
        if (!b.isAlive() || !p.isAlive()) return;
        p.hurt(b.damageSources().mobAttack(b), dmg * scale(b));
    }

    static List<Player> near(AddonBoss b, Vec3 c, double r) {
        List<Player> out = new ArrayList<>();
        for (Player p : b.level().getEntitiesOfClass(Player.class, new AABB(c, c).inflate(r))) {
            if (p.isAlive() && !p.isCreative() && !p.isSpectator() && p.position().add(0, 0.9, 0).distanceTo(c) <= r + 0.6) out.add(p);
        }
        return out;
    }

    static List<Player> along(AddonBoss b, Vec3 a, Vec3 c, double w) {
        List<Player> out = new ArrayList<>();
        for (Player p : b.level().getEntitiesOfClass(Player.class, new AABB(a, c).inflate(w + 1))) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;
            if (closestOnSegment(a, c, mid(p)).distanceTo(mid(p)) <= w) out.add(p);
        }
        return out;
    }

    static void warnCircle(ServerLevel sl, Vec3 c, float r, int ticks) { ZenonAI.warnCircle(sl, c, r, ticks); }
    static void warnLine(ServerLevel sl, Vec3 a, Vec3 c, float w, int ticks) { ZenonAI.warnLine(sl, a, c, w, ticks); }
    static Vec3 ground(ServerLevel sl, Vec3 c) { return ZenonAI.ground(sl, c); }

    /**
     * The wind-up before a blow, so you can see it coming: rings of the boss's magic closing in on it, a glow building
     * at its hands, and a flare when it lets go.
     */
    static void charge(AddonBoss b, int pal, int T) {
        ServerLevel sl = (ServerLevel) b.level();
        for (int t = 0; t < T; t += 3) {
            final float k = t / (float) Math.max(1, T);
            AstaFx.later(t, () -> {
                if (!b.isAlive()) return;
                Vec3 c = b.position();
                float r = 3.2f * (1 - k) + 0.6f;
                BossFx.ring(sl, pal, c.add(0, 0.08, 0), r - 0.12f, r, 0.05f, 2);
                BossFx.orb(sl, pal, c.add(0, b.getBbHeight() * 0.62, 0).add(b.getLookAngle().scale(0.6)), Vec3.ZERO, 0.15f + 0.35f * k, 2);
            });
        }
        AstaFx.later(Math.max(1, T - 1), () -> {
            if (!b.isAlive()) return;
            BossFx.ring(sl, pal, b.position().add(0, b.getBbHeight() * 0.55, 0), 0.3f, 2.4f, 0.08f, 6);
        });
    }

    static void say(AddonBoss b, String who, ChatFormatting col, String line) {
        for (Player p : b.level().getEntitiesOfClass(Player.class, b.getBoundingBox().inflate(48))) {
            p.sendSystemMessage(Component.literal(who + ": ").withStyle(col, ChatFormatting.BOLD)
                    .append(Component.literal(line).withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC)));
        }
    }

    static Vec3 dirTo(AddonBoss b, Player t) {
        Vec3 d = flat(t.position().subtract(b.position()));
        return d.lengthSqr() < 1e-4 ? flat(b.getLookAngle()).normalize() : d.normalize();
    }

    static void face(AddonBoss b, Vec3 dir) {
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        b.setYRot(yaw); b.setYHeadRot(yaw); b.yBodyRot = yaw;
    }

    /** Keep a fighting distance from {@code t}. */
    static void keep(AddonBoss b, Player t, double want) {
        double dist = b.distanceTo(t);
        if (dist > want + 2) b.getNavigation().moveTo(t, 1.05);
        else if (dist < want - 2) {
            Vec3 away = flat(b.position().subtract(t.position()));
            if (away.lengthSqr() > 1e-4) b.getNavigation().moveTo(b.getX() + away.normalize().x * 3, b.getY(), b.getZ() + away.normalize().z * 3, 1.05);
        } else b.getNavigation().stop();
    }
}
