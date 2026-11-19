"""The Architect of Ruin Grimoire (a pocket fortress inside a book; every name and look here is original): spell and room
icons, the cover, leather, trim, inventory icon, book models, the curios tag, the advancements and the text.
  Cover - weathered grey leather with brass trim; on it a fortress gate between two crenellated towers, a brass
          keystone over the arch and an ember glow in the gateway, a banner on each tower.
  (The walls, towers and sentries themselves: tools/blender/architect_ruin.py.)
python3 tools/architect_assets.py"""
import json, math, os, sys
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
from icons import make
from _cover_helpers import *   # SC, W, H, Pp, poly, circ, ell, arc, frame, glow, shift, tint_trim, R, A_, TEX, M

# ------------------------------------------------------------------ icons (16x16)
ST = ((92, 88, 84), (20, 18, 16), (232, 184, 96), (255, 154, 64))     # brass on stone, ember accent
AR = ((40, 70, 76), (10, 18, 22), (120, 230, 220), (232, 184, 96))    # teal on night, brass accent
SA = ((96, 80, 40), (24, 18, 8), (250, 220, 130), (255, 255, 255))    # gold on umber
WK = ((100, 54, 24), (24, 12, 6), (255, 154, 64), (232, 184, 96))     # ember on soot
MA = ((96, 36, 32), (22, 8, 8), (240, 200, 120), (220, 90, 70))       # brass on crimson

tower = [('m', 'r', 5, 4, 10, 14), ('m', 'r', 4, 2, 5, 4), ('m', 'r', 7, 2, 8, 4), ('m', 'r', 10, 2, 11, 4), ('d', 'r', 7, 10, 8, 14)]
make('fortress_rampart', *ST, [('m', 'r', 1, 7, 14, 14), ('m', 'r', 1, 5, 3, 7), ('m', 'r', 6, 5, 9, 7), ('m', 'r', 12, 5, 14, 7), ('d', 'l', 1, 10, 14, 10), ('d', 'l', 5, 7, 5, 10), ('d', 'l', 10, 10, 10, 14), ('a', 'l', 1, 12, 14, 12)])
make('siege_mode', *ST, [('m', 'R', 2, 3, 13, 13), ('m', 'R', 3, 4, 12, 12), ('m', 'r', 1, 2, 3, 4), ('m', 'r', 12, 2, 14, 4), ('m', 'r', 1, 12, 3, 14), ('m', 'r', 12, 12, 14, 14), ('d', 'r', 6, 12, 9, 13), ('a', 'p', 7, 8), ('a', 'p', 8, 8)])
for i in range(1, 5):
    make(f'fortress_room_{i}', *ST, [('m', 'R', 3, 3, 12, 12), ('m', 'r', 3, 3, 12, 5), ('a', 'l', 6, 7, 6 + i * 1, 7)] + [('w', 'p', 5 + k * 2, 9) for k in range(i)])
make('the_fortress', *ST, [('m', 'r', 2, 6, 13, 14)] + tower[:0] + [('m', 'r', 2, 3, 5, 6), ('m', 'r', 10, 3, 13, 6), ('m', 'r', 6, 4, 9, 6), ('d', 'r', 6, 9, 9, 14), ('a', 'r', 7, 10, 8, 13), ('w', 'p', 3, 2), ('w', 'p', 11, 2)])
make('citadel', *SA, [('m', 'r', 1, 8, 14, 14), ('m', 'r', 3, 4, 6, 8), ('m', 'r', 9, 4, 12, 8), ('m', 'r', 6, 1, 9, 8), ('d', 'r', 6, 10, 9, 14), ('a', 'p', 7, 0), ('a', 'p', 8, 0), ('w', 'p', 4, 3), ('w', 'p', 10, 3), ('a', 'l', 1, 11, 14, 11)])

ROOM_ICONS = {
    'barracks': (MA, [('m', 'r', 3, 6, 12, 13), ('m', 'g', [(2, 6), (7, 2), (8, 2), (13, 6)]), ('d', 'r', 6, 9, 9, 13), ('a', 'l', 5, 9, 5, 12), ('a', 'l', 10, 9, 10, 12)]),
    'armoury': (MA, [('m', 'l', 4, 12, 11, 3), ('m', 'l', 11, 12, 4, 3), ('a', 'l', 3, 11, 5, 13), ('a', 'l', 12, 11, 10, 13), ('w', 'p', 7, 7)]),
    'training_yard': (MA, [('m', 'E', 4, 4, 11, 11), ('d', 'E', 6, 6, 9, 9), ('a', 'E', 7, 7, 8, 8), ('m', 'l', 12, 2, 8, 6), ('w', 'p', 12, 2)]),
    'watchtower': (MA, tower + [('a', 'r', 7, 6, 8, 8)]),
    'gatehouse': (MA, [('m', 'r', 2, 4, 13, 14), ('d', 'r', 5, 7, 10, 14), ('a', 'l', 6, 7, 6, 13), ('a', 'l', 8, 7, 8, 13), ('a', 'l', 5, 10, 10, 10), ('m', 'r', 2, 2, 4, 4), ('m', 'r', 11, 2, 13, 4)]),
    'war_room': (MA, [('m', 'r', 2, 4, 13, 12), ('d', 'l', 4, 6, 7, 9), ('d', 'l', 7, 9, 11, 7), ('a', 'g', [(10, 5), (12, 7), (10, 8)]), ('w', 'p', 4, 6)]),
    'observatory': (AR, [('m', 'g', [(3, 14), (3, 8), (8, 4), (13, 8), (13, 14)]), ('a', 'l', 8, 6, 13, 1), ('w', 'p', 13, 1), ('w', 'p', 3, 3), ('w', 'p', 11, 5), ('d', 'r', 7, 10, 9, 14)]),
    'library': (AR, [('m', 'r', 2, 3, 4, 13), ('a', 'r', 5, 4, 7, 13), ('m', 'r', 8, 2, 10, 13), ('a', 'g', [(11, 4), (13, 5), (11, 13), (10, 12)]), ('d', 'l', 1, 13, 14, 13)]),
    'arcanum': (AR, [('m', 'g', [(7, 1), (10, 6), (7, 14), (4, 6)]), ('w', 'l', 7, 3, 7, 11), ('a', 'p', 2, 10), ('a', 'p', 12, 3)]),
    'scrying_pool': (AR, [('m', 'E', 2, 8, 13, 14), ('d', 'E', 4, 9, 11, 13), ('w', 'a', 5, 9, 10, 12, 200, 340), ('a', 'l', 7, 2, 7, 6), ('a', 'p', 5, 4), ('a', 'p', 9, 4)]),
    'alchemy_lab': (AR, [('m', 'r', 6, 2, 9, 6), ('m', 'g', [(6, 6), (9, 6), (13, 13), (2, 13)]), ('a', 'g', [(4, 10), (11, 10), (13, 13), (2, 13)]), ('w', 'p', 7, 11), ('w', 'p', 9, 12)]),
    'rune_vault': (AR, [('m', 'R', 2, 2, 13, 13), ('a', 'G', [(7, 3), (12, 7), (7, 12), (3, 7)]), ('w', 'p', 7, 7), ('m', 'l', 7, 4, 7, 11)]),
    'chapel': (SA, [('m', 'g', [(3, 14), (3, 7), (7, 3), (8, 3), (12, 7), (12, 14)]), ('a', 'l', 7, 0, 7, 3), ('a', 'l', 6, 1, 8, 1), ('d', 'r', 6, 9, 9, 14), ('w', 'p', 7, 6)]),
    'reliquary': (SA, [('m', 'r', 3, 7, 12, 13), ('m', 'r', 2, 5, 13, 7), ('a', 'E', 6, 1, 9, 4), ('w', 'p', 7, 2), ('d', 'l', 3, 10, 12, 10)]),
    'crypt': (SA, [('m', 'g', [(3, 14), (3, 6), (7, 2), (8, 2), (12, 6), (12, 14)]), ('d', 'E', 5, 6, 10, 11), ('w', 'p', 6, 8), ('w', 'p', 9, 8), ('d', 'r', 6, 12, 9, 14)]),
    'bell_tower': (SA, [('m', 'g', [(5, 4), (10, 4), (12, 11), (3, 11)]), ('m', 'r', 2, 11, 13, 12), ('a', 'E', 6, 12, 9, 14), ('m', 'r', 7, 2, 8, 4), ('w', 'a', 1, 9, 4, 13, 120, 240), ('w', 'a', 11, 9, 14, 13, 300, 60)]),
    'garden': (SA, [('a', 'l', 7, 14, 7, 6), ('m', 'E', 3, 3, 8, 8), ('m', 'E', 7, 2, 12, 7), ('m', 'E', 5, 6, 10, 11), ('w', 'p', 6, 5), ('w', 'p', 10, 4)]),
    'hall_of_banners': (SA, [('a', 'l', 3, 1, 3, 14), ('a', 'l', 12, 1, 12, 14), ('m', 'g', [(4, 2), (7, 2), (7, 10), (5, 8), (4, 10)]), ('m', 'g', [(8, 2), (11, 2), (11, 10), (10, 8), (8, 10)])]),
    'forge': (WK, [('m', 'r', 2, 9, 13, 11), ('m', 'r', 4, 11, 11, 13), ('m', 'r', 5, 13, 10, 14), ('a', 'g', [(7, 3), (9, 6), (8, 8), (6, 8), (5, 6)]), ('w', 'p', 7, 6)]),
    'foundry': (WK, [('m', 'r', 3, 3, 12, 7), ('a', 'r', 7, 7, 8, 14), ('w', 'p', 4, 4), ('d', 'l', 3, 6, 12, 6)]),
    'workshop': (WK, [('m', 'l', 2, 12, 12, 4), ('m', 'l', 3, 12, 13, 4), ('a', 'r', 10, 2, 14, 6), ('m', 'E', 2, 9, 6, 13), ('d', 'E', 3, 10, 5, 12)]),
    'siege_yard': (WK, [('m', 'l', 2, 13, 12, 3), ('m', 'r', 1, 12, 7, 14), ('a', 'E', 10, 1, 14, 5), ('w', 'p', 11, 2)]),
    'cistern': (WK, [('a', 'a', 1, 4, 8, 11, 180, 360), ('a', 'a', 7, 4, 14, 11, 180, 360), ('m', 'a', 1, 7, 8, 14, 180, 360), ('m', 'a', 7, 7, 14, 14, 180, 360), ('w', 'p', 4, 6)]),
    'treasury': (WK, [('a', 'E', 2, 8, 7, 13), ('a', 'E', 8, 8, 13, 13), ('m', 'E', 5, 3, 10, 8), ('w', 'p', 7, 5), ('d', 'l', 4, 10, 5, 10), ('d', 'l', 10, 10, 11, 10)]),
}
for k, (pal, cmds) in ROOM_ICONS.items():
    make('fortress_' + k, *pal, cmds)

# ------------------------------------------------------------------ cover
INK = (12, 10, 8, 255)
STONE, STONE_SH, STONE_HI = (150, 146, 140, 255), (92, 88, 84, 255), (200, 196, 188, 255)
BR, BR_SH, BR_HI = (232, 184, 96, 255), (154, 106, 40, 255), (255, 230, 160, 255)
EMB = (255, 154, 64)


def fortress_draw(im, d, cx, cy, k, big):
    im = glow(im, cx, cy + 30 * k, ((70 * k, 22), (44 * k, 40)), EMB); d = ImageDraw.Draw(im)
    def rect(x0, y0, x1, y1, fill, out=INK, w=2):
        poly(d, [(cx + x0 * k, cy + y0 * k), (cx + x1 * k, cy + y0 * k), (cx + x1 * k, cy + y1 * k), (cx + x0 * k, cy + y1 * k)], fill, out, w)
    # the curtain wall and its merlons
    rect(-62, -6, 62, 58, STONE)
    for i in range(-3, 4):
        rect(i * 18 - 6, -16, i * 18 + 6, -6, STONE)
    # the two towers, taller, with banners
    for sx in (-1, 1):
        rect(sx * 74 - 18, -40, sx * 74 + 18, 58, STONE_SH if sx < 0 else STONE)
        for j in (-1, 0, 1):
            rect(sx * 74 + j * 12 - 5, -50, sx * 74 + j * 12 + 5, -40, STONE)
        d.line([Pp(cx + sx * 74 * k, cy - 50 * k), Pp(cx + sx * 74 * k, cy - 82 * k)], fill=INK, width=int(3 * k * SC))
        poly(d, [(cx + sx * 74 * k, cy - 82 * k), (cx + (sx * 74 + sx * 26) * k, cy - 76 * k), (cx + sx * 74 * k, cy - 68 * k)], EMB + (255,), INK, 2)
        rect(sx * 74 - 3, -22, sx * 74 + 3, -8, (24, 18, 14, 255))   # an arrow slit
    # stone courses
    for yy in range(4, 56, 12):
        d.line([Pp(cx - 60 * k, cy + yy * k), Pp(cx + 60 * k, cy + yy * k)], fill=STONE_SH, width=int(2 * SC))
    # the gate: dark arch with an ember glow, a brass portcullis
    gate = [(cx - 22 * k, cy + 58 * k), (cx - 22 * k, cy + 16 * k)]
    for i in range(13):
        a = math.pi - math.pi * i / 12
        gate.append((cx + 22 * k * math.cos(a), cy + 16 * k - 22 * k * math.sin(a)))
    gate.append((cx + 22 * k, cy + 58 * k))
    poly(d, gate, (40, 18, 8, 255), INK, 3)
    im = glow(im, cx, cy + 40 * k, ((18 * k, 70),), EMB); d = ImageDraw.Draw(im)
    for x in (-14, -5, 5, 14):
        d.line([Pp(cx + x * k, cy + 4 * k), Pp(cx + x * k, cy + 56 * k)], fill=BR, width=int(2.5 * k * SC))
    for y in (24, 40):
        d.line([Pp(cx - 20 * k, cy + y * k), Pp(cx + 20 * k, cy + y * k)], fill=BR_SH, width=int(2 * k * SC))
    # the brass keystone over the arch
    poly(d, [(cx - 7 * k, cy - 10 * k), (cx + 7 * k, cy - 10 * k), (cx + 5 * k, cy + 2 * k), (cx - 5 * k, cy + 2 * k)], BR, INK, 2)
    circ(d, cx, cy - 4 * k, 2.4 * k, fill=BR_HI)
    return im


def cover(big):
    LEA, LEA_SH = (64, 60, 56, 255), (40, 37, 34, 255)
    im = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(im)
    frame(d, INK, LEA, LEA_SH, BR, BR_SH, BR_HI, EMB + (255,))
    cy = 190 if big else 206
    im = fortress_draw(im, d, 128, cy, 0.95 if big else 0.6, big)
    return im.resize((256, 384), Image.LANCZOS)


name = 'architect_grimoire'
cover(True).save(os.path.join(M, f'{name}_front.png'))
cover(False).save(os.path.join(M, f'{name}_back.png'))
shift(Image.open(os.path.join(M, 'dark_grimoire_leather.png')), 0.08, 0.15, 1.15).save(os.path.join(M, f'{name}_leather.png'))
tint_trim(name, (232, 184, 96))
ic = shift(Image.open(os.path.join(TEX, 'dark_grimoire.png')), 0.07, 0.4, 0.95); px = ic.load()
for (x, y, c) in ((6, 6, (232, 184, 96)), (9, 6, (232, 184, 96)), (6, 7, (200, 196, 188)), (7, 7, (200, 196, 188)), (8, 7, (200, 196, 188)), (9, 7, (200, 196, 188)),
                  (6, 8, (200, 196, 188)), (7, 8, (255, 154, 64)), (8, 8, (255, 154, 64)), (9, 8, (200, 196, 188))):
    if px[x, y][3]: px[x, y] = c + (255,)
ic.save(os.path.join(TEX, f'{name}.png'))
MD = os.path.join(A_, 'models/item')
CT = os.path.join(R, 'data/curios/tags/items/spellbook.json')
c = json.load(open(CT))
for suf in ('', '_model', '_open'):
    s = open(os.path.join(MD, f'light_grimoire{suf}.json')).read().replace('light_grimoire', name)
    open(os.path.join(MD, f'{name}{suf}.json'), 'w').write(s)
if f'iron_spell_grimoire:{name}' not in c['values']: c['values'].append(f'iron_spell_grimoire:{name}')
json.dump(c, open(CT, 'w'), indent=2)

# ------------------------------------------------------------------ advancements
D = os.path.join(R, 'data/iron_spell_grimoire/advancements/main')
ADV = [
    ("first_room_spell", "chosen", "iron_spell_grimoire:architect_grimoire", "task", "Foundations", "Cast the spell of a room you built in the Architect of Ruin", True),
    ("fortress_level", "first_room_spell", "minecraft:bricks", "task", "Raising the Walls", "Raise the Architect of Ruin to a new book level", True),
    ("full_garrison", "fortress_level", "minecraft:white_banner", "goal", "Full Garrison", "Have eight defenders in your garrison", True),
    ("captain", "full_garrison", "minecraft:golden_sword", "challenge", "Captain of the Guard", "A defender of yours rises to Captain", True),
    ("every_blueprint", "fortress_level", "minecraft:map", "goal", "Master Plans", "Find every blueprint", True),
    ("every_room", "every_blueprint", "minecraft:lodestone", "challenge", "The Whole Fortress", "Build all 24 rooms", True),
    ("citadel", "every_room", "minecraft:beacon", "challenge", "Citadel", "Raise the Citadel", True),
]
LP = os.path.join(A_, 'lang/en_us.json')
lang = json.load(open(LP))
for n, par, icon, frame_, t_, desc, hid in ADV:
    j = {"display": {"icon": {"item": icon}, "title": {"translate": f"advancement.iron_spell_grimoire.{n}.title"},
                     "description": {"translate": f"advancement.iron_spell_grimoire.{n}.description"}, "frame": frame_,
                     "show_toast": True, "announce_to_chat": True, "hidden": hid},
         "parent": "iron_spell_grimoire:main/" + par, "criteria": {"code": {"trigger": "minecraft:impossible"}}}
    json.dump(j, open(os.path.join(D, n + '.json'), 'w'), indent=1)
    lang[f"advancement.iron_spell_grimoire.{n}.title"] = t_
    lang[f"advancement.iron_spell_grimoire.{n}.description"] = desc

# ------------------------------------------------------------------ text
lang['item.iron_spell_grimoire.architect_grimoire'] = 'Architect of Ruin Grimoire'
lang['entity.iron_spell_grimoire.fortress_wall'] = 'Fortress Wall'
lang['entity.iron_spell_grimoire.fortress_tower'] = 'Fortress Tower'
lang['entity.iron_spell_grimoire.fortress_defender'] = 'Fortress Defender'
lang['ui.iron_spell_grimoire.architect_grimoire_only'] = 'Only the Architect of Ruin Grimoire can cast this.'
lang['tooltip.iron_spell_grimoire.architect_grimoire.passive'] = ('Passive - a fortress inside the book. Defeat foes for Stone, then open The Fortress page to build rooms on its '
    'floor plan (3x3, growing to 5x5). Each of the 24 rooms gives a spell for one of the four Room pages, and many a passive; rooms side by side can pair '
    'for a bonus. Siege Mode raises walls and towers round you while your garrison - mobs whose banners you claimed - holds the middle. Blueprints for new '
    'rooms come from book levels, bosses and rifts. Open The Fortress for a guided tour.')
lang['tooltip.iron_spell_grimoire.architect_grimoire.desc'] = 'Eight pages, one fortress, built stone by stone.'
lang['tooltip.iron_spell_grimoire.architect_grimoire.lore'] = 'Every ruin was a fortress once.'
for k, (title, guide) in {
 'fortress_rampart': ('Rampart', 'Five blocks of wall rise across your path, three blocks ahead, for 12 s. They block mobs and arrows and can be broken. 8 s cooldown, 30 mana.'),
 'siege_mode': ('Siege Mode', 'A square of walls rises round you for 30 s (wider as the book levels up) with a gate on the side you face, towers at the corners and your garrison holding the middle. 3 min cooldown, 120 mana.'),
 'fortress_room_1': ('Room I', 'Casts the room spell on your first Room page (choose it on the Spells tab of The Fortress).'),
 'fortress_room_2': ('Room II', 'Casts the room spell on your second Room page.'),
 'fortress_room_3': ('Room III', 'Casts the room spell on your third Room page.'),
 'fortress_room_4': ('Room IV', 'Casts the room spell on your fourth Room page.'),
 'the_fortress': ('The Fortress', 'Open your fortress: the floor plan, your garrison, your Room pages and a full guide.'),
 'citadel': ('Citadel', 'Book level 10 and the grimoire mastered: a full fortress rises for 20 s, every room fires its spell once, then the walls fall inward on your foes.'),
}.items():
    lang[f'spell.iron_spell_grimoire.{k}'] = f'Architect: {title}'
    lang[f'spell.iron_spell_grimoire.{k}.guide'] = guide
json.dump(lang, open(LP, 'w'), indent=2, ensure_ascii=False)
print('architect assets written')
