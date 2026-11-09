import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image, ImageDraw, ImageFilter
import math
OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/iron_spell_grimoire/textures/gui/spell_icons')
# Yuno's Mana Zone, in the style of the other Wind icons: dark green field, mint border, glowing rings with wind curling through
S = 64
im = Image.new('RGBA', (S, S))
for y in range(S):
    for x in range(S):
        t = min(1, math.hypot(x - 31.5, y - 31.5) / 40)
        im.putpixel((x, y), (int(8 + 4 * (1 - t)), int(26 + 10 * (1 - t)), int(22 + 6 * (1 - t)), 255))
glow = Image.new('RGBA', (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(glow)
d.ellipse([10, 10, 53, 53], outline=(150, 230, 190, 200), width=2)
d.ellipse([19, 19, 44, 44], outline=(190, 255, 220, 220), width=1)
for k in range(3):   # three wind arcs sweeping through the zone
    a = k * 120 + 20
    d.arc([6 + k * 2, 6 + k * 2, 57 - k * 2, 57 - k * 2], a, a + 70, fill=(230, 255, 240, 255), width=2)
d.ellipse([29, 29, 34, 34], fill=(240, 255, 245, 255))
blur = glow.filter(ImageFilter.GaussianBlur(2.2))
im = Image.alpha_composite(im, blur); im = Image.alpha_composite(im, glow)
d2 = ImageDraw.Draw(im); d2.rectangle([0, 0, S - 1, S - 1], outline=(150, 220, 190, 255), width=2)
im.save(os.path.join(OUT, 'mana_zone.png'))
