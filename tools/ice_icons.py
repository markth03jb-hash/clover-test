"""The Scarlet Moon's ice: icons for the ice versions of the Blood Grimoire's spells (shown in the wheel, the spell
bar and everywhere else while the mode runs). Original pixel art: cut ice in pale blue and white."""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from icons import make
I = ((58, 104, 160), (8, 18, 38), (186, 226, 255), (110, 168, 232))
def shard(x, y, h, k='m'):   # a crystal standing up from (x, y), h tall
    return [(k, 'g', [(x - 1, y), (x + 1, y), (x + 1, y - h + 2), (x, y - h), (x - 1, y - h + 2)]), ('w', 'l', x, y - 1, x, y - h + 1)]
make('ice_crimson_edge', *I, [('m', 'a', 1, 1, 15, 15, 200, 340), ('m', 'a', 2, 2, 14, 14, 200, 340), ('a', 'a', 3, 3, 13, 13, 205, 335), ('w', 'p', 13, 4), ('w', 'p', 3, 4)])
make('ice_hemorrhage', *I, [('m', 'l', 2, 13, 11, 4), ('w', 'l', 3, 12, 10, 5), ('m', 'g', [(10, 3), (13, 2), (12, 5)]), ('a', 'R', 9, 7, 14, 14), ('w', 'p', 11, 10)])
make('ice_sanguine_volley', *I, sum([[('m', 'l', 1, 8, 13, 8 + o), ('w', 'p', 14, 8 + o)] for o in (-6, -3, 0, 3, 6)], []))
make('ice_transfusion', *I, [('a', 'l', 1, 8, 14, 8), ('w', 'l', 1, 8, 14, 8), ('m', 'l', 2, 6, 13, 6), ('m', 'l', 2, 10, 13, 10), ('w', 'p', 5, 6), ('w', 'p', 9, 10), ('m', 'R', 11, 5, 14, 11)])
make('ice_blood_pact', *I, [('a', 'e', 2, 2, 13, 13), ('m', 'g', [(7, 1), (9, 3), (7, 5), (5, 3)]), ('m', 'g', [(7, 10), (9, 12), (7, 14), (5, 12)]), ('m', 'g', [(1, 7), (3, 5), (5, 7), (3, 9)]), ('m', 'g', [(10, 7), (12, 5), (14, 7), (12, 9)]), ('w', 'p', 7, 7)])
make('ice_red_tide', *I, shard(3, 14, 7) + shard(7, 14, 11) + shard(11, 14, 9) + shard(14, 14, 5, 'a') + [('a', 'l', 0, 14, 15, 14)])
make('ice_thorned_veins', *I, shard(4, 14, 9, 'a') + shard(8, 14, 13) + shard(12, 14, 10, 'a') + [('w', 'l', 0, 15, 15, 15)])
make('ice_crimson_bulwark', *I, [('m', 'a', 1, 3, 15, 25, 180, 360), ('a', 'a', 3, 5, 13, 23, 180, 360), ('w', 'l', 4, 6, 6, 5), ('m', 'l', 0, 14, 15, 14), ('w', 'p', 8, 4)])
make('ice_exsanguinate', *I, [('m', 'l', 7, 1, 7, 13), ('m', 'l', 1, 7, 13, 7), ('m', 'l', 3, 3, 11, 11), ('m', 'l', 11, 3, 3, 11), ('a', 'l', 5, 1, 7, 3), ('a', 'l', 9, 1, 7, 3), ('a', 'l', 5, 13, 7, 11), ('a', 'l', 9, 13, 7, 11), ('w', 'p', 7, 7)])
make('ice_scarlet_moon', *I, [('m', 'g', [(11, 1), (14, 4), (9, 11), (6, 8)]), ('w', 'l', 12, 2, 8, 9), ('a', 'l', 7, 9, 2, 14), ('a', 'l', 6, 8, 1, 12), ('a', 'l', 8, 10, 4, 15), ('w', 'l', 0, 15, 15, 15)])
print('ice icons written')
