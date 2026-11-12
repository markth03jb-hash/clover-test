package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.CinderSovereign;
import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The world boss: the Cinder Sovereign (entity/CinderSovereign). About once a real day it rises somewhere in the world -
 * a few hundred blocks from one of the mages online - and every player is told where. It waits forty minutes for
 * challengers (longer while it's being fought), rising out of the ground when the first comes near, its health grown
 * for every mage there. When it falls, everyone who hurt it is rewarded by their share: merit stars and experience,
 * Mana Cores, and a chance (certain for whoever did the most) at a Sovereign's Ember, a grimoire upgrade found nowhere
 * else. {@code /worldboss} says where it is or when it's due; ops can start or summon it.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class WorldBoss {
    private WorldBoss() {}

    static final long HOUR = 3_600_000L;
    static final long WAIT = 40 * 60_000L;

    public static final class Data extends SavedData {
        long nextAt, expires;
        boolean active, risen;
        int x, z;
        UUID boss;

        static Data load(CompoundTag t) {
            Data d = new Data();
            d.nextAt = t.getLong("Next"); d.expires = t.getLong("Expires"); d.active = t.getBoolean("Active"); d.risen = t.getBoolean("Risen");
            d.x = t.getInt("X"); d.z = t.getInt("Z");
            if (t.hasUUID("Boss")) d.boss = t.getUUID("Boss");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            t.putLong("Next", nextAt); t.putLong("Expires", expires); t.putBoolean("Active", active); t.putBoolean("Risen", risen);
            t.putInt("X", x); t.putInt("Z", z);
            if (boss != null) t.putUUID("Boss", boss);
            return t;
        }
    }

    static Data data(MinecraftServer s) { return s.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "isg_world_boss"); }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        if (s.getTickCount() % 40 != 7) return;
        Data d = data(s);
        long now = System.currentTimeMillis();
        ServerLevel ow = s.overworld();
        if (d.nextAt == 0) { d.nextAt = now + 2 * HOUR; d.setDirty(); }
        if (!d.active) {
            if (now < d.nextAt || ow.players().isEmpty()) return;
            List<ServerPlayer> mages = new ArrayList<>();
            for (ServerPlayer p : ow.players()) if (GrimoireCeremony.chosen(p) >= 0 && !p.isSpectator()) mages.add(p);
            if (mages.isEmpty()) return;
            ServerPlayer near = mages.get(ThreadLocalRandom.current().nextInt(mages.size()));
            double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2), r = ThreadLocalRandom.current().nextDouble(250, 450);
            begin(s, (int) (near.getX() + Math.cos(a) * r), (int) (near.getZ() + Math.sin(a) * r));
            return;
        }
        CinderSovereign b = find(s, d);
        if (s.getTickCount() % 200 == 7) for (ServerPlayer p : s.getPlayerList().getPlayers()) track(p, d);
        if (!d.risen) {   // it waits under the ground until a mage comes near
            for (ServerPlayer p : ow.players()) {
                double dx = p.getX() - d.x, dz = p.getZ() - d.z;
                if (dx * dx + dz * dz < 80 * 80 && !p.isSpectator()) { rise(ow, d); return; }
            }
        } else if (b == null) {   // risen, but not here: lost with an unloaded chunk - it rises again when someone comes
            for (ServerPlayer p : ow.players()) {
                double dx = p.getX() - d.x, dz = p.getZ() - d.z;
                if (dx * dx + dz * dz < 48 * 48 && !p.isSpectator()) { d.risen = false; d.setDirty(); return; }
            }
        }
        if (now > d.expires) {
            if (b != null && b.isAlive() && ow.getGameTime() - b.lastHurt < 1200) { d.expires = now + 5 * 60_000L; d.setDirty(); return; }   // not while it's being fought
            if (b != null && b.isAlive()) depart(b);
            end(s, d);
            broadcast(s, Component.literal("The Cinder Sovereign sinks back into the earth. It will rise again.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        }
    }

    private static CinderSovereign find(MinecraftServer s, Data d) {
        if (d.boss == null) return null;
        Entity e = s.overworld().getEntity(d.boss);
        return e instanceof CinderSovereign c ? c : null;
    }

    /** It is coming: everyone is told where. */
    static void begin(MinecraftServer s, int x, int z) {
        Data d = data(s);
        d.active = true; d.risen = false; d.x = x; d.z = z; d.boss = null;
        d.expires = System.currentTimeMillis() + WAIT;
        d.setDirty();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("THE CINDER SOVEREIGN").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("is rising").withStyle(ChatFormatting.GOLD)));
            p.playNotifySound(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 0.8f, 0.5f);
            p.sendSystemMessage(where(p, d));
            track(p, d);
        }
    }

    /** The tracker at the top right (client/WorldBossHud): where it is and how long it waits - or, ended, gone. */
    static void track(ServerPlayer p, Data d) {
        com.ironspellgrimoire.network.FxPacket k = new com.ironspellgrimoire.network.FxPacket();
        k.shape = 78; k.id = p.getId();
        k.life = d.active ? 1 : 0;
        k.c = new Vec3(d.x + 0.5, 0, d.z + 0.5);
        k.sweep = d.risen ? 1 : 0;
        k.r = (float) Math.max(0, (d.expires - System.currentTimeMillis()) / 50L);
        com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), k);
    }

    @SubscribeEvent
    public static void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { Data d = data(p.server); if (d.active) { track(p, d); p.sendSystemMessage(where(p, d)); } }
    }

    static Component where(ServerPlayer p, Data d) {
        double dx = d.x - p.getX(), dz = d.z - p.getZ();
        boolean ow = p.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD);
        return Component.literal("WORLD BOSS  ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal("The ground burns at " + d.x + ", " + d.z + (ow ? " - about " + (int) Math.hypot(dx, dz) + " blocks " + Bounties.direction(dx, dz) : " (in the Overworld)")
                        + ". It waits " + Math.max(1, (d.expires - System.currentTimeMillis()) / 60_000L) + " minutes for challengers.").withStyle(ChatFormatting.GOLD));
    }

    /** The first mage comes near: it rises out of the ground, its health grown for every mage nearby. */
    static void rise(ServerLevel sl, Data d) {
        CinderSovereign b = AddonEntities.CINDER_SOVEREIGN.get().create(sl);
        if (b == null) return;
        Vec3 at = ZenonAI.ground(sl, new Vec3(d.x + 0.5, sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, d.x, d.z) + 2, d.z + 0.5));
        int mages = 0;
        for (ServerPlayer p : sl.players()) if (p.distanceToSqr(at) < 128 * 128 && !p.isSpectator()) mages++;
        double hp = 2000 + 900 * Math.max(0, mages - 1);
        b.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        b.setHealth((float) hp);
        Player near = sl.getNearestPlayer(at.x, at.y, at.z, 128, false);
        float yaw = near == null ? 0 : (float) Math.toDegrees(Math.atan2(-(near.getX() - at.x), near.getZ() - at.z));
        b.moveTo(at.x, at.y, at.z, yaw, 0);
        b.setYHeadRot(yaw); b.yBodyRot = yaw;
        b.busy = 70;
        sl.addFreshEntity(b);
        b.play("roar");
        d.risen = true; d.boss = b.getUUID(); d.setDirty();
        for (ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) track(p, d);
        BossFx.ring(sl, Fx.FIRE, at.add(0, 0.2, 0), 1f, 18f, 0.6f, 24);
        BossFx.beam(sl, Fx.FUEGO, at, at.add(0, 50, 0), 4f, 30);
        FxSend.shake(sl, at, 1.6f, 30, 96);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 5f, 0.35f);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4f, 0.4f);
        for (ServerPlayer p : sl.players()) if (p.distanceToSqr(at) < 160 * 160)
            p.sendSystemMessage(Component.literal("The Cinder Sovereign: ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .append(Component.literal(mages > 1 ? "So many little flames. I will gutter you all." : "One little flame, come to the furnace.").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC)));
    }

    private static void depart(CinderSovereign b) {
        ServerLevel sl = (ServerLevel) b.level();
        Vec3 c = b.position();
        BossFx.ring(sl, Fx.FIRE, c.add(0, 0.2, 0), 12f, 1f, 0.5f, 20);
        BossFx.beam(sl, Fx.FUEGO, c, c.add(0, 40, 0), 3f, 20);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4f, 0.3f);
        b.discard();
    }

    private static void end(MinecraftServer s, Data d) {
        d.active = false; d.risen = false; d.boss = null;
        d.nextAt = System.currentTimeMillis() + 20 * HOUR + ThreadLocalRandom.current().nextLong(8 * HOUR);
        d.setDirty();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) track(p, d);
    }

    private static void broadcast(MinecraftServer s, Component c) { for (ServerPlayer p : s.getPlayerList().getPlayers()) p.sendSystemMessage(c); }

    /** It falls: rewards for everyone who hurt it, by their share. */
    public static void defeated(CinderSovereign b) {
        ServerLevel sl = (ServerLevel) b.level();
        MinecraftServer s = sl.getServer();
        Data d = data(s);
        if (d.boss != null && d.boss.equals(b.getUUID())) end(s, d);
        float total = 0;
        for (float v : b.dealt.values()) total += v;
        if (total <= 0) return;
        UUID top = null; float best = -1;
        for (Map.Entry<UUID, Float> e : b.dealt.entrySet()) if (e.getValue() > best) { best = e.getValue(); top = e.getKey(); }
        List<String> names = new ArrayList<>();
        for (Map.Entry<UUID, Float> e : b.dealt.entrySet()) {
            ServerPlayer p = s.getPlayerList().getPlayer(e.getKey());
            if (p == null) continue;
            float share = e.getValue() / total;
            if (share < 0.02f && e.getValue() < 60) continue;
            names.add(p.getGameProfile().getName());
            int stars = Math.min(60, 20 + Math.round(share * 80));
            Bounties.addStars(p, stars);
            p.giveExperiencePoints(800 + Math.round(share * 1600));
            give(p, new ItemStack(AddonItemRegistry.MANA_CORE.get(), share >= 0.25f ? 3 : 2));
            boolean ember = e.getKey().equals(top) || ThreadLocalRandom.current().nextFloat() < 0.4f;
            if (ember) give(p, new ItemStack(AddonItemRegistry.SOVEREIGN_EMBER.get()));
            if (ThreadLocalRandom.current().nextFloat() < 0.5f) { ItemStack ink = new ItemStack(AddonItemRegistry.RIFT_INK.get()); ink.getOrCreateTag().putInt("Rule", 4 + ThreadLocalRandom.current().nextInt(6)); give(p, ink); }
            p.sendSystemMessage(Component.literal("THE CINDER SOVEREIGN FALLS  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("Your share: " + Math.round(share * 100) + "% - +" + stars + " merit stars, Mana Cores" + (ember ? ", a Sovereign's Ember" : "") + ".").withStyle(ChatFormatting.YELLOW)));
        }
        if (!names.isEmpty()) broadcast(s, Component.literal("The Cinder Sovereign has been brought down by " + String.join(", ", names) + ".").withStyle(ChatFormatting.GOLD));
    }

    private static void give(ServerPlayer p, ItemStack st) { if (!p.getInventory().add(st)) p.drop(st, false); }

    // ------------------------------------------------------------------ /worldboss
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("worldboss").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            Data d = data(p.server);
            if (d.active) p.sendSystemMessage(where(p, d));
            else {
                long h = Math.max(0, d.nextAt - System.currentTimeMillis()) / 60_000L;
                p.sendSystemMessage(Component.literal("The Cinder Sovereign sleeps. It stirs in about " + (h >= 60 ? (h / 60) + " h " : "") + (h % 60) + " min.").withStyle(ChatFormatting.GRAY));
            }
            return 1;
        }).then(Commands.literal("start").requires(s -> s.hasPermission(2)).executes(c -> {   // ops: it rises 40 blocks before you
            ServerPlayer p = c.getSource().getPlayerOrException();
            Vec3 at = p.position().add(FxUtil.flat(p.getLookAngle()).normalize().scale(40));
            Data d = data(p.server);
            CinderSovereign old = find(p.server, d);
            if (old != null) old.discard();
            begin(p.server, (int) at.x, (int) at.z);
            return 1;
        })).then(Commands.literal("end").requires(s -> s.hasPermission(2)).executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            Data d = data(p.server);
            CinderSovereign b = find(p.server, d);
            if (b != null) depart(b);
            end(p.server, d);
            return 1;
        })));
    }
}
