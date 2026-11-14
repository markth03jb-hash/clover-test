"""Self-made sounds for the Dream Grimoire (synthesised here, no samples): a soft music-box chime, a springy boing,
a sleepy hush, a clock tick, a little alarm bell. Writes the ogg files and the sounds.json entries.
python3 tools/sfx_dream.py   (then: git checkout the other .ogg files - importing sfx_synth2 re-encodes them)"""
import numpy as np, json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from sfx_synth2 import SR, t, env, bp, lp, write, OUT


def noise(r, n): return r.normal(0, 1, n)
def tone(f, tt, ph=0.0): return np.sin(2 * np.pi * f * tt + ph)


def chime(seed, dur=1.2):   # two soft bell notes, like a music box, with a shimmer
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    base = [880, 1046.5, 1174.7, 1318.5][seed % 4]
    x = np.zeros(n)
    for k, f0 in enumerate((base, base * 1.5)):
        tk = np.clip(tt - k * 0.16, 0, None)
        on = tt >= k * 0.16
        x += on * sum(a * tone(f0 * m, tk) * np.exp(-tk * d) for m, a, d in ((1, 0.6, 3.5), (2.76, 0.18, 7), (5.4, 0.07, 12)))
    shimmer = bp(noise(r, n), 6000, 11000) * np.exp(-tt * 5) * 0.05
    return (x + shimmer) * env(n, 0.003, dur, 2)


def boing(seed, dur=0.45):   # a soft spring: a pitch that bounces up and settles
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    f = 220 + 160 * np.exp(-tt * 9) * np.cos(2 * np.pi * 9 * tt) + r.uniform(-15, 15)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.exp(-tt * 6) * 0.8 + lp(noise(r, n), 200) * np.exp(-tt * 30) * 0.6


def hush(seed, dur=1.0):   # a long soft "shh", rising and falling like a sleeping breath
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    x = bp(noise(r, n), 2500, 7500) * np.sin(np.pi * np.clip(tt / dur, 0, 1)) ** 2 * 0.5
    pad = sum(tone(f, tt) for f in (330, 392, 494)) * 0.05 * np.sin(np.pi * np.clip(tt / dur, 0, 1))
    return x + pad


def tick(seed, dur=0.18):   # a wooden clock tick with a tiny bell after it
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    click = bp(noise(r, n), 1800, 5000) * np.exp(-tt * 140) * 1.4
    knock = tone(900 + r.uniform(-40, 40), tt) * np.exp(-tt * 60) * 0.4
    return click + knock


def alarm(seed, dur=1.0):   # a little bell alarm ringing fast, then stopping
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    gate = (np.sin(2 * np.pi * 22 * tt) > 0) * (tt < 0.75)
    bell = sum(a * tone(f, tt) for f, a in ((1568, 0.45), (2350, 0.2), (3920, 0.08)))
    return bell * gate * env(n, 0.002, dur, 3) * 0.8


made = {}
for name, fn in (("dream_chime", chime), ("dream_boing", boing), ("dream_hush", hush), ("dream_tick", tick), ("dream_alarm", alarm)):
    made[name] = []
    for i in range(1, 4):
        write(f"{name}_{i}", fn(100 * i + len(name) + i))
        made[name].append(f"iron_spell_grimoire:sfx/{name}_{i}")
sj = os.path.join(OUT, "..", "..", "sounds.json")
d = json.load(open(sj))
for name, files in made.items():
    d["sfx." + name] = {"sounds": [{"name": f} for f in files]}
json.dump(d, open(sj, "w"), indent=2)
print("dream sounds written")
