package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.server.FallingStars;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A fallen star (server/FallingStars): it comes down out of the sky (no block is touched), lands as a glowing Star
 * Core, and elites gather round it. While any of them lives it's sealed; then anyone can take it (use it).
 */
public class FallingStar extends Entity {
    private static final EntityDataAccessor<Boolean> LANDED = SynchedEntityData.defineId(FallingStar.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SEALED = SynchedEntityData.defineId(FallingStar.class, EntityDataSerializers.BOOLEAN);
    public double groundY;
    public int life;
    public final List<UUID> guardians = new ArrayList<>();

    public FallingStar(EntityType<? extends FallingStar> type, Level level) { super(type, level); this.noPhysics = true; }

    @Override protected void defineSynchedData() { entityData.define(LANDED, false); entityData.define(SEALED, true); }
    public boolean landed() { return entityData.get(LANDED); }
    public void setLanded(boolean b) { entityData.set(LANDED, b); }
    public boolean sealed() { return entityData.get(SEALED); }
    public void setSealed(boolean b) { entityData.set(SEALED, b); }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) FallingStars.tick(this);
    }

    @Override public boolean isPickable() { return landed(); }
    @Override public boolean hurt(DamageSource src, float amount) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 256 * 256; }

    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (!level().isClientSide) FallingStars.take(this, p);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        groundY = t.getDouble("Ground"); life = t.getInt("Life"); setLanded(t.getBoolean("Landed")); setSealed(t.getBoolean("Sealed"));
        guardians.clear();
        for (Tag g : t.getList("Guardians", Tag.TAG_INT_ARRAY)) guardians.add(NbtUtils.loadUUID(g));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putDouble("Ground", groundY); t.putInt("Life", life); t.putBoolean("Landed", landed()); t.putBoolean("Sealed", sealed());
        var l = new net.minecraft.nbt.ListTag();
        for (UUID g : guardians) l.add(NbtUtils.createUUID(g));
        t.put("Guardians", l);
    }
}
