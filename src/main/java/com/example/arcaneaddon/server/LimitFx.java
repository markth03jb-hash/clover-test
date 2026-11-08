package com.example.arcaneaddon.server;

import com.example.arcaneaddon.network.AddonNetwork;
import com.example.arcaneaddon.network.FxPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Surpassing Limits: each grimoire's awakening, and the evolved ultimates cast while the state lasts.
 * Book index (GrimoireMastery.BOOKS): 0 Asta, 1 Yami, 2 Yuno, 3 Nozel, 4 Magna, 5 Luck, 6 Mereoleona, 7 Zenon, 8 Klaus.
 */
public final class LimitFx {
    private LimitFx() {}

    private static final int EMBER = 9, DARK = 1, YAMI = 10, SILVER = 6, WIND = 5, MERC = 11, FIRE = 2,
            BLUE_L = 13, CRIMSON = 14, BONE = 15, STEEL = 7;

    // ------------------------------------------------------------------ helpers
    private static void sound(ServerLevel sl, Vec3 at, SoundEvent s, float v, float pitch) {
        sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, v, pitch);
    }

    private static boolean alive(ServerPlayer p) { return p.isAlive() && !p.isRemoved(); }

    private static Vec3 mid(LivingEntity t) { return t.position().add(0, t.getBbHeight() * 0.5, 0); }

    private static List<LivingEntity> foes(ServerLevel sl, ServerPlayer p, AABB box) {
        return sl.getEntitiesOfClass(LivingEntity.class, box, x -> x != p && x.isAlive() && !x.isAlliedTo(p));
    }

    private static List<LivingEntity> foesNear(ServerLevel sl, ServerPlayer p, Vec3 c, double r) {
        List<LivingEntity> l = new ArrayList<>(foes(sl, p, new AABB(c, c).inflate(r)));
        l.removeIf(t -> mid(t).distanceTo(c) > r + t.getBbWidth() * 0.5);
        return l;
    }

    private static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.normalize();
    }

    private static Vec3 rightOf(Vec3 d) {
        Vec3 r = d.cross(new Vec3(0, 1, 0));
        return r.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : r.normalize();
    }

    /** Where the player is looking: the first block hit within {@code range}, else that far ahead, on the ground. */
    private static Vec3 aimPoint(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(range));
        HitResult hr = p.serverLevel().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 at = hr.getType() == HitResult.Type.MISS ? end : hr.getLocation();
        Vec3 g = FxLib.ground(p.serverLevel(), at.add(0, 1, 0), 12);
        return g != null ? g : at;
    }

    private static void fireball(ServerLevel sl, int pal, Vec3 c, Vec3 vel, float r, float r2, int life, boolean explode) {
        FxPacket p = new FxPacket();
        if (life <= 3 && life > 0) life = 1;
        p.shape = 20; p.palette = (byte) pal; p.life = life; p.c = c; p.v = vel; p.r = r; p.r2 = r2; p.h = explode ? 1 : 0;
        AddonNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(c.x, c.y, c.z, 128, sl.dimension())), p);
    }

    private static void tornado(ServerLevel sl, int pal, Vec3 base, float r, float h, int age, Vec3 vel) {
        FxPacket p = new FxPacket();
        p.shape = 28; p.palette = (byte) pal; p.life = 1; p.c = base; p.v = vel; p.r = r; p.h = h; p.a0 = age;
        AddonNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(base.x, base.y, base.z, 128, sl.dimension())), p);
    }

    private static void blast(ServerPlayer p, ServerLevel sl, int pal, Vec3 at, double R, float dmg, String id) {
        fireball(sl, pal, at, Vec3.ZERO, (float) R * 0.3f, (float) R, 10, true);
        Vec3 g = FxLib.ground(sl, at.add(0, 0.5, 0), R + 1);
        if (g != null) FxLib.shockRing(sl, pal, g.add(0, 0.05, 0), (float) R * 0.35f);
        for (LivingEntity t : foesNear(sl, p, at, R)) {
            double d = mid(t).distanceTo(at);
            AstaFx.damage(p, t, dmg * (float) (1.0 - 0.4 * Math.min(1, d / R)), id);
            Vec3 away = mid(t).subtract(at);
            if (away.lengthSqr() > 1.0E-4) t.setDeltaMovement(t.getDeltaMovement().add(away.normalize().scale(0.6 + R * 0.08)).add(0, 0.35, 0));
            t.hurtMarked = true;
        }
        FxSend.shake(sl, at, (float) Math.min(1.2, R * 0.12), 8, 32);
        sound(sl, at, SoundEvents.GENERIC_EXPLODE, (float) Math.min(1.6, 0.5 + R * 0.12), 0.8f);
    }

    // ================================================================== awakenings (one per grimoire)
    public static void awaken(ServerPlayer p, int idx) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        // shared beat: the world goes white for an instant, the ground shakes, the grimoire's power erupts
        FxSend.impactFrame(sl, c, 4, false, 24);
        FxSend.shake(sl, c, 0.7f, 18, 32);
        sound(sl, c, SoundEvents.BEACON_ACTIVATE, 1f, 0.6f);
        sound(sl, c, SoundEvents.TOTEM_USE, 0.6f, 0.8f);
        switch (idx) {
            case 0 -> {   // Asta: black anti magic erupts from his body in a pillar, ember shockwaves, the ground cracks
                FxSend.beam(sl, DARK, c, c.add(0, 18, 0), 2.4f, 24);
                FxSend.beam(sl, EMBER, c, c.add(0, 16, 0), 0.9f, 22);
                FxLib.shockRing(sl, EMBER, c.add(0, 0.1, 0), 2.6f);
                AstaFx.later(4, () -> FxLib.shockRing(sl, DARK, c.add(0, 0.1, 0), 3.6f));
                FxSend.cracks(sl, EMBER, c.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 5f, 60, false, (int) sl.getGameTime());
                sound(sl, c, SoundEvents.WITHER_SPAWN, 0.5f, 1.2f);
            }
            case 1 -> {   // Yami: darkness pours out in a rolling dome, crescents fold in, space fractures around him
                for (int k = 0; k < 4; k++) {
                    final int kk = k;
                    AstaFx.later(k * 3, () -> FxSend.wall(sl, YAMI, c.add(0, 1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 1.0f + kk, 1.6f + kk * 1.4f, -180, 180, 1.2f + kk * 0.4f, 10, 0, 0));
                }
                for (int k = 0; k < 6; k++) {
                    double a = Math.PI * 2 * k / 6;
                    Vec3 from = c.add(Math.cos(a) * 6, 2.5, Math.sin(a) * 6);
                    Vec3 d = c.add(0, 1.2, 0).subtract(from).normalize();
                    FxSend.crescent(sl, YAMI, from, d, rightOf(d).cross(d).normalize(), 1.0f, 0.5f, 8, 0.6f);
                }
                Vec3 fw = flat(p.getLookAngle());
                FxSend.cracks(sl, YAMI, c.add(0, 1.4, 0).add(fw.scale(1.2)), rightOf(fw), new Vec3(0, 1, 0), 3.2f, 50, false, (int) sl.getGameTime());
                sound(sl, c, SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.5f);
            }
            case 2 -> {   // Yuno: the wind spirit roars up around him in a rising cyclone, gust rings rolling out
                for (int t = 0; t < 24; t++) {
                    final int tt = t;
                    AstaFx.later(t, () -> { if (alive(p)) tornado(sl, WIND, p.position(), 2.2f + tt * 0.08f, 6f, tt, p.getDeltaMovement()); });
                }
                for (int k = 0; k < 3; k++) {
                    final int kk = k;
                    AstaFx.later(k * 5, () -> FxSend.ring(sl, WIND, c.add(0, 0.2 + kk * 1.2, 0), 0.5f, 6f + kk * 2, 0.15f, 12));
                }
                FxLib.halo(sl, WIND, c.add(0, p.getBbHeight() + 0.6, 0), 1.2f);
                sound(sl, c, SoundEvents.ENDER_DRAGON_FLAP, 1f, 1.2f);
            }
            case 3 -> {   // Nozel: mercury bursts out of him into a ring of silver spears pointing at the sky
                for (int k = 0; k < 12; k++) {
                    double a = Math.PI * 2 * k / 12;
                    Vec3 b = c.add(Math.cos(a) * 3, 0, Math.sin(a) * 3);
                    Vec3 g = FxLib.ground(sl, b.add(0, 1, 0), 3);
                    if (g != null) FxSend.spike(sl, MERC, g, new Vec3(Math.cos(a) * 0.3, 1, Math.sin(a) * 0.3), 2.6f, 0.22f, 50, 4 + k % 3, FxPacket.GROUND);
                }
                FxSend.ring(sl, SILVER, c.add(0, 0.05, 0), 0.4f, 7f, 0.1f, 14);
                FxSend.wall(sl, MERC, c.add(0, 1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.9f, 1.4f, -180, 180, 1.2f, 12, 0, 0);
                sound(sl, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 0.6f);
                sound(sl, c, SoundEvents.BUCKET_EMPTY, 1f, 0.5f);
            }
            case 4 -> {   // Magna: his flames explode outward in one big fireball blast
                fireball(sl, FIRE, c.add(0, 1, 0), Vec3.ZERO, 1.5f, 6f, 12, true);
                FxLib.shockRing(sl, FIRE, c.add(0, 0.1, 0), 2.4f);
                FxSend.beam(sl, FIRE, c, c.add(0, 10, 0), 1.4f, 14);
                sound(sl, c, SoundEvents.FIRECHARGE_USE, 1f, 0.6f);
                sound(sl, c, SoundEvents.GENERIC_EXPLODE, 0.8f, 0.8f);
            }
            case 5 -> {   // Luck: lightning slams down all around him, blue rings racing out
                for (int k = 0; k < 7; k++) {
                    final int kk = k;
                    AstaFx.later(k * 2, () -> {
                        double a = kk * 2.39996;
                        Vec3 g = c.add(Math.cos(a) * (2 + kk * 0.5), 0, Math.sin(a) * (2 + kk * 0.5));
                        LightningFx.bolt(sl, g.add(0, 20, 0), g, 0.35f, 5, 3);
                        sound(sl, g, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.4f);
                    });
                }
                FxSend.ring(sl, BLUE_L, c.add(0, 0.05, 0), 0.3f, 6f, 0.12f, 10);
                LightningFx.bolt(sl, c.add(0, 22, 0), c, 0.6f, 6, 5);
                sound(sl, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.9f, 1.3f);
            }
            case 6 -> {   // Mereoleona: crimson hellfire roars up from her in a pillar and a wave of heat
                fireball(sl, CRIMSON, c.add(0, 1.2, 0), Vec3.ZERO, 1.6f, 4.5f, 14, true);
                FxSend.beam(sl, CRIMSON, c, c.add(0, 14, 0), 2.0f, 20);
                for (int k = 0; k < 3; k++) {
                    final int kk = k;
                    AstaFx.later(k * 4, () -> FxSend.ring(sl, CRIMSON, c.add(0, 0.1, 0), 0.5f, 5f + kk * 2.5f, 0.6f, 10));
                }
                sound(sl, c, SoundEvents.BLAZE_SHOOT, 1f, 0.5f);
                sound(sl, c, SoundEvents.RAVAGER_ROAR, 0.7f, 1.1f);
            }
            case 7 -> {   // Zenon: bones burst out of the ground in a ring and curl inward like ribs
                for (int k = 0; k < 14; k++) {
                    double a = Math.PI * 2 * k / 14;
                    Vec3 g = FxLib.ground(sl, c.add(Math.cos(a) * 4, 1, Math.sin(a) * 4), 3);
                    if (g == null) continue;
                    Vec3 axis = new Vec3(-Math.cos(a) * 0.5, 1, -Math.sin(a) * 0.5);
                    BoneFx.bone(sl, BoneFx.RIB, g.add(axis.normalize().scale(1.6)), axis, rightOf(axis), 1.6f, 0.16f, 60, 3 + k % 4, FxPacket.GROUND, 0, Vec3.ZERO, 0);
                }
                FxSend.ring(sl, BONE, c.add(0, 0.05, 0), 0.4f, 5f, 0.1f, 12);
                sound(sl, c, SoundEvents.SKELETON_HURT, 1f, 0.4f);
                sound(sl, c, SoundEvents.BONE_BLOCK_BREAK, 1f, 0.6f);
            }
            default -> {   // Klaus: steel plates rise around him in a circle, ringing like a bell
                for (int k = 0; k < 8; k++) {
                    double a = Math.PI * 2 * k / 8;
                    Vec3 g = FxLib.ground(sl, c.add(Math.cos(a) * 3.2, 1, Math.sin(a) * 3.2), 3);
                    if (g == null) continue;
                    Vec3 tan = new Vec3(-Math.sin(a), 0, Math.cos(a));
                    FxSend.box(sl, STEEL, g.add(0, 1.0, 0), tan, new Vec3(0, 1, 0), 0.9f, 0.08f, 1.0f, 30, 4, FxPacket.GROUND);
                }
                FxSend.ring(sl, STEEL, c.add(0, 0.05, 0), 0.4f, 5f, 0.1f, 12);
                sound(sl, c, SoundEvents.ANVIL_LAND, 0.8f, 0.6f);
                sound(sl, c, SoundEvents.BELL_BLOCK, 1f, 0.7f);
            }
        }
    }

    // ================================================================== evolved ultimates
    // ---------------------------------------------------------------- Asta: Full Devil Union
    private static final java.util.UUID FULL_UNION_DMG = java.util.UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a100201");

    /** Called right after Devil Union starts while surpassing limits: the union holds for the whole state, stronger. */
    public static void fullUnion(ServerPlayer p, int ticks) {
        ServerLevel sl = p.serverLevel();
        TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, FULL_UNION_DMG, "Full Devil Union", 6.0, AttributeModifier.Operation.ADDITION, ticks);
        Vec3 c = p.position();
        FxSend.impactFrame(sl, c, 4, true, 24);
        FxSend.beam(sl, DARK, c, c.add(0, 24, 0), 3.0f, 30);
        FxSend.beam(sl, EMBER, c, c.add(0, 22, 0), 1.2f, 28);
        FxLib.shockRing(sl, EMBER, c.add(0, 0.1, 0), 4f);
        FxSend.cracks(sl, EMBER, c.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 7f, 70, false, (int) sl.getGameTime() + 3);
        for (LivingEntity t : foesNear(sl, p, c, 10)) {
            Vec3 away = flat(t.position().subtract(c));
            t.setDeltaMovement(away.scale(1.5).add(0, 0.6, 0));
            t.hurtMarked = true;
        }
        sound(sl, c, SoundEvents.ENDER_DRAGON_GROWL, 1f, 0.5f);
    }

    // ---------------------------------------------------------------- Yuno: Zephyr Crown - Star Storm
    /** A storm of wind blades raining down on where you look for 3 s around a cyclone, then one great gust. */
    public static void starStorm(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = aimPoint(p, 32);
        final double R = 11;
        FxLib.halo(sl, WIND, p.position().add(0, p.getBbHeight() + 0.5, 0), 1.4f);   // the crown
        FxSend.ring(sl, WIND, c.add(0, 0.1, 0), (float) R - 0.3f, (float) R, 0.2f, 70);
        FxSend.ring(sl, SILVER, c.add(0, 16, 0), 2f, (float) R, 0.06f, 70);
        sound(sl, c, SoundEvents.ELYTRA_FLYING, 1f, 1.4f);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int t = 0; t < 60; t++) {
            final int tt = t;
            AstaFx.later(t, () -> {
                if (!alive(p)) return;
                tornado(sl, WIND, c, 2.5f + Math.min(1f, tt / 10f) * 2f, 9f, tt, Vec3.ZERO);
                if (tt % 2 != 0) return;
                for (int k = 0; k < 2; k++) {
                    double a = r.nextDouble(Math.PI * 2), d = Math.sqrt(r.nextDouble()) * R;
                    Vec3 g = c.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                    Vec3 top = g.add(r.nextDouble(-3, 3), 16, r.nextDouble(-3, 3));
                    Vec3 dir = g.subtract(top).normalize();
                    FxSend.crescent(sl, WIND, top, dir, rightOf(dir), 0.9f, 0.4f, 6, (float) (top.distanceTo(g) / 5));
                    AstaFx.later(5, () -> {
                        FxSend.ring(sl, WIND, g.add(0, 0.1, 0), 0.2f, 1.8f, 0.06f, 6);
                        for (LivingEntity f : foesNear(sl, p, g.add(0, 1, 0), 2.6)) AstaFx.damage(p, f, power * 0.3f, "spirit_storm");
                    });
                }
                for (LivingEntity f : foesNear(sl, p, c.add(0, 1, 0), R)) {   // drawn toward the eye of the storm
                    Vec3 to = flat(c.subtract(f.position()));
                    f.setDeltaMovement(f.getDeltaMovement().scale(0.7).add(to.scale(0.12)));
                    f.hurtMarked = true;
                }
            });
        }
        AstaFx.later(62, () -> {
            if (!alive(p)) return;
            FxSend.impactFrame(sl, c, 3, false, 40);
            FxLib.shockRing(sl, WIND, c.add(0, 0.1, 0), 4f);
            for (LivingEntity f : foesNear(sl, p, c.add(0, 1, 0), R + 1)) {
                AstaFx.damage(p, f, power * 1.6f, "spirit_storm");
                f.setDeltaMovement(flat(f.position().subtract(c)).scale(1.4).add(0, 1.0, 0));
                f.hurtMarked = true;
            }
            sound(sl, c, SoundEvents.ENDER_DRAGON_FLAP, 1.2f, 0.6f);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.2f);
        });
    }

    // ---------------------------------------------------------------- Luck: Thunder God Overdrive
    /** Luck flashes through every enemy within 24 blocks, again and again, then calls a bolt down on each of them. */
    public static void overdrive(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 home = p.position();
        Set<LivingEntity> struck = new HashSet<>();
        final Vec3[] last = {p.position().add(0, 1, 0)};
        sound(sl, home, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.7f, 1.8f);
        for (int i = 0; i < 24; i++) {
            final int ii = i;
            AstaFx.later(4 + i * 2, () -> {
                if (!alive(p)) return;
                List<LivingEntity> fs = foesNear(sl, p, home, 24);
                if (fs.isEmpty()) return;
                LivingEntity t = fs.get(ii % fs.size());
                double a = ii * 2.39996;
                Vec3 stand = t.position().add(Math.cos(a) * 1.5, 0, Math.sin(a) * 1.5);
                p.teleportTo(stand.x, stand.y, stand.z);
                p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, mid(t));
                p.resetFallDistance();
                Vec3 now = p.position().add(0, 1, 0);
                LightningFx.bolt(sl, last[0], now, 0.25f, 4, 2);   // the path of the flash
                last[0] = now;
                AstaFx.damage(p, t, power * 0.3f, "thunder_rampage");
                t.setDeltaMovement(t.getDeltaMovement().scale(0.2));
                t.hurtMarked = true;
                struck.add(t);
                FxLib.impact(sl, BLUE_L, mid(t), 1.0f);
                if (ii % 6 == 0) FxSend.impactFrame(sl, mid(t), 1, false, 24);
                sound(sl, t.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.9f, 1.6f);
            });
        }
        AstaFx.later(4 + 24 * 2 + 4, () -> {
            if (!alive(p)) return;
            p.teleportTo(home.x, home.y, home.z);
            p.resetFallDistance();
            FxSend.impactFrame(sl, home, 4, true, 40);
            FxSend.shake(sl, home, 1.0f, 16, 48);
            for (LivingEntity t : struck) {
                if (!t.isAlive()) continue;
                LightningFx.bolt(sl, t.position().add(0, 26, 0), t.position(), 0.7f, 6, 5);
                AstaFx.damage(p, t, power * 0.9f, "thunder_rampage");
                FxSend.ring(sl, BLUE_L, t.position().add(0, 0.05, 0), 0.3f, 3f, 0.1f, 8);
            }
            sound(sl, home, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2f, 0.8f);
        });
    }

    // ---------------------------------------------------------------- Mereoleona: Hellfire Lion King
    /** A giant lion of crimson hellfire charges 30 blocks forward, burning everything it runs through, then erupts. */
    public static void lionKing(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 dir = flat(p.getLookAngle()), rt = rightOf(dir);
        Vec3 start = p.position().add(dir.scale(2)).add(0, 1.8, 0);
        final double speed = 1.2;
        final int ticks = 26;
        sound(sl, p.position(), SoundEvents.RAVAGER_ROAR, 1.2f, 0.6f);
        sound(sl, p.position(), SoundEvents.BLAZE_SHOOT, 1f, 0.5f);
        Set<LivingEntity> hit = new HashSet<>();
        for (int t = 0; t < ticks; t++) {
            final int tt = t;
            AstaFx.later(4 + t, () -> {
                if (!alive(p)) return;
                Vec3 head = start.add(dir.scale(tt * speed));
                Vec3 g = FxLib.ground(sl, head, 6);
                if (g != null) head = new Vec3(head.x, g.y + 1.8, head.z);   // it runs along the ground
                Vec3 vel = dir.scale(speed);
                fireball(sl, CRIMSON, head, vel, 1.6f, 1.6f, 1, false);                          // the head
                fireball(sl, CRIMSON, head.subtract(dir.scale(2.4)).add(0, -0.3, 0), vel, 1.4f, 1.4f, 1, false);   // the body
                if (tt % 3 == 0) {   // the mane flaring round the head
                    FxSend.wall(sl, CRIMSON, head.subtract(dir.scale(0.4)), dir, rt.cross(dir).scale(-1), 1.6f, 2.6f, -180, 180, 0.4f, 5, 0, 0);
                }
                if (tt % 2 == 0 && g != null) {   // paws leave burning prints
                    Vec3 side = rt.scale(tt % 4 == 0 ? 1.2 : -1.2);
                    FxSend.ring(sl, CRIMSON, g.add(side).add(0, 0.05, 0), 0.2f, 0.9f, 0.4f, 30);
                }
                for (LivingEntity f : foesNear(sl, p, head, 3.6)) {
                    if (hit.add(f)) {
                        AstaFx.damage(p, f, power * 0.9f, "calidos_brachium_purgatory");
                        FireFx.burn(p, f, 100);
                    }
                    f.setDeltaMovement(dir.scale(1.0).add(0, 0.25, 0));
                    f.hurtMarked = true;
                }
                if (tt % 6 == 0) sound(sl, head, SoundEvents.BLAZE_BURN, 1f, 0.6f);
            });
        }
        AstaFx.later(4 + ticks, () -> {
            if (!alive(p)) return;
            Vec3 end = start.add(dir.scale(ticks * speed));
            FxSend.impactFrame(sl, end, 3, false, 40);
            blast(p, sl, CRIMSON, end, 8, power * 1.6f, "calidos_brachium_purgatory");
            FxSend.beam(sl, CRIMSON, end.add(0, -1.5, 0), end.add(0, 14, 0), 2.6f, 18);
            sound(sl, end, SoundEvents.RAVAGER_ROAR, 1f, 0.8f);
        });
    }

    // ---------------------------------------------------------------- Nozel: Ten Thousand Regal Spears
    /** The sky above where you look fills with mercury spears, and they rain down for 2 seconds. */
    public static void tenThousand(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = aimPoint(p, 32);
        final double R = 10;
        FxSend.ring(sl, MERC, c.add(0, 18, 0), 0.5f, (float) R + 1, 0.15f, 50);   // the silver sky
        FxSend.ring(sl, SILVER, c.add(0, 0.1, 0), (float) R - 0.2f, (float) R, 0.08f, 50);
        sound(sl, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 0.5f);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int t = 0; t < 40; t++) {
            AstaFx.later(6 + t, () -> {
                if (!alive(p)) return;
                for (int k = 0; k < 2; k++) {
                    double a = r.nextDouble(Math.PI * 2), d = Math.sqrt(r.nextDouble()) * R;
                    Vec3 gp = c.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                    Vec3 g0 = FxLib.ground(sl, gp.add(0, 4, 0), 10);
                    final Vec3 g = g0 != null ? g0 : gp;
                    Vec3 top = g.add(r.nextDouble(-1.5, 1.5), 18, r.nextDouble(-1.5, 1.5));
                    Vec3 dir = g.subtract(top).normalize();
                    int fall = 5;
                    Vec3 vel = g.subtract(top).scale(1.0 / fall);
                    FxSend.spike(sl, MERC, top, dir, 3.0f, 0.2f, fall + 1, 0, FxPacket.POP, 0, vel, fall);
                    AstaFx.later(fall, () -> {
                        FxSend.spike(sl, MERC, g.subtract(dir.scale(1.6)), dir, 2.2f, 0.18f, 40, 0, FxPacket.POP);   // stuck in the ground
                        for (LivingEntity f : foesNear(sl, p, g.add(0, 1, 0), 1.8)) AstaFx.damage(p, f, power * 0.28f, "mercury_cage");
                    });
                }
                if (r.nextInt(4) == 0) sound(sl, c, SoundEvents.TRIDENT_HIT_GROUND, 0.7f, 1.3f);
            });
        }
        AstaFx.later(50, () -> {   // every spear bursts into silver at once
            if (!alive(p)) return;
            FxLib.flash(sl, SILVER, c.add(0, 1, 0), 2.2f);
            FxLib.shockRing(sl, MERC, c.add(0, 0.1, 0), (float) R * 0.35f);
            for (LivingEntity f : foesNear(sl, p, c.add(0, 1, 0), R)) AstaFx.damage(p, f, power * 1.0f, "mercury_cage");
            sound(sl, c, SoundEvents.GLASS_BREAK, 1f, 0.6f);
        });
    }

    // ---------------------------------------------------------------- Magna: Super Magna Special - Final
    /** One huge fireball, swung like a homerun: it flies until it hits something and goes up in a massive blast. */
    public static void magnaFinal(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 hand = p.getEyePosition().add(0, 1.6, 0);
        for (int t = 0; t < 10; t++) {   // tossed up, swelling
            final int tt = t;
            AstaFx.later(t, () -> { if (alive(p)) fireball(sl, FIRE, p.getEyePosition().add(0, 1.2 + tt * 0.08, 0), Vec3.ZERO, 0.6f + tt * 0.12f, 0.6f + tt * 0.12f, 1, false); });
        }
        sound(sl, hand, SoundEvents.FIRECHARGE_USE, 1f, 0.5f);
        AstaFx.later(10, () -> {
            if (!alive(p)) return;
            Vec3 dir = p.getLookAngle().normalize();
            sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.6f);
            FxSend.impactFrame(sl, p.position(), 2, false, 24);
            fly(p, sl, p.getEyePosition().add(dir.scale(1.5)), dir.scale(1.6), power, 0);
        });
    }

    private static void fly(ServerPlayer p, ServerLevel sl, Vec3 pos, Vec3 vel, float power, int step) {
        if (!alive(p)) return;
        Vec3 next = pos.add(vel);
        HitResult hr = sl.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        boolean wall = hr.getType() != HitResult.Type.MISS;
        boolean foe = !foesNear(sl, p, next, 2.2).isEmpty();
        if (wall || foe || step >= 40) {
            Vec3 at = wall ? hr.getLocation() : next;
            FxSend.impactFrame(sl, at, 5, false, 48);
            blast(p, sl, FIRE, at, 9, power * 2.5f, "fire_cannon");
            for (int k = 0; k < 6; k++) {   // the blast throws fire everywhere: smaller explosions round the edge
                final double a = Math.PI * 2 * k / 6;
                AstaFx.later(4 + k, () -> blast(p, sl, FIRE, at.add(Math.cos(a) * 6, 0, Math.sin(a) * 6), 3, power * 0.6f, "fire_cannon"));
            }
            return;
        }
        fireball(sl, FIRE, pos, vel, 2.0f, 2.0f, 1, false);
        if (step % 3 == 0) FxSend.trail(sl, FIRE, pos.subtract(vel.scale(2)), pos, 1.2f, 8);
        AstaFx.later(1, () -> fly(p, sl, next, vel, power, step + 1));
    }

    // ---------------------------------------------------------------- Zenon: Beelzebub - Full Spatial Release
    /** With the union on, bones close in a dome around you and every enemy within 18 blocks is sealed in a spatial cube that crushes it. */
    public static void spatialRelease(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        for (int k = 0; k < 16; k++) {   // the dome: long bones bursting up and bending in over you
            double a = Math.PI * 2 * k / 16;
            Vec3 g = FxLib.ground(sl, c.add(Math.cos(a) * 7, 1, Math.sin(a) * 7), 4);
            if (g == null) continue;
            Vec3 axis = new Vec3(-Math.cos(a) * 0.7, 1, -Math.sin(a) * 0.7).normalize();
            BoneFx.bone(sl, BoneFx.LONG, g.add(axis.scale(3)), axis, new Vec3(-Math.cos(a), 0, -Math.sin(a)), 3f, 0.28f, 120, 4 + k % 4, FxPacket.GROUND, 0, Vec3.ZERO, 0);
        }
        FxSend.impactFrame(sl, c, 3, false, 32);
        sound(sl, c, SoundEvents.BONE_BLOCK_PLACE, 1f, 0.4f);
        int k = 0;
        for (LivingEntity t : foesNear(sl, p, c, 18)) {
            final LivingEntity tt = t;
            AstaFx.later(6 + k * 2, () -> {
                if (!tt.isAlive() || !alive(p)) return;
                Vec3 m = mid(tt);
                float s = (float) Math.max(1.0, tt.getBbHeight() * 0.7);
                FxSend.box(sl, SILVER, m, new Vec3(1, 0, 0), new Vec3(0, 1, 0), s, s, s, 22, 3, FxPacket.POP);
                FxSend.box(sl, DARK, m, new Vec3(1, 0, 0), new Vec3(0, 1, 0), s * 0.97f, s * 0.97f, s * 0.97f, 22, 3, FxPacket.POP);
                AstaFx.damage(p, tt, power * 0.5f, "beelzebub_union");
                for (int h = 1; h <= 4; h++) AstaFx.later(h * 5, () -> {   // held inside the cube
                    if (!tt.isAlive()) return;
                    tt.teleportTo(m.x, m.y - tt.getBbHeight() * 0.5, m.z);
                    tt.setDeltaMovement(Vec3.ZERO);
                    tt.hurtMarked = true;
                });
                AstaFx.later(22, () -> {   // the cube collapses on it
                    if (!tt.isAlive() || !alive(p)) return;
                    FxLib.implode(sl, SILVER, m, 1.2f);
                    AstaFx.damage(p, tt, power * 1.4f, "beelzebub_union");
                    sound(sl, m, SoundEvents.ENDERMAN_TELEPORT, 0.9f, 0.5f);
                });
            });
            k++;
        }
    }

    // ---------------------------------------------------------------- Klaus: Steel Fortress
    /** A ring of steel walls rises around you for 10 s: magic and arrows can't cross it, and lances fire out of it. */
    public static void fortress(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        final double R = 6;
        for (int k = 0; k < 10; k++) {
            double a = Math.PI * 2 * k / 10;
            Vec3 g = FxLib.ground(sl, c.add(Math.cos(a) * R, 1, Math.sin(a) * R), 4);
            if (g == null) g = c.add(Math.cos(a) * R, 0, Math.sin(a) * R);
            Vec3 tan = new Vec3(-Math.sin(a), 0, Math.cos(a));
            FxSend.box(sl, STEEL, g.add(0, 1.6, 0), tan, new Vec3(0, 1, 0), 2.0f, 0.3f, 1.6f, 200, 5 + k % 3, FxPacket.GROUND);
        }
        FxSend.shake(sl, c, 0.5f, 10, 24);
        sound(sl, c, SoundEvents.ANVIL_PLACE, 1f, 0.5f);
        sound(sl, c, SoundEvents.IRON_DOOR_CLOSE, 1f, 0.5f);
        for (int t = 0; t < 200; t += 2) {
            final int tt = t;
            AstaFx.later(t, () -> {
                // nothing hostile crosses the wall
                for (Projectile pr : sl.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(R + 1.5, 6, R + 1.5), x -> x.getOwner() != p)) {
                    double d = pr.position().subtract(c).multiply(1, 0, 1).length();
                    if (Math.abs(d - R) < 1.5) {
                        FxLib.spark(sl, STEEL, pr.position(), 0.6f);
                        pr.discard();
                    }
                }
                if (tt % 10 != 0) return;
                // lances out of the wall at every enemy outside it
                for (LivingEntity f : foesNear(sl, p, c, 22)) {
                    Vec3 to = flat(mid(f).subtract(c));
                    if (f.position().distanceTo(c) <= R) continue;
                    Vec3 from = c.add(to.scale(R)).add(0, 1.2, 0);
                    Vec3 d = mid(f).subtract(from);
                    FxSend.spike(sl, STEEL, from, d, (float) d.length(), 0.18f, 6, 1, FxPacket.EXTEND);
                    AstaFx.damage(p, f, power * 0.35f, "acier_silva");
                    sound(sl, from, SoundEvents.TRIDENT_THROW, 0.7f, 0.7f);
                }
            });
        }
    }
}
