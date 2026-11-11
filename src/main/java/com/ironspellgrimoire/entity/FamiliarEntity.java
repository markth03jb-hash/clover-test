package com.ironspellgrimoire.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A mana familiar (server/Familiars): a small creature of its owner's grimoire floating at their shoulder. It isn't
 * alive in the usual sense - it can't be hurt, isn't saved with the world (it's called back each time its owner is
 * about) and both sides fly it the same way, so it glides smoothly. Its kind (the grimoire's element) and stage (1-3,
 * from the grimoire's mastery) are synced for the client to draw (client/FamiliarRenderer).
 */
public class FamiliarEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STAGE = SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CAST = SynchedEntityData.defineId(FamiliarEntity.class, EntityDataSerializers.INT);

    public FamiliarEntity(EntityType<?> type, Level level) { super(type, level); this.noPhysics = true; }

    @Override protected void defineSynchedData() {
        this.entityData.define(OWNER, -1);
        this.entityData.define(KIND, 0);
        this.entityData.define(STAGE, 1);
        this.entityData.define(CAST, 0);
    }

    public int ownerId() { return this.entityData.get(OWNER); }
    public void setOwner(int id) { this.entityData.set(OWNER, id); }
    public int kind() { return this.entityData.get(KIND); }
    public void setKind(int k) { this.entityData.set(KIND, k); }
    public int stage() { return this.entityData.get(STAGE); }
    public void setStage(int s) { this.entityData.set(STAGE, s); }
    /** When it last cast (tickCount), for the client's flare. */
    public int castAt() { return this.entityData.get(CAST); }
    public void markCast() { this.entityData.set(CAST, this.tickCount); }

    @Override protected void readAdditionalSaveData(CompoundTag t) {}
    @Override protected void addAdditionalSaveData(CompoundTag t) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource s, float a) { return false; }

    public Player owner() {
        Entity e = level().getEntity(ownerId());
        return e instanceof Player p ? p : null;
    }

    /** Its owner can send it home (sneak + right-click it). */
    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (p.getId() != ownerId()) return InteractionResult.PASS;
        if (!level().isClientSide && p instanceof net.minecraft.server.level.ServerPlayer sp && sp.isShiftKeyDown()) com.ironspellgrimoire.server.Familiars.sendHome(sp);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void tick() {
        super.tick();
        Player o = owner();
        if (o == null) { if (!level().isClientSide && tickCount > 40) discard(); return; }
        // at the owner's shoulder, a little behind, bobbing and drifting
        float yaw = o.getYRot() * Mth.DEG_TO_RAD;
        double side = 0.75, back = 0.45;
        Vec3 want = o.position().add(-Math.cos(yaw) * side + Math.sin(yaw) * back, o.getBbHeight() + 0.15 + Mth.sin(tickCount * 0.1f) * 0.12,
                -Math.sin(yaw) * side - Math.cos(yaw) * back);
        Vec3 d = want.subtract(position());
        if (d.lengthSqr() > 24 * 24) { setPos(want.x, want.y, want.z); setDeltaMovement(Vec3.ZERO); return; }   // fell far behind: it catches up at once
        Vec3 v = getDeltaMovement().scale(0.7).add(d.scale(0.12));
        setDeltaMovement(v);
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        setYRot(o.getYRot());
    }

    /** Both sides fly it; the server's position updates are only a nudge. */
    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        if (position().distanceToSqr(x, y, z) > 9) setPos(x, y, z);
    }
}
