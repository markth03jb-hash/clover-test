package com.example.arcaneaddon.spells;

import com.example.arcaneaddon.server.AstaFx;
import net.minecraft.server.level.ServerPlayer;

public class SkySplitterSpell extends GrimoireSpell {
    public SkySplitterSpell() { super("sky_splitter", 14, 24f, true); }

    @Override
    protected void perform(ServerPlayer p, float power) {
        AstaFx.skySplitter(p, power);
    }
}
