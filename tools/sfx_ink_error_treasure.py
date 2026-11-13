"""Self-made sounds for the Ink, Error and Treasure Grimoires (synthesised here, no samples):
brush (a brush sweeping across paper), ink_splash (a wet slap and drops), glitch (a stuttering bit-crushed buzz),
error_beep (a harsh two-tone beep), portal_open (a shimmering golden chime opening), blade_fly (a whoosh with a
ring of steel), chain_rattle (links clinking). Writes the ogg files and the sounds.json entries."""
import numpy as np, json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from sfx_synth2 import SR, t, env, bp, lp, write, OUT


def brush(seed, dur=0.5):
    r = np.random.default_rng(seed); n = int(SR * dur); tt = t(dur)
    sweep = np.sin(np.pi * np.clip(tt / dur, 0, 1)) ** 1.5
    x = bp(r.normal(0, 1, n), 1500, 6000) * sweep * 0.7
    bristle = bp(r.normal(0, 1, n), 4000, 9000) * (r.random(n) < 0.02) * 2.0 * sweep
    return x + bristle


def splash(seed, dur=0.7):
    r = np.random.default_rng(seed); n = int(SR * dur)
    x = lp(r.normal(0, 1, n), 1200) * env(n, 0.003, 0.18, 8)
    for _ in range(8):
        at = r.integers(int(0.06 * SR), n - 2500); m = 2000
        f = r.uniform(250, 700)
        x[at:at + m] += np.sin(2 * np.pi * np.linspace(f, f * 2.4, m) * np.arange(m) / SR) * np.exp(-np.linspace(0, 7, m)) * 0.3
    return x


def glitch(seed, dur=0.6):
    r = np.random.default_rng(seed); n = int(SR * dur); tt = t(dur)
    f = np.repeat(r.uniform(90, 900, 40), n // 40 + 1)[:n]           # the pitch jumps about
    sq = np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR))
    gate = np.repeat(r.random(60) > 0.3, n // 60 + 1)[:n]          # and cuts out
    x = sq * gate * 0.5 + bp(r.normal(0, 1, n), 2000, 8000) * gate * 0.3
    x = np.round(x * 6) / 6                                          # crushed
    return x * env(n, 0.002, 0.5, 2)


def beep(seed, dur=0.45):
    tt = t(dur); n = len(tt)
    f = np.where(tt < 0.2, 880, 660)
    x = np.sign(np.sin(2 * np.pi * f * tt)) * 0.45
    x *= ((tt < 0.18) | ((tt > 0.22) & (tt < 0.42)))
    return lp(x, 5000)


def portal(seed, dur=1.1):
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    x = sum(np.sin(2 * np.pi * f * (1 + 0.3 * tt) * tt + r.uniform(0, 6)) * np.exp(-tt * (2 + k)) for k, f in enumerate((660, 990, 1320, 1760)))
    shimmer = bp(r.normal(0, 1, n), 5000, 10000) * env(n, 0.2, 0.6, 3) * 0.3
    whoosh = bp(r.normal(0, 1, n), 300, 2000) * env(n, 0.15, 0.5, 3) * 0.4
    return x * 0.35 * env(n, 0.05, 0.9, 2) + shimmer + whoosh


def blade(seed, dur=0.7):
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    whoosh = bp(r.normal(0, 1, n), 600, 4000) * np.sin(np.pi * np.clip(tt / 0.35, 0, 1)) ** 2 * (tt < 0.35) * 0.8
    f0 = r.uniform(2400, 3200)
    ring = (np.sin(2 * np.pi * f0 * tt) + 0.4 * np.sin(2 * np.pi * f0 * 2.7 * tt)) * np.exp(-tt * 5) * 0.3
    return whoosh + ring


def chain(seed, dur=0.9):
    r = np.random.default_rng(seed); n = int(SR * dur); x = np.zeros(n)
    for _ in range(16):
        at = int(abs(r.normal(0.15, 0.15)) * SR) % (n - 6000); m = 5000
        f = r.uniform(1800, 4200); tt = np.arange(m) / SR
        x[at:at + m] += (np.sin(2 * np.pi * f * tt) + 0.5 * np.sin(2 * np.pi * f * 2.3 * tt)) * np.exp(-tt * 40) * r.uniform(0.2, 0.5)
    return x


made = {}
for name, fn in (("brush", brush), ("ink_splash", splash), ("glitch", glitch), ("error_beep", beep), ("portal_open", portal), ("blade_fly", blade), ("chain_rattle", chain)):
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
