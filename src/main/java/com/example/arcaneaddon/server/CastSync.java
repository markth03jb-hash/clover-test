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

    private static final class Win { ServerPlayer p; long start; int impact; int shift = -1; long lastLaunch; }

    private static final Map<UUID, Win> WIN = new HashMap<>();
    private static boolean bypass = false;

    public static void cast(ServerPlayer p, AnimationHolder anim) {
        String name = AnimNames.of(anim);
        if (name == null) return;
        int impact = AnimImpact.of(name);
        long now = p.serverLevel().getGameTime();
        if (impact > 0) {
            Win w = new Win();
            w.p = p; w.start = now; w.impact = impact; w.lastLaunch = now;
            WIN.put(p.getUUID(), w);
        } else WIN.remove(p.getUUID());
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

    /** Same rule as the client: the effects that "launch" a move (not held pieces re-sent every tick). */
    private static boolean launch(FxPacket p) {
        return switch (p.shape) {
            case 13, 1, 41, 23, 29, 33, 0, 10, 12 -> true;
            case 2, 7, 8, 19, 20, 30 -> p.life > 1;
            default -> false;
        };
    }

    /** Called for every effect packet the server sends (AddonNetwork.send). */
    public static void onSend(FxPacket p) {
        if (WIN.isEmpty() || !launch(p) || p.c == null || p.c.equals(Vec3.ZERO)) return;
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
    }

    /**
     * Deal the move's damage on the swing: returns true if the damage was taken over (it will be dealt by
     * {@code deal} after the shift), false if it should be dealt right now.
     */
    public static boolean deferDamage(ServerPlayer p, Runnable deal) {
        if (bypass || WIN.isEmpty()) return false;
        Win w = WIN.get(p.getUUID());
        if (w == null) return false;
        long now = p.serverLevel().getGameTime();
        if (!live(w, now)) { WIN.remove(p.getUUID()); return false; }
        int s = decide(w, now);
        if (s <= 0) return false;
        AstaFx.later(s, () -> {
            bypass = true;
            try { deal.run(); } finally { bypass = false; }
        });
        return true;
    }

    public static void forget(ServerPlayer p) { WIN.remove(p.getUUID()); }
}
