"""Icons for the boss ladder (original designs, drawn here):
  the seven signature drops (textures/item) - Marrow Plate (a bone plate, cracked), Hopeless Claw (a black claw, red at
  the tip), Turncoat Sigil (a coin half gold, half black, two arrows turning), Lich's Page (a torn page, violet
  writing), Grave Knot (a knot of dark green cord), Hexbound Ink (a bottle of swirling violet ink), Unbound Seal
  (a gold seal cracked open, red light through it);
  and the three late elite traits in the elite atlas (cells 20-22): Overseer (an eye in a ring), Unyielding
  (a wall with a 1/5 notch), Blinker (a figure and a dashed arc behind it).
python3 tools/ladder_assets.py [preview.png]"""
import os, sys, math
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/iron_spell_grimoire")
S = 4


def canvas(): im = Image.new("RGBA", (16 * S, 16 * S), (0, 0, 0, 0)); return im, ImageDraw.Draw(im)
def P(*xy): return [(x * S, y * S) for x, y in zip(xy[::2], xy[1::2])]
def box(x0, y0, x1, y1): return (x0 * S, y0 * S, x1 * S, y1 * S)


def outline(im, col=(18, 12, 14, 255)):
    """A one-pixel dark edge round the shape, at 16 px."""
    px = im.load(); w, h = im.size
    out = im.copy(); po = out.load()
    for y in range(h):
        for x in range(w):
            if px[x, y][3] > 0: continue
            if any(0 <= x + dx < w and 0 <= y + dy < h and px[x + dx, y + dy][3] > 160 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))): po[x, y] = col
    return out


def finish(im):
    small = im.resize((16, 16), Image.LANCZOS)
    px = small.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 255 if a > 110 else 0)
    return outline(small)


def marrow_plate():
    im, g = canvas()
    g.polygon(P(8, 1.5, 13.5, 3.5, 13, 9.5, 8, 14.5, 3, 9.5, 2.5, 3.5), fill=(232, 224, 200))
    g.polygon(P(8, 3, 12, 4.5, 11.6, 9, 8, 12.8, 4.4, 9, 4, 4.5), fill=(208, 196, 166))
    for y in (5.5, 7.8, 10): g.line(P(5, y, 11, y), fill=(160, 140, 110), width=S // 2 + 1)   # ribs of bone
    g.line(P(9.5, 2, 8.2, 5.5, 9.4, 8, 7.8, 11), fill=(90, 70, 60), width=S // 2 + 1)        # the crack
    return finish(im)


def hopeless_claw():
    im, g = canvas()
    g.polygon(P(4, 2, 9.5, 1.5, 12.5, 4, 12, 8, 10, 11.5, 7, 14.5, 7.6, 10.5, 6.8, 6.5), fill=(46, 36, 58))
    g.polygon(P(5.2, 2.6, 9, 2.4, 11, 4.3, 10.6, 7, 8.6, 6.4, 7, 4.2), fill=(84, 70, 104))
    g.polygon(P(7.6, 10.5, 10, 11.5, 7, 14.5), fill=(210, 40, 50))   # red at the tip
    return finish(im)


def turncoat_sigil():
    im, g = canvas()
    g.pieslice(box(2, 2, 14, 14), 90, 270, fill=(236, 196, 90))
    g.pieslice(box(2, 2, 14, 14), 270, 90, fill=(36, 30, 40))
    g.ellipse(box(2, 2, 14, 14), outline=(150, 110, 50), width=S // 2 + 1)
    g.arc(box(4.5, 4.5, 11.5, 11.5), 200, 340, fill=(250, 250, 240), width=S)
    g.polygon(P(11.2, 5.4, 12.6, 7.6, 10.2, 7.4), fill=(250, 250, 240))
    g.arc(box(4.5, 4.5, 11.5, 11.5), 20, 160, fill=(180, 60, 200), width=S)
    g.polygon(P(4.8, 10.6, 3.4, 8.4, 5.8, 8.6), fill=(180, 60, 200))
    return finish(im)


def lichs_page():
    im, g = canvas()
    g.polygon(P(3, 2, 12, 1.5, 13, 5, 12.4, 9, 13.2, 14, 4, 14.5, 3.6, 10, 2.4, 6), fill=(214, 200, 168))
    g.polygon(P(12, 1.5, 13, 5, 11, 3.5), fill=(176, 160, 128))
    for k, y in enumerate((4, 6.2, 8.4, 10.6, 12.6)):
        g.line(P(4.8, y, 11 - (k % 2) * 2, y), fill=(120, 60, 170), width=S // 2 + 1)   # violet writing
    g.ellipse(box(9, 9.5, 11.6, 12), fill=(170, 90, 230))
    return finish(im)


def grave_knot():
    im, g = canvas()
    c, d = (60, 110, 70), (30, 64, 40)
    g.arc(box(2.5, 3, 9.5, 10), 30, 330, fill=c, width=int(1.6 * S))
    g.arc(box(6.5, 3, 13.5, 10), 210, 150, fill=d, width=int(1.6 * S))
    g.line(P(5, 9.5, 3.5, 14), fill=c, width=int(1.6 * S))
    g.line(P(11, 9.5, 12.5, 14), fill=d, width=int(1.6 * S))
    g.ellipse(box(6.6, 5.4, 9.4, 8.2), fill=(140, 190, 140))
    return finish(im)


def hexbound_ink():
    im, g = canvas()
    g.rectangle(box(6.2, 1.5, 9.8, 4), fill=(110, 80, 60))
    g.rounded_rectangle(box(3, 4, 13, 14.5), radius=3 * S, fill=(70, 60, 90))
    g.rounded_rectangle(box(4, 6.5, 12, 13.5), radius=2.5 * S, fill=(120, 50, 180))
    for k in range(3):
        a0 = k * 120
        g.arc(box(5 + k * 0.6, 7.2 + k * 0.6, 11 - k * 0.6, 12.8 - k * 0.6), a0, a0 + 200, fill=(210, 160, 255), width=S // 2 + 1)
    g.line(P(4.6, 5, 4.6, 9), fill=(200, 200, 230), width=S // 2 + 1)   # a glint on the glass
    return finish(im)


def unbound_seal():
    im, g = canvas()
    g.ellipse(box(2, 2, 14, 14), fill=(214, 164, 60))
    g.ellipse(box(4, 4, 12, 12), fill=(176, 124, 40))
    g.polygon(P(8, 2, 9, 6, 7.4, 8, 9.2, 10.6, 8, 14, 6.8, 10.4, 8.4, 8, 6.6, 5.8), fill=(255, 70, 60))   # the crack, red light through it
    g.polygon(P(8, 3.5, 8.5, 6, 7.6, 8, 8.6, 10.4, 8, 12.5, 7.4, 10.4, 8.2, 8, 7.2, 5.8), fill=(255, 210, 150))
    return finish(im)


ICONS = {"marrow_plate": marrow_plate, "hopeless_claw": hopeless_claw, "turncoat_sigil": turncoat_sigil, "lichs_page": lichs_page,
         "grave_knot": grave_knot, "hexbound_ink": hexbound_ink, "unbound_seal": unbound_seal}

if __name__ == "__main__":
    import json
    made = {}
    for name, fn in ICONS.items():
        im = fn(); made[name] = im
        im.save(os.path.join(ROOT, "textures/item", name + ".png"))
        with open(os.path.join(ROOT, "models/item", name + ".json"), "w") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "iron_spell_grimoire:item/" + name}}, f, indent=1)
    if len(sys.argv) > 1:
        sheet = Image.new("RGBA", (len(made) * 80 + 10, 90), (40, 36, 44, 255))
        for i, im in enumerate(made.values()): sheet.paste(im.resize((64, 64), Image.NEAREST), (10 + i * 80, 13), im.resize((64, 64), Image.NEAREST))
        sheet.save(sys.argv[1])
    print("ladder icons:", len(made))
