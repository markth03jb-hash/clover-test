"""Client/shared-table side of the seven newer grimoires: palettes, auras, wheel themes, HUD colours, impact frames, awakening hook."""
import os, sys
ROOT = os.path.join(os.path.dirname(__file__), '..')
J = os.path.join(ROOT, 'src/main/java/com/ironspellgrimoire')

def rd(p): return open(p, encoding='utf-8').read()
def wr(p, s): open(p, 'w', encoding='utf-8').write(s)

def patch(rel, marker, old, new):
    path = os.path.join(J, rel)
    s = rd(path)
    if marker in s: print('skip', rel, marker); return
    if old not in s: raise SystemExit(f'pattern missing in {rel}: {old[:70]!r}')
    wr(path, s.replace(old, new, 1)); print('patched', rel)

# palette order 22..28: flame spirit, lion, world tree, ripper, earth, beast, cotton
# ---- FxBase BODY (also fills 19-21, which had none) and RIM
patch('client/FxBase.java', '22 flame spirit',
      '      {0.2F, 0.0F, 0.01F}      // 18 pure crimson (Demon Slayer\'s Sky Splitter: red only)\n',
      '''      {0.2F, 0.0F, 0.01F},     // 18 pure crimson (Demon Slayer's Sky Splitter: red only)
      {0.02F, 0.1F, 0.2F},     // 19 water
      {0.45F, 0.47F, 0.55F},   // 20 mirror
      {0.1F, 0.0F, 0.12F},     // 21 void
      {0.25F, 0.04F, 0.0F},    // 22 flame spirit
      {0.22F, 0.12F, 0.0F},    // 23 lion's flame
      {0.03F, 0.12F, 0.04F},   // 24 world tree
      {0.18F, 0.02F, 0.04F},   // 25 ripper
      {0.2F, 0.13F, 0.06F},    // 26 earth
      {0.02F, 0.14F, 0.1F},    // 27 beast
      {0.55F, 0.5F, 0.55F}     // 28 cotton
''')
patch('client/FxBase.java', '22 flame spirit rim',
      '      {0.9F, 0.25F, 0.85F}     // 21 void: magenta-violet cuts\n',
      '''      {0.9F, 0.25F, 0.85F},    // 21 void: magenta-violet cuts
      {1.0F, 0.38F, 0.1F},     // 22 flame spirit rim: vermilion
      {1.0F, 0.72F, 0.18F},    // 23 lion's flame: gold-orange
      {0.45F, 0.95F, 0.38F},   // 24 world tree: living green
      {1.0F, 0.3F, 0.36F},     // 25 ripper: blood-red edge
      {0.86F, 0.64F, 0.36F},   // 26 earth: ochre
      {0.3F, 0.88F, 0.7F},     // 27 beast: sea-green
      {1.0F, 0.78F, 0.9F}      // 28 cotton: candy pink
''')

# ---- PixelFx tables
patch('client/PixelFx.java', '{1.0F, 0.38F, 0.1F}',
      '            {0.3F, 0.7F, 1.0F}, {0.85F, 0.88F, 1.0F}, {0.9F, 0.25F, 0.85F}\n    };\n    /** The colour of the solid',
      '''            {0.3F, 0.7F, 1.0F}, {0.85F, 0.88F, 1.0F}, {0.9F, 0.25F, 0.85F},
            {1.0F, 0.38F, 0.1F}, {1.0F, 0.72F, 0.18F}, {0.45F, 0.95F, 0.38F}, {1.0F, 0.3F, 0.36F}, {0.86F, 0.64F, 0.36F},
            {0.3F, 0.88F, 0.7F}, {1.0F, 0.78F, 0.9F}
    };
    /** The colour of the solid''')
patch('client/PixelFx.java', '{0.12F, 0.02F, 0.01F}',
      '            {0.02F, 0.08F, 0.22F}, {0.55F, 0.58F, 0.7F}, {0.1F, 0.0F, 0.12F}\n    };',
      '''            {0.02F, 0.08F, 0.22F}, {0.55F, 0.58F, 0.7F}, {0.1F, 0.0F, 0.12F},
            {0.12F, 0.02F, 0.01F}, {0.2F, 0.12F, 0.02F}, {0.05F, 0.14F, 0.05F}, {0.08F, 0.0F, 0.02F}, {0.34F, 0.24F, 0.14F},
            {0.04F, 0.16F, 0.12F}, {0.95F, 0.9F, 0.95F}
    };''')
patch('client/PixelFx.java', '17, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28}',
      'BOOK_PAL = {0, 10, 5, 11, 2, 13, 14, 15, 7, 16, 17, 19, 20, 21};',
      'BOOK_PAL = {0, 10, 5, 11, 2, 13, 14, 15, 7, 16, 17, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28};')
patch('client/PixelFx.java', 'case 2, 12, 14, 22, 23',
      '            case 2, 12, 14 -> FLAME;', '            case 2, 12, 14, 22, 23 -> FLAME;\n            case 24 -> WIND;\n            case 26 -> METAL;')

# ---- aura-outline colours by book (11-20 were missing)
patch('client/LimitClient.java', '// Noelle: water',
      '            {0.62F, 0.45F, 1.0F},   // Finral: space violet\n',
      '''            {0.62F, 0.45F, 1.0F},   // Finral: space violet
            {0.3F, 0.7F, 1.0F},     // Noelle: water
            {0.9F, 0.9F, 1.0F},     // Gauche: mirror
            {0.9F, 0.25F, 0.85F},   // Langris: rift
            {1.0F, 0.38F, 0.1F},    // Fuegoleon: vermilion
            {1.0F, 0.72F, 0.18F},   // Leopold: gold-orange
            {0.45F, 0.95F, 0.38F},  // William: green
            {1.0F, 0.3F, 0.36F},    // Jack: blood-red
            {0.86F, 0.64F, 0.36F},  // Sol: ochre
            {0.3F, 0.88F, 0.7F},    // Vetto: sea-green
            {1.0F, 0.78F, 0.9F},    // Charmy: pink
''')

# ---- floating book aura
patch('client/GrimoireFloatRenderer.java', 'FLAME_SPIRIT_GRIMOIRE',
      '      String id = BuiltInRegistries.ITEM.getKey(item).getPath();\n      return switch (id) {',
      '''      if (item == AddonItemRegistry.FLAME_SPIRIT_GRIMOIRE.get()) return new int[]{22, 4};
      if (item == AddonItemRegistry.LION_FLAME_GRIMOIRE.get()) return new int[]{23, 0};
      if (item == AddonItemRegistry.WORLD_TREE_GRIMOIRE.get()) return new int[]{24, 3};
      if (item == AddonItemRegistry.SLASH_GRIMOIRE.get()) return new int[]{25, 2};
      if (item == AddonItemRegistry.EARTH_GRIMOIRE.get()) return new int[]{26, 2};
      if (item == AddonItemRegistry.BEAST_GRIMOIRE.get()) return new int[]{27, 3};
      if (item == AddonItemRegistry.COTTON_GRIMOIRE.get()) return new int[]{28, 4};
      String id = BuiltInRegistries.ITEM.getKey(item).getPath();
      return switch (id) {''')

# ---- mana bar
patch('client/ManaBarHud.java', '0xF0CE7A',
      '            {0xE0B0F0, 0x9A22B0, 0xFF7AF0, 0x14061A, 0xF2C0FF},   // Langris\n    };\n    private static final int[] LEAVES = {5, 3, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3};',
      '''            {0xE0B0F0, 0x9A22B0, 0xFF7AF0, 0x14061A, 0xF2C0FF},   // Langris
            {0xF0CE7A, 0xE0501A, 0xFFB070, 0x140503, 0xFFB890},   // Fuegoleon
            {0xFFD36B, 0xE69010, 0xFFE08A, 0x180C03, 0xFFE0A0},   // Leopold
            {0xE8E2A0, 0x2E9A3A, 0xA0F090, 0x06140B, 0xCFFFC0},   // William
            {0xE8E8F0, 0xB01E34, 0xFF8090, 0x0E0609, 0xFFC0CC},   // Jack
            {0xD8B878, 0x8A6034, 0xD8B070, 0x140E07, 0xEBD0A0},   // Sol
            {0xE6D08A, 0x1E9C7C, 0x80F0D0, 0x061511, 0xBFFFEA},   // Vetto
            {0xFFF0F6, 0xE0709E, 0xFFC4DC, 0x1C0C16, 0xFFD6EA},   // Charmy
    };
    private static final int[] LEAVES = {5, 3, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3};''')

# ---- spell wheel themes
patch('client/GrimoireWheel.java', 'Fuegoleon: vermilion',
      '            new Theme(c(0x0A0410), c(0x35103E), c(0xE0B0F0), c(0xE040D8), c(0x5A1E6E), 3, O_BOLTS, M_NOTCHES, true, 0x0C0612, 0xF0C8FF),\n    };',
      '''            new Theme(c(0x0A0410), c(0x35103E), c(0xE0B0F0), c(0xE040D8), c(0x5A1E6E), 3, O_BOLTS, M_NOTCHES, true, 0x0C0612, 0xF0C8FF),
            // Fuegoleon: vermilion pages, pale gold trim, a flame spirit's tongues round the ring
            new Theme(c(0x140503), c(0x4C1208), c(0xF0CE7A), c(0xFF5A1E), c(0x7A1C08), 3, O_FIRE, M_NOTCHES, true, 0x160604, 0xFFB890),
            // Leopold: scorched amber pages, bright gold trim, a lion's mane of claws
            new Theme(c(0x160A02), c(0x5A2E06), c(0xFFD36B), c(0xFFB02E), c(0x8A4A0A), 3, O_MANE, M_DOTS, true, 0x180C03, 0xFFE0A0),
            // William: deep forest pages, pale gold trim, leaves turning round the ring
            new Theme(c(0x04120A), c(0x14401C), c(0xE8E2A0), c(0x7CE866), c(0x2A7A32), 3, O_WIND, M_CRESCENTS, false, 0x06140B, 0xCFFFC0),
            // Jack: black-wine pages, white trim, a blood-red edge
            new Theme(c(0x0C0508), c(0x3A0A14), c(0xE8E8F0), c(0xFF4458), c(0x5A1220), 3, O_BOLTS, M_NOTCHES, true, 0x0E0609, 0xFFC0CC),
            // Sol: baked-clay pages, sandstone trim, riveted like a slab of stone
            new Theme(c(0x120C06), c(0x3E2A14), c(0xD8B878), c(0xC8964E), c(0x6A4A26), 3, O_RIVETS, M_NOTCHES, true, 0x140E07, 0xEBD0A0),
            // Vetto: wet-moss pages, old-gold trim, claws round the ring
            new Theme(c(0x04130F), c(0x0F4034), c(0xE6D08A), c(0x4DDCB0), c(0x1E7A62), 3, O_MANE, M_DOTS, false, 0x061511, 0xBFFFEA),
            // Charmy: rose-cream pages, white trim, soft drops of cream round the ring
            new Theme(c(0x1A0A14), c(0x5A2A48), c(0xFFF0F6), c(0xFF9CCB), c(0x8A3E6A), 3, O_DROPS, M_DOTS, false, 0x1C0C16, 0xFFD6EA),
    };''')
patch('client/GrimoireWheel.java', 'Fuegoleon: a tongue of flame',
      '''            case 13 -> {   // Langris: a straight cut of violet light across each page, with a crack off it''',
      '''            case 14 -> {   // Fuegoleon: a tongue of flame on each page, a paler one inside it
                tri(bb, m, cx, cy, a0 + w * 0.2, ro - 3, a1 - w * 0.2, ro - 3, am, ro + 5.0 + 3.0 * p, ac[0], ac[1], ac[2], 0.9F * A);
                tri(bb, m, cx, cy, a0 + w * 0.38, ro - 3, a1 - w * 0.38, ro - 3, am, ro + 1.0 + 2.0 * p, t[0], t[1], t[2], 0.85F * A);
            }
            case 15 -> {   // Leopold: three claw marks raked across each page
                for (int k = 0; k < 3; k++) {
                    double o = (k - 1) * w * 0.2;
                    seg(bb, m, cx, cy, am + o - w * 0.12, ro - 4, am + o + w * 0.12, ri + 5, 1.1, ac[0], ac[1], ac[2], (0.75F + 0.2F * p) * A);
                }
            }
            case 16 -> {   // William: a leaf on each page - two curved edges, a midrib, a stalk
                seg(bb, m, cx, cy, am, ri + 4, am - w * 0.22, mid + 2, 1.0, ac[0], ac[1], ac[2], 0.85F * A);
                seg(bb, m, cx, cy, am - w * 0.22, mid + 2, am, ro - 3, 1.0, ac[0], ac[1], ac[2], 0.85F * A);
                seg(bb, m, cx, cy, am, ri + 4, am + w * 0.22, mid + 2, 1.0, ac[0], ac[1], ac[2], 0.85F * A);
                seg(bb, m, cx, cy, am + w * 0.22, mid + 2, am, ro - 3, 1.0, ac[0], ac[1], ac[2], 0.85F * A);
                seg(bb, m, cx, cy, am, ri + 2, am, ro - 3, 0.6, t[0], t[1], t[2], (0.5F + 0.4F * p) * A);
            }
            case 17 -> {   // Jack: two thin cuts across each page, the second shorter, with a red bead where they meet
                seg(bb, m, cx, cy, a0 + w * 0.1, ro - 2, a1 - w * 0.1, ri + 3, 0.9, ac[0], ac[1], ac[2], 0.95F * A);
                seg(bb, m, cx, cy, a0 + w * 0.1, ro - 2, a1 - w * 0.1, ri + 3, 0.35, 1.0F, 1.0F, 1.0F, 0.95F * A);
                seg(bb, m, cx, cy, a0 + w * 0.3, ri + 3, a1 - w * 0.05, mid + 3, 0.6, ac[0], ac[1], ac[2], (0.4F + 0.4F * p) * A);
            }
            case 18 -> {   // Sol: a slab on each page, split by a crack
                ring(bb, m, cx, cy, ri + 4, ro - 4, a0 + w * 0.18, a1 - w * 0.18, t[0], t[1], t[2], 0.55F * A, t[0], t[1], t[2], 0.55F * A, 4);
                seg(bb, m, cx, cy, a0 + w * 0.3, ro - 5, am + w * 0.05, mid, 0.8, ac[0], ac[1], ac[2], 0.8F * A);
                seg(bb, m, cx, cy, am + w * 0.05, mid, a1 - w * 0.28, ri + 6, 0.8, ac[0], ac[1], ac[2], 0.8F * A);
            }
            case 19 -> {   // Vetto: a paw print on each page
                double px = cx + Math.sin(am) * (mid - 1), py = cy - Math.cos(am) * (mid - 1);
                disc(bb, m, px, py, 3.0 + p, ac[0], ac[1], ac[2], 0.9F * A);
                for (int k = -1; k <= 1; k++) {
                    double ta = am + k * w * 0.2, tr = mid + 6 - Math.abs(k) * 1.5;
                    disc(bb, m, cx + Math.sin(ta) * tr, cy - Math.cos(ta) * tr, 1.5, ac[0], ac[1], ac[2], 0.9F * A);
                }
            }
            case 20 -> {   // Charmy: a puff of cotton on each page - three soft balls
                disc(bb, m, cx + Math.sin(am) * mid, cy - Math.cos(am) * mid, 3.0 + p, t[0], t[1], t[2], 0.85F * A);
                disc(bb, m, cx + Math.sin(am - w * 0.18) * (mid + 3), cy - Math.cos(am - w * 0.18) * (mid + 3), 2.0, t[0], t[1], t[2], 0.8F * A);
                disc(bb, m, cx + Math.sin(am + w * 0.18) * (mid + 3), cy - Math.cos(am + w * 0.18) * (mid + 3), 2.0, ac[0], ac[1], ac[2], 0.85F * A);
            }
            case 13 -> {   // Langris: a straight cut of violet light across each page, with a crack off it''')

# ---- impact frames: palette rows and which pattern each reuses
patch('client/ImpactFrames.java', '13 Fuegoleon',
      '            {0xE0D4FF, 0x0A0420, 0x9C78FF, 0xFFE6A0}};  // 12 Finral',
      '''            {0xE0D4FF, 0x0A0420, 0x9C78FF, 0xFFE6A0},   // 12 Finral
            {0xFFD2B0, 0x1E0600, 0xFF5A1E, 0xFFF0D0},   // 13 Fuegoleon: flame tongues
            {0xFFE6A8, 0x241000, 0xFFB02E, 0xFFFFFF},   // 14 Leopold: a lion's flame
            {0xD6FFC8, 0x03200A, 0x62E050, 0xFFF4B0},   // 15 William: spiralling leaves
            {0xFFD0D6, 0x14000A, 0xFF4458, 0xFFFFFF},   // 16 Jack: cuts
            {0xF2DFC0, 0x1C1208, 0xB88A4E, 0xFFFFFF},   // 17 Sol: slabs
            {0xCFFFF0, 0x021C16, 0x3CD4A8, 0xFFE7A0},   // 18 Vetto: fangs
            {0xFFE4F0, 0x2A0E1E, 0xFF8CC6, 0xFFFFFF}};  // 19 Charmy: threads of cream''')
patch('client/ImpactFrames.java', 'case 4, 16 ->', 'case 4 -> {   // Yami', 'case 4, 16 -> {   // Yami / Jack')
patch('client/ImpactFrames.java', 'case 5, 15 ->', 'case 5 -> {   // Yuno', 'case 5, 15 -> {   // Yuno / William')
patch('client/ImpactFrames.java', 'case 6, 18 ->', 'case 6 -> {   // Nozel', 'case 6, 18 -> {   // Nozel / Vetto')
patch('client/ImpactFrames.java', 'case 7, 8, 13, 14 ->', 'case 7, 8 -> {   // Magna', 'case 7, 8, 13, 14 -> {   // Magna / Mereoleona / Fuegoleon / Leopold')
patch('client/ImpactFrames.java', 'case 10, 17 ->', 'case 10 -> {  // Klaus', 'case 10, 17 -> {  // Klaus / Sol')
patch('client/ImpactFrames.java', 'case 11, 19 ->', 'case 11 -> {  // Vanessa', 'case 11, 19 -> {  // Vanessa / Charmy')

# ---- awakening hook + frame list
patch('server/LimitFx.java', 'FRAME_FUEGOLEON',
      'FxSend.FRAME_FINRAL}[Math.max(0, Math.min(13, idx))]',
      'FxSend.FRAME_FINRAL, FxSend.FRAME_FUEGOLEON, FxSend.FRAME_LEOPOLD, FxSend.FRAME_WILLIAM, FxSend.FRAME_JACK, FxSend.FRAME_SOL, FxSend.FRAME_VETTO, FxSend.FRAME_CHARMY}[Math.max(0, Math.min(20, idx))]')
patch('server/LimitFx.java', 'AwakenFx.awaken',
      '            case 8 -> {    // Klaus: steel plates rise',
      '            case 14, 15, 16, 17, 18, 19, 20 -> AwakenFx.awaken(p, idx);\n            case 8 -> {    // Klaus: steel plates rise')
print('done')
