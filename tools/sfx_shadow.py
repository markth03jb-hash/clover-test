"""Self-made sounds for the Shadow Grimoire (synthesised here, no samples): a deep rumbling rise out of the ground, a
breathy dark whisper, a smoky slash, and the heavy boom of the Gate of Shade. Writes the oggs and the sounds.json
entries. python3 tools/sfx_shadow.py   (then: git checkout the other .ogg files - importing sfx_synth2 re-encodes them)"""
import numpy as np, json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from sfx_synth2 import SR, t, env, bp, lp, write, OUT


def noise(r, n): return r.normal(0, 1, n)
def tone(f, tt): return np.sin(2 * np.pi * f * tt)


def rise(seed, dur=1.4):   # a low rumble swelling up out of the ground, a breathy rush on top
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    sw = np.clip(tt / (dur * 0.7), 0, 1)
    f = 38 + 30 * sw
    rum = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.7 + lp(noise(r, n), 120) * 0.5
    air = bp(noise(r, n), 400, 2400) * sw ** 2 * 0.35
    return (rum + air) * env(n, 0.25, dur, 0.4)


def whisper(seed, dur=0.9):   # a breathy dark whisper: filtered noise in soft syllables
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    syll = np.clip(np.sin(2 * np.pi * (3.5 + r.uniform(-0.5, 0.5)) * tt), 0, None) ** 1.5
    x = bp(noise(r, n), 900 + 300 * (seed % 3), 3800) * syll * 0.5
    return x * env(n, 0.05, dur, 0.3) + lp(noise(r, n), 80) * 0.15 * env(n, 0.1, dur, 0.5)


def slash(seed, dur=0.5):   # a smoky cut: a falling swish and a low thump
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    lo, hi = 3000 * np.exp(-tt * 6) + 400, 7000 * np.exp(-tt * 5) + 1200
    sw = bp(noise(r, n), 700, 6000) * np.exp(-((tt - 0.06) / 0.06) ** 2) * 0.9
    th = tone(60, tt) * np.exp(-tt * 14) * 0.6
    return sw + th + lp(noise(r, n), 300) * np.exp(-tt * 9) * 0.3


def gate(seed, dur=1.6):   # the Gate of Shade: a heavy boom and a long dark tail
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    boom = (tone(42, tt) + 0.5 * tone(63, tt)) * np.exp(-tt * 2.6) * 0.8
    tail = lp(noise(r, n), 220) * np.exp(-tt * 1.6) * 0.5 + bp(noise(r, n), 300, 1200) * np.exp(-tt * 3) * 0.2
    return boom + tail


made = {}
for name, fn in (("shadow_rise", rise), ("shadow_whisper", whisper), ("shadow_slash", slash), ("shadow_gate", gate)):
    made[name] = []
    for i in range(1, 4):
        write(f"{name}_{i}", fn(200 * i + len(name) + i))
        made[name].append(f"iron_spell_grimoire:sfx/{name}_{i}")
sj = os.path.join(OUT, "..", "..", "sounds.json")
d = json.load(open(sj))
for name, files in made.items():
    d["sfx." + name] = {"sounds": [{"name": f} for f in files]}
json.dump(d, open(sj, "w"), indent=2)
print("shadow sounds written")
