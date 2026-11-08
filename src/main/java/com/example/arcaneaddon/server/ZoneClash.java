package com.example.arcaneaddon.server;

import com.example.arcaneaddon.AddonConfig;
import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.network.AddonNetwork;
import com.example.arcaneaddon.network.FxPacket;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mana Zone clash: when two players' Mana Zones overlap, the stronger one (higher Grimoire Mastery, then more mana
 * left) pushes its border into the other. The weaker dome is shoved back and shrinks so the two never overlap, and
 * while its owner stands inside the stronger zone their own zone does nothing (no zone versions of their spells, no
 * zone effects). A bright seam of sparks and cracking space marks where the two zones grind against each other.
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public final class ZoneClash {
    private ZoneClash() {}

    private static final double MIN_R = 2.5;

    /** Losing zones: owner -> (winner, pushed-back radius, smothered). */
    private record Loss(UUID winner, double radius, boolean smothered) {}
    private static final Map<UUID, Loss> LOSING = new HashMap<>();

    private record Zone(ServerPlayer p, double r, int pal, long end, String spell) {}

    /** True while a stronger Mana Zone has this player inside it: their own zone is smothered. */
    public static boolean suppressed(ServerPlayer p) {
        Loss l = LOSING.get(p.getUUID());
        return l != null && l.smothered();
    }

    /** The radius this player's zone should be drawn at right now. */
    public static double radius(ServerPlayer p, double full) {
        Loss l = LOSING.get(p.getUUID());
        return l == null ? full : Math.min(full, l.radius());
    }

    private static List<Zone> zones(MinecraftServer server) {
        List<Zone> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            long d = DarkFx.zoneEnd(p);
            if (d > 0) out.add(new Zone(p, DarkFx.ZONE_RADIUS, Fx.YAMI, d, "mana_zone_dimension_slash"));
            long h = HellfireFx.zoneEnd(p);
            if (h > 0) out.add(new Zone(p, HellfireFx.ZONE_RADIUS, 14, h, "lion_mana_zone"));
        }
        return out;
    }

    /** Mastery decides first (when progression is on), then the mana you have left. */
    private static double strength(ServerPlayer p) {
        double mastery = 1;
        if (AddonConfig.progression()) {
            var book = Utils.getPlayerSpellbookStack(p);
            if (book != null && GrimoireMastery.bookOf(book) != null) mastery = GrimoireMastery.level(book);
        }
        return mastery * 100000 + MagicData.getPlayerMagicData(p).getMana();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = e.getServer();
        long tick = server.getTickCount();
        if (tick % 5 != 0) return;
        List<Zone> zones = zones(server);
        Map<UUID, Loss> next = new HashMap<>();
        Map<UUID, Zone> byOwner = new HashMap<>();
        for (Zone z : zones) byOwner.put(z.p().getUUID(), z);
        for (int i = 0; i < zones.size(); i++) {
            for (int j = i + 1; j < zones.size(); j++) {
                Zone a = zones.get(i), b = zones.get(j);
                if (a.p() == b.p() || a.p().level() != b.p().level() || a.p().isAlliedTo(b.p())) continue;
                double d = a.p().position().distanceTo(b.p().position());
                if (d >= a.r() + b.r()) continue;
                Zone win = strength(a.p()) >= strength(b.p()) ? a : b, lose = win == a ? b : a;
                double r = Math.max(MIN_R, Math.min(lose.r(), d - win.r()));
                boolean smothered = d < win.r();
                Loss prev = next.get(lose.p().getUUID());
                if (prev == null || r < prev.radius()) next.put(lose.p().getUUID(), new Loss(win.p().getUUID(), r, smothered || (prev != null && prev.smothered())));
                seam((ServerLevel) win.p().level(), win, lose, d, tick);
            }
        }
        // tell the clients when a dome is pushed back, released, or the clash starts / ends
        for (Map.Entry<UUID, Loss> en : next.entrySet()) {
            Loss now = en.getValue(), was = LOSING.get(en.getKey());
            Zone z = byOwner.get(en.getKey());
            if (z == null) continue;
            if (was == null) {
                ServerPlayer w = server.getPlayerList().getPlayer(now.winner());
                z.p().displayClientMessage(Component.translatable("ui.arcane_addon.zone_clash.losing", w == null ? "?" : w.getDisplayName()).withStyle(ChatFormatting.RED), true);
                if (w != null) w.displayClientMessage(Component.translatable("ui.arcane_addon.zone_clash.winning", z.p().getDisplayName()).withStyle(ChatFormatting.GOLD), true);
            }
            if (was == null || Math.abs(was.radius() - now.radius()) > 0.4) LOSING.put(en.getKey(), now); else LOSING.put(en.getKey(), new Loss(now.winner(), was.radius(), now.smothered()));
            if (was == null || Math.abs(was.radius() - now.radius()) > 0.4) sendRadius(z, now.radius(), server);
        }
        for (UUID id : new ArrayList<>(LOSING.keySet())) {
            if (next.containsKey(id)) continue;
            LOSING.remove(id);   // the clash is over: the dome swells back to full size
            Zone z = byOwner.get(id);
            if (z != null) sendRadius(z, z.r(), server);
        }
    }

    private static void sendRadius(Zone z, double r, MinecraftServer server) {
        long now = z.p().serverLevel().getGameTime();
        FxPacket pkt = new FxPacket();
        pkt.shape = 17; pkt.id = z.p().getId(); pkt.palette = (byte) z.pal(); pkt.life = (int) Math.max(1, z.end() - now); pkt.r = (float) r;
        AddonNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(z::p), pkt);
    }

    /** Where the two zones grind together: sparks in both colours, and space cracking along the border. */
    private static void seam(ServerLevel sl, Zone win, Zone lose, double d, long tick) {
        Vec3 a = win.p().position(), b = lose.p().position();
        Vec3 dir = b.subtract(a).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : dir.normalize();
        double along = Math.min(win.r(), d - MIN_R * 0.5);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 c = a.add(dir.scale(along)).add(0, 1.4, 0);
        double off = Math.sin(tick * 0.37) * 2.5;
        FxLib.spark(sl, win.pal(), c.add(side.scale(off)), 0.9f);
        FxLib.spark(sl, lose.pal(), c.add(side.scale(-off)).add(0, 0.8, 0), 0.7f);
        if (tick % 10 == 0) {
            FxSend.cracks(sl, win.pal(), c.add(side.scale(off * 0.5)).add(0, 0.6, 0), side, new Vec3(0, 1, 0), 2.2f, 16, false, (int) tick);
            FxSend.wall(sl, win.pal(), a.add(0, 0.05, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), (float) along - 0.2f, (float) along, -180, 180, 0.08f, 10, 0, 0);
        }
        if (tick % 20 == 0) {
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 0.5f);
        }
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) { LOSING.clear(); }
}
