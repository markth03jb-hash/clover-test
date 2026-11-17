"""The Hunger Blade Grimoire (a living sword that grows as it feeds; every name and look here is original): spell icons,
the cover, leather, trim, inventory icon, models, the curios tag and the text.
  Hunger - dark crimson leather with blackened steel trim; on the cover a sword standing point-down through an open
             jaw of teeth, a slit eye in its guard.
python3 tools/hunger_assets.py"""
import json, math, os, random, sys
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
from icons import make
from _cover_helpers import *   # SC, W, H, Pp, poly, circ, ell, arc, frame, glow, shift, tint_trim, R, A_, TEX, M

# ------------------------------------------------------------------ spell icons (16x16)
BL = ((120, 14, 26), (16, 3, 6), (220, 220, 228), (255, 60, 70))    # steel blade on red-black, red accent
JW = ((150, 24, 36), (22, 4, 8), (236, 228, 205), (255, 70, 70))    # teeth on flesh
make('draw_blade', *BL, [('m', 'l', 3, 12, 11, 4), ('m', 'l', 4, 12, 12, 4), ('w', 'p', 12, 3), ('a', 'l', 2, 10, 5, 13), ('a', 'r', 1, 13, 3, 14),
                         ('a', 'p', 6, 9), ('a', 'p', 8, 7), ('a', 'p', 10, 5)])
make('gnash', *JW, [('m', 'g', [(1, 4), (14, 4), (12, 7), (3, 7)]), ('m', 'g', [(1, 11), (14, 11), (12, 8), (3, 8)]), ('d', 'p', 3, 5), ('d', 'p', 6, 5),
                    ('d', 'p', 9, 5), ('d', 'p', 12, 5), ('d', 'p', 4, 10), ('d', 'p', 7, 10), ('d', 'p', 10, 10), ('a', 'l', 0, 7, 0, 8), ('a', 'l', 15, 7, 15, 8)])
make('feast', *BL, [('m', 'a', 1, 1, 14, 14, 200, 520), ('a', 'a', 3, 3, 12, 12, 20, 340), ('w', 'p', 7, 7), ('a', 'E', 6, 6, 9, 9),
                    ('m', 'l', 13, 2, 11, 4)])
make('unleash', *JW, [('a', 'E', 4, 4, 11, 11), ('w', 'E', 6, 6, 9, 9), ('m', 'l', 7, 0, 7, 3), ('m', 'l', 7, 12, 7, 15), ('m', 'l', 0, 7, 3, 7),
                      ('m', 'l', 12, 7, 15, 7), ('m', 'l', 2, 2, 4, 4), ('m', 'l', 13, 13, 11, 11), ('m', 'l', 2, 13, 4, 11), ('m', 'l', 13, 2, 11, 4)])
make('the_blade', *BL, [('m', 'l', 7, 1, 7, 10), ('m', 'l', 8, 1, 8, 10), ('w', 'p', 7, 0), ('a', 'l', 3, 11, 12, 11), ('a', 'E', 6, 10, 9, 13),
                        ('d', 'p', 7, 11), ('m', 'l', 7, 13, 7, 15), ('a', 'p', 3, 10), ('a', 'p', 12, 10)])


# ------------------------------------------------------------------ cover
def cover(big, INK, LEA, LEA_SH, MET, MET_SH, MET_HI, GEM, draw):
    im = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(im)
    frame(d, INK, LEA, LEA_SH, MET, MET_SH, MET_HI, GEM)
    cy = 176 if big else 196
    im = draw(im, d, 128, cy, 1.0 if big else 0.62, big)
    return im.resize((256, 384), Image.LANCZOS)


def hunger_draw(im, d, cx, cy, k, big):
    INKC, RED, DEEP, STEEL, STEEL_SH, BONE, EYE = (12, 3, 6, 255), (190, 30, 44, 255), (96, 12, 22, 255), (200, 200, 210, 255), (90, 90, 104, 255), (236, 228, 205, 255), (255, 70, 60, 255)
    im = glow(im, cx, cy, ((84 * k, 26), (54 * k, 50)), (255, 40, 50)); d = ImageDraw.Draw(im)
    # the open jaw behind: an upper and a lower arc of teeth
    for sgn in (-1, 1):
        y0 = cy + sgn * 46 * k
        pts = [(cx + math.cos(a) * 70 * k, y0 - sgn * math.sin(a) * 26 * k) for a in [math.pi * i / 20 for i in range(21)]]
        for w_, col in ((14, INKC), (9, DEEP)):
            for i in range(len(pts) - 1): d.line([Pp(*pts[i]), Pp(*pts[i + 1])], fill=col, width=max(1, int(w_ * k * SC)))
        for i in range(1, 20, 2):
            x, y = pts[i]
            tip = (x, y + sgn * (-16 if i % 4 == 1 else -11) * k * -1)
            poly(d, [(x - 4 * k, y), (x + 4 * k, y), (tip[0], y - sgn * 14 * k)], BONE, INKC)
    # the sword, point down through the jaw
    blade = [(cx - 9 * k, cy - 52 * k), (cx + 9 * k, cy - 52 * k), (cx + 7 * k, cy + 62 * k), (cx, cy + 82 * k), (cx - 7 * k, cy + 62 * k)]
    poly(d, blade, STEEL, INKC)
    poly(d, [(cx, cy - 52 * k), (cx + 9 * k, cy - 52 * k), (cx + 7 * k, cy + 62 * k), (cx, cy + 82 * k)], STEEL_SH, None)
    d.line([Pp(cx, cy - 48 * k), Pp(cx, cy + 70 * k)], fill=RED, width=max(1, int(2 * k * SC)))
    # its guard: a little jaw with an eye; the grip above
    poly(d, [(cx - 30 * k, cy - 58 * k), (cx + 30 * k, cy - 58 * k), (cx + 20 * k, cy - 46 * k), (cx - 20 * k, cy - 46 * k)], DEEP, INKC)
    for x in (-24, -14, 14, 24): poly(d, [(cx + (x - 3) * k, cy - 50 * k), (cx + (x + 3) * k, cy - 50 * k), (cx + x * k, cy - 40 * k)], BONE, INKC)
    for (rx, ry, col) in ((10, 6, INKC), (8, 5, BONE), (6, 4, EYE)): d.ellipse([Pp(cx - rx * k, cy - 52 * k - ry * k), Pp(cx + rx * k, cy - 52 * k + ry * k)], fill=col)
    d.line([Pp(cx, cy - 56 * k), Pp(cx, cy - 48 * k)], fill=INKC, width=max(1, int(2 * k * SC)))
    d.rectangle([Pp(cx - 5 * k, cy - 84 * k), Pp(cx + 5 * k, cy - 58 * k)], fill=RED, outline=INKC)
    for j in range(5): d.line([Pp(cx - 5 * k, cy - (80 - 5 * j) * k), Pp(cx + 5 * k, cy - (77 - 5 * j) * k)], fill=BONE, width=max(1, int(1.5 * k * SC)))
    circ(d, cx, cy - 88 * k, 6 * k, fill=EYE)
    return im


name = 'hunger_grimoire'
fn = lambda b: cover(b, (14, 4, 7, 255), (104, 14, 24, 255), (66, 8, 16, 255), (70, 70, 80, 255), (34, 34, 40, 255), (150, 150, 160, 255), (255, 60, 60, 255), hunger_draw)
fn(True).save(os.path.join(M, f'{name}_front.png'))
fn(False).save(os.path.join(M, f'{name}_back.png'))
shift(Image.open(os.path.join(M, 'dark_grimoire_leather.png')), 0.99, 0.9, 0.75).save(os.path.join(M, f'{name}_leather.png'))
tint_trim(name, (70, 70, 80))
ic = shift(Image.open(os.path.join(TEX, 'dark_grimoire.png')), 0.99, 0.95, 0.7); px = ic.load()
for (x, y, c) in ((7, 5, (220, 220, 228)), (7, 6, (220, 220, 228)), (7, 7, (255, 70, 60)), (7, 8, (220, 220, 228)), (7, 9, (220, 220, 228))):
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

# ------------------------------------------------------------------ text
LP = os.path.join(A_, 'lang/en_us.json')
lang = json.load(open(LP))
lang['item.iron_spell_grimoire.hunger_grimoire'] = 'Hunger Blade Grimoire'
lang['item.iron_spell_grimoire.hunger_blade'] = 'Hunger Blade'
lang['entity.iron_spell_grimoire.thrown_hunger_blade'] = 'Thrown Hunger Blade'
lang['ui.iron_spell_grimoire.hunger_grimoire_only'] = 'Only the Hunger Blade Grimoire can cast this.'
lang['tooltip.iron_spell_grimoire.hunger_grimoire.passive'] = ('Passive - the Hunger Blade: Draw it into your empty hand. Kills made with it feed it Growth, '
    'and Growth grows new swords - three base stages, then four paths (Bone, Crystal, Shadow, Flame), each splitting two ways: 23 in all. '
    'Carry four and switch between them on the G wheel. Keep it fed: well fed it steadies you, hungry it hits harder and pulls you to prey, '
    'starved it bites you. Fight together to deepen your Bond - at Bond 3 you can throw it, at Bond 5 Unleash wakes the Awakened Pact.')
lang['tooltip.iron_spell_grimoire.hunger_grimoire.desc'] = 'Five pages, and a sword that is always hungry.'
lang['tooltip.iron_spell_grimoire.hunger_grimoire.lore'] = 'Something inside it is chewing.'
for k, (title, guide) in {
 'draw_blade': ('Draw', 'Draw the Hunger Blade from the book into your empty main hand - the sword in your selected slot - or put it back.'),
 'gnash': ('Gnash', 'Lunge forward and bite the first foe in your way: heavy damage, a little health back. At Bond 3, sneak to throw the blade instead - it cuts all it passes and flies back to you.'),
 'feast': ('Feast', 'A cleave all the way round you, wider with every stage. Each foe it hits heals you (twice as much when the blade is hungry) and feeds it.'),
 'unleash': ('Unleash', 'The blade\'s ultimate, its own for each path: Devouring Bite (base), Ossuary Eruption (bone), Shard Nova (crystal), Umbral Rend (shadow) or Pyre Sweep (flame) - stronger with every stage. At Bond 5 it also wakes the Awakened Pact: 10 s of harder, faster hits that heal you.'),
 'the_blade': ('The Blade', 'Open The Blade: grow new swords with Growth, and choose the four you carry.'),
}.items():
    lang[f'spell.iron_spell_grimoire.{k}'] = f'Hunger: {title}'
    lang[f'spell.iron_spell_grimoire.{k}.guide'] = guide
json.dump(lang, open(LP, 'w'), indent=2, ensure_ascii=False)
print('hunger assets written')
