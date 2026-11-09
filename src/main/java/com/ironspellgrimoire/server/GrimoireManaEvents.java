package com.ironspellgrimoire.server;

import com.ironspellgrimoire.IronSpellGrimoire;
import com.ironspellgrimoire.item.BlackGrimoireItem;
import io.redspace.ironsspellbooks.api.events.ChangeManaEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Anti-magic wielder. While the Black Grimoire is equipped:
 *  - mana drains continuously, DRAIN_PERCENT of your MAX mana every tick, so a full bar empties in ~1.7 s
 *    whatever your max mana is;
 *  - mana can never go UP (regeneration, potions, etc. are blocked), so nothing can out-pace the drain;
 *  - the grimoire's own spells cost 0 mana, so they still work at 0.
 * Unequip the book and mana regenerates normally again.
 */
@Mod.EventBusSubscriber(modid = IronSpellGrimoire.MODID)
public class GrimoireManaEvents {
    /** Fraction of max mana removed per tick (0.03 = 3%/tick, 60%/second). */
    private static final float DRAIN_PERCENT = 0.03f;

    private static boolean wearsGrimoire(Player p) {
        ItemStack book = Utils.getPlayerSpellbookStack(p);
        return book != null && book.getItem() instanceof BlackGrimoireItem;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp) || !wearsGrimoire(sp)) return;
        MagicData data = MagicData.getPlayerMagicData(sp);
        if (data == null) return;
        float mana = data.getMana();
        if (mana <= 0f) return;
        float max = (float) sp.getAttributeValue(AttributeRegistry.MAX_MANA.get());
        data.setMana(Math.max(0f, mana - Math.max(1f, max * DRAIN_PERCENT)));
    }

    /** Block every mana increase while the grimoire is worn (this is what stops regeneration from overriding the drain). */
    @SubscribeEvent
    public static void onManaChange(ChangeManaEvent e) {
        if (e.getNewMana() > e.getOldMana() && wearsGrimoire(e.getEntity())) {
            e.setNewMana(e.getOldMana());
        }
    }
}
