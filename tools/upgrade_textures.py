"""Grimoire upgrade item textures, 32x32 pixel art: each drawn from shapes with gradients, then given a dark outline,
a light edge on the top-left and a shade on the bottom-right, and (for the magical ones) a soft glow. Also redraws the
Devil Shard, Mana Core and Rift Ink in the same style, writes the item models and a preview sheet."""
import json, os, math, random
from PIL import Image, ImageDraw, ImageFilter
R = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/iron_spell_grimoire')
TEX = os.path.join(R, 'textures/item'); MOD = os.path.join(R, 'models/item')
N = 32

def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))

def finish(im, glow=None, outline=(16, 10, 14, 255)):
    """Outline, rim light, shade, and an optional glow behind."""
    px = im.load()
    a = [[px[x, y][3] > 0 for x in range(N)] for y in range(N)]
    out = im.copy(); po = out.load()
    for y in range(N):
        for x in range(N):
            if a[y][x]:
                r, g, b, al = px[x, y]
                up = y > 0 and not a[y - 1][x]; lf = x > 0 and not a[y][x - 1]
                dn = y < N - 1 and not a[y + 1][x]; rt = x < N - 1 and not a[y][x + 1]
                if up or lf: po[x, y] = (min(255, r + 45), min(255, g + 45), min(255, b + 45), al)
                elif dn or rt: po[x, y] = (int(r * 0.62), int(g * 0.62), int(b * 0.62), al)
                continue
            if any(0 <= x + dx < N and 0 <= y + dy < N and a[y + dy][x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                po[x, y] = outline
    if glow:
        g = Image.new('RGBA', (N, N), (0, 0, 0, 0)); pg = g.load()
        for y in range(N):
            for x in range(N):
                if po[x, y][3] > 0: pg[x, y] = glow
        g = g.filter(ImageFilter.GaussianBlur(1.6))
        gp = g.load()
        for y in range(N):
            for x in range(N):
                r, gg, b, al = gp[x, y]
                gp[x, y] = (r, gg, b, min(110, int(al * 0.8)))
        g.alpha_composite(out); out = g
    return out

def radial(d, cx, cy, rad, c0, c1, steps=None):
    for i in range(int(rad), 0, -1):
        t = i / rad
        d.ellipse([cx - i, cy - i, cx + i, cy + i], fill=lerp(c1, c0, 1 - t))

def glint(px, x, y, big=False):
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)) + (((2, 0), (-2, 0), (0, 2), (0, -2)) if big else ()):
        if 0 <= x + dx < N and 0 <= y + dy < N and px[x + dx, y + dy][3] > 0:
            px[x + dx, y + dy] = (255, 255, 255, 255) if (dx, dy) == (0, 0) else (255, 250, 230, 255)

def new(): im = Image.new('RGBA', (N, N), (0, 0, 0, 0)); return im, ImageDraw.Draw(im)

T = {}
# ---------------------------------------------------------------- Devil Shard: a jagged crimson crystal with black heart and glowing veins
im, d = new()
d.polygon([(16, 1), (22, 9), (25, 20), (19, 30), (12, 28), (8, 18), (10, 8)], fill=(120, 14, 22, 255))
d.polygon([(16, 1), (22, 9), (17, 14), (12, 9)], fill=(200, 40, 46, 255))
d.polygon([(17, 14), (25, 20), (19, 30)], fill=(80, 8, 14, 255))
d.polygon([(12, 9), (17, 14), (13, 24), (8, 18)], fill=(150, 20, 30, 255))
d.polygon([(14, 13), (19, 15), (17, 23), (13, 21)], fill=(22, 6, 10, 255))   # the black heart
for (a, b) in (((15, 5), (14, 13)), ((18, 15), (22, 20)), ((13, 21), (11, 26)), ((17, 23), (19, 27))): d.line([a, b], fill=(255, 90, 80, 255))
px = im.load(); glint(px, 15, 4, True); glint(px, 21, 11)
T['devil_shard'] = finish(im, glow=(255, 40, 40, 255))
# ---------------------------------------------------------------- Mana Core: a glowing violet core in a gold cage
im, d = new()
radial(d, 16, 16, 11, (255, 240, 255, 255), (70, 30, 140, 255))
d.ellipse([10, 10, 22, 22], fill=None, outline=(160, 120, 255, 255))
for a in range(0, 360, 60):   # the gold cage: four arcs and a band
    pass
d.arc([4, 4, 28, 28], 0, 360, fill=(220, 170, 60, 255), width=2)
d.arc([9, 4, 23, 28], 0, 360, fill=(240, 200, 90, 255), width=1)
d.line([(4, 16), (28, 16)], fill=(200, 150, 50, 255), width=2)
for (x, y) in ((16, 3), (16, 29), (3, 16), (29, 16)): d.rectangle([x - 1, y - 1, x + 1, y + 1], fill=(255, 220, 110, 255))
px = im.load(); glint(px, 13, 12, True)
T['mana_core'] = finish(im, glow=(170, 110, 255, 255))
# ---------------------------------------------------------------- Rift Ink: a round flask of swirling void, a cork, stars inside
im, d = new()
d.rectangle([13, 3, 19, 8], fill=(150, 105, 60, 255)); d.rectangle([13, 3, 19, 4], fill=(190, 140, 90, 255))   # cork
d.rectangle([12, 8, 20, 11], fill=(170, 200, 230, 255))                                                        # neck
d.ellipse([5, 10, 27, 30], fill=(170, 200, 230, 255))
radial(d, 16, 21, 9, (190, 120, 255, 255), (30, 10, 70, 255))
for i in range(40):   # the swirl
    a = i * 0.32; r = 1 + i * 0.2
    x, y = 16 + math.cos(a) * r, 21 + math.sin(a) * r * 0.8
    d.point((x, y), fill=(230, 180, 255, 255))
px = im.load()
for (x, y) in ((12, 18), (20, 24), (17, 15)): px[x, y] = (255, 255, 255, 255)
glint(px, 9, 16)
T['rift_ink'] = finish(im, glow=(150, 80, 255, 255))
# ---------------------------------------------------------------- Despair Fang: a long curved fang, teal-white, green cracks
im, d = new()
pts = []
for i in range(21):
    t = i / 20; x = 8 + t * 16 + math.sin(t * 3.0) * 3; y = 3 + t * 26
    w = 5.5 * (1 - t) + 0.6
    pts.append((x, y, w))
left = [(x - w, y) for x, y, w in pts]; right = [(x + w, y) for x, y, w in pts]
d.polygon(left + right[::-1], fill=(200, 235, 225, 255))
d.polygon([(p[0], p[1]) for p in left[:6]] + [(x, y) for x, y, w in pts[5::-1]], fill=(150, 200, 190, 255))
d.rectangle([3, 2, 13, 6], fill=(40, 70, 60, 255))   # the root, dark
for (a, b) in (((12, 9), (15, 13)), ((15, 13), (14, 16)), ((17, 17), (20, 21))): d.line([a, b], fill=(80, 255, 170, 255))
px = im.load(); glint(px, 18, 11); glint(px, 23, 25)
T['despair_fang'] = finish(im, glow=(80, 255, 170, 255))
# ---------------------------------------------------------------- Copied Sigil: a hexagon seal, half violet, half silver, mirrored rune
im, d = new()
hexa = [(16 + 13 * math.cos(math.pi / 3 * k + math.pi / 6), 16 + 13 * math.sin(math.pi / 3 * k + math.pi / 6)) for k in range(6)]
d.polygon(hexa, fill=(120, 70, 190, 255))
d.polygon([(16, 3)] + [p for p in hexa if p[0] >= 16] + [(16, 29)], fill=(200, 200, 215, 255))
d.ellipse([8, 8, 24, 24], outline=(250, 240, 255, 255))
for s in (-1, 1):   # the rune and its copy
    d.line([(16 + s * 2, 11), (16 + s * 6, 16), (16 + s * 2, 21)], fill=(40, 20, 60, 255) if s < 0 else (60, 60, 80, 255), width=2)
d.line([(16, 9), (16, 23)], fill=(255, 255, 255, 255))
px = im.load(); glint(px, 10, 9)
T['copied_sigil'] = finish(im, glow=(190, 140, 255, 255))
# ---------------------------------------------------------------- Elven Blade Shard: a broken white-gold blade tip with a gold rune
im, d = new()
d.polygon([(26, 2), (29, 5), (12, 24), (6, 26), (8, 20)], fill=(235, 240, 250, 255))
d.polygon([(26, 2), (29, 5), (18, 16)], fill=(255, 255, 255, 255))
d.line([(27, 3), (9, 23)], fill=(190, 200, 220, 255))
d.polygon([(6, 26), (12, 24), (10, 29), (3, 30), (2, 27)], fill=(200, 205, 220, 255))   # the jagged break
for (x, y) in ((17, 13), (19, 12), (18, 14), (15, 16)): d.point((x, y), fill=(240, 190, 60, 255))
d.line([(14, 17), (20, 11)], fill=(240, 190, 60, 255))
px = im.load(); glint(px, 25, 5, True)
T['elven_blade_shard'] = finish(im, glow=(255, 230, 140, 255))
# ---------------------------------------------------------------- Grave Ember: a soul flame in a small iron cage
im, d = new()
d.polygon([(16, 4), (22, 14), (21, 22), (16, 25), (11, 22), (10, 14)], fill=(70, 90, 230, 255))
d.polygon([(16, 9), (19, 15), (18, 21), (16, 22), (14, 21), (13, 15)], fill=(140, 200, 255, 255))
d.polygon([(16, 14), (17.5, 18), (16, 21), (14.5, 18)], fill=(240, 250, 255, 255))
d.rectangle([8, 26, 24, 29], fill=(60, 60, 70, 255))                          # the cage's base
for x in (8, 13, 19, 24): d.line([(x, 26), (x if x in (8, 24) else x, 10)], fill=(80, 80, 92, 255), width=1)
d.arc([8, 4, 24, 16], 180, 360, fill=(80, 80, 92, 255))
d.rectangle([14, 1, 18, 3], fill=(90, 90, 100, 255))
T['grave_ember'] = finish(im, glow=(90, 140, 255, 255))
# ---------------------------------------------------------------- Curse Thread: a spool of cursed purple thread, its loose end curling
im, d = new()
d.rectangle([8, 6, 24, 9], fill=(120, 80, 50, 255)); d.rectangle([8, 23, 24, 26], fill=(120, 80, 50, 255))   # spool ends
d.rectangle([9, 9, 23, 23], fill=(90, 30, 120, 255))
for y in range(10, 23, 2): d.line([(9, y), (23, y + 1)], fill=(140, 60, 170, 255))
pts = [(23 + math.sin(t * 0.9) * 3 + t * 0.6, 14 + t * 1.8) for t in range(8)]
d.line(pts, fill=(170, 90, 200, 255), width=1)
for (x, y) in ((12, 12), (19, 18), (15, 20)): d.point((x, y), fill=(140, 255, 120, 255))   # the curse, glinting in it
px = im.load(); glint(px, 11, 7)
T['curse_thread'] = finish(im, glow=(130, 255, 110, 255))
# ---------------------------------------------------------------- Mirror Shard: a long triangle of mirror glass in a silver rim
im, d = new()
d.polygon([(7, 3), (27, 12), (11, 29)], fill=(190, 210, 235, 255))
for i in range(12):   # the reflection: diagonal bands
    t = i / 12
    d.line([(9 + i * 1.4, 5 + i * 0.6), (10 + i * 0.3, 27 - i * 1.2)], fill=lerp((160, 190, 230, 255), (240, 250, 255, 255), abs(math.sin(i))))
d.line([(7, 3), (27, 12)], fill=(220, 225, 235, 255), width=1)
d.line([(7, 3), (11, 29)], fill=(150, 155, 170, 255), width=1)
px = im.load(); glint(px, 12, 9, True); glint(px, 18, 16)
T['mirror_shard'] = finish(im, glow=(220, 240, 255, 255))
# ---------------------------------------------------------------- Mana Crystal Binding: a leather band set with three cyan crystals
im, d = new()
d.rounded_rectangle([2, 11, 29, 21], 3, fill=(110, 70, 40, 255))
d.line([(3, 13), (28, 13)], fill=(150, 100, 60, 255)); d.line([(3, 19), (28, 19)], fill=(80, 50, 30, 255))
for x in (5, 16, 27): d.rectangle([x - 1, 12, x, 20], fill=(200, 160, 70, 255))   # brass studs
for cx in (10, 21):
    d.polygon([(cx, 6), (cx + 4, 12), (cx + 3, 20), (cx, 25), (cx - 3, 20), (cx - 4, 12)], fill=(60, 200, 230, 255))
    d.polygon([(cx, 6), (cx + 4, 12), (cx, 14), (cx - 4, 12)], fill=(170, 245, 255, 255))
px = im.load(); glint(px, 9, 9); glint(px, 20, 9)
T['mana_crystal_binding'] = finish(im, glow=(80, 220, 255, 255))
# ---------------------------------------------------------------- Silver Clasp: an ornate book clasp with a blue stone
im, d = new()
d.polygon([(3, 12), (14, 9), (28, 10), (29, 16), (28, 22), (14, 23), (3, 20)], fill=(185, 190, 205, 255))
d.polygon([(3, 12), (14, 9), (28, 10), (14, 14)], fill=(225, 230, 240, 255))
for (x, y) in ((6, 14), (6, 18)): d.ellipse([x - 1, y - 1, x + 1, y + 1], fill=(120, 125, 140, 255))   # rivets
d.ellipse([16, 11, 26, 21], fill=(130, 135, 150, 255))
d.ellipse([18, 13, 24, 19], fill=(50, 90, 210, 255)); d.point((19, 14), fill=(200, 220, 255, 255))
d.arc([12, 7, 30, 25], 200, 340, fill=(225, 230, 240, 255))
px = im.load(); glint(px, 9, 11)
T['silver_clasp'] = finish(im)
# ---------------------------------------------------------------- Swift Bookmark: a teal ribbon with a forked tail and a feather tassel
im, d = new()
d.polygon([(13, 7), (19, 7), (19, 26), (16, 23), (13, 26)], fill=(40, 170, 160, 255))
d.line([(14, 8), (14, 24)], fill=(80, 210, 200, 255))
for y in range(10, 22, 4): d.line([(13, y), (19, y + 1)], fill=(220, 190, 80, 255))   # gold stitching
d.rectangle([12, 4, 20, 7], fill=(220, 180, 70, 255))
for i in range(9):   # the feather
    t = i / 8; x = 20 + t * 8; y = 5 - t * 3
    d.line([(x, y), (x - 2, y + 3)], fill=(240, 240, 250, 255))
d.line([(19, 5), (28, 2)], fill=(180, 180, 200, 255))
px = im.load(); glint(px, 15, 12)
T['swift_bookmark'] = finish(im)
# ---------------------------------------------------------------- Runed Spine: a grimoire's spine, gold bands, glowing violet runes
im, d = new()
d.rounded_rectangle([10, 2, 22, 29], 4, fill=(70, 40, 30, 255))
d.rectangle([11, 3, 13, 28], fill=(100, 60, 45, 255))
for y in (5, 15, 25): d.rectangle([9, y, 23, y + 2], fill=(220, 175, 70, 255))
for (y0, k) in ((9, 0), (19, 1)):
    if k == 0: d.line([(14, y0), (18, y0 + 3), (14, y0 + 4)], fill=(190, 140, 255, 255))
    else: d.line([(18, y0), (14, y0 + 2), (18, y0 + 4)], fill=(190, 140, 255, 255)); d.point((16, y0 + 2), fill=(255, 230, 255, 255))
px = im.load(); glint(px, 12, 6)
T['runed_spine'] = finish(im, glow=(170, 110, 255, 255))
# ---------------------------------------------------------------- Phoenix Ribbon: a flowing red ribbon, its ends in flame, a gold feather
im, d = new()
for i in range(26):
    t = i / 25; x = 4 + t * 24; y = 16 + math.sin(t * 6.3) * 6
    d.line([(x, y - 3), (x, y + 3)], fill=lerp((200, 30, 30, 255), (255, 140, 40, 255), t))
for (x, y) in ((28, 13), (29, 10), (27, 8), (30, 15)): d.point((x, y), fill=(255, 220, 90, 255))   # flame at the end
d.polygon([(26, 5), (30, 9), (28, 18), (26, 12)], fill=(255, 150, 40, 255))
d.polygon([(27, 8), (29, 10), (28, 14)], fill=(255, 230, 120, 255))
d.line([(6, 24), (14, 28)], fill=(240, 190, 60, 255), width=2)   # the gold feather
for k in range(4): d.line([(7 + k * 2, 25 + k), (6 + k * 2, 22 + k)], fill=(255, 220, 110, 255))
px = im.load(); glint(px, 9, 12)
T['phoenix_ribbon'] = finish(im, glow=(255, 120, 40, 255))

for name, img in T.items():
    img.save(os.path.join(TEX, name + '.png'))
    json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'iron_spell_grimoire:item/' + name}}, open(os.path.join(MOD, name + '.json'), 'w'), indent=1)
# preview sheet
cols = 7; S = 6
sheet = Image.new('RGBA', (cols * (N * S + 10) + 10, ((len(T) + cols - 1) // cols) * (N * S + 10) + 10), (60, 60, 66, 255))
for i, (n, img) in enumerate(T.items()):
    sheet.alpha_composite(img.resize((N * S, N * S), Image.NEAREST), (10 + (i % cols) * (N * S + 10), 10 + (i // cols) * (N * S + 10)))
sheet.save(os.path.join(os.path.dirname(__file__), '..', 'upgrade_preview.png'))
print('ok', len(T))
