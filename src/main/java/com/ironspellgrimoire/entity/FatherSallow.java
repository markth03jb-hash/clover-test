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
public class FatherSallow extends AddonBoss {
    public static final int YIELDED = 1;
    public int knifeCd = 40, lanternCd = 100, callCd = 200, pillarCd = 160;
    public int party;

    public FatherSallow(EntityType<? extends FatherSallow> type, Level level) {
        super(type, level, "Father Sallow", BossEvent.BossBarColor.WHITE);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 320).add(Attributes.ARMOR, 6).add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.MOVEMENT_SPEED, 0.27).add(Attributes.FOLLOW_RANGE, 48.0).add(Attributes.ATTACK_DAMAGE, 7.0);
    }

    public boolean yielded() { return (flag() & YIELDED) != 0; }
    public void setYielded(boolean y) { setFlag(y ? flag() | YIELDED : flag() & ~YIELDED); }

    @Override
    protected void actuallyHurt(DamageSource src, float amount) {
        boolean bypass = src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
        super.actuallyHurt(src, amount);
        if (bypass || level().isClientSide || getHealth() > 1.01f) return;
        setHealth(1f);   // he doesn't die of a blow: he yields
        if (!yielded()) SallowAI.yieldNow(this);
    }

    @Override public boolean hurt(DamageSource src, float amount) {
        if (yielded() && !src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(src, amount);
    }

    @Override protected float[] phaseLines() { return new float[]{0.66f, 0.33f}; }
    @Override public int maxPhase() { return 3; }
    @Override public List<RegistryObject<SoundEvent>> music() { return AddonSounds.BOSS_MUSIC; }
    @Override protected void aiTick() { SallowAI.tick(this); }
    @Override protected void onPhase(int phase) { SallowAI.phase(this, phase); }
    @Override protected void onDying(int t) { SallowAI.dying(this, t); }

    @Override public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("StoryParty", party); }
    @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag t) { super.readAdditionalSaveData(t); party = t.getInt("StoryParty"); }
}
