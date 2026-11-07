package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.network.AddonNetwork;
import com.example.arcaneaddon.network.SummonAnimPacket;
import com.example.arcaneaddon.registry.AddonItemRegistry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.joml.Vector3f;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cast -> the client plays the "sword rises out of the grimoire" animation ->
 * DELAY_TICKS later the real sword is placed in the (still empty) main hand = "you grab it".
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public class SummonQueue {
    public static final int DELAY_TICKS = 16;
    private static final DustParticleOptions CRIMSON = new DustParticleOptions(new Vector3f(0.8f, 0.05f, 0.1f), 1.6f);
    private static final Map<UUID, Integer> PENDING = new HashMap<>();
    private static final Map<UUID, Boolean> OFFHAND = new HashMap<>();   // true = the twin Demon Dweller Sword goes to the off hand

    public static boolean isPending(Player p) { return PENDING.containsKey(p.getUUID()); }

    public static void start(ServerPlayer p, boolean offhand) {
        PENDING.put(p.getUUID(), DELAY_TICKS);
        OFFHAND.put(p.getUUID(), offhand);
        AddonNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new SummonAnimPacket(p.getId(), offhand));
        p.level().playSound(null, p.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    /** Both swords out: trade them between the main hand and the off hand. */
    public static void swap(ServerPlayer p) {
        ItemStack main = p.getMainHandItem().copy(), off = p.getOffhandItem().copy();
        p.setItemInHand(InteractionHand.MAIN_HAND, off);
        p.setItemInHand(InteractionHand.OFF_HAND, main);
        if (p.level() instanceof ServerLevel sl) {
            double yr = Math.toRadians(p.getYRot());
            double lx = Math.cos(yr), lz = Math.sin(yr), fx = -Math.sin(yr), fz = Math.cos(yr);
            double y = p.getY() + p.getEyeHeight() * 0.7;
            for (int i = 0; i <= 10; i++) {   // two streaks of crimson crossing between the hands
                double t = i / 10.0, k = -0.45 + 0.9 * t;
                sl.sendParticles(CRIMSON, p.getX() + fx * 0.6 + lx * k, y + Math.sin(t * Math.PI) * 0.25, p.getZ() + fz * 0.6 + lz * k, 1, 0, 0, 0, 0);
                sl.sendParticles(CRIMSON, p.getX() + fx * 0.6 - lx * k, y - Math.sin(t * Math.PI) * 0.25, p.getZ() + fz * 0.6 - lz * k, 1, 0, 0, 0, 0);
            }
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX() + fx * 0.6 + lx * 0.45, y, p.getZ() + fz * 0.6 + lz * 0.45, 8, 0.1, 0.2, 0.1, 0.03);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX() + fx * 0.6 - lx * 0.45, y, p.getZ() + fz * 0.6 - lz * 0.45, 8, 0.1, 0.2, 0.1, 0.03);
            sl.sendParticles(ParticleTypes.CRIT, p.getX() + fx * 0.6, y, p.getZ() + fz * 0.6, 12, 0.4, 0.2, 0.4, 0.3);
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS, 0.9f, 1.3f);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.5f);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Integer left = PENDING.get(p.getUUID());
        if (left == null) return;
        if (--left > 0) { PENDING.put(p.getUUID(), left); return; }
        PENDING.remove(p.getUUID());
        boolean off = Boolean.TRUE.equals(OFFHAND.remove(p.getUUID()));
        boolean ok = p.isAlive() && (off
                ? p.getMainHandItem().is(AddonItemRegistry.DEMON_SLAYER_SWORD.get()) && p.getOffhandItem().isEmpty()
                : p.getMainHandItem().isEmpty());
        if (ok) {
            if (off) p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AddonItemRegistry.DEMON_DWELLER_SWORD.get()));
            else p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AddonItemRegistry.DEMON_SLAYER_SWORD.get()));
            p.level().playSound(null, p.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.6f, 0.5f);
            p.level().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS, 1.0f, 0.6f);
            p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 0.6f);
            if (p.level() instanceof ServerLevel sl) {
                double y = p.getY() + 1.1;
                sl.sendParticles(ParticleTypes.FLASH, p.getX(), y, p.getZ(), 1, 0, 0, 0, 0);
                sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), y, p.getZ(), 40, 0.45, 0.55, 0.45, 0.05);
                sl.sendParticles(ParticleTypes.CRIT, p.getX(), y, p.getZ(), 25, 0.5, 0.5, 0.5, 0.3);
                for (int i = 0; i < 40; i++) {
                    double a = i * Math.PI * 2 / 40;
                    sl.sendParticles(CRIMSON, p.getX() + Math.cos(a) * 0.5, y, p.getZ() + Math.sin(a) * 0.5, 0, Math.cos(a), 0.02, Math.sin(a), 0.35);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { PENDING.remove(e.getEntity().getUUID()); OFFHAND.remove(e.getEntity().getUUID()); }
}
