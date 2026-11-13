"""The Chain, Hunter, Void, Gambler, Tarot, Abyss, Mech and Weather Grimoires (original grimoires): spell icons,
covers, leather, trim, inventory icons, models, the curios tag and the text. All original designs:
  Chain   - gunmetal leather, steel, a ring of five chain links with a pendulum hanging through it
  Hunter  - soot-black leather, tarnished silver, a red moon with a toothed blade across it
  Void    - pure black leather, white metal, a hole of nothing ringed in white with square flakes drifting off
  Gambler - green felt leather, gold, two dice and a coin
  Tarot   - deep violet leather, gold, a card with a star over a crescent
  Abyss   - black-blue leather, steel-blue, a teal lure hanging over a ring of pale teeth in the dark
  Mech    - gunmetal plates, hazard-yellow stripes, a gear with an orange visor slit
  Weather - sky-blue leather, cloud-white metal, a sun behind a cloud with a bolt under it
Cast animations reuse existing ones (see AddonSpellRegistry)."""
import json, os, sys, math, random
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
from icons import make
from _cover_helpers import *   # SC, W, H, Pp, poly, circ, ell, arc, frame, glow, shift, tint_trim, R, A_, TEX, M


def L(a, b, k='m'): return (k, 'l', *a, *b)
def chainpath(pts, k='m'): return [(k, 'l', *pts[i], *pts[i + 1]) for i in range(len(pts) - 1)]


# ------------------------------------------------------------------ spell icons
C = ((60, 66, 78), (10, 12, 16), (200, 208, 220), (232, 198, 106))   # chain: steel on gunmetal, a gold glint
def links(pts, k='m'): return [(k, 'e', x - 1, y - 2, x + 1, y + 2) if i % 2 == 0 else (k, 'e', x - 2, y - 1, x + 2, y + 1) for i, (x, y) in enumerate(pts)]
make('chain_lash', *C, links([(2, 12), (4, 10), (6, 8), (8, 7), (10, 6), (12, 6)]) + [('a', 'p', 14, 5), ('w', 'p', 13, 6)])
make('binding_chain', *C, [('a', 'E', 5, 3, 10, 13)] + [('m', 'l', 3, y, 12, y + 1) for y in (4, 7, 10)] + [('w', 'p', 7, 2)])
make('dowsing_chain', *C, links([(7, 1), (7, 4), (7, 7)]) + [('a', 'g', [(5, 9), (9, 9), (7, 14)]), ('w', 'p', 7, 10), ('m', 'a', 2, 6, 12, 16, 200, 340)])
make('healing_chain', (40, 70, 60), (6, 14, 10), (200, 208, 220), (120, 255, 160), links([(2, 13), (4, 11), (6, 9), (8, 7)]) + [('a', 'r', 10, 3, 14, 5), ('a', 'r', 11, 2, 13, 6)])
make('judgment_chain', *C, links([(1, 13), (3, 11), (5, 9)]) + [('w', 'l', 6, 8, 13, 1), ('a', 'g', [(13, 1), (10, 2), (12, 4)]), ('a', 'p', 7, 7)])
make('stealing_chain', *C, links([(1, 2), (3, 4), (5, 6), (7, 8)]) + [('a', 'a', 8, 7, 14, 13, 270, 450), ('a', 'l', 14, 10, 12, 12), ('w', 'p', 13, 9)])
make('chain_net', *C, [('m', 'l', x, 1, x, 14) for x in (3, 7, 11)] + [('m', 'l', 1, y, 14, y) for y in (3, 7, 11)] + [('a', 'p', 7, 7), ('a', 'p', 3, 11), ('a', 'p', 11, 3)])
make('anchor_chain', *C, links([(2, 1), (3, 3), (4, 5)]) + [('w', 'l', 8, 4, 8, 13), ('w', 'l', 5, 6, 11, 6), ('a', 'a', 3, 8, 13, 15, 0, 180), ('a', 'p', 3, 11), ('a', 'p', 13, 11)])
make('restriction', (70, 60, 90), (10, 8, 16), (200, 208, 220), (232, 198, 106), [('m', 'e', 1, 1, 14, 14)] + sum([links([(int(7.5 + 5 * math.cos(a)), int(7.5 + 5 * math.sin(a)))], 'a') for a in [i * 2 * math.pi / 5 for i in range(5)]], []) + [('w', 'p', 7, 7)])
make('thousand_chains', (90, 96, 110), (8, 10, 14), (220, 228, 240), (232, 198, 106), sum([links([(x, 14), (x, 11), (x + 1, 8), (x + 1, 5)]) for x in (1, 6, 11)], []) + [('a', 'p', 2, 3), ('a', 'p', 7, 2), ('a', 'p', 12, 3)])

HU = ((60, 14, 18), (8, 2, 4), (184, 176, 168), (220, 24, 40))       # hunter: tarnished silver and blood red
make('saw_cleave', *HU, [('m', 'l', 3, 13, 12, 4), ('m', 'l', 4, 13, 13, 4)] + [('m', 'p', 4 + i * 2, 10 - i * 2) for i in range(4)] + [('a', 'a', 1, 1, 15, 15, 200, 300)])
make('trick_switch', *HU, [('m', 'l', 2, 13, 7, 8), ('w', 'p', 7, 8), ('m', 'l', 8, 7, 13, 2), ('a', 'a', 4, 4, 12, 12, 0, 270), ('a', 'p', 11, 11)])
make('silver_shot', *HU, [('m', 'r', 1, 6, 8, 8), ('m', 'r', 2, 8, 4, 12), ('w', 'l', 9, 7, 14, 7), ('w', 'p', 14, 6), ('a', 'p', 10, 5)])
make('quickstep', *HU, [('a', 'E', 2, 6, 6, 10), ('m', 'E', 9, 4, 13, 12), ('m', 'l', 5, 8, 9, 8), ('a', 'p', 3, 4), ('a', 'p', 6, 12)])
make('visceral_strike', *HU, [('m', 'l', 1, 8, 9, 8), ('w', 'p', 9, 8), ('a', 'E', 9, 5, 14, 11), ('a', 'p', 13, 3), ('a', 'p', 14, 13), ('a', 'p', 11, 14)])
make('hunters_mark', *HU, [('a', 'e', 2, 2, 13, 13), ('a', 'l', 7, 0, 7, 4), ('a', 'l', 7, 11, 7, 15), ('a', 'l', 0, 7, 4, 7), ('a', 'l', 11, 7, 15, 7), ('w', 'p', 7, 7)])
make('rending_spin', *HU, [('m', 'a', 1, 1, 14, 14, 0, 300), ('m', 'a', 3, 3, 12, 12, 40, 340), ('a', 'p', 13, 5), ('w', 'p', 7, 7)])
make('blood_volley', *HU, [('a', 'E', 2, 3, 4, 5), ('a', 'E', 6, 6, 8, 8), ('a', 'E', 10, 9, 12, 11), ('m', 'l', 1, 2, 3, 4), ('m', 'l', 5, 5, 7, 7), ('m', 'l', 9, 8, 11, 10), ('a', 'p', 13, 13)])
make('blood_moon_hunt', (90, 10, 16), (10, 0, 2), (255, 90, 100), (255, 200, 200), [('m', 'E', 3, 2, 13, 12), ('d', 'E', 6, 3, 14, 11), ('a', 'p', 6, 6), ('w', 'l', 1, 14, 14, 14)])
make('nightmare_cleave', (120, 10, 20), (12, 0, 2), (255, 60, 80), (255, 255, 255), [('m', 'a', 0, 4, 15, 18, 200, 340), ('m', 'a', 2, 7, 13, 18, 200, 340), ('a', 'a', 4, 10, 11, 18, 200, 340), ('w', 'E', 6, 0, 9, 3)])

V = ((40, 40, 40), (0, 0, 0), (0, 0, 0), (255, 255, 255))          # void: black holes edged in white
def hole(x0, y0, x1, y1): return [('a', 'R', x0, y0, x1, y1), ('m', 'r', x0 + 1, y0 + 1, x1 - 1, y1 - 1)]
make('erase', *V, [('a', 'G', [(1, 7), (14, 2), (14, 13)]), ('m', 'g', [(3, 7), (13, 3), (13, 12)]), ('a', 'p', 1, 7)])
make('null_step', *V, hole(1, 5, 5, 11) + hole(10, 5, 14, 11) + [('a', 'p', 7, 8), ('a', 'p', 8, 8)])
make('unmake', *V, [('a', 'G', [(4, 2), (11, 2), (12, 6), (11, 13), (4, 13), (3, 6)])] + hole(5, 5, 10, 10))
make('nullify', *V, [('a', 'e', 0, 0, 15, 15), ('a', 'e', 3, 3, 12, 12)] + hole(6, 6, 9, 9))
make('seam', *V, [('a', 'l', 0, 14, 14, 0), ('m', 'l', 1, 14, 15, 0), ('a', 'l', 3, 4, 5, 6), ('a', 'l', 10, 9, 12, 11)])
make('absence', *V, [('a', 'e', 1, 1, 14, 14), ('m', 'E', 3, 3, 12, 12), ('a', 'p', 7, 0), ('a', 'p', 0, 7), ('a', 'p', 15, 7)])
make('void_lance', *V, [('a', 'l', 0, 7, 15, 7), ('a', 'l', 0, 9, 15, 9), ('m', 'l', 0, 8, 15, 8), ('a', 'p', 2, 3), ('a', 'p', 12, 12)])
make('delete', *V, [('a', 'R', 2, 2, 13, 13), ('a', 'l', 4, 4, 11, 11), ('a', 'l', 11, 4, 4, 11)])
make('emptiness', (90, 90, 90), (0, 0, 0), (0, 0, 0), (255, 255, 255), [('a', 'e', 2, 1, 13, 14)] + hole(5, 5, 10, 10) + [('a', 'p', 1, 12), ('a', 'p', 14, 3)])
make('origin_zero', (200, 200, 200), (20, 20, 20), (0, 0, 0), (255, 255, 255), [('w', 'E', 1, 1, 14, 14), ('m', 'e', 4, 4, 11, 11), ('m', 'e', 6, 6, 9, 9)])

G = ((14, 74, 38), (2, 14, 6), (242, 207, 90), (232, 52, 74))       # gambler: gold on felt, a red accent
def die(x, y, pips): return [('w', 'r', x, y, x + 5, y + 5)] + [('d', 'p', x + a, y + b) for a, b in pips]
make('coin_toss', *G, [('m', 'E', 4, 6, 11, 13), ('a', 'e', 5, 7, 10, 12), ('m', 'a', 2, 0, 13, 10, 200, 340), ('w', 'p', 7, 9)])
make('double_or_nothing', *G, [('m', 'E', 1, 4, 7, 10), ('a', 'E', 8, 4, 14, 10), ('w', 'p', 4, 7), ('d', 'p', 11, 7)])
make('roulette', *G, [('m', 'E', 1, 1, 14, 14), ('a', 'g', [(7, 1), (10, 7), (7, 7)]), ('d', 'g', [(14, 7), (8, 10), (8, 7)]), ('a', 'g', [(7, 14), (4, 8), (7, 8)]), ('w', 'p', 7, 7)])
make('chip_shot', *G, [('a', 'E', 1, 9, 5, 11), ('m', 'E', 5, 6, 9, 8), ('a', 'E', 9, 3, 13, 5), ('w', 'p', 3, 10), ('w', 'p', 7, 7), ('w', 'p', 11, 4)])
make('lucky_seven', *G, [('a', 'l', 3, 2, 12, 2), ('a', 'l', 12, 2, 6, 14), ('a', 'l', 4, 3, 12, 3), ('m', 'p', 2, 12), ('m', 'p', 13, 11)])
make('slot_machine', *G, [('m', 'R', 0, 3, 15, 12)] + [('w', 'r', 2 + 4 * i, 5, 4 + 4 * i, 10) for i in range(3)] + [('a', 'p', 3, 7), ('a', 'p', 7, 7), ('a', 'p', 11, 7)])
make('loaded_dice', *G, die(1, 2, [(1, 1), (4, 4), (2, 2)]) + die(8, 8, [(1, 1), (4, 1), (1, 4), (4, 4)]) + [('a', 'p', 14, 2)])
make('insurance', *G, [('m', 'g', [(7, 1), (13, 3), (12, 10), (7, 14), (2, 10), (1, 3)]), ('d', 'g', [(7, 3), (11, 4), (10, 9), (7, 12), (4, 9), (3, 4)]), ('a', 'l', 7, 4, 7, 11), ('a', 'l', 5, 6, 9, 6), ('a', 'l', 5, 9, 9, 9)])
make('all_in', (120, 30, 40), (14, 2, 4), (242, 207, 90), (255, 255, 255), [('m', 'E', 1, 9, 6, 13), ('m', 'E', 4, 6, 9, 10), ('m', 'E', 7, 3, 12, 7), ('m', 'E', 9, 0, 14, 4), ('a', 'p', 11, 2)])
make('jackpot', (160, 120, 20), (20, 10, 2), (255, 220, 90), (255, 255, 255), [('m', 'R', 0, 2, 15, 13)] + [('a', 'r', 2 + 4 * i, 5, 4 + 4 * i, 10) for i in range(3)] + [('w', 'p', 3, 7), ('w', 'p', 7, 7), ('w', 'p', 11, 7), ('m', 'p', 2, 0), ('m', 'p', 13, 15)])

T = ((60, 22, 90), (10, 4, 18), (232, 198, 106), (200, 160, 255))    # tarot: gold on deep violet
def card(x, y): return [('w', 'r', x, y, x + 6, y + 10), ('m', 'R', x, y, x + 6, y + 10)]
make('draw_card', *T, card(5, 3) + [('a', 'p', 8, 8), ('a', 'l', 7, 6, 9, 6), ('m', 'l', 2, 14, 13, 14)])
make('the_magician', *T, [('m', 'l', 7, 3, 7, 13), ('a', 'p', 3, 4), ('a', 'p', 11, 4), ('a', 'p', 3, 11), ('a', 'p', 11, 11), ('m', 'e', 2, 1, 7, 4), ('m', 'e', 7, 1, 12, 4)])
make('the_tower', *T, [('m', 'r', 5, 4, 9, 14), ('a', 'l', 12, 0, 9, 4), ('a', 'l', 9, 4, 11, 5), ('a', 'l', 11, 5, 8, 9), ('w', 'p', 6, 7)])
make('the_star', *T, [('m', 'l', 7, 1, 7, 13), ('m', 'l', 1, 7, 13, 7), ('m', 'l', 3, 3, 11, 11), ('m', 'l', 11, 3, 3, 11), ('w', 'p', 7, 7), ('a', 'p', 2, 13), ('a', 'p', 13, 2)])
make('death_card', *T, [('w', 'l', 4, 14, 11, 2), ('m', 'a', 4, 0, 15, 10, 180, 320), ('a', 'p', 13, 4)])
make('wheel_of_fortune', *T, [('m', 'e', 1, 1, 14, 14), ('m', 'e', 5, 5, 10, 10)] + [('a', 'l', int(7.5 + 2.5 * math.cos(a)), int(7.5 + 2.5 * math.sin(a)), int(7.5 + 6 * math.cos(a)), int(7.5 + 6 * math.sin(a))) for a in [i * math.pi / 4 for i in range(8)]])
make('the_hanged_man', *T, [('m', 'l', 1, 1, 14, 1), ('a', 'l', 7, 1, 7, 6), ('m', 'E', 5, 11, 9, 14), ('m', 'l', 7, 6, 7, 11), ('m', 'l', 4, 8, 10, 8)])
make('three_card_spread', *T, card(0, 4) + card(5, 2) + card(9, 4) + [('a', 'p', 8, 7)])
make('the_world', (90, 40, 120), (12, 4, 20), (232, 198, 106), (255, 255, 255), [('m', 'e', 2, 1, 13, 14), ('a', 'e', 4, 3, 11, 12), ('w', 'p', 7, 7), ('a', 'p', 1, 1), ('a', 'p', 14, 1), ('a', 'p', 1, 14), ('a', 'p', 14, 14)])
make('judgement', (120, 90, 30), (20, 10, 4), (255, 220, 120), (255, 255, 255), [('m', 'l', 1, 4, 9, 9), ('m', 'l', 1, 6, 9, 9), ('a', 'E', 9, 7, 12, 11), ('w', 'l', 13, 5, 15, 3), ('w', 'l', 13, 9, 15, 9), ('w', 'l', 13, 12, 15, 14)])

A = ((10, 28, 48), (1, 3, 8), (52, 240, 210), (220, 225, 210))      # abyss: teal light, pale teeth, in the dark
def teeth(y, up): return [('a', 'g', [(x, y), (x + 2, y), (x + 1, y + (-3 if up else 3))]) for x in range(1, 14, 3)]
make('crushing_pressure', *A, [('m', 'l', x, 1, x, 6) for x in (3, 7, 11)] + [('m', 'p', 3, 7), ('m', 'p', 7, 7), ('m', 'p', 11, 7), ('a', 'r', 1, 11, 14, 13)])
make('anglers_lure', *A, [('a', 'a', 1, 3, 12, 15, 180, 290), ('m', 'E', 9, 2, 13, 6), ('w', 'p', 11, 4)] + teeth(13, True))
make('abyssal_grasp', *A, [('d', 'a', 0, 2, 8, 16, 270, 360), ('m', 'a', 1, 2, 9, 16, 270, 360), ('m', 'a', 6, 2, 14, 16, 180, 270), ('m', 'p', 3, 9), ('m', 'p', 12, 9), ('a', 'E', 6, 3, 9, 6)])
make('undertow', *A, [('m', 'a', 1, 4, 13, 16, 180, 330), ('a', 'a', 3, 7, 11, 15, 180, 330), ('w', 'p', 11, 5), ('m', 'l', 14, 12, 9, 12), ('m', 'l', 9, 12, 11, 10)])
make('abyssal_dive', *A, [('m', 'e', 2, 10, 13, 14), ('d', 'E', 4, 11, 11, 13), ('a', 'l', 7, 1, 7, 9), ('a', 'l', 5, 7, 7, 9), ('a', 'l', 9, 7, 7, 9)])
make('depth_charge', *A, [('d', 'E', 4, 4, 11, 11), ('m', 'e', 4, 4, 11, 11), ('m', 'p', 7, 3), ('m', 'p', 3, 7), ('m', 'p', 11, 7), ('m', 'p', 7, 11), ('w', 'p', 7, 7), ('a', 'p', 13, 2)])
make('leviathan', *A, [('a', 'a', 0, 3, 15, 20, 190, 350), ('a', 'a', 1, 5, 14, 20, 190, 350), ('m', 'p', 3, 9), ('m', 'p', 6, 6), ('m', 'p', 9, 6), ('m', 'p', 12, 8), ('w', 'p', 13, 9), ('m', 'l', 0, 14, 15, 14)])
make('hadal_weight', *A, [('m', 'g', [(4, 6), (11, 6), (13, 13), (2, 13)]), ('a', 'e', 6, 2, 9, 6), ('w', 'l', 4, 10, 11, 10)])
make('bioluminescence', (20, 60, 70), (2, 6, 10), (52, 240, 210), (220, 255, 240), [('m', 'p', x, y) for x, y in ((2, 3), (5, 6), (9, 2), (12, 5), (3, 10), (7, 9), (11, 11), (13, 14), (5, 13))] + [('a', 'p', 7, 4), ('a', 'p', 10, 8)])
make('the_trench', (8, 20, 36), (0, 0, 2), (52, 240, 210), (230, 230, 210), [('d', 'E', 1, 5, 14, 14), ('m', 'e', 1, 5, 14, 14)] + teeth(5, False) + teeth(14, True))

K = ((48, 54, 62), (8, 10, 12), (255, 194, 26), (255, 122, 32))     # mech: hazard yellow and hot orange on gunmetal
def gear(cx, cy, r): return [('m', 'e', cx - r, cy - r, cx + r, cy + r)] + [('m', 'p', int(cx + (r + 1) * math.cos(a)), int(cy + (r + 1) * math.sin(a))) for a in [i * math.pi / 4 for i in range(8)]]
make('rivet_barrage', *K, [('w', 'l', 1, 4, 6, 4), ('w', 'l', 3, 8, 9, 8), ('w', 'l', 5, 12, 11, 12), ('a', 'p', 7, 4), ('a', 'p', 10, 8), ('a', 'p', 12, 12), ('m', 'r', 13, 2, 15, 14)])
make('thruster_dash', *K, [('m', 'r', 8, 5, 13, 10), ('a', 'g', [(8, 6), (2, 4), (4, 7), (1, 8), (4, 9), (2, 11), (8, 9)]), ('w', 'p', 12, 7)])
make('rocket_fist', *K, [('m', 'r', 7, 4, 13, 11), ('w', 'l', 13, 5, 13, 10), ('a', 'g', [(7, 6), (2, 5), (4, 8), (2, 10), (7, 9)]), ('d', 'l', 9, 4, 9, 11)])
make('piston_stomp', *K, [('m', 'r', 6, 1, 9, 9), ('w', 'r', 3, 9, 12, 11), ('a', 'l', 0, 14, 15, 14), ('a', 'p', 1, 12), ('a', 'p', 14, 12)])
make('bulwark_plating', *K, [('m', 'r', 2, 2, 13, 13), ('d', 'R', 4, 4, 11, 11)] + [('a', 'p', x, y) for x, y in ((3, 3), (12, 3), (3, 12), (12, 12))] + [('m', 'l', 2, 7, 13, 7)])
make('missile_swarm', *K, [('w', 'l', 2, 12, 5, 9), ('w', 'l', 6, 13, 9, 7), ('w', 'l', 10, 12, 13, 5), ('a', 'p', 5, 9), ('a', 'p', 9, 7), ('a', 'p', 13, 5), ('m', 'p', 1, 13), ('m', 'p', 5, 14), ('m', 'p', 9, 14)])
make('overclock', *K, gear(7, 7, 4) + [('a', 'E', 6, 6, 9, 9), ('a', 'l', 12, 1, 14, 1), ('a', 'l', 13, 0, 13, 2)])
make('rail_cannon', *K, [('m', 'r', 1, 6, 9, 9), ('m', 'r', 3, 9, 5, 12), ('a', 'l', 10, 7, 15, 7), ('a', 'l', 10, 8, 15, 8), ('w', 'p', 9, 7)])
make('pilot', (90, 70, 20), (10, 8, 4), (255, 194, 26), (255, 122, 32), [('m', 'r', 4, 2, 11, 7), ('a', 'l', 5, 4, 10, 4), ('m', 'r', 3, 8, 12, 12), ('m', 'r', 1, 8, 2, 13), ('m', 'r', 13, 8, 14, 13), ('m', 'r', 5, 13, 6, 15), ('m', 'r', 9, 13, 10, 15)])
make('titan_buster', (120, 60, 20), (14, 6, 2), (255, 194, 26), (255, 255, 255), [('m', 'r', 4, 0, 11, 9), ('w', 'l', 4, 9, 11, 9), ('a', 'l', 0, 14, 15, 14), ('a', 'p', 2, 12), ('a', 'p', 13, 12), ('a', 'p', 7, 12)])

Wt = ((40, 100, 160), (6, 18, 34), (242, 248, 255), (255, 211, 74))  # weather: cloud-white on sky, sun gold
def cloud(x, y): return [('m', 'E', x, y + 1, x + 5, y + 5), ('m', 'E', x + 3, y, x + 9, y + 5), ('m', 'E', x + 7, y + 2, x + 11, y + 5)]
make('sunbeam', *Wt, [('a', 'E', 5, 0, 10, 5), ('a', 'r', 6, 5, 9, 13), ('w', 'l', 7, 5, 7, 13), ('a', 'l', 3, 14, 12, 14)])
make('downpour', *Wt, cloud(2, 2) + [(('a', 'l', x, 9, x - 1, 12)) for x in (4, 7, 10, 13)])
make('thunder_strike', (30, 40, 70), (4, 6, 14), (110, 116, 140), (255, 230, 80), cloud(2, 1) + [('a', 'l', 8, 7, 6, 10), ('a', 'l', 6, 10, 9, 10), ('a', 'l', 9, 10, 6, 15)])
make('hailstorm', *Wt, cloud(2, 1) + [('w', 'r', x, y, x + 1, y + 1) for x, y in ((3, 9), (7, 11), (11, 9), (5, 13), (10, 13))])
make('gale', *Wt, [('m', 'l', 1, 4, 10, 4), ('m', 'a', 8, 1, 13, 6, 270, 450), ('m', 'l', 1, 8, 13, 8), ('m', 'a', 11, 8, 15, 12, 270, 450), ('m', 'l', 3, 12, 9, 12)])
make('fog_bank', (90, 100, 110), (20, 24, 28), (210, 214, 222), (255, 255, 255), [('m', 'l', 1 + (i % 2), 3 + i * 3, 13 - (i % 2), 3 + i * 3) for i in range(4)] + [('a', 'p', 7, 6)])
make('cyclone', *Wt, [('m', 'l', 2, 2, 13, 2), ('m', 'l', 3, 5, 12, 5), ('m', 'l', 5, 8, 10, 8), ('m', 'l', 6, 11, 9, 11), ('m', 'l', 7, 14, 8, 14), ('a', 'p', 1, 4), ('a', 'p', 13, 7)])
make('rainbow', (60, 120, 180), (8, 20, 36), (255, 255, 255), (255, 211, 74), [('w', 'a', 1, 3, 15, 17, 180, 360)] + [(k, 'a', 1 + i, 3 + i, 15 - i, 17 - i, 180, 360) for i, k in enumerate(('a', 'm', 'a'))] + [('d', 'a', 4, 6, 12, 14, 180, 360)])
make('eye_of_the_storm', (30, 50, 80), (4, 8, 14), (140, 150, 170), (255, 230, 80), [('m', 'e', 0, 0, 15, 15), ('m', 'e', 2, 2, 13, 13), ('w', 'E', 6, 6, 9, 9), ('a', 'l', 13, 2, 11, 5), ('a', 'l', 2, 12, 4, 10)])
make('cataclysm', (110, 60, 30), (14, 6, 4), (255, 211, 74), (140, 200, 255), [('m', 'E', 0, 0, 6, 6)] + cloud(4, 3) + [('a', 'l', 8, 9, 6, 12), ('a', 'l', 6, 12, 9, 15), ('w', 'p', 12, 11), ('w', 'p', 14, 13)])


# ------------------------------------------------------------------ covers
def cover(big, INK, LEA, LEA_SH, MET, MET_SH, MET_HI, GEM, draw):
    im = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(im)
    frame(d, INK, LEA, LEA_SH, MET, MET_SH, MET_HI, GEM)
    cy = 176 if big else 196
    im = draw(im, d, 128, cy, 1.0 if big else 0.62, big)
    return im.resize((256, 384), Image.LANCZOS)


def chain_draw(im, d, cx, cy, k, big):
    STEEL, DARK, GOLD, INK = (200, 208, 220, 255), (70, 78, 92, 255), (232, 198, 106, 255), (8, 10, 14, 255)
    im = glow(im, cx, cy, ((90 * k, 18), (64 * k, 30)), (232, 198, 106)); d = ImageDraw.Draw(im)
    for i in range(5):   # five links in a ring
        a = -math.pi / 2 + i * 2 * math.pi / 5
        x, y = cx + math.cos(a) * 52 * k, cy + math.sin(a) * 52 * k
        rot = a + math.pi / 2
        pts = [(x + math.cos(rot) * 22 * k * math.cos(t) - math.sin(rot) * 12 * k * math.sin(t), y + math.sin(rot) * 22 * k * math.cos(t) + math.cos(rot) * 12 * k * math.sin(t)) for t in [j * 2 * math.pi / 40 for j in range(41)]]
        d.line([Pp(*p) for p in pts], fill=INK, width=int(11 * k * SC)); d.line([Pp(*p) for p in pts], fill=STEEL, width=int(6 * k * SC))
    if big:   # the pendulum hanging through the ring
        d.line([Pp(cx, cy - 52 * k), Pp(cx, cy + 40)], fill=INK, width=6 * SC); d.line([Pp(cx, cy - 52 * k), Pp(cx, cy + 40)], fill=DARK, width=3 * SC)
        poly(d, [(cx - 12, cy + 40), (cx + 12, cy + 40), (cx, cy + 74)], GOLD, INK, 3)
    else:
        circ(d, cx, cy, 6, fill=GOLD, outline=INK, w=2)
    return im


def hunter_draw(im, d, cx, cy, k, big):
    MOON, MOON_D, SIL, INK = (210, 24, 40, 255), (140, 10, 22, 255), (184, 176, 168, 255), (6, 2, 2, 255)
    im = glow(im, cx, cy - 10 * k, ((92 * k, 26), (70 * k, 50)), (210, 24, 40)); d = ImageDraw.Draw(im)
    circ(d, cx, cy - 10 * k, 58 * k, fill=MOON, outline=INK, w=3)
    circ(d, cx + 18 * k, cy - 20 * k, 14 * k, fill=MOON_D); circ(d, cx - 22 * k, cy + 8 * k, 9 * k, fill=MOON_D)
    # a long toothed blade across the moon
    x0, y0, x1, y1 = cx - 80 * k, cy + 52 * k, cx + 70 * k, cy - 74 * k
    poly(d, [(x0, y0), (x1, y1), (x1 + 10 * k, y1 + 10 * k), (x0 + 12 * k, y0 + 10 * k)], SIL, INK, 3)
    for i in range(9):
        u = 0.12 + i * 0.09
        px, py = x0 + (x1 - x0) * u + 10 * k, y0 + (y1 - y0) * u + 10 * k
        poly(d, [(px, py), (px + 9 * k, py + 2 * k), (px + 2 * k, py + 9 * k)], SIL, INK, 2)
    poly(d, [(x0 - 6 * k, y0 + 4 * k), (x0 + 6 * k, y0 - 6 * k), (x0 - 22 * k, y0 + 30 * k), (x0 - 32 * k, y0 + 22 * k)], (60, 40, 30, 255), INK, 3)
    return im


def void_draw(im, d, cx, cy, k, big):
    WHITE, BLACK = (255, 255, 255, 255), (0, 0, 0, 255)
    circ(d, cx, cy, 64 * k, fill=WHITE); circ(d, cx, cy, 58 * k, fill=BLACK)
    rnd = random.Random(3)
    for i in range(14 if big else 6):   # flakes of the world drifting off into it
        a = rnd.uniform(0, 2 * math.pi); r = rnd.uniform(70, 120) * k; s = rnd.uniform(4, 10) * k
        x, y = cx + math.cos(a) * r, cy + math.sin(a) * r
        d.rectangle([Pp(x - s - 1.5, y - s - 1.5), Pp(x + s + 1.5, y + s + 1.5)], fill=WHITE); d.rectangle([Pp(x - s, y - s), Pp(x + s, y + s)], fill=BLACK)
    return im


def gambler_draw(im, d, cx, cy, k, big):
    IVORY, PIP, GOLD, GOLD_D, RED, INK = (246, 240, 224, 255), (16, 16, 16, 255), (242, 207, 90, 255), (180, 140, 40, 255), (232, 52, 74, 255), (4, 10, 6, 255)
    def die(x, y, s, pips, rot):
        c, sn = math.cos(rot), math.sin(rot)
        q = [(x + (px * c - py * sn) * s, y + (px * sn + py * c) * s) for px, py in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
        poly(d, q, IVORY, INK, 3)
        for px, py in pips:
            circ(d, x + (px * c - py * sn) * s * 0.55, y + (px * sn + py * c) * s * 0.55, s * 0.16, fill=RED if len(pips) == 1 else PIP)
    die(cx - 34 * k, cy - 18 * k, 32 * k, [(-1, -1), (0, 0), (1, 1)], -0.25)
    die(cx + 36 * k, cy + 10 * k, 30 * k, [(-1, -1), (1, -1), (-1, 1), (1, 1), (0, 0)], 0.3)
    if big:
        circ(d, cx + 6, cy + 86, 26, fill=GOLD, outline=INK, w=3); circ(d, cx + 6, cy + 86, 18, outline=GOLD_D, w=3)
        d.text(Pp(cx - 1, cy + 74), '7', fill=GOLD_D)
    return im


def tarot_draw(im, d, cx, cy, k, big):
    CARD, GOLD, VIO, INK = (240, 228, 200, 255), (232, 198, 106, 255), (90, 40, 140, 255), (10, 4, 18, 255)
    im = glow(im, cx, cy, ((96 * k, 20), (70 * k, 34)), (200, 160, 255)); d = ImageDraw.Draw(im)
    poly(d, [(cx - 44 * k, cy - 70 * k), (cx + 44 * k, cy - 70 * k), (cx + 44 * k, cy + 70 * k), (cx - 44 * k, cy + 70 * k)], CARD, INK, 3)
    d.rectangle([Pp(cx - 36 * k, cy - 62 * k), Pp(cx + 36 * k, cy + 62 * k)], outline=GOLD, width=int(3 * k * SC))
    for i in range(8):   # a star
        a = i * math.pi / 4; r = (24 if i % 2 == 0 else 11) * k
        d.line([Pp(cx, cy - 16 * k), Pp(cx + math.cos(a) * r, cy - 16 * k + math.sin(a) * r)], fill=GOLD, width=int(4 * k * SC))
    circ(d, cx, cy - 16 * k, 5 * k, fill=VIO)
    d.arc([Pp(cx - 20 * k, cy + 14 * k), Pp(cx + 20 * k, cy + 54 * k)], 200, 340, fill=VIO, width=int(6 * k * SC))   # and a crescent
    return im


def abyss_draw(im, d, cx, cy, k, big):
    TEAL, PALE, BONE, DARK, INK = (52, 240, 210, 255), (210, 255, 240, 255), (226, 228, 210, 255), (6, 16, 28, 255), (0, 2, 6, 255)
    lx, ly = cx + 8 * k, cy - 46 * k
    d.arc([Pp(cx - 70 * k, ly - 30 * k), Pp(lx + 4 * k, ly + 70 * k)], 190, 300, fill=DARK, width=int(6 * k * SC))   # the stalk
    im = glow(im, lx, ly, ((46 * k, 24), (28 * k, 60), (16 * k, 120)), (52, 240, 210)); d = ImageDraw.Draw(im)
    circ(d, lx, ly, 9 * k, fill=PALE)
    jy = cy + 40 * k
    for row, sg in ((jy - 18 * k, 1), (jy + 30 * k, -1)):   # two rows of long teeth closing on the dark
        for i in range(7):
            x = cx - 66 * k + i * 22 * k
            poly(d, [(x, row), (x + 14 * k, row), (x + 7 * k, row + sg * 34 * k)], BONE, INK, 2)
    for (x, y) in ((cx - 80 * k, cy - 70 * k), (cx + 70 * k, cy + 6 * k), (cx - 50 * k, cy + 90 * k)):
        circ(d, x, y, 3 * k, fill=TEAL)
    return im


def mech_draw(im, d, cx, cy, k, big):
    METAL, YEL, ORG, INK = (90, 98, 110, 255), (255, 194, 26, 255), (255, 122, 32, 255), (8, 10, 12, 255)
    for i in range(12):   # the gear's teeth
        a = i * math.pi / 6
        x, y = cx + math.cos(a) * 62 * k, cy + math.sin(a) * 62 * k
        poly(d, [(x + math.cos(a + 1.57) * 9 * k, y + math.sin(a + 1.57) * 9 * k), (x + math.cos(a - 1.57) * 9 * k, y + math.sin(a - 1.57) * 9 * k),
                 (x + math.cos(a) * 14 * k + math.cos(a - 1.57) * 7 * k, y + math.sin(a) * 14 * k + math.sin(a - 1.57) * 7 * k),
                 (x + math.cos(a) * 14 * k + math.cos(a + 1.57) * 7 * k, y + math.sin(a) * 14 * k + math.sin(a + 1.57) * 7 * k)], METAL, INK, 2)
    circ(d, cx, cy, 64 * k, fill=METAL, outline=INK, w=3); circ(d, cx, cy, 44 * k, fill=(50, 56, 64, 255), outline=INK, w=2)
    d.rectangle([Pp(cx - 30 * k, cy - 6 * k), Pp(cx + 30 * k, cy + 6 * k)], fill=INK)
    im = glow(im, cx, cy, ((40 * k, 40),), (255, 122, 32)); d = ImageDraw.Draw(im)
    d.rectangle([Pp(cx - 26 * k, cy - 3 * k), Pp(cx + 26 * k, cy + 3 * k)], fill=ORG)
    if big:   # hazard stripes along the foot of the cover
        for i in range(8):
            x = 30 + i * 26
            poly(d, [(x, 300), (x + 12, 300), (x + 2, 318), (x - 10, 318)], YEL, None)
    return im


def weather_draw(im, d, cx, cy, k, big):
    SUN, CLOUD, CLOUD_D, BOLT, INK = (255, 211, 74, 255), (244, 248, 255, 255), (190, 204, 222, 255), (255, 236, 120, 255), (6, 16, 30, 255)
    im = glow(im, cx + 30 * k, cy - 40 * k, ((70 * k, 30), (50 * k, 60)), (255, 211, 74)); d = ImageDraw.Draw(im)
    circ(d, cx + 30 * k, cy - 40 * k, 36 * k, fill=SUN, outline=INK, w=3)
    for (x, y, r) in ((-40, 0, 30), (-6, -16, 38), (34, 2, 30), (-22, 14, 26), (16, 16, 26)):
        circ(d, cx + x * k, cy + y * k, (r + 3) * k, fill=INK)
    for (x, y, r) in ((-40, 0, 30), (-6, -16, 38), (34, 2, 30), (-22, 14, 26), (16, 16, 26)):
        circ(d, cx + x * k, cy + y * k, r * k, fill=CLOUD)
    circ(d, cx - 10 * k, cy + 20 * k, 20 * k, fill=CLOUD_D)
    if big:
        poly(d, [(cx - 4, cy + 40), (cx + 16, cy + 40), (cx + 4, cy + 70), (cx + 20, cy + 70), (cx - 14, cy + 118), (cx - 4, cy + 80), (cx - 18, cy + 80)], BOLT, INK, 3)
    return im


BOOKS = [
    ('chain_grimoire', lambda b: cover(b, (8, 10, 14, 255), (44, 50, 60, 255), (30, 34, 42, 255), (200, 208, 220, 255), (120, 128, 140, 255), (236, 240, 248, 255), (232, 198, 106, 255), chain_draw),
     0.6, 0.15, 0.75, (200, 208, 220), ((7, 6, (200, 208, 220)), (8, 7, (200, 208, 220)), (7, 8, (232, 198, 106)))),
    ('hunter_grimoire', lambda b: cover(b, (6, 2, 2, 255), (26, 12, 12, 255), (16, 6, 6, 255), (176, 168, 158, 255), (110, 100, 92, 255), (214, 206, 196, 255), (210, 24, 40, 255), hunter_draw),
     0.0, 0.6, 0.45, (184, 170, 160), ((7, 7, (210, 24, 40)), (8, 7, (210, 24, 40)), (7, 8, (210, 24, 40)), (8, 6, (184, 176, 168)))),
    ('void_grimoire', lambda b: cover(b, (0, 0, 0, 255), (8, 8, 8, 255), (2, 2, 2, 255), (240, 240, 240, 255), (150, 150, 150, 255), (255, 255, 255, 255), (255, 255, 255, 255), void_draw),
     0.0, 0.0, 0.25, (245, 245, 245), ((7, 7, (0, 0, 0)), (8, 7, (0, 0, 0)), (6, 7, (255, 255, 255)), (9, 7, (255, 255, 255)))),
    ('gambler_grimoire', lambda b: cover(b, (2, 10, 4, 255), (14, 74, 38, 255), (8, 50, 24, 255), (242, 207, 90, 255), (170, 130, 40, 255), (255, 240, 170, 255), (232, 52, 74, 255), gambler_draw),
     0.4, 1.0, 0.8, (242, 207, 90), ((7, 7, (246, 240, 224)), (8, 7, (246, 240, 224)), (7, 8, (232, 52, 74)))),
    ('tarot_grimoire', lambda b: cover(b, (10, 4, 18, 255), (60, 22, 90, 255), (40, 12, 62, 255), (232, 198, 106, 255), (160, 124, 50, 255), (255, 236, 170, 255), (200, 160, 255, 255), tarot_draw),
     0.78, 0.9, 0.7, (232, 198, 106), ((7, 6, (240, 228, 200)), (7, 7, (240, 228, 200)), (7, 8, (232, 198, 106)))),
    ('abyss_grimoire', lambda b: cover(b, (0, 2, 6, 255), (8, 22, 38, 255), (4, 12, 22, 255), (92, 122, 148, 255), (52, 72, 92, 255), (150, 180, 200, 255), (52, 240, 210, 255), abyss_draw),
     0.58, 0.9, 0.35, (92, 122, 148), ((7, 7, (52, 240, 210)), (8, 8, (210, 255, 240)))),
    ('mech_grimoire', lambda b: cover(b, (8, 10, 12, 255), (48, 54, 62, 255), (32, 36, 42, 255), (255, 194, 26, 255), (176, 130, 10, 255), (255, 230, 140, 255), (255, 122, 32, 255), mech_draw),
     0.58, 0.15, 0.6, (255, 194, 26), ((6, 7, (255, 122, 32)), (7, 7, (255, 122, 32)), (8, 7, (255, 122, 32)), (7, 9, (255, 194, 26)))),
    ('weather_grimoire', lambda b: cover(b, (6, 16, 30, 255), (42, 100, 150, 255), (28, 70, 110, 255), (242, 248, 255, 255), (170, 186, 206, 255), (255, 255, 255, 255), (255, 211, 74, 255), weather_draw),
     0.58, 1.1, 1.0, (242, 248, 255), ((7, 6, (255, 211, 74)), (7, 8, (244, 248, 255)), (8, 8, (244, 248, 255)))),
]
for name, fn, hue, sm, vm, trim, ic_marks in BOOKS:
    fn(True).save(os.path.join(M, f'{name}_front.png'))
    fn(False).save(os.path.join(M, f'{name}_back.png'))
    shift(Image.open(os.path.join(M, 'dark_grimoire_leather.png')), hue, sm, vm).save(os.path.join(M, f'{name}_leather.png'))
    tint_trim(name, trim)
    ic = shift(Image.open(os.path.join(TEX, 'dark_grimoire.png')), hue, sm, vm + 0.15); px = ic.load()
    for (x, y, c) in ic_marks:
        if px[x, y][3]: px[x, y] = c + (255,)
    ic.save(os.path.join(TEX, f'{name}.png'))

MD = os.path.join(A_, 'models/item')
CT = os.path.join(R, 'data/curios/tags/items/spellbook.json')
c = json.load(open(CT))
for name, *_ in BOOKS:
    for suf in ('', '_model', '_open'):
        s = open(os.path.join(MD, f'light_grimoire{suf}.json')).read().replace('light_grimoire', name)
        open(os.path.join(MD, f'{name}{suf}.json'), 'w').write(s)
    if f'iron_spell_grimoire:{name}' not in c['values']: c['values'].append(f'iron_spell_grimoire:{name}')
json.dump(c, open(CT, 'w'), indent=2)

# ------------------------------------------------------------------ lang
LP = os.path.join(A_, 'lang/en_us.json')
lang = json.load(open(LP))
def spells(school, dd):
    for k, (title, guide) in dd.items():
        lang[f'spell.iron_spell_grimoire.{k}'] = f'{school}: {title}'
        lang[f'spell.iron_spell_grimoire.{k}.guide'] = guide
for key, name, passive, desc, lore in (
        ('chain', 'Chain Grimoire', 'Passive - Five Rules: use all five kinds of chain (Binding, Healing, Judgment, Dowsing, Stealing) within 20 s and your next spell lands twice as hard.',
         'Ten pages of Chain Magic: five chains, each with its own rule - one binds, one heals, one judges, one seeks and one steals - and a thousand more under the ground.', 'Every link was forged with a promise. Break the promise, and the chain breaks you.'),
        ('hunter', 'Hunter Grimoire', 'Passive - Rally: health a foe takes from you can be won back by hitting back within 3 s. Silver Shots on a foe that just attacked stagger it, and staggered foes take Visceral Strikes four times over.',
         'Ten pages for the night of the hunt: a toothed blade that changes form, silver bullets, blood paid for power, and a red moon.', 'The ink is dried blood. The last page is always wet.'),
        ('void', 'Void Grimoire', 'Passive - Erasure: every Void hit erases a little of the foe\'s armour and keeps two Nothing. What the book erases becomes Nothing (shown on your screen), and Origin Zero lets it all out.',
         'Ten pages of taking things away: missiles, armour, strength, a slice of space, and in the end the foe itself.', 'There used to be more pages.'),
        ('gambler', 'Gambler Grimoire', 'Passive - Coin Flip: most Gambler spells flip a coin - heads, they land better. Every tails earns a point of Luck, and three Luck makes the next flip heads.',
         'Ten pages of chance: coins, dice, a roulette wheel and a slot machine - and a jackpot that always pays.', 'The house always wins. This book is the house.'),
        ('tarot', 'Tarot Grimoire', 'Passive - The Reading: you carry a deck of twenty-two cards. Draw them all and the reading is complete: your spells land 30% harder for 15 s and the deck is shuffled.',
         'Ten pages of fortune: cards drawn upright or reversed, a tower struck, stars falling, the scythe of Death, and Judgement in the sky.', 'Every page is a card. Every card has already been read.'),
        ('abyss', 'Lightless Abyss Grimoire', 'Passive - Pressure: every Abyss hit sinks you deeper (Depth, on your screen). The deeper you are, the harder Abyss spells hit (up to 50%), and from halfway down you take 12% less. The edges of your sight grow dark the deeper you go.',
         'Ten pages from the bottom of the sea: crushing pressure, lures of cold light, things with too many arms, a leviathan, and a trench with a mouth.', 'It smells of salt. It is cold to the touch, always.'),
        ('mech', 'Mech Grimoire', 'Passive - Assembly: every Mech spell bolts its part onto your machine for 45 s (the blueprint on your screen). With all eight parts at once the machine is complete: Mech spells land 25% harder.',
         'Ten pages of blueprints: rivets, thrusters, a rocket fist, pistons, plating, missiles, a cannon - a machine to climb into, and a fist the size of a house.', 'Some assembly required.'),
        ('weather', 'Weather Grimoire', 'Passive - Forecast: every spell changes your own sky (its little cloud hangs over your head). Sun: 15% harder. Rain: hits refresh you and soak foes. Storm: hits may call a spare bolt. Hail: hits chill. Wind: hits knock back and you move faster. Fog: you take 15% less.',
         'Ten pages of your own sky: sunbeams, rain, lightning, hail, gales, fog, a cyclone, a rainbow, the eye of a storm - and a catastrophe of whatever the forecast is.', 'Its pages are always a little damp, and they smell like the air before a storm.')):
    lang[f'item.iron_spell_grimoire.{key}_grimoire'] = name
    lang[f'ui.iron_spell_grimoire.{key}_grimoire_only'] = f'Only the {name} can cast this.'
    lang[f'tooltip.iron_spell_grimoire.{key}_grimoire.passive'] = passive
    lang[f'tooltip.iron_spell_grimoire.{key}_grimoire.desc'] = desc
    lang[f'tooltip.iron_spell_grimoire.{key}_grimoire.lore'] = lore
spells('Chain Magic', {
 'chain_lash': ('Chain Lash', 'A whip of chain cracks across everything in front of you.'),
 'binding_chain': ('Binding Chain', 'The Binding chain wraps the foe you aim at and holds it for 3 s (bosses 1.5 s).'),
 'dowsing_chain': ('Dowsing Chain', 'The pendulum swings: every foe within 24 blocks is shown to you and Read - your Chain spells hit Read foes harder.'),
 'healing_chain': ('Healing Chain', 'The Healing chain links you to the ally you aim at (or to yourself) and mends them over a few seconds.'),
 'judgment_chain': ('Judgment Chain', 'Stake a foe\'s heart with a vow: if it hurts anyone in the next 10 s, the chain closes and it takes a terrible blow.'),
 'stealing_chain': ('Stealing Chain', 'The hook rips into a foe and takes: its health heals you, its speed becomes yours.'),
 'chain_net': ('Chain Net', 'A net of chains falls on your target\'s ground and holds everything under it.'),
 'anchor_chain': ('Anchor Chain', 'Throw the anchor: aimed at a foe, you drag it to you; aimed at the ground, it drags you there.'),
 'restriction': ('Restriction', 'Mode, 20 s: you bind yourself to a vow - every Chain spell hits 40% harder and comes back faster.'),
 'thousand_chains': ('Thousand Chains', 'Chains burst out of the ground round your target, bind every foe there, and crush them.'),
})
spells('Hunter Magic', {
 'saw_cleave': ('Saw Cleave', 'Short form: two quick cuts in front of you. Long form: one long sweep.'),
 'trick_switch': ('Trick Switch', 'The weapon snaps to its other form with a strike: out to long with a lunge, back to short with a cut.'),
 'silver_shot': ('Silver Shot', 'A quick silver shot at the foe you aim at - a foe that just attacked is staggered.'),
 'quickstep': ('Quickstep', 'Dissolve into mist and reappear six blocks away; for a moment nothing can touch you.'),
 'visceral_strike': ('Visceral Strike', 'A thrust into the foe in front of you; a staggered foe takes it four times over.'),
 'hunters_mark': ('Hunter\'s Mark', 'Mark a foe: for 10 s it takes 25% more from you, and if it dies marked your cooldowns refresh.'),
 'rending_spin': ('Rending Spin', 'Spin with the weapon three times - tight and fast in short form, wide in long form.'),
 'blood_volley': ('Blood Volley', 'Pay a tenth of your health: five bullets of your own blood, far stronger than silver.'),
 'blood_moon_hunt': ('Blood Moon', 'Mode, 20 s: a red moon rises over you. Rally wins back everything, and every hit builds Frenzy.'),
 'nightmare_cleave': ('Nightmare Cleave', 'Leap and bring the weapon down: three great crimson arcs roll out under the moon.'),
})
spells('Void Magic', {
 'erase': ('Erase', 'A wedge of nothing in front of you: missiles in it are gone and foes are hurt.'),
 'null_step': ('Null Step', 'You stop existing for a moment and start again ten blocks on.'),
 'unmake': ('Unmake', 'The armour of the foe you aim at is erased for 8 s.'),
 'nullify': ('Nullify', 'Every foe within 6 blocks has its strength taken away: 30% weaker and slower for 6 s.'),
 'seam': ('Seam', 'Cut a line out of space; a moment later it snaps shut, dragging what is on either side in.'),
 'absence': ('Absence', '6 s: a sphere where nothing exists - missiles vanish in it, foes are worn away, and you take 40% less.'),
 'void_lance': ('Void Lance', 'A beam of nothing straight through everything in its path, unmaking their armour.'),
 'delete': ('Delete', 'Mark a foe: in two seconds it is deleted if it is below a quarter of its health - otherwise it takes a terrible blow.'),
 'emptiness': ('Emptiness', 'Mode, 20 s: you are part nothing yourself - Void spells hit 30% harder and erase twice as much.'),
 'origin_zero': ('Origin Zero', 'Let out all the Nothing you have kept: a sphere of white void that grows with it and erases everything inside.'),
})
spells('Luck Magic', {
 'coin_toss': ('Coin Toss', 'Flick a heavy coin at your target. Heads: it ricochets on to two more foes.'),
 'double_or_nothing': ('Double or Nothing', 'Stake a tenth of your health. Heads: win it back twice over and your next spell lands harder.'),
 'roulette': ('Roulette', 'A wheel spins under your target. Red: a burst of fire. Black: a curse - foes there take more.'),
 'chip_shot': ('Chip Shot', 'A stack of chips fanned out in front of you - eight on heads, five on tails.'),
 'lucky_seven': ('Lucky Seven', 'Seven coins rain onto your target\'s ground; each one that lands heads explodes.'),
 'slot_machine': ('Slot Machine', 'Three reels spin over your target and stop one by one: three of a kind pays big.'),
 'loaded_dice': ('Loaded Dice', 'Two dice tumble at your foes and hit for what they show; doubles roll again.'),
 'insurance': ('Insurance', '10 s: any blow bigger than a third of your health is paid out by the house.'),
 'all_in': ('All In', 'Mode, 20 s: every flip is double or nothing - heads lands at 2.2x, tails at 0.4x.'),
 'jackpot': ('Jackpot', 'A great slot machine opens over you and always pays - how much depends on your Luck.'),
})
spells('Fortune Magic', {
 'draw_card': ('Draw', 'Draw the next card from your deck: one of twenty-two effects, upright or reversed.'),
 'the_magician': ('The Magician', 'Four bolts, one of each suit - wand, cup, sword and coin - at your target.'),
 'the_tower': ('The Tower', 'A tower of light rises under your target, is struck, and falls on everything round it.'),
 'the_star': ('The Star', 'Stars fall round you for 4 s, mending you and your allies.'),
 'death_card': ('Death', 'A sweep of the scythe: foes below a third of their health take it three times over.'),
 'wheel_of_fortune': ('Wheel of Fortune', 'The wheel turns: all your other cooldowns run ahead by a random 5 to 15 s.'),
 'the_hanged_man': ('The Hanged Man', 'The foe you aim at is hung in the air, helpless, for 3 s, then dropped.'),
 'three_card_spread': ('Three-Card Spread', 'Draw three cards at once: past, present and future.'),
 'the_world': ('The World', 'Mode, 20 s: every card you draw is upright, and Tarot spells land harder.'),
 'judgement': ('Judgement', 'A great card opens in the sky over your target and light pours down from it.'),
})
spells('Abyss Magic', {
 'crushing_pressure': ('Crushing Pressure', 'The deep presses down where you aim: foes there are crushed and slowed - harder the deeper you are.'),
 'anglers_lure': ('Angler\'s Lure', 'A lantern of cold light hangs out ahead; foes nearby are drawn to it, then jaws close on it.'),
 'abyssal_grasp': ('Abyssal Grasp', 'Arms rise round the foe you aim at, hold it still and squeeze for three seconds.'),
 'undertow': ('Undertow', 'A current rolls forward, carrying foes away - then drags them back under you.'),
 'abyssal_dive': ('Abyssal Dive', 'Sink into black water and come up where you aim, jaws snapping shut round you.'),
 'depth_charge': ('Depth Charge', 'A slow, sinking orb; after a beat it bursts in a crushing ring.'),
 'leviathan': ('Leviathan', 'A vast shadow breaches out of the ground along a line, throwing foes into the air.'),
 'hadal_weight': ('Hadal Weight', 'The foe you aim at is made so heavy it cannot jump or be lifted, its armour bowed, for 6 s.'),
 'bioluminescence': ('Bioluminescence', 'Mode, 20 s: you glow like the deep\'s own creatures - Abyss spells hit 25% harder, every hit feeds you, and the dark lifts.'),
 'the_trench': ('The Trench', 'The ground where you aim falls away into a black trench (to the eye only); all near it are dragged down and a great maw closes. Spends all your Depth.'),
})
spells('Machine Magic', {
 'rivet_barrage': ('Rivet Barrage', 'Arms: six rivets punched at what you aim at, one after another.'),
 'thruster_dash': ('Thruster Dash', 'Thrusters: a burst of thrust forward, ramming through everything in the way.'),
 'rocket_fist': ('Rocket Fist', 'Fists: your fist flies out on a rocket, smashes what it meets and comes back.'),
 'piston_stomp': ('Piston Stomp', 'Legs: a piston-driven stomp jolts the ground round you and throws foes up.'),
 'bulwark_plating': ('Bulwark Plating', 'Plating: 6 s - armour plates lock round you and you take 40% less.'),
 'missile_swarm': ('Missile Swarm', 'Launchers: eight little missiles curl out and find the nearest foes.'),
 'overclock': ('Overclock', 'Core: 8 s - the core runs hot: faster, quicker hands, and it patches you up a little.'),
 'rail_cannon': ('Rail Cannon', 'Cannon: a moment\'s charge, then a slug straight through everything in a long line.'),
 'pilot': ('Pilot', 'Mode, 20 s: the machine stands round you with every part you have built, each giving its strength.'),
 'titan_buster': ('Titan Buster', 'A giant fist drops out of the sky where you aim - every part you have built goes into it.'),
})
spells('Weather Magic', {
 'sunbeam': ('Sunbeam', 'Sun: a ray of hard sunlight burns down on what you aim at, following your aim.'),
 'downpour': ('Downpour', 'Rain: a raincloud over where you aim - foes under it are soaked and slowed, you are refreshed.'),
 'thunder_strike': ('Thunder Strike', 'Storm: lightning on the foe you aim at - and on every soaked foe near it (soaked foes take 50% more).'),
 'hailstorm': ('Hailstorm', 'Hail: hail hammers an area for three seconds, slowing more with each stone.'),
 'gale': ('Gale', 'Wind: a wall of wind blows foes back and turns their arrows and fireballs round.'),
 'fog_bank': ('Fog Bank', 'Fog: 6 s - a fog rolls out round you; foes in it lose sight of you, and you take 25% less.'),
 'cyclone': ('Cyclone', 'Wind: a whirlwind crawls forward, sucking foes in and throwing them up.'),
 'rainbow': ('Rainbow', 'Sun: you and your allies are healed and cleansed (more after rain); foes under it are dazzled.'),
 'eye_of_the_storm': ('Eye of the Storm', 'Storm, mode, 20 s: a ring of storm turns round you, striking foes at its edge, while it is calm in the middle.'),
 'cataclysm': ('Cataclysm', 'Your forecast at its worst: heatwave, deluge, thunderstorm, blizzard, great tornado or whiteout.'),
})
json.dump(lang, open(LP, 'w'), indent=2, ensure_ascii=False)
print('eight grimoires: assets ok')
