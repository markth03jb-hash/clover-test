package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.BossKit.*;
import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.entity.Licht;
import com.ironspellgrimoire.registry.AddonItemRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Licht's fight - Sword Magic, one sword per phase.
 * <ul>
 *   <li>Phase 1 (100-70%), the first sword: Origin Flash (a giant slash straight down a line), a step faster than the
 *       eye (Leg Strength Boost: he is behind you and cutting), and swords of light thrown at where you stand.</li>
 *   <li>Phase 2 (70-40%), the sword that drinks magic: Conquering Eon - for four seconds he draws in every spell that
 *       hits him (they barely hurt), then pays it all back in one slash. Stop casting, or dodge it. Origin Flash Barrage:
 *       a fan of giant slashes.</li>
 *   <li>Phase 3 (40-15%), two swords: every Origin Flash leaves its cut hanging in the air for a while - don't walk
 *       into it.</li>
 *   <li>Phase 4 (below 15%), Forbidden Magic: his light turns black; the sky fills with swords that fall in waves, and
 *       their cuts sap your maximum health for a while.</li>
 * </ul>
 */
public final class LichtAI {
    private LichtAI() {}

    private static final String WHO = "Licht";
    private static final ChatFormatting COL = ChatFormatting.WHITE;
    private static final UUID SAP = UUID.fromString("11c70001-0000-4000-8000-0000000000e1");
    private static final Map<UUID, float[]> ABSORB = new HashMap<>();          // {until (game time), stored}
    private static final Map<UUID, List<Object[]>> CUTS = new HashMap<>();     // lingering cuts: {Vec3 a, Vec3 b, Long until}
    private static final Map<UUID, Boolean> MET = new HashMap<>();

    private static int pal(Licht b) { return b.phase() >= 4 ? Fx.DARK : Fx.SILVER; }
    private static int lightPal(Licht b) { return b.phase() >= 4 ? Fx.YAMI : Fx.GOLD; }

    public static void tick(Licht b) {
        ServerLevel sl = (ServerLevel) b.level();
        long now = sl.getGameTime();
        if (b.getMainHandItem().isEmpty()) arm(b);
        lingering(b, sl, now);
        absorbing(b, sl, now);
        Player t = target(b);
        if (t == null) { b.getNavigation().stop(); return; }
        if (MET.put(b.getUUID(), true) == null) {
            say(b, WHO, COL, "You carry a grimoire... Then show me what you would protect with it.");
            sl.playSound(null, b.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.4f, 1.6f);
        }
        b.getLookControl().setLookAt(t, 30, 30);
        if (b.busy > 0) { b.busy--; return; }
        double dist = b.distanceTo(t);
        keep(b, t, 5);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<java.util.function.ToIntBiFunction<Licht, Player>> pool = new ArrayList<>();
        pool.add(LichtAI::originFlash);
        pool.add(LichtAI::step);
        pool.add(LichtAI::lightSwords);
        if (dist > 10) pool.add(LichtAI::step);
        if (b.phase() >= 2) { pool.add(LichtAI::barrage); if (!ABSORB.containsKey(b.getUUID()) && r.nextInt(3) == 0) pool.add(LichtAI::eon); }
        if (b.phase() >= 4) { pool.add(LichtAI::skyOfSwords); pool.add(LichtAI::skyOfSwords); }
        int took = pool.get(r.nextInt(pool.size())).applyAsInt(b, t);
        b.busy = took + (b.phase() == 1 ? 28 : b.phase() == 2 ? 22 : 16);
    }

    private static void arm(Licht b) {
        switch (b.phase()) {
            case 1 -> b.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AddonItemRegistry.DEMON_SLAYER_SWORD.get()));
            case 2 -> b.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AddonItemRegistry.DEMON_DWELLER_SWORD.get()));
            case 3 -> { b.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AddonItemRegistry.DEMON_SLAYER_SWORD.get())); b.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(AddonItemRegistry.DEMON_DWELLER_SWORD.get())); }
            default -> { b.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AddonItemRegistry.DARK_KATANA.get())); b.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY); }
        }
        b.setDropChance(EquipmentSlot.MAINHAND, 0f);
        b.setDropChance(EquipmentSlot.OFFHAND, 0f);
    }

    // ------------------------------------------------------------------ Origin Flash
    private static void flash(Licht b, ServerLevel sl, Vec3 from, Vec3 dir, int T, float len) {
        Vec3 a = from, c = from.add(dir.scale(len));
        warnLine(sl, a.add(0, -0.9, 0), c.add(0, -0.9, 0), 2.4f, T);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            int pal = pal(b);
            for (int k = 0; k < 4; k++) FxSend.slash(sl, pal, a.add(dir.scale(len * (k + 0.5) / 4)), dir, new Vec3(0, 1, 0).add(rightOf(dir).scale(0.25)).normalize(), len * 0.32f, 1.0f, 8, 1.8f, 0.9f);
            FxSend.trail(sl, pal, a, c, 0.5f, 10);
            for (Player p : along(b, a, c, 2.0)) {
                hurt(b, p, 14f);
                if (b.phase() >= 4) TimedModifiers.apply(p, Attributes.MAX_HEALTH, SAP, "Forbidden cut", -0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, 200);
            }
            if (b.phase() >= 3) CUTS.computeIfAbsent(b.getUUID(), k -> new ArrayList<>()).add(new Object[]{a, c, sl.getGameTime() + 120});
            FxSend.shake(sl, a.add(c).scale(0.5), 0.4f, 6, 18);
            sl.playSound(null, b.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.6f, 0.6f);
            sl.playSound(null, b.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.HOSTILE, 0.8f, 1.4f);
        });
    }

    static int originFlash(Licht b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_SLASH);
        b.getNavigation().stop();
        Vec3 d = dirTo(b, t);
        face(b, d);
        int T = b.phase() >= 3 ? 12 : 16;
        flash(b, sl, b.position().add(0, 1.0, 0), d, T, 22);
        return T + 8;
    }

    static int barrage(Licht b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_SPIN);
        b.getNavigation().stop();
        Vec3 d = dirTo(b, t);
        face(b, d);
        int n = b.phase() >= 3 ? 5 : 3;
        for (int i = 0; i < n; i++) {
            Vec3 di = rotY(d, (i - (n - 1) / 2.0) * 0.35);
            flash(b, sl, b.position().add(0, 1.0, 0), di, 18 + i * 4, 18);
        }
        return 18 + n * 4 + 8;
    }

    /** Leg Strength Boost: faster than the eye - behind you, and cutting. */
    static int step(Licht b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_DASH);
        b.getNavigation().stop();
        int T = 8;
        warnCircle(sl, t.position(), 2.4f, T);
        Vec3 from = b.position();
        AstaFx.later(T, () -> {
            if (!ok(b) || !t.isAlive()) return;
            Vec3 behind = t.position().add(flat(t.getLookAngle()).normalize().scale(-1.6));
            Vec3 to = SpaceFx.safe(sl, behind, b);
            if (to == null) return;
            FxSend.trail(sl, lightPal(b), from.add(0, 1, 0), to.add(0, 1, 0), 0.4f, 8);
            Vec3 face = flat(t.position().subtract(to));
            SpaceFx.move(b, to, (float) Math.toDegrees(Math.atan2(-face.x, face.z)));
            b.setPose(P_SLASH);
            FxSend.slash(sl, pal(b), t.position().add(0, 1, 0), face.lengthSqr() > 1e-4 ? face.normalize() : new Vec3(1, 0, 0), new Vec3(0, 1, 0), 2.2f, 0.5f, 6, 1.6f, 0.7f);
            for (Player p : near(b, t.position().add(0, 1, 0), 2.4)) hurt(b, p, 8f);
            sl.playSound(null, to.x, to.y, to.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.2f, 1.4f);
        });
        return T + 6;
    }

    /** Swords of light, thrown at where you stand and around it. */
    static int lightSwords(Licht b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int T = 16;
        for (int i = 0; i < 5; i++) {
            Vec3 land = ground(sl, t.position().add(i == 0 ? 0 : r.nextDouble(-3, 3), 0, i == 0 ? 0 : r.nextDouble(-3, 3))).add(0, 0.4, 0);
            Vec3 at = b.position().add(Math.cos(i * 1.25) * 1.6, 2.6 + (i % 2) * 0.5, Math.sin(i * 1.25) * 1.6);
            warnCircle(sl, land, 1.3f, T + 2);
            LightFx.sword(sl, lightPal(b), at, land, land.subtract(at).normalize(), 1.4f, 1f, T - 2, 4, 0, T + 8);
            AstaFx.later(T + 2, () -> { if (ok(b)) for (Player p : near(b, land.add(0, 0.6, 0), 1.4)) hurt(b, p, 6f); });
        }
        sl.playSound(null, b.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.4f, 1.2f);
        return T + 6;
    }

    /** Conquering Eon: four seconds of drawing in every spell thrown at him - then all of it, in one slash. */
    static int eon(Licht b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        ABSORB.put(b.getUUID(), new float[]{sl.getGameTime() + 80, 0});
        say(b, WHO, COL, "This sword drinks magic. Pour yours into it, if you dare.");
        for (Player p : near(b, b.position(), 40)) p.displayClientMessage(Component.literal("Licht is drawing in magic - stop casting!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
        sl.playSound(null, b.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.5f, 0.6f);
        return 100;
    }

    private static void absorbing(Licht b, ServerLevel sl, long now) {
        float[] a = ABSORB.get(b.getUUID());
        if (a == null) return;
        if (now % 6 == 0) FxSend.ring(sl, Fx.GOLD, b.position().add(0, 1.2, 0), 3.5f, 0.4f, 0.08f, 6);
        if (now % 10 == 0) for (Player p : near(b, b.position(), 20)) FxSend.trail(sl, Fx.GOLD, mid(p), b.position().add(0, 1.4, 0), 0.12f, 8);
        if (now < (long) a[0]) return;
        ABSORB.remove(b.getUUID());
        Player t = target(b);
        if (t == null) return;
        float stored = a[1];
        Vec3 d = dirTo(b, t);
        face(b, d);
        b.setPose(P_SLASH);
        Vec3 from = b.position().add(0, 1, 0), to = from.add(d.scale(24));
        warnLine(sl, from.add(0, -0.9, 0), to.add(0, -0.9, 0), 3.2f, 14);
        AstaFx.later(14, () -> {
            if (!ok(b)) return;
            for (int k = 0; k < 6; k++) FxSend.slash(sl, Fx.GOLD, from.add(d.scale(24 * (k + 0.5) / 6)), d, new Vec3(0, 1, 0), 5f, 1.4f, 10, 2f, 1.2f);
            float dmg = 8f + Math.min(30f, stored * 0.6f);
            for (Player p : along(b, from, to, 3.0)) hurt(b, p, dmg);
            FxSend.impactFrame(sl, from.add(d.scale(6)), 4, FxSend.FRAME_LIGHT, 30);
            FxSend.shake(sl, from, 0.8f, 12, 30);
            sl.playSound(null, b.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2f, 1.4f);
        });
    }

    /** Lingering cuts (phase 3+): the Origin Flash's line hangs in the air for six seconds. */
    private static void lingering(Licht b, ServerLevel sl, long now) {
        List<Object[]> l = CUTS.get(b.getUUID());
        if (l == null) return;
        l.removeIf(o -> now > (Long) o[2]);
        for (Object[] o : l) {
            Vec3 a = (Vec3) o[0], c = (Vec3) o[1];
            if (now % 20 == 0) RiftFx.cut(sl, a.add(c).scale(0.5), c.subtract(a).normalize(), new Vec3(0, 1, 0), (float) a.distanceTo(c) * 0.5f, 22);
            if (now % 10 == 0) for (Player p : along(b, a, c, 0.9)) hurt(b, p, 4f);
        }
    }

    /** Forbidden Magic: the sky fills with black swords that fall in waves on where you stand. */
    static int skyOfSwords(Licht b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vec3 c = t.position();
        for (int w = 0; w < 3; w++) {
            for (int i = 0; i < 6; i++) {
                Vec3 land = ground(sl, c.add(r.nextDouble(-6, 6), 0, r.nextDouble(-6, 6))).add(0, 0.4, 0);
                Vec3 sky = land.add(r.nextDouble(-1, 1), 11, r.nextDouble(-1, 1));
                int hang = 18 + w * 10;
                warnCircle(sl, land, 1.4f, hang + 4);
                LightFx.sword(sl, Fx.YAMI, sky, land, new Vec3(0, -1, 0), 1.9f, 1.1f, hang, 4, 0, hang + 10);
                AstaFx.later(hang + 4, () -> {
                    if (!ok(b)) return;
                    for (Player p : near(b, land.add(0, 0.6, 0), 1.5)) {
                        hurt(b, p, 7f);
                        TimedModifiers.apply(p, Attributes.MAX_HEALTH, SAP, "Forbidden cut", -0.2, AttributeModifier.Operation.MULTIPLY_TOTAL, 200);
                    }
                });
            }
        }
        sl.playSound(null, b.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.2f, 0.6f);
        return 50;
    }

    public static void hurt(Licht b, DamageSource src, float amount) {
        if (src.getEntity() instanceof Player p && valid(b, p)) b.focus = p.getUUID();
        float[] a = ABSORB.get(b.getUUID());
        if (a != null && src instanceof io.redspace.ironsspellbooks.damage.SpellDamageSource) a[1] += amount / 0.3f;
    }

    /** While he drinks magic, spells barely hurt him. */
    public static float taken(Licht b, DamageSource src) {
        return ABSORB.containsKey(b.getUUID()) && src instanceof io.redspace.ironsspellbooks.damage.SpellDamageSource ? 0.3f : 1f;
    }

    public static void phase(Licht b, int phase) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setTransforming(50);
        b.setPose(P_ROAR);
        ABSORB.remove(b.getUUID());
        Vec3 c = b.position();
        for (Player p : near(b, c.add(0, 1, 0), 5)) {
            Vec3 away = flat(p.position().subtract(c));
            if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
            p.setDeltaMovement(away.normalize().scale(1.2).add(0, 0.4, 0));
            p.hurtMarked = true;
        }
        String line = switch (phase) {
            case 2 -> "Not bad. This one is heavier.";
            case 3 -> "Two blades now. Keep up.";
            default -> "...Forgive me. What comes next is not light.";
        };
        say(b, WHO, COL, line);
        AstaFx.later(30, () -> {
            arm(b);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                LightFx.sword(sl, phase >= 4 ? Fx.YAMI : Fx.GOLD, c.add(Math.cos(a) * 2.2, 0.4, Math.sin(a) * 2.2), c.add(Math.cos(a) * 2.2, 5, Math.sin(a) * 2.2), new Vec3(0, 1, 0), 1.6f, 1f, 4, 12, 1, 18);
            }
            FxSend.ring(sl, phase >= 4 ? Fx.DARK : Fx.GOLD, c.add(0, 0.1, 0), 0.5f, 9f, 0.3f, 16);
            FxSend.impactFrame(sl, c.add(0, 1.6, 0), 4, FxSend.FRAME_LIGHT, 28);
            FxSend.shake(sl, c, 0.8f, 12, 30);
            sl.playSound(null, b.blockPosition(), phase >= 4 ? SoundEvents.WITHER_SPAWN : SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.4f, phase >= 4 ? 1.2f : 0.8f);
        });
    }

    public static void dying(Licht b, int t) {
        ServerLevel sl = (ServerLevel) b.level();
        if (t == 1) {
            b.setPose(P_ROAR);
            say(b, WHO, COL, "So this is... how it ends. Protect what I could not.");
            FxSend.impactFrame(sl, b.position().add(0, 1.5, 0), 4, FxSend.FRAME_LIGHT, 26);
            CUTS.remove(b.getUUID());
            ABSORB.remove(b.getUUID());
            MET.remove(b.getUUID());
        }
        if (t < 60 && t % 5 == 0) LightFx.glint(sl, Fx.GOLD, b.position().add(ThreadLocalRandom.current().nextDouble(-0.6, 0.6), 0.5 + ThreadLocalRandom.current().nextDouble(1.6), ThreadLocalRandom.current().nextDouble(-0.6, 0.6)), 0.5f, 14);
    }
}
