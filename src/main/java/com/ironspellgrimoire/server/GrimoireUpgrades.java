package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.item.GrimoireUpgradeItem;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Grimoire upgrades: things you bind into your grimoire at the Grimoire Altar (with it equipped), kept on the book
 * ("Upgrades"), each up to a limit. Every boss leaves its own (only that boss - Zenon's is the Devil Shard, in
 * ZenonDrops); none are crafted: the rest are found on quests, in rifts and on bounties.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class GrimoireUpgrades {
    private GrimoireUpgrades() {}

    /** One kind of upgrade: what it's called, how many a book takes, what each does, where it comes from. */
    public record Kind(String id, String name, int max, String each, String from) {}

    public static final List<Kind> KINDS = List.of(
            new Kind("despair_fang", "Despair Fang", 3, "+6% damage to foes below half health", "The Beast of Despair"),
            new Kind("copied_sigil", "Copied Sigil", 3, "6% chance a spell comes straight off cooldown", "Rhya"),
            new Kind("elven_blade_shard", "Elven Blade Shard", 3, "+8% damage to bosses", "Licht"),
            new Kind("grave_ember", "Grave Ember", 3, "each kill restores 4% of your max mana", "The Revenant"),
            new Kind("curse_thread", "Curse Thread", 3, "8% chance a hit curses: +20% damage taken for 4 s", "The Curse Witch"),
            new Kind("mirror_shard", "Mirror Shard", 1, "+8% spell power, +8% cooldown reduction", "Your Reflection (the Ascension Trial)"),
            new Kind("mana_crystal_binding", "Mana Crystal Binding", 3, "+40 max mana", "Quests (a chance)"),
            new Kind("silver_clasp", "Silver Clasp", 2, "+2 armour", "Quests (a chance)"),
            new Kind("swift_bookmark", "Swift Bookmark", 3, "+8% mana regeneration", "Quests (a chance)"),
            new Kind("runed_spine", "Runed Spine", 3, "+3% spell power", "Quests, rift elite rooms, bounties"),
            new Kind("phoenix_ribbon", "Phoenix Ribbon", 1, "a killing blow leaves you at 1 health instead (once every 5 min)", "Big quests and the Daily Special"),
            new Kind("wind_feather", "Wind Feather", 2, "+5% movement speed", "Quests (a chance)"),
            new Kind("bloodstone", "Bloodstone", 2, "+10% damage while you're below half health", "Quests, rift elite rooms"),
            new Kind("warding_glyph", "Warding Glyph", 3, "-6% damage taken from bosses", "Quests, bounties"),
            new Kind("starlit_seal", "Starlit Seal", 1, "every quest and bounty pays one more merit star", "The Daily Special"));

    public static Kind kind(String id) { for (Kind k : KINDS) if (k.id().equals(id)) return k; return null; }

    public static int count(ItemStack book, String id) {
        return book == null || !book.hasTag() ? 0 : book.getTag().getCompound("Upgrades").getInt(id);
    }

    private static int count(ServerPlayer p, String id) { return count(Utils.getPlayerSpellbookStack(p), id); }

    /** Total of everything bound into it (Devil Shards included). */
    public static int total(ItemStack book) {
        if (book == null || !book.hasTag()) return 0;
        int n = book.getTag().getInt("DevilShards");
        CompoundTag u = book.getTag().getCompound("Upgrades");
        for (String k : u.getAllKeys()) n += u.getInt(k);
        return n;
    }

    // ------------------------------------------------------------------ binding
    /** Binds one upgrade into the equipped grimoire. Returns whether it took. */
    public static boolean bind(ServerPlayer p, ItemStack stack) {
        if (!(stack.getItem() instanceof GrimoireUpgradeItem u)) return false;
        Kind k = kind(u.id);
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        if (k == null || book == null || book.isEmpty() || GrimoireMastery.bookOf(book) == null) {
            p.displayClientMessage(Component.literal("Equip a grimoire to bind this into it.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        CompoundTag tag = book.getOrCreateTag(), ups = tag.getCompound("Upgrades");
        int n = ups.getInt(k.id());
        if (n >= k.max()) {
            p.displayClientMessage(Component.literal("This grimoire already holds " + k.max() + " " + k.name() + (k.max() > 1 ? "s" : "") + ".").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        ups.putInt(k.id(), n + 1);
        tag.put("Upgrades", ups);
        refresh(book);
        if (!p.isCreative()) stack.shrink(1);
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        FxSend.ring(sl, Fx.GOLD | com.ironspellgrimoire.network.FxPacket.KEEP_SHAPE, c.add(0, 0.1, 0), 0.3f, 2.8f, 0.08f, 14);
        FxSend.beam(sl, Fx.GOLD | com.ironspellgrimoire.network.FxPacket.KEEP_SHAPE, c.add(0, 0.2, 0), c.add(0, 3, 0), 0.25f, 12);
        FxLib.halo(sl, Fx.GOLD, c.add(0, p.getBbHeight() + 0.4, 0), 0.7f);
        sl.playSound(null, p.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 1f, 1.2f);
        sl.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.0f);
        p.displayClientMessage(Component.literal(k.name() + " bound (" + (n + 1) + "/" + k.max() + "): " + k.each() + ".").withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    // the stat upgrades live on the book as curio modifiers (Curios reads them from the stack), like the Devil Shards
    private static final UUID MIRROR_POWER = UUID.fromString("7b1e0001-0000-4000-8000-0000000000a1");
    private static final UUID MIRROR_CDR = UUID.fromString("7b1e0001-0000-4000-8000-0000000000a2");
    private static final UUID CRYSTAL = UUID.fromString("7b1e0001-0000-4000-8000-0000000000a3");
    private static final UUID CLASP = UUID.fromString("7b1e0001-0000-4000-8000-0000000000a4");
    private static final UUID BOOKMARK = UUID.fromString("7b1e0001-0000-4000-8000-0000000000a5");
    private static final UUID SPINE = UUID.fromString("7b1e0001-0000-4000-8000-0000000000a6");
    private static final UUID FEATHER = UUID.fromString("7b1e0001-0000-4000-8000-0000000000a7");
    private static final List<UUID> MINE = List.of(MIRROR_POWER, MIRROR_CDR, CRYSTAL, CLASP, BOOKMARK, SPINE, FEATHER);

    private static void refresh(ItemStack book) {
        CompoundTag tag = book.getOrCreateTag();
        if (tag.contains("CurioAttributeModifiers", Tag.TAG_LIST))
            tag.getList("CurioAttributeModifiers", Tag.TAG_COMPOUND).removeIf(t -> t instanceof CompoundTag ct && ct.hasUUID("UUID") && MINE.contains(ct.getUUID("UUID")));
        int mirror = count(book, "mirror_shard"), crystal = count(book, "mana_crystal_binding"), clasp = count(book, "silver_clasp"),
                mark = count(book, "swift_bookmark"), spine = count(book, "runed_spine");
        if (mirror > 0) {
            add(book, AttributeRegistry.SPELL_POWER.get(), MIRROR_POWER, 0.08 * mirror, AttributeModifier.Operation.MULTIPLY_BASE);
            add(book, AttributeRegistry.COOLDOWN_REDUCTION.get(), MIRROR_CDR, 0.08 * mirror, AttributeModifier.Operation.MULTIPLY_BASE);
        }
        if (crystal > 0) add(book, AttributeRegistry.MAX_MANA.get(), CRYSTAL, 40.0 * crystal, AttributeModifier.Operation.ADDITION);
        if (clasp > 0) add(book, Attributes.ARMOR, CLASP, 2.0 * clasp, AttributeModifier.Operation.ADDITION);
        if (mark > 0) add(book, AttributeRegistry.MANA_REGEN.get(), BOOKMARK, 0.08 * mark, AttributeModifier.Operation.MULTIPLY_BASE);
        if (spine > 0) add(book, AttributeRegistry.SPELL_POWER.get(), SPINE, 0.03 * spine, AttributeModifier.Operation.MULTIPLY_BASE);
        int feather = count(book, "wind_feather");
        if (feather > 0) add(book, Attributes.MOVEMENT_SPEED, FEATHER, 0.05 * feather, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    private static void add(ItemStack book, Attribute a, UUID id, double amt, AttributeModifier.Operation op) {
        CuriosApi.addModifier(book, a, "Grimoire upgrade", id, amt, op, "spellbook");
    }

    // ------------------------------------------------------------------ what they do in a fight
    private static final Map<UUID, Long> CURSED = new HashMap<>();

    /** How much harder a hit lands for the book's upgrades (fangs on the wounded, blade shards on bosses, the curse). */
    public static float damageMult(ServerPlayer p, LivingEntity t) {
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        float m = 1f;
        int fang = count(book, "despair_fang"), blade = count(book, "elven_blade_shard");
        if (fang > 0 && t.getHealth() < t.getMaxHealth() * 0.5f) m *= 1f + 0.06f * fang;
        boolean boss = TimeStop.isBoss(t) || BossCores.isBoss(t.getType());
        if (blade > 0 && boss) m *= 1f + 0.08f * blade;
        if (boss && book != null && book.hasTag() && book.getTag().getInt("Sigil") == 4) m *= 1.1f;   // the Star Forge's Sigil of the Hunt
        int blood = count(book, "bloodstone");
        if (blood > 0 && p.getHealth() < p.getMaxHealth() * 0.5f) m *= 1f + 0.1f * blood;
        Long until = CURSED.get(t.getUUID());
        if (until != null) { if (t.level().getGameTime() < until) m *= 1.2f; else CURSED.remove(t.getUUID()); }
        return m;
    }

    /** After a hit: Curse Threads may curse the target. */
    public static void afterHit(ServerPlayer p, LivingEntity t) {
        int thread = count(p, "curse_thread");
        if (thread <= 0 || ThreadLocalRandom.current().nextFloat() >= 0.08f * thread) return;
        ServerLevel sl = p.serverLevel();
        CURSED.put(t.getUUID(), sl.getGameTime() + 80);
        if (CURSED.size() > 256) CURSED.clear();
        Vec3 at = t.position().add(0, t.getBbHeight() + 0.3, 0);
        FxSend.ring(sl, 24 | com.ironspellgrimoire.network.FxPacket.KEEP_SHAPE, at, 0.5f, 0.7f, 0.05f, 30);
        FxSend.ring(sl, 24 | com.ironspellgrimoire.network.FxPacket.KEEP_SHAPE, t.position().add(0, 0.05, 0), 0.3f, 1.2f, 0.05f, 16);
        sl.playSound(null, t.blockPosition(), SoundEvents.WITCH_CELEBRATE, SoundSource.PLAYERS, 0.7f, 1.4f);
    }

    /** Copied Sigils: now and then a spell is copied back at once - off cooldown. */
    @SubscribeEvent
    public static void onCast(SpellOnCastEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getSpellId() == null) return;
        int sig = count(p, "copied_sigil");
        if (sig <= 0 || ThreadLocalRandom.current().nextFloat() >= 0.06f * sig) return;
        String id = e.getSpellId();
        AstaFx.later(2, () -> {
            try {
                var cd = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p).getPlayerCooldowns();
                if (cd.removeCooldown(id)) {
                    cd.syncToPlayer(p);
                    FxSend.ring(p.serverLevel(), Fx.SILVER | com.ironspellgrimoire.network.FxPacket.KEEP_SHAPE, p.position().add(0, 1.2, 0), 0.9f, 1.0f, 0.05f, 10);
                    p.displayClientMessage(Component.literal("Copied Sigil - that spell is ready again.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                }
            } catch (Throwable ignored) {}
        });
    }

    /** Grave Embers: kills feed your mana. */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getEntity() == p) return;
        int ember = count(p, "grave_ember");
        if (ember <= 0) return;
        var md = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p);
        float max = (float) p.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        md.setMana(Math.min(max, md.getMana() + max * 0.04f * ember));
        BossFx.orb(p.serverLevel(), Fx.VOID, e.getEntity().position().add(0, 1, 0), p.position().add(0, 1, 0).subtract(e.getEntity().position()).normalize().scale(0.3), 0.2f, 10);
    }

    /** Warding Glyphs: bosses' blows land softer. */
    @SubscribeEvent
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getSource().getEntity() instanceof LivingEntity by)) return;
        int ward = count(p, "warding_glyph");
        if (ward > 0 && (TimeStop.isBoss(by) || BossCores.isBoss(by.getType()))) e.setAmount(e.getAmount() * (1f - 0.06f * ward));
    }

    /** The Starlit Seal: one more merit star from every quest and bounty. */
    public static int bonusStars(ServerPlayer p) { return count(p, "starlit_seal"); }

    /** The Phoenix Ribbon: once every five minutes, a blow that would kill you leaves you at 1 health. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDamage(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getAmount() < p.getHealth() || count(p, "phoenix_ribbon") <= 0) return;
        long now = p.serverLevel().getGameTime(), last = p.getPersistentData().getLong("isg_phoenix");
        if (last != 0 && now - last < 6000) return;
        p.getPersistentData().putLong("isg_phoenix", now);
        e.setAmount(Math.max(0f, p.getHealth() - 1f));
        ServerLevel sl = p.serverLevel();
        FxSend.ring(sl, Fx.FIRE | com.ironspellgrimoire.network.FxPacket.KEEP_SHAPE, p.position().add(0, 0.1, 0), 0.4f, 4f, 0.15f, 18);
        FxSend.beam(sl, Fx.FIRE | com.ironspellgrimoire.network.FxPacket.KEEP_SHAPE, p.position(), p.position().add(0, 8, 0), 0.6f, 16);
        sl.playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.4f);
        p.displayClientMessage(Component.literal("The Phoenix Ribbon burns - you're still standing.").withStyle(ChatFormatting.GOLD), true);
    }

    /** Each boss leaves its own upgrade (Zenon's Devil Shard is dropped in ZenonDrops). */
    static ItemStack trophy(net.minecraft.world.entity.EntityType<?> t) {
        if (t == com.ironspellgrimoire.registry.AddonEntities.BEAST_OF_DESPAIR.get()) return new ItemStack(AddonItemRegistry.DESPAIR_FANG.get());
        if (t == com.ironspellgrimoire.registry.AddonEntities.RHYA.get()) return new ItemStack(AddonItemRegistry.COPIED_SIGIL.get());
        if (t == com.ironspellgrimoire.registry.AddonEntities.LICHT.get()) return new ItemStack(AddonItemRegistry.ELVEN_BLADE_SHARD.get());
        if (t == com.ironspellgrimoire.registry.AddonEntities.REVENANT.get()) return new ItemStack(AddonItemRegistry.GRAVE_EMBER.get());
        if (t == com.ironspellgrimoire.registry.AddonEntities.CURSE_WITCH.get()) return new ItemStack(AddonItemRegistry.CURSE_THREAD.get());
        if (t == com.ironspellgrimoire.registry.AddonEntities.REFLECTION.get()) return new ItemStack(AddonItemRegistry.MIRROR_SHARD.get());
        return ItemStack.EMPTY;
    }
}
