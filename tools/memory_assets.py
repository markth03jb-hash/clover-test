"""Lost Memory Pages assets: the three Memory Guardians' skins (64x64 player layout, original designs - the Copyist's
Echo, Taddeus the warden, Elowen the first binder), the torn Memory Page icon and the three unique upgrades' icons
(Binder's Quill, Warden's Key, Binder's Pen), with their item models, plus a preview sheet."""
import json, os, math, random
from PIL import Image, ImageDraw, ImageFilter
R = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/iron_spell_grimoire')
ENT = os.path.join(R, 'textures/entity'); TEX = os.path.join(R, 'textures/item'); MOD = os.path.join(R, 'models/item')
PREV = '/tmp/claude-0/-home-claude/267cab68-cab5-5d3b-ab2c-ac307f4fb5bc/scratchpad/memory_preview.png'

PARTS = {  # name: (U, V, w, h, d), overlay (U, V)
    'head': ((0, 0, 8, 8, 8), (32, 0)), 'body': ((16, 16, 8, 12, 4), (16, 32)),
    'rarm': ((40, 16, 4, 12, 4), (40, 32)), 'larm': ((32, 48, 4, 12, 4), (48, 48)),
    'rleg': ((0, 16, 4, 12, 4), (0, 32)), 'lleg': ((16, 48, 4, 12, 4), (0, 48))}

def faces(U, V, w, h, d):
    return {'top': (U + d, V, w, d), 'bottom': (U + d + w, V, w, d), 'right': (U, V + d, d, h), 'front': (U + d, V + d, w, h),
            'left': (U + d + w, V + d, d, h), 'back': (U + 2 * d + w, V + d, w, h)}

class Skin:
    def __init__(s): s.im = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); s.px = s.im.load(); s.rnd = random.Random(7)
    def f(s, part, face, over=False):
        (U, V, w, h, d), (OU, OV) = PARTS[part]
        if over: U, V = OU, OV
        return faces(U, V, w, h, d)[face]
    def paint(s, part, fn, over=False, faces_=('top', 'bottom', 'right', 'front', 'left', 'back')):
        """fn(face, x, y, w, h) -> colour or None, x/y local to the face."""
        for fc in faces_:
            x0, y0, w, h = s.f(part, fc, over)
            for y in range(h):
                for x in range(w):
                    c = fn(fc, x, y, w, h)
                    if c is not None: s.px[x0 + x, y0 + y] = tuple(c) + ((255,) if len(c) == 3 else ())
    def at(s, part, face, x, y, c, over=False):
        x0, y0, w, h = s.f(part, face, over)
        if 0 <= x < w and 0 <= y < h: s.px[x0 + x, y0 + y] = tuple(c) + ((255,) if len(c) == 3 else ())
    def noise(s, c, k=6):
        n = s.rnd.randint(-k, k); return tuple(max(0, min(255, v + n)) for v in c[:3])

def shade(c, k): return tuple(max(0, min(255, int(v * k))) for v in c[:3])

def head(sk, skin, hair, eyes, hair_front_rows=2, fringe=None, beard=None, brows=None):
    def fn(fc, x, y, w, h):
        if fc == 'top': return sk.noise(hair)
        if fc == 'bottom': return shade(skin, 0.8) if beard is None else sk.noise(beard)
        if fc == 'back': return sk.noise(hair)
        if fc in ('right', 'left'):
            if y < 3 or (fc == 'right' and x < 3) or (fc == 'left' and x > 4): return sk.noise(hair)
            if beard and y >= 5: return sk.noise(beard)
            return sk.noise(skin, 3)
        # front
        if y < hair_front_rows: return sk.noise(hair)
        if fringe and y < hair_front_rows + 1 and x in fringe: return sk.noise(hair)
        if y == 3 and brows: return brows if x in (1, 2, 5, 6) else sk.noise(skin, 3)
        if y == 4 and x in (1, 2, 5, 6): return eyes if x in (2, 5) else (250, 250, 255)
        if beard and (y >= 6 or (y == 5 and x in (0, 7))): return sk.noise(beard)
        if y == 6 and x in (3, 4): return shade(skin, 0.72)
        return sk.noise(skin, 3)
    sk.paint('head', fn)

def save_skin(sk, name): sk.im.save(os.path.join(ENT, name + '.png'))

# ------------------------------------------------------------------ 0: the Copyist's Echo - a young scribe, made of her own ink
sk = Skin()
SKIN0, HAIR0, EYE0 = (198, 206, 222), (26, 24, 40), (210, 230, 255)
CREAM, INK, STRAP, BOOT = (214, 206, 182), (22, 20, 34), (120, 80, 50), (70, 50, 40)
head(sk, SKIN0, HAIR0, EYE0, 2, fringe=(0, 1, 6, 7))
def hat0(fc, x, y, w, h):   # her hair falling to the shoulders, and a quill behind the right ear
    if fc == 'back': return sk.noise(HAIR0) if y >= 0 else None
    if fc in ('right', 'left'): return sk.noise(HAIR0) if (y >= 2 and ((fc == 'right' and x < 2) or (fc == 'left' and x > 5))) else None
    if fc == 'top': return None
    if fc == 'front': return sk.noise(HAIR0) if y < 2 and x not in (3, 4) else None
    return None
sk.paint('head', hat0, over=True)
for i, (x, y) in enumerate(((3, 0), (4, 1), (5, 1), (5, 2))): sk.at('head', 'right', x, y, (240, 240, 250), over=True)
def body0(fc, x, y, w, h):
    if fc in ('top', 'bottom'): return sk.noise(CREAM if fc == 'top' else INK)
    if y >= 9: return sk.noise(INK, 3)   # the ink-dark apron hem
    c = sk.noise(CREAM, 5)
    if fc == 'front':
        if x == y or x == y + 1: return sk.noise(STRAP, 4)   # satchel strap, shoulder to hip
        if y >= 5 and 2 <= x <= 5: return sk.noise(INK, 3)   # apron bib
    if (x * 7 + y * 3) % 11 == 0: return sk.noise(INK, 5)     # ink spots
    return c
sk.paint('body', body0)
def jack0(fc, x, y, w, h):   # ink running down from the hem
    if fc in ('front', 'back') and y >= 10 and x in (1, 4, 6): return (16, 14, 26, 255)
    if fc == 'front' and y == 11 and x in (2, 5): return (16, 14, 26, 255)
    return None
sk.paint('body', jack0, over=True)
def arm0(fc, x, y, w, h):
    if fc == 'top': return sk.noise(CREAM)
    if fc == 'bottom': return sk.noise(INK)
    if y >= 9: return sk.noise(INK, 3) if y >= 10 else sk.noise(shade(SKIN0, 0.7))   # ink-stained hands
    if y == 8: return sk.noise(SKIN0, 2)
    return sk.noise(CREAM, 5) if y < 7 else sk.noise(shade(CREAM, 0.8))
sk.paint('rarm', arm0); sk.paint('larm', arm0)
def leg0(fc, x, y, w, h):
    if fc == 'top': return sk.noise(INK)
    if y >= 8 or fc == 'bottom': return sk.noise(BOOT, 4)
    return sk.noise((52, 50, 62), 4)
sk.paint('rleg', leg0); sk.paint('lleg', leg0)
save_skin(sk, 'memory_guardian_0')

# ------------------------------------------------------------------ 1: Taddeus, Warden of the Stair - an old knight in plate and a blue tabard
sk = Skin()
SKIN1, HAIR1, EYE1 = (196, 182, 170), (150, 150, 156), (140, 230, 255)
STEEL, STEEL_D, TABARD, GOLD, BELT = (158, 166, 178), (96, 104, 118), (38, 58, 112), (214, 172, 70), (80, 56, 36)
head(sk, SKIN1, HAIR1, EYE1, 1, beard=(168, 168, 172), brows=(120, 120, 126))
def body1(fc, x, y, w, h):
    if fc == 'top': return sk.noise(STEEL)
    if fc == 'bottom': return sk.noise(TABARD)
    if y == 7: return sk.noise(BELT, 3) if not (fc == 'front' and x in (3, 4)) else GOLD
    if fc in ('front', 'back') and 1 <= x <= 6 and y >= 1:
        c = sk.noise(TABARD, 4)
        if x in (1, 6): c = sk.noise(GOLD, 6)   # gold edging
        if fc == 'front' and y < 7:   # the key, on his chest
            key = {(3, 2), (4, 2), (3, 3), (4, 3), (3, 4), (3, 5), (3, 6), (4, 5)}
            if (x, y) in key: return GOLD
        return c
    c = sk.noise(STEEL, 6)
    if y in (0, 4): c = sk.noise(STEEL_D, 4)
    return c
sk.paint('body', body1)
def arm1(fc, x, y, w, h):
    if fc in ('top',): return sk.noise(STEEL)
    if y in (0, 1): return sk.noise(STEEL_D if y == 1 else STEEL, 4)   # pauldron
    if y in (4, 8): return sk.noise(STEEL_D, 3)
    if y >= 10: return sk.noise((120, 126, 138), 4)   # gauntlet
    return sk.noise(STEEL, 6)
sk.paint('rarm', arm1); sk.paint('larm', arm1)
def sleeve1(fc, x, y, w, h):   # the pauldron's rim stands off the arm
    if y == 0 and fc != 'bottom': return (180, 188, 200, 255)
    if fc == 'top': return (170, 178, 190, 255)
    return None
sk.paint('rarm', sleeve1, over=True); sk.paint('larm', sleeve1, over=True)
def leg1(fc, x, y, w, h):
    if fc == 'top': return sk.noise(TABARD)
    if y < 3 and fc in ('front', 'back'): return sk.noise(TABARD, 3)
    if y == 6: return sk.noise(STEEL_D, 3)   # knee
    if y >= 10: return sk.noise((70, 74, 84), 3)
    return sk.noise(STEEL, 6)
sk.paint('rleg', leg1); sk.paint('lleg', leg1)
save_skin(sk, 'memory_guardian_1')

# ------------------------------------------------------------------ 2: Elowen, the First Binder - an old mage in violet, threaded with gold
sk = Skin()
SKIN2, HAIR2, EYE2 = (226, 214, 200), (232, 230, 222), (255, 210, 90)
ROBE, ROBE_D, THREAD, SASH = (70, 44, 104), (48, 30, 74), (220, 178, 74), (24, 20, 30)
head(sk, SKIN2, HAIR2, EYE2, 2, fringe=(0, 7), brows=(200, 198, 190))
sk.at('head', 'front', 2, 5, shade(SKIN2, 0.86)); sk.at('head', 'front', 5, 5, shade(SKIN2, 0.86))   # lines of age
def hat2(fc, x, y, w, h):   # long white hair down her back, a thin gold circlet
    if fc == 'front': return THREAD if y == 1 else None
    if fc in ('right', 'left'):
        if y == 1: return THREAD
        return sk.noise(HAIR2) if (y >= 2 and ((fc == 'right' and x < 3) or (fc == 'left' and x > 4))) else None
    if fc == 'back': return sk.noise(HAIR2) if y != 1 else THREAD
    if fc == 'top': return sk.noise(HAIR2) if (x + y) % 3 == 0 else None
    return None
sk.paint('head', hat2, over=True)
def body2(fc, x, y, w, h):
    if fc == 'top': return sk.noise(ROBE)
    if fc == 'bottom': return sk.noise(ROBE_D)
    if y == 7: return sk.noise(SASH, 3)
    if fc == 'front' and x in (3, 4): return sk.noise(THREAD, 6) if y != 7 else SASH   # the gold binding down the front
    if fc == 'back' and y >= 8 and x in (2, 5): return sk.noise(HAIR2, 3)
    c = sk.noise(ROBE, 5)
    if fc == 'back' and y < 8 and 1 <= x <= 6: c = sk.noise(HAIR2, 4)   # her hair over the back
    return c
sk.paint('body', body2)
def jack2(fc, x, y, w, h):   # the robe's collar, standing up
    if y == 0 and fc in ('front', 'right', 'left', 'back') and not (fc == 'front' and x in (3, 4)): return (*ROBE_D, 255)
    return None
sk.paint('body', jack2, over=True)
def arm2(fc, x, y, w, h):
    if fc == 'top': return sk.noise(ROBE)
    if fc == 'bottom': return sk.noise(SKIN2, 3)
    if y >= 10: return sk.noise(SKIN2, 3)
    if y == 9: return sk.noise(THREAD, 5)   # gold cuff
    return sk.noise(ROBE, 5) if y < 8 else sk.noise(ROBE_D, 4)
sk.paint('rarm', arm2); sk.paint('larm', arm2)
def sleeve2(fc, x, y, w, h):   # wide sleeves hanging at the cuff
    if fc in ('front', 'back', 'right', 'left') and y in (8, 9, 10): return (*(THREAD if y == 10 else ROBE_D), 255)
    return None
sk.paint('rarm', sleeve2, over=True); sk.paint('larm', sleeve2, over=True)
def leg2(fc, x, y, w, h):   # the robe's skirt, hemmed in gold
    if fc == 'top': return sk.noise(ROBE)
    if fc == 'bottom': return sk.noise((40, 32, 30))
    if y == 10: return sk.noise(THREAD, 5)
    if y == 11: return sk.noise((40, 32, 30), 3)
    return sk.noise(ROBE if y < 6 else ROBE_D, 5)
sk.paint('rleg', leg2); sk.paint('lleg', leg2)
save_skin(sk, 'memory_guardian_2')

# ------------------------------------------------------------------ item icons (32px)
N = 32
def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))
def new(): im = Image.new('RGBA', (N, N), (0, 0, 0, 0)); return im, ImageDraw.Draw(im)
def finish(im, glow=None, outline=(16, 10, 14, 255)):
    px = im.load(); a = [[px[x, y][3] > 0 for x in range(N)] for y in range(N)]
    out = im.copy(); po = out.load()
    for y in range(N):
        for x in range(N):
            if a[y][x]:
                r, g, b, al = px[x, y]
                if (y > 0 and not a[y-1][x]) or (x > 0 and not a[y][x-1]): po[x, y] = (min(255, r+40), min(255, g+40), min(255, b+40), al)
                elif (y < N-1 and not a[y+1][x]) or (x < N-1 and not a[y][x+1]): po[x, y] = (int(r*.62), int(g*.62), int(b*.62), al)
                continue
            if any(0 <= x+dx < N and 0 <= y+dy < N and a[y+dy][x+dx] for dx, dy in ((1,0),(-1,0),(0,1),(0,-1))): po[x, y] = outline
    if glow:
        g = Image.new('RGBA', (N, N), (0,0,0,0)); pg = g.load()
        for y in range(N):
            for x in range(N):
                if po[x, y][3] > 0: pg[x, y] = glow
        g = g.filter(ImageFilter.GaussianBlur(1.6)); gp = g.load()
        for y in range(N):
            for x in range(N):
                r, gg, b, al = gp[x, y]; gp[x, y] = (r, gg, b, min(110, int(al*.8)))
        g.alpha_composite(out); out = g
    return out
T = {}
# the torn page: parchment, one edge ripped, lines of faded ink and a gold seal
im, d = new()
pts = [(6, 3), (25, 2), (27, 28)] + [(27 - i * 2, 28 + (1 if i % 2 else -1) * (i % 3)) for i in range(1, 11)] + [(5, 27)]
d.polygon(pts, fill=(222, 204, 160, 255))
d.polygon([(6, 3), (25, 2), (25, 6), (7, 7)], fill=(236, 222, 184, 255))
for y in range(9, 25, 3): d.line([(9, y), (23 - (y * 3) % 5, y)], fill=(96, 70, 50, 255))
d.ellipse([18, 19, 24, 25], fill=(214, 168, 64, 255)); d.point((20, 21), fill=(255, 236, 160, 255))
T['memory_page'] = finish(im, (255, 220, 140, 255))
# Binder's Quill: a long white quill dipped in ink, with a gold band
im, d = new()
for i in range(18):
    t = i / 17; cx, cy = 26 - i, 4 + i
    w = int(1 + 4 * math.sin(t * math.pi * 0.9))
    d.line([(cx - w, cy - w // 2), (cx + 1, cy)], fill=lerp((250, 250, 255, 255), (200, 206, 226, 255), t))
d.line([(26, 4), (8, 22)], fill=(170, 176, 196, 255))
d.line([(8, 22), (4, 27)], fill=(30, 28, 44, 255), width=2); d.point((3, 28), fill=(20, 18, 30, 255))
d.line([(9, 21), (11, 19)], fill=(220, 178, 74, 255), width=2)
T['binders_quill'] = finish(im, (200, 210, 255, 255))
# Warden's Key: a heavy steel key with a gold bow
im, d = new()
d.ellipse([3, 3, 14, 14], fill=(214, 172, 70, 255)); d.ellipse([6, 6, 11, 11], fill=(0, 0, 0, 0))
d.line([(12, 12), (26, 26)], fill=(150, 158, 172, 255), width=3)
d.polygon([(21, 25), (25, 21), (28, 24), (24, 28)], fill=(130, 138, 152, 255)); d.rectangle([22, 27, 24, 29], fill=(130, 138, 152, 255))
d.point((8, 4), fill=(255, 236, 160, 255))
T['wardens_key'] = finish(im, (150, 200, 255, 255))
# Binder's Pen: a violet-and-gold pen with a glowing nib, trailing gold thread
im, d = new()
d.line([(25, 4), (9, 20)], fill=(70, 44, 104, 255), width=4)
d.line([(24, 5), (10, 19)], fill=(110, 74, 150, 255), width=1)
for k in (7, 13): d.line([(25 - k - 1, 4 + k - 1), (25 - k + 1, 4 + k + 1)], fill=(220, 178, 74, 255), width=2)
d.polygon([(9, 19), (11, 21), (5, 26)], fill=(255, 220, 120, 255)); d.point((5, 26), fill=(255, 255, 230, 255))
d.arc([2, 20, 16, 31], 120, 300, fill=(230, 190, 90, 255))
T['binders_pen'] = finish(im, (255, 210, 110, 255))
for k, v in T.items():
    v.save(os.path.join(TEX, k + '.png'))
    json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'iron_spell_grimoire:item/' + k}}, open(os.path.join(MOD, k + '.json'), 'w'), indent=1)

# preview: icons and the skins' fronts, big
sheet = Image.new('RGBA', (4 * 160 + 3 * 200, 260), (40, 40, 50, 255))
for i, v in enumerate(T.values()): sheet.paste(v.resize((128, 128), Image.NEAREST), (16 + i * 160, 60), v.resize((128, 128), Image.NEAREST))
for i in range(3):
    s = Image.open(os.path.join(ENT, 'memory_guardian_%d.png' % i)); fig = Image.new('RGBA', (16, 32), (0, 0, 0, 0))
    fig.paste(s.crop((8, 8, 16, 16)), (4, 0)); fig.alpha_composite(s.crop((40, 8, 48, 16)), (4, 0))
    fig.paste(s.crop((20, 20, 28, 32)), (4, 8)); fig.alpha_composite(s.crop((20, 36, 28, 48)), (4, 8))
    fig.paste(s.crop((44, 20, 48, 32)), (0, 8)); fig.alpha_composite(s.crop((44, 36, 48, 48)), (0, 8))
    fig.paste(s.crop((36, 52, 40, 64)), (12, 8)); fig.alpha_composite(s.crop((52, 52, 56, 64)), (12, 8))
    fig.paste(s.crop((4, 20, 8, 32)), (4, 20)); fig.paste(s.crop((20, 52, 24, 64)), (8, 20))
    big = fig.resize((112, 224), Image.NEAREST); sheet.paste(big, (4 * 160 + i * 200 + 40, 18), big)
sheet.save(PREV)
