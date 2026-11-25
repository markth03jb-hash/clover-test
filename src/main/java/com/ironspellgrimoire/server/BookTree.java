package com.ironspellgrimoire.server;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.BookTreePackets;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

/**
 * The exclusive trees of three grimoires - the Vector Field (Vector Grimoire), the Fuse Board (Grimoire of the Quiet
 * Fuse) and the Sightlines (Grimoire of the Foreseen). Each is its own, apart from the shared Mana Tree, and grows its
 * own way:
 * <ul>
 *   <li>Growth: each book counts what it does best (force redirected, foes caught in blasts, blows dodged) as growth;
 *       growth makes levels (up to 30), and each level - and each mastery level, twice - is a point.</li>
 *   <li>Nodes: small stats with several ranks, new passives, skill upgrades that change a page, wheel nodes that unlock
 *       an ability on the G wheel, and keystones that come in pairs - take one, never both. There is more to learn
 *       than points to learn it with, so every mage's tree is their own.</li>
 *   <li>Unlearning is free, once every five minutes.</li>
 * </ul>
 * Kept on the book ("BT": "r" ranks, "xp" growth). Shared by both sides (the screen reads the nodes).
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.ironspellgrimoire.IronSpellGrimoire.MODID)
public final class BookTree {
    private BookTree() {}

    private static final Map<UUID, Integer> LAST_KIND = new HashMap<>();

    /** Keep the client's copy fresh: once a second while one of the three is worn, and once when it comes off. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onTick(net.minecraftforge.event.TickEvent.PlayerTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        int k = kind(p);
        Integer last = LAST_KIND.get(p.getUUID());
        if (last == null || last != k) { LAST_KIND.put(p.getUUID(), k); sync(p, false); }
        else if (k >= 0 && p.tickCount % 20 == 0) sync(p, false);
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) { LAST_KIND.remove(e.getEntity().getUUID()); WHEEL_CD.remove(e.getEntity().getUUID()); }

    /** A real blow - a fist, a weapon, a mob's strike - not a spell, a blast or a thorn. */
    public static boolean melee(net.minecraft.world.damagesource.DamageSource s) {
        return s.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK) || s.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK)
                || s.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK_NO_AGGRO);
    }

    public static final int VECTOR = 0, FUSE = 1, FORESEEN = 2, BOUND = 3;
    public static final int STAT = 0, PASSIVE = 1, SKILL = 2, WHEEL = 3, KEY = 4, ROOT = 5;
    public static final int MAX_LEVEL = 30, RESPEC_TICKS = 6000;
    public static final String[] TREE = {"The Vector Field", "The Fuse Board", "The Sightlines", "The Bond Tree"};
    public static final String[] GROWTH = {"Force redirected", "Foes caught in blasts", "Blows dodged", "Battles fought for others"};

    /** One node: where it sits on the board (x 0-8, y 0-7), what it needs (any one of), its ranks and cost a rank,
     *  what kind it is, its words, its icon (a spell icon, or "" for a drawn glyph), and the keystone it excludes. */
    public record Node(String id, int x, int y, String[] req, int ranks, int cost, int kind, String name, String desc, String icon, String excl) {}

    private static Node n(String id, int x, int y, String req, int ranks, int cost, int kind, String name, String desc, String icon) {
        return new Node(id, x, y, req == null ? new String[0] : req.split("\\|"), ranks, cost, kind, name, desc, icon, null);
    }
    private static Node k(String id, int x, int y, String req, String excl, String name, String desc) {
        return new Node(id, x, y, req.split("\\|"), 1, 3, KEY, name, desc, "", excl);
    }

    public static final List<List<Node>> TREES = List.of(vector(), fuse(), foreseen(), bound());

    // ------------------------------------------------------------------ the Vector Field
    private static List<Node> vector() {
        List<Node> l = new ArrayList<>();
        l.add(n("root", 4, 0, null, 1, 0, ROOT, "Redirection", "Some of every projectile that strikes you is turned back at its sender, and some of every blow's force goes back to the striker. Both grow with your Vector level.", "redirect"));
        // Reflection
        l.add(n("r1", 1, 1, "root", 3, 1, STAT, "Deflection Angle", "+5% chance to reflect a projectile, per rank.", ""));
        l.add(n("r2", 1, 2, "r1", 3, 1, STAT, "Kinetic Return", "+5% of a blow's force returned to the striker, per rank.", ""));
        l.add(n("r3", 0, 3, "r1", 1, 2, PASSIVE, "Sharpened Return", "Arrows you reflect strike 30% harder.", ""));
        l.add(n("r4", 2, 3, "r2", 1, 2, PASSIVE, "Recoil Plating", "Each reflect or return gives +1 armour for 6 s, stacking to +5.", ""));
        l.add(n("r5", 1, 4, "r3|r4", 1, 2, SKILL, "Perfect Angle", "Return to Sender lasts 5 s instead of 3, and what it returns flies 50% faster.", "return_to_sender"));
        // Momentum
        l.add(n("m1", 3, 1, "root", 3, 1, STAT, "Mass Transfer", "Redirect throws 15% harder and hits 10% harder, per rank.", ""));
        l.add(n("m2", 3, 2, "m1", 1, 2, PASSIVE, "Impact Vector", "A creature you throw that slams into another deals 6 to both.", ""));
        l.add(n("m3", 4, 2, "m1", 1, 2, SKILL, "Pulse Depth", "Pulse Touch carries through 2 more creatures, half again as hard.", "pulse_touch"));
        l.add(n("m4", 3, 3, "m2", 1, 2, SKILL, "Pressure Lanes", "Pressure Gust fires three lanes in a fan.", "pressure_gust"));
        l.add(n("m5", 3, 4, "m3|m4", 1, 3, PASSIVE, "Terminal Velocity", "Land from 4 blocks or more and the fall goes into the ground instead of you: 1.5 damage a block to foes within 4.", ""));
        // Flow
        l.add(n("f1", 5, 1, "root", 2, 1, STAT, "Air Purchase", "+1 Recoil Step before you land, per rank.", ""));
        l.add(n("f2", 5, 2, "f1", 1, 1, PASSIVE, "Soft Landing", "You take no fall damage.", ""));
        l.add(n("f3", 6, 2, "f1", 1, 2, PASSIVE, "Slipstream", "Sprinting builds speed: +5% each second, up to +30%.", ""));
        l.add(n("f4", 6, 3, "f3", 1, 2, SKILL, "Wind Lattice", "Wind Vector holds for 6 s and pulls from 9 blocks.", "wind_vector"));
        l.add(n("f5", 5, 4, "f2", 1, 2, PASSIVE, "Skimming", "Sprinting carries you over water and lava without sinking.", ""));
        // Pressure
        l.add(n("p1", 7, 1, "root", 3, 1, STAT, "Compression", "+6% damage from this book's pages, per rank.", ""));
        l.add(n("p2", 7, 2, "p1", 1, 2, SKILL, "Dense Plasma", "Plasma Point bursts a block wider, and twice.", "plasma_point"));
        l.add(n("p3", 8, 3, "p1", 1, 2, PASSIVE, "Ionized", "Foes caught by Plasma Point or Pressure Gust take 20% more from your pages for 5 s.", ""));
        l.add(n("p4", 7, 4, "p2|p3", 1, 3, PASSIVE, "Overpressure", "Every 5th page you cast gives back 50 mana.", ""));
        // the G wheel
        l.add(n("w1", 5, 5, "f2", 1, 2, WHEEL, "Hover Vector", "G wheel: turn your own gravity aside and hang in the air for 3 s.", "vec_hover"));
        l.add(n("w2", 1, 5, "r2", 1, 2, WHEEL, "Reverse Charge", "G wheel: every creature rushing you within 10 blocks has its momentum turned around.", "vec_reverse"));
        l.add(n("w3", 7, 5, "p1", 1, 2, WHEEL, "Air Lance", "G wheel: a spear of compressed air, 20 blocks, through everything in its line.", "vec_lance"));
        // keystones
        l.add(k("k1", 1, 6, "r4|r5", "k2", "Total Reflection", "Every projectile is reflected - but you return only half as much of a blow's force."));
        l.add(k("k2", 3, 6, "r4|m2", "k1", "Absolute Return", "A blow's force comes back in full, and a quarter of its damage with it - but you reflect only half as many projectiles."));
        l.add(k("k3", 5, 6, "p4|f4", "k4", "Eye of the Storm", "Vector Absolute lasts 20 s."));
        l.add(k("k4", 7, 6, "p4|m5", "k3", "Overload", "When Vector Absolute ends, everything it turned aside bursts out: 4 damage per redirect (up to 40) to foes within 8."));
        return l;
    }

    // ------------------------------------------------------------------ the Fuse Board
    private static List<Node> fuse() {
        List<Node> l = new ArrayList<>();
        l.add(n("root", 4, 0, null, 1, 0, ROOT, "Primed Touch", "Your blows arm what they strike. Detonate sets it off - creatures only, never a block. How many you can keep armed and how hard they blow grow with your Fuse level.", "detonate"));
        // Primer
        l.add(n("p1", 1, 1, "root", 3, 1, STAT, "Extra Fuse", "+1 target armed at once, per rank.", ""));
        l.add(n("p2", 0, 2, "p1", 1, 2, PASSIVE, "Hot Touch", "Arrows you shoot arm what they hit.", ""));
        l.add(n("p3", 1, 2, "p1", 3, 1, STAT, "Shaped Charge", "+10% blast damage, per rank.", ""));
        l.add(n("p4", 1, 3, "p3", 1, 2, SKILL, "Delayed Fuse", "Detonate: targets armed 5 s or longer blow 40% harder.", "detonate"));
        l.add(n("p5", 0, 4, "p2|p4", 1, 2, PASSIVE, "Sympathetic", "Chain Fuse can arm 3 more than your limit.", ""));
        // Seeker
        l.add(n("s1", 3, 1, "root", 2, 1, STAT, "Treads", "+20% Heat Seeker speed, per rank.", ""));
        l.add(n("s2", 3, 2, "s1", 1, 2, SKILL, "Armored Shell", "The Heat Seeker survives its first 2 blasts and keeps hunting.", "heat_seeker"));
        l.add(n("s3", 3, 3, "s2", 1, 3, SKILL, "Twin Seekers", "Heat Seeker launches two.", "heat_seeker"));
        l.add(n("s4", 4, 2, "s1", 1, 2, PASSIVE, "Heat Map", "Your blasts deal 25% more to burning or fire-proof foes.", ""));
        // Air
        l.add(n("a1", 5, 1, "root", 3, 1, STAT, "Bubble Count", "+2 Air Bubbles, per rank.", ""));
        l.add(n("a2", 5, 2, "a1", 1, 2, SKILL, "Drifting Mines", "Air Bubbles last 10 s longer, circle you, and dart at foes within 5.", "air_bubble"));
        l.add(n("a3", 6, 2, "a1", 1, 2, SKILL, "Booby Trap", "Prime Item arms up to 3 items and your next 5 arrows.", "prime_item"));
        l.add(n("a4", 5, 3, "a2", 1, 2, SKILL, "Kind Tenant", "Hidden Tenant lasts 60 s, and heals its host 4 when it goes off.", "hidden_tenant"));
        // Blast
        l.add(n("b1", 7, 1, "root", 3, 1, STAT, "Bigger Bang", "+0.5 blocks of blast radius, per rank.", ""));
        l.add(n("b2", 7, 2, "b1", 1, 2, PASSIVE, "Concussion", "Blasts slow foes by 30% for 2 s.", ""));
        l.add(n("b3", 8, 2, "b1", 1, 2, PASSIVE, "Shrapnel", "Each blast throws 4 shards at foes up to 6 blocks away, for 30% of its damage.", ""));
        l.add(n("b4", 7, 3, "b2|b3", 1, 3, PASSIVE, "Afterburn", "Blasts leave a burning ring: 1 damage every half second for 3 s.", ""));
        // the G wheel
        l.add(n("w1", 1, 5, "p2|p4", 1, 2, WHEEL, "Fuse Mode", "G wheel: choose how armed targets blow - on Detonate, 3 s after arming, or when a foe comes near.", "fuse_mode"));
        l.add(n("w2", 3, 5, "s1", 1, 2, WHEEL, "Primed Coin", "G wheel: flick a coin that arms the first creature it strikes - even past your limit.", "fuse_coin"));
        l.add(n("w3", 5, 5, "a1", 1, 2, WHEEL, "Defuse", "G wheel: disarm everything, and take back 8 mana for each.", "fuse_defuse"));
        // keystones
        l.add(k("k1", 1, 6, "p4|p5", "k2", "Quiet Hands", "Armed foes take 30% more from your blasts - but you can arm one fewer."));
        l.add(k("k2", 3, 6, "s2|b2", "k1", "Overkill", "Blasts are 50% wider - but each Detonate costs you 1 health."));
        l.add(k("k3", 5, 6, "a2|b4", "k4", "Endless Loop", "Loop of Ruin replays every blast twice."));
        l.add(k("k4", 7, 6, "b4|s3", "k3", "First Bomb", "Your first Detonate every 20 s blows twice as hard."));
        return l;
    }

    // ------------------------------------------------------------------ the Sightlines
    private static List<Node> foreseen() {
        List<Node> l = new ArrayList<>();
        l.add(n("root", 4, 0, null, 1, 0, ROOT, "Foresight", "A blow aimed at you is seen before it lands: it waits out your warning, and if you are out of its reach by then it misses. The warning and its reach grow with your Sight level.", "read_intent"));
        // Sight
        l.add(n("s1", 1, 1, "root", 3, 1, STAT, "Longer Glimpse", "+0.15 s of warning, per rank.", ""));
        l.add(n("s2", 0, 2, "s1", 2, 1, STAT, "Wide Eyes", "Foresight reaches 6 blocks further, per rank.", ""));
        l.add(n("s3", 1, 2, "s1", 1, 2, PASSIVE, "Crowd Reading", "Every foe that has you as its target is foreseen, not just the nearest.", ""));
        l.add(n("s4", 2, 3, "s3", 1, 2, PASSIVE, "Arrow Sight", "The path of every arrow and bolt flying at you is drawn ahead of it.", ""));
        l.add(n("s5", 1, 4, "s3|s2", 1, 3, PASSIVE, "Boss Omen", "Even a boss's blows can be foreseen.", ""));
        // Evasion
        l.add(n("e1", 3, 1, "root", 3, 1, STAT, "Light Feet", "Step Through carries you 1 block further, per rank.", ""));
        l.add(n("e2", 3, 2, "e1", 1, 2, PASSIVE, "Perfect Dodge", "Each dodge restores 15 mana and gives +20% speed for 1.5 s.", ""));
        l.add(n("e3", 4, 2, "e1", 1, 2, SKILL, "After-Step", "Step Through leaves a False Future double behind for 3 s.", "step_through"));
        l.add(n("e4", 3, 3, "e2", 1, 3, PASSIVE, "Untouchable", "Dodge three blows in a row and, for 4 s, every foreseen blow misses on its own.", ""));
        // Punish
        l.add(n("c1", 5, 1, "root", 3, 1, STAT, "Counterweight", "+15% Punish damage, per rank.", ""));
        l.add(n("c2", 5, 2, "c1", 1, 2, PASSIVE, "Exposed", "A foe whose blow you dodged takes 15% more from you for 4 s.", ""));
        l.add(n("c3", 6, 2, "c1", 1, 2, SKILL, "Inevitable Chain", "Inevitable strikes three points along the future path.", "inevitable"));
        l.add(n("c4", 5, 3, "c2", 1, 2, PASSIVE, "Fated Blow", "Every 4th Punish lands twice as hard.", ""));
        // Fate
        l.add(n("t1", 7, 1, "root", 2, 1, STAT, "Deep Memory", "Snap Back reaches 1 s further into the past, per rank.", ""));
        l.add(n("t2", 7, 2, "t1", 1, 2, SKILL, "Second Glance", "Read Intent marks 2 more foes near the first.", "read_intent"));
        l.add(n("t3", 8, 3, "t2", 1, 2, SKILL, "Seen Twice", "Seen It Before lasts 5 s longer.", "seen_it_before"));
        l.add(n("t4", 7, 3, "t2", 1, 3, SKILL, "Fate Ledger", "Rewrite undoes the last 2 blows, not just one.", "rewrite"));
        // the G wheel
        l.add(n("w1", 5, 5, "c1", 1, 2, WHEEL, "Foretold Strike", "G wheel: mark a spot - 2 s later, the strike you saw lands there.", "fore_strike"));
        l.add(n("w2", 3, 5, "e1", 1, 2, WHEEL, "Borrowed Second", "G wheel: step back to where you stood a second ago.", "fore_second"));
        l.add(n("w3", 7, 5, "t1", 1, 2, WHEEL, "Fate Thread", "G wheel: tie two foes together for 10 s - half of what you deal one, the other takes too.", "fore_thread"));
        // keystones
        l.add(k("k1", 1, 6, "s4|s5", "k2", "Almighty Eye", "Your warning is 0.5 s longer - but blows you did not foresee hurt 15% more."));
        l.add(k("k2", 3, 6, "s5|e4", "k1", "Closed Eyes", "No warnings, no held blows - instead, one blow in four simply misses you."));
        l.add(k("k3", 5, 6, "c4|t4", "k4", "Rewrite Anything", "Rewrite reaches 10 s back instead of 5."));
        l.add(k("k4", 7, 6, "t4|c3", "k3", "Second Draft", "Rewrite heals 30% more, and strikes the attacker twice."));
        return l;
    }

    // ------------------------------------------------------------------ the Bond Tree (Grimoire of the Bound Demon): almost every node changes how something works
    private static List<Node> bound() {
        List<Node> l = new ArrayList<>();
        l.add(n("root", 6, 0, null, 1, 0, ROOT, "The Bound Demon", "A demon lives in your katana. Possession rises as you fight; its power is yours - until it fills, and the demon takes the wheel. Allies near you calm it. Hurt them, and it wakes.", "demon_draw"));
        // Demon
        l.add(n("d1", 1, 1, "root", 1, 1, PASSIVE, "Hungry Edge", "Katana kills feed the demon: +8 Possession and 2 health each.", ""));
        l.add(n("d2", 0, 2, "d1", 1, 2, PASSIVE, "Red Eye", "At 50% Possession or more, a quarter of each katana blow lands again past armour.", ""));
        l.add(n("d3", 2, 2, "d1", 1, 2, PASSIVE, "Whispered Warning", "Before a takeover you get 3 s of warning. Demon Draw used then pushes the demon back by 25%.", ""));
        l.add(n("d4", 0, 3, "d2", 1, 2, PASSIVE, "Shadow Limbs", "At 75% Possession or more, every 4th katana blow is followed by a demon arm's sweep: 60% of it to up to 3 foes in front.", ""));
        l.add(n("d5", 2, 3, "d3", 1, 3, SKILL, "Inner Duel", "Demon's Whisper during the warning wins the fight in your head: no takeover, and 10 s of full Possession's power with full control.", "demons_whisper"));
        l.add(n("d10", 3, 4, "d5", 1, 2, SKILL, "Demon's Tongue", "Demon's Whisper drags every foe within 6 blocks to you.", "demons_whisper"));
        l.add(n("d6", 1, 4, "d4|d5", 1, 2, PASSIVE, "Ride the Frenzy", "In a takeover your wild blows land 50% harder and heal you for a tenth of what they deal.", ""));
        l.add(n("d7", 0, 5, "d6", 1, 2, PASSIVE, "Demon Skin", "In a takeover you take 30% less damage instead of 25% more, and can't be knocked back.", ""));
        l.add(n("d8", 2, 5, "d6", 1, 2, PASSIVE, "Feast", "Each kill in a takeover makes it last 2 s longer and heals you 4.", ""));
        l.add(n("d9", 1, 6, "d7|d8", 1, 3, PASSIVE, "Afterglow", "When a takeover ends you keep the demon's strength for 6 s more - in your own hands.", ""));
        // Blade
        l.add(n("k1", 4, 1, "root", 1, 1, SKILL, "Draw Cut", "Demon Draw cuts as the blade comes out: an arc in front of you.", "demon_draw"));
        l.add(n("k2", 3, 2, "k1", 1, 2, SKILL, "Smoke Trail", "Cursed Edge's black cut hangs in the air for 3 s and bites whatever crosses it.", "cursed_edge"));
        l.add(n("k3", 5, 2, "k1", 1, 2, SKILL, "Double Edge", "Within 2 s of Cursed Edge, cast it again - free - to dash back the way you came.", "cursed_edge"));
        l.add(n("k10", 3, 3, "k2", 1, 2, SKILL, "Severing Wind", "A flying cut leaves the end of Cursed Edge and runs on 10 blocks.", "cursed_edge"));
        l.add(n("k4", 4, 3, "k2|k3", 1, 2, PASSIVE, "Parry", "Tap sneak with the katana drawn just as a blow lands: it doesn't, and its striker staggers.", ""));
        l.add(n("k5", 5, 4, "k4", 1, 2, PASSIVE, "Iai Stance", "Stand still with the katana drawn for 2 s: your next Cursed Edge strikes three times as hard.", ""));
        l.add(n("k9", 5, 5, "k5", 1, 2, PASSIVE, "Blood on Steel", "Each katana hit stores a drop (up to 10); Cursed Edge spends them, +10% each.", ""));
        l.add(n("k6", 4, 5, "k4", 1, 2, SKILL, "Flowing Flurry", "Demon Flurry follows you as you move and lasts twice as long.", "demon_flurry"));
        l.add(n("k7", 4, 6, "k6", 1, 2, SKILL, "Ghost Blades", "Demon Flurry leaves three spectral blades circling you, still cutting, for 3 s.", "demon_flurry"));
        l.add(n("k8", 5, 7, "k7|k9", 1, 3, PASSIVE, "Executioner's Draw", "Katana blows on foes under 25% health cut through what's left: +40% of their missing health (up to 20).", ""));
        // Bond
        l.add(n("b1", 8, 1, "root", 1, 1, SKILL, "Shared Burden", "Bond Guard lasts 20 s, and you take only 30% of your ally's hurt instead of half.", "bond_guard"));
        l.add(n("b2", 7, 2, "b1", 1, 2, PASSIVE, "A Voice You Know", "In a takeover, an ally within 4 blocks makes it end twice as fast - and your wild blows pass them by.", ""));
        l.add(n("b3", 9, 2, "b1", 1, 2, SKILL, "Vow of Return", "If an ally was hurt in the last 3 s, Cursed Edge dashes straight at whatever hurt them.", "cursed_edge"));
        l.add(n("b4", 8, 3, "b2|b3", 1, 2, PASSIVE, "Shield of the Elder", "An ally near you who falls below 30% health is bound to you by Bond Guard at once (every 30 s).", ""));
        l.add(n("b5", 7, 4, "b4", 1, 2, PASSIVE, "Fight for Them", "Each ally within 10 blocks (up to 4): katana blows +10%, and the demon fills 10% slower.", ""));
        l.add(n("b6", 9, 4, "b4", 1, 2, PASSIVE, "Grudge", "Whatever kills an ally near you is marked a Sinner for a minute and takes double from you.", ""));
        l.add(n("b8", 7, 5, "b5", 1, 2, PASSIVE, "Carried Hope", "The ally bound by Bond Guard hits 20% harder.", ""));
        l.add(n("b7", 9, 5, "b6", 1, 2, PASSIVE, "Family Meal", "Killing a Sinner heals every ally within 8 blocks for 4.", ""));
        l.add(n("b9", 8, 6, "b7|b8", 1, 3, PASSIVE, "Never Again", "The awakening that saves a dying ally comes back twice as fast, and heals them to half.", ""));
        l.add(n("b10", 8, 7, "b9", 1, 3, PASSIVE, "Last Stand Together", "A killing blow with an ally beside you leaves you at 1 health, and you both can't be hurt for 3 s (every 3 min).", ""));
        // Salt
        l.add(n("s1", 11, 1, "root", 1, 2, SKILL, "Salt in the Veins", "Salt Lance is yours before your first awakening, and pierces everything in its line.", "salt_lance"));
        l.add(n("s2", 10, 2, "s1", 1, 2, PASSIVE, "Crystallize", "Three salt hits crust a foe: 60% slower for 3 s, and your next katana blow shatters it for 10 more.", ""));
        l.add(n("s4", 12, 2, "s1", 1, 2, PASSIVE, "Trumpet Call", "The awakening begins with a ringing blast: foes within 10 are thrown back and salted.", ""));
        l.add(n("s3", 10, 3, "s2", 1, 2, PASSIVE, "Pillar of Salt", "A crusted foe that dies bursts a second later: 6 to everything within 3 blocks.", ""));
        l.add(n("s5", 12, 3, "s4", 1, 2, PASSIVE, "Judgment Sight", "Awakened, whatever hurts you or an ally is marked a Sinner on the spot.", ""));
        l.add(n("s6", 11, 4, "s3|s5", 1, 2, PASSIVE, "Wing Guard", "Awakened, your wing curls round you: blows from behind never land.", ""));
        l.add(n("s7", 10, 5, "s6", 1, 2, PASSIVE, "Salt Rain", "Awakened, salt spears fall on Sinners twice as often.", ""));
        l.add(n("s8", 12, 5, "s6", 1, 2, PASSIVE, "Longer Wings", "The awakening lasts 30 s instead of 20.", ""));
        l.add(n("s9", 11, 6, "s7|s8", 1, 2, PASSIVE, "Seraph's Mercy", "Awakened, allies within 8 blocks heal 1 every 2 s.", ""));
        l.add(n("s10", 11, 7, "s9", 1, 3, PASSIVE, "Fallen Seraph", "Awaken in a takeover and the demon turns judge: still wild, but it never strikes an ally, every kill turns to salt, and it hits twice as hard.", ""));
        // the G wheel
        l.add(n("w1", 6, 2, "b1", 1, 2, WHEEL, "Call to Family", "G wheel: your tamed beasts come to you, and every ally within 16 gains speed and 4 absorption.", "bd_family"));
        l.add(n("w2", 6, 3, "s1|s2", 1, 2, WHEEL, "Salt Wall", "G wheel: a wall of salt for 4 s - arrows and bolts die in it, foes crossing it are salted.", "bd_saltwall"));
        l.add(n("w3", 6, 4, "s4|s8", 1, 2, WHEEL, "Wing Dash", "G wheel (after your first awakening): one beat of the wing - 10 blocks, through foes.", "bd_wingdash"));
        // keystones
        l.add(k("K1", 0, 8, "d9|d6", "K2", "Symbiosis", "The demon is your friend: Possession stops at 90% and you never lose control - but Demon's Whisper gives half."));
        l.add(k("K2", 2, 8, "d9|d8", "K1", "Surrender", "Takeovers last 12 s and hit twice as hard - and the demon strikes what you look at."));
        l.add(k("K5", 5, 8, "b9|k8", "K6", "Avenger", "Sinners stay marked until they fall, and take 50% more from you."));
        l.add(k("K6", 7, 8, "b9|b10", "K5", "Guardian", "Bond Guard binds up to three allies at once."));
        l.add(k("K3", 10, 8, "s9|s10", "K4", "One Wing", "The awakening keeps its one branched wing and gives no flight - but your blows land 40% harder in it."));
        l.add(k("K4", 12, 8, "s9|s10", "K3", "Black Wings", "The true awakening: a pair of ragged black wings. You fly, and the wings turn six blows aside."));
        return l;
    }

    public static Node node(int book, String id) {
        if (book < 0 || book >= TREES.size()) return null;
        for (Node x : TREES.get(book)) if (x.id().equals(id)) return x;
        return null;
    }

    // ------------------------------------------------------------------ which book
    public static int kind(ItemStack b) {
        if (b == null || b.isEmpty()) return -1;
        if (b.is(AddonItemRegistry.VECTOR_GRIMOIRE.get())) return VECTOR;
        if (b.is(AddonItemRegistry.FUSE_GRIMOIRE.get())) return FUSE;
        if (b.is(AddonItemRegistry.FORESEEN_GRIMOIRE.get())) return FORESEEN;
        if (b.is(AddonItemRegistry.BOUND_GRIMOIRE.get())) return BOUND;
        return -1;
    }
    public static ItemStack book(Player p) { ItemStack b = Utils.getPlayerSpellbookStack(p); return b == null ? ItemStack.EMPTY : b; }
    public static int kind(Player p) { return kind(book(p)); }

    // ------------------------------------------------------------------ what a tree holds
    static CompoundTag data(ItemStack b) { return b.getOrCreateTagElement("BT"); }

    public static int rank(ItemStack b, String id) {
        if (b == null || b.isEmpty() || !b.hasTag()) return "root".equals(id) ? 1 : 0;
        if ("root".equals(id)) return 1;
        return b.getTag().getCompound("BT").getCompound("r").getInt(id);
    }
    public static int rank(Player p, String id) { return rank(book(p), id); }
    public static boolean has(Player p, String id) { return rank(p, id) > 0; }

    public static float xp(ItemStack b) { return b == null || !b.hasTag() ? 0 : b.getTag().getCompound("BT").getFloat("xp"); }

    /** Growth level: the n-th level takes 12 n growth (0 .. 30). */
    public static int level(float xp) {
        int l = 0;
        float need = 12;
        while (l < MAX_LEVEL && xp >= need) { xp -= need; l++; need = 12 * (l + 1); }
        return l;
    }
    public static int level(ItemStack b) { return level(xp(b)); }
    public static int level(Player p) { return level(book(p)); }
    /** Growth still wanted for the next level, and how much the whole level takes. */
    public static float[] toNext(float xp) {
        int l = 0; float need = 12;
        while (l < MAX_LEVEL && xp >= need) { xp -= need; l++; need = 12 * (l + 1); }
        return l >= MAX_LEVEL ? new float[]{0, 1} : new float[]{xp, need};
    }

    /** Points: the growth level and two a mastery level - and the Bond Tree, the biggest, twice the growth level. */
    public static int points(ItemStack b) { return level(b) * (kind(b) == BOUND ? 2 : 1) + 2 * GrimoireMastery.level(b); }
    public static int spent(ItemStack b) {
        int k = kind(b), s = 0;
        if (k < 0) return 0;
        for (Node x : TREES.get(k)) if (!"root".equals(x.id())) s += rank(b, x.id()) * x.cost();
        return s;
    }

    /** Why a node can't take another rank now, or null if it can. */
    public static String blocked(ItemStack b, Node x) {
        int r = rank(b, x.id());
        if (x.kind() == ROOT) return "Always yours";
        if (r >= x.ranks()) return x.ranks() > 1 ? "All ranks learned" : "Learned";
        boolean ok = x.req().length == 0;
        for (String q : x.req()) if (rank(b, q) > 0) { ok = true; break; }
        if (!ok) {
            StringBuilder s = new StringBuilder("Needs ");
            for (int i = 0; i < x.req().length; i++) { Node q = node(kind(b), x.req()[i]); s.append(i > 0 ? " or " : "").append(q == null ? x.req()[i] : q.name()); }
            return s.toString();
        }
        if (x.excl() != null && rank(b, x.excl()) > 0) return "You took " + node(kind(b), x.excl()).name();
        if (points(b) - spent(b) < x.cost()) return "Needs " + x.cost() + (x.cost() == 1 ? " point" : " points");
        return null;
    }

    // ------------------------------------------------------------------ server: growth, learning, unlearning
    private static final Map<UUID, Long> RESPEC = new HashMap<>();

    /** The book grows: amount of growth, if this book is the one worn. */
    public static void grow(ServerPlayer p, int kind, float amount) {
        ItemStack b = book(p);
        if (kind(b) != kind || amount <= 0) return;
        int before = level(b);
        CompoundTag d = data(b);
        d.putFloat("xp", Math.min(1e7f, d.getFloat("xp") + amount));
        int after = level(b);
        if (after > before) {
            p.displayClientMessage(Component.literal("✦ " + TREE[kind] + ": level " + after + " - a point to spend (G, then the middle)").withStyle(ChatFormatting.AQUA), false);
            FxUtil.play(p.serverLevel(), p.position(), "tree_learn", 0.9f, 1.2f);
            sync(p, false);
        } else if (p.tickCount % 10 == 0) sync(p, false);
    }

    public static void learn(ServerPlayer p, String id) {
        ItemStack b = book(p);
        int k = kind(b);
        Node x = node(k, id);
        if (x == null) return;
        String why = blocked(b, x);
        if (why != null) { p.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.RED), true); return; }
        CompoundTag r = data(b).getCompound("r");
        r.putInt(id, r.getInt(id) + 1);
        data(b).put("r", r);
        FxUtil.play(p.serverLevel(), p.position(), "tree_learn", 0.8f, 1f + 0.1f * r.getInt(id));
        sync(p, false);
    }

    public static void respec(ServerPlayer p) {
        ItemStack b = book(p);
        if (kind(b) < 0) return;
        long now = p.level().getGameTime(), until = RESPEC.getOrDefault(p.getUUID(), 0L);
        if (now < until) { p.displayClientMessage(Component.literal("You can unlearn again in " + (until - now + 19) / 20 + " s").withStyle(ChatFormatting.RED), true); return; }
        data(b).put("r", new CompoundTag());
        RESPEC.put(p.getUUID(), now + RESPEC_TICKS);
        p.displayClientMessage(Component.literal("Every point is back in your hand.").withStyle(ChatFormatting.AQUA), true);
        sync(p, false);
    }

    // ------------------------------------------------------------------ the G wheel
    private static final Map<UUID, long[]> WHEEL_CD = new HashMap<>();
    /** The wheel's four abilities: slot 0 is always there, 1-3 need their wheel node. */
    public static final String[][] WHEEL_IDS = {{"", "w1", "w2", "w3"}, {"", "w1", "w2", "w3"}, {"", "w2", "w1", "w3"}, {"", "w1", "w2", "w3"}};
    public static final String[][] WHEEL_NAMES = {{"Stance Shift", "Hover Vector", "Reverse Charge", "Air Lance"},
            {"Detonate All", "Fuse Mode", "Primed Coin", "Defuse"}, {"Glimpse", "Borrowed Second", "Foretold Strike", "Fate Thread"},
            {"Restrain / Release", "Call to Family", "Salt Wall", "Wing Dash"}};
    public static final String[][] WHEEL_ICONS = {{"vec_stance", "vec_hover", "vec_reverse", "vec_lance"},
            {"detonate", "fuse_mode", "fuse_coin", "fuse_defuse"}, {"fore_glimpse", "fore_second", "fore_strike", "fore_thread"},
            {"bd_restrain", "bd_family", "bd_saltwall", "bd_wingdash"}};
    public static final String[][] WHEEL_DESC = {
            {"Switch stance: Reflect (projectiles), Return (blows), Flow (no fall damage, faster).", "Hang in the air for 3 s.", "Turn the momentum of everything rushing you.", "A spear of compressed air through everything in its line."},
            {"Set off everything armed - no mana.", "Choose how armed targets blow: on Detonate, on a 3 s timer, or when a foe comes near.", "A coin that arms the first creature it strikes.", "Disarm everything; 8 mana back for each."},
            {"For 5 s, see every nearby foe's next blow, and foresee them all.", "Step back to where you stood a second ago.", "Mark a spot: 2 s later the strike lands there.", "Tie two foes together for 10 s."},
            {"Balanced, then Restrain (the demon fills slowly and fades), then Release (it fills fast, and gives more).", "Your beasts come to you; allies within 16 gain speed and 4 absorption.", "A wall of salt for 4 s: arrows die in it, foes crossing are salted.", "One beat of the wing: 10 blocks, through foes."}};
    public static final int[][] WHEEL_COOLDOWN = {{20, 300, 240, 160}, {40, 10, 120, 200}, {400, 200, 160, 300}, {10, 600, 300, 160}};

    public static boolean wheelOpen(Player p, int slot) {
        int k = kind(p);
        if (k < 0 || slot < 0 || slot > 3) return false;
        return slot == 0 || has(p, WHEEL_IDS[k][slot]);
    }

    public static int wheelCd(Player p, int slot) {
        long[] cd = WHEEL_CD.get(p.getUUID());
        return cd == null ? 0 : (int) Math.max(0, cd[slot] - p.level().getGameTime());
    }

    public static void useWheel(ServerPlayer p, int slot) {
        int k = kind(p);
        if (!wheelOpen(p, slot)) return;
        if (wheelCd(p, slot) > 0) { p.displayClientMessage(Component.literal(WHEEL_NAMES[k][slot] + " is ready in " + (wheelCd(p, slot) + 19) / 20 + " s").withStyle(ChatFormatting.GRAY), true); return; }
        boolean done = switch (k) {
            case VECTOR -> VectorFx.wheel(p, slot);
            case FUSE -> FuseFx.wheel(p, slot);
            case BOUND -> BoundFx.wheel(p, slot);
            default -> ForeseenFx.wheel(p, slot);
        };
        if (done) WHEEL_CD.computeIfAbsent(p.getUUID(), u -> new long[4])[slot] = p.level().getGameTime() + WHEEL_COOLDOWN[k][slot];
        sync(p, false);
    }

    // ------------------------------------------------------------------ sync
    public static void sync(ServerPlayer p, boolean open) {
        ItemStack b = book(p);
        int k = kind(b);
        StringBuilder s = new StringBuilder();
        int[] cd = new int[4];
        int[] extra = new int[0];
        if (k >= 0) {
            for (Node x : TREES.get(k)) { int r = rank(b, x.id()); if (r > 0 && x.kind() != ROOT) s.append(x.id()).append(':').append(r).append(','); }
            for (int i = 0; i < 4; i++) cd[i] = wheelCd(p, i);
            extra = switch (k) { case VECTOR -> VectorFx.extra(p); case FUSE -> FuseFx.extra(p); case BOUND -> BoundFx.extra(p); default -> ForeseenFx.extra(p); };
        }
        long until = RESPEC.getOrDefault(p.getUUID(), 0L);
        AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new BookTreePackets.Sync(k, xp(b), points(b), s.toString(), cd, extra,
                (int) Math.max(0, until - p.level().getGameTime()), open));
    }

    /** Client asks: 0 open the tree, 1 learn (id), 2 unlearn all, 3 use wheel slot (arg). */
    public static void act(ServerPlayer p, int action, int arg, String id) {
        if (kind(p) < 0) return;
        switch (action) {
            case 0 -> sync(p, true);
            case 1 -> learn(p, id);
            case 2 -> respec(p);
            case 3 -> useWheel(p, arg);
            default -> {}
        }
    }

    /** Test hooks. */
    public static void testSet(ItemStack b, String id, int r) { CompoundTag t = data(b).getCompound("r"); t.putInt(id, r); data(b).put("r", t); }
    public static void testXp(ItemStack b, float xp) { data(b).putFloat("xp", xp); }
    public static void testClearCd(Player p) { WHEEL_CD.remove(p.getUUID()); RESPEC.remove(p.getUUID()); }
}
