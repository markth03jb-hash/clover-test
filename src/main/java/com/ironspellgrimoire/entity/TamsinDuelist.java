package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.registry.AddonSounds;
import com.ironspellgrimoire.server.TamsinAI;
import java.util.List;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;

/** Tamsin Reed in the Ember Trials (ASHFALL, Arc II): a hedge-mage's bout. She yields; the Wardens come for her. Her fight: server/TamsinAI. */
public class TamsinDuelist extends YieldingBoss {
    public int boltCd = 30, rootCd = 120, blinkCd = 80;

    public TamsinDuelist(EntityType<? extends TamsinDuelist> type, Level level) { super(type, level, "Tamsin Reed", BossEvent.BossBarColor.GREEN); }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 180).add(Attributes.ARMOR, 2).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 40.0).add(Attributes.ATTACK_DAMAGE, 4.0);
    }

    @Override protected void onYield() { TamsinAI.yielded(this); }
    @Override protected float[] phaseLines() { return new float[]{0.5f}; }
    @Override public int maxPhase() { return 2; }
    @Override public List<RegistryObject<SoundEvent>> music() { return AddonSounds.BOSS_MUSIC; }
    @Override protected void aiTick() { TamsinAI.tick(this); }
    @Override protected void onPhase(int phase) { TamsinAI.phase(this, phase); }
    @Override protected void onDying(int t) {}
}
