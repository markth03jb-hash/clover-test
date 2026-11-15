package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.ShadowPackets;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * The Shadow Army menu (key V): every shadow you've raised - its name, what it was, its level and XP, its rank - and
 * what you can do with it: equip it (it rises and follows you while you hold the Shadow Grimoire, up to your slots),
 * rank it up once it's strong enough, rename it, or release it. Black and blue, like the army.
 */
public class ShadowArmyScreen extends Screen {
    public static final KeyMapping KEY = new KeyMapping("key.iron_spell_grimoire.shadow_army", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.iron_spell_grimoire");
    static final String[] RANKS = {"Soldier", "Elite", "Knight", "Captain", "General", "Sovereign's Hand"};
    static final int[] RANK_COL = {0xFF8FA6C8, 0xFF6FB6FF, 0xFF4FA0FF, 0xFF7FD8FF, 0xFFB8E8FF, 0xFFFFFFFF};
    private static final int ROW = 30, W = 340;

    private List<ShadowPackets.Entry> list = new ArrayList<>();
    private int slots, storage, scroll, renaming = -1;
    private EditBox nameBox;

    ShadowArmyScreen() { super(Component.literal("Shadow Army")); }

    /** The server sent the army: open the menu, or refresh it if it's open. */
    public static void show(ShadowPackets.Open p) {
        Minecraft mc = Minecraft.getInstance();
        ShadowArmyScreen s = mc.screen instanceof ShadowArmyScreen open ? open : new ShadowArmyScreen();
        s.list = p.list; s.slots = p.slots; s.storage = p.storage;
        if (mc.screen != s) mc.setScreen(s);
        else s.rebuild();
    }

    private static void act(int action, int id, String name) { AddonNetwork.CHANNEL.sendToServer(new ShadowPackets.Act(action, id, name)); }

    private int left() { return (width - W) / 2; }
    private int top() { return 46; }
    private int visibleRows() { return Math.max(1, (height - top() - 40) / ROW); }

    @Override protected void init() { rebuild(); }

    private void rebuild() {
        clearWidgets();
        int x0 = left(), y = top();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - visibleRows())));
        for (int i = scroll; i < Math.min(list.size(), scroll + visibleRows()); i++, y += ROW) {
            ShadowPackets.Entry e = list.get(i);
            int bx = x0 + W - 150;
            addRenderableWidget(Button.builder(Component.literal(e.equipped() ? "Unequip" : "Equip"), b -> act(1, e.id(), ""))
                    .bounds(bx, y + 5, 48, 18).build());
            Button rank = Button.builder(Component.literal("Rank up"), b -> act(2, e.id(), "")).bounds(bx + 50, y + 5, 48, 18).build();
            rank.active = e.canRankUp();
            addRenderableWidget(rank);
            addRenderableWidget(Button.builder(Component.literal("Name"), b -> { renaming = e.id(); rebuild(); }).bounds(bx + 100, y + 5, 22, 18).build());
            Button rel = Button.builder(Component.literal("X"), b -> act(4, e.id(), "")).bounds(bx + 124, y + 5, 18, 18).build();
            rel.active = !e.exclusive();
            addRenderableWidget(rel);
        }
        if (renaming >= 0) {   // a box to type its new name in, Enter to keep it
            nameBox = new EditBox(font, x0 + 20, height - 32, 200, 18, Component.literal("Name"));
            nameBox.setMaxLength(24);
            for (ShadowPackets.Entry e : list) if (e.id() == renaming) nameBox.setValue(e.name());
            addRenderableWidget(nameBox);
            setInitialFocus(nameBox);
            addRenderableWidget(Button.builder(Component.literal("Keep"), b -> { act(3, renaming, nameBox.getValue()); renaming = -1; rebuild(); })
                    .bounds(x0 + 226, height - 32, 50, 18).build());
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (renaming >= 0 && nameBox != null && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            act(3, renaming, nameBox.getValue()); renaming = -1; rebuild(); return true;
        }
        if (renaming < 0 && KEY.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        scroll = Math.max(0, Math.min(Math.max(0, list.size() - visibleRows()), scroll - (int) Math.signum(d)));
        rebuild();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x0 = left(), y0 = top();
        g.fill(x0 - 8, 8, x0 + W + 8, height - 8, 0xE00A0C14);   // a dark panel, a blue rim
        g.fill(x0 - 8, 8, x0 + W + 8, 9, 0xFF3C78E0);
        g.fill(x0 - 8, height - 9, x0 + W + 8, height - 8, 0xFF3C78E0);
        int eq = 0;
        for (ShadowPackets.Entry e : list) if (e.equipped() && !e.exclusive()) eq++;
        g.drawCenteredString(font, Component.literal("SHADOW ARMY"), width / 2, 16, 0xFF8CC8FF);
        g.drawCenteredString(font, Component.literal(list.size() + " / " + storage + " raised   -   at your side " + eq + " / " + slots), width / 2, 28, 0xFF7F8EA8);
        if (list.isEmpty()) {
            g.drawCenteredString(font, Component.literal("No shadows yet. Slay something while holding the Shadow Grimoire,"), width / 2, y0 + 20, 0xFF9AA8C0);
            g.drawCenteredString(font, Component.literal("then cast Rise on its remnant before it fades."), width / 2, y0 + 32, 0xFF9AA8C0);
        }
        int y = y0;
        for (int i = scroll; i < Math.min(list.size(), scroll + visibleRows()); i++, y += ROW) {
            ShadowPackets.Entry e = list.get(i);
            int rc = RANK_COL[Math.min(5, e.rank())];
            g.fill(x0, y + 1, x0 + W, y + ROW - 1, e.equipped() ? 0x6A12306A : 0x40101826);
            g.fill(x0, y + 1, x0 + 2, y + ROW - 1, rc);   // a stripe in its rank's colour
            String nm = e.name() + (e.exclusive() ? "  *" : "");
            g.drawString(font, nm, x0 + 7, y + 4, 0xFFE6F0FF, false);
            String sub = "Lv " + e.level() + "  " + RANKS[Math.min(5, e.rank())] + (e.recovering() > 0 ? "  - reforming " + e.recovering() + "s" : "");
            g.drawString(font, sub, x0 + 7, y + 15, rc, false);
            int bw = 70, bx = x0 + W - 150 - bw - 8, by = y + 18;   // its XP toward the next level
            g.fill(bx, by, bx + bw, by + 4, 0xFF161C2A);
            float f = e.xpNext() <= 0 ? 1 : Math.min(1f, e.xp() / (float) e.xpNext());
            g.fill(bx, by, bx + (int) (bw * f), by + 4, 0xFF3C8CFF);
            if (e.canRankUp()) g.drawString(font, "ready", bx + bw - font.width("ready"), y + 5, 0xFF9FE0FF, false);
        }
        if (list.size() > visibleRows()) g.drawString(font, "scroll for more", x0 + W - font.width("scroll for more"), height - 22, 0xFF5A6A88, false);
        super.render(g, mx, my, pt);
        if (renaming >= 0) g.drawString(font, "New name:", x0 + 20, height - 44, 0xFF8CC8FF, false);
    }

    @Override public boolean isPauseScreen() { return false; }

    // ---------------------------------------------------------------------------------------------------- the key
    @Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent e) { e.register(KEY); }
    }

    @Mod.EventBusSubscriber(modid = "iron_spell_grimoire", value = Dist.CLIENT)
    public static final class Ticks {
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (KEY.consumeClick()) if (mc.player != null && mc.screen == null) act(0, 0, "");
        }
    }
}
