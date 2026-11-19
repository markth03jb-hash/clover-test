package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.server.FortressSiege;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/**
 * An Architect of Ruin defender (server/FortressSiege): a mob from its master's garrison, standing as stone and brass
 * (client/FortressRenderers draws the mob it was, tinted), or one of the Crypt's bone sentries. Roles: 0 Footman, 1
 * Archer, 2 Mystic, 3 Brute (from the mob it was), 4 Bone sentry. It guards a post (the fortress's middle, or where
 * it was called), fights what its master fights or what comes near, never hurts its master's side, and is never
 * saved: it lasts as long as the siege or spell that called it.
 */
public class FortressDefender extends PathfinderMob {
    public static final int FOOTMAN = 0, ARCHER = 1, MYSTIC = 2, BRUTE = 3, SENTRY = 4;
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(FortressDefender.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> KIND = SynchedEntityData.defineId(FortressDefender.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(FortressDefender.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RANK = SynchedEntityData.defineId(FortressDefender.class, EntityDataSerializers.INT);
    public int gid = -1, life = 300, shot;
    public Vec3 post;
    public LivingEntity focus;   // Drill Charge / Battle Plan: go for this one
    public boolean drilling;

    public FortressDefender(EntityType<? extends FortressDefender> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24).add(Attributes.ARMOR, 4).add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OWNER, Optional.empty());
        this.entityData.define(KIND, "minecraft:vindicator");
        this.entityData.define(ROLE, FOOTMAN);
        this.entityData.define(RANK, 0);
    }

    public void setup(UUID owner, String kind, int role, int rank, int gid, Vec3 post, int life, float hp, float dmg) {
        this.entityData.set(OWNER, Optional.of(owner));
        this.entityData.set(KIND, kind);
        this.entityData.set(ROLE, role);
        this.entityData.set(RANK, rank);
        this.gid = gid;
        this.post = post;
        this.life = life;
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(dmg);
        getAttribute(Attributes.ARMOR).setBaseValue(4 + rank * 2);
        if (role == BRUTE) getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.8);
        setHealth(hp);
    }

    public UUID ownerUUID() { return this.entityData.get(OWNER).orElse(null); }
    public String kind() { return this.entityData.get(KIND); }
    public int role() { return this.entityData.get(ROLE); }
    public int rank() { return this.entityData.get(RANK); }
    public Player owner() { UUID o = ownerUUID(); return o == null ? null : level().getPlayerByUUID(o); }
    /** A little bigger each rank. */
    public float rankScale() { return 1.0f + 0.05f * rank(); }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new Ranged(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true) {
            @Override public boolean canUse() { return !ranged() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !ranged() && super.canContinueToUse(); }
        });
        this.goalSelector.addGoal(5, new Guard(this));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new Defend(this));
    }

    public boolean ranged() { return role() == ARCHER || role() == MYSTIC; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        Player o = owner();
        if (o == null || !o.isAlive() || o.level() != level() || tickCount >= life || o.distanceToSqr(this) > 80 * 80) { FortressSiege.defenderGone(this); discard(); return; }
        LivingEntity t = getTarget();
        if (t != null && (!t.isAlive() || isFriend(t) || t.distanceToSqr(this) > 28 * 28 || !FortressSiege.mayChase(this, t))) setTarget(null);
        if (shot > 0) shot--;
        if (tickCount % 20 == 0) FortressSiege.defenderTick(this);
    }

    /** Its master, its master's other defenders, walls and towers, and its master's allies. */
    public boolean isFriend(Entity e) { return FortressSiege.friendOf(ownerUUID(), e); }
    @Override public boolean isAlliedTo(Entity e) { return isFriend(e) || super.isAlliedTo(e); }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (src.getEntity() != null && isFriend(src.getEntity())) return false;
        return super.hurt(src, amount);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = FortressSiege.defenderStrike(this, target);
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        return hit;
    }

    @Override
    public void die(DamageSource src) {
        if (!level().isClientSide) FortressSiege.defenderGone(this);
        super.die(src);
    }

    @Override protected boolean shouldDropLoot() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean canBeLeashed(Player p) { return false; }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); discard(); }

    /** Back to its post (or its master's side) when it has nothing to fight. */
    static class Guard extends Goal {
        final FortressDefender d;
        Guard(FortressDefender d) { this.d = d; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        Vec3 home() {
            Vec3 ordered = FortressSiege.orderHome(d);   // Hold the Gate, a breach, Fall Back
            if (ordered != null) {
                double a = (d.gid * 2.39996) % (Math.PI * 2);
                return ordered.add(Math.cos(a) * 1.5, 0, Math.sin(a) * 1.5);
            }
            if (d.post != null) {   // its own spot round the post
                double a = (d.gid * 2.39996) % (Math.PI * 2);
                double r = d.role() == SENTRY ? 1.5 : 2.5;
                return d.post.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            }
            Player o = d.owner();
            return o == null ? d.position() : o.position();
        }
        @Override public boolean canUse() { return d.getTarget() == null && d.distanceToSqr(home()) > 4; }
        @Override public boolean canContinueToUse() { return d.getTarget() == null && d.distanceToSqr(home()) > 1.5; }
        @Override public void stop() { d.getNavigation().stop(); }
        @Override public void tick() {
            Vec3 h = home();
            if (d.distanceToSqr(h) > 32 * 32) { d.teleportTo(h.x, h.y, h.z); d.getNavigation().stop(); return; }
            if (d.tickCount % 10 == 0 || d.getNavigation().isDone()) d.getNavigation().moveTo(h.x, h.y, h.z, 1.0);
        }
    }

    /** Archers and mystics keep their distance and shoot (FortressSiege.defenderShot). */
    static class Ranged extends Goal {
        final FortressDefender d;
        Ranged(FortressDefender d) { this.d = d; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return d.ranged() && d.getTarget() != null && d.getTarget().isAlive(); }
        @Override public void stop() { d.getNavigation().stop(); }
        @Override public void tick() {
            LivingEntity t = d.getTarget();
            if (t == null) return;
            d.getLookControl().setLookAt(t, 30, 30);
            double dist = d.distanceToSqr(t);
            boolean see = d.getSensing().hasLineOfSight(t);
            if (dist > 12 * 12 || !see) { if (d.tickCount % 10 == 0) d.getNavigation().moveTo(t, 1.0); }
            else if (dist < 4 * 4) {   // too close: step back
                Vec3 away = d.position().subtract(t.position()).normalize().scale(3).add(d.position());
                if (d.tickCount % 10 == 0) d.getNavigation().moveTo(away.x, away.y, away.z, 1.1);
            } else d.getNavigation().stop();
            if (see && dist < 16 * 16 && d.shot <= 0) { d.shot = d.role() == MYSTIC ? 40 : 30; FortressSiege.defenderShot(d, t); }
        }
    }

    /** What it fights: a focus (Drill Charge, Battle Plan), what its master fights or is hit by, or a hostile near its post. */
    static class Defend extends Goal {
        final FortressDefender d;
        Defend(FortressDefender d) { this.d = d; setFlags(EnumSet.of(Flag.TARGET)); }
        LivingEntity pick() {
            LivingEntity charge = FortressSiege.chargeTarget(d);
            if (charge != null) return charge;
            LivingEntity best0 = pick0();
            return best0 != null && FortressSiege.mayChase(d, best0) ? best0 : null;
        }
        LivingEntity pick0() {
            if (d.focus != null && d.focus.isAlive() && d.focus.distanceToSqr(d) < 28 * 28) return d.focus;
            Player o = d.owner();
            if (o == null) return null;
            for (LivingEntity e : new LivingEntity[]{o.getLastHurtMob(), o.getLastHurtByMob(), d.getLastHurtByMob()})
                if (e != null && e.isAlive() && !d.isFriend(e) && e.distanceToSqr(d) < 24 * 24 && !(e instanceof Player pl && (pl.isCreative() || !(o instanceof ServerPlayer sp && com.ironspellgrimoire.AddonConfig.mayHurt(sp, pl))))) return e;
            Vec3 c = d.post != null ? d.post : d.position();
            Mob best = null; double bd = 14 * 14;
            Vec3 held = FortressSiege.orderHome(d);
            if (held != null) c = held;
            for (Mob m : d.level().getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(c, c).inflate(14), m -> m instanceof Enemy && m.isAlive() && !d.isFriend(m) && FortressSiege.mayChase(d, m))) {
                double dd = m.distanceToSqr(c);
                if (dd < bd) { bd = dd; best = m; }
            }
            return best;
        }
        @Override public boolean canUse() {
            if (d.tickCount % 5 != 0) return false;
            LivingEntity p = pick();
            return p != null && p != d.getTarget();
        }
        @Override public void start() { d.setTarget(pick()); }
        @Override public boolean canContinueToUse() { return false; }
    }
}
