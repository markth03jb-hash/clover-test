package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.entity.AddonBoss;
import com.ironspellgrimoire.entity.ThrownHungerBlade;
import com.ironspellgrimoire.item.HungerBladeItem;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.HungerPackets;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Hunger Blade Grimoire: a living sword drawn from the book into your hand. It grows on what it kills - Growth
 * unlocks new swords down a branching tree (3 base stages, then 4 paths, each splitting two ways, each with its own
 * ascended form: 23 swords) - and you carry four of them, switched on the G wheel. It hungers: fed, it steadies you;
 * hungry, it hits harder, whispers and pulls you toward prey; starved, it bites you. Fighting together builds a Bond:
 * the blade speaks, at Bond 3 it can be thrown (it comes back), and at Bond 5 Unleash brings on the Awakened Pact.
 *
 * Pages: Draw (summon / put away), Gnash (a lunging bite; sneak at Bond 3: throw it), Feast (a cleave all round you
 * that heals), Unleash (each path's own ultimate), The Blade (the growth tree and your four swords).
 * No status effects (attribute modifiers only), nothing breaks or changes blocks.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class HungerFx {
    private HungerFx() {}

    static final String ROOT = "isg_hunger";
    public static final int FORMS = 23;
    public static final String[] NAMES = {"Fang", "Gnaw", "Maw Blade",
            "Marrow Cleaver", "Ribcage Greatsword", "Ossuary Colossus", "Spine Saw", "Tailbone Reaper",
            "Shard Blade", "Prism Rapier", "Refraction Lance", "Geode Breaker", "Cluster Colossus",
            "Umbral Edge", "Eclipse Sickle", "Nightfall Crescent", "Twin Fang", "Hydra Edge",
            "Ember Brand", "Flamberge", "Pyre Wing", "Furnace Cleaver", "Magma Maw"};
    public static final String[] LINE_NAMES = {"Base", "Bone", "Crystal", "Shadow", "Flame"};
    /** The form each one grows from (-1: the first). */
    public static final int[] PARENT = {-1, 0, 1, 2, 3, 4, 3, 6, 2, 8, 9, 8, 11, 2, 13, 14, 13, 16, 2, 18, 19, 18, 21};
    public static final int[] STAGE = {1, 2, 3, 4, 5, 6, 5, 6, 4, 5, 6, 5, 6, 4, 5, 6, 5, 6, 4, 5, 6, 5, 6};
    /** Growth to grow a sword of each stage (index = stage). */
    public static final int[] COST = {0, 0, 20, 40, 80, 140, 220};
    public static final float[] DMG = {5, 7, 9, 12, 15, 20, 13, 17, 10, 11, 15, 15, 19, 11, 13, 17, 12, 15, 11, 14, 17, 16, 19};
    /** Added to the base attack speed of 4 (a vanilla sword's is -2.4). */
    public static final float[] SPEED = {-1.4f, -2.0f, -2.4f, -2.8f, -3.0f, -3.2f, -2.6f, -2.8f, -2.2f, -1.6f, -2.2f, -3.0f, -3.1f,
            -2.4f, -2.4f, -2.7f, -2.0f, -2.3f, -2.5f, -2.6f, -2.7f, -3.0f, -3.1f};
    public static final float[] REACH = {0, 0, 0.5f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f, 0.5f, 1.5f, 3.0f, 0.5f, 1.0f,
            0.5f, 1.5f, 2.5f, 0.5f, 1.0f, 0.5f, 1.5f, 2.0f, 0.5f, 1.0f};
    public static final String[] PASSIVE = {
            "Each hit drinks a little of what it cuts.", "Each hit drinks a little of what it cuts.", "Each hit drinks a little of what it cuts.",
            "Bone: hits crack armour.", "Bone: hits crack armour; each hit splashes onto foes near the one you struck.",
            "Bone: hits crack armour; each hit splashes onto foes near the one you struck.",
            "Bone: hits crack armour; long reach, and each hit sweeps the foes in front of you.", "Bone: hits crack armour; longer reach, and each hit sweeps the foes in front of you.",
            "Crystal: hits may shatter for a critical.", "Crystal: more criticals; each hit runs through to the foe behind.",
            "Crystal: more criticals; each hit runs through to the foe behind.",
            "Crystal: hits may shatter for a critical; heavy hits stagger and slow.", "Crystal: hits may shatter for a critical; heavy hits stagger and slow.",
            "Shadow: hits heal you.", "Shadow: hits heal you; each cut sweeps a wide arc.", "Shadow: hits heal you; each cut sweeps a wide arc.",
            "Shadow: hits heal you; each hit bites again a moment later.", "Shadow: hits heal you; each hit bites twice more.",
            "Flame: hits burn on.", "Flame: hits burn on; every third hit sends a wave of flame ahead.", "Flame: hits burn on; every third hit sends a wave of flame ahead.",
            "Flame: hits burn on; every fourth hit bursts round the foe.", "Flame: hits burn on; every fourth hit bursts round the foe."};

    /** 0 base, 1 bone, 2 crystal, 3 shadow, 4 flame. */
    public static int line(int f) { return f < 3 ? 0 : 1 + (f - 3) / 5; }
    /** The branch's on-hit kind: 0 none (base or a stage-4), 1-8 (bone A, bone B, crystal A, crystal B, shadow A, shadow B, flame A, flame B). */
    static int branch(int f) {
        if (f < 3) return 0;
        int k = (f - 3) % 5;
        if (k == 0) return 0;
        return (line(f) - 1) * 2 + (k <= 2 ? 1 : 2);
    }
    static final int[] PAL = {Fx.CRIMSON, Fx.SILVER, Fx.CRIMSON, Fx.YAMI, Fx.FIRE};
    public static final int[] BOND_AT = {0, 10, 30, 70, 130, 220};

    // ------------------------------------------------------------------ state
    static CompoundTag root(Player p) {
        CompoundTag pd = p.getPersistentData();
        if (!pd.contains(Player.PERSISTED_NBT_TAG)) pd.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag per = pd.getCompound(Player.PERSISTED_NBT_TAG);
        if (!per.contains(ROOT)) {
            CompoundTag r = new CompoundTag();
            r.putInt("unlocked", 1); r.putIntArray("load", new int[]{0, -1, -1, -1}); r.putFloat("hunger", 60f);
            per.put(ROOT, r);
        }
        return per.getCompound(ROOT);
    }

    public static boolean equipped(Player p) {
        var b = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
        return b != null && b.is(AddonItemRegistry.HUNGER_GRIMOIRE.get());
    }

    public static boolean isBlade(ItemStack s) { return !s.isEmpty() && s.is(AddonItemRegistry.HUNGER_BLADE.get()); }
    public static boolean drawn(Player p) { return isBlade(p.getMainHandItem()); }
    public static boolean unlocked(Player p, int f) { return f >= 0 && f < FORMS && (root(p).getInt("unlocked") & (1 << f)) != 0; }
    public static int growth(Player p) { return root(p).getInt("growth"); }
    public static float hunger(Player p) { return root(p).getFloat("hunger"); }
    public static int bondXp(Player p) { return root(p).getInt("bond"); }
    public static int bond(Player p) { int x = bondXp(p), l = 0; while (l < 5 && x >= BOND_AT[l + 1]) l++; return l; }
    static int[] load(Player p) {
        int[] l = root(p).getIntArray("load");
        return l.length == 4 ? l : new int[]{0, -1, -1, -1};
    }
    static int sel(Player p) { return Math.max(0, Math.min(3, root(p).getInt("sel"))); }
    /** The sword in the selected slot (the Fang if the slot's empty). */
    public static int current(Player p) { int f = load(p)[sel(p)]; return f >= 0 && unlocked(p, f) ? f : 0; }
    /** 3 well fed, 2 content, 1 hungry, 0 starved. */
    public static int mood(float h) { return h >= 70 ? 3 : h >= 30 ? 2 : h >= 10 ? 1 : 0; }
    static int heldForm(Player p) { return drawn(p) ? HungerBladeItem.form(p.getMainHandItem()) : current(p); }
    static float bladeDmg(Player p) { return DMG[heldForm(p)]; }
    static long now(Player p) { return p.level().getGameTime(); }
    static boolean pact(Player p) { return root(p).getLong("pact") > now(p); }

    static void feed(Player p, float h) {
        CompoundTag r = root(p);
        r.putFloat("hunger", Math.max(0f, Math.min(100f, r.getFloat("hunger") + h)));
    }

    static void say(ServerPlayer p, String s) {
        p.sendSystemMessage(Component.literal("The blade: ").withStyle(ChatFormatting.DARK_RED).append(Component.literal(s).withStyle(ChatFormatting.RED, ChatFormatting.ITALIC)));
    }

    static void tell(ServerPlayer p, String s) { p.displayClientMessage(Component.literal(s).withStyle(ChatFormatting.GRAY), true); }

    // ------------------------------------------------------------------ voice
    static final String[] WHISPERS = {"...feed me...", "There. That one. Can you smell it?", "So empty. Find me something warm.",
            "Don't make me wait.", "I can hear its heart from here.", "We could be eating."};
    static final String[] STARVED = {"You'll do, then.", "If nothing else will feed me...", "Hold still."};
    static final String[] BOND_LINES = {"",
            "You swing me well enough. Keep doing it.",
            "I know your grip now. I think I like it.",
            "Throw me, if you must. I always come back to you. (Bond 3: sneak and Gnash to throw the blade)",
            "We hunt the same way now, you and I.",
            "We are one hunger. Call on me and I will answer with all of it. (Bond 5: Unleash wakes the Awakened Pact)"};

    // ------------------------------------------------------------------ the pages
    /** Draw: the blade into your empty main hand - or back into the book. */
    public static void draw(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        if (drawn(p)) {
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER, 0.8f, 0.7f);
            tell(p, "The blade sinks back into the book");
            return;
        }
        if (root(p).getBoolean("thrown")) { tell(p, "The blade is still flying"); return; }
        if (!p.getMainHandItem().isEmpty()) { p.displayClientMessage(Component.literal("Your main hand must be empty").withStyle(ChatFormatting.RED), true); return; }
        give(p, current(p));
        Vec3 hand = p.getEyePosition().add(p.getLookAngle().scale(0.8)).add(0, -0.4, 0);
        Fx.spark(sl, PAL[line(current(p))], hand, 1.2f);
        FxSend.ring(sl, PAL[line(current(p))], p.position().add(0, 0.1, 0), 0.4f, 1.6f, 0.08f, 8);
        OverdriveFx.play(sl, p.position(), "blade_draw", 1f, 0.8f);
        OverdriveFx.play(sl, p.position(), "jaws", 0.5f, 1.3f);
    }

    static void give(ServerPlayer p, int f) {
        p.setItemInHand(InteractionHand.MAIN_HAND, HungerBladeItem.of(AddonItemRegistry.HUNGER_BLADE.get(), f));
    }

    public static boolean canStrike(Player p) { return drawn(p); }

    /** Gnash: lunge and bite the first foe in your way. At Bond 3, sneak to throw the blade instead. */
    public static void gnash(ServerPlayer p, float power) {
        if (!drawn(p)) { tell(p, "Draw the blade first"); return; }
        if (p.isShiftKeyDown() && bond(p) >= 3) { throwBlade(p); return; }
        ServerLevel sl = p.serverLevel();
        Vec3 d = flat(p.getLookAngle());
        int pal = PAL[line(heldForm(p))];
        p.setDeltaMovement(d.scale(1.5).add(0, 0.22, 0));
        p.hurtMarked = true;
        OverdriveFx.play(sl, p.position(), "rush", 0.9f, 1.1f);
        float dmg = bladeDmg(p) * 1.3f * (power / 10f);
        final boolean[] bit = {false};
        for (int k = 1; k <= 7; k++) {
            AstaFx.later(k, () -> {
                if (bit[0] || !alive(p)) return;
                for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(1.6))) {
                    bit[0] = true;
                    Vec3 c = mid(t), side = rightOf(d);
                    FxSend.crescent(sl, pal, c.add(0, 0.5, 0), d, side, 1.1f, 1.4f, 5, 0.5f);
                    FxSend.crescent(sl, pal, c.add(0, -0.5, 0), d, side.scale(-1), 1.1f, 1.4f, 5, 0.5f);
                    OverdriveFx.play(sl, c, "jaws", 1.1f, 1f);
                    AstaFx.damage(p, t, dmg, "gnash");
                    p.heal(2f);
                    feed(p, 4f);
                    break;
                }
            });
        }
        FxSend.trail(sl, pal, p.position().add(0, 1, 0), p.position().add(0, 1, 0).add(d.scale(4)), 0.4f, 6);
    }

    /** Feast: a cleave all the way round you; every foe it hits heals you and feeds the blade. */
    public static void feast(ServerPlayer p, float power) {
        if (!drawn(p)) { tell(p, "Draw the blade first"); return; }
        ServerLevel sl = p.serverLevel();
        int f = heldForm(p), pal = PAL[line(f)];
        float r = 3f + 0.4f * STAGE[f];
        Vec3 c = p.position().add(0, 1, 0);
        FxSend.wall(sl, pal, c, new Vec3(1, 0, 0), new Vec3(0, 0, 1), r * 0.5f, r, -180, 180, 0.6f, 8, 1, 1.5f);
        FxSend.ring(sl, pal, p.position().add(0, 0.1, 0), 0.5f, r, 0.1f, 9);
        OverdriveFx.play(sl, c, "slash", 1.2f, 0.8f);
        float dmg = bladeDmg(p) * (power / 10f);
        int hits = 0;
        for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(r, 1.5, r))) {
            if (t.distanceTo(p) > r + 0.5) continue;
            AstaFx.damage(p, t, dmg, "feast");
            Fx.spark(sl, pal, mid(t), 1f);
            hits++;
        }
        if (hits > 0) {
            p.heal(hits * (mood(hunger(p)) <= 1 ? 2f : 1f));
            feed(p, 2f * hits);
            OverdriveFx.play(sl, c, "blood_splash", 0.8f, 1f);
        }
    }

    /** Unleash: the blade's ultimate - its own for each path, stronger with every stage. At Bond 5 it wakes the Awakened Pact. */
    public static void unleash(ServerPlayer p, float power) {
        if (!drawn(p)) { tell(p, "Draw the blade first"); return; }
        ServerLevel sl = p.serverLevel();
        int f = heldForm(p), ln = line(f), pal = PAL[ln];
        float dmg = bladeDmg(p) * (1.5f + 0.25f * STAGE[f]) * (power / 10f);
        Vec3 eye = p.getEyePosition(), d = flat(p.getLookAngle()), feet = p.position();
        switch (ln) {
            case 1 -> {   // Ossuary Eruption: rings of bone spires bursting up round you, wave on wave
                OverdriveFx.play(sl, feet, "stomp", 1.2f, 0.7f);
                for (int w = 0; w < 3; w++) {
                    final float rr = 2.5f + 2.5f * w;
                    AstaFx.later(w * 6, () -> {
                        if (!alive(p)) return;
                        for (int k = 0; k < 10 + 4 * (int) (rr / 2.5f); k++) {
                            double a = Math.PI * 2 * k / (10 + 4 * (int) (rr / 2.5f));
                            Vec3 b = feet.add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
                            Fx.pillar(sl, Fx.SILVER, b, 1.8f + 0.4f * STAGE[f] / 2f, 0.25f, 8);
                        }
                        FxSend.ring(sl, Fx.SILVER, feet.add(0, 0.1, 0), rr - 1.2f, rr + 0.8f, 0.15f, 8);
                        for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(rr + 1, 2, rr + 1))) {
                            double dist = t.distanceTo(p);
                            if (dist < rr - 2.5 || dist > rr + 1.2) continue;
                            AstaFx.damage(p, t, dmg * 0.6f, "unleash");
                            t.setDeltaMovement(t.getDeltaMovement().add(0, 0.5, 0)); t.hurtMarked = true;
                        }
                        OverdriveFx.play(sl, feet, "impact", 0.9f, 0.8f + 0.1f * rr / 2.5f);
                    });
                }
            }
            case 2 -> {   // Shard Nova: sixteen crystal shards fired outward, each running through everything for 12 blocks
                OverdriveFx.play(sl, feet, "ice_shatter", 1.2f, 1.2f);
                java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
                for (int k = 0; k < 16; k++) {
                    Vec3 dir = rotY(d, Math.PI * 2 * k / 16);
                    Vec3 a = eye.add(0, -0.4, 0), b = clip(sl, p, a, a.add(dir.scale(12)));
                    FxSend.beam(sl, Fx.CRIMSON, a, b, 0.22f, 8);
                    for (LivingEntity t : enemies(sl, p, new net.minecraft.world.phys.AABB(a, b).inflate(1.2))) {
                        if (mid(t).distanceTo(closestOnSegment(a, b, mid(t))) > 1.2 || !hit.add(t)) continue;
                        AstaFx.damage(p, t, dmg * 0.7f, "unleash");
                        Fx.impact(sl, Fx.CRIMSON, mid(t), 1.2f);
                    }
                }
            }
            case 3 -> {   // Umbral Rend: you vanish and reappear up to 10 blocks ahead, everything you passed through cut open a beat later
                Vec3 a = feet.add(0, 0.1, 0), b = clip(sl, p, a.add(0, 1, 0), a.add(0, 1, 0).add(d.scale(10))).subtract(d.scale(0.6)).add(0, -1, 0);
                List<LivingEntity> path = enemies(sl, p, new net.minecraft.world.phys.AABB(a, b.add(0, 2, 0)).inflate(1.6));
                FxSend.trail(sl, Fx.YAMI, a.add(0, 1, 0), b.add(0, 1, 0), 0.9f, 10);
                p.teleportTo(b.x, b.y, b.z);
                OverdriveFx.play(sl, b, "shadow_slash", 1.2f, 0.9f);
                AstaFx.later(6, () -> {
                    if (!alive(p)) return;
                    for (LivingEntity t : path) {
                        if (!t.isAlive() || mid(t).distanceTo(closestOnSegment(a.add(0, 1, 0), b.add(0, 1, 0), mid(t))) > 2.0) continue;
                        Vec3 c = mid(t);
                        FxSend.slash(sl, Fx.YAMI, c, d, new Vec3(0, 1, 0), 1.6f, 2.0f, 6, 0.6f);
                        AstaFx.damage(p, t, dmg, "unleash");
                        p.heal(2f);
                    }
                    OverdriveFx.play(sl, p.position(), "shadow_whisper", 1f, 0.8f);
                });
            }
            case 4 -> {   // Pyre Sweep: three arcs of flame rolling out ahead, each further than the last
                OverdriveFx.play(sl, feet, "fire", 1.2f, 0.8f);
                java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
                for (int w = 0; w < 3; w++) {
                    final float rr = 3.5f + 3f * w;
                    AstaFx.later(w * 5, () -> {
                        if (!alive(p)) return;
                        FxSend.wall(sl, Fx.FIRE, p.position().add(0, 0.8, 0), d, new Vec3(0, 1, 0), rr - 1f, rr, -55, 55, 1.4f, 9, 1, 0.8f);
                        for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(rr + 1, 2, rr + 1))) {
                            Vec3 to = t.position().subtract(p.position());
                            double dist = Math.sqrt(to.x * to.x + to.z * to.z);
                            if (dist > rr + 0.8 || dist < rr - 3.5 || flat(to).dot(d) < 0.55 || !hit.add(t)) continue;
                            AstaFx.damage(p, t, dmg * 0.8f, "unleash");
                            burn(p, t, 3, 2f);
                        }
                    });
                }
            }
            default -> {   // Devouring Bite: a great jaw closing on everything in front of you
                Vec3 c = eye.add(d.scale(3.2)).add(0, -0.4, 0), side = rightOf(d);
                FxSend.crescent(sl, Fx.CRIMSON, c.add(0, 1.0, 0), d, side, 2.6f, 3.2f, 7, 0.4f);
                FxSend.crescent(sl, Fx.CRIMSON, c.add(0, -1.0, 0), d, side.scale(-1), 2.6f, 3.2f, 7, 0.4f);
                OverdriveFx.play(sl, c, "jaws", 1.4f, 0.7f);
                for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(6))) {
                    Vec3 to = mid(t).subtract(eye);
                    if (to.length() > 6.5 || flat(to).dot(d) < 0.5) continue;
                    AstaFx.damage(p, t, dmg, "unleash");
                    p.heal(1.5f);
                    Fx.impact(sl, Fx.CRIMSON, mid(t), 1.4f);
                }
            }
        }
        if (bond(p) >= 5) startPact(p);
    }

    /** The Awakened Pact: 10 s of you and the blade as one - more damage, faster swings, a quicker step and more of every hit drunk. */
    static void startPact(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        root(p).putLong("pact", now(p) + 200);
        TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, PACT_DMG, "Awakened Pact", 0.3, AttributeModifier.Operation.MULTIPLY_TOTAL, 200);
        TimedModifiers.apply(p, Attributes.ATTACK_SPEED, PACT_SPD, "Awakened Pact", 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, 200);
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, PACT_MOVE, "Awakened Pact", 0.15, AttributeModifier.Operation.MULTIPLY_TOTAL, 200);
        FxSend.ring(sl, PAL[line(heldForm(p))], p.position().add(0, 0.1, 0), 0.5f, 3.5f, 0.2f, 12);
        Fx.pillar(sl, PAL[line(heldForm(p))], p.position(), 3f, 0.6f, 10);
        OverdriveFx.play(sl, p.position(), "heartbeat", 1.2f, 0.8f);
        say(p, "Now. Together.");
        sync(p, false);
    }

    /** The Blade: the growth tree and your four swords. */
    public static void theBlade(ServerPlayer p, float power) { sync(p, true); }

    // ------------------------------------------------------------------ throwing (Bond 3)
    static void throwBlade(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        ItemStack s = p.getMainHandItem().copy();
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        root(p).putBoolean("thrown", true);
        ThrownHungerBlade b = new ThrownHungerBlade(sl, p, s, bladeDmg(p) * 1.2f);
        sl.addFreshEntity(b);
        OverdriveFx.play(sl, p.position(), "blade_fly", 1.1f, 1f);
    }

    /** The thrown blade came back (or was lost): into the empty hand it goes. */
    public static void caught(ServerPlayer p, ItemStack s) {
        root(p).putBoolean("thrown", false);
        if (p.getMainHandItem().isEmpty() && equipped(p)) {
            p.setItemInHand(InteractionHand.MAIN_HAND, s);
            OverdriveFx.play(p.serverLevel(), p.position(), "blade_draw", 0.9f, 1.3f);
        }
    }

    /** A thrown blade struck something (ThrownHungerBlade). */
    public static void thrownHit(ServerPlayer p, LivingEntity t, float dmg, int pal) {
        if (!foe(p, t)) return;
        AstaFx.damage(p, t, dmg, "gnash");
        Fx.spark(p.serverLevel(), pal, mid(t), 1.2f);
        feed(p, 2f);
    }

    static boolean foe(ServerPlayer p, LivingEntity t) { return t != p && t.isAlive() && !(t instanceof Player) && !t.isAlliedTo(p); }

    // ------------------------------------------------------------------ the G wheel and The Blade page
    public static void handle(ServerPlayer p, int action, int arg) {
        if (!equipped(p)) return;
        switch (action) {
            case 1 -> grow(p, arg);
            case 2 -> select(p, arg);
            case 3 -> assign(p, arg / 100, arg % 100 == 99 ? -1 : arg % 100);
            default -> {}
        }
    }

    /** Grow (unlock) a sword: its parent grown, and enough Growth. */
    static void grow(ServerPlayer p, int f) {
        if (f < 0 || f >= FORMS || unlocked(p, f)) return;
        if (PARENT[f] >= 0 && !unlocked(p, PARENT[f])) { tell(p, "Grow " + NAMES[PARENT[f]] + " first"); return; }
        int cost = COST[STAGE[f]];
        CompoundTag r = root(p);
        if (r.getInt("growth") < cost) { tell(p, "Needs " + cost + " Growth"); return; }
        r.putInt("growth", r.getInt("growth") - cost);
        r.putInt("unlocked", r.getInt("unlocked") | (1 << f));
        int[] l = load(p);
        boolean placed = false;
        for (int i = 0; i < 4 && !placed; i++) if (l[i] < 0) { l[i] = f; placed = true; }
        r.putIntArray("load", l);
        ServerLevel sl = p.serverLevel();
        Fx.pillar(sl, PAL[line(f)], p.position(), 2.5f, 0.5f, 10);
        OverdriveFx.play(sl, p.position(), "jaws", 1f, 0.6f);
        say(p, switch (STAGE[f]) {
            case 2 -> "I'm growing. More.";
            case 3 -> "Teeth. Finally, real teeth.";
            case 4 -> "I am becoming something. " + LINE_NAMES[line(f)] + "... yes.";
            case 5 -> "Look at me now.";
            case 6 -> "This is what I was always meant to be.";
            default -> "More.";
        });
        sync(p, false);
    }

    /** The wheel: switch to the sword in one of your four slots. */
    static void select(ServerPlayer p, int slot) {
        if (slot < 0 || slot > 3) return;
        int f = load(p)[slot];
        if (f < 0 || !unlocked(p, f)) return;
        root(p).putInt("sel", slot);
        if (drawn(p)) {
            p.getMainHandItem().getOrCreateTag().putInt(HungerBladeItem.FORM, f);
            ServerLevel sl = p.serverLevel();
            Vec3 hand = p.getEyePosition().add(p.getLookAngle().scale(0.8)).add(0, -0.4, 0);
            Fx.impact(sl, PAL[line(f)], hand, 1.2f);
            OverdriveFx.play(sl, p.position(), "blade_draw", 1f, 1.1f);
            OverdriveFx.play(sl, p.position(), "jaws", 0.5f, 1.5f);
        }
        tell(p, NAMES[f]);
        sync(p, false);
    }

    /** The Blade page: put an unlocked sword in one of your four slots (never in a fight). */
    static void assign(ServerPlayer p, int slot, int f) {
        if (slot < 0 || slot > 3) return;
        if (now(p) - root(p).getLong("combat") < 100) { tell(p, "You can't change your swords in a fight"); sync(p, false); return; }
        int[] l = load(p);
        if (f >= 0) {
            if (!unlocked(p, f)) return;
            for (int i = 0; i < 4; i++) if (l[i] == f) l[i] = l[slot];   // already carried: swap the two slots
        }
        l[slot] = f;
        boolean any = false;
        for (int v : l) any |= v >= 0;
        if (!any) l[slot] = 0;   // never carry nothing
        root(p).putIntArray("load", l);
        if (sel(p) == slot && drawn(p)) p.getMainHandItem().getOrCreateTag().putInt(HungerBladeItem.FORM, current(p));
        sync(p, false);
    }

    public static void sync(ServerPlayer p, boolean open) {
        CompoundTag r = root(p);
        long pl = Math.max(0, r.getLong("pact") - now(p));
        AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new HungerPackets.Sync(equipped(p), r.getInt("growth"), r.getInt("unlocked"), load(p), sel(p),
                r.getFloat("hunger"), r.getInt("bond"), drawn(p), r.getInt("fed"), (int) (pl / 20), open));
    }

    // ------------------------------------------------------------------ the blade's hits and kills
    static boolean extra;
    static final UUID FED_SPD = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a9000b1"), HUNGRY_DMG = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a9000b2"),
            PACT_DMG = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a9000b3"), PACT_SPD = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a9000b4"),
            PACT_MOVE = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a9000b5"), CRACK = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a9000b6"),
            STAGGER = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a9000b7");

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim instanceof ServerPlayer vp && equipped(vp)) root(vp).putLong("combat", now(vp));
        if (extra || !(e.getSource().getEntity() instanceof ServerPlayer p) || !equipped(p)) return;
        root(p).putLong("combat", now(p));
        if (e.getSource().getDirectEntity() != p || !drawn(p) || !e.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)) return;
        ServerLevel sl = p.serverLevel();
        int f = HungerBladeItem.form(p.getMainHandItem()), ln = line(f), pal = PAL[ln];
        float amt = e.getAmount();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        feed(p, 0.5f);
        if (pact(p)) p.heal(amt * 0.2f);
        switch (ln) {
            case 1 -> TimedModifiers.apply(victim, Attributes.ARMOR, CRACK, "Cracked", -4, AttributeModifier.Operation.ADDITION, 80);
            case 2 -> {
                float chance = 0.2f + (branch(f) == 3 ? 0.15f : 0f);
                if (rnd.nextFloat() < chance) { amt *= 1.5f; Fx.impact(sl, Fx.CRIMSON, mid(victim), 1.2f); OverdriveFx.play(sl, mid(victim), "ice_crack", 0.8f, 1.4f); }
            }
            case 3 -> p.heal(amt * 0.12f);
            case 4 -> burn(p, victim, 3, 1.5f);
            default -> p.heal(amt * 0.05f);
        }
        e.setAmount(amt);
        final float hit = amt;
        Vec3 d = flat(p.getLookAngle());
        int n = root(p).getInt("hits") + 1;
        root(p).putInt("hits", n);
        switch (branch(f)) {
            case 1 -> splash(p, victim, 2.5, hit * 0.4f, pal);
            case 2 -> arc(p, victim, 3.5 + REACH[f], 0.4, hit * 0.5f, pal);
            case 3 -> {
                Vec3 a = mid(victim), b = a.add(d.scale(3));
                for (LivingEntity t : enemies(sl, p, new net.minecraft.world.phys.AABB(a, b).inflate(1))) {
                    if (t == victim || mid(t).distanceTo(closestOnSegment(a, b, mid(t))) > 1) continue;
                    extraHit(p, t, hit * 0.6f, pal);
                }
                FxSend.beam(sl, Fx.CRIMSON, a, b, 0.12f, 5);
            }
            case 4 -> {
                TimedModifiers.apply(victim, Attributes.MOVEMENT_SPEED, STAGGER, "Staggered", -0.4, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
                victim.knockback(0.6, -d.x, -d.z);
            }
            case 5 -> {
                arc(p, victim, 3.5 + REACH[f], 0.0, hit * 0.6f, pal);
                FxSend.wall(sl, Fx.YAMI, p.position().add(0, 1, 0), d, new Vec3(0, 1, 0), 1.5f, 3.5f + REACH[f], -80, 80, 0.2f, 5, 1, 1.4f);
            }
            case 6 -> {
                int times = f == 17 ? 2 : 1;
                for (int k = 1; k <= times; k++) AstaFx.later(4 * k, () -> { if (victim.isAlive() && alive(p)) { extraHit(p, victim, hit * 0.35f, Fx.YAMI); OverdriveFx.play(sl, mid(victim), "jaws", 0.6f, 1.4f); } });
            }
            case 7 -> {
                if (n % 3 == 0) {
                    FxSend.wall(sl, Fx.FIRE, p.position().add(0, 0.6, 0), d, new Vec3(0, 1, 0), 1f, 6f, -25, 25, 1.0f, 8, 1, 1f);
                    for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(6.5))) {
                        Vec3 to = t.position().subtract(p.position());
                        if (t == victim || to.length() > 6.5 || flat(to).dot(d) < 0.8) continue;
                        extraHit(p, t, hit * 0.7f, Fx.FIRE);
                        burn(p, t, 2, 1.5f);
                    }
                    OverdriveFx.play(sl, p.position(), "fire", 0.9f, 1.2f);
                }
            }
            case 8 -> {
                if (n % 4 == 0) {
                    Vec3 c = mid(victim);
                    FxSend.ring(sl, Fx.FIRE, c, 0.3f, 3f, 0.6f, 8);
                    Fx.impact(sl, Fx.FIRE, c, 2f);
                    for (LivingEntity t : enemies(sl, p, victim.getBoundingBox().inflate(3))) if (t != victim) extraHit(p, t, hit * 0.8f, Fx.FIRE);
                    OverdriveFx.play(sl, c, "blast", 1f, 1.1f);
                }
            }
            default -> {}
        }
    }

    static void extraHit(ServerPlayer p, LivingEntity t, float dmg, int pal) {
        extra = true;
        try { t.hurt(p.damageSources().playerAttack(p), dmg); } finally { extra = false; }
        Fx.spark(p.serverLevel(), pal, mid(t), 0.9f);
    }

    static void splash(ServerPlayer p, LivingEntity victim, double r, float dmg, int pal) {
        for (LivingEntity t : enemies(p.serverLevel(), p, victim.getBoundingBox().inflate(r))) if (t != victim) extraHit(p, t, dmg, pal);
        FxSend.ring(p.serverLevel(), pal, victim.position().add(0, 0.2, 0), 0.3f, (float) r, 0.1f, 6);
    }

    static void arc(ServerPlayer p, LivingEntity victim, double r, double minDot, float dmg, int pal) {
        Vec3 d = flat(p.getLookAngle());
        for (LivingEntity t : enemies(p.serverLevel(), p, p.getBoundingBox().inflate(r, 1.5, r))) {
            if (t == victim) continue;
            Vec3 to = t.position().subtract(p.position());
            if (to.length() > r || flat(to).dot(d) < minDot) continue;
            extraHit(p, t, dmg, pal);
        }
    }

    /** Burning on: a little damage each second for a few seconds (no fire on the entity or the world). */
    static void burn(ServerPlayer p, LivingEntity t, int seconds, float per) {
        for (int s = 1; s <= seconds; s++) {
            AstaFx.later(20 * s, () -> {
                if (!t.isAlive() || !alive(p)) return;
                extra = true;
                try { t.hurt(p.damageSources().indirectMagic(p, p), per); } finally { extra = false; }
                Fx.spark(p.serverLevel(), Fx.FIRE, mid(t), 0.7f);
            });
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || !equipped(p) || e.getEntity() == p) return;
        LivingEntity t = e.getEntity();
        boolean blade = drawn(p) || root(p).getBoolean("thrown");
        if (!blade) return;
        boolean boss = t instanceof AddonBoss || t.getType().is(Tags.EntityTypes.BOSSES);
        int g = Math.max(1, Math.round(t.getMaxHealth() / 8f)) * (boss ? 3 : 1);
        CompoundTag r = root(p);
        int lv = bond(p);
        r.putInt("growth", r.getInt("growth") + g);
        r.putInt("fed", r.getInt("fed") + g);
        r.putInt("bond", r.getInt("bond") + (boss ? 3 : 1));
        feed(p, Math.min(25f, g * 3f));
        ServerLevel sl = p.serverLevel();
        Fx.spark(sl, PAL[line(heldForm(p))], mid(t), 1.4f);
        p.displayClientMessage(Component.literal("+" + g + " Growth").withStyle(ChatFormatting.RED), true);
        if (bond(p) > lv) {
            say(p, BOND_LINES[bond(p)]);
            OverdriveFx.play(sl, p.position(), "heartbeat", 1f, 1.1f);
        }
        sync(p, false);
    }

    // ------------------------------------------------------------------ every second: hunger, moods, whispers, the blade's place
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        keepInHand(p);
        if (!equipped(p) || p.tickCount % 20 != 0) return;
        CompoundTag r = root(p);
        boolean drawn = drawn(p);
        float h = r.getFloat("hunger");
        if (!p.isCreative()) h = Math.max(0f, h - (drawn ? 0.35f : 0.15f));
        r.putFloat("hunger", h);
        int m = mood(h);
        ServerLevel sl = p.serverLevel();
        if (m == 3) TimedModifiers.apply(p, Attributes.ATTACK_SPEED, FED_SPD, "Well fed", 0.15, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
        if (m <= 1) TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, HUNGRY_DMG, "Hungry", m == 0 ? 0.3 : 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (m == 1 && rnd.nextInt(40) == 0) say(p, WHISPERS[rnd.nextInt(WHISPERS.length)]);
        if (m == 1 && drawn && !p.isShiftKeyDown()) {   // it pulls you, gently, toward the nearest prey
            LivingEntity near = null; double best = 100;
            for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(10))) {
                if (!(t instanceof net.minecraft.world.entity.monster.Enemy)) continue;
                double dd = t.distanceToSqr(p);
                if (dd < best) { best = dd; near = t; }
            }
            if (near != null && best > 4) {
                Vec3 to = flat(near.position().subtract(p.position()));
                p.setDeltaMovement(p.getDeltaMovement().add(to.scale(0.18)));
                p.hurtMarked = true;
            }
        }
        if (m == 0 && p.tickCount % 100 == 0 && p.getHealth() > 4) {   // starved: it bites you
            p.hurt(p.damageSources().magic(), 1.5f);
            if (rnd.nextInt(3) == 0) say(p, STARVED[rnd.nextInt(STARVED.length)]);
            if (drawn) Fx.spark(sl, Fx.CRIMSON, p.getEyePosition().add(p.getLookAngle().scale(0.6)).add(0, -0.5, 0), 0.8f);
        }
        if (pact(p)) FxSend.ring(sl, PAL[line(heldForm(p))], p.position().add(0, 0.1, 0), 0.4f, 1.4f, 0.12f, 10);
        if (p.tickCount % 60 == 0) sync(p, false);
    }

    /** The blade lives only in its wielder's selected main hand, and only while the book is worn. */
    static void keepInHand(ServerPlayer p) {
        var inv = p.getInventory();
        boolean eq = equipped(p);
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (isBlade(s) && (!eq || i != inv.selected)) inv.setItem(i, ItemStack.EMPTY);
        }
        var menu = p.containerMenu;
        for (var slot : menu.slots) if (isBlade(slot.getItem()) && !(slot.container == inv && slot.getContainerSlot() == inv.selected && eq)) slot.set(ItemStack.EMPTY);
        if (isBlade(menu.getCarried())) menu.setCarried(ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------ test helpers (server/GameTests, UiShotsServer)
    public static void testReset(ServerPlayer p) {
        CompoundTag per = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        per.remove(ROOT);
        root(p);
    }
    public static void testGrowth(ServerPlayer p, int g) { root(p).putInt("growth", g); }
    public static void testUnlockAll(ServerPlayer p) { root(p).putInt("unlocked", (1 << FORMS) - 1); }
    public static void testGrow(ServerPlayer p, int f) { root(p).putInt("unlocked", root(p).getInt("unlocked") | (1 << f)); }
    public static void testLoad(ServerPlayer p, int a, int b, int c, int d) { root(p).putIntArray("load", new int[]{a, b, c, d}); }
    public static void testSelect(ServerPlayer p, int slot) { root(p).putInt("sel", slot); }
    public static void testHunger(ServerPlayer p, float h) { root(p).putFloat("hunger", h); }
    public static void testBond(ServerPlayer p, int xp) { root(p).putInt("bond", xp); }
    public static void testCalm(ServerPlayer p) { root(p).putLong("combat", -1000); }
    public static int testLoadAt(ServerPlayer p, int slot) { return load(p)[slot]; }
    /** Draw a given sword straight into the hand. */
    public static void testHold(ServerPlayer p, int f) { give(p, f); }
}
