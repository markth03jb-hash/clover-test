package com.ironspellgrimoire.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Server -> client: the First Steps tracker (server/FirstSteps, client/FirstStepsHud). flags: 1 shown, 2 Codex read, 4 tower reached, 8 tower known. */
public class FirstStepsPacket {
    public final int flags, tx, tz;

    public FirstStepsPacket(int flags, int tx, int tz) { this.flags = flags; this.tx = tx; this.tz = tz; }

    public static void encode(FirstStepsPacket m, FriendlyByteBuf b) { b.writeByte(m.flags); b.writeInt(m.tx); b.writeInt(m.tz); }
    public static FirstStepsPacket decode(FriendlyByteBuf b) { return new FirstStepsPacket(b.readByte(), b.readInt(), b.readInt()); }

    public static void handle(FirstStepsPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.ironspellgrimoire.client.FirstStepsHud.set(m.flags, m.tx, m.tz)));
        ctx.get().setPacketHandled(true);
    }
}
