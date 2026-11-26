package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.registry.AddonSounds;
import com.ironspellgrimoire.server.VeyAI;
import java.util.List;
import java.util.UUID;
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
 * Magistrate Vey (ASHFALL, Arc II): the man who runs the Ember Trials and keeps what the losers leave behind. He
 * writes down the blows that hurt him and reads them back. His fight: server/VeyAI.
 */
public class MagistrateVey extends AddonBoss {
    public int party;
    public int recitalCd = 160, sealCd = 90, strikeCd = 30, callCd = 300, judgeCd = 200;
    /** The ledger: the hardest blow in the last 8 s, and whose it was. */
    public float written;
    public UUID writer;
    public long writtenAt;

    public MagistrateVey(EntityType<? extends MagistrateVey> type, Level level) { super(type, level, "Magistrate Vey", BossEvent.BossBarColor.YELLOW); }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 420).add(Attributes.ARMOR, 8).add(Attributes.KNOCKBACK_RESISTANCE, 0.85)
                .add(Attributes.MOVEMENT_SPEED, 0.26).add(Attributes.FOLLOW_RANGE, 48.0).add(Attributes.ATTACK_DAMAGE, 8.0);
    }

    @Override protected void onHurt(DamageSource src, float amount) { VeyAI.write(this, src, amount); }
    @Override protected float[] phaseLines() { return new float[]{0.66f, 0.33f}; }
    @Override public int maxPhase() { return 3; }
    @Override public List<RegistryObject<SoundEvent>> music() { return AddonSounds.BOSS_MUSIC; }
    @Override protected void aiTick() { VeyAI.tick(this); }
    @Override protected void onPhase(int phase) { VeyAI.phase(this, phase); }
    @Override protected void onDying(int t) { VeyAI.dying(this, t); }

    @Override public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("StoryParty", party); }
    @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag t) { super.readAdditionalSaveData(t); party = t.getInt("StoryParty"); }
}
