"""Licht, Rades and Catherine boss assets (original skins): an original 64x64 beast-man skin (shaved head with teal stripes, a dark beard, a fur mantle, a
leather harness over a bare chest, dark trousers and wrapped shins), the spawn egg model, sounds.json and lang."""
import json, os, random
from PIL import Image
R = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources')
A = os.path.join(R, 'assets/iron_spell_grimoire')
rnd = random.Random(11)

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


def new():
    global im, px
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); px = im.load()

def skin(path, slim, head_front, head_side, head_top, hat, body_front, body_side, arm, sleeve, leg):
    new()
    box(0, 0, 8, 8, 8, {'front': head_front, 'top': head_top, 'bottom': head_side, '*': head_side})
    if hat: box(32, 0, 8, 8, 8, {'*': hat, 'top': hat})
    box(16, 16, 8, 12, 4, {'front': body_front, '*': body_side})
    aw = 3 if slim else 4
    for (u, v) in ((40, 16), (32, 48)): box(u, v, aw, 12, 4, {'*': arm})
    if sleeve:
        for (u, v) in ((40, 32), (48, 48)): box(u, v, aw, 12, 4, {'*': sleeve})
    for (u, v) in ((0, 16), (16, 48)): box(u, v, 4, 12, 4, {'*': leg})
    im.save(os.path.join(A, 'textures/entity', path + '.png'))
T = lambda *a: (0, 0, 0, 0)

# Licht (original look): short pale-gold hair, green eyes, a white-and-gold coat with a sword sigil, dark gloves
SK, HAIR, HAIR_D, WHITE, WHITE_D, GOLD, GOLD_D, DARK = (226, 200, 176), (238, 222, 160), (200, 180, 120), (236, 236, 240), (196, 196, 206), (222, 182, 72), (170, 130, 40), (40, 40, 50)
def l_face(x, y, W, H):
    if y <= 1: return jit(HAIR, 6)
    if y == 2 and x in (0, 7): return jit(HAIR_D, 4)
    if y == 3 and x in (1, 6): return (250, 250, 250, 255)
    if y == 3 and x in (2, 5): return (60, 150, 90, 255)
    if y == 5 and x in (3, 4): return (180, 120, 110, 255)
    return jit(SK, 5)
def l_side(x, y, W, H): return jit(HAIR if y <= 3 else SK if y < 7 else HAIR_D, 5)
def l_body(x, y, W, H):
    if x in (3, 4) and 2 <= y <= 9: return jit(GOLD, 6)              # the sword sigil down the front
    if y == 4 and 2 <= x <= 5: return jit(GOLD, 6)
    if y >= 10: return jit(GOLD_D, 4)
    return jit(WHITE if x not in (0, 7) else WHITE_D, 5)
def l_arm(x, y, W, H): return jit(DARK, 4) if y >= 9 else jit(GOLD, 6) if y == 2 else jit(WHITE, 5)
def l_leg(x, y, W, H): return jit(DARK, 4) if y >= 9 else jit(WHITE_D, 5)
# Licht now wears the user's own skin (tools/licht_skin.py adds the detail); this original is no longer written
if False: skin('licht', False, l_face, l_side, lambda *a: jit(HAIR, 8), lambda x, y, W, H: jit(HAIR, 8) if y <= 2 else T(), l_body, lambda x, y, W, H: jit(WHITE_D if y < 10 else GOLD_D, 5), l_arm, None, l_leg)

# Rades (original look): ashen skin, a ragged dark-green coat with stitched seams, a hood, hollow eyes that glow
ASH, COAT, COAT_D, STITCH, GLOW = (150, 160, 150), (40, 66, 44), (24, 40, 28), (150, 140, 110), (140, 255, 120)
def r_face(x, y, W, H):
    if y <= 1 or x in (0, 7): return jit(COAT_D, 5)
    if y == 3 and x in (2, 5): return GLOW + (255,)
    if y == 6 and 2 <= x <= 5: return jit((90, 60, 60), 5)
    if x == 4 and y in (2, 4): return jit(STITCH, 4)
    return jit(ASH, 6)
r_side = lambda x, y, W, H: jit(COAT_D if y <= 4 else ASH, 5)
def r_body(x, y, W, H):
    if x == y % 8 or x == 7 - y % 8: return jit(STITCH, 6)
    return jit(COAT if y < 10 else COAT_D, 6)
skin('rades', False, r_face, r_side, lambda *a: jit(COAT_D, 5), lambda x, y, W, H: jit(COAT, 6) if y <= 2 or x in (0, W - 1) else T(), r_body,
     lambda x, y, W, H: jit(COAT_D, 5), lambda x, y, W, H: jit(ASH, 5) if y >= 10 else jit(COAT, 6) if y % 4 else jit(STITCH, 4), None,
     lambda x, y, W, H: jit((30, 30, 32), 4) if y >= 9 else jit(COAT_D, 5))

# Catherine (original look): an old witch - long grey hair, a tall violet hat brim on the hat layer, a violet robe with
# green curse sigils, a green brooch
GREY, GREY_D, SKW, VIO, VIO_D, CUR = (196, 196, 200), (150, 150, 160), (214, 190, 170), (88, 40, 120), (56, 24, 80), (130, 230, 110)
def c_face(x, y, W, H):
    if y <= 1: return jit(VIO_D, 4)
    if x in (0, 7) or (y >= 6 and x in (0, 1, 6, 7)): return jit(GREY, 5)
    if y == 3 and x in (2, 5): return (130, 230, 110, 255)
    if y == 4 and x == 4: return jit((200, 170, 150), 4)              # a long nose
    if y == 6 and 3 <= x <= 5: return (120, 60, 70, 255)
    return jit(SKW, 5)
c_side = lambda x, y, W, H: jit(GREY if y > 1 else VIO_D, 5)
def c_hat(x, y, W, H): return jit(VIO, 6) if y <= 3 else T()
def c_body(x, y, W, H):
    if (x, y) in ((3, 2), (4, 2)): return CUR + (255,)
    if (x + y) % 5 == 0 and y > 4: return jit(CUR, 10)
    return jit(VIO if y < 11 else VIO_D, 6)
skin('catherine', True, c_face, c_side, lambda *a: jit(VIO, 6), c_hat, c_body, lambda x, y, W, H: jit(VIO_D, 5),
     lambda x, y, W, H: jit(SKW, 5) if y >= 10 else jit(VIO, 6), None, lambda x, y, W, H: jit(VIO_D, 5) if y < 10 else jit((40, 30, 30), 4))

for n in ('licht', 'rades', 'catherine'):
    json.dump({'parent': 'minecraft:item/template_spawn_egg'}, open(os.path.join(A, f'models/item/{n}_spawn_egg.json'), 'w'), indent=1)
SP = os.path.join(A, 'sounds.json'); s = json.load(open(SP))
for n, k in (('licht', 3), ('rades', 1), ('catherine', 1)):
    for i in range(1, k + 1):
        s[f'music.{n}_{i}'] = {'category': 'music', 'subtitle': f'subtitles.iron_spell_grimoire.music.{n}', 'sounds': [{'name': f'iron_spell_grimoire:music/{n}_{i}', 'stream': True}]}
json.dump(s, open(SP, 'w'), indent=2)
LP = os.path.join(A, 'lang/en_us.json'); l = json.load(open(LP))
for n, name in (('licht', 'Licht'), ('rades', 'Rades'), ('catherine', 'Catherine')):
    l[f'entity.iron_spell_grimoire.{n}'] = name
    l[f'item.iron_spell_grimoire.{n}_spawn_egg'] = name + ' Spawn Egg'
    l[f'subtitles.iron_spell_grimoire.music.{n}'] = name + "'s theme"
json.dump(l, open(LP, 'w'), indent=2, ensure_ascii=False)
print('boss3 assets ok')
