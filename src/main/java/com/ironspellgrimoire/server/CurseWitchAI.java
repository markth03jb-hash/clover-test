package com.ironspellgrimoire.server;

import static com.ironspellgrimoire.server.BossKit.*;
import static com.ironspellgrimoire.server.FxUtil.*;

import com.ironspellgrimoire.entity.CurseWitch;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * CurseWitch's fight - Curse Magic. She floats above the ground, laughing.
 * <ul>
 *   <li>Curse Bullets: quick shots that each lay a curse on you. Every curse slows you and weakens your blows; at five
 *       the hex goes off - you're held fast for two seconds and it hurts. Curses fade one every six seconds.</li>
 *   <li>Ash Absorbing Formation: a circle under you that drains whoever stands in it and feeds her. Step out.</li>
 *   <li>Heehoehoehoe: she cackles and sprays curse bullets in spirals all round her.</li>
 *   <li>From half health, two familiars circle her and shoot too, and she blinks about.</li>
 * </ul>
 */
public final class CurseWitchAI {
    private CurseWitchAI() {}

    private static final String WHO = "The Curse Witch";
    private static final ChatFormatting COL = ChatFormatting.DARK_PURPLE;
    private static final int CURSE = 24, FORM = Fx.DARK;
    private static final UUID SLOW = UUID.fromString("ca7e0001-0000-4000-8000-0000000000f1");
    private static final UUID WEAK = UUID.fromString("ca7e0001-0000-4000-8000-0000000000f2");
    private static final Map<UUID, int[]> CURSES = new HashMap<>();   // player -> {stacks, ticks to the next fade}

    public static void tick(CurseWitch b) {
        ServerLevel sl = (ServerLevel) b.level();
        long now = sl.getGameTime();
        // she floats a little off the ground
        b.setNoGravity(true);
        Vec3 g = ground(sl, b.position().add(0, 0.5, 0));
        double want = g.y + 2.2 + Math.sin(now * 0.08) * 0.25;
        b.setDeltaMovement(b.getDeltaMovement().x, (want - b.getY()) * 0.2, b.getDeltaMovement().z);
        curses(b, sl, now);
        if (b.phase() >= 2) familiars(b, sl, now);
        Player t = target(b);
        if (t == null) { b.getNavigation().stop(); return; }
        if (!b.getPersistentData().getBoolean("isg_met")) {
            b.getPersistentData().putBoolean("isg_met", true);
            say(b, WHO, COL, "Heehoehoehoe! A visitor in the forest? Stay, stay - let me curse you properly!");
            sl.playSound(null, b.blockPosition(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 1.4f, 1.2f);
        }
        b.getLookControl().setLookAt(t, 30, 30);
        if (b.busy > 0) { b.busy--; return; }
        keep(b, t, 9);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int took;
        if (b.phase() >= 2 && b.distanceTo(t) < 4 && r.nextBoolean()) took = blink(b, t);
        else took = switch (r.nextInt(5)) {
            case 0, 1 -> bullets(b, t);
            case 2, 3 -> b.distanceTo(t) < 18 ? formation(b, t) : bullets(b, t);
            default -> cackle(b, t);
        };
        b.busy = took + (b.phase() == 1 ? 26 : 18);
    }

    // ------------------------------------------------------------------ the curse
    static void curse(CurseWitch b, Player p, int n) {
        int[] c = CURSES.computeIfAbsent(p.getUUID(), k -> new int[]{0, 120});
        c[0] += n;
        c[1] = 120;
        if (c[0] >= 5) {   // the hex goes off
            c[0] = 0;
            TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, SLOW, "Curse", -0.9, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
            hurt(b, p, 6f);
            ServerLevel sl = (ServerLevel) b.level();
            FxSend.ring(sl, CURSE, p.position().add(0, 0.1, 0), 1.6f, 0.3f, 0.1f, 14);
            Kit.orb(sl, FORM, p.position().add(0, 1, 0), Vec3.ZERO, 0.8f, 10);
            sl.playSound(null, p.blockPosition(), SoundEvents.WITCH_DRINK, SoundSource.HOSTILE, 1.2f, 0.6f);
            p.displayClientMessage(Component.literal("HEXED!").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), true);
        }
        apply(p, c[0]);
    }

    private static void apply(Player p, int n) {
        if (n <= 0) return;
        TimedModifiers.apply(p, Attributes.MOVEMENT_SPEED, SLOW, "Curse", -0.06 * n, AttributeModifier.Operation.MULTIPLY_TOTAL, 130);
        TimedModifiers.apply(p, Attributes.ATTACK_DAMAGE, WEAK, "Curse", -0.05 * n, AttributeModifier.Operation.MULTIPLY_TOTAL, 130);
    }

    private static void curses(CurseWitch b, ServerLevel sl, long now) {
        for (Map.Entry<UUID, int[]> e : CURSES.entrySet()) {
            int[] c = e.getValue();
            if (c[0] <= 0) continue;
            Player p = sl.getPlayerByUUID(e.getKey());
            if (--c[1] <= 0) { c[0]--; c[1] = 120; if (p != null) apply(p, c[0]); }
            if (p != null && now % 20 == 0)
                p.displayClientMessage(Component.literal("Curse " + "■".repeat(c[0]) + "□".repeat(5 - c[0])).withStyle(ChatFormatting.DARK_PURPLE), true);
        }
    }

    // ------------------------------------------------------------------ moves
    private static void bullet(CurseWitch b, ServerLevel sl, Vec3 at, Vec3 d, double speed, int left, float dmg, boolean[] done) {
        if (left <= 0 || done[0] || !b.isAlive()) return;
        AstaFx.later(1, () -> {
            Vec3 next = at.add(d.scale(speed));
            Vec3 wall = clip(sl, b, at, next);
            Kit.orb(sl, CURSE, at, d.scale(speed), 0.22f, 2);
            for (Player p : along(b, at, next, 0.8)) { hurt(b, p, dmg); curse(b, p, 1); done[0] = true; }
            if (done[0] || wall.distanceToSqr(next) > 1e-4) return;
            bullet(b, sl, next, d, speed, left - 1, dmg, done);
        });
    }

    /** Curse Bullets: three quick shots, one after another. */
    static int bullets(CurseWitch b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_CAST);
        b.getNavigation().stop();
        for (int i = 0; i < 3; i++) {
            AstaFx.later(6 + i * 6, () -> {
                if (!ok(b) || !t.isAlive()) return;
                Vec3 from = b.position().add(0, 1.4, 0);
                Vec3 d = mid(t).subtract(from);
                if (d.lengthSqr() < 1e-4) return;
                FxSend.trail(sl, WARN, from, from.add(d.normalize().scale(4)), 0.06f, 4);
                bullet(b, sl, from, d.normalize(), 1.1, 24, 4f, new boolean[1]);
                sl.playSound(null, b.blockPosition(), SoundEvents.WITCH_THROW, SoundSource.HOSTILE, 1f, 1.4f);
            });
        }
        return 22;
    }

    /** Ash Absorbing Formation: a circle under you that drains whoever stands in it - and feeds her. */
    static int formation(CurseWitch b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_RAISE);
        b.getNavigation().stop();
        Vec3 c = ground(sl, t.position());
        int T = 18;
        warnCircle(sl, c, 2.8f, T);
        AstaFx.later(T, () -> {
            if (!ok(b)) return;
            for (int k = 0; k < 6; k++) {
                final int kk = k;
                AstaFx.later(k * 10, () -> {
                    if (!ok(b)) return;
                    FxSend.ring(sl, FORM, c.add(0, 0.06, 0), 2.7f, 2.8f, 0.06f, 12);
                    FxSend.ring(sl, CURSE, c.add(0, 0.08 + kk * 0.2, 0), 2.6f, 0.4f, 0.05f, 10);
                    for (Player p : near(b, c.add(0, 1, 0), 2.8)) {
                        hurt(b, p, 2f);
                        b.heal(2f);
                        FxSend.trail(sl, FORM, mid(p), b.position().add(0, 1.2, 0), 0.1f, 8);
                    }
                });
            }
            sl.playSound(null, c.x, c.y, c.z, SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.HOSTILE, 1.2f, 0.7f);
        });
        return T + 10;
    }

    /** Heehoehoehoe: spirals of curse bullets all round her. */
    static int cackle(CurseWitch b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setPose(P_LAUGH);
        b.getNavigation().stop();
        int T = 14;
        for (Player p : near(b, b.position(), 30)) p.displayClientMessage(Component.literal("\"Heehoehoehoe!\"").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), true);
        FxSend.ring(sl, WARN, b.position().add(0, 1.4, 0), 0.3f, 2.5f, 0.06f, T);
        sl.playSound(null, b.blockPosition(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 1.6f, 1.4f);
        int waves = b.phase() >= 2 ? 4 : 3, per = 12;
        for (int w = 0; w < waves; w++) {
            final int ww = w;
            AstaFx.later(T + w * 8, () -> {
                if (!ok(b)) return;
                Vec3 from = b.position().add(0, 1.3, 0);
                for (int i = 0; i < per; i++) {
                    double a = i * Math.PI * 2 / per + ww * 0.26;
                    bullet(b, sl, from, new Vec3(Math.cos(a), -0.08, Math.sin(a)), 0.7, 26, 3f, new boolean[1]);
                }
            });
        }
        return T + waves * 8 + 6;
    }

    static int blink(CurseWitch b, Player t) {
        ServerLevel sl = (ServerLevel) b.level();
        Vec3 side = rightOf(dirTo(b, t)).scale(ThreadLocalRandom.current().nextBoolean() ? 7 : -7);
        Vec3 dest = SpaceFx.safe(sl, b.position().add(side), b);
        if (dest == null) return 6;
        Kit.orb(sl, FORM, b.position().add(0, 1, 0), Vec3.ZERO, 0.6f, 8);
        SpaceFx.move(b, dest, b.getYRot());
        Kit.orb(sl, FORM, dest.add(0, 1, 0), Vec3.ZERO, 0.6f, 8);
        sl.playSound(null, b.blockPosition(), SoundEvents.WITCH_AMBIENT, SoundSource.HOSTILE, 1f, 1.6f);
        return 6;
    }

    /** Phase 2: two familiars circle her and shoot curse bullets of their own. */
    private static void familiars(CurseWitch b, ServerLevel sl, long now) {
        for (int i = 0; i < 2; i++) {
            double a = now * 0.06 + i * Math.PI;
            Vec3 at = b.position().add(Math.cos(a) * 2.2, 1.6 + Math.sin(now * 0.1 + i) * 0.3, Math.sin(a) * 2.2);
            if (now % 2 == 0) Kit.orb(sl, FORM, at, Vec3.ZERO, 0.3f, 3);
            if (now % 50 == i * 25) {
                Player t = target(b);
                if (t != null && t.distanceTo(b) < 24) {
                    Vec3 d = mid(t).subtract(at);
                    if (d.lengthSqr() > 1e-4) bullet(b, sl, at, d.normalize(), 1.0, 26, 3f, new boolean[1]);
                }
            }
        }
    }

    public static void onHurt(CurseWitch b, DamageSource src, float amount) {
        if (src.getEntity() instanceof Player p && valid(b, p)) b.focus = p.getUUID();
    }

    public static float taken(CurseWitch b, DamageSource src) { return 1f; }

    public static void phase(CurseWitch b, int phase) {
        ServerLevel sl = (ServerLevel) b.level();
        b.setTransforming(40);
        b.setPose(P_LAUGH);
        say(b, WHO, COL, "Oh-ho! You're a tough one! Then my little ones will help - heehoehoe!");
        FxSend.ring(sl, CURSE, b.position().add(0, 0.1, 0), 0.5f, 8f, 0.25f, 16);
        FxSend.impactFrame(sl, b.position().add(0, 1.5, 0), 3, FxSend.FRAME_YAMI, 22);
        sl.playSound(null, b.blockPosition(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 1.8f, 0.8f);
    }

    public static void dying(CurseWitch b, int t) {
        ServerLevel sl = (ServerLevel) b.level();
        if (t == 1) {
            b.setNoGravity(false);
            say(b, WHO, COL, "Heehoe... hoe... my curses... they're lifting...");
            for (UUID u : CURSES.keySet()) { Player p = sl.getPlayerByUUID(u); if (p != null) apply(p, 0); }
            CURSES.clear();
            sl.playSound(null, b.blockPosition(), SoundEvents.WITCH_DEATH, SoundSource.HOSTILE, 1.4f, 0.8f);
        }
        if (t < 60 && t % 4 == 0) Kit.orb(sl, CURSE, b.position().add(0, 1 + ThreadLocalRandom.current().nextDouble(), 0), new Vec3(0, 0.06, 0), 0.25f, 12);
    }
}
