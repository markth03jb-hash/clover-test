package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.server.GrimoireMastery;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The grimoire tower ceremony, seen by everyone nearby (FxPacket shape 45: id = the player, c = the altar's top,
 * palette = the grimoire that chooses them, life = length):
 *   rise    every grimoire lifts out of the altar and spreads into a ring above it
 *   circle  the ring turns, faster and faster, the books bobbing and tilting, motes drifting up
 *   choose  one slows, leaves the ring and glides down to the player; the others rise away and fade
 *   bond    it hovers before them, turns once, and goes into their hands
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class GrimoireCeremonyRenderer {
    private GrimoireCeremonyRenderer() {}

    private static final class Rite { int player, pick; Vec3 top; double start; int life; }
    private static final List<Rite> RITES = new ArrayList<>();

    public static void start(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Rite r = new Rite();
        r.player = p.id; r.pick = p.palette & 0x1F; r.top = p.c; r.start = mc.level.getGameTime(); r.life = Math.max(20, p.life);
        RITES.add(r);
    }

    private static float ease(float x) { x = Mth.clamp(x, 0F, 1F); return x * x * (3F - 2F * x); }

    /** Where book i is at time t (0..1), and its scale. */
    private static double[] place(Rite r, int i, int n, float t, Vec3 hold) {
        float rise = ease(t / 0.15F);
        float spin = t * t * 14F;                       // accelerating turn
        double ang = i * Math.PI * 2 / n + spin;
        double R = 2.2 * rise, y = 0.6 + 2.0 * rise + Math.sin(t * 30 + i) * 0.12;
        Vec3 ring = r.top.add(Math.cos(ang) * R, y, Math.sin(ang) * R);
        double scale = 0.55;
        if (t > 0.6F) {
            float k = ease((t - 0.6F) / 0.18F);
            if (i == r.pick) {
                Vec3 to = hold;
                if (t > 0.86F) {   // into the hands
                    float h = ease((t - 0.86F) / 0.14F);
                    to = hold.add(0, -0.45 * h, 0);
                    scale = 0.55 * (1.0 - 0.6 * h);
                }
                ring = ring.add(to.subtract(ring).scale(k));
            } else {
                ring = ring.add(0, 3.0 * k, 0);
                scale = 0.55 * (1.0 - k);
            }
        }
        return new double[]{ring.x, ring.y, ring.z, scale, ang};
    }

    private static Vec3 holdPos(Entity e, float pt) {
        Vec3 base = e.getPosition(pt);
        float yaw = e instanceof net.minecraft.world.entity.LivingEntity le ? le.getViewYRot(pt) : e.getYRot();
        double r = Math.toRadians(yaw);
        return base.add(-Math.sin(r) * 0.9, e.getBbHeight() * 0.75, Math.cos(r) * 0.9);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { RITES.clear(); return; }
        double now = mc.level.getGameTime();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Iterator<Rite> it = RITES.iterator(); it.hasNext(); ) {
            Rite r = it.next();
            float t = (float) ((now - r.start) / r.life);
            if (t > 1F) { it.remove(); continue; }
            Entity pl = mc.level.getEntity(r.player);
            Vec3 hold = pl == null ? r.top.add(0, 1.5, 0) : holdPos(pl, 1F);
            int n = GrimoireMastery.BOOKS.size();
            for (int i = 0; i < n; i++) {   // motes drifting off the books
                if (rnd.nextInt(3) != 0) continue;
                double[] q = place(r, i, n, t, hold);
                if (q[3] < 0.05) continue;
                DarkSmoke.mote(new Vec3(q[0], q[1], q[2]).add(rnd.nextGaussian() * 0.15, rnd.nextGaussian() * 0.15, rnd.nextGaussian() * 0.15),
                        new Vec3(0, 0.02, 0), 0.05F, 10);
            }
            if (rnd.nextInt(2) == 0) {      // a column of light motes rising off the altar
                DarkSmoke.mote(r.top.add(rnd.nextGaussian() * 0.25, 0, rnd.nextGaussian() * 0.25), new Vec3(0, 0.06, 0), 0.06F, 18);
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || RITES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick();
        double now = mc.level.getGameTime() + pt;
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        int n = GrimoireMastery.BOOKS.size();
        for (Rite r : RITES) {
            float t = (float) Mth.clamp((now - r.start) / r.life, 0.0, 1.0);
            Entity pl = mc.level.getEntity(r.player);
            Vec3 hold = pl == null ? r.top.add(0, 1.5, 0) : holdPos(pl, pt);
            for (int i = 0; i < n; i++) {
                double[] q = place(r, i, n, t, hold);
                if (q[3] < 0.03) continue;
                ItemStack stack = new ItemStack(GrimoireMastery.BOOKS.get(i).item().get());
                ps.pushPose();
                ps.translate(q[0] - cam.x, q[1] - cam.y, q[2] - cam.z);
                boolean chosen = i == r.pick && t > 0.6F;
                // facing out of the ring while it turns; the chosen one turns to face its new owner
                float yaw = chosen ? (float) (now * 8.0) : (float) Math.toDegrees(-q[4]) + 90F;
                ps.mulPose(Axis.YP.rotationDegrees(yaw));
                ps.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(now * 0.2 + i) * 10F));
                float s = (float) q[3];
                ps.scale(s, s, s);
                mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, i);
                ps.popPose();
            }
        }
        buf.endBatch();
    }

    // ------------------------------------------------------------------ the glow: light drawn with the rest of the effects
    private static void circle(Matrix4f m, Vec3 cam, Vec3 c, double rad, int seg, double spin, double dash, float w, int pal, float a) {
        if (rad < 0.02 || a <= 0.01F) return;
        for (int i = 0; i < seg; i++) {
            double t0 = Math.PI * 2 * i / seg + spin, t1 = t0 + Math.PI * 2 / seg * dash;
            FxBase.gline(m, cam, c.add(Math.cos(t0) * rad, 0, Math.sin(t0) * rad), c.add(Math.cos(t1) * rad, 0, Math.sin(t1) * rad), w, pal, a);
        }
    }

    /**
     * What the grimoire ceremony adds in light: a column standing up from the altar, a rune circle on the ground
     * and a turning crown of light under the ring of books, pulses of light rolling out of the altar, a comet tail
     * behind the chosen book, a burst of rays the moment it is chosen, a beam to its new owner, and a ring closing
     * round them as it goes into their hands. All in the colour of the grimoire that chose.
     */
    static void glow(Matrix4f m, Vec3 cam, double now, float pt) {
        if (RITES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int nb = GrimoireMastery.BOOKS.size();
        for (Rite r : RITES) {
            float t = (float) Mth.clamp((now - r.start) / r.life, 0.0, 1.0);
            double age = now - r.start;
            int pal = GrimoireMastery.BOOKS.get(Mth.clamp(r.pick, 0, nb - 1)).palette();
            Entity pl = mc.level.getEntity(r.player);
            Vec3 hold = pl == null ? r.top.add(0, 1.5, 0) : holdPos(pl, pt);
            float rise = ease(t / 0.15F);
            float fade = 1F - ease((t - 0.84F) / 0.16F);
            Vec3 base = r.top.add(0, 0.05, 0);
            // the column of light, growing up out of the altar and brightening as the turning speeds up
            float col = rise * fade * (0.45F + 0.4F * t);
            FxBase.gline(m, cam, base, base.add(0, 9.0 * rise, 0), 0.05F + 0.1F * t, 8, col);
            FxBase.gline(m, cam, base, base.add(0, 6.0 * rise, 0), 0.2F + 0.15F * t, pal, col * 0.55F);
            // the rune circle on the ground, turning faster as the rite goes on
            double spin = age * 0.02 + t * t * 9.0;
            circle(m, cam, base, 2.6 * rise, 56, spin, 0.6, 0.03F, pal, 0.9F * fade);
            circle(m, cam, base, 2.1 * rise, 40, -spin * 1.3, 0.5, 0.025F, 8, 0.8F * fade);
            circle(m, cam, base, 1.0 * rise, 28, spin * 2.0, 0.55, 0.02F, pal, 0.7F * fade);
            for (int i = 0; i < 16; i++) {   // ticks between the two big circles
                double ang = Math.PI * 2 * i / 16 + spin;
                Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                FxBase.gline(m, cam, base.add(dir.scale(2.15 * rise)), base.add(dir.scale(2.55 * rise)), 0.02F, 8, 0.7F * fade);
            }
            // a crown of light under the books
            Vec3 crown = r.top.add(0, 2.6 * rise, 0);
            circle(m, cam, crown, 2.2 * rise, 64, -spin * 0.6, 0.35, 0.025F, pal, 0.55F * fade);
            // pulses rolling out of the altar along the ground
            for (int k = 0; k < 3; k++) {
                double ph = ((age * 0.045) + k / 3.0) % 1.0;
                circle(m, cam, base, 0.4 + ph * 4.5, 48, 0, 0.9, 0.04F * (1F - (float) ph), pal, 0.8F * (1F - (float) ph) * rise * fade);
            }
            // the comet tail behind the book that will choose you
            if (t > 0.3F && t < 0.9F) {
                double[] prev = place(r, r.pick, nb, t, hold);
                Vec3 pp = new Vec3(prev[0], prev[1], prev[2]);
                for (int s = 1; s <= 10; s++) {
                    double[] q = place(r, r.pick, nb, Math.max(0F, t - s * 0.012F), hold);
                    Vec3 qq = new Vec3(q[0], q[1], q[2]);
                    float f = 1F - s / 10F;
                    FxBase.gline(m, cam, pp, qq, 0.09F * f, pal, 0.9F * f * fade);
                    pp = qq;
                }
            }
            // chosen: a burst of rays, a shock ring, and a beam to its new owner
            if (t > 0.6F) {
                double[] ch = place(r, r.pick, nb, t, hold);
                Vec3 cp = new Vec3(ch[0], ch[1], ch[2]);
                float k = (t - 0.6F) / 0.12F;
                if (k < 1F) {
                    float ek = 1F - k;
                    for (int i = 0; i < 14; i++) {
                        double ang = Math.PI * 2 * i / 14 + 0.4, el = Math.sin(i * 1.7) * 0.7;
                        Vec3 d = new Vec3(Math.cos(ang) * Math.cos(el), Math.sin(el), Math.sin(ang) * Math.cos(el));
                        FxBase.gline(m, cam, cp.add(d.scale(0.3 + 0.8 * k)), cp.add(d.scale(0.5 + 3.0 * k)), 0.05F * ek, i % 2 == 0 ? 8 : pal, ek);
                    }
                    circle(m, cam, cp, 0.4 + 3.2 * k, 40, 0, 0.9, 0.07F * ek, 8, ek);
                }
                if (t < 0.9F) {
                    float bk = Mth.clamp((t - 0.6F) / 0.26F, 0F, 1F);
                    Vec3 to = cp.add(hold.subtract(cp).scale(0.0)), from = r.top.add(0, 1.0, 0);
                    FxBase.gline(m, cam, from, cp, 0.03F, pal, 0.35F * fade);   // the thread between the altar and the chosen book
                }
            }
            // bonded: a ring closes round the hand and a flash of rays
            if (t > 0.86F) {
                float k = (t - 0.86F) / 0.14F;
                Vec3 hp = hold.add(0, -0.45 * ease(k), 0);
                circle(m, cam, hp.add(0, -0.3, 0), 1.6 * (1F - k) + 0.2, 40, age * 0.3, 0.7, 0.05F * (1F - k), 8, 1F - k);
                circle(m, cam, hp.add(0, 0.3, 0), 1.2 * (1F - k) + 0.2, 32, -age * 0.3, 0.7, 0.04F * (1F - k), pal, 1F - k);
                for (int i = 0; i < 10; i++) {
                    double ang = Math.PI * 2 * i / 10 + age * 0.15;
                    Vec3 d = new Vec3(Math.cos(ang), 0.9, Math.sin(ang)).normalize();
                    FxBase.gline(m, cam, hp.add(d.scale(0.25)), hp.add(d.scale(0.25 + 1.6 * k)), 0.04F * (1F - k), pal, 1F - k);
                }
            }
        }
    }
}
