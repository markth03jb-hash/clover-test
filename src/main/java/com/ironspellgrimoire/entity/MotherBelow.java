package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.registry.AddonSounds;
import com.ironspellgrimoire.server.MotherAI;
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
 * The Mother Below (ASHFALL, Arc III): the Deepwardens' queen, woken under Ashwick. Her fight comes in two halves -
 * at half health she tears the cavern floor open and goes down (StoryArc3), and the party meets her again wherever
 * it chose to stand. Her fight: server/MotherAI.
 */
public class MotherBelow extends AddonBoss {
    public int party;
    /** 1: the first half (she goes down at half health); 2: the second. */
    public int half = 1;
    public int rakeCd = 30, pillarCd = 100, stabCd = 160, broodCd = 240, songCd = 220;
    public long bareUntil;

    public MotherBelow(EntityType<? extends MotherBelow> type, Level level) { super(type, level, "The Mother Below", BossEvent.BossBarColor.BLUE); }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 560).add(Attributes.ARMOR, 10).add(Attributes.ARMOR_TOUGHNESS, 3)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.MOVEMENT_SPEED, 0.24).add(Attributes.FOLLOW_RANGE, 48.0).add(Attributes.ATTACK_DAMAGE, 10.0);
    }

    @Override protected float damageTaken(DamageSource src) { return MotherAI.taken(this, src); }
    @Override protected float[] phaseLines() { return new float[]{0.5f, 0.25f}; }
    @Override public int maxPhase() { return 3; }
    @Override public List<RegistryObject<SoundEvent>> music() { return AddonSounds.BOSS_MUSIC; }
    @Override protected void aiTick() { MotherAI.tick(this); }
    @Override protected void onPhase(int phase) { MotherAI.phase(this, phase); }
    @Override protected void onDying(int t) { MotherAI.dying(this, t); }

    @Override public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("StoryParty", party); t.putInt("Half", half); }
    @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag t) { super.readAdditionalSaveData(t); party = t.getInt("StoryParty"); half = Math.max(1, t.getInt("Half")); }
}
