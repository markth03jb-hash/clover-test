package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.StoryArc1.give;
import static com.ironspellgrimoire.server.StoryArc1.tag;
import static com.ironspellgrimoire.server.StoryArc1.title;

import com.ironspellgrimoire.entity.Militia;
import com.ironspellgrimoire.entity.MotherBelow;
import com.ironspellgrimoire.entity.StoryNpc;
import com.ironspellgrimoire.entity.WildFolk;
import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ASHFALL, Arc III: The Hollow War. Something under the world has woken; the wild families flee the surface and the
 * Hollowfolk come up behind them.
 * <ol start="0">
 *   <li><b>Exodus:</b> a Mirefolk caravan under attack on Ashwick's south road; Old Sedge; Reeve Maren at the gate.</li>
 *   <li><b>Holding the Brand:</b> three fights won with the Brand burning, never below half health. Its price halves.</li>
 *   <li><b>The Tunnels:</b> down the well, three hives in the dark - burn them out with the Brand.</li>
 *   <li><b>The Siege:</b> five waves out of the pits at night; the militia and Corvin on the wall with you.</li>
 *   <li><b>The Mother Below:</b> the queen in the deep cavern. At half health she tears the floor open and goes down.</li>
 *   <li><b>The Choice:</b> the party votes - hold Ashwick, or go down for the Mirefolk trapped below. She comes back
 *       wherever you stand.</li>
 *   <li><b>Aftermath:</b> what's left; out.</li>
 *   <li>Arc III complete.</li>
 * </ol>
 */
public final class StoryArc3 {
    private StoryArc3() {}

    public static final String ARC = "ARC III - THE HOLLOW WAR";
    public static final String[] TITLES = {"Exodus", "Holding the Brand", "The Tunnels", "The Siege", "The Mother Below", "The Choice", "Aftermath", "The Hollow War"};
    static final String[] TEXT = {
            "The wild families are fleeing the surface - Mirefolk, Stonefolk, Rimefolk, all on the roads, all going away from the deep places. A Mirefolk caravan is under attack on the road to Ashwick.",
            "Brand II burns hot and bleeds you while it does. Corvin wants you to hold it: three fights won with it burning, without ever falling below half your health.",
            "The ground under Ashwick is hollow. Old Sedge knows the way down - the old well. Something is growing in the tunnels. Burn it out.",
            "Night. The pits north of the village are full of voices. The Hollowfolk are coming up - all of them. Hold Ashwick.",
            "The ground under the village has split, and something enormous is singing at the bottom of it. Go down to the deep cavern.",
            "The cavern floor is gone. The Mirefolk who sheltered in the tunnels are trapped below - and the Hollowfolk are pouring up toward Ashwick.",
            "It's over. Count what's left.",
            "The Mother Below is dead. The deep is quiet. The Brand is quiet too - for now.\n\nARC IV - THE BRAND AWAKENS comes in a later update."};

    // ================================================================== the scenes
    static List<String[]> scene(String id) {
        return switch (id) {
            case "exodus_arrive" -> Story.lines(
                    "narrator", "The road to Ashwick is full of people walking the wrong way. Mirefolk, mostly - bog-green, moss-bearded, carrying everything they own on their backs.",
                    "sedge", "You! You with the light in your arm - help us! They're in the carts! They came up out of the ground!",
                    "corvin", "Hollowfolk. On the surface, in daylight. That's never happened.");
            case "sedge_meet" -> Story.lines(
                    "sedge", "Old Sedge, they call me. Elder of what's left of the Fenwater clans. We walked six days from the marsh.",
                    "sedge", "The ground is singing down there. The Hollowfolk heard it first. Then the stone ones, then the frost ones. Everything that lives underneath is coming up.",
                    "sedge", "The village. Ashwick. They have walls. Take us to the gate - please.");
            case "maren_gate" -> Story.lines(
                    "maren", "That's far enough. I'm the Reeve here. Maren.",
                    "maren", "Mirefolk. Forty of you, and you eat like eighty. And whatever's chasing you, you've brought it to my door.",
                    "corvin", "It's coming either way, Reeve. Better with forty more hands on your wall.",
                    "maren", "...Outside the walls. You camp outside the south gate, and you don't come in after dark. That's my offer.",
                    "sedge", "We'll take it. Bless you, Reeve. In our way.");
            case "brand_held" -> Story.lines(
                    "corvin", "Three fights, and you never went under. You held it.",
                    "corvin", "Feel that? It still takes - but less. You're feeding it, instead of it feeding on you.",
                    "corvin", "That's all I ever learned to do with mine. I hope you go further.");
            case "tunnels_done" -> Story.lines(
                    "sedge", "Three hives. Burnt. That will hurt them. But listen - do you hear it? Under the hives. Under everything.",
                    "sedge", "In the oldest songs we call her the Mother Below. Every Hollow you have ever killed came out of her.",
                    "corvin", "And now she knows our scent. Back up. Get Maren's people on the wall. They'll come tonight.");
            case "siege_begins" -> Story.lines(
                    "maren", "Every pitchfork on the north wall! Torches - no, no torches, they come to the light - just hold!",
                    "corvin", "Five years I've waited to stand on a wall again. Let's make it worth the wait.",
                    "narrator", "The pits to the north begin to glow.");
            case "siege_won" -> Story.lines(
                    "narrator", "The last of them falls back into the pits. The militia cheer - and stop, all at once.",
                    "narrator", "The ground is moving. Not shaking. Breathing.",
                    "maren", "The well. Something came up the well -",
                    "corvin", "No. Something is pulling the village DOWN. Go - go now, get under it before it brings the whole of Ashwick into the dark!");
            case "mother_wakes" -> Story.lines(
                    "narrator", "The deep cavern. A dome of black stone, lit from below by veins of teal light. In the middle of it, something unfolds.",
                    "mother", "Little sparks. You burnt my children. You burnt my hives. You carry a piece of the thing that fell from the sky.",
                    "mother", "I will keep it for you. Down here. Forever.");
            case "mother_retreats" -> Story.lines(
                    "narrator", "She drives her limbs into the floor, and the floor gives way. Stone falls into a dark that has no bottom you can see.",
                    "sedge", "NO - the lower tunnels! My people are in the lower tunnels! She's taken the floor out from under them!",
                    "maren", "And up top they're coming out of every pit at once! Ashwick won't last without you - not without you!",
                    "corvin", "One way or the other. We can't be in two places. Choose.");
            case "the_choice" -> Story.lines(
                    "corvin", "Hold the wall, and the village lives - and the Mirefolk below die in the dark. Go down after them, and Ashwick burns while we're gone.",
                    "corvin", "Either way she comes for you. Decide where you want to meet her.");
            case "hold_up" -> Story.lines(
                    "narrator", "You climb. Behind you, under you, someone is calling Sedge's name, again and again, until they don't.",
                    "maren", "You came back. You came back! North wall - they're coming over the north wall!");
            case "go_down" -> Story.lines(
                    "narrator", "You jump. The dark takes you down, and down, and you land in a lower cavern lit by cages of Mirefolk and the Mother's light.",
                    "sedge", "Here! We're here! Get us out - she's coming back!");
            case "aftermath_hold" -> Story.lines(
                    "narrator", "Ashwick stands. Half its roofs are gone, and its north wall is a line of black stumps, but it stands.",
                    "maren", "You saved my village. I'll not forget it. ...I'll not forget the other thing either. Forty of them. Down there.",
                    "maren", "Sedge's reed - one of his people pressed it into my hand before they went down. I think he'd want you to have it. No. I don't think that. I'm giving it to you anyway.",
                    "narrator", "ARC III - THE HOLLOW WAR - COMPLETE.");
            case "aftermath_down" -> Story.lines(
                    "narrator", "You bring them up through the tunnels at dawn: forty Mirefolk, alive. Ashwick is smoke. The Reeve sits on what is left of her well.",
                    "sedge", "We owe you everything. My people will not forget the ones who came down.",
                    "maren", "...They burnt it. All of it. I don't know whether to thank you or never look at you again.",
                    "corvin", "Both. She'll do both. And she'll be right to.",
                    "narrator", "ARC III - THE HOLLOW WAR - COMPLETE.");
            default -> Story.lines("narrator", "...");
        };
    }

    static void play(MinecraftServer s, Story.Party pt, String id, String... choices) { Story.talk(s, pt, id, scene(id), choices); }
    static Vec3 pos(Story.Party pt, int place, double[] a) { return StoryStage.at(pt.id, place, a[0], a[1], a[2]); }

    // ================================================================== chapters
    static void begin(MinecraftServer s, Story.Party pt) {
        StoryArc1.clearScenes(s, pt);
        pt.arc = 3; pt.ch = 0; pt.step = 0; pt.n = new CompoundTag(); pt.talk = null; pt.target = false;
        pt.note("ARC III - THE HOLLOW WAR began.");
        for (ServerPlayer p : Story.online(s, pt)) {
            give(p, new ItemStack(AddonItemRegistry.STORY_JOURNAL.get()), AddonItemRegistry.STORY_JOURNAL.get());
            StoryStage.enter(p, pt, StoryStage.ASHWICK, StoryStage.spawn(pt.id, StoryStage.ASHWICK), 180);
            title(p, "THE HOLLOW WAR", "Chapter 1 - " + TITLES[0]);
        }
        pt.dirty = true;
    }

    static void toChapter(MinecraftServer s, Story.Party pt, int ch) {
        int was = pt.ch;
        StoryArc1.clearScenes(s, pt);
        pt.arc = 3; pt.ch = ch; pt.step = 0; pt.n = new CompoundTag(); pt.talk = null; pt.target = false;
        if (ch != was && ch > 0) {
            for (ServerPlayer p : Story.online(s, pt)) title(p, ch >= 7 ? "ARC III COMPLETE" : "Chapter " + (ch + 1), ch >= 7 ? "The Hollow War" : TITLES[ch]);
            pt.note(ch >= 7 ? "ARC III - THE HOLLOW WAR - complete." : "Chapter " + (ch + 1) + ": " + TITLES[ch] + ".");
        }
        pt.dirty = true;
    }

    static void restartChapter(MinecraftServer s, Story.Party pt) {
        StoryArc1.clearScenes(s, pt);
        int held = pt.count("held");
        pt.step = 0; pt.n = new CompoundTag(); pt.talk = null;
        if (pt.ch == 1) pt.n.putInt("held", held);
        if (pt.ch == 5 && (pt.flag("hold") || pt.flag("down"))) pt.step = 1;
        pt.dirty = true;
    }

    /** Where the chapter is (the place it happens in). */
    static int place(Story.Party pt) {
        return switch (pt.ch) {
            case 2 -> StoryStage.TUNNELS;
            case 4 -> StoryStage.DEEP;
            case 5, 6 -> pt.flag("down") ? StoryStage.DEEP : pt.flag("hold") ? StoryStage.ASHWICK : StoryStage.DEEP;
            default -> StoryStage.ASHWICK;
        };
    }

    // ================================================================== the tick
    static void tick(MinecraftServer s, Story.Party pt, List<ServerPlayer> on) {
        if (pt.ch < 0 || pt.ch >= 7) return;
        ServerLevel sl = StoryStage.rift(s);
        if (sl == null) return;
        long now = s.overworld().getGameTime();
        well(s, pt);
        if (pt.ch == 1) return;   // anywhere: the fights are the party's own
        int place = place(pt);
        List<ServerPlayer> in = StoryStage.inside(s, pt, place);
        if (in.isEmpty()) {
            pt.add("away", 1);
            if (pt.count("away") > 30) { abandon(sl, pt); pt.n.putInt("away", 0); }
            return;
        }
        pt.n.putInt("away", 0);
        people(sl, pt, place);
        if (Story.talking(pt)) return;
        switch (pt.ch) {
            case 0 -> exodus(s, sl, pt, in, now);
            case 2 -> hives(s, sl, pt, in);
            case 3 -> siege(s, sl, pt, in, now);
            case 4 -> deep(s, sl, pt, in);
            case 5 -> choice(s, sl, pt, in, now);
            case 6 -> { Vec3 pad = pos(pt, place, place == StoryStage.ASHWICK ? StoryStage3.GATE : EXIT_DEEP); if (StoryArc1.pad(s, pt, in, pad, "Leaving")) finish(s, pt); }
            default -> {}
        }
    }

    /** Down the well: into the tunnels (from the chapter that sends you there on). */
    static void well(MinecraftServer s, Story.Party pt) {
        if (pt.ch != 2) return;
        Vec3 w = pos(pt, StoryStage.ASHWICK, StoryStage3.WELL);
        for (ServerPlayer p : StoryStage.inside(s, pt, StoryStage.ASHWICK))
            if (Math.hypot(p.getX() - w.x, p.getZ() - w.z) < 0.9 && p.getY() < w.y - 1.5) StoryStage.enter(p, pt, StoryStage.TUNNELS, StoryStage.spawn(pt.id, StoryStage.TUNNELS), 180);
    }

    static void abandon(ServerLevel sl, Story.Party pt) {
        for (int place : new int[]{StoryStage.ASHWICK, StoryStage.TUNNELS, StoryStage.DEEP}) {
            Vec3 c = StoryStage.centre(pt.id, place);
            for (Entity e : sl.getEntitiesOfClass(Entity.class, new AABB(c, c).inflate(60, 30, 60), e -> e.getPersistentData().getInt("isg_bout") == pt.id)) e.discard();
        }
        switch (pt.ch) {
            case 0 -> { if (pt.step == 1) pt.n.putInt("wave", 0); }
            case 2 -> { for (int i = 0; i < 3; i++) if (!pt.n.getBoolean("hive" + i + "burnt")) pt.n.putBoolean("hive" + i + "woke", false); }
            case 3 -> { if (pt.step >= 1) { pt.step = 1; pt.n.putInt("wave", 0); pt.n.putLong("next", 0); } }
            case 4 -> { if (pt.step == 1) pt.n.putBoolean("up", false); }
            case 5 -> { if (pt.step >= 1) { pt.step = 1; pt.n.putBoolean("up", false); pt.n.putInt("surge", 0); } }
            default -> {}
        }
        pt.dirty = true;
    }

    /** Who stands where, in each chapter. */
    static void people(ServerLevel sl, Story.Party pt, int place) {
        if (place == StoryStage.ASHWICK) {
            StoryArc1.ensureNpc(sl, pt, AddonEntities.STORY_CORVIN.get(), place, StoryStage3.CORVIN, 180);
            if (pt.ch >= 0) StoryArc1.ensureNpc(sl, pt, AddonEntities.STORY_MAREN.get(), place, StoryStage3.MAREN, 180);
            if (pt.ch <= 3) {
                StoryArc1.ensureNpc(sl, pt, AddonEntities.STORY_SEDGE.get(), place, StoryStage3.SEDGE, 180);
                double[][] camp = new double[3][];
                for (int i = 0; i < 3; i++) camp[i] = new double[]{StoryStage3.CAMP[i][0] + 2, 0, StoryStage3.CAMP[i][1]};
                crowd(sl, pt, place, camp);
            }
        } else if (place == StoryStage.TUNNELS) {
            StoryArc1.ensureNpc(sl, pt, AddonEntities.STORY_SEDGE.get(), place, new double[]{2, 0, 1}, 180);
        } else {
            StoryArc1.ensureNpc(sl, pt, AddonEntities.STORY_CORVIN.get(), place, new double[]{2, 0, 20}, 180);
        }
    }

    /** Mirefolk refugees: one stood at each spot in the place (put back if they're gone). */
    static void crowd(ServerLevel sl, Story.Party pt, int place, double[][] spots) {
        Vec3 c = StoryStage.centre(pt.id, place);
        List<StoryNpc> have = sl.getEntitiesOfClass(StoryNpc.class, new AABB(c, c).inflate(60, 30, 60), n -> n.getType() == AddonEntities.STORY_REFUGEE.get() && n.party == pt.id && n.isAlive());
        for (int i = have.size(); i < spots.length; i++) {
            StoryNpc n = AddonEntities.STORY_REFUGEE.get().create(sl);
            if (n == null) return;
            Vec3 at = pos(pt, place, spots[i]);
            float yaw = ThreadLocalRandom.current().nextInt(360);
            n.moveTo(at.x, at.y, at.z, yaw, 0);
            n.setYBodyRot(yaw); n.setYHeadRot(yaw);
            n.party = pt.id;
            tag(n, pt, place);
            sl.addFreshEntity(n);
        }
    }

    static void spawnHollow(ServerLevel sl, Story.Party pt, int place, Vec3 at, WildFolk.Kind k) {
        WildFolk m = WildFolkAI.type(k).create(sl);
        if (m == null) return;
        m.moveTo(at.x, at.y, at.z, ThreadLocalRandom.current().nextFloat() * 360, 0);
        m.setPersistenceRequired();
        tag(m, pt, place);
        m.getPersistentData().putInt("isg_bout", pt.id);
        sl.addFreshEntity(m);
        FxSend.ring(sl, Fx.ICE, at.add(0, 0.1, 0), 0.2f, 1.3f, 0.05f, 10);
    }

    static List<Mob> foes(ServerLevel sl, Story.Party pt) {
        List<Mob> out = new ArrayList<>();
        for (int place : new int[]{StoryStage.ASHWICK, StoryStage.TUNNELS, StoryStage.DEEP}) {
            Vec3 c = StoryStage.centre(pt.id, place);
            out.addAll(sl.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(60, 30, 60), m -> m.isAlive() && m.getPersistentData().getInt("isg_bout") == pt.id && !(m instanceof Militia)
                    && !(m instanceof MotherBelow)));
        }
        return out;
    }

    // ---- chapter 1: the road
    static void exodus(MinecraftServer s, ServerLevel sl, Story.Party pt, List<ServerPlayer> in, long now) {
        switch (pt.step) {
            case 0 -> { pt.step = 1; play(s, pt, "exodus_arrive"); }
            case 1 -> {
                if (!foes(sl, pt).isEmpty()) return;
                int wave = pt.count("wave");
                if (wave >= 2) { pt.step = 2; play(s, pt, "sedge_meet"); return; }
                pt.add("wave", 1);
                int n = (wave == 0 ? 3 : 4) + (in.size() - 1);
                ThreadLocalRandom r = ThreadLocalRandom.current();
                for (int i = 0; i < n; i++) {
                    double a = r.nextDouble(Math.PI * 2);
                    Vec3 at = pos(pt, StoryStage.ASHWICK, new double[]{Math.cos(a) * 9, 0, 30 + Math.sin(a) * 6});
                    spawnHollow(sl, pt, StoryStage.ASHWICK, at, wave == 1 && i < 2 ? WildFolk.Kind.HOLLOW_SINGER : WildFolk.Kind.HOLLOW_CRAWLER);
                }
            }
            case 3 -> {
                Vec3 g = pos(pt, StoryStage.ASHWICK, StoryStage3.GATE);
                for (ServerPlayer p : in) if (p.position().distanceTo(g) < 3.5) { pt.step = 4; play(s, pt, "maren_gate"); return; }
            }
            default -> {}
        }
    }

    /** Chapter 2: a fight won with the Brand burning, never below half health (Brand reports it). */
    static void onBrandHeld(ServerPlayer p) {
        Story.Party pt = Story.partyOf(p);
        if (pt == null || pt.arc != 3 || pt.ch != 1 || pt.flag("brandHeld")) return;
        pt.add("held", 1);
        p.displayClientMessage(Component.literal("You held the Brand through that fight (" + Math.min(3, pt.count("held")) + "/3).").withStyle(ChatFormatting.GOLD), true);
        if (pt.count("held") >= 3 && !Story.talking(pt)) play(p.server, pt, "brand_held");
    }

    // ---- chapter 3: the hives
    static void hives(MinecraftServer s, ServerLevel sl, Story.Party pt, List<ServerPlayer> in) {
        if (pt.count("hives") >= 3) { play(s, pt, "tunnels_done"); return; }
        for (int i = 0; i < StoryStage3.HIVES.length; i++) {
            String k = "hive" + i;
            if (pt.n.getBoolean(k + "burnt")) continue;
            Vec3 c = pos(pt, StoryStage.TUNNELS, new double[]{StoryStage3.HIVES[i][0], 0, StoryStage3.HIVES[i][1]});
            boolean someone = false;
            for (ServerPlayer p : in) if (p.position().distanceTo(c) < 7) someone = true;
            if (!someone) continue;
            if (!pt.n.getBoolean(k + "woke")) {
                pt.n.putBoolean(k + "woke", true);
                for (int j = 0; j < 3 + (in.size() - 1); j++) {
                    double a = j * 2.1;
                    spawnHollow(sl, pt, StoryStage.TUNNELS, c.add(Math.cos(a) * 4, 0, Math.sin(a) * 4), j == 0 ? WildFolk.Kind.HOLLOW_SINGER : WildFolk.Kind.HOLLOW_CRAWLER);
                }
                Story.tellAll(s, pt, Component.literal("The hive wakes. Its brood comes out of the walls.").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
                continue;
            }
            int ii = i;
            boolean guarded = !sl.getEntitiesOfClass(WildFolk.class, new AABB(c, c).inflate(9, 4, 9), m -> m.isAlive() && m.getPersistentData().getInt("isg_bout") == pt.id).isEmpty();
            if (guarded) continue;
            for (ServerPlayer p : in) {
                if (p.position().distanceTo(c) > 3.6) continue;
                if (!Brand.burning(p)) {
                    if (s.getTickCount() % 40 == 0) p.displayClientMessage(Component.literal("Burn it out - let the Brand burn and stand in the hive's heart.").withStyle(ChatFormatting.GOLD), true);
                    continue;
                }
                int t = pt.n.getInt(k + "t") + 1;
                pt.n.putInt(k + "t", t);
                FxSend.ring(sl, Fx.FIRE, c.add(0, 1, 0), 0.4f, 2.6f, 0.08f, 10);
                if (t >= 4) {
                    pt.n.putBoolean(k + "burnt", true);
                    pt.add("hives", 1);
                    StoryStage3.hiveCore(sl, (int) Math.floor(c.x), (int) Math.floor(c.z), true);
                    FxSend.beam(sl, Fx.FIRE, c, c.add(0, 6, 0), 0.8f, 16);
                    sl.playSound(null, net.minecraft.core.BlockPos.containing(c), net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.PLAYERS, 1.6f, 0.5f);
                    Story.tellAll(s, pt, Component.literal("The hive burns out (" + pt.count("hives") + "/3).").withStyle(ChatFormatting.GOLD));
                    GrimoireMastery.bountyXp(p, 400);
                    pt.note("A hive under Ashwick burnt out.");
                }
                break;
            }
            pt.dirty = true;
        }
    }

    // ---- chapter 4: the siege
    static final int[] SIEGE = {3, 4, 5, 4, 5};
    /** The way out of the deep, after (by the tunnel the party came down). */
    static final double[] EXIT_DEEP = {-3, 0, 20};

    static void siege(MinecraftServer s, ServerLevel sl, Story.Party pt, List<ServerPlayer> in, long now) {
        switch (pt.step) {
            case 0 -> { pt.step = 1; play(s, pt, "siege_begins"); }
            case 1 -> {
                militia(sl, pt, 4);
                corvinStrikes(sl, pt, now);
                if (!foes(sl, pt).isEmpty()) return;
                int wave = pt.count("wave");
                if (wave >= SIEGE.length) { pt.step = 2; play(s, pt, "siege_won"); return; }
                long next = pt.n.getLong("next");
                if (next == 0) { pt.n.putLong("next", now + (wave == 0 ? 20 : 80)); return; }
                if (now < next) return;
                pt.n.putLong("next", 0);
                pt.add("wave", 1);
                Story.tellAll(s, pt, Component.literal("WAVE " + (wave + 1) + " OF 5  ").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD)
                        .append(Component.literal(wave >= 3 ? "A Deepwarden walks with them." : "Out of the pits.").withStyle(ChatFormatting.GRAY)));
                ThreadLocalRandom r = ThreadLocalRandom.current();
                int n = SIEGE[wave] + 2 * (in.size() - 1);
                for (int i = 0; i < n; i++) {
                    int[] pit = StoryStage3.PITS[i % StoryStage3.PITS.length];
                    Vec3 at = pos(pt, StoryStage.ASHWICK, new double[]{pit[0] + r.nextDouble(-1, 1), 0, pit[1] + 3});
                    spawnHollow(sl, pt, StoryStage.ASHWICK, at, i % 3 == 2 ? WildFolk.Kind.HOLLOW_SINGER : WildFolk.Kind.HOLLOW_CRAWLER);
                }
                if (wave >= 3) for (int i = 0; i < (wave == 4 ? 2 : 1); i++) spawnHollow(sl, pt, StoryStage.ASHWICK, pos(pt, StoryStage.ASHWICK, new double[]{i * 6 - 3, 0, -27}), WildFolk.Kind.DEEPWARDEN);
                pt.dirty = true;
            }
            default -> {}
        }
    }

    /** Ashwick's militia on the north side, topped back up to n. */
    static void militia(ServerLevel sl, Story.Party pt, int n) {
        Vec3 c = StoryStage.centre(pt.id, StoryStage.ASHWICK);
        int have = sl.getEntitiesOfClass(Militia.class, new AABB(c, c).inflate(60, 20, 60), m -> m.isAlive() && m.getPersistentData().getInt("isg_story") == pt.id).size();
        for (int i = have; i < n; i++) {
            Militia m = AddonEntities.MILITIA.get().create(sl);
            if (m == null) return;
            Vec3 at = pos(pt, StoryStage.ASHWICK, new double[]{(i - 1.5) * 3, 0, -14});
            m.moveTo(at.x, at.y, at.z, 180, 0);
            tag(m, pt, StoryStage.ASHWICK);
            m.getPersistentData().putInt("isg_bout", pt.id);
            sl.addFreshEntity(m);
        }
    }

    /** Corvin fights beside you: every few seconds he cuts down a Hollow near him. */
    static void corvinStrikes(ServerLevel sl, Story.Party pt, long now) {
        if (now % 120 != 0) return;
        for (StoryNpc c : StoryArc1.npcs(sl, pt, AddonEntities.STORY_CORVIN.get())) {
            WildFolk t = sl.getEntitiesOfClass(WildFolk.class, c.getBoundingBox().inflate(14), m -> m.isAlive() && m.getPersistentData().getInt("isg_bout") == pt.id && m.kind != WildFolk.Kind.DEEPWARDEN)
                    .stream().findFirst().orElse(null);
            if (t == null) continue;
            c.teleportTo(t.getX() + 1.2, t.getY(), t.getZ() + 1.2);
            c.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            FxSend.slash(sl, Fx.STEEL, t.position().add(0, 1, 0), Vec3.directionFromRotation(0, c.getYRot()), new Vec3(0, 1, 0), 1.8f, 1.3f, 8, 1f);
            t.hurt(sl.damageSources().mobAttack(c), 14f);
        }
    }

    // ---- chapter 5: the deep
    static void deep(MinecraftServer s, ServerLevel sl, Story.Party pt, List<ServerPlayer> in) {
        switch (pt.step) {
            case 0 -> { pt.step = 1; play(s, pt, "mother_wakes"); }
            case 1 -> { if (!pt.n.getBoolean("half")) spawnMother(sl, pt, StoryStage.DEEP, StoryStage3.MOTHER_DEEP, 1, in.size()); }
            default -> {}
        }
    }

    static List<MotherBelow> mother(ServerLevel sl, Story.Party pt) {
        List<MotherBelow> out = new ArrayList<>();
        for (int place : new int[]{StoryStage.ASHWICK, StoryStage.DEEP}) {
            Vec3 c = StoryStage.centre(pt.id, place);
            out.addAll(sl.getEntitiesOfClass(MotherBelow.class, new AABB(c, c).inflate(60, 30, 60), b -> b.isAlive() && b.party == pt.id && !b.isDeadOrDying()));
        }
        return out;
    }

    static void spawnMother(ServerLevel sl, Story.Party pt, int place, double[] where, int half, int players) {
        if (!mother(sl, pt).isEmpty()) { pt.n.putBoolean("up", true); return; }
        MotherBelow b = AddonEntities.MOTHER_BELOW.get().create(sl);
        if (b == null) return;
        Vec3 at = pos(pt, place, where);
        b.moveTo(at.x, at.y, at.z, 0, 0);
        b.party = pt.id;
        b.half = half;
        StoryArc2.scale(b, players);
        if (half == 2) { b.setPhase(2); b.setHealth(b.getMaxHealth() * 0.5f); }
        tag(b, pt, place);
        b.getPersistentData().putInt("isg_bout", pt.id);
        sl.addFreshEntity(b);
        FxSend.ring(sl, Fx.ICE, at.add(0, 0.1, 0), 0.4f, 7f, 0.12f, 18);
        FxSend.shake(sl, at, 0.7f, 24, 48);
        pt.n.putBoolean("up", true);
        pt.dirty = true;
    }

    /** Half health, the first time: she tears the floor open and goes down. */
    static void motherHalf(MotherBelow b) {
        if (!(b.level() instanceof ServerLevel sl)) return;
        Story.Party pt = Story.byId(sl.getServer(), b.party);
        if (pt == null || pt.arc != 3 || pt.ch != 4) return;
        pt.n.putBoolean("half", true);
        BossFx.ring(sl, Fx.ICE, b.position().add(0, 0.1, 0), 9f, 0.2f, 0.12f, 20);
        FxSend.shake(sl, b.position(), 1f, 30, 48);
        b.discard();
        for (Mob m : foes(sl, pt)) m.discard();
        pt.note("At half her strength, the Mother tore the cavern floor open and went down.");
        play(sl.getServer(), pt, "mother_retreats");
    }

    // ---- chapter 6: the choice
    static void choice(MinecraftServer s, ServerLevel sl, Story.Party pt, List<ServerPlayer> in, long now) {
        switch (pt.step) {
            case 0 -> play(s, pt, "the_choice", "Hold Ashwick", "Go down for the Mirefolk");
            case 1 -> {
                boolean hold = pt.flag("hold");
                if (hold) {   // a last surge over the wall, then she comes up into the village
                    militia(sl, pt, 4);
                    corvinStrikes(sl, pt, now);
                    if (pt.count("surge") == 0) {
                        pt.add("surge", 1);
                        for (int i = 0; i < 6 + 2 * (in.size() - 1); i++) {
                            int[] pit = StoryStage3.PITS[i % 3];
                            spawnHollow(sl, pt, StoryStage.ASHWICK, pos(pt, StoryStage.ASHWICK, new double[]{pit[0], 0, pit[1] + 3}), i % 3 == 2 ? WildFolk.Kind.HOLLOW_SINGER : WildFolk.Kind.HOLLOW_CRAWLER);
                        }
                        return;
                    }
                    if (!foes(sl, pt).isEmpty() && !pt.n.getBoolean("up")) return;
                    spawnMother(sl, pt, StoryStage.ASHWICK, StoryStage3.MOTHER_VILLAGE, 2, in.size());
                } else {
                    double[][] cages = new double[StoryStage3.CAGES.length][];
                    for (int i = 0; i < cages.length; i++) cages[i] = new double[]{StoryStage3.CAGES[i][0], 0, StoryStage3.CAGES[i][1]};
                    crowd(sl, pt, StoryStage.DEEP, cages);
                    StoryArc1.ensureNpc(sl, pt, AddonEntities.STORY_SEDGE.get(), StoryStage.DEEP, new double[]{-19, 0, 0}, 90);
                    spawnMother(sl, pt, StoryStage.DEEP, StoryStage3.MOTHER_DEEP, 2, in.size());
                }
            }
            default -> {}
        }
    }

    static void motherFell(MotherBelow b) {
        if (!(b.level() instanceof ServerLevel sl)) return;
        Story.Party pt = Story.byId(sl.getServer(), b.party);
        if (pt == null || pt.arc != 3 || pt.ch != 5 || b.half != 2) return;
        for (Mob m : foes(sl, pt)) m.discard();
        boolean down = pt.flag("down");
        if (down) for (StoryNpc n : StoryArc1.npcs(sl, pt, AddonEntities.STORY_REFUGEE.get())) {   // out of the cages
            Vec3 to = n.position().add(n.getX() < pos(pt, StoryStage.DEEP, new double[]{0, 0, 0}).x ? 3 : -3, 0, 0);
            FxSend.ring(sl, Fx.ICE, n.position().add(0, 0.1, 0), 1.2f, 0.2f, 0.05f, 10);
            n.teleportTo(to.x, to.y, to.z);
        }
        pt.note(down ? "The Mother Below died in the deep. The Mirefolk came up alive; Ashwick burnt." : "The Mother Below died in Ashwick's square. The village stands; the Mirefolk below were lost.");
        toChapter(sl.getServer(), pt, 6);
        if (down) pt.set("down"); else pt.set("hold");
        play(sl.getServer(), pt, down ? "aftermath_down" : "aftermath_hold");
    }

    static void finish(MinecraftServer s, Story.Party pt) {
        boolean down = pt.flag("down");
        var charm = down ? AddonItemRegistry.FENWATER_BEADS.get() : AddonItemRegistry.SEDGE_REED.get();
        int place = place(pt);
        for (ServerPlayer p : StoryStage.inside(s, pt, place)) {
            GrimoireMastery.bountyXp(p, 5000);
            give(p, BossRules.core(3), null);
            give(p, new ItemStack(charm), null);
            p.sendSystemMessage(Component.literal("ARC III COMPLETE  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("+5000 mastery, a tier III Mana Core, " + new ItemStack(charm).getHoverName().getString() + ".").withStyle(ChatFormatting.YELLOW)));
            StoryStage.leave(p);
        }
        toChapter(s, pt, 7);
        if (down) pt.set("down"); else pt.set("hold");
    }

    // ================================================================== scenes ended, choices made
    static void onTalkDone(MinecraftServer s, Story.Party pt, String id, int choice) {
        switch (id) {
            case "sedge_meet" -> { if (pt.ch == 0 && pt.step == 2) pt.step = 3; }
            case "maren_gate" -> {
                if (pt.ch != 0) return;
                pt.note("Reeve Maren let the Mirefolk camp outside Ashwick's south gate.");
                toChapter(s, pt, 1);
                for (ServerPlayer p : StoryStage.inside(s, pt, StoryStage.ASHWICK)) {   // out into the world: the fights to hold it in are anywhere
                    StoryStage.leave(p);
                    p.sendSystemMessage(Component.literal("Corvin: ").withStyle(ChatFormatting.AQUA).append(Component.literal("Go and fight with it burning. Come back to Ashwick when you can hold it.").withStyle(ChatFormatting.WHITE)));
                }
            }
            case "brand_held" -> {
                if (pt.ch != 1) return;
                pt.set("brandHeld");
                pt.note("You learned to hold Brand II: it takes half as much from you now.");
                for (ServerPlayer p : Story.online(s, pt)) title(p, "BRAND II HELD", "Cinderheart takes half as much");
                toChapter(s, pt, 2);
            }
            case "tunnels_done" -> { if (pt.ch == 2) toChapter(s, pt, 3); }
            case "siege_won" -> {
                if (pt.ch != 3) return;
                List<ServerPlayer> in = StoryStage.inside(s, pt, StoryStage.ASHWICK);
                pt.note("Ashwick held through five waves.");
                toChapter(s, pt, 4);
                for (ServerPlayer p : in) StoryStage.enter(p, pt, StoryStage.DEEP, StoryStage.spawn(pt.id, StoryStage.DEEP), 180);
            }
            case "mother_retreats" -> { if (pt.ch == 4) toChapter(s, pt, 5); }
            case "the_choice" -> {
                if (pt.ch != 5) return;
                boolean down = choice == 1;
                pt.set(down ? "down" : "hold");
                pt.step = 1;
                pt.note(down ? "The party chose to go down for the Mirefolk." : "The party chose to hold Ashwick.");
                List<ServerPlayer> in = StoryStage.inside(s, pt, StoryStage.DEEP);
                if (!down) for (ServerPlayer p : in) StoryStage.enter(p, pt, StoryStage.ASHWICK, pos(pt, StoryStage.ASHWICK, new double[]{0, 0, 6}), 180);
                play(s, pt, down ? "go_down" : "hold_up");
            }
            default -> {}
        }
        pt.dirty = true;
    }

    // ================================================================== the people
    static void talkTo(ServerPlayer p, Story.Party pt, StoryNpc npc) {
        String line = switch (npc.who) {
            case "maren" -> pt.ch <= 1 ? "Outside the walls after dark. That's the rule." : pt.ch == 3 ? "Hold the north wall. That's all I ask of anyone tonight." : "Ashwick has stood two hundred years. It'll stand tonight.";
            case "sedge" -> pt.ch == 2 ? "Three hives. Burn them - the Brand will do it. Fire is the one thing they fear." : "The marsh sang to us for a thousand years. Now it screams.";
            case "refugee" -> "...Thank you. For the road.";
            default -> switch (pt.ch) {
                case 0 -> "Clear the road. Then get them to the gate.";
                case 1 -> "Fight with it burning. Keep your blood in you - don't drop under half. Three times.";
                case 2 -> "Sedge says the old well goes down. Burn every hive you find.";
                case 3 -> "North wall. With me.";
                default -> "Rest. The Brand has been quiet. I don't like it quiet.";
            };
        };
        String who = switch (npc.who) { case "maren" -> "Reeve Maren"; case "sedge" -> "Old Sedge"; case "refugee" -> "A Mirefolk"; default -> "Corvin"; };
        p.sendSystemMessage(Component.literal(who + ": ").withStyle(npc.who.equals("maren") ? ChatFormatting.RED : npc.who.equals("sedge") || npc.who.equals("refugee") ? ChatFormatting.GREEN : ChatFormatting.AQUA)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
    }

    static void enterRequest(MinecraftServer s, Story.Party pt, ServerPlayer p) {
        if (StoryStage.inStage(p) || pt.ch >= 7 || pt.ch == 1) return;
        int place = place(pt);
        StoryStage.enter(p, pt, place, StoryStage.spawn(pt.id, place), 180);
    }

    // ================================================================== the Journal
    static void journal(MinecraftServer s, Story.Party pt, CompoundTag t) {
        t.putString("arcTitle", ARC);
        int ch = Math.max(0, Math.min(7, pt.ch));
        t.putString("chTitle", ch >= 7 ? "Arc III complete" : "Chapter " + (ch + 1) + " - " + TITLES[ch]);
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
        if (ch < 7 && ch != 1) t.putString("enter", "Return to " + StoryStage.NAMES[place(pt)]);
        if (pt.talk != null && pt.talk.choices.length > 0) t.putString("voting", pt.talk.id);
    }

    static List<Object[]> goals(Story.Party pt) {
        List<Object[]> g = new ArrayList<>();
        switch (pt.ch) {
            case 0 -> {
                g.add(new Object[]{"Drive the Hollowfolk off the caravan", pt.step >= 2 ? 2 : Math.max(0, pt.count("wave") - 1), 2});
                if (pt.step >= 3) g.add(new Object[]{"Bring the Mirefolk to Ashwick's gate", pt.step >= 4 ? 1 : 0, 1});
            }
            case 1 -> g.add(new Object[]{"Win a fight with the Brand burning, never below half health", Math.min(3, pt.count("held")), 3});
            case 2 -> {
                g.add(new Object[]{"Go down the old well in Ashwick", pt.n.getBoolean("hive0woke") || pt.n.getBoolean("hive1woke") || pt.n.getBoolean("hive2woke") ? 1 : 0, 1});
                g.add(new Object[]{"Burn out the hives with the Brand", Math.min(3, pt.count("hives")), 3});
            }
            case 3 -> g.add(new Object[]{"Hold Ashwick through the night", Math.max(0, pt.count("wave") - (pt.step >= 2 ? 0 : 1)), 5});
            case 4 -> g.add(new Object[]{"Face the Mother Below in the deep", pt.n.getBoolean("half") ? 1 : 0, 1});
            case 5 -> {
                g.add(new Object[]{"Choose: Ashwick, or the Mirefolk", pt.flag("hold") || pt.flag("down") ? 1 : 0, 1});
                if (pt.step >= 1) g.add(new Object[]{pt.flag("hold") ? "Hold the village - and finish her in the square" : "Free the Mirefolk - and finish her in the deep", 0, 1});
            }
            case 6 -> g.add(new Object[]{"Leave: stand in the circle", 0, 1});
            default -> {}
        }
        return g;
    }
}
