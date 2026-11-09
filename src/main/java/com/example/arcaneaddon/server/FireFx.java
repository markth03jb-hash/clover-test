package com.example.arcaneaddon.server;

import static com.example.arcaneaddon.server.FxUtil.*;
import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.item.FireGrimoireItem;
import com.example.arcaneaddon.network.AddonNetwork;
import com.example.arcaneaddon.network.FxPacket;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Magna Swing's Flame Magic - the ten spells of the Fire Grimoire and its passive. Loud, straightforward fire:
 * fireballs, a flaming bat, burning fists, his broom, and his secret Soul Chain Deathmatch.
 *
 *  1 Exploding Fireball      - a fireball that explodes on whatever it hits
 *  2 Explosive Scattershot   - five fireballs in a spread
 *  3 Super Magna Special     - toss up a huge fireball and bat it at them
 *  4 Flaming Bat             - three swings of a bat of fire
 *  5 Flame Knuckles          - knuckledusters of fire: your punches explode for 12 s
 *  6 Ring of Fire            - a ring of fire walls around you for 5 s
 *  7 Fireball Barrage        - a stream of twelve fireballs thrown as fast as your arms can go
 *  8 Fireball Rain           - fireballs fall from the sky on the area you aim at
 *  9 Soul Chain Deathmatch   - a burning chain binds you and one enemy: they can't get away and take more damage from you
 * 10 Explosive Fire Cannon   - gather everything into one giant fireball and fire it
 *
 * Passive "Delinquent's Guts": every hit of these spells sets the target burning for 3 s, and below 30% health your
 * fire hits 25% harder.
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public class FireFx {
    private static final int F = Fx.FIRE;

    private static final Map<UUID, Long> KNUCKLES = new HashMap<>();
    private static final Map<UUID, Chain> CHAINS = new HashMap<>();
    private static final class Chain { LivingEntity target; long until; }
    private static final List<Burn> BURNS = new ArrayList<>();
    private static final class Burn { LivingEntity e; ServerPlayer src; long until; }
    private static final Map<UUID, Long> PUNCH_CD = new HashMap<>();

    // ------------------------------------------------------------------ helpers

    private static boolean wearsFireGrimoire(ServerPlayer p) {
        ItemStack s = Utils.getPlayerSpellbookStack(p);
        return s != null && s.getItem() instanceof FireGrimoireItem;
    }

    private static Vec3 handPos(ServerPlayer p) {
        double yr = Math.toRadians(p.getYRot());
        double lx = Math.cos(yr), lz = Math.sin(yr), fx = -Math.sin(yr), fz = Math.cos(yr);
        return new Vec3(p.getX() + fx * 0.6 - lx * 0.35, p.getY() + p.getEyeHeight() * 0.8, p.getZ() + fz * 0.6 - lz * 0.35);
    }

    /** A fire hit: damage (25% more when you're below 30% health), and the target burns for 3 s. */
    private static void hit(ServerPlayer p, LivingEntity t, float dmg, String id) {
        if (p.getHealth() < p.getMaxHealth() * 0.3f) dmg *= 1.25f;
        Chain c = CHAINS.get(p.getUUID());
        if (c != null && c.target == t) dmg *= 1.3f;   // Soul Chain: they can't escape your fire
        AstaFx.damage(p, t, dmg, id);
        burn(p, t, 60);
    }

    /** Our own burning (not the vanilla fire effect): 1 damage a second, drawn as flames licking the body. */
    static void burn(ServerPlayer src, LivingEntity t, int ticks) {
        long until = t.level().getGameTime() + ticks;
        for (Burn b : BURNS) if (b.e == t) { b.until = Math.max(b.until, until); b.src = src; sendBurn(t, ticks); return; }
        Burn b = new Burn(); b.e = t; b.src = src; b.until = until;
        BURNS.add(b);
        sendBurn(t, ticks);
    }

    private static void sendBurn(LivingEntity t, int life) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 21; pkt.id = t.getId(); pkt.palette = (byte) F; pkt.life = life;
        AddonNetwork.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> t), pkt);
    }

    /** A ball of fire (client draws a churning, glowing fireball): shape 20. explode = a blast that swells from r to r2. */
    static void fireball(ServerLevel sl, Vec3 c, Vec3 vel, float r, float r2, int life, boolean explode) {
        FxPacket p = new FxPacket();
        if (life <= 3 && life > 0) life = 1;   // per-tick copies: the client moves each along its velocity through its tick
        p.shape = 20; p.palette = (byte) F; p.life = life; p.c = c; p.v = vel; p.r = r; p.r2 = r2; p.h = explode ? 1 : 0;
        AddonNetwork.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(c.x, c.y, c.z, 128, sl.dimension())), p);
    }

    /** An explosion of fire: a swelling fireball, a shock ring on the ground, a hit on everything in range. */
    private static void explode(ServerPlayer p, ServerLevel sl, Vec3 at, double R, float dmg, String id) {
        fireball(sl, at, Vec3.ZERO, (float) R * 0.3f, (float) R, 10, true);
        Vec3 g = FxLib.ground(sl, at.add(0, 0.5, 0), R + 1);
        if (g != null) FxLib.shockRing(sl, F, g.add(0, 0.05, 0), (float) R * 0.35f);
        for (LivingEntity t : enemies(sl, p, new AABB(at, at).inflate(R))) {
            double d = mid(t).distanceTo(at);
            if (d > R + t.getBbWidth() * 0.5) continue;
            hit(p, t, dmg * (float) (1.0 - 0.4 * Math.min(1, d / R)), id);
            Vec3 away = mid(t).subtract(at);
            if (away.lengthSqr() > 1.0E-4) t.setDeltaMovement(t.getDeltaMovement().add(away.normalize().scale(0.6 + R * 0.08)).add(0, 0.3, 0));
            t.hurtMarked = true;
        }
        Cutshake.send(sl, at, (float) Math.min(1.0, R * 0.12), 6);
        sound(sl, at, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, (float) Math.min(1.5, 0.5 + R * 0.12), 1.0f);
        sound(sl, at, net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, 1f, 0.7f);
    }

    /** Screen shake for players near an explosion (FxPacket shape 10). */
    private static final class Cutshake {
        static void send(ServerLevel sl, Vec3 at, float strength, int life) {
            FxPacket p = new FxPacket();
            p.shape = 10; p.c = at; p.r = strength; p.life = life;
            AddonNetwork.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, 24, sl.dimension())), p);
        }
    }

    /** A flying fireball: moves {@code speed} a tick, explodes on the first enemy or block it meets. */
    private static void shot(ServerPlayer p, ServerLevel sl, Vec3 pos, Vec3 dir, float speed, float r, double blast, float dmg, int step, int max, String id) {
        if (!alive(p)) return;
        Vec3 want = pos.add(dir.scale(speed));
        Vec3 next = clip(sl, p, pos, want);
        fireball(sl, next, dir.scale(speed), r, r, 2, false);
        FxSend.trail(sl, F, pos, next, r * 0.7f, 6);
        for (LivingEntity t : enemies(sl, p, new AABB(pos, next).inflate(r + 0.5))) {
            if (!t.getBoundingBox().inflate(r).clip(pos, next).isPresent()) continue;
            explode(p, sl, mid(t), blast, dmg, id);
            return;
        }
        if (next.distanceToSqr(want) > 1.0E-4 || step >= max) { explode(p, sl, next, blast, dmg, id); return; }
        AstaFx.later(1, () -> shot(p, sl, next, dir, speed, r, blast, dmg, step + 1, max, id));
    }

    /** Fire gathering in your hand while the animation winds up. */
    private static void windup(ServerPlayer p, int ticks, float size) {
        ServerLevel sl = p.serverLevel();
        for (int k = 0; k < ticks; k += 2) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (alive(p)) fireball(sl, handPos(p), Vec3.ZERO, (0.12f + kk * 0.02f) * size, (0.12f + kk * 0.02f) * size, 3, false);
            });
        }
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, 0.5f, 1.4f);
    }

    // ================================================================== 1. Exploding Fireball
    private static final int FIREBALL_RELEASE = 8;

    /** A fireball, 2.5 blocks a tick, that explodes (3 blocks) on whatever it hits. */
    public static void explodingFireball(ServerPlayer p, float power) {
        windup(p, FIREBALL_RELEASE, 1f);
        AstaFx.later(FIREBALL_RELEASE, () -> {
            if (!alive(p)) return;
            shot(p, p.serverLevel(), handPos(p), p.getLookAngle(), 2.5f, 0.45f, 3.0, power, 0, 16, "exploding_fireball");
            sound(p.serverLevel(), p.position(), net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, 1f, 0.9f);
        });
    }

    // ================================================================== 2. Explosive Scattershot
    private static final int SCATTER_RELEASE = 9;

    /** Five fireballs in a fan, each exploding where it lands. */
    public static void scattershot(ServerPlayer p, float power) {
        windup(p, SCATTER_RELEASE, 1.2f);
        AstaFx.later(SCATTER_RELEASE, () -> {
            if (!alive(p)) return;
            ServerLevel sl = p.serverLevel();
            Vec3 look = p.getLookAngle();
            for (int i = 0; i < 5; i++) {
                Vec3 d = rotY(look, Math.toRadians((i - 2) * 9)).add(0, (i % 2) * 0.05, 0).normalize();
                AstaFx.later(i % 2, () -> shot(p, sl, handPos(p), d, 2.2f, 0.32f, 2.2, power * 0.55f, 0, 14, "explosive_scattershot"));
            }
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, 1f, 1.2f);
        });
    }

    // ================================================================== 3. Super Magna Special
    static final int SPECIAL_HIT = 14;

    /**
     * A bat of fire in your hands, a huge fireball tossed up in front of you... and you bat it straight at them: it
     * flies 3 blocks a tick and explodes 5 blocks wide.
     */
    public static void magnaSpecial(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        // the toss: straight up in front of your face, hanging at the top of its arc right where the bat will meet it
        for (int t = 0; t <= SPECIAL_HIT; t++) {
            final int tt = t;
            AstaFx.later(t, () -> {
                if (!alive(p)) return;
                Vec3 at = tossPos(p, tt), next = tossPos(p, Math.min(SPECIAL_HIT, tt + 1));
                fireball(sl, at, next.subtract(at), 0.4f + tt * 0.025f, 0.4f + tt * 0.025f, 2, false);
            });
        }
        batSwing(p, SPECIAL_SWING, SPECIAL_HIT + 8);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, 1f, 0.8f);
        AstaFx.later(SPECIAL_HIT, () -> {
            if (!alive(p)) return;
            Vec3 look = p.getLookAngle();
            Vec3 at = tossPos(p, SPECIAL_HIT);
            FxSend.slash(sl, F, at.subtract(look.scale(0.4)), look, rightOf(look), 1.4f, 1.9f, 4, 0f, 0.9f);   // the swing connecting
            FxLib.flash(sl, F, at, 1.0f);
            Cutshake.send(sl, at, 0.35f, 4);
            shot(p, sl, at, look, 3.0f, 0.75f, 5.0, power, 0, 16, "magna_special");
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ANVIL_LAND, 0.4f, 1.8f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, 1.2f, 0.7f);
        });
    }

    /** Where the tossed ball is: centred in front of your face, rising and hanging. */
    static Vec3 tossPos(ServerPlayer p, int t) {
        double k = Math.min(1.0, t / (double) SPECIAL_HIT);
        double lift = Math.sin(Math.PI * 0.5 * Math.min(1.0, k * 1.4)) * 1.0;   // up fast, then hanging
        return p.getEyePosition().add(p.getLookAngle().scale(1.7)).add(0, -0.7 + lift, 0);
    }

    // ------------------------------------------------------------------ the flaming bat
    /** Bat keyframes: {tick, yaw (deg, + = to your right), pitch (deg, + = up)} - matched to the swing animations. */
    static final double[][] SPECIAL_SWING = {{0, 40, -50}, {4, 110, 25}, {12, 115, 30}, {14, -70, 10}, {17, -105, 0}, {22, -40, -50}};
    private static final double[][] COMBO_SWING = {{0, 30, -50}, {3, 100, 10}, {5, -80, 10}, {9, -95, 10}, {12, 80, 10},
            {17, 0, 85}, {20, 0, -40}, {26, 30, -55}};

    private static double[] batAt(double[][] keys, double t) {
        if (t <= keys[0][0]) return new double[]{keys[0][1], keys[0][2]};
        for (int i = 1; i < keys.length; i++) {
            if (t <= keys[i][0]) {
                double f = (t - keys[i - 1][0]) / (keys[i][0] - keys[i - 1][0]);
                f = f * f * (3 - 2 * f);
                return new double[]{keys[i - 1][1] + (keys[i][1] - keys[i - 1][1]) * f, keys[i - 1][2] + (keys[i][2] - keys[i - 1][2]) * f};
            }
        }
        double[] l = keys[keys.length - 1];
        return new double[]{l[1], l[2]};
    }

    /** Grip and direction of the bat at a given yaw/pitch relative to where you face. */
    private static Vec3[] batFrame(ServerPlayer p, double yaw, double pitch) {
        Vec3 look = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).normalize();
        Vec3 rt = rightOf(look);
        double y = Math.toRadians(yaw), pt = Math.toRadians(pitch);
        Vec3 flat = look.scale(Math.cos(y)).add(rt.scale(Math.sin(y)));
        Vec3 dir = flat.scale(Math.cos(pt)).add(0, Math.sin(pt), 0).normalize();
        Vec3 grip = p.position().add(0, 1.15, 0).add(look.scale(0.3)).add(dir.scale(0.25));
        return new Vec3[]{grip, dir};
    }

    /** A baseball bat of fire in your hands, following the swing keyframes every tick for {@code ticks} ticks. */
    static void batSwing(ServerPlayer p, double[][] keys, int ticks) {
        ServerLevel sl = p.serverLevel();
        for (int t = 0; t <= ticks; t++) {
            final int tt = t;
            AstaFx.later(t, () -> {
                if (!alive(p)) return;
                double[] a = batAt(keys, tt), b = batAt(keys, tt + 1);
                Vec3[] f0 = batFrame(p, a[0], a[1]), f1 = batFrame(p, b[0], b[1]);
                drawBat(sl, f0[0], f0[1], f1[0], f1[1]);
            });
        }
    }

    /** The bat: a slim handle with a knob, swelling into a thick barrel, all burning. Moves toward the next tick's pose. */
    private static void drawBat(ServerLevel sl, Vec3 grip, Vec3 dir, Vec3 grip1, Vec3 dir1) {
        Vec3 up = rightOf(dir).cross(dir).normalize();
        float[][] parts = {   // {centre along the bat, half length, half width}
                {-0.05f, 0.05f, 0.06f},   // knob
                {0.25f, 0.3f, 0.035f},    // handle
                {0.75f, 0.22f, 0.07f},    // taper
                {1.12f, 0.2f, 0.1f},      // barrel
        };
        for (float[] pt : parts) {
            Vec3 c0 = grip.add(dir.scale(pt[0])), c1 = grip1.add(dir1.scale(pt[0]));
            FxSend.box(sl, F, c0, dir, up, pt[1], pt[2], pt[2], 2, 0, FxPacket.HOLD, 0, c1.subtract(c0), 0);
        }
    }

    // ================================================================== 4. Flaming Bat
    private static final int[] BAT_SWINGS = {5, 12, 20};

    /** A bat of fire: three heavy swings - two sweeps and an overhead smash - each one an arc of fire 5 blocks ahead. */
    public static void flamingBat(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        batSwing(p, COMBO_SWING, 26);   // the bat itself, in your hands, following every swing
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, 0.8f, 1.2f);
        for (int i = 0; i < 3; i++) {
            final int ii = i;
            AstaFx.later(BAT_SWINGS[i], () -> {
                if (!alive(p)) return;
                Vec3 look = p.getLookAngle(), rt = rightOf(look), up = rt.cross(look).normalize();
                Vec3 span = ii == 2 ? up : rt.scale(ii == 0 ? 1 : -1).add(up.scale(0.2)).normalize();
                Vec3 o = p.getEyePosition().add(0, -0.4, 0).add(look.scale(1.1));
                FxSend.slash(sl, F, o, look, span, 1.8f, 2.6f, 5, 0.4f, 0.9f);   // the arc of fire the bat throws
                for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(5))) {
                    Vec3 to = mid(t).subtract(p.getEyePosition());
                    if (to.length() > 5 || to.normalize().dot(look) < 0.35) continue;
                    hit(p, t, power * (ii == 2 ? 0.5f : 0.35f), "flaming_bat");
                    t.setDeltaMovement(t.getDeltaMovement().add(look.scale(ii == 2 ? 0.4 : 0.9)).add(0, ii == 2 ? -0.4 : 0.25, 0));
                    t.hurtMarked = true;
                    FxLib.impact(sl, F, mid(t), 0.9f);
                }
                if (ii == 2) {
                    Vec3 g = FxLib.ground(sl, o.add(look.scale(2)), 4);
                    if (g != null) { FxLib.shockRing(sl, F, g.add(0, 0.05, 0), 1.0f); Cutshake.send(sl, g, 0.3f, 4); }
                }
                sound(sl, p.position(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.8f);
                sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, 0.5f, 1.4f);
            });
        }
    }

    // ================================================================== 5. Flame Knuckles
    /** Knuckledusters of fire for 12 s: every punch explodes on what it hits (+5 damage, a small blast, burning). */
    public static void flameKnuckles(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        KNUCKLES.put(p.getUUID(), sl.getGameTime() + 240);
        sendKnuckles(p, 240);
        FxLib.flash(sl, F, handPos(p), 0.8f);
        FxSend.ring(sl, F, p.position().add(0, 0.05, 0), 0.4f, 2.4f, 0.1f, 8);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, 1f, 1.0f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON, 1f, 0.8f);
    }

    private static void sendKnuckles(ServerPlayer p, int life) {
        ModeHud.show(p, "flame_knuckles", life);
        FxPacket pkt = new FxPacket();
        pkt.shape = 22; pkt.id = p.getId(); pkt.palette = (byte) F; pkt.life = life;
        AddonNetwork.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }

    // ================================================================== 6. Ring of Fire
    /** Walls of fire spring up in a ring 5 blocks around you for 5 s: anything that touches them burns and is thrown back. */
    public static void ringOfFire(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 c0 = FxLib.floor(sl, p.position());
        final Vec3 c = c0 != null ? c0 : p.position();
        final double R = 7;
        for (int t = 0; t < 100; t += 4) {
            final int tt = t;
            AstaFx.later(t, () -> {
                if (!alive(p)) return;
                float h = tt < 8 ? 0.8f + tt * 0.35f : 3.2f + 0.4f * (float) Math.sin(tt * 0.4);
                FxSend.wall(sl, F, c.add(0, h * 0.5, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), (float) R, (float) R, -180, 180, h * 0.5f, 6, 0, 0);
                for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(R + 1.5, 3, R + 1.5))) {
                    Vec3 off = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
                    double d = off.length();
                    if (Math.abs(d - R) > 1.0) continue;
                    hit(p, e, power * 0.3f, "ring_of_fire");
                    Vec3 push = off.lengthSqr() < 1.0E-4 ? Vec3.ZERO : off.normalize().scale(d > R ? 0.8 : -0.8);
                    e.setDeltaMovement(e.getDeltaMovement().add(push).add(0, 0.25, 0));
                    e.hurtMarked = true;
                }
                if (tt % 20 == 0) sound(sl, c, net.minecraft.sounds.SoundEvents.BLAZE_BURN, 1f, 0.8f);
            });
        }
        FxLib.shockRing(sl, F, c.add(0, 0.05, 0), 1.2f);
        sound(sl, c, net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, 1f, 0.6f);
    }

    // ================================================================== 7. Fireball Barrage
    private static final int BARRAGE_START = 6, BARRAGE_SHOTS = 12;

    /**
     * Throw fireballs as fast as your arms can go: twelve in 1.2 s, alternating hands, each one chasing where you look
     * at that moment (so you can sweep the stream across a crowd), each exploding where it lands.
     */
    public static void fireballBarrage(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        // six overhand pitches, right / left in turn every 4 ticks (matching the animation); each one lets two
        // fireballs go - the same twelve shots and damage as before
        for (int i = 0; i < BARRAGE_SHOTS; i++) {
            final int ii = i, throwNo = i / 2;
            AstaFx.later(BARRAGE_START + throwNo * 4 + (i % 2), () -> {
                if (!alive(p)) return;
                Vec3 look = p.getLookAngle();
                Vec3 side = rightOf(look).scale(throwNo % 2 == 0 ? 0.4 : -0.4);
                Vec3 from = p.getEyePosition().add(look.scale(0.8)).add(side).add(0, -0.15, 0);
                ThreadLocalRandom r = ThreadLocalRandom.current();
                Vec3 d = look.add(r.nextDouble(-0.04, 0.04), r.nextDouble(-0.03, 0.03), r.nextDouble(-0.04, 0.04)).normalize();
                shot(p, sl, from, d, 2.6f, 0.3f, 1.8, power * 0.3f, 0, 14, "fireball_barrage");
                if (ii % 2 == 0) sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, 0.7f, 1.1f + r.nextFloat() * 0.3f);
            });
        }
        windup(p, BARRAGE_START, 1f);
    }

    // ================================================================== 8. Fireball Rain
    private static final int RAIN_START = 10;

    /** Throw a fireball high into the sky: it splits, and fireballs rain down for 2 s on a 6-block circle where you aimed. */
    public static void fireballRain(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 hitAt = clip(sl, p, eye, eye.add(p.getLookAngle().scale(32)));
        Vec3 g = FxLib.ground(sl, hitAt.add(0, 0.5, 0), 12);
        final Vec3 c = g != null ? g : hitAt;
        FxSend.ring(sl, F, c.add(0, 0.05, 0), 5.8f, 6f, 0.05f, RAIN_START + 44);
        AstaFx.later(5, () -> { if (alive(p)) shotUp(p, sl); });   // thrown as the arm goes up
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 9; i++) {
            AstaFx.later(RAIN_START + i * 4 + r.nextInt(3), () -> {
                if (!alive(p)) return;
                double a = r.nextDouble(Math.PI * 2), d = Math.sqrt(r.nextDouble()) * 6;
                Vec3 land = c.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                Vec3 top = land.add(r.nextDouble(-3, 3), 18, r.nextDouble(-3, 3));
                Vec3 dir = land.subtract(top).normalize();
                shot(p, sl, top, dir, 2.2f, 0.5f, 2.6, power * 0.5f, 0, 14, "fireball_rain");
            });
        }
    }

    private static void shotUp(ServerPlayer p, ServerLevel sl) {
        Vec3 h = handPos(p);
        for (int k = 0; k < 5; k++) {
            final int kk = k;
            AstaFx.later(k, () -> fireball(sl, h.add(0, kk * 3.5, 0), new Vec3(0, 3.5, 0), 0.5f, 0.5f, 2, false));
        }
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, 1f, 0.6f);
    }

    // ================================================================== 9. Soul Chain Deathmatch
    private static final int CHAIN_TICKS = 10 * 20;

    /**
     * Secret Fire Magic: a fireball carrying a spell trap hits the enemy you aim at, and a burning chain binds you to
     * each other for 10 s. They can't get further than 8 blocks from you, and they take 30% more damage from your fire.
     */
    public static void soulChain(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity target = null;
        double best = Math.cos(Math.toRadians(14));
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(30))) {
            Vec3 to = mid(t).subtract(eye);
            if (to.length() > 30 || !p.hasLineOfSight(t)) continue;
            double d = to.normalize().dot(look);
            if (d > best) { best = d; target = t; }
        }
        if (target == null) {
            p.displayClientMessage(Component.translatable("ui.arcane_addon.soul_chain.miss").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        final LivingEntity tg = target;
        // the spell trap: a magic circle opens under them
        Vec3 g = FxLib.ground(sl, tg.position().add(0, 0.5, 0), 4);
        Vec3 c = g != null ? g : tg.position();
        FxSend.ring(sl, F, c.add(0, 0.05, 0), 2.6f, 2.8f, 0.04f, 30);
        FxSend.ring(sl, F, c.add(0, 0.05, 0), 1.6f, 1.75f, 0.04f, 30);
        for (int k = 0; k < 5; k++) {   // a five-pointed star inside it
            double a0 = Math.PI * 2 * k / 5 - Math.PI / 2, a1 = Math.PI * 2 * (k + 2) / 5 - Math.PI / 2;
            FxSend.beam(sl, F, c.add(Math.cos(a0) * 2.6, 0.06, Math.sin(a0) * 2.6), c.add(Math.cos(a1) * 2.6, 0.06, Math.sin(a1) * 2.6), 0.06f, 30);
        }
        AstaFx.later(3, () -> {
            if (!alive(p) || !tg.isAlive()) return;
            Chain ch = new Chain(); ch.target = tg; ch.until = sl.getGameTime() + CHAIN_TICKS;
            CHAINS.put(p.getUUID(), ch);
            ModeHud.show(p, "soul_chain", CHAIN_TICKS);
            hit(p, tg, power, "soul_chain");
            FxLib.impact(sl, F, mid(tg), 1.2f);
            p.displayClientMessage(Component.translatable("ui.arcane_addon.soul_chain.on").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            sound(sl, tg.position(), net.minecraft.sounds.SoundEvents.CHAIN_PLACE, 1f, 0.6f);
            sound(sl, tg.position(), net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, 1f, 0.6f);
        });
    }

    private static void chainTick(ServerPlayer p, ServerLevel sl, Chain ch, long now) {
        LivingEntity t = ch.target;
        Vec3 a = p.position().add(0, 1.0, 0), b = mid(t);
        double d = a.distanceTo(b);
        if (d > 8) {   // pulled back toward you
            Vec3 pull = a.subtract(b).normalize().scale(Math.min(0.9, (d - 8) * 0.35));
            t.setDeltaMovement(t.getDeltaMovement().add(pull));
            t.hurtMarked = true;
        }
        // the chain: links of fire, alternating orientation, slowly sagging
        Vec3 ab = b.subtract(a);
        int links = (int) Math.max(3, Math.min(16, d / 0.55));
        Vec3 side = rightOf(ab.normalize());
        for (int k = 0; k <= links; k++) {
            double f = (double) k / links;
            Vec3 q = a.add(ab.scale(f)).add(0, -Math.sin(Math.PI * f) * Math.min(1.2, d * 0.08), 0);
            Vec3 up = k % 2 == 0 ? new Vec3(0, 1, 0) : side;
            FxSend.box(sl, F, q, ab.normalize(), up, 0.2f, 0.03f, 0.1f, 2, 0, FxPacket.HOLD);
        }
        if (now % 20 == 0) sound(sl, b, net.minecraft.sounds.SoundEvents.CHAIN_STEP, 0.6f, 0.8f);
    }

    // ================================================================== 10. Explosive Fire Cannon
    private static final int CANNON_RELEASE = 24;

    /** Pour everything into one giant fireball over your hands, then fire it: 1.6 blocks a tick, a 8-block explosion. */
    public static void fireCannon(ServerPlayer p, float power) {
        if (LimitBreak.active(p)) { LimitFx.magnaFinal(p, power); return; }   // Surpassing Limits: the evolved ultimate
        ServerLevel sl = p.serverLevel();
        for (int t = 0; t < CANNON_RELEASE; t++) {
            final int tt = t;
            AstaFx.later(t, () -> {
                if (!alive(p)) return;
                Vec3 at = p.getEyePosition().add(p.getLookAngle().scale(1.6)).add(0, 0.9, 0);
                float r = 0.3f + tt * 0.06f;
                fireball(sl, at, Vec3.ZERO, r, r, 2, false);
                if (tt % 3 == 0) {   // fire pulled in from all around
                    ThreadLocalRandom rr = ThreadLocalRandom.current();
                    double a = rr.nextDouble(Math.PI * 2);
                    Vec3 from = at.add(Math.cos(a) * 5, rr.nextDouble(-2, 2), Math.sin(a) * 5);
                    FxSend.trail(sl, F, from, from.add(at.subtract(from).scale(0.8)), 0.15f, 5);
                }
            });
        }
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BLAZE_AMBIENT, 1f, 0.5f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, 1f, 0.5f);
        AstaFx.later(CANNON_RELEASE, () -> {
            if (!alive(p)) return;
            Vec3 look = p.getLookAngle();
            Vec3 at = p.getEyePosition().add(look.scale(1.6)).add(0, 0.9, 0);
            Vec3 aim = clip(sl, p, p.getEyePosition(), p.getEyePosition().add(look.scale(60)));
            Vec3 dir = aim.subtract(at).normalize();
            shot(p, sl, at, dir, 1.6f, 1.7f, 8.0, power, 0, 36, "fire_cannon");
            FxLib.shockRing(sl, F, p.position().add(0, 0.05, 0), 1.2f);
            p.setDeltaMovement(p.getDeltaMovement().subtract(look.scale(0.5)));
            p.hurtMarked = true;
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 0.6f, 0.6f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, 1.4f, 0.5f);
        });
    }

    // ================================================================== events
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || BURNS.isEmpty()) return;
        Iterator<Burn> it = BURNS.iterator();
        List<Burn> due = new ArrayList<>();
        while (it.hasNext()) {
            Burn b = it.next();
            long now = b.e.level().getGameTime();
            if (!b.e.isAlive() || now > b.until) { it.remove(); continue; }
            if (now % 20 == 0) due.add(b);
        }
        for (Burn b : due) {   // outside the loop: damage can kill and change the list
            if (b.src != null && b.src.isAlive()) AstaFx.damage(b.src, b.e, 1f, "exploding_fireball");
            else b.e.hurt(b.e.damageSources().onFire(), 1f);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        UUID id = p.getUUID();
        Long k = KNUCKLES.get(id);
        if (k != null) {
            if (now > k) { KNUCKLES.remove(id); sendKnuckles(p, 0); }
            else if (now % 40 == 0) sendKnuckles(p, (int) (k - now));
        }
        Chain ch = CHAINS.get(id);
        if (ch != null) {
            if (now > ch.until || !ch.target.isAlive() || ch.target.level() != p.level()) {
                CHAINS.remove(id);
                ModeHud.hide(p, "soul_chain");
                FxLib.flash(sl, F, mid(ch.target), 0.8f);
                sound(sl, p.position(), net.minecraft.sounds.SoundEvents.CHAIN_BREAK, 1f, 0.8f);
            } else chainTick(p, sl, ch, now);
        }
    }

    /** Flame Knuckles: your punches explode. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getSource().getDirectEntity() != p) return;
        if (e.getSource() instanceof io.redspace.ironsspellbooks.damage.SpellDamageSource) return;
        Long k = KNUCKLES.get(p.getUUID());
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        if (k == null || now > k) return;
        Long cd = PUNCH_CD.get(p.getUUID());
        if (cd != null && now - cd < 6) return;
        PUNCH_CD.put(p.getUUID(), now);
        e.setAmount(e.getAmount() + 5f);
        LivingEntity t = e.getEntity();
        burn(p, t, 60);
        fireball(sl, mid(t), Vec3.ZERO, 0.4f, 1.6f, 7, true);
        Vec3 push = t.position().subtract(p.position()).multiply(1, 0, 1);
        if (push.lengthSqr() > 1.0E-4) t.setDeltaMovement(t.getDeltaMovement().add(push.normalize().scale(0.6)).add(0, 0.2, 0));
        sound(sl, t.position(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 0.4f, 1.5f);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        KNUCKLES.remove(id); CHAINS.remove(id); PUNCH_CD.remove(id);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        KNUCKLES.clear(); CHAINS.clear(); BURNS.clear(); PUNCH_CD.clear();
    }
}
