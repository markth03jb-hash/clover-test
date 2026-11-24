"""Self-made sounds for the Architect of Ruin Grimoire (synthesised here, no samples): stone grinding up out of the
ground, a war horn, masonry crumbling, bells, a hammer's blow, a trebuchet's throw, arrows, ley light, a flask, a wave
and coins. Writes the ogg files and the sounds.json entries.
python3 tools/sfx_architect.py   (then: git checkout the other .ogg files - importing sfx_synth2 re-encodes them)"""
import numpy as np, json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from sfx_synth2 import SR, t, env, bp, lp, write, OUT


def noise(r, n): return r.normal(0, 1, n)
def tone(f, tt, ph=0.0): return np.sin(2 * np.pi * f * tt + ph)
def norm(x, peak=0.9): m = np.max(np.abs(x)) or 1; return x / m * peak


def bell(f, tt, decay):
    return sum(a * tone(f * m, tt) * np.exp(-tt * decay * d) for m, a, d in ((1, 1.0, 1.0), (2.0, 0.5, 1.6), (2.76, 0.35, 2.2), (5.4, 0.15, 3.5)))


def grind(seed, dur=0.9):   # stone grinding: low rumble with gritty crackle, swelling then settling
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    rumble = lp(noise(r, n), 160) * 3.0
    grit = bp(noise(r, n), 600, 3000) * (r.random(n) < 0.06) * 2.5
    shape = np.sin(np.pi * np.clip(tt / dur, 0, 1)) ** 0.6
    thud = tone(55, tt) * np.exp(-tt * 9) * (tt > dur * 0.75)
    return norm((rumble + grit) * shape + thud * 0.8)


def horn(seed, dur=1.4):   # a war horn: a brassy note with a slow swell and a slight bend up
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    f0 = 146 * (1 + 0.01 * r.uniform(-1, 1))
    f = f0 * (1 + 0.03 * np.clip(tt / 0.25, 0, 1))
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = sum(np.sin(k * ph) / k ** 0.8 for k in range(1, 9))
    x += bp(noise(r, n), 200, 1200) * 0.05
    return norm(x * env(n, 0.25, dur, 2.0))


def crumble(seed, dur=0.8):   # masonry falling apart: many small knocks over a dusty hiss
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    x = bp(noise(r, n), 300, 5000) * np.exp(-tt * 4) * 0.3
    for _ in range(18):
        at = int(r.uniform(0, dur * 0.7) * SR); L = int(0.06 * SR)
        if at + L >= n: continue
        k = np.arange(L) / SR
        x[at:at + L] += np.sin(2 * np.pi * r.uniform(120, 420) * k) * np.exp(-k * 60) * r.uniform(0.5, 1.2)
    return norm(x)


def build(seed, dur=0.6):   # a block set in place: a solid knock, a brass tick
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    knock = tone(90, tt) * np.exp(-tt * 18) + lp(noise(r, n), 900) * np.exp(-tt * 30) * 0.8
    tick = bell(1800 + r.uniform(-60, 60), tt, 9) * 0.25 * (tt > 0.08)
    return norm(knock + tick)


def gate(seed, dur=0.9):   # a portcullis slamming: chains rattling down, an iron crash
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    rattle = bp(noise(r, n), 2000, 7000) * (r.random(n) < 0.03) * 3 * (tt < 0.3)
    crash = (bp(noise(r, n), 150, 2500) * 1.5 + tone(70, tt) * 1.2) * np.exp(-np.clip(tt - 0.3, 0, None) * 8) * (tt >= 0.3)
    ring = bell(410, np.clip(tt - 0.3, 0, None), 4) * 0.3 * (tt >= 0.3)
    return norm(rattle + crash + ring)


def toll(seed, dur=2.0, f=196):
    r = np.random.default_rng(seed); tt = t(dur)
    return norm(bell(f * (1 + 0.005 * r.uniform(-1, 1)), tt, 1.4) + bell(f * 2.01, tt, 2.5) * 0.3)


def hammer(seed, dur=1.0):   # a giant blow: a deep boom and stone crack
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    boom = tone(48, tt) * np.exp(-tt * 5) * 1.5 + lp(noise(r, n), 300) * np.exp(-tt * 7)
    crack = bp(noise(r, n), 1500, 6000) * np.exp(-tt * 25) * 0.8
    return norm(boom + crack)


def arrow(seed, dur=0.35):   # a twang and a hiss
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    twang = tone(220 + r.uniform(-20, 20), tt) * np.exp(-tt * 18) * 0.6
    hiss = bp(noise(r, n), 3000, 9000) * np.exp(-tt * 9) * np.clip(tt / 0.03, 0, 1) * 0.5
    return norm(twang + hiss)


def ley(seed, dur=0.6):   # ley light: a bright rising shimmer
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    f = 600 * (1 + 1.5 * tt / dur)
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(ph) * 0.5 + np.sin(ph * 2.01) * 0.25 + bp(noise(r, n), 4000, 10000) * 0.08
    return norm(x * env(n, 0.01, dur, 2.5))


def fire(seed, dur=0.6):
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    roar = bp(noise(r, n), 150, 2500) * np.sin(np.pi * np.clip(tt / dur, 0, 1))
    crackle = bp(noise(r, n), 2000, 8000) * (r.random(n) < 0.01) * 4
    return norm(roar + crackle)


def bolt(seed, dur=0.5):   # a ballista: a heavy thunk, a whoosh
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    thunk = tone(80, tt) * np.exp(-tt * 22) * 1.4 + lp(noise(r, n), 600) * np.exp(-tt * 35)
    whoosh = bp(noise(r, n), 500, 3000) * np.exp(-((tt - 0.12) / 0.08) ** 2) * 0.7
    return norm(thunk + whoosh)


def blades(seed, dur=0.8):   # steel raining down: several short metal rings
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    x = np.zeros(n)
    for i in range(6):
        at = 0.06 + i * 0.09; m = tt >= at
        x[m] += bell(900 + r.uniform(-150, 300), tt[m] - at, 12) * 0.5 + bp(noise(r, int(m.sum())), 2000, 8000) * np.exp(-(tt[m] - at) * 40) * 0.6
    return norm(x)


def page(seed, dur=0.5):   # a page turning and a soft chime
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    flip = bp(noise(r, n), 1500, 7000) * np.sin(np.pi * np.clip(tt / 0.25, 0, 1)) * (tt < 0.25) * 0.6
    chime = bell(1320, np.clip(tt - 0.15, 0, None), 6) * 0.35 * (tt > 0.15)
    return norm(flip + chime)


def flask(seed, dur=0.6):   # glass breaking and a fizz
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    glass = sum(tone(f, tt) * np.exp(-tt * d) for f, d in ((2900, 30), (4100, 40), (5300, 50))) * 0.4
    burst = bp(noise(r, n), 800, 6000) * np.exp(-tt * 14)
    fizz = bp(noise(r, n), 5000, 12000) * np.exp(-tt * 3) * 0.3
    return norm(glass + burst + fizz)


def flood(seed, dur=1.2):   # a rushing wave
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    wave = lp(noise(r, n), 1200) * np.sin(np.pi * np.clip(tt / dur, 0, 1)) ** 0.5
    spray = bp(noise(r, n), 3000, 9000) * np.exp(-((tt - 0.5) / 0.3) ** 2) * 0.4
    return norm(wave + spray)


def coins(seed, dur=0.8):
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    x = np.zeros(n)
    for _ in range(10):
        at = r.uniform(0, 0.5); m = tt >= at
        x[m] += bell(r.uniform(2400, 3600), tt[m] - at, 14) * r.uniform(0.3, 0.7)
    return norm(x)


def launch(seed, dur=0.9):   # a trebuchet's arm: a creak and a big swoosh
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    creak = bp(noise(r, n), 300, 900) * (np.sin(2 * np.pi * 35 * tt) > 0.6) * (tt < 0.3) * 0.8
    swoosh = bp(noise(r, n), 250, 2000) * np.exp(-((tt - 0.45) / 0.15) ** 2) * 1.2
    thud = tone(60, tt) * np.exp(-tt * 15) * 0.8
    return norm(creak + swoosh + thud)


def collapse(seed, dur=2.0):
    a = crumble(seed, dur) * 0.8
    tt = t(dur)
    return norm(a + hammer(seed + 1, dur) * 0.6 + lp(np.random.default_rng(seed).normal(0, 1, len(tt)), 120) * np.exp(-tt * 1.5) * 1.5)


SOUNDS = {"fort_horn": horn, "fort_build": build, "fort_wall": grind, "fort_gate": gate, "fort_collapse": collapse, "fort_crumble": crumble,
          "fort_arrow": arrow, "fort_ley": ley, "fort_fire": fire, "fort_bolt": bolt, "fort_blades": blades, "fort_page": page,
          "fort_bell": toll, "fort_flask": flask, "fort_hammer": hammer, "fort_flood": flood, "fort_coins": coins, "fort_launch": launch}
made = {}
for name, fn in SOUNDS.items():
    made[name] = []
    for i in range(1, 3):
        write(f"{name}_{i}", fn(100 * i + len(name)))
        made[name].append(f"iron_spell_grimoire:sfx/{name}_{i}")
sj = os.path.join(OUT, "..", "..", "sounds.json")
d = json.load(open(sj))
for name, files in made.items():
    d["sfx." + name] = {"sounds": [{"name": f} for f in files]}
json.dump(d, open(sj, "w"), indent=2)
print("architect sounds written:", len(made))
