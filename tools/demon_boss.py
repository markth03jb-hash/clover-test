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
# a hunched, heavy torso: a narrow waist under plated abs, a broad chest with a furnace caged in ribs, a hump of
# muscle behind the neck, and a ridge of spikes down the spine
box('body', -11, 40, -7, 11, 51, 7, 'hide')
for row, y in enumerate((41, 44.5, 48)):
    for x0, x1 in ((-8, -0.6), (0.6, 8)):
        box('body', x0 + row * 0.6, y, -8, x1 - row * 0.6 if x0 > 0 else x1, y + 3, -7, 'plate')
box('body', -16, 51, -9, 16, 65, 9, 'hide')
box('body', -18, 54, -6, 18, 64, 7, 'hide')                       # the lats flaring under the arms
box('body', -15, 57, -11, -1, 65, -9, 'hide'); box('body', 1, 57, -11, 15, 65, -9, 'hide')   # the chest
box('body', -15, 56, -10.5, -1, 57, -9, 'plate'); box('body', 1, 56, -10.5, 15, 57, -9, 'plate')
bone('heart', 'body', (0, 53.5, -10))
box('heart', -5, 50, -10.5, 5, 57, -9.5, 'core')                  # the furnace in its chest...
for y in (51, 53.5, 56):                                          # ...caged by ribs
    box('body', -9, y, -11, -3.5, y + 1.2, -10, 'rib'); box('body', 3.5, y, -11, 9, y + 1.2, -10, 'rib')
box('body', -1, 50, -11, 1, 57, -10.2, 'rib')
box('body', -14, 64, -5, 14, 70, 8, 'hide')                       # the hump of muscle behind its neck
box('body', -10, 68, 0, 10, 72, 7, 'hide')
for k, y in enumerate(range(44, 70, 4)):                         # the spine's ridge
    box('body', -1.2, y, 9, 1.2, y + 2.4, 11 + (k % 2) * 2, 'horntip')
def shoulders(s, sx):
    box('body', 14, 62, -8, 24, 70, 8, 'hide', sx)               # the shoulder's bulk
    box('body', 15, 68, -6, 23, 71, 6, 'horn', sx)               # a plate of bone over it, spiked
    box('body', 17, 71, -3, 20, 77, 0, 'horntip', sx)
    box('body', 20, 70, 1, 23, 75, 4, 'horntip', sx)
    box('body', 22, 66, -5, 26, 69, -1, 'horntip', sx)
sym(shoulders)
box('body', -6, 64, -12, 6, 73, 2, 'hide')                        # the thick neck, thrust forward

# the head: a wedge-shaped demon's face tapering to a pointed chin - a heavy scowling brow over deep-set burning eyes,
# veins of fire running from them down the cheeks, bone cheek-plates, slit nostrils, a wide grin of jagged teeth over a
# glowing throat (the lower jaw is its own bone, so it can open), finned ears, great ram horns curling round the sides
# and two tall horns sweeping up and back from the crown
bone('head', 'body', (0, 72, -8))
ROWS = [(62, 65, 2.5, -14, -9), (65, 68, 4.5, -15, -6), (68, 72, 6.5, -15, -4), (72, 76, 7.5, -15, -3),
        (76, 81, 8.5, -15, -2), (81, 85, 8, -14, 0), (85, 88, 6, -12, -1)]
bone('jaw', 'head', (0, 69, -5))
for y0, y1, w, z0, z1 in ROWS:
    box('jaw' if y1 <= 68 else 'head', -w, y0, z0, w, y1, z1, 'skull')
box('head', -6.5, 68, -15.3, 6.5, 69.4, -15, 'maw')               # the grin: a glowing seam...
box('jaw', -4.5, 67.2, -15.3, 4.5, 68, -15, 'maw')
for x in (-6.5, 5.5):                                             # ...curling up at its ends
    box('head', x, 69.4, -15.3, x + 1, 71, -15, 'maw')
for i, x in enumerate((-5.5, -3.5, -1.5, 0.5, 2.5, 4.5)):         # jagged teeth, top and bottom
    box('head', x, 67.6 if i % 2 else 68.2, -15.6, x + 1.1, 69.4, -15.1, 'tooth')
    box('jaw', x + 0.9, 67.2, -15.6, x + 1.9, 68.6 if i % 2 else 68.1, -15.1, 'tooth')
box('head', -2, 73, -15.4, -1, 74.6, -15, 'socket'); box('head', 1, 73, -15.4, 2, 74.6, -15, 'socket')   # nostrils
def face(s, sx):
    box('head', 1.2, 76.5, -15.4, 7, 80.2, -15, 'socket', sx)    # a deep socket...
    bone('eye' + s, 'head', (1.6 * sx, 78.4, -15.4), (0, 0, -14 * sx))
    box('eye' + s, 1.8, 77.6, -15.7, 6.2, 79.2, -15.3, 'eye', sx)   # ...and a slanted burning eye in it
    bone('brow' + s, 'head', (0.4 * sx, 80, -15), (0, 0, -22 * sx))
    box('brow' + s, 0, 80, -17, 9.5, 83, -14, 'skull', sx)        # the scowling brow, jutting over the eyes
    box('brow' + s, 0.5, 79.6, -17.2, 9, 80.2, -15, 'horntip', sx)
    for x, y0, y1 in ((5.4, 74.5, 76.5), (4.8, 72.8, 74.6), (5.3, 71, 72.9), (2.6, 74.6, 76.5), (3.2, 73, 74.7)):   # veins of fire, cracking down from the eyes
        box('head', x, y0, -15.25, x + 0.7, y1, -15.05, 'vein', sx)
    box('head', 5, 72, -15.7, 8.2, 74, -14.5, 'horn', sx)        # cheek plates
    box('head', 6.5, 70, -14.5, 7.8, 72, -9, 'horn', sx)
    bone('ear' + s, 'head', (8.5 * sx, 79, -5), (0, -35 * sx, -15 * sx))
    box('ear' + s, 8.5, 77, -6, 15, 81, -4.5, 'skull', sx)       # finned ears, swept back
    box('ear' + s, 13, 78, -5.8, 17, 79.5, -4.7, 'horntip', sx)
    bone('crown' + s, 'head', (3 * sx, 87, -6), (-38, 0, -14 * sx))   # a tall horn sweeping up and back from the crown
    for k in range(6):
        w = 3.4 - k * 0.45
        box('crown' + s, 3 - w / 2, 87 + k * 3.2, -6 - w / 2, 3 + w / 2, 87 + k * 3.2 + 3.4, -6 + w / 2, 'darkhorn' if k < 5 else 'horntip', sx)
sym(face)
for x in (-1.5, 0.5):                                             # a beard of bone spurs under the chin
    box('jaw', x, 58.5, -13, x + 1, 62, -12, 'horntip')

def horns(s, sx):
    # great ram horns from the sides of the brow: out and back, down, and curling round forward past the cheek
    for k in range(48):
        t = k / 47
        ang = t * math.pi * 1.7
        r = 9 * (1 - 0.35 * t)
        x = 8 + 6 * t + 2 * math.sin(ang * 0.5)
        y = 82 + r * math.sin(ang) * 0.95
        z = -6 + r * (1 - math.cos(ang)) * 0.9 - 2 * t
        w = 6.2 * (1 - 0.68 * t)
        box('head', x - w / 2, y - w / 2, z - w / 2, x + w / 2, y + w / 2, z + w / 2, 'darkhorn' if t < 0.85 else 'horntip', sx)
sym(horns)

def scale_subtree(root, k, lift=(0, 0, 0)):
    """Scales a bone and everything under it about its pivot (and moves it), so a part can be resized whole."""
    c = list(BONES[root]['pivot'])
    sub = [root]
    for bn in ORDER:
        if BONES[bn]['parent'] in sub and bn not in sub: sub.append(bn)
    f = lambda v: [c[i] + (v[i] - c[i]) * k + lift[i] for i in range(3)]
    for bn in sub:
        B = BONES[bn]
        B['pivot'] = f(B['pivot'])
        B['cubes'] = [(f(o), [v * k for v in sz], m, inf) for (o, sz, m, inf) in B['cubes']]
scale_subtree('head', 1.4, (0, 2, -2))

def arms(s, sx):
    # long, heavy arms hanging to the knee: a bulging upper arm, a forearm thick at the elbow and spurred with bone,
    # a big knuckled hand and long jointed claws curling forward
    bone('arm' + s, 'body', (20 * sx, 64, 0), (-12, 0, 40 * sx))
    box('arm' + s, 15, 56, -6, 25, 68, 6, 'hide', sx)            # deltoid
    box('arm' + s, 16, 44, -5, 24.5, 57, 5, 'hide', sx)          # biceps
    box('arm' + s, 17, 45, 4, 24, 55, 7, 'hide', sx)             # triceps
    box('arm' + s, 17.5, 42, -4, 23, 45, 4, 'hide', sx)
    bone('forearm' + s, 'arm' + s, (20.2 * sx, 43, 0), (22, 0, -30 * sx))
    box('forearm' + s, 15, 32, -6, 25.5, 44, 6, 'hide', sx)      # thick below the elbow
    box('forearm' + s, 16.2, 21, -4.8, 24.2, 32, 4.8, 'hide', sx)
    box('forearm' + s, 15.6, 22, -5.2, 24.8, 26, 5.2, 'plate', sx)   # a band of plate at the wrist
    box('forearm' + s, 18.5, 41, 5, 22, 46, 13, 'horntip', sx)   # the elbow spike, raking back
    for i, y in enumerate((38, 33, 28)):                          # spurs down the outside of the forearm
        box('forearm' + s, 25, y, -1, 28.5 - i * 0.6, y + 2.5, 1.5, 'horntip', sx)
    bone('hand' + s, 'forearm' + s, (20.2 * sx, 21.5, 0), (2, 0, 10 * sx))
    box('hand' + s, 15.5, 12, -6.5, 25, 22, 6.5, 'hide', sx)
    for z in (-5, -1.5, 2, 5.5):                                  # knuckles
        box('hand' + s, 15, 12, z - 1.4, 16.2, 15, z + 1.4, 'plate', sx)
    for i, z in enumerate((-4.6, -1.5, 1.6, 4.7)):               # four fingers, two joints each, ending in claws
        f1 = 'digit%d%s' % (i, s); f2 = 'claw%d%s' % (i, s)
        x = 20.2 - 3 + i * 0.4
        bone(f1, 'hand' + s, (x * sx, 12.5, z), (6, 0, (6 - i * 4) * sx))
        box(f1, x - 1.3, 4, z - 1.3, x + 1.3, 13, z + 1.3, 'hide', sx)
        bone(f2, f1, (x * sx, 4.5, z), (16, 0, 0))
        box(f2, x - 1, -6, z - 1, x + 1, 5, z + 1, 'horn', sx)
        box(f2, x - 0.6, -10, z - 0.6, x + 0.6, -6, z + 0.6, 'horntip', sx)
    bone('thumb' + s, 'hand' + s, (17 * sx, 17, -6), (40, 0, -30 * sx))
    box('thumb' + s, 16, 8, -7.5, 18.5, 17, -5, 'hide', sx)
    box('thumb' + s, 16.4, 2, -7.1, 18, 8, -5.4, 'horn', sx)
sym(arms)

def legs(s, sx):
    bone('thigh' + s, 'hips', (7 * sx, 32, 0), (22, 0, 6 * sx))
    box('thigh' + s, 1.5, 22, -7, 12.5, 34, 7, 'hide', sx)
    box('thigh' + s, 2.5, 16, -6, 11.5, 23, 6, 'hide', sx)
    box('thigh' + s, 3, 26, -8, 11, 33, -6.5, 'plate', sx)
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
THORNS = [(-16, 30, -52), (-8, 34, -28), (0, 38, 0), (8, 34, 28), (16, 30, 52)]   # x, height, tilt (deg)
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
EMISSIVE = {'core', 'eye', 'maw', 'fire', 'fire2', 'fire3', 'vein'}
def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
def paint(mat, x, y, w, h, seed):
    """The colour of texel (x, y) on a face of size w x h of this material."""
    r = random.Random(seed * 7919 + x * 131 + y)
    if mat == 'hide':
        base = lerp((22, 15, 17), (44, 24, 22), r.random() * 0.6)
        return base
    if mat == 'socket': return (8, 4, 6)
    if mat == 'tooth': return lerp((200, 190, 170), (150, 136, 120), r.random())
    if mat == 'vein': return lerp((255, 120, 30), (255, 180, 70), r.random())
    if mat == 'skull': return lerp((30, 20, 22), (56, 30, 28), r.random() * 0.6)
    if mat == 'darkhorn': t = y / max(1, h - 1); return lerp((88, 74, 70), (34, 26, 28), t * 0.6 + r.random() * 0.25)
    if mat == 'plate': return lerp((30, 26, 30), (54, 46, 50), r.random() * 0.6)
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
    if mat == 'eye': return (255, 236, 140) if r.random() < 0.8 else (255, 255, 230)
    if mat == 'maw': return lerp((150, 34, 8), (210, 70, 14), r.random())
    if mat == 'fire': return lerp((255, 80, 10), (255, 150, 30), r.random())
    if mat == 'fire2': return lerp((255, 150, 30), (255, 210, 70), r.random())
    if mat == 'fire3': return lerp((255, 220, 100), (255, 250, 200), r.random())
    return (255, 0, 255)

# molten cracks: on the hide, a few random-walk lines of glowing orange per face (the cracked-ember look)
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
def flicker(A, L, strength=0.2):
    for i in range(5):
        r = random.Random(i * 31)
        ks = [(round(L * j / 8, 4), (1 + r.uniform(-0.3, 0.3) * strength, 1 + r.uniform(-0.2, 1) * strength, 1 + r.uniform(-0.3, 0.3) * strength)) for j in range(8)]
        ks.append((L, ks[0][1]))
        key(A, 'flame%d' % i, 'scale', ks, False)

# idle: breathing, looking about, wings stirring, braziers flickering, the tail swaying
A = anim('idle', 4.0, True)
key(A, 'body', 'rot', [(0, Z), (2, (3, 0, 0)), (4, Z)])
key(A, 'hips', 'pos', [(0, Z), (2, (0, 0.6, 0)), (4, Z)])
key(A, 'head', 'rot', [(0, Z), (1, (-3, 6, 0)), (2, (2, 0, 0)), (3, (-3, -6, 0)), (4, Z)])
key(A, 'jaw', 'rot', [(0, Z), (2, (6, 0, 0)), (4, Z)])
key(A, 'arm_r', 'rot', [(0, Z), (2, (-3, 0, 4)), (4, Z)])
key(A, 'forearm_r', 'rot', [(0, Z), (2, (6, 0, 0)), (4, Z)])
key(A, 'wing_r', 'rot', [(0, Z), (2, (0, -4, 8)), (4, Z)])
key(A, 'wingfore_r', 'rot', [(0, Z), (2, (0, 0, -6)), (4, Z)])
for k in range(6): key(A, 'tail%d' % k, 'rot', wave(4.0, 4 + k, k * 0.08, axis=1), False)
flicker(A, 4.0)

# walk: a heavy stride - hips dropping on each step, the torso turning against the legs, arms swinging, wings half-folded
A = anim('walk', 2.4, True)
key(A, 'thigh_r', 'rot', [(0, (22, 0, 0)), (0.6, (0, 0, 0)), (1.2, (-24, 0, 0)), (1.8, (0, 0, 0)), (2.4, (22, 0, 0))], shift=1.2)
key(A, 'shin_r', 'rot', [(0, (-6, 0, 0)), (0.6, (0, 0, 0)), (1.2, (6, 0, 0)), (1.6, (-38, 0, 0)), (2.0, (-20, 0, 0)), (2.4, (-6, 0, 0))], shift=1.2)
key(A, 'foot_r', 'rot', [(0, (-10, 0, 0)), (0.6, Z), (1.2, (18, 0, 0)), (1.6, (14, 0, 0)), (2.4, (-10, 0, 0))], shift=1.2)
key(A, 'hips', 'pos', [(0, Z), (0.6, (0, -1.6, 0)), (1.2, Z), (1.8, (0, -1.6, 0)), (2.4, Z)])
key(A, 'hips', 'rot', [(0, (0, 0, 2)), (1.2, (0, 0, -2)), (2.4, (0, 0, 2))])
key(A, 'body', 'rot', [(0, (4, -5, 0)), (1.2, (4, 5, 0)), (2.4, (4, -5, 0))])
key(A, 'head', 'rot', [(0, (-2, 4, 0)), (0.6, (2, 0, 0)), (1.2, (-2, -4, 0)), (1.8, (2, 0, 0)), (2.4, (-2, 4, 0))])
key(A, 'arm_r', 'rot', [(0, (18, 0, -6)), (1.2, (-16, 0, -6)), (2.4, (18, 0, -6))], shift=1.2)
key(A, 'forearm_r', 'rot', [(0, (10, 0, 0)), (1.2, (-4, 0, 0)), (2.4, (10, 0, 0))], shift=1.2)
key(A, 'wing_r', 'rot', [(0, (0, 12, -20)), (1.2, (0, 12, -16)), (2.4, (0, 12, -20))])
key(A, 'wingfore_r', 'rot', [(0, (0, 0, 22)), (2.4, (0, 0, 22))])
for k in range(6): key(A, 'tail%d' % k, 'rot', wave(2.4, 6 + k, k * 0.1, axis=1), False)
flicker(A, 2.4)

# roar: it gathers itself, then throws its head back - jaw wide, arms and wings flung out, the braziers flaring
A = anim('roar', 3.2, False)
key(A, 'body', 'rot', [(0, Z), (0.6, (10, 0, 0)), (1.0, (-14, 0, 0)), (2.4, (-12, 0, 0)), (3.2, Z)])
key(A, 'hips', 'pos', [(0, Z), (0.6, (0, -2, 0)), (1.0, (0, 0.5, 0)), (2.4, (0, 0.5, 0)), (3.2, Z)])
key(A, 'head', 'rot', [(0, Z), (0.6, (16, 0, 0)), (1.0, (-30, 0, 0)), (1.7, (-26, 6, 0)), (2.4, (-30, -6, 0)), (3.2, Z)])
key(A, 'jaw', 'rot', [(0, Z), (0.6, Z), (1.0, (40, 0, 0)), (2.4, (38, 0, 0)), (2.9, Z), (3.2, Z)])
key(A, 'arm_r', 'rot', [(0, Z), (0.6, (10, 0, -16)), (1.0, (-30, 0, 34)), (2.4, (-32, 0, 30)), (3.2, Z)])
key(A, 'forearm_r', 'rot', [(0, Z), (0.6, (20, 0, 0)), (1.0, (-24, 0, 10)), (2.4, (-22, 0, 10)), (3.2, Z)])
key(A, 'hand_r', 'rot', [(0, Z), (1.0, (-20, 0, 0)), (2.4, (-20, 0, 0)), (3.2, Z)])
for i in range(4): key(A, 'claw%d_r' % i, 'rot', [(0, Z), (1.0, (-14, 0, 0)), (2.4, (-14, 0, 0)), (3.2, Z)])
key(A, 'wing_r', 'rot', [(0, Z), (0.6, (0, 18, -12)), (1.0, (0, -16, 18)), (2.4, (0, -14, 16)), (3.2, Z)])
key(A, 'wingfore_r', 'rot', [(0, Z), (0.6, (0, 0, 14)), (1.0, (0, 0, -14)), (2.4, (0, 0, -12)), (3.2, Z)])
for i in range(5): key(A, 'flame%d' % i, 'scale', [(0, (1, 1, 1)), (0.9, (1, 1, 1)), (1.1, (1.5, 2.0, 1.5)), (2.4, (1.4, 1.8, 1.4)), (3.2, (1, 1, 1))], False)
for k in range(6): key(A, 'tail%d' % k, 'rot', [(0, Z), (1.0, (-6 - k, 0, 0)), (2.4, (-6 - k, 0, 0)), (3.2, Z)], False)

# slam: both fists raised high overhead, then the whole body comes down behind them - a deep crouch, the fists driven
# into the ground before it, the wings snapping up; a shockwave runs out from the blow
A = anim('slam', 2.8, False)
A['fx'] = [('shock', 1.15, 0.9, (0, -0.5, -32))]
key(A, 'arm_r', 'rot', [(0, Z), (0.9, (165, 0, -34)), (1.05, (80, 0, -34)), (1.15, (4, 0, -36)), (1.25, (6, 0, -36)), (1.9, (6, 0, -36)), (2.8, Z)])
key(A, 'forearm_r', 'rot', [(0, Z), (0.9, (-14, 0, 0)), (1.15, (-6, 0, 10)), (1.9, (-6, 0, 10)), (2.8, Z)])
key(A, 'hand_r', 'rot', [(0, Z), (1.15, (-30, 0, 0)), (1.9, (-30, 0, 0)), (2.8, Z)])
key(A, 'body', 'rot', [(0, Z), (0.9, (-16, 0, 0)), (1.05, (8, 0, 0)), (1.15, (40, 0, 0)), (1.3, (36, 0, 0)), (1.9, (36, 0, 0)), (2.8, Z)])
key(A, 'hips', 'pos', [(0, Z), (0.9, (0, 2, 2)), (1.15, (0, -10, -4)), (1.25, (0, -9.5, -4)), (1.9, (0, -9.5, -4)), (2.8, Z)])
key(A, 'hips', 'rot', [(0, Z), (0.9, (-6, 0, 0)), (1.15, (14, 0, 0)), (1.9, (14, 0, 0)), (2.8, Z)])
key(A, 'thigh_r', 'rot', [(0, Z), (0.9, (-6, 0, 0)), (1.15, (40, 0, -6)), (1.9, (40, 0, -6)), (2.8, Z)])
key(A, 'shin_r', 'rot', [(0, Z), (1.15, (-58, 0, 0)), (1.9, (-58, 0, 0)), (2.8, Z)])
key(A, 'foot_r', 'rot', [(0, Z), (1.15, (6, 0, 0)), (1.9, (6, 0, 0)), (2.8, Z)])
key(A, 'head', 'rot', [(0, Z), (0.9, (-26, 0, 0)), (1.15, (-24, 0, 0)), (1.6, (-30, 0, 0)), (2.8, Z)])
key(A, 'jaw', 'rot', [(0, Z), (0.9, (18, 0, 0)), (1.15, (40, 0, 0)), (1.8, (24, 0, 0)), (2.8, Z)])
key(A, 'wing_r', 'rot', [(0, Z), (0.9, (0, -8, 22)), (1.15, (0, 16, 28)), (1.4, (0, 12, 18)), (2.8, Z)])
key(A, 'wingfore_r', 'rot', [(0, Z), (1.15, (0, 0, -18)), (1.5, (0, 0, -8)), (2.8, Z)])
key(A, 'tail0', 'rot', [(0, Z), (1.15, (-34, 0, 0)), (1.9, (-34, 0, 0)), (2.8, Z)], False)
for k in range(1, 6): key(A, 'tail%d' % k, 'rot', [(0, Z), (1.15, (-6, 0, 0)), (1.35, (2, 0, 0)), (2.8, Z)], False)
for i in range(5): key(A, 'flame%d' % i, 'scale', [(0, (1, 1, 1)), (1.1, (1, 1, 1)), (1.2, (1.6, 2.2, 1.6)), (2.0, (1.1, 1.2, 1.1)), (2.8, (1, 1, 1))], False)

# swipe: the right arm wound back and torn across its front, the torso turning into it
A = anim('swipe', 1.8, False)
key(A, 'arm_r', 'rot', [(0, Z), (0.55, (40, 0, 46)), (0.8, (70, 0, -40)), (1.0, (66, 0, -48)), (1.8, Z)], mirror=False)
key(A, 'forearm_r', 'rot', [(0, Z), (0.55, (20, 0, 0)), (0.8, (-10, 0, -20)), (1.8, Z)], mirror=False)
key(A, 'body', 'rot', [(0, Z), (0.55, (0, -22, 0)), (0.8, (6, 24, 0)), (1.0, (6, 26, 0)), (1.8, Z)])
key(A, 'head', 'rot', [(0, Z), (0.55, (0, 12, 0)), (0.8, (0, -14, 0)), (1.8, Z)])
key(A, 'arm_l', 'rot', [(0, Z), (0.55, (-14, 0, 8)), (0.8, (10, 0, -6)), (1.8, Z)], mirror=False)
key(A, 'jaw', 'rot', [(0, Z), (0.8, (20, 0, 0)), (1.8, Z)])

# wing gust: the wings drawn up and forward, then hurled back and down in one great beat
A = anim('wing_gust', 2.4, False)
key(A, 'wing_r', 'rot', [(0, Z), (0.9, (0, 34, 26)), (1.15, (0, -36, -12)), (1.5, (0, -30, -8)), (2.4, Z)])
key(A, 'wingfore_r', 'rot', [(0, Z), (0.9, (0, 10, 24)), (1.15, (0, -6, -26)), (1.5, (0, 0, -20)), (2.4, Z)])
for i in range(5): key(A, 'finger%d_r' % i, 'rot', [(0, Z), (0.9, (0, 0, 10 + i * 4)), (1.15, (0, 0, -8 - i * 3)), (2.4, Z)])
key(A, 'body', 'rot', [(0, Z), (0.9, (8, 0, 0)), (1.15, (-10, 0, 0)), (1.5, (-8, 0, 0)), (2.4, Z)])
key(A, 'hips', 'pos', [(0, Z), (0.9, (0, -1.5, 0)), (1.15, (0, 1.5, 0)), (2.4, Z)])
key(A, 'head', 'rot', [(0, Z), (0.9, (10, 0, 0)), (1.15, (-14, 0, 0)), (2.4, Z)])
key(A, 'jaw', 'rot', [(0, Z), (1.15, (24, 0, 0)), (1.7, (6, 0, 0)), (2.4, Z)])

# death: struck, it staggers and clutches at the furnace in its chest as it gutters; it drops to its knees and roars at
# the sky with its arms flung wide - its braziers burst - and then it comes apart: the wings crumble to ash from the
# tips inward, the heart goes out, and the husk pitches forward onto its face
A = anim('death', 6.4, False)
A['fx'] = [('embers', 2.6, 6.4)]
key(A, 'body', 'rot', [(0, Z), (0.5, (-16, 8, 0)), (1.3, (18, 0, 0)), (2.2, (-20, 0, 0)), (3.2, (-22, 0, 0)), (4.4, (14, 0, 0)), (5.4, (60, 0, 0)), (5.7, (66, 0, 0)), (6.4, (64, 0, 0))])
key(A, 'hips', 'pos', [(0, Z), (0.5, (0, 0, 3)), (1.3, (0, -1, 0)), (2.0, (0, -16.5, -2)), (4.4, (0, -16.5, -2)), (5.4, (0, -15, -6)), (5.7, (0, -15.5, -7)), (6.4, (0, -15, -7))])
key(A, 'thigh_r', 'rot', [(0, Z), (0.5, (-8, 0, 0)), (1.3, (6, 0, 0)), (2.0, (70, 0, -6)), (6.4, (74, 0, -6))])
key(A, 'hips', 'rot', [(0, Z), (4.4, Z), (5.4, (22, 0, 0)), (5.7, (30, 0, 0)), (6.4, (29, 0, 0))])
key(A, 'shin_r', 'rot', [(0, Z), (2.0, (-80, 0, 0)), (6.4, (-84, 0, 0))])
key(A, 'foot_r', 'rot', [(0, Z), (2.0, (40, 0, 0)), (6.4, (44, 0, 0))])
key(A, 'head', 'rot', [(0, Z), (0.5, (-24, -10, 0)), (1.3, (26, 0, 0)), (2.2, (-44, 0, 0)), (3.2, (-46, 0, 0)), (4.4, (10, 0, 0)), (5.4, (24, 0, 10)), (6.4, (26, 0, 12))])
key(A, 'jaw', 'rot', [(0, Z), (0.5, (30, 0, 0)), (1.3, (10, 0, 0)), (2.2, (46, 0, 0)), (3.4, (44, 0, 0)), (4.6, (14, 0, 0)), (6.4, (18, 0, 0))])
key(A, 'arm_r', 'rot', [(0, Z), (0.5, (-20, 0, 20)), (1.3, (60, 0, -48)), (2.2, (-50, 0, 54)), (3.2, (-54, 0, 52)), (4.4, (10, 0, 6)), (5.4, (0, 0, 30)), (5.7, (-4, 0, 40)), (6.4, (-4, 0, 40))])
key(A, 'forearm_r', 'rot', [(0, Z), (1.3, (60, 0, -10)), (2.2, (-20, 0, 0)), (4.4, (0, 0, 0)), (6.4, (0, 0, 0))])
key(A, 'hand_r', 'rot', [(0, Z), (1.3, (20, 0, 0)), (2.2, (-26, 0, 0)), (4.4, Z), (6.4, (30, 0, 0))])
key(A, 'heart', 'scale', [(0, (1, 1, 1)), (0.5, (1.5, 1.5, 1.5)), (1.3, (0.8, 0.8, 0.8)), (2.2, (1.8, 1.8, 1.8)), (3.2, (1.4, 1.4, 1.4)), (4.6, (0.6, 0.6, 0.6)), (5.4, (0.02, 0.02, 0.02)), (6.4, (0.02, 0.02, 0.02))], False)
key(A, 'wing_r', 'rot', [(0, Z), (0.5, (0, -10, 12)), (1.3, (0, 16, -14)), (2.2, (0, -16, 30)), (3.2, (0, -14, 28)), (4.4, (0, 10, -20)), (6.4, (0, 14, -34))])
key(A, 'wingfore_r', 'rot', [(0, Z), (2.2, (0, 0, -16)), (3.2, (0, 0, -12)), (4.4, (0, 0, 20)), (6.4, (0, 0, 30))])
GONE = (0.02, 0.02, 0.02)
for i in range(5):   # the wings crumble from the outermost finger in
    t0 = 3.0 + (4 - i) * 0.32
    for k in range(1, 8):
        if i < 4: key(A, 'mem%d_%d_r' % (i, k), 'scale', [(0, (1, 1, 1)), (t0 + k * 0.03, (1, 1, 1)), (t0 + 0.45 + k * 0.03, GONE), (6.4, GONE)])
    key(A, 'finger%d_r' % i, 'scale', [(0, (1, 1, 1)), (t0 + 0.3, (1, 1, 1)), (t0 + 0.75, GONE), (6.4, GONE)])
for k in range(1, 7): key(A, 'memarm%d_r' % k, 'scale', [(0, (1, 1, 1)), (4.3 + k * 0.05, (1, 1, 1)), (4.8 + k * 0.05, GONE), (6.4, GONE)])
key(A, 'wingfore_r', 'scale', [(0, (1, 1, 1)), (4.6, (1, 1, 1)), (5.0, GONE), (6.4, GONE)])
key(A, 'wing_r', 'scale', [(0, (1, 1, 1)), (4.9, (1, 1, 1)), (5.3, GONE), (6.4, GONE)])
for i in range(5):   # the braziers burst at the height of the roar, and go out
    key(A, 'flame%d' % i, 'scale', [(0, (1, 1, 1)), (1.3, (0.7, 0.6, 0.7)), (2.2, (1.5, 2.0, 1.5)), (2.7 + i * 0.06, (2.6, 3.4, 2.6)), (2.95 + i * 0.06, GONE), (6.4, GONE)], False)
key(A, 'tail0', 'rot', [(0, Z), (2.0, (-30, 0, 0)), (4.4, (-32, 0, 0)), (5.4, (-14, 0, 0)), (5.7, (-10, 0, 4)), (6.4, (-10, 0, 6))], False)
for k in range(1, 6): key(A, 'tail%d' % k, 'rot', [(0, Z), (2.0, (4, 0, 0)), (6.4, (6, 4, 0))], False)

def export_anims(path):
    out = {}
    for name, A in ANIMS.items():
        bones = {}
        for bn, chans in A['bones'].items():
            e = {}
            for c, ks in chans.items():
                f = (lambda v: [v[0], -v[1], -v[2]]) if c == 'rot' else (lambda v: [-v[0], v[1], v[2]]) if c == 'pos' else (lambda v: list(v))
                e[{'rot': 'rotation', 'pos': 'position', 'scale': 'scale'}[c]] = {'%.4f' % t: {'vector': [round(x, 3) for x in f(v)], 'lerp_mode': 'catmullrom'} for t, v in ks}
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

def gif(tex, glow, UV, name, fps=12, size=(680, 560), scale=2.15, yaw=28, pitch=8):
    A = ANIMS[name]
    n = int(A['length'] * fps)
    frames = []
    for i in range(n + (0 if A['loop'] else 8)):
        t = min(A['length'], i / fps)
        shake = 0
        for f in A.get('fx', ()):
            if f[0] == 'shock' and f[1] <= t <= f[1] + 0.35: shake = 6 * (1 - (t - f[1]) / 0.35) * (1 if i % 2 else -1)
        im = render(tex, glow, UV, yaw=yaw, pitch=pitch, size=size, scale=scale, pose=pose_at(A, t), cy=0.6 + shake / 560, ground_y=-0.5, effects=fx_at(A, t))
        ImageDraw.Draw(im).text((12, 10), name, fill=(255, 220, 160))
        frames.append(im.convert('P', palette=Image.ADAPTIVE, colors=200))
    path = os.path.join(OUT, 'demon_%s.gif' % name)
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=int(1000 / fps), loop=0, disposal=2)
    return path

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
    if mode in ('head2', 'hands'):
        for bn in ORDER:
            if bn.startswith(('thorn', 'flame', 'wing', 'finger', 'mem')): BONES[bn]['cubes'] = []
        tex, glow, UV = build_texture()
        if mode == 'head2':
            ims = [render(tex, glow, UV, yaw=y, pitch=p, size=(600, 600), scale=9, cy=1.12) for y, p in ((0, 4), (35, 8), (90, 2))]
        else:
            ims = [render(tex, glow, UV, yaw=y, pitch=4, size=(600, 600), scale=5, cy=0.35) for y in (0, 30, -30)]
        sh = Image.new('RGB', (1800, 600))
        for i, im in enumerate(ims): sh.paste(im, (600 * i, 0))
        sh.save(os.path.join(OUT, 'demon_%s.png' % mode))
    if mode == 'head':
        ims = [render(tex, glow, UV, yaw=y, pitch=4, size=(600, 600), scale=9, cy=1.08) for y in (0, 30, 70)]
        sh = Image.new('RGB', (1800, 600))
        for i, im in enumerate(ims): sh.paste(im, (600 * i, 0))
        sh.save(os.path.join(OUT, 'demon_head.png'))
    if mode == 'gif':
        names = sys.argv[2:] or list(ANIMS)
        for nm in names: print(gif(tex, glow, UV, nm))
    if mode == 'preview':
        S = 4.6
        hero = render(tex, glow, UV, yaw=18, pitch=-6, size=(1500, 1100), scale=S, cy=0.6)
        feet = 1100 * 0.6 + 48 * S
        ground(hero, feet)
        scene(hero, feet, 750 + 34 * S, S * 16 / 2.6)        # a mage, 2 blocks tall, at its foot (in game it's drawn 2.6x)
        ImageDraw.Draw(hero).text((24, 24), "The Cinder Sovereign  -  original world boss concept  (about 15 blocks tall)", fill=(255, 220, 160))
        small = []
        for y, p, lab, sc, cyy in [(0, 4, 'Front', 2.9, 0.62), (0, 4, 'Head', 9, 1.08), (60, 6, 'Side', 2.9, 0.62)]:
            sz = (600, 600) if lab == 'Head' else (900, 900)
            im = render(tex, glow, UV, yaw=y, pitch=p, size=sz, scale=sc, cy=cyy)
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
        os.makedirs(os.path.join(R, 'animations'), exist_ok=True)
        export_anims(os.path.join(R, 'animations', NAME + '.animation.json'))
