"""Wires Julius Novachrono's Time Grimoire (book index 21) into the mod. Idempotent: every insert is skipped once present."""
import json, os
ROOT = os.path.join(os.path.dirname(__file__), '..')
J = os.path.join(ROOT, 'src/main/java/com/ironspellgrimoire')
A = os.path.join(ROOT, 'src/main/resources/assets/iron_spell_grimoire')
D = os.path.join(ROOT, 'src/main/resources/data')
def rd(p): return open(p, encoding='utf-8').read()
def wr(p, s): open(p, 'w', encoding='utf-8').write(s)
def patch(rel, marker, old, new):
    path = os.path.join(J, rel); s = rd(path)
    if marker in s: print('skip', rel, marker); return
    if old not in s: raise SystemExit(f'pattern missing in {rel}: {old[:80]!r}')
    wr(path, s.replace(old, new, 1)); print('patched', rel)

# id, name, cd, mana, power, anim, method, level, guide
SPELLS = [
 ('clock_hand_bolt', 'Clock Hand Bolt', 3, 20, 9, 'julius_hand_bolt', 'clockHandBolt', 1,
  'Flick a gold clock hand straight ahead: it strikes the first foe and slows it. With 3 stored seconds, two hands fly.'),
 ('time_stop_point', 'Time Stop Point', 14, 45, 6, 'julius_stop_point', 'timeStopPoint', 1,
  'A cage of pages snaps shut round the foe you aim at: it is frozen for 1.5 s. 2 stored seconds add one more.'),
 ('time_skip', 'Time Skip', 6, 25, 0, 'julius_time_skip', 'timeSkip', 1,
  'Skip a few seconds: you are 8 blocks ahead, and your golden silhouette is left standing where you were.'),
 ('time_recovery', 'Time Recovery', 35, 80, 0, 'julius_time_recovery', 'timeRecovery', 2,
  'Your body rewinds to how it was 4 seconds ago. With 5 stored seconds, to your best moment of the last 6.'),
 ('chrono_acceleration', 'Chrono Acceleration', 28, 55, 0, 'julius_acceleration', 'chronoAcceleration', 2,
  'Your own time runs fast for 6 s: you move and strike faster, and every cooldown runs twice as fast.'),
 ('ticking_snare', 'Ticking Snare', 12, 40, 10, 'julius_ticking_snare', 'tickingSnare', 2,
  'Lay a clock on the ground. It ticks three times; whoever is still standing on it then is frozen for 2 s.'),
 ('time_theft', 'Time Theft', 16, 55, 10, 'julius_time_theft', 'timeTheft', 3,
  'Gold threads pull the time out of everything ahead of you: they are slowed, and you store 2 seconds for each.'),
 ('hourglass_barrage', 'Hourglass Barrage', 18, 65, 7, 'julius_hourglass_barrage', 'hourglassBarrage', 3,
  'A clock opens over your target and its hands rain down, one every tick. With 4 stored seconds, half as many again.'),
 ('rewind_ward', 'Rewind Ward', 24, 50, 0, 'julius_rewind_ward', 'rewindWard', 3,
  'For 3 s, half of every hurt you take is written down - and then rewound back into you.'),
 ('echo_of_the_past', 'Echo of the Past', 20, 45, 0, 'julius_echo', 'echoOfThePast', 4,
  'A gold afterimage of you stays behind and, 2 seconds later, recasts your last spell from where it stands, at half power.'),
 ('chronostasis_field', 'Chronostasis Field', 60, 100, 6, 'julius_chronostasis', 'chronostasisField', 4,
  'Mode: a column of pages surrounds you for 15 s. Inside, foes crawl, arrows hang in the air and your cooldowns run twice as fast.'),
 ('zenith_hour', 'Zenith Hour', 90, 150, 32, 'julius_zenith_hour', 'zenithHour', 5,
  'A vast column of pages bursts up and a great clock opens in the sky: time stops for every foe inside for 4 s. Your blows hang in the air and land all at once when it starts again.'),
]

# ---------------------------------------------------------------- spells
reg = '''
    // ------------------------------------------------------------------ Time Grimoire (Julius Novachrono)
    private static com.ironspellgrimoire.spells.BookSpell time(String id, int cd, int mana, float power, String anim, com.ironspellgrimoire.spells.BookSpell.Effect fx) {
        return new com.ironspellgrimoire.spells.BookSpell(id, io.redspace.ironsspellbooks.api.registry.SchoolRegistry.ENDER_RESOURCE,
                com.ironspellgrimoire.item.TimeGrimoireItem.class, "ui.iron_spell_grimoire.time_grimoire_only", cd, mana, power,
                com.ironspellgrimoire.spells.BookSpell.anim(anim), fx);
    }
'''
for (sid, name, cd, mana, pw, anim, meth, lvl, guide) in SPELLS:
    reg += f'    public static final RegistryObject<AbstractSpell> {sid.upper()} = registerSpell(time("{sid}", {cd}, {mana}, {pw}, "iron_spell_grimoire:{anim}", com.ironspellgrimoire.server.TimeFx::{meth}));\n'
reg += '\n    /** The twelve Time Grimoire spells, in the order they fill the book. */\n    public static final List<RegistryObject<AbstractSpell>> TIME_SPELLS = List.of(\n            ' + ', '.join(s[0].upper() for s in SPELLS) + ');\n'
patch('registry/AddonSpellRegistry.java', 'TIME_SPELLS', '\n    private static RegistryObject<AbstractSpell> registerSpell(', reg + '\n    private static RegistryObject<AbstractSpell> registerSpell(')

# ---------------------------------------------------------------- item
patch('registry/AddonItemRegistry.java', 'TIME_GRIMOIRE', '    public static final RegistryObject<CreativeModeTab> TAB',
      '    // ------------------------------------------------------------------ Julius Novachrono: Time Grimoire\n    public static final RegistryObject<Item> TIME_GRIMOIRE = ITEMS.register("time_grimoire", com.ironspellgrimoire.item.TimeGrimoireItem::new);\n\n    public static final RegistryObject<CreativeModeTab> TAB')
patch('registry/AddonItemRegistry.java', 'COTTON_GRIMOIRE, TIME_GRIMOIRE', 'BEAST_GRIMOIRE, COTTON_GRIMOIRE)', 'BEAST_GRIMOIRE, COTTON_GRIMOIRE, TIME_GRIMOIRE)')
patch('item/ThemedGrimoireItem.java', 'int slots,',
      '''    protected ThemedGrimoireItem(String key, ChatFormatting color, Supplier<List<RegistryObject<AbstractSpell>>> spells,
                                 Supplier<Attribute> school, double mana, double regen, double power, double armor) {
        super(8, SpellRarity.LEGENDARY, modifiers(key, school.get(), mana, regen, power, armor));''',
      '''    protected ThemedGrimoireItem(String key, ChatFormatting color, Supplier<List<RegistryObject<AbstractSpell>>> spells,
                                 Supplier<Attribute> school, double mana, double regen, double power, double armor) {
        this(8, key, color, spells, school, mana, regen, power, armor);
    }

    /** A grimoire with {@code slots} pages (Julius's holds twelve). */
    protected ThemedGrimoireItem(int slots, String key, ChatFormatting color, Supplier<List<RegistryObject<AbstractSpell>>> spells,
                                 Supplier<Attribute> school, double mana, double regen, double power, double armor) {
        super(slots, SpellRarity.LEGENDARY, modifiers(key, school.get(), mana, regen, power, armor));''')
wr(os.path.join(J, 'item/TimeGrimoireItem.java'), '''package com.ironspellgrimoire.item;

import com.ironspellgrimoire.registry.AddonSpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;

/** Julius Novachrono's Time Grimoire: twelve pages of Time Magic ({@code AddonSpellRegistry.TIME_SPELLS}). */
public class TimeGrimoireItem extends ThemedGrimoireItem {
    public TimeGrimoireItem() {
        super(12, "time_grimoire", ChatFormatting.GOLD, () -> AddonSpellRegistry.TIME_SPELLS, AttributeRegistry.ENDER_SPELL_POWER::get, 1200, 0.6, 0.2, 2);
    }
}
''')

# ---------------------------------------------------------------- mastery
ents = [f'"{s[0]}", {s[7]}' for s in SPELLS]
mast = '        book(AddonItemRegistry.TIME_GRIMOIRE::get, Fx.GOLD,\n' + ',\n'.join('                ' + ', '.join(ents[i:i + 3]) for i in range(0, 12, 3)) + ');\n'
patch('server/GrimoireMastery.java', 'TIME_GRIMOIRE', '                "nean_armor", 4, "grand_banquet", 5);\n', '                "nean_armor", 4, "grand_banquet", 5);\n' + mast)

# ---------------------------------------------------------------- mode
patch('network/ModeIds.java', '"chronostasis_field"', '"beast_instinct", "nean_armor"', '"beast_instinct", "nean_armor", "chronostasis_field"')
patch('client/ModeHudOverlay.java', '"chronostasis_field"', '            case "nean_armor" -> 0xFFFFB4DC;', '            case "nean_armor" -> 0xFFFFB4DC;\n            case "chronostasis_field" -> 0xFFFFD36B;')

# ---------------------------------------------------------------- combos (they boost the second spell; TimeFx reads it)
patch('server/ComboChain.java', 'Hands of the Hour', '"the cake bursts in a cloud of cream")',
      '"the cake bursts in a cloud of cream"),\n'
      '            new Combo("clock_hand_bolt", "time_stop_point", "Hands of the Hour", Kind.BOOST, 8, FxSend.FRAME_JULIUS, "the frozen target shatters out of the stopped moment"),\n'
      '            new Combo("time_theft", "chrono_acceleration", "Borrowed Time", Kind.BOOST, 8, FxSend.FRAME_JULIUS, "the stolen seconds come off your cooldowns"),\n'
      '            new Combo("time_skip", "time_stop_point", "Between Seconds", Kind.BOOST, 8, FxSend.FRAME_JULIUS, "the freeze lasts twice as long")')

# ---------------------------------------------------------------- awakening, limit pose
patch('server/LimitFx.java', 'FRAME_CHARMY, FxSend.FRAME_JULIUS', 'FxSend.FRAME_CHARMY}[Math.max(0, Math.min(20, idx))]', 'FxSend.FRAME_CHARMY, FxSend.FRAME_JULIUS}[Math.max(0, Math.min(21, idx))]')
patch('server/LimitFx.java', 'TimeFx.awaken', '            case 14, 15, 16, 17, 18, 19, 20 -> AwakenFx.awaken(p, idx);', '            case 14, 15, 16, 17, 18, 19, 20 -> AwakenFx.awaken(p, idx);\n            case 21 -> TimeFx.awaken(p);')
patch('server/AnimSend.java', 'TIME_AWAKEN', '    public static final int EQUINOX = 15;', '    public static final int EQUINOX = 15;\n    /** Julius: arms crossed while rising, then thrown open (limit_time). */\n    public static final int TIME_AWAKEN = 16;')
patch('client/ArcaneAnimations.java', 'limit_time', '"dark_equinox"),         // 15: Yami\'s Equinox',
      '"dark_equinox"),         // 15: Yami\'s Equinox\n            ResourceLocation.fromNamespaceAndPath("iron_spell_grimoire", "limit_time"),           // 16: Julius\'s awakening')
patch('server/LimitBreak.java', 'TIME_AWAKEN', 'AnimSend.play(p, AnimSend.LIMIT_FIRST + (idx <= 8 ? idx : 1));',
      'AnimSend.play(p, idx == 21 ? AnimSend.TIME_AWAKEN : AnimSend.LIMIT_FIRST + (idx <= 8 ? idx : 1));')

# ---------------------------------------------------------------- client tables (book index 21)
patch('client/LimitClient.java', '// Julius', '            {1.0F, 0.78F, 0.9F},    // Charmy: pink\n', '            {1.0F, 0.78F, 0.9F},    // Charmy: pink\n            {1.0F, 0.82F, 0.36F},   // Julius: clock gold\n')
patch('client/ManaBarHud.java', '// Julius', '            {0xFFF0F6, 0xE0709E, 0xFFC4DC, 0x1C0C16, 0xFFD6EA},   // Charmy\n',
      '            {0xFFF0F6, 0xE0709E, 0xFFC4DC, 0x1C0C16, 0xFFD6EA},   // Charmy\n            {0xF4E6B8, 0xD9A63A, 0xFFE8A0, 0x070A1C, 0xFFF0C8},   // Julius\n')
patch('client/ManaBarHud.java', 'LEAVES = {5, 3, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3}', 'LEAVES = {5, 3, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3};', 'LEAVES = {5, 3, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3};')
patch('client/PixelFx.java', '26, 27, 28, 8}', 'BOOK_PAL = {0, 10, 5, 11, 2, 13, 14, 15, 7, 16, 17, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28};',
      'BOOK_PAL = {0, 10, 5, 11, 2, 13, 14, 15, 7, 16, 17, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 8};')
patch('client/GrimoireFloatRenderer.java', 'TIME_GRIMOIRE.get()) return', '      if (item == AddonItemRegistry.COTTON_GRIMOIRE.get()) return new int[]{28, 4};',
      '      if (item == AddonItemRegistry.COTTON_GRIMOIRE.get()) return new int[]{28, 4};\n      if (item == AddonItemRegistry.TIME_GRIMOIRE.get()) return new int[]{8, 3};')
patch('client/GrimoireWheel.java', 'Julius: midnight', '            new Theme(c(0x1A0A14), c(0x5A2A48), c(0xFFF0F6), c(0xFF9CCB), c(0x8A3E6A), 3, O_DROPS, M_DOTS, false, 0x1C0C16, 0xFFD6EA),\n    };',
      '            new Theme(c(0x1A0A14), c(0x5A2A48), c(0xFFF0F6), c(0xFF9CCB), c(0x8A3E6A), 3, O_DROPS, M_DOTS, false, 0x1C0C16, 0xFFD6EA),\n'
      '            // Julius: midnight-navy pages, bright gold trim, a little clock on every page\n'
      '            new Theme(c(0x070A1C), c(0x1A2450), c(0xF4E6B8), c(0xFFC844), c(0x2E3A78), 3, O_RIVETS, M_NOTCHES, true, 0x080B1E, 0xFFF0C8),\n    };')
patch('client/GrimoireWheel.java', 'case 21 -> {', '            case 20 -> {   // Charmy: a puff of cotton on each page - three soft balls',
      '''            case 21 -> {   // Julius: a small clock on each page - a gold ring, two hands
                double px = cx + Math.sin(am) * mid, py = cy - Math.cos(am) * mid, rr = 4.2 + p;
                float[] in = th.pageIn;
                disc(bb, m, px, py, rr, ac[0], ac[1], ac[2], 0.9F * A);
                disc(bb, m, px, py, rr - 1.0, in[0], in[1], in[2], 0.95F * A);
                double hh = time * 0.8 + am * 3, mm = time * 2.4 + am * 7;
                double hx = px + Math.sin(hh) * rr * 0.5, hy = py - Math.cos(hh) * rr * 0.5, mx = px + Math.sin(mm) * rr * 0.8, my = py - Math.cos(mm) * rr * 0.8;
                double pa = Math.atan2(px - cx, -(py - cy)), pr = Math.hypot(px - cx, py - cy);
                seg(bb, m, cx, cy, pa, pr, Math.atan2(hx - cx, -(hy - cy)), Math.hypot(hx - cx, hy - cy), 0.9, t[0], t[1], t[2], 0.95F * A);
                seg(bb, m, cx, cy, pa, pr, Math.atan2(mx - cx, -(my - cy)), Math.hypot(mx - cx, my - cy), 0.6, ac[0], ac[1], ac[2], 0.95F * A);
                disc(bb, m, px, py, 0.8, t[0], t[1], t[2], A);
            }
            case 20 -> {   // Charmy: a puff of cotton on each page - three soft balls''')

# ---------------------------------------------------------------- impact frame 20: pages spiralling in on the hit, a clock ring round it
patch('client/ImpactFrames.java', '20 Julius', '            {0xFFE4F0, 0x2A0E1E, 0xFF8CC6, 0xFFFFFF}};  // 19 Charmy: threads of cream',
      '            {0xFFE4F0, 0x2A0E1E, 0xFF8CC6, 0xFFFFFF},   // 19 Charmy: threads of cream\n            {0xFFF4D8, 0x070A1C, 0xE8D9A8, 0xFFC844}};  // 20 Julius: pages spiralling in, a clock round the hit')
patch('client/ImpactFrames.java', 'case 20 -> {  // Julius', '            case 12 -> {  // Finral: rings of space closing in on the hit, a gold rim on the innermost',
      '''            case 20 -> {  // Julius: page edges spiralling in on the hit (FWIP), a clock ring round it, two hands stopped
                for (int i = 0; i < 64; i++) {
                    double a0 = i * Math.PI * 2 / 64 + r.nextFloat() * 0.05;
                    float px = fx, py = fy;
                    for (int q = 1; q <= 6; q++) {
                        float fq = q / 6F, rr = reach * (1F - fq * 0.86F * rush);
                        double a = a0 + 1.6 * Math.pow(fq, 0.9);
                        float nx = fx + (float) Math.cos(a) * rr, ny = fy + (float) Math.sin(a) * rr;
                        if (q > 1) taper(bb, m, px, py, (1F - fq) * 3.2F + 0.4F, nx, ny, (1F - fq) * 2.6F + 0.3F, i % 9 == 0 ? acc : line, la);
                        px = nx; py = ny;
                    }
                }
                float cr = Math.min(W, H) * 0.2F;
                for (int j = 0; j < 48; j++) {
                    double a0 = j * Math.PI * 2 / 48, a1 = (j + 1) * Math.PI * 2 / 48;
                    taper(bb, m, fx + (float) Math.cos(a0) * cr, fy + (float) Math.sin(a0) * cr, 3F, fx + (float) Math.cos(a1) * cr, fy + (float) Math.sin(a1) * cr, 3F, acc, la);
                }
                for (int j = 0; j < 12; j++) {
                    double a = j * Math.PI / 6;
                    taper(bb, m, fx + (float) Math.cos(a) * cr * 0.82F, fy + (float) Math.sin(a) * cr * 0.82F, j % 3 == 0 ? 3F : 1.6F,
                            fx + (float) Math.cos(a) * cr * 0.96F, fy + (float) Math.sin(a) * cr * 0.96F, j % 3 == 0 ? 3F : 1.6F, acc, la);
                }
                taper(bb, m, fx, fy, 3F, fx + cr * 0.15F, fy - cr * 0.55F, 0.5F, acc, la);
                taper(bb, m, fx, fy, 2F, fx + cr * 0.72F, fy + cr * 0.12F, 0.5F, acc, la);
            }
            case 12 -> {  // Finral: rings of space closing in on the hit, a gold rim on the innermost''')

# ---------------------------------------------------------------- lang, models, curios
lp = os.path.join(A, 'lang/en_us.json'); lang = json.loads(rd(lp))
lang['item.iron_spell_grimoire.time_grimoire'] = 'Time Grimoire'
lang['tooltip.iron_spell_grimoire.time_grimoire.passive'] = 'Passive - Stored Time'
lang['tooltip.iron_spell_grimoire.time_grimoire.desc'] = 'Every hit you land banks a second, up to ten; some spells spend them for more. Twelve spells of Time Magic, from Clock Hand Bolt to Zenith Hour.'
lang['tooltip.iron_spell_grimoire.time_grimoire.lore'] = 'A midnight grimoire with a clock for a heart. When its pages open, time holds its breath.'
lang['ui.iron_spell_grimoire.time_grimoire_only'] = 'Only the Time Grimoire can cast this.'
for (sid, name, cd, mana, pw, anim, meth, lvl, guide) in SPELLS:
    lang[f'spell.iron_spell_grimoire.{sid}'] = f'Time Magic: {name}'
    lang[f'spell.iron_spell_grimoire.{sid}.guide'] = guide
wr(lp, json.dumps(lang, indent=2, ensure_ascii=False) + '\n')
for suffix in ('', '_model', '_open'):
    wr(os.path.join(A, f'models/item/time_grimoire{suffix}.json'), rd(os.path.join(A, f'models/item/water_grimoire{suffix}.json')).replace('water_grimoire', 'time_grimoire'))
cp = os.path.join(D, 'curios/tags/items/spellbook.json'); cur = json.loads(rd(cp))
if 'iron_spell_grimoire:time_grimoire' not in cur['values']: cur['values'].append('iron_spell_grimoire:time_grimoire')
wr(cp, json.dumps(cur, indent=2) + '\n')
print('done')
