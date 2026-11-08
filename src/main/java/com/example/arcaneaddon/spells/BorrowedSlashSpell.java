package com.example.arcaneaddon.spells;

import com.example.arcaneaddon.server.AstaFx;
import net.minecraft.server.level.ServerPlayer;

/** Needs the Demon Dweller Sword in either hand. */
public class BorrowedSlashSpell extends GrimoireSpell {
    public BorrowedSlashSpell() { super("borrowed_slash", 10, 20f, Need.DWELLER_MAIN); }

    @Override
    protected void perform(ServerPlayer p, float power) {
        AstaFx.dwellerSlash(p, power, true);
    }
}
