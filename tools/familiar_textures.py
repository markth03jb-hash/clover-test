"""Mana familiar sprites, 32x32 pixel art (client/FamiliarRenderer): ten small original creatures, one per element,
plus the soft halo drawn behind a stage-3 familiar. Outline/rim/shade/glow as in upgrade_textures.py."""
import os, math
from PIL import Image, ImageDraw, ImageFilter
R = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/iron_spell_grimoire/textures/entity/familiar')
os.makedirs(R, exist_ok=True)
N = 32
def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))
def new(): im = Image.new('RGBA', (N, N), (0, 0, 0, 0)); return im, ImageDraw.Draw(im)
def radial(d, cx, cy, rad, c0, c1):
    for i in range(int(rad), 0, -1): d.ellipse([cx - i, cy - i, cx + i, cy + i], fill=lerp(c1, c0, 1 - i / rad))
def finish(im, glow=None, outline=(16, 10, 14, 255)):
    px = im.load(); a = [[px[x, y][3] > 0 for x in range(N)] for y in range(N)]
    out = im.copy(); po = out.load()
    for y in range(N):
        for x in range(N):
            if a[y][x]:
                r, g, b, al = px[x, y]
                if (y > 0 and not a[y-1][x]) or (x > 0 and not a[y][x-1]): po[x, y] = (min(255, r+45), min(255, g+45), min(255, b+45), al)
                elif (y < N-1 and not a[y+1][x]) or (x < N-1 and not a[y][x+1]): po[x, y] = (int(r*.62), int(g*.62), int(b*.62), al)
                continue
            if any(0 <= x+dx < N and 0 <= y+dy < N and a[y+dy][x+dx] for dx, dy in ((1,0),(-1,0),(0,1),(0,-1))): po[x, y] = outline
    if glow:
        g = Image.new('RGBA', (N, N), (0,0,0,0)); pg = g.load()
        for y in range(N):
            for x in range(N):
                if po[x, y][3] > 0: pg[x, y] = glow
        g = g.filter(ImageFilter.GaussianBlur(1.6)); gp = g.load()
        for y in range(N):
            for x in range(N):
                r, gg, b, al = gp[x, y]; gp[x, y] = (r, gg, b, min(110, int(al*.8)))
        g.alpha_composite(out); out = g
    return out
def eyes(d, y, x0=13, x1=19, c=(255, 255, 255, 255), pupil=(20, 14, 20, 255)):
    for x in (x0, x1):
        d.rectangle([x-1, y-1, x+1, y+1], fill=c); d.point((x, y), fill=pupil)
T = {}
# Salamander: a round ember lizard curled on itself, flame crest and tail flame
im, d = new()
d.ellipse([7, 12, 25, 27], fill=(200, 60, 24, 255)); d.ellipse([10, 16, 22, 26], fill=(240, 130, 40, 255))
d.ellipse([9, 6, 23, 18], fill=(214, 72, 28, 255))
d.polygon([(14, 7), (16, 1), (18, 7)], fill=(255, 190, 60, 255)); d.polygon([(11, 8), (11, 3), (14, 7)], fill=(255, 150, 40, 255)); d.polygon([(18, 7), (21, 3), (21, 8)], fill=(255, 150, 40, 255))
d.polygon([(24, 22), (30, 16), (29, 24), (26, 27)], fill=(255, 170, 50, 255)); d.point((29, 17), fill=(255, 240, 160, 255))
d.rectangle([9, 25, 11, 28], fill=(170, 46, 20, 255)); d.rectangle([20, 25, 22, 28], fill=(170, 46, 20, 255))
eyes(d, 12, c=(255, 230, 120, 255))
T['salamander'] = finish(im, (255, 120, 40, 255))
# Wind sprite: a leaf-winged teardrop of green air with a swirl tail
im, d = new()
d.polygon([(4, 8), (13, 13), (8, 18)], fill=(150, 230, 170, 255)); d.polygon([(28, 8), (19, 13), (24, 18)], fill=(150, 230, 170, 255))
d.ellipse([10, 8, 22, 22], fill=(90, 200, 140, 255)); d.ellipse([12, 10, 20, 18], fill=(150, 240, 190, 255))
d.arc([12, 18, 22, 30], 200, 80, fill=(120, 220, 170, 255), width=2); d.arc([14, 22, 20, 28], 260, 60, fill=(200, 255, 220, 255), width=1)
eyes(d, 14, 14, 18, c=(240, 255, 245, 255), pupil=(20, 60, 40, 255))
T['sylph'] = finish(im, (120, 255, 180, 255))
# Light wisp: a bright four-pointed star-soul with a halo
im, d = new()
d.polygon([(16, 2), (19, 13), (30, 16), (19, 19), (16, 30), (13, 19), (2, 16), (13, 13)], fill=(255, 230, 130, 255))
radial(d, 16, 16, 7, (255, 255, 240, 255), (255, 210, 90, 255))
eyes(d, 16, 14, 18, c=(255, 255, 255, 255), pupil=(150, 100, 20, 255))
T['wisp'] = finish(im, (255, 230, 120, 255), outline=(90, 60, 10, 255))
# Shadow moth: dark wings with violet eye-spots
im, d = new()
d.polygon([(15, 12), (3, 4), (2, 14), (8, 20), (15, 18)], fill=(46, 30, 70, 255)); d.polygon([(17, 12), (29, 4), (30, 14), (24, 20), (17, 18)], fill=(46, 30, 70, 255))
d.polygon([(15, 18), (6, 22), (9, 29), (15, 23)], fill=(36, 22, 56, 255)); d.polygon([(17, 18), (26, 22), (23, 29), (17, 23)], fill=(36, 22, 56, 255))
for cx in (8, 24): d.ellipse([cx-3, 9, cx+3, 15], fill=(150, 70, 220, 255)); d.ellipse([cx-1, 11, cx+1, 13], fill=(240, 200, 255, 255))
d.ellipse([14, 9, 18, 25], fill=(70, 50, 90, 255)); d.line([(15, 9), (12, 4)], fill=(120, 90, 150, 255)); d.line([(17, 9), (20, 4)], fill=(120, 90, 150, 255))
d.point((15, 12), fill=(230, 160, 255, 255)); d.point((17, 12), fill=(230, 160, 255, 255))
T['moth'] = finish(im, (150, 70, 220, 255))
# Pebble golem: a stack of mossy stones with a glowing seam
im, d = new()
d.ellipse([6, 14, 26, 29], fill=(120, 104, 84, 255)); d.ellipse([9, 5, 23, 17], fill=(140, 122, 98, 255))
d.ellipse([3, 15, 9, 23], fill=(110, 94, 76, 255)); d.ellipse([23, 15, 29, 23], fill=(110, 94, 76, 255))
d.line([(12, 21), (16, 24), (20, 20)], fill=(255, 190, 90, 255)); d.rectangle([10, 5, 15, 7], fill=(90, 150, 70, 255))
eyes(d, 11, 13, 19, c=(255, 200, 100, 255), pupil=(255, 240, 200, 255))
T['golem'] = finish(im, None)
# Quicksilver drop: a shiny droplet with a smile-less calm face
im, d = new()
d.polygon([(16, 2), (24, 16), (8, 16)], fill=(150, 170, 200, 255)); d.ellipse([7, 10, 25, 29], fill=(150, 170, 200, 255))
radial(d, 13, 16, 5, (250, 252, 255, 255), (170, 190, 220, 255)); d.ellipse([9, 22, 23, 28], fill=(110, 130, 170, 255))
d.ellipse([10, 12, 13, 15], fill=(255, 255, 255, 255))
eyes(d, 19, 13, 19, c=(40, 50, 80, 255), pupil=(230, 240, 255, 255))
T['droplet'] = finish(im, (180, 210, 255, 255))
# Spark imp: a crackling yellow ball with zig-zag horns
im, d = new()
radial(d, 16, 18, 9, (255, 255, 210, 255), (240, 200, 40, 255))
d.polygon([(15, 10), (19, 3), (17, 7), (21, 6), (17, 12)], fill=(255, 250, 190, 255)); d.polygon([(13, 26), (11, 31), (16, 27)], fill=(240, 200, 40, 255)); d.polygon([(19, 26), (21, 31), (16, 27)], fill=(240, 200, 40, 255))
d.line([(25, 18), (29, 15), (27, 20), (31, 19)], fill=(255, 250, 170, 255)); d.line([(7, 20), (3, 17), (5, 22), (1, 22)], fill=(255, 250, 170, 255))
eyes(d, 17, 13, 19, c=(70, 50, 10, 255), pupil=(255, 255, 255, 255))
T['spark'] = finish(im, (255, 230, 80, 255))
# Clockwork moth: brass wings with gear rims and a clock-face body
im, d = new()
for cx, cy, r in ((8, 11, 7), (24, 11, 7), (10, 23, 5), (22, 23, 5)):
    d.ellipse([cx-r, cy-r, cx+r, cy+r], fill=(190, 150, 70, 255)); d.ellipse([cx-r+2, cy-r+2, cx+r-2, cy+r-2], fill=(230, 200, 120, 255))
    for k in range(8):
        a = k * math.pi / 4; d.point((round(cx + math.cos(a) * r), round(cy + math.sin(a) * r)), fill=(120, 90, 40, 255))
d.ellipse([12, 9, 20, 27], fill=(80, 140, 150, 255)); d.ellipse([13, 13, 19, 19], fill=(230, 245, 240, 255))
d.line([(16, 16), (16, 13)], fill=(40, 50, 60, 255)); d.line([(16, 16), (18, 16)], fill=(40, 50, 60, 255))
T['clockwork'] = finish(im, (120, 220, 210, 255))
# Crystal shard: a floating prism with an inner eye
im, d = new()
d.polygon([(16, 1), (25, 12), (16, 31), (7, 12)], fill=(130, 110, 220, 255))
d.polygon([(16, 1), (25, 12), (16, 15)], fill=(200, 190, 255, 255)); d.polygon([(7, 12), (16, 15), (16, 31)], fill=(90, 70, 180, 255))
d.ellipse([12, 11, 20, 18], fill=(250, 250, 255, 255)); d.ellipse([14, 12, 18, 17], fill=(90, 60, 200, 255)); d.point((16, 14), fill=(255, 255, 255, 255))
d.point((4, 5), fill=(220, 210, 255, 255)); d.point((28, 22), fill=(220, 210, 255, 255))
T['crystal'] = finish(im, (170, 150, 255, 255))
# Bone wisp: a little horned skull trailing pale flame
im, d = new()
d.polygon([(10, 20), (16, 31), (22, 20)], fill=(170, 220, 230, 200)); d.polygon([(13, 22), (16, 29), (19, 22)], fill=(230, 250, 255, 220))
d.ellipse([8, 5, 24, 21], fill=(236, 230, 214, 255)); d.rectangle([11, 18, 21, 23], fill=(236, 230, 214, 255))
d.polygon([(9, 9), (4, 3), (8, 4)], fill=(210, 200, 180, 255)); d.polygon([(23, 9), (28, 3), (24, 4)], fill=(210, 200, 180, 255))
d.ellipse([10, 11, 15, 16], fill=(30, 26, 34, 255)); d.ellipse([17, 11, 22, 16], fill=(30, 26, 34, 255))
d.point((12, 13), fill=(140, 230, 255, 255)); d.point((19, 13), fill=(140, 230, 255, 255))
for x in (13, 16, 19): d.line([(x, 21), (x, 23)], fill=(150, 140, 125, 255))
T['bone'] = finish(im, (140, 220, 255, 255))
for k, v in T.items(): v.save(os.path.join(R, k + '.png'))
# the halo: a soft white ring with eight points
g = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); gd = ImageDraw.Draw(g)
for i in range(30, 0, -1):
    t = i / 30; a = int(150 * math.exp(-((t - 0.7) ** 2) / 0.02)) + int(70 * (1 - t) ** 2)
    gd.ellipse([32-i, 32-i, 32+i, 32+i], fill=(255, 255, 255, min(255, a)))
for k in range(8):
    a = k * math.pi / 4; gd.line([(32 + math.cos(a) * 18, 32 + math.sin(a) * 18), (32 + math.cos(a) * 31, 32 + math.sin(a) * 31)], fill=(255, 255, 255, 120), width=1)
g = g.filter(ImageFilter.GaussianBlur(0.8)); g.save(os.path.join(R, 'glow.png'))
sheet = Image.new('RGBA', (N * 8 * 5, N * 8 * 2), (40, 40, 50, 255))
for i, v in enumerate(T.values()): sheet.paste(v.resize((N * 8, N * 8), Image.NEAREST), ((i % 5) * N * 8, (i // 5) * N * 8), v.resize((N * 8, N * 8), Image.NEAREST))
sheet.save('/tmp/claude-0/-home-claude/267cab68-cab5-5d3b-ab2c-ac307f4fb5bc/scratchpad/familiar_preview.png')
