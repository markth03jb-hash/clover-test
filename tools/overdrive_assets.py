"""The Overdrive Grimoire (an original grimoire) and its sword, the Heartline: spell icons, the cover, leather, trim,
inventory icon, models, the curios tag, the sword's 3D model and texture, and the text. Original designs:
  Overdrive - black leather, bone-white metal, a crimson heart with a heartbeat trace running through it
  Heartline - a black katana, inspired by an anime swordsman's black blade but with its own details: jet-black steel
              with a thin bright edge and a crimson heartbeat trace down the flat, an oval guard and a brass collar, a
              long grip of black cord over white diamonds, a red tassel at the pommel - a classic katana build. (The model is made at half size and
              drawn to scale - with the grip at the model's centre so it sits
              in the hand; the hotbar shows a flat drawn icon.)
python3 tools/overdrive_assets.py"""
import json, os, sys, math
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
from icons import make
from _cover_helpers import *   # SC, W, H, Pp, poly, circ, ell, arc, frame, glow, shift, tint_trim, R, A_, TEX, M

# ------------------------------------------------------------------ spell icons (16x16: main, dark, metal, accent)
OD = ((150, 16, 30), (12, 2, 4), (236, 232, 222), (255, 60, 70))     # crimson on black, bone white, hot red
def ecg(y=8, x0=1, x1=14, k='a'):   # a little heartbeat trace
    pts = [(x0, y), (x0 + 4, y), (x0 + 5, y - 1), (x0 + 6, y + 2), (x0 + 7, y - 5), (x0 + 8, y + 3), (x0 + 9, y), (x1, y)]
    return [(k, 'l', *pts[i], *pts[i + 1]) for i in range(len(pts) - 1)]
def blade(x0, y0, x1, y1): return [('w', 'l', x0, y0, x1, y1), ('m', 'l', x0 + 1, y0, x1 + 1, y1)]
make('draw_heartline', *OD, [('m', 'E', 4, 8, 9, 13), ('a', 'p', 6, 10)] + blade(7, 9, 13, 2) + [('w', 'p', 13, 1), ('d', 'l', 3, 14, 6, 11)])
make('pulse_cut', *OD, [('a', 'l', 0, 8, 15, 8), ('w', 'l', 0, 7, 15, 7)] + blade(2, 12, 6, 9) + [('m', 'l', 9, 3, 11, 5), ('m', 'l', 11, 5, 10, 6), ('m', 'l', 10, 6, 12, 8), ('w', 'p', 15, 7)])   # a flash of a draw-cut, the crack after it
make('adrenaline_rush', *OD, [('m', 'l', 1, y, 9, y) for y in (4, 8, 12)] + [('w', 'E', 10, 5, 14, 11), ('a', 'p', 12, 7)])
make('heartbeat', *OD, [('w', 'a', 0, 3, 15, 16, 205, 335), ('w', 'a', 0, -1, 15, 12, 25, 155), ('m', 'E', 5, 5, 10, 10), ('a', 'E', 6, 6, 9, 9), ('w', 'p', 6, 6)])   # an eye reading a pulse
make('flatline', *OD, [('w', 'l', 1, 9, 14, 9), ('a', 'l', 3, 9, 4, 6), ('a', 'l', 4, 6, 5, 9)] + blade(1, 5, 14, 5))
make('tachycardia', *OD, [('w', 'l', 2, 3, 13, 12), ('w', 'l', 2, 12, 13, 3), ('m', 'l', 7, 1, 7, 14), ('a', 'l', 1, 7, 14, 7)])
make('second_wind', (60, 90, 110), (6, 10, 14), (236, 232, 222), (255, 160, 170), [('w', 'a', 2, 2, 13, 13, 200, 520), ('w', 'a', 5, 5, 10, 10, 200, 520), ('a', 'p', 7, 7)])
make('ribbon_bind', *OD, [('w', 'a', 1, 4, 9, 12, 180, 360), ('w', 'a', 6, 4, 14, 12, 0, 180), ('m', 'E', 10, 2, 14, 6), ('a', 'p', 12, 4)])
make('redline', (180, 20, 30), (20, 2, 4), (255, 200, 200), (255, 255, 255), [('d', 'E', 1, 1, 14, 14), ('m', 'e', 1, 1, 14, 14), ('w', 'l', 7, 7, 7, 2), ('a', 'l', 7, 7, 11, 7), ('a', 'p', 7, 1), ('a', 'p', 14, 7), ('a', 'p', 7, 14), ('a', 'p', 1, 7)])   # one minute on the clock
make('final_beat', (40, 6, 10), (2, 0, 0), (236, 232, 222), (255, 40, 50), [('a', 'l', 0, 9, 15, 9)] + blade(1, 13, 14, 2) + [('w', 'p', 14, 1), ('a', 'p', 7, 8)])


# ------------------------------------------------------------------ cover
def cover(big, INK, LEA, LEA_SH, MET, MET_SH, MET_HI, GEM, draw):
    im = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(im)
    frame(d, INK, LEA, LEA_SH, MET, MET_SH, MET_HI, GEM)
    cy = 176 if big else 196
    im = draw(im, d, 128, cy, 1.0 if big else 0.62, big)
    return im.resize((256, 384), Image.LANCZOS)


def overdrive_draw(im, d, cx, cy, k, big):
    RED, DEEP, WHITE, INK = (210, 20, 36, 255), (110, 6, 16, 255), (240, 236, 226, 255), (6, 2, 2, 255)
    im = glow(im, cx, cy, ((80 * k, 30), (56 * k, 70)), (255, 40, 50)); d = ImageDraw.Draw(im)
    # the heart: two lobes and a point
    for sx in (-1, 1):
        circ(d, cx + sx * 26 * k, cy - 14 * k, 34 * k, fill=INK)
    poly(d, [(cx - 58 * k, cy - 6 * k), (cx + 58 * k, cy - 6 * k), (cx, cy + 66 * k)], INK, None)
    for sx in (-1, 1):
        circ(d, cx + sx * 26 * k, cy - 14 * k, 30 * k, fill=RED)
    poly(d, [(cx - 54 * k, cy - 6 * k), (cx + 54 * k, cy - 6 * k), (cx, cy + 60 * k)], RED, None)
    circ(d, cx - 30 * k, cy - 22 * k, 10 * k, fill=(255, 120, 130, 255))   # its shine
    # the heartbeat trace running right through it
    pts = [(-110, 0), (-30, 0), (-22, -8), (-14, 10), (-6, -46), (4, 30), (12, 0), (40, 0), (48, -10), (58, 0), (110, 0)]
    pts = [(cx + x * k, cy + y * k) for x, y in pts]
    for i in range(len(pts) - 1):
        d.line([Pp(*pts[i]), Pp(*pts[i + 1])], fill=INK, width=int(9 * k * SC) or 1)
    for i in range(len(pts) - 1):
        d.line([Pp(*pts[i]), Pp(*pts[i + 1])], fill=WHITE, width=int(4 * k * SC) or 1)
    if big:   # a faint blade across the foot of the cover
        poly(d, [(40, 316), (220, 296), (224, 300), (44, 322)], (180, 184, 190, 255), INK, 2)
    return im


name = 'overdrive_grimoire'
fn = lambda b: cover(b, (6, 2, 2, 255), (22, 6, 8, 255), (12, 2, 4, 255), (236, 232, 222, 255), (150, 146, 138, 255), (255, 255, 250, 255), (210, 20, 36, 255), overdrive_draw)
fn(True).save(os.path.join(M, f'{name}_front.png'))
fn(False).save(os.path.join(M, f'{name}_back.png'))
shift(Image.open(os.path.join(M, 'dark_grimoire_leather.png')), 0.0, 0.7, 0.4).save(os.path.join(M, f'{name}_leather.png'))
tint_trim(name, (236, 232, 222))
ic = shift(Image.open(os.path.join(TEX, 'dark_grimoire.png')), 0.0, 0.8, 0.55); px = ic.load()
for (x, y, c) in ((7, 7, (230, 30, 46)), (8, 7, (230, 30, 46)), (7, 8, (230, 30, 46)), (6, 7, (240, 236, 226)), (9, 7, (240, 236, 226))):
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


# ------------------------------------------------------------------ the Heartline: texture (32x32 = uv 0-16, 2 px per unit) and model
T = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); td = ImageDraw.Draw(T)
# The Heartline: a black katana (inspired by an anime swordsman's black blade - the details are its own): a jet-black
# blade with a gunmetal sheen and a thin bright edge, and instead of a temper line a crimson heartbeat trace running
# its length; a charcoal grip wrap with crimson diamonds showing through, a black guard ringed in red, a red tassel.
for y in range(32):   # the flat of the blade (uv 0-4): black spine, a ridge line, black steel, then a bright wavy-tempered edge band
    hamon = 5 + (1 if (y // 3) % 3 == 1 else 0)
    for x in range(8):
        if x < 2: col = (14, 14, 18)                                    # the spine
        elif x == 2: col = (70, 72, 84)                                  # the ridge line
        elif x < hamon: col = (30, 31, 38) if (x + y) % 6 else (46, 48, 58)   # black steel with a sheen
        elif x == hamon: col = (236, 240, 248)                           # the temper line
        else: col = (196, 204, 218) if (x + y) % 4 else (214, 222, 236)  # the polished edge
        T.putpixel((x, y), col + (255,))
    T.putpixel((3, y), (200, 22, 38, 255))                              # the crimson heartbeat: a fine line...
    if y % 16 == 6:                                                     # ...and a beat, spiking across the black
        for x in range(2, 5): T.putpixel((x, y), (240, 40, 56, 255))
    if y % 16 in (5, 7): T.putpixel((4, y), (220, 24, 40, 255))
for y in range(32):   # the blade's back and edge strips (uv 4-5)
    T.putpixel((8, y), (12, 12, 16, 255)); T.putpixel((9, y), (214, 220, 232, 255))
for y in range(32):   # the grip (uv 6-10): black silk cord wrapped over white ray-skin - white diamonds between the crossings
    for x in range(12, 20):
        u, v = x - 12, y % 8
        d = abs(u - 4) + abs(v - 4)
        if d <= 2: col = (238, 234, 224) if (u + v) % 2 else (224, 220, 208)   # the white skin showing through
        elif d == 3: col = (60, 58, 62)                                         # the cord's edge
        else: col = (20, 19, 22) if (u + y) % 5 else (36, 34, 40)               # the black cord
        T.putpixel((x, y), col + (255,))
for y in range(8):   # the guard and the pommel cap (uv 11-13, 0-4): dark iron, a red line round the rim
    for x in range(22, 26):
        edge = y in (0, 7) or x in (22, 25)
        T.putpixel((x, y), ((120, 16, 26) if edge else ((22, 22, 26) if (x + y) % 3 else (38, 38, 44))) + (255,))
for y in range(8, 16):   # brass (uv 11-13, 4-8): the collar at the blade's base
    for x in range(22, 26):
        T.putpixel((x, y), ((196, 152, 64) if (x + y) % 3 else (232, 196, 110)) + (255,))
for y in range(32):   # the tassel (uv 14-16): red cord with soft folds
    for x in range(28, 32):
        f = 190 - (30 if (y // 3) % 2 else 0) - (14 if x == 31 else 0)
        T.putpixel((x, y), (f, int(f * 0.1), int(f * 0.16), 255))
T.save(os.path.join(TEX, 'heartline.png'))

def face(u0, v0, u1, v1, rot=0):
    f = {"uv": [u0, v0, u1, v1], "texture": "#0"}
    if rot: f["rotation"] = rot
    return f

def el(fr, to, faces, rot=None, name=None):
    e = {"from": fr, "to": to, "faces": faces}
    if rot: e["rotation"] = rot
    if name: e["name"] = name
    return e

BLADE = lambda: {"east": face(0, 0, 4, 16), "west": face(4, 0, 0, 16), "north": face(4, 0, 5, 16), "south": face(4, 0, 5, 16),
                 "up": face(4, 0, 5, 1), "down": face(4, 0, 5, 1)}
GRIP = lambda: {d: face(6, 0, 10, 16) for d in ("north", "south", "east", "west")} | {"up": face(11, 0, 13, 2), "down": face(11, 0, 13, 2)}
METAL = lambda: {d: face(11, 0, 13, 4) for d in ("north", "south", "east", "west", "up", "down")}
BRASS = lambda: {d: face(11, 4, 13, 8) for d in ("north", "south", "east", "west", "up", "down")}
CLOTH = lambda: {d: face(14, 0, 16, 16) for d in ("north", "south", "east", "west", "up", "down")}
els = [   # katana proportions: a long two-hand grip, an oval guard, a brass collar, a blade about two and a half grips long
    el([7.4, 4.6, 7.35], [8.6, 5.0, 8.65], METAL(), name="pommel"),
    el([7.5, 5.0, 7.45], [8.5, 11.0, 8.55], GRIP(), name="grip"),                                  # the grip's centre is the model's centre
    el([6.9, 11.0, 6.3], [9.1, 11.4, 9.7], METAL(), name="guard"),                                   # the oval guard
    el([7.25, 11.0, 5.95], [8.75, 11.4, 10.05], METAL(), name="guard_ends"),                         # its rounded ends
    el([7.75, 11.4, 7.3], [8.25, 12.2, 8.75], BRASS(), name="collar"),
]
# the blade: many thin sections following one smooth curve (item models only turn in 22.5-degree steps, so the curve is
# built from small offsets instead), with the texture running on continuously from the collar to the point. The spine
# curves back gently (more toward the tip); near the end the edge sweeps up to meet the spine in a sharp point (kissaki).
B0, B1, P0 = 12.2, 27.2, 24.6            # blade start, the very tip, where the point begins
def spine_z(y): t = (y - B0) / (B1 - B0); return 8.65 + 0.95 * t * t
def width(y):
    t = (y - B0) / (B1 - B0)
    w = 1.3 - 0.2 * t
    if y > P0: w *= max(0.0, 1 - ((y - P0) / (B1 - P0)) ** 1.35)   # the point: narrowing smoothly to nothing
    return w
def vv(y): return round(16 * (y - B0) / (B1 - B0), 3)
cuts = [B0 + (P0 - B0) * i / 26 for i in range(26)] + [P0 + (B1 - P0) * i / 12 for i in range(13)]
for i, (y0, y1) in enumerate(zip(cuts, cuts[1:])):
    ym = (y0 + y1) / 2
    w = width(ym)
    if w < 0.03: continue
    sz = spine_z(ym)
    th = 0.3 if y0 < P0 else max(0.12, 0.3 * (w / width(P0)) ** 0.5)   # thinning toward the point too
    faces = {"east": face(0, vv(y0), 4, vv(y1)), "west": face(4, vv(y0), 0, vv(y1)), "north": face(4, vv(y0), 5, vv(y1)),
             "south": face(4, vv(y0), 5, vv(y1)), "up": face(4, 0, 5, 1), "down": face(4, 0, 5, 1)}
    els.append(el([round(8 - th / 2, 4), round(y0, 4), round(sz - w, 4)], [round(8 + th / 2, 4), round(y1, 4), round(sz, 4)], faces, name=f"blade{i}"))
els.append(el([7.95, 2.6, 8.2], [8.05, 4.9, 8.75], CLOTH(), rot={"angle": 22.5, "axis": "x", "origin": [8, 4.9, 8.5]}, name="tassel_a"))   # the tassel at the pommel
els.append(el([7.95, 0.6, 8.6], [8.05, 2.8, 9.05], CLOTH(), rot={"angle": -22.5, "axis": "x", "origin": [8, 2.8, 8.85]}, name="tassel_b"))
model = {
    "credit": "Iron Spell Grimoire - the Heartline (tools/overdrive_assets.py)",
    "textures": {"0": "iron_spell_grimoire:item/heartline", "particle": "iron_spell_grimoire:item/heartline"},
    "elements": els,
    "display": {
        # the grip sits at the model's centre, so it lands in the hand; held in a ready stance - blade raised forward-up,
        # the edge facing the foe (forward-down) and the spine toward you
        "thirdperson_righthand": {"rotation": [40, 0, 0], "translation": [0, 1.0, 0.5], "scale": [0.95, 0.95, 0.95]},
        "thirdperson_lefthand": {"rotation": [40, 0, 0], "translation": [0, 1.0, 0.5], "scale": [0.95, 0.95, 0.95]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 1.6, 1.13], "scale": [0.72, 0.72, 0.72]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 1.6, 1.13], "scale": [0.72, 0.72, 0.72]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.8, 0.8, 0.8]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    },
}
json.dump(model, open(os.path.join(MD, 'heartline_3d.json'), 'w'), indent=2)
# in the hotbar, item frames and on the ground: a flat drawn icon (the 3D blade is too thin to read edge-on)
json.dump({"parent": "minecraft:item/handheld", "loader": "forge:separate_transforms",
           "base": {"parent": "iron_spell_grimoire:item/heartline_3d"},
           "perspectives": {k: {"parent": "minecraft:item/generated", "textures": {"layer0": "iron_spell_grimoire:item/heartline_icon"}} for k in ("gui", "fixed", "ground")}},
          open(os.path.join(MD, 'heartline.json'), 'w'), indent=1)
ic = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); px = ic.load()
ICON = [   # k black steel, e polished edge, h crimson trace, g guard, b brass collar, w black cord, s white diamond, t tassel
    "...............e",
    "..............ke",
    ".............khe",
    "............kke.",
    "...........kke..",
    "..........khe...",
    ".........kke....",
    "........kke.....",
    ".......bb.......",
    ".....ggbgg......",
    "......ws........",
    ".....wsw........",
    "....wsw.........",
    "...wsw..........",
    "..gw............",
    ".t.t............",
]
PAL = {'k': (22, 22, 28, 255), 'e': (210, 218, 232, 255), 'h': (220, 24, 40, 255), 'g': (30, 30, 36, 255), 'b': (214, 172, 84, 255),
       'w': (24, 22, 26, 255), 's': (236, 232, 222, 255), 't': (190, 20, 34, 255)}
for y, row in enumerate(ICON):
    for x, ch in enumerate(row):
        if ch in PAL: px[x, y] = PAL[ch]
ic.save(os.path.join(TEX, 'heartline_icon.png'))


# ------------------------------------------------------------------ text
LP = os.path.join(A_, 'lang/en_us.json')
lang = json.load(open(LP))
lang['item.iron_spell_grimoire.overdrive_grimoire'] = 'Overdrive Grimoire'
lang['ui.iron_spell_grimoire.overdrive_grimoire_only'] = 'Only the Overdrive Grimoire can cast this.'
lang['tooltip.iron_spell_grimoire.overdrive_grimoire.passive'] = ('Passive - Heart Rate: every spell, every blow you land and every blow you take raise your heart rate (the monitor by your hotbar); it settles back when you stop. '
    'Above 150 bpm you run Hot: Overdrive spells hit 20% harder and you move quicker. Above 200, the Redline: 40% harder, quicker still and quicker hands - but your heart strains, '
    'and pinned at the very top too long it gives out. Every page but the first needs the Heartline in your hand.')
lang['tooltip.iron_spell_grimoire.overdrive_grimoire.desc'] = 'Ten pages for a swordsman with almost no magic and nothing but his technique - and one minute when he holds nothing back.'
lang['tooltip.iron_spell_grimoire.overdrive_grimoire.lore'] = 'You can feel it beating through the cover.'
lang['item.iron_spell_grimoire.heartline'] = 'Heartline'
lang['tooltip.iron_spell_grimoire.heartline.1'] = 'A black-bladed katana drawn out of your own chest, a crimson heartbeat running down the steel. The Overdrive Grimoire\'s sword pages need it in your hand.'
lang['tooltip.iron_spell_grimoire.heartline.2'] = 'The trace on the blade beats when your heart does.'
lang['key.iron_spell_grimoire.world_boss'] = lang.get('key.iron_spell_grimoire.world_boss', 'Close / open the world boss tracker')
for k, (title, guide) in {
 'draw_heartline': ('Draw the Heartline', 'The Heartline is drawn out of your chest on a ribbon of red light, into your hand (what you held is put away). It fades if it leaves your hand.'),
 'pulse_cut': ('Thunderclap Draw', 'Sword: a breath of stillness, then a draw-cut thirteen blocks long, so fast its crack arrives after it. The first foe in its line takes it hardest.'),
 'adrenaline_rush': ('Adrenaline Rush', 'Sword: dash nine blocks straight through everything ahead, cutting as you pass.'),
 'heartbeat': ('Read the Pulse', 'Sword: for 3 s you hear every heart round you - the first attack aimed at you misses, and you are already behind the attacker, cutting.'),
 'flatline': ('Flatline', 'Sword: a long piercing thrust - whatever it runs through is flatlined, slowed and weakened for 3 s.'),
 'tachycardia': ('Tachycardia', 'Sword: a storm of cuts on the ground ahead - six at rest, up to fourteen at the Redline.'),
 'second_wind': ('Second Wind', 'Sword: breathe - heals more the faster your heart is beating, and slows it by 40.'),
 'ribbon_bind': ('Ribbon Bind', 'Sword: the ribbon lashes out, wraps the foe you aim at and drags it onto your blade.'),
 'redline': ('Last Minute', 'Sword, mode, 60 s: every limit off - your heart held at the Redline without the strain, Overdrive hits 60% harder. When the minute is up you collapse, slowed and weak, for 8 s.'),
 'final_beat': ('Final Beat', 'Sword: your heart stops - a beat of silence - then one enormous cut, harder the faster your heart was beating; the lines it leaves burst a beat later. Drops you back to resting. Inside Last Minute it spends every second left of the minute on this one cut - up to two and a half times as hard - and then you collapse.'),
}.items():
    lang[f'spell.iron_spell_grimoire.{k}'] = f'Overdrive: {title}'
    lang[f'spell.iron_spell_grimoire.{k}.guide'] = guide
json.dump(lang, open(LP, 'w'), indent=2, ensure_ascii=False)
print('overdrive assets written')
