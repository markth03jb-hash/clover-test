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
    cape; and a glowing blue greatsword - a wide blade with dark smoky veins winding up it, a heavy black guard and a long two-hand grip."""
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
    head = [   # a sculpted, menacing helm: a stepped dome, a heavy brow over sunken burning eyes, a jutting fanged jaw
        cube([-2.8, 41, -3.0], [5.6, 6, 6], "helm"),                 # the skull of the helm
        cube([-2.5, 47, -2.7], [5, 1.2, 5.4], "helm"),               # the dome, stepping in toward the top
        cube([-1.9, 48.2, -2.1], [3.8, 0.8, 4.2], "plate2"),
        cube([-3.3, 45.5, -4.0], [6.6, 1.2, 1.5], "plate2", rot=[10, 0, 0], pivot=[0, 46, -3.2]),     # the brow, heavy and jutting
        cube([-0.5, 44.8, -4.35], [1.0, 1.9, 1.0], "plate", rot=[10, 0, 0], pivot=[0, 46, -3.2]),     # a point dropping between the eyes
        cube([-3.0, 42.9, -3.5], [6.0, 2.9, 0.5], "plate3"),         # a dark socket plate, the eyes sunk into it
        cube([-2.9, 43.3, -3.85], [5.8, 2.2, 0.8], "eyes"),          # the eye slits, burning
        cube([-3.6, 39.5, -2.6], [7.2, 1.5, 5.2], "plate2"),         # the gorget
        cube([-3.2, 39.9, 2.7], [6.4, 1.4, 1.2], "plate2"),          # a stepped neck guard at the back
        cube([-3.4, 38.6, 3.2], [6.8, 1.3, 1.0], "plate", rot=[22, 0, 0], pivot=[0, 39.3, 3.2]),
        cube([-2.4, 38.1, -4.4], [4.8, 1.4, 1.6], "plate", rot=[-12, 0, 0], pivot=[0, 39.2, -3.6]),  # a heavy lower jaw, jutting out
    ]
    head += sym([
        cube([2.2, 40.4, -3.6], [1.0, 4.8, 4.6], "plate2", rot=[0, -12, -6], pivot=[2.7, 43, -1.3]),   # cheek plates, flaring out
        cube([0, 39.6, -4.0], [2.6, 1.8, 0.8], "plate", rot=[0, 0, -26], pivot=[0, 40.5, -3.6]),       # a pointed V jaw
        cube([1.15, 40.2, -3.95], [0.25, 3.1, 0.2], "glow"),        # glowing streaks running down from the eyes
        cube([2.85, 41.6, -2.2], [0.2, 0.25, 2.6], "glow", rot=[0, 0, 30], pivot=[2.95, 41.7, -0.9]),   # glowing cracks across the helm
        cube([2.85, 42.6, -0.4], [0.2, 0.25, 2.0], "glow", rot=[0, 0, -25], pivot=[2.95, 42.7, 0.6]),
    ])
    for i, x in enumerate((-1.75, -1.05, -0.35, 0.35, 1.05, 1.75)):   # long fangs under the jaw
        L = 1.7 + 0.6 * (i in (1, 4)) + 0.3 * (i in (0, 5))
        head.append(cube([x - 0.24, 38.2 - L, -4.55 + 0.12 * abs(x)], [0.48, L, 0.45], "plate"))
    head += sym([cube([2.95, 44.2, -3.0], [0.3, 0.4, 5.0], "glow")])   # the eye-fire trailing back along the helm
    halo = []   # (only the smoke now)
    for k in range(4):   # shadow smoke rising off the top of the helm
        x = -1.8 + k * 1.2
        halo.append(cube([x - 0.6, 48.9, -1.5 + 0.8 * k], [1.2, 3.5 + 1.5 * (k % 2), 0.15], "smoke", rot=[0, 35 * k, 0], pivot=[x, 49, -1.2 + 0.8 * k]))
    head += halo
    horn = []   # two great horns: back from the temples, rising, then hooking forward at the tips
    for k, (x, y, z, t, L) in enumerate(((2.9, 45.0, -1.6, 2.2, 3.6), (3.3, 46.1, 1.3, 1.9, 3.0), (3.7, 47.6, 3.4, 1.6, 2.4),
                                         (4.0, 49.4, 4.6, 1.35, 1.8), (4.2, 51.2, 4.9, 1.1, 1.4), (4.35, 52.8, 4.4, 0.85, 1.3),
                                         (4.45, 54.0, 3.4, 0.6, 1.5), (4.5, 54.7, 2.2, 0.4, 1.4))):
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
    sword = [   # a greatsword: a long two-hand grip, a heavy pommel, a broad guard, a wide long blade
        cube([-9.0, 6.0, -0.9], [1.8, 11.5, 1.8], "plate2"),          # the grip, long enough for both hands
        cube([-9.6, 4.2, -1.2], [3.0, 2.0, 2.4], "plate"),            # a heavy pommel
        cube([-8.6, 3.0, -0.6], [1.0, 1.4, 1.2], "plate2"),
        cube([-13.2, 17.3, -1.4], [10.2, 2.0, 2.8], "plate"),         # a broad, heavy guard...
        cube([-11.4, 19.3, -1.15], [6.6, 1.0, 2.3], "plate2"),
    ]
    for s_ in (-1, 1):   # ...its ends curving up toward the blade
        x = -8.1 + s_ * 4.6
        sword.append(cube([x - 0.8, 18.0, -1.2], [1.6, 2.0, 2.4], "plate"))
        sword.append(cube([x - 0.6 + s_ * 0.5, 20.0, -1.0], [1.2, 1.6, 2.0], "plate"))
        sword.append(cube([x - 0.4 + s_ * 0.8, 21.6, -0.9], [0.8, 1.3, 1.8], "plate2"))
    y = 20.2
    for wdt, L in ((5.6, 10), (5.3, 9), (4.9, 8), (4.3, 5), (3.4, 3), (2.4, 2.2), (1.4, 1.8), (0.6, 1.4)):   # a wide blade, tapering late to the point
        sword.append(cube([-8.1 - wdt / 2, y, -0.55], [wdt, L, 1.1], "flameblade"))
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


def ravener():
    """The Ravener (an original design): a tall, upright stalker about 2.8 blocks high. Broad, high, plated shoulders
    over a narrow waist, on long backward-bending legs. Its upper arms carry great curved bone scythes, its lower arms
    small grasping claws. A low-slung, eyeless, crested skull hangs forward with a split jaw that burns blue inside. A
    ridge of long spines sweeps off its back and a slim tail ends in a crescent barb. Black, with blue light only in
    the seams."""
    def sym(cubes): return cubes + mirror_x(cubes)
    def lift(cubes, dy, dz):   # move a group of cubes (and their pivots) up and back
        for c in cubes:
            c["origin"] = [c["origin"][0], round(c["origin"][1] + dy, 3), round(c["origin"][2] + dz, 3)]
            if "pivot" in c: c["pivot"] = [c["pivot"][0], round(c["pivot"][1] + dy, 3), round(c["pivot"][2] + dz, 3)]
        return cubes
    HY, HZ = 4.0, 6.5   # the head sits up on the shoulders, not out in front
    B = [bone("root", None, [0, 0, 0], [])]
    body = [
        cube([-3.2, 19.5, -1.5], [6.4, 4, 5], "plate2"),                       # pelvis
        cube([-2.3, 22.8, -1.2], [4.6, 4.2, 3.9], "mail"),                     # a narrow waist
        cube([-4.2, 26.5, -2.4], [8.4, 4.5, 5.4], "plate2"),                   # ribcage
        cube([-5.8, 30.5, -3.0], [11.6, 5.0, 6.4], "plate"),                   # a broad upper chest
        cube([-2.2, 31.2, -3.6], [4.4, 3.4, 0.7], "plate3"),                   # sternum plate
    ]
    body += sym([   # great pauldrons sloping off the shoulders, each with two swept-back horns
        cube([5.2, 33.2, -3.8], [4.4, 3.2, 7.4], "plate", rot=[0, 0, -18], pivot=[5.2, 34.8, 0]),
        cube([5.6, 32.2, -4.0], [3.6, 1.0, 7.8], "plate3", rot=[0, 0, -28], pivot=[5.6, 33.2, 0]),
        cube([7.2, 35.5, -1.4], [1.2, 6.5, 1.3], "bone", rot=[-40, 0, -22], pivot=[7.8, 35.6, -0.8]),
        cube([6.0, 35.8, 0.6], [1.0, 4.5, 1.1], "bone", rot=[-55, 0, -12], pivot=[6.5, 35.9, 1.1]),
    ])
    for n in range(9):   # shadow smoke boiling up off its shoulders and back, swept behind it
        x = -5 + n * 1.25
        h = 7 + 5 * abs(math.sin(n * 1.9)) + (3 if 2 < n < 6 else 0)
        z = 1.5 + 1.2 * math.sin(n * 2.7)
        for yaw in (25, -25):
            body.append(cube([x - 1.1, 33.5 - abs(x) * 0.35, z], [2.2 + 0.6 * (n % 2), h, 0.12], "smoke",
                             rot=[-28, yaw + 10 * math.sin(n), -x * 2.5], pivot=[x, 33.5 - abs(x) * 0.35, z]))
    B.append(bone("body", "root", [0, 21, 0.5], body, rotation=[12, 0, 0]))
    B.append(bone("neck", "body", [0, 33.5, -2.5], [
        cube([-1.7, 32.5, -4.2], [3.4, 5.0, 3.4], "mail"),                       # a short, thick neck, straight up
    ]))
    head = [
        cube([-2.4, 32.0, -12.0], [4.8, 3.8, 6.4], "skull"),                   # the skull, eyeless
        cube([-2.0, 32.0, -15.0], [4.0, 2.3, 3.2], "skull"),                   # a short, wedge-like snout
        cube([-2.7, 35.3, -12.4], [5.4, 1.0, 4.0], "plate3"),                  # a heavy brow ridge
    ]
    head += sym([   # two burning eyes under the brow
        cube([0.6, 34.4, -12.3], [1.8, 0.85, 0.4], "eyes", rot=[0, 0, -14], pivot=[0.6, 34.8, -12]),
    ])
    head += sym([   # two long horns sweeping straight back off the brow, and two shorter below them
        cube([1.6, 35.7, -9.0], [1.1, 1.1, 10.5], "bone", rot=[14, -14, 0], pivot=[2.1, 36.2, -8.5]),
        cube([1.95, 35.9, 0.4], [0.5, 0.5, 2.5], "bone", rot=[30, -14, 0], pivot=[2.1, 36.2, -8.5]),
        cube([2.4, 34.0, -8.0], [0.8, 0.8, 6.5], "bone", rot=[6, -26, 0], pivot=[2.8, 34.4, -7.5]),
    ])
    head += sym([cube([1.3, 31.0, -14.6], [0.4, 1.4, 0.4], "teeth"), cube([1.1, 31.2, -12.6], [0.35, 1.0, 0.35], "teeth")])   # two fangs a side
    B.append(bone("head", "neck", [0, 33.5 + HY, -6.5 + HZ], lift(head, HY, HZ), rotation=[4, 0, 0]))
    jaw = [cube([-2.3, 30.4, -14.4], [4.6, 1.4, 7.8], "skull")]   # a heavy, closed jaw
    B.append(bone("jaw", "head", [0, 31.4 + HY, -7.0 + HZ], lift(jaw, HY, HZ), rotation=[2, 0, 0]))
    upper = [   # long, powerful arms: a plated shoulder-arm, a spiked elbow, a heavy forearm and a big taloned hand
        cube([5.3, 25.5, -2.0], [3.0, 7.5, 3.2], "plate2"),
        cube([5.1, 24.2, -2.4], [3.4, 1.8, 3.9], "plate3"),                                               # elbow guard
        cube([6.2, 24.6, 1.2], [1.0, 1.0, 3.2], "bone", rot=[30, 0, 0], pivot=[6.8, 25.1, 1.4]),          # a spike off the elbow
        cube([5.4, 17.6, -2.0], [2.8, 7.4, 3.0], "plate2", rot=[-24, 0, 0], pivot=[6.8, 25, -0.5]),
        cube([5.25, 19.0, -2.25], [3.1, 2.4, 3.5], "plate3", rot=[-24, 0, 0], pivot=[6.8, 25, -0.5]),      # bracer at the wrist
        cube([5.6, 15.4, -4.6], [2.4, 2.8, 1.6], "plate2", rot=[-30, 0, 0], pivot=[6.8, 18.2, -3.6]),     # a narrow, bony hand
    ]
    def claw(x, y, z, segs, spread):   # a long skinny finger, bending at each knuckle so it curls into a hooked claw
        out = []
        for n, (L, ang, t) in enumerate(segs):
            mat = "skull" if n < len(segs) - 1 else "bone"
            out.append(cube([x - t / 2, y - L, z - t / 2], [t, L + 0.15, t], mat, rot=[ang, 0, spread], pivot=[x, y, z]))
            a = math.radians(ang)
            y, z = y - L * math.cos(a), z + L * math.sin(a)
        return out
    for f, (x, sp) in enumerate(((5.85, 10), (6.8, 0), (7.75, -10))):   # three long fingers
        k = (1.0, 1.15, 1.0)[f]
        upper += claw(x, 15.0, -4.4, [(2.8 * k, -18, 0.55), (2.6 * k, -40, 0.45), (2.4 * k, -72, 0.34), (1.4 * k, -105, 0.22)], sp)
    upper += claw(5.3, 16.2, -4.2, [(2.0, -10, 0.5), (1.8, -40, 0.4), (1.3, -80, 0.26)], 30)   # and a hooked thumb
    for q in range(3):
        upper.append(cube([6.0 + q * 0.5, 19 + q * 2, 0.6], [1.6, 6 + q, 0.12], "smoke", rot=[-70 + q * 8, 20 * (q - 1), 0], pivot=[6.6 + q * 0.5, 19 + q * 2, 0.6]))
    B.append(bone("arm_ul", "body", [6.8, 32.5, -0.3], upper, rotation=[-10, 0, 8]))
    B.append(bone("arm_ur", "body", [-6.8, 32.5, -0.3], mirror_x(upper), rotation=[-10, 0, -8]))
    lower = [   # the lower arms: thin, tucked in under the chest, with grasping claws
        cube([3.6, 23.0, -2.0], [1.6, 4.6, 1.8], "plate2"),
        cube([3.7, 18.8, -2.1], [1.4, 4.4, 1.6], "bone", rot=[-30, 0, 0], pivot=[4.4, 23, -1.2]),
    ]
    for f in range(3):
        lower.append(cube([3.55 + f * 0.5, 16.8, -4.6], [0.4, 2.4, 0.4], "bone", rot=[-60, 0, (f - 1) * 12], pivot=[3.75 + f * 0.5, 18.6, -3.4]))
    B.append(bone("arm_ll", "body", [4.0, 27, -0.8], lower, rotation=[-35, 0, -6]))
    B.append(bone("arm_lr", "body", [-4.0, 27, -0.8], mirror_x(lower), rotation=[-35, 0, 6]))
    leg = [   # long, lean, backward-bending legs
        cube([1.7, 12.8, -1.7], [3.3, 8.6, 3.6], "plate2", rot=[-28, 0, 0], pivot=[3.3, 21, 0]),
        cube([1.5, 14.2, -5.4], [3.7, 2.4, 2.0], "plate3"),                                       # knee guard
        cube([2.1, 14.4, -6.0], [2.5, 1.0, 1.4], "bone", rot=[-30, 0, 0], pivot=[3.3, 14.9, -5.4]),  # knee spike
        cube([2.2, 4.0, -5.0], [2.2, 10.5, 2.2], "plate2", rot=[34, 0, 0], pivot=[3.3, 14.4, -4.0]),
        cube([2.4, 0.8, 0.4], [1.8, 5.2, 1.8], "bone", rot=[-8, 0, 0], pivot=[3.3, 5.6, 1.4]),
        cube([1.9, 0, -3.6], [2.8, 1.2, 4.6], "plate2"),
    ]
    for x in (1.9, 2.95, 4.0):   # three long toe claws and a hooked dewclaw
        leg.append(cube([x, 0, -5.8], [0.75, 0.9, 2.4], "bone"))
        leg.append(cube([x + 0.15, 0, -6.8], [0.45, 0.6, 1.1], "teeth"))
    leg.append(cube([3.0, 1.0, 1.8], [0.6, 0.6, 2.0], "bone", rot=[35, 0, 0], pivot=[3.3, 1.3, 1.9]))
    B.append(bone("leg_l", "root", [3.3, 21, 0], leg))
    B.append(bone("leg_r", "root", [-3.3, 21, 0], mirror_x(leg)))
    prev, y0, z0 = "body", 21.5, 3.4
    for i in range(6):   # a slim, rising tail
        t = 2.4 - i * 0.28
        L = 3.4
        cubes = [cube([-t / 2, y0 - t / 2, z0], [t, t, L], "plate2"),
]
        if i >= 3: cubes.append(cube([-1.0, y0, z0 + 1.0], [2.0, 3 + i, 0.12], "smoke", rot=[-55, 0, 0], pivot=[0, y0, z0 + 1.0]))
        B.append(bone(f"tail{i}", prev, [0, y0, z0], cubes, rotation=[(-6, -3, 0, 5, 7, 9)[i], 0, 0]))
        prev, z0 = f"tail{i}", z0 + L
    B.append(bone("barb", prev, [0, y0, z0], [   # a crescent barb
        cube([-0.4, y0 - 0.5, z0], [0.8, 1.0, 2.0], "bone"),
        cube([-0.3, y0 - 0.3, z0 + 1.2], [0.6, 4.2, 1.4], "bone", rot=[-35, 0, 0], pivot=[0, y0, z0 + 1.6]),
        cube([-0.3, y0 - 3.4, z0 + 1.2], [0.6, 3.2, 1.2], "bone", rot=[35, 0, 0], pivot=[0, y0, z0 + 1.6]),
    ]))
    smoke = []
    for k in range(10):   # low wisps of shadow round the feet
        ang = k * 2 * math.pi / 10 + 0.3
        r = 4.5 + 1.2 * math.sin(k * 2.3)
        x, z = math.cos(ang) * r, math.sin(ang) * r
        smoke.append(cube([x - 1.2, 0, z], [2.4, 3 + 4 * abs(math.sin(k * 1.7)), 0.12], "smoke", rot=[0, math.degrees(-ang), 0], pivot=[x, 0, z]))
    B.append(bone("smoke", "root", [0, 0, 0], smoke))
    return geo("shadow_ravener", B)


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


def ravener_anims():
    A = {}
    tail = lambda amp, per: {f"tail{i}": {"rotation": kf({0: [0, amp * (i + 1) / 3, 0], per / 2: [0, -amp * (i + 1) / 3, 0], per: [0, amp * (i + 1) / 3, 0]})} for i in range(6)}
    A["rise"] = {"animation_length": 1.8, "bones": {   # crouched low under its pool, then it uncoils, jaw wide in a shriek
        "root": {"position": kf({0: [0, -30, 0], 1.0: [0, -3, 0], 1.25: [0, 0, 0]})},
        "body": {"rotation": kf({0: [35, 0, 0], 1.0: [25, 0, 0], 1.35: [-12, 0, 0], 1.8: [0, 0, 0]})},
        "neck": {"rotation": kf({0: [40, 0, 0], 1.2: [10, 0, 0], 1.4: [-30, 0, 0], 1.8: [0, 0, 0]})},
        "jaw": {"rotation": kf({0: [0, 0, 0], 1.3: [0, 0, 0], 1.45: [35, 0, 0], 1.7: [0, 0, 0]})},
        "arm_ul": {"rotation": kf({0: [40, 0, 0], 1.3: [0, 0, 0], 1.45: [-60, 0, -25], 1.8: [0, 0, 0]})},
        "arm_ur": {"rotation": kf({0: [40, 0, 0], 1.3: [0, 0, 0], 1.45: [-60, 0, 25], 1.8: [0, 0, 0]})}}}
    idle = {   # stands tall and still: back straight, shoulders high, long arms hanging loose at its sides, breathing slowly
            "body": {"position": kf({0: [0, 0, 0], 1.5: [0, -0.3, 0], 3.0: [0, 0, 0]}),
                     "rotation": kf({0: [-11, 0, 0], 1.5: [-9.5, 0, 0], 3.0: [-11, 0, 0]})},
            "neck": {"rotation": kf({0: [0, 0, 0], 1.1: [0, 0, 0], 1.2: [2, 9, 0], 1.9: [2, 9, 0], 2.0: [0, 0, 0], 3.0: [0, 0, 0]})},   # a slow look aside
            "head": {"rotation": kf({0: [-4, 0, 0], 1.5: [-3, 0, 0], 3.0: [-4, 0, 0]})},
            "arm_ul": {"rotation": kf({0: [9, 0, 4], 1.5: [8, 0, 6], 3.0: [9, 0, 4]})},
            "arm_ur": {"rotation": kf({0: [9, 0, -4], 1.5: [8, 0, -6], 3.0: [9, 0, -4]})},
            "arm_ll": {"rotation": kf({0: [10, 0, 0], 3.0: [10, 0, 0]})},
            "arm_lr": {"rotation": kf({0: [10, 0, 0], 3.0: [10, 0, 0]})},
            "smoke": {"rotation": kf({0: [0, 0, 0], 3.0: [0, 60, 0]})}}
    idle.update(tail(5, 3.0))
    A["idle"] = {"loop": True, "animation_length": 3.0, "bones": idle}
    walk = {   # a stalking gait: shoulders roll against the stride, long arms swing, the head stays level and locked on
        "leg_l": {"rotation": kf({0: [26, 0, 0], 0.35: [-26, 0, 0], 0.7: [26, 0, 0]})},
        "leg_r": {"rotation": kf({0: [-26, 0, 0], 0.35: [26, 0, 0], 0.7: [-26, 0, 0]})},
        "body": {"position": kf({0: [0, 0, 0], 0.175: [0, 0.9, 0], 0.35: [0, 0, 0], 0.525: [0, 0.9, 0], 0.7: [0, 0, 0]}),
                 "rotation": kf({0: [7, -7, 3], 0.35: [7, 7, -3], 0.7: [7, -7, 3]})},
        "neck": {"rotation": kf({0: [-5, 7, -3], 0.35: [-5, -7, 3], 0.7: [-5, 7, -3]})},
        "arm_ul": {"rotation": kf({0: [-22, 0, 2], 0.35: [18, 0, 5], 0.7: [-22, 0, 2]})},
        "arm_ur": {"rotation": kf({0: [18, 0, -5], 0.35: [-22, 0, -2], 0.7: [18, 0, -5]})},
        "arm_ll": {"rotation": kf({0: [-10, 0, 0], 0.35: [10, 0, 0], 0.7: [-10, 0, 0]})},
        "arm_lr": {"rotation": kf({0: [10, 0, 0], 0.35: [-10, 0, 0], 0.7: [10, 0, 0]})},
        "smoke": {"rotation": kf({0: [0, 0, 0], 0.7: [0, 30, 0]})}}
    walk.update(tail(12, 0.7))
    A["walk"] = {"loop": True, "animation_length": 0.7, "bones": walk}
    A["attack"] = {"animation_length": 0.75, "bones": {   # a lunge and two crossing rakes, twisting into each, jaws snapping
        "body": {"position": kf({0: [0, 0, 0], 0.12: [0, -0.6, 1.0], 0.28: [0, -1.2, -2.2], 0.45: [0, -1.0, -2.6], 0.75: [0, 0, 0]}),
                 "rotation": kf({0: [0, 0, 0], 0.12: [-6, -22, 0], 0.28: [16, 24, 0], 0.45: [16, -24, 0], 0.75: [0, 0, 0]})},
        "neck": {"rotation": kf({0: [0, 0, 0], 0.12: [-12, 10, 0], 0.3: [12, -12, 0], 0.45: [10, 12, 0], 0.75: [0, 0, 0]})},
        "jaw": {"rotation": kf({0: [0, 0, 0], 0.15: [32, 0, 0], 0.3: [0, 0, 0], 0.4: [28, 0, 0], 0.5: [0, 0, 0]})},
        "arm_ur": {"rotation": kf({0: [0, 0, 0], 0.12: [-150, 0, -35], 0.26: [-35, 0, 30], 0.42: [-15, 0, 10], 0.75: [0, 0, 0]})},
        "arm_ul": {"rotation": kf({0: [0, 0, 0], 0.12: [-25, 0, 10], 0.28: [-150, 0, 35], 0.44: [-35, 0, -30], 0.6: [-10, 0, -5], 0.75: [0, 0, 0]})},
        "arm_ll": {"rotation": kf({0: [0, 0, 0], 0.3: [-70, 0, 0], 0.45: [-20, 0, 0], 0.75: [0, 0, 0]})},
        "arm_lr": {"rotation": kf({0: [0, 0, 0], 0.35: [-70, 0, 0], 0.5: [-20, 0, 0], 0.75: [0, 0, 0]})},
        "leg_l": {"rotation": kf({0: [0, 0, 0], 0.28: [-22, 0, 0], 0.55: [-10, 0, 0], 0.75: [0, 0, 0]})},
        "leg_r": {"rotation": kf({0: [0, 0, 0], 0.28: [14, 0, 0], 0.55: [8, 0, 0], 0.75: [0, 0, 0]})},
        "tail2": {"rotation": kf({0: [0, 0, 0], 0.28: [0, -25, 0], 0.45: [0, 25, 0], 0.75: [0, 0, 0]})},
        "tail4": {"rotation": kf({0: [0, 0, 0], 0.32: [0, -30, 0], 0.5: [0, 30, 0], 0.75: [0, 0, 0]})}}}
    A["frenzy"] = {"animation_length": 1.2, "bones": {   # it rears back, head thrown up, arms flung wide, and shrieks
        "body": {"position": kf({0: [0, 0, 0], 0.2: [0, -1.5, 0], 0.45: [0, 0.8, 0], 1.0: [0, 0.5, 0], 1.2: [0, 0, 0]}),
                 "rotation": kf({0: [0, 0, 0], 0.2: [18, 0, 0], 0.45: [-16, 0, 0], 0.9: [-14, 0, 0], 1.2: [0, 0, 0]})},
        "neck": {"rotation": kf({0: [0, 0, 0], 0.2: [15, 0, 0], 0.45: [-38, 0, 0], 0.6: [-34, 6, 0], 0.75: [-36, -6, 0], 0.95: [-34, 0, 0], 1.2: [0, 0, 0]})},
        "jaw": {"rotation": kf({0: [0, 0, 0], 0.4: [0, 0, 0], 0.5: [45, 0, 0], 0.95: [42, 0, 0], 1.1: [0, 0, 0]})},
        "arm_ul": {"rotation": kf({0: [0, 0, 0], 0.2: [25, 0, -5], 0.45: [-22, 0, 82], 0.95: [-18, 0, 76], 1.2: [0, 0, 0]})},
        "arm_ur": {"rotation": kf({0: [0, 0, 0], 0.2: [25, 0, 5], 0.45: [-22, 0, -82], 0.95: [-18, 0, -76], 1.2: [0, 0, 0]})},
        "arm_ll": {"rotation": kf({0: [0, 0, 0], 0.45: [-40, 0, 30], 0.95: [-40, 0, 30], 1.2: [0, 0, 0]})},
        "arm_lr": {"rotation": kf({0: [0, 0, 0], 0.45: [-40, 0, -30], 0.95: [-40, 0, -30], 1.2: [0, 0, 0]})},
        "tail0": {"rotation": kf({0: [0, 0, 0], 0.45: [-15, 0, 0], 1.2: [0, 0, 0]})},
        "tail3": {"rotation": kf({0: [0, 0, 0], 0.45: [-20, 0, 0], 1.2: [0, 0, 0]})}}}
    A["pounce"] = {"animation_length": 0.9, "bones": {   # a coiled crouch, then it springs, claws thrown forward, and lands low
        "root": {"position": kf({0: [0, 0, 0], 0.25: [0, 1.5, 0], 0.6: [0, 0, 0]})},
        "body": {"position": kf({0: [0, 0, 0], 0.12: [0, -2.5, 0], 0.3: [0, 0.5, 0], 0.65: [0, -2, 0], 0.9: [0, 0, 0]}),
                 "rotation": kf({0: [0, 0, 0], 0.12: [25, 0, 0], 0.3: [15, 0, 0], 0.65: [22, 0, 0], 0.9: [0, 0, 0]})},
        "neck": {"rotation": kf({0: [0, 0, 0], 0.12: [-15, 0, 0], 0.3: [-20, 0, 0], 0.9: [0, 0, 0]})},
        "jaw": {"rotation": kf({0: [0, 0, 0], 0.25: [30, 0, 0], 0.6: [0, 0, 0]})},
        "arm_ul": {"rotation": kf({0: [0, 0, 0], 0.12: [30, 0, 12], 0.3: [-85, 0, -15], 0.6: [-55, 0, -5], 0.9: [0, 0, 0]})},
        "arm_ur": {"rotation": kf({0: [0, 0, 0], 0.12: [30, 0, -12], 0.3: [-85, 0, 15], 0.6: [-55, 0, 5], 0.9: [0, 0, 0]})},
        "leg_l": {"rotation": kf({0: [0, 0, 0], 0.12: [-8, 0, 0], 0.3: [30, 0, 0], 0.6: [-12, 0, 0], 0.9: [0, 0, 0]})},
        "leg_r": {"rotation": kf({0: [0, 0, 0], 0.12: [-8, 0, 0], 0.3: [24, 0, 0], 0.6: [-16, 0, 0], 0.9: [0, 0, 0]})},
        "tail0": {"rotation": kf({0: [0, 0, 0], 0.3: [25, 0, 0], 0.9: [0, 0, 0]})}}}
    return {"format_version": "1.8.0", "animations": {f"animation.shadow_ravener.{k}": v for k, v in A.items()}}


PAINTERS = {
    "plate": gp.plate_painter((34, 35, 46), (26, 70, 160), (50, 120, 230), ornate=True),
    "plate2": gp.plate_painter((20, 20, 28), (30, 90, 200), (90, 170, 255), engrave=False, ridges=True),
    "helm": gp.plate_painter((30, 31, 42), (26, 70, 160), (50, 120, 230), engrave=False, rivets=False),
    "plate3": gp.plate_painter((6, 6, 10), (20, 50, 120), (40, 90, 200), engrave=False, rivets=False),
    "mail": gp.mail_painter((40, 44, 58)),
    "cape": gp.cloth_painter((24, 58, 150)),
    "visor": gp.visor_painter((14, 14, 20), BLUE, (200, 235, 255)),
    "eyes": gp.eyes_painter((12, 12, 18), BLUE, (210, 240, 255)),
    "blade": gp.blade_painter((34, 36, 48), BLUE, BRIGHT),
    "glow": gp.glow_painter(BLUE, BRIGHT),
    "bone": gp.bone_painter((40, 42, 56), (50, 120, 240), (110, 190, 255)),
    "skull": gp.bone_painter((34, 36, 48), (50, 120, 240), (110, 190, 255)),
    "teeth": gp.plate_painter((176, 196, 226), (120, 170, 240), (200, 230, 255), engrave=False, rivets=False),
    "rune": gp.rune_painter((16, 16, 24), (70, 160, 255), (150, 215, 255)),
    "smoke": gp.smoke_painter((6, 6, 10), (30, 70, 160), (60, 140, 255)),
    "flameblade": gp.flameblade_painter((70, 165, 255), (12, 14, 26), (110, 200, 255)),
    "grip": gp.grip_painter((12, 12, 16), (34, 58, 130)),
}


class _NoGlow:
    def __setitem__(self, k, v): pass


def noglow(painter):
    """The same painter, but nothing it paints glows (a living thing's plates and bone aren't lit from within)."""
    return lambda px, gx, *a: painter(px, _NoGlow(), *a)


def nothing(px, gx, *a):
    """Paints nothing - the part is left clear (the living forms have no shadow smoke)."""


# The living forms the shadows were before they were raised - their own colours, fought in a Challenge
# (Shadow Army menu): the knight in steel and gold with a crimson cape and amber eyes behind the visor; the Ravener in
# ash-grey hide and ivory bone with amber eyes. Same models; only the textures differ (<name>_living.png).
KNIGHT_LIVING = {
    "plate": noglow(gp.plate_painter((150, 156, 168), (96, 82, 52), (0, 0, 0), ornate=True)),
    "plate2": noglow(gp.plate_painter((112, 116, 128), (150, 120, 60), (0, 0, 0), engrave=False, ridges=True)),
    "helm": noglow(gp.plate_painter((160, 166, 178), (100, 104, 116), (0, 0, 0), engrave=False, rivets=False)),
    "plate3": noglow(gp.plate_painter((62, 64, 72), (44, 46, 54), (0, 0, 0), engrave=False, rivets=False)),
    "mail": gp.mail_painter((92, 94, 102)),
    "cape": gp.cloth_painter((128, 26, 32)),
    "visor": gp.visor_painter((36, 36, 42), (255, 180, 70), (255, 215, 140)),
    "eyes": gp.eyes_painter((36, 36, 42), (255, 180, 70), (255, 215, 140)),
    "blade": noglow(gp.blade_painter((176, 182, 196), (128, 134, 150), (0, 0, 0))),
    "glow": noglow(gp.glow_painter((196, 156, 70), (226, 190, 100))),
    "bone": noglow(gp.bone_painter((200, 190, 166), (150, 140, 120), (0, 0, 0))),
    "skull": noglow(gp.bone_painter((190, 180, 156), (150, 140, 120), (0, 0, 0))),
    "teeth": noglow(gp.plate_painter((232, 224, 204), (200, 190, 170), (0, 0, 0), engrave=False, rivets=False)),
    "rune": noglow(gp.rune_painter((62, 64, 72), (206, 166, 84), (0, 0, 0))),
    "smoke": nothing,
    "flameblade": noglow(gp.flameblade_painter((200, 206, 220), (70, 74, 88), (0, 0, 0))),
    "grip": gp.grip_painter((54, 32, 22), (160, 120, 56)),
}
RAVENER_LIVING = dict(KNIGHT_LIVING, **{
    "plate": noglow(gp.plate_painter((88, 86, 82), (58, 56, 52), (0, 0, 0), engrave=False)),
    "plate2": noglow(gp.plate_painter((66, 64, 62), (46, 44, 42), (0, 0, 0), engrave=False, ridges=True)),
    "plate3": noglow(gp.plate_painter((42, 40, 38), (30, 28, 26), (0, 0, 0), engrave=False, rivets=False)),
    "mail": gp.mail_painter((72, 68, 64)),
    "eyes": gp.eyes_painter((40, 38, 36), (255, 160, 40), (255, 200, 90)),
    "glow": gp.glow_painter((255, 160, 40), (255, 200, 90)),
})


def build_living(name, model, painters, density=8):
    """Paint a second texture for the same model (identical layout), as <name>_living.png + its glowmask."""
    bones = model["minecraft:geometry"][0]["bones"]
    size, faces = gp.layout(bones, density, sheet=2048)
    blade = [fc for fc in faces if fc["mat"] == "flameblade"]
    if blade:
        lo = min(fc["cube"]["origin"][1] for fc in blade); hi = max(fc["cube"]["origin"][1] + fc["cube"]["size"][1] for fc in blade)
        for fc in blade:
            o, sz = fc["cube"]["origin"][1], fc["cube"]["size"][1]
            fc["t0"], fc["t1"] = (o - lo) / (hi - lo), (o + sz - lo) / (hi - lo)
    img, glow = gp.paint(faces, size, painters)
    img.save(os.path.join(TEX, name + "_living.png")); glow.save(os.path.join(TEX, name + "_living_glowmask.png"))


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
    build("shadow_ravener", ravener())
    build_living("shadow_knight", knight(), KNIGHT_LIVING)
    build_living("shadow_ravener", ravener(), RAVENER_LIVING)
    json.dump(ravener_anims(), open(os.path.join(ANIM, "shadow_ravener.animation.json"), "w"), indent=1)
    print("shadow models written")


if __name__ == "__main__":
    main()
