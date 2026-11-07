package com.example.arcaneaddon.registry;

import com.example.arcaneaddon.ArcaneAddon;
import com.example.arcaneaddon.spells.*;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class AddonSpellRegistry {
    // Spells live in Iron's own registry, keyed by SpellRegistry.SPELL_REGISTRY_KEY
    public static final DeferredRegister<AbstractSpell> SPELLS =
            DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, ArcaneAddon.MODID);

    private static RegistryObject<AbstractSpell> registerSpell(AbstractSpell spell) {
        return SPELLS.register(spell.getSpellName(), () -> spell);
    }

    public static final RegistryObject<AbstractSpell> ARCANE_PULSE = registerSpell(new ArcanePulseSpell());
    public static final RegistryObject<AbstractSpell> SUMMON_GREATSWORD = registerSpell(new SummonGreatswordSpell());
    public static final RegistryObject<AbstractSpell> PIERCING_DASH = registerSpell(new PiercingDashSpell());
    public static final RegistryObject<AbstractSpell> ZETTEN = registerSpell(new ZettenSpell());
    public static final RegistryObject<AbstractSpell> SKY_SPLITTER = registerSpell(new SkySplitterSpell());
    public static final RegistryObject<AbstractSpell> ANTI_MAGIC = registerSpell(new AntiMagicSpell());
    public static final RegistryObject<AbstractSpell> KI_SENSE = registerSpell(new KiSenseSpell());
    public static final RegistryObject<AbstractSpell> INSTINCT_GUARD = registerSpell(new InstinctGuardSpell());
    public static final RegistryObject<AbstractSpell> DWELLER_SLASH = registerSpell(new DwellerSlashSpell());
    public static final RegistryObject<AbstractSpell> BORROWED_SLASH = registerSpell(new BorrowedSlashSpell());
    public static final RegistryObject<AbstractSpell> DEVIL_UNION = registerSpell(new DevilUnionSpell());
}
