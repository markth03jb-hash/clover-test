"""The Gilded Thorn, modelled in Blender (run with Blender's Python: bpy) as a real mesh, exported as an OBJ that
Forge's OBJ loader ("loader": "forge:obj") draws as the item.

Built from the user's concept sketch: a broad double-edged dagger of dark gunmetal with a raised ridge down its middle,
narrow pale honed edges with little jagged burrs off them and a few nicks; at its root a guard of thorny antique gold - a
stepped collar, a horn sweeping up beside the back of the blade and hooking in, a curl rolling off the other side, thorns
growing from both, pointed gold plates on each face with a red stone, and a thorned gold vine running down the blade
with two more stones. A dark cord-wrapped grip with gold rings, and a pointed dark pommel.

Blender space (pixels; exported at 1/16 so 16 px = 1 block): Z up the blade, X across it (the curl's side -X, the
horn's side +X), Y through it (the faces). Origin at the middle of the grip.

  python tools/blender/gilded_thorn.py export OUT_DIR      writes gilded_thorn.obj/.mtl + the texture into OUT_DIR
  python tools/blender/gilded_thorn.py render OUT_DIR      also renders previews (front, angled, back, edge, guard close-up)
  python tools/blender/gilded_thorn.py game OUT_DIR        exports, then writes it into the mod (models/item, textures/item)
  (Blender's Python: pip install bpy pillow - see tools/blender/README)
"""
import math, os, sys
import bpy, bmesh
from mathutils import Vector, Matrix

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gilded_thorn_tex

REG = {"blade": (0, 0, 256, 128), "gold": (0, 128, 128, 192), "gem": (128, 128, 192, 192), "wrap": (192, 128, 256, 192),
       "dark": (0, 192, 128, 256), "cord": (128, 192, 256, 256)}
PARTS = []   # (object, smooth)


def uvp(px_x, px_y):   # an atlas pixel position -> Blender UV (v up from the bottom)
    return (px_x / 256, 1 - px_y / 256)


def new_obj(name, bm, region=None, smooth=True, uv_fn=None):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me); bm.free()
    ob = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(ob)
    if region or uv_fn: fit_uv(ob, region, uv_fn)
    for p in me.polygons: p.use_smooth = smooth
    PARTS.append(ob)
    return ob


def fit_uv(ob, region, uv_fn=None):
    """Planar-project the object's two longest axes into its atlas region (or use uv_fn(co) -> atlas px)."""
    me = ob.data
    if not me.uv_layers: me.uv_layers.new(name="UVMap")
    uv = me.uv_layers.active.data
    cos = [ob.matrix_world @ v.co for v in me.vertices]
    if uv_fn is None:
        ext = [max(c[i] for c in cos) - min(c[i] for c in cos) for i in range(3)]
        a, b = sorted(range(3), key=lambda i: -ext[i])[:2]
        lo = [min(c[i] for c in cos) for i in range(3)]
        x0, y0, x1, y1 = region
        m = 1.5
        def uv_fn(co): return (x0 + m + (co[a] - lo[a]) / max(1e-6, ext[a]) * (x1 - x0 - 2 * m),
                               y0 + m + (co[b] - lo[b]) / max(1e-6, ext[b]) * (y1 - y0 - 2 * m))
    for poly in me.polygons:
        for li in poly.loop_indices:
            px_ = uv_fn(cos[me.loops[li].vertex_index])
            uv[li].uv = uvp(*px_)


def curve_obj(name, pts, flat=1.0, region="gold", res=5, bev=2):
    """A tapering tube along a smooth curve through pts [(x, y, z, radius)], flattened through Y by `flat`."""
    cu = bpy.data.curves.new(name, "CURVE")
    cu.dimensions = "3D"; cu.resolution_u = res
    cu.bevel_depth = 1.0; cu.bevel_resolution = bev; cu.use_fill_caps = True
    sp = cu.splines.new("BEZIER"); sp.bezier_points.add(len(pts) - 1)
    for bp, (x, y, z, r) in zip(sp.bezier_points, pts):
        bp.co = (x, y, z); bp.radius = r
        bp.handle_left_type = bp.handle_right_type = "AUTO"
    ob = bpy.data.objects.new(name + "_c", cu)
    bpy.context.collection.objects.link(ob)
    dg = bpy.context.evaluated_depsgraph_get()
    me = bpy.data.meshes.new_from_object(ob.evaluated_get(dg))
    bpy.data.objects.remove(ob)
    bm = bmesh.new(); bm.from_mesh(me)
    bmesh.ops.scale(bm, vec=(1, flat, 1), verts=bm.verts)
    return new_obj(name, bm, REG[region])


def cone(name, base, direction, r, length, region="gold", segs=5):
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=segs, radius1=r, radius2=0.0, depth=length)
    bmesh.ops.translate(bm, vec=(0, 0, length / 2), verts=bm.verts)        # base at the origin, point up +Z
    q = Vector((0, 0, 1)).rotation_difference(Vector(direction).normalized())
    bmesh.ops.rotate(bm, cent=(0, 0, 0), matrix=q.to_matrix(), verts=bm.verts)
    bmesh.ops.translate(bm, vec=base, verts=bm.verts)
    return new_obj(name, bm, REG[region])


def box(name, center, size, region, bevel=0.0, segs=3):
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    bmesh.ops.scale(bm, vec=size, verts=bm.verts)
    if bevel > 0: bmesh.ops.bevel(bm, geom=bm.verts[:] + bm.edges[:], offset=bevel, segments=segs, affect="EDGES", profile=0.5)
    bmesh.ops.translate(bm, vec=center, verts=bm.verts)
    return new_obj(name, bm, REG[region])


def torus(name, center, axis, R, r, region="gold"):
    bm = bmesh.new()
    segs, rings = 12, 5
    vs = []
    for i in range(segs):
        a = 2 * math.pi * i / segs
        row = []
        for j in range(rings):
            b = 2 * math.pi * j / rings
            row.append(bm.verts.new(((R + r * math.cos(b)) * math.cos(a), (R + r * math.cos(b)) * math.sin(a), r * math.sin(b))))
        vs.append(row)
    for i in range(segs):
        for j in range(rings):
            bm.faces.new((vs[i][j], vs[(i + 1) % segs][j], vs[(i + 1) % segs][(j + 1) % rings], vs[i][(j + 1) % rings]))
    q = Vector((0, 0, 1)).rotation_difference(Vector(axis))
    bmesh.ops.rotate(bm, cent=(0, 0, 0), matrix=q.to_matrix(), verts=bm.verts)
    bmesh.ops.translate(bm, vec=center, verts=bm.verts)
    return new_obj(name, bm, REG[region])


def gemstone(name, center, r, squash):
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=8, v_segments=5, radius=r)
    bmesh.ops.scale(bm, vec=(1, squash, 1), verts=bm.verts)
    bmesh.ops.translate(bm, vec=center, verts=bm.verts)
    return new_obj(name, bm, REG["gem"])


# ------------------------------------------------------------------ the blade
LEN, Z0 = 22.0, 4.6
def xr(t): return 2.0 + 0.3 * t                                  # the straight side: dead straight, running to the point
def xl(t):                                                          # the curved side: bowing gently out, then sweeping
    if t < 0.62: return -2.15 - 0.35 * math.sin(t / 0.62 * math.pi * 0.5)      # round in a curve to meet it there
    u = (t - 0.62) / 0.38
    return xr(t) - (xr(t) + 2.5) * math.sqrt(max(0.0, 1 - u ** 1.6))
def xc(t): return (xl(t) + xr(t)) / 2                               # the ridge runs down the middle
def half(t): return max(0.05, (xr(t) - xl(t)) / 2)
def hone(t): return min(0.42, half(t) * 0.28)
def thick(t): return 0.5 * (1.0 * (1 - t ** 2) ** 0.4 + 0.12)       # half the thickness, at the ridge
CHIPS = [(0.2, 0.22), (0.34, 0.28), (0.55, 0.18), (0.68, 0.2)]
def chip(t):
    v = 0.0
    for c, d in CHIPS:
        u = (t - c) / 0.025
        if -1 < u < 0.5: v = max(v, d * ((1 + u) if u < 0 else (1 - u / 0.5)))
    return v
BURRS = [(0.12, 1, 0.3), (0.27, -1, 0.35), (0.4, 1, 0.28), (0.52, -1, 0.3), (0.63, 1, 0.22)]


def blade():
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new("UVMap")
    N = 72
    rings = []
    for i in range(N + 1):
        t = i / N
        z = Z0 + LEN * t
        h, hn, th = half(t), hone(t), thick(t)
        b = th * 0.3
        c = xc(t)
        r = h - chip(t)
        ring = [(-h, 0), (-(h - hn), b), (0, th), (r - hn, b), (r, 0), (r - hn, -b), (0, -th), (-(h - hn), -b)]
        rings.append([bm.verts.new((c + x, y, z)) for x, y in ring])
    tip = bm.verts.new((xr(1.0) + 0.05, 0, Z0 + LEN + 0.35))
    def V(k): return (k * 16 + (0.5 if k == 0 else 0)) if k < 8 else 127.5
    for i in range(N):
        u0, u1 = 0.5 + 255 * i / N, 0.5 + 255 * (i + 1) / N
        for k in range(8):
            k2 = (k + 1) % 8
            f = bm.faces.new((rings[i][k], rings[i][k2], rings[i + 1][k2], rings[i + 1][k]))
            vk, vk2 = V(k) + 0.5, V(k + 1) - 0.5
            for lp, (uu, vv) in zip(f.loops, ((u0, vk), (u0, vk2), (u1, vk2), (u1, vk))):
                lp[uvl].uv = uvp(uu, vv)
    for k in range(8):   # the point
        k2 = (k + 1) % 8
        f = bm.faces.new((rings[N][k], rings[N][k2], tip))
        for lp, (uu, vv) in zip(f.loops, ((255, V(k) + 0.5), (255, V(k + 1) - 0.5), (255.5, V(k) + 8))):
            lp[uvl].uv = uvp(min(255.5, uu), vv)
    f = bm.faces.new(list(reversed(rings[0])))   # the root (hidden in the collar)
    for lp in f.loops: lp[uvl].uv = uvp(64, 24)
    ob = new_obj("blade", bm, smooth=False)
    # burrs: small jagged spurs off both edges, raking toward the point
    for n, (t, sd, out) in enumerate(BURRS):
        z0 = Z0 + LEN * t
        xe = xc(t) + sd * (half(t) - (chip(t) if sd > 0 else 0) - 0.15)
        bm = bmesh.new()
        a = bm.verts.new((xe, 0.1, z0)); b = bm.verts.new((xe, -0.1, z0)); c = bm.verts.new((xe, 0, z0 + 1.1))
        p = bm.verts.new((xe + sd * (out + 0.15), 0, z0 + 0.15))
        for fv in ((a, b, p), (b, c, p), (c, a, p), (a, c, b)): bm.faces.new(fv)
        new_obj("burr%d" % n, bm, smooth=False, uv_fn=lambda co: (40 + 60 * (co[2] - Z0) / LEN, 8))
    return ob


def face_y(x, t):
    """How far the blade's face stands out (Y) at x across it, a fraction t along it."""
    h, hn, th = half(t), hone(t), thick(t)
    b = th * 0.3
    return th - (th - b) * min(1.0, abs(x - xc(t)) / max(1e-3, h - hn))


# ------------------------------------------------------------------ the guard
def guard():
    th0 = thick(0.0)
    # the collar: a heavy gold band flowing across the blade's root, dipping in the middle, its ends rising into the
    # horn on one side and the curl on the other
    curve_obj("collar", [(-2.5, 0, 4.35, 0.85), (-1.2, 0, 3.85, 1.05), (0.0, 0, 3.7, 1.1), (1.2, 0, 3.85, 1.05), (2.3, 0, 4.1, 0.95)], flat=1.25)
    curve_obj("collar_lo", [(-1.5, 0, 3.0, 0.45), (0.0, 0, 2.85, 0.6), (1.5, 0, 3.0, 0.45)], flat=1.7)
    # the horn: sweeping up beside the back of the blade, leaning out, then hooking in toward it
    horn = [(2.0, 0, 3.9, 1.0), (3.3, 0, 5.2, 0.9), (3.95, 0, 7.4, 0.72), (3.75, 0, 9.6, 0.52), (3.1, 0, 11.5, 0.32),
            (2.55, 0, 12.7, 0.14), (2.35, 0, 13.2, 0.03)]
    curve_obj("horn", horn, flat=0.6)
    # the curl: off the other side, out and up, rolling over and down
    curl = [(-2.3, 0, 4.3, 0.85), (-3.5, 0, 5.1, 0.68), (-4.4, 0, 4.6, 0.52), (-4.3, 0, 3.5, 0.36), (-3.5, 0, 2.95, 0.2),
            (-3.0, 0, 3.25, 0.05)]
    curve_obj("curl", curl, flat=0.62)
    # thorns growing out of the horn and the curl
    for n, (x, z, dx, dz, r, L) in enumerate(((3.55, 5.6, 1.0, 0.25, 0.32, 1.3), (4.2, 8.0, 1.0, 0.45, 0.28, 1.2),
                                               (3.9, 10.3, 0.9, 0.7, 0.22, 1.0), (2.9, 12.0, 0.6, 0.9, 0.15, 0.8),
                                               (-4.6, 4.9, -0.9, 0.6, 0.25, 1.0), (-2.6, 2.9, -0.4, -1.0, 0.2, 0.8))):
        cone("thorn%d" % n, (x, 0, z), (dx, 0, dz), r, L)
    # the face plates: pointed shields rising from the collar, a red stone at their heart
    outline = [(-2.0, 4.6), (2.0, 4.6), (1.75, 6.0), (1.0, 7.3), (0.0, 8.7), (-1.0, 7.3), (-1.75, 6.0)]
    for side in (1, -1):
        bm = bmesh.new()
        y_in, y_out = side * th0 * 0.8, side * (th0 + 0.38)
        lo = [bm.verts.new((x, y_in, z)) for x, z in outline]
        hi = [bm.verts.new((x, y_out, z)) for x, z in outline]
        bm.faces.new(lo if side < 0 else list(reversed(lo))); bm.faces.new(hi if side > 0 else list(reversed(hi)))
        n = len(outline)
        for k in range(n):
            q = (lo[k], lo[(k + 1) % n], hi[(k + 1) % n], hi[k])
            bm.faces.new(q if side > 0 else tuple(reversed(q)))
        bmesh.ops.bevel(bm, geom=[e for e in bm.edges if all(abs(v.co.y - y_out) < 1e-4 for v in e.verts)], offset=0.12, segments=2, affect="EDGES")
        new_obj("plate%d" % side, bm, REG["gold"], smooth=False)
        gy = y_out + side * 0.12
        torus("setting%d" % side, (0, gy, 6.1), (0, 1, 0), 0.62, 0.15)
        gemstone("gem%d" % side, (0, gy + side * 0.05, 6.1), 0.6, 0.55)
    # the vine: a thorned gold tendril running on from the plate's point down each face, toward the back edge
    for side in (1, -1):
        pts = []
        for k in range(9):
            t = 0.18 + 0.34 * k / 8
            z = Z0 + LEN * t
            x = xc(t) + 0.45 + 0.5 * math.sin(k * 1.2)
            r = 0.24 - 0.17 * k / 8
            pts.append((x, side * (face_y(x, t) + r * 0.55), z, r))
        curve_obj("vine%d" % side, [(0.0, side * (th0 + 0.3), 8.4, 0.26)] + pts, flat=0.7)
        for n, k in enumerate((2, 4, 6)):
            x, y, z, r = pts[k]
            cone("vthorn%d%d" % (side, n), (x, y, z), (0.9 if n % 2 == 0 else -0.9, 0, 0.8), 0.13, 0.75)
        for n, k in enumerate((1, 5)):
            x, y, z, r = pts[k]
            torus("vset%d%d" % (side, n), (x, y + side * 0.06, z), (0, 1, 0), 0.4, 0.1)
            gemstone("vgem%d%d" % (side, n), (x, y + side * 0.1, z), 0.38, 0.55)


# ------------------------------------------------------------------ the grip and pommel
def grip():
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=12, radius1=0.92, radius2=0.98, depth=6.6)
    bmesh.ops.translate(bm, vec=(0, 0, -0.25), verts=bm.verts)
    x0, y0, x1, y1 = REG["wrap"]
    new_obj("grip", bm, uv_fn=lambda co: (x0 + 1 + (math.atan2(co[1], co[0]) / (2 * math.pi) + 0.5) * (x1 - x0 - 2),
                                          y0 + 1 + ((co[2] + 3.6) / 7.0) * (y1 - y0 - 2)))
    torus("ring_top", (0, 0, 3.05), (0, 0, 1), 1.0, 0.26)
    torus("ring_bot", (0, 0, -3.6), (0, 0, 1), 1.0, 0.24)
    bm = bmesh.new()   # the pommel: a dark cap tapering to a point
    bmesh.ops.create_cone(bm, cap_ends=True, segments=10, radius1=1.05, radius2=0.0, depth=3.6)
    bmesh.ops.translate(bm, vec=(0, 0, -3.75 - 1.8), verts=bm.verts)
    bmesh.ops.rotate(bm, cent=(0, 0, -5.55), matrix=Matrix.Rotation(math.pi, 3, "X"), verts=bm.verts)
    new_obj("pommel", bm, REG["dark"])
    torus("ring_pommel", (0, 0, -4.4), (0, 0, 1), 0.9, 0.2)


def build(out_dir):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    os.makedirs(out_dir, exist_ok=True)
    tex_path = os.path.join(out_dir, "gilded_thorn.png")
    gilded_thorn_tex.paint(tex_path)
    blade(); guard(); grip()
    mat = bpy.data.materials.new("gilded_thorn")
    mat.use_nodes = True
    nt = mat.node_tree
    bsdf = nt.nodes.get("Principled BSDF")
    img = nt.nodes.new("ShaderNodeTexImage"); img.image = bpy.data.images.load(tex_path); img.interpolation = "Closest"
    nt.links.new(img.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.85
    for ob in PARTS: ob.data.materials.append(mat)
    bpy.ops.object.select_all(action="DESELECT")
    for ob in PARTS: ob.select_set(True)
    bpy.context.view_layer.objects.active = PARTS[0]
    bpy.ops.object.join()
    dag = bpy.context.view_layer.objects.active
    dag.name = "gilded_thorn"
    dag.location = (8, -8, 8)   # the grip's middle at the middle of the block (Blender Y becomes the model's -Z)
    bpy.ops.wm.obj_export(filepath=os.path.join(out_dir, "gilded_thorn.obj"), export_selected_objects=True,
                          export_uv=True, export_normals=True, export_materials=True, export_triangulated_mesh=True,
                          forward_axis="NEGATIVE_Z", up_axis="Y", global_scale=1 / 16, path_mode="STRIP")
    print("tris", sum(len(p.vertices) - 2 for p in dag.data.polygons))
    return dag


VIEW = [37, 10.0]   # the full views' ortho scale and centre height
CLOSE = [13, 6.5]   # the close-up's


def render(dag, out_dir):
    dag.location = (0, 0, 0)   # back to the middle of the frame (the export moved it to the block's middle)
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"; sc.cycles.samples = 40; sc.cycles.device = "CPU"
    sc.view_settings.view_transform = "Standard"   # plain colours, as the game shows them (the default view washes them out)
    sc.render.resolution_x, sc.render.resolution_y = 420, 900
    sc.render.film_transparent = False
    world = bpy.data.worlds.new("w"); sc.world = world; world.use_nodes = True
    world.node_tree.nodes["Background"].inputs[0].default_value = (0.03, 0.035, 0.06, 1)
    world.node_tree.nodes["Background"].inputs[1].default_value = 1.0
    for name, rot, energy in (("key", (math.radians(50), 0, math.radians(-35)), 4.0), ("fill", (math.radians(70), 0, math.radians(150)), 1.6),
                              ("top", (math.radians(10), 0, 0), 1.5)):
        L = bpy.data.lights.new(name, "SUN"); L.energy = energy
        o = bpy.data.objects.new(name, L); o.rotation_euler = rot; sc.collection.objects.link(o)
    cam = bpy.data.cameras.new("cam"); cam.type = "ORTHO"
    co = bpy.data.objects.new("cam", cam); sc.collection.objects.link(co); sc.camera = co
    shots = []
    for label, yaw, scale, zc in (("front", 0, VIEW[0], VIEW[1]), ("angled", 35, VIEW[0], VIEW[1]), ("back", 180, VIEW[0], VIEW[1]),
                                  ("edge", 90, VIEW[0], VIEW[1]), ("guard", 20, CLOSE[0], CLOSE[1])):
        dag.rotation_euler = (0, 0, math.radians(yaw))
        cam.ortho_scale = scale
        co.location = (0, -60, zc); co.rotation_euler = (math.radians(90), 0, 0)
        if label == "guard": sc.render.resolution_x, sc.render.resolution_y = 900, 900
        sc.render.filepath = os.path.join(out_dir, "view_%s.png" % label)
        bpy.ops.render.render(write_still=True)
        shots.append(sc.render.filepath)
    return shots


ASSETS = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "iron_spell_grimoire")

def display(length, held=20.0):
    """Hand and slot transforms for a weapon modelled upright (blade +Y, faces +-Z) with its grip's middle at the model's
    middle, `length` model pixels long. Taken from the Nightfang's (models/item/nightfang_3d.json), which is held well -
    upright, leaning in a little, low and forward - turned a quarter round (the Nightfang's faces point sideways, ours
    front and back: the right hand turns one way, the left the other) and scaled so the weapon looks `held` pixels long
    in the hand (a dagger ~20; a greatsword more)."""
    k = held / length
    def r(v): return [round(x, 3) for x in v]
    return {
        "thirdperson_righthand": {"rotation": [25, 90, 0], "translation": [0, 0.5, 0.8], "scale": r([0.85 * k * 1.6] * 3)},
        "thirdperson_lefthand": {"rotation": [25, -90, 0], "translation": [0, 0.5, 0.8], "scale": r([0.85 * k * 1.6] * 3)},
        "firstperson_righthand": {"rotation": [-20, 0, 0], "translation": [1.13, 2.4, 1.13], "scale": r([0.68 * k] * 3)},
        "firstperson_lefthand": {"rotation": [-20, 0, 0], "translation": [1.13, 2.4, 1.13], "scale": r([0.68 * k] * 3)},
        "gui": {"rotation": [0, 0, -45], "translation": [-3.0, -3.0, 0], "scale": r([22.0 / length * 0.9] * 3)},
        "ground": {"rotation": [0, 90, 0], "translation": [0, 2, 0], "scale": r([22.0 / length * 0.6] * 3)},
        "fixed": {"rotation": [0, 0, -45], "translation": [-3.0, -3.0, 0], "scale": r([22.0 / length] * 3)},
    }


DISPLAY = display(36)



def slim_obj(text):
    """The OBJ with its comments gone, its numbers cut to the precision the game can use (4 places for positions and
    texture coordinates - a 16th of a pixel is 0.004 here - 3 for normals), and the values that come out the same
    merged into one (flat-shaded faces repeat their normals over and over): well under half the file."""
    def num(n, places):
        v = round(float(n), places)
        t = ("%.*f" % (places, v)).rstrip("0").rstrip(".")
        return "0" if t in ("-0", "") else t
    tables = {"v": {}, "vt": {}, "vn": {}}
    remap = {"v": [], "vt": [], "vn": []}
    order = {"v": [], "vt": [], "vn": []}
    body = []
    for l in text.splitlines():
        if l.startswith("#") or not l.strip(): continue
        tag = l.split(" ", 1)[0]
        if tag in tables:
            key = " ".join(num(n, 3 if tag == "vn" else 4) for n in l.split()[1:])
            if key not in tables[tag]:
                tables[tag][key] = len(order[tag]) + 1; order[tag].append(key)
            remap[tag].append(tables[tag][key])
        elif tag == "f":
            parts = []
            for c in l.split()[1:]:
                ids = c.split("/")
                out = [str(remap["v"][int(ids[0]) - 1])]
                if len(ids) > 1: out.append(str(remap["vt"][int(ids[1]) - 1]) if ids[1] else "")
                if len(ids) > 2: out.append(str(remap["vn"][int(ids[2]) - 1]))
                parts.append("/".join(out))
            body.append("f " + " ".join(parts))
        elif tag in ("mtllib", "usemtl", "o", "g"):
            body.append(l)
    head = [l for l in body if l.startswith("mtllib")]
    rest = [l for l in body if not l.startswith("mtllib")]
    verts = ["%s %s" % (tag, k) for tag in ("v", "vt", "vn") for k in order[tag]]
    return "\n".join(head + verts + rest) + "\n"


def export_to_game(name, out, display, glowing=(), textures=None, render_type=None):
    """Move a Blender export into the mod: the OBJ (slimmed) and MTL into models/item, its texture(s) into textures/item,
    and the item model JSON that has Forge's OBJ loader draw it.
    glowing: the material names lit by their own colour (Ka 1 - Forge's OBJ loader turns Ka into a light level);
    textures: material name -> texture name (default: every material uses NAME)."""
    import json, shutil
    textures = textures or {}
    mdir, tdir = os.path.join(ASSETS, "models", "item"), os.path.join(ASSETS, "textures", "item")
    open(os.path.join(mdir, name + ".obj"), "w").write(slim_obj(open(os.path.join(out, name + ".obj")).read()))
    mtl, used = [], set()
    for l in open(os.path.join(out, name + ".mtl")).read().splitlines():
        if not l.startswith("newmtl"): continue        # everything else Blender writes is rebuilt below
        m = l.split()[1]
        tex = textures.get(m, name); used.add(tex)
        mtl += [l, "Kd 1 1 1",                          # Forge tints by Kd: keep it white
                "Ka 1 1 1" if m in glowing else "Ka 0 0 0",
                "map_Kd iron_spell_grimoire:item/" + tex, ""]
    open(os.path.join(mdir, name + ".mtl"), "w").write("\n".join(mtl))
    for tex in used: shutil.copy(os.path.join(out, tex + ".png"), os.path.join(tdir, tex + ".png"))
    model = {"loader": "forge:obj", "model": "iron_spell_grimoire:models/item/%s.obj" % name, "flip_v": True,
             "textures": {"particle": "iron_spell_grimoire:item/" + name}, "display": display}
    if render_type: model["render_type"] = render_type
    json.dump(model, open(os.path.join(mdir, name + ".json"), "w"), indent=1)
    print("written into the mod's assets")


def to_game(out):
    export_to_game("gilded_thorn", out, DISPLAY)


if __name__ == "__main__":
    mode, out = sys.argv[1], sys.argv[2]
    d = build(out)
    if mode == "render": render(d, out)
    if mode == "game": to_game(out)
