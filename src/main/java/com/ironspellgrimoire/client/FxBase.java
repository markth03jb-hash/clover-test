package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.registry.AddonItemRegistry;
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

/**
 * Shared by every effect drawer: the palettes, the render state of the frame being drawn, and the drawing primitives (shaded quads,
 * glow lines, halos, beams, crescents, tubes) the effects are built from.
 * (Split out of FxRenderer.)
 */
abstract class FxBase {
   static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/effect/white.png");
   static final float[][] BODY = new float[][]{
      {0.06F, 0.0F, 0.04F},
      {0.03F, 0.0F, 0.08F},
      {0.22F, 0.05F, 0.0F},
      {0.02F, 0.1F, 0.16F},
      {0.12F, 0.11F, 0.0F},
      {0.04F, 0.13F, 0.09F},
      {0.07F, 0.07F, 0.09F},
      {0.09F, 0.10F, 0.12F},   // 7 steel
      {0.16F, 0.10F, 0.02F},   // 8 gold
      {0.07F, 0.0F, 0.0F},     // 9 devil red (devil form)
      {0.015F, 0.0F, 0.035F},  // 10 Yami's darkness
      {0.42F, 0.45F, 0.5F},    // 11 mercury (liquid chrome)
      {0.0F, 0.06F, 0.3F},     // 12 blue flame
      {0.02F, 0.07F, 0.22F},   // 13 blue lightning (Luck)
      {0.28F, 0.0F, 0.03F},    // 14 crimson flame (Mereoleona)
      {0.55F, 0.5F, 0.42F},    // 15 bone (Zenon)
      {0.30F, 0.10F, 0.18F},   // 16 thread (Vanessa)
      {0.05F, 0.02F, 0.14F},   // 17 space (Finral)
      {0.2F, 0.0F, 0.01F},     // 18 pure crimson (Demon Slayer's Sky Splitter: red only)
      {0.02F, 0.1F, 0.2F},     // 19 water
      {0.45F, 0.47F, 0.55F},   // 20 mirror
      {0.1F, 0.0F, 0.12F},     // 21 void
      {0.25F, 0.04F, 0.0F},    // 22 flame spirit
      {0.22F, 0.12F, 0.0F},    // 23 lion's flame
      {0.03F, 0.12F, 0.04F},   // 24 world tree
      {0.18F, 0.02F, 0.04F},   // 25 ripper
      {0.2F, 0.13F, 0.06F},    // 26 earth
      {0.02F, 0.14F, 0.1F},    // 27 beast
      {0.55F, 0.5F, 0.55F},    // 28 cotton
      {0.02F, 0.13F, 0.22F},   // 29 Julius's time: deep clockwork blue
      {0.26F, 0.15F, 0.07F}    // 30 bark (the World Tree's wood)
   };
   static final float[][] RIM = new float[][]{
      {1.0F, 0.1F, 0.06F}, {0.67F, 0.24F, 1.0F}, {1.0F, 0.6F, 0.12F}, {0.6F, 0.88F, 1.0F}, {1.0F, 0.96F, 0.44F}, {0.75F, 1.0F, 0.84F}, {0.95F, 0.95F, 1.0F},
      {0.78F, 0.85F, 0.94F},   // 7 steel
      {1.0F, 0.84F, 0.36F},    // 8 gold
      {1.0F, 0.07F, 0.05F},    // 9 devil red (devil form)
      {0.6F, 0.2F, 1.0F},      // 10 Yami's darkness (smoke comes off everything drawn in it)
      {0.86F, 0.92F, 1.0F},    // 11 mercury
      {0.35F, 0.65F, 1.0F},    // 12 blue flame (Calidos Brachium Purgatory)
      {0.45F, 0.72F, 1.0F},    // 13 blue lightning (Luck Voltia)
      {1.0F, 0.16F, 0.1F},     // 14 crimson flame (Mereoleona Vermillion)
      {0.95F, 0.92F, 0.84F},   // 15 bone (Zenon Zogratis)
      {1.0F, 0.55F, 0.78F},    // 16 thread (Vanessa Enoteca): pink-white thread
      {0.62F, 0.45F, 1.0F},    // 17 space (Finral Roulacase): violet, gold-rimmed gates
      {1.0F, 0.08F, 0.05F},    // 18 pure crimson
      {0.3F, 0.7F, 1.0F},      // 19 water: a bright clear blue
      {0.85F, 0.88F, 1.0F},    // 20 mirror: silvery, with a rose glint
      {0.9F, 0.25F, 0.85F},    // 21 void: magenta-violet cuts
      {1.0F, 0.38F, 0.1F},     // 22 flame spirit rim: vermilion
      {1.0F, 0.72F, 0.18F},    // 23 lion's flame: gold-orange
      {0.45F, 0.95F, 0.38F},   // 24 world tree: living green
      {1.0F, 0.3F, 0.36F},     // 25 ripper: blood-red edge
      {0.86F, 0.64F, 0.36F},   // 26 earth: ochre
      {0.3F, 0.88F, 0.7F},     // 27 beast: sea-green
      {1.0F, 0.78F, 0.9F},     // 28 cotton: candy pink
      {0.3F, 0.86F, 1.0F},     // 29 Julius: cyan
      {0.66F, 0.44F, 0.22F}    // 30 bark: warm brown
   };
   /** Overall strength of every world effect. Lower = subtler. */
   static final float FX_ALPHA = 1.0F;
   /** Effects closer than this to the camera fade out so they never cover the screen (first person). */
   static final double NEAR_FADE_START = 0.8;
   static final double NEAR_FADE_RANGE = 2.4;
   static final float NEAR_FADE_MIN = 0.15F;
   static boolean nearFade = false;
   static double bookTime = 0.0;
   static final int EMBER = 9;
   /** Separate buffer for the additive glow layer (cores, rims, metal highlights). */
   static final MultiBufferSource.BufferSource GLOW_SRC = MultiBufferSource.immediate(new BufferBuilder(262144));
   static VertexConsumer gv;
   /** Key light for the shaded 3D solids. */
   static final Vec3 LIGHT = new Vec3(0.35, 1.0, 0.25).normalize();

   static final int DARKNESS = 10;

   static final int WIND = 5, MERCURY = 11, FIRE = 2, LIGHTNING = 4, BLUE_FIRE = 12, BLUE_LIGHTNING = 13, CRIMSON = 14;
   static int flamePal = 2, boltPal = 4;

   // ------------------------------------------------------------------ bones (Zenon)
   static final float[] IVORY = {0.98F, 0.97F, 0.93F}, MARROW = {0.62F, 0.56F, 0.48F};


   /** A shaded ivory tube along the centre points with the given radii (capped at the ends when caps is true). */
   static void tube(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3[] ctr, double[] rad, Vec3 ref, int sides, boolean caps) {
      int S = ctr.length - 1;
      Vec3[][] ring = new Vec3[S + 1][sides];
      Vec3[][] nor = new Vec3[S + 1][sides];
      for (int i = 0; i <= S; i++) {
         Vec3 tg = (i < S ? ctr[i + 1].subtract(ctr[i]) : ctr[i].subtract(ctr[i - 1]));
         if (tg.lengthSqr() < 1.0E-10) tg = new Vec3(0, 1, 0);
         tg = tg.normalize();
         Vec3 a = ref.subtract(tg.scale(ref.dot(tg)));
         if (a.lengthSqr() < 1.0E-6) a = tg.cross(new Vec3(0, 1, 0));
         if (a.lengthSqr() < 1.0E-6) a = tg.cross(new Vec3(1, 0, 0));
         a = a.normalize();
         Vec3 b = tg.cross(a).normalize();
         for (int j = 0; j < sides; j++) {
            double th = Math.PI * 2 * j / sides;
            Vec3 dir = a.scale(Math.cos(th)).add(b.scale(Math.sin(th)));
            ring[i][j] = ctr[i].add(dir.scale(rad[i]));
            nor[i][j] = dir;
         }
      }
      for (int i = 0; i < S; i++) {
         for (int j = 0; j < sides; j++) {
            int j1 = (j + 1) % sides;
            Vec3 fc = ring[i][j].add(ring[i + 1][j1]).scale(0.5);
            Vec3 fn = nor[i][j].add(nor[i + 1][j1]).normalize();
            if (fn.dot(cam.subtract(fc)) < 0.0) continue;   // back face
            boneV(vc, m, n, cam, ring[i][j], nor[i][j], (double)i / S);
            boneV(vc, m, n, cam, ring[i][j1], nor[i][j1], (double)i / S);
            boneV(vc, m, n, cam, ring[i + 1][j1], nor[i + 1][j1], (double)(i + 1) / S);
            boneV(vc, m, n, cam, ring[i + 1][j], nor[i + 1][j], (double)(i + 1) / S);
         }
      }
      if (caps) {
         for (int e = 0; e <= 1; e++) {
            int i = e == 0 ? 0 : S;
            Vec3 cn = (e == 0 ? ctr[0].subtract(ctr[1]) : ctr[S].subtract(ctr[S - 1])).normalize();
            for (int j = 0; j < sides; j++) {
               int j1 = (j + 1) % sides;
               boneV(vc, m, n, cam, ctr[i], cn, 0.5);
               boneV(vc, m, n, cam, ring[i][j], cn, 0.5);
               boneV(vc, m, n, cam, ring[i][j1], cn, 0.5);
               boneV(vc, m, n, cam, ctr[i], cn, 0.5);
            }
         }
      }
   }

   /** A knob of bone (a condyle): a small shaded lump. */
   static void knob(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, double R) {
      int LAT = 5, LON = 8;
      for (int i = 0; i < LAT; i++) {
         for (int j = 0; j < LON; j++) {
            Vec3[] q = new Vec3[4];
            int[][] ij = {{i, j}, {i, j + 1}, {i + 1, j + 1}, {i + 1, j}};
            for (int k = 0; k < 4; k++) {
               double th = Math.PI * ij[k][0] / LAT, ph = Math.PI * 2 * ij[k][1] / LON;
               q[k] = new Vec3(Math.sin(th) * Math.cos(ph), Math.cos(th), Math.sin(th) * Math.sin(ph));
            }
            Vec3 qc = q[0].add(q[2]).scale(0.5);
            if (qc.dot(cam.subtract(c.add(qc.scale(R)))) < 0.0) continue;
            for (Vec3 nv : q) boneV(vc, m, n, cam, c.add(nv.scale(R)), nv, 0.5);
         }
      }
   }

   /** One vertex of bone: ivory lit by the key light, with soft ambient, a hint of sheen, darker toward the very ends. */
   static void boneV(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 w, Vec3 nrm, double t) {
      float lam = (float)Math.max(0.0, nrm.dot(LIGHT));
      Vec3 view = cam.subtract(w).normalize();
      float rimL = (float)Math.pow(1.0 - Math.max(0.0, nrm.dot(view)), 3.0);
      float sheen = (float)Math.pow(Math.max(0.0, nrm.dot(LIGHT.add(view).normalize())), 18.0) * 0.18F;
      float ao = (float)(0.85 + 0.15 * Math.sin(Math.PI * Mth.clamp(t, 0.0, 1.0)));
      float k = (0.38F + 0.62F * lam) * ao;
      float[] col = {
         Mth.clamp(MARROW[0] + (IVORY[0] - MARROW[0]) * k + sheen - rimL * 0.12F, 0, 1),
         Mth.clamp(MARROW[1] + (IVORY[1] - MARROW[1]) * k + sheen - rimL * 0.13F, 0, 1),
         Mth.clamp(MARROW[2] + (IVORY[2] - MARROW[2]) * k + sheen - rimL * 0.14F, 0, 1)};
      vtx(vc, m, n, cam, w, col, 1.0F);
   }

   /** A soft round glow facing the camera: bright in the middle, fading to nothing at radius R (additive). */
   static void halo(Matrix4f m, Vec3 cam, Vec3 c, double R, float[] col, float a) {
      if (gv == null || a <= 0.01F) return;
      Vec3 view = c.subtract(cam);
      if (view.lengthSqr() < 1.0E-6) return;
      view = view.normalize();
      Vec3 u = view.cross(new Vec3(0, 1, 0));
      if (u.lengthSqr() < 1.0E-4) u = new Vec3(1, 0, 0);
      u = u.normalize();
      Vec3 v = view.cross(u).normalize();
      int N = 18;
      for (int i = 0; i < N; i++) {
         double a0 = Math.PI * 2 * i / N, a1 = Math.PI * 2 * (i + 1) / N;
         Vec3 e0 = c.add(u.scale(Math.cos(a0) * R)).add(v.scale(Math.sin(a0) * R));
         Vec3 e1 = c.add(u.scale(Math.cos(a1) * R)).add(v.scale(Math.sin(a1) * R));
         Vec3 m0 = c.add(e0.subtract(c).scale(0.35)), m1 = c.add(e1.subtract(c).scale(0.35));
         gquad(m, cam, c, m0, m1, c, col, a, a * 0.55F, a * 0.55F, a);
         gquad(m, cam, m0, e0, e1, m1, col, a * 0.55F, 0.0F, 0.0F, a * 0.55F);
      }
   }

   static Vec3 jit(java.util.concurrent.ThreadLocalRandom r, double s) {
      return new Vec3(r.nextDouble(-s, s), r.nextDouble(-s, s), r.nextDouble(-s, s));
   }

   /**
    * Energy palettes (fire, lightning, wind, blue flame) are made of light, not matter: while one of their slashes,
    * crescents, walls or beams is drawn, its "body" goes into the additive glow layer, tinted toward the palette's
    * colour - so wind is a bright, airy shimmer and fire a luminous blaze instead of dark see-through ribbons.
    */
   static int lightPal = -1;
   /** True while a slash is drawn in the Spirit of Zephyr style. */
   static boolean slashGlow = false;
   /** effect_opacity from the client config, read once per frame. */
   static float OPACITY = 1F;
   /** Alpha multiplier for the effect being drawn right now (OPACITY, faded for an overdue per-tick copy). */
   static float ALPHA_NOW = 1F;

   static boolean isLight(int pal) { return pal == 2 || pal == 4 || pal == 5 || pal == 6 || pal == 12 || pal == 13 || pal == 14 || pal == 22 || pal == 23; }

   static void vtx(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 w, float[] col, float a) {
      if (lightPal >= 0 && gv != null) {
         float[] rc = RIM[lightPal];
         boolean zephyr = lightPal == 5 || slashGlow;   // the Spirit of Zephyr look: a soft, bright, see-through blade
         float k = zephyr ? 0.85F : 0.75F;
         float[] lc = {col[0] + (rc[0] - col[0]) * k, col[1] + (rc[1] - col[1]) * k, col[2] + (rc[2] - col[2]) * k};
         gvtx(m, cam, w, lc, a * (zephyr ? 0.28F : (lightPal == 22 || lightPal == 23 ? 0.9F : 0.6F)));   // gvtx applies the opacity
         return;
      }
      a *= ALPHA_NOW;
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

   static void quad(
      VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float[] col, float aa, float ab, float ac, float ad
   ) {
      vtx(vc, m, n, cam, a, col, aa);
      vtx(vc, m, n, cam, b, col, ab);
      vtx(vc, m, n, cam, c, col, ac);
      vtx(vc, m, n, cam, d, col, ad);
   }

   // ------------------------------------------------------------------ additive glow layer
   static void gvtx(Matrix4f m, Vec3 cam, Vec3 w, float[] col, float a) {
      if (gv == null) return;
      a *= ALPHA_NOW;
      if (nearFade) {
         double d = w.distanceTo(cam);
         a *= Mth.clamp((float)((d - NEAR_FADE_START) / NEAR_FADE_RANGE), NEAR_FADE_MIN, 1.0F) * FX_ALPHA;
      }
      gv.vertex(m, (float)(w.x - cam.x), (float)(w.y - cam.y), (float)(w.z - cam.z)).color(col[0], col[1], col[2], Mth.clamp(a, 0.0F, 1.0F)).endVertex();
   }

   static void gquad(Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float[] col, float aa, float ab, float ac, float ad) {
      gvtx(m, cam, a, col, aa);
      gvtx(m, cam, b, col, ab);
      gvtx(m, cam, c, col, ac);
      gvtx(m, cam, d, col, ad);
   }

   /** A camera-facing glowing line: white-hot core with a soft coloured falloff. */
   static void gline(Matrix4f m, Vec3 cam, Vec3 p0, Vec3 p1, float w, int pal, float a) {
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

   static float[] hot(int pal) {
      float[] r = RIM[pal];
      float w = isFlame(pal) ? 0.3F : (pal == 13 ? 0.32F : 0.55F);   // black flame keeps a red core; Luck's lightning stays blue
      return new float[]{r[0] + (1.0F - r[0]) * w, r[1] + (1.0F - r[1]) * w, r[2] + (1.0F - r[2]) * w};
   }

   // ------------------------------------------------------------------ black flame (Asta's anti magic look)
   static double fxTime;
   static final float[] SMOKE = new float[]{0.015F, 0.0F, 0.008F};

   /** Asta's palettes (slayer crimson, dweller dark, devil ember) burn with black flame and a red underglow. */
   static boolean isFlame(int pal) {
      return pal == 0 || pal == 1 || pal == 9;
   }

   /**
    * One licking tongue of black flame on the edge a-b, leaning along {@code out} and rising: a black smoke tongue in
    * front of a larger red glow tongue, flickering over time.
    */
   static void flameTongue(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, Vec3 out, double height, int pal, float al, int seed) {
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

   // ------------------------------------------------------------------ 3D solids (steel plates, bars, spikes...)
   static double backOut(double x) {
      x = Mth.clamp(x, 0.0, 1.0) - 1.0;
      return 1.0 + 2.2 * x * x * x + 1.2 * x * x;
   }

   // ------------------------------------------------------------------ cracks in space (Event Horizon)
   static final float[] VOID = new float[]{0.01F, 0.0F, 0.025F};

   static double smooth(double x) {
      x = Mth.clamp(x, 0.0, 1.0);
      return x * x * (3.0 - 2.0 * x);
   }

   /** How long a packet has moved: per-tick copies (life 1) stop at the end of their own tick and wait for the next. */
   static double mv(FxPacket p, double age) { return p.life <= 1 ? Math.min(age, PER_TICK_GLIDE) : age; }
   /** How far (in ticks) a per-tick copy keeps moving along its velocity while waiting for the next one. */
   static final double PER_TICK_GLIDE = 2.0;

   static Vec3 travelled(FxRenderer.Fx f, double age) {
      double mt = f.p.a1 > 0.0F ? Math.min(age, (double)f.p.a1) : age;
      if (f.p.life <= 1) mt = Math.min(mt, PER_TICK_GLIDE);
      if (f.killAge >= 0.0) mt = Math.min(mt, f.killAge);
      return f.p.c.add(f.p.v.scale(mt));
   }

   static float[] solidLo(int pal) {
      float[] b = BODY[pal];
      return new float[]{b[0] * 1.6F + 0.06F, b[1] * 1.6F + 0.06F, b[2] * 1.6F + 0.07F};
   }

   /** Lit colour of a face: lambert from the key light plus a fresnel rim toward the camera. */
   static float[] shade(int pal, Vec3 nrm, Vec3 view, float mul) {
      if (pal == 11) {   // mercury: a mirror - the sky reflected on top, dark below, a hard highlight
         Vec3 refl = view.subtract(nrm.scale(2.0 * view.dot(nrm)));
         float sky = (float)Math.pow(Mth.clamp(refl.y * 0.5 + 0.5, 0.0, 1.0), 1.4);
         float spec = (float)Math.pow(Math.max(0.0, refl.dot(LIGHT)), 24.0);
         float v = (0.12F + 0.78F * sky + 0.6F * spec) * (0.85F + 0.15F * mul);
         return new float[]{Math.min(1F, v * 0.95F), Math.min(1F, v * 0.97F), Math.min(1F, v * 1.03F)};
      }
      float lam = (float)Math.max(0.0, nrm.dot(LIGHT));
      float fres = 1.0F - (float)Math.abs(nrm.dot(view));
      float k = Mth.clamp(0.16F + 0.62F * lam + 0.3F * fres * fres, 0.0F, 1.0F) * mul;
      float[] lo = solidLo(pal), hi = RIM[pal];
      return new float[]{lo[0] + (hi[0] - lo[0]) * k, lo[1] + (hi[1] - lo[1]) * k, lo[2] + (hi[2] - lo[2]) * k};
   }

   /** Palettes whose solid 3D pieces (blocks, spikes) are drawn with a pixel texture instead of one flat colour. */
   static boolean texPal(int pal) { return pal >= 22 && pal <= 30; }

   static float h2(int a, int b, int c) {
      int h = a * 374761393 + b * 668265263 + c * 1274126177;
      h = (h ^ (h >>> 13)) * 1274126177;
      h ^= h >>> 16;
      return (h & 0xFFFF) / 65535F;
   }

   /** The pixel-art pattern of each material: what one cell (i, j) of a face is multiplied by. */
   static float[] texMul(int pal, int i, int j, int nu, int nv, int salt) {
      float h = h2(i, j, salt + pal * 17);
      boolean edge = i == 0 || j == 0 || i == nu - 1 || j == nv - 1;
      float m;
      switch (pal) {
         case 26 -> {   // stone: chunky blocks, darker at the joints, the odd crack
            m = 0.82F + 0.3F * h;
            if (edge) m *= 0.74F;
            if (h > 0.92F) m *= 0.5F;
            return new float[]{m, m * 0.97F, m * 0.92F};
         }
         case 30 -> {   // bark: long vertical grain, rings, dark knots
            float st = h2(i, 0, salt + 3);
            m = 0.7F + 0.34F * st + 0.08F * h;
            if (j % 4 == 3) m *= 0.86F;
            if (h > 0.95F) m *= 0.45F;
            return new float[]{m * 1.05F, m * 0.97F, m * 0.88F};
         }
         case 24 -> {   // leaves: a patchwork of greens with lit yellow-green cells
            m = 0.7F + 0.45F * h;
            if (h > 0.84F) return new float[]{m * 1.25F, m * 1.3F, m * 0.7F};
            if (h < 0.15F) return new float[]{m * 0.7F, m * 0.9F, m * 0.8F};
            return new float[]{m, m * 1.05F, m * 0.9F};
         }
         case 22 -> {   // volcanic crust: dark plates with glowing seams
            if (h > 0.8F) return new float[]{2.4F, 1.5F, 0.7F};
            m = 0.55F + 0.35F * h;
            return new float[]{m, m * 0.9F, m * 0.85F};
         }
         case 23 -> {   // beaten gold plates in a chequer, riveted edges
            m = ((i + j) & 1) == 0 ? 1.12F : 0.86F;
            if (edge) m *= 0.8F;
            if (i == 1 && j == 1) m = 1.4F;
            return new float[]{m, m * 0.96F, m * 0.8F};
         }
         case 25 -> {   // honed steel: hairline streaks along the blade
            m = 0.9F + 0.18F * h2(0, j, salt);
            return new float[]{m, m, m * 1.03F};
         }
         case 27 -> {   // bone and fang: ridges across, a pale tip
            m = j % 2 == 0 ? 1.1F : 0.82F;
            m *= 0.93F + 0.12F * h;
            return new float[]{m, m * 1.02F, m * 0.95F};
         }
         case 28 -> {   // quilted cotton: soft puffed cells, pinched at the stitches
            m = edge ? 0.86F : 1.0F + 0.1F * h;
            return new float[]{m, m * 0.98F, m * 1.0F};
         }
         case 29 -> {   // clockwork plate: riveted panels, a bright rivet in each corner
            m = ((i / 2 + j / 2) & 1) == 0 ? 1.08F : 0.9F;
            if (edge) m *= 0.76F;
            if ((i == 1 || i == nu - 2) && (j == 1 || j == nv - 2)) m = 1.5F;
            return new float[]{m * 0.95F, m, m * 1.05F};
         }
         default -> { return new float[]{1F, 1F, 1F}; }
      }
   }

   /** One flat-shaded face (quad, or triangle with d == c). Returns false when it faces away and was skipped. */
   static boolean face(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 nrm, int pal, float al, Vec3 up, Vec3 ctr) {
      return face(vc, m, n, cam, a, b, c, d, nrm, pal, al, up, ctr, 1.0F);
   }

   static boolean face(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 nrm, int pal, float al, Vec3 up, Vec3 ctr, float tone) {
      Vec3 mid = a.add(b).add(c).add(d).scale(0.25);
      Vec3 view = mid.subtract(cam);
      double vl = view.length();
      if (vl < 1.0E-6 || nrm.dot(view) > 0.0) return false;
      view = view.scale(1.0 / vl);
      float[] ca = shade(pal, nrm, view, a.subtract(ctr).dot(up) > 0.0 ? 1.0F : 0.78F);
      float[] cb = shade(pal, nrm, view, b.subtract(ctr).dot(up) > 0.0 ? 1.0F : 0.78F);
      float[] cc = shade(pal, nrm, view, c.subtract(ctr).dot(up) > 0.0 ? 1.0F : 0.78F);
      float[] cd = shade(pal, nrm, view, d.subtract(ctr).dot(up) > 0.0 ? 1.0F : 0.78F);
      if (tone != 1.0F) for (float[] q : new float[][]{ca, cb, cc, cd}) { q[0] *= tone; q[1] *= tone; q[2] *= tone; }
      boolean quad = c.subtract(d).lengthSqr() > 1.0E-10;
      if (texPal(pal) && quad) {
         Vec3 ab = b.subtract(a), ad = d.subtract(a), extra = a.subtract(b).add(c).subtract(d);
         int nu = Mth.clamp((int) Math.round(ab.length() / 0.16), 1, 10), nv = Mth.clamp((int) Math.round(ad.length() / 0.16), 1, 10);
         int salt = (int) Math.round(nrm.x * 3) * 31 + (int) Math.round(nrm.y * 3) * 17 + (int) Math.round(nrm.z * 3) * 7;
         for (int i = 0; i < nu; i++) {
            for (int j = 0; j < nv; j++) {
               float u0 = i / (float) nu, u1 = (i + 1) / (float) nu, v0 = j / (float) nv, v1 = (j + 1) / (float) nv;
               float uc = (u0 + u1) * 0.5F, vc2 = (v0 + v1) * 0.5F;
               float[] tm = texMul(pal, i, j, nu, nv, salt);
               float w0 = (1 - uc) * (1 - vc2), w1 = uc * (1 - vc2), w2 = uc * vc2, w3 = (1 - uc) * vc2;
               float[] col = {Math.min(1F, (ca[0] * w0 + cb[0] * w1 + cc[0] * w2 + cd[0] * w3) * tm[0]),
                              Math.min(1F, (ca[1] * w0 + cb[1] * w1 + cc[1] * w2 + cd[1] * w3) * tm[1]),
                              Math.min(1F, (ca[2] * w0 + cb[2] * w1 + cc[2] * w2 + cd[2] * w3) * tm[2])};
               vtx(vc, m, n, cam, a.add(ab.scale(u0)).add(ad.scale(v0)).add(extra.scale(u0 * v0)), col, al);
               vtx(vc, m, n, cam, a.add(ab.scale(u1)).add(ad.scale(v0)).add(extra.scale(u1 * v0)), col, al);
               vtx(vc, m, n, cam, a.add(ab.scale(u1)).add(ad.scale(v1)).add(extra.scale(u1 * v1)), col, al);
               vtx(vc, m, n, cam, a.add(ab.scale(u0)).add(ad.scale(v1)).add(extra.scale(u0 * v1)), col, al);
            }
         }
         return true;
      }
      vtx(vc, m, n, cam, a, ca, al);
      vtx(vc, m, n, cam, b, cb, al);
      vtx(vc, m, n, cam, c, cc, al);
      vtx(vc, m, n, cam, d, cd, al);
      return true;
   }

   /**
    * How far an animation is turning the whole body right now (PlayerAnimator's body bone, e.g. Black Hurricane's
    * spin), in degrees of body yaw - so forms drawn round the player (devil wings, bone wings, the spirit) turn with it.
    */
   static double animYawDeg(net.minecraft.world.entity.player.Player p, float pt) {
      if (!(p instanceof net.minecraft.client.player.AbstractClientPlayer acp)) return 0.0;
      try {
         var stack = dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess.getPlayerAnimLayer(acp);
         if (stack == null || !stack.isActive()) return 0.0;
         var v = stack.get3DTransform("body", dev.kosmx.playerAnim.api.TransformType.ROTATION, pt, dev.kosmx.playerAnim.core.util.Vec3f.ZERO);
         return Math.toDegrees(v.getY().doubleValue());
      } catch (RuntimeException ex) {
         return 0.0;
      }
   }

   static boolean tri(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 base, Vec3 d, int pal, float al, Vec3 ctr) {
      Vec3 nrm = b.subtract(a).cross(c.subtract(a));
      if (nrm.lengthSqr() < 1.0E-10) return false;
      nrm = nrm.normalize();
      Vec3 mid = a.add(b).add(c).scale(1.0 / 3.0);
      Vec3 axisPt = base.add(d.scale(mid.subtract(base).dot(d)));
      if (nrm.dot(mid.subtract(axisPt)) < 0.0) nrm = nrm.scale(-1.0);
      float tone = 1.0F;
      if (texPal(pal)) {   // spikes: bands along the length (growth rings, bone segments, forged plates) and a per-facet tint
         int band = (int) Math.floor(mid.subtract(base).dot(d) / 0.26);
         tone = ((band & 1) == 0 ? 1.0F : 0.84F) * (0.9F + 0.2F * h2(band, (int) Math.round(nrm.x * 4), (int) Math.round(nrm.z * 4) + pal));
      }
      return face(vc, m, n, cam, a, b, c, c, nrm, pal, al, d, ctr, tone);
   }

   static void crescent(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 c, Vec3 d, Vec3 u, double R, int pal, float env) {
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

   static void beam(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Vec3 p0, Vec3 p1, float width, int pal, float env, double endTaper) {
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
}
