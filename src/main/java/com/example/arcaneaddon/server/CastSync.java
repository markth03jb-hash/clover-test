package com.example.arcaneaddon.server;

import com.example.arcaneaddon.network.AddonNetwork;
import com.example.arcaneaddon.network.FxPacket;
import com.example.arcaneaddon.spells.AnimNames;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * Tells everyone watching that a move's cast animation just started (FxPacket shape 42: id = player, sweep = hash
 * of the animation id). Clients then hold the move's first effects back until the swing actually lands
 * (client/FxSync), so the slash leaves the blade on the frame the blade comes through.
 */
public final class CastSync {
    private CastSync() {}

    public static void cast(ServerPlayer p, AnimationHolder anim) {
        String name = AnimNames.of(anim);
        if (name == null) return;
        FxPacket pkt = new FxPacket();
        pkt.shape = 42; pkt.id = p.getId(); pkt.sweep = name.hashCode();
        AddonNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }
}
