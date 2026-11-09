package com.ironspellgrimoire.spells;

import com.ironspellgrimoire.server.AstaFx;
import net.minecraft.server.level.ServerPlayer;

public class DevilUnionSpell extends GrimoireSpell {
    public DevilUnionSpell() { super("devil_union", 120, 0f, false); }

    @Override
    protected void perform(ServerPlayer p, float power) {
        AstaFx.devilUnion(p);
    }
}
