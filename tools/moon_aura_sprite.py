"""The Scarlet Moon aura as a 2D pixel-art sprite: N looping frames (a vertical strip) of a teardrop of white-blue
flame around the body (moon_aura.png, drawn behind you, alpha-blended) and the white streaks rising through it with
the odd crackle of lightning (moon_aura_front.png, drawn over you, additive). Self-made, no outside art."""
import math, random, sys
from PIL import Image

W, H, N = 40, 64, 12
OUT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/iron_spell_grimoire/textures/effect"

OUTLINE = (22, 44, 150, 255)
RIM = (215, 240, 255, 250)
MID = (120, 185, 255, 200)
INNER = (175, 215, 255, 110)
CORE = (235, 245, 255, 55)
TAU = math.tau


def half_width(y):          # y: 0 feet .. 1 tip, as a fraction of the frame's half-width
    w = 0.4 * (1 - y ** 2.0) * (0.86 + 0.26 * math.sin(math.pi * y * 0.8))
    if y < 0.07:
        w *= math.sqrt(max(0.0, y / 0.07)) * 0.5 + 0.5
    return max(0.0, w)


def edge(y, T, side):       # flame spikes running up the sides: each widens as it rises and breaks off in a point
    out = 0.0
    for k, amp, ph in ((4, 0.62, 0.0), (7, 0.22, 0.41)):
        f = (k * y - T * (k - 1) + ph + (0.37 if side > 0 else 0)) % 1.0
        out += amp * f ** 4
    return (out - 0.16) * (0.45 + 0.9 * y)


def frame(T):
    back = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = {}
    for py in range(1, H - 1):
        y = 1 - (py + 0.5) / H
        for px in range(1, W - 1):
            u = (px + 0.5) / W - 0.5
            side = 1 if u > 0 else -1
            w = half_width(y) * (1 + edge(y, T, side))
            # tongues licking above the tip
            if y > 0.6:   # flame points round the top, flickering up
                for c, ph in ((-0.2, 0.0), (-0.09, 0.5), (0.1, 0.25), (0.21, 0.75)):
                    tip = 0.84 + 0.13 * (0.5 + 0.5 * math.sin(TAU * (T * 2 + ph)))
                    if y < tip:
                        hw = 0.075 * (tip - y) / (tip - 0.6)
                        if abs(u - c) < hw:
                            w = max(w, abs(u) + 0.01)
            if w <= 0:
                continue
            dd = 1 - abs(u) / w
            if dd > 0:
                d[(px, py)] = dd
    px_ = back.load()
    for (x, y), dd in d.items():
        border = any((x + dx, y + dy) not in d for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if border:
            c = OUTLINE
        elif dd < 0.12:
            c = RIM
        elif dd < 0.3:
            c = MID if (x + y) % 2 == 0 or dd < 0.24 else INNER   # dithered step
        elif dd < 0.55:
            c = INNER
        else:
            c = CORE
        px_[x, y] = c
    # the front layer: white streaks rising through the aura, and now and then a crackle of lightning
    front = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    fp = front.load()
    rnd = random.Random(7)
    for s in range(9):
        cx = rnd.uniform(-0.32, 0.32)
        y0, k, ln = rnd.random(), rnd.choice((1, 2)), rnd.randint(5, 10)
        head = (y0 + k * T) % 1.0
        for j in range(ln):
            y = head - j / H
            if y < 0:
                continue
            w = half_width(y)
            u = cx * w / 0.47
            x, py = int((u + 0.5) * W), int((1 - y) * H)
            if (x, py) in d and 0 <= py < H:
                a = int(230 * (1 - j / ln))
                fp[x, py] = (255, 255, 255, a)
    fi = round(T * N)
    if fi in (2, 3, 8):     # lightning: a jagged white bolt with a blue fringe
        r2 = random.Random(fi * 31)
        x, y = r2.randint(10, 29), r2.randint(14, 24)
        for _ in range(r2.randint(14, 20)):
            if (x, y) in d:
                fp[x, y] = (255, 255, 255, 255)
                for dx in (-1, 1):
                    if (x + dx, y) in d and fp[x + dx, y][3] == 0:
                        fp[x + dx, y] = (90, 150, 255, 200)
            x += r2.choice((-1, 0, 1, 1)) * (1 if fi != 8 else -1)
            y += 1
    return back, front


def main():
    back = Image.new("RGBA", (W, H * N), (0, 0, 0, 0))
    front = Image.new("RGBA", (W, H * N), (0, 0, 0, 0))
    for i in range(N):
        b, f = frame(i / N)
        back.paste(b, (0, i * H))
        front.paste(f, (0, i * H))
    back.save(f"{OUT}/moon_aura.png")
    front.save(f"{OUT}/moon_aura_front.png")


if __name__ == "__main__":
    main()
