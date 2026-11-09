"""Wires the seven newer grimoires (tools/new_books.py) into the mod: registries, mastery, modes, lang, models, curios, combos.
Idempotent: every insert is skipped when its marker is already present."""
import json, os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
from new_books import BOOKS

ROOT = os.path.join(os.path.dirname(__file__), '..')
J = os.path.join(ROOT, 'src/main/java/com/ironspellgrimoire')
A = os.path.join(ROOT, 'src/main/resources/assets/iron_spell_grimoire')
D = os.path.join(ROOT, 'src/main/resources/data')

def rd(p): return open(p, encoding='utf-8').read()
def wr(p, s): open(p, 'w', encoding='utf-8').write(s)
def up(s): return s.upper()

def patch(path, marker, old, new, count=1):
    s = rd(path)
    if marker in s:
        print('skip', os.path.basename(path), marker); return
    if old not in s:
        raise SystemExit(f'pattern missing in {path}: {old[:60]!r}')
    wr(path, s.replace(old, new, count)); print('patched', os.path.basename(path))

# ------------------------------------------------------------------ AddonSpellRegistry
reg = ''
for b in BOOKS:
    k = b['key']
    reg += f'''
    // ------------------------------------------------------------------ {b['title']} ({b['owner']})
    private static com.ironspellgrimoire.spells.BookSpell {k}(String id, int cd, int mana, float power, String anim, com.ironspellgrimoire.spells.BookSpell.Effect fx) {{
        return new com.ironspellgrimoire.spells.BookSpell(id, io.redspace.ironsspellbooks.api.registry.SchoolRegistry.{b['school']},
                com.ironspellgrimoire.item.{b['cls']}.class, "ui.iron_spell_grimoire.{b['item']}_only", cd, mana, power,
                com.ironspellgrimoire.spells.BookSpell.anim(anim), fx);
    }}
'''
    names = []
    for (sid, name, cd, mana, power, anim, meth, lvl, guide) in b['spells']:
        reg += f'    public static final RegistryObject<AbstractSpell> {up(sid)} = registerSpell({k}("{sid}", {cd}, {mana}, {power}, "{anim}", com.ironspellgrimoire.server.{b["fx"]}::{meth}));\n'
        names.append(up(sid))
    reg += f'\n    /** The eight {b["title"]} spells, in the order they fill the book. */\n    public static final List<RegistryObject<AbstractSpell>> {b["list"]} = List.of(\n            ' + ', '.join(names) + ');\n'
patch(os.path.join(J, 'registry/AddonSpellRegistry.java'), 'FLAME_SPIRIT_SPELLS',
      '    private static RegistryObject<AbstractSpell> registerSpell(', reg + '\n    private static RegistryObject<AbstractSpell> registerSpell(')

# ------------------------------------------------------------------ AddonItemRegistry
items = ''
for b in BOOKS:
    items += f'''    // ------------------------------------------------------------------ {b['owner']}: {b['title']}
    public static final RegistryObject<Item> {up(b['key'])}_GRIMOIRE = ITEMS.register("{b['item']}", com.ironspellgrimoire.item.{b['cls']}::new);

'''
p = os.path.join(J, 'registry/AddonItemRegistry.java')
patch(p, 'FLAME_SPIRIT_GRIMOIRE', '    public static final RegistryObject<CreativeModeTab> TAB', items + '    public static final RegistryObject<CreativeModeTab> TAB')
patch(p, 'FLAME_SPIRIT_GRIMOIRE, LION', 'List.of(WATER_GRIMOIRE, MIRROR_GRIMOIRE, RIFT_GRIMOIRE)',
      'List.of(WATER_GRIMOIRE, MIRROR_GRIMOIRE, RIFT_GRIMOIRE, ' + ', '.join(up(b['key']) + '_GRIMOIRE' for b in BOOKS) + ')')

# ------------------------------------------------------------------ GrimoireMastery
mast = ''
for b in BOOKS:
    mast += f'        book(AddonItemRegistry.{up(b["key"])}_GRIMOIRE::get, Fx.{b["pal"]},\n'
    ents = []
    for s in b['spells']:
        ents.append(f'"{s[0]}", {s[7]}')
    # two per line, keep readable
    lines = ['                ' + ', '.join(ents[i:i + 2]) for i in range(0, len(ents), 2)]
    mast += ',\n'.join(lines) + ');\n'
patch(os.path.join(J, 'server/GrimoireMastery.java'), 'FLAME_SPIRIT_GRIMOIRE',
      '                "dimensional_severance", 5);\n', '                "dimensional_severance", 5);\n' + mast)

# ------------------------------------------------------------------ Fx palette constants
patch(os.path.join(J, 'server/Fx.java'), 'FUEGO = 22', 'VOID = 21;', 'VOID = 21,\n            FUEGO = 22, LION = 23, TREE = 24, RIPPER = 25, EARTH = 26, BEAST = 27, COTTON = 28;')

# ------------------------------------------------------------------ FxSend frame constants
patch(os.path.join(J, 'server/FxSend.java'), 'FRAME_FUEGOLEON', 'FRAME_VANESSA = 11, FRAME_FINRAL = 12;',
      'FRAME_VANESSA = 11, FRAME_FINRAL = 12,\n            FRAME_FUEGOLEON = 13, FRAME_LEOPOLD = 14, FRAME_WILLIAM = 15, FRAME_JACK = 16, FRAME_SOL = 17, FRAME_VETTO = 18, FRAME_CHARMY = 19;')

# ------------------------------------------------------------------ modes
modes = ['salamander_spirit', 'mane_blaze', 'world_tree_mantle', 'ripper_frenzy', 'bedrock_armor', 'beast_instinct', 'nean_armor']
patch(os.path.join(J, 'network/ModeIds.java'), '"salamander_spirit"', '"spatial_gate", "thread_cage", "thread_net", "mana_zone", "valkyrie_dress", "reflect_guard"',
      '"spatial_gate", "thread_cage", "thread_net", "mana_zone", "valkyrie_dress", "reflect_guard",\n            ' + ', '.join(f'"{m}"' for m in modes))
patch(os.path.join(J, 'client/ModeHudOverlay.java'), '"salamander_spirit"', '            case "surpassing_limits" -> 0xFFFFF4C0;',
      '''            case "salamander_spirit" -> 0xFFFF5A1E;
            case "mane_blaze" -> 0xFFFFB02E;
            case "world_tree_mantle" -> 0xFF6BE05A;
            case "ripper_frenzy" -> 0xFFFF4458;
            case "bedrock_armor" -> 0xFFC8A064;
            case "beast_instinct" -> 0xFF4DDCB0;
            case "nean_armor" -> 0xFFFFB4DC;
            case "surpassing_limits" -> 0xFFFFF4C0;''')

# ------------------------------------------------------------------ lang
lp = os.path.join(A, 'lang/en_us.json')
lang = json.loads(rd(lp))
magic = {'flame_spirit': 'Flame Spirit Magic', 'lion_flame': "Lion's Flame Magic", 'world_tree': 'World Tree Magic', 'slash': 'Slash Magic',
         'earth': 'Earth Magic', 'beast': 'Beast Magic', 'cotton': 'Cotton Magic'}
for b in BOOKS:
    it = b['item']
    lang[f'item.iron_spell_grimoire.{it}'] = b['title']
    lang[f'tooltip.iron_spell_grimoire.{it}.passive'] = f'Passive - {b["passive"]}'
    first, last = b['spells'][0][1], b['spells'][-1][1]
    lang[f'tooltip.iron_spell_grimoire.{it}.desc'] = f'{b["passive_desc"]} Eight spells of {magic[b["key"]]}, from {first} to {last}.'
    lang[f'tooltip.iron_spell_grimoire.{it}.lore'] = b['lore']
    lang[f'ui.iron_spell_grimoire.{it}_only'] = f'Only the {b["title"]} can cast this.'
    for (sid, name, cd, mana, power, anim, meth, lvl, guide) in b['spells']:
        lang[f'spell.iron_spell_grimoire.{sid}'] = f'{magic[b["key"]]}: {name}'
        lang[f'spell.iron_spell_grimoire.{sid}.guide'] = guide
wr(lp, json.dumps(lang, indent=2, ensure_ascii=False) + '\n')
print('lang', len(lang))

# ------------------------------------------------------------------ models + curios
for b in BOOKS:
    it = b['item']
    for suffix in ('', '_model', '_open'):
        src = rd(os.path.join(A, f'models/item/water_grimoire{suffix}.json')).replace('water_grimoire', it)
        wr(os.path.join(A, f'models/item/{it}{suffix}.json'), src)
cp = os.path.join(D, 'curios/tags/items/spellbook.json')
cur = json.loads(rd(cp))
for b in BOOKS:
    v = f'iron_spell_grimoire:{b["item"]}'
    if v not in cur['values']: cur['values'].append(v)
wr(cp, json.dumps(cur, indent=2) + '\n')

# ------------------------------------------------------------------ combos
combos = ''
for b in BOOKS:
    for (a, c, name, kind, what) in b['combos']:
        combos += f',\n            new Combo("{a}", "{c}", "{name}", Kind.{kind}, {b["palN"]}, FxSend.FRAME_{b["frame"]}, "{what}")'
patch(os.path.join(J, 'server/ComboChain.java'), 'Spirit Pierce', '"the space round the target folds in on it")', '"the space round the target folds in on it")' + combos)
print('done')
