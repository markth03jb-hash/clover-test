"""The Shadow Grimoire (raise the slain as your shadows; every look here is original): spell icons, the cover, leather,
trim, inventory icon, models, the curios tag and the text.
  Shadow - black leather, steel-blue trim, a burning blue eye under a crown of blue fire, black smoke curling round it.
python3 tools/shadow_assets.py"""
import json, os, sys
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
from icons import make
from _cover_helpers import *   # SC, W, H, Pp, poly, circ, ell, arc, frame, glow, shift, tint_trim, R, A_, TEX, M

SH = ((70, 150, 255), (6, 8, 16), (200, 230, 255), (30, 60, 140))   # blue fire on black, pale blue, deep blue
make('rise', *SH, [('d', 'E', 2, 11, 13, 15), ('a', 'l', 7, 11, 7, 4), ('m', 'E', 5, 2, 9, 6), ('w', 'p', 6, 4), ('w', 'p', 8, 4)])
make('shadow_exchange', *SH, [('m', 'l', 2, 5, 12, 5), ('m', 'l', 12, 5, 10, 3), ('a', 'l', 13, 10, 3, 10), ('a', 'l', 3, 10, 5, 12)])
make('umbral_step', *SH, [('a', 'l', 1, 12, 13, 4), ('m', 'l', 3, 13, 14, 6), ('w', 'p', 13, 4), ('d', 'l', 1, 9, 6, 6)])
make('unseen_hand', *SH, [('a', 'l', 3, 14, 3, 6), ('a', 'l', 6, 14, 6, 4), ('a', 'l', 9, 14, 9, 4), ('a', 'l', 12, 14, 12, 6), ('m', 'E', 5, 1, 10, 5)])
make('gate_of_shade', *SH, [('d', 'E', 1, 5, 14, 13), ('m', 'e', 1, 5, 14, 13), ('a', 'e', 4, 7, 11, 11), ('w', 'p', 7, 9)])
make('command_hunt', *SH, [('m', 'e', 2, 2, 13, 13), ('a', 'l', 7, 0, 7, 4), ('a', 'l', 7, 11, 7, 15), ('a', 'l', 0, 7, 4, 7), ('a', 'l', 11, 7, 15, 7), ('w', 'p', 7, 7)])
make('veil', *SH, [('d', 'E', 3, 2, 12, 14), ('a', 'a', 2, 1, 13, 15, 200, 340), ('m', 'p', 6, 6), ('m', 'p', 9, 6)])
make('oathbound_knight', *SH, [('d', 'E', 4, 2, 11, 12), ('m', 'l', 4, 2, 2, 0), ('m', 'l', 11, 2, 13, 0), ('w', 'l', 5, 6, 7, 7), ('w', 'l', 10, 6, 8, 7), ('a', 'l', 7, 10, 7, 15)])
make('sovereigns_domain', *SH, [('m', 'e', 0, 9, 15, 15), ('a', 'l', 2, 11, 3, 6), ('a', 'l', 5, 11, 6, 4), ('a', 'l', 9, 11, 9, 3), ('a', 'l', 12, 11, 12, 6), ('w', 'p', 7, 12)])
make('night_legion', *SH, [('d', 'E', 0, 10, 15, 15)] + [('m', 'E', x, 6 - (x % 3), x + 2, 10) for x in (1, 5, 9, 12)] + [('w', 'p', x + 1, 7 - (x % 3)) for x in (1, 5, 9, 12)])


def cover(big, INK, LEA, LEA_SH, MET, MET_SH, MET_HI, GEM, draw):
    im = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(im)
    frame(d, INK, LEA, LEA_SH, MET, MET_SH, MET_HI, GEM)
    cy = 176 if big else 196
    im = draw(im, d, 128, cy, 1.0 if big else 0.62, big)
    return im.resize((256, 384), Image.LANCZOS)


def shadow_draw(im, d, cx, cy, k, big):
    INKC, SMOKE, SMOKE2, BLUE, PALE, DEEP = (4, 4, 8, 255), (22, 24, 36, 255), (34, 38, 56, 255), (60, 140, 255, 255), (200, 232, 255, 255), (16, 40, 110, 255)
    im = glow(im, cx, cy, ((80 * k, 30), (54 * k, 60)), (50, 110, 255)); d = ImageDraw.Draw(im)
    for (x, y, r) in ((-46, 40, 24), (-20, 52, 28), (14, 54, 26), (44, 42, 24), (-56, 10, 18), (58, 12, 18)):   # black smoke curling round it
        circ(d, cx + x * k, cy + y * k, (r + 3) * k, fill=INKC)
        circ(d, cx + x * k, cy + y * k, r * k, fill=SMOKE if x < 0 else SMOKE2)
    # a crown of blue fire over the eye: five tongues
    for (x, hgt) in ((-34, 34), (-17, 48), (0, 60), (17, 48), (34, 34)):
        poly(d, [(cx + (x - 9) * k, cy - 18 * k), (cx + x * k, cy - (18 + hgt) * k), (cx + (x + 9) * k, cy - 18 * k)], INKC, None)
        poly(d, [(cx + (x - 6) * k, cy - 20 * k), (cx + x * k, cy - (16 + hgt) * k), (cx + (x + 6) * k, cy - 20 * k)], BLUE, None)
        poly(d, [(cx + (x - 3) * k, cy - 22 * k), (cx + x * k, cy - (18 + hgt * 0.55) * k), (cx + (x + 3) * k, cy - 22 * k)], PALE, None)
    # the eye: a long almond, a burning blue iris, a slit pupil
    poly(d, [(cx - 52 * k, cy + 8 * k), (cx, cy - 16 * k), (cx + 52 * k, cy + 8 * k), (cx, cy + 30 * k)], INKC, None)
    poly(d, [(cx - 46 * k, cy + 8 * k), (cx, cy - 11 * k), (cx + 46 * k, cy + 8 * k), (cx, cy + 25 * k)], DEEP, None)
    circ(d, cx, cy + 7 * k, 16 * k, fill=BLUE)
    circ(d, cx, cy + 7 * k, 9 * k, fill=PALE)
    poly(d, [(cx, cy - 7 * k), (cx + 3 * k, cy + 7 * k), (cx, cy + 21 * k), (cx - 3 * k, cy + 7 * k)], INKC, None)
    return im


name = 'shadow_grimoire'
fn = lambda b: cover(b, (4, 4, 8, 255), (20, 22, 30, 255), (12, 13, 20, 255), (150, 180, 220, 255), (90, 110, 150, 255), (220, 236, 255, 255), (60, 140, 255, 255), shadow_draw)
fn(True).save(os.path.join(M, f'{name}_front.png'))
fn(False).save(os.path.join(M, f'{name}_back.png'))
shift(Image.open(os.path.join(M, 'dark_grimoire_leather.png')), 0.62, 0.3, 0.45).save(os.path.join(M, f'{name}_leather.png'))
tint_trim(name, (150, 180, 220))
ic = shift(Image.open(os.path.join(TEX, 'dark_grimoire.png')), 0.62, 0.3, 0.5); px = ic.load()
for (x, y, c) in ((7, 7, (60, 140, 255)), (8, 7, (60, 140, 255)), (7, 8, (200, 232, 255)), (8, 8, (60, 140, 255))):
    if px[x, y][3]: px[x, y] = c + (255,)
ic.save(os.path.join(TEX, f'{name}.png'))

MD = os.path.join(A_, 'models/item')
CT = os.path.join(R, 'data/curios/tags/items/spellbook.json')
c = json.load(open(CT))
for suf in ('', '_model', '_open'):
    s = open(os.path.join(MD, f'light_grimoire{suf}.json')).read().replace('light_grimoire', name)
    open(os.path.join(MD, f'{name}{suf}.json'), 'w').write(s)
if f'iron_spell_grimoire:{name}' not in c['values']: c['values'].append(f'iron_spell_grimoire:{name}')
json.dump(c, open(CT, 'w'), indent=2)

LP = os.path.join(A_, 'lang/en_us.json')
lang = json.load(open(LP))
lang['item.iron_spell_grimoire.shadow_grimoire'] = 'Shadow Grimoire'
lang['entity.iron_spell_grimoire.shadow_soldier'] = 'Shadow Soldier'
lang['entity.iron_spell_grimoire.oathbound_knight'] = 'Oathbound Knight'
lang['key.iron_spell_grimoire.shadow_army'] = 'Shadow Army menu'
lang['ui.iron_spell_grimoire.shadow_grimoire_only'] = 'Only the Shadow Grimoire can cast this.'
lang['tooltip.iron_spell_grimoire.shadow_grimoire.passive'] = ('Passive - Remnants: what you slay while holding this grimoire leaves a remnant where it fell for 60 s - '
    'a black shadow of it in a dark pool, seen by you alone. Cast Rise on it to raise it as your shadow (3 tries; the stronger it was, the harder). '
    'Your shadows level up from their own kills and rank up: Soldier, Elite, Knight, Captain, General. Open your army with V to equip, rank up, rename or release them.')
lang['tooltip.iron_spell_grimoire.shadow_grimoire.desc'] = 'Ten pages of black smoke and blue fire, and an army that never stays dead.'
lang['tooltip.iron_spell_grimoire.shadow_grimoire.lore'] = 'Whatever falls before you rises again - for you.'
for k, (title, guide) in {
 'rise': ('Rise', 'Raise the remnant you look at as your shadow - it climbs up out of its pool, eyes burning blue. 3 tries each; weak foes rise easily, strong ones rarely.'),
 'shadow_exchange': ('Shadow Exchange', 'Trade places in an instant with the shadow at your side nearest where you look.'),
 'umbral_step': ('Umbral Step', 'Dash nine blocks through everything ahead, leaving shadow cuts in your wake.'),
 'unseen_hand': ('Unseen Hand', 'An invisible grip of smoke lifts the foe you aim at into the air, then slams it into the ground, hurting what stands near.'),
 'gate_of_shade': ('Gate of Shade', 'Sink every shadow at your side into your own shadow - or call them all out again.'),
 'command_hunt': ('Command: Hunt', 'Mark the foe you aim at: every shadow at your side turns on it, and its armour is torn down for 8 s.'),
 'veil': ('Veil', 'For 5 s you melt into shadow: foes lose sight of you, and your next blow lands 50% harder.'),
 'oathbound_knight': ('Oathbound Knight', 'Call your exclusive shadow, the Oathbound Knight - a towering knight of black plate with a blue greatsword. It joins your army at the Knight rank and can rise to Sovereign\'s Hand.'),
 'sovereigns_domain': ('Sovereign\'s Domain', 'Mode, 60 s: your shadows hit 50% harder and move faster, and any that fall rise again after 3 s instead of 30.'),
 'night_legion': ('Night Legion', 'Your whole army - even the shadows not at your side - bursts out of the ground in ranks and charges ahead behind a tide of shadow. Surpassing Limits: a second wave.'),
}.items():
    lang[f'spell.iron_spell_grimoire.{k}'] = f'Shadow: {title}'
    lang[f'spell.iron_spell_grimoire.{k}.guide'] = guide
json.dump(lang, open(LP, 'w'), indent=2, ensure_ascii=False)
print('shadow assets written')
