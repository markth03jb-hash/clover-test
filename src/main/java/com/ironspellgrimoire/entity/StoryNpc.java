package com.ironspellgrimoire.entity;

import com.ironspellgrimoire.server.StoryArc1;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The story's people (ASHFALL): Corvin Hale and Ilsa Marr. Nothing hurts them. Talk to them (use on them) for the
 * scene the story is at. Corvin walks with the party's leader once the story has met him (StoryArc1 moves him);
 * the ones standing in a story place belong to that scene.
 */
public class StoryNpc extends PathfinderMob {
    public final String who;
    /** The party this one belongs to, and whether it walks with them (true) or stands in a scene. */
    public int party;
    public boolean follower;

    public StoryNpc(EntityType<? extends StoryNpc> type, Level level, String who) {
        super(type, level);
        this.who = who;
        this.setPersistenceRequired();
        this.setInvulnerable(true);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override public boolean hurt(DamageSource src, float amount) {
        if (src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(src, amount);
        return false;
    }

    @Override protected InteractionResult mobInteract(Player p, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (p instanceof ServerPlayer sp) StoryArc1.talkTo(sp, this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 10 == 0) StoryArc1.npcTick(this);
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(net.minecraft.world.entity.Entity e) {}
    @Override public boolean canBeLeashed(Player p) { return false; }

    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("StoryParty", party); t.putBoolean("Follower", follower); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); party = t.getInt("StoryParty"); follower = t.getBoolean("Follower"); }
}
