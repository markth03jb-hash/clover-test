package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.WaypointPackets;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The Spatial Grimoire's Waypoint: cast it to open a menu of every place you have saved - save where you stand under a
 * name you choose, go to any saved place, or forget one. Waypoints never expire and stay with you through death.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class Waypoints {
    private Waypoints() {}
    private static final String TAG = "iron_spell_grimoire_waypoints";
    private static final int MAX = 30;

    private static ListTag tag(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (!d.contains(TAG, Tag.TAG_LIST)) d.put(TAG, new ListTag());
        return d.getList(TAG, Tag.TAG_COMPOUND);
    }

    private static List<WaypointPackets.Entry> list(ServerPlayer p) {
        List<WaypointPackets.Entry> l = new ArrayList<>();
        for (Tag t : tag(p)) {
            CompoundTag c = (CompoundTag) t;
            l.add(new WaypointPackets.Entry(c.getString("n"), c.getString("d"), (int) Math.floor(c.getDouble("x")), (int) Math.floor(c.getDouble("y")), (int) Math.floor(c.getDouble("z"))));
        }
        return l;
    }

    /** The spell: opens the menu. */
    public static void open(ServerPlayer p) {
        AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new WaypointPackets.Open(list(p)));
    }

    private static boolean holdsGrimoire(ServerPlayer p) {
        ItemStack s = Utils.getPlayerSpellbookStack(p);
        return s != null && !s.isEmpty() && s.getItem() == AddonItemRegistry.SPACE_GRIMOIRE.get();
    }

    public static void handle(ServerPlayer p, int action, int index, String rawName) {
        if (!holdsGrimoire(p) || !p.isAlive()) return;
        ListTag l = tag(p);
        ServerLevel sl = p.serverLevel();
        switch (action) {
            case 0 -> {
                if (l.size() >= MAX) { p.displayClientMessage(Component.literal("You can only keep " + MAX + " waypoints.").withStyle(ChatFormatting.RED), true); break; }
                String name = rawName.replaceAll("[^\\p{L}\\p{N} _'\\-]", "").trim();
                if (name.isEmpty()) name = "Waypoint " + (l.size() + 1);
                if (name.length() > 24) name = name.substring(0, 24);
                CompoundTag c = new CompoundTag();
                c.putString("n", name);
                c.putString("d", sl.dimension().location().toString());
                c.putDouble("x", p.getX()); c.putDouble("y", p.getY()); c.putDouble("z", p.getZ());
                l.add(c);
                FxSend.ring(sl, Fx.SILVER, p.position().add(0, 0.05, 0), 0.2f, 1.6f, 0.05f, 10);
                SpaceFx.gate(sl, p.position().add(0, 0.06, 0), new Vec3(0, 1, 0), 1.15f, 1.15f, 40, 0);
                sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_PLACE, 1f, 1.2f);
                p.displayClientMessage(Component.literal("Waypoint saved: " + name).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            case 1 -> {
                if (index < 0 || index >= l.size()) break;
                CompoundTag c = l.getCompound(index);
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(c.getString("d")));
                ServerLevel dest = p.server.getLevel(key);
                if (dest == null) { p.displayClientMessage(Component.literal("That place can't be reached.").withStyle(ChatFormatting.RED), true); break; }
                Vec3 at = new Vec3(c.getDouble("x"), c.getDouble("y"), c.getDouble("z"));
                Vec3 look = flat(p.getLookAngle());
                SpaceFx.gate(sl, p.position().add(look.scale(0.8)).add(0, 1, 0), look.scale(-1), 0.7f, 1.1f, 12, 0);
                SpaceFx.whoosh(sl, p.position());
                AstaFx.later(3, () -> {
                    if (!alive(p)) return;
                    if (dest == p.serverLevel()) SpaceFx.move(p, at, p.getYRot());
                    else p.teleportTo(dest, at.x, at.y, at.z, p.getYRot(), p.getXRot());
                    SpaceFx.gate(dest, at.add(look.scale(0.8)).add(0, 1, 0), look, 0.7f, 1.1f, 12, 0);
                    SpaceFx.whoosh(dest, at);
                });
            }
            case 2 -> {
                if (index < 0 || index >= l.size()) break;
                l.remove(index);
            }
            default -> { }
        }
        if (action != 1) open(p);   // refresh the menu
    }

    /** Waypoints stay with you through death. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        CompoundTag old = e.getOriginal().getPersistentData();
        if (old.contains(TAG, Tag.TAG_LIST)) e.getEntity().getPersistentData().put(TAG, old.getList(TAG, Tag.TAG_COMPOUND).copy());
    }
}
