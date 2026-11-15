package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.server.ShadowFx;
import com.ironspellgrimoire.server.TimedModifiers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * The Ravener (an original design): the Shadow Grimoire's second exclusive shadow - a tall, lean stalker of black
 * shadow with burning blue eyes, swept horns, long taloned arms, smoke boiling off its back and a hooked tail. Where
 * the Oathbound Knight guards, the Ravener hunts. Its own instincts:
 *  - Pounce: a foe 4-14 blocks away - it leaps the gap and lands on it (1.6x a blow). Every 6 s; at once if the foe
 *    just struck its master.
 *  - Devour: whatever it kills, it feeds on - it heals a quarter of its health.
 *  - Frenzy: hurt below half, it shrieks and goes wild for 8 s - 35% faster and 30% harder blows (every 30 s).
 * Model: geo/shadow_ravener (tools/shadow_models.py), drawn by client/ShadowRavenerRenderer; animations rise, idle,
 * walk, attack, pounce, frenzy.
 */
public class ShadowRavener extends ShadowSoldier implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation RISE = RawAnimation.begin().thenPlay("animation.shadow_ravener.rise");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.shadow_ravener.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.shadow_ravener.walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.shadow_ravener.attack");
    private static final RawAnimation POUNCE = RawAnimation.begin().thenPlay("animation.shadow_ravener.pounce");
    private static final RawAnimation FRENZY = RawAnimation.begin().thenPlay("animation.shadow_ravener.frenzy");
    private static final UUID FRENZY_SPEED = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000011");
    private static final UUID FRENZY_POWER = UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000012");

    private int pounceReady, frenzyReady, leaping;
    private LivingEntity leapAt;

    public ShadowRavener(EntityType<? extends ShadowRavener> type, Level level) { super(type, level); }

    @Override public EntityDimensions getDimensions(Pose pose) { return super.getDimensions(pose).scale(rankScale()); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl) || isRemoved()) return;
        if (pounceReady > 0) pounceReady--;
        if (frenzyReady > 0) frenzyReady--;
        if (leaping > 0) { land(sl); return; }
        LivingEntity t = getTarget();
        Player o = owner();
        boolean guarding = t != null && o != null && o.getLastHurtByMob() == t && o.tickCount - o.getLastHurtByMobTimestamp() < 20;   // it struck its master
        if (t != null && (pounceReady == 0 || guarding) && onGround()) {
            double d = distanceTo(t);
            if (d > 4 && d < 14 && hasLineOfSight(t)) leap(sl, t);
        }
        if (frenzyReady == 0 && getHealth() < getMaxHealth() * 0.5f && t != null) {   // Frenzy
            frenzyReady = 600;
            TimedModifiers.apply(this, Attributes.MOVEMENT_SPEED, FRENZY_SPEED, "Frenzy", 0.35, AttributeModifier.Operation.MULTIPLY_BASE, 160);
            TimedModifiers.apply(this, Attributes.ATTACK_DAMAGE, FRENZY_POWER, "Frenzy", 0.3, AttributeModifier.Operation.MULTIPLY_BASE, 160);
            ShadowFx.ravenerFrenzy(sl, this, 160);
            level().broadcastEntityEvent(this, (byte) 61);
        }
    }

    /** Pounce: up and across in one arc, onto the foe. */
    private void leap(ServerLevel sl, LivingEntity t) {
        Vec3 to = t.position().subtract(position());
        double flat = Math.sqrt(to.x * to.x + to.z * to.z);
        Vec3 dir = new Vec3(to.x / flat, 0, to.z / flat);
        double v = Math.min(1.9, 0.35 + flat * 0.11);
        setDeltaMovement(dir.x * v, 0.55 + Math.max(0, to.y) * 0.08, dir.z * v);
        hasImpulse = true;
        setYRot((float) (Math.atan2(-dir.x, dir.z) * 180 / Math.PI));
        yBodyRot = getYRot();
        leaping = 30;
        leapAt = t;
        pounceReady = 120;
        getNavigation().stop();
        level().broadcastEntityEvent(this, (byte) 60);
        ShadowFx.ravenerLeap(sl, this);
    }

    private void land(ServerLevel sl) {
        leaping--;
        LivingEntity t = leapAt;
        if (t != null && t.isAlive() && distanceToSqr(t) < 2.6 * 2.6) {   // it landed on its prey
            float before = t.getHealth();
            float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.6f;
            boolean hit = t.hurt(source(), dmg * ShadowFx.domainBoost(owner()));
            if (hit) {
                Vec3 push = t.position().subtract(position()).multiply(1, 0, 1);
                if (push.lengthSqr() > 1e-4) t.push(push.normalize().x * 0.5, 0.25, push.normalize().z * 0.5);
                ShadowFx.ravenerLand(sl, t.position(), this);
                ShadowFx.shadowHit(sl, t, rank());
                devour(sl, t, before);
            }
            leaping = 0; leapAt = null;
            return;
        }
        if (leaping == 0 || (leaping < 26 && onGround())) {
            if (onGround()) ShadowFx.ravenerLand(sl, position(), this);
            leaping = 0; leapAt = null;
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        float before = target instanceof LivingEntity le ? le.getHealth() : 0;
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity le && level() instanceof ServerLevel sl) devour(sl, le, before);
        return hit;
    }

    /** Devour: what it kills, it feeds on. */
    private void devour(ServerLevel sl, LivingEntity t, float before) {
        if (t.isAlive() || before <= 0) return;
        heal(getMaxHealth() * 0.25f);
        ShadowFx.ravenerDevour(sl, t.position().add(0, t.getBbHeight() * 0.5, 0), this);
    }

    private net.minecraft.world.damagesource.DamageSource source() {
        return owner() instanceof net.minecraft.server.level.ServerPlayer sp ? damageSources().indirectMagic(this, sp) : damageSources().mobAttack(this);
    }

    /** No fall damage from its own leaps. */
    @Override public boolean causeFallDamage(float dist, float mult, net.minecraft.world.damagesource.DamageSource src) { return false; }

    // ------------------------------------------------------------------ animation
    private int leapAnimAt = -100, frenzyAnimAt = -100;

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 60) leapAnimAt = tickCount;
        else if (id == 61) frenzyAnimAt = tickCount;
        else super.handleEntityEvent(id);
    }

    private PlayState move(AnimationState<ShadowRavener> s) {
        if (tickCount - risingAt() < 36) return s.setAndContinue(RISE);
        return s.setAndContinue(s.isMoving() ? WALK : IDLE);
    }

    private PlayState attack(AnimationState<ShadowRavener> s) {
        var c = s.getController();
        if (tickCount - leapAnimAt < 2) { c.forceAnimationReset(); c.setAnimation(POUNCE); }
        else if (tickCount - frenzyAnimAt < 2) { c.forceAnimationReset(); c.setAnimation(FRENZY); }
        else if (swinging && c.getAnimationState() == AnimationController.State.STOPPED) { c.forceAnimationReset(); c.setAnimation(ATTACK); }
        return PlayState.CONTINUE;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "move", 4, this::move));
        c.add(new AnimationController<>(this, "attack", 0, this::attack));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
