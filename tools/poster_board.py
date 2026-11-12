"""The Poster Board: a 3-wide, 2-tall notice board as one block model (coordinates -16..32), plus its textures,
blockstates, item model, loot table, recipe and name. Front faces north before the blockstate turns it."""
import json, os, random
from PIL import Image, ImageDraw
R = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources')
A = os.path.join(R, 'assets/iron_spell_grimoire'); D = os.path.join(R, 'data/iron_spell_grimoire')
rnd = random.Random(3)

# ---------------------------------------------------------------- textures
def save(name, im): im.save(os.path.join(A, 'textures/block', name + '.png'))
os.makedirs(os.path.join(A, 'textures/block'), exist_ok=True)
sign = Image.new('RGBA', (16, 16)); d = ImageDraw.Draw(sign)
d.rectangle([0, 0, 15, 15], fill=(110, 24, 24, 255)); d.rectangle([0, 0, 15, 1], fill=(200, 160, 70, 255)); d.rectangle([0, 14, 15, 15], fill=(200, 160, 70, 255))
for x in range(1, 15, 3):   # a row of gold stars across the banner
    for (dx, dy) in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)): sign.putpixel((min(15, max(0, x + dx)), 7 + dy), (240, 200, 90, 255))
for y in range(2, 14):
    for x in range(16):
        r, g, b, a = sign.getpixel((x, y))
        if (r, g, b) == (110, 24, 24): sign.putpixel((x, y), (110 + rnd.randint(-8, 8), 24, 24, 255))
save('poster_board_sign', sign)
def poster(seed, face):
    r2 = random.Random(seed); im = Image.new('RGBA', (16, 16)); d = ImageDraw.Draw(im)
    for y in range(16):
        for x in range(16): im.putpixel((x, y), (214 + r2.randint(-10, 6), 190 + r2.randint(-10, 6), 140 + r2.randint(-10, 6), 255))
    d.rectangle([1, 1, 14, 3], fill=(150, 20, 20, 255))                    # WANTED band
    for x in range(3, 13, 2): im.putpixel((x, 2), (230, 200, 160, 255))    # its letters, as dots
    d.rectangle([4, 5, 11, 11], fill=(60, 40, 30, 255)); face(d, im)       # the portrait
    d.line([(3, 13), (12, 13)], fill=(90, 60, 40, 255)); im.putpixel((5, 14), (150, 20, 20, 255)); im.putpixel((10, 14), (150, 20, 20, 255))
    for (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15)): im.putpixel((x, y), (0, 0, 0, 0))
    return im
save('poster_board_wanted_a', poster(1, lambda d, im: (d.ellipse([5, 6, 10, 11], fill=(200, 190, 170, 255)), im.putpixel((6, 8), (20, 20, 20, 255)), im.putpixel((9, 8), (20, 20, 20, 255)))))
save('poster_board_wanted_b', poster(2, lambda d, im: (d.polygon([(5, 11), (7, 6), (8, 6), (10, 11)], fill=(120, 140, 120, 255)), im.putpixel((7, 8), (140, 255, 120, 255)), im.putpixel((8, 8), (140, 255, 120, 255)))))
save('poster_board_wanted_c', poster(3, lambda d, im: (d.rectangle([6, 5, 9, 6], fill=(88, 40, 120, 255)), d.rectangle([5, 7, 10, 11], fill=(200, 180, 160, 255)), im.putpixel((6, 9), (130, 230, 110, 255)), im.putpixel((9, 9), (130, 230, 110, 255)))))
note = Image.new('RGBA', (16, 16)); dn = ImageDraw.Draw(note)
for y in range(16):
    for x in range(16): note.putpixel((x, y), (236 + rnd.randint(-8, 4), 230 + rnd.randint(-8, 4), 212 + rnd.randint(-8, 4), 255))
for y in (4, 7, 10, 13): dn.line([(2, y), (13 - (y % 3), y)], fill=(90, 80, 70, 255))
save('poster_board_note', note)

# ---------------------------------------------------------------- the model
els = []
def face(tex, uv): return {'texture': tex, 'uv': uv}
def box(fr, to, tex, faces=('north', 'south', 'east', 'west', 'up', 'down'), rot=None, tile=True):
    """A box, split on the 16-grid so textures tile instead of stretching."""
    xs = sorted(set([fr[0], to[0]] + [v for v in (-16, 0, 16, 32) if fr[0] < v < to[0]]))
    ys = sorted(set([fr[1], to[1]] + [v for v in (0, 16, 32) if fr[1] < v < to[1]]))
    if not tile: xs, ys = [fr[0], to[0]], [fr[1], to[1]]
    for i in range(len(xs) - 1):
        for j in range(len(ys) - 1):
            a = [xs[i], ys[j], fr[2]]; b = [xs[i + 1], ys[j + 1], to[2]]
            u0, u1 = a[0] % 16, (b[0] - 1) % 16 + 1; v0, v1 = 16 - ((b[1] - 1) % 16 + 1), 16 - (a[1] % 16)
            if not tile: u0, u1, v0, v1 = 0, 16, 0, 16
            fs = {}
            for f in faces:
                if f in ('north', 'south'): fs[f] = face(tex, [u0, v0, u1, v1])
                elif f in ('east', 'west'): fs[f] = face(tex, [a[2] % 16, v0, (b[2] - 1) % 16 + 1, v1])
                else: fs[f] = face(tex, [u0, a[2] % 16, u1, (b[2] - 1) % 16 + 1])
            e = {'from': a, 'to': b, 'faces': fs}
            if rot: e['rotation'] = rot
            els.append(e)
LOG, PLANK, DARK, PAPER = '#log', '#plank', '#dark', '#note'
box([-15, 0, 6], [-12, 30, 10], LOG); box([28, 0, 6], [31, 30, 10], LOG)              # posts
box([-12, 5, 7], [28, 27, 9], PLANK)                                                   # the board
box([-13, 27, 6], [29, 29, 10], DARK); box([-13, 3, 6], [29, 5, 10], DARK)             # frame top and bottom
box([-16, 30, 2], [32, 32, 14], DARK)                                                  # the roof
box([-14, 29, 4], [30, 30, 12], PLANK)                                                 # under the roof
box([-6, 22, 6.4], [22, 26, 7], '#sign', faces=('north',), tile=False)                 # the banner
for (x0, y0, x1, y1, tex, ang) in ((-11, 8, -2, 20, '#wanted_a', 0), (0, 7, 9, 19, '#wanted_b', -22.5), (12, 9, 21, 21, '#wanted_c', 0), (22, 12, 27, 18, '#note', 22.5)):
    rot = {'origin': [(x0 + x1) / 2, (y0 + y1) / 2, 6.8], 'axis': 'z', 'angle': ang} if ang else None
    box([x0, y0, 6.6], [x1, y1, 7], tex, faces=('north',), rot=rot, tile=False)
    px, py = (x0 + x1) / 2, y1 - 1.5   # a red pin through the top of each
    box([px - 0.5, py - 0.5, 6.2], [px + 0.5, py + 0.5, 6.6], '#pin', tile=False)
box([-11, 20, 2], [-8, 24, 5], '#lantern', tile=False)                                # a lantern hung from the roof
box([-10, 24, 3], [-9, 30, 4], DARK, tile=False)
model = {'ambientocclusion': False, 'textures': {'particle': 'minecraft:block/dark_oak_planks', 'log': 'minecraft:block/dark_oak_log', 'plank': 'minecraft:block/spruce_planks',
         'dark': 'minecraft:block/dark_oak_planks', 'sign': 'iron_spell_grimoire:block/poster_board_sign', 'note': 'iron_spell_grimoire:block/poster_board_note',
         'wanted_a': 'iron_spell_grimoire:block/poster_board_wanted_a', 'wanted_b': 'iron_spell_grimoire:block/poster_board_wanted_b',
         'wanted_c': 'iron_spell_grimoire:block/poster_board_wanted_c', 'pin': 'minecraft:block/red_wool', 'lantern': 'minecraft:block/shroomlight'},
         'elements': els,
         'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, -1, 0], 'scale': [0.32, 0.32, 0.32]},
                     'ground': {'translation': [0, 2, 0], 'scale': [0.18, 0.18, 0.18]},
                     'fixed': {'scale': [0.4, 0.4, 0.4]},
                     'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.2, 0.2, 0.2]},
                     'firstperson_righthand': {'rotation': [0, 45, 0], 'scale': [0.2, 0.2, 0.2]}}}
os.makedirs(os.path.join(A, 'models/block'), exist_ok=True)
json.dump(model, open(os.path.join(A, 'models/block/poster_board.json'), 'w'), indent=1)
json.dump({'textures': {'particle': 'minecraft:block/dark_oak_planks'}}, open(os.path.join(A, 'models/block/poster_board_part.json'), 'w'))
json.dump({'parent': 'iron_spell_grimoire:block/poster_board'}, open(os.path.join(A, 'models/item/poster_board.json'), 'w'))
rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
json.dump({'variants': {f'facing={k}': {'model': 'iron_spell_grimoire:block/poster_board', 'y': v} for k, v in rot.items()}}, open(os.path.join(A, 'blockstates/poster_board.json'), 'w'), indent=1)
json.dump({'variants': {f'facing={k}': {'model': 'iron_spell_grimoire:block/poster_board_part'} for k in rot}}, open(os.path.join(A, 'blockstates/poster_board_part.json'), 'w'), indent=1)
json.dump({'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'iron_spell_grimoire:poster_board'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]},
          open(os.path.join(D, 'loot_tables/blocks/poster_board.json'), 'w'), indent=1)
json.dump({'type': 'minecraft:crafting_shaped', 'pattern': ['DDD', 'PSP', 'L L'], 'key': {'D': {'item': 'minecraft:dark_oak_planks'}, 'P': {'item': 'minecraft:paper'},
          'S': {'item': 'minecraft:spruce_planks'}, 'L': {'item': 'minecraft:dark_oak_log'}}, 'result': {'item': 'iron_spell_grimoire:poster_board'}},
          open(os.path.join(D, 'recipes/poster_board.json'), 'w'), indent=1)
LP = os.path.join(A, 'lang/en_us.json'); l = json.load(open(LP))
l['block.iron_spell_grimoire.poster_board'] = 'Poster Board'; l['block.iron_spell_grimoire.poster_board_part'] = 'Poster Board'
json.dump(l, open(LP, 'w'), indent=2, ensure_ascii=False)
print('poster board ok', len(els), 'elements')
