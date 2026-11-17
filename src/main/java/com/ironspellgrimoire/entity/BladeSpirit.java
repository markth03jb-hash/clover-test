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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The Hunger Blade's spirit (Bond 5: Unleash): a ghost of the wielder's sword that floats at their shoulder for 15 s,
 * darting out once a second to cut the nearest foe and coming back. Touches nothing but its prey.
 */
public class BladeSpirit extends Entity {
    private static final EntityDataAccessor<ItemStack> STACK = SynchedEntityData.defineId(BladeSpirit.class, EntityDataSerializers.ITEM_STACK);
    private UUID owner;
    private float damage;
    private int cd = 10, strike;
    private Vec3 dest;

    public BladeSpirit(EntityType<? extends BladeSpirit> t, Level l) { super(t, l); noPhysics = true; }

    public BladeSpirit(ServerLevel l, ServerPlayer p, ItemStack s, float damage) {
        this(AddonEntities.BLADE_SPIRIT.get(), l);
        owner = p.getUUID();
        this.damage = damage;
        entityData.set(STACK, s);
        Vec3 h = home(p);
        setPos(h.x, h.y, h.z);
    }

    public ItemStack stack() { return entityData.get(STACK); }
    public UUID owner() { return owner; }

    @Override protected void defineSynchedData() { entityData.define(STACK, ItemStack.EMPTY); }

    private static Vec3 home(ServerPlayer p) {
        Vec3 look = p.getLookAngle(), right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
        return p.position().add(right.scale(-1.1)).add(0, 1.9 + 0.15 * Math.sin(p.tickCount * 0.15), 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerPlayer p = owner == null ? null : (ServerPlayer) ((ServerLevel) level()).getPlayerByUUID(owner);
        if (p == null || !p.isAlive() || tickCount > 300 || p.level() != level()) { discard(); return; }
        if (--cd <= 0) {
            LivingEntity t = HungerFx.spiritTarget(p);
            if (t != null) {
                dest = t.position().add(0, t.getBbHeight() * 0.5, 0);
                strike = 6;
                HungerFx.spiritHit(p, t, damage, HungerBladeItem.form(stack()));
            }
            cd = 20;
        }
        Vec3 to = strike > 0 && dest != null ? dest : home(p);
        if (strike > 0) strike--;
        Vec3 at = position();
        Vec3 next = at.add(to.subtract(at).scale(strike > 0 ? 0.5 : 0.3));
        setPos(next.x, next.y, next.z);
        setYRot(p.getYRot());
    }

    @Override protected void readAdditionalSaveData(CompoundTag t) { discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag t) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(this); }
}
