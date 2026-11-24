package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.registry.AddonSounds;
import com.ironspellgrimoire.server.SerenAI;
import com.ironspellgrimoire.server.Rivals;
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

/** Seren Vale, the Glass Duelist: a rival who fights with a blade of light and never stands where you aim. The fight is in {@link SerenAI}; the rivals' shared rules are in {@link Rivals}. */
public class SerenVale extends Rival {
    /** Mirror Parry until this game time; the next sidestep from this game time; White Noons cast. */
    public long parryUntil, dodgeAt, noonAt;
    public int noons;

    public SerenVale(EntityType<? extends SerenVale> type, Level level) { super(type, level, "Seren Vale", BossEvent.BossBarColor.WHITE); }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 300).add(Attributes.ARMOR, 4)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6).add(Attributes.MOVEMENT_SPEED, 0.34).add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0);
    }

    @Override public int rivalId() { return Rivals.SEREN; }
    @Override public List<RegistryObject<SoundEvent>> music() { return AddonSounds.RIVAL_SEREN_MUSIC; }
    @Override public void fight() { SerenAI.tick(this); }
    @Override public void phaseUp(int phase) { SerenAI.phase(this, phase); }
    @Override public float guard(DamageSource src) { return SerenAI.guard(this, src); }
}
