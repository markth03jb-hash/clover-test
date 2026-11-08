package com.example.arcaneaddon.spells;

import io.redspace.ironsspellbooks.api.util.AnimationHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Remembers which animation id every spell's AnimationHolder was made from, so the cast can tell clients which
 * animation is playing (they use it to time the move's effects to the swing - see client/FxSync).
 */
public final class AnimNames {
    private AnimNames() {}

    private static final Map<AnimationHolder, String> NAMES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Integer, String> BY_HASH = Collections.synchronizedMap(new HashMap<>());

    /** Same as {@code com.example.arcaneaddon.spells.AnimNames.holder(id)}, but remembered. */
    public static AnimationHolder holder(String id) {
        AnimationHolder h = new AnimationHolder(id, true, true);
        NAMES.put(h, id);
        BY_HASH.put(id.hashCode(), id);
        return h;
    }

    public static String of(AnimationHolder h) { return h == null ? null : NAMES.get(h); }

    public static String byHash(int hash) { return BY_HASH.get(hash); }
}
