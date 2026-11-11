package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.item.PhylacteryItem;
import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * What the Ossuarch leaves, and what it does: the Phylactery (cheats death once every 5 minutes) and Devil Shards
 * (forged into a grimoire at the altar for permanent spell power and cooldown reduction).
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class OssuarchDrops {
    private OssuarchDrops() {}

    public static final int MAX_SHARDS = 5;
    private static final UUID SHARD_POWER = UUID.fromString("5a0c0001-0000-4000-8000-0000000000e1");
    private static final UUID SHARD_CDR = UUID.fromString("5a0c0001-0000-4000-8000-0000000000e2");

    /** He crumbles, and these are left in the dust: the Phylactery, and a Devil Shard (two on Hard). */
    static void drop(ServerLevel sl, Vec3 at) {
        ItemStack[] loot = {new ItemStack(AddonItemRegistry.PHYLACTERY.get()),
                new ItemStack(AddonItemRegistry.DEVIL_SHARD.get(), sl.getDifficulty() == net.minecraft.world.Difficulty.HARD ? 2 : 1)};
        for (ItemStack s : loot) {
            ItemEntity it = new ItemEntity(sl, at.x, at.y + 0.6, at.z, s);
            it.setDeltaMovement((sl.random.nextDouble() - 0.5) * 0.2, 0.3, (sl.random.nextDouble() - 0.5) * 0.2);
            it.setUnlimitedLifetime();
            it.setGlowingTag(true);
            sl.addFreshEntity(it);
        }
        FxSend.ring(sl, Fx.CRIMSON, at.add(0, 0.1, 0), 0.2f, 2.5f, 0.1f, 20);
    }

    // ------------------------------------------------------------------ the Phylactery: cheating death
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getAmount() < p.getHealth()) return;
        if (e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;   // /kill and the void still win
        long now = p.serverLevel().getGameTime();
        if (p.getPersistentData().getLong(PhylacteryItem.READY_AT) > now) return;
        boolean worn = CuriosApi.getCuriosInventory(p).resolve().flatMap(h -> h.findFirstCurio(AddonItemRegistry.PHYLACTERY.get())).isPresent();
        if (!worn) return;
        e.setAmount(Math.max(0f, p.getHealth() - 1f));
        p.getPersistentData().putLong(PhylacteryItem.READY_AT, now + PhylacteryItem.RECHARGE);
        p.invulnerableTime = 40;
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        // the reliquary cracks: a cage of bone snaps shut round you for a heartbeat, and falls away
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            BoneFx.bone(sl, BoneFx.LONG, c.add(Math.cos(a) * 1.0, 1.0, Math.sin(a) * 1.0), new Vec3(-Math.cos(a) * 0.3, 1, -Math.sin(a) * 0.3), new Vec3(Math.cos(a), 0, Math.sin(a)), 1.0f, 0.07f, 16, 3, FxPacket.GROUND, 0, Vec3.ZERO, 0);
        }
        FxSend.ring(sl, 15, c.add(0, 0.1, 0), 0.3f, 3.5f, 0.12f, 12);
        FxSend.ring(sl, Fx.CRIMSON, c.add(0, 1.0, 0), 0.2f, 2.0f, 0.06f, 10);
        sound(sl, c, SoundEvents.TOTEM_USE, 0.8f, 0.6f);
        sound(sl, c, SoundEvents.BONE_BLOCK_BREAK, 1.4f, 0.5f);
        p.displayClientMessage(Component.literal("The Phylactery cracks - and holds.").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
    }

    // ------------------------------------------------------------------ Devil Shards: forging them into a grimoire
    /** Called by the altar when it's right-clicked with a shard. Returns true if the shard was used. */
    public static boolean forge(ServerPlayer p, ItemStack shard) {
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        if (book == null || book.isEmpty() || GrimoireMastery.bookOf(book) == null) {
            p.displayClientMessage(Component.literal("Equip a grimoire to forge the shard into it.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        CompoundTag tag = book.getOrCreateTag();
        int n = tag.getInt("DevilShards");
        if (n >= MAX_SHARDS) {
            p.displayClientMessage(Component.literal("This grimoire can't take any more.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        n++;
        tag.putInt("DevilShards", n);
        // rebuild our two curio modifiers on the book (Curios reads them from the stack)
        if (tag.contains("CurioAttributeModifiers", Tag.TAG_LIST)) {
            ListTag list = tag.getList("CurioAttributeModifiers", Tag.TAG_COMPOUND);
            list.removeIf(t -> t instanceof CompoundTag ct && ct.hasUUID("UUID") && (ct.getUUID("UUID").equals(SHARD_POWER) || ct.getUUID("UUID").equals(SHARD_CDR)));
        }
        CuriosApi.addModifier(book, AttributeRegistry.SPELL_POWER.get(), "Devil Shards", SHARD_POWER, 0.05 * n, AttributeModifier.Operation.MULTIPLY_BASE, "spellbook");
        CuriosApi.addModifier(book, AttributeRegistry.COOLDOWN_REDUCTION.get(), "Devil Shards", SHARD_CDR, 0.03 * n, AttributeModifier.Operation.MULTIPLY_BASE, "spellbook");
        if (!p.isCreative()) shard.shrink(1);
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position();
        FxSend.cracks(sl, Fx.CRIMSON, c.add(0, 1.0, 0), new Vec3(0, 1, 0), p.getLookAngle(), 1.2f, 20, false, p.getId() + n);
        FxSend.ring(sl, Fx.CRIMSON, c.add(0, 0.1, 0), 0.2f, 3f, 0.1f, 14);
        FxSend.ring(sl, Fx.DARK, c.add(0, 1.2, 0), 1.6f, 0.2f, 0.08f, 12);
        sound(sl, c, SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.7f);
        sound(sl, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2f, 0.5f);
        p.displayClientMessage(Component.literal("Devil-forged " + n + "/" + MAX_SHARDS + ": +" + (5 * n) + "% spell power, +" + (3 * n) + "% cooldown reduction.").withStyle(ChatFormatting.DARK_RED), true);
        return true;
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent e) {
        ItemStack s = e.getItemStack();
        if (!s.hasTag() || !s.getTag().contains("DevilShards")) return;
        int n = s.getTag().getInt("DevilShards");
        e.getToolTip().add(1, Component.literal("Devil-forged " + n + "/" + MAX_SHARDS).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
    }
}
