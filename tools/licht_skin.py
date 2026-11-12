"""Licht's skin: the user's own 64x64 slim skin, given more detail - shading on every face of every part (lit from
above and the front), soft cloth texture on the large fills, strands in the hair, highlights and shadows on the gold
trim, and a little depth where colours meet. The design itself is left exactly as drawn."""
import sys, colorsys, random
from PIL import Image
src, dst = sys.argv[1], sys.argv[2]
im = Image.open(src).convert('RGBA'); px = im.load(); orig = im.copy().load()
rnd = random.Random(5)

def nets(u, v, w, h, d):
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
            'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}
parts = []   # (name, faces, is_overlay)
for (u, v, w, h, d, name, ov) in [(0, 0, 8, 8, 8, 'head', 0), (32, 0, 8, 8, 8, 'hat', 1), (16, 16, 8, 12, 4, 'body', 0), (16, 32, 8, 12, 4, 'jacket', 1),
                                  (40, 16, 3, 12, 4, 'rarm', 0), (40, 32, 3, 12, 4, 'rsleeve', 1), (32, 48, 3, 12, 4, 'larm', 0), (48, 48, 3, 12, 4, 'lsleeve', 1),
                                  (0, 16, 4, 12, 4, 'rleg', 0), (0, 32, 4, 12, 4, 'rpants', 1), (16, 48, 4, 12, 4, 'lleg', 0), (0, 48, 4, 12, 4, 'lpants', 1)]:
    parts.append((name, nets(u, v, w, h, d), ov))

FACE_LIGHT = {'top': 1.10, 'front': 1.0, 'right': 0.9, 'left': 0.9, 'back': 0.82, 'bottom': 0.72}

def kind(c):
    r, g, b = c[:3]
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    if v > 0.82 and s < 0.12: return 'white'
    if 0.10 < h < 0.20 and s > 0.4 and v > 0.5: return 'gold'
    if 0.55 < h < 0.72 and v < 0.5: return 'navy'
    if (h > 0.9 or h < 0.03) and s > 0.6: return 'red'
    return 'other'

def shade(c, k):
    return tuple(max(0, min(255, int(round(ch * k)))) for ch in c[:3]) + (c[3],)

for name, faces, ov in parts:
    for fname, (x0, y0, W, H) in faces.items():
        face_detail = name in ('head', 'hat') and fname == 'front'
        for y in range(y0, y0 + H):
            for x in range(x0, x0 + W):
                c = orig[x, y]
                if c[3] == 0: continue
                fx, fy = x - x0, y - y0
                kd = kind(c)
                k = FACE_LIGHT[fname]
                if kd == 'white': k = 1.0 - (1.0 - k) * 0.45   # white stays bright; it only takes a little of the shading
                if fname not in ('top', 'bottom') and H > 1:   # lit from above: a soft gradient down each side face
                    k *= 1.04 - (0.06 if kd == 'white' else 0.14) * fy / (H - 1)
                if face_detail and kd not in ('white',): k = 1.0   # keep the eyes and marks exactly as drawn
                # texture
                if kd == 'navy' and not face_detail:
                    k *= 1.0 + rnd.uniform(-0.05, 0.05) + (0.06 if (fx + fy * 3) % 7 == 0 else 0)        # woven cloth
                elif kd == 'white':
                    if name in ('head', 'hat') and fname != 'front' or name == 'hat':
                        k *= 0.98 + 0.03 * ((fx * 5 + fy) % 3 == 0) - 0.05 * (fx % 2)                     # strands of hair
                    else:
                        k *= 1.0 + rnd.uniform(-0.02, 0.01) - (0.04 if (fx + fy) % 5 == 0 else 0)         # soft folds
                elif kd == 'gold':
                    above = orig[x, y - 1] if y > y0 else (0, 0, 0, 0)
                    below = orig[x, y + 1] if y < y0 + H - 1 else (0, 0, 0, 0)
                    if kind(above) != 'gold': k *= 1.22                                                 # bright top edge
                    elif kind(below) != 'gold': k *= 0.78                                               # shadowed lower edge
                    if (fx + fy) % 4 == 0: k *= 1.12                                                    # a glint
                elif kd == 'red':
                    k *= 1.0 + rnd.uniform(-0.04, 0.04)
                # a little depth where two different colours meet (not on the face)
                if not face_detail and not ov:
                    for dx, dy in ((0, -1), (-1, 0)):
                        nx, ny = x + dx, y + dy
                        if x0 <= nx < x0 + W and y0 <= ny < y0 + H:
                            n = orig[nx, ny]
                            if n[3] and kind(n) != kd and sum(n[:3]) > sum(c[:3]): k *= 0.88
                px[x, y] = shade(c, k)
im.save(dst)
print('ok')
