package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.registry.AddonSounds;
import com.ironspellgrimoire.server.SallowAI;
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
 * Father Sallow (ASHFALL, Arc I): the priest who cuts the brands out. He isn't killed by a blow - brought to the
 * last of his health he yields and kneels, and the party decides what's done with him (StoryArc1). His fight is in
 * server/SallowAI.
 */
public class FatherSallow extends YieldingBoss {
    public int knifeCd = 40, lanternCd = 100, callCd = 200, pillarCd = 160;

    public FatherSallow(EntityType<? extends FatherSallow> type, Level level) {
        super(type, level, "Father Sallow", BossEvent.BossBarColor.WHITE);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 320).add(Attributes.ARMOR, 6).add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.MOVEMENT_SPEED, 0.27).add(Attributes.FOLLOW_RANGE, 48.0).add(Attributes.ATTACK_DAMAGE, 7.0);
    }

    @Override protected void onYield() { SallowAI.yieldNow(this); }

    @Override protected float[] phaseLines() { return new float[]{0.66f, 0.33f}; }
    @Override public int maxPhase() { return 3; }
    @Override public List<RegistryObject<SoundEvent>> music() { return AddonSounds.BOSS_MUSIC; }
    @Override protected void aiTick() { SallowAI.tick(this); }
    @Override protected void onPhase(int phase) { SallowAI.phase(this, phase); }
    @Override protected void onDying(int t) { SallowAI.dying(this, t); }

}
