"""The Ossuarch's third phase - the Marrow Colossus: an original design. Builds its GeckoLib model, box-UV texture and previews."""
import json, math, os, random, sys
from PIL import Image, ImageDraw, ImageFilter

from PIL import Image, ImageDraw, ImageFilter

OUT = '/tmp/claude-0/-home-claude/267cab68-cab5-5d3b-ab2c-ac307f4fb5bc/scratchpad'
R = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/iron_spell_grimoire')
NAME = 'ossuarch_gravebound'
rnd = random.Random(777)

# ---------------------------------------------------------------------------------------------------------------- model
BONES = {}   # name -> dict(parent, pivot, rot, cubes=[(origin, size, material)])
ORDER = []
def bone(name, parent, pivot, rot=(0, 0, 0)):
    BONES[name] = dict(parent=parent, pivot=list(pivot), rot=list(rot), cubes=[])
    ORDER.append(name)
def cube(b, origin, size, mat, inflate=0):
    BONES[b]['cubes'].append((list(origin), list(size), mat, inflate))

def mirror(name, src_names, fn):
    """Build a bone chain for the right side, then mirror it in x for the left side (names _r / _l)."""
    pass

def sym(fn):
    """Call fn(side, sx) for the right (+x, '_r') and left (-x, '_l') sides."""
    fn('_r', 1); fn('_l', -1)

def box(b, x0, y0, z0, x1, y1, z1, mat, sx=1):
    """A cube from corner to corner (mirrored in x when sx = -1)."""
    if sx < 0: x0, x1 = -x1, -x0
    cube(b, (min(x0, x1), min(y0, y1), min(z0, z1)), (abs(x1 - x0), abs(y1 - y0), abs(z1 - z0)), mat)


# ---------------------------------------------------------------------------------------------------------------- model
# The Grave-Bound: in his last phase the Ossuarch stays a man, but the grave answers him. Human in shape, in a long coat
# of maroon and ash over dark clothes, his face behind a smooth bone half-mask with one glowing slit. The demonic parts
# grow around him: his left arm swells into a huge arm of black stone split with glowing cracks, ending in long claws;
# chains of grave-light wind round his chest and trail hooked ends; a ring of floating bone shards turns behind his
# shoulders; and the cracks run up his neck and across his chest.
import math as _m
bone('root', None, (0, 0, 0))
bone('hips', 'root', (0, 24, 0))
box('hips', -4.5, 22, -2.5, 4.5, 26, 2.5, 'cloth2')
box('hips', -4.8, 24.5, -2.8, 4.8, 25.5, 2.8, 'leather')
def legs(s, sx):
    bone('thigh' + s, 'hips', (2.2 * sx, 23, 0))
    box('thigh' + s, 0.4, 12, -1.9, 4.2, 24, 1.9, 'cloth2', sx)
    bone('shin' + s, 'thigh' + s, (2.3 * sx, 12, 0))
    box('shin' + s, 0.6, 1.5, -1.7, 4.0, 12.5, 1.7, 'cloth2', sx)
    box('shin' + s, 0.1, 1.5, -2.3, 4.5, 6, 2.3, 'leather', sx)  # boots
    bone('foot' + s, 'shin' + s, (2.3 * sx, 1.5, 0))
    box('foot' + s, 0.1, 0, -3.6, 4.5, 1.6, 2.3, 'leather', sx)
sym(legs)
bone('body', 'hips', (0, 26, 0))
box('body', -3.8, 26, -2.2, 3.8, 32, 2.2, 'cloth2')
box('body', -4.6, 32, -2.5, 4.6, 38, 2.5, 'cloth2')
box('body', -4.6, 26, -2.6, -1.0, 38, -2.4, 'cloth'); box('body', 1.0, 26, -2.6, 4.6, 38, -2.4, 'cloth')   # the coat, open at the front
box('body', -4.6, 26, 2.4, 4.6, 38, 2.6, 'cloth')
box('body', -4.8, 36.5, -2.7, 4.8, 38.5, 2.7, 'cloth')                  # its collar
box('body', -1.2, 38, -1.2, 1.2, 40.8, 1.2, 'skin')                    # the neck
for i, (x0, y0, x1, y1) in enumerate([(-0.8, 29, 0.2, 35), (0.2, 31, 1.2, 33), (-0.4, 26.5, 0.4, 29)]):   # cracks glowing on the chest
    box('body', x0, y0, -2.5, x1, y1, -2.42, 'glow')
bone('coattail', 'body', (0, 26, 0))                               # the coat's long tails, to swing
box('coattail', -5.2, 9, 2.4, 5.2, 26, 2.8, 'cloth')
box('coattail', -4.6, 9, -2.8, -1.2, 26, -2.4, 'cloth'); box('coattail', 1.2, 9, -2.8, 4.6, 26, -2.4, 'cloth')
for x in (-4.4, -1.6, 1.2):
    box('coattail', x, 6 + rnd.randint(0, 3), 2.45, x + 2.6, 9, 2.75, 'cloth')
# chains of grave-light round the chest, their hooked ends trailing
bone('chains', 'body', (0, 32, 0))
for k in range(16):
    a = 2 * _m.pi * k / 16
    x, z, y = _m.cos(a) * 5.4, _m.sin(a) * 3.6, 28 + k * 0.55
    box('chains', x - 0.45, y - 0.45, z - 0.45, x + 0.45, y + 0.45, z + 0.45, 'chain' if k % 2 else 'glow2')
for side in (-1, 1):
    for k in range(7):
        x, y, z = side * (5.6 + k * 0.9), 30 - k * 1.4, 1 + k * 0.6
        box('chains', x - 0.4, y - 0.4, z - 0.4, x + 0.4, y + 0.4, z + 0.4, 'chain' if k % 2 else 'glow2')
    box('chains', side * 12 - 0.5, 19, 4.6, side * 12 + 0.5, 21.5, 5.6, 'bonetip')   # the hooks
    box('chains', side * 12 - 0.5, 19, 3.6, side * 12 + 0.5, 20, 4.6, 'bonetip')

# the right arm: a man's arm in the coat's sleeve
bone('arm_r', 'body', (5.5, 37, 0), (0, 0, 6))
box('arm_r', 4.6, 26, -1.7, 7.8, 38, 1.7, 'cloth', 1)
bone('forearm_r', 'arm_r', (6.2, 26, 0), (-8, 0, 0))
box('forearm_r', 4.8, 16, -1.5, 7.6, 26.5, 1.5, 'cloth', 1)
box('forearm_r', 4.6, 16, -1.8, 7.8, 18, 1.8, 'cloth2', 1)
bone('hand_r', 'forearm_r', (6.2, 16, 0))
box('hand_r', 5.1, 13, -1.2, 7.3, 16, 1.2, 'skin', 1)
for z in (-0.9, -0.3, 0.3, 0.9): box('hand_r', 5.2 + 0.6 * abs(z), 11.6, z - 0.25, 5.8 + 0.6 * abs(z), 13, z + 0.25, 'skin', 1)
# the left arm: the demon's - heavier than a man's, black stone split with thin lines of light, long claws
bone('arm_l', 'body', (-5.5, 37, 0), (0, 0, -10))
box('arm_l', -9, 33, -2.6, -4.6, 39.5, 2.6, 'stone')               # a swollen shoulder...
box('arm_l', -8.4, 27, -2.0, -5.0, 33, 2.0, 'stone')               # ...narrowing to the elbow
for i in range(3): box('arm_l', -9.2 + i * 1.6, 39.5, -0.5, -8.4 + i * 1.6, 42 - i * 0.6, 0.5, 'stonetip')   # spurs
box('arm_l', -9.05, 34, -2.65, -8.95, 38.5, -1.8, 'glow')
bone('forearm_l', 'arm_l', (-6.7, 27, 0), (-10, 0, 3))
box('forearm_l', -9.0, 21, -2.6, -4.4, 27.5, 2.6, 'stone')         # the forearm thickens toward the hand
box('forearm_l', -9.4, 15, -2.9, -4.0, 21, 2.9, 'stone')
box('forearm_l', -9.45, 17, -2.95, -9.35, 24, -2.0, 'glow')
bone('hand_l', 'forearm_l', (-6.7, 15, 0), (8, 0, 0))
box('hand_l', -9.4, 11, -2.8, -4.0, 15.2, 2.8, 'stone')
for i, z in enumerate((-2.1, -0.7, 0.7, 2.1)):                      # knuckles and long claws
    box('hand_l', -7.5, 10.4, z - 0.6, -6.1, 11.4, z + 0.6, 'stonetip')
    c = 'claw%d_l' % i
    bone(c, 'hand_l', (-6.8, 10.8, z), (14, 0, 0))
    box(c, -7.4, 5, z - 0.5, -6.2, 11, z + 0.5, 'stone')
    box(c, -7.1, 2, z - 0.3, -6.5, 5, z + 0.3, 'clawglow')
box('hand_l', -4.4, 11.4, -2.8, -3.4, 14, -1.6, 'stonetip')          # the thumb

bone('head', 'body', (0, 40.5, 0))
box('head', -2.6, 40.6, -2.6, 2.6, 46.6, 2.6, 'skin')             # a man's face, gaunt
box('head', -2.0, 40.4, -2.8, 2.0, 41.4, -2.5, 'skin')            # the jaw
box('head', -0.4, 42.6, -3.0, 0.4, 44.2, -2.6, 'skin')            # the nose
box('head', -1.9, 43.9, -2.65, -0.7, 44.5, -2.55, 'eye'); box('head', 0.7, 43.9, -2.65, 1.9, 44.5, -2.55, 'eye')   # pale burning eyes
box('head', -2.2, 44.7, -2.7, -0.5, 45.0, -2.6, 'brow'); box('head', 0.5, 44.7, -2.7, 2.2, 45.0, -2.6, 'brow')
box('head', -1.2, 41.6, -2.65, 1.2, 41.9, -2.58, 'lip')
box('head', -2.8, 45.6, -2.8, 2.8, 47.4, 2.9, 'ash')              # ash-white hair, drained by the grave, swept back
box('head', -2.8, 42.5, 0.4, 2.8, 46.0, 3.1, 'ash')
box('head', -2.0, 40.0, 2.2, 2.0, 43.0, 3.4, 'ash')               # tied at the nape
box('head', 1.8, 40.9, -2.66, 2.2, 42.4, -2.58, 'glow')           # one crack of grave-light on his cheek
bone('hood', 'body', (0, 39, 2))                                   # the hood, down round his shoulders
box('hood', -4.2, 37.5, -1, 4.2, 40, 4, 'cloth')
box('hood', -3.6, 34, 2.6, 3.6, 39, 4.2, 'cloth')

bone('halo', 'body', (0, 38, 4.5))                                  # bone shards turning behind his shoulders
for i in range(12):
    a = 2 * _m.pi * i / 12
    for k in range(3):
        r0 = 8 + k * 1.4
        x, y = _m.cos(a) * r0, 38 + _m.sin(a) * r0
        sz = 0.9 - k * 0.2
        box('halo', x - sz, y - sz, 4.3, x + sz, y + sz, 5.1, 'bonetip' if k == 2 else 'bone')
for i in range(24):
    a = 2 * _m.pi * (i + 0.5) / 24
    box('halo', _m.cos(a) * 7.2 - 0.4, 38 + _m.sin(a) * 7.2 - 0.4, 4.5, _m.cos(a) * 7.2 + 0.4, 38 + _m.sin(a) * 7.2 + 0.4, 4.9, 'glow2' if i % 4 == 0 else 'bonedark')

# ---------------------------------------------------------------------------------------------------------- materials
EMISSIVE = {'glow', 'glow2', 'clawglow', 'eye'}
def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
def paint(mat, x, y, w, h, seed):
    r = random.Random(seed * 7919 + x * 131 + y)
    if mat == 'bone':
        c = lerp((214, 204, 182), (168, 156, 134), r.random() * 0.6)
        return lerp(c, (120, 110, 96), 0.5) if r.random() < 0.06 else c          # pits and stains
    if mat == 'bonedark': return lerp((120, 108, 94), (82, 74, 66), r.random() * 0.7)
    if mat == 'bonetip': t = y / max(1, h - 1); return lerp((70, 62, 58), (36, 32, 32), t * 0.6 + r.random() * 0.3)
    if mat == 'cloth2': return lerp((34, 30, 34), (52, 46, 50), r.random() * 0.6)
    if mat == 'skin': return lerp((214, 186, 168), (196, 166, 148), r.random() * 0.5)
    if mat == 'ash': return lerp((200, 200, 206), (232, 232, 236), r.random() * 0.6) if y < h - 1 or r.random() < 0.7 else (170, 170, 178)
    if mat == 'eye': return (210, 240, 255)
    if mat == 'brow': return (150, 148, 156)
    if mat == 'lip': return (150, 104, 100)
    if mat == 'hair': return lerp((20, 18, 22), (40, 36, 40), r.random() * 0.6)
    if mat == 'stone': return lerp((24, 20, 26), (46, 40, 48), r.random() * 0.6)
    if mat == 'stonetip': t = y / max(1, h - 1); return lerp((60, 54, 60), (20, 16, 20), t * 0.7 + r.random() * 0.2)
    if mat == 'chain': return lerp((70, 70, 80), (110, 110, 124), r.random() * 0.6)
    if mat == 'clawglow': return lerp((150, 215, 255), (220, 245, 255), r.random())
    if mat == 'leather': return lerp((46, 36, 34), (66, 52, 46), r.random() * 0.6)
    if mat == 'socket': return (10, 8, 10)
    if mat == 'cloth': return lerp((88, 16, 24), (120, 26, 34), r.random() * 0.7) if (y < h - 2 or r.random() < 0.6) else (48, 8, 12)
    if mat == 'glow': return lerp((190, 236, 255), (240, 252, 255), r.random())
    if mat == 'glow2': return lerp((120, 200, 255), (170, 225, 255), r.random())
    return (255, 0, 255)

def cracks(w, h, seed):
    r = random.Random(seed)
    s = set()
    for _ in range((w * h) // 150 + (1 if w * h > 24 and r.random() < 0.5 else 0)):
        x, y = r.randrange(max(1, w)), r.randrange(max(1, h))
        for _ in range(r.randint(6, 14)):
            s.add((x, y))
            x += r.choice((-1, 0, 0, 1)); y += r.choice((1, 1, 0))
            if not (0 <= x < w and 0 <= y < h): break
    return s

# ------------------------------------------------------------------------------------------------- box UV + texture
TEX_W = 512
def layout():
    """Packs every cube's box UV (2(w+d) x (d+h) texels) into the texture, shelf by shelf."""
    x = y = shelf = 0
    out = []
    for bn in ORDER:
        for ci, (o, s, m, inf) in enumerate(BONES[bn]['cubes']):
            w, h, d = (max(1, int(math.ceil(v))) for v in s)
            uw, uh = 2 * (w + d), d + h
            if x + uw > TEX_W: x, y, shelf = 0, y + shelf, 0
            out.append((bn, ci, x, y, w, h, d))
            x += uw; shelf = max(shelf, uh)
    return out, y + shelf

FACE_ORDER = ('up', 'down', 'east', 'north', 'west', 'south')
def face_rects(u, v, w, h, d):
    return {'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d), 'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
            'west': (u + d + w, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h)}

def build_texture():
    uv, th = layout()
    TH = 1
    while TH < th: TH *= 2
    tex = Image.new('RGBA', (TEX_W, TH), (0, 0, 0, 0)); px = tex.load()
    glow = Image.new('RGBA', (TEX_W, TH), (0, 0, 0, 0)); gp = glow.load()
    UV = {}
    for n, (bn, ci, u, v, w, h, d) in enumerate(uv):
        mat = BONES[bn]['cubes'][ci][2]
        UV[(bn, ci)] = (u, v, w, h, d)
        for fi, (fname, (fx, fy, fw, fh)) in enumerate(face_rects(u, v, w, h, d).items()):
            cr = cracks(fw, fh, n * 13 + fi) if mat in ('hide', 'obsidian') else set()
            for yy in range(fh):
                for xx in range(fw):
                    c = paint(mat, xx, yy, fw, fh, n * 17 + fi)
                    if mat not in EMISSIVE:   # shade the form: darker toward the bottom of each side, darker at the edges
                        k = 1.08 - 0.38 * (yy / max(1, fh - 1)) if fname not in ('up', 'down') else (1.12 if fname == 'up' else 0.6)
                        if fw > 2 and fh > 2 and (xx == 0 or yy == 0 or xx == fw - 1 or yy == fh - 1): k *= 0.78
                        c = tuple(max(0, min(255, int(v * k))) for v in c)
                    if (xx, yy) in cr:
                        c = (255, 110, 30) if mat == 'hide' else (190, 60, 255)
                        gp[fx + xx, fy + yy] = c + (255,)
                    px[fx + xx, fy + yy] = c + (255,)
                    if mat in EMISSIVE: gp[fx + xx, fy + yy] = (tuple(int(v * 0.4) for v in c) if mat == 'maw' else c) + (255,)
    return tex, glow, UV

# ---------------------------------------------------------------------------------------------------------- export
def export_geo(UV, path, TH):
    bones = []
    for bn in ORDER:
        B = BONES[bn]
        e = {'name': bn, 'pivot': [-B['pivot'][0], B['pivot'][1], B['pivot'][2]]}
        if B['parent']: e['parent'] = B['parent']
        if any(B['rot']): e['rotation'] = [-B['rot'][0], -B['rot'][1], B['rot'][2]]   # GeckoLib reads x and y negated
        cs = []
        for ci, (o, s, m, inf) in enumerate(B['cubes']):
            u, v, w, h, d = UV[(bn, ci)]
            c = {'origin': [-(o[0] + s[0]), o[1], o[2]], 'size': s, 'uv': [u, v]}
            cs.append(c)
        if cs: e['cubes'] = cs
        bones.append(e)
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{'description': {
        'identifier': 'geometry.' + NAME, 'texture_width': TEX_W, 'texture_height': TH,
        'visible_bounds_width': 16, 'visible_bounds_height': 12, 'visible_bounds_offset': [0, 5, 0]}, 'bones': bones}]}
    json.dump(geo, open(path, 'w'))

# ---------------------------------------------------------------------------------------------------------- renderer
def rot_matrix(rx, ry, rz):
    rx, ry, rz = (math.radians(a) for a in (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    X = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Y = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Z = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mul(Z, mul(Y, X))
def mul(A, B): return [[sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
def app(M, v): return [sum(M[i][k] * v[k] for k in range(3)) for i in range(3)]

def world_transforms(pose):
    """Each bone's (matrix, offset): world = M * local + t. pose: bone -> {'rot', 'pos', 'scale'} on top of the rest pose
    (GeckoLib's order: moved by pos, then turned and scaled about its pivot)."""
    T = {}
    for bn in ORDER:
        B = BONES[bn]
        q = pose.get(bn, {})
        if isinstance(q, tuple): q = {'rot': q}
        rr, pp, ss = q.get('rot', (0, 0, 0)), q.get('pos', (0, 0, 0)), q.get('scale', (1, 1, 1))
        R = rot_matrix(*[B['rot'][i] + rr[i] for i in range(3)])
        R = [[R[i][j] * ss[j] for j in range(3)] for i in range(3)]
        p = B['pivot']
        off = [pp[i] + p[i] - app(R, p)[i] for i in range(3)]
        if B['parent']:
            PM, Pt = T[B['parent']]
            M = mul(PM, R); t = [app(PM, off)[i] + Pt[i] for i in range(3)]
        else:
            M, t = R, off
        T[bn] = (M, t)
    return T

def render(tex, glow, UV, yaw=25, pitch=8, size=(900, 900), scale=4.2, pose=None, bg=True, cy=0.55, ground_y=None, effects=()):
    pose = pose or {}
    T = world_transforms(pose)
    W, H = size
    tp, gpx = tex.load(), glow.load()
    cyw, syw = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    def cam(v):
        x, y, z = v
        x, z = x * cyw - z * syw, x * syw + z * cyw
        y, z = y * cp - z * sp, y * sp + z * cp
        return x, y, z
    light = (-0.45, 0.75, -0.5); ln = math.sqrt(sum(a * a for a in light)); light = tuple(a / ln for a in light)
    faces = []
    for bn in ORDER:
        M, t = T[bn]
        for ci, (o, s, mat, inf) in enumerate(BONES[bn]['cubes']):
            u, v, w, h, d = UV[(bn, ci)]
            x0, y0, z0 = o; x1, y1, z1 = o[0] + s[0], o[1] + s[1], o[2] + s[2]
            # each face: corner at texel (0,0), the u axis and v axis in model space, the texel counts, its UV rect
            rects = face_rects(u, v, w, h, d)
            F = {'north': ((x1, y1, z0), (-s[0], 0, 0), (0, -s[1], 0), rects['north'], (0, 0, -1)),
                 'south': ((x0, y1, z1), (s[0], 0, 0), (0, -s[1], 0), rects['south'], (0, 0, 1)),
                 'east': ((x1, y1, z1), (0, 0, -s[2]), (0, -s[1], 0), rects['east'], (1, 0, 0)),
                 'west': ((x0, y1, z0), (0, 0, s[2]), (0, -s[1], 0), rects['west'], (-1, 0, 0)),
                 'up': ((x0, y1, z0), (s[0], 0, 0), (0, 0, s[2]), rects['up'], (0, 1, 0)),
                 'down': ((x0, y0, z1), (s[0], 0, 0), (0, 0, -s[2]), rects['down'], (0, -1, 0))}
            for fname, (c0, ua, va, (fx, fy, fw, fh), nrm) in F.items():
                wc = [app(M, c0)[i] + t[i] for i in range(3)]
                wu, wv = app(M, ua), app(M, va)
                n = cam(app(M, nrm))
                if n[2] > 0.05: continue   # facing away
                cc = cam([wc[i] + wu[i] / 2 + wv[i] / 2 for i in range(3)])
                faces.append((cc[2], wc, wu, wv, fx, fy, fw, fh, n, mat))
    faces.sort(key=lambda f: -f[0])
    img = Image.new('RGB', (W, H), (0, 0, 0))
    if bg:
        d = ImageDraw.Draw(img)
        for yy in range(H):
            t = yy / H
            d.line([(0, yy), (W, yy)], fill=lerp((40, 6, 6), (150, 40, 14), t ** 1.6))
    gl = Image.new('RGB', (W, H), (0, 0, 0))
    d, dg = ImageDraw.Draw(img), ImageDraw.Draw(gl)
    def P(v):
        x, y, z = cam(v)
        return (W / 2 + x * scale, H * cy - (y - 45) * scale)
    if ground_y is not None:   # scorched ground to a flat horizon, with a soft shadow under it
        hy = P((0, ground_y, 60))[1]
        for yy in range(int(hy), H):
            t = min(1, (yy - hy) / max(1, H - hy))
            d.line([(0, yy), (W, yy)], fill=lerp((70, 24, 14), (30, 12, 10), t))
        d.polygon([P((x * 30, ground_y, z * 22 + 4)) for x, z in ((math.cos(a), math.sin(a)) for a in [i * math.pi / 16 for i in range(32)])], fill=(22, 8, 6))
    for depth, wc, wu, wv, fx, fy, fw, fh, n, mat in faces:
        sh = 0.35 + 0.65 * max(0.0, -(n[0] * light[0] + n[1] * light[1] + n[2] * light[2]) * -1 if False else (n[0] * light[0] + n[1] * light[1] - n[2] * light[2]))
        sh = max(0.3, min(1.0, sh))
        for j in range(fh):
            for i in range(fw):
                a = [wc[k] + wu[k] * i / fw + wv[k] * j / fh for k in range(3)]
                b = [a[k] + wu[k] / fw for k in range(3)]
                c = [b[k] + wv[k] / fh for k in range(3)]
                e = [a[k] + wv[k] / fh for k in range(3)]
                col = tp[fx + i, fy + j]
                g = gpx[fx + i, fy + j]
                if col[3] == 0: continue
                poly = [P(a), P(b), P(c), P(e)]
                if g[3] > 0:
                    d.polygon(poly, fill=col[:3]); dg.polygon(poly, fill=g[:3])
                else:
                    d.polygon(poly, fill=tuple(int(cv * sh) for cv in col[:3]))
    for f in effects:   # effects drawn over the model: shockwave rings on the ground, rising embers
        if f[0] == 'ring':
            _, c, r, col, wd = f
            pts = [P((c[0] + math.cos(a) * r, c[1], c[2] + math.sin(a) * r)) for a in [i * math.pi / 24 for i in range(49)]]
            d.line(pts, fill=col, width=wd); dg.line(pts, fill=col, width=wd)
        elif f[0] == 'dot':
            _, c, rad, col = f
            q = P(c); d.ellipse([q[0] - rad, q[1] - rad, q[0] + rad, q[1] + rad], fill=col); dg.ellipse([q[0] - rad, q[1] - rad, q[0] + rad, q[1] + rad], fill=col)
    # bloom
    from PIL import ImageChops
    blur = gl.filter(ImageFilter.GaussianBlur(10))
    blur2 = gl.filter(ImageFilter.GaussianBlur(28))
    img = ImageChops.add(img, blur); img = ImageChops.add(img, blur2.point(lambda v: int(v * 0.6)))
    return img

def ground(img, feet_y):
    """A low bank of flame along the ground line."""
    from PIL import ImageChops
    W, H = img.size
    fl = Image.new('RGB', (W, H), (0, 0, 0)); d = ImageDraw.Draw(fl)
    r = random.Random(5)
    for i in range(160):
        x = r.uniform(0, W); h = r.uniform(10, 46)
        d.ellipse([x - 14, feet_y - h, x + 14, feet_y + 8], fill=lerp((120, 30, 6), (255, 150, 40), r.random()))
    d.rectangle([0, feet_y, W, H], fill=(30, 8, 6))
    fl = fl.filter(ImageFilter.GaussianBlur(5))
    img.paste(ImageChops.lighter(img, fl))

def scene(img, feet_y, x, px_per_block):
    """A 2-block-tall mage beside its foot, for scale, and a low bank of fire along the ground."""
    d = ImageDraw.Draw(img)
    h = 2 * px_per_block; w = h * 0.3
    d.rectangle([x - w / 2, feet_y - h, x + w / 2, feet_y], fill=(20, 12, 14))
    d.rectangle([x - w * 0.45, feet_y - h * 1.0, x + w * 0.45, feet_y - h * 0.75], fill=(20, 12, 14))
    return img

# ---------------------------------------------------------------------------------------------------------- animations
# Each animation: its length, whether it loops, and per bone the keyframes of rotation (degrees), position (pixels)
# and scale, on top of the rest pose. Right-side bones are written once and mirrored to the left ('_r' -> '_l').
ANIMS = {}
# parts that stand up from their pivot (or, like the jaw, hang forward of it) turn the other way about x
FLIP_X = {'body', 'head', 'jaw', 'hips'}
def anim(name, length, loop):
    A = dict(length=length, loop=loop, bones={}); ANIMS[name] = A; return A
def key(A, bone, chan, keys, mirror=True, shift=0.0):
    """keys: [(time, (x, y, z)), ...]. With mirror, also the '_l' twin (rotation y/z and position x flipped); shift
    offsets the twin's timing (a walk's other leg), wrapping round a looping animation."""
    if chan == 'rot' and bone in FLIP_X:   # authored as "+ leans forward / looks down / opens the jaw"
        keys = [(t, (-v[0], v[1], v[2])) for t, v in keys]
    A['bones'].setdefault(bone, {})[chan] = keys
    if mirror and bone.endswith('_r'):
        f = (lambda v: (v[0], -v[1], -v[2])) if chan == 'rot' else (lambda v: (-v[0], v[1], v[2])) if chan == 'pos' else (lambda v: v)
        tw = [((t + shift) % A['length'] if shift else t, f(v)) for t, v in keys]
        if shift:
            tw.sort()
            if tw[0][0] > 0: tw.insert(0, (0.0, sample(keys, (0 - shift) % A['length'], A['length'], True, f)))
            tw.append((A['length'], tw[0][1]))
        A['bones'].setdefault(bone[:-2] + '_l', {})[chan] = tw

def sample(keys, t, length, loop, f=None):
    """Catmull-Rom through the keyframes (GeckoLib's catmullrom lerp mode)."""
    if f: keys = [(k, f(v)) for k, v in keys]
    if t <= keys[0][0]: return keys[0][1]
    if t >= keys[-1][0]: return keys[-1][1]
    for i in range(len(keys) - 1):
        t0, v0 = keys[i]; t1, v1 = keys[i + 1]
        if t0 <= t <= t1:
            u = (t - t0) / (t1 - t0) if t1 > t0 else 0
            vp = keys[i - 1][1] if i > 0 else v0
            vn = keys[i + 2][1] if i + 2 < len(keys) else v1
            return tuple(0.5 * ((2 * v0[j]) + (-vp[j] + v1[j]) * u + (2 * vp[j] - 5 * v0[j] + 4 * v1[j] - vn[j]) * u * u
                                + (-vp[j] + 3 * v0[j] - 3 * v1[j] + vn[j]) * u * u * u) for j in range(3))
    return keys[-1][1]

def pose_at(A, t):
    P = {}
    for bn, chans in A['bones'].items():
        P[bn] = {c: sample(k, t, A['length'], A['loop']) for c, k in chans.items()}
    return P

Z = (0, 0, 0)
def wave(L, amp, phase=0.0, n=8, axis=0):
    """A looping sine as keyframes."""
    out = []
    for i in range(n + 1):
        t = L * i / n
        v = [0, 0, 0]; v[axis] = amp * math.sin(2 * math.pi * (i / n + phase))
        out.append((round(t, 4), tuple(v)))
    return out
#ANIMS

def export_anims(path):
    out = {}
    for name, A in ANIMS.items():
        bones = {}
        for bn, chans in A['bones'].items():
            e = {}
            for c, ks in chans.items():
                f = (lambda v: [-v[0], -v[1], v[2]]) if c == 'rot' else (lambda v: [-v[0], v[1], v[2]]) if c == 'pos' else (lambda v: list(v))
                e[{'rot': 'rotation', 'pos': 'position', 'scale': 'scale'}[c]] = {'%.4f' % t: {'post': [round(x, 3) for x in f(v)], 'lerp_mode': 'catmullrom'} for t, v in ks}
            bones[bn] = e
        out['animation.%s.%s' % (NAME, name)] = {'loop': True if A['loop'] else 'hold_on_last_frame', 'animation_length': A['length'], 'bones': bones}
    json.dump({'format_version': '1.8.0', 'animations': out}, open(path, 'w'))

def fx_at(A, t):
    out = []
    for f in A.get('fx', ()):
        if f[0] == 'shock' and f[1] <= t <= f[1] + f[2]:
            k = (t - f[1]) / f[2]
            c = f[3]
            out.append(('ring', c, 6 + k * 70, (255, int(200 - 120 * k), 60), max(1, int(6 * (1 - k)))))
            out.append(('ring', c, 3 + k * 42, (255, 150, 40), max(1, int(4 * (1 - k)))))
        if f[0] == 'embers' and f[1] <= t:
            r = random.Random(7)
            for i in range(90):
                born = f[1] + r.random() * (f[2] - f[1] - 0.5)
                if t < born: continue
                age = t - born
                x0, y0, z0 = r.uniform(-60, 60), r.uniform(20, 90), r.uniform(-6, 16)
                out.append(('dot', (x0 + math.sin(age * 2 + i) * 3, y0 + age * 14, z0), max(1.0, 3.2 - age * 1.2),
                            (255, int(140 + 80 * r.random()), 40) if age < 1.2 else (120, 110, 104)))
    return out

def gif(tex, glow, UV, name, fps=12, size=(680, 560), scale=7.5, yaw=28, pitch=8):
    A = ANIMS[name]
    n = int(A['length'] * fps)
    frames = []
    for i in range(n + (0 if A['loop'] else 8)):
        t = min(A['length'], i / fps)
        shake = 0
        for f in A.get('fx', ()):
            if f[0] == 'shock' and f[1] <= t <= f[1] + 0.35: shake = 6 * (1 - (t - f[1]) / 0.35) * (1 if i % 2 else -1)
        im = render(tex, glow, UV, yaw=yaw, pitch=pitch, size=size, scale=scale, pose=pose_at(A, t), cy=0.5 + (48 - 45) * scale / size[1] + shake / 560, ground_y=-0.5, effects=fx_at(A, t))
        ImageDraw.Draw(im).text((12, 10), name, fill=(255, 220, 160))
        frames.append(im.convert('P', palette=Image.ADAPTIVE, colors=200))
    path = os.path.join(OUT, 'beast_%s.gif' % name)
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=int(1000 / fps), loop=0, disposal=2)
    return path



if __name__ == '__main__':
    mode = sys.argv[1] if len(sys.argv) > 1 else 'preview'
    tex, glow, UV = build_texture()
    if mode == 'preview':
        S, HH = 15, 1000
        cy = 0.5 + (26 - 45) * S / HH
        hero = render(tex, glow, UV, yaw=28, pitch=6, size=(1200, HH), scale=S, cy=cy)
        feet = HH * cy + 45 * S
        ground(hero, feet)
        scene(hero, feet, 600 + 18 * S, S * 16 / 2.4)
        ImageDraw.Draw(hero).text((24, 24), "The Ossuarch, third phase: the Grave-Bound  -  concept  (the dark figure is a player)", fill=(255, 220, 160))
        small = []
        for y, p, lab, sc, cc in [(0, 4, 'Front', 12, 26), (20, 4, 'Head', 60, 44), (90, 4, 'Side', 12, 26), (180, 6, 'Back', 12, 26)]:
            im = render(tex, glow, UV, yaw=y, pitch=p, size=(800, 800), scale=sc, cy=0.5 + (cc - 45) * sc / 800)
            ImageDraw.Draw(im).text((20, 20), lab, fill=(255, 220, 160))
            small.append(im.resize((300, 300)))
        sheet = Image.new('RGB', (1200, 1300), (0, 0, 0))
        sheet.paste(hero, (0, 0))
        for i, im in enumerate(small): sheet.paste(im, (i * 300, 1000))
        sheet.save(os.path.join(OUT, 'ossuarch_preview.png'))
        print('cubes', sum(len(B['cubes']) for B in BONES.values()), 'bones', len(ORDER))
    if mode == 'gif':
        for nm in (sys.argv[2:] or list(ANIMS)): print(gif(tex, glow, UV, nm))
    if mode == 'sheet':   # a few frames of each move, side by side
        nm = sys.argv[2]; A = ANIMS[nm]; ts = [float(x) for x in sys.argv[3:]] or [A['length'] * i / 5 for i in range(6)]
        ims = [render(tex, glow, UV, yaw=float(os.environ.get('YAW', 50)), pitch=6, size=(400, 400), scale=3, pose=pose_at(A, t), cy=0.5 + (48 - 45) * 3 / 400, ground_y=-0.5) for t in ts]
        sh = Image.new('RGB', (400 * len(ims), 400))
        for i, (im, t) in enumerate(zip(ims, ts)): ImageDraw.Draw(im).text((8, 8), '%s %.2f' % (nm, t), fill=(255, 220, 160)); sh.paste(im, (400 * i, 0))
        sh.save(os.path.join(OUT, 'ossuarch_sheet_%s.png' % nm))
    if mode == 'export':
        base = paint
        def red(mat, x, y, w, h, seed):
            c = base(mat, x, y, w, h, seed)
            if mat in ('eye', 'clawglow'): return (255, int(c[1] * 0.35), int(c[2] * 0.25))
            if mat == 'mane' and c[0] > 80: return (150, 30, 24)
            return c
        paint = red
        t3, g3, _ = build_texture()
        paint = base
        t3.save(os.path.join(R, 'textures/entity', NAME + '_despair.png'))
        g3.save(os.path.join(R, 'textures/entity', NAME + '_despair_glowmask.png'))
        uv, th = layout(); TH = 1
        while TH < th: TH *= 2
        export_geo(UV, os.path.join(R, 'geo', NAME + '.geo.json'), TH)
        tex.save(os.path.join(R, 'textures/entity', NAME + '_geo.png'))
        glow.save(os.path.join(R, 'textures/entity', NAME + '_geo_glowmask.png'))
        export_anims(os.path.join(R, 'animations', NAME + '.animation.json'))
