package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Julius Novachrono's Time Magic, drawn here in the ink-on-paper look of a manga page. Every piece is one packet and is
 * animated on the client, so it moves smoothly and costs almost nothing on the network.
 *
 *  shape 49  the page column: hundreds of ink-edged pages stand on end in a ring, twisted into a spiral so they all
 *            sweep in to one bright point on top. It fans open in a wave both ways round the ring (FWIP), the pages
 *            flutter, and when it closes they scatter outward like a book thrown open.
 *            id, c = centre of the base, r = radius, r2 = height, life, sweep = ticks to open, h = style
 *            (0 a column, 1 the Sovereign's: a second ring of gold pages turning the other way inside, 2 a small cage
 *            round one target), a0 = flutter, speed = how fast the ring turns, v = (entity id, 1, 0) to follow one
 *  shape 50  freeze a column: id = the column, life = ticks (0 = let go now). Its pages hang perfectly still.
 *  shape 51  a clock face: id, c, d = the way it faces, u = its twelve o'clock, r, life, h = style (0 the great clock
 *            in the sky, 1 a trap on the ground, 2 a small clock beside you, 3 a clock running backwards),
 *            speed = minute hand (radians per tick, negative = backwards), sweep = ticks per tick of the hand
 *            (0 = smooth), a0 = marks on the trap, a1/r2 = when the hands stop and for how long
 *  shape 52  a clock hand flying: id, c = start, v = velocity per tick, d = which way it points, r = length, life,
 *            h = style (0 a bolt, 1 a giant hand falling from the sky). A kill packet ends it with a flash.
 *  shape 53  a gold afterimage of a player: id, c, a0 = yaw, life, h = style (0 fades, 1 the Echo: it pulses),
 *            sweep = pose (0 stand, 1 cast, 2 arms raised, 3 stride), v.x = the player, a1 = how solid
 *  shape 54  a frozen target: id = the entity, life = ticks (0 = let go). A stopped clock at its feet, pages hanging
 *            round it and an hourglass over its head - and when time starts again the pages scatter.
 *  shape 55  stored time on your HUD: life = seconds (0..10), sweep = 1 while you hold the book
 *  shape 57  a time bubble round one trapped target (the Ticking Snare): id = the entity, life = ticks. A glassy blue
 *            sphere with a ring of numerals tumbling round it; it pops when the time is up.
 *  shape 56  your screen while time stops (h 0) or runs back (h 1): life = ticks
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class TimeFxClient {
    private TimeFxClient() {}

    private static final double TAU = Math.PI * 2;
    static final float[] PAPER = {0.97F, 0.96F, 0.9F}, COLD = {0.84F, 0.89F, 1.0F}, INK = {0.05F, 0.05F, 0.09F},
            GOLD = {0.3F, 0.86F, 1.0F}, GOLD_HI = {0.8F, 0.97F, 1.0F}, GOLD_PAPER = {0.72F, 0.92F, 1.0F}, NAVY = {0.04F, 0.06F, 0.17F};

    // ================================================================== state
    private static final class Column {
        int id, style, life, open, follow = -1, n;
        Vec3 c, lastC;
        float r, h, flutter, spin;
        double born, closeAt = -1, frozenUntil = -1, flT, flPrev, spT, spPrev;
        float[] jit;
        boolean scatterSounded;
    }

    private static final class Clock {
        int id, style, life, step, marks, lastTick = -1, follow = -1;
        double followY;
        Vec3 c, n, up, right;
        float r, speed, stopAt, stopFor;
        double born;
    }

    private static final class Hand {
        int id, style, life;
        Vec3 c, v, d;
        float len;
        double born, killedAt = -1;
    }

    private static final class Ghost {
        int id, style, life, pose, owner;
        Vec3 c;
        float yaw, alpha;
        double born;
    }

    private static final List<Column> COLUMNS = new ArrayList<>();
    private static final List<Clock> CLOCKS = new ArrayList<>();
    private static final List<Hand> HANDS = new ArrayList<>();
    private static final List<Ghost> GHOSTS = new ArrayList<>();
    /** The Chronostasis Field and Zenith Hour realms. */
    private static final class Realm {
        int id, style, life, follow = -1, lockAt;
        Vec3 c;
        float r, h;
        double born, closeAt = -1;
    }
    private static final List<Realm> REALMS = new ArrayList<>();
    /** entity id -> {start, end of the freeze} */
    private static final Map<Integer, double[]> MARKS = new HashMap<>();
    private static final Map<Integer, double[]> BUBBLES = new HashMap<>();
    /** the open Time grimoires this frame: {x, y, z, open 0..1} */
    private static final List<double[]> FANS = new ArrayList<>();
    private static final List<Runnable> GLOWS = new ArrayList<>();

    private static int stored = 0;
    private static double storedShown = -1000;   // when the stored-time arc last changed: it only shows for a few seconds
    private static boolean holding = false;
    private static double scrStart = -1, scrEnd = -1;
    private static int scrStyle = 0;

    // ================================================================== packets
    public static void spawn(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = mc.level.getGameTime();
        switch (p.shape) {
            case 49 -> {
                Column k = new Column();
                k.id = p.id; k.style = (int) p.h; k.life = Math.max(6, p.life); k.open = Math.max(1, p.sweep);
                k.c = p.c; k.lastC = p.c; k.r = Math.max(0.3F, p.r); k.h = Math.max(0.3F, p.r2);
                k.flutter = p.a0; k.spin = p.speed; k.born = now;
                if (p.v.y > 0.5) k.follow = (int) Math.round(p.v.x);
                k.n = Mth.clamp((int) (k.r * 26), k.style == 2 ? 16 : 28, 420);
                Random r = new Random(p.id * 7919L + 13);
                k.jit = new float[k.n];
                for (int i = 0; i < k.n; i++) k.jit[i] = r.nextFloat();
                COLUMNS.add(k);
            }
            case 58 -> {
                Realm k = new Realm();
                k.id = p.id; k.style = (int) p.h; k.life = Math.max(10, p.life); k.lockAt = p.sweep;
                k.c = p.c; k.r = Math.max(2F, p.r); k.h = Math.max(2F, p.r2); k.born = now;
                if (p.v.y > 0.5) k.follow = (int) Math.round(p.v.x);
                REALMS.add(k);
            }
            case 50 -> {
                for (Column k : COLUMNS) if (k.id == p.id) k.frozenUntil = p.life > 0 ? now + p.life : now;
            }
            case 51 -> {
                Clock k = new Clock();
                k.id = p.id; k.style = (int) p.h; k.life = Math.max(4, p.life); k.step = p.sweep; k.marks = (int) p.a0;
                k.c = p.c; k.r = Math.max(0.2F, p.r); k.speed = p.speed; k.stopAt = p.a1; k.stopFor = p.r2; k.born = now;
                k.n = p.d.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : p.d.normalize();
                Vec3 up = p.u.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, -1) : p.u.normalize();
                up = up.subtract(k.n.scale(up.dot(k.n)));
                if (up.lengthSqr() < 1.0E-6) up = Math.abs(k.n.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(0, 0, -1);
                k.up = up.normalize();
                k.right = k.up.cross(k.n).normalize();
                if (p.v.y > 0.5) { k.follow = (int) Math.round(p.v.x); k.followY = p.v.z; }
                CLOCKS.add(k);
            }
            case 52 -> {
                Hand k = new Hand();
                k.id = p.id; k.style = (int) p.h; k.life = Math.max(1, p.life); k.c = p.c; k.v = p.v; k.len = Math.max(0.2F, p.r); k.born = now;
                Vec3 d = p.d.lengthSqr() > 1.0E-6 ? p.d : p.v;
                k.d = d.lengthSqr() < 1.0E-6 ? new Vec3(0, -1, 0) : d.normalize();
                HANDS.add(k);
            }
            case 53 -> {
                Ghost k = new Ghost();
                k.id = p.id; k.style = (int) p.h; k.life = Math.max(2, p.life); k.pose = p.sweep; k.owner = (int) Math.round(p.v.x);
                k.c = p.c; k.yaw = p.a0; k.alpha = p.a1 > 0 ? p.a1 : 0.6F; k.born = now;
                GHOSTS.add(k);
            }
            case 54 -> {
                if (p.life > 0) {
                    double[] f = MARKS.get(p.id);
                    if (f == null || f[1] < now) MARKS.put(p.id, new double[]{now, now + p.life});
                    else f[1] = Math.max(f[1], now + p.life);
                } else {
                    double[] f = MARKS.get(p.id);
                    if (f != null) f[1] = Math.min(f[1], now);
                }
            }
            case 57 -> {
                BUBBLES.put(p.id, new double[]{now, now + Math.max(4, p.life)});
            }
            case 55 -> {
                int ns = Mth.clamp(p.life, 0, 10);
                if (ns != stored && Minecraft.getInstance().level != null) storedShown = Minecraft.getInstance().level.getGameTime();
                stored = ns; holding = p.sweep == 1;
            }
            case 56 -> {
                if (p.life > 0) { scrStart = now; scrEnd = now + p.life; scrStyle = (int) p.h; }
                else scrEnd = Math.min(scrEnd, now);
            }
            default -> {}
        }
    }

    /** A kill packet: a column scatters, a hand ends in a flash. True if it was one of ours. */
    public static boolean kill(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        double now = mc.level.getGameTime();
        boolean any = false;
        for (Column k : COLUMNS) if (k.id == id && k.closeAt < 0) { k.closeAt = now; k.frozenUntil = -1; any = true; }
        for (Hand k : HANDS) if (k.id == id && k.killedAt < 0) { k.killedAt = now; any = true; }
        for (Clock k : CLOCKS) if (k.id == id) { k.life = (int) Math.min(k.life, now - k.born + 6); any = true; }
        for (Realm k : REALMS) if (k.id == id && k.closeAt < 0) { k.closeAt = now; any = true; }
        return any;
    }

    /** Called by the floating-book renderer for every open Time grimoire, each frame. */
    public static void bookFan(Vec3 pos, float open, float scale, float castCycle) {
        FANS.add(new double[]{pos.x, pos.y, pos.z, open, scale, castCycle});
    }

    // ================================================================== ticking: flutter, sounds, clean-up
    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            COLUMNS.clear(); REALMS.clear(); CLOCKS.clear(); HANDS.clear(); GHOSTS.clear(); MARKS.clear(); BUBBLES.clear(); stored = 0; holding = false;
            return;
        }
        if (mc.isPaused()) return;
        double now = mc.level.getGameTime();
        Random rnd = new Random();
        for (Iterator<Column> it = COLUMNS.iterator(); it.hasNext(); ) {
            Column k = it.next();
            double close = closeStart(k), age = now - k.born;
            if (now > close + 16) { it.remove(); continue; }
            k.flPrev = k.flT; k.spPrev = k.spT;
            if (now >= k.frozenUntil) { k.flT += 1; k.spT += k.spin; }
            Vec3 c = k.lastC;
            if (age <= k.open && age % 2 == 0) {   // the rings click into place one after another, like a ratchet
                double frac = Mth.clamp(age / k.open, 0, 1);
                mc.level.playLocalSound(c.x, c.y + k.h * frac, c.z, SoundEvents.COMPARATOR_CLICK, SoundSource.PLAYERS,
                        (float) Math.min(1.0, 0.35 + k.r * 0.07), 1.2F + (float) frac * 0.8F, false);
            }
            if (now >= close && !k.scatterSounded) {
                k.scatterSounded = true;
                for (int i = 0; i < 3; i++)
                    mc.level.playLocalSound(c.x, c.y + k.h * 0.5, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                            (float) Math.min(1.0, 0.4 + k.r * 0.06), 0.6F + i * 0.35F, false);
            }
        }
        for (Iterator<Clock> it = CLOCKS.iterator(); it.hasNext(); ) {
            Clock k = it.next();
            double age = now - k.born;
            if (age > k.life + 1) { it.remove(); continue; }
            if (k.step > 0) {
                int idx = (int) Math.floor(ageEff(k, age) / k.step);
                if (idx != k.lastTick) {
                    if (k.lastTick >= 0) mc.level.playLocalSound(k.c.x, k.c.y, k.c.z, SoundEvents.COMPARATOR_CLICK, SoundSource.PLAYERS,
                            k.style == 1 ? 1.0F : 0.6F, k.style == 1 ? 1.3F + idx * 0.15F : 1.8F, false);
                    k.lastTick = idx;
                }
            }
        }
        HANDS.removeIf(k -> (k.killedAt >= 0 && now - k.killedAt > 6) || now - k.born > k.life + 6);
        GHOSTS.removeIf(k -> now - k.born > k.life + 1);
        MARKS.values().removeIf(f -> now > f[1] + 8);
        BUBBLES.values().removeIf(f -> now > f[1] + 8);
        if (scrEnd >= 0 && now > scrEnd + 8) scrEnd = scrStart = -1;
    }

    private static double closeStart(Column k) {
        double end = k.born + k.life;
        return k.closeAt >= 0 ? Math.min(end, k.closeAt) : end;
    }

    private static double ageEff(Clock k, double age) {
        if (k.stopFor <= 0 || age <= k.stopAt) return age;
        return age < k.stopAt + k.stopFor ? k.stopAt : age - k.stopFor;
    }

    // ================================================================== drawing helpers (world)
    private static BufferBuilder BB;
    private static Matrix4f M;
    private static Vec3 CAM = Vec3.ZERO;
    private static float PT = 0F;

    private static void begin() {
        BB = Tesselator.getInstance().getBuilder();
        BB.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }

    private static void end() { BufferUploader.drawWithShader(BB.end()); }

    private static void v(Vec3 p, float[] c, float a) {
        BB.vertex(M, (float) (p.x - CAM.x), (float) (p.y - CAM.y), (float) (p.z - CAM.z)).color(c[0], c[1], c[2], a).endVertex();
    }

    private static void tri(Vec3 a, Vec3 b, Vec3 c, float[] col, float al) { v(a, col, al); v(b, col, al); v(c, col, al); }

    private static void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float[] col, float al) {
        v(a, col, al); v(b, col, al); v(c, col, al);
        v(a, col, al); v(c, col, al); v(d, col, al);
    }

    /** A ribbon from a to b facing the camera, w wide, lifted toward the camera so it sits on top of what it outlines. */
    private static void line(Vec3 a, Vec3 b, float w, float[] col, float al, double lift) {
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 tc = CAM.subtract(mid);
        double dist = tc.length();
        if (dist < 1.0E-4) return;
        tc = tc.scale(1.0 / dist);
        Vec3 side = b.subtract(a).cross(tc);
        if (side.lengthSqr() < 1.0E-12) return;
        side = side.normalize().scale(w * 0.5);
        Vec3 off = tc.scale(lift);
        quad(a.subtract(side).add(off), a.add(side).add(off), b.add(side).add(off), b.subtract(side).add(off), col, al);
    }

    /** A stroke lying in a plane with normal n (on a clock face). */
    private static void stroke(Vec3 a, Vec3 b, Vec3 n, float w, float[] col, float al, Vec3 lift) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1.0E-12) return;
        Vec3 side = n.cross(d).normalize().scale(w * 0.5);
        quad(a.subtract(side).add(lift), a.add(side).add(lift), b.add(side).add(lift), b.subtract(side).add(lift), col, al);
    }

    private static Vec3 ring(Vec3 c, double th, double r, double y) { return new Vec3(c.x + Math.cos(th) * r, y, c.z + Math.sin(th) * r); }

    private static float backOut(float x) {
        float k = Mth.clamp(x, 0F, 1F) - 1F;
        return 1F + 2.70158F * k * k * k + 1.70158F * k * k;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    private static float[] mul(float[] c, float k) { return new float[]{c[0] * k, c[1] * k, c[2] * k}; }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    // ================================================================== world render
    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { FANS.clear(); return; }
        boolean any = !REALMS.isEmpty() || !COLUMNS.isEmpty() || !CLOCKS.isEmpty() || !HANDS.isEmpty() || !MARKS.isEmpty() || !BUBBLES.isEmpty() || !FANS.isEmpty();
        float pt = e.getPartialTick();
        PT = pt;
        double now = mc.level.getGameTime() + pt;
        CAM = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        if (!GHOSTS.isEmpty()) ghosts(mc, ps, now);
        if (!any) return;
        M = ps.last().pose();
        GLOWS.clear();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        begin();
        REALMS.removeIf(k -> now - k.born > k.life + 14 || (k.closeAt >= 0 && now - k.closeAt > 14));
        for (Realm k : REALMS) realm(mc, k, now, pt);
        for (Column k : COLUMNS) column(mc, k, now, pt);
        for (Clock k : CLOCKS) clock(k, now);
        for (Hand k : HANDS) hand(k, now);
        for (Map.Entry<Integer, double[]> m : MARKS.entrySet()) mark(mc, m.getKey(), m.getValue(), now, pt);
        for (double[] f : FANS) fan(f, now);
        for (Map.Entry<Integer, double[]> m : BUBBLES.entrySet()) bubble(mc, m.getKey(), m.getValue(), now, pt);
        end();
        FANS.clear();

        // the light: additive glows over everything above
        if (!GLOWS.isEmpty()) {
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            begin();
            for (Runnable r : GLOWS) r.run();
            end();
            GLOWS.clear();
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }


    // ================================================================== realms: the Chronostasis Field and Zenith Hour
    private static void fadeDisc(Vec3 c, double R, double y, int seg, float[] col, float aC, float aE) {
        Vec3 o = new Vec3(c.x, y, c.z);
        for (int i = 0; i < seg; i++) {
            double a0 = TAU * i / seg, a1 = TAU * (i + 1) / seg;
            v(o, col, aC); v(ring(c, a0, R, y), col, aE); v(ring(c, a1, R, y), col, aE);
        }
    }

    private static void bandXZ(Vec3 c, double r0, double r1, double y, int seg, double rot, float[] col, float a) {
        for (int i = 0; i < seg; i++) {
            double a0 = rot + TAU * i / seg, a1 = rot + TAU * (i + 1) / seg;
            quad(ring(c, a0, r0, y), ring(c, a0, r1, y), ring(c, a1, r1, y), ring(c, a1, r0, y), col, a);
        }
    }

    private static void ticksXZ(Vec3 c, double r0, double r1, double y, int n, int heavyEvery, double rot, float w, float[] col, float a) {
        for (int i = 0; i < n; i++) {
            boolean hv = i % heavyEvery == 0;
            double ang = rot + TAU * i / n;
            stroke(ring(c, ang, r0 - (hv ? (r1 - r0) * 0.5 : 0), y), ring(c, ang, r1, y), UPV, hv ? w * 2.2F : w, col, a, Vec3.ZERO);
        }
    }

    private static final Vec3 UPV = new Vec3(0, 1, 0);

    /** A rising shaft of light: wide at the foot, fading out toward the top. */
    private static void shaft(Vec3 base, double th, float w, float h, float[] col, float a0, float a1) {
        for (int k = 0; k < 2; k++) {
            double t = th + k * Math.PI / 2;
            Vec3 d = new Vec3(Math.cos(t) * w, 0, Math.sin(t) * w);
            Vec3 b0 = base.subtract(d), b1 = base.add(d), t0 = b0.add(0, h, 0), t1 = b1.add(0, h, 0);
            v(b0, col, a0); v(b1, col, a0); v(t1, col, a1);
            v(b0, col, a0); v(t1, col, a1); v(t0, col, a1);
        }
    }

    private static void realm(Minecraft mc, Realm k, double now, float pt) {
        double age = now - k.born;
        double end = k.closeAt >= 0 ? k.closeAt : k.born + k.life;
        float a = (float) Math.min(Mth.clamp(age / 12.0, 0.0, 1.0), Mth.clamp((end + 12 - now) / 12.0, 0.0, 1.0));
        if (a <= 0.01F) return;
        Vec3 c = k.c;
        if (k.follow >= 0 && mc.level != null) {
            Entity en = mc.level.getEntity(k.follow);
            if (en != null) c = new Vec3(Mth.lerp(pt, en.xo, en.getX()), Mth.lerp(pt, en.yo, en.getY()), Mth.lerp(pt, en.zo, en.getZ()));
        }
        double R = k.r * smooth((float) (age / 14.0));
        double y = c.y + 0.06;
        if (k.style == 0) chronoRealm(k, c, R, y, age, now, a);
        else zenithRealm(k, c, R, y, age, now, a);
    }

    /** The Chronostasis Field: a clock lying on the ground under everything inside it, turning slowly, with a dome of time over it. */
    private static void chronoRealm(Realm k, Vec3 c, double R, double y, double age, double now, float a) {
        double t = age;
        fadeDisc(c, R, y, 64, NAVY, 0.55F * a, 0.0F);
        fadeDisc(c, R * 0.98, y + 0.01, 64, GOLD, 0.16F * a, 0.0F);
        bandXZ(c, R * 0.955, R * 1.0, y + 0.02, 96, 0, INK, a * 0.9F);
        bandXZ(c, R * 0.965, R * 0.99, y + 0.03, 96, 0, GOLD, a * 0.85F);
        ticksXZ(c, R * 0.86, R * 0.95, y + 0.03, 60, 5, t * 0.004, (float) (R * 0.01), GOLD_HI, a * 0.9F);
        bandXZ(c, R * 0.74, R * 0.76, y + 0.03, 96, -t * 0.006, GOLD, a * 0.7F);
        for (int i = 0; i < 12; i++) {   // twelve hour studs that drift the other way round the inner ring
            double ang = -t * 0.006 + TAU * i / 12;
            Vec3 p0 = ring(c, ang, R * 0.75, y + 0.04);
            double s = R * 0.035;
            quad(p0.add(s, 0, 0), p0.add(0, 0, s), p0.add(-s, 0, 0), p0.add(0, 0, -s), GOLD_HI, a);
        }
        cog(null, new Vec3(c.x, y + 0.05, c.z), (float) (R * 0.5), t * 0.02, a * 0.8F, 0F, GOLD, GOLD_HI);
        cog(null, new Vec3(c.x, y + 0.12, c.z), (float) (R * 0.26), -t * 0.04, a * 0.9F, 0F, GOLD_HI, PAPER);
        double minute = t * 0.05, hour = t * 0.004;
        stroke(new Vec3(c.x, y + 0.2, c.z), ring(c, minute, R * 0.62, y + 0.2), UPV, (float) (R * 0.02), INK, a, Vec3.ZERO);
        stroke(new Vec3(c.x, y + 0.21, c.z), ring(c, minute, R * 0.6, y + 0.21), UPV, (float) (R * 0.011), PAPER, a, Vec3.ZERO);
        stroke(new Vec3(c.x, y + 0.2, c.z), ring(c, hour, R * 0.38, y + 0.2), UPV, (float) (R * 0.03), INK, a, Vec3.ZERO);
        stroke(new Vec3(c.x, y + 0.21, c.z), ring(c, hour, R * 0.36, y + 0.21), UPV, (float) (R * 0.018), GOLD_HI, a, Vec3.ZERO);
        double rp = (t % 40) / 40.0;   // a ripple of slowed time runs out from the middle every two seconds
        bandXZ(c, R * rp, R * rp + 0.35, y + 0.05, 64, 0, GOLD_HI, a * (float) (1.0 - rp) * 0.7F);
        for (int i = 0; i < 5; i++) {   // five great wheels hang in the air round the edge, turning
            double ang = t * 0.012 + TAU * i / 5;
            double bob = Math.sin(t * 0.05 + i * 1.7) * 0.25;
            cog(null, ring(c, ang, R * 0.86, c.y + 3.6 + bob), 1.5F, (i % 2 == 0 ? 1 : -1) * t * 0.05, a * 0.9F, 0F, GOLD, GOLD_HI);
        }
        // the dome: a see-through shell with a cyan rim of light, and a bright band that climbs it
        double H = k.h;
        boolean inside = CAM.distanceTo(c) < R;
        for (int i = 0; i < 8; i++) {
            double e0 = Math.PI / 2 * i / 8, e1 = Math.PI / 2 * (i + 1) / 8;
            for (int j = 0; j < 36; j++) {
                double a0 = TAU * j / 36, a1 = TAU * (j + 1) / 36;
                Vec3[] q = {FxZones.dome(a0, e0), FxZones.dome(a1, e0), FxZones.dome(a1, e1), FxZones.dome(a0, e1)};
                float[] al = new float[4];
                Vec3[] w = new Vec3[4];
                for (int m = 0; m < 4; m++) {
                    w[m] = new Vec3(c.x + q[m].x * R, c.y + q[m].y * H, c.z + q[m].z * R);
                    float fres = (float) Math.pow(1.0 - Math.abs(q[m].dot(CAM.subtract(w[m]).normalize())), 3.0);
                    double climb = ((t * 0.01) % 1.0);
                    float band = (float) Math.max(0, 1.0 - Math.abs(q[m].y - climb) * 9.0);
                    al[m] = a * ((inside ? 0.02F : 0.05F) + (inside ? 0.05F : 0.22F) * fres + 0.12F * band);
                }
                v(w[0], GOLD, al[0]); v(w[1], GOLD, al[1]); v(w[2], GOLD, al[2]);
                v(w[0], GOLD, al[0]); v(w[2], GOLD, al[2]); v(w[3], GOLD, al[3]);
            }
        }
        final Vec3 cc = c;
        final double tt = t, RR = R;
        final float aa = a;
        GLOWS.add(() -> {   // grains of time drifting up through the field
            for (int i = 0; i < 36; i++) {
                double seed = i * 12.9898;
                double ang = (seed * 43758.5453) % TAU, rad = Math.sqrt(((seed * 7.13) % 1.0 + 1.0) % 1.0) * RR * 0.95;
                double yy = ((tt * 0.018 + i * 0.137) % 1.0) * 5.0;
                float tw = (float) (0.5 + 0.5 * Math.sin(tt * 0.2 + i));
                glowDisc(new Vec3(cc.x + Math.cos(ang) * rad, cc.y + 0.2 + yy, cc.z + Math.sin(ang) * rad), new Vec3(0, 1, 0), 0.06F, GOLD_HI, 0.8F * aa * tw);
            }
            glowDisc(new Vec3(cc.x, cc.y + 0.15, cc.z), new Vec3(0, 1, 0), (float) (RR * 0.9), GOLD, 0.1F * aa);
        });
    }

    /** Zenith Hour: a colossal clock drawn on the ground to the edge of the world, twelve shafts of light at its hours, a beam to the sky clock; the hands stop when time does. */
    private static void zenithRealm(Realm k, Vec3 c, double R, double y, double age, double now, float a) {
        boolean max = k.style == 2;
        double lock = k.lockAt;
        double t = Math.min(age, lock);                 // everything turns until time stops, then hangs still
        double after = Math.max(0, age - lock);
        float hold = after > 0 ? 1F : 0F;
        fadeDisc(c, R, y, 96, NAVY, 0.5F * a, 0.0F);
        fadeDisc(c, R * 0.98, y + 0.01, 96, GOLD, 0.2F * a, 0.02F * a);
        bandXZ(c, R * 0.94, R * 1.0, y + 0.02, 160, 0, INK, a);
        bandXZ(c, R * 0.95, R * 0.985, y + 0.03, 160, 0, GOLD, a * 0.9F);
        bandXZ(c, R * 0.8, R * 0.815, y + 0.03, 160, 0, GOLD, a * 0.6F);
        ticksXZ(c, R * 0.82, R * 0.93, y + 0.03, 60, 5, 0, (float) (R * 0.006), GOLD_HI, a);
        for (int i = 0; i < 12; i++) {   // the twelve hours: big slabs set into the ring
            double ang = TAU * i / 12;
            Vec3 a0 = ring(c, ang, R * 0.83, y + 0.04), a1 = ring(c, ang, R * 0.935, y + 0.04);
            stroke(a0, a1, UPV, (float) (R * 0.035), INK, a, Vec3.ZERO);
            stroke(a0, a1, UPV, (float) (R * 0.022), GOLD_HI, a, Vec3.ZERO);
        }
        cog(null, new Vec3(c.x, y + 0.05, c.z), (float) (R * 0.55), t * 0.01, a * 0.75F, 0F, GOLD, GOLD_HI);
        cog(null, new Vec3(c.x, y + 0.1, c.z), (float) (R * 0.3), -t * 0.02, a * 0.85F, 0F, GOLD_HI, PAPER);
        if (max) cog(null, new Vec3(c.x, y + 0.15, c.z), (float) (R * 0.75), -t * 0.007, a * 0.55F, 0F, GOLD, GOLD_HI);
        double minute = t * 0.09 - 1.4, hour = t * 0.0075 - 1.4;
        stroke(new Vec3(c.x, y + 0.25, c.z), ring(c, minute, R * 0.9, y + 0.25), UPV, (float) (R * 0.03), INK, a, Vec3.ZERO);
        stroke(new Vec3(c.x, y + 0.26, c.z), ring(c, minute, R * 0.88, y + 0.26), UPV, (float) (R * 0.016), PAPER, a, Vec3.ZERO);
        stroke(new Vec3(c.x, y + 0.25, c.z), ring(c, hour, R * 0.55, y + 0.25), UPV, (float) (R * 0.045), INK, a, Vec3.ZERO);
        stroke(new Vec3(c.x, y + 0.26, c.z), ring(c, hour, R * 0.53, y + 0.26), UPV, (float) (R * 0.026), GOLD_HI, a, Vec3.ZERO);
        // twelve shafts of light rise at the hours, one after another; with the Max form a second, inner twelve
        for (int i = 0; i < 12; i++) {
            float up = smooth((float) ((age - 4 - i * 1.2) / 10.0));
            if (up <= 0F) continue;
            double ang = TAU * i / 12;
            shaft(ring(c, ang, R * 0.97, y), ang, (float) (0.5 + R * 0.012), k.h * up, GOLD, 0.55F * a, 0.0F);
            shaft(ring(c, ang, R * 0.97, y), ang + 0.4, 0.18F, k.h * 1.3F * up, GOLD_HI, 0.8F * a, 0.0F);
            if (max) shaft(ring(c, ang + TAU / 24, R * 0.62, y), ang, 0.4F, k.h * 0.8F * up, GOLD_HI, 0.5F * a, 0.0F);
        }
        if (after > 0) {   // when time stops: shock rings roll out across the ground and up the shafts
            for (int i = 0; i < 3; i++) {
                double rr = (after * 2.4 - i * 7) ;
                if (rr <= 0 || rr > R * 1.05) continue;
                bandXZ(c, rr, rr + 0.7 + i * 0.3, y + 0.08, 120, 0, GOLD_HI, a * (float) Math.max(0, 1.0 - rr / (R * 1.05)));
            }
        }
        final Vec3 cc = c;
        final double tt = age;
        final float aa = a;
        final double RR = R, HH = k.h;
        GLOWS.add(() -> {
            glowDisc(new Vec3(cc.x, cc.y + 0.2, cc.z), new Vec3(0, 1, 0), (float) (RR * 0.95), GOLD, 0.12F * aa);
            glowDisc(new Vec3(cc.x, cc.y + HH * 1.5, cc.z), new Vec3(0, 1, 0), (float) (RR * 0.6), GOLD_HI, 0.18F * aa);
            for (int i = 0; i < 70; i++) {   // time dust swirling up round the edge
                double seed = i * 12.9898;
                double ang = TAU * i / 70 + tt * 0.01 * (i % 2 == 0 ? 1 : -1), rad = RR * (0.3 + ((seed * 7.13) % 1.0 + 1.0) % 1.0 * 0.7);
                double yy = ((tt * 0.02 + i * 0.113) % 1.0) * HH;
                glowDisc(new Vec3(cc.x + Math.cos(ang) * rad, cc.y + 0.3 + yy, cc.z + Math.sin(ang) * rad), new Vec3(0, 1, 0), 0.1F, GOLD_HI, 0.7F * aa * (float) (0.5 + 0.5 * Math.sin(tt * 0.15 + i)));
            }
        });
    }

    // ------------------------------------------------------------------ the page column
    private static Vec3 centre(Minecraft mc, Column k, float pt) {
        if (k.follow >= 0 && mc.level != null) {
            Entity en = mc.level.getEntity(k.follow);
            if (en != null) {
                k.lastC = new Vec3(Mth.lerp(pt, en.xo, en.getX()), Mth.lerp(pt, en.yo, en.getY()) - 0.02, Mth.lerp(pt, en.zo, en.getZ()));
                return k.lastC;
            }
        }
        return k.lastC != null ? k.lastC : k.c;
    }

    private static void column(Minecraft mc, Column k, double now, float pt) {
        double age = now - k.born, close = closeStart(k);
        Vec3 c = centre(mc, k, pt);
        boolean frozen = now < k.frozenUntil;
        double sp = frozen ? k.spT : Mth.lerp(pt, k.spPrev, k.spT);
        float s = now >= close ? (float) ((now - close) / 14.0) : 0F;   // 0..1 while it rings out
        if (s >= 1F) return;
        float[] metal = frozen ? COLD : GOLD, hi = frozen ? PAPER : GOLD_HI;
        stack(k, c, k.r, k.h, sp, age, s, metal, hi, frozen, 1);
        if (k.style == 1) stack(k, c, k.r * 0.78F, k.h * 0.92F, -sp * 1.6, age - 4, s, hi, PAPER, frozen, -1);
        if (k.style != 2) {   // a great cog turning at the top and the foot
            float g = (float) Mth.clamp((age - k.open - 1) / 7.0, 0.0, 1.0);
            if (g > 0F) {
                cog(k, c.add(0, k.h, 0), k.r, sp * 0.6, g * (1F - s), s, metal, hi);
                cog(k, c.add(0, 0.02, 0), k.r * 0.9F, -sp * 0.6, g * (1F - s), s, metal, hi);
            }
        }
        final Vec3 cc = c;
        final float R = k.r, H = k.h, ss = s, gr = (float) Mth.clamp(age / 6.0, 0, 1);
        final double ag = age, fz = frozen ? 0 : 1;
        final int id = k.id;
        GLOWS.add(() -> chronoLight(cc, R, H, ss, gr, ag, fz, id));
    }

    /** A stack of clockwork rings - short bands of metal with tick slits - rising round the column, turning in turn. */
    private static void stack(Column k, Vec3 c, float R, float H, double sp, double age, float s, float[] col, float[] hi, boolean frozen, int dir) {
        int m = Mth.clamp(Math.round(H * 1.1F) + 2, 3, 12);
        int seg = Mth.clamp((int) (R * 14), 32, 96);
        float bh = Mth.clamp(0.09F + R * 0.012F, 0.09F, 0.3F);
        for (int j = 0; j < m; j++) {
            float g = (float) Mth.clamp((age - k.open * (j / (double) m)) / 5.0, 0.0, 1.0);
            if (g <= 0F) continue;
            g = backOut(g);
            float al = Math.min(1F, g) * (1F - s);
            if (al <= 0.02F) continue;
            double y = c.y + (H - bh) * (j / (double) (m - 1));
            double rr = R * (0.8 + 0.2 * Math.min(1F, g)) * (1.0 + s * 1.3);
            double rot = (j % 2 == 0 ? 1 : -1) * dir * sp * (1.0 + 0.18 * j) + j * 0.9;
            for (int i = 0; i < seg; i++) {
                double t0 = TAU * i / seg + rot, t1 = TAU * (i + 1) / seg + rot;
                Vec3 p0 = ring(c, t0, rr, y), p1 = ring(c, t1, rr, y), p2 = ring(c, t1, rr, y + bh), p3 = ring(c, t0, rr, y + bh);
                Vec3 nm = new Vec3(Math.cos((t0 + t1) * 0.5), 0, Math.sin((t0 + t1) * 0.5));
                Vec3 tc = CAM.subtract(p0.add(p2).scale(0.5));
                float shade = 0.7F + 0.3F * (float) Math.abs(nm.dot(tc.lengthSqr() < 1.0E-6 ? nm : tc.normalize()));
                quad(p0, p1, p2, p3, mul(col, shade), al);
            }
            // ink edges top and bottom, and a slit every fifteen degrees (a bright tick every thirty)
            for (int i = 0; i < seg; i++) {
                double t0 = TAU * i / seg + rot, t1 = TAU * (i + 1) / seg + rot;
                double e = bh * 0.1;
                quad(ring(c, t0, rr * 1.003, y), ring(c, t1, rr * 1.003, y), ring(c, t1, rr * 1.003, y + e), ring(c, t0, rr * 1.003, y + e), INK, al);
                quad(ring(c, t0, rr * 1.003, y + bh - e), ring(c, t1, rr * 1.003, y + bh - e), ring(c, t1, rr * 1.003, y + bh), ring(c, t0, rr * 1.003, y + bh), INK, al);
            }
            for (int q = 0; q < 24; q++) {
                double t0 = TAU * q / 24 + rot, t1 = t0 + 0.035 * (q % 2 == 0 ? 1.6 : 1.0) / Math.max(0.6, Math.sqrt(R));
                float[] tc2 = q % 2 == 0 ? hi : INK;
                double y0 = y + bh * (q % 2 == 0 ? 0.18 : 0.3), y1 = y + bh * (q % 2 == 0 ? 0.82 : 0.7);
                quad(ring(c, t0, rr * 1.006, y0), ring(c, t1, rr * 1.006, y0), ring(c, t1, rr * 1.006, y1), ring(c, t0, rr * 1.006, y1), tc2, al);
            }
        }
    }

    /** A flat cog with a toothed rim and spokes: metal, ink-edged, turning. */
    private static void cog(Column k, Vec3 c, float R, double sp, float al, float s, float[] col, float[] hi) {
        if (al <= 0.02F) return;
        int T = Mth.clamp((int) (R * 5), 10, 28);
        double w = TAU / T, th = 0.2 * R * (1 + s * 1.3);
        double rr = R * (1 + s * 1.3);
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i < T; i++) {
            double a = sp + i * w;
            pts.add(new double[]{a, 0.93});
            pts.add(new double[]{a + 0.12 * w, 1.1});
            pts.add(new double[]{a + 0.4 * w, 1.1});
            pts.add(new double[]{a + 0.52 * w, 0.93});
        }
        double yb = c.y, yt = c.y + Math.min(0.2, 0.05 + R * 0.02);
        for (int i = 0; i < pts.size(); i++) {
            double[] p = pts.get(i), q = pts.get((i + 1) % pts.size());
            double a1 = q[0] < p[0] ? q[0] + TAU : q[0];
            Vec3 b0 = ring(c, p[0], rr * p[1], yb), b1 = ring(c, a1, rr * q[1], yb), t1 = ring(c, a1, rr * q[1], yt), t0 = ring(c, p[0], rr * p[1], yt);
            quad(b0, b1, t1, t0, mul(col, 0.8F), al);                                // the rim of the tooth
            Vec3 i0 = ring(c, p[0], rr * 0.62, yt), i1 = ring(c, a1, rr * 0.62, yt);
            quad(i0, i1, t1, t0, col, al);                                           // the top face
            line(t0, t1, (float) (R * 0.012 + 0.01), INK, al, 0.004);
        }
        for (int i = 0; i < 6; i++) {   // spokes and a bright hub
            double a = sp + i * TAU / 6;
            line(ring(c, a, rr * 0.12, yt), ring(c, a, rr * 0.62, yt), (float) (R * 0.05 + 0.03), INK, al, 0.006);
            line(ring(c, a, rr * 0.12, yt), ring(c, a, rr * 0.62, yt), (float) (R * 0.028 + 0.015), hi, al, 0.01);
        }
        for (int i = 0; i < 48; i++) {
            double a0 = TAU * i / 48, a1 = TAU * (i + 1) / 48;
            line(ring(c, a0, rr * 0.62, yt), ring(c, a1, rr * 0.62, yt), (float) (R * 0.02 + 0.014), INK, al, 0.006);
        }
    }

    /** The light of the column: a soft cyan wall, a glow on the ground, and grains of time rising inside it. */
    private static void chronoLight(Vec3 c, float R, float H, float s, float gr, double age, double mv, int id) {
        float a = gr * (1F - s);
        if (a <= 0.01F) return;
        int seg = 40;
        double rr = R * (1 + s * 1.3);
        for (int i = 0; i < seg; i++) {
            double t0 = TAU * i / seg, t1 = TAU * (i + 1) / seg;
            Vec3 b0 = ring(c, t0, rr, c.y), b1 = ring(c, t1, rr, c.y), u0 = ring(c, t0, rr, c.y + H * gr), u1 = ring(c, t1, rr, c.y + H * gr);
            v(b0, GOLD, 0.22F * a); v(b1, GOLD, 0.22F * a); v(u1, GOLD, 0.02F * a);
            v(b0, GOLD, 0.22F * a); v(u1, GOLD, 0.02F * a); v(u0, GOLD, 0.02F * a);
        }
        glowDisc(c.add(0, 0.05, 0), new Vec3(0, 1, 0), (float) (rr * 1.05), GOLD, 0.4F * a);
        glowDisc(c.add(0, 0.05, 0), new Vec3(0, 1, 0), (float) (rr * 0.6), GOLD_HI, 0.5F * a);
        int n = Mth.clamp((int) (R * 14), 10, 56);
        Random r = new Random(id * 131L + 7);
        double drift = age * 0.012 * mv;
        for (int j = 0; j < n; j++) {
            double f = (r.nextDouble() + drift * (0.6 + r.nextDouble())) % 1.0;
            double ang = r.nextDouble() * TAU + f * 5 + age * 0.02 * mv;
            double rad = R * (0.25 + 0.7 * r.nextDouble()) * (1 + s * 1.3);
            Vec3 at = ring(c, ang, rad, c.y + H * f);
            float tw = (float) (0.6 + 0.4 * Math.sin(age * 0.3 + j));
            glowDisc(at, new Vec3(0, 1, 0), 0.05F + R * 0.006F, GOLD_HI, 0.9F * a * tw * (float) Math.sin(f * Math.PI));
        }
    }

    /**
     * Julius's open grimoire: the covers fold all the way back until they meet, so there is no front or back - just
     * the spine standing upright and every page fanned out round it in a full circle, turning slowly. There is no cover at all, only pages: sixty-odd ink-edged sheets of paper. When a spell is cast the fan
     * riffles: a wave of pages lifts and falls round the ring.
     */
    private static void fan(double[] f, double now) {
        float open = (float) f[3];
        if (open <= 0.05F) return;
        float sc = (float) f[4];
        double cast = f[5];
        Vec3 c = new Vec3(f[0], f[1] + 0.02, f[2]);
        int n = 64;
        float W = 0.3F * sc, Hh = 0.24F * sc;                        // page width, half height
        Vec3 up = new Vec3(0, 1, 0);
        double spin = now * 0.03;
        float fold = smooth(open);                                    // 0 closed .. 1 folded right round
        for (int i = 0; i < n; i++) {
            // the pages spread from a closed book (all at angle 0) to an even full circle
            double th = spin + TAU * i / n * fold;
            double lift = 0.025 * fold * Math.cos(th * 2.0 - spin * 2.0);   // a barely-there swell: the top and bottom edges stay level, no dips
            if (cast >= 0) {                                          // the riffle: a wave runs round the ring
                double wave = (cast * 0.9 - i * 0.15);
                if (wave > 0 && wave < Math.PI) lift += Math.sin(wave) * 0.35;
            }
            double bow = 0.0;
            Vec3 out = new Vec3(Math.cos(th), 0, Math.sin(th));
            Vec3 side = new Vec3(-Math.sin(th), 0, Math.cos(th));
            Vec3 tip = out.scale(Math.cos(lift)).add(up.scale(Math.sin(lift)));
            Vec3 s0 = c.add(0, -Hh, 0), s1 = c.add(0, Hh, 0);
            Vec3 midOff = tip.scale(W * 0.5).add(side.scale(bow));
            Vec3 m0 = s0.add(midOff), m1 = s1.add(midOff);
            Vec3 e0 = s0.add(tip.scale(W)), e1 = s1.add(tip.scale(W));
            Vec3 tc = CAM.subtract(m0.add(m1).scale(0.5));
            float shade = 0.86F + 0.14F * (float) Math.abs(side.dot(tc.lengthSqr() < 1.0E-6 ? side : tc.normalize()));
            float[] col = mul(PAPER, shade);
            float a = Math.min(1F, open * 1.4F);
            quad(s0, m0, m1, s1, col, a);
            quad(m0, e0, e1, m1, col, a);
        }
        // the spine, and a paper cap over the middle of the top and the bottom so there is no hollow where the pages meet
        float a2 = Math.min(1F, open * 1.4F);
        float[] capCol = mul(PAPER, 0.8F), spineCol = mul(INK, 1F);
        for (int s = 0; s < 2; s++) {
            double y = s == 0 ? Hh : -Hh;
            for (int i = 0; i < 24; i++) {
                double t0 = TAU * i / 24 + spin, t1 = TAU * (i + 1) / 24 + spin;
                double rc = W * 0.5 * fold;
                tri(c.add(0, y, 0), c.add(Math.cos(t0) * rc, y, Math.sin(t0) * rc), c.add(Math.cos(t1) * rc, y, Math.sin(t1) * rc), capCol, a2);
            }
        }
        for (int i = 0; i < 8; i++) {
            double t0 = TAU * i / 8, t1 = TAU * (i + 1) / 8;
            double rs = 0.025 * sc;
            quad(c.add(Math.cos(t0) * rs, -Hh, Math.sin(t0) * rs), c.add(Math.cos(t1) * rs, -Hh, Math.sin(t1) * rs),
                    c.add(Math.cos(t1) * rs, Hh, Math.sin(t1) * rs), c.add(Math.cos(t0) * rs, Hh, Math.sin(t0) * rs), spineCol, a2);
        }
    }

    // ------------------------------------------------------------------ clocks
    private static Vec3 onFace(Clock k, Vec3 c, double ang, double rr) {
        return c.add(k.up.scale(Math.cos(ang) * rr)).add(k.right.scale(Math.sin(ang) * rr));
    }

    private static void band(Clock k, Vec3 c, double r0, double r1, int seg, float[] col, float a, Vec3 lift) {
        for (int i = 0; i < seg; i++) {
            double t0 = TAU * i / seg, t1 = TAU * (i + 1) / seg;
            quad(onFace(k, c, t0, r0).add(lift), onFace(k, c, t0, r1).add(lift), onFace(k, c, t1, r1).add(lift), onFace(k, c, t1, r0).add(lift), col, a);
        }
    }

    private static void disc(Clock k, Vec3 c, double r, int seg, float[] col, float a, Vec3 lift) {
        for (int i = 0; i < seg; i++) {
            double t0 = TAU * i / seg, t1 = TAU * (i + 1) / seg;
            tri(c.add(lift), onFace(k, c, t0, r).add(lift), onFace(k, c, t1, r).add(lift), col, a);
        }
    }

    private static void glowDisc(Vec3 c, Vec3 n, float r, float[] col, float a) {
        Vec3 u = Math.abs(n.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 s1 = u.cross(n).normalize(), s2 = n.cross(s1).normalize();
        for (int i = 0; i < 24; i++) {
            double t0 = TAU * i / 24, t1 = TAU * (i + 1) / 24;
            Vec3 a0 = c.add(s1.scale(Math.cos(t0) * r)).add(s2.scale(Math.sin(t0) * r));
            Vec3 a1 = c.add(s1.scale(Math.cos(t1) * r)).add(s2.scale(Math.sin(t1) * r));
            v(c, col, a); v(a0, col, 0F); v(a1, col, 0F);
        }
    }

    /** A clock hand on the face: ink under, gold on top, a lozenge at the tip and a counterweight ring at the tail. */
    private static void faceHand(Clock k, Vec3 c, double ang, double len, double w, float[] col, float a, Vec3 l1, Vec3 l2) {
        Vec3 d = k.up.scale(Math.cos(ang)).add(k.right.scale(Math.sin(ang)));
        Vec3 s = k.n.cross(d).normalize();
        for (int pass = 0; pass < 2; pass++) {
            double g = pass == 0 ? w * 0.6 : 0;
            Vec3 lift = pass == 0 ? l1 : l2;
            float[] cc = pass == 0 ? INK : col;
            Vec3 tail = c.subtract(d.scale(len * 0.18 + g)), tip = c.add(d.scale(len * 0.72));
            quad(tail.add(s.scale(w * 0.5 + g)).add(lift), tail.subtract(s.scale(w * 0.5 + g)).add(lift),
                    tip.subtract(s.scale(w * 0.5 + g)).add(lift), tip.add(s.scale(w * 0.5 + g)).add(lift), cc, a);
            Vec3 lz0 = c.add(d.scale(len * 0.62 - g)), lz1 = c.add(d.scale(len * 0.8)).add(s.scale(w * 1.6 + g)),
                    lz2 = c.add(d.scale(len + g)), lz3 = c.add(d.scale(len * 0.8)).subtract(s.scale(w * 1.6 + g));
            quad(lz0.add(lift), lz1.add(lift), lz2.add(lift), lz3.add(lift), cc, a);
            Vec3 cw = c.subtract(d.scale(len * 0.12));
            for (int i = 0; i < 12; i++) {
                double t0 = TAU * i / 12, t1 = TAU * (i + 1) / 12;
                double ro = w * 1.5 + g, ri = Math.max(0, w * 0.8 - g);
                Vec3 o0 = cw.add(d.scale(Math.cos(t0) * ro)).add(s.scale(Math.sin(t0) * ro)), o1 = cw.add(d.scale(Math.cos(t1) * ro)).add(s.scale(Math.sin(t1) * ro));
                Vec3 i0 = cw.add(d.scale(Math.cos(t0) * ri)).add(s.scale(Math.sin(t0) * ri)), i1 = cw.add(d.scale(Math.cos(t1) * ri)).add(s.scale(Math.sin(t1) * ri));
                quad(o0.add(lift), o1.add(lift), i1.add(lift), i0.add(lift), cc, a);
            }
        }
    }

    // Roman numerals as straight strokes in a unit cell (x -0.5..0.5, y 0..1), clock style IIII for four
    private static final String[] NUMERALS = {"XII", "I", "II", "III", "IIII", "V", "VI", "VII", "VIII", "IX", "X", "XI"};

    private static void numeral(Clock k, Vec3 c, int hour, double R, float a, Vec3 l1, Vec3 l2) {
        String s = NUMERALS[hour % 12];
        double ang = TAU * hour / 12.0;
        Vec3 up = k.up.scale(Math.cos(ang)).add(k.right.scale(Math.sin(ang)));   // the glyph stands on the centre
        Vec3 rt = k.n.cross(up).normalize().scale(-1);
        double size = R * 0.12, gap = 0.12;
        double width = 0;
        for (char ch : s.toCharArray()) width += (ch == 'I' ? 0.22 : 0.62) + gap;
        width -= gap;
        Vec3 base = c.add(up.scale(R * 0.68 - size * 0.5));
        double x = -width * 0.5;
        float w = (float) (size * 0.13);
        for (char ch : s.toCharArray()) {
            double cw = ch == 'I' ? 0.22 : 0.62, cx = x + cw * 0.5;
            List<double[]> st = new ArrayList<>();
            if (ch == 'I') st.add(new double[]{0, 0, 0, 1});
            else if (ch == 'V') { st.add(new double[]{-0.28, 1, 0, 0}); st.add(new double[]{0, 0, 0.28, 1}); }
            else { st.add(new double[]{-0.28, 0, 0.28, 1}); st.add(new double[]{-0.28, 1, 0.28, 0}); }
            for (double[] q : st) {
                Vec3 p0 = base.add(rt.scale((cx + q[0]) * size)).add(up.scale(q[1] * size));
                Vec3 p1 = base.add(rt.scale((cx + q[2]) * size)).add(up.scale(q[3] * size));
                stroke(p0, p1, k.n, w * 2.0F, INK, a, l1);
                stroke(p0, p1, k.n, w, GOLD, a, l2);
            }
            x += cw + gap;
        }
    }

    private static void clock(Clock k, double now) {
        double age = now - k.born;
        if (age < 0 || age > k.life) return;
        float grow = backOut((float) (age / 6.0));
        float a = (float) Mth.clamp((k.life - age) / 6.0, 0.0, 1.0) * (float) Mth.clamp(age / 2.0, 0.0, 1.0);
        if (a <= 0.01F) return;
        double R = k.r * grow;
        Vec3 c = k.c;
        if (k.follow >= 0) {
            Entity en = Minecraft.getInstance().level.getEntity(k.follow);
            if (en != null) c = new Vec3(Mth.lerp(PT, en.xo, en.getX()), Mth.lerp(PT, en.yo, en.getY()) + k.followY, Mth.lerp(PT, en.zo, en.getZ()));
        }
        double sign = CAM.subtract(c).dot(k.n) >= 0 ? 1 : -1;   // lift each layer toward whoever is looking
        double lz = 0.004 + k.r * 0.0015;
        Vec3 l0 = k.n.scale(sign * lz), l1 = k.n.scale(sign * lz * 2), l2 = k.n.scale(sign * lz * 3), l3 = k.n.scale(sign * lz * 4);
        int seg = Mth.clamp((int) (k.r * 10), 32, 128);
        boolean big = k.r >= 1.6;

        // the face
        if (k.style == 1) disc(k, c, R, seg, GOLD, 0.12F * a, Vec3.ZERO);
        else disc(k, c, R, seg, NAVY, 0.55F * a, Vec3.ZERO);
        band(k, c, R * 0.95, R * 1.07, seg, INK, a, l0);
        band(k, c, R * 0.97, R * 1.04, seg, GOLD, a, l1);
        band(k, c, R * 0.85, R * 0.89, seg, INK, a, l0);
        band(k, c, R * 0.86, R * 0.88, seg, GOLD, a * 0.85F, l1);
        for (int i = 0; i < 60; i++) {   // minute ticks, hour ticks heavier
            double ang = TAU * i / 60;
            boolean hr = i % 5 == 0;
            if (!hr && !big) continue;
            Vec3 p0 = onFace(k, c, ang, R * (hr ? 0.84 : 0.9)), p1 = onFace(k, c, ang, R * 0.95);
            float w = (float) (R * (hr ? 0.035 : 0.012));
            stroke(p0, p1, k.n, w * 1.8F, INK, a, l1);
            stroke(p0, p1, k.n, w, hr ? GOLD_HI : GOLD, a, l2);
        }
        if (big && k.style != 1) for (int hr = 0; hr < 12; hr++) numeral(k, c, hr, R, a, l1, l2);

        // the trap's marks: one lights each time the hand ticks
        if (k.style == 1 && k.marks > 0) {
            int lit = k.step > 0 ? (int) Math.floor(age / k.step) : 0;
            for (int j = 0; j < k.marks; j++) {
                Vec3 pc = onFace(k, c, TAU * j / k.marks, R * 0.62);
                boolean on = j < lit;
                diskAt(k, pc, R * 0.13, INK, a, l1);
                diskAt(k, pc, R * 0.1, on ? GOLD_HI : mul(GOLD, 0.45F), a, l2);
                if (on) {
                    final Vec3 pp = pc.add(l3);
                    final float rr = (float) (R * 0.35), aa = a;
                    GLOWS.add(() -> glowDisc(pp, k.n, rr, GOLD_HI, 0.8F * aa));
                }
            }
        }

        // the hands
        double ae = ageEff(k, age), minute;
        if (k.step > 0) {
            int idx = (int) Math.floor(ae / k.step);
            float f = (float) ((ae - idx * k.step) / k.step);
            float snap = f < 0.25F ? backOut(f / 0.25F) : 1F;
            minute = (idx - 1 + snap) * k.speed * k.step;
        } else minute = k.speed * ae;
        double hour = minute / 12.0 - 1.05;
        if (k.style == 3) {   // running backwards: ghosts of the minute hand trail it
            for (int j = 1; j <= 4; j++) {
                final double ga = minute - Math.signum(k.speed) * j * 0.22;
                final float ja = a * (0.5F - j * 0.1F);
                final Vec3 cc = c;
                GLOWS.add(() -> {
                    Vec3 d = k.up.scale(Math.cos(ga)).add(k.right.scale(Math.sin(ga)));
                    stroke(cc, cc.add(d.scale(R * 0.82)), k.n, (float) (R * 0.05), GOLD, ja, l3);
                });
            }
        }
        faceHand(k, c, hour, R * 0.55, R * 0.06, GOLD, a, l2, l3);
        faceHand(k, c, minute, R * 0.84, R * 0.035, GOLD_HI, a, l2.scale(1.25), l3.scale(1.25));
        if (k.style == 0 || k.style == 3) {
            Vec3 d = k.up.scale(Math.cos(minute * 4)).add(k.right.scale(Math.sin(minute * 4)));
            stroke(c.subtract(d.scale(R * 0.15)), c.add(d.scale(R * 0.9)), k.n, (float) (R * 0.012), GOLD_HI, a, l3.scale(1.5));
        }
        diskAt(k, c, R * 0.07, INK, a, l3.scale(1.6));
        diskAt(k, c, R * 0.05, GOLD_HI, a, l3.scale(1.8));

        final Vec3 cc = c;
        final float aa = a;
        GLOWS.add(() -> {   // a soft gold halo round the rim
            for (int i = 0; i < seg; i++) {
                double t0 = TAU * i / seg, t1 = TAU * (i + 1) / seg;
                Vec3 i0 = onFace(k, cc, t0, R * 1.04), i1 = onFace(k, cc, t1, R * 1.04);
                Vec3 o0 = onFace(k, cc, t0, R * 1.3), o1 = onFace(k, cc, t1, R * 1.3);
                v(i0, GOLD, 0.45F * aa); v(o0, GOLD, 0F); v(o1, GOLD, 0F);
                v(i0, GOLD, 0.45F * aa); v(o1, GOLD, 0F); v(i1, GOLD, 0.45F * aa);
            }
        });
    }

    private static void diskAt(Clock k, Vec3 c, double r, float[] col, float a, Vec3 lift) { disc(k, c, r, 16, col, a, lift); }

    // ------------------------------------------------------------------ clock hands in flight
    private static void handShape(Vec3 p, Vec3 d, Vec3 s, double L, double g, float[] col, float a, Vec3 lift) {
        // shaft
        Vec3 t0 = p.subtract(d.scale(L * 0.2 + g)), t1 = p.add(d.scale(L * 0.55));
        double hw = L * 0.03 + g;
        quad(t0.add(s.scale(hw)).add(lift), t0.subtract(s.scale(hw)).add(lift), t1.subtract(s.scale(hw)).add(lift), t1.add(s.scale(hw)).add(lift), col, a);
        // the lozenge tip
        Vec3 z0 = p.add(d.scale(L * 0.45 - g)), z1 = p.add(d.scale(L * 0.66)).add(s.scale(L * 0.09 + g)),
                z2 = p.add(d.scale(L * 0.95 + g)), z3 = p.add(d.scale(L * 0.66)).subtract(s.scale(L * 0.09 + g));
        quad(z0.add(lift), z1.add(lift), z2.add(lift), z3.add(lift), col, a);
        // the counterweight ring
        Vec3 cw = p.subtract(d.scale(L * 0.08));
        double ro = L * 0.075 + g, ri = Math.max(0, L * 0.042 - g);
        for (int i = 0; i < 12; i++) {
            double a0 = TAU * i / 12, a1 = TAU * (i + 1) / 12;
            Vec3 o0 = cw.add(d.scale(Math.cos(a0) * ro)).add(s.scale(Math.sin(a0) * ro)), o1 = cw.add(d.scale(Math.cos(a1) * ro)).add(s.scale(Math.sin(a1) * ro));
            Vec3 i0 = cw.add(d.scale(Math.cos(a0) * ri)).add(s.scale(Math.sin(a0) * ri)), i1 = cw.add(d.scale(Math.cos(a1) * ri)).add(s.scale(Math.sin(a1) * ri));
            quad(o0.add(lift), o1.add(lift), i1.add(lift), i0.add(lift), col, a);
        }
        // a little diamond at the tail
        Vec3 q0 = p.subtract(d.scale(L * 0.18 - g)), q1 = p.subtract(d.scale(L * 0.26)).add(s.scale(L * 0.045 + g)),
                q2 = p.subtract(d.scale(L * 0.36 + g)), q3 = p.subtract(d.scale(L * 0.26)).subtract(s.scale(L * 0.045 + g));
        quad(q0.add(lift), q1.add(lift), q2.add(lift), q3.add(lift), col, a);
    }

    private static void hand(Hand k, double now) {
        double age = now - k.born;
        if (k.killedAt >= 0) {   // it struck: a gold flash where it ended
            double t = now - k.killedAt;
            final Vec3 at = k.c.add(k.v.scale(Math.min(k.killedAt - k.born, k.life)));
            final float f = (float) Mth.clamp(1.0 - t / 6.0, 0, 1), L = k.len;
            if (f > 0) GLOWS.add(() -> {
                Vec3 n = CAM.subtract(at);
                n = n.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : n.normalize();
                glowDisc(at, n, L * (0.4F + 0.6F * (1 - f)), GOLD_HI, 0.9F * f);
                glowDisc(at, n, L * (0.8F + 0.8F * (1 - f)), GOLD, 0.4F * f);
            });
            return;
        }
        if (age > k.life + 4) return;
        double t = Math.min(age, k.life);
        Vec3 p = k.c.add(k.v.scale(t));
        float a = (float) Mth.clamp((k.life + 4 - age) / 4.0, 0.0, 1.0);
        Vec3 tc = CAM.subtract(p);
        if (tc.lengthSqr() < 1.0E-6) return;
        tc = tc.normalize();
        Vec3 d = k.d, s = d.cross(tc);
        if (s.lengthSqr() < 1.0E-8) return;
        s = s.normalize();
        double L = k.len * smooth((float) Math.min(1.0, age / 3.0 + 0.3));
        handShape(p, d, s, L, L * 0.025, INK, a, tc.scale(0.005));
        handShape(p, d, s, L, 0, k.style == 1 ? GOLD : GOLD_HI, a, tc.scale(0.012 + L * 0.002));
        final Vec3 pp = p, vv = k.v, ss = s, dd = d;
        final double LL = L;
        final float aa = a;
        GLOWS.add(() -> {   // the streak behind it and three fading afterimages
            Vec3 back = vv.lengthSqr() < 1.0E-6 ? dd.scale(-LL) : vv.scale(-3.5);
            Vec3 tail = pp.add(back);
            v(pp.add(ss.scale(LL * 0.08)), GOLD, 0.5F * aa); v(pp.subtract(ss.scale(LL * 0.08)), GOLD, 0.5F * aa); v(tail, GOLD, 0F);
            for (int j = 1; j <= 3; j++) handShape(pp.add(vv.scale(-1.1 * j)), dd, ss, LL, 0, GOLD, 0.28F * aa / j, Vec3.ZERO);
        });
    }

    // ------------------------------------------------------------------ frozen targets
    private static void mark(Minecraft mc, int id, double[] f, double now, float pt) {
        Entity en = mc.level.getEntity(id);
        if (en == null) return;
        Vec3 pos = new Vec3(Mth.lerp(pt, en.xo, en.getX()), Mth.lerp(pt, en.yo, en.getY()), Mth.lerp(pt, en.zo, en.getZ()));
        double w = en.getBbWidth(), h = en.getBbHeight();
        boolean held = now < f[1];
        float in = (float) Mth.clamp((now - f[0]) / 4.0, 0, 1);
        Random r = new Random(id * 31L + 5);
        double R = w * 0.7 + 0.45;
        if (held) {
            // a stopped clock at its feet
            Clock k = new Clock();
            k.c = pos.add(0, 0.04, 0); k.n = new Vec3(0, 1, 0); k.up = new Vec3(0, 0, -1); k.right = k.up.cross(k.n).normalize();
            k.r = (float) R; k.life = 1;
            double sign = CAM.y >= k.c.y ? 1 : -1;
            Vec3 l0 = new Vec3(0, 0.006 * sign, 0), l1 = new Vec3(0, 0.012 * sign, 0), l2 = new Vec3(0, 0.018 * sign, 0);
            double RR = R * backOut(in);
            band(k, k.c, RR * 0.93, RR * 1.06, 40, INK, in, l0);
            band(k, k.c, RR * 0.96, RR * 1.03, 40, GOLD, in, l1);
            for (int i = 0; i < 12; i++) {
                Vec3 p0 = onFace(k, k.c, TAU * i / 12, RR * 0.8), p1 = onFace(k, k.c, TAU * i / 12, RR * 0.93);
                stroke(p0, p1, k.n, (float) (RR * 0.05), GOLD_HI, in, l1);
            }
            faceHand(k, k.c, r.nextDouble() * TAU, RR * 0.5, RR * 0.06, GOLD, in, l1, l2);
            faceHand(k, k.c, r.nextDouble() * TAU, RR * 0.78, RR * 0.035, GOLD_HI, in, l1.scale(1.3), l2.scale(1.3));
            // pages hanging in the air round it, perfectly still
            for (int i = 0; i < 5; i++) {
                double th = TAU * i / 5 + r.nextDouble() * 0.6;
                Vec3 at = pos.add(Math.cos(th) * (w * 0.6 + 0.35), h * (0.25 + 0.6 * r.nextDouble()), Math.sin(th) * (w * 0.6 + 0.35));
                card(at, th + r.nextDouble() - 0.5, r.nextDouble() - 0.5, 0.16F * in, COLD, in);
            }
            // an hourglass over its head
            Vec3 top = pos.add(0, h + 0.45, 0);
            Vec3 tc = CAM.subtract(top);
            if (tc.lengthSqr() > 1.0E-6) {
                tc = tc.normalize();
                Vec3 sd = new Vec3(0, 1, 0).cross(tc);
                if (sd.lengthSqr() > 1.0E-6) {
                    sd = sd.normalize();
                    double s = 0.18 * in;
                    Vec3 tl = top.add(sd.scale(-s)).add(0, s * 1.4, 0), tr = top.add(sd.scale(s)).add(0, s * 1.4, 0);
                    Vec3 bl = top.add(sd.scale(-s)).add(0, -s * 1.4, 0), br = top.add(sd.scale(s)).add(0, -s * 1.4, 0);
                    tri(tl, tr, top, GOLD, in);
                    tri(bl, br, top, mul(GOLD, 0.8F), in);
                    float lw = (float) (s * 0.22);
                    line(tl, tr, lw, INK, in, 0.01); line(bl, br, lw, INK, in, 0.01);
                    line(tl, br, lw, INK, in, 0.01); line(tr, bl, lw, INK, in, 0.01);
                }
            }
        } else {
            float s = (float) Mth.clamp((now - f[1]) / 8.0, 0, 1);   // time runs again: the pages scatter
            for (int i = 0; i < 5; i++) {
                double th = TAU * i / 5 + r.nextDouble() * 0.6;
                double hh = h * (0.25 + 0.6 * r.nextDouble());
                double out = w * 0.6 + 0.35 + s * 2.2;
                Vec3 at = pos.add(Math.cos(th) * out, hh + s * 1.2, Math.sin(th) * out);
                card(at, th + s * 5, s * 4, 0.16F * (1 - s * 0.5F), PAPER, 1 - s);
            }
        }
    }

    /**
     * The Ticking Snare's time bubble: a glassy blue sphere round the trapped target - clear in the middle, bright at
     * the rim - with a ribbon of Roman numerals tumbling round it like a ring round a planet. It pops when time is up.
     */
    private static void bubble(Minecraft mc, int id, double[] f, double now, float pt) {
        Entity en = mc.level.getEntity(id);
        if (en == null) return;
        double age = now - f[0], left = f[1] - now;
        float in = backOut((float) Mth.clamp(age / 5.0, 0, 1));
        float pop = left < 0 ? (float) Mth.clamp(-left / 4.0, 0, 1) : 0F;
        if (pop >= 1F) return;
        float a = (1F - pop) * (float) Mth.clamp(age / 2.0, 0, 1);
        double w = en.getBbWidth(), h = en.getBbHeight();
        Vec3 c = new Vec3(Mth.lerp(pt, en.xo, en.getX()), Mth.lerp(pt, en.yo, en.getY()) + h * 0.5, Mth.lerp(pt, en.zo, en.getZ()));
        double R = (Math.max(h * 0.62, w * 0.9) + 0.25) * in * (1 + pop * 0.6);
        // the ring of numerals: tipped over and turning, like Saturn's
        double tilt = 0.5 + 0.25 * Math.sin(now * 0.03 + id), ph = now * 0.035 + id * 1.7;
        Clock k = new Clock();
        k.n = new Vec3(Math.sin(tilt) * Math.cos(ph), Math.cos(tilt), Math.sin(tilt) * Math.sin(ph)).normalize();
        Vec3 ref = Math.abs(k.n.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u0 = ref.cross(k.n).normalize(), u1 = k.n.cross(u0).normalize();
        double sp = now * 0.12;
        k.up = u0.scale(Math.cos(sp)).add(u1.scale(Math.sin(sp)));
        k.right = k.up.cross(k.n).normalize();
        k.c = c; k.r = (float) R;
        double Rr = R * 1.14;
        double sg = CAM.subtract(c).dot(k.n) >= 0 ? 1 : -1;
        Vec3 l0 = k.n.scale(0.004 * sg), l1 = k.n.scale(0.01 * sg), l2 = k.n.scale(0.016 * sg);
        float[] ribbon = {0.82F, 0.94F, 1.0F};
        band(k, c, Rr * 0.9, Rr * 1.1, 56, INK, a * 0.9F, l0);
        band(k, c, Rr * 0.92, Rr * 1.08, 56, ribbon, a, l1);
        for (int i = 0; i < 12; i++) numeral(k, c, i, Rr / 0.68, a, l1, l2);
        // the sphere: a thin film, brighter where it turns away from you (like glass)
        int LA = 10, LO = 18;
        float[] film = {0.55F, 0.82F, 1.0F};
        for (int i = 0; i < LA; i++) {
            double p0 = Math.PI * i / LA - Math.PI / 2, p1 = Math.PI * (i + 1) / LA - Math.PI / 2;
            for (int j = 0; j < LO; j++) {
                double t0 = TAU * j / LO, t1 = TAU * (j + 1) / LO;
                Vec3 n00 = new Vec3(Math.cos(p0) * Math.cos(t0), Math.sin(p0), Math.cos(p0) * Math.sin(t0));
                Vec3 n01 = new Vec3(Math.cos(p0) * Math.cos(t1), Math.sin(p0), Math.cos(p0) * Math.sin(t1));
                Vec3 n10 = new Vec3(Math.cos(p1) * Math.cos(t0), Math.sin(p1), Math.cos(p1) * Math.sin(t0));
                Vec3 n11 = new Vec3(Math.cos(p1) * Math.cos(t1), Math.sin(p1), Math.cos(p1) * Math.sin(t1));
                Vec3 mid = n00.add(n11).normalize();
                Vec3 tv = CAM.subtract(c.add(mid.scale(R)));
                double facing = tv.lengthSqr() < 1.0E-6 ? 1 : Math.abs(mid.dot(tv.normalize()));
                float al = (float) (0.16 + 0.5 * Math.pow(1 - facing, 2.2)) * a;
                quad(c.add(n00.scale(R)), c.add(n01.scale(R)), c.add(n11.scale(R)), c.add(n10.scale(R)), film, al);
            }
        }
        // a bright edge line round the silhouette, and a little glint
        Vec3 tc = CAM.subtract(c);
        if (tc.lengthSqr() > 1.0E-6) {
            Vec3 vn = tc.normalize();
            Vec3 s1 = (Math.abs(vn.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).cross(vn).normalize(), s2 = vn.cross(s1).normalize();
            double rr = R * Math.sqrt(Math.max(0, 1 - R * R / Math.max(R * R, tc.lengthSqr())));
            for (int j = 0; j < 36; j++) {
                double t0 = TAU * j / 36, t1 = TAU * (j + 1) / 36;
                Vec3 e0 = c.add(s1.scale(Math.cos(t0) * rr)).add(s2.scale(Math.sin(t0) * rr));
                Vec3 e1 = c.add(s1.scale(Math.cos(t1) * rr)).add(s2.scale(Math.sin(t1) * rr));
                line(e0, e1, (float) (R * 0.035), GOLD_HI, 0.55F * a, 0.02);
            }
            Vec3 gl = c.add(s1.scale(-rr * 0.55)).add(s2.scale(rr * 0.5));
            line(gl, gl.add(s2.scale(-rr * 0.18)).add(s1.scale(rr * 0.1)), (float) (R * 0.05), PAPER, 0.8F * a, 0.03);
        }
    }

    /** A small shard of time at {@code at}, turned by yaw and tipped by pitch, with an ink edge. */
    private static void card(Vec3 at, double yaw, double pitch, float size, float[] paper, float a) {
        if (a <= 0.01F || size <= 0.001F) return;
        Vec3 rt = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 up = new Vec3(0, Math.cos(pitch), 0).add(new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)).scale(Math.sin(pitch)));
        // a shard of stopped time: a little diamond, ink-edged
        Vec3 q0 = at.subtract(up.scale(size * 1.5)), q1 = at.add(rt.scale(size * 0.75)), q2 = at.add(up.scale(size * 1.5)), q3 = at.subtract(rt.scale(size * 0.75));
        quad(q0, q1, q2, q3, paper, a);
        float w = size * 0.12F;
        line(q0, q1, w, INK, a, 0.006); line(q1, q2, w, GOLD, a, 0.006); line(q2, q3, w, INK, a, 0.006); line(q3, q0, w, INK, a, 0.006);
    }

    // ------------------------------------------------------------------ gold afterimages
    @SuppressWarnings("unchecked")
    private static void ghosts(Minecraft mc, PoseStack ps, double now) {
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        boolean drew = false;
        for (Ghost g : GHOSTS) {
            double age = now - g.born;
            if (age < 0 || age > g.life) continue;
            Entity en = mc.level.getEntity(g.owner);
            if (!(en instanceof AbstractClientPlayer pl)) continue;
            EntityRenderer<?> er = mc.getEntityRenderDispatcher().getRenderer(pl);
            if (!(er instanceof PlayerRenderer prr)) continue;
            PlayerModel<AbstractClientPlayer> model = prr.getModel();
            float in = (float) Mth.clamp(age / 2.0, 0, 1), out = (float) Mth.clamp((g.life - age) / 8.0, 0, 1);
            float a = g.alpha * Math.min(in, out);
            if (g.style == 1) a *= (float) (0.75 + 0.25 * Math.sin(age * 0.5));
            if (a <= 0.01F) continue;
            pose(model, pl, g.pose, (float) age);
            double bob = g.style == 1 ? Math.sin(age * 0.15) * 0.05 : 0;
            ps.pushPose();
            ps.translate(g.c.x - CAM.x, g.c.y - CAM.y + bob, g.c.z - CAM.z);
            ps.mulPose(Axis.YP.rotationDegrees(180.0F - g.yaw));
            ps.scale(-1.0F, -1.0F, 1.0F);
            ps.scale(0.9375F, 0.9375F, 0.9375F);
            ps.translate(0.0F, -1.501F, 0.0F);
            VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(pl.getSkinTextureLocation()));
            ps.pushPose();   // an ink halo just behind the gold
            ps.translate(0.0F, 0.75F, 0.0F);
            ps.scale(1.07F, 1.04F, 1.07F);
            ps.translate(0.0F, -0.75F, 0.0F);
            model.renderToBuffer(ps, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0.05F, 0.05F, 0.12F, a * 0.45F);
            ps.popPose();
            model.renderToBuffer(ps, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0.4F, 0.88F, 1.0F, a * 0.7F);
            ps.popPose();
            drew = true;
        }
        if (drew) buf.endBatch();
    }

    private static void pose(PlayerModel<AbstractClientPlayer> m, AbstractClientPlayer pl, int pose, float age) {
        m.setupAnim(pl, 0.0F, 0.0F, age, 0.0F, 0.0F);
        ModelPart[] all = {m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg, m.jacket, m.leftSleeve, m.rightSleeve, m.leftPants, m.rightPants};
        for (ModelPart part : all) part.resetPose();
        m.crouching = false;
        switch (pose) {
            case 1 -> {   // casting: the right hand reaches out, the left held back
                m.rightArm.xRot = -1.45F; m.rightArm.yRot = -0.1F;
                m.leftArm.xRot = -0.25F; m.leftArm.zRot = 0.2F;
            }
            case 2 -> {   // both arms raised
                m.rightArm.xRot = -2.8F; m.rightArm.zRot = 0.25F;
                m.leftArm.xRot = -2.8F; m.leftArm.zRot = -0.25F;
            }
            case 3 -> {   // a stride, caught mid-step
                m.body.xRot = 0.25F;
                m.rightArm.xRot = 0.9F; m.leftArm.xRot = -0.9F;
                m.rightLeg.xRot = -0.8F; m.leftLeg.xRot = 0.7F;
            }
            default -> {
                m.rightArm.xRot = 0.1F; m.rightArm.zRot = 0.08F;
                m.leftArm.xRot = 0.1F; m.leftArm.zRot = -0.08F;
            }
        }
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }

    // ================================================================== the HUD
    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        GuiGraphics g = e.getGuiGraphics();
        double now = mc.level.getGameTime() + e.getPartialTick();
        float W = g.guiWidth(), H = g.guiHeight();
        Matrix4f m = g.pose().last().pose();
        boolean screen = scrEnd >= 0 && now >= scrStart && now <= scrEnd + 8;
        if (holding || stored > 0) {   // the stored time only shows while the Time Grimoire is the book in hand
            net.minecraft.world.item.ItemStack bk = mc.player == null ? null : io.redspace.ironsspellbooks.api.util.Utils.getPlayerSpellbookStack(mc.player);
            if (bk == null || bk.isEmpty() || !bk.is(com.ironspellgrimoire.registry.AddonItemRegistry.TIME_GRIMOIRE.get())) { holding = false; stored = 0; }
        }
        boolean showStored = (holding || stored > 0) && now - storedShown < 80;   // fades out; it does not stay on the crosshair
        if (!screen && !showStored) return;
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        if (screen) {
            float a = (float) Math.min(Mth.clamp((now - scrStart) / 3.0, 0, 1), Mth.clamp((scrEnd + 8 - now) / 8.0, 0, 1));
            float[] tint = scrStyle == 0 ? new float[]{0.12F, 0.18F, 0.34F} : new float[]{0.42F, 0.3F, 0.1F};
            rect(bb, m, 0, 0, W, H, tint, 0.2F * a);
            float vw = Math.min(W, H) * 0.22F;   // an ink vignette
            gradRect(bb, m, 0, 0, W, vw, NAVY, 0.55F * a, 0F, true);
            gradRect(bb, m, 0, H - vw, W, H, NAVY, 0F, 0.55F * a, true);
            gradRect(bb, m, 0, 0, vw, H, NAVY, 0.55F * a, 0F, false);
            gradRect(bb, m, W - vw, 0, W, H, NAVY, 0F, 0.55F * a, false);
            // a huge faint clock over the world: stopped, or running backwards
            float cx = W * 0.5F, cy = H * 0.5F, R = Math.min(W, H) * 0.42F;
            screenRing(bb, m, cx, cy, R * 0.97F, R, GOLD, 0.35F * a);
            screenRing(bb, m, cx, cy, R * 0.86F, R * 0.875F, GOLD, 0.2F * a);
            for (int i = 0; i < 12; i++) {
                double ang = TAU * i / 12;
                float sx = (float) Math.sin(ang), sy = (float) -Math.cos(ang);
                screenLine(bb, m, cx + sx * R * 0.88F, cy + sy * R * 0.88F, cx + sx * R * 0.96F, cy + sy * R * 0.96F, i % 3 == 0 ? 3F : 1.5F, GOLD_HI, 0.35F * a);
            }
            double mh = scrStyle == 0 ? 1.1 : -(now - scrStart) * 0.6, hh = scrStyle == 0 ? -1.9 : mh / 12.0 - 1.9;
            screenLine(bb, m, cx, cy, cx + (float) Math.sin(mh) * R * 0.8F, cy - (float) Math.cos(mh) * R * 0.8F, 2.5F, GOLD_HI, 0.3F * a);
            screenLine(bb, m, cx, cy, cx + (float) Math.sin(hh) * R * 0.5F, cy - (float) Math.cos(hh) * R * 0.5F, 4F, GOLD, 0.3F * a);
        }
        if (showStored) {   // stored time: ten small ticks on an arc under the crosshair
            float cx = W * 0.5F, cy = H * 0.5F;
            for (int i = 0; i < 10; i++) {
                double ang = Math.toRadians(140 - i * (100.0 / 9.0));
                float sx = (float) Math.cos(ang), sy = (float) Math.sin(ang);
                boolean on = i < stored;
                float r0 = 13F, r1 = on ? 18F : 16F;
                screenLine(bb, m, cx + sx * r0, cy + sy * r0, cx + sx * r1, cy + sy * r1, on ? 2.2F : 1.4F, INK, on ? 0.9F : 0.4F);
                screenLine(bb, m, cx + sx * r0, cy + sy * r0, cx + sx * r1, cy + sy * r1, on ? 1.2F : 0.8F, on ? GOLD_HI : PAPER, on ? 1F : 0.35F);
            }
        }
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    private static void sv(BufferBuilder bb, Matrix4f m, float x, float y, float[] c, float a) { bb.vertex(m, x, y, 0).color(c[0], c[1], c[2], a).endVertex(); }

    private static void rect(BufferBuilder bb, Matrix4f m, float x0, float y0, float x1, float y1, float[] c, float a) {
        sv(bb, m, x0, y0, c, a); sv(bb, m, x0, y1, c, a); sv(bb, m, x1, y1, c, a);
        sv(bb, m, x0, y0, c, a); sv(bb, m, x1, y1, c, a); sv(bb, m, x1, y0, c, a);
    }

    private static void gradRect(BufferBuilder bb, Matrix4f m, float x0, float y0, float x1, float y1, float[] c, float a0, float a1, boolean vertical) {
        float tl = a0, tr = vertical ? a0 : a1, bl = vertical ? a1 : a0, br = a1;
        sv(bb, m, x0, y0, c, tl); sv(bb, m, x0, y1, c, bl); sv(bb, m, x1, y1, c, br);
        sv(bb, m, x0, y0, c, tl); sv(bb, m, x1, y1, c, br); sv(bb, m, x1, y0, c, tr);
    }

    private static void screenLine(BufferBuilder bb, Matrix4f m, float x0, float y0, float x1, float y1, float w, float[] c, float a) {
        float dx = x1 - x0, dy = y1 - y0, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 0.01F) return;
        float nx = -dy / l * w * 0.5F, ny = dx / l * w * 0.5F;
        sv(bb, m, x0 + nx, y0 + ny, c, a); sv(bb, m, x0 - nx, y0 - ny, c, a); sv(bb, m, x1 - nx, y1 - ny, c, a);
        sv(bb, m, x0 + nx, y0 + ny, c, a); sv(bb, m, x1 - nx, y1 - ny, c, a); sv(bb, m, x1 + nx, y1 + ny, c, a);
    }

    private static void screenRing(BufferBuilder bb, Matrix4f m, float cx, float cy, float r0, float r1, float[] c, float a) {
        int seg = 64;
        for (int i = 0; i < seg; i++) {
            double t0 = TAU * i / seg, t1 = TAU * (i + 1) / seg;
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            sv(bb, m, cx + c0 * r0, cy + s0 * r0, c, a); sv(bb, m, cx + c0 * r1, cy + s0 * r1, c, a); sv(bb, m, cx + c1 * r1, cy + s1 * r1, c, a);
            sv(bb, m, cx + c0 * r0, cy + s0 * r0, c, a); sv(bb, m, cx + c1 * r1, cy + s1 * r1, c, a); sv(bb, m, cx + c1 * r0, cy + s1 * r0, c, a);
        }
    }
}
