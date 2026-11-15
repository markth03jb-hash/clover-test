"""The Shadow Grimoire's Market wares (original designs) - item models, textures and icons.

  nightfang - a short dagger: a single-edged black blade that curves forward to a sharp point, a thin line of blue
              light along its edge, a small hooked guard, a grip wrapped in black cord with blue bands, a blue gem at
              the pommel. Bought in the Market; it carries Thousand Cuts.

python3 tools/shadow_items.py            (writes the models and textures)
python3 tools/shadow_items.py preview OUT.png   (also renders a preview of the 3D model from a few sides)
"""
import json
import os
import sys
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "iron_spell_grimoire")
MD = os.path.join(ROOT, "models", "item")
TEX = os.path.join(ROOT, "textures", "item")

# ------------------------------------------------------------------ the texture (32x32; model uv units are 1/16 of it)
K, K2, K3 = (14, 15, 22), (24, 26, 36), (40, 44, 58)          # black steel, a shade lighter, the flat's sheen
EDGE, EDGE2 = (90, 180, 255), (190, 230, 255)                  # the blue edge light
CORD, CORD2, BAND = (18, 18, 24), (30, 30, 40), (50, 110, 220)
GEM, GEM2 = (60, 140, 255), (200, 235, 255)
T = Image.new("RGBA", (32, 32), (0, 0, 0, 0)); px = T.load()
for y in range(32):   # the blade's flat (uv 0-4 across, 0-16 along): the edge on the left, the spine on the right
    for x in range(0, 8):
        if x == 0: c = EDGE2
        elif x == 1: c = EDGE
        elif x == 7: c = K2
        else: c = K3 if (x == 3 and y % 5 != 0) else K
        px[x, y] = c + (255,)
for y in range(32):   # the blade's back and edge strip (uv 4-5)
    for x in range(8, 10): px[x, y] = (EDGE if y % 2 else EDGE2) + (255,)
for y in range(32):   # the grip (uv 6-10): black cord wound diagonally, two blue bands
    for x in range(12, 20):
        c = CORD2 if (x + y) % 4 < 2 else CORD
        if y in (8, 9, 22, 23): c = BAND
        px[x, y] = c + (255,)
for y in range(8):    # the guard and the pommel's steel (uv 11-13, 0-4)
    for x in range(22, 26): px[x, y] = (K2 if (x + y) % 3 else K3) + (255,)
for y in range(8, 16):   # the gem (uv 11-13, 4-8)
    for x in range(22, 26): px[x, y] = (GEM2 if (x == 23 and y < 11) else GEM) + (255,)
T.save(os.path.join(TEX, "nightfang.png"))


# ------------------------------------------------------------------ the model
def face(u0, v0, u1, v1, rot=0):
    f = {"uv": [u0, v0, u1, v1], "texture": "#0"}
    if rot: f["rotation"] = rot
    return f


def el(fr, to, faces, rot=None, name=None):
    e = {"from": [round(v, 4) for v in fr], "to": [round(v, 4) for v in to], "faces": faces}
    if rot: e["rotation"] = rot
    if name: e["name"] = name
    return e


GRIP = lambda: {d: face(6, 0, 10, 16) for d in ("north", "south", "east", "west")} | {"up": face(6, 0, 10, 2), "down": face(6, 0, 10, 2)}
STEEL = lambda: {d: face(11, 0, 13, 4) for d in ("north", "south", "east", "west", "up", "down")}
GEMF = lambda: {d: face(11, 4, 13, 8) for d in ("north", "south", "east", "west", "up", "down")}
els = [
    el([7.45, 3.4, 7.45], [8.55, 4.4, 8.55], GEMF(), name="pommel_gem"),
    el([7.6, 4.2, 7.6], [8.4, 4.6, 8.4], STEEL(), name="pommel"),
    el([7.5, 4.6, 7.5], [8.5, 8.6, 8.5], GRIP(), name="grip"),                     # the grip's middle sits at the model's centre
    el([7.4, 8.6, 6.6], [8.6, 9.1, 9.6], STEEL(), name="guard"),                   # a short guard...
    el([7.55, 7.7, 6.6], [8.45, 8.6, 7.1], STEEL(), name="guard_hook"),            # ...hooking down at the front
    el([7.55, 9.1, 9.1], [8.45, 9.6, 9.6], STEEL(), name="guard_back"),
]
# the blade: a stack of thin sections along one curve - single-edged, the edge (front, low z) sweeping forward and up to
# meet the spine at a sharp point; it thins toward the point.
B0, B1 = 9.6, 17.6
def front(y): t = (y - B0) / (B1 - B0); return 7.0 - 0.9 * t * t          # the edge curves forward
def back(y):
    t = (y - B0) / (B1 - B0)
    return 8.9 - 0.2 * t - (2.6 * (t - 0.62) / 0.38) ** 1.6 if t > 0.62 else 8.9 - 0.2 * t   # the spine drops to the point near the end
def vv(y): return round(16 * (y - B0) / (B1 - B0), 3)
N = 22
for i in range(N):
    y0, y1 = B0 + (B1 - B0) * i / N, B0 + (B1 - B0) * (i + 1) / N
    ym = (y0 + y1) / 2
    z0, z1 = front(ym), back(ym)
    if z1 - z0 < 0.08: continue
    th = 0.34 * (1 - 0.55 * (ym - B0) / (B1 - B0))
    faces = {"east": face(0, vv(y0), 4, vv(y1)), "west": face(4, vv(y0), 0, vv(y1)), "north": face(4, vv(y0), 5, vv(y1)),
             "south": face(4, vv(y0), 5, vv(y1)), "up": face(4, 0, 5, 1), "down": face(4, 0, 5, 1)}
    els.append(el([8 - th / 2, y0, z0], [8 + th / 2, y1, z1], faces, name=f"blade{i}"))
model = {
    "credit": "Iron Spell Grimoire - the Nightfang (tools/shadow_items.py)",
    "textures": {"0": "iron_spell_grimoire:item/nightfang", "particle": "iron_spell_grimoire:item/nightfang"},
    "elements": els,
    "display": {   # held low and forward, point up, edge out - a quick knife grip
        "thirdperson_righthand": {"rotation": [25, 0, 0], "translation": [0, 1.5, 0.8], "scale": [0.85, 0.85, 0.85]},
        "thirdperson_lefthand": {"rotation": [25, 0, 0], "translation": [0, 1.5, 0.8], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, -90, 20], "translation": [1.13, 2.4, 1.13], "scale": [0.68, 0.68, 0.68]},
        "firstperson_lefthand": {"rotation": [0, 90, -20], "translation": [1.13, 2.4, 1.13], "scale": [0.68, 0.68, 0.68]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    },
}
json.dump(model, open(os.path.join(MD, "nightfang_3d.json"), "w"), indent=1)
json.dump({"parent": "minecraft:item/handheld", "loader": "forge:separate_transforms",
           "base": {"parent": "iron_spell_grimoire:item/nightfang_3d"},
           "perspectives": {k: {"parent": "minecraft:item/generated", "textures": {"layer0": "iron_spell_grimoire:item/nightfang_icon"}} for k in ("gui", "fixed", "ground")}},
          open(os.path.join(MD, "nightfang.json"), "w"), indent=1)

ICON = [   # k black steel, e blue edge, s sheen, c cord, b blue band, g guard, j gem
    "..............ee",
    ".............eke",
    "............ekks",
    "...........ekks.",
    "..........ekks..",
    ".........ekks...",
    "........ekks....",
    ".......ekks.....",
    "....g.ekks......",
    ".....gkks.......",
    "......gg........",
    ".....cbgg.......",
    "....cc...g......",
    "...bc...........",
    "..jc............",
    ".jj.............",
]
COL = {"k": K, "e": EDGE, "s": K3, "c": (52, 52, 66), "b": BAND, "g": (70, 76, 96), "j": GEM}
ic = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); ip = ic.load()
for y, row in enumerate(ICON):
    for x, ch in enumerate(row):
        if ch in COL: ip[x, y] = COL[ch] + (255,)
ip[15, 0] = EDGE2 + (255,); ip[14, 0] = EDGE2 + (255,)
ic.save(os.path.join(TEX, "nightfang_icon.png"))
def flask(name, liquid, liquid_hi):
    """A small stoppered flask of black glass, its liquid lit."""
    rows = [
        "......wwww......",
        "......wccw......",
        ".......gg.......",
        ".......gg.......",
        "......g..g......",
        ".....g....g.....",
        "....g......g....",
        "...gLLLLLLLLg...",
        "...gLHLLLLLLg...",
        "...gLHLLLLLLg...",
        "...gLLLLLLLLg...",
        "...gLLLLLLLLg...",
        "....gLLLLLLg....",
        ".....gggggg.....",
        "................",
        "................",
    ]
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); q = im.load()
    cmap = {"w": (110, 84, 56), "c": (150, 116, 76), "g": (60, 70, 96), "L": liquid, "H": liquid_hi}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in cmap: q[x, y] = cmap[ch] + (255,)
    for x in range(5, 11):   # dark glass at the shoulder
        q[x, 6 if x in (5, 10) else 7] = q[x, 6 if x in (5, 10) else 7]
    im.save(os.path.join(TEX, name + ".png"))
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "iron_spell_grimoire:item/" + name}}, open(os.path.join(MD, name + ".json"), "w"), indent=1)


flask("mana_tonic", (40, 110, 230), (170, 215, 255))
flask("shade_salve", (30, 150, 100), (150, 235, 190))
print("shadow items written")


# ------------------------------------------------------------------ a preview of the 3D model (reuses the geo renderer)
def preview(out):
    import geo_paint as gp
    sheet = T.resize((32, 32))
    cubes = []
    for e in els:
        fr, to = e["from"], e["to"]
        uv = {}
        for f, fc in e["faces"].items():
            u0, v0, u1, v1 = [c * 2 for c in fc["uv"]]
            uv[f] = {"uv": [min(u0, u1), min(v0, v1)], "uv_size": [abs(u1 - u0) or 1, abs(v1 - v0) or 1]}
        cubes.append({"origin": [fr[0] - 8, fr[1], fr[2] - 8], "size": [to[i] - fr[i] for i in range(3)], "uv": uv})
    geo = {"bones": [{"name": "root", "pivot": [0, 0, 0], "cubes": cubes}]}
    glow = Image.new("RGBA", sheet.size)
    row = Image.new("RGB", (1200, 400))
    for i, yaw in enumerate((90, 30, -60)):
        row.paste(gp.render(geo, None, 0, sheet, glow, 400, yaw, "", ground=2, scale_div=24), (i * 400, 0))
    big = ic.resize((128, 128), Image.NEAREST)
    row.paste(big, (1200 - 136, 8), big)
    row.save(out)
    print("wrote", out)


if len(sys.argv) > 2 and sys.argv[1] == "preview": preview(sys.argv[2])
