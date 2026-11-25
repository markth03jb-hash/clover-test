"""The Grimoire of the Bound Demon's look (original designs, black, crimson and salt-white): cover, leather, trim, icon,
book models, spell icons and the G-wheel's icons.
  Cover: one great wing rising behind the medallion - black feathers paling to salt-white at the roots - a black katana
  laid across the foot with a crimson seam, a demon's red eye under the medallion, salt grains drifting.
python3 tools/bound_assets.py [preview_dir]"""
import math, os, random, sys
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import covers as C
from icons import make
import trio_assets as T

C.ACC['bound'] = (230, 70, 80); C.GLOW['bound'] = (220, 30, 50)


def cover():
    c = C.Cv((22, 12, 16), (4, 2, 4), 83, grain=8)
    X0, Y0, R0 = C.X0, C.Y0, C.R0
    rnd = random.Random(21)
    l, d = c.layer()
    for _ in range(60):   # salt grains
        x, y = rnd.uniform(24, 232), rnd.uniform(24, 330)
        T.dot(d, x, y, rnd.choice([0.7, 1.0, 1.4]), (235, 235, 240, rnd.randint(80, 200)))
    c.put(l, (255, 255, 255), 2, 0.3)
    # one great wing rising behind the medallion, on its left: black tips, salt-white roots
    l, d = c.layer()
    for i in range(12):
        a = math.radians(168 + i * 9.5)
        ln = 34 + 34 * math.sin(math.pi * (i + 1) / 13)
        bx, by = X0 + math.cos(a) * (R0 - 4), Y0 + math.sin(a) * (R0 - 4)
        tx, ty = X0 + math.cos(a) * (R0 + ln), Y0 + math.sin(a) * (R0 + ln)
        nx, ny = -math.sin(a), math.cos(a)
        w = 5 + 2 * math.sin(math.pi * i / 11)
        mx, my = bx + (tx - bx) * 0.5, by + (ty - by) * 0.5
        C.poly(d, [(bx + nx * 3, by + ny * 3), (mx + nx * w, my + ny * w), (mx - nx * w, my - ny * w), (bx - nx * 3, by - ny * 3)], fill=(228, 228, 236, 235))
        C.poly(d, [(mx + nx * w, my + ny * w), (tx - (tx - mx) * 0.12 + nx * w * 0.7, ty - (ty - my) * 0.12 + ny * w * 0.7), (tx, ty),
                   (tx - (tx - mx) * 0.12 - nx * w * 0.7, ty - (ty - my) * 0.12 - ny * w * 0.7), (mx - nx * w, my - ny * w)], fill=(16, 10, 14, 255), outline=(230, 70, 80, 200))
    c.put(l, (255, 255, 255), 4, 0.35)
    # the katana across the foot, and the eye
    l, d = c.layer()
    # the katana: grip low left, a gentle curve up to the tip at the right
    pts = [(36 + k * 9.6, 318 - k * 3.2 - 0.05 * k * k) for k in range(21)]
    C.line(d, pts[:5], fill=(150, 26, 36, 255), width=6)
    gx, gy = pts[5]
    C.poly(d, [(gx - 3, gy - 9), (gx + 3, gy - 9), (gx + 3, gy + 9), (gx - 3, gy + 9)], fill=(10, 8, 10, 255), outline=(200, 170, 90, 255))
    C.line(d, pts[5:], fill=(20, 16, 20, 255), width=5)
    C.line(d, pts[6:-1], fill=(230, 40, 60, 255), width=1)
    ex, ey = X0, Y0 + R0 + 26
    d.ellipse([(ex - 16) * C.S, (ey - 6) * C.S, (ex + 16) * C.S, (ey + 6) * C.S], fill=(30, 6, 10, 255), outline=(230, 60, 70, 255), width=C.S * 2)
    T.dot(d, ex, ey, 4, (255, 60, 70, 255)); T.dot(d, ex, ey, 1.6, (10, 0, 0, 255))
    c.put(l, (220, 30, 50), 6, 0.6)
    l, d = c.layer(); C.frame(d, (230, 70, 80, 255), 16, 2, 0, True); c.put(l)
    return c


BO = ((44, 14, 20), (6, 2, 4), (230, 60, 70), (240, 240, 245))


def icons():
    blade = [('a', 'l', 3, 12, 12, 3), ('m', 'l', 2, 13, 4, 11), ('w', 'l', 4, 10, 6, 12)]
    make('demon_draw', *BO, blade + [('m', 'E', 9, 9, 13, 13), ('d', 'p', 11, 11)])
    make('cursed_edge', *BO, [('m', 'l', 1, 12, 14, 3), ('d', 'l', 1, 13, 14, 4), ('a', 'l', 2, 11, 13, 2)])
    make('demons_whisper', *BO, [('m', 'E', 3, 5, 12, 10), ('d', 'E', 6, 6, 9, 9), ('a', 'p', 7, 7), ('w', 'l', 2, 12, 5, 13), ('w', 'l', 10, 13, 13, 12)])
    make('bond_guard', *BO, [('a', 'E', 2, 5, 6, 9), ('a', 'E', 9, 5, 13, 9), ('w', 'l', 6, 7, 9, 7), ('m', 'e', 1, 3, 14, 12)])
    make('demon_flurry', *BO, [('a', 'l', 2, 2, 13, 13), ('a', 'l', 13, 2, 2, 13), ('w', 'l', 7, 1, 7, 14), ('m', 'l', 1, 7, 14, 7)])
    make('sinners_mark', *BO, [('m', 'e', 3, 3, 12, 12), ('a', 'l', 7, 4, 7, 11), ('a', 'l', 4, 7, 11, 7), ('w', 'p', 7, 7)])
    make('salt_lance', *BO, [('w', 'l', 2, 13, 12, 3), ('w', 'l', 10, 3, 12, 3), ('w', 'l', 12, 3, 12, 5), ('m', 'p', 4, 10), ('m', 'p', 7, 8)])
    make('last_seraph', *BO, [('w', 'g', [(7, 4), (1, 2), (3, 7), (1, 10), (6, 9)]), ('d', 'g', [(8, 4), (14, 2), (12, 7), (14, 10), (9, 9)]), ('a', 'E', 6, 5, 9, 11)])
    make('bd_restrain', *BO, [('m', 'E', 4, 4, 11, 11), ('d', 'E', 6, 6, 9, 9), ('a', 'p', 7, 7), ('w', 'l', 2, 2, 13, 13)])
    make('bd_family', *BO, [('w', 'E', 2, 6, 5, 9), ('w', 'E', 6, 4, 9, 7), ('w', 'E', 10, 6, 13, 9), ('a', 'l', 2, 12, 13, 12)])
    make('bd_saltwall', *BO, [('w', 'r', 2, 3, 4, 13), ('w', 'r', 6, 2, 8, 13), ('w', 'r', 10, 3, 12, 13), ('m', 'l', 1, 14, 14, 14)])
    make('bd_wingdash', *BO, [('w', 'g', [(2, 4), (8, 7), (2, 10)]), ('d', 'g', [(5, 5), (13, 7), (5, 9)]), ('a', 'l', 9, 7, 14, 7)])


if __name__ == '__main__':
    pv = sys.argv[1] if len(sys.argv) > 1 else None
    T.BOOKS = {'bound_grimoire': (cover, 'bound', 0.98, (230, 70, 80))}
    for key, (fn, deco, hue, trim) in T.BOOKS.items(): T.book_files(key, fn, deco, hue, trim)
    icons()
    if pv:
        os.makedirs(pv, exist_ok=True)
        sheet = Image.new('RGBA', (290 + 6 * 46, 400), (30, 30, 36, 255))
        sheet.alpha_composite(Image.open(os.path.join(T.M, 'bound_grimoire_front.png')).convert('RGBA'), (10, 8))
        for j, n in enumerate(['demon_draw', 'cursed_edge', 'demons_whisper', 'bond_guard', 'demon_flurry', 'sinners_mark', 'salt_lance', 'last_seraph', 'bd_restrain', 'bd_family', 'bd_saltwall', 'bd_wingdash']):
            sheet.alpha_composite(Image.open(os.path.join(T.ROOT, 'gui', 'spell_icons', n + '.png')).convert('RGBA').resize((40, 40), Image.NEAREST), (280 + (j % 6) * 46, 20 + (j // 6) * 48))
        sheet.alpha_composite(Image.open(os.path.join(T.ITEM, 'bound_grimoire.png')).convert('RGBA').resize((64, 64), Image.NEAREST), (300, 140))
        sheet.save(os.path.join(pv, 'bound_look.png'))
    print('bound assets written')
