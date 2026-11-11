"""Rhya boss assets: an original 64x64 slim skin (a hooded patchwork coat sewn from every grimoire's colours, a blank
mirror mask with one painted eye slit, grey trousers, soft boots), the spawn egg model, sounds.json and lang."""
import json, os, random
from PIL import Image
R = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources')
A = os.path.join(R, 'assets/iron_spell_grimoire')
rnd = random.Random(11)
im = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); px = im.load()
SKIN, SKIN_SH, SKIN_HI = (142, 118, 96), (110, 88, 70), (168, 142, 116)
TEAL, TEAL_D = (60, 220, 170), (24, 130, 100)
BEARD, BEARD_D = (40, 30, 24), (26, 18, 14)
FUR, FUR_D, FUR_L = (88, 62, 40), (60, 40, 26), (130, 98, 64)
LEATHER, LEATHER_D = (70, 46, 30), (46, 30, 20)
TROU, TROU_D = (38, 40, 44), (26, 28, 30)
WRAP, WRAP_D = (170, 158, 130), (130, 118, 94)

def jit(c, a=8): return tuple(max(0, min(255, v + rnd.randint(-a, a))) for v in c) + (255,)
def rect(x0, y0, w, h, f):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w): px[x, y] = f(x - x0, y - y0)
def box(u, v, w, h, d, faces):
    """faces: dict name -> f(x, y, W, H) colour; names top bottom right front left back."""
    spec = {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
            'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}
    for n, (x0, y0, W, H) in spec.items():
        f = faces.get(n, faces.get('*'))
        if f: rect(x0, y0, W, H, lambda x, y, f=f, W=W, H=H: f(x, y, W, H))

# Rhya: hood, mirror mask, patchwork coat
HOOD, HOOD_D = (70, 64, 84), (46, 42, 58)
MASK, MASK_HI, MASK_SH = (222, 226, 236), (255, 255, 255), (170, 176, 192)
PATCH = [(170, 40, 40), (60, 40, 120), (60, 170, 110), (200, 160, 60), (60, 120, 200), (140, 140, 150), (200, 90, 150), (90, 200, 220)]
GREY, GREY_D = (90, 92, 100), (64, 66, 72)
BOOT = (52, 40, 34)
def hood(x, y, W, H): return jit(HOOD_D if y >= H - 2 or x in (0, W - 1) else HOOD, 6)
def mask_front(x, y, W, H):
    if y <= 1: return jit(HOOD_D, 4)                                     # the hood's edge over the brow
    if x in (0, 7): return jit(HOOD, 4)
    if y == 3 and x in (2, 5): return (24, 22, 40, 255)                  # eye slits
    if y == 3 and x in (1, 6): return (90, 70, 200, 255)                 # painted violet marks beside them
    if y == 6 and 3 <= x <= 4: return jit(MASK_SH, 3)
    if (x + y) == 4: return (MASK_HI[0], MASK_HI[1], MASK_HI[2], 255)    # a glint across the mirror
    return jit(MASK if y < 6 else MASK_SH, 4)
box(0, 0, 8, 8, 8, {'front': mask_front, 'top': lambda *a: jit(HOOD, 6), 'bottom': lambda *a: jit(HOOD_D, 4), '*': hood})
box(32, 0, 8, 8, 8, {'top': lambda *a: jit(HOOD, 8), '*': lambda x, y, W, H: jit(HOOD, 6) if y <= 1 else (0, 0, 0, 0)})
def coat(x, y, W, H):
    if y >= H - 1: return jit(GREY_D, 4)
    k = (x // 3 + (y // 3) * 3) % len(PATCH)
    if x % 3 == 2 or y % 3 == 2: return (40, 34, 30, 255)                # stitching between the patches
    return jit(PATCH[k], 10)
box(16, 16, 8, 12, 4, {'*': coat})
box(16, 32, 8, 12, 4, {'*': lambda x, y, W, H: jit(HOOD, 5) if y <= 1 else (0, 0, 0, 0)})   # a hood collar
for (u, v) in ((40, 16), (32, 48)):
    box(u, v, 3, 12, 4, {'*': lambda x, y, W, H: jit(MASK_SH, 4) if y >= 10 else coat(x + 3, y, W, H)})   # slim sleeves, pale hands
for (u, v) in ((0, 16), (16, 48)):
    box(u, v, 4, 12, 4, {'*': lambda x, y, W, H: jit(BOOT, 4) if y >= 9 else jit(GREY_D if x == 0 else GREY, 4)})
os.makedirs(os.path.join(A, 'textures/entity'), exist_ok=True)
# the skin is now the one supplied by the user; this script no longer overwrites it
# im.save(os.path.join(A, 'textures/entity/rhya.png'))
json.dump({'parent': 'minecraft:item/template_spawn_egg'}, open(os.path.join(A, 'models/item/rhya_spawn_egg.json'), 'w'), indent=1)
SP = os.path.join(A, 'sounds.json'); s = json.load(open(SP))
for i in (1, 2, 3):
    s[f'music.rhya_{i}'] = {'category': 'music', 'subtitle': 'subtitles.iron_spell_grimoire.music.rhya',
                            'sounds': [{'name': f'iron_spell_grimoire:music/rhya_{i}', 'stream': True}]}
json.dump(s, open(SP, 'w'), indent=2)
LP = os.path.join(A, 'lang/en_us.json'); l = json.load(open(LP))
l.update({'entity.iron_spell_grimoire.rhya': 'Rhya the Disloyal', 'item.iron_spell_grimoire.rhya_spawn_egg': 'Rhya Spawn Egg',
          'subtitles.iron_spell_grimoire.music.rhya': "Rhya's theme"})
json.dump(l, open(LP, 'w'), indent=2, ensure_ascii=False)
print('rhya assets ok')
