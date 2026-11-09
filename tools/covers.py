"""Draws a unique front (and a quieter back) cover for every grimoire. Original designs, 256x384, drawn at 2x and scaled down."""
from PIL import Image, ImageDraw, ImageFilter, ImageChops
import math, random, os
OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/iron_spell_grimoire/textures/item/spell_book_models')
S = 2; W, H = 256 * S, 384 * S
CX, CY = W // 2, int(H * 0.45)

def rgb(h): return ((h >> 16) & 255, (h >> 8) & 255, h & 255)
def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

class Cv:
    def __init__(self, top, bot, seed=1, grain=10):
        random.seed(seed)
        self.im = Image.new('RGBA', (W, H))
        px = self.im.load()
        for y in range(H):
            c = lerp(top, bot, y / H)
            for x in range(W):
                n = random.randint(-grain, grain)
                v = abs(x - W / 2) / (W / 2)
                k = 1 - 0.25 * v * v
                px[x, y] = (max(0, min(255, int(c[0] * k + n))), max(0, min(255, int(c[1] * k + n))), max(0, min(255, int(c[2] * k + n))), 255)
        self.bg = self.im.copy()
    def layer(self): 
        l = Image.new('RGBA', (W, H), (0, 0, 0, 0)); return l, ImageDraw.Draw(l)
    def put(self, l, glow=None, gr=10, ga=1.0):
        if glow:
            g = Image.new('RGBA', (W, H), (0, 0, 0, 0))
            tint = Image.new('RGBA', (W, H), glow + (255,))
            a = l.split()[3].filter(ImageFilter.GaussianBlur(gr))
            a = a.point(lambda v: min(255, int(v * 1.6 * ga)))
            g = Image.composite(tint, g, a)
            self.im = Image.alpha_composite(self.im, g)
        self.im = Image.alpha_composite(self.im, l)
    def save(self, name):
        self.im.resize((256, 384), Image.LANCZOS).convert('RGBA').save(os.path.join(OUT, name + '.png'))

def clover(d, cx, cy, r, n, fill, ang0=-math.pi / 2):
    cx, cy, r = cx * S, cy * S, r * S
    for i in range(n):
        a = ang0 + i * 2 * math.pi / n
        lx, ly = cx + math.cos(a) * r * 0.55, cy + math.sin(a) * r * 0.55
        d.ellipse([lx - r * 0.5, ly - r * 0.5, lx + r * 0.5, ly + r * 0.5], fill=fill)
    d.line([cx, cy, cx, cy + r * 1.3], fill=fill, width=int(r * 0.12))

def frame(d, col, m=18, w=3, notch=0, double=True):
    d.rectangle([m * S, m * S, W - m * S, H - m * S], outline=col, width=w * S)
    if double: d.rectangle([(m + 7) * S, (m + 7) * S, W - (m + 7) * S, H - (m + 7) * S], outline=col, width=max(1, S))
    if notch:
        for (x, y) in [(m, m), (256 - m, m), (m, 384 - m), (256 - m, 384 - m)]:
            d.rectangle([(x - notch) * S, (y - notch) * S, (x + notch) * S, (y + notch) * S], fill=col)

def poly(d, pts, **k): d.polygon([(x * S, y * S) for x, y in pts], **k)
def line(d, pts, **k):
    d.line([(x * S, y * S) for x, y in pts], **{**k, 'width': int(k.get('width', 1) * S)})

def crack(d, x, y, ang, length, col, w, rnd, branch=2):
    pts = [(x, y)]
    for _ in range(int(length / 8)):
        ang += rnd.uniform(-0.6, 0.6); x += math.cos(ang) * 8; y += math.sin(ang) * 8; pts.append((x, y))
    line(d, pts, fill=col, width=w)
    if branch and len(pts) > 3:
        bx, by = pts[len(pts) // 2]
        crack(d, bx, by, ang + rnd.choice([-1, 1]) * 0.9, length * 0.45, col, max(1, w - 1), rnd, branch - 1)

# ---------------------------------------------------------------- the fourteen covers
def black():   # Asta: torn black leather, red cracks, five black leaves
    c = Cv((10, 10, 10), (18, 14, 14), 1); r = random.Random(5)
    l, d = c.layer(); [crack(d, 128, 175, a, 150, (255, 40, 24, 255), 2, r) for a in [0.3, 1.2, 2.2, 3.1, 4.0, 5.0, 5.7]]
    c.put(l, (255, 30, 20), 6, 0.8)
    l, d = c.layer(); frame(d, (138, 122, 110, 255), 16, 3, 0, False)
    for i in range(14): poly(d, [(16 + i * 17, 16), (24 + i * 17, 16), (20 + i * 17, 28 + (i % 3) * 5)], fill=(138, 122, 110, 255))
    clover(d, CX / S, CY / S, 52, 5, (6, 5, 6, 255)); c.put(l, (255, 30, 20), 8, 1.2)
    return c
# Every cover below (Asta's is kept as it was) keeps the middle clear: an empty medallion where a clover can sit.
X0, Y0 = 128, CY / S          # the centre, in 1x units
R0 = 62                       # nothing is drawn inside this radius

def ringpts(r, a0, a1, n=60):
    return [(X0 + math.cos(a0 + (a1 - a0) * t / n) * r, Y0 + math.sin(a0 + (a1 - a0) * t / n) * r) for t in range(n + 1)]

def dark():    # Yami: violet-black; ripples of darkness spreading out from the medallion, crescents above and below
    c = Cv((8, 5, 14), (22, 10, 34), 2)
    l, d = c.layer()
    for k in range(5): line(d, ringpts(R0 + 8 + k * 16, 0, 6.29, 90), fill=(120, 60, 200, 110 - k * 18), width=2)
    c.put(l)
    l, d = c.layer()
    for (y, dy) in ((58, 8), (290, -8)):   # a thin crescent moon top and bottom, horns pointing at the medallion
        d.ellipse([(X0 - 20) * S, (y - 20) * S, (X0 + 20) * S, (y + 20) * S], fill=(190, 160, 240, 255))
        d.ellipse([(X0 - 20) * S, (y - 20 + dy) * S, (X0 + 20) * S, (y + 20 + dy) * S], fill=(0, 0, 0, 0))
    c.put(l, (150, 70, 255), 10, 0.9)
    l, d = c.layer(); frame(d, (180, 169, 200, 255), 18, 2, 6); c.put(l); return c

def wind():    # Yuno: jade green; wind spirals sweeping round the medallion, feathers at the corners
    c = Cv((8, 34, 24), (14, 60, 42), 3)
    l, d = c.layer()
    for k in range(4):
        a0 = math.radians(k * 90 + 20); line(d, ringpts(R0 + 12 + k * 9, a0, a0 + 2.6), fill=(190, 255, 215, 210), width=2)
    for k in range(3):
        a0 = math.radians(k * 120 + 60); line(d, ringpts(R0 + 4, a0, a0 + 1.2, 30), fill=(230, 255, 240, 230), width=1)
    c.put(l, (110, 255, 170), 6, 0.6)
    l, d = c.layer(); frame(d, (231, 217, 160, 255), 16, 2, 0, True)
    for (x, y, sx) in [(36, 40, 1), (220, 40, -1), (36, 344, 1), (220, 344, -1)]:   # a feather in each corner
        line(d, [(x, y + 18), (x + 22 * sx, y - 10)], fill=(231, 217, 160, 255), width=2)
        for t in range(1, 6): line(d, [(x + 4 * t * sx, y + 18 - 5 * t), (x + (4 * t - 6) * sx, y + 18 - 5 * t - 7)], fill=(231, 217, 160, 200), width=1)
    c.put(l); return c

def mercury(): # Nozel: chrome-blue; ripples spreading from the medallion, mercury drops hanging from the top
    c = Cv((26, 32, 44), (60, 72, 92), 4, 6)
    l, d = c.layer()
    for k in range(6): line(d, ringpts(R0 + 6 + k * 15, 0, 6.29, 90), fill=(220, 232, 248, 170 - k * 24), width=1)
    c.put(l, (170, 200, 240), 6, 0.5)
    l, d = c.layer()
    for (x, ln) in [(70, 34), (100, 52), (156, 52), (186, 34)]:
        line(d, [(x, 26), (x, 26 + ln)], fill=(215, 226, 244, 255), width=2)
        d.ellipse([(x - 5) * S, (26 + ln - 2) * S, (x + 5) * S, (26 + ln + 10) * S], fill=(215, 226, 244, 255))
    for edge in (22, 362):
        line(d, [(x, edge + math.sin(x / 9.0) * 4) for x in range(20, 237, 3)], fill=(220, 230, 245, 255), width=2)
    line(d, [(20, 22), (20, 362)], fill=(220, 230, 245, 255), width=2); line(d, [(236, 22), (236, 362)], fill=(220, 230, 245, 255), width=2)
    c.put(l, (190, 215, 255), 5, 0.5); return c

def fire():    # Magna: scorched brown; a ring of flame tongues round the medallion, a fire rising from the foot
    c = Cv((26, 12, 6), (60, 22, 8), 5)
    l, d = c.layer(); r = random.Random(9)
    for i in range(18):
        a = i * math.pi / 9; h = r.randint(14, 26)
        p0 = (X0 + math.cos(a - 0.12) * (R0 + 2), Y0 + math.sin(a - 0.12) * (R0 + 2)); p1 = (X0 + math.cos(a + 0.12) * (R0 + 2), Y0 + math.sin(a + 0.12) * (R0 + 2))
        tip = (X0 + math.cos(a + 0.1) * (R0 + h), Y0 + math.sin(a + 0.1) * (R0 + h))
        poly(d, [p0, p1, tip], fill=(255, 140, 30, 220))
    for i in range(22):
        x = 24 + i * 10; hh = r.randint(30, 80)
        poly(d, [(x - 8, 366), (x + 8, 366), (x + r.randint(-5, 5), 366 - hh)], fill=(255, 120 + r.randint(0, 60), 20, 190))
    c.put(l, (255, 100, 20), 10, 0.6)
    l, d = c.layer(); frame(d, (217, 169, 76, 255), 16, 3, 5, False); c.put(l); return c

def lightning(): # Luck: midnight blue; two bolts down the sides, sparks crackling off the medallion
    c = Cv((4, 10, 26), (10, 28, 62), 6)
    l, d = c.layer(); r = random.Random(3)
    for sx in (1, -1):
        x = X0 + 92 * sx
        line(d, [(x, 36), (x - 14 * sx, 120), (x + 4 * sx, 128), (x - 16 * sx, 230), (x + 8 * sx, 236), (x - 6 * sx, 340)], fill=(150, 205, 255, 255), width=4)
    for a in [0.4, 1.6, 2.8, 3.6, 4.8, 5.8]: crack(d, X0 + math.cos(a) * (R0 + 2), Y0 + math.sin(a) * (R0 + 2), a, 40, (190, 225, 255, 255), 2, r, 1)
    c.put(l, (90, 170, 255), 8, 1.0)
    l, d = c.layer()
    for sx in (1, -1):
        x = X0 + 92 * sx
        line(d, [(x, 36), (x - 14 * sx, 120), (x + 4 * sx, 128), (x - 16 * sx, 230), (x + 8 * sx, 236), (x - 6 * sx, 340)], fill=(255, 255, 255, 255), width=1)
    frame(d, (237, 231, 200, 255), 16, 2, 0, True); c.put(l); return c

def hellfire(): # Mereoleona: crimson; a lion's mane of flame claws round the medallion
    c = Cv((34, 6, 8), (74, 12, 10), 7)
    l, d = c.layer()
    for i in range(16):
        a = i * math.pi / 8; r0, r1 = R0 + 2, R0 + 34 + (i % 2) * 18
        poly(d, [(X0 + math.cos(a - 0.12) * r0, Y0 + math.sin(a - 0.12) * r0), (X0 + math.cos(a + 0.12) * r0, Y0 + math.sin(a + 0.12) * r0), (X0 + math.cos(a + 0.3) * r1, Y0 + math.sin(a + 0.3) * r1)], fill=(255, 70, 30, 235))
    c.put(l, (255, 60, 20), 8, 0.9)
    l, d = c.layer(); frame(d, (232, 182, 82, 255), 16, 3, 0, False); c.put(l); return c

def bone():    # Zenon: dark red-black; a spine above and below the medallion with ribs curving round it
    c = Cv((22, 10, 10), (40, 18, 18), 8)
    l, d = c.layer(); bc = (237, 227, 204, 255)
    line(d, [(X0, 40), (X0, Y0 - R0 - 4)], fill=bc, width=6); line(d, [(X0, Y0 + R0 + 4), (X0, 312)], fill=bc, width=6)
    for k in range(4):   # ribs curving round the medallion
        rr = R0 + 10 + k * 13
        for s_ in (-1, 1):
            line(d, ringpts(rr, -math.pi / 2 + s_ * 0.35, -math.pi / 2 + s_ * 1.25, 20), fill=bc, width=4)
            line(d, ringpts(rr, math.pi / 2 - s_ * 0.35, math.pi / 2 - s_ * 1.25, 20), fill=bc, width=4)
    for y in (52, 70, 88, 258, 276, 294): d.ellipse([(X0 - 6) * S, (y - 3) * S, (X0 + 6) * S, (y + 3) * S], fill=bc)
    c.put(l, (240, 220, 190), 6, 0.3)
    l, d = c.layer(); frame(d, bc, 16, 3, 0, False)
    for (x, y) in [(28, 28), (228, 28), (28, 356), (228, 356)]:
        d.ellipse([(x - 8) * S, (y - 5) * S, (x + 1) * S, (y + 4) * S], fill=bc); d.ellipse([(x - 1) * S, (y - 5) * S, (x + 8) * S, (y + 4) * S], fill=bc)
    c.put(l); return c

def steel():   # Klaus: grey plate; bolted panels, and a brass gear rim round the medallion
    c = Cv((48, 54, 62), (72, 80, 92), 9, 7)
    l, d = c.layer()
    for (x0, y0, x1, y1) in [(26, 26, 230, 96), (26, 252, 230, 358)]:
        d.rectangle([x0 * S, y0 * S, x1 * S, y1 * S], outline=(30, 34, 40, 255), width=3 * S)
        for (x, y) in [(x0 + 9, y0 + 9), (x1 - 9, y0 + 9), (x0 + 9, y1 - 9), (x1 - 9, y1 - 9)]: d.ellipse([(x - 4) * S, (y - 4) * S, (x + 4) * S, (y + 4) * S], fill=(210, 216, 226, 255))
    c.put(l)
    l, d = c.layer()
    for i in range(16):
        a = i * math.pi / 8
        poly(d, [(X0 + math.cos(a - .1) * (R0 + 6), Y0 + math.sin(a - .1) * (R0 + 6)), (X0 + math.cos(a + .1) * (R0 + 6), Y0 + math.sin(a + .1) * (R0 + 6)),
                 (X0 + math.cos(a + .08) * (R0 + 18), Y0 + math.sin(a + .08) * (R0 + 18)), (X0 + math.cos(a - .08) * (R0 + 18), Y0 + math.sin(a - .08) * (R0 + 18))], fill=(217, 169, 76, 255))
    line(d, ringpts(R0 + 4, 0, 6.29, 90), fill=(217, 169, 76, 255), width=5)
    c.put(l); return c

def string():  # Vanessa: rose cloth; stitched border, a thread looping round the medallion, a needle in the corner
    c = Cv((74, 22, 52), (110, 36, 78), 10, 14)
    l, d = c.layer()
    for k in range(40):
        x = 24 + k * 5.6; d.line([x * S, 22 * S, (x + 3) * S, 22 * S], fill=(242, 221, 232, 255), width=2 * S); d.line([x * S, 362 * S, (x + 3) * S, 362 * S], fill=(242, 221, 232, 255), width=2 * S)
    for k in range(60):
        y = 28 + k * 5.5; d.line([22 * S, y * S, 22 * S, (y + 3) * S], fill=(242, 221, 232, 255), width=2 * S); d.line([234 * S, y * S, 234 * S, (y + 3) * S], fill=(242, 221, 232, 255), width=2 * S)
    c.put(l)
    l, d = c.layer()
    pts = [(X0 + math.cos(t / 40) * (R0 + 10 + 7 * math.sin(t / 11)), Y0 + math.sin(t / 40) * (R0 + 10 + 7 * math.sin(t / 11))) for t in range(0, 252)]
    line(d, pts, fill=(255, 140, 198, 255), width=2)
    line(d, [(196, 60), (218, 34)], fill=(255, 140, 198, 255), width=1)
    c.put(l, (255, 120, 190), 5, 0.7)
    l, d = c.layer(); line(d, [(186, 72), (214, 38)], fill=(245, 235, 240, 255), width=3); d.ellipse([211 * S, 32 * S, 219 * S, 44 * S], outline=(245, 235, 240, 255), width=S)
    c.put(l); return c

def spatial(): # Finral: deep indigo; a golden gate ring round the medallion, small gates drifting outside it
    c = Cv((6, 4, 26), (22, 16, 64), 11)
    l, d = c.layer()
    line(d, ringpts(R0 + 6, 0, 6.29, 90), fill=(231, 199, 102, 255), width=5)
    line(d, ringpts(R0 + 13, 0, 6.29, 90), fill=(156, 120, 255, 255), width=2)
    c.put(l, (156, 120, 255), 10, 0.8)
    l, d = c.layer()
    for (x, y, s_) in [(52, 64, 10), (204, 84, 8), (56, 300, 9), (200, 296, 12)]: d.ellipse([(x - s_ * .6) * S, (y - s_) * S, (x + s_ * .6) * S, (y + s_) * S], outline=(231, 199, 102, 255), width=2 * S, fill=(30, 12, 90, 255))
    frame(d, (231, 199, 102, 255), 16, 2, 4, True); c.put(l); return c

def water():   # Noelle: sea blue; waves above and below, a crest of foam curling round the medallion, bubbles
    c = Cv((6, 26, 52), (10, 70, 120), 12)
    l, d = c.layer()
    for k in range(3):
        for y0 in (60 + k * 22, 270 + k * 22):
            line(d, [(x, y0 + math.sin(x / 14.0 + k) * 7) for x in range(24, 233, 3)], fill=(130, 210, 255, 220 - k * 40), width=3)
    c.put(l, (90, 190, 255), 6, 0.5)
    l, d = c.layer()
    line(d, ringpts(R0 + 6, math.radians(150), math.radians(390)), fill=(92, 200, 255, 255), width=6)
    line(d, ringpts(R0 + 12, math.radians(170), math.radians(360), 40), fill=(228, 242, 255, 230), width=2)
    for (x, y, s_) in [(52, 150, 6), (204, 140, 8), (190, 200, 4), (60, 210, 7)]: d.ellipse([(x - s_) * S, (y - s_) * S, (x + s_) * S, (y + s_) * S], outline=(228, 242, 255, 255), width=2 * S)
    c.put(l, (110, 205, 255), 8, 0.7)
    l, d = c.layer(); frame(d, (228, 242, 255, 255), 16, 2, 0, False)
    line(d, [(x, 366 + math.sin(x / 8.0) * 4) for x in range(24, 233, 2)], fill=(228, 242, 255, 255), width=3); c.put(l); return c

def mirror():  # Gauche: silver glass; angled mirror panes round the edges, the middle left clear
    c = Cv((52, 52, 70), (96, 96, 122), 13, 5)
    l, d = c.layer(); r = random.Random(11)
    cells = [(28, 28, 118, 96), (126, 28, 228, 90), (28, 104, 58, 250), (198, 98, 228, 250), (28, 258, 132, 356), (140, 262, 228, 356)]
    for (x0, y0, x1, y1) in cells:
        poly(d, [(x0, y0 + r.randint(0, 8)), (x1 - r.randint(0, 8), y0), (x1, y1 - r.randint(0, 8)), (x0 + r.randint(0, 8), y1)], fill=(120 + r.randint(0, 40), 120 + r.randint(0, 40), 150 + r.randint(0, 40), 255), outline=(232, 232, 242, 255))
        line(d, [(x0 + 8, y1 - 10), (x1 - 10, y0 + 10)], fill=(255, 176, 216, 200), width=2)
    c.put(l, (255, 176, 216), 5, 0.3)
    l, d = c.layer()
    for i in range(12):   # a frame of small glass facets round the medallion
        a = i * math.pi / 6
        poly(d, [(X0 + math.cos(a) * (R0 + 4), Y0 + math.sin(a) * (R0 + 4)), (X0 + math.cos(a + 0.26) * (R0 + 14), Y0 + math.sin(a + 0.26) * (R0 + 14)), (X0 + math.cos(a + 0.52) * (R0 + 4), Y0 + math.sin(a + 0.52) * (R0 + 4))], fill=(200, 200, 225, 255), outline=(240, 240, 250, 255))
    frame(d, (232, 232, 242, 255), 14, 3, 0, False); c.put(l); return c

def rift():    # Langris: plum black; straight cuts in space that stop at the medallion's edge, cracks off them
    c = Cv((10, 4, 16), (30, 10, 40), 14)
    l, d = c.layer(); r = random.Random(21)
    for (a, ln) in [(-2.4, 130), (-0.6, 120), (0.75, 150), (2.6, 120), (-1.5, 70), (1.65, 110)]:
        p0 = (X0 + math.cos(a) * (R0 + 6), Y0 + math.sin(a) * (R0 + 6)); p1 = (X0 + math.cos(a) * (R0 + 6 + ln), Y0 + math.sin(a) * (R0 + 6 + ln))
        p1 = (max(22, min(234, p1[0])), max(22, min(362, p1[1])))
        line(d, [p0, p1], fill=(255, 220, 255, 255), width=3)
        for k in range(2):
            t = r.uniform(0.3, 0.9); crack(d, p0[0] + (p1[0] - p0[0]) * t, p0[1] + (p1[1] - p0[1]) * t, a + r.choice([-1, 1]) * 1.2, 28, (224, 64, 216, 255), 2, r, 1)
    c.put(l, (224, 64, 216), 10, 1.0)
    l, d = c.layer(); frame(d, (214, 160, 232, 255), 16, 2, 0, False); c.put(l); return c

# ---------------------------------------------------------------- the shared grimoire look: a clover crest, corner brackets, worn leather
ACC = dict(dark=(180, 169, 200), wind=(231, 217, 160), mercury=(220, 232, 248), fire=(217, 169, 76), lightning=(237, 231, 200),
           hellfire=(232, 182, 82), bone=(237, 227, 204), steel=(217, 169, 76), string=(242, 221, 232), spatial=(231, 199, 102),
           water=(228, 242, 255), mirror=(232, 232, 242), rift=(214, 160, 232))
GLOW = dict(dark=(150, 70, 255), wind=(110, 255, 170), mercury=(170, 200, 240), fire=(255, 110, 20), lightning=(90, 170, 255),
            hellfire=(255, 80, 30), bone=(240, 220, 190), steel=(150, 190, 235), string=(255, 120, 190), spatial=(156, 120, 255),
            water=(110, 205, 255), mirror=(255, 176, 216), rift=(224, 64, 216))
LEAVES = dict(wind=4)

def heart_clover_mask(cx, cy, r, n, ang0=-math.pi / 2):
    """A clover of heart-shaped leaves (and a curved stem), as a soft-edged mask, in 2x pixels."""
    m = Image.new('L', (W, H), 0); d = ImageDraw.Draw(m)
    for i in range(n):
        a = ang0 + i * 2 * math.pi / n
        ux, uy = math.cos(a), math.sin(a); px, py = -uy, ux
        for s_ in (-1, 1):   # the two lobes of the heart
            lx, ly = cx + ux * r * 0.62 + px * r * 0.24 * s_, cy + uy * r * 0.62 + py * r * 0.24 * s_
            d.ellipse([lx - r * 0.31, ly - r * 0.31, lx + r * 0.31, ly + r * 0.31], fill=255)
        d.polygon([(cx + ux * r * 0.05, cy + uy * r * 0.05),
                   (cx + ux * r * 0.62 + px * r * 0.53, cy + uy * r * 0.62 + py * r * 0.53),
                   (cx + ux * r * 0.8, cy + uy * r * 0.8),
                   (cx + ux * r * 0.62 - px * r * 0.53, cy + uy * r * 0.62 - py * r * 0.53)], fill=255)
    pts = [(cx + math.sin(t / 10 * 1.2) * r * 0.25, cy + t / 10 * r * 1.25) for t in range(11)]
    d.line(pts, fill=255, width=max(2, int(r * 0.11)))
    return m.filter(ImageFilter.GaussianBlur(0.8))

def shift(m, dx, dy):
    return ImageChops.offset(m, dx, dy)

def decorate(c, n):
    acc = ACC[n]; glow = GLOW[n]
    # 1. the middle is left as clean leather: an empty medallion where a clover can go
    clear = Image.new('L', (W, H), 0); ImageDraw.Draw(clear).ellipse([CX - (R0 - 2) * S, CY - (R0 - 2) * S, CX + (R0 - 2) * S, CY + (R0 - 2) * S], fill=255)
    c.im = Image.composite(c.bg, c.im, clear.filter(ImageFilter.GaussianBlur(3 * S)))
    # 2. a tooled ring pressed into the leather round it
    ring = Image.new('L', (W, H), 0); rd = ImageDraw.Draw(ring)
    rd.ellipse([CX - R0 * S, CY - R0 * S, CX + R0 * S, CY + R0 * S], outline=255, width=2 * S)
    rd.ellipse([CX - (R0 - 6) * S, CY - (R0 - 6) * S, CX + (R0 - 6) * S, CY + (R0 - 6) * S], outline=255, width=1 * S)
    for i in range(28):
        a = i * math.pi / 14
        x, y = CX + math.cos(a) * (R0 - 3) * S, CY + math.sin(a) * (R0 - 3) * S
        rd.ellipse([x - 1.2 * S, y - 1.2 * S, x + 1.2 * S, y + 1.2 * S], fill=255)
    emboss(c, ring.filter(ImageFilter.GaussianBlur(0.6)), acc, glow, depth=1, gold=0.8, inset=0.0, glowAmt=0.25)
    # 2b. the clover in the medallion, pressed into the leather with a fine metal edge and veins
    leaves = LEAVES.get(n, 3); r = 40 * S
    m = heart_clover_mask(CX, CY - 7 * S, r, leaves)
    emboss(c, m, acc, glow, depth=2, gold=0.95, inset=0.55, veins=(CX, CY - 7 * S, r, leaves), glowAmt=0.4)
    # 3. bracketed corners and a title plate, pressed in the same way
    k = Image.new('L', (W, H), 0); kd = ImageDraw.Draw(k)
    for (x, y, sx, sy) in [(12, 12, 1, 1), (244, 12, -1, 1), (12, 372, 1, -1), (244, 372, -1, -1)]:
        kd.line([((x) * S, (y + 30 * sy) * S), (x * S, y * S), ((x + 30 * sx) * S, y * S)], fill=255, width=3 * S)
        kd.ellipse([(x + 8 * sx - 2.5) * S, (y + 8 * sy - 2.5) * S, (x + 8 * sx + 2.5) * S, (y + 8 * sy + 2.5) * S], fill=255)
    kd.rounded_rectangle([82 * S, 322 * S, 174 * S, 340 * S], radius=5 * S, outline=255, width=2 * S)
    kd.line([(96 * S, 331 * S), (160 * S, 331 * S)], fill=255, width=1 * S)
    emboss(c, k.filter(ImageFilter.GaussianBlur(0.6)), acc, glow, depth=1, gold=0.8, inset=0.0, glowAmt=0.0)
    # 4. worn leather: a few pale scuffs, edges darker
    rr = random.Random(len(n))
    l, d = c.layer()
    for _ in range(30):
        x, y = rr.randint(10, 246), rr.randint(10, 374); a = rr.uniform(0, 6.28); ln = rr.randint(6, 26)
        line(d, [(x, y), (x + math.cos(a) * ln, y + math.sin(a) * ln)], fill=(255, 255, 255, 26), width=1)
    c.put(l)
    v = Image.new('L', (W, H), 0); ImageDraw.Draw(v).rectangle([28 * S, 28 * S, W - 28 * S, H - 28 * S], fill=255)
    v = v.filter(ImageFilter.GaussianBlur(26 * S))
    c.im = Image.alpha_composite(c.im, Image.composite(Image.new('RGBA', (W, H), (0, 0, 0, 0)), Image.new('RGBA', (W, H), (0, 0, 0, 120)), v))

def emboss(c, m, acc, glow, depth=2, gold=0.9, inset=0.5, veins=None, glowAmt=0.45):
    """Presses a shape into the leather: darkened inside, a shadow on the upper-left edge, light on the lower-right,
    then a thin metal outline and a faint glow of the book's magic - so it reads as part of the cover."""
    dd = depth * S
    edge = ImageChops.subtract(m, m.filter(ImageFilter.MinFilter(3 if S < 2 else 5)))
    img = c.im
    if inset > 0:   # the leather inside the shape is pressed down and a shade darker, tinted with the magic
        dark = ImageChops.multiply(img, Image.new('RGBA', (W, H), (int(150 + glow[0] * 0.2), int(150 + glow[1] * 0.2), int(150 + glow[2] * 0.2), 255)))
        img = Image.composite(dark, img, m.point(lambda v: int(v * inset)))
    shadow = ImageChops.subtract(shift(m, dd, dd), m).filter(ImageFilter.GaussianBlur(1.2 * S))
    light = ImageChops.subtract(shift(m, -dd, -dd), m).filter(ImageFilter.GaussianBlur(1.0 * S))
    img = Image.composite(Image.new('RGBA', (W, H), (0, 0, 0, 255)), img, shadow.point(lambda v: int(v * 0.75)))
    img = Image.composite(Image.new('RGBA', (W, H), tuple(min(255, int(a * 0.9 + 40)) for a in acc) + (255,)), img, light.point(lambda v: int(v * 0.5)))
    if glowAmt > 0:   # the magic glowing faintly out of the pressed shape
        g = m.filter(ImageFilter.GaussianBlur(6 * S)).point(lambda v: int(v * glowAmt))
        img = Image.composite(Image.new('RGBA', (W, H), glow + (255,)), img, ImageChops.subtract(g, m.point(lambda v: v // 2)))
    metal = Image.new('RGBA', (W, H), acc + (255,))
    img = Image.composite(metal, img, edge.point(lambda v: int(v * gold)))
    if veins:   # fine veins down the middle of each leaf
        cx, cy, r, nl = veins
        vm = Image.new('L', (W, H), 0); vd = ImageDraw.Draw(vm)
        for i in range(nl):
            a = -math.pi / 2 + i * 2 * math.pi / nl
            vd.line([(cx + math.cos(a) * r * 0.12, cy + math.sin(a) * r * 0.12), (cx + math.cos(a) * r * 0.7, cy + math.sin(a) * r * 0.7)], fill=255, width=S)
        img = Image.composite(metal, img, vm.filter(ImageFilter.GaussianBlur(0.6)).point(lambda v: int(v * 0.5)))
    c.im = img

BOOKS = dict(dark=dark, wind=wind, mercury=mercury, fire=fire, lightning=lightning, hellfire=hellfire, bone=bone, steel=steel,
             string=string, spatial=spatial, water=water, mirror=mirror, rift=rift)
if __name__ == '__main__':
    for n, f in BOOKS.items():
        c = f(); decorate(c, n); c.save(f'{n}_grimoire_front')
        # the back: the same ground, quieter - just the frame colour and a small emblem would do, so reuse the cover softened
        b = Image.open(os.path.join(OUT, f'{n}_grimoire_front.png')).convert('RGBA')
        b = Image.blend(b, Image.new('RGBA', b.size, tuple(int(v) for v in b.resize((1, 1), Image.BOX).getpixel((0, 0)))), 0.55)
        b.save(os.path.join(OUT, f'{n}_grimoire_back.png'))
    print('covers ok')
