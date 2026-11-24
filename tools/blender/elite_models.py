"""The elite mob trait models, made in Blender (run with Blender's Python: bpy). Original designs, in the same
weathered, realistic style as the fortress pieces:

  Shield (Bulwark)        a heater shield of dark oak planks, an iron rim with rivets, a bronze boss
  Plate (Ironhide)        a curved slab of riveted stone-iron armour; three are worn, one cracks off a hit
  Shard (Reflector)       a long silvered crystal in a bronze collar; five circle the mob while it reflects
  Chain link (Tether)     one oval iron link; the chain is drawn as a row of them
  Chest + lid (Cursed)    an iron-banded chest of black wood with a row of bone teeth along its mouth and a glowing lock
  Rift (Rift-Born)        a ring of jagged dark stones round a tear of glowing void

Blender space is in pixels (exported at 1/16): Z up, the front toward -Y.

  python tools/blender/elite_models.py render OUT_DIR    a sheet of every model
  python tools/blender/elite_models.py game OUT_DIR      writes the models into the mod (models/item, textures/item)
"""
import math, os, sys
import bpy, bmesh
from mathutils import Vector, Matrix
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import architect_ruin as ar   # (its noise and helpers)
import gilded_thorn as gt

TEX = "elite"
gt.REG.clear()
gt.REG.update({"steel": (0, 0, 64, 64), "iron": (64, 0, 128, 64), "bronze": (128, 0, 192, 64), "wood": (192, 0, 256, 64),
               "wood_d": (0, 64, 64, 128), "stone": (64, 64, 128, 128), "stone_d": (128, 64, 192, 128), "bone": (192, 64, 256, 128),
               "mirror": (0, 128, 64, 192), "void": (64, 128, 128, 192), "ember": (128, 128, 192, 192), "leather": (192, 128, 256, 192),
               "rust": (0, 192, 64, 256), "dark": (64, 192, 96, 224)})
REG = gt.REG
ar.REG = REG
GLOW = []
K = 2
mix, fbm, _hash = ar.mix, ar.fbm, ar._hash


def paint(out):
    S = 256 * K
    im = Image.new("RGBA", (S, S), (0, 0, 0, 255)); px = im.load()
    def region(name, fn):
        x0, y0, x1, y1 = [c * K for c in REG[name]]
        for x in range(x0, x1):
            for y in range(y0, y1):
                u, v = (x - x0) / (x1 - x0 - 1), (y - y0) / (y1 - y0 - 1)
                px[x, y] = tuple(max(0, min(255, int(c))) for c in fn(u, v, x - x0, y - y0)) + (255,)
    def metal(base, var, seed, patina=None, scratches=True):
        def f(u, v, x, y):
            n = fbm(x / 6.0, y / 6.0, seed)
            c = tuple(base[i] + (n - 0.5) * var[i] * 2 + (_hash(x, y, seed) - 0.5) * 16 for i in range(3))
            if scratches and _hash(x // 1, (y + x // 3) // 1, seed + 3) > 0.985: c = mix(c, (200, 200, 205), 0.5)
            if patina:
                p = fbm(x / 8.0, y / 8.0, seed + 4)
                if p > 0.6: c = mix(c, patina, min(1, (p - 0.6) * 5))
            return c
        return f
    region("steel", metal((112, 116, 124), (28, 28, 30), 1))
    region("iron", metal((58, 58, 62), (18, 18, 20), 2, (100, 60, 34)))
    region("bronze", metal((134, 100, 56), (34, 26, 16), 3, (70, 110, 92)))
    region("rust", metal((96, 58, 36), (30, 18, 10), 4, (60, 40, 28), scratches=False))
    def wood(base, dark, seed):
        def f(u, v, x, y):
            w = math.sin(x * 0.55 + fbm(x / 6.0, y / 20.0, seed) * 7.0) * 0.5 + 0.5
            c = mix(dark, base, 0.3 + 0.5 * w + (_hash(x, y, seed) - 0.5) * 0.15)
            if x % 16 == 0: c = mix(c, (14, 10, 6), 0.7)   # plank seams
            return c
        return f
    region("wood", wood((110, 80, 50), (60, 42, 26), 5))
    region("wood_d", wood((54, 40, 30), (24, 18, 14), 6))
    def stone(base, var, seed):
        def f(u, v, x, y):
            n = fbm(x / 9.0, y / 9.0, seed)
            crack = 1.0 if abs(fbm(x / 14.0, y / 14.0, seed + 5, 3) - 0.5) < 0.012 else 0.0
            k = 0.55 + (n - 0.5) * 0.9 + _hash(x, y, seed + 3) * 0.18 - crack * 0.35
            return tuple(base[i] + (k - 0.5) * var[i] * 2 for i in range(3))
        return f
    region("stone", stone((112, 108, 100), (34, 32, 30), 7))
    region("stone_d", stone((52, 48, 54), (20, 18, 22), 8))
    region("bone", stone((206, 196, 170), (24, 22, 18), 9))
    region("mirror", lambda u, v, x, y: mix((250, 252, 255), (140, 170, 200), abs(math.sin((x * 0.7 + y * 0.35))) * 0.6 + fbm(x / 4, y / 4, 10) * 0.4))
    region("void", lambda u, v, x, y: mix((235, 190, 255), (70, 20, 120), min(1, math.hypot(u - 0.5, v - 0.5) * 2.2) * 0.8 + fbm(x / 3, y / 3, 11) * 0.3))
    region("ember", lambda u, v, x, y: mix((255, 220, 140), (230, 90, 30), abs(v - 0.5) * 2 + fbm(x / 3, y / 3, 12) * 0.3))
    region("leather", lambda u, v, x, y: mix((70, 44, 28), (110, 74, 46), fbm(x / 5, y / 5, 13) * 0.9))
    region("dark", lambda u, v, x, y: (14, 12, 12))
    im.save(out)


# ------------------------------------------------------------------ helpers (as the fortress script's, without bevels in the game copy)
def box(name, c, s, region, bevel=0.0, glow=False):
    o = gt.box(name, c, s, region, bevel=0.0 if ar.LOW else bevel, segs=1)
    if glow: GLOW.append(o)
    return o


def rbox(name, c, s, region, yaw=0.0, tilt=0.0, roll=0.0, bevel=0.0):
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    bmesh.ops.scale(bm, vec=s, verts=bm.verts)
    if bevel > 0 and not ar.LOW: bmesh.ops.bevel(bm, geom=bm.verts[:] + bm.edges[:], offset=bevel, segments=1, affect="EDGES", profile=0.5)
    if roll: bmesh.ops.rotate(bm, cent=(0, 0, 0), matrix=Matrix.Rotation(roll, 3, "Y"), verts=bm.verts)
    if tilt: bmesh.ops.rotate(bm, cent=(0, 0, 0), matrix=Matrix.Rotation(tilt, 3, "X"), verts=bm.verts)
    bmesh.ops.rotate(bm, cent=(0, 0, 0), matrix=Matrix.Rotation(yaw, 3, "Z"), verts=bm.verts)
    bmesh.ops.translate(bm, vec=c, verts=bm.verts)
    return gt.new_obj(name, bm, REG[region], smooth=False)


def prism(name, poly, y0, y1, region, glow=False):
    """A flat shape (x, z points) given thickness along Y."""
    bm = bmesh.new()
    a = [bm.verts.new((x, y0, z)) for x, z in poly]
    b = [bm.verts.new((x, y1, z)) for x, z in poly]
    bm.faces.new(a); bm.faces.new(b[::-1])
    n = len(poly)
    for i in range(n):
        j = (i + 1) % n
        bm.faces.new((a[i], a[j], b[j], b[i]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bmesh.ops.triangulate(bm, faces=[f for f in bm.faces if len(f.verts) > 4])
    o = gt.new_obj(name, bm, REG[region], smooth=False)
    if glow: GLOW.append(o)
    return o


def torus(name, c, R, r, region, sx=1.0, M=12, N=6, yaw=0.0):
    bm = bmesh.new(); rows = []
    for i in range(M):
        a = 2 * math.pi * i / M
        ring = []
        for j in range(N):
            b = 2 * math.pi * j / N
            x = (R + r * math.cos(b)) * math.cos(a) * sx
            z = (R + r * math.cos(b)) * math.sin(a)
            y = r * math.sin(b)
            ring.append(bm.verts.new((x, y, z)))
        rows.append(ring)
    for i in range(M):
        for j in range(N):
            bm.faces.new((rows[i][j], rows[(i + 1) % M][j], rows[(i + 1) % M][(j + 1) % N], rows[i][(j + 1) % N]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bmesh.ops.rotate(bm, cent=(0, 0, 0), matrix=Matrix.Rotation(yaw, 3, "Z"), verts=bm.verts)
    bmesh.ops.translate(bm, vec=c, verts=bm.verts)
    return gt.new_obj(name, bm, REG[region], smooth=False)


def gem(name, c, r, h, region, sides=6, glow=True):
    bm = bmesh.new()
    top = bm.verts.new((c[0], c[1], c[2] + h)); bot = bm.verts.new((c[0], c[1], c[2] - h * 0.35))
    ring = [bm.verts.new((c[0] + r * math.cos(a * 2 * math.pi / sides), c[1] + r * math.sin(a * 2 * math.pi / sides), c[2])) for a in range(sides)]
    for i in range(sides):
        bm.faces.new((ring[i], ring[(i + 1) % sides], top)); bm.faces.new((ring[(i + 1) % sides], ring[i], bot))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    o = gt.new_obj(name, bm, REG[region], smooth=False)
    if glow: GLOW.append(o)
    return o


# ------------------------------------------------------------------ the models
def heater(scale, inset=0.0):
    """The outline of a heater shield (x, z), 12 wide, 16 tall, centred."""
    pts = []
    W, T, B = 6 * scale - inset, 7.5 * scale - inset, -8.5 * scale + inset * 1.4
    pts += [(-W, T), (W, T)]
    for k in range(1, 9):   # the curved sides down to the point
        a = k / 9
        pts.append((W * math.cos(a * math.pi / 2) ** 0.8, T - (T - B) * (a ** 1.4)))
    pts.append((0, B))
    for k in range(8, 0, -1):
        a = k / 9
        pts.append((-W * math.cos(a * math.pi / 2) ** 0.8, T - (T - B) * (a ** 1.4)))
    return pts


def shield():
    prism("face", heater(1.0, 0.6), -0.6, 0.6, "wood_d")                         # oak planks
    rim = heater(1.0)
    for i in range(len(rim)):                                                    # the iron rim, piece by piece
        (x0, z0), (x1, z1) = rim[i], rim[(i + 1) % len(rim)]
        L = math.hypot(x1 - x0, z1 - z0)
        rbox("rim%d" % i, ((x0 + x1) / 2, 0, (z0 + z1) / 2), (L + 0.3, 1.6, 0.9), "iron", roll=-math.atan2(z1 - z0, x1 - x0))
    for i, (x, z) in enumerate(heater(1.0, 1.2)[::2]):                           # rivets
        box("rv%d" % i, (x, -0.9, z), (0.6, 0.4, 0.6), "steel")
    box("bandh", (0, -0.75, 2.5), (11, 0.4, 1.4), "iron")                         # a cross band
    box("bandv", (0, -0.75, -0.5), (1.4, 0.4, 14.5), "iron")
    gem("boss", (0, -0.9, 0.6), 2.4, 1.2, "bronze", sides=8, glow=False)           # the bronze boss
    bpy.data.objects["boss"].rotation_euler = (math.radians(90), 0, 0)
    bpy.data.objects["boss"].location = (0, -1.0, 0.6)
    box("grip", (0, 1.1, 0), (1.2, 1.0, 6), "leather")                             # the strap behind


def plate():
    bm = bmesh.new()                                                               # a curved slab: a strip of a cylinder
    rows = []
    for i in range(7):
        a = math.radians(-34 + 68 * i / 6)
        rows.append([(math.sin(a) * 9, 9 - math.cos(a) * 9, z) for z in (-3.5, 3.5)])
    outer = [[bm.verts.new(p) for p in r] for r in rows]
    inner = [[bm.verts.new((p[0] * 0.88, p[1] + 1.1, p[2])) for p in r] for r in rows]
    for i in range(6):
        bm.faces.new((outer[i][0], outer[i + 1][0], outer[i + 1][1], outer[i][1]))
        bm.faces.new((inner[i][1], inner[i + 1][1], inner[i + 1][0], inner[i][0]))
        bm.faces.new((outer[i][1], outer[i + 1][1], inner[i + 1][1], inner[i][1]))
        bm.faces.new((inner[i][0], inner[i + 1][0], outer[i + 1][0], outer[i][0]))
    for s in (0, 6):
        r = outer[s]; q = inner[s]
        bm.faces.new((r[0], r[1], q[1], q[0]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    gt.new_obj("slab", bm, REG["stone_d"], smooth=False)
    for i, (x, z) in enumerate([(-4.2, 2.4), (4.2, 2.4), (-4.2, -2.4), (4.2, -2.4), (0, 0)]):   # rivets
        y = 9 - math.cos(math.asin(x / 9)) * 9 - 0.35
        box("rv%d" % i, (x, y, z), (0.9, 0.7, 0.9), "rust" if i == 4 else "iron")
    box("edge", (0, 0.1, 3.2), (10, 0.6, 0.7), "iron")


def shard():
    gem("crystal", (0, 0, 2), 1.3, 7, "mirror", sides=5, glow=True)
    for i in range(6):
        a = i * math.pi / 3
        rbox("collar%d" % i, (math.cos(a) * 1.2, math.sin(a) * 1.2, 0.6), (0.9, 0.5, 1.2), "bronze", yaw=a)


def chain_link():
    torus("link", (0, 0, 0), 1.7, 0.55, "iron", sx=1.6, M=12, N=5)


def chest():
    box("body", (0, 0, 5), (14, 11.5, 10), "wood_d", bevel=0.3)
    for x in (-5, 0, 5): box("band%d" % (x + 5), (x, 0, 5), (1.2, 11.9, 10.3), "iron")
    for sx in (-1, 1):
        for sy in (-1, 1): box("corner%d%d" % (sx + 1, sy + 1), (sx * 6.6, sy * 5.4, 5), (1.2, 1.2, 10.4), "bronze")
    for i in range(9):   # bone teeth along the mouth (the front edge, facing -Y)
        x = -6 + i * 1.5
        gem("tooth%d" % i, (x, -5.6, 9.6), 0.55, 1.6, "bone", sides=4, glow=False)
    box("feet", (0, 0, 0.4), (14.6, 12, 0.8), "iron")


def chest_lid():
    r, n = 6.1, 6
    for i in range(n):   # a barrel-curved lid in staves, an iron band over each
        a = math.radians(-70 + 140 * (i + 0.5) / n)
        y, z = -math.sin(a) * r, 10 + (math.cos(a) - math.cos(math.radians(70))) * r
        rbox("stave%d" % i, (0, y, z), (14, 2.65, 1.1), "wood_d", tilt=a)
        for x in (-5, 0, 5):
            rbox("lb%d_%d" % (i, x + 5), (x, y * 1.03, z + math.cos(a) * 0.35), (1.2, 2.7, 0.5), "iron", tilt=a)
    for x0 in (-7.0, 6.4):   # the curved ends
        bm = bmesh.new()
        pts = [(-math.sin(math.radians(a)) * r, 10 + (math.cos(math.radians(a)) - math.cos(math.radians(70))) * r) for a in range(-70, 71, 10)]
        f0 = [bm.verts.new((x0, y, z)) for y, z in pts]
        f1 = [bm.verts.new((x0 + 0.6, y, z)) for y, z in pts]
        bm.faces.new(f0); bm.faces.new(f1[::-1])
        for i in range(len(pts) - 1): bm.faces.new((f0[i], f0[i + 1], f1[i + 1], f1[i]))
        bm.faces.new((f0[-1], f0[0], f1[0], f1[-1]))
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
        bmesh.ops.triangulate(bm, faces=[f for f in bm.faces if len(f.verts) > 4])
        gt.new_obj("end%d" % int(x0), bm, REG["wood_d"], smooth=False)
    for i in range(9):   # the upper jaw: teeth pointing down along the front edge
        x = -6 + i * 1.5
        gem("ltooth%d" % i, (0, 0, 0), 0.55, 1.4, "bone", sides=4, glow=False)
        o = bpy.data.objects["ltooth%d" % i]; o.rotation_euler = (math.radians(180), 0, 0); o.location = (x, -5.4, 10.2)
    box("lock", (0, -6.2, 10.9), (2.4, 0.9, 2.6), "bronze")
    box("keyhole", (0, -6.7, 10.8), (0.8, 0.3, 1.2), "ember", glow=True)


def rift():
    rnd = ar.random.Random(31)
    n = 11
    for i in range(n):   # jagged dark stones round an upright oval
        a = 2 * math.pi * i / n
        x, z = math.cos(a) * 8, 12 + math.sin(a) * 12
        s = (rnd.uniform(2.4, 3.6), rnd.uniform(2.4, 3.4), rnd.uniform(3.0, 4.6))
        rbox("st%d" % i, (x, rnd.uniform(-0.8, 0.8), z), s, "stone_d" if rnd.random() < 0.7 else "stone", roll=-a + rnd.uniform(-0.4, 0.4), tilt=rnd.uniform(-0.3, 0.3))
    bm = bmesh.new()   # the tear: a jagged disc of glowing void, seen from both sides
    for side, y in ((1, -0.05), (-1, 0.05)):   # a front and a back face, a hair apart
        centre = bm.verts.new((0, y, 12))
        ring = []
        for i in range(18):
            a = 2 * math.pi * i / 18
            k = 0.82 + 0.12 * math.sin(i * 2.3)
            ring.append(bm.verts.new((math.cos(a) * 7 * k, y, 12 + math.sin(a) * 11 * k)))
        for i in range(18):
            bm.faces.new((centre, ring[i], ring[(i + 1) % 18]) if side > 0 else (centre, ring[(i + 1) % 18], ring[i]))
    o = gt.new_obj("tear", bm, REG["void"], smooth=False); GLOW.append(o)
    for i in range(5):   # shards drifting off it
        a = rnd.uniform(0, 2 * math.pi)
        gem("chip%d" % i, (math.cos(a) * 10.5, rnd.uniform(-1, 1), 12 + math.sin(a) * 14), 0.7, 1.4, "void", sides=4, glow=True)


def chest_full(): chest(); chest_lid()


MODELS = {"shield": shield, "plate": plate, "shard": shard, "chain_link": chain_link, "chest": chest, "chest_lid": chest_lid, "rift": rift, "chest_full": chest_full}
NAMES = {"shield": "Bulwark: shield", "plate": "Ironhide: plate (x3)", "shard": "Reflector: mirror shard (x5)", "chain_link": "Tether: chain link",
         "chest": "Cursed Loot: chest", "chest_lid": "Cursed Loot: lid", "rift": "Rift-Born: rift", "chest_full": "Cursed Loot: chest, shut"}
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
        bsdf.inputs["Roughness"].default_value = 0.7
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
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
    print(form, "tris", tris(ob))
    return ob


def render_sheet(out_dir):
    CW, CH = 420, 420
    for form in ORDER:
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
        cam = bpy.data.cameras.new("cam"); cam.type = "ORTHO"; cam.ortho_scale = max(h, w) * 1.35
        co = bpy.data.objects.new("cam", cam); sc.collection.objects.link(co); sc.camera = co
        el = math.radians(20)
        co.location = ((min(xs) + max(xs)) / 2, -200 * math.cos(el), (min(zs) + max(zs)) / 2 + 200 * math.sin(el))
        co.rotation_euler = (math.radians(90) - el, 0, 0)
        sc.render.filepath = os.path.join(out_dir, "elite_%s.png" % form)
        bpy.ops.render.render(write_still=True)
    cols = 4
    rows = (len(ORDER) + cols - 1) // cols
    sheet = Image.new("RGB", (CW * cols, CH * rows), (10, 10, 14))
    d = ImageDraw.Draw(sheet)
    for i, form in enumerate(ORDER):
        x, y = (i % cols) * CW, (i // cols) * CH
        sheet.paste(Image.open(os.path.join(out_dir, "elite_%s.png" % form)).convert("RGB"), (x, y))
        d.text((x + 10, y + 8), NAMES[form], fill=(235, 228, 210))
    sheet.save(os.path.join(out_dir, "elite_sheet.png"))


def to_game(out_dir):
    for form in [f for f in ORDER if f != "chest_full"]:
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
        bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
        name = "elite_" + form
        ob.name = name
        ob.location = (0, 0, 0)
        bpy.ops.wm.obj_export(filepath=os.path.join(out_dir, name + ".obj"), export_selected_objects=True, export_uv=True, export_normals=True,
                              export_materials=True, export_triangulated_mesh=True, forward_axis="NEGATIVE_Z", up_axis="Y", global_scale=1 / 16, path_mode="STRIP")
        n = tris(ob)
        print(form, "tris", n)
        assert n >= 24, form + " needs at least 24 triangles to be drawn"
        disp = {"gui": {"rotation": [20, 225, 0], "translation": [0, 0, 0], "scale": [0.6] * 3},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5] * 3},
                "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5] * 3}}
        gt.export_to_game(name, out_dir, disp, glowing={form + "_glow"}, textures={form: TEX, form + "_glow": TEX}, render_type="minecraft:cutout")


if __name__ == "__main__":
    mode, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    paint(os.path.join(out, TEX + ".png"))
    if mode == "render": render_sheet(out)
    if mode == "game": ar.LOW = True; to_game(out)
