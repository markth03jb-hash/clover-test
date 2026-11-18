package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.server.TwinsFx;
import com.ironspellgrimoire.server.TwinsTrial;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The Masked Twins' trial (server/TwinsFx.startTrial): the Dawn Warden (Sol's mask on a floating crimson robe, a sun
 * halo, fire) and the Dusk Warden (Nyx's mask on a starry midnight robe, a crescent halo, frost and blinks). They
 * circle the one who called them and fight only them; they leave when the trial ends and are never kept across a reload.
 * Drawn by client/TwinsMasks.WardenRenderer from the twin_mask models.
 */
public class TwinWarden extends Monster {
    private static final EntityDataAccessor<Boolean> DUSK = SynchedEntityData.defineId(TwinWarden.class, EntityDataSerializers.BOOLEAN);
    private UUID owner;
    private int attackCd = 50, turn;
    private final ServerBossEvent bar = new ServerBossEvent(Component.literal("Twin Warden"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);

    public TwinWarden(EntityType<? extends TwinWarden> t, Level l) { super(t, l); setNoGravity(true); xpReward = 0; }

    public TwinWarden(ServerLevel l, ServerPlayer p, boolean dusk) {
        this(AddonEntities.TWIN_WARDEN.get(), l);
        owner = p.getUUID();
        entityData.set(DUSK, dusk);
        setCustomName(Component.literal(dusk ? "The Dusk Warden" : "The Dawn Warden"));
        bar.setName(getCustomName());
        bar.setColor(dusk ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.YELLOW);
        attackCd = dusk ? 70 : 50;
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 220).add(Attributes.ARMOR, 8).add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 48).add(Attributes.ATTACK_DAMAGE, 6);
    }

    public boolean dusk() { return entityData.get(DUSK); }
    public UUID owner() { return owner; }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(DUSK, false); }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        if (level().isClientSide) return;
        ServerPlayer p = owner == null ? null : (ServerPlayer) ((ServerLevel) level()).getPlayerByUUID(owner);
        if (p == null || !p.isAlive() || p.level() != level() || p.distanceTo(this) > 80) { discard(); return; }
        // circle the challenger, the two wardens on opposite sides, a little above the ground
        double a = tickCount * 0.02 + (dusk() ? Math.PI : 0);
        Vec3 want = p.position().add(Math.cos(a) * 6, 1.8 + 0.3 * Math.sin(tickCount * 0.08), Math.sin(a) * 6);
        Vec3 v = want.subtract(position()).scale(0.06);
        setDeltaMovement(getDeltaMovement().scale(0.6).add(v));
        Vec3 to = p.position().subtract(position());
        float yaw = (float) (Math.atan2(to.z, to.x) * 180 / Math.PI) - 90F;
        setYRot(yaw); yBodyRot = yaw; yHeadRot = yaw;
        setTarget(p);
        if (--attackCd <= 0) {
            TwinsTrial.wardenAttack(this, p, turn++);
            attackCd = (dusk() ? 55 : 45) + random.nextInt(20);
        }
        bar.setProgress(getHealth() / getMaxHealth());
    }

    @Override
    public boolean hurt(DamageSource s, float amt) {
        if (s.getEntity() instanceof TwinWarden) return false;   // the two never hurt each other
        return super.hurt(s, amt);
    }

    @Override
    public void die(DamageSource s) {
        if (!level().isClientSide && owner != null && ((ServerLevel) level()).getPlayerByUUID(owner) instanceof ServerPlayer p) TwinsTrial.wardenDown(this, p);
        super.die(s);
    }

    @Override public void startSeenByPlayer(ServerPlayer p) { super.startSeenByPlayer(p); bar.addPlayer(p); }
    @Override public void stopSeenByPlayer(ServerPlayer p) { super.stopSeenByPlayer(p); bar.removePlayer(p); }
    @Override public boolean causeFallDamage(float d, float m, DamageSource s) { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean shouldDropExperience() { return false; }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); discard(); }   // never kept across a reload
}
