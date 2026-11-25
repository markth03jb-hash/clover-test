package com.ironspellgrimoire.client;

import net.minecraft.nbt.CompoundTag;

/** What the client knows of its own boss ladder (server/BossLadder), for the Codex. */
public final class BossLogClient {
    private BossLogClient() {}
    public static CompoundTag data = new CompoundTag();

    public static int[] arr(String k) {
        int[] a = data.getIntArray(k);
        if (a.length < 7) { int[] b = new int[7]; System.arraycopy(a, 0, b, 0, a.length); a = b; }
        return a;
    }
}
