package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.registry.AddonSounds;
import com.ironspellgrimoire.server.CatherineAI;
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

/** Catherine, Witch of Curses: a boss (spawn egg or command; also on Wanted Posters and as a rift shade). His fight is in {@link CatherineAI}. */
public class Catherine extends AddonBoss {
    public Catherine(EntityType<? extends Catherine> type, Level level) { super(type, level, "Catherine, Witch of Curses", BossEvent.BossBarColor.PURPLE); }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 280).add(Attributes.ARMOR, 4)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0);
    }

    @Override protected float[] phaseLines() { return new float[]{0.5f}; }
    @Override public int maxPhase() { return 2; }
    @Override public List<RegistryObject<SoundEvent>> music() { return AddonSounds.CATHERINE_MUSIC; }
    @Override protected void aiTick() { CatherineAI.tick(this); }
    @Override protected void onPhase(int phase) { CatherineAI.phase(this, phase); }
    @Override protected void onDying(int t) { CatherineAI.dying(this, t); }
    @Override protected void onHurt(DamageSource src, float amount) { CatherineAI.hurt(this, src, amount); }
    @Override protected float damageTaken(DamageSource src) { return CatherineAI.taken(this, src); }
}
