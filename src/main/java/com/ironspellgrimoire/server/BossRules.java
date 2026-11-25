package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * How a bounty boss fights as you climb (BossLadder). Everything here sits on top of each boss's own fight:
 * <ul>
 *   <li><b>Threat:</b> +10% health for each threat rank of the mage who took the poster. From rank 3, and always when
 *       Hardened, it has the Reckoning: every 12 s a ring under one of you, then a burst where it was.</li>
 *   <li><b>Groups:</b> +60% health for every mage past the first who comes to the fight (within 40 blocks) - it keeps
 *       the share of health it had.</li>
 *   <li><b>Stages:</b> by tier - tier I has 2 stages, tier II 3, tier III 4 and a Last Stand at 10% (6 s: half damage
 *       taken, 30% harder hits). Each new stage throws you back and makes it 10% faster.</li>
 *   <li><b>Enrage:</b> 3 minutes into the fight (2 when Hardened) it hits 25% harder and moves 20% faster.</li>
 *   <li><b>Weak points:</b> every 15 s a gold light opens on its back or side for 3 s. Hit it from that side and it's
 *       staggered for 2.5 s: it can't move or strike, and takes 50% more.</li>
 *   <li><b>Memory:</b> a boss you've beaten guards against how you beat it (melee, spells or projectiles): 25% less
 *       from that for the first 30 s.</li>
 *   <li><b>Shared kills:</b> everyone who hurt it gets the kill in their log, mastery experience, a Mana Core of its
 *       tier and a chance at its signature drop (certain for whoever took the poster).</li>
 * </ul>
 * Status effects are never used: speed comes from attribute modifiers, damage from the blow itself.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class BossRules {
    private BossRules() {}

    public static final String TAG = "isg_boss";
    static final UUID HP_RANK = UUID.fromString("b055a000-0000-4000-8000-000000000001"), HP_GROUP = UUID.fromString("b055a000-0000-4000-8000-000000000002"),
            HP_HARD = UUID.fromString("b055a000-0000-4000-8000-000000000003"), SPD_STAGE = UUID.fromString("b055a000-0000-4000-8000-000000000004"),
            SPD_RAGE = UUID.fromString("b055a000-0000-4000-8000-000000000005"), SPD_STAGGER = UUID.fromString("b055a000-0000-4000-8000-000000000006");
    public static final int ENRAGE_TICKS = 3600, ENRAGE_HARD = 2400, WEAK_EVERY = 300, WEAK_OPEN = 60, STAGGER = 50, RECKON_EVERY = 240, MEMORY_TICKS = 600, LAST_STAND = 120;
    public static final float GROUP_HP = 0.6f;
    /** Health fractions where each stage begins, by tier. */
    static final float[][] LINES = {{}, {0.5f}, {0.66f, 0.33f}, {0.75f, 0.5f, 0.25f}};

    static final class St {
        int boss, tier, rank, group = 1, stage, memory;
        boolean hardened, enraged, lastStand;
        long first, lastStandUntil, weakUntil, weakNext, stagUntil, reckonNext;
        float weakOff;
        final Map<UUID, Float> dmg = new HashMap<>();
        final float[] how = new float[4];
    }
    static final Map<Mob, St> LIVE = new WeakHashMap<>();

    // ------------------------------------------------------------------ setting up
    /** A bounty boss arrives: scaled to the mage who took the poster. */
    static void setup(Mob m, ServerPlayer owner, int boss, boolean hardened) {
        CompoundTag t = new CompoundTag();
        t.putInt("b", boss);
        t.putInt("t", BossLadder.TIER[Math.max(0, Math.min(6, boss))]);
        t.putInt("r", owner == null ? 0 : EliteMobs.threat(owner));
        t.putBoolean("h", hardened);
        t.putInt("m", owner == null ? 0 : BossLadder.memory(owner, boss));
        m.getPersistentData().put(TAG, t);
        float frac = m.getHealth() / Math.max(1, m.getMaxHealth());
        modifier(m, Attributes.MAX_HEALTH, HP_RANK, "Threat", 0.1 * t.getInt("r"));
        modifier(m, Attributes.MAX_HEALTH, HP_HARD, "Hardened", hardened ? 0.5 : 0);
        m.setHealth(m.getMaxHealth() * frac);
        St s = state(m, t);
        LIVE.put(m, s);
        if (owner != null && s.memory > 0)
            owner.sendSystemMessage(Component.literal(m.getDisplayName().getString() + " remembers you beat it with " + BossLadder.HOW[s.memory] + " - it guards against them for the first 30 s.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    static St state(Mob m, CompoundTag t) {
        St s = new St();
        s.boss = t.getInt("b"); s.tier = Math.max(1, t.getInt("t")); s.rank = t.getInt("r"); s.hardened = t.getBoolean("h"); s.memory = t.getInt("m");
        s.group = Math.max(1, t.getInt("g")); s.stage = t.getInt("s");
        return s;
    }

    public static boolean tracked(Entity e) { return e != null && e.getPersistentData().contains(TAG); }
    public static boolean hardened(Entity e) { return tracked(e) && e.getPersistentData().getCompound(TAG).getBoolean("h"); }

    static void modifier(LivingEntity m, net.minecraft.world.entity.ai.attributes.Attribute a, UUID id, String name, double amount) {
        AttributeInstance at = m.getAttribute(a);
        if (at == null) return;
        at.removeModifier(id);
        if (amount != 0) at.addPermanentModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && e.getEntity() instanceof Mob m && tracked(m) && !LIVE.containsKey(m))
            LIVE.put(m, state(m, m.getPersistentData().getCompound(TAG)));
    }

    // ------------------------------------------------------------------ the fight
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || LIVE.isEmpty()) return;
        for (var en : new ArrayList<>(LIVE.entrySet())) {
            Mob m = en.getKey();
            if (m == null) continue;
            if (!m.isAlive() || m.isRemoved()) { LIVE.remove(m); continue; }   // (unloaded: it comes back through onJoin)
            if (m.level() instanceof ServerLevel sl) tick(m, en.getValue(), sl);
        }
    }

    static void tick(Mob m, St s, ServerLevel sl) {
        long now = sl.getGameTime();
        if (now % 20 == 0) group(m, s, sl);
        if (s.first == 0) return;   // nothing more until the fight starts
        // stages
        float frac = m.getHealth() / Math.max(1, m.getMaxHealth());
        float[] lines = LINES[Math.min(3, s.tier)];
        if (s.stage < lines.length && frac <= lines[s.stage]) stage(m, s, sl);
        if (s.tier >= 3 && !s.lastStand && frac <= 0.1f) {
            s.lastStand = true;
            s.lastStandUntil = now + LAST_STAND;
            FxSend.impactFrame(sl, mid(m), 4, FxSend.FRAME_LIGHT, 32);
            FxSend.shake(sl, m.position(), 0.8f, 16, 40);
            tell(sl, m, Component.literal("LAST STAND  ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                    .append(Component.literal(m.getDisplayName().getString() + " won't fall easily - hold on for 6 s.").withStyle(ChatFormatting.RED)));
        }
        if (s.lastStand && now < s.lastStandUntil && now % 10 == 0) FxSend.ring(sl, Fx.CRIMSON, m.position().add(0, 0.1, 0), 0.4f, 3.2f, 0.08f, 10);
        // enrage
        if (!s.enraged && now - s.first > (s.hardened ? ENRAGE_HARD : ENRAGE_TICKS)) {
            s.enraged = true;
            modifier(m, Attributes.MOVEMENT_SPEED, SPD_RAGE, "Enraged", 0.2);
            tell(sl, m, Component.literal(m.getDisplayName().getString() + " is ENRAGED - it hits 25% harder and moves faster.").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            sl.playSound(null, m.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.6f, 0.7f);
        }
        if (s.enraged && now % 40 == 0) FxSend.ring(sl, Fx.CRIMSON, m.position().add(0, 0.05, 0), 1.2f, 1.4f, 0.05f, 30);
        // weak points
        if (now >= s.weakNext && now >= s.weakUntil) {
            s.weakNext = now + WEAK_EVERY;
            s.weakUntil = now + WEAK_OPEN;
            s.weakOff = new float[]{180f, 90f, -90f}[ThreadLocalRandom.current().nextInt(3)];
            tell(sl, m, Component.literal("A weak point opens on its " + (s.weakOff == 180f ? "back" : s.weakOff > 0 ? "left side" : "right side") + "!").withStyle(ChatFormatting.GOLD), true);
            sl.playSound(null, m.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.6f, 1.6f);
        }
        if (now < s.weakUntil && now % 2 == 0) {
            Vec3 at = mid(m).add(weakDir(m, s).scale(m.getBbWidth() * 0.6 + 0.35));
            Kit.orb(sl, Fx.GOLD, at, Vec3.ZERO, 0.32f, 3);
            if (now % 10 == 0) FxSend.ring(sl, Fx.GOLD, at, 0.2f, 0.6f, 0.04f, 8);
        }
        // staggered: it stands still
        if (now < s.stagUntil) { m.getNavigation().stop(); m.setDeltaMovement(0, Math.min(0, m.getDeltaMovement().y), 0); }
        // the Reckoning
        if ((s.rank >= 3 || s.hardened) && now >= s.reckonNext) {
            s.reckonNext = now + RECKON_EVERY;
            reckon(m, s, sl);
        }
    }

    static Vec3 mid(Entity e) { return e.position().add(0, e.getBbHeight() * 0.55, 0); }
    static Vec3 weakDir(Mob m, St s) { return Vec3.directionFromRotation(0, m.yBodyRot + s.weakOff); }

    /** Everyone near the boss hears it (the chat, or the action bar). */
    static void tell(ServerLevel sl, Mob m, Component c) { tell(sl, m, c, false); }
    static void tell(ServerLevel sl, Mob m, Component c, boolean bar) {
        for (ServerPlayer p : sl.players()) if (p.distanceToSqr(m) < 48 * 48) { if (bar) p.displayClientMessage(c, true); else p.sendSystemMessage(c); }
    }

    static void group(Mob m, St s, ServerLevel sl) {
        int n = 0;
        for (ServerPlayer p : sl.players()) if (!p.isSpectator() && !p.isCreative() && p.distanceToSqr(m) < 40 * 40) n++;
        if (n <= s.group) return;
        s.group = n;
        m.getPersistentData().getCompound(TAG).putInt("g", n);
        float frac = m.getHealth() / Math.max(1, m.getMaxHealth());
        modifier(m, Attributes.MAX_HEALTH, HP_GROUP, "Group", GROUP_HP * (n - 1));
        m.setHealth(m.getMaxHealth() * frac);
        tell(sl, m, Component.literal(n + " mages have come - " + m.getDisplayName().getString() + " grows to meet you.").withStyle(ChatFormatting.GRAY), true);
    }

    static void stage(Mob m, St s, ServerLevel sl) {
        s.stage++;
        m.getPersistentData().getCompound(TAG).putInt("s", s.stage);
        modifier(m, Attributes.MOVEMENT_SPEED, SPD_STAGE, "Stage", 0.1 * s.stage);
        Vec3 c = m.position();
        FxSend.ring(sl, Fx.CRIMSON, c.add(0, 0.1, 0), 0.5f, 7f, 0.15f, 14);
        FxSend.shake(sl, c, 0.5f, 10, 24);
        for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, new AABB(c, c).inflate(6, 3, 6))) {
            if (p.isSpectator()) continue;
            Vec3 push = p.position().subtract(c);
            push = push.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : new Vec3(push.x, 0, push.z).normalize();
            p.setDeltaMovement(push.scale(1.1).add(0, 0.4, 0));
            p.hurtMarked = true;
        }
        sl.playSound(null, m.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.2f, 1.2f);
        tell(sl, m, Component.literal(m.getDisplayName().getString() + " - stage " + (s.stage + 1) + "/" + (LINES[Math.min(3, s.tier)].length + 1) + ": it grows fiercer.").withStyle(ChatFormatting.RED), true);
    }

    /** The Reckoning: a ring under one of you, then a burst where it was. */
    static void reckon(Mob m, St s, ServerLevel sl) {
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer p : sl.players()) if (!p.isSpectator() && !p.isCreative() && p.distanceToSqr(m) < 24 * 24) near.add(p);
        if (near.isEmpty()) return;
        ServerPlayer t = near.get(ThreadLocalRandom.current().nextInt(near.size()));
        Vec3 at = t.position();
        ZenonAI.warnCircle(sl, at, 2.4f, 24);
        float dmg = 4f + 3f * s.tier + s.rank;
        AstaFx.later(24, () -> {
            if (!m.isAlive()) return;
            FxSend.spike(sl, Fx.CRIMSON, at, new Vec3(0, 1, 0), 2.6f, 0.4f, 12, 2, com.ironspellgrimoire.network.FxPacket.GROUND);
            FxSend.ring(sl, Fx.CRIMSON, at.add(0, 0.1, 0), 0.3f, 2.6f, 0.1f, 10);
            for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, new AABB(at, at).inflate(2.4, 2.5, 2.4)))
                if (!p.isSpectator()) p.hurt(m.damageSources().mobAttack(m), dmg);
            sl.playSound(null, BlockPos.containing(at), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 1.2f, 0.7f);
        });
    }

    // ------------------------------------------------------------------ blows
    static int how(DamageSource src) {
        if (BookTree.melee(src)) return 1;
        if (src instanceof io.redspace.ironsspellbooks.damage.SpellDamageSource) return 2;
        if (src.is(DamageTypeTags.IS_PROJECTILE) || src.getDirectEntity() != src.getEntity()) return 3;
        return 0;
    }

    /** It can't strike while staggered. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        if (e.getSource().getEntity() instanceof Mob m && LIVE.get(m) instanceof St s && m.level().getGameTime() < s.stagUntil) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent e) {
        DamageSource src = e.getSource();
        long now = e.getEntity().level().getGameTime();
        // its blows: Hardened, enraged, a last stand
        if (src.getEntity() instanceof Mob a && LIVE.get(a) instanceof St sa) {
            float k = (sa.hardened ? 1.2f : 1f) * (sa.enraged ? 1.25f : 1f) * (sa.lastStand && now < sa.lastStandUntil ? 1.3f : 1f);
            e.setAmount(e.getAmount() * k);
        }
        if (!(e.getEntity() instanceof Mob m) || !(LIVE.get(m) instanceof St s) || !(m.level() instanceof ServerLevel sl)) return;
        Entity by = src.getEntity();
        if (by instanceof ServerPlayer && s.first == 0) { s.first = now; s.reckonNext = now + 100; s.weakNext = now + 160; }
        float amt = e.getAmount();
        int how = how(src);
        if (s.memory > 0 && how == s.memory && now - s.first < MEMORY_TICKS) amt *= 0.75f;
        if (s.lastStand && now < s.lastStandUntil) amt *= 0.5f;
        if (now < s.weakUntil && by != null) {
            Vec3 to = by.position().subtract(m.position()), wd = weakDir(m, s);
            Vec3 flat = new Vec3(to.x, 0, to.z);
            if (flat.lengthSqr() > 1e-4 && flat.normalize().dot(wd) > Math.cos(Math.toRadians(55))) {
                s.weakUntil = 0;
                s.stagUntil = now + STAGGER;
                TimedModifiers.apply(m, Attributes.MOVEMENT_SPEED, SPD_STAGGER, "Staggered", -0.9, AttributeModifier.Operation.MULTIPLY_TOTAL, STAGGER);
                FxSend.impactFrame(sl, mid(m), 3, FxSend.FRAME_GOLD, 24);
                FxSend.ring(sl, Fx.GOLD, mid(m), 0.3f, 3f, 0.1f, 12);
                sl.playSound(null, m.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2f, 0.6f);
                tell(sl, m, Component.literal("STAGGERED!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            }
        }
        if (now < s.stagUntil) amt *= 1.5f;
        e.setAmount(amt);
    }

    /** Who did how much (after armour), for the shared kill and the boss's memory. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof Mob m) || !(LIVE.get(m) instanceof St s)) return;
        if (e.getSource().getEntity() instanceof ServerPlayer p) {
            s.dmg.merge(p.getUUID(), e.getAmount(), Float::sum);
            s.how[how(e.getSource())] += e.getAmount();
        }
    }

    // ------------------------------------------------------------------ the end of it
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead instanceof ServerPlayer p) {   // a fall, for the log
            for (var en : LIVE.entrySet()) if (en.getKey() != null && en.getKey().level() == p.level() && en.getKey().distanceToSqr(p) < 48 * 48 && en.getValue().dmg.containsKey(p.getUUID()))
                BossLadder.fell(p, en.getValue().boss);
            return;
        }
        if (!(dead instanceof Mob m) || !(LIVE.get(m) instanceof St s) || !(m.level() instanceof ServerLevel sl)) return;
        LIVE.remove(m);
        if (Rifts.isShade(m)) return;
        long ticks = s.first == 0 ? 0 : sl.getGameTime() - s.first;
        int how = 0;
        for (int i = 1; i < 4; i++) if (s.how[i] > s.how[how]) how = i;
        UUID owner = m.getPersistentData().hasUUID("isg_bounty_owner") ? m.getPersistentData().getUUID("isg_bounty_owner") : null;
        WorldGrowth.bossFell(sl.getServer(), s.boss);
        for (UUID id : s.dmg.keySet()) {
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
            if (p == null) continue;
            boolean isOwner = id.equals(owner);
            BossLadder.killed(p, s.boss, ticks, how);
            give(p, core(s.tier));
            if (isOwner || ThreadLocalRandom.current().nextFloat() < 0.6f) give(p, signature(s.boss, s.hardened));
            if (!isOwner) {
                GrimoireMastery.bountyXp(p, 600 + 300 * s.tier);
                p.sendSystemMessage(Component.literal("You helped bring down " + m.getDisplayName().getString() + " - it counts in your log, too.").withStyle(ChatFormatting.GREEN));
            }
            if (ticks > 0) p.displayClientMessage(Component.literal("Time: " + time(ticks)).withStyle(ChatFormatting.YELLOW), true);
        }
    }

    public static String time(long ticks) { long sec = ticks / 20; return sec / 60 + ":" + String.format("%02d", sec % 60); }

    static void give(ServerPlayer p, ItemStack s) { if (!s.isEmpty() && !p.getInventory().add(s)) p.drop(s, false); }

    /** A Mana Core of a tier (the Star Forge's strongest mutations need a tier III core). */
    public static ItemStack core(int tier) {
        ItemStack c = new ItemStack(AddonItemRegistry.MANA_CORE.get());
        if (tier > 1) c.getOrCreateTag().putInt("Tier", Math.min(3, tier));   // tier I stays plain, so it stacks with the cores from quests and raids
        return c;
    }
    public static int coreTier(ItemStack s) { return com.ironspellgrimoire.item.ManaCoreItem.tier(s); }

    /** A boss's signature drop - the upgraded one from a Hardened kill. */
    public static ItemStack signature(int boss, boolean hardened) {
        if (boss < 0 || boss >= AddonItemRegistry.SIGNATURES.size()) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(AddonItemRegistry.SIGNATURES.get(boss).get());
        if (hardened) s.getOrCreateTag().putBoolean("Hardened", true);
        return s;
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) { LIVE.clear(); }

    // the game tests
    public static void testSetup(Mob m, ServerPlayer owner, int boss, boolean hardened) { setup(m, owner, boss, hardened); }
    public static int testStage(Mob m) { St s = LIVE.get(m); return s == null ? -1 : s.stage; }
    public static int testGroup(Mob m) { St s = LIVE.get(m); return s == null ? -1 : s.group; }
    public static boolean testEnraged(Mob m) { St s = LIVE.get(m); return s != null && s.enraged; }
    public static void testStart(Mob m, long ago) { St s = LIVE.get(m); if (s != null) s.first = m.level().getGameTime() - ago; }
    public static void testWeak(Mob m, float off) { St s = LIVE.get(m); if (s != null) { s.weakOff = off; s.weakUntil = m.level().getGameTime() + WEAK_OPEN; s.weakNext = s.weakUntil + WEAK_EVERY; } }
    public static boolean testStaggered(Mob m) { St s = LIVE.get(m); return s != null && m.level().getGameTime() < s.stagUntil; }
    public static void testTick(Mob m) { St s = LIVE.get(m); if (s != null && m.level() instanceof ServerLevel sl) { tick(m, s, sl); group(m, s, sl); } }
}
