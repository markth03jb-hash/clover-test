package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.registry.AddonSounds;
import com.ironspellgrimoire.server.RevenantAI;
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

/** Revenant the Revenant: a boss (spawn egg or command; also on Wanted Posters and as a rift shade). His fight is in {@link RevenantAI}. */
public class Revenant extends AddonBoss {
    public Revenant(EntityType<? extends Revenant> type, Level level) { super(type, level, "The Revenant", BossEvent.BossBarColor.GREEN); }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 200).add(Attributes.ARMOR, 6)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0);
    }

    @Override protected float[] phaseLines() { return new float[]{0.5f}; }
    @Override public int maxPhase() { return 2; }
    @Override public List<RegistryObject<SoundEvent>> music() { return AddonSounds.REVENANT_MUSIC; }
    @Override protected void aiTick() { RevenantAI.tick(this); }
    @Override protected void onPhase(int phase) { RevenantAI.phase(this, phase); }
    @Override protected void onDying(int t) { RevenantAI.dying(this, t); }
    @Override protected void onHurt(DamageSource src, float amount) { RevenantAI.onHurt(this, src, amount); }
    @Override protected float damageTaken(DamageSource src) { return RevenantAI.taken(this, src); }
}
