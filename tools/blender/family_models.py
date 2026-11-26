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
EXTRA = {"moss": (70, 104, 52), "bark": (92, 66, 44), "bone": (226, 218, 192), "amber": (255, 176, 60), "marsh": (150, 255, 140), "sun": (255, 210, 110), "teal": (80, 240, 220), "ice": (196, 232, 255)}
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


# ---- the Rimefolk
def ice_axe():
    cyl("haft", (0, 0, -4), 0.6, 17, "wood", sides=6)
    em.prism("blade", [(0, 9), (4.4, 8), (5.6, 11), (4.4, 14.5), (0, 13.5)], -0.5, 0.5, "ice", glow=True)
    em.rbox("cap", (0, 0, 13.4), (1.4, 1.4, 1.2), "iron")

def fur_mantle():
    em.rbox("mantle", (0, 0.4, 23.6), (10, 6.4, 2.2), "white", bevel=0.8)
    for s in (-1, 1): em.rbox("drape%d" % s, (s * 4.6, 0.4, 21), (1.6, 6, 4), "white", bevel=0.6)

def ice_staff():
    cyl("staff", (0, 0, -8), 0.5, 25, "silver", sides=6)
    em.gem("shard", (0, 0, 20.5), 1.6, 4.4, "ice", sides=5, glow=True)
    for i in range(3): em.gem("chip%d" % i, (math.cos(i * 2.1) * 2.2, math.sin(i * 2.1) * 2.2, 19 + i * 0.8), 0.5, 1.2, "ice", sides=4, glow=True)

def ice_crown():
    em.torus("band", (0, 0, 0.4), 4.4, 0.5, "ice", M=12, N=4)
    for i in range(7):
        a = i * 2 * math.pi / 7
        em.gem("spike%d" % i, (math.cos(a) * 4.2, math.sin(a) * 4.2, 1.4), 0.6, 2.4 + (i % 2) * 1.4, "ice", sides=4, glow=True)

def frost_greatsword():
    em.prism("blade", [(-1.1, 0.6), (1.1, 0.6), (1.3, 17), (0, 21), (-1.3, 17)], -0.4, 0.4, "ice", glow=True)
    em.rbox("guard", (0, 0, 0), (5.2, 1.2, 1), "steel")
    cyl("grip", (0, 0, -5), 0.55, 5, "leather", sides=6)
    em.gem("pommel", (0, 0, -5.6), 0.9, 1.0, "ice", sides=6, glow=True)

def fur_cloak():
    em.rbox("cloak", (0, 2.6, 13), (9, 0.8, 14), "brownfur", tilt=0.08)
    em.rbox("collar", (0, 1.0, 23.6), (9.6, 5.6, 2), "brownfur", bevel=0.6)

# ---- the Hollowfolk
def hook_claw():
    for i in range(3):
        em.prism("hook%d" % i, [(-0.4, 0), (0.4, 0), (0.8, 5.5), (2.6, 8.5), (1.8, 9.2), (-0.2, 6)], -0.25 + (i - 1) * 0.8, 0.25 + (i - 1) * 0.8, "silver")

def fork_staff():
    cyl("staff", (0, 0, -8), 0.5, 22, "black", sides=6)
    for s in (-1, 1): em.rbox("tine%d" % s, (s * 1.4, 0, 17), (0.6, 0.6, 6), "silver")
    em.rbox("bridge", (0, 0, 14), (3.4, 0.7, 0.7), "silver")
    em.gem("hum", (0, 0, 18), 0.8, 1.4, "teal", sides=6, glow=True)

def stone_shell():
    for i, (x, z, w) in enumerate([(-3, 22, 5), (3, 22, 5), (0, 17, 8), (0, 13, 7)]): em.rbox("plate%d" % i, (x, 2.8, z), (w, 1.6, 4), "slate", bevel=0.4)
    for s in (-1, 1): em.rbox("pauldron%d" % s, (s * 6, 0, 23.4), (4.6, 5, 2.4), "slate", roll=s * 0.3, bevel=0.5)

def heart_lantern():
    em.rbox("cage", (0, -2.4, 18), (3.2, 1.4, 3.6), "iron")
    em.gem("heart", (0, -2.8, 18), 1.4, 1.8, "teal", sides=6, glow=True)

# ---- the Ironbound Twins
def tower_shield():
    em.rbox("shield", (0, 0, 0), (8, 1.4, 15), "iron", bevel=0.4)
    em.rbox("stripe", (0, -0.8, 0), (2.2, 0.3, 15), "red")
    em.gem("boss", (0, -1.0, 2), 1.4, 0.8, "steel", sides=6, glow=False)

def flanged_mace():
    cyl("haft", (0, 0, -4), 0.55, 15, "iron", sides=6)
    for i in range(6):
        a = i * math.pi / 3
        em.rbox("flange%d" % i, (math.cos(a) * 1.1, math.sin(a) * 1.1, 12.5), (0.5, 1.8, 3.6), "steel", yaw=a)

def iron_greatsword():
    em.prism("blade", [(-1.2, 0.6), (1.2, 0.6), (1.0, 19), (0, 22), (-1.0, 19)], -0.35, 0.35, "steel")
    em.rbox("fuller", (0, 0, 9), (0.5, 0.75, 15), "iron")
    em.rbox("guard", (0, 0, 0), (6, 1.2, 1.1), "iron")
    cyl("grip", (0, 0, -6), 0.55, 6, "leather", sides=6)

def wrist_chain():
    em.torus("cuff", (0, 0, 0), 1.6, 0.45, "iron", M=10, N=4)
    for i in range(5): em.torus("link%d" % i, (0, -0.6 - i * 1.0, -1.2 - i * 1.3), 0.6, 0.22, "iron", M=8, N=4)


# ---- ASHFALL, Arc 1
def spun(fn, deg):
    """A blade turned on its own axis, so it's held edge-on (its flat to the side, a hook in the upright plane)."""
    def built():
        before = list(gt.PARTS); fn()
        a = math.radians(deg)
        for o in gt.PARTS:
            if o in before: continue
            x, y, z = o.location
            o.location = (x * math.cos(a) - y * math.sin(a), x * math.sin(a) + y * math.cos(a), z)
            o.rotation_euler[2] += a
    return built

def knight_longsword():
    em.prism("blade", [(-0.9, 0.6), (0.9, 0.6), (0.7, 16), (0, 18.5), (-0.7, 16)], -0.3, 0.3, "steel")
    em.rbox("fuller", (0, 0, 7.5), (0.35, 0.65, 12), "iron")
    em.rbox("guard", (0, 0, 0), (5.4, 1.0, 0.9), "iron")
    for x in (-2.7, 2.7): em.rbox("quillon%d" % (x > 0), (x, 0, 0.6), (0.8, 1.0, 1.4), "iron")
    cyl("grip", (0, 0, -4.8), 0.5, 4.8, "leather", sides=6)
    em.gem("pommel", (0, 0, -5.4), 0.8, 1.0, "bronze", sides=6, glow=False)

def half_cloak():   # over the shoulder where his left arm was
    em.rbox("shoulder", (3.4, 0, 23.7), (7.4, 5.4, 1.6), "black", bevel=0.4)
    em.rbox("drape", (5.8, 0, 16.5), (2.6, 5.2, 13), "black", bevel=0.3)
    em.rbox("back", (2.2, 2.5, 15.5), (8, 0.6, 15), "black", tilt=0.05)
    em.rbox("front", (3.6, -2.5, 17), (3.6, 0.6, 12), "black")
    em.rbox("lining", (6.9, 0, 16.5), (0.3, 4.6, 12), "blue")
    em.gem("clasp", (0.6, -2.9, 22.6), 0.8, 0.6, "bronze", sides=6, glow=False)

def sallow_mitre():
    em.rbox("mitre", (0, 0, 3.2), (6.8, 6.8, 6.4), "cream", bevel=0.3)
    em.prism("peak", [(-3.4, 6.2), (3.4, 6.2), (2.2, 10), (0, 11.4), (-2.2, 10)], -3.0, 3.0, "cream")
    em.rbox("band", (0, -3.5, 4.0), (1.4, 0.3, 10), "red")
    em.rbox("rim", (0, 0, 0.5), (7.2, 7.2, 1.0), "red")

def brand_knife():   # a hooked blade for cutting the brand out
    em.prism("blade", [(-0.4, 0), (0.6, 0), (1.0, 4), (0.4, 8), (-1.8, 9.6), (-1.2, 8.4), (-0.2, 5)], -0.2, 0.2, "silver")
    em.rbox("guard", (0, 0, 0), (2.2, 0.8, 0.5), "bronze")
    cyl("grip", (0, 0, -3.8), 0.5, 3.8, "bone", sides=6)

def reliquary():   # a cage of the brands he has cut out, still burning
    for i in range(3): em.rbox("chain%d" % i, (math.cos(i * 2.1) * 0.5, math.sin(i * 2.1) * 0.5, -2.5), (0.2, 0.2, 5), "iron")
    em.rbox("lid", (0, 0, -5.4), (3.8, 3.8, 0.8), "iron")
    em.rbox("floor", (0, 0, -10.4), (3.8, 3.8, 0.8), "iron")
    for x in (-1.7, 1.7):
        for y in (-1.7, 1.7): em.rbox("bar%d%d" % (x > 0, y > 0), (x, y, -7.9), (0.35, 0.35, 4.4), "iron")
    for i, (x, y, z) in enumerate([(-0.6, 0.2, -8.6), (0.7, -0.3, -7.8), (0, 0.5, -6.8)]): em.gem("brand%d" % i, (x, y, z), 0.6, 1.2, "amber", sides=5, glow=True)

def rust_sickle():
    cyl("grip", (0, 0, -3.5), 0.5, 6, "wood", sides=6)
    em.rbox("collar", (0, 0, 2.6), (1.3, 1.3, 0.8), "iron")
    em.prism("blade", [(-0.4, 2.8), (0.5, 2.8), (2.6, 5), (3.6, 8.5), (2.6, 11.5), (-1.2, 12.6), (1.8, 9.8), (2.2, 6.8), (0.3, 4.6)], -0.25, 0.25, "iron")


# ---- ASHFALL, Arc II
def vey_mantle():   # a stiff high collar and a short mantle, black edged in gold
    em.rbox("collar", (0, 2.6, 26.4), (9.4, 1.0, 5.2), "black", bevel=0.3)
    em.rbox("collar_rim", (0, 2.0, 29.0), (9.6, 1.2, 0.6), "gold")
    em.rbox("mantle", (0, 0.4, 23.7), (10.6, 5.8, 1.6), "black", bevel=0.4)
    em.rbox("mantle_rim", (0, 0.4, 22.8), (10.8, 6.0, 0.4), "gold")

def chain_of_office():
    for i in range(7):
        t = (i - 3) / 3.0
        em.torus("link%d" % i, (t * 3.2, -2.5, 23.4 - (1 - t * t) * 3.2), 0.55, 0.2, "gold", M=8, N=4)
    em.gem("medallion", (0, -2.7, 19.6), 1.3, 0.6, "gold", sides=8, glow=False)
    em.gem("stone", (0, -3.1, 19.6), 0.5, 0.4, "red", sides=6, glow=False)

def ledger_staff():   # a black staff banded in gold, a ledger caged at its head, its open pages glowing
    cyl("staff", (0, 0, -8), 0.5, 26, "black", sides=6)
    for z in (2, 9, 15): em.torus("band%d" % z, (0, 0, z), 0.62, 0.2, "gold", M=8, N=4)
    em.rbox("cover", (0, 0.5, 20.5), (3.2, 0.5, 4), "red")
    em.box("pages", (0, 0, 20.5), (3.0, 0.6, 3.6), "page", glow=True)
    for x in (-1.9, 1.9):
        for y in (-1.2, 1.2): em.rbox("bar%d%d" % (x > 0, y > 0), (x, y, 20.5), (0.3, 0.3, 5), "gold")
    em.rbox("cap", (0, 0, 23.2), (4.2, 2.8, 0.6), "gold")

def warden_halberd():
    cyl("haft", (0, 0, -10), 0.5, 30, "black", sides=6)
    em.torus("collar", (0, 0, 16), 0.65, 0.22, "gold", M=8, N=4)
    em.prism("axe", [(0.4, 16.5), (4.2, 15.5), (4.8, 18.5), (4.2, 21.5), (0.4, 20.5)], -0.3, 0.3, "steel")
    em.prism("hook", [(-0.4, 17.5), (-2.6, 18.2), (-1.4, 19.2), (-0.4, 19)], -0.25, 0.25, "steel")
    em.prism("spike", [(-0.5, 20.5), (0.5, 20.5), (0, 25)], -0.3, 0.3, "steel")

def warden_crest():
    em.prism("crest", [(-0.6, 0), (0.6, 0), (0.4, 2.6), (0, 3.4), (-0.4, 2.6)], -3.6, 3.6, "gold")
    em.rbox("base", (0, 0, 0.2), (1.6, 7.6, 0.5), "gold")

def tamsin_wand():   # crooked wood, a white feather tied on, a green bead that glows
    em.rbox("lower", (0, 0, -1.5), (0.7, 0.7, 6), "wood")
    em.rbox("upper", (0.5, 0, 5), (0.6, 0.6, 7), "wood", roll=-0.18)
    em.gem("bead", (1.1, 0, 9), 0.7, 1.1, "marsh", sides=6, glow=True)
    em.rbox("feather", (-0.8, 0.3, 6), (0.3, 1.2, 3.6), "white", roll=0.5)
    em.torus("tie", (0.3, 0, 5.6), 0.55, 0.15, "red", M=8, N=4)

def tamsin_satchel():
    em.rbox("bag", (3.6, -1.6, 10.6), (3.2, 2.0, 3.0), "leather", bevel=0.3)
    em.rbox("flap", (3.6, -2.7, 11.4), (3.2, 0.3, 1.6), "brownfur")


# ---- ASHFALL, Arc III
def seg(name, a, b, w, region, glow=False):
    """A bar of width w from point a to point b (the limbs, horns and ribs are made of these)."""
    d = [b[k] - a[k] for k in range(3)]
    L = math.sqrt(sum(x * x for x in d)) or 0.01
    tilt = math.acos(max(-1, min(1, d[2] / L)))
    yaw = math.atan2(d[0], -d[1]) if abs(d[0]) + abs(d[1]) > 1e-6 else 0.0
    o = em.rbox(name, tuple((a[k] + b[k]) / 2 for k in range(3)), (w, w, L), region, yaw=yaw, tilt=tilt)
    if glow: GLOW.append(o)
    return o

def chain(name, pts, ws, region, joints=True, tip=None):
    for i in range(len(pts) - 1):
        seg("%s%d" % (name, i), pts[i], pts[i + 1], ws[i], region)
        if joints and 0 < i: em.gem("%sj%d" % (name, i), pts[i], ws[i] * 0.75, ws[i] * 0.9, "teal", sides=5, glow=True)
    if tip: seg(name + "tip", pts[-1], tip, ws[-1] * 0.6, "bone")

def mother_crown():   # a tall horn, two swept-back horns hung with teal shards, a ring of spikes, and a broken halo behind
    seg("horn", (0, 0, 0), (0, 1.4, 6), 1.5, "slate"); seg("horn2", (0, 1.4, 6), (0, 3.2, 10.5), 0.9, "slate")
    em.gem("horntip", (0, 3.2, 10.4), 0.5, 1.4, "teal", sides=4, glow=True)
    for sx in (-1, 1):
        chain("sweep%d" % (sx > 0), [(sx * 3.2, 0.8, 0), (sx * 6.2, 3.4, 3.6), (sx * 7.4, 7.2, 3.0)], [1.2, 0.8], "slate", tip=(sx * 7.6, 9.4, 1.4))
        em.gem("shard%d" % (sx > 0), (sx * 6.4, 3.6, 2.0), 0.6, 1.6, "teal", sides=4, glow=True)
    for i in range(5):
        a = (i - 2) * 0.5
        em.gem("spike%d" % i, (math.sin(a) * 3.8, -math.cos(a) * 3.8 + 1, 0.2), 0.55, 1.6 + (i % 2), "slate", sides=4, glow=False)
    for i in range(14):   # the halo, broken: every third piece missing
        if i % 5 == 4: continue
        a0, a1 = i * 2 * math.pi / 14, (i + 0.8) * 2 * math.pi / 14
        p0 = (math.cos(a0) * 7, 5.5, -4 + math.sin(a0) * 7); p1 = (math.cos(a1) * 7, 5.5, -4 + math.sin(a1) * 7)
        seg("halo%d" % i, p0, p1, 0.45, "teal" if i % 2 else "slate", glow=i % 2 == 1)

def mother_limbs():   # four spider limbs off her back, arched over the shoulders, tipped in bone; shards hang in the air by them
    for sx in (-1, 1):
        chain("upper%d" % (sx > 0), [(sx * 2.2, 2.6, 21.5), (sx * 7.2, 7.5, 31.5), (sx * 11.5, 4.5, 28), (sx * 12.5, -3, 19)], [1.3, 1.0, 0.7], "slate", tip=(sx * 12.2, -5.5, 15.5))
        chain("lower%d" % (sx > 0), [(sx * 2.2, 2.6, 16.5), (sx * 8, 8, 22), (sx * 12.5, 6, 15), (sx * 13.5, 0, 8)], [1.1, 0.85, 0.6], "slate", tip=(sx * 13.2, -2, 4.5))
        em.gem("mote%d" % (sx > 0), (sx * 9.5, -4, 33), 0.5, 1.2, "teal", sides=4, glow=True)
        em.gem("mote2%d" % (sx > 0), (sx * 15, 1, 24), 0.4, 1.0, "teal", sides=4, glow=True)

def mother_heart():   # the heart in a cage of stone ribs, its light running out along veins
    em.gem("heart", (0, -2.5, 17.5), 1.9, 2.8, "teal", sides=6, glow=True)
    for k, z in enumerate((21, 19, 16.5, 14)):
        for sx in (-1, 1):
            seg("rib%d%d" % (k, sx > 0), (sx * 3.6, -1.6, z), (sx * 1.0, -2.8, z - 1.0 - k * 0.2), 0.6, "slate")
    for sx in (-1, 1):
        seg("vein%d" % (sx > 0), (sx * 0.8, -2.25, 18.5), (sx * 3.4, -2.25, 22.5), 0.25, "teal", glow=True)
        seg("vein2%d" % (sx > 0), (sx * 0.8, -2.25, 16), (sx * 3.2, -2.25, 12.6), 0.25, "teal", glow=True)

def mother_pauldrons():   # layered shell on each shoulder, three spikes rising off it
    for sx in (-1, 1):
        for k in range(3):
            em.rbox("shell%d%d" % (sx > 0, k), (sx * (5.6 + k * 0.5), 0, 24.6 - k * 1.4), (5.0 - k * 0.6, 5.2, 1.4), "slate", roll=sx * (0.3 + k * 0.15), bevel=0.3)
        for k, (x, z, h) in enumerate([(4.6, 25.4, 2.6), (6.2, 25.0, 3.4), (7.6, 24.2, 2.4)]):
            em.gem("pspike%d%d" % (sx > 0, k), (sx * x, 0, z), 0.5, h, "bone", sides=4, glow=False)

def mother_skirt():   # a skirt of hive comb hanging from her waist, its cells lit; tendrils trailing under it
    for i in range(10):
        a = i * 2 * math.pi / 10
        cx, cy = math.cos(a) * 4.6, math.sin(a) * 3.0
        em.rbox("comb%d" % i, (cx, cy, 6.5), (3.0, 0.8, 11 - (i % 3)), "slate", yaw=a + math.pi / 2, tilt=0.12 * math.sin(a), bevel=0.2)
        for k, z in enumerate((9.5, 5.5)):
            em.box("cell%d%d" % (i, k), (cx * 1.12, cy * 1.12, z), (0.9, 0.9, 0.9), "teal", glow=True)
    for i, (x, y) in enumerate([(-2.2, -2.6), (1.8, -2.8), (-3.5, 2.4), (3.2, 2.6)]):
        seg("tendril%d" % i, (x, y, 3), (x * 1.3, y * 1.4, -0.5), 0.4, "slate")
        em.gem("drop%d" % i, (x * 1.3, y * 1.4, -0.6), 0.35, 0.6, "teal", sides=4, glow=True)

def mother_claw():   # three hooked talons fanned from the fist, their inner edges lit
    for k in range(3):
        off = (k - 1) * 0.9
        em.prism("talon%d" % k, [(-0.5 + off, 0), (0.6 + off, 0), (1.2 + off * 1.6, 6.5), (3.6 + off * 1.4, 10.5), (2.4 + off * 1.4, 11.4), (-0.1 + off * 1.6, 7.2)], -0.35, 0.35, "slate")
        em.prism("edge%d" % k, [(0.5 + off, 1), (0.8 + off, 1), (1.4 + off * 1.6, 6.4), (3.4 + off * 1.4, 10.1), (3.0 + off * 1.4, 10.5), (1.0 + off * 1.6, 6.9)], -0.4, 0.4, "teal", glow=True)
    em.rbox("knuckle", (0, 0, 0), (3.6, 1.4, 1.2), "slate", bevel=0.2)

def boar_spear():
    cyl("haft", (0, 0, -9), 0.5, 27, "wood", sides=6)
    em.prism("head", [(-0.9, 17.5), (0.9, 17.5), (0.5, 21.5), (0, 23.5), (-0.5, 21.5)], -0.3, 0.3, "steel")
    em.rbox("lug", (0, 0, 17.2), (2.8, 0.5, 0.5), "iron")

def round_shield():
    em.torus("rim", (0, 0, 0), 4.2, 0.5, "iron", sx=1.0, M=14, N=4)
    em.gem("boss", (0, -0.6, 0), 1.1, 0.9, "iron", sides=8, glow=False)
    for i in range(5): em.rbox("plank%d" % i, ((i - 2) * 1.6, 0, 0), (1.5, 0.6, 7.6 - abs(i - 2) * 1.8), "wood")

def sedge_cane():   # a gnarled cane, a bundle of reeds tied at the top, a bone charm hanging
    em.rbox("lower", (0, 0, -3), (0.8, 0.8, 9), "bark")
    em.rbox("upper", (0.4, 0, 5.5), (0.7, 0.7, 8), "bark", roll=-0.12)
    for i in range(5): em.rbox("reed%d" % i, (0.6 + (i - 2) * 0.35, 0, 11.5), (0.25, 0.25, 4 + (i % 2)), "moss", roll=(i - 2) * 0.12)
    em.torus("tie", (0.6, 0, 9.8), 0.7, 0.18, "rope", M=8, N=4)
    em.gem("charm", (1.6, 0, 8.4), 0.5, 0.8, "bone", sides=4, glow=False)

def pitchfork():
    cyl("haft", (0, 0, -8), 0.45, 24, "wood", sides=6)
    em.rbox("bar", (0, 0, 16.2), (3.2, 0.5, 0.5), "iron")
    for x in (-1.3, 0, 1.3): em.rbox("tine%d" % int(x * 10), (x, 0, 18.3), (0.35, 0.35, 4.2), "iron")


GEAR = {"ice_axe": ice_axe, "fur_mantle": fur_mantle, "ice_staff": ice_staff, "ice_crown": ice_crown, "frost_greatsword": frost_greatsword,
        "fur_cloak": fur_cloak, "hook_claw": hook_claw, "fork_staff": fork_staff, "stone_shell": stone_shell, "heart_lantern": heart_lantern,
        "tower_shield": tower_shield, "flanged_mace": flanged_mace, "iron_greatsword": iron_greatsword, "wrist_chain": wrist_chain,
        "bog_club": bog_club, "reed_crown": reed_crown, "hexer_staff": hexer_staff, "root_crown": root_crown, "bone_rattle": bone_rattle,
        "moss_train": moss_train, "stone_maul": stone_maul, "rock_plates": rock_plates, "caller_staff": caller_staff, "horned_helm": horned_helm,
        "slab_shield": slab_shield, "warden_pick": warden_pick, "curved_blade": curved_blade, "head_wrap": head_wrap, "censer": censer,
        "sand_mantle": sand_mantle, "sun_disc": sun_disc, "glaive": glaive, "hooked_blade": hooked_blade, "gloam_hood": gloam_hood, "coat_tails": coat_tails,
        "knight_longsword": spun(knight_longsword, 90), "half_cloak": half_cloak, "sallow_mitre": sallow_mitre, "brand_knife": spun(brand_knife, -90), "reliquary": reliquary, "rust_sickle": spun(rust_sickle, 90),
        "vey_mantle": vey_mantle, "chain_of_office": chain_of_office, "ledger_staff": ledger_staff, "warden_halberd": spun(warden_halberd, 90),
        "warden_crest": warden_crest, "tamsin_wand": tamsin_wand, "tamsin_satchel": tamsin_satchel,
        "mother_crown": mother_crown, "mother_heart": mother_heart, "mother_claw": spun(mother_claw, 90), "mother_limbs": mother_limbs,
        "mother_pauldrons": mother_pauldrons, "mother_skirt": mother_skirt,
        "boar_spear": spun(boar_spear, 90), "round_shield": round_shield, "sedge_cane": sedge_cane, "pitchfork": spun(pitchfork, 90)}
# the grip's middle goes in the fist
rm.GRIP.update({"knight_longsword": 2.4, "brand_knife": 1.9, "rust_sickle": 0.5, "tamsin_wand": 1.5,
               "ledger_staff": -5.5, "warden_halberd": -4.5, "boar_spear": -4, "pitchfork": -4, "sedge_cane": -1})   # (a pole is held a little below its middle, upright)
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
    ("hooked_blade", "RARM", -50, hand(-1, -50, True, 1.15), (75, 0, 0), 1.15 * 1.1),
    ("gloam_hood", "HEAD", 0, (0, 0, 27 * 1.15), (0, 0, 0), 1.15),
    ("coat_tails", "BODY", 0, (0, 0, 13 * 1.15), (0, 0, 0), 1.15)])

fam("rime_brute", False, 1.1, {"rarm": (-30, 0, -6)}, [
    ("ice_axe", "RARM", -30, hand(-1, -30, False, 1.1), (70, 0, 0), 1.1 * 1.4),
    ("fur_mantle", "BODY", 0, (0, 0, 0), (0, 0, 0), 1.1)])
fam("frost_chanter", False, 1.0, {"larm": (-20, 0, 6)}, [
    ("ice_staff", "LARM", -20, hand(1, -20), (-8, 0, 0), 1)])
fam("hoarfrost_jarl", False, 1.25, {"rarm": (-35, 0, -6)}, [
    ("ice_crown", "HEAD", 0, (0, 0, 30.8 * 1.25), (0, 0, 0), 1.25),
    ("frost_greatsword", "RARM", -35, hand(-1, -35, False, 1.25), (75, 0, 0), 1.25),
    ("fur_cloak", "BODY", 0, (0, 0, 0), (0, 0, 0), 1.25)])
fam("hollow_crawler", True, 1.0, {"rarm": (-45, 0, -6), "larm": (-45, 0, 6)}, [
    ("hook_claw", "RARM", -45, hand(-1, -45, True), (85, 0, 0), 1),
    ("hook_claw", "LARM", -45, hand(1, -45, True), (85, 0, 0), 1)])
fam("hollow_singer", True, 1.0, {"rarm": (-20, 0, -6)}, [
    ("fork_staff", "RARM", -20, hand(-1, -20, True), (-8, 0, 0), 1)])
fam("deepwarden", False, 1.3, {}, [
    ("stone_shell", "BODY", 0, (0, 0, 0), (0, 0, 0), 1.3),
    ("heart_lantern", "BODY", 0, (0, 0, 0), (0, 0, 0), 1.3)])
fam("twin_haldric", False, 1.3, {"rarm": (-30, 0, -6), "larm": (-25, 0, 6)}, [
    ("tower_shield", "LARM", -25, (7.4 * 1.3, -5.8 * 1.3, 13 * 1.3), (0, 0, 8), 1.3),
    ("flanged_mace", "RARM", -30, hand(-1, -30, False, 1.3), (70, 0, 0), 1.3),
    ("wrist_chain", "RARM", -30, hand(-1, -30, False, 1.3, 8), (60, 0, 0), 1.3)])
fam("twin_oswin", False, 1.3, {"rarm": (-35, 0, -6), "larm": (-35, 0, 6)}, [
    ("iron_greatsword", "RARM", -35, hand(-1, -35, False, 1.3), (75, 0, 0), 1.3),
    ("wrist_chain", "LARM", -35, hand(1, -35, False, 1.3, 8), (60, 0, 0), 1.3)])

# ASHFALL, Arc 1
fam("corvin", False, 1.1, {"rarm": (-30, 0, -6)}, [
    ("knight_longsword", "RARM", -30, hand(-1, -30, False, 1.1), (70, 0, 0), 1.1),
    ("half_cloak", "BODY", 0, (0, 0, 0), (0, 0, 0), 1.1)])
fam("sallow", False, 1.3, {"rarm": (-45, 0, -6), "larm": (-15, 0, 6)}, [
    ("sallow_mitre", "HEAD", 0, (0, 0, 32 * 1.3), (0, 0, 0), 1.3),
    ("brand_knife", "RARM", -45, hand(-1, -45, False, 1.3), (85, 0, 0), 1.7),
    ("reliquary", "LARM", -15, hand(1, -15, False, 1.3), (-15, 0, 0), 1.3)])
fam("penitent", False, 1.0, {"rarm": (-40, 0, -6)}, [
    ("rust_sickle", "RARM", -40, hand(-1, -40), (80, 0, 0), 1.4)])
fam("ilsa", True, 0.95, {}, [])
# ASHFALL, Arc III
fam("mother", False, 1.9, {"rarm": (-35, 0, -6), "larm": (-35, 0, 6)}, [
    ("mother_crown", "HEAD", 0, (0, 0, 32 * 1.9), (0, 0, 0), 1.9),
    ("mother_heart", "BODY", 0, (0, 0, 0), (0, 0, 0), 1.9),
    ("mother_limbs", "BODY", 0, (0, 0, 0), (0, 0, 0), 1.9),
    ("mother_pauldrons", "BODY", 0, (0, 0, 0), (0, 0, 0), 1.9),
    ("mother_skirt", "BODY", 0, (0, 0, 0), (0, 0, 0), 1.9),
    ("mother_claw", "RARM", -35, hand(-1, -35, False, 1.9), (75, 0, 0), 1.9),
    ("mother_claw", "LARM", -35, hand(1, -35, False, 1.9), (75, 0, 0), 1.9)])
fam("maren", True, 1.0, {"rarm": (-15, 0, -6), "larm": (-25, 0, 6)}, [
    ("boar_spear", "RARM", -15, hand(-1, -15, True), (0, 0, 0), 1),
    ("round_shield", "LARM", -25, (7.2, -5.2, 13), (0, 0, 10), 1)])
fam("sedge", False, 0.95, {"larm": (-20, 0, 6)}, [
    ("sedge_cane", "LARM", -20, hand(1, -20, False, 0.95), (0, 0, 0), 0.95)])
fam("militia", False, 1.0, {"rarm": (-15, 0, -6)}, [
    ("pitchfork", "RARM", -15, hand(-1, -15), (0, 0, 0), 1)])
# ASHFALL, Arc II
fam("vey", False, 1.25, {"larm": (-25, 0, 6)}, [
    ("vey_mantle", "BODY", 0, (0, 0, 0), (0, 0, 0), 1.25),
    ("chain_of_office", "BODY", 0, (0, 0, 0), (0, 0, 0), 1.25),
    ("ledger_staff", "LARM", -25, hand(1, -25, False, 1.25), (0, 0, 0), 1.25)])
fam("warden", False, 1.15, {"rarm": (-20, 0, -6)}, [
    ("warden_halberd", "RARM", -20, hand(-1, -20, False, 1.15), (0, 0, 0), 1.15),
    ("warden_crest", "HEAD", 0, (0, 0, 32 * 1.15), (0, 0, 0), 1.15)])
fam("tamsin", True, 0.95, {"rarm": (-40, 0, -6)}, [
    ("tamsin_wand", "RARM", -40, hand(-1, -40, True, 0.95), (80, 0, 0), 0.95 * 1.6),
    ("tamsin_satchel", "BODY", 0, (0, 0, 0), (0, 0, 0), 0.95)])


def skin_of(name): return name if name.startswith("twin_") else ("story_" + name if name in STORY else "family_" + name)


def build(name):
    """One of them, with its gear (Blender: the front faces -Y)."""
    slim, k, pose, gear = FAMILY[name]
    rm.body(skin_of(name), slim, scale=k, pose=pose)
    for form, bone, arm, at, rot, sc in gear: rm.gear(form, at, rot, sc)


def java():
    """The same placements for client/RivalRenderer (paste into FAMILY_GEAR)."""
    out = []
    for name, (slim, k, pose, gear) in FAMILY.items():
        # in game the renderer scales the whole body by k: the placements go in unscaled
        ps = ",\n".join('                    new Piece("%s", Bone.%s, %g, v(%.3f, %.3f, %.3f), v(%g, %g, %g), %.3ff)' % ((f, b, a) + tuple(c / k for c in at) + tuple(rot) + (sc / k,)) for f, b, a, at, rot, sc in gear)
        out.append('            Map.entry("%s", List.of(\n%s))' % (skin_of(name), ps))
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
NEW = ["rime_brute", "frost_chanter", "hoarfrost_jarl", "hollow_crawler", "hollow_singer", "deepwarden", "twin_haldric", "twin_oswin"]
STORY = ["corvin", "sallow", "penitent", "ilsa", "vey", "warden", "tamsin", "mother", "maren", "sedge", "militia"]
STORY1, STORY2, STORY3 = STORY[:4], STORY[4:7], STORY[7:]
NAMES = {"mother": "The Mother Below (boss)", "maren": "Reeve Maren of Ashwick", "sedge": "Old Sedge (Mirefolk elder)", "militia": "Ashwick militia",
         "vey": "Magistrate Vey (boss)", "warden": "Vey's Warden", "tamsin": "Tamsin Reed",
         "corvin": "Corvin Hale (mentor)", "sallow": "Father Sallow (boss)", "penitent": "Penitent (Sallow's freed)", "ilsa": "Ilsa Marr (the other survivor)",
         "rime_brute": "Rime Brute", "frost_chanter": "Frost Chanter", "hoarfrost_jarl": "Hoarfrost Jarl (leader)", "hollow_crawler": "Hollow Crawler",
         "hollow_singer": "Hollow Singer", "deepwarden": "Deepwarden (leader)", "twin_haldric": "Ser Haldric (Ironbound Twin)", "twin_oswin": "Ser Oswin (Ironbound Twin)",
         "bog_crawler": "Bog Crawler", "mire_hexer": "Mire Hexer", "fen_matron": "Fen Matron (leader)", "scree_brute": "Scree Brute",
         "stonecaller": "Stonecaller", "peak_warden": "Peak Warden (leader)", "dune_stalker": "Dune Stalker", "sandwraith": "Sandwraith",
         "sun_herald": "Sun Herald (leader)", "gloamstalker": "Gloamstalker (night hunter)"}


VIEWS = (("front", -28), ("back", 152))
CAM = (56, 22)   # the camera: its height in pixels, and where it looks

def render(out, skins, groups=None):
    from PIL import ImageDraw
    groups = groups or (("a", ORDER[:len(ORDER) // 2]), ("b", ORDER[len(ORDER) // 2:]))
    ORDER_ = [n for _, g in groups for n in g]
    rm.SKINS = skins
    tex = os.path.join(out, "family_gear.png")
    paint(tex)
    CW, CH = 300, 470
    for name in ORDER_:
        for view, yaw in VIEWS:
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
            cam = bpy.data.cameras.new("cam"); cam.type = "ORTHO"; cam.ortho_scale = CAM[0]
            co = bpy.data.objects.new("cam", cam); sc.collection.objects.link(co); sc.camera = co
            el = math.radians(10)
            co.location = (0, -200 * math.cos(el), CAM[1] + 200 * math.sin(el)); co.rotation_euler = (math.radians(90) - el, 0, 0)
            sc.render.filepath = os.path.join(out, "fam_%s_%s.png" % (name, view))
            bpy.ops.render.render(write_still=True)
    for part, names in groups:
        sheet = Image.new("RGB", (CW * len(names), CH * len(VIEWS) + 30), (12, 12, 16))
        d = ImageDraw.Draw(sheet)
        for i, name in enumerate(names):
            for r, view in enumerate(v for v, _ in VIEWS):
                sheet.paste(Image.open(os.path.join(out, "fam_%s_%s.png" % (name, view))).convert("RGB"), (i * CW, 30 + r * CH))
            d.text((i * CW + 10, 8), NAMES[name], fill=(235, 228, 210))
        sheet.save(os.path.join(out, "family_sheet_%s.png" % part))


if __name__ == "__main__":
    mode, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    if mode == "render": render(out, sys.argv[3])
    if mode == "render_new": render(out, sys.argv[3], (("c", NEW[:6]), ("d", NEW[6:])))
    if mode == "render_story":
        CAM = (92, 40)
        if len(sys.argv) > 4: VIEWS = (("side", -90), ("top", 0)) if sys.argv[4] == "side" else (("front0", 0), ("side", -90))
        if os.environ.get("CAMZ"): CAM = tuple(float(v) for v in os.environ["CAMZ"].split(","))
        render(out, sys.argv[3], ((os.environ.get("SHEET", "story3"), [n for n in STORY3 if n in os.environ.get("ONLY", ",".join(STORY3)).split(",")]),))
    if mode == "game": to_game(out)
    if mode == "java": print(java())
