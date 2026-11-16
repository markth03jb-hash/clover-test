"""The Dragon Fang (an original design) - a GeckoLib item model, painted in the shadow models' style.

A single curved fang, as if pulled from a dragon's jaw: ivory at the root, deepening to crimson toward the point, with
ember veins glowing through it. Its root is clamped in the jaws of a small dark-gold dragon's head (the guard) - a snout,
two jaws with teeth, ember eyes, horns swept back along the grip. The grip is wound in dark red scaled hide; the pommel is
a dragon's claw clutching a glowing ember orb.

Model space: pixels, the blade pointing up (+y), the edge (the inside of the curve) facing -z. Origin at the middle of
the grip, where the hand holds it.

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
def fang_painter(seed=7):
    """Ivory at the root, through blood red to a deep crimson point; ember veins wind up through it and glow; the broad
    faces are lit down their middle (a rounded tooth), the edges catch a pale highlight."""
    IVORY, RED, DEEP = (236, 226, 198), (190, 30, 34), (96, 8, 16)
    VEIN, VEIN_GLOW = (255, 96, 40), (255, 140, 70)

    def p(px, gx, x0, y0, w, h, face, rnd, fc=None):
        broad = face in ("east", "west")
        along_x = False if broad else w > h   # on the broad faces the fang always runs up the texture
        L, Wd = (w, h) if along_x else (h, w)
        g0, g1 = (fc.get("t0", 0.0), fc.get("t1", 1.0)) if fc else (0.0, 1.0)
        r2 = random.Random(seed + (1 if face == "west" else 0))
        ph = [r2.uniform(0, 6.28) for _ in range(4)]
        for j in range(Wd):
            for i in range(L):
                u, a = i / max(1, L - 1), j / max(1, Wd - 1)
                t = g0 + (g1 - g0) * (1 - u if not along_x else u)   # 0 root, 1 point
                c = gp.mix(IVORY, RED, min(1, t / 0.55)) if t < 0.55 else gp.mix(RED, DEEP, (t - 0.55) / 0.45)
                k = 0.78 + 0.32 * (1 - abs(a - 0.5) * 2) if broad else 0.62   # rounded: lit down the middle; the edges darker
                c = gp.lit(c, k * (0.97 + 0.05 * rnd.random()))
                gl = None
                if broad and t > 0.08:
                    centre = 0.5 + 0.18 * math.sin(t * 9 + ph[0]) + 0.06 * math.sin(t * 23 + ph[1])
                    width = 0.07 * (1 - t) + 0.03
                    if abs(a - centre) < width: c, gl = VEIN, VEIN_GLOW
                    for n in (2, 3):   # branches
                        t0 = 0.2 + 0.22 * (n - 2)
                        if t0 < t < t0 + 0.25:
                            k2 = (t - t0) / 0.25
                            bc = centre + (1 if n == 2 else -1) * 0.32 * k2
                            if abs(a - bc) < 0.05 * (1 - k2) + 0.02: c, gl = VEIN, VEIN_GLOW
                if broad and (a < 0.06 or a > 0.94): c = gp.mix(c, (255, 236, 220), 0.4)   # a pale highlight along the edges
                xx, yy = (i, j) if along_x else (j, i)
                px[x0 + xx, y0 + yy] = c + (255,)
                if gl: gx[x0 + xx, y0 + yy] = gl + (255,)
    p.wants_face = True
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
    "fang": fang_painter(),
    "gold": gp.plate_painter((168, 122, 46), (110, 74, 24), (0, 0, 0), engrave=False, rivets=False),
    "gold2": gp.plate_painter((120, 84, 30), (84, 56, 18), (0, 0, 0), engrave=False, rivets=False),
    "tooth": gp.plate_painter((236, 228, 206), (200, 190, 168), (0, 0, 0), engrave=False, rivets=False),
    "hide": scale_painter((96, 20, 24), (170, 52, 46), (40, 6, 10), S=5),
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
def dragon_fang():
    B = [bone("root", None, [0, 0, 0], [])]
    def sym(cs): return cs + mirror_x(cs)
    # the grip: wound in scaled hide, slightly waisted, with gold collars at each end
    grip = [cube([-0.85, -3.4, -0.85], [1.7, 6.6, 1.7], "hide"),
            cube([-1.0, 2.6, -1.0], [2.0, 0.7, 2.0], "gold2"), cube([-1.0, -3.9, -1.0], [2.0, 0.7, 2.0], "gold2")]
    B.append(bone("grip", "root", [0, 0, 0], grip))
    # the pommel: a claw of four talons clutching an ember orb
    pom = [cube([-1.1, -6.6, -1.1], [2.2, 2.2, 2.2], "ember"), cube([-0.75, -4.4, -0.75], [1.5, 0.6, 1.5], "gold")]
    for sx, sz in ((1, 0), (-1, 0), (0, 1), (0, -1)):   # out and down, then curling in under the orb
        x, z = sx * 1.0, sz * 1.0
        out_r = [sz * 22, 0, sx * 22]                    # tilts its foot outward (see the renderer's axes)
        in_r = [-sz * 55, 0, -sx * 55]
        pom.append(cube([x - 0.28, -5.8, z - 0.28], [0.56, 1.5, 0.56], "gold", rot=out_r, pivot=[x, -4.3, z]))
        fx, fz = x + sx * 0.55, z + sz * 0.55
        pom.append(cube([fx - 0.22, -7.0, fz - 0.22], [0.44, 1.3, 0.44], "gold2", rot=in_r, pivot=[fx, -5.75, fz]))
    B.append(bone("pommel", "root", [0, -4, 0], pom))
    # the guard: a dragon's head, its snout forward (-z), its jaws clamped round the fang's root
    head = [
        cube([-1.5, 3.2, -1.7], [3.0, 2.1, 3.2], "goldscale"),               # the skull
        cube([-1.15, 3.45, -3.8], [2.3, 1.55, 2.3], "goldscale"),            # the snout, in two steps,
        cube([-0.85, 3.5, -5.1], [1.7, 1.3, 1.5], "goldscale"),              # tapering to the nose
        cube([-0.9, 3.25, -5.3], [1.8, 0.4, 3.8], "gold2"),                  # the upper jaw's lip
        cube([-0.95, 2.55, -4.7], [1.9, 0.7, 3.2], "goldscale", rot=[14, 0, 0], pivot=[0, 3.1, -1.5]),   # the lower jaw, open a little
        cube([-0.55, 4.75, -5.15], [1.1, 0.35, 0.6], "gold2"),               # the nostrils' ridge
        cube([-0.3, 5.25, -2.4], [0.6, 0.6, 3.8], "gold2"),                  # a spined ridge down the skull...
        cube([-0.2, 5.8, -0.6], [0.4, 0.6, 0.5], "gold2"), cube([-0.2, 5.8, 0.6], [0.4, 0.5, 0.5], "gold2"),   # ...with little spines
    ]
    head += sym([
        cube([0.75, 4.25, -2.35], [0.6, 0.45, 0.6], "eye"),                  # ember eyes...
        cube([0.6, 4.75, -2.6], [0.95, 0.4, 1.1], "gold2", rot=[0, 0, -12], pivot=[0.6, 4.9, -2]),   # ...under heavy brows
        cube([0.85, 4.6, 0.2], [0.75, 0.75, 2.4], "gold", rot=[18, -16, 0], pivot=[1.2, 5.0, 0.4]),   # horns: thick at the root...
        cube([0.95, 5.3, 2.3], [0.5, 0.5, 2.0], "gold", rot=[42, -20, 0], pivot=[1.2, 5.5, 2.4]),     # ...sweeping back...
        cube([1.02, 6.4, 3.7], [0.32, 0.32, 1.3], "gold2", rot=[70, -24, 0], pivot=[1.2, 6.5, 3.8]),  # ...curling up at the tips
        cube([1.3, 3.2, -1.4], [0.5, 1.7, 2.4], "goldscale"),                # the cheeks, gripping the fang's root
        cube([1.4, 3.5, 0.5], [0.4, 0.4, 1.4], "gold2", rot=[20, -30, 0], pivot=[1.5, 3.7, 0.6]),   # a frill spine at the jaw
    ])
    for k in range(3):   # teeth, upper and lower, biting on the fang
        head += sym([cube([0.55 + k * 0.28, 3.05, -3.8 + k * 0.7], [0.22, 0.45, 0.22], "tooth"),
                     cube([0.55 + k * 0.28, 2.95, -3.5 + k * 0.7], [0.22, 0.4, 0.22], "tooth", rot=[14, 0, 0], pivot=[0, 3.1, -1.4])])
    B.append(bone("guard", "root", [0, 3.2, 0], head))
    # the fang: a long curved tooth rising out of the jaws, curving toward its edge (-z), round at the root and tapering
    # to a hooked point. Built from sections along an arc; each is a broad core plus a narrower ridge so it reads round.
    fang, N = [], 9
    R, sweep = 26.0, math.radians(34)
    z0, y0 = 0.4, 4.6
    for i in range(N):
        a0, a1 = sweep * i / N, sweep * (i + 1) / N
        pa = (z0 - R * (1 - math.cos(a0)), y0 + R * math.sin(a0))
        pb = (z0 - R * (1 - math.cos(a1)), y0 + R * math.sin(a1))
        L = math.hypot(pb[0] - pa[0], pb[1] - pa[1]) + 0.35   # a little overlap: no gaps at the joins
        t = (i + 0.5) / N
        wd = 3.0 * (1 - t) ** 0.8 + 0.3        # its depth (edge to spine), tapering
        th = 1.4 * (1 - t) ** 0.7 + 0.2        # its thickness
        ang = math.degrees((a0 + a1) / 2)      # leaning forward (-z) along the curve (+x rotation tips the top toward -z)
        piv = [0, pa[1], pa[0]]
        c1 = cube([-th / 2, pa[1] - 0.15, pa[0] - wd * 0.62], [th, L, wd], "fang", rot=[ang, 0, 0], pivot=piv)
        c1["t"] = (i / N, (i + 1) / N)
        fang.append(c1)
    # the hooked point
    end = (z0 - R * (1 - math.cos(sweep)), y0 + R * math.sin(sweep))
    tip = cube([-0.12, end[1] - 0.1, end[0] - 0.5], [0.24, 1.4, 0.5], "fang", rot=[math.degrees(sweep) + 25, 0, 0], pivot=[0, end[1], end[0]])
    tip["t"] = (0.97, 1.0)
    fang.append(tip)
    B.append(bone("fang", "root", [0, 4.6, 0.4], fang))
    # embers drifting off the point (they slowly turn - animation "idle")
    sparks = [cube([0.6 * math.cos(k * 2.1), end[1] - 2 - k * 1.6, end[0] + 0.6 * math.sin(k * 2.1)], [0.25, 0.25, 0.25], "ember") for k in range(3)]
    B.append(bone("sparks", "fang", [0, end[1] - 3, end[0]], sparks))
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
        row.paste(gp.render(g, an, 0, img, glow, 450, yaw, label, ground=-7, scale_div=34), (i * 400 - 25, 0))
    row.paste(gp.render(g, an, 0, img, glow, 450, 60, "close: the guard", ground=-2, scale_div=16), (0, 450))
    row.paste(gp.render(g, an, 0, img, glow, 450, 110, "close: the fang", ground=6, scale_div=20), (450, 450))
    row.paste(gp.render(g, an, 0, img, glow, 450, -60, "close: the pommel", ground=-7.5, scale_div=14), (900, 450))
    row.save(out)
    print("wrote", out)


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "preview": preview(sys.argv[2])
    else: build(); print("dragon fang written")
