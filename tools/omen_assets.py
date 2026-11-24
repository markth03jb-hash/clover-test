"""The Grimoire of Ill Omen's look (original designs, black and deep blue): its cover, leather and trim, inventory icon,
spell icons, book models, the omen mark shown over pursuers, and the Afterimage's skin (a black-blue silhouette).
  Cover: night-blue leather; a trail of footprints walking up to the medallion and stopping short of it; needles of
  rain falling round it; a single hairline crack running off the medallion's rim.
python3 tools/omen_assets.py [preview_dir]"""
import colorsys, json, math, os, random, sys
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import covers as C
from icons import make

ROOT = os.path.join(HERE, '..', 'src/main/resources/assets/iron_spell_grimoire/textures')
ITEM = os.path.join(ROOT, 'item')
M = os.path.join(ITEM, 'spell_book_models')
BLUE = (120, 160, 255)
C.ACC['omen'] = (150, 180, 240)
C.GLOW['omen'] = (60, 100, 255)


def cover():
    """Calamity, not weather: one jagged crack splits the cover corner to corner (broken by the medallion), shards
    breaking off it; a weight falling at the top right; a row of dominoes toppling across the foot."""
    c = C.Cv((14, 18, 40), (4, 4, 12), 71, grain=8)
    X0, Y0, R0 = C.X0, C.Y0, C.R0
    rnd = random.Random(13)
    BL, PALE = (110, 150, 255, 235), (190, 210, 255, 255)
    # the crack: top-left to the medallion, and on from its far side to the bottom-right
    def jag(x0, y0, x1, y1, n, amp):
        pts = [(x0, y0)]
        for i in range(1, n):
            t = (i + rnd.uniform(-0.35, 0.35)) / n
            nx, ny = -(y1 - y0), (x1 - x0); L = math.hypot(nx, ny); nx, ny = nx / L, ny / L
            o = amp * (1 if i % 2 else -1) * rnd.uniform(0.5, 1.0)
            pts.append((x0 + (x1 - x0) * t + nx * o, y0 + (y1 - y0) * t + ny * o))
        pts.append((x1, y1)); return pts
    ang = math.atan2(Y0 - 34, X0 - 34)
    e1 = (X0 - math.cos(ang) * (R0 + 6), Y0 - math.sin(ang) * (R0 + 6))
    e2 = (X0 + math.cos(ang) * (R0 + 6), Y0 + math.sin(ang) * (R0 + 6))
    parts = [jag(34, 34, e1[0], e1[1], 15, 3.5), jag(e2[0], e2[1], 226, 262, 15, 3.5)]
    l, d = c.layer()
    for pts in parts:
        C.line(d, pts, fill=BL, width=4)
        C.line(d, pts, fill=(4, 5, 14, 255), width=2)   # the split itself: a dark gap lit at its edges
        for k in (4, 10):   # branch cracks
            bx, by = pts[k]
            C.crack(d, bx, by, ang + rnd.choice([-1, 1]) * 1.1, 34, (110, 150, 255, 190), 1, rnd, 1)
    # shards breaking off the crack, drifting away from it
    for pts in parts:
        for k in (3, 7, 12):
            px_, py_ = pts[k]
            side = 1 if k % 2 else -1
            for j in range(2):
                cx_ = px_ + side * (10 + j * 9) * math.sin(ang) * -1
                cy_ = py_ + side * (10 + j * 9) * math.cos(ang)
                r_ = 4.5 - j * 1.5; a0 = rnd.uniform(0, 6.28)
                tri = [(cx_ + math.cos(a0 + q * 2.2) * r_ * (1.3 if q == 0 else 1), cy_ + math.sin(a0 + q * 2.2) * r_) for q in range(3)]
                C.poly(d, tri, fill=(60, 90, 190, 210 - j * 60), outline=PALE)
    c.put(l, (60, 100, 255), 7, 0.7)
    # a weight falling, top right: an iron block with a ring, speed lines above it
    l, d = c.layer()
    wx, wy = 200, 72
    C.poly(d, [(wx - 15, wy - 6), (wx + 15, wy - 6), (wx + 19, wy + 16), (wx - 19, wy + 16)], fill=(22, 26, 52, 255), outline=(150, 180, 240, 255))
    d.ellipse([(wx - 6) * C.S, (wy - 17) * C.S, (wx + 6) * C.S, (wy - 5) * C.S], outline=(150, 180, 240, 255), width=2 * C.S)
    C.line(d, [(wx - 9, wy + 7), (wx + 9, wy + 7)], fill=(110, 150, 255, 200), width=1)
    for dx, ln in ((-12, 22), (0, 30), (12, 22)):
        C.line(d, [(wx + dx, wy - 24 - ln), (wx + dx, wy - 22)], fill=(150, 180, 240, 140), width=1)
    # dominoes toppling across the foot, each leaning further than the last
    for i in range(6):
        bx, by = 52 + i * 31, 300
        lean = math.radians(min(78, i * 15))
        w2, h = 6, 26
        corners = [(-w2, 0), (w2, 0), (w2, -h), (-w2, -h)]
        rot = [(bx + x * math.cos(lean) - y * math.sin(lean) * -1 * 0 + (-y) * math.sin(lean) * 1, by + x * math.sin(lean) + y * math.cos(lean)) for x, y in corners]
        C.poly(d, rot, fill=(22, 26, 52, 255), outline=(150, 180, 240, 255))
        for fy in (-0.28, -0.72):   # two pips
            px_, py_ = bx + (-fy * h) * math.sin(lean), by + fy * h * math.cos(lean)
            d.ellipse([(px_ - 1.6) * C.S, (py_ - 1.6) * C.S, (px_ + 1.6) * C.S, (py_ + 1.6) * C.S], fill=(150, 190, 255, 255))
        mx_, my_ = bx + (h * 0.5) * math.sin(lean), by - h * 0.5 * math.cos(lean)
        C.line(d, [(mx_ - 3 * math.cos(lean), my_ - 3 * math.sin(lean)), (mx_ + 3 * math.cos(lean), my_ + 3 * math.sin(lean))], fill=(110, 150, 255, 200), width=1)
    c.put(l, (60, 100, 255), 5, 0.45)
    l, d = c.layer(); C.frame(d, (150, 180, 240, 255), 16, 2, 0, True); c.put(l)
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


def icon():
    im = shift(Image.open(os.path.join(ITEM, 'dark_grimoire.png')), 0.62, 1.2, 0.95)
    px = im.load()
    for (x, y) in [(7, 5), (8, 6), (7, 7), (8, 8)]: px[x, y] = (150, 190, 255, 255)   # a small crack of light
    return im


# ------------------------------------------------------------------ spell icons
IO = ((30, 36, 70), (4, 4, 12), (110, 150, 255), (220, 230, 255))   # blue on black


def spell_icons():
    foot = lambda x, y: [('m', 'E', x, y, x + 2, y + 3), ('m', 'p', x + 1, y - 1)]
    make('ill_fated_path', *IO, foot(3, 10) + foot(7, 6) + foot(11, 2) + [('a', 'l', 12, 10, 14, 12), ('a', 'l', 14, 10, 12, 12)])
    make('laden_token', *IO, [('m', 'E', 4, 4, 11, 11), ('d', 'E', 6, 6, 9, 9), ('a', 'p', 7, 7), ('a', 'p', 8, 8), ('w', 'p', 5, 5)])
    make('afterimage', *IO, [('d', 'r', 6, 2, 9, 5), ('d', 'r', 5, 6, 10, 11), ('d', 'r', 5, 12, 7, 14), ('d', 'r', 8, 12, 10, 14),
                             ('m', 'R', 6, 2, 9, 5), ('m', 'R', 5, 6, 10, 11), ('a', 'l', 3, 6, 3, 13), ('a', 'l', 12, 6, 12, 13)])
    make('needle_rain', *IO, [('m', 'l', x, 1 + (x * 5) % 4, x - 1, 6 + (x * 5) % 4) for x in range(3, 15, 3)] + [('a', 'l', 2, 13, 13, 13), ('w', 'p', 7, 12)])
    make('domino', *IO, [('m', 'r', 2, 6, 4, 13), ('m', 'g', [(6, 6), (8, 5), (10, 12), (8, 13)]), ('m', 'g', [(10, 9), (12, 7), (14, 13), (12, 14)]), ('a', 'p', 3, 9), ('a', 'p', 8, 9)])
    make('stillness', *IO, [('m', 'e', 2, 2, 13, 13), ('d', 'E', 4, 4, 11, 11), ('a', 'l', 7, 4, 7, 11), ('a', 'l', 8, 4, 8, 11), ('w', 'p', 7, 7)])
    make('karmas_ledger', *IO, [('m', 'r', 3, 3, 12, 13), ('d', 'l', 7, 3, 7, 13), ('a', 'l', 4, 5, 6, 5), ('a', 'l', 4, 7, 6, 7), ('a', 'l', 9, 5, 11, 5),
                                ('a', 'l', 9, 7, 11, 7), ('w', 'l', 9, 10, 11, 10), ('w', 'p', 5, 10)])
    make('the_law_itself', *IO, [('m', 'e', 1, 1, 14, 14), ('m', 'e', 4, 4, 11, 11), ('a', 'l', 7, 1, 7, 14), ('a', 'l', 1, 7, 14, 7),
                                 ('w', 'E', 6, 6, 9, 9), ('d', 'p', 7, 7)])


def omen_mark():
    """The omen mark (16 x 32: the empty mark on top, the full one under it): a thin black-blue diamond with a drop."""
    im = Image.new('RGBA', (16, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    for oy, fill in ((0, None), (16, (90, 130, 255, 255))):
        d.polygon([(8, 1 + oy), (14, 7 + oy), (8, 13 + oy), (2, 7 + oy)], outline=(10, 10, 20, 255), fill=(16, 18, 34, 220))
        d.polygon([(8, 3 + oy), (12, 7 + oy), (8, 11 + oy), (4, 7 + oy)], outline=(110, 150, 255, 255), fill=fill)
        d.ellipse([7, 6 + oy, 9, 9 + oy], fill=(220, 230, 255, 255) if fill else (110, 150, 255, 255))
    os.makedirs(os.path.join(ROOT, 'gui'), exist_ok=True)
    im.save(os.path.join(ROOT, 'gui', 'omen_mark.png'))
    return im


def afterimage_skin():
    """The Afterimage: a player's shape in near-black, every edge rimmed in deep blue, no face but two pale points."""
    sk = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    rnd = random.Random(9)
    # the base boxes of the 64x64 player layout: (u, v, width, height, depth)
    boxes = [(0, 0, 8, 8, 8), (16, 16, 8, 12, 4), (40, 16, 4, 12, 4), (32, 48, 4, 12, 4), (0, 16, 4, 12, 4), (16, 48, 4, 12, 4)]
    for u, v, w, h, d in boxes:
        faces = [(u + d, v, w, d), (u + d + w, v, w, d), (u, v + d, d, h), (u + d, v + d, w, h), (u + d + w, v + d, d, h), (u + 2 * d + w, v + d, w, h)]
        for fx, fy, fw, fh in faces:
            for x in range(fx, fx + fw):
                for y in range(fy, fy + fh):
                    edge = x in (fx, fx + fw - 1) or y in (fy, fy + fh - 1)
                    k = rnd.randint(0, 6)
                    sk.putpixel((x, y), (30, 60, 160, 235) if edge else (6 + k, 7 + k, 16 + k * 2, 225))
    for x, y in ((10, 12), (13, 12)): sk.putpixel((x, y), (200, 220, 255, 255))   # two pale points for eyes
    os.makedirs(os.path.join(ROOT, 'entity'), exist_ok=True)
    sk.save(os.path.join(ROOT, 'entity', 'omen_afterimage.png'))
    return sk


def models():
    MD = os.path.join(HERE, '..', 'src/main/resources/assets/iron_spell_grimoire/models/item')
    for suffix in ('', '_model', '_open'):
        src = json.load(open(os.path.join(MD, 'architect_grimoire%s.json' % suffix)))
        json.dump(json.loads(json.dumps(src).replace('architect_grimoire', 'omen_grimoire')), open(os.path.join(MD, 'omen_grimoire%s.json' % suffix), 'w'), indent=1)


if __name__ == '__main__':
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    c = cover(); C.decorate(c, 'omen'); c.save('omen_grimoire_front')
    b = Image.open(os.path.join(M, 'omen_grimoire_front.png')).convert('RGBA')
    b = Image.blend(b, Image.new('RGBA', b.size, tuple(int(v) for v in b.resize((1, 1), Image.BOX).getpixel((0, 0)))), 0.55)
    b.save(os.path.join(M, 'omen_grimoire_back.png'))
    shift(Image.open(os.path.join(M, 'dark_grimoire_leather.png')), 0.62, 1.0, 0.7).save(os.path.join(M, 'omen_grimoire_leather.png'))
    t = Image.open(os.path.join(M, 'spatial_grimoire_trim.png')).convert('RGBA'); px = t.load()
    for y in range(t.height):
        for x in range(t.width):
            r, g, bb, a = px[x, y]
            k = 0.45 + 0.6 * min(1.2, (r + g + bb) / 450.0)
            px[x, y] = (min(255, int(150 * k)), min(255, int(180 * k)), min(255, int(240 * k)), a)
    t.save(os.path.join(M, 'omen_grimoire_trim.png'))
    icon().save(os.path.join(ITEM, 'omen_grimoire.png'))
    spell_icons(); models()
    mark = omen_mark(); skin = afterimage_skin()
    if preview:
        os.makedirs(preview, exist_ok=True)
        sheet = Image.new('RGBA', (256 + 200 + 8 * 72 + 60, 400), (30, 30, 36, 255))
        sheet.alpha_composite(Image.open(os.path.join(M, 'omen_grimoire_front.png')).convert('RGBA'), (10, 8))
        sheet.alpha_composite(icon().resize((160, 160), Image.NEAREST), (280, 8))
        sheet.alpha_composite(mark.resize((80, 160), Image.NEAREST), (300, 200))
        for i, n in enumerate(['ill_fated_path', 'laden_token', 'afterimage', 'needle_rain', 'domino', 'stillness', 'karmas_ledger', 'the_law_itself']):
            sheet.alpha_composite(Image.open(os.path.join(ROOT, 'gui', 'spell_icons', n + '.png')).convert('RGBA'), (470 + (i % 4) * 72, 20 + (i // 4) * 80))
        d = ImageDraw.Draw(sheet)
        d.text((470, 190), "pages: path, token, afterimage, needle rain / domino, stillness, ledger, the law itself", fill=(220, 220, 230))
        d.text((300, 370), "omen mark (empty / full)", fill=(220, 220, 230))
        sheet.save(os.path.join(preview, 'omen_look.png'))
    print('omen assets written')
