package com.ironspellgrimoire.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * The Parasite Grimoire's Hive Nest: a living mound of flesh its owner grows where they stand (server/ParasiteFx). It
 * keeps the biomass that spills past its owner's full gut, heals them while they're near, and once a day brings them
 * back to it when they die. Right-click it (its owner) to take its biomass back. Nothing can hurt it; it places no
 * blocks. Drawn by client/HiveNestRenderer (a Blender model, tools/blender/parasite_arms.py).
 */
public class HiveNest extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(HiveNest.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> STORED = SynchedEntityData.defineId(HiveNest.class, EntityDataSerializers.FLOAT);

    public HiveNest(EntityType<?> type, Level level) { super(type, level); }

    @Override protected void defineSynchedData() { entityData.define(OWNER, Optional.empty()); entityData.define(STORED, 0f); }

    public UUID owner() { return entityData.get(OWNER).orElse(null); }
    public void setOwner(UUID u) { entityData.set(OWNER, Optional.ofNullable(u)); }
    public float stored() { return entityData.get(STORED); }
    public void setStored(float f) { entityData.set(STORED, Math.max(0, Math.min(MAX, f))); }
    public static final float MAX = 300f;

    @Override protected void readAdditionalSaveData(CompoundTag t) {
        if (t.hasUUID("owner")) setOwner(t.getUUID("owner"));
        setStored(t.getFloat("stored"));
    }
    @Override protected void addAdditionalSaveData(CompoundTag t) {
        if (owner() != null) t.putUUID("owner", owner());
        t.putFloat("stored", stored());
    }

    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean hurt(DamageSource s, float a) { return false; }
    @Override public boolean isInvulnerable() { return true; }

    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (!level().isClientSide && p instanceof ServerPlayer sp) com.ironspellgrimoire.server.ParasiteFx.nestUsed(sp, this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void tick() {
        super.tick();
        if (!onGround()) setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));   // it settles on the ground
        else setDeltaMovement(getDeltaMovement().multiply(0.5, 0, 0.5));
        move(MoverType.SELF, getDeltaMovement());
        if (!level().isClientSide && tickCount % 40 == 0) com.ironspellgrimoire.server.ParasiteFx.nestTick(this);
    }
}
