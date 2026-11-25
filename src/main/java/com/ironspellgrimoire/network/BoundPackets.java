package com.ironspellgrimoire.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** The Grimoire of the Bound Demon (server/BoundFx): a mage's awakening and takeover, for everyone who can see them (the wing, the eye). */
public final class BoundPackets {
    private BoundPackets() {}

    public static class State {
        public final int id, awake;
        public final boolean possessed, fallen, six;
        public State(int id, int awake, boolean possessed, boolean fallen, boolean six) { this.id = id; this.awake = awake; this.possessed = possessed; this.fallen = fallen; this.six = six; }
        public static void encode(State m, FriendlyByteBuf b) { b.writeVarInt(m.id); b.writeVarInt(m.awake); b.writeByte((m.possessed ? 1 : 0) | (m.fallen ? 2 : 0) | (m.six ? 4 : 0)); }
        public static State decode(FriendlyByteBuf b) { int id = b.readVarInt(), aw = b.readVarInt(), f = b.readByte(); return new State(id, aw, (f & 1) != 0, (f & 2) != 0, (f & 4) != 0); }
        public static void handle(State m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.BoundClient.state(m)));
            ctx.get().setPacketHandled(true);
        }
    }
}
