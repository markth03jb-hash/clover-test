"""The Scarlet Moon's ice: icons for the ice versions of the Blood Grimoire's spells (shown in the wheel, the spell
bar and everywhere else while the mode runs). Original pixel art: cut ice in pale blue and white."""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from icons import make
I = ((58, 104, 160), (8, 18, 38), (186, 226, 255), (110, 168, 232))
def shard(x, y, h, k='m'):   # a crystal standing up from (x, y), h tall
    return [(k, 'g', [(x - 1, y), (x + 1, y), (x + 1, y - h + 2), (x, y - h), (x - 1, y - h + 2)]), ('w', 'l', x, y - 1, x, y - h + 1)]
make('ice_crimson_edge', *I, [('a', 'l', 1, 6, 9, 6), ('a', 'l', 0, 9, 8, 9), ('a', 'l', 2, 12, 9, 12)] + shard(12, 14, 10) + [('w', 'p', 12, 4)])
make('ice_hemorrhage', *I, [('m', 'g', [(2, 1), (5, 1), (3, 8)]), ('m', 'g', [(7, 2), (10, 2), (8, 11)]), ('m', 'g', [(11, 0), (14, 0), (12, 7)]), ('w', 'l', 3, 2, 3, 6), ('w', 'l', 8, 3, 8, 9), ('w', 'l', 12, 1, 12, 5), ('a', 'l', 0, 14, 15, 14), ('w', 'p', 8, 13)])
make('ice_sanguine_volley', *I, [('m', 'g', [(3, 2), (5, 5), (3, 8), (1, 5)]), ('m', 'g', [(12, 2), (14, 5), (12, 8), (10, 5)]), ('m', 'g', [(7, 7), (9, 10), (7, 13), (5, 10)]), ('w', 'p', 3, 4), ('w', 'p', 12, 4), ('w', 'p', 7, 9), ('a', 'l', 4, 6, 11, 6)])
make('ice_transfusion', *I, sum([shard(x, 14, 11, 'm' if x % 2 else 'a') for x in (2, 5, 8, 11, 14)], []) + [('w', 'l', 1, 6, 14, 6), ('w', 'l', 1, 11, 14, 11)])
make('ice_blood_pact', *I, [('m', 'a', 1, 1, 14, 14, 0, 270), ('a', 'a', 3, 3, 12, 12, 90, 360), ('w', 'a', 5, 5, 10, 10, 180, 450), ('w', 'p', 7, 7), ('m', 'p', 13, 3), ('m', 'p', 2, 12)])
make('ice_red_tide', *I, [('a', 'e', 2, 9, 13, 14), ('w', 'e', 4, 10, 11, 13)] + shard(7, 11, 10) + shard(2, 12, 5, 'a') + shard(13, 12, 5, 'a'))
make('ice_thorned_veins', *I, [('a', 'l', 1, 13, 4, 10), ('a', 'l', 4, 10, 8, 12), ('a', 'l', 8, 12, 11, 8)] + shard(4, 10, 4) + shard(8, 12, 5) + shard(11, 8, 4) + [('m', 'g', [(11, 8), (15, 4), (13, 8)]), ('w', 'p', 13, 5)])
make('ice_crimson_bulwark', *I, shard(2, 15, 9, 'a') + shard(5, 15, 12) + shard(8, 15, 14) + shard(11, 15, 12) + shard(14, 15, 9, 'a'))
make('ice_exsanguinate', *I, [('m', 'l', 7, 1, 7, 13), ('m', 'l', 1, 7, 13, 7), ('m', 'l', 3, 3, 11, 11), ('m', 'l', 11, 3, 3, 11), ('a', 'l', 5, 1, 7, 3), ('a', 'l', 9, 1, 7, 3), ('a', 'l', 5, 13, 7, 11), ('a', 'l', 9, 13, 7, 11), ('w', 'p', 7, 7)])
make('ice_scarlet_moon', *I, [('m', 'g', [(11, 1), (14, 4), (9, 11), (6, 8)]), ('w', 'l', 12, 2, 8, 9), ('a', 'l', 7, 9, 2, 14), ('a', 'l', 6, 8, 1, 12), ('a', 'l', 8, 10, 4, 15), ('w', 'l', 0, 15, 15, 15)])
print('ice icons written')
