"""
The Shadow Grimoire's exclusive shadow - original designs, in the army's black and blue:

  shadow_knight - the Oathbound Knight: a tall swordmaster in layered black plate traced with glowing blue seams, a
                  closed helm with a Y-shaped visor slit burning blue, a tattered blue cape in strips, and a long
                  straight greatsword with a blue-glowing fuller, held point-down at rest.
                  Animations: rise (climbs up out of the ground), idle, walk, slash.

Made with the feet at y = 0, facing north (-Z). One 64x64 tile per material on a 512x512 sheet; the _glowmask sheet
lights the seams, the visor, the fuller, the veins and the eyes.
Run from the project root: python3 tools/shadow_models.py   (preview: python3 tools/preview_geo.py shadow_knight idle ...)
"""
import json
import math
import os
import random
import sys
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
import geo_paint as gp

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "iron_spell_grimoire")
GEO = os.path.join(ROOT, "geo")
ANIM = os.path.join(ROOT, "animations")
TEX = os.path.join(ROOT, "textures", "geo")

T = 64
SHEET = 512
TILES = {"plate": (0, 0), "plate2": (1, 0), "mail": (2, 0), "cape": (3, 0), "visor": (4, 0), "blade": (5, 0), "glow": (6, 0), "grip": (7, 0),
         "scale": (0, 1), "belly": (1, 1), "membrane": (2, 1), "horn": (3, 1), "eye": (4, 1), "bone": (5, 1)}
BLACK = (16, 16, 22)
BLUE = (40, 120, 255)
BRIGHT = (120, 200, 255)


def fill(img, box, base, amount, rnd):
    px = img.load()
    for y in range(box[1], box[3]):
        for x in range(box[0], box[2]):
            n = rnd.randint(-amount, amount)
            px[x, y] = tuple(max(0, min(255, c + n)) for c in base) + (255,)


def make_texture(path, glow_path):
    rnd = random.Random(11)
    img = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
    glow = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
    d, gd = ImageDraw.Draw(img), ImageDraw.Draw(glow)

    def box(n):
        c, r = TILES[n]
        return c * T, r * T, c * T + T, r * T + T

    for name, base in (("plate", (22, 22, 30)), ("plate2", (14, 14, 20))):   # black plate, blue seams near the edges
        b = box(name); fill(img, b, base, 4, rnd)
        for i in (3, 4):
            d.rectangle([b[0] + i, b[1] + i, b[2] - 1 - i, b[3] - 1 - i], outline=BLUE + (255,))
            gd.rectangle([b[0] + i, b[1] + i, b[2] - 1 - i, b[3] - 1 - i], outline=BRIGHT + (255,))
        d.line([b[0] + 32, b[1] + 8, b[0] + 32, b[3] - 9], fill=(34, 36, 46, 255), width=2)   # a raised ridge
    b = box("mail"); fill(img, b, (20, 20, 26), 3, rnd)   # mail: small dark rings
    for y in range(b[1], b[3], 4):
        for x in range(b[0] + (y // 4 % 2) * 2, b[2], 4):
            d.ellipse([x, y, x + 3, y + 3], outline=(46, 50, 64, 255))
    b = box("cape"); fill(img, b, (22, 54, 140), 10, rnd)   # tattered blue cloth, darker folds, frayed bottom
    for x in range(b[0], b[2], 8):
        d.line([x + 3, b[1], x + 3, b[3]], fill=(14, 34, 96, 255), width=2)
    for x in range(b[0], b[2], 5):
        d.rectangle([x, b[3] - rnd.randint(4, 14), x + rnd.randint(1, 3), b[3] - 1], fill=(0, 0, 0, 0))
    b = box("visor"); fill(img, b, (12, 12, 18), 3, rnd)   # the Y-shaped visor slit, burning blue
    for g in (d, gd):
        col = (BLUE if g is d else BRIGHT) + (255,)
        g.line([b[0] + 32, b[1] + 30, b[0] + 32, b[1] + 54], fill=col, width=5)
        g.line([b[0] + 32, b[1] + 32, b[0] + 12, b[1] + 16], fill=col, width=5)
        g.line([b[0] + 32, b[1] + 32, b[0] + 52, b[1] + 16], fill=col, width=5)
    b = box("blade"); fill(img, b, (30, 32, 42), 4, rnd)   # dark steel, a glowing fuller down the middle
    d.rectangle([b[0] + 26, b[1], b[0] + 37, b[3] - 1], fill=BLUE + (255,))
    gd.rectangle([b[0] + 28, b[1], b[0] + 35, b[3] - 1], fill=BRIGHT + (255,))
    d.rectangle([b[0], b[1], b[0] + 3, b[3] - 1], fill=(120, 130, 160, 255))   # the edges catch a little light
    d.rectangle([b[2] - 4, b[1], b[2] - 1, b[3] - 1], fill=(120, 130, 160, 255))
    b = box("glow"); fill(img, b, BLUE, 8, rnd); gd.rectangle(b, fill=BRIGHT + (255,))
    b = box("grip"); fill(img, b, (10, 10, 14), 3, rnd)
    for y in range(b[1], b[3], 6):
        d.line([b[0], y, b[2], y + 4], fill=(30, 50, 110, 255), width=2)
    b = box("scale"); fill(img, b, (18, 18, 26), 4, rnd)   # black scales with blue veins
    for y in range(b[1], b[3], 8):
        for x in range(b[0] + (y // 8 % 2) * 4, b[2], 8):
            d.arc([x, y, x + 8, y + 8], 0, 180, fill=(34, 36, 50, 255))
    for _ in range(5):
        x, y = rnd.randint(b[0], b[2] - 1), b[1]
        pts = [(x, y)]
        while y < b[3] - 1:
            x += rnd.randint(-3, 3); y += rnd.randint(4, 8); pts.append((max(b[0], min(b[2] - 1, x)), min(b[3] - 1, y)))
        d.line(pts, fill=BLUE + (255,), width=1); gd.line(pts, fill=BRIGHT + (255,), width=1)
    b = box("belly"); fill(img, b, (28, 30, 42), 4, rnd)
    for y in range(b[1] + 4, b[3], 8):
        d.line([b[0], y, b[2], y], fill=(16, 16, 22, 255), width=2)
    b = box("membrane"); fill(img, b, (14, 26, 70), 8, rnd)   # dark blue wing skin with ribs
    for x in range(b[0] + 6, b[2], 14):
        d.line([x, b[1], x + 4, b[3]], fill=(8, 10, 20, 255), width=2)
    b = box("horn"); fill(img, b, (26, 28, 38), 4, rnd)
    for y in range(b[1], b[3], 6):
        d.line([b[0], y, b[2], y], fill=(40, 44, 60, 255))
    b = box("eye"); fill(img, b, BRIGHT, 6, rnd); gd.rectangle(b, fill=(200, 235, 255, 255))
    b = box("bone"); fill(img, b, (34, 36, 48), 5, rnd)
    img.save(path)
    glow.save(glow_path)


# ---------------------------------------------------------------------------------------------------- geometry
def cube(origin, size, mat, rot=None, pivot=None, inflate=0):
    cb = {"origin": [round(v, 3) for v in origin], "size": [round(v, 3) for v in size], "mat": mat}
    if inflate: cb["inflate"] = inflate
    if rot:
        cb["rotation"] = rot
        cb["pivot"] = pivot or [origin[0] + size[0] / 2, origin[1] + size[1] / 2, origin[2] + size[2] / 2]
    return cb


def bone(name, parent, pivot, cubes, rotation=None):
    b = {"name": name, "pivot": [round(v, 3) for v in pivot], "cubes": cubes}
    if parent: b["parent"] = parent
    if rotation: b["rotation"] = rotation
    return b


def mirror_x(cubes):
    out = []
    for c in cubes:
        c = json.loads(json.dumps(c))
        c["origin"][0] = -(c["origin"][0] + c["size"][0])
        if "pivot" in c:
            c["pivot"][0] = -c["pivot"][0]
            c["rotation"] = [c["rotation"][0], -c["rotation"][1], -c["rotation"][2]]
        out.append(c)
    return out


def geo(name, bones, w=128, h=128):
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry." + name, "texture_width": SHEET, "texture_height": SHEET,
                        "visible_bounds_width": 6, "visible_bounds_height": 6, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": bones}]}


def knight():
    """The Oathbound Knight (an original design): a towering, menacing shadow, about 3.3 blocks to the horn tips.
    A horned helm crowned with jagged spikes, a fanged jaw-grille and two slanted eye slits whose blue fire trails back
    along the helm; a ribcage breastplate with a burning blue core in the chest; huge pauldrons with blades sweeping
    up off them; clawed gauntlets; spikes down the spine, chains at the hip, spiked knees and boots; a long ragged
    cape; and a long glowing blue sword with dark smoky veins winding up the blade, a black horned guard and a black grip."""
    def sym(cubes): return cubes + mirror_x(cubes)
    B = [bone("root", None, [0, 0, 0], [])]
    body = [
        cube([-6, 26, -3.4], [12, 13, 6.8], "plate"),                # the torso, broad and deep
        cube([-6.6, 33.5, -4.1], [13.2, 5.5, 1], "plate2"),          # the upper chest plate, stepped out
        cube([-2.1, 30.2, -4.45], [4.2, 4.2, 0.6], "plate2"),        # the core's frame
        cube([-1.3, 31, -4.75], [2.6, 2.6, 0.8], "glow"),            # the burning core in the chest
        cube([-4.5, 22, -2.8], [9, 4.5, 5.6], "plate2"),             # a narrow waist
        cube([-5, 21.4, -3.1], [10, 0.8, 6.2], "glow"),              # a glowing belt
        cube([-1.6, 20.4, -3.75], [3.2, 2.6, 0.8], "plate"),         # the buckle: a fanged face
        cube([-5.4, 17.5, -3.2], [10.8, 4, 6.4], "plate2"),          # layered faulds
        cube([-5.9, 15.6, -3.4], [11.8, 2.2, 6.8], "plate"),
        cube([-4.2, 14.5, -2.5], [8.4, 3, 5], "mail"),
        cube([-5.8, 26, 2.5], [11.6, 13, 1.4], "plate2"),            # the back plate
    ]
    body += sym([cube([0.2, 33.6, -4.6], [5.9, 4.6, 0.6], "plate", rot=[0, 10, 0], pivot=[0.2, 35.9, -4.3])])   # angled pectoral plates: a curved chest
    for i in range(3):   # layered bands over the stomach
        body.append(cube([-4.3, 22.6 + i * 1.35, -3.2 - 0.1 * i], [8.6, 1.15, 0.5], "plate"))
    for i in range(4):   # ribs across the chest, sloping down from the breastbone
        y = 28.6 + i * 1.25
        body += sym([cube([0.4, y, -4.3], [4.6 - 0.4 * i, 0.6, 0.6], "plate", rot=[0, 0, -14], pivot=[0.4, y + 0.3, -4.0])])
    for i in range(5):   # spikes down the spine
        body.append(cube([-0.45, 27.5 + i * 2.4, 3.6], [0.9, 1.8, 1.6 - 0.1 * i], "plate", rot=[35, 0, 0], pivot=[0, 28 + i * 2.4, 3.8]))
    body += sym([cube([0.4, 10.6, -3.6], [4.8, 5.6, 0.8], "plate2", rot=[-8, 0, 0], pivot=[2.8, 16, -3.2]),   # long front tassets
                 cube([5.5, 12.2, -2.6], [1.4, 5, 5.2], "plate2", rot=[0, 0, -8], pivot=[5.6, 17, 0])])        # and side plates
    for i in range(4):   # chains hanging off the right hip
        body.append(cube([-6.3, 19.2 - i * 1.3, -0.4 + (0.2 if i % 2 else 0)], [0.5 if i % 2 == 0 else 0.9, 1.3, 0.9 if i % 2 == 0 else 0.5], "mail"))
    B.append(bone("body", "root", [0, 20, 0], body))
    head = [   # a sculpted helm: a stepped dome, a jutting brow, flared cheeks, a pointed jaw, a bladed crest
        cube([-2.8, 41, -3.0], [5.6, 6, 6], "plate"),                # the skull of the helm
        cube([-2.5, 47, -2.7], [5, 1.2, 5.4], "plate"),              # the dome, stepping in toward the top
        cube([-1.9, 48.2, -2.1], [3.8, 0.8, 4.2], "plate2"),
        cube([-3.2, 45.6, -3.9], [6.4, 1.0, 1.4], "plate2", rot=[8, 0, 0], pivot=[0, 46, -3.2]),   # the brow, jutting forward
        cube([-0.5, 45.0, -4.2], [1.0, 1.6, 0.9], "plate", rot=[8, 0, 0], pivot=[0, 46, -3.2]),     # a point in the middle of it
        cube([-2.9, 43.3, -3.85], [5.8, 2.2, 0.8], "eyes"),           # the eye slits, deep under the brow
        cube([-3.6, 39.5, -2.6], [7.2, 1.5, 5.2], "plate2"),         # the gorget
        cube([-3.2, 39.9, 2.7], [6.4, 1.4, 1.2], "plate2"),          # a stepped neck guard at the back
        cube([-3.4, 38.6, 3.2], [6.8, 1.3, 1.0], "plate", rot=[22, 0, 0], pivot=[0, 39.3, 3.2]),
    ]
    head += sym([
        cube([2.2, 40.4, -3.6], [1.0, 4.8, 4.6], "plate2", rot=[0, -12, -6], pivot=[2.7, 43, -1.3]),   # cheek plates, flaring out
        cube([0, 39.6, -4.0], [2.6, 1.8, 0.8], "plate", rot=[0, 0, -26], pivot=[0, 40.5, -3.6]),       # a pointed V jaw
    ])
    for i, x in enumerate((-1.6, -0.55, 0.5, 1.55)):   # fangs under the jaw
        head.append(cube([x - 0.28, 38.6 - 0.35 * (i % 2 == 0), -4.0], [0.56, 1.3 + 0.35 * (i % 2 == 0), 0.5], "plate"))
    head += sym([cube([2.95, 44.2, -3.0], [0.3, 0.4, 5.0], "glow")])   # the eye-fire trailing back along the helm
    for k in range(9):   # a bladed crest: a fin rising smoothly from the brow to a point at the back
        z = -2.6 + k * 0.85
        hgt = 0.6 + 2.8 * math.sin(min(1.0, (k + 1) / 8) * math.pi * 0.62)
        head.append(cube([-0.32, 48.8, z], [0.64, hgt, 0.85], "plate2"))
        head.append(cube([-0.12, 48.8 + hgt, z], [0.24, 0.25, 0.85], "glow"))
    horn = []   # two great horns sweeping back and up from the temples: joined, stepped segments, each thinner
    for k, (x, y, z, t, L) in enumerate(((2.9, 45.4, -1.4, 1.8, 3.4), (3.2, 46.5, 1.4, 1.5, 3.0), (3.5, 47.5, 3.8, 1.2, 2.6),
                                         (3.8, 48.4, 5.8, 0.9, 2.2), (4.0, 49.1, 7.4, 0.6, 1.8))):
        horn.append(cube([x, y, z], [t, t, L], "plate2"))
    head += sym(horn)
    B.append(bone("head", "body", [0, 40, 0], head))
    pauldron = [
        cube([5.6, 36, -4.2], [6.2, 4.8, 8.4], "plate"),
        cube([5.9, 34.6, -4.0], [5.8, 1.6, 8], "plate2", rot=[0, 0, -14], pivot=[5.9, 35.4, 0]),
        cube([6.2, 33.0, -3.8], [5.4, 1.6, 7.6], "plate2", rot=[0, 0, -20], pivot=[6.2, 33.8, 0]),
        cube([11.6, 40.3, -4.3], [0.4, 0.5, 8.6], "glow"),
        cube([11.75, 36.6, -1.3], [0.3, 2.6, 2.6], "rune"),          # a rune disc on the side of the pauldron
        cube([5.4, 40.8, -3.9], [6.6, 0.8, 7.8], "plate2")]
    for i in range(3):   # tapered spikes rising off the pauldron, leaning outward
        L = 2.6 + i * 0.8
        x, z = 7.4 + i * 1.4, -2.6 + i * 2.2
        pauldron.append(cube([x, 40.4, z], [1.4, L, 1.4], "plate", rot=[0, 0, -22], pivot=[x + 0.7, 40.6, z + 0.7]))
        pauldron.append(cube([x + 0.35, 40.4 + L, z + 0.35], [0.7, L * 0.8, 0.7], "plate2", rot=[0, 0, -22], pivot=[x + 0.7, 40.6, z + 0.7]))
    arm = [
        cube([6.4, 27, -1.8], [3.6, 7, 3.6], "plate2"),
        cube([6.1, 25.4, -2.3], [4.2, 2.4, 4.6], "plate"),           # the elbow cop, with a spike out the back
        cube([7.8, 25.8, 2.1], [0.8, 0.8, 2.4], "plate", rot=[30, 0, 0], pivot=[8.2, 26.2, 2.2]),
        cube([6.2, 19.5, -2.0], [4, 6, 4], "plate2"),                # the vambrace, a flared cuff
        cube([6.0, 23.6, -2.4], [4.4, 1.6, 4.8], "plate"),
        cube([6.1, 21.5, -2.05], [4.1, 0.5, 4.1], "glow"),
        cube([6.2, 17, -2.2], [4, 2.6, 4.4], "plate"),               # the gauntlet
        cube([6.1, 17.4, -2.6], [4.2, 1.0, 0.6], "plate2"),          # knuckle plates
    ]
    for i in range(4):   # clawed fingers
        arm.append(cube([6.3 + 0.95 * i, 14.2, -2.0], [0.8, 3, 0.8], "plate"))
        arm.append(cube([6.4 + 0.95 * i, 13.0, -2.3], [0.6, 1.4, 0.6], "plate2", rot=[-25, 0, 0], pivot=[6.7 + 0.95 * i, 14.2, -1.6]))
    B.append(bone("arm_r", "body", [-7.6, 37, 0], mirror_x(arm + pauldron), rotation=[-30, 10, 0]))
    B.append(bone("arm_l", "body", [7.6, 37, 0], arm + pauldron, rotation=[-30, -40, 20]))
    sword = [   # a long black grip and a round pommel
        cube([-8.9, 8.8, -0.8], [1.6, 9, 1.6], "plate2"),
        cube([-9.3, 7.4, -1.0], [2.4, 1.6, 2.0], "plate"),
        cube([-11.0, 17.6, -1.2], [5.8, 1.6, 2.4], "plate"),          # a black guard...
        cube([-10.4, 19.2, -1.0], [4.6, 0.8, 2.0], "plate2"),
    ]
    for s_ in (-1, 1):   # ...with two horns curving up toward the blade
        x = -8.1 + s_ * 2.6
        sword.append(cube([x - 0.7, 18.0, -1.0], [1.4, 1.6, 2.0], "plate"))
        sword.append(cube([x - 0.55 + s_ * 0.6, 19.4, -0.9], [1.1, 1.4, 1.8], "plate"))
        sword.append(cube([x - 0.4 + s_ * 1.0, 20.6, -0.8], [0.8, 1.2, 1.6], "plate2"))
        sword.append(cube([x - 0.25 + s_ * 1.2, 21.6, -0.7], [0.5, 1.0, 1.4], "plate2"))
    y = 20.0
    for wdt, L in ((3.4, 8), (3.1, 7), (2.7, 6), (2.2, 4), (1.6, 2.6), (1.0, 2), (0.5, 1.6)):   # the blue blade, tapering to a long point
        sword.append(cube([-8.1 - wdt / 2, y, -0.45], [wdt, L, 0.9], "flameblade"))
        y += L
    B.append(bone("sword", "arm_r", [-8.2, 16.8, 0], sword, rotation=[90, 0, 30]))
    leg = [
        cube([0.6, 12, -2.2], [4, 5, 4.4], "mail"),
        cube([0.5, 12.6, -2.75], [4.2, 4.2, 0.6], "plate"),          # a plate over the thigh
        cube([0.4, 10, -2.7], [4.4, 2.6, 5], "plate"),               # the knee, a spike forward
        cube([2.1, 10.6, -4.6], [1, 1, 2.2], "plate", rot=[-20, 0, 0], pivot=[2.6, 11.1, -2.7]),
        cube([0.6, 3, -2.2], [4, 7, 4.4], "plate2"),                 # the greave, a ridge down the shin
        cube([2.2, 3.5, -2.65], [0.8, 6, 0.6], "plate"),
        cube([0.55, 7.5, -2.25], [4.1, 0.5, 4.5], "glow"),
        cube([0.4, 0, -3.0], [4.4, 3, 6], "plate"),                  # a pointed sabaton with a claw
        cube([1.2, 0.2, -4.6], [2.8, 1.6, 1.8], "plate2"),
        cube([2.1, 0.1, -5.5], [1, 1, 1.1], "plate"),
    ]
    B.append(bone("leg_l", "body", [2.8, 17, 0], leg, rotation=[0, -10, 0]))
    B.append(bone("leg_r", "body", [-2.8, 17, 0], mirror_x(leg), rotation=[0, 10, 0]))
    cape = [cube([-6.6, 38, 2.6], [13.2, 2.2, 1.6], "plate2")]   # the mantle at the shoulders
    for i, x in enumerate((-6.2, -4.4, -2.6, -0.8, 1.0, 2.8, 4.6)):   # a long ragged cape hanging in folds, flaring
        L = [31, 37, 34, 38, 33, 36, 30][i]
        z = 3.3 + 0.55 * (1 + math.sin(i * 1.9)) + 0.2 * abs(i - 3)   # each strip at its own depth: folds
        cape.append(cube([x, 39 - L, z], [1.9, L, 0.4], "cape", rot=[0, 0, (i - 3) * 1.6], pivot=[x + 0.9, 39, z]))
    B.append(bone("cape", "body", [0, 39, 3.2], cape, rotation=[6, 0, 0]))
    smoke = []   # wisps of shadow rising round the feet
    for k in range(9):
        ang = k * 2 * math.pi / 9 + 0.3
        r = 4.6 + 1.2 * math.sin(k * 2.3)
        hgt = 8 + 6 * abs(math.sin(k * 1.7))
        x, z = math.cos(ang) * r, math.sin(ang) * r
        smoke.append(cube([x - 1.0, 0, z - 0.5], [2.0 + 0.8 * (k % 2), hgt, 0.15], "smoke", rot=[(z / r) * 12, math.degrees(-ang), -(x / r) * 12], pivot=[x, 0, z]))
    B.append(bone("smoke", "root", [0, 0, 0], smoke))
    return geo("shadow_knight", B)


# ---------------------------------------------------------------------------------------------------- animations
def kf(d): return {f"{t:.4f}": v for t, v in sorted(d.items())}


def knight_anims():
    A = {}
    A["rise"] = {"animation_length": 1.8, "bones": {   # climbs up out of a pool of shadow, then takes its guard
        "root": {"position": kf({0: [0, -46, 0], 1.1: [0, -4, 0], 1.4: [0, 0, 0]})},
        "head": {"rotation": kf({0: [40, 0, 0], 1.2: [30, 0, 0], 1.6: [0, 0, 0]})},
        "body": {"rotation": kf({0: [25, 0, 0], 1.2: [15, 0, 0], 1.6: [0, 0, 0]})},
        "cape": {"rotation": kf({0: [40, 0, 0], 1.4: [30, 0, 0], 1.8: [0, 0, 0]})}}}
    A["idle"] = {"loop": True, "animation_length": 3.0, "bones": {
        "body": {"position": kf({0: [0, 0, 0], 1.5: [0, -0.3, 0], 3.0: [0, 0, 0]})},
        "cape": {"rotation": kf({0: [0, 0, 0], 1.5: [4, 0, 1.5], 3.0: [0, 0, 0]})},
        "head": {"rotation": kf({0: [0, 0, 0], 1.0: [0, 6, 0], 2.0: [0, -6, 0], 3.0: [0, 0, 0]})},
        "sword": {"rotation": kf({0: [0, 0, 0], 1.5: [-3, 0, 0], 3.0: [0, 0, 0]})},
        "smoke": {"rotation": kf({0: [0, 0, 0], 1.5: [0, 25, 0], 3.0: [0, 50, 0]}), "scale": kf({0: [1, 1, 1], 1.5: [1.05, 1.15, 1.05], 3.0: [1, 1, 1]})}}}
    A["walk"] = {"loop": True, "animation_length": 1.0, "bones": {
        "leg_l": {"rotation": kf({0: [25, 0, 0], 0.5: [-25, 0, 0], 1.0: [25, 0, 0]})},
        "leg_r": {"rotation": kf({0: [-25, 0, 0], 0.5: [25, 0, 0], 1.0: [-25, 0, 0]})},
        "cape": {"rotation": kf({0: [14, 0, 0], 0.5: [20, 0, 0], 1.0: [14, 0, 0]})},
        "body": {"position": kf({0: [0, 0, 0], 0.25: [0, 0.6, 0], 0.5: [0, 0, 0], 0.75: [0, 0.6, 0], 1.0: [0, 0, 0]})}}}
    A["slash"] = {"animation_length": 0.8, "bones": {   # from the low guard: up over the shoulder, then a great diagonal cut
        "arm_r": {"rotation": kf({0: [0, 0, 0], 0.3: [-110, 30, 0], 0.45: [40, -30, 0], 0.8: [0, 0, 0]})},
        "arm_l": {"rotation": kf({0: [0, 0, 0], 0.3: [-100, 50, 0], 0.45: [40, -10, 0], 0.8: [0, 0, 0]})},
        "body": {"rotation": kf({0: [0, 0, 0], 0.3: [0, 25, 0], 0.45: [8, -30, 0], 0.8: [0, 0, 0]})},
        "cape": {"rotation": kf({0: [0, 0, 0], 0.45: [25, 0, 0], 0.8: [0, 0, 0]})}}}
    return {"format_version": "1.8.0", "animations": {f"animation.shadow_knight.{k}": v for k, v in A.items()}}


PAINTERS = {
    "plate": gp.plate_painter((34, 35, 46), (26, 70, 160), (50, 120, 230), ornate=True),
    "plate2": gp.plate_painter((20, 20, 28), (30, 90, 200), (90, 170, 255), engrave=False, ridges=True),
    "mail": gp.mail_painter((40, 44, 58)),
    "cape": gp.cloth_painter((24, 58, 150)),
    "visor": gp.visor_painter((14, 14, 20), BLUE, (200, 235, 255)),
    "eyes": gp.eyes_painter((12, 12, 18), BLUE, (210, 240, 255)),
    "blade": gp.blade_painter((34, 36, 48), BLUE, BRIGHT),
    "glow": gp.glow_painter(BLUE, BRIGHT),
    "rune": gp.rune_painter((16, 16, 24), (70, 160, 255), (150, 215, 255)),
    "smoke": gp.smoke_painter((6, 6, 10), (30, 70, 160), (60, 140, 255)),
    "flameblade": gp.flameblade_painter((70, 165, 255), (12, 14, 26), (110, 200, 255)),
    "grip": gp.grip_painter((12, 12, 16), (34, 58, 130)),
}


def build(name, model, density=8):
    bones = model["minecraft:geometry"][0]["bones"]
    size, faces = gp.layout(bones, density, sheet=2048)
    blade = [fc for fc in faces if fc["mat"] == "flameblade"]   # the blade's veins run continuously across its pieces
    if blade:
        lo = min(fc["cube"]["origin"][1] for fc in blade); hi = max(fc["cube"]["origin"][1] + fc["cube"]["size"][1] for fc in blade)
        for fc in blade:
            o, sz = fc["cube"]["origin"][1], fc["cube"]["size"][1]
            fc["t0"], fc["t1"] = (o - lo) / (hi - lo), (o + sz - lo) / (hi - lo)
    img, glow = gp.paint(faces, size, PAINTERS)
    gp.clean(bones)
    d = model["minecraft:geometry"][0]["description"]
    d["texture_width"] = d["texture_height"] = size
    json.dump(model, open(os.path.join(GEO, name + ".geo.json"), "w"), indent=1)
    img.save(os.path.join(TEX, name + ".png")); glow.save(os.path.join(TEX, name + "_glowmask.png"))


def main():
    for d in (GEO, ANIM, TEX): os.makedirs(d, exist_ok=True)
    build("shadow_knight", knight())
    json.dump(knight_anims(), open(os.path.join(ANIM, "shadow_knight.animation.json"), "w"), indent=1)
    print("shadow models written")


if __name__ == "__main__":
    main()
