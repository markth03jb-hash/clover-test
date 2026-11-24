package com.ironspellgrimoire.server;

import com.ironspellgrimoire.AddonConfig;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.ElitePackets;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Elite mobs and threat levels.
 * <p>
 * <b>Threat:</b> each player has a threat rank 0-5 from the best grimoire they carry (its mastery: 2 = rank 1 ... 5 =
 * rank 4, past mastery = rank 5). A hostile mob that appears near them takes that rank: +20% health and +10% damage a
 * rank. A new mage sees rank 0 - the game as it always was.
 * <p>
 * <b>Elites:</b> about 5% of hostile mobs (up to ~12% at rank 5, twice that in rifts) roll 1-3 traits (EliteTraits).
 * Bosses, bounty targets and anything a player owns or summoned never do. The traits and rank are kept on the mob
 * (persistent data "isg_elite"), so an elite is still one after a reload. Players near one learn its traits for the
 * Codex. Elites give more XP and drops; champions (rank 4-5 only traits) a bounty star too.
 * <p>
 * Nothing here is a status effect, breaks a block or hides the view.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class EliteMobs {
    private EliteMobs() {}

    public static final String TAG = "isg_elite", SEEN = "isg_elite_seen";
    /** Automatic rolls are off while the game tests or screenshots run (they make their elites on purpose). */
    static final boolean AUTO = !(Boolean.getBoolean("isg.gametests") || Boolean.getBoolean("isg.uishots"));

    // ------------------------------------------------------------------ the traits
    public static final int BULWARK = 0, IRONHIDE = 1, SECOND_WIND = 2, OVERCHARGED = 3, SEEKER = 4, VOLLEY = 5, ECHO = 6, LEECH = 7,
            DISRUPTOR = 8, REFLECTOR = 9, ANCHORED = 10, HUNTER = 11, TETHER = 12, SPRINTER = 13, SPLITTER = 14, VOLATILE = 15,
            CURSED_LOOT = 16, VENGEFUL = 17, MIRROR_TWIN = 18, RIFT_BORN = 19, COUNT = 20;
    public static final String[] NAME = {"Bulwark", "Ironhide", "Second Wind", "Overcharged", "Seeker", "Volley", "Echo Strike", "Mana Leech",
            "Disruptor", "Reflector", "Anchored", "Hunter", "Tether", "Sprinter", "Splitter", "Volatile", "Cursed Loot", "Vengeful", "Mirror Twin", "Rift-Born"};
    /** What it does, then how to beat it (the Codex). */
    public static final String[][] SAYS = {
            {"Takes 70% less damage from the front.", "Get round it - hits from the side or behind land in full."},
            {"Stone plates soak up its first 3 hits; one cracks off each time.", "Break the plates with quick, cheap hits before your big ones."},
            {"Rises once more at half health, 20% faster.", "Save something for the second fight."},
            {"Every 4th blow it winds up for a second (a ring under it), then slams everything within 3 blocks.", "When the ring shows, step out of it."},
            {"Sends a slow homing orb every 5 s while it can see you.", "Break line of sight - the orb gives up."},
            {"Archers loose three arrows at once; others throw a dagger when you keep your distance.", "Fight it close, or from behind cover."},
            {"Each blow strikes the same spot again a second later, at half strength.", "Move after you're hit."},
            {"Each blow drains 10% of your mana, and heals it.", "Kill it fast, or keep it off you."},
            {"Its blows cut off a spell you're casting or charging.", "Cast from range, or between its swings."},
            {"Mirror shards spin round it: for 2 s in every 7 it sends projectiles back.", "Wait for the shards to fade, or use spells that don't fly."},
            {"Can't be knocked back, pushed or pulled.", "Don't count on pulls and knockback - fight it where it stands."},
            {"The further you are, the faster it runs (up to 60%).", "Kiting won't shake it; stand and fight."},
            {"Run more than 10 blocks off and it hooks you with a chain and drags you back.", "Kill it before you leave, or break line of sight."},
            {"Every 6 s it marks a line on the ground, then dashes along it.", "Step off the line."},
            {"Splits into two half-size copies when it dies.", "Have something that hits a group."},
            {"Leaves a ring when it dies that bursts 1.5 s later.", "Step out of the ring."},
            {"Drops a cursed chest. Open it within 3 s and it bites; wait, and it pays out double.", "Be patient."},
            {"Its ghost chases you for 5 s after it dies.", "Dodge it for a reward."},
            {"A shadow copy fights beside it. Both must fall within 5 s of each other, or the fallen one rises again.", "Bring them both low, then finish them together."},
            {"Dying tears open a small rift. Hit it 4 times in 5 s to close it, or three elites step out.", "Keep a few hits ready for the rift."}};
    /** Traits that need their own model: they roll only once those models are approved (until then, tests only). */
    public static final int MODEL_TRAITS = 1 << BULWARK | 1 << IRONHIDE | 1 << REFLECTOR | 1 << TETHER | 1 << CURSED_LOOT | 1 << RIFT_BORN;
    public static boolean modelsApproved = false;
    /** Champion traits: rank 4-5 only. */
    public static final int CHAMPION = 1 << MIRROR_TWIN | 1 << RIFT_BORN;

    // ------------------------------------------------------------------ the data on the mob
    public static CompoundTag data(Entity m) { return m.getPersistentData().getCompound(TAG); }
    public static boolean marked(Entity m) { return m.getPersistentData().contains(TAG); }
    public static int traits(Entity m) { return data(m).getInt("t"); }
    public static boolean has(Entity m, int trait) { return (traits(m) & (1 << trait)) != 0; }
    public static int rank(Entity m) { return data(m).getInt("r"); }
    public static boolean elite(Entity m) { return traits(m) != 0; }
    public static boolean champion(Entity m) { return (traits(m) & CHAMPION) != 0; }
    /** A Splitter's half-size copy, a Vengeful ghost, a Mirror Twin's shadow. */
    public static boolean child(Entity m) { return data(m).getBoolean("child"); }
    public static boolean ghost(Entity m) { return data(m).getBoolean("ghost"); }
    public static boolean shadow(Entity m) { return data(m).getBoolean("shadow"); }

    static void put(Mob m, CompoundTag d) { m.getPersistentData().put(TAG, d); }

    // ------------------------------------------------------------------ threat
    private static final Map<UUID, int[]> THREAT = new HashMap<>();   // {rank, gameTime it was worked out}

    /** A player's threat rank 0-5: the best grimoire they carry (equipped or in the inventory), by its mastery. */
    public static int threat(Player p) {
        long now = p.level().getGameTime();
        int[] c = THREAT.get(p.getUUID());
        if (c != null && now - c[1] < 200) return c[0];
        int best = 0;
        ItemStack eq = Utils.getPlayerSpellbookStack(p);
        if (eq != null && GrimoireMastery.bookOf(eq) != null) best = GrimoireMastery.level(eq);
        for (ItemStack s : p.getInventory().items) if (!s.isEmpty() && GrimoireMastery.bookOf(s) != null) best = Math.max(best, GrimoireMastery.level(s));
        int r = !AddonConfig.threat() ? 0 : best <= 1 ? 0 : best >= 6 ? 5 : best - 1;
        THREAT.put(p.getUUID(), new int[]{r, (int) now});
        return r;
    }
    public static void testForgetThreat(Player p) { THREAT.remove(p.getUUID()); }
    public static ElitePackets.Sync testPacket(Mob m) { return packet(m); }
    public static void testThreat(Player p, int r) { THREAT.put(p.getUUID(), new int[]{r, (int) p.level().getGameTime()}); }

    static final UUID RANK_HP = UUID.fromString("e1173000-0000-4000-8000-000000000001"), RANK_DMG = UUID.fromString("e1173000-0000-4000-8000-000000000002");

    static void modifier(LivingEntity m, Attribute a, UUID id, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance at = m.getAttribute(a);
        if (at == null) return;
        at.removeModifier(id);
        if (amount != 0) at.addPermanentModifier(new AttributeModifier(id, name, amount, op));
    }

    /** Set a mob's rank: its health and damage modifiers. */
    static void applyRank(Mob m, int rank) {
        float frac = m.getHealth() / Math.max(1, m.getMaxHealth());
        modifier(m, Attributes.MAX_HEALTH, RANK_HP, "Threat rank", 0.2 * rank, AttributeModifier.Operation.MULTIPLY_TOTAL);
        modifier(m, Attributes.ATTACK_DAMAGE, RANK_DMG, "Threat rank", 0.1 * rank, AttributeModifier.Operation.MULTIPLY_TOTAL);
        m.setHealth(m.getMaxHealth() * frac);
    }

    // ------------------------------------------------------------------ who may be one
    /** Hostile, wild, not a boss, not a bounty, not owned or summoned, not one of the mod's own allies. */
    public static boolean eligible(Entity e) {
        if (!(e instanceof Mob m) || !(e instanceof Enemy) || !m.isAlive() || m.isNoAi()) return false;
        if (TimeStop.isBoss(m) || BossCores.isBoss(m.getType()) || m instanceof com.ironspellgrimoire.entity.AddonBoss) return false;
        CompoundTag pd = m.getPersistentData();
        if (pd.contains("isg_bounty_owner") || pd.contains(Rifts.SHADE_TAG) || pd.getBoolean("isg_unbound")) return false;
        if (m instanceof OwnableEntity o && o.getOwnerUUID() != null) return false;
        if (m instanceof io.redspace.ironsspellbooks.entity.mobs.IMagicSummon) return false;
        for (String k : pd.getAllKeys()) if (k.startsWith("isg_owner") || k.startsWith("isg_shadow") || k.startsWith("isg_summon")) return false;
        var id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(m.getType());
        return id == null || !id.getNamespace().equals("iron_spell_grimoire") || m.getType() == com.ironspellgrimoire.registry.AddonEntities.MAGE.get();
    }

    // ------------------------------------------------------------------ spawning
    private static final List<Mob> PENDING = new ArrayList<>();
    /** Every elite (and ghost) loaded on the server, with what it's doing now. */
    static final Map<Mob, EliteTraits.Run> ACTIVE = new WeakHashMap<>();

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        Entity en = e.getEntity();
        if (en instanceof net.minecraft.world.entity.projectile.AbstractArrow ar) { EliteTraits.onArrow(ar); return; }
        if (!(en instanceof Mob m)) return;
        if (marked(m)) {
            if (e.loadedFromDisk() && ghost(m)) { e.setCanceled(true); return; }   // a ghost never outlives its chase
            if (elite(m)) ACTIVE.computeIfAbsent(m, k -> new EliteTraits.Run());   // (a mob with only a rank has nothing to do each tick)
            return;
        }
        if (!e.loadedFromDisk() && AUTO && AddonConfig.elites()) PENDING.add(m);   // a tick later: whoever spawned it has tagged it by then
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!PENDING.isEmpty()) {
            List<Mob> now = new ArrayList<>(PENDING);
            PENDING.clear();
            for (Mob m : now) if (m.isAlive() && !marked(m) && eligible(m)) roll(m);
        }
        if (ACTIVE.isEmpty()) return;
        for (var en : new ArrayList<>(ACTIVE.entrySet())) {
            Mob m = en.getKey();
            if (m == null) continue;
            if (m.isRemoved() || !m.isAlive()) { ACTIVE.remove(m); continue; }
            EliteTraits.tick(m, en.getValue());
        }
        EliteTraits.tickShots();
    }

    /** A new hostile mob: take the threat of the nearest player within 48 blocks, then maybe become an elite. */
    static void roll(Mob m) {
        if (!(m.level() instanceof ServerLevel sl)) return;
        Player near = sl.getNearestPlayer(m, 48);
        if (near == null || near.isSpectator()) return;
        int rank = threat(near);
        boolean rift = sl.dimension() == Rifts.RIFT;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double chance = AddonConfig.eliteChance() * (1 + 0.28 * rank) * (rift ? 2 : 1);
        int mask = r.nextDouble() < chance ? pick(m, rank, 1 + (r.nextDouble() < 0.35 ? 1 : 0) + (rank >= 3 && r.nextDouble() < 0.3 ? 1 : 0)) : 0;
        if (rank == 0 && mask == 0) return;   // nothing to mark
        make(m, rank, mask);
    }

    /** Pick n traits this mob can have at this rank. */
    static int pick(Mob m, int rank, int n) {
        n = Math.min(n, AddonConfig.eliteMaxTraits());
        List<Integer> pool = new ArrayList<>();
        int off = AddonConfig.eliteOff();
        for (int i = 0; i < COUNT; i++) {
            int bit = 1 << i;
            if ((off & bit) != 0) continue;
            if ((MODEL_TRAITS & bit) != 0 && !modelsApproved) continue;
            if ((CHAMPION & bit) != 0 && rank < 4) continue;
            pool.add(i);
        }
        int mask = 0, champions = 0;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        while (n-- > 0 && !pool.isEmpty()) {
            int i = pool.remove(r.nextInt(pool.size()));
            if ((CHAMPION & (1 << i)) != 0) { if (champions > 0) { n++; continue; } champions++; }
            mask |= 1 << i;
        }
        return mask;
    }

    /** Make m an elite of this rank with these traits (0: just ranked). */
    public static void make(Mob m, int rank, int mask) {
        CompoundTag d = new CompoundTag();
        d.putInt("r", rank);
        d.putInt("t", mask);
        put(m, d);
        applyRank(m, rank);
        EliteTraits.setup(m, mask);
        m.setHealth(m.getMaxHealth());
        if (mask != 0) {
            m.setPersistenceRequired();   // an elite doesn't vanish when you look away
            ACTIVE.computeIfAbsent(m, k -> new EliteTraits.Run());
        }
        sync(m);
    }
    public static void testMake(Mob m, int rank, int mask) { make(m, rank, mask); }

    // ------------------------------------------------------------------ to the client
    /** What the client draws: the rank, the traits, and the bits of state that show (plates left, the shell's timing, a chain). */
    static ElitePackets.Sync packet(Mob m) {
        CompoundTag d = data(m);
        EliteTraits.Run run = ACTIVE.get(m);
        int flags = (d.getBoolean("child") ? 1 : 0) | (d.getBoolean("ghost") ? 2 : 0) | (d.getBoolean("shadow") ? 4 : 0);
        return new ElitePackets.Sync(m.getId(), d.getInt("r"), d.getInt("t"), flags, d.getInt("ih"), d.getInt("phase"),
                run == null ? -1 : run.chainTo, run == null ? 0 : run.chainUntil);
    }

    static void sync(Mob m) {
        if (!(m.level() instanceof ServerLevel)) return;
        AddonNetwork.send(PacketDistributor.TRACKING_ENTITY.with(() -> m), packet(m));
    }

    @SubscribeEvent
    public static void onTrack(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof Mob m && marked(m) && e.getEntity() instanceof ServerPlayer p)
            AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), packet(m));
    }

    /** A player near an elite learns its traits (the Codex lists the ones you've met). */
    static void seen(ServerPlayer p, int mask) {
        int had = p.getPersistentData().getInt(SEEN);
        if ((had | mask) == had) return;
        int now = had | mask, fresh = now & ~had;
        p.getPersistentData().putInt(SEEN, now);
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < COUNT; i++) if ((fresh & (1 << i)) != 0) names.append(names.length() > 0 ? ", " : "").append(NAME[i]);
        p.displayClientMessage(Component.literal("New elite trait: " + names + " - see the Codex (Elite Mobs)").withStyle(ChatFormatting.GOLD), true);
        sendSeen(p);
    }

    static void sendSeen(ServerPlayer p) {
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), new ElitePackets.Seen(p.getPersistentData().getInt(SEEN), threat(p)));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) sendSeen(p); }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { THREAT.remove(e.getEntity().getUUID()); }

    // ------------------------------------------------------------------ death and rewards
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeathFirst(LivingDeathEvent e) {
        if (e.getEntity() instanceof Mob m && marked(m) && EliteTraits.cheatDeath(m)) e.setCanceled(true);   // Second Wind
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof Mob m) || !marked(m) || !(m.level() instanceof ServerLevel sl)) return;
        ACTIVE.remove(m);
        if (ghost(m)) return;
        EliteTraits.died(m, sl, e.getSource().getEntity());
        if (champion(m) && e.getSource().getEntity() instanceof ServerPlayer p) {
            Bounties.addStars(p, 1);
            p.displayClientMessage(Component.literal("Champion defeated: +1 bounty star").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    @SubscribeEvent
    public static void onXp(LivingExperienceDropEvent e) {
        if (!marked(e.getEntity())) return;
        if (ghost(e.getEntity())) { e.setDroppedExperience(0); return; }
        int n = Integer.bitCount(traits(e.getEntity()));
        e.setDroppedExperience((int) (e.getDroppedExperience() * (1 + 0.6 * n + 0.2 * rank(e.getEntity()))));
    }

    /** Each trait: another copy of what it drops (never gear it wore or anything that wears out). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDrops(LivingDropsEvent e) {
        if (!marked(e.getEntity())) return;
        if (ghost(e.getEntity())) { e.getDrops().clear(); return; }
        int n = Integer.bitCount(traits(e.getEntity()));
        List<ItemStack> plain = new ArrayList<>();
        for (var it : e.getDrops()) { ItemStack s = it.getItem(); if (!s.isDamageableItem() && !s.hasTag()) plain.add(s.copy()); }
        for (int k = 0; k < n; k++)
            for (ItemStack s : plain) {
                if (ThreadLocalRandom.current().nextFloat() > 0.6f) continue;
                var it = new net.minecraft.world.entity.item.ItemEntity(e.getEntity().level(), e.getEntity().getX(), e.getEntity().getY() + 0.5, e.getEntity().getZ(), s.copy());
                it.setDefaultPickUpDelay();
                e.getDrops().add(it);
            }
        if (has(e.getEntity(), CURSED_LOOT) && e.getEntity() instanceof Mob m) EliteTraits.cursedChest(m, plain);
    }

    /** A Splitter's copies are 60% size, hitbox and all (the client does the same in EliteClient). */
    @SubscribeEvent
    public static void onSize(net.minecraftforge.event.entity.EntityEvent.Size e) {
        Entity en = e.getEntity();
        if (en.level() == null || en.level().isClientSide()) return;
        if (en.getPersistentData().contains(TAG) && child(en)) e.setNewSize(e.getNewSize().scale(0.6f), true);
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) { PENDING.clear(); ACTIVE.clear(); THREAT.clear(); EliteTraits.clearShots(); }
}
