package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.server.AstaFx;
import com.ironspellgrimoire.server.Fx;
import com.ironspellgrimoire.server.FxSend;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A hooded spellcaster with one simple elemental spell (fire, wind, lightning or water). It keeps its distance and
 * casts every few seconds. It drops nothing at all, but gives much more grimoire mastery than a normal mob. Its spells
 * hurt only the target: nothing in the world is ever changed.
 */
public class MageEnemy extends Monster {
    public static final int FIRE = 0, WIND = 1, LIGHTNING = 2, WATER = 3;
    private static final EntityDataAccessor<Integer> ELEMENT = SynchedEntityData.defineId(MageEnemy.class, EntityDataSerializers.INT);
    private static final int[] PAL = {Fx.FIRE, Fx.WIND, Fx.LIGHTNING, Fx.ICE};

    public MageEnemy(EntityType<? extends MageEnemy> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 26.0).add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 26.0).add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); this.entityData.define(ELEMENT, 0); }
    public int element() { return this.entityData.get(ELEMENT); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Element", element()); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); this.entityData.set(ELEMENT, t.getInt("Element")); }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor lvl, net.minecraft.world.DifficultyInstance d,
                                                                    net.minecraft.world.entity.MobSpawnType r, net.minecraft.world.entity.SpawnGroupData g, CompoundTag tag) {
        this.entityData.set(ELEMENT, this.random.nextInt(4));
        return super.finalizeSpawn(lvl, d, r, g, tag);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new CastGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ------------------------------------------------------------------ nothing is ever dropped
    @Override protected void dropCustomDeathLoot(DamageSource s, int looting, boolean recentlyHit) {}
    @Override protected void dropEquipment() {}
    @Override public boolean shouldDropExperience() { return false; }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override public int getExperienceReward() { return 0; }
    @Override public boolean isSunBurnTick() { return false; }

    // ------------------------------------------------------------------ the spells
    private void cast(LivingEntity t) {
        if (!(level() instanceof ServerLevel sl)) return;
        int pal = PAL[element()];
        Vec3 from = position().add(0, getBbHeight() * 0.75, 0);
        Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0);
        Vec3 dir = to.subtract(from).normalize();
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.HOSTILE, 0.8f, 1.2f);
        Fx.flash(sl, from, 0.7f);
        switch (element()) {
            case FIRE, WATER -> bolt(sl, pal, from, dir, t, element() == FIRE ? 5f : 4f);
            case WIND -> {   // a crescent of wind that knocks you back
                Vec3 up = new Vec3(0, 1, 0);
                FxSend.crescent(sl, pal, from.add(dir.scale(1.2)), dir, up, 1.4f, 0.4f, 6, 0.5f);
                AstaFx.later(5, () -> {
                    if (!isAlive() || t.position().distanceTo(position()) > 14) return;
                    hurtTarget(t, 4f);
                    t.knockback(0.9, -dir.x, -dir.z);
                    Fx.impact(sl, pal, t.position().add(0, t.getBbHeight() * 0.5, 0), 0.9f);
                });
            }
            case LIGHTNING -> {   // marks the spot, then strikes it: step away and it misses
                Vec3 spot = t.position();
                FxSend.ring(sl, pal, spot.add(0, 0.05, 0), 0.2f, 1.6f, 0.05f, 16);
                AstaFx.later(16, () -> {
                    if (!isAlive()) return;
                    FxSend.beam(sl, pal, spot.add(0, 9, 0), spot, 0.3f, 4);
                    Fx.impact(sl, pal, spot.add(0, 0.5, 0), 1.2f);
                    sl.playSound(null, spot.x, spot.y, spot.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 0.4f, 1.6f);
                    if (t.isAlive() && t.position().distanceTo(spot) < 1.8) hurtTarget(t, 6f);
                });
            }
        }
    }

    /** A bolt that flies for up to 20 ticks and bursts on the first body it meets; it never touches a block. */
    private void bolt(ServerLevel sl, int pal, Vec3 from, Vec3 dir, LivingEntity t, float dmg) {
        step(sl, pal, from, dir.scale(1.1), t, dmg, 0);
    }

    private void step(ServerLevel sl, int pal, Vec3 at, Vec3 vel, LivingEntity t, float dmg, int n) {
        if (!isAlive() || n > 24) return;
        Vec3 next = at.add(vel);
        FxSend.trail(sl, pal, at, next, 0.32f, 4);
        Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
        if (next.distanceTo(c) < 0.9) {
            hurtTarget(t, dmg);
            Fx.impact(sl, pal, c, 1.0f);
            return;
        }
        AstaFx.later(1, () -> step(sl, pal, next, vel, t, dmg, n + 1));
    }

    private void hurtTarget(LivingEntity t, float dmg) {
        t.hurt(damageSources().indirectMagic(this, this), dmg);
    }

    private static final class CastGoal extends Goal {
        private final MageEnemy m;
        private int cooldown = 40;
        CastGoal(MageEnemy m) { this.m = m; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { LivingEntity t = m.getTarget(); return t != null && t.isAlive(); }
        @Override public void tick() {
            LivingEntity t = m.getTarget();
            if (t == null) return;
            m.getLookControl().setLookAt(t, 30f, 30f);
            double d = m.distanceTo(t);
            boolean see = m.getSensing().hasLineOfSight(t);
            if (d < 6) m.getNavigation().moveTo(m.getX() + (m.getX() - t.getX()), m.getY(), m.getZ() + (m.getZ() - t.getZ()), 1.1);   // keeps its distance
            else if (d > 12 || !see) m.getNavigation().moveTo(t, 1.0);
            else m.getNavigation().stop();
            if (--cooldown <= 0 && see && d < 20) { m.cast(t); cooldown = 55 + m.random.nextInt(30); }
        }
    }
}
