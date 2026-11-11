package com.ironspellgrimoire.network;

import com.ironspellgrimoire.IronSpellGrimoire;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class AddonNetwork {
    private static final String PROTOCOL = "4";   // 2: compact FxPacket encoding (server and client must match)
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(IronSpellGrimoire.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    /** Send a packet; effect packets are also shown to CastSync so damage can follow the move's swing. */
    public static <M> void send(net.minecraftforge.network.PacketDistributor.PacketTarget target, M msg) {
        if (msg instanceof FxPacket fx) {
            com.ironspellgrimoire.server.CastSync.onSend(fx);
            if (worldShape(fx.shape) && fromMeteor()) fx.palette = (byte) (fx.palette | FxPacket.PIXEL_ONLY);   // Black Meteorite: all pixel style
        }
        CHANNEL.send(target, msg);
    }

    private static boolean worldShape(int s) {
        return s == 0 || s == 1 || s == 2 || s == 7 || s == 8 || s == 13 || s == 19 || s == 20 || s == 23 || s == 28 || s == 29 || s == 30 || s == 33;
    }

    private static final StackWalker WALKER = StackWalker.getInstance();

    /** True when Black Meteorite (MeteorFx, including its scheduled steps) is the one sending. */
    private static boolean fromMeteor() {
        return WALKER.walk(st -> st.limit(14).anyMatch(f -> f.getClassName().startsWith("com.ironspellgrimoire.server.MeteorFx")));
    }

    public static void init() {
        CHANNEL.registerMessage(0, SummonAnimPacket.class, SummonAnimPacket::encode, SummonAnimPacket::decode, SummonAnimPacket::handle);
        CHANNEL.registerMessage(1, GrimoireOpenPacket.class, GrimoireOpenPacket::encode, GrimoireOpenPacket::decode, GrimoireOpenPacket::handle);
        CHANNEL.registerMessage(2, FxPacket.class, FxPacket::encode, FxPacket::decode, FxPacket::handle);
        CHANNEL.registerMessage(3, WaypointPackets.Open.class, WaypointPackets.Open::encode, WaypointPackets.Open::decode, WaypointPackets.Open::handle);
        CHANNEL.registerMessage(4, WaypointPackets.Act.class, WaypointPackets.Act::encode, WaypointPackets.Act::decode, WaypointPackets.Act::handle);
        CHANNEL.registerMessage(5, StarForgePackets.Open.class, StarForgePackets.Open::encode, StarForgePackets.Open::decode, StarForgePackets.Open::handle);
        CHANNEL.registerMessage(6, StarForgePackets.Act.class, StarForgePackets.Act::encode, StarForgePackets.Act::decode, StarForgePackets.Act::handle);
        CHANNEL.registerMessage(7, AltarPackets.Open.class, AltarPackets.Open::encode, AltarPackets.Open::decode, AltarPackets.Open::handle);
        CHANNEL.registerMessage(8, AltarPackets.Act.class, AltarPackets.Act::encode, AltarPackets.Act::decode, AltarPackets.Act::handle);
    }
}
