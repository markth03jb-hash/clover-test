package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.item.BoneGrimoireItem;
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
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Zenon Zogratis' Bone Magic - the ten spells of the Bone Grimoire and its passive. He grows, shapes and throws his
 * own bones - spears in bunches and tangles, swords, whips - regrows them faster than they break, and (through the
 * devil Beelzebub) traps enemies in cubes of space.
 *
 *  1 Bone Spear Volley      - a bunch of bone spears thrown at once
 *  2 Bone Spike Eruption    - spikes of bone burst out of the ground in a line ahead of you
 *  3 Bone Sword             - a sword of bone grows in your hand: three cuts
 *  4 Bone Whips             - whips of vertebrae lash out from your back at everything around you
 *  5 Ribcage Prison         - giant ribs close around the enemy you aim at
 *  6 Marrow Regeneration    - bone plates over your body while you regrow: heal and take less damage
 *  7 Bone Thicket           - a tangle of bones bursts out across an area, cornering everyone in it
 *  8 Spatial Cube           - Beelzebub's space: a cube closes on your target, holds it, then crushes inward
 *  9 Devil Union: Beelzebub - horns, bone wings, faster and harder bones for 25 s
 * 10 Bone Spear Storm       - a cube of space opens in the sky and rains bone spears on an area
 *
 * Passive "Endless Bone": you regrow - 1 health every 2 s while you're hurt.
 * Bones are drawn as real bone (client shape 30): shaded ivory with knobbed ends, sharpened points, vertebrae.
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public class BoneFx {
    private static final int B = 15;   // bone palette (the client draws bone in its own ivory, this only groups them)
    private static final int SPACE = Fx.DARK;   // Beelzebub's space: violet-black edges
    public static final int LONG = 0, SPEAR = 1, SPIKE = 2, VERTEBRA = 3, RIB = 4;

    private static final Map<UUID, Long> PLATES = new HashMap<>();
    private static final Map<UUID, Long> UNION = new HashMap<>();
    private static final UUID UNION_SPEED = UUID.fromString("f3a4b5c6-d7e8-4f90-a1b2-c3d4e5f6a7b8");
    private static final int UNION_TICKS = 25 * 20;

    // ------------------------------------------------------------------ helpers
    private static void sound(ServerLevel sl, Vec3 at, SoundEvent s, float v, float pitch) {
        sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, v, pitch);
    }

    private static Vec3 mid(LivingEntity t) { return t.position().add(0, t.getBbHeight() * 0.5, 0); }

    private static Vec3 rightOf(Vec3 dir) {
        Vec3 r = dir.cross(new Vec3(0, 1, 0));
        return r.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : r.normalize();
    }

    private static Vec3 rotY(Vec3 v, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    private static Vec3 clip(ServerLevel sl, Entity p, Vec3 a, Vec3 b) {
        var hit = sl.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? b : hit.getLocation();
    }

    private static List<LivingEntity> enemies(ServerLevel sl, ServerPlayer p, AABB box) {
        return sl.getEntitiesOfClass(LivingEntity.class, box, x -> x != p && x.isAlive() && !x.isAlliedTo(p)
                && !(x instanceof net.minecraft.world.entity.decoration.ArmorStand));
    }

    private static boolean alive(ServerPlayer p) { return p.isAlive() && !p.isRemoved(); }

    private static boolean wearsBoneGrimoire(ServerPlayer p) {
        ItemStack s = Utils.getPlayerSpellbookStack(p);
        return s != null && s.getItem() instanceof BoneGrimoireItem;
    }

    public static boolean inUnion(ServerPlayer p) {
        Long u = UNION.get(p.getUUID());
        return u != null && p.serverLevel().getGameTime() <= u;
    }

    private static void unionMsg(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable("ui.arcane_addon.beelzebub." + key).withStyle(ChatFormatting.DARK_RED), true);
    }

    private static LivingEntity aimed(ServerPlayer p, double range, double deg) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity best = null;
        double bd = Math.cos(Math.toRadians(deg));
        for (LivingEntity t : enemies(p.serverLevel(), p, p.getBoundingBox().inflate(range))) {
            Vec3 to = mid(t).subtract(eye);
            if (to.length() > range || !p.hasLineOfSight(t)) continue;
            double d = to.normalize().dot(look);
            if (d > bd) { bd = d; best = t; }
        }
        return best;
    }

    /** One bone (client shape 30). */
    static void bone(ServerLevel sl, int kind, Vec3 c, Vec3 axis, Vec3 bend, float halfLen, float radius,
                     int life, int rise, float mode, float spin, Vec3 vel, int moveTicks) {
        FxPacket p = new FxPacket();
        if (mode == FxPacket.HOLD && life <= 2) life = 1;
        p.shape = 30; p.palette = (byte) B; p.life = life; p.sweep = rise; p.h = kind;
        p.c = c; p.d = axis.normalize(); p.u = bend; p.r = halfLen; p.r2 = radius; p.speed = mode; p.a0 = spin; p.v = vel; p.a1 = moveTicks;
        AddonNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(c.x, c.y, c.z, 128, sl.dimension())), p);
    }

    /** A spike of bone bursting out of the ground at {@code base}, pointing {@code dir}. */
    private static void spikeUp(ServerLevel sl, Vec3 base, Vec3 dir, float len, float rad, int life, int rise) {
        bone(sl, SPIKE, base.add(dir.normalize().scale(len * 0.5)), dir, rightOf(dir), len * 0.5f, rad, life, rise, FxPacket.GROUND, 0, Vec3.ZERO, 0);
    }

    private static void crack(ServerLevel sl, Vec3 at, float s) {
        sound(sl, at, SoundEvents.SKELETON_HURT, 0.6f * s, 0.6f);
        sound(sl, at, SoundEvents.BONE_BLOCK_BREAK, 0.9f * s, 0.8f);
    }

    private static float power(ServerPlayer p, float base) { return inUnion(p) ? base * 1.35f : base; }

    // ================================================================== 1. Bone Spear Volley
    private static final int VOLLEY_RELEASE = 8;

    /** Bone spears grow out of your arm and fly in a bunch - 6 (Beelzebub: 10) - piercing, 3 blocks a tick. */
    public static void spearVolley(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean u = inUnion(p);
        int n = u ? 10 : 6;
        // the spears grow out in a fan behind your hand, then turn and fly
        for (int t = 0; t < VOLLEY_RELEASE; t++) {
            final int tt = t;
            AstaFx.later(t, () -> {
                if (!alive(p)) return;
                Vec3 look = p.getLookAngle(), rt = rightOf(look), up = rt.cross(look).normalize();
                Vec3 base = p.getEyePosition().add(0, -0.2, 0).subtract(look.scale(0.3));
                for (int i = 0; i < n; i++) {
                    double a = (i - (n - 1) / 2.0) * 0.32;
                    Vec3 at = base.add(rt.scale(Math.sin(a) * 1.4)).add(up.scale(0.6 + Math.cos(a) * 0.5));
                    float g = Math.min(1f, (tt + 1) / 5f);
                    bone(sl, SPEAR, at, look, up, 0.9f * g, 0.08f, 2, 0, FxPacket.HOLD, 0, Vec3.ZERO, 0);
                }
            });
        }
        sound(sl, p.position(), SoundEvents.SKELETON_AMBIENT, 0.8f, 0.6f);
        AstaFx.later(VOLLEY_RELEASE, () -> {
            if (!alive(p)) return;
            if (u) unionMsg(p, "spear_volley");
            Vec3 look = p.getLookAngle(), rt = rightOf(look), up = rt.cross(look).normalize();
            Vec3 base = p.getEyePosition().add(0, -0.2, 0).subtract(look.scale(0.3));
            Vec3 aim = clip(sl, p, p.getEyePosition(), p.getEyePosition().add(look.scale(40)));
            for (int i = 0; i < n; i++) {
                double a = (i - (n - 1) / 2.0) * 0.32;
                Vec3 at = base.add(rt.scale(Math.sin(a) * 1.4)).add(up.scale(0.6 + Math.cos(a) * 0.5));
                Vec3 d = aim.add(rt.scale(Math.sin(a) * 1.5)).subtract(at).normalize();
                AstaFx.later(i / 3, () -> spear(p, sl, at, d, power(p, power) * 0.4f, 0, new HashSet<>(), 0.9f));
            }
            sound(sl, p.position(), SoundEvents.TRIDENT_THROW, 1f, 0.7f);
        });
    }

    /** One flying bone spear, piercing; it sticks where it hits a wall. */
    private static void spear(ServerPlayer p, ServerLevel sl, Vec3 pos, Vec3 dir, float dmg, int step, Set<UUID> hit, float size) {
        if (!alive(p) || step > 14) return;
        Vec3 want = pos.add(dir.scale(3));
        Vec3 next = clip(sl, p, pos, want);
        bone(sl, SPEAR, next.subtract(dir.scale(0.9 * size)), dir, rightOf(dir), 0.9f * size, 0.08f * size, 2, 0, FxPacket.HOLD, 0, dir.scale(3), 0);
        for (LivingEntity t : enemies(sl, p, new AABB(pos, next).inflate(1.0))) {
            if (!t.getBoundingBox().inflate(0.35).clip(pos, next).isPresent()) continue;
            if (!hit.add(t.getUUID())) continue;
            AstaFx.damage(p, t, dmg, "bone_spear_volley");
            sound(sl, t.position(), SoundEvents.TRIDENT_HIT, 0.9f, 1.2f);
        }
        if (next.distanceToSqr(want) > 1.0E-4) {   // stuck in the wall, shaking, then crumbling
            bone(sl, SPEAR, next.subtract(dir.scale(0.6 * size)), dir, rightOf(dir), 0.9f * size, 0.08f * size, 40, 0, FxPacket.POP, 0, Vec3.ZERO, 0);
            crack(sl, next, 0.4f);
            return;
        }
        AstaFx.later(1, () -> spear(p, sl, next, dir, dmg, step + 1, hit, size));
    }

    // ================================================================== 2. Bone Spike Eruption
    private static final int ERUPT_RELEASE = 6;

    /** Stamp: spikes of bone burst out of the ground in a widening line 16 blocks ahead, impaling and launching. */
    public static void spikeEruption(ServerPlayer p, float power) {
        AstaFx.later(ERUPT_RELEASE, () -> {
            if (!alive(p)) return;
            ServerLevel sl = p.serverLevel();
            boolean u = inUnion(p);
            if (u) unionMsg(p, "spike_eruption");
            Vec3 look = p.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z).normalize(), rt = rightOf(flat);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            double reach = u ? 22 : 16;
            Set<UUID> hit = new HashSet<>();
            for (int k = 1; k <= (int) (reach / 1.4); k++) {
                final int kk = k;
                AstaFx.later(k, () -> {
                    if (!alive(p)) return;
                    double dist = kk * 1.4;
                    int per = 1 + (int) (dist / 5);
                    for (int j = 0; j < per; j++) {
                        double off = (j - (per - 1) / 2.0) * 1.1 + r.nextDouble(-0.3, 0.3);
                        Vec3 g = FxLib.ground(sl, p.position().add(flat.scale(dist)).add(rt.scale(off)).add(0, 2, 0), 5);
                        if (g == null) continue;
                        Vec3 dir = new Vec3(r.nextDouble(-0.35, 0.35), 1, r.nextDouble(-0.35, 0.35)).add(flat.scale(0.3)).normalize();
                        float len = (float) (1.6 + dist * 0.08 + r.nextDouble(0.6));
                        spikeUp(sl, g, dir, len, 0.22f + (float) (dist * 0.006), 34, 3);
                        for (LivingEntity t : enemies(sl, p, new AABB(g, g).inflate(1.0, 2.5, 1.0))) {
                            if (!hit.add(t.getUUID())) continue;
                            AstaFx.damage(p, t, power(p, power), "bone_spike_eruption");
                            t.setDeltaMovement(t.getDeltaMovement().add(0, 0.8, 0).add(flat.scale(0.3)));
                            t.hurtMarked = true;
                        }
                    }
                    if (kk % 2 == 0) crack(sl, p.position().add(flat.scale(dist)), 0.6f);
                });
            }
        });
    }

    // ================================================================== 3. Bone Sword
    private static final int[] SWORD_CUTS = {5, 12, 20};   // the swings of the fire_bat_combo animation

    /** A long sword of bone grows out of your hand - three cuts that reach 4.5 blocks, each throwing splinters of bone. */
    public static void boneSword(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean u = inUnion(p);
        // the sword itself, following the swings: {tick, yaw, pitch}
        double[][] keys = {{0, 30, -50}, {3, 100, 10}, {5, -80, 10}, {9, -95, 10}, {12, 80, 10}, {17, 0, 85}, {20, 0, -40}, {26, 30, -55}};
        for (int t = 0; t <= 26; t++) {
            final int tt = t;
            AstaFx.later(t, () -> {
                if (!alive(p)) return;
                Vec3[] a = swordFrame(p, keys, tt), b = swordFrame(p, keys, tt + 1);
                float len = u ? 1.3f : 1.05f;
                Vec3 c0 = a[0].add(a[1].scale(len)), c1 = b[0].add(b[1].scale(len));
                bone(sl, SPEAR, c0, a[1], rightOf(a[1]), len, 0.09f, 2, 0, FxPacket.HOLD, 0, c1.subtract(c0), 0);   // the blade: a long sharpened bone
                Vec3 g0 = a[0], g1 = b[0];
                bone(sl, LONG, g0, rightOf(a[1]), a[1], 0.22f, 0.05f, 2, 0, FxPacket.HOLD, 0, g1.subtract(g0), 0);   // the guard: a cross-bone
            });
        }
        sound(sl, p.position(), SoundEvents.SKELETON_AMBIENT, 0.7f, 0.7f);
        for (int i = 0; i < 3; i++) {
            final int ii = i;
            AstaFx.later(SWORD_CUTS[i], () -> {
                if (!alive(p)) return;
                Vec3 look = p.getLookAngle();
                double reach = u ? 6 : 4.5;
                for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(reach))) {
                    Vec3 to = mid(t).subtract(p.getEyePosition());
                    if (to.length() > reach || to.normalize().dot(look) < 0.3) continue;
                    AstaFx.damage(p, t, power(p, power) * (ii == 2 ? 0.5f : 0.35f), "bone_sword");
                    t.setDeltaMovement(t.getDeltaMovement().add(look.scale(0.6)).add(0, ii == 2 ? -0.3 : 0.2, 0));
                    t.hurtMarked = true;
                    splinters(sl, mid(t), look);
                }
                sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.8f);
            });
        }
    }

    private static Vec3[] swordFrame(ServerPlayer p, double[][] keys, double t) {
        double yaw = keys[keys.length - 1][1], pitch = keys[keys.length - 1][2];
        for (int i = 1; i < keys.length; i++) {
            if (t <= keys[i][0]) {
                double f = (t - keys[i - 1][0]) / (keys[i][0] - keys[i - 1][0]);
                f = f * f * (3 - 2 * f);
                yaw = keys[i - 1][1] + (keys[i][1] - keys[i - 1][1]) * f;
                pitch = keys[i - 1][2] + (keys[i][2] - keys[i - 1][2]) * f;
                break;
            }
        }
        if (t <= keys[0][0]) { yaw = keys[0][1]; pitch = keys[0][2]; }
        Vec3 look = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).normalize();
        Vec3 rt = rightOf(look);
        double y = Math.toRadians(yaw), pt = Math.toRadians(pitch);
        Vec3 flat = look.scale(Math.cos(y)).add(rt.scale(Math.sin(y)));
        Vec3 dir = flat.scale(Math.cos(pt)).add(0, Math.sin(pt), 0).normalize();
        Vec3 grip = p.position().add(0, 1.15, 0).add(look.scale(0.3)).add(dir.scale(0.25));
        return new Vec3[]{grip, dir};
    }

    /** Splinters of bone flying off a hit. */
    private static void splinters(ServerLevel sl, Vec3 at, Vec3 push) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int k = 0; k < 4; k++) {
            Vec3 d = push.scale(0.5).add(r.nextDouble(-1, 1), r.nextDouble(0, 1), r.nextDouble(-1, 1)).normalize();
            bone(sl, SPIKE, at, d, rightOf(d), 0.15f, 0.04f, 8, 0, FxPacket.POP, 30, d.scale(0.25), 6);
        }
        crack(sl, at, 0.4f);
    }

    // ================================================================== 4. Bone Whips
    private static final int WHIP_TICKS = 30;

    /** Four whips of vertebrae grow out of your back and lash at every enemy within 9 blocks for 1.5 s (Beelzebub: six, 12 blocks, faster). */
    public static void boneWhips(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean u = inUnion(p);
        if (u) unionMsg(p, "bone_whips");
        int n = u ? 6 : 4;
        double R = u ? 12 : 9;
        Map<UUID, Long> lastHit = new HashMap<>();
        for (int t = 0; t < WHIP_TICKS; t++) {
            final int tt = t;
            AstaFx.later(t, () -> {
                if (!alive(p)) return;
                List<LivingEntity> foes = new ArrayList<>(enemies(sl, p, p.getBoundingBox().inflate(R)));
                foes.removeIf(e -> e.distanceTo(p) > R);
                foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
                Vec3 look = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).normalize();
                Vec3 back = p.position().add(0, 1.3, 0).subtract(look.scale(0.3));
                for (int i = 0; i < n; i++) {
                    double side = (i - (n - 1) / 2.0);
                    Vec3 root = back.add(rightOf(look).scale(side * 0.25));
                    LivingEntity t0 = foes.isEmpty() ? null : foes.get(i % foes.size());
                    // the whip's tip: lashing out toward its target and back in a cycle, or swaying behind you
                    double phase = (tt + i * 4) % 10 / 10.0;
                    double lash = Math.sin(Math.PI * phase);
                    Vec3 rest = root.add(rightOf(look).scale(side * 1.2)).add(0, 1.2, 0).subtract(look.scale(1.2));
                    Vec3 tip = t0 != null ? rest.add(mid(t0).subtract(rest).scale(lash)) : rest.add(look.scale(lash * 4));
                    whip(sl, root, tip, side, tt);
                    if (t0 != null && lash > 0.92) {
                        Long lh = lastHit.get(t0.getUUID());
                        if (lh == null || tt - lh >= 6) {
                            lastHit.put(t0.getUUID(), (long) tt);
                            AstaFx.damage(p, t0, power(p, power) * 0.22f, "bone_whips");
                            Vec3 kb = mid(t0).subtract(root).normalize();
                            t0.setDeltaMovement(t0.getDeltaMovement().add(kb.scale(0.5)).add(0, 0.2, 0));
                            t0.hurtMarked = true;
                            crack(sl, mid(t0), 0.3f);
                        }
                    }
                }
                if (tt % 6 == 0) sound(sl, p.position(), SoundEvents.SKELETON_STEP, 0.9f, 0.7f);
            });
        }
    }

    /** A whip of vertebrae from root to tip, sagging in a curve. */
    private static void whip(ServerLevel sl, Vec3 root, Vec3 tip, double seed, int t) {
        Vec3 d = tip.subtract(root);
        double len = d.length();
        int segs = (int) Math.max(4, Math.min(14, len / 0.4));
        Vec3 sag = new Vec3(0, -1, 0).scale(len * 0.12).add(rightOf(d.normalize()).scale(Math.sin(t * 0.5 + seed) * len * 0.1));
        Vec3 prev = root;
        for (int k = 1; k <= segs; k++) {
            double f = (double) k / segs;
            Vec3 q = root.add(d.scale(f)).add(sag.scale(Math.sin(Math.PI * f)));
            Vec3 ax = q.subtract(prev);
            float s = (float) (1.0 - f * 0.55);
            bone(sl, VERTEBRA, prev.add(ax.scale(0.5)), ax, new Vec3(0, 1, 0), (float) (ax.length() * 0.5), 0.09f * s, 2, 0, FxPacket.HOLD, 0, Vec3.ZERO, 0);
            prev = q;
        }
        bone(sl, SPIKE, tip, d.normalize(), new Vec3(0, 1, 0), 0.25f, 0.07f, 2, 0, FxPacket.HOLD, 0, Vec3.ZERO, 0);   // a hooked point at the end
    }

    // ================================================================== 5. Ribcage Prison
    /** Giant ribs burst out of the ground around the enemy you aim at and close over it like a cage: held for 3 s, then crushed. */
    public static void ribcage(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 20, 18);
        if (t == null) {
            p.displayClientMessage(Component.translatable("ui.arcane_addon.bone.no_target").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Vec3 g = FxLib.ground(sl, t.position().add(0, 1, 0), 6);
        final Vec3 c = g != null ? g : t.position();
        int ribs = 10;
        for (int i = 0; i < ribs; i++) {
            double a = Math.PI * 2 * i / ribs;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 base = c.add(out.scale(2.0));
            Vec3 dir = new Vec3(0, 1, 0).subtract(out.scale(0.35)).normalize();
            bone(sl, RIB, base.add(dir.scale(2.0)), dir, out.scale(-1), 2.0f, 0.13f, 70, 5, FxPacket.GROUND, 0, Vec3.ZERO, 0);
        }
        bone(sl, LONG, c.add(0, 4.0, 0), new Vec3(0, 1, 0), new Vec3(1, 0, 0), 0.5f, 0.25f, 64, 6, FxPacket.POP, 0, Vec3.ZERO, 0);   // the spine on top
        crack(sl, c, 1f);
        sound(sl, c, SoundEvents.SKELETON_AMBIENT, 1f, 0.5f);
        for (int k = 0; k < 60; k += 2) {
            AstaFx.later(5 + k, () -> {
                if (!alive(p) || !t.isAlive()) return;
                Vec3 off = new Vec3(t.getX() - c.x, 0, t.getZ() - c.z);
                if (off.length() > 1.4) { t.setDeltaMovement(off.normalize().scale(-0.3).add(0, t.getDeltaMovement().y, 0)); t.hurtMarked = true; }
                if (t.getY() > c.y + 3.2) { t.setDeltaMovement(t.getDeltaMovement().x, -0.3, t.getDeltaMovement().z); t.hurtMarked = true; }
            });
        }
        AstaFx.later(66, () -> {
            if (!alive(p) || !t.isAlive()) return;
            AstaFx.damage(p, t, power(p, power), "ribcage_prison");
            for (int i = 0; i < ribs; i++) {
                double a = Math.PI * 2 * i / ribs;
                Vec3 from = c.add(Math.cos(a) * 2.2, 1.0 + (i % 3) * 0.7, Math.sin(a) * 2.2);
                Vec3 d = c.add(0, 1.0, 0).subtract(from).normalize();
                bone(sl, SPIKE, from.add(d.scale(0.9)), d, rightOf(d), 0.9f, 0.12f, 10, 2, FxPacket.EXTEND, 0, Vec3.ZERO, 0);
            }
            splinters(sl, mid(t), new Vec3(0, 1, 0));
            crack(sl, c, 1.2f);
        });
    }

    // ================================================================== 6. Marrow Regeneration
    /** Bone plates grow over your body for 10 s while you regrow: heal 30% of your health over the time, take 25% less damage. */
    public static void regeneration(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        PLATES.put(p.getUUID(), sl.getGameTime() + 200);
        for (int k = 0; k < 200; k += 10) {
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                p.heal(p.getMaxHealth() * 0.015f);
            });
        }
        // the plates growing over you: ribs around the chest, a bone along each arm
        for (int i = 0; i < 6; i++) {
            final int ii = i;
            AstaFx.later(i * 2, () -> {
                if (!alive(p)) return;
                double a = Math.PI * 2 * ii / 6;
                Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                bone(sl, RIB, p.position().add(0, 1.1, 0).add(out.scale(0.42)), new Vec3(0, 1, 0), out, 0.45f, 0.06f, 14, 3, FxPacket.POP, 0, Vec3.ZERO, 0);
            });
        }
        crack(sl, p.position(), 0.8f);
        sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_TURTLE, 1f, 0.6f);
        sendPlates(p, 200);
    }

    private static void sendPlates(ServerPlayer p, int life) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 14; pkt.id = p.getId(); pkt.palette = 6; pkt.life = life;   // AuraOutlineLayer 6 = bone plates
        AddonNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }

    // ================================================================== 7. Bone Thicket
    /** A tangle of bones bursts out across a 7-block circle where you aim: everything in it is hit, held back and cut for 4 s. */
    public static void thicket(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 hitAt = clip(sl, p, eye, eye.add(p.getLookAngle().scale(28)));
        Vec3 g = FxLib.ground(sl, hitAt.add(0, 0.5, 0), 10);
        final Vec3 c = g != null ? g : hitAt;
        final double R = inUnion(p) ? 9 : 7;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 46; i++) {
            final int ii = i;
            AstaFx.later(i / 4, () -> {
                double a = r.nextDouble(Math.PI * 2), d = Math.sqrt(r.nextDouble()) * R;
                Vec3 gg = FxLib.ground(sl, c.add(Math.cos(a) * d, 2, Math.sin(a) * d), 5);
                if (gg == null) return;
                Vec3 dir = new Vec3(r.nextDouble(-0.9, 0.9), r.nextDouble(0.4, 1.0), r.nextDouble(-0.9, 0.9)).normalize();
                int kind = ii % 3 == 0 ? LONG : SPIKE;
                float len = (float) r.nextDouble(1.0, 2.4);
                bone(sl, kind, gg.add(dir.scale(len * 0.5)), dir, rightOf(dir), len * 0.5f, (float) r.nextDouble(0.08, 0.16), 90, 3, FxPacket.GROUND, 0, Vec3.ZERO, 0);
            });
        }
        crack(sl, c, 1.2f);
        for (int t = 0; t < 80; t += 4) {
            final int tt = t;
            AstaFx.later(t + 4, () -> {
                if (!alive(p)) return;
                for (LivingEntity e : enemies(sl, p, new AABB(c, c).inflate(R, 3, R))) {
                    if (new Vec3(e.getX() - c.x, 0, e.getZ() - c.z).length() > R) continue;
                    e.setDeltaMovement(e.getDeltaMovement().multiply(0.2, 1, 0.2));   // tangled
                    e.hurtMarked = true;
                    if (tt % 12 == 0) AstaFx.damage(p, e, power(p, power) * 0.3f, "bone_thicket");
                }
            });
        }
    }

    // ================================================================== 8. Spatial Cube
    /** Beelzebub's spatial magic: a cube of space closes around the enemy you aim at - held still for 3 s - then crushes inward. */
    public static void spatialCube(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 30, 18);
        if (t == null) {
            p.displayClientMessage(Component.translatable("ui.arcane_addon.bone.no_target").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Vec3 c = mid(t);
        float h = 1.6f;
        cube(sl, c, h, 60);
        sound(sl, c, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.5f);
        sound(sl, c, SoundEvents.BEACON_POWER_SELECT, 0.6f, 0.5f);
        for (int k = 0; k < 60; k++) {
            AstaFx.later(k, () -> {
                if (!t.isAlive()) return;
                t.setDeltaMovement(Vec3.ZERO);
                t.hurtMarked = true;
                if (t.position().add(0, t.getBbHeight() * 0.5, 0).distanceTo(c) > 0.5)
                    t.teleportTo(c.x, c.y - t.getBbHeight() * 0.5, c.z);
            });
        }
        AstaFx.later(60, () -> {
            if (!alive(p) || !t.isAlive()) return;
            cube(sl, c, 0.4f, 6);   // the cube folds in
            AstaFx.damage(p, t, power(p, power), "spatial_cube");
            FxLib.implode(sl, SPACE, c, 1.2f);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 0.6f, 0.6f);
        });
    }

    /** A cube of space: its twelve edges as thin violet-black lines, plus a faint shimmer at the corners. */
    private static void cube(ServerLevel sl, Vec3 c, float h, int life) {
        int[][] edges = {{0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        Vec3[] k = new Vec3[8];
        for (int i = 0; i < 8; i++) k[i] = c.add((i & 1) != 0 ? h : -h, (i & 4) != 0 ? h : -h, (i & 2) != 0 ? h : -h);
        for (int[] e : edges) {
            FxSend.trail(sl, SPACE, k[e[0]], k[e[1]], 0.06f, life);
            FxSend.trail(sl, Fx.SILVER, k[e[0]], k[e[1]], 0.015f, life);
        }
    }

    // ================================================================== 9. Devil Union: Beelzebub
    private static final int UNION_FORM = 15;

    /** Beelzebub's power: horns and bone wings grow, your bones grow bigger and faster (+35% on every move), and you move faster, for 25 s. */
    public static void devilUnion(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        for (int k = 0; k < UNION_FORM; k += 3) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                ThreadLocalRandom r = ThreadLocalRandom.current();
                double a = r.nextDouble(Math.PI * 2);
                Vec3 g = FxLib.ground(sl, p.position().add(Math.cos(a) * (3 - kk * 0.15), 1, Math.sin(a) * (3 - kk * 0.15)), 3);
                if (g != null) spikeUp(sl, g, new Vec3(-Math.cos(a) * 0.4, 1, -Math.sin(a) * 0.4), 1.4f, 0.14f, 20, 2);
            });
        }
        sound(sl, p.position(), SoundEvents.WITHER_SPAWN, 0.4f, 1.4f);
        AstaFx.later(UNION_FORM, () -> {
            if (!alive(p)) return;
            UNION.put(p.getUUID(), sl.getGameTime() + UNION_TICKS);
            sendForm(p, UNION_TICKS);
            var att = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (att != null) {
                att.removeModifier(UNION_SPEED);
                att.addTransientModifier(new AttributeModifier(UNION_SPEED, "beelzebub", 0.3, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            FxLib.implode(sl, SPACE, p.position().add(0, 1, 0), 1.6f);
            p.displayClientMessage(Component.translatable("ui.arcane_addon.beelzebub.open").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
            crack(sl, p.position(), 1.5f);
        });
    }

    /** Client marker for the union form (shape 31): horns and bone wings are drawn on the player. */
    private static void sendForm(ServerPlayer p, int life) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 31; pkt.id = p.getId(); pkt.palette = (byte) B; pkt.life = life;
        AddonNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }

    // ================================================================== 10. Bone Spear Storm
    /** A cube of space opens high over the spot you aim at and pours down bone spears for 3 s across an 8-block circle. */
    public static void spearStorm(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 hitAt = clip(sl, p, eye, eye.add(p.getLookAngle().scale(36)));
        Vec3 g = FxLib.ground(sl, hitAt.add(0, 0.5, 0), 14);
        final Vec3 c = g != null ? g : hitAt;
        final double R = inUnion(p) ? 11 : 8;
        final Vec3 sky = c.add(0, 16, 0);
        cube(sl, sky, (float) R * 0.6f, 70);
        sound(sl, sky, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.4f);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int t = 0; t < 60; t++) {
            final int tt = t;
            AstaFx.later(8 + t, () -> {
                if (!alive(p)) return;
                for (int k = 0; k < 2; k++) {
                    double a = r.nextDouble(Math.PI * 2), d = Math.sqrt(r.nextDouble()) * R;
                    Vec3 land = c.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                    Vec3 top = sky.add(r.nextDouble(-R * 0.5, R * 0.5), 0, r.nextDouble(-R * 0.5, R * 0.5));
                    Vec3 dir = land.subtract(top).normalize();
                    stormSpear(p, sl, top, dir, power(p, power) * 0.18f, 0);
                }
                if (tt % 10 == 0) crack(sl, c, 0.6f);
            });
        }
    }

    private static void stormSpear(ServerPlayer p, ServerLevel sl, Vec3 pos, Vec3 dir, float dmg, int step) {
        if (!alive(p) || step > 12) return;
        Vec3 want = pos.add(dir.scale(3.2));
        Vec3 next = clip(sl, p, pos, want);
        bone(sl, SPEAR, next.subtract(dir.scale(0.8)), dir, rightOf(dir), 0.8f, 0.07f, 2, 0, FxPacket.HOLD, 0, dir.scale(3.2), 0);
        for (LivingEntity t : enemies(sl, p, new AABB(pos, next).inflate(0.8))) {
            if (!t.getBoundingBox().inflate(0.3).clip(pos, next).isPresent()) continue;
            AstaFx.damage(p, t, dmg, "bone_spear_storm");
            return;
        }
        if (next.distanceToSqr(want) > 1.0E-4) {   // stuck in the ground, then crumbles
            bone(sl, SPEAR, next.subtract(dir.scale(0.5)), dir, rightOf(dir), 0.8f, 0.07f, 30, 0, FxPacket.POP, 0, Vec3.ZERO, 0);
            return;
        }
        AstaFx.later(1, () -> stormSpear(p, sl, next, dir, dmg, step + 1));
    }

    // ================================================================== events
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        long now = p.serverLevel().getGameTime();
        UUID id = p.getUUID();
        Long u = UNION.get(id);
        if (u != null) {
            if (now > u) {
                UNION.remove(id);
                sendForm(p, 0);
                var att = p.getAttribute(Attributes.MOVEMENT_SPEED);
                if (att != null) att.removeModifier(UNION_SPEED);
                p.displayClientMessage(Component.translatable("ui.arcane_addon.beelzebub.close").withStyle(ChatFormatting.DARK_RED), true);
            } else if (now % 40 == 0) sendForm(p, (int) (u - now));
        }
        Long pl = PLATES.get(id);
        if (pl != null && now > pl) PLATES.remove(id);
        // passive: Endless Bone - you regrow
        if (now % 40 == 0 && p.getHealth() < p.getMaxHealth() && wearsBoneGrimoire(p)) p.heal(1f);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Long pl = PLATES.get(p.getUUID());
        if (pl != null && p.serverLevel().getGameTime() <= pl) e.setAmount(e.getAmount() * 0.75f);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        PLATES.remove(id); UNION.remove(id);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        PLATES.clear(); UNION.clear();
    }
}
