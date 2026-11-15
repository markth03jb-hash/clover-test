package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.server.ShadowArmy;
import com.ironspellgrimoire.server.ShadowFx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/**
 * A shadow soldier of the Shadow Grimoire (server/ShadowArmy): the shadow of a mob its master slew and raised. It
 * looks like that mob turned to living shadow - black, a blue sheen, burning blue eyes (client/ShadowSoldierRenderer)
 * - follows its master, fights whatever its master fights or is attacked by, and never harms its master or its
 * master's other shadows. It isn't saved with the world: its master's army raises it again each time.
 */
public class ShadowSoldier extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(ShadowSoldier.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> KIND = SynchedEntityData.defineId(ShadowSoldier.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> RANK = SynchedEntityData.defineId(ShadowSoldier.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RISING = SynchedEntityData.defineId(ShadowSoldier.class, EntityDataSerializers.INT);
    private int armyId, temporary, level = 1;
    public int hunting;   // Command: Hunt - ticks it hits harder

    public ShadowSoldier(EntityType<? extends ShadowSoldier> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 32).add(Attributes.ARMOR, 2).add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OWNER, Optional.empty());
        this.entityData.define(KIND, "minecraft:zombie");
        this.entityData.define(RANK, 0);
        this.entityData.define(RISING, 0);
    }

    public UUID ownerUUID() { return this.entityData.get(OWNER).orElse(null); }
    public String kind() { return this.entityData.get(KIND); }
    public int rank() { return this.entityData.get(RANK); }
    public int armyId() { return armyId; }
    public int temporary() { return temporary; }
    /** The tick it began to rise (for the client's rise out of the pool). */
    public int risingAt() { return this.entityData.get(RISING); }

    public Player owner() {
        UUID u = ownerUUID();
        return u == null ? null : level().getPlayerByUUID(u);
    }

    /** Set it up as one of a player's army. temporary > 0: a Night Legion shadow that sinks away after that many ticks. */
    public void setup(UUID owner, String kind, int armyId, int level, int rank, String name, int temporary) {
        this.entityData.set(OWNER, Optional.of(owner));
        this.entityData.set(KIND, kind);
        this.armyId = armyId;
        this.temporary = temporary;
        this.entityData.set(RISING, this.tickCount);
        setShadowName(name);
        applyStats(level, rank);
    }

    public void setShadowName(String name) {
        setCustomName(Component.literal(name));
        setCustomNameVisible(false);
    }

    /** Its health and strength: from the mob it was, its level and its rank. */
    public void applyStats(int level, int rank) {
        this.level = level;
        this.entityData.set(RANK, rank);
        double baseHp = 20, baseDmg = 4;
        net.minecraft.world.entity.EntityType<?> t = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(net.minecraft.resources.ResourceLocation.tryParse(kind()));
        if (t != null && net.minecraft.world.entity.ai.attributes.DefaultAttributes.hasSupplier(t)) {
            @SuppressWarnings("unchecked")
            var sup = net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) t);
            baseHp = Math.max(10, sup.getBaseValue(Attributes.MAX_HEALTH));
            if (sup.hasAttribute(Attributes.ATTACK_DAMAGE)) baseDmg = Math.max(3, sup.getBaseValue(Attributes.ATTACK_DAMAGE));
        }
        if (this instanceof OathboundKnight) { baseHp = 80; baseDmg = 9; }
        double hp = Math.min(600, baseHp * (1 + 0.08 * (level - 1)) * (1 + 0.25 * rank));
        double dmg = baseDmg * (1 + 0.06 * (level - 1)) * (1 + 0.2 * rank);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(dmg);
        getAttribute(Attributes.ARMOR).setBaseValue(2 + (rank >= 1 ? 4 : 0) + rank * 2);
        setHealth((float) hp);
        refreshDimensions();
    }

    /** A little bigger each rank. */
    public float rankScale() { return 1.0f + 0.06f * rank(); }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
        this.goalSelector.addGoal(5, new FollowMaster(this));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new MasterFights(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        Player o = owner();
        if (o == null || !o.isAlive()) { if (tickCount > 40) discard(); return; }
        if (hunting > 0) hunting--;
        if (temporary > 0 && tickCount >= temporary) { ShadowFx.sinkPool((ServerLevel) level(), position(), getBbWidth()); discard(); return; }
        LivingEntity t = getTarget();
        if (t != null && (!t.isAlive() || isFriend(t) || t.distanceToSqr(this) > 32 * 32)) setTarget(null);
    }

    /** Its master, and its master's other shadows. */
    public boolean isFriend(Entity e) {
        UUID o = ownerUUID();
        if (o == null) return false;
        if (e.getUUID().equals(o)) return true;
        return e instanceof ShadowSoldier s && o.equals(s.ownerUUID());
    }

    @Override public boolean isAlliedTo(Entity e) { return isFriend(e) || super.isAlliedTo(e); }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (src.getEntity() != null && isFriend(src.getEntity())) return false;
        return super.hurt(src, amount);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        int r = rank();
        float boost = (hunting > 0 ? 1.4f : 1f) * ShadowFx.domainBoost(owner());
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * boost;
        DamageSource src = owner() instanceof ServerPlayer sp ? damageSources().indirectMagic(this, sp) : damageSources().mobAttack(this);
        boolean hit = target.hurt(src, dmg);
        if (hit && target instanceof LivingEntity le) {
            if (r >= 2) {   // Knight and up: the blow cleaves into whatever stands beside it
                for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, le.getBoundingBox().inflate(1.6), e -> e != le && e.isAlive() && !isFriend(e) && !(e instanceof Player))) {
                    e.hurt(src, dmg * 0.5f);
                }
            }
            if (r >= 4) heal(dmg * 0.1f);   // General: it drinks a little of every blow
            ShadowFx.shadowHit((ServerLevel) level(), le, r);
        }
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        return hit;
    }

    @Override
    public void die(DamageSource src) {
        if (!level().isClientSide) { ShadowFx.sinkPool((ServerLevel) level(), position(), getBbWidth()); ShadowArmy.fell(this); }
        super.die(src);
    }

    @Override protected boolean shouldDropLoot() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); }
    @Override public boolean canBeLeashed(Player p) { return false; }

    /** Follow its master, keeping close; if left far behind it steps out of its master's shadow beside them. */
    static class FollowMaster extends Goal {
        final ShadowSoldier s;
        FollowMaster(ShadowSoldier s) { this.s = s; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { Player o = s.owner(); return o != null && s.getTarget() == null && s.distanceToSqr(o) > 16; }
        @Override public boolean canContinueToUse() { Player o = s.owner(); return o != null && s.getTarget() == null && s.distanceToSqr(o) > 6; }
        @Override public void tick() {
            Player o = s.owner();
            if (o == null) return;
            s.getLookControl().setLookAt(o, 10, s.getMaxHeadXRot());
            if (s.distanceToSqr(o) > 24 * 24) {
                Vec3 at = o.position().add(o.getLookAngle().scale(-2));
                s.teleportTo(at.x, o.getY(), at.z);
                s.getNavigation().stop();
            } else if (s.tickCount % 10 == 0) s.getNavigation().moveTo(o, 1.25);
        }
    }

    /** Fight whatever its master fights, or whatever attacks its master or it. */
    static class MasterFights extends Goal {
        final ShadowSoldier s;
        MasterFights(ShadowSoldier s) { this.s = s; setFlags(EnumSet.of(Flag.TARGET)); }
        LivingEntity pick() {
            Player o = s.owner();
            if (o == null) return null;
            LivingEntity a = o.getLastHurtByMob(), b = o.getLastHurtMob(), c = s.getLastHurtByMob();
            for (LivingEntity e : new LivingEntity[]{b, a, c})
                if (e != null && e.isAlive() && !s.isFriend(e) && e.distanceToSqr(s) < 28 * 28 && !(e instanceof Player pl && pl.isCreative())) return e;
            return null;
        }
        @Override public boolean canUse() { return s.getTarget() == null && s.tickCount % 5 == 0 && pick() != null; }
        @Override public void start() { s.setTarget(pick()); }
        @Override public boolean canContinueToUse() { return false; }
    }
}
