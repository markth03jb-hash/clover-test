"""The Ink, Error and Treasure Grimoires (original grimoires): spell icons, covers, leather, trim, inventory icons,
models, the curios tag and the text. Original designs:
  Ink      - ink-black leather, paper-white metal, an open brush circle with a falling stroke and splashes
  Error    - black leather, gunmetal with magenta gems, a broken frame whose colours have slipped apart, dead pixels
  Treasure - deep gold-brown leather, bright gold, a key standing in a golden ripple with blade points round it
Cast animations reuse existing ones (see AddonSpellRegistry.ink / .error / .treasure)."""
import json, os, sys, math, random
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
from icons import make
from _cover_helpers import *   # SC, W, H, Pp, poly, circ, ell, arc, frame, glow, shift, tint_trim, R, A_, TEX, M

# ------------------------------------------------------------------ spell icons
I = ((44, 44, 70), (6, 6, 12), (12, 12, 20), (226, 220, 200))     # ink: black ink on a dark slate, paper-white accents
def stroke(pts, k='m'): return [(k, 'l', *pts[i], *pts[i + 1]) for i in range(len(pts) - 1)]
make('brush_stroke', (200, 194, 176), (120, 114, 100), (10, 10, 18), (60, 62, 140), [('m', 'a', 1, 2, 15, 16, 190, 330), ('m', 'a', 2, 3, 14, 15, 190, 330), ('m', 'a', 1, 3, 15, 15, 200, 320), ('a', 'p', 13, 5), ('m', 'p', 14, 4)])
make('ink_splash', (200, 194, 176), (120, 114, 100), (10, 10, 18), (60, 62, 140), [('m', 'E', 5, 5, 10, 10), ('m', 'E', 2, 3, 4, 5), ('m', 'E', 11, 2, 13, 4), ('m', 'E', 12, 11, 14, 13), ('m', 'p', 3, 12), ('m', 'l', 7, 10, 7, 14), ('a', 'p', 6, 6)])
make('ink_serpent', (200, 194, 176), (120, 114, 100), (10, 10, 18), (60, 62, 140), stroke([(1, 13), (4, 10), (7, 12), (10, 8), (12, 5)]) + stroke([(1, 12), (4, 9), (7, 11), (10, 7)]) + [('m', 'E', 11, 2, 14, 5), ('w', 'p', 13, 3)])
make('ink_blot', (200, 194, 176), (120, 114, 100), (10, 10, 18), (60, 62, 140), [('m', 'E', 2, 4, 13, 12), ('m', 'E', 4, 2, 10, 6), ('m', 'E', 0, 7, 3, 10), ('m', 'p', 14, 3), ('m', 'p', 1, 14), ('a', 'p', 5, 6)])
make('seal_script', (200, 194, 176), (120, 114, 100), (10, 10, 18), (60, 62, 140), [('m', 'e', 1, 1, 14, 14), ('m', 'l', 4, 5, 11, 5), ('m', 'l', 7, 3, 7, 12), ('m', 'l', 4, 9, 10, 11), ('a', 'p', 11, 5)])
make('ink_beasts', (200, 194, 176), (120, 114, 100), (10, 10, 18), (60, 62, 140), [('m', 'E', 4, 6, 12, 10), ('m', 'E', 10, 3, 14, 7), ('m', 'l', 11, 2, 11, 4), ('m', 'l', 13, 2, 13, 4), ('m', 'l', 5, 10, 4, 14), ('m', 'l', 7, 10, 7, 14), ('m', 'l', 10, 10, 11, 14), ('m', 'l', 4, 7, 1, 5), ('w', 'p', 12, 5)])
make('ink_rain', (200, 194, 176), (120, 114, 100), (10, 10, 18), (60, 62, 140), sum([[('m', 'l', x, y, x, y + 2), ('m', 'p', x, y + 3)] for x, y in ((2, 1), (6, 4), (10, 0), (13, 5), (4, 9), (9, 9))], []) + [('m', 'l', 0, 15, 15, 15)])
make('ink_wave', (200, 194, 176), (120, 114, 100), (10, 10, 18), (60, 62, 140), [('m', 'a', 2, 2, 14, 16, 180, 300), ('m', 'a', 3, 3, 13, 15, 180, 300), ('m', 'r', 0, 12, 15, 15), ('m', 'E', 11, 3, 14, 6), ('a', 'p', 4, 7)])
make('masterpiece', (200, 194, 176), (120, 114, 100), (10, 10, 18), (60, 62, 140), [('m', 'l', 2, 14, 10, 3), ('m', 'l', 3, 14, 11, 3), ('a', 'l', 10, 3, 13, 0), ('m', 'e', 6, 6, 14, 14), ('m', 'p', 9, 9)])
make('grand_calligraphy', (220, 214, 196), (140, 132, 116), (6, 6, 12), (90, 92, 200), [('m', 'l', 2, 3, 13, 3), ('m', 'l', 2, 4, 13, 4), ('m', 'l', 7, 3, 4, 13), ('m', 'l', 8, 3, 5, 13), ('m', 'l', 8, 7, 13, 13), ('m', 'l', 9, 7, 14, 13), ('a', 'p', 13, 4)])

E = ((30, 8, 34), (2, 0, 4), (255, 60, 200), (60, 240, 255))       # error: magenta and cyan on black
make('glitch_bolt', *E, [('m', 'l', 1, 13, 5, 13), ('m', 'l', 5, 13, 5, 8), ('m', 'l', 5, 8, 10, 8), ('m', 'l', 10, 8, 10, 3), ('m', 'l', 10, 3, 14, 3), ('a', 'l', 2, 12, 6, 12), ('a', 'l', 6, 12, 6, 7), ('a', 'l', 6, 7, 11, 7), ('w', 'p', 14, 3)])
make('frame_skip', *E, [('a', 'r', 1, 5, 3, 11), ('m', 'r', 6, 5, 8, 11), ('w', 'r', 11, 5, 13, 11), ('d', 'p', 7, 8), ('m', 'p', 4, 3), ('a', 'p', 9, 13)])
make('corrupted_threads', *E, [('a', 'l', 0, 3, 15, 7), ('m', 'l', 0, 6, 15, 9), ('a', 'l', 0, 10, 15, 8), ('m', 'l', 0, 13, 15, 6), ('w', 'e', 10, 5, 14, 10)])
make('lag_spike', *E, [('w', 'R', 4, 2, 11, 13), ('m', 'R', 3, 2, 10, 13), ('a', 'R', 5, 2, 12, 13), ('w', 'l', 4, 6, 11, 6), ('w', 'l', 4, 9, 11, 9)])
make('copy_paste', *E, [('a', 'R', 1, 1, 8, 8), ('m', 'R', 7, 7, 14, 14), ('w', 'r', 3, 3, 6, 6), ('w', 'r', 9, 9, 12, 12), ('a', 'p', 13, 2), ('m', 'p', 2, 13)])
make('crash_zone', *E, [('a', 'r', 1, 11, 3, 13), ('m', 'r', 5, 9, 7, 11), ('w', 'r', 9, 12, 11, 14), ('a', 'r', 12, 8, 14, 10), ('m', 'e', 0, 6, 15, 15), ('a', 'p', 7, 3)])
make('error_windows', *E, [('w', 'R', 1, 3, 14, 13), ('m', 'r', 1, 3, 14, 5), ('w', 'p', 13, 4), ('a', 'l', 3, 8, 10, 8), ('a', 'l', 3, 10, 8, 10), ('m', 'E', 10, 8, 12, 11)])
make('blue_screen', (8, 20, 70), (0, 2, 12), (40, 90, 255), (220, 230, 255), [('m', 'r', 2, 2, 13, 13), ('d', 'r', 4, 4, 6, 6), ('d', 'r', 9, 5, 11, 7), ('a', 'l', 4, 9, 11, 9), ('a', 'l', 4, 11, 9, 11)])
make('fatal_error', *E, [('w', 'g', [(7, 1), (14, 14), (0, 14)]), ('d', 'g', [(7, 4), (12, 13), (2, 13)]), ('m', 'l', 7, 6, 7, 10), ('m', 'p', 7, 12), ('a', 'p', 1, 2), ('a', 'p', 13, 1)])
make('system_crash', (60, 10, 50), (2, 0, 4), (255, 60, 200), (255, 255, 255), [('d', 'r', 3, 0, 5, 15), ('d', 'r', 9, 2, 12, 15), ('m', 'l', 2, 0, 2, 15), ('a', 'l', 6, 0, 6, 15), ('m', 'l', 8, 2, 8, 15), ('a', 'l', 13, 2, 13, 15), ('w', 'p', 7, 7)])

T = ((90, 60, 10), (20, 12, 2), (255, 200, 70), (255, 245, 210))   # treasure: gold
def ripple(x, y, r): return [('m', 'e', x - r, y - r, x + r, y + r), ('a', 'e', x - r + 1, y - r + 1, x + r - 1, y + r - 1)]
def sword(x0, y0, x1, y1): return [('w', 'l', x0, y0, x1, y1)]
make('vault_volley', *T, ripple(3, 4, 2) + ripple(12, 4, 2) + ripple(3, 11, 2) + ripple(12, 11, 2) + sword(5, 5, 9, 8) + sword(10, 5, 8, 7))
make('gilded_spear', *T, ripple(3, 12, 3) + [('w', 'l', 5, 10, 14, 1), ('a', 'g', [(14, 1), (11, 2), (13, 4)])])
make('vault_chains', *T, [('m', 'e', 1, 1, 4, 3), ('m', 'e', 3, 3, 6, 5), ('m', 'e', 5, 5, 8, 7), ('m', 'e', 7, 7, 10, 9), ('m', 'e', 9, 9, 12, 11), ('m', 'e', 11, 11, 14, 13), ('w', 'p', 7, 6)])
make('blade_wall', *T, sum([sword(x, 3, x + 2, 13) + sword(x + 2, 3, x, 13) for x in (1, 6, 11)], []) + [('m', 'l', 0, 14, 15, 14)])
make('blade_rain', *T, ripple(7, 2, 2) + sword(3, 5, 3, 10) + sword(7, 6, 7, 13) + sword(11, 5, 11, 10) + [('m', 'l', 0, 15, 15, 15)])
make('seeking_blades', *T, ripple(2, 7, 2) + sword(4, 6, 13, 2) + sword(4, 7, 14, 7) + sword(4, 8, 13, 12) + [('a', 'p', 13, 2), ('a', 'p', 14, 7), ('a', 'p', 13, 12)])
make('treasure_barrage', *T, sum([ripple(2, y, 1) for y in (2, 7, 12)], []) + sum([sword(4, y, 14, y) for y in (2, 7, 12)], []) + sword(4, 5, 12, 5) + sword(4, 10, 12, 10))
make('vault_aegis', *T, [('m', 'g', [(7, 1), (13, 3), (12, 10), (7, 14), (2, 10), (1, 3)]), ('d', 'g', [(7, 3), (11, 4), (10, 9), (7, 12), (4, 9), (3, 4)]), ('w', 'l', 7, 4, 7, 11), ('a', 'l', 4, 6, 10, 6)])
make('kings_vault', *T, ripple(3, 3, 2) + ripple(8, 2, 2) + ripple(13, 3, 2) + ripple(5, 8, 2) + ripple(11, 8, 2) + [('w', 'g', [(4, 12), (7, 10), (8, 12), (11, 10), (12, 14), (3, 14)])])
make('vault_breaker', (120, 80, 10), (30, 10, 2), (255, 210, 80), (255, 255, 255), [('w', 'l', 2, 13, 9, 6), ('m', 'e', 8, 1, 14, 7), ('d', 'e', 10, 3, 12, 5), ('m', 'l', 4, 11, 2, 9), ('m', 'l', 6, 9, 4, 7), ('a', 'a', 0, 0, 15, 15, 300, 420)])

# ------------------------------------------------------------------ covers
def ink_cover(big):
    INK = (4, 4, 8, 255); LEA, LEA_SH = (16, 16, 26, 255), (10, 10, 18, 255)
    MET, MET_SH, MET_HI = (220, 210, 186, 255), (150, 140, 120, 255), (246, 240, 222, 255)
    PAPER, INDIGO = (236, 228, 206, 255), (100, 106, 255, 255)
    im = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(im)
    frame(d, INK, LEA, LEA_SH, MET, MET_SH, MET_HI, INDIGO)
    cx, cy, Rr = 128, (172 if big else 192), (66 if big else 42)
    rnd = random.Random(5)
    # an open brush circle painted in pale ink: thick, thinning, a gap at the top right
    for i in range(300):
        a0 = math.radians(-50 + i * 1.05)
        if i > 288: break
        w = 13 * math.sin(math.pi * min(1, i / 288)) ** 0.6 + 2
        x, y = cx + math.cos(a0) * Rr, cy + math.sin(a0) * Rr
        circ(d, x + rnd.uniform(-0.6, 0.6), y + rnd.uniform(-0.6, 0.6), w * 0.5, fill=PAPER)
    if big:
        for i in range(80):   # a stroke falling through it
            y = 110 + i * 2.4
            w = 9 * (1 - i / 80) + 1.5
            circ(d, cx + 18 * math.sin(i * 0.05) - 6, y, w * 0.5, fill=PAPER)
        for (x, y, r) in ((60, 280, 6), (192, 96, 4), (205, 270, 5), (72, 104, 3), (176, 300, 3)):
            circ(d, x, y, r, fill=PAPER)
        circ(d, 128, 308, 5, fill=INDIGO)
    return im.resize((256, 384), Image.LANCZOS)

def error_cover(big):
    INK = (0, 0, 0, 255); LEA, LEA_SH = (12, 6, 14, 255), (6, 2, 8, 255)
    MET, MET_SH, MET_HI = (80, 74, 92, 255), (44, 40, 54, 255), (130, 124, 146, 255)
    MAG, CYA, WH = (255, 60, 200, 255), (60, 240, 255, 255), (255, 255, 255, 255)
    im = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(im)
    frame(d, INK, LEA, LEA_SH, MET, MET_SH, MET_HI, MAG)
    cx, cy, s = 128, (176 if big else 192), (62 if big else 40)
    def broken(col, ox, oy, w):
        pts = [(cx - s, cy - s), (cx + s * 0.4, cy - s)], [(cx + s, cy - s * 0.5), (cx + s, cy + s)], [(cx + s, cy + s), (cx - s * 0.2, cy + s)], [(cx - s, cy + s * 0.6), (cx - s, cy - s)]
        for (a, b) in pts:
            d.line([Pp(a[0] + ox, a[1] + oy), Pp(b[0] + ox, b[1] + oy)], fill=col, width=int(w * SC))
    broken(MAG, -6, 0, 8); broken(CYA, 6, 0, 8); broken(WH, 0, 0, 4)
    rnd = random.Random(9)
    for i in range(26 if big else 10):   # dead pixels
        x, y, q = rnd.uniform(40, 216), rnd.uniform(60, 330 if big else 260), rnd.choice((6, 8, 10, 12))
        col = rnd.choice((INK, INK, MAG, CYA, WH))
        d.rectangle([Pp(x, y), Pp(x + q, y + q)], fill=col)
    if big:   # a warning mark in the middle
        poly(d, [(cx, cy - 26), (cx + 26, cy + 20), (cx - 26, cy + 20)], MAG, INK, 3)
        d.rectangle([Pp(cx - 3, cy - 10), Pp(cx + 3, cy + 6)], fill=INK); d.rectangle([Pp(cx - 3, cy + 10), Pp(cx + 3, cy + 15)], fill=INK)
    return im.resize((256, 384), Image.LANCZOS)

def treasure_cover(big):
    INK = (24, 12, 2, 255); LEA, LEA_SH = (74, 48, 10, 255), (50, 30, 6, 255)
    MET, MET_SH, MET_HI = (240, 196, 80, 255), (170, 124, 30, 255), (255, 236, 160, 255)
    GOLD, PALE, STEEL = (255, 200, 70, 255), (255, 246, 210, 255), (214, 220, 232, 255)
    im = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(im)
    frame(d, INK, LEA, LEA_SH, MET, MET_SH, MET_HI, (220, 40, 40, 255))
    cx, cy, Rr = 128, (180 if big else 192), (64 if big else 42)
    im = glow(im, cx, cy, ((Rr + 40, 30), (Rr + 20, 50)), (255, 200, 80)); d = ImageDraw.Draw(im)
    for k in range(3):   # the ripple
        circ(d, cx, cy, Rr - k * 14, outline=INK, w=8); circ(d, cx, cy, Rr - k * 14, outline=GOLD if k % 2 == 0 else PALE, w=4)
    if big:
        for i in range(8):   # blade points coming out round it
            a = i * math.pi / 4 + math.pi / 8
            x0, y0 = cx + math.cos(a) * (Rr + 6), cy + math.sin(a) * (Rr + 6)
            x1, y1 = cx + math.cos(a) * (Rr + 34), cy + math.sin(a) * (Rr + 34)
            nx, ny = -math.sin(a) * 6, math.cos(a) * 6
            poly(d, [(x0 + nx, y0 + ny), (x1, y1), (x0 - nx, y0 - ny)], STEEL, INK, 2)
    # the key, standing in it
    kw = 1.0 if big else 0.65
    def K(x, y): return (cx + (x - 128) * kw, cy + (y - 180) * kw)
    circ(d, *K(128, 112), 26 * kw, fill=GOLD, outline=INK, w=3); circ(d, *K(128, 112), 11 * kw, fill=LEA)
    poly(d, [K(121, 136), K(135, 136), K(135, 252), K(121, 252)], GOLD, INK, 3)
    poly(d, [K(135, 214), K(156, 214), K(156, 226), K(135, 226)], GOLD, INK, 3)
    poly(d, [K(135, 236), K(150, 236), K(150, 248), K(135, 248)], GOLD, INK, 3)
    poly(d, [K(124, 140), K(127, 140), K(127, 248), K(124, 248)], PALE, None)
    return im.resize((256, 384), Image.LANCZOS)

for name, fn, hue, sm, vm, trim, ic_marks in (
        ('ink_grimoire', ink_cover, 0.68, 0.4, 0.45, (226, 216, 192), ((7, 7, (236, 228, 206)), (8, 7, (236, 228, 206)), (7, 8, (236, 228, 206)), (8, 9, (100, 106, 255)))),
        ('error_grimoire', error_cover, 0.85, 0.5, 0.4, (96, 90, 110), ((6, 7, (255, 60, 200)), (7, 7, (255, 255, 255)), (8, 7, (60, 240, 255)), (7, 9, (255, 60, 200)), (8, 8, (0, 0, 0)))),
        ('treasure_grimoire', treasure_cover, 0.11, 1.3, 1.0, (250, 206, 96), ((7, 6, (255, 246, 210)), (8, 6, (255, 246, 210)), (7, 7, (255, 200, 70)), (7, 8, (255, 200, 70)), (8, 8, (255, 200, 70)), (7, 9, (255, 200, 70))))):
    fn(True).save(os.path.join(M, f'{name}_front.png'))
    fn(False).save(os.path.join(M, f'{name}_back.png'))
    shift(Image.open(os.path.join(M, 'dark_grimoire_leather.png')), hue, sm, vm).save(os.path.join(M, f'{name}_leather.png'))
    tint_trim(name, trim)
    ic = shift(Image.open(os.path.join(TEX, 'dark_grimoire.png')), hue, sm, vm + 0.15); px = ic.load()
    for (x, y, c) in ic_marks:
        if px[x, y][3]: px[x, y] = c + (255,)
    ic.save(os.path.join(TEX, f'{name}.png'))

MD = os.path.join(A_, 'models/item')
CT = os.path.join(R, 'data/curios/tags/items/spellbook.json')
c = json.load(open(CT))
for name in ('ink_grimoire', 'error_grimoire', 'treasure_grimoire'):
    for suf in ('', '_model', '_open'):
        s = open(os.path.join(MD, f'light_grimoire{suf}.json')).read().replace('light_grimoire', name)
        open(os.path.join(MD, f'{name}{suf}.json'), 'w').write(s)
    if f'iron_spell_grimoire:{name}' not in c['values']: c['values'].append(f'iron_spell_grimoire:{name}')
json.dump(c, open(CT, 'w'), indent=2)

# ------------------------------------------------------------------ lang
LP = os.path.join(A_, 'lang/en_us.json')
lang = json.load(open(LP))
def spells(school, dd):
    for k, (title, guide) in dd.items():
        lang[f'spell.iron_spell_grimoire.{k}'] = f'{school}: {title}'
        lang[f'spell.iron_spell_grimoire.{k}.guide'] = guide
for key, name, passive, desc, lore in (
        ('ink', 'Ink Grimoire', 'Passive - Signature: every Ink spell is a stroke, and every third completes a seal that bursts on the nearest foe by itself. Ink also stains: each stain on a foe makes your Ink spells hit it 4% harder (up to five).',
         'Ten pages of Ink Magic: brush strokes, splashes, painted serpents and beasts, seals that burst, and a grand character falling from the sky.', 'The pages are blank until you dip the brush. Then they will not stop writing.'),
        ('error', 'Error Grimoire', 'Passive - Duplicate: one Error hit in five is duplicated, striking again for half.',
         'Ten pages of corrupted magic: bolts that skip, frames that freeze, windows that slam shut, and reality crashing outright.', 'Some of its pages are missing. Some of them are there twice.'),
        ('treasure', 'Treasure Grimoire', 'Passive - Open Vault: every Treasure spell leaves one more portal open (up to five, for 8 s), and each open portal makes your weapons hit 5% harder.',
         'Ten pages that are really a key: portals of gold open behind you and weapons pour out of them - volleys, chains, walls of blades, and the vault breaker.', 'Nobody knows what is in the vault. The book just knows where the door is.')):
    lang[f'item.iron_spell_grimoire.{key}_grimoire'] = name
    lang[f'ui.iron_spell_grimoire.{key}_grimoire_only'] = f'Only the {name} can cast this.'
    lang[f'tooltip.iron_spell_grimoire.{key}_grimoire.passive'] = passive
    lang[f'tooltip.iron_spell_grimoire.{key}_grimoire.desc'] = desc
    lang[f'tooltip.iron_spell_grimoire.{key}_grimoire.lore'] = lore
spells('Ink Magic', {
 'brush_stroke': ('Brush Stroke', 'One sweeping stroke of ink across everything in front of you.'),
 'ink_splash': ('Ink Splash', 'Lob five globs of ink onto your target; each splatters everything round where it lands.'),
 'ink_serpent': ('Ink Serpent', 'A painted serpent slithers along the ground in front of you, biting everything on its way.'),
 'ink_blot': ('Ink Blot', 'A great blot spreads under your target for 5 s: foes in it are stained, slowed and soaked.'),
 'seal_script': ('Seal Script', 'Write a seal on the ground; a moment later it bursts up in a column of characters.'),
 'ink_beasts': ('Ink Beasts', 'Three ink wolves are painted at your side and run down the nearest foes.'),
 'ink_rain': ('Ink Rain', 'Ink rains on the ground round your target for three seconds.'),
 'ink_wave': ('Ink Wave', 'A wave of ink rolls forward and carries foes along with it.'),
 'masterpiece': ('Masterpiece', 'Mode, 20 s: everything you paint is painted twice - the copy follows a moment later at 60%.'),
 'grand_calligraphy': ('Grand Calligraphy', 'A giant character is painted stroke by stroke in the sky, then falls on your target.'),
})
spells('Error Magic', {
 'glitch_bolt': ('Glitch Bolt', 'A bolt that skips toward your target in jagged jumps, hurting everything on its line.'),
 'frame_skip': ('Frame Skip', 'Skip ahead about ten blocks in three jumps, glitching through everything in between.'),
 'corrupted_threads': ('Corrupted Threads', 'Glitching threads lash the foe you aim at and hold it in place, cutting it as they hold.'),
 'lag_spike': ('Lag Spike', 'Foes in front of you lag: they freeze for a moment, then snap back to where they stood and take the hit.'),
 'copy_paste': ('Copy Paste', 'A strike on your target, then the same strike pasted onto up to three other foes nearby.'),
 'crash_zone': ('Crash Zone', 'For 4 s, everything in a circle stutters, slows and takes damage.'),
 'error_windows': ('Error Windows', 'Error windows pop up round your target and slam shut on it.'),
 'blue_screen': ('Blue Screen', 'A cube of dead pixels forms round your target, holds everything in it, and crushes it.'),
 'fatal_error': ('Fatal Error', 'Mode, 20 s: each Error spell may fire a second time, your cooldowns skip ahead every cast, and you move faster.'),
 'system_crash': ('System Crash', 'Reality tears open round your target; everything in the tears stutters, then crashes.'),
})
spells('Treasure Magic', {
 'vault_volley': ('Vault Volley', 'Four golden portals open behind you, and each fires a weapon at your target.'),
 'gilded_spear': ('Gilded Spear', 'One heavy spear from one portal, straight through everything in a line.'),
 'vault_chains': ('Vault Chains', 'Chains shoot from four portals round your target and hold it fast.'),
 'blade_wall': ('Blade Wall', '6 s: a wall of crossed blades stands in front of you. Missiles break on it and foes that touch it are cut.'),
 'blade_rain': ('Blade Rain', 'Portals open in the sky over your target and blades rain down for two seconds.'),
 'seeking_blades': ('Seeking Blades', 'Six weapons fly out of the vault, each hunting a different foe.'),
 'treasure_barrage': ('Treasure Barrage', 'Ten portals open behind you and fire as fast as they can at your target for two seconds.'),
 'vault_aegis': ('Vault Aegis', '10 s: a golden shield. You take 30% less damage, and every blow is answered by a sword from the vault.'),
 'kings_vault': ('King\'s Vault', 'Mode, 20 s: portals fill the sky behind you and fire on their own at foes near you. Treasure spells hit 25% harder.'),
 'vault_breaker': ('Vault Breaker', 'The vault\'s key turns: a spiral of tearing golden wind rips forward through everything in its path.'),
})
json.dump(lang, open(LP, 'w'), indent=2, ensure_ascii=False)
print('ink, error and treasure assets ok')
