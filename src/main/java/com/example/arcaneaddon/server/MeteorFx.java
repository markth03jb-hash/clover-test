package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.network.AddonNetwork;
import com.example.arcaneaddon.network.FxPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Devil Union: Black Meteorite - a cutscene combo.
 *
 *  DASH      you rocket at the enemy you aim at, trailing black flame. Miss and it ends there.
 *  LAUNCH    on contact an uppercut slash throws them high into the air
 *  BLITZ     you blink around them, cutting through them from every side, a dozen times in a heartbeat
 *  FINISHER  you appear above them, black flame gathers on the blade, and you come down like a meteor with one huge
 *            slash, driving them into the ground
 *  IMPACT    a crater of black flame: shockwaves, a pillar of fire, burning fissures, a ring of shards
 *
 * The victim cannot die until the finisher lands (the finishing blow can kill). You can't be hurt while it plays.
 * The caster's client plays it as a cutscene - see client/Cutscene. Packet shape 9 = cutscene (palette = phase:
 * 0 dash, 2 blitz, 3 finisher, 1 impact/end; c = where to look), shape 10 = screen shake for people nearby.
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public final class MeteorFx {
    private MeteorFx() {}

    private static final int EMBER = 9, DARK = 1, SILVER = 6;
    private static final int MAX_DASH = 12, LAUNCH = 5, STRIKES = 12, GATHER = 6, MAX_FALL = 12;
    private static final Set<UUID> ACTIVE = new HashSet<>();      // casters in the combo
    private static final Set<UUID> PROTECTED = new HashSet<>();   // victims who can't die yet

    public static boolean active(ServerPlayer p) { return ACTIVE.contains(p.getUUID()); }

    private static void sound(ServerLevel sl, Vec3 at, SoundEvent s, float v, float pitch) {
        sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, v, pitch);
    }

    private static Vec3 mid(LivingEntity t) { return t.position().add(0, t.getBbHeight() * 0.5, 0); }

    private static void cutscene(ServerPlayer p, Vec3 look, int phase) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 9; pkt.id = p.getId(); pkt.palette = (byte) phase; pkt.c = look;
        AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
    }

    private static void shake(ServerLevel sl, Vec3 at, float strength, int ticks) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 10; pkt.c = at; pkt.r = strength; pkt.life = ticks;
        AddonNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, 48, sl.dimension())), pkt);
    }

    /** Put the caster at {@code pos}, facing {@code look}, motionless. */
    private static void place(ServerPlayer p, Vec3 pos, Vec3 look) {
        Vec3 d = look.subtract(pos.add(0, 1.5, 0));
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        p.connection.teleport(pos.x, pos.y, pos.z, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        p.resetFallDistance();
    }

    /** Hold the victim with its middle at {@code at}. */
    private static void hold(LivingEntity t, Vec3 at) {
        t.teleportTo(at.x, at.y - t.getBbHeight() * 0.5, at.z);
        t.setDeltaMovement(Vec3.ZERO);
        t.hurtMarked = true;
        t.resetFallDistance();
    }

    private static boolean ok(ServerPlayer p) { return p.isAlive() && !p.isRemoved(); }

    private static void abort(ServerPlayer p, LivingEntity t) {
        ACTIVE.remove(p.getUUID());
        if (t != null) PROTECTED.remove(t.getUUID());
        if (!p.isRemoved()) cutscene(p, p.position(), 1);
    }

    // ------------------------------------------------------------------ cast + dash
    public static void cast(ServerPlayer p, float power) {
        if (!ACTIVE.add(p.getUUID())) return;
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity target = null;
        double best = Math.cos(Math.toRadians(22));
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(28), x -> x != p && x.isAlive() && !x.isAlliedTo(p))) {
            Vec3 to = mid(t).subtract(eye);
            double d = to.length();
            if (d > 28 || !p.hasLineOfSight(t)) continue;
            double dot = to.scale(1 / d).dot(look);
            if (dot > best) { best = dot; target = t; }
        }
        Vec3 aim = target != null ? mid(target) : eye.add(look.scale(14));
        cutscene(p, aim, 0);
        sound(sl, p.position(), SoundEvents.ENDER_DRAGON_FLAP, 1f, 0.6f);
        sound(sl, p.position(), SoundEvents.TRIDENT_RIPTIDE_3, 1f, 0.7f);
        FxLib.shockRing(sl, EMBER, p.position().add(0, 0.05, 0), 1.0f);
        dash(p, sl, target, aim, power, 0, p.position().add(0, 1, 0));
    }

    private static void dash(ServerPlayer p, ServerLevel sl, LivingEntity t, Vec3 aim, float power, int step, Vec3 last) {
        if (!ok(p) || !AstaFx.inDevilUnion(p)) { abort(p, null); return; }
        Vec3 me = p.position().add(0, 1, 0);
        Vec3 goal = t != null && t.isAlive() ? mid(t) : aim;
        if (t != null && t.isAlive() && me.distanceTo(goal) < 2.6) { launch(p, sl, t, power); return; }
        if (step >= MAX_DASH || (t == null && me.distanceTo(goal) < 2.0) || (step > 1 && p.horizontalCollision)) {
            // missed: skid to a stop
            p.setDeltaMovement(Vec3.ZERO);
            p.hurtMarked = true;
            FxLib.groundPuff(sl, EMBER, p.position(), 1.6f);
            p.displayClientMessage(Component.translatable("ui.arcane_addon.meteorite_miss").withStyle(ChatFormatting.GRAY), true);
            abort(p, null);
            return;
        }
        Vec3 dir = goal.subtract(me).normalize();
        p.setDeltaMovement(dir.scale(2.6));
        p.hurtMarked = true;
        p.resetFallDistance();
        FxSend.beam(sl, EMBER, last, me, 0.9f, 8);
        FxSend.beam(sl, DARK, last, me, 0.45f, 7);
        FxSend.wall(sl, DARK, me, FxLib.right(dir), FxLib.up(dir), 0.6f, 1.2f, -180, 180, 0.1f, 4, 0, 0);
        AstaFx.later(1, () -> dash(p, sl, t, aim, power, step + 1, me));
    }

    // ------------------------------------------------------------------ launch + blitz
    private static void launch(ServerPlayer p, ServerLevel sl, LivingEntity t, float power) {
        PROTECTED.add(t.getUUID());
        Vec3 base = mid(t);
        // how high there's room for (up to 6 blocks)
        var ceil = sl.clip(new ClipContext(base, base.add(0, 7, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double room = ceil.getType() == HitResult.Type.MISS ? 6 : Math.max(1.5, ceil.getLocation().y - base.y - 1.5);
        Vec3 apex = base.add(0, room, 0);
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        // the uppercut
        Vec3 f = new Vec3(base.x - p.getX(), 0, base.z - p.getZ());
        f = f.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : f.normalize();
        FxSend.crescent(sl, DARK, base, new Vec3(0, 1, 0), FxLib.right(f), 1.6f, 3.2f, 8, 1.2f);
        FxSend.wall(sl, EMBER, base.subtract(f.scale(0.5)), f, new Vec3(0, 1, 0), 2.0f, 2.4f, -60, 90, 0.4f, 7, 2, 0);
        FxLib.impact(sl, EMBER, base, 1.4f);
        AstaFx.damage(p, t, power * 0.3f, "devil_black_meteorite");
        sound(sl, base, SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.6f);
        sound(sl, base, SoundEvents.GENERIC_EXPLODE, 0.6f, 1.4f);
        cutscene(p, apex, 2);
        rise(p, sl, t, base, apex, power, 0);
    }

    private static void rise(ServerPlayer p, ServerLevel sl, LivingEntity t, Vec3 from, Vec3 apex, float power, int k) {
        if (!ok(p) || !t.isAlive()) { abort(p, t); return; }
        double u = Math.min(1.0, (k + 1) / (double) LAUNCH);
        u = 1 - (1 - u) * (1 - u);
        Vec3 at = from.add(apex.subtract(from).scale(u));
        hold(t, at);
        FxSend.beam(sl, EMBER, from, at, 0.35f, 6);
        if (k + 1 >= LAUNCH) { blitz(p, sl, t, apex, power, 0, p.position().add(0, 1, 0)); return; }
        AstaFx.later(1, () -> rise(p, sl, t, from, apex, power, k + 1));
    }

    /** One blink-strike every 2 ticks: appear somewhere around them and cut straight through to the other side. */
    private static void blitz(ServerPlayer p, ServerLevel sl, LivingEntity t, Vec3 apex, float power, int k, Vec3 prevPos) {
        if (!ok(p) || !t.isAlive()) { abort(p, t); return; }
        hold(t, apex);
        if (k >= STRIKES * 2) { finisher(p, sl, t, apex, power, 0); return; }
        if (k % 2 == 0) {
            Random r = new Random();
            double a = r.nextDouble() * Math.PI * 2, el = (r.nextDouble() - 0.35) * 1.2;
            Vec3 off = new Vec3(Math.cos(a) * Math.cos(el), Math.sin(el), Math.sin(a) * Math.cos(el)).scale(3.2);
            Vec3 from = apex.add(off), to = apex.subtract(off);
            // afterimage from where you were, then you are already on the other side
            FxSend.beam(sl, DARK, prevPos, from, 0.12f, 4);
            place(p, to.subtract(0, 1.0, 0), apex);
            FxSend.beam(sl, DARK, from, to, 0.55f, 6);
            FxSend.beam(sl, EMBER, from, to, 0.18f, 5);
            FxSend.beam(sl, SILVER, from, to, 0.04f, 4);
            Vec3 cd = to.subtract(from).normalize();
            FxSend.crescent(sl, k % 4 == 0 ? DARK : EMBER, apex, cd, FxLib.up(cd), 1.0f, 1.6f, 4, 0f);
            AstaFx.damage(p, t, power * 0.12f, "devil_black_meteorite");
            sound(sl, apex, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 1.2f + r.nextFloat() * 0.6f);
            sound(sl, apex, SoundEvents.PLAYER_ATTACK_CRIT, 0.7f, 1.0f + r.nextFloat() * 0.5f);
            final Vec3 now = to;
            AstaFx.later(1, () -> blitz(p, sl, t, apex, power, k + 1, now));
            return;
        }
        AstaFx.later(1, () -> blitz(p, sl, t, apex, power, k + 1, prevPos));
    }

    // ------------------------------------------------------------------ finisher
    private static void finisher(ServerPlayer p, ServerLevel sl, LivingEntity t, Vec3 apex, float power, int k) {
        if (!ok(p) || !t.isAlive()) { abort(p, t); return; }
        Vec3 above = apex.add(0, 4.0, 0);
        if (k == 0) {
            place(p, above.subtract(0, 1.0, 0), apex);
            cutscene(p, apex, 3);
            FxLib.flash(sl, EMBER, above, 1.2f);
            sound(sl, above, SoundEvents.WITHER_SHOOT, 1f, 0.5f);
        }
        if (k < GATHER) {                                     // black flame gathers on the blade above them
            hold(t, apex);
            place(p, above.subtract(0, 1.0, 0), apex);
            FxSend.ring(sl, EMBER, above, 3.0f - k * 0.4f, 0.4f, 0.1f, 4);
            FxSend.wall(sl, DARK, above, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.8f + k * 0.1f, 1.0f + k * 0.12f, -180, 180, 1.0f, 3, 0, 0);
            AstaFx.later(1, () -> finisher(p, sl, t, apex, power, k + 1));
            return;
        }
        // the meteor: one huge vertical slash, and you both come down
        if (k == GATHER) {
            FxSend.crescent(sl, DARK, above, new Vec3(0, -1, 0), FxLib.right(new Vec3(0, -1, 0)), 3.0f, 6.0f, 10, 0.8f);
            FxSend.wall(sl, EMBER, above, new Vec3(0, -1, 0), p.getLookAngle().multiply(1, 0, 1).normalize(), 3.0f, 3.6f, -80, 80, 1.2f, 8, 2, 0);
            sound(sl, above, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.4f);
            sound(sl, above, SoundEvents.TRIDENT_THUNDER, 0.8f, 0.6f);
        }
        int f = k - GATHER;
        var floor = sl.clip(new ClipContext(apex, apex.add(0, -48, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 ground = floor.getType() == HitResult.Type.MISS ? apex.add(0, -6, 0) : floor.getLocation();
        double fallY = apex.y - (f + 1) * 2.6;
        Vec3 tAt = new Vec3(apex.x, Math.max(ground.y + t.getBbHeight() * 0.5, fallY), apex.z);
        hold(t, tAt);
        place(p, tAt.add(0, 1.4, 0), tAt.subtract(0, 2, 0));
        FxSend.beam(sl, EMBER, tAt.add(0, 7, 0), tAt.add(0, 1.4, 0), 1.6f, 6);   // burning meteor tail
        FxSend.beam(sl, DARK, tAt.add(0, 5, 0), tAt.add(0, 1.4, 0), 0.9f, 5);
        if (tAt.y <= ground.y + t.getBbHeight() * 0.5 + 0.01 || f >= MAX_FALL) { impact(p, sl, t, ground, power); return; }
        AstaFx.later(1, () -> finisher(p, sl, t, apex, power, k + 1));
    }

    private static void impact(ServerPlayer p, ServerLevel sl, LivingEntity victim, Vec3 at, float power) {
        ACTIVE.remove(p.getUUID());
        PROTECTED.remove(victim.getUUID());
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        p.resetFallDistance();
        cutscene(p, at, 1);
        shake(sl, at, 1.0f, 22);
        // the finishing blow - this one can kill
        AstaFx.damage(p, victim, power * 1.5f, "devil_black_meteorite");
        Vec3 g = new Vec3(at.x, at.y + 0.05, at.z);
        double R = 8;
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(R, 5, R), x -> x != p && x != victim && x.isAlive() && !x.isAlliedTo(p))) {
            double dist = t.position().distanceTo(at);
            if (dist > R) continue;
            AstaFx.damage(p, t, (float) (power * (1.0 - dist / R * 0.6)), "devil_black_meteorite");
            Vec3 away = t.position().subtract(at).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
            t.setDeltaMovement(away.scale(1.4 - dist / R).add(0, 0.8 - dist / R * 0.4, 0));
            t.hurtMarked = true;
        }
        FxLib.flash(sl, SILVER, at.add(0, 1, 0), 2.0f);
        FxSend.ring(sl, EMBER, g, 0.5f, 12f, 0.35f, 14);
        FxSend.ring(sl, DARK, g, 0.5f, 8f, 0.6f, 12);
        AstaFx.later(3, () -> FxSend.ring(sl, SILVER, g, 1f, 16f, 0.06f, 12));
        FxLib.dome(sl, EMBER, g, 2.0f);
        FxSend.beam(sl, EMBER, g, g.add(0, 14, 0), 2.8f, 20);
        FxSend.beam(sl, DARK, g, g.add(0, 10, 0), 1.6f, 18);
        Random rnd = new Random();
        for (int k = 0; k < 12; k++) {                         // burning fissures
            double a = Math.PI * 2 * k / 12 + rnd.nextDouble() * 0.3;
            Vec3 prev = g.add(Math.cos(a) * 2.2, 0, Math.sin(a) * 2.2);
            for (int s = 1; s <= 4; s++) {
                double rr = 2.2 + s * 1.5, aa = a + (rnd.nextDouble() - 0.5) * 0.25;
                Vec3 q = g.add(Math.cos(aa) * rr, 0, Math.sin(aa) * rr);
                final Vec3 pa = prev, pb = q;
                AstaFx.later(s, () -> FxSend.beam(sl, EMBER, pa, pb, 0.16f, 40));
                prev = q;
            }
        }
        for (int k = 0; k < 14; k++) {                         // crater rim of black shards
            double a = Math.PI * 2 * k / 14 + rnd.nextDouble() * 0.2;
            Vec3 b = g.add(Math.cos(a) * 3.0, -0.2, Math.sin(a) * 3.0);
            FxSend.spike(sl, k % 4 == 0 ? EMBER : DARK, b, new Vec3(Math.cos(a) * 0.8, 1, Math.sin(a) * 0.8), 1.3f + rnd.nextFloat() * 1.1f, 0.26f, 50, 2, FxPacket.GROUND);
        }
        FxLib.spikeBurst(sl, DARK, at.add(0, 1, 0), 1.8f);
        sound(sl, at, SoundEvents.GENERIC_EXPLODE, 1.6f, 0.5f);
        sound(sl, at, SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.6f);
        sound(sl, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 0.6f);
    }

    // ------------------------------------------------------------------ rules while it plays
    /** The victim can't die before the finisher; anything that would kill it leaves it at half a heart. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent e) {
        LivingEntity t = e.getEntity();
        if (PROTECTED.contains(t.getUUID()) && e.getAmount() >= t.getHealth()) e.setAmount(Math.max(0f, t.getHealth() - 1f));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity t = e.getEntity();
        if (PROTECTED.contains(t.getUUID())) {
            e.setCanceled(true);
            t.setHealth(1f);
        }
        if (t instanceof ServerPlayer sp) ACTIVE.remove(sp.getUUID());
    }

    /** You can't be hurt while the combo plays. */
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && ACTIVE.contains(p.getUUID())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onStop(net.minecraftforge.event.server.ServerStoppingEvent e) {
        ACTIVE.clear();
        PROTECTED.clear();
    }
}
