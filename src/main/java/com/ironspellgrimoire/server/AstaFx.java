package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;
import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Effects of the Asta-themed grimoire spells (Zetten, Sky Splitter, Anti Magic, Ki Sense, Instinct Guard, Devil Union,
 * Dweller / Borrowed Slash).
 *
 * Rules for this addon:
 *  - every visual is drawn with the addon's own 3D shapes (FxSend / Fx); no vanilla particles.
 *  - no vanilla status effects are applied; buffs and debuffs are transient attribute modifiers (TimedModifiers)
 *    or handled directly in events, so other mods can't override them.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public class AstaFx {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String KI_GLOW_TAG = "iron_spell_grimoire_ki_glow";
    // ------------------------------------------------------------------ tuning
    private static final int GUARD_TICKS = 40;               // Instinct Guard: a 2 s counter stance
    private static final int COUNTER_GAP = 10;               // at most one counter every 0.5 s (also stops two guards countering forever)
    private static final double COUNTER_RANGE = 6.0;
    private static final int UNION_TICKS = 50 * 20;          // Devil Union: 50 s, like the manga
    private static final float SWORD_SPELL_RESIST = 0.35f;   // holding a sword: 35% less spell damage
    private static final float UNION_SPELL_RESIST = 0.70f;   // Devil Union: 70% less spell damage
    private static final float UNION_DAMAGE_RESIST = 0.35f;  // Devil Union: 35% less of every other kind of damage
    private static final int UNION_HEAL_EVERY = 25;          // Devil Union: heal 1 HP every 25 ticks
    private static final int KI_GLOW_TICKS = 12 * 20;

    private static final Map<UUID, Long> GUARD = new HashMap<>();
    private static final Map<UUID, Long> UNION = new HashMap<>();
    private static final Map<UUID, Long> LAST_COUNTER = new HashMap<>();

    // ------------------------------------------------------------------ helpers

    /** Cuts a ranged attack short at the first solid block, so beams and slashes don't pass through walls. */
    private static Vec3 clipToBlocks(ServerLevel sl, Entity p, Vec3 start, Vec3 end) {
        var hit = sl.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    private static float spellPower(ServerPlayer p) {
        return (float) p.getAttributeValue(AttributeRegistry.SPELL_POWER.get());
    }

    // ------------------------------------------------------------------ tiny scheduler for staged effects
    /** due tick -> tasks in the order they were scheduled. Only the due tick's bucket is touched each tick. */
    private static final java.util.TreeMap<Long, List<Runnable>> TASKS = new java.util.TreeMap<>();
    private static long serverTicks = 0;

    /** Runs {@code r} on the server thread {@code ticks} ticks from now. Used to stage effects over time. */
    public static void later(int ticks, Runnable r) {
        TASKS.computeIfAbsent(serverTicks + Math.max(1, ticks), k -> new ArrayList<>()).add(r);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        serverTicks++;
        if (TASKS.isEmpty()) return;
        List<Runnable> due = new ArrayList<>();
        while (!TASKS.isEmpty() && TASKS.firstKey() <= serverTicks) due.addAll(TASKS.pollFirstEntry().getValue());
        for (Runnable t : due) {
            try { t.run(); } catch (Exception ex) { LOGGER.warn("Iron Spell Grimoire staged effect failed", ex); }
        }
    }

    // ------------------------------------------------------------------ queries
    /** True while the player is in Devil Union. */
    public static boolean inDevilUnion(ServerPlayer p) {
        Long u = UNION.get(p.getUUID());
        return u != null && p.level().getGameTime() <= u;
    }

    /** True when the player holds a Demon Slayer or Demon Dweller Sword in either hand. */
    public static boolean holdsAntiMagicSword(ServerPlayer p) {
        return p.getMainHandItem().is(AddonItemRegistry.DEMON_SLAYER_SWORD.get()) || p.getMainHandItem().is(AddonItemRegistry.DEMON_DWELLER_SWORD.get())
                || p.getOffhandItem().is(AddonItemRegistry.DEMON_SLAYER_SWORD.get()) || p.getOffhandItem().is(AddonItemRegistry.DEMON_DWELLER_SWORD.get());
    }

    public static boolean isDweller(ServerPlayer p) { return p.getMainHandItem().is(AddonItemRegistry.DEMON_DWELLER_SWORD.get()); }

    private static final Map<String, io.redspace.ironsspellbooks.api.spells.AbstractSpell> SPELL_CACHE = new HashMap<>();

    /** The addon spell with this id (path), looked up once and then cached. Same result as scanning the registry. */
    private static io.redspace.ironsspellbooks.api.spells.AbstractSpell spellById(String id) {
        if (SPELL_CACHE.containsKey(id)) return SPELL_CACHE.get(id);
        var spell = AddonSpellRegistry.SPELLS.getEntries().stream().filter(e -> e.getId().getPath().equals(id)).findFirst().map(e -> e.get()).orElse(null);
        SPELL_CACHE.put(id, spell);
        return spell;
    }

    static void damage(ServerPlayer p, LivingEntity t, float dmg, String id) {
        if (CastSync.clashLost(p)) return;   // the move was broken in a Spell Clash
        if (CastSync.deferDamage(p, t, () -> { if (t.isAlive()) damage(p, t, dmg, id); })) return;   // lands when the move reaches it
        var spell = spellById(id);
        if (spell != null) DamageSources.applyDamage(t, dmg, spell.getDamageSource(p));
        else t.hurt(p.damageSources().playerAttack(p), dmg);
    }

    private static void variantMsg(ServerPlayer p) {
        p.displayClientMessage(Component.translatable("ui.iron_spell_grimoire.variant.dweller").withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    // ================================================================== Zetten
    /** Huge downward slash (Slayer) / fast X of two slashes (Dweller). */
    public static void zetten(ServerPlayer p, float dmg, boolean dw) {
        ServerLevel sl = p.serverLevel();
        final int K = com.ironspellgrimoire.network.FxPacket.KEEP_SHAPE;   // Zetten keeps its solid 3D blades and walls
        if (dw) { dmg *= 0.85f; variantMsg(p); AnimSend.play(p, AnimSend.CROSS_CUT); }   // Dweller: the X is two quick cuts
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z).normalize();
        Vec3 center = p.position().add(dir.scale(2.6));
        List<LivingEntity> targets = sl.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(dw ? 3.8 : 3.2, 2.5, dw ? 3.8 : 3.2),
                t -> t != p && t.isAlive() && !t.isAlliedTo(p) && t.position().subtract(p.position()).multiply(1, 0, 1).normalize().dot(dir) > 0.2);
        for (LivingEntity t : targets) {
            damage(p, t, dmg, "zetten");
            t.knockback(1.2, -dir.x, -dir.z);
            Fx.impact(sl, dw ? Fx.DARK : Fx.CRIMSON, mid(t), 1.2f);
        }
        Vec3 rt = rightOf(dir);
        Vec3 hit = p.position().add(dir.scale(3.2)).add(0, 0.1, 0);
        if (dw) {   // two diagonal dark blades crossing in an X, then a pale afterimage
            Vec3 cx = eye.add(dir.scale(2.4)).add(0, -0.2, 0);
            for (int dgn = 0; dgn < 2; dgn++) {
                Vec3 u = rt.scale(dgn == 0 ? 0.7 : -0.7).add(0, 0.7, 0).scale(3.0);
                FxSend.beam(sl, (Fx.DARK) | K, cx.subtract(u), cx.add(u), 0.55f, 9);
            }
            FxSend.wall(sl, (Fx.DARK) | K, p.position().add(0, 0.1, 0).add(dir.scale(0.2)), dir, rt, 3.4f, 3.4f, -70, 70, 0.8f, 8, 2, 0);
            later(2, () -> {
                for (int dgn = 0; dgn < 2; dgn++) {
                    Vec3 u2 = rt.scale(dgn == 0 ? 0.7 : -0.7).add(0, 0.7, 0).scale(3.3);
                    FxSend.beam(sl, (Fx.SILVER) | K, cx.subtract(u2), cx.add(u2), 0.2f, 7);
                }
            });
        } else {    // the Slayer's vertical cleave, from above your head down to the ground, then the ground splits
            FxSend.wall(sl, (Fx.CRIMSON) | K, eye.add(0, -0.3, 0), dir, new Vec3(0, 1, 0), 3.4f, 3.4f, 80, -70, 1.5f, 13, 3, 0);
            Fx.flash(sl, hit.add(0, 0.6, 0), 1.8f);
            // the ground splits: a jagged glowing fissure runs ahead, with thin side cracks and small black shards
            Vec3 ground = new Vec3(hit.x, p.getY() + 0.04, hit.z);
            java.util.Random rr = new java.util.Random();
            Vec3[] pts = new Vec3[7];
            pts[0] = FxLib.ground(sl, ground.subtract(dir.scale(0.6)), 2.0);
            for (int i = 1; i < pts.length; i++) pts[i] = FxLib.ground(sl, ground.add(dir.scale(i * 1.0)).add(rt.scale((rr.nextDouble() - 0.5) * 0.6)), 2.0);   // follows the ground
            for (int i = 1; i < pts.length; i++) {
                if (pts[i - 1] == null || pts[i] == null) break;   // the crack stops where the ground drops away
                final Vec3 a = pts[i - 1], b = pts[i];
                final int seg = i;
                final double side = rr.nextBoolean() ? 1 : -1;
                later(seg / 2, () -> {
                    FxSend.beam(sl, (Fx.CRIMSON) | K, a, b, 0.14f, 26);
                    FxSend.beam(sl, (Fx.SILVER) | K, a, b, 0.03f, 18);
                    if (seg % 2 == 0) FxSend.beam(sl, (Fx.CRIMSON) | K, b, b.add(rt.scale(0.75 * side)).add(dir.scale(0.35)), 0.07f, 20);
                    if (seg % 2 == 1) {
                        for (int sd = -1; sd <= 1; sd += 2) {
                            FxSend.spike(sl, Fx.DARK, b.add(rt.scale(0.22 * sd)).add(0, -0.05, 0), new Vec3(0, 1, 0).add(rt.scale(0.7 * sd)),
                                    0.4f + 0.15f * (seg % 3), 0.07f, 20, 2, com.ironspellgrimoire.network.FxPacket.GROUND);
                        }
                    }
                });
            }
            sound(sl, hit, net.minecraft.sounds.SoundEvents.STONE_BREAK, 0.9f, 0.6f);
        }
        FxSend.ring(sl, (dw ? Fx.DARK : Fx.CRIMSON) | K, hit, 0.3f, 3.0f, 0.08f, 8);
        later(2, () -> FxSend.ring(sl, (Fx.SILVER) | K, hit, 0.6f, 5.5f, 0.05f, 8));   // one thin shockwave rolling out
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.5f);
        sound(sl, hit, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 0.8f, 1.2f);
        sound(sl, hit, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
    }

    // ================================================================== Sky Splitter
    /*
     * Reworked: no longer a plain beam.
     *  1. WIND-UP (6 ticks): anti magic drains into the blade - rings collapse onto the sword and a black edge forms along it.
     *  2. RELEASE: an upward cleave launches a giant vertical black crescent (Dweller: two crescents crossed in an X) that
     *     flies forward, cutting everything it passes through and stopping at walls.
     *  3. THE SKY SPLITS: behind the crescent a rift opens along its whole path - a glowing tear line with jagged shards.
     *  4. COLLAPSE: a moment later the rift snaps shut, hitting everything still standing in it for a second time.
     */
    private static final int SKY_WINDUP = 6;
    private static final double SKY_SPEED = 2.2;
    private static final int SKY_MAX_STEPS = 12;           // ~26 blocks
    private static final float SKY_RIFT_FACTOR = 0.4f;     // the collapse deals 40% of the crescent's damage

    public static void skySplitter(ServerPlayer p, float dmg, boolean dw) {
        ServerLevel sl = p.serverLevel();
        if (dw) variantMsg(p);
        final float hitDmg = dw ? dmg * 0.85f : dmg * 1.2f;   // the Slayer's is the slash alone (no rift), so it cuts a little harder

        // ---- 1. wind-up: energy drains into the sword
        Vec3 look0 = p.getLookAngle();
        Vec3 tip = p.getEyePosition().add(look0.scale(1.2)).add(0, -0.3, 0);
        int pal = dw ? Fx.DARK : 18;   // the Slayer's is red only
        if (dw) {
            FxSend.ring(sl, pal, p.position().add(0, 0.05, 0), 3.5f, 0.3f, 0.2f, SKY_WINDUP);          // ring collapsing inward
            FxSend.ring(sl, Fx.SILVER, p.position().add(0, 0.9, 0), 2.6f, 0.2f, 0.12f, SKY_WINDUP);
            FxSend.beam(sl, Fx.DARK, p.position().add(0, 0.2, 0), tip.add(0, 1.6, 0), 0.25f, SKY_WINDUP + 2);   // black edge along the raised blade
        } else {   // Slayer: a single red edge gathering along the raised blade, nothing else
            FxSend.beam(sl, 18, tip.add(0, -0.4, 0), tip.add(0, 1.6, 0), 0.18f, SKY_WINDUP + 2);
        }
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, 1f, 0.5f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.TRIDENT_RIPTIDE_1, 0.8f, 0.6f);

        // ---- 2. release
        later(SKY_WINDUP, () -> {
            if (!p.isAlive() || p.isRemoved()) return;
            Vec3 dir = p.getLookAngle();
            Vec3 start = p.getEyePosition().add(dir.scale(1.0)).add(0, -0.4, 0);
            Vec3 rt = rightOf(dir);
            Vec3 up = rt.cross(dir).normalize();
            List<Integer> fxIds = new ArrayList<>();
            if (dw) {   // two crescents crossed in an X
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 u = up.scale(Math.cos(Math.toRadians(45))).add(rt.scale(s * Math.sin(Math.toRadians(45))));
                    fxIds.add(FxSend.crescent(sl, Fx.DARK, start, dir, u, 1.6f, 3.4f, SKY_MAX_STEPS + 2, (float) SKY_SPEED));
                }
            } else {    // Demon Slayer: the same crescent slash as the Dweller's - one, upright and towering - all in red
                fxIds.add(FxSend.crescent(sl, 18, start, dir, up, 1.8f, 3.9f, SKY_MAX_STEPS + 2, (float) SKY_SPEED));
                later(2, () -> fxIds.add(FxSend.crescent(sl, 18, start, dir, up, 1.1f, 2.8f, SKY_MAX_STEPS, (float) SKY_SPEED)));   // a slimmer echo chasing it
            }
            if (dw) FxSend.wall(sl, pal, p.position().add(0, 0.1, 0), dir, up, 2.4f, 2.8f, -60, 80, 0.9f, 7, 2, 0);   // the upward swing itself
            Fx.flash(sl, start, 1.4f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.4f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.TRIDENT_THUNDER, 0.8f, 1.3f);
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.WITHER_SHOOT, 0.6f, 0.6f);
            flyCrescent(p, sl, start, dir, up, rt, hitDmg, dw, fxIds, new ArrayList<>(), new HashSet<>(), 0);
        });
    }

    /** Moves the Sky Splitter crescent one step per tick, hitting everything inside it once. */
    private static void flyCrescent(ServerPlayer p, ServerLevel sl, Vec3 pos, Vec3 dir, Vec3 up, Vec3 rt, float dmg, boolean dw,
                                    List<Integer> fxIds, List<Vec3> path, Set<UUID> hit, int step) {
        if (!p.isAlive() || p.isRemoved()) return;
        path.add(pos);
        Vec3 next = pos.add(dir.scale(SKY_SPEED));
        Vec3 clipped = clipToBlocks(sl, p, pos, next);
        boolean blocked = clipped.distanceToSqr(next) > 1.0E-4;
        // the crescent is tall and thin: a box ~4.5 high, ~2.5 wide around the segment
        AABB box = new AABB(pos, clipped).inflate(1.3, 2.3, 1.3);
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, box, x -> x != p && x.isAlive() && !x.isAlliedTo(p))) {
            if (!hit.add(t.getUUID())) continue;
            damage(p, t, dmg, "sky_splitter");
            t.knockback(0.6, -dir.x, -dir.z);
            Fx.impact(sl, dw ? Fx.DARK : 18, mid(t), 1.3f);
            if (dw) FxSend.beam(sl, Fx.SILVER, mid(t).subtract(up.scale(1.4)), mid(t).add(up.scale(1.4)), 0.2f, 6);   // a clean vertical cut through them
            sound(sl, t.position(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG, 1f, 0.6f);
        }
        if (step % 2 == 0) sound(sl, clipped, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 0.6f + step * 0.05f);
        if (blocked || step + 1 >= SKY_MAX_STEPS) {
            path.add(clipped);
            if (blocked) {
                for (int id : fxIds) FxSend.kill(sl, id, clipped);
                Fx.impact(sl, dw ? Fx.DARK : 18, clipped, 2.2f);
                FxSend.ring(sl, Fx.SILVER, clipped, 0.3f, 3.2f, 0.4f, 8);
                sound(sl, clipped, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 0.7f, 1.4f);
            }
            if (dw) later(2, () -> openRift(p, sl, path, up, rt, dmg * SKY_RIFT_FACTOR, dw));   // only the Dweller's tears the sky open
            return;
        }
        later(1, () -> flyCrescent(p, sl, next, dir, up, rt, dmg, dw, fxIds, path, hit, step + 1));
    }

    /** "The sky splits": a glowing tear opens along the crescent's path, then collapses and cuts again. */
    private static void openRift(ServerPlayer p, ServerLevel sl, List<Vec3> path, Vec3 up, Vec3 rt, float dmg, boolean dw) {
        if (path.size() < 2) return;
        Vec3 a = path.get(0), b = path.get(path.size() - 1);
        int riftPal = dw ? Fx.DARK : 18;
        // the tear line itself: a bright core inside a dark edge
        FxSend.beam(sl, dw ? Fx.DARK : 18, a, b, 0.9f, 16);
        FxSend.beam(sl, riftPal, a, b, 0.45f, 14);
        FxSend.beam(sl, Fx.SILVER, a, b, 0.14f, 12);
        // jagged shards jutting out of the tear
        Random rnd = new Random();
        for (int i = 1; i < path.size(); i++) {
            Vec3 c = path.get(i);
            double s = (rnd.nextDouble() - 0.5) * 0.9;
            Vec3 shard = up.scale(0.8 + rnd.nextDouble() * 0.9).add(rt.scale(s));
            FxSend.beam(sl, riftPal, c, c.add(shard), 0.12f, 12);
            FxSend.spike(sl, riftPal, c, shard, (float) shard.length(), 0.09f, 12, 2, com.ironspellgrimoire.network.FxPacket.POP);   // solid shard
            FxSend.spike(sl, riftPal, c, shard.scale(-1), (float) shard.length() * 0.7f, 0.07f, 12, 2, com.ironspellgrimoire.network.FxPacket.POP);
        }
        sound(sl, a, net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP, 0.9f, 0.5f);
        sound(sl, b, net.minecraft.sounds.SoundEvents.ILLUSIONER_CAST_SPELL, 0.9f, 0.5f);

        // collapse: the rift snaps shut and everything still inside it is cut again
        later(10, () -> {
            if (!p.isAlive() || p.isRemoved()) return;
            Set<UUID> struck = new HashSet<>();
            for (int i = 0; i < path.size(); i++) {
                Vec3 c = path.get(i);
                if (i % 2 == 0) {
                    FxSend.ring(sl, riftPal, c, 0.2f, 2.2f, 0.25f, 7);
                    Fx.spark(sl, Fx.SILVER, c, 1.0f);
                }
                for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(1.8, 2.2, 1.8),
                        x -> x != p && x.isAlive() && !x.isAlliedTo(p))) {
                    if (!struck.add(t.getUUID())) continue;
                    damage(p, t, dmg, "sky_splitter");
                    Fx.impact(sl, riftPal, mid(t), 1.0f);
                }
            }
            FxSend.beam(sl, Fx.SILVER, a, b, 0.6f, 4);   // final flash as it seals
            sound(sl, a, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 0.8f, 0.5f);
            sound(sl, b, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.5f);
        });
    }

    // ================================================================== Demon Dweller Sword: projected slash of anti magic
    private static final Random RND = new Random();
    private static final String[] ELEMENTS = {"fire", "ice", "lightning", "wind"};

    public static String randomElement() { return ELEMENTS[RND.nextInt(ELEMENTS.length)]; }

    /**
     * The wind-up before a Dweller slash: energy spirals into the blade at your hand (dark for the Dweller Slash, the
     * element's colour for Borrowed Slash) and tightens until the release.
     */
    public static void dwellerWindup(ServerPlayer p, String element, int ticks) {
        ServerLevel sl = p.serverLevel();
        int pal = element == null ? Fx.DARK : switch (element) { case "fire" -> Fx.FIRE; case "ice" -> Fx.ICE; case "lightning" -> Fx.LIGHTNING; default -> Fx.WIND; };
        for (int k = 0; k < ticks; k += 2) {
            final int kk = k;
            later(Math.max(1, k), () -> {
                if (!p.isAlive()) return;
                double yr = Math.toRadians(p.getYRot());
                Vec3 hand = p.position().add(-Math.sin(yr) * 0.4 - Math.cos(yr) * 0.45, 0.9, Math.cos(yr) * 0.4 - Math.sin(yr) * 0.45);
                float r = 1.4f - kk * 0.12f;
                double a = kk * 1.1;
                Vec3 from = hand.add(Math.cos(a) * r, 0.5 - kk * 0.05, Math.sin(a) * r);
                FxSend.beam(sl, pal, from, hand, 0.12f, 3);
                FxSend.ring(sl, pal, hand, r * 0.6f, 0.15f, 0.05f, 3);
            });
        }
        if (element != null) p.displayClientMessage(Component.translatable("ui.iron_spell_grimoire.borrowed." + element).withStyle(ChatFormatting.DARK_RED), true);
    }

    /** borrowed=false: pure anti magic crescent that also strips effects. borrowed=true: it takes on a random element. */
    public static void dwellerSlash(ServerPlayer p, float dmg, boolean borrowed) { dwellerSlash(p, dmg, borrowed, null); }

    /** @param forced element to use instead of a random one ("fire", "ice", "lightning", "wind"), or null */
    public static void dwellerSlash(ServerPlayer p, float dmg, boolean borrowed, String forced) {
        ServerLevel sl = p.serverLevel();
        Vec3 start = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        double len = 20;
        Vec3 end = clipToBlocks(sl, p, start, start.add(look.scale(len)));
        String el = !borrowed ? null : forced != null ? forced : ELEMENTS[RND.nextInt(ELEMENTS.length)];
        int pal = el == null ? Fx.DARK : switch (el) { case "fire" -> Fx.FIRE; case "ice" -> Fx.ICE; case "lightning" -> Fx.LIGHTNING; default -> Fx.WIND; };
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(2.5), x -> x != p && x.isAlive() && !x.isAlliedTo(p))) {
            if (!t.getBoundingBox().inflate(1.3).clip(start, end).isPresent()) continue;
            float d = dmg;
            if ("lightning".equals(el)) d += 4;
            damage(p, t, d, borrowed ? "borrowed_slash" : "dweller_slash");
            Vec3 c = mid(t);
            if (el == null) t.removeAllEffects();   // anti magic strips magic off the target
            else switch (el) {
                case "fire" -> t.setSecondsOnFire(6);
                case "ice" -> {
                    TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, TimedModifiers.CHILL_SLOW, "Borrowed ice", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL, 80);
                    t.setTicksFrozen(t.getTicksRequiredToFreeze() + 60);
                }
                case "lightning" -> {
                    sound(sl, c, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);
                    FxSend.beam(sl, Fx.LIGHTNING, c.add(0, 4, 0), c, 0.3f, 5);   // a bolt drops onto them
                }
                default -> t.push(0, 0.9, 0);
            }
            Fx.impact(sl, pal, c, 1.1f);
            sound(sl, c, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, 1f, 1.3f);
        }
        // the flying crescent blade, tilted 30 degrees like a diagonal cut, with a pale echo chasing it
        Vec3 right = rightOf(look);
        Vec3 up = right.cross(look).normalize();
        double tilt = Math.toRadians(30);
        Vec3 u = right.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt)));
        Vec3 cStart = start.add(look.scale(1.6)).add(0, -0.2, 0);
        FxSend.crescent(sl, pal, cStart, look, u, 1.3f, 2.6f, 9, 2.4f);
        later(2, () -> FxSend.crescent(sl, el == null ? Fx.SILVER : pal, cStart, look, u, 1.0f, 2.0f, 7, 2.4f));
        Fx.flash(sl, cStart, 0.9f);
        FxSend.ring(sl, pal, start.add(look.scale(1.2)), 0.2f, 1.4f, 0.3f, 6);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.7f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.TRIDENT_RIPTIDE_1, 0.7f, 1.4f);
        if (el != null) {
            // the Dweller blade blazes in the borrowed element's colour for 4 seconds
            for (net.minecraft.world.item.ItemStack st : new net.minecraft.world.item.ItemStack[]{p.getMainHandItem(), p.getOffhandItem()}) {
                if (st.getItem() instanceof com.ironspellgrimoire.item.DemonDwellerSwordItem) {
                    com.ironspellgrimoire.item.DemonDwellerSwordItem.glow(st, el, sl.getGameTime() + 80);
                }
            }
            sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE, 0.8f, 1.4f);
            p.displayClientMessage(Component.translatable("ui.iron_spell_grimoire.borrowed." + el).withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    // ================================================================== Anti Magic
    /** Strips magic around you: enemy effects, enemy casts, and your own debuffs. */
    public static void antiMagic(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        // self-cleanse: harmful effects from other mods, and any debuff this addon put on you
        new ArrayList<>(p.getActiveEffects()).stream().filter(e -> !e.getEffect().isBeneficial()).forEach(e -> p.removeEffect(e.getEffect()));
        TimedModifiers.clearDebuffs(p);
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(9), x -> x != p && x.isAlive() && !x.isAlliedTo(p))) {
            if (!t.getActiveEffects().isEmpty()) t.removeAllEffects();
            boolean broke = false;
            if (t instanceof ServerPlayer sp && MagicData.getPlayerMagicData(sp).isCasting()) {
                Utils.serverSideCancelCast(sp);
                sp.displayClientMessage(Component.translatable("ui.iron_spell_grimoire.cast_broken").withStyle(ChatFormatting.DARK_RED), true);
                broke = true;
            } else if (t instanceof IMagicEntity mage && mage.isCasting()) {
                mage.cancelCast();
                broke = true;
            }
            if (broke) {   // their spell shatters
                Fx.impact(sl, Fx.SILVER, mid(t), 1.6f);
                FxSend.wall(sl, Fx.DARK, mid(t), new Vec3(1, 0, 0), new Vec3(0, 1, 0), 0.2f, 1.6f, -180, 180, 0.3f, 6, 0, 0);
                sound(sl, t.position(), net.minecraft.sounds.SoundEvents.GLASS_BREAK, 1f, 0.7f);
            } else {
                Fx.spark(sl, Fx.DARK, mid(t), 0.8f);
            }
        }
        Vec3 c = p.position().add(0, 0.1, 0);
        FxSend.ring(sl, Fx.DARK, c, 0.5f, 9.5f, 0.7f, 12);
        FxSend.ring(sl, Fx.SILVER, c.add(0, 0.4, 0), 0.3f, 7f, 0.25f, 10);
        // a black pillar of anti magic, then one more wave rolling outward
        Fx.pillar(sl, Fx.DARK, c, 6f, 0.6f, 12);
        later(4, () -> FxSend.ring(sl, Fx.DARK, c, 0.5f, 12f, 0.2f, 10));
        later(3, () -> sound(sl, c, net.minecraft.sounds.SoundEvents.ILLUSIONER_CAST_SPELL, 1f, 0.6f));
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, 1.2f, 0.6f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE, 1f, 0.5f);
    }

    // ================================================================== Ki Sense
    public static final int KI_HOSTILE = 0, KI_PASSIVE = 1, KI_NEUTRAL = 2, KI_PLAYER = 3;

    /** What kind of life a creature's ki reads as: red monsters, green passive, yellow neutral, blue players. */
    private static int kiType(LivingEntity t) {
        if (t instanceof Player) return KI_PLAYER;
        if (t instanceof Enemy) return KI_HOSTILE;
        if (t instanceof NeutralMob) return KI_NEUTRAL;
        return KI_PASSIVE;
    }

    /**
     * Reveals every creature nearby, through walls, as a faint flame of ki coloured by what it is.
     * Only the caster sees it (sent to that player alone) - no glowing effect, nothing other players notice.
     */
    /**
     * Shows one creature's ki to {@code p} only. strong = the Mana Zone version: much brighter, with a ring at its
     * feet and a beacon of light over its head so it reads at a glance, even far away or behind walls.
     */
    public static void sendKiMark(ServerPlayer p, LivingEntity t, int ticks, boolean strong) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 4;
        pkt.id = t.getId();
        pkt.palette = (byte) (kiType(t) + (strong ? 8 : 0));
        pkt.life = ticks;
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), pkt);
    }

    public static void kiSense(ServerPlayer p) { kiSense(p, 32, KI_GLOW_TICKS); }

    public static void kiSense(ServerPlayer p, double range, int ticks) {
        ServerLevel sl = p.serverLevel();
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), x -> x != p && x.isAlive() && !(x instanceof ArmorStand))) {
            FxPacket pkt = new FxPacket();
            pkt.shape = 4;                      // 4 = ki mark, handled by the client KiSenseRenderer
            pkt.id = t.getId();
            pkt.palette = (byte) kiType(t);
            pkt.life = ticks;
            AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), pkt);
        }
        Vec3 c = p.position().add(0, 0.1, 0);
        FxSend.ring(sl, Fx.CRIMSON, c, 0.5f, 32f, 0.25f, 16);
        // two more sonar pings, each one higher in pitch
        for (int k = 1; k <= 2; k++) {
            final float pitch = 1.0f + k * 0.35f;
            later(k * 6, () -> {
                FxSend.ring(sl, Fx.SILVER, c, 0.5f, 32f, 0.15f, 16);
                sound(sl, c, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, pitch);
            });
        }
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, 1f, 1.6f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 0.7f);
    }

    // ================================================================== Instinct Guard
    /** A 2 s ki stance: nothing can hurt you, and the first enemy to strike gets countered. */
    private static final java.util.Set<UUID> DEVIL_GUARD = new java.util.HashSet<>();

    public static void instinct(ServerPlayer p) { instinct(p, false); }

    /** devil = Devil Instinct: a 3 s stance, counters every 0.3 s instead of once, and each counter hits 50% harder. */
    public static void instinct(ServerPlayer p, boolean devil) {
        if (devil) DEVIL_GUARD.add(p.getUUID()); else DEVIL_GUARD.remove(p.getUUID());
        ServerLevel sl = p.serverLevel();
        GUARD.put(p.getUUID(), sl.getGameTime() + (devil ? GUARD_TICKS + 20 : GUARD_TICKS));
        LAST_COUNTER.remove(p.getUUID());
        FxSend.ring(sl, Fx.SILVER, p.position().add(0, 0.9, 0), 0.4f, 1.6f, 0.5f, 9);
        FxSend.ring(sl, Fx.SILVER, p.position().add(0, 0.05, 0), 0.3f, 6.0f, 0.08f, 10);   // ki spreads out to read the next attack
        sendOutline(p, 7, devil ? GUARD_TICKS + 20 : GUARD_TICKS);   // a thin skin of white ki while the stance holds
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.6f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.8f);
    }

    /** Instinct Guard counter: step behind the attacker and cut them before they recover. */
    private static void counter(ServerPlayer p, LivingEntity attacker) {
        ServerLevel sl = p.serverLevel();
        Vec3 from = p.position();
        Vec3 a = attacker.position();
        Vec3 through = a.subtract(from).multiply(1, 0, 1);
        if (through.lengthSqr() < 1.0E-4) through = attacker.getLookAngle().multiply(-1, 0, -1);
        through = through.normalize();
        Vec3 dest = a.add(through.scale(attacker.getBbWidth() * 0.5 + 1.0));
        // only blink if there is room behind them
        if (sl.noCollision(p, p.getBoundingBox().move(dest.subtract(from)))) {
            float yaw = (float) (Mth.atan2(a.z - dest.z, a.x - dest.x) * (180.0 / Math.PI)) - 90f;
            p.connection.teleport(dest.x, dest.y, dest.z, yaw, p.getXRot());
            p.resetFallDistance();
            FxSend.beam(sl, Fx.SILVER, from.add(0, 1, 0), dest.add(0, 1, 0), 0.25f, 8);   // afterimage trail of the blink
            FxSend.wall(sl, Fx.SILVER, from.add(0, 1.0, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0.6f, 0.9f, -180, 180, 1.0f, 6, 0, 0);   // your ghost where you stood
        }
        AnimSend.play(p, AnimSend.LUNGE_STAB);   // the counter-thrust
        FxSend.impactFrame(sl, mid(attacker), 2, false, 16);   // the instant the counter lands
        boolean devil = DEVIL_GUARD.contains(p.getUUID());
        damage(p, attacker, 12f * spellPower(p) * (devil ? 1.5f : 1f), devil ? "devil_instinct_guard" : "instinct_guard");
        if (devil) FxLib.impact(sl, 9, mid(attacker), 1.4f);   // ember
        // stagger: slowed and weakened for 2 s (attribute modifiers, not status effects)
        TimedModifiers.apply(attacker, Attributes.MOVEMENT_SPEED, TimedModifiers.STAGGER_SLOW, "Instinct stagger", -0.35, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
        TimedModifiers.apply(attacker, Attributes.ATTACK_DAMAGE, TimedModifiers.STAGGER_WEAK, "Instinct stagger", -3.0, AttributeModifier.Operation.ADDITION, 40);
        Vec3 m = mid(attacker);
        Vec3 look = m.subtract(p.getEyePosition());
        look = look.lengthSqr() < 1.0E-4 ? p.getLookAngle() : look.normalize();
        Vec3 right = rightOf(look);
        Vec3 up = right.cross(look).normalize();
        Vec3 u = right.scale(Math.cos(Math.toRadians(-35))).add(up.scale(Math.sin(Math.toRadians(-35))));
        FxSend.crescent(sl, Fx.CRIMSON, m.subtract(look.scale(0.8)), look, u, 1.1f, 1.9f, 6, 0.4f);
        Fx.flash(sl, m, 1.2f);
        Fx.impact(sl, Fx.CRIMSON, m, 1.4f);
        sound(sl, m, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 0.7f, 1.8f);
        sound(sl, m, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.7f);
        sound(sl, m, net.minecraft.sounds.SoundEvents.TRIDENT_HIT, 1f, 0.6f);
        p.displayClientMessage(Component.translatable("ui.iron_spell_grimoire.counter").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
    }

    // ================================================================== Devil Union
    /** Liebe merges with you for 50 seconds. */
    public static void devilUnion(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        final boolean full = LimitBreak.active(p);   // Surpassing Limits: Full Devil Union - it holds for the whole state
        final int UNION_TICKS = full ? Math.max(AstaFx.UNION_TICKS, LimitBreak.ticksLeft(p)) : AstaFx.UNION_TICKS;
        UNION.put(p.getUUID(), sl.getGameTime() + UNION_TICKS);
        // every grimoire spell becomes its devil version (new icons too) - one tick later, so the cast that started it finishes first
        later(1, () -> { if (!p.isRemoved()) DevilBook.apply(p, true); });
        sendForm(p, UNION_TICKS);   // wings unfold, crown and mantle appear
        // the power of the union (transient attribute modifiers, removed automatically when it ends)
        TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, TimedModifiers.UNION_DAMAGE, "Devil Union strength", 6.0, AttributeModifier.Operation.ADDITION, UNION_TICKS);
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, TimedModifiers.UNION_SPEED, "Devil Union speed", 0.5, AttributeModifier.Operation.MULTIPLY_TOTAL, UNION_TICKS);
        TimedModifiers.apply(p, Attributes.ATTACK_SPEED, TimedModifiers.UNION_ATTACK_SPEED, "Devil Union fury", 0.3, AttributeModifier.Operation.MULTIPLY_TOTAL, UNION_TICKS);
        TimedModifiers.apply(p, Attributes.KNOCKBACK_RESISTANCE, TimedModifiers.UNION_KNOCKBACK, "Devil Union weight", 0.5, AttributeModifier.Operation.ADDITION, UNION_TICKS);
        Vec3 c = p.position();
        // no rings or pillars around you any more - just the transformation itself, and a shove as the power bursts out
        later(8, () -> {
            sound(sl, c, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 0.9f, 0.5f);
            if (p.isAlive()) {
                for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6), x -> x != p && x.isAlive() && !x.isAlliedTo(p))) {
                    Vec3 away = t.position().subtract(p.position()).multiply(1, 0, 1);
                    if (away.lengthSqr() < 1.0E-4) away = new Vec3(1, 0, 0);
                    away = away.normalize();
                    t.knockback(1.6, -away.x, -away.z);
                }
            }
        });
        if (full) LimitFx.fullUnion(p, UNION_TICKS);
        sound(sl, c, net.minecraft.sounds.SoundEvents.WITHER_SPAWN, 0.5f, 1.6f);
        sound(sl, c, net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, 0.6f, 0.7f);
        sound(sl, c, net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, 1f, 0.5f);
    }

    /** Body aura outline (FxPacket shape 14) for everyone who can see {@code p}: palette = colour, life 0 = off. */
    static void sendOutline(ServerPlayer p, int palette, int life) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 14; pkt.id = p.getId(); pkt.palette = (byte) palette; pkt.life = life;
        AddonNetwork.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }

    /** Tells everyone who can see {@code p} to draw (life > 0) or fade out (life = 0) the devil form. */
    private static void sendForm(ServerPlayer p, int life) {
        ModeHud.show(p, "devil_union", life);
        sendOutline(p, 8, life);   // a faint demonic haze round the body for the whole union
        FxPacket pkt = new FxPacket();
        pkt.shape = 6;   // 6 = Devil Union form, drawn by the client FxRenderer
        pkt.id = p.getId();
        pkt.life = life;
        AddonNetwork.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }

    /**
     * Devil Union: Release - end the union early in one blast. The more union time is left, the harder it hits.
     * Slayer in hand: a meteor of darkness slams down from above. Dweller in hand: a vortex tears outward.
     */
    public static void releaseUnion(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Long u = UNION.get(p.getUUID());
        if (u == null) return;
        float left = Math.max(0, u - sl.getGameTime()) / (float) UNION_TICKS;   // 1.0 = fresh union
        float dmg = power * (1f + 2f * left);
        boolean dw = isDweller(p);
        Vec3 c = p.position();
        if (dw) {
            for (int k = 0; k < 6; k++) FxLib.spiral(sl, 9, c.add(0, 0.2, 0), new Vec3(Math.cos(k), 0.35, Math.sin(k)).normalize(), 1.2f, k);
            FxLib.spinSlash(sl, 1, c.add(0, 1, 0), p.getLookAngle(), 2.4f);
        } else {
            FxSend.beam(sl, 9, c.add(0, 14, 0), c, 1.6f, 10);
            FxSend.beam(sl, 1, c.add(0, 14, 0), c, 0.6f, 9);
            FxLib.spikeRing(sl, 9, c, 2.2f);
        }
        FxLib.shockRing(sl, 9, c.add(0, 0.1, 0), 3.0f);
        later(3, () -> FxLib.shockRing(sl, 1, c.add(0, 0.1, 0), 4.0f));
        FxLib.dome(sl, 9, c, 2.0f);
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(8), x -> x != p && x.isAlive() && !x.isAlliedTo(p))) {
            if (t.distanceTo(p) > 8) continue;
            damage(p, t, dmg, "devil_union_release");
            Vec3 away = t.position().subtract(c).multiply(1, 0, 1);
            if (away.lengthSqr() < 1.0E-4) away = new Vec3(1, 0, 0);
            away = away.normalize();
            t.knockback(2.0, -away.x, -away.z);
            t.push(0, 0.7, 0);
            t.hurtMarked = true;
        }
        sound(sl, c, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 1.2f, 0.5f);
        sound(sl, c, net.minecraft.sounds.SoundEvents.WITHER_DEATH, 0.5f, 1.4f);
        endUnion(p);
    }

    private static void endUnion(ServerPlayer p) {
        sendForm(p, 0);
        later(1, () -> { if (!p.isRemoved()) DevilBook.apply(p, false); });   // back to the normal spells (a logged-out player is fixed on next login)
        UNION.remove(p.getUUID());
        TimedModifiers.remove(p, Attributes.ATTACK_DAMAGE, TimedModifiers.UNION_DAMAGE);
        TimedModifiers.remove(p, Attributes.MOVEMENT_SPEED, TimedModifiers.UNION_SPEED);
        TimedModifiers.remove(p, Attributes.ATTACK_SPEED, TimedModifiers.UNION_ATTACK_SPEED);
        TimedModifiers.remove(p, Attributes.KNOCKBACK_RESISTANCE, TimedModifiers.UNION_KNOCKBACK);
    }

    // ================================================================== events
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;   // /kill, the void
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Long until = GUARD.get(p.getUUID());
        if (until == null) return;
        if (p.level().getGameTime() > until) { GUARD.remove(p.getUUID()); return; }
        e.setCanceled(true);
        ServerLevel sl = p.serverLevel();
        Fx.spark(sl, Fx.SILVER, p.position().add(0, 1, 0), 1.0f);
        sound(sl, p.position(), net.minecraft.sounds.SoundEvents.SHIELD_BLOCK, 0.8f, 1.5f);
        Entity src = e.getSource().getEntity();
        if (src instanceof LivingEntity attacker && attacker != p && attacker.isAlive() && attacker.distanceTo(p) <= COUNTER_RANGE) {
            long now = sl.getGameTime();
            Long last = LAST_COUNTER.get(p.getUUID());
            if (last == null || now - last >= (DEVIL_GUARD.contains(p.getUUID()) ? 6 : COUNTER_GAP)) {
                LAST_COUNTER.put(p.getUUID(), now);   // set first: stops two guarding players countering forever
                counter(p, attacker);
            }
        }
    }

    /** Damage reduction: the swords cut incoming spells apart, Devil Union shrugs off almost everything. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        boolean spell = e.getSource() instanceof SpellDamageSource;
        float keep = 1f;
        if (inDevilUnion(p)) keep = spell ? 1f - UNION_SPELL_RESIST : 1f - UNION_DAMAGE_RESIST;
        else if (spell && holdsAntiMagicSword(p)) keep = 1f - SWORD_SPELL_RESIST;
        if (keep >= 1f) return;
        e.setAmount(e.getAmount() * keep);
        if (spell) {
            Fx.spark(p.serverLevel(), Fx.DARK, p.position().add(0, 1, 0), 0.9f);
            sound(p.serverLevel(), p.position(), net.minecraft.sounds.SoundEvents.ANVIL_LAND, 0.25f, 1.8f);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();

        Long g = GUARD.get(p.getUUID());
        if (g != null) {
            if (now > g) { GUARD.remove(p.getUUID()); sendOutline(p, 7, 0); }
        }

        Long u = UNION.get(p.getUUID());
        if (u == null && now % 20 == 0) DevilBook.apply(p, false);   // no union: make sure the book holds the normal spells
        if (u != null) {
            if (now > u) {
                endUnion(p);
                sound(sl, p.position(), net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, 1f, 0.8f);
            } else {
                if (now % UNION_HEAL_EVERY == 0 && p.getHealth() < p.getMaxHealth()) p.heal(1.0f);   // devil regeneration
                if (now % 40 == 0) sendForm(p, (int) (u - now));   // keeps the form visible to players who arrive later
                if (now % 20 == 0) DevilBook.apply(p, true);        // also covers a grimoire equipped mid-union
                p.resetFallDistance();   // devil wings: no fall damage during the union
            }
        }
    }

    /** Devil Union: the wings break every fall. */
    @SubscribeEvent
    public static void onFall(net.minecraftforge.event.entity.living.LivingFallEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && inDevilUnion(p)) e.setCanceled(true);
    }

    /** Clears a glow left on a mob by Ki Sense from an older version of the addon. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        Entity ent = e.getEntity();
        if (ent.getPersistentData().getBoolean(KI_GLOW_TAG)) {
            ent.setGlowingTag(false);
            ent.getPersistentData().remove(KI_GLOW_TAG);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            GUARD.remove(p.getUUID());
            if (UNION.containsKey(p.getUUID())) endUnion(p);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        GUARD.clear();
        UNION.clear();
        LAST_COUNTER.clear();
        TASKS.clear();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && UNION.containsKey(sp.getUUID())) sendForm(sp, 0);
        GUARD.remove(e.getEntity().getUUID());
        UNION.remove(e.getEntity().getUUID());
        LAST_COUNTER.remove(e.getEntity().getUUID());
    }
}
