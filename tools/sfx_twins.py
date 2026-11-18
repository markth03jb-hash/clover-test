"""Self-made sounds for the Masked Twins Grimoire (synthesised here, no samples): the swap chimes (rising into Sol,
falling into Nyx), a sun flare, a frost shimmer, and the eclipse's toll. Writes the ogg files and the sounds.json entries.
python3 tools/sfx_twins.py   (then: git checkout the other .ogg files - importing sfx_synth2 re-encodes them)"""
import numpy as np, json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from sfx_synth2 import SR, t, env, bp, lp, write, OUT


def noise(r, n): return r.normal(0, 1, n)
def tone(f, tt, ph=0.0): return np.sin(2 * np.pi * f * tt + ph)


def bell(f, tt, decay):   # a struck bell: a few inharmonic partials, each fading at its own rate
    return sum(a * tone(f * m, tt) * np.exp(-tt * decay * d) for m, a, d in ((1, 1.0, 1.0), (2.0, 0.5, 1.6), (2.76, 0.35, 2.2), (5.4, 0.15, 3.5)))


def chime(seed, up, dur=0.9):   # three bell notes, a fifth and an octave, up into the sun or down into the moon
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    base = (660 if up else 990) * (1 + r.uniform(-0.01, 0.01))
    notes = (1.0, 1.5, 2.0) if up else (1.0, 0.75, 0.5)
    x = np.zeros(n)
    for i, k in enumerate(notes):
        at = i * 0.09; m = tt >= at
        x[m] += bell(base * k, tt[m] - at, 4.5) * (0.6 if i < 2 else 0.8)
    shimmer = bp(noise(r, n), 5000, 11000) * np.exp(-tt * 9) * 0.15
    return (x + shimmer) * env(n, 0.002, dur, 1.4)


def sun_flare(seed, dur=0.8):   # a hot roar blooming out, a bright ring on top
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    roar = bp(noise(r, n), 200, 3000) * np.sin(np.pi * np.clip(tt / dur, 0, 1)) ** 0.7
    crackle = bp(noise(r, n), 2500, 8000) * (r.random(n) < 0.004) * 6
    ring = bell(1320 + r.uniform(-30, 30), tt, 5) * 0.25
    return roar + crackle * np.exp(-tt * 3) + ring


def moon_shimmer(seed, dur=0.7):   # glassy frost: high detuned sines glittering, a cold breath under them
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    glitter = sum(tone(f, tt) * (0.5 + 0.5 * np.sin(2 * np.pi * (7 + 3 * i) * tt + i)) for i, f in enumerate((2400, 3170, 4020, 5330))) * 0.18
    breath = bp(noise(r, n), 1500, 6000) * 0.35
    return (glitter + breath) * env(n, 0.01, dur, 2.2)


def eclipse_toll(seed, dur=2.2):   # a deep bell tolled once, a low swell, a high gold-and-silver chord over it
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    toll = bell(110, tt, 1.1) * 1.2
    swell = lp(noise(r, n), 180) * np.sin(np.pi * np.clip(tt / dur, 0, 1)) * 2.0
    chord = sum(bell(f, tt, 1.6) for f in (880, 1320, 1760)) * 0.15
    return (toll + swell + chord) * env(n, 0.003, dur, 1.2)


made = {}
for name, fn in (("twin_rise", lambda s: chime(s, True)), ("twin_fall", lambda s: chime(s, False)), ("sun_flare", sun_flare),
                 ("moon_shimmer", moon_shimmer), ("eclipse_toll", eclipse_toll)):
    made[name] = []
    for i in range(1, 4):
        write(f"{name}_{i}", fn(100 * i + len(name)))
        made[name].append(f"iron_spell_grimoire:sfx/{name}_{i}")
sj = os.path.join(OUT, "..", "..", "sounds.json")
d = json.load(open(sj))
for name, files in made.items():
    d["sfx." + name] = {"sounds": [{"name": f} for f in files]}
json.dump(d, open(sj, "w"), indent=2)
print("twins sounds written")
