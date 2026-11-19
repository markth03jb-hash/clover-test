package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.AddonBoss;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FortressPackets;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Architect of Ruin Grimoire: a pocket fortress inside a book (every name and look original).
 *
 * Stone (from kills, bosses and rifts) builds and upgrades rooms on a floor plan - 3x3 at first, 4x4 at book level 4,
 * 5x5 at 8. Each of the 24 rooms (and three prestige rooms) gives one spell and a small passive; rooms side by side (up, down, left, right) can
 * pair for a neighbour bonus. Four of your rooms' spells go in the book's four Room pages; the other pages are always
 * there: Rampart, Siege Mode (a ring of walls and towers round you, your garrison stepping out), The Fortress (the
 * menu) and, at book level 10 with mastery 5, the Citadel. Rooms 1-8 are open from the start; the rest come from
 * blueprints (one per book level, and from bosses, rifts and the world boss). Hostile mobs you defeat sometimes leave
 * a banner: claim it in the Garrison tab and that mob joins your garrison (FortressSiege).
 *
 * Nothing here ever places or changes a block: walls, towers and defenders are temporary entities that are never saved.
 * State: the player's persisted data, under "isg_fortress" (sent whole to its player as NBT: FortressPackets.Sync).
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class FortressFx {
    private FortressFx() {}

    static final String ROOT = "isg_fortress";
    public static final int ROOMS = 27, BASE_ROOMS = 24, MAX_LV = 10, ROOM_MAX = 5, SLOTS = 4, MAX_STARS = 5;
    /** The prestige rooms (24-26) open at these stars. */
    public static final int[] STAR_ROOM = {1, 3, 5};
    /** Siege Mode's shapes (the Citadel is always a ring). */
    public static final int RING = 0, LINE = 1, HORSESHOE = 2;
    public static final String[] SHAPE = {"Ring", "Line", "Horseshoe"};
    public static final int[] COST = {20, 40, 80, 160, 320};   // Stone for room level 1..5
    public static final int OPEN = 8;   // rooms 0-7 need no blueprint

    /** The rooms: name, wing (0 Martial, 1 Arcane, 2 Sacred, 3 Works), spell, cooldown (ticks), mana, passive, what the spell does. */
    public record Room(String name, int wing, String spell, int cd, int mana, String passive, String does) {}

    public static final Room[] ROOM = {
        new Room("Barracks", 0, "Call Guard", 400, 60, "+1 garrison room", "Two defenders from your garrison fight beside you for 15 s"),
        new Room("Armoury", 0, "Blade Rack", 160, 50, "+5% attack damage", "Six phantom weapons rain down where you look"),
        new Room("Training Yard", 0, "Drill Charge", 200, 40, "Defenders hit 10% harder", "Your defenders dash at your target and strike"),
        new Room("Watchtower", 0, "Arrow Slits", 120, 40, "Towers reach 4 blocks further", "A fan of twelve bolts"),
        new Room("Gatehouse", 0, "Portcullis", 240, 40, "Walls 20% tougher per level", "A gate slams down in front of you, throwing foes back and blocking them for 6 s"),
        new Room("War Room", 0, "Battle Plan", 300, 30, "Siege Mode cooldown -10%", "Mark a foe for 10 s: everything you command focuses it, and it takes 25% more from you"),
        new Room("Observatory", 1, "Foresight", 600, 50, "+10% mana regen", "For 20 s every foe near you shows through walls, and revealed foes take 10% more from you"),
        new Room("Library", 1, "Annotated Page", 400, 20, "+100 max mana", "Your next three spells give back 30% of their mana"),
        new Room("Arcanum", 1, "Ley Lance", 100, 45, "+5% spell power", "A piercing beam of ley light"),
        new Room("Scrying Pool", 1, "Mirror Ward", 300, 40, "-", "The next projectile in 5 s flies back at its shooter"),
        new Room("Alchemy Lab", 1, "Volatile Flask", 120, 40, "-", "A thrown flask bursts in a ring"),
        new Room("Rune Vault", 1, "Ward Sigil", 400, 70, "-", "A sigil on the ground: allies standing in it take 40% less damage for 8 s"),
        new Room("Chapel", 2, "Sanctuary Bell", 400, 80, "+1 heart", "Heals you and your allies nearby over 4 s"),
        new Room("Reliquary", 2, "Saint's Keepsake", 1200, 60, "-", "For 30 s, a blow that would kill you leaves you at 1 health instead (once)"),
        new Room("Crypt", 2, "Ossuary Sentries", 500, 80, "-", "Three bone sentries guard where you look for 20 s"),
        new Room("Bell Tower", 2, "Great Toll", 200, 50, "-", "A great bell rings: a shockwave throws foes back"),
        new Room("Garden", 2, "Quiet Grove", 600, 60, "+5% mana regen", "A circle that restores health and mana for 10 s"),
        new Room("Hall of Banners", 2, "Rally", 400, 50, "-", "You and your allies nearby attack 20% faster for 8 s"),
        new Room("Forge", 3, "Molten Bolt", 100, 40, "-", "A bolt of molten iron that leaves a burning patch"),
        new Room("Foundry", 3, "Hammerfall", 240, 70, "+5% armour", "A giant hammer slams down where you look"),
        new Room("Workshop", 3, "Ballista", 600, 90, "-", "Place a ballista that fires at foes for 20 s"),
        new Room("Siege Yard", 3, "Trebuchet Shot", 300, 70, "-", "A boulder lobbed far, crashing down on an area"),
        new Room("Cistern", 3, "Floodgate", 240, 60, "-", "A wall of water that sweeps foes away"),
        new Room("Treasury", 3, "Tribute", 1200, 30, "+25% Stone", "For 30 s, defeated foes give three times the Stone"),
        // the prestige rooms: opened by the fortress's stars (1, 3 and 5), never by blueprints
        new Room("Herald's Gate", 0, "Lure Horn", 300, 50, "In a siege, foes near your walls drift toward the gate", "Drags foes within 14 blocks toward where you look"),
        new Room("Map Room", 1, "Survey", 600, 40, "In a siege, every foe within 32 blocks shows through walls", "Every foe within 32 blocks shows through walls for 15 s, and takes 10% more from you"),
        new Room("Throne Room", 2, "Royal Decree", 600, 80, "Defenders fight one rank higher", "Your defenders heal fully and hit 30% harder for 10 s"),
    };
    public static final String[] WING = {"Martial", "Arcane", "Sacred", "Works"};
    public static final int BARRACKS = 0, ARMOURY = 1, TRAINING = 2, WATCHTOWER = 3, GATEHOUSE = 4, WAR_ROOM = 5, OBSERVATORY = 6, LIBRARY = 7,
            ARCANUM = 8, SCRYING = 9, ALCHEMY = 10, RUNE_VAULT = 11, CHAPEL = 12, RELIQUARY = 13, CRYPT = 14, BELL_TOWER = 15, GARDEN = 16,
            HALL = 17, FORGE = 18, FOUNDRY = 19, WORKSHOP = 20, SIEGE_YARD = 21, CISTERN = 22, TREASURY = 23,
            HERALD = 24, MAP_ROOM = 25, THRONE = 26;

    /** The neighbour bonuses: two rooms side by side on the floor plan, and what they give. */
    public record Pair(int a, int b, String gives) {}
    public static final Pair[] PAIRS = {
        new Pair(FORGE, ARMOURY, "Blade Rack's weapons burn"),
        new Pair(BARRACKS, CHAPEL, "Defenders slowly heal"),
        new Pair(WATCHTOWER, OBSERVATORY, "Towers never miss a revealed or marked foe"),
        new Pair(GATEHOUSE, BELL_TOWER, "Portcullis rings the Great Toll as it falls"),
        new Pair(LIBRARY, ARCANUM, "Every third Ley Lance costs no mana"),
        new Pair(WORKSHOP, SIEGE_YARD, "The Ballista lobs trebuchet shots too"),
        new Pair(WAR_ROOM, TRAINING, "Drill Charge strikes a marked foe twice"),
        new Pair(CRYPT, RELIQUARY, "When the Keepsake saves you, bone sentries rise round you"),
        new Pair(CISTERN, GARDEN, "Floodgate's wave heals the allies it passes"),
        new Pair(ALCHEMY, FORGE, "Volatile Flasks leave a burning patch"),
        new Pair(RUNE_VAULT, CHAPEL, "The Ward Sigil also heals"),
        new Pair(TREASURY, HALL, "Rally: defeated foes give Stone while it lasts"),
    };

    // -------------------------------------------------------------- the pages' own cooldowns and costs
    public static final int RAMPART_CD = 160, RAMPART_MANA = 30, SIEGE_CD = 3600, SIEGE_MANA = 120, CITADEL_CD = 12000, CITADEL_MANA = 250;

    // ================================================================== state
    static CompoundTag root(Player p) {
        CompoundTag pd = p.getPersistentData();
        if (!pd.contains(Player.PERSISTED_NBT_TAG)) pd.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag per = pd.getCompound(Player.PERSISTED_NBT_TAG);
        if (!per.contains(ROOT)) {
            CompoundTag r = new CompoundTag();
            r.putInt("lv", 1);
            r.putIntArray("grid", blankGrid());
            r.putIntArray("slots", new int[]{-1, -1, -1, -1});
            per.put(ROOT, r);
        }
        CompoundTag r = per.getCompound(ROOT);
        sanitize(r);
        return r;
    }

    /** A grid of 25 cells: -1 empty, else room * 10 + level (1-5). */
    static int[] blankGrid() { int[] g = new int[25]; java.util.Arrays.fill(g, -1); return g; }

    /** Keep whatever was saved in range (a hand-edited or older save can't break anything). */
    static void sanitize(CompoundTag r) {
        int[] g = r.getIntArray("grid");
        if (g.length != 25) { g = blankGrid(); r.putIntArray("grid", g); }
        boolean[] seen = new boolean[ROOMS];
        for (int i = 0; i < 25; i++) {
            int c = g[i];
            if (c < 0) { g[i] = -1; continue; }
            int room = c / 10, lv = c % 10;
            if (room >= ROOMS || lv < 1 || lv > ROOM_MAX || seen[room]) g[i] = -1;
            else seen[room] = true;
        }
        int[] s = r.getIntArray("slots");
        if (s.length != SLOTS) { s = new int[]{-1, -1, -1, -1}; r.putIntArray("slots", s); }
        for (int i = 0; i < SLOTS; i++) if (s[i] < -1 || s[i] >= ROOMS) s[i] = -1;
        int lv = r.getInt("lv");
        if (lv < 1 || lv > MAX_LV) r.putInt("lv", Math.max(1, Math.min(MAX_LV, lv)));
        if (r.getInt("stone") < 0) r.putInt("stone", 0);
        int st = r.getInt("stars");
        if (st < 0 || st > MAX_STARS) r.putInt("stars", Math.max(0, Math.min(MAX_STARS, st)));
        int sh = r.getInt("shape");
        if (sh < 0 || sh > HORSESHOE) r.putInt("shape", RING);
    }

    public static boolean equipped(Player p) {
        var b = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
        return b != null && b.is(AddonItemRegistry.ARCHITECT_GRIMOIRE.get());
    }

    static long now(Player p) { return p.level().getGameTime(); }
    public static int level(Player p) { return root(p).getInt("lv"); }
    public static int stone(Player p) { return root(p).getInt("stone"); }
    /** Book XP from one level to the next. */
    public static int need(int lv) { return (int) Math.round(120 * Math.pow(lv, 1.35)); }
    /** The floor plan's size at a book level. */
    public static int gridSize(int lv) { return lv >= 8 ? 5 : lv >= 4 ? 4 : 3; }
    public static int gridSize(Player p) { return gridSize(level(p)); }

    /** Is a grid cell (0-24, row-major 5 wide) inside the current plan? */
    static boolean inPlan(Player p, int cell) { int n = gridSize(p); return cell >= 0 && cell < 25 && cell % 5 < n && cell / 5 < n; }

    static int[] grid(Player p) { return root(p).getIntArray("grid"); }
    /** A room's level (0 = not built). */
    public static int roomLevel(Player p, int room) {
        for (int c : grid(p)) if (c >= 0 && c / 10 == room) return c % 10;
        return 0;
    }
    static int cellOf(Player p, int room) {
        int[] g = grid(p);
        for (int i = 0; i < 25; i++) if (g[i] >= 0 && g[i] / 10 == room) return i;
        return -1;
    }
    public static boolean built(Player p, int room) { return roomLevel(p, room) > 0; }
    public static int builtCount(Player p) { int n = 0; for (int c : grid(p)) if (c >= 0) n++; return n; }
    public static boolean unlocked(Player p, int room) { return unlocked(room, root(p).getInt("bp"), stars(p)); }
    /** Rooms 0-7 are open; 8-23 need their blueprint; 24-26 need the fortress's stars. */
    public static boolean unlocked(int room, int bp, int stars) {
        if (room < 0 || room >= ROOMS) return false;
        if (room >= BASE_ROOMS) return stars >= STAR_ROOM[room - BASE_ROOMS];
        return room < OPEN || (bp & (1 << room)) != 0;
    }
    public static int stars(Player p) { return root(p).getInt("stars"); }
    public static int shape(Player p) { return root(p).getInt("shape"); }

    /** Two rooms side by side (up, down, left or right) on the plan. */
    public static boolean paired(Player p, int a, int b) {
        int ca = cellOf(p, a), cb = cellOf(p, b);
        if (ca < 0 || cb < 0) return false;
        int dx = Math.abs(ca % 5 - cb % 5), dy = Math.abs(ca / 5 - cb / 5);
        return dx + dy == 1;
    }
    public static boolean pair(Player p, int i) { return paired(p, PAIRS[i].a, PAIRS[i].b); }
    static boolean pairOf(Player p, int a, int b) { return paired(p, a, b); }

    /** How strong the book's spells are: book level, the room's level and spell power. */
    static float mult(ServerPlayer p, int room) {
        int rl = room < 0 ? 1 : Math.max(1, roomLevel(p, room));
        float stars = room < 0 ? 1 + 0.05f * stars(p) : 1f;   // each star: towers, walls and defenders +5%
        return (1 + 0.04f * (level(p) - 1)) * (1 + 0.15f * (rl - 1)) * stars * (float) p.getAttributeValue(AttributeRegistry.SPELL_POWER.get());
    }

    static void tell(ServerPlayer p, String s) { p.displayClientMessage(Component.literal(s).withStyle(ChatFormatting.GRAY), true); }
    static void say(ServerPlayer p, String s) {
        p.sendSystemMessage(Component.literal("Architect: ").withStyle(ChatFormatting.GOLD).append(Component.literal(s).withStyle(ChatFormatting.WHITE)));
    }

    /** The first time something happens, a short line above the hotbar (each hint once, ever). */
    public static final int HINT_BANNER = 0, HINT_GRID4 = 1, HINT_GRID5 = 2, HINT_BLUEPRINT = 3, HINT_LEVEL = 4, HINT_SIEGE = 5, HINT_PAIR = 6, HINT_FIRST = 7,
            HINT_BREACH = 8, HINT_ORDERS = 9, HINT_PRESTIGE = 10, HINT_SHAPE = 11;
    static void hint(ServerPlayer p, int which, String text) {
        CompoundTag r = root(p);
        int h = r.getInt("hints");
        if ((h & (1 << which)) != 0) return;
        r.putInt("hints", h | (1 << which));
        p.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.GOLD), true);
    }

    // ================================================================== Stone and levels
    static void addStone(ServerPlayer p, int n) {
        if (n <= 0) return;
        CompoundTag r = root(p);
        r.putInt("stone", (int) Math.min(1_000_000L, (long) r.getInt("stone") + n));
        r.putInt("stoneAll", r.getInt("stoneAll") + n);
    }

    static void addXp(ServerPlayer p, float xp) {
        CompoundTag r = root(p);
        if (r.getInt("lv") >= MAX_LV) return;
        float x = r.getFloat("xp") + xp;
        int lv = r.getInt("lv");
        while (lv < MAX_LV && x >= need(lv)) {
            x -= need(lv);
            lv++;
            r.putInt("lv", lv);
            levelUp(p, lv);
        }
        r.putFloat("xp", lv >= MAX_LV ? 0 : x);
    }

    static void levelUp(ServerPlayer p, int lv) {
        say(p, "The fortress grows: book level " + lv + ".");
        OverdriveFx.play(p.serverLevel(), p.position(), "fort_horn", 0.8f, 1.0f);
        hint(p, HINT_LEVEL, "Each book level gives a blueprint - open The Fortress to build a new room");
        giveBlueprint(p, "a level up");
        if (lv == 4) { say(p, "Your floor plan grows to 4x4."); hint(p, HINT_GRID4, "Your floor plan grew to 4x4 - more room to build"); }
        if (lv == 8) { say(p, "Your floor plan grows to 5x5."); hint(p, HINT_GRID5, "Your floor plan grew to 5x5"); }
        if (lv == MAX_LV) say(p, "The fortress stands complete. The Citadel answers you once the grimoire is mastered.");
        if (lv == MAX_LV && canPrestige(p)) hint(p, HINT_PRESTIGE, "Your fortress can be rebuilt stronger - Prestige, on the Floor Plan");
        Adv.grant(p, "fortress_level");
    }

    /** A blueprint for a room still locked (picked at random); nothing if every room is open. */
    static int giveBlueprint(ServerPlayer p, String from) {
        CompoundTag r = root(p);
        List<Integer> locked = new ArrayList<>();
        for (int i = OPEN; i < BASE_ROOMS; i++) if (!unlocked(p, i)) locked.add(i);
        if (locked.isEmpty()) return -1;
        int room = locked.get(ThreadLocalRandom.current().nextInt(locked.size()));
        r.putInt("bp", r.getInt("bp") | (1 << room));
        say(p, "A blueprint from " + from + ": the " + ROOM[room].name + " (" + ROOM[room].spell + ").");
        hint(p, HINT_BLUEPRINT, "New blueprint! Build it on the Floor Plan in The Fortress");
        if (locked.size() == 1) Adv.grant(p, "every_blueprint");
        return room;
    }

    // ================================================================== building
    /** Build a room on an empty cell (or upgrade it where it stands); costs Stone. */
    static void build(ServerPlayer p, int room, int cell) {
        if (room < 0 || room >= ROOMS) return;
        CompoundTag r = root(p);
        if (!unlocked(p, room)) { tell(p, "The " + ROOM[room].name + " needs its blueprint first"); return; }
        int lv = roomLevel(p, room);
        if (lv >= ROOM_MAX) { tell(p, "The " + ROOM[room].name + " is at its highest level"); return; }
        int cost = COST[lv];
        if (r.getInt("stone") < cost && !p.isCreative()) { tell(p, "Not enough Stone (" + cost + " needed)"); return; }
        int[] g = grid(p);
        if (lv == 0) {
            if (!inPlan(p, cell) || g[cell] >= 0) { tell(p, "Choose an empty space on the floor plan"); return; }
            g[cell] = room * 10 + 1;
            if (builtCount(p) == 0) hint(p, HINT_FIRST, "Room built! Put its spell on a Room page in the Spells tab");
        } else g[cellOf(p, room)] = room * 10 + lv + 1;
        r.putIntArray("grid", g);
        if (!p.isCreative()) r.putInt("stone", r.getInt("stone") - cost);
        OverdriveFx.play(p.serverLevel(), p.position(), "fort_build", 0.7f, 0.9f + lv * 0.08f);
        if (lv == 0) {   // an empty Room page takes the new room's spell
            int[] s = r.getIntArray("slots");
            for (int i = 0; i < SLOTS; i++) if (s[i] == -1) { s[i] = room; r.putIntArray("slots", s); break; }
            for (int i = 0; i < PAIRS.length; i++) if (pair(p, i) && (PAIRS[i].a == room || PAIRS[i].b == room)) {
                say(p, "Neighbours: the " + ROOM[PAIRS[i].a].name + " and the " + ROOM[PAIRS[i].b].name + " - " + PAIRS[i].gives + ".");
                hint(p, HINT_PAIR, "Neighbour bonus! Rooms side by side work together");
            }
        }
        int all = 0; for (int c : grid(p)) if (c >= 0 && c / 10 < BASE_ROOMS) all++;
        if (all >= BASE_ROOMS) Adv.grant(p, "every_room");
        refreshPassives(p);
    }

    /** Move a room to another cell (swapping with whatever is there). Free. */
    static void move(ServerPlayer p, int from, int to) {
        if (!inPlan(p, from) || !inPlan(p, to) || from == to) return;
        int[] g = grid(p);
        if (g[from] < 0) return;
        int t = g[to]; g[to] = g[from]; g[from] = t;
        root(p).putIntArray("grid", g);
        for (int i = 0; i < PAIRS.length; i++) {
            int a = PAIRS[i].a, b = PAIRS[i].b, moved = g[to] / 10;
            if ((a == moved || b == moved) && pair(p, i)) say(p, "Neighbours: " + PAIRS[i].gives + ".");
        }
    }

    /** Put a built room's spell on a Room page (or clear it: room -1). */
    static void slot(ServerPlayer p, int slot, int room) {
        if (slot < 0 || slot >= SLOTS) return;
        if (room >= 0 && !built(p, room)) { tell(p, "Build the " + ROOM[room].name + " first"); return; }
        int[] s = root(p).getIntArray("slots");
        for (int i = 0; i < SLOTS; i++) if (room >= 0 && s[i] == room) s[i] = -1;   // one page per spell
        s[slot] = room;
        root(p).putIntArray("slots", s);
    }

    // ================================================================== room passives (plain attribute modifiers)
    private static final UUID P_ARMOURY = UUID.fromString("a7c1f001-0b3e-4c2a-9a01-5f0e1d2c0001"), P_OBS = UUID.fromString("a7c1f001-0b3e-4c2a-9a01-5f0e1d2c0002"),
            P_LIB = UUID.fromString("a7c1f001-0b3e-4c2a-9a01-5f0e1d2c0003"), P_ARC = UUID.fromString("a7c1f001-0b3e-4c2a-9a01-5f0e1d2c0004"),
            P_CHAPEL = UUID.fromString("a7c1f001-0b3e-4c2a-9a01-5f0e1d2c0005"), P_GARDEN = UUID.fromString("a7c1f001-0b3e-4c2a-9a01-5f0e1d2c0006"),
            P_FOUNDRY = UUID.fromString("a7c1f001-0b3e-4c2a-9a01-5f0e1d2c0007");

    private static void mod(Player p, Attribute a, UUID id, String name, boolean on, double amount, AttributeModifier.Operation op) {
        AttributeInstance ai = p.getAttribute(a);
        if (ai == null) return;
        AttributeModifier m = ai.getModifier(id);
        if (!on) { if (m != null) ai.removeModifier(id); return; }
        if (m != null && m.getAmount() == amount) return;
        if (m != null) ai.removeModifier(id);
        ai.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    /** The passives of every built room, while the book is worn; none otherwise. */
    public static void refreshPassives(Player p) {
        boolean on = equipped(p);
        mod(p, Attributes.ATTACK_DAMAGE, P_ARMOURY, "Armoury", on && built(p, ARMOURY), 0.05, AttributeModifier.Operation.MULTIPLY_TOTAL);
        mod(p, AttributeRegistry.MANA_REGEN.get(), P_OBS, "Observatory", on && built(p, OBSERVATORY), 0.10, AttributeModifier.Operation.MULTIPLY_BASE);
        mod(p, AttributeRegistry.MANA_REGEN.get(), P_GARDEN, "Garden", on && built(p, GARDEN), 0.05, AttributeModifier.Operation.MULTIPLY_BASE);
        mod(p, AttributeRegistry.MAX_MANA.get(), P_LIB, "Library", on && built(p, LIBRARY), 100, AttributeModifier.Operation.ADDITION);
        mod(p, AttributeRegistry.SPELL_POWER.get(), P_ARC, "Arcanum", on && built(p, ARCANUM), 0.05, AttributeModifier.Operation.MULTIPLY_BASE);
        mod(p, Attributes.MAX_HEALTH, P_CHAPEL, "Chapel", on && built(p, CHAPEL), 2, AttributeModifier.Operation.ADDITION);
        mod(p, Attributes.ARMOR, P_FOUNDRY, "Foundry", on && built(p, FOUNDRY), 0.05, AttributeModifier.Operation.MULTIPLY_TOTAL);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    // ================================================================== the pages
    public static void rampartPage(ServerPlayer p, float power) { if (pay(p, -1, "rampart", RAMPART_CD, RAMPART_MANA)) FortressSiege.rampart(p); }
    public static void siegePage(ServerPlayer p, float power) {
        if (p.isShiftKeyDown() && equipped(p)) { setShape(p, (shape(p) + 1) % SHAPE.length); return; }   // sneak: the next shape, free
        int cd = Math.round(SIEGE_CD * (built(p, WAR_ROOM) ? 0.9f : 1f));
        if (FortressSiege.active(p)) { tell(p, "Siege Mode is already up"); return; }
        if (pay(p, -2, "siege", cd, SIEGE_MANA)) FortressSiege.siege(p, false);
    }
    public static void citadelPage(ServerPlayer p, float power) {
        if (!canCitadel(p)) { tell(p, "The Citadel needs book level 10 and the grimoire mastered"); return; }
        if (FortressSiege.active(p)) { tell(p, "A siege is already up"); return; }
        if (pay(p, -3, "citadel", CITADEL_CD, CITADEL_MANA)) FortressSiege.siege(p, true);
    }
    public static void fortressPage(ServerPlayer p, float power) { sync(p, true); }
    public static void room1(ServerPlayer p, float power) { roomPage(p, 0); }
    public static void room2(ServerPlayer p, float power) { roomPage(p, 1); }
    public static void room3(ServerPlayer p, float power) { roomPage(p, 2); }
    public static void room4(ServerPlayer p, float power) { roomPage(p, 3); }

    /** Siege Mode's shape (0 ring, 1 line, 2 horseshoe). */
    static void setShape(ServerPlayer p, int s) {
        if (s < 0 || s >= SHAPE.length) return;
        root(p).putInt("shape", s);
        tell(p, "Siege Mode: " + SHAPE[s] + " - " + SHAPE_SAYS[s]);
        OverdriveFx.play(p.serverLevel(), p.position(), "fort_page", 0.5f, 0.8f + 0.15f * s);
        hint(p, HINT_SHAPE, "Sneak and cast Siege Mode to switch its shape (or pick it on the Spells tab)");
        sync(p, false);
    }
    public static final String[] SHAPE_SAYS = {"walls all round you, a gate where you face", "one long wall across your front, towers behind it", "walls in front and at your sides, open behind"};

    /** Prestige: a level-10 fortress with the grimoire mastered can be rebuilt for a star (5 at most). */
    public static boolean canPrestige(Player p) { return canCitadel(p) && stars(p) < MAX_STARS; }

    /** Rebuild: book level 1, an empty plan and Room pages; garrison, banners, blueprints, Stone and stars stay. */
    static void prestige(ServerPlayer p) {
        if (!canPrestige(p)) { tell(p, stars(p) >= MAX_STARS ? "Your fortress already has every star" : "Prestige needs book level 10 and the grimoire mastered"); return; }
        FortressSiege.endAll(p, false);
        CompoundTag r = root(p);
        int st = r.getInt("stars") + 1;
        r.putInt("stars", st);
        r.putInt("lv", 1);
        r.putFloat("xp", 0);
        r.putIntArray("grid", blankGrid());
        r.putIntArray("slots", new int[]{-1, -1, -1, -1});
        ListTag g = FortressSiege.garrison(p);
        for (int i = 0; i < g.size(); i++) g.getCompound(i).putInt("post", -1);   // their rooms are gone
        refreshPassives(p);
        say(p, "The fortress falls and rises again - star " + st + " of " + MAX_STARS + ". Towers, walls and defenders are " + (5 * st) + "% stronger.");
        for (int i = 0; i < STAR_ROOM.length; i++) if (STAR_ROOM[i] == st) say(p, "A new room opens: the " + ROOM[BASE_ROOMS + i].name + " (" + ROOM[BASE_ROOMS + i].spell + ").");
        prestigeFx(p);
        Adv.grant(p, "prestige");
    }
    private static void prestigeFx(ServerPlayer p) {
        OverdriveFx.play(p.serverLevel(), p.position(), "fort_prestige", 1.0f, 1.0f);
        FxSend.ring(p.serverLevel(), FortressSiege.BRASS, p.position().add(0, 0.1, 0), 0.5f, 6f, 0.2f, 20);
        FxSend.shake(p.serverLevel(), p.position(), 0.3f, 10, 8);
    }

    public static boolean canCitadel(Player p) {
        if (level(p) < MAX_LV) return false;
        var b = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
        return b != null && GrimoireMastery.level(b) >= GrimoireMastery.MAX_LEVEL;
    }

    static void roomPage(ServerPlayer p, int slot) {
        int room = root(p).getIntArray("slots")[slot];
        if (room < 0) { tell(p, "Room page " + (slot + 1) + " is empty - choose a room's spell in The Fortress (Spells tab)"); return; }
        castRoom(p, room);
    }

    /** Cast a room's spell: built, off its cooldown, paid for. */
    public static boolean castRoom(ServerPlayer p, int room) {
        if (room < 0 || room >= ROOMS || !equipped(p)) return false;
        if (!built(p, room)) { tell(p, "Build the " + ROOM[room].name + " first"); return false; }
        int mana = ROOM[room].mana;
        if (room == ARCANUM && pair(p, indexOfPair(LIBRARY, ARCANUM))) {   // Library + Arcanum: every third Ley Lance is free
            CompoundTag r = root(p);
            int n = r.getInt("lances") + 1;
            r.putInt("lances", n);
            if (n % 3 == 0) mana = 0;
        }
        if (!pay(p, room, ROOM[room].spell, ROOM[room].cd, mana)) return false;
        FortressSpells.cast(p, room);
        return true;
    }

    static int indexOfPair(int a, int b) {
        for (int i = 0; i < PAIRS.length; i++) if (PAIRS[i].a == a && PAIRS[i].b == b || PAIRS[i].a == b && PAIRS[i].b == a) return i;
        return 0;
    }

    /** Cooldowns: index room (0-23), 24 Rampart, 25 Siege, 26 Citadel. */
    static int cdIndex(int key) { return key >= 0 ? key : ROOMS - 1 - key; }
    static long[] cds(Player p) {
        long[] c = root(p).getLongArray("cds");
        return c.length == ROOMS + 3 ? c : new long[ROOMS + 3];
    }
    public static int cooldownLeft(Player p, int key) { return (int) Math.max(0, cds(p)[cdIndex(key)] - now(p)); }

    /** Off cooldown and enough mana: take both, and the Library's refund if it's running. */
    static boolean pay(ServerPlayer p, int key, String name, int cd, int mana) {
        if (!equipped(p)) return false;
        long t = now(p);
        long[] c = cds(p);
        int i = cdIndex(key);
        if (c[i] > t && !p.isCreative()) { tell(p, name + " ready in " + (int) Math.ceil((c[i] - t) / 20.0) + " s"); return false; }
        MagicData md = MagicData.getPlayerMagicData(p);
        if (!p.isCreative() && md.getMana() < mana) { tell(p, "Not enough mana"); return false; }
        if (!p.isCreative()) md.setMana(md.getMana() - mana);
        CompoundTag r = root(p);
        int notes = r.getInt("notes");
        if (notes > 0 && mana > 0) {   // Annotated Page
            r.putInt("notes", notes - 1);
            md.setMana(md.getMana() + mana * 0.3f);
        }
        c[i] = t + cd;
        r.putLongArray("cds", c);
        r.putLong("combat", t);
        sync(p, false);
        return true;
    }

    // ================================================================== sync and the menu's actions
    /** The whole state to its player (NBT: small), plus what the client can't work out for itself. */
    public static void sync(ServerPlayer p, boolean open) {
        CompoundTag out = root(p).copy();
        out.putBoolean("eq", equipped(p));
        out.putLong("now", now(p));
        out.putBoolean("citadelOk", canCitadel(p));
        out.putBoolean("siegeUp", FortressSiege.active(p));
        out.putInt("cap", FortressSiege.cap(p));
        out.putBoolean("prestigeOk", canPrestige(p));
        FortressSiege.syncInto(p, out);
        AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new FortressPackets.Sync(out, open));
    }

    /** From the menu: 1 build/upgrade (room * 100 + cell), 2 move (from * 100 + to), 3 set a Room page (slot * 100 + room + 1; 0 = clear),
     *  4 claim a banner (its index), 5 dismiss a defender (its id), 6 post a defender (id * 100 + room + 1; 0 = none),
     *  7 the walkthrough finished, 8 open the menu, 9 release a banner (its index), 10 Siege Mode's shape, 11 prestige,
     *  12 a garrison order (0 none, 1 hold, 2 charge, 3 fall back), 13 overcharge the tower you look at. */
    public static void handle(ServerPlayer p, int action, int arg) {
        if (arg < 0) return;
        switch (action) {
            case 1 -> build(p, arg / 100, arg % 100);
            case 2 -> move(p, arg / 100, arg % 100);
            case 3 -> slot(p, arg / 100, arg % 100 - 1);
            case 4 -> FortressSiege.claim(p, arg);
            case 5 -> FortressSiege.dismiss(p, arg);
            case 6 -> FortressSiege.post(p, arg / 100, arg % 100 - 1);
            case 7 -> root(p).putBoolean("toured", true);
            case 8 -> { sync(p, true); return; }
            case 9 -> FortressSiege.release(p, arg);
            case 10 -> { setShape(p, arg); return; }
            case 11 -> prestige(p);
            case 12 -> { FortressSiege.order(p, arg); return; }
            case 13 -> { FortressSiege.overcharge(p); return; }
            default -> { return; }
        }
        sync(p, false);
    }

    // ================================================================== events
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || !equipped(p) || e.getEntity() == p) {
            // a defender's kill counts for its master
            if (e.getSource().getEntity() instanceof com.ironspellgrimoire.entity.FortressDefender d && d.owner() instanceof ServerPlayer o) {
                FortressSiege.defenderKill(o, d);
                reward(o, e.getEntity(), 0.5f);
            }
            return;
        }
        reward(p, e.getEntity(), 1f);
    }

    /** Stone, XP and maybe a banner for a defeated foe. */
    static void reward(ServerPlayer p, LivingEntity t, float k) {
        if (t instanceof Player || t instanceof com.ironspellgrimoire.entity.FortressDefender) return;
        if (!(t instanceof Enemy) && !(t instanceof AddonBoss)) k *= 0.25f;   // animals and the like give little
        boolean boss = t instanceof AddonBoss || t.getType().is(Tags.EntityTypes.BOSSES);
        boolean elite = !boss && t.getMaxHealth() >= 40;
        float stone = (Math.max(1f, t.getMaxHealth() / 8f) + (elite ? 10 : 0) + (boss ? 50 : 0)) * k;
        if (built(p, TREASURY)) stone *= 1.25f;
        CompoundTag r = root(p);
        if (r.getLong("tribute") > now(p)) stone *= 3;
        if (r.getLong("rallyStone") > now(p)) stone += 2;   // Treasury + Hall of Banners
        addStone(p, Math.max(1, Math.round(stone)));
        addXp(p, Math.max(2f, t.getMaxHealth() / 2f) * (boss ? 4 : 1) * k);
        if (k >= 1f && t instanceof Enemy) FortressSiege.maybeBanner(p, t, boss);
        if (boss) giveBlueprint(p, "a fallen boss");
        sync(p, false);
    }

    /** A rift cleared (Rifts) or the world boss beaten (WorldBoss): Stone, and a blueprint (rifts: half the time). */
    public static void riftCleared(ServerPlayer p) {
        if (!equipped(p)) return;
        addStone(p, 30);
        if (ThreadLocalRandom.current().nextBoolean()) giveBlueprint(p, "the rift");
        sync(p, false);
    }
    public static void worldBossDown(ServerPlayer p) {
        if (!equipped(p)) return;
        addStone(p, 80);
        giveBlueprint(p, "the Cinder Sovereign");
        sync(p, false);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 40 == 7) {
            boolean eq = equipped(p);
            CompoundTag r = root(p);
            if (eq != r.getBoolean("wasEq")) {
                r.putBoolean("wasEq", eq);
                refreshPassives(p);
                if (!eq) FortressSiege.endAll(p, false);
                sync(p, false);
            }
        }
        if (p.tickCount % 20 == 3) { FortressSpells.tick(p); FortressSiege.tick(p); }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { refreshPassives(p); sync(p, false); }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) FortressSiege.endAll(p, true);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) FortressSiege.endAll(p, false);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        if (e.getEntity() instanceof ServerPlayer p) FortressSiege.endAll(p, false);
    }

    // ================================================================== test hooks
    public static void testReset(ServerPlayer p) {
        FortressSiege.endAll(p, false);
        CompoundTag pd = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        pd.remove(ROOT);
        refreshPassives(p);
    }
    public static void testStone(ServerPlayer p, int n) { addStone(p, n); }
    public static void testStars(ServerPlayer p, int n) { root(p).putInt("stars", Math.max(0, Math.min(MAX_STARS, n))); }
    public static void testLevel(ServerPlayer p, int lv) { root(p).putInt("lv", Math.max(1, Math.min(MAX_LV, lv))); }
    public static void testUnlockAll(ServerPlayer p) { root(p).putInt("bp", -1); }
    public static void testClearCds(ServerPlayer p) { root(p).putLongArray("cds", new long[ROOMS + 3]); }
    public static int testCell(ServerPlayer p, int room) { return cellOf(p, room); }
    public static int[] testSlots(ServerPlayer p) { return root(p).getIntArray("slots").clone(); }
    public static int testBlueprints(ServerPlayer p) { return root(p).getInt("bp"); }
    public static CompoundTag testRoot(ServerPlayer p) { return root(p); }
}
