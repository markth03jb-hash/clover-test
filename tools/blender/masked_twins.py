"""The Masked Twins, modelled in Blender (run with Blender's Python: bpy). Original designs:

  Sol (the sun twin)    a round golden face ringed with rays; three sunstone sockets (brow, both cheeks)
  Nyx (the moon twin)   a long silver face hugged by a crescent; three moonstone sockets (brow, under an eye, the crescent)
  Each mask at three stages: 1 bare sockets, 2 the first stone set and the ornament grown, 3 every stone set and lit.
  Eclipse Union         the two fused down the middle round a black eclipse
  The trial wardens     each mask worn by a floating robed body (the Dawn Warden, the Dusk Warden)
  The Twin Grimoire     a book split gold and silver, a small mask on each half
  A player wearing Sol on the face, Nyx hanging at the hip (a preview of how they're worn)

Blender space (pixels; exported at 1/16): Z up, X across, the face looking toward -Y.

  python tools/blender/masked_twins.py render OUT_DIR [names]   a sheet of every model
  python tools/blender/masked_twins.py game OUT_DIR             writes the masks and wardens into the mod (models/item, textures/item)
"""
import math, os, sys
import bpy, bmesh
from mathutils import Vector, Matrix
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gilded_thorn as gt

TEX = "masked_twins"
gt.REG.clear()
gt.REG.update({"gold": (0, 0, 64, 32), "gold_d": (64, 0, 128, 32), "silver": (128, 0, 192, 32), "silver_d": (192, 0, 256, 32),
               "sunstone": (0, 32, 32, 64), "moonstone": (32, 32, 64, 64), "glow_g": (64, 32, 96, 64), "glow_s": (96, 32, 128, 64),
               "void": (128, 32, 160, 64), "socket": (160, 32, 192, 64), "skin": (192, 32, 224, 64), "hair": (224, 32, 256, 64),
               "shirt": (0, 64, 64, 96), "pants": (64, 64, 128, 96), "cord": (128, 64, 192, 96), "gem": (192, 64, 224, 96),
               "robe_g": (0, 96, 128, 128), "robe_n": (128, 96, 256, 128),
               "page": (0, 128, 64, 160), "cover_g": (64, 128, 128, 160), "cover_s": (128, 128, 192, 160)})
REG = gt.REG
GLOW = []
LOW = False   # the game's export: coarser tubes and shells (the previews use the fine ones)


# ------------------------------------------------------------------ the texture
def mix(a, b, t): t = max(0.0, min(1.0, t)); return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def paint(out):
    im = Image.new("RGBA", (256, 256), (0, 0, 0, 255)); px = im.load()
    def region(name, fn):
        x0, y0, x1, y1 = REG[name]
        for x in range(x0, x1):
            for y in range(y0, y1):
                px[x, y] = fn((x - x0) / (x1 - x0 - 1), (y - y0) / (y1 - y0 - 1), x - x0, y - y0) + (255,)
    def wave(x, y, a, b): return 0.5 + 0.5 * math.sin(x * a + math.sin(y * b) * 2)
    region("gold", lambda u, v, x, y: mix((196, 140, 44), (250, 208, 108), 0.5 + 0.3 * math.sin(x * 0.09 + y * 0.05) + 0.2 * math.sin(y * 0.21)))
    region("gold_d", lambda u, v, x, y: mix((96, 54, 16), (170, 108, 34), wave(x, y, 0.3, 0.4)))
    region("silver", lambda u, v, x, y: mix((168, 178, 200), (236, 242, 255), 0.5 + 0.3 * math.sin(x * 0.09 + y * 0.05) + 0.2 * math.sin(y * 0.21)))
    region("silver_d", lambda u, v, x, y: mix((36, 40, 78), (86, 96, 150), wave(x, y, 0.27, 0.4)))
    def stone(c0, c1):
        return lambda u, v, x, y: mix(c1, c0, min(1, math.hypot(u - 0.35, v - 0.35) * 1.6))
    region("sunstone", stone((200, 60, 10), (255, 236, 150)))
    region("moonstone", stone((70, 110, 230), (236, 246, 255)))
    region("glow_g", lambda u, v, x, y: mix((255, 250, 210), (255, 190, 60), abs(v - 0.5) * 2))
    region("glow_s", lambda u, v, x, y: mix((240, 250, 255), (130, 180, 255), abs(v - 0.5) * 2))
    region("void", lambda u, v, x, y: mix((2, 2, 6), (14, 14, 26), wave(x, y, 0.5, 0.5)))
    region("socket", lambda u, v, x, y: mix((30, 26, 30), (64, 56, 60), wave(x, y, 0.8, 0.6)))
    region("skin", lambda u, v, x, y: mix((190, 140, 110), (214, 166, 132), wave(x, y, 0.6, 0.5)))
    region("hair", lambda u, v, x, y: mix((40, 26, 16), (70, 46, 28), wave(x, y, 0.9, 0.4)))
    region("shirt", lambda u, v, x, y: mix((20, 120, 130), (40, 160, 170), wave(x, y, 0.4, 0.4)))
    region("pants", lambda u, v, x, y: mix((40, 40, 110), (60, 60, 150), wave(x, y, 0.4, 0.4)))
    region("cord", lambda u, v, x, y: mix((70, 40, 20), (130, 86, 46), 0.5 + 0.5 * math.sin(x * 1.3 + y * 0.6)))
    region("gem", lambda u, v, x, y: (255, 255, 255))
    def robe_g(u, v, x, y):   # crimson cloth fading to gold at the hem, a gold band
        c = mix((110, 20, 20), (190, 60, 24), wave(x, y, 0.15, 0.2))
        return mix(c, (255, 190, 80), max(0.0, v - 0.85) * 4)
    region("robe_g", robe_g)
    def robe_n(u, v, x, y):   # midnight cloth with a scatter of stars
        c = mix((8, 10, 30), (30, 36, 80), wave(x, y, 0.13, 0.22))
        if (x * 37 + y * 101) % 97 == 0: c = (220, 230, 255)
        return c
    region("robe_n", robe_n)
    region("page", lambda u, v, x, y: mix((200, 184, 150), (236, 224, 196), wave(x, y, 0.1, 1.4)))
    region("cover_g", lambda u, v, x, y: mix((140, 70, 20), (220, 160, 60), wave(x, y, 0.17, 0.5)))
    region("cover_s", lambda u, v, x, y: mix((40, 46, 90), (150, 160, 200), wave(x, y, 0.17, 0.5)))
    im.save(out)


# ------------------------------------------------------------------ pieces
def apply_mod(ob, m):
    bpy.ops.object.select_all(action="DESELECT"); ob.select_set(True); bpy.context.view_layer.objects.active = ob
    bpy.ops.object.modifier_apply(modifier=m.name)


def disc(name, rfn, yfn, thick, region, a0=0.0, a1=2 * math.pi, M=56, N=7, glow=False):
    """A curved shell: rings out from the middle to the outline r(angle), each point pushed through Y by yfn(x, z)."""
    if LOW: M, N = 36, 4
    full = abs(a1 - a0 - 2 * math.pi) < 1e-6
    bm = bmesh.new()
    if full: c = bm.verts.new((0, yfn(0, 0), 0))
    J = M if full else M + 1
    rings = []
    for i in range(1, N + 1):
        f = i / N; row = []
        for j in range(J):
            a = a0 + (a1 - a0) * j / M
            r = rfn(a) * f; x, z = r * math.cos(a), r * math.sin(a)
            row.append(bm.verts.new((x, yfn(x, z), z)))
        rings.append(row)
    if not full:   # a half: a spine of points down the cut, from the middle out along both edges' directions
        c = bm.verts.new((0, yfn(0, 0), 0))
    for j in range(J if full else J - 1):
        k = (j + 1) % J
        bm.faces.new((c, rings[0][k], rings[0][j]))
        for i in range(N - 1):
            bm.faces.new((rings[i][j], rings[i][k], rings[i + 1][k], rings[i + 1][j]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    ob = gt.new_obj(name, bm, REG[region])
    m = ob.modifiers.new("solid", "SOLIDIFY"); m.thickness = thick; m.offset = 1.0
    apply_mod(ob, m)
    if min(v.co.y for v in ob.data.vertices) < yfn(0, 0) - thick * 0.5:   # it grew forward: push it back, so the face's front is the surface
        for v in ob.data.vertices: v.co.y += thick
    gt.fit_uv(ob, REG[region])
    if glow: GLOW.append(ob)
    return ob


def tube(name, pts, region, res=4, bev=2, glow=False):
    if LOW: res, bev = 2, 1
    o = gt.curve_obj(name, pts, flat=1.0, region=region, res=res, bev=bev)
    if glow: GLOW.append(o)
    return o


def blob(name, center, size, region, rot=None, glow=False, segs=10):
    if LOW: segs = min(segs, 8)
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=segs, v_segments=max(4, segs // 2 + 1), radius=1.0)
    bmesh.ops.scale(bm, vec=size, verts=bm.verts)
    if rot: bmesh.ops.rotate(bm, cent=(0, 0, 0), matrix=rot, verts=bm.verts)
    bmesh.ops.translate(bm, vec=center, verts=bm.verts)
    o = gt.new_obj(name, bm, REG[region])
    if glow: GLOW.append(o)
    return o


def spike(name, base, direction, r, length, region, flat=0.45, glow=False, segs=4):
    """A flat ray: a pyramid squashed through Y."""
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=segs, radius1=r, radius2=0.0, depth=length)
    bmesh.ops.translate(bm, vec=(0, 0, length / 2), verts=bm.verts)
    bmesh.ops.scale(bm, vec=(1, flat, 1), verts=bm.verts)
    d = Vector(direction).normalized()
    q = Vector((0, 0, 1)).rotation_difference(d)
    bmesh.ops.rotate(bm, cent=(0, 0, 0), matrix=q.to_matrix(), verts=bm.verts)
    bmesh.ops.translate(bm, vec=base, verts=bm.verts)
    o = gt.new_obj(name, bm, REG[region])
    if glow: GLOW.append(o)
    return o


def ring(name, pts_fn, n, region, r, glow=False, closed=True):
    """A tube through n points of pts_fn(t) (t 0..1), closed round."""
    if LOW: n = max(10, n * 2 // 3)
    P = [pts_fn(i / n) for i in range(n + (1 if closed else 0))]
    return tube(name, [(x, y, z, r(i / n) if callable(r) else r) for i, (x, y, z) in enumerate(P)], region, glow=glow)


def socket(name, at, filled, stone, lit):
    x, y, z = at
    gt.torus(name + "_rim", (x, y - 0.05, z), (0, 1, 0), 0.62, 0.2, region="gold_d" if stone == "sunstone" else "silver_d")
    if filled: blob(name + "_gem", (x, y - 0.25, z), (0.5, 0.32, 0.5), stone, glow=lit)
    else: blob(name + "_hole", (x, y + 0.05, z), (0.45, 0.15, 0.45), "socket")


def sun_depth(x, z): return -(1.6 * max(0.0, 1 - (x / 5.4) ** 2) + 0.5 * max(0.0, 1 - (z / 5.6) ** 2))
def moon_depth(x, z): return -(1.5 * max(0.0, 1 - (x / 4.8) ** 2) + 0.45 * max(0.0, 1 - (z / 6.0) ** 2))


def on(depth, x, z, lift=0.0): return (x, depth(x, z) - lift, z)


# ------------------------------------------------------------------ the masks
def sol(stage, half=False):
    """The sun twin. half: only its left half (for the Eclipse)."""
    R = 4.6
    a0, a1 = (math.pi / 2, 3 * math.pi / 2) if half else (0.0, 2 * math.pi)
    d = sun_depth
    disc("sol_face", lambda a: R * (1 + 0.06 * max(0.0, -math.sin(a)) ** 3), d, 0.55, "gold", a0, a1)
    ring("sol_rim", lambda t: (lambda a: on(d, (R + 0.1) * math.cos(a), (R + 0.1) * math.sin(a), 0.1))(a0 + (a1 - a0) * t), 32,
         "gold_d", 0.32, closed=False) if half else \
        ring("sol_rim", lambda t: on(d, (R + 0.1) * math.cos(2 * math.pi * t), (R + 0.1) * math.sin(2 * math.pi * t) * (1 + 0.06 * max(0.0, -math.sin(2 * math.pi * t)) ** 3), 0.1), 40, "gold_d", 0.32)
    # rays: short and few at first, long and many when grown
    n = {1: 8, 2: 12, 3: 16}[stage]
    for k in range(n):
        a = 2 * math.pi * k / n + math.pi / 2
        if half and math.cos(a) > 0.05: continue
        L = {1: 1.4, 2: 2.0, 3: 2.6}[stage] * (1.0 if k % 2 == 0 else 0.62)
        b = on(d, (R + 0.1) * math.cos(a), (R + 0.1) * math.sin(a), 0.1)
        spike("sol_ray%d" % k, b, (math.cos(a), 0, math.sin(a)), 0.75 if k % 2 == 0 else 0.55, L, "gold")
        if stage == 3 and k % 2 == 0:
            spike("sol_rayglow%d" % k, (b[0], b[1] - 0.2, b[2]), (math.cos(a), 0, math.sin(a)), 0.28, L * 0.8, "glow_g", glow=True)
    # the face: calm brows, a long nose ridge, a slight smile, almond eyes
    for s in (-1, 1):
        if half and s > 0: continue
        tube("sol_brow%d" % s, [on(d, s * 0.7, 1.8, 0.15) + (0.18,), on(d, s * 1.8, 2.05, 0.15) + (0.24,), on(d, s * 2.9, 1.6, 0.15) + (0.14,)], "gold_d")
        rot = Matrix.Rotation(-s * 0.12, 3, "Y")
        blob("sol_eye%d" % s, on(d, s * 1.75, 0.7, 0.12), (1.05, 0.25, 0.42), "void", rot=rot)
    tube("sol_nose", [on(d, 0, 1.5, 0.05) + (0.22,), on(d, 0, 0.2, 0.25) + (0.3,), on(d, 0, -0.9, 0.3) + (0.36,)], "gold")
    tube("sol_mouth", [on(d, -1.1, -2.1, 0.1) + (0.12,), on(d, 0, -2.35, 0.12) + (0.15,), on(d, 1.1 if not half else 0, -2.1, 0.1) + (0.12,)], "gold_d")
    # the sunstone sockets: the brow, then the cheeks
    for i, (x, z) in enumerate([(0, 3.0), (-2.8, -1.3), (2.8, -1.3)]):
        if half and x > 0: continue
        socket("sol_slot%d" % i, on(d, x, z, 0.05), stage >= (2 if i == 0 else 3), "sunstone", stage == 3)
    if stage == 3 and not half:   # a halo of light behind it
        ring("sol_halo", lambda t: ((R + 1.6) * math.cos(2 * math.pi * t), 0.8, (R + 1.6) * math.sin(2 * math.pi * t)), 48, "glow_g", 0.18, glow=True)
    return 2 * R


def nyx(stage, half=False):
    """The moon twin. half: only its right half (for the Eclipse)."""
    d = moon_depth
    def rfn(a):
        r = 1 / math.sqrt((math.cos(a) / 4.1) ** 2 + (math.sin(a) / 4.9) ** 2)
        return r * (1 + 0.14 * max(0.0, -math.sin(a)) ** 4)
    a0, a1 = (-math.pi / 2, math.pi / 2) if half else (0.0, 2 * math.pi)
    disc("nyx_face", rfn, d, 0.5, "silver", a0, a1)
    def rim(t):
        a = a0 + (a1 - a0) * t; r = rfn(a) + 0.08
        return on(d, r * math.cos(a), r * math.sin(a), 0.1)
    ring("nyx_rim", rim, 40, "silver_d", 0.26, closed=not half)
    # the crescent hugging one side (the left; on the Eclipse, mirrored onto the right)
    side = 1 if half else -1
    def crescent(name, c0, c1, bulge, rad, region, glow=False, lift=0.3, n=22):
        P = []
        for i in range(n + 1):
            p = i / n; a = c0 + (c1 - c0) * p
            r = rfn(a) + 0.35 + bulge * math.sin(p * math.pi)
            P.append(on(d, r * math.cos(a), r * math.sin(a), lift) + (0.08 + rad * math.sin(p * math.pi) ** 0.8,))
        if half: P = [(-x, y, z, r) for x, y, z, r in P]
        return tube(name, P, region, glow=glow)
    crescent("nyx_crescent", math.radians(70), math.radians(255), {1: 0.9, 2: 1.4, 3: 1.8}[stage], {1: 0.5, 2: 0.65, 3: 0.75}[stage], "silver")
    if stage == 3: crescent("nyx_crescent_glow", math.radians(80), math.radians(245), {3: 1.8}[3] - 0.15, 0.22, "glow_s", glow=True, lift=0.75)
    if stage >= 2 and not half:   # a second, smaller crescent over the right brow
        crescent2 = []
        for i in range(15):
            p = i / 14; a = math.radians(-10) + math.radians(80) * p
            r = rfn(a) + 0.3 + 0.8 * math.sin(p * math.pi)
            crescent2.append(on(d, r * math.cos(a), r * math.sin(a), 0.2) + (0.06 + 0.35 * math.sin(p * math.pi),))
        tube("nyx_crescent2", crescent2, "silver")
    # stars: four-point sparks round the face
    if stage >= 2:
        stars = [(-5.6, 4.6), (5.0, -3.6), (-4.6, -5.0)] + ([(5.6, 2.8), (-6.4, 0.6), (3.0, 6.0)] if stage == 3 else [])
        for i, (x, z) in enumerate(stars):
            if half: x = abs(x)
            sz = 0.55 if i < 3 else 0.42
            for k in range(4):
                a = k * math.pi / 2
                spike("nyx_star%d_%d" % (i, k), (x, -0.4, z), (math.cos(a), 0, math.sin(a)), sz * 0.45, sz * 1.6,
                      "glow_s" if stage == 3 else "silver", glow=stage == 3)
    # the face: thin slanting brows, narrow eyes tilting down at the outside, a small closed mouth
    for s in (-1, 1):
        if half and s < 0: continue
        tube("nyx_brow%d" % s, [on(d, s * 0.6, 1.7, 0.12) + (0.12,), on(d, s * 1.7, 2.1, 0.12) + (0.16,), on(d, s * 2.7, 1.9, 0.12) + (0.1,)], "silver_d")
        rot = Matrix.Rotation(s * 0.28, 3, "Y")
        blob("nyx_eye%d" % s, on(d, s * 1.6, 0.8, 0.12), (1.1, 0.25, 0.3), "void", rot=rot)
    tube("nyx_nose", [on(d, 0, 1.3, 0.05) + (0.16,), on(d, 0, -0.6, 0.18) + (0.24,)], "silver")
    tube("nyx_mouth", [on(d, -0.7 if not half else 0, -2.5, 0.08) + (0.1,), on(d, 0 if not half else 0.35, -2.55, 0.1) + (0.12,), on(d, 0.7, -2.5, 0.08) + (0.1,)], "silver_d")
    # moonstone sockets: the brow, under the right eye (a tear), on the crescent
    slots = [(0, 3.1), (1.7, -0.6), (-4.9, -0.4)]
    for i, (x, z) in enumerate(slots):
        if half and x < 0: continue
        at = on(d, x, z, 0.05) if i < 2 else (x, -0.6, z)
        socket("nyx_slot%d" % i, at, stage >= (2 if i == 0 else 3), "moonstone", stage == 3)
    return 9


def eclipse():
    """The two fused: Sol's left half, Nyx's right, an eclipse at the brow, a black seam down the middle."""
    sol(3, half=True)
    nyx(3, half=True)
    tube("ecl_seam", [(0, -2.4, 5.0, 0.3), (0, -2.3, 0, 0.36), (0, -1.6, -5.3, 0.3)], "void")
    # the eclipse: a black disc ringed half gold, half silver
    blob("ecl_sun", (0, -2.6, 3.4), (1.15, 0.35, 1.15), "void", segs=14)
    ring("ecl_ring_g", lambda t: (-1.45 * math.sin(math.pi * t), -2.55, 3.4 + 1.45 * math.cos(math.pi * t)), 16, "glow_g", 0.18, glow=True, closed=False)
    ring("ecl_ring_s", lambda t: (1.45 * math.sin(math.pi * t), -2.55, 3.4 + 1.45 * math.cos(math.pi * t)), 16, "glow_s", 0.18, glow=True, closed=False)
    # lit pupils: gold left, silver right
    blob("ecl_pupil_g", on(sun_depth, -1.75, 0.7, 0.4), (0.28, 0.15, 0.28), "glow_g", glow=True)
    blob("ecl_pupil_s", on(moon_depth, 1.6, 0.8, 0.4), (0.26, 0.15, 0.22), "glow_s", glow=True)
    return 10


# ------------------------------------------------------------------ the wardens, the book, the player
def place(start, scale=1.0, offset=(0, 0, 0), rot=None):
    """Scale, turn and move the parts made since `start` (their vertices: the texture mapping stays as made)."""
    M = Matrix.Translation(offset) @ (rot.to_4x4() if rot else Matrix.Identity(4)) @ Matrix.Scale(scale, 4)
    for ob in gt.PARTS[start:]:
        ob.data.transform(M)


def lathe(name, prof, region, M=20, jag=0.0, glow=False):
    """A turned body through prof [(radius, z)], bottom first; jag: a ragged hem (alternate points dropped)."""
    bm = bmesh.new(); rows = []
    for i, (r, z) in enumerate(prof):
        row = []
        for j in range(M):
            a = 2 * math.pi * j / M
            zz = z - (jag * (1.0 if j % 2 else 0.35) if i == 0 else 0)
            row.append(bm.verts.new((r * math.cos(a), r * math.sin(a) * 0.75, zz)))
        rows.append(row)
    for i in range(len(rows) - 1):
        for j in range(M):
            k = (j + 1) % M
            bm.faces.new((rows[i][j], rows[i][k], rows[i + 1][k], rows[i + 1][j]))
    bm.faces.new(rows[-1][::-1])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    o = gt.new_obj(name, bm, REG[region])
    if glow: GLOW.append(o)
    return o


def warden(sun):
    robe, trim, glow = ("robe_g", "gold", "glow_g") if sun else ("robe_n", "silver", "glow_s")
    lathe("w_robe", [(6.2, -16), (5.4, -11), (4.4, -5), (3.8, -1), (3.4, 1), (2.4, 2.4)], robe, jag=2.2)
    gt.torus("w_collar", (0, 0, 1.4), (0, 0, 1), 3.4, 0.55, region=trim)
    blob("w_hood", (0, 1.8, 7.0), (6.2, 4.2, 6.8), robe)
    s = len(gt.PARTS)
    (sol if sun else nyx)(3)
    place(s, 1.25, (0, -2.2, 7.0))
    for sd in (-1, 1):   # the shoulders, and a floating hand each side
        if sun:
            for k in range(3):
                a = math.radians(60 + 30 * k)
                spike("w_pauldron%d_%d" % (sd, k), (sd * 4.2, 0, 1.2), (sd * math.sin(a), 0, math.cos(a)), 0.9, 3.0 - 0.6 * k, trim)
        else:
            tube("w_pauldron%d" % sd, [(sd * 2.6, 0, 3.4, 0.1), (sd * 5.2, 0, 2.2, 0.75), (sd * 6.2, 0, -0.8, 0.1)], trim)
        hx, hz = sd * 9.5, -4.0
        blob("w_hand%d" % sd, (hx, -1, hz), (1.5, 1.3, 1.8), trim)
        for k in range(3):
            spike("w_claw%d_%d" % (sd, k), (hx + sd * 0.4 * (k - 1), -1.2, hz - 1.4), (sd * 0.25 * (k - 1), -0.2, -1), 0.38, 2.2, trim)
        blob("w_wisp%d" % sd, (hx, -1.2, hz + 2.6), (0.6, 0.6, 0.6), glow, glow=True)
    if sun: ring("w_halo", lambda t: (9.6 * math.cos(2 * math.pi * t), 3.0, 7.0 + 9.6 * math.sin(2 * math.pi * t)), 56, "glow_g", 0.3)
    else:
        ring("w_halo", lambda t: (9.4 * math.cos(math.radians(110 + 230 * t)), 3.0, 7.0 + 9.4 * math.sin(math.radians(110 + 230 * t))), 30,
             "glow_s", lambda t: 0.12 + 0.5 * math.sin(math.pi * t), closed=False)
    GLOW.append(gt.PARTS[-1])
    return 34


def book():
    W, H, T = 12.0, 16.0, 3.4
    gt.box("bk_back", (0, T / 2 - 0.4, 0), (W, 0.8, H), "cover_g", bevel=0.2, segs=1)
    gt.box("bk_pages", (0.4, 0, 0), (W - 1.2, T - 1.2, H - 1.2), "page")
    gt.box("bk_front_g", (-W / 4, -T / 2 + 0.4, 0), (W / 2, 0.8, H), "cover_g", bevel=0.2, segs=1)
    gt.box("bk_front_s", (W / 4, -T / 2 + 0.4, 0), (W / 2, 0.8, H), "cover_s", bevel=0.2, segs=1)
    gt.box("bk_spine", (-W / 2 + 0.3, 0, 0), (0.9, T, H), "cover_g", bevel=0.2, segs=1)
    for sx, reg in ((-1, "gold_d"), (1, "silver_d")):
        for sz in (-1, 1):
            gt.box("bk_corner%d%d" % (sx, sz), (sx * (W / 2 - 1.0), -T / 2 + 0.05, sz * (H / 2 - 1.0)), (2.2, 0.5, 2.2), reg, bevel=0.2, segs=1)
    gt.box("bk_clasp", (W / 2 + 0.2, -0.4, 0), (0.8, T - 0.6, 2.4), "silver", bevel=0.15, segs=1)
    tube("bk_seam", [(0, -T / 2 - 0.15, -H / 2 + 0.6, 0.22), (0, -T / 2 - 0.2, H / 2 - 0.6, 0.22)], "void")
    s = len(gt.PARTS); sol(2); place(s, 0.42, (-W / 4, -T / 2 - 0.3, 3.0))
    s = len(gt.PARTS); nyx(2); place(s, 0.42, (W / 4, -T / 2 - 0.3, 3.0))
    blob("bk_eclipse", (0, -T / 2 - 0.3, -4.2), (1.2, 0.3, 1.2), "void", segs=14)
    ring("bk_ecl_g", lambda t: (-1.5 * math.sin(math.pi * t), -T / 2 - 0.35, -4.2 + 1.5 * math.cos(math.pi * t)), 14, "glow_g", 0.16, glow=True, closed=False)
    ring("bk_ecl_s", lambda t: (1.5 * math.sin(math.pi * t), -T / 2 - 0.35, -4.2 + 1.5 * math.cos(math.pi * t)), 14, "glow_s", 0.16, glow=True, closed=False)
    return H


def player():
    """A plain player figure wearing Sol, with Nyx hanging at the hip - to show how the masks sit and their size."""
    gt.box("pl_head", (0, 0, 28), (8, 8, 8), "skin"); gt.box("pl_hair", (0, 0.5, 31.2), (8.2, 7.4, 1.8), "hair")
    gt.box("pl_body", (0, 0, 18), (8, 4, 12), "shirt")
    for s in (-1, 1):
        gt.box("pl_arm%d" % s, (s * 6, 0, 18), (4, 4, 12), "shirt" if s < 0 else "skin")
        gt.box("pl_leg%d" % s, (s * 2, 0, 6), (4, 4, 12), "pants")
    gt.box("pl_belt", (0, 0, 12.6), (8.3, 4.3, 1.2), "cord")
    s = len(gt.PARTS); sol(3); place(s, 0.92, (0, -4.05, 28.2))
    tube("pl_cord", [(2.6, -2.3, 12.4, 0.16), (2.9, -2.5, 11.0, 0.16), (3.0, -2.6, 9.9, 0.16)], "cord")
    s = len(gt.PARTS); nyx(1); place(s, 0.5, (3.0, -2.25, 7.4))
    return 32


MODELS = {"book": book, "sol_1": lambda: sol(1), "sol_2": lambda: sol(2), "sol_3": lambda: sol(3),
          "nyx_1": lambda: nyx(1), "nyx_2": lambda: nyx(2), "nyx_3": lambda: nyx(3), "eclipse": eclipse,
          "dawn_warden": lambda: warden(True), "dusk_warden": lambda: warden(False), "player": player}
NAMES = {"book": "The Twin Grimoire", "sol_1": "Sol I", "sol_2": "Sol II", "sol_3": "Sol III", "nyx_1": "Nyx I", "nyx_2": "Nyx II",
         "nyx_3": "Nyx III", "eclipse": "Eclipse Union", "dawn_warden": "The Dawn Warden (trial)", "dusk_warden": "The Dusk Warden (trial)",
         "player": "Worn: Sol on the face, Nyx at the hip"}


# ------------------------------------------------------------------ build and render
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
        bsdf.inputs["Roughness"].default_value = 0.45
        if not glow: bsdf.inputs["Metallic"].default_value = 0.35
        if glow:
            bsdf.inputs["Base Color"].default_value = (0, 0, 0, 1)
            nt.links.new(img.outputs["Color"], bsdf.inputs["Emission Color"]); bsdf.inputs["Emission Strength"].default_value = 1.4
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


ORDER = ["book", "sol_1", "sol_2", "sol_3", "eclipse", "nyx_1", "nyx_2", "nyx_3", "dawn_warden", "dusk_warden", "player"]


def render_sheet(out_dir, only=None):
    forms = [f for f in ORDER if not only or f in only]
    CW, CH = 420, 480
    for form in forms:
        ob = build(form, out_dir)
        ob.rotation_euler = (0, 0, math.radians(-24 if form != "player" else -16))
        sc = bpy.context.scene
        sc.render.engine = "CYCLES"; sc.cycles.samples = 24; sc.cycles.device = "CPU"
        sc.view_settings.view_transform = "Standard"
        sc.render.resolution_x, sc.render.resolution_y = CW, CH
        world = bpy.data.worlds.new("w"); sc.world = world; world.use_nodes = True
        world.node_tree.nodes["Background"].inputs[0].default_value = (0.03, 0.035, 0.06, 1)
        for name, rot, energy in (("key", (math.radians(50), 0, math.radians(-35)), 4.0), ("fill", (math.radians(70), 0, math.radians(150)), 1.6),
                                  ("top", (math.radians(10), 0, 0), 1.2)):
            L = bpy.data.lights.new(name, "SUN"); L.energy = energy
            o = bpy.data.objects.new(name, L); o.rotation_euler = rot; sc.collection.objects.link(o)
        bpy.context.view_layer.update()
        ws = [ob.matrix_world @ v.co for v in ob.data.vertices]
        xs, zs = [w.x for w in ws], [w.z for w in ws]
        w, h = max(xs) - min(xs), max(zs) - min(zs)
        cam = bpy.data.cameras.new("cam"); cam.type = "ORTHO"; cam.ortho_scale = max(h, w * CH / CW) * 1.18
        co = bpy.data.objects.new("cam", cam); sc.collection.objects.link(co); sc.camera = co
        co.location = ((min(xs) + max(xs)) / 2, -80, (min(zs) + max(zs)) / 2 - h * 0.03); co.rotation_euler = (math.radians(90), 0, 0)
        sc.render.filepath = os.path.join(out_dir, "twins_%s.png" % form)
        bpy.ops.render.render(write_still=True)
    cols = 4
    rows = (len(forms) + cols - 1) // cols
    sheet = Image.new("RGB", (CW * cols, CH * rows), (8, 9, 16))
    d = ImageDraw.Draw(sheet)
    for i, form in enumerate(forms):
        x, y = (i % cols) * CW, (i // cols) * CH
        sheet.paste(Image.open(os.path.join(out_dir, "twins_%s.png" % form)).convert("RGB"), (x, y))
        d.text((x + 10, y + 8), NAMES[form], fill=(235, 228, 210))
    sheet.save(os.path.join(out_dir, "masked_twins_sheet.png"))


# ------------------------------------------------------------------ into the game
GAME = ["sol_1", "sol_2", "sol_3", "nyx_1", "nyx_2", "nyx_3", "eclipse", "dawn_warden", "dusk_warden"]   # the form numbers the game uses (TwinsFx)
TARGETS = {"eclipse": 3200, "dawn_warden": 4200, "dusk_warden": 4600}
GLOW_KEEP = 200


def slim(target):
    """Fewer triangles: the big smooth shells and robes are thinned, the small pieces (eyes, gems, rays, glow) kept."""
    total = sum(tris(o) for o in gt.PARTS)
    if total <= target: return
    big = [o for o in gt.PARTS if tris(o) > GLOW_KEEP and o not in GLOW]
    small = total - sum(tris(o) for o in big)
    ratio = max(0.2, (target - small) / max(1, total - small))
    for ob in big:
        m = ob.modifiers.new("slim", "DECIMATE"); m.decimate_type = "COLLAPSE"; m.ratio = ratio
        apply_mod(ob, m)


def to_game(out_dir):
    """Every mask (and each warden) into the mod as twin_mask_<name>, then the item's own model: Sol I, with an override
    per form ("iron_spell_grimoire:form" = form / 16) pointing at the others."""
    import json
    for i, form in enumerate(GAME):
        bpy.ops.wm.read_factory_settings(use_empty=True)
        gt.PARTS.clear(); GLOW.clear()
        MODELS[form]()
        for ob in list(gt.PARTS):
            if ob.type != "MESH":
                bpy.ops.object.select_all(action="DESELECT"); ob.select_set(True); bpy.context.view_layer.objects.active = ob
                bpy.ops.object.convert(target="MESH")
        slim(TARGETS.get(form, 2400))
        glow_m, plain_m = bpy.data.materials.new(form + "_glow"), bpy.data.materials.new(form)
        for ob in gt.PARTS: ob.data.materials.clear(); ob.data.materials.append(glow_m if ob in GLOW else plain_m)
        bpy.ops.object.select_all(action="DESELECT")
        for ob in gt.PARTS: ob.select_set(True)
        bpy.context.view_layer.objects.active = gt.PARTS[0]
        bpy.ops.object.join()
        ob = bpy.context.view_layer.objects.active
        name = "twin_mask_" + form
        ob.name = name
        ob.location = (8, -8, 8)   # the model's middle at the block's middle
        bpy.ops.wm.obj_export(filepath=os.path.join(out_dir, name + ".obj"), export_selected_objects=True, export_uv=True, export_normals=True,
                              export_materials=True, export_triangulated_mesh=True, forward_axis="NEGATIVE_Z", up_axis="Y", global_scale=1 / 16, path_mode="STRIP")
        print(form, "tris", tris(ob))
        warden = form.endswith("warden")
        gui = 0.5 if warden else 1.2
        disp = {"gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [gui] * 3},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5] * 3},
                "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [gui] * 3}}
        gt.export_to_game(name, out_dir, disp, glowing={form + "_glow"}, textures={form: TEX, form + "_glow": TEX}, render_type="minecraft:cutout")
    md = os.path.join(gt.ASSETS, "models", "item")
    base = json.load(open(os.path.join(md, "twin_mask_sol_1.json")))
    base["overrides"] = [{"predicate": {"iron_spell_grimoire:form": round(i / 16, 5)}, "model": "iron_spell_grimoire:item/twin_mask_" + f}
                         for i, f in enumerate(GAME) if i > 0]
    json.dump(base, open(os.path.join(md, "twin_mask.json"), "w"), indent=1)


if __name__ == "__main__":
    mode, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    paint(os.path.join(out, TEX + ".png"))
    if mode == "render": render_sheet(out, sys.argv[3].split(",") if len(sys.argv) > 3 else None)
    if mode == "game": LOW = True; to_game(out)
