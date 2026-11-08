package com.example.arcaneaddon.client;

import com.example.arcaneaddon.registry.AddonItemRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * The grimoire of every addon book: small and closed on your hip; when you cast it floats open beside you as the
 * real 3D book with its own aura. It is anchored to your body (not the camera) and trails a little behind your
 * movement and turning, so it feels like it is floating after you. In first person it hovers low at your left.
 * Summoning Asta's swords draws the blade up out of the open pages, hilt first, then into your hand.
 */
@EventBusSubscriber(modid = "arcane_addon", value = {Dist.CLIENT})
public class GrimoireFloatRenderer {
   private static final float HIP_SCALE = 0.4F;
   private static final float OPEN_SCALE = 0.6F;
   private static final float FP_SCALE = 0.36F;
   /** How quickly the floating book catches up with where it should be (per tick). Lower = more lag. */
   private static final double FOLLOW = 0.3;
   private static final float TURN = 0.14F;

   private static final Map<Integer, ItemStack> BOOK_STACK = new HashMap<>();
   private static final Set<Integer> BURSTED = new HashSet<>();
   private static final Map<Integer, Long> ANIMS = new HashMap<>();
   private static final Map<Integer, Long> OPEN_UNTIL = new HashMap<>();
   private static final Map<Integer, float[]> PROGRESS = new HashMap<>();
   private static final Map<Integer, float[]> SMOOTH_YAW = new HashMap<>();
   private static final Map<Integer, Vec3[]> SMOOTH_POS = new HashMap<>();
   private static final Set<Integer> HOLDERS = new HashSet<>();
   private static final Set<Integer> OFFHAND_ANIMS = new HashSet<>();

   public static void startAnim(int entityId, boolean offhand) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null) {
         if (offhand) OFFHAND_ANIMS.add(entityId);
         else OFFHAND_ANIMS.remove(entityId);
         ANIMS.put(entityId, level.getGameTime());
         openBook(entityId);
      }
   }

   public static void openBook(int entityId) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null) OPEN_UNTIL.put(entityId, level.getGameTime() + 60L);
   }

   /** Every grimoire of this addon floats open while you cast (Black, Dark, Steel and the themed ones). */
   public static boolean isAddonGrimoire(ItemStack s) {
      return s != null && !s.isEmpty() && "arcane_addon".equals(BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace());
   }

   /** {palette, aura style} per grimoire - see FxRenderer.bookAura. */
   private static int[] aura(Item item) {
      if (item == AddonItemRegistry.BLACK_GRIMOIRE.get()) return new int[]{0, 0};
      if (item == AddonItemRegistry.DARK_GRIMOIRE.get()) return new int[]{1, 1};
      if (item == AddonItemRegistry.STEEL_GRIMOIRE.get()) return new int[]{7, 2};
      if (item == AddonItemRegistry.WIND_GRIMOIRE.get()) return new int[]{5, 3};
      String id = BuiltInRegistries.ITEM.getKey(item).getPath();
      return switch (id) {
         case "four_leaf_grimoire" -> new int[]{5, 3};
         case "three_leaf_grimoire" -> new int[]{8, 4};
         case "mana_zone_codex" -> new int[]{3, 5};
         case "spirit_bound_grimoire" -> new int[]{6, 6};
         default -> new int[]{8, 4};
      };
   }

   /**
    * The open 3D book, centred, in the frame FxRenderer.book draws its aura in: local +z (the open model's page
    * side) faces you.
    */
   private static void renderOpen(Minecraft mc, PoseStack ps, BufferSource buf, ItemStack book, int seed, int light) {
      mc.getItemRenderer().renderStatic(book, ItemDisplayContext.HEAD, light, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, seed);
   }

   private static float ease(float x) {
      x = Mth.clamp(x, 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
   }

   private static Vec3 base(Player p, float pt) {
      return new Vec3(Mth.lerp((double)pt, p.xo, p.getX()), Mth.lerp((double)pt, p.yo, p.getY()), Mth.lerp((double)pt, p.zo, p.getZ()));
   }

   private static Vec3 fwdOf(float yaw) {
      double r = Math.toRadians((double)yaw);
      return new Vec3(-Math.sin(r), 0.0, Math.cos(r));
   }

   private static Vec3 leftOf(float yaw) {
      double r = Math.toRadians((double)yaw);
      return new Vec3(Math.cos(r), 0.0, Math.sin(r));
   }

   private static boolean firstPerson(Minecraft mc, Player p) {
      return p == mc.player && mc.options.getCameraType().isFirstPerson();
   }

   /** Where the open book wants to be: beside you in third person, low at your left in first person. Follows your body, not your pitch. */
   private static Vec3 openTarget(Player p, float yaw, boolean fp, double time) {
      Vec3 f = fwdOf(yaw), l = leftOf(yaw);
      double bob = Math.sin(time * 0.08 + (double)p.getId()) * (fp ? 0.015 : 0.05);
      if (fp) return p.position().add(0.0, p.getEyeHeight() - 0.5 + bob, 0.0).add(f.scale(0.85)).add(l.scale(0.5));
      return p.position().add(0.0, (double)p.getBbHeight() * 0.7 + bob, 0.0).add(f.scale(1.7)).add(l.scale(1.35));
   }

   private static Vec3 hipPosition(Player p, float pt, float bodyYaw) {
      return base(p, pt).add(0.0, (double)p.getBbHeight() * 0.45, 0.0).add(leftOf(bodyYaw).scale(0.36)).add(fwdOf(bodyYaw).scale(-0.05));
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent e) {
      if (e.phase != Phase.END) return;
      Minecraft mc = Minecraft.getInstance();
      HOLDERS.clear();
      if (mc.level == null) {
         ANIMS.clear();
         OPEN_UNTIL.clear();
         PROGRESS.clear();
         SMOOTH_POS.clear();
         return;
      }
      if (mc.isPaused()) return;
      long now = mc.level.getGameTime();
      RandomSource rnd = mc.level.random;
      for (Player p : mc.level.players()) {
         ItemStack equipped = Utils.getPlayerSpellbookStack(p);
         boolean has = isAddonGrimoire(equipped);
         if (has) BOOK_STACK.put(p.getId(), equipped.copyWithCount(1));
         float[] pr = PROGRESS.computeIfAbsent(p.getId(), k -> new float[]{0.0F, 0.0F});
         pr[0] = pr[1];
         boolean open = has && (OPEN_UNTIL.getOrDefault(p.getId(), 0L) > now || ANIMS.containsKey(p.getId()));
         pr[1] = Mth.clamp(pr[1] + (open ? 0.12F : -0.08F), 0.0F, 1.0F);
         if (has) HOLDERS.add(p.getId());

         // the book turns after you and floats after you, a little behind
         float tgt = p.getYRot();
         float[] sy = SMOOTH_YAW.computeIfAbsent(p.getId(), k -> new float[]{tgt, tgt});
         sy[0] = sy[1];
         sy[1] += Mth.wrapDegrees(tgt - sy[1]) * TURN;
         Vec3 target = openTarget(p, sy[1], firstPerson(mc, p), (double)now);
         Vec3[] sp = SMOOTH_POS.computeIfAbsent(p.getId(), k -> new Vec3[]{target, target});
         sp[0] = sp[1];
         sp[1] = sp[1].distanceToSqr(target) > 16.0 || pr[0] <= 0.0F ? target : sp[1].add(target.subtract(sp[1]).scale(FOLLOW));

         if (has && !p.isSpectator() && !p.isInvisible() && pr[1] > 0.6F) {
            Vec3 c = sp[1];
            if (pr[0] <= 0.6F) {
               mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6F, 0.7F, false);
               mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 0.8F, false);
            }
            long cyc = (now + (long)p.getId() * 13L) % 50L;
            if (pr[1] >= 1.0F && (cyc == 4L || cyc == 9L || cyc == 14L)) {
               mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.5F, 0.9F + rnd.nextFloat() * 0.3F, false);
            }
         }
         Long st = ANIMS.get(p.getId());
         if (has && st != null && now - st <= 1L && BURSTED.add(p.getId())) {
            Vec3 cx = sp[1].add(0.0, 0.1, 0.0);
            mc.level.playLocalSound(cx.x, cx.y, cx.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.7F, 0.5F, false);
         }
      }
      BURSTED.removeIf(id -> !ANIMS.containsKey(id));
      ANIMS.values().removeIf(s -> now - s > 18L);
      PROGRESS.keySet().removeIf(id -> !HOLDERS.contains(id) && PROGRESS.get(id)[1] <= 0.0F);
      SMOOTH_YAW.keySet().retainAll(PROGRESS.keySet());
      SMOOTH_POS.keySet().retainAll(PROGRESS.keySet());
      BOOK_STACK.keySet().retainAll(HOLDERS);
   }

   @SubscribeEvent
   public static void onRender(RenderLevelStageEvent e) {
      if (e.getStage() != Stage.AFTER_ENTITIES) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || HOLDERS.isEmpty()) return;
      float pt = e.getPartialTick();
      PoseStack ps = e.getPoseStack();
      Vec3 cam = e.getCamera().getPosition();
      BufferSource buf = mc.renderBuffers().bufferSource();
      ItemStack fallback = new ItemStack((ItemLike)AddonItemRegistry.BLACK_GRIMOIRE.get());
      ItemStack sword = new ItemStack((ItemLike)AddonItemRegistry.DEMON_SLAYER_SWORD.get());
      ItemStack twin = new ItemStack((ItemLike)AddonItemRegistry.DEMON_DWELLER_SWORD.get());
      double time = (double)mc.level.getGameTime() + pt;

      for (Player p : mc.level.players()) {
         if (!HOLDERS.contains(p.getId()) || p.isSpectator() || p.isInvisible()) continue;
         float[] pr = PROGRESS.get(p.getId());
         Vec3[] sp = SMOOTH_POS.get(p.getId());
         if (pr == null || sp == null) continue;
         ItemStack book = BOOK_STACK.getOrDefault(p.getId(), fallback);
         float prog = ease(Mth.lerp(pt, pr[0], pr[1]));
         boolean fp = firstPerson(mc, p);
         float yaw = p.getViewYRot(pt);
         float[] sy = SMOOTH_YAW.get(p.getId());
         float yawS = sy == null ? yaw : sy[0] + Mth.wrapDegrees(sy[1] - sy[0]) * pt;
         float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
         Vec3 open = sp[0].add(sp[1].subtract(sp[0]).scale((double)pt));
         // where it comes from: the hip (third person) or from below the view (first person)
         Vec3 from = fp ? base(p, pt).add(0.0, p.getEyeHeight() - 1.1, 0.0).add(fwdOf(yawS).scale(0.4)).add(leftOf(yawS).scale(0.4))
                        : hipPosition(p, pt, bodyYaw);
         Vec3 bookPos = from.add(open.subtract(from).scale((double)prog));
         int wl = LevelRenderer.getLightColor(mc.level, BlockPos.containing(bookPos));
         int light = LightTexture.pack(Math.max(LightTexture.block(wl), 5), Math.max(LightTexture.sky(wl), 2));
         int[] au = aura(book.getItem());

         ps.pushPose();
         ps.translate(bookPos.x - cam.x, bookPos.y - cam.y, bookPos.z - cam.z);
         if (!fp && prog < 0.5F) {
            // closed on the hip, small enough to sit against the leg; it swings as you walk
            float s = HIP_SCALE * (1.0F + 0.25F * prog);
            ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
            float sway = (float)Math.sin(time * 0.12 + (double)p.getId()) * 2.0F + (float)Math.min(p.getDeltaMovement().horizontalDistance() * 40.0, 14.0);
            ps.mulPose(Axis.ZP.rotationDegrees(sway));
            ps.mulPose(Axis.XP.rotationDegrees(90.0F));
            ps.mulPose(Axis.YP.rotationDegrees(prog * 120.0F));
            ps.scale(s, s, s);
            ps.translate(-0.0625, 0.21875, 0.0);
            mc.getItemRenderer().renderStatic(book, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, p.getId());
         } else if (prog > 0.02F) {
            // open, floating: the solid 3D book plus its aura
            float k = ease(fp ? prog : (prog - 0.5F) * 2.0F);
            float kb = k - 1.0F;
            float back = 1.0F + 2.70158F * kb * kb * kb + 1.70158F * kb * kb;
            float s = (fp ? FP_SCALE : OPEN_SCALE) * (0.5F + 0.5F * back);
            float tilt = (fp ? 42.0F : 35.0F) * k;
            float turn = fp ? 18.0F : 0.0F;   // in first person it angles in toward you a little
            ps.mulPose(Axis.YP.rotationDegrees(-(yawS - turn)));
            ps.mulPose(Axis.YP.rotationDegrees(180.0F));
            ps.mulPose(Axis.XP.rotationDegrees(-tilt));
            ps.scale(s, s, s);
            renderOpen(mc, ps, buf, book, p.getId(), light);
            float cyc = k > 0.95F ? (float)((mc.level.getGameTime() + (long)p.getId() * 13L) % 50L) + pt : -1.0F;
            FxRenderer.queueBook(bookPos, yawS - turn, tilt, s, k, cyc, 0.46F, 0.375F, 22.5F, au[0], au[1], light);
         }
         ps.popPose();

         // Asta's sword rising out of the open pages, hilt first, then pulled into the hand
         Long start = ANIMS.get(p.getId());
         if (start != null && prog >= 0.99F) {
            float t = Mth.clamp(((float)(mc.level.getGameTime() - start) + pt) / 16.0F, 0.0F, 1.0F);
            boolean off = OFFHAND_ANIMS.contains(p.getId());
            Vec3 fwd = fwdOf(yaw), left = leftOf(yaw);
            Vec3 hand = base(p, pt).add(0.0, (double)p.getEyeHeight() * 0.8, 0.0).add(fwd.scale(0.55)).add(left.scale(off ? 0.4 : -0.4));
            float half = off ? 1.05F : 1.45F;
            Vec3 up = new Vec3(0.0, 1.0, 0.0);
            Vec3 pos;
            float scale;
            float roll;
            if (t < 0.45F) {
               float u = ease(t / 0.45F);
               scale = 0.1F + 0.9F * u;
               // the tip stays on the pages while the blade grows out of them, so it emerges from the centre of the book
               pos = bookPos.add(up.scale((double)(half * scale * 0.9F + 0.05F)));
               roll = 225.0F;
            } else {
               float u = (t - 0.45F) / 0.55F;
               float m = ease(u);
               Vec3 top = bookPos.add(up.scale((double)(half * 0.9F + 0.05F)));
               Vec3 ctrl = top.add(hand).scale(0.5).add(0.0, 0.35, 0.0).add(fwd.scale(0.35));
               pos = top.scale((double)((1.0F - m) * (1.0F - m))).add(ctrl.scale((double)(2.0F * (1.0F - m) * m))).add(hand.scale((double)(m * m)));
               scale = 1.0F;
               roll = 225.0F + (off ? -90.0F : 90.0F) * ease(Mth.clamp(u * 3.0F, 0.0F, 1.0F));
            }
            double ang = Math.toRadians((double)(45.0F + roll));
            double yr = Math.toRadians((double)yaw);
            Vec3 axis = new Vec3(Math.cos(ang) * Math.cos(yr), Math.sin(ang), Math.cos(ang) * Math.sin(yr));
            float auraA = t < 0.45F ? 0.35F + 0.35F * (t / 0.45F) : 0.7F * (1.0F - 0.6F * (t - 0.45F) / 0.55F);
            FxRenderer.queueAura(pos.subtract(axis.scale((double)(half * scale))), pos.add(axis.scale((double)(half * scale))), (off ? 0.16F : 0.22F) * scale, off ? 1 : 0, auraA);
            ps.pushPose();
            ps.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
            ps.mulPose(Axis.YP.rotationDegrees(-yaw));
            ps.mulPose(Axis.ZP.rotationDegrees(roll));
            ps.scale(0.9F * scale, 0.9F * scale, 0.9F * scale);
            ps.translate(-0.582F, -0.582F, 0.0F);
            mc.getItemRenderer().renderStatic(off ? twin : sword, ItemDisplayContext.NONE, 15728880, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, p.getId());
            ps.popPose();
         }
      }
      buf.endBatch();
   }
}
