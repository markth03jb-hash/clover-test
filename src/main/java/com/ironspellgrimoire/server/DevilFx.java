package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/**
 * Devil Union versions of the Black Grimoire's moves. While the union lasts every move uses BOTH swords:
 * the sword in your main hand strikes first, then the other sword follows up. Everything is drawn in ember (palette 9)
 * and black. Each method is the effect of one devil spell (see AddonSpellRegistry).
 */
public final class DevilFx {
    private DevilFx() {}

    private static final int EMBER = 9, DARK = 1;

    private static boolean holds(ServerPlayer p, Item item) {
        return p.getMainHandItem().is(item) || p.getOffhandItem().is(item);
    }

    /** Devil Zetten: Twin Cleave - the main-hand sword's Zetten, then the other sword's, with an ember crater. */
    public static void zetten(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean dw = AstaFx.isDweller(p);
        AstaFx.zetten(p, power * 1.15f, dw);
        Vec3 dir = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).normalize();
        Vec3 hit = p.position().add(dir.scale(3.2));
        FxLib.shockRing(sl, EMBER, hit.add(0, 0.1, 0), 2.2f);
        AstaFx.later(7, () -> { if (alive(p)) AstaFx.zetten(p, power * 0.75f, !dw); });   // the second sword follows
        sound(sl, hit, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 0.6f, 0.7f);
    }

    /** Devil Sky Splitter: Twin Rift - two splitting cleaves, one per sword. */
    public static void skySplitter(ServerPlayer p, float power) {
        boolean dw = AstaFx.isDweller(p);
        AstaFx.skySplitter(p, power * 1.2f, dw);
        AstaFx.later(12, () -> { if (alive(p)) AstaFx.skySplitter(p, power * 0.6f, !dw); });
    }

    /**
     * Anti-Magic Domain: the anti magic wave, then a second, wider domain (16 blocks) that breaks every cast again and
     * cuts anyone carrying magic. Slayer: a crushing dome. Dweller: eight dark crescents fly out.
     */
    public static void antiMagic(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        AstaFx.antiMagic(p);
        boolean dw = AstaFx.isDweller(p);
        Vec3 c = p.position();
        if (dw) {
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8;
                FxLib.crescentShot(sl, DARK, c.add(0, 1, 0), new Vec3(Math.cos(a), 0, Math.sin(a)), 1.2f);
            }
        } else {
            FxLib.dome(sl, EMBER, c, 3.0f);
        }
        AstaFx.later(6, () -> {
            if (!alive(p)) return;
            FxLib.shockRing(sl, EMBER, c.add(0, 0.1, 0), 5.5f);
            for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16), x -> x != p && x.isAlive() && !x.isAlliedTo(p))) {
                if (t.distanceTo(p) > 16) continue;
                boolean magical = !t.getActiveEffects().isEmpty() || t instanceof IMagicEntity;
                if (t instanceof ServerPlayer sp && MagicData.getPlayerMagicData(sp).isCasting()) { Utils.serverSideCancelCast(sp); magical = true; }
                if (t instanceof IMagicEntity mage && mage.isCasting()) mage.cancelCast();
                if (!t.getActiveEffects().isEmpty()) t.removeAllEffects();
                if (magical) {
                    AstaFx.damage(p, t, power, "devil_anti_magic");
                    FxLib.impact(sl, EMBER, t.position().add(0, t.getBbHeight() * 0.5, 0), 1.0f);
                }
            }
            sound(sl, c, net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, 1f, 0.4f);
        });
    }

    /** Devil's Eye: Ki Sense out to 48 blocks for 20 seconds. */
    public static void kiSense(ServerPlayer p, float power) {
        AstaFx.kiSense(p, 48, 20 * 20);
        FxLib.halo(p.serverLevel(), EMBER, p.position().add(0, p.getBbHeight() + 0.3, 0), 1.4f);
    }

    /** Devil Instinct: a longer stance that counters every attack, harder. */
    public static void instinct(ServerPlayer p, float power) {
        AstaFx.instinct(p, true);
        FxLib.aura(p.serverLevel(), EMBER, p.position().add(0, 1, 0), 1.0f);
    }

    /** Devil Union: Release - see AstaFx.releaseUnion. */
    public static void release(ServerPlayer p, float power) {
        AstaFx.releaseUnion(p, power);
    }

    /** Devil Dweller: Triple Slash - three anti magic crescents in a row; with the Slayer also in hand it adds a sweeping cut. */
    public static void dwellerSlash(ServerPlayer p, float power) {
        for (int k = 0; k < 3; k++) {
            AstaFx.later(1 + k * 3, () -> { if (alive(p)) AstaFx.dwellerSlash(p, power * 0.8f, false); });
        }
        if (holds(p, AddonItemRegistry.DEMON_SLAYER_SWORD.get())) {
            AstaFx.later(10, () -> { if (alive(p)) SlashProjectiles.horizontalSlash(p, power * 0.7f); });
        }
        FxLib.crossSlash(p.serverLevel(), EMBER, p.getEyePosition().add(p.getLookAngle().scale(1.5)), p.getLookAngle(), 1.0f);
    }

    /** Devil Borrowed: Four Elements - fire, ice, lightning and wind slashes back to back, in a random order. */
    public static void borrowedSlash(ServerPlayer p, float power) {
        java.util.List<String> order = new java.util.ArrayList<>(java.util.List.of("fire", "ice", "lightning", "wind"));
        java.util.Collections.shuffle(order, new java.util.Random(p.getRandom().nextLong()));   // all four, in a new order each cast
        String[] els = order.toArray(new String[0]);
        for (int k = 0; k < 4; k++) {
            final String el = els[k];
            AstaFx.later(1 + k * 4, () -> { if (alive(p)) AstaFx.dwellerSlash(p, power * 0.75f, true, el); });
        }
    }
    // ================================================================== Black Divider
    private static final int DIVIDER_CUT = 9;

    private static Vec3 onSegment(Vec3 a, Vec3 b, Vec3 q) {
        Vec3 ab = b.subtract(a);
        double t = Math.max(0, Math.min(1, q.subtract(a).dot(ab) / ab.lengthSqr()));
        return a.add(ab.scale(t));
    }

    private static java.util.List<LivingEntity> foes(ServerLevel sl, ServerPlayer p, net.minecraft.world.phys.AABB box) {
        return sl.getEntitiesOfClass(LivingEntity.class, box, x -> x != p && x.isAlive() && !x.isAlliedTo(p));
    }

    /**
     * Black Divider: Asta raises the sword overhead with both hands while anti magic floods into it, then brings it
     * down in one cut that releases a towering black blade with an ember core. It splits the ground along
     * a 40-block line, cancels the magic of everything it passes (casts broken, magic projectiles erased) and
     * cuts every enemy in its path.
     */
    public static void blackDivider(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.WITHER_SHOOT, 0.7f, 0.5f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, 0.8f, 0.4f);
        for (int t = 0; t < DIVIDER_CUT; t += 2) {   // anti magic flooding the raised blade
            final int tt = t;
            AstaFx.later(t, () -> {
                if (!alive(p)) return;
                double yr = Math.toRadians(p.getYRot());
                Vec3 fw = new Vec3(-Math.sin(yr), 0, Math.cos(yr));
                Vec3 hilt = p.position().add(0, 2.0, 0).add(fw.scale(0.1));
                Vec3 tip = hilt.add(0, 1.7 + tt * 0.06, 0).subtract(fw.scale(0.4));
                FxSend.trail(sl, DARK, hilt, tip, 0.35f + tt * 0.03f, 3);
                FxSend.trail(sl, EMBER, hilt, tip, 0.12f, 3);
                if (tt % 4 == 0) FxSend.ring(sl, EMBER, p.position().add(0, 0.05, 0), 3.5f - tt * 0.3f, 0.3f, 0.06f, 5);
            });
        }
        AstaFx.later(DIVIDER_CUT, () -> {
            if (!alive(p)) return;
            Vec3 look = p.getLookAngle();
            boolean aimed = Math.abs(look.y) > 0.3;
            Vec3 flat = new Vec3(look.x, 0, look.z).lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : new Vec3(look.x, 0, look.z).normalize();
            Vec3 dir = aimed ? look.normalize() : flat;
            Vec3 rt = rightOf(dir), up = aimed ? rt.cross(dir).normalize() : new Vec3(0, 1, 0);
            Vec3 start = aimed ? p.getEyePosition().add(0, -0.3, 0) : FxLib.floor(sl, p.position());
            final double reach = 40;
            final float speed = 3.6f;
            Vec3 end = start.add(dir.scale(reach));
            FxSend.shake(sl, p.position(), 0.8f, 12, 40);
            final boolean[] framed = {false};
            Vec3 o = start.add(dir.scale(1.2)).add(aimed ? Vec3.ZERO : up.scale(1.0));
            int life = (int) (reach / speed) + 2;
            FxSend.slash(sl, DARK, o, dir, up, 3.0f, 12f, life, speed, 1.2f);          // the black blade
            FxSend.slash(sl, EMBER, o.add(dir.scale(0.3)), dir, up, 2.4f, 9f, life, speed, 0.5f);   // its ember core
            for (LivingEntity t : foes(sl, p, new net.minecraft.world.phys.AABB(start, end).inflate(7))) {
                Vec3 m = t.position().add(0, t.getBbHeight() * 0.5, 0), on = onSegment(start, end, m), off = m.subtract(on);
                if (Math.abs(off.dot(rt)) > 3.0 || Math.abs(off.dot(up)) > 6.5) continue;
                final LivingEntity tt = t;
                AstaFx.later((int) (on.distanceTo(start) / speed), () -> {
                    if (!tt.isAlive() || !alive(p)) return;
                    AstaFx.damage(p, tt, power, "devil_black_divider");
                    if (!framed[0]) { framed[0] = true; FxSend.impactFrame(sl, tt.position().add(0, tt.getBbHeight() * 0.5, 0), 3, FxSend.FRAME_ASTA_SLASH, 32); }
                    if (tt instanceof ServerPlayer sp && MagicData.getPlayerMagicData(sp).isCasting()) Utils.serverSideCancelCast(sp);   // anti magic
                    if (tt instanceof IMagicEntity mage && mage.isCasting()) mage.cancelCast();
                    FxLib.impact(sl, EMBER, tt.position().add(0, tt.getBbHeight() * 0.5, 0), 1.3f);
                    tt.setDeltaMovement(tt.getDeltaMovement().add(dir.scale(0.9)).add(0, 0.3, 0));
                    tt.hurtMarked = true;
                });
            }
            // the cut splits the ground and erases magic as it passes
            int n = 0;
            for (double k = 3; k < reach; k += 4, n++) {
                final double kk = k;
                final int nn = n;
                AstaFx.later((int) (k / speed), () -> {
                    Vec3 a0 = aimed ? start.add(dir.scale(kk - 4)) : FxLib.floor(sl, start.add(dir.scale(kk - 4)));
                    Vec3 a1 = aimed ? start.add(dir.scale(kk)) : FxLib.floor(sl, start.add(dir.scale(kk)));
                    FxSend.trail(sl, DARK, a0, a1, 0.5f, 40);
                    FxSend.trail(sl, EMBER, a0, a1, 0.12f, 36);
                    for (net.minecraft.world.entity.projectile.Projectile pr : sl.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                            new net.minecraft.world.phys.AABB(a1, a1).inflate(3.5, 6, 3.5), x -> x.getOwner() != p)) {
                        FxLib.spark(sl, EMBER, pr.position(), 0.6f);
                        pr.discard();
                    }
                });
            }
            FxLib.shockRing(sl, EMBER, p.position().add(0, 0.05, 0), 1.6f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, 0.9f, 0.7f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 0.9f, 0.6f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.5f);
        });
    }

    // ================================================================== Black Hurricane
    private static final int HURRICANE_TICKS = 40;

    /**
     * Black Hurricane: Asta spins with both swords out and becomes the eye of a vortex of black flame and embers.
     * It drags everything nearby in and shreds it for 2 seconds (and you can move it), then bursts outward,
     * launching everyone caught in it.
     */
    public static void blackHurricane(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 1f, 0.5f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 0.7f, 0.6f);
        for (int t = 0; t < HURRICANE_TICKS; t++) {
            final int tt = t;
            AstaFx.later(t, () -> {
                if (!alive(p)) return;
                Vec3 base = p.position();
                float r = 2.5f + Math.min(1f, tt / 8f) * 2.5f;
                hurricanePacket(sl, base, r, 7f, tt, p.getDeltaMovement());
                if (tt % 4 == 0)   // the swords' black cuts whirling round inside it
                    FxLib.spinSlash(sl, tt % 8 == 0 ? DARK : EMBER, base.add(0, 0.8 + (tt % 12) * 0.12, 0), new Vec3(Math.cos(tt), 0, Math.sin(tt)), 1.1f);
                // everything near is dragged in and lifted
                for (LivingEntity t2 : foes(sl, p, p.getBoundingBox().inflate(10))) {
                    Vec3 to = base.subtract(t2.position()).multiply(1, 0, 1);
                    double d = to.length();
                    if (d > 10) continue;
                    Vec3 tan = new Vec3(-to.z, 0, to.x).normalize();
                    Vec3 pull = (d > 1.2 ? to.normalize().scale(0.22) : Vec3.ZERO).add(tan.scale(0.2)).add(0, d < r + 1 ? 0.08 : 0.0, 0);
                    t2.setDeltaMovement(t2.getDeltaMovement().scale(0.6).add(pull));
                    t2.hurtMarked = true;
                    t2.resetFallDistance();
                    if (tt % 5 == 0 && d < r + 1.5) AstaFx.damage(p, t2, power, "devil_black_hurricane");
                }
                if (tt % 10 == 0) sound(sl, base, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.5f + tt * 0.01f);
            });
        }
        AstaFx.later(HURRICANE_TICKS, () -> {   // the burst
            if (!alive(p)) return;
            Vec3 c = p.position();
            FxLib.shockRing(sl, EMBER, c.add(0, 0.1, 0), 3.0f);
            AstaFx.later(2, () -> FxLib.shockRing(sl, DARK, c.add(0, 0.1, 0), 4.0f));
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8;
                FxLib.crescentShot(sl, k % 2 == 0 ? DARK : EMBER, c.add(0, 1, 0), new Vec3(Math.cos(a), 0, Math.sin(a)), 1.2f);
            }
            FxSend.shake(sl, c, 0.6f, 10, 24);
            for (LivingEntity t : foes(sl, p, p.getBoundingBox().inflate(8))) {
                if (t.distanceTo(p) > 8) continue;
                AstaFx.damage(p, t, power * 3f, "devil_black_hurricane");
                Vec3 away = t.position().subtract(c).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
                t.setDeltaMovement(away.scale(1.6).add(0, 0.9, 0));
                t.hurtMarked = true;
            }
            sound(sl, c, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 1f, 0.6f);
            sound(sl, c, net.minecraft.sounds.SoundEvents.WITHER_SHOOT, 0.6f, 0.6f);
        });
    }

    private static void hurricanePacket(ServerLevel sl, Vec3 base, float r, float h, int age, Vec3 vel) {
        com.ironspellgrimoire.network.FxPacket pk = new com.ironspellgrimoire.network.FxPacket();
        pk.shape = 28; pk.palette = (byte) EMBER; pk.life = 1; pk.c = base; pk.v = vel; pk.r = r; pk.h = h; pk.a0 = age;
        com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.NEAR.with(
                () -> new net.minecraftforge.network.PacketDistributor.TargetPoint(base.x, base.y, base.z, 128, sl.dimension())), pk);
    }
}
