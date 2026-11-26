package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.item.BoundGrimoireItem;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.ISpellContainerMutable;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Last Seraph's own moves (the Grimoire of the Bound Demon, awakened). Like Devil Union, the form swaps the pages in
 * the book while it lasts (SeraphFx.apply), so the wheel shows the seraph's moves; when it ends, the demon's come back.
 * <ul>
 *   <li><b>Judgment Feathers</b> (for Demon Draw) - the wing sheds a storm of salt-white feathers at what you look at.</li>
 *   <li><b>Wing Cleave</b> (for Cursed Edge) - the great wing sweeps everything in front of you aside.</li>
 *   <li><b>Salt Lance</b> (for Demon's Reach) - five lances of salt, thirty blocks long, through everything.</li>
 *   <li><b>Verdict</b> (for Demon Flurry) - circles under every foe ahead; then pillars of white light fall on them.</li>
 *   <li><b>Fall of Heaven</b> (for Sinner's Mark) - up on the wing, then down into the ground where you look: a crater
 *       of light and salt.</li>
 *   <li><b>Fold the Wing</b> (for the Last Seraph) - end the form, in a burst of feathers.</li>
 * </ul>
 * Effects and hitboxes only - no block is touched.
 */
public final class SeraphFx {
    private SeraphFx() {}

    static final int SALT = BoundFx.SALT, GOLD = Fx.GOLD;

    /** Only while the seraph is awake (the client can't tell - the server decides). */
    public static boolean ready(Player p) { return !(p instanceof ServerPlayer sp) || BoundFx.awake(sp); }

    static boolean go(ServerPlayer p) {
        if (!BoundFx.awake(p)) { p.displayClientMessage(Component.literal("The seraph is asleep.").withStyle(ChatFormatting.GRAY), true); return false; }
        return BoundFx.free(p);
    }

    static boolean foe(ServerPlayer p, LivingEntity t) { return !BoundFx.ally(p, t); }

    /** Where a move lands: the creature you look at (within range), else the first block down your sight. */
    static Vec3 mark(ServerPlayer p, double range) {
        LivingEntity t = aimed(p, range, 12);
        return t != null && foe(p, t) ? t.position().add(0, t.getBbHeight() * 0.5, 0) : Kit.aim(p, range);
    }

    // ------------------------------------------------------------------ the pages
    /** Judgment Feathers: sixteen feathers out of the wing, thrown at what you look at. */
    public static void feathers(ServerPlayer p, float power) {
        if (!go(p)) return;
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition(), target = mark(p, 24);
        Vec3 left = rotY(p.getLookAngle(), Math.PI / 2).multiply(1, 0, 1).normalize();
        play(sl, p.position(), "bd_seraph", 1.2f, 1.5f);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 16; i++) {
            final int k = i;
            AstaFx.later(i / 2, () -> {
                // each feather leaves the wing (up and to your left), then flies at the mark
                Vec3 from = eye.add(left.scale(1.2 + r.nextDouble(2.2))).add(0, 0.6 + r.nextDouble(2.0), 0);
                Vec3 to = target.add(r.nextDouble(-1.2, 1.2), r.nextDouble(-0.6, 1.0), r.nextDouble(-1.2, 1.2));
                Vec3 d = to.subtract(from).normalize();
                double len = Math.max(1, to.distanceTo(from));
                int ticks = (int) Math.max(3, Math.min(10, len / 3));
                { var fe = BoundFx.bfx(10, from, ticks + 24); fe.u = to; fe.r2 = ticks; fe.a0 = k % 4 == 0 ? 1 : 0; BoundFx.bsend(sl, fe); }   // a feather, flying, then stuck where it lands
                AstaFx.later(ticks, () -> {
                    for (LivingEntity t : Kit.near(sl, p, to, 1.4)) if (foe(p, t)) { AstaFx.damage(p, t, 2.5f, "seraph_feathers"); BoundFx.salt(p, t, 1); }
                });
            });
        }
    }

    /** Wing Cleave: one sweep of the great wing - everything in front of you thrown aside. */
    public static void wingCleave(ServerPlayer p, float power) {
        if (!go(p)) return;
        ServerLevel sl = p.serverLevel();
        Vec3 look = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).normalize();
        Vec3 c = p.position().add(0, 1.1, 0);
        play(sl, p.position(), "bd_seraph", 1.6f, 0.7f);
        // the sweep: three rising walls of light from your left round to your right, the last widest
        { var wg = BoundFx.bfx(11, c, 18); wg.d = look; wg.r = 7.5f; BoundFx.bsend(sl, wg); }   // the great wing sweeping round, its feathers blades
        FxSend.impactFrame(sl, c, 4, true, 24);
        FxSend.shake(sl, c, 0.6f, 8, 24);
        for (LivingEntity t : enemies(sl, p, p.getBoundingBox().inflate(7.5, 3, 7.5))) {
            if (!foe(p, t)) continue;
            Vec3 to = t.position().subtract(p.position()).multiply(1, 0, 1);
            if (to.lengthSqr() > 56 || (to.lengthSqr() > 1 && to.normalize().dot(look) < -0.35)) continue;
            AstaFx.damage(p, t, 10f, "seraph_wing_cleave");
            BoundFx.salt(p, t, 1);
            if (!BoundFx.boss(t)) Kit.push(t, to.lengthSqr() < 1e-3 ? look : to.normalize(), 1.6, 0.5);
        }
    }

    /** The seraph's Salt Lance: five lances of salt, thirty blocks, through everything in their line. */
    public static void saltLance(ServerPlayer p, float power) {
        if (!go(p)) return;
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        play(sl, p.position(), "bd_salt", 1.4f, 0.8f);
        double[] fan = {-0.2, -0.1, 0, 0.1, 0.2};
        for (int i = 0; i < fan.length; i++) {
            final double ang = fan[i];
            AstaFx.later(i, () -> {
                Vec3 d = rotY(p.getLookAngle(), ang).normalize();
                Vec3 end = clip(sl, p, eye, eye.add(d.scale(30)));
                { var ln = BoundFx.bfx(12, eye.add(d).add(0, -0.2, 0), 16); ln.u = end; ln.r = 0.28f; BoundFx.bsend(sl, ln); }   // a lance of crystal, bursting where it ends
                for (LivingEntity t : enemies(sl, p, new AABB(eye, end).inflate(1.2)))
                    if (foe(p, t) && closestOnSegment(eye, end, mid(t)).distanceTo(mid(t)) < 1.2) { AstaFx.damage(p, t, 6f, "salt_lance"); BoundFx.salt(p, t, 1); }
            });
        }
        FxSend.shake(sl, eye, 0.35f, 6, 16);
    }

    /** Verdict: a circle under each foe ahead (up to eight); a breath later, pillars of white light fall on them. */
    public static void verdict(ServerPlayer p, float power) {
        if (!go(p)) return;
        ServerLevel sl = p.serverLevel();
        Vec3 c = mark(p, 20);
        List<Vec3> at = new ArrayList<>();
        for (LivingEntity t : Kit.near(sl, p, c, 12)) if (foe(p, t) && at.size() < 8) at.add(BossKit.ground(sl, t.position()));
        if (at.isEmpty()) at.add(BossKit.ground(sl, c));
        int T = 18;
        play(sl, p.position(), "bd_trumpet", 1.4f, 1.2f);
        for (Vec3 g : at) {
            BossKit.warnCircle(sl, g, 2.4f, T);
            { var vm = BoundFx.bfx(13, g, T); vm.r = 2.4f; BoundFx.bsend(sl, vm); }   // the scales of judgment, and the circle closing
            AstaFx.later(T, () -> {
                BoundFx.pillar(sl, g, 1.3f, 30);
                FxSend.shake(sl, g, 0.5f, 6, 18);
                for (LivingEntity t : Kit.near(sl, p, g.add(0, 1, 0), 2.4)) if (foe(p, t)) { AstaFx.damage(p, t, 8f, "seraph_verdict"); BoundFx.salt(p, t, 2); }
            });
        }
        AstaFx.later(T, () -> FxSend.impactFrame(sl, at.get(0), 3, false, 30));
    }

    /** Fall of Heaven: up on the wing, then down where you look - a crater of light and salt. */
    public static void fallOfHeaven(ServerPlayer p, float power) {
        if (!go(p)) return;
        ServerLevel sl = p.serverLevel();
        Vec3 land = BossKit.ground(sl, mark(p, 16));
        p.setDeltaMovement(p.getDeltaMovement().x * 0.2, 1.3, p.getDeltaMovement().z * 0.2);
        p.hurtMarked = true;
        p.fallDistance = 0;
        play(sl, p.position(), "bd_seraph", 1.6f, 1.3f);
        BossKit.warnCircle(sl, land, 8f, 16);
        { var vm = BoundFx.bfx(13, land, 16); vm.r = 8f; BoundFx.bsend(sl, vm); }
        AstaFx.later(14, () -> {
            if (!p.isAlive() || p.level() != sl) return;
            Vec3 from = p.position();
            p.teleportTo(land.x, land.y, land.z);
            p.setDeltaMovement(0, 0, 0); p.hurtMarked = true;
            p.fallDistance = 0;
            { var ln = BoundFx.bfx(12, from.add(0, 1, 0), 10); ln.u = land.add(0, 1, 0); ln.r = 0.6f; BoundFx.bsend(sl, ln); }
            BoundFx.pillar(sl, land, 4f, 44);
            FxSend.impactFrame(sl, land, 5, false, 32);
            FxSend.shake(sl, land, 1f, 14, 32);
            play(sl, land, "bd_seraph", 2f, 0.6f);
            for (LivingEntity t : Kit.near(sl, p, land.add(0, 1, 0), 8)) {
                if (!foe(p, t)) continue;
                double d = t.position().distanceTo(land);
                AstaFx.damage(p, t, d < 4 ? 14f : 8f, "seraph_descent");
                BoundFx.salt(p, t, 2);
                if (!BoundFx.boss(t)) Kit.push(t, BoundFx.flatAway(t, p), 1.4, 0.7);
            }
        });
    }

    /** Fold the Wing: the form ends - in a burst of feathers. */
    public static void foldWing(ServerPlayer p, float power) {
        if (!BoundFx.awake(p)) return;
        ServerLevel sl = p.serverLevel();
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6;
            Vec3 d = new Vec3(Math.cos(a), 0.4, Math.sin(a)).normalize();
            FxSend.spike(sl, SALT, mid(p), d, 0.9f, 0.1f, 10, 0, FxPacket.POP, 0, d.scale(0.4), 8);
        }
        play(sl, p.position(), "bd_seraph", 1f, 1.8f);
        BoundFx.endAwake(p);
    }

    /** While the seraph is awake: salt-white feathers drift off the wing, light under your feet. */
    static void aura(ServerPlayer p, ServerLevel sl, long now) {
        if (now % 6 == 0) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Vec3 left = rotY(p.getLookAngle(), Math.PI / 2).multiply(1, 0, 1).normalize();
            Vec3 at = p.position().add(left.scale(1 + r.nextDouble(3))).add(0, 1.5 + r.nextDouble(3), 0);   // a feather drifting down
            FxSend.spike(sl, SALT, at, new Vec3(r.nextDouble(-0.3, 0.3), -1, r.nextDouble(-0.3, 0.3)), 0.45f, 0.06f, 20, 0, FxPacket.POP, 0, new Vec3(0, -0.06, 0), 18);
        }
        if (now % 20 == 0) FxSend.ring(sl, SALT, p.position().add(0, 0.05, 0), 1.4f, 1.6f, 0.03f, 20);
    }

    // ------------------------------------------------------------------ the book
    /** The seraph's pages in place of the demon's (awake) or back again. Slots, levels and locks are kept. */
    public static void apply(ServerPlayer p, boolean seraph) {
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        if (book == null || !(book.getItem() instanceof BoundGrimoireItem) || !ISpellContainer.isSpellContainer(book)) return;
        ISpellContainer container = ISpellContainer.get(book);
        ISpellContainerMutable edit = null;
        for (SpellSlot slot : container.getAllSpells()) {
            if (slot == null) continue;
            AbstractSpell from = slot.getSpell();
            AbstractSpell to = seraph ? AddonSpellRegistry.seraphVersionOf(from) : AddonSpellRegistry.demonVersionOf(from);
            if (to == null || to == from) continue;
            if (edit == null) edit = container.mutableCopy();
            edit.removeSpellAtIndex(slot.index());
            edit.addSpellAtIndex(to, slot.getLevel(), slot.index(), slot.isLocked());
        }
        if (edit != null) ISpellContainer.set(book, edit.toImmutable());
    }
}
