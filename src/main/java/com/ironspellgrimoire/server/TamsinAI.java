package com.ironspellgrimoire.server;

import com.ironspellgrimoire.entity.TamsinDuelist;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Tamsin Reed's bout (entity/TamsinDuelist): a hedge-mage who keeps her distance.
 * <ul>
 *   <li><b>Thornbolt:</b> a green bolt down the line to you (it's marked for a moment first).</li>
 *   <li><b>Bramble:</b> a circle under you; stay in it and roots hold you (slowed hard for 2 s) and scratch.</li>
 *   <li><b>Slip:</b> get close and she's gone - six blocks back, leaving a ring of leaves.</li>
 *   <li><b>Half health:</b> she stops holding back - faster bolts, two Brambles at once.</li>
 * </ul>
 * She yields at the last of her health (YieldingBoss); the story takes it from there (StoryArc2).
 */
public final class TamsinAI {
    private TamsinAI() {}

    static final UUID ROOTED = UUID.fromString("7a451000-0000-4000-8000-000000000001");
    static final ChatFormatting COL = ChatFormatting.GREEN;
    static final String WHO = "Tamsin Reed";

    static Vec3 chest(TamsinDuelist t) { return t.position().add(0, 1.3, 0); }

    public static void tick(TamsinDuelist t) {
        ServerLevel sl = (ServerLevel) t.level();
        if (t.yielded()) { t.getNavigation().stop(); return; }
        Player p = BossKit.target(t);
        if (p == null) { t.getNavigation().stop(); return; }
        t.getLookControl().setLookAt(p, 30f, 30f);
        if (t.busy > 0) { t.busy--; t.getNavigation().stop(); return; }
        BossKit.keep(t, p, 7.5);
        boolean hard = t.phase() >= 2;
        if (--t.blinkCd <= 0 && t.distanceTo(p) < 3.2) { t.blinkCd = hard ? 60 : 90; slip(t, p, sl); return; }
        if (--t.rootCd <= 0) { t.rootCd = hard ? 110 : 150; bramble(t, p, sl, hard); return; }
        if (--t.boltCd <= 0) { t.boltCd = hard ? 22 : 34; bolt(t, p, sl); }
    }

    static void bolt(TamsinDuelist t, Player p, ServerLevel sl) {
        Vec3 a = chest(t), dir = p.position().add(0, 1, 0).subtract(a).normalize();
        Vec3 c = a.add(dir.scale(18));
        BossKit.warnLine(sl, t.position().add(0, 0.1, 0), new Vec3(c.x, t.getY() + 0.1, c.z), 0.9f, 10);
        t.busy = 8;
        t.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        AstaFx.later(10, () -> {
            if (!t.isAlive() || t.yielded()) return;
            BossFx.trail(sl, Fx.TREE, a, c, 0.35f, 8);
            for (Player h : BossKit.along(t, a, c, 1.1)) BossKit.hurt(t, h, 5f);
            sl.playSound(null, t.blockPosition(), SoundEvents.SWEET_BERRY_BUSH_BREAK, SoundSource.HOSTILE, 1.2f, 0.7f);
        });
    }

    static void bramble(TamsinDuelist t, Player p, ServerLevel sl, boolean twice) {
        BossKit.say(t, WHO, COL, twice ? "Stay down. Please, just stay down -" : "Sorry - sorry!");
        for (Player h : BossKit.near(t, t.position(), 24)) {
            for (int i = 0; i < (twice ? 2 : 1); i++) {
                Vec3 at = BossKit.ground(sl, i == 0 ? h.position() : h.position().add(h.getDeltaMovement().scale(12)));
                BossKit.warnCircle(sl, at, 1.8f, 20);
                AstaFx.later(20, () -> {
                    if (!t.isAlive() || t.yielded()) return;
                    BossFx.ring(sl, Fx.TREE, at.add(0, 0.1, 0), 0.3f, 1.9f, 0.1f, 14);
                    for (Player v : BossKit.near(t, at, 1.9)) {
                        BossKit.hurt(t, v, 4f);
                        TimedModifiers.apply(v, Attributes.MOVEMENT_SPEED, ROOTED, "Bramble", -0.8, AttributeModifier.Operation.MULTIPLY_TOTAL, 40);
                    }
                });
            }
        }
    }

    static void slip(TamsinDuelist t, Player p, ServerLevel sl) {
        Vec3 away = t.position().subtract(p.position()); away = new Vec3(away.x, 0, away.z);
        if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
        Vec3 to = BossKit.ground(sl, t.position().add(away.normalize().scale(6)));
        BossFx.ring(sl, Fx.TREE, t.position().add(0, 0.1, 0), 0.2f, 1.6f, 0.06f, 10);
        t.teleportTo(to.x, to.y, to.z);
        BossFx.ring(sl, Fx.TREE, to.add(0, 0.1, 0), 1.6f, 0.2f, 0.06f, 10);
        sl.playSound(null, t.blockPosition(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.HOSTILE, 1.4f, 1.2f);
    }

    public static void phase(TamsinDuelist t, int phase) {
        if (!(t.level() instanceof ServerLevel sl)) return;
        t.setTransforming(20);
        BossKit.say(t, WHO, COL, "I'm not going back to that village. I'm NOT. Not to the ash -");
        BossFx.ring(sl, Fx.TREE, t.position().add(0, 0.1, 0), 0.4f, 5f, 0.1f, 14);
    }

    public static void yielded(TamsinDuelist t) {
        if (t.level() instanceof ServerLevel sl) BossFx.ring(sl, Fx.TREE, t.position().add(0, 0.1, 0), 2.5f, 0.2f, 0.08f, 14);
        BossKit.say(t, WHO, COL, "...Okay. Okay, you win. Good fight. You'll - you'll buy me a drink after, yes?");
        StoryArc2.tamsinYielded(t);
    }
}
