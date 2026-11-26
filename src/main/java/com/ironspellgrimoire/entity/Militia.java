package com.ironspellgrimoire.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Ashwick's militia (ASHFALL, Arc III): villagers with pitchforks who stand on the wall with you. They go for the monsters; players can't hurt them. */
public class Militia extends PathfinderMob {
    public Militia(EntityType<? extends Militia> type, Level level) { super(type, level); }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ARMOR, 3).add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false) {
            @Override protected double getAttackReachSqr(net.minecraft.world.entity.LivingEntity t) { return 3.2 * 3.2; }   // a pitchfork's reach
        });
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Monster.class, true));
    }

    @Override public boolean hurt(DamageSource src, float amount) {
        if (src.getEntity() instanceof Player) return false;   // your spells pass them by
        return super.hurt(src, amount);
    }

    @Override public boolean doHurtTarget(net.minecraft.world.entity.Entity t) {
        boolean hit = super.doHurtTarget(t);
        if (hit) swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        return hit;
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }
}
