package com.ironspellgrimoire.spells;

import com.ironspellgrimoire.server.AstaFx;
import net.minecraft.server.level.ServerPlayer;

public class ZettenSpell extends GrimoireSpell {
    public ZettenSpell() { super("zetten", 5, 16f, Need.ANY_MAIN); }

    @Override
    protected void perform(ServerPlayer p, float power) {
        AstaFx.zetten(p, power, AstaFx.isDweller(p));
    }
}
