package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Piercing Dash: for TICKS ticks the player is launched along their look direction at SPEED blocks/tick
 * (riptide-style pose, sword thrust forward). Every living enemy touched by the swept path is hit once and the
 * dash does not stop for them. It ends early only when you hit a wall.
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public class DashQueue {
    public static final int TICKS = 7;
    public static final double SPEED = 1.55;   // ~11 blocks total
    private static final DustParticleOptions CRIMSON = new DustParticleOptions(new Vector3f(0.75f, 0.04f, 0.10f), 1.5f);

    private static final DustParticleOptions BLACKTRAIL = new DustParticleOptions(new Vector3f(0.04f, 0.0f, 0.07f), 1.9f);

    private static class Dash {
        int left = TICKS; double speed = SPEED; boolean dweller; Vec3 dir; float damage; AbstractSpell spell; final Set<UUID> hit = new HashSet<>();
    }
    private static final Map<UUID, Dash> ACTIVE = new HashMap<>();

    public static boolean isDashing(Player p) { return ACTIVE.containsKey(p.getUUID()); }

    public static void start(ServerPlayer p, AbstractSpell spell, float damage, boolean dweller) {
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, Mth.clamp(look.y, -0.5, 0.5), look.z).normalize();
        Dash d = new Dash(); d.dir = dir; d.damage = damage; d.spell = spell;
        if (dweller) {   // Dweller variant: quicker, lighter, leaves a dark trail
            d.speed = 1.95; d.left = 6; d.damage = damage * 0.7f; d.dweller = true;
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("ui.arcane_addon.variant.dweller").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), true);
        }
        ACTIVE.put(p.getUUID(), d);
        p.startAutoSpinAttack(TICKS + 2);   // vanilla riptide pose: whole body thrust forward, weapon out front
        p.level().playSound(null, p.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.0f, 0.8f);
        p.level().playSound(null, p.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.8f, 0.5f);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.6f);
        if (p.level() instanceof ServerLevel sl) {
            ring(sl, p.position().add(0, 0.1, 0), 36, 0.45);
            sl.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 18, 0.4, 0.1, 0.4, 0.12);
            sl.sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.5, p.getZ(), 10, 0.3, 0.3, 0.3, 0.08);
        }
    }

    /** Flat expanding ring of crimson dust. */
    private static void ring(ServerLevel sl, Vec3 c, int n, double speed) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            sl.sendParticles(CRIMSON, c.x, c.y, c.z, 0, Math.cos(a), 0.0, Math.sin(a), speed);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Dash d = ACTIVE.get(p.getUUID());
        if (d == null) return;
        if (!p.isAlive()) { ACTIVE.remove(p.getUUID()); return; }

        p.setDeltaMovement(d.dir.scale(d.speed));
        p.hurtMarked = true;          // sync the velocity to the client
        p.resetFallDistance();

        ServerLevel sl = p.serverLevel();
        AABB swept = p.getBoundingBox().expandTowards(d.dir.scale(d.speed)).inflate(0.6);
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, swept, x -> x != p && x.isAlive() && !x.isAlliedTo(p))) {
            if (!d.hit.add(t.getUUID())) continue;           // pierce: each target is hit once, dash keeps going
            DamageSources.applyDamage(t, d.damage, d.spell.getDamageSource(p));
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 1, 0, 0, 0, 0);
            sl.sendParticles(CRIMSON, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 30, 0.35, 0.5, 0.35, 0.12);
            sl.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 25, 0.3, 0.4, 0.3, 0.5);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 14, 0.25, 0.4, 0.25, 0.08);
            sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 6, 0.2, 0.3, 0.2, 0.1);
            sl.sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 1, 0, 0, 0, 0);
            sl.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0f, 0.7f);
            sl.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
        // crimson slash trail
        sl.sendParticles(CRIMSON, p.getX(), p.getY() + 1.0, p.getZ(), 14, 0.3, 0.5, 0.3, 0.02);
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.25, 0.4, 0.25, 0.02);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.3, 0.5, 0.3, 0.4);
        sl.sendParticles(ParticleTypes.CRIT, p.getX() + d.dir.x, p.getY() + 1.0, p.getZ() + d.dir.z, 4, 0.2, 0.3, 0.2, 0.3);
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX() + d.dir.x * 0.8, p.getY() + 1.0, p.getZ() + d.dir.z * 0.8, 1, 0, 0, 0, 0);

        if (d.dweller) {
            sl.sendParticles(BLACKTRAIL, p.getX(), p.getY() + 1.0, p.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 0.8, p.getZ(), 3, 0.2, 0.4, 0.2, 0.02);
            sl.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.2, 0.4, 0.2, 0.02);
        }
        if (--d.left <= 0 || p.horizontalCollision) {
            ACTIVE.remove(p.getUUID());
            ring(sl, p.position().add(0, 0.1, 0), 28, 0.3);
            sl.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 14, 0.4, 0.2, 0.4, 0.1);
            sl.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8f, 0.6f);
            p.setDeltaMovement(d.dir.scale(0.3));   // brake
            p.hurtMarked = true;
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { ACTIVE.remove(e.getEntity().getUUID()); }
}
