"""The Meridian Grimoire's look (original designs): its cover, leather and trim for the floating book, its inventory
icon, and a preview of the sundial HUD.
  Cover: dawn-coloured leather, darkest at the foot; a half sun rising behind the medallion with long and short rays;
  sundial hour lines fanning down across the lower half; the gnomon's long shadow falling from the medallion.
python3 tools/meridian_assets.py [preview_dir]"""
import colorsys, math, os, sys
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import covers as C
from icons import make
import json

ROOT = os.path.join(HERE, '..', 'src/main/resources/assets/iron_spell_grimoire/textures/item')
M = os.path.join(ROOT, 'spell_book_models')
GOLD = (255, 222, 140)
C.ACC['meridian'] = GOLD
C.GLOW['meridian'] = (255, 200, 80)


def meridian():
    c = C.Cv((150, 96, 30), (64, 30, 12), 61)   # pale gold at the head, burnt amber at the foot
    l, d = c.layer()
    X0, Y0, R0 = C.X0, C.Y0, C.R0
    # the rising sun's rays above the medallion: long and short, the upper half only
    for i in range(25):
        a = math.pi + i * math.pi / 24
        long_ = i % 2 == 0
        r0, r1 = R0 + 6, R0 + (70 if long_ else 40)
        px, py = -math.sin(a), math.cos(a)
        C.poly(d, [(X0 + math.cos(a) * r0 + px * 5, Y0 + math.sin(a) * r0 + py * 5), (X0 + math.cos(a) * r1, Y0 + math.sin(a) * r1),
                   (X0 + math.cos(a) * r0 - px * 5, Y0 + math.sin(a) * r0 - py * 5)], fill=(255, 226, 140, 255 if long_ else 190))
    c.put(l, (255, 190, 70), 9, 0.8)
    # the sundial: hour lines fanning down from the medallion across the lower half, numerals as small notches
    l, d = c.layer()
    for k in range(13):
        a = math.radians(15 + k * 12.5)
        x1, y1 = X0 + math.cos(a) * (R0 + 96), Y0 + math.sin(a) * (R0 + 96)
        C.line(d, [(X0 + math.cos(a) * (R0 + 10), Y0 + math.sin(a) * (R0 + 10)), (x1, y1)], fill=(60, 30, 10, 200), width=2)
        d.rectangle([(x1 - 3) * C.S, (y1 - 3) * C.S, (x1 + 3) * C.S, (y1 + 3) * C.S], fill=(255, 222, 140, 230))
    # the gnomon's shadow: a long dark wedge falling to the lower right
    a = math.radians(62)
    C.poly(d, [(X0 + math.cos(a - 0.08) * (R0 + 4), Y0 + math.sin(a - 0.08) * (R0 + 4)), (X0 + math.cos(a) * (R0 + 112), Y0 + math.sin(a) * (R0 + 112)),
               (X0 + math.cos(a + 0.08) * (R0 + 4), Y0 + math.sin(a + 0.08) * (R0 + 4))], fill=(30, 12, 4, 170))
    c.put(l)
    l, d = c.layer(); C.frame(d, GOLD + (255,), 16, 3, 6, True); c.put(l)
    return c


def shift(im, hue, sm, vm):
    im = im.convert('RGBA'); px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0: continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            nr, ng, nb = colorsys.hsv_to_rgb(hue, min(1, max(s, 0.1) * sm), min(1, v * vm))
            px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    return im


def icon():
    """The inventory icon: the shared book shape in dawn gold, a white-gold sun on its cover."""
    im = shift(Image.open(os.path.join(ROOT, 'dark_grimoire.png')), 0.10, 1.15, 1.05)
    px = im.load()
    for (x, y) in [(7, 5), (8, 5), (6, 6), (7, 6), (8, 6), (9, 6), (7, 7), (8, 7)]: px[x, y] = (255, 246, 200, 255)
    for (x, y) in [(7, 4), (5, 6), (10, 6), (8, 8)]: px[x, y] = (255, 200, 80, 255)
    return im


def hud(out):
    """A preview of the sundial HUD (gui scale 1, drawn big): the dial, the gnomon's shadow at today's hour, the meter."""
    S = 6
    for hour, name in ((6, 'dawn'), (12, 'noon'), (17, 'dusk'), (0, 'night')):
        im = Image.new('RGBA', (52 * S, 30 * S), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
        cx, cy, R = 26 * S, 26 * S, 22 * S
        day = 0 < hour < 18 and hour >= 6
        d.pieslice([cx - R, cy - R, cx + R, cy + R], 180, 360, fill=(40, 26, 14, 220) if day else (14, 14, 26, 220), outline=(255, 222, 140, 255), width=S)
        for k in range(13):
            a = math.radians(180 + k * 15)
            d.line([cx + math.cos(a) * R * 0.55, cy + math.sin(a) * R * 0.55, cx + math.cos(a) * R * 0.9, cy + math.sin(a) * R * 0.9], fill=(255, 222, 140, 160), width=S // 2)
        sun = max(0.0, math.sin(math.pi * (hour - 6) / 12)) if day else 0.0
        if day:   # the shadow points away from the sun: a dark hand
            d.line([cx, cy, cx + math.cos(math.radians(180 + (18 - hour) * 15)) * R * 0.85, cy + math.sin(math.radians(180 + (18 - hour) * 15)) * R * 0.85],
                   fill=(10, 6, 4, 255), width=2 * S)
        d.ellipse([cx - 3 * S, cy - 3 * S, cx + 3 * S, cy + 3 * S], fill=(255, 240, 180, 255) if day else (120, 120, 160, 255))
        # the meter underneath the dial: sun power 0-100%
        d.rectangle([4 * S, 27 * S, 48 * S, 29 * S], fill=(20, 14, 8, 230))
        d.rectangle([4 * S, 27 * S, 4 * S + int(44 * S * sun), 29 * S], fill=(255, 210, 90, 255))
        im.save(os.path.join(out, 'meridian_hud_%s.png' % name))


# ------------------------------------------------------------------ spell icons (16x16, x4)
DAY = ((150, 96, 30), (40, 20, 6), (255, 214, 110), (255, 250, 220))     # gold on amber, white-hot accent
NIGHT = ((40, 44, 80), (8, 8, 20), (255, 170, 80), (255, 230, 170))      # ember on night blue
NOON = ((200, 150, 60), (60, 30, 8), (255, 250, 220), (255, 200, 80))   # white on gold


def spell_icons():
    sun = [('a', 'E', 5, 5, 10, 10), ('m', 'p', 7, 2), ('m', 'p', 8, 2), ('m', 'p', 7, 13), ('m', 'p', 8, 13), ('m', 'p', 2, 7), ('m', 'p', 2, 8),
           ('m', 'p', 13, 7), ('m', 'p', 13, 8), ('m', 'p', 4, 4), ('m', 'p', 11, 4), ('m', 'p', 4, 11), ('m', 'p', 11, 11)]
    make('hearth_star', *DAY, sun + [('w', 'p', 7, 7)])
    make('haughty_flare', *DAY, [('m', 'E', 3, 3, 12, 12), ('a', 'E', 5, 5, 10, 10), ('w', 'E', 6, 6, 9, 9), ('m', 'l', 0, 7, 2, 7), ('m', 'l', 13, 8, 15, 8),
                                 ('m', 'l', 7, 0, 7, 2), ('m', 'l', 8, 13, 8, 15), ('m', 'l', 1, 1, 3, 3), ('m', 'l', 12, 12, 14, 14)])
    make('call_dayreaver', *DAY, [('d', 'l', 3, 14, 11, 3), ('m', 'l', 4, 14, 12, 3), ('m', 'g', [(9, 3), (14, 4), (15, 8), (13, 11), (10, 6)]), ('a', 'p', 11, 6), ('w', 'p', 12, 5)])
    make('noonwave', *DAY, [('m', 'a', 1, 1, 14, 14, 300, 60), ('m', 'a', 3, 3, 12, 12, 300, 60), ('a', 'a', 5, 5, 10, 10, 300, 60), ('w', 'p', 7, 7), ('m', 'l', 2, 7, 5, 7)])
    make('lamplight', *NIGHT, [('m', 'r', 6, 6, 9, 11), ('a', 'r', 5, 5, 10, 5), ('a', 'r', 5, 12, 10, 12), ('w', 'p', 7, 8), ('w', 'p', 8, 8), ('a', 'l', 7, 2, 8, 4)])
    make('warm_hands', *NIGHT, [('m', 'g', [(3, 10), (7, 6), (8, 6), (12, 10), (11, 12), (4, 12)]), ('a', 'E', 6, 3, 9, 6), ('w', 'p', 7, 4)])
    make('last_coal', *NIGHT, [('m', 'E', 4, 6, 11, 12), ('a', 'r', 6, 8, 9, 10), ('w', 'p', 7, 9), ('a', 'R', 2, 3, 13, 14)])
    make('zenith_unbound', *NOON, [('a', 'E', 4, 4, 11, 11), ('w', 'E', 6, 6, 9, 9)] + [('m', 'l', 7.5 + math.cos(a) * 5, 7.5 + math.sin(a) * 5, 7.5 + math.cos(a) * 7.4, 7.5 + math.sin(a) * 7.4)
                                                                                       for a in [k * math.pi / 6 for k in range(12)]])


def models():
    MD = os.path.join(HERE, '..', 'src/main/resources/assets/iron_spell_grimoire/models/item')
    for suffix in ('', '_model', '_open'):
        src = json.load(open(os.path.join(MD, 'architect_grimoire%s.json' % suffix)))
        txt = json.dumps(src).replace('architect_grimoire', 'meridian_grimoire')
        json.dump(json.loads(txt), open(os.path.join(MD, 'meridian_grimoire%s.json' % suffix), 'w'), indent=1)


if __name__ == '__main__':
    preview = sys.argv[1] if len(sys.argv) > 1 else None
    c = meridian(); C.decorate(c, 'meridian'); c.save('meridian_grimoire_front')
    b = Image.open(os.path.join(M, 'meridian_grimoire_front.png')).convert('RGBA')
    b = Image.blend(b, Image.new('RGBA', b.size, tuple(int(v) for v in b.resize((1, 1), Image.BOX).getpixel((0, 0)))), 0.55)
    b.save(os.path.join(M, 'meridian_grimoire_back.png'))
    shift(Image.open(os.path.join(M, 'dark_grimoire_leather.png')), 0.115, 1.0, 1.2).save(os.path.join(M, 'meridian_grimoire_leather.png'))
    t = Image.open(os.path.join(M, 'spatial_grimoire_trim.png')).convert('RGBA'); px = t.load()
    for y in range(t.height):
        for x in range(t.width):
            r, g, bb, a = px[x, y]
            k = 0.45 + 0.6 * min(1.2, (r + g + bb) / 450.0)
            px[x, y] = (min(255, int(GOLD[0] * k)), min(255, int(GOLD[1] * k)), min(255, int(GOLD[2] * k)), a)
    t.save(os.path.join(M, 'meridian_grimoire_trim.png'))
    icon().save(os.path.join(ROOT, 'meridian_grimoire.png'))
    spell_icons(); models()
    if preview:
        os.makedirs(preview, exist_ok=True); hud(preview)
        # a preview sheet: cover, icon (big), the four HUD states
        sheet = Image.new('RGBA', (256 + 220 + 2 * 330 + 40, 400), (30, 30, 36, 255))
        sheet.alpha_composite(Image.open(os.path.join(M, 'meridian_grimoire_front.png')).convert('RGBA'), (10, 8))
        sheet.alpha_composite(icon().resize((192, 192), Image.NEAREST), (280, 8))
        d = ImageDraw.Draw(sheet)
        for i, n in enumerate(('dawn', 'noon', 'dusk', 'night')):
            x, y = 490 + (i % 2) * 330, 20 + (i // 2) * 200
            sheet.alpha_composite(Image.open(os.path.join(preview, 'meridian_hud_%s.png' % n)), (x, y))
            d.text((x + 4, y + 184), n, fill=(235, 228, 210))
        sheet.save(os.path.join(preview, 'meridian_look.png'))
    print('meridian assets written')
