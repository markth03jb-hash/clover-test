"""Item icons for the rivals' keepsakes and the Rival's Token (16x16 pixel art, drawn here; original designs):
  Prism Ribbon       - Seren Vale's: a looped ribbon of clear glass, a spectrum running through its folds.
  Warlord's Standard - Brannoch Tor's: a short crimson standard on an iron pole, his ringed-tower sigil in gold.
  Last Bell          - the Bellmars': a hand bell split down the middle, Alder's red and Wren's blue, one clapper.
  Ledger Coin        - Mira Hollowell's: a gold coin stamped with an open ledger, a green bookkeeper's edge.
  Rival's Token      - an iron medallion, two crossed blades over it, a gold rim: calls a rival to you.
python3 tools/rival_icons.py [preview.png]"""
import os, sys
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/iron_spell_grimoire/textures/item")

ICONS = {
    "prism_ribbon": ({"o": (40, 52, 70), "w": (236, 246, 255), "c": (170, 220, 255), "r": (255, 120, 140), "y": (255, 224, 120),
                      "g": (130, 235, 170), "b": (120, 160, 255), "v": (190, 140, 255)}, [
        "................",
        "....oooo..oooo..",
        "...owwcco.owwco.",
        "..owcrrwoowcywo.",
        "..owyo.owwo.owo.",
        "..owgo..ow..ogo.",
        "..owbwooowoowbo.",
        "...owvvwwwwvwo..",
        "....oowwcwwoo...",
        ".....ocwwwco....",
        "....owcoowcwo...",
        "...owro...ogwo..",
        "...owyo...obwo..",
        "...owgo...ovwo..",
        "....oo.....oo...",
        "................"]),
    "warlords_standard": ({"o": (24, 16, 14), "i": (120, 124, 132), "d": (70, 72, 80), "r": (168, 34, 36), "k": (110, 20, 24),
                           "g": (240, 196, 90), "h": (255, 236, 160)}, [
        "...oo...........",
        "..ohgo..........",
        "...oioooooooo...",
        "...oirrrrrrrro..",
        "...oirrrggrrro..",
        "...oirrgkkgrro..",
        "...oirrgrrgrro..",
        "...oirrggggrro..",
        "...oirrrggrrko..",
        "...oikrrrrrkko..",
        "...oikkrkkrkko..",
        "...oiko.okko.o..",
        "...oido..oo.....",
        "...oido.........",
        "...odo..........",
        "....o..........."]),
    "last_bell": ({"o": (26, 18, 18), "r": (200, 50, 46), "R": (130, 28, 30), "b": (70, 130, 220), "B": (36, 70, 140),
                   "g": (240, 200, 100), "h": (255, 240, 180), "w": (120, 90, 60)}, [
        "......oooo......",
        ".....owwwwo.....",
        "......oggo......",
        ".....orrbbo.....",
        "....orrhbbbo....",
        "....orrhbbbo....",
        "...orrrhbbbbo...",
        "...orrrrbbbbo...",
        "...oRrrrbbbBo...",
        "..oRrrrrbbbbBo..",
        "..oRRrrrbbbBBo..",
        ".oggggggggggggo.",
        "..oooooggoooo...",
        "......ohgo......",
        ".......oo.......",
        "................"]),
    "ledger_coin": ({"o": (40, 28, 10), "g": (232, 184, 70), "y": (255, 226, 130), "d": (170, 120, 40), "e": (60, 150, 90),
                     "E": (30, 90, 56), "p": (250, 244, 220), "k": (90, 70, 40)}, [
        ".....oooooo.....",
        "...ooeeeeeeoo...",
        "..oeEggggggEeo..",
        "..oEgyyyyyygEo..",
        ".oeggyyyyyyggeo.",
        ".oegpppkkpppgeo.",
        ".oegpddkkddpgeo.",
        ".oegpppkkpppgeo.",
        ".oegpddkkddpgeo.",
        ".oegpppkkpppgeo.",
        ".oeggpppppppgeo.",
        "..oEggddddggEo..",
        "..oeEggggggEeo..",
        "...ooeeeeeeoo...",
        ".....oooooo.....",
        "................"]),
    "rival_token": ({"o": (14, 12, 16), "i": (88, 92, 104), "I": (54, 56, 66), "g": (226, 178, 70), "y": (255, 228, 140),
                     "s": (214, 222, 236), "S": (150, 160, 178), "r": (170, 40, 40)}, [
        "s..............s",
        ".sS..oooooo..Ss.",
        "..sSogggggyoSs..",
        "..oSsiiiiiisSo..",
        ".ogiSsiiiisSIgo.",
        ".ogiiSsiisSiIgo.",
        ".ogiiiSssSiiIgo.",
        ".ogiiiiSSiiiIgo.",
        ".ogiiisSSsiiIgo.",
        ".ogiisSiiSsiIgo.",
        ".ogiiSiirriSIgo.",
        "..ogIrIIIIrIgo..",
        "..oggIIIIIIggo..",
        "...oogggggyoo...",
        ".....oooooo.....",
        "................"]),
}


def draw(rows, pal):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            if ch != ".": im.putpixel((x, y), pal[ch] + (255,))
    return im


if __name__ == "__main__":
    ims = {}
    for name, (pal, rows) in ICONS.items():
        assert len(rows) == 16, name
        ims[name] = draw(rows, pal)
        ims[name].save(os.path.join(OUT, name + ".png"))
    if len(sys.argv) > 1:   # a preview sheet: each icon big, on a dark and a light tile
        S = 12
        sheet = Image.new("RGBA", (len(ims) * (16 * S + 24) + 24, 2 * (16 * S + 24) + 24), (30, 30, 36, 255))
        for i, (n, im) in enumerate(ims.items()):
            for j, bg in enumerate(((52, 52, 60, 255), (198, 198, 198, 255))):
                x, y = 24 + i * (16 * S + 24), 24 + j * (16 * S + 24)
                tile = Image.new("RGBA", (16 * S, 16 * S), bg)
                tile.alpha_composite(im.resize((16 * S, 16 * S), Image.NEAREST))
                sheet.paste(tile, (x, y))
        sheet.save(sys.argv[1])
    print("rival icons written:", len(ims))
