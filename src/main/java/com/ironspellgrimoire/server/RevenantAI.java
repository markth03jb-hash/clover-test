package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.BossKit.*;
import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.Revenant;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Revenant' fight - spatial magic and the dead.
 * <ul>
 *   <li>Portals open round the arena and the dead walk out of them (four at most). While any portal is open, space folds round him
 *       and he takes half the damage. Close a portal by standing in its ring for two seconds.</li>
 *   <li>His dead don't stay down: one killed by anything but magic gets back up once (a spell finishes it for good).</li>
 *   <li>His own moves: cutting space along a line, dropping the dead on you through a portal overhead, and stepping
 *       away through space when you get close.</li>
 *   <li>At half health he tears open more portals and raises a Revenant Knight.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class RevenantAI {
    private RevenantAI() {}

    private static final String WHO = "The Revenant";
    private static final ChatFormatting COL = ChatFormatting.DARK_GREEN;
    private static final String TAG = "isg_revenant", REVIVED = "isg_revenant_up";

    private static final class Portal { Vec3 at; boolean open = true; int sealing; Portal(Vec3 at) { this.at = at; } }
    private static final Map<UUID, List<Portal>> PORTALS = new HashMap<>();

    private static List<Portal> portals(Revenant b) {
        return PORTALS.computeIfAbsent(b.getUUID(), k -> {
            List<Portal> l = new ArrayList<>();
            open(b, l, 3);
            return l;
        });
    }

    private static void open(Revenant b, List<Portal> l, int n) {
        ServerLevel sl = (ServerLevel) b.level();
        Vec3 c = b.home != null ? b.home : b.position();
        double a0 = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        for (int i = 0; i < n; i++) {
            double a = a0 + i * Math.PI * 2 / n;
            Vec3 at = ground(sl, c.add(Math.cos(a) * 9, 1, Math.sin(a) * 9));
            l.add(new Portal(at));
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.HOSTILE, 1.4f, 0.6f);
        }
    }

    private static int openCount(Revenant b) {
        int n = 0;
        for (Portal p : portals(b)) if (p.open) n++;
        return n;
    }

    public static void tick(Revenant b) {
        ServerLevel sl = (ServerLevel) b.level();
        long now = sl.getGameTime();
        List<Portal> ps = portals(b);
        Player t = target(b);
        // the portals: drawn, spilling the dead, and closed by standing in them
        int alive = sl.getEntitiesOfClass(Mob.class, b.getBoundingBox().inflate(40), m -> m.getPersistentData().getBoolean(TAG) && m.isAlive()).size();
        for (Portal p : ps) {
            if (!p.open) continue;
            if (now % 40 == 0) {
                SpaceFx.gate(sl, p.at.add(0, 1.4, 0), new Vec3(0, 0, 1), 1.1f, 1.6f, 44, 0);
                BossFx.ring(sl, Fx.VOID, p.at.add(0, 0.06, 0), 1.4f, 1.5f, 0.05f, 42);
            }
            if (t != null && now % 180 == 0 && alive < 4) { dead(sl, p.at, false); alive++; }
            boolean someone = false;
            for (Player pl : near(b, p.at.add(0, 0.9, 0), 1.6)) {
                someone = true;
                if (++p.sealing % 20 == 0) pl.displayClientMessage(Component.literal("Sealing the portal... " + p.sealing / 20 + "/2").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
            if (!someone) p.sealing = Math.max(0, p.sealing - 2);
            if (p.sealing >= 40) {
                p.open = false;
                BossFx.ring(sl, Fx.VOID, p.at.add(0, 1, 0), 1.8f, 0.1f, 0.1f, 10);
                sl.playSound(null, p.at.x, p.at.y, p.at.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 0.6f, 1.8f);
                for (Player pl : near(b, b.position(), 40)) pl.displayClientMessage(Component.literal("Portal sealed - " + openCount(b) + " left").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
            }
        }
        b.setFlag(openCount(b));
        if (b.flag() > 0 && now % 10 == 0) BossFx.ring(sl, Fx.VOID, b.position().add(0, 1.0, 0), 1.0f, 1.1f, 0.05f, 10);   // folded space round him
        if (t == null) { b.getNavigation().stop(); return; }
        if (b.getPersistentData().getBoolean("isg_met") != true) {
            b.getPersistentData().putBoolean("isg_met", true);
            say(b, WHO, COL, "Welcome, welcome. My friends here have been dying to meet you.");
        }
        b.getLookControl().setLookAt(t, 30, 30);
        if (b.busy > 0) { b.busy--; return; }
        double dist = b.distanceTo(t);
        keep(b, t, 8);
        int took;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (dist < 3.5) took = stepAway(b, t);
        else took = switch (r.nextInt(3)) {
            case 0 -> spaceCut(b, t);
            case 1 -> dropDead(b, t);
            default -> spaceCut(b, t);
        };
        b.busy = took + (b.phase() == 1 ? 34 : 24);
    }

    private static Mob dead(ServerLevel sl, Vec3 at, boolean knight) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        EntityType<? extends Mob> type = knight ? EntityType.ZOMBIE : r.nextInt(3) == 0 ? EntityType.HUSK : EntityType.ZOMBIE;
        Mob m = type.create(sl);
        if (m == null) return null;
        m.moveTo(at.x + r.nextDouble(-0.5, 0.5), at.y, at.z + r.nextDouble(-0.5, 0.5), r.nextFloat() * 360, 0);
        m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.EVENT, null, null);
        m.getPersistentData().putBoolean(TAG, true);
        m.setPersistenceRequired();
        if (knight) {
            m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            m.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            for (EquipmentSlot s : EquipmentSlot.values()) m.setDropChance(s, 0f);
            var hp = m.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.setBaseValue(70);
            m.setHealth(70);
            m.setCustomName(Component.literal("Revenant Knight").withStyle(ChatFormatting.DARK_GREEN));
        }
        sl.addFreshEntity(m);
        BossFx.orb(sl, Fx.VOID, at.add(0, 1, 0), Vec3.ZERO, 0.6f, 8);
        return m;
    }

    /** His dead get back up once - unless magic finished them. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity m = e.getEntity();
        if (m.level().isClientSide || !m.getPersistentData().getBoolean(TAG) || m.getPersistentData().getBoolean(REVIVED)) return;
        if (e.getSource() instanceof SpellDamageSource) return;
        ServerLevel sl = (ServerLevel) m.level();
        Vec3 at = m.position();
        boolean knight = m.hasCustomName();
        EntityType<?> type = m.getType();
        AstaFx.later(50, () -> {
            if (!(type.create(sl) instanceof Mob up)) return;
            up.moveTo(at.x, at.y, at.z, m.getYRot(), 0);
            up.finalizeSpawn(sl, sl.getCurrentDifficultyAt(up.blockPosition()), MobSpawnType.EVENT, null, null);
            up.getPersistentData().putBoolean(TAG, true);
            up.getPersistentData().putBoolean(REVIVED, true);
            up.setPersistenceRequired();
            if (knight) up.setCustomName(Component.literal("Revenant Knight").withStyle(ChatFormatting.DARK_GREEN));
            up.setHealth(up.getMaxHealth() * 0.5f);
            sl.addFreshEntity(up);
            BossFx.orb(sl, Fx.VOID, at.add(0, 0.6, 0), new Vec3(0, 0.05, 0), 0.5f, 10);
            sl.playSound(null, up.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.HOSTILE, 0.8f, 0.6f);
        });
    }

    static int spaceCut(Revenant b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_SLASH);
        b.getNavigation().stop();
        int T = 16;
        charge(b, Fx.VOID, T);
        Vec3 d = dirTo(b, t);
        face(b, d);
        Vec3 a = b.position().add(0, 1, 0), c = a.add(d.scale(18));
        warnLine(sl, a.add(0, -0.9, 0), c.add(0, -0.9, 0), 1.6f, T);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            RiftFx.cut(sl, a.add(c).scale(0.5), d, new Vec3(0, 1, 0), 9f, 12);
            for (Player p : along(b, a, c, 1.5)) hurt(b, p, 9f);
            sl.playSound(null, b.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.2f, 0.6f);
        });
        return T + 6;
    }

    /** A portal opens over your head and the dead fall out of it. */
    static int dropDead(Revenant b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        int T = 18;
        charge(b, Fx.VOID, T);
        Vec3 g = ground(sl, t.position());
        warnCircle(sl, g, 2f, T);
        SpaceFx.gate(sl, g.add(0, 5, 0), new Vec3(0, -1, 0), 1.2f, 1.2f, T + 10, 0);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            for (Player p : near(b, g.add(0, 1, 0), 2)) hurt(b, p, 6f);
            for (int i = 0; i < (b.phase() == 1 ? 1 : 2); i++) { Mob m = dead(sl, g.add(0, 3, 0), false); if (m != null) m.setDeltaMovement(0, -0.6, 0); }
            sl.playSound(null, g.x, g.y, g.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1f, 0.6f);
        });
        return T + 6;
    }

    static int stepAway(Revenant b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        Vec3 away = flat(b.position().subtract(t.position()));
        if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
        Vec3 dest = SpaceFx.safe(sl, b.position().add(away.normalize().scale(8)), b);
        if (dest == null) return 6;
        SpaceFx.gate(sl, b.position().add(0, 1, 0), away, 0.8f, 1.2f, 10, 0);
        SpaceFx.move(b, dest, b.getYRot());
        SpaceFx.gate(sl, dest.add(0, 1, 0), away, 0.8f, 1.2f, 10, 0);
        sl.playSound(null, b.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1f, 0.8f);
        return 8;
    }

    public static void onHurt(Revenant b, DamageSource src, float amount) {
        if (src.getEntity() instanceof Player p && valid(b, p)) b.focus = p.getUUID();
    }

    /** While a portal is open, space folds round him. */
    public static float taken(Revenant b, DamageSource src) { return openCount(b) > 0 ? 0.5f : 1f; }

    public static void phase(Revenant b, int phase) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setTransforming(40);
        b.setPose(P_ROAR);
        say(b, WHO, COL, "You think you can close them all? I'll just open more!");
        open(b, portals(b), 2);
        AstaFx.later(30, () -> {
            Mob k = dead(sl, b.position().add(2, 0, 0), true);
            FxSend.impactFrame(sl, b.position().add(0, 1.5, 0), 3, FxSend.FRAME_FINRAL, 24);
            sl.playSound(null, b.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE, 1.4f, 0.5f);
        });
    }

    public static void dying(Revenant b, int t) {
        ServerLevel sl = (ServerLevel) b.level();
        if (t == 1) {
            say(b, WHO, COL, "No... not like this... not before I get my revenge...");
            for (Mob m : sl.getEntitiesOfClass(Mob.class, b.getBoundingBox().inflate(48), m -> m.getPersistentData().getBoolean(TAG))) {   // his dead fall still
                BossFx.orb(sl, Fx.VOID, m.position().add(0, 1, 0), new Vec3(0, 0.05, 0), 0.4f, 10);
                m.discard();
            }
            List<Portal> l = PORTALS.remove(b.getUUID());
            if (l != null) for (Portal p : l) if (p.open) BossFx.ring(sl, Fx.VOID, p.at.add(0, 1, 0), 1.8f, 0.1f, 0.1f, 10);
        }
        if (t < 60 && t % 6 == 0) BossFx.orb(sl, Fx.VOID, b.position().add(0, 1 + ThreadLocalRandom.current().nextDouble(), 0), new Vec3(0, 0.05, 0), 0.3f, 12);
    }
}
