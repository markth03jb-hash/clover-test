package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.item.HungerBladeItem;
import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.server.HungerFx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The Hunger Blade thrown (Bond 3: sneak and Gnash): it flies straight out, spinning, cutting everything it passes,
 * turns back at a wall or after 16 blocks and flies home to its wielder's hand. Never touches a block.
 */
public class ThrownHungerBlade extends Entity {
    private static final EntityDataAccessor<ItemStack> STACK = SynchedEntityData.defineId(ThrownHungerBlade.class, EntityDataSerializers.ITEM_STACK);
    private UUID owner;
    private float damage;
    private boolean back;
    private final Set<Integer> hitOut = new HashSet<>(), hitBack = new HashSet<>();

    public ThrownHungerBlade(EntityType<? extends ThrownHungerBlade> t, Level l) { super(t, l); noPhysics = true; }

    public ThrownHungerBlade(ServerLevel l, ServerPlayer p, ItemStack s, float damage) {
        this(AddonEntities.THROWN_HUNGER_BLADE.get(), l);
        owner = p.getUUID();
        this.damage = damage;
        entityData.set(STACK, s);
        Vec3 eye = p.getEyePosition();
        setPos(eye.x, eye.y - 0.3, eye.z);
        setDeltaMovement(p.getLookAngle().scale(1.6));
    }

    public ItemStack stack() { return entityData.get(STACK); }

    @Override protected void defineSynchedData() { entityData.define(STACK, ItemStack.EMPTY); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;   // the server moves it; the tracker sends every tick
        ServerPlayer p = owner == null ? null : (ServerPlayer) ((ServerLevel) level()).getPlayerByUUID(owner);
        if (p == null || !p.isAlive() || tickCount > 100) { if (p != null) HungerFx.caught(p, stack()); discard(); return; }
        Vec3 at = position();
        if (!back) {
            Vec3 next = at.add(getDeltaMovement());
            var hit = level().clip(new ClipContext(at, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS || tickCount >= 10) back = true;
            else setPos(next);
        }
        if (back) {
            Vec3 to = p.getEyePosition().add(0, -0.4, 0).subtract(position());
            if (to.length() < 1.4) { HungerFx.caught(p, stack()); discard(); return; }
            setDeltaMovement(to.normalize().scale(Math.min(1.8, to.length())));
            setPos(position().add(getDeltaMovement()));
        }
        Set<Integer> done = back ? hitBack : hitOut;
        for (LivingEntity t : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.9), x -> x != p && x.isAlive())) {
            if (done.add(t.getId())) HungerFx.thrownHit(p, t, damage, HungerBladeItem.form(stack()) < 3 ? 0 : 6);
        }
    }

    @Override protected void readAdditionalSaveData(CompoundTag t) { discard(); }   // never saved: a thrown blade lasts seconds
    @Override protected void addAdditionalSaveData(CompoundTag t) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(this); }
}
