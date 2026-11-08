package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.item.DarkGrimoireItem;
import com.example.arcaneaddon.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
 * Yami Sukehiro's Dark Magic - the ten spells of the Dark Grimoire and its passive.
 *
 *  1 Dark Cloaked Black Blade        - coat your blade in darkness: heavier melee hits that devour the target's mana
 *  2 Dark Cloaked Dimension Slash    - a cut through space itself: instant, 40 blocks, passes through walls
 *  3 Dark Cloaked Avidya Slash Raijin - a ki-guided slash that bends toward the enemy you're looking at
 *  4 Dark Cloaked Avidya Wild Slash   - a ki-guided slash for every enemy around you
 *  5 Dark Cloaked Death Thrust       - a piercing thrust of darkness straight ahead
 *  6 Black Moon                      - a moon of darkness that swallows enemy spells, then hurls them back
 *  7 Black Hole                      - a hole of darkness that drags enemies in and crushes them
 *  8 Mana Zone: Dimension Slash      - inside your mana zone, cuts appear on every enemy, again and again
 *  9 Ki Read: Foresight              - read their ki: the next 3 attacks simply miss
 * 10 Dimension Slash: Event Horizon  - the ultimate cut that splits space open and leaves the horizon behind
 *
 * Passive "Surpass Your Limits": the first time you fall below 25% health (once per 90 s), every spell cooldown resets.
 * Sword techniques (every slash, the thrust, Black Blade, Mana Zone and Event Horizon) need the Dark Katana in the main hand.
 *
 * All visuals use the addon's own 3D shapes (black body, violet rim) and are kept thin so the screen stays readable.
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public class DarkFx {
    private static final int D = Fx.DARK, S = Fx.SILVER;

    // ------------------------------------------------------------------ state
    private static final Map<UUID, Long> BLADE = new HashMap<>();          // Black Blade active until
    private static final Map<UUID, Moon> MOON = new HashMap<>();           // Black Moon
    private static final Map<UUID, Long> ZONE = new HashMap<>();           // Mana Zone: Dimension Slash until
    private static final Map<UUID, Foresight> FORESIGHT = new HashMap<>();
    private static final Map<UUID, Long> LIMITS_USED = new HashMap<>();

    private static final class Moon { long until; float absorbed; float power; }
    private static final class Foresight { long until; int charges; }

    // ------------------------------------------------------------------ helpers
    private static void sound(ServerLevel sl, Vec3 at, SoundEvent s, float v, float pitch) {
        sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, v, pitch);
    }

    private static Vec3 mid(LivingEntity t) { return t.position().add(0, t.getBbHeight() * 0.5, 0); }

    private static Vec3 rightOf(Vec3 dir) {
        Vec3 r = dir.cross(new Vec3(0, 1, 0));
        return r.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : r.normalize();
    }

    private static Vec3 clip(ServerLevel sl, Entity p, Vec3 a, Vec3 b) {
        var hit = sl.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? b : hit.getLocation();
    }

    private static List<LivingEntity> enemies(ServerLevel sl, ServerPlayer p, AABB box) {
        return sl.getEntitiesOfClass(LivingEntity.class, box, x -> x != p && x.isAlive() && !x.isAlliedTo(p));
    }

    private static boolean alive(ServerPlayer p) { return p.isAlive() && !p.isRemoved(); }

    private static boolean wearsDarkGrimoire(ServerPlayer p) {
        ItemStack s = Utils.getPlayerSpellbookStack(p);
        return s != null && s.getItem() instanceof DarkGrimoireItem;
    }

    /** A thin horizontal "cut" drawn across a target: the mark every dark slash leaves. */
    private static void cutMark(ServerLevel sl, Vec3 at, Vec3 across, float half) {
        FxSend.beam(sl, D, at.subtract(across.scale(half)), at.add(across.scale(half)), 0.28f, 8);
        FxSend.beam(sl, S, at.subtract(across.scale(half * 0.8)), at.add(across.scale(half * 0.8)), 0.06f, 6);
    }

    private static Vec3 handPos(ServerPlayer p) {
        double yr = Math.toRadians(p.getYRot());
        double lx = Math.cos(yr), lz = Math.sin(yr), fx = -Math.sin(yr), fz = Math.cos(yr);
        return new Vec3(p.getX() + fx * 0.55 - lx * 0.4, p.getY() + p.getEyeHeight() * 0.72, p.getZ() + fz * 0.55 - lz * 0.4);
    }

    // ================================================================== 1. Black Blade
    public static void blackBlade(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        BLADE.put(p.getUUID(), sl.getGameTime() + 20 * 20);
        Vec3 h = handPos(p);
        FxSend.beam(sl, D, h, h.add(p.getLookAngle().scale(0.4)).add(0, 1.4, 0), 0.3f, 12);
        FxSend.ring(sl, D, p.position().add(0, 0.05, 0), 0.2f, 1.6f, 0.15f, 10);
        sound(sl, p.position(), SoundEvents.SOUL_ESCAPE, 1f, 0.6f);
        sound(sl, p.position(), SoundEvents.ARMOR_EQUIP_NETHERITE, 0.8f, 0.6f);
    }

    // ================================================================== 2. Dimension Slash
    public static void dimensionSlash(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        float dmg = power;
        Vec3 start = p.getEyePosition().add(0, -0.2, 0);
        Vec3 dir = p.getLookAngle();
        Vec3 end = start.add(dir.scale(40));            // cuts through space: walls don't stop it
        Vec3 rt = rightOf(dir);
        for (LivingEntity t : enemies(sl, p, new AABB(start, end).inflate(1.2))) {
            if (!t.getBoundingBox().inflate(0.6).clip(start, end).isPresent()) continue;
            AstaFx.damage(p, t, dmg, "dimension_slash");
            cutMark(sl, mid(t), rt, 1.4f);
            Fx.spark(sl, D, mid(t), 0.9f);
        }
        // a hairline appears... then space opens along it for a moment
        Vec3 from = start.add(dir.scale(1.2));
        FxSend.beam(sl, D, from, end, 0.16f, 10);
        FxSend.beam(sl, S, from, end, 0.04f, 8);
        AstaFx.later(2, () -> FxSend.beam(sl, D, from, end, 0.75f, 7));
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.45f);
        sound(sl, p.position(), SoundEvents.ENDERMAN_TELEPORT, 0.6f, 0.5f);
        AstaFx.later(2, () -> sound(sl, p.position(), SoundEvents.WITHER_SHOOT, 0.4f, 0.5f));
    }

    // ================================================================== 3 + 4. Avidya Slash Raijin (ki-guided)
    public static void avidyaSlash(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity target = null;
        double best = Math.cos(Math.toRadians(25));
        for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(30))) {
            Vec3 to = mid(t).subtract(eye);
            double d = to.length();
            if (d > 30 || !p.hasLineOfSight(t)) continue;
            double dot = to.scale(1 / d).dot(look);
            if (dot > best) { best = dot; target = t; }
        }
        guidedSlash(p, sl, handPos(p), look, target, power, 0, new HashSet<>());
        FxSend.ring(sl, D, handPos(p), 0.1f, 0.9f, 0.15f, 6);
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.3f);
        sound(sl, p.position(), SoundEvents.TRIDENT_RIPTIDE_1, 0.6f, 1.5f);
    }

    public static void wildSlash(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        List<LivingEntity> targets = new ArrayList<>(enemies(sl, p, p.getBoundingBox().inflate(14)));
        targets.removeIf(t -> t.distanceTo(p) > 14 || !p.hasLineOfSight(t));
        targets.sort(Comparator.comparingDouble(t -> t.distanceToSqr(p)));
        float dmg = power;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int n = Math.max(6, Math.min(8, targets.size()));
        for (int i = 0; i < n; i++) {
            final LivingEntity t = i < targets.size() ? targets.get(i) : null;
            // each slash leaves in its own direction, then bends toward its target
            double ang = Math.PI * 2 * i / n + r.nextDouble(-0.3, 0.3);
            final Vec3 heading = new Vec3(Math.cos(ang), r.nextDouble(-0.2, 0.4), Math.sin(ang)).normalize();
            AstaFx.later(1 + i * 2, () -> {
                if (alive(p)) guidedSlash(p, sl, p.getEyePosition().add(0, -0.3, 0), heading, t, dmg, 0, new HashSet<>());
            });
        }
        FxSend.ring(sl, D, p.position().add(0, 0.8, 0), 0.4f, 3.5f, 0.2f, 10);
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.8f);
        sound(sl, p.position(), SoundEvents.ENDER_DRAGON_FLAP, 0.6f, 1.6f);
    }

    /** One ki-guided slash: flies 2.4 blocks a tick, steering toward its target (or straight on), leaving a thin dark trail. */
    private static void guidedSlash(ServerPlayer p, ServerLevel sl, Vec3 pos, Vec3 dir, LivingEntity target, float dmg, int step, Set<UUID> hit) {
        if (!alive(p) || step > 18) return;
        if (target != null && target.isAlive()) {
            Vec3 to = mid(target).subtract(pos);
            dir = dir.scale(0.55).add(to.normalize().scale(0.45)).normalize();   // bend toward the ki
        }
        Vec3 next = pos.add(dir.scale(2.4));
        FxSend.beam(sl, D, pos, next, 0.32f, 6);
        FxSend.beam(sl, S, pos, next, 0.05f, 4);
        for (LivingEntity t : enemies(sl, p, new AABB(pos, next).inflate(1.0))) {
            if (!t.getBoundingBox().inflate(0.5).clip(pos, next).isPresent() && t.distanceToSqr(next) > 1.6) continue;
            if (!hit.add(t.getUUID())) continue;
            AstaFx.damage(p, t, dmg, "avidya_slash");
            cutMark(sl, mid(t), rightOf(dir), 1.1f);
            sound(sl, t.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.8f);
            if (t == target) return;   // it found the ki it was chasing
        }
        if (target == null && clip(sl, p, pos, next).distanceToSqr(next) > 1.0E-4) {   // unguided slashes break on walls
            Fx.spark(sl, D, next, 0.8f);
            return;
        }
        final Vec3 nd = dir;
        AstaFx.later(1, () -> guidedSlash(p, sl, next, nd, target, dmg, step + 1, hit));
    }

    // ================================================================== 5. Death Thrust
    public static void deathThrust(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        float dmg = power;
        Vec3 dir = p.getLookAngle();
        Vec3 from = handPos(p);
        Vec3 end = clip(sl, p, p.getEyePosition(), p.getEyePosition().add(dir.scale(12)));
        for (LivingEntity t : enemies(sl, p, new AABB(from, end).inflate(1.5))) {
            if (!t.getBoundingBox().inflate(0.9).clip(p.getEyePosition(), end).isPresent()) continue;
            AstaFx.damage(p, t, dmg, "death_thrust");
            t.knockback(1.8, -dir.x, -dir.z);
            Fx.impact(sl, D, mid(t), 1.1f);
        }
        Vec3 rt = rightOf(dir), up = rt.cross(dir).normalize();
        FxSend.beam(sl, D, from, end, 0.6f, 9);
        FxSend.beam(sl, S, from, end, 0.1f, 7);
        FxSend.wall(sl, D, from.add(dir.scale(0.6)), rt, up, 0.3f, 1.4f, -180, 180, 0.15f, 7, 0, 0);   // ring punched out around the blade
        AstaFx.later(2, () -> FxSend.wall(sl, S, end, rt, up, 0.2f, 1.2f, -180, 180, 0.1f, 6, 0, 0));
        sound(sl, p.position(), SoundEvents.TRIDENT_RIPTIDE_3, 0.8f, 0.8f);
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.5f);
    }

    // ================================================================== 6. Black Moon
    public static void blackMoon(ServerPlayer p, float power) {
        Moon m = new Moon();
        m.until = p.serverLevel().getGameTime() + 80;
        m.power = power;
        MOON.put(p.getUUID(), m);
        sound(p.serverLevel(), p.position(), SoundEvents.SOUL_ESCAPE, 1.0f, 0.5f);
    }

    private static Vec3 moonPos(ServerPlayer p) { return p.getEyePosition().add(p.getLookAngle().scale(1.8)).add(0, -0.2, 0); }

    private static void drawMoon(ServerLevel sl, Vec3 c, float r) {
        FxSend.wall(sl, D, c, new Vec3(1, 0, 0), new Vec3(0, 1, 0), r * 0.9f, r, -180, 180, r * 0.6f, 6, 0, 0);
        FxSend.wall(sl, D, c, new Vec3(0, 0, 1), new Vec3(0, 1, 0), r * 0.9f, r, -180, 180, r * 0.6f, 6, 0, 0);
        FxSend.ring(sl, S, c, r, r * 1.05f, 0.04f, 6);
    }

    private static void releaseMoon(ServerPlayer p, Moon m) {
        ServerLevel sl = p.serverLevel();
        float dmg = m.power + m.absorbed * 0.5f;
        Vec3 c = moonPos(p), dir = p.getLookAngle();
        Vec3 end = clip(sl, p, c, c.add(dir.scale(22)));
        for (LivingEntity t : enemies(sl, p, new AABB(c, end).inflate(1.8))) {
            if (!t.getBoundingBox().inflate(1.2).clip(c, end).isPresent()) continue;
            AstaFx.damage(p, t, dmg, "black_moon");
            Fx.impact(sl, D, mid(t), 1.2f);
        }
        Vec3 rt = rightOf(dir);
        FxSend.crescent(sl, D, c, dir, rt.cross(dir).normalize(), 1.4f, 2.6f, 10, 2.2f);   // the moon flies out as a dark crescent
        sound(sl, c, SoundEvents.WITHER_SHOOT, 0.7f, 0.7f);
        if (m.absorbed > 0) {
            p.displayClientMessage(Component.translatable("ui.arcane_addon.black_moon_return", (int) m.absorbed).withStyle(ChatFormatting.DARK_PURPLE), true);
        }
    }

    // ================================================================== 7. Black Hole
    public static void blackHole(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        Vec3 hit = clip(sl, p, eye, eye.add(look.scale(18)));
        Vec3 c = hit.subtract(look.scale(0.8)).add(0, 0.6, 0);
        float dmg = power;
        sound(sl, c, SoundEvents.PORTAL_TRIGGER, 0.5f, 1.6f);
        holeTick(p, sl, c, dmg, 0);
    }

    private static void holeTick(ServerPlayer p, ServerLevel sl, Vec3 c, float dmg, int age) {
        if (!alive(p)) return;
        final int LIFE = 60;
        List<LivingEntity> near = enemies(sl, p, new AABB(c, c).inflate(7));
        for (LivingEntity t : near) {
            Vec3 pull = c.subtract(t.position().add(0, t.getBbHeight() * 0.5, 0));
            double d = pull.length();
            if (d > 7 || d < 0.3) continue;
            t.setDeltaMovement(t.getDeltaMovement().scale(0.6).add(pull.scale(0.12 / d * Math.min(1.5, 7 / d))));
            t.hurtMarked = true;
            if (age % 10 == 0) AstaFx.damage(p, t, dmg * 0.25f, "black_hole");
        }
        if (age % 6 == 0) {
            FxSend.ring(sl, D, c, 6f, 0.4f, 0.12f, 8);             // rings collapsing inward
            drawMoon(sl, c, 0.55f);                               // the dark core
        }
        if (age % 20 == 0) sound(sl, c, SoundEvents.BEACON_AMBIENT, 0.8f, 0.5f);
        if (age >= LIFE) {   // implosion
            for (LivingEntity t : enemies(sl, p, new AABB(c, c).inflate(4))) {
                if (t.position().distanceTo(c) > 4.5) continue;
                AstaFx.damage(p, t, dmg * 1.5f, "black_hole");
                t.push(0, 0.6, 0);
                t.hurtMarked = true;
            }
            FxSend.ring(sl, D, c, 0.3f, 5f, 0.3f, 10);
            Fx.impact(sl, S, c, 1.6f);
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 0.9f, 0.6f);
            return;
        }
        AstaFx.later(1, () -> holeTick(p, sl, c, dmg, age + 1));
    }

    // ================================================================== 8. Mana Zone: Dimension Slash
    public static void manaZone(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        ZONE.put(p.getUUID(), sl.getGameTime() + 160);
        FxSend.ring(sl, D, p.position().add(0, 0.05, 0), 0.5f, 12f, 0.2f, 14);
        sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 0.5f);
        sound(sl, p.position(), SoundEvents.BEACON_POWER_SELECT, 0.8f, 0.6f);
        // store the zone's power so the ticking cuts use it
        ZONE_POWER.put(p.getUUID(), power);
    }
    private static final Map<UUID, Float> ZONE_POWER = new HashMap<>();

    private static void zoneTick(ServerPlayer p, ServerLevel sl) {
        float dmg = ZONE_POWER.getOrDefault(p.getUUID(), 12f);
        FxSend.ring(sl, D, p.position().add(0, 0.05, 0), 11.8f, 12f, 0.05f, 20);   // faint edge of the zone
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(12))) {
            if (t.distanceTo(p) > 12) continue;
            double a = r.nextDouble(Math.PI);
            Vec3 across = new Vec3(Math.cos(a), r.nextDouble(-0.4, 0.4), Math.sin(a)).normalize();
            AstaFx.damage(p, t, dmg, "mana_zone_dimension_slash");
            cutMark(sl, mid(t), across, 1.3f);           // the cut simply appears on them
            sound(sl, t.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.5f);
        }
    }

    // ================================================================== 9. Ki Read: Foresight
    public static void foresight(ServerPlayer p, float power) {
        Foresight f = new Foresight();
        f.until = p.serverLevel().getGameTime() + 100;
        f.charges = 3;
        FORESIGHT.put(p.getUUID(), f);
        FxSend.ring(p.serverLevel(), S, p.position().add(0, 0.05, 0), 0.3f, 4f, 0.06f, 10);
        sound(p.serverLevel(), p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 0.6f);
    }

    // ================================================================== 10. Dimension Slash: Event Horizon
    public static void eventHorizon(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final float dmg = power;
        sound(sl, p.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.6f);
        for (int k = 0; k < 5; k++) {   // darkness gathers into the blade
            final int kk = k;
            AstaFx.later(k * 4, () -> {
                if (!alive(p)) return;
                FxSend.ring(sl, D, p.position().add(0, 0.05 + kk * 0.35, 0), 5f - kk * 0.6f, 0.3f, 0.1f, 6);
            });
        }
        AstaFx.later(20, () -> {
            if (!alive(p)) return;
            Vec3 start = p.getEyePosition().add(0, -0.3, 0), dir = p.getLookAngle();
            Vec3 end = start.add(dir.scale(50));
            Vec3 rt = rightOf(dir), up = rt.cross(dir).normalize();
            for (LivingEntity t : enemies(sl, p, new AABB(start, end).inflate(2.5))) {
                if (!t.getBoundingBox().inflate(1.0, 2.5, 1.0).clip(start, end).isPresent()) continue;
                AstaFx.damage(p, t, dmg, "event_horizon");
                Fx.impact(sl, D, mid(t), 1.5f);
            }
            // the horizon: a line through space with tall vertical tears along it, left hanging open
            FxSend.beam(sl, D, start.add(dir), end, 1.4f, 34);
            FxSend.beam(sl, S, start.add(dir), end, 0.12f, 30);
            for (int i = 2; i < 50; i += 4) {
                Vec3 c = start.add(dir.scale(i));
                FxSend.beam(sl, D, c.subtract(up.scale(3)), c.add(up.scale(3)), 0.35f, 22);
            }
            sound(sl, p.position(), SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.5f);
            sound(sl, p.position(), SoundEvents.GENERIC_EXPLODE, 0.8f, 0.5f);
            // the horizon keeps pulling and cutting, then seals with a flash
            for (int k = 1; k <= 3; k++) {
                AstaFx.later(k * 10, () -> {
                    if (!alive(p)) return;
                    for (LivingEntity t : enemies(sl, p, new AABB(start, end).inflate(5))) {
                        Vec3 onLine = closestOnSegment(start, end, mid(t));
                        Vec3 pull = onLine.subtract(mid(t));
                        if (pull.length() > 5) continue;
                        t.setDeltaMovement(t.getDeltaMovement().add(pull.normalize().scale(0.35)));
                        t.hurtMarked = true;
                        AstaFx.damage(p, t, dmg * 0.25f, "event_horizon");
                    }
                    sound(sl, start.add(dir.scale(10)), SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.4f);
                });
            }
            AstaFx.later(40, () -> {
                FxSend.beam(sl, S, start.add(dir), end, 0.9f, 4);
                sound(sl, start, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.4f);
            });
        });
    }

    private static Vec3 closestOnSegment(Vec3 a, Vec3 b, Vec3 q) {
        Vec3 ab = b.subtract(a);
        double t = Math.max(0, Math.min(1, q.subtract(a).dot(ab) / ab.lengthSqr()));
        return a.add(ab.scale(t));
    }

    // ================================================================== events
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        UUID id = p.getUUID();

        Long blade = BLADE.get(id);
        if (blade != null) {
            if (now > blade) BLADE.remove(id);
            else if (now % 8 == 0) {   // a thin black edge along the coated blade
                Vec3 h = handPos(p);
                FxSend.beam(sl, D, h, h.add(p.getLookAngle().scale(0.5)).add(0, 1.1, 0), 0.16f, 9);
            }
        }

        Moon m = MOON.get(id);
        if (m != null) {
            if (now >= m.until) { MOON.remove(id); releaseMoon(p, m); }
            else if (now % 5 == 0) drawMoon(sl, moonPos(p), 0.6f);
        }

        Long z = ZONE.get(id);
        if (z != null) {
            if (now > z) { ZONE.remove(id); ZONE_POWER.remove(id); }
            else if (now % 20 == 0) zoneTick(p, sl);
        }

        Foresight f = FORESIGHT.get(id);
        if (f != null && (now > f.until || f.charges <= 0)) FORESIGHT.remove(id);
    }

    /** Black Blade: heavier melee hits that devour the target's mana. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getSource().getDirectEntity() != p) return;
        if (e.getSource() instanceof SpellDamageSource) return;
        Long blade = BLADE.get(p.getUUID());
        if (blade == null || p.serverLevel().getGameTime() > blade) return;
        e.setAmount(e.getAmount() + 6f);
        LivingEntity t = e.getEntity();
        if (t instanceof ServerPlayer victim) {
            MagicData data = MagicData.getPlayerMagicData(victim);
            data.setMana(Math.max(0, data.getMana() - 20));   // darkness swallows magic
        }
        Fx.spark(p.serverLevel(), D, mid(t), 0.9f);
    }

    /** Black Moon swallows spells; Foresight lets attacks miss. */
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();

        Moon m = MOON.get(p.getUUID());
        if (m != null && now < m.until && e.getSource() instanceof SpellDamageSource) {
            e.setCanceled(true);
            m.absorbed = Math.min(40f, m.absorbed + e.getAmount());
            Fx.spark(sl, D, moonPos(p), 0.7f);
            sound(sl, p.position(), SoundEvents.SCULK_CLICKING, 1f, 0.6f);
            return;
        }

        Foresight f = FORESIGHT.get(p.getUUID());
        Entity src = e.getSource().getEntity();
        if (f != null && now <= f.until && f.charges > 0 && src != null && src != p) {
            e.setCanceled(true);
            f.charges--;
            Vec3 away = p.position().subtract(src.position()).multiply(1, 0, 1);
            Vec3 side = rightOf(away.lengthSqr() < 1.0E-4 ? p.getLookAngle() : away.normalize());
            for (int s = 1; s >= -1; s -= 2) {   // step left or right, whichever is free
                Vec3 dest = p.position().add(side.scale(1.4 * s));
                if (sl.noCollision(p, p.getBoundingBox().move(dest.subtract(p.position())))) {
                    FxSend.beam(sl, S, p.position().add(0, 1, 0), dest.add(0, 1, 0), 0.2f, 6);   // afterimage
                    p.connection.teleport(dest.x, dest.y, dest.z, p.getYRot(), p.getXRot());
                    break;
                }
            }
            AnimSend.play(p, AnimSend.DEFLECT);   // the blade turns the blow aside as you step
            sound(sl, p.position(), SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.8f);
        }
    }

    /** Passive: Surpass Your Limits - falling below 25% health resets every cooldown (once per 90 s). */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !wearsDarkGrimoire(p)) return;
        float after = p.getHealth() - e.getAmount();
        if (after <= 0 || after > p.getMaxHealth() * 0.25f || p.getHealth() <= p.getMaxHealth() * 0.25f) return;
        long now = p.serverLevel().getGameTime();
        Long used = LIMITS_USED.get(p.getUUID());
        if (used != null && now - used < 90 * 20) return;
        LIMITS_USED.put(p.getUUID(), now);
        MagicData data = MagicData.getPlayerMagicData(p);
        data.getPlayerCooldowns().clearCooldowns();
        data.getPlayerCooldowns().syncToPlayer(p);
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, D, p.position().add(0, 0.05, 0), 0.3f, 5f, 0.3f, 12);
        FxSend.wall(sl, D, p.position().add(0, 1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.7f, 1.0f, -180, 180, 1.4f, 12, 0, 0);
        sound(sl, p.position(), SoundEvents.TOTEM_USE, 0.4f, 0.6f);
        p.displayClientMessage(Component.translatable("ui.arcane_addon.surpass_limits").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        BLADE.remove(id); MOON.remove(id); ZONE.remove(id); ZONE_POWER.remove(id); FORESIGHT.remove(id); LIMITS_USED.remove(id);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        BLADE.clear(); MOON.clear(); ZONE.clear(); ZONE_POWER.clear(); FORESIGHT.clear(); LIMITS_USED.clear();
    }
}
