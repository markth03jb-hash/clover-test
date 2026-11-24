"""The rivals (Seren Vale, Brannoch Tor and his Legionnaires, Alder and Wren Bellmar, Mira Hollowell) for the approval
sheet, and their gear for the game. Run with Blender's Python (bpy).

Each rival's body is the player's shape in their own skin (tools/rival_skins.py) - in game that's the boss renderer,
here it's rebuilt box by box with the same texture layout so the sheet shows them as they'll look. Their gear is
modelled here and exported as baked models:

  seren_blade     a long blade of pale crystal on a gold guard
  brannoch_sword  a broad war-sword          brannoch_banner  his war banner, worn on the back
  brannoch_pauldron  a curved steel shoulder plate edged in red (one each side)
  legion_shield   a red tower shield with the black tower     legion_spear  a leaf-bladed spear
  alder_sword     an arming sword with a bell for a pommel    wren_staff    a staff with a glowing bell in a bronze cage
  mira_hat        a wide-brimmed felt hat with a feather      mira_ledger   a thick ledger with brass corners
  mira_cat        her cat familiar, black with gold eyes

  python tools/blender/rival_models.py render OUT_DIR    the sheet
  python tools/blender/rival_models.py game OUT_DIR      the gear into the mod (models/item, textures/item/rivals.png)
"""
import math, os, sys
import bpy, bmesh
from mathutils import Matrix
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import elite_models as em   # (box, rbox, prism, torus, gem - and the noise)
import architect_ruin as ar
import gilded_thorn as gt

TEX = "rivals"
SKINS = os.path.join(HERE, "..", "..", "src/main/resources/assets/iron_spell_grimoire/textures/entity")
gt.REG.clear()
_NAMES = ["steel", "iron", "gold", "wood", "leather", "red", "black", "felt", "crystal", "bell", "parchment", "cream", "fur", "eye", "bronze", "slate",
          "silver", "sand", "copper", "blue", "brownfur", "page", "coin", "white", "ink", "rope"]
for _i, _n in enumerate(_NAMES): gt.REG[_n] = ((_i % 8) * 32, (_i // 8) * 32, (_i % 8) * 32 + 32, (_i // 8) * 32 + 32)   # 32 px cells
REG = gt.REG
GLOW = em.GLOW
mix, fbm, _hash = ar.mix, ar.fbm, ar._hash
K = 2


def paint(out):
    S = 256 * K
    im = Image.new("RGBA", (S, S), (0, 0, 0, 255)); px = im.load()
    def region(name, fn):
        x0, y0, x1, y1 = [c * K for c in REG[name]]
        for x in range(x0, x1):
            for y in range(y0, y1):
                u, v = (x - x0) / (x1 - x0 - 1), (y - y0) / (y1 - y0 - 1)
                px[x, y] = tuple(max(0, min(255, int(c))) for c in fn(u, v, x - x0, y - y0)) + (255,)
    def metal(base, var, seed, patina=None):
        def f(u, v, x, y):
            n = fbm(x / 6.0, y / 6.0, seed)
            c = tuple(base[i] + (n - 0.5) * var[i] * 2 + (_hash(x, y, seed) - 0.5) * 14 for i in range(3))
            if _hash(x, (y + x // 3), seed + 3) > 0.985: c = mix(c, (225, 225, 230), 0.5)
            if patina and fbm(x / 8.0, y / 8.0, seed + 4) > 0.62: c = mix(c, patina, 0.5)
            return c
        return f
    def cloth(base, var, seed, weave=True):
        def f(u, v, x, y):
            n = fbm(x / 7.0, y / 7.0, seed)
            k = 1 + (n - 0.5) * var + ((x + y) % 2) * 0.04 * weave - (_hash(x, y, seed) - 0.5) * 0.08
            return tuple(base[i] * k for i in range(3))
        return f
    region("steel", metal((150, 154, 162), (26, 26, 28), 1))
    region("iron", metal((62, 62, 68), (16, 16, 18), 2, (96, 60, 36)))
    region("gold", metal((206, 164, 80), (30, 24, 12), 3))
    region("bronze", metal((160, 112, 60), (30, 22, 12), 4, (74, 110, 92)))
    region("wood", lambda u, v, x, y: mix((70, 48, 30), (122, 88, 56), 0.5 + 0.5 * math.sin(x * 0.5 + fbm(x / 6, y / 18, 5) * 6)))
    region("leather", cloth((92, 60, 40), 0.5, 6, False))
    region("red", cloth((150, 36, 34), 0.4, 7))
    region("black", cloth((32, 28, 30), 0.5, 8))
    region("felt", cloth((104, 30, 44), 0.35, 9))
    region("cream", cloth((228, 218, 196), 0.25, 10))
    region("slate", cloth((74, 92, 112), 0.35, 11))
    region("parchment", lambda u, v, x, y: mix((236, 224, 192), (196, 176, 136), fbm(x / 5, y / 5, 12) * 0.8 + (0.4 if y % 4 == 0 else 0)))
    region("crystal", lambda u, v, x, y: mix((250, 252, 255), (170, 205, 240), abs(math.sin(x * 0.6 + y * 0.25)) * 0.6 + fbm(x / 4, y / 4, 13) * 0.4))
    region("bell", lambda u, v, x, y: mix((255, 236, 170), (240, 170, 70), fbm(x / 3, y / 3, 14) * 0.9))
    region("fur", lambda u, v, x, y: mix((16, 14, 16), (52, 48, 56), fbm(x / 2.5, y / 7, 15)))
    region("eye", lambda u, v, x, y: (255, 210, 70))
    def hair(base, dark, seed):
        return lambda u, v, x, y: mix(dark, base, 0.35 + 0.65 * (0.5 + 0.5 * math.sin(x * 1.3 + fbm(x / 3, y / 12, seed) * 5)))
    region("silver", hair((236, 240, 248), (170, 176, 192), 16))
    region("sand", hair((214, 178, 112), (150, 112, 62), 17))
    region("copper", hair((206, 104, 52), (130, 56, 28), 18))
    region("blue", cloth((60, 110, 190), 0.35, 19))
    region("brownfur", lambda u, v, x, y: mix((70, 50, 34), (150, 116, 80), fbm(x / 2, y / 6, 20)))
    region("page", lambda u, v, x, y: mix((255, 240, 200), (250, 210, 120), fbm(x / 3, y / 3, 21) * 0.6 + (0.35 if y % 5 == 0 and 4 < x < 28 else 0)))
    region("coin", metal((236, 196, 96), (24, 20, 8), 22))
    region("white", cloth((240, 238, 234), 0.15, 23))
    region("ink", lambda u, v, x, y: (20, 16, 24))
    region("rope", lambda u, v, x, y: mix((120, 92, 60), (180, 146, 100), 0.5 + 0.5 * math.sin((x + y) * 1.4)))
    im.save(out)


def cyl(name, c, r, h, region, sides=12, glow=False, top=True):
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=top, segments=sides, radius1=r, radius2=r, depth=h)
    bmesh.ops.translate(bm, vec=(c[0], c[1], c[2] + h / 2), verts=bm.verts)
    o = gt.new_obj(name, bm, REG[region], smooth=False)
    if glow: GLOW.append(o)
    return o


# ------------------------------------------------------------------ gear (grip or mount point at the origin; a weapon's blade along +Z)
def seren_blade():
    em.prism("blade", [(-1.1, 0.6), (1.1, 0.6), (1.0, 15.5), (0, 19.5), (-1.0, 15.5)], -0.35, 0.35, "crystal", glow=True)
    em.prism("edge", [(-0.25, 1.0), (0.25, 1.0), (0.2, 16), (0, 18.5), (-0.2, 16)], -0.5, 0.5, "crystal", glow=True)
    em.rbox("guard", (0, 0, 0.2), (6.4, 1.3, 1.0), "gold")
    for s in (-1, 1): em.gem("tip%d" % (s + 1), (s * 3.4, 0, 0.2), 0.6, 0.8, "crystal", sides=4)
    cyl("grip", (0, 0, -4.2), 0.55, 4.2, "leather", sides=8)
    em.gem("pommel", (0, 0, -4.8), 0.8, 0.9, "gold", sides=6, glow=False)


def brannoch_sword():
    em.prism("blade", [(-1.4, 0.4), (1.4, 0.4), (1.3, 19), (0, 22), (-1.3, 19)], -0.4, 0.4, "steel")
    em.box("fuller", (0, -0.42, 9), (0.6, 0.1, 15), "iron")
    em.box("fuller2", (0, 0.42, 9), (0.6, 0.1, 15), "iron")
    em.rbox("guard", (0, 0, 0), (8, 1.6, 1.2), "iron", bevel=0.2)
    cyl("grip", (0, 0, -5.5), 0.7, 5.5, "leather", sides=8)
    em.gem("pommel", (0, 0, -6.2), 1.2, 1.2, "bronze", sides=6, glow=False)


def brannoch_banner():
    cyl("pole", (0, 0, 0), 0.6, 40, "wood", sides=8)
    em.gem("finial", (0, 0, 40), 1.1, 3.2, "gold", sides=4, glow=False)
    em.rbox("bar", (0, 0, 37.5), (15, 0.9, 0.9), "wood")
    em.rbox("cloth", (0, 0.3, 28.5), (13.5, 0.35, 17), "red")
    for i, (x, z, w, h) in enumerate([(0, 30, 3, 6), (-2.2, 26.4, 2, 2.6), (2.2, 26.4, 2, 2.6), (0, 33.6, 4.6, 1.2), (-1.6, 34.6, 1, 1), (1.6, 34.6, 1, 1)]):
        em.box("tower%d" % i, (x, -0.02, z), (w, 0.3, h), "black")   # the black tower
    for i in range(9): em.gem("fringe%d" % i, (-6 + i * 1.5, 0.3, 19.6), 0.35, 1.2, "gold", sides=4, glow=False)


def brannoch_pauldron():
    bm = bmesh.new(); rows = []
    for i in range(7):   # a curved shell over the shoulder
        a = math.radians(-80 + 160 * i / 6)
        rows.append([bm.verts.new((math.sin(a) * 4.2, z, math.cos(a) * 4.2)) for z in (-2.6, 2.6)])
    inner = [[bm.verts.new((v.co.x * 0.86, v.co.y, v.co.z * 0.86)) for v in r] for r in rows]
    for i in range(6):
        bm.faces.new((rows[i][0], rows[i + 1][0], rows[i + 1][1], rows[i][1]))
        bm.faces.new((inner[i][1], inner[i + 1][1], inner[i + 1][0], inner[i][0]))
        bm.faces.new((rows[i][1], rows[i + 1][1], inner[i + 1][1], inner[i][1]))
        bm.faces.new((inner[i][0], inner[i + 1][0], rows[i + 1][0], rows[i][0]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    gt.new_obj("shell", bm, REG["steel"], smooth=False)
    for i in range(7):
        a = math.radians(-80 + 160 * i / 6)
        em.rbox("trim%d" % i, (math.sin(a) * 4.35, 2.4, math.cos(a) * 4.35), (1.5, 0.5, 0.5), "red", roll=a)
    em.gem("stud", (0, 0, 4.5), 0.7, 0.6, "bronze", sides=6, glow=False)


def legion_shield():
    em.rbox("face", (0, 0, 0), (10, 1.1, 16), "red", bevel=0.2)
    for i, (x, z, w, h) in enumerate([(0, 0.5, 3, 6.5), (-2.4, -3.4, 2.2, 2.8), (2.4, -3.4, 2.2, 2.8), (0, 4.4, 5, 1.3), (-1.8, 5.5, 1, 1), (1.8, 5.5, 1, 1)]):
        em.box("tower%d" % i, (x, -0.65, z), (w, 0.3, h), "black")
    for i, (x, z, w, h) in enumerate([(0, 8.2, 10.6, 0.9), (0, -8.2, 10.6, 0.9), (5.2, 0, 0.9, 16.4), (-5.2, 0, 0.9, 16.4)]):
        em.box("rim%d" % i, (x, 0, z), (w, 1.5, h), "steel")
    em.box("grip", (0, 1.1, 0), (1.2, 1.2, 5), "leather")


def legion_spear():
    cyl("shaft", (0, 0, -10), 0.5, 34, "wood", sides=8)
    em.prism("head", [(0, 0), (1.5, 3), (0.8, 6.5), (0, 8), (-0.8, 6.5), (-1.5, 3)], -0.3, 0.3, "steel")
    bpy.data.objects["head"].location = (0, 0, 24)
    cyl("collar", (0, 0, 23), 0.75, 1.2, "iron", sides=8)


def alder_sword():
    em.prism("blade", [(-0.9, 0.4), (0.9, 0.4), (0.8, 15), (0, 17.5), (-0.8, 15)], -0.32, 0.32, "steel")
    em.box("fuller", (0, -0.34, 7), (0.4, 0.08, 11), "iron")
    em.rbox("guard", (0, 0, 0), (5.6, 1.1, 0.9), "bronze")
    cyl("grip", (0, 0, -4), 0.5, 4, "leather", sides=8)
    em.gem("pommel", (0, 0, -4.9), 1.0, 1.0, "bronze", sides=8, glow=False)   # the bell pommel


def wren_staff():
    cyl("shaft", (0, 0, -14), 0.6, 30, "wood", sides=8)
    for i in range(4):   # the bronze cage
        a = i * math.pi / 2 + math.pi / 4
        em.rbox("bar%d" % i, (math.cos(a) * 1.6, math.sin(a) * 1.6, 19), (0.4, 0.4, 5), "bronze")
    cyl("cage_base", (0, 0, 16.2), 1.9, 0.6, "bronze", sides=10)
    cyl("cage_top", (0, 0, 21.4), 1.9, 0.6, "bronze", sides=10)
    em.gem("bell", (0, 0, 18.6), 1.2, 1.6, "bell", sides=8, glow=True)
    em.gem("finial", (0, 0, 22), 0.6, 1.8, "bronze", sides=4, glow=False)


def mira_hat():
    cyl("brim", (0, 0, 0), 7.2, 0.6, "felt", sides=16)
    cyl("crown", (0, 0, 0.6), 4.1, 4.6, "felt", sides=14)
    cyl("band", (0, 0, 0.6), 4.2, 1.1, "gold", sides=14, top=False)
    em.prism("feather", [(0, 0), (1.2, 2), (1.4, 6), (0.6, 9), (-0.2, 6), (-0.4, 2)], -0.15, 0.15, "cream")
    o = bpy.data.objects["feather"]; o.rotation_euler = (0, math.radians(-35), math.radians(20)); o.location = (3.6, 0.6, 1.4)


def mira_ledger():
    em.box("pages", (0, 0, 0), (5.4, 2.4, 7.4), "parchment")
    em.box("front", (0, -1.35, 0), (6, 0.5, 8), "felt")
    em.box("back", (0, 1.35, 0), (6, 0.5, 8), "felt")
    em.box("spine", (-3.0, 0, 0), (0.6, 3.2, 8), "leather")
    for i, (x, z) in enumerate([(2.6, 3.6), (2.6, -3.6), (-2.2, 3.6), (-2.2, -3.6)]):
        for y in (-1.6, 1.6): em.box("corner%d%d" % (i, int(y > 0)), (x, y, z), (1.2, 0.2, 1.2), "gold")
    em.box("clasp", (3.2, 0, 0), (0.6, 3.4, 1.6), "gold")


def mira_cat():
    em.rbox("body", (0, 0.6, 3), (4.4, 6.6, 4.6), "fur", bevel=0.4)
    em.rbox("chest", (0, -2.4, 3.6), (4, 2.4, 5), "fur", bevel=0.4)
    em.rbox("head", (0, -3.4, 7.6), (4.4, 3.8, 3.6), "fur", bevel=0.4)
    for s in (-1, 1):
        em.gem("ear%d" % (s + 1), (s * 1.4, -3.2, 9.4), 0.8, 1.5, "fur", sides=3, glow=False)
        em.box("eye%d" % (s + 1), (s * 1.0, -5.32, 8.0), (0.8, 0.12, 0.7), "eye", glow=True)
        em.rbox("leg%d" % (s + 1), (s * 1.3, -2.6, 0.9), (1.1, 1.1, 1.8), "fur")
    em.box("nose", (0, -5.35, 7.2), (0.5, 0.1, 0.4), "felt")
    for i in range(6):   # the tail curling round
        a = math.radians(-60 + i * 30)
        em.rbox("tail%d" % i, (math.sin(a) * 3.4, 3.6 + math.cos(a) * 1.6 - 1.6, 0.7 + i * 0.12), (1.0, 1.6, 1.0), "fur", yaw=-a)
    em.box("collar", (0, -3.0, 5.8), (3.8, 1.6, 0.6), "red")
    em.gem("coin", (0, -3.9, 5.2), 0.6, 0.6, "gold", sides=6, glow=False)


def seren_tail():
    """A high ponytail falling from the crown down her back, held by a crystal pin."""
    for i in range(8):
        t = i / 7
        em.rbox("tail%d" % i, (0, 4.6 + t * 2.4, 7.6 - t * 10), (2.4 - t * 1.2, 1.8, 2.2), "silver", tilt=math.radians(-20 + t * 25))
    em.gem("pin", (0, 4.2, 8.2), 0.9, 1.4, "crystal", sides=4, glow=True)


def seren_coattails():
    """Two long coat tails behind her from the waist to the knee, the right one longer."""
    for side, L in ((-1, 12), (1, 9.5)):
        em.prism("tail%d" % (side + 1), [(-2.0, 0), (2.0, 0), (1.6, -L), (0, -L - 1.6), (-1.6, -L)], -0.35, 0.35, "white")
        o = bpy.data.objects["tail%d" % (side + 1)]; o.location = (side * 2.2, 2.4, 0); o.rotation_euler = (math.radians(-12), 0, 0)
        em.box("trim%d" % (side + 1), (side * 2.2, 2.0, -0.2), (4.2, 0.4, 0.5), "gold")


def seren_halo():
    """Five small crystal blades fanned behind her shoulders, points up."""
    for i in range(5):
        a = math.radians(-60 + 30 * i)
        name = "hb%d" % i
        em.prism(name, [(-0.6, 0), (0.6, 0), (0.55, 7), (0, 9), (-0.55, 7)], -0.2, 0.2, "crystal", glow=True)
        o = bpy.data.objects[name]; o.rotation_euler = (0, a, 0); o.location = (math.sin(a) * 3, 0, math.cos(a) * 3)


def brannoch_tower():
    """His left pauldron: a little round tower with crenellations and a red pennant."""
    cyl("drum", (0, 0, 0), 3.0, 4.2, "steel", sides=12)
    for i in range(6):
        a = i * math.pi / 3
        em.box("merlon%d" % i, (math.cos(a) * 2.6, math.sin(a) * 2.6, 4.8), (1.2, 1.2, 1.2), "steel")
    em.box("slit", (0, -2.95, 2.2), (0.5, 0.2, 1.6), "ink")
    cyl("mast", (0, 0, 4.2), 0.25, 4.6, "wood", sides=6)
    em.prism("pennant", [(0, 0), (3.2, -0.6), (0, -1.6)], -0.12, 0.12, "red")
    bpy.data.objects["pennant"].location = (0.2, 0, 8.6)
    cyl("base", (0, 0, -0.7), 3.6, 0.8, "iron", sides=12)


def brannoch_mantle():
    """A heavy fur mantle round his shoulders."""
    for i in range(12):
        a = math.radians(-180 + 360 * i / 12)
        em.rbox("fur%d" % i, (math.sin(a) * 5.2, math.cos(a) * 3.2, 0), (3.2, 2.2, 2.4), "brownfur", yaw=-a, bevel=0.3)


def legion_plume():
    """The red crest along the top of the helm, front to back."""
    for i in range(7):
        t = i / 6
        em.rbox("crest%d" % i, (0, -3.6 + t * 8.4, 0.6 + math.sin(t * math.pi) * 1.6), (0.9, 1.4, 2.4 - abs(t - 0.4) * 1.2), "red", tilt=math.radians(-30 + t * 50))
    em.box("ridge", (0, 0.4, 0), (1.3, 8.6, 0.6), "steel")


def alder_bell():
    """The Last Bell: a big bronze bell on his belt, its clapper showing."""
    bm = bmesh.new()
    prof = [(0.8, 3.0), (1.8, 2.6), (2.0, 1.2), (2.4, 0.0), (2.9, -0.6)]
    rings = []
    for r, z in prof:
        rings.append([bm.verts.new((math.cos(a) * r, math.sin(a) * r, z)) for a in (2 * math.pi * k / 12 for k in range(12))])
    for i in range(len(rings) - 1):
        for k in range(12): bm.faces.new((rings[i][k], rings[i][(k + 1) % 12], rings[i + 1][(k + 1) % 12], rings[i + 1][k]))
    bm.faces.new(rings[0][::-1])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    gt.new_obj("bellbody", bm, REG["bronze"], smooth=False)
    em.gem("clapper", (0, 0, -0.9), 0.6, 0.8, "iron", sides=6, glow=False)
    em.rbox("loop", (0, 0, 3.4), (1.4, 0.4, 1.2), "leather")


def alder_scarf():
    """The red scarf's long tail, streaming back off his shoulder."""
    for i in range(7):
        t = i / 6
        em.rbox("sc%d" % i, (-1.5 + t * 0.6, 2.2 + t * 4.2, -t * 7), (2.2, 0.5, 1.9), "red", tilt=math.radians(-35 + 25 * t), roll=math.radians(10 * math.sin(t * 6)))


def alder_capelet():
    """A half-cape over his left shoulder, bronze-trimmed."""
    em.prism("cape", [(0, 0), (4.6, 0), (4.2, -7.5), (2.4, -9), (0.2, -7)], -0.35, 0.35, "slate")
    o = bpy.data.objects["cape"]; o.rotation_euler = (math.radians(-8), 0, math.radians(90)); o.location = (0, 0, 0)
    em.box("clasp", (0.4, -2.4, -0.4), (1.2, 0.6, 1.2), "bronze")


def wren_braid():
    """A long braid over her shoulder, tied with a little bell."""
    for i in range(7):
        em.rbox("b%d" % i, (0, -i * 0.15, -i * 1.6), (1.6, 1.4, 1.8), "sand", yaw=math.radians(25 * (i % 2) - 12), bevel=0.2)
    em.gem("tie", (0, -1.2, -11.6), 0.6, 0.9, "bell", sides=6, glow=True)


def wren_bells():
    """A ring of six little bells floating round her."""
    for i in range(6):
        a = i * math.pi / 3
        em.gem("bell%d" % i, (math.cos(a) * 9, math.sin(a) * 9, math.sin(a * 2) * 1.2), 0.8, 1.4, "bell", sides=6, glow=True)
        em.box("knot%d" % i, (math.cos(a) * 9, math.sin(a) * 9, math.sin(a * 2) * 1.2 + 1.5), (0.4, 0.4, 0.5), "bronze")


def mira_coins():
    """Coins circling her in a slow ring."""
    for i in range(8):
        a = i * math.pi / 4
        c = cyl("coin%d" % i, (0, 0, 0), 1.0, 0.25, "coin", sides=10)
        c.rotation_euler = (math.radians(90), 0, a + math.pi / 2); c.location = (math.cos(a) * 10, math.sin(a) * 10, math.sin(a * 3) * 1.2)


def mira_scales():
    """A little brass balance hanging at her hip."""
    em.box("beam", (0, 0, 0), (5, 0.4, 0.4), "gold")
    cyl("post", (0, 0, -0.2), 0.2, 1.6, "gold", sides=6)
    for s_ in (-1, 1):
        for k in (-1, 1): em.rbox("str%d%d" % (s_ + 1, k + 1), (s_ * 2.3 + k * 0.5, 0, -1.4), (0.12, 0.12, 2.8), "gold", roll=math.radians(k * 12))
        cyl("pan%d" % (s_ + 1), (s_ * 2.3, 0, -3.0), 1.2, 0.3, "gold", sides=10)
    em.gem("weight", (2.3, 0, -2.6), 0.5, 0.5, "coin", sides=6, glow=False)


def mira_book():
    """The ledger, open and floating, its pages glowing."""
    em.rbox("lcover", (-3.2, 0, 0), (6.2, 8, 0.5), "felt", roll=math.radians(12))
    em.rbox("rcover", (3.2, 0, 0), (6.2, 8, 0.5), "felt", roll=math.radians(-12))
    em.rbox("lpages", (-3.0, 0, 0.55), (5.8, 7.4, 0.8), "page", roll=math.radians(12))
    em.rbox("rpages", (3.0, 0, 0.55), (5.8, 7.4, 0.8), "page", roll=math.radians(-12))
    for o in ("lpages", "rpages"): GLOW.append(bpy.data.objects[o])
    em.box("spine", (0, 0, -0.1), (0.8, 8, 0.6), "leather")
    for i, (x, y) in enumerate([(5.6, 3.6), (5.6, -3.6), (-5.6, 3.6), (-5.6, -3.6)]): em.box("c%d" % i, (x, y, -0.5 if x > 0 else -0.5), (1.1, 1.1, 0.6), "gold")


GEAR = {"seren_tail": seren_tail, "seren_coattails": seren_coattails, "seren_halo": seren_halo, "brannoch_tower": brannoch_tower,
        "brannoch_mantle": brannoch_mantle, "legion_plume": legion_plume, "alder_bell": alder_bell, "alder_scarf": alder_scarf,
"wren_braid": wren_braid, "wren_bells": wren_bells, "mira_coins": mira_coins,
        "mira_scales": mira_scales, "mira_book": mira_book,
        "seren_blade": seren_blade, "brannoch_sword": brannoch_sword, "brannoch_banner": brannoch_banner, "brannoch_pauldron": brannoch_pauldron,
        "legion_shield": legion_shield, "legion_spear": legion_spear, "alder_sword": alder_sword, "wren_staff": wren_staff,
        "mira_hat": mira_hat, "mira_ledger": mira_ledger, "mira_cat": mira_cat}


# ------------------------------------------------------------------ the body, from a skin
PARTS = {"head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8), "body": (16, 16, 8, 12, 4), "jacket": (16, 32, 8, 12, 4),
         "rarm": (40, 16, 4, 12, 4), "rsleeve": (40, 32, 4, 12, 4), "larm": (32, 48, 4, 12, 4), "lsleeve": (48, 48, 4, 12, 4),
         "rleg": (0, 16, 4, 12, 4), "rpants": (0, 32, 4, 12, 4), "lleg": (16, 48, 4, 12, 4), "lpants": (0, 48, 4, 12, 4)}


def skin_box(name, part, c, size, inflate, mat, slim, pivot=None, rot=(0, 0, 0)):
    """A box of the player's body: (w, h, d) in skin pixels, centred at c, faces mapped from the skin like the game does."""
    u, v, w, h, d = PARTS[part]
    if slim and part in ("rarm", "rsleeve", "larm", "lsleeve"): w = 3
    sx, sz, sy = size
    x0, x1 = c[0] - sx / 2 - inflate, c[0] + sx / 2 + inflate
    y0, y1 = c[1] - sy / 2 - inflate, c[1] + sy / 2 + inflate
    z0, z1 = c[2] - sz / 2 - inflate, c[2] + sz / 2 + inflate
    rects = {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
             "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}
    corners = {"front": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)], "back": [(x1, y1, z1), (x0, y1, z1), (x0, y1, z0), (x1, y1, z0)],
               "right": [(x0, y1, z1), (x0, y0, z1), (x0, y0, z0), (x0, y1, z0)], "left": [(x1, y0, z1), (x1, y1, z1), (x1, y1, z0), (x1, y0, z0)],
               "top": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)], "bottom": [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)]}
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new()
    for f, cs in corners.items():
        rx, ry, rw, rh = rects[f]
        uvs = [(rx, ry), (rx + rw, ry), (rx + rw, ry + rh), (rx, ry + rh)]
        vs = [bm.verts.new(p) for p in cs]
        face = bm.faces.new(vs)
        for lp, (pu, pv) in zip(face.loops, uvs): lp[uvl].uv = (pu / 64, 1 - pv / 64)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    if pivot is not None:
        for ax, ang in zip("XYZ", rot):
            if ang: bmesh.ops.rotate(bm, cent=pivot, matrix=Matrix.Rotation(math.radians(ang), 3, ax), verts=bm.verts)
    me = bpy.data.meshes.new(name); bm.to_mesh(me); bm.free()
    ob = bpy.data.objects.new(name, me); bpy.context.collection.objects.link(ob)
    ob.data.materials.append(mat)
    return ob


def skin_material(name):
    m = bpy.data.materials.new(name); m.use_nodes = True
    nt = m.node_tree; bsdf = nt.nodes.get("Principled BSDF")
    img = nt.nodes.new("ShaderNodeTexImage"); img.image = bpy.data.images.load(os.path.join(SKINS, name + ".png")); img.interpolation = "Closest"
    nt.links.new(img.outputs["Color"], bsdf.inputs["Base Color"]); nt.links.new(img.outputs["Alpha"], bsdf.inputs["Alpha"])
    bsdf.inputs["Roughness"].default_value = 0.85
    m.blend_method = "CLIP" if hasattr(m, "blend_method") else None
    return m


def body(skin, slim, scale=1.0, at=(0, 0, 0), pose=None):
    """The player's shape: legs 0-12, body 12-24, head 24-32 (pixels); arms at the sides. pose: arm and leg angles."""
    pose = pose or {}
    mat = skin_material(skin)
    aw = 3 if slim else 4
    obs = []
    def add(o): obs.append(o)
    add(skin_box("head", "head", (0, 0, 28), (8, 8, 8), 0, mat, slim))
    add(skin_box("hat", "hat", (0, 0, 28), (8, 8, 8), 0.5, mat, slim))
    add(skin_box("body", "body", (0, 0, 18), (8, 12, 4), 0, mat, slim))
    add(skin_box("jacket", "jacket", (0, 0, 18), (8, 12, 4), 0.25, mat, slim))
    for side, arm, sleeve in ((-1, "rarm", "rsleeve"), (1, "larm", "lsleeve")):
        x = side * (4 + aw / 2)
        piv = (x, 0, 22)
        rot = pose.get(arm, (0, 0, side * 6))
        add(skin_box(arm, arm, (x, 0, 18), (aw, 12, 4), 0, mat, slim, piv, rot))
        add(skin_box(sleeve, sleeve, (x, 0, 18), (aw, 12, 4), 0.25, mat, slim, piv, rot))
    for side, leg, pants in ((-1, "rleg", "rpants"), (1, "lleg", "lpants")):
        piv = (side * 2, 0, 12)
        rot = pose.get(leg, (0, 0, 0))
        add(skin_box(leg, leg, (side * 2, 0, 6), (4, 12, 4), 0, mat, slim, piv, rot))
        add(skin_box(pants, pants, (side * 2, 0, 6), (4, 12, 4), 0.25, mat, slim, piv, rot))
    for o in obs:
        o.scale = (scale, scale, scale); o.location = at
    return obs


MATS = {}
# a sword's model is built with its crossguard at the origin; moved by this much along its blade, the origin is the
# middle of its grip, where the hand closes
GRIP = {"seren_blade": 2.1, "brannoch_sword": 2.75, "alder_sword": 2.0}


def to_grip(form, parts):
    off = GRIP.get(form, 0)
    if off:
        for o in parts: o.location = (o.location[0], o.location[1], o.location[2] + off)




def gear(form, at=(0, 0, 0), rot=(0, 0, 0), scale=1.0):
    """Gear built into the scene at a place on the body (its glowing parts glow)."""
    before = list(gt.PARTS)
    GEAR[form]()
    new = [o for o in gt.PARTS if o not in before]
    to_grip(form, new)
    for o in new:
        if o.type != "MESH":
            bpy.ops.object.select_all(action="DESELECT"); o.select_set(True); bpy.context.view_layer.objects.active = o
            bpy.ops.object.convert(target="MESH")
        if MATS:
            o.data.materials.clear(); o.data.materials.append(MATS["glow"] if o in GLOW else MATS["plain"])
    bpy.ops.object.select_all(action="DESELECT")
    for o in new: o.select_set(True)
    bpy.context.view_layer.objects.active = new[0]
    bpy.ops.object.join()
    ob = bpy.context.view_layer.objects.active
    ob.name = form
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    ob.rotation_euler = tuple(math.radians(a) for a in rot)
    ob.scale = (scale, scale, scale)
    ob.location = at
    return ob


def gear_materials(image):
    glow_m, plain_m = bpy.data.materials.new("glow"), bpy.data.materials.new("plain")
    for m, glow in ((glow_m, True), (plain_m, False)):
        m.use_nodes = True
        nt = m.node_tree; bsdf = nt.nodes.get("Principled BSDF")
        img = nt.nodes.new("ShaderNodeTexImage"); img.image = image; img.interpolation = "Closest"
        bsdf.inputs["Roughness"].default_value = 0.65
        if glow:
            bsdf.inputs["Base Color"].default_value = (0, 0, 0, 1)
            nt.links.new(img.outputs["Color"], bsdf.inputs["Emission Color"]); bsdf.inputs["Emission Strength"].default_value = 1.8
        else: nt.links.new(img.outputs["Color"], bsdf.inputs["Base Color"])
    return glow_m, plain_m


# the right hand is at the bottom of the right arm: x = -(4 + aw/2), z = 11; a held weapon points forward and up
RIGHT_HAND = lambda aw: (-(4 + aw / 2), -1.5, 11.5)


def rival(name):
    """Build one rival with their gear (Blender: the front faces -Y)."""
    if name == "seren":
        body("rival_seren", True, pose={"rarm": (-35, 0, -8)})
        gear("seren_blade", (-5.5, -10 * math.sin(math.radians(35)), 22 - 10 * math.cos(math.radians(35))), (75, 0, 0))
        gear("seren_tail", (0, 0, 23.5), (0, 0, 0))
        gear("seren_coattails", (0, 0, 13), (0, 0, 0))
        gear("seren_halo", (0, 5.0, 19.5), (0, 0, 0), 0.85)
    elif name == "brannoch":
        k = 1.22
        body("rival_brannoch", False, scale=k, pose={"rarm": (-30, 0, -6)})
        gear("brannoch_sword", (-6 * k, -10 * math.sin(math.radians(30)) * k, (22 - 10 * math.cos(math.radians(30))) * k), (70, 0, 0), k)
        gear("brannoch_banner", (1.5 * k, 3.2 * k, 8 * k), (0, 0, 0), k)
        gear("brannoch_pauldron", (-6.2 * k, 0, 21.8 * k), (0, 0, 0), k)
        gear("brannoch_tower", (6.4 * k, 0, 23.6 * k), (0, 0, 0), k)
        gear("brannoch_mantle", (0, 0, 23.6 * k), (0, 0, 0), k)
    elif name == "legion":
        body("rival_legion", False, pose={"rarm": (-20, 0, -6), "larm": (-30, 0, 10)})
        gear("legion_spear", (-6, -3, 11), (-20, 0, 0))
        gear("legion_shield", (7.5, -4.5, 15), (0, 0, 15))
        gear("legion_plume", (0, 0, 32.5), (0, 0, 0))
    elif name == "alder":
        body("rival_alder", False, pose={"rarm": (-40, 0, -6), "lleg": (12, 0, 0), "rleg": (-12, 0, 0)})
        gear("alder_sword", (-6, -10 * math.sin(math.radians(40)), 22 - 10 * math.cos(math.radians(40))), (80, 0, 0))
        gear("alder_bell", (3.2, -2.6, 11.2), (0, 0, 0), 0.9)
        gear("alder_scarf", (0, 0, 23.5), (0, 0, 0))
    elif name == "wren":
        body("rival_wren", True, pose={"larm": (-20, 0, 8)})
        gear("wren_staff", (6, -2.5, 11), (-8, 0, 0))
        gear("wren_braid", (-3.2, -2.2, 26), (0, 0, 0))
        gear("wren_bells", (0, 0, 20), (0, 0, 0))
    elif name == "mira":
        body("rival_mira", True, pose={"larm": (-55, 0, 10)})
        gear("mira_hat", (0, 0, 32.2), (0, 0, -10))
        gear("mira_book", (8, -7, 20), (60, 0, 20))
        gear("mira_cat", (5.4, -0.6, 24.2), (0, 0, 30), 0.7)
        gear("mira_coins", (0, 0, 16), (0, 0, 0))
        gear("mira_scales", (-4.8, -2.4, 11.5), (0, 0, 0), 0.8)


ORDER = ["seren", "brannoch", "legion", "alder", "wren", "mira"]
NAMES = {"seren": "Seren Vale, the Glass Duelist", "brannoch": "Brannoch Tor, the Siege-Lord", "legion": "Tor Legionnaire",
         "alder": "Alder Bellmar", "wren": "Wren Bellmar", "mira": "Mira Hollowell (and her cat)"}


def render_sheet(out):
    paint(os.path.join(out, TEX + ".png"))
    CW, CH = 380, 560
    for name in ORDER:
        for view, yaw in (("front", -28), ("back", 152)):
            bpy.ops.wm.read_factory_settings(use_empty=True)
            gt.PARTS.clear(); GLOW.clear()
            image = bpy.data.images.load(os.path.join(out, TEX + ".png"))
            MATS["glow"], MATS["plain"] = gear_materials(image)
            rival(name)
            empty = bpy.data.objects.new("pivot", None); bpy.context.collection.objects.link(empty)
            for o in list(bpy.data.objects):
                if o.type == "MESH": o.parent = empty
            empty.rotation_euler = (0, 0, math.radians(yaw))
            sc = bpy.context.scene
            sc.render.engine = "CYCLES"; sc.cycles.samples = 24; sc.cycles.device = "CPU"
            sc.view_settings.view_transform = "Standard"
            sc.render.resolution_x, sc.render.resolution_y = CW, CH
            sc.render.film_transparent = False
            world = bpy.data.worlds.new("w"); sc.world = world; world.use_nodes = True
            world.node_tree.nodes["Background"].inputs[0].default_value = (0.1, 0.1, 0.12, 1)
            for ln, rot, energy in (("key", (math.radians(50), 0, math.radians(-35)), 3.6), ("fill", (math.radians(70), 0, math.radians(150)), 1.6), ("top", (0.2, 0, 0), 1.0)):
                L = bpy.data.lights.new(ln, "SUN"); L.energy = energy
                lo = bpy.data.objects.new(ln, L); lo.rotation_euler = rot; sc.collection.objects.link(lo)
            cam = bpy.data.cameras.new("cam"); cam.type = "ORTHO"; cam.ortho_scale = 62 if name == "brannoch" else 52
            co = bpy.data.objects.new("cam", cam); sc.collection.objects.link(co); sc.camera = co
            el = math.radians(10)
            co.location = (0, -200 * math.cos(el), (24 if name == "brannoch" else 20) + 200 * math.sin(el)); co.rotation_euler = (math.radians(90) - el, 0, 0)
            sc.render.filepath = os.path.join(out, "rival_%s_%s.png" % (name, view))
            bpy.ops.render.render(write_still=True)
    sheet = Image.new("RGB", (CW * len(ORDER), CH * 2 + 30), (12, 12, 16))
    d = ImageDraw.Draw(sheet)
    for i, name in enumerate(ORDER):
        for r, view in enumerate(("front", "back")):
            sheet.paste(Image.open(os.path.join(out, "rival_%s_%s.png" % (name, view))).convert("RGB"), (i * CW, 30 + r * CH))
        d.text((i * CW + 10, 8), NAMES[name], fill=(235, 228, 210))
    sheet.save(os.path.join(out, "rival_sheet.png"))


def to_game(out):
    MATS.clear()
    paint(os.path.join(out, TEX + ".png"))
    ar.LOW = True
    for form, fn in GEAR.items():
        bpy.ops.wm.read_factory_settings(use_empty=True)
        gt.PARTS.clear(); GLOW.clear()
        fn()
        to_grip(form, list(gt.PARTS))
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
        bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
        name = "rival_" + form
        ob.name = name
        bpy.ops.wm.obj_export(filepath=os.path.join(out, name + ".obj"), export_selected_objects=True, export_uv=True, export_normals=True,
                              export_materials=True, export_triangulated_mesh=True, forward_axis="NEGATIVE_Z", up_axis="Y", global_scale=1 / 16, path_mode="STRIP")
        n = em.tris(ob)
        print(form, "tris", n)
        assert n >= 24, form
        disp = {"gui": {"rotation": [20, 225, 0], "translation": [0, 0, 0], "scale": [0.6] * 3},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5] * 3},
                "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5] * 3}}
        gt.export_to_game(name, out, disp, glowing={form + "_glow"}, textures={form: TEX, form + "_glow": TEX}, render_type="minecraft:cutout")


if __name__ == "__main__":
    mode, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    if mode == "render": render_sheet(out)
    if mode == "game": to_game(out)
