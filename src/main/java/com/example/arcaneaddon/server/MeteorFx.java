package com.example.arcaneaddon.server;

import com.example.arcaneaddon.network.AddonNetwork;
import com.example.arcaneaddon.network.FxPacket;
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
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Devil Union: Black Meteorite - a cutscene move.
 *
 *  CHARGE  (0-10)   you crouch as ember rings close in and the ground cracks under you
 *  LAUNCH  (10-18)  you blast straight up ~20 blocks on a column of black flame
 *  APEX    (18-30)  you hang in the sky while a shell of black flame and ember forms around you, growing
 *  DIVE    (30-...) you fall on the spot you aimed at as a black meteor with a burning tail
 *  IMPACT           a crater of black flame: shockwaves, a pillar of fire, burning fissures, a ring of shards
 *
 * The caster's client plays it as a cutscene (camera angles, letterbox, FOV, shake - see client/Cutscene).
 * Packet shape 9 = cutscene (c = target, palette 0 start / 1 impact), shape 10 = screen shake for people nearby.
 */
public final class MeteorFx {
    private MeteorFx() {}

    private static final int EMBER = 9, DARK = 1, SILVER = 6;
    private static final int CHARGE = 10, LAUNCH = 8, APEX = 12, MAX_DIVE = 18;
    private static final Set<UUID> ACTIVE = new HashSet<>();

    public static boolean active(ServerPlayer p) { return ACTIVE.contains(p.getUUID()); }

    private static void sound(ServerLevel sl, Vec3 at, SoundEvent s, float v, float pitch) {
        sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, v, pitch);
    }

    private static void still(ServerPlayer p) {
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        p.resetFallDistance();
    }

    private static void cutscene(ServerPlayer p, Vec3 target, int phase) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 9; pkt.id = p.getId(); pkt.palette = (byte) phase; pkt.c = target; pkt.life = 0;
        AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
    }

    private static void shake(ServerLevel sl, Vec3 at, float strength, int ticks) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 10; pkt.c = at; pkt.r = strength; pkt.life = ticks;
        AddonNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, 48, sl.dimension())), pkt);
    }

    public static void cast(ServerPlayer p, float power) {
        if (!ACTIVE.add(p.getUUID())) return;
        ServerLevel sl = p.serverLevel();
        // where it will land: what you are looking at (up to 48 blocks), else the floor 20 blocks ahead
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        var hit = sl.clip(new ClipContext(eye, eye.add(look.scale(48)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 aim = hit.getType() == HitResult.Type.MISS ? eye.add(new Vec3(look.x, 0, look.z).normalize().scale(20)) : hit.getLocation();
        var down = sl.clip(new ClipContext(aim.add(0, 2, 0), aim.add(0, -40, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 target = down.getType() == HitResult.Type.MISS ? aim : down.getLocation();
        cutscene(p, target, 0);
        sound(sl, p.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.5f);
        sound(sl, p.position(), SoundEvents.ENDER_DRAGON_GROWL, 0.6f, 0.6f);
        step(p, sl, target, power, 0, p.position());
    }

    private static void step(ServerPlayer p, ServerLevel sl, Vec3 target, float power, int t, Vec3 last) {
        if (!p.isAlive() || p.isRemoved() || !AstaFx.inDevilUnion(p)) {
            ACTIVE.remove(p.getUUID());
            if (!p.isRemoved()) cutscene(p, p.position(), 1);   // end the cutscene cleanly
            return;
        }
        Vec3 pos = p.position(), mid = pos.add(0, 1, 0);
        if (t < CHARGE) {                                                       // ---- CHARGE
            still(p);
            if (t % 2 == 0) FxSend.ring(sl, EMBER, pos.add(0, 0.05, 0), 4.5f - t * 0.35f, 0.4f, 0.15f, 5);
            if (t % 3 == 0) FxSend.wall(sl, DARK, mid, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 1.6f - t * 0.1f, 0.6f, -180, 180, 1.1f, 5, 0, 0);
            if (t == 4) for (int k = 0; k < 6; k++) {                           // the ground cracks under the pressure
                double a = Math.PI * 2 * k / 6;
                FxSend.beam(sl, EMBER, pos.add(0, 0.04, 0), pos.add(Math.cos(a) * 2.2, 0.04, Math.sin(a) * 2.2), 0.12f, 30);
            }
        } else if (t < CHARGE + LAUNCH) {                                       // ---- LAUNCH
            p.setDeltaMovement(0, 2.6, 0);
            p.hurtMarked = true;
            p.resetFallDistance();
            if (t == CHARGE) {
                FxLib.shockRing(sl, EMBER, pos.add(0, 0.05, 0), 1.6f);
                FxLib.groundPuff(sl, DARK, pos, 2.2f);
                FxLib.spikeRing(sl, DARK, pos, 1.1f);
                sound(sl, pos, SoundEvents.GENERIC_EXPLODE, 1f, 0.6f);
                sound(sl, pos, SoundEvents.FIRECHARGE_USE, 1f, 0.5f);
            }
            FxSend.beam(sl, EMBER, last, mid, 1.1f, 10);                         // the column of black flame under you
            FxSend.beam(sl, DARK, last, mid, 0.55f, 9);
        } else if (t < CHARGE + LAUNCH + APEX) {                                // ---- APEX
            still(p);
            int a = t - CHARGE - LAUNCH;
            float r = 0.8f + a * 0.12f;
            if (a % 2 == 0) {
                for (int k = 0; k < 3; k++) {                                   // a shell of black flame forming around you
                    double ang = a * 0.4 + Math.PI * k / 3;
                    FxSend.wall(sl, DARK, mid, new Vec3(Math.cos(ang), 0, Math.sin(ang)), new Vec3(0, 1, 0), r, r, -180, 180, r * 0.4f, 3, 0, 0);
                }
                FxSend.ring(sl, EMBER, mid, r * 1.2f, r * 1.25f, 0.06f, 3);
            }
            if (a % 3 == 0) FxSend.ring(sl, EMBER, mid, 5.5f - a * 0.3f, 0.6f, 0.08f, 4);   // ember drawn in from the sky
            if (a == 0) sound(sl, pos, SoundEvents.BEACON_POWER_SELECT, 1f, 0.5f);
            if (a == APEX - 2) {
                FxLib.flash(sl, EMBER, mid, 1.6f);
                sound(sl, pos, SoundEvents.WITHER_SHOOT, 1f, 0.5f);
            }
        } else {                                                                // ---- DIVE
            int d = t - CHARGE - LAUNCH - APEX;
            Vec3 to = target.subtract(pos);
            boolean landed = d > 0 && (p.onGround() || to.length() < 3.2 || d >= MAX_DIVE);
            if (landed) { impact(p, sl, p.onGround() || to.length() < 3.2 ? pos : target, power); return; }
            Vec3 dir = to.normalize();
            p.setDeltaMovement(dir.scale(3.0));
            p.hurtMarked = true;
            p.resetFallDistance();
            Vec3 tailEnd = mid.subtract(dir.scale(7));
            FxSend.beam(sl, EMBER, tailEnd, mid, 1.8f, 6);                     // burning tail
            FxSend.beam(sl, DARK, mid.subtract(dir.scale(5)), mid, 1.0f, 5);
            FxSend.crescent(sl, DARK, mid.add(dir.scale(1.2)), dir, FxLib.right(dir), 1.6f, 1.8f, 2, 0f);   // the meteor's head
            FxSend.ring(sl, EMBER, mid, 1.4f, 1.6f, 0.9f, 2);
            if (d % 3 == 0) sound(sl, pos, SoundEvents.BLAZE_SHOOT, 0.8f, 0.5f);
        }
        AstaFx.later(1, () -> step(p, sl, target, power, t + 1, mid));
    }

    private static void impact(ServerPlayer p, ServerLevel sl, Vec3 at, float power) {
        ACTIVE.remove(p.getUUID());
        still(p);
        cutscene(p, at, 1);
        shake(sl, at, 1.0f, 22);
        Vec3 g = new Vec3(at.x, at.y + 0.05, at.z);
        double R = 9;
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(R, 5, R), x -> x != p && x.isAlive() && !x.isAlliedTo(p))) {
            double dist = t.position().distanceTo(at);
            if (dist > R) continue;
            AstaFx.damage(p, t, (float) (power * (1.6 - dist / R)), "devil_black_meteorite");
            Vec3 away = t.position().subtract(at).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
            t.setDeltaMovement(away.scale(1.4 - dist / R).add(0, 0.9 - dist / R * 0.4, 0));
            t.hurtMarked = true;
            FxLib.impact(sl, EMBER, t.position().add(0, t.getBbHeight() * 0.5, 0), 1.2f);
        }
        // the blast
        FxLib.flash(sl, SILVER, at.add(0, 1, 0), 2.0f);
        FxSend.ring(sl, EMBER, g, 0.5f, 13f, 0.35f, 14);
        FxSend.ring(sl, DARK, g, 0.5f, 9f, 0.6f, 12);
        AstaFx.later(3, () -> FxSend.ring(sl, SILVER, g, 1f, 17f, 0.06f, 12));
        FxLib.dome(sl, EMBER, g, 2.2f);
        FxSend.beam(sl, EMBER, g, g.add(0, 16, 0), 3.2f, 22);                  // pillar of black flame
        FxSend.beam(sl, DARK, g, g.add(0, 12, 0), 1.8f, 20);
        // burning fissures running out from the crater
        Random rnd = new Random();
        for (int k = 0; k < 12; k++) {
            double a = Math.PI * 2 * k / 12 + rnd.nextDouble() * 0.3;
            Vec3 prev = g.add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5);
            for (int s = 1; s <= 4; s++) {
                double rr = 2.5 + s * 1.6;
                double aa = a + (rnd.nextDouble() - 0.5) * 0.25;
                Vec3 q = g.add(Math.cos(aa) * rr, 0, Math.sin(aa) * rr);
                final Vec3 pa = prev, pb = q;
                AstaFx.later(s, () -> FxSend.beam(sl, EMBER, pa, pb, 0.16f, 40));
                prev = q;
            }
        }
        // the crater rim: black shards heaved up and leaning out
        for (int k = 0; k < 16; k++) {
            double a = Math.PI * 2 * k / 16 + rnd.nextDouble() * 0.2;
            Vec3 base = g.add(Math.cos(a) * 3.2, -0.2, Math.sin(a) * 3.2);
            Vec3 dir = new Vec3(Math.cos(a) * 0.8, 1, Math.sin(a) * 0.8);
            FxSend.spike(sl, k % 4 == 0 ? EMBER : DARK, base, dir, 1.4f + rnd.nextFloat() * 1.2f, 0.28f, 50, 2, FxPacket.GROUND);
        }
        FxLib.spikeBurst(sl, DARK, at.add(0, 1, 0), 2.0f);
        sound(sl, at, SoundEvents.GENERIC_EXPLODE, 1.6f, 0.5f);
        sound(sl, at, SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.6f);
        sound(sl, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 0.6f);
        AstaFx.later(4, () -> sound(sl, at, SoundEvents.GENERIC_EXPLODE, 1.0f, 0.4f));
    }
}
