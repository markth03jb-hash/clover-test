package com.ironspellgrimoire.entity;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Grimoire of Ill Omen's Afterimage (server/OmenFx): a black-blue silhouette of its mage that walks steadily away,
 * through walls, for 10 s. It cannot be hurt and touches nothing; whatever was after its mage chases it instead - and
 * chasing it still counts as pursuit, so the law keeps counting. When it fades, everything that chased it is set back
 * on its mage's trail.
 */
public class OmenAfterimage extends PathfinderMob {
    public static final int LIFE = 200;
    public UUID owner;
    public Vec3 heading = Vec3.ZERO;

    public OmenAfterimage(EntityType<? extends OmenAfterimage> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.2);
    }

    @Override public boolean hurt(DamageSource src, float amount) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(net.minecraft.world.entity.Entity e) {}
    @Override public boolean isPickable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean shouldShowName() { return false; }
    /** On its mage's side: their own pages never pick it out. */
    @Override public boolean isAlliedTo(net.minecraft.world.entity.Entity e) { return owner != null && owner.equals(e.getUUID()) || super.isAlliedTo(e); }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean shouldBeSaved() { return false; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (tickCount >= LIFE || owner == null) { fade(); return; }
        // walks steadily on along its heading, a hand above whatever floor it finds, never touching a block
        Vec3 h = heading.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, getYRot()) : heading;
        Vec3 step = new Vec3(h.x, 0, h.z).normalize().scale(0.16);
        double y = getY();
        int top = level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(getX() + step.x), (int) Math.floor(getZ() + step.z));
        if (Math.abs(top - y) < 3) y += (top - y) * 0.3;   // follows the lie of the land; under a roof it keeps its height
        setPos(getX() + step.x, y, getZ() + step.z);
        setDeltaMovement(Vec3.ZERO);   // it moves itself; its legs swing from the change in position
        float yaw = (float) Math.toDegrees(Math.atan2(-step.x, step.z));
        setYRot(yaw); yBodyRot = yaw; yHeadRot = yaw;
    }

    /** It fades: everything chasing it turns back to its mage. */
    void fade() {
        if (owner != null && level().getPlayerByUUID(owner) instanceof net.minecraft.world.entity.player.Player p && p.isAlive()) {
            for (Mob m : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(32), x -> x.getTarget() == this)) m.setTarget(p);
        } else {
            for (Mob m : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(32), x -> x.getTarget() == this)) m.setTarget(null);
        }
        discard();
    }

    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); if (owner != null) t.putUUID("Owner", owner); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); if (t.hasUUID("Owner")) owner = t.getUUID("Owner"); }
}
