package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The way to the Grimoire Tower. The first time a player joins, they're handed a compass pointing to the nearest
 * Grimoire Tower (found once, from where they stand - "isg_tower_compass"). When they reach it - within 24 blocks -
 * the compass crumbles away; it also goes if a grimoire has already chosen them.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class TowerCompass {
    private TowerCompass() {}

    private static final TagKey<Structure> TOWER = TagKey.create(Registries.STRUCTURE, new ResourceLocation(IronSpellGrimoire.MODID, "grimoire_tower"));

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.getPersistentData().getBoolean("isg_tower_compass")) return;
        p.getPersistentData().putBoolean("isg_tower_compass", true);
        if (GrimoireCeremony.chosen(p) >= 0) return;   // already chosen: no need
        ServerLevel ow = p.server.getLevel(Level.OVERWORLD);
        if (ow == null) return;
        BlockPos from = p.level().dimension().equals(Level.OVERWORLD) ? p.blockPosition() : ow.getSharedSpawnPos();
        BlockPos tower = ow.findNearestMapStructure(TOWER, from, 100, false);
        if (tower == null) return;
        ItemStack c = new ItemStack(AddonItemRegistry.TOWER_COMPASS.get());
        c.getOrCreateTag().putInt("TowerX", tower.getX());
        c.getOrCreateTag().putInt("TowerZ", tower.getZ());
        if (!p.getInventory().add(c)) p.drop(c, false);
        double dx = tower.getX() - p.getX(), dz = tower.getZ() - p.getZ();
        p.sendSystemMessage(Component.literal("A compass for a new mage. ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("It points to the Grimoire Tower - about " + (int) Math.hypot(dx, dz) + " blocks " + Bounties.direction(dx, dz)
                        + ". Climb it, and a grimoire may choose you.").withStyle(ChatFormatting.YELLOW)));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 9) return;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(AddonItemRegistry.TOWER_COMPASS.get()) || (s.hasTag() && s.getTag().contains("Memory"))) continue;   // a memory's compass (server/MemoryPages) stays
            boolean there = false;
            if (s.hasTag() && p.level().dimension().equals(Level.OVERWORLD)) {
                double dx = s.getTag().getInt("TowerX") + 0.5 - p.getX(), dz = s.getTag().getInt("TowerZ") + 0.5 - p.getZ();
                there = dx * dx + dz * dz < 24 * 24;
            }
            if (there || GrimoireCeremony.chosen(p) >= 0) {
                inv.setItem(i, ItemStack.EMPTY);
                p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8f, 0.8f);
                if (there) p.displayClientMessage(Component.literal("You've reached the Grimoire Tower - the compass crumbles away.").withStyle(ChatFormatting.GOLD), true);
            }
        }
    }
}
