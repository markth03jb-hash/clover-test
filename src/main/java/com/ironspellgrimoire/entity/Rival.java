package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.server.RivalAI;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Your rival: a mage with a name, a grimoire and a personality of their own (kept on your player data by
 * {@link RivalAI}). This entity is only their body for one encounter - a duel, a race, a call for help - and it never
 * dies: beaten, a rival yields. Not saved with the world; it goes when the encounter ends.
 */
public class Rival extends Monster {
    private static final EntityDataAccessor<ItemStack> BOOK = SynchedEntityData.defineId(Rival.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> PAL = SynchedEntityData.defineId(Rival.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(Rival.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(Rival.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POSE_AT = SynchedEntityData.defineId(Rival.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> SEED = SynchedEntityData.defineId(Rival.class, EntityDataSerializers.OPTIONAL_UUID);

    /** Whose rival this is (server only). */
    public UUID owner;
    public int busy, tier = 1;

    public Rival(EntityType<? extends Rival> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 80.0).add(Attributes.ARMOR, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 40.0).add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(BOOK, ItemStack.EMPTY);
        this.entityData.define(PAL, 6);
        this.entityData.define(MODE, 0);
        this.entityData.define(POSE, 0);
        this.entityData.define(POSE_AT, 0);
        this.entityData.define(SEED, Optional.empty());
    }

    public ItemStack book() { return this.entityData.get(BOOK); }
    public void setBook(ItemStack s) { this.entityData.set(BOOK, s.copyWithCount(1)); }
    public int pal() { return this.entityData.get(PAL); }
    public void setPal(int p) { this.entityData.set(PAL, p); }
    /** 0 a challenger, 1 an ally, 2 the villain they became. */
    public int mode() { return this.entityData.get(MODE); }
    public void setMode(int m) { this.entityData.set(MODE, m); }
    public int pose() { return this.entityData.get(POSE); }
    public int poseAt() { return this.entityData.get(POSE_AT); }
    public void setPose(int p) { this.entityData.set(POSE, p); this.entityData.set(POSE_AT, this.tickCount); }
    /** Picks their look (a default skin) on the client. */
    public UUID seed() { return this.entityData.get(SEED).orElse(this.getUUID()); }
    public void setSeed(UUID u) { this.entityData.set(SEED, Optional.of(u)); }

    @Override protected void registerGoals() { this.goalSelector.addGoal(0, new FloatGoal(this)); }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        RivalAI.tick(this);
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (this.level().isClientSide) return false;
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) { this.discard(); return false; }
        if (!RivalAI.canHurt(this, src)) return false;
        if (this.getHealth() - amount <= 1f) {   // a rival never dies - they yield
            boolean r = super.hurt(src, Math.max(0f, this.getHealth() - 1f));
            RivalAI.yielded(this);
            return r;
        }
        return super.hurt(src, amount);
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override public boolean shouldDropExperience() { return false; }
    @Override protected void dropEquipment() {}
    @Override public boolean causeFallDamage(float d, float m, DamageSource s) { return false; }
    @Override public boolean isSunBurnTick() { return false; }
}
