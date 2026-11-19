package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FirstStepsPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * A new mage's first steps, shown as a small tracker (client/FirstStepsHud) until a grimoire chooses them:
 * read the Grimoire Codex, find the Grimoire Tower (where the starter compass points), be chosen at the altar.
 * State lives in the player's persistent data ("isg_steps"); players who were chosen before it existed never see it.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class FirstSteps {
    private FirstSteps() {}

    private static CompoundTag tag(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (!d.contains("isg_steps")) d.put("isg_steps", new CompoundTag());
        return d.getCompound("isg_steps");
    }

    /** Where the starter compass points (TowerCompass, on first join). */
    public static void towerAt(ServerPlayer p, int x, int z) {
        CompoundTag t = tag(p);
        t.putInt("x", x); t.putInt("z", z); t.putBoolean("known", true);
        sync(p);
    }

    /** The player opened the Codex (CodexItem). */
    public static void codexRead(ServerPlayer p) {
        CompoundTag t = tag(p);
        if (t.getBoolean("codex")) return;
        t.putBoolean("codex", true);
        sync(p);
    }

    private static boolean active(ServerPlayer p) {
        CompoundTag t = tag(p);
        return t.getBoolean("started") && !t.getBoolean("done");
    }

    public static void sync(ServerPlayer p) {
        CompoundTag t = tag(p);
        int f = active(p) ? 1 : 0;
        if (t.getBoolean("codex")) f |= 2;
        if (t.getBoolean("tower")) f |= 4;
        if (t.getBoolean("known")) f |= 8;
        AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new FirstStepsPacket(f, t.getInt("x"), t.getInt("z")));
    }

    /** Runs before TowerCompass's login (which hands out the compass and tells us where it points). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag t = tag(p);
        if (!t.getBoolean("started")) {
            t.putBoolean("started", true);
            boolean fresh = !p.getPersistentData().getBoolean("isg_tower_compass");   // never joined with the mod before
            if (!fresh || GrimoireCeremony.chosen(p) >= 0) t.putBoolean("done", true);
            else p.sendSystemMessage(Component.literal("Welcome, mage! ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                    .append(Component.literal("Your first steps are in the top right. Right-click the Grimoire Codex to read how it all works.").withStyle(ChatFormatting.GRAY)));
        }
        sync(p);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 13 || !active(p)) return;
        CompoundTag t = tag(p);
        if (GrimoireCeremony.chosen(p) >= 0) {
            t.putBoolean("done", true);
            p.displayClientMessage(Component.literal("First steps done - your grimoire has chosen you.").withStyle(ChatFormatting.AQUA), true);
            sync(p);
            return;
        }
        if (!t.getBoolean("tower") && t.getBoolean("known") && p.level().dimension().equals(Level.OVERWORLD)) {
            double dx = t.getInt("x") + 0.5 - p.getX(), dz = t.getInt("z") + 0.5 - p.getZ();
            if (dx * dx + dz * dz < 24 * 24) { t.putBoolean("tower", true); sync(p); }
        }
    }
}
