package com.ironspellgrimoire.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * Pack Call (the Parasite Grimoire): an infected brute that bursts out of the ground beside its master, follows them,
 * goes for whatever they fight (and whatever goes for them) and withers after 30 s. Its master's Strain shapes it:
 * 0 a plain brute, 1 a fast clawed stalker (Hunter), 2 a big heavy brawler (Brute), 3 a spine-spitter that shoots from
 * range (Hive). It never turns on its master, other players, or their shadows and pets. Drawn by
 * client/InfectedBruteRenderer; its fighting in server/ParasiteFx (bruteTick).
 */
public class InfectedBrute extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(InfectedBrute.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(InfectedBrute.class, EntityDataSerializers.INT);
    public int life = 600, spitCd;

    public InfectedBrute(EntityType<? extends InfectedBrute> type, Level level) { super(type, level); }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32).add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(OWNER, Optional.empty()); entityData.define(KIND, 0); }
    public UUID owner() { return entityData.get(OWNER).orElse(null); }
    public void setOwner(UUID u) { entityData.set(OWNER, Optional.ofNullable(u)); }
    public int kind() { return entityData.get(KIND); }

    /** Shape it by its master's strain: its stats and its size. */
    public void setKind(int k) {
        entityData.set(KIND, k);
        double[] s = switch (k) { case 1 -> new double[]{18, 6, 0.38}; case 2 -> new double[]{40, 9, 0.26}; case 3 -> new double[]{20, 3, 0.28}; default -> new double[]{24, 5, 0.3}; };
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(s[0]); setHealth((float) s[0]);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(s[1]);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(s[2]);
        refreshDimensions();
    }

    /** How big it's drawn and how big it is. */
    public float size() { return switch (kind()) { case 1 -> 0.95F; case 2 -> 1.35F; default -> 1.0F; }; }
    @Override public EntityDimensions getDimensions(Pose pose) { return super.getDimensions(pose).scale(size()); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) { super.onSyncedDataUpdated(key); if (KIND.equals(key)) refreshDimensions(); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true) {
            @Override public boolean canUse() { return kind() != 3 && super.canUse(); }
        });
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    /** Never its master, never a player, never its master's own (shadows, pets, other brutes). */
    @Override
    public boolean canAttack(LivingEntity t) {
        if (t instanceof Player || t instanceof InfectedBrute || t instanceof MoltHusk) return false;
        UUID o = owner();
        if (o != null && t instanceof ShadowSoldier s && o.equals(s.ownerUUID())) return false;
        if (o != null && t instanceof net.minecraft.world.entity.TamableAnimal ta && o.equals(ta.getOwnerUUID())) return false;
        return super.canAttack(t);
    }

    @Override public boolean isAlliedTo(net.minecraft.world.entity.Entity e) {
        UUID o = owner();
        return (o != null && (o.equals(e.getUUID()) || e instanceof InfectedBrute b && o.equals(b.owner()))) || super.isAlliedTo(e);
    }

    public boolean isFoe(LivingEntity t) { return t != null && t.isAlive() && canAttack(t) && (t instanceof Enemy || t.getLastHurtByMob() == getOwnerEntity()); }

    public ServerPlayer getOwnerEntity() {
        UUID o = owner();
        return o != null && level().getPlayerByUUID(o) instanceof ServerPlayer sp ? sp : null;
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean shouldDropExperience() { return false; }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); discard(); }   // never kept across a reload

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) com.ironspellgrimoire.server.ParasiteFx.bruteTick(this);
    }
}
