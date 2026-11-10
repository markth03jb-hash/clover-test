package com.ironspellgrimoire.server;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/** Plays a pose on a player for everyone who can see them (client side: client/ArcaneAnimations). */
public final class AnimSend {
    private AnimSend() {}

    // indexes into ArcaneAnimations.POSES
    public static final int TWIN_WHIRLWIND = 0, CROSS_CUT = 1, LUNGE_STAB = 2, DEFLECT = 3, HORIZONTAL_SLASH = 4;
    /** Surpassing Limits awakenings: LIMIT_FIRST + book index (GrimoireMastery.BOOKS order). */
    public static final int LIMIT_FIRST = 5;
    /** Magna's batting swing (fire_magna_special), played over the cast pose. */
    public static final int MAGNA_SWING = 14;
    /** Yami's Equinox: katana drawn level, held, one cut. */
    public static final int EQUINOX = 15;
    /** Julius: arms crossed while rising, then thrown open (limit_time). */
    public static final int TIME_AWAKEN = 16;
    /** Both arms raised overhead, held, then one cut (dark_event_horizon): Jack's Ripper's Horizon. */
    public static final int RIPPER_HORIZON = 17;

    public static void play(ServerPlayer p, int pose) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 5;              // 5 = play pose, handled in FxRenderer.spawn
        pkt.id = p.getId();
        pkt.palette = (byte) pose;
        AddonNetwork.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }
}
