package com.ironspellgrimoire.entity;

import net.minecraft.world.entity.EntityDimensions;
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
 * and can rise all the way to Sovereign's Hand. Model: geo/shadow_knight (tools/shadow_models.py), drawn by
 * client/OathboundKnightRenderer; animations rise, idle, walk, slash.
 */
public class OathboundKnight extends ShadowSoldier implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation RISE = RawAnimation.begin().thenPlay("animation.shadow_knight.rise");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.shadow_knight.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.shadow_knight.walk");
    private static final RawAnimation SLASH = RawAnimation.begin().thenPlay("animation.shadow_knight.slash");

    public OathboundKnight(EntityType<? extends OathboundKnight> type, Level level) { super(type, level); }

    @Override public EntityDimensions getDimensions(Pose pose) { return super.getDimensions(pose).scale(rankScale()); }

    private PlayState move(AnimationState<OathboundKnight> s) {
        if (tickCount - risingAt() < 36) return s.setAndContinue(RISE);
        return s.setAndContinue(s.isMoving() ? WALK : IDLE);
    }

    private PlayState attack(AnimationState<OathboundKnight> s) {
        if (swinging && s.getController().getAnimationState() == AnimationController.State.STOPPED) {
            s.getController().forceAnimationReset();
            s.getController().setAnimation(SLASH);
        }
        return PlayState.CONTINUE;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "move", 4, this::move));
        c.add(new AnimationController<>(this, "attack", 0, this::attack));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
