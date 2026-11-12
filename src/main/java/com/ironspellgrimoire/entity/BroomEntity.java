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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A magic broom, ridden like a Clover Kingdom mage's (summoned with the Magic Broom item; it vanishes when you get off).
 * Hold forward to fly where you look - look up to climb, down to dive; back to brake; let go and it hovers. It runs on
 * your mana (server/BroomFlight drains it and trails your grimoire's colour behind you): with none left it can only
 * glide slowly down. Its rider never takes fall damage. The rider's own client moves it (like a boat), so it answers
 * at once; everyone else sees it glide to where it is.
 */
public class BroomEntity extends Entity {
    private static final EntityDataAccessor<Boolean> NO_MANA = SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.BOOLEAN);
    private int idle;
    private int lerpSteps;
    private double lx, ly, lz, lyaw;

    public BroomEntity(EntityType<?> type, Level level) { super(type, level); }

    private static final EntityDataAccessor<Integer> BOOK = SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.INT);
    /** Set each tick by the client while its sprint key is held: the broom boosts (and the server drains more mana). */
    public static volatile boolean CLIENT_BOOST;
    @Override protected void defineSynchedData() { this.entityData.define(NO_MANA, false); this.entityData.define(BOOK, -1); }
    /** The rider's grimoire (GrimoireMastery.BOOKS index, -1 none): its bands take that grimoire's colour. */
    public int book() { return this.entityData.get(BOOK); }
    public void setBook(int b) { this.entityData.set(BOOK, b); }
    public boolean noMana() { return this.entityData.get(NO_MANA); }
    public void setNoMana(boolean b) { this.entityData.set(NO_MANA, b); }

    @Override protected void readAdditionalSaveData(CompoundTag t) {}
    @Override protected void addAdditionalSaveData(CompoundTag t) {}

    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected boolean canAddPassenger(Entity e) { return getPassengers().isEmpty() && e instanceof Player; }
    @Override public LivingEntity getControllingPassenger() { return getFirstPassenger() instanceof Player p ? p : null; }
    @Override public double getPassengersRidingOffset() { return 0.1; }
    @Override public boolean shouldRiderSit() { return true; }

    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (!level().isClientSide && getPassengers().isEmpty()) p.startRiding(this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity rider = getControllingPassenger();
        if (rider == null) {   // no one on it: it fades
            if (!level().isClientSide && ++idle > 20) discard();
            setDeltaMovement(getDeltaMovement().scale(0.8));
            return;
        }
        idle = 0;
        rider.fallDistance = 0;
        if (isControlledByLocalInstance()) {   // the rider's own game flies it
            lerpSteps = 0;
            setYRot(rider.getYRot());
            Vec3 look = rider.getLookAngle();
            boolean empty = noMana();
            boolean charm = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(rider).resolve()
                    .flatMap(h -> h.findFirstCurio(com.ironspellgrimoire.registry.AddonItemRegistry.ELVEN_WIND_CHARM.get())).isPresent();   // elven wind: faster
            double sp = (empty ? 0.25 : charm ? 1.05 : 0.75) * (!empty && level().isClientSide && CLIENT_BOOST ? 1.5 : 1.0);   // sprint: a boost
            Vec3 want = rider.zza > 0 ? look.scale(sp) : rider.zza < 0 ? look.scale(-0.15) : Vec3.ZERO;
            if (rider.xxa != 0) {   // strafing drifts it sideways a little
                Vec3 side = new Vec3(look.z, 0, -look.x).normalize().scale(rider.xxa * 0.3);
                want = want.add(side);
            }
            if (empty) want = want.add(0, -0.12, 0);   // out of mana: it glides down
            Vec3 v = getDeltaMovement().scale(0.86).add(want.scale(0.14));
            setDeltaMovement(v);
            move(MoverType.SELF, v);
        } else if (level().isClientSide && lerpSteps > 0) {   // someone else's broom: glide to where it is
            double x = getX() + (lx - getX()) / lerpSteps, y = getY() + (ly - getY()) / lerpSteps, z = getZ() + (lz - getZ()) / lerpSteps;
            setYRot((float) (getYRot() + Mth.wrapDegrees(lyaw - getYRot()) / lerpSteps));
            lerpSteps--;
            setPos(x, y, z);
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        lx = x; ly = y; lz = z; lyaw = yaw; lerpSteps = 3;
    }

    @Override public boolean causeFallDamage(float d, float m, net.minecraft.world.damagesource.DamageSource s) { return false; }
}
