"""The look of three grimoires (all original designs): the Vector Grimoire (white and cyan on slate), the Grimoire of the
Quiet Fuse (rose on charcoal plum) and the Grimoire of the Foreseen (ice-white on deep navy) - covers, leather, trim,
inventory icons, book models, spell icons, the G-wheel ability icons, and the Foreseen's False Future double skin.
  Vector cover:    a field of small arrows flowing round the medallion like field lines, one great arrow bent back on itself.
  Quiet Fuse:      a lit fuse cord winding up round the medallion to a spark, round charges in the corners, drifting bubbles.
  Foreseen:        a star chart, a great almond eye drawn round the medallion, faint after-images of its rim.
python3 tools/trio_assets.py [preview_dir]"""
import colorsys, json, math, os, random, sys
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import covers as C
from icons import make

ROOT = os.path.join(HERE, '..', 'src/main/resources/assets/iron_spell_grimoire/textures')
ITEM = os.path.join(ROOT, 'item')
M = os.path.join(ITEM, 'spell_book_models')
C.ACC['vector'] = (200, 240, 255); C.GLOW['vector'] = (80, 220, 255)
C.ACC['fuse'] = (255, 170, 205); C.GLOW['fuse'] = (255, 90, 160)
C.ACC['foreseen'] = (215, 230, 255); C.GLOW['foreseen'] = (140, 180, 255)


def dot(d, x, y, r, fill):
    d.ellipse([(x - r) * C.S, (y - r) * C.S, (x + r) * C.S, (y + r) * C.S], fill=fill)


def arrow(d, x, y, ang, ln, fill, w=1):
    x1, y1 = x + math.cos(ang) * ln, y + math.sin(ang) * ln
    C.line(d, [(x, y), (x1, y1)], fill=fill, width=w)
    for s in (-1, 1):
        a2 = ang + math.pi + s * 0.5
        C.line(d, [(x1, y1), (x1 + math.cos(a2) * ln * 0.45, y1 + math.sin(a2) * ln * 0.45)], fill=fill, width=w)


# ------------------------------------------------------------------ covers
def vector_cover():
    c = C.Cv((20, 28, 38), (6, 8, 12), 41, grain=7)
    X0, Y0, R0 = C.X0, C.Y0, C.R0
    l, d = c.layer()
    # a dipole's field: arrows along the field direction, skipping the medallion
    for gx in range(30, 236, 17):
        for gy in range(30, 360, 17):
            if math.hypot(gx - X0, gy - Y0) < R0 + 14 or gy > 312: continue
            ax, ay = X0, Y0 - 120; bx, by = X0, Y0 + 120
            ex = (gx - ax) / max(1, math.hypot(gx - ax, gy - ay)) ** 3 * 4e4 - (gx - bx) / max(1, math.hypot(gx - bx, gy - by)) ** 3 * 4e4
            ey = (gy - ay) / max(1, math.hypot(gx - ax, gy - ay)) ** 3 * 4e4 - (gy - by) / max(1, math.hypot(gx - bx, gy - by)) ** 3 * 4e4
            arrow(d, gx, gy, math.atan2(ey, ex), 7, (150, 225, 255, 150))
    c.put(l, (80, 220, 255), 4, 0.5)
    # the great arrow, flying up the left, bent back round the top of the medallion and down the right
    l, d = c.layer()
    pts = [(X0 - R0 - 22, 300)]
    pts += [(X0 - R0 - 22, Y0)]
    for k in range(0, 19):
        a = math.pi + k * math.pi / 18
        pts.append((X0 + math.cos(a) * (R0 + 22), Y0 + math.sin(a) * (R0 + 22)))
    pts += [(X0 + R0 + 22, 286)]
    C.line(d, pts, fill=(220, 245, 255, 255), width=4)
    C.poly(d, [(X0 + R0 + 22, 302), (X0 + R0 + 12, 282), (X0 + R0 + 32, 282)], fill=(220, 245, 255, 255))
    c.put(l, (80, 220, 255), 7, 0.7)
    l, d = c.layer(); C.frame(d, (200, 240, 255, 255), 16, 2, 0, True); c.put(l)
    return c


def fuse_cover():
    c = C.Cv((40, 18, 32), (10, 4, 10), 53, grain=8)
    X0, Y0, R0 = C.X0, C.Y0, C.R0
    rnd = random.Random(3)
    l, d = c.layer()
    # drifting bubbles
    for _ in range(18):
        x, y, r = rnd.uniform(28, 228), rnd.uniform(30, 300), rnd.uniform(3, 9)
        if math.hypot(x - X0, y - Y0) < R0 + 18: continue
        d.ellipse([(x - r) * C.S, (y - r) * C.S, (x + r) * C.S, (y + r) * C.S], outline=(255, 180, 215, 140), width=C.S)
        dot(d, x - r * 0.35, y - r * 0.35, max(1, r * 0.2), (255, 220, 235, 160))
    # round charges in the four corners
    for x, y in ((44, 44), (212, 44), (44, 300), (212, 300)):
        dot(d, x, y, 9, (24, 10, 20, 255))
        d.ellipse([(x - 9) * C.S, (y - 9) * C.S, (x + 9) * C.S, (y + 9) * C.S], outline=(255, 170, 205, 255), width=C.S * 2)
        C.line(d, [(x + 6, y - 6), (x + 11, y - 12)], fill=(255, 170, 205, 255), width=2)
        dot(d, x + 12, y - 13, 2.4, (255, 240, 200, 255))
    c.put(l, (255, 90, 160), 4, 0.45)
    # the fuse: up from the foot, once round the medallion, to a spark at the top
    l, d = c.layer()
    pts = []
    for k in range(60):
        t = k / 59
        if t < 0.35:
            u = t / 0.35; pts.append((X0 + math.sin(u * 9) * 10, 312 - u * (312 - (Y0 + R0 + 16))))
        else:
            u = (t - 0.35) / 0.65; a = math.pi / 2 + u * 1.85 * math.pi
            pts.append((X0 + math.cos(a) * (R0 + 16), Y0 + math.sin(a) * (R0 + 16)))
    C.line(d, pts, fill=(120, 60, 80, 255), width=4)
    C.line(d, pts[40:], fill=(255, 150, 190, 255), width=2)   # the burning end, lit
    sx, sy = pts[-1]
    for k in range(8):
        a = k * math.pi / 4
        C.line(d, [(sx, sy), (sx + math.cos(a) * (9 if k % 2 else 14), sy + math.sin(a) * (9 if k % 2 else 14))], fill=(255, 230, 180, 255), width=2)
    dot(d, sx, sy, 4, (255, 250, 230, 255))
    c.put(l, (255, 90, 160), 7, 0.7)
    l, d = c.layer(); C.frame(d, (255, 170, 205, 255), 16, 2, 0, True); c.put(l)
    return c


def foreseen_cover():
    c = C.Cv((14, 20, 48), (3, 4, 14), 67, grain=7)
    X0, Y0, R0 = C.X0, C.Y0, C.R0
    rnd = random.Random(7)
    l, d = c.layer()
    stars = []
    for _ in range(46):
        x, y = rnd.uniform(24, 232), rnd.uniform(24, 320)
        if abs(y - Y0) < R0 + 30 and abs(x - X0) < 120: continue
        stars.append((x, y)); dot(d, x, y, rnd.choice([0.8, 1.1, 1.6]), (220, 230, 255, rnd.randint(150, 255)))
    for i in range(0, len(stars) - 1, 3):   # constellations
        a, b = stars[i], stars[i + 1]
        if math.hypot(a[0] - b[0], a[1] - b[1]) < 70: C.line(d, [a, b], fill=(160, 190, 255, 110), width=1)
    c.put(l, (140, 180, 255), 3, 0.4)
    # the eye round the medallion, and its after-images
    l, d = c.layer()
    for k, al in ((3, 60), (2, 100), (1, 150), (0, 255)):
        w, h = R0 + 52 + k * 7, R0 + 18 + k * 4
        top = [(X0 + math.cos(a) * w, Y0 - math.sin(a) * h * math.sin(a) ** 0.3) for a in [i * math.pi / 30 for i in range(31)]]
        bot = [(X0 + math.cos(a) * w, Y0 + math.sin(a) * h * math.sin(a) ** 0.3) for a in [i * math.pi / 30 for i in range(31)]]
        C.line(d, top, fill=(215, 230, 255, al), width=3 if k == 0 else 1)
        C.line(d, bot, fill=(215, 230, 255, al), width=3 if k == 0 else 1)
    for k in range(12):   # lashes
        a = math.pi * (0.15 + 0.7 * k / 11)
        x, y = X0 - math.cos(a) * (R0 + 52), Y0 - math.sin(a) * (R0 + 18)
        C.line(d, [(x, y), (x - math.cos(a) * 8, y - math.sin(a) * 10)], fill=(215, 230, 255, 200), width=1)
    c.put(l, (140, 180, 255), 7, 0.7)
    l, d = c.layer(); C.frame(d, (215, 230, 255, 255), 16, 2, 0, True); c.put(l)
    return c


def shift(im, hue, sm, vm):
    im = im.convert('RGBA'); px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0: continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            nr, ng, nb = colorsys.hsv_to_rgb(hue, min(1, max(s, 0.15) * sm), min(1, v * vm))
            px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    return im


BOOKS = {
    'vector_grimoire': (vector_cover, 'vector', 0.54, (200, 240, 255)),
    'fuse_grimoire': (fuse_cover, 'fuse', 0.92, (255, 170, 205)),
    'foreseen_grimoire': (foreseen_cover, 'foreseen', 0.62, (215, 230, 255)),
}


def book_files(key, fn, deco, hue, trim):
    c = fn(); C.decorate(c, deco); c.save(key + '_front')
    b = Image.open(os.path.join(M, key + '_front.png')).convert('RGBA')
    b = Image.blend(b, Image.new('RGBA', b.size, tuple(int(v) for v in b.resize((1, 1), Image.BOX).getpixel((0, 0)))), 0.55)
    b.save(os.path.join(M, key + '_back.png'))
    shift(Image.open(os.path.join(M, 'dark_grimoire_leather.png')), hue, 0.8, 0.75).save(os.path.join(M, key + '_leather.png'))
    t = Image.open(os.path.join(M, 'spatial_grimoire_trim.png')).convert('RGBA'); px = t.load()
    for y in range(t.height):
        for x in range(t.width):
            r, g, bb, a = px[x, y]
            k = 0.45 + 0.6 * min(1.2, (r + g + bb) / 450.0)
            px[x, y] = tuple(min(255, int(v * k)) for v in trim) + (a,)
    t.save(os.path.join(M, key + '_trim.png'))
    ic = shift(Image.open(os.path.join(ITEM, 'dark_grimoire.png')), hue, 1.1, 1.05)
    ic.save(os.path.join(ITEM, key + '.png'))
    MD = os.path.join(HERE, '..', 'src/main/resources/assets/iron_spell_grimoire/models/item')
    for suffix in ('', '_model', '_open'):
        src = json.load(open(os.path.join(MD, 'architect_grimoire%s.json' % suffix)))
        json.dump(json.loads(json.dumps(src).replace('architect_grimoire', key)), open(os.path.join(MD, key + suffix + '.json'), 'w'), indent=1)


# ------------------------------------------------------------------ spell and wheel icons
VO = ((26, 40, 54), (4, 6, 10), (120, 220, 255), (235, 250, 255))
FO = ((54, 22, 42), (8, 3, 8), (255, 130, 180), (255, 230, 200))
SO = ((20, 28, 66), (2, 3, 12), (150, 185, 255), (235, 240, 255))


def icons():
    # Vector
    make('redirect', *VO, [('m', 'E', 2, 9, 5, 12), ('a', 'l', 5, 10, 12, 4), ('a', 'l', 12, 4, 9, 4), ('a', 'l', 12, 4, 12, 7), ('w', 'p', 3, 10)])
    make('recoil_step', *VO, [('m', 'l', 3, 13, 12, 13), ('a', 'l', 5, 11, 7, 6), ('a', 'l', 7, 6, 10, 9), ('a', 'l', 7, 6, 7, 2), ('m', 'p', 6, 12), ('m', 'p', 8, 12)])
    make('return_to_sender', *VO, [('m', 'l', 2, 5, 11, 5), ('m', 'l', 9, 3, 11, 5), ('m', 'l', 9, 7, 11, 5), ('a', 'l', 13, 10, 4, 10), ('a', 'l', 6, 8, 4, 10), ('a', 'l', 6, 12, 4, 10), ('w', 'l', 13, 3, 13, 12)])
    make('pressure_gust', *VO, [('m', 'l', 2, y, 11 + (y % 3), y) for y in (4, 7, 10)] + [('a', 'l', 12, 3, 14, 7), ('a', 'l', 14, 7, 12, 11), ('w', 'p', 13, 7)])
    make('pulse_touch', *VO, [('m', 'E', 2, 6, 5, 9), ('a', 'e', 6, 4, 9, 11), ('a', 'e', 9, 2, 13, 13), ('w', 'p', 3, 7)])
    make('wind_vector', *VO, [('m', 'a', 2, 2, 13, 13, 200, 520), ('a', 'a', 4, 4, 11, 11, 20, 340), ('w', 'a', 6, 6, 9, 9, 0, 300), ('w', 'p', 7, 7)])
    make('plasma_point', *VO, [('a', 'E', 5, 5, 10, 10), ('w', 'E', 6, 6, 9, 9)] + [('m', 'l', 7 + int(5 * math.cos(a)), 7 + int(5 * math.sin(a)), 7 + int(7 * math.cos(a)), 7 + int(7 * math.sin(a))) for a in [k * math.pi / 4 for k in range(8)]])
    make('vector_absolute', *VO, [('m', 'e', 1, 1, 14, 14)] + [('a', 'l', 7 + int(2 * math.cos(a)), 7 + int(2 * math.sin(a)), 7 + int(6 * math.cos(a)), 7 + int(6 * math.sin(a))) for a in [k * math.pi / 4 for k in range(8)]] + [('w', 'p', 7, 7)])
    make('vec_stance', *VO, [('m', 'l', 3, 4, 12, 4), ('a', 'l', 12, 11, 3, 11), ('m', 'l', 10, 2, 12, 4), ('a', 'l', 5, 9, 3, 11), ('w', 'p', 7, 7)])
    make('vec_hover', *VO, [('m', 'l', 7, 12, 7, 4), ('m', 'l', 5, 6, 7, 4), ('m', 'l', 9, 6, 7, 4), ('a', 'l', 3, 14, 11, 14), ('w', 'p', 7, 9)])
    make('vec_reverse', *VO, [('m', 'l', 2, 7, 7, 7), ('m', 'l', 5, 5, 7, 7), ('m', 'l', 5, 9, 7, 7), ('w', 'l', 8, 3, 8, 11), ('a', 'l', 13, 7, 10, 7)])
    make('vec_lance', *VO, [('a', 'l', 2, 13, 12, 3), ('w', 'l', 10, 3, 12, 3), ('w', 'l', 12, 3, 12, 5), ('m', 'l', 3, 11, 5, 13), ('m', 'l', 5, 9, 7, 11)])
    # Quiet Fuse
    bomb = lambda x, y: [('d', 'E', x - 3, y - 3, x + 3, y + 3), ('m', 'e', x - 3, y - 3, x + 3, y + 3), ('m', 'l', x + 2, y - 3, x + 4, y - 5), ('w', 'p', x + 5, y - 6)]
    make('detonate', *FO, [('a', 'E', 4, 4, 11, 11), ('w', 'E', 6, 6, 9, 9)] + [('m', 'l', 7 + int(5 * math.cos(a)), 7 + int(5 * math.sin(a)), 7 + int(7 * math.cos(a)), 7 + int(7 * math.sin(a))) for a in [k * math.pi / 4 for k in range(8)]])
    make('prime_item', *FO, [('m', 'r', 4, 7, 9, 12), ('a', 'R', 4, 7, 9, 12), ('m', 'l', 9, 7, 12, 4), ('w', 'p', 12, 3), ('a', 'p', 6, 9)])
    make('heat_seeker', *FO, [('d', 'E', 3, 6, 12, 13), ('m', 'e', 3, 6, 12, 13), ('a', 'p', 6, 9), ('a', 'p', 9, 9), ('m', 'l', 7, 6, 7, 3), ('w', 'p', 7, 2), ('m', 'l', 2, 13, 13, 13)])
    make('air_bubble', *FO, [('m', 'e', 2, 5, 8, 11), ('m', 'e', 9, 2, 13, 6), ('m', 'e', 9, 9, 12, 12), ('w', 'p', 4, 7), ('w', 'p', 10, 3)])
    make('contact_charge', *FO, [('m', 'l', 2, 7, 6, 7), ('m', 'l', 4, 5, 6, 7), ('m', 'l', 4, 9, 6, 7)] + bomb(10, 8))
    make('chain_fuse', *FO, bomb(4, 11) + bomb(10, 6) + [('a', 'l', 6, 10, 9, 8)])
    make('hidden_tenant', *FO, [('m', 'E', 5, 1, 9, 5), ('m', 'r', 4, 6, 10, 13), ('d', 'E', 6, 8, 8, 10), ('a', 'p', 7, 9)])
    make('loop_of_ruin', *FO, [('m', 'a', 2, 2, 13, 13, 30, 330), ('m', 'l', 12, 2, 13, 4), ('m', 'l', 13, 4, 11, 5)] + bomb(7, 8))
    make('fuse_mode', *FO, [('m', 'e', 3, 3, 12, 12), ('a', 'l', 7, 7, 7, 4), ('a', 'l', 7, 7, 10, 8), ('w', 'p', 7, 7)])
    make('fuse_coin', *FO, [('a', 'E', 4, 4, 11, 11), ('m', 'e', 4, 4, 11, 11), ('d', 'l', 7, 6, 7, 9), ('m', 'l', 11, 4, 13, 2), ('w', 'p', 13, 1)])
    make('fuse_defuse', *FO, bomb(7, 8) + [('w', 'l', 2, 13, 13, 2)])
    # Foreseen
    eye = [('m', 'a', 1, 4, 14, 13, 200, 340), ('m', 'a', 1, 2, 14, 11, 20, 160), ('a', 'E', 5, 5, 10, 10), ('w', 'p', 7, 7)]
    make('step_through', *SO, [('m', 'l', 2, 12, 6, 12), ('a', 'l', 7, 12, 13, 12), ('w', 'l', 6, 4, 6, 13), ('a', 'l', 11, 10, 13, 12), ('a', 'l', 11, 14, 13, 12)])
    make('read_intent', *SO, eye)
    make('punish', *SO, [('a', 'l', 3, 12, 12, 3), ('w', 'l', 10, 3, 12, 3), ('w', 'l', 12, 3, 12, 5), ('m', 'l', 2, 9, 6, 13)])
    make('false_future', *SO, [('d', 'r', 3, 3, 6, 6), ('d', 'r', 2, 7, 7, 13), ('m', 'R', 3, 3, 6, 6), ('m', 'R', 2, 7, 7, 13), ('a', 'R', 9, 3, 12, 6), ('a', 'R', 8, 7, 13, 13)])
    make('snap_back', *SO, [('m', 'a', 2, 2, 13, 13, 60, 360), ('m', 'l', 2, 5, 4, 7), ('m', 'l', 4, 7, 6, 5), ('w', 'p', 7, 7), ('a', 'l', 7, 7, 7, 4)])
    make('inevitable', *SO, [('d', 'e', 9, 6, 13, 10), ('a', 'l', 2, 8, 9, 8), ('w', 'p', 11, 8), ('m', 'l', 7, 6, 9, 8), ('m', 'l', 7, 10, 9, 8)])
    make('seen_it_before', *SO, eye + [('a', 'l', 2, 14, 13, 14)])
    make('rewrite', *SO, [('m', 'r', 3, 2, 12, 13), ('d', 'l', 5, 5, 10, 5), ('d', 'l', 5, 8, 10, 8), ('a', 'l', 4, 12, 12, 3), ('w', 'p', 7, 7)])
    make('fore_glimpse', *SO, eye + [('m', 'p', 1, 1), ('m', 'p', 14, 2)])
    make('fore_strike', *SO, [('m', 'e', 3, 9, 12, 14), ('a', 'l', 7, 1, 7, 10), ('w', 'p', 7, 11)])
    make('fore_second', *SO, [('d', 'r', 9, 4, 12, 13), ('m', 'R', 9, 4, 12, 13), ('a', 'l', 8, 8, 3, 8), ('a', 'l', 3, 8, 5, 6), ('a', 'l', 3, 8, 5, 10)])
    make('fore_thread', *SO, [('m', 'E', 1, 6, 4, 9), ('m', 'E', 11, 6, 14, 9), ('a', 'a', 3, 3, 12, 11, 200, 340), ('w', 'p', 7, 4)])


def double_skin():
    """The False Future double: a player's shape in pale ice-white, edges in soft blue, see-through, two blue points for eyes."""
    sk = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    rnd = random.Random(11)
    boxes = [(0, 0, 8, 8, 8), (16, 16, 8, 12, 4), (40, 16, 4, 12, 4), (32, 48, 4, 12, 4), (0, 16, 4, 12, 4), (16, 48, 4, 12, 4)]
    for u, v, w, h, d in boxes:
        faces = [(u + d, v, w, d), (u + d + w, v, w, d), (u, v + d, d, h), (u + d, v + d, w, h), (u + d + w, v + d, d, h), (u + 2 * d + w, v + d, w, h)]
        for fx, fy, fw, fh in faces:
            for x in range(fx, fx + fw):
                for y in range(fy, fy + fh):
                    edge = x in (fx, fx + fw - 1) or y in (fy, fy + fh - 1)
                    k = rnd.randint(0, 10)
                    sk.putpixel((x, y), (120, 160, 255, 210) if edge else (210 + k, 222 + k, 245, 150))
    for x, y in ((10, 12), (13, 12)): sk.putpixel((x, y), (40, 90, 220, 255))
    sk.save(os.path.join(ROOT, 'entity', 'foreseen_double.png'))
    return sk


if __name__ == '__main__':
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    for key, (fn, deco, hue, trim) in BOOKS.items(): book_files(key, fn, deco, hue, trim)
    icons(); sk = double_skin()
    if preview:
        os.makedirs(preview, exist_ok=True)
        sheet = Image.new('RGBA', (3 * 270 + 20, 400 + 3 * 90 + 40), (30, 30, 36, 255))
        d = ImageDraw.Draw(sheet)
        groups = [['redirect', 'recoil_step', 'return_to_sender', 'pressure_gust', 'pulse_touch', 'wind_vector', 'plasma_point', 'vector_absolute', 'vec_stance', 'vec_hover', 'vec_reverse', 'vec_lance'],
                  ['detonate', 'prime_item', 'heat_seeker', 'air_bubble', 'contact_charge', 'chain_fuse', 'hidden_tenant', 'loop_of_ruin', 'fuse_mode', 'fuse_coin', 'fuse_defuse'],
                  ['step_through', 'read_intent', 'punish', 'false_future', 'snap_back', 'inevitable', 'seen_it_before', 'rewrite', 'fore_glimpse', 'fore_strike', 'fore_second', 'fore_thread']]
        for i, key in enumerate(BOOKS):
            sheet.alpha_composite(Image.open(os.path.join(M, key + '_front.png')).convert('RGBA'), (10 + i * 270, 8))
            sheet.alpha_composite(Image.open(os.path.join(ITEM, key + '.png')).convert('RGBA').resize((48, 48), Image.NEAREST), (10 + i * 270 + 200, 340))
            for j, n in enumerate(groups[i]):
                ic = Image.open(os.path.join(ROOT, 'gui', 'spell_icons', n + '.png')).convert('RGBA').resize((40, 40), Image.NEAREST)
                sheet.alpha_composite(ic, (10 + i * 270 + (j % 6) * 44, 410 + (j // 6) * 46))
        sheet.alpha_composite(sk.resize((128, 128), Image.NEAREST), (10, 520))
        d.text((150, 560), "False Future double skin; per book: cover, item icon, 8 page icons + G-wheel icons", fill=(220, 220, 230))
        sheet.save(os.path.join(preview, 'trio_look.png'))
    print('trio assets written')
