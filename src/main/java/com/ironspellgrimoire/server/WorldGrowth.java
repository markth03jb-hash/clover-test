package com.ironspellgrimoire.server;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The world grows with its mages: each bounty boss brought down for the first time anywhere on the server wakes
 * something new in the world (saved with the overworld, "isg_world_growth"):
 * <ul>
 *   <li>The Revenant - packs of the restless: pack leaders can now appear even in the safe lands (rank 0).</li>
 *   <li>The Curse Witch - the Overseer trait; the Ossuarch - Unyielding; Rhya - the Blinker (rank 4-5 elites).</li>
 *   <li>The Beast of Despair - packs grow: a leader brings four followers instead of three.</li>
 *   <li>Licht - champions turn up twice as often at rank 4-5.</li>
 * </ul>
 */
public final class WorldGrowth {
    private WorldGrowth() {}

    public static final String[] WAKES = {
            "Unyielding elites now walk the far lands.",
            "Packs grow larger: a leader brings four.",
            "Blinker elites now walk the far lands.",
            "Champions stir twice as often in the far lands.",
            "Packs of the restless now gather even near spawn.",
            "Overseer elites now walk the far lands.",
            ""};

    static final class Data extends SavedData {
        int mask;
        static Data load(CompoundTag t) { Data d = new Data(); d.mask = t.getInt("Mask"); return d; }
        @Override public CompoundTag save(CompoundTag t) { t.putInt("Mask", mask); return t; }
    }

    static Data data(MinecraftServer s) { return s.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "isg_world_growth"); }

    public static boolean fallen(MinecraftServer s, int boss) { return s != null && (data(s).mask & (1 << boss)) != 0; }

    /** A bounty boss down: the first time on this server, the world changes and everyone hears. */
    static void bossFell(MinecraftServer s, int boss) {
        if (s == null || boss < 0 || boss >= 7) return;
        Data d = data(s);
        if ((d.mask & (1 << boss)) != 0) return;
        d.mask |= 1 << boss;
        d.setDirty();
        if (WAKES[boss].isEmpty()) return;
        Component c = Component.literal("THE WORLD STIRS  ").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal(Bounties.NAMES[boss] + " has fallen. " + WAKES[boss]).withStyle(ChatFormatting.LIGHT_PURPLE));
        for (var p : s.getPlayerList().getPlayers()) p.sendSystemMessage(c);
    }

    /** Whether a late elite trait may roll: its boss has fallen on this server. */
    public static boolean traitOpen(MinecraftServer s, int trait) {
        return switch (trait) {
            case EliteMobs.OVERSEER -> fallen(s, 5);
            case EliteMobs.UNYIELDING -> fallen(s, 0);
            case EliteMobs.BLINKER -> fallen(s, 2);
            default -> true;
        };
    }

    public static void testSet(MinecraftServer s, int mask) { data(s).mask = mask; data(s).setDirty(); }
    public static int testMask(MinecraftServer s) { return data(s).mask; }
}
