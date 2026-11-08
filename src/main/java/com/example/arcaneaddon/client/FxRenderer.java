package com.example.arcaneaddon.client;

import com.example.arcaneaddon.network.FxPacket;
import com.example.arcaneaddon.registry.AddonItemRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

@EventBusSubscriber(
   modid = "arcane_addon",
   value = {Dist.CLIENT}
)
public class FxRenderer {
   private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("arcane_addon", "textures/effect/white.png");
   private static final float[][] BODY = new float[][]{
      {0.06F, 0.0F, 0.04F},
      {0.03F, 0.0F, 0.08F},
      {0.22F, 0.05F, 0.0F},
      {0.02F, 0.1F, 0.16F},
      {0.12F, 0.11F, 0.0F},
      {0.04F, 0.13F, 0.09F},
      {0.07F, 0.07F, 0.09F},
      {0.09F, 0.10F, 0.12F},   // 7 steel
      {0.16F, 0.10F, 0.02F},   // 8 gold
      {0.05F, 0.0F, 0.01F}     // 9 ember (devil form)
   };
   private static final float[][] RIM = new float[][]{
      {1.0F, 0.16F, 0.24F}, {0.67F, 0.24F, 1.0F}, {1.0F, 0.6F, 0.12F}, {0.6F, 0.88F, 1.0F}, {1.0F, 0.96F, 0.44F}, {0.75F, 1.0F, 0.84F}, {0.95F, 0.95F, 1.0F},
      {0.78F, 0.85F, 0.94F},   // 7 steel
      {1.0F, 0.84F, 0.36F},    // 8 gold
      {1.0F, 0.30F, 0.07F}     // 9 ember (devil form)
   };
   /** Overall strength of every world effect. Lower = subtler. */
   private static final float FX_ALPHA = 0.85F;
   /** Effects closer than this to the camera fade out so they never cover the screen (first person). */
   private static final double NEAR_FADE_START = 0.8;
   private static final double NEAR_FADE_RANGE = 2.4;
   private static final float NEAR_FADE_MIN = 0.08F;
   private static boolean nearFade = false;
   private static double bookTime = 0.0;
   private static final List<FxRenderer.Fx> FX = new ArrayList<>();
   /** Players in Devil Union: entity id -> {start tick, end tick}. Fed by FxPacket shape 6. */
   private static final java.util.Map<Integer, double[]> DEVIL = new java.util.HashMap<>();
   private static final int EMBER = 9;
   private static final List<FxRenderer.BookDraw> BOOKS = new ArrayList<>();
   private static final List<FxRenderer.AuraDraw> AURAS = new ArrayList<>();

   public static void queueBook(Vec3 pos, float yawS, float tiltDeg, float scale, float alpha, float flipCycle) {
      BOOKS.add(new FxRenderer.BookDraw(pos, yawS, tiltDeg, scale, alpha, flipCycle));
   }

   public static void queueAura(Vec3 a, Vec3 b, float width, int pal, float alpha) {
      AURAS.add(new FxRenderer.AuraDraw(a, b, width, pal, alpha));
   }

   public static void spawn(FxPacket p) {
      if (p.shape == 4) {   // Ki Sense mark: id = entity, palette = creature type, life = duration
         KiSenseRenderer.mark(p.id, p.palette, p.life);
         return;
      }
      if (p.shape == 6) {   // Devil Union form: id = player, life = ticks left (0 = ending now)
         Minecraft mc0 = Minecraft.getInstance();
         if (mc0.level != null) {
            double now0 = mc0.level.getGameTime();
            double[] f = DEVIL.get(p.id);
            if (p.life > 0) {
               if (f == null || f[1] < now0) DEVIL.put(p.id, new double[]{now0, now0 + p.life});   // a new transformation
               else f[1] = now0 + p.life;                                                           // just a refresh
            } else if (f != null) {
               f[1] = Math.min(f[1], now0);                                                         // start fading out
            }
         }
         return;
      }
      if (p.shape == 5) {   // mid-move pose: id = player, palette = pose index
         ArcaneAnimations.play(p.id, p.palette);
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         if (p.shape == 3) {
            for (FxRenderer.Fx f : FX) {
               if (f.p.id == p.id && f.killAge < 0.0) {
                  f.killAge = (double)mc.level.getGameTime() - f.born;
               }
            }
         } else {
            FxRenderer.Fx fx = new FxRenderer.Fx();
            fx.p = p;
            fx.born = (double)mc.level.getGameTime();
            FX.add(fx);
         }
      }
   }

   public static void localRing(Vec3 c, int pal, float r0, float r1, float h, int life) {
      FxPacket p = new FxPacket();
      p.shape = 0;
      p.palette = (byte)pal;
      p.life = life;
      p.c = c;
      p.d = new Vec3(1.0, 0.0, 0.0);
      p.u = new Vec3(0.0, 0.0, 1.0);
      p.r = r0;
      p.r2 = r1;
      p.a0 = -180.0F;
      p.a1 = 180.0F;
      p.h = h;
      spawn(p);
   }

   @SubscribeEvent
   public static void onTick(ClientTickEvent e) {
      if (e.phase == Phase.END) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null) {
            FX.clear();
         } else {
            double now = (double)mc.level.getGameTime();
            Iterator<FxRenderer.Fx> it = FX.iterator();

            while (it.hasNext()) {
               FxRenderer.Fx f = it.next();
               double life = f.killAge >= 0.0 ? f.killAge + 4.0 : (double)f.p.life;
               if (now - f.born > life + 1.0) {
                  it.remove();
               }
            }
         }
      }
   }

   private static void vtx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 w, float[] col, float a) {
      if (nearFade) {
         double d = w.distanceTo(cam);
         a *= Mth.clamp((float)((d - NEAR_FADE_START) / NEAR_FADE_RANGE), NEAR_FADE_MIN, 1.0F) * FX_ALPHA;
      }
      vc.vertex(m, (float)(w.x - cam.x), (float)(w.y - cam.y), (float)(w.z - cam.z))
         .color(col[0], col[1], col[2], Mth.clamp(a, 0.0F, 1.0F))
         .uv(0.5F, 0.5F)
         .overlayCoords(OverlayTexture.NO_OVERLAY)
         .uv2(15728880)
         .normal(n, 0.0F, 1.0F, 0.0F)
         .endVertex();
   }

   private static void quad(
      VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float[] col, float aa, float ab, float ac, float ad
   ) {
      vtx(vc, m, n, cam, a, col, aa);
      vtx(vc, m, n, cam, b, col, ab);
      vtx(vc, m, n, cam, c, col, ac);
      vtx(vc, m, n, cam, d, col, ad);
   }

   @SubscribeEvent
   public static void onRender(RenderLevelStageEvent e) {
      if (e.getStage() == Stage.AFTER_PARTICLES) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null) {
            BOOKS.clear();
            AURAS.clear();
         } else {
            float pt = e.getPartialTick();
            PoseStack ps = e.getPoseStack();
            Vec3 cam = e.getCamera().getPosition();
            BufferSource buf = mc.renderBuffers().bufferSource();
            RenderType rt = RenderType.entityTranslucentEmissive(WHITE);
            VertexConsumer vc = buf.getBuffer(rt);
            double now = (double)((float)mc.level.getGameTime() + pt);
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            nearFade = true;
            bookTime = now;

            for (FxRenderer.Fx f : FX) {
               double age = now - f.born;
               double life = f.killAge >= 0.0 ? f.killAge + 4.0 : (double)f.p.life;
               if (!(age < 0.0) && !(age > life)) {
                  float env = (float)(Mth.clamp(age / 1.5, 0.0, 1.0) * Mth.clamp((life - age) / Math.max(2.0, life * 0.45), 0.0, 1.0));
                  switch (f.p.shape) {
                     case 0:
                        wall(vc, m, n, cam, f.p, age, env);
                        break;
                     case 1:
                        crescentFx(vc, m, n, cam, f.p, age, env);
                        break;
                     case 2:
                        beam(vc, m, n, cam, f.p.c, f.p.c.add(f.p.d), f.p.r * (1.0F - 0.55F * (float)(age / life)), f.p.palette, env, 0.35);
                  }
               }
            }

            float pulse = 0.75F + 0.25F * (float)Math.sin(now * 0.35);

            for (Player p : mc.level.players()) {
               if (!p.isSpectator() && !p.isInvisible() && (p != mc.player || !mc.options.getCameraType().isFirstPerson())) {
                  heldAura(vc, m, n, cam, p, pt, pulse, p.getMainHandItem().getItem(), true);
                  heldAura(vc, m, n, cam, p, pt, pulse, p.getOffhandItem().getItem(), false);
                  double[] form = DEVIL.get(p.getId());
                  if (form != null) devilForm(vc, m, n, cam, p, pt, now, form);
               }
            }
            DEVIL.values().removeIf(f -> now > f[1] + 20.0);

            for (FxRenderer.AuraDraw a : AURAS) {
               beam(vc, m, n, cam, a.a, a.b, a.width, a.pal, a.alpha * pulse, 0.5);
            }

            AURAS.clear();
            nearFade = false;
            buf.endBatch(rt);
            if (!BOOKS.isEmpty()) {
               VertexConsumer bv = buf.getBuffer(rt);

               for (FxRenderer.BookDraw b : BOOKS) {
                  book(ps, bv, cam, b);
               }

               buf.endBatch(rt);
               BOOKS.clear();
            }
         }
      }
   }

   /**
    * Devil Union form - an original design drawn over the player's own skin.
    *
    *  WINGS   five blades per side, each bent in three joints. The flap is a wave that travels from shoulder to tip
    *          (tips lag behind), and the wings react to how you move: a slow beat standing still, swept back when
    *          running, hard flaps in the air, spread wide to glide when falling, folded in when crouching.
    *          They burst open with an overshoot when the form starts; ember trails follow the tips and a glow pulse
    *          runs along every blade.
    *  CROWN   black flame tongues above the head, with three taller ones that pulse.
    *  MANTLE  three shadow streams down the back that ripple and get blown back as you move.
    *  SIGIL   a slowly turning ember circle and star under your feet while you stand on the ground.
    *  BURST   an ember shockwave the moment the form begins.
    * Everything fades out over 20 ticks when the union ends.
    */
   private static void devilForm(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Player p, float pt, double now, double[] f) {
      double age = now - f[0];
      float fade = (float)Mth.clamp((f[1] + 20.0 - now) / 20.0, 0.0, 1.0);
      // unfold with an overshoot (ease-out-back)
      double g = Mth.clamp(age / 14.0, 0.0, 1.0) - 1.0;
      float grow = (float)(1.0 + 2.70158 * g * g * g + 1.70158 * g * g);
      float a = fade * Mth.clamp(0.35F + 0.65F * (float)Mth.clamp(age / 14.0, 0.0, 1.0), 0.0F, 1.0F);
      if (a <= 0.01F) return;

      double by = Math.toRadians((double)Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot));
      Vec3 fw = new Vec3(-Math.sin(by), 0.0, Math.cos(by));
      Vec3 lf = new Vec3(Math.cos(by), 0.0, Math.sin(by));
      Vec3 base = new Vec3(Mth.lerp((double)pt, p.xo, p.getX()), Mth.lerp((double)pt, p.yo, p.getY()), Mth.lerp((double)pt, p.zo, p.getZ()));
      float h = p.getBbHeight();
      float[] dark = BODY[EMBER];

      // ---- how the body is moving drives the wings
      double speed = Math.sqrt((p.getX() - p.xo) * (p.getX() - p.xo) + (p.getZ() - p.zo) * (p.getZ() - p.zo));
      double vy = p.getY() - p.yo;
      boolean air = !p.onGround();
      boolean falling = air && vy < -0.08;
      boolean crouch = p.isCrouching();
      double opening = Mth.clamp(1.0 - age / 18.0, 0.0, 1.0);               // a few big beats as the wings burst open
      double freq = air && !falling ? 0.34 : (speed > 0.05 ? 0.18 : 0.09);
      double amp = falling ? 0.06 : (air ? 0.5 : (speed > 0.05 ? 0.2 : 0.11)) + opening * 0.45;
      double sweep = Mth.clamp(speed * 3.0, 0.0, 0.7);
      double fold = crouch ? 0.65 : 0.0;
      double glide = falling ? 1.0 : 0.0;

      Vec3 back = base.add(0.0, h * 0.76, 0.0).subtract(fw.scale(0.2));
      for (int side = -1; side <= 1; side += 2) {
         Vec3 root = back.add(lf.scale(0.16 * side));
         Vec3[] w = demonWing(root, fw, lf, side, now, grow, freq, amp, sweep, fold, glide);
         Vec3[] wPrev = demonWing(root, fw, lf, side, now - 2.5, grow, freq, amp, sweep, fold, glide);
         drawDemonWing(vc, m, n, cam, w, wPrev, a, now, dark);
      }

      // ---- crown of black flame
      Vec3 crown = base.add(0.0, h + 0.02, 0.0);
      for (int k = 0; k < 10; k++) {
         boolean tallOne = k % 3 == 0 && k < 9;
         double ang = Math.PI * 2.0 * k / 10.0 + now * 0.025;
         double r0 = 0.26, span = tallOne ? 0.14 : 0.2;
         Vec3 b0 = crown.add(Math.cos(ang - span) * r0, 0.0, Math.sin(ang - span) * r0);
         Vec3 b1 = crown.add(Math.cos(ang + span) * r0, 0.0, Math.sin(ang + span) * r0);
         double tall = ((tallOne ? 0.42 : 0.22) + 0.12 * Math.sin(now * (tallOne ? 0.5 : 0.4) + k * 1.7)) * Math.min(1.0F, grow);
         Vec3 tip = crown.add(Math.cos(ang) * (tallOne ? 0.3 : 0.34), tall, Math.sin(ang) * (tallOne ? 0.3 : 0.34))
                         .subtract(fw.scale(sweep * 0.25 * tall));                // flames lean back when you run
         quad(vc, m, n, cam, b0, b1, tip, tip, dark, a * 0.9F, a * 0.9F, 0.0F, 0.0F);
         beam(vc, m, n, cam, crown.add(Math.cos(ang) * r0, 0.0, Math.sin(ang) * r0), tip, tallOne ? 0.045F : 0.03F, EMBER, a * 0.75F, 0.5);
      }

      // ---- mantle of shadow: rippling, blown back by movement
      for (int s = -1; s <= 1; s++) {
         Vec3 start = back.add(lf.scale(0.12 * s));
         Vec3 end = base.add(0.0, 0.2, 0.0).subtract(fw.scale(0.42 + 0.06 * Math.abs(s) + speed * 2.2)).add(lf.scale(0.28 * s)).add(0.0, speed * 1.2, 0.0);
         Vec3 prev = start;
         for (int k = 1; k <= 3; k++) {
            double t = k / 3.0 * Math.min(1.0F, grow);
            Vec3 q = start.add(end.subtract(start).scale(t)).add(lf.scale(Math.sin(now * 0.2 + k * 1.1 + s * 2.0) * 0.07 * k));
            beam(vc, m, n, cam, prev, q, 0.2F - k * 0.03F, EMBER, a * 0.5F, 0.7);
            prev = q;
         }
      }

      // ---- ember sigil under the feet
      if (!air) {
         Vec3 c = base.add(0.0, 0.03, 0.0);
         double rot = now * 0.03;
         float sa = a * 0.4F;
         Vec3 prevR = null;
         for (int k = 0; k <= 20; k++) {
            double an = Math.PI * 2.0 * k / 20.0 + rot;
            Vec3 q = c.add(Math.cos(an) * 1.1 * grow, 0.0, Math.sin(an) * 1.1 * grow);
            if (prevR != null) beam(vc, m, n, cam, prevR, q, 0.035F, EMBER, sa, 0.0);
            prevR = q;
         }
         for (int k = 0; k < 5; k++) {
            double a0 = -rot * 1.5 + Math.PI * 2.0 * k / 5.0, a1 = a0 + Math.PI * 4.0 / 5.0;
            beam(vc, m, n, cam, c.add(Math.cos(a0) * 0.95 * grow, 0.0, Math.sin(a0) * 0.95 * grow),
                                c.add(Math.cos(a1) * 0.95 * grow, 0.0, Math.sin(a1) * 0.95 * grow), 0.03F, EMBER, sa, 0.0);
         }
      }

      // ---- burst the moment the form begins
      if (age < 10.0) {
         float bt = (float)(age / 10.0);
         float br = 0.4F + bt * 4.0F;
         Vec3 c = base.add(0.0, 0.1, 0.0);
         Vec3 prevB = null;
         for (int k = 0; k <= 24; k++) {
            double an = Math.PI * 2.0 * k / 24.0;
            Vec3 q = c.add(Math.cos(an) * br, 0.0, Math.sin(an) * br);
            if (prevB != null) beam(vc, m, n, cam, prevB, q, 0.12F * (1.0F - bt), EMBER, fade * (1.0F - bt), 0.0);
            prevB = q;
         }
      }
   }

   // indexes into the demon wing skeleton
   private static final int WS = 0, WE = 1, WW = 2, WF = 3, WC0 = 11, WC1 = 12, WWAIST = 13;

   /**
    * Skeleton of one bat-like demon wing at time {@code t}: shoulder, elbow, wrist (with a hooked claw), four finger
    * bones of two joints each, and the waist anchor of the membrane. The arm swings from the shoulder, the elbow flexes a
    * beat later and the fingers spread on the downstroke / close on the upstroke; {@code fold} tucks the fingers down
    * along the arm, {@code glide} spreads everything flat and wide.
    * Result: [S, E, W, M0, T0, M1, T1, M2, T2, M3, T3, claw0, claw1, waist].
    */
   private static Vec3[] demonWing(Vec3 root, Vec3 fw, Vec3 lf, int side, double t, float grow,
                                   double freq, double amp, double sweep, double fold, double glide) {
      Vec3 out = lf.scale(side), up = new Vec3(0.0, 1.0, 0.0), back = fw.scale(-1.0);
      double ph = freq * t;
      double beat = Math.sin(ph);
      double elev = 1.12 * (1.0 - glide) + 0.55 * glide + amp * beat;
      elev = elev * (1.0 - fold) + 1.35 * fold;
      double tilt = 0.3 + sweep + fold * 0.6;
      double s = Math.max(0.05F, grow) * 1.25;
      Vec3[] r = new Vec3[14];
      r[WS] = root;
      r[WE] = root.add(wdir(out, up, back, elev, tilt).scale(0.55 * s));
      double bend = (0.6 + 0.25 * Math.sin(ph - 0.6)) * (1.0 - glide) + 0.35 * glide;
      bend = bend * (1.0 - fold) + 2.3 * fold;
      double fore = elev - bend;
      r[WW] = r[WE].add(wdir(out, up, back, fore, tilt + 0.1).scale(0.75 * s));
      double spread = (0.9 + 0.12 * Math.sin(ph + 1.2)) * (1.0 - glide) + 1.1 * glide;
      spread = spread * (1.0 - fold) + 0.25 * fold;
      double[] design = {0.35, -0.3, -0.85, -1.3};   // finger angles when fully open: outward-up ... down
      double[] lens = {1.1, 1.5, 1.45, 1.15};
      for (int j = 0; j < 4; j++) {
         double a1 = fore + (design[j] - 0.4) * spread;
         double len = lens[j] * s * (0.6 + 0.4 * (1.0 - fold));
         double b = tilt + 0.2 + j * 0.1;
         Vec3 mid = r[WW].add(wdir(out, up, back, a1, b).scale(len * 0.55));
         double curl = -0.15 + amp * 0.5 * Math.sin(ph - 0.9 - j * 0.25);   // finger tips lag the beat
         r[WF + j * 2] = mid;
         r[WF + j * 2 + 1] = mid.add(wdir(out, up, back, a1 + curl, b + 0.1).scale(len * 0.45));
      }
      r[WC0] = r[WW].add(wdir(out, up, back, fore + 1.3, tilt - 0.2).scale(0.18 * s));
      r[WC1] = r[WC0].add(wdir(out, up, back, fore + 2.3, tilt - 0.4).scale(0.1 * s));
      r[WWAIST] = root.add(0.0, -0.55, 0.0).add(out.scale(0.1));
      return r;
   }

   private static Vec3 wdir(Vec3 out, Vec3 up, Vec3 back, double ang, double tilt) {
      return out.scale(Math.cos(ang)).add(up.scale(Math.sin(ang))).add(back.scale(tilt)).normalize();
   }

   private static Vec3 sag(Vec3 a, Vec3 b, Vec3 toward, double k) {
      Vec3 midp = a.add(b).scale(0.5);
      return midp.add(toward.subtract(midp).scale(k));
   }

   /** Leathery membrane with a scalloped trailing edge, faint veins, glowing bones, a bone claw and tip trails. */
   private static void drawDemonWing(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3[] w, Vec3[] wPrev, float a, double now, float[] dark) {
      Vec3 S = w[WS], E = w[WE], W = w[WW], waist = w[WWAIST];
      float in = a * 0.62F, edge = a * 0.38F;
      // membrane: leading edge (shoulder-elbow-wrist-first finger)
      quad(vc, m, n, cam, S, E, W, w[WF], dark, in, in, in, edge);
      // membrane between fingers, sagging inward between the tips
      for (int j = 0; j < 3; j++) {
         Vec3 m0 = w[WF + j * 2], t0 = w[WF + j * 2 + 1], m1 = w[WF + j * 2 + 2], t1 = w[WF + j * 2 + 3];
         Vec3 sg = sag(t0, t1, W, 0.22);
         quad(vc, m, n, cam, W, m0, m1, W, dark, in, in, in, in);
         quad(vc, m, n, cam, m0, t0, sg, m1, dark, in, edge, edge, in);
         quad(vc, m, n, cam, sg, t1, m1, sg, dark, edge, edge, in, edge);
         beam(vc, m, n, cam, W, sg, 0.02F, EMBER, a * 0.25F, 0.5);   // faint vein
      }
      // membrane from the last finger down to the waist
      Vec3 m3 = w[WF + 6], t3 = w[WF + 7];
      Vec3 sgw = sag(t3, waist, W, 0.15);
      quad(vc, m, n, cam, m3, t3, sgw, waist, dark, in, edge, edge, in);
      quad(vc, m, n, cam, W, m3, waist, S, dark, in, in, in, in);
      quad(vc, m, n, cam, S, E, W, S, dark, in, in, in, in);
      // glowing trailing edge
      Vec3 prev = w[WF + 1];
      for (int j = 0; j < 3; j++) {
         Vec3 t0 = w[WF + j * 2 + 1], t1 = w[WF + j * 2 + 3], sg = sag(t0, t1, W, 0.22);
         beam(vc, m, n, cam, prev, sg, 0.035F, EMBER, a * 0.6F, 0.2);
         beam(vc, m, n, cam, sg, t1, 0.035F, EMBER, a * 0.6F, 0.2);
         prev = t1;
      }
      beam(vc, m, n, cam, t3, sgw, 0.035F, EMBER, a * 0.6F, 0.2);
      beam(vc, m, n, cam, sgw, waist, 0.035F, EMBER, a * 0.5F, 0.2);
      // bones: arm, then fingers, with a glow pulse running outward
      float pulse = 1.0F + 0.35F * (float)Math.sin(now * 0.3);
      beam(vc, m, n, cam, S, E, 0.13F * pulse, EMBER, a * 0.95F, 0.4);
      beam(vc, m, n, cam, E, W, 0.11F * pulse, EMBER, a * 0.95F, 0.4);
      for (int j = 0; j < 4; j++) {
         float fp = 1.0F + 0.4F * (float)Math.sin(now * 0.3 - 1.0 - j * 0.4);
         beam(vc, m, n, cam, W, w[WF + j * 2], 0.07F * fp, EMBER, a * 0.9F, 0.5);
         beam(vc, m, n, cam, w[WF + j * 2], w[WF + j * 2 + 1], 0.05F * fp, EMBER, a * 0.85F, 0.6);
      }
      // bone-white hooked claw at the wrist
      beam(vc, m, n, cam, W, w[WC0], 0.05F, 6, a, 0.3);
      beam(vc, m, n, cam, w[WC0], w[WC1], 0.035F, 6, a, 0.8);
      // ember trails off the two longest finger tips
      for (int j = 1; j <= 2; j++) beam(vc, m, n, cam, wPrev[WF + j * 2 + 1], w[WF + j * 2 + 1], 0.04F, EMBER, a * 0.4F, 0.8);
   }

   private static void heldAura(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Player p, float pt, float pulse, Item item, boolean main) {
      int pal;
      float len;
      float width;
      if (item == AddonItemRegistry.DEMON_SLAYER_SWORD.get()) {
         pal = 0;
         len = 3.0F;
         width = 0.34F;
      } else {
         if (item != AddonItemRegistry.DEMON_DWELLER_SWORD.get()) {
            return;
         }

         pal = 1;
         len = 2.1F;
         width = 0.24F;
      }

      double by = Math.toRadians((double)Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot));
      Vec3 fw = new Vec3(-Math.sin(by), 0.0, Math.cos(by));
      Vec3 lf = new Vec3(Math.cos(by), 0.0, Math.sin(by));
      boolean rightHand = p.getMainArm() == HumanoidArm.RIGHT == main;
      Vec3 base = new Vec3(
         Mth.lerp((double)pt, p.xo, p.getX()),
         Mth.lerp((double)pt, p.yo, p.getY()),
         Mth.lerp((double)pt, p.zo, p.getZ())
      );
      Vec3 hand = base.add(0.0, (double)p.getEyeHeight() * 0.62, 0.0).add(fw.scale(0.35)).add(lf.scale(rightHand ? -0.42 : 0.42));
      boolean dash = p.isAutoSpinAttack();
      Vec3 dir = dash ? p.getViewVector(pt).normalize() : fw.scale(0.5).add(0.0, 0.85, 0.0).normalize();
      // idle: a faint edge-glow along the blade; dashing: a stronger streak
      float a = (dash ? 0.9F : 0.4F) * pulse;
      beam(vc, m, n, cam, hand.subtract(dir.scale(0.25)), hand.add(dir.scale((double)len)), width * (dash ? 1.4F : 0.75F), pal, a, 0.5);
   }

   private static void wall(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, float env) {
      double lifeFrac = Mth.clamp(age / (double)Math.max(1, p.life), 0.0, 1.0);
      float prog = p.sweep > 0 ? (float)Mth.clamp(age / (double)p.sweep, 0.0, 1.0) : 1.0F;
      prog = prog * prog * (3.0F - 2.0F * prog);
      float R = p.r + (p.r2 - p.r) * (float)lifeFrac;
      Vec3 c = p.c.add(p.d.scale((double)p.speed * age));
      Vec3 h = p.d.cross(p.u).normalize();
      float a0 = p.a0;
      float a1 = p.a0 + (p.a1 - p.a0) * prog;
      int segs = Math.max(4, (int)(Math.abs(a1 - a0) / 7.0F));
      float[] body = BODY[p.palette];
      float[] rim = RIM[p.palette];
      float[] mul = new float[]{0.92F, 1.0F, 1.09F};
      float[] am = new float[]{0.45F, 1.0F, 0.35F};

      for (int layer = 0; layer < 3; layer++) {
         double rl = (double)(R * mul[layer]);

         for (int i = 0; i < segs; i++) {
            double t0 = (double)i / (double)segs;
            double t1 = (double)(i + 1) / (double)segs;
            double th0 = Math.toRadians((double)a0 + (double)(a1 - a0) * t0);
            double th1 = Math.toRadians((double)a0 + (double)(a1 - a0) * t1);
            Vec3 p0 = c.add(p.d.scale(Math.cos(th0) * rl)).add(p.u.scale(Math.sin(th0) * rl));
            Vec3 p1 = c.add(p.d.scale(Math.cos(th1) * rl)).add(p.u.scale(Math.sin(th1) * rl));
            double hh0 = (double)p.h * (0.3 + 0.7 * Math.pow(Math.sin(Math.PI * (0.04 + 0.92 * t0)), 0.6));
            double hh1 = (double)p.h * (0.3 + 0.7 * Math.pow(Math.sin(Math.PI * (0.04 + 0.92 * t1)), 0.6));
            float lead0 = 0.45F + 0.55F * (float)t0;
            float lead1 = 0.45F + 0.55F * (float)t1;
            float A0 = 0.62F * env * am[layer] * lead0;
            float A1 = 0.62F * env * am[layer] * lead1;
            quad(vc, m, n, cam, p0.add(h.scale(hh0)), p1.add(h.scale(hh1)), p1, p0, body, 0.0F, 0.0F, A1, A0);
            quad(vc, m, n, cam, p0, p1, p1.subtract(h.scale(hh1)), p0.subtract(h.scale(hh0)), body, A0, A1, 0.0F, 0.0F);
            if (layer == 1) {
               double lw = (double)p.h * 0.05;
               quad(
                  vc,
                  m,
                  n,
                  cam,
                  p0.add(h.scale(lw)),
                  p1.add(h.scale(lw)),
                  p1.subtract(h.scale(lw)),
                  p0.subtract(h.scale(lw)),
                  rim,
                  0.95F * env * lead0,
                  0.95F * env * lead1,
                  0.95F * env * lead1,
                  0.95F * env * lead0
               );
            }
         }
      }

      for (int ix = 0; ix < segs; ix++) {
         double t0 = (double)ix / (double)segs;
         double t1 = (double)(ix + 1) / (double)segs;
         double th0 = Math.toRadians((double)a0 + (double)(a1 - a0) * t0);
         double th1 = Math.toRadians((double)a0 + (double)(a1 - a0) * t1);
         Vec3 p0 = c.add(p.d.scale(Math.cos(th0) * (double)R)).add(p.u.scale(Math.sin(th0) * (double)R));
         Vec3 p1 = c.add(p.d.scale(Math.cos(th1) * (double)R)).add(p.u.scale(Math.sin(th1) * (double)R));
         double hh0 = (double)p.h * (0.3 + 0.7 * Math.pow(Math.sin(Math.PI * (0.04 + 0.92 * t0)), 0.6));
         double hh1 = (double)p.h * (0.3 + 0.7 * Math.pow(Math.sin(Math.PI * (0.04 + 0.92 * t1)), 0.6));

         for (int sgn = -1; sgn <= 1; sgn += 2) {
            Vec3 e0 = p0.add(h.scale(hh0 * (double)sgn));
            Vec3 e1 = p1.add(h.scale(hh1 * (double)sgn));
            Vec3 i0 = p0.add(h.scale(hh0 * (double)sgn * 0.9));
            Vec3 i1 = p1.add(h.scale(hh1 * (double)sgn * 0.9));
            quad(vc, m, n, cam, e0, e1, i1, i0, rim, 0.55F * env, 0.55F * env, 0.1F * env, 0.1F * env);
         }
      }
   }

   private static void crescentFx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxPacket p, double age, float env) {
      double lifeFrac = Mth.clamp(age / (double)Math.max(1, p.life), 0.0, 1.0);
      float R = p.r + (p.r2 - p.r) * (float)lifeFrac;
      Vec3 c = p.c.add(p.d.scale((double)p.speed * age));
      Vec3 h = p.d.cross(p.u).normalize();

      for (int j = 0; j < 5; j++) {
         float a = env * (1.0F - 0.2F * (float)j);
         crescent(vc, m, n, cam, c.subtract(p.d.scale(0.55 * (double)j)), p.d, p.u, (double)(R * (1.0F - 0.09F * (float)j)), p.palette, a);
      }

      crescent(vc, m, n, cam, c.add(p.d.scale(0.05)), p.d, h, (double)(R * 0.5F), p.palette, env * 0.55F);
   }

   private static void crescent(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 d, Vec3 u, double R, int pal, float env) {
      float[] body = BODY[pal];
      float[] rim = RIM[pal];
      int N = 16;

      for (int i = 0; i < N; i++) {
         double f0 = -78.0 + 156.0 * (double)i / (double)N;
         double f1 = -78.0 + 156.0 * (double)(i + 1) / (double)N;
         double r0 = Math.toRadians(f0);
         double r1 = Math.toRadians(f1);
         Vec3 o0 = c.add(u.scale(Math.sin(r0) * R)).add(d.scale((Math.cos(r0) - 1.0) * R * 0.55));
         Vec3 o1 = c.add(u.scale(Math.sin(r1) * R)).add(d.scale((Math.cos(r1) - 1.0) * R * 0.55));
         double t0 = 0.5 * R * Math.cos(Math.toRadians(f0 / 78.0 * 90.0));
         double t1 = 0.5 * R * Math.cos(Math.toRadians(f1 / 78.0 * 90.0));
         Vec3 i0 = o0.subtract(d.scale(t0));
         Vec3 i1 = o1.subtract(d.scale(t1));
         quad(vc, m, n, cam, o0, o1, i1, i0, body, 0.85F * env, 0.85F * env, 0.15F * env, 0.15F * env);
         double w0 = 0.09 * R * Math.cos(Math.toRadians(f0 / 78.0 * 90.0));
         double w1 = 0.09 * R * Math.cos(Math.toRadians(f1 / 78.0 * 90.0));
         quad(
            vc,
            m,
            n,
            cam,
            o0.add(d.scale(w0 * 0.3)),
            o1.add(d.scale(w1 * 0.3)),
            o1.subtract(d.scale(w1)),
            o0.subtract(d.scale(w0)),
            rim,
            0.98F * env,
            0.98F * env,
            0.7F * env,
            0.7F * env
         );
      }
   }

   private static void beam(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 p0, Vec3 p1, float width, int pal, float env, double endTaper) {
      Vec3 axis = p1.subtract(p0);
      double len = axis.length();
      if (!(len < 1.0E-4)) {
         Vec3 d = axis.scale(1.0 / len);
         Vec3 ref = Math.abs(d.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
         Vec3 n1 = d.cross(ref).normalize();
         Vec3 n2 = d.cross(n1).normalize();
         float[] body = BODY[pal];
         float[] rim = RIM[pal];
         int K = Math.max(4, Math.min(24, (int)(len * 2.0)));

         for (Vec3 nn : new Vec3[]{n1, n2}) {
            for (int k = 0; k < K; k++) {
               double t0 = (double)k / (double)K;
               double t1 = (double)(k + 1) / (double)K;
               double w0 = (double)width * Math.pow(Math.sin(Math.PI * (0.03 + 0.94 * t0)), endTaper);
               double w1 = (double)width * Math.pow(Math.sin(Math.PI * (0.03 + 0.94 * t1)), endTaper);
               Vec3 a = p0.add(axis.scale(t0));
               Vec3 b = p0.add(axis.scale(t1));
               float A = 0.7F * env;
               quad(vc, m, n, cam, a.add(nn.scale(w0)), b.add(nn.scale(w1)), b, a, body, 0.0F, 0.0F, A, A);
               quad(vc, m, n, cam, a, b, b.subtract(nn.scale(w1)), a.subtract(nn.scale(w0)), body, A, A, 0.0F, 0.0F);
               double cw0 = w0 * 0.12;
               double cw1 = w1 * 0.12;
               quad(
                  vc,
                  m,
                  n,
                  cam,
                  a.add(nn.scale(cw0)),
                  b.add(nn.scale(cw1)),
                  b.subtract(nn.scale(cw1)),
                  a.subtract(nn.scale(cw0)),
                  rim,
                  0.95F * env,
                  0.95F * env,
                  0.95F * env,
                  0.95F * env
               );
            }
         }
      }
   }

   private static void book(PoseStack ps, VertexConsumer vc, Vec3 cam, FxRenderer.BookDraw b) {
      ps.pushPose();
      ps.translate(b.pos.x - cam.x, b.pos.y - cam.y, b.pos.z - cam.z);
      ps.mulPose(Axis.YP.rotationDegrees(-b.yaw));
      ps.mulPose(Axis.YP.rotationDegrees(180.0F));
      ps.mulPose(Axis.XP.rotationDegrees(-b.tilt));
      ps.scale(b.scale, b.scale, b.scale);
      Matrix4f m = ps.last().pose();
      Matrix3f n = ps.last().normal();
      float HW = 0.5F;
      float HH = 0.36F;
      float V = (float)Math.toRadians(12.0);
      float al = b.alpha;

      for (int s = -1; s <= 1; s += 2) {
         float dx = (float)s * (float)Math.cos((double)V), dz = (float)Math.sin((double)V);
         panelOutline(vc, m, n, dx, dz, -0.004F, 0.5F, 0.36F, al, s);
         panel(vc, m, n, dx, dz, 0.0F, 0.5F, 0.36F, al);
      }

      if (b.flipCycle >= 0.0F) {
         for (int i = 0; i < 3; i++) {
            float sP = (b.flipCycle - (float)(4 + i * 5)) / 14.0F;
            if (!(sP <= 0.0F) && !(sP >= 1.0F)) {
               float mm = 0.5F - 0.5F * (float)Math.cos(Math.PI * (double)sP);
               double alpha = Math.toRadians((double)(-22.5F - 135.0F * mm));
               float lift = 0.015F * (1.0F - 2.0F * mm);
               panel(vc, m, n, (float)Math.cos(alpha), (float)(-Math.sin(alpha)), lift, 0.48F, 0.3456F, al * 0.9F);
            }
         }
      }

      ps.popPose();
   }

   private static void panel(VertexConsumer vc, Matrix4f m, Matrix3f n, float dx, float dz, float lift, float w, float h, float al) {
      float[] dark = new float[]{0.02F, 0.0F, 0.04F};
      float[] glow = new float[]{0.55F, 0.06F, 0.16F};
      float[] line = new float[]{0.05F, 0.0F, 0.08F};
      Vec3 a = pt(dx, dz, lift, 0.0F, -h);
      Vec3 b = pt(dx, dz, lift, w, -h);
      Vec3 c = pt(dx, dz, lift, w, h);
      Vec3 d = pt(dx, dz, lift, 0.0F, h);
      Vec3 z = Vec3.ZERO;
      quad(vc, m, n, z, a, b, c, d, dark, 0.16F * al, 0.16F * al, 0.16F * al, 0.16F * al);

      for (int i = 1; i <= 6; i++) {
         float y = -h + 2.0F * h * (float)i / 7.0F;
         float len = w * (i % 3 == 0 ? 0.55F : 0.82F);
         quad(
            vc,
            m,
            n,
            z,
            pt(dx, dz, lift, w * 0.1F, y - 0.006F),
            pt(dx, dz, lift, w * 0.1F + len, y - 0.006F),
            pt(dx, dz, lift, w * 0.1F + len, y + 0.006F),
            pt(dx, dz, lift, w * 0.1F, y + 0.006F),
            line,
            0.35F * al,
            0.35F * al,
            0.35F * al,
            0.35F * al
         );
      }

      float t = 0.028F;
      edge(vc, m, n, a, b, pt(dx, dz, lift, w, -h + t), pt(dx, dz, lift, 0.0F, -h + t), dark, al, glow);
      edge(vc, m, n, d, c, pt(dx, dz, lift, w, h - t), pt(dx, dz, lift, 0.0F, h - t), dark, al, glow);
      edge(vc, m, n, a, d, pt(dx, dz, lift, t, h), pt(dx, dz, lift, t, -h), dark, al, glow);
      edge(vc, m, n, b, c, pt(dx, dz, lift, w - t, h), pt(dx, dz, lift, w - t, -h), dark, al, glow);
   }

   /**
    * Black-flame outline around one open page: a soft dark-crimson halo hugging the outer three edges
    * (the spine edge is skipped), whose width flickers along its length like licking flame. Kept thin and
    * translucent so the book stays readable.
    */
   private static void panelOutline(VertexConsumer vc, Matrix4f m, Matrix3f n, float dx, float dz, float lift, float w, float h, float al, int seed) {
      float[] core = new float[]{0.42F, 0.03F, 0.08F};
      float[] smoke = new float[]{0.05F, 0.0F, 0.04F};
      // outer three edges as (start u,y) -> (end u,y) with outward normal (nu,ny); top edge gets taller flames
      float[][] edges = new float[][]{
         {0.0F, -h, w, -h, 0.0F, -1.0F, 0.7F},
         {w, -h, w, h, 1.0F, 0.0F, 1.0F},
         {w, h, 0.0F, h, 0.0F, 1.0F, 1.6F}
      };
      int N = 10;
      double t = bookTime * 0.22 + seed * 3.1;
      for (int e = 0; e < edges.length; e++) {
         float[] E = edges[e];
         for (int i = 0; i < N; i++) {
            float f0 = (float)i / N, f1 = (float)(i + 1) / N;
            float u0 = E[0] + (E[2] - E[0]) * f0, y0 = E[1] + (E[3] - E[1]) * f0;
            float u1 = E[0] + (E[2] - E[0]) * f1, y1 = E[1] + (E[3] - E[1]) * f1;
            int k0 = e * N + i, k1 = k0 + 1;
            float x0 = (float)(0.035 + 0.03 * Math.sin(t + k0 * 1.37) + 0.015 * Math.sin(t * 2.3 + k0 * 0.71)) * E[6];
            float x1 = (float)(0.035 + 0.03 * Math.sin(t + k1 * 1.37) + 0.015 * Math.sin(t * 2.3 + k1 * 0.71)) * E[6];
            x0 = Math.max(0.012F, x0);
            x1 = Math.max(0.012F, x1);
            Vec3 a = pt(dx, dz, lift, u0, y0), b = pt(dx, dz, lift, u1, y1);
            Vec3 ao = pt(dx, dz, lift, u0 + E[4] * x0, y0 + E[5] * x0), bo = pt(dx, dz, lift, u1 + E[4] * x1, y1 + E[5] * x1);
            Vec3 ao2 = pt(dx, dz, lift, u0 + E[4] * x0 * 1.9F, y0 + E[5] * x0 * 1.9F), bo2 = pt(dx, dz, lift, u1 + E[4] * x1 * 1.9F, y1 + E[5] * x1 * 1.9F);
            quad(vc, m, n, Vec3.ZERO, a, b, bo, ao, core, 0.55F * al, 0.55F * al, 0.12F * al, 0.12F * al);    // crimson inner glow
            quad(vc, m, n, Vec3.ZERO, ao, bo, bo2, ao2, smoke, 0.35F * al, 0.35F * al, 0.0F, 0.0F);          // dark smoky fringe
         }
      }
   }

   private static void edge(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 o0, Vec3 o1, Vec3 i1, Vec3 i0, float[] dark, float al, float[] glow) {
      quad(vc, m, n, Vec3.ZERO, o0, o1, i1, i0, dark, 0.95F * al, 0.95F * al, 0.95F * al, 0.95F * al);
      quad(
         vc,
         m,
         n,
         Vec3.ZERO,
         i0,
         i1,
         i1.add(i1.subtract(o1).scale(0.25)),
         i0.add(i0.subtract(o0).scale(0.25)),
         glow,
         0.65F * al,
         0.65F * al,
         0.0F,
         0.0F
      );
   }

   private static Vec3 pt(float dx, float dz, float lift, float u, float y) {
      return new Vec3((double)(dx * u), (double)y, (double)(dz * u + lift));
   }

   private static record AuraDraw(Vec3 a, Vec3 b, float width, int pal, float alpha) {
   }

   private static record BookDraw(Vec3 pos, float yaw, float tilt, float scale, float alpha, float flipCycle) {
   }

   private static class Fx {
      FxPacket p;
      double born;
      double killAge = -1.0;
   }
}
