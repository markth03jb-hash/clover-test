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
 * The open grimoire drawn in the world: its flame outline, glowing underglow and turning pages.
 * (Split out of FxRenderer.)
 */
final class FxBooks extends FxBase {
   private FxBooks() {}


   static void book(PoseStack ps, VertexConsumer vc, Vec3 cam, FxRenderer.BookDraw b) {
      ps.pushPose();
      ps.translate(b.pos().x - cam.x, b.pos().y - cam.y, b.pos().z - cam.z);
      ps.mulPose(Axis.YP.rotationDegrees(-b.yaw()));
      ps.mulPose(Axis.YP.rotationDegrees(180.0F));
      ps.mulPose(Axis.XP.rotationDegrees(-b.tilt()));
      ps.scale(b.scale(), b.scale(), b.scale());
      Matrix4f m = ps.last().pose();
      Matrix3f n = ps.last().normal();
      float HW = b.hw();
      float HH = b.hh();
      float V = (float)Math.toRadians((double)b.vDeg());
      float al = b.alpha();

      // the book itself is the real 3D model (drawn by GrimoireFloatRenderer); here each grimoire's own aura
      bookAura(vc, m, n, b, HW, HH, V, al);

      if (b.flipCycle() >= 0.0F) {
         for (int i = 0; i < 3; i++) {
            float sP = (b.flipCycle() - (float)(4 + i * 5)) / 14.0F;
            if (!(sP <= 0.0F) && !(sP >= 1.0F)) {
               float mm = 0.5F - 0.5F * (float)Math.cos(Math.PI * (double)sP);
               double alpha = Math.toRadians((double)(-22.5F - 135.0F * mm));
               float lift = 0.07F + 0.03F * (1.0F - 2.0F * Math.abs(mm - 0.5F));
               PAGES.add(new PageDraw(new Matrix4f(m), new Matrix3f(n), (float)Math.cos(alpha), (float)(-Math.sin(alpha)), lift, HW * 0.95F, HH * 0.92F, b.light()));
            }
         }
      }

      ps.popPose();
   }

   /** A flat glowing ribbon in the book's plane (local frame, viewer roughly along +z). */
   static void lline(Matrix4f m, Vec3 p0, Vec3 p1, float w, int pal, float a0, float a1) {
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
   static void lstar(Matrix4f m, Vec3 c, float r, int pal, float a) {
      lline(m, c.add(-r, 0.0, 0.0), c.add(r, 0.0, 0.0), r * 0.18F, pal, a, a);
      lline(m, c.add(0.0, -r, 0.0), c.add(0.0, r, 0.0), r * 0.18F, pal, a, a);
   }

   /** Soft elliptical underglow behind the book. */
   static void underglow(Matrix4f m, float HW, float HH, int pal, float a) {
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
   static void bookAura(VertexConsumer vc, Matrix4f m, Matrix3f n, FxRenderer.BookDraw b, float HW, float HH, float V, float al) {
      double t = bookTime;
      int pal = b.pal();
      switch (b.style()) {
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
   static void page(VertexConsumer vc, Matrix4f m, Matrix3f n, float dx, float dz, float lift, float w, float h, float al) {
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

   static void panel(VertexConsumer vc, Matrix4f m, Matrix3f n, float dx, float dz, float lift, float w, float h, float al) {
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
   static void panelOutline(VertexConsumer vc, Matrix4f m, Matrix3f n, float dx, float dz, float lift, float w, float h, float al, int seed, int pal) {
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

   static void edge(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 o0, Vec3 o1, Vec3 i1, Vec3 i0, float[] dark, float al, float[] glow) {
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

   static Vec3 pt(float dx, float dz, float lift, float u, float y) {
      return new Vec3((double)(dx * u), (double)y, (double)(dz * u + lift));
   }

   static final ResourceLocation PAGE_R = ResourceLocation.fromNamespaceAndPath("arcane_addon", "textures/item/spell_book_models/black_grimoire_page_right.png");
   static final ResourceLocation PAGE_L = ResourceLocation.fromNamespaceAndPath("arcane_addon", "textures/item/spell_book_models/black_grimoire_page_left.png");
   static final List<PageDraw> PAGES = new ArrayList<>();

   static record PageDraw(Matrix4f m, Matrix3f n, float dx, float dz, float lift, float w, float h, int light) {
   }

   /** One side of a turning page: wound so only the side facing you is drawn. */
   static void pageQuad(VertexConsumer vc, PageDraw p, boolean back) {
      Vec3[] q = {pt(p.dx, p.dz, p.lift, 0.0F, -p.h), pt(p.dx, p.dz, p.lift, p.w, -p.h), pt(p.dx, p.dz, p.lift, p.w, p.h), pt(p.dx, p.dz, p.lift, 0.0F, p.h)};
      float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
      for (int i = 0; i < 4; i++) {
         int k = back ? 3 - i : i;
         float u = back ? 1.0F - uv[k][0] : uv[k][0];
         vc.vertex(p.m, (float)q[k].x, (float)q[k].y, (float)q[k].z).color(1.0F, 1.0F, 1.0F, 1.0F).uv(u, uv[k][1])
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(p.light).normal(p.n, 0.0F, 0.0F, back ? -1.0F : 1.0F).endVertex();
      }
   }
}
