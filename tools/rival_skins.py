"""The rivals' skins (64 x 64, the player layout) - painted here, original designs, shaded like cloth, leather, steel
and skin rather than flat colour:

  seren      Seren Vale: a white duelling coat with gold trim over pale blue, silver hair in a long tail, grey eyes
  brannoch   Brannoch Tor: dark plate over mail, a red tabard with a black tower, a broad scarred face and a russet beard
  legion     his Legionnaires: a steel half-helm, mail and the same red tabard
  alder      Alder Bellmar: a slate-blue jacket with a bronze bell clasp, sandy hair, a sword belt
  wren       Wren Bellmar: a deep-blue robe with bell embroidery, sandy hair in a braid
  mira       Mira Hollowell: a burgundy merchant's coat with brass buttons, a moss-green waistcoat, copper hair

python3 tools/rival_skins.py [OUT_DIR]   (default: the mod's textures/entity)
"""
import os, sys, math, random
from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/iron_spell_grimoire/textures/entity")

# the player layout: part -> (u, v, w, h, d), and its outer layer
PARTS = {"head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8), "body": (16, 16, 8, 12, 4), "jacket": (16, 32, 8, 12, 4),
         "rarm": (40, 16, 4, 12, 4), "rsleeve": (40, 32, 4, 12, 4), "larm": (32, 48, 4, 12, 4), "lsleeve": (48, 48, 4, 12, 4),
         "rleg": (0, 16, 4, 12, 4), "rpants": (0, 32, 4, 12, 4), "lleg": (16, 48, 4, 12, 4), "lpants": (0, 48, 4, 12, 4)}


def faces(part, slim=False):
    """Each face's rectangle (x, y, w, h) on the sheet: top, bottom, right, front, left, back."""
    u, v, w, h, d = PARTS[part]
    if slim and part in ("rarm", "rsleeve", "larm", "lsleeve"): w = 3
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def jitter(c, r, amt): return tuple(max(0, min(255, int(c[i] + r.uniform(-amt, amt)))) for i in range(3)) + (255,)
def shade(c, k): return tuple(max(0, min(255, int(c[i] * k))) for i in range(3)) + ((c[3],) if len(c) > 3 else (255,))
def mix(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


class Skin:
    def __init__(self, seed, slim):
        self.im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.r = random.Random(seed)
        self.slim = slim

    def fill(self, part, col, amt=10, faces_only=None, grain=None):
        """Fill a part with a colour: lit from above, a little noise, darker at the bottom and the back."""
        light = {"top": 1.12, "bottom": 0.7, "front": 1.0, "back": 0.86, "right": 0.92, "left": 0.92}
        for f, (x, y, w, h) in faces(part, self.slim).items():
            if faces_only and f not in faces_only: continue
            for i in range(w):
                for j in range(h):
                    k = light[f]
                    if f in ("front", "back", "right", "left"): k *= 1.04 - 0.12 * (j / max(1, h - 1))   # lit from above
                    if grain == "v" and i % 2 == 0: k *= 0.95
                    if grain == "h" and j % 3 == 2: k *= 0.93
                    self.im.putpixel((x + i, y + j), jitter(shade(col, k), self.r, amt))

    def px(self, part, face, i, j, col, amt=6):
        x, y, w, h = faces(part, self.slim)[face]
        if not (0 <= i < w and 0 <= j < h): return
        if len(col) > 3 and col[3] == 0: self.im.putpixel((x + i, y + j), (0, 0, 0, 0))   # a gap in an outer layer
        else: self.im.putpixel((x + i, y + j), jitter(col, self.r, amt))

    def rows(self, part, face, j0, j1, col, amt=8, i0=0, i1=None):
        x, y, w, h = faces(part, self.slim)[face]
        for j in range(j0, min(j1, h)):
            for i in range(i0, w if i1 is None else min(i1, w)):
                self.im.putpixel((x + i, y + j), jitter(shade(col, 1.04 - 0.1 * j / h), self.r, amt))

    def ring(self, part, j0, j1, col, amt=8):
        for f in ("front", "back", "left", "right"): self.rows(part, f, j0, j1, col, amt)

    def clear(self, part):
        for f, (x, y, w, h) in faces(part, self.slim).items():
            for i in range(w):
                for j in range(h): self.im.putpixel((x + i, y + j), (0, 0, 0, 0))

    def face(self, skin, hair, eye, brow=None, mouth=None, fringe=2, sides=4, beard=None, scar=False):
        """The head: skin all round, hair on top and back, the fringe, brows, eyes with whites, a nose's shadow, a mouth."""
        self.fill("head", skin, 6)
        self.fill("head", hair, 12, faces_only=("top", "back"))
        for f in ("left", "right"): self.rows("head", f, 0, sides, hair, 12)
        self.rows("head", "back", 0, 8, hair, 12)
        self.rows("head", "front", 0, fringe, hair, 12)
        brow = brow or shade(hair, 0.8)
        for i in (1, 2, 5, 6): self.px("head", "front", i, 3, brow)
        for i, c in ((1, (236, 236, 232)), (2, eye), (5, eye), (6, (236, 236, 232))): self.px("head", "front", i, 4, c, 4)
        self.px("head", "front", 3, 5, shade(skin, 0.86)); self.px("head", "front", 4, 5, shade(skin, 0.86))
        m = mouth or shade(skin, 0.7)
        for i in (3, 4): self.px("head", "front", i, 6, m, 4)
        if beard:
            for i in range(1, 7): self.px("head", "front", i, 7, beard, 10)
            for i in (1, 2, 5, 6): self.px("head", "front", i, 6, beard, 10)
            for f in ("left", "right"): self.rows("head", f, 5, 8, beard, 10)
            self.rows("head", "bottom", 0, 8, beard, 10)
        if scar:
            for j, i in ((2, 5), (3, 6), (4, 6), (5, 7)): self.px("head", "front", i, j, (170, 110, 100))

    def save(self, name): self.im.save(os.path.join(OUT, name + ".png"))


SKIN_FAIR, SKIN_TAN, SKIN_WARM, PORCELAIN = (232, 200, 178), (190, 140, 108), (222, 182, 152), (240, 226, 222)
GOLD = (206, 166, 82)


def seren():
    """Porcelain skin with glowing glass cracks down the right cheek and hand; ice eyes; an asymmetric coat."""
    s = Skin(11, True)
    silver, coat, ice, glow, boot = (222, 226, 236), (240, 238, 234), (150, 196, 232), (190, 236, 255), (40, 42, 54)
    s.face(PORCELAIN, silver, (120, 200, 240), brow=(196, 200, 212), mouth=(196, 132, 140), fringe=2, sides=5)
    for i in (1, 6): s.px("head", "front", i, 3, (70, 70, 84))   # lashes
    for j, i in ((4, 7), (5, 6), (5, 7), (6, 6), (7, 5)): s.px("head", "front", i, j, glow, 4)   # glass cracks, glowing
    for j in range(2, 8): s.px("head", "right", 6 - j // 2, j, glow, 4) if j % 2 == 0 else None
    s.clear("hat"); s.rows("hat", "back", 0, 3, silver, 8)
    s.fill("body", ice, 8)
    s.px("body", "front", 3, 3, glow); s.px("body", "front", 4, 3, glow); s.px("body", "front", 3, 4, glow, 3)   # a crystal brooch
    s.fill("jacket", coat, 5, grain="v")
    for j in range(12):
        if j < 9:
            for i in (3, 4, 5): s.px("jacket", "front", i, j, (0, 0, 0, 0), 0)   # open, off-centre
        s.px("jacket", "front", 2, j, GOLD); s.px("jacket", "front", 6, j, GOLD)
    s.rows("jacket", "front", 0, 2, GOLD); s.rows("jacket", "back", 0, 2, GOLD)   # the high collar
    s.rows("body", "front", 7, 8, (50, 52, 70)); s.px("body", "front", 4, 7, GOLD)
    s.fill("rarm", coat, 5); s.clear("rsleeve"); s.ring("rsleeve", 0, 2, GOLD); s.ring("rarm", 10, 12, PORCELAIN, 3)
    for j in (9, 10, 11): s.px("rarm", "front", 1, j, glow, 3)   # the cracks run down her sword hand too
    s.fill("larm", ice, 6); s.clear("lsleeve"); s.ring("lsleeve", 0, 3, GOLD); s.ring("lsleeve", 8, 10, coat, 4); s.ring("larm", 10, 12, (232, 230, 226), 3)
    for l, p in (("rleg", "rpants"), ("lleg", "lpants")):
        s.fill(l, (64, 70, 96), 8); s.ring(l, 6, 12, boot, 6); s.ring(l, 6, 7, GOLD, 4)
        s.clear(p); s.ring(p, 0, 4, coat, 5)
    s.save("rival_seren")


def brannoch():
    """A broad scarred face with an eyepatch, a braided beard with iron rings, a fur mantle, a red tabard."""
    s = Skin(22, False)
    steel, dark, red, black, fur, beard = (118, 122, 130), (54, 56, 62), (128, 28, 30), (24, 20, 20), (104, 76, 52), (148, 72, 36)
    s.face(SKIN_TAN, (90, 52, 30), (90, 74, 52), fringe=1, sides=3, beard=beard, scar=True, mouth=(110, 56, 46))
    for i in range(8): s.px("head", "front", i, 3 if i < 4 else 2, black, 3)   # the patch's strap
    for i, j in ((1, 4), (2, 4), (1, 5), (2, 5)): s.px("head", "front", i, j, black, 3)   # the patch over his right eye
    for f in ("left", "right", "back"): s.rows("head", f, 2, 3, black, 3)
    s.px("head", "front", 3, 7, (60, 62, 70)); s.px("head", "front", 4, 7, (60, 62, 70))   # iron rings in the beard
    s.clear("hat"); s.rows("hat", "bottom", 0, 8, beard, 10)
    s.fill("body", (92, 94, 100), 14, grain="h")
    s.fill("jacket", red, 8, grain="v")
    for i, j in ((3, 4), (4, 4), (3, 5), (4, 5), (2, 6), (3, 6), (4, 6), (5, 6), (2, 7), (5, 7), (3, 3), (4, 3), (2, 2), (5, 2)): s.px("jacket", "front", i, j, black, 3)
    for f in ("front", "back", "left", "right"): s.rows("jacket", f, 0, 2, fur, 16)   # the fur mantle over the shoulders
    s.rows("jacket", "front", 9, 10, (70, 48, 32)); s.px("jacket", "front", 3, 9, (196, 160, 80)); s.px("jacket", "front", 4, 9, (196, 160, 80))
    for j in range(10, 12):
        for i in (1, 3, 5, 7): s.px("jacket", "front", i, j, (0, 0, 0, 0), 0)   # the tabard's torn hem
    s.fill("rarm", dark, 10); s.clear("rsleeve"); s.ring("rsleeve", 0, 2, fur, 16); s.ring("rarm", 7, 12, steel, 10)   # sword arm: an iron vambrace
    s.fill("larm", (92, 94, 100), 14, grain="h"); s.clear("lsleeve"); s.ring("lsleeve", 8, 12, dark, 8)
    for l, p in (("rleg", "rpants"), ("lleg", "lpants")):
        s.fill(l, dark, 10); s.ring(l, 3, 5, steel, 8); s.ring(l, 9, 12, (60, 44, 32), 8); s.clear(p); s.ring(p, 0, 3, red, 8)
    s.save("rival_brannoch")


def legion():
    """A full visored helm (the crest is a model), mail, a short red cloak."""
    s = Skin(33, False)
    steel, red = (134, 138, 146), (128, 32, 30)
    s.face(SKIN_WARM, (60, 44, 34), (60, 50, 40), fringe=1, sides=3)
    s.fill("hat", steel, 10)
    for i in range(8):
        for j in range(8):
            if j == 3 and 1 <= i <= 6 or (i in (3, 4) and 3 <= j <= 6): s.px("hat", "front", i, j, (12, 12, 14))   # the T of the visor
    s.fill("body", (100, 102, 108), 14, grain="h"); s.fill("jacket", (120, 124, 132), 10)
    s.rows("jacket", "front", 8, 9, (80, 58, 40)); s.rows("jacket", "back", 0, 12, red, 8)   # the cloak behind
    for a, sl in (("rarm", "rsleeve"), ("larm", "lsleeve")):
        s.fill(a, (100, 102, 108), 14, grain="h"); s.clear(sl); s.ring(sl, 0, 4, steel, 10); s.ring(a, 9, 12, (70, 52, 38), 8)
    for l, p in (("rleg", "rpants"), ("lleg", "lpants")):
        s.fill(l, (66, 66, 72), 10); s.ring(l, 7, 12, steel, 8); s.clear(p)
    s.save("rival_legion")


def alder():
    """Sandy hair, a scar across the nose, a red scarf, a bandaged sword arm, a slate coat with a bell clasp."""
    s = Skin(44, False)
    sand, slate, bronze, leather, scarf, wrap = (198, 160, 98), (64, 80, 100), (178, 132, 70), (82, 58, 40), (176, 40, 40), (226, 214, 190)
    s.face(SKIN_WARM, sand, (96, 132, 88), fringe=2, sides=3)
    for i in (2, 3, 4, 5): s.px("head", "front", i, 5, (196, 128, 120))   # the scar across the nose
    s.px("head", "front", 0, 1, sand); s.px("head", "front", 7, 2, sand); s.px("head", "front", 2, 2, sand)   # messy fringe
    s.fill("body", (214, 206, 190), 6); s.fill("jacket", slate, 8, grain="v")
    s.rows("jacket", "front", 0, 2, scarf, 10); s.ring("jacket", 0, 2, scarf, 10)   # the scarf round his neck
    s.px("jacket", "front", 3, 3, bronze); s.px("jacket", "front", 4, 3, bronze); s.px("jacket", "front", 3, 4, bronze, 3)
    for j in range(2, 12): s.px("jacket", "front", 7 - (j - 2) * 7 // 9, j, leather)
    s.rows("jacket", "front", 8, 9, leather)
    s.fill("rarm", slate, 8); s.clear("rsleeve"); s.ring("rsleeve", 0, 5, slate, 8)
    for j in range(5, 12): s.ring("rarm", j, j + 1, wrap if j % 2 else shade(wrap, 0.86), 4)   # the bandaged sword arm
    s.fill("larm", slate, 8); s.clear("lsleeve"); s.ring("lsleeve", 7, 8, bronze, 6); s.ring("larm", 10, 12, (110, 84, 60), 6)
    for l, p in (("rleg", "rpants"), ("lleg", "lpants")):
        s.fill(l, (64, 58, 56), 8); s.ring(l, 6, 12, leather, 8); s.ring(l, 6, 7, bronze, 4); s.clear(p)
    s.save("rival_alder")


def wren():
    """Round spectacles, a blue scarf to match her brother's red, a deep-blue robe stitched with silver bells."""
    s = Skin(55, True)
    sand, blue, bronze, silver, scarf = (204, 168, 106), (36, 50, 104), (190, 144, 76), (210, 214, 226), (60, 110, 190)
    s.face(SKIN_WARM, sand, (96, 132, 88), mouth=(180, 110, 106), fringe=2, sides=5)
    for i, j in ((0, 4), (3, 4), (4, 4), (7, 4), (1, 3), (2, 3), (5, 3), (6, 3), (1, 5), (2, 5), (5, 5), (6, 5)): s.px("head", "front", i, j, GOLD, 4)   # round spectacles
    for i in (2, 5): s.px("head", "front", i, 6, (214, 160, 140))   # freckles
    s.clear("hat")
    s.fill("body", blue, 8); s.fill("jacket", blue, 8, grain="v")
    s.ring("jacket", 0, 2, scarf, 10)
    for j in (3, 6, 9): s.px("jacket", "front", 2, j, silver); s.px("jacket", "front", 5, j + 1, silver)
    s.rows("jacket", "front", 7, 8, bronze)
    for a, sl in (("rarm", "rsleeve"), ("larm", "lsleeve")):
        s.fill(a, blue, 8); s.clear(sl); s.ring(sl, 7, 12, shade(blue, 1.18), 8); s.ring(sl, 11, 12, silver, 4); s.ring(a, 11, 12, SKIN_WARM, 4)
    for l, p in (("rleg", "rpants"), ("lleg", "lpants")):
        s.fill(l, shade(blue, 0.88), 8); s.ring(l, 10, 12, (46, 36, 32), 6); s.clear(p); s.ring(p, 0, 10, blue, 8); s.ring(p, 9, 10, silver, 4)
    s.save("rival_wren")


def mira():
    """Copper hair, freckles, a monocle on a chain, a beauty mark; a burgundy coat of many pockets over a moss waistcoat."""
    s = Skin(66, True)
    copper, burgundy, moss, brass, cream = (190, 90, 44), (104, 26, 42), (70, 94, 56), (214, 172, 86), (232, 222, 200)
    s.face(SKIN_FAIR, copper, (90, 150, 80), mouth=(200, 90, 96), fringe=2, sides=6)
    for i, j in ((4, 3), (6, 3), (4, 5), (6, 5), (7, 4)): s.px("head", "front", i, j, brass, 3)   # the monocle round her left eye
    s.px("head", "front", 5, 4, (190, 230, 220), 3)
    for j in (5, 6, 7): s.px("head", "front", 7, j, brass, 3)   # its chain
    for i in (1, 2, 5): s.px("head", "front", i, 5, (214, 150, 120))   # freckles
    s.px("head", "front", 2, 6, (80, 40, 40))   # a beauty mark
    s.clear("hat"); s.rows("hat", "back", 0, 8, copper, 12)
    for f in ("left", "right"): s.rows("hat", f, 3, 8, copper, 12)
    s.fill("body", cream, 6); s.rows("body", "front", 2, 9, moss, 8)
    for j in (3, 5, 7): s.px("body", "front", 3, j, brass); s.px("body", "front", 4, j, brass)
    s.px("body", "front", 5, 2, brass); s.px("body", "front", 6, 3, brass)   # the monocle's chain to a waistcoat button
    s.fill("jacket", burgundy, 8, grain="v")
    for j in range(10):
        for i in (2, 3, 4, 5): s.px("jacket", "front", i, j, (0, 0, 0, 0), 0)
        s.px("jacket", "front", 1, j, brass if j % 3 == 1 else shade(burgundy, 0.8)); s.px("jacket", "front", 6, j, shade(burgundy, 0.8))
    for i, j in ((0, 9), (1, 9), (6, 9), (7, 9), (0, 6), (7, 6)): s.px("jacket", "front", i, j, shade(burgundy, 0.66))   # pockets
    for a, sl in (("rarm", "rsleeve"), ("larm", "lsleeve")):
        s.fill(a, burgundy, 8); s.clear(sl); s.ring(sl, 8, 11, cream, 6); s.ring(sl, 10, 11, brass, 3); s.ring(a, 11, 12, SKIN_FAIR, 4)
    for l, p in (("rleg", "rpants"), ("lleg", "lpants")):
        s.fill(l, (52, 44, 48), 8); s.ring(l, 7, 12, (70, 46, 32), 8); s.clear(p); s.ring(p, 0, 8, burgundy, 8)
    s.save("rival_mira")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for f in (seren, brannoch, legion, alder, wren, mira): f()
    print("rival skins written to", OUT)
