package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.server.Bounties;
import com.ironspellgrimoire.server.Quests;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The quest tracker, top right (under the bounty tracker when there is one). On the way: how far and which way, with
 * an arrow that turns as you do, and a blue pillar of light standing over the place. There: what's left to defeat.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class QuestHud {
    private QuestHud() {}

    private static int type = -1, state, have, need, flags;
    private static double tx, tz;
    private static long deadline, doneAt;

    public static boolean shown() { return type >= 0; }

    public static void set(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        if (p.life == 0) { if (state != Quests.DONE) type = -1; return; }
        type = p.sweep;
        state = p.life;
        if (state == Quests.DONE) { have = need; doneAt = now; return; }
        have = (int) p.a0; need = (int) p.a1; flags = (int) p.h;
        tx = p.c.x; tz = p.c.z;
        deadline = now + (long) p.r;
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (type < 0 || mc.player == null || mc.level == null || mc.options.hideGui) return;
        long now = mc.level.getGameTime();
        if (state == Quests.DONE && now - doneAt > 80) { type = -1; return; }
        GuiGraphics g = e.getGuiGraphics();
        Font f = mc.font;
        boolean daily = (flags & 16) != 0;
        int w = 168, x = g.guiWidth() - w - 4, y = BountyHud.shown() ? 70 : 4, h = daily && state != Quests.DONE ? 51 : 40;
        int head = state == Quests.DONE ? 0xFF60FF80 : daily ? 0xFFFFC840 : 0xFF50C8FF;
        g.fill(x, y, x + w, y + h, 0xA0081018);
        g.renderOutline(x, y, w, h, daily ? 0xC0E8B84A : 0xC060A8D8);
        g.fill(x, y, x + 3, y + h, head);
        g.drawString(f, state == Quests.DONE ? "QUEST COMPLETE" : daily ? "DAILY SPECIAL" : "QUEST", x + 8, y + 4, head, false);
        if (daily && state != Quests.DONE) g.drawString(f, Quests.modsText(flags & 15), x + 8, y + 40, 0xFFFF9060, false);
        String title = Quests.TITLES[Math.max(0, Math.min(Quests.TITLES.length - 1, type))];
        g.drawString(f, title, x + w - 6 - f.width(title), y + 4, 0xFFFFFFFF, false);
        if (state == Quests.TRAVEL) {
            double dx = tx - mc.player.getX(), dz = tz - mc.player.getZ();
            String line = (type == Quests.BEAST ? "Roaming: " : "") + (int) Math.sqrt(dx * dx + dz * dz) + " m " + Bounties.direction(dx, dz);
            g.drawString(f, line, x + 24, y + 16, 0xFFFFFFFF, false);
            String left = "Time left: " + com.ironspellgrimoire.item.WantedPosterItem.time(Math.max(0, deadline - now));
            g.drawString(f, left, x + 8, y + 28, 0xFFB0C8D8, false);
            double bearing = Math.toDegrees(Math.atan2(-dx, dz));
            float rel = Mth.wrapDegrees((float) bearing - mc.player.getViewYRot(e.getPartialTick()));
            float pulse = 0.7F + 0.3F * (float) Math.sin(now * 0.3);
            int col = 0xFF000000 | ((int) (80 * pulse) << 16) | ((int) (200 * pulse) << 8) | (int) (255 * pulse);
            g.pose().pushPose();
            g.pose().translate(x + 13, y + 19.5, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(rel));
            g.fill(-1, -2, 1, 5, col);
            for (int i = 0; i < 4; i++) g.fill(-i, -5 + i, i + 1, -4 + i, col);
            g.pose().popPose();
            return;
        }
        boolean taken = (flags & 32) != 0;
        String what = switch (type) {
            case Quests.RIFT -> "Reach the rift's relic chamber";
            case Quests.BOSS, Quests.BEAST -> "Bring it down";
            case Quests.PAGE -> taken ? "Hold the ground: " + have + " / " + need + " s" : "Take the page from its guards";
            default -> "Defeat them all";
        };
        g.drawString(f, what, x + 8, y + 16, 0xFFB0C8D8, false);
        int bw = w - 16, filled = need <= 0 ? 0 : (int) (bw * Math.min(1f, have / (float) need));
        g.fill(x + 8, y + 29, x + 8 + bw, y + 34, 0xFF203040);
        g.fill(x + 8, y + 29, x + 8 + filled, y + 34, head);
        if (type == Quests.MAGES || type == Quests.MONSTERS) {
            String n = have + " / " + need;
            g.drawString(f, n, x + w - 8 - f.width(n), y + 16, 0xFFFFFFFF, false);
        }
    }

    /** The blue pillar over the quest's place (drawn from FxRenderer's glow pass). */
    static void drawBeacon(Matrix4f m, Vec3 cam, double now) {
        Minecraft mc = Minecraft.getInstance();
        if (type < 0 || state != Quests.TRAVEL || mc.player == null || mc.level == null) return;
        double dx = tx - cam.x, dz = tz - cam.z, d = Math.sqrt(dx * dx + dz * dz);
        if (d > 480 || d < 6) return;
        double ground = mc.player.getY();
        int bx = Mth.floor(tx), bz = Mth.floor(tz);
        if (mc.level.hasChunk(bx >> 4, bz >> 4)) ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        float w = (float) Math.max(0.5, d * 0.01);
        float a = (float) (0.55 + 0.25 * Math.sin(now * 0.15 + 1.5)) * (float) Mth.clamp((d - 6) / 20.0, 0, 1);
        Vec3 bot = new Vec3(tx, ground, tz), top = new Vec3(tx, ground + 120, tz);
        FxBase.gline(m, cam, bot, top, w, 19, a);
        FxBase.gline(m, cam, bot, bot.add(0, 6, 0), w * 2.2F, 19, a * 0.6F);
    }
}
