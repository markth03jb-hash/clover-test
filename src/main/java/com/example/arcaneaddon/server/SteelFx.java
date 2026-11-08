package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.item.SteelGrimoireItem;
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
 * Every visual comes from the effect library ({@link FxLib}) in steel and gold.
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

    /** A steel lance drawn along {@code a -> b}: dark steel shaft, gold-trimmed tip, bright edge. */
    private static void lance(ServerLevel sl, Vec3 a, Vec3 b, float width, int life) {
        Vec3 d = b.subtract(a).normalize();
        FxSend.beam(sl, ST, a, b, width, life);
        FxSend.beam(sl, SV, a, b, width * 0.25f, life - 1);
        FxSend.beam(sl, GD, b.subtract(d.scale(0.5)), b.add(d.scale(0.25)), width * 0.7f, life);
    }

    // ================================================================== 1. Violent Rotating Lance
    public static void violentLance(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        FxLib.flash(sl, ST, handPos(p), 1.0f);
        sound(sl, p.position(), SoundEvents.ANVIL_USE, 0.6f, 1.6f);
        sound(sl, p.position(), SoundEvents.TRIDENT_THROW, 1f, 0.7f);
        flyLance(p, sl, handPos(p), p.getLookAngle(), power, 0, new HashSet<>());
    }

    private static void flyLance(ServerPlayer p, ServerLevel sl, Vec3 pos, Vec3 dir, float dmg, int step, Set<UUID> hit) {
        if (!alive(p) || step > 16) return;
        Vec3 next = pos.add(dir.scale(1.7));
        Vec3 end = clip(sl, p, pos, next);
        boolean blocked = end.distanceToSqr(next) > 1.0E-4;
        lance(sl, end.subtract(dir.scale(2.2)), end, 0.24f, 3);
        FxLib.spiral(sl, SV, end.subtract(dir.scale(2.0)), dir, 0.35f, step * 1.2);   // it spins as it flies
        for (LivingEntity t : enemies(sl, p, new AABB(pos, end).inflate(0.9))) {
            if (!t.getBoundingBox().inflate(0.4).clip(pos, end).isPresent() || !hit.add(t.getUUID())) continue;
            AstaFx.damage(p, t, dmg, "violent_rotating_lance");
            FxLib.impact(sl, ST, mid(t), 1.0f);
            sound(sl, t.position(), SoundEvents.ANVIL_LAND, 0.4f, 1.8f);
        }
        if (blocked) {
            FxLib.spikeBurst(sl, ST, end, 0.9f);
            sound(sl, end, SoundEvents.ANVIL_LAND, 0.6f, 1.2f);
            return;
        }
        AstaFx.later(1, () -> flyLance(p, sl, next, dir, dmg, step + 1, hit));
    }

    // ================================================================== 2. Blazing Spiral Lance
    public static void spiralLance(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        FxLib.implode(sl, FI, handPos(p), 0.6f);                   // heat gathers in the lance
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
            lance(sl, a, tip, 0.32f, 8);
            FxLib.spiral(sl, FI, a, d, (float) (a.distanceTo(tip) / 5.0), 0);
            FxLib.spiral(sl, GD, a, d, (float) (a.distanceTo(tip) / 5.0), Math.PI);
            if (first != null) {
                AstaFx.damage(p, first, power, "blazing_spiral_lance");
                TimedModifiers.apply(first, Attributes.MOVEMENT_SPEED, TimedModifiers.SPIRAL_STAGGER, "Blazing Spiral Lance", -0.6, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
                first.knockback(1.2, -d.x, -d.z);
                FxLib.impact(sl, FI, tip, 1.4f);
                FxLib.starburst(sl, GD, tip, d, 0.9f);
            }
            sound(sl, p.position(), SoundEvents.TRIDENT_RIPTIDE_2, 1f, 0.8f);
            sound(sl, tip, SoundEvents.ANVIL_LAND, 0.7f, 0.9f);
        });
    }

    // ================================================================== 3. Steel Castle's Armored Wall
    public static void armoredWall(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 n = flat(p.getLookAngle());
        Vec3 c = p.position().add(n.scale(2.2));
        WALLS.put(p.getUUID(), new Wall(c, n, sl.getGameTime() + 160));
        drawWall(sl, c, n, true);
        FxLib.groundPuff(sl, ST, c, 2.0f);
        sound(sl, c, SoundEvents.ANVIL_PLACE, 1f, 0.6f);
        sound(sl, c, SoundEvents.IRON_DOOR_CLOSE, 1f, 0.6f);
    }

    /** Plates rising out of the ground with gold trim along the top. */
    private static void drawWall(ServerLevel sl, Vec3 c, Vec3 n, boolean rising) {
        Vec3 r = FxLib.right(n);
        int life = rising ? 14 : 12;
        for (int i = -2; i <= 2; i++) {
            Vec3 b = c.add(r.scale(i * 0.85));
            FxSend.beam(sl, ST, b, b.add(0, 3.0, 0), 0.5f, life);
        }
        FxSend.beam(sl, GD, c.add(r.scale(-2.1)).add(0, 3.0, 0), c.add(r.scale(2.1)).add(0, 3.0, 0), 0.12f, life);
        FxSend.beam(sl, SV, c.add(r.scale(-2.1)).add(0, 1.5, 0), c.add(r.scale(2.1)).add(0, 1.5, 0), 0.05f, life);
    }

    // ================================================================== 4. White Steel Horse Cart
    public static void horseCart(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 d = flat(p.getLookAngle());
        // allies close by ride along in the cart's wake
        for (ServerPlayer ally : sl.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(4), x -> x != p && x.isAlliedTo(p))) {
            TimedModifiers.apply(ally, Attributes.MOVEMENT_SPEED, TimedModifiers.CART_RIDE, "White Steel Horse Cart", 0.5, AttributeModifier.Operation.MULTIPLY_TOTAL, 60);
            FxLib.halo(sl, SV, ally.position().add(0, ally.getBbHeight() + 0.2, 0), 1f);
        }
        sound(sl, p.position(), SoundEvents.HORSE_GALLOP, 1.2f, 0.7f);
        sound(sl, p.position(), SoundEvents.ANVIL_USE, 0.5f, 0.8f);
        cartTick(p, sl, d, power, 0, new HashSet<>());
    }

    private static void cartTick(ServerPlayer p, ServerLevel sl, Vec3 d, float dmg, int step, Set<UUID> hit) {
        if (!alive(p) || step >= 12 || (step > 0 && p.horizontalCollision)) {
            if (alive(p)) {
                p.setDeltaMovement(d.scale(0.2));
                p.hurtMarked = true;
                FxLib.groundPuff(sl, ST, p.position(), 2.0f);
            }
            return;
        }
        p.setDeltaMovement(d.x * 1.15, p.getDeltaMovement().y, d.z * 1.15);
        p.hurtMarked = true;
        p.resetFallDistance();
        Vec3 r = FxLib.right(d), base = p.position().add(0, 0.5, 0);
        // the cart: a steel frame around you with spinning wheels
        FxSend.beam(sl, ST, base.add(r.scale(0.9)).add(d.scale(-0.9)), base.add(r.scale(0.9)).add(d.scale(1.1)), 0.18f, 3);
        FxSend.beam(sl, ST, base.add(r.scale(-0.9)).add(d.scale(-0.9)), base.add(r.scale(-0.9)).add(d.scale(1.1)), 0.18f, 3);
        FxSend.beam(sl, SV, base.add(r.scale(-0.9)).add(d.scale(1.1)), base.add(r.scale(0.9)).add(d.scale(1.1)), 0.14f, 3);
        if (step % 2 == 0) for (int k = -1; k <= 1; k += 2) {
            Vec3 w = p.position().add(r.scale(k * 1.0)).add(0, 0.45, 0);
            FxSend.wall(sl, SV, w, d, new Vec3(0, 1, 0), 0.4f, 0.45f, -180, 180, 0.1f, 4, 0, 0);
        }
        for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(1.2, 0.3, 1.2).expandTowards(d.scale(1.2)))) {
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
        AstaFx.later(1, () -> cartTick(p, sl, d, dmg, step + 1, hit));
    }

    // ================================================================== 5. Thunder Arrow (steel railgun)
    public static void thunderArrow(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        sound(sl, p.position(), SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.6f);
        for (int k = 0; k < 5; k++) {   // two steel rails form in front of you, crackling as they charge
            final int kk = k;
            AstaFx.later(k * 4, () -> {
                if (!alive(p)) return;
                Vec3 d = p.getLookAngle(), r = FxLib.right(d), a = handPos(p);
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 o = a.add(r.scale(0.28 * s));
                    FxSend.beam(sl, ST, o, o.add(d.scale(2.6)), 0.12f, 5);
                }
                FxLib.spark(sl, LT, a.add(d.scale(0.6 + kk * 0.45)), 0.5f);
                sound(sl, a, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.25f, 1.6f + kk * 0.1f);
            });
        }
        AstaFx.later(20, () -> {
            if (!alive(p)) return;
            Vec3 d = p.getLookAngle(), a = handPos(p).add(d.scale(2.6));
            Vec3 end = clip(sl, p, p.getEyePosition(), p.getEyePosition().add(d.scale(48)));
            for (LivingEntity t : enemies(sl, p, new AABB(a, end).inflate(1.0))) {
                if (!t.getBoundingBox().inflate(0.5).clip(p.getEyePosition(), end).isPresent()) continue;
                AstaFx.damage(p, t, power, "thunder_arrow");
                FxLib.impact(sl, LT, mid(t), 1.1f);
            }
            FxSend.beam(sl, LT, a, end, 0.4f, 8);
            FxSend.beam(sl, ST, a, end, 0.14f, 7);
            FxSend.beam(sl, SV, a, end, 0.04f, 6);
            FxLib.starburst(sl, LT, a, d, 0.8f);
            FxLib.impact(sl, LT, end, 1.6f);
            p.push(-d.x * 0.6, 0.1, -d.z * 0.6);        // recoil
            p.hurtMarked = true;
            sound(sl, p.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.6f);
            sound(sl, end, SoundEvents.ANVIL_LAND, 0.6f, 1.0f);
        });
    }

    // ================================================================== 6. Acier Silva (forest of steel)
    public static void acierSilva(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        Set<UUID> hit = new HashSet<>();
        sound(sl, c, SoundEvents.ANVIL_PLACE, 0.8f, 0.5f);
        for (int ring = 0; ring < 3; ring++) {
            final double r = 2.5 + ring * 2.5;
            AstaFx.later(1 + ring * 3, () -> {
                if (!alive(p)) return;
                int n = 8 + (int) (r * 2);
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n + r;
                    Vec3 b = c.add(Math.cos(a) * r, 0.05, Math.sin(a) * r);
                    double h = 1.6 + (i % 3) * 0.5;
                    FxSend.beam(sl, ST, b, b.add(Math.cos(a) * 0.3, h, Math.sin(a) * 0.3), 0.32f, 14);
                    if (i % 3 == 0) FxSend.beam(sl, GD, b.add(0, h - 0.4, 0), b.add(Math.cos(a) * 0.3, h + 0.1, Math.sin(a) * 0.3), 0.14f, 14);
                }
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
        for (int k = 0; k < 6; k++) {
            final int kk = k;
            AstaFx.later(k * 10, () -> {
                if (!t.isAlive()) return;
                t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
                t.hurtMarked = true;
                FxLib.cage(sl, kk == 0 ? GD : ST, t.position(), s);
            });
        }
        AstaFx.later(60, () -> {   // the bars crush shut
            if (!t.isAlive() || !alive(p)) return;
            AstaFx.damage(p, t, power, "steel_prison");
            FxLib.implode(sl, ST, t.position().add(0, 1, 0), s);
            FxLib.spikeBurst(sl, ST, mid(t), s);
            sound(sl, t.position(), SoundEvents.ANVIL_LAND, 0.9f, 0.6f);
        });
        sound(sl, t.position(), SoundEvents.IRON_DOOR_CLOSE, 1f, 0.5f);
        sound(sl, t.position(), SoundEvents.CHAIN_PLACE, 1f, 0.6f);
    }

    // ================================================================== 8. Knight's Armor (addon original)
    public static void knightsArmor(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        int dur = 20 * 20;
        ARMOR.put(p.getUUID(), sl.getGameTime() + dur);
        TimedModifiers.apply(p, Attributes.ARMOR, TimedModifiers.KNIGHT_ARMOR, "Knight's Armor", 8, AttributeModifier.Operation.ADDITION, dur);
        TimedModifiers.apply(p, Attributes.ARMOR_TOUGHNESS, TimedModifiers.KNIGHT_TOUGHNESS, "Knight's Armor", 4, AttributeModifier.Operation.ADDITION, dur);
        TimedModifiers.apply(p, Attributes.KNOCKBACK_RESISTANCE, TimedModifiers.KNIGHT_FOOTING, "Knight's Armor", 0.6, AttributeModifier.Operation.ADDITION, dur);
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, TimedModifiers.KNIGHT_WEIGHT, "Knight's Armor", -0.1, AttributeModifier.Operation.MULTIPLY_TOTAL, dur);
        FxLib.aura(sl, ST, p.position().add(0, 1, 0), 1.0f);
        FxLib.dome(sl, ST, p.position(), 0.7f);
        FxLib.halo(sl, GD, p.position().add(0, p.getBbHeight() + 0.25, 0), 1.0f);
        sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_IRON, 1f, 0.7f);
        sound(sl, p.position(), SoundEvents.ANVIL_USE, 0.6f, 1.2f);
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
            } else if (now % 10 == 0) drawWall(sl, w.center(), w.normal(), false);
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
