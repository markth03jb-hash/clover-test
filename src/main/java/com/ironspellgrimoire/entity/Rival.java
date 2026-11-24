package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.server.Rivals;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A rival (server/Rivals): a human mage who comes looking for you, fights inside a ring, remembers you, and yields
 * instead of dying. Three fighting phases; the last line (3% health) is where they yield. Each win against them
 * raises their rank for the rematch (up to 3): more health, harder blows and one more move per rank.
 */
public abstract class Rival extends AddonBoss {
    /** Bit in {@link #flag()}: kneeling, yielded. The fights use the low bits. */
    public static final int YIELDED = 1 << 10;

    public int rank;
    public Vec3 arena;
    public UUID challenger, partner;
    public int away, yieldT, idle;
    public boolean yielded, met, over;
    public long lastMove;

    protected Rival(EntityType<? extends Rival> type, Level level, String name, BossEvent.BossBarColor color) {
        super(type, level, name, color);
    }

    /** Which rival (server/Rivals: SEREN, BRANNOCH, TWINS, MIRA). */
    public abstract int rivalId();
    /** The fight itself, run while the challenger stands inside the ring. */
    public abstract void fight();
    /** A new phase began (2 or 3). */
    public abstract void phaseUp(int phase);
    /** Damage multiplier from the fight's own guards (a parry, a shield wall...). */
    public float guard(DamageSource src) { return 1f; }

    @Override protected float[] phaseLines() { return new float[]{0.66f, 0.33f, 0.03f}; }
    @Override public int maxPhase() { return 3; }
    @Override protected final void aiTick() { Rivals.tick(this); }
    @Override protected final void onPhase(int phase) { if (phase > 3) Rivals.yieldNow(this); else Rivals.phase(this, phase); }
    @Override protected void onDying(int t) {}
    @Override protected final float damageTaken(DamageSource src) { return Rivals.taken(this, src); }
    @Override protected void onHurt(DamageSource src, float amount) { Rivals.hurtBy(this, src, amount); }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (yielded) Rivals.yieldTick(this);
    }

    /** Sets the rank (0-3): +30% health per rank, healed to full. */
    public void setRank(int r) {
        rank = Math.max(0, Math.min(3, r));
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        UUID id = UUID.fromString("7a1e0000-0000-4000-8000-0000000000a1");
        if (hp != null) {
            hp.removeModifier(id);
            if (rank > 0) hp.addPermanentModifier(new AttributeModifier(id, "Rival rank", 0.3 * rank, AttributeModifier.Operation.MULTIPLY_BASE));
        }
        setHealth(getMaxHealth());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("Rank", rank);
        t.putBoolean("Met", met);
        if (arena != null) { t.putDouble("AX", arena.x); t.putDouble("AY", arena.y); t.putDouble("AZ", arena.z); }
        if (challenger != null) t.putUUID("Challenger", challenger);
        if (partner != null) t.putUUID("Partner", partner);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        rank = t.getInt("Rank");
        met = t.getBoolean("Met");
        if (t.contains("AX")) arena = new Vec3(t.getDouble("AX"), t.getDouble("AY"), t.getDouble("AZ"));
        if (t.hasUUID("Challenger")) challenger = t.getUUID("Challenger");
        if (t.hasUUID("Partner")) partner = t.getUUID("Partner");
    }
}
