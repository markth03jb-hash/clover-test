"""Frostwing, a greatsword modelled in Blender (run with Blender's Python: bpy) and exported as an OBJ for Forge's OBJ
loader. Built by hand from the user's concept picture (not traced).

A long, broad blade of glowing electric cyan - near-white edges, a jagged deeper-blue core crackling with bright
streaks, fading toward the point; a notch by its root, a small blue diamond near the tip. The blade glows. A black crossguard spreading like
layered wings swept back toward the hands, a black centrepiece reaching down onto the blade with a teal gem; a long two-handed grip wound in dark navy, black rings; a black pommel set with teal gems. The blade glows.

Blender space (pixels; exported at 1/16): Z up the blade, X across it, Y through it. Origin at the middle of the grip.

  python tools/blender/frostwing.py render OUT_DIR    previews
  python tools/blender/frostwing.py game OUT_DIR      writes it into the mod (models/item, textures/item)
"""
import json, math, os, random, shutil, sys
import bpy, bmesh
from mathutils import Vector, Matrix
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gilded_thorn as gt

NAME = "frostwing"
gt.REG.update({"blade": (0, 0, 256, 120), "gem": (128, 128, 160, 160), "grip": (128, 160, 192, 192), "strap": (192, 128, 256, 192),
               "diamond": (128, 192, 160, 224), "black": (192, 192, 256, 224), "black2": (192, 224, 256, 256)})
REG = gt.REG
BAND = 10


# ------------------------------------------------------------------ the texture
def mix(a, b, t): t = max(0.0, min(1.0, t)); return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def paint(out):
    rnd = random.Random(3)
    im = Image.new("RGBA", (256, 256)); px = im.load()
    # a glowing electric blade: near-white edges, bright cyan faces, a deeper electric-blue core with bright streaks
    EDGE, ICE, ICE_D, NAVY, NAVY_L = (214, 255, 255), (40, 232, 255), (16, 190, 240), (8, 12, 24), (30, 44, 72)
    edge_at = {0: 0, 6: 0, 5: 1, 11: 1}
    face_to_core = {1: 1, 7: 1, 4: 0, 10: 0}
    for x in range(256):
        u = x / 255
        for y in range(120):
            k, f = y // BAND, (y % BAND) / (BAND - 1)
            if k in edge_at:
                c = mix(EDGE, ICE, abs(f - edge_at[k]) ** 0.7)
            elif k in face_to_core:
                d = abs(f - face_to_core[k])                  # 0 where it meets the core
                c = mix(ICE_D, ICE, d ** 0.5)
                if (x * 5 + y * 7) % 29 == 0: c = mix(c, EDGE, 0.5)
            else:                                             # the navy core, lit along its middle
                ridge = f if k in (2, 8) else 1 - f
                c = mix(NAVY, NAVY_L, ridge ** 2)
                n = math.sin(x * 0.09 + y * 0.6) * math.sin(x * 0.031 - y)
                c = mix(c, (60, 200, 240), 0.45 * max(0, n) ** 4)   # faint cyan streaks crackling through the black
            px[x, y] = c + (255,)
    for y in range(120, 128):
        for x in range(256): px[x, y] = ICE + (255,)
    x0, y0, x1, y1 = REG["gem"]
    for x in range(x0, x1):
        for y in range(y0, y1):
            d = math.hypot((x - (x0 + x1) / 2) / 16, (y - (y0 + y1) / 2) / 16)
            px[x, y] = mix((190, 255, 240), mix((40, 196, 176), (10, 80, 90), (d - 0.4) / 0.6), min(1, d / 0.4)) + (255,)
    for name, (hi, lo) in (("black", ((84, 92, 108), (14, 15, 20))), ("black2", ((58, 64, 78), (8, 9, 13)))):   # the black guard: dark, lit along its tops
        x0, y0, x1, y1 = REG[name]
        for x in range(x0, x1):
            for y in range(y0, y1):
                c = mix(hi, lo, ((y - y0) / (y1 - y0)) ** 0.7)
                if (x * 3 + y * 5) % 19 == 0: c = mix(c, (120, 130, 150), 0.3)
                px[x, y] = c + (255,)
    x0, y0, x1, y1 = REG["diamond"]
    for x in range(x0, x1):
        for y in range(y0, y1): px[x, y] = mix((24, 32, 52), (8, 10, 18), (y - y0) / (y1 - y0)) + (255,)
    x0, y0, x1, y1 = REG["grip"]
    for x in range(x0, x1):
        for y in range(y0, y1): px[x, y] = mix((14, 18, 34), (30, 38, 64), 0.5 + 0.5 * math.sin(x * 0.4)) + (255,)
    x0, y0, x1, y1 = REG["strap"]
    for x in range(x0, x1):
        for y in range(y0, y1):
            k = ((y - y0) % 16) / 16
            c = mix((20, 26, 48), (66, 84, 128), math.sin(k * math.pi) ** 0.6)
            if k < 0.1: c = (8, 10, 20)
            px[x, y] = c + (255,)
    im.save(out)


# ------------------------------------------------------------------ the blade
LEN, Z0 = 44.0, 8.6
def half(t):                                            # broad, near-straight, a long taper into the point
    if t < 0.74: return 3.45 - 0.4 * t
    u = (t - 0.74) / 0.26
    return (3.45 - 0.4 * 0.74) * (1 - u ** 1.15) + 0.05
def notch_r(t):                                         # a notch cut in the right edge near the root
    u = (t - 0.075) / 0.03
    return 0.8 * ((1 - u) if 0 <= u < 1 else (1 + u / 0.3) if -0.3 < u < 0 else 0)
def hl(t): return half(t)
def hr(t): return half(t) - notch_r(t)
def thick(t): return 0.5 * (1.3 * (1 - t ** 2) ** 0.4 + 0.14)
def core(t, h):                                         # the navy core's half-width: jagged, narrowing and fading out
    if t > 0.78: return 0.0
    j = 0.16 * math.sin(t * 47) + 0.1 * math.sin(t * 131) + 0.08 * math.sin(t * 23)
    return h * max(0.0, (0.6 + j) * (1 - (t / 0.82) ** 2.5))


def blade():
    bm = bmesh.new(); uvl = bm.loops.layers.uv.new("UVMap")
    N = 110
    rings = []
    for i in range(N + 1):
        t = i / N
        z = Z0 + LEN * t
        L, R, th = hl(t), hr(t), thick(t)
        hm = min(L, R)
        b = th * 0.42
        cw = max(0.06, min(core(t, hm), hm * 0.72))
        eL, eR = min(0.6, L * 0.25), min(0.6, R * 0.25)
        ring = [(-L, 0), (-(L - eL), b), (-cw, th * 0.9), (0, th), (cw, th * 0.9), (R - eR, b),
                (R, 0), (R - eR, -b), (cw, -th * 0.9), (0, -th), (-cw, -th * 0.9), (-(L - eL), -b)]
        rings.append([bm.verts.new((x, y, z)) for x, y in ring])
    tip = bm.verts.new((0, 0, Z0 + LEN + 0.6))
    for i in range(N):
        u0, u1 = 0.5 + 255 * i / N, 0.5 + 255 * (i + 1) / N
        for k in range(12):
            k2 = (k + 1) % 12
            f = bm.faces.new((rings[i][k], rings[i][k2], rings[i + 1][k2], rings[i + 1][k]))
            v0, v1 = k * BAND + 0.5, (k + 1) * BAND - 0.5
            for lp, (uu, vv) in zip(f.loops, ((u0, v0), (u0, v1), (u1, v1), (u1, v0))): lp[uvl].uv = gt.uvp(uu, vv)
    for k in range(12):
        f = bm.faces.new((rings[N][k], rings[N][(k + 1) % 12], tip))
        for lp, (uu, vv) in zip(f.loops, ((255, k * BAND + 0.5), (255, (k + 1) * BAND - 0.5), (255.5, k * BAND + 5))): lp[uvl].uv = gt.uvp(min(255.5, uu), vv)
    f = bm.faces.new(list(reversed(rings[0])))
    for lp in f.loops: lp[uvl].uv = gt.uvp(64, 124)
    gt.new_obj("blade", bm, smooth=False)
    # a small navy diamond engraved near the tip, on both faces
    t1 = 0.86
    z = Z0 + LEN * t1
    for side in (1, -1):
        bm = bmesh.new()
        y = side * (thick(t1) + 0.03)
        vs = [bm.verts.new(p) for p in ((0, y, z - 1.1), (0.55, y, z), (0, y, z + 1.1), (-0.55, y, z))]
        f = bm.faces.new(vs)
        if (f.normal.y > 0) != (side > 0): f.normal_flip()
        gt.new_obj("diamond%d" % side, bm, REG["diamond"], smooth=False)


# ------------------------------------------------------------------ the guard
def angular(name, pts, flat, region="black"):
    return gt.curve_obj(name, pts, flat=flat, region=region, res=4, bev=0)


def guard():
    gz = 7.4
    gt.box("g_core", (0, 0, gz), (4.4, 2.8, 2.0), "black", bevel=0.35, segs=2)
    gt.box("g_core_lo", (0, 0, gz - 1.3), (2.8, 2.4, 0.9), "black2", bevel=0.2, segs=1)
    # the wings: on each side, two broad black blades fanning out and sweeping back toward the hands, a darker one under them
    for sd in (1, -1):
        for n, (lift, L, r) in enumerate(((0.7, 5.6, 1.35), (-0.7, 4.2, 1.1))):
            x0, z0 = sd * 1.8, gz + lift * 0.5
            pts = [(x0, 0, z0, r), (x0 + sd * L * 0.35, 0, z0 + 0.6 + lift * 0.4, r * 0.85), (x0 + sd * L * 0.7, 0, z0 + 0.1 + lift * 0.3, r * 0.6),
                   (x0 + sd * L * 0.95, 0, z0 - 1.4 + lift * 0.5, r * 0.3), (x0 + sd * L, 0, z0 - 2.3 + lift * 0.4, 0.04)]
            angular("wing%d_%d" % (sd, n), pts, 0.62, region="black" if n == 0 else "black2")
        x0, z0 = sd * 1.6, gz - 0.2
        pts = [(x0, 0, z0, 1.0), (x0 + sd * 2.0, 0, z0 + 0.1, 0.85), (x0 + sd * 3.9, 0, z0 - 0.7, 0.45), (x0 + sd * 4.7, 0, z0 - 2.2, 0.04)]
        angular("under%d" % sd, pts, 0.45, region="black2")
        # a hooked black spike rising toward the blade off each wing's root
        angular("spur%d" % sd, [(sd * 2.0, 0, gz + 0.8, 0.5), (sd * 2.7, 0, gz + 2.0, 0.35), (sd * 2.6, 0, gz + 3.0, 0.05)], 0.8)
    # the centrepiece: a black langet reaching down onto the blade on each face, layered, a teal gem at its heart
    th0 = thick(0.0)
    for side in (1, -1):
        for layer, (w, top, zt, depth) in enumerate(((2.2, 0.0, 14.5, 0.32), (1.4, 0.32, 12.8, 0.28))):
            bm = bmesh.new()
            outline = [(-w, gz + 0.8), (w, gz + 0.8), (w * 0.8, gz + 3.0), (w * 0.35, zt - 2.0), (0, zt), (-w * 0.35, zt - 2.0), (-w * 0.8, gz + 3.0)]
            y_in, y_out = side * (th0 * 0.6 + top), side * (th0 * 0.6 + top + depth + 0.25)
            lo = [bm.verts.new((x, y_in, z)) for x, z in outline]; hi = [bm.verts.new((x, y_out, z)) for x, z in outline]
            bm.faces.new(lo if side < 0 else list(reversed(lo))); bm.faces.new(hi if side > 0 else list(reversed(hi)))
            for k in range(len(outline)):
                q = (lo[k], lo[(k + 1) % len(outline)], hi[(k + 1) % len(outline)], hi[k])
                bm.faces.new(q if side > 0 else tuple(reversed(q)))
            gt.new_obj("langet%d_%d" % (side, layer), bm, REG["black" if layer == 0 else "black2"], smooth=False)
        gy = side * (th0 * 0.6 + 0.32 + 0.28 + 0.3)
        gt.torus("gem_set%d" % side, (0, gy, gz + 2.2), (0, 1, 0), 0.75, 0.2, region="black")
        gt.gemstone("gem%d" % side, (0, gy + side * 0.08, gz + 2.2), 0.7, 0.6)


# ------------------------------------------------------------------ the grip and pommel
def grip():
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=10, radius1=1.05, radius2=1.1, depth=12.4)
    gt.new_obj("grip", bm, REG["grip"])
    pts, turns, n = [], 13.0, 160
    for k in range(n + 1):
        a = 2 * math.pi * turns * k / n
        pts.append((1.02 * math.cos(a), 1.02 * math.sin(a), -6.0 + 12.0 * k / n, 0.25))
    cu = bpy.data.curves.new("strap", "CURVE"); cu.dimensions = "3D"; cu.resolution_u = 1
    cu.bevel_depth = 1.0; cu.bevel_resolution = 0; cu.use_fill_caps = True
    sp = cu.splines.new("POLY"); sp.points.add(len(pts) - 1)
    for p, (x, y, z, r) in zip(sp.points, pts): p.co = (x, y, z, 1); p.radius = r
    ob = bpy.data.objects.new("strap_c", cu); bpy.context.collection.objects.link(ob)
    me = bpy.data.meshes.new_from_object(ob.evaluated_get(bpy.context.evaluated_depsgraph_get())); bpy.data.objects.remove(ob)
    bm = bmesh.new(); bm.from_mesh(me)
    x0, y0, x1, y1 = REG["strap"]
    gt.new_obj("strap", bm, uv_fn=lambda co: (x0 + 2 + (math.atan2(co[1], co[0]) / (2 * math.pi) + 0.5) * (x1 - x0 - 4),
                                              y0 + 2 + ((co[2] + 6.4) / 12.8) * (y1 - y0 - 4)))
    gt.torus("ring_top", (0, 0, 6.1), (0, 0, 1), 1.15, 0.3, region="black")
    gt.torus("ring_mid", (0, 0, 0.0), (0, 0, 1), 1.15, 0.22, region="black")
    gt.torus("ring_bot", (0, 0, -6.2), (0, 0, 1), 1.15, 0.3, region="black")
    # the pommel: a black collar, a rounded black cap set with teal gems, a little spike
    gt.box("pom_collar", (0, 0, -6.9), (2.3, 2.3, 0.9), "black", bevel=0.25, segs=2)
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=10, v_segments=6, radius=1.45)
    bmesh.ops.scale(bm, vec=(1, 0.85, 1.05), verts=bm.verts)
    bmesh.ops.translate(bm, vec=(0, 0, -8.6), verts=bm.verts)
    gt.new_obj("pom_cap", bm, REG["black"])
    for side in (1, -1):
        gt.gemstone("pom_gem%d" % side, (0, side * 1.15, -8.6), 0.6, 0.6)
    gt.cone("pom_spike", (0, 0, -9.9), (0, 0, -1), 0.45, 1.5, region="black2")


# ------------------------------------------------------------------ build, export, render
DISPLAY = gt.display(64, held=48, fp_held=34)   # a greatsword: pommel to point - in third person over twice a sword



def build(out_dir):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    gt.PARTS.clear()
    os.makedirs(out_dir, exist_ok=True)
    tex = os.path.join(out_dir, NAME + ".png"); paint(tex)
    blade()
    n_glow = len(gt.PARTS)                              # the blade glows; the black hilt takes normal light
    guard(); grip()
    image = bpy.data.images.load(tex)
    def material(name, glow):
        m = bpy.data.materials.new(name); m.use_nodes = True
        nt = m.node_tree; bsdf = nt.nodes.get("Principled BSDF")
        img = nt.nodes.new("ShaderNodeTexImage"); img.image = image; img.interpolation = "Closest"
        nt.links.new(img.outputs["Color"], bsdf.inputs["Base Color"]); bsdf.inputs["Roughness"].default_value = 0.75
        if glow:   # as the game draws it: the texture's own colour, unshaded (no light added on top)
            nt.links.remove(nt.links[-1]); bsdf.inputs["Base Color"].default_value = (0, 0, 0, 1)
            nt.links.new(img.outputs["Color"], bsdf.inputs["Emission Color"]); bsdf.inputs["Emission Strength"].default_value = 1.0
        return m
    glow_m, plain_m = material(NAME + "_glow", True), material(NAME, False)
    for i, ob in enumerate(gt.PARTS): ob.data.materials.append(glow_m if i < n_glow else plain_m)
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
    gt.export_to_game(NAME, out, DISPLAY, glowing={NAME + "_glow"})


if __name__ == "__main__":
    mode, out = sys.argv[1], sys.argv[2]
    d = build(out)
    if mode == "render": gt.VIEW[:] = [66, 21.0]; gt.CLOSE[:] = [22, 9.0]; gt.render(d, out)
    if mode == "game": to_game(out)
