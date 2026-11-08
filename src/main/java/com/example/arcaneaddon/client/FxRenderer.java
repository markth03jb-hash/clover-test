package com.example.arcaneaddon.client;

import com.example.arcaneaddon.network.FxPacket;
import com.example.arcaneaddon.registry.AddonItemRegistry;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
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
      {1.0F, 0.1F, 0.06F}, {0.67F, 0.24F, 1.0F}, {1.0F, 0.6F, 0.12F}, {0.6F, 0.88F, 1.0F}, {1.0F, 0.96F, 0.44F}, {0.75F, 1.0F, 0.84F}, {0.95F, 0.95F, 1.0F},
      {0.78F, 0.85F, 0.94F},   // 7 steel
      {1.0F, 0.84F, 0.36F},    // 8 gold
      {1.0F, 0.30F, 0.07F}     // 9 ember (devil form)
   };
   /** Overall strength of every world effect. Lower = subtler. */
   private static final float FX_ALPHA = 1.0F;
   /** Effects closer than this to the camera fade out so they never cover the screen (first person). */
   private static final double NEAR_FADE_START = 0.8;
   private static final double NEAR_FADE_RANGE = 2.4;
   private static final float NEAR_FADE_MIN = 0.15F;
   private static boolean nearFade = false;
   private static double bookTime = 0.0;
   private static final List<FxRenderer.Fx> FX = new ArrayList<>();
   /** Players in Devil Union: entity id -> {start tick, end tick}. Fed by FxPacket shape 6. */
   private static final java.util.Map<Integer, double[]> DEVIL = new java.util.HashMap<>();
   private static final int EMBER = 9;
   private static final List<FxRenderer.BookDraw> BOOKS = new ArrayList<>();
   private static final List<FxRenderer.AuraDraw> AURAS = new ArrayList<>();
   /** Separate buffer for the additive glow layer (cores, rims, metal highlights). */
   private static final MultiBufferSource.BufferSource GLOW_SRC = MultiBufferSource.immediate(new BufferBuilder(262144));
   private static VertexConsumer gv;
   /** Key light for the shaded 3D solids. */
   private static final Vec3 LIGHT = new Vec3(0.35, 1.0, 0.25).normalize();

   /**
    * Black-flame outline and turning pages for an open grimoire drawn at {@code pos} with the same transform as its
    * 3D model. hw/hh = page width / half height and vDeg = how far the boards open from flat, in model blocks.
    */
   public static void queueBook(Vec3 pos, float yawS, float tiltDeg, float scale, float alpha, float flipCycle, float hw, float hh, float vDeg, int pal, int style, int light) {
      BOOKS.add(new FxRenderer.BookDraw(pos, yawS, tiltDeg, scale, alpha, flipCycle, hw, hh, vDeg, pal, style, light));
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
      if (p.shape == 12) {  // impact frame: life = ticks
         Cutscene.impactFrame(p.life);
         return;
      }
      if (p.shape == 11) {  // Knight's Armor form: id = player, life = ticks left (0 = off)
         KnightArmorLayer.set(p.id, p.life);
         return;
      }
      if (p.shape == 9) {   // Black Meteorite cutscene: id = player, palette = phase (0 start, 1 impact), c = target
         Cutscene.handle(p.id, p.palette, p.c);
         return;
      }
      if (p.shape == 10) {  // screen shake: c = where, r = strength, life = ticks
         Cutscene.shake(p.c, p.r, p.life);
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

   /** True while the player with this entity id is in Devil Union (as far as this client knows). */
   public static boolean inDevilForm(int entityId, double now) {
      double[] f = DEVIL.get(entityId);
      return f != null && now >= f[0] && now <= f[1];
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

   // ------------------------------------------------------------------ additive glow layer
   private static void gvtx(Matrix4f m, Vec3 cam, Vec3 w, float[] col, float a) {
      if (gv == null) return;
      if (nearFade) {
         double d = w.distanceTo(cam);
         a *= Mth.clamp((float)((d - NEAR_FADE_START) / NEAR_FADE_RANGE), NEAR_FADE_MIN, 1.0F) * FX_ALPHA;
      }
      gv.vertex(m, (float)(w.x - cam.x), (float)(w.y - cam.y), (float)(w.z - cam.z)).color(col[0], col[1], col[2], Mth.clamp(a, 0.0F, 1.0F)).endVertex();
   }

   private static void gquad(Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float[] col, float aa, float ab, float ac, float ad) {
      gvtx(m, cam, a, col, aa);
      gvtx(m, cam, b, col, ab);
      gvtx(m, cam, c, col, ac);
      gvtx(m, cam, d, col, ad);
   }

   /** A camera-facing glowing line: white-hot core with a soft coloured falloff. */
   private static void gline(Matrix4f m, Vec3 cam, Vec3 p0, Vec3 p1, float w, int pal, float a) {
      Vec3 ax = p1.subtract(p0);
      if (ax.lengthSqr() < 1.0E-8) return;
      Vec3 side = ax.cross(p0.add(p1).scale(0.5).subtract(cam));
      if (side.lengthSqr() < 1.0E-8) return;
      side = side.normalize();
      float[] rim = RIM[pal], hot = hot(pal);
      Vec3 c0 = side.scale(w * 0.35), o0 = side.scale(w);
      gquad(m, cam, p0.add(c0), p1.add(c0), p1.subtract(c0), p0.subtract(c0), hot, a, a, a, a);
      gquad(m, cam, p0.add(c0), p1.add(c0), p1.add(o0), p0.add(o0), rim, a * 0.6F, a * 0.6F, 0.0F, 0.0F);
      gquad(m, cam, p0.subtract(c0), p1.subtract(c0), p1.subtract(o0), p0.subtract(o0), rim, a * 0.6F, a * 0.6F, 0.0F, 0.0F);
   }

   private static float[] hot(int pal) {
      float[] r = RIM[pal];
      float w = isFlame(pal) ? 0.3F : 0.55F;   // black-flame palettes keep a red-orange core instead of white
      return new float[]{r[0] + (1.0F - r[0]) * w, r[1] + (1.0F - r[1]) * w, r[2] + (1.0F - r[2]) * w};
   }

   // ------------------------------------------------------------------ black flame (Asta's anti magic look)
   private static double fxTime;
   private static final float[] SMOKE = new float[]{0.015F, 0.0F, 0.008F};

   /** Asta's palettes (slayer crimson, dweller dark, devil ember) burn with black flame and a red underglow. */
   private static boolean isFlame(int pal) {
      return pal == 0 || pal == 1 || pal == 9;
   }

   /**
    * One licking tongue of black flame on the edge a-b, leaning along {@code out} and rising: a black smoke tongue in
    * front of a larger red glow tongue, flickering over time.
    */
   private static void flameTongue(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, Vec3 out, double height, int pal, float al, int seed) {
      if (al <= 0.01F || height <= 0.0) return;
      Vec3 mid = a.add(b).scale(0.5), along = b.subtract(a);
      double t = fxTime;
      double hg = height * (0.5 + 0.5 * Math.abs(Math.sin(t * 0.45 + seed * 1.71)) + 0.15 * Math.sin(t * 1.3 + seed * 0.37));
      Vec3 tip = mid.add(out.scale(hg)).add(0.0, hg * 0.45, 0.0).add(along.scale(0.35 * Math.sin(t * 0.33 + seed * 2.3)));
      Vec3 tipG = mid.add(tip.subtract(mid).scale(1.35));
      float[] rim = RIM[pal];
      gquad(m, cam, a.subtract(along.scale(0.2)), b.add(along.scale(0.2)), tipG, tipG, rim, al * 0.6F, al * 0.6F, 0.0F, 0.0F);
      quad(vc, m, n, cam, a, b, tip, tip, SMOKE, al * 0.85F, al * 0.85F, 0.0F, 0.0F);
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
            gv = GLOW_SRC.getBuffer(GlowRenderType.GLOW);
            double now = (double)((float)mc.level.getGameTime() + pt);
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            nearFade = true;
            bookTime = now;
            fxTime = now;

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
                        break;
                     case 7:
                        boxFx(vc, m, n, cam, f, age, life);
                        break;
                     case 8:
                        spikeFx(vc, m, n, cam, f, age, life);
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
            // open grimoires: black-flame aura (smoke in the translucent pass, red underglow in the glow pass)
            for (FxRenderer.BookDraw b : BOOKS) {
               book(ps, vc, cam, b);
            }
            BOOKS.clear();
            buf.endBatch(rt);
            GLOW_SRC.endBatch(GlowRenderType.GLOW);
            gv = null;
            if (!PAGES.isEmpty()) {   // turning pages, drawn with the book's own page art (front = right page, back = left page)
               for (int side = 0; side < 2; side++) {
                  RenderType prt = RenderType.entityCutout(side == 0 ? PAGE_R : PAGE_L);
                  VertexConsumer pv = buf.getBuffer(prt);
                  for (PageDraw pd : PAGES) pageQuad(pv, pd, side == 1);
                  buf.endBatch(prt);
               }
               PAGES.clear();
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
      if (!dash) return;   // no glow stuck on the sword at rest - only the dash streak
      Vec3 dir = dash ? p.getViewVector(pt).normalize() : fw.scale(0.5).add(0.0, 0.85, 0.0).normalize();
      // idle: a faint edge-glow along the blade; dashing: a stronger streak
      float a = (dash ? 0.9F : 0.4F) * pulse;
      beam(vc, m, n, cam, hand.subtract(dir.scale(0.25)), hand.add(dir.scale((double)len)), width * (dash ? 1.4F : 0.75F), pal, a, 0.5);
   }

   // ------------------------------------------------------------------ 3D solids (steel plates, bars, spikes...)
   private static double backOut(double x) {
      x = Mth.clamp(x, 0.0, 1.0) - 1.0;
      return 1.0 + 2.2 * x * x * x + 1.2 * x * x;
   }

   private static double smooth(double x) {
      x = Mth.clamp(x, 0.0, 1.0);
      return x * x * (3.0 - 2.0 * x);
   }

   /** How far a moving solid has travelled: stops when its move time is up, or where it was killed. */
   private static Vec3 travelled(FxRenderer.Fx f, double age) {
      double mt = f.p.a1 > 0.0F ? Math.min(age, (double)f.p.a1) : age;
      if (f.killAge >= 0.0) mt = Math.min(mt, f.killAge);
      return f.p.c.add(f.p.v.scale(mt));
   }

   private static float[] solidLo(int pal) {
      float[] b = BODY[pal];
      return new float[]{b[0] * 1.6F + 0.06F, b[1] * 1.6F + 0.06F, b[2] * 1.6F + 0.07F};
   }

   /** Lit colour of a face: lambert from the key light plus a fresnel rim toward the camera. */
   private static float[] shade(int pal, Vec3 nrm, Vec3 view, float mul) {
      float lam = (float)Math.max(0.0, nrm.dot(LIGHT));
      float fres = 1.0F - (float)Math.abs(nrm.dot(view));
      float k = Mth.clamp(0.16F + 0.62F * lam + 0.3F * fres * fres, 0.0F, 1.0F) * mul;
      float[] lo = solidLo(pal), hi = RIM[pal];
      return new float[]{lo[0] + (hi[0] - lo[0]) * k, lo[1] + (hi[1] - lo[1]) * k, lo[2] + (hi[2] - lo[2]) * k};
   }

   /** One flat-shaded face (quad, or triangle with d == c). Returns false when it faces away and was skipped. */
   private static boolean face(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 nrm, int pal, float al, Vec3 up, Vec3 ctr) {
      Vec3 mid = a.add(b).add(c).add(d).scale(0.25);
      Vec3 view = mid.subtract(cam);
      double vl = view.length();
      if (vl < 1.0E-6 || nrm.dot(view) > 0.0) return false;
      view = view.scale(1.0 / vl);
      float[] ca = shade(pal, nrm, view, a.subtract(ctr).dot(up) > 0.0 ? 1.0F : 0.78F);
      float[] cb = shade(pal, nrm, view, b.subtract(ctr).dot(up) > 0.0 ? 1.0F : 0.78F);
      float[] cc = shade(pal, nrm, view, c.subtract(ctr).dot(up) > 0.0 ? 1.0F : 0.78F);
      float[] cd = shade(pal, nrm, view, d.subtract(ctr).dot(up) > 0.0 ? 1.0F : 0.78F);
      vtx(vc, m, n, cam, a, ca, al);
      vtx(vc, m, n, cam, b, cb, al);
      vtx(vc, m, n, cam, c, cc, al);
      vtx(vc, m, n, cam, d, cd, al);
      return true;
   }

   /** BOX: a shaded block that pops in, rises out of the ground, or extends along its axis; can spin and travel. */
   private static void boxFx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxRenderer.Fx f, double age, double life) {
      FxPacket p = f.p;
      Vec3 ax = p.d;
      Vec3 s0 = ax.cross(p.u);
      if (s0.lengthSqr() < 1.0E-6) s0 = ax.cross(new Vec3(1.0, 0.0, 0.0));
      s0 = s0.normalize();
      Vec3 u0 = s0.cross(ax).normalize();
      double th = Math.toRadians((double)p.a0 * age);
      Vec3 side = s0.scale(Math.cos(th)).add(u0.scale(Math.sin(th)));
      Vec3 upv = u0.scale(Math.cos(th)).subtract(s0.scale(Math.sin(th)));
      double in = p.sweep > 0 ? Mth.clamp(age / (double)p.sweep, 0.0, 1.0) : 1.0;
      double out = smooth((life - age) / Math.min(6.0, Math.max(2.0, life * 0.3)));
      double hl = p.r, hw = p.r2, hh = p.h;
      Vec3 c = travelled(f, age);
      if (p.speed == FxPacket.GROUND) {
         c = c.subtract(u0.scale(2.0 * hh * (1.0 - backOut(in)) + 2.0 * hh * (1.0 - out)));
      } else if (p.speed == FxPacket.EXTEND) {
         double L = hl * in;
         c = c.subtract(ax.scale(hl - L));
         hl = Math.max(0.001, L);
         hw *= out;
         hh *= out;
      } else if (p.speed != FxPacket.HOLD) {
         double k = backOut(in) * out;
         hl *= k; hw *= k; hh *= k;
      }
      if (hl < 0.002 || hw < 0.002 || hh < 0.002) return;
      float al = p.speed == FxPacket.HOLD ? 0.96F : 0.96F * (float)Mth.clamp(age * 2.0 + 0.3, 0.0, 1.0);
      drawBox(vc, m, n, cam, c, ax, side, upv, hl, hw, hh, p.palette, al, 1.0F);
   }

   /** A shaded box with glowing visible edges (brighter along the top). */
   private static void drawBox(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 ax, Vec3 side, Vec3 upv,
                               double hl, double hw, double hh, int pal, float al, float edgeGlow) {
      Vec3[] k = new Vec3[8];
      for (int i = 0; i < 8; i++) {
         k[i] = c.add(ax.scale((i & 1) != 0 ? hl : -hl)).add(side.scale((i & 2) != 0 ? hw : -hw)).add(upv.scale((i & 4) != 0 ? hh : -hh));
      }
      Vec3 wup = new Vec3(0.0, 1.0, 0.0);
      // visible[bit index][sign]
      boolean[][] vis = new boolean[3][2];
      vis[0][1] = face(vc, m, n, cam, k[1], k[3], k[7], k[5], ax, pal, al, wup, c);
      vis[0][0] = face(vc, m, n, cam, k[0], k[4], k[6], k[2], ax.scale(-1.0), pal, al, wup, c);
      vis[1][1] = face(vc, m, n, cam, k[2], k[6], k[7], k[3], side, pal, al, wup, c);
      vis[1][0] = face(vc, m, n, cam, k[0], k[1], k[5], k[4], side.scale(-1.0), pal, al, wup, c);
      vis[2][1] = face(vc, m, n, cam, k[4], k[5], k[7], k[6], upv, pal, al, wup, c);
      vis[2][0] = face(vc, m, n, cam, k[0], k[2], k[3], k[1], upv.scale(-1.0), pal, al, wup, c);
      if (edgeGlow <= 0.0F) return;
      float lw = (float)Mth.clamp(Math.min(hw, hh) * 0.35, 0.012, 0.06);
      for (int i = 0; i < 8; i++) {
         for (int b = 0; b < 3; b++) {
            int bit = 1 << b;
            if ((i & bit) != 0) continue;
            int j = i | bit;
            int b1 = (b + 1) % 3, b2 = (b + 2) % 3;
            boolean v1 = vis[b1][(i >> b1) & 1], v2 = vis[b2][(i >> b2) & 1];
            if (!v1 && !v2) continue;
            boolean top = b != 2 && (i & 4) != 0;
            boolean sil = v1 != v2;   // silhouette edges glow a little stronger
            gline(m, cam, k[i], k[j], lw, pal, al * edgeGlow * (top ? 0.55F : (sil ? 0.32F : 0.18F)));
         }
      }
   }

   /** SPIKE: a faceted six-sided steel spike with a collar; erupts (overshooting), can spin and travel, retracts at the end. */
   private static void spikeFx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, FxRenderer.Fx f, double age, double life) {
      FxPacket p = f.p;
      double in = p.sweep > 0 ? Mth.clamp(age / (double)p.sweep, 0.0, 1.0) : 1.0;
      double out = smooth((life - age) / Math.min(6.0, Math.max(2.0, life * 0.3)));
      double k = p.speed == FxPacket.HOLD ? 1.0 : (p.speed == FxPacket.EXTEND ? in : backOut(in)) * out;
      double L = p.r * k;
      double R = p.r2 * (0.45 + 0.55 * Math.min(1.0, k));
      if (L < 0.01) return;
      float al = p.speed == FxPacket.HOLD ? 0.96F : 0.96F * (float)Mth.clamp(age * 2.0 + 0.3, 0.0, 1.0);
      drawSpike(vc, m, n, cam, travelled(f, age), p.d, L, R, Math.toRadians((double)p.a0 * age), p.palette, al);
      // a glint off the tip right as it finishes erupting
      double g = age - (double)p.sweep;
      if (g > -1.0 && g < 6.0) {
         Vec3 tip = travelled(f, age).add(p.d.scale(L));
         float ga = al * (float)(1.0 - Math.abs(g - 1.0) / 5.0);
         double gs = Math.max(0.25, R * 2.4);
         Vec3 view = tip.subtract(cam).normalize();
         Vec3 g1 = view.cross(new Vec3(0.0, 1.0, 0.0));
         if (g1.lengthSqr() < 1.0E-4) g1 = new Vec3(1.0, 0.0, 0.0);
         g1 = g1.normalize();
         Vec3 g2 = view.cross(g1).normalize();
         gline(m, cam, tip.subtract(g1.scale(gs)), tip.add(g1.scale(gs)), 0.04F, p.palette, ga);
         gline(m, cam, tip.subtract(g2.scale(gs)), tip.add(g2.scale(gs)), 0.04F, p.palette, ga);
      }
   }

   private static void drawSpike(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 base, Vec3 d, double L, double R, double spin, int pal, float al) {
      Vec3 ref = Math.abs(d.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
      Vec3 e1 = d.cross(ref).normalize(), e2 = d.cross(e1).normalize();
      int N = 6;
      Vec3[] r0 = new Vec3[N], r1 = new Vec3[N];
      Vec3 collar = base.add(d.scale(L * 0.3));
      for (int j = 0; j < N; j++) {
         double a0 = spin + Math.PI * 2.0 * j / N, a1 = a0 + Math.PI / N;
         r0[j] = base.add(e1.scale(Math.cos(a0) * R)).add(e2.scale(Math.sin(a0) * R));
         r1[j] = collar.add(e1.scale(Math.cos(a1) * R * 0.72)).add(e2.scale(Math.sin(a1) * R * 0.72));
      }
      Vec3 tip = base.add(d.scale(L));
      Vec3 ctr = base.add(d.scale(L * 0.4));
      boolean[] tipVis = new boolean[N];
      for (int j = 0; j < N; j++) {
         int jn = (j + 1) % N;
         tri(vc, m, n, cam, r0[j], r0[jn], r1[j], base, d, pal, al, ctr);
         tri(vc, m, n, cam, r1[j], r0[jn], r1[jn], base, d, pal, al, ctr);
         tipVis[j] = tri(vc, m, n, cam, r1[j], r1[jn], tip, base, d, pal, al, ctr);
      }
      float lw = (float)Mth.clamp(R * 0.18, 0.012, 0.05);
      for (int j = 0; j < N; j++) {
         if (tipVis[j] || tipVis[(j + N - 1) % N]) gline(m, cam, r1[j], tip, lw, pal, al * 0.4F);
      }
   }

   private static boolean tri(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 base, Vec3 d, int pal, float al, Vec3 ctr) {
      Vec3 nrm = b.subtract(a).cross(c.subtract(a));
      if (nrm.lengthSqr() < 1.0E-10) return false;
      nrm = nrm.normalize();
      Vec3 mid = a.add(b).add(c).scale(1.0 / 3.0);
      Vec3 axisPt = base.add(d.scale(mid.subtract(base).dot(d)));
      if (nrm.dot(mid.subtract(axisPt)) < 0.0) nrm = nrm.scale(-1.0);
      return face(vc, m, n, cam, a, b, c, c, nrm, pal, al, d, ctr);
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
               double lw = (double)p.h * 0.07;
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
               // glow: white-hot cutting edge plus a soft bloom either side
               float[] hc = hot(p.palette);
               double bw = (double)p.h * 0.32;
               gquad(m, cam, p0.add(h.scale(lw)), p1.add(h.scale(lw)), p1.subtract(h.scale(lw)), p0.subtract(h.scale(lw)), hc,
                  0.9F * env * lead0, 0.9F * env * lead1, 0.9F * env * lead1, 0.9F * env * lead0);
               gquad(m, cam, p0.add(h.scale(lw)), p1.add(h.scale(lw)), p1.add(h.scale(bw)), p0.add(h.scale(bw)), rim, 0.45F * env * lead0, 0.45F * env * lead1, 0.0F, 0.0F);
               gquad(m, cam, p0.subtract(h.scale(lw)), p1.subtract(h.scale(lw)), p1.subtract(h.scale(bw)), p0.subtract(h.scale(bw)), rim, 0.45F * env * lead0, 0.45F * env * lead1, 0.0F, 0.0F);
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
            gquad(m, cam, e0, e1, i1, i0, rim, 0.35F * env, 0.35F * env, 0.0F, 0.0F);
            if (isFlame(p.palette)) flameTongue(vc, m, n, cam, e0, e1, h.scale(sgn), p.h * 0.6 + 0.12, p.palette, env, ix * 2 + sgn + p.id);
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
      if (isFlame(p.palette)) {
         int N = 12;
         for (int i = 0; i < N; i++) {
            double r0 = Math.toRadians(-78.0 + 156.0 * i / N), r1 = Math.toRadians(-78.0 + 156.0 * (i + 1) / N);
            Vec3 o0 = c.add(p.u.scale(Math.sin(r0) * R)).add(p.d.scale((Math.cos(r0) - 1.0) * R * 0.55));
            Vec3 o1 = c.add(p.u.scale(Math.sin(r1) * R)).add(p.d.scale((Math.cos(r1) - 1.0) * R * 0.55));
            double rm = (r0 + r1) * 0.5;
            Vec3 out = p.u.scale(Math.sin(rm)).add(p.d.scale(Math.cos(rm) * 0.55)).subtract(p.d.scale(0.6)).normalize();   // outward, trailing behind
            flameTongue(vc, m, n, cam, o0, o1, out, R * 0.28, p.palette, env, i + p.id * 7);
         }
      }
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
         // glow: white-hot blade edge and a bloom ahead of it, so the slash reads from far away
         float[] hc = hot(pal);
         gquad(m, cam, o0.add(d.scale(w0 * 0.3)), o1.add(d.scale(w1 * 0.3)), o1.subtract(d.scale(w1 * 0.6)), o0.subtract(d.scale(w0 * 0.6)), hc, env, env, 0.8F * env, 0.8F * env);
         gquad(m, cam, o0.add(d.scale(w0 * 0.3)), o1.add(d.scale(w1 * 0.3)), o1.add(d.scale(w1 * 2.6)), o0.add(d.scale(w0 * 2.6)), rim, 0.5F * env, 0.5F * env, 0.0F, 0.0F);
         gquad(m, cam, o0.subtract(d.scale(w0 * 0.6)), o1.subtract(d.scale(w1 * 0.6)), i1, i0, rim, 0.45F * env, 0.45F * env, 0.0F, 0.0F);
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
         if (isFlame(pal) && width > 0.15F) {
            Vec3 rise = new Vec3(0.0, 1.0, 0.0).subtract(d.scale(d.y));
            rise = rise.lengthSqr() < 1.0E-4 ? n1 : rise.normalize();
            for (int k = 0; k < K; k++) {
               double t0 = (double)k / K, t1 = (double)(k + 1) / K;
               double wf = width * Math.pow(Math.sin(Math.PI * (0.03 + 0.94 * (t0 + t1) * 0.5)), endTaper);
               flameTongue(vc, m, n, cam, p0.add(axis.scale(t0)).add(rise.scale(wf * 0.3)), p0.add(axis.scale(t1)).add(rise.scale(wf * 0.3)), rise, wf * 1.3, pal, env * 0.9F, k * 3 + (int)(p0.x * 7.0));
            }
         }

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
               // glow: hot core and bloom
               float[] hc = hot(pal);
               double gc0 = w0 * 0.16, gc1 = w1 * 0.16, gb0 = w0 * 0.75, gb1 = w1 * 0.75;
               gquad(m, cam, a.add(nn.scale(gc0)), b.add(nn.scale(gc1)), b.subtract(nn.scale(gc1)), a.subtract(nn.scale(gc0)), hc, 0.8F * env, 0.8F * env, 0.8F * env, 0.8F * env);
               gquad(m, cam, a.add(nn.scale(gc0)), b.add(nn.scale(gc1)), b.add(nn.scale(gb1)), a.add(nn.scale(gb0)), rim, 0.35F * env, 0.35F * env, 0.0F, 0.0F);
               gquad(m, cam, a.subtract(nn.scale(gc0)), b.subtract(nn.scale(gc1)), b.subtract(nn.scale(gb1)), a.subtract(nn.scale(gb0)), rim, 0.35F * env, 0.35F * env, 0.0F, 0.0F);
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
      float HW = b.hw;
      float HH = b.hh;
      float V = (float)Math.toRadians((double)b.vDeg);
      float al = b.alpha;

      // the book itself is the real 3D model (drawn by GrimoireFloatRenderer); here each grimoire's own aura
      bookAura(vc, m, n, b, HW, HH, V, al);

      if (b.flipCycle >= 0.0F) {
         for (int i = 0; i < 3; i++) {
            float sP = (b.flipCycle - (float)(4 + i * 5)) / 14.0F;
            if (!(sP <= 0.0F) && !(sP >= 1.0F)) {
               float mm = 0.5F - 0.5F * (float)Math.cos(Math.PI * (double)sP);
               double alpha = Math.toRadians((double)(-22.5F - 135.0F * mm));
               float lift = 0.07F + 0.03F * (1.0F - 2.0F * Math.abs(mm - 0.5F));
               PAGES.add(new PageDraw(new Matrix4f(m), new Matrix3f(n), (float)Math.cos(alpha), (float)(-Math.sin(alpha)), lift, HW * 0.95F, HH * 0.92F, b.light));
            }
         }
      }

      ps.popPose();
   }

   /** A flat glowing ribbon in the book's plane (local frame, viewer roughly along +z). */
   private static void lline(Matrix4f m, Vec3 p0, Vec3 p1, float w, int pal, float a0, float a1) {
      Vec3 ax = p1.subtract(p0);
      if (ax.lengthSqr() < 1.0E-8) return;
      Vec3 side = ax.cross(new Vec3(0.0, 0.0, 1.0));
      if (side.lengthSqr() < 1.0E-8) side = new Vec3(1.0, 0.0, 0.0);
      side = side.normalize().scale(w);
      float[] hc = hot(pal), rim = RIM[pal];
      Vec3 c = side.scale(0.35);
      gquad(m, Vec3.ZERO, p0.add(c), p1.add(c), p1.subtract(c), p0.subtract(c), hc, a0, a1, a1, a0);
      gquad(m, Vec3.ZERO, p0.add(c), p1.add(c), p1.add(side), p0.add(side), rim, a0 * 0.55F, a1 * 0.55F, 0.0F, 0.0F);
      gquad(m, Vec3.ZERO, p0.subtract(c), p1.subtract(c), p1.subtract(side), p0.subtract(side), rim, a0 * 0.55F, a1 * 0.55F, 0.0F, 0.0F);
   }

   /** A small four-pointed glint in the book's plane. */
   private static void lstar(Matrix4f m, Vec3 c, float r, int pal, float a) {
      lline(m, c.add(-r, 0.0, 0.0), c.add(r, 0.0, 0.0), r * 0.18F, pal, a, a);
      lline(m, c.add(0.0, -r, 0.0), c.add(0.0, r, 0.0), r * 0.18F, pal, a, a);
   }

   /** Soft elliptical underglow behind the book. */
   private static void underglow(Matrix4f m, float HW, float HH, int pal, float a) {
      float[] glowC = RIM[pal];
      int seg = 24;
      for (int i = 0; i < seg; i++) {
         double a0 = Math.PI * 2.0 * i / seg, a1 = Math.PI * 2.0 * (i + 1) / seg;
         double wob0 = 1.0 + 0.12 * Math.sin(bookTime * 0.3 + i * 1.9), wob1 = 1.0 + 0.12 * Math.sin(bookTime * 0.3 + (i + 1) * 1.9);
         Vec3 q0 = new Vec3(Math.cos(a0) * HW * 1.75 * wob0, Math.sin(a0) * HH * 1.6 * wob0, -0.06);
         Vec3 q1 = new Vec3(Math.cos(a1) * HW * 1.75 * wob1, Math.sin(a1) * HH * 1.6 * wob1, -0.06);
         Vec3 c0 = new Vec3(0.0, 0.0, -0.06);
         gquad(m, Vec3.ZERO, c0, c0, q0, q1, glowC, a, a, 0.0F, 0.0F);
      }
   }

   /**
    * Each grimoire's own aura around the open book:
    *  0 Black  - licking black flame with a red core and a red underglow (like the anime)
    *  1 Dark   - violet-black flame, and three dark crescents circling the book
    *  2 Steel  - six steel shards orbiting in a tilted ring, glinting
    *  3 Four-leaf - green wind streaks spiralling around it, with leaves carried on the wind
    *  4 Three-leaf - a turning golden halo behind it and motes of light rising (hope)
    *  5 Mana Zone Codex - two cyan rune circles turning opposite ways behind it
    *  6 Spirit-bound - three pale spirit wisps orbiting with comet tails
    */
   private static void bookAura(VertexConsumer vc, Matrix4f m, Matrix3f n, FxRenderer.BookDraw b, float HW, float HH, float V, float al) {
      double t = bookTime;
      int pal = b.pal;
      switch (b.style) {
         case 0 -> {
            underglow(m, HW, HH, pal, 0.42F * al);
            for (int s = -1; s <= 1; s += 2) panelOutline(vc, m, n, s * (float)Math.cos(V), (float)Math.sin(V), -0.03F, HW, HH, al, s, pal);
         }
         case 1 -> {
            underglow(m, HW, HH, pal, 0.3F * al);
            for (int s = -1; s <= 1; s += 2) panelOutline(vc, m, n, s * (float)Math.cos(V), (float)Math.sin(V), -0.03F, HW, HH, al * 0.8F, s, pal);
            for (int k = 0; k < 3; k++) {
               double c0 = t * 0.09 + k * Math.PI * 2.0 / 3.0;
               Vec3 prev = null;
               for (int i = 0; i <= 8; i++) {
                  double a = c0 + i * 0.13;
                  Vec3 q = new Vec3(Math.cos(a) * HW * 1.45, Math.sin(a) * HH * 1.45, 0.04);
                  if (prev != null) {
                     float f = (float)Math.sin(Math.PI * i / 8.0);
                     lline(m, prev, q, 0.035F * f + 0.01F, pal, al * 0.8F * f, al * 0.8F * f);
                     quad(vc, m, n, Vec3.ZERO, prev, q, q.scale(1.08), prev.scale(1.08), SMOKE, 0.7F * al * f, 0.7F * al * f, 0.0F, 0.0F);
                  }
                  prev = q;
               }
            }
         }
         case 2 -> {
            underglow(m, HW, HH, pal, 0.18F * al);
            float[] steel = new float[]{0.62F, 0.68F, 0.76F}, dark = new float[]{0.22F, 0.25F, 0.3F};
            for (int k = 0; k < 6; k++) {
               double a = t * 0.05 + k * Math.PI / 3.0;
               Vec3 c = new Vec3(Math.cos(a) * HW * 1.5, Math.sin(a) * HH * 1.15, 0.12 * Math.sin(a + 0.6));
               Vec3 tan = new Vec3(-Math.sin(a), Math.cos(a), 0.0).scale(0.11);
               Vec3 nrm = new Vec3(Math.cos(a), Math.sin(a), 0.0).scale(0.04);
               Vec3 tip0 = c.add(tan), tip1 = c.subtract(tan);
               quad(vc, m, n, Vec3.ZERO, tip0, c.add(nrm), tip1, c.subtract(nrm), steel, al, al, al, al);
               quad(vc, m, n, Vec3.ZERO, tip0, c.add(nrm).add(0.0, 0.0, -0.01), tip1, c.subtract(nrm).add(0.0, 0.0, -0.01), dark, al, al, al, al);
               float g = 0.5F + 0.5F * (float)Math.sin(t * 0.4 + k * 1.3);
               lline(m, tip0, tip1, 0.012F, pal, al * 0.6F * g, al * 0.6F * g);
               if (g > 0.92F) lstar(m, tip0, 0.06F, pal, al * (g - 0.92F) * 10.0F);
            }
         }
         case 3 -> {
            underglow(m, HW, HH, pal, 0.2F * al);
            for (int k = 0; k < 3; k++) {
               Vec3 prev = null;
               for (int i = 0; i <= 14; i++) {
                  double u = i / 14.0;
                  double a = t * 0.16 + k * 2.1 + u * 4.0;
                  double r = 0.55 + 0.25 * u;
                  Vec3 q = new Vec3(Math.cos(a) * r, -0.35 + u * 0.8 + 0.05 * Math.sin(t * 0.2 + k), 0.15 * Math.sin(a));
                  if (prev != null) lline(m, prev, q, 0.018F, pal, al * (float)(u * 0.7), al * (float)(u * 0.7));
                  prev = q;
               }
            }
            float[] leaf = new float[]{0.3F, 0.85F, 0.4F};
            for (int k = 0; k < 4; k++) {
               double a = t * 0.11 + k * 1.57;
               Vec3 c = new Vec3(Math.cos(a) * 0.7, 0.35 * Math.sin(t * 0.07 + k * 2.0), 0.18 * Math.sin(a));
               double sp = t * 0.3 + k;
               Vec3 l1 = new Vec3(Math.cos(sp) * 0.05, Math.sin(sp) * 0.05, 0.0), l2 = new Vec3(-Math.sin(sp) * 0.025, Math.cos(sp) * 0.025, 0.0);
               quad(vc, m, n, Vec3.ZERO, c.add(l1), c.add(l2), c.subtract(l1), c.subtract(l2), leaf, al * 0.95F, al * 0.95F, al * 0.95F, al * 0.95F);
            }
         }
         case 4 -> {
            underglow(m, HW, HH, pal, 0.3F * al);
            Vec3 prev = null;
            for (int i = 0; i <= 32; i++) {
               double a = Math.PI * 2.0 * i / 32.0;
               Vec3 q = new Vec3(Math.cos(a) * 0.62, Math.sin(a) * 0.62, -0.09);
               if (prev != null) lline(m, prev, q, 0.02F, pal, al * 0.7F, al * 0.7F);
               prev = q;
            }
            for (int k = 0; k < 8; k++) {
               double a = t * 0.03 + k * Math.PI / 4.0;
               lline(m, new Vec3(Math.cos(a) * 0.66, Math.sin(a) * 0.66, -0.09), new Vec3(Math.cos(a) * 0.78, Math.sin(a) * 0.78, -0.09), 0.02F, pal, al * 0.8F, 0.0F);
            }
            for (int k = 0; k < 8; k++) {
               double ph = (t * 0.015 + k / 8.0) % 1.0;
               Vec3 c = new Vec3(Math.sin(k * 2.3) * 0.5, -0.4 + ph * 0.95, 0.1 + 0.05 * Math.cos(k * 1.7));
               lstar(m, c, 0.035F, pal, al * (float)Math.sin(Math.PI * ph));
            }
         }
         case 5 -> {
            underglow(m, HW, HH, pal, 0.22F * al);
            for (int ring = 0; ring < 2; ring++) {
               double r = ring == 0 ? 0.6 : 0.78, rot = (ring == 0 ? 1 : -1) * t * 0.025;
               Vec3 prev = null;
               for (int i = 0; i <= 40; i++) {
                  double a = Math.PI * 2.0 * i / 40.0 + rot;
                  Vec3 q = new Vec3(Math.cos(a) * r, Math.sin(a) * r, -0.09);
                  if (prev != null && (ring == 0 || i % 5 != 0)) lline(m, prev, q, 0.014F, pal, al * 0.75F, al * 0.75F);
                  prev = q;
               }
               for (int k = 0; k < 12; k++) {   // rune ticks
                  double a = Math.PI * 2.0 * k / 12.0 + rot;
                  double l = k % 3 == 0 ? 0.1 : 0.05;
                  lline(m, new Vec3(Math.cos(a) * r, Math.sin(a) * r, -0.09), new Vec3(Math.cos(a) * (r - l), Math.sin(a) * (r - l), -0.09), 0.012F, pal, al * 0.8F, al * 0.4F);
               }
            }
            for (int k = 0; k < 6; k++) {   // a turning hexagram inside
               double a0 = t * 0.02 + k * Math.PI / 3.0, a1 = a0 + Math.PI * 2.0 / 3.0;
               lline(m, new Vec3(Math.cos(a0) * 0.6, Math.sin(a0) * 0.6, -0.1), new Vec3(Math.cos(a1) * 0.6, Math.sin(a1) * 0.6, -0.1), 0.01F, pal, al * 0.45F, al * 0.45F);
            }
         }
         default -> {
            underglow(m, HW, HH, pal, 0.25F * al);
            for (int k = 0; k < 3; k++) {
               Vec3 prev = null;
               for (int i = 0; i <= 10; i++) {
                  double a = t * 0.12 + k * Math.PI * 2.0 / 3.0 - i * 0.12;
                  Vec3 q = new Vec3(Math.cos(a) * HW * 1.5, Math.sin(a) * HH * 1.4 + 0.05 * Math.sin(t * 0.3 + k), 0.15 * Math.sin(a * 2.0));
                  if (prev != null) lline(m, prev, q, 0.03F * (1.0F - i / 11.0F), pal, al * (1.0F - i / 10.0F), al * (1.0F - (i - 1) / 10.0F));
                  else lstar(m, q, 0.06F, pal, al);
                  prev = q;
               }
            }
         }
      }
   }

   /** A turning parchment page with a few lines of ink. */
   private static void page(VertexConsumer vc, Matrix4f m, Matrix3f n, float dx, float dz, float lift, float w, float h, float al) {
      float[] paper = new float[]{0.86F, 0.8F, 0.64F};
      float[] ink = new float[]{0.22F, 0.12F, 0.1F};
      Vec3 z = Vec3.ZERO;
      quad(vc, m, n, z, pt(dx, dz, lift, 0.0F, -h), pt(dx, dz, lift, w, -h), pt(dx, dz, lift, w, h), pt(dx, dz, lift, 0.0F, h), paper, 0.97F * al, 0.97F * al, 0.97F * al, 0.97F * al);
      for (int i = 1; i <= 6; i++) {
         float y = -h + 2.0F * h * (float)i / 7.0F;
         float len = w * (i % 3 == 0 ? 0.5F : 0.78F);
         float l2 = lift + 0.002F;
         quad(vc, m, n, z, pt(dx, dz, l2, w * 0.1F, y - 0.007F), pt(dx, dz, l2, w * 0.1F + len, y - 0.007F), pt(dx, dz, l2, w * 0.1F + len, y + 0.007F), pt(dx, dz, l2, w * 0.1F, y + 0.007F), ink, 0.7F * al, 0.7F * al, 0.7F * al, 0.7F * al);
      }
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
   private static void panelOutline(VertexConsumer vc, Matrix4f m, Matrix3f n, float dx, float dz, float lift, float w, float h, float al, int seed, int pal) {
      float[] core = RIM[pal];
      float[] smoke = new float[]{0.02F, 0.0F, 0.015F};
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
            float x0 = (float)(0.07 + 0.055 * Math.sin(t + k0 * 1.37) + 0.03 * Math.sin(t * 2.3 + k0 * 0.71)) * E[6];
            float x1 = (float)(0.07 + 0.055 * Math.sin(t + k1 * 1.37) + 0.03 * Math.sin(t * 2.3 + k1 * 0.71)) * E[6];
            x0 = Math.max(0.012F, x0);
            x1 = Math.max(0.012F, x1);
            Vec3 a = pt(dx, dz, lift, u0, y0), b = pt(dx, dz, lift, u1, y1);
            Vec3 ao = pt(dx, dz, lift, u0 + E[4] * x0, y0 + E[5] * x0), bo = pt(dx, dz, lift, u1 + E[4] * x1, y1 + E[5] * x1);
            Vec3 ao2 = pt(dx, dz, lift, u0 + E[4] * x0 * 1.9F, y0 + E[5] * x0 * 1.9F), bo2 = pt(dx, dz, lift, u1 + E[4] * x1 * 1.9F, y1 + E[5] * x1 * 1.9F);
            gquad(m, Vec3.ZERO, a, b, bo2, ao2, core, 0.75F * al, 0.75F * al, 0.0F, 0.0F);                    // glowing core of the flame
            quad(vc, m, n, Vec3.ZERO, ao, bo, bo2, ao2, smoke, 0.7F * al, 0.7F * al, 0.0F, 0.0F);            // black flame licking outward
            // a tall flickering tongue every few segments
            if ((k0 + seed) % 3 == 0) {
               float tl = (float)(0.12 + 0.1 * Math.abs(Math.sin(t * 0.8 + k0 * 2.1))) * E[6];
               float um = (u0 + u1) * 0.5F, ym = (y0 + y1) * 0.5F;
               Vec3 tip = pt(dx, dz, lift, um + E[4] * (x0 + tl), ym + E[5] * (x0 + tl) + tl * 0.4F);
               gquad(m, Vec3.ZERO, ao, bo, tip, tip, core, 0.55F * al, 0.55F * al, 0.0F, 0.0F);
               quad(vc, m, n, Vec3.ZERO, ao, bo, tip, tip, smoke, 0.75F * al, 0.75F * al, 0.0F, 0.0F);
            }
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

   private static final ResourceLocation PAGE_R = ResourceLocation.fromNamespaceAndPath("arcane_addon", "textures/item/spell_book_models/black_grimoire_page_right.png");
   private static final ResourceLocation PAGE_L = ResourceLocation.fromNamespaceAndPath("arcane_addon", "textures/item/spell_book_models/black_grimoire_page_left.png");
   private static final List<PageDraw> PAGES = new ArrayList<>();

   private static record PageDraw(Matrix4f m, Matrix3f n, float dx, float dz, float lift, float w, float h, int light) {
   }

   /** One side of a turning page: wound so only the side facing you is drawn. */
   private static void pageQuad(VertexConsumer vc, PageDraw p, boolean back) {
      Vec3[] q = {pt(p.dx, p.dz, p.lift, 0.0F, -p.h), pt(p.dx, p.dz, p.lift, p.w, -p.h), pt(p.dx, p.dz, p.lift, p.w, p.h), pt(p.dx, p.dz, p.lift, 0.0F, p.h)};
      float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
      for (int i = 0; i < 4; i++) {
         int k = back ? 3 - i : i;
         float u = back ? 1.0F - uv[k][0] : uv[k][0];
         vc.vertex(p.m, (float)q[k].x, (float)q[k].y, (float)q[k].z).color(1.0F, 1.0F, 1.0F, 1.0F).uv(u, uv[k][1])
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(p.light).normal(p.n, 0.0F, 0.0F, back ? -1.0F : 1.0F).endVertex();
      }
   }

   private static record BookDraw(Vec3 pos, float yaw, float tilt, float scale, float alpha, float flipCycle, float hw, float hh, float vDeg, int pal, int style, int light) {
   }

   private static class Fx {
      FxPacket p;
      double born;
      double killAge = -1.0;
   }
}
