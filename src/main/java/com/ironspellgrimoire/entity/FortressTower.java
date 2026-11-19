package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.server.FortressSiege;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/**
 * An Architect of Ruin tower (server/FortressSiege): it stands, it shoots. Kinds - 0 Watchtower (arrows), 1 Arcane
 * spire (beams), 2 Forge tower (fire), 3 Siege platform (bolts), 4 the Workshop's Ballista on its own. Solid, can be
 * broken, never saved; it fires on its own at the nearest foe in range (FortressSiege.towerTick).
 */
public class FortressTower extends Entity {
    public static final int ARROWS = 0, BEAM = 1, FIRE = 2, BOLTS = 3, BALLISTA = 4;
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(FortressTower.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HP = SynchedEntityData.defineId(FortressTower.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIM = SynchedEntityData.defineId(FortressTower.class, EntityDataSerializers.FLOAT);
    public UUID owner;
    public float maxHp = 80, range = 16;
    public int life = 600, reload;

    public FortressTower(EntityType<? extends FortressTower> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setup(UUID owner, int kind, float hp, int life, float range, float yaw) {
        this.owner = owner;
        this.entityData.set(KIND, kind);
        this.entityData.set(HP, hp);
        this.entityData.set(AIM, yaw);
        this.maxHp = hp;
        this.life = life;
        this.range = range;
        this.reload = 10 + (int) (Math.abs(getId() * 7L) % 10);   // towers don't all fire on the same tick
        setYRot(yaw); yRotO = yaw;
    }

    @Override protected void defineSynchedData() {
        this.entityData.define(KIND, ARROWS);
        this.entityData.define(HP, 80f);
        this.entityData.define(AIM, 0f);
    }

    public int kind() { return this.entityData.get(KIND); }
    public float hp() { return this.entityData.get(HP); }
    /** Where the ballista on top points (degrees): turned toward what it shoots. */
    public float aim() { return this.entityData.get(AIM); }
    public void setAim(float yaw) { this.entityData.set(AIM, yaw); }
    public Player ownerPlayer() { return owner == null ? null : level().getPlayerByUUID(owner); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        Player o = ownerPlayer();
        if (o == null || !o.isAlive() || o.level() != level() || tickCount >= life || o.distanceToSqr(this) > 80 * 80) { FortressSiege.towerGone(this, false); discard(); return; }
        FortressSiege.towerTick(this);
    }

    @Override public boolean canBeCollidedWith() { return isAlive(); }
    @Override public boolean isPickable() { return isAlive(); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean fireImmune() { return true; }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide || isRemoved()) return false;
        if (src.is(DamageTypeTags.IS_FIRE) || src.is(DamageTypeTags.IS_FALL) || src.is(DamageTypeTags.IS_DROWNING)) return false;
        Entity by = src.getEntity();
        if (by != null && FortressSiege.friendOf(owner, by)) return false;
        float hp = hp() - amount;
        this.entityData.set(HP, hp);
        if (hp <= 0) { FortressSiege.towerGone(this, true); discard(); }
        return true;
    }

    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(0.6, 1.5, 0.6); }
    @Override protected void readAdditionalSaveData(CompoundTag t) { discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag t) {}
}
