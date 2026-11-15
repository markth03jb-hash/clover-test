package com.ironspellgrimoire.server;

import com.ironspellgrimoire.entity.ShadowSoldier;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.ShadowPackets;
import com.ironspellgrimoire.registry.AddonEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Shadow Grimoire's army: every shadow a player has raised, saved with them (it survives death). Each has a kind
 * (the mob it was), a name, a level (1-30, from its own kills), a rank and whether it is equipped - equipped shadows
 * rise and follow their master whenever the grimoire is held, up to the slots its mastery allows (3, up to 10).
 *
 *  ranks: 0 Soldier, 1 Elite (lv 8), 2 Knight (lv 14), 3 Captain (lv 20), 4 General (lv 26). The exclusive shadows
 *  (Oathbound Knight, Ravener) join at Knight and rank up the same way to General. The army has room for only one
 *  General and three Captains.
 *  each rank: +25% health, +20% damage, a little bigger, eyes brighter, and a perk - Elite armour, Knight cleave,
 *  Captain war cry (shadows round it hit harder), General lifesteal.
 *
 * A fallen shadow isn't lost: it returns to its master's shadow and rises again after 30 s (3 s in Sovereign's Domain).
 */
public final class ShadowArmy {
    private ShadowArmy() {}

    static final String KEY = "isg_shadow_army", NEXT = "isg_shadow_next", GATE = "isg_shadow_gate";
    public static final String[] RANKS = {"Soldier", "Elite", "Knight", "Captain", "General"};
    static final int[] RANK_LEVEL = {0, 8, 14, 20, 26, 30};
    public static final int MAX_LEVEL = 30, STORAGE = 50;
    public static final String KNIGHT = "iron_spell_grimoire:oathbound_knight", RAVENER = "iron_spell_grimoire:ravener";
    /** The exclusive shadows, won in a Challenge (menu): beat its living form, then raise its remnant. */
    public static final String[] EXCLUSIVES = {KNIGHT, RAVENER};
    static final String DEFEATED = "isg_shadow_beaten_";

    /** The army's saved data lives in the part of the player's data that survives death. */
    static CompoundTag root(Player p) {
        CompoundTag pd = p.getPersistentData();
        if (!pd.contains(Player.PERSISTED_NBT_TAG)) pd.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return pd.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static ListTag list(Player p) {
        CompoundTag r = root(p);
        if (!r.contains(KEY, Tag.TAG_LIST)) r.put(KEY, new ListTag());
        return r.getList(KEY, Tag.TAG_COMPOUND);
    }

    public static CompoundTag find(Player p, int id) {
        for (Tag t : list(p)) if (((CompoundTag) t).getInt("id") == id) return (CompoundTag) t;
        return null;
    }

    public static int count(Player p) { return list(p).size(); }

    /** How many shadows can follow at once: 3, rising with the grimoire's mastery to 10. */
    public static int slots(ServerPlayer p) {
        ItemStack b = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
        int lv = b == null ? 1 : GrimoireMastery.level(b);
        return Math.min(10, 3 + (lv - 1) * 2 + (lv >= GrimoireMastery.MAX_LEVEL ? 1 : 0));
    }

    public static Vec3 stationOf(CompoundTag e) {
        net.minecraft.nbt.ListTag l = e.getList("st", Tag.TAG_DOUBLE);
        return new Vec3(l.getDouble(0), l.getDouble(1), l.getDouble(2));
    }

    /** Station a shadow where it stands (or call it back to your side). */
    static void toggleStation(ServerPlayer p, CompoundTag e) {
        ShadowSoldier s = summoned(p, e.getInt("id"));
        if (e.contains("st")) {
            e.remove("st"); e.remove("stdim");
            if (s != null) s.station = null;
            p.displayClientMessage(Component.literal(e.getString("name") + " returns to your side").withStyle(ChatFormatting.AQUA), true);
            return;
        }
        if (s == null) { p.displayClientMessage(Component.literal("It must be out at your side to be stationed").withStyle(ChatFormatting.GRAY), true); return; }
        net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
        for (double v : new double[]{s.getX(), s.getY(), s.getZ()}) l.add(net.minecraft.nbt.DoubleTag.valueOf(v));
        e.put("st", l);
        e.putString("stdim", p.serverLevel().dimension().location().toString());
        s.station = s.position();
        p.displayClientMessage(Component.literal(e.getString("name") + " keeps watch here - Shadow Step brings you back to it").withStyle(ChatFormatting.AQUA), true);
    }

    public static int equippedCount(Player p) {
        int n = 0;
        for (Tag t : list(p)) if (((CompoundTag) t).getBoolean("eq") && !((CompoundTag) t).getBoolean("excl")) n++;
        return n;
    }

    /** XP a shadow needs for its next level. */
    public static int xpNext(int level) { return (int) Math.round(20 * Math.pow(level, 1.4)); }

    public static final int MAX_RANK = 4, GENERALS = 1, CAPTAINS = 3;

    public static boolean canRankUp(CompoundTag e) {
        int rank = e.getInt("rank");
        return rank < MAX_RANK && e.getInt("lvl") >= RANK_LEVEL[rank + 1];
    }

    static int atRank(Player p, int rank) {
        int n = 0;
        for (Tag t : list(p)) if (((CompoundTag) t).getInt("rank") == rank) n++;
        return n;
    }

    /** Why it can't take its next rank even when strong enough: 0 nothing, 1 the army already has its General, 2 all three Captains are taken. */
    public static int rankBlock(Player p, CompoundTag e) {
        int next = e.getInt("rank") + 1;
        if (next == 4 && atRank(p, 4) >= GENERALS) return 1;
        if (next == 3 && atRank(p, 3) >= CAPTAINS) return 2;
        return 0;
    }

    public static String typeName(String type) {
        if (KNIGHT.equals(type)) return "Oathbound Knight";
        if (RAVENER.equals(type)) return "Ravener";
        EntityType<?> t = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(type));
        return t == null ? type : t.getDescription().getString();
    }

    /** A new shadow joins the army (equipped if there's a free slot). Returns it, or null if the army is full. */
    public static CompoundTag add(ServerPlayer p, String type, boolean exclusive) {
        if (count(p) >= STORAGE) {
            p.displayClientMessage(Component.literal("Your army is full (" + STORAGE + ") - release a shadow in the menu").withStyle(ChatFormatting.GRAY), true);
            return null;
        }
        CompoundTag r = root(p);
        int id = r.getInt(NEXT) + 1;
        r.putInt(NEXT, id);
        CompoundTag e = new CompoundTag();
        e.putInt("id", id);
        e.putString("type", type);
        e.putString("name", typeName(type));
        e.putInt("lvl", 1);
        e.putInt("xp", 0);
        e.putInt("rank", exclusive ? 2 : 0);
        e.putBoolean("excl", exclusive);
        e.putBoolean("eq", exclusive || equippedCount(p) < slots(p));
        list(p).add(e);
        return e;
    }

    /** A shadow earned XP (its own kill). */
    public static void reward(ServerPlayer owner, int id, float amount) {
        CompoundTag e = find(owner, id);
        if (e == null) return;
        int lvl = e.getInt("lvl");
        if (lvl >= MAX_LEVEL) return;
        int xp = e.getInt("xp") + Math.max(2, Math.round(amount));
        while (lvl < MAX_LEVEL && xp >= xpNext(lvl)) {
            xp -= xpNext(lvl);
            lvl++;
            owner.displayClientMessage(Component.literal(e.getString("name") + " reached level " + lvl + (canRankUpAt(e, lvl) ? " - it can rank up" : ""))
                    .withStyle(ChatFormatting.DARK_AQUA), true);
        }
        e.putInt("lvl", lvl);
        e.putInt("xp", xp);
        ShadowSoldier s = summoned(owner, id);
        if (s != null) s.applyStats(lvl, e.getInt("rank"));
    }

    private static boolean canRankUpAt(CompoundTag e, int lvl) {
        int rank = e.getInt("rank");
        return rank < MAX_RANK && lvl >= RANK_LEVEL[rank + 1];
    }

    // ================================================================== the shadows at their master's side
    private static final Map<UUID, Map<Integer, ShadowSoldier>> OUT = new HashMap<>();

    public static ShadowSoldier summoned(ServerPlayer p, int id) {
        Map<Integer, ShadowSoldier> m = OUT.get(p.getUUID());
        ShadowSoldier s = m == null ? null : m.get(id);
        return s != null && s.isAlive() ? s : null;
    }

    public static List<ShadowSoldier> out(ServerPlayer p) {
        Map<Integer, ShadowSoldier> m = OUT.get(p.getUUID());
        List<ShadowSoldier> l = new ArrayList<>();
        if (m != null) for (ShadowSoldier s : m.values()) if (s.isAlive()) l.add(s);
        return l;
    }

    /** Raise one of the army's shadows at a spot (it climbs up out of a pool of shadow). */
    public static ShadowSoldier raise(ServerPlayer p, CompoundTag e, Vec3 at, int temporary) {
        ServerLevel sl = p.serverLevel();
        String type = e.getString("type");
        ShadowSoldier s = KNIGHT.equals(type) ? AddonEntities.OATHBOUND_KNIGHT.get().create(sl)
                : RAVENER.equals(type) ? AddonEntities.SHADOW_RAVENER.get().create(sl)
                : AddonEntities.SHADOW_SOLDIER.get().create(sl);
        if (s == null) return null;
        s.setup(p.getUUID(), e.getString("type"), e.getInt("id"), e.getInt("lvl"), e.getInt("rank"), e.getString("name"), temporary);
        if (temporary <= 0 && e.contains("st")) s.station = stationOf(e);
        s.moveTo(at.x, at.y, at.z, p.getYRot(), 0);
        sl.addFreshEntity(s);
        if (temporary <= 0) OUT.computeIfAbsent(p.getUUID(), k -> new HashMap<>()).put(e.getInt("id"), s);
        ShadowFx.risePool(sl, at, s.getBbWidth());
        return s;
    }

    /** Sink every shadow at a player's side back into their shadow. */
    public static void recallAll(ServerPlayer p) {
        Map<Integer, ShadowSoldier> m = OUT.remove(p.getUUID());
        if (m != null) for (ShadowSoldier s : m.values()) if (s.isAlive()) { ShadowFx.sinkPool(p.serverLevel(), s.position(), s.getBbWidth()); s.discard(); }
    }

    /** A shadow fell: back to its master's shadow, rising again later. */
    public static void fell(ShadowSoldier s) {
        if (s.temporary() > 0 || !(s.level() instanceof ServerLevel sl)) return;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(s.ownerUUID());
        if (p == null) return;
        CompoundTag e = find(p, s.armyId());
        if (e != null) e.putLong("rec", sl.getGameTime() + (Moves.inMode(p, ShadowFx.MODE) ? 60 : 600));
        Map<Integer, ShadowSoldier> m = OUT.get(p.getUUID());
        if (m != null) m.remove(s.armyId());
    }

    /** Keep the equipped shadows at their master's side while the grimoire is held (each second). */
    static void keep(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        boolean gate = root(p).getBoolean(GATE);   // stored in their master's shadow (Gate of Shade)
        Map<Integer, ShadowSoldier> m = OUT.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        m.values().removeIf(s -> !s.isAlive() || s.isRemoved());
        for (Tag t : list(p)) {
            CompoundTag e = (CompoundTag) t;
            int id = e.getInt("id");
            ShadowSoldier s = m.get(id);
            boolean stationed = e.contains("st");
            boolean want = e.getBoolean("eq") && !gate && sl.getGameTime() >= e.getLong("rec")
                    && (!stationed || (e.getString("stdim").equals(sl.dimension().location().toString()) && sl.isLoaded(net.minecraft.core.BlockPos.containing(stationOf(e)))));
            if (want && s == null) {
                Vec3 at = stationed ? stationOf(e) : p.position().add(new Vec3(Math.cos(id * 2.4) * 2.5, 0, Math.sin(id * 2.4) * 2.5));
                raise(p, e, at, 0);
            } else if (!want && s != null) {
                ShadowFx.sinkPool(sl, s.position(), s.getBbWidth());
                s.discard();
                m.remove(id);
            } else if (s != null && !stationed && s.distanceToSqr(p) > 40 * 40) {   // left far behind: it steps out of its master's shadow
                Vec3 at = p.position().add(Math.cos(id * 2.4) * 2, 0, Math.sin(id * 2.4) * 2);
                s.teleportTo(at.x, at.y, at.z);
            }
        }
    }

    // ================================================================== Challenges
    private static final Map<UUID, ShadowSoldier> CHALLENGE = new HashMap<>();

    static boolean inArmy(Player p, String type) {
        for (Tag t : list(p)) if (type.equals(((CompoundTag) t).getString("type"))) return true;
        return false;
    }

    /** 0 can challenge, 1 fighting it now, 2 beaten - raise it, 3 in your army. */
    static int challengeState(ServerPlayer p, int i) {
        String type = EXCLUSIVES[i];
        if (inArmy(p, type)) return 3;
        ShadowSoldier c = CHALLENGE.get(p.getUUID());
        if (c != null && c.isAlive() && type.equals(c.kind())) return 1;
        return root(p).getBoolean(DEFEATED + i) ? 2 : 0;
    }

    /** Call out an exclusive shadow's living form to fight you. */
    public static ShadowSoldier challenge(ServerPlayer p, int i) {
        if (i < 0 || i >= EXCLUSIVES.length || challengeState(p, i) != 0) return null;
        ShadowSoldier cur = CHALLENGE.get(p.getUUID());
        if (cur != null && cur.isAlive()) {
            p.displayClientMessage(Component.literal("Finish the challenge you're in first").withStyle(ChatFormatting.GRAY), true);
            return null;
        }
        ServerLevel sl = p.serverLevel();
        ShadowSoldier s = i == 0 ? AddonEntities.OATHBOUND_KNIGHT.get().create(sl) : AddonEntities.SHADOW_RAVENER.get().create(sl);
        if (s == null) return null;
        Vec3 f = p.getLookAngle().multiply(1, 0, 1);
        f = f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
        Vec3 at = p.position().add(f.scale(9));
        float yaw = (float) (Math.atan2(-(p.getX() - at.x), p.getZ() - at.z) * 180 / Math.PI);
        s.moveTo(at.x, p.getY(), at.z, yaw, 0);
        s.setYHeadRot(yaw); s.yBodyRot = yaw;
        s.setupLiving(p, EXCLUSIVES[i], typeName(EXCLUSIVES[i]));
        sl.addFreshEntity(s);
        CHALLENGE.put(p.getUUID(), s);
        ShadowFx.challengeFx(sl, s);
        p.displayClientMessage(Component.literal(typeName(EXCLUSIVES[i]) + " answers your challenge").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        return s;
    }

    /** The challenge ended without a win (the challenger fell, fled or ran out of time). */
    public static void challengeOver(ShadowSoldier s) {
        if (s.challenger() != null) CHALLENGE.remove(s.challenger(), s);
    }

    /** Its living form fell: it is beaten, and its remnant waits to be raised. */
    public static void challengeWon(ShadowSoldier s) {
        challengeOver(s);
        if (!(s.level() instanceof ServerLevel sl) || s.challenger() == null) return;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(s.challenger());
        if (p == null) return;
        int i = KNIGHT.equals(s.kind()) ? 0 : 1;
        root(p).putBoolean(DEFEATED + i, true);
        ShadowFx.exclusiveRemnant(p, s);
        p.displayClientMessage(Component.literal(typeName(s.kind()) + " is beaten - cast Rise on its remnant").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
    }

    /** Raise a beaten exclusive shadow from the menu (if its remnant was missed). */
    public static void arise(ServerPlayer p, int i) {
        if (i < 0 || i >= EXCLUSIVES.length || challengeState(p, i) != 2 || !ShadowFx.equipped(p)) return;
        CompoundTag e = add(p, EXCLUSIVES[i], true);
        if (e == null) return;
        root(p).putBoolean(GATE, false);
        raise(p, e, p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(2.5)), 0);
        p.displayClientMessage(Component.literal(e.getString("name") + " rises as your shadow").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
    }

    // ================================================================== the menu
    public static void open(ServerPlayer p) {
        List<ShadowPackets.Entry> l = new ArrayList<>();
        long now = p.serverLevel().getGameTime();
        for (Tag t : list(p)) {
            CompoundTag e = (CompoundTag) t;
            int rec = (int) Math.max(0, (e.getLong("rec") - now) / 20);
            l.add(new ShadowPackets.Entry(e.getInt("id"), e.getString("type"), e.getString("name"), e.getInt("lvl"), e.getInt("xp"), xpNext(e.getInt("lvl")),
                    e.getInt("rank"), e.getBoolean("eq"), e.getBoolean("excl"), rec, canRankUp(e) && rankBlock(p, e) == 0, rankBlock(p, e), e.contains("st")));
        }
        int[] ch = new int[EXCLUSIVES.length];
        for (int i = 0; i < ch.length; i++) ch[i] = challengeState(p, i);
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), new ShadowPackets.Open(l, slots(p), STORAGE, ch));
    }

    public static void handle(ServerPlayer p, int action, int id, String name) {
        if (!ShadowFx.equipped(p) && action != 0) return;
        if (action == 0) { open(p); return; }
        if (action == 5) { challenge(p, id); return; }
        if (action == 6) { arise(p, id); keep(p); open(p); return; }
        if (action >= 10) { HunterAscension.handle(p, action, id); return; }
        CompoundTag e = find(p, id);
        if (e == null) return;
        switch (action) {
            case 1 -> {   // equip / unequip
                if (!e.getBoolean("eq") && !e.getBoolean("excl") && equippedCount(p) >= slots(p)) {
                    p.displayClientMessage(Component.literal("All " + slots(p) + " slots are taken - unequip one first").withStyle(ChatFormatting.GRAY), true);
                } else e.putBoolean("eq", !e.getBoolean("eq"));
            }
            case 2 -> {   // rank up
                int block = rankBlock(p, e);
                if (canRankUp(e) && block != 0) {
                    p.displayClientMessage(Component.literal(block == 1 ? "Your army already has its General" : "All three Captain ranks are taken").withStyle(ChatFormatting.GRAY), true);
                } else if (canRankUp(e)) {
                    e.putInt("rank", e.getInt("rank") + 1);
                    p.displayClientMessage(Component.literal(e.getString("name") + " ranks up: " + RANKS[e.getInt("rank")]).withStyle(ChatFormatting.AQUA), true);
                    ShadowSoldier s = summoned(p, id);
                    if (s != null) { s.applyStats(e.getInt("lvl"), e.getInt("rank")); ShadowFx.rankUpFx(p.serverLevel(), s); }
                    ShadowFx.play(p.serverLevel(), p.position(), "shadow_rise", 1.0f, 1.4f);
                }
            }
            case 7 -> toggleStation(p, e);   // station here / call back
            case 3 -> {   // rename
                String n = name.strip();
                if (!n.isEmpty()) {
                    e.putString("name", n.length() > 24 ? n.substring(0, 24) : n);
                    ShadowSoldier s = summoned(p, id);
                    if (s != null) s.setShadowName(e.getString("name"));
                }
            }
            case 4 -> {   // release
                if (e.getBoolean("excl")) {
                    p.displayClientMessage(Component.literal(e.getString("name") + " is sworn to you - it can't be released").withStyle(ChatFormatting.GRAY), true);
                } else {
                    ShadowSoldier s = summoned(p, id);
                    if (s != null) { ShadowFx.sinkPool(p.serverLevel(), s.position(), s.getBbWidth()); s.discard(); }
                    ListTag l = list(p);
                    l.removeIf(t -> ((CompoundTag) t).getInt("id") == id);
                }
            }
            default -> { }
        }
        keep(p);
        open(p);
    }

    public static void clearAll() { OUT.clear(); CHALLENGE.clear(); }
}
