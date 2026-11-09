package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Small shared helpers for the Water, Mirror and Void grimoires (aiming, area hits, the slowing "drench"). */
final class Kit {
    private Kit() {}

    private static final UUID DRENCH = UUID.fromString("c41d2a10-5b7e-4f21-8a90-1f3b6d700001");

    /** Where the player looks: the first thing in the way within range, else that far ahead. */
    static Vec3 aim(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(range));
        HitResult hr = p.serverLevel().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hr.getType() == HitResult.Type.MISS ? end : hr.getLocation();
    }

    /** Where a hand is: a little in front of and beside the chest. */
    static Vec3 hand(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        return p.getEyePosition().add(look.scale(0.7)).add(rightOf(look).scale(0.3)).add(0, -0.3, 0);
    }

    static List<LivingEntity> near(ServerLevel sl, ServerPlayer p, Vec3 c, double r) {
        List<LivingEntity> l = new ArrayList<>(enemies(sl, p, new AABB(c, c).inflate(r + 1)));
        l.removeIf(t -> mid(t).distanceTo(c) > r + t.getBbWidth() * 0.5);
        return l;
    }

    /** Enemies within {@code w} of the segment a-b. */
    static List<LivingEntity> along(ServerLevel sl, ServerPlayer p, Vec3 a, Vec3 b, double w) {
        List<LivingEntity> l = new ArrayList<>(enemies(sl, p, new AABB(a, b).inflate(w + 1)));
        l.removeIf(t -> mid(t).distanceTo(closestOnSegment(a, b, mid(t))) > w + t.getBbWidth() * 0.5);
        return l;
    }

    /** Water slows what it hits by a quarter for a few seconds (a plain attribute modifier, no potion effect). */
    static void drench(LivingEntity t, int ticks) {
        TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, DRENCH, "Drenched", -0.25, AttributeModifier.Operation.MULTIPLY_TOTAL, ticks);
    }

    static void push(LivingEntity t, Vec3 dir, double h, double up) {
        Vec3 d = dir.lengthSqr() < 1.0E-4 ? Vec3.ZERO : dir.normalize();
        t.setDeltaMovement(t.getDeltaMovement().add(d.x * h, up, d.z * h));
        t.hurtMarked = true;
    }

    /** A glowing sphere of magic (the client draws it like Magna's fireballs): size r, moving with {@code vel} per tick. */
    static void orb(ServerLevel sl, int pal, Vec3 c, Vec3 vel, float r, int life) {
        FxPacket p = new FxPacket();
        p.shape = 20; p.palette = (byte) pal; p.life = Math.max(1, life <= 3 ? 1 : life); p.c = c; p.v = vel; p.r = r; p.r2 = r; p.h = 0;
        AddonNetwork.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(c.x, c.y, c.z, 128, sl.dimension())), p);
    }
}
