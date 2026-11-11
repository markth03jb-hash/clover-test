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
 *   <li>A star sigil on the cover, one at a time (25 stars to set or change) - "Sigil": the Well (+20% max mana),
 *   Haste (+8% cooldown reduction) or the Spring (+30% mana regeneration).</li>
 *   <li>Mutate a fully empowered spell, once, with a Mana Core (a boss's drop): Echo (each hit lands again for 40%
 *   half a second later) or Siphon (15% of the damage it deals comes back as mana) - "StarMut".</li>
 * </ul>
 */
public final class StarForge {
    private StarForge() {}

    public static final int MAX_BOOST = 3, SLOT_COST = 40, SIGIL_COST = 25;
    public static final String[] SIGIL_NAMES = {"the Well", "Haste", "the Spring"};
    public static final String[] SIGIL_EFFECTS = {"+20% max mana", "+8% cooldowns", "+30% mana regen"};
    private static final java.util.UUID SIGIL_ID = java.util.UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a1001a1");

    public static final int MUT_ECHO = 1, MUT_SIPHON = 2;
    public static final String[] MUT_NAMES = {"", "Echo", "Siphon"};
    public static final String[] MUT_TEXT = {"", "Each hit lands again for 40% half a second later.", "15% of the damage dealt returns as mana."};

    public static int boostCost(int toLevel) { return 10 * toLevel; }

    private static int cores(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(com.ironspellgrimoire.registry.AddonItemRegistry.MANA_CORE.get())) n += s.getCount();
        return n;
    }

    private static ItemStack book(ServerPlayer p) {
        ItemStack b = Utils.getPlayerSpellbookStack(p);
        return b == null || b.isEmpty() || GrimoireMastery.bookOf(b) == null ? null : b;
    }

    public static void open(ServerPlayer p) {
        ItemStack b = book(p);
        if (b == null) { p.displayClientMessage(Component.literal("Equip a grimoire to forge it with stars.").withStyle(ChatFormatting.RED), true); return; }
        CompoundTag t = b.getOrCreateTag(), boost = t.getCompound("StarBoost"), mut = t.getCompound("StarMut");
        List<StarForgePackets.Spell> spells = new ArrayList<>();
        for (String id : GrimoireMastery.bookOf(b).unlocks().keySet()) spells.add(new StarForgePackets.Spell(id, boost.getInt(id), mut.getInt(id)));
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p),
                new StarForgePackets.Open(Bounties.stars(p), t.getBoolean("InkSlot2"), t.getInt("Sigil"), cores(p), b.getHoverName().getString(), spells));
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
            case 2 -> {   // a star sigil on the cover
                int g;
                try { g = Integer.parseInt(arg); } catch (NumberFormatException e) { return; }
                if (g < 0 || g >= SIGIL_NAMES.length || t.getInt("Sigil") == g + 1 || !pay(p, stars, SIGIL_COST)) return;
                t.putInt("Sigil", g + 1);
                sigil(b, g);
                done(p, "Sigil of " + SIGIL_NAMES[g] + " pressed into the cover: " + SIGIL_EFFECTS[g] + ".");
            }
            case 3 -> {   // mutate a fully empowered spell with a Mana Core
                int bar = arg.indexOf('|');
                if (bar < 0) return;
                String id = arg.substring(0, bar);
                int m;
                try { m = Integer.parseInt(arg.substring(bar + 1)); } catch (NumberFormatException e) { return; }
                if ((m != MUT_ECHO && m != MUT_SIPHON) || !GrimoireMastery.bookOf(b).unlocks().containsKey(id)) return;
                if (t.getCompound("StarBoost").getInt(id) < MAX_BOOST || t.getCompound("StarMut").getInt(id) != 0) return;
                ItemStack core = null;
                for (ItemStack s : p.getInventory().items) if (s.is(com.ironspellgrimoire.registry.AddonItemRegistry.MANA_CORE.get())) { core = s; break; }
                if (core == null) { p.displayClientMessage(Component.literal("You need a Mana Core - bosses drop them.").withStyle(ChatFormatting.RED), true); return; }
                if (!p.isCreative()) core.shrink(1);
                CompoundTag mut = t.getCompound("StarMut");
                mut.putInt(id, m);
                t.put("StarMut", mut);
                ServerLevel sl = p.serverLevel();
                FxSend.ring(sl, Fx.CRIMSON, p.position().add(0, 1, 0), 1.6f, 0.2f, 0.08f, 14);
                FxSend.impactFrame(sl, p.position().add(0, 1.2, 0), 2, FxSend.FRAME_LIGHT, 10);
                sl.playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1f, 0.8f);
                done(p, Component.translatable("spell.iron_spell_grimoire." + id).getString() + " mutated: " + MUT_NAMES[m] + ".");
            }
            default -> { return; }
        }
        open(p);   // refresh the forge
    }

    /** The sigil's bonus, as a curio modifier on the book itself (like the Devil Shards), so it works only while equipped. */
    private static void sigil(ItemStack book, int g) {
        CompoundTag tag = book.getOrCreateTag();
        if (tag.contains("CurioAttributeModifiers", net.minecraft.nbt.Tag.TAG_LIST)) {
            tag.getList("CurioAttributeModifiers", net.minecraft.nbt.Tag.TAG_COMPOUND)
                    .removeIf(x -> x instanceof CompoundTag ct && ct.hasUUID("UUID") && ct.getUUID("UUID").equals(SIGIL_ID));
        }
        var attr = g == 0 ? io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get()
                : g == 1 ? io.redspace.ironsspellbooks.api.registry.AttributeRegistry.COOLDOWN_REDUCTION.get()
                : io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MANA_REGEN.get();
        double amt = g == 0 ? 0.2 : g == 1 ? 0.08 : 0.3;
        top.theillusivec4.curios.api.CuriosApi.addModifier(book, attr, "Star Sigil", SIGIL_ID, amt,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE, "spellbook");
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

    /** The mutation (0 none, MUT_ECHO, MUT_SIPHON) of a spell of the equipped grimoire. */
    public static int mutation(ServerPlayer p, String spellId) {
        ItemStack b = Utils.getPlayerSpellbookStack(p);
        if (b == null || !b.hasTag()) return 0;
        int i = spellId.indexOf(':');
        return b.getTag().getCompound("StarMut").getInt(i < 0 ? spellId : spellId.substring(i + 1));
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
