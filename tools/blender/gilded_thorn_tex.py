"""The Gilded Thorn's texture atlas (256x256), painted for the Blender mesh's UV layout:
  blade   x 0-255, y 0-127   u runs along the blade (root to point), v round its cross-section in 8 bands:
                             honed edge, steel face, steel face, honed edge (front), then the same for the back
  gold    x 0-127, y 128-191
  gem     x 128-191, y 128-191
  wrap    x 192-255, y 128-191
  dark    x 0-127, y 192-255   (the pommel)
  cord    x 128-255, y 192-255
"""
import math, random, sys
from PIL import Image

def mix(a, b, t): t = max(0.0, min(1.0, t)); return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def paint(out):
    rnd = random.Random(4)
    im = Image.new("RGBA", (256, 256))
    px = im.load()
    STEEL, STEEL_D, RIDGE, HONE, HONE_D = (70, 72, 84), (28, 29, 36), (112, 116, 130), (196, 200, 210), (120, 124, 136)
    for x in range(256):
        t = x / 255
        for y in range(128):
            band, f = y // 16, (y % 16) / 15
            if band in (0, 3, 4, 7):        # honed edges: pale, brightest at the very rim
                rim = f if band in (3, 7) else 1 - f    # (bands 0/4 run edge->bevel, 3/7 bevel->edge)
                c = mix(HONE_D, HONE, rim ** 0.6)
                if (x * 3 + y) % 9 == 0: c = mix(c, (255, 255, 255), 0.15)
            else:                           # steel faces: dark at the bevel, lighter toward the ridge
                toward_ridge = f if band in (1, 5) else 1 - f
                c = mix(STEEL_D, STEEL, toward_ridge ** 1.4)
                if toward_ridge > 0.93: c = mix(c, RIDGE, 0.8)
                n = 0.5 + 0.5 * math.sin(x * 0.17 + y * 0.6) * math.sin(x * 0.05 - y * 0.2)
                c = mix(c, STEEL_D, 0.18 * n)
            c = mix(c, (30, 30, 38), 0.18 * t)  # a touch darker toward the point
            px[x, y] = c + (255,)
    for _ in range(36):                     # faint scratches on the faces
        x, y = rnd.randrange(250), rnd.choice([16, 32, 80, 96]) + rnd.randrange(30)
        for k in range(rnd.randint(3, 7)):
            if x + k < 256 and y < 128 and (y // 16) not in (0, 3, 4, 7): px[x + k, y] = mix(px[x + k, y][:3], (120, 124, 138), 0.35) + (255,)
    GOLD, GOLD_D, GOLD_L = (186, 136, 48), (110, 72, 20), (246, 214, 130)
    for x in range(128):
        for y in range(128, 192):
            n = math.sin(x * 0.09 + y * 0.05) * math.sin(y * 0.11 - x * 0.04)
            c = mix(GOLD, GOLD_L if n > 0 else GOLD_D, abs(n) * 0.6)
            px[x, y] = c + (255,)
    for x in range(128, 192):
        for y in range(128, 192):
            d = math.hypot((x - 160) / 32, (y - 160) / 32)
            px[x, y] = mix((255, 170, 160), mix((200, 20, 34), (80, 0, 12), (d - 0.4) / 0.6), min(1, d / 0.4)) + (255,)
    for x in range(192, 256):
        for y in range(128, 192):
            k = ((x * 2 + y) % 10) / 10          # cord wound diagonally, crossing: raised bands with dark gaps
            k2 = ((x * 2 - y) % 10) / 10
            v = max(math.sin(k * math.pi), 0.8 * math.sin(k2 * math.pi)) ** 1.5
            px[x, y] = mix((12, 10, 14), (70, 62, 76), v) + (255,)
    for x in range(128):
        for y in range(192, 256):
            n = math.sin(x * 0.3 + y * 0.2)
            px[x, y] = mix((48, 46, 54), (22, 20, 26), 0.5 + 0.4 * n) + (255,)
    for x in range(128, 256):
        for y in range(192, 256):
            k = (y % 6) / 6
            px[x, y] = mix((20, 16, 22), (78, 70, 84), math.sin(k * math.pi) ** 1.5) + (255,)
    im.save(out)

if __name__ == "__main__":
    paint(sys.argv[1])
