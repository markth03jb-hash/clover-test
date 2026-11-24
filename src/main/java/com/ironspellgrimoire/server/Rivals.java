package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.BossKit.*;
import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.AddonConfig;
import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.AddonBoss;
import com.ironspellgrimoire.entity.AlderBellmar;
import com.ironspellgrimoire.entity.MiraHollowell;
import com.ironspellgrimoire.entity.Rival;
import com.ironspellgrimoire.entity.WrenBellmar;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.RivalPackets;
import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import com.ironspellgrimoire.registry.AddonSounds;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.RegistryObject;

/**
 * Rivals: four human mages who come looking for you once your grimoire makes a name for you (threat rank 2+), about
 * once every few in-game days - or when you call one with a Rival's Token from the bounty board.
 * <ul>
 *   <li>They fight inside a ring (16 blocks). Step out and they stop, wait and taunt; blows from outside the ring
 *       don't land. Stay away a minute and they leave.</li>
 *   <li>They talk: about your grimoire, your record against them, and what happened last time.</li>
 *   <li>They never die: at the end they yield. Each win raises their rank for the rematch (up to 3): more health,
 *       harder blows and one more move per rank.</li>
 *   <li>A win pays merit stars and mastery XP; the first win against each also gives their keepsake (a charm) and a
 *       title (an advancement). Records are kept on you ("isg_rivals") and shown in the Codex.</li>
 * </ul>
 * The fights: {@link SerenAI}, {@link BrannochAI}, {@link TwinsAI}, {@link MiraAI}.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class Rivals {
    private Rivals() {}

    public static final int SEREN = 0, BRANNOCH = 1, TWINS = 2, MIRA = 3, COUNT = 4;
    public static final float RING = 16f;
    public static final String KEY = "isg_rivals", NEXT = "isg_rival_next";
    /** No unasked-for visits during the automatic test and screenshot runs. */
    static final boolean AUTO = !(Boolean.getBoolean("isg.gametests") || Boolean.getBoolean("isg.uishots"));
    public static final String[] NAMES = {"Seren Vale", "Brannoch Tor", "The Bellmar Twins", "Mira Hollowell"};
    public static final String[] EPITHETS = {"the Glass Duelist", "the Siege-Lord", "Alder and Wren", "the Debt-Keeper"};
    public static final String[] TITLES = {"Glassbreaker", "Siege-Breaker", "The Third Bell", "Debt-Free"};
    public static final ChatFormatting[] COLOR = {ChatFormatting.AQUA, ChatFormatting.RED, ChatFormatting.GOLD, ChatFormatting.YELLOW};
    /** Each rival's colours: the ring, and their magic. */
    public static final int[] PAL = {Fx.MIRROR, Fx.CRIMSON, Fx.GOLD, Fx.GOLD};
    public static final String[] ABOUT = {
            "A duelist with a blade of light. She steps through her own reflections, parries anything you throw while her blade is raised, and at noon only the shade of her glass is safe.",
            "A warlord who brings his Legion. Shield walls, a war horn, waves of soldiers - and while his standard stands he shrugs off half of every blow. Bring down the bearer.",
            "Two siblings, one fight: Alder's sword and Wren's bells. They swap places, shoot through each other, and when one falls the other grieves - beat them both before the first gets up.",
            "She fights with coins and contracts. She offers deals mid-fight (accept or refuse), borrows your spells, holds your mana as collateral and charges interest. At the end, she forecloses."};
    public static final String[] MOVES = {
            "Refraction Step, Spectrum Blades, Mirror Parry (don't hit her while her blade glows), White Noon (stand in the shade of a prism). Sidesteps the first blow every few seconds.",
            "Cleave, Shield Wall, Warhorn, the Standard and its bearer, Legion waves, Siege Walls. Legionnaires block blows from the front: go round.",
            "Swap, Bond Shot (the bolt splits when it passes through Alder), Peal, Grief, Two as One (don't cross the tether).",
            "Coin Volley, Ledger Slam, Deals (Y to accept, N to refuse), Borrow (one of your spells is hers for a while), Collateral (hit her three times to get your mana back), Compound Interest, Foreclosure."};
    public static final String[] RANK_MOVES = {
            "Rank 1: Prism Cage.  Rank 2: Afterimage.  Rank 3: Noon's Edge.",
            "Rank 1: Shield Bash.  Rank 2: Testudo.  Rank 3: Muster.",
            "Rank 1: Echo Bell.  Rank 2: Back to Back.  Rank 3: Last Bell.",
            "Rank 1: Late Fee.  Rank 2: Hostile Takeover.  Rank 3: Bankruptcy."};

    // ------------------------------------------------------------------ what they say ("Who|line" for the twins)
    static final String[][] FIRST = {
            {"You're the one they talk about. Draw - I'd like to see if the stories are true."},
            {"Tor's Legion holds this field. You hold a grimoire. Let's see which holds longer."},
            {"Alder|Wren, look - the one from the posters.", "Wren|Two against one isn't fair, Alder.", "Alder|It's never stopped us before."},
            {"Mira Hollowell: debts and accounts. You owe the world a good fight, and I'm here to collect."}};
    static final String[][] AGAIN = {
            {"Fight {n}. You've won {wins}. I've been practising the step you kept catching.", "Back again? Good. I hate an even score."},
            {"Back for more, mage? Fight {n}. My Legion remembers you.", "{wins} wins against Tor. Don't let it go to your head."},
            {"Wren|Round {n}! I've been counting.", "Alder|{wins} to them, Wren. Let's even it."},
            {"Account {n}, still open. Shall we settle?", "You've won {wins} times. I've been charging interest on every one."}};
    static final String[] BOOK = {
            "That {book} of yours - loud magic. Mine is quiet. Watch.",
            "A {book}? Every wall I ever built was built against magic like that.",
            "Alder|A {book}. Wren, keep your bells up.",
            "A {book}! Lovely collateral. Shall we put a price on it?"};
    static final String[][] TAUNT = {
            {"The ring is right here. Step in.", "I don't chase. Come back.", "Afraid of a little glass?"},
            {"Coward's ground out there. Come to the line!", "The Legion waits. I wait.", "Hah! My youngest recruit stands in the ring."},
            {"Wren|Come back! We can't ring for an empty field.", "Alder|Running? Our mother ran faster than that."},
            {"Interest accrues while you stand out there, dear.", "The ledger waits inside the ring.", "My cat is bored. Come back."}};
    static final String[][] PHASE = {
            {"Good. Now I stop holding back the light.", "Noon is coming. Find the shade - if you can."},
            {"Raise the standard! Show them what Tor means!", "Walls! Walls on every side!"},
            {"Alder|Swap, Wren - keep them guessing!", "Wren|Together, Alder - two as one!"},
            {"Let's renegotiate.", "Foreclosure. Everything inside these lines is mine."}};
    static final String[] YIELD = {
            "Enough - enough. You saw the step. Take it; you've earned it.",
            "Hold - HOLD. Lower your arms, Legion. The mage has the field.",
            "Wren|We yield. Both of us. Ring this for us sometime.",
            "Paid in full. I hate saying that. Here - one for your ledger."};
    static final String[] WIN = {
            "Breathe. Next time, watch my feet.",
            "Carry them off gently. They fought well.",
            "Alder|Easy, easy - we've got them. Wren, a potion!",
            "Debt settled. Do come again."};
    static final String[] LEAVE = {
            "Find me when you're ready to stand in the ring.",
            "We march. Find us again.",
            "Wren|We'll come back when you're braver!",
            "Account closed - for now."};

    public static List<Supplier<? extends EntityType<? extends Rival>>> types(int id) {
        return switch (id) {
            case SEREN -> List.of(AddonEntities.SEREN_VALE);
            case BRANNOCH -> List.of(AddonEntities.BRANNOCH_TOR);
            case TWINS -> List.of(AddonEntities.ALDER_BELLMAR, AddonEntities.WREN_BELLMAR);
            default -> List.of(AddonEntities.MIRA_HOLLOWELL);
        };
    }

    static RegistryObject<Item> keepsake(int id) {
        return switch (id) {
            case SEREN -> AddonItemRegistry.PRISM_RIBBON;
            case BRANNOCH -> AddonItemRegistry.WARLORDS_STANDARD;
            case TWINS -> AddonItemRegistry.LAST_BELL;
            default -> AddonItemRegistry.LEDGER_COIN;
        };
    }

    // ================================================================== records
    static CompoundTag rec(Player p, int id) {
        CompoundTag all = p.getPersistentData().getCompound(KEY);
        CompoundTag r = all.getCompound("r" + id);
        all.put("r" + id, r);
        p.getPersistentData().put(KEY, all);
        return r;
    }
    public static int wins(Player p, int id) { return rec(p, id).getInt("Wins"); }
    public static int met(Player p, int id) { return rec(p, id).getInt("Met"); }
    public static int rankFor(Player p, int id) { return Math.min(3, wins(p, id)); }

    public static void sync(ServerPlayer p) {
        int[] met = new int[COUNT], wins = new int[COUNT], losses = new int[COUNT];
        for (int i = 0; i < COUNT; i++) { CompoundTag r = rec(p, i); met[i] = r.getInt("Met"); wins[i] = r.getInt("Wins"); losses[i] = r.getInt("Losses"); }
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), new RivalPackets.Sync(met, wins, losses));
    }

    @SubscribeEvent public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) sync(p); }

    // ================================================================== calling a rival
    /** Calls rival {@code id} to fight {@code p}: the ring's middle between you, them across it. Returns the first body. */
    public static Rival call(ServerPlayer p, int id) { return call(p, id, rankFor(p, id)); }

    public static Rival call(ServerPlayer p, int id, int rank) {
        ServerLevel sl = p.serverLevel();
        Vec3 look = flat(p.getLookAngle());
        if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        Vec3 c = spot(sl, p.position().add(look.scale(7)), p.getY());
        Rival first = null;
        List<Supplier<? extends EntityType<? extends Rival>>> ts = types(id);
        List<Rival> made = new ArrayList<>();
        for (int i = 0; i < ts.size(); i++) {
            Rival r = ts.get(i).get().create(sl);
            if (r == null) continue;
            Vec3 at = spot(sl, c.add(look.scale(6)).add(rightOf(look).scale((i - (ts.size() - 1) * 0.5) * 3)), c.y);
            float yaw = (float) Math.toDegrees(Math.atan2(look.x, -look.z));
            r.moveTo(at.x, at.y, at.z, yaw, 0);
            r.setYHeadRot(yaw); r.yBodyRot = yaw;
            r.arena = c;
            r.challenger = p.getUUID();
            r.focus = p.getUUID();
            r.setRank(rank);
            sl.addFreshEntity(r);
            made.add(r);
            if (first == null) first = r;
        }
        if (made.size() == 2) { made.get(0).partner = made.get(1).getUUID(); made.get(1).partner = made.get(0).getUUID(); }
        snd(sl, c, "rival_arena", 1.4f, 1f);
        BossFx.ring(sl, PAL[id], c.add(0, 0.06, 0), RING - 0.2f, RING, 0.08f, 30);
        BossFx.ring(sl, PAL[id], c.add(0, 0.06, 0), 0.2f, RING, 0.02f, 16);
        p.displayClientMessage(Component.literal(NAMES[id] + ", " + EPITHETS[id] + ", has come for you." + (rank > 0 ? "  (Rank " + rank + ")" : ""))
                .withStyle(COLOR[id], ChatFormatting.BOLD), true);
        return first;
    }

    /** Solid ground near {@code at} (searching from a little above {@code y}). */
    static Vec3 spot(ServerLevel sl, Vec3 at, double y) {
        Vec3 g = ground(sl, new Vec3(at.x, y + 3, at.z));
        if (g.y == y + 3) g = ground(sl, new Vec3(at.x, y + 0.5, at.z));
        return g.y > y + 2.5 ? new Vec3(at.x, y, at.z) : g;
    }

    /** The token: calls the rival it's set to, if none is about already. */
    public static boolean useToken(ServerPlayer p, int id) {
        if (busy(p)) { p.displayClientMessage(Component.literal("A rival is already here.").withStyle(ChatFormatting.GRAY), true); return false; }
        call(p, id);
        return true;
    }

    static boolean busy(Player p) {
        return !p.level().getEntitiesOfClass(AddonBoss.class, p.getBoundingBox().inflate(48), Entity::isAlive).isEmpty();
    }

    // ================================================================== they come on their own
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 400 != 77) return;
        if (!AUTO || !AddonConfig.rivals() || p.isCreative() || p.isSpectator() || p.level().dimension() != Level.OVERWORLD) return;
        if (EliteMobs.threat(p) < 2) return;
        long now = p.serverLevel().getGameTime();
        CompoundTag d = p.getPersistentData();
        long gap = (long) (AddonConfig.rivalDays() * 24000);
        if (!d.contains(NEXT)) { d.putLong(NEXT, now + gap / 2 + ThreadLocalRandom.current().nextLong(gap)); return; }
        if (now < d.getLong(NEXT) || busy(p) || p.isInWater() || p.isPassenger()) return;
        int id = pick(p);
        if (id < 0) { d.putLong(NEXT, now + gap); return; }
        d.putLong(NEXT, now + (long) (gap * (0.7 + 0.6 * ThreadLocalRandom.current().nextDouble())));
        call(p, id);
    }

    /** Which rival comes: one you've beaten fewest times (those beaten at rank 3 come no more on their own). */
    static int pick(Player p) {
        List<Integer> best = new ArrayList<>();
        int least = Integer.MAX_VALUE;
        for (int i = 0; i < COUNT; i++) {
            int w = wins(p, i);
            if (w > 3) continue;
            if (w < least) { least = w; best.clear(); }
            if (w == least) best.add(i);
        }
        return best.isEmpty() ? -1 : best.get(ThreadLocalRandom.current().nextInt(best.size()));
    }

    // ================================================================== the ring
    static double flatDist(Vec3 a, Vec3 b) { double dx = a.x - b.x, dz = a.z - b.z; return Math.sqrt(dx * dx + dz * dz); }
    public static boolean inRing(Rival b, Entity e) { return b.arena == null || flatDist(e.position(), b.arena) <= RING + 0.5; }

    public static void tick(Rival b) {
        ServerLevel sl = (ServerLevel) b.level();
        long now = sl.getGameTime();
        if (b.arena == null) b.arena = b.position();
        int id = b.rivalId();
        boolean lead = !(b instanceof WrenBellmar);   // the twins draw one ring and speak once
        if (lead && now % 20 == 0) {
            Vec3 g = ground(sl, b.arena.add(0, 1, 0)).add(0, 0.05, 0);
            BossFx.ring(sl, PAL[id], g, RING - 0.15f, RING, 0.06f, 22);
        }
        if (b.challenger != null && b.focus == null) b.focus = b.challenger;
        Player t = target(b);
        if (t != null && !b.met) { b.met = true; if (lead) greet(b, t); }
        if (t == null || !inRing(b, t)) {
            b.away++;
            b.busy = Math.max(b.busy, 10);
            if (flatDist(b.position(), b.arena) > 2.5) b.getNavigation().moveTo(b.arena.x, b.arena.y, b.arena.z, 1.0);
            else b.getNavigation().stop();
            if (t != null) b.getLookControl().setLookAt(t, 30, 30);
            if (lead && t != null && b.away % 160 == 40) line(b, TAUNT[id][(int) ((now / 160) % TAUNT[id].length)]);
            if (b.away > 1200) leave(b, true);
            return;
        }
        b.away = 0;
        if (flatDist(b.position(), b.arena) > RING - 1.5 && b.busy <= 0) b.getNavigation().moveTo(b.arena.x, b.arena.y, b.arena.z, 1.1);
        b.fight();
    }

    static void greet(Rival b, Player t) {
        int id = b.rivalId();
        CompoundTag r = rec(t, id);
        int n = r.getInt("Met") + 1, wins = r.getInt("Wins");
        r.putInt("Met", n);
        if (t instanceof ServerPlayer sp) sync(sp);
        String[] lines = n == 1 ? FIRST[id] : new String[]{AGAIN[id][n % AGAIN[id].length]};
        int k = 0;
        for (String l : lines) { String s = l.replace("{n}", String.valueOf(n)).replace("{wins}", String.valueOf(wins)); AstaFx.later(k, () -> line(b, s)); k += 30; }
        ItemStack book = Utils.getPlayerSpellbookStack(t);
        if (book != null && GrimoireMastery.bookOf(book) != null) {
            String s = BOOK[id].replace("{book}", book.getHoverName().getString());
            AstaFx.later(k + 20, () -> line(b, s));
        }
    }

    /** Says a line ("Alder|...": that twin speaks). */
    static void line(Rival b, String l) {
        if (!b.isAlive()) return;
        int bar = l.indexOf('|');
        String who = bar < 0 ? NAMES[b.rivalId()] : l.substring(0, bar) + " Bellmar";
        say(b, who, COLOR[b.rivalId()], bar < 0 ? l : l.substring(bar + 1));
    }

    public static float taken(Rival b, DamageSource src) {
        if (b.yielded) return 0f;
        if (src.getEntity() instanceof Player p && !inRing(b, p)) {
            p.displayClientMessage(Component.literal(NAMES[b.rivalId()] + " won't trade blows across the ring - step inside.").withStyle(ChatFormatting.GRAY), true);
            return 0f;
        }
        return b.guard(src);
    }

    public static void hurtBy(Rival b, DamageSource src, float amount) {
        if (b instanceof MiraHollowell m && src.getEntity() instanceof ServerPlayer p) MiraAI.struck(m, p);
    }

    public static void phase(Rival b, int phase) {
        if (!(b instanceof WrenBellmar) && phase >= 2) line(b, PHASE[b.rivalId()][Math.min(1, phase - 2)]);
        b.phaseUp(phase);
    }

    /** A blow on a player, scaled by difficulty and the rival's rank (and Mira's deals). */
    static void hit(Rival b, Player p, float dmg) {
        if (!inRing(b, p)) return;
        dmg *= (1f + 0.15f * b.rank) * TwinsAI.mult(b);
        if (b instanceof MiraHollowell m) dmg = MiraAI.outgoing(m, p, dmg);
        hurt(b, p, dmg);
    }

    // ================================================================== yielding
    public static void yieldNow(Rival b) {
        b.yielded = true;
        b.setTransforming(600);
        b.setFlag(b.flag() | Rival.YIELDED);
        b.setPose(P_IDLE);
        b.getNavigation().stop();
        if ((b instanceof AlderBellmar || b instanceof WrenBellmar) && !TwinsAI.bothDown(b)) { TwinsAI.down(b); return; }
        won(b);
    }

    public static void yieldTick(Rival b) {
        b.setTransforming(Math.max(b.transforming(), 20));
        if (!b.over) { TwinsAI.downTick(b); return; }
        if (++b.yieldT >= 90) vanish(b);
    }

    static void won(Rival b) {
        ServerLevel sl = (ServerLevel) b.level();
        int id = b.rivalId();
        List<Rival> all = new ArrayList<>(List.of(b));
        Rival other = TwinsAI.partner(b);
        if (other != null) all.add(other);
        for (Rival r : all) { r.over = true; r.yielded = true; r.setFlag(r.flag() | Rival.YIELDED); r.yieldT = 0; }
        line(b, YIELD[id]);
        snd(sl, b.position(), "rival_yield", 1.3f, 1f);
        for (Player p : sl.players()) {
            if (!(p instanceof ServerPlayer sp) || sp.isSpectator() || !inRing(b, sp) || sp.distanceTo(b) > 40) continue;
            reward(sp, b);
        }
    }

    static void reward(ServerPlayer p, Rival b) {
        int id = b.rivalId();
        CompoundTag r = rec(p, id);
        int wins = r.getInt("Wins") + 1;
        r.putInt("Wins", wins);
        int stars = 2 + b.rank;
        Bounties.addStars(p, stars);
        GrimoireMastery.bountyXp(p, 400 + 250 * b.rank);
        StringBuilder msg = new StringBuilder("You beat " + NAMES[id] + "  (+" + stars + "★, mastery XP)");
        if (wins == 1) {
            ItemStack k = new ItemStack(keepsake(id).get());
            if (!p.getInventory().add(k)) p.drop(k, false);
            Adv.grant(p, "rival_" + new String[]{"seren", "brannoch", "twins", "mira"}[id]);
            msg.append("  - title: ").append(TITLES[id]);
            p.sendSystemMessage(Component.literal("Keepsake: ").withStyle(ChatFormatting.GRAY).append(k.getHoverName().copy().withStyle(COLOR[id])));
        }
        if (wins <= 3) msg.append("  - next time, rank ").append(Math.min(3, wins));
        p.sendSystemMessage(Component.literal(msg.toString()).withStyle(COLOR[id]));
        sync(p);
    }

    static void vanish(Rival b) {
        ServerLevel sl = (ServerLevel) b.level();
        Vec3 c = b.position();
        BossFx.ring(sl, PAL[b.rivalId()], c.add(0, 0.1, 0), 0.2f, 2.2f, 0.1f, 10);
        BossFx.beam(sl, PAL[b.rivalId()], c, c.add(0, 2.4, 0), 0.25f, 8);
        if (b instanceof com.ironspellgrimoire.entity.BrannochTor bt) BrannochAI.dismiss(bt);
        if (b instanceof MiraHollowell)
            for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, b.getBoundingBox().inflate(64))) {
                if (MiraAI.offering(p)) AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), new RivalPackets.Offer(-1, "", "", 0));
                MiraAI.settle(p);
            }
        b.discard();
    }

    /** They leave without a result (you stayed out of the ring, or fell). */
    static void leave(Rival b, boolean say) {
        if (say && !(b instanceof WrenBellmar)) line(b, LEAVE[b.rivalId()]);
        Rival o = TwinsAI.partner(b);
        vanish(b);
        if (o != null) vanish(o);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        for (Rival b : p.serverLevel().getEntitiesOfClass(Rival.class, p.getBoundingBox().inflate(48), r -> r.isAlive() && !r.over)) {
            if (b instanceof WrenBellmar && TwinsAI.partner(b) != null) continue;
            CompoundTag r = rec(p, b.rivalId());
            r.putInt("Losses", r.getInt("Losses") + 1);
            sync(p);
            line(b, WIN[b.rivalId()]);
            AstaFx.later(60, () -> { if (b.isAlive()) leave(b, false); });
        }
    }

    // ================================================================== shared moves and helpers
    static void snd(ServerLevel sl, Vec3 at, String kind, float vol, float pitch) {
        SoundEvent s = AddonSounds.get(kind);
        if (s != null) sl.playSound(null, at.x, at.y, at.z, s, SoundSource.HOSTILE, vol, pitch);
    }
    static void snd(Rival b, String kind, float vol, float pitch) { snd((ServerLevel) b.level(), b.position(), kind, vol, pitch); }

    /** A sweeping cut in front: everyone within {@code r} and {@code deg} of {@code dir}. */
    static void arc(Rival b, int pal, Vec3 dir, float r, float deg, float dmg) {
        ServerLevel sl = (ServerLevel) b.level();
        Vec3 c = b.position().add(0, b.getBbHeight() * 0.55, 0);
        BossFx.slash(sl, pal, c, dir, new Vec3(0, 1, 0), r, 0.3f, 7, 1.6f);
        double cos = Math.cos(Math.toRadians(deg / 2));
        for (Player p : near(b, c, r)) {
            Vec3 to = flat(p.position().subtract(b.position()));
            if (to.lengthSqr() < 0.5 || to.normalize().dot(dir) >= cos) hit(b, p, dmg);
        }
    }

    /** A bolt that flies {@code steps} ticks along {@code dir}, hitting the first player it meets. */
    static void bolt(Rival b, int pal, Vec3 at, Vec3 dir, double speed, int steps, double w, float dmg, Consumer<Player> onHit) {
        if (steps <= 0 || !b.isAlive() || b.yielded) return;
        ServerLevel sl = (ServerLevel) b.level();
        AstaFx.later(1, () -> {
            Vec3 next = at.add(dir.scale(speed));
            Vec3 wall = clip(sl, b, at, next);
            BossFx.orb(sl, pal, at, dir.scale(speed), 0.2f, 2);
            for (Player p : along(b, at, next, w)) { hit(b, p, dmg); if (onHit != null) onHit.accept(p); return; }
            if (wall.distanceToSqr(next) > 1e-4) return;
            bolt(b, pal, next, dir, speed, steps - 1, w, dmg, onHit);
        });
    }

    /** Steps the rival to {@code to} in a flash of its colour. */
    static void blink(Rival b, Vec3 to) {
        ServerLevel sl = (ServerLevel) b.level();
        Vec3 from = b.position();
        Vec3 g = ground(sl, to.add(0, 1.5, 0));
        if (flatDist(g, b.arena) > RING - 1) g = b.arena.add(g.subtract(b.arena).multiply(1, 0, 1).normalize().scale(RING - 2));
        g = ground(sl, new Vec3(g.x, from.y + 1.5, g.z));
        BossFx.ring(sl, PAL[b.rivalId()], from.add(0, 0.1, 0), 0.2f, 1.2f, 0.06f, 6);
        b.teleportTo(g.x, g.y, g.z);
        BossFx.ring(sl, PAL[b.rivalId()], g.add(0, 0.1, 0), 1.2f, 0.2f, 0.06f, 6);
    }

    static List<Rival> inArena(ServerLevel sl, Vec3 c) {
        return sl.getEntitiesOfClass(Rival.class, new AABB(c, c).inflate(RING + 8), Entity::isAlive);
    }

    // ================================================================== /rival (testing and admin)
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("rival").requires(s -> s.hasPermission(2))
                .then(Commands.literal("call").then(Commands.argument("rival", IntegerArgumentType.integer(0, COUNT - 1))
                        .executes(c -> { call(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "rival")); return 1; })
                        .then(Commands.argument("rank", IntegerArgumentType.integer(0, 3)).executes(c -> {
                            call(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "rival"), IntegerArgumentType.getInteger(c, "rank"));
                            return 1;
                        }))))
                .then(Commands.literal("reset").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    p.getPersistentData().remove(KEY); p.getPersistentData().remove(NEXT);
                    sync(p);
                    return 1;
                }))
                .then(Commands.literal("soon").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    p.getPersistentData().putLong(NEXT, p.serverLevel().getGameTime());
                    return 1;
                })));
    }
}
