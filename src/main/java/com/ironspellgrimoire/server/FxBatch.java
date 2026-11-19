package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxBatchPacket;
import com.ironspellgrimoire.network.FxPacket;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Effect packets are queued per player and sent together at the end of the server tick: one message instead of
 * dozens (a big spell draws 5-15 pieces a tick, and every message carries its own channel header). The client draws
 * exactly the same thing in the same tick. Anything sent the old way flushes the queue first, so order is kept.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class FxBatch {
    private FxBatch() {}

    private static final int MAX = 96;   // pieces per message; more are split into further messages
    private static final Map<ServerPlayer, List<FxPacket>> QUEUE = new IdentityHashMap<>();
    /** Network messages carrying effects (batches and single sends); the game tests read this next to fxSent. */
    public static int messages;

    /** Queue one effect for every player within {@code radius} of {@code at} in that level. */
    public static void near(ServerLevel sl, Vec3 at, double radius, FxPacket p) {
        if (!sl.getServer().isSameThread()) {   // off the server thread: send straight away
            AddonNetwork.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, radius, sl.dimension())), p);
            return;
        }
        AddonNetwork.noteFx(p);
        double r2 = radius * radius;
        for (ServerPlayer sp : sl.players()) if (sp.distanceToSqr(at) <= r2) QUEUE.computeIfAbsent(sp, k -> new ArrayList<>()).add(p);
    }

    /** Send everything queued. */
    public static void flush() {
        if (QUEUE.isEmpty()) return;
        List<Map.Entry<ServerPlayer, List<FxPacket>>> all = new ArrayList<>(QUEUE.entrySet());
        QUEUE.clear();
        for (var e : all) {
            ServerPlayer sp = e.getKey();
            if (sp.hasDisconnected()) continue;
            List<FxPacket> l = e.getValue();
            for (int i = 0; i < l.size(); i += MAX) {
                List<FxPacket> part = new ArrayList<>(l.subList(i, Math.min(l.size(), i + MAX)));
                AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new FxBatchPacket(part));
                messages++;
            }
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) flush();
    }
}
