package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.AddonBoss;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonEntities;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Boss intro cards. The first time you ever meet each boss, its title card plays (client/BossIntroCard): your view
 * turns to it, letterbox bars close in, and its name and epithet slam across the screen while it stands and roars -
 * three seconds in which neither of you can be hurt. Every later meeting gets the quick card (one second, nothing
 * stops). Who you have met is kept on you ("isg_met_bosses").
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class BossIntro {
    private BossIntro() {}

    public static final int SHAPE = 73, FULL = 60, QUICK = 26;
    private static final String MET = "isg_met_bosses";
    /** Which boss is which on the card: the client has the epithets and colours by this number. */
    private static List<EntityType<?>> types() {
        return List.of(AddonEntities.ZENON.get(), AddonEntities.BEAST_OF_DESPAIR.get(), AddonEntities.RHYA.get(), AddonEntities.LICHT.get(),
                AddonEntities.REVENANT.get(), AddonEntities.CURSE_WITCH.get(), AddonEntities.REFLECTION.get(), AddonEntities.MEMORY_GUARDIAN.get(), AddonEntities.CINDER_SOVEREIGN.get());
    }

    /** Boss encounters already carded, per player (forgotten when the boss is). */
    private static final Map<UUID, Set<UUID>> SHOWN = new HashMap<>();
    /** Players held in a full intro: until when. */
    private static final Map<UUID, Long> HELD = new HashMap<>();

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 3 || p.isSpectator()) return;
        ServerLevel sl = p.serverLevel();
        List<EntityType<?>> types = types();
        for (LivingEntity b : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(22), x -> x.isAlive() && types.contains(x.getType()))) {
            if (b.isDeadOrDying() || !p.hasLineOfSight(b)) continue;
            Set<UUID> shown = SHOWN.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
            if (!shown.add(b.getUUID())) continue;
            if (shown.size() > 64) { shown.clear(); shown.add(b.getUUID()); }
            int idx = types.indexOf(b.getType());
            if (b instanceof com.ironspellgrimoire.entity.MemoryGuardian mg) idx += mg.chapter();   // each memory has its own card
            if (b instanceof com.ironspellgrimoire.entity.CinderSovereign) idx = 10;
            CompoundTag d = p.getPersistentData();
            int met = d.getInt(MET);
            boolean first = (met & (1 << idx)) == 0;
            d.putInt(MET, met | (1 << idx));
            show(p, b, idx, first);
            break;
        }
    }

    private static void show(ServerPlayer p, LivingEntity b, int idx, boolean full) {
        ServerLevel sl = p.serverLevel();
        FxPacket k = new FxPacket();
        k.shape = SHAPE; k.id = b.getId(); k.sweep = idx; k.life = full ? FULL : QUICK; k.h = full ? 1 : 0;
        k.a0 = Bounties.unbound(b) ? 1 : 0;
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), k);
        if (!full) return;
        HELD.put(p.getUUID(), sl.getGameTime() + FULL);
        if (b instanceof AddonBoss ab) { ab.busy = Math.max(ab.busy, FULL); ab.setPose(BossKit.P_ROAR); ab.getNavigation().stop(); }
        else if (b instanceof Mob m) { m.getNavigation().stop(); TimeStop.freeze(p, m, FULL - 10, false, null); }
        sl.playSound(null, b.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 0.5f, 1.3f);
        sl.playSound(null, p.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 1.0f, 0.5f);
    }

    /** Neither of you is hurt while the card plays. */
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Long until = HELD.get(p.getUUID());
        if (until == null) return;
        if (p.serverLevel().getGameTime() > until) { HELD.remove(p.getUUID()); return; }
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { SHOWN.remove(e.getEntity().getUUID()); HELD.remove(e.getEntity().getUUID()); }
}
