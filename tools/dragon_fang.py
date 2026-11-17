"""The Dragon Fang (an original design) - a GeckoLib item model, painted in the shadow models' style.

A long, slender dragon-tooth blade of dark crimson, its edge rising in flame-like waves, a hooked spike swept back off
its spine near the hilt, its point lifting slightly. Down the whole blade runs a dark channel with two glowing ember
strands braiding through it. The guard is a small swept crescent of dark gold set with an ember gem; the grip is wound
in crimson cord; the pommel ends in a little claw.

Model space: pixels, the blade pointing up (+y), the cutting edge facing -z, the spine +z. Origin at the middle of the
grip, where the hand holds it.

python3 tools/dragon_fang.py                 writes geo/item/dragon_fang.geo.json, its texture + glowmask, animation
python3 tools/dragon_fang.py preview OUT.png  also renders it from a few sides (and close up)
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(__file__))
import geo_paint as gp
from shadow_models import cube, bone, geo, mirror_x, kf

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "iron_spell_grimoire")


# ------------------------------------------------------------------ painters
def blade_painter():
    """The blade's core: dark crimson, a brighter red bevel along both edges, and down its middle a near-black channel
    with two ember strands braiding through it the whole way to the point (fading out near the tip)."""
    BODY, BODY2, BEVEL, EDGE = (128, 14, 24), (70, 6, 14), (214, 44, 44), (255, 130, 110)
    CH, LINE, GLOW = (34, 2, 8), (255, 96, 40), (255, 140, 70)

    def p(px, gx, x0, y0, w, h, face, rnd, fc=None):
        broad = face in ("east", "west")
        g0, g1 = (fc.get("t0", 0.0), fc.get("t1", 1.0)) if fc else (0.0, 1.0)
        for y in range(h):
            for x in range(w):
                if not broad:   # the narrow faces: the bright honed edge
                    c = gp.lit(BEVEL, 0.85 + 0.1 * rnd.random()) if face not in ("up", "down") else BODY
                    px[x0 + x, y0 + y] = c + (255,)
                    continue
                t = g0 + (g1 - g0) * (1 - y / max(1, h - 1))   # 0 root, 1 point
                a = x / max(1, w - 1)
                d = a - 0.5
                c = gp.mix(BODY, BODY2, t * 0.8)
                c = gp.lit(c, 0.88 + 0.22 * (1 - abs(d) * 2) + 0.04 * rnd.random())
                if abs(d) > 0.36: c = gp.mix(c, BEVEL, min(1, (abs(d) - 0.36) / 0.1))   # the bevel
                if abs(d) > 0.47: c = EDGE
                gl = None
                hw = 0.2 * (1 - 0.5 * t)                          # the channel
                if t < 0.93 and abs(d) < hw:
                    c = gp.mix(CH, BODY2, 0.3 + 0.2 * math.sin(t * 60))
                    s1 = hw * 0.75 * math.sin(t * 46)
                    if abs(d - s1) < 0.045 or abs(d + s1) < 0.045:   # two strands, crossing and parting
                        c, gl = LINE, GLOW
                    if abs(abs(d) - hw) < 0.03: c = gp.mix(BEVEL, CH, 0.4)   # the channel's rim
                px[x0 + x, y0 + y] = c + (255,)
                if gl: gx[x0 + x, y0 + y] = gl + (255,)
    p.wants_face = True
    return p


def lobe_painter():
    """A flame-wave of the edge: crimson at its root brightening to a hot red rim, an ember streak down its middle."""
    ROOT_C, TIP, RIM, STREAK = (120, 12, 22), (226, 50, 44), (255, 140, 110), (255, 110, 50)
    def p(px, gx, x0, y0, w, h, face, rnd):
        for y in range(h):
            for x in range(w):
                a = x / max(1, w - 1)
                v = 1 - y / max(1, h - 1)
                c = gp.mix(ROOT_C, TIP, v)
                c = gp.lit(c, 0.9 + 0.15 * (1 - abs(a - 0.5) * 2))
                if a < 0.1 or a > 0.9: c = RIM
                if abs(a - 0.5) < 0.08 and 0.15 < v < 0.8:
                    c = STREAK; gx[x0 + x, y0 + y] = (255, 140, 70, 255)
                px[x0 + x, y0 + y] = c + (255,)
    return p


def wave_painter():
    """The wavy cutting edge: honed red, darkening to its middle, bright along its rims; a thin glow at its crest."""
    MID, HI, RIM = (130, 14, 22), (205, 36, 38), (250, 110, 90)
    def p(px, gx, x0, y0, w, h, face, rnd):
        for y in range(h):
            for x in range(w):
                a = x / max(1, w - 1)
                c = gp.lit(gp.mix(HI, MID, 1 - abs(a - 0.5) * 2), 0.92 + 0.08 * rnd.random())
                if a < 0.12 or a > 0.88: c = RIM
                px[x0 + x, y0 + y] = c + (255,)
                if face in ("north", "south"): gx[x0 + x, y0 + y] = (255, 120, 80, 255)
    return p


def wrap_painter(base, cord, S=6):
    """Cord wound diagonally round the grip: raised bands, lit along their crests, dark in the gaps."""
    def p(px, gx, x0, y0, w, h, face, rnd):
        for y in range(h):
            for x in range(w):
                k = ((x + y) % S) / S
                c = gp.lit(cord, 0.7 + 0.5 * math.sin(k * math.pi)) if k < 0.8 else base
                px[x0 + x, y0 + y] = c + (255,)
    return p


def scale_painter(base, hi, edge, S=4):
    """Overlapping scales: rows of half-moons, each lit on top and dark along its rim."""
    def p(px, gx, x0, y0, w, h, face, rnd):
        for y in range(h):
            for x in range(w):
                row = y // S
                xo = x + (S // 2 if row % 2 else 0)
                cx, cy = (xo // S) * S + S / 2, row * S
                d = math.hypot((xo - cx) / (S / 2), (y - cy) / S)
                c = gp.lit(base, 1.15 - 0.35 * min(1, d))
                if 0.85 < d < 1.05: c = edge
                if d < 0.35 and (y - cy) < 1: c = hi
                px[x0 + x, y0 + y] = c + (255,)
    return p


GOLD = gp.noglow if hasattr(gp, "noglow") else None
def orb_painter(core, mid, rim):
    """A glowing orb: a bright core fading through ember to a deep rim, all of it lit."""
    def p(px, gx, x0, y0, w, h, face, rnd):
        for y in range(h):
            for x in range(w):
                d = math.hypot((x + 0.5) / w - 0.5, (y + 0.5) / h - 0.5) * 2
                c = gp.mix(core, mid, min(1, d * 1.4)) if d < 0.7 else gp.mix(mid, rim, (d - 0.7) / 0.7)
                px[x0 + x, y0 + y] = c + (255,)
                gx[x0 + x, y0 + y] = gp.mix(c, (255, 255, 255), 0.15) + (255,)
    return p


P = {
    "blade": blade_painter(),
    "lobe": lobe_painter(),
    "wave": wave_painter(),
    "wrap": wrap_painter((30, 4, 8), (150, 26, 30)),
    "gold": gp.plate_painter((168, 122, 46), (110, 74, 24), (0, 0, 0), engrave=False, rivets=False),
    "gold2": gp.plate_painter((120, 84, 30), (84, 56, 18), (0, 0, 0), engrave=False, rivets=False),
    "tooth": gp.plate_painter((236, 228, 206), (200, 190, 168), (0, 0, 0), engrave=False, rivets=False),
    "goldscale": scale_painter((156, 112, 42), (232, 196, 110), (80, 52, 16), S=7),
    "ember": orb_painter((255, 230, 170), (255, 110, 30), (150, 20, 10)),
    "eye": gp.glow_painter((255, 150, 40), (255, 220, 140)),
}


def nolight(painter):
    class _N:
        def __setitem__(self, k, v): pass
    q = lambda px, gx, *a: painter(px, _N(), *a)
    return q


for k in ("gold", "gold2", "tooth"):
    P[k] = nolight(P[k])


# ------------------------------------------------------------------ the model
def seg(p0, p1, w, d, mat):
    """A cube running from p0 to p1 (each (y, z), at x = 0): w wide (x), d deep, turned to follow the line."""
    dy, dz = p1[0] - p0[0], p1[1] - p0[1]
    L = math.hypot(dy, dz) + 0.15
    ang = math.degrees(math.atan2(-dz, dy))              # +x rotation tips the top toward -z
    return cube([-w / 2, p0[0], p0[1] - d / 2], [w, L, d], mat, rot=[ang, 0, 0], pivot=[0, p0[0], p0[1]])


def chain(pts, w0, w1, d0, d1, mats):
    out, n = [], len(pts) - 1
    for k in range(n):
        f = k / max(1, n - 1)
        out.append(seg(pts[k], pts[k + 1], w0 + (w1 - w0) * f, d0 + (d1 - d0) * f, mats[k % len(mats)]))
    return out


def arc(y, z, length, a0, a1, n):
    """Points along a curve starting at (y, z) heading a0 degrees from +y toward -z, turning to a1."""
    pts = [(y, z)]
    for k in range(n):
        a = math.radians(a0 + (a1 - a0) * (k + 0.5) / n)
        y, z = y + length / n * math.cos(a), z - length / n * math.sin(a)
        pts.append((y, z))
    return pts


def dragon_fang():
    B = [bone("root", None, [0, 0, 0], [])]
    def sym(cs): return cs + mirror_x(cs)
    # the grip: wound in crimson cord, with gold collars at each end
    grip = [cube([-0.8, -3.4, -0.9], [1.6, 6.6, 1.8], "wrap"),
            cube([-0.95, 2.6, -1.05], [1.9, 0.6, 2.1], "gold2"), cube([-0.95, -3.9, -1.05], [1.9, 0.6, 2.1], "gold2")]
    B.append(bone("grip", "root", [0, 0, 0], grip))
    # the pommel: a gold cap set with an ember gem, tapering into a claw that hooks toward the spine
    pom = [cube([-0.9, -4.8, -1.05], [1.8, 0.9, 2.1], "gold"), cube([-0.6, -4.7, -1.3], [1.2, 0.7, 0.3], "ember"),
           cube([-0.6, -4.7, 1.0], [1.2, 0.7, 0.3], "ember")]
    pom += chain(arc(-4.6, 0.0, 3.0, 180, 235, 4), 1.1, 0.35, 1.1, 0.35, ["gold", "gold2"])
    B.append(bone("pommel", "root", [0, -4, 0], pom))
    # the guard: a crescent of dark gold - curling down toward the grip at the edge side, sweeping up at the spine -
    # with an ember gem on each face
    guard = [cube([-1.0, 3.2, -1.7], [2.0, 1.3, 3.4], "gold"), cube([-0.75, 4.4, -1.4], [1.5, 0.4, 2.8], "gold2")]
    guard += chain(arc(3.85, -1.5, 3.2, 95, 185, 4), 1.2, 0.4, 0.9, 0.35, ["gold", "gold2"])    # out and curling down
    guard += chain(arc(3.85, 1.5, 3.6, -80, -10, 4), 1.2, 0.4, 0.9, 0.35, ["gold", "gold2"])    # out and sweeping up
    guard += sym([cube([0.95, 3.45, -0.55], [0.25, 0.85, 1.1], "ember")])
    B.append(bone("guard", "root", [0, 3.2, 0], guard))
    # the blade, in slices up its length: a core centred on a line that lifts toward the spine near the point, and on
    # each side of it a wavy edge - flames rising slowly and breaking sharply, leaning toward the point
    blade, N, LEN, Y0 = [], 48, 22.0, 4.5
    def cz(t): return 2.4 * t ** 3                       # its centre line
    def hw(t): return 1.25 * (1 - t) ** 0.6 + 0.1         # half the core's depth, tapering
    def th(t): return 0.85 * (1 - t) ** 0.5 + 0.18       # its thickness
    def flame(t, k, amp):
        w = (t * k) % 1.0
        sh = (w / 0.8) ** 1.6 if w < 0.8 else (1 - w) / 0.2
        return amp * sh
    for i in range(N):
        t0, t1 = i / N, (i + 1) / N
        t = (t0 + t1) / 2
        y = Y0 + LEN * t0
        L = LEN / N + 0.12
        ang = -math.degrees(math.atan((cz(t1) - cz(t0)) / (LEN / N)))
        piv = [0, y, cz(t)]
        c = cube([-th(t) / 2, y, cz(t) - hw(t)], [th(t), L, 2 * hw(t)], "blade", rot=[ang, 0, 0], pivot=piv)
        c["t"] = (t0, t1)
        blade.append(c)
        taper = min(1.0, t / 0.08) * (1 - t) ** 0.55
        fe = 0.35 + flame(t, 6.5, 1.25) * taper          # the edge (-z)
        fs = 0.2 + flame(t + 0.07, 4.0, 1.1) * taper * (1 if t > 0.2 else 0)   # the spine (+z)
        tw = th(t) * 0.55
        blade.append(cube([-tw / 2, y, cz(t) - hw(t) - fe], [tw, L, fe + 0.05], "wave", rot=[ang, 0, 0], pivot=piv))
        blade.append(cube([-tw / 2, y, cz(t) + hw(t) - 0.05], [tw, L, fs + 0.05], "wave", rot=[ang, 0, 0], pivot=piv))
    tp = cube([-0.1, Y0 + LEN - 0.2, cz(1) - 0.15], [0.2, 1.4, 0.3], "wave", rot=[-12, 0, 0], pivot=[0, Y0 + LEN, cz(1)])
    blade.append(tp)
    # the spike off the spine by the hilt, swept back and down toward the grip
    sp = arc(Y0 + 2.2, cz(0.1) + hw(0.1) + 0.1, 4.2, -100, -160, 4)
    blade += chain(sp, 0.5, 0.25, 1.3, 0.35, ["wave"])
    B.append(bone("blade", "root", [0, Y0, 0], blade))
    end_y, end_z = Y0 + LEN + 1.2, cz(1)
    # embers drifting off the point (they slowly turn - animation "idle")
    sparks = [cube([0.6 * math.cos(k * 2.1), end_y - 3 - k * 1.6, end_z + 0.6 * math.sin(k * 2.1)], [0.25, 0.25, 0.25], "ember") for k in range(3)]
    B.append(bone("sparks", "blade", [0, end_y - 4, end_z], sparks))
    return geo("dragon_fang", B)


def anims():
    return {"format_version": "1.8.0", "animations": {
        "animation.dragon_fang.idle": {"loop": True, "animation_length": 4.0, "bones": {
            "sparks": {"rotation": kf({0: [0, 0, 0], 4.0: [0, 360, 0]}), "position": kf({0: [0, 0, 0], 2.0: [0, 0.6, 0], 4.0: [0, 0, 0]})}}}}}


def build():
    model = dragon_fang()
    bones = model["minecraft:geometry"][0]["bones"]
    size, faces = gp.layout(bones, 16, sheet=1024)   # a high-density texture: 16 pixels to a model pixel
    for fc in faces:   # where each fang section sits along the whole fang (for the colour and the veins)
        t = fc["cube"].get("t")
        if t: fc["t0"], fc["t1"] = t
    img, glow = gp.paint(faces, size, P)
    os.makedirs(os.path.join(ROOT, "geo", "item"), exist_ok=True)
    os.makedirs(os.path.join(ROOT, "animations", "item"), exist_ok=True)
    gp.clean(bones)
    for b in bones:
        for c in b.get("cubes", []): c.pop("t", None)
    model["minecraft:geometry"][0]["description"]["texture_width"] = size
    model["minecraft:geometry"][0]["description"]["texture_height"] = size
    json.dump(model, open(os.path.join(ROOT, "geo", "item", "dragon_fang.geo.json"), "w"))
    img.save(os.path.join(ROOT, "textures", "item", "dragon_fang.png"))
    glow.save(os.path.join(ROOT, "textures", "item", "dragon_fang_glowmask.png"))
    json.dump(anims(), open(os.path.join(ROOT, "animations", "item", "dragon_fang.animation.json"), "w"), indent=1)
    # the item model: drawn by its GeckoLib renderer everywhere (the hotbar too), held point-up like a knife
    json.dump({"parent": "builtin/entity", "display": {
        "thirdperson_righthand": {"rotation": [20, 0, 0], "translation": [0, 3.5, 1.5], "scale": [0.75, 0.75, 0.75]},
        "thirdperson_lefthand": {"rotation": [20, 0, 0], "translation": [0, 3.5, 1.5], "scale": [0.75, 0.75, 0.75]},
        "firstperson_righthand": {"rotation": [0, -90, 15], "translation": [1.0, 4.0, 1.0], "scale": [0.6, 0.6, 0.6]},
        "firstperson_lefthand": {"rotation": [0, 90, -15], "translation": [1.0, 4.0, 1.0], "scale": [0.6, 0.6, 0.6]},
        "gui": {"rotation": [0, 90, -38], "translation": [1.5, -1.5, 0], "scale": [0.55, 0.55, 0.55]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.45, 0.45, 0.45]},
        "fixed": {"rotation": [0, 90, -38], "translation": [1.5, -1.5, 0], "scale": [0.55, 0.55, 0.55]}}},
        open(os.path.join(ROOT, "models", "item", "dragon_fang.json"), "w"), indent=1)
    return model, img, glow


def preview(out):
    from PIL import Image
    model, img, glow = build()
    g = json.load(open(os.path.join(ROOT, "geo", "item", "dragon_fang.geo.json")))["minecraft:geometry"][0]
    an = anims()["animations"]["animation.dragon_fang.idle"]
    row = Image.new("RGB", (1600, 900))
    for i, (yaw, label) in enumerate(((90, "side"), (40, "three-quarter"), (-90, "other side"), (180, "edge on"))):
        row.paste(gp.render(g, an, 0, img, glow, 450, yaw, label, ground=-8, scale_div=44), (i * 400 - 25, 0))
    row.paste(gp.render(g, an, 0, img, glow, 450, 60, "close: the guard", ground=-2, scale_div=16), (0, 450))
    row.paste(gp.render(g, an, 0, img, glow, 450, 110, "close: the blade", ground=6, scale_div=20), (450, 450))
    row.paste(gp.render(g, an, 0, img, glow, 450, -60, "close: the pommel", ground=-7.5, scale_div=14), (900, 450))
    row.save(out)
    print("wrote", out)


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "preview": preview(sys.argv[2])
    else: build(); print("dragon fang written")
