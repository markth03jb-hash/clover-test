package com.ironspellgrimoire.server;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.network.ModeIds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/** Mode timers on the caster's HUD, like vanilla effect timers: icon, mode name and time left. */
public final class ModeHud {
    private ModeHud() {}

    /** Show (or refresh) a mode's timer with {@code ticks} left; 0 removes it. Only the caster sees it. */
    public static void show(ServerPlayer p, String spellId, int ticks) { show(p, spellId, ticks, -1); }

    /**
     * Show (or refresh) a mode's timer with {@code ticks} left and {@code count} hits/dodges/shield left (-1 = none).
     * Use this for anything with charges, so the HUD shows both how long it lasts and how many are left.
     */
    public static void show(ServerPlayer p, String spellId, int ticks, int count) {
        int i = ModeIds.index(spellId);
        if (i < 0 || p == null || p.connection == null) return;
        java.util.Map<String, Long> m = ACTIVE.computeIfAbsent(p.getUUID(), k -> new java.util.HashMap<>());
        if (ticks > 0) m.put(spellId, p.serverLevel().getGameTime() + ticks); else m.remove(spellId);
        FxPacket pkt = new FxPacket();
        pkt.shape = 32; pkt.id = p.getId(); pkt.palette = (byte) i; pkt.life = Math.max(0, ticks); pkt.sweep = count;
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), pkt);
    }

    private static final java.util.Map<java.util.UUID, java.util.Map<String, Long>> ACTIVE = new java.util.concurrent.ConcurrentHashMap<>();

    /** The modes this player has running right now (spell ids). */
    public static java.util.List<String> active(ServerPlayer p) {
        java.util.Map<String, Long> m = ACTIVE.get(p.getUUID());
        if (m == null) return java.util.List.of();
        long now = p.serverLevel().getGameTime();
        m.values().removeIf(end -> end <= now);
        return new java.util.ArrayList<>(m.keySet());
    }

    public static void hide(ServerPlayer p, String spellId) { show(p, spellId, 0); }
}
