import struct, gzip, math, random, json, sys
random.seed(20261109)
NS = 'iron_spell_grimoire'
# ---------------------------------------------------------------- tiny NBT writer
def s_str(s): b = s.encode('utf-8'); return struct.pack('>H', len(b)) + b
def tag(t, name, payload): return bytes([t]) + s_str(name) + payload
def p_byte(v): return struct.pack('>b', v)
def p_int(v): return struct.pack('>i', v)
def p_float(v): return struct.pack('>f', v)
def p_double(v): return struct.pack('>d', v)
def p_str(v): return s_str(v)
def p_list(t, items): return bytes([t]) + struct.pack('>i', len(items)) + b''.join(items)
def p_comp(entries): return b''.join(entries) + b'\x00'

W = D = 33; C = 16
FD = 6                      # foundation depth below the ground floor
H = FD + 62
blocks = {}
def put(x, y, z, name, props=None):
    if 0 <= x < W and 0 <= y < H and 0 <= z < D: blocks[(x, y, z)] = (name, props or {})
def get(x, y, z): return blocks.get((x, y, z), ('', {}))[0]
def dist(x, z): return math.hypot(x - C, z - C)
def ang(x, z): return math.atan2(z - C, x - C)
def adiff(a, b): return abs((a - b + math.pi) % (2 * math.pi) - math.pi)
def facing_out(th):
    fx, fz = math.cos(th), math.sin(th)
    return ('east' if fx > 0 else 'west') if abs(fx) > abs(fz) else ('south' if fz > 0 else 'north')
OPP = {'east': 'west', 'west': 'east', 'north': 'south', 'south': 'north'}

def aged(y):
    r = random.random()
    if y < FD + 6 and r < 0.25: return 'minecraft:mossy_stone_bricks'
    if r < 0.10: return 'minecraft:mossy_stone_bricks'
    if r < 0.20: return 'minecraft:cracked_stone_bricks'
    if r < 0.24: return 'minecraft:andesite'
    return 'minecraft:stone_bricks'

G = FD                      # the ground floor level (local y)
R_BASE_O, R_BASE_I = 14.2, 11.6     # lower tier wall
R_UP_O, R_UP_I = 12.2, 10.6         # upper tower wall
R_HALL = 10.6
TIER = G + 12               # where the lower tier ends (an outside walkway round the tower)
CEIL = G + 30               # the hall's ceiling / the roof's base
DOOR_TH = math.pi / 2       # the door faces south (+z)

# ---------------------------------------------------------------- foundation, floor
for x in range(W):
    for z in range(D):
        d = dist(x, z)
        if d <= R_BASE_O + 0.3:
            for y in range(0, G):
                put(x, y, z, 'minecraft:stone' if y < G - 1 else 'minecraft:stone_bricks')
# floor of the hall: smooth stone rings, a great five-leaf clover inlaid in black and gold round the dais
D_, OFF, RAD, GAP = 0.545, 0.105, 0.248, 0.03
def clover_in(u, v, grow=0.0):
    r = math.hypot(u, v)
    if r < 1e-6: return True
    a = math.atan2(v, u) + math.pi / 2
    step = 2 * math.pi / 5; k = round(a / step); phi = a - k * step
    if r * math.sin(step / 2 - abs(phi)) < GAP / 2 - grow and r > 0.06: return False
    best = 0
    for s in (1, -1):
        cx, cy = D_, OFF * s; b = math.cos(phi) * cx + math.sin(phi) * cy
        disc = b * b - (cx * cx + cy * cy - RAD * RAD)
        if disc >= 0: best = max(best, b + math.sqrt(disc))
    return r <= best + grow
CR = 8.5
for x in range(W):
    for z in range(D):
        d = dist(x, z)
        if d < R_BASE_I + 0.5:
            u, v = (x - C) / CR, (z - C) / CR
            if clover_in(u, v): b = 'minecraft:polished_blackstone'
            elif clover_in(u, v, 0.09): b = 'minecraft:gilded_blackstone'
            elif 9.55 <= d <= 10.15: b = 'minecraft:gilded_blackstone'          # the outer ring of the circle
            elif 8.6 <= d < 9.55:                                                # a ring of runes, glowing between dark stones
                seg = int((ang(x, z) + math.pi) / (2 * math.pi) * 30)
                b = 'minecraft:ochre_froglight' if seg % 3 == 0 else 'minecraft:polished_blackstone_bricks'
            elif d > R_HALL - 1.5: b = 'minecraft:polished_deepslate'
            else: b = 'minecraft:smooth_stone' if int(d) % 3 else 'minecraft:polished_andesite'
            put(x, G, z, b)

# ---------------------------------------------------------------- walls
for x in range(W):
    for z in range(D):
        d = dist(x, z); th = ang(x, z)
        for y in range(G + 1, CEIL + 1):
            lower = y <= TIER
            ro, ri = (R_BASE_O, R_BASE_I) if lower else (R_UP_O, R_UP_I)
            if ri < d <= ro:
                b = aged(y)
                if y in (G + 1, TIER, G + 21, CEIL): b = 'minecraft:deepslate_bricks'
                # the great door: an arch 5 wide, 7 tall
                if lower and adiff(th, DOOR_TH) < 0.2 and y <= G + 6 + (0 if adiff(th, DOOR_TH) < 0.12 else -1): b = 'minecraft:air'
                # windows: tall arched panels of violet and blue glass
                rows = ((G + 4, G + 8, 8, math.pi / 8),) if lower else ((G + 14, G + 19, 8, 0.0), (G + 23, G + 27, 12, math.pi / 12))
                for y0, y1, n, off in rows:
                    if y0 <= y <= y1:
                        for k in range(n):
                            a = off + k * 2 * math.pi / n
                            if lower and adiff(a, DOOR_TH) < 0.4: continue
                            w = 0.075 if y == y1 else 0.11
                            if adiff(th, a) < w:
                                b = random.choice(['minecraft:purple_stained_glass', 'minecraft:blue_stained_glass', 'minecraft:magenta_stained_glass', 'minecraft:light_blue_stained_glass'])
                                if d > ro - 0.8 and y == y1 + 0: pass
                if y == y1 + 1 if False else False: pass
                put(x, y, z, b)
            elif d <= ri:
                put(x, y, z, 'minecraft:air')
            elif lower is False and d <= R_BASE_O and y == TIER + 1:
                pass
# the rose window above the great door: a five-leaf clover of gold glass in violet, ringed in black stone
for x in range(W):
    for z in range(D):
        d = dist(x, z); th = ang(x, z)
        if not (R_BASE_I < d <= R_BASE_O) or adiff(th, DOOR_TH) > 0.4: continue
        lat = (th - DOOR_TH) * d
        for y in range(G + 7, TIER):
            r = math.hypot(lat, (y + 0.5) - (G + 9.6))
            if r <= 2.4:
                u, v = lat / 2.4, ((y + 0.5) - (G + 9.6)) / 2.4
                put(x, y, z, 'minecraft:yellow_stained_glass' if clover_in(u * 1.15, -v * 1.15, 0.02) else 'minecraft:purple_stained_glass')
            elif r <= 3.1: put(x, y, z, 'minecraft:polished_blackstone_bricks')
# the great door's frame: black stone with a gold keystone, and lamps either side
for x in range(W):
    for z in range(D):
        d = dist(x, z); th = ang(x, z)
        if R_BASE_O - 0.6 < d <= R_BASE_O + 0.4 and 0.2 <= adiff(th, DOOR_TH) < 0.3:
            for y in range(G + 1, G + 7): put(x, y, z, 'minecraft:polished_blackstone_bricks')
for x in range(C - 2, C + 3):
    zz = max(z for z in range(D) if dist(x, z) <= R_BASE_O + 0.3)
    put(x, G + 7, zz, 'minecraft:gilded_blackstone' if x == C else 'minecraft:polished_blackstone_bricks')
for x in (C - 4, C + 4):
    zz = max(z for z in range(D) if dist(x, z) <= R_BASE_O + 0.3) + 1
    put(x, G + 1, zz, 'minecraft:polished_blackstone_wall', {'up': 'true', 'north': 'none', 'south': 'none', 'east': 'none', 'west': 'none', 'waterlogged': 'false'})
    put(x, G + 2, zz, 'minecraft:polished_blackstone_wall', {'up': 'true', 'north': 'none', 'south': 'none', 'east': 'none', 'west': 'none', 'waterlogged': 'false'})
    put(x, G + 3, zz, 'minecraft:soul_lantern', {'hanging': 'false', 'waterlogged': 'false'})
# buttresses round the lower tier, eight of them, stepping in as they rise
for k in range(8):
    a = math.pi / 8 + k * math.pi / 4
    if adiff(a, DOOR_TH) < 0.3: continue
    for x in range(W):
        for z in range(D):
            d = dist(x, z); th = ang(x, z)
            if adiff(th, a) * d > 1.3 or d < R_BASE_O - 0.5: continue
            for y in range(G, TIER + 1):
                reach = R_BASE_O + 2.2 - (y - G) * 0.18
                if d <= reach: put(x, y, z, 'minecraft:deepslate_bricks' if y in (G, TIER) else aged(y))
            # cap
            if d <= R_BASE_O + 2.2 - (TIER - G) * 0.18 + 0.4:
                put(x, TIER + 1, z, 'minecraft:stone_brick_slab', {'type': 'bottom', 'waterlogged': 'false'})
# the walkway on top of the lower tier, with merlons round the edge
for x in range(W):
    for z in range(D):
        d = dist(x, z); th = ang(x, z)
        if R_UP_O < d <= R_BASE_O:
            put(x, TIER, z, 'minecraft:polished_andesite' if d < R_BASE_O - 1 else 'minecraft:deepslate_bricks')
            if d > R_BASE_O - 1.1:
                put(x, TIER + 1, z, aged(TIER) if (int((th + math.pi) / (2 * math.pi) * 40) % 2 == 0) else 'minecraft:air')
            else:
                put(x, TIER + 1, z, 'minecraft:air')
                if random.random() < 0.18: put(x, TIER + 1, z, 'minecraft:moss_carpet')
            put(x, TIER + 2, z, 'minecraft:air'); put(x, TIER + 3, z, 'minecraft:air')
# a doorway onto the walkway from inside (from the first balcony)
for y in range(TIER + 1, TIER + 4):
    for x in range(W):
        for z in range(D):
            d = dist(x, z)
            if R_UP_I < d <= R_UP_O + 0.2 and adiff(ang(x, z), -math.pi / 2) < 0.12: put(x, y, z, 'minecraft:air')

# ---------------------------------------------------------------- inside: balconies, the spiral stair, shelves
BAL = (G + 12, G + 21)       # balcony floors (the first one meets the outside walkway)
GALLERY = G + 27
RAMP_I = 8.6
def ring_in(d, ri): return RAMP_I <= d <= ri
for x in range(W):
    for z in range(D):
        d = dist(x, z); th = ang(x, z)
        ri = R_BASE_I if True else R_UP_I
        for by in BAL + (GALLERY,):
            wall_i = R_BASE_I if by <= TIER else R_UP_I
            if RAMP_I - 0.2 <= d <= wall_i + 0.2:
                put(x, by, z, 'minecraft:spruce_planks' if d > RAMP_I + 0.6 else 'minecraft:dark_oak_planks')
                if d < RAMP_I + 0.7 and random.random() < 0.6:
                    put(x, by + 1, z, 'minecraft:dark_oak_fence', {'north': 'false', 'south': 'false', 'east': 'false', 'west': 'false', 'waterlogged': 'false'})
                elif d >= wall_i - 0.8 and by + 1 < CEIL:
                    for yy in range(by + 1, by + 4):
                        if get(x, yy, z) == 'minecraft:air' and yy < by + 4: put(x, yy, z, 'minecraft:bookshelf')
# the spiral stair, climbing the wall from the floor to the gallery, landing on each balcony
REV = 2.0
def ramp_h(th):
    t = ((th - DOOR_TH - 0.5) % (2 * math.pi)) / (2 * math.pi)
    return t
for x in range(W):
    for z in range(D):
        d = dist(x, z)
        if not (RAMP_I - 0.3 <= d <= RAMP_I + 1.9): continue
        th = ang(x, z)
        base = ((th - DOOR_TH - 0.6) % (2 * math.pi)) / (2 * math.pi)
        for lap in range(3):
            f = (base + lap) / 2.6
            if f > 1: continue
            h = G + 1 + f * (GALLERY - G - 1)
            y = int(h)
            if y > GALLERY: continue
            tx, tz = -math.sin(th), math.cos(th)
            fdir = ('east' if tx > 0 else 'west') if abs(tx) > abs(tz) else ('south' if tz > 0 else 'north')
            if y in BAL or y == GALLERY: continue
            if h - y < 0.5: put(x, y, z, 'minecraft:stone_brick_stairs', {'facing': fdir, 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'})
            else: put(x, y, z, 'minecraft:stone_bricks')
            if get(x, y - 1, z) == 'minecraft:air' and y - 1 > G: put(x, y - 1, z, 'minecraft:stone_brick_slab', {'type': 'top', 'waterlogged': 'false'})
            for yy in range(y + 1, y + 4):   # headroom through the balconies above
                if get(x, yy, z) in ('minecraft:spruce_planks', 'minecraft:dark_oak_planks', 'minecraft:bookshelf', 'minecraft:dark_oak_fence'): put(x, yy, z, 'minecraft:air')

# ---------------------------------------------------------------- the ceremony at the heart of the hall
# a stepped dais of black stone and gold: a wide first step, a chiselled ring, and the top where the altar stands,
# ringed by amethyst; five pedestals round it carry floating grimoires (item displays, below), with lamps between
for x in range(W):
    for z in range(D):
        d = dist(x, z)
        if 3.6 < d <= 4.6: put(x, G + 1, z, 'minecraft:polished_blackstone_brick_slab', {'type': 'bottom', 'waterlogged': 'false'})
        elif 2.6 < d <= 3.6: put(x, G + 1, z, 'minecraft:chiseled_polished_blackstone' if int((ang(x, z) + math.pi) * 3) % 2 else 'minecraft:gilded_blackstone')
        elif d <= 2.6: put(x, G + 1, z, 'minecraft:polished_deepslate')
        if 1.7 < d <= 2.6: put(x, G + 2, z, 'minecraft:polished_blackstone_brick_slab', {'type': 'bottom', 'waterlogged': 'false'})
        elif d <= 1.7: put(x, G + 2, z, 'minecraft:gold_block' if d > 0.5 else 'minecraft:polished_deepslate')
put(C, G + 3, C, NS + ':grimoire_altar')
for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
    put(C + dx, G + 3, C + dz, 'minecraft:amethyst_cluster', {'facing': 'up', 'waterlogged': 'false'})
PEDESTALS = []
for k in range(5):   # five pedestals round the dais, one for each leaf of the clover
    a = -math.pi / 2 + k * 2 * math.pi / 5
    x, z = int(round(C + math.cos(a) * 6.2)), int(round(C + math.sin(a) * 6.2))
    put(x, G + 1, z, 'minecraft:chiseled_polished_blackstone')
    put(x, G + 2, z, 'minecraft:polished_blackstone_wall', {'up': 'true', 'north': 'none', 'south': 'none', 'east': 'none', 'west': 'none', 'waterlogged': 'false'})
    put(x, G + 3, z, 'minecraft:gilded_blackstone')
    put(x, G + 4, z, 'minecraft:amethyst_cluster', {'facing': 'up', 'waterlogged': 'false'})
    PEDESTALS.append((x + 0.5, G + 5.6, z + 0.5, a))
    x2, z2 = int(round(C + math.cos(a + 0.63) * 7.2)), int(round(C + math.sin(a + 0.63) * 7.2))
    put(x2, G + 1, z2, 'minecraft:polished_blackstone_wall', {'up': 'true', 'north': 'none', 'south': 'none', 'east': 'none', 'west': 'none', 'waterlogged': 'false'})
    put(x2, G + 2, z2, 'minecraft:soul_lantern', {'hanging': 'false', 'waterlogged': 'false'})
# the Grimoire Archive: a wall of black-and-gold shelves on the ground floor opposite the door, under the stair,
# where the Grimoire Library is read (server/GrimoireLibrary) - every mage the tower's grimoires have chosen
for x in range(C - 1, C + 2):
    for y in (G + 1, G + 2):
        put(x, y, C - 11, NS + ':grimoire_archive', {'facing': 'south'})
    put(x, G + 3, C - 11, 'minecraft:gilded_blackstone' if x == C else 'minecraft:polished_blackstone_bricks')
for x in (C - 2, C + 2):
    put(x, G + 1, C - 11, 'minecraft:polished_blackstone_wall', {'up': 'true', 'north': 'none', 'south': 'none', 'east': 'none', 'west': 'none', 'waterlogged': 'false'})
    put(x, G + 2, C - 11, 'minecraft:soul_lantern', {'hanging': 'false', 'waterlogged': 'false'})
# chandeliers: a great ring of lanterns and a central chain down the atrium
for y in range(G + 16, CEIL):
    put(C, y, C, 'minecraft:chain', {'axis': 'y', 'waterlogged': 'false'})
put(C, G + 15, C, 'minecraft:lantern', {'hanging': 'true', 'waterlogged': 'false'})
for k in range(8):
    a = k * math.pi / 4
    x, z = int(round(C + math.cos(a) * 5)), int(round(C + math.sin(a) * 5))
    for y in range(G + 25, CEIL): put(x, y, z, 'minecraft:chain', {'axis': 'y', 'waterlogged': 'false'})
    put(x, G + 24, z, 'minecraft:lantern', {'hanging': 'true', 'waterlogged': 'false'})

# ---------------------------------------------------------------- the roof: a broad cone of dark tiles with flared eaves and a spire
for x in range(W):
    for z in range(D):
        d = dist(x, z)
        if d <= R_UP_O:
            b = 'minecraft:dark_oak_planks' if d < R_UP_I else 'minecraft:deepslate_bricks'
            if d < R_UP_I:   # the ceiling's magic circle, mirroring the floor: gold rings, five spokes, lamps at their ends
                th = ang(x, z); spoke = min(adiff(th, -math.pi / 2 + k * 2 * math.pi / 5) for k in range(5)) * d
                if d <= 1.2: b = 'minecraft:shroomlight'
                elif 2.4 <= d <= 3.1 or 6.0 <= d <= 6.7 or 9.4 <= d <= 10.1: b = 'minecraft:gilded_blackstone'
                elif spoke < 0.55 and 3.1 < d < 9.4: b = 'minecraft:polished_blackstone_bricks' if not 7.2 <= d <= 8.2 else 'minecraft:shroomlight'
                elif 6.7 < d < 9.4 and int((th + math.pi) / (2 * math.pi) * 40) % 4 == 0: b = 'minecraft:ochre_froglight'
            put(x, CEIL, z, b)
        if R_UP_O < d <= R_UP_O + 1.2:
            put(x, CEIL, z, 'minecraft:deepslate_tile_stairs', {'facing': OPP[facing_out(ang(x, z))], 'half': 'top', 'shape': 'straight', 'waterlogged': 'false'})
roof_top = CEIL
for k in range(1, 20):
    r = R_UP_O + 1.0 - k * 1.3
    y = CEIL + k
    if r < 0.8:
        put(C, y, C, 'minecraft:deepslate_tiles'); roof_top = y; break
    for x in range(W):
        for z in range(D):
            d = dist(x, z)
            if r - 1.3 < d <= r: put(x, y, z, 'minecraft:deepslate_tile_stairs' if d > r - 0.6 else 'minecraft:deepslate_tiles',
                                    {'facing': OPP[facing_out(ang(x, z))], 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'} if d > r - 0.6 else None)
            elif d <= r - 1.3: put(x, y, z, 'minecraft:air')
    roof_top = y
for y in range(roof_top + 1, roof_top + 6): put(C, y, C, 'minecraft:deepslate_tile_wall', {'up': 'true', 'north': 'none', 'south': 'none', 'east': 'none', 'west': 'none', 'waterlogged': 'false'})
put(C, roof_top + 6, C, 'minecraft:gold_block')
put(C, roof_top + 7, C, 'minecraft:lightning_rod', {'facing': 'up', 'powered': 'false', 'waterlogged': 'false'})
HALO_Y = roof_top + 3
for x in range(W):
    for z in range(D):
        d = dist(x, z)
        if 3.3 <= d <= 4.1:   # a ring of gold hanging in the air round the spire, set with five lamps
            th = ang(x, z)
            lamp = min(adiff(th, -math.pi / 2 + k * 2 * math.pi / 5) for k in range(5)) * d < 0.6
            put(x, HALO_Y, z, 'minecraft:ochre_froglight' if lamp else 'minecraft:gilded_blackstone')
for k in range(8):   # pinnacles on the buttresses, each with a lamp
    a = math.pi / 8 + k * math.pi / 4
    if adiff(a, DOOR_TH) < 0.3: continue
    x, z = int(round(C + math.cos(a) * (R_BASE_O + 0.9))), int(round(C + math.sin(a) * (R_BASE_O + 0.9)))
    for y in range(TIER + 1, TIER + 4): put(x, y, z, 'minecraft:deepslate_tile_wall', {'up': 'true', 'north': 'none', 'south': 'none', 'east': 'none', 'west': 'none', 'waterlogged': 'false'})
    put(x, TIER + 4, z, 'minecraft:soul_lantern', {'hanging': 'false', 'waterlogged': 'false'})

# ---------------------------------------------------------------- blending in: steps, rubble, moss and ivy
for x in range(W):
    for z in range(D):
        d = dist(x, z); th = ang(x, z)
        if R_BASE_O < d <= R_BASE_O + 1.0 and get(x, G, z) == '':
            put(x, G, z, 'minecraft:stone_brick_stairs', {'facing': OPP[facing_out(th)], 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'})
# steps up to the door
for z in range(C + 14, D):
    for x in range(C - 2, C + 3): put(x, G, z, 'minecraft:mossy_stone_bricks' if random.random() < 0.3 else 'minecraft:stone_bricks')
# ivy climbing the outside walls
for x in range(W):
    for z in range(D):
        d = dist(x, z); th = ang(x, z)
        for ro, y0, y1 in ((R_BASE_O, G + 1, TIER), (R_UP_O, TIER + 1, CEIL - 1)):
            if ro < d <= ro + 1.0 and random.random() < 0.22:
                f = facing_out(th)           # the wall is on the inner side of this cell
                side = OPP[f]
                top = y0 + random.randint(2, max(3, (y1 - y0) // 2))
                for y in range(y0, min(y1, top)):
                    if get(x, y, z) in ('',):
                        props = {'north': 'false', 'south': 'false', 'east': 'false', 'west': 'false', 'up': 'false'}
                        props[side] = 'true'
                        put(x, y, z, 'minecraft:vine', props)
# moss and a few cobwebs inside
for (x, y, z), (n, p) in list(blocks.items()):
    if n == 'minecraft:air' and get(x, y - 1, z) in ('minecraft:smooth_stone', 'minecraft:polished_andesite', 'minecraft:spruce_planks') and random.random() < 0.03:
        put(x, y, z, 'minecraft:moss_carpet')
    elif n == 'minecraft:air' and y == CEIL - 1 and random.random() < 0.02:
        put(x, y, z, 'minecraft:cobweb')

# ---------------------------------------------------------------- grimoires floating through the hall (item displays)
entities = []
books = ['black_grimoire', 'dark_grimoire', 'wind_grimoire', 'mercury_grimoire', 'fire_grimoire', 'lightning_grimoire',
         'hellfire_grimoire', 'bone_grimoire', 'steel_grimoire', 'water_grimoire', 'mirror_grimoire', 'earth_grimoire',
         'time_grimoire', 'light_grimoire', 'slash_grimoire']
plain = ['minecraft:book', 'minecraft:enchanted_book', 'minecraft:writable_book', 'minecraft:paper', 'minecraft:paper']
ENTS = []
def display(item, x, y, z, s, yaw, pitch, light=12):
    ENTS.append((x, y, z, s))
    nbt = p_comp([
        tag(8, 'id', p_str('minecraft:item_display')),
        tag(10, 'item', p_comp([tag(8, 'id', p_str(item)), tag(1, 'Count', p_byte(1))])),
        tag(10, 'transformation', p_comp([
            tag(9, 'left_rotation', p_list(5, [p_float(0), p_float(0), p_float(0), p_float(1)])),
            tag(9, 'right_rotation', p_list(5, [p_float(0), p_float(0), p_float(0), p_float(1)])),
            tag(9, 'translation', p_list(5, [p_float(0), p_float(0), p_float(0)])),
            tag(9, 'scale', p_list(5, [p_float(s), p_float(s), p_float(s)]))])),
        tag(10, 'brightness', p_comp([tag(3, 'block', p_int(light)), tag(3, 'sky', p_int(light))])),
        tag(9, 'Rotation', p_list(5, [p_float(yaw), p_float(pitch)])),
        tag(9, 'Tags', p_list(8, [p_str('grimoire_tower_book')])),
    ])
    entities.append(p_comp([
        tag(9, 'pos', p_list(6, [p_double(x), p_double(y), p_double(z)])),
        tag(9, 'blockPos', p_list(3, [p_int(int(math.floor(x))), p_int(int(math.floor(y))), p_int(int(math.floor(z)))])),
        tag(10, 'nbt', nbt)]))
# the cyclone: three spiralling streams of books and loose pages, widening as they rise from the altar to the ceiling
for strand in range(3):
    n = 44
    for i in range(n):
        t = i / (n - 1)
        a = strand * 2 * math.pi / 3 + t * 4.2 * math.pi + random.uniform(-0.08, 0.08)
        r = 2.4 + t * 5.6 + random.uniform(-0.3, 0.3)
        y = G + 4.5 + t * (CEIL - G - 8) + random.uniform(-0.3, 0.3)
        x, z = C + 0.5 + math.cos(a) * r, C + 0.5 + math.sin(a) * r
        item = (NS + ':' + random.choice(books)) if random.random() < 0.28 else random.choice(plain)
        yaw = math.degrees(a) + 90 + random.uniform(-25, 25)   # turned along the stream
        display(item, x, y, z, random.uniform(0.45, 0.7), yaw, random.uniform(-30, 30), 13)
# a few strays drifting near the walls and balconies
for i in range(16):
    a = random.uniform(0, 2 * math.pi); r = random.uniform(7.5, 9.5)
    display(random.choice(plain), C + 0.5 + math.cos(a) * r, random.uniform(G + 3, CEIL - 3), C + 0.5 + math.sin(a) * r, random.uniform(0.4, 0.55), random.uniform(0, 360), random.uniform(-20, 20))
# the five grimoires waiting over their pedestals
for (x, y, z, a), b in zip(PEDESTALS, ['fire_grimoire', 'wind_grimoire', 'water_grimoire', 'light_grimoire', 'black_grimoire']):
    display(NS + ':' + b, x, y, z, 0.9, math.degrees(a) + 90, -15, 15)
# a great grimoire hanging open over the spire, facing the door
display(NS + ':black_grimoire', C + 0.5, HALO_Y + 9.0, C + 0.5, 6.0, 0, -10, 15)
# ---------------------------------------------------------------- write
pal, idx = [], {}
def key(v): return (v[0], tuple(sorted((v[1] or {}).items())))
for v in blocks.values():
    k = key(v)
    if k not in idx: idx[k] = len(pal); pal.append(k)
pal_nbt = []
for name, props in pal:
    e = [tag(8, 'Name', p_str(name))]
    if props: e.append(tag(10, 'Properties', p_comp([tag(8, k, p_str(v)) for k, v in props])))
    pal_nbt.append(p_comp(e))
blk_nbt = [p_comp([tag(9, 'pos', p_list(3, [p_int(x), p_int(y), p_int(z)])), tag(3, 'state', p_int(idx[key(v)]))]) for (x, y, z), v in sorted(blocks.items())]
root = tag(10, '', p_comp([
    tag(3, 'DataVersion', p_int(3465)),
    tag(9, 'size', p_list(3, [p_int(W), p_int(H), p_int(D)])),
    tag(9, 'palette', p_list(10, pal_nbt)),
    tag(9, 'blocks', p_list(10, blk_nbt)),
    tag(9, 'entities', p_list(10, entities)),
]))
with gzip.open(sys.argv[1], 'wb') as f: f.write(root)
import os
if os.path.isdir('/tmp/claude-0/tower'):   # for the preview renderer
    json.dump({'%d,%d,%d' % k: v[0].split(':')[1] for k, v in blocks.items()}, open('/tmp/claude-0/tower/blocks.json', 'w'))
    json.dump(ENTS, open('/tmp/claude-0/tower/ents.json', 'w'))
print('blocks', len(blocks), 'palette', len(pal), 'roof top', roof_top + 3 - G, 'above ground; size', W, H, D)
