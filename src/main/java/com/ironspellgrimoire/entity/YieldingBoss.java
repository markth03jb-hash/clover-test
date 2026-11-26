package com.ironspellgrimoire.entity;

import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * A story boss who isn't killed by a blow (ASHFALL): brought to the last of their health they yield and kneel, and
 * the story decides what happens next (Father Sallow, Tamsin Reed). Only a killing the story itself orders - one
 * that bypasses invulnerability - ends them.
 */
public abstract class YieldingBoss extends AddonBoss {
    public static final int YIELDED = 1;
    public int party;

    protected YieldingBoss(EntityType<? extends YieldingBoss> type, Level level, String name, BossEvent.BossBarColor color) { super(type, level, name, color); }

    public boolean yielded() { return (flag() & YIELDED) != 0; }
    public void setYielded(boolean y) { setFlag(y ? flag() | YIELDED : flag() & ~YIELDED); }
    /** Called once, when they yield. */
    protected abstract void onYield();

    @Override
    protected void actuallyHurt(DamageSource src, float amount) {
        boolean bypass = src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
        super.actuallyHurt(src, amount);
        if (bypass || level().isClientSide || getHealth() > 1.01f) return;
        setHealth(1f);
        if (!yielded()) { setYielded(true); getNavigation().stop(); onYield(); }
    }

    @Override public boolean hurt(DamageSource src, float amount) {
        if (yielded() && !src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(src, amount);
    }

    @Override public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("StoryParty", party); }
    @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag t) { super.readAdditionalSaveData(t); party = t.getInt("StoryParty"); }
}
