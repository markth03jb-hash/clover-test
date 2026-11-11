package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.registry.AddonSounds;
import com.ironspellgrimoire.server.LichtAI;
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

/** Licht, Leader of the Elves: a boss (spawn egg or command; also on Wanted Posters and as a rift shade). His fight is in {@link LichtAI}. */
public class Licht extends AddonBoss {
    public Licht(EntityType<? extends Licht> type, Level level) { super(type, level, "Licht, Leader of the Elves", BossEvent.BossBarColor.WHITE); }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 520).add(Attributes.ARMOR, 10)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.MOVEMENT_SPEED, 0.36).add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0);
    }

    @Override protected float[] phaseLines() { return new float[]{0.7f, 0.4f, 0.15f}; }
    @Override public int maxPhase() { return 4; }
    @Override public List<RegistryObject<SoundEvent>> music() { return AddonSounds.LICHT_MUSIC; }
    @Override protected void aiTick() { LichtAI.tick(this); }
    @Override protected void onPhase(int phase) { LichtAI.phase(this, phase); }
    @Override protected void onDying(int t) { LichtAI.dying(this, t); }
    @Override protected void onHurt(DamageSource src, float amount) { LichtAI.onHurt(this, src, amount); }
    @Override protected float damageTaken(DamageSource src) { return LichtAI.taken(this, src); }
}
