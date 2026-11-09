package com.example.arcaneaddon.server;

import com.example.arcaneaddon.network.AddonNetwork;
import com.example.arcaneaddon.network.FxPacket;
import com.example.arcaneaddon.spells.AnimNames;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps every move's effects AND damage on the swing of its animation.
 *
 * On cast: the animation's impact tick is looked up (AnimImpact) and sent to everyone watching (FxPacket shape 42:
 * id = player, sweep = hash of the animation id, life = impact tick). A short "cast window" opens for the caster.
 * The first launched effect the move sends (or the first damage it deals, whichever comes first) decides the
 * shift: if it comes before the impact tick, the move is behind its swing by that many ticks (max 8). Clients
 * hold the move's effects back by that shift (client/FxSync) and here the move's damage is held back by the same
 * shift, so the slash, the hit and the knockback all land as the blade comes through. Moves already timed to
 * their swing get a shift of 0 and are untouched.
 */
public final class CastSync {
    private CastSync() {}

    private static final int MAX_SHIFT = 8;

    private static final class Win { ServerPlayer p; long start; int impact; int shift = -1; long lastLaunch; Vec3 origin; double speed; }

    private static final Map<UUID, Win> WIN = new HashMap<>();
    private static boolean bypass = false;

    public static void cast(ServerPlayer p, AnimationHolder anim) {
        String name = AnimNames.of(anim);
        if (name == null) return;
        int impact = AnimImpact.of(name);
        long now = p.serverLevel().getGameTime();
        Win w = new Win();   // every cast gets a window (also moves with no swing: their damage still waits for travel)
        w.p = p; w.start = now; w.impact = impact; w.lastLaunch = now;
        WIN.put(p.getUUID(), w);
        FxPacket pkt = new FxPacket();
        pkt.shape = 42; pkt.id = p.getId(); pkt.sweep = name.hashCode(); pkt.life = impact;
        AddonNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }

    private static boolean live(Win w, long now) {
        return w.p.isAlive() && (now - w.start <= w.impact + 30 || now - w.lastLaunch <= 10);
    }

    private static int decide(Win w, long now) {
        if (w.shift < 0) w.shift = Mth.clamp(w.impact - (int) (now - w.start), 0, MAX_SHIFT);
        return w.shift;
    }

    /**
     * The effects that CARRY a move to its target - a slash, crescent, bolt, gust, thrown ball or the impact frame.
     * Only these decide when the move "comes out"; wind-up glows, rings and trails never hold anything back.
     */
    static boolean carrier(FxPacket p) {
        return switch (p.shape) {
            case 13, 1, 41, 23, 29, 12 -> true;
            case 19, 20 -> p.life > 1;
            default -> false;
        };
    }

    /** How fast a carrier travels (blocks per tick), 0 if it appears at once. */
    private static double speed(FxPacket p) {
        return switch (p.shape) {
            case 13, 1, 41 -> p.speed;
            case 19, 20 -> p.v.length();
            case 29 -> p.life > 0 ? p.r / Math.max(1.0, p.life * 0.55) : 0.0;
            default -> 0.0;
        };
    }

    /** Called for every effect packet the server sends (AddonNetwork.send). */
    public static void onSend(FxPacket p) {
        if (WIN.isEmpty() || !carrier(p) || p.c == null || p.c.equals(Vec3.ZERO)) return;
        Win best = null;
        double bestD = 8.0 * 8.0;
        for (Win w : WIN.values()) {
            long now = w.p.serverLevel().getGameTime();
            if (!live(w, now)) continue;
            double d = w.p.position().add(0, 1, 0).distanceToSqr(p.c);
            if (d < bestD) { bestD = d; best = w; }
        }
        if (best == null) return;
        long now = best.p.serverLevel().getGameTime();
        decide(best, now);
        best.lastLaunch = now;
        double sp = speed(p);
        if (best.origin == null && sp > 0.2) { best.origin = p.c; best.speed = sp; }   // the move travels: remember from where, how fast
    }

    /**
     * Deal a move's damage when the move actually reaches the target - never before it has visibly come out:
     *   1. it waits for the move to come out (its carrier effect is held for the swing - the same shift as the visuals)
     *   2. then for the carrier to travel from where it was launched to the target (a slash or a thrown ball flies)
     * The check runs one tick later, so the move's own effects sent in the same tick are already known.
     * Returns true if the damage was taken over ({@code deal} runs later), false to deal it right now.
     */
    public static boolean deferDamage(ServerPlayer p, net.minecraft.world.entity.LivingEntity target, Runnable deal) {
        if (bypass || WIN.isEmpty()) return false;
        Win w = WIN.get(p.getUUID());
        if (w == null) return false;
        long now = p.serverLevel().getGameTime();
        if (!live(w, now)) { WIN.remove(p.getUUID()); return false; }
        AstaFx.later(1, () -> {
            int shift = Math.max(0, w.shift);
            int travel = 0;
            if (w.origin != null && w.speed > 0.2 && target != null) {
                double dist = w.origin.distanceTo(target.position().add(0, target.getBbHeight() * 0.5, 0));
                travel = (int) Math.min(12, Math.round(dist / w.speed));
            }
            int rest = Math.max(0, shift + travel - 1);
            Runnable go = () -> { bypass = true; try { deal.run(); } finally { bypass = false; } };
            if (rest <= 0) go.run(); else AstaFx.later(rest, go);
        });
        return true;
    }

    /**
     * A move's sound plays when the move comes out, like its effects: if a cast near {@code at} is being held for its
     * swing, the sound waits by the same shift (checked a tick later, once the move's carrier is known).
     * Returns true if {@code play} was taken over.
     */
    public static boolean deferSound(Vec3 at, Runnable play) {
        if (WIN.isEmpty() || at == null) return false;
        Win best = null;
        double bestD = 8.0 * 8.0;
        for (Win w : WIN.values()) {
            if (!live(w, w.p.serverLevel().getGameTime())) continue;
            double d = w.p.position().add(0, 1, 0).distanceToSqr(at);
            if (d < bestD) { bestD = d; best = w; }
        }
        if (best == null || best.shift == 0) return false;
        Win w = best;
        AstaFx.later(1, () -> {
            int rest = Math.max(0, w.shift) - 1;
            if (rest <= 0) play.run(); else AstaFx.later(rest, play);
        });
        return true;
    }

    public static void forget(ServerPlayer p) { WIN.remove(p.getUUID()); }
}
