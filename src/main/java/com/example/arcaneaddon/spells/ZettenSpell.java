package com.example.arcaneaddon.spells;

import com.example.arcaneaddon.server.AstaFx;
import net.minecraft.server.level.ServerPlayer;

public class ZettenSpell extends GrimoireSpell {
    public ZettenSpell() { super("zetten", 5, 16f, true); }

    @Override
    protected void perform(ServerPlayer p, float power) {
        AstaFx.zetten(p, power);
    }
}
