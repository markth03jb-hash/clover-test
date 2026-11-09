package com.ironspellgrimoire.client;

import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/** Which spells would finish a combo right now (spell name hash -> game tick the window ends). Only the wheel reads it. */
public final class ComboHint {
    private ComboHint() {}
    private static final Map<Integer, Long> UNTIL = new HashMap<>();

    public static void set(int hash, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (ticks <= 0) { UNTIL.clear(); return; }
        UNTIL.put(hash, mc.level.getGameTime() + ticks);
    }

    /** 0 = none, otherwise 0..1 pulse strength (fades out over the last second). */
    public static float pulse(String spellName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || UNTIL.isEmpty()) return 0f;
        Long end = UNTIL.get(spellName.hashCode());
        if (end == null) return 0f;
        float left = end - mc.level.getGameTime();
        if (left <= 0) { UNTIL.remove(spellName.hashCode()); return 0f; }
        float t = (System.nanoTime() / 1.0E9F);
        float wave = 0.5f + 0.5f * (float) Math.sin(t * 6.0f);
        return (0.35f + 0.65f * wave) * Math.min(1f, left / 20f);
    }
}
