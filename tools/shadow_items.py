"""The Shadow Grimoire's Market wares (original designs) - item models, textures and icons.

  nightfang - a short dagger: a single-edged black blade that curves forward to a sharp point, a thin line of blue
              light along its edge, a small hooked guard, a grip wrapped in black cord with blue bands, a blue gem at
              the pommel. Bought in the Market; it carries Thousand Cuts.

python3 tools/shadow_items.py            (writes the models and textures)
python3 tools/shadow_items.py preview OUT.png [knife]   (also renders a preview of a knife's 3D model from a few sides)

  gloamthorn     - a stiletto: a long, narrow black spike with a blue-lit fuller, a ring pommel (Shadow Mark)
  hollowtooth    - a broad hooked blade, serrated, a hollow near the spine (Rend)
  duskglass_kris - a wavy blade of smoked black glass, light along its waves (Phantom Edges)
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
    el([7.5, 5.5, 7.5], [8.5, 6.3, 8.5], GEMF(), name="pommel_gem"),
    el([7.6, 6.2, 7.6], [8.4, 6.6, 8.4], STEEL(), name="pommel"),
    el([7.5, 6.6, 7.5], [8.5, 8.6, 8.5], GRIP(), name="grip"),                     # a short grip - a knife's, a hand's width
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
    "display": {   # held low and forward, point up, edge out - a quick knife grip; in the hotbar, the model itself, on the diagonal
        "gui": {"rotation": [90, 45, -90], "translation": [-2.4, -2.4, 0], "scale": [1.0, 1.0, 1.0]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
        "fixed": {"rotation": [90, 45, -90], "translation": [-2.4, -2.4, 0], "scale": [1.0, 1.0, 1.0]},
        "thirdperson_righthand": {"rotation": [25, 0, 0], "translation": [0, 0.5, 0.8], "scale": [0.85, 0.85, 0.85]},
        "thirdperson_lefthand": {"rotation": [25, 0, 0], "translation": [0, 0.5, 0.8], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, -90, 20], "translation": [1.13, 2.4, 1.13], "scale": [0.68, 0.68, 0.68]},
        "firstperson_lefthand": {"rotation": [0, 90, -20], "translation": [1.13, 2.4, 1.13], "scale": [0.68, 0.68, 0.68]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    },
}
json.dump(model, open(os.path.join(MD, "nightfang_3d.json"), "w"), indent=1)
json.dump({"parent": "iron_spell_grimoire:item/nightfang_3d"}, open(os.path.join(MD, "nightfang.json"), "w"), indent=1)   # the 3D model everywhere, the hotbar too

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


# ------------------------------------------------------------------ three more knives for the Market (original designs)
DISPLAY = model["display"]   # held and shown in the hotbar the same way as the Nightfang


def sheet(flat, strip, grip, steel, gem):
    """A 32x32 texture in the Nightfang's layout: blade flat (uv 0-4), edge strip (4-5), grip (6-10), steel (11-13, 0-4),
    gem (11-13, 4-8); each part painted by a function (x, y) -> (r, g, b)."""
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0)); q = im.load()
    for y in range(32):
        for x in range(0, 8): q[x, y] = flat(x, y) + (255,)
        for x in range(8, 10): q[x, y] = strip(x, y) + (255,)
        for x in range(12, 20): q[x, y] = grip(x, y) + (255,)
    for y in range(8):
        for x in range(22, 26): q[x, y] = steel(x, y) + (255,)
    for y in range(8, 16):
        for x in range(22, 26): q[x, y] = gem(x, y) + (255,)
    return im


def handle(y_top, grip_len, ring=False, gem=True):
    """Grip, pommel (a gem, or an open ring) and the base of a guard, the grip ending at y_top."""
    out = [el([7.5, y_top - grip_len, 7.5], [8.5, y_top, 8.5], GRIP(), name="grip")]
    yb = y_top - grip_len
    if ring:   # an open ring pommel
        out += [el([7.55, yb - 1.6, 7.0], [8.45, yb - 1.3, 9.0], STEEL()), el([7.55, yb - 0.3, 7.0], [8.45, yb, 9.0], STEEL()),
                el([7.55, yb - 1.6, 7.0], [8.45, yb, 7.3], STEEL()), el([7.55, yb - 1.6, 8.7], [8.45, yb, 9.0], STEEL())]
    else:
        out.append(el([7.6, yb - 0.4, 7.6], [8.4, yb, 8.4], STEEL()))
        if gem: out.append(el([7.5, yb - 1.2, 7.5], [8.5, yb - 0.4, 8.5], GEMF()))
    return out


def blade_faces(y0, y1, b0, b1):
    vv2 = lambda y: round(16 * (y - b0) / (b1 - b0), 3)
    return {"east": face(0, vv2(y0), 4, vv2(y1)), "west": face(4, vv2(y0), 0, vv2(y1)), "north": face(4, vv2(y0), 5, vv2(y1)),
            "south": face(4, vv2(y0), 5, vv2(y1)), "up": face(4, 0, 5, 1), "down": face(4, 0, 5, 1)}


def write_knife(name, elements, tex):
    tex.save(os.path.join(TEX, name + ".png"))
    m = {"credit": "Iron Spell Grimoire - the " + name + " (tools/shadow_items.py)",
         "textures": {"0": "iron_spell_grimoire:item/" + name, "particle": "iron_spell_grimoire:item/" + name},
         "elements": elements, "display": DISPLAY}
    json.dump(m, open(os.path.join(MD, name + ".json"), "w"), indent=1)


KNIVES = {}

# the Gloamthorn: a stiletto - a long, narrow, square-sectioned black spike with a blue-lit fuller down each face, a
# small square guard, a short grip and an open ring for a pommel
g_els = handle(8.6, 2.2, ring=True)
g_els += [el([7.0, 8.6, 7.0], [9.0, 9.1, 9.0], STEEL(), name="guard"), el([7.3, 9.1, 7.3], [8.7, 9.5, 8.7], STEEL())]
GB0, GB1, n = 9.5, 19.2, 18
for i in range(n):
    y0, y1 = GB0 + (GB1 - GB0) * i / n, GB0 + (GB1 - GB0) * (i + 1) / n
    w = 0.95 * (1 - 0.9 * ((y0 + y1) / 2 - GB0) / (GB1 - GB0))
    g_els.append(el([8 - w / 2, y0, 8 - w / 2], [8 + w / 2, y1, 8 + w / 2], blade_faces(y0, y1, GB0, GB1), name=f"spike{i}"))
KNIVES["gloamthorn"] = (g_els, sheet(lambda x, y: (70, 160, 255) if x in (3, 4) and y % 7 else ((26, 28, 38) if x in (0, 7) else (14, 15, 22)),
                                     lambda x, y: (40, 44, 58), lambda x, y: (48, 30, 22) if (x + y) % 4 < 2 else (34, 22, 16) if y not in (6, 7, 24, 25) else (150, 156, 170),
                                     lambda x, y: (120, 126, 140) if (x + y) % 3 else (90, 96, 110), lambda x, y: (150, 210, 255)))

# the Hollowtooth: a broad, hooked blade, serrated along its edge with blue light between the teeth, a hollow cut through
# it near the spine, a heavy guard and a grip with blue bands
h_els = handle(8.6, 2.4)
h_els += [el([7.3, 8.6, 6.0], [8.7, 9.3, 10.0], STEEL(), name="guard"), el([7.4, 8.0, 5.8], [8.6, 8.8, 6.4], STEEL())]
HB0, HB1, n = 9.3, 17.6, 16
for i in range(n):
    y0, y1 = HB0 + (HB1 - HB0) * i / n, HB0 + (HB1 - HB0) * (i + 1) / n
    t = ((y0 + y1) / 2 - HB0) / (HB1 - HB0)
    zf = 6.4 - 1.6 * max(0, t - 0.55) ** 1.4 * 4            # the edge, hooking forward near the tip
    zb = 9.6 - 3.4 * max(0, t - 0.45) ** 1.2 * 1.8          # the spine sweeping down into the hook
    if zb - zf < 0.15: continue
    if 0.18 < t < 0.42:   # the hollow near the spine
        h_els.append(el([7.82, y0, zf], [8.18, y1, zb - 1.3], blade_faces(y0, y1, HB0, HB1), name=f"blade{i}"))
        h_els.append(el([7.82, y0, zb - 0.5], [8.18, y1, zb], blade_faces(y0, y1, HB0, HB1)))
    else:
        h_els.append(el([7.82, y0, zf], [8.18, y1, zb], blade_faces(y0, y1, HB0, HB1), name=f"blade{i}"))
    if i % 2 == 0 and t < 0.8:   # a tooth
        h_els.append(el([7.85, y0, zf - 0.45], [8.15, y0 + 0.35, zf], STEEL(), name=f"tooth{i}"))
KNIVES["hollowtooth"] = (h_els, sheet(lambda x, y: (90, 180, 255) if x == 0 and y % 2 else ((24, 26, 36) if x > 5 else (14, 15, 22)),
                                      lambda x, y: (90, 180, 255) if y % 3 == 0 else (20, 22, 30), lambda x, y: (50, 110, 220) if y in (5, 6, 25, 26) else ((30, 30, 40) if (x + y) % 4 < 2 else (18, 18, 24)),
                                      lambda x, y: (24, 26, 36) if (x + y) % 3 else (40, 44, 58), lambda x, y: (60, 140, 255)))

# the Duskglass Kris: a wavy blade like smoked black glass, light running along its waves, a wide asymmetric guard
# sweeping forward at its base, and a carved black grip
k_els = handle(8.6, 2.4)
k_els += [el([7.45, 8.6, 5.6], [8.55, 9.4, 9.6], STEEL(), name="guard"), el([7.5, 9.4, 5.6], [8.5, 9.9, 6.6], STEEL()), el([7.5, 9.0, 5.1], [8.5, 9.6, 5.7], STEEL())]
import math as _m
KB0, KB1, n = 9.4, 18.4, 24
for i in range(n):
    y0, y1 = KB0 + (KB1 - KB0) * i / n, KB0 + (KB1 - KB0) * (i + 1) / n
    t = ((y0 + y1) / 2 - KB0) / (KB1 - KB0)
    zc = 8.0 + 0.55 * _m.sin(t * _m.pi * 5) * (1 - 0.6 * t)   # five waves, gentler toward the point
    w = 1.9 * (1 - 0.92 * t)
    k_els.append(el([7.83, y0, zc - w / 2], [8.17, y1, zc + w / 2], blade_faces(y0, y1, KB0, KB1), name=f"wave{i}"))
KNIVES["duskglass_kris"] = (k_els, sheet(lambda x, y: (110, 190, 255) if (x + (y // 3)) % 6 == 0 else ((26, 36, 66) if x in (2, 3, 4, 5) else (16, 20, 38)),
                                         lambda x, y: (90, 170, 255), lambda x, y: (14, 14, 18) if (x * 2 + y) % 5 else (40, 60, 110),
                                         lambda x, y: (30, 34, 48) if (x + y) % 3 else (60, 90, 160), lambda x, y: (40, 90, 220) if (x + y) % 2 else (120, 190, 255)))

for name, (els_k, tex_k) in KNIVES.items():
    write_knife(name, els_k, tex_k)


# ------------------------------------------------------------------ a preview of the 3D model (reuses the geo renderer)
def preview(out, which="nightfang"):
    import geo_paint as gp
    global els, ic
    if which != "nightfang":
        els, sheet_img = KNIVES[which]
    else:
        sheet_img = T
    sheet = sheet_img.resize((32, 32))
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
    if which == "nightfang":
        big = ic.resize((128, 128), Image.NEAREST)
        row.paste(big, (1200 - 136, 8), big)
    row.save(out)
    print("wrote", out)


if len(sys.argv) > 2 and sys.argv[1] == "preview": preview(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else "nightfang")
