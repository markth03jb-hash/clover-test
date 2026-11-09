"""Builds the Water / Mirror / Rift grimoire textures by hue-shifting the Dark Grimoire's (same way the other books were made)."""
from PIL import Image
import colorsys, os
ROOT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/iron_spell_grimoire/textures/item')
M = os.path.join(ROOT, 'spell_book_models')
# name: (hue, saturation multiplier, value multiplier, trim colour)
BOOKS = {
    'water_grimoire':  (0.58, 1.15, 1.05, (196, 226, 250)),
    'mirror_grimoire': (0.68, 0.30, 1.30, (222, 222, 236)),
    'rift_grimoire':   (0.84, 1.20, 0.95, (214, 160, 232)),
}

def shift(im, hue, sm, vm, keep_neutral=True):
    im = im.convert('RGBA'); px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0: continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s < 0.08 and keep_neutral:   # whites / greys (the strap, glints) keep their colour, with a faint tint
                s2 = min(1, s + 0.05)
            else:
                s2 = min(1, s * sm)
            nr, ng, nb = colorsys.hsv_to_rgb(hue, s2, min(1, v * vm))
            px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    return im

for name, (hue, sm, vm, trim) in BOOKS.items():
    for part in ('leather', 'front', 'back'):
        shift(Image.open(os.path.join(M, f'dark_grimoire_{part}.png')), hue, sm, vm).save(os.path.join(M, f'{name}_{part}.png'))
    shift(Image.open(os.path.join(ROOT, 'dark_grimoire.png')), hue, sm, vm).save(os.path.join(ROOT, f'{name}.png'))
    t = Image.open(os.path.join(M, 'spatial_grimoire_trim.png')).convert('RGBA'); px = t.load()
    for y in range(t.height):
        for x in range(t.width):
            r, g, b, a = px[x, y]
            L = (r + g + b) / (3 * 150.0)
            k = 0.45 + 0.6 * min(1.2, L)
            px[x, y] = (min(255, int(trim[0] * k)), min(255, int(trim[1] * k)), min(255, int(trim[2] * k)), a)
    t.save(os.path.join(M, f'{name}_trim.png'))
print('ok')
