"""Self-made sounds for the Overdrive Grimoire (synthesised here, no samples): a blade being drawn, a rushing dash,
a monitor flatlining, a deep breath, a ribbon snapping out. Writes the ogg files and the sounds.json entries.
python3 tools/sfx_overdrive.py   (then: git checkout the other .ogg files - importing sfx_synth2 re-encodes them)"""
import numpy as np, json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from sfx_synth2 import SR, t, env, bp, lp, write, OUT


def noise(r, n): return r.normal(0, 1, n)
def tone(f, tt, ph=0.0): return np.sin(2 * np.pi * f * tt + ph)


def blade_draw(seed, dur=0.6):   # steel sliding out of a scabbard, ending in a bright ring
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    slide = bp(noise(r, n), 3000, 9000) * np.clip(tt / (dur * 0.45), 0, 1) * (tt < dur * 0.5) * 0.7
    ring = sum(tone(f, tt) * np.exp(-np.clip(tt - dur * 0.45, 0, None) * 6) * (tt > dur * 0.45) for f in (2300 + r.uniform(-80, 80), 3450, 5200)) * 0.25
    whoosh = bp(noise(r, n), 500, 2500) * np.exp(-((tt - dur * 0.45) / 0.06) ** 2) * 0.8
    return (slide + ring + whoosh) * env(n, 0.005, dur * 0.9, 2)


def rush(seed, dur=0.5):   # a hard burst of air past your ears, a heartbeat thump under it
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    air = bp(noise(r, n), 300, 4000) * np.sin(np.pi * np.clip(tt / dur, 0, 1)) ** 1.5
    thump = lp(noise(r, n), 120) * np.exp(-tt * 18) * 3
    return air + thump


def flatline(seed, dur=1.3):   # a monitor's two beeps, then the long flat tone
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    f = 980 + r.uniform(-20, 20)
    beep = lambda a, b: tone(f, tt) * ((tt > a) & (tt < b))
    x = (beep(0.0, 0.09) + beep(0.28, 0.37)) * 0.5 + tone(f, tt) * (tt > 0.56) * 0.45
    return x * env(n, 0.003, dur, 1.5)


def breath(seed, dur=1.1):   # a long deep breath in and out
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    inhale = bp(noise(r, n), 500, 2800) * np.sin(np.pi * np.clip(tt / (dur * 0.5), 0, 1)) ** 2 * (tt < dur * 0.5)
    exhale = bp(noise(r, n), 250, 1600) * np.sin(np.pi * np.clip((tt - dur * 0.5) / (dur * 0.5), 0, 1)) ** 2 * (tt >= dur * 0.5)
    return (inhale * 0.6 + exhale * 0.8)


def ribbon(seed, dur=0.45):   # cloth cracking out like a whip and fluttering
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    snap = bp(noise(r, n), 1500, 7000) * np.exp(-((tt - 0.12) / 0.015) ** 2) * 2
    flutter = bp(noise(r, n), 400, 2000) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 26 * tt))) * env(n, 0.02, dur * 0.8, 3) * 0.5
    return snap + flutter


made = {}
for name, fn in (("blade_draw", blade_draw), ("rush", rush), ("flatline", flatline), ("breath", breath), ("ribbon", ribbon)):
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
