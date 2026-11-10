package com.ironspellgrimoire.spells;

/** What the client last heard of this player's Limbus resources (the server's own numbers live in LimbusStatus). */
public final class LimbusGate {
    private LimbusGate() {}

    public static volatile int clientFortune = 0;

    /** Furioso: only with a full Fortune (100). */
    public static boolean fortuneFull(net.minecraft.world.entity.player.Player p) {
        if (p instanceof net.minecraft.server.level.ServerPlayer sp) return com.ironspellgrimoire.server.LimbusStatus.fortune(sp) >= 100;
        return clientFortune >= 100;
    }
}
