"""The Blood Grimoire (an original grimoire): spell icons, the cover (deep oxblood leather, cold silver metal, a red
crescent moon over a single drop of blood - an original design), leather, trim, inventory icon, models, curios tag, lang.
Cast animations reuse existing ones (see AddonSpellRegistry.blood)."""
import json, os, sys, math, colorsys
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
from icons import make
R = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources')
A_ = os.path.join(R, 'assets/iron_spell_grimoire')
TEX = os.path.join(A_, 'textures/item'); M = os.path.join(TEX, 'spell_book_models')

# ------------------------------------------------------------------ spell icons: oxblood, crimson, white
B = ((70, 8, 14), (18, 2, 6), (200, 24, 34), (255, 120, 120))
def drop(x, y, k='m'):   # a drop of blood, point up
    return [(k, 'l', x, y, x, y + 1), (k, 'E', x - 2, y + 2, x + 2, y + 5), ('w', 'p', x - 1, y + 3)]
make('crimson_edge', *B, [('m', 'l', 2, 13, 13, 2), ('a', 'l', 3, 13, 13, 3), ('m', 'l', 1, 11, 11, 1), ('w', 'l', 6, 9, 9, 6)])
make('hemorrhage', *B, [('a', 'e', 3, 3, 12, 12), ('m', 'e', 5, 5, 10, 10), ('w', 'p', 7, 7)] + drop(12, 9) + drop(3, 10))
make('sanguine_volley', *B, sum([[('m', 'l', 1, 8, 14, 8 + o), ('a', 'p', 14, 8 + o)] for o in (-5, -3, 0, 3, 5)], []))
make('transfusion', *B, [('m', 'l', 2, 4, 7, 9), ('m', 'l', 7, 9, 13, 4), ('a', 'l', 2, 5, 7, 10)] + drop(7, 10))
make('blood_pact', *B, drop(7, 2) + [('a', 'r', 3, 9, 12, 14), ('w', 'l', 4, 11, 11, 11), ('m', 'l', 5, 13, 10, 13)])
make('red_tide', *B, [('m', 'l', 0, 12, 4, 6), ('m', 'l', 4, 6, 8, 12), ('m', 'l', 8, 12, 12, 6), ('m', 'l', 12, 6, 15, 10), ('a', 'l', 0, 14, 15, 14), ('w', 'p', 4, 6), ('w', 'p', 12, 6)])
make('thorned_veins', *B, [('m', 'l', 2, 14, 4, 4), ('m', 'l', 6, 14, 8, 2), ('m', 'l', 10, 14, 12, 5), ('a', 'l', 0, 14, 15, 14), ('w', 'p', 8, 2), ('w', 'p', 4, 4)])
make('crimson_bulwark', *B, [('m', 'r', 4, 2, 11, 12), ('a', 'r', 5, 3, 10, 11), ('m', 'l', 4, 12, 7, 15), ('m', 'l', 11, 12, 8, 15), ('w', 'l', 7, 4, 7, 10)])
make('exsanguinate', *B, [('a', 'l', 1, 1, 6, 6), ('a', 'l', 14, 1, 9, 6), ('a', 'l', 1, 14, 6, 9), ('a', 'l', 14, 14, 9, 9), ('m', 'E', 5, 5, 10, 10), ('w', 'p', 7, 7)])
make('scarlet_moon', (40, 4, 8), (6, 0, 2), (230, 30, 40), (255, 255, 255), [('m', 'E', 4, 1, 11, 8), ('d', 'E', 6, 0, 13, 7), ('a', 'l', 7, 9, 7, 15), ('a', 'l', 4, 12, 10, 12), ('w', 'p', 7, 11)])

# ------------------------------------------------------------------ cover
S = 2; W, H = 256 * S, 384 * S
INK = (10, 2, 4, 255); OX, OX_SH, OX_HI = (74, 10, 18, 255), (44, 4, 10, 255), (104, 20, 30, 255)
SIL, SIL_SH, SIL_HI, WHITE = (196, 196, 206, 255), (120, 120, 132, 255), (236, 236, 244, 255), (255, 255, 255, 255)
RED, RED_SH, RED_HI = (210, 24, 36, 255), (130, 8, 18, 255), (255, 90, 90, 255)
def Pp(x, y): return (x * S, y * S)
def poly(d, pts, fill, outline=INK, w=3):
    q = [Pp(*p) for p in pts]
    if outline: d.line(q + [q[0]], fill=outline, width=w * S * 2, joint='curve')
    d.polygon(q, fill=fill)
def circ(d, cx, cy, r, fill=None, outline=None, w=0):
    d.ellipse([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], fill=fill, outline=outline, width=int(w * S))
def frame(d):
    d.rectangle([0, 0, W, H], fill=OX)
    poly(d, [(256, 0), (256, 384), (0, 384), (0, 330), (190, 330), (190, 0)], OX_SH, None)
    for (x0, y0, x1, y1, col) in [(4, 4, 252, 380, INK), (7, 7, 249, 377, SIL), (7, 7, 249, 9, SIL_HI), (7, 7, 9, 377, SIL_HI),
                                  (7, 374, 249, 377, SIL_SH), (246, 7, 249, 377, SIL_SH), (17, 17, 239, 367, INK), (19, 19, 237, 365, OX)]:
        d.rectangle([x0 * S, y0 * S, x1 * S, y1 * S], fill=col)
    poly(d, [(237, 19), (237, 365), (19, 365), (19, 330), (190, 330), (190, 19)], OX_SH, None)
    for (sx, sy, x, y) in [(1, 1, 4, 4), (-1, 1, 252, 4), (1, -1, 4, 380), (-1, -1, 252, 380)]:   # thorned silver corners
        poly(d, [(x, y), (x + sx * 46, y), (x + sx * 30, y + sy * 8), (x + sx * 8, y + sy * 30), (x, y + sy * 46)], SIL, INK, 3)
        circ(d, x + sx * 14, y + sy * 14, 4.5, fill=RED, outline=INK, w=2.5)
def moon(d, cx, cy, R):
    circ(d, cx, cy, R + 4, fill=INK); circ(d, cx, cy, R, fill=RED); circ(d, cx - R * 0.15, cy - R * 0.15, R * 0.7, fill=RED_HI)
    circ(d, cx + R * 0.38, cy - R * 0.2, R * 0.86, fill=OX)   # carve the crescent out of it
def blood_drop(d, cx, cy, s):
    pts = [(cx, cy - s * 1.6)] + [(cx + math.sin(a) * s, cy + math.cos(a) * s) for a in [i * math.pi / 12 for i in range(-9, 10)]]
    poly(d, pts, RED, INK, 3)
    poly(d, [(cx - s * 0.45, cy - s * 0.2), (cx - s * 0.25, cy - s * 0.6), (cx - s * 0.1, cy - s * 0.15)], RED_HI, None)
def cover(big):
    im = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(im)
    frame(d)
    cx = 128
    if big:
        for k, a in ((120, 30), (100, 50)):
            g = Image.new('RGBA', (W, H)); gd = ImageDraw.Draw(g); circ(gd, cx, 150, k, fill=(255, 40, 50, a))
            im = Image.alpha_composite(im, g); d = ImageDraw.Draw(im)
        moon(d, cx, 150, 72)
        blood_drop(d, cx, 270, 26)
        for x, y in ((70, 300), (186, 300), (60, 230), (196, 230)): circ(d, x, y, 3, fill=RED)
    else:
        moon(d, cx, 192, 44)
    return im.resize((256, 384), Image.LANCZOS)
def shift(im, hue, sm, vm):
    im = im.convert('RGBA'); px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0: continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            nr, ng, nb = colorsys.hsv_to_rgb(hue, min(1, s * sm), min(1, v * vm))
            px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    return im
cover(True).save(os.path.join(M, 'blood_grimoire_front.png'))
cover(False).save(os.path.join(M, 'blood_grimoire_back.png'))
shift(Image.open(os.path.join(M, 'dark_grimoire_leather.png')), 0.985, 1.4, 0.75).save(os.path.join(M, 'blood_grimoire_leather.png'))
t = Image.open(os.path.join(M, 'spatial_grimoire_trim.png')).convert('RGBA'); px = t.load()
for y in range(t.height):
    for x in range(t.width):
        r, g, b, a = px[x, y]; k = 0.5 + 0.6 * min(1.2, (r + g + b) / 450.0)
        px[x, y] = (min(255, int(200 * k)), min(255, int(200 * k)), min(255, int(212 * k)), a)
t.save(os.path.join(M, 'blood_grimoire_trim.png'))
icon = shift(Image.open(os.path.join(TEX, 'dark_grimoire.png')), 0.985, 1.4, 0.8); px = icon.load()
for (x, y, c) in ((7, 6, (255, 70, 70)), (7, 7, (220, 20, 30)), (8, 7, (220, 20, 30)), (7, 8, (220, 20, 30)), (8, 8, (220, 20, 30)), (7, 9, (160, 10, 20)), (8, 9, (160, 10, 20))):
    if px[x, y][3]: px[x, y] = c + (255,)
icon.save(os.path.join(TEX, 'blood_grimoire.png'))

# ------------------------------------------------------------------ models and the curios tag
MD = os.path.join(A_, 'models/item')
for suf in ('', '_model', '_open'):
    s = open(os.path.join(MD, f'light_grimoire{suf}.json')).read().replace('light_grimoire', 'blood_grimoire')
    open(os.path.join(MD, f'blood_grimoire{suf}.json'), 'w').write(s)
CT = os.path.join(R, 'data/curios/tags/items/spellbook.json')
c = json.load(open(CT))
if 'iron_spell_grimoire:blood_grimoire' not in c['values']: c['values'].append('iron_spell_grimoire:blood_grimoire')
json.dump(c, open(CT, 'w'), indent=2)

# ------------------------------------------------------------------ lang
LP = os.path.join(A_, 'lang/en_us.json')
lang = json.load(open(LP))
lang.update({
 'item.iron_spell_grimoire.blood_grimoire': 'Blood Grimoire',
 'tooltip.iron_spell_grimoire.blood_grimoire.passive': 'Passive - Blood Price: every spell costs a little of your health as well as mana (never your last heart), and the lower your health, the harder it hits (up to +80%).',
 'tooltip.iron_spell_grimoire.blood_grimoire.desc': 'Ten pages of Blood Magic: blades and needles of blood, a tide that throws foes back, thorns from the ground, and the Scarlet Moon.',
 'tooltip.iron_spell_grimoire.blood_grimoire.lore': 'Every page is written in the reader\'s own blood. It reads better the more you bleed.',
 'ui.iron_spell_grimoire.blood_grimoire_only': 'Only the Blood Grimoire can cast this.',
 'spell.iron_spell_grimoire.crimson_edge': 'Blood Magic: Crimson Edge',
 'spell.iron_spell_grimoire.crimson_edge.guide': 'Cut a wide arc of blood in front of you.',
 'spell.iron_spell_grimoire.hemorrhage': 'Blood Magic: Hemorrhage',
 'spell.iron_spell_grimoire.hemorrhage.guide': 'Mark the foe you aim at: it bleeds every second for 8 s, and takes 25% more from your Blood spells.',
 'spell.iron_spell_grimoire.sanguine_volley': 'Blood Magic: Sanguine Volley',
 'spell.iron_spell_grimoire.sanguine_volley.guide': 'Fire a fan of seven needles of blood (eleven under the Scarlet Moon).',
 'spell.iron_spell_grimoire.transfusion': 'Blood Magic: Transfusion',
 'spell.iron_spell_grimoire.transfusion.guide': 'A red thread to the foe you aim at drains it into you for two seconds.',
 'spell.iron_spell_grimoire.blood_pact': 'Blood Magic: Blood Pact',
 'spell.iron_spell_grimoire.blood_pact.guide': 'Pay a fifth of your health: your next Blood spell is 60% stronger.',
 'spell.iron_spell_grimoire.red_tide': 'Blood Magic: Red Tide',
 'spell.iron_spell_grimoire.red_tide.guide': 'A wall of blood rolls forward and throws back everything in its way.',
 'spell.iron_spell_grimoire.thorned_veins': 'Blood Magic: Thorned Veins',
 'spell.iron_spell_grimoire.thorned_veins.guide': 'Thorns of blood burst out of the ground along a line, flinging foes up.',
 'spell.iron_spell_grimoire.crimson_bulwark': 'Blood Magic: Crimson Bulwark',
 'spell.iron_spell_grimoire.crimson_bulwark.guide': 'A shell of hardened blood absorbs damage; when it breaks, it bursts outward and hurts everything close.',
 'spell.iron_spell_grimoire.exsanguinate': 'Blood Magic: Exsanguinate',
 'spell.iron_spell_grimoire.exsanguinate.guide': 'Tear the blood out of every foe within 9 blocks and take half of it into yourself.',
 'spell.iron_spell_grimoire.scarlet_moon': 'Blood Magic: Scarlet Moon',
 'spell.iron_spell_grimoire.scarlet_moon.guide': 'Mode, 30 s: a white aura blazes round you and a red moon hangs over your head. Blood spells hit 35% harder and heal you for a quarter of what they deal (their health price doubles); you take less damage, move faster and hold your ground.',
})
json.dump(lang, open(LP, 'w'), indent=2, ensure_ascii=False)
print('blood assets ok')
