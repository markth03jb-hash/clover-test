package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.registry.AddonSounds;
import com.ironspellgrimoire.server.ReflectionAI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;

/**
 * The Reflection: the Ascension Trial's examiner (server/Ascension, server/Rifts). It wears its challenger's own skin
 * and fights with their grimoire's magic, in their grimoire's colours. Its fight is in {@link ReflectionAI}.
 */
public class Reflection extends AddonBoss {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(Reflection.class, EntityDataSerializers.OPTIONAL_UUID);
    /** The challenger's grimoire palette (server side; the client shows the skin). */
    public int pal = 8;

    public Reflection(EntityType<? extends Reflection> type, Level level) { super(type, level, "Reflection", BossEvent.BossBarColor.WHITE); }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 420).add(Attributes.ARMOR, 6)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9).add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0);
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); this.entityData.define(OWNER, Optional.empty()); }
    public UUID owner() { return this.entityData.get(OWNER).orElse(null); }
    public void setOwner(UUID u) { this.entityData.set(OWNER, Optional.ofNullable(u)); }

    @Override protected float[] phaseLines() { return new float[]{0.5f}; }
    @Override public int maxPhase() { return 2; }
    @Override public List<RegistryObject<SoundEvent>> music() { return AddonSounds.RHYA_MUSIC; }
    @Override protected void aiTick() { ReflectionAI.tick(this); }
    @Override protected void onPhase(int phase) { ReflectionAI.phase(this, phase); }
    @Override protected void onDying(int t) { ReflectionAI.dying(this, t); }
    @Override protected void onHurt(DamageSource src, float amount) { ReflectionAI.onHurt(this, src, amount); }

    @Override public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag t) { super.addAdditionalSaveData(t); if (owner() != null) t.putUUID("Owner", owner()); t.putInt("Pal", pal); }
    @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag t) { super.readAdditionalSaveData(t); if (t.hasUUID("Owner")) setOwner(t.getUUID("Owner")); pal = t.getInt("Pal"); }
}
