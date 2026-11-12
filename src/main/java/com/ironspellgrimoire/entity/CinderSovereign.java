package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.registry.AddonSounds;
import com.ironspellgrimoire.server.CinderSovereignAI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.RegistryObject;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Cinder Sovereign: the world boss (server/WorldBoss). A demon lord about fifteen blocks tall - charred hide cracked
 * with molten veins, a furnace for a heart, great ram horns, tattered wings, and the Pyre Spine on its back: five
 * black thorns crowned with braziers. Its model and moves are GeckoLib (geo/cinder_sovereign, client/CinderSovereignRenderer);
 * its fight is in server/CinderSovereignAI. It keeps count of who hurt it how much, and rewards them by that.
 */
public class CinderSovereign extends AddonBoss implements GeoEntity {
    private static final String A = "animation.cinder_sovereign.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(A + "idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(A + "walk");
    public static final String[] MOVES = {"roar", "slam", "swipe", "wing_gust", "death"};

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Damage dealt to it, by player. */
    public final Map<UUID, Float> dealt = new HashMap<>();
    public int stuck, idleFor;
    public long expires;

    public CinderSovereign(EntityType<? extends CinderSovereign> type, Level level) {
        super(type, level, "The Cinder Sovereign", BossEvent.BossBarColor.RED);
        this.setMaxUpStep(2.5f);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 2000).add(Attributes.ARMOR, 14).add(Attributes.ARMOR_TOUGHNESS, 6)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        AnimationController<CinderSovereign> main = new AnimationController<>(this, "main", 6,
                s -> s.setAndContinue(s.isMoving() && !isDeadOrDying() ? WALK : IDLE));
        for (String m : MOVES) main.triggerableAnim(m, RawAnimation.begin().thenPlay(A + m));
        c.add(main);
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    /** Plays one of its moves on every watching client. */
    public void play(String move) { triggerAnim("main", move); }

    @Override protected float[] phaseLines() { return new float[]{0.66f, 0.33f}; }
    @Override public int maxPhase() { return 3; }
    @Override public List<RegistryObject<SoundEvent>> music() { return AddonSounds.BOSS_MUSIC; }
    @Override protected void aiTick() { CinderSovereignAI.tick(this); }
    @Override protected void onPhase(int phase) { CinderSovereignAI.phase(this, phase); }
    @Override protected void onDying(int t) { CinderSovereignAI.dying(this, t); }

    @Override
    protected void onHurt(DamageSource src, float amount) {
        if (src.getEntity() instanceof Player p) { dealt.merge(p.getUUID(), amount, Float::sum); focus = p.getUUID(); }
    }

    @Override
    public void die(DamageSource src) {
        super.die(src);
        if (!level().isClientSide) play("death");
    }

    /** Its death plays out longer than the other bosses': the roar, the crumbling wings, the fall. */
    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (!level().isClientSide) onDying(deathTime);
        if (deathTime >= 150 && !level().isClientSide && !isRemoved()) remove(RemovalReason.KILLED);
    }

    /** Its wings reach far past its body: keep drawing it while any of it is on screen. */
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(16, 6, 16); }

    @Override public boolean fireImmune() { return true; }

    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putLong("Expires", expires); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); expires = t.getLong("Expires"); }
}
