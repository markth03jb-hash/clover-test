package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.DamageSources;
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

    private static class Dash {
        int left = TICKS; double speed = SPEED; boolean dweller; boolean devil; Vec3 dir; float damage; AbstractSpell spell; final Set<UUID> hit = new HashSet<>(); final java.util.List<Vec3> path = new java.util.ArrayList<>();
    }
    private static final Map<UUID, Dash> ACTIVE = new HashMap<>();

    public static boolean isDashing(Player p) { return ACTIVE.containsKey(p.getUUID()); }

    public static void start(ServerPlayer p, AbstractSpell spell, float damage, boolean dweller) { start(p, spell, damage, dweller, false); }

    /** devil = Devil Rush: 30% faster, 3 ticks longer, and the path it leaves explodes half a second later. */
    public static void start(ServerPlayer p, AbstractSpell spell, float damage, boolean dweller, boolean devil) {
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, Mth.clamp(look.y, -0.5, 0.5), look.z).normalize();
        Dash d = new Dash(); d.dir = dir; d.damage = damage; d.spell = spell;
        if (dweller) {   // Dweller variant: quicker, lighter, leaves a dark trail
            d.speed = 1.95; d.left = 6; d.damage = damage * 0.7f; d.dweller = true;
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("ui.arcane_addon.variant.dweller").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), true);
        }
        if (devil) { d.devil = true; d.speed *= 1.3; d.left += 3; }
        ACTIVE.put(p.getUUID(), d);
        p.startAutoSpinAttack(TICKS + 2);   // vanilla riptide pose: whole body thrust forward, weapon out front
        p.level().playSound(null, p.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.0f, 0.8f);
        p.level().playSound(null, p.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.8f, 0.5f);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.6f);
        if (p.level() instanceof ServerLevel sl) {
            int pal = dweller ? 1 : 0;
            Vec3 o = p.position().add(0, 1.0, 0);
            FxSend.beam(sl, pal, o, o.add(dir.scale(d.speed * d.left)), dweller ? 0.8f : 1.1f, 12);   // the dash as a long 3D slash streak
            FxSend.ring(sl, pal, p.position().add(0, 0.1, 0), 0.4f, 3.2f, 0.5f, 9);
            Fx.groundPuff(sl, Fx.SILVER, p.position(), 1.8f);
            Fx.flash(sl, o, 0.9f);
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
            Vec3 tm = new Vec3(t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ());
            Fx.impact(sl, d.dweller ? Fx.DARK : Fx.CRIMSON, tm, 1.3f);
            // a clean cut straight through them along the dash line
            FxSend.beam(sl, Fx.SILVER, tm.subtract(d.dir.scale(1.2)), tm.add(d.dir.scale(1.2)), 0.18f, 6);
            sl.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0f, 0.7f);
            sl.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
        // slash trail: a ring around the body each tick, plus a silver spear-tip ahead of you
        Vec3 body = p.position().add(0, 1.0, 0);
        Vec3 rt = rightOf(d.dir), upv = rt.cross(d.dir).normalize();
        FxSend.wall(sl, d.dweller ? Fx.DARK : Fx.CRIMSON, body, rt, upv, 0.5f, 1.1f, -180, 180, 0.25f, 5, 0, 0);   // ring wrapped around the dash line
        FxSend.beam(sl, Fx.SILVER, body, body.add(d.dir.scale(1.6)), 0.12f, 3);
        if (d.dweller) FxSend.ring(sl, Fx.DARK, p.position().add(0, 0.6, 0), 0.4f, 1.0f, 0.5f, 6);   // dark wake
        if (d.devil) {
            d.path.add(p.position());
            FxSend.beam(sl, 9, p.position().add(0, 0.3, 0), p.position().add(0, 0.3, 0).subtract(d.dir.scale(d.speed)), 0.35f, 12);   // ember trail
        }
        if (--d.left <= 0 || p.horizontalCollision) {
            ACTIVE.remove(p.getUUID());
            if (d.devil) detonate(p, d);
            FxSend.ring(sl, d.dweller ? 1 : 0, p.position().add(0, 0.1, 0), 0.4f, 3.0f, 0.5f, 8);
            Fx.groundPuff(sl, Fx.SILVER, p.position(), 2.4f);
            sl.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8f, 0.6f);
            p.setDeltaMovement(d.dir.scale(0.3));   // brake
            p.hurtMarked = true;
        }
    }

    /** Devil Rush: the ember trail bursts, point by point along the path. */
    private static void detonate(ServerPlayer p, Dash d) {
        ServerLevel sl = p.serverLevel();
        java.util.Set<UUID> struck = new HashSet<>();
        for (int i = 0; i < d.path.size(); i++) {
            final Vec3 at = d.path.get(i);
            AstaFx.later(8 + i, () -> {
                if (!p.isAlive()) return;
                FxLib.pillar(sl, 9, at, 0.8f);
                FxLib.groundPuff(sl, 9, at, 1.6f);
                for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(1.8, 1.5, 1.8), x -> x != p && x.isAlive() && !x.isAlliedTo(p))) {
                    if (struck.add(t.getUUID())) DamageSources.applyDamage(t, d.damage * 0.6f, d.spell.getDamageSource(p));
                }
                sl.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.35f, 1.4f);
            });
        }
    }

    private static Vec3 rightOf(Vec3 dir) {
        Vec3 r = dir.cross(new Vec3(0, 1, 0));
        return r.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : r.normalize();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { ACTIVE.remove(e.getEntity().getUUID()); }
}
