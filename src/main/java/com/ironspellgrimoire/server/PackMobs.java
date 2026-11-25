package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Packs. Now and then a hostile mob appears as a pack leader (a gold ring at its feet, "Pack Leader" over its head)
 * with three of its own kind beside it - four once the Beast of Despair has fallen on the server. While the leader
 * lives, its pack within 16 blocks hits 20% harder and moves 15% faster; when it dies, the pack scatters and runs
 * for 6 seconds. From threat rank 1, or anywhere once the Revenant has fallen. Attributes only - no status effects.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class PackMobs {
    private PackMobs() {}

    public static final String LEADER = "isg_pack_leader", MEMBER = "isg_pack_of", SCATTER = "isg_pack_scatter";
    static final UUID PACK_DMG = UUID.fromString("e1173000-0000-4000-8000-000000000031"), PACK_SPEED = UUID.fromString("e1173000-0000-4000-8000-000000000032");
    static final Map<Mob, Boolean> LEADERS = new WeakHashMap<>();
    static final Map<Mob, Long> SCATTERING = new WeakHashMap<>();

    static boolean mayLead(Mob m, int rank) {
        if (!(m instanceof Enemy) || m.getPersistentData().contains(MEMBER)) return false;
        return rank >= 1 || WorldGrowth.fallen(m.getServer(), 4);
    }
    public static boolean leader(Entity m) { return m.getPersistentData().getBoolean(LEADER); }

    /** Make m a pack leader and raise its pack round it. */
    static void lead(Mob m, int rank) {
        if (!(m.level() instanceof ServerLevel sl)) return;
        m.getPersistentData().putBoolean(LEADER, true);
        m.setPersistenceRequired();
        m.setCustomName(Component.literal("Pack Leader").withStyle(ChatFormatting.GOLD));
        LEADERS.put(m, true);
        int n = WorldGrowth.fallen(sl.getServer(), 1) ? 4 : 3;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            Entity e = m.getType().create(sl);
            if (!(e instanceof Mob k)) continue;
            double a = r.nextDouble(Math.PI * 2);
            int x = (int) Math.floor(m.getX() + Math.cos(a) * 2.5), z = (int) Math.floor(m.getZ() + Math.sin(a) * 2.5);
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - m.getY()) > 4) y = (int) m.getY();
            k.moveTo(x + 0.5, y, z + 0.5, r.nextFloat() * 360f, 0);
            k.getPersistentData().putUUID(MEMBER, m.getUUID());
            k.getPersistentData().put(EliteMobs.TAG, new CompoundTag());   // marked, so it doesn't roll as an elite (or a leader) of its own
            EliteMobs.make(k, EliteMobs.rank(m), 0);
            k.finalizeSpawn(sl, sl.getCurrentDifficultyAt(BlockPos.containing(k.position())), MobSpawnType.REINFORCEMENT, null, null);
            sl.addFreshEntity(k);
        }
        FxSend.ring(sl, Fx.GOLD, m.position().add(0, 0.05, 0), 0.3f, 3f, 0.06f, 14);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && e.getEntity() instanceof Mob m && leader(m)) LEADERS.put(m, true);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 10 != 0) return;
        for (Mob l : new ArrayList<>(LEADERS.keySet())) {
            if (l == null) continue;
            if (!l.isAlive() || l.isRemoved()) { LEADERS.remove(l); continue; }
            if (!(l.level() instanceof ServerLevel sl)) continue;
            for (Mob k : pack(sl, l, 16)) {
                TimedModifiers.apply(k, Attributes.ATTACK_DAMAGE, PACK_DMG, "Pack", 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, 15);
                TimedModifiers.apply(k, Attributes.MOVEMENT_SPEED, PACK_SPEED, "Pack", 0.15, AttributeModifier.Operation.MULTIPLY_TOTAL, 15);
            }
            if (e.getServer().getTickCount() % 40 == 0 && sl.getNearestPlayer(l, 32) != null) FxSend.ring(sl, Fx.GOLD, l.position().add(0, 0.05, 0), 0.7f, 0.85f, 0.04f, 30);
        }
        long now = e.getServer().overworld().getGameTime();
        for (var en : new ArrayList<>(SCATTERING.entrySet())) {
            Mob k = en.getKey();
            if (k == null || !k.isAlive() || now > en.getValue()) { if (k != null) SCATTERING.remove(k); continue; }
            ServerPlayer p = k.level() instanceof ServerLevel sl ? (ServerPlayer) sl.getNearestPlayer(k, 24) : null;
            k.setTarget(null);
            if (p != null) {
                Vec3 away = k.position().subtract(p.position());
                away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
                Vec3 to = k.position().add(away.scale(8));
                k.getNavigation().moveTo(to.x, to.y, to.z, 1.3);
            }
        }
    }

    static List<Mob> pack(ServerLevel sl, Mob l, double r) {
        UUID id = l.getUUID();
        return sl.getEntitiesOfClass(Mob.class, l.getBoundingBox().inflate(r), k -> k != l && k.isAlive() && k.getPersistentData().hasUUID(MEMBER) && k.getPersistentData().getUUID(MEMBER).equals(id));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof Mob l) || !leader(l) || !(l.level() instanceof ServerLevel sl)) return;
        LEADERS.remove(l);
        long until = sl.getGameTime() + 120;
        for (Mob k : pack(sl, l, 24)) {
            SCATTERING.put(k, until);
            k.getPersistentData().putLong(SCATTER, until);
            TimedModifiers.remove(k, Attributes.ATTACK_DAMAGE, PACK_DMG);
            TimedModifiers.remove(k, Attributes.MOVEMENT_SPEED, PACK_SPEED);
        }
        FxSend.ring(sl, Fx.GOLD, l.position().add(0, 0.1, 0), 0.4f, 5f, 0.08f, 12);
        if (e.getSource().getEntity() instanceof ServerPlayer p) p.displayClientMessage(Component.literal("The pack leader falls - its pack scatters!").withStyle(ChatFormatting.GOLD), true);
    }

    /** A scattering mob won't pick a target. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent e) {
        if (e.getEntity() instanceof Mob k && e.getNewTarget() != null && SCATTERING.containsKey(k)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) { LEADERS.clear(); SCATTERING.clear(); }

    public static boolean testScattering(Mob k) { return SCATTERING.containsKey(k); }
    public static void testLead(Mob m, int rank) { EliteMobs.make(m, rank, 0); lead(m, rank); }
    public static List<Mob> testPack(Mob l) { return l.level() instanceof ServerLevel sl ? pack(sl, l, 16) : List.of(); }
}
