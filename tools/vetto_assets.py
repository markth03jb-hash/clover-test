"""Vetto boss assets: an original 64x64 beast-man skin (shaved head with teal stripes, a dark beard, a fur mantle, a
leather harness over a bare chest, dark trousers and wrapped shins), the spawn egg model, sounds.json and lang."""
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

# head: shaved, teal stripes along the sides, heavy brow, beard
def head_side(x, y, W, H):
    if y >= 5: return jit(BEARD_D if y >= 6 else BEARD, 5)
    if y in (1, 3) and 1 <= x <= 6: return jit(TEAL if y == 1 else TEAL_D, 4)
    return jit(SKIN_SH if y == 0 else SKIN, 6)
def head_front(x, y, W, H):
    if y == 2: return jit(BEARD_D, 3) if x not in (3, 4) else jit(SKIN_SH, 4)          # brow
    if y == 3:
        if x in (1, 6): return (235, 235, 225, 255)
        if x in (2, 5): return (30, 90, 70, 255)                                        # eyes, a green-black iris
        return jit(SKIN, 5)
    if y == 4: return jit(SKIN_SH if x in (3, 4) else SKIN, 5)                           # nose
    if y == 5: return jit(BEARD, 5) if x not in (3, 4) else (60, 30, 26, 255)            # moustache, mouth
    if y >= 6: return jit(BEARD_D if (x + y) % 3 == 0 else BEARD, 4)
    if y == 0 and x in (3, 4): return jit(TEAL, 4)                                       # a teal mark on the crown
    return jit(SKIN, 6)
box(0, 0, 8, 8, 8, {'top': lambda x, y, W, H: jit(TEAL if x in (3, 4) else SKIN_HI, 6), 'bottom': lambda *a: jit(BEARD_D, 4),
                    'front': head_front, 'right': head_side, 'left': head_side,
                    'back': lambda x, y, W, H: jit(TEAL_D if y in (1, 3) else SKIN_SH, 6)})
# body: bare chest, a diagonal harness strap, belt; fur mantle on the jacket layer over the shoulders
def chest(x, y, W, H):
    if y >= 10: return jit(LEATHER_D if y == 11 else LEATHER, 4) if x not in (3, 4) or y == 11 else (160, 140, 90, 255)
    if abs((x) - (y * 0.7)) < 1.0: return jit(LEATHER, 5)
    if y in (4, 5) and x in (3, 4): return jit(SKIN_SH, 4)
    if y == 3 and x in (1, 2, 5, 6): return jit(SKIN_SH, 4)
    if y in (6, 8) and x in (2, 5): return jit(TEAL_D, 3)
    return jit(SKIN, 6)
box(16, 16, 8, 12, 4, {'front': chest, 'back': lambda x, y, W, H: jit(LEATHER if y >= 10 else SKIN_SH, 5), '*': lambda x, y, W, H: jit(LEATHER if y >= 10 else SKIN, 5)})
def mantle(x, y, W, H):
    if y <= 2: return jit(FUR_L if (x + y) % 2 else FUR, 8)
    if y == 3 and (x * 7) % 3: return jit(FUR_D, 6)
    return (0, 0, 0, 0)
box(16, 32, 8, 12, 4, {'top': lambda *a: jit(FUR_L, 10), '*': mantle})
# arms: bare upper arms with teal bands, leather bracers below; fur on the sleeve tops
def arm(x, y, W, H):
    if y >= 7: return jit(LEATHER_D if y in (7, 11) else LEATHER, 5)
    if y == 3: return jit(TEAL, 5)
    return jit(SKIN, 6)
for (u, v) in ((40, 16), (32, 48)):
    box(u, v, 4, 12, 4, {'top': lambda *a: jit(SKIN_HI, 5), 'bottom': lambda *a: jit(SKIN_SH, 5), '*': arm})
def sleeve(x, y, W, H):
    if y <= 1: return jit(FUR if (x + y) % 2 else FUR_L, 8)
    return (0, 0, 0, 0)
for (u, v) in ((40, 32), (48, 48)):
    box(u, v, 4, 12, 4, {'top': lambda *a: jit(FUR_L, 8), '*': sleeve})
# legs: dark trousers, wrapped shins
def leg(x, y, W, H):
    if y >= 7: return jit(WRAP_D if (y + x) % 3 == 0 else WRAP, 5)
    return jit(TROU_D if x == 0 else TROU, 4)
for (u, v) in ((0, 16), (16, 48)):
    box(u, v, 4, 12, 4, {'top': lambda *a: jit(TROU, 4), 'bottom': lambda *a: jit(LEATHER_D, 4), '*': leg})
os.makedirs(os.path.join(A, 'textures/entity'), exist_ok=True)
im.save(os.path.join(A, 'textures/entity/vetto.png'))

json.dump({'parent': 'minecraft:item/template_spawn_egg'}, open(os.path.join(A, 'models/item/vetto_spawn_egg.json'), 'w'), indent=1)
SP = os.path.join(A, 'sounds.json'); s = json.load(open(SP))
for i in (1, 2, 3):
    s[f'music.vetto_{i}'] = {'category': 'music', 'subtitle': 'subtitles.iron_spell_grimoire.music.vetto',
                             'sounds': [{'name': f'iron_spell_grimoire:music/vetto_{i}', 'stream': True}]}
json.dump(s, open(SP, 'w'), indent=2)
LP = os.path.join(A, 'lang/en_us.json'); l = json.load(open(LP))
l.update({'entity.iron_spell_grimoire.vetto': 'Vetto, Beast of Despair', 'item.iron_spell_grimoire.vetto_spawn_egg': 'Vetto Spawn Egg',
          'subtitles.iron_spell_grimoire.music.vetto': "Vetto's theme"})
json.dump(l, open(LP, 'w'), indent=2, ensure_ascii=False)
print('vetto assets ok')
