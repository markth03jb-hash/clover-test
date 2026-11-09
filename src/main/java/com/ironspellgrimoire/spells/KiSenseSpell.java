package com.ironspellgrimoire.spells;

import com.ironspellgrimoire.server.AstaFx;
import net.minecraft.server.level.ServerPlayer;

public class KiSenseSpell extends GrimoireSpell {
    public KiSenseSpell() { super("ki_sense", 30, 0f, false); }

    @Override
    protected void perform(ServerPlayer p, float power) {
        AstaFx.kiSense(p);
    }
}
