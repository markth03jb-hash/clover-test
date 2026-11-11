package com.ironspellgrimoire.server;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.StarForgePackets;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

/**
 * The Star Forge: at the Grimoire Altar (sneak + right-click, empty hand), spend merit stars on the grimoire you have
 * equipped. Everything is written on the book (NBT), so it goes where the book goes.
 * <ul>
 *   <li>Empower a spell: +10% damage, up to three times per spell (10, 20, 30 stars) - "StarBoost".</li>
 *   <li>A second ink page: the book can hold two Rift Inks at once (40 stars) - "InkSlot2".</li>
 *   <li>A cover gem: its floating aura takes the gem's colour (15 stars, change any time) - "Gem".</li>
 * </ul>
 */
public final class StarForge {
    private StarForge() {}

    public static final int MAX_BOOST = 3, SLOT_COST = 40, GEM_COST = 15;
    public static final String[] GEM_NAMES = {"Ruby", "Sapphire", "Emerald", "Amethyst", "Topaz", "Diamond"};
    /** The aura palette each gem gives. */
    public static final int[] GEM_PALETTES = {18, 19, 25, 17, 8, 6};

    public static int boostCost(int toLevel) { return 10 * toLevel; }

    private static ItemStack book(ServerPlayer p) {
        ItemStack b = Utils.getPlayerSpellbookStack(p);
        return b == null || b.isEmpty() || GrimoireMastery.bookOf(b) == null ? null : b;
    }

    public static void open(ServerPlayer p) {
        ItemStack b = book(p);
        if (b == null) { p.displayClientMessage(Component.literal("Equip a grimoire to forge it with stars.").withStyle(ChatFormatting.RED), true); return; }
        CompoundTag t = b.getOrCreateTag(), boost = t.getCompound("StarBoost");
        List<StarForgePackets.Spell> spells = new ArrayList<>();
        for (String id : GrimoireMastery.bookOf(b).unlocks().keySet()) spells.add(new StarForgePackets.Spell(id, boost.getInt(id)));
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p),
                new StarForgePackets.Open(Bounties.stars(p), t.getBoolean("InkSlot2"), t.getInt("Gem"), b.getHoverName().getString(), spells));
    }

    public static void act(ServerPlayer p, int action, String arg) {
        ItemStack b = book(p);
        if (b == null) return;
        CompoundTag t = b.getOrCreateTag();
        int stars = Bounties.stars(p);
        switch (action) {
            case 0 -> {   // empower a spell
                if (!GrimoireMastery.bookOf(b).unlocks().containsKey(arg)) return;
                CompoundTag boost = t.getCompound("StarBoost");
                int lvl = boost.getInt(arg);
                if (lvl >= MAX_BOOST) return;
                int cost = boostCost(lvl + 1);
                if (!pay(p, stars, cost)) return;
                boost.putInt(arg, lvl + 1);
                t.put("StarBoost", boost);
                done(p, Component.translatable("spell.iron_spell_grimoire." + arg).getString() + " empowered: +" + (lvl + 1) * 10 + "%");
            }
            case 1 -> {   // the second ink page
                if (t.getBoolean("InkSlot2") || !pay(p, stars, SLOT_COST)) return;
                t.putBoolean("InkSlot2", true);
                done(p, "A second ink page is bound into the grimoire.");
            }
            case 2 -> {   // a cover gem
                int g;
                try { g = Integer.parseInt(arg); } catch (NumberFormatException e) { return; }
                if (g < 0 || g >= GEM_NAMES.length || t.getInt("Gem") == g + 1 || !pay(p, stars, GEM_COST)) return;
                t.putInt("Gem", g + 1);
                done(p, GEM_NAMES[g] + " set into the cover.");
            }
            default -> { return; }
        }
        open(p);   // refresh the forge
    }

    private static boolean pay(ServerPlayer p, int have, int cost) {
        if (have < cost) { p.displayClientMessage(Component.literal("Not enough merit stars (" + cost + " needed).").withStyle(ChatFormatting.RED), true); return false; }
        Bounties.addStars(p, -cost);
        return true;
    }

    private static void done(ServerPlayer p, String what) {
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, Fx.GOLD, p.position().add(0, 0.1, 0), 0.3f, 2.5f, 0.08f, 12);
        FxLib.halo(sl, Fx.GOLD, p.position().add(0, p.getBbHeight() + 0.4, 0), 0.7f);
        sl.playSound(null, p.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6f, 1.6f);
        sl.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.2f);
        p.displayClientMessage(Component.literal(what).withStyle(ChatFormatting.GOLD), true);
    }

    /** How much harder a spell from the equipped grimoire hits for its star empowerment. */
    public static float boost(ServerPlayer p, String spellId) {
        ItemStack b = Utils.getPlayerSpellbookStack(p);
        if (b == null || !b.hasTag()) return 1f;
        int i = spellId.indexOf(':');
        int lvl = b.getTag().getCompound("StarBoost").getInt(i < 0 ? spellId : spellId.substring(i + 1));
        return 1f + 0.1f * lvl;
    }
}
