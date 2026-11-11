package com.ironspellgrimoire.item;

import com.ironspellgrimoire.server.Rifts;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Rift Ink: the law of the rift you beat, bottled. Right-click a Grimoire Altar with it (grimoire equipped) to write it
 * into your grimoire for good - the rift's curse, turned to your side. One inscription per grimoire; a new one
 * replaces the old. Which ink it is sits in NBT "Rule" (0 Blood, 1 Feather, 2 Echo, 3 Shade, 4 Storm, 5 Frost, 6 Moon,
 * 7 Vital); the effects are in
 * server/RiftRewards.
 */
public class RiftInkItem extends Item {
    public static final String[] NAMES = {"Blood Ink", "Feather Ink", "Echo Ink", "Shade Ink", "Storm Ink", "Frost Ink", "Moon Ink", "Vital Ink", "Thorn Ink", "Gold Ink"};
    public static final String[] TEXT = {
            "Your spells cost a quarter less mana - and a sliver of health each.",
            "No fall damage, and you fall slower.",
            "Your spell hits sometimes echo a second later, for 40%.",
            "Now and then a blow passes through you like shadow.",
            "Your spell hits sometimes arc to a second foe nearby, for 30%.",
            "Your spell hits slow foes by a quarter for two seconds.",
            "Your spells hit 15% harder at night.",
            "Your spell hits heal you for 3% of the damage they deal.",
            "Whatever strikes you in close is hurt for a fifth of the blow.",
            "Foes you defeat sometimes leave an emerald."};
    public static final int[] COLORS = {0xC0202A, 0xBFE8FF, 0x9A5CFF, 0x5A5A70, 0x60D0FF, 0xA0F0FF, 0xD8D0FF, 0x60E070, 0x3A8A30, 0xF0C030};
    /** How many inks there are (the first four are the rift laws, bottled; the rest are found on quests and in elite rooms). */
    public static final int COUNT = NAMES.length;

    public RiftInkItem() { super(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)); }

    public static int rule(ItemStack s) { return s.hasTag() ? Math.max(0, Math.min(COUNT - 1, s.getTag().getInt("Rule"))) : 0; }

    @Override
    public Component getName(ItemStack s) {
        int r = rule(s);
        return Component.literal(NAMES[r]).withStyle(st -> st.withColor(COLORS[r]));
    }

    @Override
    public void appendHoverText(ItemStack s, Level level, List<Component> tip, TooltipFlag flag) {
        int r = rule(s);
        tip.add(Component.literal(r < 4 ? "Bottled from a rift of " + Rifts.RULE_NAMES[r] + "." : "A rare ink - found on quests and in rifts' elite rooms.").withStyle(ChatFormatting.LIGHT_PURPLE));
        tip.add(Component.literal(TEXT[r]).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Use on a Grimoire Altar to write it into your grimoire.").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
