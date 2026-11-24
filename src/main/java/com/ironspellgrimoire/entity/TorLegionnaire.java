package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.server.BrannochAI;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A Tor Legionnaire: one of Brannoch Tor's soldiers (server/BrannochAI). Spear and tower shield - a blow from the
 * front is mostly turned aside by the shield, so get round it. One of them may carry his standard. They leave the
 * field when he does.
 */
public class TorLegionnaire extends Monster {
    private static final EntityDataAccessor<Boolean> BEARER = SynchedEntityData.defineId(TorLegionnaire.class, EntityDataSerializers.BOOLEAN);
    public UUID lord;

    public TorLegionnaire(EntityType<? extends TorLegionnaire> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.ARMOR, 6).add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 4.0).add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); this.entityData.define(BEARER, false); }
    public boolean bearer() { return this.entityData.get(BEARER); }
    public void setBearer(boolean b) { this.entityData.set(BEARER, b); }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false) {
            @Override public boolean canUse() { return !bearer() && super.canUse(); }
        });
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12f));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (!level().isClientSide) amount = BrannochAI.shield(this, src, amount);
        return amount > 0 && super.hurt(src, amount);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && tickCount % 20 == 0) BrannochAI.legionTick(this);
    }

    @Override public boolean doHurtTarget(net.minecraft.world.entity.Entity t) { return BrannochAI.legionHit(this, t) && super.doHurtTarget(t); }

    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); if (lord != null) t.putUUID("Lord", lord); t.putBoolean("Bearer", bearer()); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); if (t.hasUUID("Lord")) lord = t.getUUID("Lord"); setBearer(t.getBoolean("Bearer")); }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean isSunBurnTick() { return false; }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override public boolean shouldDropExperience() { return false; }
    @Override protected void dropEquipment() {}
    @Override public boolean canChangeDimensions() { return false; }
}
