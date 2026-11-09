package com.ironspellgrimoire.events;

import com.ironspellgrimoire.IronSpellGrimoire;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

/**
 * The mod's id used to be "arcane_addon". Worlds made before the rename keep their items, blocks and grimoire
 * spells: old ids are remapped to the new ones, and the spells stored inside grimoires (and the player's
 * ceremony record) are rewritten the first time a player joins.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class OldNameMigration {
    private OldNameMigration() {}

    private static final String OLD = "arcane" + "_addon";
    private static final String OLD_PREFIX = OLD + ":";

    @SubscribeEvent
    public static void onMissing(MissingMappingsEvent e) {
        for (var m : e.getMappings(Registries.ITEM, OLD)) {
            var v = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath(IronSpellGrimoire.MODID, m.getKey().getPath()));
            if (v != null && v != net.minecraft.world.item.Items.AIR) m.remap(v);
        }
        for (var m : e.getMappings(Registries.BLOCK, OLD)) {
            var v = ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath(IronSpellGrimoire.MODID, m.getKey().getPath()));
            if (v != null && v != net.minecraft.world.level.block.Blocks.AIR) m.remap(v);
        }
        for (var m : e.getMappings(Registries.SOUND_EVENT, OLD)) {
            var v = ForgeRegistries.SOUND_EVENTS.getValue(ResourceLocation.fromNamespaceAndPath(IronSpellGrimoire.MODID, m.getKey().getPath()));
            if (v != null) m.remap(v);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        for (ItemStack s : p.getInventory().items) fix(s);
        for (ItemStack s : p.getInventory().offhand) fix(s);
        for (ItemStack s : p.getInventory().armor) fix(s);
        fix(Utils.getPlayerSpellbookStack(p));
        for (int i = 0; i < p.getEnderChestInventory().getContainerSize(); i++) fix(p.getEnderChestInventory().getItem(i));
        // the ceremony record and other saved values
        CompoundTag all = p.getPersistentData();
        CompoundTag kept = all.getCompound(Player.PERSISTED_NBT_TAG);
        for (CompoundTag t : new CompoundTag[]{all, kept}) {
            for (String k : t.getAllKeys().toArray(new String[0])) {
                if (k.startsWith(OLD)) { t.put(IronSpellGrimoire.MODID + k.substring(OLD.length()), t.get(k).copy()); t.remove(k); }
            }
        }
    }

    private static void fix(ItemStack s) {
        if (s == null || s.isEmpty() || !s.hasTag()) return;
        rewrite(s.getTag());
    }

    /** Every string in the tag that starts with the old id is rewritten to the new one. */
    private static void rewrite(Tag t) {
        if (t instanceof CompoundTag c) {
            for (String k : c.getAllKeys().toArray(new String[0])) {
                Tag v = c.get(k);
                if (v instanceof StringTag st && st.getAsString().startsWith(OLD_PREFIX)) c.putString(k, IronSpellGrimoire.MODID + ":" + st.getAsString().substring(OLD_PREFIX.length()));
                else rewrite(v);
            }
        } else if (t instanceof ListTag l) {
            for (int i = 0; i < l.size(); i++) {
                Tag v = l.get(i);
                if (v instanceof StringTag st && st.getAsString().startsWith(OLD_PREFIX)) l.set(i, StringTag.valueOf(IronSpellGrimoire.MODID + ":" + st.getAsString().substring(OLD_PREFIX.length())));
                else rewrite(v);
            }
        }
    }
}
