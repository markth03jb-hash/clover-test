package com.ironspellgrimoire.server;

import com.ironspellgrimoire.entity.FatherSallow;
import com.ironspellgrimoire.entity.Penitent;
import com.ironspellgrimoire.entity.StoryNpc;
import com.ironspellgrimoire.item.AshenPageItem;
import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ASHFALL, Arc I: Embers. The chapters, what each asks of the party, the scenes, and the places it all happens.
 * <ol start="0">
 *   <li><b>Ashfall</b> (Emberfall): wake in the burnt village, find Ilsa, hold off Sallow's Penitents. They take her;
 *       Corvin Hale arrives too late, as he always does. Leave with him.</li>
 *   <li><b>The Knight in the Ash</b>: Corvin trains you - cast, hunt by night, bring down an elite.</li>
 *   <li><b>What Burns in You</b>: hunt Sallow's patrols for the pages they carry. The Brand wakes (Brand I); learn to use it.</li>
 *   <li><b>The Trail of Ash</b>: follow it to the chapel's door, far off.</li>
 *   <li><b>The Chapel of the Freed</b>: the nave, Father Sallow, and what the party does with him when he yields.</li>
 *   <li>Arc I complete.</li>
 * </ol>
 */
public final class StoryArc1 {
    private StoryArc1() {}

    public static final String ARC = "ARC I - EMBERS";
    public static final String[] TITLES = {"Ashfall", "The Knight in the Ash", "What Burns in You", "The Trail of Ash", "The Chapel of the Freed", "Embers"};
    static final String[] TEXT = {
            "The star came down on Emberfall in the night. You woke in the ash with a brand burning on your arm. Someone else is alive in the village.",
            "Corvin Hale, a one-armed knight who follows the falling stars, pulled you out of Emberfall. Sallow's Penitents took Ilsa. Before you can go after her, Corvin says, you have to learn to fight.",
            "Sallow's people walk the roads, hunting anyone with the mark. Corvin says faith that ugly always writes itself down - take what they carry and read it.",
            "The Hand's letter came from the north. Sallow's chapel won't be on any map: it's where the ash still falls. Follow it.",
            "Candles, hundreds of them, and under the incense the smell of a butcher's yard. Ilsa is somewhere inside.",
            "Father Sallow's chapel is done. Ilsa is gone - out there with a brand bigger than yours and a voice in her head. Corvin has a name from Sallow's list: the Ember Trials.\n\nWhen your party is ready, its leader begins ARC II - THE PROVING from this Journal."};

    // ================================================================== the pages
    static final String[] PAGE_TITLES = {"The Litany of the Freed, I", "The Litany of the Freed, II", "A Letter, Unsigned",
            "The Harvest Ledger, Spring", "The Harvest Ledger, Autumn", "A Receipt, Sealed in Gold"};
    static final String[] PAGES = {
            "The star is a wound in the sky, and the wound bleeds into the faithless. Those it marks are not chosen. They are infected.\n\nWe do not hate the marked. We pity them. We cut because we love.",
            "Brother Aud held the girl from Harrowmere while I worked. She thanked me before the end. They always thank me, after a while.\n\nThe mark comes out whole, like a seed. It is still warm in the jar.",
            "Father - we found two at Emberfall. The girl is in the lower cells. The other ran with the one-armed knight.\n\nThe girl's brand is larger than any you have taken. She will not stop praying to it. Send word what to do.\n\n- your Hand",
            "Bout 4, first round. Loser: a fire-singer from the coast. Grimoire: twelve pages, three of them strong. Vault, shelf nine.\nBout 6. Loser: a boy from Harrowmere. Grimoire: poor. Kept for the binding.\nBout 9. Loser...\n\nThe list goes on for forty pages.",
            "Received from Father S.: four names, all marked. Sent on to the Trials as invited guests. Two lost in the first round; their books are on shelf eleven.\n\nA note in the margin, in the same hand: 'He sends us the branded now. Their books burn warmer.'",
            "To the Magistrate, from the Choir: payment for one grimoire carrying the star's mark. More are wanted. More will be paid.\n\nSealed in gold wax. The seal is a crown of ash."};
    public static String pageTitle(int i) { return PAGE_TITLES[Math.max(0, Math.min(PAGE_TITLES.length - 1, i))]; }

    // ================================================================== the scenes
    static List<String[]> scene(String id) {
        return switch (id) {
            case "wake" -> Story.lines(
                    "narrator", "Ash. Ash where the roofs were. Ash in your mouth.",
                    "narrator", "The star came down over Emberfall at the second bell. You remember the light. You don't remember the screaming stopping.",
                    "narrator", "Your arm burns. Under the soot something is written into your skin - a brand, still glowing, still warm.",
                    "narrator", "Somewhere in the village, someone is crying.");
            case "ilsa" -> Story.lines(
                    "ilsa", "Don't - oh. Oh, it's you. You're alive.",
                    "ilsa", "Everyone's gone. My father was by the well when it hit. There's nothing left of the well.",
                    "ilsa", "Look at my arm. It's the same as yours. It won't stop burning.",
                    "ilsa", "Lanterns. On the hill road. Someone's coming - are they here to help?",
                    "narrator", "They wear sacks over their heads. They carry sickles. They are not here to help.");
            case "corvin_meet" -> Story.lines(
                    "corvin", "Down. Stay down. ...There. That's the last of them.",
                    "corvin", "Corvin Hale. Once a knight. Now a man who follows falling stars - and gets there too late.",
                    "corvin", "Show me your arm. ...Hells. You're carrying a piece of it.",
                    "corvin", "The girl? Sallow's people took her. They take everyone with the mark.",
                    "corvin", "They say he cuts it out. They say it sets you free. I've never seen anyone walk out of his chapel.",
                    "corvin", "You can't save her like this. Come with me. I'll teach you to fight - and to keep that thing in your arm from eating you alive.");
            case "corvin_lesson" -> Story.lines(
                    "corvin", "Not bad. You still fight like a farmer - but a farmer who doesn't die.",
                    "corvin", "I lost this arm to a thing like the one in yours. Ten years ago. I was a captain then. I had men.",
                    "corvin", "Sallow's people hunt the roads, for anyone with the mark. Kill them. Read what they carry. Faith that ugly always writes itself down.");
            case "brand_wakes" -> Story.lines(
                    "corvin", "'The girl is in the lower cells.' She's alive. For now.",
                    "corvin", "...Your arm. Look at it.",
                    "narrator", "The brand splits open like an eye. Heat pours up your arm, into your chest, into your teeth. For one heartbeat you can hear the star - far away, and very, very hungry.",
                    "corvin", "Breathe. Don't fight it - hold it. Like a blade by the wrong end.",
                    "corvin", "That's the first stage. It'll lend you its fire for a little while. Use it, and keep using it, until you're the one who decides when it burns.");
            case "corvin_trail" -> Story.lines(
                    "corvin", "Good. You're still you. That's more than most manage.",
                    "corvin", "The Hand's letter came down the north road. Sallow's chapel won't be on any map - it'll be where the ash still falls.",
                    "corvin", "Follow the embers. I'll be at your back.");
            case "chapel_enter" -> Story.lines(
                    "narrator", "Candles. Hundreds of them. And under the incense, the smell of a butcher's yard.",
                    "corvin", "I'll hold the door. Nobody comes in behind you. Find her.");
            case "sallow_meet" -> Story.lines(
                    "sallow", "Ah. The one that ran. Come in, child, come in - you're bleeding light all over my floor.",
                    "sallow", "Do you know what you carry? A splinter of a dead god that wants to live again. It will grow in you until there is no you left.",
                    "sallow", "I am the only one who has ever offered your kind mercy. A small cut. A little pain. And then nothing in you at all.",
                    "ilsa", "Don't listen to him! He takes them out and they just - stop. They stop talking. They stop -",
                    "sallow", "Hush, Ilsa. You're next, and you've been so patient.",
                    "sallow", "Hold them still, my faithful. Let us free them all.");
            case "sallow_yield" -> Story.lines(
                    "sallow", "Enough - enough! Mercy. You understand mercy, don't you? I gave it to so many.",
                    "sallow", "Spare me and I'll tell you where she'll go. She's mine. I know what the Brand whispers to her.",
                    "corvin", "He's a butcher. But he isn't lying about knowing things. Your call.");
            case "sallow_spared" -> Story.lines(
                    "sallow", "The Choir. She'll go to the Ashen Choir. They worship what you carry. They'll make her a saint.",
                    "sallow", "Go on, then. I'll pray for you. For both of you.",
                    "narrator", "He limps into the dark behind the altar. You will see him again.");
            case "sallow_killed" -> Story.lines(
                    "sallow", "You'll... become... what you kill...",
                    "narrator", "His lantern of stolen brands goes dark, one by one. Something in your arm drinks the last of their heat.",
                    "corvin", "No more chapel. No more jars.");
            case "arc1_end" -> Story.lines(
                    "corvin", "She's out there with a brand bigger than yours and a voice in her head. That isn't a rescue. That's a lit fuse.",
                    "corvin", "Sallow kept a list. Mages from all over, sent to the Ember Trials in the walled city. The winners get coin. The losers don't come home.",
                    "corvin", "Rest while you can. When the Trials open, we go.",
                    "narrator", "ARC I - EMBERS - COMPLETE.");
            default -> Story.lines("narrator", "...");
        };
    }

    static void play(MinecraftServer s, Story.Party pt, String id, String... choices) { Story.talk(s, pt, id, scene(id), choices); }

    // ================================================================== the places: where things stand in them
    static final double[] EMBER_SPAWN = {0, 0, 18}, ILSA = {11, 0, 2}, GATE_PAD = {0, 0, 22}, CORVIN_GATE = {2, 0, 23};
    static final double[] CHAPEL_SPAWN = {0, 0, 3}, CHAPEL_PAD = {0, 0, 1}, CORVIN_DOOR = {2, 0, 1}, SALLOW = {0, 0, 40};

    static Vec3 pos(Story.Party pt, int place, double[] a) { return StoryStage.at(pt.id, place, a[0], a[1], a[2]); }

    // ================================================================== starting, chapters
    static void begin(MinecraftServer s, Story.Party pt) {
        pt.arc = 1; pt.ch = 0; pt.step = 0; pt.n = new CompoundTag();
        pt.note("ARC I - EMBERS began.");
        for (ServerPlayer p : Story.online(s, pt)) {
            give(p, new ItemStack(AddonItemRegistry.STORY_JOURNAL.get()), AddonItemRegistry.STORY_JOURNAL.get());
            StoryStage.enter(p, pt, StoryStage.EMBER, pos(pt, StoryStage.EMBER, EMBER_SPAWN), 180);
            title(p, "ASHFALL", "Chapter 1 - " + TITLES[0]);
        }
        setTarget(pt, s, pos(pt, StoryStage.EMBER, ILSA), Rifts.RIFT);
        play(s, pt, "wake");
        pt.dirty = true;
    }

    static void give(ServerPlayer p, ItemStack s, net.minecraft.world.item.Item unless) {
        if (unless != null && p.getInventory().contains(new ItemStack(unless))) return;
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    static void title(ServerPlayer p, String big, String small) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(small).withStyle(ChatFormatting.GRAY)));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(big).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
    }

    static void setTarget(Story.Party pt, MinecraftServer s, Vec3 at, net.minecraft.resources.ResourceKey<Level> dim) {
        pt.target = true; pt.tdim = dim.location().toString(); pt.tx = at.x; pt.ty = at.y; pt.tz = at.z; pt.dirty = true;
    }

    /** Moves the story to a chapter's start (also /story admin chapter). */
    static void toChapter(MinecraftServer s, Story.Party pt, int ch) {
        clearScenes(s, pt);
        int was = pt.ch;
        pt.ch = ch; pt.step = 0; pt.n = new CompoundTag(); pt.target = false; pt.talk = null;
        if (ch < 0) { pt.dirty = true; return; }
        if (ch >= 1 && pt.brand < 0) pt.brand = 0;
        if (ch >= 3) Story.raiseBrand(s, pt, 1);
        setup(s, pt);
        if (ch != was && ch > 0) {
            for (ServerPlayer p : Story.online(s, pt)) title(p, ch >= 5 ? "ARC I COMPLETE" : "Chapter " + (ch + 1), ch >= 5 ? "Embers" : TITLES[ch]);
            pt.note(ch >= 5 ? "ARC I - EMBERS - complete." : "Chapter " + (ch + 1) + ": " + TITLES[ch] + ".");
        }
        pt.dirty = true;
    }

    /** A chapter's opening state: who stands where, where the tracker points. */
    static void setup(MinecraftServer s, Story.Party pt) {
        pt.stepAt = s.overworld().getGameTime();
        switch (pt.ch) {
            case 0 -> setTarget(pt, s, pos(pt, StoryStage.EMBER, ILSA), Rifts.RIFT);
            case 3 -> trail(s, pt);
            case 4 -> { if (pt.target) break; trail(s, pt); }
            default -> pt.target = false;
        }
    }

    /** The chapel's door: far off from the leader (400 to 520 blocks), on the surface. */
    static void trail(MinecraftServer s, Story.Party pt) {
        ServerPlayer l = Story.lead(s, pt);
        ServerLevel ow = s.overworld();
        Vec3 from = l != null && l.level().dimension().equals(Level.OVERWORLD) ? l.position() : Vec3.atCenterOf(ow.getSharedSpawnPos());
        double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2), d = ThreadLocalRandom.current().nextDouble(400, 520);
        setTarget(pt, s, new Vec3(Math.floor(from.x + Math.cos(a) * d) + 0.5, -1, Math.floor(from.z + Math.sin(a) * d) + 0.5), Level.OVERWORLD);
        pt.ty = -1;   // found when someone gets there (the chunk loads)
    }

    /** Back to the start of the chapter (leaving a party, or a scene abandoned). */
    static void restartChapter(MinecraftServer s, Story.Party pt) {
        if (pt.ch < 0) return;
        clearScenes(s, pt);
        pt.step = 0; pt.n = new CompoundTag(); pt.talk = null;
        if (pt.ch == 2 && pt.flag("brandWoke")) pt.step = 1;
        setup(s, pt);
        pt.dirty = true;
    }

    static void dissolve(MinecraftServer s, Story.Party pt) { clearScenes(s, pt); removeCorvin(s, pt); }

    // ================================================================== the tick
    static final Map<Integer, Long> NEXT_PATROL = new HashMap<>();
    static final Map<UUID, Integer> PAD = new HashMap<>();

    static void tick(MinecraftServer s, Story.Party pt, List<ServerPlayer> on) {
        if (pt.arc == 2) { catchFalls(pt, on); follow(s, pt); StoryArc2.tick(s, pt, on); return; }
        if (pt.arc == 3) { catchFalls(pt, on); follow(s, pt); StoryArc3.tick(s, pt, on); return; }
        if (pt.ch < 0) return;
        long now = s.overworld().getGameTime();
        // nobody falls off a story place
        for (ServerPlayer p : on) {
            int[] w = StoryStage.where(p);
            if (w != null && p.getY() < StoryStage.Y - 15) {
                Vec3 sp = StoryStage.spawn(pt.id, w[1]);
                p.teleportTo(sp.x, sp.y, sp.z); p.fallDistance = 0;
            }
        }
        follow(s, pt);
        switch (pt.ch) {
            case 0 -> emberfall(s, pt, on, now);
            case 1 -> { if (goalsMet(pt) && !pt.flag("c1ready")) { pt.set("c1ready"); Story.tellAll(s, pt, corvinLine("That'll do. Come and talk to me.")); } }
            case 2 -> patrols(s, pt, on, now);
            case 3 -> door(s, pt, on, now);
            case 4 -> { door(s, pt, on, now); chapel(s, pt, on, now); }
            default -> {}
        }
    }

    static Component corvinLine(String t) {
        return Component.literal("Corvin: ").withStyle(ChatFormatting.AQUA).append(Component.literal(t).withStyle(ChatFormatting.WHITE));
    }

    static void catchFalls(Story.Party pt, List<ServerPlayer> on) {
        for (ServerPlayer p : on) {
            int[] w = StoryStage.where(p);
            if (w != null && p.getY() < StoryStage.Y - 25) { Vec3 sp = StoryStage.spawn(pt.id, w[1]); p.teleportTo(sp.x, sp.y, sp.z); p.fallDistance = 0; }
        }
    }

    // ---- chapter 1: Emberfall
    static void emberfall(MinecraftServer s, Story.Party pt, List<ServerPlayer> on, long now) {
        ServerLevel sl = StoryStage.rift(s);
        if (sl == null) return;
        List<ServerPlayer> in = StoryStage.inside(s, pt, StoryStage.EMBER);
        if (in.isEmpty()) {   // left (or thrown out): after a while the village goes back to how they found it
            pt.add("away", 1);
            if (pt.count("away") > 30 && pt.step > 0 && pt.step < 4) { clearScenes(s, pt); pt.step = 0; pt.n = new CompoundTag(); }
            return;
        }
        pt.n.putInt("away", 0);
        if (!pt.flag("ilsaTaken") && pt.step <= 2) ensureNpc(sl, pt, AddonEntities.STORY_ILSA.get(), StoryStage.EMBER, ILSA, 200);
        if (Story.talking(pt)) return;
        switch (pt.step) {
            case 0 -> {
                Vec3 ilsa = pos(pt, StoryStage.EMBER, ILSA);
                for (ServerPlayer p : in) if (p.position().distanceTo(ilsa) < 4.5) { pt.step = 1; play(s, pt, "ilsa"); return; }
            }
            case 2 -> waves(s, sl, pt, in, now);
            case 4 -> { if (pad(s, pt, in, pos(pt, StoryStage.EMBER, GATE_PAD), "Leaving Emberfall")) leaveEmberfall(s, pt); }
            default -> {}
        }
    }

    static final int[] WAVES = {3, 4, 5};

    static void waves(MinecraftServer s, ServerLevel sl, Story.Party pt, List<ServerPlayer> in, long now) {
        int wave = pt.count("wave");
        int alive = penitents(sl, pt, StoryStage.EMBER).size();
        if (wave >= 3) {
            long w3 = pt.n.getLong("w3at");
            if (!pt.flag("ilsaTaken") && now - w3 > 60) ilsaTaken(s, sl, pt);
            if (now - w3 > 260 || (alive == 0 && now - w3 > 80)) corvinArrives(s, sl, pt);
            return;
        }
        if (alive > 0) return;
        int extra = 2 * (in.size() - 1);
        spawnWave(sl, pt, StoryStage.EMBER, WAVES[wave] + extra, wave == 2);
        pt.add("wave", 1);
        if (wave == 2) pt.n.putLong("w3at", now);
        Story.tellAll(s, pt, Component.literal(new String[]{"Lanterns on the north road. They're coming.", "More of them - out of the smoke.", "The Penitents part. Someone else walks between them."}[wave]).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        pt.dirty = true;
    }

    static void spawnWave(ServerLevel sl, Story.Party pt, int place, int n, boolean hand) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < n + (hand ? 1 : 0); i++) {
            Penitent m = AddonEntities.PENITENT.get().create(sl);
            if (m == null) continue;
            Vec3 at = place == StoryStage.EMBER ? pos(pt, place, new double[]{r.nextInt(-9, 10), 0, -22 + r.nextInt(4)})
                    : pos(pt, place, new double[]{(r.nextBoolean() ? 1 : -1) * r.nextInt(3, 7), 0, 16 + r.nextInt(10)});
            m.moveTo(at.x, at.y, at.z, place == StoryStage.EMBER ? 0 : 180, 0);
            tag(m, pt, place);
            if (hand && i == n) {   // Sallow's Hand
                m.getPersistentData().putBoolean("isg_hand", true);
                m.setCustomName(Component.literal("Sallow's Hand").withStyle(ChatFormatting.DARK_RED));
                var hp = m.getAttribute(Attributes.MAX_HEALTH);
                if (hp != null) hp.addPermanentModifier(new AttributeModifier(UUID.fromString("5a110000-0000-4000-8000-000000000001"), "Sallow's Hand", 1.5, AttributeModifier.Operation.MULTIPLY_TOTAL));
                m.setHealth(m.getMaxHealth());
            }
            sl.addFreshEntity(m);
            FxSend.ring(sl, Fx.FIRE, at.add(0, 0.1, 0), 0.2f, 1.2f, 0.05f, 10);
        }
    }

    static void tag(Entity m, Story.Party pt, int place) {
        CompoundTag t = m.getPersistentData();
        t.putInt("isg_story", pt.id);
        t.putInt("isg_place", place);
        if (m instanceof Mob mob) mob.setPersistenceRequired();
    }

    static List<Penitent> penitents(ServerLevel sl, Story.Party pt, int place) {
        Vec3 c = StoryStage.at(pt.id, place, 0, 0, place == StoryStage.CHAPEL ? 22 : 0);
        return sl.getEntitiesOfClass(Penitent.class, new AABB(c, c).inflate(60, 30, 60), m -> m.isAlive() && m.getPersistentData().getInt("isg_story") == pt.id);
    }

    static void ilsaTaken(MinecraftServer s, ServerLevel sl, Story.Party pt) {
        pt.set("ilsaTaken");
        Story.tellAll(s, pt, Component.literal("Sallow's Hand: ").withStyle(ChatFormatting.DARK_RED).append(Component.literal("The girl first. Father wants the girl.").withStyle(ChatFormatting.WHITE)));
        Story.tellAll(s, pt, Component.literal("Ilsa: ").withStyle(ChatFormatting.GOLD).append(Component.literal("No - let go of me! HELP ME -").withStyle(ChatFormatting.WHITE)));
        for (StoryNpc n : npcs(sl, pt, AddonEntities.STORY_ILSA.get())) {
            FxSend.ring(sl, Fx.FIRE, n.position().add(0, 0.1, 0), 2.4f, 0.2f, 0.08f, 16);
            FxSend.beam(sl, Fx.FIRE, n.position(), n.position().add(0, 3, 0), 0.4f, 12);
            n.discard();
        }
        for (Penitent m : penitents(sl, pt, StoryStage.EMBER)) if (m.getPersistentData().getBoolean("isg_hand")) { FxSend.ring(sl, Fx.FIRE, m.position().add(0, 0.1, 0), 2f, 0.2f, 0.08f, 16); m.discard(); }
        sl.playSound(null, BlockPos.containing(pos(pt, StoryStage.EMBER, ILSA)), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 1f, 0.5f);
        pt.note("Sallow's Penitents took Ilsa.");
    }

    static void corvinArrives(MinecraftServer s, ServerLevel sl, Story.Party pt) {
        if (pt.step != 2) return;
        pt.step = 3;
        StoryNpc c = ensureNpc(sl, pt, AddonEntities.STORY_CORVIN.get(), StoryStage.EMBER, CORVIN_GATE, 0);
        for (Penitent m : penitents(sl, pt, StoryStage.EMBER)) {   // he cuts down what's left
            Vec3 a = m.position().add(0, 1, 0);
            FxSend.slash(sl, Fx.STEEL, a, Vec3.directionFromRotation(0, m.getYRot()), new Vec3(0, 1, 0), 1.6f, 1.2f, 8, 1f);
            m.getPersistentData().putBoolean("isg_cut", true);
            m.kill();
        }
        if (c != null) sl.playSound(null, c.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 1.4f, 0.7f);
        pt.note("Corvin Hale came out of the smoke.");
        play(s, pt, "corvin_meet");
    }

    static void leaveEmberfall(MinecraftServer s, Story.Party pt) {
        for (ServerPlayer p : StoryStage.inside(s, pt, StoryStage.EMBER)) StoryStage.leave(p);
        toChapter(s, pt, 1);
    }

    /** Standing on a place's circle for 3 s does what's asked. */
    static boolean pad(MinecraftServer s, Story.Party pt, List<ServerPlayer> in, Vec3 at, String what) {
        ServerLevel sl = StoryStage.rift(s);
        if (sl != null && s.getTickCount() % 20 == 0) FxSend.ring(sl, Fx.FIRE, at.add(0, 0.05, 0), 1.3f, 1.4f, 0.06f, 22);
        for (ServerPlayer p : in) {
            double dx = p.getX() - at.x, dz = p.getZ() - at.z;
            if (dx * dx + dz * dz < 1.6 * 1.6) {
                int t = PAD.merge(p.getUUID(), 1, Integer::sum);
                p.displayClientMessage(Component.literal(what + "... " + Math.max(1, 3 - t / 2)).withStyle(ChatFormatting.GOLD), true);
                if (t >= 6) { PAD.remove(p.getUUID()); return true; }
            } else PAD.remove(p.getUUID());
        }
        return false;
    }

    // ---- chapter 3: the patrols and their pages
    static void patrols(MinecraftServer s, Story.Party pt, List<ServerPlayer> on, long now) {
        if (pt.step == 1) {
            if (pt.count("brand") >= 3 && !Story.talking(pt) && !pt.flag("c2trail")) { pt.set("c2trail"); play(s, pt, "corvin_trail"); }
            return;
        }
        if (pt.count("pages") >= 3) { if (!pt.flag("c2ready")) { pt.set("c2ready"); Story.tellAll(s, pt, corvinLine("Three pages. Bring them here - I want to hear what they say.")); } return; }
        long next = NEXT_PATROL.computeIfAbsent(pt.id, k -> now + 300);
        if (now < next) return;
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : on) if (p.level().dimension().equals(Level.OVERWORLD) && !p.isSpectator()) out.add(p);
        if (out.isEmpty()) return;
        ServerPlayer p = out.get(ThreadLocalRandom.current().nextInt(out.size()));
        ServerLevel sl = p.serverLevel();
        boolean night = sl.isNight();
        NEXT_PATROL.put(pt.id, now + (night ? 1200 : 2400));
        int live = sl.getEntitiesOfClass(Penitent.class, p.getBoundingBox().inflate(128), m -> m.getPersistentData().getInt("isg_story") == pt.id).size();
        if (live >= 3) return;
        Vec3 at = Rifts.spot(sl, p.position(), 18, 28);
        if (at == null) return;
        int id = pt.count("patrolId") + 1;
        pt.n.putInt("patrolId", id);
        for (int i = 0; i < 3; i++) {
            Penitent m = AddonEntities.PENITENT.get().create(sl);
            if (m == null) continue;
            m.moveTo(at.x + i - 1, at.y, at.z + (i % 2), ThreadLocalRandom.current().nextFloat() * 360, 0);
            tag(m, pt, -1);
            m.getPersistentData().putInt("isg_patrol", id);
            sl.addFreshEntity(m);
        }
        p.displayClientMessage(Component.literal("Lanterns, somewhere close. Sallow's Penitents are out hunting.").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), true);
    }

    /** The page the party gets next: the first neither read nor already dropped (a lost one drops again). */
    static int nextPage(Story.Party pt) {
        for (int i = 0; i < PAGES.length; i++) if (!pt.flag("page" + i) && !pt.flag("dropped" + i)) return i;
        for (int i = 0; i < PAGES.length; i++) if (!pt.flag("page" + i)) return i;
        return -1;
    }

    public static void readPage(ServerPlayer p, int idx) {
        idx = Math.max(0, Math.min(PAGES.length - 1, idx));
        Story.Party pt = Story.partyOf(p);
        if (pt != null && !pt.flag("page" + idx)) {
            pt.set("page" + idx);
            if (pt.arc == 1 && pt.ch == 2 && idx < 3) pt.add("pages", 1);
            if (pt.arc == 2 && pt.ch == 3 && idx >= 3) pt.add("ledger", 1);
            pt.note("Found: " + PAGE_TITLES[idx] + ".");
        }
        Story.sync(p, "page:" + idx);
    }

    // ---- chapters 4 and 5: the trail, the door
    static void door(MinecraftServer s, Story.Party pt, List<ServerPlayer> on, long now) {
        if (!pt.target || !pt.tdim.equals(Level.OVERWORLD.location().toString())) return;
        ServerLevel ow = s.overworld();
        Vec3 t = new Vec3(pt.tx, pt.ty, pt.tz);
        for (ServerPlayer p : on) {
            if (!p.level().dimension().equals(Level.OVERWORLD)) continue;
            double dx = p.getX() - pt.tx, dz = p.getZ() - pt.tz;
            double d2 = dx * dx + dz * dz;
            if (d2 > 64 * 64) continue;
            if (pt.ty < 0) {   // the ground under it, now that it's loaded
                BlockPos b = BlockPos.containing(pt.tx, 0, pt.tz);
                pt.ty = ow.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, b.getX(), b.getZ());
                t = new Vec3(pt.tx, pt.ty, pt.tz);
                pt.dirty = true;
            }
            if (s.getTickCount() % 40 == 0) {
                RiftFx.cut(ow, t.add(0, 2, 0), new Vec3(0, 1, 0), new Vec3(1, 0, 0), 2.2f, 44);
                FxSend.ring(ow, Fx.FIRE, t.add(0, 0.1, 0), 1.6f, 1.7f, 0.06f, 42);
                FxSend.beam(ow, Fx.FIRE, t.add(0, 4.5, 0), t.add(0, 50, 0), 0.5f, 42);
            }
            if (s.getTickCount() % 20 == 0 && d2 < 100) p.displayClientMessage(Component.literal("A door of ash, standing on its own. Step in.").withStyle(ChatFormatting.GOLD), true);
            if (d2 < 1.4 * 1.4 && Math.abs(p.getY() - pt.ty) < 2.5) enterChapel(s, pt, p);
        }
    }

    static void enterChapel(MinecraftServer s, Story.Party pt, ServerPlayer p) {
        if (!StoryStage.enter(p, pt, StoryStage.CHAPEL, pos(pt, StoryStage.CHAPEL, CHAPEL_SPAWN), 0)) return;
        if (pt.ch == 3) {
            int keep = pt.n.getInt("brand");
            double tx = pt.tx, ty = pt.ty, tz = pt.tz;
            toChapter(s, pt, 4);
            pt.n.putInt("brand", keep);
            setTarget(pt, s, new Vec3(tx, ty, tz), Level.OVERWORLD);
        }
    }

    // ---- chapter 5: the chapel
    static void chapel(MinecraftServer s, Story.Party pt, List<ServerPlayer> on, long now) {
        ServerLevel sl = StoryStage.rift(s);
        if (sl == null) return;
        List<ServerPlayer> in = StoryStage.inside(s, pt, StoryStage.CHAPEL);
        if (in.isEmpty()) {
            pt.add("away", 1);
            if (pt.count("away") > 30 && pt.step >= 1 && pt.step < 6) {   // nobody's inside: it waits for them, as it was
                for (Penitent m : penitents(sl, pt, StoryStage.CHAPEL)) m.discard();
                for (FatherSallow b : sallow(sl, pt)) b.discard();
                pt.step = Math.min(pt.step, pt.step >= 3 ? 3 : 1);
                pt.n.putInt("away", 0);
            }
            return;
        }
        pt.n.putInt("away", 0);
        ensureNpc(sl, pt, AddonEntities.STORY_CORVIN.get(), StoryStage.CHAPEL, CORVIN_DOOR, 180);
        if (!pt.flag("ilsaFled")) ensureNpc(sl, pt, AddonEntities.STORY_ILSA.get(), StoryStage.CHAPEL, new double[]{StoryStage.CAGES[0][0], 0, StoryStage.CAGES[0][1]}, 90);
        if (Story.talking(pt)) return;
        double far = -99;
        for (ServerPlayer p : in) far = Math.max(far, p.getZ() - StoryStage.oz(StoryStage.CHAPEL));
        switch (pt.step) {
            case 0 -> { pt.step = 1; play(s, pt, "chapel_enter"); }
            case 1 -> { if (far >= 13) { pt.step = 2; spawnWave(sl, pt, StoryStage.CHAPEL, 4 + 2 * (in.size() - 1), false); Story.tellAll(s, pt, Component.literal("The ones in the pews stand up. They were never praying.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)); } }
            case 2 -> { if (penitents(sl, pt, StoryStage.CHAPEL).isEmpty()) { pt.step = 3; pt.dirty = true; } }
            case 3 -> { if (far >= 27) { pt.step = 4; play(s, pt, "sallow_meet"); } }
            case 5 -> {
                if (sallow(sl, pt).isEmpty()) spawnSallow(s, sl, pt, in.size());
            }
            case 9 -> { if (pad(s, pt, in, pos(pt, StoryStage.CHAPEL, CHAPEL_PAD), "Leaving the chapel")) finish(s, pt); }
            default -> {}
        }
    }

    static List<FatherSallow> sallow(ServerLevel sl, Story.Party pt) {
        Vec3 c = StoryStage.at(pt.id, StoryStage.CHAPEL, 0, 0, 22);
        return sl.getEntitiesOfClass(FatherSallow.class, new AABB(c, c).inflate(40, 30, 40), b -> b.isAlive() && b.party == pt.id);
    }

    static final UUID SALLOW_GROUP = UUID.fromString("5a110000-0000-4000-8000-000000000002");

    static void spawnSallow(MinecraftServer s, ServerLevel sl, Story.Party pt, int players) {
        FatherSallow b = AddonEntities.FATHER_SALLOW.get().create(sl);
        if (b == null) return;
        Vec3 at = pos(pt, StoryStage.CHAPEL, SALLOW);
        b.moveTo(at.x, at.y, at.z, 180, 0);
        b.setYBodyRot(180); b.setYHeadRot(180);
        b.party = pt.id;
        tag(b, pt, StoryStage.CHAPEL);
        if (players > 1) {   // +60% health for each mage beyond the first, as the ladder's bosses
            var hp = b.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.addPermanentModifier(new AttributeModifier(SALLOW_GROUP, "Sallow: the party", 0.6 * (players - 1), AttributeModifier.Operation.MULTIPLY_BASE));
        }
        b.setHealth(b.getMaxHealth());
        sl.addFreshEntity(b);
        FxSend.ring(sl, Fx.FIRE, at.add(0, 0.1, 0), 0.4f, 4f, 0.1f, 16);
        pt.dirty = true;
    }

    /** Phase 3: Ilsa breaks out - burning - and runs. */
    static void ilsaFlees(FatherSallow b) {
        if (!(b.level() instanceof ServerLevel sl)) return;
        Story.Party pt = Story.byId(sl.getServer(), b.party);
        if (pt == null || pt.flag("ilsaFled")) return;
        pt.set("ilsaFled");
        Story.tellAll(sl.getServer(), pt, Component.literal("Ilsa: ").withStyle(ChatFormatting.GOLD).append(Component.literal("...It's talking to me. The star. It's TALKING to me -").withStyle(ChatFormatting.WHITE)));
        for (StoryNpc n : npcs(sl, pt, AddonEntities.STORY_ILSA.get())) {
            FxSend.ring(sl, Fx.FIRE, n.position().add(0, 0.1, 0), 0.3f, 3f, 0.1f, 14);
            FxSend.beam(sl, Fx.FIRE, n.position(), n.position().add(0, 4, 0), 0.6f, 14);
            n.discard();
        }
        Story.tellAll(sl.getServer(), pt, Component.literal("Ilsa tears through the iron with her bare, burning hands - and is gone.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        pt.note("Ilsa broke out of Sallow's cage, burning, and ran.");
    }

    /** Sallow has yielded: the party decides. */
    static void sallowYielded(FatherSallow b) {
        if (!(b.level() instanceof ServerLevel sl)) return;
        Story.Party pt = Story.byId(sl.getServer(), b.party);
        if (pt == null || pt.step != 5) return;
        for (Penitent m : penitents(sl, pt, StoryStage.CHAPEL)) { FxSend.ring(sl, Fx.FIRE, m.position().add(0, 0.1, 0), 1.2f, 0.2f, 0.05f, 10); m.discard(); }
        pt.step = 6;
        play(sl.getServer(), pt, "sallow_yield", "Spare him", "End him");
    }

    // ================================================================== scenes ended, choices made
    static void onTalkDone(MinecraftServer s, Story.Party pt, String id, int choice) {
        switch (id) {
            case "ilsa" -> { if (pt.ch == 0 && pt.step == 1) { pt.step = 2; pt.target = false; } }
            case "corvin_meet" -> { if (pt.ch == 0 && pt.step == 3) { pt.step = 4; setTarget(pt, s, pos(pt, StoryStage.EMBER, GATE_PAD), Rifts.RIFT); } }
            case "corvin_lesson" -> { if (pt.ch == 1) toChapter(s, pt, 2); }
            case "brand_wakes" -> {
                if (pt.ch != 2) return;
                pt.set("brandWoke");
                Story.raiseBrand(s, pt, 1);
                pt.step = 1;
                for (ServerPlayer p : Story.online(s, pt)) title(p, "BRAND I", "Ember Surge - press X");
                pt.note("The Brand woke: Brand I, Ember Surge.");
            }
            case "corvin_trail" -> { if (pt.ch == 2) toChapter(s, pt, 3); }
            case "sallow_meet" -> { if (pt.ch == 4 && pt.step == 4) pt.step = 5; }
            case "sallow_yield" -> {
                if (pt.ch != 4 || pt.step != 6) return;
                ServerLevel sl = StoryStage.rift(s);
                boolean spare = choice == 0;
                pt.f.putBoolean(spare ? "sallowSpared" : "sallowKilled", true);
                if (sl != null) for (FatherSallow b : sallow(sl, pt)) {
                    if (spare) { FxSend.ring(sl, Fx.FIRE, b.position().add(0, 0.1, 0), 2f, 0.2f, 0.08f, 16); b.discard(); }
                    else b.hurt(b.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
                }
                pt.note(spare ? "You spared Father Sallow. He went into the dark behind the altar." : "You ended Father Sallow.");
                pt.step = 7;
                play(s, pt, spare ? "sallow_spared" : "sallow_killed");
            }
            case "sallow_spared", "sallow_killed" -> {
                if (pt.ch != 4 || pt.step != 7) return;
                var charm = pt.flag("sallowSpared") ? AddonItemRegistry.SALLOW_ROSARY.get() : AddonItemRegistry.SALLOW_HOOK.get();
                for (ServerPlayer p : StoryStage.inside(s, pt, StoryStage.CHAPEL)) give(p, new ItemStack(charm), null);
                pt.step = 8;
                play(s, pt, "arc1_end");
            }
            case "arc1_end" -> { if (pt.ch == 4 && pt.step == 8) { pt.step = 9; setTarget(pt, s, pos(pt, StoryStage.CHAPEL, CHAPEL_PAD), Rifts.RIFT); } }
            default -> {}
        }
        pt.dirty = true;
    }

    /** Out of the chapel: Arc I is done. Everyone who stood in it to the end gets the arc's reward. */
    static void finish(MinecraftServer s, Story.Party pt) {
        for (ServerPlayer p : StoryStage.inside(s, pt, StoryStage.CHAPEL)) {
            GrimoireMastery.bountyXp(p, 3000);
            give(p, BossRules.core(2), null);
            p.sendSystemMessage(Component.literal("ARC I COMPLETE  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("+3000 mastery, a tier II Mana Core.").withStyle(ChatFormatting.YELLOW)));
            StoryStage.leave(p);
        }
        toChapter(s, pt, 5);
    }

    // ================================================================== what the party does
    static void onKill(MinecraftServer s, Story.Party pt, ServerPlayer p, Mob m) {
        if (pt.ch == 1) {
            if (m.level().isNight()) pt.add("night", 1);
            if (EliteMobs.elite(m) && !EliteMobs.ghost(m)) pt.add("elite", 1);
        }
    }

    static void onCast(MinecraftServer s, Story.Party pt, ServerPlayer p) { if (pt.ch == 1) pt.add("cast", 1); }

    static void onBrand(MinecraftServer s, Story.Party pt, ServerPlayer p, boolean fight) { if (pt.arc == 1 && pt.ch == 2 && pt.step == 1 && fight) pt.add("brand", 1); }

    /** Any mob's death: a patrol's last Penitent drops a page. */
    static void onDeath(Mob m, ServerPlayer by) {
        if (!(m instanceof Penitent) || !(m.level() instanceof ServerLevel sl)) return;
        int id = m.getPersistentData().getInt("isg_patrol");
        if (id <= 0) return;
        Story.Party pt = Story.byId(sl.getServer(), m.getPersistentData().getInt("isg_story"));
        if (pt == null || pt.ch != 2) return;
        boolean rest = !sl.getEntitiesOfClass(Penitent.class, m.getBoundingBox().inflate(64), o -> o != m && o.isAlive() && o.getPersistentData().getInt("isg_patrol") == id
                && o.getPersistentData().getInt("isg_story") == pt.id).isEmpty();
        if (rest) return;
        int page = nextPage(pt);
        if (page < 0) return;
        pt.set("dropped" + page);
        m.spawnAtLocation(AshenPageItem.of(page));
        if (by != null) by.displayClientMessage(Component.literal("The last of them dropped something: a scorched page.").withStyle(ChatFormatting.GOLD), true);
    }

    static void onFall(ServerPlayer p, int[] where) {
        p.sendSystemMessage(Component.literal("You were thrown out of " + StoryStage.NAMES[where[1]] + ". Your Journal takes you back.").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
    }

    /** The Journal's "go back": into the story place the chapter is in. */
    static void enterRequest(MinecraftServer s, Story.Party pt, ServerPlayer p) {
        if (StoryStage.inStage(p)) return;
        if (pt.ch == 0) StoryStage.enter(p, pt, StoryStage.EMBER, pos(pt, StoryStage.EMBER, EMBER_SPAWN), 180);
        else if (pt.ch == 4) StoryStage.enter(p, pt, StoryStage.CHAPEL, pos(pt, StoryStage.CHAPEL, CHAPEL_SPAWN), 0);
    }

    // ================================================================== the people
    public static void talkTo(ServerPlayer p, StoryNpc npc) {
        Story.Party pt = Story.partyOf(p);
        if (pt == null || npc.party != pt.id) { p.displayClientMessage(Component.literal(npc.who.equals("corvin") ? "Corvin looks past you. He's waiting for someone else." : "She doesn't seem to see you.").withStyle(ChatFormatting.GRAY), true); return; }
        MinecraftServer s = p.server;
        if (Story.talking(pt)) return;
        if (pt.arc == 2) { StoryArc2.talkTo(p, pt, npc); return; }
        if (pt.arc == 3) { StoryArc3.talkTo(p, pt, npc); return; }
        if (npc.who.equals("ilsa")) {
            if (pt.ch == 0 && pt.step == 0) { pt.step = 1; play(s, pt, "ilsa"); }
            else p.sendSystemMessage(Component.literal("Ilsa: ").withStyle(ChatFormatting.GOLD).append(Component.literal(pt.ch == 4 ? "Get me out. Please. Before he comes back." : "Stay close. Please.").withStyle(ChatFormatting.WHITE)));
            return;
        }
        if (pt.ch == 1 && goalsMet(pt)) { play(s, pt, "corvin_lesson"); return; }
        if (pt.ch == 2 && pt.step == 0 && pt.count("pages") >= 3) { play(s, pt, "brand_wakes"); return; }
        p.sendSystemMessage(corvinLine(hint(pt)));
    }

    static String hint(Story.Party pt) {
        return switch (pt.ch) {
            case 0 -> "Out. Through the gate. Don't look back at it.";
            case 1 -> "Cast until it's in your bones. Hunt at night - the things that come out then are honest about wanting you dead. And find me an elite.";
            case 2 -> pt.step == 0 ? "Sallow's patrols carry their filth written down. Bring me three pages." : "Use it in a fight - X - and keep your head while it burns.";
            case 3 -> "The ash is falling thicker that way. Follow the marker.";
            case 4 -> "I'll hold the door. Go.";
            default -> "Rest. The Trials will come soon enough.";
        };
    }

    static boolean goalsMet(Story.Party pt) {
        return pt.ch == 1 && pt.count("cast") >= 40 && pt.count("night") >= 12 && pt.count("elite") >= 1;
    }

    /** Corvin walks with the leader once he's met them (outside the story's places). */
    static void follow(MinecraftServer s, Story.Party pt) {
        if (pt.arc == 1 && pt.ch < 1 || s.getTickCount() % 40 != 0) return;
        ServerPlayer l = Story.lead(s, pt);
        if (l == null || StoryStage.inStage(l) || Rifts.riftOf(l) != null || l.isSpectator()) return;
        ServerLevel sl = l.serverLevel();
        Entity c = pt.corvin == null ? null : sl.getEntity(pt.corvin);
        if (c instanceof StoryNpc && c.isAlive()) return;
        StoryNpc n = AddonEntities.STORY_CORVIN.get().create(sl);
        if (n == null) return;
        Vec3 at = l.position().add(Vec3.directionFromRotation(0, l.getYRot() + 150).scale(3));
        n.moveTo(at.x, l.getY(), at.z, l.getYRot(), 0);
        n.party = pt.id; n.follower = true;
        sl.addFreshEntity(n);
        pt.corvin = n.getUUID();
        Story.dirty(s);
    }

    static void removeCorvin(MinecraftServer s, Story.Party pt) {
        if (pt.corvin == null) return;
        for (ServerLevel sl : s.getAllLevels()) { Entity c = sl.getEntity(pt.corvin); if (c != null) c.discard(); }
        pt.corvin = null;
    }

    /** Each tick of a story person: Corvin keeps up with the leader; a stale one (another's replaced it) goes. */
    public static void npcTick(StoryNpc n) {
        if (!(n.level() instanceof ServerLevel sl) || n.party == 0) return;   // (0: one stood up by hand, belonging to no story)
        Story.Party pt = Story.byId(sl.getServer(), n.party);
        if (pt == null) { n.discard(); return; }
        if (!n.follower) return;
        if (pt.arc == 1 && pt.ch < 1 || !n.getUUID().equals(pt.corvin)) { n.discard(); return; }
        ServerPlayer l = Story.lead(sl.getServer(), pt);
        if (l == null || l.level() != n.level()) { n.getNavigation().stop(); return; }
        double d = n.distanceTo(l);
        if (d > 24) { Vec3 at = l.position().add(Vec3.directionFromRotation(0, l.getYRot() + 150).scale(3)); n.teleportTo(at.x, l.getY(), at.z); n.getNavigation().stop(); }
        else if (d > 5) n.getNavigation().moveTo(l, 1.15);
        else n.getNavigation().stop();
    }

    public static void penitentTick(Penitent m) {
        if (m.getTarget() instanceof ServerPlayer p && ThreadLocalRandom.current().nextInt(40) == 0 && m.distanceTo(p) < 10)
            p.displayClientMessage(Component.literal(new String[]{"\"Be freed.\"", "\"The mark is a sickness.\"", "\"Father will take it out of you.\"", "\"Hold still. It's mercy.\""}[ThreadLocalRandom.current().nextInt(4)]).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
    }

    static StoryNpc ensureNpc(ServerLevel sl, Story.Party pt, EntityType<StoryNpc> type, int place, double[] at, float yaw) {
        for (StoryNpc n : npcs(sl, pt, type)) if (StoryStage.at(pt.id, place, 0, 0, 0).distanceTo(n.position()) < 120) return n;
        StoryNpc n = type.create(sl);
        if (n == null) return null;
        Vec3 p = pos(pt, place, at);
        n.moveTo(p.x, p.y, p.z, yaw, 0);
        n.setYBodyRot(yaw); n.setYHeadRot(yaw);
        n.party = pt.id;
        tag(n, pt, place);
        sl.addFreshEntity(n);
        return n;
    }

    static List<StoryNpc> npcs(ServerLevel sl, Story.Party pt, EntityType<StoryNpc> type) {
        List<StoryNpc> out = new ArrayList<>();
        for (int place = 0; place <= StoryStage.DEEP; place++) {
            Vec3 c = StoryStage.centre(pt.id, place);
            out.addAll(sl.getEntitiesOfClass(StoryNpc.class, new AABB(c, c).inflate(60, 30, 60), n -> n.getType() == type && n.party == pt.id && !n.follower && n.isAlive()));
        }
        return out;
    }

    /** Takes everything a scene put in the story's places away (they're put back as the story needs them). */
    static void clearScenes(MinecraftServer s, Story.Party pt) {
        ServerLevel sl = StoryStage.rift(s);
        if (sl == null) return;
        for (int place = 0; place <= StoryStage.DEEP; place++) {
            Vec3 c = StoryStage.centre(pt.id, place);
            for (Entity e : sl.getEntitiesOfClass(Entity.class, new AABB(c, c).inflate(60, 30, 60), e -> e.getPersistentData().getInt("isg_story") == pt.id && !(e instanceof ServerPlayer))) e.discard();
        }
    }

    // ================================================================== the Journal
    static void journal(MinecraftServer s, Story.Party pt, CompoundTag t) {
        t.putString("arcTitle", ARC);
        int ch = Math.max(-1, Math.min(5, pt.ch));
        t.putString("chTitle", ch < 0 ? "Not yet begun" : ch >= 5 ? "Arc I complete" : "Chapter " + (ch + 1) + " - " + TITLES[ch]);
        t.putString("chText", ch < 0 ? "A star is going to fall. When you're ready, begin." : TEXT[ch]);
        ListTag goals = new ListTag();
        for (Object[] g : goals(pt)) {
            CompoundTag x = new CompoundTag();
            x.putString("t", (String) g[0]); x.putInt("h", (Integer) g[1]); x.putInt("n", (Integer) g[2]);
            goals.add(x);
        }
        t.put("goals", goals);
        ListTag pages = new ListTag();
        for (int i = 0; i < PAGES.length; i++) if (pt.flag("page" + i)) {
            CompoundTag x = new CompoundTag();
            x.putInt("i", i); x.putString("t", PAGE_TITLES[i]); x.putString("b", PAGES[i]);
            pages.add(x);
        }
        t.put("pages", pages);
        if (pt.ch == 0) t.putString("enter", "Return to Emberfall");
        if (pt.ch == 4) t.putString("enter", "Return to the chapel");
        if (pt.talk != null && pt.talk.choices.length > 0) t.putString("voting", pt.talk.id);
    }

    /** The chapter's goals as the Journal and tracker show them: {text, have, need}. need 0: a plain step, not yet done. */
    static List<Object[]> goals(Story.Party pt) {
        List<Object[]> g = new ArrayList<>();
        switch (pt.ch) {
            case 0 -> {
                g.add(new Object[]{"Find who's left in the village", pt.step >= 1 ? 1 : 0, 1});
                if (pt.step >= 2) g.add(new Object[]{"Hold off Sallow's Penitents", Math.min(3, pt.count("wave")) + (pt.step >= 3 ? 1 : 0), 4});
                if (pt.step >= 4) g.add(new Object[]{"Leave Emberfall with Corvin: stand in the circle at the gate", 0, 1});
            }
            case 1 -> {
                g.add(new Object[]{"Cast spells", Math.min(40, pt.count("cast")), 40});
                g.add(new Object[]{"Hunt by night", Math.min(12, pt.count("night")), 12});
                g.add(new Object[]{"Bring down an elite", Math.min(1, pt.count("elite")), 1});
                if (goalsMet(pt)) g.add(new Object[]{"Speak with Corvin", 0, 1});
            }
            case 2 -> {
                g.add(new Object[]{"Take Ashen Pages from Sallow's patrols", Math.min(3, pt.count("pages")), 3});
                if (pt.count("pages") >= 3 && pt.step == 0) g.add(new Object[]{"Speak with Corvin", 0, 1});
                if (pt.step >= 1) g.add(new Object[]{"Use the Brand in a fight (X)", Math.min(3, pt.count("brand")), 3});
            }
            case 3 -> g.add(new Object[]{"Follow the trail of ash to Sallow's chapel", 0, 1});
            case 4 -> {
                g.add(new Object[]{"Walk the nave", pt.step >= 3 ? 1 : 0, 1});
                if (pt.step >= 3) g.add(new Object[]{"Reach the altar", pt.step >= 5 ? 1 : 0, 1});
                if (pt.step >= 5) g.add(new Object[]{"Bring Father Sallow to his knees", pt.step >= 6 ? 1 : 0, 1});
                if (pt.step >= 6) g.add(new Object[]{"Decide what's done with him", pt.step >= 7 ? 1 : 0, 1});
                if (pt.step >= 9) g.add(new Object[]{"Leave the chapel: stand in the circle by the door", 0, 1});
            }
            default -> {}
        }
        return g;
    }

    // ================================================================== for the tests
    static void testWaveClear(MinecraftServer s, Story.Party pt) {
        ServerLevel sl = StoryStage.rift(s);
        if (sl != null) for (Penitent m : penitents(sl, pt, pt.ch == 0 ? StoryStage.EMBER : StoryStage.CHAPEL)) m.discard();
    }
    static void testStep(Story.Party pt, int step) { pt.step = step; pt.dirty = true; }
}
