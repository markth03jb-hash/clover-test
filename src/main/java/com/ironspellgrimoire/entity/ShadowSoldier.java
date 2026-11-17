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
    /** A Challenge (Shadow Army menu): the exclusive shadow in its living form, before it was ever raised - its own
     *  colours, no master, fighting the one who challenged it. */
    private static final EntityDataAccessor<Boolean> LIVING = SynchedEntityData.defineId(ShadowSoldier.class, EntityDataSerializers.BOOLEAN);
    private UUID challenger;
    private int challengeTicks;
    private net.minecraft.server.level.ServerBossEvent bar;
    private int armyId, temporary, level = 1;
    /** Stationed (army menu): it keeps watch here instead of following its master. */
    public Vec3 station;
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
        this.entityData.define(LIVING, false);
    }

    public UUID ownerUUID() { return this.entityData.get(OWNER).orElse(null); }
    public String kind() { return this.entityData.get(KIND); }
    public int rank() { return this.entityData.get(RANK); }
    public int armyId() { return armyId; }
    public int temporary() { return temporary; }
    /** The tick it began to rise (for the client's rise out of the pool). */
    public int risingAt() { return this.entityData.get(RISING); }

    public boolean living() { return this.entityData.get(LIVING); }

    /** The army menu's model of a shadow (client only, never added to the world). */
    public void preview(String kind, int rank, boolean living) {
        this.entityData.set(KIND, kind);
        this.entityData.set(RANK, rank);
        this.entityData.set(LIVING, living);
        this.entityData.set(RISING, 0);
    }
    public UUID challenger() { return challenger; }

    /** Set it up as a Challenge: its living form, fighting {@code who}. */
    public void setupLiving(ServerPlayer who, String kind, String title) {
        this.entityData.set(LIVING, true);
        this.entityData.set(KIND, kind);
        this.challenger = who.getUUID();
        this.entityData.set(RISING, this.tickCount);
        setCustomName(Component.literal(title));
        setCustomNameVisible(false);
        double hp = this instanceof OathboundKnight ? 320 : 260, dmg = this instanceof OathboundKnight ? 13 : 11;
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(dmg);
        getAttribute(Attributes.ARMOR).setBaseValue(this instanceof OathboundKnight ? 14 : 8);
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.7);
        if (this instanceof ShadowRavener) getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.38);
        setHealth((float) hp);
        this.entityData.set(RANK, 2);
        refreshDimensions();
        bar = new net.minecraft.server.level.ServerBossEvent(Component.literal(title),
                this instanceof OathboundKnight ? net.minecraft.world.BossEvent.BossBarColor.RED : net.minecraft.world.BossEvent.BossBarColor.YELLOW,
                net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_10);
        bar.addPlayer(who);
        setTarget(who);
    }

    /** The challenge's own turn: hunt the challenger; if they fall, flee or wait too long, it withdraws. */
    private void livingTick() {
        challengeTicks++;
        ServerPlayer c = challenger == null ? null : (ServerPlayer) level().getPlayerByUUID(challenger);
        if (bar != null) bar.setProgress(Math.max(0, getHealth() / getMaxHealth()));
        if (c == null || !c.isAlive() || c.distanceToSqr(this) > 48 * 48 || challengeTicks > 6000) {
            if (c != null) c.displayClientMessage(Component.literal(getName().getString() + " withdraws - the challenge is lost").withStyle(net.minecraft.ChatFormatting.GRAY), true);
            ShadowFx.sinkPool((ServerLevel) level(), position(), getBbWidth());
            ShadowArmy.challengeOver(this);
            discard();
            return;
        }
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive() || (t != c && t.distanceToSqr(this) > 20 * 20)) setTarget(c);
    }

    @Override
    public void remove(RemovalReason r) {
        if (bar != null) bar.removeAllPlayers();
        super.remove(r);
    }

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
        if (this instanceof ShadowRavener) { baseHp = 60; baseDmg = 8; getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.38); }
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
        if (living()) { livingTick(); return; }
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
        if (this instanceof software.bernie.geckolib.animatable.GeoEntity g) g.triggerAnim("attack", "attack");   // played once from the server - it can't stick
        return hit;
    }

    @Override
    public void die(DamageSource src) {
        if (!level().isClientSide && living()) { if (bar != null) bar.removeAllPlayers(); ShadowArmy.challengeWon(this); }
        else if (!level().isClientSide) { ShadowFx.sinkPool((ServerLevel) level(), position(), getBbWidth()); ShadowArmy.fell(this); }
        super.die(src);
    }

    @Override protected boolean shouldDropLoot() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); }
    @Override public boolean canBeLeashed(Player p) { return false; }

    /** Follow its master, keeping close; if left far behind it steps out of its master's shadow beside them. */
    /** Keep its own place in a loose ring round its master - spread out round and behind them, a few blocks off,
     *  each shadow on its own spot (by its army id) - and only look at its master once it has stopped there. */
    static class FollowMaster extends Goal {
        final ShadowSoldier s;
        FollowMaster(ShadowSoldier s) { this.s = s; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        /** Its spot: an arc behind and beside its master, three rings deep, turning with the way they face. */
        Vec3 slot(Player o) {
            int id = Math.max(0, s.armyId);
            double ring = 3.5 + (id % 3) * 1.8;
            double ang = Math.toRadians(o.getYRot()) + Math.PI * (0.5 + 1.0 * ((id * 0.618034) % 1.0));   // golden-ratio spread over the back half-circle and the flanks
            return o.position().add(-Math.sin(ang) * ring, 0, Math.cos(ang) * ring);
        }

        @Override public boolean canUse() {
            Player o = s.owner();
            if (o == null || s.getTarget() != null) return false;
            if (s.station != null) return s.distanceToSqr(s.station) > 4;
            return s.distanceToSqr(slot(o)) > 9 || s.distanceToSqr(o) > 12 * 12;
        }
        @Override public boolean canContinueToUse() {
            Player o = s.owner();
            if (o == null || s.getTarget() != null) return false;
            if (s.station != null) return s.distanceToSqr(s.station) > 2;
            return s.distanceToSqr(slot(o)) > 1.5 * 1.5;
        }
        @Override public void stop() { s.getNavigation().stop(); }
        @Override public void tick() {
            Player o = s.owner();
            if (o == null) return;
            if (s.station != null) {   // back to its post
                if (s.tickCount % 10 == 0) s.getNavigation().moveTo(s.station.x, s.station.y, s.station.z, 1.0);
                return;
            }
            Vec3 at = slot(o);
            if (s.distanceToSqr(o) > 24 * 24) {
                s.teleportTo(at.x, o.getY(), at.z);
                s.getNavigation().stop();
                return;
            }
            double far = s.distanceToSqr(o);
            if (s.tickCount % 8 == 0 || s.getNavigation().isDone()) s.getNavigation().moveTo(at.x, at.y, at.z, far > 100 ? 1.35 : 1.1);
            if (s.getNavigation().isDone()) s.getLookControl().setLookAt(o, 6, s.getMaxHeadXRot());   // only once it stands still
        }
    }

    /** Idle at its spot: a slow glance round now and then, never a spin. */
    @Override public int getMaxHeadYRot() { return 45; }
    @Override public int getHeadRotSpeed() { return 6; }

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
            if (s.station != null && s.tickCount % 20 == 0) {   // on watch: it turns on anything hostile that comes near its post
                for (net.minecraft.world.entity.Mob m : s.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, s.getBoundingBox().inflate(10),
                        m -> m instanceof net.minecraft.world.entity.monster.Enemy && m.isAlive() && !s.isFriend(m))) return m;
            }
            return null;
        }
        @Override public boolean canUse() { return s.getTarget() == null && s.tickCount % 5 == 0 && pick() != null; }
        @Override public void start() { s.setTarget(pick()); }
        @Override public boolean canContinueToUse() { return false; }
    }
}
