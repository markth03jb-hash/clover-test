"""The Grimoire of the Bound Demon's models - the Demon-Bound Katana and the one great wing of its awakening - for the
approval sheet and for the game. Run with Blender's Python (bpy). Original designs:
  Katana: a gently curved, single-edged blade of near-black steel with a thin crimson seam that glows along its length,
          a gold collar, an eight-sided black guard with a red demon's eye set in each face, a black grip bound in a
          red diamond wrap, a dark iron cap at the end.
  Wing:   one great wing - long primary feathers fanning from the shoulder, black at the tips paling to salt-white at
          the root, a row of shorter covert feathers over them.

Blender space (pixels; 16 = one block): Z up the blade, the edge on the +X side, Y through the flat. Katana origin at the
middle of the grip; wing origin at its root (the shoulder).

  python tools/blender/bound_models.py render OUT_DIR    the sheet (katana side/angled, held, and the wing on a player)
  python tools/blender/bound_models.py game OUT_DIR      into the mod (models/item/demon_katana.*, bound_wing.*)
"""
import math, os, sys
import bpy
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import elite_models as em
import architect_ruin as ar
import gilded_thorn as gt
import rival_models as rm
import meridian_models as mm

TEX = "rivals"
GLOW = em.GLOW
GRIP = -4.5   # the grip's middle


def katana():
    # the blade: a gentle curve, edge on +X, from the collar (z 0.6) to the tip
    L = 20.0
    spine, edge = [], []
    for i in range(0, 21):
        z = 0.6 + L * i / 20
        back = 0.0   # a straight blade
        w = 1.35 - 0.25 * i / 20
        spine.append((back, z)); edge.append((back + w, z))
    tip = (spine[-1][0] + 0.15, L + 2.4)
    poly = edge + [tip] + list(reversed(spine))
    em.prism("blade", poly, -0.3, 0.3, "black")
    # the honed edge, pale steel
    hone = [(x - 0.35, z) for x, z in edge] + [tip] + [(x, z) for x, z in reversed(edge)]
    em.prism("edge", hone, -0.18, 0.18, "steel")
    # the crimson seam, glowing, a little proud of both faces
    seam = [(x + 0.42, z) for x, z in spine[1:-2]] + [(x + 0.6, z) for x, z in reversed(spine[1:-2])]
    em.prism("seam", seam, -0.34, 0.34, "red", glow=True)
    # the collar (habaki)
    em.rbox("collar", (0.65, 0, 0.9), (1.7, 0.9, 1.2), "gold")
    # the guard: eight-sided, black, with a red eye in each flat face
    g = em.gem("guard", (0, 0, 0), 2.6, 0.55, "black", sides=8, glow=False)
    g.location = (0.5, 0, -0.3)
    for y, nm in ((-0.36, "f"), (0.36, "b")):
        e = em.gem("eye" + nm, (0, 0, 0), 0.9, 0.18, "red", sides=10, glow=True)
        e.rotation_euler = (math.radians(90 if y < 0 else -90), 0, 0); e.location = (0.5, y, -0.05)
    # the grip: black, wrapped in red diamonds, an iron cap
    rm.cyl("grip", (0.5, 0, -9.2), 0.8, 8.8, "black", sides=8)
    for k in range(7):
        z = -8.6 + k * 1.2
        em.rbox("wrap%d" % k, (0.5, 0, z), (1.15, 1.75, 0.55), "red", yaw=0, roll=math.radians(45))
    rm.cyl("cap", (0.5, 0, -9.9), 0.95, 0.9, "iron", sides=8)


def wing():
    """The first awakening's wing: one enormous wing out of the upper left shoulder made of branches, not feathers - a
    long arched spine reaching up and out, and from it limbs that hang like flight feathers and fork, and fork again,
    into thin pale plumes. A faint glow along the finest tips."""
    import random as _r
    rnd = _r.Random(7)
    spine = [(0.0, 0.0), (5.0, 14.0), (13.0, 27.0), (24.0, 35.0), (36.0, 37.0), (45.0, 33.0)]
    for i in range(len(spine) - 1):
        (x0, z0), (x1, z1) = spine[i], spine[i + 1]
        L = math.hypot(x1 - x0, z1 - z0); nx, nz = -(z1 - z0) / L, (x1 - x0) / L
        w = 1.9 - i * 0.3
        em.prism("sp%d" % i, [(x0 + nx * w, z0 + nz * w), (x1 + nx * w * 0.85, z1 + nz * w * 0.85), (x1 - nx * w * 0.85, z1 - nz * w * 0.85), (x0 - nx * w, z0 - nz * w)], -w * 0.6, w * 0.6, "white")
    def along(t):
        n = len(spine) - 1; seg = min(n - 1, int(t * n)); f = t * n - seg
        (x0, z0), (x1, z1) = spine[seg], spine[seg + 1]
        return x0 + (x1 - x0) * f, z0 + (z1 - z0) * f
    def limb(name, x0, z0, ang, ln, w, depth):
        x1, z1 = x0 + math.cos(ang) * ln, z0 + math.sin(ang) * ln
        nx, nz = -math.sin(ang), math.cos(ang)
        em.prism(name, [(x0 + nx * w, z0 + nz * w), (x1 + nx * w * 0.55, z1 + nz * w * 0.55), (x1 - nx * w * 0.55, z1 - nz * w * 0.55), (x0 - nx * w, z0 - nz * w)],
                 -w * 0.5, w * 0.5, "white", glow=depth == 0 and rnd.random() < 0.35)
        if depth == 0:
            return
        for k, da in enumerate((-0.32, 0.28)):   # it forks
            limb(name + str(k), x1, z1, ang + da + rnd.uniform(-0.08, 0.08), ln * rnd.uniform(0.55, 0.7), w * 0.6, depth - 1)
    # the hanging limbs: short at the shoulder, longest past the middle, swept out toward the tip
    n = 13
    for i in range(n):
        t = 0.08 + 0.9 * i / (n - 1)
        x, z = along(t)
        ang = math.radians(-100 + 72 * t) + rnd.uniform(-0.06, 0.06)
        ln = (7 + 13 * math.sin(math.pi * min(1.0, 0.15 + t * 0.95))) * rnd.uniform(0.9, 1.1)
        limb("l%d" % i, x, z, ang, ln, 1.0 + 0.3 * t, 2)
    em.gem("root", (0, 0, 0), 2.4, 2.0, "white", sides=8, glow=True)


def black_wings():
    """The true awakening: one wing of a ragged pair (the other is mirrored in game) - black, irregular, feathers of
    uneven length, some missing, some torn short."""
    import random as _r
    rnd = _r.Random(23)
    arm = [(0.0, 0.0), (6.0, 11.0), (15.0, 19.0), (25.0, 22.0)]
    def along(t):
        n = len(arm) - 1; seg = min(n - 1, int(t * n)); f = t * n - seg
        (x0, z0), (x1, z1) = arm[seg], arm[seg + 1]
        return x0 + (x1 - x0) * f, z0 + (z1 - z0) * f
    for i in range(len(arm) - 1):
        (x0, z0), (x1, z1) = arm[i], arm[i + 1]
        L = math.hypot(x1 - x0, z1 - z0); nx, nz = -(z1 - z0) / L, (x1 - x0) / L
        w = 1.3 - i * 0.25
        em.prism("barm%d" % i, [(x0 + nx * w, z0 + nz * w), (x1 + nx * w, z1 + nz * w), (x1 - nx * w, z1 - nz * w), (x0 - nx * w, z0 - nz * w)], -0.5, 0.5, "black")
    for i in range(20):
        if rnd.random() < 0.15: continue   # a gap: a feather lost
        t = 0.03 + 0.96 * i / 19
        x, z = along(t)
        ang = math.radians(-98 + 70 * t) + rnd.uniform(-0.18, 0.18)
        ln = (8 + 18 * math.sin(math.pi * min(1.0, 0.2 + t))) * (rnd.uniform(0.45, 0.7) if rnd.random() < 0.25 else rnd.uniform(0.85, 1.15))   # some torn short
        dx, dz = math.cos(ang), math.sin(ang); nx, nz = -dz, dx
        w = 1.3 + 0.5 * t
        pts = [(x + nx * w * 0.6, z + nz * w * 0.6), (x + dx * ln * 0.5 + nx * w, z + dz * ln * 0.5 + nz * w), (x + dx * ln * 0.8 + nx * w * rnd.uniform(0.2, 0.9), z + dz * ln * 0.8 + nz * w * rnd.uniform(0.2, 0.9)),
               (x + dx * ln, z + dz * ln), (x + dx * ln * 0.75 - nx * w * rnd.uniform(0.2, 0.8), z + dz * ln * 0.75 - nz * w * rnd.uniform(0.2, 0.8)), (x - nx * w * 0.5, z - nz * w * 0.5)]
        y = 0.12 * (i % 3) - 0.12
        em.prism("bf%d" % i, pts, y - 0.18, y + 0.18, "black")
    em.gem("broot", (0, 0, 0), 1.8, 1.6, "black", sides=8, glow=False)


def build(fn, shift=0.0):
    before = list(gt.PARTS)
    fn()
    new = [o for o in gt.PARTS if o not in before]
    for o in new: o.location = (o.location[0], o.location[1], o.location[2] - shift)
    for o in new:
        if o.type != "MESH":
            bpy.ops.object.select_all(action="DESELECT"); o.select_set(True); bpy.context.view_layer.objects.active = o
            bpy.ops.object.convert(target="MESH")
    return new


def render_sheet(out):
    rm.paint(os.path.join(out, TEX + ".png"))
    sk = Image.new("RGBA", (64, 64), (150, 150, 158, 255))
    os.makedirs(os.path.join(out, "skins"), exist_ok=True)
    sk.save(os.path.join(out, "skins", "grey.png"))
    rm.SKINS = os.path.join(out, "skins")
    shots = []
    for view, yaw in (("side", 0), ("angled", -40)):
        mm.fresh(out)
        parts = build(katana, GRIP); mm.material(parts); ob = mm.join(parts, "katana")
        ob.rotation_euler = (0, math.radians(90), 0); ob.location = (-8, 0, 20)
        p = os.path.join(out, "katana_%s.png" % view)
        mm.scene(42, 20, yaw, p, W=640, H=420); shots.append(p)
    # held, the branched wing out of the upper left shoulder, seen from behind
    mm.fresh(out)
    rm.body("grey", False, pose={"rarm": (-80, 0, -6), "larm": (0, 0, 8)})
    parts = build(katana, GRIP); mm.material(parts)
    k = mm.join(parts, "katana")
    k.rotation_euler = (math.radians(-35), 0, 0); k.location = (-5.8, -8, 22)
    wp = build(wing); mm.material(wp)
    w = mm.join(wp, "wing")
    w.scale = (0.8, 0.8, 0.8); w.location = (2.5, 2.5, 26)   # out from the left shoulder (the camera is behind)
    for o in list(bpy.data.objects):
        if o.type == "MESH": o.location = (o.location[0] - 14, o.location[1], o.location[2])
    p = os.path.join(out, "bound_held.png")
    mm.scene(150, 44, 160, p, W=640, H=640); shots.append(p)
    # the branched wing alone
    mm.fresh(out)
    wp = build(wing); mm.material(wp); w = mm.join(wp, "wing")
    w.location = (-22, 0, -14)
    p = os.path.join(out, "bound_wing.png")
    mm.scene(80, 4, 0, p, W=640, H=640); shots.append(p)
    # the black pair (true awakening) on a player, from behind
    mm.fresh(out)
    rm.body("grey", False, pose={"rarm": (0, 0, -6), "larm": (0, 0, 8)})
    for side in (1, -1):
        bp = build(black_wings); mm.material(bp); bw = mm.join(bp, "bw%d" % side)
        bw.scale = (side, 1, 1); bw.location = (side * 2.5, 2.5, 22)
    p = os.path.join(out, "bound_black.png")
    mm.scene(90, 26, 180, p, W=640, H=640); shots.append(p)
    sheet = Image.new("RGB", (1920, 450 + 670), (12, 12, 16))
    dr = ImageDraw.Draw(sheet)
    for i, (s, label) in enumerate(zip(shots, ("katana - side", "katana - angled", "first awakening: the branched wing (from behind)", "the branched wing", "true awakening: the black pair (from behind)"))):
        x, y = (i * 640, 0) if i < 2 else ((i - 2) * 640, 450)
        sheet.paste(Image.open(s).convert("RGB"), (x, y + 30)); dr.text((x + 10, y + 8), label, fill=(235, 228, 210))
    sheet.save(os.path.join(out, "bound_sheet.png"))


def export(name, fn, shift, disp, out):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    gt.PARTS.clear(); GLOW.clear()
    parts = build(fn, shift)
    glow_m, plain_m = bpy.data.materials.new(name + "_glow"), bpy.data.materials.new(name)
    for o in parts: o.data.materials.clear(); o.data.materials.append(glow_m if o in GLOW else plain_m)
    ob = mm.join(parts, name)
    ob.location = (8, -8, 8)
    bpy.ops.object.transform_apply(location=True, rotation=False, scale=False)
    bpy.ops.wm.obj_export(filepath=os.path.join(out, name + ".obj"), export_selected_objects=True, export_uv=True, export_normals=True,
                          export_materials=True, export_triangulated_mesh=True, forward_axis="NEGATIVE_Z", up_axis="Y", global_scale=1 / 16, path_mode="STRIP")
    print(name, "tris", em.tris(ob))
    gt.export_to_game(name, out, disp, glowing={name + "_glow"}, textures={name: TEX, name + "_glow": TEX}, render_type="minecraft:cutout")


def to_game(out):
    rm.paint(os.path.join(out, TEX + ".png"))
    ar.LOW = True
    # held like a sword: the blade up and forward out of the fist, edge forward
    kd = {"thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 2, 0.5], "scale": [0.85] * 3},
          "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 2, 0.5], "scale": [0.85] * 3},
          "firstperson_righthand": {"rotation": [0, -90, 20], "translation": [1.5, 2, 0], "scale": [0.6] * 3},
          "firstperson_lefthand": {"rotation": [0, 90, -20], "translation": [1.5, 2, 0], "scale": [0.6] * 3},
          "gui": {"rotation": [0, 90, -45], "translation": [-1, -1, 0], "scale": [0.45] * 3},
          "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5] * 3},
          "fixed": {"rotation": [0, 90, -45], "translation": [0, 0, 0], "scale": [0.6] * 3}}
    export("demon_katana", katana, GRIP, kd, out)
    wd = {"fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
          "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.35] * 3}}
    export("bound_wing", wing, 0.0, wd, out)
    export("bound_wing_black", black_wings, 0.0, wd, out)


if __name__ == "__main__":
    mode, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    (render_sheet if mode == "render" else to_game)(out)
