"""
The Mech Grimoire's GeckoLib models, made in code so they can be tuned and rebuilt - an original heavy war machine
in a grounded military style: layered painted armour (sand plates over a slate frame) with panel lines, rivets,
chipped edges and grime, hydraulic pistons, cable joints, red unit stencils and a visor bar of three amber lenses.

  mech_titan  - the machine that stands round its pilot (Pilot). One bone per buildable part, so the game hides the
                parts you haven't built: arm_l/arm_r (Arms) with fist_l/fist_r (Fists) on them, thrusters
                (Thrusters), leg_l/leg_r (Legs), plating (Plating), launcher (Launchers), core (Core), cannon
                (Cannon). The frame - hips, waist pistons, spine and the sensor head - is always there.
                Animations: assemble (bolted together from the feet up), idle, walk, punch.
  mech_fist   - one great armoured fist (Rocket Fist, and Titan Buster scaled huge); the forearm bone is hidden for
                the rocket fist. Animations: clench, idle.

Made in Blockbench's entity space with the feet at y = -24 (the player model's origin is 24 px above the feet;
client/MechModels turns it the right way up), the front facing north (-Z). The texture sheet holds one 64x64 tile per
material and every face maps a whole tile; the _glowmask sheet lights the lenses, the reactor and the thruster throats.

Run from the project root: python3 tools/mech_models.py
"""
import json
import math
import os
import random
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "iron_spell_grimoire")
GEO = os.path.join(ROOT, "geo")
ANIM = os.path.join(ROOT, "animations")
TEX = os.path.join(ROOT, "textures", "geo")

T = 64
SHEET = 512
TILES = {   # material -> (column, row) of its 64x64 tile on the 512x512 sheet
    "sand": (0, 0), "sand2": (1, 0), "slate": (2, 0), "frame": (3, 0), "piston": (4, 0), "joint": (5, 0), "hazard": (6, 0), "vent": (7, 0),
    "visor": (0, 1), "lens": (1, 1), "thrust": (2, 1), "reactor": (3, 1), "stencil": (4, 1), "tube": (5, 1), "barrel": (6, 1), "rubber": (7, 1),
}
GLOWING = {"lens", "thrust", "reactor"}

SAND = (176, 156, 116)
SLATE = (74, 84, 92)
GUN = (44, 48, 54)
RED = (170, 40, 32)


def noise_fill(img, box, base, amount, rnd):
    x0, y0, x1, y1 = box
    px = img.load()
    for y in range(y0, y1):
        for x in range(x0, x1):
            n = rnd.randint(-amount, amount)
            px[x, y] = tuple(max(0, min(255, c + n)) for c in base) + (255,)


def grime(img, box, rnd, strength=60):
    """Darker toward the bottom and in a few streaks: dirt run down the plate."""
    x0, y0, x1, y1 = box
    px = img.load()
    streaks = [rnd.randint(x0, x1 - 1) for _ in range(3)]
    for y in range(y0, y1):
        k = (y - y0) / (y1 - y0)
        for x in range(x0, x1):
            r, g, b, a = px[x, y]
            d = int(strength * k * k)
            if any(abs(x - s) < 2 for s in streaks) and rnd.random() < 0.6:
                d += int(25 * k)
            px[x, y] = (max(0, r - d), max(0, g - d), max(0, b - d), a)


def chips(d, box, rnd, n=14):
    """Paint chipped off the edges, showing bare metal."""
    x0, y0, x1, y1 = box
    for _ in range(n):
        side = rnd.randint(0, 3)
        if side == 0: x, y = rnd.randint(x0, x1 - 3), y0 + rnd.randint(0, 2)
        elif side == 1: x, y = rnd.randint(x0, x1 - 3), y1 - 3 - rnd.randint(0, 2)
        elif side == 2: x, y = x0 + rnd.randint(0, 2), rnd.randint(y0, y1 - 3)
        else: x, y = x1 - 3 - rnd.randint(0, 2), rnd.randint(y0, y1 - 3)
        w, h = rnd.randint(1, 3), rnd.randint(1, 2)
        d.rectangle([x, y, x + w, y + h], fill=(150, 152, 156, 255))


def panel(img, d, box, base, rnd, lines=True, rivets=True, chipped=True):
    x0, y0, x1, y1 = box
    noise_fill(img, (x0, y0, x1, y1), base, 6, rnd)
    hi = tuple(min(255, c + 28) for c in base) + (255,)
    lo = tuple(max(0, c - 45) for c in base) + (255,)
    d.line([(x0, y0), (x1 - 1, y0)], fill=hi, width=2)        # light on the top edge, shadow underneath
    d.line([(x0, y0), (x0, y1 - 1)], fill=hi, width=1)
    d.line([(x0, y1 - 1), (x1 - 1, y1 - 1)], fill=lo, width=2)
    d.line([(x1 - 1, y0), (x1 - 1, y1 - 1)], fill=lo, width=1)
    if lines:   # panel lines
        mx = x0 + (x1 - x0) * rnd.choice([1, 2]) // 3
        d.line([(mx, y0 + 4), (mx, y1 - 5)], fill=lo, width=1)
        my = y0 + (y1 - y0) // 2 + rnd.randint(-6, 6)
        d.line([(x0 + 4, my), (x1 - 5, my)], fill=lo, width=1)
    if rivets:
        for rx in (x0 + 4, x1 - 6):
            for ry in (y0 + 4, y1 - 6):
                d.rectangle([rx, ry, rx + 1, ry + 1], fill=lo)
                d.point((rx, ry), fill=hi)
    if chipped:
        chips(d, box, rnd)
    grime(img, box, rnd)


def make_texture(path, glow_path):
    rnd = random.Random(7)
    img = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
    glow = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
    d, gd = ImageDraw.Draw(img), ImageDraw.Draw(glow)

    def box(name):
        c, r = TILES[name]
        return c * T, r * T, c * T + T, r * T + T

    # sand armour: two variants (one with a red stencil band)
    panel(img, d, box("sand"), SAND, rnd)
    b = box("sand2"); panel(img, d, b, SAND, rnd)
    d.rectangle([b[0] + 6, b[1] + 40, b[2] - 7, b[1] + 47], fill=RED + (255,))
    # slate armour
    panel(img, d, box("slate"), SLATE, rnd)
    # frame: dark gunmetal with bolts and a recessed channel
    b = box("frame"); panel(img, d, b, GUN, rnd, lines=False, chipped=False)
    d.rectangle([b[0] + 22, b[1] + 6, b[0] + 41, b[3] - 7], fill=(30, 33, 38, 255))
    for yy in range(b[1] + 10, b[3] - 8, 10):
        d.rectangle([b[0] + 28, yy, b[0] + 35, yy + 3], fill=(66, 70, 78, 255))
    # piston: a chrome rod - bright stripe down a dark cylinder
    b = box("piston")
    for x in range(b[0], b[2]):
        k = (x - b[0]) / T
        v = int(70 + 160 * math.exp(-((k - 0.35) ** 2) / 0.02) + 40 * k)
        d.line([(x, b[1]), (x, b[3] - 1)], fill=(min(255, v), min(255, v + 4), min(255, v + 10), 255))
    # joint: dark rubber boot with folds and cable bundles
    b = box("joint"); noise_fill(img, b[:4], (34, 34, 36), 5, rnd)
    for yy in range(b[1] + 4, b[3], 8):
        d.line([(b[0], yy), (b[2] - 1, yy)], fill=(18, 18, 20, 255), width=2)
        d.line([(b[0], yy + 2), (b[2] - 1, yy + 2)], fill=(58, 58, 62, 255), width=1)
    for cx in (b[0] + 14, b[0] + 46):
        d.line([(cx, b[1]), (cx, b[3] - 1)], fill=(140, 40, 30, 255), width=3)
    # hazard: worn yellow/black stripes
    b = box("hazard")
    tile = Image.new("RGBA", (T, T), (28, 28, 30, 255))   # drawn on its own so the stripes can't spill into the next tile
    td = ImageDraw.Draw(tile)
    for k in range(-T, 2 * T, 16):
        td.polygon([(k, 0), (k + 8, 0), (k + 8 - T, T), (k - T, T)], fill=(214, 168, 40, 255))
    img.paste(tile, (b[0], b[1]))
    chips(d, b, rnd, 20); grime(img, b, rnd, 40)
    # vent: slots
    b = box("vent"); panel(img, d, b, SLATE, rnd, lines=False)
    for yy in range(b[1] + 8, b[3] - 8, 7):
        d.rectangle([b[0] + 8, yy, b[2] - 9, yy + 3], fill=(16, 16, 18, 255))
    # visor bar: dark glass in a frame
    b = box("visor"); noise_fill(img, b[:4], (20, 24, 28), 3, rnd)
    d.rectangle([b[0], b[1], b[2] - 1, b[3] - 1], outline=GUN + (255,), width=4)
    d.line([(b[0] + 8, b[1] + 12), (b[0] + 20, b[1] + 8)], fill=(90, 100, 110, 255), width=2)   # a glint
    # lens: three amber lenses on dark glass (glowing)
    b = box("lens"); noise_fill(img, b[:4], (18, 20, 24), 3, rnd)
    for i, cx in enumerate((b[0] + 12, b[0] + 32, b[0] + 52)):
        cy = b[1] + 32
        r = 9 if i == 1 else 7
        for rr in range(r, 0, -1):
            k = 1 - rr / r
            col = (255, int(120 + 120 * k), int(30 + 180 * k * k), 255)
            d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=col)
            gd.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=col)
        d.ellipse([cx - r - 2, cy - r - 2, cx + r + 2, cy + r + 2], outline=GUN + (255,), width=2)
    # thrust: dark nozzle with a blue throat (glowing)
    b = box("thrust"); noise_fill(img, b[:4], (40, 42, 46), 4, rnd)
    for rr in range(26, 0, -1):
        k = 1 - rr / 26
        col = (int(60 + 180 * k), int(120 + 130 * k), 255, 255) if rr < 20 else (30, 32, 36, 255)
        d.ellipse([b[0] + 32 - rr, b[1] + 32 - rr, b[0] + 32 + rr, b[1] + 32 + rr], fill=col)
        if rr < 20: gd.ellipse([b[0] + 32 - rr, b[1] + 32 - rr, b[0] + 32 + rr, b[1] + 32 + rr], fill=col)
    # reactor: amber glow behind a cross grille (glowing)
    b = box("reactor")
    for rr in range(30, 0, -1):
        k = 1 - rr / 30
        col = (255, int(110 + 140 * k), int(20 + 200 * k * k), 255)
        d.ellipse([b[0] + 32 - rr, b[1] + 32 - rr, b[0] + 32 + rr, b[1] + 32 + rr], fill=col)
        gd.ellipse([b[0] + 32 - rr, b[1] + 32 - rr, b[0] + 32 + rr, b[1] + 32 + rr], fill=col)
    for k in range(4):
        d.rectangle([b[0], b[1] + 6 + k * 16, b[2] - 1, b[1] + 9 + k * 16], fill=GUN + (255,))
        gd.rectangle([b[0], b[1] + 6 + k * 16, b[2] - 1, b[1] + 9 + k * 16], fill=(0, 0, 0, 0))
    d.rectangle([b[0], b[1], b[2] - 1, b[3] - 1], outline=GUN + (255,), width=4)
    # stencil: sand plate with an original unit marking
    b = box("stencil"); panel(img, d, b, SAND, rnd, lines=False)
    d.rectangle([b[0] + 6, b[1] + 6, b[0] + 12, b[3] - 7], fill=RED + (255,))
    font_rows = {   # 3x5 digits/letters for "IS-07"
        "I": ["111", "010", "010", "010", "111"], "S": ["111", "100", "111", "001", "111"], "-": ["000", "000", "111", "000", "000"],
        "0": ["111", "101", "101", "101", "111"], "7": ["111", "001", "010", "010", "010"]}
    x = b[0] + 18
    for ch in "IS-07":
        for ry, row in enumerate(font_rows[ch]):
            for rx, bit in enumerate(row):
                if bit == "1": d.rectangle([x + rx * 2, b[1] + 22 + ry * 3, x + rx * 2 + 1, b[1] + 24 + ry * 3], fill=(40, 36, 30, 255))
        x += 8
    # tube: a missile-pod face - six dark tubes with red tips
    b = box("tube"); panel(img, d, b, SLATE, rnd, lines=False)
    for i in range(6):
        cx, cy = b[0] + 14 + (i % 3) * 18, b[1] + 20 + (i // 3) * 24
        d.ellipse([cx - 7, cy - 7, cx + 7, cy + 7], fill=(14, 14, 16, 255))
        d.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], fill=RED + (255,))
    # barrel: gunmetal with cooling rings
    b = box("barrel"); noise_fill(img, b[:4], GUN, 5, rnd)
    for yy in range(b[1] + 3, b[3], 10):
        d.rectangle([b[0], yy, b[2] - 1, yy + 3], fill=(80, 84, 90, 255))
    # rubber: plain dark (soles, grips)
    b = box("rubber"); noise_fill(img, b[:4], (30, 29, 28), 6, rnd)

    img.save(path)
    glow.save(glow_path)


# ---------------------------------------------------------------------------------------------------- geometry
def cube(origin, size, mat, rot=None, pivot=None):
    c, r = TILES[mat]
    face = {"uv": [c * T, r * T], "uv_size": [T, T]}
    cb = {"origin": [round(v, 3) for v in origin], "size": [round(v, 3) for v in size],
          "uv": {f: dict(face) for f in ("north", "south", "east", "west", "up", "down")}}
    if rot:
        cb["rotation"] = rot
        cb["pivot"] = pivot or [origin[0] + size[0] / 2, origin[1] + size[1] / 2, origin[2] + size[2] / 2]
    return cb


def bone(name, parent, pivot, cubes, rotation=None):
    b = {"name": name, "pivot": [round(v, 3) for v in pivot], "cubes": cubes}
    if parent:
        b["parent"] = parent
    if rotation:
        b["rotation"] = rotation
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


def titan():
    bones = [bone("root", None, [0, -24, 0], [])]
    # ---- frame (always): pelvis, waist pistons, spine, shoulder axle, the sensor head
    bones.append(bone("frame", "root", [0, -2, 0], [
        cube([-8, -4, -5], [16, 6, 10], "frame"),              # pelvis
        cube([-5, -6, -6], [10, 3, 11], "slate"),               # groin plate
        cube([-6, 2, -3], [3, 8, 3], "piston"),                 # waist pistons
        cube([3, 2, -3], [3, 8, 3], "piston"),
        cube([-3, 2, -2], [6, 12, 6], "joint"),                 # the spine's cable trunk
        cube([-14, 20, -2], [28, 3, 3], "frame"),               # shoulder axle
        cube([-9, -1, -5.5], [3, 3, 3], "joint"),               # hip balls
        cube([6, -1, -5.5], [3, 3, 3], "joint"),
    ]))
    bones.append(bone("head", "frame", [0, 24, -6], [
        cube([-5, 22, -12], [10, 6, 8], "slate"),               # sensor housing, forward on the chest
        cube([-5.5, 27, -12.5], [11, 2, 9], "sand"),            # brow plate
        cube([-4.5, 23.5, -12.6], [9, 3, 1], "lens"),           # the visor bar: three amber lenses
        cube([-6, 22, -11], [1, 5, 6], "frame"),                # cheek guards
        cube([5, 22, -11], [1, 5, 6], "frame"),
        cube([3, 29, -8], [1, 6, 1], "piston"),                 # whip antenna
    ]))
    # ---- plating: the hull - a deep chest, layered front plates, collar, back armour, belly plates, the stencil
    bones.append(bone("plating", "frame", [0, 12, 0], [
        cube([-11, 8, -9], [22, 14, 16], "slate"),              # chest hull
        cube([-10, 13, -10.5], [20, 8, 2], "sand"),             # upper breastplate
        cube([-9, 8, -10], [8, 6, 1.5], "sand2"),               # lower plates (one with a red band)
        cube([1, 8, -10], [8, 6, 1.5], "sand"),
        cube([-11.5, 21, -9.5], [23, 2, 17], "hazard"),         # collar trim
        cube([-12, 9, -7], [1.5, 11, 12], "sand"),              # side plates
        cube([10.5, 9, -7], [1.5, 11, 12], "stencil"),
        cube([-9, 9, 6.5], [18, 11, 2], "vent"),                # back armour: vents
        cube([-7, 2, -7.5], [14, 6, 2], "sand"),                # belly plate
    ]))
    bones.append(bone("core", "frame", [0, 15, -11], [
        cube([-3, 13.5, -11.6], [6, 6, 1], "reactor"),          # the reactor port, glowing through its grille
        cube([-4, 12.5, -11.2], [8, 1, 1], "frame"),
        cube([-4, 19.5, -11.2], [8, 1, 1], "frame"),
    ]))
    # ---- arms (Arms) with hands (Fists): long and heavy, hanging forward of the body
    arm_l = [
        cube([10, 17, -7], [10, 8, 12], "sand"),                # pauldron, two layers
        cube([11, 15, -6.5], [9, 3, 11], "sand2"),
        cube([13, 7, -3], [5, 10, 5], "frame"),                 # upper arm
        cube([18, 7, -2], [1.5, 9, 2], "piston"),               # biceps piston
        cube([12.5, 4, -3.5], [6, 4, 6], "joint"),              # elbow
        cube([12, -9, -5], [8, 13, 9], "sand"),                 # forearm armour
        cube([11.5, -5, -5.5], [9, 2, 10], "hazard"),
        cube([13, -11, -3.5], [6, 3, 6], "joint"),              # wrist
    ]
    hand_l = [
        cube([11.5, -18, -6], [9, 8, 9], "frame"),              # the hand
        cube([11, -15, -7], [10, 4, 2], "sand"),                # knuckle guard
        cube([12, -21, -6.5], [2.5, 4, 3], "frame"),            # three heavy fingers and a thumb
        cube([15, -21, -6.5], [2.5, 4, 3], "frame"),
        cube([18, -21, -6.5], [2.5, 4, 3], "frame"),
        cube([10, -17, -4], [2, 5, 3], "frame"),
    ]
    bones.append(bone("arm_l", "frame", [15, 21, 0], arm_l))
    bones.append(bone("fist_l", "arm_l", [16, -11, 0], hand_l))
    bones.append(bone("arm_r", "frame", [-15, 21, 0], mirror_x(arm_l)))
    bones.append(bone("fist_r", "arm_r", [-16, -11, 0], mirror_x(hand_l)))
    # ---- legs (Legs): thick, armoured, with a hydraulic ram down the back of each
    leg_l = [
        cube([3, -11, -4], [7, 9, 7], "frame"),                 # thigh
        cube([2.5, -10, -5], [8, 7, 2], "sand"),                # thigh plate
        cube([9.5, -12, 0], [2, 10, 2], "piston"),              # hip-to-knee ram
        cube([3, -14, -5.5], [7, 4, 7], "joint"),               # knee
        cube([2.5, -15, -7], [8, 6, 3], "sand2"),               # knee cap
        cube([3, -21, -4], [7, 8, 7], "sand"),                  # shin armour
        cube([3.5, -19, -5], [6, 2, 1], "hazard"),
        cube([4, -22, -2], [5, 2, 5], "joint"),                 # ankle
        cube([2, -24, -8], [10, 2.5, 13], "frame"),             # foot
        cube([2, -24.2, -8], [10, 0.6, 13], "rubber"),          # its sole
        cube([2.5, -23, -10], [2.5, 2, 2.5], "frame"),          # toes
        cube([5.8, -23, -10], [2.5, 2, 2.5], "frame"),
        cube([9, -23, -10], [2.5, 2, 2.5], "frame"),
        cube([5.5, -23.5, 5], [3, 2, 3], "frame"),              # heel spur
    ]
    bones.append(bone("leg_l", "frame", [6.5, -1, 0], leg_l))
    bones.append(bone("leg_r", "frame", [-6.5, -1, 0], mirror_x(leg_l)))
    # ---- thrusters (Thrusters): a jump pack with two exhausts and a fuel tank
    bones.append(bone("thrusters", "frame", [0, 14, 9], [
        cube([-8, 8, 8], [16, 12, 5], "slate"),
        cube([-7, 9, 12.5], [5, 9, 3], "sand"),
        cube([2, 9, 12.5], [5, 9, 3], "sand"),
        cube([-7, 4, 9], [5, 4, 5], "thrust"),                  # exhaust bells, blue in their throats
        cube([2, 4, 9], [5, 4, 5], "thrust"),
        cube([-2, 12, 13], [4, 7, 3], "piston"),                # fuel tank
    ]))
    # ---- launchers (Launchers): a six-tube missile box on the left shoulder
    bones.append(bone("launcher", "frame", [15, 26, 0], [
        cube([11, 25, -7], [9, 7, 12], "slate"),
        cube([11.5, 25.5, -7.6], [8, 6, 1], "tube"),
        cube([12, 23, -3], [3, 2, 5], "frame"),                 # its mount
        cube([20, 26, -5], [1, 5, 8], "hazard"),
    ]))
    # ---- cannon (Cannon): a long rifle over the right shoulder
    bones.append(bone("cannon", "frame", [-15, 26, 0], [
        cube([-20, 24, -4], [8, 6, 10], "slate"),               # receiver
        cube([-17.5, 25.5, -22], [3, 3, 18], "barrel"),         # barrel
        cube([-18.5, 24.5, -14], [5, 5, 7], "frame"),           # shroud
        cube([-19, 24, -24], [6, 4, 3], "frame"),               # muzzle brake
        cube([-18, 21, -1], [4, 3, 5], "slate"),                # magazine
        cube([-20.5, 28, 0], [9, 1, 6], "hazard"),
    ]))
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.mech_titan", "texture_width": SHEET, "texture_height": SHEET,
                        "visible_bounds_width": 5, "visible_bounds_height": 5, "visible_bounds_offset": [0, 1, 0]},
        "bones": bones}]}


def fist():
    # a heavy armoured fist pointing north (-Z), knuckles at the front, wrist and forearm behind
    bones = [bone("root", None, [0, 0, 0], [])]
    bones.append(bone("palm", "root", [0, 0, 0], [
        cube([-6, -6, -4], [12, 12, 10], "frame"),
        cube([-6.5, 2, -5], [13, 5, 9], "sand"),                # back-of-hand armour
        cube([-6.5, -6.5, 3], [13, 13, 2], "hazard"),
    ]))
    for i in range(3):   # three heavy fingers curled down
        x = -6 + i * 4.2
        bones.append(bone(f"finger{i}", "palm", [x + 1.8, 5, -4], [
            cube([x, -1, -9], [3.6, 6, 5], "frame"),
            cube([x - 0.2, 4, -9.5], [4, 1.5, 4], "sand2"),     # knuckle caps
        ]))
    bones.append(bone("thumb", "palm", [6, -2, -2], [
        cube([5.5, -5, -7], [3, 5, 5], "frame"),
    ]))
    bones.append(bone("wrist", "root", [0, 0, 6], [
        cube([-5, -5, 5], [10, 10, 4], "joint"),
    ]))
    bones.append(bone("forearm", "root", [0, 0, 10], [
        cube([-5.5, -5.5, 9], [11, 11, 18], "sand"),
        cube([-6, -6, 13], [12, 12, 2], "hazard"),
        cube([-4, 5.5, 12], [8, 1, 12], "stencil"),
        cube([-6.5, -2, 15], [1.5, 4, 10], "piston"),
        cube([5, -2, 15], [1.5, 4, 10], "piston"),
        cube([-3, -3, 27], [6, 6, 2], "thrust"),                # a rocket in its elbow
    ]))
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.mech_fist", "texture_width": SHEET, "texture_height": SHEET,
                        "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 0, 0]},
        "bones": bones}]}


# ---------------------------------------------------------------------------------------------------- animations
def kf(d):
    return {f"{t:.4f}": v for t, v in sorted(d.items())}


def titan_anims():
    A = {}
    # assemble: bolted together from the feet up - each part drops in from above and seats with a jolt
    order = [("leg_l", 0.0), ("leg_r", 0.06), ("frame", 0.14), ("plating", 0.3), ("core", 0.4), ("arm_l", 0.46),
             ("arm_r", 0.52), ("fist_l", 0.6), ("fist_r", 0.63), ("thrusters", 0.66), ("launcher", 0.72), ("cannon", 0.76), ("head", 0.84)]
    bones = {}
    for name, t0 in order:
        bones[name] = {
            "scale": kf({0.0: [0, 0, 0], t0: [0, 0, 0], t0 + 0.01: [1, 1, 1], 1.1: [1, 1, 1]}),
            "position": kf({0.0: [0, 10, 0], t0: [0, 10, 0], t0 + 0.1: [0, -0.6, 0], t0 + 0.16: [0, 0, 0], 1.1: [0, 0, 0]}),
        }
    bones["root"] = {"position": kf({0.0: [0, 0, 0], 0.9: [0, -1, 0], 1.0: [0, 0.3, 0], 1.1: [0, 0, 0]})}
    A["animation.mech_titan.assemble"] = {"loop": False, "animation_length": 1.1, "bones": bones}
    # idle: heavy breathing - the hull rises and settles, the head scans, the arms hang and sway
    A["animation.mech_titan.idle"] = {"loop": True, "animation_length": 4.0, "bones": {
        "root": {"position": kf({0.0: [0, 0, 0], 2.0: [0, 0.35, 0], 4.0: [0, 0, 0]})},
        "head": {"rotation": kf({0.0: [0, 0, 0], 1.2: [0, 12, 0], 2.0: [3, 12, 0], 3.0: [0, -10, 0], 4.0: [0, 0, 0]})},
        "arm_l": {"rotation": kf({0.0: [-6, 0, -4], 2.0: [-9, 0, -6], 4.0: [-6, 0, -4]})},
        "arm_r": {"rotation": kf({0.0: [-6, 0, 4], 2.0: [-9, 0, 6], 4.0: [-6, 0, 4]})},
    }}
    # walk: heavy strides - the body dips on each footfall, the arms swing against the legs
    A["animation.mech_titan.walk"] = {"loop": True, "animation_length": 1.4, "bones": {
        "leg_l": {"rotation": kf({0.0: [24, 0, 0], 0.7: [-24, 0, 0], 1.4: [24, 0, 0]})},
        "leg_r": {"rotation": kf({0.0: [-24, 0, 0], 0.7: [24, 0, 0], 1.4: [-24, 0, 0]})},
        "arm_l": {"rotation": kf({0.0: [-18, 0, -4], 0.7: [12, 0, -4], 1.4: [-18, 0, -4]})},
        "arm_r": {"rotation": kf({0.0: [12, 0, 4], 0.7: [-18, 0, 4], 1.4: [12, 0, 4]})},
        "frame": {"rotation": kf({0.0: [0, 4, 0], 0.7: [0, -4, 0], 1.4: [0, 4, 0]})},
        "root": {"position": kf({0.0: [0, 0, 0], 0.15: [0, -1, 0], 0.35: [0, 0.6, 0], 0.7: [0, 0, 0], 0.85: [0, -1, 0], 1.05: [0, 0.6, 0], 1.4: [0, 0, 0]})},
    }}
    # punch: the right arm drives straight out and back (played while the pilot swings)
    A["animation.mech_titan.punch"] = {"loop": True, "animation_length": 0.5, "bones": {
        "arm_r": {"rotation": kf({0.0: [-6, 0, 4], 0.1: [-30, 10, 4], 0.2: [-92, -12, 4], 0.3: [-85, -10, 4], 0.5: [-6, 0, 4]})},
        "frame": {"rotation": kf({0.0: [0, 0, 0], 0.1: [0, 10, 0], 0.2: [0, -14, 0], 0.5: [0, 0, 0]})},
    }}
    return {"format_version": "1.8.0", "animations": A}


def fist_anims():
    A = {}
    curl = {}
    for i in range(3):
        d = 0.03 * i
        curl[f"finger{i}"] = {"rotation": kf({0.0: [70, 0, 0], 0.08 + d: [70, 0, 0], 0.22 + d: [-12, 0, 0], 0.3 + d: [0, 0, 0]})}
    curl["thumb"] = {"rotation": kf({0.0: [0, 50, 0], 0.15: [0, 50, 0], 0.3: [0, 0, 0]})}
    curl["root"] = {"scale": kf({0.0: [0.6, 0.6, 0.6], 0.15: [1.06, 1.06, 1.06], 0.3: [1, 1, 1]})}
    A["animation.mech_fist.clench"] = {"loop": False, "animation_length": 0.4, "bones": curl}
    A["animation.mech_fist.idle"] = {"loop": True, "animation_length": 0.6, "bones": {
        "palm": {"position": kf({0.0: [0, 0, 0], 0.3: [0, 0, -0.3], 0.6: [0, 0, 0]})},
    }}
    return {"format_version": "1.8.0", "animations": A}


def main():
    for d in (GEO, ANIM, TEX):
        os.makedirs(d, exist_ok=True)
    json.dump(titan(), open(os.path.join(GEO, "mech_titan.geo.json"), "w"), indent=1)
    json.dump(fist(), open(os.path.join(GEO, "mech_fist.geo.json"), "w"), indent=1)
    json.dump(titan_anims(), open(os.path.join(ANIM, "mech_titan.animation.json"), "w"), indent=1)
    json.dump(fist_anims(), open(os.path.join(ANIM, "mech_fist.animation.json"), "w"), indent=1)
    for name in ("mech_titan", "mech_fist"):
        make_texture(os.path.join(TEX, name + ".png"), os.path.join(TEX, name + "_glowmask.png"))
    print("mech models written")


if __name__ == "__main__":
    main()
