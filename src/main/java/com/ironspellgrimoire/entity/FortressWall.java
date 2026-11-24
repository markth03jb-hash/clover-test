package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.server.FortressSiege;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/**
 * One block of an Architect of Ruin wall (server/FortressSiege): solid like a boat - mobs and players bump into it,
 * arrows stop on it - and it can be broken. Kind 0 a wall block, 1 the gatehouse arch (no collision: the way in and
 * out). It is never saved, lives a fixed time, and fades at once if its owner or an ally ends up inside it, so no one
 * is ever trapped. It never touches a block.
 */
public class FortressWall extends Entity {
    public static final int WALL = 0, GATE = 1;
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(FortressWall.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HP = SynchedEntityData.defineId(FortressWall.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> GRAND = SynchedEntityData.defineId(FortressWall.class, EntityDataSerializers.BOOLEAN);
    public UUID owner;
    public float maxHp = 40;
    public int life = 600;

    public FortressWall(EntityType<? extends FortressWall> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setup(UUID owner, int kind, float hp, int life, boolean grand, float yaw) {
        this.owner = owner;
        this.entityData.set(KIND, kind);
        this.entityData.set(HP, hp);
        this.entityData.set(GRAND, grand);
        this.maxHp = hp;
        this.life = life;
        this.setYRot(yaw);
        this.yRotO = yaw;
    }

    @Override protected void defineSynchedData() {
        this.entityData.define(KIND, WALL);
        this.entityData.define(HP, 40f);
        this.entityData.define(GRAND, false);
    }

    public int kind() { return this.entityData.get(KIND); }
    public float hp() { return this.entityData.get(HP); }
    /** The Citadel's walls: taller and tougher. */
    public boolean grand() { return this.entityData.get(GRAND); }
    public Player ownerPlayer() { return owner == null ? null : level().getPlayerByUUID(owner); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        Player o = ownerPlayer();
        if (o == null || !o.isAlive() || o.level() != level() || tickCount >= life || o.distanceToSqr(this) > 80 * 80) { FortressSiege.wallGone(this, false); discard(); return; }
        if (kind() == WALL && tickCount % 5 == 0 && FortressSiege.someoneInside(this)) { FortressSiege.wallGone(this, true, false); discard(); }   // gives way: not a breach
    }

    /** Solid to everything, like a boat - except the gate, which is the way through. */
    @Override public boolean canBeCollidedWith() { return kind() == WALL && isAlive(); }
    @Override public boolean isPickable() { return kind() == WALL && isAlive(); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isAttackable() { return kind() == WALL; }
    @Override public boolean fireImmune() { return true; }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (level().isClientSide || isRemoved() || kind() != WALL) return false;
        if (src.is(DamageTypeTags.IS_FIRE) || src.is(DamageTypeTags.IS_FALL) || src.is(DamageTypeTags.IS_DROWNING)) return false;
        Entity by = src.getEntity();
        if (by != null && FortressSiege.friendOf(owner, by)) return false;   // its own side can't knock it down
        float hp = hp() - amount;
        this.entityData.set(HP, hp);
        if (hp <= 0) { FortressSiege.wallGone(this, true); discard(); }
        return true;
    }

    /** The gatehouse's model is three blocks wide: keep it drawn when only its ends are on screen. */
    @Override public AABB getBoundingBoxForCulling() { return kind() == GATE ? getBoundingBox().inflate(1.6, 0.6, 1.6) : getBoundingBox().inflate(0, 0.5, 0); }

    @Override protected void readAdditionalSaveData(CompoundTag t) { discard(); }   // never saved: if one ever is, it goes
    @Override protected void addAdditionalSaveData(CompoundTag t) {}
}
