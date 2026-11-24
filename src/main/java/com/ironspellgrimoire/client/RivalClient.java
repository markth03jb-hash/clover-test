package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.RivalPackets;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * The rivals on the client: your records against them (for the Codex) and Mira Hollowell's deals - a small ledger
 * page at the right of the screen with her terms, a countdown, and Y to accept / N to refuse.
 */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class RivalClient {
    private RivalClient() {}

    public static final KeyMapping ACCEPT = new KeyMapping("key.iron_spell_grimoire.deal_accept", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.iron_spell_grimoire");
    public static final KeyMapping REFUSE = new KeyMapping("key.iron_spell_grimoire.deal_refuse", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.iron_spell_grimoire");

    public static int[] met = new int[4], wins = new int[4], losses = new int[4];
    private static int deal = -1, ticksLeft, total;
    private static String name = "", terms = "";

    public static void records(RivalPackets.Sync m) { met = m.met; wins = m.wins; losses = m.losses; }

    public static void offer(RivalPackets.Offer m) {
        deal = m.deal;
        name = m.name;
        terms = m.terms;
        total = ticksLeft = m.seconds * 20;
    }

    public static boolean offering() { return deal >= 0 && ticksLeft > 0; }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { deal = -1; return; }
        if (deal >= 0 && --ticksLeft <= 0) deal = -1;
        while (ACCEPT.consumeClick()) if (offering()) answer(true);
        while (REFUSE.consumeClick()) if (offering()) answer(false);
    }

    private static void answer(boolean yes) {
        AddonNetwork.CHANNEL.sendToServer(new RivalPackets.Answer(yes));
        Minecraft.getInstance().player.playSound(SoundEvents.BOOK_PAGE_TURN, 1F, yes ? 1.2F : 0.7F);
        deal = -1;
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        if (!offering()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(e.getGuiGraphics(), mc.font, e.getGuiGraphics().guiWidth(), e.getGuiGraphics().guiHeight());
    }

    /** The ledger page: at the right edge, clear of the crosshair and the hotbar. */
    public static void draw(GuiGraphics g, Font font, int W, int H) {
        int w = Math.min(176, W / 3), x = W - w - 8;
        var lines = font.split(Component.literal(terms), w - 14);
        int h = 44 + lines.size() * 10, y = Math.max(40, H / 2 - h / 2 - 10);
        g.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x50000000);
        g.fill(x, y, x + w, y + h, 0xF0EDE2C4);
        g.fill(x, y, x + w, y + 2, 0xFFB8902C);
        g.fill(x, y + h - 2, x + w, y + h, 0xFFB8902C);
        g.fill(x + 4, y + 2, x + 5, y + h - 2, 0x40A03020);   // the ledger's margin line
        g.drawString(font, Component.literal("MIRA'S OFFER").withStyle(ChatFormatting.BOLD), x + 9, y + 6, 0xFF6A4A10, false);
        g.drawString(font, Component.literal(name).withStyle(ChatFormatting.BOLD), x + 9, y + 17, 0xFF2A2010, false);
        int ty = y + 29;
        for (FormattedCharSequence l : lines) { g.drawString(font, l, x + 9, ty, 0xFF3A3020, false); ty += 10; }
        String keys = "[" + ACCEPT.getTranslatedKeyMessage().getString() + "] Accept    [" + REFUSE.getTranslatedKeyMessage().getString() + "] Refuse";
        g.drawString(font, keys, x + 9, ty + 3, 0xFF2A6A2A, false);
        float k = total <= 0 ? 0 : ticksLeft / (float) total;
        g.fill(x + 2, y + h - 5, x + 2 + (int) ((w - 4) * k), y + h - 3, 0xFFB03020);
    }

    /** The automatic screenshot run (UiShots) shows a sample offer. */
    public static void sample() { offer(new RivalPackets.Offer(2, "Mana Loan", com.ironspellgrimoire.server.MiraAI.TERMS[2], 8)); }

    /** The keys' registration (on the mod bus). */
    @Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent e) { e.register(ACCEPT); e.register(REFUSE); }
    }
}
