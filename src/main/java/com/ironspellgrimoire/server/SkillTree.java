package com.ironspellgrimoire.server;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * The Mana Tree: every grimoire grows its own skill tree, kept on the book ("Tree": the nodes learned, "TreeResets").
 * <ul>
 *   <li>Root (mana) and Body (survival): the same six nodes on every grimoire.</li>
 *   <li>Element: seven nodes of that grimoire's own magic - its own names, mix and strengths.</li>
 *   <li>Crown: one capstone unique to each grimoire (server/SkillEffects), once five element nodes are learned.</li>
 * </ul>
 * Points are Grimoire Leaves, grown by the book: 2 per mastery level, 1 per evolved spell (up to 6), 2 for the Magic
 * Knight Exam, 2 for Awakening, 2 for Ascension, and 1 per chapter of Lost Memory recovered - 31 at most, against a
 * tree that costs 37, so every mage's tree is their own. Learned in the grimoire screen's Tree ribbon; reset at a
 * Grimoire Altar (the first time free, then 10 merit stars). Shared by both sides.
 */
public final class SkillTree {
    private SkillTree() {}

    public static final int ROOT = 0, ELEMENT = 1, BODY = 2, CROWN = 3;
    public static final int CROWN_NEEDS = 5, RESPEC_COST = 10;

    /** One node: where it sits (branch, tier 1-4, lane 0-2), what it needs, what it costs, and what it does (effect, value). */
    public record Node(String id, int branch, int tier, int lane, String parent, int cost, String name, String effect, float val) {}

    private static final int[] TIER_COST = {0, 1, 1, 2, 3};

    private static List<Node> shared() {
        List<Node> l = new ArrayList<>();
        l.add(new Node("r1", ROOT, 1, 1, null, 1, "Deep Well", "mana", 40));
        l.add(new Node("r2", ROOT, 2, 0, "r1", 1, "Steady Flow", "regen", 0.10f));
        l.add(new Node("r3", ROOT, 2, 2, "r1", 1, "Thrift", "cost", 0.08f));
        l.add(new Node("r4", ROOT, 3, 0, "r2", 2, "Overflow", "mana", 60));
        l.add(new Node("r5", ROOT, 3, 2, "r3", 2, "Focus", "cdr", 0.06f));
        l.add(new Node("r6", ROOT, 4, 1, "r4", 3, "Font of Mana", "killmana", 0.06f));
        l.add(new Node("b1", BODY, 1, 1, null, 1, "Tough Skin", "health", 4));
        l.add(new Node("b2", BODY, 2, 0, "b1", 1, "Iron Will", "armor", 3));
        l.add(new Node("b3", BODY, 2, 2, "b1", 1, "Fleet Foot", "speed", 0.06f));
        l.add(new Node("b4", BODY, 3, 0, "b2", 2, "Second Wind", "regenhp", 1));
        l.add(new Node("b5", BODY, 3, 2, "b3", 2, "Evasion", "dodge", 0.06f));
        l.add(new Node("b6", BODY, 4, 1, "b4", 3, "Unbreakable", "resist", 0.08f));
        return l;
    }

    /** Each grimoire's element branch (GrimoireMastery.BOOKS order): seven {name, effect, value}, then its crown {name, text}. */
    private static final Object[][] ELEMENTS = {
            /* 0 Black */ {"Anti-Magic Edge", "boss", .10f, "Zetten Footwork", "hastecast", .15f, "Never Give Up", "lowhp", .15f, "Demon-Dweller", "lifesteal", .06f, "Ki Sense", "dodge", .08f, "Black Divider", "execute", .25f, "Surpass Your Limits", "crit", .12f},
            /* 1 Dark */ {"Darkness Cloak", "weaken", .15f, "Ki Foresight", "dodge", .07f, "Avidya Edge", "crit", .10f, "Black Moon", "slow", .25f, "Dimension Slash", "chain", .15f, "Death Thrust", "execute", .25f, "Past Your Limits", "power", .08f},
            /* 2 Wind */ {"Tailwind", "hastecast", .20f, "Gale Edge", "power", .05f, "Updraft", "knock", .6f, "Sylph's Favour", "cdr", .08f, "Blade Shower", "chain", .15f, "Spirit of Zephyr", "echo", .10f, "Hushed Dance", "dodge", .08f},
            /* 3 Mercury */ {"Silver Rain", "chain", .12f, "Silver Mirror", "shield", 4, "Quicksilver", "crit", .08f, "Mercury's Weight", "slow", .20f, "Regal Bearing", "fullhp", .12f, "Storm of Blades", "echo", .10f, "Noble Pride", "boss", .12f},
            /* 4 Fire */ {"Kindling", "burn", .15f, "Hot-Blooded", "lowhp", .12f, "Scattershot", "chain", .12f, "Flame Knuckles", "crit", .10f, "Soul Chain", "manasteal", .06f, "Wildfire", "burn", .15f, "Ride or Die", "killheal", 3},
            /* 5 Lightning */ {"Lightning Step", "hastecast", .20f, "Chain Lightning", "chain", .18f, "Battle Maniac", "first", .20f, "Thunder Gloves", "echo", .08f, "Static Shock", "slow", .15f, "Thunderfall", "crit", .12f, "Mana Sense", "dodge", .06f},
            /* 6 Hellfire */ {"Calidos Heat", "burn", .20f, "Mana Skin", "armor", 4, "Lioness Charge", "knock", .5f, "Burning Guard", "thorns", .15f, "Ground Punch", "crit", .12f, "Untamed Power", "power", .08f, "Hellfire Veins", "lifesteal", .05f},
            /* 7 Bone */ {"Bone Spikes", "execute", .15f, "Marrow Regeneration", "regenhp", 1, "Bone Whips", "pull", .5f, "Ribcage", "weaken", .15f, "Bone Thicket", "thorns", .15f, "Spear Storm", "chain", .15f, "Beelzebub's Hunger", "killheal", 4},
            /* 8 Steel */ {"Steel Lance", "crit", .08f, "Castle Wall", "armor", 4, "Rotating Lance", "knock", .5f, "Steel Prison", "slow", .25f, "Horse-Cart Charge", "hastecast", .15f, "Knight's Armour", "shield", 6, "War Princess", "boss", .12f},
            /* 9 Thread */ {"Binding Threads", "slow", .20f, "Puppet Strings", "pull", .5f, "Stitching", "regenhp", 1, "Thread Net", "chain", .12f, "Battle Dress", "dodge", .08f, "Thread Cage", "weaken", .20f, "Fate's Weave", "refund", .06f},
            /* 10 Space */ {"Short Hop", "hastecast", .20f, "Spatial Gate", "cost", .08f, "Redirect", "refund", .06f, "Evasion Gate", "dodge", .10f, "Escort Gate", "shield", 4, "Sky Drop", "first", .25f, "Space Deletion", "execute", .20f},
            /* 11 Water */ {"Water Ball", "knock", .4f, "Torrent Lance", "crit", .08f, "Sea Dragon's Nest", "shield", 5, "Healing Tide", "regenhp", 1, "Valkyrie's Poise", "fullhp", .12f, "Tidal Wave", "chain", .15f, "Royal Blood", "power", .08f},
            /* 12 Mirror */ {"Mirror Shard", "echo", .08f, "Mirror Step", "dodge", .08f, "Reflect Guard", "thorns", .20f, "Prism Beam", "crit", .10f, "Mirror Cage", "slow", .20f, "Mirror Barrage", "chain", .15f, "Self-Reflection", "power", .06f},
            /* 13 Rift */ {"Spatial Cut", "crit", .08f, "Blink", "hastecast", .15f, "Severing Line", "execute", .15f, "Gate Barrage", "chain", .15f, "Collapse", "pull", .5f, "Convergence", "echo", .10f, "Void Hunger", "manasteal", .06f},
            /* 14 Flame Spirit */ {"Salamander Flare", "burn", .15f, "Flame Aegis", "shield", 5, "Lion's Pride", "fullhp", .12f, "Spirit Lance", "crit", .10f, "King's Will", "boss", .10f, "Salamander Coil", "chain", .12f, "Royal Flame", "power", .08f},
            /* 15 Lion Flame */ {"Ember Fist", "burn", .12f, "Prowl", "hastecast", .15f, "Roaring Burst", "knock", .5f, "Pride", "stacking", .03f, "Lion Claw", "crit", .10f, "Mane Blaze", "lifesteal", .05f, "King's Roar", "weaken", .20f},
            /* 16 Slash */ {"Phantom Slash", "crit", .10f, "Shadow Step", "dodge", .07f, "Bleeding Edge", "burn", .15f, "Thousand Cuts", "echo", .10f, "Bloodlust", "killheal", 3, "Cross Reaper", "execute", .30f, "Frenzy", "hastecast", .20f},
            /* 17 Earth */ {"Stone Fist", "knock", .5f, "Bedrock Skin", "armor", 5, "Rock Spike", "crit", .08f, "Earth Wall", "shield", 6, "Quake", "slow", .25f, "Golem's Strength", "health", 6, "Mountain Fall", "boss", .12f},
            /* 18 Time */ {"Clock Hand", "refund", .06f, "Chrono Acceleration", "hastecast", .20f, "Ticking Snare", "slow", .25f, "Time Theft", "manasteal", .06f, "Hourglass", "cdr", .08f, "Echo of the Past", "echo", .12f, "Zenith Hour", "first", .25f},
            /* 19 Light */ {"Light Speed", "hastecast", .25f, "Judgment", "crit", .10f, "Healing Ray", "regenhp", 1, "Blinding Flash", "weaken", .20f, "Arrows of Judgment", "chain", .15f, "Light Shaft", "first", .25f, "Imperial Conviction", "boss", .12f},
            /* 20 Blood (BloodFx/IceFx read its own three) */ {"Thirst", "gaugefill", .15f, "Hard Freeze", "encase", 20, "Pale Ward", "moonguard", .10f, "Blood Price", "lowhp", .15f, "Sanguine Drain", "lifesteal", .05f, "Frostbite", "slow", .20f, "Red Moon Rising", "power", .08f},
            /* 21 Gravity */ {"Dense Shot", "crit", .08f, "Heavy Hand", "slow", .25f, "Event Pull", "pull", .6f, "Featherfall", "dodge", .08f, "Crushing Weight", "boss", .12f, "Orbit", "echo", .10f, "Singularity", "chain", .15f},
            /* 22 Sound */ {"Sharp Note", "crit", .10f, "Quick Tempo", "hastecast", .20f, "Echo", "echo", .12f, "Rebound", "thorns", .15f, "Deafening", "weaken", .20f, "Wide Chorus", "chain", .15f, "Tuned Ear", "dodge", .08f},
            /* 23 Ink */ {"Bold Stroke", "crit", .10f, "Quick Brush", "hastecast", .20f, "Bleeding Ink", "burn", .12f, "Soaked", "slow", .25f, "Second Copy", "echo", .12f, "Spatter", "chain", .15f, "Finished Work", "execute", .20f},
            /* 24 Error */ {"Bad Sector", "crit", .10f, "Overclock", "cdr", .08f, "Memory Leak", "manasteal", .06f, "Stack Overflow", "echo", .12f, "Null Pointer", "weaken", .20f, "Fork Bomb", "chain", .15f, "Exploit", "first", .25f},
            /* 25 Treasure */ {"Fine Edge", "crit", .10f, "Golden Hoard", "refund", .06f, "Royal Guard", "shield", 5, "Collector", "killheal", 3, "Heavy Spear", "knock", .5f, "Endless Armory", "chain", .15f, "King's Due", "boss", .12f},
            /* 26 Chain */ {"Tight Links", "slow", .20f, "Dowsing Sense", "dodge", .07f, "Mending Chain", "regenhp", 1, "Stealing Hook", "lifesteal", .05f, "Judgment Blade", "execute", .20f, "Chain Reaction", "chain", .15f, "Anchor Weight", "knock", .5f},
            /* 27 Hunter */ {"Serrated Edge", "crit", .10f, "Quickstep", "dodge", .08f, "Rally", "lifesteal", .06f, "Silver Bullet", "boss", .12f, "Visceral", "execute", .25f, "Nightmare Pace", "hastecast", .20f, "Hunter's Resolve", "lowhp", .15f},
            /* 28 Void */ {"Erosion", "weaken", .20f, "Unmade", "crit", .10f, "Null Field", "slow", .20f, "Hollow Ward", "shield", 5, "Seamless", "cdr", .08f, "Gone", "execute", .25f, "Empty Hand", "manasteal", .06f},
            /* 29 Gambler */ {"Loaded", "crit", .12f, "House Money", "refund", .07f, "Ricochet", "chain", .15f, "Hedge Bet", "shield", 5, "Winning Streak", "echo", .10f, "Cash Out", "killheal", 3, "High Roller", "boss", .12f},
            /* 30 Tarot */ {"Upright", "power", .06f, "Foresight", "dodge", .08f, "The Lovers", "lifesteal", .05f, "Temperance", "regenhp", 1, "The Chariot", "hastecast", .20f, "The Devil", "weaken", .20f, "The Sun", "first", .25f},
            /* 31 Abyss */ {"Pressure", "slow", .25f, "Cold Blood", "armor", 4, "Lure Light", "pull", .5f, "Many Arms", "chain", .15f, "Undertow", "knock", .5f, "Hunger of the Deep", "lifesteal", .05f, "Leviathan Bite", "boss", .12f},
            /* 32 Mech */ {"Reinforced Frame", "armor", 5, "Rivet Gun", "crit", .10f, "Thrusters", "hastecast", .20f, "Plating", "shield", 6, "Heavy Hitter", "knock", .6f, "Missile Pod", "chain", .15f, "Cooling System", "cdr", .08f},
            /* 33 Weather */ {"Tailwind", "dodge", .07f, "Static", "chain", .15f, "Hail", "slow", .20f, "Downpour", "regenhp", 1, "Heatwave", "burn", .12f, "Gale Force", "knock", .5f, "Calm Eye", "fullhp", .12f},
            /* 34 Overdrive */ {"Racing Pulse", "hastecast", .20f, "Adrenaline", "dodge", .07f, "Clean Cut", "crit", .10f, "Bloodrush", "lifesteal", .05f, "Last Breath", "lowhp", .15f, "Afterimage", "echo", .10f, "Flatliner", "execute", .20f},
            /* 35 Dream */ {"Lullaby", "slow", .20f, "Sweet Dreams", "regenhp", 1, "Sleepwalker", "dodge", .07f, "Pillow Soft", "shield", 6, "Daydream", "cdr", .08f, "Lucid", "hastecast", .20f, "Nightmare", "execute", .20f}};

    /** The crowns (server/SkillEffects does what they say). */
    public static final String[][] CROWNS = {
            {"Demon Union", "Below 40% health your hits land 35% harder and heal you for 10% of the damage."},
            {"Beyond the Limit", "Every 8th spell hit cuts through dimensions: it deals double damage."},
            {"Sky Walker", "No fall damage. Casting in the air lifts you, and airborne hits land 20% harder."},
            {"Silver Guardian", "Every 15 s, silver gathers round you: 6 absorption."},
            {"Inferno Heart", "Below 30% health your hits explode: half their damage to every foe within 3 blocks."},
            {"Battle Joy", "Each kill: +15% damage and +10% speed for 6 s, stacking three times."},
            {"Untamed Lioness", "Every hit sets foes burning; hits on burning foes land 25% harder and heal you 1."},
            {"Devil's Marrow", "Kills heal 3 and grow bone armour: +4 armour for 10 s, stacking three times."},
            {"Steel Resolve", "Above 70% health: your hits land 25% harder and you take 20% less."},
            {"Red Thread of Fate", "Once every 90 s, a killing blow is rewritten: you stand at 30% health and your attacker is bound."},
            {"Escape Gate", "Hit below 30% health: a gate takes you 8 blocks clear (once every 45 s)."},
            {"Saint Valkyrie", "Every 20 s your next spell hit lands 80% harder and heals you 4."},
            {"Twin World", "15% chance any spell hit is mirrored: it strikes again for 60%."},
            {"Unmaking", "Hits on the same foe within 3 s tear space: +6% each, up to +48%."},
            {"Spirit Flare", "Every 6th hit, the salamander erupts: +60% to every foe within 3 blocks."},
            {"Rising Flame", "Each spell you cast: +2% damage, up to +30% - it fades after 8 s without casting."},
            {"Ripper's Hunger", "Each hit in a row on the same foe: +4% damage, up to +48%."},
            {"Mountain's Root", "You can't be knocked back, and standing still you take 25% less damage."},
            {"Rewind", "Once a minute, a killing blow is undone: you return to where you stood, with the health you had, 3 s ago."},
            {"Speed of Light", "Your first spell after 3 s without casting lands 60% harder, and you gain +40% speed for 2 s."},
            {"Eternal Night", "Every Scarlet Moon lasts 15 s longer, and Moonfall returns twice as fast."},
            {"Event Horizon", "Hits on foes off the ground land 30% harder, and pull everything within 3 blocks of them in."},
            {"Perfect Pitch", "Every 4th spell hit on the same foe strikes the perfect note: it lands 80% harder."},
            {"Living Signature", "Every 6th spell hit signs your name on the foe: it lands 70% harder."},
            {"Kernel Panic", "Hits on foes below half health land 25% harder, and one hit in ten lands three times over."},
            {"Gate of Plenty", "Every 5th spell hit is followed by a sword from the vault: it lands 60% harder."},
            {"Bound Fate", "Hits on foes that are slowed or held land 30% harder."},
            {"Hunter's Dream", "Every third hit in quick succession on the same foe is a visceral blow: it lands 60% harder."},
            {"Zero Point", "Hits on foes with no armour left land 40% harder."},
            {"House Edge", "Every hit is a bet: one in four lands twice as hard, one in twenty three times."},
            {"Fated Meeting", "Your first hit on each new foe lands 40% harder."},
            {"Crushing Depths", "The deeper you stand below sea level, the harder you hit: +1% a block, up to +40%."},
            {"Overdrive", "Every 10th spell hit discharges the core: it lands twice as hard."},
            {"Lightning Rod", "Under real rain your hits land 30% harder; under any open sky, 10%."},
            {"Engine Heart", "Above 150 bpm your hits land 15% harder; at the Redline, 30%."},
            {"Sweet Dreams", "Your hits on a drowsy foe land up to 30% harder; on a sleeping one, 40%."}};

    private static final List<List<Node>> TREES = new ArrayList<>();
    static {
        for (int b = 0; b < ELEMENTS.length; b++) {
            List<Node> l = shared();
            Object[] e = ELEMENTS[b];
            int[][] at = {{1, 1}, {2, 0}, {2, 2}, {3, 0}, {3, 2}, {4, 0}, {4, 2}};
            String[] par = {null, "e1", "e1", "e2", "e3", "e4", "e5"};
            for (int i = 0; i < 7; i++)
                l.add(new Node("e" + (i + 1), ELEMENT, at[i][0], at[i][1], par[i], TIER_COST[at[i][0]], (String) e[i * 3], (String) e[i * 3 + 1], ((Number) e[i * 3 + 2]).floatValue()));
            l.add(new Node("crown", CROWN, 5, 1, null, 4, CROWNS[b][0], "crown", 0));
            TREES.add(l);
        }
    }

    public static List<Node> nodes(int book) { return TREES.get(Math.max(0, Math.min(TREES.size() - 1, book))); }

    public static Node node(int book, String id) { for (Node n : nodes(book)) if (n.id().equals(id)) return n; return null; }

    public static int bookIndex(ItemStack book) {
        GrimoireMastery.Book b = GrimoireMastery.bookOf(book);
        return b == null ? -1 : GrimoireMastery.BOOKS.indexOf(b);
    }

    // ------------------------------------------------------------------ what a tree holds
    public static CompoundTag tree(ItemStack book) { return book == null || !book.hasTag() ? new CompoundTag() : book.getTag().getCompound("Tree"); }

    public static boolean has(ItemStack book, String id) { return tree(book).getBoolean(id); }

    /** Leaves the grimoire has grown (memory = Lost Memory chapters its mage recovered). */
    public static int earned(ItemStack book, int memory) {
        if (bookIndex(book) < 0) return 0;
        int ev = book.hasTag() ? Math.min(6, book.getTag().getCompound("Evolved").size()) : 0;
        return GrimoireMastery.level(book) * 2 + ev + (GrimoireMastery.ascended(book) ? 2 : 0) + (Awakening.awakened(book) ? 2 : 0)
                + (Ascension.ascended(book) ? 2 : 0) + Math.min(3, memory);
    }

    public static int spent(ItemStack book) {
        int b = bookIndex(book), n = 0;
        if (b < 0) return 0;
        CompoundTag t = tree(book);
        for (Node x : nodes(b)) if (t.getBoolean(x.id())) n += x.cost();
        return n;
    }

    public static int elementCount(ItemStack book) {
        CompoundTag t = tree(book);
        int n = 0;
        for (int i = 1; i <= 7; i++) if (t.getBoolean("e" + i)) n++;
        return n;
    }

    /** Why this node can't be learned now, or null if it can. */
    public static String blocked(ItemStack book, Node n, int memory) {
        if (has(book, n.id())) return "Learned";
        if (n.parent() != null && !has(book, n.parent())) return "Needs " + node(bookIndex(book), n.parent()).name();
        if (n.branch() == CROWN && elementCount(book) < CROWN_NEEDS) return "Needs " + CROWN_NEEDS + " element nodes";
        if (earned(book, memory) - spent(book) < n.cost()) return "Needs " + n.cost() + " leaves";
        return null;
    }

    /** Sum of an effect over what this grimoire has learned. */
    public static float sum(ItemStack book, String effect) {
        int b = bookIndex(book);
        if (b < 0 || !book.hasTag() || !book.getTag().contains("Tree")) return 0;
        CompoundTag t = tree(book);
        float s = 0;
        for (Node n : nodes(b)) if (n.effect().equals(effect) && t.getBoolean(n.id())) s += n.val();
        return s;
    }

    public static boolean crown(ItemStack book) { return has(book, "crown"); }

    /** What a node does, in words. */
    public static String describe(int book, Node n) {
        int pct = Math.round(n.val() * 100), v = Math.round(n.val());
        return switch (n.effect()) {
            case "mana" -> "+" + v + " max mana";
            case "regen" -> "+" + pct + "% mana regeneration";
            case "cost" -> pct + "% chance a spell costs no mana";
            case "cdr" -> "+" + pct + "% cooldown reduction";
            case "power" -> "+" + pct + "% spell power";
            case "killmana" -> "Kills restore " + pct + "% of your max mana";
            case "health" -> "+" + v + " max health";
            case "armor" -> "+" + v + " armour";
            case "speed" -> "+" + pct + "% movement speed";
            case "regenhp" -> "Heal " + v + " every 5 s";
            case "dodge" -> pct + "% chance to evade a blow";
            case "resist" -> "+" + pct + "% spell resistance, and knockback resistance";
            case "boss" -> "+" + pct + "% damage to bosses";
            case "hastecast" -> "Casting gives +" + pct + "% speed for 2 s";
            case "lowhp" -> "+" + pct + "% damage while below half health";
            case "fullhp" -> "+" + pct + "% damage while at full health";
            case "first" -> "+" + pct + "% damage to foes at full health";
            case "execute" -> "+" + pct + "% damage to foes below 30% health";
            case "crit" -> pct + "% chance a hit lands 50% harder";
            case "lifesteal" -> "Heal for " + pct + "% of the damage you deal";
            case "manasteal" -> pct + "% of the damage you deal returns as mana";
            case "weaken" -> "Hits weaken foes' attacks by " + pct + "% for 3 s";
            case "slow" -> "Hits slow foes by " + pct + "% for 2 s";
            case "chain" -> pct + "% chance a hit leaps to another foe for 40%";
            case "echo" -> pct + "% chance a hit strikes again for 40%";
            case "burn" -> "Hits linger: " + pct + "% more over 3 s";
            case "knock" -> "Hits throw foes back";
            case "pull" -> "Hits drag foes toward you";
            case "thorns" -> pct + "% of the damage you take strikes back";
            case "shield" -> "Casting gives " + v + " absorption (every 10 s)";
            case "refund" -> pct + "% chance a spell comes straight off cooldown";
            case "killheal" -> "Kills heal " + v;
            case "stacking" -> "Each cast in a fight: +" + pct + "% damage (up to 5)";
            case "gaugefill" -> "The Blood Gauge fills " + pct + "% faster";
            case "encase" -> "Ice encases foes " + (v / 20f) + " s longer";
            case "moonguard" -> "Under the Scarlet Moon you take " + pct + "% less damage";
            case "crown" -> CROWNS[Math.max(0, Math.min(CROWNS.length - 1, book))][1];
            default -> "";
        };
    }

    // ------------------------------------------------------------------ the stats, as curio modifiers on the book
    private static final String[] STATS = {"mana", "regen", "cdr", "power", "health", "armor", "speed", "resist"};
    private static final UUID[] IDS = new UUID[STATS.length + 1];
    static { for (int i = 0; i < IDS.length; i++) IDS[i] = UUID.fromString(String.format("5e1d7700-0000-4000-8000-%012x", i + 1)); }

    public static void refresh(ItemStack book) {
        CompoundTag tag = book.getOrCreateTag();
        if (tag.contains("CurioAttributeModifiers", Tag.TAG_LIST))
            tag.getList("CurioAttributeModifiers", Tag.TAG_COMPOUND).removeIf(t -> t instanceof CompoundTag ct && ct.hasUUID("UUID") && java.util.Arrays.asList(IDS).contains(ct.getUUID("UUID")));
        for (int i = 0; i < STATS.length; i++) {
            float v = sum(book, STATS[i]);
            if (v == 0) continue;
            Attribute a; AttributeModifier.Operation op = AttributeModifier.Operation.MULTIPLY_BASE;
            switch (STATS[i]) {
                case "mana" -> { a = AttributeRegistry.MAX_MANA.get(); op = AttributeModifier.Operation.ADDITION; }
                case "regen" -> a = AttributeRegistry.MANA_REGEN.get();
                case "cdr" -> a = AttributeRegistry.COOLDOWN_REDUCTION.get();
                case "power" -> a = AttributeRegistry.SPELL_POWER.get();
                case "health" -> { a = Attributes.MAX_HEALTH; op = AttributeModifier.Operation.ADDITION; }
                case "armor" -> { a = Attributes.ARMOR; op = AttributeModifier.Operation.ADDITION; }
                case "speed" -> a = Attributes.MOVEMENT_SPEED;
                default -> a = AttributeRegistry.SPELL_RESIST.get();
            }
            CuriosApi.addModifier(book, a, "Mana Tree", IDS[i], v, op, "spellbook");
            if (STATS[i].equals("resist")) CuriosApi.addModifier(book, Attributes.KNOCKBACK_RESISTANCE, "Mana Tree", IDS[STATS.length], 0.2, AttributeModifier.Operation.ADDITION, "spellbook");
        }
    }
}
