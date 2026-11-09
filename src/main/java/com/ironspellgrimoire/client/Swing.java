package com.ironspellgrimoire.client;

import net.mehvahdjukaar.moonlight.api.client.anim.PendulumAnimation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Swinging physics from Moonlight: a real damped pendulum per player for the grimoire hanging on the hip. Starting,
 * stopping and turning kick it sideways, and it then swings back and settles by itself (no fake sine wave).
 */
final class Swing {
    private Swing() {}

    private static final PendulumAnimation.Config CONFIG = new PendulumAnimation.Config();
    private static final Map<Integer, PendulumAnimation> HIP = new HashMap<>();
    private static final Map<Integer, Vec3> LAST = new HashMap<>();

    private static PendulumAnimation of(int id) {
        return HIP.computeIfAbsent(id, k -> new PendulumAnimation(() -> CONFIG, () -> new Vector3f(0F, 0F, 1F)));
    }

    /** Once per client tick for each player wearing a grimoire. */
    static void tick(Player p, float bodyYaw) {
        PendulumAnimation a = of(p.getId());
        Vec3 now = p.getDeltaMovement();
        Vec3 was = LAST.put(p.getId(), now);
        if (was != null) {
            Vec3 dv = now.subtract(was);
            float r = (float) Math.toRadians(bodyYaw);
            double side = dv.x * Math.cos(r) + dv.z * Math.sin(r);          // acceleration across the body
            double fwd = -dv.x * Math.sin(r) + dv.z * Math.cos(r);           // along it: a lurch makes it kick too
            float kick = (float) Mth.clamp(side * 9.0 + fwd * 3.0, -1.2, 1.2);
            if (Math.abs(kick) > 0.02F) a.addImpulse(kick);
        }
        a.tick(false);
    }

    /** The swing in degrees, between ticks. */
    static float angle(int id, float pt) {
        PendulumAnimation a = HIP.get(id);
        return a == null ? 0F : Mth.clamp(a.getAngle(pt), -45F, 45F);
    }

    static void retain(java.util.Set<Integer> keep) {
        HIP.keySet().retainAll(keep);
        LAST.keySet().retainAll(keep);
    }

    static void clear() { HIP.clear(); LAST.clear(); }
}
