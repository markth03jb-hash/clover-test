package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
    private static final String DMG_ID = "summon_greatsword";

    private enum Kind { DARK, AURA, SWEEP }

    private static class S {
        ServerLevel level; UUID owner; Kind kind; Vec3 pos, dir, left; int fxId; float damage; int left_, age; double speed; boolean full; final Set<UUID> hit = new HashSet<>();
    }
    private static final List<S> LIST = new ArrayList<>();

    private static void sound(ServerLevel sl, Vec3 at, SoundEvent s, float v, float p) { sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, v, p); }

    // ------------------------------------------------------------------ entry points
    /** Left of {@code f}, kept level with the horizon even when {@code f} points up or down. */
    private static Vec3 leftOf(Vec3 f) {
        Vec3 l = new Vec3(0, 1, 0).cross(f);
        return l.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : l.normalize();
    }

    /**
     * Slayer swings from your right to your left along where you aim (up or down too); at the end a huge crescent of
     * slash flies on in the same direction.
     */
    public static void horizontalSlash(ServerPlayer p, float dmg) {
        ServerLevel sl = p.serverLevel();
        Vec3 f = p.getLookAngle().normalize();
        S s = new S();
        s.level = sl; s.owner = p.getUUID(); s.kind = Kind.SWEEP; s.damage = dmg;
        s.dir = f; s.left = leftOf(f);
        s.pos = p.getEyePosition().add(0, -0.35, 0).add(f.scale(0.3));
        s.left_ = 4;
        LIST.add(s);
        FxSend.wall(sl, 0, s.pos, f, s.left, 3.4f, 3.4f, -105, 105, 1.1f, 14, 4, 0);   // the swing as a real 3D slash wall
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.55f);
        sound(sl, p.position(), SoundEvents.TRIDENT_RIPTIDE_2, 0.8f, 1.2f);
    }

    /** Slayer Whirlwind: a full 360 degree spin cut around you, then the slash aura flies on ahead. */
    public static void whirlSlash(ServerPlayer p, float dmg) {
        ServerLevel sl = p.serverLevel();
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        S s = new S();
        s.level = sl; s.owner = p.getUUID(); s.kind = Kind.SWEEP; s.damage = dmg; s.full = true;
        s.dir = look.normalize(); s.left = leftOf(look);   // the spin is level, the aura that leaves it flies where you aim
        Vec3 f = flat;
        s.pos = p.position().add(0, p.getEyeHeight() * 0.75, 0);
        s.left_ = 4;
        LIST.add(s);
        // two counter-rotating slash rings: crimson Slayer cut and a silver trail
        FxSend.wall(sl, 0, s.pos, f, s.left, 3.6f, 3.6f, -180, 180, 1.2f, 14, 5, 0);
        FxSend.wall(sl, 6, s.pos.add(0, 0.25, 0), f, s.left, 3.0f, 3.2f, 180, -180, 0.5f, 10, 4, 0);
        FxSend.ring(sl, 0, p.position().add(0, 0.05, 0), 0.4f, 5.0f, 0.2f, 10);
        FxSend.ring(sl, Fx.SILVER, s.pos, 3.4f, 3.8f, 0.08f, 6);   // the edge of the spin
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.45f);
        sound(sl, p.position(), SoundEvents.TRIDENT_RIPTIDE_3, 0.9f, 1.1f);
    }

    /** Dweller fires a dark slash straight ahead. */
    public static void darkShot(ServerPlayer p, float dmg) { darkShot(p, dmg, 0); }

    /** Dweller fires a dark slash, turned {@code yawOffsetDeg} degrees left/right of where you look (used for the volley fan). */
    public static void darkShot(ServerPlayer p, float dmg, float yawOffsetDeg) {
        ServerLevel sl = p.serverLevel();
        S s = new S();
        s.level = sl; s.owner = p.getUUID(); s.kind = Kind.DARK; s.damage = dmg;
        s.dir = p.getLookAngle().yRot((float) Math.toRadians(yawOffsetDeg));
        s.pos = p.getEyePosition().add(s.dir.scale(1.2)).add(0, -0.25, 0);
        s.speed = 1.5; s.left_ = 16;
        LIST.add(s);
        Vec3 right = s.dir.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 upv = right.cross(s.dir).normalize();
        Vec3 u = right.scale(Math.cos(Math.toRadians(25))).add(upv.scale(Math.sin(Math.toRadians(25))));
        s.fxId = FxSend.crescent(sl, 1, s.pos, s.dir, u, 1.5f, 2.4f, 18, 1.5f);   // dark crescent blade, drawn in 3D on the client
        Fx.flash(sl, s.pos, 0.9f);
        FxSend.ring(sl, Fx.DARK, s.pos, 0.1f, 1.2f, 0.25f, 6);
        sound(sl, p.position(), SoundEvents.WITHER_SHOOT, 0.7f, 1.4f);
        sound(sl, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
        sound(sl, p.position(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.4f, 1.8f);
    }

    // ------------------------------------------------------------------ ticking
    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent e) { LIST.clear(); }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || LIST.isEmpty()) return;
        for (S s : new ArrayList<>(LIST)) {   // copy: ticking a sweep adds its aura to LIST
            ServerPlayer owner = s.level.getServer().getPlayerList().getPlayer(s.owner);
            if (owner == null || !owner.isAlive()) { LIST.remove(s); continue; }
            boolean done = s.kind == Kind.SWEEP ? tickSweep(s, owner) : tickProjectile(s, owner);
            if (done) LIST.remove(s);
        }
    }

    /** Anti magic corrosion: 3 small hits over 3 seconds after a dark slash connects. */
    private static void corrode(ServerPlayer owner, LivingEntity t, float base) {
        float tick = Math.max(1f, base * 0.1f);
        for (int i = 1; i <= 3; i++) {
            AstaFx.later(i * 20, () -> {
                if (!t.isAlive() || t.isRemoved() || !owner.isAlive() || owner.isRemoved()) return;
                AstaFx.damage(owner, t, tick, DMG_ID);
                if (t.level() instanceof ServerLevel sl) {
                    Vec3 m = t.position().add(0, t.getBbHeight() * 0.5, 0);
                    FxSend.wall(sl, Fx.DARK, m, new Vec3(1, 0, 0), new Vec3(0, 0, 1), t.getBbWidth() * 0.6f, t.getBbWidth() * 0.9f, -180, 180, t.getBbHeight() * 0.8f, 6, 0, 0);
                }
            });
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
                Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(c);
                double dist = to.length();
                if (dist > 4.8) continue;
                if (!s.full && dist > 0.8 && to.normalize().dot(f) < Math.cos(Math.toRadians(100))) continue;
                hurt(s, owner, t, s.damage);
                Vec3 k = dist > 0.01 ? to.normalize() : f;
                t.knockback(1.0, -k.x, -k.z);
                Vec3 m = t.position().add(0, t.getBbHeight() * 0.5, 0);
                Fx.impact(sl, Fx.CRIMSON, m, 1.2f);
                sound(sl, m, SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.7f);
            }
        }
        if (s.age == 2) {                      // the slash aura leaves the blade and flies on
            S a = new S();
            a.level = sl; a.owner = s.owner; a.kind = Kind.AURA; a.damage = s.damage * 0.6f;
            a.dir = f; a.left = l; a.pos = c.add(f.scale(1.6)); a.speed = 1.7; a.left_ = 10;
            LIST.add(a);
            // the aura: a huge black-flame crescent, as wide as the swing, flying on where you aimed, with a pale echo
            a.fxId = FxSend.crescent(sl, 0, c.add(f.scale(1.6)), f, l, 3.0f, 4.4f, 11, 1.7f);
            AstaFx.later(2, () -> FxSend.crescent(sl, Fx.SILVER, c.add(f.scale(1.6)), f, l, 2.4f, 3.6f, 8, 1.7f));
            FxSend.wall(sl, 0, c.add(f.scale(1.2)), l, l.cross(f).normalize(), 0.3f, 2.2f, -180, 180, 0.12f, 6, 0, 0);   // shock ring as it leaves
            sound(sl, c, SoundEvents.ENDER_DRAGON_FLAP, 0.8f, 1.6f);
            sound(sl, c, SoundEvents.TRIDENT_RIPTIDE_3, 0.7f, 0.8f);
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
        double reach = dark ? 1.4 + s.age * 0.05 : 2.6 + s.age * 0.15;   // the aura crescent grows as it flies
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(s.pos, next).inflate(reach),
                x -> x != owner && x.isAlive() && !x.isAlliedTo(owner))) {
            if (!s.hit.add(t.getUUID())) continue;          // pierces: everything is hit once
            hurt(s, owner, t, s.damage);
            Vec3 m = t.position().add(0, t.getBbHeight() * 0.5, 0);
            if (dark) corrode(owner, t, s.damage);   // anti magic eats at them for 3 s (custom damage over time, not a status effect)
            Fx.impact(sl, dark ? Fx.DARK : Fx.CRIMSON, m, 1.2f);
            sound(sl, m, SoundEvents.PLAYER_ATTACK_STRONG, 1f, dark ? 0.5f : 0.8f);
        }
        if (dark && s.age % 2 == 0) FxSend.ring(sl, Fx.DARK, next, 0.2f, 0.8f, 0.15f, 5);   // dark wake behind the blade
        if (s.age % 3 == 0) sound(sl, next, dark ? SoundEvents.SOUL_ESCAPE : SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, dark ? 0.8f : 1.5f);
        s.pos = next;
        s.age++;
        if (blocked || --s.left_ <= 0) {
            if (blocked && s.fxId != 0) FxSend.kill(sl, s.fxId, next);
            // dissipate
            FxSend.ring(sl, dark ? Fx.DARK : Fx.CRIMSON, next, 0.2f, 2.4f, 0.3f, 8);
            Fx.spark(sl, Fx.SILVER, next, 0.9f);
            if (dark) sound(sl, next, SoundEvents.WITHER_BREAK_BLOCK, 0.5f, 1.5f);
            return true;
        }
        return false;
    }
}
