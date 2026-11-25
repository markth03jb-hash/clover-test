package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.FallingStar;
import com.ironspellgrimoire.registry.AddonEntities;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Falling stars. Every five minutes there's a one-in-four chance (at night, one-in-two) that a star falls 150-350
 * blocks from a mage in the overworld; everyone is told roughly where. It lands without touching a block, as a Star
 * Core (entity/FallingStar), and elites gather round it - three, and one more for each rank of the land's danger
 * (EliteMobs.dangerAt). With the guardians down, anyone can take the core (use it, item/StarCoreItem: a tier II or III
 * Mana Core and merit stars). An untaken star fades after 20 minutes. At most two at a time. {@code /star} for ops.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class FallingStars {
    private FallingStars() {}

    public static final int LIFE = 24000, FALL = 60;
    public static int testLanded;

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 6000 != 3000 || !EliteMobs.AUTO) return;
        ServerLevel ow = e.getServer().overworld();
        List<ServerPlayer> ps = ow.players().stream().filter(p -> !p.isSpectator()).toList();
        if (ps.isEmpty()) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextFloat() > (ow.isNight() ? 0.5f : 0.25f)) return;
        if (ow.getEntities(AddonEntities.FALLING_STAR.get(), x -> true).size() >= 2) return;
        ServerPlayer p = ps.get(r.nextInt(ps.size()));
        double a = r.nextDouble(Math.PI * 2), d = 150 + r.nextDouble(200);
        callDown(ow, new Vec3(p.getX() + Math.cos(a) * d, 0, p.getZ() + Math.sin(a) * d), true);
    }

    /** A star comes down at (x, z): from 60 blocks above the ground there. */
    public static FallingStar callDown(ServerLevel sl, Vec3 at, boolean announce) {
        int x = (int) Math.floor(at.x), z = (int) Math.floor(at.z);
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        FallingStar s = AddonEntities.FALLING_STAR.get().create(sl);
        if (s == null) return null;
        s.groundY = y;
        s.moveTo(x + 0.5, y + FALL, z + 0.5, 0, 0);
        sl.addFreshEntity(s);
        if (announce) for (ServerPlayer p : sl.players()) {
            Vec3 to = s.position().subtract(p.position());
            double dist = Math.hypot(to.x, to.z);
            if (dist > 1500) continue;
            p.sendSystemMessage(Component.literal("A STAR FALLS  ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                    .append(Component.literal("about " + Math.round(dist / 10) * 10 + " blocks to the " + compass(to) + ". Its guardians will gather fast.").withStyle(ChatFormatting.GRAY)));
        }
        return s;
    }

    static String compass(Vec3 v) {
        double ang = Math.toDegrees(Math.atan2(v.x, -v.z));   // 0 = north
        String[] n = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return n[(int) Math.floorMod(Math.round(ang / 45), 8)];
    }

    public static void tick(FallingStar s) {
        if (!(s.level() instanceof ServerLevel sl)) return;
        if (!s.landed()) {
            double ny = Math.max(s.groundY, s.getY() - 1.2);
            s.setPos(s.getX(), ny, s.getZ());
            if (s.tickCount % 2 == 0) FxSend.beam(sl, Fx.SILVER, s.position(), s.position().add(0, 7, 0), 0.5f, 6);
            if (ny <= s.groundY) land(s, sl);
            return;
        }
        if (++s.life > LIFE) { FxSend.ring(sl, Fx.SILVER, s.position().add(0, 0.5, 0), 1.5f, 0.2f, 0.06f, 14); s.discard(); return; }
        if (s.tickCount % 20 == 0) {
            if (s.sealed()) {
                boolean any = false;
                for (var id : s.guardians) if (sl.getEntity(id) instanceof Mob g && g.isAlive()) any = true;
                if (!any) {
                    s.setSealed(false);
                    for (ServerPlayer p : sl.players()) if (p.distanceToSqr(s) < 48 * 48)
                        p.displayClientMessage(Component.literal("The star's guardians are down - take it.").withStyle(ChatFormatting.AQUA), true);
                }
            }
            FxSend.ring(sl, s.sealed() ? Fx.CRIMSON : Fx.SILVER, s.position().add(0, 0.05, 0), 1.6f, 1.8f, 0.04f, 18);
        }
    }

    static void land(FallingStar s, ServerLevel sl) {
        s.setLanded(true);
        testLanded++;
        Vec3 c = s.position();
        FxSend.ring(sl, Fx.SILVER, c.add(0, 0.1, 0), 0.5f, 9f, 0.2f, 16);
        FxSend.impactFrame(sl, c.add(0, 1, 0), 4, FxSend.FRAME_LIGHT, 64);
        FxSend.shake(sl, c, 0.9f, 16, 64);
        sl.playSound(null, BlockPos.containing(c), SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 3f, 0.6f);
        sl.playSound(null, BlockPos.containing(c), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 3f, 0.5f);
        int rank = Math.min(5, EliteMobs.dangerAt(sl, c) + 1);
        int n = Math.min(6, 3 + rank - 1);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        EntityType<?>[] kinds = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.HUSK, EntityType.STRAY};
        for (int i = 0; i < n; i++) {
            if (!(kinds[r.nextInt(kinds.length)].create(sl) instanceof Mob g)) continue;
            double a = i * Math.PI * 2 / n;
            int x = (int) Math.floor(c.x + Math.cos(a) * 4), z = (int) Math.floor(c.z + Math.sin(a) * 4);
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - c.y) > 5) y = (int) c.y;
            g.moveTo(x + 0.5, y, z + 0.5, r.nextFloat() * 360f, 0);
            g.getPersistentData().put(EliteMobs.TAG, new CompoundTag());
            g.finalizeSpawn(sl, sl.getCurrentDifficultyAt(BlockPos.containing(g.position())), MobSpawnType.EVENT, null, null);
            EliteMobs.make(g, rank, EliteMobs.pick(g, rank, 1 + r.nextInt(2)));
            g.setPersistenceRequired();
            sl.addFreshEntity(g);
            s.guardians.add(g.getUUID());
        }
        s.setSealed(!s.guardians.isEmpty());
    }

    public static void take(FallingStar s, Player p) {
        if (!s.landed()) return;
        if (s.sealed()) { p.displayClientMessage(Component.literal("Its guardians still stand.").withStyle(ChatFormatting.RED), true); return; }
        ItemStack core = new ItemStack(AddonItemRegistry.STAR_CORE.get());
        if (!p.getInventory().add(core)) p.drop(core, false);
        if (s.level() instanceof ServerLevel sl) {
            FxSend.ring(sl, Fx.SILVER, s.position().add(0, 0.6, 0), 2f, 0.2f, 0.08f, 14);
            sl.playSound(null, s.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.4f, 1.2f);
        }
        p.displayClientMessage(Component.literal("You take the Star Core.").withStyle(ChatFormatting.AQUA), true);
        s.discard();
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("star").requires(c -> c.hasPermission(2)).executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            callDown(p.serverLevel(), p.position().add(p.getLookAngle().scale(20)), true);
            return 1;
        }));
    }

}
