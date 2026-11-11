package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.server.RhyaAI;
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
 * Rhya the Disloyal: a three-phase boss of Copy Magic. He copies the grimoire of whoever he touches - so he fights you
 * with your own magic.
 * <ul>
 *   <li>Phase 1 (100%-60%): he touches your grimoire and fights in its magic, one attribute at a time.</li>
 *   <li>Phase 2 (60%-30%): he cycles through the magic of the captains he has copied - dark, slash, silver, thorns.</li>
 *   <li>Phase 3 (from 30%): he copies anti-magic itself: a black sword that silences your spells, and every spell that
 *       hits him is copied straight back. Near the end, Total Copy: everything he has copied, at once.</li>
 * </ul>
 * Same rules as the other bosses: no hit over 15% of his health, no more than 120 a second, no skipping a phase.
 * Spawned only by egg or command. His fight is in {@link RhyaAI}.
 */
public class Rhya extends Monster {
    public static final float MAX_HP = 450f;
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(Rhya.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(Rhya.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POSE_AT = SynchedEntityData.defineId(Rhya.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TRANSFORM = SynchedEntityData.defineId(Rhya.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COPY = SynchedEntityData.defineId(Rhya.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<net.minecraft.world.item.ItemStack> BOOK = SynchedEntityData.defineId(Rhya.class, EntityDataSerializers.ITEM_STACK);

    private final ServerBossEvent bar = new ServerBossEvent(Component.literal("Rhya the Disloyal"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
    private long windowStart = -100;
    private float windowDmg = 0;
    public final RhyaAI.State ai = new RhyaAI.State();

    public Rhya(EntityType<? extends Rhya> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
        this.setMaxUpStep(1.1f);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, MAX_HP).add(Attributes.ARMOR, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.MOVEMENT_SPEED, 0.33).add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_DAMAGE, 10.0);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(PHASE, 1);
        this.entityData.define(POSE, 0);
        this.entityData.define(POSE_AT, 0);
        this.entityData.define(TRANSFORM, 0);
        this.entityData.define(COPY, -1);
        this.entityData.define(BOOK, net.minecraft.world.item.ItemStack.EMPTY);
    }

    public int phase() { return this.entityData.get(PHASE); }
    public void setPhase(int p) { this.entityData.set(PHASE, p); }
    public int pose() { return this.entityData.get(POSE); }
    public int poseAt() { return this.entityData.get(POSE_AT); }
    public void setPose(int pose) { this.entityData.set(POSE, pose); this.entityData.set(POSE_AT, this.tickCount); }
    public int transforming() { return this.entityData.get(TRANSFORM); }
    public void setTransforming(int t) { this.entityData.set(TRANSFORM, t); }
    /** The palette of the magic he is using right now (-1: nothing copied yet). */
    public int copy() { return this.entityData.get(COPY); }
    public void setCopy(int pal) { this.entityData.set(COPY, pal); }
    /** The grimoire he copied, floating open at his side. */
    public net.minecraft.world.item.ItemStack book() { return this.entityData.get(BOOK); }
    public void setBook(net.minecraft.world.item.ItemStack s) { this.entityData.set(BOOK, s.copyWithCount(1)); }

    @Override protected void registerGoals() { this.goalSelector.addGoal(0, new FloatGoal(this)); }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        RhyaAI.tick(this);
        this.bar.setProgress(this.getHealth() / this.getMaxHealth());
        this.bar.setColor(phase() == 1 ? BossEvent.BossBarColor.YELLOW : phase() == 2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED);
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (this.level().isClientSide) return false;
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(src, amount);
        if (transforming() > 0 || this.isDeadOrDying()) return false;
        float max = this.getMaxHealth();
        boolean ult = com.ironspellgrimoire.server.GrimoireMastery.isUltimate(src);   // a grimoire's ultimate isn't capped
        if (!ult) amount = Math.min(amount, max * 0.15f);
        amount *= RhyaAI.damageTaken(this, src);                        // copies of your magic soften what they copied
        long now = this.level().getGameTime();
        if (now - windowStart >= 20) { windowStart = now; windowDmg = 0; }
        if (!ult) amount = Math.min(amount, 120f - windowDmg);
        float floor = phase() == 1 ? max * 0.6f : phase() == 2 ? max * 0.3f : 0f;
        float hp = this.getHealth();
        if (phase() < 3 && hp - amount < floor) amount = Math.max(0f, hp - floor);
        boolean done = amount > 0 && super.hurt(src, amount);
        if (done) {
            windowDmg += amount;
            RhyaAI.onHurt(this, src, amount);
        }
        if (phase() < 3 && this.getHealth() <= floor + 0.01f) RhyaAI.transform(this, phase() + 1);
        return done;
    }

    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (!this.level().isClientSide) RhyaAI.dying(this, this.deathTime);
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
