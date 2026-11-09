package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;
import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.item.WindGrimoireItem;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.util.Utils;
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
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
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
 * Yuno Grinberryall's Wind Magic - the ten spells of the Wind Grimoire (his four-leaf grimoire) and its passive.
 *
 *  1 Wind Blade Shower        - a fan of wind blades
 *  2 Tornado Fang             - a travelling tornado that drags enemies in, shreds projectiles, then flings them
 *  3 Sylph's Breath           - Bell blows everything in front of you away
 *  4 Gale White Bow           - a bow of wind: three piercing arrows
 *  5 Spirit's Hushed Dance    - take the mana around you into your control: the next attacks miss, and refill your mana
 *  6 Spirit of Zephyr         - a sword of wind: three fast cuts
 *  7 Spirit of Boreas         - wind halberds circle you, then fly at your enemies
 *  8 Spirit of Euros          - draw a great bow of condensed mana and loose one tremendous piercing arrow
 *  9 Spirit Dive              - merge with Sylph for 30 s: four wings of wind, free flight, every move turns into its Spirit Dive form
 * 10 Spirit Storm             - gather the mana around you and release it as a storm of wind
 *
 * Passive "Sylph's Favour": no fall damage (a gust always catches you), and 25% of arrows and projectiles aimed at
 * you are blown aside.
 * All visuals are the addon's own 3D shapes in the wind palette (mint green / white), plus wind streaks on the client.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public class WindFx {
    private static final int W = Fx.WIND, S = Fx.SILVER;

    // ------------------------------------------------------------------ state
    private static final Map<UUID, Long> DIVE = new HashMap<>();
    private static final Map<UUID, Dance> DANCE = new HashMap<>();
    private static final class Dance { long until; int charges; boolean counter; }
    private static final UUID DIVE_SPEED = UUID.fromString("7c2d4b63-ae1f-4a8b-b2d5-3a4c9f8eaa21");
    private static final int DIVE_TICKS = 30 * 20;

    // ------------------------------------------------------------------ helpers

    private static boolean wearsWindGrimoire(ServerPlayer p) {
        ItemStack s = Utils.getPlayerSpellbookStack(p);
        return s != null && s.getItem() instanceof WindGrimoireItem;
    }

    /** Right hand, in front of the shoulder. */
    private static Vec3 handPos(ServerPlayer p) {
        double yr = Math.toRadians(p.getYRot());
        double lx = Math.cos(yr), lz = Math.sin(yr), fx = -Math.sin(yr), fz = Math.cos(yr);
        return new Vec3(p.getX() + fx * 0.55 - lx * 0.35, p.getY() + p.getEyeHeight() * 0.78, p.getZ() + fz * 0.55 - lz * 0.35);
    }

    /** Left hand held out in front (the bow hand). */
    private static Vec3 bowHand(ServerPlayer p) {
        return p.getEyePosition().add(p.getLookAngle().scale(0.9)).add(0, -0.25, 0);
    }

    public static boolean diving(ServerPlayer p) {
        Long d = DIVE.get(p.getUUID());
        return d != null && p.serverLevel().getGameTime() <= d;
    }

    private static void diveMsg(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable("ui.iron_spell_grimoire.dive." + key).withStyle(ChatFormatting.GREEN), true);
    }

    /** A wind impact: a burst ring and a puff of light. */
    private static void gust(ServerLevel sl, Vec3 at, float s) {
        FxLib.impact(sl, W, at, s);
        FxSend.ring(sl, W, at, 0.2f * s, 1.6f * s, 0.05f, 6);
    }

    /** Anticipation: wind spiralling into your hand while the animation winds up. */
    private static void windup(ServerPlayer p, int ticks, boolean bow) {
        ServerLevel sl = p.serverLevel();
        for (int k = 0; k < ticks; k += 2) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 h = bow ? bowHand(p) : handPos(p);
                double a = kk * 1.9 + p.getYRot() * 0.017;
                Vec3 from = h.add(Math.cos(a) * 1.6, 0.6 - kk * 0.1, Math.sin(a) * 1.6);
                FxSend.trail(sl, W, from, from.add(h.subtract(from).scale(0.7)), 0.05f, 4);
            });
        }
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 0.25f, 1.6f);
    }

    // ================================================================== 1. Wind Blade Shower
    private static final int SHOWER_RELEASE = 10;

    /**
     * Sylph's feather blades, the way Yuno fights - precise, light, never wasted. Slender blades of wind open round
     * your shoulders like a pair of wings and hang there a breath, glinting; then Sylph guides each one, bending in a
     * clean curve onto its own mark (spread over every enemy ahead of you; with none, they arc out to the front) and
     * cutting once, exactly. Spirit Dive: twelve blades, bigger and brighter.
     */
    public static void windBladeShower(ServerPlayer p, float power) {
        windup(p, SHOWER_RELEASE, false);
        AstaFx.later(SHOWER_RELEASE, () -> {
            if (!alive(p)) return;
            ServerLevel sl = p.serverLevel();
            boolean dive = diving(p);
            if (dive) diveMsg(p, "wind_blade_shower");
            final int n = dive ? 12 : 8;
            Vec3 look = p.getLookAngle();
            Vec3 rt = rightOf(look), up = new Vec3(0, 1, 0);
            Vec3 centre = p.position().add(0, p.getBbHeight() * 0.72, 0);
            List<LivingEntity> foes = new ArrayList<>();
            for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(26))) {
                Vec3 to = mid(t).subtract(centre);
                if (to.length() < 26 && to.normalize().dot(look) > 0.25) foes.add(t);
            }
            foes.sort(Comparator.comparingDouble(t -> t.distanceToSqr(p)));
            // the blades open like wings: a fan of slender blades behind both shoulders
            Vec3[] at = new Vec3[n];
            for (int i = 0; i < n; i++) {
                double side = i % 2 == 0 ? -1 : 1, row = i / 2, rows = Math.max(1, n / 2 - 1);
                double a = Math.toRadians(25 + 55 * row / rows);   // each wing fans from low to high
                at[i] = centre.add(rt.scale(side * (0.5 + Math.sin(a) * 1.4))).add(up.scale(Math.cos(a) * 0.9 - 0.2)).add(look.scale(-0.35 - row * 0.05));
                Vec3 out = at[i].subtract(centre).normalize();
                final Vec3 pos = at[i], dirOut = out;
                AstaFx.later((int) (row * 1), () -> {   // they unfold from the middle outward
                    FxSend.slash(sl, W, pos, dirOut, rt.cross(dirOut), 0.18f, 0.95f, 9, 0.0f, 0.22f);
                    Fx.spark(sl, W, pos, 0.35f);
                });
            }
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 0.7f, 1.8f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.9f);
            // Sylph guides them off, left and right in turn, each along its own curve
            for (int i = 0; i < n; i++) {
                final int ii = i;
                final LivingEntity mark = foes.isEmpty() ? null : foes.get(i % foes.size());
                final Vec3 aim = look.add(rt.scale((i % 2 == 0 ? -1 : 1) * (0.05 + 0.04 * (i / 2)))).normalize();
                AstaFx.later(9 + i / 2 * 2 + (i % 2), () -> volleyBlade(p, sl, at[ii], mark, aim, rt, ii % 2 == 0 ? -1 : 1, power * (dive ? 0.55f : 0.5f), dive));
            }
        });
    }

    /** One guided feather blade: a quadratic curve from its perch to the mark, drawn as short slashes joined by a thin streak. */
    private static void volleyBlade(ServerPlayer p, ServerLevel sl, Vec3 from, LivingEntity mark, Vec3 aim, Vec3 rt, int side, float dmg, boolean big) {
        if (!alive(p)) return;
        Vec3 target = mark != null && mark.isAlive() ? mid(mark) : clip(sl, p, from, from.add(aim.scale(22)));
        Vec3 chord = target.subtract(from);
        double len = Math.max(1.0, chord.length());
        Vec3 ctrl = from.add(chord.scale(0.5)).add(rt.scale(side * Math.min(3.2, len * 0.35))).add(0, Math.min(1.5, len * 0.12), 0);
        final int T = (int) Math.max(5, Math.min(11, len / 2.4));
        final float s = big ? 1.25f : 1.0f;
        Vec3[] prev = {from};
        for (int k = 1; k <= T; k++) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                double t = kk / (double) T, u = 1 - t;
                Vec3 pos = from.scale(u * u).add(ctrl.scale(2 * u * t)).add(target.scale(t * t));
                Vec3 tan = pos.subtract(prev[0]);
                if (tan.lengthSqr() < 1.0E-6) return;
                Vec3 d = tan.normalize();
                FxSend.trail(sl, W, prev[0], pos, 0.05f * s, 5);
                if (kk % 2 == 0 || kk == T) FxSend.slash(sl, W, pos, d, rt.cross(d), 0.2f * s, 1.1f * s, 3, 0.0f, 0.22f);
                prev[0] = pos;
                if (kk == T) {   // the cut: exactly once, clean through the mark
                    Vec3 end = pos;
                    if (mark != null && mark.isAlive()) {
                        AstaFx.damage(p, mark, dmg, "wind_blade_shower");
                        FxSend.beam(sl, S, mid(mark).subtract(d.scale(0.9)), mid(mark).add(d.scale(0.9)), 0.1f, 4);
                    }
                    gust(sl, end, 0.55f * s);
                }
            });
        }
    }

    private static void blade(ServerPlayer p, ServerLevel sl, Vec3 from, Vec3 dir, float dmg, boolean big) {
        if (!alive(p)) return;
        final float SPEED = 2.2f;
        int life = 12;
        Vec3 end = clip(sl, p, from, from.add(dir.scale(SPEED * life)));
        Vec3 rt = rightOf(dir), up = rt.cross(dir).normalize();
        Vec3 span = rt.scale(0.94).add(up.scale(0.34)).normalize();   // nearly flat, a little tilted
        float s = big ? 1.3f : 1.0f;
        FxSend.slash(sl, W, from, dir, span, 1.0f * s, 1.9f * s, (int) Math.max(3, from.distanceTo(end) / SPEED) + 1, SPEED, 0.6f);
        for (LivingEntity t : enemies(sl, p, new AABB(from, end).inflate(1.6 * s))) {
            Vec3 on = closestOnSegment(from, end, mid(t));
            if (on.distanceTo(mid(t)) > 1.3 * s + t.getBbWidth() * 0.5) continue;
            final LivingEntity tt = t;
            AstaFx.later((int) (on.distanceTo(from) / SPEED), () -> {
                if (!tt.isAlive() || !alive(p)) return;
                AstaFx.damage(p, tt, dmg, "wind_blade_shower");
                gust(sl, mid(tt), 0.7f);
            });
        }
        if (end.distanceToSqr(from.add(dir.scale(SPEED * life))) > 0.5) {
            AstaFx.later((int) (from.distanceTo(end) / SPEED), () -> gust(sl, end, 0.6f));
        }
    }

    // ================================================================== 2. Tornado Fang
    private static final int TORNADO_RELEASE = 11;

    /**
     * Throw out a tornado that rolls forward along the ground for 2.5 s: it drags enemies into its spin, cuts them,
     * tears apart any projectile it touches, and at the end flings everything inside it into the sky.
     * Spirit Dive: a huge tornado that lasts 4 s and pulls from twice as far.
     */
    public static void tornadoFang(ServerPlayer p, float power) {
        windup(p, TORNADO_RELEASE, false);
        AstaFx.later(TORNADO_RELEASE, () -> {
            if (!alive(p)) return;
            ServerLevel sl = p.serverLevel();
            boolean dive = diving(p);
            if (dive) diveMsg(p, "tornado_fang");
            Vec3 look = p.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
            Vec3 start = p.position().add(flat.scale(2.5));
            Vec3 g = FxLib.ground(sl, start.add(0, 1, 0), 5);
            if (g != null) start = g;
            FxLib.shockRing(sl, W, start.add(0, 0.05, 0), dive ? 1.6f : 1.1f);
            sound(sl, start, net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 0.8f, 0.6f);
            sound(sl, start, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.6f);
            tornadoTick(p, sl, start, flat, power, 0, dive, new HashMap<>());
        });
    }

    private static void tornadoTick(ServerPlayer p, ServerLevel sl, Vec3 base, Vec3 dir, float dmg, int age, boolean big, Map<UUID, Integer> held) {
        if (!alive(p)) return;
        final int LIFE = big ? 80 : 50;
        final double R = big ? 4.5 : 3.0, H = big ? 11 : 7, PULL = big ? 10 : 6;
        // the funnel: drawn by the client as a spinning tornado (spiral bands of wind, dust at its foot)
        tornadoPacket(sl, base, (float) R * 1.25f, (float) H, age, dir.scale(big ? 0.32 : 0.42));
        if (age % 4 == 0) FxSend.ring(sl, W, base.add(0, 0.06, 0), (float) R * 0.5f, (float) R * 1.3f, 0.06f, 6);   // dust swirl at its foot
        // pull, spin, lift, cut
        for (LivingEntity t : enemies(sl, p, new AABB(base, base.add(0, H, 0)).inflate(PULL))) {
            Vec3 axis = new Vec3(base.x, t.getY(), base.z);
            Vec3 in = axis.subtract(t.position());
            double d = in.length();
            if (d > PULL || t.getY() > base.y + H + 2) continue;
            Vec3 tan = d < 1.0E-3 ? Vec3.ZERO : new Vec3(-in.z, 0, in.x).normalize();
            Vec3 v = t.getDeltaMovement().scale(0.5)
                    .add(d < 1.0E-3 ? Vec3.ZERO : in.normalize().scale(Math.min(0.35, 0.1 + 0.6 / Math.max(1, d))))
                    .add(tan.scale(d < R + 1 ? 0.45 : 0.15))
                    .add(0, d < R + 1 ? 0.12 : 0.0, 0)
                    .add(dir.scale(0.45));
            t.setDeltaMovement(v.x, Math.min(v.y, 0.5), v.z);
            t.hurtMarked = true;
            t.fallDistance = 0;
            if (d < R + 1) held.put(t.getUUID(), 1);
            if (d < R + 1 && age % 10 == 0) AstaFx.damage(p, t, dmg * 0.25f, "tornado_fang");
        }
        // shred projectiles: the fang tears enemy magic apart
        for (Projectile pr : sl.getEntitiesOfClass(Projectile.class, new AABB(base, base.add(0, H, 0)).inflate(R + 0.5))) {
            if (pr.getOwner() == p) continue;
            gust(sl, pr.position(), 0.5f);
            pr.discard();
        }
        if (age % 10 == 0) sound(sl, base, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 0.5f + age * 0.004f);
        if (age >= LIFE) {
            for (LivingEntity t : enemies(sl, p, new AABB(base, base.add(0, H, 0)).inflate(R + 1.5))) {
                if (new Vec3(base.x, t.getY(), base.z).distanceTo(t.position()) > R + 1.5) continue;
                AstaFx.damage(p, t, dmg, "tornado_fang");
                t.setDeltaMovement(t.getDeltaMovement().add(0, big ? 1.4 : 1.0, 0));
                t.hurtMarked = true;
            }
            FxSend.wall(sl, W, base.add(0, 0.3, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), (float) R, (float) R * 2.4f, -180, 180, 0.5f, 10, 0, 0);
            FxSend.beam(sl, W, base, base.add(0, H + 3, 0), (float) R * 0.4f, 8);
            FxSend.beam(sl, S, base, base.add(0, H + 3, 0), 0.08f, 6);
            sound(sl, base, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 0.6f, 1.4f);
            return;
        }
        // roll forward along the ground (climb or step down with it)
        Vec3 next = base.add(dir.scale(big ? 0.32 : 0.42));
        Vec3 g = FxLib.ground(sl, next.add(0, 1.5, 0), 4);
        final Vec3 nb = g != null ? g : next;
        AstaFx.later(1, () -> tornadoTick(p, sl, nb, dir, dmg, age + 1, big, held));
    }

    static void tornadoPacket(ServerLevel sl, Vec3 base, float r, float h, int age, Vec3 vel) {
        FxPacket p = new FxPacket();
        p.shape = 28; p.palette = (byte) W; p.life = 1; p.c = base; p.v = vel; p.r = r; p.h = h; p.a0 = age;
        AddonNetwork.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(base.x, base.y, base.z, 128, sl.dimension())), p);
    }

    // ================================================================== 3. Sylph's Breath
    private static final int BREATH_RELEASE = 8;

    /**
     * Bell blows: a great gust in a 60-degree cone, 10 blocks - everything in it is hit and thrown far back.
     * Spirit Dive: 80 degrees, 14 blocks, and it throws them into the air.
     */
    public static void sylphsBreath(ServerPlayer p, float power) {
        windup(p, BREATH_RELEASE, false);
        AstaFx.later(BREATH_RELEASE, () -> {
            if (!alive(p)) return;
            ServerLevel sl = p.serverLevel();
            boolean dive = diving(p);
            if (dive) diveMsg(p, "sylphs_breath");
            double range = dive ? 14 : 10, half = Math.toRadians(dive ? 40 : 30);
            Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
            Vec3 rt = rightOf(look), up = rt.cross(look).normalize();
            for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(range))) {
                Vec3 to = mid(t).subtract(eye);
                double d = to.length();
                if (d > range || to.normalize().dot(look) < Math.cos(half)) continue;
                final LivingEntity tt = t;
                AstaFx.later((int) (d / 2.5), () -> {
                    if (!tt.isAlive() || !alive(p)) return;
                    AstaFx.damage(p, tt, power, "sylphs_breath");
                    Vec3 push = new Vec3(look.x, 0, look.z).normalize().scale(dive ? 2.6 : 2.0);
                    tt.setDeltaMovement(tt.getDeltaMovement().add(push.x, dive ? 0.9 : 0.4, push.z));
                    tt.hurtMarked = true;
                    gust(sl, mid(tt), 0.6f);
                });
            }
            // the breath: a swirling cone of wind pouring out of your palm (drawn by the client: spiral streamlines
            // racing forward, rings of air rolling out at the front, leaves and dust carried along)
            Vec3 o = eye.add(look.scale(0.8)).add(0, -0.2, 0);
            FxPacket gp = new FxPacket();
            gp.shape = 29; gp.palette = (byte) W; gp.life = 14; gp.c = o; gp.d = look; gp.r = (float) range; gp.h = (float) half;
            AddonNetwork.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(o.x, o.y, o.z, 96, sl.dimension())), gp);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 0.8f, 1.3f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 0.8f, 1.4f);
        });
    }

    // ================================================================== 4. Gale White Bow
    private static final int BOW_RELEASE = 10;

    /**
     * A bow of wind forms in your hand: three arrows of wind, 60 blocks, piercing everything they pass through.
     * Spirit Dive: five arrows that seek out enemies.
     */
    public static void galeWhiteBow(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        windup(p, BOW_RELEASE, true);
        // the bow: a tall arc of wind in front of your hand, held while you draw
        for (int k = 0; k <= BOW_RELEASE; k += 3) {
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 look = p.getLookAngle(), rt = rightOf(look), up = rt.cross(look).normalize();
                FxSend.wall(sl, W, bowHand(p).subtract(look.scale(0.55)), look, up, 0.75f, 0.75f, -62, 62, 0.04f, 4, 0, 0);
                FxSend.beam(sl, S, bowHand(p).add(up.scale(0.65)).subtract(look.scale(0.15)), bowHand(p).subtract(up.scale(0.65)).subtract(look.scale(0.15)), 0.015f, 4);   // the string
            });
        }
        AstaFx.later(BOW_RELEASE, () -> {
            if (!alive(p)) return;
            boolean dive = diving(p);
            if (dive) diveMsg(p, "gale_white_bow");
            int n = dive ? 5 : 3;
            Vec3 look = p.getLookAngle();
            for (int i = 0; i < n; i++) {
                double a = Math.toRadians((i - (n - 1) / 2.0) * (dive ? 6 : 4));
                Vec3 d = rotY(look, a).normalize();
                AstaFx.later(i / 2, () -> arrow(p, sl, bowHand(p), d, null, power * 0.7f, 0, dive, new HashSet<>(), 0.18f));
            }
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ARROW_SHOOT, 1f, 1.4f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.TRIDENT_RIPTIDE_1, 0.6f, 1.6f);
        });
    }

    /** One wind arrow, 4 blocks a tick, piercing; seeking = steer toward the nearest enemy ahead. */
    private static void arrow(ServerPlayer p, ServerLevel sl, Vec3 pos, Vec3 dir, LivingEntity target, float dmg, int step, boolean seeking, Set<UUID> hit, float width) {
        if (!alive(p) || step > 15) return;
        if (seeking) {
            if (target == null || !target.isAlive()) {
                double best = Math.cos(Math.toRadians(30));
                for (LivingEntity t : enemies(sl, p, new AABB(pos, pos).inflate(30))) {
                    if (hit.contains(t.getUUID())) continue;
                    Vec3 to = mid(t).subtract(pos);
                    double d = to.normalize().dot(dir);
                    if (d > best) { best = d; target = t; }
                }
            }
            if (target != null && target.isAlive()) dir = dir.scale(0.6).add(mid(target).subtract(pos).normalize().scale(0.4)).normalize();
        }
        Vec3 want = pos.add(dir.scale(4));
        Vec3 next = clip(sl, p, pos, want);
        FxSend.trail(sl, W, pos, next, width, 7);
        FxSend.trail(sl, S, pos, next, width * 0.25f, 5);
        FxSend.spike(sl, S, next.subtract(dir.scale(0.6)), dir, 0.8f, width * 0.6f, 2, 0, FxPacket.HOLD, 0, dir.scale(4), 0);
        for (LivingEntity t : enemies(sl, p, new AABB(pos, next).inflate(1.0))) {
            if (!t.getBoundingBox().inflate(0.4).clip(pos, next).isPresent()) continue;
            if (!hit.add(t.getUUID())) continue;
            AstaFx.damage(p, t, dmg, "gale_white_bow");
            gust(sl, mid(t), 0.7f);
            sound(sl, t.position(), net.minecraft.sounds.SoundEvents.ARROW_HIT, 1f, 1.2f);
        }
        if (next.distanceToSqr(want) > 1.0E-4) { gust(sl, next, 0.7f); return; }
        final Vec3 nd = dir; final LivingEntity tg = target;
        AstaFx.later(1, () -> arrow(p, sl, next, nd, tg, dmg, step + 1, seeking, hit, width));
    }

    // ================================================================== 5. Spirit's Hushed Dance
    /**
     * You take the mana around you under your control and read every movement in it: for 6 s the next 3 attacks
     * miss as the wind carries you aside, and each one you dodge refills 15 mana.
     * Spirit Dive: 8 s, 5 dodges, and every dodge answers with a wind blade.
     */
    public static void hushedDance(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean dive = diving(p);
        Dance d = new Dance();
        d.until = sl.getGameTime() + (dive ? 160 : 120);
        d.charges = dive ? 5 : 3;
        d.counter = dive;
        DANCE.put(p.getUUID(), d);
        ModeHud.show(p, "hushed_dance", dive ? 160 : 120, d.charges);
        if (dive) diveMsg(p, "hushed_dance");
        // mana drawn in from all around: thin streams curling into you
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int k = 0; k < 8; k++) {
            final double a = Math.PI * 2 * k / 8 + r.nextDouble(0.3);
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 c = p.position().add(0, 1, 0);
                Vec3 from = c.add(Math.cos(a) * 5, r.nextDouble(-0.5, 1.5), Math.sin(a) * 5);
                Vec3 mid = c.add(Math.cos(a + 0.6) * 2.4, 0.3, Math.sin(a + 0.6) * 2.4);
                FxSend.trail(sl, W, from, mid, 0.05f, 7);
                FxSend.trail(sl, W, mid, c.add(Math.cos(a + 1.2) * 0.6, 0, Math.sin(a + 1.2) * 0.6), 0.04f, 6);
            });
        }
        FxSend.ring(sl, W, p.position().add(0, 0.05, 0), 4.5f, 0.6f, 0.05f, 10);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.4f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 0.4f, 1.8f);
    }

    // ================================================================== 6. Spirit of Zephyr
    private static final int[] ZEPHYR_CUTS = {4, 11, 19};

    /**
     * A sword of wind in your hand: three fast cuts - across, down, and rising - each one throwing a blade of wind
     * 8 blocks ahead and carrying you a step forward.
     * Spirit Dive: Saint Spirit of Zephyr - the third cut is one colossal blade of wind that splits 30 blocks.
     */
    public static void spiritOfZephyr(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean dive = diving(p);
        if (dive) diveMsg(p, "spirit_of_zephyr");
        // the sword of wind forming in your hand
        FxSend.ring(sl, W, handPos(p), 1.2f, 0.1f, 0.05f, 5);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 0.4f, 2f);
        for (int i = 0; i < 3; i++) {
            final int ii = i;
            AstaFx.later(ZEPHYR_CUTS[i], () -> {
                if (!alive(p)) return;
                if (ii == 2 && dive) { saintZephyr(p, sl, power); return; }
                Vec3 look = p.getLookAngle(), rt = rightOf(look), up = rt.cross(look).normalize();
                Vec3 span = switch (ii) {
                    case 0 -> rt.scale(0.97).add(up.scale(0.25));          // across
                    case 1 -> up.scale(0.95).add(rt.scale(0.3));           // down
                    default -> rt.scale(-0.6).add(up.scale(0.8));          // rising diagonal
                };
                span = span.normalize();
                Vec3 o = p.getEyePosition().add(0, -0.35, 0).add(look.scale(1.0));
                FxSend.slash(sl, W, o, look, span, 1.6f, 2.0f, 4, 0f, 0.8f);           // the cut itself
                FxSend.slash(sl, W, o.add(look.scale(0.6)), look, span, 2.0f, 3.8f, 10, 2.4f, 0.75f);   // the blade it throws, thin and far
                Vec3 end = clip(sl, p, o, o.add(look.scale(22)));
                for (LivingEntity t : enemies(sl, p, new AABB(o, end).inflate(2.2))) {
                    Vec3 on = closestOnSegment(o, end, mid(t));
                    if (on.distanceTo(mid(t)) > 2.0 + t.getBbWidth() * 0.5) continue;
                    final LivingEntity tt = t;
                    AstaFx.later((int) (on.distanceTo(o) / 2.4), () -> {   // when the thrown blade reaches them
                        if (!tt.isAlive() || !alive(p)) return;
                        AstaFx.damage(p, tt, power * 0.55f, "spirit_of_zephyr");
                        gust(sl, mid(tt), 0.8f);
                    });
                }
                p.setDeltaMovement(p.getDeltaMovement().add(new Vec3(look.x, 0, look.z).normalize().scale(0.45)));
                p.hurtMarked = true;
                sound(sl, p.position(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 1.1f, 1.3f + ii * 0.15f);
            });
        }
    }

    private static void saintZephyr(ServerPlayer p, ServerLevel sl, float power) {
        Vec3 look = p.getLookAngle(), rt = rightOf(look), up = rt.cross(look).normalize();
        Vec3 span = rt.scale(-0.55).add(up.scale(0.83)).normalize();
        Vec3 o = p.getEyePosition().add(0, -0.3, 0).add(look.scale(1.2));
        final float SPEED = 3.0f;
        Vec3 end = o.add(look.scale(30));
        FxSend.slash(sl, W, o, look, span, 3.0f, 3.6f, 5, 0f, 2.2f);
        FxSend.slash(sl, W, o.add(look.scale(1)), look, span, 4.0f, 13f, 12, SPEED, 2.4f);
        FxSend.slash(sl, S, o.add(look.scale(1)), look, span, 3.6f, 12f, 10, SPEED, 0.6f);
        for (LivingEntity t : enemies(sl, p, new AABB(o, end).inflate(7))) {
            Vec3 on = closestOnSegment(o, end, mid(t));
            double along = on.distanceTo(o);
            if (on.distanceTo(mid(t)) > 2.5 + along * 0.15) continue;
            final LivingEntity tt = t;
            AstaFx.later((int) (along / SPEED), () -> {
                if (!tt.isAlive() || !alive(p)) return;
                AstaFx.damage(p, tt, power * 1.6f, "spirit_of_zephyr");
                gust(sl, mid(tt), 1.4f);
            });
        }
        FxLib.shockRing(sl, W, p.position().add(0, 0.05, 0), 1.4f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.7f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, 0.35f, 1.6f);
    }

    // ================================================================== 7. Spirit of Boreas
    private static final int BOREAS_LAUNCH = 17;

    /**
     * Wind halberds form and circle you, then fly at your enemies one after another, piercing through.
     * Spirit Dive: eight halberds.
     */
    public static void spiritOfBoreas(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean dive = diving(p);
        if (dive) diveMsg(p, "spirit_of_boreas");
        int n = dive ? 8 : 4;
        // circling you: one packet, animated by the client every frame so it is perfectly smooth
        FxSend.orbit(sl, p, 0, n, 1.8f, 1.6f, 0.25f, 0f, 0.2f, 6, 2, BOREAS_LAUNCH);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 0.6f, 1.2f);
        AstaFx.later(BOREAS_LAUNCH, () -> {
            if (!alive(p)) return;
            List<LivingEntity> foes = new ArrayList<>(enemies(sl, p, p.getBoundingBox().inflate(30)));
            foes.removeIf(t -> t.distanceTo(p) > 30 || !p.hasLineOfSight(t));
            foes.sort(Comparator.comparingDouble(t -> t.distanceToSqr(p)));
            Vec3 look = p.getLookAngle();
            for (int i = 0; i < n; i++) {
                final int ii = i;
                LivingEntity target = foes.isEmpty() ? null : foes.get(i % foes.size());
                AstaFx.later(i * 2, () -> {   // each leaves the ring from exactly where the client is drawing it
                    if (!alive(p)) return;
                    double age = BOREAS_LAUNCH + ii * 2;
                    double a = Math.PI * 2 * ii / n + age * 0.25;
                    Vec3 c = p.position().add(Math.cos(a) * 1.8, 1.6 + 0.2 * Math.sin(age * 0.35 + ii), Math.sin(a) * 1.8);
                    Vec3 d = target != null && target.isAlive() ? mid(target).subtract(c).normalize() : rotY(p.getLookAngle(), (ii - (n - 1) / 2.0) * 0.12).normalize();
                    halberdFly(p, sl, c, d, power * 0.6f, 0, new HashSet<>());
                });
            }
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.TRIDENT_THROW, 1f, 1.3f);
        });
    }

    /** A halberd flying along {@code vel} for {@code ticks} ticks, sent once (the client moves it smoothly). */
    private static void halberdFlight(ServerLevel sl, Vec3 c, Vec3 dir, Vec3 vel, int ticks) {
        Vec3 back = c.subtract(dir.scale(1.4));
        int life = ticks + 1;
        FxSend.spike(sl, W, back, dir, 2.8f, 0.07f, life, 0, FxPacket.HOLD, 0, vel, ticks);
        Vec3 side = rightOf(dir);
        FxSend.spike(sl, S, c.add(dir.scale(0.9)), dir, 0.7f, 0.12f, life, 0, FxPacket.HOLD, 0, vel, ticks);
        FxSend.spike(sl, W, c.add(dir.scale(0.7)), side.add(dir.scale(0.3)).normalize(), 0.55f, 0.16f, life, 0, FxPacket.HOLD, 0, vel, ticks);
    }

    /** One halberd of wind: a long shaft and a crescent blade near its head. */
    private static void halberd(ServerLevel sl, Vec3 c, Vec3 dir, float s) { halberd(sl, c, dir, s, Vec3.ZERO); }

    private static void halberd(ServerLevel sl, Vec3 c, Vec3 dir, float s, Vec3 vel) {
        Vec3 back = c.subtract(dir.scale(1.4 * s));
        FxSend.spike(sl, W, back, dir, 2.8f * s, 0.07f, 2, 0, FxPacket.HOLD, 0, vel, 0);
        Vec3 side = rightOf(dir);
        FxSend.spike(sl, S, c.add(dir.scale(0.9 * s)), dir, 0.7f * s, 0.12f, 2, 0, FxPacket.HOLD, 0, vel, 0);   // the spear point
        FxSend.spike(sl, W, c.add(dir.scale(0.7 * s)), side.add(dir.scale(0.3)).normalize(), 0.55f * s, 0.16f, 2, 0, FxPacket.HOLD, 0, vel, 0);   // the axe blade
    }

    private static void halberdFly(ServerPlayer p, ServerLevel sl, Vec3 pos, Vec3 dir, float dmg, int step, Set<UUID> hit) {
        if (!alive(p) || step > 14) return;
        if (step == 0) {   // the whole flight in one go: the client glides it, no per-tick re-sends
            Vec3 far = clip(sl, p, pos, pos.add(dir.scale(3 * 15)));
            int ticks = (int) Math.max(1, Math.ceil(far.distanceTo(pos) / 3.0));
            halberdFlight(sl, pos, dir, dir.scale(3), ticks);
        }
        Vec3 want = pos.add(dir.scale(3));
        Vec3 next = clip(sl, p, pos, want);
        FxSend.trail(sl, W, pos, next, 0.12f, 6);
        for (LivingEntity t : enemies(sl, p, new AABB(pos, next).inflate(1.2))) {
            if (!t.getBoundingBox().inflate(0.6).clip(pos, next).isPresent()) continue;
            if (!hit.add(t.getUUID())) continue;
            AstaFx.damage(p, t, dmg, "spirit_of_boreas");
            gust(sl, mid(t), 0.9f);
            sound(sl, t.position(), net.minecraft.sounds.SoundEvents.TRIDENT_HIT, 1f, 1.2f);
        }
        if (next.distanceToSqr(want) > 1.0E-4) { gust(sl, next, 0.9f); return; }
        AstaFx.later(1, () -> halberdFly(p, sl, next, dir, dmg, step + 1, hit));
    }

    // ================================================================== 8. Spirit of Euros
    private static final int EUROS_RELEASE = 24;

    /**
     * Draw a great bow of condensed mana for a moment while the arrow gathers, then loose it: one tremendous arrow
     * that pierces everything for 80 blocks.
     * Spirit Dive: the arrow is bigger and bursts into a storm of wind where it ends.
     */
    public static void spiritOfEuros(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        for (int k = 0; k < EUROS_RELEASE; k += 3) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 look = p.getLookAngle(), rt = rightOf(look), up = rt.cross(look).normalize();
                Vec3 h = bowHand(p);
                FxSend.wall(sl, W, h.subtract(look.scale(0.8)), look, up, 1.1f, 1.1f, -65, 65, 0.06f, 4, 0, 0);   // the great bow
                FxSend.ring(sl, W, h.add(look.scale(0.4)), 1.4f - kk * 0.04f, 0.1f, 0.04f, 4);                   // mana gathering on the arrow
                FxSend.beam(sl, S, h.subtract(look.scale(0.9)), h.add(look.scale(0.6)), 0.03f + kk * 0.002f, 4);  // the arrow growing
            });
        }
        windup(p, EUROS_RELEASE, true);
        AstaFx.later(EUROS_RELEASE, () -> {
            if (!alive(p)) return;
            boolean dive = diving(p);
            if (dive) diveMsg(p, "spirit_of_euros");
            eurosArrow(p, sl, bowHand(p), p.getLookAngle(), power, 0, dive, new HashSet<>());
            FxLib.shockRing(sl, W, bowHand(p), 0.9f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ARROW_SHOOT, 1f, 0.6f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, 0.3f, 1.8f);
        });
    }

    private static void eurosArrow(ServerPlayer p, ServerLevel sl, Vec3 pos, Vec3 dir, float dmg, int step, boolean big, Set<UUID> hit) {
        if (!alive(p)) return;
        int max = 16;
        float w = big ? 0.75f : 0.5f;
        Vec3 want = pos.add(dir.scale(5));
        Vec3 next = clip(sl, p, pos, want);
        FxSend.trail(sl, W, pos, next, w, 10);
        FxSend.trail(sl, S, pos, next, w * 0.22f, 8);
        if (step == 0) {   // the arrowhead's whole flight, sent once: the client glides it (no per-tick re-sends)
            Vec3 far = clip(sl, p, pos, pos.add(dir.scale(5 * (max + 1))));
            int ticks = (int) Math.max(1, Math.ceil(far.distanceTo(pos) / 5.0));
            FxSend.spike(sl, S, pos.add(dir.scale(3.6)), dir, 1.8f, w * 0.7f, ticks + 1, 0, FxPacket.HOLD, 0, dir.scale(5), ticks - 1);
        }
        Vec3 rt = rightOf(dir);
        FxSend.wall(sl, W, next, rt, rt.cross(dir).normalize(), w * 1.6f, w * 3.2f, -180, 180, 0.08f, 6, 0, 0);   // rings of air peeled off it
        for (LivingEntity t : enemies(sl, p, new AABB(pos, next).inflate(1.8))) {
            if (!t.getBoundingBox().inflate(w + 0.4).clip(pos, next).isPresent()) continue;
            if (!hit.add(t.getUUID())) continue;
            AstaFx.damage(p, t, dmg, "spirit_of_euros");
            t.setDeltaMovement(t.getDeltaMovement().add(dir.scale(0.8)));
            t.hurtMarked = true;
            gust(sl, mid(t), 1.2f);
        }
        boolean blocked = next.distanceToSqr(want) > 1.0E-4;
        if (blocked || step >= max) {
            gust(sl, next, big ? 2.0f : 1.2f);
            if (big) {
                for (LivingEntity t : enemies(sl, p, new AABB(next, next).inflate(5))) {
                    if (t.position().distanceTo(next) > 5) continue;
                    AstaFx.damage(p, t, dmg * 0.6f, "spirit_of_euros");
                    t.setDeltaMovement(t.getDeltaMovement().add(t.position().subtract(next).normalize().scale(1.2)).add(0, 0.5, 0));
                    t.hurtMarked = true;
                }
                FxSend.wall(sl, W, next, new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.5f, 6f, -180, 180, 0.6f, 9, 0, 0);
                sound(sl, next, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 0.6f, 1.5f);
            }
            return;
        }
        AstaFx.later(1, () -> eurosArrow(p, sl, next, dir, dmg, step + 1, big, hit));
    }

    // ================================================================== 9. Spirit Dive
    private static final int DIVE_FORM = 15;   // ticks into the transformation animation when Sylph merges with you

    /**
     * Sylph merges with you: four wings of wind open on your back, you fly freely (jump in the air to take off, then
     * fly where you look - the same controls as Devil Union), you move faster, and for 30 s every other move of the
     * grimoire turns into its Spirit Dive form.
     */
    public static void spiritDive(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        // Sylph gathers: wind spirals in around you
        for (int k = 0; k < DIVE_FORM; k += 2) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                FxSend.ring(sl, W, p.position().add(0, 0.1 + kk * 0.13, 0), 3f - kk * 0.15f, 0.3f, 0.06f, 4);
            });
        }
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 0.8f, 1.0f);
        AstaFx.later(DIVE_FORM, () -> {
            if (!alive(p)) return;
            DIVE.put(p.getUUID(), sl.getGameTime() + DIVE_TICKS);
            sendDive(p, DIVE_TICKS);
            speed(p, true);
            FxLib.shockRing(sl, W, p.position().add(0, 0.05, 0), 1.6f);
            FxSend.wall(sl, W, p.position().add(0, 1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.5f, 7f, -180, 180, 1.2f, 10, 0, 0);
            FxSend.beam(sl, S, p.position(), p.position().add(0, 9, 0), 0.4f, 8);
            p.displayClientMessage(Component.translatable("ui.iron_spell_grimoire.dive.open").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 1f, 1.6f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, 0.7f, 1.6f);
        });
    }

    private static void sendDive(ServerPlayer p, int life) {
        ModeHud.show(p, "spirit_dive", life);
        FxPacket pkt = new FxPacket();
        pkt.shape = 18; pkt.id = p.getId(); pkt.palette = (byte) W; pkt.life = life;
        AddonNetwork.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }

    private static void speed(ServerPlayer p, boolean on) {
        var att = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (att == null) return;
        att.removeModifier(DIVE_SPEED);
        if (on) att.addTransientModifier(new AttributeModifier(DIVE_SPEED, "spirit_dive", 0.3, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    // ================================================================== 10. Spirit Storm
    private static final int STORM_RELEASE = 21;

    /**
     * Gather the mana around you - streams of wind pour in from every side into your hands - then release it as one
     * vast storm of wind, 50 blocks long, that hits everything in it and drives it away.
     * Spirit Dive: Double Spirit Storm - two storms twisting around each other, wider and 70 blocks long.
     */
    public static void spiritStorm(ServerPlayer p, float power) {
        if (LimitBreak.active(p)) { LimitFx.starStorm(p, power); return; }   // Surpassing Limits: the evolved ultimate
        ServerLevel sl = p.serverLevel();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int k = 0; k < STORM_RELEASE; k++) {
            final int kk = k;
            AstaFx.later(k, () -> {
                if (!alive(p)) return;
                Vec3 h = p.getEyePosition().add(p.getLookAngle().scale(0.8)).add(0, -0.3, 0);
                double a = r.nextDouble(Math.PI * 2), el = r.nextDouble(-0.3, 0.9);
                Vec3 from = h.add(Math.cos(a) * Math.cos(el) * 7, Math.sin(el) * 5, Math.sin(a) * Math.cos(el) * 7);
                Vec3 bend = h.add(from.subtract(h).scale(0.45)).add(Math.cos(a + 1.5) * 1.2, 0.3, Math.sin(a + 1.5) * 1.2);
                FxSend.trail(sl, W, from, bend, 0.06f, 6);
                FxSend.trail(sl, W, bend, h, 0.05f, 5);
                if (kk % 4 == 0) FxSend.ring(sl, W, h, 1.6f - kk * 0.05f, 0.15f, 0.04f, 5);
                if (kk % 2 == 0) {   // stars twinkling in as the storm gathers
                    double sa = r.nextDouble(Math.PI * 2);
                    star(sl, h.add(Math.cos(sa) * r.nextDouble(0.8, 2.6), r.nextDouble(-0.6, 1.4), Math.sin(sa) * r.nextDouble(0.8, 2.6)), p.getLookAngle(), 0.25f + r.nextFloat() * 0.3f, 6);
                }
            });
        }
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 0.8f, 0.7f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, 0.6f, 1.8f);
        AstaFx.later(STORM_RELEASE, () -> {
            if (!alive(p)) return;
            boolean dive = diving(p);
            if (dive) diveMsg(p, "spirit_storm");
            Vec3 dir = flat(p.getLookAngle());
            Vec3 rt = rightOf(dir);
            Vec3 first = p.position().add(dir.scale(3.5));
            final int cyclones = dive ? 2 : 1;              // Double Spirit Storm: two cyclones side by side
            final double R = dive ? 3.6 : 3.0;
            final float height = dive ? 15f : 12f;
            final int LIFE = 44;
            final double speed = 0.75;
            // a great cyclone is born ahead and rolls forward, pulling everything into its spin
            Vec3[] centre = new Vec3[cyclones];
            for (int c = 0; c < cyclones; c++) centre[c] = first.add(rt.scale(cyclones == 1 ? 0 : (c == 0 ? -4.2 : 4.2)));
            final Vec3[] posRef = centre;
            Set<UUID> caught = new HashSet<>();
            star(sl, p.getEyePosition().add(dir.scale(0.9)), dir, 1.8f, 8);   // a star flares in your hands as it leaves
            FxLib.shockRing(sl, W, p.position().add(0, 0.05, 0), 1.8f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 1.5f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 1f, 0.8f);
            for (int t = 0; t < LIFE; t++) {
                final int tt = t;
                AstaFx.later(t, () -> {
                    if (!alive(p)) return;
                    ThreadLocalRandom rr = ThreadLocalRandom.current();
                    for (int c = 0; c < cyclones; c++) {
                        Vec3 pos = posRef[c];
                        Vec3 nxt = pos.add(dir.scale(speed));
                        Vec3 wall = clip(sl, p, pos.add(0, 1.2, 0), nxt.add(0, 1.2, 0));
                        if (wall.distanceToSqr(nxt.add(0, 1.2, 0)) > 1.0E-4) nxt = pos;   // a wall holds it where it is
                        Vec3 ground = FxLib.floor(sl, nxt.add(0, 2, 0));
                        if (ground != null) nxt = new Vec3(nxt.x, ground.y, nxt.z);
                        posRef[c] = nxt;
                        float grow = Math.min(1f, tt / 8f) * (tt > LIFE - 8 ? (LIFE - tt) / 8f : 1f);
                        tornadoPacket(sl, nxt, (float) R * Math.max(0.4f, grow), height * Math.max(0.4f, grow), tt, dir.scale(speed));
                        if (tt % 3 == 0) {   // blades of wind orbiting the column, one ring high and one low
                            for (int q = 0; q < 2; q++) {
                                double ang = tt * 0.55 + q * Math.PI;
                                Vec3 o = nxt.add(Math.cos(ang) * R * 1.15, height * (q == 0 ? 0.28 : 0.6), Math.sin(ang) * R * 1.15);
                                Vec3 tan = new Vec3(-Math.sin(ang), 0.12, Math.cos(ang));
                                FxSend.crescent(sl, W, o, tan, new Vec3(0, 1, 0), 0.9f, 0.55f, 4, 0.0f);
                            }
                        }
                        if (tt % 2 == 0) {   // a river of stars through the column
                            double sa = rr.nextDouble(Math.PI * 2), sd = rr.nextDouble() * R;
                            star(sl, nxt.add(Math.cos(sa) * sd, rr.nextDouble() * height * 0.8, Math.sin(sa) * sd), dir, rr.nextInt(5) == 0 ? 0.9f : 0.35f, 8 + rr.nextInt(6));
                        }
                        // everything near is drawn in, spun up the column and cut
                        for (LivingEntity e : enemies(sl, p, new AABB(nxt, nxt).inflate(R * 1.7, height * 0.6, R * 1.7))) {
                            Vec3 off = flat(e.position().subtract(nxt));
                            double dist = e.position().subtract(nxt).multiply(1, 0, 1).length();
                            if (dist > R * 1.7) continue;
                            Vec3 tan = new Vec3(-off.z, 0, off.x);
                            e.setDeltaMovement(tan.scale(0.55).add(off.scale(-0.08 * Math.min(2.0, dist))).add(0, 0.28, 0));
                            e.hurtMarked = true;
                            caught.add(e.getUUID());
                            if (tt % 6 == 0) {
                                AstaFx.damage(p, e, power * (dive ? 0.3f : 0.25f), "spirit_storm");
                                FxSend.beam(sl, S, mid(e).subtract(tan.scale(0.6)), mid(e).add(tan.scale(0.6)), 0.08f, 3);
                            }
                        }
                        if (tt % 8 == 0) sound(sl, nxt, net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 0.7f, 0.7f + rr.nextFloat() * 0.3f);
                    }
                });
            }
            // the end: the cyclone bursts - one great star and a shockwave that throws out everything still inside
            AstaFx.later(LIFE, () -> {
                if (!alive(p)) return;
                for (int c = 0; c < cyclones; c++) {
                    Vec3 e0 = posRef[c];
                    star(sl, e0.add(0, 1.2, 0), dir, dive ? 3.6f : 2.8f, 14);
                    gust(sl, e0.add(0, 1, 0), dive ? 2.4f : 1.8f);
                    FxLib.shockRing(sl, W, e0.add(0, 0.1, 0), (float) R + 1.2f);
                    FxSend.ring(sl, S, e0.add(0, 1.2, 0), 0.4f, (float) R * 2.2f, 0.05f, 8);
                    boolean any = false;
                    for (LivingEntity e : enemies(sl, p, new AABB(e0, e0).inflate(R * 2.2, height * 0.5, R * 2.2))) {
                        if (e.position().subtract(e0).multiply(1, 0, 1).length() > R * 2.2) continue;
                        AstaFx.damage(p, e, power * (dive ? 1.3f : 1.1f), "spirit_storm");
                        e.setDeltaMovement(flat(e.position().subtract(e0)).scale(1.5).add(0, 0.9, 0));
                        e.hurtMarked = true;
                        any = true;
                    }
                    if (any && c == 0) FxSend.impactFrame(sl, e0.add(0, 1, 0), 3, FxSend.FRAME_YUNO, 40);
                }
                sound(sl, posRef[0], net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 0.6f, 1.6f);
                sound(sl, posRef[0], net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 1.2f, 0.6f);
            });
        });
    }

    /** A four-pointed star glint facing {@code view}: two long silver-white rays and two short diagonal ones. */
    static void star(ServerLevel sl, Vec3 c, Vec3 view, float size, int life) {
        Vec3 a = rightOf(view), b = a.cross(view).normalize();
        FxSend.trail(sl, S, c.subtract(a.scale(size)), c.add(a.scale(size)), size * 0.09f, life);
        FxSend.trail(sl, S, c.subtract(b.scale(size)), c.add(b.scale(size)), size * 0.09f, life);
        Vec3 d1 = a.add(b).normalize().scale(size * 0.4), d2 = a.subtract(b).normalize().scale(size * 0.4);
        FxSend.trail(sl, W, c.subtract(d1), c.add(d1), size * 0.06f, life);
        FxSend.trail(sl, W, c.subtract(d2), c.add(d2), size * 0.06f, life);
    }


    // ================================================================== Mana Zone
    private static final double ZONE_R = 12;
    private static final Map<UUID, long[]> ZONEW = new HashMap<>();          // player -> {until, power*100}

    /**
     * Yuno's Mana Zone: he spreads his mana through the air around him, so he feels everything inside it and the wind
     * answers him from anywhere - for 20 s, every second a blade of wind appears beside each enemy (up to 3) within
     * 12 blocks and cuts it, with no aiming needed.
     * Spirit Dive: five enemies at once and stronger blades.
     */
    public static void manaZone(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        boolean dive = diving(p);
        ZONEW.put(p.getUUID(), new long[]{sl.getGameTime() + 400, (long) (power * 100)});
        ModeHud.show(p, "mana_zone", 400);
        if (dive) diveMsg(p, "mana_zone");
        // the mana spreads out: a ring racing to the edge of the zone, then streams curling back in
        FxSend.ring(sl, W, p.position().add(0, 0.05, 0), 0.5f, (float) ZONE_R, 0.08f, 14);
        FxSend.ring(sl, S, p.position().add(0, 0.05, 0), 0.3f, (float) ZONE_R * 0.7f, 0.05f, 10);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int k = 0; k < 10; k++) {
            final double a = Math.PI * 2 * k / 10 + r.nextDouble(0.3);
            AstaFx.later(2 + k, () -> {
                if (!alive(p)) return;
                Vec3 c = p.position().add(0, 1, 0);
                Vec3 out = c.add(Math.cos(a) * ZONE_R * 0.9, r.nextDouble(0.2, 1.8), Math.sin(a) * ZONE_R * 0.9);
                Vec3 mid = c.add(Math.cos(a + 0.5) * ZONE_R * 0.5, 0.4, Math.sin(a + 0.5) * ZONE_R * 0.5);
                FxSend.trail(sl, W, out, mid, 0.05f, 8);
                FxSend.trail(sl, W, mid, c.add(Math.cos(a + 1.0) * 0.8, 0, Math.sin(a + 1.0) * 0.8), 0.04f, 7);
            });
        }
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.1f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 0.5f, 1.5f);
    }

    private static void zoneTick(ServerPlayer p, long now) {
        long[] z = ZONEW.get(p.getUUID());
        if (z == null) return;
        ServerLevel sl = p.serverLevel();
        if (now > z[0]) {
            ZONEW.remove(p.getUUID());
            ModeHud.hide(p, "mana_zone");
            FxSend.ring(sl, W, p.position().add(0, 0.05, 0), (float) ZONE_R, 0.5f, 0.05f, 10);
            return;
        }
        if (now % 10 == 0) {   // the edge of the zone, faint, with one slow stream of wind drifting round it
            FxSend.ring(sl, W, p.position().add(0, 0.05, 0), (float) ZONE_R - 0.15f, (float) ZONE_R, 0.05f, 12);
            double a = now * 0.07;
            Vec3 c = p.position().add(0, 0.6, 0);
            FxSend.trail(sl, W, c.add(Math.cos(a) * ZONE_R, 0.3, Math.sin(a) * ZONE_R), c.add(Math.cos(a + 0.5) * ZONE_R, 0.9, Math.sin(a + 0.5) * ZONE_R), 0.04f, 10);
        }
        if (now % 20 != 0) return;
        boolean dive = diving(p);
        float dmg = z[1] / 100f * (dive ? 0.5f : 0.38f);
        List<LivingEntity> near = new ArrayList<>(enemies(sl, p, p.getBoundingBox().inflate(ZONE_R)));
        near.removeIf(t -> t.distanceTo(p) > ZONE_R);
        near.sort(Comparator.comparingDouble(t -> t.distanceToSqr(p)));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int max = dive ? 5 : 3;
        for (int i = 0; i < Math.min(max, near.size()); i++) {
            LivingEntity t = near.get(i);
            double a = r.nextDouble(Math.PI * 2);
            Vec3 from = mid(t).add(Math.cos(a) * 3.0, r.nextDouble(0.5, 2.2), Math.sin(a) * 3.0);   // appears beside it: the zone reaches everywhere
            Vec3 aim = mid(t).subtract(from).normalize();
            final int ii = i;
            AstaFx.later(1 + i * 2, () -> volleyBlade(p, sl, from, t, aim, rightOf(aim), ii % 2 == 0 ? -1 : 1, dmg, dive));
        }
    }

    // ================================================================== events
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        long now = p.serverLevel().getGameTime();
        UUID id = p.getUUID();
        Long d = DIVE.get(id);
        if (d != null) {
            if (now > d) {
                DIVE.remove(id);
                sendDive(p, 0);
                speed(p, false);
                FxSend.ring(p.serverLevel(), W, p.position().add(0, 0.05, 0), 3f, 0.3f, 0.05f, 8);
                p.displayClientMessage(Component.translatable("ui.iron_spell_grimoire.dive.close").withStyle(ChatFormatting.GREEN), true);
            } else {
                p.fallDistance = 0;
                if (now % 40 == 0) sendDive(p, (int) (d - now));
            }
        }
        zoneTick(p, now);
        Dance dc = DANCE.get(id);
        if (dc != null && (now > dc.until || dc.charges <= 0)) { DANCE.remove(id); ModeHud.hide(p, "hushed_dance"); }
    }

    /** Hushed Dance dodges; the passive cancels fall damage and blows some projectiles aside. */
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        ServerLevel sl = p.serverLevel();
        boolean wears = wearsWindGrimoire(p);
        if (e.getSource().is(DamageTypeTags.IS_FALL) && (wears || diving(p))) {
            e.setCanceled(true);
            FxSend.ring(sl, W, p.position().add(0, 0.05, 0), 0.3f, 2.2f, 0.06f, 6);   // a gust catches you
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 0.3f, 2f);
            return;
        }
        if (wears && e.getSource().is(DamageTypeTags.IS_PROJECTILE) && ThreadLocalRandom.current().nextFloat() < 0.25f) {
            e.setCanceled(true);
            Entity proj = e.getSource().getDirectEntity();
            if (proj != null) {
                gust(sl, proj.position(), 0.6f);
                proj.setDeltaMovement(proj.getDeltaMovement().scale(-0.3).add(0, 0.3, 0));
            }
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 0.4f, 1.8f);
            return;
        }
        long now = sl.getGameTime();
        Dance d = DANCE.get(p.getUUID());
        Entity src = e.getSource().getEntity();
        if (d != null && now <= d.until && d.charges > 0 && src != null && src != p) {
            e.setCanceled(true);
            d.charges--;
            ModeHud.show(p, "hushed_dance", (int) Math.max(1, d.until - p.serverLevel().getGameTime()), d.charges);
            Vec3 away = p.position().subtract(src.position()).multiply(1, 0, 1);
            Vec3 side = rightOf(away.lengthSqr() < 1.0E-4 ? p.getLookAngle() : away.normalize());
            for (int s = 1; s >= -1; s -= 2) {   // the wind carries you aside, left or right, whichever is free
                Vec3 dest = p.position().add(side.scale(1.8 * s));
                if (sl.noCollision(p, p.getBoundingBox().move(dest.subtract(p.position())))) {
                    FxSend.trail(sl, W, p.position().add(0, 1, 0), dest.add(0, 1, 0), 0.25f, 7);
                    p.connection.teleport(dest.x, dest.y, dest.z, p.getYRot(), p.getXRot());
                    break;
                }
            }
            MagicData data = MagicData.getPlayerMagicData(p);
            data.setMana((float) Math.min(p.getAttributeValue(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get()), data.getMana() + 15));
            if (d.counter && src instanceof LivingEntity le && le.isAlive()) {
                AstaFx.later(3, () -> {
                    if (!le.isAlive() || !alive(p)) return;
                    Vec3 from = p.getEyePosition().add(0, -0.3, 0);
                    blade(p, sl, from, mid(le).subtract(from).normalize(), 6f, false);
                });
            }
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 0.5f, 2f);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        DIVE.remove(id); DANCE.remove(id); ZONEW.remove(id);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        DIVE.clear(); DANCE.clear(); ZONEW.clear();
    }
}
