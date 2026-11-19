"""The Architect of Ruin Grimoire's fortress pieces, modelled in Blender (run with Blender's Python: bpy). Original designs:

  Rampart wall     two blocks wide, three tall: grey stone courses, a brass band, merlons on top, an arrow slit
  Gatehouse        an arch with a brass-banded portcullis and two small flag poles
  Watchtower       a round stone tower, crenellated, an ember-orange banner (arrows)
  Arcane spire     a slim tower, open at the top, a teal crystal floating over it (beams)
  Forge tower      a square tower with a chimney and a glowing ember window (fire)
  Siege platform   a low square bastion with a ballista on top (bolts)
  Ballista         the Workshop's turret on its own: a wooden frame, brass-capped arms, a loaded bolt
  Bone sentry      the Crypt's guard: a hooded figure of pale stone and bone, a tower shield and a halberd

Blender space (pixels; exported at 1/16): Z up, X across, the front looking toward -Y. Nothing here is ever placed as
blocks in the world: in the game they are entities (FortressWall, FortressTower...) drawn with these models.

  python tools/blender/architect_ruin.py render OUT_DIR [names]   a sheet of every model
  python tools/blender/architect_ruin.py game OUT_DIR             writes the models into the mod (models/item, textures/item)
"""
import math, os, sys
import bpy, bmesh
from mathutils import Vector, Matrix
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gilded_thorn as gt

TEX = "architect_ruin"
gt.REG.clear()
gt.REG.update({"stone": (0, 0, 64, 64), "stone_d": (64, 0, 128, 64), "stone_l": (128, 0, 192, 64), "mortar": (192, 0, 256, 64),
               "brass": (0, 64, 64, 96), "brass_d": (64, 64, 128, 96), "wood": (128, 64, 192, 96), "wood_d": (192, 64, 256, 96),
               "cloth": (0, 96, 64, 128), "cloth_d": (64, 96, 128, 128), "ember": (128, 96, 160, 128), "arcane": (160, 96, 192, 128),
               "bone": (192, 96, 224, 128), "iron": (224, 96, 256, 128), "slate": (0, 128, 64, 160), "hood": (64, 128, 128, 160),
               "dark": (128, 128, 160, 160)})
REG = gt.REG
GLOW = []
LOW = False


# ------------------------------------------------------------------ the texture
def mix(a, b, t): t = max(0.0, min(1.0, t)); return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def paint(out):
    im = Image.new("RGBA", (256, 256), (0, 0, 0, 255)); px = im.load()
    def region(name, fn):
        x0, y0, x1, y1 = REG[name]
        for x in range(x0, x1):
            for y in range(y0, y1):
                px[x, y] = fn((x - x0) / (x1 - x0 - 1), (y - y0) / (y1 - y0 - 1), x - x0, y - y0) + (255,)
    def grain(x, y, a=0.37, b=0.23): return 0.5 + 0.25 * math.sin(x * a + y * b) + 0.25 * math.sin(x * 0.91 - y * 0.57)
    def speck(x, y): return ((x * 73 + y * 151) % 17) / 16.0
    region("stone", lambda u, v, x, y: mix((96, 98, 104), (148, 150, 156), 0.35 * grain(x, y) + 0.4 * speck(x, y)))
    region("stone_d", lambda u, v, x, y: mix((58, 60, 68), (92, 94, 102), 0.35 * grain(x, y) + 0.4 * speck(x, y)))
    region("stone_l", lambda u, v, x, y: mix((150, 152, 158), (196, 196, 198), 0.35 * grain(x, y) + 0.4 * speck(x, y)))
    region("mortar", lambda u, v, x, y: mix((40, 40, 46), (62, 62, 68), speck(x, y)))
    region("brass", lambda u, v, x, y: mix((176, 128, 52), (236, 196, 110), 0.5 + 0.35 * math.sin(x * 0.12 + y * 0.07)))
    region("brass_d", lambda u, v, x, y: mix((96, 64, 24), (150, 104, 40), grain(x, y)))
    region("wood", lambda u, v, x, y: mix((104, 70, 40), (150, 104, 62), 0.5 + 0.5 * math.sin(y * 0.9 + math.sin(x * 0.15) * 2)))
    region("wood_d", lambda u, v, x, y: mix((58, 38, 22), (88, 58, 34), 0.5 + 0.5 * math.sin(y * 0.9 + math.sin(x * 0.15) * 2)))
    region("cloth", lambda u, v, x, y: mix((214, 96, 28), (246, 150, 60), 0.5 + 0.3 * math.sin(x * 0.4) + 0.2 * v))
    region("cloth_d", lambda u, v, x, y: mix((120, 44, 14), (170, 70, 22), 0.5 + 0.3 * math.sin(x * 0.4)))
    region("ember", lambda u, v, x, y: mix((255, 236, 170), (255, 120, 30), abs(v - 0.5) * 2))
    region("arcane", lambda u, v, x, y: mix((220, 255, 250), (60, 210, 200), abs(v - 0.5) * 2))
    region("bone", lambda u, v, x, y: mix((200, 192, 170), (236, 230, 212), grain(x, y)))
    region("iron", lambda u, v, x, y: mix((46, 48, 56), (96, 100, 112), grain(x, y)))
    region("slate", lambda u, v, x, y: mix((44, 50, 66), (74, 82, 102), 0.5 + 0.5 * math.sin(y * 1.6)))
    region("hood", lambda u, v, x, y: mix((40, 40, 50), (78, 76, 88), grain(x, y)))
    region("dark", lambda u, v, x, y: (10, 10, 14))
    im.save(out)


# ------------------------------------------------------------------ helpers
def box(name, c, s, region, bevel=0.0, glow=False):
    o = gt.box(name, c, s, region, bevel=bevel, segs=1)
    if glow: GLOW.append(o)
    return o


def tube(name, pts, region, res=4, bev=2, glow=False):
    if LOW: res, bev = 2, 1
    o = gt.curve_obj(name, pts, flat=1.0, region=region, res=res, bev=bev)
    if glow: GLOW.append(o)
    return o


def lathe(name, prof, region, M=12, glow=False, sx=1.0):
    """A turned body through prof [(radius, z)], bottom first, capped at the top."""
    bm = bmesh.new(); rows = []
    for (r, z) in prof:
        rows.append([bm.verts.new((r * math.cos(2 * math.pi * j / M + math.pi / M) * sx, r * math.sin(2 * math.pi * j / M + math.pi / M), z)) for j in range(M)])
    for i in range(len(rows) - 1):
        for j in range(M):
            k = (j + 1) % M
            bm.faces.new((rows[i][j], rows[i][k], rows[i + 1][k], rows[i + 1][j]))
    bm.faces.new(rows[-1][::-1]); bm.faces.new(rows[0])
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    o = gt.new_obj(name, bm, REG[region])
    if glow: GLOW.append(o)
    return o


def gem(name, c, r, h, region, glow=True):
    """A long double-pointed crystal."""
    bm = bmesh.new()
    top = bm.verts.new((c[0], c[1], c[2] + h)); bot = bm.verts.new((c[0], c[1], c[2] - h))
    ring = [bm.verts.new((c[0] + r * math.cos(a * math.pi / 3), c[1] + r * math.sin(a * math.pi / 3), c[2])) for a in range(6)]
    for i in range(6):
        bm.faces.new((ring[i], ring[(i + 1) % 6], top)); bm.faces.new((ring[(i + 1) % 6], ring[i], bot))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    o = gt.new_obj(name, bm, REG[region])
    if glow: GLOW.append(o)
    return o


def courses(prefix, x0, x1, z0, z1, y, depth, h=4.0, region_a="stone", region_b="stone_l"):
    """Stone courses on a wall face: rows of blocks, every other row shifted half a block, a mortar back."""
    box(prefix + "_back", ((x0 + x1) / 2, y + depth / 2, (z0 + z1) / 2), (x1 - x0, depth, z1 - z0), "mortar")
    row, z = 0, z0
    while z < z1 - 0.01:
        hh = min(h, z1 - z)
        bw = 8.0
        x = x0 - (bw / 2 if row % 2 else 0)
        k = 0
        while x < x1 - 0.01:
            a, b = max(x, x0), min(x + bw, x1)
            if b - a > 0.6:
                reg = region_b if (row * 3 + k) % 5 == 0 else region_a
                box("%s_%d_%d" % (prefix, row, k), ((a + b) / 2, y - 0.15, z + hh / 2), (b - a - 0.5, depth * 0.35, hh - 0.5), reg)
            x += bw; k += 1
        z += h; row += 1


def merlons(prefix, x0, x1, z, y, depth, n):
    w = (x1 - x0) / (2 * n - 1)
    for i in range(n):
        x = x0 + w * (2 * i) + w / 2
        box("%s_m%d" % (prefix, i), (x, y, z + 2.2), (w, depth, 4.4), "stone", bevel=0.2)


# ------------------------------------------------------------------ the pieces
def wall():
    W, Hh, D = 16.0, 38.0, 10.0   # one block wide: the game lines these up side by side
    box("w_core", (0, 0, Hh / 2), (W, D - 1.5, Hh), "stone_d")
    courses("w_front", -W / 2, W / 2, 0, Hh, -D / 2, 1.2)
    courses("w_backside", -W / 2, W / 2, 0, Hh, D / 2 - 1.2, 1.2)
    box("w_band", (0, 0, Hh * 0.62), (W, D + 0.6, 1.6), "brass")
    box("w_band_d", (0, 0, Hh * 0.62 - 1.0), (W, D + 0.7, 0.5), "brass_d")
    box("w_walk", (0, 0, Hh + 0.6), (W, D, 1.2), "stone_l")
    merlons("w", -W / 2, W / 2, Hh + 1.2, 0, D, 2)
    box("w_slit", (0, -D / 2 - 0.2, Hh * 0.35), (1.2, 0.8, 6.0), "dark")
    box("w_slit2", (0, -D / 2 - 0.2, Hh * 0.35), (3.2, 0.8, 1.0), "dark")
    box("w_plinth", (0, 0, 1.2), (W, D + 1.4, 2.4), "stone_d", bevel=0.3)


def gatehouse():
    W, Hh, D = 48.0, 44.0, 12.0
    for sx in (-1, 1):
        box("g_pier%d" % sx, (sx * 17, 0, Hh / 2), (14, D, Hh), "stone_d")
        courses("g_front%d" % sx, sx * 17 - 7, sx * 17 + 7, 0, Hh, -D / 2, 1.2)
        merlons("g_top%d" % sx, sx * 17 - 7, sx * 17 + 7, Hh, 0, D, 2)
        tube("g_pole%d" % sx, [(sx * 21, 0, Hh + 4, 0.5), (sx * 21, 0, Hh + 18, 0.5)], "wood_d")
        bm_pts = [(sx * 21 + sx * 0.4, 0, Hh + 17, 0.2), (sx * 21 + sx * 8, 0, Hh + 15.5, 0.2)]
        box("g_flag%d" % sx, (sx * 25, 0, Hh + 14.5), (7.5, 0.5, 5.5), "cloth")
    # the arch over the opening
    pts = []
    for i in range(13):
        a = math.pi * i / 12
        pts.append((10 * math.cos(a), -D / 2 + 0.2, 26 + 10 * math.sin(a), 2.2))
    tube("g_arch", pts, "stone_l", res=6)
    box("g_lintel", (0, 0, 38), (20, D, 12), "stone_d")
    courses("g_lintelf", -10, 10, 33, Hh, -D / 2, 1.2)
    merlons("g_topm", -10, 10, Hh, 0, D, 3)
    # the portcullis: iron bars, brass bands, spikes at the foot
    for i in range(5):
        x = -8 + 4 * i
        box("g_bar%d" % i, (x, -1.0, 15), (1.0, 1.0, 26), "iron")
        gt.cone("g_spike%d" % i, (x, -1.0, 2.2), (0, 0, -1), 0.7, 2.0, region="iron", segs=4)
    for z in (8, 17, 25):
        box("g_band%d" % z, (0, -1.0, z), (18, 1.4, 1.2), "brass")
    box("g_crest", (0, -D / 2 - 0.5, 36), (6, 1, 6), "brass", bevel=0.4)
    box("g_crest_g", (0, -D / 2 - 1.0, 36), (2.4, 0.6, 2.4), "ember", glow=True)


def watchtower():
    prof = [(11, 0), (10.5, 3), (9.5, 46), (11.5, 48), (11.5, 52)]
    lathe("t_body", prof, "stone", M=12)
    for i, z in enumerate(range(6, 44, 6)):   # course lines round it
        lathe("t_ring%d" % i, [(9.9 + (44 - z) * 0.02, z), (9.9 + (44 - z) * 0.02, z + 0.6)], "mortar", M=12)
    lathe("t_band", [(10.2, 30), (10.2, 31.6)], "brass", M=12)
    for i in range(6):
        a = 2 * math.pi * i / 6
        box("t_m%d" % i, (math.cos(a) * 10, math.sin(a) * 10, 54.5), (5, 3.4, 5), "stone", bevel=0.2)
    for a in (0, math.pi / 2, math.pi, 3 * math.pi / 2):
        box("t_slit%.1f" % a, (math.cos(a + math.pi / 2) * 9.9, math.sin(a + math.pi / 2) * 9.9 * -1, 34), (1.2, 1.2, 6), "dark")
    lathe("t_roof", [(8, 52), (0.2, 66)], "slate", M=12)
    tube("t_pole", [(0, 0, 64, 0.45), (0, 0, 76, 0.45)], "wood_d")
    box("t_banner", (4.6, 0, 72.5), (8.5, 0.5, 6.0), "cloth")
    box("t_banner_d", (4.6, -0.3, 72.5), (2.0, 0.3, 2.0), "brass")
    box("t_door", (0, -10, 5), (5, 1.4, 9), "wood_d")


def arcane_spire():
    prof = [(8.5, 0), (8, 3), (6, 44), (8, 46), (8, 48)]
    lathe("a_body", prof, "stone_d", M=8)
    for i, z in enumerate((10, 22, 34)):
        lathe("a_ring%d" % i, [(8.2 - z * 0.05, z), (8.2 - z * 0.05, z + 1.2)], "brass", M=8)
    for i in range(4):   # four brass claws reaching up to hold the crystal
        a = math.pi / 4 + i * math.pi / 2
        tube("a_claw%d" % i, [(math.cos(a) * 7, math.sin(a) * 7, 47, 0.8), (math.cos(a) * 6.5, math.sin(a) * 6.5, 53, 0.7),
                               (math.cos(a) * 3.5, math.sin(a) * 3.5, 57, 0.5)], "brass")
    gem("a_crystal", (0, 0, 60), 3.6, 7, "arcane")
    lathe("a_runes", [(6.3, 18), (6.3, 19)], "arcane", M=8, glow=True)
    lathe("a_runes2", [(5.7, 30), (5.7, 31)], "arcane", M=8, glow=True)
    box("a_door", (0, -7.6, 5), (4, 1.4, 8), "wood_d")


def forge_tower():
    S, Hh = 20.0, 42.0
    box("f_core", (0, 0, Hh / 2), (S - 1.5, S - 1.5, Hh), "stone_d")
    for sy, rot in ((-1, 0), (1, 0)):
        courses("f_fy%d" % sy, -S / 2, S / 2, 0, Hh, sy * (S / 2) - (1.2 if sy > 0 else 0), 1.2)
    for sx in (-1, 1):
        for i, z in enumerate(range(0, int(Hh), 4)):
            box("f_side%d_%d" % (sx, i), (sx * (S / 2 - 0.3), 0, z + 2), (1.0, S - 0.6, 3.5), "stone" if i % 3 else "stone_l")
    merlons("f_mf", -S / 2, S / 2, Hh, -S / 2 + 1.5, 3, 3)
    merlons("f_mb", -S / 2, S / 2, Hh, S / 2 - 1.5, 3, 3)
    box("f_floor", (0, 0, Hh + 0.6), (S, S, 1.2), "stone_l")
    box("f_chimney", (5, 4, Hh + 9), (6, 6, 18), "stone_d", bevel=0.3)
    box("f_chim_cap", (5, 4, Hh + 18.5), (7.5, 7.5, 1.4), "brass")
    box("f_chim_glow", (5, 4, Hh + 18.2), (4, 4, 1.2), "ember", glow=True)
    box("f_window", (0, -S / 2 - 0.2, 24), (7, 0.8, 8), "ember", glow=True)
    for x in (-2.4, 0, 2.4):
        box("f_wbar%.1f" % x, (x, -S / 2 - 0.6, 24), (0.6, 0.6, 8.4), "iron")
    box("f_anvil", (0, -S / 2 - 0.9, 30), (6, 0.8, 2.4), "brass")
    box("f_door", (0, -S / 2 - 0.2, 5), (6, 1.2, 10), "wood_d")


def ballista(z0=0.0, scale=1.0):
    s = len(gt.PARTS)
    box("b_base", (0, 0, 2), (10, 12, 4), "wood_d", bevel=0.3)
    box("b_post", (0, 0, 6), (3, 3, 6), "wood")
    box("b_stock", (0, -2, 9.5), (2.6, 18, 2.2), "wood")
    box("b_rail", (0, -2, 10.9), (1.2, 18, 0.6), "brass_d")
    for sx in (-1, 1):   # the arms bent back, brass capped, the string between their tips
        tube("b_arm%d" % sx, [(0, -8, 10, 0.9), (sx * 6, -7, 10.4, 0.8), (sx * 11, -3, 10.6, 0.6)], "wood")
        box("b_cap%d" % sx, (sx * 11, -3, 10.6), (1.6, 1.6, 1.6), "brass")
    tube("b_string", [(-11, -3, 10.7, 0.18), (0, 5, 10.9, 0.18), (11, -3, 10.7, 0.18)], "bone", res=2)
    tube("b_bolt", [(0, 5, 11.4, 0.45), (0, -14, 11.4, 0.45)], "wood_d")
    gt.cone("b_tip", (0, -14, 11.4), (0, -1, 0), 0.9, 3.0, region="iron", segs=4)
    for sx in (-1, 1):
        box("b_fl%d" % sx, (sx * 0.8, 4.4, 11.4), (1.4, 2.0, 0.2), "cloth")
    box("b_wheel", (0, 6.5, 8), (5, 1.2, 5), "brass_d", bevel=0.4)
    if scale != 1.0 or z0:
        M = Matrix.Translation((0, 0, z0)) @ Matrix.Scale(scale, 4)
        for ob in gt.PARTS[s:]: ob.data.transform(M)


def siege_platform():
    S, Hh = 26.0, 22.0
    box("p_core", (0, 0, Hh / 2), (S - 1.5, S - 1.5, Hh), "stone_d")
    for sy in (-1, 1):
        courses("p_fy%d" % sy, -S / 2, S / 2, 0, Hh, sy * (S / 2) - (1.2 if sy > 0 else 0), 1.2)
    for sx in (-1, 1):
        for i, z in enumerate(range(0, int(Hh), 4)):
            box("p_side%d_%d" % (sx, i), (sx * (S / 2 - 0.3), 0, z + 2), (1.0, S - 0.6, 3.5), "stone" if i % 2 else "stone_l")
    box("p_floor", (0, 0, Hh + 0.6), (S, S, 1.2), "wood_d")
    for (x, y) in ((-S / 2 + 2, -S / 2 + 2), (S / 2 - 2, -S / 2 + 2), (-S / 2 + 2, S / 2 - 2), (S / 2 - 2, S / 2 - 2)):
        box("p_post%.0f%.0f" % (x, y), (x, y, Hh + 4), (3.4, 3.4, 6), "stone", bevel=0.2)
    box("p_band", (0, 0, Hh - 3), (S + 0.4, S + 0.4, 1.2), "brass")
    ballista(Hh + 1.2, 1.0)


def bone_sentry():
    # legs and robe
    lathe("s_robe", [(5.2, 0), (5.0, 3), (4.0, 14), (3.4, 18)], "hood", M=10)
    lathe("s_body", [(3.6, 17), (4.2, 22), (3.8, 27), (2.6, 29)], "stone_l", M=10)
    lathe("s_belt", [(4.15, 18.5), (4.15, 19.8)], "brass", M=10)
    for i in range(3):   # the ribs: bone bars across the chest
        box("s_rib%d" % i, (0, -3.9, 21 + i * 1.8), (5.2, 0.6, 0.7), "bone")
    lathe("s_hood", [(3.2, 28), (3.6, 31), (3.2, 35), (1.2, 37.5)], "hood", M=10)
    box("s_face", (0, -2.5, 32), (3.2, 1.0, 3.6), "dark")
    for sx in (-1, 1):
        box("s_eye%d" % sx, (sx * 0.8, -3.1, 32.6), (0.7, 0.4, 0.5), "ember", glow=True)
        box("s_shoulder%d" % sx, (sx * 4.6, 0, 26.5), (3.0, 4.0, 2.6), "bone", bevel=0.3)
    # the tower shield on the left, the halberd in the right
    box("s_shield", (-6.5, -2.0, 18), (1.2, 7.5, 13), "stone", bevel=0.3)
    box("s_shield_rim", (-7.2, -2.0, 18), (0.4, 8.2, 13.8), "brass")
    box("s_shield_boss", (-7.6, -2.0, 19), (0.6, 2.4, 2.4), "bone")
    tube("s_haft", [(6.4, -1.5, 1, 0.45), (6.4, -1.5, 40, 0.45)], "wood_d")
    gt.cone("s_point", (6.4, -1.5, 40), (0, 0, 1), 0.8, 4.0, region="bone", segs=4)
    box("s_blade", (8.2, -1.5, 36.5), (3.4, 0.5, 5.0), "bone")
    box("s_hook", (4.8, -1.5, 36.5), (1.6, 0.5, 2.2), "bone")


MODELS = {"wall": wall, "gatehouse": gatehouse, "watchtower": watchtower, "arcane_spire": arcane_spire, "forge_tower": forge_tower,
          "siege_platform": siege_platform, "ballista": ballista, "bone_sentry": bone_sentry}
NAMES = {"wall": "Rampart wall", "gatehouse": "Gatehouse", "watchtower": "Watchtower (arrows)", "arcane_spire": "Arcane spire (beams)",
         "forge_tower": "Forge tower (fire)", "siege_platform": "Siege platform (bolts)", "ballista": "Ballista (Workshop)", "bone_sentry": "Bone sentry (Crypt)"}
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
    print(form, "tris", tris(ob))
    return ob


def render_sheet(out_dir, only=None):
    forms = [f for f in ORDER if not only or f in only]
    CW, CH = 420, 480
    for form in forms:
        ob = build(form, out_dir)
        ob.rotation_euler = (0, 0, math.radians(-30))
        sc = bpy.context.scene
        sc.render.engine = "CYCLES"; sc.cycles.samples = 24; sc.cycles.device = "CPU"
        sc.view_settings.view_transform = "Standard"
        sc.render.resolution_x, sc.render.resolution_y = CW, CH
        world = bpy.data.worlds.new("w"); sc.world = world; world.use_nodes = True
        world.node_tree.nodes["Background"].inputs[0].default_value = (0.05, 0.05, 0.07, 1)
        for name, rot, energy in (("key", (math.radians(50), 0, math.radians(-35)), 4.0), ("fill", (math.radians(70), 0, math.radians(150)), 1.6),
                                  ("top", (math.radians(10), 0, 0), 1.2)):
            L = bpy.data.lights.new(name, "SUN"); L.energy = energy
            o = bpy.data.objects.new(name, L); o.rotation_euler = rot; sc.collection.objects.link(o)
        bpy.context.view_layer.update()
        ws = [ob.matrix_world @ v.co for v in ob.data.vertices]
        xs, zs = [w.x for w in ws], [w.z for w in ws]
        w, h = max(xs) - min(xs), max(zs) - min(zs)
        cam = bpy.data.cameras.new("cam"); cam.type = "ORTHO"; cam.ortho_scale = max(h, w * CH / CW) * 1.25
        co = bpy.data.objects.new("cam", cam); sc.collection.objects.link(co); sc.camera = co
        el = math.radians(18)
        co.location = ((min(xs) + max(xs)) / 2, -200 * math.cos(el), (min(zs) + max(zs)) / 2 + 200 * math.sin(el))
        co.rotation_euler = (math.radians(90) - el, 0, 0)
        sc.render.filepath = os.path.join(out_dir, "arch_%s.png" % form)
        bpy.ops.render.render(write_still=True)
    cols = 4
    rows = (len(forms) + cols - 1) // cols
    sheet = Image.new("RGB", (CW * cols, CH * rows), (10, 10, 14))
    d = ImageDraw.Draw(sheet)
    for i, form in enumerate(forms):
        x, y = (i % cols) * CW, (i // cols) * CH
        sheet.paste(Image.open(os.path.join(out_dir, "arch_%s.png" % form)).convert("RGB"), (x, y))
        d.text((x + 10, y + 8), NAMES[form], fill=(235, 228, 210))
    sheet.save(os.path.join(out_dir, "architect_sheet.png"))


# ------------------------------------------------------------------ into the game
GAME = ["wall", "gatehouse", "watchtower", "arcane_spire", "forge_tower", "siege_platform", "ballista", "bone_sentry"]


def to_game(out_dir):
    """Each piece into the mod as fortress_<name> (an item model the entity renderers draw; never placed as blocks)."""
    for form in GAME:
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
        name = "fortress_" + form
        ob.name = name
        ob.location = (0, 0, 0)   # the entity renderers draw it round the entity's feet: middle at the origin, standing on it
        bpy.ops.wm.obj_export(filepath=os.path.join(out_dir, name + ".obj"), export_selected_objects=True, export_uv=True, export_normals=True,
                              export_materials=True, export_triangulated_mesh=True, forward_axis="NEGATIVE_Z", up_axis="Y", global_scale=1 / 16, path_mode="STRIP")
        print(form, "tris", tris(ob))
        disp = {"gui": {"rotation": [20, 225, 0], "translation": [0, -4, 0], "scale": [0.22] * 3},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.25] * 3},
                "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.25] * 3}}
        gt.export_to_game(name, out_dir, disp, glowing={form + "_glow"}, textures={form: TEX, form + "_glow": TEX}, render_type="minecraft:cutout")


if __name__ == "__main__":
    mode, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    paint(os.path.join(out, TEX + ".png"))
    if mode == "render": render_sheet(out, sys.argv[3].split(",") if len(sys.argv) > 3 else None)
    if mode == "game": LOW = True; to_game(out)
