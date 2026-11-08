package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.item.SteelGrimoireItem;
import com.example.arcaneaddon.network.FxPacket;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Klaus Lunettes' Steel Magic - the eight spells of the Steel Grimoire and its passive.
 * The steel itself is drawn as real shaded 3D solids (FxSend.box / FxSend.spike) in steel, white steel and gold,
 * with glow and impacts from the effect library ({@link FxLib}).
 *
 *  1 Violent Rotating Lance       - a spinning steel lance that drills forward through everything in its path
 *  2 Blazing Spiral Lance         - a wound-up lance thrust that spirals into one target and staggers it
 *  3 Steel Castle's Armored Wall  - a steel wall in front of you; nothing gets through it from the front for 8 s
 *  4 White Steel Horse Cart       - a white steel cart that charges forward, trampling enemies; nearby allies ride its wake
 *  5 Thunder Arrow                - steel rails charge for 1 s, then fire a steel bolt like a railgun
 *  6 Acier Silva                  - a forest of steel spikes erupts around you in widening rings
 *  7 Steel Prison                 - steel bars lock the enemy you look at in place, then crush shut   (addon original)
 *  8 Knight's Armor               - steel plating for 20 s: heavy armor, slower steps                 (addon original)
 *
 * Passive "Unbending Steel": projectiles deal 25% less damage to you.
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public class SteelFx {
    private static final int ST = FxLib.STEEL, GD = FxLib.GOLD, SV = FxLib.SILVER, LT = FxLib.LIGHTNING, FI = FxLib.FIRE;

    private record Wall(Vec3 center, Vec3 normal, long until) {}
    private static final Map<UUID, Wall> WALLS = new HashMap<>();
    private static final Map<UUID, Long> ARMOR = new HashMap<>();

    // ------------------------------------------------------------------ helpers
    private static void sound(ServerLevel sl, Vec3 at, SoundEvent s, float v, float pitch) {
        sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, v, pitch);
    }

    private static Vec3 mid(LivingEntity t) { return t.position().add(0, t.getBbHeight() * 0.5, 0); }

    private static Vec3 flat(Vec3 d) {
        Vec3 f = new Vec3(d.x, 0, d.z);
        return f.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.normalize();
    }

    private static Vec3 clip(ServerLevel sl, Entity p, Vec3 a, Vec3 b) {
        var hit = sl.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? b : hit.getLocation();
    }

    private static List<LivingEntity> enemies(ServerLevel sl, ServerPlayer p, AABB box) {
        return sl.getEntitiesOfClass(LivingEntity.class, box, x -> x != p && x.isAlive() && !x.isAlliedTo(p));
    }

    private static boolean alive(ServerPlayer p) { return p.isAlive() && !p.isRemoved(); }

    private static Vec3 handPos(ServerPlayer p) {
        double yr = Math.toRadians(p.getYRot());
        double lx = Math.cos(yr), lz = Math.sin(yr), fx = -Math.sin(yr), fz = Math.cos(yr);
        return new Vec3(p.getX() + fx * 0.55 - lx * 0.4, p.getY() + p.getEyeHeight() * 0.72, p.getZ() + fz * 0.55 - lz * 0.4);
    }

    private static boolean wearsSteelGrimoire(ServerPlayer p) {
        ItemStack s = Utils.getPlayerSpellbookStack(p);
        return s != null && s.getItem() instanceof SteelGrimoireItem;
    }

    // ------------------------------------------------------------------ 3D steel pieces
    private static final float POP = FxPacket.POP, GROUND = FxPacket.GROUND, EXTEND = FxPacket.EXTEND;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int WHITE = FxLib.SILVER;   // "white steel"

    private static Vec3 perp(Vec3 d) { return Math.abs(d.y) > 0.95 ? new Vec3(1, 0, 0) : UP; }

    /** Height of the floor under (x, z), searching a little above and below y. */
    private static double floorY(ServerLevel sl, double x, double y, double z) {
        var hit = sl.clip(new ClipContext(new Vec3(x, y + 1.5, z), new Vec3(x, y - 3.0, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        return hit.getType() == HitResult.Type.MISS ? y : hit.getLocation().y;
    }

    /**
     * A solid 3D steel lance from {@code tail} along {@code dir}: square shaft, gold collar and pommel, faceted drill tip.
     * Every piece spins and travels together. Returns the effect ids (to stop it at a wall).
     */
    private static int[] lance3d(ServerLevel sl, Vec3 tail, Vec3 dir, float len, float thick, int life, int rise, float spin, Vec3 vel, int moveTicks) {
        Vec3 up = perp(dir);
        float shaft = len * 0.6f;
        Vec3 collar = tail.add(dir.scale(shaft));
        return new int[]{
            FxSend.box(sl, ST, tail.add(dir.scale(shaft / 2)), dir, up, shaft / 2, thick, thick, life, rise, POP, spin, vel, moveTicks),
            FxSend.box(sl, GD, collar, dir, up, 0.07f, thick * 1.8f, thick * 1.8f, life, rise, POP, spin, vel, moveTicks),
            FxSend.box(sl, GD, tail, dir, up, 0.06f, thick * 1.4f, thick * 1.4f, life, rise, POP, spin, vel, moveTicks),
            FxSend.spike(sl, ST, collar.add(dir.scale(0.04)), dir, len - shaft, thick * 2.3f, life, rise, POP, spin, vel, moveTicks)
        };
    }

    private static void killAll(ServerLevel sl, int[] ids, Vec3 near) {
        for (int id : ids) FxSend.kill(sl, id, near);
    }

    // ================================================================== 1. Violent Rotating Lance
    /** A solid steel lance forms in your hand and drills forward, spinning, through everything in its path; it sticks in walls. */
    public static void violentLance(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 start = handPos(p), dir = p.getLookAngle();
        int[] ids = lance3d(sl, start, dir, 2.4f, 0.075f, 20, 2, 42f, dir.scale(1.7), 0);
        FxLib.flash(sl, ST, start, 1.0f);
        FxSend.wall(sl, SV, start.add(dir.scale(0.4)), FxLib.right(dir), FxLib.up(dir), 0.15f, 1.1f, -180, 180, 0.05f, 6, 0, 0);   // shock ring at launch
        sound(sl, p.position(), SoundEvents.ANVIL_USE, 0.6f, 1.6f);
        sound(sl, p.position(), SoundEvents.TRIDENT_THROW, 1f, 0.7f);
        flyLance(p, sl, start, dir, power, 0, new HashSet<>(), ids);
    }

    private static void flyLance(ServerPlayer p, ServerLevel sl, Vec3 pos, Vec3 dir, float dmg, int step, Set<UUID> hit, int[] ids) {
        if (!alive(p) || step > 16) return;
        Vec3 next = pos.add(dir.scale(1.7));
        Vec3 end = clip(sl, p, pos, next);
        boolean blocked = end.distanceToSqr(next) > 1.0E-4;
        Vec3 tip = end.add(dir.scale(2.4));
        FxSend.beam(sl, SV, end.subtract(dir.scale(1.2)), end.add(dir.scale(1.2)), 0.1f, 3);   // the air it tears through
        if (step % 2 == 0) FxLib.spiral(sl, SV, end, dir, 0.3f, step * 1.2);
        for (LivingEntity t : enemies(sl, p, new AABB(pos, tip).inflate(0.9))) {
            if (!t.getBoundingBox().inflate(0.4).clip(pos, tip).isPresent() || !hit.add(t.getUUID())) continue;
            AstaFx.damage(p, t, dmg, "violent_rotating_lance");
            FxLib.impact(sl, ST, mid(t), 1.0f);
            FxSend.spike(sl, ST, mid(t).subtract(dir.scale(0.2)), dir, 0.9f, 0.1f, 6, 1, POP);   // steel bursting out the far side
            sound(sl, t.position(), SoundEvents.ANVIL_LAND, 0.4f, 1.8f);
        }
        if (blocked) {
            killAll(sl, ids, end);
            Vec3 wallHit = clip(sl, p, pos, pos.add(dir.scale(4.1)));
            lance3d(sl, wallHit.subtract(dir.scale(1.6)), dir, 2.4f, 0.075f, 30, 0, 0f, Vec3.ZERO, 0);   // stuck in the wall
            FxLib.spikeBurst(sl, ST, wallHit, 0.8f);
            FxLib.shockRing(sl, SV, wallHit, 0.6f);
            sound(sl, end, SoundEvents.ANVIL_LAND, 0.6f, 1.2f);
            return;
        }
        AstaFx.later(1, () -> flyLance(p, sl, next, dir, dmg, step + 1, hit, ids));
    }

    // ================================================================== 2. Blazing Spiral Lance
    /** The lance materialises and spins up in a fire spiral, then thrusts out in an instant and drills into one target. */
    public static void spiralLance(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 h0 = handPos(p), d0 = p.getLookAngle();
        lance3d(sl, h0, d0, 1.8f, 0.07f, 9, 6, 28f, Vec3.ZERO, 0);   // forming in the hand, spinning faster
        FxLib.implode(sl, FI, h0.add(d0.scale(1.0)), 0.6f);
        sound(sl, p.position(), SoundEvents.FIRECHARGE_USE, 0.8f, 0.6f);
        AstaFx.later(8, () -> {
            if (!alive(p)) return;
            Vec3 a = handPos(p), d = p.getLookAngle();
            Vec3 end = clip(sl, p, p.getEyePosition(), p.getEyePosition().add(d.scale(10)));
            LivingEntity first = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity t : enemies(sl, p, new AABB(a, end).inflate(1.2))) {
                if (!t.getBoundingBox().inflate(0.6).clip(p.getEyePosition(), end).isPresent()) continue;
                double dd = t.distanceToSqr(p);
                if (dd < best) { best = dd; first = t; }
            }
            Vec3 tip = first != null ? mid(first) : end;
            float L = (float) Math.max(1.5, a.distanceTo(tip));
            Vec3 up = perp(d);
            // the thrust: the shaft shoots out to full length in two ticks with the drill tip riding its front
            FxSend.box(sl, ST, a.add(d.scale(L / 2)), d, up, L / 2, 0.085f, 0.085f, 12, 2, EXTEND, 60f, Vec3.ZERO, 0);
            FxSend.spike(sl, GD, a, d, 1.1f, 0.22f, 12, 1, POP, 60f, d.scale((L - 1.1f) / 2f), 2);
            FxLib.spiral(sl, FI, a, d, L / 5.0f, 0);
            FxLib.spiral(sl, GD, a, d, L / 5.0f, Math.PI);
            FxSend.wall(sl, FI, a.add(d.scale(0.5)), FxLib.right(d), FxLib.up(d), 0.2f, 1.6f, -180, 180, 0.08f, 7, 0, 0);
            if (first != null) {
                AstaFx.damage(p, first, power, "blazing_spiral_lance");
                TimedModifiers.apply(first, Attributes.MOVEMENT_SPEED, TimedModifiers.SPIRAL_STAGGER, "Blazing Spiral Lance", -0.6, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
                first.knockback(1.2, -d.x, -d.z);
                FxLib.impact(sl, FI, tip, 1.4f);
                FxLib.starburst(sl, GD, tip, d, 0.9f);
                FxSend.wall(sl, FI, tip, FxLib.right(d), FxLib.up(d), 0.3f, 2.2f, -180, 180, 0.12f, 9, 0, 0);
            }
            sound(sl, p.position(), SoundEvents.TRIDENT_RIPTIDE_2, 1f, 0.8f);
            sound(sl, tip, SoundEvents.ANVIL_LAND, 0.7f, 0.9f);
        });
    }

    // ================================================================== 3. Steel Castle's Armored Wall
    /** Five steel slabs slam up out of the ground one after another, then a gold-trimmed battlement locks on top. */
    public static void armoredWall(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 n = flat(p.getLookAngle());
        Vec3 c0 = p.position().add(n.scale(2.2));
        Vec3 c = new Vec3(c0.x, floorY(sl, c0.x, p.getY(), c0.z), c0.z);
        WALLS.put(p.getUUID(), new Wall(c, n, sl.getGameTime() + 160));
        Vec3 r = FxLib.right(n);
        for (int i = -2; i <= 2; i++) {
            final int k = i, delay = Math.abs(i);
            AstaFx.later(delay, () -> {
                Vec3 b = c.add(r.scale(k * 0.86));
                FxSend.box(sl, ST, b.add(0, 1.5, 0), r, UP, 0.42f, 0.17f, 1.5f, 160 - delay, 4, GROUND);
                FxSend.box(sl, ST, b.add(n.scale(0.19)).add(0, 1.5, 0), UP, n, 1.35f, 0.05f, 0.025f, 156 - delay, 7, POP);   // raised rib down the slab
                FxLib.groundPuff(sl, ST, b, 0.9f);
                sound(sl, b, SoundEvents.ANVIL_PLACE, 0.5f, 0.55f + delay * 0.08f);
            });
        }
        AstaFx.later(5, () -> {
            FxSend.box(sl, GD, c.add(0, 3.06, 0), r, UP, 2.2f, 0.23f, 0.07f, 155, 3, POP);   // gold trim
            for (int j = -2; j <= 2; j += 2) {                                                  // battlements
                FxSend.box(sl, ST, c.add(r.scale(j * 0.86)).add(0, 3.38, 0), r, UP, 0.3f, 0.18f, 0.26f, 154, 3, POP);
            }
            // the castle crest: a gold diamond on the front
            FxSend.box(sl, GD, c.add(n.scale(0.24)).add(0, 1.9, 0), r.add(UP).normalize(), n, 0.3f, 0.3f, 0.04f, 154, 4, POP);
            sound(sl, c, SoundEvents.IRON_DOOR_CLOSE, 1f, 0.6f);
        });
        FxLib.shockRing(sl, ST, c.add(0, 0.05, 0), 1.2f);
        sound(sl, c, SoundEvents.ANVIL_PLACE, 1f, 0.6f);
    }

    // ================================================================== 4. White Steel Horse Cart
    /** A white steel cart pulled by a steel horse forms around you and charges forward; allies close by ride in its wake. */
    public static void horseCart(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 d = flat(p.getLookAngle());
        for (ServerPlayer ally : sl.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(4), x -> x != p && x.isAlliedTo(p))) {
            TimedModifiers.apply(ally, Attributes.MOVEMENT_SPEED, TimedModifiers.CART_RIDE, "White Steel Horse Cart", 0.5, AttributeModifier.Operation.MULTIPLY_TOTAL, 60);
            FxLib.halo(sl, SV, ally.position().add(0, ally.getBbHeight() + 0.2, 0), 1f);
        }
        int[] ids = buildCart(sl, p.position(), d, 12);
        sound(sl, p.position(), SoundEvents.HORSE_GALLOP, 1.2f, 0.7f);
        sound(sl, p.position(), SoundEvents.ANVIL_USE, 0.5f, 0.8f);
        sound(sl, p.position(), SoundEvents.HORSE_ANGRY, 0.8f, 0.6f);
        cartTick(p, sl, d, power, 0, new HashSet<>(), ids);
    }

    /** The cart and horse, all moving forward at the charge speed for {@code ticks}. */
    private static int[] buildCart(ServerLevel sl, Vec3 feet, Vec3 d, int ticks) {
        Vec3 r = FxLib.right(d), v = d.scale(1.15);
        int life = ticks + 2, rise = 2;
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        // cart body
        ids.add(FxSend.box(sl, WHITE, feet.add(0, 0.55, 0), d, UP, 1.0f, 0.7f, 0.08f, life, rise, POP, 0, v, ticks));
        for (int s = -1; s <= 1; s += 2) {
            ids.add(FxSend.box(sl, WHITE, feet.add(r.scale(0.68 * s)).add(0, 0.85, 0), d, UP, 1.0f, 0.05f, 0.24f, life, rise, POP, 0, v, ticks));
            ids.add(FxSend.box(sl, GD, feet.add(r.scale(0.68 * s)).add(0, 1.11, 0), d, UP, 1.02f, 0.07f, 0.03f, life, rise, POP, 0, v, ticks));
        }
        ids.add(FxSend.box(sl, WHITE, feet.add(d.scale(-1.0)).add(0, 0.85, 0), r, UP, 0.7f, 0.05f, 0.24f, life, rise, POP, 0, v, ticks));
        ids.add(FxSend.box(sl, WHITE, feet.add(d.scale(1.0)).add(0, 0.95, 0), r, UP, 0.7f, 0.06f, 0.34f, life, rise, POP, 0, v, ticks));
        ids.add(FxSend.box(sl, GD, feet.add(d.scale(1.03)).add(0, 1.0, 0), r.add(UP).normalize(), d, 0.18f, 0.18f, 0.03f, life, rise, POP, 0, v, ticks));   // crest
        // wheels: a steel rim and two spinning spokes each
        for (int a = -1; a <= 1; a += 2) for (int s = -1; s <= 1; s += 2) {
            Vec3 w = feet.add(d.scale(0.62 * a)).add(r.scale(0.8 * s)).add(0, 0.42, 0);
            FxSend.wall(sl, WHITE, w, d, UP, 0.4f, 0.4f, -180, 180, 0.07f, life, 0, 1.15f);
            ids.add(FxSend.box(sl, ST, w, r, UP, 0.04f, 0.37f, 0.03f, life, rise, POP, 70f, v, ticks));
            ids.add(FxSend.box(sl, ST, w, r, d, 0.04f, 0.37f, 0.03f, life, rise, POP, 70f, v, ticks));
        }
        // shafts to the horse
        for (int s = -1; s <= 1; s += 2) {
            ids.add(FxSend.box(sl, GD, feet.add(d.scale(1.45)).add(r.scale(0.3 * s)).add(0, 0.85, 0), d, UP, 0.45f, 0.025f, 0.025f, life, rise, POP, 0, v, ticks));
        }
        // the steel horse
        Vec3 h = feet.add(d.scale(2.3));
        ids.add(FxSend.box(sl, WHITE, h.add(0, 1.1, 0), d, UP, 0.55f, 0.22f, 0.25f, life, rise, POP, 0, v, ticks));                   // body
        Vec3 neckAx = d.add(UP.scale(1.2)).normalize();
        ids.add(FxSend.box(sl, WHITE, h.add(d.scale(0.58)).add(0, 1.5, 0), neckAx, r.cross(neckAx), 0.32f, 0.13f, 0.12f, life, rise, POP, 0, v, ticks));
        Vec3 headAx = d.add(UP.scale(-0.6)).normalize();
        ids.add(FxSend.box(sl, WHITE, h.add(d.scale(0.88)).add(0, 1.8, 0), headAx, r.cross(headAx), 0.24f, 0.11f, 0.1f, life, rise, POP, 0, v, ticks));
        ids.add(FxSend.spike(sl, GD, h.add(d.scale(0.8)).add(0, 1.95, 0), d.scale(-0.3).add(UP).normalize(), 0.22f, 0.05f, life, rise, POP, 0, v, ticks));   // ear / plume
        for (int a = -1; a <= 1; a += 2) for (int s = -1; s <= 1; s += 2) {
            ids.add(FxSend.box(sl, WHITE, h.add(d.scale(0.4 * a)).add(r.scale(0.14 * s)).add(0, 0.45, 0), UP, d, 0.45f, 0.06f, 0.06f, life, rise, POP, 0, v, ticks));   // legs
        }
        ids.add(FxSend.spike(sl, ST, h.add(d.scale(-0.55)).add(0, 1.2, 0), d.scale(-1).add(UP.scale(-0.8)).normalize(), 0.6f, 0.07f, life, rise, POP, 0, v, ticks));   // tail
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void cartTick(ServerPlayer p, ServerLevel sl, Vec3 d, float dmg, int step, Set<UUID> hit, int[] ids) {
        if (!alive(p) || step >= 12 || (step > 0 && p.horizontalCollision)) {
            if (alive(p)) {
                p.setDeltaMovement(d.scale(0.2));
                p.hurtMarked = true;
                FxLib.groundPuff(sl, ST, p.position(), 2.0f);
            }
            if (step < 12) killAll(sl, ids, p.position());
            return;
        }
        p.setDeltaMovement(d.x * 1.15, p.getDeltaMovement().y, d.z * 1.15);
        p.hurtMarked = true;
        p.resetFallDistance();
        Vec3 r = FxLib.right(d);
        if (step % 3 == 0) FxLib.groundPuff(sl, SV, p.position().add(d.scale(-1.2)), 0.8f);   // dust kicked up behind
        for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(1.2, 0.3, 1.2).expandTowards(d.scale(3.4)))) {
            if (!hit.add(t.getUUID())) continue;
            AstaFx.damage(p, t, dmg, "white_steel_horse_cart");
            Vec3 side = t.position().subtract(p.position()).multiply(1, 0, 1);
            Vec3 k = side.lengthSqr() < 1.0E-4 ? r : side.normalize();
            t.knockback(1.3, -k.x, -k.z);
            t.push(0, 0.35, 0);
            FxLib.impact(sl, ST, mid(t), 1.0f);
            sound(sl, t.position(), SoundEvents.ANVIL_LAND, 0.4f, 1.4f);
        }
        if (step % 4 == 0) sound(sl, p.position(), SoundEvents.HORSE_STEP, 1f, 0.6f);
        AstaFx.later(1, () -> cartTick(p, sl, d, dmg, step + 1, hit, ids));
    }

    // ================================================================== 5. Thunder Arrow (steel railgun)
    /** Two steel rails form in front of you and charge through crackling coils for 1 s, then fire a steel bolt. */
    public static void thunderArrow(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        sound(sl, p.position(), SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.6f);
        for (int k = 0; k < 5; k++) {
            final int kk = k;
            AstaFx.later(k * 4, () -> {
                if (!alive(p)) return;
                Vec3 d = p.getLookAngle(), r = FxLib.right(d), u = FxLib.up(d), a = handPos(p);
                for (int s = -1; s <= 1; s += 2) {
                    FxSend.box(sl, ST, a.add(r.scale(0.28 * s)).add(d.scale(1.3)), d, u, 1.3f, 0.05f, 0.09f, 6, kk == 0 ? 3 : 0, POP);
                }
                FxSend.box(sl, GD, a.add(d.scale(0.1)), r, u, 0.36f, 0.06f, 0.07f, 6, kk == 0 ? 3 : 0, POP);   // breech
                for (int c = 0; c <= kk; c++) {   // coils light up one by one along the rails
                    FxSend.wall(sl, LT, a.add(d.scale(0.5 + c * 0.5)), r, u, 0.4f, 0.4f, -180, 180, 0.04f, 5, 0, 0);
                }
                FxLib.spark(sl, LT, a.add(d.scale(0.6 + kk * 0.45)), 0.5f);
                sound(sl, a, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.25f, 1.6f + kk * 0.1f);
            });
        }
        AstaFx.later(20, () -> {
            if (!alive(p)) return;
            Vec3 d = p.getLookAngle(), r = FxLib.right(d), u = FxLib.up(d), a = handPos(p).add(d.scale(2.6));
            Vec3 end = clip(sl, p, p.getEyePosition(), p.getEyePosition().add(d.scale(48)));
            for (LivingEntity t : enemies(sl, p, new AABB(a, end).inflate(1.0))) {
                if (!t.getBoundingBox().inflate(0.5).clip(p.getEyePosition(), end).isPresent()) continue;
                AstaFx.damage(p, t, power, "thunder_arrow");
                FxLib.impact(sl, LT, mid(t), 1.1f);
            }
            float L = (float) a.distanceTo(end);
            FxSend.box(sl, ST, a.add(d.scale(L / 2)), d, u, L / 2, 0.05f, 0.05f, 9, 2, EXTEND);   // the steel bolt's trail
            FxSend.beam(sl, LT, a, end, 0.4f, 8);
            FxSend.beam(sl, SV, a, end, 0.06f, 6);
            for (float k = 2; k < Math.min(L, 32); k += 5) {   // shock rings along the shot
                FxSend.wall(sl, LT, a.add(d.scale(k)), r, u, 0.2f, 1.2f, -180, 180, 0.05f, 6 + (int) (k / 8), 0, 0);
            }
            FxSend.spike(sl, ST, end.subtract(d.scale(0.8)), d, 1.0f, 0.12f, 40, 0, POP);   // the bolt, buried where it hit
            FxLib.starburst(sl, LT, a, d, 0.8f);
            FxLib.impact(sl, LT, end, 1.6f);
            p.push(-d.x * 0.6, 0.1, -d.z * 0.6);        // recoil
            p.hurtMarked = true;
            sound(sl, p.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.6f);
            sound(sl, end, SoundEvents.ANVIL_LAND, 0.6f, 1.0f);
        });
    }

    // ================================================================== 6. Acier Silva (forest of steel)
    /** A forest of solid steel spikes erupts around you in three widening rings, launching whoever stands in them. */
    public static void acierSilva(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        Set<UUID> hit = new HashSet<>();
        sound(sl, c, SoundEvents.ANVIL_PLACE, 0.8f, 0.5f);
        FxLib.shockRing(sl, ST, c.add(0, 0.05, 0), 1.0f);
        for (int ring = 0; ring < 3; ring++) {
            final double r = 2.5 + ring * 2.5;
            final int rr = ring;
            AstaFx.later(1 + ring * 3, () -> {
                if (!alive(p)) return;
                int n = 8 + (int) (r * 2);
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n + r + (sl.random.nextDouble() - 0.5) * 0.2;
                    double rx = r + (sl.random.nextDouble() - 0.5) * 0.6;
                    double x = c.x + Math.cos(a) * rx, z = c.z + Math.sin(a) * rx;
                    Vec3 b = new Vec3(x, floorY(sl, x, c.y, z) - 0.15, z);
                    Vec3 dir = new Vec3(Math.cos(a) * 0.28, 1, Math.sin(a) * 0.28).add(sl.random.nextDouble() * 0.16 - 0.08, 0, sl.random.nextDouble() * 0.16 - 0.08);
                    float h = 1.7f + (i % 3) * 0.5f + sl.random.nextFloat() * 0.3f;
                    FxSend.spike(sl, i % 4 == 0 ? GD : ST, b, dir, h, 0.2f + h * 0.05f, 34 - rr * 3, 3, GROUND);
                    if (i % 2 == 0) FxSend.spike(sl, ST, b.add(Math.cos(a + 1.6) * 0.3, 0, Math.sin(a + 1.6) * 0.3), dir.add(Math.cos(a + 1.6) * 0.3, 0, Math.sin(a + 1.6) * 0.3), h * 0.55f, 0.13f, 32 - rr * 3, 4, GROUND);
                }
                FxLib.shockRing(sl, ST, c.add(0, 0.05, 0), (float) r * 0.45f);
                for (LivingEntity t : enemies(sl, p, new AABB(c, c).inflate(r + 1.4, 2.0, r + 1.4))) {
                    double dist = Math.sqrt(t.distanceToSqr(c.x, t.getY(), c.z));
                    if (Math.abs(dist - r) > 1.4 || !hit.add(t.getUUID())) continue;
                    AstaFx.damage(p, t, power, "acier_silva");
                    t.push(0, 0.6, 0);
                    t.hurtMarked = true;
                    FxLib.spark(sl, ST, mid(t), 1.0f);
                }
                sound(sl, c, SoundEvents.ANVIL_LAND, 0.5f, 0.7f + (float) r * 0.05f);
            });
        }
    }

    // ================================================================== 7. Steel Prison (addon original)
    /** Solid steel bars shoot up around the enemy you look at and lock it in place, then slam inward and crush. */
    public static void steelPrison(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity target = null;
        double best = Math.cos(Math.toRadians(15));
        for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(24))) {
            Vec3 to = mid(t).subtract(eye);
            double d = to.length();
            if (d > 24 || !p.hasLineOfSight(t)) continue;
            double dot = to.scale(1 / d).dot(look);
            if (dot > best) { best = dot; target = t; }
        }
        if (target == null) {
            p.displayClientMessage(Component.translatable("ui.arcane_addon.no_target").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        final LivingEntity t = target;
        TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, TimedModifiers.PRISON_ROOT, "Steel Prison", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, 60);
        float s = Math.max(1f, t.getBbWidth() * 1.4f);
        Vec3 feet = t.position();
        double R = 0.75 * s;
        float hh = (t.getBbHeight() + 0.7f) / 2f;
        int N = 8;
        for (int i = 0; i < N; i++) {
            final int k = i;
            AstaFx.later(i / 2, () -> {
                double a = Math.PI * 2 * k / N;
                Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                FxSend.box(sl, ST, feet.add(out.scale(R)).add(0, hh, 0), out, UP, 0.07f, 0.07f, hh, 61 - k / 2, 3, GROUND);
            });
        }
        AstaFx.later(5, () -> {   // rings lock the bars together, top and bottom
            for (int i = 0; i < N; i++) {
                double a0 = Math.PI * 2 * i / N, a1 = Math.PI * 2 * (i + 1) / N;
                Vec3 q0 = new Vec3(Math.cos(a0) * R, 0, Math.sin(a0) * R), q1 = new Vec3(Math.cos(a1) * R, 0, Math.sin(a1) * R);
                Vec3 m = q0.add(q1).scale(0.5), tan = q1.subtract(q0);
                float half = (float) tan.length() / 2f;
                FxSend.box(sl, GD, feet.add(m).add(0, hh * 2 - 0.05, 0), tan, UP, half, 0.06f, 0.05f, 56, 3, POP);
                FxSend.box(sl, ST, feet.add(m).add(0, 0.12, 0), tan, UP, half, 0.06f, 0.05f, 56, 3, POP);
            }
            FxSend.spike(sl, GD, feet.add(0, hh * 2 + 0.1, 0), UP, 0.5f, 0.12f, 56, 4, POP);   // finial on the cage roof
            sound(sl, feet, SoundEvents.IRON_DOOR_CLOSE, 1f, 0.5f);
        });
        for (int k = 0; k < 6; k++) {
            AstaFx.later(k * 10, () -> {
                if (!t.isAlive()) return;
                t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
                t.hurtMarked = true;
            });
        }
        AstaFx.later(60, () -> {   // the bars crush shut
            if (!t.isAlive() || !alive(p)) return;
            AstaFx.damage(p, t, power, "steel_prison");
            for (int i = 0; i < N; i++) {
                double a = Math.PI * 2 * i / N;
                Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                FxSend.box(sl, ST, feet.add(out.scale(R)).add(0, hh, 0), out, UP, 0.07f, 0.07f, hh, 9, 0, POP, 0, out.scale(-R * 0.3), 3);
                FxSend.spike(sl, ST, feet.add(out.scale(R)).add(0, hh, 0), out.scale(-1), (float) R * 0.8f, 0.1f, 8, 2, POP);
            }
            FxLib.implode(sl, ST, t.position().add(0, 1, 0), s);
            FxLib.spikeBurst(sl, ST, mid(t), s);
            sound(sl, t.position(), SoundEvents.ANVIL_LAND, 0.9f, 0.6f);
        });
        sound(sl, t.position(), SoundEvents.CHAIN_PLACE, 1f, 0.6f);
        sound(sl, t.position(), SoundEvents.ANVIL_PLACE, 0.6f, 0.8f);
    }

    // ================================================================== 8. Knight's Armor (addon original)
    /** Steel plates fly in from all around and lock onto your body with a clang: heavy armor for 20 s. */
    public static void knightsArmor(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        int dur = 20 * 20;
        ARMOR.put(p.getUUID(), sl.getGameTime() + dur);
        TimedModifiers.apply(p, Attributes.ARMOR, TimedModifiers.KNIGHT_ARMOR, "Knight's Armor", 8, AttributeModifier.Operation.ADDITION, dur);
        TimedModifiers.apply(p, Attributes.ARMOR_TOUGHNESS, TimedModifiers.KNIGHT_TOUGHNESS, "Knight's Armor", 4, AttributeModifier.Operation.ADDITION, dur);
        TimedModifiers.apply(p, Attributes.KNOCKBACK_RESISTANCE, TimedModifiers.KNIGHT_FOOTING, "Knight's Armor", 0.6, AttributeModifier.Operation.ADDITION, dur);
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, TimedModifiers.KNIGHT_WEIGHT, "Knight's Armor", -0.1, AttributeModifier.Operation.MULTIPLY_TOTAL, dur);
        Vec3 f = flat(p.getLookAngle()), r = FxLib.right(f), o = p.position();
        // {offset (right, up, forward), axis, up, half sizes, outward direction}
        plate(sl, ST, o, r, f, 0, 1.15, 0.2, r, UP, 0.26f, 0.04f, 0.3f, f);                          // breastplate
        plate(sl, GD, o, r, f, 0, 1.47, 0.24, r, UP, 0.27f, 0.045f, 0.04f, f.add(UP));              // its gold collar
        plate(sl, ST, o, r, f, 0, 1.15, -0.2, r, UP, 0.26f, 0.04f, 0.3f, f.scale(-1));              // backplate
        plate(sl, ST, o, r, f, 0, 1.78, 0.24, r, UP, 0.2f, 0.03f, 0.06f, f.add(UP));                // visor
        for (int s = -1; s <= 1; s += 2) {
            Vec3 tilt = UP.add(r.scale(0.6 * s)).normalize();
            plate(sl, ST, o, r, f, 0.38 * s, 1.45, 0, f, tilt, 0.18f, 0.2f, 0.05f, r.scale(s).add(UP.scale(0.5)));   // pauldrons
            plate(sl, ST, o, r, f, 0.4 * s, 0.85, 0.05, UP, f, 0.12f, 0.1f, 0.1f, r.scale(s));                        // gauntlets
            plate(sl, ST, o, r, f, 0.13 * s, 0.4, 0.13, r, UP, 0.1f, 0.03f, 0.25f, f.add(r.scale(0.4 * s)));          // greaves
        }
        AstaFx.later(5, () -> {
            if (!alive(p)) return;
            FxLib.flash(sl, ST, p.position().add(0, 1.1, 0), 0.9f);
            FxLib.halo(sl, GD, p.position().add(0, p.getBbHeight() + 0.25, 0), 1.0f);
            FxLib.shockRing(sl, SV, p.position().add(0, 0.05, 0), 0.8f);
            sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_NETHERITE, 1f, 0.8f);
            sound(sl, p.position(), SoundEvents.ANVIL_LAND, 0.4f, 1.5f);
        });
        sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_IRON, 1f, 0.7f);
        sound(sl, p.position(), SoundEvents.ANVIL_USE, 0.6f, 1.2f);
    }

    /** One armor plate flying in from {@code outward} and locking onto the body at the given offset. */
    private static void plate(ServerLevel sl, int pal, Vec3 feet, Vec3 r, Vec3 f, double or, double ou, double of,
                              Vec3 axis, Vec3 up, float hl, float hw, float hh, Vec3 outward) {
        Vec3 dst = feet.add(r.scale(or)).add(UP.scale(ou)).add(f.scale(of));
        Vec3 out = outward.normalize();
        int fly = 5;
        double dist = 1.8;
        FxSend.box(sl, pal, dst.add(out.scale(dist)), axis, up, hl, hw, hh, 10, 2, POP, 0, out.scale(-dist / fly), fly);
    }

    // ================================================================== events
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        Wall w = WALLS.get(p.getUUID());
        if (w != null) {
            if (now > w.until()) {
                WALLS.remove(p.getUUID());
                FxLib.groundPuff(sl, ST, w.center(), 2.0f);
                sound(sl, w.center(), SoundEvents.IRON_DOOR_OPEN, 0.8f, 0.6f);
            }
        }
        Long a = ARMOR.get(p.getUUID());
        if (a != null) {
            if (now > a) ARMOR.remove(p.getUUID());
            else if (now % 40 == 0) FxLib.aura(sl, ST, p.position().add(0, 1, 0), 1.0f);   // faint glint of the plating
        }
    }

    /** The wall stops anything that comes at you from in front of it. */
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Wall w = WALLS.get(p.getUUID());
        if (w == null || p.serverLevel().getGameTime() > w.until()) return;
        Vec3 from = e.getSource().getSourcePosition();
        if (from == null) return;
        Vec3 rel = from.subtract(w.center());
        double ahead = rel.dot(w.normal());
        double side = Math.abs(rel.dot(FxLib.right(w.normal())));
        boolean behindMe = p.position().subtract(w.center()).dot(w.normal()) < 0;
        if (ahead > 0 && side < 3.0 && behindMe) {
            e.setCanceled(true);
            Vec3 hitAt = w.center().add(0, 1.5, 0).add(FxLib.right(w.normal()).scale(Math.max(-2, Math.min(2, rel.dot(FxLib.right(w.normal()))))));
            FxLib.spark(p.serverLevel(), ST, hitAt, 1.0f);
            sound(p.serverLevel(), hitAt, SoundEvents.ANVIL_LAND, 0.4f, 1.6f);
        }
    }

    /** Passive "Unbending Steel": projectiles deal 25% less damage. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && e.getSource().is(DamageTypeTags.IS_PROJECTILE) && wearsSteelGrimoire(p)) {
            e.setAmount(e.getAmount() * 0.75f);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        WALLS.remove(e.getEntity().getUUID());
        ARMOR.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        WALLS.clear();
        ARMOR.clear();
    }
}
