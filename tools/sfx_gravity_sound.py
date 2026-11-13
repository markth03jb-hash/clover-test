"""Self-made sounds for the Gravity and Sound Grimoires (synthesised here, no samples):
gravity_hum   - a deep, wobbling sub-bass drone (a well opening, the domain)
gravity_crush - a heavy downward boom with a crunch of pressure
sonic_boom    - a sharp crack with a rushing tail (pulses, claps, the lance)
sound_ping    - a bright resonant tuning-fork ping (resonance)
sound_chord   - a swelling three-note chord that bursts (the final chord)
Writes ogg files into the mod's sounds/sfx folder and the sounds.json entries.  python3 tools/sfx_gravity_sound.py"""
import numpy as np, json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from sfx_synth2 import SR, t, env, bp, lp, write, OUT


def hum(seed, dur=1.4):
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    f = 38 + 6 * np.sin(2 * np.pi * 1.7 * tt + r.uniform(0, 6))          # the pitch sags and swells
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.5 * np.sin(2 * np.pi * np.cumsum(f * 2.01) / SR)
    x *= 0.6 + 0.4 * np.sin(2 * np.pi * 5.5 * tt)                          # a slow throb
    air = lp(r.normal(0, 1, n), 300) * 0.4                                   # pressure in the air
    return (x * 0.8 + air) * env(n, 0.25, 0.5, 2.5)


def crush(seed, dur=1.2):
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    f = 120 * np.exp(-tt * 5) + 32                                           # a falling boom
    boom = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(n, 0.005, 0.7, 4)
    crunch = bp(r.normal(0, 1, n), 200, 2400) * env(n, 0.002, 0.25, 8) * 0.6
    rumble = lp(r.normal(0, 1, n), 120) * env(n, 0.05, 0.9, 3) * 0.8
    return boom + crunch + rumble


def boom(seed, dur=0.9):
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    crack = bp(r.normal(0, 1, n), 1200, 9000) * env(n, 0.001, 0.06, 10)    # the snap of the air
    whump = np.sin(2 * np.pi * np.cumsum(90 * np.exp(-tt * 9) + 55) / SR) * env(n, 0.002, 0.3, 6) * 0.8
    rush = bp(r.normal(0, 1, n), 500, 4000) * env(n, 0.02, 0.6, 3) * 0.35  # the wave rushing past
    return crack + whump + rush


def ping(seed, dur=1.3):
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    f0 = r.uniform(820, 980)
    x = sum(a * np.sin(2 * np.pi * f0 * k * tt + r.uniform(0, 6)) for k, a in ((1, 1.0), (2.76, 0.35), (5.4, 0.12)))
    x *= np.exp(-tt * 3.2) * (1 + 0.15 * np.sin(2 * np.pi * 6 * tt))      # a fork ringing, beating a little
    tick = bp(r.normal(0, 1, n), 3000, 9000) * env(n, 0.001, 0.02, 10) * 0.5
    return x * 0.7 + tick


def chord(seed, dur=1.8):
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    swell = np.clip(tt / 1.1, 0, 1) ** 2
    x = sum(np.sin(2 * np.pi * f * tt) + 0.3 * np.sin(2 * np.pi * f * 2 * tt) for f in (220, 277.2, 329.6, 440))
    x = x / 4 * swell * (tt < 1.15)
    hit = int(1.1 * SR)
    b = np.zeros(n); m = n - hit
    bb = boom(seed + 1, m / SR + 0.01)[:m]
    b[hit:hit + len(bb)] = bb * 1.2
    return x * 0.6 + b


made = {}
for name, fn in (("gravity_hum", hum), ("gravity_crush", crush), ("sonic_boom", boom), ("sound_ping", ping), ("sound_chord", chord)):
    made[name] = []
    for i in range(1, 4):
        write(f"{name}_{i}", fn(100 * i + len(name)))
        made[name].append(f"iron_spell_grimoire:sfx/{name}_{i}")

sj = os.path.join(OUT, "..", "..", "sounds.json")
d = json.load(open(sj))
for name, files in made.items():
    d["sfx." + name] = {"sounds": [{"name": f} for f in files]}
json.dump(d, open(sj, "w"), indent=2)
print("sounds written:", ", ".join(made))
