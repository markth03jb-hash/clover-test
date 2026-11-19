package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.server.EliteTraits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * A Cursed Loot elite's chest (server/EliteTraits): right-click or hit it to open. Within 3 s it bites and gives only
 * its share; after that it pays out double. It fades after a minute, opened or not. Never saved.
 */
public class CursedChest extends Entity {
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(CursedChest.class, EntityDataSerializers.INT);   // 0 shut, 1 opened, 2 bit
    private final List<ItemStack> loot = new ArrayList<>();
    private int rank, openedAt = -1;

    public CursedChest(EntityType<? extends CursedChest> type, Level level) {
        super(type, level);
    }

    public void setup(List<ItemStack> items, int rank) {
        for (ItemStack s : items) loot.add(s.copy());
        this.rank = rank;
    }
    public List<ItemStack> loot() { return loot; }
    public int rank() { return rank; }
    public int age() { return tickCount; }
    public boolean opened() { return this.entityData.get(STATE) != 0; }
    public boolean bit() { return this.entityData.get(STATE) == 2; }
    public void setOpened(boolean on, boolean bite) { this.entityData.set(STATE, on ? (bite ? 2 : 1) : 0); openedAt = tickCount; }
    /** Ticks since it opened (the lid's swing on the client is worked out from the state change). */
    public int sinceOpen() { return openedAt < 0 ? -1 : tickCount - openedAt; }

    @Override protected void defineSynchedData() { this.entityData.define(STATE, 0); }

    @Override
    public void tick() {
        super.tick();
        if (!isNoGravity()) {
            setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().multiply(0.6, 0.98, 0.6));
        }
        if (level().isClientSide()) {
            if (opened() && openedAt < 0) openedAt = tickCount;
            return;
        }
        if (tickCount > 1200 || (opened() && sinceOpen() > 60)) discard();
    }

    @Override public boolean isPickable() { return true; }
    @Override public boolean isAttackable() { return true; }

    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        EliteTraits.openChest(this, p);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (!level().isClientSide() && src.getEntity() instanceof Player p) EliteTraits.openChest(this, p);
        return false;
    }

    @Override protected void readAdditionalSaveData(CompoundTag t) { discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag t) {}
}
