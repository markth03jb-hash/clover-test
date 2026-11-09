package com.ironspellgrimoire.spells;

import com.ironspellgrimoire.server.AstaFx;
import net.minecraft.server.level.ServerPlayer;

public class SkySplitterSpell extends GrimoireSpell {
    public SkySplitterSpell() { super("sky_splitter", 14, 24f, Need.ANY_MAIN); }

    @Override
    protected void perform(ServerPlayer p, float power) {
        AstaFx.skySplitter(p, power, AstaFx.isDweller(p));
    }
}
