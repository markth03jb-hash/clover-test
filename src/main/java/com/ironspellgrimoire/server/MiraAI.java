package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.BossKit.*;
import static com.ironspellgrimoire.server.FxUtil.*;
import static com.ironspellgrimoire.server.Rivals.*;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.entity.MiraHollowell;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.RivalPackets;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Mira Hollowell, the Debt-Keeper - coins, contracts, and a cat who has seen it all.
 * <ul>
 *   <li>Coin Volley: a fan of flicked coins. Ledger Slam: her book comes down where you stand.</li>
 *   <li>Deals: an offer on your screen - Y to accept, N to refuse (8 seconds). Advance (both of you hit 50% harder),
 *       Quick Money (both of you faster), Mana Loan (full mana now; in 20 s she takes half your pool back) or
 *       Insurance (none of her blows over 4, but yours on her do half).</li>
 *   <li>Borrow: the last spell you cast is hers for 12 seconds - you can't cast it, and she throws it back at you.</li>
 *   <li>Collateral (phase 2+): she holds 30% of your mana. Hit her three times within 10 s to get it back; if not,
 *       she keeps it and heals.</li>
 *   <li>Compound Interest (phase 2+): every blow she lands, and every 4 s you don't hit her, adds interest. At five,
 *       it's payday.</li>
 *   <li>Foreclosure (last phase): the ring closes in to a small circle round her. Outside it, everything costs.</li>
 *   <li>Rank 1 Late Fee (refusing a deal hurts), rank 2 Hostile Takeover (Borrow takes two spells), rank 3 Bankruptcy
 *       (Foreclosure closes tighter while coins fly).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public final class MiraAI {
    private MiraAI() {}

    static final int COIN = Fx.GOLD, INK = Fx.TREE;
    public static final int FORECLOSE = 2;
    public static final String[] DEALS = {"Advance", "Quick Money", "Mana Loan", "Insurance"};
    public static final String[] TERMS = {
            "For 15 s your blows on her land 50% harder. So do hers on you.",
            "For 15 s you move 35% faster. So does she, and she acts sooner.",
            "Your mana, filled right now. In 20 s she collects half your pool - what you can't pay, you pay in blood.",
            "For 15 s none of her blows can take more than 4. Yours on her do half."};
    static final UUID SPD = UUID.fromString("7a1e0000-0000-4000-8000-0000000000c1");

    record Offer(int mira, int deal, long until) {}
    record Deal(int deal, long until) {}
    record Collateral(int mira, float mana, long until, int hits) {}
    static final Map<UUID, Offer> OFFERS = new HashMap<>();
    static final Map<UUID, Deal> DEALS_ON = new HashMap<>();
    static final Map<UUID, Long> LOAN_DUE = new HashMap<>();
    static final Map<UUID, Collateral> COLLATERAL = new HashMap<>();
    static final Map<UUID, int[]> INTEREST = new HashMap<>();   // {stacks, game time she was last hit by them}
    static final Map<UUID, List<String>> LAST = new HashMap<>();   // the last spells each player cast
    static final Map<UUID, List<String>> BORROWED = new HashMap<>();
    static final Map<UUID, Long> BORROW_UNTIL = new HashMap<>();

    public static void tick(MiraHollowell b) {
        ServerLevel sl = (ServerLevel) b.level();
        long now = sl.getGameTime();
        Player t = target(b);
        if (t == null) return;
        b.getLookControl().setLookAt(t, 30, 30);
        if (t instanceof ServerPlayer p) upkeep(b, p, now);
        if ((b.flag() & FORECLOSE) != 0) { foreclosing(b, now); return; }
        if (b.dealAt == 0) b.dealAt = now + 100;
        if (b.busy > 0) { b.busy -= fast(b, now) ? 2 : 1; return; }
        keep(b, t, 7);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int took;
        if (b.phase() >= 3 && now >= b.forecloseAt) took = foreclose(b, now);
        else if (now >= b.dealAt && t instanceof ServerPlayer p && !OFFERS.containsKey(p.getUUID())) { b.dealAt = now + 400 - 40L * b.rank; took = offer(b, p); }
        else if (b.phase() >= 2 && t instanceof ServerPlayer p && !COLLATERAL.containsKey(p.getUUID()) && r.nextInt(5) == 0) took = collateral(b, p);
        else if (t instanceof ServerPlayer p && LAST.containsKey(p.getUUID()) && !BORROWED.containsKey(p.getUUID()) && r.nextInt(4) == 0) took = borrow(b, p);
        else took = b.distanceTo(t) < 9 && r.nextInt(3) == 0 ? slam(b, t) : volley(b, t, 5);
        b.busy = took + (b.phase() == 1 ? 24 : 18) - 2 * b.rank;
    }

    public static void phase(MiraHollowell b, int phase) {
        if (phase >= 3) b.forecloseAt = b.level().getGameTime() + 30;
    }

    static boolean fast(MiraHollowell b, long now) {
        for (Deal d : DEALS_ON.values()) if (d.deal == 1 && d.until > now) return true;
        return false;
    }

    static Deal deal(Player p) {
        Deal d = DEALS_ON.get(p.getUUID());
        return d != null && d.until > p.level().getGameTime() ? d : null;
    }

    /** Her blows on {@code p}: Advance makes them harder, Insurance caps them, and each adds interest. */
    static float outgoing(MiraHollowell b, Player p, float dmg) {
        Deal d = deal(p);
        if (d != null && d.deal == 0) dmg *= 1.5f;
        if (d != null && d.deal == 3) dmg = Math.min(dmg, 4f / Math.max(0.1f, scale(b)));
        if (b.phase() >= 2) addInterest(b, p, 1);
        return dmg;
    }

    public static float guard(MiraHollowell b, DamageSource src) {
        if (!(src.getEntity() instanceof Player p)) return 1f;
        Deal d = deal(p);
        return d == null ? 1f : d.deal == 0 ? 1.5f : d.deal == 3 ? 0.5f : 1f;
    }

    /** A player's blow landed: interest eases, collateral comes back after three. */
    public static void struck(MiraHollowell b, ServerPlayer p) {
        long now = b.level().getGameTime();
        int[] in = INTEREST.computeIfAbsent(p.getUUID(), k -> new int[]{0, 0});
        in[1] = (int) now;
        if (in[0] > 0) in[0]--;
        Collateral c = COLLATERAL.get(p.getUUID());
        if (c != null && c.mira == b.getId()) {
            if (c.hits + 1 >= 3) {
                COLLATERAL.remove(p.getUUID());
                MagicData md = MagicData.getPlayerMagicData(p);
                md.setMana((float) Math.min(p.getAttributeValue(AttributeRegistry.MAX_MANA.get()), md.getMana() + c.mana));
                p.displayClientMessage(Component.literal("Collateral returned.").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
                snd(b, "rival_coins", 1f, 1.4f);
            } else COLLATERAL.put(p.getUUID(), new Collateral(c.mira, c.mana, c.until, c.hits + 1));
        }
    }

    static void addInterest(MiraHollowell b, Player p, int n) {
        int[] in = INTEREST.computeIfAbsent(p.getUUID(), k -> new int[]{0, (int) b.level().getGameTime()});
        in[0] += n;
        if (in[0] >= 5) {   // payday
            in[0] = 0;
            ServerLevel sl = (ServerLevel) b.level();
            BossFx.ring(sl, COIN, p.position().add(0, 0.1, 0), 1.6f, 0.3f, 0.1f, 12);
            snd(sl, p.position(), "rival_coins", 1.3f, 0.7f);
            p.displayClientMessage(Component.literal("PAYDAY - the interest comes due!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
            AstaFx.later(1, () -> { if (b.isAlive() && !b.yielded) hurt(b, p, 9f); });
        }
    }

    /** The running costs: interest while you don't hit her, the loan falling due, collateral expiring, borrowed spells coming back. */
    static void upkeep(MiraHollowell b, ServerPlayer p, long now) {
        UUID id = p.getUUID();
        int[] in = INTEREST.get(id);
        if (b.phase() >= 2) {
            if (in == null) INTEREST.put(id, in = new int[]{0, (int) now});
            if (now - in[1] >= 80) { in[1] = (int) now; addInterest(b, p, 1); }
            if (now % 20 == 0 && in[0] > 0 && !OFFERS.containsKey(id))
                p.displayClientMessage(Component.literal("Interest " + "■".repeat(in[0]) + "□".repeat(5 - in[0])).withStyle(ChatFormatting.GOLD), true);
        }
        Long due = LOAN_DUE.get(id);
        if (due != null && now >= due) {
            LOAN_DUE.remove(id);
            MagicData md = MagicData.getPlayerMagicData(p);
            float owe = (float) p.getAttributeValue(AttributeRegistry.MAX_MANA.get()) * 0.5f, paid = Math.min(owe, md.getMana());
            md.setMana(md.getMana() - paid);
            p.displayClientMessage(Component.literal("Mira collects on the loan.").withStyle(ChatFormatting.GOLD), true);
            snd(b, "rival_stamp", 1.2f, 0.9f);
            if (owe - paid > 1) hurt(b, p, Math.min(10f, (owe - paid) * 0.1f));
        }
        Collateral c = COLLATERAL.get(id);
        if (c != null && now >= c.until) {
            COLLATERAL.remove(id);
            b.heal(b.getMaxHealth() * 0.04f);
            p.displayClientMessage(Component.literal("Collateral forfeited.").withStyle(ChatFormatting.RED), true);
            snd(b, "rival_stamp", 1f, 1.2f);
        }
        Long bu = BORROW_UNTIL.get(id);
        if (bu != null) {
            if (now >= bu) { BORROWED.remove(id); BORROW_UNTIL.remove(id); p.displayClientMessage(Component.literal("Mira returns what she borrowed.").withStyle(ChatFormatting.GREEN), true); }
            else if ((bu - now) % 60 == 0 && b.busy <= 0) thrownBack(b, p);
        }
        Offer o = OFFERS.get(id);
        if (o != null && now >= o.until) answer(p, false);
    }

    // ------------------------------------------------------------------ deals
    static int offer(MiraHollowell b, ServerPlayer p) {
        int deal = ThreadLocalRandom.current().nextInt(DEALS.length);
        long now = b.level().getGameTime();
        OFFERS.put(p.getUUID(), new Offer(b.getId(), deal, now + 160));
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), new RivalPackets.Offer(deal, DEALS[deal], TERMS[deal], 8));
        b.setPose(P_CAST);
        b.getNavigation().stop();
        snd(b, "rival_deal", 1.3f, 1f);
        say(b, "Mira Hollowell", ChatFormatting.YELLOW, "A proposition. " + DEALS[deal] + " - yes or no?");
        return 30;
    }

    /** The player's answer (or silence: a no). */
    public static void answer(ServerPlayer p, boolean yes) {
        Offer o = OFFERS.remove(p.getUUID());
        if (o == null) return;
        AddonNetwork.send(PacketDistributor.PLAYER.with(() -> p), new RivalPackets.Offer(-1, "", "", 0));
        Entity e = p.serverLevel().getEntity(o.mira);
        if (!(e instanceof MiraHollowell b) || !b.isAlive() || b.yielded) return;
        long now = p.serverLevel().getGameTime();
        if (!yes) {
            if (b.rank >= 1) {
                say(b, "Mira Hollowell", ChatFormatting.YELLOW, "No? There's a fee for wasting my time.");
                hurt(b, p, 4f);
            } else say(b, "Mira Hollowell", ChatFormatting.YELLOW, "Your loss.");
            b.busy = Math.min(b.busy, 6);
            return;
        }
        snd(b, "rival_stamp", 1.4f, 1f);
        BossFx.ring(p.serverLevel(), COIN, p.position().add(0, 0.1, 0), 0.2f, 1.4f, 0.08f, 10);
        say(b, "Mira Hollowell", ChatFormatting.YELLOW, "Signed. Pleasure doing business.");
        switch (o.deal) {
            case 0, 3 -> DEALS_ON.put(p.getUUID(), new Deal(o.deal, now + 300));
            case 1 -> {
                DEALS_ON.put(p.getUUID(), new Deal(1, now + 300));
                TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, SPD, "Quick Money", 0.35, AttributeModifier.Operation.MULTIPLY_TOTAL, 300);
                TimedModifiers.apply(b, Attributes.MOVEMENT_SPEED, SPD, "Quick Money", 0.35, AttributeModifier.Operation.MULTIPLY_TOTAL, 300);
            }
            default -> {
                MagicData md = MagicData.getPlayerMagicData(p);
                md.setMana((float) p.getAttributeValue(AttributeRegistry.MAX_MANA.get()));
                LOAN_DUE.put(p.getUUID(), now + 400);
            }
        }
    }

    public static boolean offering(Player p) { return OFFERS.containsKey(p.getUUID()); }

    // ------------------------------------------------------------------ moves
    static int volley(MiraHollowell b, Player t, int n) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_THROW);
        b.getNavigation().stop();
        Vec3 d0 = dirTo(b, t);
        face(b, d0);
        Vec3 a = b.position().add(0, 1.3, 0);
        for (int i = 0; i < n; i++) {
            Vec3 d = rotY(d0, Math.toRadians((i - (n - 1) / 2.0) * 11));
            warnLine(sl, a, a.add(d.scale(15)), 0.7f, 12);
            AstaFx.later(12, () -> { if (ok(b) && !b.yielded) bolt(b, COIN, b.position().add(0, 1.3, 0), d, 1.2, 14, 0.55, 4f, null); });
        }
        AstaFx.later(12, () -> snd(b, "rival_coins", 1.2f, 1f));
        return 20;
    }

    static int slam(MiraHollowell b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_RAISE);
        Vec3 at = t.position();
        warnCircle(sl, at, 3f, 18);
        AstaFx.later(18, () -> {
            if (!b.isAlive() || b.yielded) return;
            BossFx.ring(sl, INK, at.add(0, 0.15, 0), 0.3f, 3.2f, 0.14f, 8);
            BossFx.slash(sl, COIN, at.add(0, 2.5, 0), new Vec3(0, -1, 0), flat(dirTo(b, t)), 2.4f, 0.3f, 6, 1.6f);
            snd(sl, at, "rival_stamp", 1.4f, 0.7f);
            for (Player p : near(b, at.add(0, 0.9, 0), 3)) hit(b, p, 8f);
        });
        return 24;
    }

    /** Borrow: your last spell (two at rank 2) is hers for 12 seconds. */
    static int borrow(MiraHollowell b, ServerPlayer p) {
        List<String> last = LAST.get(p.getUUID());
        if (last == null || last.isEmpty()) return volley(b, p, 5);
        List<String> take = new ArrayList<>(last.subList(0, Math.min(b.rank >= 2 ? 2 : 1, last.size())));
        BORROWED.put(p.getUUID(), take);
        BORROW_UNTIL.put(p.getUUID(), b.level().getGameTime() + 240);
        b.setPose(P_CAST);
        snd(b, "rival_deal", 1.2f, 0.8f);
        BossFx.trail((ServerLevel) b.level(), INK, mid(p), b.position().add(0, 1.4, 0), 0.1f, 10);
        p.displayClientMessage(Component.literal("Mira borrows " + names(take) + " - you can't cast it for 12 s.").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        return 20;
    }

    static String names(List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            int c = id.indexOf(':');
            out.add(Component.translatable("spell." + (c < 0 ? id : id.substring(0, c) + "." + id.substring(c + 1))).getString());
        }
        return String.join(" and ", out);
    }

    /** She throws your borrowed spell back at you: a heavy gilded bolt. */
    static void thrownBack(MiraHollowell b, ServerPlayer p) {
        Vec3 from = b.position().add(0, 1.4, 0), d = mid(p).subtract(from);
        if (d.lengthSqr() < 1e-4) return;
        b.setPose(P_THROW);
        warnLine((ServerLevel) b.level(), from, from.add(d.normalize().scale(16)), 1.0f, 10);
        AstaFx.later(10, () -> { if (ok(b) && !b.yielded) bolt(b, INK, b.position().add(0, 1.4, 0), d.normalize(), 1.2, 16, 0.8, 6f, null); });
    }

    /** Collateral: she holds 30% of your mana - three hits in 10 s gets it back. */
    static int collateral(MiraHollowell b, ServerPlayer p) {
        MagicData md = MagicData.getPlayerMagicData(p);
        float take = md.getMana() * 0.3f;
        if (take < 5) return volley(b, p, 5);
        md.setMana(md.getMana() - take);
        COLLATERAL.put(p.getUUID(), new Collateral(b.getId(), take, b.level().getGameTime() + 200, 0));
        b.setPose(P_CAST);
        snd(b, "rival_stamp", 1.2f, 1.1f);
        BossFx.trail((ServerLevel) b.level(), COIN, mid(p), b.position().add(0, 1.4, 0), 0.12f, 10);
        p.displayClientMessage(Component.literal("Collateral: " + Math.round(take) + " mana held. Hit her 3 times in 10 s to get it back.").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        return 16;
    }

    /** Foreclosure: the ring closes in round her. */
    static int foreclose(MiraHollowell b, long now) {
        b.forecloseAt = now + 600 - 60L * b.rank;
        b.setFlag(b.flag() | FORECLOSE);
        b.lastMove = now;
        blink(b, b.arena);
        b.setPose(P_RAISE);
        snd(b, "rival_foreclose", 1.6f, 1f);
        say(b, "Mira Hollowell", ChatFormatting.YELLOW, "Foreclosure.");
        return 10;
    }

    static void foreclosing(MiraHollowell b, long now) {
        ServerLevel sl = (ServerLevel) b.level();
        int age = (int) (now - b.lastMove);
        float small = b.rank >= 3 ? 4f : 6f;
        float r = age < 80 ? RING - (RING - small) * (age / 80f) : small;
        b.getNavigation().stop();
        Vec3 g = ground(sl, b.arena.add(0, 1, 0)).add(0, 0.06, 0);
        if (age % 5 == 0) {
            BossFx.ring(sl, COIN, g, r - 0.2f, r, 0.08f, 7);
            BossFx.ring(sl, WARN, g, r, Math.min(RING, r + 0.25f), 0.03f, 7);
        }
        if (age > 20 && age % 10 == 0)
            for (Player p : near(b, b.arena, RING + 1)) if (flatDist(p.position(), b.arena) > r) hit(b, p, 3f);
        if (b.rank >= 3 && age % 24 == 12) { Player t = target(b); if (t != null) volley(b, t, 3); }
        if (age >= 140) { b.setFlag(b.flag() & ~FORECLOSE); b.busy = 20; b.setPose(P_IDLE); }
    }

    // ------------------------------------------------------------------ spells: remembered, and locked while borrowed
    @SubscribeEvent
    public static void onCast(SpellOnCastEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getSpellId() == null) return;
        List<String> l = LAST.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
        l.remove(e.getSpellId());
        l.add(0, e.getSpellId());
        while (l.size() > 2) l.remove(l.size() - 1);
    }

    @SubscribeEvent
    public static void onPreCast(SpellPreCastEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        List<String> b = BORROWED.get(p.getUUID());
        if (b == null || e.getSpellId() == null || !b.contains(e.getSpellId())) return;
        e.setCanceled(true);
        Long until = BORROW_UNTIL.get(p.getUUID());
        long s = until == null ? 0 : Math.max(1, (until - p.level().getGameTime()) / 20);
        p.displayClientMessage(Component.literal("Mira is borrowing that spell (" + s + " s).").withStyle(ChatFormatting.GOLD), true);
    }

    /** Clears everything she holds on a player (her fight is over). */
    public static void settle(Player p) {
        UUID id = p.getUUID();
        OFFERS.remove(id); DEALS_ON.remove(id); LOAN_DUE.remove(id); COLLATERAL.remove(id); INTEREST.remove(id); BORROWED.remove(id); BORROW_UNTIL.remove(id);
    }

    // test hooks
    public static void testBorrow(ServerPlayer p, String spell) { BORROWED.put(p.getUUID(), new ArrayList<>(List.of(spell))); BORROW_UNTIL.put(p.getUUID(), p.level().getGameTime() + 240); }
    public static int testInterest(Player p) { int[] in = INTEREST.get(p.getUUID()); return in == null ? 0 : in[0]; }
    public static boolean testCollateral(Player p) { return COLLATERAL.containsKey(p.getUUID()); }
    public static int testOffer(MiraHollowell b, ServerPlayer p) { return offer(b, p); }
    public static int testCollateralize(MiraHollowell b, ServerPlayer p) { return collateral(b, p); }
}
