package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.registry.AddonSounds;
import com.ironspellgrimoire.server.MiraAI;
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

/** Mira Hollowell, the Debt-Keeper: a rival who fights with coins, contracts and a very old cat. The fight is in {@link MiraAI}; the rivals' shared rules are in {@link Rivals}. */
public class MiraHollowell extends Rival {
    /** The next deal; the next Foreclosure. */
    public long dealAt, forecloseAt;

    public MiraHollowell(EntityType<? extends MiraHollowell> type, Level level) { super(type, level, "Mira Hollowell", BossEvent.BossBarColor.YELLOW); }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 320).add(Attributes.ARMOR, 4)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0);
    }

    @Override public int rivalId() { return Rivals.MIRA; }
    @Override public List<RegistryObject<SoundEvent>> music() { return AddonSounds.RIVAL_MIRA_MUSIC; }
    @Override public void fight() { MiraAI.tick(this); }
    @Override public void phaseUp(int phase) { MiraAI.phase(this, phase); }
    @Override public float guard(DamageSource src) { return MiraAI.guard(this, src); }
}
