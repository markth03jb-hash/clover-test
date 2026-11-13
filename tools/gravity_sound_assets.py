"""The Gravity and Sound Grimoires (original grimoires): spell icons, covers, leather, trim, inventory icons, models,
the curios tag and the text. Original designs:
  Gravity - near-black violet leather, pewter metal, a black world held in a bright tilted ring with three small orbs
  Sound   - deep sea-green leather, pale jade metal, a tuning fork with rings of sound spreading out from it
Cast animations reuse existing ones (see AddonSpellRegistry.gravity / .sound)."""
import json, os, sys, math, colorsys
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
from icons import make
R = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources')
A_ = os.path.join(R, 'assets/iron_spell_grimoire')
TEX = os.path.join(A_, 'textures/item'); M = os.path.join(TEX, 'spell_book_models')

# ------------------------------------------------------------------ spell icons
G = ((44, 20, 78), (6, 2, 14), (150, 96, 255), (226, 208, 255))      # gravity: violet on black
def world(x, y, r=2):   # a black world with a violet rim
    return [('a', 'E', x - r - 1, y - r - 1, x + r + 1, y + r + 1), ('d', 'E', x - r, y - r, x + r, y + r)]
make('gravity_bullet', *G, world(4, 11) + world(9, 6, 1) + [('m', 'l', 5, 10, 13, 2), ('a', 'p', 13, 2), ('w', 'p', 3, 10)])
make('weightless_step', *G, [('m', 'l', 2, 14, 7, 4), ('m', 'l', 7, 4, 13, 9), ('a', 'e', 5, 12, 11, 15), ('w', 'p', 7, 4), ('a', 'p', 4, 7), ('a', 'p', 11, 3), ('a', 'p', 13, 12)])
make('crushing_field', *G, [('m', 'l', 3, 1, 3, 9), ('m', 'l', 7, 0, 7, 10), ('m', 'l', 11, 1, 11, 9), ('a', 'l', 2, 8, 4, 10), ('a', 'l', 6, 9, 8, 11), ('a', 'l', 10, 8, 12, 10), ('m', 'r', 0, 12, 15, 14), ('w', 'l', 1, 12, 14, 12)])
make('repulse', *G, world(7, 7, 1) + [('m', 'a', 2, 2, 12, 12, 0, 360), ('a', 'a', 0, 0, 14, 14, 200, 340), ('a', 'a', 0, 0, 14, 14, 20, 160), ('w', 'p', 1, 7), ('w', 'p', 13, 7)])
make('gravity_well', *G, [('m', 'a', 1, 1, 14, 14, 0, 270), ('a', 'a', 3, 3, 12, 12, 90, 360), ('m', 'a', 5, 5, 10, 10, 180, 450)] + world(7, 7, 1))
make('gravity_lift', *G, [('m', 'l', 3, 14, 3, 4), ('m', 'l', 7, 14, 7, 2), ('m', 'l', 11, 14, 11, 4), ('a', 'l', 1, 6, 3, 4), ('a', 'l', 5, 6, 3, 4), ('a', 'l', 5, 4, 7, 2), ('a', 'l', 9, 4, 7, 2), ('a', 'l', 9, 6, 11, 4), ('a', 'l', 13, 6, 11, 4), ('w', 'l', 0, 15, 15, 15)])
make('orbiting_mass', *G, [('a', 'e', 1, 4, 14, 11)] + world(7, 7, 2) + [('m', 'E', 0, 6, 2, 8), ('m', 'E', 13, 6, 15, 8), ('m', 'E', 6, 0, 8, 2), ('w', 'p', 1, 7)])
make('heavy_descent', *G, [('m', 'l', 4, 0, 8, 9), ('m', 'l', 10, 0, 8, 9), ('a', 'l', 7, 0, 8, 9)] + world(8, 10, 2) + [('a', 'l', 0, 14, 15, 14), ('w', 'l', 3, 13, 12, 13)])
make('mass_domain', *G, [('a', 'e', 0, 9, 15, 15), ('m', 'e', 2, 10, 13, 14)] + world(7, 6, 3) + [('w', 'p', 6, 4), ('a', 'l', 1, 2, 4, 5), ('a', 'l', 14, 2, 11, 5)])
make('starfall_collapse', (60, 24, 110), (4, 0, 12), (180, 120, 255), (255, 255, 255), world(9, 5, 3) + [('m', 'l', 2, 14, 6, 8), ('a', 'l', 1, 12, 5, 7), ('m', 'l', 5, 15, 8, 10), ('w', 'l', 0, 15, 15, 15), ('w', 'p', 8, 3)])

S_ = ((14, 70, 62), (2, 14, 12), (90, 255, 208), (210, 255, 240))    # sound: sea green on deep teal
def arcs(x, y, side=1, n=3, k='m'):
    out = []
    for i in range(n):
        r = 2 + i * 2
        a0, a1 = (300, 420) if side > 0 else (120, 240)
        out.append((k if i % 2 == 0 else 'a', 'a', x - r, y - r, x + r, y + r, a0, a1))
    return out
def fork(x, y, h=8):
    return [('w', 'l', x - 1, y - h, x - 1, y - 3), ('w', 'l', x + 1, y - h, x + 1, y - 3), ('w', 'l', x - 1, y - 3, x + 1, y - 3), ('m', 'l', x, y - 2, x, y)]
make('sonic_pulse', *S_, arcs(2, 7, 1, 4) + [('w', 'p', 2, 7)])
make('echo_step', *S_, [('a', 'e', 1, 3, 5, 13), ('d', 'E', 2, 4, 4, 12), ('w', 'e', 9, 3, 13, 13), ('m', 'l', 5, 8, 9, 8), ('m', 'p', 7, 6), ('m', 'p', 7, 10)])
make('tuning_strike', *S_, fork(7, 14, 10) + arcs(9, 6, 1, 2) + arcs(5, 6, -1, 2))
make('sound_barrier', *S_, [('m', 'e', 1, 1, 14, 14), ('a', 'e', 3, 3, 12, 12), ('m', 'l', 0, 7, 3, 7), ('w', 'l', 12, 7, 15, 7), ('w', 'p', 7, 7)])
make('silence_field', *S_, [('a', 'l', 0, 7, 3, 7), ('a', 'l', 3, 7, 4, 4), ('a', 'l', 4, 4, 5, 10), ('a', 'l', 5, 10, 6, 6), ('m', 'l', 6, 7, 15, 7), ('w', 'e', 8, 10, 14, 14), ('w', 'l', 9, 13, 13, 11)])
make('thunderclap', *S_, [('w', 'g', [(3, 3), (7, 6), (6, 9), (2, 7)]), ('w', 'g', [(12, 3), (8, 6), (9, 9), (13, 7)]), ('m', 'a', 1, 1, 14, 14, 30, 150), ('a', 'a', 0, 0, 15, 15, 200, 340), ('m', 'p', 7, 7)])
make('sonic_lance', *S_, [('w', 'l', 0, 7, 15, 7), ('m', 'e', 3, 4, 5, 10), ('m', 'e', 7, 4, 9, 10), ('a', 'e', 11, 5, 13, 9), ('a', 'l', 0, 6, 15, 6), ('a', 'l', 0, 8, 15, 8)])
make('echo_chamber', *S_, fork(3, 14, 6) + fork(12, 14, 6) + [('m', 'e', 4, 4, 11, 11), ('a', 'e', 6, 6, 9, 9), ('w', 'p', 7, 7)])
make('crescendo', *S_, [('a', 'r', 1, 11, 2, 14), ('a', 'r', 4, 9, 5, 14), ('m', 'r', 7, 6, 8, 14), ('m', 'r', 10, 3, 11, 14), ('w', 'r', 13, 0, 14, 14)])
make('final_chord', (20, 90, 80), (0, 10, 8), (110, 255, 220), (255, 255, 255), [('m', 'e', 5, 1, 10, 4), ('a', 'e', 4, 5, 11, 8), ('w', 'e', 3, 9, 12, 12), ('m', 'a', 0, 4, 15, 19, 200, 340), ('w', 'p', 7, 2)])

# ------------------------------------------------------------------ covers
SC = 2; W, H = 256 * SC, 384 * SC
def Pp(x, y): return (x * SC, y * SC)
def poly(d, pts, fill, outline, w=3):
    q = [Pp(*p) for p in pts]
    if outline: d.line(q + [q[0]], fill=outline, width=w * SC * 2, joint='curve')
    d.polygon(q, fill=fill)
def circ(d, cx, cy, r, fill=None, outline=None, w=0):
    d.ellipse([(cx - r) * SC, (cy - r) * SC, (cx + r) * SC, (cy + r) * SC], fill=fill, outline=outline, width=int(w * SC))
def ell(d, cx, cy, rx, ry, outline, w):
    d.ellipse([(cx - rx) * SC, (cy - ry) * SC, (cx + rx) * SC, (cy + ry) * SC], outline=outline, width=int(w * SC))
def arc(d, cx, cy, r, a0, a1, col, w):
    d.arc([(cx - r) * SC, (cy - r) * SC, (cx + r) * SC, (cy + r) * SC], a0, a1, fill=col, width=int(w * SC))

def frame(d, INK, LEA, LEA_SH, MET, MET_SH, MET_HI, GEM):
    d.rectangle([0, 0, W, H], fill=LEA)
    poly(d, [(256, 0), (256, 384), (0, 384), (0, 330), (190, 330), (190, 0)], LEA_SH, None)
    for (x0, y0, x1, y1, col) in [(4, 4, 252, 380, INK), (7, 7, 249, 377, MET), (7, 7, 249, 9, MET_HI), (7, 7, 9, 377, MET_HI),
                                  (7, 374, 249, 377, MET_SH), (246, 7, 249, 377, MET_SH), (17, 17, 239, 367, INK), (19, 19, 237, 365, LEA)]:
        d.rectangle([x0 * SC, y0 * SC, x1 * SC, y1 * SC], fill=col)
    poly(d, [(237, 19), (237, 365), (19, 365), (19, 330), (190, 330), (190, 19)], LEA_SH, None)
    for (sx, sy, x, y) in [(1, 1, 4, 4), (-1, 1, 252, 4), (1, -1, 4, 380), (-1, -1, 252, 380)]:
        poly(d, [(x, y), (x + sx * 40, y), (x + sx * 40, y + sy * 10), (x + sx * 10, y + sy * 40), (x, y + sy * 40)], MET, INK, 3)
        circ(d, x + sx * 13, y + sy * 13, 4.5, fill=GEM, outline=INK, w=2.5)

def glow(im, cx, cy, layers, col):
    for k, a in layers:
        g = Image.new('RGBA', (W, H)); gd = ImageDraw.Draw(g); circ(gd, cx, cy, k, fill=col + (a,))
        im = Image.alpha_composite(im, g)
    return im

def gravity_cover(big):
    INK = (6, 2, 12, 255); LEA, LEA_SH = (30, 18, 52, 255), (18, 10, 34, 255)
    MET, MET_SH, MET_HI = (150, 140, 176, 255), (90, 82, 116, 255), (204, 196, 228, 255)
    VIO, VIO_HI, BLACK = (150, 92, 255, 255), (226, 206, 255, 255), (4, 0, 8, 255)
    im = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(im)
    frame(d, INK, LEA, LEA_SH, MET, MET_SH, MET_HI, VIO)
    cx, cy, R = 128, (176 if big else 192), (58 if big else 38)
    im = glow(im, cx, cy, ((R + 50, 26), (R + 30, 40), (R + 14, 70)), (140, 80, 255)); d = ImageDraw.Draw(im)
    ell(d, cx, cy, R * 2.0, R * 0.55, INK, 10)           # the ring, behind
    ell(d, cx, cy, R * 2.0, R * 0.55, VIO, 5)
    circ(d, cx, cy, R + 4, fill=INK); circ(d, cx, cy, R + 1, fill=VIO_HI); circ(d, cx, cy, R - 3, fill=BLACK)   # the world: black, a white-violet rim
    for a0, a1 in ((200, 340),):                          # the ring's front half, over the world
        d.arc([(cx - R * 2.0) * SC, (cy - R * 0.55) * SC, (cx + R * 2.0) * SC, (cy + R * 0.55) * SC], 20, 160, fill=INK, width=10 * SC)
        d.arc([(cx - R * 2.0) * SC, (cy - R * 0.55) * SC, (cx + R * 2.0) * SC, (cy + R * 0.55) * SC], 20, 160, fill=VIO, width=5 * SC)
    if big:
        for (x, y, r) in ((60, 92, 7), (200, 270, 9), (84, 300, 5)):   # three small orbs held in its pull
            circ(d, x, y, r + 3, fill=INK); circ(d, x, y, r + 1, fill=VIO); circ(d, x, y, r - 1.5, fill=BLACK)
    return im.resize((256, 384), Image.LANCZOS)

def sound_cover(big):
    INK = (2, 12, 10, 255); LEA, LEA_SH = (14, 60, 54, 255), (8, 40, 36, 255)
    MET, MET_SH, MET_HI = (176, 220, 204, 255), (108, 156, 140, 255), (226, 252, 242, 255)
    SEA, SEA_HI = (92, 255, 208, 255), (230, 255, 248, 255)
    im = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(im)
    frame(d, INK, LEA, LEA_SH, MET, MET_SH, MET_HI, SEA)
    cx, cy = 128, (200 if big else 196)
    im = glow(im, cx, cy - 30, ((110, 22), (80, 34)), (90, 255, 210)); d = ImageDraw.Draw(im)
    for i in range(3 if big else 2):   # rings of sound out of each side
        r = 46 + i * 26
        for a0, a1 in ((300, 360 + 60), (120, 240)):
            arc(d, cx, cy - 40, r, a0, a1, INK, 9); arc(d, cx, cy - 40, r, a0, a1, SEA if i % 2 == 0 else SEA_HI, 4.5)
    s = 1.0 if big else 0.7
    def fx(x): return cx + (x - 128) * s
    def fy(y): return cy + (y - 200) * s
    # the fork: two prongs, the bend, the stem and its foot
    for x in (112, 144):
        poly(d, [(fx(x - 6), fy(110)), (fx(x + 6), fy(110)), (fx(x + 6), fy(200)), (fx(x - 6), fy(200))], MET, INK, 3)
        poly(d, [(fx(x - 3), fy(114)), (fx(x), fy(114)), (fx(x), fy(196)), (fx(x - 3), fy(196))], MET_HI, None)
    poly(d, [(fx(106), fy(196)), (fx(150), fy(196)), (fx(150), fy(214)), (fx(106), fy(214))], MET, INK, 3)
    poly(d, [(fx(122), fy(212)), (fx(134), fy(212)), (fx(134), fy(276)), (fx(122), fy(276))], MET, INK, 3)
    circ(d, fx(128), fy(286), 13 * s, fill=SEA, outline=INK, w=3)
    circ(d, fx(124), fy(282), 4 * s, fill=SEA_HI)
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
def tint_trim(name, col):
    t = Image.open(os.path.join(M, 'spatial_grimoire_trim.png')).convert('RGBA'); px = t.load()
    for y in range(t.height):
        for x in range(t.width):
            r, g, b, a = px[x, y]; k = 0.5 + 0.6 * min(1.2, (r + g + b) / 450.0)
            px[x, y] = tuple(min(255, int(c * k)) for c in col) + (a,)
    t.save(os.path.join(M, f'{name}_trim.png'))

gravity_cover(True).save(os.path.join(M, 'gravity_grimoire_front.png'))
gravity_cover(False).save(os.path.join(M, 'gravity_grimoire_back.png'))
shift(Image.open(os.path.join(M, 'dark_grimoire_leather.png')), 0.74, 1.2, 0.7).save(os.path.join(M, 'gravity_grimoire_leather.png'))
tint_trim('gravity_grimoire', (160, 150, 190))
sound_cover(True).save(os.path.join(M, 'sound_grimoire_front.png'))
sound_cover(False).save(os.path.join(M, 'sound_grimoire_back.png'))
shift(Image.open(os.path.join(M, 'dark_grimoire_leather.png')), 0.46, 1.3, 1.0).save(os.path.join(M, 'sound_grimoire_leather.png'))
tint_trim('sound_grimoire', (180, 226, 208))

# inventory icons: the dark grimoire's shape, recoloured, with a mark on the cover
ic = shift(Image.open(os.path.join(TEX, 'dark_grimoire.png')), 0.74, 1.2, 0.8); px = ic.load()
for (x, y, c) in ((7, 7, (4, 0, 8)), (8, 7, (4, 0, 8)), (7, 8, (4, 0, 8)), (8, 8, (4, 0, 8)), (6, 7, (170, 110, 255)), (9, 8, (170, 110, 255)), (6, 8, (120, 70, 220)), (9, 7, (120, 70, 220)), (7, 6, (226, 206, 255))):
    if px[x, y][3]: px[x, y] = c + (255,)
ic.save(os.path.join(TEX, 'gravity_grimoire.png'))
ic = shift(Image.open(os.path.join(TEX, 'dark_grimoire.png')), 0.46, 1.3, 1.1); px = ic.load()
for (x, y, c) in ((7, 6, (230, 255, 248)), (8, 6, (230, 255, 248)), (7, 7, (230, 255, 248)), (8, 7, (230, 255, 248)), (7, 8, (176, 220, 204)), (7, 9, (176, 220, 204)), (6, 6, (92, 255, 208)), (9, 6, (92, 255, 208))):
    if px[x, y][3]: px[x, y] = c + (255,)
ic.save(os.path.join(TEX, 'sound_grimoire.png'))

# ------------------------------------------------------------------ models and the curios tag
MD = os.path.join(A_, 'models/item')
for name in ('gravity_grimoire', 'sound_grimoire'):
    for suf in ('', '_model', '_open'):
        s = open(os.path.join(MD, f'light_grimoire{suf}.json')).read().replace('light_grimoire', name)
        open(os.path.join(MD, f'{name}{suf}.json'), 'w').write(s)

# ------------------------------------------------------------------ lang
LP = os.path.join(A_, 'lang/en_us.json')
lang = json.load(open(LP))
def spells(school, d):
    for k, (title, guide) in d.items():
        lang[f'spell.iron_spell_grimoire.{k}'] = f'{school}: {title}'
        lang[f'spell.iron_spell_grimoire.{k}.guide'] = guide
lang.update({
 'item.iron_spell_grimoire.gravity_grimoire': 'Gravity Grimoire',
 'ui.iron_spell_grimoire.gravity_grimoire_only': 'Only the Gravity Grimoire can cast this.',
 'tooltip.iron_spell_grimoire.gravity_grimoire.passive': 'Passive - Featherweight: you take no fall damage, and every Gravity hit makes its foe 20% slower for 2 s.',
 'tooltip.iron_spell_grimoire.gravity_grimoire.desc': 'Ten pages of Gravity Magic: dense black orbs, wells that drag everything in, fields that press foes into the ground, and a falling star of compressed mass.',
 'tooltip.iron_spell_grimoire.gravity_grimoire.lore': 'Every page is heavier than the last. Whoever finishes it can lift the book with one finger.',
 'item.iron_spell_grimoire.sound_grimoire': 'Sound Grimoire',
 'ui.iron_spell_grimoire.sound_grimoire_only': 'Only the Sound Grimoire can cast this.',
 'tooltip.iron_spell_grimoire.sound_grimoire.passive': 'Passive - Resonance: every Sound hit leaves its foe ringing; the fifth ring within 6 s shatters in a burst that hurts it and everything right round it.',
 'tooltip.iron_spell_grimoire.sound_grimoire.desc': 'Ten pages of Sound Magic: pulses that hit like a wall, echoes that burst where you stood, a shell that turns missiles back, a silence that saps strength, and a final chord.',
 'tooltip.iron_spell_grimoire.sound_grimoire.lore': 'The pages hum when you hold it. Read aloud, they shake the room.',
})
spells('Gravity Magic', {
 'gravity_bullet': ('Gravity Bullet', 'Fire three dense black orbs one after another; each drags the foes round where it lands in toward it.'),
 'weightless_step': ('Weightless Step', 'Leap and grow light: you glide for a few seconds, and the air you leave throws nearby foes off.'),
 'crushing_field': ('Crushing Field', 'Gravity runs five times over in a circle where you aim: foes in it are pressed down, slowed and hurt every half second.'),
 'repulse': ('Repulse', 'Everything near you is thrown away, and missiles in the air turn back.'),
 'gravity_well': ('Gravity Well', 'A well opens where you aim and drags foes into it for three seconds, then collapses on them.'),
 'gravity_lift': ('Gravity Lift', 'Foes round your target float up helplessly, then are slammed into the ground.'),
 'orbiting_mass': ('Orbiting Mass', 'Four black orbs circle you for 10 s; one by one, each flings itself at the nearest foe.'),
 'heavy_descent': ('Heavy Descent', 'Rise, then fall like a meteor on where you aim: a crater of force where you land.'),
 'mass_domain': ('Mass Domain', 'Mode, 20 s: the air round you grows heavy. Foes near you are slowed and dragged in, you can\'t be moved, and your Gravity spells hit 30% harder.'),
 'starfall_collapse': ('Starfall Collapse', 'A sphere of compressed mass falls from the sky on your target, pulling everything under it in, and implodes.'),
})
spells('Sound Magic', {
 'sonic_pulse': ('Sonic Pulse', 'Three pulses of sound roll out in a cone in front of you.'),
 'echo_step': ('Echo Step', 'Dash ahead; an echo of you stays behind and bursts a second later.'),
 'tuning_strike': ('Tuning Strike', 'A ringing strike on the foe you aim at that leaves two rings of Resonance at once.'),
 'sound_barrier': ('Sound Barrier', '8 s: a shell of sound round you turns missiles back, and you take 30% less damage.'),
 'silence_field': ('Silence Field', '5 s: a circle where sound dies. Foes in it hit 40% softer and move slower.'),
 'thunderclap': ('Thunderclap', 'One clap: everything near you is knocked off its feet and left reeling.'),
 'sonic_lance': ('Sonic Lance', 'A beam of focused sound that runs 28 blocks through everything in its path.'),
 'echo_chamber': ('Echo Chamber', 'Four ringing pillars stand round your target and ring the sound back in on it for 5 s.'),
 'crescendo': ('Crescendo', 'Mode, 20 s: each Sound spell is louder than the last (+8% damage, up to five times). At the top, every spell also breaks out of you in a shockwave.'),
 'final_chord': ('Final Chord', 'Three notes rise over your target, then a blast of sound breaks everything round it.'),
})
json.dump(lang, open(LP, 'w'), indent=2, ensure_ascii=False)
print('gravity and sound assets ok')
