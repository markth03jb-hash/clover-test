package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.Rival;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Rivals. Every player gets one: a mage with a name, a grimoire, a look and a personality (arrogant, honourable,
 * cheerful or cold), kept on the player (persistent data "isg_rival") so they remember every bout.
 * <ul>
 *   <li><b>Encounters:</b> about once an in-game day they turn up and challenge you - a duel (fought in a ring of their
 *       magic; leave it and you forfeit; nobody dies - the loser yields at a fifth of their health) or a race (first to a
 *       pillar of gold light). Accept or refuse in chat.</li>
 *   <li><b>Memory:</b> they talk about your record, and each time you beat them they come back a tier stronger (more
 *       health, harder hits, a new move: dash, rain, burst, then their signature).</li>
 *   <li><b>Endings:</b> after five bouts - win most and don't dodge them, and they become an ally (a whistle calls them
 *       to fight beside you for 30 s, once every 5 minutes); keep refusing or losing, and they go over to the Eye of
 *       the Midnight Sun and come back once more as a far stronger foe. Either way you end up with their bookmark.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class RivalAI {
    private RivalAI() {}

    static final String TAG = "isg_rival";
    static final int ARROGANT = 0, HONOURABLE = 1, CHEERFUL = 2, COLD = 3;
    static final String[] PERS = {"Arrogant", "Honourable", "Cheerful", "Cold"};
    static final ChatFormatting[] PERS_COL = {ChatFormatting.GOLD, ChatFormatting.AQUA, ChatFormatting.GREEN, ChatFormatting.DARK_AQUA};
    static final int OFFER = 0, DUEL = 1, RACE = 2, ALLY = 3, FINAL = 4;
    static final int WARN = 18;
    static final double ARENA = 12;
    public static final int P_IDLE = 0, P_CAST = 1, P_SLASH = 2, P_DASH = 3, P_RAISE = 4;

    private static final String[] FIRST = {"Lorenz", "Siegfried", "Mira", "Kael", "Ophelia", "Rufus", "Talia", "Dorian", "Elric", "Seraphine",
            "Gideon", "Liesel", "Corvin", "Nadia", "Basil", "Isolde", "Fenwick", "Rosalind", "Aldous", "Wren"};
    private static final String[] LAST = {"Vermont", "Ashgrave", "Silverleaf", "Duskmoor", "Hollowell", "Brightwater", "Kestrel",
            "Ravensworth", "Thorne", "Valcourt", "Ember", "Greaves", "Larkspur", "Mournvale"};

    // what they say: [personality][line]
    private static final int L_FIRST = 0, L_GREET = 1, L_WON_LAST = 2, L_LOST_LAST = 3, L_ACCEPT = 4, L_REFUSE = 5, L_RIVAL_WINS = 6,
            L_RIVAL_LOSES = 7, L_RACE = 8, L_ALLY = 9, L_VILLAIN = 10, L_FINAL = 11, L_FINAL_LOST = 12, L_CALLED = 13, L_FORFEIT = 14;
    private static final String[][] LINES = {
            {   // arrogant
                    "So you're the one everyone's talking about, {you}? Show me that grimoire isn't just for show.",
                    "{you}. I was hoping I'd run into you.",
                    "Last time was a fluke, {you}. I'll prove it.",
                    "Back for another lesson? I'm {w} and {l} against you, you know - well, you know.",
                    "Try to keep up.", "Running away? How fitting.",
                    "As expected. Come back when you're worth my time.", "Tch... this isn't over, {you}.",
                    "A race. You'll be eating my dust.",
                    "...Fine. You're strong. Strong enough that I'd rather fight beside you than against you. Call me if you need me - not that you will.",
                    "You keep dodging me. Someone else noticed what I can do - people who reward power. Next time, I won't hold back.",
                    "The Eye of the Midnight Sun gave me everything you wouldn't. Now - fight me, {you}!",
                    "Even this... wasn't enough? Hah. Take it. I won't need it where I'm going.",
                    "Hmph. Couldn't manage without me, could you?", "Leaving the ring? Then I win by default."},
            {   // honourable
                    "Well met, {you}. I am a mage who seeks strong opponents. Will you cross grimoires with me?",
                    "{you}. I have trained since our last bout. Will you face me?",
                    "You bested me last time. I have thought of little else. Once more, if you will.",
                    "Our record stands at {w} to {l}. Shall we add to it?",
                    "Then let us begin. With honour.", "I understand. Another time, then.",
                    "A fair fight. Thank you, {you}.", "Well fought. You have earned it.",
                    "A race, then - speed is also a mage's strength.",
                    "We have tested each other enough. I would be proud to fight at your side. Call, and I will come.",
                    "You turn me away again and again. Perhaps strength without honour is all this world respects. Very well.",
                    "I have joined the Eye of the Midnight Sun. Do not hold back, {you} - I will not.",
                    "...You were right, and I was wrong. Keep this. Remember who I was before.",
                    "I am here. Let us fight together.", "You stepped out of the ring. The bout is mine."},
            {   // cheerful
                    "Heyyy! You're {you}, right? I'm gonna be your rival! Wanna fight? Come on, come on!",
                    "{you}! Rematch? Rematch? Please?",
                    "You beat me last time! That was SO cool. Again!",
                    "I'm winning, {w} to {l}! Okay, okay, I'm counting it wrong probably. Fight me!",
                    "Yesss! Here I go!", "Aww... fine. Next time though!",
                    "I won! I won! Good fight, {you}!", "Ahaha, you got me! You're amazing!",
                    "Race you! Last one there buys lunch!",
                    "Hey... we're friends now, right? Blow this whistle and I'll come running! Promise!",
                    "You never want to play anymore... Some people said they'd train me if I joined them. So I did.",
                    "It's not fun anymore, {you}. They made me strong. Let's see if you can still keep up.",
                    "...I'm sorry. I just wanted you to notice me. Here. Keep it, okay?",
                    "You called! I'm here, I'm here!", "Hey, you left the ring! That means I win!"},
            {   // cold
                    "{you}. You have a grimoire. That is reason enough. Draw it.",
                    "{you}. Again.",
                    "You won. It will not happen twice.",
                    "{w} to {l}. Nothing has changed.",
                    "Begin.", "Coward.",
                    "Expected.", "...Acknowledged.",
                    "The pillar. Go.",
                    "You are worth fighting beside. Use the whistle. I will answer.",
                    "You waste my time. There are others who will not.",
                    "I serve the Eye of the Midnight Sun now. This ends here.",
                    "...So this is defeat. Take it. I have no use for it.",
                    "Here.", "Out of the ring. Forfeit."}};

    // ================================================================== the profile on the player
    static CompoundTag profile(ServerPlayer p) {
        CompoundTag t = p.getPersistentData().getCompound(TAG);
        if (t.isEmpty()) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            t.putString("Name", FIRST[r.nextInt(FIRST.length)] + " " + LAST[r.nextInt(LAST.length)]);
            t.putInt("Pers", r.nextInt(4));
            t.putInt("Book", r.nextInt(GrimoireMastery.BOOKS.size()));
            t.putUUID("Seed", UUID.randomUUID());
            t.putInt("Tier", 1);
            t.putLong("Next", p.level().getGameTime() + 6000);
            p.getPersistentData().put(TAG, t);
        }
        return t;
    }

    private static String line(CompoundTag t, int which, ServerPlayer p) {
        return LINES[t.getInt("Pers")][which].replace("{you}", p.getGameProfile().getName())
                .replace("{w}", String.valueOf(t.getInt("Losses"))).replace("{l}", String.valueOf(t.getInt("Wins")));
    }

    private static void say(ServerPlayer p, CompoundTag t, int which) {
        p.sendSystemMessage(Component.literal(t.getString("Name") + ": ").withStyle(PERS_COL[t.getInt("Pers")], ChatFormatting.BOLD)
                .append(Component.literal(line(t, which, p)).withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC)));
    }

    // ================================================================== encounters
    static final class Session {
        UUID owner, rival;
        int kind, pending;
        Vec3 centre, goal;
        long start, deadline;
        int outside;
        boolean done;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> WHISTLE = new HashMap<>();

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Session s = SESSIONS.get(p.getUUID());
        if (s != null) { session(p, s); return; }
        if (p.tickCount % 200 != 0 || !p.level().dimension().equals(Level.OVERWORLD) || p.isSpectator() || p.isCreative()) return;
        CompoundTag t = profile(p);
        if (t.getInt("Ending") == 1 || t.getBoolean("FinalDone") || p.level().getGameTime() < t.getLong("Next")) return;
        if (!p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(48),
                x -> x instanceof com.ironspellgrimoire.entity.Zenon || x instanceof com.ironspellgrimoire.entity.Vetto || x instanceof com.ironspellgrimoire.entity.Rhya).isEmpty()) return;
        offer(p, t);
    }

    private static Rival body(ServerPlayer p, CompoundTag t, Vec3 at, int mode) {
        ServerLevel sl = p.serverLevel();
        Rival r = AddonEntities.RIVAL.get().create(sl);
        if (r == null) return null;
        int bookIdx = Math.max(0, Math.min(GrimoireMastery.BOOKS.size() - 1, t.getInt("Book")));
        GrimoireMastery.Book bk = GrimoireMastery.BOOKS.get(bookIdx);
        int tier = mode == 2 ? 6 : Math.max(1, Math.min(5, t.getInt("Tier")));
        r.owner = p.getUUID();
        r.tier = tier;
        r.setBook(new ItemStack(bk.item().get()));
        r.setPal(mode == 2 ? Fx.DARK : bk.palette());
        r.setMode(mode);
        r.setSeed(t.getUUID("Seed"));
        MutableComponent name = Component.literal(t.getString("Name")).withStyle(mode == 2 ? ChatFormatting.DARK_RED : PERS_COL[t.getInt("Pers")]);
        if (mode == 2) name = Component.literal("[Eye of the Midnight Sun] ").withStyle(ChatFormatting.DARK_PURPLE).append(name);
        r.setCustomName(name);
        double hp = mode == 2 ? 320 : 50 + tier * 30;
        var mh = r.getAttribute(Attributes.MAX_HEALTH);
        if (mh != null) mh.setBaseValue(hp);
        var sp = r.getAttribute(Attributes.MOVEMENT_SPEED);
        if (sp != null) sp.setBaseValue(0.3 + tier * 0.012);
        r.setHealth(r.getMaxHealth());
        Vec3 safe = SpaceFx.safe(sl, at, r);
        if (safe == null) safe = at;
        Vec3 face = p.position().subtract(safe);
        r.moveTo(safe.x, safe.y, safe.z, (float) Math.toDegrees(Math.atan2(-face.x, face.z)), 0);
        sl.addFreshEntity(r);
        FxSend.ring(sl, r.pal(), safe.add(0, 0.1, 0), 0.3f, 2.4f, 0.1f, 12);
        RiftFx.cut(sl, safe.add(0, 1, 0), new Vec3(0, 1, 0), new Vec3(1, 0, 0), 1.0f, 12);
        sl.playSound(null, r.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1f, 1f);
        return r;
    }

    static void offer(ServerPlayer p, CompoundTag t) {
        boolean villain = t.getInt("Ending") == 2;
        Vec3 dir = flat(p.getLookAngle());
        if (dir.lengthSqr() < 1e-4) dir = new Vec3(0, 0, 1);
        Vec3 at = p.position().add(dir.normalize().scale(9));
        Rival r = body(p, t, at, villain ? 2 : 0);
        if (r == null) return;
        Session s = new Session();
        s.owner = p.getUUID();
        s.rival = r.getUUID();
        s.kind = OFFER;
        s.pending = villain ? FINAL : ThreadLocalRandom.current().nextInt(10) < 7 ? DUEL : RACE;
        s.start = p.level().getGameTime();
        s.deadline = s.start + 600;
        SESSIONS.put(p.getUUID(), s);
        boolean first = t.getInt("Accepted") + t.getInt("Ignored") == 0;
        if (villain) say(p, t, L_FINAL);
        else if (first) say(p, t, L_FIRST);
        else if (t.getInt("Last") == 1) say(p, t, L_WON_LAST);
        else if (t.getInt("Last") == 2) say(p, t, L_LOST_LAST);
        else say(p, t, L_GREET);
        if (villain) { AstaFx.later(60, () -> accept(p)); return; }   // the villain doesn't ask
        String what = s.pending == DUEL ? "a duel" : "a race";
        p.sendSystemMessage(Component.literal("  " + t.getString("Name") + " challenges you to " + what + " (tier " + t.getInt("Tier") + ").  ").withStyle(ChatFormatting.GRAY)
                .append(button("[Accept]", "/rival accept", ChatFormatting.GREEN)).append(Component.literal("  "))
                .append(button("[Refuse]", "/rival refuse", ChatFormatting.RED)));
    }

    private static MutableComponent button(String text, String cmd, ChatFormatting col) {
        return Component.literal(text).withStyle(st -> st.withColor(col).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(cmd))));
    }

    static void accept(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.kind != OFFER) { p.displayClientMessage(Component.literal("Nobody is challenging you right now.").withStyle(ChatFormatting.GRAY), true); return; }
        CompoundTag t = profile(p);
        ServerLevel sl = p.serverLevel();
        Rival r = rival(sl, s);
        if (r == null) { end(p, s); return; }
        t.putInt("Accepted", t.getInt("Accepted") + 1);
        s.kind = s.pending;
        s.start = sl.getGameTime() + 60;   // a three-count first
        if (s.kind == RACE) {
            Vec3 dir = flat(p.getLookAngle());
            if (dir.lengthSqr() < 1e-4) dir = new Vec3(1, 0, 0);
            Vec3 g = p.position().add(rotY(dir.normalize(), ThreadLocalRandom.current().nextDouble(-0.8, 0.8)).scale(ThreadLocalRandom.current().nextDouble(45, 60)));
            int gx = (int) Math.floor(g.x), gz = (int) Math.floor(g.z);
            s.goal = new Vec3(gx + 0.5, sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz), gz + 0.5);
            s.deadline = s.start + 20 * 90;
            say(p, t, L_RACE);
            race(p, s.goal, 20 * 95);
            r.moveTo(p.getX() + 1.5, p.getY(), p.getZ(), r.getYRot(), 0);
        } else {
            s.centre = p.position().add(r.position()).scale(0.5);
            s.deadline = s.start + 20 * 300;
            say(p, t, L_ACCEPT);
        }
        for (int i = 0; i < 3; i++) {
            final int n = 3 - i;
            AstaFx.later(i * 20, () -> p.displayClientMessage(Component.literal(String.valueOf(n)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true));
        }
        AstaFx.later(60, () -> { p.displayClientMessage(Component.literal("GO!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true); sl.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1f, 1.4f); });
    }

    static void refuse(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.kind != OFFER) return;
        CompoundTag t = profile(p);
        t.putInt("Ignored", t.getInt("Ignored") + 1);
        say(p, t, L_REFUSE);
        t.putLong("Next", p.level().getGameTime() + 24000);
        leaveLater(p, s, 20);
        checkEnding(p, t);
    }

    private static Rival rival(ServerLevel sl, Session s) {
        return sl.getEntity(s.rival) instanceof Rival r && r.isAlive() ? r : null;
    }

    private static void end(ServerPlayer p, Session s) {
        s.done = true;
        SESSIONS.remove(p.getUUID());
        Rival r = rival(p.serverLevel(), s);
        if (r != null) {
            FxSend.ring(p.serverLevel(), r.pal(), r.position().add(0, 1, 0), 1.6f, 0.2f, 0.08f, 10);
            p.serverLevel().playSound(null, r.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1f, 1.4f);
            r.discard();
        }
        race(p, Vec3.ZERO, 0);
    }

    private static void leaveLater(ServerPlayer p, Session s, int ticks) {
        s.done = true;
        AstaFx.later(ticks, () -> { if (SESSIONS.get(p.getUUID()) == s) end(p, s); });
    }

    /** The race's pillar on your compass bar (FxPacket shape 69 -> client/BountyHud). */
    private static void race(ServerPlayer p, Vec3 goal, int life) {
        FxPacket k = new FxPacket();
        k.shape = 69; k.c = goal; k.life = life;
        if (p.connection != null) AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), k);
    }

    private static void session(ServerPlayer p, Session s) {
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        Rival r = rival(sl, s);
        if (s.done) return;
        if (r == null || r.level() != p.level()) { end(p, s); return; }
        CompoundTag t = profile(p);
        switch (s.kind) {
            case OFFER -> {
                r.getNavigation().stop();
                r.getLookControl().setLookAt(p, 30, 30);
                if (now > s.deadline) refuse(p);
            }
            case DUEL, FINAL -> {
                if (now % 20 == 0) FxSend.ring(sl, r.pal(), ZenonAI.ground(sl, s.centre).add(0, 0.06, 0), (float) ARENA - 0.1f, (float) ARENA, 0.05f, 22);
                if (now < s.start) { r.getNavigation().stop(); r.getLookControl().setLookAt(p, 30, 30); return; }
                double d = Math.hypot(p.getX() - s.centre.x, p.getZ() - s.centre.z);
                if (d > ARENA + 1) {
                    s.outside++;
                    if (s.outside % 20 == 1) p.displayClientMessage(Component.literal("Back into the ring! " + Math.max(0, 3 - s.outside / 20) + "...").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                    if (s.outside > 60) { say(p, t, L_FORFEIT); result(p, s, false); }
                } else s.outside = 0;
                if (now > s.deadline) result(p, s, false);
            }
            case RACE -> {
                if (now < s.start) { r.getNavigation().stop(); return; }
                if (now % 4 == 0) r.getNavigation().moveTo(s.goal.x, s.goal.y, s.goal.z, 1.15 + r.tier * 0.05);
                if (r.tier >= 2 && now % 70 == 0) {   // a burst of magic carries them forward
                    Vec3 to = flat(s.goal.subtract(r.position()));
                    if (to.lengthSqr() > 9) {
                        Vec3 dest = SpaceFx.safe(sl, r.position().add(to.normalize().scale(3 + r.tier * 0.6)), r);
                        if (dest != null) { FxSend.trail(sl, r.pal(), r.position().add(0, 1, 0), dest.add(0, 1, 0), 0.3f, 8); SpaceFx.move(r, dest, r.getYRot()); }
                    }
                }
                if (now % 20 == 0) FxSend.beam(sl, Fx.GOLD, s.goal, s.goal.add(0, 30, 0), 0.5f, 22);
                if (Math.hypot(p.getX() - s.goal.x, p.getZ() - s.goal.z) < 3) result(p, s, true);
                else if (Math.hypot(r.getX() - s.goal.x, r.getZ() - s.goal.z) < 2.5 || now > s.deadline) result(p, s, false);
            }
            case ALLY -> { if (now > s.deadline) { say(p, t, L_CALLED); end(p, s); } }
            default -> {}
        }
    }

    /** A bout is over. {@code won} = the player won. */
    static void result(ServerPlayer p, Session s, boolean won) {
        if (s.done) return;
        s.done = true;
        CompoundTag t = profile(p);
        ServerLevel sl = p.serverLevel();
        race(p, Vec3.ZERO, 0);
        if (s.kind == FINAL) {
            if (won) {
                say(p, t, L_FINAL_LOST);
                t.putBoolean("FinalDone", true);
                give(p, new ItemStack(AddonItemRegistry.RIVAL_BOOKMARK.get()));
                Bounties.addStars(p, 15);
                p.sendSystemMessage(Component.literal("Your rivalry is over.  +15 merit stars").withStyle(ChatFormatting.YELLOW));
            } else {
                say(p, t, L_RIVAL_WINS);
                t.putLong("Next", sl.getGameTime() + 24000);
            }
            leaveLater(p, s, 40);
            return;
        }
        if (won) {
            t.putInt("Wins", t.getInt("Wins") + 1);
            t.putInt("Last", 1);
            say(p, t, L_RIVAL_LOSES);
            int stars = 1 + t.getInt("Tier");
            Bounties.addStars(p, stars);
            p.displayClientMessage(Component.literal("You beat " + t.getString("Name") + "!  +" + stars + " merit stars").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), true);
            t.putInt("Tier", Math.min(5, t.getInt("Tier") + 1));
            sl.playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1.2f);
        } else {
            t.putInt("Losses", t.getInt("Losses") + 1);
            t.putInt("Last", 2);
            say(p, t, L_RIVAL_WINS);
        }
        t.putLong("Next", sl.getGameTime() + 24000);
        leaveLater(p, s, 40);
        checkEnding(p, t);
    }

    private static void checkEnding(ServerPlayer p, CompoundTag t) {
        if (t.getInt("Ending") != 0 || t.getInt("Accepted") + t.getInt("Ignored") < 5) return;
        if (t.getInt("Ignored") <= 1 && t.getInt("Wins") >= 3) {
            t.putInt("Ending", 1);
            AstaFx.later(50, () -> {
                say(p, t, L_ALLY);
                give(p, new ItemStack(AddonItemRegistry.RIVAL_WHISTLE.get()));
                give(p, new ItemStack(AddonItemRegistry.RIVAL_BOOKMARK.get()));
                p.sendSystemMessage(Component.literal(t.getString("Name") + " is your ally now.").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            });
        } else {
            t.putInt("Ending", 2);
            t.putLong("Next", p.level().getGameTime() + 12000);
            AstaFx.later(50, () -> {
                say(p, t, L_VILLAIN);
                p.sendSystemMessage(Component.literal(t.getString("Name") + " has gone over to the Eye of the Midnight Sun...").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
            });
        }
    }

    private static void give(ServerPlayer p, ItemStack s) { if (!p.getInventory().add(s)) p.drop(s, false); }

    /** The whistle: your ally comes to fight beside you for 30 s. */
    public static void whistle(ServerPlayer p) {
        CompoundTag t = profile(p);
        if (t.getInt("Ending") != 1) { p.displayClientMessage(Component.literal("Nobody answers.").withStyle(ChatFormatting.GRAY), true); return; }
        long now = p.level().getGameTime();
        Long ready = WHISTLE.get(p.getUUID());
        if (ready != null && now < ready) { p.displayClientMessage(Component.literal(t.getString("Name") + " is resting - " + (ready - now) / 20 + " s.").withStyle(ChatFormatting.GRAY), true); return; }
        if (SESSIONS.containsKey(p.getUUID())) return;
        WHISTLE.put(p.getUUID(), now + 6000);
        Rival r = body(p, t, p.position().add(flat(p.getLookAngle()).scale(-2)), 1);
        if (r == null) return;
        Session s = new Session();
        s.owner = p.getUUID(); s.rival = r.getUUID(); s.kind = ALLY; s.start = now; s.deadline = now + 600;
        SESSIONS.put(p.getUUID(), s);
        say(p, t, L_CALLED);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), SoundSource.PLAYERS, 1f, 1.2f);
    }

    // ================================================================== the rival's body: fighting
    private static Session sessionOf(Rival r) {
        if (r.owner == null) return null;
        Session s = SESSIONS.get(r.owner);
        return s != null && r.getUUID().equals(s.rival) ? s : null;
    }

    public static void tick(Rival r) {
        Session s = sessionOf(r);
        if (s == null) { r.discard(); return; }
        ServerLevel sl = (ServerLevel) r.level();
        ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(r.owner);
        if (owner == null) { SESSIONS.remove(r.owner); r.discard(); return; }
        if (s.done || s.kind == OFFER || s.kind == RACE || sl.getGameTime() < s.start) return;
        LivingEntity t;
        if (s.kind == ALLY) {
            t = null;
            double bd = 24 * 24;
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(24), x -> x instanceof Enemy && x.isAlive() && !(x instanceof Rival))) {
                double d = e.distanceToSqr(r);
                if (d < bd) { bd = d; t = e; }
            }
            if (t == null) {   // nothing to fight: stay by you
                if (r.distanceTo(owner) > 4) r.getNavigation().moveTo(owner, 1.2);
                if (r.distanceTo(owner) > 24) r.moveTo(owner.getX(), owner.getY(), owner.getZ());
                return;
            }
        } else t = owner;
        r.getLookControl().setLookAt(t, 30, 30);
        if (r.busy > 0) { r.busy--; return; }
        double dist = r.distanceTo(t);
        if (dist > 9) r.getNavigation().moveTo(t, 1.1);
        else if (dist < 4) {
            Vec3 away = flat(r.position().subtract(t.position()));
            if (away.lengthSqr() > 1e-4) r.getNavigation().moveTo(r.getX() + away.normalize().x * 3, r.getY(), r.getZ() + away.normalize().z * 3, 1.1);
        } else r.getNavigation().stop();
        choose(r, sl, t, s, dist);
    }

    private interface Move { int run(Rival r, ServerLevel sl, LivingEntity t, Session s); }

    private static void choose(Rival r, ServerLevel sl, LivingEntity t, Session s, double dist) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<Move> pool = new ArrayList<>();
        pool.add(RivalAI::bolts);
        pool.add(RivalAI::wave);
        if (r.tier >= 2) pool.add(RivalAI::dash);
        if (r.tier >= 3) pool.add(RivalAI::rain);
        if (r.tier >= 4 && dist < 6) pool.add(RivalAI::burst);
        if (r.tier >= 5) pool.add(RivalAI::signature);
        int took = pool.get(rnd.nextInt(pool.size())).run(r, sl, t, s);
        r.busy = took + Math.max(10, 34 - r.tier * 4);
    }

    private static float dmg(Rival r, float base) {
        Difficulty d = r.level().getDifficulty();
        float k = d == Difficulty.EASY ? 0.6f : d == Difficulty.HARD ? 1.25f : 1f;
        return base * (1f + (r.tier - 1) * 0.15f) * (r.mode() == 1 ? 2f : k);   // as an ally they hit monsters harder
    }

    /** Who a rival's magic can hit: in a bout, only you; as an ally, only monsters. */
    private static List<LivingEntity> foes(Rival r, ServerLevel sl, Session s, AABB box) {
        List<LivingEntity> out = new ArrayList<>();
        if (s.kind == ALLY) out.addAll(sl.getEntitiesOfClass(LivingEntity.class, box, x -> x instanceof Enemy && x.isAlive() && !(x instanceof Rival)));
        else {
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(s.owner);
            if (p != null && p.isAlive() && box.intersects(p.getBoundingBox())) out.add(p);
        }
        return out;
    }

    private static void hit(Rival r, LivingEntity t, float amt) {
        if (!r.isAlive() || !t.isAlive()) return;
        t.hurt(r.damageSources().mobAttack(r), amt);
    }

    private static void face(Rival r, Vec3 dir) {
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        r.setYRot(yaw); r.setYHeadRot(yaw); r.yBodyRot = yaw;
    }

    private static Vec3 dirTo(Rival r, LivingEntity t) {
        Vec3 d = flat(t.position().subtract(r.position()));
        return d.lengthSqr() < 1e-4 ? flat(r.getLookAngle()).normalize() : d.normalize();
    }

    private static boolean ok(Rival r) { Session s = sessionOf(r); return r.isAlive() && s != null && !s.done; }

    static int bolts(Rival r, ServerLevel sl, LivingEntity t, Session s) {
        int T = 12;
        r.setPose(P_CAST);
        Vec3 fwd = dirTo(r, t);
        face(r, fwd);
        Vec3 from = r.position().add(0, 1.4, 0);
        for (int k = -1; k <= 1; k++) ZenonAI.warnLine(sl, from.add(0, -1.3, 0), from.add(rotY(fwd, k * 0.2).scale(18)).add(0, -1.3, 0), 0.5f, T);
        AstaFx.later(T, () -> {
            if (!ok(r)) return;
            Vec3 aim = mid(t).subtract(from);
            double up = aim.lengthSqr() > 1e-4 ? aim.normalize().y : 0;
            for (int k = -1; k <= 1; k++) bolt(r, sl, s, from, rotY(fwd, k * 0.2).add(0, up, 0).normalize(), 15, new boolean[1]);
            sl.playSound(null, r.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.HOSTILE, 1f, 1.3f);
        });
        return T + 6;
    }

    private static void bolt(Rival r, ServerLevel sl, Session s, Vec3 at, Vec3 d, int left, boolean[] done) {
        if (left <= 0 || done[0] || !r.isAlive()) return;
        AstaFx.later(1, () -> {
            Vec3 next = at.add(d.scale(1.3));
            Vec3 wall = clip(sl, r, at, next);
            Kit.orb(sl, r.pal(), at, d.scale(1.3), 0.3f, 2);
            for (LivingEntity e : foes(r, sl, s, new AABB(at, next).inflate(1.0))) {
                if (closestOnSegment(at, next, mid(e)).distanceTo(mid(e)) > 1.0 + e.getBbWidth() * 0.5) continue;
                hit(r, e, dmg(r, 5f));
                done[0] = true;
            }
            if (done[0] || wall.distanceToSqr(next) > 1e-4) { FxSend.ring(sl, r.pal(), wall, 0.2f, 1.2f, 0.05f, 8); return; }
            bolt(r, sl, s, next, d, left - 1, done);
        });
    }

    static int wave(Rival r, ServerLevel sl, LivingEntity t, Session s) {
        int T = 12;
        r.setPose(P_SLASH);
        Vec3 fwd = dirTo(r, t);
        face(r, fwd);
        Vec3 from = r.position().add(0, 1.0, 0);
        ZenonAI.warnLine(sl, from.add(0, -0.9, 0), from.add(fwd.scale(16)).add(0, -0.9, 0), 1.8f, T);
        AstaFx.later(T, () -> {
            if (!ok(r)) return;
            sl.playSound(null, r.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.2f, 0.8f);
            travel(r, sl, s, from, fwd, 13, new HashSet<>());
        });
        return T + 6;
    }

    private static void travel(Rival r, ServerLevel sl, Session s, Vec3 at, Vec3 d, int left, Set<UUID> hit) {
        if (left <= 0 || !r.isAlive()) return;
        AstaFx.later(1, () -> {
            Vec3 next = at.add(d.scale(1.2));
            FxSend.slash(sl, r.pal(), at, d, new Vec3(0, 1, 0), 1.8f, 0.35f, 3, 1.0f);
            for (LivingEntity e : foes(r, sl, s, new AABB(at, next).inflate(1.8))) if (hit.add(e.getUUID())) hit(r, e, dmg(r, 6f));
            if (clip(sl, r, at, next).distanceToSqr(next) > 1e-4) return;
            travel(r, sl, s, next, d, left - 1, hit);
        });
    }

    static int dash(Rival r, ServerLevel sl, LivingEntity t, Session s) {
        int T = 10;
        r.setPose(P_DASH);
        Vec3 fwd = dirTo(r, t);
        face(r, fwd);
        Vec3 a = r.position();
        Vec3 end = clip(sl, r, a.add(0, 1, 0), a.add(0, 1, 0).add(fwd.scale(Math.min(10, r.distanceTo(t) + 2)))).subtract(0, 1, 0);
        ZenonAI.warnLine(sl, a.add(0, 0.1, 0), end.add(0, 0.1, 0), 1.2f, T);
        AstaFx.later(T, () -> {
            if (!ok(r)) return;
            r.moveTo(end.x, end.y, end.z, r.getYRot(), r.getXRot());
            FxSend.trail(sl, r.pal(), a.add(0, 1, 0), end.add(0, 1, 0), 0.5f, 8);
            for (LivingEntity e : foes(r, sl, s, new AABB(a, end).inflate(1.5)))
                if (closestOnSegment(a.add(0, 1, 0), end.add(0, 1, 0), mid(e)).distanceTo(mid(e)) < 1.5) hit(r, e, dmg(r, 7f));
            sl.playSound(null, r.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.2f, 1f);
        });
        return T + 4;
    }

    static int rain(Rival r, ServerLevel sl, LivingEntity t, Session s) {
        int T = 16;
        r.setPose(P_RAISE);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<Vec3> spots = new ArrayList<>();
        spots.add(ZenonAI.ground(sl, t.position()));
        for (int i = 0; i < 2 + r.tier / 2; i++) spots.add(ZenonAI.ground(sl, t.position().add(rnd.nextDouble(-3.5, 3.5), 0, rnd.nextDouble(-3.5, 3.5))));
        for (Vec3 c : spots) ZenonAI.warnCircle(sl, c, 1.6f, T);
        AstaFx.later(T, () -> {
            if (!ok(r)) return;
            for (Vec3 c : spots) {
                FxSend.beam(sl, r.pal(), c.add(0, 10, 0), c, 0.4f, 8);
                FxSend.ring(sl, r.pal(), c.add(0, 0.1, 0), 0.2f, 1.8f, 0.07f, 10);
                for (LivingEntity e : foes(r, sl, s, new AABB(c, c).inflate(1.6, 2.5, 1.6))) hit(r, e, dmg(r, 6f));
            }
            sl.playSound(null, BlockPosOf(spots.get(0)), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.6f, 1.5f);
        });
        return T + 6;
    }

    private static net.minecraft.core.BlockPos BlockPosOf(Vec3 v) { return net.minecraft.core.BlockPos.containing(v); }

    static int burst(Rival r, ServerLevel sl, LivingEntity t, Session s) {
        int T = 12;
        r.setPose(P_RAISE);
        Vec3 c = r.position();
        ZenonAI.warnCircle(sl, c, 4.5f, T);
        AstaFx.later(T, () -> {
            if (!ok(r)) return;
            FxSend.ring(sl, r.pal(), c.add(0, 0.6, 0), 0.5f, 4.5f, 0.25f, 10);
            for (LivingEntity e : foes(r, sl, s, new AABB(c, c).inflate(4.5, 2.5, 4.5))) {
                hit(r, e, dmg(r, 7f));
                Vec3 away = flat(e.position().subtract(c));
                if (away.lengthSqr() > 1e-4) { e.setDeltaMovement(away.normalize().scale(1.1).add(0, 0.4, 0)); e.hurtMarked = true; }
            }
            sl.playSound(null, r.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.2f, 0.8f);
        });
        return T + 6;
    }

    /** Their signature: crescents of their magic thrown out all round them, twice. */
    static int signature(Rival r, ServerLevel sl, LivingEntity t, Session s) {
        int T = 16;
        r.setPose(P_RAISE);
        Vec3 c = r.position();
        ZenonAI.warnCircle(sl, c, 6.5f, T);
        for (int w = 0; w < 2; w++) {
            final int ww = w;
            AstaFx.later(T + w * 10, () -> {
                if (!ok(r)) return;
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3 + ww * Math.PI / 6;
                    FxSend.slash(sl, r.pal(), c.add(0, 1, 0), new Vec3(Math.cos(a), 0, Math.sin(a)), new Vec3(0, 1, 0), 6f, 1f, 9, 1.4f, 0.7f);
                }
                for (LivingEntity e : foes(r, sl, s, new AABB(c, c).inflate(6.5, 2.5, 6.5))) hit(r, e, dmg(r, 7f));
                FxSend.shake(sl, c, 0.4f, 8, 14);
                sl.playSound(null, r.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.4f, 0.6f);
            });
        }
        return T + 24;
    }

    // ================================================================== who can hurt whom, and the ends of bouts
    public static boolean canHurt(Rival r, DamageSource src) {
        Session s = sessionOf(r);
        if (s == null || s.done) return false;
        if (s.kind == ALLY || s.kind == OFFER || s.kind == RACE) return false;   // allies and race-runners can't be touched
        return src.getEntity() != null && src.getEntity().getUUID().equals(s.owner);   // in a bout, only you
    }

    public static void yielded(Rival r) {
        Session s = sessionOf(r);
        if (s == null || s.done) return;
        ServerPlayer p = ((ServerLevel) r.level()).getServer().getPlayerList().getPlayer(s.owner);
        r.setPose(P_IDLE);
        FxSend.ring((ServerLevel) r.level(), r.pal(), r.position().add(0, 0.1, 0), 0.3f, 3f, 0.1f, 12);
        if (p != null) result(p, s, true);
    }

    /** In a bout you don't die to your rival: you yield at a fifth of your health. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.done || (s.kind != DUEL && s.kind != FINAL)) return;
        if (!(e.getSource().getEntity() instanceof Rival r) || !r.getUUID().equals(s.rival)) return;
        float floor = p.getMaxHealth() * 0.2f;
        if (p.getHealth() - e.getAmount() <= floor) {
            e.setAmount(Math.max(0f, p.getHealth() - floor));
            result(p, s, false);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { Session s = SESSIONS.get(p.getUUID()); if (s != null) end(p, s); }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { Session s = SESSIONS.get(p.getUUID()); if (s != null) end(p, s); }
    }

    // ================================================================== /rival
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("rival")
                .then(Commands.literal("accept").executes(c -> { accept(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("refuse").executes(c -> { refuse(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("info").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    CompoundTag t = profile(p);
                    String end = t.getInt("Ending") == 1 ? "Ally" : t.getInt("Ending") == 2 ? (t.getBoolean("FinalDone") ? "Defeated villain" : "Villain") : "Rival";
                    p.sendSystemMessage(Component.literal(t.getString("Name")).withStyle(PERS_COL[t.getInt("Pers")], ChatFormatting.BOLD)
                            .append(Component.literal("  (" + PERS[t.getInt("Pers")] + ", " + end + ")").withStyle(ChatFormatting.GRAY)));
                    p.sendSystemMessage(Component.literal("Grimoire: ").withStyle(ChatFormatting.GRAY)
                            .append(new ItemStack(GrimoireMastery.BOOKS.get(Math.min(GrimoireMastery.BOOKS.size() - 1, t.getInt("Book"))).item().get()).getHoverName())
                            .append(Component.literal("   Tier " + t.getInt("Tier") + "   You " + t.getInt("Wins") + " - " + t.getInt("Losses")
                                    + " them   Refused " + t.getInt("Ignored")).withStyle(ChatFormatting.GRAY)));
                    return 1;
                }))
                .then(Commands.literal("call").requires(s -> s.hasPermission(2)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!SESSIONS.containsKey(p.getUUID())) offer(p, profile(p));
                    return 1;
                }))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(2)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Session s = SESSIONS.get(p.getUUID());
                    if (s != null) end(p, s);
                    p.getPersistentData().remove(TAG);
                    return 1;
                })));
    }
}
