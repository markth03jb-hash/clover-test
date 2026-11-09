package com.example.arcaneaddon.client;

import com.example.arcaneaddon.network.FxPacket;
import com.example.arcaneaddon.spells.AnimNames;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Syncs every move's effects to its animation.
 *
 * When a move is cast, the server says which animation started (shape 42). Here the animation's "impact" tick is
 * found once - the tick the arms and body move fastest, i.e. where the swing comes through - by reading its
 * keyframes. If the move's first launched effect (a slash, crescent, beam, bolt, gust, shockwave, burst, impact
 * frame or shake) arrives before that tick, it and everything else the move sends while it plays are held back by
 * the same few ticks (at most 8), so their own timing is kept and the first one leaves on the swing. Moves whose
 * effects already come after the swing are left alone. Held objects re-sent every tick are never delayed.
 */
public final class FxSync {
    private FxSync() {}

    private static final int MAX_SHIFT = 8;
    /** player id -> {start tick, impact tick, shift (-1 = not decided yet), last launched effect tick} */
    private static final Map<Integer, double[]> WIN = new HashMap<>();
    private static final Map<String, Integer> IMPACT = new HashMap<>();
    /** effect id -> the shift it was given, so its kill packet is shifted the same */
    private static final Map<Integer, Integer> SHIFTED = new HashMap<>();
    private static final List<Object[]> QUEUE = new ArrayList<>();
    private static boolean releasing = false;

    public static void cast(int playerId, int nameHash, int serverImpact) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        String name = AnimNames.byHash(nameHash);
        // the server's impact tick (read from the same animation files) keeps both sides in step with each other
        int impact = serverImpact > 0 ? serverImpact : (name == null ? 0 : impactOf(name));
        if (impact <= 0) { WIN.remove(playerId); return; }
        double now = mc.level.getGameTime();
        WIN.put(playerId, new double[]{now, impact, -1, now});
    }

    /** The effects that carry a move (same rule as the server's CastSync.carrier): only these decide the shift. */
    private static boolean carrier(FxPacket p) {
        return switch (p.shape) {
            case 13, 1, 41, 23, 29, 12 -> true;
            case 19, 20 -> p.life > 1;
            default -> false;
        };
    }

    /** Once a move's shift is decided, what it puts in the world is held by that much. */
    private static boolean launch(FxPacket p) {
        return switch (p.shape) {
            case 13, 1, 41, 23, 29, 33, 0, 10, 12 -> true;
            case 2, 7, 8, 19, 20, 30 -> p.life > 1;   // not the held pieces re-sent every tick
            default -> false;
        };
    }

    /** True if the packet was held back (it will be spawned later). */
    public static boolean defer(FxPacket p) {
        if (releasing) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || WIN.isEmpty()) return false;
        double now = mc.level.getGameTime();
        if (p.shape == 3) {   // a kill follows its effect
            Integer s = SHIFTED.get(p.id);
            if (s == null || s <= 0) return false;
            QUEUE.add(new Object[]{p, now + s});
            return true;
        }
        if (!launch(p)) return false;
        // whose move is it? the nearest player with a fresh cast, within a few blocks of where the effect starts
        double[] best = null;
        double bestD = 8.0 * 8.0;
        for (Map.Entry<Integer, double[]> e : WIN.entrySet()) {
            double[] w = e.getValue();
            if (now - w[0] > w[1] + 30 && now - w[3] > 10) continue;
            Entity ent = mc.level.getEntity(e.getKey());
            if (!(ent instanceof Player)) continue;
            double d = ent.position().add(0, 1, 0).distanceToSqr(p.c.equals(Vec3.ZERO) ? ent.position() : p.c);
            if (d < bestD) { bestD = d; best = w; }
        }
        if (best == null) return false;
        if (best[2] < 0) {
            if (!carrier(p)) return false;   // wind-up glows and rings before the move comes out show at once
            best[2] = Mth.clamp(best[1] - (now - best[0]), 0, MAX_SHIFT);
        }
        best[3] = now;
        int shift = (int) best[2];
        if (shift <= 0) return false;
        if (SHIFTED.size() > 512) SHIFTED.clear();
        SHIFTED.put(p.id, shift);
        QUEUE.add(new Object[]{p, now + shift});
        return true;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { QUEUE.clear(); WIN.clear(); SHIFTED.clear(); return; }
        double now = mc.level.getGameTime();
        WIN.values().removeIf(w -> now - w[0] > w[1] + 30 && now - w[3] > 10);
        if (QUEUE.isEmpty()) return;
        List<FxPacket> due = new ArrayList<>();
        for (Iterator<Object[]> it = QUEUE.iterator(); it.hasNext(); ) {
            Object[] q = it.next();
            if ((double) q[1] <= now) { due.add((FxPacket) q[0]); it.remove(); }
        }
        releasing = true;
        try { for (FxPacket p : due) FxRenderer.spawn(p); } finally { releasing = false; }
    }

    // ------------------------------------------------------------------ the swing's impact tick
    private static int impactOf(String name) {
        return IMPACT.computeIfAbsent(name, FxSync::compute);
    }

    /** Reads the keyframes (by reflection, so a library update can't break the build) and finds the fastest tick. */
    private static int compute(String name) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(name);
            if (rl == null) return 0;
            Object anim = PlayerAnimationRegistry.getAnimation(rl);
            if (anim == null) return 0;
            Object partsObj = get(anim, "bodyParts");
            if (!(partsObj instanceof Map<?, ?> parts)) return 0;
            int end = 40;
            Object e = get(anim, "endTick");
            if (e instanceof Integer i && i > 0) end = Math.min(i, 60);
            String[] names = {"rightArm", "leftArm", "body", "torso", "rightItem"};
            float[] weight = {1.0F, 0.8F, 1.5F, 1.2F, 0.6F};
            String[] axes = {"pitch", "yaw", "roll"};
            float[] speed = new float[end + 1];
            for (int k = 0; k < names.length; k++) {
                Object part = parts.get(names[k]);
                if (part == null) continue;
                for (String ax : axes) {
                    Object state = get(part, ax);
                    if (state == null) continue;
                    List<?> kfs = keyFrames(state);
                    if (kfs == null || kfs.size() < 2) continue;
                    for (int t = 0; t < end; t++) speed[t] += weight[k] * Math.abs(value(kfs, t + 1) - value(kfs, t));
                }
            }
            int best = 0;
            for (int t = 1; t < end; t++) if (speed[t] > speed[best]) best = t;
            return speed[best] < 0.06F ? 0 : Math.min(best + 1, 24);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static List<?> keyFrames(Object state) throws Exception {
        try {
            Method m = state.getClass().getMethod("getKeyFrames");
            Object o = m.invoke(state);
            if (o instanceof List<?> l) return l;
        } catch (NoSuchMethodException ignored) {}
        Object o = get(state, "keyFrames");
        return o instanceof List<?> l ? l : null;
    }

    private static float value(List<?> kfs, int t) throws Exception {
        Object prev = null;
        for (Object kf : kfs) {
            int tick = ((Number) get(kf, "tick")).intValue();
            if (tick >= t) {
                float v = ((Number) get(kf, "value")).floatValue();
                if (prev == null || tick == t) return v;
                int pt = ((Number) get(prev, "tick")).intValue();
                float pv = ((Number) get(prev, "value")).floatValue();
                return pv + (v - pv) * (t - pt) / (float) Math.max(1, tick - pt);
            }
            prev = kf;
        }
        return prev == null ? 0.0F : ((Number) get(prev, "value")).floatValue();
    }

    private static Object get(Object o, String field) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException ex) { c = c.getSuperclass(); }
        }
        return null;
    }
}
