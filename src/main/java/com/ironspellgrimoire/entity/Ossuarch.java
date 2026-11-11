package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.server.OssuarchAI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Ossuarch: a boss of bone, space and devil power, fought in three phases.
 * <ul>
 *   <li>Phase 1 (100%-75%): bone only.</li>
 *   <li>Phase 2 (75%-25%): Bone Mode - armoured in bone, and he uses space.</li>
 *   <li>Phase 3 (from 25%, healed by half his health): Devil Union - a new form and moves of its own; below 10% he
 *       goes all out.</li>
 * </ul>
 * He cannot be one-shot: no hit takes more than 15% of his health, no more than 120 a second gets through, a blow can't
 * carry him past a phase line, and he can't be hurt while he transforms. Spawned only by egg or command; drops nothing
 * (yet). His moves, arena hazards and fight brain are in {@link OssuarchAI}.
 */
public class Ossuarch extends Monster {
    public static final float MAX_HP = 400f;
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(Ossuarch.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(Ossuarch.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POSE_AT = SynchedEntityData.defineId(Ossuarch.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TRANSFORM = SynchedEntityData.defineId(Ossuarch.class, EntityDataSerializers.INT);

    private final ServerBossEvent bar = new ServerBossEvent(Component.literal("The Ossuarch"), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
    private long windowStart = -100;
    private float windowDmg = 0;
    /** The fight's own state, kept by OssuarchAI. */
    public final OssuarchAI.State ai = new OssuarchAI.State();

    public Ossuarch(EntityType<? extends Ossuarch> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, MAX_HP).add(Attributes.ARMOR, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(PHASE, 1);
        this.entityData.define(POSE, 0);
        this.entityData.define(POSE_AT, 0);
        this.entityData.define(TRANSFORM, 0);
    }

    public int phase() { return this.entityData.get(PHASE); }
    public void setPhase(int p) { this.entityData.set(PHASE, p); }
    /** Which pose the body is in (client model) and since when (tickCount). */
    public int pose() { return this.entityData.get(POSE); }
    public int poseAt() { return this.entityData.get(POSE_AT); }
    public void setPose(int pose) { this.entityData.set(POSE, pose); this.entityData.set(POSE_AT, this.tickCount); }
    /** Ticks left of a transformation (0 = not transforming). */
    public int transforming() { return this.entityData.get(TRANSFORM); }
    public void setTransforming(int t) { this.entityData.set(TRANSFORM, t); }

    @Override protected void registerGoals() { this.goalSelector.addGoal(0, new FloatGoal(this)); }   // everything else: OssuarchAI

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        OssuarchAI.tick(this);
        this.bar.setProgress(this.getHealth() / this.getMaxHealth());
        this.bar.setColor(phase() == 1 ? BossEvent.BossBarColor.WHITE : phase() == 2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED);
    }

    // ------------------------------------------------------------------ no one-shots
    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (this.level().isClientSide) return false;
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(src, amount);   // /kill still works
        if (transforming() > 0 || this.isDeadOrDying()) return false;
        float max = this.getMaxHealth();
        amount = Math.min(amount, max * 0.15f);                       // no hit takes more than 15%
        long now = this.level().getGameTime();
        if (now - windowStart >= 20) { windowStart = now; windowDmg = 0; }
        amount = Math.min(amount, 120f - windowDmg);                    // no more than 120 a second
        float floor = phase() == 1 ? max * 0.75f : phase() == 2 ? max * 0.25f : 0f;
        float hp = this.getHealth();
        if (phase() < 3 && hp - amount < floor) amount = Math.max(0f, hp - floor);   // a blow can't skip a phase
        boolean done = amount > 0 && super.hurt(src, amount);
        if (done) {
            windowDmg += amount;
            OssuarchAI.onHurt(this, src);
        }
        if (phase() < 3 && this.getHealth() <= floor + 0.01f) OssuarchAI.transform(this, phase() + 1);
        return done;
    }

    // ------------------------------------------------------------------ death: crumbling, not a vanilla poof
    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (!this.level().isClientSide) OssuarchAI.dying(this, this.deathTime);
        if (this.deathTime >= 70 && !this.level().isClientSide && !this.isRemoved()) this.remove(RemovalReason.KILLED);
    }

    // ------------------------------------------------------------------ boss bar, saving, odds and ends
    @Override public void startSeenByPlayer(ServerPlayer p) { super.startSeenByPlayer(p); this.bar.addPlayer(p); }
    @Override public void stopSeenByPlayer(ServerPlayer p) { super.stopSeenByPlayer(p); this.bar.removePlayer(p); }

    @Override public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("Phase", phase());
        if (ai.home != null) { t.putDouble("HomeX", ai.home.x); t.putDouble("HomeY", ai.home.y); t.putDouble("HomeZ", ai.home.z); }
    }

    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        setPhase(Math.max(1, t.getInt("Phase")));
        if (t.contains("HomeX")) ai.home = new Vec3(t.getDouble("HomeX"), t.getDouble("HomeY"), t.getDouble("HomeZ"));
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean isSunBurnTick() { return false; }
    @Override public boolean causeFallDamage(float d, float m, DamageSource s) { return false; }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override public boolean shouldDropExperience() { return false; }
    @Override protected void dropEquipment() {}
    @Override public boolean canChangeDimensions() { return false; }
    @Override public boolean isPushable() { return false; }
}
