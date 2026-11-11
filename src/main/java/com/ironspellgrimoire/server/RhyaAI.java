package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.entity.Rhya;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Rhya's fight - Copy Magic. He needs to touch a grimoire to copy it, and he can only use one attribute at a time.
 * <ul>
 *   <li>Phase 1: he dashes in to touch your grimoire, then fights you with its magic (its colours, bolts, slashes,
 *       rain, walls). Every so often he touches another one - with more than one of you, he swaps between your books.</li>
 *   <li>Phase 2: "I've touched a lot of grimoires" - he cycles through four captains' magic he copied long ago, each
 *       with its own signature: Dark (a black crescent all round him), Slash (long cuts across the arena), Silver (a
 *       rain of spikes), Thorns (vines that root you).</li>
 *   <li>Phase 3: he copies anti-magic itself. A black sword whose hits silence your spells for a few seconds, and every
 *       spell that hits him is copied straight back at you. Near the end, Total Copy: every magic he has copied in
 *       this fight, one after another.</li>
 * </ul>
 * Every move is shown before it lands. Nothing breaks blocks.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class RhyaAI {
    private RhyaAI() {}

    public static final int P_IDLE = 0, P_CAST = 1, P_DASH = 2, P_SLASH = 3, P_RAISE = 4, P_SPIN = 5, P_ROAR = 6, P_LAZY = 7;
    static final int WARN = 18, SIL = Fx.SILVER, ANTI = 0;
    /** The captains' magic he carries: Dark, Slash, Silver, Thorns. */
    static final int[] STYLES = {Fx.YAMI, Fx.RIPPER, 11, 24};
    static final String[] STYLE_NAMES = {"Dark Magic", "Slash Magic", "Silver Magic", "Thorn Magic"};
    private static final UUID ROOT = UUID.fromString("41a70001-0000-4000-8000-0000000000b1");
    private static final Map<UUID, Long> SILENCED = new HashMap<>();

    public static final class State {
        public Vec3 home;
        UUID target;
        int busy, farTicks, closeTicks, style;
        boolean introduced, totalDone;
        long lastSeen, recopyAt, styleAt, counterAt;
        final List<Integer> copied = new ArrayList<>();
    }

    // ================================================================== the tick
    public static void tick(Rhya b) {
        ServerLevel sl = (ServerLevel) b.level();
        State s = b.ai;
        long now = sl.getGameTime();
        if (s.home == null) s.home = b.position();
        if (b.transforming() > 0) {
            b.setTransforming(b.transforming() - 1);
            b.getNavigation().stop();
            b.setDeltaMovement(0, Math.min(0, b.getDeltaMovement().y), 0);
            if (b.transforming() == 0) b.setPose(P_IDLE);
            return;
        }
        Player t = target(b);
        if (t == null) {
            b.getNavigation().stop();
            if (now - s.lastSeen > 600 && b.getHealth() < b.getMaxHealth()) b.heal(0.5f);
            return;
        }
        s.lastSeen = now;
        if (!s.introduced) {
            s.introduced = true;
            say(b, "Ugh, more work... Fine. Let me see that grimoire of yours - I'll be borrowing it.");
            sound(sl, b.position(), SoundEvents.ILLUSIONER_AMBIENT, 1.4f, 0.8f);
        }
        if (b.phase() == 2 && now >= s.styleAt) nextStyle(b, sl);
        if (b.phase() == 3 && !s.totalDone && b.getHealth() < b.getMaxHealth() * 0.12f && s.busy <= 0) { s.busy = totalCopy(b, sl, t) + 20; return; }
        b.getLookControl().setLookAt(t, 30f, 30f);
        double dist = b.distanceTo(t);
        if (dist > 12) s.farTicks++; else s.farTicks = Math.max(0, s.farTicks - 2);
        if (dist < 3) s.closeTicks++; else s.closeTicks = Math.max(0, s.closeTicks - 2);
        if (s.busy > 0) { s.busy--; return; }
        double want = b.phase() == 3 ? 2.5 : 7;
        if (dist > want + 2) b.getNavigation().moveTo(t, 1.0);
        else if (dist < want - 2.5 && b.phase() < 3) {
            Vec3 away = flat(b.position().subtract(t.position()));
            if (away.lengthSqr() > 1e-4) b.getNavigation().moveTo(b.getX() + away.normalize().x * 3, b.getY(), b.getZ() + away.normalize().z * 3, 1.0);
        } else b.getNavigation().stop();
        choose(b, sl, t, dist);
    }

    private static Player target(Rhya b) {
        State s = b.ai;
        ServerLevel sl = (ServerLevel) b.level();
        if (s.target != null) {
            Player p = sl.getPlayerByUUID(s.target);
            if (valid(b, p)) return p;
            s.target = null;
        }
        Player best = null;
        double bd = 40 * 40;
        for (Player p : sl.players()) {
            if (!valid(b, p)) continue;
            double d = p.distanceToSqr(b);
            if (d < bd) { bd = d; best = p; }
        }
        if (best != null) s.target = best.getUUID();
        return best;
    }

    private static boolean valid(Rhya b, Player p) {
        return p != null && p.isAlive() && !p.isCreative() && !p.isSpectator() && p.level() == b.level() && p.distanceToSqr(b) < 48 * 48;
    }

    /** His copies are a little weaker against the magic they were copied from. */
    public static float damageTaken(Rhya b, DamageSource src) {
        if (b.copy() >= 0 && src.getEntity() instanceof Player p) {
            GrimoireMastery.Book bk = GrimoireMastery.bookOf(Utils.getPlayerSpellbookStack(p));
            if (bk != null && bk.palette() == b.copy()) return 0.85f;
        }
        return 1f;
    }

    public static void onHurt(Rhya b, DamageSource src, float amount) {
        State s = b.ai;
        ServerLevel sl = (ServerLevel) b.level();
        if (src.getEntity() instanceof Player p && valid(b, p)) {
            s.target = p.getUUID();
            // Copy Counter (phase 3): a spell that hits him is copied straight back
            if (b.phase() == 3 && src instanceof SpellDamageSource && sl.getGameTime() >= s.counterAt) {
                s.counterAt = sl.getGameTime() + 50;
                GrimoireMastery.Book bk = GrimoireMastery.bookOf(Utils.getPlayerSpellbookStack(p));
                int pal = bk != null ? bk.palette() : SIL;
                say(b, "Copied.");
                Vec3 from = b.position().add(0, 1.5, 0);
                Vec3 d = mid(p).subtract(from);
                if (d.lengthSqr() > 1e-4) bolt(b, sl, from, d.normalize(), pal, 1.4, 22, 7f, new boolean[1]);
                FxSend.ring(sl, pal, from, 0.3f, 1.8f, 0.08f, 8);
                sound(sl, from, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.2f, 1.4f);
            }
        }
    }

    // ================================================================== Copy Magic: touching a grimoire
    /** What he would copy from this player: their book (or, with none, any grimoire). */
    private static ItemStack bookFrom(Player p) {
        ItemStack s = Utils.getPlayerSpellbookStack(p);
        if (s != null && !s.isEmpty() && GrimoireMastery.bookOf(s) != null) return s;
        List<GrimoireMastery.Book> all = GrimoireMastery.BOOKS;
        return new ItemStack(all.get(ThreadLocalRandom.current().nextInt(all.size())).item().get());
    }

    private static void copyFrom(Rhya b, ServerLevel sl, Player p) {
        ItemStack book = bookFrom(p);
        GrimoireMastery.Book bk = GrimoireMastery.bookOf(book);
        int pal = bk != null ? bk.palette() : SIL;
        b.setBook(book);
        b.setCopy(pal);
        if (!b.ai.copied.contains(pal)) b.ai.copied.add(pal);
        b.ai.recopyAt = sl.getGameTime() + 400;
        FxSend.trail(sl, pal, mid(p), b.position().add(0, 1.4, 0), 0.25f, 12);
        FxSend.ring(sl, pal, p.position().add(0, 0.1, 0), 0.3f, 2.4f, 0.08f, 12);
        FxSend.ring(sl, pal, b.position().add(0, 1.2, 0), 1.8f, 0.3f, 0.08f, 12);
        sound(sl, b.position(), SoundEvents.BOOK_PAGE_TURN, 1.4f, 0.7f);
        sound(sl, b.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.2f, 1.2f);
        for (Player q : near(b, b.position(), 40))
            q.displayClientMessage(Component.literal("Rhya copied ").withStyle(ChatFormatting.GRAY)
                    .append(book.getHoverName().copy().withStyle(ChatFormatting.YELLOW)).append(Component.literal("!").withStyle(ChatFormatting.GRAY)), true);
    }

    /** He dashes in to touch your grimoire; if he reaches you, he copies it. */
    static int touch(Rhya b, ServerLevel sl, Player t) {
        int T = tel(b, 10);
        b.setPose(P_DASH);
        b.getNavigation().stop();
        Vec3 a = b.position();
        Vec3 end = t.position();
        warnLine(sl, a.add(0, 0.1, 0), end.add(0, 0.1, 0), 1.2f, T);
        sound(sl, a, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 1.0f, 1.3f);
        final int STEPS = 6;
        for (int k = 1; k <= STEPS; k++) {
            final double u = k / (double) STEPS, u0 = (k - 1) / (double) STEPS;
            AstaFx.later(T + k, () -> {
                if (!ok(b)) return;
                Vec3 to = t.isAlive() ? t.position() : end;
                Vec3 p0 = a.add(to.subtract(a).scale(u0)), p1 = a.add(to.subtract(a).scale(u * 0.92));
                b.moveTo(p1.x, p1.y, p1.z, b.getYRot(), b.getXRot());
                b.setDeltaMovement(Vec3.ZERO);
                FxSend.trail(sl, b.copy() >= 0 ? b.copy() : SIL, p0.add(0, 1.1, 0), p1.add(0, 1.1, 0), 0.4f, 6);
            });
        }
        AstaFx.later(T + STEPS + 1, () -> {
            if (!ok(b) || !t.isAlive()) return;
            if (t.distanceTo(b) < 3.0) {
                hurt(b, t, 6f);
                copyFrom(b, sl, t);
            } else sound(sl, b.position(), SoundEvents.ILLUSIONER_AMBIENT, 0.8f, 1.5f);
        });
        return T + STEPS + 6;
    }

    // ================================================================== choosing
    private interface Move { int run(Rhya b, ServerLevel sl, Player t); }

    private static void choose(Rhya b, ServerLevel sl, Player t, double dist) {
        State s = b.ai;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int ph = b.phase();
        long now = sl.getGameTime();
        int took;
        if (ph == 1 && (b.copy() < 0 || now >= s.recopyAt)) {   // nothing copied yet, or time to touch another
            Player who = t;
            List<Player> all = near(b, b.position(), 30);
            if (all.size() > 1) who = all.get(r.nextInt(all.size()));
            took = touch(b, sl, who);
        } else if (s.farTicks > 100) {
            s.farTicks = 0;
            took = blink(b, sl, t);
        } else if (s.closeTicks > 60 && ph < 3) {
            s.closeTicks = 0;
            took = r.nextBoolean() ? copyWall(b, sl, t) : blink(b, sl, t);
        } else {
            List<Move> pool = new ArrayList<>();
            pool.add(RhyaAI::copyBolts);
            pool.add(RhyaAI::copySlash);
            pool.add(RhyaAI::copyRain);
            if (dist < 6) pool.add(RhyaAI::copyWall);
            if (ph == 2) { pool.add(RhyaAI::signature); pool.add(RhyaAI::signature); }
            if (ph == 3) {
                pool.clear();
                pool.add(RhyaAI::antiSlash); pool.add(RhyaAI::antiSlash);
                if (dist < 5) pool.add(RhyaAI::antiSpin);
                pool.add(RhyaAI::signature);
                pool.add(RhyaAI::copyRain);
                if (dist > 8) pool.add(RhyaAI::blink);
            }
            took = pool.get(r.nextInt(pool.size())).run(b, sl, t);
        }
        int rest = ph == 1 ? 32 : ph == 2 ? 26 : 18;
        s.busy = took + rest;
    }

    // ================================================================== shared pieces
    private static int tel(Rhya b, int ticks) { return b.phase() == 3 ? Math.max(6, (int) (ticks * 0.8)) : ticks; }
    private static int pal(Rhya b) { return b.phase() == 3 && b.copy() == ANTI ? ANTI : b.copy() >= 0 ? b.copy() : SIL; }

    static void warnCircle(ServerLevel sl, Vec3 c, float r, int ticks) {
        Vec3 g = ZenonAI.ground(sl, c).add(0, 0.06, 0);
        FxSend.ring(sl, WARN, g, r - 0.1f, r, 0.04f, ticks);
        FxSend.ring(sl, WARN, g, 0.05f, r, 0.025f, ticks);
    }

    static void warnLine(ServerLevel sl, Vec3 a, Vec3 c, float w, int ticks) { ZenonAI.warnLine(sl, a, c, w, ticks); }

    static float scale(Rhya b) {
        Difficulty d = b.level().getDifficulty();
        return d == Difficulty.EASY ? 0.6f : d == Difficulty.HARD ? 1.25f : d == Difficulty.PEACEFUL ? 0.4f : 1f;
    }

    static void hurt(Rhya b, Player p, float dmg) {
        if (!b.isAlive() || !p.isAlive()) return;
        p.hurt(b.damageSources().mobAttack(b), dmg * scale(b));
    }

    static List<Player> near(Rhya b, Vec3 c, double r) {
        List<Player> out = new ArrayList<>();
        for (Player p : b.level().getEntitiesOfClass(Player.class, new AABB(c, c).inflate(r))) {
            if (p.isAlive() && !p.isCreative() && !p.isSpectator() && p.position().add(0, 0.9, 0).distanceTo(c) <= r + 0.6) out.add(p);
        }
        return out;
    }

    static List<Player> along(Rhya b, Vec3 a, Vec3 c, double w) {
        List<Player> out = new ArrayList<>();
        for (Player p : b.level().getEntitiesOfClass(Player.class, new AABB(a, c).inflate(w + 1))) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;
            if (closestOnSegment(a, c, mid(p)).distanceTo(mid(p)) <= w) out.add(p);
        }
        return out;
    }

    static void say(Rhya b, String line) {
        for (Player p : b.level().getEntitiesOfClass(Player.class, b.getBoundingBox().inflate(48))) {
            p.sendSystemMessage(Component.literal("Rhya: ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal(line).withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC)));
        }
    }

    private static boolean ok(Rhya b) { return b.isAlive() && b.transforming() == 0; }

    private static Vec3 facing(Rhya b, Player t) {
        Vec3 d = flat(t.position().subtract(b.position()));
        return d.lengthSqr() < 1e-4 ? flat(b.getLookAngle()).normalize() : d.normalize();
    }

    private static void face(Rhya b, Vec3 dir) {
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        b.setYRot(yaw); b.setYHeadRot(yaw); b.yBodyRot = yaw;
    }

    /** One copied bolt: a sphere of the copied magic flying straight, striking the first player it meets. */
    private static void bolt(Rhya b, ServerLevel sl, Vec3 at, Vec3 d, int pal, double speed, int left, float dmg, boolean[] done) {
        if (left <= 0 || done[0] || !b.isAlive()) return;
        AstaFx.later(1, () -> {
            Vec3 next = at.add(d.scale(speed));
            Vec3 wall = clip(sl, b, at, next);
            Kit.orb(sl, pal, at, d.scale(speed), 0.32f, 2);
            for (Player p : along(b, at, next, 1.1)) { hurt(b, p, dmg); done[0] = true; }
            if (done[0] || wall.distanceToSqr(next) > 1e-4) { FxSend.ring(sl, pal, wall, 0.2f, 1.4f, 0.06f, 8); return; }
            bolt(b, sl, next, d, pal, speed, left - 1, dmg, done);
        });
    }

    // ================================================================== copied magic (any phase)
    static int copyBolts(Rhya b, ServerLevel sl, Player t) {
        int T = tel(b, 12);
        b.setPose(P_CAST);
        b.getNavigation().stop();
        Vec3 fwd = facing(b, t);
        face(b, fwd);
        Vec3 from = b.position().add(0, 1.5, 0);
        int pal = pal(b);
        List<Vec3> dirs = List.of(rotY(fwd, -0.22), fwd, rotY(fwd, 0.22));
        for (Vec3 d : dirs) warnLine(sl, from.add(0, -1.4, 0), from.add(d.scale(22)).add(0, -1.4, 0), 0.6f, T);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            Vec3 aim = mid(t).subtract(from);
            double up = aim.lengthSqr() > 1e-4 ? aim.normalize().y : 0;
            for (Vec3 d : dirs) bolt(b, sl, from, d.add(0, up, 0).normalize(), pal, 1.3, 18, 8f, new boolean[1]);
            sound(sl, from, SoundEvents.ILLUSIONER_CAST_SPELL, 1.2f, 1.2f);
        });
        return T + 8;
    }

    static int copySlash(Rhya b, ServerLevel sl, Player t) {
        int T = tel(b, 12);
        b.setPose(P_SLASH);
        b.getNavigation().stop();
        Vec3 fwd = facing(b, t);
        face(b, fwd);
        Vec3 from = b.position().add(0, 1.0, 0);
        int pal = pal(b);
        warnLine(sl, from.add(0, -0.9, 0), from.add(fwd.scale(20)).add(0, -0.9, 0), 2.2f, T);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            sound(sl, from, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.7f);
            wave(b, sl, from, fwd, pal, 1.2, 17, new HashSet<>());
        });
        return T + 8;
    }

    private static void wave(Rhya b, ServerLevel sl, Vec3 at, Vec3 d, int pal, double speed, int left, Set<UUID> hit) {
        if (left <= 0 || !b.isAlive()) return;
        AstaFx.later(1, () -> {
            Vec3 next = at.add(d.scale(speed));
            FxSend.slash(sl, pal, at, d, new Vec3(0, 1, 0), 2.2f, 0.4f, 3, 1.0f);
            for (Player p : along(b, at, next, 2.0)) if (hit.add(p.getUUID())) hurt(b, p, 9f);
            if (clip(sl, b, at, next).distanceToSqr(next) > 1e-4) return;
            wave(b, sl, next, d, pal, speed, left - 1, hit);
        });
    }

    static int copyRain(Rhya b, ServerLevel sl, Player t) { return rain(b, sl, t, pal(b), tel(b, 18)); }

    private static int rain(Rhya b, ServerLevel sl, Player t, int pal, int T) {
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Vec3> spots = new ArrayList<>();
        spots.add(ZenonAI.ground(sl, t.position()));
        for (int i = 0; i < 4; i++) spots.add(ZenonAI.ground(sl, t.position().add(r.nextDouble(-4, 4), 0, r.nextDouble(-4, 4))));
        for (Vec3 c : spots) warnCircle(sl, c, 1.8f, T);
        sound(sl, b.position(), SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 1.0f, 0.9f);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            for (Vec3 c : spots) {
                FxSend.beam(sl, pal, c.add(0, 12, 0), c, 0.45f, 8);
                FxSend.ring(sl, pal, c.add(0, 0.1, 0), 0.2f, 2.0f, 0.08f, 10);
                for (Player p : near(b, c.add(0, 1, 0), 1.8)) hurt(b, p, 8f);
            }
            sound(sl, spots.get(0), SoundEvents.GENERIC_EXPLODE, 0.8f, 1.4f);
        });
        return T + 8;
    }

    static int copyWall(Rhya b, ServerLevel sl, Player t) {
        int T = tel(b, 10);
        b.setPose(P_CAST);
        b.getNavigation().stop();
        Vec3 fwd = facing(b, t);
        face(b, fwd);
        Vec3 c = b.position();
        int pal = pal(b);
        warnCircle(sl, c.add(fwd.scale(2.5)), 3.2f, T);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            FxSend.wall(sl, pal, c.add(0, 0.1, 0), fwd, new Vec3(0, 1, 0), 1.5f, 5.5f, -60, 60, 2.5f, 12, 0, 0.6f);
            for (Player p : near(b, c.add(fwd.scale(2.5)).add(0, 1, 0), 3.5)) {
                hurt(b, p, 7f);
                p.setDeltaMovement(fwd.scale(1.4).add(0, 0.4, 0));
                p.hurtMarked = true;
            }
            sound(sl, c, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.2f, 0.8f);
        });
        return T + 8;
    }

    /** A blink: gone in a flash of copied magic, out again beside you. */
    static int blink(Rhya b, ServerLevel sl, Player t) {
        Vec3 side = rightOf(facing(b, t)).scale(ThreadLocalRandom.current().nextBoolean() ? 3.5 : -3.5);
        Vec3 dest = SpaceFx.safe(sl, t.position().add(side), b);
        if (dest == null) return 4;
        int pal = pal(b);
        FxSend.ring(sl, pal, b.position().add(0, 1, 0), 1.2f, 0.2f, 0.08f, 8);
        SpaceFx.move(b, dest, b.getYRot());
        FxSend.ring(sl, pal, dest.add(0, 1, 0), 0.2f, 1.6f, 0.08f, 8);
        sound(sl, dest, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.2f, 1.3f);
        return 6;
    }

    // ================================================================== phase 2: the captains' magic
    private static void nextStyle(Rhya b, ServerLevel sl) {
        State s = b.ai;
        s.style = (s.style + 1) % STYLES.length;
        s.styleAt = sl.getGameTime() + 240;
        int pal = STYLES[s.style];
        b.setCopy(pal);
        if (!s.copied.contains(pal)) s.copied.add(pal);
        FxSend.ring(sl, pal, b.position().add(0, 0.1, 0), 0.3f, 3.5f, 0.1f, 12);
        FxSend.ring(sl, pal, b.position().add(0, 1.2, 0), 2.0f, 0.3f, 0.08f, 12);
        sound(sl, b.position(), SoundEvents.BOOK_PAGE_TURN, 1.4f, 0.6f);
        for (Player q : near(b, b.position(), 40))
            q.displayClientMessage(Component.literal("Copy: " + STYLE_NAMES[s.style]).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
    }

    /** The signature move of the magic he is using right now. */
    static int signature(Rhya b, ServerLevel sl, Player t) {
        int st = b.ai.style;
        return switch (st) {
            case 0 -> blackCrescent(b, sl, t);
            case 1 -> longCuts(b, sl, t);
            case 2 -> silverRain(b, sl, t);
            default -> thornSnare(b, sl, t);
        };
    }

    /** Dark: a black crescent swept all the way round him. Get out of the circle. */
    static int blackCrescent(Rhya b, ServerLevel sl, Player t) {
        int T = tel(b, 18);
        b.setPose(P_SPIN);
        b.getNavigation().stop();
        Vec3 c = b.position();
        warnCircle(sl, c, 6.5f, T);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2;
                FxSend.slash(sl, Fx.YAMI, c.add(0, 1.0, 0), new Vec3(Math.cos(a), 0, Math.sin(a)), new Vec3(0, 1, 0), 6.0f, 1.2f, 10, 1.4f, 0.8f);
            }
            FxSend.ring(sl, Fx.YAMI, c.add(0, 0.2, 0), 0.5f, 6.5f, 0.3f, 12);
            for (Player p : near(b, c.add(0, 1, 0), 6.5)) hurt(b, p, 12f);
            FxSend.shake(sl, c, 0.5f, 8, 16);
            sound(sl, c, SoundEvents.WITHER_SHOOT, 1.2f, 0.6f);
        });
        return T + 10;
    }

    /** Slash: three long cuts across the arena, crossing where you stand. */
    static int longCuts(Rhya b, ServerLevel sl, Player t) {
        int T = tel(b, 20);
        b.setPose(P_SLASH);
        b.getNavigation().stop();
        Vec3 c = ZenonAI.ground(sl, t.position());
        double a0 = ThreadLocalRandom.current().nextDouble(Math.PI);
        List<Vec3[]> lines = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double a = a0 + i * Math.PI / 3;
            Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a)).scale(12);
            lines.add(new Vec3[]{c.subtract(d), c.add(d)});
        }
        for (int i = 0; i < 3; i++) warnLine(sl, lines.get(i)[0].add(0, 0.1, 0), lines.get(i)[1].add(0, 0.1, 0), 0.9f, T + i * 4);
        for (int i = 0; i < 3; i++) {
            final Vec3[] l = lines.get(i);
            AstaFx.later(T + i * 4, () -> {
                if (!ok(b)) return;
                FxSend.trail(sl, Fx.RIPPER, l[0].add(0, 1.0, 0), l[1].add(0, 1.0, 0), 0.5f, 10);
                FxSend.trail(sl, Fx.RIPPER, l[0].add(0, 0.2, 0), l[1].add(0, 0.2, 0), 0.25f, 8);
                for (Player p : along(b, l[0].add(0, 1, 0), l[1].add(0, 1, 0), 1.0)) hurt(b, p, 10f);
                sound(sl, centre(l), SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 1.3f);
            });
        }
        return T + 12;
    }

    private static Vec3 centre(Vec3[] l) { return l[0].add(l[1]).scale(0.5); }

    /** Silver: a rain of silver spikes over where you stand and around it. */
    static int silverRain(Rhya b, ServerLevel sl, Player t) {
        int T = tel(b, 18);
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Vec3> spots = new ArrayList<>();
        spots.add(ZenonAI.ground(sl, t.position()));
        for (int i = 0; i < 7; i++) spots.add(ZenonAI.ground(sl, t.position().add(r.nextDouble(-5, 5), 0, r.nextDouble(-5, 5))));
        for (Vec3 c : spots) warnCircle(sl, c, 1.4f, T);
        for (int i = 0; i < spots.size(); i++) {
            final Vec3 c = spots.get(i);
            AstaFx.later(T + i, () -> {
                if (!ok(b)) return;
                FxSend.spike(sl, 11, c.add(0, 2.2, 0), new Vec3(0, -1, 0), 2.4f, 0.22f, 14, 2, FxPacket.EXTEND);
                for (Player p : near(b, c.add(0, 1, 0), 1.4)) hurt(b, p, 9f);
                sound(sl, c, SoundEvents.TRIDENT_HIT, 1.0f, 1.2f);
            });
        }
        return T + 12;
    }

    /** Thorns: vines burst up round where you stand and hold you fast. */
    static int thornSnare(Rhya b, ServerLevel sl, Player t) {
        int T = tel(b, 16);
        b.setPose(P_CAST);
        b.getNavigation().stop();
        Vec3 c = ZenonAI.ground(sl, t.position());
        warnCircle(sl, c, 2.8f, T);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI / 5;
                Vec3 base = c.add(Math.cos(a) * 2.2, 0, Math.sin(a) * 2.2);
                FxSend.spike(sl, 24, base, new Vec3(-Math.cos(a) * 0.6, 1, -Math.sin(a) * 0.6).normalize(), 2.4f, 0.14f, 40, 4, FxPacket.GROUND);
            }
            for (Player p : near(b, c.add(0, 1, 0), 2.8)) {
                hurt(b, p, 6f);
                TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, ROOT, "Thorn snare", -0.85, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
            }
            sound(sl, c, SoundEvents.SWEET_BERRY_BUSH_BREAK, 1.4f, 0.6f);
        });
        return T + 8;
    }

    // ================================================================== phase 3: copied anti-magic
    static int antiSlash(Rhya b, ServerLevel sl, Player t) {
        int T = tel(b, 12);
        b.setPose(P_SLASH);
        b.getNavigation().stop();
        Vec3 fwd = facing(b, t);
        face(b, fwd);
        Vec3 a = b.position();
        Vec3 end = clip(sl, b, a.add(0, 1, 0), a.add(0, 1, 0).add(fwd.scale(7))).subtract(0, 1, 0);
        warnLine(sl, a.add(0, 0.1, 0), end.add(0, 0.1, 0), 1.6f, T);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            b.moveTo(end.x, end.y, end.z, b.getYRot(), b.getXRot());
            FxSend.slash(sl, ANTI, a.add(end).scale(0.5).add(0, 1.2, 0), fwd, new Vec3(0, 1, 0).add(rightOf(fwd).scale(0.6)).normalize(), 3.5f, 0.6f, 8, 1.5f, 0.9f);
            for (Player p : along(b, a.add(0, 1, 0), end.add(0, 1, 0), 1.6)) { hurt(b, p, 10f); silence(p, 60); }
            sound(sl, end, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.5f);
            sound(sl, end, SoundEvents.WITHER_SHOOT, 0.6f, 1.6f);
        });
        return T + 8;
    }

    static int antiSpin(Rhya b, ServerLevel sl, Player t) {
        int T = tel(b, 12);
        b.setPose(P_SPIN);
        b.getNavigation().stop();
        Vec3 c = b.position();
        warnCircle(sl, c, 4f, T);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            for (int i = 0; i < 3; i++) {
                double a = i * Math.PI * 2 / 3;
                FxSend.slash(sl, ANTI, c.add(0, 1.0, 0), new Vec3(Math.cos(a), 0, Math.sin(a)), new Vec3(0, 1, 0), 4f, 0.8f, 8, 1.6f, 0.8f);
            }
            for (Player p : near(b, c.add(0, 1, 0), 4)) { hurt(b, p, 9f); silence(p, 60); }
            sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.6f);
        });
        return T + 8;
    }

    /** Total Copy: every magic he copied in this fight, one after another, on where you stand. */
    static int totalCopy(Rhya b, ServerLevel sl, Player t) {
        State s = b.ai;
        s.totalDone = true;
        say(b, "Total Copy. Everything I've touched today - all of it, at once.");
        FxSend.impactFrame(sl, b.position().add(0, 1.5, 0), 3, FxSend.FRAME_ASTA, 26);
        sound(sl, b.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 2.0f, 0.6f);
        List<Integer> pals = new ArrayList<>(s.copied);
        if (pals.isEmpty()) pals.add(SIL);
        int n = Math.min(6, pals.size());
        for (int i = 0; i < n; i++) {
            final int pal = pals.get(i);
            AstaFx.later(10 + i * 14, () -> {
                if (!ok(b)) return;
                Player tt = target(b);
                if (tt != null) rain(b, sl, tt, pal, 14);
            });
        }
        return 10 + n * 14 + 14;
    }

    /** Copied anti-magic: your spells won't cast for a moment. */
    private static void silence(Player p, int ticks) {
        SILENCED.put(p.getUUID(), p.level().getGameTime() + ticks);
        p.displayClientMessage(Component.literal("Silenced by anti-magic!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
    }

    @SubscribeEvent
    public static void onPreCast(SpellPreCastEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Long until = SILENCED.get(p.getUUID());
        if (until == null) return;
        if (p.level().getGameTime() >= until) { SILENCED.remove(p.getUUID()); return; }
        e.setCanceled(true);
        p.displayClientMessage(Component.literal("Silenced by anti-magic!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
    }

    // ================================================================== transformations and death
    public static void transform(Rhya b, int phase) {
        if (b.transforming() > 0 || b.phase() >= phase) return;
        ServerLevel sl = (ServerLevel) b.level();
        b.setPhase(phase);
        b.setTransforming(50);
        b.setPose(P_ROAR);
        b.ai.busy = 0;
        b.getNavigation().stop();
        Vec3 c = b.position();
        for (Player p : near(b, c.add(0, 1, 0), 5)) {
            Vec3 away = flat(p.position().subtract(c));
            if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
            p.setDeltaMovement(away.normalize().scale(1.1).add(0, 0.4, 0));
            p.hurtMarked = true;
        }
        if (phase == 2) {
            say(b, "Alright, alright... you know I've touched a lot of grimoires, right? Captains' ones, even.");
            b.ai.style = -1;
            b.ai.styleAt = 0;
            for (int i = 0; i < STYLES.length; i++) {
                final int pal = STYLES[i], k = i;
                AstaFx.later(i * 8, () -> {
                    FxSend.ring(sl, pal, c.add(0, 0.1 + k * 0.3, 0), 0.4f, 5f + k, 0.12f, 14);
                    sound(sl, c, SoundEvents.BOOK_PAGE_TURN, 1.2f, 0.6f + k * 0.15f);
                });
            }
            AstaFx.later(40, () -> FxSend.impactFrame(sl, c.add(0, 1.6, 0), 3, FxSend.FRAME_YAMI, 24));
        } else {
            say(b, "Fine. Then I'll copy the one magic that isn't supposed to be copied.");
            b.setCopy(ANTI);
            b.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AddonItemRegistry.DEMON_SLAYER_SWORD.get()));
            b.setDropChance(EquipmentSlot.MAINHAND, 0f);
            AstaFx.later(30, () -> {
                FxSend.impactFrame(sl, c.add(0, 1.6, 0), 4, FxSend.FRAME_ASTA, 28);
                FxSend.ring(sl, ANTI, c.add(0, 0.1, 0), 0.5f, 9f, 0.3f, 16);
                FxSend.shake(sl, c, 0.8f, 12, 30);
                sound(sl, c, SoundEvents.WITHER_SPAWN, 1.0f, 1.2f);
            });
        }
    }

    public static void dying(Rhya b, int t) {
        ServerLevel sl = (ServerLevel) b.level();
        Vec3 c = b.position();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (t == 1) {
            b.setPose(P_LAZY);
            say(b, "Heh... I'm so tired... Wake me when it's over...");
            sound(sl, c, SoundEvents.ILLUSIONER_DEATH, 1.4f, 0.8f);
            FxSend.impactFrame(sl, c.add(0, 1.5, 0), 3, FxSend.FRAME_ASTA, 22);
        }
        if (t < 60 && t % 4 == 0) {   // the copied magic leaves him, page by page
            List<Integer> pals = b.ai.copied;
            int pal = pals.isEmpty() ? SIL : pals.get(r.nextInt(pals.size()));
            Kit.orb(sl, pal, c.add(r.nextDouble(-0.6, 0.6), 0.6 + r.nextDouble(1.4), r.nextDouble(-0.6, 0.6)), new Vec3(0, 0.06, 0), 0.22f, 16);
        }
    }

    public static void clear() { SILENCED.clear(); }
}
