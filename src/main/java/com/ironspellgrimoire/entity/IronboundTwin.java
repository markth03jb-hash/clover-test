package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.registry.AddonSounds;
import com.ironspellgrimoire.server.IronboundAI;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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
 * The Ironbound Twins, a tier II bounty: Ser Haldric (tower shield and mace) and Ser Oswin (greatsword), chained
 * wrist to wrist. They share one health bar - Haldric's: a blow on Oswin lands on it too. Their fight is in
 * server/IronboundAI.
 */
public class IronboundTwin extends AddonBoss {
    public UUID partner;
    public int swingCd = 20, bashCd = 80, sweepCd = 160, tautHits;
    public long staggerUntil;

    public IronboundTwin(EntityType<? extends IronboundTwin> type, Level level) {
        super(type, level, "The Ironbound Twins", BossEvent.BossBarColor.WHITE);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 380).add(Attributes.ARMOR, 12).add(Attributes.ARMOR_TOUGHNESS, 2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9).add(Attributes.MOVEMENT_SPEED, 0.29).add(Attributes.FOLLOW_RANGE, 48.0).add(Attributes.ATTACK_DAMAGE, 9.0);
    }

    /** Haldric carries the shared health (and the bounty); Oswin is his other half. */
    public boolean isMain() { return getType() == com.ironspellgrimoire.registry.AddonEntities.TWIN_HALDRIC.get(); }

    public IronboundTwin other() {
        return partner != null && level() instanceof ServerLevel sl && sl.getEntity(partner) instanceof IronboundTwin t ? t : null;
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (!level().isClientSide && !isMain() && !src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            IronboundTwin h = other();
            return h != null && h.isAlive() && h.hurt(src, amount);
        }
        return super.hurt(src, amount);
    }

    @Override protected boolean showBar() { return isMain(); }
    @Override protected float[] phaseLines() { return new float[]{0.5f}; }
    @Override public int maxPhase() { return 2; }
    @Override public List<RegistryObject<SoundEvent>> music() { return isMain() ? AddonSounds.BOSS_MUSIC : List.of(); }
    @Override protected void aiTick() { IronboundAI.tick(this); }
    @Override protected void onPhase(int phase) { IronboundAI.phase(this, phase); }
    @Override protected void onDying(int t) { IronboundAI.dying(this, t); }
    @Override protected float damageTaken(DamageSource src) { return IronboundAI.taken(this, src); }

    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); if (partner != null) t.putUUID("Partner", partner); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); if (t.hasUUID("Partner")) partner = t.getUUID("Partner"); }
}
