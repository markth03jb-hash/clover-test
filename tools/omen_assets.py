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
    c = C.Cv((14, 18, 40), (4, 4, 12), 71, grain=8)
    l, d = c.layer()
    X0, Y0, R0 = C.X0, C.Y0, C.R0
    rnd = random.Random(5)
    # needles of rain, falling slant round the medallion
    for _ in range(70):
        x, y = rnd.uniform(24, 232), rnd.uniform(24, 360)
        if math.hypot(x - X0, y - Y0) < R0 + 12: continue
        C.line(d, [(x, y), (x - 3, y + 14)], fill=(110, 150, 255, rnd.randint(90, 200)), width=1)
    # footprints walking up from the foot to the medallion, stopping short
    for k in range(7):
        side = -1 if k % 2 else 1
        fx, fy = X0 + side * 9 + (k % 3 - 1) * 2, 352 - k * 22
        if fy < Y0 + R0 + 14: break
        d.ellipse([(fx - 4) * C.S, (fy - 7) * C.S, (fx + 4) * C.S, (fy + 5) * C.S], fill=(150, 180, 240, 150 - k * 12))
        d.ellipse([(fx - 3) * C.S, (fy - 12) * C.S, (fx + 3) * C.S, (fy - 8) * C.S], fill=(150, 180, 240, 140 - k * 12))
    # one hairline crack off the rim
    C.crack(d, X0 + R0 * 0.7, Y0 + R0 * 0.7, math.radians(40), 90, (120, 160, 255, 220), 2, rnd, 2)
    c.put(l, (60, 100, 255), 6, 0.6)
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
    """The Afterimage: a player's shape in black, its edges outlined in deep blue, no face but two pale points."""
    sk = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); d = ImageDraw.Draw(sk)
    # the base layer regions (head, body, arms, legs) of the 64x64 skin layout, filled black with a blue rim
    regions = [(0, 0, 32, 16), (16, 16, 40, 32), (40, 16, 56, 32), (0, 16, 16, 32), (32, 48, 48, 64), (16, 48, 32, 64)]
    rnd = random.Random(9)
    for x0, y0, x1, y1 in regions:
        for x in range(x0, x1):
            for y in range(y0, y1):
                edge = (x - x0) % 4 in (0,) or (y - y0) % 4 == 0
                v = rnd.randint(0, 14)
                sk.putpixel((x, y), (8 + v, 10 + v, 24 + v * 2, 230) if not edge else (40, 70, 170, 230))
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
