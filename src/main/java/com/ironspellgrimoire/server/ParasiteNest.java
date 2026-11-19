package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;
import static com.ironspellgrimoire.server.ParasiteFx.*;
import static com.ironspellgrimoire.server.ParasiteMoves.*;
import com.ironspellgrimoire.entity.AddonBoss;
import com.ironspellgrimoire.entity.HiveNest;
import com.ironspellgrimoire.entity.ShadowSoldier;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.network.ParasitePackets;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Split out of ParasiteFx (same code, own file): the Hive Nest. */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class ParasiteNest {
    private ParasiteNest() {}

    // ------------------------------------------------------------------ the Hive Nest
    static final int NEST_COST = 50;

    /** Your nests: {id, dim, x, y, z, cd} each (an older save's single nest moved into the list). */
    static net.minecraft.nbt.ListTag nests(Player p) {
        CompoundTag r = root(p);
        if (r.hasUUID("nest") && !r.contains("nests")) {
            CompoundTag e = new CompoundTag();
            e.putUUID("id", r.getUUID("nest")); e.putString("dim", r.getString("nestDim"));
            e.putDouble("x", r.getDouble("nestX")); e.putDouble("y", r.getDouble("nestY")); e.putDouble("z", r.getDouble("nestZ"));
            net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag(); l.add(e); r.put("nests", l);
        }
        r.remove("nest");
        if (!r.contains("nests")) r.put("nests", new net.minecraft.nbt.ListTag());
        return r.getList("nests", 10);
    }

    static boolean ownsNest(Player p, java.util.UUID id) {
        for (net.minecraft.nbt.Tag t : nests(p)) if (((CompoundTag) t).getUUID("id").equals(id)) return true;
        return false;
    }

    /** The nearest of your nests that's loaded where you are. */
    static HiveNest nestOf(ServerPlayer p) {
        HiveNest best = null;
        for (net.minecraft.nbt.Tag t : nests(p)) {
            Entity e = p.serverLevel().getEntity(((CompoundTag) t).getUUID("id"));
            if (e instanceof HiveNest n && n.isAlive() && (best == null || n.distanceToSqr(p) < best.distanceToSqr(p))) best = n;
        }
        return best;
    }

    static int maxNests(Player p) { return rank(p, NETWORK) > 0 ? 3 : 1; }

    /** Grow a nest where you stand (50 biomass): one, or three with Hive Network - past that the oldest withers. */
    public static void growNest(ServerPlayer p) {
        if (!p.onGround()) { p.displayClientMessage(Component.literal("Stand on the ground to grow a nest").withStyle(ChatFormatting.GRAY), true); return; }
        if (!spend(p, NEST_COST)) { p.displayClientMessage(Component.literal("Growing a nest takes " + NEST_COST + " biomass").withStyle(ChatFormatting.RED), true); return; }
        ServerLevel sl = p.serverLevel();
        net.minecraft.nbt.ListTag list = nests(p);
        float kept = 0;
        while (list.size() >= maxNests(p)) {   // the oldest withers (its biomass carried over)
            CompoundTag old = list.getCompound(0);
            list.remove(0);
            Entity e = sl.getEntity(old.getUUID("id"));
            if (e instanceof HiveNest n) { kept += n.stored(); n.discard(); }
        }
        HiveNest n = com.ironspellgrimoire.registry.AddonEntities.HIVE_NEST.get().create(sl);
        if (n == null) return;
        n.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0);
        n.setOwner(p.getUUID());
        n.setStored(kept);
        sl.addFreshEntity(n);
        CompoundTag e = new CompoundTag();
        e.putUUID("id", n.getUUID()); e.putString("dim", sl.dimension().location().toString());
        e.putDouble("x", n.getX()); e.putDouble("y", n.getY()); e.putDouble("z", n.getZ());
        list.add(e);
        root(p).put("nests", list);
        groundSpines(sl, n.position(), 2.0f, 12, 12);
        sound(sl, n.position(), SoundEvents.SLIME_SQUISH, 1.2f, 0.4f);
        sound(sl, n.position(), SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.8f);
        p.displayClientMessage(Component.literal("A Hive Nest takes root (" + list.size() + "/" + maxNests(p) + ")").withStyle(ChatFormatting.RED), true);
        dirty(p);
    }

    /** Hive Network: from beside one of your nests, sink and burst out at another (5 min a destination). */
    static void nestTravel(ServerPlayer p, int index) {
        net.minecraft.nbt.ListTag list = nests(p);
        if (rank(p, NETWORK) <= 0 || index < 0 || index >= list.size()) return;
        HiveNest here = nestOf(p);
        if (here == null || here.distanceTo(p) > 4) { p.displayClientMessage(Component.literal("Stand by one of your nests").withStyle(ChatFormatting.GRAY), true); return; }
        CompoundTag to = list.getCompound(index);
        if (to.getUUID("id").equals(here.getUUID())) return;
        long now = p.serverLevel().getGameTime();
        if (now < to.getLong("cd")) { p.displayClientMessage(Component.literal("That nest is still knitting: " + (to.getLong("cd") - now) / 20 + " s").withStyle(ChatFormatting.GRAY), true); return; }
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new net.minecraft.resources.ResourceLocation(to.getString("dim")));
        ServerLevel there = p.server.getLevel(key);
        if (there == null) return;
        to.putLong("cd", now + 6000);
        root(p).put("nests", list);
        ServerLevel sl = p.serverLevel();
        groundSpines(sl, p.position(), 1.5f, 10, 10);
        sound(sl, p.position(), SoundEvents.ROOTED_DIRT_BREAK, 1.0f, 0.5f);
        p.teleportTo(there, to.getDouble("x"), to.getDouble("y") + 0.3, to.getDouble("z"), p.getYRot(), p.getXRot());
        groundSpines(there, p.position(), 2.0f, 14, 12);
        sound(there, p.position(), SoundEvents.EVOKER_FANGS_ATTACK, 1.0f, 0.6f);
        root(p).putLong("glide", there.getGameTime() + 40);
        dirty(p);
    }

    /** Every 2 s: a nest that's no longer its owner's withers; its owner, near it, is healed. */
    public static void nestTick(HiveNest n) {
        if (!(n.level() instanceof ServerLevel sl)) return;
        if (n.owner() == null) { n.discard(); return; }
        if (!(sl.getPlayerByUUID(n.owner()) instanceof ServerPlayer p)) return;
        CompoundTag r = root(p);
        if (!ownsNest(p, n.getUUID())) { n.discard(); return; }
        if (p.distanceTo(n) < 6 && p.getHealth() < p.getMaxHealth()) {
            p.heal(1f);
            tendril(sl, n.position().add(0, 0.6, 0), mid(p), 6);
        }
    }

    /** Its owner takes the stored biomass back (up to a full gut). */
    public static void nestUsed(ServerPlayer p, HiveNest n) {
        if (!p.getUUID().equals(n.owner())) { p.displayClientMessage(Component.literal("Someone else's nest").withStyle(ChatFormatting.GRAY), true); return; }
        CompoundTag r = root(p);
        float room = cap(p) - r.getFloat("bio"), take = Math.min(room, n.stored());
        if (rank(p, NETWORK) > 0 && nests(p).size() > 1 && p.isShiftKeyDown()) { sendNests(p); return; }   // sneak + right-click: travel
        if (take <= 0) { p.displayClientMessage(Component.literal("Nest: " + Math.round(n.stored()) + " biomass stored" + (rank(p, NETWORK) > 0 && nests(p).size() > 1 ? "  (sneak + right-click to travel)" : "")).withStyle(ChatFormatting.RED), true); return; }
        n.setStored(n.stored() - take);
        r.putFloat("bio", r.getFloat("bio") + take);
        tendril(p.serverLevel(), n.position().add(0, 0.6, 0), mid(p), 8);
        sound(p.serverLevel(), n.position(), SoundEvents.GENERIC_DRINK, 1.0f, 0.6f);
        p.displayClientMessage(Component.literal("+" + Math.round(take) + " biomass from your nest (" + Math.round(n.stored()) + " left)").withStyle(ChatFormatting.RED), true);
        dirty(p);
    }

    /** The travel menu: every nest (where, its biomass if loaded, how long till it can be reached). */
    static void sendNests(ServerPlayer p) {
        net.minecraft.nbt.ListTag list = nests(p);
        long now = p.serverLevel().getGameTime();
        HiveNest here = nestOf(p);
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            String dim = e.getString("dim"); dim = dim.substring(dim.indexOf(':') + 1).replace('_', ' ');
            boolean isHere = here != null && e.getUUID("id").equals(here.getUUID());
            long cd = Math.max(0, (e.getLong("cd") - now) / 20);
            rows.add(dim + "|" + (int) e.getDouble("x") + "|" + (int) e.getDouble("y") + "|" + (int) e.getDouble("z") + "|" + cd + "|" + (isHere ? 1 : 0));
        }
        AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ParasitePackets.Nests(rows));
    }

    /** Once a day, death brings you back at your nest. */
    @SubscribeEvent
    public static void onRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent e) {
        if (e.isEndConquered() || !(e.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag r = root(p);
        net.minecraft.nbt.ListTag list = nests(p);
        if (list.isEmpty()) return;
        long now = p.serverLevel().getGameTime();
        if (now < r.getLong("nestRespawn")) return;
        String died = r.getString("diedDim");
        CompoundTag pick = list.getCompound(0);
        double best = Double.MAX_VALUE;
        for (net.minecraft.nbt.Tag t : list) {
            CompoundTag c = (CompoundTag) t;
            if (!c.getString("dim").equals(died)) continue;
            double d = Math.pow(c.getDouble("x") - r.getDouble("diedX"), 2) + Math.pow(c.getDouble("z") - r.getDouble("diedZ"), 2);
            if (d < best) { best = d; pick = c; }
        }
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new net.minecraft.resources.ResourceLocation(pick.getString("dim")));
        ServerLevel there = p.server.getLevel(key);
        if (there == null) return;
        r.putLong("nestRespawn", now + 24000);
        p.teleportTo(there, pick.getDouble("x"), pick.getDouble("y") + 0.2, pick.getDouble("z"), p.getYRot(), p.getXRot());
        p.displayClientMessage(Component.literal("You re-form at your Hive Nest").withStyle(ChatFormatting.RED), true);
    }

    /** Where you died (for the nearest nest). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDiedWhere(LivingDeathEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag r = root(p);
        r.putString("diedDim", p.serverLevel().dimension().location().toString());
        r.putDouble("diedX", p.getX()); r.putDouble("diedZ", p.getZ());
    }

}
