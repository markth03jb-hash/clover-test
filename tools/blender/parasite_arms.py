"""The Parasite Grimoire's arm forms, modelled in Blender (run with Blender's Python: bpy) - each a held "form item":
the arm from the elbow down, overgrown by the parasite and reshaped. Original designs, in dark red flesh, black bone and
chitin, with glowing red veins.

Forms (each its own model):
  reaper_edge      a long curved bone blade growing out of the wrist
  rending_talons   a clawed hand: four long hooked talons and a thumb claw
  spine_launcher   the arm grown into a bone cannon that fires volleys of bone spines
  titan_fist       a swollen fist, bone-knuckled and plated
  bone_lance       a long straight bone spear, ringed and spiralled
  bulwark          a broad chitin shield grown over the forearm (held in the off hand)

Blender space (pixels; exported at 1/16): Z along the arm (the hand at the origin, forward +Z, the elbow at -9), X across,
Y through. Like the weapons, the hand - the "grip" - sits at the model's middle.

  python tools/blender/parasite_arms.py render OUT_DIR    a sheet of every form, front and side
  python tools/blender/parasite_arms.py game OUT_DIR      writes them into the mod (models/item, textures/item)
"""
import math, os, random, sys
import bpy, bmesh
from mathutils import Vector, Matrix
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gilded_thorn as gt

TEX = "parasite_arms"
gt.REG.clear()
gt.REG.update({"flesh": (0, 0, 128, 64), "flesh2": (128, 0, 256, 64), "vein": (0, 64, 32, 96), "blade": (32, 64, 256, 128),
               "claw": (0, 128, 128, 160), "tendril": (128, 128, 256, 160), "bone": (0, 160, 128, 192), "chitin": (128, 160, 256, 224),
               "eye": (0, 192, 32, 224), "sleeve": (32, 192, 128, 256), "dummy": (128, 224, 160, 256),
               "redplate": (160, 224, 256, 256), "greyblade": (32, 96, 256, 128)})
REG = gt.REG
GLOW = []


# ------------------------------------------------------------------ the texture
def mix(a, b, t): t = max(0.0, min(1.0, t)); return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def paint(out):
    rnd = random.Random(11)
    im = Image.new("RGBA", (256, 256), (0, 0, 0, 255)); px = im.load()
    def region(name, fn):
        x0, y0, x1, y1 = REG[name]
        for x in range(x0, x1):
            for y in range(y0, y1):
                px[x, y] = fn((x - x0) / (x1 - x0 - 1), (y - y0) / (y1 - y0 - 1), x - x0, y - y0) + (255,)
    MUSCLE, MUSCLE_D, SINEW = (118, 16, 26), (52, 6, 12), (170, 44, 48)
    def flesh(u, v, x, y):   # muscle fibres running along the arm (along u), with darker sinew between
        f = 0.5 + 0.5 * math.sin(y * 1.3 + math.sin(x * 0.15) * 2.5)
        c = mix(MUSCLE_D, MUSCLE, f ** 1.5)
        if f > 0.93: c = mix(c, SINEW, 0.6)
        return mix(c, (20, 2, 6), 0.25 * (0.5 + 0.5 * math.sin(x * 0.07 + y * 0.2)))
    region("flesh", flesh)
    def sleeve_tex(u, v, x, y):   # wrapped round the forearm (u round it, v along it): fibres running along the arm
        f = 0.5 + 0.5 * math.sin(x * 1.1 + math.sin(y * 0.18) * 2.0)
        c = mix(MUSCLE_D, MUSCLE, f ** 1.6)
        if f > 0.94: c = mix(c, SINEW, 0.55)
        return mix(c, (20, 2, 6), 0.3 * (0.5 + 0.5 * math.sin(y * 0.09 + x * 0.3)))
    region("sleeve", sleeve_tex)
    region("flesh2", lambda u, v, x, y: mix(flesh(u, v, x, y), (24, 4, 8), 0.45))
    region("vein", lambda u, v, x, y: mix((255, 120, 110), (230, 24, 36), abs(v - 0.5) * 2))
    def blade(u, v, x, y):   # black bone, a red cutting edge along its far side (v -> 1), red veins running along it
        c = mix((14, 10, 12), (44, 30, 34), 0.5 + 0.5 * math.sin(x * 0.11 + y * 0.6))
        if v > 0.78: c = mix(c, (220, 30, 40), (v - 0.78) / 0.22)
        if v > 0.95: c = (255, 110, 100)
        if abs(v - 0.45 - 0.08 * math.sin(u * 14)) < 0.03 and u < 0.85: c = (190, 20, 34)
        return c
    region("blade", blade)
    region("claw", lambda u, v, x, y: mix((16, 12, 14), (230, 40, 46), max(0.0, (u - 0.55) / 0.45) ** 1.3))   # black, reddening to the tips
    def tendril(u, v, x, y):   # ringed muscle: soft bands across it, darker toward its edges
        band = 0.5 + 0.5 * math.sin(x * 0.55)
        c = mix(MUSCLE_D, MUSCLE, 0.35 + 0.5 * band * (1 - abs(v - 0.5) * 1.6))
        if band < 0.08: c = mix(c, (20, 2, 6), 0.7)
        return c
    region("tendril", tendril)
    region("bone", lambda u, v, x, y: mix((40, 34, 36), (12, 9, 11), 0.5 + 0.5 * math.sin(x * 0.4 + y * 0.9)) if (x + y * 3) % 23 else (90, 70, 74))
    seeds = [(rnd.uniform(0, 128), rnd.uniform(0, 64)) for _ in range(26)]
    def chitin(u, v, x, y):   # glossy black plates of uneven size, red seams between them, lit toward one edge
        d = sorted(((x - sx) ** 2 + (y - sy) ** 2, i) for i, (sx, sy) in enumerate(seeds))
        if math.sqrt(d[1][0]) - math.sqrt(d[0][0]) < 1.6: return (140, 16, 26)
        sx, sy = seeds[d[0][1]]
        return mix((12, 9, 13), (66, 56, 70), max(0.0, 1 - math.sqrt(d[0][0]) / 14) ** 1.5)
    region("chitin", chitin)
    region("dummy", lambda u, v, x, y: (96, 100, 112))
    def redplate(u, v, x, y):   # glossy dark-red armour: lit toward one edge, darker grooves across it
        c = mix((60, 4, 10), (170, 22, 30), (1 - v) ** 1.4)
        if (x + int(6 * math.sin(y * 0.4))) % 18 == 0: c = mix(c, (24, 0, 4), 0.8)
        return c
    region("redplate", redplate)
    def greyblade(u, v, x, y):   # dark grey bone-steel, mottled, a pale honed edge along its outer curve (v -> 0)
        c = mix((46, 46, 52), (88, 88, 96), 0.5 + 0.5 * math.sin(x * 0.07 + y * 0.9) * math.sin(x * 0.023))
        if v < 0.16: c = mix((200, 202, 210), c, v / 0.16)
        return c
    region("greyblade", greyblade)
    region("eye", lambda u, v, x, y: mix((255, 220, 200), (200, 10, 20), min(1, math.hypot(u - 0.5, v - 0.5) * 2.4)))
    im.save(out)


# ------------------------------------------------------------------ shared pieces
def tube(name, pts, region, flat=1.0, res=5, bev=3, glow=False):
    o = gt.curve_obj(name, pts, flat=flat, region=region, res=res, bev=bev)
    if glow: GLOW.append(o)
    return o


ARM = 4.0   # a Minecraft arm is 4 x 4 pixels, 12 long: the forearm (the lower 8) is what the parasite overgrows


def turn_since(mark, angle):
    """Turn every part made since `mark` (an index into gt.PARTS) round the arm's axis by angle (radians)."""
    m = Matrix.Rotation(angle, 4, "Z")
    for ob in gt.PARTS[mark:]: ob.data.transform(m)


def sleeve():
    """The forearm of a Minecraft arm, overgrown: a block of flesh a little over the arm's 4 x 4 (so it sits over it),
    from the elbow (8 px up) to the wrist, its edges softened; tendrils wound round it; glowing veins on its faces;
    bone spurs along its back; and a knotted mass at the wrist, wider than the arm, that the form grows out of."""
    W = ARM + 0.9
    gt.box("sleeve", (0, 0, -4.0), (W, W, 8.0), "flesh", bevel=0.6, segs=2)
    for k, ph in enumerate((0.0, math.pi)):   # two tendrils winding up it
        pts = []
        for i in range(14):
            t = i / 13
            a = ph + t * math.pi * 2.4
            r = W * 0.62
            pts.append((r * math.cos(a), r * math.sin(a), -7.8 + 7.8 * t, 0.42 - 0.1 * t))
        tube("wind%d" % k, pts, "flesh2", res=3, bev=2)
    for k, (fx, fy) in enumerate(((1, 0.3), (-1, -0.2), (0.25, 1), (-0.3, -1))):   # veins glowing on its faces
        pts = []
        for i in range(8):
            t = i / 7
            sway = 0.7 * math.sin(t * 5 + k)
            x = fx * W / 2 * 1.02 if abs(fx) == 1 else fx * W / 2 + sway
            y = fy * W / 2 * 1.02 if abs(fy) == 1 else fy * W / 2 + sway
            pts.append((x, y, -7.4 + 7.0 * t, 0.15))
        tube("vein%d" % k, pts, "vein", res=2, bev=1, glow=True)
    for k in range(3):   # bone spurs along the back of the forearm
        z = -7.0 + k * 2.5
        gt.cone("spur%d" % k, (-W / 2, 0, z), (-0.9, 0, -0.5), 0.5, 2.0 - k * 0.3, region="bone")
    bm = bmesh.new()   # the knotted wrist, wider than the arm
    bmesh.ops.create_uvsphere(bm, u_segments=12, v_segments=8, radius=3.2)
    bmesh.ops.scale(bm, vec=(1.0, 0.95, 0.7), verts=bm.verts)
    bmesh.ops.translate(bm, vec=(0, 0, 0.6), verts=bm.verts)
    gt.new_obj("wrist", bm, REG["flesh2"])


def armour():
    """Over the forearm: glossy red armour plates, fins sweeping back and out along it, red tendrils standing off it."""
    W = ARM + 0.9
    gt.box("plate_top", (0, 0, -3.6), (W + 0.6, W + 0.6, 6.2), "redplate", bevel=0.8, segs=2)
    for k, (z, L, out) in enumerate(((0.4, 8.0, 1.6), (-2.2, 9.0, 1.9), (-4.8, 7.6, 1.6), (-7.0, 5.6, 1.2))):   # big fins along the outside, flaring back
        f = tube("fin%d" % k, [(W / 2 - 0.4, 0, z, 1.5), (W / 2 + 1.8 * out, 0.2, z - L * 0.3, 1.2), (W / 2 + 3.0 * out, 0.4, z - L * 0.7, 0.6),
                               (W / 2 + 3.3 * out, 0.5, z - L, 0.03)], "redplate", res=4, bev=2)
        f.data.transform(Matrix.Scale(0.3, 4, (0, 1, 0)))
    for k, (z, L) in enumerate(((-1.0, 5.0), (-4.0, 5.5))):   # and a pair flaring off the inner side
        f = tube("ifin%d" % k, [(-W / 2 + 0.4, 0, z, 1.1), (-W / 2 - 1.8, 0.2, z - L * 0.4, 0.8), (-W / 2 - 2.8, 0.4, z - L, 0.03)], "redplate", res=4, bev=2)
        f.data.transform(Matrix.Scale(0.3, 4, (0, 1, 0)))
    for k, (z, L) in enumerate(((-1.4, 5.6), (-4.4, 6.4))):   # and up its top
        f = tube("tfin%d" % k, [(0, W / 2, z, 1.2), (0.3, W / 2 + 2.0, z - L * 0.4, 0.8), (0.4, W / 2 + 3.4, z - L, 0.03)], "redplate", res=4, bev=2)
        f.data.transform(Matrix.Scale(0.4, 4, (1, 0, 0)))
    for k, ph in enumerate((0.4, 3.4)):   # loose tendrils standing off it
        pts = []
        for i in range(10):
            t = i / 9
            a = ph + t * math.pi * 1.4
            r = W * 0.68 + 0.5 * math.sin(t * math.pi)
            pts.append((r * math.cos(a), r * math.sin(a), -8.0 + 9.0 * t, 0.28))
        tube("loose%d" % k, pts, "redplate", res=3, bev=2)


def braid():
    """Over the forearm: a braid of red and black tendrils twisting round it, standing off it, grey spikes up its top."""
    W = ARM + 0.9
    for k in range(6):
        ph = k * math.pi / 3
        pts = []
        for i in range(16):
            t = i / 15
            a = ph + t * math.pi * (1.6 if k % 2 else -1.6)
            r = W * 0.6 + 0.45 * (1 + math.sin(t * math.pi * 3 + k))
            pts.append((r * math.cos(a), r * math.sin(a), -9.0 + 10.5 * t, 0.42))
        tube("braid%d" % k, pts, "redplate" if k % 2 == 0 else "flesh2", res=2, bev=1)
    for k, (z, L) in enumerate(((-7.0, 2.4), (-4.6, 3.0), (-2.2, 2.6), (0.0, 2.0))):   # grey spikes up its top
        gt.cone("tspike%d" % k, (0, W / 2 + 0.3, z), (0, 1, -0.55), 0.6, L, region="greyblade")


def dummy():
    """For the previews only: a Minecraft-sized figure the arm belongs to - its upper arm (the 4 px above the elbow),
    body, head, legs and other arm - so the forms are seen at their true size. Built hanging from the shoulder: the
    arm runs -Z to the shoulder at -12 (the render is turned over so it stands)."""
    for name, c, sz in (("d_upper", (0, 0, -10.0), (ARM, ARM, 4.0)), ("d_body", (6.0, 0, -6.0), (8, 4, 12)), ("d_head", (6.0, 0, -16.0), (8, 8, 8)),
                        ("d_leg1", (4.0, 0, 6.0), (4, 4, 12)), ("d_leg2", (8.0, 0, 6.0), (4, 4, 12)), ("d_arm2", (12.0, 0, -6.0), (4, 4, 12))):
        gt.box(name, c, sz, "dummy")


# ------------------------------------------------------------------ the forms
def reaper_edge():
    """A great hooked blade sweeping forward out from under the wrist - broad at its root, curving into a point -
    dark grey bone-steel with a pale edge along its outer curve; the forearm clad in red armour."""
    sleeve()
    armour()
    mark = len(gt.PARTS)
    pts = [(-1.2, 0, -1.0, 3.0), (-0.2, 0, 4.0, 3.8), (1.6, 0, 10.0, 3.7), (3.9, 0, 16.0, 3.0), (6.0, 0, 21.0, 2.0), (7.6, 0, 25.0, 0.9), (8.2, 0, 27.4, 0.04)]
    tube("blade", pts, "greyblade", flat=0.17, res=7, bev=4)
    for k, (z, s_) in enumerate(((3.0, 1.6), (6.5, 1.1))):   # a couple of bone hooks off its back near the root
        gt.cone("bhook%d" % k, (-3.4 + 0.3 * z, 0, z), (-1.0, 0, 0.3), 0.45, s_, region="greyblade")
    turn_since(mark, -math.pi / 2)   # its edge forward (-Y, the way you face and swing), its flat facing out from the body
    return 34.0


def rending_talons():
    """Long, narrow finger-blades reaching straight out from armoured knuckles, slightly curved, with knuckle joints;
    a hooked thumb blade curving down below the hand; the forearm a braid of red and black tendrils."""
    sleeve()
    braid()
    mark = len(gt.PARTS)
    bm = bmesh.new()   # the hand
    bmesh.ops.create_uvsphere(bm, u_segments=12, v_segments=8, radius=3.1)
    bmesh.ops.scale(bm, vec=(1.05, 0.85, 0.85), verts=bm.verts)
    bmesh.ops.translate(bm, vec=(0, 0, 2.0), verts=bm.verts)
    gt.new_obj("hand", bm, REG["flesh2"])
    gt.box("knuckle_plate", (0, 0, 4.0), (7.0, 4.2, 1.6), "redplate", bevel=0.5, segs=2)
    for k, (x, L) in enumerate(((-2.4, 15.5), (-0.8, 18.5), (0.8, 19.0), (2.4, 16.5))):
        spread = x * 0.09   # the fingers fan out a little toward their tips
        # armoured finger segments: two chitin plates, then the blade
        for j, (z, sz) in enumerate(((5.6, 2.2), (7.8, 1.9))):   # on the same line the blade follows, so they all join up
            gt.box("phal%d_%d" % (k, j), (x + spread * (z - 5), -0.2 * j, z), (1.5, 1.6 - 0.2 * j, sz), "chitin", bevel=0.25, segs=1)
        bm = bmesh.new()   # a knuckle joint
        bmesh.ops.create_uvsphere(bm, u_segments=8, v_segments=6, radius=0.95)
        bmesh.ops.translate(bm, vec=(x, 0, 4.8), verts=bm.verts)
        gt.new_obj("joint%d" % k, bm, REG["redplate"])
        z0 = 7.6   # the blade starts inside the last finger segment, so it grows out of it
        def X(z): return x + spread * (z - 5)
        # the blade: swelling in its middle, curving into a hooked point (toward -Y here; the hand is turned below so
        # the hooks curve in toward the body), flattened across X
        pts = [(X(z0), -0.2, z0, 1.2), (X(z0 + L * 0.3), -0.9, z0 + L * 0.3, 1.55), (X(z0 + L * 0.6), -2.2, z0 + L * 0.6, 1.5),
               (X(z0 + L * 0.82), -4.2, z0 + L * 0.82, 0.95), (X(z0 + L * 0.95), -6.0, z0 + L * 0.93, 0.4), (X(z0 + L), -7.2, z0 + L * 0.96, 0.03)]
        c = tube("fblade%d" % k, pts, "greyblade", res=5, bev=2)
        cx = X(z0)   # flattened about its root, so the root stays in the socket
        c.data.transform(Matrix.Translation((cx, 0, 0)) @ Matrix.Scale(0.28, 4, (1, 0, 0)) @ Matrix.Translation((-cx, 0, 0)))
        sz = 8.9   # a chitin socket round the blade's root, joining it to the finger
        bm = bmesh.new()
        bmesh.ops.create_cone(bm, cap_ends=True, segments=8, radius1=1.0, radius2=0.75, depth=1.6)
        bmesh.ops.scale(bm, vec=(1.0, 1.3, 1.0), verts=bm.verts)
        bmesh.ops.translate(bm, vec=(X(z0), -0.3, sz), verts=bm.verts)
        gt.new_obj("socket%d" % k, bm, REG["chitin"])
        bz = z0 + L * 0.45   # a barb on its back
        gt.cone("fbarb%d" % k, (X(bz), 0.2, bz), (0, 0.9, -0.6), 0.35, 1.5, region="greyblade")
    # the hooked thumb blade, curving down and back below the hand
    t = tube("thook", [(2.6, -2.0, 1.0, 1.1), (3.0, -4.2, 3.0, 0.95), (3.0, -6.0, 6.5, 0.6), (2.6, -6.2, 9.6, 0.25), (1.8, -5.2, 11.0, 0.03)],
             "greyblade", res=5, bev=3)
    t.data.transform(Matrix.Translation((2.8, 0, 0)) @ Matrix.Scale(0.32, 4, (1, 0, 0)) @ Matrix.Translation((-2.8, 0, 0)))
    turn_since(mark, math.pi / 2)   # the whole hand turned: the hooks curve in toward the body (+X)
    return 28.0


def spine_launcher():
    """The arm grown into a bone cannon: a ringed barrel out of the wrist, a glowing red core at its muzzle, fins along
    its sides, and a bundle of spines loaded along its top, ready to fire."""
    sleeve()
    armour()
    bm = bmesh.new()   # the barrel
    bmesh.ops.create_cone(bm, cap_ends=True, segments=12, radius1=2.9, radius2=2.3, depth=11.0)
    bmesh.ops.translate(bm, vec=(0, 0, 6.2), verts=bm.verts)
    gt.new_obj("barrel", bm, REG["greyblade"])
    for k in range(4):   # chitin rings round it
        gt.torus("bring%d" % k, (0, 0, 2.2 + k * 2.8), (0, 0, 1), 2.85 - k * 0.15, 0.45, region="redplate")
    gt.torus("muzzle", (0, 0, 11.7), (0, 0, 1), 2.25, 0.6, region="redplate")
    bm = bmesh.new()   # the glowing core, deep in the muzzle
    bmesh.ops.create_uvsphere(bm, u_segments=12, v_segments=8, radius=1.7)
    bmesh.ops.scale(bm, vec=(1, 1, 0.5), verts=bm.verts)
    bmesh.ops.translate(bm, vec=(0, 0, 11.4), verts=bm.verts)
    GLOW.append(gt.new_obj("core", bm, REG["eye"]))
    for sd in (1, -1):   # fins along its sides
        f = tube("fin%d" % sd, [(sd * 2.6, 0, 1.6, 1.0), (sd * 4.0, 0, 4.5, 0.8), (sd * 3.6, 0, 8.0, 0.45), (sd * 2.8, 0, 10.4, 0.04)], "redplate", res=4, bev=2)
        f.data.transform(Matrix.Scale(0.35, 4, (0, 1, 0)))
    for k, (x, y) in enumerate(((-0.9, 2.9), (0.0, 3.2), (0.9, 2.9), (-0.45, 3.6), (0.45, 3.6))):   # spines loaded along its top
        gt.cone("load%d" % k, (x, y, 2.0 + 0.3 * k), (0, 0.05, 1), 0.42, 9.0 - k * 0.5, region="greyblade")
    for k, a in enumerate((0.5, 2.1, 3.7, 5.2)):   # veins running up the barrel
        pts = [(2.9 * math.cos(a + 0.2 * i), 2.9 * math.sin(a + 0.2 * i), 1.2 + i * 2.4, 0.15) for i in range(5)]
        tube("bvein%d" % k, pts, "vein", res=2, bev=1, glow=True)
    return 26.0


def titan_fist():
    sleeve()
    armour()
    bm = bmesh.new()   # the swollen fist
    bmesh.ops.create_uvsphere(bm, u_segments=14, v_segments=10, radius=4.8)
    bmesh.ops.scale(bm, vec=(1.05, 0.95, 1.05), verts=bm.verts)
    bmesh.ops.translate(bm, vec=(0, 0, 4.4), verts=bm.verts)
    gt.new_obj("fist", bm, REG["flesh"])
    for k, (x, z, r) in enumerate(((-3.2, 1.2, 2.2), (3.2, 1.4, 2.1), (0.0, 0.6, 2.6))):   # muscle swelling up out of the wrist
        bm = bmesh.new()
        bmesh.ops.create_uvsphere(bm, u_segments=10, v_segments=7, radius=r)
        bmesh.ops.translate(bm, vec=(x, 0.3, z), verts=bm.verts)
        gt.new_obj("bulge%d" % k, bm, REG["flesh2"])
    for k, x in enumerate((-3.0, -1.0, 1.0, 3.0)):   # bone knuckles
        bm = bmesh.new()
        bmesh.ops.create_uvsphere(bm, u_segments=8, v_segments=6, radius=1.45)
        bmesh.ops.translate(bm, vec=(x, -0.4, 8.6), verts=bm.verts)
        gt.new_obj("knuckle%d" % k, bm, REG["greyblade"])
        gt.cone("kspike%d" % k, (x, -0.4, 9.6), (0, -0.1, 1), 0.6, 2.2, region="greyblade")
    for k, z in enumerate((1.8, 4.2, 6.6)):   # red armour plates wrapping round its back and sides
        for j_, a in enumerate((-0.75, 0.0, 0.75)):
            bm = bmesh.new()
            bmesh.ops.create_cube(bm, size=1.0)
            bmesh.ops.scale(bm, vec=(3.0 - abs(a) * 0.8, 1.0, 2.2), verts=bm.verts)
            bmesh.ops.bevel(bm, geom=bm.verts[:] + bm.edges[:], offset=0.3, segments=1, affect="EDGES", profile=0.5)
            r = 4.7 - k * 0.2
            bmesh.ops.translate(bm, vec=(0, -r, 0), verts=bm.verts)
            bmesh.ops.rotate(bm, cent=(0, 0, 0), matrix=Matrix.Rotation(a, 3, "Z"), verts=bm.verts)
            bmesh.ops.translate(bm, vec=(0, 0, z), verts=bm.verts)
            gt.new_obj("plate%d_%d" % (k, j_), bm, REG["redplate"])
    for k, x in enumerate((-2.6, 2.6)):   # veins bulging up its sides
        tube("fvein%d" % k, [(x * 1.2, 1.0, 1.0, 0.18), (x * 1.75, 1.4, 4.0, 0.2), (x * 1.6, 1.1, 7.0, 0.14)], "vein", res=2, bev=1, glow=True)
    return 28.0


def bone_lance():
    sleeve()
    armour()
    tube("lance", [(0, 0, 1.0, 2.1), (0, 0, 9, 1.8), (0, 0, 21, 1.25), (0, 0, 30, 0.55), (0, 0, 34, 0.04)], "greyblade", res=6, bev=4)
    for k in range(6):   # rings of red armour up its length
        z = 4 + k * 4.6
        r = 2.05 - k * 0.24
        gt.torus("ring%d" % k, (0, 0, z), (0, 0, 1), r, 0.3, region="redplate")
    pts = []
    for i in range(40):   # a glowing groove spiralling up it
        t = i / 39
        z = 1.5 + 28 * t
        r = 2.06 - 1.4 * t
        a = t * math.pi * 9
        pts.append((r * math.cos(a), r * math.sin(a), z, 0.09))
    tube("groove", pts, "vein", res=1, bev=1, glow=True)
    return 40.0


def bulwark():
    sleeve()
    braid()
    bm = bmesh.new()   # a broad, domed shield of armour over the forearm
    bmesh.ops.create_uvsphere(bm, u_segments=20, v_segments=12, radius=1.0)
    bmesh.ops.scale(bm, vec=(8.0, 1.7, 11.0), verts=bm.verts)
    for v in bm.verts:
        if v.co.y > 0: v.co.y *= 0.35   # flatter at the back, against the arm
    bmesh.ops.translate(bm, vec=(0, -3.4, -2.0), verts=bm.verts)
    gt.new_obj("shield", bm, REG["redplate"])
    for k in range(10):   # spines round its rim
        a = k / 10 * math.pi * 2
        x, z = 8.0 * math.cos(a), 11.0 * math.sin(a) - 2.0
        gt.cone("rim%d" % k, (x * 0.95, -3.4, z * 0.95 + (-2.0) * 0.05), (math.cos(a), -0.3, math.sin(a)), 0.6, 2.4, region="greyblade")
    bm = bmesh.new()   # a red eye at its heart
    bmesh.ops.create_uvsphere(bm, u_segments=10, v_segments=6, radius=1.6)
    bmesh.ops.scale(bm, vec=(1, 0.5, 1), verts=bm.verts)
    bmesh.ops.translate(bm, vec=(0, -5.3, -1.5), verts=bm.verts)
    GLOW.append(gt.new_obj("eye", bm, REG["eye"]))
    for k, a in enumerate((0.3, 1.2, 2.0, 2.9, 3.7, 4.6, 5.4)):   # veins radiating from it
        pts = [(1.6 * math.cos(a), -5.1, -1.5 + 1.6 * math.sin(a), 0.15), (4.2 * math.cos(a + 0.2), -4.6, -1.5 + 5.8 * math.sin(a + 0.2), 0.15),
               (6.6 * math.cos(a + 0.35), -3.6, -1.5 + 9.2 * math.sin(a + 0.35), 0.1)]
        tube("svein%d" % k, pts, "vein", res=2, bev=1, glow=True)
    return 22.0


FORMS = {"reaper_edge": reaper_edge, "rending_talons": rending_talons, "spine_launcher": spine_launcher,
         "titan_fist": titan_fist, "bone_lance": bone_lance, "bulwark": bulwark}


TARGET = 3500   # triangles a form may have in game


def tris(ob): return sum(len(p.vertices) - 2 for p in ob.data.polygons)


def slim(target):
    """Bring the form down to about `target` triangles: curves made into meshes, doubled points merged, then the
    heavy parts (never the small ones - a spike or a ring stays whole) collapsed by the same share until it fits."""
    for ob in list(gt.PARTS):
        if ob.type != "MESH":
            bpy.ops.object.select_all(action="DESELECT"); ob.select_set(True); bpy.context.view_layer.objects.active = ob
            bpy.ops.object.convert(target="MESH")
    for ob in gt.PARTS:
        bm = bmesh.new(); bm.from_mesh(ob.data)
        bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=0.01)
        bm.to_mesh(ob.data); bm.free()
    total = sum(tris(o) for o in gt.PARTS)
    small = sum(tris(o) for o in gt.PARTS if tris(o) <= 80)
    if total <= target: return
    ratio = max(0.2, (target - small) / max(1, total - small))
    for ob in gt.PARTS:
        if tris(ob) <= 80: continue
        m = ob.modifiers.new("slim", "DECIMATE"); m.decimate_type = "COLLAPSE"; m.ratio = ratio
        bpy.ops.object.select_all(action="DESELECT"); ob.select_set(True); bpy.context.view_layer.objects.active = ob
        bpy.ops.object.modifier_apply(modifier="slim")


def build(form, out_dir, with_dummy=False, game=False):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    gt.PARTS.clear(); GLOW.clear()
    tex = os.path.join(out_dir, TEX + ".png")
    length = FORMS[form]()
    slim(TARGET)
    if with_dummy: dummy()
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
    if game:   # the game draws it on the right arm: these are built on a left one, so mirrored (toward the body stays toward the body)
        ob.scale = (-1, 1, 1)
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
        bm = bmesh.new(); bm.from_mesh(ob.data)
        bmesh.ops.reverse_faces(bm, faces=bm.faces)
        bm.to_mesh(ob.data); bm.free()
    else: ob.location = (8, -8, 8)
    bpy.ops.wm.obj_export(filepath=os.path.join(out_dir, form + ".obj"), export_selected_objects=True, export_uv=True, export_normals=True,
                          export_materials=True, export_triangulated_mesh=True, forward_axis="NEGATIVE_Z", up_axis="Y", global_scale=1 / 16, path_mode="STRIP")
    print(form, "tris", tris(ob), "length", length)
    return ob, length


def render_sheet(out_dir):
    """Every form, front and side, on one sheet."""
    shots = []
    for form in FORMS:
        ob, length = build(form, out_dir, with_dummy=True)
        ob.location = (0, 0, 0)
        sc = bpy.context.scene
        sc.render.engine = "CYCLES"; sc.cycles.samples = 24; sc.cycles.device = "CPU"
        sc.view_settings.view_transform = "Standard"
        sc.render.resolution_x, sc.render.resolution_y = 420, 700
        world = bpy.data.worlds.new("w"); sc.world = world; world.use_nodes = True
        world.node_tree.nodes["Background"].inputs[0].default_value = (0.03, 0.035, 0.06, 1)
        for name, rot, energy in (("key", (math.radians(50), 0, math.radians(-35)), 4.0), ("fill", (math.radians(70), 0, math.radians(150)), 1.6)):
            L = bpy.data.lights.new(name, "SUN"); L.energy = energy
            o = bpy.data.objects.new(name, L); o.rotation_euler = rot; sc.collection.objects.link(o)
        cam = bpy.data.cameras.new("cam"); cam.type = "ORTHO"; cam.ortho_scale = 62
        co = bpy.data.objects.new("cam", cam); sc.collection.objects.link(co); sc.camera = co
        zc = 4.0
        for label, yaw in (("front", 25), ("side", 100)):
            ob.rotation_euler = (0, 0, math.radians(yaw))
            co.location = (0, -60, zc); co.rotation_euler = (math.radians(90), 0, 0)
            sc.render.filepath = os.path.join(out_dir, "form_%s_%s.png" % (form, label))
            bpy.ops.render.render(write_still=True)
            shots.append(sc.render.filepath)
    sheet = Image.new("RGB", (420 * 6, 700 * 2), (8, 9, 15))
    for i, form in enumerate(FORMS):
        for j, label in enumerate(("front", "side")):   # turned over: the figure stands, the form hanging from its arm
            im = Image.open(os.path.join(out_dir, "form_%s_%s.png" % (form, label))).convert("RGB").transpose(Image.ROTATE_180)
            sheet.paste(im, (i * 420, j * 700))
    sheet.save(os.path.join(out_dir, "parasite_forms.png"))


def to_game(out_dir):
    """Every form into the mod as a bare OBJ model (no item: ParasiteArms draws it in the arm's own space - the OBJ's
    origin is the hand end of the forearm's centre, +Y on past the hand (the shoulder is at -12 px), -X toward the body, -Z the back of the arm)."""
    for form in FORMS:
        build(form, out_dir, game=True)
        for ext in (".obj", ".mtl"): os.replace(os.path.join(out_dir, form + ext), os.path.join(out_dir, "parasite_" + form + ext))
        gt.export_to_game("parasite_" + form, out_dir, {}, glowing={form + "_glow"},
                          textures={form: TEX, form + "_glow": TEX}, render_type="minecraft:cutout")


if __name__ == "__main__":
    mode, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    paint(os.path.join(out, TEX + ".png"))
    if mode == "render": render_sheet(out)
    if mode == "game": to_game(out)
