package com.example.arcaneaddon.network;

import com.example.arcaneaddon.ArcaneAddon;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class AddonNetwork {
    private static final String PROTOCOL = "2";   // 2: compact FxPacket encoding (server and client must match)
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ArcaneAddon.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void init() {
        CHANNEL.registerMessage(0, SummonAnimPacket.class, SummonAnimPacket::encode, SummonAnimPacket::decode, SummonAnimPacket::handle);
        CHANNEL.registerMessage(1, GrimoireOpenPacket.class, GrimoireOpenPacket::encode, GrimoireOpenPacket::decode, GrimoireOpenPacket::handle);
        CHANNEL.registerMessage(2, FxPacket.class, FxPacket::encode, FxPacket::decode, FxPacket::handle);
    }
}
