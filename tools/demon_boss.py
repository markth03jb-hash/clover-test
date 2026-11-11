"""The Cinder Sovereign: an original winged demon lord (world boss). Builds its GeckoLib model (geo.json) and a
box-UV texture from materials (charred hide with molten cracks, ash horn, ember-lit wing membranes, iron chains,
burning braziers on the thorns of its back), and renders 3D previews - stills and animated GIFs - with a small
software renderer (orthographic, flat-shaded, textured per texel, emissive bloom).

  python3 tools/demon_boss.py preview            stills: front, three-quarter, back
  python3 tools/demon_boss.py gif <anim>          an animation, as a GIF (once animations exist)
  python3 tools/demon_boss.py export              writes geo/texture into the mod's assets
"""
import json, math, os, random, sys
from PIL import Image, ImageDraw, ImageFilter

OUT = '/tmp/claude-0/-home-claude/267cab68-cab5-5d3b-ab2c-ac307f4fb5bc/scratchpad'
R = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/iron_spell_grimoire')
NAME = 'cinder_sovereign'
rnd = random.Random(1337)

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

bone('root', None, (0, 0, 0))
bone('hips', 'root', (0, 34, 0))
box('hips', -11, 30, -7, 11, 40, 7, 'hide')
box('hips', -12, 31, -8, 12, 33, 8, 'iron')                      # a belt of black iron
for x in (-8, -3, 2, 7):                                          # tattered cinder-cloth hanging from it
    box('hips', x, 18 + rnd.randint(0, 6), -9, x + 4, 31, -8, 'cloth')
    box('hips', x, 16 + rnd.randint(0, 6), 8, x + 4, 31, 9, 'cloth')

bone('body', 'hips', (0, 40, 0))                                  # the torso never bends (bosses keep it still)
box('body', -11, 40, -7, 11, 48, 7, 'hide')
box('body', -15, 48, -9, 15, 56, 9, 'hide')
box('body', -18, 56, -10, 18, 66, 10, 'hide')
box('body', -6, 50, -11, 6, 60, -10, 'core')                      # the furnace in its chest
box('body', -8, 48, -11, -6, 62, -10, 'rib'); box('body', 6, 48, -11, 8, 62, -10, 'rib')
box('body', -13, 54, -11, -9, 56, -10, 'rib'); box('body', 9, 54, -11, 13, 56, -10, 'rib')
box('body', -13, 50, -11, -9, 52, -10, 'rib'); box('body', 9, 50, -11, 13, 52, -10, 'rib')
def shoulders(s, sx):
    box('body', 15, 59, -8, 23, 66, 8, 'horn', sx)               # bone pauldrons, spiked
    box('body', 18, 66, -3, 21, 71, 0, 'horntip', sx)
    box('body', 20, 65, 2, 23, 69, 5, 'horntip', sx)
sym(shoulders)
box('body', -6, 64, -7, 6, 71, 4, 'hide')                         # neck

bone('head', 'body', (0, 71, -3))
box('head', -9, 70, -12, 9, 86, 5, 'hide')
box('head', -11, 71, -10, -9, 79, -4, 'horntip'); box('head', 9, 71, -10, 11, 79, -4, 'horntip')   # jaw spurs
box('head', -12, 69, -9, -10, 73, -6, 'horntip'); box('head', 10, 69, -9, 12, 73, -6, 'horntip')
box('head', -7, 76, -12.3, 7, 82, -12, 'socket')                  # deep-set eye sockets
box('head', -7, 68, -19, 7, 75, -12, 'hide')                      # the long jaw
box('head', -6, 70, -19.5, 6, 71.5, -19, 'maw')                   # its glowing maw
box('head', -7, 73, -12.5, 7, 74, -12, 'maw')
box('head', -7, 78, -12.6, -2, 80.5, -12, 'eye'); box('head', 2, 78, -12.6, 7, 80.5, -12, 'eye')
box('head', -9, 80.5, -13, 9, 83, -11, 'horn')                     # a heavy brow
box('head', -1, 81, -14, 1, 89, -12, 'horntip')                   # a blade of bone up its brow
for i, x in enumerate((-6, -2, 2, 5)):                            # teeth
    box('head', x, 67, -19, x + 1.5, 70, -18, 'horn')
def horns(s, sx):
    # great ram horns: a curve of shrinking blocks sweeping out and up from the skull, back, then curling round forward
    pts = []
    for k in range(40):
        t = k / 39
        ang = t * math.pi * 1.55
        r = 15 * (1 - 0.45 * t)
        x = 9 + 2 + 14 * t + r * 0.35 * (1 - math.cos(ang * 0.5))
        y = 82 + r * math.sin(ang) * 0.9 + 4
        z = -2 + r * (1 - math.cos(ang)) * 0.75
        w = 8 * (1 - 0.7 * t)
        box('head', x - w / 2, y - w / 2, z - w / 2, x + w / 2, y + w / 2, z + w / 2, 'horn' if t < 0.8 else 'horntip', sx)
sym(horns)

def arms(s, sx):
    bone('arm' + s, 'body', (21 * sx, 62, 0), (-15, 0, 58 * sx))
    box('arm' + s, 17, 40, -6, 26, 62, 6, 'hide', sx)
    box('arm' + s, 17, 52, -7, 26, 54, 7, 'iron', sx)            # an iron cuff
    bone('forearm' + s, 'arm' + s, (21.5 * sx, 41, 0), (30, 0, -38 * sx))
    box('forearm' + s, 17.5, 18, -5, 25.5, 42, 5, 'hide', sx)
    box('forearm' + s, 25, 24, -2, 28, 36, 2, 'horn', sx)        # bone spurs on the elbow line
    box('forearm' + s, 26, 32, -1, 30, 34, 1, 'horn', sx)
    bone('hand' + s, 'forearm' + s, (21.5 * sx, 19, 0), (20, 0, 8 * sx))
    box('hand' + s, 17, 10, -6, 26, 19, 6, 'hide', sx)
    for i, z in enumerate((-5, -1.5, 2, 5.5)):                    # four long claws
        bone('claw%d%s' % (i, s), 'hand' + s, (21.5 * sx, 10, z), (18 + i * 4, 0, (-8 + i * 5) * sx))
        box('claw%d%s' % (i, s), 20.5 + (i - 1.5) * 1.5, -4, z - 1, 23 + (i - 1.5) * 1.5, 10, z + 1, 'horn', sx)
        box('claw%d%s' % (i, s), 21 + (i - 1.5) * 1.5, -8, z - 0.5, 22.5 + (i - 1.5) * 1.5, -4, z + 0.5, 'horntip', sx)
sym(arms)

def legs(s, sx):
    bone('thigh' + s, 'hips', (7 * sx, 32, 0), (22, 0, 6 * sx))
    box('thigh' + s, 2, 16, -6, 12, 34, 6, 'hide', sx)
    bone('shin' + s, 'thigh' + s, (7 * sx, 17, -2), (-48, 0, -4 * sx))
    box('shin' + s, 3, 2, -5, 11, 18, 3, 'hide', sx)
    box('shin' + s, 5, 8, 3, 9, 16, 6, 'horn', sx)               # a spur behind the knee
    bone('foot' + s, 'shin' + s, (7 * sx, 3, -1), (26, 0, 0))
    box('foot' + s, 2, -3, -8, 12, 4, 4, 'hide', sx)
    for x in (2.5, 6, 9.5):
        box('foot' + s, x, -3, -12, x + 2.5, 0, -8, 'horntip', sx)
sym(legs)

# the tail: heavy segments sweeping down behind, ending in a blade
parent, z = 'hips', 7
for k in range(6):
    name = 'tail%d' % k
    bone(name, parent, (0, 33 - k * 2, z), (38 if k == 0 else -7, 0, 0))
    w = 7 - k
    box(name, -w / 2, 33 - k * 2 - w / 2, z, w / 2, 33 - k * 2 + w / 2, z + 9, 'hide')
    parent, z = name, z + 9
box('tail5', -1, 18, 54, 1, 30, 62, 'horntip')

# the Pyre Spine: five black thorns fanned behind the shoulders, each crowned with a brazier of fire, iron chains
# slung between them
THORNS = [(-16, 32, -46), (-8, 38, -22), (0, 44, 0), (8, 38, 22), (16, 32, 46)]   # x, height, tilt (deg)
for i, (x, h, tilt) in enumerate(THORNS):
    name = 'thorn%d' % i
    bone(name, 'body', (x, 60, 9), (-22, 0, -tilt))
    segs = [(6, h * 0.4, 'obsidian'), (4.4, h * 0.35, 'obsidian'), (3, h * 0.25, 'obsidian')]
    y = 60
    for w, L, m in segs:
        box(name, x - w / 2, y, 9 - w / 2 + 1, x + w / 2, y + L, 9 + w / 2 + 1, m)
        y += L
    box(name, x - 4, y, 6, x + 4, y + 2, 14, 'iron')              # the brazier's iron bowl
    box(name, x - 3.5, y - 3, 6.5, x + 3.5, y, 13.5, 'iron')
    bone('flame%d' % i, name, (x, y + 2, 10))
    box('flame%d' % i, x - 3, y + 2, 7, x + 3, y + 7, 13, 'fire')
    box('flame%d' % i, x - 2, y + 7, 8, x + 2, y + 11, 12, 'fire2')
    box('flame%d' % i, x - 1, y + 11, 9, x + 1, y + 14, 11, 'fire3')
    THORNS[i] = (x, h, tilt, y)

def wings(s, sx):
    bone('wing' + s, 'body', (8 * sx, 60, 9), (0, -22 * sx, 58 * sx))
    box('wing' + s, 8, 57, 7, 42, 63, 12, 'hide', sx)              # the wing's arm
    bone('wingfore' + s, 'wing' + s, (41 * sx, 60, 9.5), (0, 8 * sx, -38 * sx))
    box('wingfore' + s, 41, 58, 8, 80, 62, 11, 'hide', sx)
    box('wingfore' + s, 78, 59, 7, 86, 64, 12, 'horn', sx)        # the thumb claw
    # fingers fanning down from the wrist, with membrane hung between them, tattered at the edge
    fingers = [(-10, 58), (-35, 62), (-60, 58), (-85, 50), (-110, 42)]
    for i, (ang, L) in enumerate(fingers):
        fb = 'finger%d%s' % (i, s)
        bone(fb, 'wingfore' + s, (80 * sx, 60, 9.5), (0, 0, ang * sx))
        box(fb, 80, 59, 8.5, 80 + L, 61, 10.5, 'horn', sx)
        # the membrane panel toward the next finger: a fan of strips, shortening into a ragged edge
        if i < len(fingers) - 1:
            gap = fingers[i + 1][0] - ang
            for k in range(1, 8):
                mb = 'mem%d_%d%s' % (i, k, s)
                bone(mb, fb, (80 * sx, 60, 9.5), (0, 0, gap * k / 8 * sx))
                Ln = L + (fingers[i + 1][1] - L) * k / 8
                cut = Ln * (0.84 - 0.2 * rnd.random()) if k % 2 else Ln * 0.95
                box(mb, 80, 55, 9.3, 80 + cut, 65, 9.8, 'membrane', sx)
    # membrane from the wing's arm down to the body
    for k in range(1, 7):
        mb = 'memarm%d%s' % (k, s)
        bone(mb, 'wing' + s, (8 * sx, 60, 9), (0, 0, (-k * 9) * sx))
        box(mb, 8, 55, 9.3, 8 + 36 - k * 1.5, 65, 9.8, 'membrane', sx)
sym(wings)

# chains slung between the thorns' braziers (drawn in the body's frame - they sag between the tilted tips)
def thorn_tip(i):
    x, h, tilt, y = THORNS[i]
    # the tip's place after the thorn's tilt (about the thorn's pivot (x, 60, 9))
    a = math.radians(-tilt); b = math.radians(-22)
    dx, dy, dz = 0, y - 60, 1
    dx, dy = dx * math.cos(a) - dy * math.sin(a), dx * math.sin(a) + dy * math.cos(a)
    dy, dz = dy * math.cos(b) - dz * math.sin(b), dy * math.sin(b) + dz * math.cos(b)
    return x + dx, 60 + dy - 2, 9 + dz
for i in range(len(THORNS) - 1):
    a, b = thorn_tip(i), thorn_tip(i + 1)
    n = 9
    for k in range(n + 1):
        t = k / n
        x = a[0] + (b[0] - a[0]) * t; y = a[1] + (b[1] - a[1]) * t - math.sin(t * math.pi) * 9; z = a[2] + (b[2] - a[2]) * t
        box('body', x - 0.8, y - 0.8, z - 0.8, x + 0.8, y + 0.8, z + 0.8, 'chain' if k % 2 else 'iron')

# ---------------------------------------------------------------------------------------------------------- materials
EMISSIVE = {'core', 'eye', 'maw', 'fire', 'fire2', 'fire3'}
def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
def paint(mat, x, y, w, h, seed):
    """The colour of texel (x, y) on a face of size w x h of this material."""
    r = random.Random(seed * 7919 + x * 131 + y)
    if mat == 'hide':
        base = lerp((22, 15, 17), (44, 24, 22), r.random() * 0.6)
        return base
    if mat == 'socket': return (8, 4, 6)
    if mat == 'obsidian': return lerp((14, 10, 20), (36, 22, 44), r.random() * 0.5)
    if mat == 'rib': return lerp((90, 70, 62), (130, 104, 90), r.random())
    if mat == 'horn': t = y / max(1, h - 1); return lerp((150, 138, 124), (82, 70, 64), t * 0.7 + r.random() * 0.2)
    if mat == 'horntip': return lerp((40, 30, 30), (70, 56, 50), r.random())
    if mat == 'iron': return lerp((38, 36, 42), (70, 66, 74), r.random() * 0.7)
    if mat == 'chain': return lerp((60, 56, 60), (110, 70, 50), r.random() * 0.5)
    if mat == 'cloth': return lerp((52, 8, 12), (88, 16, 18), r.random() * 0.7) if (y < h - 2 or r.random() < 0.6) else (30, 6, 8)
    if mat == 'membrane':
        c = lerp((34, 6, 8), (58, 12, 12), r.random() * 0.6)
        if x % 7 == 0: c = (92, 20, 18)                            # veins
        return c
    if mat == 'core': d = math.hypot(x - w / 2, y - h / 2) / max(w, h); return lerp((255, 240, 170), (255, 90, 20), min(1, d * 2.2))
    if mat == 'eye': return (255, 210, 90) if r.random() < 0.85 else (255, 255, 220)
    if mat == 'maw': return lerp((255, 120, 30), (255, 200, 80), r.random())
    if mat == 'fire': return lerp((255, 80, 10), (255, 150, 30), r.random())
    if mat == 'fire2': return lerp((255, 150, 30), (255, 210, 70), r.random())
    if mat == 'fire3': return lerp((255, 220, 100), (255, 250, 200), r.random())
    return (255, 0, 255)

# molten cracks: on the hide, a few random-walk lines of glowing orange per face (the cracked-ember look)
def cracks(w, h, seed):
    r = random.Random(seed)
    s = set()
    for _ in range((w * h) // 110 + (1 if w * h > 24 and r.random() < 0.5 else 0)):
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
                    if (xx, yy) in cr:
                        c = (255, 110, 30) if mat == 'hide' else (190, 60, 255)
                        gp[fx + xx, fy + yy] = c + (255,)
                    px[fx + xx, fy + yy] = c + (255,)
                    if mat in EMISSIVE: gp[fx + xx, fy + yy] = c + (255,)
    return tex, glow, UV

# ---------------------------------------------------------------------------------------------------------- export
def export_geo(UV, path, TH):
    bones = []
    for bn in ORDER:
        B = BONES[bn]
        e = {'name': bn, 'pivot': [-B['pivot'][0], B['pivot'][1], B['pivot'][2]]}
        if B['parent']: e['parent'] = B['parent']
        if any(B['rot']): e['rotation'] = [B['rot'][0], -B['rot'][1], -B['rot'][2]]
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
    """Each bone's (matrix, offset): world = M * local + t. pose: bone -> (rx, ry, rz) added to the rest rotation."""
    T = {}
    for bn in ORDER:
        B = BONES[bn]
        r = [B['rot'][i] + pose.get(bn, (0, 0, 0))[i] for i in range(3)]
        R = rot_matrix(*r)
        p = B['pivot']
        off = [p[i] - app(R, p)[i] for i in range(3)]   # rotate about the pivot
        if B['parent']:
            PM, Pt = T[B['parent']]
            M = mul(PM, R); t = [app(PM, off)[i] + Pt[i] for i in range(3)]
        else:
            M, t = R, off
        T[bn] = (M, t)
    return T

def render(tex, glow, UV, yaw=25, pitch=8, size=(900, 900), scale=4.2, pose=None, bg=True, cy=0.55):
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
                    d.polygon(poly, fill=g[:3]); dg.polygon(poly, fill=g[:3])
                else:
                    d.polygon(poly, fill=tuple(int(cv * sh) for cv in col[:3]))
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

if __name__ == '__main__':
    mode = sys.argv[1] if len(sys.argv) > 1 else 'preview'
    if mode == 'dbg':
        for bn in ORDER:
            if bn.startswith(('wing', 'finger', 'mem')): BONES[bn]['cubes'] = []
    tex, glow, UV = build_texture()
    if mode == 'dbg':
        a = render(tex, glow, UV, yaw=0, pitch=6, size=(700, 860), scale=3.6, cy=0.62)
        b = render(tex, glow, UV, yaw=90, pitch=6, size=(700, 860), scale=3.6, cy=0.62)
        s = Image.new('RGB', (1400, 860)); s.paste(a, (0, 0)); s.paste(b, (700, 0)); s.save(os.path.join(OUT, 'dbg.png'))
    if mode == 'preview':
        S = 4.6
        hero = render(tex, glow, UV, yaw=18, pitch=-6, size=(1500, 1100), scale=S, cy=0.6)
        feet = 1100 * 0.6 + 48 * S
        ground(hero, feet)
        scene(hero, feet, 750 + 34 * S, S * 16 / 2.6)        # a mage, 2 blocks tall, at its foot (in game it's drawn 2.6x)
        ImageDraw.Draw(hero).text((24, 24), "The Cinder Sovereign  -  original world boss concept  (about 15 blocks tall)", fill=(255, 220, 160))
        small = []
        for y, p, lab in [(0, 4, 'Front'), (90, 4, 'Side'), (165, 10, 'Back')]:
            im = render(tex, glow, UV, yaw=y, pitch=p, size=(900, 900), scale=2.9, cy=0.62)
            ImageDraw.Draw(im).text((20, 20), lab, fill=(255, 220, 160))
            small.append(im.resize((500, 500)))
        sheet = Image.new('RGB', (1500, 1600), (0, 0, 0))
        sheet.paste(hero, (0, 0))
        for i, im in enumerate(small): sheet.paste(im, (i * 500, 1100))
        sheet.save(os.path.join(OUT, 'demon_preview.png'))
        tex.save(os.path.join(OUT, 'demon_tex.png'))
        print('cubes', sum(len(B['cubes']) for B in BONES.values()), 'bones', len(ORDER))
    elif mode == 'export':
        uv, th = layout(); TH = 1
        while TH < th: TH *= 2
        os.makedirs(os.path.join(R, 'geo'), exist_ok=True); os.makedirs(os.path.join(R, 'textures/entity'), exist_ok=True)
        export_geo(UV, os.path.join(R, 'geo', NAME + '.geo.json'), TH)
        tex.save(os.path.join(R, 'textures/entity', NAME + '.png'))
        glow.save(os.path.join(R, 'textures/entity', NAME + '_glowmask.png'))
