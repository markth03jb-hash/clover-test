package com.example.arcaneaddon.server;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.network.AddonNetwork;
import com.example.arcaneaddon.network.FxPacket;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Surpassing Limits: when you are nearly dead in a hard fight, your grimoire can answer. It heals you to half health,
 * plays that grimoire's own awakening (pose + effect), and for one minute its ultimate spell becomes an evolved
 * version (see LimitFx). A white-hot aura in the grimoire's colour burns around you the whole time.
 *
 *  Trigger:  under 25% health (or a hit that would kill you) in a hard fight - a boss or strong monster within 32
 *            blocks, or another player hitting you. 35% chance (50% with a Mastery 5 book), then 20 minutes cooldown.
 *  Command:  /arcane limit [player] - trigger it now; /arcane limit reset [player] - clear the cooldown.
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public final class LimitBreak {
    private LimitBreak() {}

    public static final int DURATION = 60 * 20;
    private static final int COOLDOWN = 20 * 60 * 20;
    private static final int GRACE = 30;   // untouchable while the awakening plays
    private static final String LAST_TAG = "arcane_limit_last";

    private static final Map<UUID, long[]> ACTIVE = new HashMap<>();   // {end, book index, grace end}

    public static boolean active(ServerPlayer p) {
        long[] a = ACTIVE.get(p.getUUID());
        return a != null && p.serverLevel().getGameTime() <= a[0];
    }

    public static int ticksLeft(ServerPlayer p) {
        long[] a = ACTIVE.get(p.getUUID());
        return a == null ? 0 : (int) Math.max(0, a[0] - p.serverLevel().getGameTime());
    }

    /** Index of the equipped grimoire in GrimoireMastery.BOOKS, or -1. */
    private static int bookIndex(ServerPlayer p) {
        ItemStack s = Utils.getPlayerSpellbookStack(p);
        GrimoireMastery.Book b = s == null ? null : GrimoireMastery.bookOf(s);
        return b == null ? -1 : GrimoireMastery.BOOKS.indexOf(b);
    }

    /** Surpass your limits now. Returns false if no grimoire is equipped (or it is already active). */
    public static boolean trigger(ServerPlayer p) {
        int idx = bookIndex(p);
        if (idx < 0 || active(p)) return false;
        long now = p.serverLevel().getGameTime();
        ACTIVE.put(p.getUUID(), new long[]{now + DURATION, idx, now + GRACE});
        p.getPersistentData().putLong(LAST_TAG, now);
        p.setHealth(Math.max(p.getHealth(), p.getMaxHealth() * 0.5f));   // back up to half health
        ModeHud.show(p, "surpassing_limits", DURATION);
        sendAura(p, idx, DURATION);
        AnimSend.play(p, AnimSend.LIMIT_FIRST + idx);   // the grimoire's own awakening pose
        LimitFx.awaken(p, idx);
        GrimoireMastery.Book b = GrimoireMastery.BOOKS.get(idx);
        String ult = null;
        for (Map.Entry<String, Integer> e : b.unlocks().entrySet()) if (e.getValue() == GrimoireMastery.MAX_LEVEL) ult = e.getKey();
        Component msg = Component.translatable("ui.arcane_addon.limit.start").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        p.displayClientMessage(ult == null ? msg : msg.copy().append(Component.literal(" — ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("ui.arcane_addon.limit.evolved", Component.translatable("spell.arcane_addon." + ult)).withStyle(ChatFormatting.YELLOW)), true);
        return true;
    }

    private static void end(ServerPlayer p) {
        ACTIVE.remove(p.getUUID());
        ModeHud.hide(p, "surpassing_limits");
        sendAura(p, 0, 0);
        p.displayClientMessage(Component.translatable("ui.arcane_addon.limit.end").withStyle(ChatFormatting.GRAY), true);
    }

    /** FxPacket shape 35: the limit aura. id = player, palette = book index, life = ticks (0 = off). */
    private static void sendAura(ServerPlayer p, int idx, int life) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 35; pkt.id = p.getId(); pkt.palette = (byte) idx; pkt.life = life;
        AddonNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), pkt);
    }

    // ------------------------------------------------------------------ the natural trigger
    private static boolean hardFight(ServerPlayer p, LivingEntity attacker) {
        if (attacker instanceof Player && attacker != p) return true;
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(32), x -> x != p && x.isAlive())) {
            if (e.getType().is(Tags.EntityTypes.BOSSES) || e instanceof Warden) return true;
            if (e instanceof Enemy && e.getMaxHealth() >= 60) return true;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDamage(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || active(p) || bookIndex(p) < 0) return;
        float after = p.getHealth() - e.getAmount();
        if (after > p.getMaxHealth() * 0.25f) return;
        long now = p.serverLevel().getGameTime();
        long last = p.getPersistentData().contains(LAST_TAG) ? p.getPersistentData().getLong(LAST_TAG) : -COOLDOWN;
        if (now - last < COOLDOWN && now >= last) return;
        LivingEntity attacker = e.getSource().getEntity() instanceof LivingEntity le ? le : null;
        if (!hardFight(p, attacker)) return;
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        float chance = book != null && GrimoireMastery.level(book) >= GrimoireMastery.MAX_LEVEL ? 0.5f : 0.35f;
        if (ThreadLocalRandom.current().nextFloat() >= chance) {
            p.getPersistentData().putLong(LAST_TAG, now - COOLDOWN + 20 * 30);   // missed: you can't roll again for 30 s
            return;
        }
        if (after <= 0) e.setAmount(Math.max(0f, p.getHealth() - 1f));   // the grimoire refuses to let you fall
        AstaFx.later(1, () -> { if (p.isAlive()) trigger(p); });
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        long[] a = ACTIVE.get(p.getUUID());
        if (a != null && p.serverLevel().getGameTime() <= a[2]) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        long[] a = ACTIVE.get(p.getUUID());
        if (a == null) return;
        long now = p.serverLevel().getGameTime();
        if (now > a[0]) { end(p); return; }
        if (now % 40 == 0) sendAura(p, (int) a[1], (int) (a[0] - now));   // for players who arrive later
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && ACTIVE.containsKey(p.getUUID())) end(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { ACTIVE.remove(e.getEntity().getUUID()); }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) { ACTIVE.clear(); }

    // ------------------------------------------------------------------ /arcane limit
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("arcane").requires(s -> s.hasPermission(2))
                .then(Commands.literal("limit")
                        .executes(c -> fire(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> fire(c.getSource(), EntityArgument.getPlayer(c, "player"))))
                        .then(Commands.literal("reset")
                                .executes(c -> reset(c.getSource(), c.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> reset(c.getSource(), EntityArgument.getPlayer(c, "player")))))));
    }

    private static int fire(net.minecraft.commands.CommandSourceStack src, ServerPlayer p) {
        if (active(p)) end(p);   // re-trigger: start it fresh
        if (!trigger(p)) {
            src.sendFailure(Component.translatable("ui.arcane_addon.mastery.no_book"));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("ui.arcane_addon.limit.cmd", p.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static int reset(net.minecraft.commands.CommandSourceStack src, ServerPlayer p) {
        p.getPersistentData().remove(LAST_TAG);
        src.sendSuccess(() -> Component.translatable("ui.arcane_addon.limit.reset", p.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
        return 1;
    }
}
