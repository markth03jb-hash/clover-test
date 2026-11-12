"""Cotton pixel sprites: a lumpy, fuzzy boll, a small tuft and a curly fibre (4-frame dissolve strips, grey-scale, tinted in game)."""
import math, random
from PIL import Image
OUT = '/home/claude/clover-test/src/main/resources/assets/iron_spell_grimoire/textures/effect/pixel/'
BAYER = [[0,8,2,10],[12,4,14,6],[3,11,1,9],[15,7,13,5]]
KEEP = [16, 11, 6, 2]   # frames: whole, then thinning out

def strip(w, h, draw):
    im = Image.new('RGBA', (w, h * 4), (0, 0, 0, 0))
    base = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    draw(base)
    for f in range(4):
        fr = base.copy()
        px = fr.load()
        for y in range(h):
            for x in range(w):
                if px[x, y][3] and BAYER[y % 4][x % 4] >= KEEP[f]:
                    px[x, y] = (0, 0, 0, 0)
        im.paste(fr, (0, f * h))
    return im

def boll(img):
    rnd = random.Random(7)
    w = h = 26
    cx = cy = 12.5
    lobes = [(cx, cy, 6.6)]
    for i in range(7):
        a = math.tau * i / 7 + 0.3
        lobes.append((cx + math.cos(a) * 5.6, cy + math.sin(a) * 5.4, 4.6 + rnd.random() * 1.0))
    px = img.load()
    for y in range(h):
        for x in range(w):
            ds = [math.hypot(x - lx, y - ly) - lr for lx, ly, lr in lobes]
            d = min(ds)
            if d <= 0:
                n2 = sorted(ds)
                crevice = n2[1] - n2[0] < 1.3 and n2[1] < 1.2   # the seam between two lobes
                light = 1.0 - 0.32 * ((x - cx) + (y - cy)) / 18.0
                best = ds.index(d)
                lx, ly, lr = lobes[best]
                lobe = 1.0 - 0.18 * ((x - lx) + (y - ly)) / (lr * 1.6)   # each lobe is lit from the top left on its own
                v = 225 * light * lobe + rnd.uniform(-7, 7)
                if crevice: v -= 34
                v = max(120, min(255, v))
                px[x, y] = (int(v), int(v), int(min(255, v + 5)), 255)
            elif d < 2.2 and rnd.random() < 0.42 - 0.12 * d:   # loose fibres fuzzing the edge
                v = 230 + rnd.uniform(-12, 10)
                px[x, y] = (int(v), int(v), 255, 170)

def tuft(img):
    rnd = random.Random(11)
    w = h = 26
    px = img.load()
    cx = cy = 12.5
    lobes = [(cx - 2.5, cy + 1, 3.6), (cx + 2.2, cy - 0.5, 3.3), (cx, cy + 2.8, 2.8), (cx + 0.5, cy - 3, 2.6)]
    for y in range(h):
        for x in range(w):
            d = min(math.hypot(x - lx, y - ly) - lr for lx, ly, lr in lobes)
            if d <= 0:
                v = 232 - 18 * ((x - cx) + (y - cy)) / 10 + rnd.uniform(-6, 6)
                px[x, y] = (int(max(150, min(255, v))),) * 2 + (255, 255)
    for k in range(16):   # fine fibres streaming out of it
        a = rnd.uniform(0, math.tau)
        r0 = rnd.uniform(3, 5)
        ln = rnd.uniform(4, 8)
        curl = rnd.uniform(-0.25, 0.25)
        for s in range(int(ln * 2)):
            t = s / 2.0
            aa = a + curl * t * 0.3
            x = int(round(cx + math.cos(aa) * (r0 + t))); y = int(round(cy + math.sin(aa) * (r0 + t)))
            if 0 <= x < w and 0 <= y < h and px[x, y][3] == 0:
                px[x, y] = (238, 238, 255, 200 - int(t * 14))

def fiber(img):
    w, h = img.size
    px = img.load()
    for y in range(2, h - 2):
        t = y / h
        x = w / 2 + math.sin(t * math.tau * 1.5) * 2.6 * (0.4 + t)
        for dx in (0, 1):
            xi = int(x) + dx
            if 0 <= xi < w:
                v = 245 - int(30 * abs(math.sin(t * 9)))
                px[xi, y] = (v, v, 255, 255 if y > 6 else 150)

def clear(w, h):
    return Image.new('RGBA', (w, h * 4), (0, 0, 0, 0))

for name, w, h, fn in (('cotton', 26, 26, boll), ('tuft', 26, 26, tuft), ('fiber', 12, 32, fiber)):
    strip(w, h, fn).save(OUT + name + '.png')
    clear(w, h).save(OUT + name + '_core.png')
    clear(w, h).save(OUT + name + '_rim.png')
print('ok')
