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

    // ------------------------------------------------------------------ the pages
    /** Judgment Feathers: sixteen feathers out of the wing, thrown at what you look at. */
    public static void feathers(ServerPlayer p, float power) {
        if (!go(p)) return;
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition(), target = Kit.aim(p, 24);
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
                FxSend.spike(sl, k % 4 == 0 ? GOLD : SALT, from, d, 1.1f, 0.12f, ticks + 2, 0, FxPacket.POP, 0, d.scale(len / ticks), ticks);
                FxSend.trail(sl, SALT, from, to, 0.04f, ticks + 4);
                AstaFx.later(ticks, () -> {
                    for (LivingEntity t : Kit.near(sl, p, to, 1.4)) if (foe(p, t)) { AstaFx.damage(p, t, 2.5f, "seraph_feathers"); BoundFx.salt(p, t, 1); }
                    FxSend.ring(sl, SALT, to, 0.1f, 0.7f, 0.04f, 6);
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
        for (int k = 0; k < 3; k++) {
            final int kk = k;
            AstaFx.later(k, () -> FxSend.wall(sl, kk == 2 ? GOLD : SALT, c.add(0, kk * 0.4, 0), look, new Vec3(0, 1, 0), 2f + kk * 1.6f, 4.5f + kk * 1.6f, -115, 115, 1.4f - kk * 0.3f, 10, 6, 0));
        }
        FxSend.crescent(sl, SALT, c.add(look.scale(2)), look, new Vec3(0, 1, 0), 3.5f, 7.5f, 9, 1.1f);
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
        FxSend.ring(sl, SALT, eye.add(p.getLookAngle().scale(1.5)), 0.3f, 2.2f, 0.12f, 8);
        double[] fan = {-0.2, -0.1, 0, 0.1, 0.2};
        for (int i = 0; i < fan.length; i++) {
            final double ang = fan[i];
            AstaFx.later(i, () -> {
                Vec3 d = rotY(p.getLookAngle(), ang).normalize();
                Vec3 end = clip(sl, p, eye, eye.add(d.scale(30)));
                FxSend.beam(sl, SALT, eye.add(d), end, 0.32f, 10);
                FxSend.beam(sl, Fx.SILVER, eye.add(d), end, 0.12f, 12);
                FxSend.cracks(sl, SALT, end, d.scale(-1), new Vec3(0, 1, 0), 1.6f, 14, false, (int) (ang * 100) + 7);
                FxSend.ring(sl, SALT, end, 0.2f, 1.4f, 0.08f, 8);
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
        Vec3 c = Kit.aim(p, 20);
        List<Vec3> at = new ArrayList<>();
        for (LivingEntity t : Kit.near(sl, p, c, 12)) if (foe(p, t) && at.size() < 8) at.add(BossKit.ground(sl, t.position()));
        if (at.isEmpty()) at.add(BossKit.ground(sl, c));
        int T = 18;
        play(sl, p.position(), "bd_trumpet", 1.4f, 1.2f);
        for (Vec3 g : at) {
            BossKit.warnCircle(sl, g, 2.4f, T);
            FxSend.ring(sl, GOLD, g.add(0, 0.05, 0), 2.2f, 2.4f, 0.05f, T);
            AstaFx.later(T, () -> {
                FxSend.beam(sl, SALT, g.add(0, 26, 0), g, 1.3f, 12);
                FxSend.beam(sl, Fx.SILVER, g.add(0, 26, 0), g, 0.5f, 14);
                FxSend.ring(sl, SALT, g.add(0, 0.1, 0), 0.4f, 3.4f, 0.25f, 12);
                FxSend.cracks(sl, SALT, g.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 2.6f, 20, false, (int) (g.x * 7 + g.z));
                for (int k = 0; k < 6; k++) {   // salt crystals thrust up round it
                    double a = k * Math.PI / 3;
                    Vec3 b = g.add(Math.cos(a) * 1.6, 0, Math.sin(a) * 1.6);
                    FxSend.spike(sl, SALT, b, new Vec3(Math.cos(a) * 0.3, 1, Math.sin(a) * 0.3), 1.4f, 0.25f, 24, 3, FxPacket.GROUND);
                }
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
        Vec3 land = BossKit.ground(sl, Kit.aim(p, 16));
        p.setDeltaMovement(p.getDeltaMovement().x * 0.2, 1.3, p.getDeltaMovement().z * 0.2);
        p.hurtMarked = true;
        p.fallDistance = 0;
        play(sl, p.position(), "bd_seraph", 1.6f, 1.3f);
        FxSend.ring(sl, SALT, p.position().add(0, 0.1, 0), 0.3f, 3f, 0.15f, 10);
        BossKit.warnCircle(sl, land, 8f, 16);
        AstaFx.later(14, () -> {
            if (!p.isAlive() || p.level() != sl) return;
            Vec3 from = p.position();
            p.teleportTo(land.x, land.y, land.z);
            p.setDeltaMovement(0, 0, 0); p.hurtMarked = true;
            p.fallDistance = 0;
            FxSend.beam(sl, SALT, from.add(0, 1, 0), land.add(0, 1, 0), 1.4f, 8);
            FxSend.ring(sl, SALT, land.add(0, 0.1, 0), 0.5f, 9f, 0.4f, 16);
            FxSend.ring(sl, GOLD, land.add(0, 0.1, 0), 0.3f, 6f, 0.2f, 12);
            FxSend.cracks(sl, SALT, land.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 6f, 30, false, 31);
            for (int k = 0; k < 12; k++) {   // salt thrown up round the crater's rim
                double a = k * Math.PI / 6;
                Vec3 b = land.add(Math.cos(a) * 5, 0, Math.sin(a) * 5);
                FxSend.spike(sl, SALT, b, new Vec3(Math.cos(a) * 0.5, 1, Math.sin(a) * 0.5), 2.2f, 0.35f, 30, 3, FxPacket.GROUND);
            }
            FxSend.beam(sl, SALT, land.add(0, 30, 0), land, 2.2f, 10);
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
            Kit.orb(sl, SALT, p.position().add(left.scale(1 + r.nextDouble(3))).add(0, 1.5 + r.nextDouble(3), 0), new Vec3(0, -0.04, 0), 0.12f, 20);
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
