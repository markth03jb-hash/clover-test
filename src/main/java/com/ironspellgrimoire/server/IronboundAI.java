package com.ironspellgrimoire.server;

import com.ironspellgrimoire.entity.IronboundTwin;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Ironbound Twins' fight (entity/IronboundTwin).
 * <ul>
 *   <li><b>The chain:</b> pull them more than 8 blocks apart and it goes taut (a steel line between them): they take
 *       50% more, and every third blow while it's taut staggers them both for 1.5 s. Kept within 4 blocks, they guard
 *       each other: 20% less taken, 25% harder blows.</li>
 *   <li><b>Oswin</b> goes for you; <b>Haldric</b> keeps close to his brother, and bashes with his shield (thrown back).</li>
 *   <li><b>Chain Sweep:</b> the line between them is marked, then the chain whips along it.</li>
 *   <li><b>Stage 2</b> (half health): faster, the Sweep more often, and Haldric hauls his brother back to him when the
 *       chain runs long.</li>
 * </ul>
 */
public final class IronboundAI {
    private IronboundAI() {}

    static final UUID RAGE = UUID.fromString("f01c0000-0000-4000-8000-000000000021");
    public static final float TAUT = 8f, CLOSE = 4f;
    static final ChatFormatting COL = ChatFormatting.GRAY;

    static Vec3 chest(IronboundTwin t) { return t.position().add(0, t.getBbHeight() * 0.62, 0); }
    public static double apart(IronboundTwin t) { IronboundTwin o = t.other(); return o == null ? 0 : o.distanceTo(t); }

    public static void tick(IronboundTwin t) {
        ServerLevel sl = (ServerLevel) t.level();
        IronboundTwin o = t.other();
        if (o == null || !o.isAlive()) {   // alone: the other half is gone (a loaded world without it) - it fights on alone
            if (!t.isMain() && t.tickCount > 40) t.discard();
        }
        if (!t.isMain() && o != null) t.setHealth(Math.max(1f, o.getHealth() / o.getMaxHealth() * t.getMaxHealth()));   // one health, shown on both
        long now = sl.getGameTime();
        if (now < t.staggerUntil) { t.getNavigation().stop(); t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0); return; }
        double d = apart(t);
        if (t.isMain() && o != null && now % 4 == 0 && d > TAUT) BossFx.beam(sl, Fx.STEEL, chest(t), chest(o), 0.25f, 5);
        Player p = BossKit.target(t);
        if (p == null) { t.getNavigation().stop(); return; }
        // moving: Oswin to you, Haldric to his brother when the chain runs long
        if (t.isMain() && o != null && d > 6) t.getNavigation().moveTo(o, 1.05);
        else if (t.distanceTo(p) > 2.6) t.getNavigation().moveTo(p, 1.0);
        else t.getNavigation().stop();
        t.getLookControl().setLookAt(p, 30f, 30f);
        if (t.isMain() && o != null && t.phase() >= 2 && d > 12 && now % 40 == 0) {   // he hauls his brother back
            Vec3 to = t.position().add(o.position().subtract(t.position()).normalize().scale(2.5));
            BossFx.beam(sl, Fx.STEEL, chest(t), chest(o), 0.5f, 8);
            o.teleportTo(to.x, to.y, to.z);
            sl.playSound(null, t.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.4f, 0.6f);
        }
        float close = o != null && d < CLOSE ? 1.25f : 1f;
        // a blow
        if (--t.swingCd <= 0 && t.distanceTo(p) < 3.4) {
            t.swingCd = 26;
            t.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            BossKit.hurt(t, p, (float) t.getAttributeValue(Attributes.ATTACK_DAMAGE) * close);
            sl.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1f, 0.7f);
        }
        // Haldric's shield bash
        if (t.isMain() && --t.bashCd <= 0 && t.distanceTo(p) < 3.6) {
            t.bashCd = 160;
            BossKit.hurt(t, p, 6f * close);
            Vec3 away = p.position().subtract(t.position()); away = new Vec3(away.x, 0, away.z);
            if (away.lengthSqr() > 1e-4) { p.setDeltaMovement(away.normalize().scale(1.5).add(0, 0.45, 0)); p.hurtMarked = true; }
            BossFx.ring(sl, Fx.STEEL, p.position().add(0, 1, 0), 0.3f, 1.6f, 0.06f, 8);
            sl.playSound(null, t.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.4f, 0.6f);
        }
        // the Chain Sweep
        if (t.isMain() && o != null && --t.sweepCd <= 0 && d > 3 && d < 15) {
            t.sweepCd = t.phase() >= 2 ? 120 : 200;
            sweep(t, o, sl);
        }
    }

    static void sweep(IronboundTwin h, IronboundTwin o, ServerLevel sl) {
        Vec3 a = h.position().add(0, 0.1, 0), b = o.position().add(0, 0.1, 0);
        int T = 24;
        BossKit.warnLine(sl, a, b, 1.4f, T);
        BossKit.say(h, "Ser Haldric", COL, "Brother - the chain!");
        AstaFx.later(T, () -> {
            if (!h.isAlive() || !o.isAlive()) return;
            Vec3 a2 = chest(h), b2 = chest(o);
            BossFx.beam(sl, Fx.STEEL, a2, b2, 0.6f, 10);
            sl.playSound(null, h.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 2f, 0.5f);
            Vec3 ab = b.subtract(a);
            double len2 = ab.lengthSqr();
            for (Player p : sl.players()) {
                if (!BossKit.valid(h, p)) continue;
                Vec3 ap = p.position().subtract(a);
                double k = len2 < 1e-4 ? 0 : Math.max(0, Math.min(1, ap.dot(ab) / len2));
                if (p.position().distanceTo(a.add(ab.scale(k))) < 1.6) BossKit.hurt(h, p, 11f);
            }
        });
    }

    /** The chain: taut, they take more (and every third blow staggers them both); close, they take less. */
    public static float taken(IronboundTwin t, DamageSource src) {
        IronboundTwin o = t.other();
        double d = apart(t);
        if (o != null && d > TAUT) {
            if (++t.tautHits % 3 == 0) {
                long until = t.level().getGameTime() + 30;
                t.staggerUntil = until; o.staggerUntil = until;
                if (t.level() instanceof ServerLevel sl) {
                    BossFx.beam(sl, Fx.GOLD, chest(t), chest(o), 0.5f, 8);
                    sl.playSound(null, t.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.6f, 1.2f);
                }
            }
            return 1.5f;
        }
        return o != null && d < CLOSE ? 0.8f : 1f;
    }

    public static void phase(IronboundTwin t, int phase) {
        if (!(t.level() instanceof ServerLevel sl)) return;
        t.setTransforming(30);
        BossKit.say(t, "Ser Oswin", COL, "Together, brother. As we swore.");
        for (IronboundTwin x : new IronboundTwin[]{t, t.other()}) {
            if (x == null) continue;
            var a = x.getAttribute(Attributes.MOVEMENT_SPEED);
            if (a != null && a.getModifier(RAGE) == null) a.addPermanentModifier(new AttributeModifier(RAGE, "Ironbound", 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL));
            BossFx.ring(sl, Fx.STEEL, x.position().add(0, 0.1, 0), 0.4f, 6f, 0.12f, 14);
        }
    }

    public static void dying(IronboundTwin t, int tick) {
        if (tick != 1 || !(t.level() instanceof ServerLevel sl)) return;
        IronboundTwin o = t.other();
        if (t.isMain()) {
            BossKit.say(t, "Ser Haldric", COL, "The chain... holds...");
            if (o != null && o.isAlive()) { BossFx.ring(sl, Fx.STEEL, o.position().add(0, 0.1, 0), 2f, 0.2f, 0.08f, 16); o.discard(); }
        }
    }

    /** For the tests. */
    public static void testStagger(IronboundTwin t) { t.tautHits = 2; }
}
