"""Self-made sounds for the Blood Grimoire and the Scarlet Moon's ice (synthesised here, no samples):
ice cracking, ice shattering into glassy shards, a heartbeat, the hiss of something freezing, and a wet blood splash.
Writes ogg files into the mod's sounds/sfx folder and the sounds.json entries.  python3 tools/sfx_blood.py"""
import numpy as np, json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from sfx_synth2 import SR, t, env, bp, lp, write, OUT

rng = np.random.default_rng(23)

def crack(seed, dur=0.55):
    r = np.random.default_rng(seed); n = int(SR * dur); x = np.zeros(n)
    for _ in range(18 + r.integers(10)):   # a run of sharp clicks, bunched at the start: ice splitting
        at = int(abs(r.normal(0, 0.12)) * SR) % (n - 800)
        k = r.uniform(0.3, 1.0) * np.exp(-np.arange(600) / r.uniform(25, 90))
        x[at:at + 600] += k * r.choice([-1, 1]) * r.normal(0, 1, 600)
    body = bp(r.normal(0, 1, n), 1800, 7000) * env(n, 0.002, 0.25, 6) * 0.35   # the bright 'tk' of the fracture
    low = np.sin(2 * np.pi * 95 * t(dur)) * env(n, 0.003, 0.18, 7) * 0.5      # a dull thud in the ice
    return bp(x, 900, 9000) + body + low

def shatter(seed, dur=1.1):
    r = np.random.default_rng(seed); n = int(SR * dur); x = np.zeros(n)
    x += bp(r.normal(0, 1, n), 2500, 10000) * env(n, 0.001, 0.12, 9) * 0.8    # the burst
    for _ in range(40):   # shards ringing as they fall: short glassy pings
        at = int((0.02 + abs(r.normal(0, 0.25))) * SR); at = min(at, n - 9000)
        f = r.uniform(2600, 7200); d = r.uniform(0.04, 0.18); m = int(d * SR)
        ping = np.sin(2 * np.pi * f * t(d)) * np.exp(-np.linspace(0, 7, m)) * r.uniform(0.15, 0.5)
        x[at:at + m] += ping
    return x

def heartbeat(seed, dur=0.9):
    r = np.random.default_rng(seed); x = np.zeros(int(SR * dur))
    for at, amp, f in ((0.0, 1.0, 52), (0.22, 0.7, 58)):   # lub - dub
        d = 0.18; m = int(d * SR); s = int(at * SR)
        tone = np.sin(2 * np.pi * f * t(d) * (1 - 0.3 * t(d) / d)) * np.exp(-np.linspace(0, 6, m))
        thump = lp(r.normal(0, 1, m), 180) * np.exp(-np.linspace(0, 10, m)) * 0.6
        x[s:s + m] += amp * (tone + thump)
    return lp(x, 400)

def freeze(seed, dur=0.9):
    r = np.random.default_rng(seed); n = int(SR * dur)
    hiss = bp(r.normal(0, 1, n), 3000, 11000) * env(n, 0.25, 0.4, 3)          # cold air hissing in
    sweep = np.sin(2 * np.pi * np.cumsum(np.linspace(1800, 5200, n)) / SR) * env(n, 0.05, 0.5, 4) * 0.15
    tick = np.zeros(n)
    for _ in range(25):   # tiny crystals forming
        at = r.integers(int(0.2 * SR), n - 400)
        tick[at:at + 300] += r.normal(0, 1, 300) * np.exp(-np.arange(300) / 30) * 0.4
    return hiss * 0.6 + sweep + bp(tick, 3000, 9000)

def splash(seed, dur=0.6):
    r = np.random.default_rng(seed); n = int(SR * dur)
    x = lp(r.normal(0, 1, n), 1600) * env(n, 0.004, 0.2, 7)                    # the wet slap
    for _ in range(10):   # drops pattering after
        at = r.integers(int(0.08 * SR), n - 2000); m = 1500
        f = r.uniform(300, 900)
        x[at:at + m] += np.sin(2 * np.pi * np.linspace(f, f * 2.2, m) * np.arange(m) / SR) * np.exp(-np.linspace(0, 8, m)) * 0.25
    return x

made = {}
for name, fn in (("ice_crack", crack), ("ice_shatter", shatter), ("heartbeat", heartbeat), ("freeze", freeze), ("blood_splash", splash)):
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
