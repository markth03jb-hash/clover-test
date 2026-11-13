import os, math, colorsys
from PIL import Image, ImageDraw
R = os.path.join(os.path.dirname(__file__), "..", "src/main/resources")
A_ = os.path.join(R, "assets/iron_spell_grimoire")
TEX = os.path.join(A_, "textures/item"); M = os.path.join(TEX, "spell_book_models")
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

