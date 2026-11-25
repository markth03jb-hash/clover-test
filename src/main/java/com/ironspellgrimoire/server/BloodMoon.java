package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Blood Moon for mages: about one night in ten (never two in a row) everyone is told as dusk falls. Until dawn
 * elites come half again as often and each rolls one trait more (EliteMobs.roll), the wild families' grunts may bring
 * their leaders (WildFolkAI), and bounties brought down pay twice the stars (Bounties.complete). {@code /bloodmoon
 * start|stop} for ops.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class BloodMoon {
    private BloodMoon() {}
    public static final float CHANCE = 0.1f;

    static final class Data extends SavedData {
        boolean on;
        long decided = -1, last = -10;
        static Data load(CompoundTag t) { Data d = new Data(); d.on = t.getBoolean("On"); d.decided = t.getLong("Decided"); d.last = t.getLong("Last"); return d; }
        @Override public CompoundTag save(CompoundTag t) { t.putBoolean("On", on); t.putLong("Decided", decided); t.putLong("Last", last); return t; }
    }
    static Data data(MinecraftServer s) { return s.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "isg_blood_moon"); }

    public static boolean on(MinecraftServer s) { return s != null && data(s).on; }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 100 != 0) return;
        MinecraftServer s = e.getServer();
        Data d = data(s);
        long time = s.overworld().getDayTime(), day = time / 24000, t = time % 24000;
        boolean night = t >= 13000 && t < 23000;
        if (night && d.decided != day) {
            d.decided = day;
            if (!d.on && day - d.last > 1 && ThreadLocalRandom.current().nextFloat() < CHANCE) start(s, day);
            d.setDirty();
        }
        if (d.on && !night) stop(s);
    }

    static void start(MinecraftServer s, long day) {
        Data d = data(s);
        d.on = true; d.last = day; d.setDirty();
        Component c = Component.literal("BLOOD MOON  ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("Until dawn: elites come oftener and stronger, the wild leaders walk with their bands, and bounties pay twice the stars.").withStyle(ChatFormatting.RED));
        for (var p : s.getPlayerList().getPlayers()) {
            p.sendSystemMessage(c);
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 0.35f, 0.5f);
        }
    }

    static void stop(MinecraftServer s) {
        Data d = data(s);
        if (!d.on) return;
        d.on = false; d.setDirty();
        for (var p : s.getPlayerList().getPlayers()) p.sendSystemMessage(Component.literal("Dawn breaks. The Blood Moon is over.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("bloodmoon").requires(c -> c.hasPermission(2))
                .then(Commands.literal("start").executes(c -> { start(c.getSource().getServer(), c.getSource().getServer().overworld().getDayTime() / 24000); return 1; }))
                .then(Commands.literal("stop").executes(c -> { stop(c.getSource().getServer()); return 1; })));
    }

    public static void testSet(MinecraftServer s, boolean on) { if (on) start(s, s.overworld().getDayTime() / 24000); else stop(s); }
}
