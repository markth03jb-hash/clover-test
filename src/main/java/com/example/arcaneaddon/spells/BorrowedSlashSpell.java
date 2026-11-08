package com.example.arcaneaddon.spells;

import com.example.arcaneaddon.server.AstaFx;
import net.minecraft.server.level.ServerPlayer;

/**
 * Needs the Demon Dweller Sword in your main hand. Plays the dweller_borrowed_sweep animation: the blade is drawn back
 * while the borrowed element gathers on it (you can see which one is coming), then a fast wide sweep releases it.
 */
public class BorrowedSlashSpell extends GrimoireSpell {
    public static final int RELEASE = 9;

    public BorrowedSlashSpell() { super("borrowed_slash", 10, 20f, Need.DWELLER_MAIN); }

    @Override
    protected void perform(ServerPlayer p, float power) {
        String el = AstaFx.randomElement();
        AstaFx.dwellerWindup(p, el, RELEASE);
        AstaFx.later(RELEASE, () -> { if (p.isAlive()) AstaFx.dwellerSlash(p, power, true, el); });
    }
}
