"""Icons for ASHFALL (original designs, drawn here): the Story Journal (a black book, an ember brand on its cover),
the Ashen Page (a scorched page, its writing red), Sallow's Hook (the hooked knife) and Sallow's Rosary (bone beads,
one of amber).
python3 tools/story_assets.py [preview.png]"""
import os, sys, json
sys.path.insert(0, os.path.dirname(__file__))
from ladder_assets import canvas, P, box, finish, ROOT, S
from PIL import Image


def story_journal():
    im, g = canvas()
    g.rectangle(box(3, 1.5, 13.5, 14.5), fill=(30, 26, 30)); g.rectangle(box(3, 1.5, 4.6, 14.5), fill=(52, 40, 34))   # the spine
    g.rectangle(box(12.6, 2.2, 13.5, 13.8), fill=(214, 204, 180))   # page edges
    g.polygon(P(8.6, 4, 10.4, 7, 9.4, 7.4, 11, 11.5, 8.2, 8.2, 9.2, 7.8, 7.4, 5.2), fill=(255, 150, 50))   # the brand
    g.polygon(P(8.9, 5, 9.8, 7, 9.2, 7.3, 10, 9.6, 8.7, 8, 9.3, 7.7), fill=(255, 220, 150))
    return finish(im)


def ashen_page():
    im, g = canvas()
    g.polygon(P(3, 2, 12, 1.5, 13, 5, 12.4, 9, 13.2, 14, 4.5, 14.5, 3.6, 11, 2.6, 7), fill=(214, 200, 168))
    g.polygon(P(9.5, 9, 12.4, 9, 13.2, 14, 8, 14.5, 9, 12.5), fill=(70, 60, 54))   # the burnt corner
    g.polygon(P(10, 10, 12, 10.2, 12.6, 13.2, 9.4, 13.4), fill=(36, 30, 28))
    for y in (4, 6, 8): g.line(P(5, y, 11, y), fill=(150, 40, 34), width=S // 2 + 1)
    g.line(P(5, 10, 8, 10), fill=(150, 40, 34), width=S // 2 + 1)
    return finish(im)


def sallow_hook():
    im, g = canvas()
    g.polygon(P(3, 13.5, 5, 11.5, 6.5, 13, 4.5, 15), fill=(226, 218, 192))   # the bone grip
    g.polygon(P(5, 11.5, 6.5, 13, 7.5, 12, 6, 10.5), fill=(150, 110, 60))
    g.polygon(P(6, 10.5, 7.5, 12, 11, 8, 13.5, 3.5, 12.5, 1.5, 10.5, 4, 11.2, 5.2, 9.5, 7.6), fill=(200, 204, 214))
    g.polygon(P(12.5, 1.5, 13.5, 3.5, 12.8, 4.2, 12, 2.4), fill=(150, 40, 34))   # red at the hook
    return finish(im)


def sallow_rosary():
    im, g = canvas()
    beads = [(4, 3), (6.5, 2.2), (9.2, 2.4), (11.5, 3.6), (12.6, 6), (12.2, 8.6), (10.6, 10.6), (8.2, 11.4), (5.8, 10.6), (4.2, 8.6), (3.4, 6)]
    for i, (x, y) in enumerate(beads):
        g.ellipse(box(x - 1.15, y - 1.15, x + 1.15, y + 1.15), fill=(255, 150, 50) if i == 5 else (226, 218, 192))
    g.line(P(8.2, 11.4, 8.2, 13), fill=(90, 70, 56), width=S // 2 + 1)
    g.polygon(P(7, 13, 9.4, 13, 8.2, 15.4), fill=(226, 218, 192))
    return finish(im)


ICONS = {"story_journal": story_journal, "ashen_page": ashen_page, "sallow_hook": sallow_hook, "sallow_rosary": sallow_rosary}

if __name__ == "__main__":
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
    print("story icons:", len(made))
