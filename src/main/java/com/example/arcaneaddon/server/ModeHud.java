package com.example.arcaneaddon.server;

import com.example.arcaneaddon.network.AddonNetwork;
import com.example.arcaneaddon.network.FxPacket;
import com.example.arcaneaddon.network.ModeIds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/** Mode timers on the caster's HUD, like vanilla effect timers: icon, mode name and time left. */
public final class ModeHud {
    private ModeHud() {}

    /** Show (or refresh) a mode's timer with {@code ticks} left; 0 removes it. Only the caster sees it. */
    public static void show(ServerPlayer p, String spellId, int ticks) {
        int i = ModeIds.index(spellId);
        if (i < 0 || p == null || p.connection == null) return;
        FxPacket pkt = new FxPacket();
        pkt.shape = 32; pkt.id = p.getId(); pkt.palette = (byte) i; pkt.life = Math.max(0, ticks);
        AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
    }

    public static void hide(ServerPlayer p, String spellId) { show(p, spellId, 0); }
}
