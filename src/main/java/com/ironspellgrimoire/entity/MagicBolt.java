package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.registry.AddonEntities;
import net.mehvahdjukaar.moonlight.api.entity.ImprovedProjectileEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A real projectile (Moonlight's ImprovedProjectileEntity): it flies with proper velocity, gravity and drag, collides
 * with blocks and bodies through the game's own physics, and detonates through the callback the move supplied. It is
 * invisible by itself - the move draws its look with the addon's own effects through {@code flyFx}.
 */
public class MagicBolt extends ImprovedProjectileEntity {
    /** What happens where it lands: the point, and the body it struck (null on blocks, the ground or the end of its life). */
    @FunctionalInterface public interface Impact { void hit(MagicBolt bolt, Vec3 at, LivingEntity target); }
    /** The look of its flight, called each tick with where it was last tick. */
    @FunctionalInterface public interface FlyFx { void tick(MagicBolt bolt, Vec3 from); }

    private Impact impact;
    private FlyFx flyFx;
    private ServerPlayer caster;
    private float gravity = 0.03F;
    private boolean done;

    public MagicBolt(EntityType<? extends MagicBolt> type, Level level) {
        super(type, level);
        this.maxAge = 40;
    }

    /** Fires one: from {@code pos} with velocity {@code vel} (blocks per tick); heavier = drops faster. */
    public static MagicBolt launch(ServerPlayer p, Vec3 pos, Vec3 vel, float gravity, int maxAge, Impact impact, FlyFx fx) {
        MagicBolt b = new MagicBolt(AddonEntities.MAGIC_BOLT.get(), p.level());
        b.caster = p;
        b.setOwner(p);
        b.setPos(pos.x, pos.y, pos.z);
        b.setDeltaMovement(vel);
        b.gravity = gravity;
        b.maxAge = maxAge;
        b.impact = impact;
        b.flyFx = fx;
        p.level().addFreshEntity(b);
        return b;
    }

    @Override protected Item getDefaultItem() { return Items.FIRE_CHARGE; }
    @Override protected float getGravity() { return gravity; }
    @Override protected float getInertia() { return 0.99F; }
    @Override public boolean isPickable() { return false; }

    @Override protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && e instanceof LivingEntity l && l.isAlive() && !(e instanceof ArmorStand)
                && (caster == null || (e != caster && !e.isAlliedTo(caster)));
    }

    @Override public void tick() {
        Vec3 from = position();
        super.tick();
        if (level().isClientSide || done) return;
        if (caster == null || !caster.isAlive() || impact == null) { discard(); return; }   // no caster (reloaded): vanish
        if (flyFx != null) flyFx.tick(this, from);
        if (getY() < level().getMinBuildHeight()) finish(position(), null);
    }

    @Override protected void onHitEntity(EntityHitResult hit) {
        if (hit.getEntity() instanceof LivingEntity l) finish(position().add(hit.getLocation()).scale(0.5), l);
    }

    @Override protected void onHitBlock(BlockHitResult hit) { finish(hit.getLocation(), null); }

    @Override public void reachedEndOfLife() { finish(position(), null); }

    private void finish(Vec3 at, LivingEntity target) {
        if (done || level().isClientSide) return;
        done = true;
        if (impact != null) impact.hit(this, at, target);
        discard();
    }

    @Override public boolean shouldBeSaved() { return false; }
}
