package com.example.arcaneaddon.server;

import com.example.arcaneaddon.AddonConfig;
import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.network.AddonNetwork;
import com.example.arcaneaddon.network.FxPacket;
import com.example.arcaneaddon.network.GrimoireOpenPacket;
import com.example.arcaneaddon.registry.AddonItemRegistry;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Grimoire Mastery: every grimoire levels up from 1 to 5 as you fight with its magic, and its stronger spells unlock
 * as it does. Progress is stored on the book itself (item NBT), so a book keeps its level wherever it goes.
 *
 *  XP:      1 per point of damage your book's spells deal (capped at the target's remaining health), +15 per kill,
 *           +300 for a boss, +3 per second while one of the book's modes is active. Passive animals, villagers and
 *           armor stands give nothing. No diminishing returns. Tuned so a book is mastered in about an hour of fighting.
 *  Levels:  each level above 1 also gives +4% spell power, +3% cooldown reduction and +50 max mana while equipped.
 *  Toggle:  config "grimoire_mastery.enabled" or /arcane progression on|off (off = everything unlocked, no bonuses).
 */
@Mod.EventBusSubscriber(modid = ArcaneAddon.MODID)
public final class GrimoireMastery {
    private GrimoireMastery() {}

    public static final int MAX_LEVEL = 5;
    /** Total XP needed to reach level 2, 3, 4, 5. */
    public static final int[] THRESHOLDS = {0, 0, 800, 2400, 5200, 9000};
    private static final String TAG = "arcane_mastery";
    private static final float KILL_XP = 15, BOSS_XP = 300, MODE_XP_PER_SECOND = 3;

    private static final UUID POWER_ID = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a100101");
    private static final UUID COOLDOWN_ID = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a100102");
    private static final UUID MANA_ID = UUID.fromString("b7d3c1a0-3f1e-4a7b-9c11-5e0d6a100103");

    /** One grimoire: its item, colour palette for the level-up flourish, and which level unlocks each spell. */
    public record Book(Supplier<Item> item, int palette, Map<String, Integer> unlocks) {}

    public static final List<Book> BOOKS = new ArrayList<>();

    private static void book(Supplier<Item> item, int pal, Object... idLevel) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < idLevel.length; i += 2) m.put((String) idLevel[i], (Integer) idLevel[i + 1]);
        BOOKS.add(new Book(item, pal, m));
    }

    static {
        book(AddonItemRegistry.BLACK_GRIMOIRE::get, Fx.CRIMSON,
                "summon_greatsword", 1, "piercing_dash", 1, "zetten", 1,
                "ki_sense", 2, "instinct_guard", 2, "dweller_slash", 2,
                "sky_splitter", 3, "anti_magic", 3,
                "borrowed_slash", 4,
                "devil_union", 5);
        book(AddonItemRegistry.DARK_GRIMOIRE::get, Fx.YAMI,
                "black_blade", 1, "dimension_slash", 1, "ki_foresight", 1,
                "avidya_slash", 2, "death_thrust", 2, "black_cocoon", 2,
                "black_moon", 3, "black_hole", 3,
                "mana_zone_dimension_slash", 4,
                "event_horizon", 5);
        book(AddonItemRegistry.WIND_GRIMOIRE::get, Fx.WIND,
                "wind_blade_shower", 1, "sylphs_breath", 1, "gale_white_bow", 1,
                "tornado_fang", 2, "hushed_dance", 2,
                "spirit_of_zephyr", 3, "spirit_of_boreas", 3,
                "spirit_of_euros", 4, "spirit_dive", 4,
                "spirit_storm", 5);
        book(AddonItemRegistry.MERCURY_GRIMOIRE::get, 11,
                "mercury_bullets", 1, "mercury_spears", 1, "silver_mirror", 1,
                "silver_rain", 2, "storm_of_blades", 2,
                "mercury_eagle", 3, "mercury_sphere", 3,
                "regal_spear", 4, "silver_guardian", 4,
                "mercury_cage", 5);
        book(AddonItemRegistry.FIRE_GRIMOIRE::get, Fx.FIRE,
                "exploding_fireball", 1, "explosive_scattershot", 1, "flaming_bat", 1,
                "flame_knuckles", 2, "ring_of_fire", 2,
                "fireball_barrage", 3, "magna_special", 3,
                "fireball_rain", 4, "soul_chain", 4,
                "fire_cannon", 5);
        book(AddonItemRegistry.LIGHTNING_GRIMOIRE::get, 13,
                "thunder_fist", 1, "lightning_step", 1, "thunder_kick", 1,
                "thunder_orb", 2, "chain_lightning", 2, "mana_sense", 2,
                "thunder_gloves", 3, "thunderfall", 3,
                "thunder_god_mode", 4,
                "thunder_rampage", 5);
        book(AddonItemRegistry.HELLFIRE_GRIMOIRE::get, 14,
                "calidos_brachium", 1, "lioness_charge", 1, "burning_guard", 1,
                "flame_lion_paws", 2, "mana_skin", 2,
                "calidos_brachium_barrage", 3, "ground_punch", 3,
                "lion_mana_zone", 4, "hellfire_incarnate", 4,
                "calidos_brachium_purgatory", 5);
        book(AddonItemRegistry.BONE_GRIMOIRE::get, 15,
                "bone_spear_volley", 1, "bone_sword", 1, "bone_spike_eruption", 1,
                "bone_whips", 2, "marrow_regeneration", 2,
                "ribcage_prison", 3, "bone_thicket", 3,
                "spatial_cube", 4, "bone_spear_storm", 4,
                "beelzebub_union", 5);
        book(AddonItemRegistry.STEEL_GRIMOIRE::get, Fx.STEEL,
                "violent_rotating_lance", 1, "thunder_arrow", 1, "steel_castle_wall", 1,
                "blazing_spiral_lance", 2, "steel_prison", 2,
                "white_steel_horse_cart", 3,
                "knights_armor", 4,
                "acier_silva", 5);
    }

    // ------------------------------------------------------------------ book data (shared with the client)
    public static Book bookOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (Book b : BOOKS) if (stack.is(b.item().get())) return b;
        return null;
    }

    public static int xp(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t == null ? 0 : t.getCompound(TAG).getInt("xp");
    }

    public static int level(ItemStack stack) { return levelFor(xp(stack)); }

    public static int levelFor(int xp) {
        int l = 1;
        while (l < MAX_LEVEL && xp >= THRESHOLDS[l + 1]) l++;
        return l;
    }

    private static void setXp(ItemStack stack, int xp) {
        CompoundTag t = stack.getOrCreateTag();
        CompoundTag m = t.getCompound(TAG);
        m.putInt("xp", Math.max(0, Math.min(xp, THRESHOLDS[MAX_LEVEL])));
        t.put(TAG, m);
    }

    /** Spell id (path) -> the level that unlocks it in this book, or 0 when the book doesn't gate it. */
    public static int required(Book b, String spell) {
        if (b == null) return 0;
        return b.unlocks().getOrDefault(spell, 0);
    }

    /** True when this spell's XP should go to this book (one of its spells, or a devil spell for Asta's book). */
    private static boolean belongs(Book b, String spell) {
        if (b == null) return false;
        if (b.unlocks().containsKey(spell)) return true;
        return b.item().get() == AddonItemRegistry.BLACK_GRIMOIRE.get() && spell.startsWith("devil_");
    }

    public static List<String> unlockedAt(Book b, int level) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : b.unlocks().entrySet()) if (e.getValue() == level) out.add(e.getKey());
        return out;
    }

    public static MutableComponent spellNames(List<String> ids) {
        MutableComponent c = Component.empty();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) c.append(", ");
            c.append(Component.translatable("spell.arcane_addon." + ids.get(i)));
        }
        return c;
    }

    // ------------------------------------------------------------------ gaining XP
    private static ItemStack equipped(Player p) {
        ItemStack s = Utils.getPlayerSpellbookStack(p);
        return s == null ? ItemStack.EMPTY : s;
    }

    /** Adds XP to the equipped book if the spell belongs to it. */
    private static void award(ServerPlayer p, String spell, float amount) {
        if (!AddonConfig.progression() || amount <= 0) return;
        ItemStack book = equipped(p);
        Book b = bookOf(book);
        if (!belongs(b, spell)) return;
        addXp(p, book, b, amount * AddonConfig.xpMultiplier());
    }

    private static final Map<UUID, Float> FRACTION = new java.util.HashMap<>();

    private static void addXp(ServerPlayer p, ItemStack book, Book b, double amount) {
        float acc = FRACTION.getOrDefault(p.getUUID(), 0f) + (float) amount;
        int whole = (int) acc;
        FRACTION.put(p.getUUID(), acc - whole);
        if (whole <= 0) return;
        int before = xp(book);
        if (before >= THRESHOLDS[MAX_LEVEL]) return;
        int oldLevel = levelFor(before);
        setXp(book, before + whole);
        int newLevel = level(book);
        for (int l = oldLevel + 1; l <= newLevel; l++) levelUp(p, b, l);
    }

    private static String spellOf(Object source) {
        if (!(source instanceof SpellDamageSource sds)) return null;
        AbstractSpell s = sds.spell();
        if (s == null || !"arcane_addon".equals(s.getSpellResource().getNamespace())) return null;
        return s.getSpellResource().getPath();
    }

    private static boolean worthXp(LivingEntity t) {
        return !(t instanceof Animal) && !(t instanceof AbstractVillager) && !(t instanceof ArmorStand);
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getEntity() == p) return;
        String spell = spellOf(e.getSource());
        if (spell == null || !worthXp(e.getEntity())) return;
        award(p, spell, Math.min(e.getAmount(), Math.max(0f, e.getEntity().getHealth())));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getEntity() == p) return;
        String spell = spellOf(e.getSource());
        LivingEntity t = e.getEntity();
        if (spell == null || !worthXp(t)) return;
        boolean boss = t.getType().is(Tags.EntityTypes.BOSSES) || t instanceof Warden;
        award(p, spell, KILL_XP + (boss ? BOSS_XP : 0));
    }

    // ------------------------------------------------------------------ level bonuses, mode XP
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        ItemStack book = equipped(p);
        Book b = bookOf(book);
        boolean on = AddonConfig.progression() && b != null;
        int bonus = on ? level(book) - 1 : 0;
        setMod(p, AttributeRegistry.SPELL_POWER.get(), POWER_ID, "Grimoire Mastery power", 0.04 * bonus, AttributeModifier.Operation.MULTIPLY_BASE);
        setMod(p, AttributeRegistry.COOLDOWN_REDUCTION.get(), COOLDOWN_ID, "Grimoire Mastery haste", 0.03 * bonus, AttributeModifier.Operation.MULTIPLY_BASE);
        setMod(p, AttributeRegistry.MAX_MANA.get(), MANA_ID, "Grimoire Mastery mana", 50.0 * bonus, AttributeModifier.Operation.ADDITION);
        if (!on) return;
        // time spent in one of the book's modes
        for (String mode : ModeHud.active(p)) {
            if (belongs(b, mode)) { addXp(p, book, b, MODE_XP_PER_SECOND * AddonConfig.xpMultiplier()); break; }
        }
    }

    private static void setMod(Player p, Attribute a, UUID id, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(id);
        if (amount == 0) { if (cur != null) inst.removeModifier(id); return; }
        if (cur != null && cur.getAmount() == amount) return;
        if (cur != null) inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    // ------------------------------------------------------------------ locked spells
    @SubscribeEvent
    public static void onPreCast(SpellPreCastEvent e) {
        if (!AddonConfig.progression() || !(e.getEntity() instanceof ServerPlayer p)) return;
        String id = e.getSpellId();
        if (id == null || !id.startsWith("arcane_addon:")) return;
        String spell = id.substring("arcane_addon:".length());
        ItemStack book = equipped(p);
        Book b = bookOf(book);
        int need = required(b, spell);
        if (need <= 1 || level(book) >= need) return;
        if (need == MAX_LEVEL && LimitBreak.active(p)) return;   // surpassing your limits: the ultimate answers anyway
        e.setCanceled(true);
        p.displayClientMessage(Component.translatable("ui.arcane_addon.mastery.locked",
                Component.translatable("spell.arcane_addon." + spell), need).withStyle(ChatFormatting.RED), true);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHEST_LOCKED, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    // ------------------------------------------------------------------ level-up flourish
    private static void levelUp(ServerPlayer p, Book b, int level) {
        ServerLevel sl = p.serverLevel();
        Vec3 at = p.position();
        int pal = b.palette();
        // the grimoire flies open, rings of its magic burst out around you and a halo forms overhead
        AddonNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new GrimoireOpenPacket(p.getId()));
        FxSend.ring(sl, pal, at.add(0, 0.05, 0), 0.3f, 4.5f, 0.12f, 16);
        FxSend.ring(sl, pal, at.add(0, 1.0, 0), 0.2f, 2.6f, 0.08f, 12);
        AstaFx.later(4, () -> FxSend.ring(sl, Fx.SILVER, p.position().add(0, 0.06, 0), 0.5f, 3.2f, 0.05f, 12));
        FxLib.halo(sl, pal, p.position().add(0, p.getBbHeight() + 0.5, 0), 1.0f);
        if (level == MAX_LEVEL) FxSend.shake(sl, at, 0.35f, 10, 12);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 0.8f);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.7f);
        if (level == MAX_LEVEL) sl.playSound(null, at.x, at.y, at.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
        List<String> unlocked = unlockedAt(b, level);
        MutableComponent msg = Component.translatable("ui.arcane_addon.mastery.level_up", level).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        if (!unlocked.isEmpty()) msg.append(Component.literal(" — ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("ui.arcane_addon.mastery.unlocked", spellNames(unlocked)).withStyle(ChatFormatting.YELLOW));
        p.displayClientMessage(msg, true);
        p.sendSystemMessage(msg);
    }

    // ------------------------------------------------------------------ the on/off switch, synced to clients
    private static void syncToggle(ServerPlayer p) {
        FxPacket pkt = new FxPacket();
        pkt.shape = 34; pkt.life = AddonConfig.progression() ? 1 : 0;
        AddonNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) syncToggle(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { FRACTION.remove(e.getEntity().getUUID()); }

    // ------------------------------------------------------------------ commands
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("arcane").requires(s -> s.hasPermission(2))
                .then(Commands.literal("progression")
                        .then(Commands.literal("on").executes(c -> toggle(c, true)))
                        .then(Commands.literal("off").executes(c -> toggle(c, false)))
                        .executes(c -> {
                            c.getSource().sendSuccess(() -> Component.translatable(AddonConfig.progression() ? "ui.arcane_addon.mastery.is_on" : "ui.arcane_addon.mastery.is_off"), false);
                            return 1;
                        }))
                .then(Commands.literal("mastery")
                        .then(Commands.literal("set").then(Commands.argument("level", IntegerArgumentType.integer(1, MAX_LEVEL))
                                .executes(c -> setLevel(c, c.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> setLevel(c, EntityArgument.getPlayer(c, "player"))))))
                        .then(Commands.literal("add").then(Commands.argument("xp", IntegerArgumentType.integer(1))
                                .executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    ItemStack book = equipped(p);
                                    Book b = bookOf(book);
                                    if (b == null) return noBook(c);
                                    addXp(p, book, b, IntegerArgumentType.getInteger(c, "xp"));
                                    return 1;
                                })))));
    }

    private static int toggle(CommandContext<CommandSourceStack> c, boolean on) {
        AddonConfig.PROGRESSION.set(on);
        AddonConfig.PROGRESSION.save();
        for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) syncToggle(p);
        c.getSource().sendSuccess(() -> Component.translatable(on ? "ui.arcane_addon.mastery.is_on" : "ui.arcane_addon.mastery.is_off").withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static int setLevel(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        ItemStack book = equipped(p);
        Book b = bookOf(book);
        if (b == null) return noBook(c);
        int lvl = IntegerArgumentType.getInteger(c, "level");
        setXp(book, THRESHOLDS[Math.max(1, lvl)]);
        c.getSource().sendSuccess(() -> Component.translatable("ui.arcane_addon.mastery.set", p.getDisplayName(), lvl).withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static int noBook(CommandContext<CommandSourceStack> c) {
        c.getSource().sendFailure(Component.translatable("ui.arcane_addon.mastery.no_book"));
        return 0;
    }
}
