package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.registry.AddonSounds;
import com.ironspellgrimoire.server.MemoryGuardianAI;
import com.ironspellgrimoire.server.MemoryStory;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
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
 * A Memory Guardian (server/MemoryPages): a memory the first binder wrote into the world, waiting at the hidden place a
 * finished chapter of Lost Memory Pages points to. Which one - the Copyist's Echo, Taddeus or Elowen herself - is its
 * chapter, kept in the boss's free synced number (flag) so the client can dress it. It belongs to the mage it waits
 * for, isn't saved (it gathers again when they come back), and its fight is in {@link MemoryGuardianAI}.
 */
public class MemoryGuardian extends AddonBoss {
    private UUID owner;
    public int away;

    public MemoryGuardian(EntityType<? extends MemoryGuardian> type, Level level) { super(type, level, "Memory Guardian", BossEvent.BossBarColor.YELLOW); }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 400).add(Attributes.ARMOR, 6)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0);
    }

    public int chapter() { return Math.max(0, Math.min(2, flag())); }
    public UUID owner() { return owner; }

    public void setup(int ch, UUID owner) {
        setFlag(ch);
        this.owner = owner;
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(new float[]{380, 560, 820}[ch]);
        getAttribute(Attributes.ARMOR).setBaseValue(5 + ch * 3);
        setHealth(getMaxHealth());
        setBarName(Component.literal(MemoryStory.GUARDIANS[ch]));
    }

    @Override protected float[] phaseLines() { return chapter() == 2 ? new float[]{0.66f, 0.33f} : new float[]{0.5f}; }
    @Override public int maxPhase() { return chapter() == 2 ? 3 : 2; }
    @Override public List<RegistryObject<SoundEvent>> music() { return chapter() == 0 ? AddonSounds.RHYA_MUSIC : chapter() == 1 ? AddonSounds.LICHT_MUSIC : AddonSounds.BOSS_MUSIC; }
    @Override protected void aiTick() { MemoryGuardianAI.tick(this); }
    @Override protected void onPhase(int phase) { MemoryGuardianAI.phase(this, phase); }
    @Override protected void onDying(int t) { MemoryGuardianAI.dying(this, t); }
    @Override protected void onHurt(DamageSource src, float amount) { MemoryGuardianAI.onHurt(this, src, amount); }

    /** A memory: it isn't kept with the world - it gathers again when its mage comes back. */
    @Override public boolean shouldBeSaved() { return false; }
}
