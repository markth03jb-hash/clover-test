# Iron Spell Grimoire (formerly Arcane Addon) — Black Grimoire + Arcane Pulse (Iron's Spells 'n Spellbooks, Forge 1.20.1)

Needs Iron's Spells 'n Spellbooks 3.16.3+ installed alongside it (the book is an Iron's spellbook).

## Get the jar
Option A (GitHub, no setup): push this folder to a GitHub repo -> Actions tab -> "Build jar" -> download artifact.
Option B (local, JDK 17): `gradle wrapper --gradle-version 8.1.1 && ./gradlew build` -> `build/libs/arcane_addon-1.0.0.jar`.
Dependency versions are pinned in gradle.properties to the jars you supplied: Iron's 1.20.1-3.16.3, irons_lib 1.20.1-2.3.0, Curios 5.14.1+1.20.1, GeckoLib 4.8.4, Player Animator 1.0.2-rc1+1.20.
If a Maven download fails, put that jar in `libs/` named `<artifact>-<version>.jar` (flatDir is enabled).
Drop the jar in `mods/` next to Iron's.

## Book art
- textures/item/black_grimoire.png — inventory icon
- textures/item/spell_book_models/black_grimoire_front.png / _back.png — covers (worn open book)
- models/item/black_grimoire_open.json — if a cover looks sideways/upside down or front/back are swapped,
  edit the `rotation` (90/270) or swap `#front`/`#back` on elements 0 and 1 (west faces).
