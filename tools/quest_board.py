"""The Quest Board: the Poster Board's frame and model with different pictures - a blue banner with a quill, and the
village's requests pinned up: a map with a marked route, a supply list, a monster notice with crossed swords, a
checklist. Writes its textures, model (from poster_board.json), blockstates, item model, loot table, recipe and name."""
import json, os, random, copy
from PIL import Image, ImageDraw
R = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources')
A = os.path.join(R, 'assets/iron_spell_grimoire'); D = os.path.join(R, 'data/iron_spell_grimoire')
rnd = random.Random(7)
def save(name, im): im.save(os.path.join(A, 'textures/block', name + '.png'))
def paper(seed, base=(222, 204, 160)):
    r2 = random.Random(seed); im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16): im.putpixel((x, y), (base[0] + r2.randint(-10, 6), base[1] + r2.randint(-10, 6), base[2] + r2.randint(-10, 6), 255))
    for (x, y) in ((0, 0), (15, 0), (0, 15), (15, 15)): im.putpixel((x, y), (0, 0, 0, 0))
    return im
INK = (70, 50, 36, 255)
# the banner: deep blue, silver edges, a white quill across it
sign = Image.new('RGBA', (16, 16)); d = ImageDraw.Draw(sign)
for y in range(16):
    for x in range(16): sign.putpixel((x, y), (28 + rnd.randint(-4, 4), 52 + rnd.randint(-4, 4), 110 + rnd.randint(-6, 6), 255))
d.rectangle([0, 0, 15, 1], fill=(200, 210, 225, 255)); d.rectangle([0, 14, 15, 15], fill=(200, 210, 225, 255))
d.line([(3, 11), (12, 3)], fill=(240, 240, 245, 255)); d.line([(4, 11), (12, 4)], fill=(210, 215, 230, 255))
for (x, y) in ((6, 9), (8, 7), (10, 5)): sign.putpixel((x, y - 1), (240, 240, 245, 255))
sign.putpixel((2, 12), (30, 30, 40, 255))
save('quest_board_sign', sign)
# a map: a dotted route to a red X, a little forest and a lake
m = paper(11, (214, 196, 150)); d = ImageDraw.Draw(m)
d.ellipse([9, 9, 13, 12], fill=(80, 130, 180, 255))
for (x, y) in ((3, 4), (4, 3), (5, 5), (3, 6)): d.rectangle([x, y, x, y], fill=(60, 110, 50, 255))
for (x, y) in ((3, 12), (5, 11), (7, 10), (8, 8), (9, 6), (11, 5)): m.putpixel((x, y), (150, 30, 30, 255))
d.line([(11, 2), (13, 4)], fill=(170, 20, 20, 255)); d.line([(13, 2), (11, 4)], fill=(170, 20, 20, 255))
d.rectangle([1, 1, 14, 14], outline=(150, 120, 80, 255))
save('quest_board_map', m)
# a supply list: a header, rows with little item squares and ticks
s = paper(12); d = ImageDraw.Draw(s)
d.rectangle([2, 1, 13, 2], fill=(40, 70, 130, 255))
for i, col in enumerate([(200, 150, 70, 255), (190, 190, 200, 255), (220, 200, 80, 255), (50, 50, 50, 255)]):
    y = 4 + i * 3
    d.rectangle([2, y, 3, y + 1], fill=col); d.line([(5, y + 1), (12, y + 1)], fill=INK)
save('quest_board_supply', s)
# a monster notice: blue band, crossed swords, a row of tally marks
n = paper(13); d = ImageDraw.Draw(n)
d.rectangle([1, 1, 14, 3], fill=(40, 70, 130, 255))
for x in range(3, 13, 2): n.putpixel((x, 2), (220, 230, 245, 255))
d.line([(4, 5), (11, 12)], fill=(180, 185, 195, 255)); d.line([(11, 5), (4, 12)], fill=(180, 185, 195, 255))
d.line([(3, 11), (5, 13)], fill=(120, 80, 40, 255)); d.line([(12, 11), (10, 13)], fill=(120, 80, 40, 255))
for x in (4, 6, 8, 10): n.putpixel((x, 14), INK)
save('quest_board_notice', n)
# a checklist: boxes, some ticked
c = paper(14, (232, 226, 206)); d = ImageDraw.Draw(c)
for i in range(4):
    y = 2 + i * 3
    d.rectangle([2, y, 4, y + 2], outline=INK)
    if i < 2: c.putpixel((3, y + 1), (40, 140, 60, 255)); c.putpixel((4, y), (40, 140, 60, 255))
    d.line([(6, y + 1), (13 - i, y + 1)], fill=INK)
save('quest_board_check', c)

# the Daily Special: a gold-edged note with a star on it, pinned with gold
sp = paper(15, (238, 226, 190)); d = ImageDraw.Draw(sp)
d.rectangle([0, 0, 15, 15], outline=(210, 160, 40, 255)); d.rectangle([1, 1, 14, 14], outline=(240, 200, 80, 255))
for (x, y) in ((7, 3), (8, 3), (6, 5), (7, 5), (8, 5), (9, 5), (4, 6), (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6), (11, 6),
               (6, 7), (7, 7), (8, 7), (9, 7), (5, 8), (6, 8), (9, 8), (10, 8), (7, 4), (8, 4)):
    sp.putpixel((x, y), (230, 170, 30, 255))
for y in (11, 13): d.line([(3, y), (12, y)], fill=INK)
save('quest_board_special', sp)

# the model: the Poster Board's, with these pictures
src = json.load(open(os.path.join(A, 'models/block/poster_board.json')))
model = copy.deepcopy(src)
model['textures'].update({'sign': 'iron_spell_grimoire:block/quest_board_sign', 'note': 'iron_spell_grimoire:block/quest_board_check',
                          'wanted_a': 'iron_spell_grimoire:block/quest_board_map', 'wanted_b': 'iron_spell_grimoire:block/quest_board_supply',
                          'wanted_c': 'iron_spell_grimoire:block/quest_board_notice', 'pin': 'minecraft:block/light_blue_wool'})
model['textures']['special'] = 'iron_spell_grimoire:block/quest_board_special'
model['textures']['gold'] = 'minecraft:block/gold_block'
# the gold-pinned Daily Special, low on the right of the board
model['elements'].append({'from': [21.5, 5.6, 6.6], 'to': [27.5, 11.6, 7], 'faces': {'north': {'texture': '#special', 'uv': [0, 0, 16, 16]}},
                          'rotation': {'origin': [24.5, 8.6, 6.8], 'axis': 'z', 'angle': -22.5}})
model['elements'].append({'from': [24, 10, 6.2], 'to': [25, 11, 6.6], 'faces': {f: {'texture': '#gold', 'uv': [0, 0, 16, 16]} for f in ('north', 'south', 'east', 'west', 'up', 'down')}})
json.dump(model, open(os.path.join(A, 'models/block/quest_board.json'), 'w'), indent=1)
json.dump({'parent': 'iron_spell_grimoire:block/quest_board'}, open(os.path.join(A, 'models/item/quest_board.json'), 'w'))
rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
json.dump({'variants': {f'facing={k}': {'model': 'iron_spell_grimoire:block/quest_board', 'y': v} for k, v in rot.items()}}, open(os.path.join(A, 'blockstates/quest_board.json'), 'w'), indent=1)
json.dump({'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'iron_spell_grimoire:quest_board'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]},
          open(os.path.join(D, 'loot_tables/blocks/quest_board.json'), 'w'), indent=1)
json.dump({'type': 'minecraft:crafting_shaped', 'pattern': ['DDD', 'PSP', 'L L'], 'key': {'D': {'item': 'minecraft:dark_oak_planks'}, 'P': {'item': 'minecraft:map'},
          'S': {'item': 'minecraft:birch_planks'}, 'L': {'item': 'minecraft:dark_oak_log'}}, 'result': {'item': 'iron_spell_grimoire:quest_board'}},
          open(os.path.join(D, 'recipes/quest_board.json'), 'w'), indent=1)
LP = os.path.join(A, 'lang/en_us.json'); l = json.load(open(LP))
l['block.iron_spell_grimoire.quest_board'] = 'Quest Board'
json.dump(l, open(LP, 'w'), indent=2, ensure_ascii=False)
print('quest board ok')
