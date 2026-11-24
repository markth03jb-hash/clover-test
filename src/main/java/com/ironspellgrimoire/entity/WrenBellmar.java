package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.registry.AddonSounds;
import com.ironspellgrimoire.server.TwinsAI;
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

/** Wren Bellmar, the younger twin: the bells of the pair. The fight is in {@link TwinsAI}; the rivals' shared rules are in {@link Rivals}. */
public class WrenBellmar extends Rival {
    /** Grieving (the other twin is down) until this game time. */
    public long griefUntil;

    public WrenBellmar(EntityType<? extends WrenBellmar> type, Level level) { super(type, level, "Wren Bellmar", BossEvent.BossBarColor.BLUE); }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 200).add(Attributes.ARMOR, 3)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0);
    }

    @Override public int rivalId() { return Rivals.TWINS; }
    @Override public List<RegistryObject<SoundEvent>> music() { return AddonSounds.RIVAL_TWINS_MUSIC; }
    @Override public void fight() { TwinsAI.tick(this); }
    @Override public void phaseUp(int phase) { TwinsAI.phase(this, phase); }
    @Override public float guard(DamageSource src) { return TwinsAI.guard(this, src); }
}
