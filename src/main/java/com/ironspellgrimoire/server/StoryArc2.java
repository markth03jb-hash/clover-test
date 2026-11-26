package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.StoryArc1.give;
import static com.ironspellgrimoire.server.StoryArc1.tag;
import static com.ironspellgrimoire.server.StoryArc1.title;

import com.ironspellgrimoire.entity.MageEnemy;
import com.ironspellgrimoire.entity.MagistrateVey;
import com.ironspellgrimoire.entity.Rival;
import com.ironspellgrimoire.entity.StoryNpc;
import com.ironspellgrimoire.entity.TamsinDuelist;
import com.ironspellgrimoire.entity.Warden;
import com.ironspellgrimoire.item.AshenPageItem;
import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ASHFALL, Arc II: The Proving. Sallow's list leads to Cindermarch and its Ember Trials, and to the Magistrate who
 * keeps what the losers leave behind.
 * <ol start="0">
 *   <li><b>The Walled City:</b> three qualifying bouts on the sand; Tamsin Reed in the waiting cells.</li>
 *   <li><b>First Blood:</b> Brannoch Tor, under Trials rules.</li>
 *   <li><b>The Friend in the Bracket:</b> Tamsin. She yields; the Wardens carry her out; her cell is empty that night.</li>
 *   <li><b>Proof:</b> the catacomb under the arena - its Wardens, and the three pages of the harvest ledger.</li>
 *   <li><b>Semifinal:</b> the Bellmar twins.</li>
 *   <li><b>The Final:</b> Vey's offer - the party votes to win it or throw it - and Seren Vale.</li>
 *   <li><b>The Vault:</b> Magistrate Vey among his stolen grimoires. Brand II.</li>
 *   <li>Arc II complete.</li>
 * </ol>
 * Bouts against the rivals run under Trials rules: no records, no keepsakes, no deals - the story pays instead.
 */
public final class StoryArc2 {
    private StoryArc2() {}

    public static final String ARC = "ARC II - THE PROVING";
    public static final String[] TITLES = {"The Walled City", "First Blood", "The Friend in the Bracket", "Proof", "Semifinal", "The Final", "The Vault", "The Proving"};
    static final String[] TEXT = {
            "Sallow's list led to Cindermarch, a walled city of black stone and gold, and its Ember Trials. Winners get coin. Corvin says the losers never come home. He's entered you to find out why.",
            "You're in the bracket. Your first bout is against a face you know - Brannoch Tor, the Siege-Lord, fighting under Trials rules.",
            "The second round. Your opponent is Tamsin Reed, the girl from the cells - the one from a burnt village like yours.",
            "Tamsin's cell is empty. Her grimoire's sigil is burned into the Magistrate's ledger. Somewhere under the arena, the ledger is kept.",
            "Proof, in his own hand. Corvin wants you to keep winning - right up to the door of the vault. The semifinal is against the Bellmar twins.",
            "The final. Seren Vale waits on the sand - and Magistrate Vey wants a word before the bell.",
            "The vault under Cindermarch: shelves of stolen grimoires, and the Magistrate who shelved them.",
            "Magistrate Vey is dead. The vault is open, and the city knows what its Trials were for. Tamsin's grimoire was on shelf eleven.\n\nARC III - THE HOLLOW WAR comes in a later update."};

    // ================================================================== the scenes
    static List<String[]> scene(String id) {
        return switch (id) {
            case "trials_arrive" -> Story.lines(
                    "narrator", "Cindermarch. Walls of black stone, gates banded in gold, and over all of it the roar of a crowd that has paid to watch someone lose.",
                    "warden", "Entrants to the Ember Trials, below. Three bouts to qualify. Win them, and the Magistrate will see you on the sand.",
                    "corvin", "Listen to me. Win every bout. Whatever happens, don't lose here.",
                    "corvin", "I've counted the names on Sallow's list against the Trials' records. Every one of them lost. Not one of them left the city.");
            case "tamsin_meet" -> Story.lines(
                    "tamsin", "Oh! You're new. Sorry - I'm Tamsin. Tamsin Reed. Second round, if I last that long.",
                    "tamsin", "You've got the look. Ash in the hair that never washes out. Where are you from?",
                    "tamsin", "...Emberfall. Mine was Larkhollow. A star came down on us too, two summers back. I was the only one who could still walk.",
                    "tamsin", "The coin from the Trials could buy a whole new village. That's the plan. Win, go home, build it again.",
                    "tamsin", "If we meet on the sand - no hard feelings, yes? Promise me.");
            case "brannoch_after" -> Story.lines(
                    "corvin", "Good. One down.",
                    "corvin", "Your next opponent is posted. Read it.",
                    "narrator", "Second round: Tamsin Reed of Larkhollow.");
            case "tamsin_taken" -> Story.lines(
                    "vey", "The loser forfeits to the Trials. Wardens - see her to the healers.",
                    "narrator", "Two Wardens lift Tamsin between them. She's still smiling at you over her shoulder as they carry her through the north door.",
                    "corvin", "Healers. There are no healers in Cindermarch. I asked.");
            case "cell_empty" -> Story.lines(
                    "narrator", "Her cell is empty. The straw has been swept. Her satchel is gone.",
                    "narrator", "Scratched into the stone by the door, small and careful: a little house, and a name - Larkhollow.",
                    "corvin", "I looked at the posted records while you slept. Her name is struck through. Under it, in gold ink: 'Forfeit. Grimoire to vault.'",
                    "corvin", "There's a catacomb under the sand. That's where the ledgers are kept. Tonight, we go and read them.");
            case "proof" -> Story.lines(
                    "corvin", "Forty pages of names. Sallow sent them here - the branded, the ones worth the most - and Vey sold what was left to someone called the Choir.",
                    "corvin", "The losers don't come home because the losers are the harvest. That's what the Trials are for.",
                    "corvin", "We need him in front of us. Keep winning. Win your way right up to his vault door.");
            case "twins_after" -> Story.lines(
                    "narrator", "The crowd is on its feet. You can hear your name in it.",
                    "corvin", "One more bout. Seren Vale. Then the vault, one way or another.");
            case "vey_offer" -> Story.lines(
                    "vey", "A word, before the bell. You've been reading my books, I think. Rude, but ambitious. I like ambition.",
                    "vey", "Win today and you're my champion. Champions sign the ledger. Champions get a key to the vault - and a share of what's in it.",
                    "vey", "Lose, and you'll see the vault anyway. From the other side of the binding table.",
                    "corvin", "If you win, he lets you in. If you throw it, his Wardens carry you in - in front of the whole city. Either way, we get our door.",
                    "corvin", "Your call. All of you.");
            case "champion" -> Story.lines(
                    "narrator", "Seren Vale goes down on one knee in the sand, and the crowd comes apart around you.",
                    "vey", "The champion of the Ember Trials! Come - come and sign. The vault is waiting.");
            case "throw_final" -> Story.lines(
                    "narrator", "You lower your grimoire. Seren stares at you, and then at the north door.",
                    "vey", "The champion forfeits! The Trials claim the loser. Wardens -",
                    "narrator", "Hands on your arms. Chains. The north door. Above you, through a grate in the vault's ceiling, a thousand faces lean in to watch.");
            case "vault_win" -> Story.lines(
                    "vey", "Shelf after shelf. A fire-singer from the coast. A boy from Harrowmere. Your friend from Larkhollow - eleven, third from the left.",
                    "vey", "Sign, champion, and half of this is yours. Or don't, and you'll be shelf twelve.",
                    "corvin", "I'll take shelf twelve over your pen, Magistrate.");
            case "vault_throw" -> Story.lines(
                    "vey", "On the table. Gently. The branded ones burn so warm.",
                    "narrator", "Your Brand wakes before the knife can touch you. The chains go red, then soft, then nothing.",
                    "vey", "...Well. The city can watch, then. Let them see what a champion is worth.");
            case "tamsin_grimoire" -> Story.lines(
                    "narrator", "Shelf eleven. Third from the left. A small green grimoire with a feather tied into its binding.",
                    "narrator", "It's warm. It's empty. Every page has been cut out.",
                    "corvin", "...I'm sorry. I should have known what 'healers' meant. I should have taken her out of the cells myself.");
            case "brand2" -> Story.lines(
                    "narrator", "The Brand doesn't ask. It opens. The heat goes deeper this time - past your arm, into your ribs, into the space behind your eyes.",
                    "corvin", "That's the second stage. It'll burn hotter - and it'll take from you while it burns. Your own blood, every second.",
                    "corvin", "Next time, I teach you to hold it without bleeding out. If there's a next time.",
                    "narrator", "ARC II - THE PROVING - COMPLETE.");
            default -> Story.lines("narrator", "...");
        };
    }

    static void play(MinecraftServer s, Story.Party pt, String id, String... choices) { Story.talk(s, pt, id, scene(id), choices); }

    static Vec3 pos(Story.Party pt, int place, double[] a) { return StoryStage.at(pt.id, place, a[0], a[1], a[2]); }

    // ================================================================== starting, chapters
    static void begin(MinecraftServer s, Story.Party pt) {
        StoryArc1.clearScenes(s, pt);
        pt.arc = 2; pt.ch = 0; pt.step = 0; pt.n = new CompoundTag(); pt.talk = null;
        pt.note("ARC II - THE PROVING began.");
        for (ServerPlayer p : Story.online(s, pt)) {
            give(p, new ItemStack(AddonItemRegistry.STORY_JOURNAL.get()), AddonItemRegistry.STORY_JOURNAL.get());
            StoryStage.enter(p, pt, StoryStage.ARENA, StoryStage.spawn(pt.id, StoryStage.ARENA), 180);
            title(p, "THE PROVING", "Chapter 1 - " + TITLES[0]);
        }
        pt.target = false;
        pt.dirty = true;
    }

    static void toChapter(MinecraftServer s, Story.Party pt, int ch) {
        int was = pt.ch;
        StoryArc1.clearScenes(s, pt);
        pt.arc = 2; pt.ch = ch; pt.step = 0; pt.n = new CompoundTag(); pt.talk = null; pt.target = false;
        if (ch >= 7) Story.raiseBrand(s, pt, 2);
        if (ch != was && ch > 0) {
            for (ServerPlayer p : Story.online(s, pt)) title(p, ch >= 7 ? "ARC II COMPLETE" : "Chapter " + (ch + 1), ch >= 7 ? "The Proving" : TITLES[ch]);
            pt.note(ch >= 7 ? "ARC II - THE PROVING - complete." : "Chapter " + (ch + 1) + ": " + TITLES[ch] + ".");
        }
        pt.dirty = true;
    }

    static void restartChapter(MinecraftServer s, Story.Party pt) {
        StoryArc1.clearScenes(s, pt);
        int keepQual = pt.count("qual");
        pt.step = 0; pt.n = new CompoundTag(); pt.talk = null;
        if (pt.ch == 0 && keepQual > 0) { pt.n.putInt("qual", keepQual); pt.step = 1; }
        pt.dirty = true;
    }

    // ================================================================== the tick
    static void tick(MinecraftServer s, Story.Party pt, List<ServerPlayer> on) {
        if (pt.ch < 0 || pt.ch >= 7) return;
        ServerLevel sl = StoryStage.rift(s);
        if (sl == null) return;
        long now = s.overworld().getGameTime();
        int place = pt.ch == 6 ? StoryStage.VAULT : StoryStage.ARENA;
        List<ServerPlayer> in = StoryStage.inside(s, pt, place);
        if (in.isEmpty()) {   // nobody's here: after a while, what was under way goes back to its start (what's done stays done)
            pt.add("away", 1);
            if (pt.count("away") > 30) { abandon(s, sl, pt); pt.n.putInt("away", 0); }
            return;
        }
        pt.n.putInt("away", 0);
        if (place == StoryStage.ARENA) {
            StoryArc1.ensureNpc(sl, pt, AddonEntities.STORY_CORVIN.get(), StoryStage.ARENA, StoryStage2.CORVIN_GATE, 180);
            if (pt.ch == 0 || pt.ch == 1 || pt.ch == 2 && pt.step < 2) StoryArc1.ensureNpc(sl, pt, AddonEntities.STORY_TAMSIN.get(), StoryStage.ARENA, StoryStage2.TAMSIN_CELL, 270);
            if (pt.ch == 5 && pt.step <= 1) StoryArc1.ensureNpc(sl, pt, AddonEntities.STORY_VEY.get(), StoryStage.ARENA, StoryStage2.BALCONY, 0);
        } else StoryArc1.ensureNpc(sl, pt, AddonEntities.STORY_CORVIN.get(), StoryStage.VAULT, new double[]{2, 0, 2}, 0);
        if (Story.talking(pt)) return;
        switch (pt.ch) {
            case 0 -> walledCity(s, sl, pt, in, now);
            case 1 -> bout(s, sl, pt, in, Rivals.BRANNOCH, 1, "brannoch_after");
            case 2 -> friend(s, sl, pt, in, now);
            case 3 -> proof(s, sl, pt, in);
            case 4 -> bout(s, sl, pt, in, Rivals.TWINS, 2, "twins_after");
            case 5 -> finalBout(s, sl, pt, in, now);
            case 6 -> vault(s, sl, pt, in);
            default -> {}
        }
    }

    /** Everyone left: bouts and fights under way stop (the next visit starts them again); done is done. */
    static void abandon(MinecraftServer s, ServerLevel sl, Story.Party pt) {
        for (int place : new int[]{StoryStage.ARENA, StoryStage.VAULT}) {
            Vec3 c = StoryStage.centre(pt.id, place);
            for (Entity e : sl.getEntitiesOfClass(Entity.class, new AABB(c, c).inflate(60, 30, 60), e -> e.getPersistentData().getInt("isg_bout") == pt.id)) e.discard();
        }
        switch (pt.ch) {
            case 0 -> { if (pt.step == 2) pt.step = 1; }
            case 1, 4 -> { if (pt.step == 1) pt.step = 0; }
            case 2 -> { if (pt.step == 1) pt.step = 0; }
            case 5 -> { if (pt.step == 2) pt.step = 1; }
            case 6 -> { if (pt.step == 1) pt.n.putBoolean("veyUp", false); }
            default -> {}
        }
        pt.dirty = true;
    }

    static boolean onPad(Story.Party pt, List<ServerPlayer> in) {
        Vec3 c = pos(pt, StoryStage.ARENA, StoryStage2.BOUT_PAD);
        for (ServerPlayer p : in) if (Math.hypot(p.getX() - c.x, p.getZ() - c.z) < 3 && Math.abs(p.getY() - c.y) < 2.5) return true;
        return false;
    }

    static void padFx(ServerLevel sl, Story.Party pt, MinecraftServer s) {
        if (s.getTickCount() % 20 == 0) FxSend.ring(sl, Fx.GOLD, pos(pt, StoryStage.ARENA, StoryStage2.BOUT_PAD).add(0, 0.05, 0), 2.6f, 2.7f, 0.06f, 22);
    }

    static void bell(MinecraftServer s, Story.Party pt, String who) {
        Story.tellAll(s, pt, Component.literal("THE BELL  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD).append(Component.literal(who).withStyle(ChatFormatting.YELLOW)));
        ServerLevel sl = StoryStage.rift(s);
        if (sl != null) sl.playSound(null, net.minecraft.core.BlockPos.containing(pos(pt, StoryStage.ARENA, StoryStage2.BOUT_PAD)), net.minecraft.sounds.SoundEvents.BELL_BLOCK, net.minecraft.sounds.SoundSource.HOSTILE, 3f, 0.8f);
    }

    static void tagBout(Entity e, Story.Party pt, int place) { tag(e, pt, place); e.getPersistentData().putInt("isg_bout", pt.id); }

    /** +60% health for each mage beyond the first in the place, as the ladder's bosses. */
    static final UUID GROUP = UUID.fromString("7e400000-0000-4000-8000-000000000011");
    static void scale(LivingEntity m, int players) {
        if (players <= 1) return;
        var hp = m.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && hp.getModifier(GROUP) == null) hp.addPermanentModifier(new AttributeModifier(GROUP, "ASHFALL: the party", 0.6 * (players - 1), AttributeModifier.Operation.MULTIPLY_BASE));
        m.setHealth(m.getMaxHealth());
    }

    // ---- chapter 1: the qualifiers, Tamsin
    static void walledCity(MinecraftServer s, ServerLevel sl, Story.Party pt, List<ServerPlayer> in, long now) {
        switch (pt.step) {
            case 0 -> { pt.step = 1; play(s, pt, "trials_arrive"); }
            case 1 -> { padFx(sl, pt, s); if (onPad(pt, in)) { pt.step = 2; qualifier(s, sl, pt, in.size()); } }
            case 2 -> {
                List<Mob> left = bouts(sl, pt);
                if (!left.isEmpty()) return;
                if (pt.n.getLong("next") == 0) {
                    pt.add("qual", 1);
                    if (pt.count("qual") >= 3) { pt.step = 3; Story.tellAll(s, pt, Component.literal("The crowd's noise changes. You're in the bracket.").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC)); return; }
                    pt.n.putLong("next", now + 60);
                    return;
                }
                if (now >= pt.n.getLong("next")) { pt.n.putLong("next", 0); qualifier(s, sl, pt, in.size()); }
            }
            case 3 -> {
                Vec3 c = pos(pt, StoryStage.ARENA, StoryStage2.TAMSIN_CELL);
                for (ServerPlayer p : in) if (p.position().distanceTo(c) < 4.5) { pt.step = 4; play(s, pt, "tamsin_meet"); return; }
            }
            default -> {}
        }
    }

    static void qualifier(MinecraftServer s, ServerLevel sl, Story.Party pt, int players) {
        bell(s, pt, "Qualifying bout " + (pt.count("qual") + 1) + " of 3.");
        int n = 2 + (players - 1) + pt.count("qual");
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            MageEnemy m = AddonEntities.MAGE.get().create(sl);
            if (m == null) continue;
            double a = (i / (double) n) * Math.PI - Math.PI;   // across the north half of the sand
            Vec3 at = StoryStage.at(pt.id, StoryStage.ARENA, Math.cos(a) * 11, 0, Math.sin(a) * 11);
            m.moveTo(at.x, at.y, at.z, r.nextFloat() * 360, 0);
            m.setPersistenceRequired();
            tagBout(m, pt, StoryStage.ARENA);
            sl.addFreshEntity(m);
            FxSend.ring(sl, Fx.GOLD, at.add(0, 0.1, 0), 0.2f, 1.3f, 0.05f, 10);
        }
        pt.dirty = true;
    }

    static List<Mob> bouts(ServerLevel sl, Story.Party pt) {
        List<Mob> out = new ArrayList<>();
        for (int place : new int[]{StoryStage.ARENA, StoryStage.VAULT}) {
            Vec3 c = StoryStage.centre(pt.id, place);
            out.addAll(sl.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(60, 30, 60), m -> m.isAlive() && m.getPersistentData().getInt("isg_bout") == pt.id
                    && !(m instanceof Rival rv && rv.yielded) && !(m instanceof TamsinDuelist t && t.yielded())));
        }
        return out;
    }

    // ---- the rivals' bouts
    static void bout(MinecraftServer s, ServerLevel sl, Story.Party pt, List<ServerPlayer> in, int rival, int rank, String after) {
        switch (pt.step) {
            case 0 -> { padFx(sl, pt, s); if (onPad(pt, in)) { pt.step = 1; callRival(s, sl, pt, in, rival, rank); } }
            case 1 -> { if (bouts(sl, pt).isEmpty() && !pt.flag("won" + pt.ch)) pt.step = 0; }   // they left (you fell): it waits for you again
            case 2 -> { pt.step = 3; play(s, pt, after); }
            default -> {}
        }
    }

    static void callRival(MinecraftServer s, ServerLevel sl, Story.Party pt, List<ServerPlayer> in, int id, int rank) {
        bell(s, pt, Rivals.NAMES[id] + ", " + Rivals.EPITHETS[id] + ".");
        Vec3 c = pos(pt, StoryStage.ARENA, StoryStage2.BOUT_PAD);
        var ts = Rivals.types(id);
        List<Rival> made = new ArrayList<>();
        for (int i = 0; i < ts.size(); i++) {
            Rival r = ts.get(i).get().create(sl);
            if (r == null) continue;
            Vec3 at = pos(pt, StoryStage.ARENA, new double[]{(i - (ts.size() - 1) * 0.5) * 3, 0, StoryStage2.RIVAL_START[2]});
            r.moveTo(at.x, at.y, at.z, 0, 0);
            r.setYHeadRot(0); r.yBodyRot = 0;
            r.arena = c;
            r.challenger = in.get(0).getUUID();
            r.focus = r.challenger;
            r.setRank(rank);
            scale(r, in.size());
            tagBout(r, pt, StoryStage.ARENA);
            r.getPersistentData().putInt("isg_trial", pt.id);
            sl.addFreshEntity(r);
            made.add(r);
        }
        if (made.size() == 2) { made.get(0).partner = made.get(1).getUUID(); made.get(1).partner = made.get(0).getUUID(); }
        pt.dirty = true;
    }

    /** A rival's bout is won (Rivals.won, under Trials rules: the story pays, not the rival's record). */
    public static void trialWon(Rival b) {
        if (!(b.level() instanceof ServerLevel sl)) return;
        Story.Party pt = Story.byId(sl.getServer(), b.getPersistentData().getInt("isg_trial"));
        if (pt == null || pt.arc != 2) return;
        MinecraftServer s = sl.getServer();
        pt.set("won" + pt.ch);
        for (ServerPlayer p : StoryStage.inside(s, pt, StoryStage.ARENA)) GrimoireMastery.bountyXp(p, 500);
        Story.tellAll(s, pt, Component.literal("The crowd roars. ").withStyle(ChatFormatting.GOLD).append(Component.literal("You win the bout (+500 mastery).").withStyle(ChatFormatting.YELLOW)));
        if ((pt.ch == 1 || pt.ch == 4) && pt.step == 1) pt.step = 2;
        if (pt.ch == 5 && pt.step == 2 && !pt.flag("threw")) { pt.step = 3; play(s, pt, "champion"); }
        pt.dirty = true;
    }

    // ---- chapter 3: Tamsin
    static void friend(MinecraftServer s, ServerLevel sl, Story.Party pt, List<ServerPlayer> in, long now) {
        switch (pt.step) {
            case 0 -> {
                padFx(sl, pt, s);
                if (onPad(pt, in)) {
                    pt.step = 1;
                    for (StoryNpc n : StoryArc1.npcs(sl, pt, AddonEntities.STORY_TAMSIN.get())) n.discard();   // she's on the sand now
                    bell(s, pt, "Tamsin Reed of Larkhollow.");
                    TamsinDuelist t = AddonEntities.TAMSIN_DUELIST.get().create(sl);
                    if (t == null) return;
                    Vec3 at = pos(pt, StoryStage.ARENA, StoryStage2.RIVAL_START);
                    t.moveTo(at.x, at.y, at.z, 0, 0);
                    t.party = pt.id;
                    scale(t, in.size());
                    tagBout(t, pt, StoryStage.ARENA);
                    sl.addFreshEntity(t);
                }
            }
            case 2 -> {   // the Wardens come for her
                if (now < pt.n.getLong("carryAt")) return;
                for (Entity e : sl.getEntitiesOfClass(Entity.class, new AABB(StoryStage.centre(pt.id, StoryStage.ARENA), StoryStage.centre(pt.id, StoryStage.ARENA)).inflate(60, 30, 60),
                        e -> e.getPersistentData().getBoolean("isg_carry") && e.getPersistentData().getInt("isg_story") == pt.id || e instanceof TamsinDuelist t && t.party == pt.id)) {
                    FxSend.ring(sl, Fx.GOLD, e.position().add(0, 0.1, 0), 1.6f, 0.2f, 0.06f, 12);
                    e.discard();
                }
                pt.step = 3;
                play(s, pt, "tamsin_taken");
            }
            case 4 -> {
                Vec3 c = pos(pt, StoryStage.ARENA, StoryStage2.TAMSIN_CELL);
                for (ServerPlayer p : in) if (p.position().distanceTo(c) < 3.5) { pt.step = 5; play(s, pt, "cell_empty"); return; }
            }
            default -> {}
        }
    }

    /** Tamsin yields: two Wardens walk out onto the sand and stand over her. */
    static void tamsinYielded(TamsinDuelist t) {
        if (!(t.level() instanceof ServerLevel sl)) return;
        Story.Party pt = Story.byId(sl.getServer(), t.party);
        if (pt == null || pt.arc != 2 || pt.ch != 2 || pt.step != 1) return;
        pt.set("won2");
        for (int i = 0; i < 2; i++) {
            Warden w = AddonEntities.WARDEN.get().create(sl);
            if (w == null) continue;
            Vec3 at = t.position().add(i == 0 ? -1.4 : 1.4, 0, -1);
            w.moveTo(at.x, at.y, at.z, 180, 0);
            w.setNoAi(true);
            tag(w, pt, StoryStage.ARENA);
            w.getPersistentData().putBoolean("isg_carry", true);
            sl.addFreshEntity(w);
            FxSend.ring(sl, Fx.GOLD, at.add(0, 0.1, 0), 0.2f, 1.2f, 0.05f, 10);
        }
        for (ServerPlayer p : StoryStage.inside(sl.getServer(), pt, StoryStage.ARENA)) GrimoireMastery.bountyXp(p, 500);
        pt.step = 2;
        pt.n.putLong("carryAt", sl.getGameTime() + 80);
        pt.note("You beat Tamsin Reed. The Wardens carried her out.");
        pt.dirty = true;
    }

    // ---- chapter 4: the catacomb and the ledger
    static void proof(MinecraftServer s, ServerLevel sl, Story.Party pt, List<ServerPlayer> in) {
        if (pt.count("ledger") >= 3) { if (pt.step == 0) { pt.step = 1; play(s, pt, "proof"); } return; }
        for (int i = 0; i < StoryStage2.ROOMS.length; i++) {
            int[] rm = StoryStage2.ROOMS[i];
            Vec3 c = StoryStage.at(pt.id, StoryStage.ARENA, rm[0], StoryStage2.CY, rm[1]);
            boolean someone = false;
            for (ServerPlayer p : in) if (Math.abs(p.getX() - c.x) < 4.5 && Math.abs(p.getZ() - c.z) < 4.5 && Math.abs(p.getY() - c.y) < 3) someone = true;
            if (!someone) continue;
            String k = "room" + i;
            if (!pt.flag(k + "woke")) {   // its Wardens step out of the dark
                pt.set(k + "woke");
                for (int j = 0; j < 2 + (in.size() - 1); j++) {
                    Warden w = AddonEntities.WARDEN.get().create(sl);
                    if (w == null) continue;
                    Vec3 at = c.add(j % 2 == 0 ? -2 : 2, 0, j < 2 ? -1 : 1);
                    w.moveTo(at.x, at.y, at.z, 0, 0);
                    tagBout(w, pt, StoryStage.ARENA);
                    w.getPersistentData().putInt("isg_room", i + 1);
                    sl.addFreshEntity(w);
                    FxSend.ring(sl, Fx.GOLD, at.add(0, 0.1, 0), 0.2f, 1.2f, 0.05f, 10);
                }
                Story.tellAll(s, pt, Component.literal("Wardens - down here, in the dark, waiting.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                continue;
            }
            if (pt.flag(k + "read")) continue;
            int ii = i;
            boolean guarded = !sl.getEntitiesOfClass(Warden.class, new AABB(c, c).inflate(8, 4, 8), w -> w.isAlive() && w.getPersistentData().getInt("isg_room") == ii + 1).isEmpty();
            if (guarded) continue;
            for (ServerPlayer p : in) {
                if (p.position().distanceTo(c) > 3.2) continue;
                pt.set(k + "read");
                ItemStack page = AshenPageItem.of(3 + i);
                give(p, page, null);
                StoryArc1.readPage(p, 3 + i);
                break;
            }
        }
    }

    // ---- chapter 6: Vey's offer and the final
    static void finalBout(MinecraftServer s, ServerLevel sl, Story.Party pt, List<ServerPlayer> in, long now) {
        switch (pt.step) {
            case 0 -> { pt.step = 1; play(s, pt, "vey_offer", "Win the final", "Throw the final"); }
            case 1 -> {
                if (!pt.flag("chose")) return;
                padFx(sl, pt, s);
                if (onPad(pt, in)) { pt.step = 2; pt.n.putLong("bellAt", now); callRival(s, sl, pt, in, Rivals.SEREN, 3); }
            }
            case 2 -> {
                if (pt.flag("threw") && now - pt.n.getLong("bellAt") > 200) { pt.step = 3; play(s, pt, "throw_final"); return; }
                if (bouts(sl, pt).isEmpty() && !pt.flag("won5")) pt.step = 1;
            }
            default -> {}
        }
    }

    // ---- chapter 7: the vault
    static void vault(MinecraftServer s, ServerLevel sl, Story.Party pt, List<ServerPlayer> in) {
        switch (pt.step) {
            case 0 -> { pt.step = 1; play(s, pt, pt.flag("threw") ? "vault_throw" : "vault_win"); }
            case 1 -> {
                if (pt.n.getBoolean("veyUp") && !vey(sl, pt).isEmpty()) return;
                if (pt.n.getBoolean("veyUp") && vey(sl, pt).isEmpty() && pt.flag("veyFell")) return;
                MagistrateVey b = AddonEntities.MAGISTRATE_VEY.get().create(sl);
                if (b == null) return;
                Vec3 at = pos(pt, StoryStage.VAULT, StoryStage2.VEY_VAULT);
                b.moveTo(at.x, at.y, at.z, 180, 0);
                b.setYBodyRot(180); b.setYHeadRot(180);
                b.party = pt.id;
                scale(b, in.size());
                tagBout(b, pt, StoryStage.VAULT);
                sl.addFreshEntity(b);
                FxSend.ring(sl, Fx.GOLD, at.add(0, 0.1, 0), 0.4f, 4f, 0.1f, 16);
                pt.n.putBoolean("veyUp", true);
            }
            case 2 -> play(s, pt, "tamsin_grimoire");
            case 5 -> { if (StoryArc1.pad(s, pt, in, pos(pt, StoryStage.VAULT, StoryStage2.VAULT_PAD), "Leaving the vault")) finish(s, pt); }
            default -> {}
        }
    }

    static List<MagistrateVey> vey(ServerLevel sl, Story.Party pt) {
        Vec3 c = StoryStage.centre(pt.id, StoryStage.VAULT);
        return sl.getEntitiesOfClass(MagistrateVey.class, new AABB(c, c).inflate(40, 20, 40), b -> b.isAlive() && b.party == pt.id && !b.isDeadOrDying());
    }

    static void veyFell(MagistrateVey b) {
        if (!(b.level() instanceof ServerLevel sl)) return;
        Story.Party pt = Story.byId(sl.getServer(), b.party);
        if (pt == null || pt.arc != 2 || pt.ch != 6 || pt.step != 1) return;
        pt.set("veyFell");
        Vec3 c = StoryStage.centre(pt.id, StoryStage.VAULT);
        for (Warden w : sl.getEntitiesOfClass(Warden.class, new AABB(c, c).inflate(40, 20, 40), w -> w.getPersistentData().getInt("isg_story") == pt.id)) { FxSend.ring(sl, Fx.GOLD, w.position().add(0, 0.1, 0), 1.2f, 0.2f, 0.05f, 10); w.discard(); }
        pt.note("Magistrate Vey fell in his own vault" + (pt.flag("threw") ? ", with the whole city watching through the grate." : "."));
        pt.step = 2;
        pt.dirty = true;
    }

    static void finish(MinecraftServer s, Story.Party pt) {
        boolean threw = pt.flag("threw");
        var charm = threw ? AddonItemRegistry.CINDER_TOKEN.get() : AddonItemRegistry.VEY_SEAL.get();
        for (ServerPlayer p : StoryStage.inside(s, pt, StoryStage.VAULT)) {
            GrimoireMastery.bountyXp(p, 4000);
            give(p, BossRules.core(2), null);
            give(p, new ItemStack(charm), null);
            p.sendSystemMessage(Component.literal("ARC II COMPLETE  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("+4000 mastery, a tier II Mana Core, " + new ItemStack(charm).getHoverName().getString() + ".").withStyle(ChatFormatting.YELLOW)));
            StoryStage.leave(p);
        }
        toChapter(s, pt, 7);
    }

    // ================================================================== scenes ended, choices made
    static void onTalkDone(MinecraftServer s, Story.Party pt, String id, int choice) {
        switch (id) {
            case "tamsin_meet" -> { if (pt.ch == 0) { pt.note("You met Tamsin Reed of Larkhollow in the waiting cells."); toChapter(s, pt, 1); } }
            case "brannoch_after" -> { if (pt.ch == 1) toChapter(s, pt, 2); }
            case "tamsin_taken" -> { if (pt.ch == 2 && pt.step == 3) pt.step = 4; }
            case "cell_empty" -> { if (pt.ch == 2) toChapter(s, pt, 3); }
            case "proof" -> { if (pt.ch == 3) { pt.note("The harvest ledger: the Trials' losers are sold - some of them to 'the Choir'."); toChapter(s, pt, 4); } }
            case "twins_after" -> { if (pt.ch == 4) toChapter(s, pt, 5); }
            case "vey_offer" -> {
                if (pt.ch != 5) return;
                boolean threw = choice == 1;
                if (threw) pt.set("threw");
                pt.set("chose");
                pt.note(threw ? "The party chose to throw the final." : "The party chose to win the final.");
                ServerLevel sl = StoryStage.rift(s);
                if (sl != null) for (StoryNpc n : StoryArc1.npcs(sl, pt, AddonEntities.STORY_VEY.get())) n.discard();
            }
            case "champion", "throw_final" -> {
                if (pt.ch != 5) return;
                ServerLevel sl = StoryStage.rift(s);
                if (sl != null) for (Mob m : bouts(sl, pt)) m.discard();
                List<ServerPlayer> in = StoryStage.inside(s, pt, StoryStage.ARENA);
                toChapter(s, pt, 6);
                for (ServerPlayer p : in) StoryStage.enter(p, pt, StoryStage.VAULT, StoryStage.spawn(pt.id, StoryStage.VAULT), 0);
            }
            case "tamsin_grimoire" -> {
                if (pt.ch != 6) return;
                pt.note("Tamsin's grimoire was on shelf eleven. Every page had been cut out.");
                Story.raiseBrand(s, pt, 2);
                for (ServerPlayer p : Story.online(s, pt)) title(p, "BRAND II", "Cinderheart - it burns hotter, and it takes from you");
                pt.step = 3;
                play(s, pt, "brand2");
            }
            case "brand2" -> { if (pt.ch == 6) { pt.step = 5; pt.note("The Brand woke further: Brand II, Cinderheart."); } }
            default -> {}
        }
        pt.dirty = true;
    }

    // ================================================================== the people
    static void talkTo(ServerPlayer p, Story.Party pt, StoryNpc npc) {
        String line = switch (npc.who) {
            case "tamsin" -> pt.ch == 0 ? "Larkhollow had the best apples in the valley. I'm going to plant them all again." : "Good luck out there. Not too much, mind.";
            case "vey" -> "The Trials are a civic good, entrant. Coin for the victors, a purpose for the rest.";
            default -> switch (pt.ch) {
                case 0 -> pt.step < 3 ? "Win the qualifiers. Don't look at the north door." : "The cells are down the hall. Get some rest.";
                case 1 -> "Brannoch fights like a wall falls. Don't be under it.";
                case 2 -> pt.step < 4 ? "Tamsin's next. ...Win it. Whatever it costs. Win." : "Go and look at her cell. I'll wait.";
                case 3 -> "The ramp past the cells goes down. Read everything. Kill what stops you.";
                case 4 -> "The Bellmars fight as one. Make them two.";
                case 5 -> "One more bout. Then the vault.";
                case 6 -> "Shelf after shelf of them. Don't look too long.";
                default -> "Rest. We go north next - something is coming up out of the deep places.";
            };
        };
        String who = switch (npc.who) { case "tamsin" -> "Tamsin"; case "vey" -> "Vey"; default -> "Corvin"; };
        p.sendSystemMessage(Component.literal(who + ": ").withStyle(npc.who.equals("vey") ? ChatFormatting.GOLD : npc.who.equals("tamsin") ? ChatFormatting.GREEN : ChatFormatting.AQUA)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
    }

    /** The Journal's "go back": the arena, or the vault. */
    static void enterRequest(MinecraftServer s, Story.Party pt, ServerPlayer p) {
        if (StoryStage.inStage(p) || pt.ch >= 7) return;
        int place = pt.ch == 6 ? StoryStage.VAULT : StoryStage.ARENA;
        StoryStage.enter(p, pt, place, StoryStage.spawn(pt.id, place), place == StoryStage.ARENA ? 180 : 0);
    }

    // ================================================================== the Journal
    static void journal(MinecraftServer s, Story.Party pt, CompoundTag t) {
        t.putString("arcTitle", ARC);
        int ch = Math.max(0, Math.min(7, pt.ch));
        t.putString("chTitle", ch >= 7 ? "Arc II complete" : "Chapter " + (ch + 1) + " - " + TITLES[ch]);
        t.putString("chText", TEXT[ch]);
        ListTag goals = new ListTag();
        for (Object[] g : goals(pt)) {
            CompoundTag x = new CompoundTag();
            x.putString("t", (String) g[0]); x.putInt("h", (Integer) g[1]); x.putInt("n", (Integer) g[2]);
            goals.add(x);
        }
        t.put("goals", goals);
        ListTag pages = new ListTag();
        for (int i = 0; i < StoryArc1.PAGES.length; i++) if (pt.flag("page" + i)) {
            CompoundTag x = new CompoundTag();
            x.putInt("i", i); x.putString("t", StoryArc1.PAGE_TITLES[i]); x.putString("b", StoryArc1.PAGES[i]);
            pages.add(x);
        }
        t.put("pages", pages);
        if (ch < 6) t.putString("enter", "Return to Cindermarch");
        else if (ch == 6) t.putString("enter", "Return to the vault");
        if (pt.talk != null && pt.talk.choices.length > 0) t.putString("voting", pt.talk.id);
    }

    static List<Object[]> goals(Story.Party pt) {
        List<Object[]> g = new ArrayList<>();
        switch (pt.ch) {
            case 0 -> {
                g.add(new Object[]{"Win the qualifying bouts on the sand", Math.min(3, pt.count("qual")), 3});
                if (pt.step >= 3) g.add(new Object[]{"Go down to the waiting cells", pt.step >= 4 ? 1 : 0, 1});
            }
            case 1 -> g.add(new Object[]{"Step into the ring: Brannoch Tor", pt.step >= 2 ? 1 : 0, 1});
            case 2 -> {
                g.add(new Object[]{"Step into the ring: Tamsin Reed", pt.step >= 2 ? 1 : 0, 1});
                if (pt.step >= 4) g.add(new Object[]{"Find Tamsin in the waiting cells", pt.step >= 5 ? 1 : 0, 1});
            }
            case 3 -> g.add(new Object[]{"Find the harvest ledger under the arena", Math.min(3, pt.count("ledger")), 3});
            case 4 -> g.add(new Object[]{"Step into the ring: the Bellmar twins", pt.step >= 2 ? 1 : 0, 1});
            case 5 -> {
                g.add(new Object[]{"Hear the Magistrate out", pt.flag("chose") ? 1 : 0, 1});
                if (pt.flag("chose")) g.add(new Object[]{pt.flag("threw") ? "Step into the ring - and throw the final" : "Step into the ring: Seren Vale", pt.step >= 3 ? 1 : 0, 1});
            }
            case 6 -> {
                g.add(new Object[]{"Bring down Magistrate Vey", pt.flag("veyFell") ? 1 : 0, 1});
                if (pt.step >= 5) g.add(new Object[]{"Leave the vault: stand in the circle by the door", 0, 1});
            }
            default -> {}
        }
        return g;
    }

    // ================================================================== for the tests
    static void testClearBout(MinecraftServer s, Story.Party pt) { ServerLevel sl = StoryStage.rift(s); if (sl != null) for (Mob m : bouts(sl, pt)) m.discard(); }
}
