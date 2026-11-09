package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.item.BlackGrimoireItem;
import com.example.arcaneaddon.network.FxPacket;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spell Clash: when two players' moves meet head-on - a slash against a slash, a ball against a beam - they clash.
 *   - Every move that travels (its carrier effect: slash, crescent, blade, ball, gust...) is followed here along the
 *     path its effect flies.
 *   - When two from different, non-allied casters come within reach of each other flying the opposite way, they
 *     clash at that point: a flash, rings in both their colours, sparks, a shake and the sound of it.
 *   - The stronger one (spell power x the size of the move) breaks through and flies on; the weaker one is torn
 *     apart there and its damage never lands. If they are within 15% of each other both are cancelled.
 *   - Anti magic (Asta's grimoire) always cuts through magic.
 * Nothing is announced: you see who won.
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public final class SpellClash {
    private SpellClash() {}

    private static final class Move {
        ServerPlayer owner; long window; int id; int pal; Vec3 c, dir; double speed, size; long born; int life; boolean anti; boolean dead;
        Vec3 at(long now) { return c.add(dir.scale(speed * Math.max(0, now - born))); }
    }

    private static final List<Move> MOVES = new ArrayList<>();
    /** owner -> the cast window whose damage is cancelled (its move lost a clash) */
    private static final Map<UUID, Long> BROKEN = new HashMap<>();

    /** Called for every travelling carrier a cast sends (CastSync.onSend). */
    static void track(ServerPlayer owner, long window, FxPacket p, double speed) {
        if (owner == null || speed <= 0.2 || p.c == null) return;
        Vec3 dir = p.shape == 19 || p.shape == 20 ? p.v : p.d;
        if (dir == null || dir.lengthSqr() < 1.0E-6) return;
        Move m = new Move();
        m.owner = owner; m.window = window; m.id = p.id; m.pal = p.palette & 0x1F; m.c = p.c; m.dir = dir.normalize();
        m.speed = speed; m.size = Math.max(0.6, Math.max(p.r, p.r2)); m.born = owner.serverLevel().getGameTime();
        m.life = Math.max(2, Math.min(60, p.life)); m.anti = anti(owner, m.pal);
        if (MOVES.size() > 256) MOVES.remove(0);
        MOVES.add(m);
    }

    private static boolean anti(ServerPlayer p, int pal) {
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        return book != null && book.getItem() instanceof BlackGrimoireItem;
    }

    /** True if this caster's current move lost a clash: its damage does not land. */
    public static boolean broken(ServerPlayer p, long window) {
        Long w = BROKEN.get(p.getUUID());
        return w != null && w == window;
    }

    private static double power(Move m) {
        double sp = 1.0;
        try { sp = m.owner.getAttributeValue(AttributeRegistry.SPELL_POWER.get()); } catch (Throwable ignored) {}
        return sp * (1.0 + m.size * 0.25);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || MOVES.isEmpty()) return;
        for (Iterator<Move> it = MOVES.iterator(); it.hasNext(); ) {
            Move m = it.next();
            if (m.dead || !m.owner.isAlive() || m.owner.serverLevel().getGameTime() - m.born > m.life) it.remove();
        }
        for (int i = 0; i < MOVES.size(); i++) {
            Move a = MOVES.get(i);
            if (a.dead) continue;
            long now = a.owner.serverLevel().getGameTime();
            Vec3 pa = a.at(now);
            for (int j = i + 1; j < MOVES.size(); j++) {
                Move b = MOVES.get(j);
                if (b.dead || b.owner == a.owner || b.owner.level() != a.owner.level() || a.owner.isAlliedTo(b.owner)) continue;
                if (a.dir.dot(b.dir) > -0.2) continue;   // head-on only
                Vec3 pb = b.at(now);
                double reach = Math.min(3.5, (a.size + b.size) * 0.5 + 0.8);
                if (pa.distanceToSqr(pb) > reach * reach) continue;
                clash(a, b, pa.add(pb).scale(0.5));
                break;
            }
        }
    }

    private static void clash(Move a, Move b, Vec3 at) {
        ServerLevel sl = a.owner.serverLevel();
        double pa = power(a), pb = power(b);
        Move win, lose;
        boolean both = false;
        if (a.anti != b.anti) { win = a.anti ? a : b; lose = win == a ? b : a; }        // anti magic cuts through magic
        else if (pa > pb * 1.15) { win = a; lose = b; }
        else if (pb > pa * 1.15) { win = b; lose = a; }
        else { win = a; lose = b; both = true; }

        // the clash itself: a flash, rings in both colours, sparks flying out, the ground shaking
        Fx.flash(sl, at, 2.4f);
        FxSend.ring(sl, a.pal, at, 0.2f, 3.2f, 0.3f, 8);
        FxSend.ring(sl, b.pal, at, 0.4f, 4.4f, 0.2f, 9);
        Fx.spark(sl, a.pal, at, 1.6f);
        Fx.spark(sl, b.pal, at, 1.6f);
        Fx.impact(sl, win.pal, at, 1.8f);
        FxSend.impactFrame(sl, at, 2, FxSend.FRAME_ASTA, 32);
        FxSend.shake(sl, at, 0.9f, 10, 40);
        FxUtil.sound(sl, at, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, 1.2f, 1.3f);
        FxUtil.sound(sl, at, net.minecraft.sounds.SoundEvents.ANVIL_LAND, 0.8f, 0.6f);

        lose.dead = true;
        FxSend.kill(sl, lose.id, at);
        BROKEN.put(lose.owner.getUUID(), lose.window);
        if (both) {
            win.dead = true;
            FxSend.kill(sl, win.id, at);
            BROKEN.put(win.owner.getUUID(), win.window);
        } else {
            FxSend.ring(sl, win.pal, at, 0.3f, 5.5f, 0.12f, 10);   // the winner breaking through
        }
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) { MOVES.clear(); BROKEN.clear(); }
}
