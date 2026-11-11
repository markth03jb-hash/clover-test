package com.ironspellgrimoire.client;

import static com.ironspellgrimoire.client.BookUi.*;

import com.ironspellgrimoire.item.RiftInkItem;
import com.ironspellgrimoire.network.AddonNetwork;
import com.ironspellgrimoire.network.AltarPackets;
import com.ironspellgrimoire.server.ZenonDrops;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * The Grimoire Altar's menu, written into an open book in the equipped grimoire's colours (the same look as the
 * grimoire screen). Left page: receive a grimoire, the Star Forge. Right page: forge a Devil Shard, write a Rift Ink.
 */
public class AltarScreen extends Screen {
    private static final int PW = BW / 2 - 30, CH = 46;
    private AltarPackets.Open data;
    private int ticks;

    private AltarScreen(AltarPackets.Open d) { super(Component.literal("Grimoire Altar")); this.data = d; }

    public static void show(AltarPackets.Open d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AltarScreen s) s.data = d;
        else {
            mc.setScreen(new AltarScreen(d));
            if (mc.player != null) mc.player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.9F);
        }
    }

    @Override public void tick() { ticks++; }
    @Override public boolean isPauseScreen() { return false; }

    private boolean hasBook() { return !data.book.isEmpty(); }
    private int lx() { return x0(width) + 16; }
    private int rx() { return x0(width) + BW / 2 + 14; }
    private int top() { return y0(height) + 14; }
    private int choiceY(int i) { return i < 2 ? top() + 48 + i * (CH + 8) : top() + 14 + (i - 2) * (CH + 8); }
    private int inkX(int r) { return rx() + (r % 2) * (PW / 2 + 2); }
    private int inkY(int r) { return choiceY(3) + 16 + (r / 2) * 16; }
    private void send(int action, int arg) { AddonNetwork.CHANNEL.sendToServer(new AltarPackets.Act(data.pos, action, arg)); }
    private void click() { if (minecraft != null && minecraft.player != null) minecraft.player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.2F); }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            if (in(mx, my, lx(), choiceY(0), PW, CH)) { click(); send(0, 0); onClose(); return true; }
            if (in(mx, my, lx(), choiceY(1), PW, CH) && hasBook()) { click(); send(1, 0); return true; }
            if (in(mx, my, rx(), choiceY(2), PW, CH) && hasBook() && data.shards > 0) { click(); send(2, 0); return true; }
            for (int r = 0; r < 4; r++) {
                if (in(mx, my, inkX(r), inkY(r), PW / 2 - 2, 14) && hasBook() && data.inks[r] > 0) { click(); send(3, r); return true; }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int[] c = colours();
        book(g, font, width, height, c, Component.literal("Grimoire Altar"), "★ " + data.stars + " merit stars");
        int lx = lx(), rx = rx(), t = top();

        // left page: whose grimoire, then what the altar can give
        heading(g, font, "The Altar", lx, PW, t);
        String who = hasBook() ? data.book : "No grimoire equipped";
        text(g, font, who, lx + (PW - Math.min(PW, font.width(who))) / 2, t + 13, PW, hasBook() ? INK : INK_SOFT);
        rule(g, lx, PW, t + 38);
        choice(g, font, lx, choiceY(0), PW, CH, "Receive a Grimoire", "Climb the tower and be chosen.", in(mx, my, lx, choiceY(0), PW, CH), true);
        choice(g, font, lx, choiceY(1), PW, CH, "★ Star Forge", hasBook() ? "Spend merit stars on your grimoire." : "Equip a grimoire first.",
                in(mx, my, lx, choiceY(1), PW, CH), hasBook());

        // right page: what you bring to it
        heading(g, font, "Offerings", rx, PW, t);
        boolean shard = hasBook() && data.shards > 0;
        String sl = !hasBook() ? "Equip a grimoire first." : data.shards <= 0 ? "You carry no Devil Shards."
                : "Carrying " + data.shards + ". Bound: " + data.bookShards + " / " + ZenonDrops.MAX_SHARDS + ".";
        choice(g, font, rx, choiceY(2), PW, CH, "Forge a Devil Shard", sl, in(mx, my, rx, choiceY(2), PW, CH), shard);
        boolean anyInk = data.inks[0] + data.inks[1] + data.inks[2] + data.inks[3] > 0;
        choice(g, font, rx, choiceY(3), PW, CH + 2, "Write a Rift Ink", null, false, hasBook() && anyInk);
        for (int r = 0; r < 4; r++) {
            int x = inkX(r), y = inkY(r), w = PW / 2 - 2;
            boolean can = hasBook() && data.inks[r] > 0, hot = can && in(mx, my, x, y, w, 14);
            if (can) g.fill(x + 3, y + 4, x + 8, y + 9, 0xFF000000 | RiftInkItem.COLORS[r]);   // a drop of the ink
            if (hot) g.fill(x, y, x + w, y + 14, 0x287A1A12);
            if (hot) frame(g, x, y, w, 14, RED_INK);
            String n = RiftInkItem.NAMES[r].replace(" Ink", "") + " " + data.inks[r];
            g.drawString(font, n, x + 11, y + 3, can ? (hot ? RED_INK : INK) : INK_FAINT, false);
        }
        if (!hasBook()) text(g, font, "Equip a grimoire first.", rx + 5, choiceY(3) + CH + 6, PW, INK_SOFT);

        turning(g, width, height, (ticks + pt) / 5F);
        super.render(g, mx, my, pt);
    }
}
