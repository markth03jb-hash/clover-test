package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Swap slashes:
 *  - sword swapped to the Demon Dweller (Slayer in the off hand): a wide HORIZONTAL slash in front of you that leaves a slash aura flying on.
 *  - sword swapped to the Demon Slayer (Dweller in the off hand): the Dweller fires a DARK slash projectile forward.
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public class SlashProjectiles {
    private static final DustParticleOptions BLACK = new DustParticleOptions(new Vector3f(0.04f, 0.0f, 0.07f), 2.0f);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.40f, 0.0f, 0.55f), 1.4f);
    private static final DustParticleOptions CRIMSON = new DustParticleOptions(new Vector3f(0.85f, 0.05f, 0.10f), 1.7f);
    private static final DustParticleOptions BONE = new DustParticleOptions(new Vector3f(0.97f, 0.92f, 0.92f), 1.2f);
    private static final DustColorTransitionOptions EMBER = new DustColorTransitionOptions(new Vector3f(1f, 0.15f, 0.1f), new Vector3f(0.1f, 0f, 0.05f), 1.5f);
    private static final String DMG_ID = "summon_greatsword";

    private enum Kind { DARK, AURA, SWEEP }

    private static class S {
        ServerLevel level; UUID owner; Kind kind; Vec3 pos, dir, left; float damage; int left_, age; double speed; final Set<UUID> hit = new HashSet<>();
    }
    private static final List<S> LIST = new ArrayList<>();

    private static void sound(ServerLevel sl, Vec3 at, SoundEvent s, float v, float p) { sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, v, p); }
    private static void fx(ServerLevel sl, ParticleOptions o, Vec3 c, int n, double sp, double spread) { sl.sendParticles(o, c.x, c.y, c.z, n, spread, spread, spread, sp); }

    // ------------------------------------------------------------------ entry points
    /** Slayer swings horizontally from your right to your left; at the end the slash aura flies on. */
    public static void horizontalSlash(ServerPlayer p, float dmg) {
        ServerLevel sl = p.serverLevel();
        Vec3 look = p.getLookAngle();
        Vec3 f = new Vec3(look.x, 0, look.z).normalize();
        S s = new S();
        s.level = sl; s.owner = p.getUUID(); s.kind = Kind.SWEEP; s.damage = dmg;
        s.dir = f; s.left = new Vec3(f.z, 0, -f.x);
        s.pos = p.position().add(0, p.getEyeHeight() * 0.75, 0).add(f.scale(0.3));
        s.left_ = 4;
        LIST.add(s);
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.55f);
        sound(sl, p.position(), SoundEvents.TRIDENT_RIPTIDE_2, 0.8f, 1.2f);
    }

    /** Dweller fires a dark slash straight ahead. */
    public static void darkShot(ServerPlayer p, float dmg) {
        ServerLevel sl = p.serverLevel();
        S s = new S();
        s.level = sl; s.owner = p.getUUID(); s.kind = Kind.DARK; s.damage = dmg;
        s.dir = p.getLookAngle();
        s.pos = p.getEyePosition().add(s.dir.scale(1.2)).add(0, -0.25, 0);
        s.speed = 1.5; s.left_ = 16;
        LIST.add(s);
        fx(sl, ParticleTypes.FLASH, s.pos, 1, 0, 0);
        fx(sl, ParticleTypes.LARGE_SMOKE, s.pos, 14, 0.08, 0.3);
        fx(sl, ParticleTypes.SOUL_FIRE_FLAME, s.pos, 14, 0.08, 0.3);
        sound(sl, p.position(), SoundEvents.WITHER_SHOOT, 0.7f, 1.4f);
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
        sound(sl, p.position(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.4f, 1.8f);
    }

    // ------------------------------------------------------------------ crescent shape
    /** Draws a crescent bulging along dir, in the plane spanned by (a, b) rotated by tilt. */
    private static void crescent(ServerLevel sl, Vec3 c, Vec3 dir, Vec3 a, Vec3 b, double tilt, double r, ParticleOptions outer, ParticleOptions inner, ParticleOptions spark, int pts) {
        Vec3 u = a.scale(Math.cos(tilt)).add(b.scale(Math.sin(tilt)));
        for (int i = 0; i <= pts; i++) {
            double phi = Math.toRadians(-78 + 156.0 * i / pts);
            Vec3 pt = c.add(u.scale(Math.sin(phi) * r)).add(dir.scale((Math.cos(phi) - 1) * r * 0.55));
            fx(sl, outer, pt, 1, 0, 0.02);
            fx(sl, inner, pt.add(dir.scale(-0.18)), 1, 0, 0.02);
            if (spark != null && i % 5 == 0) fx(sl, spark, pt, 1, 0.02, 0.03);
        }
    }

    // ------------------------------------------------------------------ ticking
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || LIST.isEmpty()) return;
        for (Iterator<S> it = LIST.iterator(); it.hasNext(); ) {
            S s = it.next();
            ServerPlayer owner = s.level.getServer().getPlayerList().getPlayer(s.owner);
            if (owner == null || !owner.isAlive()) { it.remove(); continue; }
            boolean done = s.kind == Kind.SWEEP ? tickSweep(s, owner) : tickProjectile(s, owner);
            if (done) it.remove();
        }
    }

    private static void hurt(S s, ServerPlayer owner, LivingEntity t, float dmg) {
        AstaFx.damage(owner, t, dmg, DMG_ID);
    }

    private static boolean tickSweep(S s, ServerPlayer owner) {
        ServerLevel sl = s.level;
        Vec3 c = s.pos;                       // fixed centre of the swing
        Vec3 f = s.dir, l = s.left;
        if (s.age == 0) {                      // the blow lands on the first tick
            for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(4.2, 2.2, 4.2),
                    x -> x != owner && x.isAlive() && !x.isAlliedTo(owner))) {
                Vec3 to = t.position().subtract(owner.position()).multiply(1, 0, 1);
                double dist = to.length();
                if (dist > 4.6) continue;
                if (dist > 0.5 && to.normalize().dot(f) < Math.cos(Math.toRadians(105))) continue;
                hurt(s, owner, t, s.damage);
                Vec3 k = dist > 0.01 ? to.normalize() : f;
                t.knockback(1.0, -k.x, -k.z);
                Vec3 m = t.position().add(0, t.getBbHeight() * 0.5, 0);
                fx(sl, ParticleTypes.CRIT, m, 22, 0.5, 0.4);
                fx(sl, CRIMSON, m, 18, 0.12, 0.4);
                fx(sl, ParticleTypes.SWEEP_ATTACK, m, 1, 0, 0);
                sound(sl, m, SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.7f);
            }
        }
        // the swing: from -105 (right) to +105 (left) deg over four ticks, three layers of colour
        double a0 = -105 + 52.5 * s.age, a1 = a0 + 52.5;
        for (int i = 0; i <= 14; i++) {
            double th = Math.toRadians(a0 + (a1 - a0) * i / 14.0);
            double sn = Math.sin(th), cs = Math.cos(th);
            for (double r = 1.3; r <= 3.7; r += 0.6) {
                Vec3 pt = c.add(f.scale(cs * r)).add(l.scale(sn * r));
                ParticleOptions o = r > 3.2 ? EMBER : r > 2.5 ? BONE : r > 1.8 ? BLACK : CRIMSON;
                fx(sl, o, pt, 1, 0, 0.03);
            }
            if (i % 5 == 0) {
                Vec3 tip = c.add(f.scale(cs * 3.7)).add(l.scale(sn * 3.7));
                fx(sl, ParticleTypes.END_ROD, tip, 1, 0.03, 0.04);
                fx(sl, ParticleTypes.SOUL_FIRE_FLAME, tip, 1, 0.02, 0.05);
            }
        }
        double mid = Math.toRadians((a0 + a1) / 2);
        fx(sl, ParticleTypes.SWEEP_ATTACK, c.add(f.scale(Math.cos(mid) * 3.0)).add(l.scale(Math.sin(mid) * 3.0)), 1, 0, 0);
        if (s.age == 2) {                      // the slash aura leaves the blade and flies on
            S a = new S();
            a.level = sl; a.owner = s.owner; a.kind = Kind.AURA; a.damage = s.damage * 0.6f;
            a.dir = f; a.left = l; a.pos = c.add(f.scale(2.6)); a.speed = 1.35; a.left_ = 7;
            LIST.add(a);
            sound(sl, c, SoundEvents.ENDER_DRAGON_FLAP, 0.8f, 1.6f);
        }
        return ++s.age >= s.left_;
    }

    private static boolean tickProjectile(S s, ServerPlayer owner) {
        ServerLevel sl = s.level;
        boolean dark = s.kind == Kind.DARK;
        Vec3 next = s.pos.add(s.dir.scale(s.speed));
        var clip = sl.clip(new ClipContext(s.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        boolean blocked = clip.getType() != HitResult.Type.MISS;
        if (blocked) next = clip.getLocation();
        double reach = (dark ? 1.4 : 1.7) + s.age * 0.05;
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(s.pos, next).inflate(reach),
                x -> x != owner && x.isAlive() && !x.isAlliedTo(owner))) {
            if (!s.hit.add(t.getUUID())) continue;          // pierces: everything is hit once
            hurt(s, owner, t, s.damage);
            Vec3 m = t.position().add(0, t.getBbHeight() * 0.5, 0);
            if (dark) {
                t.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
                fx(sl, BLACK, m, 30, 0.15, 0.4);
                fx(sl, VIOLET, m, 20, 0.12, 0.4);
                fx(sl, ParticleTypes.SOUL, m, 8, 0.05, 0.3);
                fx(sl, ParticleTypes.SCULK_SOUL, m, 8, 0.1, 0.3);
            } else {
                fx(sl, CRIMSON, m, 20, 0.12, 0.4);
            }
            fx(sl, ParticleTypes.CRIT, m, 20, 0.5, 0.4);
            fx(sl, ParticleTypes.EXPLOSION, m, 1, 0, 0);
            sound(sl, m, SoundEvents.PLAYER_ATTACK_STRONG, 1f, dark ? 0.5f : 0.8f);
        }
        // look: a growing crescent with a smoky trail
        Vec3 right = s.dir.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(s.dir).normalize();
        double r = (dark ? 1.4 : 1.9) + s.age * 0.07;
        if (dark) {
            crescent(sl, next, s.dir, right, up, Math.toRadians(25), r, BLACK, VIOLET, ParticleTypes.SOUL_FIRE_FLAME, 20);
            crescent(sl, next.add(s.dir.scale(-0.7)), s.dir, right, up, Math.toRadians(25), r * 0.8, BLACK, BLACK, null, 12);
            fx(sl, ParticleTypes.LARGE_SMOKE, next, 2, 0.02, 0.25);
            fx(sl, ParticleTypes.SQUID_INK, next, 1, 0.01, 0.2);
            if (s.age % 2 == 0) fx(sl, ParticleTypes.ENCHANT, next, 2, 0.4, 0.5);
        } else {
            crescent(sl, next, s.dir, s.left, up, 0, r, EMBER, BONE, ParticleTypes.SOUL_FIRE_FLAME, 20);
            crescent(sl, next.add(s.dir.scale(-0.6)), s.dir, s.left, up, 0, r * 0.75, CRIMSON, CRIMSON, null, 12);
            fx(sl, ParticleTypes.END_ROD, next, 1, 0.02, 0.2);
        }
        if (s.age % 3 == 0) sound(sl, next, dark ? SoundEvents.SOUL_ESCAPE : SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, dark ? 0.8f : 1.5f);
        s.pos = next;
        s.age++;
        if (blocked || --s.left_ <= 0) {
            // dissipate
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2 / 24;
                sl.sendParticles(dark ? BLACK : CRIMSON, next.x, next.y, next.z, 0, Math.cos(a), 0.05, Math.sin(a), 0.25);
            }
            fx(sl, dark ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CLOUD, next, 14, 0.1, 0.5);
            if (dark) sound(sl, next, SoundEvents.WITHER_BREAK_BLOCK, 0.5f, 1.5f);
            return true;
        }
        return false;
    }
}
