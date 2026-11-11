"""Patolli's Light Grimoire: cast animations, spell icons, the cover (night-black leather, white-gold metal, a sunburst
with a sword of light through it - an original design), leather, trim, inventory icon, models, curios tag and lang."""
import json, os, sys, math, colorsys
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
from icons import make
R = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources')
A_ = os.path.join(R, 'assets/iron_spell_grimoire')
TEX = os.path.join(A_, 'textures/item'); M = os.path.join(TEX, 'spell_book_models')

# ------------------------------------------------------------------ animations
E_ = 'easeInOutSine'
def ch(keys):
    out = {}
    for i, (t, v) in enumerate(keys):
        k = {'vector': list(v)}
        if i: k['easing'] = E_
        out[f'{t:g}'] = k
    return out
def anim(length, **bones):
    return {'animation_length': length, 'bones': {n: {c: ch(k) for c, k in cs.items()} for n, cs in bones.items()}}
Z = (0, 0, 0)
A = {}
# Light Sword - right hand sweeps out to the side, then points: the swords go (0.35 s)
A['light_sword'] = anim(0.8,
    right_arm=dict(rotation=[(0, Z), (0.18, (-50, 30, 40)), (0.35, (-92, -6, 0)), (0.6, (-90, -4, 0)), (0.8, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.35, (-14, 0, -18)), (0.8, Z)]),
    torso=dict(rotation=[(0, Z), (0.18, (0, 20, 0)), (0.35, (4, -12, 0)), (0.8, Z)]))
# Light Speed Step - a crouch, then gone (0.12 s)
A['light_step'] = anim(0.5,
    torso=dict(rotation=[(0, Z), (0.12, (30, 0, 0)), (0.3, (10, 0, 0)), (0.5, Z)]),
    right_arm=dict(rotation=[(0, Z), (0.12, (48, 0, 14)), (0.3, (16, 0, 6)), (0.5, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.12, (48, 0, -14)), (0.3, (16, 0, -6)), (0.5, Z)]),
    body=dict(position=[(0, Z), (0.12, (0, -0.8, 0)), (0.3, (0, -0.2, 0)), (0.5, Z)]))
# Arrows of Judgment - right arm swept up to the sky (0.3 s), then brought down to point (0.7 s)
A['light_arrows'] = anim(1.1,
    right_arm=dict(rotation=[(0, Z), (0.3, (-170, 0, 12)), (0.55, (-172, 0, 10)), (0.7, (-95, 0, 0)), (0.9, (-92, 0, 0)), (1.1, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.3, (-30, 0, -30)), (0.9, (-20, 0, -24)), (1.1, Z)]),
    torso=dict(rotation=[(0, Z), (0.3, (-10, 0, 0)), (0.7, (6, 0, 0)), (1.1, Z)]))
# Bright Judgment Whip - lash right-to-left (0.15-0.45 s), then back (0.55-0.85 s)
A['light_whip'] = anim(1.1,
    right_arm=dict(rotation=[(0, Z), (0.15, (-80, 70, 30)), (0.45, (-88, -70, -10)), (0.55, (-86, -72, -10)), (0.85, (-80, 60, 20)), (1.1, Z)]),
    torso=dict(rotation=[(0, Z), (0.15, (0, 30, 0)), (0.45, (0, -34, 0)), (0.55, (0, -34, 0)), (0.85, (0, 26, 0)), (1.1, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.45, (-20, 0, -30)), (1.1, Z)]))
# Healing Ray - both hands held out, palms open, held
A['light_heal'] = anim(1.0,
    right_arm=dict(rotation=[(0, Z), (0.25, (-80, -12, 0)), (0.8, (-80, -12, 0)), (1.0, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.25, (-80, 12, 0)), (0.8, (-80, 12, 0)), (1.0, Z)]),
    torso=dict(rotation=[(0, Z), (0.25, (6, 0, 0)), (0.8, (6, 0, 0)), (1.0, Z)]))
# Blinding Flash - arms crossed over the face, thrown open (0.3 s)
A['light_flash'] = anim(0.8,
    right_arm=dict(rotation=[(0, Z), (0.18, (-110, -40, 0)), (0.3, (-120, 0, 70)), (0.6, (-110, 0, 64)), (0.8, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.18, (-110, 40, 0)), (0.3, (-120, 0, -70)), (0.6, (-110, 0, -64)), (0.8, Z)]),
    torso=dict(rotation=[(0, Z), (0.18, (14, 0, 0)), (0.3, (-12, 0, 0)), (0.8, Z)]))
# Light Swords of Judgment - right arm raised high and held while the swords fill the sky, then cut down (1.3 s)
A['light_swords_judgment'] = anim(1.8,
    right_arm=dict(rotation=[(0, Z), (0.35, (-176, 0, 8)), (1.15, (-178, 0, 6)), (1.3, (-60, 0, 0)), (1.6, (-50, 0, 0)), (1.8, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.35, (-24, 0, -20)), (1.6, (-24, 0, -20)), (1.8, Z)]),
    torso=dict(rotation=[(0, Z), (0.35, (-12, 0, 0)), (1.15, (-14, 0, 0)), (1.3, (14, 0, 0)), (1.8, Z)]))
# Light Shaft - both palms pressed down toward the ground ahead (0.9 s)
A['light_shaft'] = anim(1.4,
    right_arm=dict(rotation=[(0, Z), (0.4, (-160, 0, 20)), (0.9, (-60, 0, 10)), (1.2, (-58, 0, 10)), (1.4, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.4, (-160, 0, -20)), (0.9, (-60, 0, -10)), (1.2, (-58, 0, -10)), (1.4, Z)]),
    torso=dict(rotation=[(0, Z), (0.4, (-14, 0, 0)), (0.9, (16, 0, 0)), (1.2, (14, 0, 0)), (1.4, Z)]))
# Demon Light - hunched, arms out low, then straightening with the dark light
A['light_demon'] = anim(1.4,
    torso=dict(rotation=[(0, Z), (0.4, (26, 0, 0)), (0.8, (-10, 0, 0)), (1.1, (-8, 0, 0)), (1.4, Z)]),
    right_arm=dict(rotation=[(0, Z), (0.4, (-20, 0, 30)), (0.8, (-40, 0, 60)), (1.1, (-38, 0, 58)), (1.4, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.4, (-20, 0, -30)), (0.8, (-40, 0, -60)), (1.1, (-38, 0, -58)), (1.4, Z)]),
    body=dict(position=[(0, Z), (0.4, (0, -0.6, 0)), (0.8, (0, 0.6, 0)), (1.4, Z)]))
# Imperial Swords of Conviction - arms spread wide (the storm), then both raised and brought down together (the giant sword)
A['light_conviction'] = anim(2.2,
    right_arm=dict(rotation=[(0, Z), (0.4, (-90, 0, 80)), (1.2, (-92, 0, 82)), (1.6, (-175, 0, 6)), (1.85, (-70, 0, 4)), (2.0, (-66, 0, 4)), (2.2, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.4, (-90, 0, -80)), (1.2, (-92, 0, -82)), (1.6, (-175, 0, -6)), (1.85, (-70, 0, -4)), (2.0, (-66, 0, -4)), (2.2, Z)]),
    torso=dict(rotation=[(0, Z), (0.4, (-8, 0, 0)), (1.6, (-16, 0, 0)), (1.85, (16, 0, 0)), (2.2, Z)]),
    body=dict(position=[(0, Z), (0.4, (0, 1.2, 0)), (1.6, (0, 2.0, 0)), (1.85, (0, 1.0, 0)), (2.2, Z)]))
P = os.path.join(A_, 'player_animation/arcane_animations.json')
d = json.load(open(P)); d['animations'].update(A); json.dump(d, open(P, 'w'), indent=1)

# ------------------------------------------------------------------ spell icons: night, gold, white
L = ((60, 48, 14), (8, 6, 2), (255, 214, 96), (255, 250, 220))
def sword(x0, y0, x1, y1, k='a'):   # a little sword, hilt at (x0, y0), tip at (x1, y1)
    dx, dy = x1 - x0, y1 - y0; n = max(1, math.hypot(dx, dy)); sx, sy = -dy / n, dx / n
    gx, gy = x0 + dx * 0.25, y0 + dy * 0.25
    return [('m', 'l', x0, y0, round(gx), round(gy)), (k, 'l', round(gx), round(gy), x1, y1),
            ('m', 'l', round(gx + sx * 1.6), round(gy + sy * 1.6), round(gx - sx * 1.6), round(gy - sy * 1.6)), ('w', 'p', x1, y1)]
def star(x, y): return [('w', 'p', x, y), ('a', 'p', x - 1, y), ('a', 'p', x + 1, y), ('a', 'p', x, y - 1), ('a', 'p', x, y + 1)]
make('light_sword', *L, sword(2, 13, 9, 6) + sword(5, 14, 13, 6, 'w') + sword(1, 10, 8, 3) + star(13, 3))
make('light_speed_step', *L, [('m', 'l', 1, 8, 9, 8), ('a', 'l', 2, 6, 7, 6), ('a', 'l', 2, 10, 7, 10), ('m', 'r', 11, 4, 12, 12), ('w', 'r', 12, 5, 13, 11)] + star(14, 3))
make('arrows_of_judgment', *L, sum([sword(x, 1, x, 9 + (x % 3)) for x in (2, 5, 8, 11, 14)], []) + [('m', 'l', 0, 14, 15, 14)] + star(8, 13))
make('bright_judgment_whip', *L, [('m', 'l', 2, 13, 4, 10), ('a', 'l', 4, 10, 8, 8), ('a', 'l', 8, 8, 11, 9), ('a', 'l', 11, 9, 13, 6), ('w', 'l', 13, 6, 14, 3),
     ('w', 'l', 5, 9, 8, 7), ('m', 'l', 9, 10, 12, 10)] + star(14, 2))
make('healing_ray', *L, [('m', 'r', 6, 3, 9, 12), ('m', 'r', 3, 6, 12, 9), ('w', 'r', 7, 4, 8, 11), ('w', 'r', 4, 7, 11, 8)] + star(2, 2) + star(13, 13))
make('blinding_flash', *L, [('a', 'l', 7, 0, 7, 15), ('a', 'l', 0, 7, 15, 7), ('m', 'l', 2, 2, 13, 13), ('m', 'l', 2, 13, 13, 2), ('w', 'E', 5, 5, 10, 10)])
make('light_swords_of_judgment', *L, sum([sword(x, 0, x, 5) for x in (1, 4, 7, 10, 13)], []) + sum([sword(x, 6, x, 11) for x in (2, 5, 8, 11, 14)], []) + [('m', 'l', 0, 14, 15, 14)] + star(4, 13) + star(11, 13))
make('light_shaft', *L, [('a', 'r', 5, 0, 10, 12), ('w', 'r', 7, 0, 8, 12), ('m', 'e', 1, 11, 14, 15), ('a', 'e', 3, 12, 12, 14)])
make('demon_light', *((24, 24, 28), (2, 2, 4), (40, 40, 48), (245, 245, 255)), [('m', 'E', 3, 3, 12, 12), ('a', 'e', 4, 0, 11, 2), ('w', 'p', 6, 7), ('w', 'p', 9, 7),
     ('a', 'l', 1, 14, 4, 9), ('a', 'l', 14, 14, 11, 9), ('w', 'p', 7, 1)])
make('imperial_swords_of_conviction', *L, sword(7, 1, 7, 14, 'w') + sword(8, 1, 8, 14) + [('m', 'l', 4, 4, 11, 4)] + sum([sword(round(7.5 + 7 * math.cos(a)), round(7.5 + 7 * math.sin(a)), round(7.5 + 3.5 * math.cos(a)), round(7.5 + 3.5 * math.sin(a))) for a in [i * math.tau / 6 + 0.5 for i in range(6)]], []))

# ------------------------------------------------------------------ cover: night leather, white-gold metal, a sunburst pierced by a sword of light
S = 2; W, H = 256 * S, 384 * S
INK = (14, 10, 4, 255); NIGHT, NIGHT_SH, NIGHT_HI = (24, 20, 30, 255), (12, 10, 16, 255), (40, 34, 48, 255)
GOLD, GOLD_SH, GOLD_HI, WHITE = (250, 214, 110, 255), (190, 140, 40, 255), (255, 246, 200, 255), (255, 255, 248, 255)
def Pp(x, y): return (x * S, y * S)
def poly(d, pts, fill, outline=INK, w=3):
    q = [Pp(*p) for p in pts]
    if outline: d.line(q + [q[0]], fill=outline, width=w * S * 2, joint='curve')
    d.polygon(q, fill=fill)
def circ(d, cx, cy, r, fill=None, outline=None, w=0):
    d.ellipse([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], fill=fill, outline=outline, width=int(w * S))
def glint(d, x, y, s, col=WHITE):
    poly(d, [(x, y - s), (x + s * 0.22, y - s * 0.22), (x + s, y), (x + s * 0.22, y + s * 0.22), (x, y + s), (x - s * 0.22, y + s * 0.22), (x - s, y), (x - s * 0.22, y - s * 0.22)], col, None)
def frame(d):
    d.rectangle([0, 0, W, H], fill=NIGHT)
    poly(d, [(256, 0), (256, 384), (0, 384), (0, 330), (190, 330), (190, 0)], NIGHT_SH, None)
    for (x0, y0, x1, y1, col) in [(4, 4, 252, 380, INK), (7, 7, 249, 377, GOLD), (7, 7, 249, 9, GOLD_HI), (7, 7, 9, 377, GOLD_HI),
                                  (7, 374, 249, 377, GOLD_SH), (246, 7, 249, 377, GOLD_SH), (17, 17, 239, 367, INK), (19, 19, 237, 365, NIGHT)]:
        d.rectangle([x0 * S, y0 * S, x1 * S, y1 * S], fill=col)
    poly(d, [(237, 19), (237, 365), (19, 365), (19, 330), (190, 330), (190, 19)], NIGHT_SH, None)
    for (sx, sy, x, y) in [(1, 1, 4, 4), (-1, 1, 252, 4), (1, -1, 4, 380), (-1, -1, 252, 380)]:
        poly(d, [(x, y), (x + sx * 44, y), (x + sx * 44, y + sy * 10), (x + sx * 10, y + sy * 44), (x, y + sy * 44)], GOLD, INK, 3)
        circ(d, x + sx * 15, y + sy * 15, 4.5, fill=GOLD_HI, outline=INK, w=2.5)
    for y in (60, 324):
        d.rectangle([19 * S, (y - 3) * S, 237 * S, (y + 3) * S], fill=INK)
        d.rectangle([19 * S, (y - 1.5) * S, 237 * S, (y + 1.5) * S], fill=GOLD)
def sun(d, cx, cy, R, rays=16):
    for i in range(rays):   # long and short rays, ink-edged
        a = i / rays * math.tau; long_ = i % 2 == 0
        r1 = R * (1.0 if long_ else 0.72); hw = 0.09 if long_ else 0.12
        pts = [(cx + math.cos(a - hw) * R * 0.42, cy + math.sin(a - hw) * R * 0.42), (cx + math.cos(a) * r1, cy + math.sin(a) * r1),
               (cx + math.cos(a + hw) * R * 0.42, cy + math.sin(a + hw) * R * 0.42)]
        poly(d, pts, GOLD if long_ else GOLD_SH, INK, 2)
    circ(d, cx, cy, R * 0.44, fill=INK); circ(d, cx, cy, R * 0.4, fill=GOLD); circ(d, cx, cy, R * 0.3, fill=GOLD_HI); circ(d, cx, cy, R * 0.18, fill=WHITE)
def blade(d, cx, top, bot, w):
    poly(d, [(cx, top), (cx + w, top + w * 3), (cx + w, bot - 40), (cx - w, bot - 40), (cx - w, top + w * 3)], WHITE, INK, 3)
    poly(d, [(cx, top + 6), (cx + w * 0.4, top + w * 3), (cx + w * 0.4, bot - 44), (cx, bot - 44)], GOLD_HI, None)
    poly(d, [(cx - w * 4, bot - 40), (cx + w * 4, bot - 40), (cx + w * 3, bot - 32), (cx - w * 3, bot - 32)], GOLD, INK, 3)   # crossguard
    poly(d, [(cx - w * 0.6, bot - 32), (cx + w * 0.6, bot - 32), (cx + w * 0.6, bot - 8), (cx - w * 0.6, bot - 8)], GOLD_SH, INK, 2)
    circ(d, cx, bot - 4, w * 0.9, fill=GOLD, outline=INK, w=2)
def cover(big):
    im = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(im)
    frame(d)
    cx, cy = 128, 182 if big else 192
    R = 84 if big else 52
    for k, a in ((R + 24, 36), (R + 14, 64)):
        g = Image.new('RGBA', (W, H)); gd = ImageDraw.Draw(g); circ(gd, cx, cy, k, fill=(255, 220, 120, a))
        im = Image.alpha_composite(im, g); d = ImageDraw.Draw(im)
    sun(d, cx, cy, R)
    if big:
        blade(d, cx, cy - 120, cy + 140, 9)
        for (x, y, s) in ((60, 90, 9), (198, 110, 7), (70, 300, 6), (190, 280, 8), (cx, cy - 124, 12)): glint(d, x, y, s)
    else:
        glint(d, cx, cy, 16)
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
cover(True).save(os.path.join(M, 'light_grimoire_front.png'))
cover(False).save(os.path.join(M, 'light_grimoire_back.png'))
shift(Image.open(os.path.join(M, 'dark_grimoire_leather.png')), 0.75, 0.3, 0.55).save(os.path.join(M, 'light_grimoire_leather.png'))
t = Image.open(os.path.join(M, 'spatial_grimoire_trim.png')).convert('RGBA'); px = t.load()
for y in range(t.height):
    for x in range(t.width):
        r, g, b, a = px[x, y]; k = 0.5 + 0.6 * min(1.2, (r + g + b) / 450.0)
        px[x, y] = (min(255, int(255 * k)), min(255, int(214 * k)), min(255, int(110 * k)), a)
t.save(os.path.join(M, 'light_grimoire_trim.png'))
icon = shift(Image.open(os.path.join(TEX, 'dark_grimoire.png')), 0.75, 0.25, 0.6); px = icon.load()
for (x, y, c) in ((7, 7, (255, 255, 240)), (8, 7, (255, 230, 140)), (7, 8, (255, 230, 140)), (8, 8, (255, 255, 240)), (7, 6, (255, 214, 96)), (8, 9, (255, 214, 96)), (6, 7, (255, 214, 96)), (9, 8, (255, 214, 96))):
    if px[x, y][3]: px[x, y] = c + (255,)
icon.save(os.path.join(TEX, 'light_grimoire.png'))

# ------------------------------------------------------------------ models and the curios tag
MD = os.path.join(A_, 'models/item')
for suf in ('', '_model', '_open'):
    s = open(os.path.join(MD, f'time_grimoire{suf}.json')).read().replace('time_grimoire', 'light_grimoire')
    open(os.path.join(MD, f'light_grimoire{suf}.json'), 'w').write(s)
CT = os.path.join(R, 'data/curios/tags/items/spellbook.json')
c = json.load(open(CT))
if 'iron_spell_grimoire:light_grimoire' not in c['values']: c['values'].append('iron_spell_grimoire:light_grimoire')
json.dump(c, open(CT, 'w'), indent=2)

# ------------------------------------------------------------------ lang
LP = os.path.join(A_, 'lang/en_us.json')
lang = json.load(open(LP))
lang.update({
 'item.iron_spell_grimoire.light_grimoire': 'Light Grimoire',
 'tooltip.iron_spell_grimoire.light_grimoire.passive': 'Passive - Radiance: every Light spell leaves you moving at light speed for a moment (+30% speed for 2 s).',
 'tooltip.iron_spell_grimoire.light_grimoire.desc': 'Ten pages of Light Magic: swords of light, arrows of judgment, a whip sixteen blocks long and a pillar of divine punishment.',
 'tooltip.iron_spell_grimoire.light_grimoire.lore': 'Light is the fastest magic there is. By the time they see it, it has already struck.',
 'ui.iron_spell_grimoire.light_grimoire_only': 'Only the Light Grimoire can cast this.',
 'spell.iron_spell_grimoire.light_sword': 'Light Sword',
 'spell.iron_spell_grimoire.light_sword.guide': 'Three swords of light form at your side and streak into the foe you aim at.',
 'spell.iron_spell_grimoire.light_speed_step': 'Light Speed Step',
 'spell.iron_spell_grimoire.light_speed_step.guide': 'Flash-step twelve blocks in a line of light. Aim at a foe and you come out behind it and strike.',
 'spell.iron_spell_grimoire.arrows_of_judgment': 'Arrows of Judgment',
 'spell.iron_spell_grimoire.arrows_of_judgment.guide': 'Twelve arrows of light rise over you, hang for a heartbeat, and fall on the foes around you.',
 'spell.iron_spell_grimoire.bright_judgment_whip': 'Bright Judgment Whip',
 'spell.iron_spell_grimoire.bright_judgment_whip.guide': 'A whip of light sixteen blocks long lashes across in front of you, then back.',
 'spell.iron_spell_grimoire.healing_ray': 'Healing Ray of Light',
 'spell.iron_spell_grimoire.healing_ray.guide': 'Heal the ally you aim at (or yourself), and a little more over the next two seconds.',
 'spell.iron_spell_grimoire.blinding_flash': 'Blinding Flash',
 'spell.iron_spell_grimoire.blinding_flash.guide': 'Light bursts out of you: foes within 8 blocks are slowed, hit softer and lose track of you for 3 s. Players are blinded white for a moment.',
 'spell.iron_spell_grimoire.light_swords_of_judgment': 'Light Swords of Judgment',
 'spell.iron_spell_grimoire.light_swords_of_judgment.guide': 'Rows of swords open in the sky over your target, shimmer, and rain down in a sweep.',
 'spell.iron_spell_grimoire.light_shaft': 'Light Shaft of Divine Punishment',
 'spell.iron_spell_grimoire.light_shaft.guide': 'A circle draws itself on the ground where you aim, then a vast pillar of light comes down on it.',
 'spell.iron_spell_grimoire.demon_light': 'Demon Light',
 'spell.iron_spell_grimoire.demon_light.guide': 'Mode, 20 s: your light turns black and white. Light spells hit 35% harder, every cooldown runs twice as fast, and you move faster.',
 'spell.iron_spell_grimoire.imperial_swords_of_conviction': 'Imperial Swords of Conviction',
 'spell.iron_spell_grimoire.imperial_swords_of_conviction.guide': 'A storm of swords circles your target and closes in, wave after wave, then one giant sword falls. Surpassing Limits: Sovereign of Light - a wider storm, a bigger sword and a pillar of light.',
})
json.dump(lang, open(LP, 'w'), indent=2, ensure_ascii=False)
print('light assets ok')
