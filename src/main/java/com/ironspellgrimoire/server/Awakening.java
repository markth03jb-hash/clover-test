package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.network.GrimoireOpenPacket;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Grimoire Awakening. A grimoire that has passed the Magic Knight Exam and reached mastery 6 can awaken - once, in a
 * real fight: when a boss's blow would leave you under a fifth of your health, there is a chance the grimoire answers.
 * <ul>
 *   <li>Time stops round you (everything within 32 blocks is frozen) and you can't be hurt.</li>
 *   <li>The cutscene plays on your screen (client/AwakeningScene): the grimoire flies open, its pages turn by themselves
 *       and a new page writes itself - the awakened spell's name - then a flash, and time starts again.</li>
 *   <li>The grimoire gains a page: one more spell slot, holding its awakened spell (a spell of its own, cast like the
 *       others). Its mastery can go on to 8 (level 8: the awakened spell +30%), every spell you cast from it draws its
 *       sigil behind you, and its floating aura burns brighter.</li>
 *   <li>You come back at half health and stay untouchable for three seconds more.</li>
 * </ul>
 * {@code /grimoire awaken} plays it on demand (ops).
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class Awakening {
    private Awakening() {}

    public static final int SCENE = 100, WRITE_AT = 72, GRACE = 60;
    public static final int SHAPE = 70;
    private static final UUID HOLD = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a1001b1");
    private static final String MISS = "isg_awaken_miss";

    /** Each grimoire's awakened spell (the books still waiting for theirs are not in here yet). */
    private static final Map<Supplier<Item>, String> SPELLS = new LinkedHashMap<>();
    static {
        SPELLS.put(AddonItemRegistry.BLACK_GRIMOIRE::get, "absolute_zetten");
        SPELLS.put(AddonItemRegistry.DARK_GRIMOIRE::get, "moon_severing_iai");
        SPELLS.put(AddonItemRegistry.WIND_GRIMOIRE::get, "never_never_land");
        SPELLS.put(AddonItemRegistry.LIGHT_GRIMOIRE::get, "lamp_of_avior");
        SPELLS.put(AddonItemRegistry.TIME_GRIMOIRE::get, "chrono_anastasis");
    }

    /** The awakened spell's id (path) for this grimoire, or null when it has none yet. */
    public static String spellFor(ItemStack book) {
        if (book == null || book.isEmpty()) return null;
        for (Map.Entry<Supplier<Item>, String> e : SPELLS.entrySet()) if (book.is(e.getKey().get())) return e.getValue();
        return null;
    }

    public static boolean isAwakenedSpell(String id) {
        int i = id.indexOf(':');
        return SPELLS.containsValue(i < 0 ? id : id.substring(i + 1));
    }

    public static boolean awakened(ItemStack book) { return book != null && book.hasTag() && book.getTag().getBoolean("Awakened"); }

    public static boolean canAwaken(ItemStack book) {
        return book != null && GrimoireMastery.bookOf(book) != null && spellFor(book) != null && !awakened(book)
                && GrimoireMastery.ascended(book) && GrimoireMastery.level(book) >= 6;
    }

    /** Who is in the middle of awakening: ticks since it began. */
    private static final Map<UUID, Integer> ACTIVE = new HashMap<>();

    public static boolean inScene(ServerPlayer p) { Integer t = ACTIVE.get(p.getUUID()); return t != null && t < SCENE; }

    // ------------------------------------------------------------------ the trigger
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDamage(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || ACTIVE.containsKey(p.getUUID())) return;
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        if (!canAwaken(book)) return;
        if (p.getHealth() - e.getAmount() > p.getMaxHealth() * 0.2f) return;
        LivingEntity by = e.getSource().getEntity() instanceof LivingEntity le ? le : null;
        if (by == null || !(TimeStop.isBoss(by) || BossCores.isBoss(by.getType()))) return;
        long now = p.serverLevel().getGameTime();
        if (now - p.getPersistentData().getLong(MISS) < 20 * 30 && now >= p.getPersistentData().getLong(MISS)) return;
        if (ThreadLocalRandom.current().nextFloat() >= 0.4f) { p.getPersistentData().putLong(MISS, now); return; }   // not this time: 30 s before it can answer again
        e.setCanceled(true);   // the blow never lands: time stops first
        begin(p);
    }

    /** Starts the awakening for the equipped grimoire. */
    public static void begin(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        ACTIVE.put(p.getUUID(), 0);
        if (p.getHealth() < 1f) p.setHealth(1f);
        // time stops: everything round you is held where it stands, arrows and spells hang in the air
        for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(32), x -> x != p && x.isAlive()))
            TimeStop.freeze(p, t, SCENE + 6, false, null);
        TimeStop.freezeShots(sl, p.position(), 32, SCENE + 6, p);
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, HOLD, "Awakening", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL, SCENE);
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        // the scene on your screen
        FxPacket k = new FxPacket();
        k.shape = SHAPE; k.id = p.getId(); k.life = SCENE;
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), k);
        // and what everyone else sees: the grimoire flying open, its magic standing up round you
        AddonNetwork.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new GrimoireOpenPacket(p.getId()));
        int pal = palette(p);
        FxSend.ring(sl, pal, p.position().add(0, 0.05, 0), 0.4f, 5f, 0.12f, 20);
        FxSend.impactFrame(sl, p.position().add(0, 1.2, 0), 2, FxSend.FRAME_GOLD, 24);
        sl.playSound(null, p.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.4f, 0.6f);
        sl.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 0.5f);
    }

    private static int palette(ServerPlayer p) {
        GrimoireMastery.Book b = GrimoireMastery.bookOf(Utils.getPlayerSpellbookStack(p));
        return b == null ? Fx.GOLD : b.palette();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Integer t0 = ACTIVE.get(p.getUUID());
        if (t0 == null) return;
        int t = t0 + 1;
        ACTIVE.put(p.getUUID(), t);
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        int pal = palette(p);
        if (t < WRITE_AT && t % 12 == 0) FxSend.ring(sl, pal, c.add(0, 0.05, 0), 0.3f, 2.5f + t / 24f, 0.08f, 14);
        if (t == WRITE_AT) {
            if (!awaken(p)) { ACTIVE.remove(p.getUUID()); return; }
            FxSend.impactFrame(sl, c.add(0, 1.2, 0), 5, FxSend.FRAME_GOLD, 40);
            FxSend.ring(sl, pal, c.add(0, 0.1, 0), 0.5f, 12f, 0.25f, 20);
            FxSend.ring(sl, Fx.GOLD, c.add(0, 1.2, 0), 0.3f, 6f, 0.1f, 16);
            FxSend.beam(sl, pal, c, c.add(0, 40, 0), 1.2f, 30);
            FxSend.shake(sl, c, 0.8f, 16, 32);
            sl.playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
            sl.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.4f, 0.7f);
        }
        if (t >= SCENE + GRACE) ACTIVE.remove(p.getUUID());
    }

    /** The grimoire takes its new page: the awakened spell in one more slot. */
    private static boolean awaken(ServerPlayer p) {
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        String id = spellFor(book);
        if (id == null || awakened(book)) return false;
        AbstractSpell spell = SpellRegistry.getSpell(new ResourceLocation(IronSpellGrimoire.MODID, id));
        if (spell == null || ISpellContainer.isSpellContainer(book) == false) return false;
        try {
            ISpellContainer c = ISpellContainer.get(book);
            ISpellContainer fresh = ISpellContainer.create(c.getMaxSpellCount() + 1, c.isSpellWheel(), c.mustEquip());
            for (var slot : c.getActiveSpells()) fresh.addSpell(slot.getSpell(), slot.getLevel(), slot.isLocked(), book);
            fresh.addSpell(spell, 1, true, book);
            fresh.save(book);
        } catch (Throwable t) { return false; }
        book.getOrCreateTag().putBoolean("Awakened", true);
        p.setHealth(Math.max(p.getHealth(), p.getMaxHealth() * 0.5f));
        Component name = Component.translatable("spell.iron_spell_grimoire." + id);
        p.sendSystemMessage(Component.literal("GRIMOIRE AWAKENING  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(book.getHoverName().getString() + " wrote a new page: ").withStyle(ChatFormatting.YELLOW))
                .append(name.copy().withStyle(ChatFormatting.GOLD)));
        p.sendSystemMessage(Component.literal("  Its mastery can now reach 8.").withStyle(ChatFormatting.GRAY));
        return true;
    }

    // ------------------------------------------------------------------ untouchable while it plays (and a little after); no casting mid-scene
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttacked(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && ACTIVE.containsKey(p.getUUID())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPreCast(SpellPreCastEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && inScene(p)) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ awakened books draw their sigil behind you as you cast
    @SubscribeEvent
    public static void onCast(SpellOnCastEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getSpellId() == null || !e.getSpellId().startsWith(IronSpellGrimoire.MODID + ":")) return;
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        if (!awakened(book)) return;
        ServerLevel sl = p.serverLevel();
        int pal = palette(p);
        Vec3 back = FxUtil.flat(p.getLookAngle());
        back = back.lengthSqr() < 1e-4 ? new Vec3(0, 0, -1) : back.normalize().scale(-1);
        LightFx.circle(sl, pal, p.position().add(0, 1.3, 0).add(back.scale(0.7)), back, 1.3f, 0.15f, 14, null, 0);
        FxSend.ring(sl, pal, p.position().add(0, 0.05, 0), 0.3f, 1.8f, 0.06f, 10);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { ACTIVE.remove(e.getEntity().getUUID()); }

    // ------------------------------------------------------------------ /grimoire awaken
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("grimoire").requires(s -> s.hasPermission(2))
                .then(Commands.literal("awaken").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ItemStack book = Utils.getPlayerSpellbookStack(p);
                    if (spellFor(book) == null) { c.getSource().sendFailure(Component.literal("This grimoire has no awakening yet.")); return 0; }
                    if (awakened(book)) { c.getSource().sendFailure(Component.literal("This grimoire has already awakened.")); return 0; }
                    begin(p);
                    return 1;
                })));
    }
}
