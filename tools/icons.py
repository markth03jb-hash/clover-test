"""Spell icon generator: 16x16 pixel art drawn from simple shapes, upscaled x4 to 64x64 on a dark gradient."""
from PIL import Image, ImageDraw
import math, sys, os
OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/iron_spell_grimoire/textures/gui/spell_icons')

def make(name, bg_c, bg_e, main, acc, cmds):
    g = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 10.5
            t = min(1, d)
            g.putpixel((x, y), tuple(int(bg_c[i] * (1 - t) + bg_e[i] * t) for i in range(3)) + (255,))
    dr = ImageDraw.Draw(g)
    col = {'m': main, 'a': acc, 'w': (255, 255, 255), 'd': bg_e}
    for c in cmds:
        k, kind = c[0], c[1]
        cc = col[k]
        if kind == 'l': dr.line(c[2:6], fill=cc)
        elif kind == 'p': dr.point(c[2:4], fill=cc)
        elif kind == 'r': dr.rectangle(c[2:6], fill=cc)
        elif kind == 'R': dr.rectangle(c[2:6], outline=cc)
        elif kind == 'e': dr.ellipse(c[2:6], outline=cc)
        elif kind == 'E': dr.ellipse(c[2:6], fill=cc)
        elif kind == 'g': dr.polygon(c[2], fill=cc)
        elif kind == 'G': dr.polygon(c[2], outline=cc)
        elif kind == 'a': dr.arc(c[2:6], c[6], c[7], fill=cc)
    g.resize((64, 64), Image.NEAREST).save(os.path.join(OUT, name + '.png'))
