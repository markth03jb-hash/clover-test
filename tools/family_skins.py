"""Skins for the wild families and the night hunter (64 x 64, the player layout; original designs, painted here):

  the Mirefolk (marshes and swamps)
    bog_crawler   mud-green skin, moss for hair, bark plates strapped over a reed tunic
    mire_hexer    a hooded robe of rotting green, a wax-pale face, eyes like marsh-lights
    fen_matron    their leader: bark-brown skin, a cloak of moss, beads of bone down the front
  the Stonefolk (mountains and hills)
    scree_brute   grey stone-flecked skin, a slate kilt, plates of rock on the shoulders
    stonecaller   a slate robe with amber runes down the front, a stone-grey beard
    peak_warden   their leader: a horned iron helm, a mail coat with stone studs
  the Sandwraiths (deserts and badlands)
    dune_stalker  wrapped in sand-coloured cloth, a dark band over the eyes
    sandwraith    veiled, glowing amber eyes, a ragged sash and bare dark arms
    sun_herald    their leader: gold and white robes, a sun-red sash, a gilded veil
  and the night hunter
    gloamstalker  a long black coat lined in deep blue, a smooth mask with one blue slit
  the Rimefolk (snow and ice)
    rime_brute    frost-blue skin, a white fur mantle, rime on the brows
    frost_chanter a white robe with pale blue runes, a hood rimmed in frost, a silver beard
    hoarfrost_jarl their leader: a crown of ice, a blue-steel coat frosted white, a fur cloak
  the Hollowfolk (caves and the deep dark)
    hollow_crawler gaunt grey skin, no eyes - a teal glow where they'd be, ragged wraps
    hollow_singer  tattered dark robes, a teal glow from the mouth, bare grey hands
    deepwarden     their leader: a shell of dark stone plates, a teal lantern-heart in the chest
  the Ironbound Twins (a tier II bounty)
    twin_haldric  dark iron plate, a red tabard, a closed helm with a cross slit
    twin_oswin    the same iron, a blue tabard, an open helm, a scarred face

python3 tools/family_skins.py [OUT_DIR]   (default: the mod's textures/entity)
"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import rival_skins as rs
from rival_skins import Skin, shade

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/iron_spell_grimoire/textures/entity")
rs.OUT = OUT
T = (0, 0, 0, 0)


def limbs(s, arm, leg, foot=None, cuff=None, hand=None):
    for a, sl in (("rarm", "rsleeve"), ("larm", "lsleeve")):
        s.fill(a, arm, 8); s.clear(sl)
        if hand: s.ring(a, 10, 12, hand, 5)
        if cuff: s.ring(sl, 8, 10, cuff, 6)
    for l, p in (("rleg", "rpants"), ("lleg", "lpants")):
        s.fill(l, leg, 8); s.clear(p)
        if foot: s.ring(l, 9, 12, foot, 6)


def bog_crawler():
    s = Skin(101, False)
    skin, moss, bark, reed = (92, 112, 70), (60, 92, 44), (82, 60, 40), (128, 120, 72)
    s.face(skin, moss, (210, 200, 90), mouth=(52, 60, 40), fringe=3, sides=6)
    s.clear("hat"); s.rows("hat", "top", 0, 8, moss, 16); s.rows("hat", "back", 0, 4, moss, 16)
    s.fill("body", reed, 10, grain="v")
    s.fill("jacket", bark, 12, grain="h")
    for j in range(12):
        for i in range(8):
            if (i + j) % 5 == 0 or j in (5, 6) and i in (3, 4): s.px("jacket", "front", i, j, T, 0)   # straps between the plates
    limbs(s, skin, shade(reed, 0.8), foot=(48, 52, 34), hand=shade(skin, 0.9))
    s.ring("rsleeve", 0, 3, bark, 10); s.ring("lsleeve", 0, 3, bark, 10)
    s.save("family_bog_crawler")


def mire_hexer():
    s = Skin(102, True)
    robe, rot, wax, light = (56, 78, 52), (40, 56, 38), (214, 214, 190), (170, 255, 150)
    s.face(wax, rot, light, mouth=(120, 120, 100), fringe=1, sides=8)
    s.fill("hat", robe, 10)   # the hood
    for i in range(1, 7):
        for j in range(2, 8): s.px("hat", "front", i, j, T, 0)
    s.fill("body", rot, 8); s.fill("jacket", robe, 10, grain="v")
    for j in range(0, 12, 2): s.px("jacket", "front", 3, j, light, 10); s.px("jacket", "front", 4, j + 1, shade(light, 0.7), 10)
    limbs(s, robe, rot, foot=(36, 30, 26), hand=wax)
    for sl in ("rsleeve", "lsleeve"): s.ring(sl, 0, 10, robe, 10)
    for p in ("rpants", "lpants"): s.ring(p, 0, 10, robe, 10)   # the robe falls to the shins
    s.save("family_mire_hexer")


def fen_matron():
    s = Skin(103, True)
    bark, moss, bone, eye = (100, 74, 52), (64, 98, 50), (226, 218, 192), (240, 220, 120)
    s.face(bark, moss, eye, mouth=(70, 46, 36), fringe=2, sides=8)
    s.clear("hat"); s.rows("hat", "back", 0, 8, moss, 16)
    for f in ("left", "right"): s.rows("hat", f, 0, 8, moss, 16)
    s.fill("body", shade(moss, 0.8), 8); s.fill("jacket", moss, 14, grain="v")
    for j in range(1, 11, 2):
        s.px("jacket", "front", 3, j, bone, 6); s.px("jacket", "front", 4, j, bone, 6)
    for j in range(12): s.px("jacket", "front", 2, j, T, 0); s.px("jacket", "front", 5, j, T, 0)
    limbs(s, bark, shade(moss, 0.7), foot=(50, 40, 30), hand=bark)
    for sl in ("rsleeve", "lsleeve"): s.ring(sl, 0, 7, moss, 14)
    for p in ("rpants", "lpants"): s.ring(p, 0, 11, moss, 14)
    s.save("family_fen_matron")


def scree_brute():
    s = Skin(104, False)
    stone, slate, dark, eye = (128, 126, 122), (82, 86, 94), (60, 60, 64), (230, 150, 60)
    s.face(stone, dark, eye, mouth=(80, 76, 72), fringe=0, sides=0, brow=dark)
    for i, j in ((0, 1), (7, 2), (2, 7), (6, 6)): s.px("head", "front", i, j, shade(stone, 0.75))   # flecks of stone
    s.clear("hat")
    s.fill("body", stone, 10); s.fill("jacket", T, 0)
    s.clear("jacket"); s.ring("jacket", 7, 12, slate, 8)   # the kilt
    s.rows("jacket", "front", 6, 7, (110, 80, 50))
    limbs(s, stone, slate, foot=dark, hand=shade(stone, 0.9))
    for sl in ("rsleeve", "lsleeve"): s.ring(sl, 0, 3, shade(stone, 0.7), 12)   # rock plates
    s.save("family_scree_brute")


def stonecaller():
    s = Skin(105, False)
    skin, slate, beard, amber = (160, 150, 140), (70, 76, 88), (170, 170, 166), (250, 180, 70)
    s.face(skin, beard, amber, mouth=(100, 90, 86), fringe=1, sides=4, beard=beard)
    s.fill("hat", slate, 10)
    for i in range(1, 7):
        for j in range(2, 8): s.px("hat", "front", i, j, T, 0)
    s.fill("body", shade(slate, 0.8), 8); s.fill("jacket", slate, 10, grain="v")
    for j in (1, 3, 5, 7, 9): s.px("jacket", "front", 3 + j % 2, j, amber, 6)   # runes down the front
    limbs(s, slate, shade(slate, 0.8), foot=(54, 50, 48), hand=skin)
    for sl in ("rsleeve", "lsleeve"): s.ring(sl, 0, 10, slate, 10); s.ring(sl, 9, 10, amber, 4)
    for p in ("rpants", "lpants"): s.ring(p, 0, 9, slate, 10)
    s.save("family_stonecaller")


def peak_warden():
    s = Skin(106, False)
    skin, iron, mail, stone, eye = (176, 150, 132), (96, 100, 108), (130, 134, 140), (150, 146, 140), (220, 120, 60)
    s.face(skin, iron, eye, mouth=(110, 80, 70), fringe=3, sides=8, beard=(120, 110, 100))
    s.fill("hat", iron, 8)
    for i in range(1, 7):
        for j in range(4, 8): s.px("hat", "front", i, j, T, 0)
    for j in range(3, 7): s.px("hat", "front", 3, j, iron); s.px("hat", "front", 4, j, iron)   # the nasal
    s.fill("body", mail, 12, grain="h"); s.fill("jacket", T, 0); s.clear("jacket")
    for j in (2, 5, 8): s.px("jacket", "front", 2, j, stone); s.px("jacket", "front", 5, j, stone)
    s.ring("jacket", 9, 12, mail, 10)
    limbs(s, mail, shade(iron, 0.8), foot=(60, 56, 54), hand=(84, 70, 60))
    s.save("family_peak_warden")


def dune_stalker():
    s = Skin(107, False)
    wrap, dark, sand, eye = (210, 186, 140), (52, 40, 32), (190, 160, 110), (240, 200, 110)
    s.fill("head", wrap, 10, grain="h")
    s.rows("head", "front", 3, 5, dark, 6)
    s.px("head", "front", 2, 4, eye, 4); s.px("head", "front", 5, 4, eye, 4)
    s.clear("hat"); s.rows("hat", "back", 4, 8, wrap, 10)
    s.fill("body", wrap, 10, grain="h"); s.clear("jacket"); s.rows("jacket", "front", 6, 8, (140, 60, 40), 8)
    limbs(s, wrap, sand, foot=(120, 96, 70), hand=shade(wrap, 0.9))
    s.save("family_dune_stalker")


def sandwraith():
    s = Skin(108, True)
    veil, skin, sash, glow = (200, 176, 130), (70, 56, 50), (170, 80, 50), (255, 190, 80)
    s.fill("head", skin, 6)
    s.fill("head", veil, 10, faces_only=("top", "back", "left", "right"))
    s.rows("head", "front", 0, 3, veil, 10); s.rows("head", "front", 5, 8, veil, 10)
    s.px("head", "front", 2, 4, glow, 3); s.px("head", "front", 5, 4, glow, 3)
    s.fill("hat", veil, 10)
    for i in range(8):
        for j in (3, 4): s.px("hat", "front", i, j, T, 0)
    s.fill("body", skin, 6); s.clear("jacket")
    for j in range(12): s.px("jacket", "front", (j // 2) % 8, j, sash, 6); s.px("jacket", "back", 7 - (j // 2) % 8, j, sash, 6)
    limbs(s, skin, veil, foot=(90, 70, 50))
    for p in ("rpants", "lpants"): s.ring(p, 0, 12, veil, 12)
    s.save("family_sandwraith")


def sun_herald():
    s = Skin(109, False)
    skin, white, gold, red, glow = (190, 140, 108), (240, 236, 220), (220, 178, 80), (190, 50, 36), (255, 220, 140)
    s.face(skin, gold, glow, mouth=(140, 90, 70), fringe=2, sides=6)
    s.fill("hat", gold, 6)
    for i in range(8):
        for j in range(3, 8): s.px("hat", "front", i, j, T, 0) if j < 5 else s.px("hat", "front", i, j, gold if i % 2 else T, 4)   # a gilded veil, in strings
    s.fill("body", white, 6); s.fill("jacket", white, 6, grain="v")
    for j in range(12): s.px("jacket", "front", 3, j, gold, 4); s.px("jacket", "front", 4, j, gold, 4)
    s.rows("jacket", "front", 6, 8, red, 6); s.rows("jacket", "back", 6, 8, red, 6)
    limbs(s, white, white, foot=gold, hand=skin)
    for sl in ("rsleeve", "lsleeve"): s.ring(sl, 0, 10, white, 6); s.ring(sl, 9, 10, gold, 4)
    for p in ("rpants", "lpants"): s.ring(p, 0, 11, white, 6); s.ring(p, 10, 11, gold, 4)
    s.save("family_sun_herald")


def gloamstalker():
    s = Skin(110, True)
    mask, coat, line, slit = (30, 30, 36), (16, 16, 20), (24, 40, 96), (90, 150, 255)
    s.fill("head", mask, 4)
    for i in range(1, 7): s.px("head", "front", i, 4, slit, 3)
    s.fill("hat", coat, 6)
    for i in range(1, 7):
        for j in range(1, 8): s.px("hat", "front", i, j, T, 0)
    s.fill("body", line, 6); s.fill("jacket", coat, 6, grain="v")
    for j in range(12): s.px("jacket", "front", 3, j, T, 0); s.px("jacket", "front", 4, j, T, 0)
    for j in range(12): s.px("jacket", "front", 2, j, line, 4); s.px("jacket", "front", 5, j, line, 4)
    limbs(s, coat, coat, foot=(10, 10, 14), hand=(40, 40, 50))
    for sl in ("rsleeve", "lsleeve"): s.ring(sl, 0, 11, coat, 6); s.ring(sl, 10, 11, line, 4)
    for p in ("rpants", "lpants"): s.ring(p, 0, 12, coat, 6); s.ring(p, 11, 12, line, 4)
    s.save("family_gloamstalker")


def rime_brute():
    s = Skin(111, False)
    skin, fur, rime, eye = (150, 186, 214), (232, 236, 240), (210, 236, 255), (40, 90, 170)
    s.face(skin, fur, eye, mouth=(90, 120, 150), fringe=1, sides=4, brow=rime)
    s.clear("hat")
    s.fill("body", shade(skin, 0.9), 8); s.fill("jacket", fur, 10, grain="v")
    for j in range(4, 12):
        for i in range(2, 6): s.px("jacket", "front", i, j, T, 0)
    limbs(s, skin, (120, 110, 100), foot=(80, 70, 60), hand=shade(skin, 0.9))
    for sl in ("rsleeve", "lsleeve"): s.ring(sl, 0, 4, fur, 10)
    s.save("family_rime_brute")


def frost_chanter():
    s = Skin(112, False)
    skin, robe, rune, beard, eye = (200, 196, 200), (236, 240, 246), (120, 190, 255), (210, 214, 220), (120, 190, 255)
    s.face(skin, beard, eye, mouth=(150, 150, 160), fringe=1, sides=4, beard=beard)
    s.fill("hat", robe, 6)
    for i in range(1, 7):
        for j in range(2, 8): s.px("hat", "front", i, j, T, 0)
    s.ring("hat", 0, 1, rune, 6)
    s.fill("body", shade(robe, 0.9), 6); s.fill("jacket", robe, 6, grain="v")
    for j in (1, 3, 5, 7, 9): s.px("jacket", "front", 3 + j % 2, j, rune, 6)
    limbs(s, robe, shade(robe, 0.9), foot=(150, 160, 180), hand=skin)
    for sl in ("rsleeve", "lsleeve"): s.ring(sl, 0, 10, robe, 6); s.ring(sl, 9, 10, rune, 4)
    for p in ("rpants", "lpants"): s.ring(p, 0, 11, robe, 6)
    s.save("family_frost_chanter")


def hoarfrost_jarl():
    s = Skin(113, False)
    skin, steel, frost, fur, eye = (176, 196, 214), (70, 96, 130), (226, 240, 250), (150, 140, 130), (60, 160, 255)
    s.face(skin, frost, eye, mouth=(110, 120, 140), fringe=2, sides=6, beard=frost)
    s.clear("hat")
    s.fill("body", steel, 10, grain="h"); s.fill("jacket", T, 0); s.clear("jacket")
    for j in range(12): s.px("jacket", "front", 0, j, frost, 6); s.px("jacket", "front", 7, j, frost, 6)
    s.rows("jacket", "back", 0, 12, fur, 12)   # the fur cloak
    limbs(s, steel, shade(steel, 0.85), foot=(50, 60, 80), hand=(90, 100, 120))
    for sl in ("rsleeve", "lsleeve"): s.ring(sl, 0, 3, frost, 6)
    s.save("family_hoarfrost_jarl")


def hollow_crawler():
    s = Skin(114, True)
    skin, wrap, glow = (110, 112, 116), (60, 58, 56), (80, 230, 210)
    s.fill("head", skin, 8)
    for i in (1, 2, 5, 6): s.px("head", "front", i, 4, glow, 4)
    for i in range(2, 6): s.px("head", "front", i, 6, (40, 40, 44))
    s.clear("hat")
    s.fill("body", skin, 8); s.clear("jacket")
    for j in range(0, 12, 3): s.rows("jacket", "front", j, j + 1, wrap, 8)
    limbs(s, skin, wrap, foot=(40, 40, 44), hand=shade(skin, 0.8))
    s.save("family_hollow_crawler")


def hollow_singer():
    s = Skin(115, True)
    skin, robe, glow = (120, 122, 128), (34, 36, 44), (90, 240, 220)
    s.fill("head", skin, 6)
    for i in (2, 5): s.px("head", "front", i, 4, (30, 30, 34))
    for i in range(2, 6): s.px("head", "front", i, 6, glow, 4)
    s.px("head", "front", 3, 7, glow, 4); s.px("head", "front", 4, 7, glow, 4)
    s.fill("hat", robe, 6)
    for i in range(1, 7):
        for j in range(2, 8): s.px("hat", "front", i, j, T, 0)
    s.fill("body", robe, 6); s.fill("jacket", robe, 8, grain="v")
    for j in range(12):
        if j % 4 == 3: s.px("jacket", "front", 1 + j % 6, j, T, 0)
    limbs(s, robe, robe, foot=(20, 20, 24), hand=skin)
    for sl in ("rsleeve", "lsleeve"): s.ring(sl, 0, 10, robe, 8)
    for p in ("rpants", "lpants"): s.ring(p, 0, 12, robe, 8)
    s.save("family_hollow_singer")


def deepwarden():
    s = Skin(116, False)
    stone, dark, glow = (70, 72, 78), (40, 42, 48), (80, 240, 220)
    s.fill("head", stone, 10)
    s.rows("head", "front", 3, 5, dark, 4)
    s.px("head", "front", 2, 4, glow, 4); s.px("head", "front", 5, 4, glow, 4)
    s.clear("hat")
    s.fill("body", dark, 8); s.fill("jacket", stone, 12, grain="h")
    for i, j in ((3, 4), (4, 4), (3, 5), (4, 5)): s.px("jacket", "front", i, j, glow, 4)
    for i, j in ((2, 4), (5, 4), (3, 3), (4, 6)): s.px("jacket", "front", i, j, shade(glow, 0.6), 4)
    limbs(s, stone, dark, foot=(30, 30, 34), hand=dark)
    for sl in ("rsleeve", "lsleeve"): s.ring(sl, 0, 5, stone, 12)
    s.save("family_deepwarden")


def twin(name, tabard, seed, open_helm):
    s = Skin(seed, False)
    iron, dark, skin = (88, 90, 96), (52, 54, 60), (186, 150, 128)
    if open_helm:
        s.face(skin, (70, 56, 46), (90, 120, 160), mouth=(120, 80, 70), fringe=2, sides=8)
        for j in (2, 3, 4): s.px("head", "front", 6, j, (150, 90, 80))   # an old scar
        s.fill("hat", iron, 8)
        for i in range(1, 7):
            for j in range(2, 8): s.px("hat", "front", i, j, T, 0)
    else:
        s.fill("head", iron, 8)
        for i in range(1, 7): s.px("head", "front", i, 4, (14, 14, 16))   # the cross slit
        for j in range(2, 7): s.px("head", "front", 3, j, (14, 14, 16)); s.px("head", "front", 4, j, (14, 14, 16))
        s.clear("hat")
    s.fill("body", iron, 10, grain="h")
    s.fill("jacket", tabard, 8, grain="v", faces_only=("front", "back"))
    for f in ("front", "back"):
        for j in range(12):
            for i in (0, 7): s.px("jacket", f, i, j, T, 0)
    limbs(s, iron, dark, foot=(30, 30, 34), hand=dark, cuff=dark)
    for sl in ("rsleeve", "lsleeve"): s.ring(sl, 0, 3, dark, 8)
    s.save("twin_" + name)


def twin_haldric(): twin("haldric", (150, 30, 34), 117, False)
def twin_oswin(): twin("oswin", (36, 60, 140), 118, True)


# ---- ASHFALL, Arc 1 ("Embers")
EMBER = (255, 150, 50)

def cut(s, part, j0, j1):
    """Gaps in an outer layer, rows j0..j1 all round (rows() can't: it paints opaque)."""
    for f in ("front", "back", "left", "right"):
        for j in range(j0, j1):
            for i in range(8): s.px(part, f, i, j, T, 0)

def corvin_hale():
    """Corvin Hale: a one-armed exiled knight - grey and scarred, a worn navy gambeson, an empty left sleeve under a black half-cloak."""
    s = Skin(201, False)
    skin, hair, navy, leather, cloak = (192, 156, 130), (148, 146, 142), (40, 48, 72), (92, 64, 42), (30, 30, 38)
    s.face(skin, hair, (96, 120, 156), mouth=(130, 90, 80), fringe=1, sides=5, beard=shade(hair, 0.92), scar=True)
    s.clear("hat")
    s.fill("body", navy, 10, grain="h")
    s.clear("jacket")
    for j in range(12):   # a baldric across the chest, a belt
        s.px("jacket", "front", 7 - j * 7 // 11, j, leather, 6); s.px("jacket", "back", j * 7 // 11, j, leather, 6)
    s.ring("jacket", 8, 9, leather, 6)
    s.px("jacket", "front", 3, 8, (170, 150, 90)); s.px("jacket", "front", 4, 8, (170, 150, 90))   # the buckle
    s.fill("rarm", navy, 8); s.clear("rsleeve"); s.ring("rarm", 9, 12, leather, 6); s.ring("rsleeve", 7, 9, leather, 6)
    s.clear("larm"); s.clear("lsleeve")   # the arm he lost
    for l, p in (("rleg", "rpants"), ("lleg", "lpants")):
        s.fill(l, (58, 58, 64), 8); s.clear(p); s.ring(l, 8, 12, shade(leather, 0.8), 6)
    s.save("story_corvin")


def father_sallow():
    """Father Sallow: a gaunt priest in grey-white vestments gone yellow at the hem, a red-brown stole, ash-grey skin, pale eyes."""
    s = Skin(202, False)
    skin, vest, stain, stole = (196, 190, 182), (206, 202, 188), (164, 150, 110), (100, 34, 30)
    s.face(skin, (60, 58, 56), (214, 214, 200), brow=(90, 86, 80), mouth=(110, 80, 80), fringe=1, sides=3)
    for i in (2, 5): s.px("head", "front", i, 5, shade(skin, 0.8))   # hollow cheeks
    s.fill("hat", vest, 8); 
    for i in range(8):
        for j in range(8): 
            if j >= 2: s.px("hat", "front", i, j, T, 0)   # a cowl back from the face
    s.fill("body", vest, 8, grain="v")
    s.fill("jacket", vest, 8, grain="v")
    for f in ("front", "back"):
        for j in range(12):
            for i in (3, 4): s.px("jacket", f, i, j, stole, 6)
        s.rows("jacket", f, 9, 12, stain, 10)
    for j in (2, 4, 6): s.px("jacket", "front", 3, j, (200, 180, 120)); s.px("jacket", "front", 4, j, (200, 180, 120))
    for a, sl in (("rarm", "rsleeve"), ("larm", "lsleeve")):
        s.fill(a, skin, 6); s.fill(sl, vest, 8)
        cut(s, sl, 10, 12)
        s.ring(sl, 8, 10, stain, 8)
    for l, p in (("rleg", "rpants"), ("lleg", "lpants")):
        s.fill(l, vest, 8); s.fill(p, vest, 8, grain="v"); s.ring(p, 9, 12, stain, 10); s.ring(l, 11, 12, (40, 36, 34), 4)
    s.save("story_sallow")


def penitent():
    """Sallow's Penitents: the 'freed' - sackcloth hoods with no face, a stitched wound where the brand was cut out, bare grey arms."""
    s = Skin(203, False)
    sack, grey, stitch, wound = (150, 128, 96), (128, 122, 116), (40, 30, 26), (120, 40, 34)
    s.fill("head", sack, 12)
    for i in (2, 5): s.px("head", "front", i, 4, (20, 16, 14))   # two holes
    for j in (6,): 
        for i in range(2, 6): s.px("head", "front", i, j, shade(sack, 0.7))
    s.clear("hat"); s.ring("hat", 7, 8, (80, 66, 50), 6)   # the cord round the neck
    s.fill("body", grey, 8)
    for j in range(3, 9): s.px("body", "front", 5, j, wound, 6); s.px("body", "front", 6, j, shade(wound, 0.8), 6)
    for j in range(3, 9, 2): s.px("body", "front", 4, j, stitch); s.px("body", "front", 7, j, stitch)
    s.clear("jacket"); s.rows("jacket", "front", 9, 12, sack, 10); s.rows("jacket", "back", 0, 12, sack, 10)
    for f in ("left", "right"): s.rows("jacket", f, 9, 12, sack, 10)
    limbs(s, grey, sack, foot=(70, 60, 50), hand=shade(grey, 0.85))
    for sl in ("rarm", "larm"): s.px(sl, "front", 1, 3, wound); s.px(sl, "front", 2, 4, wound)
    s.save("story_penitent")


def ilsa_marr():
    """Ilsa Marr, the other survivor of the village: plain village wool, soot-dark hair, a burn up her neck, an ember-lit brand on her right arm."""
    s = Skin(204, True)
    skin, hair, wool, apron = (214, 176, 148), (44, 34, 30), (120, 86, 60), (180, 168, 140)
    s.face(skin, hair, (120, 96, 64), mouth=(150, 96, 90), fringe=3, sides=7)
    for j, i in ((6, 0), (7, 0), (7, 1), (5, 0)): s.px("head", "front", i, j, (170, 100, 84))   # the burn
    s.fill("hat", hair, 10, faces_only=("back", "left", "right")); s.clear("hat")
    s.rows("hat", "back", 0, 8, hair, 10)
    s.fill("body", wool, 10, grain="v")
    s.clear("jacket"); s.rows("jacket", "front", 4, 12, apron, 8, 1, 7)
    for i in range(8): s.px("jacket", "front", i, 8, shade(apron, 0.6))   # soot
    limbs(s, skin, (88, 66, 50), foot=(54, 40, 30))
    s.fill("rsleeve", wool, 8); s.fill("lsleeve", wool, 8)
    for sl in ("rsleeve", "lsleeve"): cut(s, sl, 5, 12)
    for j, i in ((6, 1), (7, 1), (7, 2), (8, 1), (9, 0), (9, 1)): s.px("rarm", "front", i, j, EMBER, 4)   # the brand
    s.save("story_ilsa")

STORY = (corvin_hale, father_sallow, penitent, ilsa_marr)


ALL = (rime_brute, frost_chanter, hoarfrost_jarl, hollow_crawler, hollow_singer, deepwarden, twin_haldric, twin_oswin,
       bog_crawler, mire_hexer, fen_matron, scree_brute, stonecaller, peak_warden, dune_stalker, sandwraith, sun_herald, gloamstalker)

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for f in ALL + STORY: f()
    print("family skins written to", OUT)
