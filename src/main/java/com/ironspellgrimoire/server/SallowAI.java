package com.ironspellgrimoire.server;

import com.ironspellgrimoire.entity.FatherSallow;
import com.ironspellgrimoire.entity.Penitent;
import com.ironspellgrimoire.registry.AddonEntities;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Father Sallow's fight (entity/FatherSallow), in his chapel (ASHFALL, Arc I).
 * <ul>
 *   <li><b>The knife:</b> close, a hooked cut - and the one it cuts loses 4 armour for 6 s (Excised).</li>
 *   <li><b>Excision:</b> the line to you is marked, then he lunges down it, cutting everyone in the way.</li>
 *   <li><b>The lantern of brands:</b> it swings, and embers rain where you stand (marked first).</li>
 *   <li><b>Stage 2</b> (two thirds): he calls his faithful up from the pews (two at a time, four at most).</li>
 *   <li><b>Stage 3</b> (a third): Ilsa breaks out; he opens the lantern - rings of fire around him, one after another.</li>
 *   <li>Brought to the last of his health he doesn't die: he yields, and the party decides (StoryArc1).</li>
 * </ul>
 */
public final class SallowAI {
    private SallowAI() {}

    static final UUID EXCISED = UUID.fromString("5a110000-0000-4000-8000-000000000011"), FERVOUR = UUID.fromString("5a110000-0000-4000-8000-000000000012");
    static final ChatFormatting COL = ChatFormatting.DARK_RED;
    static final String WHO = "Father Sallow";

    static Vec3 chest(FatherSallow b) { return b.position().add(0, b.getBbHeight() * 0.62, 0); }

    public static void tick(FatherSallow b) {
        ServerLevel sl = (ServerLevel) b.level();
        if (b.yielded()) { b.getNavigation().stop(); b.setDeltaMovement(0, Math.min(0, b.getDeltaMovement().y), 0); return; }
        Player p = BossKit.target(b);
        if (p == null) { b.getNavigation().stop(); return; }
        b.getLookControl().setLookAt(p, 30f, 30f);
        double d = b.distanceTo(p);
        if (b.busy > 0) { b.busy--; b.getNavigation().stop(); return; }
        if (d > 2.6) b.getNavigation().moveTo(p, 1.0); else b.getNavigation().stop();
        // the knife
        if (--b.knifeCd <= 0 && d < 3.4) {
            b.knifeCd = 28;
            b.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            BossKit.hurt(b, p, 7f);
            TimedModifiers.apply(p, Attributes.ARMOR, EXCISED, "Excised", -4, AttributeModifier.Operation.ADDITION, 120);
            Vec3 dir = p.position().subtract(b.position()); dir = new Vec3(dir.x, 0, dir.z);
            if (dir.lengthSqr() > 1e-4) BossFx.slash(sl, Fx.SILVER, chest(b).add(dir.normalize().scale(1.2)), dir.normalize(), new Vec3(0, 1, 0), 1.6f, 1.1f, 6, 1.2f);
            sl.playSound(null, b.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1f, 0.8f);
            return;
        }
        // Excision: a marked lunge
        if (--b.pillarCd <= 0 && d > 4 && d < 14 && b.phase() < 3) {
            b.pillarCd = 150;
            excision(b, p, sl);
            return;
        }
        if (b.phase() >= 3 && b.pillarCd <= 0) { b.pillarCd = 200; freeing(b, sl); return; }
        // the lantern
        if (--b.lanternCd <= 0) {
            b.lanternCd = b.phase() >= 2 ? 120 : 170;
            lantern(b, sl);
            return;
        }
        // the faithful
        if (b.phase() >= 2 && --b.callCd <= 0) {
            b.callCd = 360;
            call(b, sl);
        }
    }

    static void excision(FatherSallow b, Player p, ServerLevel sl) {
        Vec3 a = b.position().add(0, 0.1, 0);
        Vec3 dir = p.position().subtract(b.position()); dir = new Vec3(dir.x, 0, dir.z).normalize();
        Vec3 c = a.add(dir.scale(Math.min(10, b.distanceTo(p) + 2)));
        int T = 16;
        BossKit.warnLine(sl, a, c, 1.4f, T);
        BossKit.say(b, WHO, COL, "Hold still.");
        b.busy = T + 4;
        Vec3 end = c;
        AstaFx.later(T, () -> {
            if (!b.isAlive() || b.yielded()) return;
            Vec3 from = chest(b);
            b.teleportTo(end.x, b.getY(), end.z);
            BossFx.trail(sl, Fx.SILVER, from, chest(b), 0.5f, 8);
            for (Player t : BossKit.along(b, from, chest(b), 1.4)) BossKit.hurt(b, t, 9f);
            sl.playSound(null, b.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.4f, 0.6f);
        });
    }

    /** The lantern swings: embers come down where each of you stands (marked first). */
    static void lantern(FatherSallow b, ServerLevel sl) {
        int T = 22;
        b.busy = 8;
        BossFx.beam(sl, Fx.FIRE, chest(b), chest(b).add(0, 3, 0), 0.4f, 10);
        sl.playSound(null, b.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 1.2f, 0.6f);
        for (Player p : BossKit.near(b, b.position(), 24)) {
            for (int i = 0; i < (b.phase() >= 2 ? 2 : 1); i++) {
                Vec3 at = BossKit.ground(sl, p.position().add(i == 0 ? 0 : ThreadLocalRandom.current().nextDouble(-3, 3), 0, i == 0 ? 0 : ThreadLocalRandom.current().nextDouble(-3, 3)));
                BossKit.warnCircle(sl, at, 1.8f, T);
                AstaFx.later(T, () -> {
                    if (!b.isAlive() || b.yielded()) return;
                    BossFx.beam(sl, Fx.FIRE, at.add(0, 5, 0), at, 0.6f, 8);
                    BossFx.ring(sl, Fx.FIRE, at.add(0, 0.1, 0), 0.3f, 1.9f, 0.08f, 10);
                    for (Player t : BossKit.near(b, at, 1.9)) BossKit.hurt(b, t, 6f);
                });
            }
        }
    }

    static void call(FatherSallow b, ServerLevel sl) {
        int alive = sl.getEntitiesOfClass(Penitent.class, b.getBoundingBox().inflate(40), m -> m.isAlive() && m.getPersistentData().getInt("isg_story") == b.party).size();
        if (alive >= 4) return;
        BossKit.say(b, WHO, COL, "Rise, my freed. Rise and hold them.");
        Story.Party pt = Story.byId(sl.getServer(), b.party);
        if (pt == null) return;
        for (int i = 0; i < 2; i++) {
            Penitent m = AddonEntities.PENITENT.get().create(sl);
            if (m == null) continue;
            Vec3 at = StoryStage.at(pt.id, StoryStage.CHAPEL, (i == 0 ? -1 : 1) * 4, 0, 24 + ThreadLocalRandom.current().nextInt(4));
            m.moveTo(at.x, at.y, at.z, 180, 0);
            StoryArc1.tag(m, pt, StoryStage.CHAPEL);
            sl.addFreshEntity(m);
            BossFx.ring(sl, Fx.FIRE, at.add(0, 0.1, 0), 0.2f, 1.3f, 0.05f, 10);
        }
    }

    /** Stage 3: the lantern opens - rings of fire around him, the inner one first, then the outer. */
    static void freeing(FatherSallow b, ServerLevel sl) {
        BossKit.say(b, WHO, COL, "Burn, then! Burn like the rest of them!");
        b.busy = 30;
        Vec3 c = b.position();
        for (int ring = 0; ring < 2; ring++) {
            int n = ring == 0 ? 6 : 10;
            double r = ring == 0 ? 4.5 : 8.5;
            int T = 22 + ring * 14;
            double off = ThreadLocalRandom.current().nextDouble(Math.PI);
            for (int i = 0; i < n; i++) {
                double a = off + i * Math.PI * 2 / n;
                Vec3 at = BossKit.ground(sl, c.add(Math.cos(a) * r, 0, Math.sin(a) * r));
                BossKit.warnCircle(sl, at, 1.7f, T);
                AstaFx.later(T, () -> {
                    if (!b.isAlive() || b.yielded()) return;
                    BossFx.beam(sl, Fx.FIRE, at, at.add(0, 4.5, 0), 0.7f, 10);
                    for (Player t : BossKit.near(b, at, 1.8)) BossKit.hurt(b, t, 8f);
                });
            }
        }
        sl.playSound(null, b.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.4f, 0.5f);
    }

    public static void phase(FatherSallow b, int phase) {
        if (!(b.level() instanceof ServerLevel sl)) return;
        b.setTransforming(30);
        BossFx.ring(sl, Fx.FIRE, b.position().add(0, 0.1, 0), 0.4f, 6f, 0.12f, 14);
        if (phase == 2) BossKit.say(b, WHO, COL, "Your Brand is beautiful. I'll keep it in the lantern with the others.");
        if (phase == 3) {
            BossKit.say(b, WHO, COL, "Ilsa? Ilsa, what are you - no. NO. Stay in your cell!");
            var a = b.getAttribute(Attributes.MOVEMENT_SPEED);
            if (a != null && a.getModifier(FERVOUR) == null) a.addPermanentModifier(new AttributeModifier(FERVOUR, "Sallow's fervour", 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL));
            b.pillarCd = 40;
            StoryArc1.ilsaFlees(b);
        }
    }

    /** Brought to the last of his health: he kneels, and the party decides. */
    public static void yieldNow(FatherSallow b) {
        b.setYielded(true);
        b.getNavigation().stop();
        if (b.level() instanceof ServerLevel sl) {
            BossFx.ring(sl, Fx.FIRE, b.position().add(0, 0.1, 0), 3f, 0.2f, 0.08f, 16);
            sl.playSound(null, b.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 1.2f, 0.5f);
        }
        StoryArc1.sallowYielded(b);
    }

    public static void dying(FatherSallow b, int t) {
        if (t != 1 || !(b.level() instanceof ServerLevel sl)) return;
        BossFx.ring(sl, Fx.FIRE, b.position().add(0, 0.1, 0), 0.3f, 5f, 0.1f, 20);
    }
}
