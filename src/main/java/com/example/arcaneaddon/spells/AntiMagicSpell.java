package com.example.arcaneaddon.spells;

import com.example.arcaneaddon.server.AstaFx;
import net.minecraft.server.level.ServerPlayer;

public class AntiMagicSpell extends GrimoireSpell {
    public AntiMagicSpell() { super("anti_magic", 20, 0f, Need.ANY_MAIN); }

    @Override
    protected void perform(ServerPlayer p, float power) {
        AstaFx.antiMagic(p);
    }
}
