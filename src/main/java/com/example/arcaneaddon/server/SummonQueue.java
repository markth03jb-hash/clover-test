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
            net.minecraft.world.phys.Vec3 rh = new net.minecraft.world.phys.Vec3(p.getX() + fx * 0.6 - lx * 0.45, y, p.getZ() + fz * 0.6 - lz * 0.45);
            net.minecraft.world.phys.Vec3 lh = new net.minecraft.world.phys.Vec3(p.getX() + fx * 0.6 + lx * 0.45, y, p.getZ() + fz * 0.6 + lz * 0.45);
            FxSend.beam(sl, 0, rh, lh.add(0, 0.25, 0), 0.22f, 7);    // two blades crossing as they trade hands
            FxSend.beam(sl, 1, lh, rh.add(0, 0.25, 0), 0.22f, 7);
            FxSend.ring(sl, 0, rh, 0.1f, 1.0f, 0.35f, 7);
            FxSend.ring(sl, 1, lh, 0.1f, 1.0f, 0.35f, 7);
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS, 0.9f, 1.3f);
        float power = (float) p.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.SPELL_POWER.get());
        if (p.getMainHandItem().is(AddonItemRegistry.DEMON_DWELLER_SWORD.get())) {
            SlashProjectiles.horizontalSlash(p, 14f * power);   // Slayer (now off hand) cuts a wide horizontal slash with an aura
        } else {
            SlashProjectiles.darkShot(p, 15f * power);          // Dweller (now off hand) fires a dark slash projectile
        }
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
                boolean twinGrab = p.getOffhandItem().is(AddonItemRegistry.DEMON_DWELLER_SWORD.get());
                FxSend.ring(sl, twinGrab ? 1 : 0, new net.minecraft.world.phys.Vec3(p.getX(), y - 0.2, p.getZ()), 0.3f, 2.8f, 0.9f, 10);
                FxSend.ring(sl, 6, new net.minecraft.world.phys.Vec3(p.getX(), y - 0.7, p.getZ()), 0.2f, 1.8f, 0.5f, 8);
                sl.sendParticles(ParticleTypes.CRIT, p.getX(), y, p.getZ(), 14, 0.5, 0.5, 0.5, 0.3);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { PENDING.remove(e.getEntity().getUUID()); OFFHAND.remove(e.getEntity().getUUID()); }
}
