package com.ironspellgrimoire.spells;

import com.ironspellgrimoire.server.AstaFx;
import net.minecraft.server.level.ServerPlayer;

public class InstinctGuardSpell extends GrimoireSpell {
    public InstinctGuardSpell() { super("instinct_guard", 15, 0f, false); }

    @Override
    protected void perform(ServerPlayer p, float power) {
        AstaFx.instinct(p);
    }
}
