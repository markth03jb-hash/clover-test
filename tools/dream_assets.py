"""The Dream Grimoire (inspired by a sleepy Black Clover captain's Dream Magic; every look here is original): spell
icons, the cover, leather, trim, inventory icon, models, the curios tag and the text.
  Dream - night-lavender leather, cream trim, a sleeping crescent moon with a closed eye, a sheep-shaped cloud and
          gold stars; coral pink and peacock teal accents.
python3 tools/dream_assets.py"""
import json, os, sys
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
from icons import make
from _cover_helpers import *   # SC, W, H, Pp, poly, circ, ell, arc, frame, glow, shift, tint_trim, R, A_, TEX, M

# ------------------------------------------------------------------ spell icons (16x16: main, dark, metal, accent)
DR = ((150, 120, 230), (20, 14, 40), (255, 244, 230), (255, 150, 160))   # lavender on night, cream, coral
TL = ((60, 210, 200), (12, 24, 34), (255, 244, 230), (255, 214, 120))    # peacock teal, gold
def starp(x, y, k='w'): return [(k, 'p', x, y), (k, 'p', x - 1, y), (k, 'p', x + 1, y), (k, 'p', x, y - 1), (k, 'p', x, y + 1)]
make('dream_mote', *DR, starp(10, 5, 'a') + [('w', 'l', 2, 13, 5, 10), ('m', 'l', 5, 10, 8, 8), ('w', 'p', 4, 12), ('a', 'p', 7, 9)])
make('counting_sheep', *DR, [('w', 'E', 3, 6, 10, 11), ('w', 'E', 6, 5, 12, 10), ('d', 'E', 11, 6, 14, 9), ('m', 'l', 5, 11, 5, 14), ('m', 'l', 10, 11, 10, 14)])
make('pillow_fortress', *DR, [('w', 'a', 1, 3, 14, 18, 180, 360), ('a', 'E', 2, 11, 6, 15), ('w', 'E', 5, 10, 10, 15), ('a', 'E', 9, 11, 13, 15)])
make('sleepwalk', *DR, [('m', 'l', 2, 12, 9, 12), ('m', 'E', 9, 10, 12, 13), ('w', 'l', 10, 3, 13, 3), ('w', 'l', 13, 3, 10, 6), ('w', 'l', 10, 6, 13, 6)])
make('dream_clock', *TL, [('m', 'e', 1, 1, 14, 14), ('w', 'l', 7, 7, 7, 3), ('a', 'l', 7, 7, 4, 9), ('w', 'p', 7, 1), ('w', 'p', 14, 7), ('w', 'p', 7, 14), ('w', 'p', 1, 7)])
make('toy_parade', *DR, [('a', 'E', 2, 6, 7, 11), ('a', 'E', 2, 3, 4, 5), ('a', 'E', 5, 3, 7, 5), ('w', 'E', 8, 7, 13, 12), ('w', 'l', 9, 7, 9, 2), ('w', 'l', 12, 7, 12, 2)])
make('nightmare_shard', (110, 60, 170), (8, 4, 16), (255, 150, 160), (255, 90, 110), [('m', 'E', 4, 3, 11, 13), ('a', 'p', 6, 6), ('a', 'p', 9, 6), ('w', 'a', 0, 2, 15, 15, 300, 420)])
make('rude_awakening', *TL, [('a', 'E', 3, 3, 12, 12), ('d', 'E', 5, 5, 10, 10), ('w', 'l', 2, 2, 4, 1), ('w', 'l', 13, 2, 11, 1), ('m', 'l', 7, 7, 7, 5), ('w', 'l', 1, 14, 3, 12), ('w', 'l', 14, 14, 12, 12)])
make('half_asleep', *DR, [('w', 'a', 2, 2, 13, 13, 70, 330), ('a', 'l', 9, 4, 12, 4), ('a', 'l', 12, 4, 9, 7), ('a', 'l', 9, 7, 12, 7)] + starp(4, 12, 'a'))
make('dream_world', (230, 140, 170), (30, 18, 50), (255, 244, 220), (60, 220, 210), [('m', 'E', 1, 1, 14, 14), ('d', 'E', 5, 2, 15, 12), ('w', 'l', 4, 7, 7, 8)] + starp(12, 12, 'a') + starp(3, 13, 'w'))


# ------------------------------------------------------------------ cover
def cover(big, INK, LEA, LEA_SH, MET, MET_SH, MET_HI, GEM, draw):
    im = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(im)
    frame(d, INK, LEA, LEA_SH, MET, MET_SH, MET_HI, GEM)
    cy = 176 if big else 196
    im = draw(im, d, 128, cy, 1.0 if big else 0.62, big)
    return im.resize((256, 384), Image.LANCZOS)


def dream_draw(im, d, cx, cy, k, big):
    MOONC, NIGHTC, INKC, CREAM, CORAL, TEAL, GOLD = (255, 240, 196, 255), (40, 28, 78, 255), (16, 10, 32, 255), (255, 246, 232, 255), (255, 150, 160, 255), (60, 220, 210, 255), (255, 214, 110, 255)
    im = glow(im, cx, cy, ((84 * k, 30), (58 * k, 60)), (200, 170, 255)); d = ImageDraw.Draw(im)
    # a sleeping crescent moon: a full disc with a bite of the night taken out of it
    circ(d, cx - 6 * k, cy, 62 * k, fill=INKC)
    circ(d, cx - 6 * k, cy, 57 * k, fill=MOONC)
    circ(d, cx + 22 * k, cy - 18 * k, 50 * k, fill=NIGHTC)
    # its closed, sleeping eye and a little blush
    d.arc([Pp(cx - 46 * k, cy - 4 * k), Pp(cx - 18 * k, cy + 14 * k)], 20, 160, fill=INKC, width=int(4 * k * SC) or 1)
    circ(d, cx - 40 * k, cy + 22 * k, 6 * k, fill=CORAL)
    # a sheep-shaped cloud drifting across the foot of the moon
    for (x, y, r) in ((24, 44, 16), (40, 36, 18), (58, 42, 15), (36, 52, 14), (52, 54, 13)):
        circ(d, cx + x * k, cy + y * k, (r + 3) * k, fill=INKC)
    for (x, y, r) in ((24, 44, 16), (40, 36, 18), (58, 42, 15), (36, 52, 14), (52, 54, 13)):
        circ(d, cx + x * k, cy + y * k, r * k, fill=CREAM)
    circ(d, cx + 72 * k, cy + 42 * k, 8 * k, fill=INKC)   # its face
    # stars: gold and teal
    for (x, y, r, col) in ((48, -52, 7, GOLD), (70, -20, 5, TEAL), (-70, -54, 5, GOLD), (-64, 48, 4, TEAL), (16, -74, 4, CORAL)):
        poly(d, [(cx + x * k, cy + (y - r) * k), (cx + (x + r * 0.3) * k, cy + (y - r * 0.3) * k), (cx + (x + r) * k, cy + y * k), (cx + (x + r * 0.3) * k, cy + (y + r * 0.3) * k),
                 (cx + x * k, cy + (y + r) * k), (cx + (x - r * 0.3) * k, cy + (y + r * 0.3) * k), (cx + (x - r) * k, cy + y * k), (cx + (x - r * 0.3) * k, cy + (y - r * 0.3) * k)], col, None)
    return im


name = 'dream_grimoire'
fn = lambda b: cover(b, (16, 10, 32, 255), (78, 58, 140, 255), (52, 38, 100, 255), (255, 240, 222, 255), (186, 166, 150, 255), (255, 255, 250, 255), (60, 220, 210, 255), dream_draw)
fn(True).save(os.path.join(M, f'{name}_front.png'))
fn(False).save(os.path.join(M, f'{name}_back.png'))
shift(Image.open(os.path.join(M, 'dark_grimoire_leather.png')), 0.72, 0.45, 0.85).save(os.path.join(M, f'{name}_leather.png'))
tint_trim(name, (255, 240, 222))
ic = shift(Image.open(os.path.join(TEX, 'dark_grimoire.png')), 0.72, 0.5, 0.9); px = ic.load()
for (x, y, c) in ((7, 7, (255, 240, 196)), (8, 7, (255, 240, 196)), (7, 8, (255, 240, 196)), (8, 6, (60, 220, 210))):
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
lang['item.iron_spell_grimoire.dream_grimoire'] = 'Dream Grimoire'
lang['ui.iron_spell_grimoire.dream_grimoire_only'] = 'Only the Dream Grimoire can cast this.'
lang['tooltip.iron_spell_grimoire.dream_grimoire.passive'] = ('Passive - Drowsy: every Dream spell makes its targets drowsy and slower. Fully drowsy, they fall Asleep for 3 s - '
    'they can\'t move or hit back, and the first blow wakes them, landing 50% harder. And you: stand still for 2 s and you doze off - your mana comes back three times as fast, '
    'and you still see everything round you.')
lang['tooltip.iron_spell_grimoire.dream_grimoire.desc'] = 'Ten pages of a dream where whoever dreams it can make anything at all.'
lang['tooltip.iron_spell_grimoire.dream_grimoire.lore'] = 'It yawns when you open it.'
for k, (title, guide) in {
 'dream_mote': ('Lullaby Mote', 'A homing star mote drifts to the foe you aim at - a light hit that makes it much drowsier.'),
 'counting_sheep': ('Counting Sheep', 'Five dream sheep bound through everything in a line ahead, each one hitting and adding drowsiness.'),
 'pillow_fortress': ('Pillow Fortress', 'A soft dome of pillow clouds round you for 6 s: you are tougher inside, and anyone in it sinks into the pillows - slowed and drowsier every second.'),
 'sleepwalk': ('Sleepwalk', 'Blink seven blocks ahead and leave a sleeping you behind - it draws foes in, then puffs into sleep dust that hits and makes them very drowsy.'),
 'dream_clock': ('Dream Clock', 'A floating clock with its hands running backwards: everything round it moves and swings slowly for 5 s, and grows drowsy.'),
 'toy_parade': ('Toy Parade', 'A parade of plush dream toys waddles out ahead, bumping and knocking back everything in the way.'),
 'nightmare_shard': ('Nightmare Shard', 'A dark dream of the foe you aim at strikes it from behind - far harder if it is asleep.'),
 'rude_awakening': ('Rude Awakening', 'An alarm rings: every sleeping foe nearby wakes with a crash (a big hit), and drowsy ones take part of it.'),
 'half_asleep': ('Half Asleep', 'Mode, 20 s: you drift, dozing yet aware - star motes fly at nearby foes on their own, blows on you knock a toy loose to strike back, and your spells make foes twice as drowsy.'),
 'dream_world': ('Dream World', 'A pastel dream unfolds round you: everything inside is slowed and lulled to sleep in waves while toys tumble down on it, then a big sleepy moon comes down on them all.'),
}.items():
    lang[f'spell.iron_spell_grimoire.{k}'] = f'Dream: {title}'
    lang[f'spell.iron_spell_grimoire.{k}.guide'] = guide
json.dump(lang, open(LP, 'w'), indent=2, ensure_ascii=False)
print('dream assets written')
