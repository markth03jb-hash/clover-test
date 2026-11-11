package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;
import com.ironspellgrimoire.server.Quests;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The quest tracker, top right (under the bounty tracker when there is one): the quest, what it asks, and a bar. */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class QuestHud {
    private QuestHud() {}

    private static int type = -1, have, need, item;
    private static boolean done;
    private static long doneAt;

    public static void set(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (p.life == 0) { if (!done) type = -1; return; }
        type = p.sweep;
        done = p.life == 2;
        if (done) { have = need; doneAt = mc.level == null ? 0 : mc.level.getGameTime(); return; }
        have = (int) p.a0; need = (int) p.a1; item = (int) p.h;
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (type < 0 || mc.player == null || mc.level == null || mc.options.hideGui) return;
        if (done && mc.level.getGameTime() - doneAt > 80) { type = -1; done = false; return; }
        GuiGraphics g = e.getGuiGraphics();
        Font f = mc.font;
        int w = 168, x = g.guiWidth() - w - 4, y = BountyHud.shown() ? 70 : 4, h = 40;
        int head = done ? 0xFF60FF80 : 0xFF50C8FF;
        g.fill(x, y, x + w, y + h, 0xA0081018);
        g.renderOutline(x, y, w, h, 0xC060A8D8);
        g.fill(x, y, x + 3, y + h, head);
        g.drawString(f, done ? "QUEST COMPLETE" : "QUEST", x + 8, y + 4, head, false);
        String title = Quests.TITLES[Math.max(0, Math.min(Quests.TITLES.length - 1, type))];
        g.drawString(f, title, x + w - 6 - f.width(title), y + 4, 0xFFFFFFFF, false);
        String what = switch (type) {
            case Quests.MAGES -> "Hooded mages defeated";
            case Quests.MONSTERS -> "Monsters defeated";
            case Quests.PRACTICE -> "Grimoire spells cast";
            case Quests.SUPPLY -> Quests.supplyItem(item).getDescription().getString() + " handed in";
            case Quests.RIFT -> "Close a rift";
            default -> "Bring down a boss";
        };
        g.drawString(f, what, x + 8, y + 16, 0xFFB0C8D8, false);
        int bw = w - 16, filled = need <= 0 ? 0 : (int) (bw * Math.min(1f, have / (float) need));
        g.fill(x + 8, y + 29, x + 8 + bw, y + 34, 0xFF203040);
        g.fill(x + 8, y + 29, x + 8 + filled, y + 34, head);
        String n = have + " / " + need;
        g.drawString(f, n, x + w - 8 - f.width(n), y + 18, 0xFFFFFFFF, false);
    }
}
