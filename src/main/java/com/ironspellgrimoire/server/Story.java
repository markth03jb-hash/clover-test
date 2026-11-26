package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.StoryPackets;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * ASHFALL, the story. A party (one mage alone is a party of one) shares one story: its arc, chapter and step, the
 * counters toward the chapter's goals, its flags and choices, the Journal's log. Every member carries their own Brand;
 * when the party's story raises it, every member's goes up (a late joiner catches up to the party's).
 * <ul>
 *   <li>What any member does counts for the whole party.</li>
 *   <li>Scenes of dialogue go to every member online. A scene with a choice is a vote: everyone online votes, the
 *       majority decides, and the leader breaks a tie (or decides alone when the time runs out).</li>
 *   <li>Leaving a party keeps your Brand and the chapter you reached: you carry on alone from its start.</li>
 * </ul>
 * The story itself is StoryArc1 (the first arc); its places are StoryStage; the Brand is Brand.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class Story {
    private Story() {}

    // ================================================================== saved state
    public static final class Party {
        public int id;
        public UUID leader;
        public final List<UUID> members = new ArrayList<>();
        public int arc = 1, ch = -1, step;   // ch -1: not begun
        public CompoundTag n = new CompoundTag();   // the chapter's counters
        public CompoundTag f = new CompoundTag();   // flags: what's been done, what was chosen
        public int brand;   // the Brand stage the party's story has reached
        public boolean target;
        public String tdim = "";
        public double tx, ty, tz;
        public final List<String> log = new ArrayList<>();
        public UUID corvin;
        // runtime
        Talk talk;
        long stepAt;
        boolean dirty = true;
        int patrols;

        public int count(String k) { return n.getInt(k); }
        public void add(String k, int v) { n.putInt(k, n.getInt(k) + v); dirty = true; }
        public boolean flag(String k) { return f.getBoolean(k); }
        public void set(String k) { f.putBoolean(k, true); dirty = true; }
        public void note(String line) { log.add(line); dirty = true; }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("Id", id);
            if (leader != null) t.putUUID("Leader", leader);
            ListTag m = new ListTag();
            for (UUID u : members) { CompoundTag x = new CompoundTag(); x.putUUID("U", u); m.add(x); }
            t.put("Members", m);
            t.putInt("Arc", arc); t.putInt("Ch", ch); t.putInt("Step", step);
            t.put("N", n.copy()); t.put("F", f.copy());
            t.putInt("Brand", brand);
            t.putBoolean("Target", target); t.putString("TDim", tdim); t.putDouble("TX", tx); t.putDouble("TY", ty); t.putDouble("TZ", tz);
            ListTag l = new ListTag();
            for (String s : log) l.add(StringTag.valueOf(s));
            t.put("Log", l);
            if (corvin != null) t.putUUID("Corvin", corvin);
            return t;
        }

        static Party load(CompoundTag t) {
            Party p = new Party();
            p.id = t.getInt("Id");
            if (t.hasUUID("Leader")) p.leader = t.getUUID("Leader");
            for (Tag x : t.getList("Members", Tag.TAG_COMPOUND)) p.members.add(((CompoundTag) x).getUUID("U"));
            p.arc = t.getInt("Arc"); p.ch = t.getInt("Ch"); p.step = t.getInt("Step");
            p.n = t.getCompound("N"); p.f = t.getCompound("F");
            p.brand = t.getInt("Brand");
            p.target = t.getBoolean("Target"); p.tdim = t.getString("TDim"); p.tx = t.getDouble("TX"); p.ty = t.getDouble("TY"); p.tz = t.getDouble("TZ");
            for (Tag x : t.getList("Log", Tag.TAG_STRING)) p.log.add(x.getAsString());
            if (t.hasUUID("Corvin")) p.corvin = t.getUUID("Corvin");
            return p;
        }
    }

    /** A scene being played to the party. With choices it's a vote. */
    static final class Talk {
        final String id;
        final List<String[]> lines;
        final String[] choices;
        final long until;
        final Map<UUID, Integer> votes = new HashMap<>();
        Talk(String id, List<String[]> lines, String[] choices, long until) { this.id = id; this.lines = lines; this.choices = choices; this.until = until; }
    }

    static final class Data extends SavedData {
        final List<Party> parties = new ArrayList<>();
        final Map<UUID, Integer> brand = new HashMap<>();
        int next = 1;

        @Override public CompoundTag save(CompoundTag t) {
            ListTag l = new ListTag();
            for (Party p : parties) l.add(p.save());
            t.put("Parties", l);
            ListTag b = new ListTag();
            for (var en : brand.entrySet()) { CompoundTag x = new CompoundTag(); x.putUUID("U", en.getKey()); x.putInt("B", en.getValue()); b.add(x); }
            t.put("Brands", b);
            t.putInt("Next", next);
            return t;
        }

        static Data load(CompoundTag t) {
            Data d = new Data();
            for (Tag x : t.getList("Parties", Tag.TAG_COMPOUND)) d.parties.add(Party.load((CompoundTag) x));
            for (Tag x : t.getList("Brands", Tag.TAG_COMPOUND)) d.brand.put(((CompoundTag) x).getUUID("U"), ((CompoundTag) x).getInt("B"));
            d.next = Math.max(1, t.getInt("Next"));
            return d;
        }
    }

    static Data data(MinecraftServer s) { return s.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "isg_story"); }
    static void dirty(MinecraftServer s) { data(s).setDirty(); }

    /** Pending invitations: who was asked, into which party (runtime; they lapse after a minute). */
    static final Map<UUID, int[]> INVITES = new HashMap<>();

    // ================================================================== parties
    public static Party partyOf(MinecraftServer s, UUID u) {
        for (Party p : data(s).parties) if (p.members.contains(u)) return p;
        return null;
    }
    public static Party partyOf(ServerPlayer p) { return partyOf(p.server, p.getUUID()); }
    static Party byId(MinecraftServer s, int id) {
        for (Party p : data(s).parties) if (p.id == id) return p;
        return null;
    }

    static Party create(ServerPlayer p) {
        Data d = data(p.server);
        Party pt = new Party();
        pt.id = d.next++;
        pt.leader = p.getUUID();
        pt.members.add(p.getUUID());
        d.parties.add(pt);
        d.setDirty();
        return pt;
    }

    /** Your party - one of you alone if you have none. */
    public static Party ensure(ServerPlayer p) {
        Party pt = partyOf(p);
        return pt != null ? pt : create(p);
    }

    public static List<ServerPlayer> online(MinecraftServer s, Party pt) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID u : pt.members) { ServerPlayer p = s.getPlayerList().getPlayer(u); if (p != null) out.add(p); }
        return out;
    }

    static ServerPlayer leader(MinecraftServer s, Party pt) { return pt.leader == null ? null : s.getPlayerList().getPlayer(pt.leader); }
    /** The leader if online, otherwise any member online (whoever the story should follow). */
    static ServerPlayer lead(MinecraftServer s, Party pt) {
        ServerPlayer l = leader(s, pt);
        if (l != null) return l;
        List<ServerPlayer> on = online(s, pt);
        return on.isEmpty() ? null : on.get(0);
    }

    /** Takes p out of their party; they keep their Brand and go on alone from the start of the chapter they'd reached. */
    static void leave(ServerPlayer p, boolean kicked) {
        MinecraftServer s = p.server;
        Party pt = partyOf(p);
        if (pt == null || pt.members.size() <= 1) { tell(p, "You're not in a party with anyone."); return; }
        pt.members.remove(p.getUUID());
        if (p.getUUID().equals(pt.leader)) pt.leader = pt.members.get(0);
        StoryStage.leaveIfInside(p);
        Party solo = create(p);
        solo.arc = pt.arc; solo.ch = pt.ch; solo.brand = pt.brand;
        solo.f = pt.f.copy(); solo.log.addAll(pt.log);
        StoryArc1.restartChapter(s, solo);
        for (ServerPlayer o : online(s, pt)) tell(o, p.getGameProfile().getName() + (kicked ? " was removed from the party." : " left the party."));
        tell(p, kicked ? "You were removed from the party. Your story goes on alone, from the start of the chapter." : "You left the party. Your story goes on alone, from the start of the chapter.");
        pt.dirty = true; solo.dirty = true;
        dirty(s);
    }

    static void join(ServerPlayer p, Party into) {
        MinecraftServer s = p.server;
        Party old = partyOf(p);
        if (old == into) return;
        if (old != null) {
            old.members.remove(p.getUUID());
            StoryStage.leaveIfInside(p);
            if (old.members.isEmpty()) { StoryArc1.dissolve(s, old); data(s).parties.remove(old); }
            else { if (p.getUUID().equals(old.leader)) old.leader = old.members.get(0); old.dirty = true; }
        }
        into.members.add(p.getUUID());
        if (brand(p) < into.brand) setBrand(p, into.brand);   // a late joiner catches up to the party's Brand
        for (ServerPlayer o : online(s, into)) if (o != p) tell(o, p.getGameProfile().getName() + " joined the party.");
        tell(p, "You joined the party. Its story is yours now" + (into.ch >= 0 ? " - the Journal tells what you missed." : "."));
        into.dirty = true;
        dirty(s);
    }

    // ================================================================== the Brand (each member's own)
    public static int brand(ServerPlayer p) { return data(p.server).brand.getOrDefault(p.getUUID(), 0); }
    static void setBrand(ServerPlayer p, int stage) {
        data(p.server).brand.put(p.getUUID(), stage);
        dirty(p.server);
        Party pt = partyOf(p);
        if (pt != null) pt.dirty = true;
    }
    /** The party's story raises the Brand: every member's goes up to it (those away get it when they're back). */
    static void raiseBrand(MinecraftServer s, Party pt, int stage) {
        pt.brand = Math.max(pt.brand, stage);
        for (ServerPlayer p : online(s, pt)) if (brand(p) < pt.brand) setBrand(p, pt.brand);
        pt.dirty = true;
        dirty(s);
    }

    // ================================================================== scenes and votes
    public static List<String[]> lines(String... speakerAndText) {
        List<String[]> l = new ArrayList<>();
        for (int i = 0; i + 1 < speakerAndText.length; i += 2) l.add(new String[]{speakerAndText[i], speakerAndText[i + 1]});
        return l;
    }

    /** Plays a scene to every member online. It ends when the leader (or anyone, with the leader away) has read it, or in time. */
    static void talk(MinecraftServer s, Party pt, String id, List<String[]> lines, String... choices) {
        long now = s.overworld().getGameTime();
        long until = now + 60L * lines.size() + (choices.length > 0 ? 1800 : 400);
        pt.talk = new Talk(id, lines, choices, until);
        CompoundTag t = new CompoundTag();
        t.putString("id", id);
        ListTag ls = new ListTag();
        for (String[] l : lines) { CompoundTag x = new CompoundTag(); x.putString("s", l[0]); x.putString("t", l[1]); ls.add(x); }
        t.put("lines", ls);
        ListTag cs = new ListTag();
        for (String c : choices) cs.add(StringTag.valueOf(c));
        t.put("choices", cs);
        t.putInt("of", online(s, pt).size());
        for (ServerPlayer p : online(s, pt)) AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), new StoryPackets.Talk(t));
    }

    static boolean talking(Party pt) { return pt.talk != null; }

    static void vote(ServerPlayer p, Party pt, String id, int choice) {
        Talk t = pt.talk;
        if (t == null || !t.id.equals(id) || t.choices.length == 0 || choice < 0 || choice >= t.choices.length) return;
        t.votes.put(p.getUUID(), choice);
        CompoundTag up = new CompoundTag();
        up.putString("id", id); up.putInt("votes", t.votes.size()); up.putInt("of", online(p.server, pt).size());
        for (ServerPlayer o : online(p.server, pt)) AddonNetwork.send(PacketDistributor.PLAYER.with(() -> o), new StoryPackets.Talk(up));
        if (t.votes.size() >= online(p.server, pt).size()) resolve(p.server, pt);
    }

    /** The majority; a tie goes the leader's way (or to the first choice tied, if the leader didn't vote). */
    static int decide(Party pt, Talk t) {
        int[] n = new int[t.choices.length];
        for (int v : t.votes.values()) n[v]++;
        int best = 0;
        for (int i = 1; i < n.length; i++) if (n[i] > n[best]) best = i;
        Integer lv = pt.leader == null ? null : t.votes.get(pt.leader);
        if (lv != null && n[lv] == n[best]) return lv;
        return best;
    }

    static void resolve(MinecraftServer s, Party pt) {
        Talk t = pt.talk;
        if (t == null) return;
        pt.talk = null;
        int choice = -1;
        if (t.choices.length > 0) {
            choice = decide(pt, t);
            CompoundTag r = new CompoundTag();
            r.putString("id", t.id); r.putInt("result", choice); r.putString("text", t.choices[choice]);
            for (ServerPlayer o : online(s, pt)) {
                AddonNetwork.send(PacketDistributor.PLAYER.with(() -> o), new StoryPackets.Talk(r));
                if (online(s, pt).size() > 1) o.sendSystemMessage(Component.literal("The party chose: ").withStyle(ChatFormatting.GOLD).append(Component.literal(t.choices[choice]).withStyle(ChatFormatting.YELLOW)));
            }
        }
        StoryArc1.onTalkDone(s, pt, t.id, choice);
        pt.dirty = true;
    }

    // ================================================================== what the player sends
    public static void act(ServerPlayer p, CompoundTag t) {
        String a = t.getString("a");
        Party pt = partyOf(p);
        switch (a) {
            case "done" -> {
                if (pt == null || pt.talk == null || !pt.talk.id.equals(t.getString("id")) || pt.talk.choices.length > 0) return;
                ServerPlayer l = leader(p.server, pt);
                if (l == null || l == p) resolve(p.server, pt);
            }
            case "vote" -> { if (pt != null) vote(p, pt, t.getString("id"), t.getInt("choice")); }
            case "brand" -> Brand.use(p);
            case "journal" -> sync(p, "journal");
            case "begin" -> begin(p);
            case "enter" -> { if (pt != null) StoryArc1.enterRequest(p.server, pt, p); }
            default -> {}
        }
    }

    static void begin(ServerPlayer p) {
        Party pt = ensure(p);
        if (pt.ch >= 0) { sync(p, "journal"); return; }
        if (!p.getUUID().equals(pt.leader)) { tell(p, "Only the party's leader can begin the story."); return; }
        StoryArc1.begin(p.server, pt);
    }

    // ================================================================== the journal and tracker
    public static void sync(ServerPlayer p, String open) {
        Party pt = partyOf(p);
        CompoundTag t = new CompoundTag();
        t.putString("open", open == null ? "" : open);
        t.putInt("brand", brand(p));
        int[] b = Brand.state(p);
        t.putInt("brandCd", b[0]); t.putInt("brandOn", b[1]);
        if (pt != null) {
            MinecraftServer s = p.server;
            t.putBoolean("has", true);
            t.putBoolean("leader", p.getUUID().equals(pt.leader));
            t.putInt("arc", pt.arc); t.putInt("ch", pt.ch);
            ListTag ms = new ListTag();
            for (UUID u : pt.members) {
                CompoundTag x = new CompoundTag();
                ServerPlayer o = s.getPlayerList().getPlayer(u);
                x.putString("n", o != null ? o.getGameProfile().getName() : s.getProfileCache() != null ? s.getProfileCache().get(u).map(g -> g.getName()).orElse("?") : "?");
                x.putBoolean("on", o != null);
                x.putBoolean("lead", u.equals(pt.leader));
                ms.add(x);
            }
            t.put("members", ms);
            StoryArc1.journal(s, pt, t);
            if (pt.target) {
                CompoundTag g = new CompoundTag();
                g.putString("dim", pt.tdim); g.putDouble("x", pt.tx); g.putDouble("y", pt.ty); g.putDouble("z", pt.tz);
                t.put("target", g);
            }
            ListTag lg = new ListTag();
            for (String s1 : pt.log) lg.add(StringTag.valueOf(s1));
            t.put("log", lg);
        }
        int[] inv = INVITES.get(p.getUUID());
        if (inv != null) t.putString("invite", "party " + inv[0]);
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), new StoryPackets.Sync(t));
    }

    public static void sync(MinecraftServer s, Party pt) {
        for (ServerPlayer p : online(s, pt)) sync(p, null);
        pt.dirty = false;
    }

    static void tell(ServerPlayer p, String s) { p.sendSystemMessage(Component.literal(s).withStyle(ChatFormatting.GOLD)); }
    static void tellAll(MinecraftServer s, Party pt, Component c) { for (ServerPlayer p : online(s, pt)) p.sendSystemMessage(c); }

    // ================================================================== events
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        Data d = data(s);
        if (d.parties.isEmpty()) return;
        long now = s.overworld().getGameTime();
        for (Party pt : new ArrayList<>(d.parties)) {
            List<ServerPlayer> on = online(s, pt);
            if (on.isEmpty()) continue;
            if (pt.talk != null && now >= pt.talk.until) resolve(s, pt);
            if (s.getTickCount() % 10 == 0) StoryArc1.tick(s, pt, on);
            if (pt.dirty && s.getTickCount() % 5 == 0) { sync(s, pt); d.setDirty(); }
        }
        if (s.getTickCount() % 20 == 0) INVITES.entrySet().removeIf(en -> en.getValue()[1] < s.getTickCount());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Party pt = partyOf(p);
        if (pt != null && brand(p) < pt.brand) setBrand(p, pt.brand);
        sync(p, null);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onKill(LivingDeathEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof Mob m)) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer p)) { StoryArc1.onDeath(m, null); return; }
        StoryArc1.onDeath(m, p);
        if (!(m instanceof Enemy)) return;
        Party pt = partyOf(p);
        if (pt != null && pt.ch >= 0) StoryArc1.onKill(p.server, pt, p, m);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCast(io.redspace.ironsspellbooks.api.events.SpellOnCastEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer p)) return;
        Party pt = partyOf(p);
        if (pt != null && pt.ch >= 0) StoryArc1.onCast(p.server, pt, p);
    }

    @SubscribeEvent
    public static void onStopping(net.minecraftforge.event.server.ServerStoppingEvent e) { INVITES.clear(); }

    // ================================================================== /story
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("story")
                .executes(c -> { sync(c.getSource().getPlayerOrException(), "journal"); return 1; })
                .then(Commands.literal("begin").executes(c -> { begin(c.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("party")
                        .executes(c -> list(c.getSource()))
                        .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(c -> invite(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player")))))
                        .then(Commands.literal("accept").executes(c -> accept(c.getSource().getPlayerOrException())))
                        .then(Commands.literal("leave").executes(c -> { leave(c.getSource().getPlayerOrException(), false); return 1; }))
                        .then(Commands.literal("kick").then(Commands.argument("player", EntityArgument.player()).executes(c -> kick(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player")))))
                        .then(Commands.literal("lead").then(Commands.argument("player", EntityArgument.player()).executes(c -> lead(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player"))))))
                .then(Commands.literal("admin").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("chapter").then(Commands.argument("ch", IntegerArgumentType.integer(-1, 5)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Party pt = ensure(p);
                            StoryArc1.toChapter(p.server, pt, IntegerArgumentType.getInteger(c, "ch"));
                            c.getSource().sendSuccess(() -> Component.literal("Story: chapter " + pt.ch), true);
                            return 1;
                        })))
                        .then(Commands.literal("brand").then(Commands.argument("stage", IntegerArgumentType.integer(0, 4)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            setBrand(p, IntegerArgumentType.getInteger(c, "stage"));
                            sync(p, null);
                            return 1;
                        })))
                        .then(Commands.literal("reset").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Party pt = partyOf(p);
                            if (pt != null) { StoryStage.leaveIfInside(p); StoryArc1.dissolve(p.server, pt); data(p.server).parties.remove(pt); }
                            data(p.server).brand.remove(p.getUUID());
                            dirty(p.server);
                            sync(p, null);
                            c.getSource().sendSuccess(() -> Component.literal("Story reset."), true);
                            return 1;
                        }))));
    }

    static int list(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        Party pt = partyOf(p);
        if (pt == null) { tell(p, "You have no party yet. /story begin starts your story; /story party invite <player> brings someone in."); return 1; }
        StringBuilder sb = new StringBuilder();
        for (UUID u : pt.members) {
            ServerPlayer o = p.server.getPlayerList().getPlayer(u);
            String n = o != null ? o.getGameProfile().getName() : "(away)";
            sb.append(sb.length() > 0 ? ", " : "").append(u.equals(pt.leader) ? n + " (leader)" : n);
        }
        tell(p, "Your party: " + sb);
        return 1;
    }

    static int invite(ServerPlayer p, ServerPlayer who) {
        if (who == p) { tell(p, "You're already in your own party."); return 0; }
        Party pt = ensure(p);
        if (!p.getUUID().equals(pt.leader)) { tell(p, "Only the party's leader can invite."); return 0; }
        if (pt.members.contains(who.getUUID())) { tell(p, who.getGameProfile().getName() + " is already in your party."); return 0; }
        INVITES.put(who.getUUID(), new int[]{pt.id, p.server.getTickCount() + 1200});
        tell(p, "You invited " + who.getGameProfile().getName() + ". They have a minute to accept.");
        Party theirs = partyOf(who);
        who.sendSystemMessage(Component.literal(p.getGameProfile().getName() + " invites you into their party (ASHFALL). ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("[Accept]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/story party accept")))));
        if (theirs != null && theirs.ch >= 0) who.sendSystemMessage(Component.literal("  Joining leaves your own story where it is; you keep your Brand.").withStyle(ChatFormatting.GRAY));
        sync(who, null);
        return 1;
    }

    static int accept(ServerPlayer p) {
        int[] inv = INVITES.remove(p.getUUID());
        Party into = inv == null ? null : byId(p.server, inv[0]);
        if (into == null) { tell(p, "You have no invitation (they last a minute)."); return 0; }
        join(p, into);
        return 1;
    }

    static int kick(ServerPlayer p, ServerPlayer who) {
        Party pt = partyOf(p);
        if (pt == null || !p.getUUID().equals(pt.leader) || who == p || !pt.members.contains(who.getUUID())) { tell(p, "Only the leader can remove someone from the party."); return 0; }
        leave(who, true);
        return 1;
    }

    static int lead(ServerPlayer p, ServerPlayer who) {
        Party pt = partyOf(p);
        if (pt == null || !p.getUUID().equals(pt.leader) || !pt.members.contains(who.getUUID())) { tell(p, "Only the leader can hand over the lead, to someone in the party."); return 0; }
        pt.leader = who.getUUID();
        pt.dirty = true;
        dirty(p.server);
        for (ServerPlayer o : online(p.server, pt)) tell(o, who.getGameProfile().getName() + " leads the party now.");
        return 1;
    }

    // ================================================================== for the tests
    static void testClear(MinecraftServer s) { data(s).parties.clear(); data(s).brand.clear(); INVITES.clear(); }
    static Party testParty(ServerPlayer p) { return ensure(p); }
    static void testJoin(ServerPlayer p, Party into) { join(p, into); }
}
