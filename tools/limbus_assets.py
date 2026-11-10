"""Spell icons for the two Limbus-inspired grimoires (original designs):
 - Bloodfiend Grimoire (Don Quixote, La Manchaland): wine-black leather, old gold, a drop of blood over a lance.
 - Caduceus Grimoire (Rien, the Index): black leather, liquid gold, a vial hanging on a chain."""
from PIL import Image, ImageDraw, ImageFilter
import math, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from icons import make
ROOT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/iron_spell_grimoire/textures/item')
M = os.path.join(ROOT, 'spell_book_models')
S = 2
W, H = 256 * S, 384 * S
INK = (10, 6, 8, 255)

def P(x, y): return (int(x * S), int(y * S))

def leather(base, dark, light, seed):
    import random
    rnd = random.Random(seed)
    im = Image.new('RGBA', (64, 64), base)
    px = im.load()
    for y in range(64):
        for x in range(64):
            n = rnd.random()
            c = dark if n < 0.18 else light if n > 0.94 else base
            px[x, y] = c
    return im.filter(ImageFilter.SMOOTH)

def trim(gold, sh, hi):
    im = Image.new('RGBA', (16, 16), gold)
    d = ImageDraw.Draw(im)
    d.line([(0, 0), (15, 0)], fill=hi); d.line([(0, 15), (15, 15)], fill=sh)
    for x in range(0, 16, 4): d.point((x, 7), fill=hi); d.point((x + 2, 8), fill=sh)
    return im

def frame(d, gold, sh, hi):
    for k, c in ((0, INK), (3, sh), (5, gold), (8, hi)):
        d.rectangle([P(14 + k * 0.5, 14 + k * 0.5), P(242 - k * 0.5, 370 - k * 0.5)], outline=c, width=int(4 * S) if k == 0 else int(2 * S))
    for cx, cy in ((22, 22), (234, 22), (22, 362), (234, 362)):   # corner studs
        d.ellipse([P(cx - 9, cy - 9), P(cx + 9, cy + 9)], fill=INK)
        d.ellipse([P(cx - 7, cy - 7), P(cx + 7, cy + 7)], fill=gold)
        d.ellipse([P(cx - 3, cy - 4), P(cx + 1, cy)], fill=hi)

def cover(name, bg, bg2, draw_emblem, gold, sh, hi):
    im = Image.new('RGBA', (W, H), bg)
    d = ImageDraw.Draw(im)
    for y in range(H):   # a soft vertical sheen
        t = abs(y / H - 0.42)
        c = tuple(int(bg[i] * (1 - t) + bg2[i] * t) for i in range(3)) + (255,)
        d.line([(0, y), (W, y)], fill=c)
    frame(d, gold, sh, hi)
    draw_emblem(d)
    im = im.resize((256, 384), Image.LANCZOS)
    im.save(os.path.join(M, name + '_front.png'))
    back = Image.new('RGBA', (W, H), bg)
    db = ImageDraw.Draw(back)
    for y in range(H):
        t = abs(y / H - 0.5)
        c = tuple(int(bg[i] * (1 - t) + bg2[i] * t) for i in range(3)) + (255,)
        db.line([(0, y), (W, y)], fill=c)
    frame(db, gold, sh, hi)
    back.resize((256, 384), Image.LANCZOS).save(os.path.join(M, name + '_back.png'))

# ---------------------------------------------------------------- Don Quixote: a drop of blood over a lance
BG, BG2 = (44, 6, 12, 255), (18, 2, 6, 255)
OLD, OLD_SH, OLD_HI = (216, 176, 96, 255), (120, 84, 34, 255), (255, 236, 170, 255)
RED, RED_SH, RED_HI = (200, 14, 34, 255), (96, 0, 12, 255), (255, 120, 130, 255)

def dq_emblem(d):
    # the lance, corner to corner behind the drop: a long cone with a round vamplate
    a, b = (58, 318), (198, 70)
    ang = math.atan2(b[1] - a[1], b[0] - a[0])
    nx, ny = -math.sin(ang), math.cos(ang)
    tip = (b[0] + math.cos(ang) * 10, b[1] + math.sin(ang) * 10)
    base = (a[0] + math.cos(ang) * 60, a[1] + math.sin(ang) * 60)
    cone = [P(tip[0], tip[1]), P(base[0] + nx * 13, base[1] + ny * 13), P(base[0] - nx * 13, base[1] - ny * 13)]
    d.polygon([(x + 6, y + 6) for x, y in cone], fill=INK)
    d.polygon(cone, fill=OLD, outline=INK)
    d.line([P(base[0] + nx * 6, base[1] + ny * 6), P(tip[0], tip[1])], fill=OLD_HI, width=2 * S)
    d.line([P(a[0], a[1]), P(base[0], base[1])], fill=INK, width=11 * S)
    d.line([P(a[0], a[1]), P(base[0], base[1])], fill=OLD_SH, width=7 * S)
    vx, vy = base
    d.ellipse([P(vx - 22, vy - 22), P(vx + 22, vy + 22)], fill=INK)
    d.ellipse([P(vx - 18, vy - 18), P(vx + 18, vy + 18)], fill=OLD)
    d.ellipse([P(vx - 10, vy - 13), P(vx + 2, vy - 3)], fill=OLD_HI)
    # the drop of blood in the middle
    cx, cy, r = 128, 214, 46
    pts = []
    for i in range(80):
        t = i / 80 * math.tau
        x = math.sin(t) * r * (1 - 0.5 * max(0, math.cos(t)) ** 3)
        y = -math.cos(t) * r * 1.0
        if math.cos(t) > 0: y = -r * 0.2 - (r * 1.6) * math.cos(t) ** 1.5
        pts.append(P(cx + x, cy + y))
    d.polygon([(x + 3 * S, y + 3 * S) for x, y in pts], fill=INK)
    d.line(pts + [pts[0]], fill=INK, width=6 * S)
    d.polygon(pts, fill=RED)
    d.ellipse([P(cx - r * 0.75, cy - r * 0.2), P(cx + r * 0.75, cy + r * 0.95)], fill=RED_SH)
    d.ellipse([P(cx - r * 0.62, cy - r * 0.3), P(cx + r * 0.6, cy + r * 0.8)], fill=RED)
    d.ellipse([P(cx - r * 0.45, cy - r * 0.15), P(cx - r * 0.12, cy + r * 0.35)], fill=RED_HI)
    # a ring of thorns (small drops) round it
    for i in range(12):
        t = i / 12 * math.tau
        x, y = cx + math.cos(t) * 82, cy + math.sin(t) * 82
        d.ellipse([P(x - 4, y - 4), P(x + 4, y + 4)], fill=INK)
        d.ellipse([P(x - 3, y - 3), P(x + 3, y + 3)], fill=RED if i % 2 else OLD)

# ---------------------------------------------------------------- Rien: a vial on a chain, liquid gold
RBG, RBG2 = (20, 20, 22, 255), (6, 6, 8, 255)
GLD, GLD_SH, GLD_HI = (242, 196, 74, 255), (140, 96, 18, 255), (255, 246, 196, 255)
WHITE = (236, 232, 222, 255)

def rien_emblem(d):
    cx = 128
    # the chain, link by link, hanging from the top of the frame
    for i in range(9):
        y = 66 + i * 13
        if i % 2 == 0:
            d.ellipse([P(cx - 6, y - 8), P(cx + 6, y + 8)], outline=INK, width=4 * S)
            d.ellipse([P(cx - 5, y - 7), P(cx + 5, y + 7)], outline=GLD, width=2 * S)
        else:
            d.rectangle([P(cx - 2, y - 8), P(cx + 2, y + 8)], fill=INK)
            d.rectangle([P(cx - 1, y - 7), P(cx + 1, y + 7)], fill=GLD)
    # the stopper and the neck
    d.rectangle([P(cx - 14, 180), P(cx + 14, 196)], fill=INK)
    d.rectangle([P(cx - 11, 182), P(cx + 11, 194)], fill=GLD)
    d.rectangle([P(cx - 8, 196), P(cx + 8, 214)], fill=INK)
    d.rectangle([P(cx - 6, 196), P(cx + 6, 214)], fill=(60, 56, 50, 255))
    # the round body of the vial, half full of swirling gold
    r = 50
    by = 262
    d.ellipse([P(cx - r - 4, by - r - 4), P(cx + r + 4, by + r + 4)], fill=INK)
    d.ellipse([P(cx - r, by - r), P(cx + r, by + r)], fill=(48, 46, 44, 255))
    d.chord([P(cx - r, by - r), P(cx + r, by + r)], 10, 170, fill=GLD)
    d.chord([P(cx - r, by - r), P(cx + r, by + r)], 30, 150, fill=GLD_SH)
    for k in range(3):
        t0 = k * 1.2
        d.arc([P(cx - r * 0.7 + k * 6, by - 10 + k * 8), P(cx + r * 0.6, by + 24 + k * 6)], 200, 340, fill=GLD_HI, width=2 * S)
    d.ellipse([P(cx - r * 0.65, by - r * 0.7), P(cx - r * 0.25, by - r * 0.2)], fill=(220, 220, 214, 120))
    # white rivets and a gold ring round the vial, like an order's seal
    for i in range(16):
        t = i / 16 * math.tau
        x, y = cx + math.cos(t) * 80, by + math.sin(t) * 80
        d.ellipse([P(x - 4, y - 4), P(x + 4, y + 4)], fill=INK)
        d.ellipse([P(x - 3, y - 3), P(x + 3, y + 3)], fill=WHITE if i % 2 else GLD)
    d.ellipse([P(cx - 92, by - 92), P(cx + 92, by + 92)], outline=GLD, width=2 * S)

# (the two powers are not books: their inventory sprites - the hardblood lance and the Caduceus rods - are hand-made pixel art saved directly as textures/item/bloodfiend_grimoire.png and caduceus_grimoire.png)

# ---------------------------------------------------------------- spell icons
B = ((110, 8, 22), (14, 2, 4), (226, 30, 48), (255, 214, 140))   # blood: crimson + old gold accent
make('hardblood_lance', *B, [('m', 'l', 2, 13, 12, 3), ('m', 'l', 3, 13, 13, 3), ('w', 'g', [(11, 2), (14, 1), (13, 4)]), ('a', 'E', 3, 10, 7, 14), ('a', 'p', 1, 15)])
make('justice_charge', *B, [('m', 'l', 1, 9, 14, 7), ('m', 'l', 1, 10, 14, 8), ('w', 'p', 14, 7), ('a', 'E', 2, 7, 6, 12), ('a', 'l', 0, 3, 5, 3), ('a', 'l', 0, 13, 5, 13), ('a', 'l', 0, 5, 3, 5)])
make('sanguine_crescent', *B, [('m', 'a', 1, 1, 14, 14, 200, 340), ('m', 'a', 2, 3, 13, 14, 200, 340), ('w', 'a', 3, 4, 12, 13, 215, 325), ('a', 'p', 4, 12), ('a', 'p', 8, 14), ('a', 'p', 12, 12)])
make('crimson_pact', *B, [('m', 'g', [(8, 1), (12, 8), (8, 13), (4, 8)]), ('w', 'p', 6, 6), ('a', 'l', 1, 14, 14, 14), ('a', 'l', 7, 13, 9, 13)])
make('thirst', *B, [('a', 'E', 6, 6, 9, 9), ('m', 'l', 0, 2, 6, 7), ('m', 'l', 15, 2, 9, 7), ('m', 'l', 0, 13, 6, 9), ('m', 'l', 15, 13, 9, 9), ('w', 'p', 7, 7)])
make('hardblood_barricade', *B, [('m', 'l', x, 14, x + (1 if x < 8 else -1), 4 + abs(x - 8) // 2) for x in range(1, 15, 2)] + [('a', 'l', 0, 15, 15, 15)])
make('hardblood_bow', *B, [('m', 'a', 2, 1, 12, 14, 280, 80), ('a', 'l', 7, 1, 7, 14), ('w', 'l', 3, 8, 14, 8), ('w', 'g', [(13, 6), (15, 8), (13, 10)])])
make('twin_blood_blades', *B, [('m', 'l', 2, 2, 13, 13), ('m', 'l', 13, 2, 2, 13), ('w', 'p', 7, 7), ('a', 'l', 1, 4, 4, 1), ('a', 'l', 11, 1, 14, 4)])
make('blood_fist_barrage', *B, [('m', 'r', 5, 5, 10, 10), ('w', 'l', 5, 6, 10, 6), ('a', 'l', 11, 3, 14, 1), ('a', 'l', 11, 7, 15, 7), ('a', 'l', 11, 11, 14, 14), ('m', 'R', 1, 3, 3, 5), ('m', 'R', 1, 10, 3, 12)])
make('hardblood_greatsword', *B, [('m', 'r', 6, 1, 9, 11), ('w', 'l', 7, 1, 7, 10), ('a', 'r', 3, 11, 12, 12), ('a', 'r', 7, 13, 8, 15)])
make('vampire_mode', *B, [('m', 'g', [(1, 4), (8, 8), (14, 4), (12, 14), (8, 11), (3, 14)]), ('w', 'p', 6, 7), ('w', 'p', 9, 7), ('a', 'p', 6, 10), ('a', 'p', 9, 10)])
make('grand_finale', *B, [('m', 'l', 7, 0, 7, 13), ('m', 'l', 8, 0, 8, 13), ('w', 'g', [(5, 12), (8, 15), (11, 12)]), ('a', 'l', 1, 15, 14, 15), ('a', 'l', 2, 12, 4, 15), ('a', 'l', 13, 12, 11, 15)])
G = ((110, 84, 20), (8, 8, 10), (242, 196, 74), (255, 250, 230))   # gold: liquid gold + white
make('caduceus_lash', *G, [('a', 'l', 2, 1, 2, 5), ('m', 'E', 0, 5, 4, 9), ('m', 'a', 2, 2, 15, 15, 180, 300), ('w', 'a', 3, 3, 14, 14, 200, 280)])
make('gilded_rapier', *G, [('m', 'l', 2, 13, 13, 2), ('w', 'p', 13, 2), ('a', 'a', 1, 10, 6, 15, 0, 360), ('m', 'l', 5, 10, 3, 8)])
make('reapers_prescript', *G, [('a', 'l', 3, 15, 10, 1), ('m', 'a', 4, 0, 15, 10, 180, 330), ('m', 'a', 5, 1, 15, 9, 180, 320), ('w', 'p', 14, 6)])
make('molten_volley', *G, [('m', 'l', 1, 8, 7, 8), ('m', 'l', 4, 3, 10, 5), ('m', 'l', 4, 13, 10, 11), ('w', 'p', 7, 8), ('w', 'p', 10, 5), ('w', 'p', 10, 11), ('a', 'E', 0, 6, 3, 10)])
make('index_lance', *G, [('m', 'l', 1, 14, 14, 1), ('w', 'g', [(11, 1), (15, 0), (14, 4)]), ('a', 'l', 3, 10, 6, 13)])
make('golden_shell', *G, [('m', 'G', [(8, 1), (14, 4), (13, 11), (8, 15), (3, 11), (2, 4)]), ('a', 'G', [(8, 4), (11, 6), (10, 10), (8, 12), (6, 10), (5, 6)]), ('w', 'p', 8, 8)])
make('severing_wire', *G, [('m', 'l', 0, 3, 15, 12), ('m', 'l', 0, 12, 15, 3), ('a', 'l', 0, 8, 15, 8), ('w', 'p', 7, 7)])
make('hammer_of_judgement', *G, [('m', 'r', 2, 2, 13, 6), ('a', 'r', 7, 6, 8, 15), ('w', 'l', 2, 2, 13, 2)])
make('prescript_received', *G, [('w', 'r', 3, 1, 12, 14), ('d', 'l', 5, 4, 10, 4), ('d', 'l', 5, 7, 10, 7), ('d', 'l', 5, 10, 8, 10), ('m', 'R', 2, 0, 13, 15)])
make('furioso', *G, [('m', 'l', 7, 0, 7, 15), ('m', 'l', 0, 7, 15, 7), ('a', 'l', 1, 1, 14, 14), ('a', 'l', 14, 1, 1, 14), ('w', 'E', 5, 5, 10, 10)])
print('limbus assets ok')
