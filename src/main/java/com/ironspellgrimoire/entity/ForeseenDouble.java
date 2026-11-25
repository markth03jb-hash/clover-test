package com.ironspellgrimoire.entity;

import java.util.UUID;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Grimoire of the Foreseen's False Future (server/ForeseenFx): a pale, see-through double of its mage, standing where
 * the future says they will be. It can't be hurt and touches nothing; foes near it strike at it instead. When it fades,
 * they turn back to its mage.
 */
public class ForeseenDouble extends PathfinderMob {
    public UUID owner;
    public int life = 120;

    public ForeseenDouble(EntityType<? extends ForeseenDouble> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    public static AttributeSupplier.Builder attributes() { return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0); }

    @Override public boolean hurt(DamageSource src, float amount) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity e) {}
    @Override public boolean isPickable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean isAlliedTo(Entity e) { return owner != null && owner.equals(e.getUUID()) || super.isAlliedTo(e); }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if (level().isClientSide) return;
        if (tickCount >= life || owner == null) {
            Player p = owner == null ? null : level().getPlayerByUUID(owner);
            for (Mob m : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(32), x -> x.getTarget() == this)) m.setTarget(p != null && p.isAlive() ? p : null);
            discard();
        }
    }
}
