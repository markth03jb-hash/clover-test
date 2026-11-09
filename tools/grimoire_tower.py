import struct, gzip, math, random, json, sys
random.seed(1213)

# ---------------------------------------------------------------- tiny NBT writer
def s_str(s): b = s.encode('utf-8'); return struct.pack('>H', len(b)) + b
def tag(t, name, payload): return bytes([t]) + s_str(name) + payload
def p_int(v): return struct.pack('>i', v)
def p_str(v): return s_str(v)
def p_list(t, items): return bytes([t]) + struct.pack('>i', len(items)) + b''.join(items)
def p_comp(entries): return b''.join(entries) + b'\x00'

W, H, D = 15, 44, 15
C = 7  # centre
blocks = {}   # (x,y,z) -> (name, props)
def put(x, y, z, name, props=None):
    if 0 <= x < W and 0 <= y < H and 0 <= z < D: blocks[(x, y, z)] = (name, props or {})

def wall_block(y):
    if y in (12, 20): return 'minecraft:deepslate_bricks'
    if y == 26: return 'minecraft:chiseled_stone_bricks'
    r = random.random()
    return 'minecraft:mossy_stone_bricks' if r < 0.15 else ('minecraft:cracked_stone_bricks' if r < 0.27 else 'minecraft:stone_bricks')

RO, RI = 6.6, 5.4         # wall outer / inner radius
TOP = 26                   # the ceremony room floor
ROOF = 31
STEPS = 25
TH0 = math.radians(115)    # stairs start just left of the door

def ang(x, z):
    return math.atan2(z - C, x - C)

def facing_for(theta):
    tx, tz = -math.sin(theta), math.cos(theta)    # walking direction (theta increasing)
    if abs(tx) > abs(tz): return 'east' if tx > 0 else 'west'
    return 'south' if tz > 0 else 'north'

def step_of(x, z):
    """which stair step (1..STEPS) this cell is on, or 0"""
    d = math.hypot(x - C, z - C)
    if not (3.2 < d < 5.2): return 0
    a = (ang(x, z) - TH0) % (2 * math.pi)
    k = int(a / (2 * math.pi) * STEPS) + 1
    return k

for x in range(W):
    for z in range(D):
        d = math.hypot(x - C, z - C)
        th = ang(x, z)
        # foundation and ground floor
        if d <= RO:
            put(x, 0, z, 'minecraft:stone_bricks' if d > RI else ('minecraft:polished_andesite' if (x + z) % 2 else 'minecraft:polished_diorite'))
        for y in range(1, ROOF):
            if d <= RO and d > RI:            # the wall
                door = (y <= 3 and abs(x - C) <= 1 and z > C)                              # entrance, facing south
                win = False
                for wy, n, off in ((8, 4, 0.0), (16, 4, math.pi / 4), (23, 4, 0.0), (28, 8, math.pi / 8)):
                    if wy <= y <= wy + 1 + (1 if wy == 28 else 0):
                        for k in range(n):
                            a = off + k * 2 * math.pi / n
                            if abs((th - a + math.pi) % (2 * math.pi) - math.pi) < 0.17: win = True
                put(x, y, z, 'minecraft:air' if (door or win) else wall_block(y))
            elif d <= RI:                      # inside
                put(x, y, z, 'minecraft:air')
        # bookshelves lining the inner wall between the windows
        if RI - 1.0 < d <= RI:
            for y in list(range(4, 8)) + list(range(13, 16)) + list(range(21, 23)):
                if step_of(x, z) == 0 or True:
                    pass
        # the ceremony room floor (with the opening the stairs come up through)
        if d <= RI:
            k = step_of(x, z)
            if k >= STEPS - 3: put(x, TOP, z, 'minecraft:air')
            else: put(x, TOP, z, 'minecraft:polished_deepslate' if d > 2.2 else ('minecraft:chiseled_deepslate' if d > 1.2 else 'minecraft:gold_block'))

# the spiral stair round the inside, one step up per cell of angle, floating around an open atrium
for x in range(W):
    for z in range(D):
        k = step_of(x, z)
        if k == 0: continue
        y = k
        theta = TH0 + (k - 0.5) / STEPS * 2 * math.pi
        if y < TOP:
            put(x, y, z, 'minecraft:stone_brick_stairs', {'facing': facing_for(theta), 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'})
            if y - 1 >= 1 and blocks.get((x, y - 1, z), ('',))[0] == 'minecraft:air':
                put(x, y - 1, z, 'minecraft:stone_brick_slab', {'type': 'top', 'waterlogged': 'false'})   # underside of the step
# the last steps reach the floor: steps STEPS-3 .. STEPS rise 22..25, floor at 26 - stepping off at the top
# bookshelves on the inner wall where no stair touches
for x in range(W):
    for z in range(D):
        d = math.hypot(x - C, z - C)
        if RI - 0.9 < d <= RI:
            for y in list(range(3, 7)) + list(range(10, 12)) + list(range(17, 19)):
                k = step_of(x, z)
                if k and abs(k - y) <= 3: continue
                if blocks.get((x, y, z), ('',))[0] == 'minecraft:air': put(x, y, z, 'minecraft:bookshelf')
# a chandelier of chains and lanterns hanging down the atrium
for y in range(13, TOP):
    put(C, y, C, 'minecraft:chain', {'axis': 'y', 'waterlogged': 'false'})
put(C, 12, C, 'minecraft:lantern', {'hanging': 'true', 'waterlogged': 'false'})
for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
    put(C + dx * 2, TOP - 1, C + dz * 2, 'minecraft:chain', {'axis': 'y', 'waterlogged': 'false'})
    put(C + dx * 2, TOP - 2, C + dz * 2, 'minecraft:lantern', {'hanging': 'true', 'waterlogged': 'false'})
# the ceremony room: the altar in the middle, lecterns round it, lanterns by the walls
put(C, TOP + 1, C, 'arcane_addon:grimoire_altar')
for dx, dz, f in ((0, -3, 'south'), (0, 3, 'north'), (-3, 0, 'east'), (3, 0, 'west')):
    put(C + dx, TOP + 1, C + dz, 'minecraft:lectern', {'facing': f, 'has_book': 'false', 'powered': 'false'})
for dx, dz in ((3, 3), (-3, 3), (3, -3), (-3, -3)):
    put(C + dx, TOP + 1, C + dz, 'minecraft:lantern', {'hanging': 'false', 'waterlogged': 'false'})
# ceiling and the pointed roof of dark tiles
for x in range(W):
    for z in range(D):
        d = math.hypot(x - C, z - C)
        if d <= RO: put(x, ROOF, z, 'minecraft:deepslate_tiles' if d > RI - 0.5 else 'minecraft:dark_oak_planks')
for k in range(1, 12):
    r = RO + 0.4 - k * 0.62
    if r < 0.3: 
        put(C, ROOF + k, C, 'minecraft:deepslate_tiles'); break
    for x in range(W):
        for z in range(D):
            d = math.hypot(x - C, z - C)
            if d <= r and d > r - 1.25: put(x, ROOF + k, z, 'minecraft:deepslate_tiles')
            elif d <= r - 1.25: put(x, ROOF + k, z, 'minecraft:air')
top_y = max(y for (_, y, _) in blocks)
put(C, top_y + 1, C, 'minecraft:lightning_rod', {'facing': 'up', 'powered': 'false', 'waterlogged': 'false'})
# eaves: a ring of upside-down stairs flaring out under the roof edge
for x in range(W):
    for z in range(D):
        d = math.hypot(x - C, z - C)
        if RO < d <= RO + 0.95:
            th = ang(x, z)
            fx, fz = math.cos(th), math.sin(th)
            f = ('east' if fx > 0 else 'west') if abs(fx) > abs(fz) else ('south' if fz > 0 else 'north')
            opp = {'east': 'west', 'west': 'east', 'north': 'south', 'south': 'north'}[f]
            put(x, ROOF, z, 'minecraft:deepslate_tile_stairs', {'facing': opp, 'half': 'top', 'shape': 'straight', 'waterlogged': 'false'})
            put(x, 0, z, 'minecraft:stone_brick_stairs', {'facing': opp, 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'})   # plinth
# a path out of the door
for z in range(C + 7, D):
    for x in range(C - 1, C + 2): put(x, 0, z, 'minecraft:stone_bricks')

# ---------------------------------------------------------------- write
pal, idx = [], {}
for v in blocks.values():
    key = (v[0], tuple(sorted(v[1].items())))
    if key not in idx: idx[key] = len(pal); pal.append(key)
pal_nbt = []
for name, props in pal:
    e = [tag(8, 'Name', p_str(name))]
    if props: e.append(tag(10, 'Properties', p_comp([tag(8, k, p_str(v)) for k, v in props])))
    pal_nbt.append(p_comp(e))
blk_nbt = []
for (x, y, z), v in sorted(blocks.items()):
    key = (v[0], tuple(sorted(v[1].items())))
    blk_nbt.append(p_comp([tag(9, 'pos', p_list(3, [p_int(x), p_int(y), p_int(z)])), tag(3, 'state', p_int(idx[key]))]))
root = tag(10, '', p_comp([
    tag(3, 'DataVersion', p_int(3465)),
    tag(9, 'size', p_list(3, [p_int(W), p_int(H), p_int(D)])),
    tag(9, 'palette', p_list(10, pal_nbt)),
    tag(9, 'blocks', p_list(10, blk_nbt)),
    tag(9, 'entities', p_list(10, [])),
]))
out = sys.argv[1]
with gzip.open(out, 'wb') as f: f.write(root)
json.dump({'%d,%d,%d' % k: v[0].split(':')[1] for k, v in blocks.items()}, open('/tmp/claude-0/tower/blocks.json', 'w'))
print('blocks', len(blocks), 'palette', len(pal), 'top', top_y + 1)
