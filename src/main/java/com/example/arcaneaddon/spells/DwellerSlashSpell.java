package com.example.arcaneaddon.spells;

import com.example.arcaneaddon.server.AstaFx;
import net.minecraft.server.level.ServerPlayer;

/** Needs the Demon Dweller Sword in either hand. */
public class DwellerSlashSpell extends GrimoireSpell {
    public DwellerSlashSpell() { super("dweller_slash", 6, 18f, Need.DWELLER_ANY); }

    @Override
    protected void perform(ServerPlayer p, float power) {
        AstaFx.dwellerSlash(p, power, false);
    }
}
