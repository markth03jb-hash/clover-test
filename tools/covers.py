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
def dark():    # Yami: violet-black, great crescent, silver filigree, rippling darkness
    c = Cv((8, 5, 14), (22, 10, 34), 2)
    l, d = c.layer()
    for k in range(5): d.ellipse([CX - (60 + k * 22) * S, CY - (60 + k * 22) * S, CX + (60 + k * 22) * S, CY + (60 + k * 22) * S], outline=(120, 60, 200, 90 - k * 14), width=2 * S)
    c.put(l)
    l, d = c.layer(); d.ellipse([CX - 62 * S, CY - 62 * S, CX + 62 * S, CY + 62 * S], fill=(180, 150, 235, 255)); d.ellipse([CX - 40 * S, CY - 72 * S, CX + 84 * S, CY + 52 * S], fill=(0, 0, 0, 0))
    mask = l.copy(); c.put(l, (150, 70, 255), 14, 1.0)
    l, d = c.layer(); frame(d, (180, 169, 200, 255), 18, 2, 6)
    for x in (60, 196): d.arc([(x - 18) * S, 330 * S, (x + 18) * S, 366 * S], 200, 340, fill=(180, 169, 200, 255), width=2 * S)
    c.put(l); return c
def wind():    # Yuno: jade green, four-leaf clover riding wind spirals, feather corners
    c = Cv((8, 34, 24), (14, 60, 42), 3)
    l, d = c.layer()
    for k in range(4):
        a0 = k * 90 + 20; d.arc([CX - (86 + k * 8) * S, CY - (86 + k * 8) * S, CX + (86 + k * 8) * S, CY + (86 + k * 8) * S], a0, a0 + 150, fill=(190, 255, 215, 200), width=2 * S)
    for k in range(3): d.arc([CX - (50 - k * 10) * S, CY - (50 - k * 10) * S, CX + (50 - k * 10) * S, CY + (50 - k * 10) * S], k * 120, k * 120 + 220, fill=(220, 255, 235, 230), width=S)
    c.put(l, (110, 255, 170), 6, 0.6)
    l, d = c.layer(); clover(d, CX / S, CY / S, 34, 4, (22, 130, 78, 255)); c.put(l, (140, 255, 190), 6, 0.8)
    l, d = c.layer(); frame(d, (231, 217, 160, 255), 16, 2, 0, True)
    for (x, y, s) in [(34, 34, 1), (222, 34, -1), (34, 350, 1), (222, 350, -1)]:
        poly(d, [(x, y), (x + 16 * s, y + 4), (x + 4 * s, y + 20)], fill=(231, 217, 160, 255))
    c.put(l); return c
def mercury(): # Nozel: polished chrome-blue, concentric ripples and hanging drops, wave frame
    c = Cv((26, 32, 44), (60, 72, 92), 4, 6)
    l, d = c.layer()
    for k in range(7): d.ellipse([CX - (14 + k * 15) * S, CY - (14 + k * 15) * S, CX + (14 + k * 15) * S, CY + (14 + k * 15) * S], outline=(220, 232, 248, 190 - k * 20), width=S)
    c.put(l, (170, 200, 240), 6, 0.5)
    l, d = c.layer(); d.ellipse([CX - 20 * S, CY - 22 * S, CX + 20 * S, CY + 18 * S], fill=(215, 226, 244, 255)); poly(d, [(128, CY / S - 44), (116, CY / S - 14), (140, CY / S - 14)], fill=(215, 226, 244, 255))
    d.ellipse([CX - 9 * S, CY - 14 * S, CX - 2 * S, CY - 6 * S], fill=(255, 255, 255, 255)); c.put(l, (190, 215, 255), 8, 0.9)
    l, d = c.layer()
    for edge in (22, 362):
        pts = [(x, edge + math.sin(x / 9.0) * 4) for x in range(20, 237, 3)]; line(d, pts, fill=(220, 230, 245, 255), width=2)
    line(d, [(20, 22), (20, 362)], fill=(220, 230, 245, 255), width=2); line(d, [(236, 22), (236, 362)], fill=(220, 230, 245, 255), width=2)
    for x in (60, 128, 196): d.ellipse([(x - 5) * S, 330 * S, (x + 5) * S, 342 * S], fill=(200, 215, 240, 255))
    c.put(l); return c
def fire():    # Magna: scorched brown, brass frame, flames licking up from the foot, a burning fist-ball
    c = Cv((26, 12, 6), (60, 22, 8), 5)
    l, d = c.layer(); r = random.Random(9)
    for i in range(22):
        x = 24 + i * 10; h = r.randint(50, 130)
        poly(d, [(x - 8, 366), (x + 8, 366), (x + r.randint(-5, 5), 366 - h)], fill=(255, 120 + r.randint(0, 60), 20, 200))
    c.put(l, (255, 100, 20), 10, 0.6)
    l, d = c.layer(); d.ellipse([CX - 44 * S, CY - 44 * S, CX + 44 * S, CY + 44 * S], fill=(255, 150, 40, 255)); d.ellipse([CX - 28 * S, CY - 28 * S, CX + 28 * S, CY + 28 * S], fill=(255, 220, 120, 255)); d.ellipse([CX - 12 * S, CY - 12 * S, CX + 12 * S, CY + 12 * S], fill=(255, 250, 220, 255))
    c.put(l, (255, 110, 20), 12, 1.0)
    l, d = c.layer(); frame(d, (217, 169, 76, 255), 16, 3, 5, False); c.put(l); return c
def lightning(): # Luck: midnight blue, big forking bolt, four-leaf shape in the sparks
    c = Cv((4, 10, 26), (10, 28, 62), 6)
    l, d = c.layer(); r = random.Random(3)
    line(d, [(150, 40), (112, 140), (140, 150), (96, 250), (162, 150), (132, 140), (170, 40)], fill=(150, 205, 255, 255), width=5)
    for a in [0.6, 2.4, 3.6, 5.4]: crack(d, 128, 170, a, 90, (190, 225, 255, 255), 2, r)
    c.put(l, (90, 170, 255), 8, 1.0)
    l, d = c.layer(); line(d, [(150, 40), (112, 140), (140, 150), (96, 250), (162, 150), (132, 140), (170, 40)], fill=(255, 255, 255, 255), width=2); frame(d, (237, 231, 200, 255), 16, 2, 0, True)
    for (x, y) in [(40, 40), (216, 40), (40, 344), (216, 344)]: line(d, [(x - 8, y - 6), (x, y), (x - 3, y + 2), (x + 8, y + 8)], fill=(150, 205, 255, 255), width=2)
    c.put(l); return c
def hellfire(): # Mereoleona: crimson, a lion's mane of flame claws round a sun
    c = Cv((34, 6, 8), (74, 12, 10), 7)
    l, d = c.layer()
    for i in range(16):
        a = i * math.pi / 8; r0, r1 = 52, 100 + (i % 2) * 22
        poly(d, [(CX / S + math.cos(a - 0.12) * r0, CY / S + math.sin(a - 0.12) * r0), (CX / S + math.cos(a + 0.12) * r0, CY / S + math.sin(a + 0.12) * r0), (CX / S + math.cos(a + 0.28) * r1, CY / S + math.sin(a + 0.28) * r1)], fill=(255, 70, 30, 235))
    c.put(l, (255, 60, 20), 8, 0.9)
    l, d = c.layer(); d.ellipse([CX - 48 * S, CY - 48 * S, CX + 48 * S, CY + 48 * S], fill=(120, 14, 10, 255), outline=(232, 182, 82, 255), width=3 * S); d.ellipse([CX - 24 * S, CY - 24 * S, CX + 24 * S, CY + 24 * S], fill=(255, 150, 60, 255))
    frame(d, (232, 182, 82, 255), 16, 3, 0, False); c.put(l, (255, 80, 30), 8, 0.6); return c
def bone():    # Zenon: dark red-black, a ribcage of bone arches, knuckle bones at the corners
    c = Cv((22, 10, 10), (40, 18, 18), 8)
    l, d = c.layer(); bc = (237, 227, 204, 255)
    line(d, [(128, 70), (128, 300)], fill=bc, width=7)
    for k in range(6):
        y = 100 + k * 32; w = 78 - abs(k - 2) * 8
        d.arc([(128 - w) * S, (y - 22) * S, (128 + w) * S, (y + 22) * S], 0, 180, fill=bc, width=5 * S)
        d.ellipse([(128 - w - 5) * S, (y - 2) * S, (128 - w + 5) * S, (y + 8) * S], fill=bc); d.ellipse([(128 + w - 5) * S, (y - 2) * S, (128 + w + 5) * S, (y + 8) * S], fill=bc)
    c.put(l, (240, 220, 190), 6, 0.35)
    l, d = c.layer(); frame(d, bc, 16, 3, 0, False)
    for (x, y) in [(28, 28), (228, 28), (28, 356), (228, 356)]:
        d.ellipse([(x - 8) * S, (y - 5) * S, (x + 1) * S, (y + 4) * S], fill=bc); d.ellipse([(x - 1) * S, (y - 5) * S, (x + 8) * S, (y + 4) * S], fill=bc)
    c.put(l); return c
def steel():   # Klaus: grey plate, bolted panels, a gear with a lens in it
    c = Cv((48, 54, 62), (72, 80, 92), 9, 7)
    l, d = c.layer()
    for (x0, y0, x1, y1) in [(26, 26, 230, 120), (26, 128, 230, 256), (26, 264, 230, 358)]:
        d.rectangle([x0 * S, y0 * S, x1 * S, y1 * S], outline=(30, 34, 40, 255), width=3 * S)
        for (x, y) in [(x0 + 9, y0 + 9), (x1 - 9, y0 + 9), (x0 + 9, y1 - 9), (x1 - 9, y1 - 9)]: d.ellipse([(x - 4) * S, (y - 4) * S, (x + 4) * S, (y + 4) * S], fill=(210, 216, 226, 255))
    c.put(l)
    l, d = c.layer()
    for i in range(12):
        a = i * math.pi / 6; poly(d, [(CX / S + math.cos(a - .12) * 60, CY / S + math.sin(a - .12) * 60), (CX / S + math.cos(a + .12) * 60, CY / S + math.sin(a + .12) * 60), (CX / S + math.cos(a + .1) * 76, CY / S + math.sin(a + .1) * 76), (CX / S + math.cos(a - .1) * 76, CY / S + math.sin(a - .1) * 76)], fill=(217, 169, 76, 255))
    d.ellipse([CX - 62 * S, CY - 62 * S, CX + 62 * S, CY + 62 * S], fill=(217, 169, 76, 255)); d.ellipse([CX - 46 * S, CY - 46 * S, CX + 46 * S, CY + 46 * S], fill=(38, 42, 50, 255)); d.ellipse([CX - 22 * S, CY - 22 * S, CX + 22 * S, CY + 22 * S], fill=(150, 190, 235, 255)); d.ellipse([CX - 8 * S, CY - 12 * S, CX, CY - 4 * S], fill=(255, 255, 255, 255))
    c.put(l); return c
def string():  # Vanessa: rose cloth, stitched seams, needle and looping thread
    c = Cv((74, 22, 52), (110, 36, 78), 10, 14)
    l, d = c.layer()
    for k in range(40):
        x = 24 + k * 5.6; d.line([x * S, 22 * S, (x + 3) * S, 22 * S], fill=(242, 221, 232, 255), width=2 * S); d.line([x * S, 362 * S, (x + 3) * S, 362 * S], fill=(242, 221, 232, 255), width=2 * S)
    for k in range(60):
        y = 28 + k * 5.5; d.line([22 * S, y * S, 22 * S, (y + 3) * S], fill=(242, 221, 232, 255), width=2 * S); d.line([234 * S, y * S, 234 * S, (y + 3) * S], fill=(242, 221, 232, 255), width=2 * S)
    c.put(l)
    l, d = c.layer(); pts = []
    for t in range(0, 300):
        a = t / 300 * 6.4 * math.pi; rr = 20 + t / 300 * 70; pts.append((128 + math.cos(a) * rr * 0.95, 170 + math.sin(a) * rr * 0.75))
    line(d, pts, fill=(255, 140, 198, 255), width=2); c.put(l, (255, 120, 190), 5, 0.7)
    l, d = c.layer(); line(d, [(60, 330), (196, 90)], fill=(245, 235, 240, 255), width=3); d.ellipse([194 * S, 82 * S, 202 * S, 96 * S], outline=(245, 235, 240, 255), width=S); c.put(l); return c
def spatial(): # Finral: deep indigo, a golden gate ring with a violet void, tiny gates drifting
    c = Cv((6, 4, 26), (22, 16, 64), 11)
    l, d = c.layer()
    d.ellipse([CX - 62 * S, CY - 80 * S, CX + 62 * S, CY + 80 * S], fill=(30, 12, 90, 255), outline=(231, 199, 102, 255), width=5 * S)
    d.ellipse([CX - 46 * S, CY - 62 * S, CX + 46 * S, CY + 62 * S], fill=(8, 4, 34, 255), outline=(156, 120, 255, 255), width=2 * S)
    c.put(l, (156, 120, 255), 10, 0.9)
    l, d = c.layer()
    for (x, y, s) in [(52, 70, 10), (204, 110, 8), (60, 290, 9), (200, 320, 12)]: d.ellipse([(x - s * .6) * S, (y - s) * S, (x + s * .6) * S, (y + s) * S], outline=(231, 199, 102, 255), width=2 * S)
    frame(d, (231, 199, 102, 255), 16, 2, 4, True); c.put(l); return c
def water():   # Noelle: sea blue, a curling wave with foam and bubbles, a sea-dragon's coil
    c = Cv((6, 26, 52), (10, 70, 120), 12)
    l, d = c.layer()
    for k in range(6):
        y0 = 120 + k * 36; pts = [(x, y0 + math.sin(x / 14.0 + k) * 8) for x in range(24, 233, 3)]; line(d, pts, fill=(130, 210, 255, 220 - k * 20), width=3)
    c.put(l, (90, 190, 255), 6, 0.5)
    l, d = c.layer(); d.pieslice([CX - 62 * S, CY - 62 * S, CX + 62 * S, CY + 62 * S], 200, 360, fill=(92, 200, 255, 255)); d.pieslice([CX - 44 * S, CY - 44 * S, CX + 62 * S, CY + 62 * S], 200, 360, fill=(14, 60, 110, 255))
    for (x, y, s) in [(80, 110, 6), (176, 90, 8), (150, 130, 4), (96, 250, 7), (180, 270, 5)]: d.ellipse([(x - s) * S, (y - s) * S, (x + s) * S, (y + s) * S], outline=(228, 242, 255, 255), width=2 * S)
    c.put(l, (110, 205, 255), 8, 0.7)
    l, d = c.layer(); frame(d, (228, 242, 255, 255), 16, 2, 0, False)
    pts = [(x, 366 + math.sin(x / 8.0) * 4) for x in range(24, 233, 2)]; line(d, pts, fill=(228, 242, 255, 255), width=3); c.put(l); return c
def mirror():  # Gauche: silver glass, a lattice of angled mirror panes with a rose glint
    c = Cv((52, 52, 70), (96, 96, 122), 13, 5)
    l, d = c.layer(); r = random.Random(11)
    cells = [(30, 30, 126, 150), (130, 30, 226, 110), (30, 154, 110, 250), (114, 114, 226, 250), (30, 254, 140, 354), (144, 254, 226, 354)]
    for (x0, y0, x1, y1) in cells:
        poly(d, [(x0, y0 + r.randint(0, 10)), (x1 - r.randint(0, 10), y0), (x1, y1 - r.randint(0, 10)), (x0 + r.randint(0, 10), y1)], fill=(120 + r.randint(0, 40), 120 + r.randint(0, 40), 150 + r.randint(0, 40), 255), outline=(232, 232, 242, 255))
        line(d, [(x0 + 12, y1 - 14), (x1 - 14, y0 + 14)], fill=(255, 176, 216, 200), width=2)
    c.put(l, (255, 176, 216), 5, 0.35)
    l, d = c.layer(); frame(d, (232, 232, 242, 255), 14, 3, 0, False); c.put(l); return c
def rift():    # Langris: plum black, space cut open by straight slashes, violet void showing through
    c = Cv((10, 4, 16), (30, 10, 40), 14)
    l, d = c.layer(); r = random.Random(21)
    for (x0, y0, x1, y1) in [(20, 330, 236, 60), (30, 250, 200, 36), (60, 360, 240, 150)]:
        line(d, [(x0, y0), (x1, y1)], fill=(255, 220, 255, 255), width=3)
        for k in range(5):
            t = r.uniform(0.1, 0.9); px_, py_ = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t; crack(d, px_, py_, r.uniform(0, 6.28), 36, (224, 64, 216, 255), 2, r, 1)
    c.put(l, (224, 64, 216), 10, 1.0)
    l, d = c.layer(); poly(d, [(20, 330), (236, 60), (236, 80), (20, 352)], fill=(70, 10, 90, 200)); c.put(l)
    l, d = c.layer(); frame(d, (214, 160, 232, 255), 16, 2, 0, False); c.put(l); return c


# ---------------------------------------------------------------- the shared grimoire look: a clover crest, corner brackets, worn leather
ACC = dict(dark=(180, 169, 200), wind=(231, 217, 160), mercury=(220, 232, 248), fire=(217, 169, 76), lightning=(237, 231, 200),
           hellfire=(232, 182, 82), bone=(237, 227, 204), steel=(217, 169, 76), string=(242, 221, 232), spatial=(231, 199, 102),
           water=(228, 242, 255), mirror=(232, 232, 242), rift=(214, 160, 232))
GLOW = dict(dark=(150, 70, 255), wind=(110, 255, 170), mercury=(170, 200, 240), fire=(255, 110, 20), lightning=(90, 170, 255),
            hellfire=(255, 80, 30), bone=(240, 220, 190), steel=(150, 190, 235), string=(255, 120, 190), spatial=(156, 120, 255),
            water=(110, 205, 255), mirror=(255, 176, 216), rift=(224, 64, 216))
LEAVES = dict(wind=4, lightning=4)

def decorate(c, n):
    col = ACC[n] + (255,)
    # a plate behind the crest, so it reads on top of any motif
    l, d = c.layer()
    d.ellipse([CX - 50 * S, CY - 50 * S, CX + 50 * S, CY + 50 * S], fill=(0, 0, 0, 110))
    c.put(l)
    l, d = c.layer()
    clover(d, CX / S, CY / S - 2, 30, LEAVES.get(n, 3), ACC[n] + (255,))
    clover(d, CX / S, CY / S - 2, 24, LEAVES.get(n, 3), tuple(int(v * 0.18) for v in ACC[n]) + (255,))
    c.put(l, GLOW[n], 7, 0.8)
    # bracketed corners, like the metal corners of an old book
    l, d = c.layer()
    for (x, y, sx, sy) in [(12, 12, 1, 1), (244, 12, -1, 1), (12, 372, 1, -1), (244, 372, -1, -1)]:
        line(d, [(x, y + 34 * sy), (x, y), (x + 34 * sx, y)], fill=col, width=4)
        line(d, [(x + 6 * sx, y + 22 * sy), (x + 6 * sx, y + 6 * sy), (x + 22 * sx, y + 6 * sy)], fill=col, width=2)
        d.ellipse([(x + 9 * sx - 3) * S, (y + 9 * sy - 3) * S, (x + 9 * sx + 3) * S, (y + 9 * sy + 3) * S], fill=col)
    # a title plate at the foot
    d.rounded_rectangle([78 * S, 322 * S, 178 * S, 342 * S], radius=6 * S, outline=col, width=2 * S, fill=(0, 0, 0, 90))
    line(d, [(92, 332), (164, 332)], fill=col, width=2)
    c.put(l)
    # worn leather: darker edges, a few pale scratches
    r = random.Random(len(n))
    l, d = c.layer()
    for _ in range(26):
        x, y = r.randint(10, 246), r.randint(10, 374); a = r.uniform(0, 6.28); ln = r.randint(8, 30)
        line(d, [(x, y), (x + math.cos(a) * ln, y + math.sin(a) * ln)], fill=(255, 255, 255, 38), width=1)
    c.put(l)
    v = Image.new('L', (W, H), 0); vd = ImageDraw.Draw(v)
    vd.rectangle([0, 0, W, H], fill=0); vd.rectangle([30 * S, 30 * S, W - 30 * S, H - 30 * S], fill=255)
    v = v.filter(ImageFilter.GaussianBlur(26 * S))
    shade = Image.composite(Image.new('RGBA', (W, H), (0, 0, 0, 0)), Image.new('RGBA', (W, H), (0, 0, 0, 110)), v)
    c.im = Image.alpha_composite(c.im, shade)

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
