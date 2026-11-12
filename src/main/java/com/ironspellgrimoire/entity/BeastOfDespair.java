package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.server.BeastAI;
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
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * BeastOfDespair, the Beast of Despair: a three-phase boss built on Beast Magic.
 * <ul>
 *   <li>Phase 1 (100%-60%): Beast Magic - claws, charges, armour and slams.</li>
 *   <li>Phase 2 (60%-30%): Mythical Beast Magic - his third eye opens; he regrows when left alone and strikes from
 *       range.</li>
 *   <li>Phase 3 (from 30%): Despair - unbridled rage; near the end he gathers all his mana to blow himself up, and
 *       you have to break it or get clear.</li>
 * </ul>
 * He cannot be one-shot (no hit over 15% of his health, no more than 120 a second, a blow can't carry him past a phase
 * line, nothing while he transforms). Rhinoceros Armor takes 70% off what gets through. Spawned only by egg or
 * command. His fight is in {@link BeastAI}.
 */
public class BeastOfDespair extends Monster implements software.bernie.geckolib.animatable.GeoEntity {
    private static final String A = "animation.beast_of_despair.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(A + "idle"), WALK = RawAnimation.begin().thenLoop(A + "walk"),
            TRANSFORM = RawAnimation.begin().thenLoop(A + "transform"), GATHER = RawAnimation.begin().thenLoop(A + "gather"),
            BROKEN = RawAnimation.begin().thenLoop(A + "broken"), DEATH = RawAnimation.begin().thenPlayAndHold(A + "death");
    /** The moves, by pose (BeastAI.P_*): played once when the pose is set. */
    private static final String[] MOVE = {null, "claw", "charge", "roar", "slam", "guard", "throw", "bite", null, null};
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public static final float MAX_HP = 500f;
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(BeastOfDespair.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(BeastOfDespair.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POSE_AT = SynchedEntityData.defineId(BeastOfDespair.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TRANSFORM = SynchedEntityData.defineId(BeastOfDespair.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ARMOR = SynchedEntityData.defineId(BeastOfDespair.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(BeastOfDespair.class, EntityDataSerializers.INT);

    private final ServerBossEvent bar = new ServerBossEvent(Component.literal("The Beast of Despair"), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);
    /** Renames the boss bar (a bounty's modifiers are written on it). */
    public void setBarName(Component name) { bar.setName(name); }
    private long windowStart = -100;
    private float windowDmg = 0;
    public final BeastAI.State ai = new BeastAI.State();

    public BeastOfDespair(EntityType<? extends BeastOfDespair> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
        this.setMaxUpStep(1.1f);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, MAX_HP).add(Attributes.ARMOR, 12.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_DAMAGE, 10.0);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(PHASE, 1);
        this.entityData.define(POSE, 0);
        this.entityData.define(POSE_AT, 0);
        this.entityData.define(TRANSFORM, 0);
        this.entityData.define(ARMOR, 0);
        this.entityData.define(CHARGE, 0);
    }

    public int phase() { return this.entityData.get(PHASE); }
    public void setPhase(int p) { this.entityData.set(PHASE, p); }
    public int pose() { return this.entityData.get(POSE); }
    public int poseAt() { return this.entityData.get(POSE_AT); }
    public void setPose(int pose) {
        this.entityData.set(POSE, pose); this.entityData.set(POSE_AT, this.tickCount);
        if (!this.level().isClientSide && transforming() == 0 && pose >= 0 && pose < MOVE.length && MOVE[pose] != null) triggerAnim("main", MOVE[pose]);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        AnimationController<BeastOfDespair> main = new AnimationController<>(this, "main", 4, s -> {
            if (isDeadOrDying()) return s.setAndContinue(DEATH);
            if (transforming() > 0) return s.setAndContinue(TRANSFORM);
            if (charging() > 0) return s.setAndContinue(GATHER);
            if (pose() == BeastAI.P_BROKEN) return s.setAndContinue(BROKEN);
            return s.setAndContinue(s.isMoving() ? WALK : IDLE);
        });
        for (String m : MOVE) if (m != null) main.triggerableAnim(m, RawAnimation.begin().thenPlay(A + m));
        c.add(main);
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    public int transforming() { return this.entityData.get(TRANSFORM); }
    public void setTransforming(int t) { this.entityData.set(TRANSFORM, t); }
    /** Ticks of Rhinoceros Armor left. */
    public int armored() { return this.entityData.get(ARMOR); }
    public void setArmored(int t) { this.entityData.set(ARMOR, t); }
    /** Ticks into the Final Despair (0 = not gathering). */
    public int charging() { return this.entityData.get(CHARGE); }
    public void setCharging(int t) { this.entityData.set(CHARGE, t); }

    @Override protected void registerGoals() { this.goalSelector.addGoal(0, new FloatGoal(this)); }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        BeastAI.tick(this);
        this.bar.setProgress(this.getHealth() / this.getMaxHealth());
        this.bar.setColor(phase() == 1 ? BossEvent.BossBarColor.GREEN : phase() == 2 ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.RED);
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (this.level().isClientSide) return false;
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(src, amount);
        if (transforming() > 0 || this.isDeadOrDying()) return false;
        float max = this.getMaxHealth();
        boolean ult = com.ironspellgrimoire.server.GrimoireMastery.isUltimate(src);   // a grimoire's ultimate isn't capped
        if (!ult) amount = Math.min(amount, max * 0.15f);
        if (armored() > 0) amount *= 0.3f;                              // Rhinoceros Armor
        amount *= BeastAI.damageTaken(this);                             // more while he lies broken
        long now = this.level().getGameTime();
        if (now - windowStart >= 20) { windowStart = now; windowDmg = 0; }
        if (!ult) amount = Math.min(amount, 120f - windowDmg);
        float floor = phase() == 1 ? max * 0.6f : phase() == 2 ? max * 0.3f : 0f;
        float hp = this.getHealth();
        if (phase() < 3 && hp - amount < floor) amount = Math.max(0f, hp - floor);
        boolean done = amount > 0 && super.hurt(src, amount);
        if (done) {
            windowDmg += amount;
            BeastAI.onHurt(this, src, amount);
        }
        if (phase() < 3 && this.getHealth() <= floor + 0.01f) BeastAI.transform(this, phase() + 1);
        return done;
    }

    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (!this.level().isClientSide) BeastAI.dying(this, this.deathTime);
        if (this.deathTime >= 70 && !this.level().isClientSide && !this.isRemoved()) this.remove(RemovalReason.KILLED);
    }

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
