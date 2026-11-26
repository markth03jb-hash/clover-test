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
GRIP = -5.1   # the grip's middle


def katana():
    """The Demon-Bound Katana (reworked): a long, gently curved blade of ink-black steel - the curve sweeps back toward
    the spine, the polished edge on +X is the convex side - a glowing crimson groove under the spine, three red seal
    marks near the collar, a proper point. A gold collar; a black oval guard with two short demon horns curling up toward
    the blade and a red eye on each face; a black grip bound in red, a horned iron cap and a torn red seal-ribbon."""
    L, Z0 = 23.0, 0.9
    N = 24
    spine, edge = [], []
    for i in range(N + 1):
        t = i / N
        z = Z0 + L * t
        back = -1.1 * t * t                      # the curve: back toward the spine
        w = 1.55 - 0.3 * t
        spine.append((back, z)); edge.append((back + w, z))
    # the point (kissaki): the edge sweeps up to meet the spine
    tz = Z0 + L + 2.6
    tip = (spine[-1][0] - 0.1, tz)
    kiss = [(edge[-1][0] - 0.2 * k - 0.05 * k * k, edge[-1][1] + 0.85 * k) for k in (1, 2, 3)]
    inner = [(x - 0.55, z) for x, z in edge]
    k_in = [(x - 0.5, z - 0.25) for x, z in kiss]
    em.prism("blade", inner + k_in + [tip] + list(reversed(spine)), -0.28, 0.28, "ink")
    # the polished edge: its own band of bright steel, tapering out from the black, the whole length and round the point
    em.prism("edge", edge + kiss + [tip] + list(reversed(k_in)) + list(reversed(inner)), -0.16, 0.16, "steel")
    # a fine glowing crimson groove under the spine
    g = spine[2:N - 3]
    em.prism("groove", [(x + 0.3, z) for x, z in g] + [(x + 0.46, z) for x, z in reversed(g)], -0.31, 0.31, "red", glow=True)
    # three seal marks near the collar (both faces)
    for k, z in enumerate((3.2, 5.0, 6.8)):
        for y, nm in ((-0.33, "f"), (0.33, "b")):
            e = em.gem("seal%d%s" % (k, nm), (0, 0, 0), 0.32, 0.12, "red", sides=4, glow=True)
            e.rotation_euler = (math.radians(90 if y < 0 else -90), 0, 0); e.location = (0.95, y, z)
    # the collar
    em.rbox("collar", (0.7, 0, 0.45), (1.9, 0.95, 1.0), "gold")
    # the guard: a black oval, two horns curling up toward the blade, a red eye on each face
    rm.cyl("guard", (0.6, 0, -0.55), 2.2, 0.55, "ink", sides=10)
    rm.cyl("guard_rim", (0.6, 0, -0.62), 2.35, 0.2, "red", sides=10)
    for sgn in (1, -1):   # the horns: out of the guard's ends, curling up toward the blade
        hx = 0.6 + sgn * 1.9
        pts_o = [(hx + sgn * 0.5, -0.4), (hx + sgn * 0.85, 0.6), (hx + sgn * 0.7, 1.6), (hx + sgn * 0.25, 2.5)]
        pts_i = [(hx + sgn * 0.05, 2.0), (hx + sgn * 0.25, 1.3), (hx + sgn * 0.2, 0.5), (hx - sgn * 0.15, -0.3)]
        em.prism("horn%d" % sgn, pts_o + pts_i, -0.22, 0.22, "ink")
    for y, nm in ((-0.38, "f"), (0.38, "b")):
        e = em.gem("eye" + nm, (0, 0, 0), 0.8, 0.2, "red", sides=10, glow=True)
        e.rotation_euler = (math.radians(90 if y < 0 else -90), 0, 0); e.location = (0.6, y, -0.3)
    # the grip: black, bound in red diamonds on both sides, a horned iron cap
    rm.cyl("grip", (0.6, 0, -9.6), 0.8, 9.0, "ink", sides=8)
    for k in range(8):
        z = -9.0 + k * 1.1
        em.rbox("wrap%d" % k, (0.6, 0, z), (1.1, 1.72, 0.5), "red", roll=math.radians(45 if k % 2 else -45))
    rm.cyl("cap", (0.6, 0, -10.4), 0.95, 0.9, "iron", sides=8)
    em.rbox("caphorn", (0.6, 0, -10.9), (0.4, 0.4, 0.9), "iron", roll=math.radians(20))
    # a torn red seal-ribbon hanging off the cap
    em.prism("ribbon", [(0.9, -10.6), (1.6, -12.8), (1.2, -14.4), (1.7, -15.6), (1.0, -15.0), (0.6, -13.0), (0.4, -10.8)], -0.08, 0.08, "red")


def seraph_halo():
    """The Last Seraph's halo: a ring of salt-white light, broken in three places, eight short rays standing out of it."""
    import math as _m
    R, segs = 6.0, 30
    for i in range(segs):
        if i % 10 in (0,):   # the breaks
            continue
        a0, a1 = 2 * _m.pi * i / segs, 2 * _m.pi * (i + 1) / segs
        pts = [(R * _m.cos(a0), R * _m.sin(a0)), (R * _m.cos(a1), R * _m.sin(a1)), ((R - 0.7) * _m.cos(a1), (R - 0.7) * _m.sin(a1)), ((R - 0.7) * _m.cos(a0), (R - 0.7) * _m.sin(a0))]
        em.prism("h%d" % i, pts, -0.3, 0.3, "white", glow=True)
    for k in range(8):
        a = 2 * _m.pi * (k + 0.5) / 8
        ln = 2.2 if k % 2 == 0 else 1.3
        c, s_ = _m.cos(a), _m.sin(a); nx, nz = -s_, c
        base = R + 0.2
        em.prism("ray%d" % k, [(c * base + nx * 0.35, s_ * base + nz * 0.35), (c * (base + ln), s_ * (base + ln)), (c * base - nx * 0.35, s_ * base - nz * 0.35)], -0.2, 0.2, "crystal", glow=True)


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
        ob.rotation_euler = (0, math.radians(90), 0); ob.location = (-6, 0, 20)
        p = os.path.join(out, "katana_%s.png" % view)
        mm.scene(48, 20, yaw, p, W=640, H=420); shots.append(p)
    # the halo alone
    mm.fresh(out)
    hp = build(seraph_halo); mm.material(hp); h = mm.join(hp, "halo")
    h.location = (0, 0, 20)
    p = os.path.join(out, "seraph_halo.png")
    mm.scene(26, 20, 0, p, W=640, H=420); shots.append(p)
    # the Last Seraph on a player, from behind: the wing (now much larger) and the halo over the head
    mm.fresh(out)
    rm.body("grey", False, pose={"rarm": (-80, 0, -6), "larm": (0, 0, 8)})
    parts = build(katana, GRIP); mm.material(parts)
    k = mm.join(parts, "katana")
    k.rotation_euler = (math.radians(-35), 0, 0); k.location = (-5.8, -8, 22)
    wp = build(wing); mm.material(wp)
    w = mm.join(wp, "wing")
    w.scale = (1.3, 1.3, 1.3); w.location = (2.5, 2.5, 26)
    hp = build(seraph_halo); mm.material(hp); h = mm.join(hp, "halo")
    h.scale = (0.75, 0.75, 0.75); h.location = (0, 4.0, 33)
    for o in list(bpy.data.objects):
        if o.type == "MESH": o.location = (o.location[0] - 10, o.location[1], o.location[2])
    p = os.path.join(out, "seraph_on_player.png")
    mm.scene(170, 42, 160, p, W=1280, H=640); shots.append(p)
    sheet = Image.new("RGB", (1920, 450 + 670), (12, 12, 16))
    dr = ImageDraw.Draw(sheet)
    labels = ("katana - side", "katana - angled", "the Seraph's halo", "Last Seraph: the wing (x1.3 larger) and the halo, from behind")
    pos = [(0, 0), (640, 0), (1280, 0), (0, 450)]
    for s_, label, (x, y) in zip(shots, labels, pos):
        sheet.paste(Image.open(s_).convert("RGB"), (x, y + 30)); dr.text((x + 10, y + 8), label, fill=(235, 228, 210))
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



def align_like_yami(obj_path):
    """Turn and move the exported katana into the Yami katana's frame (models/item/dark_katana_model.json): blade up +Y,
    its flat across X, the edge toward +Z, the guard's top at 5.7 px and the grip centred on (8, 8) - so the Yami
    katana's own display transforms hold it the same way. A turn of 90 degrees about Y (not a mirror: the faces keep
    their winding). Plain Python, run after export."""
    XC, ZC, DY = 8.6 / 16, 8.0 / 16, (5.7 - 13.3) / 16
    out = []
    for line in open(obj_path):
        if line.startswith("v ") or line.startswith("vn "):
            tag, x, y, z = line.split()[:4]
            x, y, z = float(x), float(y), float(z)
            if tag == "v": x, y, z = -(z - ZC) + 0.5, y + DY, (x - XC) + 0.5
            else: x, z = -z, x
            out.append("%s %.6f %.6f %.6f\n" % (tag, x, y, z))
        else: out.append(line)
    open(obj_path, "w").writelines(out)



def recolor_wing_black(obj_path):
    """The first wing in the Last Seraph's true colour: black (the anime's), its glowing finest tips left salt-white.
    Moves the UVs of the wing's plain faces from the atlas's "white" cell to its "ink" cell (rivals.png, 32 px cells:
    white at (224, 64), ink at (0, 96)); the glow material's faces keep theirs. Plain Python, run after export."""
    lines = open(obj_path).read().split("\n")
    vts = [i for i, l in enumerate(lines) if l.startswith("vt ")]
    plain, glow, mtl = set(), set(), None
    for l in lines:
        if l.startswith("usemtl"): mtl = l.split()[1]
        elif l.startswith("f "):
            for c in l.split()[1:]:
                t = int(c.split("/")[1]) - 1
                (glow if mtl.endswith("_glow") else plain).add(t)
    for t in plain - glow:
        u, v = map(float, lines[vts[t]].split()[1:3])
        lines[vts[t]] = "vt %.6f %.6f" % (u - 224 / 256, v - 32 / 256)
    open(obj_path, "w").write("\n".join(lines))


def to_game(out):
    rm.paint(os.path.join(out, TEX + ".png"))
    ar.LOW = True
    # held like a sword: the blade up and forward out of the fist, edge forward
    # (the edge is the model's +X; turned +90 about Y it faces the foe: forward, away from you)
    # held exactly as the Yami katana (dark_katana_model.json): its display transforms, with the model turned into its frame
    kd = {"thirdperson_righthand": {"rotation": [0, 180, -10], "translation": [0, 4, 0.5], "scale": [0.9] * 3},
          "thirdperson_lefthand": {"rotation": [0, 180, 10], "translation": [0, 4, 0.5], "scale": [0.9] * 3},
          "firstperson_righthand": {"rotation": [0, 180, 20], "translation": [1.13, 3.2, 1.13], "scale": [0.68] * 3},
          "firstperson_lefthand": {"rotation": [0, 180, -20], "translation": [1.13, 3.2, 1.13], "scale": [0.68] * 3},
          "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4] * 3},
          "fixed": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [0.5] * 3},
          "gui": {"rotation": [117.5, -138.5, 109], "translation": [-2.8, -2.8, 0], "scale": [0.58] * 3},
          "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.6] * 3}}
    export("demon_katana", katana, GRIP, kd, out)
    align_like_yami(os.path.join(out, "demon_katana.obj"))
    wd = {"fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
          "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.35] * 3}}
    export("bound_wing", wing, 0.0, wd, out)
    recolor_wing_black(os.path.join(out, "bound_wing.obj"))
    export("bound_wing_black", black_wings, 0.0, wd, out)
    export("seraph_halo", seraph_halo, 0.0, wd, out)


if __name__ == "__main__":
    mode, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    (render_sheet if mode == "render" else to_game)(out)
