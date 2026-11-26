package com.ironspellgrimoire.server;

import com.ironspellgrimoire.entity.MotherBelow;
import com.ironspellgrimoire.entity.WildFolk;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Mother Below's fight (entity/MotherBelow), under Ashwick (ASHFALL, Arc III).
 * <ul>
 *   <li><b>Rake:</b> close, her talons sweep in front of her (a wide arc, a long reach).</li>
 *   <li><b>Rising Light:</b> circles under each of you; then teal light comes up out of the ground.</li>
 *   <li><b>The Legs:</b> her four back limbs stab down in a ring round her (marked) - step in close, or well out.</li>
 *   <li><b>Brood:</b> she sheds Hollow crawlers (two at a time, five at most).</li>
 *   <li><b>Deep Song</b> (her last quarter): lines of light run out from her in a star - and after it, her heart
 *       stands bare for 4 s: she takes 25% more.</li>
 * </ul>
 * At half health, the first time, she goes down through the floor (StoryArc3); the second half starts there.
 */
public final class MotherAI {
    private MotherAI() {}

    static final ChatFormatting COL = ChatFormatting.DARK_AQUA;
    static final String WHO = "The Mother Below";
    static final int TEAL = Fx.ICE;

    static Vec3 chest(MotherBelow b) { return b.position().add(0, b.getBbHeight() * 0.6, 0); }

    public static float taken(MotherBelow b, DamageSource src) { return b.level().getGameTime() < b.bareUntil ? 1.25f : 1f; }

    public static void tick(MotherBelow b) {
        ServerLevel sl = (ServerLevel) b.level();
        Player p = BossKit.target(b);
        if (p == null) { b.getNavigation().stop(); return; }
        b.getLookControl().setLookAt(p, 30f, 30f);
        if (sl.getGameTime() % 30 == 0) BossFx.ring(sl, TEAL, b.position().add(0, 0.08, 0), 2.2f, 2.4f, 0.04f, 28);   // the light under her
        if (b.busy > 0) { b.busy--; b.getNavigation().stop(); return; }
        double d = b.distanceTo(p);
        if (d > 3.2) b.getNavigation().moveTo(p, 1.0); else b.getNavigation().stop();
        int ph = b.phase();
        if (--b.rakeCd <= 0 && d < 4.4) { b.rakeCd = ph >= 2 ? 26 : 34; rake(b, p, sl); return; }
        if (ph >= 3 && --b.songCd <= 0) { b.songCd = 260; song(b, sl); return; }
        if (--b.stabCd <= 0) { b.stabCd = ph >= 2 ? 130 : 170; legs(b, sl); return; }
        if (--b.pillarCd <= 0) { b.pillarCd = ph >= 2 ? 90 : 120; rising(b, sl); return; }
        if (--b.broodCd <= 0) { b.broodCd = 300; brood(b, sl); }
    }

    static void rake(MotherBelow b, Player p, ServerLevel sl) {
        Vec3 dir = BossKit.dirTo(b, p);
        b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        BossFx.slash(sl, TEAL, chest(b).add(dir.scale(1.8)), dir, new Vec3(0, 1, 0), 3.2f, 2.4f, 8, 1.4f);
        for (Player t : BossKit.near(b, b.position().add(dir.scale(2.2)), 3.0)) BossKit.hurt(b, t, 10f);
        sl.playSound(null, b.blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.4f, 0.6f);
        b.busy = 8;
    }

    static void rising(MotherBelow b, ServerLevel sl) {
        int T = 22;
        for (Player h : BossKit.near(b, b.position(), 28)) {
            Vec3 at = BossKit.ground(sl, h.position());
            BossKit.warnCircle(sl, at, 2f, T);
            AstaFx.later(T, () -> {
                if (!b.isAlive()) return;
                BossFx.beam(sl, TEAL, at, at.add(0, 5, 0), 0.8f, 10);
                for (Player v : BossKit.near(b, at, 2.1)) BossKit.hurt(b, v, 9f);
            });
        }
        sl.playSound(null, b.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 1.2f, 0.6f);
        b.busy = 10;
    }

    static void legs(MotherBelow b, ServerLevel sl) {
        int T = 20;
        Vec3 c = b.position();
        double off = ThreadLocalRandom.current().nextDouble(Math.PI);
        for (int i = 0; i < 8; i++) {
            double a = off + i * Math.PI / 4;
            Vec3 at = BossKit.ground(sl, c.add(Math.cos(a) * 5.5, 1, Math.sin(a) * 5.5));
            BossKit.warnCircle(sl, at, 1.8f, T);
            AstaFx.later(T, () -> {
                if (!b.isAlive()) return;
                BossFx.beam(sl, Fx.STEEL, chest(b).add(0, 2, 0), at, 0.45f, 8);
                for (Player v : BossKit.near(b, at, 1.9)) BossKit.hurt(b, v, 8f);
            });
        }
        BossKit.say(b, WHO, COL, "Hold still, little sparks. The deep is so very cold.");
        b.busy = T + 4;
    }

    static void brood(MotherBelow b, ServerLevel sl) {
        int alive = sl.getEntitiesOfClass(WildFolk.class, b.getBoundingBox().inflate(32), m -> m.isAlive() && m.getPersistentData().getInt("isg_story") == b.party).size();
        if (alive >= 5) return;
        Story.Party pt = Story.byId(sl.getServer(), b.party);
        for (int i = 0; i < 2; i++) {
            WildFolk m = WildFolkAI.type(WildFolk.Kind.HOLLOW_CRAWLER).create(sl);
            if (m == null) continue;
            Vec3 at = BossKit.ground(sl, b.position().add(i == 0 ? -3 : 3, 1, 2));
            m.moveTo(at.x, at.y, at.z, 0, 0);
            if (pt != null) StoryArc1.tag(m, pt, b.getPersistentData().getInt("isg_place"));
            m.getPersistentData().putInt("isg_bout", b.party);
            sl.addFreshEntity(m);
            BossFx.ring(sl, TEAL, at.add(0, 0.1, 0), 0.2f, 1.4f, 0.05f, 10);
        }
    }

    static void song(MotherBelow b, ServerLevel sl) {
        BossKit.say(b, WHO, COL, "Listen. LISTEN. That is the sound the world makes from underneath.");
        int T = 28;
        Vec3 c = b.position().add(0, 0.1, 0);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + ThreadLocalRandom.current().nextDouble(0.5);
            Vec3 e = c.add(Math.cos(a) * 18, 0, Math.sin(a) * 18);
            BossKit.warnLine(sl, c, e, 1.6f, T);
            AstaFx.later(T, () -> {
                if (!b.isAlive()) return;
                BossFx.beam(sl, TEAL, chest(b), e.add(0, 1, 0), 0.7f, 10);
                for (Player t : BossKit.along(b, c.add(0, 1, 0), e.add(0, 1, 0), 1.6)) BossKit.hurt(b, t, 10f);
            });
        }
        AstaFx.later(T + 2, () -> {
            if (!b.isAlive()) return;
            b.bareUntil = sl.getGameTime() + 80;
            BossFx.ring(sl, TEAL, chest(b), 0.3f, 2.2f, 0.1f, 14);
            sl.playSound(null, b.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 2f, 0.5f);
        });
        b.busy = T + 6;
    }

    public static void phase(MotherBelow b, int phase) {
        if (!(b.level() instanceof ServerLevel sl)) return;
        b.setTransforming(30);
        BossFx.ring(sl, TEAL, b.position().add(0, 0.1, 0), 0.4f, 8f, 0.12f, 18);
        FxSend.shake(sl, b.position(), 0.6f, 20, 40);
        if (phase == 2 && b.half == 1) { StoryArc3.motherHalf(b); return; }
        BossKit.say(b, WHO, COL, phase == 2 ? "You followed me down. Good. The deep keeps everything that falls into it."
                : "My children are under every field you have ever walked on. Every one.");
    }

    public static void dying(MotherBelow b, int t) {
        if (t != 1 || !(b.level() instanceof ServerLevel sl)) return;
        BossKit.say(b, WHO, COL, "The light... is so... loud...");
        BossFx.ring(sl, TEAL, b.position().add(0, 0.1, 0), 0.3f, 9f, 0.12f, 24);
        StoryArc3.motherFell(b);
    }
}
