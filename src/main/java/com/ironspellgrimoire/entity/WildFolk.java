package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.server.WildFolkAI;
import java.util.EnumSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The wild families (server/WildFolkAI): the Mirefolk of the marshes, the Stonefolk of the mountains and the
 * Sandwraiths of the deserts - each a grunt that closes in, a caster that keeps its distance and a leader that
 * rallies them - and the Gloamstalker, a night hunter that comes for the strongest mage about. One class; what each
 * one is comes from its entity type (Kind).
 */
public class WildFolk extends Monster {
    public enum Family { MIRE, STONE, SAND, NIGHT }
    public enum Role { GRUNT, CASTER, LEADER, HUNTER }
    public enum Kind {
        BOG_CRAWLER("bog_crawler", Family.MIRE, Role.GRUNT, 26, 5, 0.25, 2, false, 0.95f),
        MIRE_HEXER("mire_hexer", Family.MIRE, Role.CASTER, 22, 3, 0.24, 0, true, 1.0f),
        FEN_MATRON("fen_matron", Family.MIRE, Role.LEADER, 60, 6, 0.24, 4, true, 1.18f),
        SCREE_BRUTE("scree_brute", Family.STONE, Role.GRUNT, 34, 7, 0.22, 6, false, 1.15f),
        STONECALLER("stonecaller", Family.STONE, Role.CASTER, 24, 3, 0.23, 2, false, 1.0f),
        PEAK_WARDEN("peak_warden", Family.STONE, Role.LEADER, 70, 8, 0.23, 10, false, 1.22f),
        DUNE_STALKER("dune_stalker", Family.SAND, Role.GRUNT, 22, 5, 0.31, 1, false, 1.0f),
        SANDWRAITH("sandwraith", Family.SAND, Role.CASTER, 20, 3, 0.26, 0, true, 1.0f),
        SUN_HERALD("sun_herald", Family.SAND, Role.LEADER, 60, 7, 0.26, 4, false, 1.2f),
        GLOAMSTALKER("gloamstalker", Family.NIGHT, Role.HUNTER, 80, 9, 0.30, 6, true, 1.15f);

        public final String id;
        public final Family family;
        public final Role role;
        public final double hp, dmg, speed, armor;
        public final boolean slim;
        public final float size;
        Kind(String id, Family f, Role r, double hp, double dmg, double speed, double armor, boolean slim, float size) {
            this.id = id; this.family = f; this.role = r; this.hp = hp; this.dmg = dmg; this.speed = speed; this.armor = armor; this.slim = slim; this.size = size;
        }
        public String skin() { return "family_" + id; }
        public AttributeSupplier.Builder attributes() {
            return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, hp).add(Attributes.ATTACK_DAMAGE, dmg).add(Attributes.MOVEMENT_SPEED, speed)
                    .add(Attributes.ARMOR, armor).add(Attributes.FOLLOW_RANGE, role == Role.HUNTER ? 48 : 28).add(Attributes.KNOCKBACK_RESISTANCE, role == Role.LEADER ? 0.5 : 0.1);
        }
    }

    public Kind kind;
    public int castCd = 40, cryCd = 100, blinkCd = 120;

    public WildFolk(EntityType<? extends WildFolk> type, Level level, Kind kind) {
        super(type, level);   // (registerGoals runs inside this, before kind is set: it looks the kind up by type)
        this.kind = kind;
        this.xpReward = kind.role == Role.LEADER ? 15 : kind.role == Role.HUNTER ? 25 : 6;
    }

    static Kind kindOf(EntityType<?> t) {
        for (var en : com.ironspellgrimoire.registry.AddonEntities.FOLK.entrySet()) if (en.getValue().get() == t) return en.getKey();
        return Kind.BOG_CRAWLER;
    }

    @Override
    protected void registerGoals() {
        if (kind == null) kind = kindOf(getType());
        this.goalSelector.addGoal(0, new FloatGoal(this));
        if (kind.role == Role.CASTER) this.goalSelector.addGoal(2, new CastGoal(this));
        else this.goalSelector.addGoal(2, new MeleeAttackGoal(this, kind.role == Role.HUNTER ? 1.15 : 1.0, false));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, WildFolk.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide) WildFolkAI.tick(this);
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (!level().isClientSide) amount = WildFolkAI.taken(this, src, amount);
        return amount > 0 && super.hurt(src, amount);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource src, int looting, boolean hit) {
        super.dropCustomDeathLoot(src, looting, hit);
        if (!level().isClientSide) WildFolkAI.drops(this, src, looting);
    }

    @Override public boolean isSunBurnTick() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return kind.role != Role.LEADER && super.removeWhenFarAway(d); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("CastCd", castCd); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); castCd = t.getInt("CastCd"); }

    /** A caster: keeps 6-12 blocks off and casts its family's spell. */
    static final class CastGoal extends Goal {
        private final WildFolk m;
        CastGoal(WildFolk m) { this.m = m; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { LivingEntity t = m.getTarget(); return t != null && t.isAlive(); }
        @Override public void tick() {
            LivingEntity t = m.getTarget();
            if (t == null) return;
            m.getLookControl().setLookAt(t, 30f, 30f);
            double d = m.distanceTo(t);
            boolean see = m.getSensing().hasLineOfSight(t);
            if (d < 6) m.getNavigation().moveTo(m.getX() + (m.getX() - t.getX()), m.getY(), m.getZ() + (m.getZ() - t.getZ()), 1.1);
            else if (d > 12 || !see) m.getNavigation().moveTo(t, 1.0);
            else m.getNavigation().stop();
            if (--m.castCd <= 0 && see && d < 20) { WildFolkAI.cast(m, t); m.castCd = 70 + m.random.nextInt(30); }
        }
    }
}
