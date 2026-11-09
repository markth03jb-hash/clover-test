"""Leather, trim and inventory icon for the seven newer grimoires (covers come from covers.py). Does not touch the older books."""
from PIL import Image
import colorsys, os
ROOT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/iron_spell_grimoire/textures/item')
M = os.path.join(ROOT, 'spell_book_models')
BOOKS = {
    'flame_spirit_grimoire': (0.03, 1.25, 1.10, (240, 206, 122)),
    'lions_flame_grimoire':  (0.10, 1.25, 1.10, (255, 211, 107)),
    'world_tree_grimoire':   (0.36, 1.00, 0.85, (232, 226, 160)),
    'slash_grimoire':        (0.97, 0.90, 0.80, (232, 232, 240)),
    'earth_grimoire':        (0.09, 0.70, 0.90, (216, 184, 120)),
    'beast_grimoire':        (0.46, 1.10, 0.95, (230, 208, 138)),
    'cotton_grimoire':       (0.90, 0.80, 1.35, (255, 244, 248)),
}
def shift(im, hue, sm, vm):
    im = im.convert('RGBA'); px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0: continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            s2 = min(1, s + 0.05) if s < 0.08 else min(1, s * sm)
            nr, ng, nb = colorsys.hsv_to_rgb(hue, s2, min(1, v * vm))
            px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    return im
for name, (hue, sm, vm, trim) in BOOKS.items():
    shift(Image.open(os.path.join(M, 'dark_grimoire_leather.png')), hue, sm, vm).save(os.path.join(M, f'{name}_leather.png'))
    shift(Image.open(os.path.join(ROOT, 'dark_grimoire.png')), hue, sm, vm).save(os.path.join(ROOT, f'{name}.png'))
    t = Image.open(os.path.join(M, 'spatial_grimoire_trim.png')).convert('RGBA'); px = t.load()
    for y in range(t.height):
        for x in range(t.width):
            r, g, b, a = px[x, y]
            k = 0.45 + 0.6 * min(1.2, (r + g + b) / 450.0)
            px[x, y] = (min(255, int(trim[0] * k)), min(255, int(trim[1] * k)), min(255, int(trim[2] * k)), a)
    t.save(os.path.join(M, f'{name}_trim.png'))
print('ok')
