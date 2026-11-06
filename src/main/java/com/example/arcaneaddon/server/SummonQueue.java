package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.network.AddonNetwork;
import com.example.arcaneaddon.network.SummonAnimPacket;
import com.example.arcaneaddon.registry.AddonItemRegistry;
import net.minecraft.core.particles.ParticleTypes;
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
    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    public static boolean isPending(Player p) { return PENDING.containsKey(p.getUUID()); }

    public static void start(ServerPlayer p) {
        PENDING.put(p.getUUID(), DELAY_TICKS);
        AddonNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new SummonAnimPacket(p.getId()));
        p.level().playSound(null, p.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Integer left = PENDING.get(p.getUUID());
        if (left == null) return;
        if (--left > 0) { PENDING.put(p.getUUID(), left); return; }
        PENDING.remove(p.getUUID());
        if (p.isAlive() && p.getMainHandItem().isEmpty()) {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AddonItemRegistry.SUMMONED_GREATSWORD.get()));
            p.level().playSound(null, p.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.6f, 0.5f);
            if (p.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1.2, p.getZ(), 20, 0.4, 0.5, 0.4, 0.03);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { PENDING.remove(e.getEntity().getUUID()); }
}
