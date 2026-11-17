package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.server.ShadowFx;
import com.ironspellgrimoire.server.TimedModifiers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Oathbound Knight (an original design): the Shadow Grimoire's exclusive shadow - a towering knight of black plate
 * traced with blue, horned and fanged, with a glowing blue greatsword. It joins its master's army at the Knight rank
 * and can rise to General (the army holds only one). Model: geo/shadow_knight (tools/shadow_models.py), drawn by
 * client/OathboundKnightRenderer; animations rise, idle, walk, slash.
 * Its own oath-bound instincts:
 *  - Oath Cleave: in a fight, every 8 s its greatsword sweeps a wide arc before it - 1.5x a blow to everything in
 *    front within 4 blocks, thrown back.
 *  - Sworn Guard: when its master falls below 40% health, it steps out of their shadow at their side, draws every foe
 *    within 12 blocks onto itself, and stands like a wall for 6 s (+12 armour, unmovable). Every 30 s.
 */
public class OathboundKnight extends ShadowSoldier implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation RISE = RawAnimation.begin().thenPlay("animation.shadow_knight.rise");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.shadow_knight.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.shadow_knight.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.shadow_knight.run");
    private static final RawAnimation SLASH = RawAnimation.begin().thenPlay("animation.shadow_knight.slash");

    private static final java.util.UUID GUARD_ARMOR = java.util.UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000021");
    private static final java.util.UUID GUARD_FOOTING = java.util.UUID.fromString("e5f3b2c1-7d40-4a3b-8e62-8f1d50000022");
    private int cleaveReady = 60, guardReady;

    public OathboundKnight(EntityType<? extends OathboundKnight> type, Level level) { super(type, level); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel sl) || isRemoved()) return;
        if (cleaveReady > 0) cleaveReady--;
        if (guardReady > 0) guardReady--;
        LivingEntity t = getTarget();
        if (cleaveReady == 0 && t != null && t.isAlive() && distanceToSqr(t) < 3.6 * 3.6) cleave(sl);
        Player o = owner();
        if (guardReady == 0 && o != null && o.isAlive() && o.getHealth() < o.getMaxHealth() * 0.4f && tickCount % 5 == 0) guard(sl, o);
        if (guardReady == 0 && living() && getHealth() < getMaxHealth() * 0.5f) {   // in a Challenge: it braces itself like a wall
            guardReady = 600;
            TimedModifiers.apply(this, Attributes.ARMOR, GUARD_ARMOR, "Sworn Guard", 12, AttributeModifier.Operation.ADDITION, 120);
            TimedModifiers.apply(this, Attributes.KNOCKBACK_RESISTANCE, GUARD_FOOTING, "Sworn Guard", 1.0, AttributeModifier.Operation.ADDITION, 120);
            ShadowFx.knightGuard(sl, this, 120);
        }
    }

    /** Oath Cleave: a wide sweep of the greatsword. */
    private void cleave(ServerLevel sl) {
        cleaveReady = 160;
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        triggerAnim("attack", "attack");
        Vec3 f = Vec3.directionFromRotation(0, getYRot());
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5f * ShadowFx.domainBoost(owner());
        var src = owner() instanceof net.minecraft.server.level.ServerPlayer sp ? damageSources().indirectMagic(this, sp) : damageSources().mobAttack(this);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4.2), e -> e != this && e.isAlive() && !isFriend(e) && !(e instanceof Player pl && pl.isCreative()))) {
            Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
            if (to.lengthSqr() > 4.2 * 4.2 || (to.lengthSqr() > 1 && to.normalize().dot(f) < -0.1)) continue;   // a half-circle before it
            e.invulnerableTime = 0;
            if (e.hurt(src, dmg)) {
                Vec3 push = to.lengthSqr() > 1e-4 ? to.normalize() : f;
                e.push(push.x * 0.8, 0.3, push.z * 0.8);
                ShadowFx.shadowHit(sl, e, rank());
            }
        }
        ShadowFx.knightCleave(sl, this, f);
    }

    /** Sworn Guard: at its master's side, every foe's eyes on it. */
    private void guard(ServerLevel sl, Player o) {
        guardReady = 600;
        Vec3 at = o.position().add(o.getLookAngle().multiply(1, 0, 1).normalize().scale(1.6));
        ShadowFx.sinkPool(sl, position(), getBbWidth());
        teleportTo(at.x, o.getY(), at.z);
        setYRot(o.getYRot()); yBodyRot = getYRot();
        TimedModifiers.apply(this, Attributes.ARMOR, GUARD_ARMOR, "Sworn Guard", 12, AttributeModifier.Operation.ADDITION, 120);
        TimedModifiers.apply(this, Attributes.KNOCKBACK_RESISTANCE, GUARD_FOOTING, "Sworn Guard", 1.0, AttributeModifier.Operation.ADDITION, 120);
        for (Mob m : sl.getEntitiesOfClass(Mob.class, o.getBoundingBox().inflate(12), m -> m.isAlive() && !isFriend(m) && !(m instanceof ShadowSoldier))) {
            m.setTarget(this);
            if (getTarget() == null) setTarget(m);
        }
        ShadowFx.knightGuard(sl, this, 120);
    }

    @Override public EntityDimensions getDimensions(Pose pose) { return super.getDimensions(pose).scale(rankScale()); }

    private PlayState move(AnimationState<OathboundKnight> s) {
        if (tickCount - risingAt() < 36) return s.setAndContinue(RISE);
        return s.setAndContinue(s.isMoving() ? (getDeltaMovement().horizontalDistanceSqr() > 0.02 ? RUN : WALK) : IDLE);
    }

    /** The attack controller only plays what the server triggers (triggerAnim "attack"), once, then lets go. */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "move", 4, this::move));
        c.add(new AnimationController<>(this, "attack", 0, s -> PlayState.STOP).triggerableAnim("attack", SLASH));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
