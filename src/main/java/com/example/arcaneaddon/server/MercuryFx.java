package com.example.arcaneaddon.server;

import static com.example.arcaneaddon.server.FxUtil.*;
import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.item.MercuryGrimoireItem;
import com.example.arcaneaddon.network.AddonNetwork;
import com.example.arcaneaddon.network.FxPacket;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Nozel Silva's Mercury Magic - the ten spells of the Mercury Grimoire and its passive. Liquid mercury shaped into
 * bullets, spears, blades, an eagle, mirrors, barriers and armour.
 *
 *  1 Mercury Bullets            - a quick volley of mercury bullets
 *  2 Mercury Spears             - three spears of mercury rise beside you and launch
 *  3 Silver Rain                - mercury raindrops pour down on the area you look at
 *  4 Storm of Blades            - six mercury swords circle you, then cut into your enemies
 *  5 Mercury Eagle              - an eagle of mercury hunts your target and bursts into spikes
 *  6 Silver Mirror              - a wall of compressed, mirror-smooth mercury that throws projectiles back
 *  7 Mercury Sphere             - a sphere of mercury closes over you and anyone beside you
 *  8 Silver Shining Regal Spear - a beam of mercury from your palm that cuts through anything
 *  9 Silver Guardian            - mercury armour over your whole body: far less damage, but the mercury poisons you
 * 10 Mercury Cage               - a giant cage of mercury bars drops over your enemies and crushes them
 *
 * Passive "Royal Mercury": when something hits you in melee, a spike of mercury lashes back at it (once a second).
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public class MercuryFx {
    /** Mercury: bright, liquid chrome. */
    public static final int M = 11;
    private static final int S = Fx.SILVER;

    private static final Map<UUID, Long> MIRROR = new HashMap<>();
    private static final Map<UUID, Vec3> MIRROR_AT = new HashMap<>();
    private static final Map<UUID, Long> GUARDIAN = new HashMap<>();
    private static final Map<UUID, Long> REFLEX = new HashMap<>();
    /** Mercury Sphere barriers: {centre, until, owner}. */
    private static final List<Barrier> BARRIERS = new ArrayList<>();
    private static final class Barrier { ServerLevel sl; Vec3 c; long until; UUID owner; }
    private static final double SPHERE_R = 3.2;

    // ------------------------------------------------------------------ helpers

    private static boolean wearsMercuryGrimoire(ServerPlayer p) {
        ItemStack s = Utils.getPlayerSpellbookStack(p);
        return s != null && s.getItem() instanceof MercuryGrimoireItem;
    }

    private static Vec3 handPos(ServerPlayer p) {
        double yr = Math.toRadians(p.getYRot());
        double lx = Math.cos(yr), lz = Math.sin(yr), fx = -Math.sin(yr), fz = Math.cos(yr);
        return new Vec3(p.getX() + fx * 0.6 - lx * 0.35, p.getY() + p.getEyeHeight() * 0.8, p.getZ() + fz * 0.6 - lz * 0.35);
    }

    /** A liquid mercury sphere (client draws a wobbling chrome ball): shape 19. */
    static void sphere(ServerLevel sl, Vec3 c, Vec3 vel, float r, int life, int grow) {
        FxPacket p = new FxPacket();
        if (life <= 3 && life > 0) life = 1;   // per-tick copies: the client moves each along its velocity through its tick
        p.shape = 19; p.palette = (byte) M; p.life = life; p.sweep = grow; p.c = c; p.v = vel; p.r = r;
        AddonNetwork.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(c.x, c.y, c.z, 96, sl.dimension())), p);
    }

    /** Mercury splashing where something hits: a few beads and a ring. */
    private static void splash(ServerLevel sl, Vec3 at, float s) {
        sphere(sl, at, Vec3.ZERO, 0.35f * s, 5, 1);
        FxSend.ring(sl, M, at, 0.2f * s, 1.3f * s, 0.06f, 6);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int k = 0; k < 4; k++) {
            Vec3 d = new Vec3(r.nextDouble(-1, 1), r.nextDouble(0, 1), r.nextDouble(-1, 1)).normalize();
            FxSend.spike(sl, M, at, d, 0.5f * s, 0.06f * s, 5, 1, FxPacket.POP);
        }
    }

    /** Mercury drawn up out of your hand while the animation winds up. */
    private static void windup(ServerPlayer p, int ticks) {
        ServerLevel sl = p.serverLevel();
        for (int k = 0; k < ticks; k += 2) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (alive(p)) sphere(sl, handPos(p), new Vec3(0, 0.01, 0), 0.12f + kk * 0.012f, 3, 1);
            });
        }
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BUCKET_FILL, 0.6f, 1.4f);
    }

    // ================================================================== 1. Mercury Bullets
    private static final int BULLET_RELEASE = 7;

    /** Six bullets of mercury, fired one after another at 3.5 blocks a tick, curving slightly onto the target you aim at. */
    public static void mercuryBullets(ServerPlayer p, float power) {
        windup(p, BULLET_RELEASE);
        AstaFx.later(BULLET_RELEASE, () -> {
            if (!alive(p)) return;
            ServerLevel sl = p.serverLevel();
            LivingEntity target = aimed(p, 40, 15);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int i = 0; i < 6; i++) {
                AstaFx.later(i * 2, () -> {
                    if (!alive(p)) return;
                    Vec3 d = p.getLookAngle().add(r.nextDouble(-0.04, 0.04), r.nextDouble(-0.04, 0.04), r.nextDouble(-0.04, 0.04)).normalize();
                    bullet(p, sl, handPos(p), d, target, power * 0.35f, 0);
                    sound(sl, p.position(), net.minecraft.sounds.SoundEvents.SLIME_SQUISH_SMALL, 0.8f, 1.6f);
                });
            }
        });
    }

    private static void bullet(ServerPlayer p, ServerLevel sl, Vec3 pos, Vec3 dir, LivingEntity target, float dmg, int step) {
        if (!alive(p) || step > 12) return;
        if (target != null && target.isAlive()) dir = dir.scale(0.75).add(mid(target).subtract(pos).normalize().scale(0.25)).normalize();
        Vec3 want = pos.add(dir.scale(3.5));
        Vec3 next = clip(sl, p, pos, want);
        sphere(sl, next, dir.scale(3.5), 0.16f, 2, 0);
        FxSend.trail(sl, M, pos, next, 0.06f, 4);
        for (LivingEntity t : enemies(sl, p, new AABB(pos, next).inflate(0.8))) {
            if (!t.getBoundingBox().inflate(0.3).clip(pos, next).isPresent()) continue;
            AstaFx.damage(p, t, dmg, "mercury_bullets");
            splash(sl, mid(t), 0.6f);
            return;
        }
        if (next.distanceToSqr(want) > 1.0E-4) { splash(sl, next, 0.5f); return; }
        final Vec3 nd = dir;
        AstaFx.later(1, () -> bullet(p, sl, next, nd, target, dmg, step + 1));
    }

    // ================================================================== 2. Mercury Spears
    private static final int SPEAR_LAUNCH = 12;

    /** Three long spears of mercury rise out of pools beside you, turn to where you look, and launch 40 blocks, piercing. */
    public static void mercurySpears(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        for (int t = 0; t < SPEAR_LAUNCH; t++) {
            final int tt = t;
            AstaFx.later(t, () -> {
                if (!alive(p)) return;
                Vec3 look = p.getLookAngle(), rt = rightOf(look);
                float g = Math.min(1f, (tt + 1) / 6f);
                for (int i = -1; i <= 1; i++) {
                    Vec3 base = p.position().add(rt.scale(i * 1.3)).add(0, 1.2 + (i == 0 ? 0.9 : 0), 0).subtract(look.scale(0.4));
                    Vec3 d = tt < 6 ? new Vec3(0, 1, 0) : look;
                    FxSend.spike(sl, M, base.subtract(d.scale(1.2 * g)), d, 2.6f * g, 0.14f, 2, 0, FxPacket.HOLD);
                }
            });
        }
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, 0.8f, 1.2f);
        AstaFx.later(SPEAR_LAUNCH, () -> {
            if (!alive(p)) return;
            Vec3 look = p.getLookAngle(), rt = rightOf(look);
            for (int i = -1; i <= 1; i++) {
                Vec3 base = p.position().add(rt.scale(i * 1.3)).add(0, 1.2 + (i == 0 ? 0.9 : 0), 0);
                Vec3 aim = clip(sl, p, p.getEyePosition(), p.getEyePosition().add(look.scale(40)));
                Vec3 d = aim.subtract(base).normalize();
                AstaFx.later(i + 1, () -> spear(p, sl, base, d, power * 0.6f, 0, new HashSet<>()));
            }
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.TRIDENT_THROW, 1f, 0.9f);
        });
    }

    private static void spear(ServerPlayer p, ServerLevel sl, Vec3 pos, Vec3 dir, float dmg, int step, Set<UUID> hit) {
        if (!alive(p) || step > 13) return;
        Vec3 want = pos.add(dir.scale(3));
        Vec3 next = clip(sl, p, pos, want);
        if (step == 0) {   // the spear's whole flight, sent once: the client glides it (no per-tick re-sends)
            Vec3 far = clip(sl, p, pos, pos.add(dir.scale(3 * 14)));
            int ticks = (int) Math.max(1, Math.ceil(far.distanceTo(pos) / 3.0));
            FxSend.spike(sl, M, pos.add(dir.scale(0.6)), dir, 2.6f, 0.15f, ticks + 1, 0, FxPacket.HOLD, 0, dir.scale(3), ticks - 1);
        }
        FxSend.trail(sl, M, pos, next, 0.08f, 5);
        for (LivingEntity t : enemies(sl, p, new AABB(pos, next).inflate(1.0))) {
            if (!t.getBoundingBox().inflate(0.4).clip(pos, next).isPresent()) continue;
            if (!hit.add(t.getUUID())) continue;
            AstaFx.damage(p, t, dmg, "mercury_spears");
            splash(sl, mid(t), 0.8f);
            sound(sl, t.position(), net.minecraft.sounds.SoundEvents.TRIDENT_HIT, 1f, 1.1f);
        }
        if (next.distanceToSqr(want) > 1.0E-4) {   // stuck in the wall, then melts back
            FxSend.spike(sl, M, next.subtract(dir.scale(2.0)), dir, 2.4f, 0.15f, 14, 0, FxPacket.POP);
            splash(sl, next, 0.7f);
            return;
        }
        AstaFx.later(1, () -> spear(p, sl, next, dir, dmg, step + 1, hit));
    }

    // ================================================================== 3. Silver Rain
    private static final int RAIN_START = 6;

    /** Raise your hand: a sheet of mercury gathers overhead where you look (up to 30 blocks) and rains needles for 2.5 s. */
    public static void silverRain(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 hit = clip(sl, p, eye, eye.add(p.getLookAngle().scale(30)));
        Vec3 g = FxLib.ground(sl, hit.add(0, 0.5, 0), 12);
        final Vec3 c = g != null ? g : hit;
        final double R = 6;
        final Vec3 sky = c.add(0, 9, 0);
        FxSend.wall(sl, M, sky, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.5f, (float) R, -180, 180, 0.12f, 60, 6, 0);   // the sheet of mercury above
        FxSend.ring(sl, M, c.add(0, 0.05, 0), (float) R - 0.15f, (float) R, 0.05f, 60);                             // where it will fall
        sound(sl, sky, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, 1f, 0.7f);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int t = 0; t < 50; t++) {
            final int tt = t;
            AstaFx.later(RAIN_START + t, () -> {
                if (!alive(p)) return;
                for (int k = 0; k < 5; k++) {   // needles of mercury falling fast
                    double a = r.nextDouble(Math.PI * 2), d = Math.sqrt(r.nextDouble()) * R;
                    Vec3 top = sky.add(Math.cos(a) * d, r.nextDouble(-0.5, 0.5), Math.sin(a) * d);
                    FxSend.spike(sl, M, top, new Vec3(0, -1, 0), 0.9f, 0.05f, 4, 0, FxPacket.HOLD, 0, new Vec3(0, -2.6, 0), 4);
                }
                if (tt % 5 == 0) {
                    for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(R, 9, R))) {
                        if (new Vec3(e.getX() - c.x, 0, e.getZ() - c.z).length() > R) continue;
                        AstaFx.damage(p, e, power * 0.18f, "silver_rain");
                        e.setDeltaMovement(e.getDeltaMovement().multiply(0.5, 1, 0.5));   // pinned under the downpour
                        e.hurtMarked = true;
                    }
                    double a = r.nextDouble(Math.PI * 2), d = r.nextDouble(R);
                    Vec3 gg = FxLib.ground(sl, c.add(Math.cos(a) * d, 2, Math.sin(a) * d), 5);
                    if (gg != null) FxSend.ring(sl, M, gg.add(0, 0.04, 0), 0.1f, 0.9f, 0.04f, 5);   // splashes on the ground
                }
                if (tt % 10 == 0) sound(sl, c, net.minecraft.sounds.SoundEvents.WEATHER_RAIN, 0.8f, 1.6f);
            });
        }
    }

    // ================================================================== 4. Storm of Blades
    private static final int BLADES_LAUNCH = 16;

    /** Six swords of mercury form and circle you, then each one darts at an enemy and cuts through it. */
    public static void stormOfBlades(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        int n = 6;
        FxSend.orbit(sl, p, 1, n, 1.9f, 1.3f, 0.3f, 0f, 0.25f, 5, 1, BLADES_LAUNCH);   // circling you, drawn smoothly by the client
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_CHAIN, 1f, 1.4f);
        AstaFx.later(BLADES_LAUNCH, () -> {
            if (!alive(p)) return;
            List<LivingEntity> foes = new ArrayList<>(enemies(sl, p, p.getBoundingBox().inflate(24)));
            foes.removeIf(t -> t.distanceTo(p) > 24 || !p.hasLineOfSight(t));
            foes.sort(Comparator.comparingDouble(t -> t.distanceToSqr(p)));
            Vec3 look = p.getLookAngle();
            for (int i = 0; i < n; i++) {
                final int ii = i;
                LivingEntity t = foes.isEmpty() ? null : foes.get(i % foes.size());
                AstaFx.later(i, () -> {   // each leaves the ring from exactly where the client is drawing it
                    if (!alive(p)) return;
                    double age = BLADES_LAUNCH + ii;
                    double a = Math.PI * 2 * ii / n + age * 0.3;
                    Vec3 c = p.position().add(Math.cos(a) * 1.9, 1.3 + 0.25 * Math.sin(age * 0.35 + ii), Math.sin(a) * 1.9);
                    Vec3 lk = p.getLookAngle();
                    Vec3 d = t != null && t.isAlive() ? mid(t).subtract(c).normalize() : lk.add(rightOf(lk).scale((ii - 2.5) * 0.08)).normalize();
                    swordFly(p, sl, c, d, power * 0.4f, 0, new HashSet<>());
                });
            }
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.5f);
        });
    }

    /** A mercury sword flying along {@code vel} for {@code ticks} ticks, sent once (the client moves it smoothly). */
    private static void swordFlight(ServerLevel sl, Vec3 c, Vec3 dir, Vec3 flat, Vec3 vel, int ticks) {
        Vec3 up = dir.cross(flat).normalize();
        if (up.lengthSqr() < 1.0E-4) up = rightOf(dir);
        int life = ticks + 1;
        FxSend.box(sl, M, c.add(dir.scale(0.35)), dir, up, 0.75f, 0.02f, 0.09f, life, 0, FxPacket.HOLD, 0, vel, ticks);
        FxSend.box(sl, M, c.subtract(dir.scale(0.45)), up, dir, 0.24f, 0.04f, 0.04f, life, 0, FxPacket.HOLD, 0, vel, ticks);
        FxSend.box(sl, M, c.subtract(dir.scale(0.65)), dir, up, 0.18f, 0.035f, 0.035f, life, 0, FxPacket.HOLD, 0, vel, ticks);
    }

    /** A sword of mercury: a long, thin blade and a cross-guard, pointing {@code dir}, flat side facing {@code flat}. */
    private static void sword(ServerLevel sl, Vec3 c, Vec3 dir, Vec3 flat, float s) { sword(sl, c, dir, flat, s, Vec3.ZERO); }

    private static void sword(ServerLevel sl, Vec3 c, Vec3 dir, Vec3 flat, float s, Vec3 vel) {
        Vec3 up = dir.cross(flat).normalize();
        if (up.lengthSqr() < 1.0E-4) up = rightOf(dir);
        FxSend.box(sl, M, c.add(dir.scale(0.35 * s)), dir, up, 0.75f * s, 0.02f, 0.09f, 2, 0, FxPacket.HOLD, 0, vel, 0);   // blade
        FxSend.box(sl, M, c.subtract(dir.scale(0.45 * s)), up, dir, 0.24f * s, 0.04f, 0.04f, 2, 0, FxPacket.HOLD, 0, vel, 0);   // guard
        FxSend.box(sl, M, c.subtract(dir.scale(0.65 * s)), dir, up, 0.18f * s, 0.035f, 0.035f, 2, 0, FxPacket.HOLD, 0, vel, 0);   // grip
    }

    private static void swordFly(ServerPlayer p, ServerLevel sl, Vec3 pos, Vec3 dir, float dmg, int step, Set<UUID> hit) {
        if (!alive(p) || step > 10) return;
        if (step == 0) {   // the whole flight in one go: the client glides it, no per-tick re-sends
            Vec3 far = clip(sl, p, pos, pos.add(dir.scale(2.6 * 11)));
            int ticks = (int) Math.max(1, Math.ceil(far.distanceTo(pos) / 2.6));
            swordFlight(sl, pos, dir, rightOf(dir), dir.scale(2.6), ticks);
        }
        Vec3 want = pos.add(dir.scale(2.6));
        Vec3 next = clip(sl, p, pos, want);
        FxSend.trail(sl, M, pos, next, 0.05f, 5);
        for (LivingEntity t : enemies(sl, p, new AABB(pos, next).inflate(1.0))) {
            if (!t.getBoundingBox().inflate(0.4).clip(pos, next).isPresent()) continue;
            if (!hit.add(t.getUUID())) continue;
            AstaFx.damage(p, t, dmg, "storm_of_blades");
            splash(sl, mid(t), 0.6f);
            sound(sl, t.position(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, 1f, 1.3f);
        }
        if (next.distanceToSqr(want) > 1.0E-4) { splash(sl, next, 0.5f); return; }
        AstaFx.later(1, () -> swordFly(p, sl, next, dir, dmg, step + 1, hit));
    }

    // ================================================================== 5. Mercury Eagle
    private static final int EAGLE_RELEASE = 12;

    /**
     * An eagle of liquid mercury, wings beating, flies from your hand and hunts the enemy you aim at (or the nearest one
     * ahead) for up to 3 s. When it strikes it bursts into a crown of mercury spikes.
     */
    public static void mercuryEagle(ServerPlayer p, float power) {
        windup(p, EAGLE_RELEASE);
        AstaFx.later(EAGLE_RELEASE, () -> {
            if (!alive(p)) return;
            ServerLevel sl = p.serverLevel();
            LivingEntity target = aimed(p, 50, 25);
            eagle(p, sl, handPos(p).add(p.getLookAngle()), p.getLookAngle(), target, power, 0);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.PHANTOM_FLAP, 1f, 0.8f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, 0.6f, 1.5f);
        });
    }

    private static void eagle(ServerPlayer p, ServerLevel sl, Vec3 pos, Vec3 dir, LivingEntity target, float dmg, int age) {
        if (!alive(p) || age > 60) return;
        if (target == null || !target.isAlive()) {   // look for prey ahead
            double best = Math.cos(Math.toRadians(50));
            for (LivingEntity t : enemies(sl, p, new AABB(pos, pos).inflate(24))) {
                double d = mid(t).subtract(pos).normalize().dot(dir);
                if (d > best) { best = d; target = t; }
            }
        }
        if (target != null) dir = dir.scale(0.8).add(mid(target).subtract(pos).normalize().scale(0.2)).normalize();
        Vec3 want = pos.add(dir.scale(1.6));
        Vec3 next = clip(sl, p, pos, want);
        drawEagle(sl, next, dir, age, dir.scale(1.6));
        FxSend.trail(sl, M, pos, next, 0.12f, 6);
        boolean strike = target != null && mid(target).distanceTo(next) < 1.6 + target.getBbWidth() * 0.5;
        if (strike || next.distanceToSqr(want) > 1.0E-4 || age == 60) {
            Vec3 at = strike ? mid(target) : next;
            for (LivingEntity t : enemies(sl, p, new AABB(at, at).inflate(3))) {
                if (t.position().distanceTo(at) > 3.5) continue;
                AstaFx.damage(p, t, t == target ? dmg : dmg * 0.5f, "mercury_eagle");
            }
            Vec3 floor = FxLib.ground(sl, at, 3);
            if (floor != null) FxLib.spikeRing(sl, M, floor, 1.0f);
            for (int k = 0; k < 8; k++) {   // the eagle bursts into spikes
                double a = Math.PI * 2 * k / 8;
                FxSend.spike(sl, M, at, new Vec3(Math.cos(a), 0.4, Math.sin(a)).normalize(), 1.6f, 0.12f, 12, 2, FxPacket.POP);
            }
            sphere(sl, at, Vec3.ZERO, 0.9f, 6, 1);
            sound(sl, at, net.minecraft.sounds.SoundEvents.TRIDENT_HIT, 1f, 0.7f);
            sound(sl, at, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, 1f, 0.8f);
            return;
        }
        if (age % 6 == 0) sound(sl, next, net.minecraft.sounds.SoundEvents.PHANTOM_FLAP, 0.6f, 1.1f);
        final Vec3 nd = dir; final LivingEntity tg = target;
        AstaFx.later(1, () -> eagle(p, sl, next, nd, tg, dmg, age + 1));
    }

    /** The eagle: a body, a head with a beak, a fanned tail, and two long wings that beat. */
    private static void drawEagle(ServerLevel sl, Vec3 c, Vec3 dir, int age, Vec3 vel) {
        Vec3 side = rightOf(dir), up = side.cross(dir).normalize();
        double beat = Math.sin(age * 0.9) * 0.6;
        sphere(sl, c, vel, 0.38f, 2, 0);                                                   // body
        sphere(sl, c.add(dir.scale(0.55)).add(up.scale(0.12)), vel, 0.2f, 2, 0);           // head
        FxSend.spike(sl, M, c.add(dir.scale(0.7)).add(up.scale(0.1)), dir.add(up.scale(-0.3)).normalize(), 0.3f, 0.06f, 2, 0, FxPacket.HOLD, 0, vel, 0);   // beak
        for (int s = -1; s <= 1; s += 2) {
            Vec3 wing = side.scale(s).scale(Math.cos(beat)).add(up.scale(Math.sin(beat))).normalize();
            Vec3 elbow = c.add(wing.scale(0.9)).subtract(dir.scale(0.15));
            FxSend.box(sl, M, c.add(wing.scale(0.45)), wing, dir, 0.48f, 0.22f, 0.04f, 2, 0, FxPacket.HOLD, 0, vel, 0);   // inner wing
            Vec3 outer = side.scale(s).scale(Math.cos(beat * 1.4)).add(up.scale(Math.sin(beat * 1.4))).subtract(dir.scale(0.35)).normalize();
            FxSend.box(sl, M, elbow.add(outer.scale(0.55)), outer, dir, 0.6f, 0.16f, 0.03f, 2, 0, FxPacket.HOLD, 0, vel, 0);   // outer wing
        }
        FxSend.box(sl, M, c.subtract(dir.scale(0.6)), dir, up, 0.3f, 0.22f, 0.03f, 2, 0, FxPacket.HOLD, 0, vel, 0);   // tail
    }

    // ================================================================== 6. Silver Mirror
    /**
     * Compress mercury into a smooth, mirror-bright wall in front of you for 4 s. Projectiles that hit it are thrown
     * straight back, and spells aimed at you from the front are blocked.
     */
    public static void silverMirror(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        long until = sl.getGameTime() + 80;
        MIRROR.put(p.getUUID(), until);
        ModeHud.show(p, "silver_mirror", 80);
        Vec3 look = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        look = look.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : look.normalize();
        MIRROR_AT.put(p.getUUID(), look);
        Vec3 rt = rightOf(look);
        Vec3 c = p.position().add(look.scale(1.6)).add(0, 1.3, 0);
        FxSend.box(sl, M, c, rt, new Vec3(0, 1, 0), 1.6f, 0.05f, 1.5f, 80, 5, FxPacket.POP);   // the mirror
        FxSend.box(sl, S, c.add(look.scale(0.06)), rt, new Vec3(0, 1, 0), 1.45f, 0.01f, 1.35f, 80, 7, FxPacket.POP);   // its bright face
        sound(sl, c, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.8f);
        sound(sl, c, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, 0.6f, 1.6f);
    }

    // ================================================================== 7. Mercury Sphere
    /**
     * A sphere of mercury closes over you and everyone within 3 blocks for 4 s. Nothing gets through from outside:
     * projectiles that reach it are swallowed, and attacks on anyone inside from outside are blocked.
     */
    public static void mercurySphere(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Barrier b = new Barrier();
        b.sl = sl;
        b.c = p.position().add(0, 1.0, 0);
        b.until = sl.getGameTime() + 80;
        b.owner = p.getUUID();
        BARRIERS.add(b);
        sphere(sl, b.c, Vec3.ZERO, (float) SPHERE_R, 80, 8);
        FxSend.ring(sl, M, p.position().add(0, 0.05, 0), 0.3f, (float) SPHERE_R, 0.08f, 8);
        sound(sl, b.c, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, 1f, 0.6f);
        sound(sl, b.c, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON, 1f, 0.6f);
    }

    private static boolean insideBarrier(Vec3 at, long now) {
        for (Barrier b : BARRIERS) if (now <= b.until && b.c.distanceTo(at) < SPHERE_R) return true;
        return false;
    }

    private static boolean crossesBarrier(Vec3 from, Vec3 to, long now) {
        for (Barrier b : BARRIERS) {
            if (now > b.until) continue;
            boolean a = b.c.distanceTo(from) < SPHERE_R, c = b.c.distanceTo(to) < SPHERE_R;
            if (a != c) return true;
        }
        return false;
    }

    // ================================================================== 8. Silver Shining Regal Spear
    private static final int REGAL_RELEASE = 18;

    /**
     * Hold out your palm while mercury gathers into a shining sphere in it, then release it as a thin, blinding beam
     * of mercury that cuts through everything - walls included - for 45 blocks.
     */
    public static void regalSpear(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        for (int k = 0; k < REGAL_RELEASE; k += 2) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 h = p.getEyePosition().add(p.getLookAngle().scale(1.0)).add(0, -0.25, 0);
                sphere(sl, h, Vec3.ZERO, 0.1f + kk * 0.018f, 3, 0);
                FxSend.ring(sl, S, h, 1.4f - kk * 0.06f, 0.1f, 0.03f, 3);
            });
        }
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, 0.7f, 1.8f);
        AstaFx.later(REGAL_RELEASE, () -> {
            if (!alive(p)) return;
            Vec3 dir = p.getLookAngle();
            Vec3 start = p.getEyePosition().add(dir.scale(1.0)).add(0, -0.25, 0);
            Vec3 end = start.add(dir.scale(45));
            for (LivingEntity t : enemies(sl, p, new AABB(start, end).inflate(1.5))) {
                if (closestOnSegment(start, end, mid(t)).distanceTo(mid(t)) > 0.9 + t.getBbWidth() * 0.5) continue;
                AstaFx.damage(p, t, power, "regal_spear");
                splash(sl, mid(t), 1.0f);
            }
            // the spear of light: a blinding silver-white beam with a mercury sheath, held for a moment, spiral rings
            // of mercury racing down it and beads of mercury shed along its length
            FxSend.trail(sl, S, start, end, 0.42f, 18);
            FxSend.trail(sl, S, start, end, 0.12f, 20);
            FxSend.trail(sl, M, start, end, 0.6f, 14);
            Vec3 rt = rightOf(dir), upv = rt.cross(dir).normalize();
            for (int k = 2; k < 45; k += 3) {
                final int kk = k;
                AstaFx.later(k / 6, () -> {
                    Vec3 c = start.add(dir.scale(kk));
                    FxSend.wall(sl, S, c, rt, upv, 0.5f, 1.3f, kk * 25f, kk * 25f + 300, 0.05f, 9, 0, 0);
                    sphere(sl, c, dir.scale(0.05).add(0, -0.03, 0), 0.14f, 14, 2);
                });
            }
            sphere(sl, start, Vec3.ZERO, 0.6f, 6, 0);
            FxLib.flash(sl, S, start, 1.0f);
            p.setDeltaMovement(p.getDeltaMovement().subtract(dir.scale(0.3)));
            p.hurtMarked = true;
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 1.8f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.TRIDENT_RIPTIDE_3, 0.8f, 1.6f);
        });
    }

    // ================================================================== 9. Silver Guardian
    private static final int GUARDIAN_FORM = 8, GUARDIAN_TICKS = 15 * 20;

    /**
     * Mercury flows over your whole body and hardens into flexible armour for 15 s: you take 60% less damage and can
     * still move freely - but mercury is poison, so you lose half a heart every 2 s while you wear it.
     */
    public static void silverGuardian(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        for (int k = 0; k < GUARDIAN_FORM; k += 2) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (alive(p)) FxSend.ring(sl, M, p.position().add(0, 0.1 + kk * 0.25, 0), 0.9f, 0.5f, 0.12f, 5);
            });
        }
        AstaFx.later(GUARDIAN_FORM, () -> {
            if (!alive(p)) return;
            GUARDIAN.put(p.getUUID(), sl.getGameTime() + GUARDIAN_TICKS);
            sendGuardian(p, GUARDIAN_TICKS);
            sphere(sl, p.position().add(0, 1, 0), Vec3.ZERO, 1.2f, 4, 0);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE, 1f, 1.3f);
            p.displayClientMessage(Component.translatable("ui.arcane_addon.silver_guardian.on").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD), true);
        });
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, 0.8f, 1.0f);
    }

    private static void sendGuardian(ServerPlayer p, int life) {
        ModeHud.show(p, "silver_guardian", life);
        FxPacket pkt = new FxPacket();
        pkt.shape = 14; pkt.id = p.getId(); pkt.palette = 2; pkt.life = life;   // AuraOutlineLayer colour 2 = liquid silver
        AddonNetwork.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }

    // ================================================================== 10. Mercury Cage
    private static final int CAGE_DROP = 9;

    /**
     * A giant cage of mercury bars drops over the spot you aim at (8 blocks across): everything inside is held in for
     * 3 s, then the bars snap inward and crush it.
     */
    public static void mercuryCage(ServerPlayer p, float power) {
        if (LimitBreak.active(p)) { LimitFx.tenThousand(p, power); return; }   // Surpassing Limits: the evolved ultimate
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        LivingEntity t0 = aimed(p, 30, 12);
        Vec3 hit = t0 != null ? t0.position() : clip(sl, p, eye, eye.add(p.getLookAngle().scale(30)));
        Vec3 g = FxLib.ground(sl, hit.add(0, 0.5, 0), 10);
        final Vec3 c = g != null ? g : hit;
        final double R = 4.0, H = 5.0;
        final int BARS = 14, HOLD = 60;
        FxSend.ring(sl, M, c.add(0, 0.05, 0), (float) R - 0.2f, (float) R, 0.05f, CAGE_DROP + 2);   // where it will land
        // the bars fall from the sky and drive into the ground, then hold
        for (int i = 0; i < BARS; i++) {
            double a = Math.PI * 2 * i / BARS;
            Vec3 foot = c.add(Math.cos(a) * R, 0, Math.sin(a) * R);
            FxSend.box(sl, M, foot.add(0, H / 2 + 8, 0), new Vec3(0, 1, 0), new Vec3(Math.cos(a), 0, Math.sin(a)), (float) H / 2, 0.11f, 0.11f,
                    CAGE_DROP + HOLD, 0, FxPacket.POP, 0, new Vec3(0, -8.0 / CAGE_DROP, 0), CAGE_DROP);
        }
        FxSend.box(sl, M, c.add(0, H + 8, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), (float) R, (float) R, 0.08f,
                CAGE_DROP + HOLD, 0, FxPacket.POP, 0, new Vec3(0, -8.0 / CAGE_DROP, 0), CAGE_DROP);   // the lid
        sound(sl, c, net.minecraft.sounds.SoundEvents.CHAIN_FALL, 1f, 0.6f);
        AstaFx.later(CAGE_DROP, () -> {
            FxLib.shockRing(sl, M, c.add(0, 0.05, 0), 1.4f);
            sound(sl, c, net.minecraft.sounds.SoundEvents.ANVIL_LAND, 0.8f, 0.7f);
        });
        // held inside
        for (int t = 0; t <= HOLD; t += 2) {
            AstaFx.later(CAGE_DROP + t, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(R + 1.5, H, R + 1.5))) {
                    Vec3 off = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
                    double d = off.length();
                    if (d > R + 1.2) continue;
                    if (d > R - 0.8) {   // pushed back off the bars
                        Vec3 in = off.normalize().scale(-(d - (R - 0.9)));
                        e.setDeltaMovement(in.x * 0.6, Math.min(e.getDeltaMovement().y, 0.1), in.z * 0.6);
                        e.hurtMarked = true;
                    }
                    if (e.getY() > c.y + H - 1.8) { e.setDeltaMovement(e.getDeltaMovement().x, -0.3, e.getDeltaMovement().z); e.hurtMarked = true; }
                }
            });
        }
        // the crush
        AstaFx.later(CAGE_DROP + HOLD, () -> {
            if (!alive(p)) return;
            for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(R + 1, H, R + 1))) {
                if (new Vec3(e.getX() - c.x, 0, e.getZ() - c.z).length() > R + 0.8) continue;
                AstaFx.damage(p, e, power, "mercury_cage");
                splash(sl, mid(e), 1.0f);
            }
            for (int i = 0; i < BARS; i++) {
                double a = Math.PI * 2 * i / BARS;
                Vec3 from = c.add(Math.cos(a) * R, 1.0 + (i % 3) * 1.2, Math.sin(a) * R);
                FxSend.spike(sl, M, from, c.add(0, 1.2, 0).subtract(from).normalize(), (float) R * 0.95f, 0.12f, 10, 2, FxPacket.EXTEND);
            }
            sphere(sl, c.add(0, 1.2, 0), Vec3.ZERO, 1.6f, 8, 1);
            sound(sl, c, net.minecraft.sounds.SoundEvents.ANVIL_LAND, 1f, 0.5f);
            sound(sl, c, net.minecraft.sounds.SoundEvents.TRIDENT_HIT, 1f, 0.6f);
        });
    }

    // ================================================================== events
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || BARRIERS.isEmpty()) return;
        BARRIERS.removeIf(b -> b.sl.getGameTime() > b.until + 200);
        for (Barrier b : BARRIERS) {
            if (b.sl.getGameTime() > b.until) continue;
            for (Projectile pr : b.sl.getEntitiesOfClass(Projectile.class, new AABB(b.c, b.c).inflate(SPHERE_R + 1))) {
                Entity owner = pr.getOwner();
                if (owner != null && owner.getUUID().equals(b.owner)) continue;
                double d = pr.position().distanceTo(b.c);
                if (d < SPHERE_R + 0.6 && d > SPHERE_R - 0.8) {   // swallowed by the surface
                    splash(b.sl, pr.position(), 0.5f);
                    pr.discard();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        long now = p.serverLevel().getGameTime();
        UUID id = p.getUUID();
        Long g = GUARDIAN.get(id);
        if (g != null) {
            if (now > g) { GUARDIAN.remove(id); sendGuardian(p, 0); }
            else {
                if (now % 40 == 0) {
                    sendGuardian(p, (int) (g - now));
                    if (p.getHealth() > 2f) p.setHealth(p.getHealth() - 1f);   // mercury poisoning: the price of the armour
                }
            }
        }
        Long m = MIRROR.get(id);
        if (m != null && now > m) { MIRROR.remove(id); MIRROR_AT.remove(id); }
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim == null || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        ServerLevel sl = victim.level() instanceof ServerLevel s0 ? s0 : null;
        if (sl == null) return;
        long now = sl.getGameTime();
        // Mercury Sphere: attacks from outside on anyone inside are blocked
        Vec3 src = e.getSource().getSourcePosition();
        if (src != null && !BARRIERS.isEmpty() && insideBarrier(mid(victim), now) && crossesBarrier(src, mid(victim), now)) {
            e.setCanceled(true);
            splash(sl, mid(victim).add(src.subtract(mid(victim)).normalize().scale(1.2)), 0.4f);
            return;
        }
        if (!(victim instanceof ServerPlayer p)) return;
        // Silver Mirror: projectiles bounce straight back; spells from the front are blocked
        Long mt = MIRROR.get(p.getUUID());
        Vec3 face = MIRROR_AT.get(p.getUUID());
        if (mt != null && now <= mt && face != null && src != null) {
            Vec3 to = src.subtract(p.position()).multiply(1, 0, 1);
            if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(face) > 0.3) {
                Entity direct = e.getSource().getDirectEntity();
                if (direct instanceof Projectile pr) {
                    pr.setDeltaMovement(pr.getDeltaMovement().scale(-1.1));
                    pr.setOwner(p);
                    e.setCanceled(true);
                } else if (e.getSource() instanceof SpellDamageSource || e.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
                    e.setCanceled(true);
                }
                if (e.isCanceled()) {
                    FxLib.flash(sl, S, p.position().add(face.scale(1.6)).add(0, 1.3, 0), 0.8f);
                    sound(sl, p.position(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT, 1f, 1.8f);
                    return;
                }
            }
        }
    }

    /** Silver Guardian cuts damage; the passive lashes back at melee attackers. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        long now = p.serverLevel().getGameTime();
        Long g = GUARDIAN.get(p.getUUID());
        if (g != null && now <= g) {
            e.setAmount(e.getAmount() * 0.4f);
            splash(p.serverLevel(), mid(p).add(p.getLookAngle().scale(0.4)), 0.4f);   // the armour cracks a little where it's hit
        }
        Entity src = e.getSource().getEntity();
        if (src instanceof LivingEntity att && e.getSource().getDirectEntity() == att && att != p && wearsMercuryGrimoire(p)) {
            Long last = REFLEX.get(p.getUUID());
            if (last != null && now - last < 20) return;
            REFLEX.put(p.getUUID(), now);
            Vec3 from = mid(p), to = mid(att);
            FxSend.spike(p.serverLevel(), M, from, to.subtract(from).normalize(), (float) from.distanceTo(to), 0.08f, 6, 1, FxPacket.EXTEND);
            AstaFx.later(1, () -> { if (att.isAlive()) AstaFx.damage(p, att, 3f, "mercury_bullets"); });
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        MIRROR.remove(id); MIRROR_AT.remove(id); GUARDIAN.remove(id); REFLEX.remove(id);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        MIRROR.clear(); MIRROR_AT.clear(); GUARDIAN.clear(); REFLEX.clear(); BARRIERS.clear();
    }
}
