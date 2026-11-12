package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.registry.AddonSounds;
import com.ironspellgrimoire.server.DanteAI;
import java.util.List;
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
 * Dante: a three-phase boss of body magic and gravity (spawn egg or command). His body mends itself from his mana; run
 * that mana dry and he can't. His fight is in {@link DanteAI}. His skin is a plain placeholder (textures/entity/dante.png).
 */
public class Dante extends AddonBoss {
    /** Body-magic mana (server side): every wound he mends spends it; at 0 he's exhausted for a while. */
    public float mana = DanteAI.MANA;
    public int exhausted, mendCheck;
    public float recent;

    public Dante(EntityType<? extends Dante> type, Level level) { super(type, level, "Dante", BossEvent.BossBarColor.RED); }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 420).add(Attributes.ARMOR, 8)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0);
    }

    @Override protected float[] phaseLines() { return new float[]{0.66f, 0.33f}; }
    @Override public int maxPhase() { return 3; }
    @Override public List<RegistryObject<SoundEvent>> music() { return AddonSounds.LICHT_MUSIC; }
    @Override protected void aiTick() { DanteAI.tick(this); }
    @Override protected void onPhase(int phase) { DanteAI.phase(this, phase); }
    @Override protected void onDying(int t) { DanteAI.dying(this, t); }
    @Override protected void onHurt(DamageSource src, float amount) { DanteAI.onHurt(this, src, amount); }
    @Override protected float damageTaken(DamageSource src) { return DanteAI.taken(this, src); }

    @Override public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag t) { super.addAdditionalSaveData(t); t.putFloat("BodyMana", mana); }
    @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag t) { super.readAdditionalSaveData(t); if (t.contains("BodyMana")) mana = t.getFloat("BodyMana"); }
}
