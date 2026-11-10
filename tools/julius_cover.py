"""Julius Novachrono's Time Grimoire: an anime-style cover (bold ink outlines, flat two-tone cel shading, cyan metal),
plus the leather, trim and inventory icon. An original design: a clock-face ring over crossed clock hands (no clover)."""
from PIL import Image, ImageDraw, ImageFilter
import colorsys, math, os
ROOT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/iron_spell_grimoire/textures/item')
M = os.path.join(ROOT, 'spell_book_models')
S = 2                     # drawn at 2x, then scaled down
W, H = 256 * S, 384 * S
INK = (12, 12, 22, 255)
NAVY, NAVY_SH, NAVY_HI = (14, 44, 88, 255), (7, 24, 54, 255), (26, 74, 128, 255)
GOLD, GOLD_SH, GOLD_HI, IVORY = (70, 205, 245, 255), (24, 120, 170, 255), (170, 240, 255, 255), (232, 250, 255, 255)   # cyan metal

def P(x, y): return (x * S, y * S)

def poly(d, pts, fill, outline=INK, w=3):
    q = [P(*p) for p in pts]
    if outline: d.line(q + [q[0]], fill=outline, width=w * S * 2, joint='curve')
    d.polygon(q, fill=fill)

def circle(d, cx, cy, r, fill=None, outline=None, w=0):
    d.ellipse([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], fill=fill, outline=outline, width=int(w * S))

def heart(cx, cy, size, ang):
    """A heart-shaped leaf: its tip at (cx, cy), lobes pointing along ang (radians, screen space)."""
    pts = []
    for i in range(48):
        t = i / 48 * math.tau
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        # heart: tip at (0,-17), lobes up; move tip to origin, lobes along +v
        u, v = x / 17.0, (y + 17) / 30.0
        rx, ry = math.cos(ang), math.sin(ang)          # forward (lobes)
        sx, sy = -ry, rx                               # side
        pts.append((cx + (rx * v + sx * u) * size, cy + (ry * v + sy * u) * size))
    return pts

def leaf_parts(cx, cy, size, ang, grow=0.0):
    """A heart leaf as two round lobes and a wedge to the tip at (cx, cy); grow widens it (for the ink outline)."""
    fx, fy, sx, sy = math.cos(ang), math.sin(ang), -math.sin(ang), math.cos(ang)
    r = size * 0.34 + grow
    lobes = [(cx + fx * size * 0.62 + sx * size * 0.27 * k, cy + fy * size * 0.62 + sy * size * 0.27 * k) for k in (-1, 1)]
    t = size * 0.08 + grow
    wedge = [(cx - fx * t, cy - fy * t), (lobes[0][0] - sx * r * 0.95 + fx * r * 0.2, lobes[0][1] - sy * r * 0.95 + fy * r * 0.2),
             (cx + fx * size * 0.62, cy + fy * size * 0.62), (lobes[1][0] + sx * r * 0.95 + fx * r * 0.2, lobes[1][1] + sy * r * 0.95 + fy * r * 0.2)]
    return lobes, r, wedge

def leaf_fill(d, cx, cy, size, ang, col, grow=0.0):
    lobes, r, wedge = leaf_parts(cx, cy, size, ang, grow)
    for (x, y) in lobes: circle(d, x, y, r, fill=col)
    d.polygon([P(*q) for q in wedge], fill=col)

def shade_leaf(d, cx, cy, size, ang):
    leaf_fill(d, cx, cy, size, ang, INK, 3.0)
    leaf_fill(d, cx, cy, size, ang, GOLD_SH)
    leaf_fill(d, cx - size * 0.045, cy - size * 0.055, size * 0.9, ang, GOLD)   # lit, a little up-left of its shadow
    lobes, r, _ = leaf_parts(cx, cy, size, ang)
    x, y = lobes[0]
    circle(d, x - r * 0.25, y - r * 0.3, r * 0.32, fill=GOLD_HI)                # a hard highlight on one lobe
    d.line([P(cx, cy), P(cx + math.cos(ang) * size * 0.6, cy + math.sin(ang) * size * 0.6)], fill=INK, width=2 * S)

def stroke(d, a, b, w, col):
    d.line([P(*a), P(*b)], fill=col, width=int(w * S))

def numeral(d, s, cx, cy, size):
    widths = {'I': 0.25, 'V': 0.62, 'X': 0.62}
    tot = sum(widths[c] for c in s) + 0.12 * (len(s) - 1)
    x = cx - tot * size / 2
    for c in s:
        w = widths[c] * size
        segs = {'I': [((x + w / 2, cy - size / 2), (x + w / 2, cy + size / 2))],
                'V': [((x, cy - size / 2), (x + w / 2, cy + size / 2)), ((x + w / 2, cy + size / 2), (x + w, cy - size / 2))],
                'X': [((x, cy - size / 2), (x + w, cy + size / 2)), ((x, cy + size / 2), (x + w, cy - size / 2))]}[c]
        for a, b in segs: stroke(d, a, b, size * 0.3, INK)
        for a, b in segs: stroke(d, a, b, size * 0.14, GOLD_HI)
        x += w + 0.12 * size

def clock_hand(d, cx, cy, ang, length, width, col):
    fx, fy = math.sin(ang), -math.cos(ang)
    sx, sy = -fy, fx
    tip = (cx + fx * length, cy + fy * length)
    base = (cx - fx * length * 0.2, cy - fy * length * 0.2)
    lz = [(cx + fx * length * 0.62 + sx * width * 1.8, cy + fy * length * 0.62 + sy * width * 1.8), tip,
          (cx + fx * length * 0.62 - sx * width * 1.8, cy + fy * length * 0.62 - sy * width * 1.8), (cx + fx * length * 0.45, cy + fy * length * 0.45)]
    shaft = [(base[0] + sx * width, base[1] + sy * width), (cx + fx * length * 0.6 + sx * width, cy + fy * length * 0.6 + sy * width),
             (cx + fx * length * 0.6 - sx * width, cy + fy * length * 0.6 - sy * width), (base[0] - sx * width, base[1] - sy * width)]
    poly(d, shaft, col, INK, 2)
    poly(d, lz, col, INK, 2)
    circle(d, cx - fx * length * 0.12, cy - fy * length * 0.12, width * 2.2, fill=col, outline=INK, w=2.5)
    circle(d, cx - fx * length * 0.12, cy - fy * length * 0.12, width * 1.0, fill=NAVY_SH)

def frame(d):
    # cover body: flat navy with a big cel shadow down the right and along the bottom, a lighter band at upper left
    d.rectangle([0, 0, W, H], fill=NAVY)
    poly(d, [(256, 0), (256, 384), (0, 384), (0, 330), (190, 330), (190, 0)], NAVY_SH, None)
    poly(d, [(14, 14), (120, 14), (14, 120)], NAVY_HI, None)
    # the thick gold border: ink, gold, a highlight edge and a shadow edge, ink
    for (x0, y0, x1, y1, col) in [(4, 4, 252, 380, INK), (7, 7, 249, 377, GOLD), (7, 7, 249, 9, GOLD_HI), (7, 7, 9, 377, GOLD_HI),
                                  (7, 374, 249, 377, GOLD_SH), (246, 7, 249, 377, GOLD_SH), (17, 17, 239, 367, INK), (19, 19, 237, 365, NAVY)]:
        d.rectangle([x0 * S, y0 * S, x1 * S, y1 * S], fill=col)
    poly(d, [(237, 19), (237, 365), (19, 365), (19, 330), (190, 330), (190, 19)], NAVY_SH, None)
    d.rectangle([17 * S, 17 * S, 239 * S, 367 * S], outline=INK, width=2 * S)
    # metal corner caps: an angled plate with a rivet and a hard white glint
    for (sx, sy, x, y) in [(1, 1, 4, 4), (-1, 1, 252, 4), (1, -1, 4, 380), (-1, -1, 252, 380)]:
        pts = [(x, y), (x + sx * 46, y), (x + sx * 46, y + sy * 12), (x + sx * 12, y + sy * 46), (x, y + sy * 46)]
        poly(d, pts, GOLD, INK, 3)
        poly(d, [(x + sx * 3, y + sy * 3), (x + sx * 40, y + sy * 3), (x + sx * 40, y + sy * 8), (x + sx * 8, y + sy * 40), (x + sx * 3, y + sy * 40)], GOLD_SH if sy < 0 else GOLD_HI, None)
        circle(d, x + sx * 16, y + sy * 16, 5, fill=GOLD, outline=INK, w=2.5)
        circle(d, x + sx * 15, y + sy * 15, 1.6, fill=IVORY)
    # spine-side bands
    for y in (60, 324):
        d.rectangle([19 * S, (y - 3) * S, 237 * S, (y + 3) * S], fill=INK)
        d.rectangle([19 * S, (y - 1.5) * S, 237 * S, (y + 1.5) * S], fill=GOLD)
        for x in range(30, 230, 20): circle(d, x, y, 2.2, fill=GOLD_HI, outline=INK, w=1.2)

def dial(d, cx, cy, R, numerals=True):
    circle(d, cx, cy, R + 7, fill=INK)
    circle(d, cx, cy, R + 4, fill=GOLD)
    d.pieslice([(cx - R - 4) * S, (cy - R - 4) * S, (cx + R + 4) * S, (cy + R + 4) * S], 20, 200, fill=GOLD_SH)   # cel shadow on the lower half
    circle(d, cx, cy, R - 4, fill=INK)
    circle(d, cx, cy, R - 7, fill=NAVY_SH)
    d.pieslice([(cx - R + 7) * S, (cy - R + 7) * S, (cx + R - 7) * S, (cy + R - 7) * S], 200, 380, fill=NAVY)
    circle(d, cx, cy, R * 0.86, outline=GOLD, w=1.6)
    for i in range(60):
        a = i / 60 * math.tau
        if i % 5: 
            stroke(d, (cx + math.sin(a) * R * 0.86, cy - math.cos(a) * R * 0.86), (cx + math.sin(a) * R * 0.8, cy - math.cos(a) * R * 0.8), 1.2, GOLD)
    for i in range(12):
        a = i / 12 * math.tau
        w = 6 if i % 3 == 0 else 4
        p0 = (cx + math.sin(a) * R * 0.92, cy - math.cos(a) * R * 0.92)
        p1 = (cx + math.sin(a) * R * 0.76, cy - math.cos(a) * R * 0.76)
        stroke(d, p0, p1, w + 3, INK)
        stroke(d, p0, p1, w, GOLD_HI)
    if numerals:
        for i, s in ((0, 'XII'), (3, 'III'), (6, 'VI'), (9, 'IX')):
            a = i / 12 * math.tau
            numeral(d, s, cx + math.sin(a) * R * 0.6, cy - math.cos(a) * R * 0.6, R * 0.13)

def front():
    im = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(im)
    frame(d)
    cx, cy, R = 128, 192, 86
    # a faint glow of gold behind the dial (flat rings, cel style)
    for k, a in ((R + 22, 40), (R + 15, 70)):
        g = Image.new('RGBA', (W, H)); gd = ImageDraw.Draw(g)
        circle(gd, cx, cy, k, fill=(90, 215, 255, a))
        im = Image.alpha_composite(im, g); d = ImageDraw.Draw(im)
    dial(d, cx, cy, R)
    clock_hand(d, cx, cy, math.radians(-60), R * 0.52, 3.2, GOLD)        # ten o'clock
    clock_hand(d, cx, cy, math.radians(60), R * 0.78, 2.4, GOLD_HI)      # ten past
    circle(d, cx, cy, 6, fill=GOLD_HI, outline=INK, w=2.5)   # just the hands and the hub - no clover
    # sharp anime glints on the metal
    for (x, y, s) in ((cx - 50, cy - 70, 7), (200, 40, 5), (60, 330, 4)):
        poly(d, [(x, y - s), (x + s * 0.25, y - s * 0.25), (x + s, y), (x + s * 0.25, y + s * 0.25), (x, y + s), (x - s * 0.25, y + s * 0.25), (x - s, y), (x - s * 0.25, y - s * 0.25)], IVORY, None)
    return im.resize((256, 384), Image.LANCZOS)

def back():
    im = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(im)
    frame(d)
    cx, cy, R = 128, 192, 52
    dial(d, cx, cy, R, numerals=False)
    clock_hand(d, cx, cy, math.radians(-120), R * 0.5, 2.6, GOLD)
    clock_hand(d, cx, cy, math.radians(150), R * 0.76, 2.0, GOLD_HI)
    circle(d, cx, cy, 4.5, fill=GOLD_HI, outline=INK, w=2)
    return im.resize((256, 384), Image.LANCZOS)

def shift(im, hue, sm, vm):
    im = im.convert('RGBA'); px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0: continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            s2 = min(1, s + 0.05) if s < 0.08 else min(1, s * sm)
            nr, ng, nb = colorsys.hsv_to_rgb(hue, s2, min(1, v * vm))
            px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    return im

front().save(os.path.join(M, 'time_grimoire_front.png'))
back().save(os.path.join(M, 'time_grimoire_back.png'))
shift(Image.open(os.path.join(M, 'dark_grimoire_leather.png')), 0.54, 1.25, 1.05).save(os.path.join(M, 'time_grimoire_leather.png'))
icon = shift(Image.open(os.path.join(ROOT, 'dark_grimoire.png')), 0.54, 1.3, 1.1)
px = icon.load()   # a gold clock-dot in the middle of the inventory icon
for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
    if px[x, y][3]: px[x, y] = (90, 225, 255, 255)
icon.save(os.path.join(ROOT, 'time_grimoire.png'))
t = Image.open(os.path.join(M, 'spatial_grimoire_trim.png')).convert('RGBA'); px = t.load()
for y in range(t.height):
    for x in range(t.width):
        r, g, b, a = px[x, y]
        k = 0.45 + 0.6 * min(1.2, (r + g + b) / 450.0)
        px[x, y] = (min(255, int(110 * k)), min(255, int(225 * k)), min(255, int(255 * k)), a)
t.save(os.path.join(M, 'time_grimoire_trim.png'))
print('julius cover ok')
