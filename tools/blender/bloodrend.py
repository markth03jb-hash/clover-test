"""Bloodrend, modelled in Blender (run with Blender's Python: bpy) and exported as an OBJ for Forge's OBJ loader.

Built from the user's concept picture: a broad, double-edged blade of glowing blood red with a black core running down
its middle (red flowing through it), its edges hacked with jagged notches and a broken second point by the tip; a
chunky crossguard of worn gold - one arm angled back toward the grip, the other curling down toward the blade - with a
round gold boss on the blade set with a red gem and a small red stone on the guard; a black grip with a thick cord
wound round it in a spiral; a chunky gold pommel.

The blade's material is lit by its own ambient colour (Ka 1 in the .mtl), so it glows; the rest takes normal light.

Blender space (pixels; exported at 1/16): Z up the blade, X across it, Y through it. Origin at the middle of the grip.

  python tools/blender/bloodrend.py render OUT_DIR    previews
  python tools/blender/bloodrend.py game OUT_DIR      writes it into the mod (models/item, textures/item)
"""
import json, math, os, random, shutil, sys
import bpy, bmesh
from mathutils import Vector, Matrix
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gilded_thorn as gt   # the shared mesh helpers (new_obj, curve_obj, cone, torus, gemstone, box ...)

NAME = "bloodrend"
gt.REG.update({"blade": (0, 0, 256, 120), "gold": (0, 128, 128, 192), "gem": (128, 128, 192, 192),
               "grip": (192, 128, 256, 192), "cord": (0, 192, 128, 256), "gold2": (128, 192, 256, 256)})
REG = gt.REG
BAND = 10   # each of the blade's 12 cross-section bands is 10 px tall in the atlas


# ------------------------------------------------------------------ the texture
def mix(a, b, t): t = max(0.0, min(1.0, t)); return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def paint(out):
    rnd = random.Random(9)
    im = Image.new("RGBA", (256, 256)); px = im.load()
    EDGE, RED, DEEP, BLACK, FLOW = (255, 72, 60), (196, 12, 24), (96, 2, 10), (14, 11, 14), (140, 6, 16)
    edge_at = {0: 0, 6: 0, 5: 1, 11: 1}          # edge bands: which end (f) the very edge is at
    face_to_black = {1: 1, 7: 1, 4: 0, 10: 0}     # red face bands: which end meets the black core
    for x in range(256):
        u = x / 255
        for y in range(120):
            k, f = y // BAND, (y % BAND) / (BAND - 1)
            if k in edge_at:
                d = abs(f - edge_at[k])                                   # 0 at the edge
                c = mix(EDGE, RED, d ** 0.7)
            elif k in face_to_black:
                d = abs(f - face_to_black[k])                             # 0 where it meets the black
                c = mix(DEEP, RED, d ** 0.6)
            else:                                                         # the black core, red flowing down it
                n = math.sin(x * 0.11 + math.sin(y * 0.9) * 2.0) * math.sin(x * 0.037 + y * 0.4)
                c = mix(BLACK, FLOW, max(0.0, n) ** 2.2 * 0.9)
            c = mix(c, (60, 0, 8), 0.12 * u)
            px[x, y] = c + (255,)
    for y in range(120, 128):
        for x in range(256): px[x, y] = (200, 20, 30, 255)
    GOLD, GOLD_D, GOLD_L = (184, 132, 52), (96, 62, 22), (238, 200, 120)
    for (x0, y0, x1, y1) in (REG["gold"], REG["gold2"]):
        for x in range(x0, x1):
            for y in range(y0, y1):
                n = math.sin(x * 0.08 + y * 0.05) * math.sin(y * 0.07 - x * 0.03)
                c = mix(GOLD, GOLD_L if n > 0 else GOLD_D, abs(n) * 0.75)
                px[x, y] = c + (255,)
        for _ in range(10):   # worn: a few chips
            cx, cy = rnd.randrange(x0, x1 - 3), rnd.randrange(y0, y1 - 2)
            for k in range(rnd.randint(1, 3)): px[cx + k, cy] = GOLD_D + (255,)
    x0, y0, x1, y1 = REG["gem"]
    for x in range(x0, x1):
        for y in range(y0, y1):
            d = math.hypot((x - (x0 + x1) / 2) / 32, (y - (y0 + y1) / 2) / 32)
            px[x, y] = mix((255, 170, 160), mix((214, 20, 34), (80, 0, 12), (d - 0.4) / 0.6), min(1, d / 0.4)) + (255,)
    x0, y0, x1, y1 = REG["grip"]
    for x in range(x0, x1):
        for y in range(y0, y1): px[x, y] = mix((10, 9, 12), (26, 24, 30), 0.5 + 0.5 * math.sin(x * 0.4)) + (255,)
    x0, y0, x1, y1 = REG["cord"]
    for x in range(x0, x1):
        for y in range(y0, y1):
            k = ((y - y0) % 16) / 16
            px[x, y] = mix((16, 14, 18), (70, 66, 76), 0.35 + 0.65 * math.sin(k * math.pi) ** 0.5) + (255,)
            if k < 0.08: px[x, y] = (6, 5, 7, 255)
    im.save(out)


# ------------------------------------------------------------------ the blade
LEN, Z0 = 26.0, 4.8
def notch(t, spots):
    """Hacked notches: a sharp step in, then a long slope back out."""
    v = 0.0
    for c, d in spots:
        u = (t - c) / 0.05
        if -0.25 < u < 1.0: v = max(v, d * (1 - u) if u >= 0 else d * (1 + u / 0.25))
    return v
NOTCH_L = [(0.16, 0.3), (0.3, 0.45), (0.45, 0.35), (0.6, 0.5), (0.8, 0.35)]
NOTCH_R = [(0.12, 0.3), (0.27, 0.4), (0.42, 0.3), (0.58, 0.4)]
def xr(t): return 2.3                                              # the straight edge: dead straight to the point
def xl_base(t):                                                     # the other: bowing gently out, then sweeping round
    if t < 0.66: return -2.2 - 0.35 * math.sin(t / 0.66 * math.pi * 0.5)
    u = (t - 0.66) / 0.34
    return xr(t) - (xr(t) + 2.55) * math.sqrt(max(0.0, 1 - u ** 1.6))
def xc(t): return (xl_base(t) + xr(t)) / 2                        # the black core runs down the middle
def hl(t): return max(0.04, xc(t) - xl_base(t) - notch(t, NOTCH_L))
def hr(t):
    v = xr(t) - xc(t) - notch(t, NOTCH_R)
    if 0.78 < t < 0.92: v -= 0.45 * math.sin((t - 0.78) / 0.14 * math.pi)   # a bite out of the straight edge near the point
    return max(0.04, v)
def thick(t): return 0.5 * (0.95 * (1 - t ** 2) ** 0.4 + 0.12)
def black(t, h): return h * (0.5 + 0.07 * math.sin(t * 21) + 0.05 * math.sin(t * 47))   # the black core's half-width, wavering


def blade():
    bm = bmesh.new(); uvl = bm.loops.layers.uv.new("UVMap")
    N = 84
    rings = []
    for i in range(N + 1):
        t = i / N
        z = Z0 + LEN * t
        L, R, th = hl(t), hr(t), thick(t)
        b = th * 0.35
        hm = min(L, R)
        bl = min(black(t, hm), hm * 0.68)
        eL, eR = min(0.45, L * 0.3), min(0.45, R * 0.3)
        ring = [(-L, 0), (-(L - eL), b), (-bl, th * 0.92), (0, th), (bl, th * 0.92), (R - eR, b),
                (R, 0), (R - eR, -b), (bl, -th * 0.92), (0, -th), (-bl, -th * 0.92), (-(L - eL), -b)]
        c = xc(t)
        rings.append([bm.verts.new((c + x, y, z)) for x, y in ring])
    tip = bm.verts.new((xr(1.0) - 0.1, 0, Z0 + LEN + 0.4))
    def V(k): return k * BAND
    for i in range(N):
        u0, u1 = 0.5 + 255 * i / N, 0.5 + 255 * (i + 1) / N
        for k in range(12):
            k2 = (k + 1) % 12
            f = bm.faces.new((rings[i][k], rings[i][k2], rings[i + 1][k2], rings[i + 1][k]))
            v0, v1 = V(k) + 0.5, V(k + 1) - 0.5
            for lp, (uu, vv) in zip(f.loops, ((u0, v0), (u0, v1), (u1, v1), (u1, v0))): lp[uvl].uv = gt.uvp(uu, vv)
    for k in range(12):
        f = bm.faces.new((rings[N][k], rings[N][(k + 1) % 12], tip))
        for lp, (uu, vv) in zip(f.loops, ((255, V(k) + 0.5), (255, V(k + 1) - 0.5), (255.5, V(k) + 5))): lp[uvl].uv = gt.uvp(min(255.5, uu), vv)
    f = bm.faces.new(list(reversed(rings[0])))
    for lp in f.loops: lp[uvl].uv = gt.uvp(64, 124)
    return gt.new_obj("blade", bm, smooth=False)


# ------------------------------------------------------------------ the guard, grip and pommel
def angular(name, pts, flat, region="gold"):
    """A chunky, faceted bar along a curve: a four-sided (diamond) section, flattened through Y."""
    return gt.curve_obj(name, pts, flat=flat, region=region, res=4, bev=0)


def guard():
    """A thick, faceted gold crossbar whose ends sweep back toward the grip like a pair of wings, a big round gold
    medallion at its heart holding the red gem, a small red stone set near the left wing's end, and a gold collar
    where the grip meets it."""
    th0 = thick(0.0)
    # the crossbar: one faceted bar right across, thickest in the middle
    angular("bar", [(-3.6, 0, 3.85, 0.75), (-1.8, 0, 4.25, 0.95), (0.0, 0, 4.35, 1.05), (1.8, 0, 4.25, 0.95), (3.6, 0, 3.85, 0.75)], 1.35)
    # the wings: from each end, out and sweeping back toward the grip, ending in a blunt angular point
    angular("wing_l", [(-3.3, 0, 3.95, 0.8), (-4.5, 0, 3.6, 0.72), (-5.3, 0, 2.8, 0.55), (-5.6, 0, 1.9, 0.35), (-5.5, 0, 1.4, 0.1)], 1.3)
    angular("wing_r", [(3.3, 0, 3.95, 0.8), (4.5, 0, 3.6, 0.72), (5.3, 0, 2.8, 0.55), (5.6, 0, 1.9, 0.35), (5.5, 0, 1.4, 0.1)], 1.3)
    # a ridge along the top of the bar, and a step under it
    angular("ridge", [(-3.0, 0, 4.95, 0.3), (0.0, 0, 5.25, 0.42), (3.0, 0, 4.95, 0.3)], 1.0)
    gt.box("collar", (0, 0, 3.15), (2.6, 2.4, 0.9), "gold", bevel=0.2, segs=1)
    # the medallion: a thick gold disc on each face of the guard's heart, a heavy ring round it, the red gem within
    for side in (1, -1):
        y0 = side * 0.95
        bm = bmesh.new()
        bmesh.ops.create_cone(bm, cap_ends=True, segments=10, radius1=1.75, radius2=1.55, depth=0.5)
        q = Vector((0, 0, 1)).rotation_difference(Vector((0, side, 0)))
        bmesh.ops.rotate(bm, cent=(0, 0, 0), matrix=q.to_matrix(), verts=bm.verts)
        bmesh.ops.translate(bm, vec=(0, y0 + side * 0.25, 4.6), verts=bm.verts)
        gt.new_obj("medal%d" % side, bm, REG["gold2"], smooth=False)
        gt.torus("medal_ring%d" % side, (0, y0 + side * 0.55, 4.6), (0, 1, 0), 1.25, 0.3)
        gt.gemstone("medal_gem%d" % side, (0, y0 + side * 0.6, 4.6), 0.95, 0.55)
        gt.gemstone("wing_gem%d" % side, (-4.6, side * 1.0, 3.55), 0.45, 0.6)   # the small stone near the left wing's end


def grip():
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=10, radius1=0.88, radius2=0.95, depth=6.8)
    bmesh.ops.translate(bm, vec=(0, 0, -0.35), verts=bm.verts)
    gt.new_obj("grip", bm, REG["grip"])
    # the cord, wound round in a thick spiral
    pts, turns, n = [], 7.5, 90
    for k in range(n + 1):
        a = 2 * math.pi * turns * k / n
        pts.append((0.9 * math.cos(a), 0.9 * math.sin(a), -3.5 + 6.8 * k / n, 0.34))
    cu = bpy.data.curves.new("cord", "CURVE"); cu.dimensions = "3D"; cu.resolution_u = 1
    cu.bevel_depth = 1.0; cu.bevel_resolution = 0; cu.use_fill_caps = True
    sp = cu.splines.new("POLY"); sp.points.add(len(pts) - 1)
    for p, (x, y, z, r) in zip(sp.points, pts): p.co = (x, y, z, 1); p.radius = r
    ob = bpy.data.objects.new("cord_c", cu); bpy.context.collection.objects.link(ob)
    me = bpy.data.meshes.new_from_object(ob.evaluated_get(bpy.context.evaluated_depsgraph_get())); bpy.data.objects.remove(ob)
    bm = bmesh.new(); bm.from_mesh(me)
    x0, y0, x1, y1 = REG["cord"]
    gt.new_obj("cord", bm, uv_fn=lambda co: (x0 + 2 + (math.atan2(co[1], co[0]) / (2 * math.pi) + 0.5) * (x1 - x0 - 4),
                                             y0 + 2 + ((co[2] + 3.9) / 7.5) * (y1 - y0 - 4)))
    # the pommel: a chunky gold cap, faceted, with two short prongs and a blunt point
    gt.box("pom_collar", (0, 0, -4.2), (2.8, 2.6, 0.8), "gold", bevel=0.22, segs=1)
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=6, radius1=1.7, radius2=0.6, depth=2.4)
    bmesh.ops.rotate(bm, cent=(0, 0, 0), matrix=Matrix.Rotation(math.pi, 3, "X"), verts=bm.verts)
    bmesh.ops.translate(bm, vec=(0, 0, -5.75), verts=bm.verts)
    gt.new_obj("pom_cap", bm, REG["gold"], smooth=False)
    angular("prong_a", [(1.1, 0, -4.4, 0.65), (2.1, 0, -4.9, 0.48), (2.4, 0, -5.9, 0.25), (2.2, 0, -6.4, 0.06)], 1.15)
    angular("prong_b", [(-1.1, 0, -4.4, 0.65), (-2.1, 0, -4.9, 0.48), (-2.4, 0, -5.9, 0.25), (-2.2, 0, -6.4, 0.06)], 1.15)


# ------------------------------------------------------------------ build, export, render
def build(out_dir):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    gt.PARTS.clear()
    os.makedirs(out_dir, exist_ok=True)
    tex = os.path.join(out_dir, NAME + ".png"); paint(tex)
    blade_obj = blade()
    n_blade = len(gt.PARTS)
    guard(); grip()
    def material(name, glow):
        m = bpy.data.materials.new(name); m.use_nodes = True
        nt = m.node_tree; bsdf = nt.nodes.get("Principled BSDF")
        img = nt.nodes.new("ShaderNodeTexImage"); img.image = bpy.data.images.get(NAME + ".png") or bpy.data.images.load(tex); img.interpolation = "Closest"
        nt.links.new(img.outputs["Color"], bsdf.inputs["Base Color"])
        bsdf.inputs["Roughness"].default_value = 0.85
        if glow:
            nt.links.new(img.outputs["Color"], bsdf.inputs["Emission Color"]); bsdf.inputs["Emission Strength"].default_value = 0.6
        return m
    glow_m, plain_m = material(NAME + "_glow", True), material(NAME, False)
    for i, ob in enumerate(gt.PARTS): ob.data.materials.append(glow_m if i < n_blade else plain_m)
    bpy.ops.object.select_all(action="DESELECT")
    for ob in gt.PARTS: ob.select_set(True)
    bpy.context.view_layer.objects.active = gt.PARTS[0]
    bpy.ops.object.join()
    dag = bpy.context.view_layer.objects.active; dag.name = NAME
    dag.location = (8, -8, 8)
    bpy.ops.wm.obj_export(filepath=os.path.join(out_dir, NAME + ".obj"), export_selected_objects=True, export_uv=True,
                          export_normals=True, export_materials=True, export_triangulated_mesh=True,
                          forward_axis="NEGATIVE_Z", up_axis="Y", global_scale=1 / 16, path_mode="STRIP")
    print("tris", sum(len(p.vertices) - 2 for p in dag.data.polygons))
    return dag


def to_game(out):
    gt.export_to_game(NAME, out, gt.display(40), glowing={NAME + "_glow"})


if __name__ == "__main__":
    mode, out = sys.argv[1], sys.argv[2]
    d = build(out)
    if mode == "render": gt.VIEW[:] = [42, 11.5]; gt.render(d, out)
    if mode == "game": to_game(out)
