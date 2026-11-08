package com.example.arcaneaddon.spells;

import com.example.arcaneaddon.server.AstaFx;
import net.minecraft.server.level.ServerPlayer;

/**
 * Needs the Demon Dweller Sword in either hand. Plays the dweller_rising_cut animation: a low stance with dark
 * energy drawn into the blade, then the rising cut - the slash leaves exactly when the swing does.
 */
public class DwellerSlashSpell extends GrimoireSpell {
    public static final int RELEASE = 7;

    public DwellerSlashSpell() { super("dweller_slash", 6, 18f, Need.DWELLER_ANY); }

    @Override
    protected void perform(ServerPlayer p, float power) {
        AstaFx.dwellerWindup(p, null, RELEASE);
        AstaFx.later(RELEASE, () -> { if (p.isAlive()) AstaFx.dwellerSlash(p, power, false); });
    }
}
