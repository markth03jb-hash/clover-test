"""The Hunger Blade, modelled in Blender (run with Blender's Python: bpy): a living sword that grows as it feeds.
Original designs: every stage keeps the same living parts - a sinew-bound flesh grip, a jaw for a guard with an eye
set in it, teeth - while the blade itself grows and, at its fourth stage, evolves down one of four paths.

  Base:     fang (a curved tooth of a dagger), gnaw (a serrated short sword), maw (a long sword, teeth along its back)
  Paths:    bone, crystal, shadow, flame - each as evo (stage 4), great (stage 5, a greatsword), ascended (stage 6, a cleaver)

Blender space (pixels; exported at 1/16): Z up the blade, X across it, Y through it. Origin at the middle of the grip.

  python tools/blender/hunger_blade.py render OUT_DIR [names]   a sheet of every stage
  python tools/blender/hunger_blade.py game OUT_DIR             writes them into the mod (models/item, textures/item)
"""
import math, os, random, sys
import bpy, bmesh
from mathutils import Vector, Matrix
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gilded_thorn as gt

TEX = "hunger_blade"
gt.REG.clear()
gt.REG.update({"flesh": (0, 0, 64, 64), "sinew": (64, 0, 128, 64), "bone": (128, 0, 192, 64), "tooth": (192, 0, 256, 64),
               "eye_r": (0, 64, 32, 96), "eye_b": (32, 64, 64, 96), "eye_o": (64, 64, 96, 96), "eye_p": (96, 64, 128, 96),
               "vein": (128, 64, 160, 96), "blue": (160, 64, 192, 96), "ember": (192, 64, 224, 96), "crys2": (224, 64, 256, 80),
               "b_maw": (0, 96, 256, 128), "b_bone": (0, 128, 256, 160), "b_crys": (0, 160, 256, 192), "b_shadow": (0, 192, 256, 224),
               "b_flame": (0, 224, 192, 256), "black": (192, 224, 224, 256), "char": (224, 224, 256, 256),
               "gem": (0, 64, 32, 96), "pale": (224, 80, 256, 96)})   # gilded_thorn.gemstone wants one (refitted to the right eye after)
REG = gt.REG
GLOW = []


# ------------------------------------------------------------------ the texture
def mix(a, b, t): t = max(0.0, min(1.0, t)); return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def paint(out):
    rnd = random.Random(5)
    im = Image.new("RGBA", (256, 256), (0, 0, 0, 255)); px = im.load()
    def region(name, fn):
        x0, y0, x1, y1 = REG[name]
        for x in range(x0, x1):
            for y in range(y0, y1):
                px[x, y] = fn((x - x0) / (x1 - x0 - 1), (y - y0) / (y1 - y0 - 1), x - x0, y - y0) + (255,)
    MUS, MUS_D = (120, 18, 28), (48, 6, 12)
    def flesh(u, v, x, y):
        f = 0.5 + 0.5 * math.sin(x * 0.9 + math.sin(y * 0.2) * 2.0)
        return mix(mix(MUS_D, MUS, f ** 1.4), (20, 2, 6), 0.3 * (0.5 + 0.5 * math.sin(y * 0.15)))
    region("flesh", flesh)
    region("sinew", lambda u, v, x, y: mix((70, 40, 36), (190, 140, 120), 0.5 + 0.5 * math.sin(y * 1.2 + x * 0.1)))
    region("bone", lambda u, v, x, y: mix((150, 138, 118), (222, 212, 190), 0.5 + 0.5 * math.sin(x * 0.3 + y * 0.7)) if (x * 7 + y * 3) % 29 else (110, 40, 40))
    region("tooth", lambda u, v, x, y: mix((235, 226, 205), (150, 20, 26), max(0.0, (u - 0.6) / 0.4) ** 1.4))
    def eye(c_in, c_out):
        def f(u, v, x, y):
            d = math.hypot(u - 0.5, v - 0.5) * 2
            if abs(u - 0.5) < 0.08 and d < 0.8: return (10, 4, 6)   # a slit pupil
            return mix(c_in, c_out, min(1, d * 1.2))
        return f
    region("eye_r", eye((255, 230, 140), (210, 20, 24)))
    region("eye_b", eye((210, 240, 255), (30, 90, 255)))
    region("eye_o", eye((255, 250, 180), (255, 110, 20)))
    region("eye_p", eye((255, 220, 240), (230, 50, 120)))
    region("vein", lambda u, v, x, y: mix((255, 130, 110), (220, 20, 34), abs(v - 0.5) * 2))
    region("blue", lambda u, v, x, y: mix((170, 220, 255), (20, 70, 230), abs(v - 0.5) * 2))
    region("ember", lambda u, v, x, y: mix((255, 240, 150), (255, 90, 10), abs(v - 0.5) * 2))
    region("pale", lambda u, v, x, y: mix((255, 240, 248), (255, 140, 190), abs(v - 0.5) * 2))
    region("crys2", lambda u, v, x, y: mix((255, 170, 200), (140, 10, 50), 0.5 + 0.5 * math.sin(x * 0.8 - y * 0.5)))
    def edge(v, w=0.14): return max(0.0, (abs(v - 0.5) * 2 - (1 - w)) / w)   # 0 in the middle, 1 at either edge
    def maw(u, v, x, y):   # dark red-black living steel, mottled, a red honed edge, faint veins
        c = mix((40, 14, 20), (100, 26, 36), 0.5 + 0.5 * math.sin(x * 0.05 + y * 0.9) * math.sin(x * 0.019 + 1))
        if abs(v - 0.5 - 0.12 * math.sin(u * 17)) < 0.035: c = (150, 14, 28)
        return mix(c, (240, 60, 60), edge(v))
    region("b_maw", maw)
    def bone_b(u, v, x, y):   # polished ivory, softly shaded, darker toward the back, a pale honed edge
        c = mix((190, 178, 154), (236, 228, 208), 0.5 + 0.5 * math.sin(x * 0.045 + y * 0.35) * math.sin(x * 0.017 + 2))
        c = mix(c, (128, 112, 92), max(0.0, 0.55 - v) * 0.8)
        return mix(c, (252, 248, 238), edge(v, 0.1))
    region("b_bone", bone_b)
    def crys(u, v, x, y):   # faceted crimson crystal, banded, bright at the edges
        k = int(x / 21 + (v * 1.7 if v < 0.5 else (1 - v) * 1.7)) % 3   # flat facets, three shades
        c = [(120, 8, 44), (200, 36, 86), (250, 110, 150)][k]
        if v < 0.5 and abs(v - 0.5) < 0.04: c = (255, 190, 210)   # the ridge
        return mix(c, (255, 215, 230), edge(v, 0.16))
    region("b_crys", crys)
    def shadow(u, v, x, y):   # black smoke drifting along it, a deep blue haze toward the edges, a blue edge
        sm = 0.5 + 0.5 * math.sin(x * 0.09 + 2.5 * math.sin(y * 0.4 + x * 0.03)) * math.sin(x * 0.037 - y * 0.2)
        c = mix((4, 4, 9), (20, 24, 52), sm ** 2)
        c = mix(c, (16, 40, 120), max(0.0, abs(v - 0.5) * 2 - 0.55) * 1.4)
        return mix(c, (90, 170, 255), edge(v, 0.14))
    region("b_shadow", shadow)
    cracks = [(rnd.uniform(0, 192), rnd.uniform(0, 32)) for _ in range(46)]
    def flame(u, v, x, y):   # charred black, a web of ember cracks, glowing orange edges
        d = sorted(math.hypot(x - sx, (y - sy) * 2) for sx, sy in cracks)
        c = mix((30, 18, 14), (54, 30, 22), 0.5 + 0.5 * math.sin(x * 0.2 + y))
        if d[1] - d[0] < 0.9: c = (255, 120, 30)
        return mix(c, (255, 200, 80), edge(v, 0.2))
    region("b_flame", flame)
    region("black", lambda u, v, x, y: mix((6, 6, 10), (24, 26, 44), 0.5 + 0.5 * math.sin(x * 0.7 + y * 0.4)))
    region("char", lambda u, v, x, y: mix((20, 12, 10), (60, 34, 24), 0.5 + 0.5 * math.sin(x * 0.9 + y * 0.3)))
    im.save(out)


# ------------------------------------------------------------------ shared pieces
def tube(name, pts, region, flat=1.0, res=4, bev=2, glow=False):
    pts = [(x, y / flat, z, r) for x, y, z, r in pts]   # (curve_obj squashes the whole thing through Y: keep where it sits)
    o = gt.curve_obj(name, pts, flat=flat, region=region, res=res, bev=bev)
    if glow: GLOW.append(o)
    return o


def taper(t, start, power=0.5):
    """1 up to `start`, then curving in to 0 at the tip."""
    if t <= start: return 1.0
    u = (t - start) / (1 - start)
    return max(0.0, 1 - u ** 1.6) ** power


def sharp(t, start):
    """1 up to `start`, then running nearly straight in to a point."""
    if t <= start: return 1.0
    return max(0.0, 1 - ((t - start) / (1 - start)) ** 1.25)


def frac(x): return x - math.floor(x)


def blade(name, z0, length, xl, xr, th, region, glow=False, n=48, tip=None, center=None):
    """The blade: a lozenge section (edge, bevel, ridge, bevel, edge) swept up from z0; xl(t), xr(t) its edges (x),
    th(t) half its thickness at the ridge, t from 0 at the guard to 1 at the tip; tip: (x, height past the end)."""
    bm = bmesh.new()
    rings = []
    for i in range(n + 1):
        t = i / n
        z = z0 + length * t
        l, r, h = xl(t), xr(t), th(t)
        c = center(t) if center else (l + r) / 2
        el, er = min(0.6, (c - l) * 0.35), min(0.6, (r - c) * 0.35)
        sec = [(l, 0), (l + el, h * 0.45), (c, h), (r - er, h * 0.45), (r, 0), (r - er, -h * 0.45), (c, -h), (l + el, -h * 0.45)]
        rings.append([bm.verts.new((x, y, z)) for x, y in sec])
    k = len(rings[0])
    for i in range(n):
        for j in range(k):
            bm.faces.new((rings[i][j], rings[i][(j + 1) % k], rings[i + 1][(j + 1) % k], rings[i + 1][j]))
    if tip:
        tv = bm.verts.new((tip[0], 0, z0 + length + tip[1]))
        for j in range(k): bm.faces.new((rings[n][j], rings[n][(j + 1) % k], tv))
    else: bm.faces.new(rings[n])
    bm.faces.new(list(reversed(rings[0])))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    x0, y0, x1, y1 = REG[region]
    lo = min(xl(i / 20) for i in range(21)) - 0.01; hi = max(xr(i / 20) for i in range(21)) + 0.01
    def uv(co):   # along the blade -> across the region, across the blade -> down it (both edges at its rims)
        t = (co[2] - z0) / (length + (tip[1] if tip else 0))
        f = (co[0] - lo) / (hi - lo)
        return (x0 + 1 + min(1, max(0, t)) * (x1 - x0 - 2), y0 + 1 + min(1, max(0, f)) * (y1 - y0 - 2))
    ob = gt.new_obj(name, bm, uv_fn=uv, smooth=False)
    if glow: GLOW.append(ob)
    return ob


def grip(length, r, pommel="fang"):
    """A flesh grip bound in a spiral of pale sinew, from -length/2 to +length/2; a pommel below it."""
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=8, radius1=r * 0.95, radius2=r, depth=length)
    gt.new_obj("grip", bm, REG["flesh"])
    pts, turns, n = [], length / 1.3, int(length * 8)
    for i in range(n + 1):
        a = 2 * math.pi * turns * i / n
        pts.append((r * math.cos(a), r * math.sin(a), -length / 2 + length * i / n, r * 0.32))
    cu = bpy.data.curves.new("sinew", "CURVE"); cu.dimensions = "3D"; cu.resolution_u = 1
    cu.bevel_depth = 1.0; cu.bevel_resolution = 0; cu.use_fill_caps = True
    sp = cu.splines.new("POLY"); sp.points.add(len(pts) - 1)
    for p, (x, y, z, rr) in zip(sp.points, pts): p.co = (x, y, z, 1); p.radius = rr
    ob = bpy.data.objects.new("sinew_c", cu); bpy.context.collection.objects.link(ob)
    me = bpy.data.meshes.new_from_object(ob.evaluated_get(bpy.context.evaluated_depsgraph_get())); bpy.data.objects.remove(ob)
    bm = bmesh.new(); bm.from_mesh(me)
    gt.new_obj("sinew", bm, REG["sinew"])
    zb = -length / 2
    gt.box("pom_knot", (0, 0, zb - 0.35 * r), (2.4 * r, 2.2 * r, 0.8 * r), "flesh", bevel=0.2 * r, segs=1)
    if pommel == "fang":   # a single fang hanging off the end
        gt.cone("pom_fang", (0, 0, zb - 0.6 * r), (0.15, 0, -1), 0.8 * r, 2.4 * r, "tooth", segs=6)
    else:                  # a little lidded eye
        o = gt.gemstone("pom_eye", (0, 0, zb - 1.1 * r), 0.9 * r, 0.8); gt.fit_uv(o, REG[pommel]); GLOW.append(o)
        gt.torus("pom_lid", (0, 0, zb - 1.1 * r), (0, 0, 1), 0.85 * r, 0.25 * r, "flesh")


def jaw(z, s, eye, body="flesh", teeth="tooth", w=1.0, fangs=True):
    """The guard: a jaw clamped round the blade's root - a curved lower jaw across it, its ends curling up, teeth up
    both faces, two long fangs biting onto the blade - and a lidded eye on each face. s: its size, w: its width."""
    W = 3.0 * s * w
    tube("jaw", [(-W, 0, z + 1.4 * s, 0.35 * s), (-W * 0.75, 0, z + 0.3 * s, 0.6 * s), (0, 0, z - 0.2 * s, 0.75 * s),
                 (W * 0.75, 0, z + 0.3 * s, 0.6 * s), (W, 0, z + 1.4 * s, 0.35 * s)], body, flat=1.5)
    gt.box("jaw_root", (0, 0, z - 0.6 * s), (1.8 * s, 1.6 * s, 0.9 * s), body, bevel=0.2 * s, segs=1)
    for side in (1, -1):
        y = side * 0.75 * s
        for i, f in enumerate((-0.85, -0.6, -0.36, 0.36, 0.6, 0.85)):
            x = W * f
            zz = z + 0.3 * s + 0.9 * s * (abs(f) - 0.3) ** 2
            gt.cone("tooth%d%d" % (side, i), (x, y, zz), (x * 0.08, side * 0.15, 1), 0.28 * s, (0.9 + 0.3 * (i % 2)) * s, teeth, segs=4)
        if fangs:   # two long fangs reaching up onto the blade
            for fx in (-1, 1):
                gt.cone("fang%d%d" % (side, fx), (fx * 0.9 * s, side * 0.55 * s, z + 0.2 * s), (-fx * 0.25, side * 0.05, 1), 0.33 * s, 2.2 * s, teeth, segs=5)
        o = gt.gemstone("eye%d" % side, (0, side * 0.95 * s, z - 0.55 * s), 0.72 * s, 0.45); gt.fit_uv(o, REG[eye]); GLOW.append(o)
        gt.torus("lid%d" % side, (0, side * 1.0 * s, z - 0.55 * s), (0, 1, 0), 0.78 * s, 0.2 * s, body)


def back_teeth(z0, length, xl, ts, s, region="tooth"):
    """Teeth growing off the blade's back (its left edge), leaning toward the tip."""
    for i, t in enumerate(ts):
        x = xl(t)
        gt.cone("bt%d" % i, (x + 0.25 * s, 0, z0 + length * t), (-1, 0, 0.55), 0.38 * s, (1.1 + 0.4 * (i % 2)) * s, region, segs=4)


def vein(z0, length, mid, th, t0=0.04, t1=0.8, r=0.16):
    """A glowing vein running up the blade's ridge on both faces, wandering a little."""
    for side in (1, -1):
        pts = []
        for i in range(9):
            t = t0 + (t1 - t0) * i / 8
            pts.append((mid(t) + 0.25 * math.sin(t * 19), side * th(t) * 0.92, z0 + length * t, r * (1 - 0.6 * i / 8)))
        tube("vein%d" % side, pts, "vein", flat=0.5, glow=True)


def edge_line(z0, length, xr, mid, th, region, r=0.15, t0=0.03, t1=0.93):
    """A glowing line just in from the cutting edge (the right one), on both faces, following its every turn."""
    for side in (1, -1):
        pts = []
        for i in range(31):
            t = t0 + (t1 - t0) * i / 30
            x_r, c, h = xr(t), mid(t), th(t)
            er = min(0.6, (x_r - c) * 0.35)
            pts.append((x_r - er * 0.65, side * (h * 0.3 + 0.06), z0 + length * t, r * (1 - 0.5 * i / 30)))
        tube("edge%d" % side, pts, region, flat=0.45, res=2, bev=1, glow=True)


def ridge(z0, length, mid, th, region, r=0.14, dashed=False):
    """A glowing line straight up the blade's ridge on both faces (dashed: broken into rune-like strokes)."""
    for side in (1, -1):
        if not dashed:
            pts = [(mid(t), side * (th(t) + 0.04), z0 + length * t, r * (1 - 0.5 * t)) for t in (0.04 + 0.86 * i / 10 for i in range(11))]
            tube("ridge%d" % side, pts, region, flat=0.45, res=2, bev=1, glow=True)
        else:
            for k in range(7):
                ta, tb = 0.06 + 0.12 * k, 0.06 + 0.12 * k + 0.07
                tc = (ta + tb) / 2
                pts = [(mid(ta) - 0.25, side * (th(ta) + 0.04), z0 + length * ta, r), (mid(tc) + 0.25 * (-1) ** k, side * (th(tc) + 0.04), z0 + length * tc, r),
                       (mid(tb) - 0.25, side * (th(tb) + 0.04), z0 + length * tb, r * 0.5)]
                tube("rune%d%d" % (side, k), pts, region, flat=0.45, res=2, bev=1, glow=True)


# ------------------------------------------------------------------ the three base stages
def fang():
    """A curved tooth of a dagger, a small jaw, a fang pommel."""
    g = 4.0; z0 = g / 2 + 0.9
    L = 9.0
    bend = lambda t: 1.6 * t ** 2
    xl = lambda t: bend(t) - 1.5 * taper(t, 0.25, 0.7)
    xr = lambda t: bend(t) + 1.3 * taper(t, 0.15, 0.7)
    th = lambda t: 0.55 * (1 - t) ** 0.4 + 0.05
    blade("blade", z0, L, xl, xr, th, "b_maw", tip=(1.7, 0.6))
    vein(z0, L, lambda t: bend(t) - 0.1, th, t1=0.6, r=0.13)
    jaw(z0 - 0.4, 0.62, "eye_r", fangs=False)
    grip(g, 0.85)
    return z0 + L + 0.6 + g / 2 + 1.5


def gnaw():
    """A short sword, its back edge cut into a saw of gnawing teeth."""
    g = 5.0; z0 = g / 2 + 1.1
    L = 15.0
    saw = lambda t: 0.9 * frac(t * 8) ** 1.5 if 0.06 < t < 0.75 else 0.0
    xl = lambda t: (-2.0 + saw(t)) * taper(t, 0.72, 0.6) - 0.05
    xr = lambda t: 1.9 * taper(t, 0.6, 0.6) + 0.05
    th = lambda t: 0.55 * (1 - t ** 2) ** 0.4 + 0.06
    blade("blade", z0, L, xl, xr, th, "b_maw", tip=(0.4, 0.9), center=lambda t: 0.0)
    vein(z0, L, lambda t: 0.0, th, t1=0.7)
    jaw(z0 - 0.4, 0.8, "eye_r")
    grip(g, 0.9)
    return z0 + L + 0.9 + g / 2 + 1.8


def maw():
    """A long sword: a jaw of teeth along its back, a broad edge sweeping to a hooked point, a vein of red down it."""
    g = 6.0; z0 = g / 2 + 1.3
    L = 22.0
    xl = lambda t: -2.3 * taper(t, 0.78, 0.6)
    xr = lambda t: (2.3 + 0.5 * math.sin(t * math.pi * 0.9)) * taper(t, 0.7, 0.55)
    th = lambda t: 0.6 * (1 - t ** 2) ** 0.4 + 0.07
    blade("blade", z0, L, xl, xr, th, "b_maw", tip=(-0.3, 1.2), center=lambda t: 0.0)
    vein(z0, L, lambda t: 0.0, th)
    back_teeth(z0, L, xl, [0.1 + 0.085 * i for i in range(8)], 1.25)
    jaw(z0 - 0.5, 1.0, "eye_r")
    grip(g, 1.0, pommel="eye_r")
    return z0 + L + 1.2 + g / 2 + 2.5


# ------------------------------------------------------------------ the four paths
SIZES = {   # stage: (grip, blade length, width, jaw size)
    "evo": (6.5, 24.0, 2.7, 1.1), "great": (8.0, 29.0, 3.5, 1.35), "ascended": (9.0, 32.0, 4.6, 1.6)}


def path_blade(path, stage):
    g, L, w, s = SIZES[stage]
    z0 = g / 2 + 1.5 * s
    eye = {"bone": "eye_r", "crystal": "eye_p", "shadow": "eye_b", "flame": "eye_o"}[path]
    body = {"bone": "flesh", "crystal": "flesh", "shadow": "black", "flame": "char"}[path]
    cleaver = stage == "ascended"
    region = {"bone": "b_bone", "crystal": "b_crys", "shadow": "b_shadow", "flame": "b_flame"}[path]
    glow = path != "bone"
    th = lambda t: (0.65 + 0.18 * (s - 1)) * (1 - t ** 2) ** 0.35 + 0.08

    if path == "bone":   # a jawbone: straight back, its edge cut into blunt teeth, a clipped point
        teeth = lambda t: 0.55 * s * frac(t * 6) ** 2 if 0.08 < t < 0.7 else 0.0
        if cleaver:   # a heavy slab, flaring toward its squared end, a hook off the back of it
            xl = lambda t: -w * 0.8 * sharp(t, 0.96)
            xr = lambda t: (w * (0.65 + 0.6 * t) - teeth(t)) * sharp(t, 0.9)
            tip = (-w * 0.55, 0.9)
        else:
            xl = lambda t: -w * 0.85 * sharp(t, 0.86)
            xr = lambda t: (w * (1 + 0.12 * math.sin(math.pi * t)) - teeth(t)) * sharp(t, 0.72)
            tip = (-w * 0.45, 1.4 * s)
    elif path == "crystal":   # stacked shards, each wide at its root and narrowing, to a long point
        shard = lambda t: 1 - 0.3 * frac(t * 4)
        if cleaver:
            xl = lambda t: -w * 0.8 * shard(t) * sharp(t, 0.86)
            xr = lambda t: w * (0.75 + 0.45 * t) * shard(t) * sharp(t, 0.8)
            tip = (w * 0.5, 2.6)
        else:
            xl = lambda t: -w * shard(t) * sharp(t, 0.72)
            xr = lambda t: w * shard(t) * sharp(t, 0.72)
            tip = (0, 2.6 * s)
    elif path == "shadow":   # a long curve, swept back, a tattered back edge
        bend = lambda t: -1.4 * s * t ** 2
        rag = lambda t: 0.5 * s * frac(t * 7) ** 3 if t > 0.25 else 0
        if cleaver:
            xl = lambda t: bend(t) - w * 0.8 * sharp(t, 0.86) + rag(t)
            xr = lambda t: bend(t) + w * (0.7 + 0.45 * math.sin(t * math.pi * 0.8)) * sharp(t, 0.72)
        else:
            xl = lambda t: bend(t) - w * 0.8 * sharp(t, 0.82) + rag(t)
            xr = lambda t: bend(t) + w * 0.85 * sharp(t, 0.62)
        tip = (bend(1) - 0.3, 1.8 * s)
    else:   # flame: both edges waving together like a flame, narrowing upward
        wave = lambda t: 0.28 * s * math.sin(t * math.pi * (9 if not cleaver else 8))
        if cleaver:
            xl = lambda t: (-w * 0.8 + wave(t)) * sharp(t, 0.86)
            xr = lambda t: (w * (0.7 + 0.4 * t) + wave(t)) * sharp(t, 0.8)
            tip = (w * 0.45, 2.2)
        else:
            xl = lambda t: (-w * 0.85 * (1 - 0.15 * t) + wave(t)) * sharp(t, 0.72)
            xr = lambda t: (w * 0.85 * (1 - 0.15 * t) + wave(t)) * sharp(t, 0.72)
            tip = (0, 2.0 * s)

    mid = lambda t: (xl(t) + xr(t)) / 2
    blade("blade", z0, L, xl, xr, th, region, glow=glow, n=56, tip=tip)
    jaw(z0 - 0.55 * s, s, eye, body=body, w=1.0 + 0.15 * (w - 2.7))
    grip(g, 0.95 + 0.12 * s, pommel=eye if stage != "evo" else "fang")

    line = {"bone": "vein", "crystal": "pale", "shadow": "blue", "flame": "ember"}[path]
    edge_line(z0, L, xr, mid, th, line, r=0.12 + 0.03 * s)   # a glowing line along the cutting edge, both faces
    W = 3.0 * s
    if path == "bone":   # ribs curling down off the guard, two bone horns rising by the blade's root, a marrow vein
        for side in (1, -1):
            for k, b in enumerate((0.0, -1.2)):
                Wk = W * (1.1 + 0.1 * k)
                tube("rib%d%d" % (side, k), [(side * Wk * 0.6, 0, z0 - 0.5 * s + b, 0.35 * s), (side * Wk * 1.05, 0, z0 - 1.2 * s + b, 0.3 * s),
                                             (side * Wk * 1.15, 0, z0 - 2.4 * s + b, 0.2 * s), (side * Wk * 0.95, 0, z0 - 3.2 * s + b, 0.08 * s)], "bone", flat=0.9)
            tube("horn%d" % side, [(side * W * 0.8, 0, z0 + 0.2 * s, 0.45 * s), (side * W * 1.1, 0, z0 + 1.6 * s, 0.32 * s),
                                   (side * W * 0.95, 0, z0 + 3.2 * s, 0.16 * s), (side * W * 0.65, 0, z0 + 4.0 * s, 0.03)], "bone", flat=0.9)
        vein(z0, L, mid, th, t1=0.85, r=0.2)
        if cleaver: gt.cone("spur", (xl(0.97), 0, z0 + L * 0.97), (-1, 0, -0.4), 0.6, 2.4, "bone", segs=5)
    elif path == "crystal":   # a crown of crystals fanning up from the guard, a bright ridge up the middle
        for side in (1, -1):
            for k in range(4):
                o = gt.cone("gc%d%d" % (side, k), (side * (1.9 + 0.55 * k) * s, 0, z0 - 0.3 * s), (side * (0.15 + 0.32 * k), 0, 1),
                            (0.5 - 0.06 * k) * s, (3.0 - 0.55 * k) * s, "crys2", segs=4)
                GLOW.append(o)
        ridge(z0, L, mid, th, "pale", r=0.14)
    elif path == "shadow":   # blue wisps curling off the guard like horns, a broken line of blue runes up the middle
        for side in (1, -1):
            tube("gw%d" % side, [(side * W, 0, z0 + 0.4 * s, 0.32 * s), (side * W * 1.45, 0, z0 - 1.0 * s, 0.24 * s), (side * W * 1.35, 0, z0 - 3.2 * s, 0.05)], "blue", flat=0.35, glow=True)
            tube("gh%d" % side, [(side * W * 0.8, 0, z0 + 0.3 * s, 0.3 * s), (side * W * 1.2, 0, z0 + 2.0 * s, 0.2 * s), (side * W * 0.9, 0, z0 + 3.8 * s, 0.04)], "blue", flat=0.35, glow=True)
        ridge(z0, L, mid, th, "blue", r=0.13, dashed=True)
    else:   # flame horns off the guard, a molten vein
        for side in (1, -1):
            tube("gf%d" % side, [(side * W * 0.9, 0, z0 + 0.6 * s, 0.42 * s), (side * W * 1.3, 0, z0 + 2.0 * s, 0.27 * s), (side * W * 1.05, 0, z0 + 3.6 * s, 0.05)], "ember", flat=0.45, glow=True)
            tube("gf2%d" % side, [(side * W * 1.0, 0, z0 - 0.2 * s, 0.32 * s), (side * W * 1.55, 0, z0 + 0.6 * s, 0.2 * s), (side * W * 1.6, 0, z0 + 2.0 * s, 0.04)], "ember", flat=0.45, glow=True)
        vein(z0, L, mid, th, t1=0.85, r=0.18)
    if cleaver:   # fully awake: a great lidded eye opens on the blade itself
        t = 0.13
        for side in (1, -1):
            c = (mid(t), side * (th(t) + 0.1), z0 + L * t)
            o = gt.gemstone("beye%d" % side, c, 1.0 * s, 0.35); gt.fit_uv(o, REG[eye]); GLOW.append(o)
            gt.torus("blid%d" % side, (c[0], side * (th(t) + 0.15), c[2]), (0, 1, 0), 1.05 * s, 0.24 * s, body)
    return z0 + L + tip[1] + g / 2 + 3 * s



# ------------------------------------------------------------------ the branches (stage 5 and their ascended forms at 6)
def sweep(name, P, wl, wr, th, region, glow=False, n=40, tiplen=1.0):
    """A blade swept along a path in the blade's plane: P(t) -> (x, z) its spine, wl/wr(t) its width to the left and
    right of the path (left = turning anticlockwise from the way it runs), th(t) half its thickness. Ends in a point
    tiplen past P(1) (0: cut square). It can curl past level - a scythe, a crescent, a wing's feather."""
    bm = bmesh.new(); uvl = bm.loops.layers.uv.new("UVMap")
    x0, y0, x1, y1 = REG[region]
    def frame(t):
        e = 1e-3
        a, b = P(max(0, t - e)), P(min(1, t + e))
        d = Vector((b[0] - a[0], b[1] - a[1])).normalized()
        return Vector(P(t)), d, Vector((-d.y, d.x))
    rings, uvs = [], {}
    for i in range(n + 1):
        t = i / n
        p, d, nr = frame(t)
        l, r, h = wl(t), wr(t), th(t)
        el, er = min(0.6, l * 0.35), min(0.6, r * 0.35)
        sec = [(l, 0), (l - el, h * 0.45), (0, h), (-(r - er), h * 0.45), (-r, 0), (-(r - er), -h * 0.45), (0, -h), (l - el, -h * 0.45)]
        ring = []
        for o, y in sec:
            v = bm.verts.new((p.x + nr.x * o, y, p.y + nr.y * o)); ring.append(v)
            uvs[v] = (x0 + 1 + t * (x1 - x0 - 2), y0 + 1 + (l - o) / max(1e-4, l + r) * (y1 - y0 - 2))
        rings.append(ring)
    k = 8
    faces = []
    for i in range(n):
        for j in range(k): faces.append(bm.faces.new((rings[i][j], rings[i][(j + 1) % k], rings[i + 1][(j + 1) % k], rings[i + 1][j])))
    p, d, nr = frame(1.0)
    if tiplen > 0:
        tv = bm.verts.new((p.x + d.x * tiplen, 0, p.y + d.y * tiplen)); uvs[tv] = (x1 - 1, (y0 + y1) / 2)
        for j in range(k): faces.append(bm.faces.new((rings[n][j], rings[n][(j + 1) % k], tv)))
    else: faces.append(bm.faces.new(rings[n]))
    faces.append(bm.faces.new(list(reversed(rings[0]))))
    for f in faces:
        for lp in f.loops: lp[uvl].uv = gt.uvp(*uvs[lp.vert])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    ob = gt.new_obj(name, bm, smooth=False)
    if glow: GLOW.append(ob)
    return ob


def sweep_edge(name, P, w, th, region, side=-1, r=0.15, t0=0.04, t1=0.94):
    """A glowing line just in from one edge of a swept blade (side -1: its right edge, w = wr; +1: its left, w = wl)."""
    for f in (1, -1):
        pts = []
        for i in range(25):
            t = t0 + (t1 - t0) * i / 24
            e = 1e-3; a, b = P(max(0, t - e)), P(min(1, t + e))
            d = Vector((b[0] - a[0], b[1] - a[1])).normalized(); nr = Vector((-d.y, d.x))
            ww = w(t); o = side * (ww - min(0.6, ww * 0.35) * 0.65)
            x, z = P(t)
            pts.append((x + nr.x * o, f * (th(t) * 0.3 + 0.06), z + nr.y * o, r * (1 - 0.5 * i / 24)))
        tube("%s%d" % (name, f), pts, region, flat=0.45, res=2, bev=1, glow=True)


def hilt(stage, path, eye_pommel=True, guard_w=1.0):
    """The living hilt every sword keeps: the sinew grip and the eyed jaw. Returns (z0 where the blade starts, size, eye, body)."""
    g, s = {5: (8.0, 1.35), 6: (9.0, 1.6)}[stage]
    eye = {"bone": "eye_r", "crystal": "eye_p", "shadow": "eye_b", "flame": "eye_o"}[path]
    body = {"bone": "flesh", "crystal": "flesh", "shadow": "black", "flame": "char"}[path]
    z0 = g / 2 + 1.5 * s
    jaw(z0 - 0.55 * s, s, eye, body=body, w=guard_w)
    grip(g, 0.95 + 0.12 * s, pommel=eye if eye_pommel else "fang")
    return z0, s, eye, body, g


def blade_eye(c, s, eye, body, th):
    for side in (1, -1):
        o = gt.gemstone("beye%d" % side, (c[0], side * (th + 0.1), c[1]), 0.9 * s, 0.35); gt.fit_uv(o, REG[eye]); GLOW.append(o)
        gt.torus("blid%d" % side, (c[0], side * (th + 0.15), c[1]), (0, 1, 0), 0.95 * s, 0.22 * s, body)


def lin(a, b): return lambda t: a + (b - a) * t
def done(z0, top, g, s): return top - (-g / 2 - 3 * s)


# --- Bone
def bone_ribcage():
    """Ribcage Greatsword: a spine up its back and an edge up its front, joined by open ribs - you see through it."""
    z0, s, eye, body, g = hilt(5, "bone"); L = 29.0
    th = lambda t: 0.7 * (1 - t) ** 0.3 + 0.1
    blade("blade", z0, L * 0.16, lambda t: -3.0, lambda t: 3.0, lambda t: 0.8, "b_bone", n=4)   # a solid root
    back = lambda t: (-3.0 * (1 - t ** 1.6), z0 + L * (0.14 + 0.86 * t))
    front = lambda t: (3.0 * (1 - t ** 2.2), z0 + L * (0.14 + 0.86 * t))
    sweep("blade_spine", back, lambda t: 0.9 * (1 - 0.5 * t), lambda t: 0.6 * (1 - 0.5 * t), lambda t: 0.85 * (1 - 0.5 * t), "b_bone", tiplen=1.5)
    sweep("blade_edge", front, lambda t: 0.6 * (1 - 0.6 * t), lambda t: 0.9 * (1 - 0.6 * t), lambda t: 0.55 * (1 - 0.5 * t), "b_bone", tiplen=1.5)
    sweep_edge("edge", front, lambda t: 0.9 * (1 - 0.6 * t), lambda t: 0.55 * (1 - 0.5 * t), "vein")
    for i in range(6):
        t = 0.1 + 0.13 * i
        bx, bz = back(t); fx, fz = front(t + 0.03)
        tube("rib%d" % i, [(bx, 0, bz, 0.42 * (1 - 0.4 * t)), ((bx + fx) / 2, 0, (bz + fz) / 2 + 1.1, 0.34 * (1 - 0.4 * t)), (fx, 0, fz, 0.42 * (1 - 0.4 * t))], "bone", flat=1.4)
    tube("marrow", [(back(t)[0] + 0.9, 0, back(t)[1], 0.16) for t in (0.0, 0.3, 0.6, 0.9)], "vein", flat=1.0, glow=True)   # the marrow running up inside the spine
    return done(z0, z0 + L + 1.5, g, s)


def bone_ossuary():
    """Ossuary Colossus: a vast slab of bone whose end is a jaw - a curled upper fang hooking over a row of teeth - and an eye at its root."""
    z0, s, eye, body, g = hilt(6, "bone", guard_w=1.3); L = 31.0
    xl = lambda t: -4.0 * sharp(t, 0.97)
    xr = lambda t: 4.2 + 1.2 * t
    th = lambda t: 1.0 - 0.3 * t
    blade("blade", z0, L, xl, xr, th, "b_bone", n=30)
    top = z0 + L
    tube("upper_jaw", [(-4.0, 0, top - 0.5, 1.3), (-1.0, 0, top + 2.6, 1.2), (3.8, 0, top + 2.2, 0.8), (6.2, 0, top - 0.4, 0.35), (6.6, 0, top - 2.6, 0.05)], "bone", flat=0.8)
    for i in range(6):   # teeth in the mouth between the slab's end and the jaw over it
        x = -2.6 + 1.5 * i
        gt.cone("t%d" % i, (x, 0, top - 0.1), (0.1, 0, 1), 0.5, 1.6 + 0.3 * (i % 2), "tooth", segs=4)
        gt.cone("u%d" % i, (x + 0.6, 0, top + 2.4 - 0.15 * i), (0, 0, -1), 0.45, 1.3, "tooth", segs=4)
    edge_line(z0, L, xr, lambda t: (xl(t) + xr(t)) / 2, th, "vein", r=0.2, t1=0.97)
    blade_eye((0.4, z0 + L * 0.12), s, eye, body, th(0.12))
    return done(z0, top + 3, g, s)


def bone_spine():
    """Spine Saw: the blade grown as a column of vertebrae with gaps between them, each winged with a blade either side,
    strung on a glowing red cord."""
    z0, s, eye, body, g = hilt(5, "bone"); L = 30.0
    n = 7; seg = L / n
    for i in range(n):
        za = z0 + i * seg; w = 2.6 * (1 - 0.55 * i / n)
        last = i == n - 1
        blade("blade%d" % i, za, seg * 0.72, lambda t, w=w: -w * (0.6 + 0.4 * math.sin(math.pi * min(1, t * 1.6))),
              lambda t, w=w: w * (0.6 + 0.4 * math.sin(math.pi * min(1, t * 1.6))), lambda t: 0.7, "b_bone", n=8,
              tip=(0, 2.5) if last else None)
        gt.box("body%d" % i, (0, 0, za + seg * 0.36), (1.4, 1.9, seg * 0.85), "bone", bevel=0.25, segs=1)
    tube("cord", [(0.0, 0, z0, 0.3), (0, 0, z0 + L * 0.5, 0.26), (0, 0, z0 + L - seg * 0.3, 0.2)], "vein", glow=True)
    return done(z0, z0 + L + 2.5, g, s)


def bone_reaper():
    """Tailbone Reaper: a shaft of vertebrae up to a skull-like crown with an eye in it; from the crown a broad bone
    scythe-blade sweeps out and hooks down, its inner edge honed red, and above it the tailbone keeps going - curling
    back over the crown in shrinking vertebrae to a barbed tip."""
    z0, s, eye, body, g = hilt(6, "bone"); H = 20.0
    for i in range(5):
        za = z0 + i * H / 5
        gt.box("vert%d" % i, (0, 0, za + H / 10), (2.2, 2.3, H / 5 * 0.72), "bone", bevel=0.35, segs=1)
        gt.box("disc%d" % i, (0, 0, za), (1.4, 1.6, H / 5 * 0.4), "flesh", bevel=0.2, segs=1)
    tube("cord", [(0.0, 0, z0, 0.35), (0, 0, z0 + H, 0.3)], "vein", glow=True)
    top = z0 + H
    tube("crown", [(1.6, 0, top + 0.2, 1.6), (0, 0, top + 1.6, 2.1), (-2.2, 0, top + 1.8, 1.8), (-3.2, 0, top + 1.4, 1.2)], "bone", flat=0.95)   # the socket the blade grows from
    blade_eye((-0.6, top + 1.6), s * 0.75, eye, body, 1.95)
    # the scythe: out to the left, cresting, then hooking down to its point; the lower (inner) side is the edge
    P = lambda t: (-3.0 - 15.0 * math.sin(t * 1.35), top + 2.2 + 2.6 * math.sin(t * math.pi * 0.8) - 6.5 * t ** 2.4)
    wl = lambda t: 0.9 * (1 - t) ** 0.6 + 0.08                     # the honed inner edge
    wr = lambda t: 3.6 * (1 - t ** 1.3) ** 0.8 + 0.1                # the broad back
    th = lambda t: 0.9 * (1 - t) ** 0.4 + 0.08
    sweep("blade", P, wl, wr, th, "b_bone", n=48, tiplen=2.0)
    sweep_edge("edge", P, wl, th, "vein", side=1, r=0.18)
    # the tailbone curling up and back over the crown: a thick tapering bone, ringed with vertebrae, a barb at the end
    arc = lambda k: (1.2 + 3.4 * math.cos(math.pi * (0.9 - 0.2 * k)), top + 3.4 + 3.4 * math.sin(math.pi * (0.9 - 0.2 * k)))
    tube("tail", [(arc(k)[0], 0, arc(k)[1], 1.25 * (1 - 0.11 * k)) for k in range(7)], "bone", flat=0.9)
    for k in range(6):
        x, z = arc(k + 0.5)
        gt.torus("tring%d" % k, (x, 0, z), (math.cos(math.pi * (0.4 - 0.2 * (k + 0.5))), 0, math.sin(math.pi * (0.4 - 0.2 * (k + 0.5)))),
                 1.1 * (1 - 0.11 * (k + 0.5)), 0.32, "bone")
    end = arc(6)
    gt.cone("barb", (end[0], 0, end[1]), (-0.5, 0, -1), 0.7, 2.6, "bone", segs=5)
    return done(z0, top + 8, g, s)


# --- Crystal
def crystal_rapier():
    """Prism Rapier: a long thin crystal needle with a swept cage of crystal round the hand."""
    z0, s, eye, body, g = hilt(5, "crystal", guard_w=0.8); L = 32.0
    xl = lambda t: -1.1 * (1 - t) ** 0.8 - 0.05
    xr = lambda t: 1.1 * (1 - t) ** 0.8 + 0.05
    th = lambda t: 0.75 * (1 - t) ** 0.7 + 0.05
    blade("blade", z0, L, xl, xr, th, "b_crys", glow=True, n=24, tip=(0, 1.5))
    ridge(z0, L, lambda t: 0.0, th, "pale", r=0.1)
    for k, (dx, dy) in enumerate(((2.6, 0), (2.2, 1.4), (2.2, -1.4))):   # the cage: crystal bows from the guard round the grip to the pommel
        tube("bow%d" % k, [(0.8, 0, z0 - 0.8, 0.28), (dx, dy, z0 - 3.0, 0.3), (dx * 0.9, dy * 0.9, -g / 2 + 1.0, 0.26), (0.6, 0, -g / 2 - 0.3, 0.22)], "crys2", glow=True)
    for side in (1, -1):
        o = gt.cone("quill%d" % side, (side * 1.3, 0, z0 - 0.4), (side * 1, 0, 0.35), 0.45, 3.4, "crys2", segs=4); GLOW.append(o)
    return done(z0, z0 + L + 1.5, g, s)


def crystal_lance():
    """Refraction Lance: a long four-edged crystal spike - two blades crossed through each other - over a wide crystal cone guarding the hand."""
    z0, s, eye, body, g = hilt(6, "crystal", guard_w=0.9); L = 34.0
    w = lambda t: 2.6 * (1 - t) ** 1.1 + 0.08
    th = lambda t: 0.5 * (1 - t) + 0.06
    blade("blade", z0 + 1.0, L, lambda t: -w(t), w, th, "b_crys", glow=True, n=20, tip=(0, 2.0))
    o = blade("blade_x", z0 + 1.0, L, lambda t: -w(t) * 0.75, lambda t: w(t) * 0.75, th, "b_crys", glow=True, n=20, tip=(0, 2.0))
    o.data.transform(Matrix.Rotation(math.pi / 2, 4, "Z"))
    bm = bmesh.new(); bmesh.ops.create_cone(bm, cap_ends=True, segments=8, radius1=4.6, radius2=1.4, depth=3.6)
    bmesh.ops.translate(bm, vec=(0, 0, z0 + 0.4), verts=bm.verts)
    o = gt.new_obj("vamplate", bm, REG["crys2"], smooth=False); GLOW.append(o)
    for k in range(8):   # facets of crystal along its rim
        a = k * math.pi / 4
        o = gt.cone("rim%d" % k, (4.2 * math.cos(a), 4.2 * math.sin(a), z0 - 1.2), (math.cos(a), math.sin(a), -0.6), 0.5, 1.6, "crys2", segs=4); GLOW.append(o)
    return done(z0, z0 + L + 3, g, s)


def crystal_geode():
    """Geode Breaker: a blunt, broadening club of a blade, cracked open at its end into a crust of crystal."""
    z0, s, eye, body, g = hilt(5, "crystal", guard_w=1.1); L = 26.0
    xl = lambda t: -(2.2 + 1.0 * t) * taper(t, 0.88, 0.5)
    xr = lambda t: (2.2 + 1.0 * t) * taper(t, 0.88, 0.5)
    th = lambda t: 0.9 + 0.5 * t
    blade("blade", z0, L, xl, xr, th, "b_bone", n=24)   # a pale stone shell...
    rnd = random.Random(7)
    for i in range(16):   # ...broken open at its end into crystal
        t = 0.6 + 0.4 * rnd.random()
        x = rnd.uniform(-1, 1) * abs(xl(t)) * 0.8
        side = rnd.choice((1, -1))
        d = Vector((x * 0.2, side * 1.0, 0.7 + rnd.random())).normalized()
        o = gt.cone("geo%d" % i, (x, side * th(t) * 0.6, z0 + L * t), d, rnd.uniform(0.5, 0.8), rnd.uniform(1.0, 2.0), "crys2", segs=4); GLOW.append(o)
    for i in range(5):
        o = gt.cone("crown%d" % i, (-2.4 + 1.2 * i, 0, z0 + L * 0.97), ((i - 2) * 0.25, 0, 1), 0.8, 2.6 - 0.4 * abs(i - 2), "crys2", segs=4); GLOW.append(o)
    edge_line(z0, L * 0.6, xr, lambda t: 0, th, "pale", r=0.18)
    return done(z0, z0 + L + 2.6, g, s)


def crystal_cluster():
    """Cluster Colossus: three great crystals grown from the guard as one - a towering middle blade and two shorter ones
    leaning out from it - with smaller crystals packed round their roots."""
    z0, s, eye, body, g = hilt(6, "crystal", guard_w=1.3)
    for name, ang, L, w in (("blade", 0.0, 32.0, 3.0), ("blade_l", 0.32, 18.0, 2.0), ("blade_r", -0.28, 21.0, 2.2)):
        o = blade(name, 0.0, L, lambda t, w=w: -w * (1 - t) ** 0.9 - 0.05, lambda t, w=w: w * (1 - t) ** 0.9 + 0.05,
                  lambda t, w=w: w * 0.45 * (1 - t) + 0.06, "b_crys", glow=True, n=8, tip=(0, 2.0))
        o.data.transform(Matrix.Translation((0, 0, z0 - 0.5)) @ Matrix.Rotation(ang, 4, "Y"))
    rnd = random.Random(11)
    for i in range(10):
        x = rnd.uniform(-3.5, 3.5)
        o = gt.cone("root%d" % i, (x, rnd.uniform(-0.8, 0.8), z0 + 0.2), (x * 0.25, rnd.uniform(-0.4, 0.4), 1), rnd.uniform(0.5, 0.9), rnd.uniform(2.0, 4.5), "crys2", segs=4); GLOW.append(o)
    ridge(z0 - 0.5, 32.0, lambda t: 0.0, lambda t: 3.0 * 0.45 * (1 - t) + 0.06, "pale", r=0.15)
    return done(z0, z0 + 34, g, s)


# --- Shadow
def shadow_sickle():
    """Eclipse Sickle: a long crescent sweeping up and over, its inner edge honed blue."""
    z0, s, eye, body, g = hilt(5, "shadow"); R = 11.0
    P = lambda t: (R - R * math.cos(t * 2.3) - 0.2 * t, z0 + R * math.sin(t * 2.3) * 1.25)
    wl = lambda t: 0.9 * (1 - t) + 0.08        # inner (toward the curve's middle)
    wr = lambda t: 2.6 * math.sin(math.pi * min(1, 0.15 + t)) ** 0.7 * (1 - t) ** 0.3 + 0.1   # the outer back, broad in the middle
    th = lambda t: 0.6 * (1 - t) ** 0.5 + 0.06
    sweep("blade", P, wl, wr, th, "b_shadow", glow=True, n=44, tiplen=1.6)
    sweep_edge("edge", P, wl, th, "blue", side=1)
    return done(z0, z0 + R * 1.3, g, s)


def shadow_nightfall():
    """Nightfall Crescent: a great moon of a blade, the grip at its back and both horns curling forward past it."""
    z0, s, eye, body, g = hilt(6, "shadow", guard_w=1.2); R = 13.0
    cx, cz = R * 0.85, z0 + R * 0.6
    P = lambda t: (cx + R * math.cos(math.pi * (1.25 - 1.5 * t)), cz + R * math.sin(math.pi * (1.25 - 1.5 * t)) * 1.1)
    wr = lambda t: 4.4 * math.sin(math.pi * t) ** 0.8 + 0.1   # the outside of the moon, fattest at its middle
    wl = lambda t: 0.9 * math.sin(math.pi * t) ** 0.5 + 0.1   # the inner, honed curve
    th = lambda t: 0.85 * math.sin(math.pi * t) ** 0.5 + 0.08
    sweep("blade", P, wl, wr, th, "b_shadow", glow=True, n=56, tiplen=1.4)
    sweep_edge("edge", P, wl, th, "blue", side=1, t0=0.05, t1=0.95)
    tube("haft", [(0, 0, z0 - 0.5, 0.8), (0.4, 0, z0 + 2.0, 0.7), (P(0.5)[0] - wr(0.5) * 0.8, 0, P(0.5)[1], 0.6)], "black")
    blade_eye((P(0.5)[0] - wr(0.5) * 0.4, P(0.5)[1]), s * 0.8, eye, body, th(0.5))
    return done(z0, cz + R * 1.15 + 1, g, s)


def prong(name, P, w, th, region, glow, tiplen=1.6):
    sweep(name, P, lambda t: w * (1 - t) ** 0.7 + 0.06, lambda t: w * (1 - t) ** 0.7 + 0.06, th, region, glow=glow, n=24, tiplen=tiplen)


def shadow_twin():
    """Twin Fang: one blade splitting halfway into two fangs, a long one and a shorter one hooked outward."""
    z0, s, eye, body, g = hilt(5, "shadow"); L = 14.0
    th = lambda t: 0.65
    blade("blade", z0, L, lambda t: -2.7, lambda t: 2.7, th, "b_shadow", glow=True, n=8)
    top = z0 + L
    prong("prong_a", lambda t: (-1.3 - 1.2 * t ** 2, top - 0.2 + 16 * t), 1.4, lambda t: 0.6 * (1 - t) + 0.06, "b_shadow", True)
    prong("prong_b", lambda t: (1.3 + 2.6 * t ** 1.5, top - 0.2 + 11 * t), 1.3, lambda t: 0.6 * (1 - t) + 0.06, "b_shadow", True)
    blade_eye((0, top - 1.2), s * 0.7, eye, body, 0.65)
    ridge(z0, L - 2.5, lambda t: 0.0, th, "blue", r=0.13, dashed=True)
    return done(z0, top + 17, g, s)


def shadow_hydra():
    """Hydra Edge: three blades from one root - the middle straight and long, the outer two curling out like necks -
    each with a small blue eye where it parts."""
    z0, s, eye, body, g = hilt(6, "shadow", guard_w=1.3); L = 10.0
    th = lambda t: 0.8
    blade("blade", z0, L, lambda t: -4.2 + 0.6 * t, lambda t: 4.2 - 0.6 * t, th, "b_shadow", glow=True, n=8)
    top = z0 + L
    pt = lambda t: 0.7 * (1 - t) + 0.06
    prong("prong_m", lambda t: (0.0, top - 0.3 + 24 * t), 1.7, pt, "b_shadow", True, 2.0)
    prong("prong_l", lambda t: (-2.6 - 7 * math.sin(t * 1.3) + 2 * t ** 3, top - 0.3 + 15 * t), 1.4, pt, "b_shadow", True)
    prong("prong_r", lambda t: (2.6 + 7 * math.sin(t * 1.3) - 2 * t ** 3, top - 0.3 + 15 * t), 1.4, pt, "b_shadow", True)
    for k, x in enumerate((-2.6, 0.0, 2.6)):
        for side in (1, -1):
            o = gt.gemstone("neye%d%d" % (k, side), (x, side * 0.85, top - 1.4), 0.55, 0.4); gt.fit_uv(o, REG[eye]); GLOW.append(o)
    return done(z0, top + 26, g, s)


# --- Flame
def flame_flamberge():
    """Flamberge: long and narrow, its edges rippling like a flame the whole way, a pair of flame-hooked lugs above the guard."""
    z0, s, eye, body, g = hilt(5, "flame", guard_w=0.9); L = 34.0
    wave = lambda t: 0.45 * math.sin(t * math.pi * 13) if t > 0.15 else 0.0
    xl = lambda t: (-1.7 + wave(t)) * sharp(t, 0.8)
    xr = lambda t: (1.7 + wave(t)) * sharp(t, 0.8)
    th = lambda t: 0.55 * (1 - t) ** 0.4 + 0.06
    blade("blade", z0, L, xl, xr, th, "b_flame", glow=True, n=80, tip=(0, 1.8))
    edge_line(z0, L, xr, lambda t: (xl(t) + xr(t)) / 2, th, "ember", r=0.12)
    for side in (1, -1):
        tube("lug%d" % side, [(side * 1.6, 0, z0 + L * 0.13, 0.4), (side * 3.0, 0, z0 + L * 0.13 + 0.6, 0.3), (side * 3.0, 0, z0 + L * 0.13 + 2.0, 0.05)], "ember", flat=0.6, glow=True)
    return done(z0, z0 + L + 1.8, g, s)


def flame_pyre():
    """Pyre Wing: a wing of flame - a long leading blade and a fan of shorter feather-blades behind it, all from the guard."""
    z0, s, eye, body, g = hilt(6, "flame", guard_w=1.2)
    for k in range(6):
        ang = 0.05 + 0.24 * k
        L = 32.0 - 4.2 * k
        P = lambda t, a=ang, L=L: (-L * math.sin(a + 0.25 * t * t) * t, z0 - 0.3 + L * math.cos(a + 0.25 * t * t) * t)
        w = 2.4 - 0.15 * k
        sweep("blade%d" % k, P, lambda t, w=w: w * 0.5 * (1 - t) ** 0.6 + 0.05, lambda t, w=w: w * (1 - t ** 1.5) ** 0.7 + 0.05,
              lambda t: 0.5 * (1 - t) + 0.06, "b_flame", glow=True, n=24, tiplen=1.5)
        if k == 0: sweep_edge("edge", P, lambda t, w=w: w * (1 - t ** 1.5) ** 0.7 + 0.05, lambda t: 0.5 * (1 - t) + 0.06, "ember")
    return done(z0, z0 + 33, g, s)


def flame_furnace():
    """Furnace Cleaver: a thick square slab of a blade with glowing vents cut across both faces."""
    z0, s, eye, body, g = hilt(5, "flame", guard_w=1.1); L = 24.0
    xl = lambda t: -2.6
    xr = lambda t: 2.8 + 0.8 * t
    th = lambda t: 1.1
    blade("blade", z0, L, xl, xr, th, "b_flame", glow=True, n=12, tip=(-0.8, 0.6))
    for i in range(4):
        z = z0 + L * (0.22 + 0.17 * i)
        for side in (1, -1):
            o = gt.box("vent%d%d" % (i, side), (0.3, side * 1.12, z), (3.6, 0.15, 0.55), "ember"); GLOW.append(o)
            gt.box("lip%d%d" % (i, side), (0.3, side * 1.15, z + 0.55), (4.0, 0.25, 0.3), "char")
    for k, x in enumerate((-1.6, 0.0, 1.6)):   # stubby chimneys on top, glowing inside
        gt.box("stack%d" % k, (x, 0, z0 + L + 0.8), (1.0, 1.4, 1.8), "char", bevel=0.15, segs=1)
        o = gt.box("glow%d" % k, (x, 0, z0 + L + 1.75), (0.7, 1.0, 0.15), "ember"); GLOW.append(o)
    edge_line(z0, L, xr, lambda t: 0.1, th, "ember", r=0.2)
    return done(z0, z0 + L + 2, g, s)


def flame_magma():
    """Magma Maw: the blade split open down the middle into a mouth - an upper and lower jaw-blade parting from the
    root, teeth along their inner edges, a molten tongue between, and an eye where they hinge."""
    z0, s, eye, body, g = hilt(6, "flame", guard_w=1.3); L = 8.0
    th = lambda t: 1.0
    blade("blade", z0, L, lambda t: -4.0, lambda t: 4.0, th, "b_flame", glow=True, n=6)
    top = z0 + L
    jt = lambda t: 0.9 * (1 - t) + 0.08
    A = lambda t: (-1.8 - 4.5 * math.sin(t * 1.4) * t, top - 0.3 + 24 * t)
    B = lambda t: (1.8 + 4.5 * math.sin(t * 1.4) * t, top - 0.3 + 22 * t)
    sweep("blade_a", A, lambda t: 2.4 * (1 - t) ** 0.6 + 0.06, lambda t: 1.0 * (1 - t) + 0.06, jt, "b_flame", glow=True, n=30, tiplen=1.6)
    sweep("blade_b", B, lambda t: 1.0 * (1 - t) + 0.06, lambda t: 2.4 * (1 - t) ** 0.6 + 0.06, jt, "b_flame", glow=True, n=30, tiplen=1.6)
    for i in range(7):   # teeth along the inner edges, pointing into the mouth
        t = 0.1 + 0.11 * i
        ax, az = A(t); bx, bz = B(t)
        gt.cone("ta%d" % i, (ax + 0.9, 0, az), (1, 0, 0.3), 0.45, 1.5 - 0.1 * i, "tooth", segs=4)
        gt.cone("tb%d" % i, (bx - 0.9, 0, bz), (-1, 0, 0.3), 0.45, 1.5 - 0.1 * i, "tooth", segs=4)
    tube("tongue", [(0, 0, top - 0.5, 1.0), (0.6, 0, top + 6, 0.8), (-0.5, 0, top + 12, 0.55), (0.3, 0, top + 16, 0.08)], "ember", flat=0.6, glow=True)
    blade_eye((0, z0 + L * 0.45), s, eye, body, 1.0)
    return done(z0, top + 25, g, s)


BRANCHES = {   # path: ((stage 5 A, its ascended), (stage 5 B, its ascended))
    "bone": (("ribcage", bone_ribcage, "ossuary", bone_ossuary), ("spine", bone_spine, "reaper", bone_reaper)),
    "crystal": (("rapier", crystal_rapier, "lance", crystal_lance), ("geode", crystal_geode, "cluster", crystal_cluster)),
    "shadow": (("sickle", shadow_sickle, "nightfall", shadow_nightfall), ("twin", shadow_twin, "hydra", shadow_hydra)),
    "flame": (("flamberge", flame_flamberge, "pyre", flame_pyre), ("furnace", flame_furnace, "magma", flame_magma))}
NAMES = {"fang": "Fang", "gnaw": "Gnaw", "maw": "Maw Blade", "bone_evo": "Marrow Cleaver", "crystal_evo": "Shard Blade",
         "shadow_evo": "Umbral Edge", "flame_evo": "Ember Brand",
         "bone_ribcage": "Ribcage Greatsword", "bone_ossuary": "Ossuary Colossus", "bone_spine": "Spine Saw", "bone_reaper": "Tailbone Reaper",
         "crystal_rapier": "Prism Rapier", "crystal_lance": "Refraction Lance", "crystal_geode": "Geode Breaker", "crystal_cluster": "Cluster Colossus",
         "shadow_sickle": "Eclipse Sickle", "shadow_nightfall": "Nightfall Crescent", "shadow_twin": "Twin Fang", "shadow_hydra": "Hydra Edge",
         "flame_flamberge": "Flamberge", "flame_pyre": "Pyre Wing", "flame_furnace": "Furnace Cleaver", "flame_magma": "Magma Maw"}


FORMS = {"fang": fang, "gnaw": gnaw, "maw": maw}
for _p in ("bone", "crystal", "shadow", "flame"):
    FORMS[_p + "_evo"] = (lambda p: lambda: path_blade(p, "evo"))(_p)
    for a5, f5, a6, f6 in BRANCHES[_p]:
        FORMS["%s_%s" % (_p, a5)] = f5; FORMS["%s_%s" % (_p, a6)] = f6
TARGETS = {"fang": 1600, "gnaw": 2200, "maw": 3000}


# ------------------------------------------------------------------ build, export, render
def tris(ob): return sum(len(p.vertices) - 2 for p in ob.data.polygons)


KEEP = ("blade", "edge", "ridge", "rune", "vein")


def slim(target):
    for ob in list(gt.PARTS):
        if ob.type != "MESH":
            bpy.ops.object.select_all(action="DESELECT"); ob.select_set(True); bpy.context.view_layer.objects.active = ob
            bpy.ops.object.convert(target="MESH")
    for ob in gt.PARTS:
        bm = bmesh.new(); bm.from_mesh(ob.data)
        bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=0.01)
        bm.to_mesh(ob.data); bm.free()
    total = sum(tris(o) for o in gt.PARTS)
    small = sum(tris(o) for o in gt.PARTS if tris(o) <= 80 or o.name.startswith(KEEP))
    if total <= target: return
    ratio = max(0.25, (target - small) / max(1, total - small))
    for ob in gt.PARTS:
        if tris(ob) <= 80 or ob.name.startswith(KEEP): continue   # the blade's outline and its glowing lines stay exact
        m = ob.modifiers.new("slim", "DECIMATE"); m.decimate_type = "COLLAPSE"; m.ratio = ratio
        bpy.ops.object.select_all(action="DESELECT"); ob.select_set(True); bpy.context.view_layer.objects.active = ob
        bpy.ops.object.modifier_apply(modifier="slim")


def build(form, out_dir):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    gt.PARTS.clear(); GLOW.clear()
    tex = os.path.join(out_dir, TEX + ".png")
    length = FORMS[form]()
    slim(TARGETS.get(form, 4000))
    image = bpy.data.images.load(tex)
    def material(name, glow):
        m = bpy.data.materials.new(name); m.use_nodes = True
        nt = m.node_tree; bsdf = nt.nodes.get("Principled BSDF")
        img = nt.nodes.new("ShaderNodeTexImage"); img.image = image; img.interpolation = "Closest"
        bsdf.inputs["Roughness"].default_value = 0.6
        if glow:
            bsdf.inputs["Base Color"].default_value = (0, 0, 0, 1)
            nt.links.new(img.outputs["Color"], bsdf.inputs["Emission Color"]); bsdf.inputs["Emission Strength"].default_value = 1.0
        else: nt.links.new(img.outputs["Color"], bsdf.inputs["Base Color"])
        return m
    glow_m, plain_m = material(form + "_glow", True), material(form, False)
    for ob in gt.PARTS: ob.data.materials.append(glow_m if ob in GLOW else plain_m)
    bpy.ops.object.select_all(action="DESELECT")
    for ob in gt.PARTS: ob.select_set(True)
    bpy.context.view_layer.objects.active = gt.PARTS[0]
    bpy.ops.object.join()
    ob = bpy.context.view_layer.objects.active; ob.name = form
    ob.location = (8, -8, 8)
    bpy.ops.wm.obj_export(filepath=os.path.join(out_dir, form + ".obj"), export_selected_objects=True, export_uv=True, export_normals=True,
                          export_materials=True, export_triangulated_mesh=True, forward_axis="NEGATIVE_Z", up_axis="Y", global_scale=1 / 16, path_mode="STRIP")
    print(form, "tris", tris(ob), "length", round(length, 1))
    return ob, length


SHEET = [["fang", "gnaw", "maw"]] + [[p + "_evo"] + ["%s_%s" % (p, n) for a5, _, a6, _ in BRANCHES[p] for n in (a5, a6)]
                                   for p in ("bone", "crystal", "shadow", "flame")]
STAGE = {0: "4", 1: "5", 2: "6", 3: "5", 4: "6"}


def render_sheet(out_dir, only=None):
    """Every stage on one sheet: a column a path (the base stages first), its stages down it, all at the same scale."""
    cols = SHEET if not only else [[f for f in col if f in only] for col in SHEET]
    cols = [c for c in cols if c]
    CW, CH = 400, 640
    for col in cols:
        for form in col:
            ob, length = build(form, out_dir)
            ob.location = (0, 0, 0); ob.rotation_euler = (0, 0, math.radians(18))
            sc = bpy.context.scene
            sc.render.engine = "CYCLES"; sc.cycles.samples = 20; sc.cycles.device = "CPU"
            sc.view_settings.view_transform = "Standard"
            sc.render.resolution_x, sc.render.resolution_y = CW, CH
            world = bpy.data.worlds.new("w"); sc.world = world; world.use_nodes = True
            world.node_tree.nodes["Background"].inputs[0].default_value = (0.03, 0.035, 0.06, 1)
            for name, rot, energy in (("key", (math.radians(50), 0, math.radians(-35)), 4.0), ("fill", (math.radians(70), 0, math.radians(150)), 1.6),
                                      ("top", (math.radians(10), 0, 0), 1.2)):
                L = bpy.data.lights.new(name, "SUN"); L.energy = energy
                o = bpy.data.objects.new(name, L); o.rotation_euler = rot; sc.collection.objects.link(o)
            cam = bpy.data.cameras.new("cam"); cam.type = "ORTHO"; cam.ortho_scale = 58
            co = bpy.data.objects.new("cam", cam); sc.collection.objects.link(co); sc.camera = co
            bpy.context.view_layer.update()
            xs = [(ob.matrix_world @ v.co).x for v in ob.data.vertices]
            co.location = ((min(xs) + max(xs)) / 2, -60, 17); co.rotation_euler = (math.radians(90), 0, 0)   # centred on what's there (a scythe, a wing)
            sc.render.filepath = os.path.join(out_dir, "blade_%s.png" % form)
            bpy.ops.render.render(write_still=True)
    sheet = Image.new("RGB", (CW * len(cols), CH * max(len(c) for c in cols) + 0), Image.open(os.path.join(out_dir, "blade_%s.png" % cols[0][0])).getpixel((2, 2)))
    d = ImageDraw.Draw(sheet)
    for i, col in enumerate(cols):
        for j, form in enumerate(col):
            sheet.paste(Image.open(os.path.join(out_dir, "blade_%s.png" % form)).convert("RGB"), (i * CW, j * CH))
            stage = str(j + 1) if i == 0 and not only else STAGE.get(j, "")
            d.text((i * CW + 10, j * CH + 8), "%s  %s" % (stage, NAMES[form]), fill=(230, 220, 220))
    sheet.save(os.path.join(out_dir, "hunger_blade_sheet.png"))


ORDER = ["fang", "gnaw", "maw"] + [n for p in ("bone", "crystal", "shadow", "flame")
                                   for n in [p + "_evo"] + ["%s_%s" % (p, x) for a5, _, a6, _ in BRANCHES[p] for x in (a5, a6)]]   # the form numbers the game uses (server/HungerFx)


def to_game(out_dir, only=None):
    """Every sword into the mod as hunger_blade_<name> (an OBJ model each), then the held item's own model: the Fang,
    with an override per form ("iron_spell_grimoire:form" = form / 32) pointing at the others."""
    import json
    for form in ORDER:
        if only and form not in only: continue
        _, length = build(form, out_dir)
        name = "hunger_blade_" + form
        for ext in (".obj", ".mtl"): os.replace(os.path.join(out_dir, form + ext), os.path.join(out_dir, name + ext))
        objp = os.path.join(out_dir, name + ".obj")
        txt = open(objp).read().replace("mtllib %s.mtl" % form, "mtllib %s.mtl" % name, 1)
        open(objp, "w").write(txt)
        i = ORDER.index(form)
        held = [15, 19, 23][i] if i < 3 else (25 if form.endswith("_evo") else (31 if (i - 3) % 5 in (2, 4) else 28))
        gt.export_to_game(name, out_dir, gt.display(length, held=held), glowing={form + "_glow"},
                          textures={form: TEX, form + "_glow": TEX}, render_type="minecraft:cutout")
    md = os.path.join(gt.ASSETS, "models", "item")
    base = json.load(open(os.path.join(md, "hunger_blade_fang.json")))
    base["overrides"] = [{"predicate": {"iron_spell_grimoire:form": round(i / 32, 5)}, "model": "iron_spell_grimoire:item/hunger_blade_" + f}
                         for i, f in enumerate(ORDER) if i > 0]
    json.dump(base, open(os.path.join(md, "hunger_blade.json"), "w"), indent=1)


if __name__ == "__main__":
    mode, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    paint(os.path.join(out, TEX + ".png"))
    if mode == "render": render_sheet(out, sys.argv[3].split(",") if len(sys.argv) > 3 else None)
    if mode == "game": to_game(out, sys.argv[3].split(",") if len(sys.argv) > 3 else None)
