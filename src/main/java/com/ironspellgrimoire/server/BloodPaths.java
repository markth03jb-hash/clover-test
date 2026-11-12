package com.ironspellgrimoire.server;

import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The Blood Grimoire's evolution paths: at mastery 5 each of its ten spells takes one of two shapes, chosen on the
 * spell's page in the grimoire (and changed there whenever you like). Kept on the book ("BloodPaths": spell -> 1 / 2).
 */
public final class BloodPaths {
    private BloodPaths() {}

    public static final int NEEDS = 5;
    private static final String TAG = "BloodPaths";

    /** spell -> {A name, A text, B name, B text} */
    public static final Map<String, String[]> PATHS = new LinkedHashMap<>();
    static {
        PATHS.put("crimson_edge", new String[]{"Twin Edge", "A second, mirrored arc follows for 60%.", "Long Edge", "The arc reaches half again as far."});
        PATHS.put("hemorrhage", new String[]{"Contagion", "The bleeding spreads to two foes near the mark.", "Triple Needle", "Three needles: the three nearest foes in front of you."});
        PATHS.put("sanguine_volley", new String[]{"Barrage", "Four more needles.", "Piercing", "Needles pass through everything in their path."});
        PATHS.put("transfusion", new String[]{"Deep Draw", "Heals you for all it drains, not 60%.", "Chain", "Also draws from a second foe nearby."});
        PATHS.put("blood_pact", new String[]{"Greater Pact", "+100% instead of +60% - it costs 30% of your health.", "Twin Pact", "Your next two Blood spells are 35% stronger."});
        PATHS.put("red_tide", new String[]{"Riptide", "When the wave has passed, it drags foes back toward you.", "Crimson Flood", "The wave is half again as wide."});
        PATHS.put("thorned_veins", new String[]{"Briar Field", "The thorns strike again a second later.", "Spiked Line", "The line of thorns runs half again as far."});
        PATHS.put("crimson_bulwark", new String[]{"Iron Blood", "+50% shield.", "Thorn Shell", "While it holds, a fifth of every blow strikes back."});
        PATHS.put("exsanguinate", new String[]{"Wide Reach", "Reaches 13 blocks instead of 9.", "Gluttony", "Heals you for all it drains, not half."});
        PATHS.put("scarlet_moon", new String[]{"Long Night", "The Moon lasts 15 s longer.", "Cold Blood", "Ice moves hit 15% harder."});
    }

    /** The path this spell took on this book (0 = none: below mastery 5, or not chosen). */
    public static int path(ItemStack book, String spell) {
        if (book == null || !book.hasTag() || GrimoireMastery.level(book) < NEEDS) return 0;
        return book.getTag().getCompound(TAG).getInt(spell);
    }

    /** The path the player's equipped Blood Grimoire took for this spell. */
    public static int path(ServerPlayer p, String spell) {
        ItemStack b = Utils.getPlayerSpellbookStack(p);
        return b != null && GrimoireMastery.bookOf(b) != null && GrimoireMastery.BOOKS.indexOf(GrimoireMastery.bookOf(b)) == 20 ? path(b, spell) : 0;
    }

    public static boolean is(ServerPlayer p, String spell, int which) { return path(p, spell) == which; }

    /** From the grimoire screen (TreePackets action 11 / 12). */
    public static void choose(ServerPlayer p, String spell, int which) {
        if (!PATHS.containsKey(spell) || (which != 1 && which != 2)) return;
        ItemStack b = Utils.getPlayerSpellbookStack(p);
        if (b == null || GrimoireMastery.bookOf(b) == null || GrimoireMastery.BOOKS.indexOf(GrimoireMastery.bookOf(b)) != 20) return;
        if (GrimoireMastery.level(b) < NEEDS) {
            p.displayClientMessage(Component.literal("Paths open at mastery " + NEEDS + ".").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        CompoundTag t = b.getOrCreateTag().getCompound(TAG);
        t.putInt(spell, which);
        b.getOrCreateTag().put(TAG, t);
        p.displayClientMessage(Component.literal(PATHS.get(spell)[which == 1 ? 0 : 2] + " chosen").withStyle(ChatFormatting.DARK_RED), true);
    }
}
