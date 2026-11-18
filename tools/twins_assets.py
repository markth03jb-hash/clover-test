"""The Masked Twins Grimoire (two masks that share one soul; every name and look here is original): spell icons, the
cover, leather, trim, inventory icon, book models, the curios tag and the text.
  Twins - a cover split down the middle, warm gold leather on the left and night-blue on the right, silver trim; on it
          one face split in two - Sol's golden sun half with its rays, Nyx's silver moon half hugged by a crescent - and
          a black eclipse at the brow where they meet. (The masks themselves: tools/blender/masked_twins.py.)
python3 tools/twins_assets.py"""
import json, math, os, sys
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
from icons import make
from _cover_helpers import *   # SC, W, H, Pp, poly, circ, ell, arc, frame, glow, shift, tint_trim, R, A_, TEX, M

# ------------------------------------------------------------------ spell icons (16x16)
GS = ((150, 104, 30), (24, 18, 30), (255, 214, 110), (214, 226, 255))   # gold on night, silver accent
NS = ((60, 70, 130), (12, 12, 30), (214, 226, 255), (255, 200, 90))      # silver on night, gold accent
# Swap: a sun half and a moon half turning round each other
make('twin_swap', *GS, [('m', 'g', [(7, 2), (7, 13), (3, 11), (2, 7), (3, 4)]), ('a', 'a', 7, 2, 14, 13, 270, 90), ('a', 'a', 9, 4, 13, 11, 270, 90),
                        ('m', 'p', 1, 7), ('m', 'p', 2, 3), ('m', 'p', 2, 12), ('w', 'l', 4, 0, 9, 0), ('w', 'p', 8, 1), ('w', 'l', 6, 15, 11, 15), ('w', 'p', 7, 14)])
# Twin Arts: four sparks round a split ring
make('twin_arts', *GS, [('m', 'a', 3, 3, 12, 12, 90, 270), ('a', 'a', 3, 3, 12, 12, 270, 90), ('m', 'l', 7, 0, 7, 2), ('a', 'l', 8, 13, 8, 15),
                        ('m', 'l', 0, 7, 2, 7), ('a', 'l', 13, 8, 15, 8), ('w', 'p', 7, 7), ('w', 'p', 8, 8), ('m', 'p', 7, 8), ('a', 'p', 8, 7)])
# Twin Strike: a gold cut and a silver cut crossing
make('twin_strike', *GS, [('m', 'l', 2, 13, 13, 2), ('m', 'l', 2, 12, 12, 2), ('a', 'l', 2, 2, 13, 13), ('a', 'l', 3, 2, 13, 12), ('w', 'p', 7, 7), ('w', 'p', 8, 8)])
# Eclipse Union: a black disc, its rim half gold, half silver
make('eclipse_union', *NS, [('d', 'E', 4, 4, 11, 11), ('a', 'a', 3, 3, 12, 12, 90, 270), ('m', 'a', 3, 3, 12, 12, 270, 90), ('a', 'l', 1, 7, 2, 7),
                            ('m', 'l', 13, 8, 14, 8), ('a', 'p', 3, 2), ('m', 'p', 12, 13), ('w', 'p', 6, 6)])
# The Masks: a face, half sun, half moon
make('the_masks', *NS, [('a', 'g', [(7, 2), (7, 14), (4, 12), (3, 8), (4, 4)]), ('m', 'g', [(8, 2), (8, 14), (11, 12), (12, 8), (11, 4)]),
                        ('d', 'l', 4, 7, 6, 7), ('d', 'l', 9, 7, 11, 7), ('d', 'l', 6, 11, 9, 11), ('a', 'p', 2, 4), ('a', 'p', 1, 8), ('m', 'p', 13, 4)])

# High Noon: a blazing sun, rays all round
make('high_noon', *GS, [('m', 'E', 4, 4, 11, 11), ('w', 'E', 6, 6, 9, 9), ('m', 'l', 7, 0, 8, 0), ('m', 'l', 7, 15, 8, 15), ('m', 'l', 0, 7, 0, 8), ('m', 'l', 15, 7, 15, 8),
                        ('a', 'l', 2, 2, 3, 3), ('a', 'l', 13, 2, 12, 3), ('a', 'l', 2, 13, 3, 12), ('a', 'l', 13, 13, 12, 12)])
# Eventide: a crescent over a slow ring of stars
make('eventide', *NS, [('m', 'a', 3, 2, 12, 11, 100, 280), ('m', 'a', 4, 2, 12, 10, 100, 280), ('d', 'E', 6, 3, 13, 10), ('a', 'p', 11, 4), ('a', 'p', 13, 8),
                       ('a', 'p', 2, 13), ('a', 'p', 7, 14), ('a', 'p', 12, 13), ('w', 'p', 5, 6)])


# ------------------------------------------------------------------ cover
GOLD, GOLD_SH, GOLD_HI = (232, 178, 64, 255), (150, 98, 26, 255), (255, 230, 150, 255)
SILV, SILV_SH, SILV_HI = (196, 206, 230, 255), (104, 112, 160, 255), (240, 246, 255, 255)
INK, VOID = (10, 8, 18, 255), (4, 4, 10, 255)


def twins_draw(im, d, cx, cy, k, big):
    im = glow(im, cx, cy, ((84 * k, 22), (58 * k, 40)), (255, 210, 120)); d = ImageDraw.Draw(im)
    r = 52 * k
    # Sol's rays, all down the left
    for i in range(9):
        a = math.pi / 2 + math.pi * (i + 0.5) / 9
        L = (24 if i % 2 == 0 else 15) * k
        b0, b1 = a - 0.11, a + 0.11
        poly(d, [(cx + math.cos(b0) * r, cy - math.sin(b0) * r), (cx + math.cos(a) * (r + L), cy - math.sin(a) * (r + L)), (cx + math.cos(b1) * r, cy - math.sin(b1) * r)], GOLD, INK, 2)
    # Nyx's crescent, hugging the right
    pts = []
    for i in range(25):
        p = i / 24; a = -math.pi / 2 - 0.25 + (math.pi + 0.5) * p
        rr = r + 6 * k + 16 * k * math.sin(p * math.pi)
        pts.append((cx + math.cos(a) * rr * -1 * -1, cy + math.sin(a) * rr))
    inner = [(cx + math.cos(-math.pi / 2 - 0.25 + (math.pi + 0.5) * (i / 24)) * (r + 4 * k), cy + math.sin(-math.pi / 2 - 0.25 + (math.pi + 0.5) * (i / 24)) * (r + 4 * k)) for i in range(24, -1, -1)]
    poly(d, pts + inner, SILV, INK, 2)
    # the face: a gold half and a silver half
    for (a0, a1, col, sh) in ((90, 270, GOLD, GOLD_SH), (-90, 90, SILV, SILV_SH)):
        d.pieslice([Pp(cx - r - 3, cy - r - 3), Pp(cx + r + 3, cy + r + 3)], a0, a1, fill=INK)
        d.pieslice([Pp(cx - r, cy - r), Pp(cx + r, cy + r)], a0, a1, fill=col)
        d.pieslice([Pp(cx - r, cy - r), Pp(cx + r, cy + r)], a0 + 20, a1 - 20, fill=None, outline=sh, width=int(3 * SC))
    d.line([Pp(cx, cy - r - 2), Pp(cx, cy + r + 2)], fill=VOID, width=int(4 * k * SC))
    # eyes: calm, closed lids; Sol's smile, Nyx's small mouth
    for sx, col in ((-1, GOLD_SH), (1, SILV_SH)):
        d.ellipse([Pp(cx + sx * 20 * k - 9 * k, cy - 6 * k - 3 * k), Pp(cx + sx * 20 * k + 9 * k, cy - 6 * k + 3 * k)], fill=INK)
        d.arc([Pp(cx + sx * 20 * k - 11 * k, cy - 20 * k), Pp(cx + sx * 20 * k + 11 * k, cy - 8 * k)], 200, 340, fill=col, width=int(3 * k * SC))
    d.arc([Pp(cx - 16 * k, cy + 12 * k), Pp(cx + 2 * k, cy + 26 * k)], 20, 170, fill=GOLD_SH, width=int(3 * k * SC))
    d.line([Pp(cx + 4 * k, cy + 24 * k), Pp(cx + 12 * k, cy + 24 * k)], fill=SILV_SH, width=int(3 * k * SC))
    # the stones: a sunstone on the gold cheek, a moonstone tear under the silver eye
    circ(d, cx - 30 * k, cy + 14 * k, 6 * k, fill=(255, 120, 40, 255), outline=INK, w=2)
    circ(d, cx + 22 * k, cy + 6 * k, 5 * k, fill=(120, 170, 255, 255), outline=INK, w=2)
    # the eclipse at the brow: black, ringed half gold, half silver
    ey = cy - 34 * k
    circ(d, cx, ey, 13 * k, fill=INK)
    arc(d, cx, ey, 13 * k, 90, 270, GOLD_HI, 3 * k)
    arc(d, cx, ey, 13 * k, -90, 90, SILV_HI, 3 * k)
    circ(d, cx, ey, 9 * k, fill=VOID)
    return im


def cover(big):
    LEA, LEA_SH = (40, 30, 62, 255), (24, 18, 40, 255)
    im = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(im)
    frame(d, INK, LEA, LEA_SH, SILV, SILV_SH, SILV_HI, (255, 200, 90, 255))
    # the gold half of the leather (the left), its own shadowed edge
    d.rectangle([Pp(19, 19), Pp(128, 365)], fill=(120, 76, 22, 255))
    d.rectangle([Pp(19, 330), Pp(128, 365)], fill=(84, 52, 14, 255))
    d.line([Pp(128, 19), Pp(128, 365)], fill=INK, width=int(3 * SC))
    cy = 176 if big else 196
    im = twins_draw(im, d, 128, cy, 1.0 if big else 0.62, big)
    return im.resize((256, 384), Image.LANCZOS)


name = 'twins_grimoire'
cover(True).save(os.path.join(M, f'{name}_front.png'))
cover(False).save(os.path.join(M, f'{name}_back.png'))
shift(Image.open(os.path.join(M, 'dark_grimoire_leather.png')), 0.68, 0.55, 0.85).save(os.path.join(M, f'{name}_leather.png'))
tint_trim(name, (196, 204, 226))
ic = shift(Image.open(os.path.join(TEX, 'dark_grimoire.png')), 0.68, 0.6, 0.85); px = ic.load()
for (x, y, c) in ((6, 6, (255, 214, 110)), (6, 7, (255, 214, 110)), (6, 8, (255, 214, 110)), (7, 5, (255, 214, 110)), (7, 9, (255, 214, 110)),
                  (8, 5, (214, 226, 255)), (8, 9, (214, 226, 255)), (9, 6, (214, 226, 255)), (9, 7, (214, 226, 255)), (9, 8, (214, 226, 255)), (7, 7, (10, 8, 18)), (8, 7, (10, 8, 18))):
    if px[x, y][3]: px[x, y] = c + (255,)
ic.save(os.path.join(TEX, f'{name}.png'))
# the mask item's inventory picture (never in an inventory; for completeness)
MD = os.path.join(A_, 'models/item')
CT = os.path.join(R, 'data/curios/tags/items/spellbook.json')
c = json.load(open(CT))
for suf in ('', '_model', '_open'):
    s = open(os.path.join(MD, f'light_grimoire{suf}.json')).read().replace('light_grimoire', name)
    open(os.path.join(MD, f'{name}{suf}.json'), 'w').write(s)
if f'iron_spell_grimoire:{name}' not in c['values']: c['values'].append(f'iron_spell_grimoire:{name}')
json.dump(c, open(CT, 'w'), indent=2)

# ------------------------------------------------------------------ text
LP = os.path.join(A_, 'lang/en_us.json')
lang = json.load(open(LP))
lang['item.iron_spell_grimoire.twins_grimoire'] = 'Masked Twins Grimoire'
lang['item.iron_spell_grimoire.twin_mask'] = 'Twin Mask'
lang['entity.iron_spell_grimoire.twin_warden'] = 'Twin Warden'
lang['ui.iron_spell_grimoire.twins_grimoire_only'] = 'Only the Masked Twins Grimoire can cast this.'
lang['tooltip.iron_spell_grimoire.twins_grimoire.passive'] = ('Passive - two masks, one soul: Sol, the sun twin (fire, fierce up close), and Nyx, the moon twin (frost and void, '
    'quick and slippery). Wear one at a time on your face - tap G to swap, hold G for the wheel of your four chosen arts. Each twin levels on its own (to 50) and learns '
    'eight arts; choose four of each for the wheel. The twin you are not wearing rests at your hip and charges Resonance as you fight - swap and it bursts out. '
    'Sol learns faster by day, Nyx by night, and a twin far ahead of the other learns slowly. Set Sunstones and Moonstones in the masks; '
    'raise both and they learn to fight together: Twin Strike, Mirror Swap, and - after the Twin Wardens\' trial - Eclipse Union. Every art masters with use (Perfected at 250 casts), each socket takes one of three cuts, '
    'from level 15 the resting twin\'s Echo fights beside you, and at 40 each twin has its own ultimate: High Noon and Eventide.')
lang['tooltip.iron_spell_grimoire.twins_grimoire.desc'] = 'Seven pages, two faces, one soul.'
lang['tooltip.iron_spell_grimoire.twins_grimoire.lore'] = 'One of them is always watching.'
for k, (title, guide) in {
 'twin_swap': ('Swap', 'Trade masks: the resting twin takes your face and bursts out with all the Resonance it stored (Sol: a sun flare round you; Nyx: a crescent wave ahead), and you get a short Rush (Sol: harder hits; Nyx: faster feet). Sneak while you swap, once the twins have bonded deep enough, to step back to where you last swapped. Tapping G does the same.'),
 'twin_arts': ('Twin Arts', 'Cast the art last chosen on the G wheel - hold G and point at one of the four arts of the twin you wear.'),
 'twin_strike': ('Twin Strike', 'Both twins at level 10: the gold mask and the silver mask strike at once - two cuts on everything in front of you, and the resting twin\'s Resonance jumps.'),
 'eclipse_union': ('Eclipse Union', 'Both twins at level 40 and the Twin Wardens beaten: the masks fuse for 20 s - every art of both twins on the wheel, no art cooldowns, swaps twice as fast, and you hit harder and move faster.'),
 'high_noon': ('High Noon', 'Sol\'s ultimate (Sol worn, level 40): for 10 s a blazing aura scorches every foe near you each second, Sol\'s art cooldowns are cleared and halved while it lasts, and every hit of Sol\'s sets foes burning.'),
 'eventide': ('Eventide', 'Nyx\'s ultimate (Nyx worn, level 40): for 8 s every foe near you crawls at half speed, you run 40% faster, and the next three blows aimed at you miss.'),
 'the_masks': ('The Masks', 'Open the Masks: each twin\'s level and arts (choose four for the wheel), the bond between them, the stones set in each mask, your titles and a guide.'),
}.items():
    lang[f'spell.iron_spell_grimoire.{k}'] = f'Twins: {title}'
    lang[f'spell.iron_spell_grimoire.{k}.guide'] = guide
json.dump(lang, open(LP, 'w'), indent=2, ensure_ascii=False)
print('twins assets written')
