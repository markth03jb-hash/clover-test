"""The Architect of Ruin Grimoire's fortress pieces, modelled in Blender (run with Blender's Python: bpy). Original designs:

  Rampart wall     two blocks wide, three tall: grey stone courses, a brass band, merlons on top, an arrow slit
  Gatehouse        an arch with a brass-banded portcullis and two small flag poles
  Watchtower       a round stone tower, crenellated, an ember-orange banner (arrows)
  Arcane spire     a slim tower, open at the top, a teal crystal floating over it (beams)
  Forge tower      a square tower with a chimney and a glowing ember window (fire)
  Siege platform   a low square bastion with a ballista on top (bolts)
  Ballista         the Workshop's turret on its own: a wooden frame, brass-capped arms, a loaded bolt
  Bone sentry      the Crypt's guard: a hooded figure of pale stone and bone, a tower shield and a halberd

Blender space (pixels; exported at 1/16): Z up, X across, the front looking toward -Y. Nothing here is ever placed as
blocks in the world: in the game they are entities (FortressWall, FortressTower...) drawn with these models.

  python tools/blender/architect_ruin.py render OUT_DIR [names]   a sheet of every model
  python tools/blender/architect_ruin.py game OUT_DIR             writes the models into the mod (models/item, textures/item)
"""
import math, os, sys
import bpy, bmesh
from mathutils import Vector, Matrix
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gilded_thorn as gt

TEX = "architect_ruin"
gt.REG.clear()
# atlas regions in 256-space (the image itself is painted at twice that, for finer detail)
gt.REG.update({"stone": (0, 0, 64, 64), "stone_d": (64, 0, 128, 64), "stone_l": (128, 0, 192, 64), "mortar": (192, 0, 256, 64),
               "stone_g": (0, 64, 64, 128), "moss": (64, 64, 128, 128), "wood": (128, 64, 192, 128), "wood_d": (192, 64, 256, 128),
               "bronze": (0, 128, 64, 160), "iron": (64, 128, 128, 160), "cloth": (128, 128, 192, 160), "rope": (192, 128, 256, 160),
               "slate": (0, 160, 64, 192), "ember": (64, 160, 96, 192), "arcane": (96, 160, 128, 192), "bone": (128, 160, 192, 192),
               "hood": (192, 160, 256, 192), "dark": (0, 192, 32, 224), "soot": (32, 192, 96, 224)})
REG = gt.REG
GLOW = []
LOW = False
K = 2   # texture scale


# ------------------------------------------------------------------ the texture: layered noise, grime, cracks
import random
def mix(a, b, t): t = max(0.0, min(1.0, t)); return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def _hash(x, y, s):
    n = (x * 374761393 + y * 668265263 + s * 1442695041) & 0xffffffff
    n = (n ^ (n >> 13)) * 1274126177 & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffff) / 65535.0


def vnoise(x, y, s):   # smooth value noise
    xi, yi = int(math.floor(x)), int(math.floor(y)); xf, yf = x - xi, y - yi
    u, v = xf * xf * (3 - 2 * xf), yf * yf * (3 - 2 * yf)
    a, b = _hash(xi, yi, s), _hash(xi + 1, yi, s); c, d = _hash(xi, yi + 1, s), _hash(xi + 1, yi + 1, s)
    return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v


def fbm(x, y, s, oct=4):
    t, amp, f, norm = 0.0, 1.0, 1.0, 0.0
    for o in range(oct):
        t += vnoise(x * f, y * f, s + o * 17) * amp; norm += amp; amp *= 0.5; f *= 2.0
    return t / norm


def paint(out):
    S = 256 * K
    im = Image.new("RGBA", (S, S), (0, 0, 0, 255)); px = im.load()
    def region(name, fn):
        x0, y0, x1, y1 = [c * K for c in REG[name]]
        for x in range(x0, x1):
            for y in range(y0, y1):
                u, v = (x - x0) / (x1 - x0 - 1), (y - y0) / (y1 - y0 - 1)
                px[x, y] = tuple(max(0, min(255, int(c))) for c in fn(u, v, x - x0, y - y0)) + (255,)
    def stone(base, var, seed, grime=0.0, moss=0.0):
        def f(u, v, x, y):
            n = fbm(x / 9.0, y / 9.0, seed)
            fine = _hash(x, y, seed + 3) * 0.18
            pits = 1.0 if _hash(x // 2, y // 2, seed + 9) > 0.965 else 0.0
            crack = 1.0 if abs(fbm(x / 14.0, y / 14.0, seed + 5, 3) - 0.5) < 0.012 else 0.0
            k = 0.55 + (n - 0.5) * 0.9 + fine - pits * 0.25 - crack * 0.35
            c = tuple(base[i] + (k - 0.5) * var[i] * 2 for i in range(3))
            if grime: c = mix(c, (38, 36, 32), grime * (0.35 + 0.65 * v) * (0.6 + 0.4 * fbm(x / 6, y / 6, seed + 7)))
            if moss:
                m = fbm(x / 7.0, y / 7.0, seed + 11)
                if m > 0.55 - moss * 0.2: c = mix(c, (62, 80, 40) if _hash(x, y, seed) > 0.4 else (44, 60, 30), min(1, (m - 0.5) * 4))
            return c
        return f
    region("stone", stone((118, 114, 106), (34, 32, 30), 1))
    region("stone_d", stone((86, 83, 78), (26, 24, 22), 2))
    region("stone_l", stone((146, 141, 130), (34, 32, 28), 3))
    region("stone_g", stone((104, 100, 92), (30, 28, 26), 4, grime=0.8))
    region("moss", stone((100, 98, 88), (30, 28, 26), 5, grime=0.3, moss=1.0))
    region("mortar", lambda u, v, x, y: mix((34, 32, 30), (58, 55, 50), fbm(x / 4, y / 4, 6)))
    def wood(base, dark, seed):
        def f(u, v, x, y):
            w = math.sin(y * 0.55 + fbm(x / 20.0, y / 6.0, seed) * 7.0) * 0.5 + 0.5
            k = 0.3 + 0.5 * w + (_hash(x, y, seed) - 0.5) * 0.15
            c = mix(dark, base, k)
            if _hash(x // 6, y // 40, seed + 2) > 0.92 and abs(y % 40 - 20) < 2: c = mix(c, (20, 14, 8), 0.7)   # a knot
            return c
        return f
    region("wood", wood((128, 96, 62), (70, 50, 30), 7))
    region("wood_d", wood((84, 62, 40), (42, 30, 18), 8))
    def metal(base, var, seed, patina):
        def f(u, v, x, y):
            n = fbm(x / 6.0, y / 6.0, seed)
            c = tuple(base[i] + (n - 0.5) * var[i] * 2 + (_hash(x, y, seed) - 0.5) * 18 for i in range(3))
            p = fbm(x / 8.0, y / 8.0, seed + 4)
            if patina and p > 0.58: c = mix(c, patina, min(1, (p - 0.58) * 5))
            return c
        return f
    region("bronze", metal((128, 96, 54), (34, 26, 16), 9, (74, 110, 92)))
    region("iron", metal((58, 58, 62), (18, 18, 20), 10, (96, 58, 34)))
    def cloth(u, v, x, y):
        weave = 0.08 * ((x + y) % 2)
        n = fbm(x / 10.0, y / 10.0, 12)
        c = mix((92, 26, 24), (138, 44, 36), n * 0.8 + weave)
        return mix(c, (60, 48, 40), max(0, v - 0.6) * 1.2)   # dirtier toward the hem
    region("cloth", cloth)
    region("rope", lambda u, v, x, y: mix((96, 80, 56), (150, 128, 92), 0.5 + 0.5 * math.sin((x + y * 0.6) * 1.3) * (0.6 + 0.4 * _hash(x, y, 13))))
    def slate(u, v, x, y):
        row = (y // 6) % 2
        tile = _hash((x + row * 5) // 10, y // 6, 14)
        c = mix((40, 44, 52), (78, 82, 92), tile * 0.7 + fbm(x / 5, y / 5, 15) * 0.3)
        if y % 6 == 0 or (x + row * 5) % 10 == 0: c = mix(c, (20, 20, 24), 0.7)
        return c
    region("slate", slate)
    region("ember", lambda u, v, x, y: mix((255, 214, 140), (220, 96, 28), abs(v - 0.5) * 2 + fbm(x / 3, y / 3, 16) * 0.3))
    region("arcane", lambda u, v, x, y: mix((210, 240, 236), (60, 150, 150), abs(v - 0.5) * 1.6 + fbm(x / 3, y / 3, 17) * 0.4))
    region("bone", stone((190, 182, 160), (26, 24, 20), 18, grime=0.35))
    region("hood", lambda u, v, x, y: mix((30, 30, 34), (64, 62, 66), fbm(x / 5, y / 5, 19) * 0.8 + 0.1 * ((x // 2 + y) % 2)))
    region("dark", lambda u, v, x, y: (12, 11, 10))
    region("soot", lambda u, v, x, y: mix((22, 20, 18), (70, 66, 60), (1 - v) * 0.6 + fbm(x / 5, y / 5, 20) * 0.4))
    im.save(out)


# ------------------------------------------------------------------ helpers
R_ = random.Random(7)


def box(name, c, s, region, bevel=0.0, glow=False):
    o = gt.box(name, c, s, region, bevel=0.0 if LOW else bevel, segs=1)   # the game's copy: plain edges (a third of the triangles)
    if glow: GLOW.append(o)
    return o


def rbox(name, c, s, region, yaw=0.0, tilt=0.0, bevel=0.0):
    """A box turned about Z by yaw (and tipped about its X by tilt): stones round a tower, voussoirs round an arch."""
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    bmesh.ops.scale(bm, vec=s, verts=bm.verts)
    if bevel > 0 and not LOW: bmesh.ops.bevel(bm, geom=bm.verts[:] + bm.edges[:], offset=bevel, segments=1, affect="EDGES", profile=0.5)
    if tilt: bmesh.ops.rotate(bm, cent=(0, 0, 0), matrix=Matrix.Rotation(tilt, 3, "Y"), verts=bm.verts)
    bmesh.ops.rotate(bm, cent=(0, 0, 0), matrix=Matrix.Rotation(yaw, 3, "Z"), verts=bm.verts)
    bmesh.ops.translate(bm, vec=c, verts=bm.verts)
    return gt.new_obj(name, bm, REG[region], smooth=False)


def tube(name, pts, region, res=4, bev=2, glow=False):
    if LOW: res, bev = 2, 1
    o = gt.curve_obj(name, pts, flat=1.0, region=region, res=res, bev=bev)
    if glow: GLOW.append(o)
    return o


def lathe(name, prof, region, M=12, glow=False):
    bm = bmesh.new(); rows = []
    for (r, z) in prof:
        rows.append([bm.verts.new((r * math.cos(2 * math.pi * j / M + math.pi / M), r * math.sin(2 * math.pi * j / M + math.pi / M), z)) for j in range(M)])
    for i in range(len(rows) - 1):
        for j in range(M):
            k = (j + 1) % M
            bm.faces.new((rows[i][j], rows[i][k], rows[i + 1][k], rows[i + 1][j]))
    bm.faces.new(rows[-1][::-1]); bm.faces.new(rows[0])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    o = gt.new_obj(name, bm, REG[region], smooth=False)
    if glow: GLOW.append(o)
    return o


def gem(name, c, r, h, region, glow=True):
    bm = bmesh.new()
    top = bm.verts.new((c[0], c[1], c[2] + h)); bot = bm.verts.new((c[0], c[1], c[2] - h))
    ring = [bm.verts.new((c[0] + r * math.cos(a * math.pi / 3), c[1] + r * math.sin(a * math.pi / 3), c[2])) for a in range(6)]
    for i in range(6):
        bm.faces.new((ring[i], ring[(i + 1) % 6], top)); bm.faces.new((ring[(i + 1) % 6], ring[i], bot))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    o = gt.new_obj(name, bm, REG[region], smooth=False)
    if glow: GLOW.append(o)
    return o


def stone_for(z, z1, rnd):
    """Weathering by height: grime and moss low down, cleaner stone higher, the odd pale one."""
    h = z / max(1.0, z1)
    r = rnd.random()
    if h < 0.18: return "moss" if r < 0.35 else "stone_g"
    if h < 0.4 and r < 0.3: return "stone_g"
    if r < 0.12: return "stone_l"
    if r < 0.35: return "stone_d"
    return "stone"


def courses(prefix, x0, x1, z0, z1, y, face, rnd, h=4.0, depth=1.4, gaps=True):
    """A wall face of real-looking masonry: courses of uneven height, blocks of uneven length and depth, every joint
    staggered, the odd stone missing or chipped. face: -1 the front (toward -Y), +1 the back."""
    box(prefix + "_back", ((x0 + x1) / 2, y + face * 0.2, (z0 + z1) / 2), (x1 - x0, 0.6, z1 - z0), "mortar")
    row, z = 0, z0
    while z < z1 - 0.05:
        hh = min(z1 - z, h + rnd.uniform(-0.7, 0.7))
        if z1 - (z + hh) < 1.2: hh = z1 - z
        x = x0 - rnd.uniform(0, 4.0) if row % 2 else x0
        k = 0
        while x < x1 - 0.05:
            bw = rnd.uniform(4.5, 8.5)
            a, b = max(x, x0), min(x + bw, x1)
            if b - a > 0.8:
                if gaps and z > z0 + 4 and rnd.random() < 0.025:   # a stone missing
                    pass
                else:
                    d = depth * rnd.uniform(0.7, 1.15)
                    reg = stone_for(z, z1, rnd)
                    cy = y + face * (0.2 + d / 2) - face * rnd.uniform(0, 0.25)
                    sz = ((b - a) - 0.45, d, hh - 0.45)
                    box("%s_%d_%d" % (prefix, row, k), ((a + b) / 2 + rnd.uniform(-0.1, 0.1), cy, z + hh / 2), sz, reg, bevel=0.18)
            x += bw; k += 1
        z += hh; row += 1


def side_courses(prefix, x, y0, y1, z0, z1, face, rnd, h=4.0, depth=1.4):
    """The same along a side face (toward -X face=-1 or +X face=+1)."""
    row, z = 0, z0
    while z < z1 - 0.05:
        hh = min(z1 - z, h + rnd.uniform(-0.7, 0.7))
        if z1 - (z + hh) < 1.2: hh = z1 - z
        yy = y0 - rnd.uniform(0, 4.0) if row % 2 else y0
        k = 0
        while yy < y1 - 0.05:
            bw = rnd.uniform(4.5, 8.5)
            a, b = max(yy, y0), min(yy + bw, y1)
            if b - a > 0.8:
                d = depth * rnd.uniform(0.7, 1.15)
                box("%s_%d_%d" % (prefix, row, k), (x + face * (d / 2 - rnd.uniform(0, 0.25)), (a + b) / 2, z + hh / 2), (d, (b - a) - 0.45, hh - 0.45), stone_for(z, z1, rnd), bevel=0.18)
            yy += bw; k += 1
        z += hh; row += 1


def merlons(prefix, x0, x1, z, y, depth, n, rnd):
    w = (x1 - x0) / (2 * n - 1)
    for i in range(n):
        x = x0 + w * (2 * i) + w / 2
        hgt = 4.6 - (rnd.uniform(0.5, 1.6) if rnd.random() < 0.3 else rnd.uniform(0, 0.3))   # some chipped down
        box("%s_m%d" % (prefix, i), (x, y, z + hgt / 2), (w - 0.3, depth, hgt), stone_for(30, 40, rnd), bevel=0.25)
        if rnd.random() < 0.5: box("%s_mc%d" % (prefix, i), (x + rnd.uniform(-1, 1), y, z + hgt + 0.3), (w * 0.5, depth * 0.8, 0.7), "stone_d", bevel=0.15)


def round_courses(prefix, r0, z0, z1, rnd, taper=0.0, n=14, h=4.0):
    """A round tower built course by course: n stones a ring, each a turned box, joints staggered."""
    row, z = 0, z0
    while z < z1 - 0.05:
        hh = min(z1 - z, h + rnd.uniform(-0.5, 0.5))
        r = r0 - taper * (z - z0)
        off = (math.pi / n) * (row % 2) + rnd.uniform(-0.05, 0.05)
        chord = 2 * r * math.sin(math.pi / n)
        for j in range(n):
            a = off + 2 * math.pi * j / n
            d = rnd.uniform(1.6, 2.2)
            rr = r - d / 2 + rnd.uniform(-0.15, 0.1)
            rbox("%s_%d_%d" % (prefix, row, j), (math.cos(a) * rr, math.sin(a) * rr, z + hh / 2), (d, chord + 0.15, hh - 0.4), stone_for(z, z1, rnd),
                 yaw=a, bevel=0.15)
        z += hh; row += 1
    lathe(prefix + "_core", [(r0 - 1.2, z0), (r0 - 1.2 - taper * (z1 - z0), z1)], "mortar", M=n)


def banner(prefix, x, y, z, w, hgt, rnd):
    """A faded, frayed cloth hanging from a pole: a few strips so its hem is ragged."""
    strips = 4
    for i in range(strips):
        sw = w / strips
        drop = hgt - rnd.uniform(0, hgt * 0.25)
        box("%s_s%d" % (prefix, i), (x - w / 2 + sw * (i + 0.5), y, z - drop / 2), (sw + 0.05, 0.35, drop), "cloth")
    box(prefix + "_rod", (x, y, z + 0.3), (w + 1.2, 0.6, 0.6), "wood_d")


# ------------------------------------------------------------------ the pieces
def wall():
    rnd = random.Random(11)
    W, Hh, D = 16.0, 38.0, 10.0   # one block wide: the game lines these up side by side
    box("w_core", (0, 0, Hh / 2), (W - 0.2, D - 2.6, Hh), "mortar")
    courses("w_front", -W / 2, W / 2, 2.2, Hh, -D / 2 + 1.4, -1, rnd)
    courses("w_backside", -W / 2, W / 2, 2.2, Hh, D / 2 - 1.4, 1, rnd)
    box("w_plinth", (0, 0, 1.1), (W, D + 1.2, 2.2), "stone_g", bevel=0.3)
    box("w_walk", (0, 0, Hh + 0.5), (W, D, 1.0), "stone_d")
    merlons("w", -W / 2, W / 2, Hh + 1.0, 0, D - 0.6, 2, rnd)
    box("w_slit", (0, -D / 2 - 0.05, Hh * 0.45), (1.0, 1.2, 6.0), "dark")
    box("w_slit2", (0, -D / 2 - 0.05, Hh * 0.45), (2.8, 1.2, 0.9), "dark")
    for x in (-5.5, 5.5):   # iron cramps at the top course
        box("w_cramp%.0f" % x, (x, -D / 2 - 0.1, Hh - 2.0), (2.2, 0.4, 0.5), "iron")


def gatehouse():
    rnd = random.Random(12)
    W, Hh, D = 48.0, 44.0, 12.0
    for sx in (-1, 1):   # the two piers
        cx = sx * 17
        box("g_core%d" % sx, (cx, 0, Hh / 2), (13, D - 2.6, Hh), "mortar")
        courses("g_front%d" % sx, cx - 7, cx + 7, 2.2, Hh, -D / 2 + 1.4, -1, rnd)
        courses("g_back%d" % sx, cx - 7, cx + 7, 2.2, Hh, D / 2 - 1.4, 1, rnd)
        side_courses("g_side%d" % sx, cx + sx * 7 - sx * 1.4, -D / 2, D / 2, 2.2, Hh, sx, rnd)
        merlons("g_top%d" % sx, cx - 7, cx + 7, Hh, 0, D - 0.6, 2, rnd)
        box("g_plinth%d" % sx, (cx, 0, 1.1), (14.4, D + 1.2, 2.2), "stone_g", bevel=0.3)
        tube("g_pole%d" % sx, [(cx + sx * 4, 0, Hh + 3, 0.45), (cx + sx * 4, 0, Hh + 20, 0.4)], "wood_d")
        banner("g_ban%d" % sx, cx + sx * 4 + sx * 3.6, 0, Hh + 19, 6.0, 9.0, rnd)
    # the arch: wedge-shaped voussoirs round the opening, a keystone at the top
    n = 11
    for i in range(n):
        a = math.pi * (i + 0.5) / n
        rr = 11.2
        cx, cz = rr * math.cos(a), 25 + rr * math.sin(a)
        tilt = -(a - math.pi / 2)
        reg = "stone_l" if i == n // 2 else stone_for(30, 40, rnd)
        bm = bmesh.new(); bmesh.ops.create_cube(bm, size=1.0)
        L = 3.4 if i == n // 2 else 2.8
        bmesh.ops.scale(bm, vec=(3.1, D + 0.4, L), verts=bm.verts)
        if not LOW: bmesh.ops.bevel(bm, geom=bm.verts[:] + bm.edges[:], offset=0.18, segments=1, affect="EDGES", profile=0.5)
        bmesh.ops.rotate(bm, cent=(0, 0, 0), matrix=Matrix.Rotation(tilt, 3, "Y"), verts=bm.verts)
        bmesh.ops.translate(bm, vec=(cx, 0, cz), verts=bm.verts)
        gt.new_obj("g_vous%d" % i, bm, REG[reg], smooth=False)
    box("g_lintel", (0, 0, 40.5), (20, D - 2.6, 7), "mortar")
    courses("g_lintelf", -10, 10, 37.5, Hh, -D / 2 + 1.4, -1, rnd, gaps=False)
    courses("g_lintelb", -10, 10, 37.5, Hh, D / 2 - 1.4, 1, rnd, gaps=False)
    merlons("g_topm", -10, 10, Hh, 0, D - 0.6, 3, rnd)
    # the wooden doors, swung half open behind, and the iron portcullis in front of them
    for sx in (-1, 1):
        for k in range(4):
            box("g_door%d_%d" % (sx, k), (sx * (2.5 + k * 2.2), 3.5, 13), (2.0, 1.2, 24), "wood" if k % 2 else "wood_d")
        for z in (5, 20):
            box("g_strap%d_%d" % (sx, z), (sx * 5.8, 2.7, z), (8.6, 0.4, 1.0), "iron")
    for i in range(7):
        x = -9 + 3 * i
        box("g_bar%d" % i, (x, -1.0, 15.5), (0.9, 0.9, 27), "iron")
        gt.cone("g_spike%d" % i, (x, -1.0, 1.9), (0, 0, -1), 0.6, 1.8, region="iron", segs=4)
    for z in (6, 14, 22):
        box("g_band%d" % z, (0, -1.0, z), (19, 1.1, 0.9), "iron")
    box("g_crest", (0, -D / 2 - 0.4, 40), (5, 0.8, 5), "bronze", bevel=0.3)
    box("g_crest_i", (0, -D / 2 - 0.85, 40), (2.2, 0.4, 2.2), "iron")


def watchtower():
    rnd = random.Random(13)
    round_courses("t", 11.0, 2.0, 46, rnd, taper=0.03, n=14)
    lathe("t_base", [(12.4, 0), (12.0, 2.4), (11.0, 2.6)], "stone_g", M=14)
    lathe("t_corbel", [(10.0, 45), (12.4, 47.5), (12.4, 49)], "stone_d", M=14)
    for i in range(7):   # merlons round the top, some chipped
        a = 2 * math.pi * i / 7
        hgt = 4.8 - (rnd.uniform(0.6, 1.8) if rnd.random() < 0.3 else 0)
        rbox("t_m%d" % i, (math.cos(a) * 11.2, math.sin(a) * 11.2, 49 + hgt / 2), (2.4, 4.4, hgt), stone_for(30, 40, rnd), yaw=a, bevel=0.2)
    # the slate roof, in layered rings, a lead cap and the pole
    for k in range(6):
        r0, r1 = 9.5 - k * 1.55, 9.5 - (k + 1) * 1.55
        z0 = 50 + k * 2.4
        lathe("t_roof%d" % k, [(r0 + 0.5, z0), (r1 + 0.3, z0 + 3.2)], "slate", M=14)
    gt.cone("t_cap", (0, 0, 64.2), (0, 0, 1), 0.9, 2.0, region="iron", segs=6)
    tube("t_pole", [(0, 0, 64, 0.35), (0, 0, 76, 0.32)], "wood_d")
    banner("t_banner", 4.0, 0, 75.2, 7.0, 6.0, rnd)
    for a in (0.6, 2.2, 3.8, 5.4):
        rbox("t_slit%.1f" % a, (math.cos(a) * 10.9, math.sin(a) * 10.9, 32), (1.0, 1.0, 5.0), "dark", yaw=a)
    rbox("t_door", (0, -10.6, 5.6), (1.0, 5.2, 9.0), "wood_d", yaw=-math.pi / 2)
    rbox("t_door_s", (0, -10.9, 8), (0.4, 5.4, 0.7), "iron", yaw=-math.pi / 2)


def arcane_spire():
    rnd = random.Random(14)
    round_courses("a", 8.0, 2.0, 44, rnd, taper=0.045, n=10, h=3.6)
    lathe("a_base", [(9.6, 0), (9.2, 2.2), (8.1, 2.4)], "stone_g", M=10)
    lathe("a_lip", [(5.8, 43.5), (7.6, 45.5), (7.6, 47)], "stone_d", M=10)
    for i in range(4):   # bronze claws reaching up to hold the crystal, weathered green
        a = math.pi / 4 + i * math.pi / 2
        tube("a_claw%d" % i, [(math.cos(a) * 6.6, math.sin(a) * 6.6, 46.5, 0.7), (math.cos(a) * 6.2, math.sin(a) * 6.2, 52, 0.6),
                               (math.cos(a) * 3.2, math.sin(a) * 3.2, 56.5, 0.45)], "bronze")
    gem("a_crystal", (0, 0, 59.5), 3.0, 6.5, "arcane")
    for z in (14, 26, 36):   # rune-cut slits that glow faintly
        for j in range(4):
            a = j * math.pi / 2 + (z % 7) * 0.2
            rr = 8.0 - 0.045 * (z - 2) + 0.05
            rbox("a_rune%d_%d" % (z, j), (math.cos(a) * rr, math.sin(a) * rr, z), (0.4, 0.8, 2.8), "arcane", yaw=a)
            GLOW.append(gt.PARTS[-1])
    rbox("a_door", (0, -7.7, 5), (1.0, 4.0, 8.0), "wood_d", yaw=-math.pi / 2)


def square_tower(prefix, S, Hh, rnd):
    box(prefix + "_core", (0, 0, Hh / 2), (S - 2.6, S - 2.6, Hh), "mortar")
    courses(prefix + "_f", -S / 2, S / 2, 2.2, Hh, -S / 2 + 1.4, -1, rnd)
    courses(prefix + "_b", -S / 2, S / 2, 2.2, Hh, S / 2 - 1.4, 1, rnd)
    side_courses(prefix + "_l", -S / 2 + 1.4, -S / 2 + 1.4, S / 2 - 1.4, 2.2, Hh, -1, rnd)
    side_courses(prefix + "_r", S / 2 - 1.4, -S / 2 + 1.4, S / 2 - 1.4, 2.2, Hh, 1, rnd)
    for sx in (-1, 1):   # quoins: big corner stones, alternating
        for sy in (-1, 1):
            z = 2.2
            k = 0
            while z < Hh - 1:
                hh = 4.0
                long_x = k % 2 == 0
                box("%s_q%d%d_%d" % (prefix, sx, sy, k), (sx * (S / 2 - (2.2 if long_x else 1.0)), sy * (S / 2 - (1.0 if long_x else 2.2)), z + hh / 2),
                    (4.6 if long_x else 2.2, 2.2 if long_x else 4.6, hh - 0.35), "stone_l" if k % 3 == 0 else "stone", bevel=0.2)
                z += hh; k += 1
    box(prefix + "_plinth", (0, 0, 1.1), (S + 1.2, S + 1.2, 2.2), "stone_g", bevel=0.3)


def forge_tower():
    rnd = random.Random(15)
    S, Hh = 20.0, 42.0
    square_tower("f", S, Hh, rnd)
    box("f_floor", (0, 0, Hh + 0.5), (S, S, 1.0), "stone_d")
    merlons("f_mf", -S / 2, S / 2, Hh + 1, -S / 2 + 1.4, 2.8, 3, rnd)
    merlons("f_mb", -S / 2, S / 2, Hh + 1, S / 2 - 1.4, 2.8, 3, rnd)
    # the chimney: stone below, soot-blackened above, an iron cowl
    box("f_chimney", (5, 4, Hh + 8), (6, 6, 15), "stone_d", bevel=0.3)
    box("f_chim_top", (5, 4, Hh + 16.5), (6.4, 6.4, 3), "soot", bevel=0.3)
    box("f_chim_cap", (5, 4, Hh + 18.4), (7.6, 7.6, 0.8), "iron")
    box("f_chim_glow", (5, 4, Hh + 18.1), (4, 4, 0.6), "ember", glow=True)
    # the forge window: a deep glowing opening with iron bars, soot above it
    box("f_window", (0, -S / 2 - 0.05, 24), (6, 0.8, 7), "ember", glow=True)
    box("f_soot", (0, -S / 2 - 0.1, 30), (7, 0.5, 5), "soot")
    for x in (-2.0, 0, 2.0):
        box("f_wbar%.1f" % x, (x, -S / 2 - 0.5, 24), (0.55, 0.55, 7.4), "iron")
    box("f_sill", (0, -S / 2 - 0.4, 20.2), (7.6, 1.6, 0.9), "stone_l")
    box("f_door", (0, -S / 2 - 0.1, 5.4), (5.4, 1.0, 9.6), "wood_d")
    for z in (2.5, 8.0):
        box("f_door_s%.0f" % z, (0, -S / 2 - 0.5, z), (5.6, 0.3, 0.6), "iron")


def ballista(z0=0.0, scale=1.0):
    s = len(gt.PARTS)
    rnd = random.Random(16)
    box("b_base", (0, 0, 1.8), (10, 12, 3.6), "wood_d", bevel=0.3)
    for y in (-4.5, 0, 4.5):
        box("b_plank%.1f" % y, (0, y, 3.75), (10.2, 3.6, 0.3), "wood")
    box("b_post", (0, 0, 6), (2.8, 2.8, 5), "wood")
    box("b_stock", (0, -2, 9.5), (2.4, 18, 2.0), "wood")
    box("b_rail", (0, -2, 10.65), (0.9, 18, 0.4), "iron")
    for sx in (-1, 1):   # the bow arms, bound with rope at the middle, iron-tipped
        tube("b_arm%d" % sx, [(0, -8, 10, 0.8), (sx * 6, -7, 10.3, 0.7), (sx * 11, -3.5, 10.5, 0.5)], "wood_d")
        box("b_tip%d" % sx, (sx * 11, -3.5, 10.5), (1.2, 1.2, 1.2), "iron")
        tube("b_bind%d" % sx, [(sx * 1.2, -8.1, 9.2, 0.35), (sx * 1.2, -8.1, 11.0, 0.35)], "rope")
    tube("b_string", [(-11, -3.5, 10.6, 0.14), (0, 4.5, 10.8, 0.14), (11, -3.5, 10.6, 0.14)], "rope", res=2)
    tube("b_bolt", [(0, 4.5, 11.2, 0.4), (0, -14, 11.2, 0.4)], "wood")
    gt.cone("b_head", (0, -14, 11.2), (0, -1, 0), 0.8, 2.6, region="iron", segs=4)
    for sx in (-1, 1):
        box("b_fl%d" % sx, (sx * 0.7, 3.8, 11.2), (1.2, 1.8, 0.15), "cloth")
    lathe("b_winch", [(1.4, 0), (1.4, 5)], "wood_d", M=8)
    for ob in gt.PARTS[-1:]:
        ob.data.transform(Matrix.Translation((-2.5, 6.5, 8)) @ Matrix.Rotation(math.pi / 2, 4, "Y"))
    if scale != 1.0 or z0:
        M = Matrix.Translation((0, 0, z0)) @ Matrix.Scale(scale, 4)
        for ob in gt.PARTS[s:]: ob.data.transform(M)


def siege_platform():
    rnd = random.Random(17)
    S, Hh = 26.0, 22.0
    square_tower("p", S, Hh, rnd)
    for k in range(7):   # the plank deck
        box("p_deck%d" % k, (0, -S / 2 + 1.9 + k * 3.7, Hh + 0.5), (S - 0.4, 3.4, 1.0), "wood" if k % 2 else "wood_d")
    for (x, y) in ((-S / 2 + 2, -S / 2 + 2), (S / 2 - 2, -S / 2 + 2), (-S / 2 + 2, S / 2 - 2), (S / 2 - 2, S / 2 - 2)):
        box("p_post%.0f%.0f" % (x, y), (x, y, Hh + 4), (3.4, 3.4, 6), stone_for(30, 40, rnd), bevel=0.25)
    ballista(Hh + 1.0, 1.0)


def bone_sentry():
    rnd = random.Random(18)
    lathe("s_robe", [(5.2, 0), (5.0, 3), (4.0, 14), (3.5, 18)], "hood", M=10)
    for i in range(5):   # the robe's ragged hem
        a = i * 2 * math.pi / 5 + 0.3
        rbox("s_hem%d" % i, (math.cos(a) * 4.9, math.sin(a) * 4.9, 0.8), (0.4, 3.0, rnd.uniform(1.0, 2.0)), "hood", yaw=a)
    lathe("s_chest", [(3.6, 17), (4.2, 22), (3.9, 27), (2.6, 29)], "iron", M=10)   # a dented iron breastplate
    box("s_belt", (0, 0, 19.2), (8.6, 8.6, 1.2), "wood_d")
    box("s_buckle", (0, -4.3, 19.2), (1.4, 0.4, 1.4), "bronze")
    for i in range(3):
        box("s_rib%d" % i, (0, -3.95, 21.2 + i * 1.8), (4.4, 0.5, 0.6), "bone")
    lathe("s_hood", [(3.2, 28), (3.7, 31), (3.3, 35), (1.4, 37.5)], "hood", M=10)
    box("s_face", (0, -2.6, 32), (3.0, 1.0, 3.4), "dark")
    box("s_skull", (0, -2.95, 31.8), (2.4, 0.5, 2.6), "bone")
    for sx in (-1, 1):
        box("s_eye%d" % sx, (sx * 0.7, -3.25, 32.3), (0.6, 0.3, 0.5), "ember", glow=True)
        box("s_pauldron%d" % sx, (sx * 4.6, 0, 26.5), (3.0, 4.0, 2.4), "iron", bevel=0.3)
        box("s_arm%d" % sx, (sx * 4.8, -0.5, 22), (1.4, 1.4, 6), "bone")
    # a battered tower shield (wood, iron rim and boss) and a halberd
    box("s_shield", (-6.5, -2.0, 18), (1.0, 7.5, 13), "wood_d", bevel=0.2)
    for z in (13, 18, 23):
        box("s_sh_band%d" % z, (-7.05, -2.0, z), (0.25, 7.6, 0.7), "iron")
    box("s_shield_boss", (-7.25, -2.0, 18.5), (0.6, 2.2, 2.2), "iron")
    tube("s_haft", [(6.4, -1.5, 1, 0.4), (6.4, -1.5, 40, 0.38)], "wood_d")
    gt.cone("s_point", (6.4, -1.5, 40), (0, 0, 1), 0.7, 3.6, region="iron", segs=4)
    box("s_blade", (8.0, -1.5, 36.5), (3.0, 0.35, 4.6), "iron")
    box("s_hook", (4.9, -1.5, 36.5), (1.4, 0.35, 2.0), "iron")


MODELS = {"wall": wall, "gatehouse": gatehouse, "watchtower": watchtower, "arcane_spire": arcane_spire, "forge_tower": forge_tower,
          "siege_platform": siege_platform, "ballista": ballista, "bone_sentry": bone_sentry}
NAMES = {"wall": "Rampart wall", "gatehouse": "Gatehouse", "watchtower": "Watchtower (arrows)", "arcane_spire": "Arcane spire (beams)",
         "forge_tower": "Forge tower (fire)", "siege_platform": "Siege platform (bolts)", "ballista": "Ballista (Workshop)", "bone_sentry": "Bone sentry (Crypt)"}
ORDER = list(MODELS)


def tris(ob): return sum(len(p.vertices) - 2 for p in ob.data.polygons)


def build(form, out_dir):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    gt.PARTS.clear(); GLOW.clear()
    MODELS[form]()
    image = bpy.data.images.load(os.path.join(out_dir, TEX + ".png"))
    def material(name, glow):
        m = bpy.data.materials.new(name); m.use_nodes = True
        nt = m.node_tree; bsdf = nt.nodes.get("Principled BSDF")
        img = nt.nodes.new("ShaderNodeTexImage"); img.image = image; img.interpolation = "Closest"
        bsdf.inputs["Roughness"].default_value = 0.85
        if glow:
            bsdf.inputs["Base Color"].default_value = (0, 0, 0, 1)
            nt.links.new(img.outputs["Color"], bsdf.inputs["Emission Color"]); bsdf.inputs["Emission Strength"].default_value = 1.6
        else: nt.links.new(img.outputs["Color"], bsdf.inputs["Base Color"])
        return m
    glow_m, plain_m = material(form + "_glow", True), material(form, False)
    for ob in gt.PARTS:
        if ob.type != "MESH":
            bpy.ops.object.select_all(action="DESELECT"); ob.select_set(True); bpy.context.view_layer.objects.active = ob
            bpy.ops.object.convert(target="MESH")
        ob.data.materials.clear(); ob.data.materials.append(glow_m if ob in GLOW else plain_m)
    bpy.ops.object.select_all(action="DESELECT")
    for ob in gt.PARTS: ob.select_set(True)
    bpy.context.view_layer.objects.active = gt.PARTS[0]
    bpy.ops.object.join()
    ob = bpy.context.view_layer.objects.active; ob.name = form
    print(form, "tris", tris(ob))
    return ob


def render_sheet(out_dir, only=None):
    forms = [f for f in ORDER if not only or f in only]
    CW, CH = 420, 480
    for form in forms:
        ob = build(form, out_dir)
        ob.rotation_euler = (0, 0, math.radians(-30))
        sc = bpy.context.scene
        sc.render.engine = "CYCLES"; sc.cycles.samples = 24; sc.cycles.device = "CPU"
        sc.view_settings.view_transform = "Standard"
        sc.render.resolution_x, sc.render.resolution_y = CW, CH
        world = bpy.data.worlds.new("w"); sc.world = world; world.use_nodes = True
        world.node_tree.nodes["Background"].inputs[0].default_value = (0.09, 0.09, 0.1, 1)
        for name, rot, energy in (("key", (math.radians(50), 0, math.radians(-35)), 4.0), ("fill", (math.radians(70), 0, math.radians(150)), 1.6),
                                  ("top", (math.radians(10), 0, 0), 1.2)):
            L = bpy.data.lights.new(name, "SUN"); L.energy = energy
            o = bpy.data.objects.new(name, L); o.rotation_euler = rot; sc.collection.objects.link(o)
        bpy.context.view_layer.update()
        ws = [ob.matrix_world @ v.co for v in ob.data.vertices]
        xs, zs = [w.x for w in ws], [w.z for w in ws]
        w, h = max(xs) - min(xs), max(zs) - min(zs)
        cam = bpy.data.cameras.new("cam"); cam.type = "ORTHO"; cam.ortho_scale = max(h, w * CH / CW) * 1.25
        co = bpy.data.objects.new("cam", cam); sc.collection.objects.link(co); sc.camera = co
        el = math.radians(18)
        co.location = ((min(xs) + max(xs)) / 2, -200 * math.cos(el), (min(zs) + max(zs)) / 2 + 200 * math.sin(el))
        co.rotation_euler = (math.radians(90) - el, 0, 0)
        sc.render.filepath = os.path.join(out_dir, "arch_%s.png" % form)
        bpy.ops.render.render(write_still=True)
    cols = 4
    rows = (len(forms) + cols - 1) // cols
    sheet = Image.new("RGB", (CW * cols, CH * rows), (10, 10, 14))
    d = ImageDraw.Draw(sheet)
    for i, form in enumerate(forms):
        x, y = (i % cols) * CW, (i // cols) * CH
        sheet.paste(Image.open(os.path.join(out_dir, "arch_%s.png" % form)).convert("RGB"), (x, y))
        d.text((x + 10, y + 8), NAMES[form], fill=(235, 228, 210))
    sheet.save(os.path.join(out_dir, "architect_sheet.png"))


# ------------------------------------------------------------------ into the game
GAME = ["wall", "gatehouse", "watchtower", "arcane_spire", "forge_tower", "siege_platform", "ballista", "bone_sentry"]


def to_game(out_dir):
    """Each piece into the mod as fortress_<name> (an item model the entity renderers draw; never placed as blocks)."""
    for form in GAME:
        bpy.ops.wm.read_factory_settings(use_empty=True)
        gt.PARTS.clear(); GLOW.clear()
        MODELS[form]()
        for ob in list(gt.PARTS):
            if ob.type != "MESH":
                bpy.ops.object.select_all(action="DESELECT"); ob.select_set(True); bpy.context.view_layer.objects.active = ob
                bpy.ops.object.convert(target="MESH")
        glow_m, plain_m = bpy.data.materials.new(form + "_glow"), bpy.data.materials.new(form)
        for ob in gt.PARTS: ob.data.materials.clear(); ob.data.materials.append(glow_m if ob in GLOW else plain_m)
        bpy.ops.object.select_all(action="DESELECT")
        for ob in gt.PARTS: ob.select_set(True)
        bpy.context.view_layer.objects.active = gt.PARTS[0]
        bpy.ops.object.join()
        ob = bpy.context.view_layer.objects.active
        name = "fortress_" + form
        ob.name = name
        ob.location = (0, 0, 0)   # the entity renderers draw it round the entity's feet: middle at the origin, standing on it
        bpy.ops.wm.obj_export(filepath=os.path.join(out_dir, name + ".obj"), export_selected_objects=True, export_uv=True, export_normals=True,
                              export_materials=True, export_triangulated_mesh=True, forward_axis="NEGATIVE_Z", up_axis="Y", global_scale=1 / 16, path_mode="STRIP")
        print(form, "tris", tris(ob))
        disp = {"gui": {"rotation": [20, 225, 0], "translation": [0, -4, 0], "scale": [0.22] * 3},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.25] * 3},
                "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.25] * 3}}
        gt.export_to_game(name, out_dir, disp, glowing={form + "_glow"}, textures={form: TEX, form + "_glow": TEX}, render_type="minecraft:cutout")


if __name__ == "__main__":
    mode, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    paint(os.path.join(out, TEX + ".png"))
    if mode == "render": render_sheet(out, sys.argv[3].split(",") if len(sys.argv) > 3 else None)
    if mode == "game": LOW = True; to_game(out)
