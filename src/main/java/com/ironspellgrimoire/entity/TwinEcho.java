package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.server.TwinsFx;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The Masked Twins' Echo (server/TwinsFx.echo): in a fight, the resting twin's mask leaves the hip and floats at its
 * wearer's shoulder, firing its own bolts (Sol's fire, Nyx's slowing frost) at the nearest foe every second and a half.
 * It goes back to the hip when the fight ends. Touches nothing but its prey; never kept across a reload.
 */
public class TwinEcho extends Entity {
    private static final EntityDataAccessor<Integer> FORM = SynchedEntityData.defineId(TwinEcho.class, EntityDataSerializers.INT);
    private UUID owner;
    private int cd = 20;

    public TwinEcho(EntityType<? extends TwinEcho> t, Level l) { super(t, l); noPhysics = true; }

    public TwinEcho(ServerLevel l, ServerPlayer p, int form) {
        this(AddonEntities.TWIN_ECHO.get(), l);
        owner = p.getUUID();
        entityData.set(FORM, form);
        Vec3 h = home(p);
        setPos(h.x, h.y, h.z);
    }

    public int form() { return entityData.get(FORM); }

    @Override protected void defineSynchedData() { entityData.define(FORM, 0); }

    /** Over the shoulder opposite the main hand, bobbing. */
    private static Vec3 home(ServerPlayer p) {
        Vec3 look = p.getLookAngle(), right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
        return p.position().add(right.scale(-1.0)).add(look.multiply(1, 0, 1).scale(-0.3)).add(0, 2.0 + 0.15 * Math.sin(p.tickCount * 0.12), 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerPlayer p = owner == null ? null : (ServerPlayer) ((ServerLevel) level()).getPlayerByUUID(owner);
        if (p == null || !p.isAlive() || p.level() != level() || tickCount > 12000) { discard(); return; }
        Vec3 at = position(), to = home(p);
        Vec3 next = at.distanceTo(to) > 8 ? to : at.add(to.subtract(at).scale(0.3));
        setPos(next.x, next.y, next.z);
        LivingEntity t = --cd <= 0 ? TwinsFx.echoTarget(p, this) : null;
        if (t != null) {
            Vec3 d = t.position().subtract(position());
            setYRot((float) (Math.atan2(d.z, d.x) * 180 / Math.PI) - 90F);
            TwinsFx.echoShot(p, this, t);
            cd = 30;
        } else if (cd <= 0) { cd = 10; setYRot(p.getYRot()); }
        else if (cd > 20) setYRot(p.getYRot());
    }

    @Override protected void readAdditionalSaveData(CompoundTag t) { discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag t) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(this); }
}
