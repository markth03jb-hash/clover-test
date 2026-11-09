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

/**
 * Forms drawn around a player each frame: Thunder God Mode, the Black Cocoon, the Devil Union crown and mantle, Spirit Dive and
 * the auras on held weapons.
 * (Split out of FxRenderer.)
 */
final class FxForms extends FxBase {
   private FxForms() {}


   /** Thunder God Mode: lightning crackling over the whole body. Gloves: crackling around the fists. */
   static void thunderBody(Matrix4f m, Vec3 cam, Player p, float pt, double now, boolean mode, boolean gloves) {
      if (gv == null) return;
      Vec3 base = new Vec3(Mth.lerp((double)pt, p.xo, p.getX()), Mth.lerp((double)pt, p.yo, p.getY()), Mth.lerp((double)pt, p.zo, p.getZ()));
      float h = p.getBbHeight();
      long frame = (long)(now * 1.5);
      java.util.Random rnd = new java.util.Random(p.getId() * 31L + frame * 977L);
      if (mode) {
         // the eyes burn with lightning, and lightning stands up off the head like hair
         double hy = Math.toRadians(Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot));
         Vec3 face = new Vec3(-Math.sin(hy), 0, Math.cos(hy)), side = new Vec3(Math.cos(hy), 0, Math.sin(hy));
         Vec3 eyes = base.add(0, p.getEyeHeight(), 0).add(face.scale(0.27));
         if (p != Minecraft.getInstance().player || !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            for (int s = -1; s <= 1; s += 2) halo(m, cam, eyes.add(side.scale(0.11 * s)), 0.16, RIM[BLUE_LIGHTNING], 0.9F);
            for (int k = 0; k < 3; k++) {
               Vec3 a = base.add((rnd.nextDouble() - 0.5) * 0.4, h * 0.95, (rnd.nextDouble() - 0.5) * 0.4);
               FxShapes.boltBranch(m, cam, a, a.add((rnd.nextDouble() - 0.5) * 0.4, 0.4 + rnd.nextDouble() * 0.4, (rnd.nextDouble() - 0.5) * 0.4), 0.025F, BLUE_LIGHTNING, 0.85F, rnd, 0, 2);
            }
         }
         // a faint field of charge around the whole body
         halo(m, cam, base.add(0, h * 0.5, 0), 1.6, RIM[BLUE_LIGHTNING], 0.07F + 0.05F * (float)Math.abs(Math.sin(now * 2.3)));
         for (int k = 0; k < 5; k++) {
            Vec3 a = base.add((rnd.nextDouble() - 0.5) * 0.7, rnd.nextDouble() * h, (rnd.nextDouble() - 0.5) * 0.7);
            Vec3 b = a.add((rnd.nextDouble() - 0.5) * 0.9, (rnd.nextDouble() - 0.3) * 0.9, (rnd.nextDouble() - 0.5) * 0.9);
            FxShapes.boltBranch(m, cam, a, b, 0.03F, BLUE_LIGHTNING, 0.8F, rnd, 1, 1);
         }
         if (rnd.nextInt(5) == 0) {   // now and then a long arc to the ground
            Vec3 a = base.add(0, h * 0.5, 0);
            Vec3 b = base.add((rnd.nextDouble() - 0.5) * 3.0, 0.05, (rnd.nextDouble() - 0.5) * 3.0);
            FxShapes.boltBranch(m, cam, a, b, 0.04F, BLUE_LIGHTNING, 0.9F, rnd, 1, 1);
         }
      }
      if (gloves || mode) {
         double by = Math.toRadians((double)Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) - animYawDeg(p, pt));
         Vec3 fw = new Vec3(-Math.sin(by), 0.0, Math.cos(by)), lf = new Vec3(Math.cos(by), 0.0, Math.sin(by));
         for (int s = -1; s <= 1; s += 2) {
            Vec3 hand = base.add(0, h * 0.4, 0).add(lf.scale(0.38 * s)).add(fw.scale(0.08));
            for (int k = 0; k < 2; k++) {
               Vec3 b = hand.add((rnd.nextDouble() - 0.5) * 0.5, (rnd.nextDouble() - 0.5) * 0.5, (rnd.nextDouble() - 0.5) * 0.5);
               FxShapes.boltBranch(m, cam, hand, b, 0.025F, BLUE_LIGHTNING, 0.85F, rnd, 0, 2);
            }
         }
      }
   }

   /**
    * Black Cocoon: a solid, shaded sphere of darkness around the entity. Near-black body, a violet fresnel rim that
    * glows at the silhouette, and three slow bands of light winding over it. It closes in over the first ticks and,
    * when it bursts, swells and fades in a few ticks. Seen from inside (first person) it is only a faint tint.
    */
   static void cocoon(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, net.minecraft.world.entity.Entity e, float pt, double now, double[] f) {
      double age = now - f[0];
      if (age < 0.0) return;
      float R = (float)f[2];
      double grow = Mth.clamp(age / f[3], 0.0, 1.0);
      grow = 1.0 - Math.pow(1.0 - grow, 3.0);
      float a = 1.0F;
      double rr = R * grow;
      if (now > f[1]) {   // bursting
         double b = (now - f[1]) / 6.0;
         rr = R * (1.0 + b * 1.6);
         a = (float)Math.max(0.0, 1.0 - b);
      }
      if (a <= 0.01F || rr < 0.05) return;
      rr *= 1.0 + 0.015 * Math.sin(now * 0.6);   // breathing
      Vec3 c = e.getPosition(pt).add(0.0, e.getBbHeight() * 0.5, 0.0);
      boolean inside = cam.distanceTo(c) < rr * 1.02;
      float bodyA = (inside ? 0.16F : 0.94F) * a, glowA = (inside ? 0.22F : 0.85F) * a;
      int LAT = 14, LON = 24;
      Vec3[][] pts = new Vec3[LAT + 1][LON + 1];
      for (int i = 0; i <= LAT; i++) {
         double th = Math.PI * i / LAT;
         for (int j = 0; j <= LON; j++) {
            double ph = Math.PI * 2 * j / LON;
            pts[i][j] = new Vec3(Math.sin(th) * Math.cos(ph), Math.cos(th), Math.sin(th) * Math.sin(ph));
         }
      }
      float[] body = BODY[1], rim = RIM[1];
      for (int i = 0; i < LAT; i++) {
         for (int j = 0; j < LON; j++) {
            Vec3[] q = {pts[i][j], pts[i][j + 1], pts[i + 1][j + 1], pts[i + 1][j]};
            Vec3 qc = q[0].add(q[2]).scale(0.5);
            Vec3 toCam = cam.subtract(c.add(qc.scale(rr)));
            if (!inside && qc.dot(toCam) < 0.0) continue;   // only the side facing you (keeps the translucent shell clean)
            for (Vec3 nv : q) {
               Vec3 w = c.add(nv.scale(rr));
               Vec3 v = cam.subtract(w).normalize();
               float fres = (float)Math.pow(1.0 - Math.abs(nv.dot(v)), 3.0);
               float lit = (float)Math.max(0.0, nv.dot(LIGHT)) * 0.08F;
               float[] col = {body[0] + rim[0] * fres * 0.5F + lit, body[1] + rim[1] * fres * 0.5F + lit, body[2] + rim[2] * fres * 0.5F + lit};
               vtx(vc, m, n, cam, w, col, bodyA);
            }
            if (gv != null) {
               for (Vec3 nv : q) {
                  Vec3 w = c.add(nv.scale(rr * 1.01));
                  float fres = (float)Math.pow(1.0 - Math.abs(nv.dot(cam.subtract(w).normalize())), 2.5);
                  gvtx(m, cam, w, rim, fres * glowA * (inside ? 1.0F : 0.9F));
               }
            }
         }
      }
      // three slow bands of light winding over the shell
      for (int b = 0; b < 3; b++) {
         double tilt = b * Math.PI / 3 + now * 0.012, spin = now * 0.05 * (b % 2 == 0 ? 1 : -1) + b * 2.1;
         Vec3 ax = new Vec3(Math.cos(tilt), 0.6, Math.sin(tilt)).normalize();
         Vec3 u0 = ax.cross(new Vec3(0, 1, 0)).normalize(), u1 = ax.cross(u0).normalize();
         Vec3 prev = null;
         for (int k = 0; k <= 10; k++) {
            double t = spin + k * 0.16;
            Vec3 w = c.add(u0.scale(Math.cos(t) * rr * 1.015).add(u1.scale(Math.sin(t) * rr * 1.015)));
            if (prev != null) {
               boolean front = inside || w.subtract(c).dot(cam.subtract(w)) > 0.0;
               if (front) gline(m, cam, prev, w, 0.05F, 1, glowA * 0.7F * (float)Math.sin(Math.PI * k / 10.0));
            }
            prev = w;
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
   static void devilForm(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Player p, float pt, double now, double[] f) {
      double age = now - f[0];
      float fade = (float)Mth.clamp((f[1] + 20.0 - now) / 20.0, 0.0, 1.0);
      // unfold with an overshoot (ease-out-back)
      double g = Mth.clamp(age / 14.0, 0.0, 1.0) - 1.0;
      float grow = (float)(1.0 + 2.70158 * g * g * g + 1.70158 * g * g);
      float a = fade * Mth.clamp(0.35F + 0.65F * (float)Mth.clamp(age / 14.0, 0.0, 1.0), 0.0F, 1.0F);
      if (a <= 0.01F) return;

      double by = Math.toRadians((double)Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) - animYawDeg(p, pt));
      Vec3 fw = new Vec3(-Math.sin(by), 0.0, Math.cos(by));
      Vec3 lf = new Vec3(Math.cos(by), 0.0, Math.sin(by));
      Vec3 base = new Vec3(Mth.lerp((double)pt, p.xo, p.getX()), Mth.lerp((double)pt, p.yo, p.getY()), Mth.lerp((double)pt, p.zo, p.getZ()));
      float h = p.getBbHeight();
      float[] dark = BODY[EMBER];

      // ---- how the body is moving drives the wings
      double speed = Math.sqrt((p.getX() - p.xo) * (p.getX() - p.xo) + (p.getZ() - p.zo) * (p.getZ() - p.zo));
      double sweep = Mth.clamp(speed * 3.0, 0.0, 0.7);

      Vec3 back = base.add(0.0, h * 0.76, 0.0).subtract(fw.scale(0.2));
      // (the wings themselves are the animated GeckoLib model - GeoFormLayer)

      // the crown of black flame and the cloak are pixel sprites now (PixelFx CROWN / MANTLE)
   }

   /**
    * Spirit Dive (Yuno merged with Sylph): four wings of wind on the back - two large upper wings and two smaller
    * lower ones - made of long, glowing, half-transparent feathers of air that flutter, plus two rings of wind turning
    * around the body.
    */
   static void spiritForm(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Player p, float pt, double now, double[] f) {
      double age = now - f[0];
      float fade = (float)Mth.clamp((f[1] + 12.0 - now) / 12.0, 0.0, 1.0);
      float grow = (float)(1.0 - Math.pow(1.0 - Mth.clamp(age / 12.0, 0.0, 1.0), 3.0));
      float a = fade * grow;
      if (a <= 0.01F) return;
      double by = Math.toRadians((double)Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) - animYawDeg(p, pt));
      Vec3 fw = new Vec3(-Math.sin(by), 0.0, Math.cos(by));
      Vec3 lf = new Vec3(Math.cos(by), 0.0, Math.sin(by));
      Vec3 base = new Vec3(Mth.lerp((double)pt, p.xo, p.getX()), Mth.lerp((double)pt, p.yo, p.getY()), Mth.lerp((double)pt, p.zo, p.getZ()));
      float h = p.getBbHeight();
      boolean air = !p.onGround();
      double flap = Math.sin(now * (air ? 0.45 : 0.18)) * (air ? 0.35 : 0.12);
      float[] rim = RIM[WIND], white = {0.92F, 1.0F, 0.96F};
      Vec3 back = base.add(0.0, h * 0.74, 0.0).subtract(fw.scale(0.22));
      Vec3 up = new Vec3(0, 1, 0);
      // {root height offset, base angle up from horizontal (deg), length, feathers}
      double[][] wings = {{0.08, 38, 1.9, 6}, {-0.35, -22, 1.1, 4}};
      for (double[] wg : wings) {
         for (int side = -1; side <= 1; side += 2) {
            Vec3 root = back.add(0, wg[0], 0).add(lf.scale(0.12 * side));
            for (int k = 0; k < (int)wg[3]; k++) {
               double spread = Math.toRadians(wg[1] - k * (wg[1] > 0 ? 14 : 12) + Math.toDegrees(flap) * (1.0 - k * 0.1));
               double sweepBack = 0.35 + k * 0.08;
               Vec3 dir = lf.scale(side * Math.cos(spread)).add(up.scale(Math.sin(spread))).subtract(fw.scale(sweepBack)).normalize();
               double len = wg[2] * (1.0 - k * 0.09) * grow;
               Vec3 tip = root.add(dir.scale(len));
               Vec3 wdt = dir.cross(fw).normalize().scale(0.09 + 0.02 * (wg[3] - k));
               Vec3 midp = root.add(dir.scale(len * 0.55));
               quad(vc, m, n, cam, root, midp.add(wdt), tip, midp.subtract(wdt), white, a * 0.32F, a * 0.22F, 0.0F, a * 0.22F);
               gquad(m, cam, root, midp.add(wdt), tip, midp.subtract(wdt), rim, a * 0.45F, a * 0.3F, a * 0.05F, a * 0.3F);
               gline(m, cam, root, tip, 0.025F, WIND, a * 0.6F);
            }
         }
      }
      // the crown of wind: a thin circlet of light around the brow with five tall feathers of air rising from it,
      // the middle one tallest, all swaying slightly
      double hyr = Math.toRadians(Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot));
      Vec3 hf = new Vec3(-Math.sin(hyr), 0, Math.cos(hyr)), hl = new Vec3(Math.cos(hyr), 0, Math.sin(hyr));
      Vec3 crown = base.add(0, h * 0.97, 0);
      Vec3 prevC = null;
      for (int k = 0; k <= 16; k++) {
         double t = Math.PI * 2 * k / 16;
         Vec3 q = crown.add(hf.scale(Math.cos(t) * 0.3)).add(hl.scale(Math.sin(t) * 0.3));
         if (prevC != null) gline(m, cam, prevC, q, 0.025F, WIND, a * 0.85F);
         prevC = q;
      }
      for (int k = -2; k <= 2; k++) {
         double t = k * 0.42;   // spread across the front of the circlet
         Vec3 root = crown.add(hf.scale(Math.cos(t) * 0.3)).add(hl.scale(Math.sin(t) * 0.3));
         double tall = (0.42 - Math.abs(k) * 0.09) * grow;
         double sway = Math.sin(now * 0.25 + k) * 0.04;
         Vec3 tip = root.add(0, tall, 0).add(hf.scale(-0.05 + sway)).add(hl.scale(Math.sin(t) * 0.08));
         Vec3 w = hl.scale(0.045);
         Vec3 midp = root.add(tip.subtract(root).scale(0.5));
         quad(vc, m, n, cam, root, midp.add(w), tip, midp.subtract(w), white, a * 0.5F, a * 0.35F, 0.0F, a * 0.35F);
         gquad(m, cam, root, midp.add(w), tip, midp.subtract(w), rim, a * 0.6F, a * 0.4F, a * 0.1F, a * 0.4F);
         gline(m, cam, root, tip, 0.018F, WIND, a * 0.8F);
      }
      halo(m, cam, crown.add(hf.scale(0.3)).add(0, 0.05, 0), 0.12, white, a * 0.8F);   // a jewel of light at the front
      // two rings of wind turning around the body
      for (int ring = 0; ring < 2; ring++) {
         double tilt = ring == 0 ? 0.25 : -0.35, spin = now * (ring == 0 ? 0.22 : -0.17);
         double yc = h * (ring == 0 ? 0.35 : 0.7), R = 0.75 + ring * 0.12;
         Vec3 prev = null;
         for (int k = 0; k <= 14; k++) {
            double t = spin + k * 0.32;
            Vec3 q = base.add(Math.cos(t) * R, yc + Math.sin(t) * R * tilt, Math.sin(t) * R);
            if (prev != null) gline(m, cam, prev, q, 0.03F, WIND, a * 0.55F * (float)Math.sin(Math.PI * k / 14.0));
            prev = q;
         }
      }
   }

   // indexes into the demon wing skeleton
   static final int WS = 0, WE = 1, WW = 2, WF = 3, WC0 = 11, WC1 = 12, WWAIST = 13;





   static void heldAura(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 cam, Player p, float pt, float pulse, Item item, boolean main) {
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

      double by = Math.toRadians((double)Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) - animYawDeg(p, pt));
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
}
