package com.ironspellgrimoire.client;

import com.ironspellgrimoire.entity.BroomEntity;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** On a broom: a small readout above the hotbar - your mana and what flying costs - and the sprint key boosts it. */
@Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
public final class BroomHud {
    private BroomHud() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        BroomEntity.CLIENT_BOOST = mc.player != null && mc.player.getVehicle() instanceof BroomEntity && mc.options.keySprint.isDown();
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !(mc.player.getVehicle() instanceof BroomEntity b)) return;
        GuiGraphics g = e.getGuiGraphics();
        float mana = ManaBarHud.mana(mc), max = (float) Math.max(1, mc.player.getAttributeValue(AttributeRegistry.MAX_MANA.get()));
        double sp = b.getDeltaMovement().length();
        boolean boost = BroomEntity.CLIENT_BOOST && !b.noMana();
        String cost = b.noMana() ? "out of mana - gliding" : boost ? "boost: -5 mana/s" : sp > 0.1 ? "-3 mana/s" : "hover: -1 mana/s";
        String s = "Broom  " + (int) mana + " / " + (int) max + "  (" + cost + ")";
        int w = mc.font.width(s) + 10, x = (g.guiWidth() - w) / 2, y = g.guiHeight() - 72;
        g.fill(x, y, x + w, y + 13, 0x90000000);
        int bw = (int) ((w - 2) * Math.min(1f, mana / max));
        g.fill(x + 1, y + 11, x + 1 + bw, y + 12, boost ? 0xFFFFC040 : 0xFF60A0FF);
        g.drawString(mc.font, s, x + 5, y + 2, b.noMana() ? 0xFFFF8080 : boost ? 0xFFFFE080 : 0xFFE0E8FF, false);
    }
}
