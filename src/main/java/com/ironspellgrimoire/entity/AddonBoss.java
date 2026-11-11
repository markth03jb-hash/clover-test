package com.ironspellgrimoire.entity;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * The shared body of the later bosses (Licht, Revenant, CurseWitch): phases at fixed health lines, a boss bar, a pose the
 * client model follows, a transformation between phases (he can't be hurt meanwhile), and the same no-one-shot rules as
 * the others - no hit over 15% of max health, no more than 120 a second, a blow can't carry him past a phase line.
 * Each boss supplies its own fight (aiTick), phase lines, transformation and death.
 */
public abstract class AddonBoss extends Monster {
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(AddonBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(AddonBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POSE_AT = SynchedEntityData.defineId(AddonBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TRANSFORM = SynchedEntityData.defineId(AddonBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLAG = SynchedEntityData.defineId(AddonBoss.class, EntityDataSerializers.INT);

    private final ServerBossEvent bar;
    private long windowStart = -100;
    private float windowDmg = 0;
    public Vec3 home;
    public int busy, far, close;
    public long lastHurt;
    public java.util.UUID focus;

    protected AddonBoss(EntityType<? extends AddonBoss> type, Level level, String name, BossEvent.BossBarColor color) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
        this.bar = new ServerBossEvent(Component.literal(name), color, BossEvent.BossBarOverlay.NOTCHED_10);
    }

    /** Health fractions where each phase ends, e.g. {0.7, 0.4}: three phases. */
    protected abstract float[] phaseLines();
    protected abstract void aiTick();
    protected abstract void onPhase(int phase);
    protected abstract void onDying(int t);
    protected void onHurt(DamageSource src, float amount) {}
    /** Multiplies what gets through after the caps (shields, guards...). */
    protected float damageTaken(DamageSource src) { return 1f; }
    /** Its music, one track per phase (the last repeats). */
    public abstract List<RegistryObject<SoundEvent>> music();
    public abstract int maxPhase();

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(PHASE, 1);
        this.entityData.define(POSE, 0);
        this.entityData.define(POSE_AT, 0);
        this.entityData.define(TRANSFORM, 0);
        this.entityData.define(FLAG, 0);
    }

    public int phase() { return this.entityData.get(PHASE); }
    public void setPhase(int p) { this.entityData.set(PHASE, p); }
    public int pose() { return this.entityData.get(POSE); }
    public int poseAt() { return this.entityData.get(POSE_AT); }
    public void setPose(int pose) { this.entityData.set(POSE, pose); this.entityData.set(POSE_AT, this.tickCount); }
    public int transforming() { return this.entityData.get(TRANSFORM); }
    public void setTransforming(int t) { this.entityData.set(TRANSFORM, t); }
    /** A free number the boss can show on its model (Revenant: open portals; CurseWitch: in flight). */
    public int flag() { return this.entityData.get(FLAG); }
    public void setFlag(int f) { this.entityData.set(FLAG, f); }

    @Override protected void registerGoals() { this.goalSelector.addGoal(0, new FloatGoal(this)); }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (home == null) home = position();
        if (transforming() > 0) {
            setTransforming(transforming() - 1);
            getNavigation().stop();
            setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
            if (transforming() == 0) setPose(0);
        } else aiTick();
        bar.setProgress(getHealth() / getMaxHealth());
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide) return false;
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(src, amount);
        if (transforming() > 0 || isDeadOrDying()) return false;
        float max = getMaxHealth();
        boolean ult = com.ironspellgrimoire.server.GrimoireMastery.isUltimate(src);   // a grimoire's ultimate isn't capped
        amount = (ult ? amount : Math.min(amount, max * 0.15f)) * damageTaken(src);
        long now = level().getGameTime();
        if (now - windowStart >= 20) { windowStart = now; windowDmg = 0; }
        if (!ult) amount = Math.min(amount, 120f - windowDmg);
        float[] lines = phaseLines();
        float floor = phase() <= lines.length ? max * lines[phase() - 1] : 0f;
        if (phase() <= lines.length && getHealth() - amount < floor) amount = Math.max(0f, getHealth() - floor);
        boolean done = amount > 0 && super.hurt(src, amount);
        if (done) { windowDmg += amount; lastHurt = now; onHurt(src, amount); }
        if (phase() <= lines.length && getHealth() <= floor + 0.01f) {
            setPhase(phase() + 1);
            busy = 0;
            onPhase(phase());
        }
        return done;
    }

    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (!level().isClientSide) onDying(deathTime);
        if (deathTime >= 70 && !level().isClientSide && !isRemoved()) remove(RemovalReason.KILLED);
    }

    @Override public void startSeenByPlayer(ServerPlayer p) { super.startSeenByPlayer(p); bar.addPlayer(p); }
    @Override public void stopSeenByPlayer(ServerPlayer p) { super.stopSeenByPlayer(p); bar.removePlayer(p); }

    @Override public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Phase", phase()); }
    @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag t) { super.readAdditionalSaveData(t); setPhase(Math.max(1, t.getInt("Phase"))); }

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean isSunBurnTick() { return false; }
    @Override public boolean causeFallDamage(float d, float m, DamageSource s) { return false; }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override public boolean shouldDropExperience() { return false; }
    @Override protected void dropEquipment() {}
    @Override public boolean canChangeDimensions() { return false; }
    @Override public boolean isPushable() { return false; }
}
