package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.server.EliteTraits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A Rift-Born elite's parting gift (server/EliteTraits): a small rift standing where it died. Hit it 4 times within its
 * 5 s and it closes; otherwise three elites of the same kind step out. Never saved, never touches a block.
 */
public class EliteRift extends Entity {
    public static final int LIFE = 100, HITS = 4;
    private static final EntityDataAccessor<Integer> HIT = SynchedEntityData.defineId(EliteRift.class, EntityDataSerializers.INT);
    private String mobType = "minecraft:zombie";
    private int rank;

    public EliteRift(EntityType<? extends EliteRift> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setup(String mobType, int rank) { this.mobType = mobType; this.rank = rank; }
    public String mobType() { return mobType; }
    public int rank() { return rank; }
    /** Hits taken so far (the client draws it fraying). */
    public int hits() { return this.entityData.get(HIT); }

    @Override protected void defineSynchedData() { this.entityData.define(HIT, 0); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (tickCount >= LIFE) { EliteTraits.riftOpens(this); discard(); }
    }

    @Override public boolean isPickable() { return true; }
    @Override public boolean isAttackable() { return true; }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide() || isRemoved()) return false;
        if (!(src.getEntity() instanceof Player p)) return false;
        int h = hits() + 1;
        this.entityData.set(HIT, h);
        var snd = com.ironspellgrimoire.registry.AddonSounds.get("elite_crack");   // (self-made)
        if (snd != null) playSound(snd, 1f, 0.6f + 0.15f * h);
        if (h >= HITS) { EliteTraits.riftClosed(this, p); discard(); }
        return true;
    }

    @Override protected void readAdditionalSaveData(CompoundTag t) { discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag t) {}
}
