"""The wild families (Mirefolk, Stonefolk, Sandwraiths) and the night hunter, for the approval sheet. Run with
Blender's Python (bpy). Bodies are the player's shape in their skins (tools/family_skins.py); their gear is modelled
here (original designs):

  bog_crawler   a bog-oak club, a crown of reeds      mire_hexer   a crooked staff with a marsh-light in a cage
  fen_matron    a crown of roots and antlers, a bone rattle, a moss train
  scree_brute   a stone maul, rock plates on the shoulders     stonecaller  a staff with a floating stone, amber-lit
  peak_warden   a horned helm, a slab shield, a pick-axe
  dune_stalker  two curved blades, a trailing head-wrap   sandwraith   a hanging censer, a ragged sand mantle
  sun_herald    a sun disc behind the head, a glaive
  gloamstalker  a hooked long blade, a hood, a coat's long tails

  python tools/blender/family_models.py render OUT_DIR SKIN_DIR   the sheet
"""
import math, os, sys
import bpy
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import rival_models as rm
import elite_models as em
import gilded_thorn as gt
import architect_ruin as ar

# four more colours on the gear sheet
EXTRA = {"moss": (70, 104, 52), "bark": (92, 66, 44), "bone": (226, 218, 192), "amber": (255, 176, 60), "marsh": (150, 255, 140), "sun": (255, 210, 110)}
for i, n in enumerate(EXTRA):
    k = 26 + i
    rm.REG[n] = ((k % 8) * 32, (k // 8) * 32, (k % 8) * 32 + 32, (k // 8) * 32 + 32)
REG = rm.REG


def paint(out):
    rm.paint(out)
    im = Image.open(out).convert("RGBA"); px = im.load()
    for n, c in EXTRA.items():
        x0, y0, x1, y1 = [v * rm.K for v in REG[n]]
        for x in range(x0, x1):
            for y in range(y0, y1):
                k = 0.86 + 0.28 * ar.fbm(x / 5.0, y / 5.0, 7 + len(n))
                px[x, y] = tuple(max(0, min(255, int(c[i] * k))) for i in range(3)) + (255,)
    im.save(out)


cyl = rm.cyl
GLOW = em.GLOW


# ------------------------------------------------------------------ gear
def bog_club():
    cyl("haft", (0, 0, -3), 0.7, 14, "bark", sides=7)
    for i, (x, z) in enumerate([(0.8, 9), (-0.9, 7), (0.5, 5.5)]): em.gem("knot%d" % i, (x, 0, z), 1.3, 1.4, "bark", sides=5, glow=False)
    em.rbox("head", (0, 0, 11), (2.6, 2.6, 3.4), "bark", bevel=0.3)
    for i in range(4): em.box("moss%d" % i, ((i % 2 - 0.5) * 2.2, (i // 2 - 0.5) * 2.2, 12.4), (1.2, 1.2, 0.6), "moss")

def reed_crown():
    for i in range(9):
        a = i * 2 * math.pi / 9
        em.rbox("reed%d" % i, (math.cos(a) * 4.3, math.sin(a) * 4.3, 1.5 + (i % 3) * 0.8), (0.5, 0.5, 3 + (i % 3) * 1.5), "moss", tilt=0.25 * math.sin(a), roll=0.25 * math.cos(a))

def hexer_staff():
    cyl("staff", (0, 0, -8), 0.5, 26, "bark", sides=6)
    em.rbox("crook", (1.4, 0, 18.5), (3, 0.9, 0.9), "bark", roll=0.6)
    for i in range(4):
        a = i * math.pi / 2
        em.rbox("bar%d" % i, (2.6 + math.cos(a) * 1.3, math.sin(a) * 1.3, 16), (0.3, 0.3, 3.4), "iron")
    em.gem("light", (2.6, 0, 15.6), 1.0, 1.4, "marsh", sides=6, glow=True)

def root_crown():
    em.torus("band", (0, 0, 0.5), 4.4, 0.6, "bark", M=12, N=5)
    for s in (-1, 1):
        em.rbox("ant%d" % s, (s * 3.6, 0, 3.8), (0.7, 0.7, 6.5), "bone", roll=-s * 0.45)
        em.rbox("tine%d" % s, (s * 5.2, 0, 5.8), (0.5, 0.5, 3), "bone", roll=-s * 1.1)
        em.rbox("root%d" % s, (s * 2.0, -2.5, 2.4), (0.6, 0.6, 4), "bark", roll=s * 0.3, tilt=-0.3)

def bone_rattle():
    cyl("handle", (0, 0, -2), 0.45, 7, "bone", sides=6)
    em.gem("gourd", (0, 0, 6.2), 1.8, 2.2, "bark", sides=7, glow=False)
    for i in range(5): em.gem("bead%d" % i, (math.cos(i) * 1.9, math.sin(i) * 1.9, 4.4 - i * 0.5), 0.45, 0.5, "bone", sides=4, glow=False)

def moss_train():
    for i in range(6): em.rbox("strand%d" % i, (-4 + i * 1.6, 2.6, -4 - (i % 2) * 1.5), (1.4, 0.5, 13 + (i % 3) * 2), "moss", tilt=0.15)

def stone_maul():
    cyl("haft", (0, 0, -6), 0.7, 20, "wood", sides=6)
    em.rbox("head", (0, 0, 15.5), (7, 4, 4), "slate", bevel=0.6)
    em.rbox("band", (0, 0, 15.5), (1.2, 4.3, 4.3), "iron")
    for i, (x, z) in enumerate([(2.2, 17.2), (-2.6, 14.0)]): em.gem("chip%d" % i, (x, -2.2, z), 0.8, 0.6, "slate", sides=4, glow=False)

def rock_plates():
    for s in (-1, 1):
        em.rbox("plate%d" % s, (s * 6.0, 0, 23.5), (4.6, 5.2, 2.2), "slate", roll=s * 0.35, bevel=0.5)
        em.gem("spur%d" % s, (s * 7.4, 0, 24.8), 0.9, 1.6, "slate", sides=4, glow=False)

def caller_staff():
    cyl("staff", (0, 0, -8), 0.55, 25, "slate", sides=6)
    for i in range(3): em.torus("ring%d" % i, (0, 0, 14 + i * 1.6), 0.9, 0.25, "bronze", M=8, N=4)
    em.rbox("stone", (0, 0, 21.5), (3.6, 3.2, 3.8), "slate", yaw=0.6, tilt=0.4, bevel=0.5)
    for i in range(4):
        a = i * math.pi / 2 + 0.3
        em.gem("rune%d" % i, (math.cos(a) * 2.9, math.sin(a) * 2.9, 21.5), 0.4, 0.6, "amber", sides=4, glow=True)

def horned_helm():
    for s in (-1, 1):
        em.rbox("horn%d" % s, (s * 5.2, 0, 2.8), (1.6, 1.6, 4), "bone", roll=-s * 0.8)
        em.gem("tip%d" % s, (s * 7.1, 0, 5.4), 0.8, 2.2, "bone", sides=5, glow=False)
    em.rbox("crest", (0, 0, 4.8), (1, 7, 1.6), "iron")

def slab_shield():
    em.rbox("slab", (0, 0, 0), (9, 1.6, 13), "slate", bevel=0.5)
    em.rbox("rim", (0, -0.9, 0), (9.6, 0.3, 1.2), "iron")
    em.rbox("rim2", (0, -0.9, 5.6), (9.6, 0.3, 1.2), "iron")
    em.gem("boss", (0, -1.2, 1), 1.6, 1.0, "bronze", sides=6, glow=False)

def warden_pick():
    cyl("haft", (0, 0, -5), 0.6, 18, "wood", sides=6)
    em.prism("pick", [(-6, 12), (-1, 13.6), (1, 13.6), (5, 12.4), (1, 14.6), (-1, 14.6)], -0.6, 0.6, "iron")

def curved_blade():
    pts = [(-0.4, 0), (0.6, 0), (1.6, 6), (2.6, 11), (1.0, 13.5), (0.6, 10), (-0.2, 5)]
    em.prism("blade", pts, -0.3, 0.3, "steel")
    em.rbox("guard", (0, 0, 0), (3, 1, 0.6), "bronze")
    cyl("grip", (0, 0, -3.6), 0.5, 3.6, "leather", sides=6)

def head_wrap():
    em.rbox("tail", (1.5, 3.2, -1.5), (1.6, 0.4, 7), "sand", tilt=0.35, roll=-0.2)
    em.rbox("tail2", (-0.6, 3.0, -3.0), (1.3, 0.4, 5), "sand", tilt=0.25, roll=0.2)

def censer():
    for i in range(3): em.rbox("chain%d" % i, (math.cos(i * 2.1) * 0.6, math.sin(i * 2.1) * 0.6, -3), (0.2, 0.2, 6), "bronze")
    em.gem("bowl", (0, 0, -7), 2.0, 1.6, "bronze", sides=8, glow=False)
    em.gem("ember", (0, 0, -6.4), 1.1, 0.9, "amber", sides=6, glow=True)

def sand_mantle():
    for i in range(7): em.rbox("rag%d" % i, (-5 + i * 1.7, 2.4, -2 - (i % 3) * 1.2), (1.6, 0.4, 9 + (i % 2) * 3), "sand", tilt=0.2)

def sun_disc():
    em.torus("disc", (0, 0, 0), 5.2, 0.7, "gold", M=16, N=5)
    for i in range(12):
        a = i * math.pi / 6
        em.rbox("ray%d" % i, (math.cos(a) * 6.8, 0, math.sin(a) * 6.8), (0.6, 0.4, 2.2), "sun", roll=-a + math.pi / 2)
    em.gem("core", (0, 0.2, 0), 1.6, 0.6, "sun", sides=8, glow=True)

def glaive():
    cyl("haft", (0, 0, -10), 0.55, 28, "white", sides=6)
    em.prism("blade", [(-0.8, 17.5), (2.6, 19), (3.0, 23), (0, 27.5), (-0.8, 22)], -0.35, 0.35, "gold")
    em.torus("collar", (0, 0, 17.6), 0.9, 0.3, "red", M=8, N=4)

def hooked_blade():
    em.prism("blade", [(-0.5, 0), (0.6, 0), (0.8, 16), (3.4, 19), (2.4, 20.5), (-0.3, 18)], -0.3, 0.3, "black")
    em.prism("edge", [(0.5, 1), (0.8, 1), (1.0, 15.5), (3.2, 18.6), (2.9, 19.2), (0.6, 16.4)], -0.36, 0.36, "blue", glow=True)
    em.rbox("guard", (0, 0, 0), (2.4, 1.0, 0.6), "black")
    cyl("grip", (0, 0, -4.4), 0.5, 4.4, "black", sides=6)

def gloam_hood():   # open at the front, so the mask shows
    em.rbox("top", (0, 0.6, 5.0), (9.6, 9.8, 1.0), "black", bevel=0.3)
    em.rbox("back", (0, 5.0, 0.6), (9.6, 1.0, 9.6), "black", bevel=0.3)
    for s in (-1, 1): em.rbox("side%d" % s, (s * 4.8, 0.8, 0.6), (1.0, 9.4, 9.6), "black", bevel=0.3)
    em.rbox("peak", (0, 1.6, 6.2), (5, 5, 2.0), "black", tilt=-0.4)
    em.rbox("rim", (0, -4.3, 4.6), (9.8, 0.6, 0.8), "blue")

def coat_tails():
    for s in (-1, 1): em.rbox("tail%d" % s, (s * 2.2, 2.4, -6), (3.8, 0.5, 13), "black", tilt=0.18, roll=s * 0.1)
    em.rbox("lining", (0, 2.05, -5), (8, 0.2, 10), "blue")


GEAR = {"bog_club": bog_club, "reed_crown": reed_crown, "hexer_staff": hexer_staff, "root_crown": root_crown, "bone_rattle": bone_rattle,
        "moss_train": moss_train, "stone_maul": stone_maul, "rock_plates": rock_plates, "caller_staff": caller_staff, "horned_helm": horned_helm,
        "slab_shield": slab_shield, "warden_pick": warden_pick, "curved_blade": curved_blade, "head_wrap": head_wrap, "censer": censer,
        "sand_mantle": sand_mantle, "sun_disc": sun_disc, "glaive": glaive, "hooked_blade": hooked_blade, "gloam_hood": gloam_hood, "coat_tails": coat_tails}
rm.GEAR.update(GEAR)

def hand(side, arm, slim=False, k=1.0, reach=10.0):
    """Where the fist is, in the sheet (pixels, feet at 0, front -Y), on an arm raised arm degrees (negative: forward)."""
    a = math.radians(-arm)
    aw = 3 if slim else 4
    return (side * (4 + aw / 2) * k, -reach * math.sin(a) * k, (22 - reach * math.cos(a)) * k)


# each one: skin, slim, scale, the arms' pose, and its gear - (form, bone, the arm's angle, where, turn, scale).
# A weapon's grip sits in the fist: where = hand(...), and a blade held out points forward along the forearm (40 - arm).
FAMILY = {}
def fam(name, slim, k, pose, gear): FAMILY[name] = (slim, k, pose, gear)
fam("bog_crawler", False, 0.95, {"rarm": (-35, 0, -6)}, [
    ("bog_club", "RARM", -35, hand(-1, -35, False, 0.95), (75, 0, 0), 0.95),
    ("reed_crown", "HEAD", 0, (0, 0, 31.5 * 0.95), (0, 0, 0), 0.95)])
fam("mire_hexer", True, 1.0, {"rarm": (-20, 0, -6)}, [
    ("hexer_staff", "RARM", -20, hand(-1, -20, True), (-8, 0, 0), 1)])
fam("fen_matron", True, 1.18, {"larm": (-40, 0, 6)}, [
    ("root_crown", "HEAD", 0, (0, 0, 32 * 1.18), (0, 0, 0), 1.18),
    ("bone_rattle", "LARM", -40, hand(1, -40, True, 1.18), (80, 0, 0), 1.18),
    ("moss_train", "BODY", 0, (0, 0, 15 * 1.18), (0, 0, 0), 1.18)])
fam("scree_brute", False, 1.15, {"rarm": (-30, 0, -6)}, [
    ("stone_maul", "RARM", -30, hand(-1, -30, False, 1.15), (70, 0, 0), 1.15),
    ("rock_plates", "BODY", 0, (0, 0, 0), (0, 0, 0), 1.15)])
fam("stonecaller", False, 1.0, {"larm": (-20, 0, 6)}, [
    ("caller_staff", "LARM", -20, hand(1, -20), (-8, 0, 0), 1)])
fam("peak_warden", False, 1.22, {"rarm": (-30, 0, -6), "larm": (-20, 0, 6)}, [
    ("horned_helm", "HEAD", 0, (0, 0, 27.5 * 1.22), (0, 0, 0), 1.22),
    ("slab_shield", "LARM", -20, (7.6 * 1.22, -5.5 * 1.22, 13 * 1.22), (0, 0, 10), 1.22),
    ("warden_pick", "RARM", -30, hand(-1, -30, False, 1.22), (70, 0, 0), 1.22)])
fam("dune_stalker", False, 1.0, {"rarm": (-40, 0, -6), "larm": (-40, 0, 6), "rleg": (-10, 0, 0), "lleg": (10, 0, 0)}, [
    ("curved_blade", "RARM", -40, hand(-1, -40), (80, 0, 0), 1.3),
    ("curved_blade", "LARM", -40, hand(1, -40), (80, 0, 0), 1.3),
    ("head_wrap", "HEAD", 0, (0, 0, 29), (0, 0, 0), 1)])
fam("sandwraith", True, 1.0, {"larm": (-30, 0, 6)}, [
    ("censer", "LARM", -30, hand(1, -30, True), (-30, 0, 0), 1.3),
    ("sand_mantle", "BODY", 0, (0, 0, 21), (0, 0, 0), 1)])
fam("sun_herald", False, 1.2, {"rarm": (-20, 0, -6)}, [
    ("sun_disc", "BODY", 0, (0, 5 * 1.2, 29 * 1.2), (0, 0, 0), 1.2),
    ("glaive", "RARM", -20, hand(-1, -20, False, 1.2), (-8, 0, 0), 1.2)])
fam("gloamstalker", True, 1.15, {"rarm": (-50, 0, -6)}, [
    ("hooked_blade", "RARM", -50, hand(-1, -50, True, 1.15), (90, 0, 0), 1.15 * 1.4),
    ("gloam_hood", "HEAD", 0, (0, 0, 27 * 1.15), (0, 0, 0), 1.15),
    ("coat_tails", "BODY", 0, (0, 0, 13 * 1.15), (0, 0, 0), 1.15)])


def build(name):
    """One of them, with its gear (Blender: the front faces -Y)."""
    slim, k, pose, gear = FAMILY[name]
    rm.body("family_" + name, slim, scale=k, pose=pose)
    for form, bone, arm, at, rot, sc in gear: rm.gear(form, at, rot, sc)


def java():
    """The same placements for client/RivalRenderer (paste into FAMILY_GEAR)."""
    out = []
    for name, (slim, k, pose, gear) in FAMILY.items():
        # in game the renderer scales the whole body by k: the placements go in unscaled
        ps = ",\n".join('                    new Piece("%s", Bone.%s, %g, v(%.3f, %.3f, %.3f), v(%g, %g, %g), %.3ff)' % ((f, b, a) + tuple(c / k for c in at) + tuple(rot) + (sc / k,)) for f, b, a, at, rot, sc in gear)
        out.append('            Map.entry("family_%s", List.of(\n%s))' % (name, ps))
    return ",\n".join(out)


def to_game(out):
    """The gear into the mod, on the rivals' sheet (models/item/rival_<form>, textures/item/rivals.png - its four new cells)."""
    import shutil
    rm.GEAR.clear(); rm.GEAR.update(GEAR)
    rm.paint = paint_into
    rm.to_game(out)


_rm_paint = rm.paint
def paint_into(out):
    _rm_paint(out)
    im = Image.open(out).convert("RGBA"); px = im.load()
    for n, c in EXTRA.items():
        x0, y0, x1, y1 = [v * rm.K for v in REG[n]]
        for x in range(x0, x1):
            for y in range(y0, y1):
                kk = 0.86 + 0.28 * ar.fbm(x / 5.0, y / 5.0, 7 + len(n))
                px[x, y] = tuple(max(0, min(255, int(c[i] * kk))) for i in range(3)) + (255,)
    im.save(out)


ORDER = ["bog_crawler", "mire_hexer", "fen_matron", "scree_brute", "stonecaller", "peak_warden", "dune_stalker", "sandwraith", "sun_herald", "gloamstalker"]
NAMES = {"bog_crawler": "Bog Crawler", "mire_hexer": "Mire Hexer", "fen_matron": "Fen Matron (leader)", "scree_brute": "Scree Brute",
         "stonecaller": "Stonecaller", "peak_warden": "Peak Warden (leader)", "dune_stalker": "Dune Stalker", "sandwraith": "Sandwraith",
         "sun_herald": "Sun Herald (leader)", "gloamstalker": "Gloamstalker (night hunter)"}


def render(out, skins):
    from PIL import ImageDraw
    rm.SKINS = skins
    tex = os.path.join(out, "family_gear.png")
    paint(tex)
    CW, CH = 300, 470
    for name in ORDER:
        for view, yaw in (("front", -28), ("back", 152)):
            bpy.ops.wm.read_factory_settings(use_empty=True)
            gt.PARTS.clear(); GLOW.clear()
            image = bpy.data.images.load(tex)
            rm.MATS["glow"], rm.MATS["plain"] = rm.gear_materials(image)
            build(name)
            empty = bpy.data.objects.new("pivot", None); bpy.context.collection.objects.link(empty)
            for o in list(bpy.data.objects):
                if o.type == "MESH": o.parent = empty
            empty.rotation_euler = (0, 0, math.radians(yaw))
            sc = bpy.context.scene
            sc.render.engine = "CYCLES"; sc.cycles.samples = 20; sc.cycles.device = "CPU"
            sc.view_settings.view_transform = "Standard"
            sc.render.resolution_x, sc.render.resolution_y = CW, CH
            world = bpy.data.worlds.new("w"); sc.world = world; world.use_nodes = True
            world.node_tree.nodes["Background"].inputs[0].default_value = (0.1, 0.1, 0.12, 1)
            for ln, rot, energy in (("key", (math.radians(50), 0, math.radians(-35)), 3.6), ("fill", (math.radians(70), 0, math.radians(150)), 1.6), ("top", (0.2, 0, 0), 1.0)):
                L = bpy.data.lights.new(ln, "SUN"); L.energy = energy
                lo = bpy.data.objects.new(ln, L); lo.rotation_euler = rot; sc.collection.objects.link(lo)
            cam = bpy.data.cameras.new("cam"); cam.type = "ORTHO"; cam.ortho_scale = 56
            co = bpy.data.objects.new("cam", cam); sc.collection.objects.link(co); sc.camera = co
            el = math.radians(10)
            co.location = (0, -200 * math.cos(el), 22 + 200 * math.sin(el)); co.rotation_euler = (math.radians(90) - el, 0, 0)
            sc.render.filepath = os.path.join(out, "fam_%s_%s.png" % (name, view))
            bpy.ops.render.render(write_still=True)
    half = len(ORDER) // 2
    for part, names in (("a", ORDER[:half]), ("b", ORDER[half:])):
        sheet = Image.new("RGB", (CW * len(names), CH * 2 + 30), (12, 12, 16))
        d = ImageDraw.Draw(sheet)
        for i, name in enumerate(names):
            for r, view in enumerate(("front", "back")):
                sheet.paste(Image.open(os.path.join(out, "fam_%s_%s.png" % (name, view))).convert("RGB"), (i * CW, 30 + r * CH))
            d.text((i * CW + 10, 8), NAMES[name], fill=(235, 228, 210))
        sheet.save(os.path.join(out, "family_sheet_%s.png" % part))


if __name__ == "__main__":
    mode, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    if mode == "render": render(out, sys.argv[3])
    if mode == "game": to_game(out)
    if mode == "java": print(java())
