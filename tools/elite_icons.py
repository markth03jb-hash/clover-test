"""The elite mob icons (client/EliteClient): an 8 x 4 atlas of 16 px cells - the 20 traits (0-19), then the threat
rank skulls 0-5 (cells 24-29). Each trait sits on a dark tile edged in its family's colour: defence steel, offence red,
anti-magic violet, movement green, on-death amber, champions magenta.
python3 tools/elite_icons.py   -> textures/gui/elite_icons.png"""
import os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/iron_spell_grimoire/textures/gui/elite_icons.png")
FAM = {"def": (120, 160, 200), "off": (220, 70, 60), "mag": (170, 100, 230), "mov": (90, 200, 110), "die": (235, 160, 50), "champ": (230, 80, 220), "late": (240, 200, 80)}
W, L, D = (240, 236, 228), (200, 196, 188), (20, 16, 14)
S = 4   # drawn at 64 px, then reduced, for clean edges


def tile(fam):
    im = Image.new("RGBA", (16 * S, 16 * S), (0, 0, 0, 0))
    g = ImageDraw.Draw(im)
    g.rounded_rectangle((0, 0, 16 * S - 1, 16 * S - 1), radius=3 * S, fill=(26, 22, 20, 235), outline=FAM[fam] + (255,), width=S)
    return im, g


def P(*xy): return [(x * S, y * S) for x, y in zip(xy[::2], xy[1::2])]


def draw(i):
    fam = ["def", "def", "def", "off", "off", "off", "off", "mag", "mag", "mag", "mov", "mov", "mov", "mov", "die", "die", "die", "die", "champ", "champ", "late", "late", "late"][i]
    im, g = tile(fam)
    c = FAM[fam]
    if i == 0: g.polygon(P(8, 3, 13, 4.5, 12.5, 9, 8, 13, 3.5, 9, 3, 4.5), fill=W); g.line(P(8, 4.5, 8, 11.5), fill=c, width=S)            # shield
    if i == 1:
        for k in range(3): g.rounded_rectangle((3 * S, (3.5 + k * 3.3) * S, 13 * S, (5.8 + k * 3.3) * S), radius=S, fill=W if k else L)   # plates
        g.line(P(9, 3.5, 7.5, 5.8), fill=D, width=S // 2 + 1)
    if i == 2: g.polygon(P(8, 13, 3, 7.5, 3.5, 4.5, 6, 3.5, 8, 5.5, 10, 3.5, 12.5, 4.5, 13, 7.5), fill=W); g.polygon(P(8, 6.5, 10, 9, 8.8, 9, 8.8, 11, 7.2, 11, 7.2, 9, 6, 9), fill=c)   # heart, arrow up
    if i == 3: g.polygon(P(9.5, 2.5, 4.5, 9, 7.5, 9, 6, 13.5, 11.5, 6.5, 8.5, 6.5), fill=W); g.line(P(3, 13.5, 13, 13.5), fill=c, width=S)  # bolt to the ground
    if i == 4: g.ellipse((5 * S, 5 * S, 11 * S, 11 * S), outline=W, width=S); g.ellipse((7 * S, 7 * S, 9 * S, 9 * S), fill=W); g.arc((1.5 * S, 1.5 * S, 14.5 * S, 14.5 * S), 200, 320, fill=c, width=S)   # homing orb
    if i == 5:
        for y0 in (4, 8, 12):
            g.line(P(2.5, y0, 10.5, y0), fill=W, width=S)
            g.polygon(P(13.5, y0, 10, y0 - 2, 10, y0 + 2), fill=W)
            g.line(P(2.5, y0, 4, y0 - 1.5), fill=c, width=S // 2 + 1)   # three arrows, fletched
    if i == 6:
        g.arc((2 * S, 2 * S, 14 * S, 14 * S), 0, 360, fill=L, width=S); g.ellipse((6 * S, 6 * S, 10 * S, 10 * S), fill=W)   # the strike and its echo
        g.arc((4 * S, 4 * S, 12 * S, 12 * S), 0, 360, fill=c, width=S // 2 + 1)
    if i == 7: g.polygon(P(8, 2.5, 12, 9, 11, 12, 8, 13.5, 5, 12, 4, 9), fill=W); g.ellipse((6 * S, 8 * S, 8.5 * S, 10.5 * S), fill=c)        # a drop of mana
    if i == 8:
        g.polygon(P(8, 2.5, 9.5, 6.5, 13.5, 8, 9.5, 9.5, 8, 13.5, 6.5, 9.5, 2.5, 8, 6.5, 6.5), fill=L)   # a spell star...
        g.line(P(3.5, 3.5, 12.5, 12.5), fill=c, width=int(1.5 * S)); g.line(P(12.5, 3.5, 3.5, 12.5), fill=c, width=int(1.5 * S))   # ...struck out
    if i == 9: g.polygon(P(8, 2.5, 13, 8, 8, 13.5, 3, 8), outline=W, width=S); g.line(P(6, 6, 10, 10), fill=L, width=S); g.polygon(P(4.5, 9.5, 4.5, 12.5, 7.5, 12.5), fill=c)   # mirror
    if i == 10:
        g.ellipse((6.5 * S, 2 * S, 9.5 * S, 5 * S), outline=W, width=S); g.line(P(8, 5, 8, 13), fill=W, width=S); g.line(P(5, 7, 11, 7), fill=W, width=S)
        g.arc((3 * S, 7 * S, 13 * S, 14 * S), 20, 160, fill=W, width=S)   # anchor
    if i == 11: g.ellipse((2 * S, 5 * S, 14 * S, 11 * S), fill=W); g.ellipse((6 * S, 5.5 * S, 10 * S, 10.5 * S), fill=c); g.ellipse((7.3 * S, 6.8 * S, 8.7 * S, 9.2 * S), fill=D)   # eye
    if i == 12:
        g.rounded_rectangle((2 * S, 5 * S, 8.5 * S, 9 * S), radius=2 * S, outline=W, width=S); g.rounded_rectangle((7.5 * S, 7 * S, 14 * S, 11 * S), radius=2 * S, outline=L, width=S)   # chain
    if i == 13:
        for x0 in (3, 8): g.polygon(P(x0, 3.5, x0 + 5, 8, x0, 12.5, x0, 10, x0 + 2.4, 8, x0, 6), fill=W)   # >>
    if i == 14: g.pieslice((2.5 * S, 3 * S, 12.5 * S, 13 * S), 90, 270, fill=W); g.pieslice((3.5 * S, 3 * S, 13.5 * S, 13 * S), 270, 90, fill=L)   # split in two
    if i == 15: g.ellipse((3 * S, 5 * S, 12 * S, 14 * S), fill=W); g.line(P(10, 6, 12.5, 3), fill=L, width=S); g.ellipse((11.5 * S, 1.5 * S, 14 * S, 4 * S), fill=c)   # bomb
    if i == 16:
        g.rectangle((2.5 * S, 7 * S, 13.5 * S, 13 * S), fill=W); g.polygon(P(2.5, 7, 3.5, 3.5, 12.5, 3.5, 13.5, 7), fill=L)
        for x0 in (4, 6.5, 9, 11.5): g.polygon(P(x0, 7, x0 + 1, 9, x0 + 2, 7), fill=D)   # a chest with teeth
    if i == 17:
        g.pieslice((3.5 * S, 2.5 * S, 12.5 * S, 11.5 * S), 180, 360, fill=W); g.rectangle((3.5 * S, 7 * S, 12.5 * S, 11 * S), fill=W)
        g.polygon(P(3.5, 11, 5, 13.5, 6.5, 11, 8, 13.5, 9.5, 11, 11, 13.5, 12.5, 11), fill=W)
        g.ellipse((5.5 * S, 6 * S, 7 * S, 8 * S), fill=D); g.ellipse((9 * S, 6 * S, 10.5 * S, 8 * S), fill=D)   # ghost
    if i == 18:
        for x0, col in ((3, W), (9, (60, 80, 150))):
            g.ellipse((x0 * S, 2.5 * S, (x0 + 4) * S, 6.5 * S), fill=col); g.polygon(P(x0 - 0.5, 13.5, x0 + 0.5, 7, x0 + 3.5, 7, x0 + 4.5, 13.5), fill=col)   # a figure and its shadow
    if i == 19:
        g.ellipse((4 * S, 2 * S, 12 * S, 14 * S), outline=W, width=S); g.ellipse((6 * S, 4.5 * S, 10 * S, 11.5 * S), fill=(110, 60, 170))
        g.arc((5 * S, 3.5 * S, 11 * S, 12.5 * S), 40, 200, fill=c, width=S)   # a rift
    if i == 20:   # Overseer: an eye in a ring
        g.ellipse((1.5 * S, 1.5 * S, 14.5 * S, 14.5 * S), outline=L, width=S // 2 + 1)
        g.ellipse((3.5 * S, 5.5 * S, 12.5 * S, 10.5 * S), fill=W); g.ellipse((6.5 * S, 6 * S, 9.5 * S, 10 * S), fill=c)
    if i == 21:   # Unyielding: a wall, and a fifth of a bar
        for k in range(3): g.rectangle((3 * S, (3 + k * 3.2) * S, 13 * S, (5.6 + k * 3.2) * S), fill=W if k != 1 else L)
        g.rectangle((3 * S, 12.6 * S, 5 * S, 13.8 * S), fill=c)
    if i == 22:   # Blinker: a figure, a dashed arc behind it
        g.ellipse((9 * S, 3 * S, 12 * S, 6 * S), fill=W); g.polygon(P(8.5, 13.5, 9.2, 7, 11.8, 7, 12.5, 13.5), fill=W)
        for a in range(150, 300, 30): g.arc((1.5 * S, 3 * S, 13.5 * S, 15 * S), a, a + 14, fill=c, width=S)
    return im.resize((16, 16), Image.LANCZOS)


def skull(rank):
    im = Image.new("RGBA", (16 * S, 16 * S), (0, 0, 0, 0))
    g = ImageDraw.Draw(im)
    col = [(200, 200, 200), (230, 220, 160), (240, 190, 90), (240, 140, 60), (235, 80, 60), (200, 60, 220)][rank]
    g.rounded_rectangle((0, 0, 16 * S - 1, 16 * S - 1), radius=3 * S, fill=(26, 22, 20, 235), outline=col + (255,), width=S)
    g.ellipse((3.5 * S, 2 * S, 12.5 * S, 10 * S), fill=W); g.rectangle((5 * S, 8 * S, 11 * S, 11 * S), fill=W)
    g.ellipse((5 * S, 5 * S, 7.5 * S, 7.5 * S), fill=D); g.ellipse((8.5 * S, 5 * S, 11 * S, 7.5 * S), fill=D)
    for k in range(rank):   # one pip a rank
        x = 8 - (rank * 2.4) / 2 + k * 2.4 + 0.2
        g.ellipse((x * S, 12 * S, (x + 1.8) * S, 13.8 * S), fill=col)
    return im.resize((16, 16), Image.LANCZOS)


atlas = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
for i in range(23): atlas.paste(draw(i), ((i % 8) * 16, (i // 8) * 16))
for r in range(6): atlas.paste(skull(r), (((24 + r) % 8) * 16, ((24 + r) // 8) * 16))
atlas.save(OUT)
print("elite icons:", OUT)
