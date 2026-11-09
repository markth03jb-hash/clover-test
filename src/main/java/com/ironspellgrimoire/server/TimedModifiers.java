package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Temporary buffs and debuffs WITHOUT vanilla status effects.
 *
 * Each one is a transient attribute modifier with a fixed UUID: transient modifiers are never saved to disk,
 * so nothing can get stuck on a player after a crash or logout, and no other mod's potion/effect logic can
 * override, extend or clear them. Re-applying the same id refreshes it instead of stacking.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class TimedModifiers {
    private TimedModifiers() {}

    // fixed ids, one per kind of buff/debuff
    public static final UUID UNION_DAMAGE = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a100001");
    public static final UUID UNION_SPEED = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a100002");
    public static final UUID UNION_ATTACK_SPEED = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a100003");
    public static final UUID UNION_KNOCKBACK = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a100004");
    public static final UUID MOMENTUM_SPEED = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a100005");
    public static final UUID CHILL_SLOW = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a100006");
    public static final UUID STAGGER_SLOW = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a100007");
    public static final UUID STAGGER_WEAK = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a100008");
    public static final UUID HOPE_POWER = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a100009");
    public static final UUID ZONE_POWER = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a10000a");
    public static final UUID ZONE_REGEN = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a10000b");
    public static final UUID SPIRAL_STAGGER = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a10000c");
    public static final UUID PRISON_ROOT = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a10000d");
    public static final UUID KNIGHT_ARMOR = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a10000e");
    public static final UUID KNIGHT_TOUGHNESS = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a10000f");
    public static final UUID KNIGHT_FOOTING = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a100010");
    public static final UUID KNIGHT_WEIGHT = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a100011");
    public static final UUID CART_RIDE = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a100012");
    public static final UUID HORIZON_CHARGE = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a100013");

    private record Entry(ServerLevel level, UUID entity, Attribute attribute, UUID id, long until, boolean negative) {}
    private static final List<Entry> ACTIVE = new ArrayList<>();
    private static long ticks = 0;

    /** Applies (or refreshes) a temporary modifier on {@code e} for {@code duration} ticks. Does nothing if the entity lacks the attribute. */
    public static void apply(LivingEntity e, Attribute attribute, UUID id, String name, double amount, AttributeModifier.Operation op, int duration) {
        if (!(e.level() instanceof ServerLevel sl)) return;
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, name, amount, op));
        ACTIVE.removeIf(x -> x.entity().equals(e.getUUID()) && x.id().equals(id));
        ACTIVE.add(new Entry(sl, e.getUUID(), attribute, id, ticks + duration, amount < 0));
    }

    /** Removes one modifier early. */
    public static void remove(LivingEntity e, Attribute attribute, UUID id) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst != null) inst.removeModifier(id);
        ACTIVE.removeIf(x -> x.entity().equals(e.getUUID()) && x.id().equals(id));
    }

    /** Clears every debuff (negative modifier) this addon put on {@code e}. Used by Anti Magic's self-cleanse. */
    public static void clearDebuffs(LivingEntity e) {
        Iterator<Entry> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Entry x = it.next();
            if (!x.negative() || !x.entity().equals(e.getUUID())) continue;
            AttributeInstance inst = e.getAttribute(x.attribute());
            if (inst != null) inst.removeModifier(x.id());
            it.remove();
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        ticks++;
        if (ACTIVE.isEmpty()) return;
        Iterator<Entry> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Entry x = it.next();
            if (x.until() > ticks) continue;
            Entity ent = x.level().getServer().getPlayerList().getPlayer(x.entity());   // players may have changed dimension
            if (ent == null) {
                for (ServerLevel l : x.level().getServer().getAllLevels()) {
                    ent = l.getEntity(x.entity());
                    if (ent != null) break;
                }
            }
            if (ent instanceof LivingEntity le) {
                AttributeInstance inst = le.getAttribute(x.attribute());
                if (inst != null) inst.removeModifier(x.id());
            }
            // if the entity is unloaded the transient modifier is already gone with it
            it.remove();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) { ACTIVE.clear(); }
}
