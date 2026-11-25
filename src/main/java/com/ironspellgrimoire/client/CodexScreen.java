package com.ironspellgrimoire.client;

import com.ironspellgrimoire.server.GrimoireMastery;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * The Grimoire Codex (item/CodexItem): the mod's guide book. An index down the left - the chapters, then every
 * grimoire - and the open page on the right, scrolled with the wheel. A grimoire's page is built from its own words
 * (its tooltip lines) and its mastery table, so it never drifts out of date.
 */
public class CodexScreen extends Screen {
    private static final int W = 372, H = 220, IDX_W = 118, ROW = 11;
    private static final int LEATHER = 0xFF15274A, LEATHER_L = 0xFF2B508F, GOLD = 0xFFE0B450, GOLD_L = 0xFFFFE48C, PARCH = 0xFFEEE2C2,
            INK = 0xFF2A1C10, INK_SOFT = 0xFF6A5638, INK_HEAD = 0xFF1F3A6B, IDX_TEXT = 0xFFD8D0B8;

    /** The chapters: a title and its paragraphs ("## " starts a sub-heading). */
    private static final String[][] CHAPTERS = {
        {"Getting Started",
         "Welcome, mage. This Codex is yours to keep - open it whenever you're lost.",
         "## The Tower",
         "Somewhere in the world stands a Grimoire Tower. New mages are handed a compass that points to the nearest one. Climb to the top and find the Grimoire Altar.",
         "## The ceremony",
         "At the altar the grimoires rise and circle above it, faster and faster, until one leaves the ring and comes to you. It has chosen you. In survival a grimoire chooses each mage once, and it is bound to you: only you can cast from it.",
         "## Lost your grimoire?",
         "Go back to any altar. It calls your grimoire back to you if you no longer carry it.",
         "## Casting",
         "Equip your grimoire as your spellbook and cast its spells like any other Iron's Spellbooks spell. Many grimoires also have modes, forms or menus of their own - their pages in this Codex say how."},
        {"Your Grimoire",
         "The grimoire you wear right now, and how far it has come with you. This page reads your book as it is."},
        {"Mastery",
         "Every grimoire grows as you fight with its magic, from level 1 to 5. Its stronger spells unlock as it does, and its ultimate waits at level 5.",
         "## Earning mastery",
         "Damage dealt with the book's spells, kills, bosses and time spent in one of its modes all count. Peaceful animals, villagers and armour stands give nothing.",
         "## What each level gives",
         "Every level above 1 adds spell power, cooldown reduction and max mana while the book is equipped. Progress is written on the book itself, so it keeps its level wherever it goes.",
         "## Spell evolution",
         "Cast a spell often enough and it evolves, once and for good: it hits harder, its hits flare in the book's colours and casting it throws a ring of magic from your feet."},
        {"Beyond Mastery",
         "## The Magic Knight Exam",
         "A grimoire at mastery 5 can take the exam, opened from the Grimoire Altar: five floors, each under its own law, then a hall of three boss shades. Pass, and its mastery can go on to 7.",
         "## Awakening",
         "A grimoire that passed the exam and reached mastery 6 may awaken in a real fight, when a boss's blow would leave you nearly dead. Time stops, a new page writes itself and the grimoire gains its awakened spell. Its mastery can then reach 8.",
         "## Ascension",
         "An awakened grimoire at mastery 8 can take the Ascension Trial at the altar and face its Reflection - you, with your own magic. Win, and every spell from it hits harder and burns gold.",
         "## Surpassing Limits",
         "Nearly dead in a hard fight, your grimoire can answer: it heals you to half health, and for a minute its ultimate becomes an evolved version.",
         "## The Star Forge",
         "Sneak and right-click the altar with an empty hand to spend merit stars on your grimoire: empower spells, add ink pages, set a star sigil on the cover, or mutate a spell with a Mana Core."},
        {"Elite Mobs",
         "Some hostile mobs are elites: a coloured name and an icon over the head for each trait they carry. They hit harder in their own ways, and pay better - more XP and more drops.",
         "## Threat",
         "The better your grimoire, the more dangerous the mobs near you. Your threat rank (0 to 5) comes from the best grimoire you carry: a skull with pips over a mob shows its rank. Each rank gives a mob 20% more health and 10% more damage.",
         "## Dangerous lands",
         "The land sets a rank of its own: within 1000 blocks of the world's spawn, nothing; past that rank 1, from 2000 rank 2, from 3000 rank 3 and from 5000 rank 4. The Nether counts one band higher and the End starts at 3. A mob takes the higher of the land's rank and yours. You're told when you cross into a new band.",
         "## Packs",
         "Now and then a mob leads a pack: a gold ring at its feet, three of its kind beside it. While the leader lives, its pack hits 20% harder and moves faster. Bring the leader down and the pack scatters.",
         "## Elites that learn",
         "At rank 4 and 5 an elite may have learned your magic: it takes 35% less from the school you cast most. Switching books for a fight works.",
         "## Champions",
         "At threat 4 and 5 a few elites are champions, with a purple name and a trait no lesser elite has. A champion is worth a bounty star.",
         "## The traits you've met"},
        {"Rivals",
         "Four human mages hear about you once your grimoire makes a name for itself (threat rank 2 and up). Now and then one of them finds you - or call one yourself with a Rival's Token from a Poster Board (3 merit stars; sneak and use it to choose who).",
         "## The ring",
         "A rival fights inside a ring 16 blocks across its middle. Step out and they stop and wait for you - and your blows from outside don't land. Stay away a minute and they leave.",
         "## Yielding and rematches",
         "Rivals never die: at the end they yield. A win pays merit stars and mastery XP; the first win over each also gives their keepsake (a charm) and a title. Every win raises their rank for the rematch, up to 3: more health, harder blows and a new move.",
         "## Your record"},
        {"The World",
         "## Rifts",
         "Now and then a crack of light tears open near you and stays for a day. Step in to enter a dungeon of five to eight rooms under one or two laws. The relic at the end is found nowhere else. As you climb the boss tiers, rifts run deeper - two more rooms a tier, a mini-boss every third room, and better Mana Cores the deeper you go.",
         "## Wild families",
         "Three families live in the wild, each a grunt, a caster and a leader. The Mirefolk of the marshes (Bog Crawlers, Mire Hexers and the Fen Matron), the Stonefolk of the mountains (Scree Brutes, Stonecallers and the Peak Warden) and the Sandwraiths of the deserts and badlands (Dune Stalkers, Sandwraiths and the Sun Herald). A caster marks the ground under you, then strikes there a second later - step out. A leader rallies its family every 10 seconds, and has a move of its own. Leaders sometimes leave something the Grimoire Altar binds.",
         "## The Rimefolk and the Hollowfolk",
         "In the snows, the Rimefolk: Rime Brutes, Frost Chanters whose strikes slow you, and the Hoarfrost Jarl, whose slam chills. In the caves and the deep dark, the Hollowfolk: Hollow Crawlers, Hollow Singers who strike out of the dark with less warning, and the Deepwarden, whose lantern-heart pulls you in. Every family leader may drop its charm - the Moss Knot, Slate Ring, Sun Shard, Rime Pin or Lantern Eye.",
         "## The Blood Moon",
         "About one night in ten, everyone is told: until dawn, elites come oftener and each rolls one trait more, the wild leaders walk with their bands, and bounties pay twice the stars.",
         "## Falling stars",
         "Now and then a star falls a few hundred blocks from a mage, and everyone hears roughly where. It lands without harming the land, and elites gather round it. Bring them down, then take the Star Core: crack it open for a tier II or III Mana Core.",
         "## The Gloamstalker",
         "At night, a mage of threat rank 3 or more may be hunted. It steps behind you every 8 seconds, shrugs off 40% of the school of magic you cast most, and fades at dawn. It pays well.",
         "## Quest Board",
         "Every village gets a Quest Board. Take one quest at a time: hooded mages, monster hunts, rift watches, great beasts and lost grimoire pages. The tracker shows the way.",
         "## Wanted Posters",
         "The Poster Board hands out bounties on the bosses: find the boss's lair within three days and bring it down for merit stars and grimoire upgrades. The Bosses chapter has the rest.",
         "## The world boss",
         "About once a real day the Cinder Sovereign rises somewhere in the world and every mage is told where. All who hurt it share the reward. Once it has fallen on the server it returns in its second form: below a quarter of its health it takes the Ashen Crown - its fire burns white-blue, rings of ash fall under every mage, and pillars of white fire follow you.",
         "## The Grimoire Library",
         "Every mage ever chosen gets a copy of their grimoire on the Library's shelves. Read it at the archive in a Grimoire Tower, or from any altar.",
         "## Familiars",
         "Spend a Mana Core at the altar and a familiar hatches. It takes your grimoire's element and grows with its mastery."},
        {"Growing Grimoires",
         "A few grimoires grow far beyond mastery. Each has its own menu (see Controls for its key) and its own way of getting stronger.",
         "## System Grimoire",
         "Raise what you slay as your shadows. They level up and rank up from Soldier to General; open the army menu to equip, rename or release them. The System also gives you levels, stats and a job.",
         "## Parasite Grimoire",
         "A strain that rewrites its host. Evolve through the ranks, grow forms on your arms and choose an apex at the top.",
         "## Hunger Blade Grimoire",
         "A sword that is always hungry. Feed it kills to grow new swords - 29 in all - deepen your Bond with it and earn titles.",
         "## Masked Twins Grimoire",
         "Two masks, two twins, sixteen arts. Swap between Sol and Nyx, level both, set stones in the masks, beat the Twin Wardens and join them in the Eclipse.",
         "## Architect of Ruin Grimoire",
         "A fortress inside a book. Earn Stone from foes, build rooms on a floor plan that grows, pair rooms side by side, claim banners for a garrison, raise Siege Mode's walls and towers round you (a Ring, a Line or a Horseshoe) and command it with G. A finished fortress can be rebuilt for stars. Its own menu has a guided tour and a full guide."},
        {"Exclusive Trees and the G Wheel",
         "Some grimoires grow a tree of their own, apart from the Mana Tree: the Vector Field, the Fuse Board, the Sightlines and the Bond Tree.",
         "## Growing it",
         "Each of those books counts what it does best - force turned aside, foes caught in a blast, blows dodged, battles fought for others - and that growth makes levels, up to 30. Every level is a point, and every mastery level two more (the Bond Tree, the biggest, gets twice the growth's).",
         "## What's in a tree",
         "Small stats with a few ranks; passives that change how the book behaves; upgrades that change a page; wheel nodes that open an ability on the G wheel; and keystones, in pairs - take one side, and the other shuts. There is always more to learn than points to learn it with.",
         "## Changing your mind",
         "Unlearn All is free, once every five minutes, at the foot of the tree's side panel.",
         "## The G wheel",
         "G opens the wheel of the book you wear (if it has one). Hold G, point, let go - or click. Its middle opens the book's own menu or tree. Abilities still locked say what opens them; ones resting show their seconds."},
        {"Mode Timers",
         "Whatever you have running - a stance, a shield, a buff, a held parry, a takeover - shows top right, under any potion icons: its icon, its name and the time left, with a bar that drains.",
         "## Charges",
         "Anything that counts - blows left to block, stacks, allies bound - shows the count beside the time.",
         "## Standing modes",
         "A stance or setting with no end (a Vector stance, a Fuse mode, the demon Restrained or Released) says \"active\" instead of a time, until you change it back.",
         "## The last ten seconds",
         "A timer blinks, like a potion's, in its last ten seconds."},
        {"Bosses",
         "The bounty bosses come in three tiers. Posters only show the tiers you've reached.",
         "## Tiers",
         "Tier I - the Revenant and the Curse Witch - is open from the start. Bring one down and tier II opens: the Ossuarch, Rhya, the Beast of Despair and the Ironbound Twins. Bring one of those down and the Poster Board offers the Gate - two gatekeepers, one after another. Pass it and tier III opens: Licht, and with 50 stars the poster with no name.",
         "## Lairs",
         "Reach the place on the poster and the boss's lair tears open there: step in, and it waits in the next room. Fall and you're thrown back out; the lair stays open while the poster lasts, and the boss rises again at full health.",
         "## How they fight you",
         "A boss grows with you: 10% more health for each threat rank of whoever took the poster, and 60% more for every other mage who comes to the fight. It fights in stages - two for tier I, three for tier II, four and a Last Stand for tier III - and each stage throws you back and makes it faster. Three minutes in, it enrages. From threat rank 3 it has the Reckoning: a ring under one of you, then a burst.",
         "## Weak points",
         "Every 15 seconds a gold light opens on a boss's back or side for 3 seconds. Hit it from that side and the boss is staggered: it can't move or strike for 2.5 seconds and takes 50% more.",
         "## Memory",
         "A boss you've beaten remembers how - melee, spells or projectiles - and guards against it for the first 30 seconds of the rematch.",
         "## Rewards",
         "Everyone who hurt the boss gets the kill in their log, mastery experience and a Mana Core of its tier. Each boss has a signature drop - certain for whoever took the poster, a good chance for the rest. A bounty streak pays up to 50% more stars; a poster that burns up or is torn up resets it. Beat a boss three times and its posters turn Hardened: harder, but paying twice, with the upgraded signature drop.",
         "## The world grows",
         "The first time each boss falls anywhere on the server, something new wakes in the world - packs near spawn, bigger packs, new elite traits at rank 4 and 5, more champions. Everyone is told.",
         "## Your log"},
        {"Server Rules",
         "Your server can change how the mod plays in its config file (iron_spell_grimoire-common.toml).",
         "## Player versus player",
         "Every grimoire follows one rule: its magic can hurt other players only when grimoire PvP is on in the config, the server allows PvP, and you aren't on the same team.",
         "## Other options",
         "Server admins can scale all grimoire damage, turn rifts and the world boss on or off, set how often rifts open and leave some grimoires out of the ceremony."},
    };

    private final List<Integer> index = new ArrayList<>();   // >= 0: a chapter; <= -2: grimoire -(i + 2); -1: the grimoires heading
    private int sel = 0, idxScroll = 0, pageScroll = 0;
    private List<Line> page = List.of();
    private ItemStack pageIcon = ItemStack.EMPTY;
    private String pageTitle = "";

    private record Line(FormattedCharSequence text, int color, boolean shadow) {}

    /** The page you had open last (this session): the Codex opens there again. The first time, Getting Started. */
    private static int lastSel = 0;

    public CodexScreen() { this(lastSel); }

    /** Open at an index row (0 = the first chapter; CHAPTERS.length + 1 = the first grimoire). */
    public CodexScreen(int at) {
        super(Component.literal("Grimoire Codex"));
        for (int i = 0; i < CHAPTERS.length; i++) index.add(i);
        index.add(-1);
        for (int i = 0; i < GrimoireMastery.BOOKS.size(); i++) index.add(-(i + 2));
        sel = Mth.clamp(at, 0, index.size() - 1);
        if (index.get(sel) == -1) sel++;
    }

    public static int chapters() { return CHAPTERS.length; }
    /** The index row of a chapter by its title (-1: none). */
    public static int chapter(String title) { for (int i = 0; i < CHAPTERS.length; i++) if (CHAPTERS[i][0].equals(title)) return i; return -1; }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new CodexScreen());
        if (mc.player != null) mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.9F);
    }

    @Override public boolean isPauseScreen() { return false; }
    private int x0() { return (width - W) / 2; }
    private int y0() { return (height - H) / 2; }
    private int idxRows() { return (H - 34) / ROW; }
    private int pageW() { return W - IDX_W - 28; }
    private int pageRows() { return (H - 30 - 34) / 10; }

    @Override
    protected void init() {
        if (sel >= idxScroll + idxRows()) idxScroll = sel - idxRows() + 1;
        build();
    }

    private void build() {
        lastSel = sel;
        int w = pageW() - 12;
        List<Line> out = new ArrayList<>();
        int e = index.get(sel);
        pageIcon = ItemStack.EMPTY;
        if (e >= 0) {
            String[] ch = CHAPTERS[e];
            pageTitle = ch[0];
            for (int i = 1; i < ch.length; i++) para(out, ch[i], w);
            if (ch[0].equals("Elite Mobs")) elites(out, w);
            if (ch[0].equals("Rivals")) rivals(out, w);
            if (ch[0].equals("Your Grimoire")) yours(out, w);
            if (ch[0].equals("Bosses")) bosses(out, w);
        } else if (e <= -2) {
            GrimoireMastery.Book b = GrimoireMastery.BOOKS.get(-e - 2);
            pageIcon = new ItemStack(b.item().get());
            pageTitle = pageIcon.getHoverName().getString();
            String key = "tooltip." + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(pageIcon.getItem()).toString().replace(':', '.');
            for (String part : new String[]{"desc", "passive"}) if (I18n.exists(key + "." + part)) para(out, I18n.get(key + "." + part), w);
            if (!b.unlocks().isEmpty()) {
                para(out, "## Spells by mastery level", w);
                int last = -1;
                for (var en : b.unlocks().entrySet()) {
                    String nm = Component.translatable("spell.iron_spell_grimoire." + en.getKey()).getString();
                    String lv = en.getValue() != last ? "Lv " + en.getValue() + "  " : "        ";
                    last = en.getValue();
                    for (var l : font.split(Component.literal(lv + nm), w)) out.add(new Line(l, INK, false));
                }
                out.add(new Line(FormattedCharSequence.EMPTY, INK, false));
            }
            int tk = com.ironspellgrimoire.server.BookTree.kind(pageIcon);
            if (tk >= 0) {   // its own tree and wheel
                para(out, "## " + com.ironspellgrimoire.server.BookTree.TREE[tk] + " - its own tree", w);
                para(out, "Grows with: " + com.ironspellgrimoire.server.BookTree.GROWTH[tk].toLowerCase() + ". " + com.ironspellgrimoire.server.BookTree.TREES.get(tk).size() + " nodes.", w);
                StringBuilder keys = new StringBuilder("Keystones: ");
                for (var n : com.ironspellgrimoire.server.BookTree.TREES.get(tk))
                    if (n.kind() == com.ironspellgrimoire.server.BookTree.KEY && n.id().compareTo(n.excl()) < 0)
                        keys.append(n.name()).append(" or ").append(com.ironspellgrimoire.server.BookTree.node(tk, n.excl()).name()).append(";  ");
                para(out, keys.toString().replaceAll(";  $", "."), w);
                para(out, "## The G wheel", w);
                for (int i = 0; i < 4; i++) para(out, com.ironspellgrimoire.server.BookTree.WHEEL_NAMES[tk][i] + " - " + com.ironspellgrimoire.server.BookTree.WHEEL_DESC[tk][i], w);
            }
            if (I18n.exists(key + ".lore")) for (var l : font.split(Component.literal(I18n.get(key + ".lore")).withStyle(ChatFormatting.ITALIC), w)) out.add(new Line(l, INK_SOFT, false));
        } else pageTitle = "";
        page = out;
        pageScroll = 0;
    }

    /** Your Grimoire: the book you wear - its mastery and the way to the next level, evolved spells, Mana Tree leaves,
     *  and its own tree's level and points if it has one. */
    private void yours(List<Line> out, int w) {
        var mc = Minecraft.getInstance();
        ItemStack b = mc.player == null ? ItemStack.EMPTY : io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(mc.player);
        if (b == null || b.isEmpty() || GrimoireMastery.bookOf(b) == null) { para(out, "You aren't wearing a grimoire. Equip one as your spellbook, then open this page again.", w); return; }
        para(out, "## " + b.getHoverName().getString(), w);
        int xp = GrimoireMastery.xp(b), cap = GrimoireMastery.cap(b), lvl = GrimoireMastery.levelFor(xp, cap);
        int[] th = GrimoireMastery.THRESHOLDS;
        para(out, "Mastery level " + lvl + " of " + cap + (lvl >= cap ? " - the most it can reach." : " - " + (th[lvl + 1] - xp) + " experience to level " + (lvl + 1) + "."), w);
        int ev = b.hasTag() ? b.getTag().getCompound("Evolved").size() : 0;
        para(out, "Spells evolved: " + ev + ".  " + (GrimoireMastery.ascended(b) ? "Ascended." : ""), w);
        para(out, "Mana Tree leaves spent: " + com.ironspellgrimoire.server.SkillTree.spent(b) + ".", w);
        int k = com.ironspellgrimoire.server.BookTree.kind(b);
        if (k >= 0) {
            float txp = com.ironspellgrimoire.server.BookTree.xp(b);
            para(out, com.ironspellgrimoire.server.BookTree.TREE[k] + ": level " + com.ironspellgrimoire.server.BookTree.level(txp) + " (" + com.ironspellgrimoire.server.BookTree.GROWTH[k].toLowerCase() + ": " + (int) txp + "), "
                    + (com.ironspellgrimoire.server.BookTree.points(b) - com.ironspellgrimoire.server.BookTree.spent(b)) + " of " + com.ironspellgrimoire.server.BookTree.points(b) + " points to spend. G, then the wheel's middle, opens it.", w);
        }
        para(out, "## Mastery quests", w);
        int q = com.ironspellgrimoire.server.BookQuests.done(b);
        for (int i = 0; i < 5; i++) {
            int got = Math.min(com.ironspellgrimoire.server.BookQuests.progress(b, i), com.ironspellgrimoire.server.BookQuests.GOAL[i]);
            boolean ok = (q & (1 << i)) != 0;
            String line = (ok ? "[done] " : "[" + got + "/" + com.ironspellgrimoire.server.BookQuests.GOAL[i] + "] ") + com.ironspellgrimoire.server.BookQuests.TEXT[i];
            for (var l : font.split(Component.literal(line).withStyle(ok ? ChatFormatting.STRIKETHROUGH : ChatFormatting.RESET), w)) out.add(new Line(l, ok ? INK_SOFT : INK, false));
        }
        para(out, "Each quest done: +" + com.ironspellgrimoire.server.BookQuests.XP + " mastery" + (k >= 0 ? " and a tree point" : "") + ".", w);
    }

    /** The Bosses chapter's log: your tier, the Gate, your streak, the land's danger, then each boss - its tier, your kills, best time, falls, what it remembers, its signature drop. */
    private void bosses(List<Line> out, int w) {
        var d = BossLogClient.data;
        int tier = Math.max(1, d.getInt("tier"));
        para(out, "Your tier: " + com.ironspellgrimoire.server.BossLadder.TIER_NAME[tier] + ".  " + (d.getBoolean("gate") ? "The Gate: passed." : d.getBoolean("gateOpen") ? "The Gate: on offer at any Poster Board." : "")
                + "  Bounty streak: " + d.getInt("streak") + (d.getInt("streak") > 1 ? " (+" + Math.round((com.ironspellgrimoire.server.BossLadder.streakMult(d.getInt("streak")) - 1) * 100) + "% stars)" : "") + ".", w);
        int danger = Math.max(0, Math.min(4, d.getInt("danger")));
        para(out, "Where you stand: " + com.ironspellgrimoire.server.EliteMobs.DANGER_TEXT[danger] + ".  Your threat rank: " + d.getInt("rank") + ".", w);
        int[] k = BossLogClient.arr("k"), b = BossLogClient.arr("b"), f = BossLogClient.arr("d"), m = BossLogClient.arr("m");
        int[] order = {4, 5, 0, 2, 1, 7, 3, 6};
        for (int i : order) {
            int t = com.ironspellgrimoire.server.BossLadder.TIER[i];
            String name = i == 6 ? "Licht, the Unbound" : com.ironspellgrimoire.server.Bounties.NAMES[i];
            if (t > tier && k[i] == 0) { for (var l : font.split(Component.literal("Tier " + com.ironspellgrimoire.server.BossLadder.TIER_NAME[t] + " - ??? (not open yet)"), w)) out.add(new Line(l, INK_SOFT, false)); continue; }
            para(out, "## " + name + "  (tier " + com.ironspellgrimoire.server.BossLadder.TIER_NAME[t] + ")", w);
            String rec = "Brought down " + k[i] + "  -  best time " + (b[i] > 0 ? com.ironspellgrimoire.server.BossRules.time(b[i]) : "-") + "  -  fell to it " + f[i]
                    + (k[i] >= com.ironspellgrimoire.server.BossLadder.HARDENED_AFTER ? "  -  its posters are Hardened" : "");
            for (var l : font.split(Component.literal(rec).withStyle(ChatFormatting.BOLD), w)) out.add(new Line(l, INK, false));
            if (m[i] > 0) for (var l : font.split(Component.literal("It remembers you beat it with " + com.ironspellgrimoire.server.BossLadder.HOW[m[i]] + ".").withStyle(ChatFormatting.ITALIC), w)) out.add(new Line(l, INK_SOFT, false));
            ItemStack sig = new ItemStack(com.ironspellgrimoire.registry.AddonItemRegistry.SIGNATURES.get(i).get());
            List<Component> tip = new ArrayList<>();
            sig.getItem().appendHoverText(sig, null, tip, net.minecraft.world.item.TooltipFlag.NORMAL);
            String what = tip.size() > 1 ? tip.get(1).getString() : "";
            for (var l : font.split(Component.literal("Signature: " + sig.getHoverName().getString() + " - " + what), w)) out.add(new Line(l, INK, false));
            out.add(new Line(FormattedCharSequence.EMPTY, INK, false));
        }
    }

    /** The Rivals chapter: each rival - your record, what they do (once you've met them), and the title won. */
    private void rivals(List<Line> out, int w) {
        for (int i = 0; i < com.ironspellgrimoire.server.Rivals.COUNT; i++) {
            int met = i < RivalClient.met.length ? RivalClient.met[i] : 0, won = i < RivalClient.wins.length ? RivalClient.wins[i] : 0,
                    lost = i < RivalClient.losses.length ? RivalClient.losses[i] : 0;
            if (met == 0) { for (var l : font.split(Component.literal("??? - not met yet"), w)) out.add(new Line(l, INK_SOFT, false)); continue; }
            para(out, "## " + com.ironspellgrimoire.server.Rivals.NAMES[i] + ", " + com.ironspellgrimoire.server.Rivals.EPITHETS[i], w);
            String rec = "Met " + met + "  -  won " + won + ", lost " + lost + "  -  next rank " + Math.min(3, won)
                    + (won > 0 ? "  -  title: " + com.ironspellgrimoire.server.Rivals.TITLES[i] : "");
            for (var l : font.split(Component.literal(rec).withStyle(ChatFormatting.BOLD), w)) out.add(new Line(l, INK, false));
            for (var l : font.split(Component.literal(com.ironspellgrimoire.server.Rivals.ABOUT[i]), w)) out.add(new Line(l, INK, false));
            for (var l : font.split(Component.literal("Moves: " + com.ironspellgrimoire.server.Rivals.MOVES[i]).withStyle(ChatFormatting.ITALIC), w)) out.add(new Line(l, INK_SOFT, false));
            for (var l : font.split(Component.literal(com.ironspellgrimoire.server.Rivals.RANK_MOVES[i]).withStyle(ChatFormatting.ITALIC), w)) out.add(new Line(l, INK_SOFT, false));
            out.add(new Line(FormattedCharSequence.EMPTY, INK, false));
        }
    }

    /** The Elite Mobs chapter: your threat rank, then each trait - what it does and how to beat it - once you've met it. */
    private void elites(List<Line> out, int w) {
        int seen = EliteClient.seenMask(), n = Integer.bitCount(seen);
        para(out, "Your threat rank: " + EliteClient.threat() + ".  Traits met: " + n + " of " + com.ironspellgrimoire.server.EliteMobs.COUNT + ".", w);
        for (int i = 0; i < com.ironspellgrimoire.server.EliteMobs.COUNT; i++) {
            boolean met = (seen & (1 << i)) != 0;
            String champ = (com.ironspellgrimoire.server.EliteMobs.CHAMPION & (1 << i)) != 0 ? " (champion)" : "";
            if (!met) { for (var l : font.split(Component.literal("???" + champ + " - not met yet"), w)) out.add(new Line(l, INK_SOFT, false)); continue; }
            para(out, "## " + com.ironspellgrimoire.server.EliteMobs.NAME[i] + champ, w);
            String[] says = com.ironspellgrimoire.server.EliteMobs.SAYS[i];
            for (var l : font.split(Component.literal(says[0]), w)) out.add(new Line(l, INK, false));
            for (var l : font.split(Component.literal("Beat it: " + says[1]).withStyle(ChatFormatting.ITALIC), w)) out.add(new Line(l, INK_SOFT, false));
            out.add(new Line(FormattedCharSequence.EMPTY, INK, false));
        }
    }

    private void para(List<Line> out, String s, int w) {
        if (s.startsWith("## ")) {
            for (var l : font.split(Component.literal(s.substring(3)).withStyle(ChatFormatting.BOLD), w)) out.add(new Line(l, INK_HEAD, false));
            return;
        }
        for (var l : font.split(Component.literal(s), w)) out.add(new Line(l, INK, false));
        out.add(new Line(FormattedCharSequence.EMPTY, INK, false));
    }

    private int rowAt(double mx, double my) {
        int ix = x0() + 8, iy = y0() + 26;
        if (mx < ix || mx >= ix + IDX_W || my < iy) return -1;
        int r = (int) ((my - iy) / ROW);
        if (r >= idxRows()) return -1;
        int i = idxScroll + r;
        return i < index.size() ? i : -1;
    }

    private void select(int i) {
        if (i < 0 || i >= index.size() || index.get(i) == -1 || i == sel) return;
        sel = i;
        if (sel < idxScroll) idxScroll = sel;
        if (sel >= idxScroll + idxRows()) idxScroll = sel - idxRows() + 1;
        build();
        Minecraft.getInstance().player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.7F, 1.1F);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int i = rowAt(mx, my);
        if (i >= 0) { select(i); return true; }
        return super.mouseClicked(mx, my, btn);
    }

    /** For the pictures (UiShots): scroll the page down by rows. */
    public void scrollPage(int rows) { pageScroll = Mth.clamp(pageScroll + rows, 0, Math.max(0, page.size() - pageRows())); }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        int step = d < 0 ? 1 : -1;
        if (mx < x0() + 8 + IDX_W) idxScroll = Mth.clamp(idxScroll + step * 2, 0, Math.max(0, index.size() - idxRows()));
        else pageScroll = Mth.clamp(pageScroll + step * 2, 0, Math.max(0, page.size() - pageRows()));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) { int n = sel + 1; if (n < index.size() && index.get(n) == -1) n++; select(n); return true; }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) { int n = sel - 1; if (n >= 0 && index.get(n) == -1) n--; select(n); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x0 = x0(), y0 = y0();
        g.drawManaged(() -> {
            g.fill(x0 - 4, y0 - 4, x0 + W + 4, y0 + H + 4, 0xFF0E0A06);
            g.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, GOLD);
            g.fill(x0, y0, x0 + W, y0 + H, LEATHER);
            g.fill(x0, y0, x0 + W, y0 + 1, LEATHER_L);
        });
        String t = "GRIMOIRE CODEX";
        g.drawString(font, t, x0 + 8 + (IDX_W - font.width(t)) / 2, y0 + 9, GOLD_L, true);

        // the index
        int ix = x0 + 8, iy = y0 + 26, hover = rowAt(mx, my);
        for (int r = 0; r < idxRows() && idxScroll + r < index.size(); r++) {
            int i = idxScroll + r, e = index.get(i), y = iy + r * ROW;
            if (e == -1) { g.fill(ix + 2, y + 5, ix + IDX_W - 2, y + 6, GOLD); String h = " Grimoires "; int hw = font.width(h); g.fill(ix + (IDX_W - hw) / 2, y + 1, ix + (IDX_W + hw) / 2, y + 10, LEATHER); g.drawString(font, h, ix + (IDX_W - hw) / 2, y + 1, GOLD, false); continue; }
            if (i == sel) g.fill(ix, y, ix + IDX_W, y + ROW, 0xFF35609F);
            else if (i == hover) g.fill(ix, y, ix + IDX_W, y + ROW, 0x40FFFFFF);
            String nm = e >= 0 ? CHAPTERS[e][0] : new ItemStack(GrimoireMastery.BOOKS.get(-e - 2).item().get()).getHoverName().getString();
            if (font.width(nm) > IDX_W - 8) nm = font.plainSubstrByWidth(nm, IDX_W - 12) + "..";
            g.drawString(font, nm, ix + 4, y + 2, i == sel ? GOLD_L : e >= 0 ? 0xFFF0E6C8 : IDX_TEXT, false);
        }
        if (index.size() > idxRows()) {   // the index's scroll bar
            int bh = idxRows() * ROW, kh = Math.max(10, bh * idxRows() / index.size()), ky = iy + (bh - kh) * idxScroll / Math.max(1, index.size() - idxRows());
            g.fill(ix + IDX_W + 2, iy, ix + IDX_W + 4, iy + bh, 0xFF0E1A33);
            g.fill(ix + IDX_W + 2, ky, ix + IDX_W + 4, ky + kh, GOLD);
        }

        // the page
        int px = x0 + IDX_W + 18, py = y0 + 8, pw = pageW(), ph = H - 16;
        g.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, GOLD);
        g.fill(px, py, px + pw, py + ph, PARCH);
        int tx = px + 6;
        if (!pageIcon.isEmpty()) { g.renderItem(pageIcon, px + 6, py + 6); tx = px + 26; }
        String pt2 = pageTitle;
        if (font.width(pt2) > px + pw - 6 - tx) pt2 = font.plainSubstrByWidth(pt2, px + pw - 10 - tx) + "..";
        g.drawString(font, pt2, tx, py + 10, INK_HEAD, false);
        g.fill(px + 6, py + 26, px + pw - 6, py + 27, GOLD);
        int ly = py + 32;
        for (int k = pageScroll; k < page.size() && k < pageScroll + pageRows(); k++) {
            Line l = page.get(k);
            g.drawString(font, l.text(), px + 6, ly, l.color(), l.shadow());
            ly += 10;
        }
        if (page.size() > pageRows()) {
            String more = pageScroll + pageRows() < page.size() ? "v" : "^";
            g.drawString(font, more, px + pw - 10, py + ph - 11, INK_SOFT, false);
        }
        super.render(g, mx, my, pt);
    }
}
