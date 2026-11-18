"""The Parasite Grimoire (a living strain that rewrites its host; every name and look here is original): spell icons,
the cover, leather, trim, inventory icon, models, the curios tag, the Chitin Hide skin and the text.
  Parasite - raw red flesh leather with grey bone trim; on the cover a single slit eye in a knot of braided tendrils,
             bone hooks curling out of it.
python3 tools/parasite_assets.py"""
import json, math, os, random, sys
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
from icons import make
from _cover_helpers import *   # SC, W, H, Pp, poly, circ, ell, arc, frame, glow, shift, tint_trim, R, A_, TEX, M

# ------------------------------------------------------------------ spell icons (16x16)
FL = ((150, 24, 36), (22, 4, 8), (230, 60, 70), (205, 205, 210))    # flesh on black, bone accent
BN = ((90, 20, 28), (14, 4, 6), (215, 215, 220), (240, 70, 80))     # bone on dark, red accent
make('consume', *FL, [('m', 'E', 3, 3, 12, 12), ('d', 'E', 5, 5, 10, 10), ('a', 'l', 4, 6, 6, 8), ('a', 'l', 11, 6, 9, 8), ('a', 'l', 4, 10, 6, 8),
                      ('a', 'l', 11, 10, 9, 8), ('w', 'p', 7, 7), ('m', 'l', 0, 15, 4, 11), ('m', 'l', 15, 15, 11, 11)])
make('evolution', *FL, [('m', 'l', 7, 14, 7, 2), ('m', 'l', 7, 9, 3, 5), ('m', 'l', 7, 6, 11, 2), ('m', 'l', 7, 11, 12, 8),
                        ('a', 'p', 3, 5), ('a', 'p', 11, 2), ('a', 'p', 12, 8), ('a', 'p', 7, 2), ('w', 'E', 6, 13, 8, 15)])
make('marrow_barrage', *BN, [('m', 'l', 1, 13, 13, 1), ('m', 'l', 1, 9, 9, 1), ('m', 'l', 5, 14, 14, 5), ('a', 'p', 13, 1), ('a', 'p', 9, 1), ('a', 'p', 14, 5),
                             ('w', 'p', 12, 2), ('w', 'p', 8, 2), ('w', 'p', 13, 6)])
make('grasping_field', *FL, [('d', 'E', 1, 10, 14, 15), ('m', 'a', 2, 2, 8, 14, 270, 360), ('m', 'a', 7, 1, 13, 15, 180, 270), ('a', 'l', 4, 13, 4, 6),
                             ('a', 'l', 8, 13, 8, 3), ('a', 'l', 11, 13, 11, 6), ('w', 'p', 8, 3)])
make('collapse_core', *FL, [('d', 'E', 1, 1, 14, 14), ('m', 'E', 4, 4, 11, 11), ('a', 'E', 6, 6, 9, 9), ('m', 'l', 0, 7, 3, 7), ('m', 'l', 12, 7, 15, 7),
                            ('m', 'l', 7, 0, 7, 3), ('m', 'l', 7, 12, 7, 15), ('w', 'p', 7, 7)])
make('pack_call', *FL, [('m', 'E', 1, 4, 5, 8), ('m', 'r', 2, 8, 4, 14), ('m', 'E', 10, 4, 14, 8), ('m', 'r', 11, 8, 13, 14), ('a', 'E', 5, 1, 10, 6),
                        ('a', 'r', 6, 6, 9, 14), ('w', 'p', 6, 3), ('w', 'p', 9, 3)])
make('spine_storm', *BN, [('m', 'l', 7, 7, 7, 0), ('m', 'l', 7, 7, 14, 7), ('m', 'l', 7, 7, 7, 14), ('m', 'l', 7, 7, 0, 7), ('m', 'l', 7, 7, 12, 2),
                          ('m', 'l', 7, 7, 2, 12), ('m', 'l', 7, 7, 2, 2), ('m', 'l', 7, 7, 12, 12), ('a', 'E', 5, 5, 9, 9), ('w', 'p', 7, 7)])


# ------------------------------------------------------------------ cover
def cover(big, INK, LEA, LEA_SH, MET, MET_SH, MET_HI, GEM, draw):
    im = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(im)
    frame(d, INK, LEA, LEA_SH, MET, MET_SH, MET_HI, GEM)
    cy = 176 if big else 196
    im = draw(im, d, 128, cy, 1.0 if big else 0.62, big)
    return im.resize((256, 384), Image.LANCZOS)


def parasite_draw(im, d, cx, cy, k, big):
    INKC, RED, DEEP, BONE, BONE_SH, EYE = (12, 3, 6, 255), (190, 30, 44, 255), (96, 12, 22, 255), (214, 214, 220, 255), (130, 130, 140, 255), (255, 70, 60, 255)
    im = glow(im, cx, cy, ((80 * k, 26), (52 * k, 50)), (255, 40, 50)); d = ImageDraw.Draw(im)
    rnd = random.Random(7)
    # braided tendrils knotting round the middle: two bands of beads twisting past each other
    for band in range(2):
        pts = []
        for i in range(60):
            t = i / 59
            a = t * math.pi * 2
            r = 58 + 8 * math.sin(a * 3 + band * math.pi)
            pts.append((cx + math.cos(a) * r * k, cy + math.sin(a) * r * 0.86 * k, 7 + 2 * math.sin(a * 3 + band * math.pi)))
        for (x, y, rr) in pts: circ(d, x, y, (rr + 2.5) * k, fill=INKC)
        for (x, y, rr) in pts: circ(d, x, y, rr * k, fill=RED if band == 0 else DEEP)
    # bone hooks curling out of the knot
    for j in range(6):
        a0 = j * math.pi / 3 + 0.3
        bx, by = cx + math.cos(a0) * 64 * k, cy + math.sin(a0) * 55 * k
        hook = []
        for s in range(9):
            t = s / 8
            a = a0 + t * 0.9
            r = 64 + t * 42
            hook.append((cx + math.cos(a) * r * k, cy + math.sin(a) * r * 0.86 * k))
        for w_, col in ((9, INKC), (6, BONE)):
            for i in range(len(hook) - 1):
                ww = max(1, int(w_ * (1 - i / len(hook)) * k * SC))
                d.line([Pp(*hook[i]), Pp(*hook[i + 1])], fill=col, width=ww)
    # the slit eye in the middle
    for (rx, ry, col) in ((40, 26, INKC), (36, 22, BONE), (30, 17, DEEP)):
        d.ellipse([Pp(cx - rx * k, cy - ry * k), Pp(cx + rx * k, cy + ry * k)], fill=col)
    poly(d, [(cx, cy - 16 * k), (cx + 5 * k, cy), (cx, cy + 16 * k), (cx - 5 * k, cy)], EYE, None)
    circ(d, cx - 1 * k, cy - 5 * k, 2 * k, fill=(255, 220, 220, 255))
    # veins from the eye
    for j in range(8):
        a = j * math.pi / 4 + rnd.uniform(-0.2, 0.2)
        p0 = (cx + math.cos(a) * 34 * k, cy + math.sin(a) * 20 * k)
        p1 = (cx + math.cos(a + 0.2) * 50 * k, cy + math.sin(a + 0.2) * 36 * k)
        d.line([Pp(*p0), Pp(*p1)], fill=EYE, width=max(1, int(2 * k * SC)))
    return im


name = 'parasite_grimoire'
fn = lambda b: cover(b, (14, 4, 7, 255), (120, 22, 34, 255), (82, 14, 24, 255), (200, 200, 206, 255), (120, 120, 128, 255), (240, 240, 245, 255), (255, 60, 60, 255), parasite_draw)
fn(True).save(os.path.join(M, f'{name}_front.png'))
fn(False).save(os.path.join(M, f'{name}_back.png'))
shift(Image.open(os.path.join(M, 'dark_grimoire_leather.png')), 0.985, 0.95, 0.8).save(os.path.join(M, f'{name}_leather.png'))
tint_trim(name, (200, 200, 206))
ic = shift(Image.open(os.path.join(TEX, 'dark_grimoire.png')), 0.985, 0.9, 0.8); px = ic.load()
for (x, y, c) in ((7, 7, (255, 70, 60)), (8, 7, (255, 70, 60)), (7, 6, (214, 214, 220)), (8, 8, (214, 214, 220))):
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

# ------------------------------------------------------------------ the Chitin Hide (an armour-layout skin, 64x32): red plates, dark seams
AD = os.path.join(A_, 'textures/models/armor'); os.makedirs(AD, exist_ok=True)
hide = Image.new('RGBA', (64, 32), (0, 0, 0, 0)); hp = hide.load()
rnd = random.Random(3)
cells = [(rnd.uniform(0, 64), rnd.uniform(0, 32)) for _ in range(46)]
for y in range(32):
    for x in range(64):
        ds = sorted(((x - a) ** 2 + (y - b) ** 2, i) for i, (a, b) in enumerate(cells))
        d0, d1 = math.sqrt(ds[0][0]), math.sqrt(ds[1][0])
        if d1 - d0 < 0.9: col = (28, 6, 10, 255)                                 # the seams between plates
        else:
            v = 0.75 + 0.25 * ((ds[0][1] * 37) % 11) / 10 - 0.12 * min(1, d0 / 4)
            col = (int(180 * v), int(30 * v), int(42 * v), 255)
            if d0 < 1.1: col = (225, 80, 88, 255)                                # a glossy point on each plate
        hp[x, y] = col
hide.save(os.path.join(AD, 'chitin_hide.png'))

# ------------------------------------------------------------------ Pack Call's infected brutes: a 64x64 humanoid skin of raw flesh
ED = os.path.join(A_, 'textures/entity'); os.makedirs(ED, exist_ok=True)
skin = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); sp = skin.load()
rnd = random.Random(11)
for y in range(64):
    for x in range(64):
        n = 0.55 + 0.25 * math.sin(x * 0.9 + y * 0.35) * math.sin(y * 0.7 - x * 0.2) + rnd.uniform(-0.08, 0.08)
        sp[x, y] = (int(150 * n + 40), int(22 * n + 6), int(30 * n + 8), 255)
for k in range(26):   # veins
    x, y = rnd.uniform(0, 64), rnd.uniform(0, 64)
    for i in range(10):
        x += rnd.uniform(-1.5, 1.5); y += 1.0
        xi, yi = int(x) % 64, int(y) % 64
        sp[xi, yi] = (235, 70, 70, 255)
for (x, y) in ((9, 12), (10, 12), (13, 12), (14, 12)):   # glowing eyes on the face (the head's front: 8-15, 8-15)
    sp[x, y] = (255, 230, 120, 255)
for x in range(10, 14): sp[x, 14] = (20, 4, 6, 255)       # the maw
skin.save(os.path.join(ED, 'infected_brute.png'))

# ------------------------------------------------------------------ text
LP = os.path.join(A_, 'lang/en_us.json')
lang = json.load(open(LP))
lang['item.iron_spell_grimoire.parasite_grimoire'] = 'Parasite Grimoire'
lang['ui.iron_spell_grimoire.parasite_grimoire_only'] = 'Only the Parasite Grimoire can cast this.'
lang['key.iron_spell_grimoire.parasite_wheel'] = 'Parasite forms / Shadow commands wheel'
lang['key.iron_spell_grimoire.parasite_tendril'] = 'Parasite tendril (sneak: burrow)'
lang['entity.iron_spell_grimoire.hive_nest'] = 'Hive Nest'
lang['entity.iron_spell_grimoire.molt_husk'] = 'Molted Husk'
lang['entity.iron_spell_grimoire.infected_brute'] = 'Infected Brute'
lang['key.iron_spell_grimoire.parasite_sight'] = 'Parasite: Predator Sight'
lang['tooltip.iron_spell_grimoire.parasite_grimoire.passive'] = ('Passive - Strain: press G for the wheel of arm forms (Reaper Edge, Rending Talons, Spine Launcher, Titan Fist, '
    'Bone Lance, the Whip-Spear and Battering Ram hybrids, the Bulwark and the Chitin Hide). Sprint-jumps leap high; evolve to run up walls, glide and slam down. '
    'Devour foes for biomass, evolution and rare mutations; keep the strain fed or it starves - and feed it past full and it goes into a Frenzy. '
    'Evolve everything and your strain awakens, with its own signature move and the Final Form.')
lang['tooltip.iron_spell_grimoire.parasite_grimoire.desc'] = 'Seven pages of a strain that rewrites whoever carries it.'
lang['tooltip.iron_spell_grimoire.parasite_grimoire.lore'] = 'Its cover is warm. Sometimes it blinks.'
for k, (title, guide) in {
 'consume': ('Consume', 'Lash a tendril into the foe you aim at. Weak enough and not a boss, it is dragged in and devoured whole: biomass, health back and an evolution point. Otherwise the tendril rips a chunk out of it.'),
 'evolution': ('Evolution', 'Open the Evolution page: spend evolution points on movement, arm forms and hunger, and settle your Strain.'),
 'marrow_barrage': ('Marrow Barrage', 'Costs 25 biomass. Ten bone lances fire from your arm in a fan, each running through everything for 20 blocks.'),
 'grasping_field': ('Grasping Field', 'Costs 30 biomass. Tendrils and spines burst up over a field where you aim: everything in it is held, dragged inward and torn at for 4 s.'),
 'collapse_core': ('Collapse Core', 'Costs 35 biomass. A dense sphere of biomass forms where you aim and drags every foe within 9 blocks into it for 3 s, then bursts in a storm of tendrils.'),
 'pack_call': ('Pack Call', 'Costs 40 biomass. Three infected brutes burst out of the ground to fight for you for 30 s - shaped by your Strain: stalkers, brawlers or spine-spitters.'),
 'spine_storm': ('Spine Storm', 'Costs 40 biomass. Your body splits into a storm of bone - three waves of spines shred everything within 8 blocks.'),
}.items():
    lang[f'spell.iron_spell_grimoire.{k}'] = f'Parasite: {title}'
    lang[f'spell.iron_spell_grimoire.{k}.guide'] = guide
json.dump(lang, open(LP, 'w'), indent=2, ensure_ascii=False)
print('parasite assets written')
