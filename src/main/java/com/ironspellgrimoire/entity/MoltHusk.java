package com.ironspellgrimoire.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * Molting (the Parasite Grimoire): the outer layer its host sheds at the brink - a hollow, dark red husk in the host's
 * own shape that mobs turn on while the host gets away. It stands still and crumbles into spines after 5 s or when it
 * breaks (server/ParasiteFx). Drawn by client/MoltHuskRenderer in its owner's skin, darkened.
 */
public class MoltHusk extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(MoltHusk.class, EntityDataSerializers.OPTIONAL_UUID);
    public int life = 100;

    public MoltHusk(EntityType<? extends MoltHusk> type, Level level) { super(type, level); setNoAi(true); }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.MOVEMENT_SPEED, 0);
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(OWNER, Optional.empty()); }
    public UUID owner() { return entityData.get(OWNER).orElse(null); }
    public void setOwner(UUID u) { entityData.set(OWNER, Optional.ofNullable(u)); }

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean shouldDropExperience() { return false; }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override public void addAdditionalSaveData(CompoundTag t) { }
    @Override public void readAdditionalSaveData(CompoundTag t) { discard(); }   // never kept across a reload

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && --life <= 0) com.ironspellgrimoire.server.ParasiteMoves.huskCrumbles(this);
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource s) {
        if (!level().isClientSide && !isRemoved()) com.ironspellgrimoire.server.ParasiteMoves.huskCrumbles(this);
        super.die(s);
    }
}
