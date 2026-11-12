"""The Scarlet Moon aura as a smooth, high-resolution 2D sprite (self-made, no outside art).

N looping frames, stacked top to bottom:
  moon_aura.png        the flame body, alpha-blended behind you: a soft blue haze, rising streaks of light, and the
                       flame tongues licking up the sides to points above the head, edged in a bright cyan-white rim
  moon_aura_glow.png   the same flame's light, added on top (additive): the white-hot rim and streaks, so it glows
  moon_aura_front.png  faint rising streaks and the glowing rim, added over your body (additive)
The noise is built from waves whose vertical frequencies and scroll speeds are whole numbers, so every frame loops.
"""
import math, sys
import numpy as np
from PIL import Image

W, H, N = 160, 256, 16
OUT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/iron_spell_grimoire/textures/effect"
rng = np.random.default_rng(11)
TAU = math.tau

ys, xs = np.mgrid[0:H, 0:W].astype(np.float64)
U = (xs + 0.5) / W - 0.5            # -0.5 .. 0.5 across
Y = 1 - (ys + 0.5) / H              # 0 at the feet .. 1 at the top
SIDE = np.sign(U)


def waves(count, fx, fy, speed, seed):
    r = np.random.default_rng(seed)
    comps = []
    for _ in range(count):
        comps.append((r.uniform(-fx, fx), r.integers(1, fy + 1), r.integers(1, speed + 1), r.uniform(0, TAU), r.uniform(0.5, 1.0)))
    total = sum(c[4] for c in comps)

    def f(T, warp=0.0):
        out = np.zeros_like(U)
        for kx, ky, sp, ph, a in comps:
            out += a * np.sin(TAU * (kx * U + ky * (Y + warp) - sp * T) + ph)
        return out / total
    return f


streak = waves(14, 9.0, 4, 2, 1)       # tall vertical streaks (high across, low up)
lick = waves(12, 4.0, 5, 2, 2)         # the big tongues on the edge
fine = waves(12, 14.0, 7, 3, 3)        # fine flicker
warpn = waves(6, 2.0, 2, 1, 4)


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def half_width(y):
    y = y / 0.84
    w = 0.31 * (1 - np.clip(y, 0, 1) ** 1.8) * (0.9 + 0.22 * np.sin(np.pi * y * 0.85))
    return np.maximum(w, 0.0) * (0.55 + 0.45 * smooth(0.0, 0.1, y))


def frame(T):
    wy = 0.05 * warpn(T)
    hw = half_width(Y + wy * 0.5)
    # tongues: the edge pushed out in long upward licks, more so higher up; they rise as the frame turns
    ln = lick(T, wy)
    tongue = 1.5 * np.clip(ln, 0, None) ** 1.8 * (0.35 + 1.3 * Y)
    edge = hw * (1 + tongue) + 0.012 * fine(T) * (hw > 0.01)
    # flame points over the head: thin tall licks rising from the top
    d = (edge - np.abs(U)) / 0.03 - np.clip(Y + wy * 0.5 - 0.8, 0, None) / 0.012                                 # >0 inside, in units of the soft edge
    for c, ph, hgt in ((-0.12, 0.0, 0.14), (-0.045, 0.4, 0.2), (0.05, 0.7, 0.17), (0.13, 0.2, 0.12)):
        top = 0.72 + hgt * (0.6 + 0.4 * np.sin(TAU * (2 * T + ph)))   # thin licks rising over the head
        k = np.clip((top - Y) / (top - 0.5), 0, 1)
        sway = 0.015 * np.sin(TAU * (3 * Y - 2 * T + ph))
        d = np.maximum(d, (0.042 * k ** 1.3 - np.abs(U - c - sway)) / 0.03 - np.clip(Y - top, 0, None) / 0.012)
    inside = smooth(-0.6, 0.6, d)
    rim = np.exp(-((d - 0.4) / 0.55) ** 2)                          # the bright band hugging the edge
    halo = smooth(-4.0, 0.0, d) ** 2 * (1 - inside)                      # the soft glow just outside it
    st = np.clip(streak(T) * 0.5 + 0.5, 0, 1) ** 3                  # streaks of light running up through it
    depth = smooth(0.0, 6.0, d)                                     # how far in from the edge
    fade_bottom = smooth(0.0, 0.08, Y)

    # body: a deep-to-light blue haze, more see-through towards the middle, streaked with light
    a_body = inside * (0.14 + 0.32 * (1 - depth) + 0.22 * st) + 0.3 * halo
    col = np.stack([0.30 + 0.45 * st, 0.62 + 0.3 * st, np.ones_like(st)], -1)
    col = col * (1 - rim[..., None] * 0.7) + np.array([0.85, 0.96, 1.0]) * rim[..., None] * 0.7
    a_body = np.clip(np.maximum(a_body * 1.5, rim * 0.9) * fade_bottom, 0, 1)
    body = np.dstack([col * 255, a_body[..., None] * 255])

    # glow: what's added on top - the white-hot rim, the streaks and a whisper of the halo
    g = np.clip((rim * 0.75 + inside * st * 0.35 * (1 - 0.5 * depth) + halo * 0.3) * fade_bottom, 0, 1)
    glow = np.dstack([np.stack([0.75 + 0.25 * rim, 0.9 + 0.1 * rim, np.ones_like(rim)], -1) * 255, g[..., None] * 255])

    # front: just faint streaks and the rim, over your body
    fr = np.clip((inside * st * 0.45 * (0.3 + 0.7 * (1 - depth)) + rim * 0.35) * fade_bottom, 0, 1)
    front = np.dstack([np.full((H, W, 3), 255.0) * np.array([0.85, 0.95, 1.0]), fr[..., None] * 255])
    return [Image.fromarray(np.clip(x, 0, 255).astype(np.uint8), "RGBA") for x in (body, glow, front)]


def main():
    sheets = [Image.new("RGBA", (W, H * N), (0, 0, 0, 0)) for _ in range(3)]
    for i in range(N):
        for s, im in zip(sheets, frame(i / N)):
            s.paste(im, (0, i * H))
    for s, name in zip(sheets, ("moon_aura", "moon_aura_glow", "moon_aura_front")):
        s.save(f"{OUT}/{name}.png", optimize=True)


if __name__ == "__main__":
    main()
