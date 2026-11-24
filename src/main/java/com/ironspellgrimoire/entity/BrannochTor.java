package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.registry.AddonSounds;
import com.ironspellgrimoire.server.BrannochAI;
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

/** Brannoch Tor, the Siege-Lord: a rival who brings his own legion, his standard and his walls. The fight is in {@link BrannochAI}; the rivals' shared rules are in {@link Rivals}. */
public class BrannochTor extends Rival {
    /** Testudo until this game time; the next wave; the standard-bearer (entity id, or -1). */
    public long testudoUntil, waveAt;
    public int bearer = -1;

    public BrannochTor(EntityType<? extends BrannochTor> type, Level level) { super(type, level, "Brannoch Tor", BossEvent.BossBarColor.RED); }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 420).add(Attributes.ARMOR, 10)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.MOVEMENT_SPEED, 0.26).add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0);
    }

    @Override public int rivalId() { return Rivals.BRANNOCH; }
    @Override public List<RegistryObject<SoundEvent>> music() { return AddonSounds.RIVAL_BRANNOCH_MUSIC; }
    @Override public void fight() { BrannochAI.tick(this); }
    @Override public void phaseUp(int phase) { BrannochAI.phase(this, phase); }
    @Override public float guard(DamageSource src) { return BrannochAI.guard(this, src); }
}
