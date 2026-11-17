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
    public static final int FORMS = 29;
    public static final String[] NAMES = {"Fang", "Gnaw", "Maw Blade",
            "Marrow Cleaver", "Ribcage Greatsword", "Ossuary Colossus", "Spine Saw", "Tailbone Reaper",
            "Shard Blade", "Prism Rapier", "Refraction Lance", "Geode Breaker", "Cluster Colossus",
            "Umbral Edge", "Eclipse Sickle", "Nightfall Crescent", "Twin Fang", "Hydra Edge",
            "Ember Brand", "Flamberge", "Pyre Wing", "Furnace Cleaver", "Magma Maw",
            "Fossil Prism", "Grave Fang", "Cinder Ossuary", "Obsidian Prism", "Sunshard", "Ashen Eclipse"};
    public static final String[] LINE_NAMES = {"Base", "Bone", "Crystal", "Shadow", "Flame"};
    /** The form each one grows from (-1: the first). */
    public static final int[] PARENT = {-1, 0, 1, 2, 3, 4, 3, 6, 2, 8, 9, 8, 11, 2, 13, 14, 13, 16, 2, 18, 19, 18, 21, -2, -2, -2, -2, -2, -2};
    public static final int[] STAGE = {1, 2, 3, 4, 5, 6, 5, 6, 4, 5, 6, 5, 6, 4, 5, 6, 5, 6, 4, 5, 6, 5, 6, 7, 7, 7, 7, 7, 7};
    /** Growth to grow a sword of each stage (index = stage). */
    public static final int[] COST = {0, 0, 20, 40, 80, 140, 220, 300};
    /** The hybrids (stage 7, forms 23-28): the two paths each fuses - an ascended sword of each must be grown. */
    public static final int[][] HYBRID = {{1, 2}, {1, 3}, {1, 4}, {2, 3}, {2, 4}, {3, 4}};
    public static final float[] DMG = {5, 7, 9, 12, 15, 20, 13, 17, 10, 11, 15, 15, 19, 11, 13, 17, 12, 15, 11, 14, 17, 16, 19, 21, 20, 22, 19, 20, 21};
    /** Added to the base attack speed of 4 (a vanilla sword's is -2.4). */
    public static final float[] SPEED = {-1.4f, -2.0f, -2.4f, -2.8f, -3.0f, -3.2f, -2.6f, -2.8f, -2.2f, -1.6f, -2.2f, -3.0f, -3.1f,
            -2.4f, -2.4f, -2.7f, -2.0f, -2.3f, -2.5f, -2.6f, -2.7f, -3.0f, -3.1f, -3.0f, -2.4f, -3.1f, -2.6f, -2.5f, -2.7f};
    public static final float[] REACH = {0, 0, 0.5f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f, 0.5f, 1.5f, 3.0f, 0.5f, 1.0f,
            0.5f, 1.5f, 2.5f, 0.5f, 1.0f, 0.5f, 1.5f, 2.0f, 0.5f, 1.0f, 2.0f, 1.5f, 1.5f, 1.5f, 2.5f, 1.5f};
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
            "Flame: hits burn on; every fourth hit bursts round the foe.", "Flame: hits burn on; every fourth hit bursts round the foe.",
            "Bone + Crystal: hits crack armour, and its criticals splash onto foes nearby.", "Bone + Shadow: hits crack armour and heal you.",
            "Bone + Flame: hits crack armour and burn on.", "Crystal + Shadow: hits may shatter for a critical, and heal you.",
            "Crystal + Flame: criticals that burn on.", "Shadow + Flame: hits heal you and burn on."};

    /** 0 base, 1 bone, 2 crystal, 3 shadow, 4 flame. */
    public static int line(int f) { return f < 3 ? 0 : f >= 23 ? HYBRID[f - 23][0] : 1 + (f - 3) / 5; }
    /** A hybrid's second path (-1 for every other sword). */
    public static int line2(int f) { return f >= 23 ? HYBRID[f - 23][1] : -1; }
    static boolean has(int f, int line) { return line(f) == line || line2(f) == line; }
    /** Whether a sword can be grown now, given what's grown (a bit a form). */
    public static boolean canGrow(int mask, int f) {
        if (f < 0 || f >= FORMS || (mask & (1 << f)) != 0) return false;
        if (PARENT[f] == -1) return true;
        if (PARENT[f] >= 0) return (mask & (1 << PARENT[f])) != 0;
        for (int path : HYBRID[f - 23]) {   // a hybrid: an ascended sword of each of its paths
            int a = 3 + (path - 1) * 5 + 2, b = a + 2;
            if ((mask & (1 << a)) == 0 && (mask & (1 << b)) == 0) return false;
        }
        return true;
    }
    /** The branch's on-hit kind: 0 none (base or a stage-4), 1-8 (bone A, bone B, crystal A, crystal B, shadow A, shadow B, flame A, flame B). */
    static int branch(int f) {
        if (f < 3 || f >= 23) return 0;
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
    /** Left starved over a minute: it sulks until it's fed. */
    public static boolean sulking(Player p) { return root(p).getInt("starvedFor") >= 60; }
    static boolean domain(Player p) { return root(p).getLong("domain") > now(p); }
    static boolean grownStage(Player p, int stage) {
        for (int f = 0; f < FORMS; f++) if (STAGE[f] >= stage && unlocked(p, f)) return true;
        return false;
    }

    static void feed(Player p, float h) {
        CompoundTag r = root(p);
        r.putFloat("hunger", Math.max(0f, Math.min(100f, r.getFloat("hunger") + h)));
    }

    static void say(ServerPlayer p, String s) {
        p.sendSystemMessage(Component.literal("The blade: ").withStyle(ChatFormatting.DARK_RED).append(Component.literal(s).withStyle(ChatFormatting.RED, ChatFormatting.ITALIC)));
    }

    static void tell(ServerPlayer p, String s) { p.displayClientMessage(Component.literal(s).withStyle(ChatFormatting.GRAY), true); }

    // ------------------------------------------------------------------ voice
    /** The blade's voice, a set for each path (0 base - a needy beast, 1 bone - gruff, 2 crystal - vain, 3 shadow - calm,
     *  4 flame - wild): [path][0 a kill, 1 hungry, 2 starved, 3 grown, 4 sulking, 5 a guard strike, 6 a perfect parry]. */
    static final String[][][] VOICE = {
            {{"More. More!", "Warm... good.", "Again."}, {"...feed me...", "So empty. Find me something warm.", "I can hear its heart from here."},
             {"You'll do, then.", "Hold still.", "If nothing else will feed me..."}, {"I'm growing. More.", "Teeth. Finally, real teeth.", "Look at me now."},
             {"No. You let me starve.", "Feed me first."}, {"Mine!", "Not while I'm here."}, {"Got it!", "Too slow."}},
            {{"Cracked.", "Another one for the pile.", "Bones break. That's all."}, {"Marrow. I want marrow.", "Find me something with a thick skull.", "Empty as an old grave."},
             {"Your bones will do.", "Stand still. This won't take long."}, {"Heavier. Good.", "Harder than stone now."},
             {"Carry yourself, then.", "Not until I've eaten."}, {"Behind you. Handled.", "Clumsy thing."}, {"Solid.", "Didn't even dent me."}},
            {{"Did you see how I shone?", "Flawless.", "That was beautiful. I was beautiful."}, {"I'm going dull. Do something.", "Feed me before anyone sees me like this."},
             {"Just a little of yours. You won't miss it.", "Sorry, darling."}, {"Look at me. Just look at me.", "More facets. More light."},
             {"I'm not coming out looking like this.", "Not until I sparkle again."}, {"Don't touch my wielder.", "How rude."}, {"Not a scratch on me.", "Perfect, as always."}},
            {{"Quiet now.", "It never saw us.", "Rest."}, {"The dark is hungry too.", "There. In the corner of your eye."},
             {"I'll take a little. You won't feel it.", "Shh."}, {"Deeper.", "The night grows with me."},
             {"...", "I'll wait in the dark until you remember me."}, {"I saw it coming.", "From the shadows, for you."}, {"Gently.", "As water parts."}},
            {{"BURN!", "Ha! Ash!", "More fuel!"}, {"I'm going out! Feed the fire!", "Something, anything - I need to burn!"},
             {"You're warm enough!", "Ow - sorry - no I'm not!"}, {"HOTTER!", "Look at me blaze!"},
             {"Fine! Freeze, then!", "No fuel, no fire!"}, {"Got you, sneak!", "Nobody touches us!"}, {"Ha! Sparks!", "Too hot to hold, huh?"}}};
    static void voice(ServerPlayer p, int kind) {
        String[] l = VOICE[line(heldForm(p))][kind];
        say(p, l[ThreadLocalRandom.current().nextInt(l.length)]);
    }
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
        if (sulking(p)) {   // left starved too long: it makes you wait
            if (now(p) < root(p).getLong("sulkDraw")) return;
            root(p).putLong("sulkDraw", now(p) + 60);
            voice(p, 4);
            AstaFx.later(60, () -> { if (alive(p) && equipped(p) && p.getMainHandItem().isEmpty() && !drawn(p)) { give(p, current(p)); OverdriveFx.play(sl, p.position(), "blade_draw", 0.7f, 0.6f); } });
            return;
        }
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
        int f = heldForm(p);
        float dmg = bladeDmg(p) * (1.5f + 0.25f * STAGE[f]) * (power / 10f);
        if (line2(f) > 0) {   // a hybrid: both its paths' ultimates, one after the other
            unleashLine(p, line(f), dmg * 0.75f);
            AstaFx.later(10, () -> { if (alive(p)) unleashLine(p, line2(f), dmg * 0.75f); });
        } else unleashLine(p, line(f), dmg);
        if (bond(p) >= 5) { startPact(p); spirit(p); }
    }

    static void unleashLine(ServerPlayer p, int ln, float dmg) {
        ServerLevel sl = p.serverLevel();
        int f = heldForm(p);
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
    }

    /** Bond 5: the blade's spirit, a ghost of the sword, fights beside you for 15 s. */
    static void spirit(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        for (var old : sl.getEntitiesOfClass(com.ironspellgrimoire.entity.BladeSpirit.class, p.getBoundingBox().inflate(40), x -> p.getUUID().equals(x.owner()))) old.discard();
        sl.addFreshEntity(new com.ironspellgrimoire.entity.BladeSpirit(sl, p, HungerBladeItem.of(AddonItemRegistry.HUNGER_BLADE.get(), heldForm(p)), bladeDmg(p) * 0.6f));
        Fx.pillar(sl, PAL[line(heldForm(p))], p.position(), 3f, 0.4f, 10);
    }

    /** The spirit picks its prey: the nearest foe within 8 of its wielder. */
    public static LivingEntity spiritTarget(ServerPlayer p) {
        LivingEntity best = null; double bd = 64;
        for (LivingEntity t : enemies(p.serverLevel(), p, p.getBoundingBox().inflate(8))) {
            if (!foe(p, t)) continue;
            double dd = t.distanceToSqr(p);
            if (dd < bd) { bd = dd; best = t; }
        }
        return best;
    }

    public static void spiritHit(ServerPlayer p, LivingEntity t, float dmg, int form) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = mid(t), d = flat(c.subtract(p.position()));
        FxSend.slash(sl, PAL[line(form)], c, d, new Vec3(0, 1, 0), 1.3f, 1.7f, 5, 0.6f);
        AstaFx.damage(p, t, dmg, "unleash");
        OverdriveFx.play(sl, c, "slash", 0.6f, 1.4f);
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
        if (!canGrow(root(p).getInt("unlocked"), f)) { tell(p, "Grow an Ascended " + LINE_NAMES[line(f)] + " and an Ascended " + LINE_NAMES[line2(f)] + " sword first"); return; }
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
        String[] g = VOICE[line(f)][3];
        say(p, STAGE[f] == 7 ? "Two hungers, one blade. " + NAMES[f] + "." : STAGE[f] == 4 ? "I am becoming something. " + LINE_NAMES[line(f)] + "... yes."
                : g[ThreadLocalRandom.current().nextInt(g.length)]);
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
            STAGGER = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a9000b7"), SULK = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a9000b8"),
            DOMAIN_SLOW = UUID.fromString("5e1f0c3a-7d21-4b8e-9a64-0b2c7a9000b9");

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim instanceof ServerPlayer vp && equipped(vp)) { root(vp).putLong("combat", now(vp)); defend(vp, e); }
        if (extra || !(e.getSource().getEntity() instanceof ServerPlayer p) || !equipped(p)) return;
        root(p).putLong("combat", now(p));
        if (e.getSource().getDirectEntity() != p || !drawn(p) || !e.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)) return;
        ServerLevel sl = p.serverLevel();
        int f = HungerBladeItem.form(p.getMainHandItem()), ln = line(f), pal = PAL[ln];
        float amt = e.getAmount();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        feed(p, 0.5f);
        if (pact(p)) p.heal(amt * 0.2f);
        // Devour: sneak and strike a foe near death (not a boss) - it's eaten whole
        boolean boss = victim instanceof AddonBoss || victim.getType().is(Tags.EntityTypes.BOSSES);
        if (p.isShiftKeyDown() && !boss && foe(p, victim) && victim.getHealth() - amt <= victim.getMaxHealth() * 0.15f && now(p) >= root(p).getLong("devourCd")) {
            root(p).putLong("devourCd", now(p) + 60);
            devouring = victim.getId();
            e.setAmount(victim.getHealth() + victim.getAbsorptionAmount() + 50f);
            devourFx(p, victim, f);
            return;
        }
        // the path's own touch (a hybrid has both of its paths')
        if (line(f) == 0) p.heal(amt * 0.05f);
        if (has(f, 1)) TimedModifiers.apply(victim, Attributes.ARMOR, CRACK, "Cracked", -4, AttributeModifier.Operation.ADDITION, 80);
        boolean crit = false;
        if (has(f, 2)) {
            float chance = 0.2f + (branch(f) == 3 ? 0.15f : 0f);
            if (rnd.nextFloat() < chance) {
                crit = true;
                amt *= 1.5f; Fx.impact(sl, Fx.CRIMSON, mid(victim), 1.2f); OverdriveFx.play(sl, mid(victim), "ice_crack", 0.8f, 1.4f);
                if (f == 23) splash(p, victim, 2.5, amt * 0.4f, Fx.SILVER);   // the Fossil Prism: its criticals splash
            }
        }
        if (has(f, 3)) p.heal(amt * 0.12f);
        if (has(f, 4) && (f != 27 || crit)) burn(p, victim, 3, 1.5f);   // the Sunshard burns on its criticals only
        if (f == 27 && crit) burn(p, victim, 4, 2f);
        e.setAmount(amt);
        // the twin in the off hand (Bond 4) bites a moment after
        if (isBlade(p.getOffhandItem())) {
            final float twin = amt * 0.35f;
            AstaFx.later(3, () -> {
                if (!victim.isAlive() || !alive(p) || !isBlade(p.getOffhandItem())) return;
                extraHit(p, victim, twin, PAL[line(f)]);
                FxSend.crescent(sl, PAL[line(f)], mid(victim), flat(p.getLookAngle()), rightOf(flat(p.getLookAngle())).scale(-1), 0.7f, 0.9f, 4, 0.6f);
            });
        }
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

    static int devouring = -1;

    /** Eaten whole: the jaws close round it, and a burst in the sword's own colour. */
    static void devourFx(ServerPlayer p, LivingEntity t, int f) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = mid(t), d = flat(c.subtract(p.position())), side = rightOf(d);
        int pal = PAL[line(f)];
        FxSend.crescent(sl, pal, c.add(0, 0.7, 0), d, side, 1.4f, 1.9f, 7, 0.4f);
        FxSend.crescent(sl, pal, c.add(0, -0.7, 0), d, side.scale(-1), 1.4f, 1.9f, 7, 0.4f);
        switch (line(f)) {
            case 1 -> { for (int k = 0; k < 8; k++) Fx.pillar(sl, Fx.SILVER, t.position().add(Math.cos(k * 0.785) * 1.2, 0, Math.sin(k * 0.785) * 1.2), 1.4f, 0.15f, 8); }
            case 2 -> { for (int k = 0; k < 8; k++) FxSend.beam(sl, Fx.CRIMSON, c, c.add(rotY(d, k * 0.785).scale(2).add(0, 0.4 * ((k % 3) - 1), 0)), 0.12f, 7); }
            case 3 -> FxSend.ring(sl, Fx.YAMI, t.position().add(0, 0.1, 0), 0.2f, 2.2f, 0.4f, 10);
            case 4 -> FxSend.wall(sl, Fx.FIRE, t.position().add(0, 0.4, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.3f, 1.8f, -180, 180, 1.2f, 8, 1, 1.2f);
            default -> Fx.impact(sl, Fx.CRIMSON, c, 1.8f);
        }
        OverdriveFx.play(sl, c, "jaws", 1.3f, 0.6f);
        OverdriveFx.play(sl, c, "blood_splash", 1f, 0.8f);
        p.heal(4f);
    }

    /** The wielder is hit: a parry in time (right-click just before), or - fed and bonded - the blade guards their back. */
    static void defend(ServerPlayer p, LivingHurtEvent e) {
        if (!drawn(p)) return;
        ServerLevel sl = p.serverLevel();
        LivingEntity att = e.getSource().getEntity() instanceof LivingEntity l && l != p ? l : null;
        long since = now(p) - root(p).getLong("parryAt");
        if (since >= 0 && since <= 8 && !e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) {
            root(p).putLong("parryAt", -100);
            e.setAmount(e.getAmount() * 0.2f);
            int f = heldForm(p);
            Vec3 at = p.getEyePosition().add(p.getLookAngle().scale(0.9)).add(0, -0.3, 0);
            Fx.impact(sl, PAL[line(f)], at, 1.3f);
            OverdriveFx.play(sl, at, "clank", 1f, 1.2f);
            if (att != null && att.distanceTo(p) < 5) {
                extraHit(p, att, bladeDmg(p) * 0.6f, PAL[line(f)]);
                OverdriveFx.play(sl, mid(att), "jaws", 0.9f, 1.2f);
            }
            if (since <= 4) { feed(p, 8f); voice(p, 6); }   // a perfect parry
            return;
        }
        if (att != null && bond(p) >= 2 && mood(hunger(p)) >= 2 && now(p) >= root(p).getLong("guardCd") && foe(p, att)) {
            Vec3 to = flat(att.position().subtract(p.position()));
            if (to.dot(flat(p.getLookAngle())) < -0.2) {   // from behind: it strikes on its own
                root(p).putLong("guardCd", now(p) + 160);
                extraHit(p, att, bladeDmg(p) * 0.8f, PAL[line(heldForm(p))]);
                FxSend.slash(sl, PAL[line(heldForm(p))], mid(att), to, new Vec3(0, 1, 0), 1.2f, 1.6f, 5, 0.6f);
                OverdriveFx.play(sl, mid(att), "slash", 0.9f, 1.3f);
                voice(p, 5);
            }
        }
    }

    // ------------------------------------------------------------------ right-click: tap to parry, hold to charge (HungerBladeItem)
    public static void startUse(ServerPlayer p) { root(p).putLong("parryAt", now(p)); }

    public static void release(ServerPlayer p, int held) { if (held >= 18 && drawn(p)) charge(p); }

    /** The charged strike, its own for each path. */
    static void charge(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        int f = heldForm(p), ln = line(f), pal = PAL[ln];
        float dmg = bladeDmg(p) * 1.7f;
        Vec3 d = flat(p.getLookAngle()), feet = p.position();
        switch (ln) {
            case 1 -> {   // a ring of bone walls bursting up round you
                for (int k = 0; k < 16; k++) {
                    double a = Math.PI * 2 * k / 16;
                    Fx.pillar(sl, Fx.SILVER, feet.add(Math.cos(a) * 3, 0, Math.sin(a) * 3), 2.4f, 0.45f, 12);
                }
                FxSend.ring(sl, Fx.SILVER, feet.add(0, 0.1, 0), 2.4f, 3.6f, 0.3f, 10);
                for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(4, 2, 4))) {
                    if (t.distanceTo(p) > 4) continue;
                    AstaFx.damage(p, t, dmg, "gnash");
                    Vec3 out = flat(t.position().subtract(feet));
                    t.setDeltaMovement(out.scale(0.8).add(0, 0.4, 0)); t.hurtMarked = true;
                }
                OverdriveFx.play(sl, feet, "stomp", 1.2f, 0.8f);
            }
            case 2 -> {   // a long piercing lunge
                Vec3 a = p.position().add(0, 1, 0), b = clip(sl, p, a, a.add(d.scale(7)));
                p.setDeltaMovement(d.scale(2.2).add(0, 0.1, 0)); p.hurtMarked = true;
                FxSend.trail(sl, Fx.CRIMSON, a, b, 0.5f, 8);
                for (LivingEntity t : enemies(sl, p, new net.minecraft.world.phys.AABB(a, b).inflate(1.3)))
                    if (mid(t).distanceTo(closestOnSegment(a, b, mid(t))) <= 1.3) { AstaFx.damage(p, t, dmg, "gnash"); Fx.impact(sl, Fx.CRIMSON, mid(t), 1.2f); }
                OverdriveFx.play(sl, a, "ice_shatter", 1f, 1.3f);
            }
            case 3 -> {   // a blink behind your target, and a cut
                LivingEntity t = aimed(p, 12, 25);
                if (t == null) { tell(p, "Nothing to cut"); return; }
                Vec3 dir = flat(t.position().subtract(feet)), to = t.position().add(dir.scale(1.6));
                FxSend.trail(sl, Fx.YAMI, p.position().add(0, 1, 0), to.add(0, 1, 0), 0.6f, 8);
                p.teleportTo(to.x, t.getY(), to.z);
                p.setYRot((float) Math.toDegrees(Math.atan2(-dir.scale(-1).x, dir.scale(-1).z)));
                FxSend.slash(sl, Fx.YAMI, mid(t), dir.scale(-1), new Vec3(0, 1, 0), 1.6f, 2.0f, 6, 0.6f);
                AstaFx.damage(p, t, dmg * 1.15f, "gnash");
                OverdriveFx.play(sl, mid(t), "shadow_slash", 1.1f, 1f);
            }
            case 4 -> {   // a spinning wheel of fire
                for (int k = 0; k < 3; k++)
                    AstaFx.later(k * 3, () -> FxSend.wall(sl, Fx.FIRE, p.position().add(0, 1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 2f, 3.4f, -180, 180, 1.2f, 6, 1, 2f));
                for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(3.6, 1.5, 3.6))) {
                    if (t.distanceTo(p) > 3.8) continue;
                    AstaFx.damage(p, t, dmg * 0.9f, "gnash");
                    burn(p, t, 3, 2f);
                }
                OverdriveFx.play(sl, feet, "fire", 1.2f, 1.1f);
            }
            default -> {   // a big lunging bite
                p.setDeltaMovement(d.scale(1.9).add(0, 0.2, 0)); p.hurtMarked = true;
                Vec3 c = p.getEyePosition().add(d.scale(3)), side = rightOf(d);
                FxSend.crescent(sl, Fx.CRIMSON, c.add(0, 0.9, 0), d, side, 2f, 2.6f, 6, 0.4f);
                FxSend.crescent(sl, Fx.CRIMSON, c.add(0, -0.9, 0), d, side.scale(-1), 2f, 2.6f, 6, 0.4f);
                AstaFx.later(4, () -> {
                    for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(2.5))) { AstaFx.damage(p, t, dmg, "gnash"); p.heal(1f); }
                    OverdriveFx.play(sl, p.position(), "jaws", 1.3f, 0.8f);
                });
            }
        }
        feed(p, -2f);
    }

    // ------------------------------------------------------------------ Hunger Domain (the sixth page; a stage-6 sword grown)
    public static boolean canDomain(Player p) { return drawn(p) && grownStage(p, 6); }

    /** For 20 s the ground round you is a mouth: foes in it are slowed, and every kill in it fills the blade. */
    public static void hungerDomain(ServerPlayer p, float power) {
        if (!canDomain(p)) { tell(p, drawn(p) ? "Grow a stage 6 sword first" : "Draw the blade first"); return; }
        CompoundTag r = root(p);
        r.putLong("domain", now(p) + 400);
        r.putDouble("domX", p.getX()); r.putDouble("domY", p.getY()); r.putDouble("domZ", p.getZ());
        ServerLevel sl = p.serverLevel();
        FxSend.impactFrame(sl, p.position(), 3, false, 24);
        OverdriveFx.play(sl, p.position(), "jaws", 1.6f, 0.5f);
        OverdriveFx.play(sl, p.position(), "deep_boom", 1f, 0.8f);
        voice(p, 0);
        domainPulse(p, true);
    }

    static Vec3 domCenter(Player p) { CompoundTag r = root(p); return new Vec3(r.getDouble("domX"), r.getDouble("domY"), r.getDouble("domZ")); }

    static void domainPulse(ServerPlayer p, boolean first) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = domCenter(p);
        float R = 7f;
        FxSend.ring(sl, Fx.CRIMSON, c.add(0, 0.06, 0), R - 0.6f, R, 0.1f, 12);
        if (first || p.tickCount % 40 == 0) {   // the teeth round its rim, upper and lower jaw
            for (int k = 0; k < 20; k++) {
                double a = Math.PI * 2 * k / 20;
                Fx.pillar(sl, k % 2 == 0 ? Fx.SILVER : Fx.CRIMSON, c.add(Math.cos(a) * R, 0, Math.sin(a) * R), k % 2 == 0 ? 1.6f : 1.0f, 0.25f, 40);
            }
            FxSend.ring(sl, Fx.CRIMSON, c.add(0, 0.05, 0), 0.5f, R - 0.6f, 0.05f, 40);
        }
        for (LivingEntity t : enemies(sl, p, new net.minecraft.world.phys.AABB(c, c).inflate(R, 3, R))) {
            if (!foe(p, t) || t.position().distanceTo(c) > R) continue;
            TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, DOMAIN_SLOW, "Hunger Domain", -0.35, AttributeModifier.Operation.MULTIPLY_TOTAL, 15);
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
        int g = Math.max(1, Math.round(t.getMaxHealth() / 8f)) * (boss ? 3 : 1) * (devouring == t.getId() ? 2 : 1);
        devouring = -1;
        if (domain(p) && t.position().distanceTo(domCenter(p)) < 9) { root(p).putFloat("hunger", 100f); Fx.spark(p.serverLevel(), Fx.CRIMSON, p.getEyePosition(), 1f); }
        if (ThreadLocalRandom.current().nextInt(8) == 0) voice(p, 0);
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
        if (p.tickCount % 2 == 0) fastTick(p);
        if (!equipped(p) || p.tickCount % 20 != 0) return;
        CompoundTag r = root(p);
        boolean drawn = drawn(p);
        float h = r.getFloat("hunger");
        if (!p.isCreative()) h = Math.max(0f, h - (drawn ? 0.35f : 0.15f));
        r.putFloat("hunger", h);
        int m = mood(h);
        ServerLevel sl = p.serverLevel();
        if (m == 0) r.putInt("starvedFor", r.getInt("starvedFor") + 1);
        else if (h >= 30) r.putInt("starvedFor", 0);
        boolean sulk = sulking(p);
        if (m == 3) TimedModifiers.apply(p, Attributes.ATTACK_SPEED, FED_SPD, "Well fed", 0.15, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
        if (sulk) TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, SULK, "Sulking", -0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
        else if (m <= 1) TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, HUNGRY_DMG, "Hungry", m == 0 ? 0.3 : 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, 30);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (m == 1 && rnd.nextInt(40) == 0) voice(p, 1);
        if (r.getInt("starvedFor") == 60) voice(p, 4);
        // the twin (Bond 4): grows into the empty off hand while the blade is out
        if (drawn && bond(p) >= 4 && p.getOffhandItem().isEmpty()) {
            ItemStack tw = HungerBladeItem.of(AddonItemRegistry.HUNGER_BLADE.get(), 0);
            tw.getOrCreateTag().putBoolean("twin", true);
            p.setItemInHand(InteractionHand.OFF_HAND, tw);
            Fx.spark(sl, PAL[line(heldForm(p))], p.getEyePosition().add(p.getLookAngle().scale(0.6)).add(0, -0.5, 0), 0.7f);
        }
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
            if (rnd.nextInt(3) == 0) voice(p, 2);
            if (drawn) Fx.spark(sl, Fx.CRIMSON, p.getEyePosition().add(p.getLookAngle().scale(0.6)).add(0, -0.5, 0), 0.8f);
        }
        if (pact(p)) FxSend.ring(sl, PAL[line(heldForm(p))], p.position().add(0, 0.1, 0), 0.4f, 1.4f, 0.12f, 10);
        if (p.tickCount % 60 == 0) sync(p, false);
    }

    /** Ten times a second: the Domain's pulse, the charge's glow, the twin's place, and the sheathed blade others see. */
    static void fastTick(ServerPlayer p) {
        if (!equipped(p)) { look(p, -1); return; }
        ServerLevel sl = p.serverLevel();
        if (domain(p)) domainPulse(p, false);
        if (drawn(p) && p.isUsingItem() && isBlade(p.getUseItem()) && p.getTicksUsingItem() >= 18 && p.tickCount % 4 == 0)
            Fx.spark(sl, PAL[line(heldForm(p))], p.getEyePosition().add(p.getLookAngle().scale(0.9)).add(0, -0.4, 0), 0.6f);   // charged: ready to let go
        if (isBlade(p.getOffhandItem()) && (!drawn(p) || bond(p) < 4)) p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        look(p, drawn(p) || root(p).getBoolean("thrown") ? -1 : current(p));
    }

    /** What sword hangs sheathed at the hip (-1 none), sent to everyone who sees the player when it changes. */
    static void look(ServerPlayer p, int form) {
        CompoundTag r = root(p);
        if (r.getInt("look") == form + 2) return;
        r.putInt("look", form + 2);
        AddonNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new HungerPackets.Look(p.getId(), form));
    }

    @SubscribeEvent
    public static void onTrack(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking e) {
        if (!(e.getTarget() instanceof ServerPlayer t) || !(e.getEntity() instanceof ServerPlayer w)) return;
        int l = root(t).getInt("look") - 2;
        if (l >= 0) AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> w), new HungerPackets.Look(t.getId(), l));
    }

    /** The blade lives only in its wielder's selected main hand, and only while the book is worn. */
    static void keepInHand(ServerPlayer p) {
        var inv = p.getInventory();
        boolean eq = equipped(p);
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (isBlade(s) && (!eq || i != inv.selected) && !(i == 40 && eq && drawn(p))) inv.setItem(i, ItemStack.EMPTY);
        }
        var menu = p.containerMenu;
        for (var slot : menu.slots) if (isBlade(slot.getItem()) && !(slot.container == inv && eq && (slot.getContainerSlot() == inv.selected || slot.getContainerSlot() == 40 && drawn(p)))) slot.set(ItemStack.EMPTY);
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
    public static void testStarvedFor(ServerPlayer p, int s) { root(p).putInt("starvedFor", s); }
    public static void testParry(ServerPlayer p) { startUse(p); }
    public static void testCharge(ServerPlayer p) { charge(p); }
    public static int testLook(ServerPlayer p) { return root(p).getInt("look") - 2; }
    /** Draw a given sword straight into the hand. */
    public static void testHold(ServerPlayer p, int f) { give(p, f); }
}
