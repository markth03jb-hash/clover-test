package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.network.AddonNetwork;
import com.example.arcaneaddon.network.SummonAnimPacket;
import com.example.arcaneaddon.registry.AddonItemRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
    private static final Map<UUID, Boolean> OFFHAND = new HashMap<>();   // true = the twin Demon Dweller Sword goes to the off hand

    public static boolean isPending(Player p) { return PENDING.containsKey(p.getUUID()); }

    public static void start(ServerPlayer p, boolean offhand) {
        PENDING.put(p.getUUID(), DELAY_TICKS);
        OFFHAND.put(p.getUUID(), offhand);
        AddonNetwork.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new SummonAnimPacket(p.getId(), offhand));
        p.level().playSound(null, p.blockPosition(), Sfx.of(Sfx.ASTA, net.minecraft.sounds.SoundEvents.SOUL_ESCAPE), SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    /** Swap attacks can fire at most every 4 s; swapping itself stays instant so you can still change stance. */
    public static final int SWAP_ATTACK_COOLDOWN = 80;
    private static final Map<UUID, Long> LAST_SWAP_ATTACK = new HashMap<>();

    /**
     * Twin Swap: flip the swords between your hands. The blade that lands in your off hand strikes as it passes:
     * <ul>
     *   <li>Dweller to main hand: the Slayer spins a full 360 degree <b>Whirlwind</b> cut, then the slash aura flies ahead.</li>
     *   <li>Slayer to main hand: the Dweller looses a <b>Volley</b> of three dark crescents in a fan.</li>
     * </ul>
     * Every swap also gives a short burst of speed (Twin Momentum).
     */
    public static void swap(ServerPlayer p) { swap(p, false); }

    /** devil = Devil Twin Swap: the strike uses both swords at once (whirlwind AND volley), in ember. */
    public static void swap(ServerPlayer p, boolean devil) {
        ItemStack main = p.getMainHandItem().copy(), off = p.getOffhandItem().copy();
        p.setItemInHand(InteractionHand.MAIN_HAND, off);
        p.setItemInHand(InteractionHand.OFF_HAND, main);
        ServerLevel sl = p.serverLevel();
        boolean toDweller = p.getMainHandItem().is(AddonItemRegistry.DEMON_DWELLER_SWORD.get());

        // the swap itself: both blades flash across your body in an X, with a ring per hand
        double yr = Math.toRadians(p.getYRot());
        double lx = Math.cos(yr), lz = Math.sin(yr), fx = -Math.sin(yr), fz = Math.cos(yr);
        double y = p.getY() + p.getEyeHeight() * 0.7;
        Vec3 rh = new Vec3(p.getX() + fx * 0.6 - lx * 0.45, y, p.getZ() + fz * 0.6 - lz * 0.45);
        Vec3 lh = new Vec3(p.getX() + fx * 0.6 + lx * 0.45, y, p.getZ() + fz * 0.6 + lz * 0.45);
        FxSend.beam(sl, 0, rh.add(0, -0.4, 0), lh.add(0, 0.6, 0), 0.26f, 8);
        FxSend.beam(sl, 1, lh.add(0, -0.4, 0), rh.add(0, 0.6, 0), 0.26f, 8);
        FxSend.ring(sl, toDweller ? 1 : 0, rh, 0.1f, 1.1f, 0.35f, 7);
        FxSend.ring(sl, toDweller ? 0 : 1, lh, 0.1f, 1.1f, 0.35f, 7);
        Fx.flash(sl, new Vec3(p.getX(), y, p.getZ()), 0.9f);
        p.level().playSound(null, p.blockPosition(), Sfx.of(Sfx.ASTA, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE), SoundSource.PLAYERS, 0.9f, 1.3f);
        p.level().playSound(null, p.blockPosition(), Sfx.of(Sfx.ASTA, net.minecraft.sounds.SoundEvents.CHAIN_PLACE), SoundSource.PLAYERS, 1.0f, 0.7f);

        // Twin Momentum
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, TimedModifiers.MOMENTUM_SPEED, "Twin Momentum", 0.4, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);

        long now = sl.getGameTime();
        Long last = LAST_SWAP_ATTACK.get(p.getUUID());
        if (last != null && now - last < (devil ? SWAP_ATTACK_COOLDOWN * 3 / 4 : SWAP_ATTACK_COOLDOWN)) {   // swap only, no strike yet: a quick flip of the blades
            AnimSend.play(p, AnimSend.DEFLECT);
            return;
        }
        LAST_SWAP_ATTACK.put(p.getUUID(), now);

        float power = (float) p.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.SPELL_POWER.get());
        AnimSend.play(p, toDweller ? AnimSend.TWIN_WHIRLWIND : AnimSend.HORIZONTAL_SLASH);   // spin / sweeping volley throw
        if (devil) {   // both swords strike: the Slayer spins, the Dweller fires
            SlashProjectiles.whirlSlash(p, 14f * power);
            SlashProjectiles.darkShot(p, 15f * power, 0);
            SlashProjectiles.darkShot(p, 9f * power, -16);
            SlashProjectiles.darkShot(p, 9f * power, 16);
            FxLib.spinSlash(sl, 9, p.position().add(0, 1, 0), p.getLookAngle(), 1.3f);
            return;
        }
        if (toDweller) {
            SlashProjectiles.whirlSlash(p, 14f * power);           // Slayer Whirlwind
        } else {
            SlashProjectiles.darkShot(p, 15f * power, 0);          // Dweller Volley: centre blade at full damage...
            SlashProjectiles.darkShot(p, 9f * power, -16);         // ...and two side blades
            SlashProjectiles.darkShot(p, 9f * power, 16);
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
            p.level().playSound(null, p.blockPosition(), Sfx.of(Sfx.ASTA, net.minecraft.sounds.SoundEvents.ANVIL_PLACE), SoundSource.PLAYERS, 0.6f, 0.5f);
            p.level().playSound(null, p.blockPosition(), Sfx.of(Sfx.ASTA, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE), SoundSource.PLAYERS, 1.0f, 0.6f);
            p.level().playSound(null, p.blockPosition(), Sfx.of(Sfx.ASTA, net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE), SoundSource.PLAYERS, 0.8f, 0.6f);
            if (p.level() instanceof ServerLevel sl) {
                double y = p.getY() + 1.1;
                boolean twinGrab = p.getOffhandItem().is(AddonItemRegistry.DEMON_DWELLER_SWORD.get());
                // a small, tight ring where the hand closes on the grip - the client already animated the sword's flight
                FxSend.ring(sl, twinGrab ? Fx.DARK : Fx.CRIMSON, new Vec3(p.getX(), y - 0.2, p.getZ()), 0.2f, 1.2f, 0.2f, 8);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { PENDING.remove(e.getEntity().getUUID()); OFFHAND.remove(e.getEntity().getUUID()); LAST_SWAP_ATTACK.remove(e.getEntity().getUUID()); }
}
