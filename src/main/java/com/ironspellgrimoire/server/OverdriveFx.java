package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import com.ironspellgrimoire.registry.AddonSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Overdrive Grimoire (an original grimoire): your heart is the engine. A heart-rate gauge climbs with every
 * spell you cast, every blow you land and every blow you take, and falls back toward resting when you stop. Above
 * 150 you run Hot - Overdrive spells hit harder and you move quicker; above 200 you hit the Redline - harder and
 * quicker still, but your heart strains (a little damage every two seconds), and if you sit at the very top too
 * long it gives out: you Collapse, slowed and weak, back down at 90.
 * Its weapon is the Heartline, a black katana with a crimson heartbeat down the blade, drawn out of your
 * chest by the first page; every other page needs it in your hand.
 * Drawn by client/OverdriveFxClient (FxPacket shape 96).
 *
 *  1 Draw the Heartline  - the sword is drawn out of your chest on a ribbon of red light, into your hand (+20 bpm)
 * The kit fights like a swordsman with almost no magic who wins on technique alone (inspired by that kind of anime
 * hero, all names and looks original): a draw-cut faster than its own sound, reading a foe's attack before it comes,
 * one minute of everything you have - and the collapse after.
 *
 *  2 Thunderclap Draw    - a breath of stillness, then a draw-cut so fast its sound lands after it (+10)
 *  3 Adrenaline Rush     - you dash through everything ahead, cutting as you pass, afterimages behind (+25)
 *  4 Read the Pulse      - 3 s: you read the next attack aimed at you - it misses, you step behind the attacker and cut
 *  5 Flatline            - a long piercing thrust; whatever it runs through flatlines - slowed and weakened (+10)
 *  6 Tachycardia         - a storm of cuts on the area ahead: 6 at rest, up to 14 at the Redline (+20)
 *  7 Second Wind         - you breathe: heals more the faster your heart is beating, and slows it by 40
 *  8 Ribbon Bind         - the sword's ribbon lashes out, wraps a foe and drags it to your blade (+10)
 *  9 Last Minute         - 60 s mode: every limit off - your heart held at the Redline with no strain, Overdrive hits
 *                          +60%; when the minute runs out (or the book leaves you) you collapse for 8 s
 * 10 Final Beat          - your heart stops - one beat of silence - then a single enormous cut; the faster your heart
 *                          was beating the harder it falls, and it drops you back to resting. Cast inside Last Minute
 *                          it spends every second left of the minute on this one cut (up to x2.5) - then you collapse.
 *                          Surpassing Limits: wider and harder.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire")
public final class OverdriveFx {
    private OverdriveFx() {}

    public static final String MODE = "redline";
    public static final float REST = 70, HOT = 150, RED = 200, MAX = 220;
    private static final String BPM = "isg_od_bpm", TOP = "isg_od_top", LAST = "isg_od_last", READ = "isg_od_read", WAS_RED = "isg_od_was_red";
    /** Last Minute: one minute. */
    public static final int MINUTE = 1200;
    private static final UUID HOT_SPEED = UUID.fromString("c3a1e5d0-6b2f-4f19-8e41-9d0b30000001");
    private static final UUID RED_TEMPO = UUID.fromString("c3a1e5d0-6b2f-4f19-8e41-9d0b30000002");
    private static final UUID FLAT_SLOW = UUID.fromString("c3a1e5d0-6b2f-4f19-8e41-9d0b30000003");
    private static final UUID FLAT_WEAK = UUID.fromString("c3a1e5d0-6b2f-4f19-8e41-9d0b30000004");
    private static final UUID COLLAPSE_SLOW = UUID.fromString("c3a1e5d0-6b2f-4f19-8e41-9d0b30000005");
    private static final UUID COLLAPSE_WEAK = UUID.fromString("c3a1e5d0-6b2f-4f19-8e41-9d0b30000006");
    private static final double TAU = Math.PI * 2;

    // ================================================================== the client shapes (OverdriveFxClient, shape 96)
    private static FxPacket pk(int kind, Vec3 c, int life) {
        FxPacket p = new FxPacket();
        p.shape = 96; p.id = FxSend.nextId(); p.h = kind; p.c = c; p.life = life; p.a1 = (float) (Math.random() * 1000);
        return p;
    }
    private static void send(ServerLevel sl, FxPacket p) { FxSend.sendNear(sl, p.c, p); }
    private static FxPacket on(int kind, ServerPlayer pl, int life) { FxPacket p = pk(kind, pl.position(), life); p.v = new Vec3(pl.getId(), 1, 0); return p; }

    /** 0 a heartbeat-spike slash from c along d, r long, w wide. */
    static void spike(ServerLevel sl, Vec3 c, Vec3 d, float r, float w, int life) { FxPacket p = pk(0, c, life); p.d = d; p.r = r; p.r2 = w; send(sl, p); }
    /** 1 the two pulses (lub - dub) round c, out to r. */
    static void pulses(ServerLevel sl, Vec3 c, float r, int life) { FxPacket p = pk(1, c, life); p.r = r; send(sl, p); }
    /** 2 a dash from c to u, afterimages along it. */
    static void rush(ServerLevel sl, Vec3 c, Vec3 u, int life) { FxPacket p = pk(2, c, life); p.u = u; send(sl, p); }
    /** 3 a thrust from c along d, r long, flattening into a line. */
    static void thrust(ServerLevel sl, Vec3 c, Vec3 d, float r, int life) { FxPacket p = pk(3, c, life); p.d = d; p.r = r; send(sl, p); }
    /** 4 a storm of n cuts over c, radius r. */
    static void flurry(ServerLevel sl, Vec3 c, float r, int n, int life) { FxPacket p = pk(4, c, life); p.r = r; p.a0 = n; send(sl, p); }
    /** 5 the ribbon lashing from a player to an entity. */
    static void ribbon(ServerLevel sl, ServerPlayer pl, LivingEntity t, int life) { FxPacket p = on(5, pl, life); p.u = t.position(); p.r = t.getId(); send(sl, p); }
    /** 6 the Redline on a player. */
    static void redlineAura(ServerLevel sl, ServerPlayer pl, int life) { send(sl, on(6, pl, life)); }
    /** 7 the Final Beat: from c along d, r long, w wide; sweep = the ticks of silence before the cut. */
    static void finalCut(ServerLevel sl, Vec3 c, Vec3 d, float r, float w, int charge, int life) { FxPacket p = pk(7, c, life); p.d = d; p.r = r; p.r2 = w; p.sweep = charge; send(sl, p); }
    /** 8 the sword drawn out of a player's chest. */
    static void draw(ServerLevel sl, ServerPlayer pl) { send(sl, on(8, pl, 22)); }
    /** 9 a breath of second wind on a player; r = how much it heals. */
    static void breath(ServerLevel sl, ServerPlayer pl, float heal) { FxPacket p = on(9, pl, 26); p.r = heal; send(sl, p); }
    /** 10 a cut landing on c. */
    static void spark(ServerLevel sl, Vec3 c, float size) { FxPacket p = pk(10, c, 8); p.r = size; send(sl, p); }

    // ================================================================== the heart
    public static float bpm(Player p) {
        float b = p.getPersistentData().getFloat(BPM);
        return b <= 0 ? REST : b;
    }

    static void setBpm(ServerPlayer p, float b) {
        float was = bpm(p);
        b = Math.max(REST - 10, Math.min(MAX, b));
        if (Moves.inMode(p, MODE)) b = Math.max(RED + 5, b);   // the Redline holds it up there
        p.getPersistentData().putFloat(BPM, b);
        if (was < HOT && b >= HOT) p.displayClientMessage(Component.literal("Heart rate " + (int) b + " - Hot").withStyle(ChatFormatting.GOLD), true);
        if (was < RED && b >= RED) p.displayClientMessage(Component.literal("Heart rate " + (int) b + " - REDLINE").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        sync(p);
    }

    /** Push the heart up (a cast, a blow) - and note when, so it doesn't settle while you're fighting. */
    static void raise(ServerPlayer p, float by) {
        p.getPersistentData().putLong(LAST, p.serverLevel().getGameTime());
        setBpm(p, bpm(p) + by);
    }

    static int state(Player p) { float b = bpm(p); return b >= RED ? 2 : b >= HOT ? 1 : 0; }

    /** How much harder Overdrive spells hit at this heart rate. */
    static float drive(ServerPlayer p) { if (Moves.inMode(p, MODE)) return 1.6f; int s = state(p); return s == 2 ? 1.4f : s == 1 ? 1.2f : 1f; }

    /** The heart on the player's screen (OverdriveFxClient, kind 99): a0 = bpm, r = 1 if equipped, r2 = 1 if in the Redline mode. */
    static void sync(ServerPlayer p) {
        if (p.connection == null) return;
        FxPacket h = new FxPacket();
        h.shape = 96; h.h = 99; h.a0 = bpm(p); h.r = equipped(p) ? 1 : 0; h.r2 = Moves.inMode(p, MODE) ? 1 : 0; h.life = 1;
        com.ironspellgrimoire.network.AddonNetwork.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), h);
    }

    public static boolean equipped(ServerPlayer p) {
        var b = io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(p);
        return b != null && b.is(AddonItemRegistry.OVERDRIVE_GRIMOIRE.get());
    }

    /** The Heartline in the main hand (every page but the first needs it). */
    public static boolean holding(Player p) { return p.getMainHandItem().is(AddonItemRegistry.HEARTLINE.get()); }

    static void play(ServerLevel sl, Vec3 at, String kind, float vol, float pitch) {
        SoundEvent s = AddonSounds.get(kind);
        if (s != null) sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    static void sound(ServerLevel sl, Vec3 at, SoundEvent s, float vol, float pitch) { sl.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch); }

    /** An Overdrive hit: harder the faster your heart beats, and every blow lands pushes it a little higher. */
    static void hit(ServerPlayer p, LivingEntity t, float amt, String id) {
        AstaFx.damage(p, t, amt * drive(p), id);
        spark(p.serverLevel(), t.position().add(0, t.getBbHeight() * 0.6, 0), 0.6f + (state(p) * 0.25f));
        raise(p, 2);
    }

    private static Vec3 look(ServerPlayer p) {
        Vec3 l = flat(p.getLookAngle());
        return l.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : l.normalize();
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && equipped(p)) raise(p, 6);   // taking a blow gets the blood up
        if (e.getSource().getEntity() instanceof ServerPlayer p && e.getSource().getDirectEntity() == p && holding(p) && equipped(p)) {
            e.setAmount(e.getAmount() * drive(p));   // the sword's own swings ride the heart too
            raise(p, 3);
        }
    }

    /** Read the Pulse: the first attack aimed at you while you read it misses - and you're behind the attacker, cutting. */
    @SubscribeEvent
    public static void onAttacked(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        long until = p.getPersistentData().getLong(READ);
        if (until == 0 || p.serverLevel().getGameTime() > until) return;
        if (!(e.getSource().getEntity() instanceof LivingEntity a) || a == p || !a.isAlive() || a.distanceToSqr(p) > 24 * 24) return;
        e.setCanceled(true);
        p.getPersistentData().remove(READ);
        ServerLevel sl = p.serverLevel();
        Vec3 from = p.position();
        Vec3 af = flat(a.getLookAngle());
        if (af.lengthSqr() < 1.0E-4) af = flat(a.position().subtract(from));
        af = af.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : af.normalize();
        Vec3 to = clip(sl, p, a.position().add(0, 0.5, 0), a.position().add(0, 0.5, 0).subtract(af.scale(1.6 + a.getBbWidth() * 0.5))).subtract(0, 0.5, 0);
        rush(sl, from, to, 10);
        p.teleportTo(to.x, to.y, to.z);
        Vec3 face = a.position().subtract(to);
        p.setYRot((float) (Math.toDegrees(Math.atan2(-face.x, face.z))));
        p.setYHeadRot(p.getYRot());
        p.hurtMarked = true;
        play(sl, from, "rush", 0.9f, 1.4f);
        float power = p.getPersistentData().getFloat(READ + "_p");
        AstaFx.later(2, () -> {
            if (!alive(p) || !a.isAlive()) return;
            a.invulnerableTime = 0;
            spike(sl, p.position().add(0, 1.0, 0), flat(a.position().subtract(p.position())).normalize(), 3.2f, 1.4f, 12);
            play(sl, a.position(), "blade_draw", 1.1f, 1.2f);
            hit(p, a, Math.max(6f, power) * 1.6f, "heartbeat");
        });
        raise(p, 12);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (p.getPersistentData().getBoolean(WAS_RED) && !Moves.inMode(p, MODE)) {   // the minute's up: everything you had, spent
            p.getPersistentData().remove(WAS_RED);
            collapse(p, 160, "The minute's up - your body gives out");
        }
        if (!equipped(p)) {
            if (p.tickCount % 5 == 0 && Moves.inMode(p, MODE)) Moves.ends(p, MODE);   // the book's gone: so is the Redline
            if (p.tickCount % 40 == 11 && p.getPersistentData().contains(BPM)) sync(p);
            return;
        }
        long now = p.serverLevel().getGameTime();
        float b = bpm(p);
        boolean red = Moves.inMode(p, MODE);
        if (p.tickCount % 10 == 0) {
            if (now - p.getPersistentData().getLong(LAST) > 60 && b > REST && !red) setBpm(p, b - 4);   // settling back toward resting
            int s = state(p);
            if (s >= 1) TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, HOT_SPEED, "Hot blood", s == 2 ? 0.15 : 0.08, AttributeModifier.Operation.MULTIPLY_BASE, 15);
            if (s == 2) TimedModifiers.apply(p, Attributes.ATTACK_SPEED, RED_TEMPO, "Redline", 0.3, AttributeModifier.Operation.MULTIPLY_BASE, 15);
        }
        if (!red && b >= RED && p.tickCount % 40 == 0) {   // the strain of the Redline
            p.hurt(p.damageSources().magic(), 1f);
            play(p.serverLevel(), p.position(), "heartbeat", 0.6f, 1.6f);
        }
        if (!red && b >= MAX - 5) {   // pinned at the very top: the heart gives out
            int top = p.getPersistentData().getInt(TOP) + 1;
            p.getPersistentData().putInt(TOP, top);
            if (top > 100) collapse(p, 50, "Your heart gives out - you collapse");
        } else p.getPersistentData().putInt(TOP, 0);
        if (p.tickCount % 40 == 13) sync(p);
    }

    static void collapse(ServerPlayer p, int ticks, String msg) {
        p.getPersistentData().putInt(TOP, 0);
        p.getPersistentData().putFloat(BPM, 90);
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, COLLAPSE_SLOW, "Collapsed", -0.6, AttributeModifier.Operation.MULTIPLY_TOTAL, ticks);
        TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, COLLAPSE_WEAK, "Collapsed", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL, ticks);
        p.displayClientMessage(Component.literal(msg).withStyle(ChatFormatting.DARK_RED), true);
        play(p.serverLevel(), p.position(), "flatline", 1.0f, 1.0f);
        sync(p);
    }

    // ================================================================== 1. Draw the Heartline
    public static void drawHeartline(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        ItemStack held = p.getMainHandItem();
        if (!held.isEmpty() && !holding(p)) {   // put away what's in your hand first
            ItemStack copy = held.copy();
            p.getInventory().setItem(p.getInventory().selected, ItemStack.EMPTY);
            if (!p.getInventory().add(copy)) { p.getInventory().setItem(p.getInventory().selected, copy); p.displayClientMessage(Component.literal("No room to put away what you're holding").withStyle(ChatFormatting.GRAY), true); return; }
        }
        if (!holding(p)) p.getInventory().setItem(p.getInventory().selected, new ItemStack(AddonItemRegistry.HEARTLINE.get()));
        draw(sl, p);
        play(sl, p.position(), "heartbeat", 1.0f, 1.0f);
        play(sl, p.position(), "blade_draw", 1.0f, 1.0f);
        raise(p, 20);
    }

    // ================================================================== 2. Thunderclap Draw
    /** A breath of stillness (a glint on the blade), then one cut faster than its sound: the first foe in its line takes
     *  it hardest, and the crack of it arrives after. */
    public static void thunderDraw(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final Vec3 d = look(p), c = p.position().add(0, 1.0, 0).add(d.scale(0.6));
        final float L = 13f, W = 1.1f;
        spark(sl, c.add(d.scale(0.4)), 0.5f);   // the glint
        Moves.stance(p, 0, 0, -0.5, 0.5, 0, 4);
        AstaFx.later(4, () -> {
            if (!alive(p)) return;
            spike(sl, c, d, L, W, 12);
            List<LivingEntity> line = new java.util.ArrayList<>(Kit.along(sl, p, c, c.add(d.scale(L)), W));
            line.sort(java.util.Comparator.comparingDouble(t -> t.distanceToSqr(c)));
            for (int i = 0; i < line.size(); i++) hit(p, line.get(i), power * (i == 0 ? 1.6f : 1.1f), "pulse_cut");
        });
        AstaFx.later(8, () -> {   // ...and then you hear it
            if (!alive(p)) return;
            play(sl, c.add(d.scale(L * 0.5)), "blade_draw", 1.2f, 1.5f);
            sound(sl, c.add(d.scale(L * 0.5)), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.35f, 1.9f);
        });
        raise(p, 10);
    }

    // ================================================================== 3. Adrenaline Rush
    public static void adrenalineRush(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 d = look(p), from = p.position();
        Vec3 to = clip(sl, p, from.add(0, 0.5, 0), from.add(0, 0.5, 0).add(d.scale(9))).subtract(0, 0.5, 0);
        rush(sl, from, to, 16);
        play(sl, from, "rush", 1.0f, 1.0f);
        Set<LivingEntity> cut = new HashSet<>(Kit.along(sl, p, from.add(0, 1, 0), to.add(0, 1, 0), 1.6));
        p.teleportTo(to.x, to.y, to.z);
        p.hurtMarked = true;
        AstaFx.later(2, () -> { for (LivingEntity t : cut) if (alive(p) && t.isAlive()) hit(p, t, power, "adrenaline_rush"); });
        raise(p, 25);
    }

    // ================================================================== 4. Read the Pulse
    /** For 3 s you hear every heart round you: the first attack aimed at you misses (see onAttacked). */
    public static void readPulse(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        p.getPersistentData().putLong(READ, sl.getGameTime() + 60);
        p.getPersistentData().putFloat(READ + "_p", power);
        pulses(sl, p.position(), 2.2f, 26);
        play(sl, p.position(), "heartbeat", 1.0f, 1.1f);
        raise(p, 8);
    }

    /** Whether Read the Pulse is waiting on the player. */
    public static boolean reading(ServerPlayer p) { return p.serverLevel().getGameTime() <= p.getPersistentData().getLong(READ); }

    // ================================================================== 5. Flatline
    public static void flatlineThrust(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        Vec3 d = p.getLookAngle().normalize(), c = p.getEyePosition().subtract(0, 0.35, 0).add(d.scale(0.8));
        float L = 13f;
        thrust(sl, c, d, L, 20);
        play(sl, c, "blade_draw", 1.0f, 0.8f);
        AstaFx.later(3, () -> {
            if (!alive(p)) return;
            play(sl, c.add(d.scale(L * 0.5)), "flatline", 0.9f, 1.0f);
            for (LivingEntity t : Kit.along(sl, p, c, c.add(d.scale(L)), 1.2)) {
                hit(p, t, power * 1.1f, "flatline");
                TimedModifiers.apply(t, Attributes.MOVEMENT_SPEED, FLAT_SLOW, "Flatlined", -0.45, AttributeModifier.Operation.MULTIPLY_TOTAL, 60);
                TimedModifiers.apply(t, Attributes.ATTACK_DAMAGE, FLAT_WEAK, "Flatlined", -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL, 60);
            }
        });
        raise(p, 10);
    }

    // ================================================================== 6. Tachycardia
    public static void tachycardia(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final Vec3 g = p.position().add(look(p).scale(4));
        final float R = 3.6f;
        final int n = 6 + Math.round(8 * Math.max(0, Math.min(1, (bpm(p) - REST) / (RED - REST))));   // the faster the heart, the more cuts
        flurry(sl, g, R, n, n * 2 + 12);
        for (int i = 0; i < n; i++) {
            final int k = i;
            AstaFx.later(i * 2, () -> {
                if (!alive(p)) return;
                if (k % 2 == 0) play(sl, g, "blade_draw", 0.6f, 1.3f + 0.05f * k);
                for (LivingEntity t : Kit.near(sl, p, g.add(0, 1, 0), R)) { t.invulnerableTime = 0; hit(p, t, power * 0.16f, "tachycardia"); }
            });
        }
        raise(p, 20);
    }

    // ================================================================== 7. Second Wind
    public static void secondWind(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        float b = bpm(p);
        float heal = 3f + 9f * Math.max(0, Math.min(1, (b - REST) / (MAX - REST)));
        p.heal(heal);
        breath(sl, p, heal);
        play(sl, p.position(), "breath", 1.0f, 1.0f);
        setBpm(p, b - 40);
    }

    // ================================================================== 8. Ribbon Bind
    public static void ribbonBind(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = aimed(p, 16, 12);
        play(sl, p.position(), "ribbon", 1.0f, 1.0f);
        if (t == null) {   // lashes out at nothing
            FxPacket miss = on(5, p, 12);
            miss.u = p.getEyePosition().add(p.getLookAngle().scale(12)); miss.r = -1;
            send(sl, miss);
            return;
        }
        ribbon(sl, p, t, 18);
        final LivingEntity tt = t;
        AstaFx.later(5, () -> {   // wrapped - and dragged to your blade
            if (!alive(p) || !tt.isAlive()) return;
            Vec3 to = p.position().add(look(p).scale(1.6));
            Vec3 pull = to.subtract(tt.position());
            tt.setDeltaMovement(pull.scale(0.32 * GravityFx.give(tt)).add(0, 0.25, 0));
            tt.hurtMarked = true;
            hit(p, tt, power * 0.6f, "ribbon_bind");
        });
        AstaFx.later(12, () -> {
            if (!alive(p) || !tt.isAlive()) return;
            tt.invulnerableTime = 0;
            hit(p, tt, power * 0.6f, "ribbon_bind");   // and cut as it arrives
            spike(sl, p.position().add(0, 1, 0), look(p), 3.5f, 1.2f, 10);
            play(sl, tt.position(), "blade_draw", 0.9f, 1.1f);
        });
        raise(p, 10);
    }

    // ================================================================== 9. Last Minute (mode)
    /** Every limit off for one minute - and when it's spent, you collapse (onTick). */
    public static void lastMinute(ServerPlayer p, float power) {
        ServerLevel sl = p.serverLevel();
        final int T = MINUTE;
        p.getPersistentData().putBoolean(WAS_RED, true);
        p.displayClientMessage(Component.literal("Last Minute - every limit off").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        Moves.mode(p, MODE, T, 10, pl -> setBpm(pl, Math.max(bpm(pl), RED + 5)));
        setBpm(p, RED + 5);
        redlineAura(sl, p, T);
        Moves.stance(p, 0, 0, 0, 0.3, 0, T);
        play(sl, p.position(), "heartbeat", 1.6f, 1.3f);
        sound(sl, p.position(), SoundEvents.BLAZE_SHOOT, 0.8f, 0.6f);
    }

    // ================================================================== 10. Final Beat
    public static void finalBeat(ServerPlayer p, float power) {
        boolean limit = LimitBreak.active(p);
        ServerLevel sl = p.serverLevel();
        final float b = bpm(p);
        final float drive = 1f + 0.9f * Math.max(0, Math.min(1, (b - REST) / (MAX - REST)));   // what the heart puts into it
        // inside Last Minute: every second left of the minute goes into this one cut (then the collapse - onTick)
        final float spent = Moves.inMode(p, MODE) ? (float) Moves.left(p, MODE) / MINUTE : 0f;
        if (spent > 0) {
            Moves.ends(p, MODE);
            p.displayClientMessage(Component.literal("The whole minute - in one cut").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        }
        final float pow = power * drive * (limit ? 1.3f : 1f) * (1f + 1.5f * spent);
        final Vec3 d = look(p), c = p.position().add(0, 1.0, 0);
        final float L = (limit ? 20f : 15f) + 6f * spent, W = (limit ? 7f : 5f) + 2f * spent;
        final int CHARGE = 16;
        finalCut(sl, c, d, L, W, CHARGE, CHARGE + 40);
        play(sl, c, "flatline", 1.2f, 0.8f);   // the heart stops
        p.getPersistentData().putFloat(BPM, REST);
        sync(p);
        AstaFx.later(CHARGE, () -> {
            if (!alive(p)) return;
            play(sl, c, "blade_draw", 1.4f, 0.6f);
            play(sl, c, "heartbeat", 1.6f, 0.5f);   // one beat
            sound(sl, c, SoundEvents.GENERIC_EXPLODE, 0.6f, 1.3f);
            FxSend.shake(sl, c, 0.7f, 12, 24);
            Vec3 rt = new Vec3(-d.z, 0, d.x);
            Set<LivingEntity> cut = new HashSet<>();
            for (int i = -2; i <= 2; i++) {
                Vec3 a = c.add(rt.scale(i * W / 4));
                cut.addAll(Kit.along(sl, p, a, a.add(d.scale(L)), 1.6));
            }
            for (LivingEntity t : cut) { hit(p, t, pow * 1.5f, "final_beat"); Kit.push(t, d, 0.6 * GravityFx.give(t), 0.25); }
        });
        AstaFx.later(CHARGE + 14, () -> {   // the cut lines left hanging in the air burst as the blade is sheathed
            if (!alive(p)) return;
            play(sl, c.add(d.scale(L * 0.5)), "blade_draw", 1.0f, 1.6f);
            for (LivingEntity t : Kit.along(sl, p, c, c.add(d.scale(L)), W * 0.5)) { t.invulnerableTime = 0; hit(p, t, pow * 0.4f, "final_beat"); }
        });
    }

    /** Surpassing Limits: the heart beats out of your chest. */
    public static void awaken(ServerPlayer p) {
        setBpm(p, RED + 5);
        redlineAura(p.serverLevel(), p, 120);
    }

}
