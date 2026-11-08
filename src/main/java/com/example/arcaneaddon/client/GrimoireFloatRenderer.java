package com.example.arcaneaddon.client;

import com.example.arcaneaddon.item.BlackGrimoireItem;
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
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
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
import org.joml.Vector3f;

@EventBusSubscriber(
   modid = "arcane_addon",
   value = {Dist.CLIENT}
)
public class GrimoireFloatRenderer {
   private static final double FORWARD = 1.7;
   private static final double LEFT = 1.35;
   private static final double HEIGHT = 0.7;
   private static final float BOOK_SCALE = 0.42F;
   private static final float TILT = 35.0F;
   private static final int OPEN_TICKS = 60;
   private static final double HIP_SIDE = 0.4;
   private static final double HIP_BACK = 0.05;
   private static final double HIP_HEIGHT = 0.45;
   private static final float HIP_SCALE = 0.4F;
   /** Open book: scale of the 3D model when floating beside you, and when held in view in first person. */
   private static final float OPEN_SCALE = 0.6F;
   private static final float FP_SCALE = 0.34F;
   private static final Map<Integer, ItemStack> BOOK_STACK = new HashMap<>();
   private static final int FLIP_CYCLE = 50;
   private static final float SWORD_SCALE = 0.9F;
   private static final float SWORD_CENTER = 0.582F;
   private static final Set<Integer> BURSTED = new HashSet<>();
   private static final Map<Integer, Long> ANIMS = new HashMap<>();
   private static final Map<Integer, Long> OPEN_UNTIL = new HashMap<>();
   private static final Map<Integer, float[]> PROGRESS = new HashMap<>();
   private static final Map<Integer, float[]> SMOOTH_YAW = new HashMap<>();
   private static final Set<Integer> HOLDERS = new HashSet<>();
   private static final Set<Integer> OFFHAND_ANIMS = new HashSet<>();

   public static void startAnim(int entityId, boolean offhand) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null) {
         if (offhand) {
            OFFHAND_ANIMS.add(entityId);
         } else {
            OFFHAND_ANIMS.remove(entityId);
         }

         ANIMS.put(entityId, level.getGameTime());
         openBook(entityId);
      }
   }

   public static void openBook(int entityId) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null) {
         OPEN_UNTIL.put(entityId, level.getGameTime() + 60L);
      }
   }

   /** Every grimoire of this addon floats open while you cast (Black, Dark, Steel and the themed ones). */
   public static boolean isAddonGrimoire(ItemStack s) {
      return s != null && !s.isEmpty() && "arcane_addon".equals(BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace());
   }

   private static boolean customModel(Item item) {
      return item == AddonItemRegistry.BLACK_GRIMOIRE.get();
   }

   /**
    * Draws the real 3D open-book model, centred, pages facing local +z (matches FxRenderer.book's frame).
    * The Black Grimoire has its own upright open model; the others use Iron's flat open template, turned to match.
    */
   private static void renderOpen(Minecraft mc, PoseStack ps, BufferSource buf, ItemStack book, int seed, int light) {
      if (!customModel(book.getItem())) {
         ps.mulPose(Axis.XP.rotationDegrees(90.0F));
         ps.translate(0.125, -0.594, 0.0);
      }
      mc.getItemRenderer().renderStatic(book, ItemDisplayContext.HEAD, light, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, seed);
   }

   private static void queueOutline(ItemStack book, Vec3 pos, float yaw, float tilt, float scale, float alpha, float cyc) {
      if (customModel(book.getItem())) FxRenderer.queueBook(pos, yaw, tilt, scale, alpha, cyc, 0.46F, 0.375F, 22.5F);
      else FxRenderer.queueBook(pos, yaw, tilt, scale, alpha, cyc, 0.44F, 0.31F, 0.0F);
   }

   private static float ease(float x) {
      x = Mth.clamp(x, 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
   }

   private static Vec3 base(Player p, float pt) {
      return new Vec3(
         Mth.lerp((double)pt, p.xo, p.getX()),
         Mth.lerp((double)pt, p.yo, p.getY()),
         Mth.lerp((double)pt, p.zo, p.getZ())
      );
   }

   private static Vec3 floatPosition(Player p, float pt, float yaw) {
      double r = Math.toRadians((double)yaw);
      Vec3 fwd = new Vec3(-Math.sin(r), 0.0, Math.cos(r));
      Vec3 left = new Vec3(Math.cos(r), 0.0, Math.sin(r));
      double bob = Math.sin((double)((float)p.level().getGameTime() + pt) * 0.08 + (double)p.getId()) * 0.05;
      return base(p, pt).add(0.0, (double)p.getBbHeight() * 0.7 + bob, 0.0).add(fwd.scale(1.7)).add(left.scale(1.35));
   }

   private static Vec3 hipPosition(Player p, float pt, float bodyYaw) {
      double r = Math.toRadians((double)bodyYaw);
      Vec3 fwd = new Vec3(-Math.sin(r), 0.0, Math.cos(r));
      Vec3 left = new Vec3(Math.cos(r), 0.0, Math.sin(r));
      return base(p, pt).add(0.0, (double)p.getBbHeight() * 0.45, 0.0).add(left.scale(0.4)).add(fwd.scale(-0.05));
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent e) {
      if (e.phase == Phase.END) {
         Minecraft mc = Minecraft.getInstance();
         HOLDERS.clear();
         if (mc.level == null) {
            ANIMS.clear();
            OPEN_UNTIL.clear();
            PROGRESS.clear();
         } else if (!mc.isPaused()) {
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
               if (has) {
                  HOLDERS.add(p.getId());
               }

               float tgt = p.getViewYRot(1.0F);
               float[] sy = SMOOTH_YAW.computeIfAbsent(p.getId(), k -> new float[]{tgt, tgt});
               sy[0] = sy[1];
               sy[1] += Mth.wrapDegrees(tgt - sy[1]) * 0.18F;
               if (has && !p.isSpectator() && !p.isInvisible() && pr[1] > 0.6F) {
                  Vec3 c = floatPosition(p, 1.0F, sy[1]);
                  if (pr[0] <= 0.6F) {
                     mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6F, 0.7F, false);
                     mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 0.8F, false);
                  }

                  long cyc = (now + (long)p.getId() * 13L) % 50L;
                  if (pr[1] >= 1.0F && (cyc == 4L || cyc == 9L || cyc == 14L)) {
                     mc.level
                        .playLocalSound(c.x, c.y, c.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.5F, 0.9F + rnd.nextFloat() * 0.3F, false);
                  }
               }

               Long st = ANIMS.get(p.getId());
               if (has && st != null && now - st <= 1L && BURSTED.add(p.getId())) {
                  Vec3 cx = floatPosition(p, 1.0F, sy[1]).add(0.0, 0.1, 0.0);
                  FxRenderer.localRing(cx, OFFHAND_ANIMS.contains(p.getId()) ? 1 : 0, 0.05F, 0.75F, 0.1F, 8);   // a soft glow off the open pages
                  mc.level.playLocalSound(cx.x, cx.y, cx.z, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.7F, 0.5F, false);
               }
            }

            BURSTED.removeIf(id -> !ANIMS.containsKey(id));
            ANIMS.values().removeIf(s -> now - s > 18L);
            PROGRESS.keySet().removeIf(id -> !HOLDERS.contains(id) && PROGRESS.get(id)[1] <= 0.0F);
            SMOOTH_YAW.keySet().retainAll(PROGRESS.keySet());
            BOOK_STACK.keySet().retainAll(HOLDERS);
         }
      }
   }

   @SubscribeEvent
   public static void onRender(RenderLevelStageEvent e) {
      if (e.getStage() == Stage.AFTER_ENTITIES) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null && !HOLDERS.isEmpty()) {
            float pt = e.getPartialTick();
            PoseStack ps = e.getPoseStack();
            Vec3 cam = e.getCamera().getPosition();
            BufferSource buf = mc.renderBuffers().bufferSource();
            ItemStack fallback = new ItemStack((ItemLike)AddonItemRegistry.BLACK_GRIMOIRE.get());
            Camera camera = e.getCamera();
            ItemStack sword = new ItemStack((ItemLike)AddonItemRegistry.DEMON_SLAYER_SWORD.get());
            ItemStack twin = new ItemStack((ItemLike)AddonItemRegistry.DEMON_DWELLER_SWORD.get());
            new ItemStack((ItemLike)AddonItemRegistry.GRIMOIRE_PAGE.get());

            for (Player p : mc.level.players()) {
               if (HOLDERS.contains(p.getId()) && !p.isSpectator() && !p.isInvisible()) {
                  float[] pr = PROGRESS.get(p.getId());
                  if (pr != null) {
                     ItemStack book = BOOK_STACK.getOrDefault(p.getId(), fallback);
                     float prog = ease(Mth.lerp(pt, pr[0], pr[1]));
                     boolean fp = p == mc.player && mc.options.getCameraType().isFirstPerson();
                     float yaw = p.getViewYRot(pt);
                     float[] sy = SMOOTH_YAW.get(p.getId());
                     float yawS = sy == null ? yaw : sy[0] + Mth.wrapDegrees(sy[1] - sy[0]) * pt;
                     float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
                     double yawR = Math.toRadians((double)yaw);
                     Vec3 fwd = new Vec3(-Math.sin(yawR), 0.0, Math.cos(yawR));
                     Vec3 left = new Vec3(Math.cos(yawR), 0.0, Math.sin(yawR));
                     Vec3 hip = hipPosition(p, pt, bodyYaw);
                     Vec3 open = floatPosition(p, pt, yawS);
                     Vec3 bookPos = hip.add(open.subtract(hip).scale((double)prog));
                     // lit by the world like a real object (a touch brighter so the cover art still reads in the dark)
                     int wl = LevelRenderer.getLightColor(mc.level, BlockPos.containing(bookPos));
                     int light = LightTexture.pack(Math.max(LightTexture.block(wl), 5), Math.max(LightTexture.sky(wl), 2));
                     ps.pushPose();
                     ps.translate(bookPos.x - cam.x, bookPos.y - cam.y, bookPos.z - cam.z);
                     if (!fp) {
                        if (prog < 0.5F) {
                           // closed on the hip, small enough to sit against the leg; it swings as you walk
                           float s = HIP_SCALE * (1.0F + 0.25F * prog);
                           ps.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
                           float sway = (float)Math.sin((double)((float)mc.level.getGameTime() + pt) * 0.12 + (double)p.getId()) * 2.0F
                              + (float)Math.min(p.getDeltaMovement().horizontalDistance() * 40.0, 14.0);
                           ps.mulPose(Axis.ZP.rotationDegrees(sway));
                           ps.mulPose(Axis.XP.rotationDegrees(90.0F));
                           ps.mulPose(Axis.YP.rotationDegrees(prog * 120.0F));
                           ps.scale(s, s, s);
                           if (customModel(book.getItem())) ps.translate(-0.0625, 0.21875, 0.0);
                           else ps.translate(0.0, 0.28125, 0.0);
                           mc.getItemRenderer().renderStatic(book, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, p.getId());
                        } else {
                           // floating open beside you: the solid 3D open-book model, plus the black-flame outline
                           float k = ease((prog - 0.5F) * 2.0F);
                           float kb = k - 1.0F;
                           float back = 1.0F + 2.70158F * kb * kb * kb + 1.70158F * kb * kb;
                           float s = OPEN_SCALE * (0.5F + 0.5F * back);
                           float tilt = 35.0F * k;
                           ps.mulPose(Axis.YP.rotationDegrees(-yawS));
                           ps.mulPose(Axis.YP.rotationDegrees(180.0F));
                           ps.mulPose(Axis.XP.rotationDegrees(-tilt));
                           ps.scale(s, s, s);
                           renderOpen(mc, ps, buf, book, p.getId(), light);
                           float cyc = k > 0.95F ? (float)((mc.level.getGameTime() + (long)p.getId() * 13L) % 50L) + pt : -1.0F;
                           queueOutline(book, bookPos, yawS, tilt, s, k, cyc);
                        }
                     } else if (prog > 0.02F) {
                        // first person: the grimoire comes up into view at the lower left while you cast
                        ps.popPose();
                        ps.pushPose();
                        Vector3f lk = camera.getLookVector(), up = camera.getUpVector(), lf = camera.getLeftVector();
                        float k = ease(prog);
                        double fwdD = 0.62 + 0.24 * k, leftD = 0.44, downD = 0.62 - 0.36 * k;
                        double bob = Math.sin((double)((float)mc.level.getGameTime() + pt) * 0.1) * 0.008;
                        Vec3 fpPos = cam.add(lk.x() * fwdD + lf.x() * leftD - up.x() * (downD + bob),
                                             lk.y() * fwdD + lf.y() * leftD - up.y() * (downD + bob),
                                             lk.z() * fwdD + lf.z() * leftD - up.z() * (downD + bob));
                        float camYaw = camera.getYRot(), tilt = camera.getXRot() + 22.0F;
                        float s = FP_SCALE * (0.55F + 0.45F * k);
                        ps.translate(fpPos.x - cam.x, fpPos.y - cam.y, fpPos.z - cam.z);
                        ps.mulPose(Axis.YP.rotationDegrees(-camYaw + 14.0F));
                        ps.mulPose(Axis.YP.rotationDegrees(180.0F));
                        ps.mulPose(Axis.XP.rotationDegrees(-tilt));
                        ps.scale(s, s, s);
                        renderOpen(mc, ps, buf, book, p.getId(), light);
                        float cyc = k > 0.95F ? (float)((mc.level.getGameTime() + (long)p.getId() * 13L) % 50L) + pt : -1.0F;
                        queueOutline(book, fpPos, camYaw - 14.0F, tilt, s, k, cyc);
                     }

                     ps.popPose();
                     Long start = ANIMS.get(p.getId());
                     if (start != null && prog >= 0.99F) {
                        float t = Mth.clamp(((float)(mc.level.getGameTime() - start) + pt) / 16.0F, 0.0F, 1.0F);
                        boolean off = OFFHAND_ANIMS.contains(p.getId());
                        Vec3 hand = base(p, pt)
                           .add(0.0, (double)p.getEyeHeight() * 0.8, 0.0)
                           .add(fwd.scale(0.55))
                           .add(left.scale(off ? 0.4 : -0.4));
                        // the blade is drawn up out of the open pages (like the anime), then pulled to the hand
                        Vec3 lowStart = open.add(0.0, -0.05, 0.0);
                        Vec3 top = open.add(0.0, 0.75, 0.0);
                        float RISE = 0.45F;
                        Vec3 pos;
                        float scale;
                        float roll;
                        if (t < 0.45F) {
                           float u = ease(t / 0.45F);
                           pos = lowStart.add(top.subtract(lowStart).scale((double)u));
                           scale = 0.15F + 0.85F * u;   // grows out of the book instead of popping in full size
                           roll = 225.0F;
                        } else {
                           float u = (t - 0.45F) / 0.55F;
                           float m = ease(u);
                           Vec3 ctrl = top.add(hand).scale(0.5).add(0.0, 0.35, 0.0).add(fwd.scale(0.35));
                           pos = top.scale((double)((1.0F - m) * (1.0F - m)))
                              .add(ctrl.scale((double)(2.0F * (1.0F - m) * m)))
                              .add(hand.scale((double)(m * m)));
                           scale = 1.0F;
                           roll = 225.0F + (off ? -90.0F : 90.0F) * ease(Mth.clamp(u * 3.0F, 0.0F, 1.0F));
                        }

                        double ang = Math.toRadians((double)(45.0F + roll));
                        double yr = Math.toRadians((double)yaw);
                        Vec3 axis = new Vec3(Math.cos(ang) * Math.cos(yr), Math.sin(ang), Math.cos(ang) * Math.sin(yr));
                        float half = (off ? 1.05F : 1.5F) * scale;
                        float auraA = t < 0.45F ? 0.35F + 0.35F * (t / 0.45F) : 0.7F * (1.0F - 0.6F * (t - 0.45F) / 0.55F);
                        FxRenderer.queueAura(
                           pos.subtract(axis.scale((double)half)),
                           pos.add(axis.scale((double)half)),
                           (off ? 0.16F : 0.22F) * scale,
                           off ? 1 : 0,
                           auraA
                        );
                        ps.pushPose();
                        ps.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
                        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
                        ps.mulPose(Axis.ZP.rotationDegrees(roll));
                        ps.scale(0.9F * scale, 0.9F * scale, 0.9F * scale);
                        ps.translate(-0.582F, -0.582F, 0.0F);
                        mc.getItemRenderer()
                           .renderStatic(off ? twin : sword, ItemDisplayContext.NONE, 15728880, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, p.getId());
                        ps.popPose();
                     }
                  }
               }
            }

            buf.endBatch();
         }
      }
   }
}
