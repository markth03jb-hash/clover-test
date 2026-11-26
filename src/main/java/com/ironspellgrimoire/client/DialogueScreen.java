package com.ironspellgrimoire.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/**
 * A scene of ASHFALL: one line at a time across the bottom of the screen, the speaker's face beside it (from their
 * skin), the words writing themselves out. Click (or Space) for the next. A scene that ends in a choice is a vote -
 * each of the party picks, and the screen waits for the rest and then shows what the party chose.
 */
public class DialogueScreen extends Screen {
    final String id;
    final List<String[]> lines = new ArrayList<>();
    final List<String> choices = new ArrayList<>();
    int line, shown, votes, of = 1, result = -1, resultT, picked = -1;
    String resultText = "";
    long openedAt;
    float chars;

    record Who(String name, String skin, int col) {}
    static Who who(String s) {
        return switch (s) {
            case "corvin" -> new Who("Corvin Hale", "story_corvin", 0xFF7FD4FF);
            case "ilsa" -> new Who("Ilsa Marr", "story_ilsa", 0xFFFFB060);
            case "sallow" -> new Who("Father Sallow", "story_sallow", 0xFFE05050);
            case "penitent" -> new Who("Penitent", "story_penitent", 0xFFB08060);
            case "tamsin" -> new Who("Tamsin Reed", "story_tamsin", 0xFF80D070);
            case "vey" -> new Who("Magistrate Vey", "story_vey", 0xFFE8C060);
            case "warden" -> new Who("Warden", "story_warden", 0xFFC8A050);
            case "maren" -> new Who("Reeve Maren", "story_maren", 0xFFE07060);
            case "sedge" -> new Who("Old Sedge", "story_sedge", 0xFF90C070);
            case "mother" -> new Who("The Mother Below", "story_mother", 0xFF50D0C8);
            default -> new Who("", null, 0xFFB8B0A8);
        };
    }

    public DialogueScreen(CompoundTag t) {
        super(Component.literal("ASHFALL"));
        this.id = t.getString("id");
        for (Tag x : t.getList("lines", Tag.TAG_COMPOUND)) lines.add(new String[]{((CompoundTag) x).getString("s"), ((CompoundTag) x).getString("t")});
        for (Tag x : t.getList("choices", Tag.TAG_STRING)) choices.add(x.getAsString());
        of = Math.max(1, t.getInt("of"));
        if (lines.isEmpty()) lines.add(new String[]{"narrator", "..."});
    }

    void update(CompoundTag t) {
        if (t.contains("votes")) { votes = t.getInt("votes"); of = Math.max(1, t.getInt("of")); }
        if (t.contains("result")) { result = t.getInt("result"); resultText = t.getString("text"); resultT = 50; }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return choices.isEmpty() || result >= 0; }
    @Override public void onClose() {
        if (choices.isEmpty()) done();
        super.onClose();
    }

    boolean sent;
    void done() {
        if (sent) return;
        sent = true;
        CompoundTag t = new CompoundTag(); t.putString("a", "done"); t.putString("id", id);
        StoryClient.act(t);
    }

    boolean atEnd() { return line >= lines.size() - 1 && full(); }
    boolean full() { return chars >= lines.get(line)[1].length(); }

    void advance() {
        if (result >= 0) { Minecraft.getInstance().setScreen(null); return; }
        if (!full()) { chars = lines.get(line)[1].length(); return; }
        if (line < lines.size() - 1) { line++; chars = 0; return; }
        if (choices.isEmpty()) { done(); Minecraft.getInstance().setScreen(null); }
    }

    @Override public void tick() {
        chars = Math.min(lines.get(line)[1].length(), chars + 2.2f);
        if (result >= 0 && --resultT <= 0) Minecraft.getInstance().setScreen(null);
    }

    // ---- layout
    int bw() { return Math.min(380, width - 20); }
    int bx() { return (width - bw()) / 2; }
    int by() { return height - 136; }   // above the hotbar, the mana bar and the spell bar
    int choiceY(int i) { return by() - 22 - (choices.size() - i) * 20; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, height - 130, width, height, 0x00000000, 0xC0000000);   // the bottom darkens, the world stays in view
        String[] l = lines.get(line);
        Who w = who(l[0]);
        int x = bx(), y = by(), bw = bw(), bh = 72;
        g.fill(x, y, x + bw, y + bh, 0xF4120C0A);
        BookUi.frame(g, x, y, bw, bh, 0xFFB0602A);
        g.fill(x, y, x + bw, y + 1, 0xFFFF9A3C);
        int tx = x + 10;
        if (w.skin() != null) {   // their face, from the skin (the head's front, and the hat layer over it)
            ResourceLocation tex = ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "textures/entity/" + w.skin() + ".png");
            g.fill(x + 7, y + 9, x + 61, y + 63, 0xFF000000);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.blit(tex, x + 8, y + 10, 52, 52, 8, 8, 8, 8, 64, 64);
            g.blit(tex, x + 8, y + 10, 52, 52, 40, 8, 8, 8, 64, 64);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            BookUi.frame(g, x + 7, y + 9, 54, 54, w.col());
            tx = x + 70;
            g.drawString(font, Component.literal(w.name()).withStyle(ChatFormatting.BOLD), tx, y + 8, w.col(), true);
        }
        String text = l[1].substring(0, Math.min(l[1].length(), (int) chars));
        Component c = w.skin() == null ? Component.literal(text).withStyle(ChatFormatting.ITALIC) : Component.literal(text);
        int ty = y + (w.skin() == null ? 12 : 22);
        for (FormattedCharSequence s : font.split(c, bw - (tx - x) - 10)) { g.drawString(font, s, tx, ty, w.skin() == null ? 0xFFC8C0B8 : 0xFFF0E8E0, false); ty += 10; }
        String step = (line + 1) + "/" + lines.size();
        g.drawString(font, step, x + bw - 6 - font.width(step), y + bh - 11, 0xFF806050, false);
        if (!atEnd() || choices.isEmpty()) {
            if (full() && (System.currentTimeMillis() / 400) % 2 == 0) g.drawString(font, "click", x + bw - 40 - font.width(step), y + bh - 11, 0xFFFF9A3C, false);
        }
        if (atEnd() && !choices.isEmpty()) {
            for (int i = 0; i < choices.size(); i++) {
                int cy = choiceY(i), cw = 200, cx = (width - cw) / 2;
                boolean hot = picked < 0 && result < 0 && mx >= cx && mx < cx + cw && my >= cy && my < cy + 16;
                boolean mine = picked == i, won = result == i;
                g.fill(cx, cy, cx + cw, cy + 16, won ? 0xE0603010 : mine ? 0xE0402010 : hot ? 0xE0301A10 : 0xD0140C08);
                BookUi.frame(g, cx, cy, cw, 16, won || mine ? 0xFFFF9A3C : hot ? 0xFFE0A060 : 0xFF805030);
                String s = choices.get(i);
                g.drawString(font, s, cx + (cw - font.width(s)) / 2, cy + 4, picked >= 0 && !mine && result < 0 ? 0xFF907060 : 0xFFFFFFFF, false);
            }
            String status = result >= 0 ? "The party chose: " + resultText : picked >= 0 ? (of > 1 ? "Waiting for the party (" + votes + "/" + of + ")" : "...") : of > 1 ? "Your party votes - the majority decides, the leader breaks a tie" : "Choose";
            g.drawCenteredString(font, status, width / 2, choiceY(0) - 13, result >= 0 ? 0xFFFFD27A : 0xFFC8B8A8);
        }
        super.render(g, mx, my, pt);
    }

    @Override public boolean mouseClicked(double mx, double my, int b) {
        if (atEnd() && !choices.isEmpty() && result < 0) {
            for (int i = 0; i < choices.size(); i++) {
                int cy = choiceY(i), cw = 200, cx = (width - cw) / 2;
                if (picked < 0 && mx >= cx && mx < cx + cw && my >= cy && my < cy + 16) {
                    picked = i;
                    CompoundTag t = new CompoundTag(); t.putString("a", "vote"); t.putString("id", id); t.putInt("choice", i);
                    StoryClient.act(t);
                    return true;
                }
            }
            return true;
        }
        advance();
        return true;
    }

    @Override public boolean keyPressed(int key, int sc, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) { if (!(atEnd() && !choices.isEmpty() && result < 0)) advance(); return true; }
        return super.keyPressed(key, sc, mods);
    }

    /** For the screenshot run: jump to a line, or to the vote. */
    public void showLine(int i) { line = Math.max(0, Math.min(lines.size() - 1, i)); chars = lines.get(line)[1].length(); }
}
