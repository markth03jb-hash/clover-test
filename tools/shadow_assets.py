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
make('status', *SH, [('d', 'E', 2, 1, 13, 14), ('m', 'l', 4, 4, 11, 4), ('a', 'l', 4, 7, 9, 7), ('a', 'l', 4, 10, 11, 10), ('w', 'p', 12, 3)])
make('market', *SH, [('d', 'E', 3, 6, 12, 14), ('m', 'e', 5, 2, 10, 7), ('a', 'l', 6, 9, 9, 9), ('w', 'p', 7, 11)])
make('sovereigns_grip', *SH, [('m', 'e', 4, 3, 11, 9), ('a', 'l', 5, 9, 5, 14), ('a', 'l', 7, 9, 7, 15), ('a', 'l', 9, 9, 9, 14), ('w', 'p', 7, 5)])
make('shadow_step', *SH, [('d', 'E', 0, 12, 6, 15), ('d', 'E', 9, 12, 15, 15), ('a', 'l', 3, 11, 12, 4), ('m', 'l', 12, 4, 12, 11), ('w', 'p', 12, 3)])
make('dread_roar', *SH, [('m', 'e', 1, 1, 14, 14), ('a', 'e', 4, 4, 11, 11), ('d', 'E', 6, 6, 9, 9), ('w', 'p', 7, 7)])
make('mantle_of_shades', *SH, [('d', 'E', 3, 2, 12, 14), ('m', 'l', 3, 6, 12, 6), ('a', 'l', 7, 6, 7, 13), ('w', 'p', 5, 4), ('w', 'p', 10, 4)])
make('ranks_of_shade', *SH, [('m', 'e', 1, 1, 14, 14)] + [('d', 'E', x - 1, y - 2, x + 1, y + 2) for x, y in ((7, 2), (12, 7), (7, 12), (2, 7))] + [('w', 'p', 7, 7)])
make('rally_of_the_fallen', *SH, [('d', 'E', 0, 12, 15, 15), ('a', 'l', 3, 12, 3, 5), ('a', 'l', 7, 12, 7, 2), ('a', 'l', 11, 12, 11, 5), ('w', 'p', 3, 4), ('w', 'p', 7, 1), ('w', 'p', 11, 4)])
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
lang['entity.iron_spell_grimoire.ravener'] = 'Ravener'
lang['key.iron_spell_grimoire.shadow_army'] = 'Shadow Army menu'
lang['ui.iron_spell_grimoire.shadow_grimoire_only'] = 'Only the Shadow Grimoire can cast this.'
lang['tooltip.iron_spell_grimoire.shadow_grimoire.passive'] = ('Passive - Remnants: what you slay while holding this grimoire leaves a remnant where it fell for 60 s - '
    'a black shadow of it in a dark pool, seen by you alone. Cast Rise on it to raise it as your shadow (3 tries; the stronger it was, the harder). '
    'Your shadows level up from their own kills and rank up: Soldier, Elite, Knight, Captain, General. Open your army with V to equip, rank up, rename or release them. '
    'Challenges (in the army menu): call out the living Oathbound Knight or Ravener, beat it, and raise it as an exclusive shadow - each with instincts of its own.')
lang['tooltip.iron_spell_grimoire.shadow_grimoire.desc'] = 'Seventeen pages of black smoke and blue fire, and an army that never stays dead.'
lang['tooltip.iron_spell_grimoire.shadow_grimoire.lore'] = 'Whatever falls before you rises again - for you.'
for k, (title, guide) in {
 'rise': ('Rise', 'Raise the remnant you look at as your shadow - it climbs up out of its pool, eyes burning blue. 3 tries each; weak foes rise easily, strong ones rarely.'),
 'shadow_exchange': ('Shadow Exchange', 'Trade places in an instant with the shadow at your side nearest where you look.'),
 'umbral_step': ('Umbral Step', 'Dash nine blocks through everything ahead, leaving shadow cuts in your wake.'),
 'unseen_hand': ('Unseen Hand', 'An invisible grip of smoke lifts the foe you aim at into the air, then slams it into the ground, hurting what stands near.'),
 'gate_of_shade': ('Gate of Shade', 'Sink every shadow at your side into your own shadow - or call them all out again.'),
 'command_hunt': ('Command: Hunt', 'Mark the foe you aim at: every shadow at your side turns on it, and its armour is torn down for 8 s.'),
 'veil': ('Veil', 'For 5 s you melt into shadow: foes lose sight of you, and your next blow lands 50% harder.'),
 'status': ('Status', 'Open your Status: your hunter level and XP, your five stats (spend your points here), your Shade Marks and today\'s Daily Trial.'),
 'market': ('Market', 'Open the Market: spend Shade Marks on the Nightfang dagger, Mana Tonics, Shade Salves, Remnant Lures, a stat reset or a Reckoning Key.'),
 'sovereigns_grip': ('Sovereign\'s Grip', 'Lift the foe you aim at and hold it where you look for 2.5 s - crouch to throw it early - then fling it along your aim; it takes damage when it lands, more the farther it flew. With no foe in sight, every dropped item within 12 blocks flies to you.'),
 'shadow_step': ('Shadow Step', 'Step out of the shadow you look at, however far away. With none in sight, step to a shadow keeping watch at its post (Station it in the army menu).'),
 'dread_roar': ('Dread Roar', 'A wave of dread rolls out 10 blocks: foes freeze for 2 s, weak ones flee for 6 s, and bosses hit 25% softer for 10 s.'),
 'mantle_of_shades': ('Mantle of Shades', 'Your three highest-ranked shadows at your side sink into you as armour for 30 s - more armour, toughness and strength the higher their ranks - then step back out.'),
 'ranks_of_shade': ('Ranks of Shade', 'Every shadow at your side steps out of your shadow into a ring round you. For 8 s they take 70% of the harm meant for you, split between them.'),
 'rally_of_the_fallen': ('Rally of the Fallen', 'Every fallen shadow rises again at once, and the shadows at your side are made whole and fight a rank stronger for 20 s - harder blows, more health, quicker feet.'),
 'sovereigns_domain': ('Sovereign\'s Domain', 'Mode, 60 s: your shadows hit 50% harder and move faster, and any that fall rise again after 3 s instead of 30.'),
 'night_legion': ('Night Legion', 'Your whole army - even the shadows not at your side - bursts out of the ground in ranks and charges ahead behind a tide of shadow. Surpassing Limits: a second wave.'),
}.items():
    lang[f'spell.iron_spell_grimoire.{k}'] = f'Shadow: {title}'
    lang[f'spell.iron_spell_grimoire.{k}.guide'] = guide
lang['item.iron_spell_grimoire.nightfang'] = 'Nightfang'
lang['item.iron_spell_grimoire.mana_tonic'] = 'Mana Tonic'
lang['item.iron_spell_grimoire.shade_salve'] = 'Shade Salve'
lang['tooltip.iron_spell_grimoire.nightfang.1'] = 'Right-click - Thousand Cuts: blink between up to five foes, cutting each, and land back where you started (15 s).'
lang['tooltip.iron_spell_grimoire.nightfang.2'] = 'Bought in the Shadow Grimoire\'s Market.'
lang['tooltip.iron_spell_grimoire.mana_tonic'] = 'Right-click: restores 150 mana at once.'
lang['tooltip.iron_spell_grimoire.shade_salve'] = 'Right-click: heals 8 hearts over 4 seconds.'
json.dump(lang, open(LP, 'w'), indent=2, ensure_ascii=False)
print('shadow assets written')
