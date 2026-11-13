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
        PATHS.put("crimson_edge", new String[]{"Twin Edge", "A mirrored 2nd arc (60%)", "Long Edge", "50% longer reach"});
        PATHS.put("hemorrhage", new String[]{"Contagion", "Bleed spreads to 2 foes", "Triple Needle", "Hits the 3 nearest foes"});
        PATHS.put("sanguine_volley", new String[]{"Barrage", "4 more needles", "Piercing", "Needles pass through"});
        PATHS.put("transfusion", new String[]{"Deep Draw", "Heals all it drains", "Chain", "Drains a 2nd foe too"});
        PATHS.put("blood_pact", new String[]{"Greater Pact", "+100%, costs 30% health", "Twin Pact", "Next 2 spells +35%"});
        PATHS.put("red_tide", new String[]{"Riptide", "Drags foes back to you", "Crimson Flood", "50% wider wave"});
        PATHS.put("thorned_veins", new String[]{"Briar Field", "Thorns strike twice", "Spiked Line", "50% longer line"});
        PATHS.put("crimson_bulwark", new String[]{"Iron Blood", "+50% shield", "Thorn Shell", "20% of blows strike back"});
        PATHS.put("exsanguinate", new String[]{"Wide Reach", "Reaches 13 blocks", "Gluttony", "Heals all it drains"});
        PATHS.put("scarlet_moon", new String[]{"Long Night", "The Moon lasts +15 s", "Cold Blood", "Ice moves +15% damage"});
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
        ItemStack b = SkillTreeServer.findBook(p, 20);   // equipped, or in hand
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
