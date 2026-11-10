package com.ironspellgrimoire.client;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** The bridge the Grimoire Altar block calls from animateTick (client side only). */
public final class AltarClient {
    private AltarClient() {}

    public static void seen(BlockPos pos, long now) { AltarFx.seen(pos, now); }

    /** A mote of light drifting up out of the bowl. */
    public static void mote(BlockPos pos, RandomSource r) {
        Vec3 at = new Vec3(pos.getX() + 0.5 + (r.nextDouble() - 0.5) * 0.7, pos.getY() + 0.85, pos.getZ() + 0.5 + (r.nextDouble() - 0.5) * 0.7);
        DarkSmoke.mote(at, new Vec3(0, 0.025 + r.nextDouble() * 0.02, 0), 0.04F, 24);
    }
}
