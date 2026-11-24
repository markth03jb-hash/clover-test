"""The Meridian Grimoire's weapon - Dayreaver, a great two-handed sun-axe - for the approval sheet and for the game.
Run with Blender's Python (bpy). An original design: a half-sun blade of gold-bronze, sundial hour lines inlaid across
both faces, short rays round its rim, a glowing sun-disc where blade meets haft, a triangular gnomon fin as the back
spike, and a long dark haft bound with gold rings, ending in a small sun knot.

Blender space (pixels; 16 = one block): Z up the haft, the blade on the +X side, Y through its faces. Origin at the
middle of the lower grip, where the back hand closes.

  python tools/blender/meridian_models.py render OUT_DIR    the sheet (three views, and held, beside a player)
  python tools/blender/meridian_models.py game OUT_DIR      into the mod (models/item/dayreaver.*, using textures/item/rivals.png)
"""
import math, os, sys
import bpy
from mathutils import Matrix
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import elite_models as em
import architect_ruin as ar
import gilded_thorn as gt
import rival_models as rm

TEX = "rivals"   # the rivals' texture sheet has the metals, woods and glows this needs
GLOW = em.GLOW
GRIP = -8.0      # the lower grip's middle, from the haft's foot


def dayreaver():
    # the haft: dark wood, gold rings, a leather-wrapped grip at the foot and another under the blade
    rm.cyl("haft", (0, 0, -14), 0.75, 44, "wood", sides=8)
    for z in (-14.6, -2.2, 12.5, 26.0):
        rm.cyl("ring%d" % int(z * 10), (0, 0, z), 1.0, 0.9, "gold", sides=8)
    rm.cyl("grip_lo", (0, 0, -13.4), 0.82, 10.6, "leather", sides=8)
    rm.cyl("grip_hi", (0, 0, 13.6), 0.82, 5.0, "leather", sides=8)
    em.gem("knot", (0, 0, -15.4), 1.3, 1.4, "gold", sides=8, glow=False)
    o = bpy.data.objects["knot"]; o.rotation_euler = (math.pi, 0, 0); o.location = (0, 0, -30.2)
    # the blade: a half sun on the +X side, centred on the haft at z 23
    cz, R, t = 23.0, 10.5, 0.55
    half = [(0.6, cz - R * 0.98)] + [(0.6 + R * math.cos(a), cz + R * math.sin(a)) for a in [math.radians(d) for d in range(-80, 81, 10)]] + [(0.6, cz + R * 0.98)]
    em.prism("blade", half, -t, t, "bell")
    # the edge: a pale honed band round the rim
    edge = []
    for d in range(-84, 85, 6):
        a = math.radians(d)
        edge.append((0.6 + (R + 1.0) * math.cos(a), cz + (R + 1.0) * math.sin(a)))
    for d in range(84, -85, -6):
        a = math.radians(d)
        edge.append((0.6 + (R - 0.2) * math.cos(a), cz + (R - 0.2) * math.sin(a)))
    em.prism("edge", edge, -t * 0.55, t * 0.55, "steel")
    # short rays standing out past the edge, between the hours
    for i, d in enumerate(range(-60, 61, 30)):
        a = math.radians(d)
        c, s = math.cos(a), math.sin(a)
        base1 = (0.6 + (R + 0.6) * math.cos(a - 0.07), cz + (R + 0.6) * math.sin(a - 0.07))
        base2 = (0.6 + (R + 0.6) * math.cos(a + 0.07), cz + (R + 0.6) * math.sin(a + 0.07))
        tip = (0.6 + (R + 3.0) * c, cz + (R + 3.0) * s)
        em.prism("ray%d" % i, [base1, tip, base2], -t * 0.35, t * 0.35, "gold")
    # the hour lines: inlaid dark strips fanning from the sun-disc, on both faces
    for d in range(-75, 76, 15):
        a = math.radians(d)
        L = R - 2.6
        mx, mz = 0.6 + (2.6 + L / 2) * math.cos(a), cz + (2.6 + L / 2) * math.sin(a)
        for y in (-t - 0.03, t + 0.03):
            em.rbox("hour%d%s" % (d, "f" if y < 0 else "b"), (mx, y, mz), (L, 0.06, 0.32), "iron", yaw=0, roll=-a)
    # the sun-disc where blade meets haft (glows), framed in gold
    for y, nm in ((-t - 0.25, "f"), (t + 0.25, "b")):
        g = em.gem("disc" + nm, (0, 0, 0), 2.4, 0.5, "bell", sides=12, glow=True)
        g.rotation_euler = (math.radians(90 if y < 0 else -90), 0, 0); g.location = (1.2, y, cz)
    em.torus("frame", (1.2, 0, cz), 2.6, 0.35, "gold", M=14, N=5)   # (a torus here lies in the blade's plane already)
    # the gnomon: a triangular fin on the back, the sundial's shadow-caster
    em.prism("gnomon", [(-0.6, cz - 3.5), (-6.5, cz + 1.0), (-0.6, cz + 4.5)], -0.3, 0.3, "bronze")
    em.prism("gnomon_edge", [(-0.6, cz + 4.5), (-6.5, cz + 1.0), (-6.9, cz + 1.1), (-0.6, cz + 5.1)], -0.18, 0.18, "gold")
    # the crown: a spearpoint sun-ray above the blade
    em.prism("crown", [(-0.8, 31.0), (0, 36.5), (0.8, 31.0)], -0.45, 0.45, "gold")


def build():
    before = list(gt.PARTS)
    dayreaver()
    new = [o for o in gt.PARTS if o not in before]
    for o in new: o.location = (o.location[0], o.location[1], o.location[2] - GRIP)
    for o in new:
        if o.type != "MESH":
            bpy.ops.object.select_all(action="DESELECT"); o.select_set(True); bpy.context.view_layer.objects.active = o
            bpy.ops.object.convert(target="MESH")
    return new


def join(parts, name):
    bpy.ops.object.select_all(action="DESELECT")
    for o in parts: o.select_set(True)
    bpy.context.view_layer.objects.active = parts[0]
    bpy.ops.object.join()
    ob = bpy.context.view_layer.objects.active
    ob.name = name
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    return ob


def scene(cam_scale, cam_z, yaw, out_png, W=420, H=640):
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"; sc.cycles.samples = 24; sc.cycles.device = "CPU"
    sc.view_settings.view_transform = "Standard"
    sc.render.resolution_x, sc.render.resolution_y = W, H
    world = bpy.data.worlds.new("w"); sc.world = world; world.use_nodes = True
    world.node_tree.nodes["Background"].inputs[0].default_value = (0.1, 0.1, 0.12, 1)
    for ln, rot, energy in (("key", (math.radians(50), 0, math.radians(-35)), 3.6), ("fill", (math.radians(70), 0, math.radians(150)), 1.6), ("top", (0.2, 0, 0), 1.0)):
        L = bpy.data.lights.new(ln, "SUN"); L.energy = energy
        lo = bpy.data.objects.new(ln, L); lo.rotation_euler = rot; sc.collection.objects.link(lo)
    empty = bpy.data.objects.new("pivot", None); bpy.context.collection.objects.link(empty)
    for o in list(bpy.data.objects):
        if o.type == "MESH": o.parent = empty
    empty.rotation_euler = (0, 0, math.radians(yaw))
    cam = bpy.data.cameras.new("cam"); cam.type = "ORTHO"; cam.ortho_scale = cam_scale
    co = bpy.data.objects.new("cam", cam); sc.collection.objects.link(co); sc.camera = co
    el = math.radians(8)
    co.location = (0, -200 * math.cos(el), cam_z + 200 * math.sin(el)); co.rotation_euler = (math.radians(90) - el, 0, 0)
    sc.render.filepath = out_png
    bpy.ops.render.render(write_still=True)


def fresh(out):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    gt.PARTS.clear(); GLOW.clear(); rm.MATS.clear()
    image = bpy.data.images.load(os.path.join(out, TEX + ".png"))
    rm.MATS["glow"], rm.MATS["plain"] = rm.gear_materials(image)


def material(parts):
    for o in parts:
        o.data.materials.clear(); o.data.materials.append(rm.MATS["glow"] if o in GLOW else rm.MATS["plain"])


def render_sheet(out):
    rm.paint(os.path.join(out, TEX + ".png"))
    # a plain grey player for scale
    sk = Image.new("RGBA", (64, 64), (150, 150, 158, 255))
    d = ImageDraw.Draw(sk); d.rectangle((8, 8, 15, 15), fill=(120, 120, 128, 255)); d.rectangle((40, 8, 47, 15), fill=(0, 0, 0, 0))
    os.makedirs(os.path.join(out, "skins"), exist_ok=True)
    sk.save(os.path.join(out, "skins", "grey.png"))
    rm.SKINS = os.path.join(out, "skins")
    shots = []
    for view, yaw in (("side", 0), ("angled", -35), ("back", 180)):
        fresh(out)
        parts = build(); material(parts); ob = join(parts, "dayreaver")
        ob.rotation_euler = (0, math.radians(90), 0)   # the whole axe turned: haft level, the blade facing the ground
        ob.location = (-19, 0, 26)
        p = os.path.join(out, "dayreaver_%s.png" % view)
        scene(60, 22, yaw, p, W=640, H=420); shots.append(p)
    # held: two hands on the haft, blade up over the right shoulder, beside a plain player
    fresh(out)
    rm.body("grey", False, pose={"rarm": (-80, 0, -6), "larm": (-70, 0, 30)})
    parts = build(); material(parts)
    ax = join(parts, "dayreaver")
    ax.rotation_euler = (0, math.radians(90), math.radians(-90))   # held out in front, haft level, the blade facing the ground
    ax.location = (-6.0, -9.8, 20.3)
    for o in list(bpy.data.objects):   # the whole scene shifted so the pair sits in the middle of the picture
        if o.type == "MESH": o.location = (o.location[0] + 14, o.location[1], o.location[2])                                 # the lower grip in the right fist
    p = os.path.join(out, "dayreaver_held.png")
    scene(80, 16, -40, p, W=640, H=420); shots.append(p)
    sheet = Image.new("RGB", (640 * 2, 450 * 2), (12, 12, 16))
    dr = ImageDraw.Draw(sheet)
    for i, (s, label) in enumerate(zip(shots, ("side", "angled", "back", "held (for scale)"))):
        x, y = (i % 2) * 640, (i // 2) * 450
        sheet.paste(Image.open(s).convert("RGB"), (x, y + 30)); dr.text((x + 10, y + 8), "Dayreaver - " + label, fill=(235, 228, 210))
    sheet.save(os.path.join(out, "dayreaver_sheet.png"))


def to_game(out):
    rm.paint(os.path.join(out, TEX + ".png"))
    ar.LOW = True
    bpy.ops.wm.read_factory_settings(use_empty=True)
    gt.PARTS.clear(); GLOW.clear()
    parts = build()
    glow_m, plain_m = bpy.data.materials.new("dayreaver_glow"), bpy.data.materials.new("dayreaver")
    for o in parts: o.data.materials.clear(); o.data.materials.append(glow_m if o in GLOW else plain_m)
    ob = join(parts, "dayreaver")
    # the game draws an item model about the middle of its block (8 px in from the origin on each axis): moved by that
    # much, the grip sits in the fist and the axe turns about it
    ob.location = (8, -8, 8)
    bpy.ops.object.transform_apply(location=True, rotation=False, scale=False)
    bpy.ops.wm.obj_export(filepath=os.path.join(out, "dayreaver.obj"), export_selected_objects=True, export_uv=True, export_normals=True,
                          export_materials=True, export_triangulated_mesh=True, forward_axis="NEGATIVE_Z", up_axis="Y", global_scale=1 / 16, path_mode="STRIP")
    print("dayreaver tris", em.tris(ob))
    # held: the haft level and forward out of the fist, the blade facing the ground (turned about the haft), tilted down
    # to cancel the arm's forward lean
    # (in the hand the item's axes are: x outward, y forward, z up - ItemInHandLayer turns the arm's -90 about x and 180
    # about y. So a quarter about y puts the blade (+x) down and keeps the haft (+y) forward; -18 about x cancels the
    # arm's forward lean. The left hand's entry is mirrored by the game.)
    disp = {"thirdperson_righthand": {"rotation": [-18, 90, 0], "translation": [0, 0, 0], "scale": [0.9] * 3},
            "thirdperson_lefthand": {"rotation": [-18, -90, 0], "translation": [0, 0, 0], "scale": [0.9] * 3},
            # in first person the haft points ahead into the view, a little up, the blade hanging under it
            "firstperson_righthand": {"rotation": [-75, 90, 0], "translation": [1.5, -1, -2], "scale": [0.55] * 3},
            "firstperson_lefthand": {"rotation": [-75, -90, 0], "translation": [1.5, -1, -2], "scale": [0.55] * 3},
            "gui": {"rotation": [0, 90, -45], "translation": [-4, -4, 0], "scale": [0.3] * 3},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4] * 3},
            "fixed": {"rotation": [0, 90, -45], "translation": [-4, -4, 0], "scale": [0.36] * 3}}
    gt.export_to_game("dayreaver", out, disp, glowing={"dayreaver_glow"}, textures={"dayreaver": TEX, "dayreaver_glow": TEX}, render_type="minecraft:cutout")


if __name__ == "__main__":
    mode, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    if mode == "render": render_sheet(out)
    if mode == "game": to_game(out)
