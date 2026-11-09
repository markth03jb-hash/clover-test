package com.example.arcaneaddon.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Small helpers every grimoire's effect code shares (they used to be copied into each Fx class). A class that needs
 * a different version of one keeps its own, which takes precedence over these.
 */
final class FxUtil {
    private FxUtil() {}

    /**
     * A move's sound: its CC0 clip of the same kind (Sfx), with the vanilla stand-in kept quietly under it. Sounds
     * that belong to the move coming out (not its wind-up charge) wait for it the same as its visuals (CastSync).
     */
    static void sound(ServerLevel sl, Vec3 at, SoundEvent s, float vol, float pitch) {
        String kind = Sfx.kind(s);
        SoundEvent own = Sfx.own(kind);
        Runnable play = () -> {
            if (own != null) {
                sl.playSound(null, at.x, at.y, at.z, own, SoundSource.PLAYERS, Math.min(1.6f, vol * 1.1f), pitch);
                sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol * 0.35f, pitch);
            } else sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
        };
        if (kind == null || "charge".equals(kind) || !CastSync.deferSound(at, play)) play.run();
    }

    static boolean alive(ServerPlayer p) { return p.isAlive() && !p.isRemoved(); }

    static Vec3 mid(LivingEntity t) { return t.position().add(0, t.getBbHeight() * 0.5, 0); }

    static Vec3 rightOf(Vec3 dir) {
        Vec3 r = dir.cross(new Vec3(0, 1, 0));
        return r.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : r.normalize();
    }

    static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.normalize();
    }

    static Vec3 rotY(Vec3 v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    static Vec3 clip(ServerLevel sl, Entity p, Vec3 a, Vec3 b) {
        var hit = sl.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? b : hit.getLocation();
    }

    static List<LivingEntity> enemies(ServerLevel sl, ServerPlayer p, AABB box) {
        return sl.getEntitiesOfClass(LivingEntity.class, box, x -> x != p && x.isAlive() && !x.isAlliedTo(p)
                && !(x instanceof net.minecraft.world.entity.decoration.ArmorStand));
    }

    static Vec3 closestOnSegment(Vec3 a, Vec3 b, Vec3 q) {
        Vec3 ab = b.subtract(a);
        double t = ab.lengthSqr() < 1.0E-6 ? 0 : Math.max(0, Math.min(1, q.subtract(a).dot(ab) / ab.lengthSqr()));
        return a.add(ab.scale(t));
    }

    static LivingEntity aimed(ServerPlayer p, double range, double deg) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity best = null;
        double bd = Math.cos(Math.toRadians(deg));
        for (LivingEntity t : enemies(p.serverLevel(), p, p.getBoundingBox().inflate(range))) {
            Vec3 to = mid(t).subtract(eye);
            if (to.length() > range || !p.hasLineOfSight(t)) continue;
            double d = to.normalize().dot(look);
            if (d > bd) { bd = d; best = t; }
        }
        return best;
    }
}
