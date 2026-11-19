"""More self-made sounds for the Architect of Ruin Grimoire (synthesised here, no samples): the garrison's three orders,
an overcharged tower's hum, the breach alarm, the prestige fanfare, and four seamless ambience loops that play inside
your walls (wind and creaking timber; hammers and chisels; a forge's roar and anvil; a distant bell).
python3 tools/sfx_architect2.py   (writes the .ogg files and adds their sounds.json entries; touches nothing else)"""
import json, os, subprocess, tempfile
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, lfilter

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/iron_spell_grimoire/sounds/sfx")


def t(d): return np.arange(int(SR * d)) / SR
def bp(x, lo, hi, o=2): b, a = butter(o, [lo / (SR / 2), hi / (SR / 2)], "band"); return lfilter(b, a, x)
def lp(x, f, o=2): b, a = butter(o, f / (SR / 2)); return lfilter(b, a, x)
def tone(f, tt, ph=0.0): return np.sin(2 * np.pi * f * tt + ph)
def norm(x, peak=0.9): m = np.max(np.abs(x)) or 1; return x / m * peak
def bell(f, tt, decay):
    return sum(a * tone(f * m, tt) * np.exp(-tt * decay * d) for m, a, d in ((1, 1.0, 1.0), (2.0, 0.5, 1.6), (2.76, 0.35, 2.2), (5.4, 0.15, 3.5)))


def encode(name, x, fade=True):
    x = x / (np.max(np.abs(x)) + 1e-9) * 0.85
    if fade:
        f = int(0.01 * SR); x[-f:] *= np.linspace(1, 0, f)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as fh: path = fh.name
    wavfile.write(path, SR, (x * 32767).astype(np.int16))
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "4", os.path.join(OUT, name + ".ogg")], check=True)
    os.remove(path)


def seamless(x, xf=1.0):
    """A loop with no click: the last xf seconds cross-fade into the start (equal power), then are cut off."""
    n = int(xf * SR)
    head, body, tail = x[:n], x[n:-n] if n else x, x[-n:]
    k = np.linspace(0, np.pi / 2, n)
    joined = tail * np.cos(k) + head * np.sin(k)
    return np.concatenate([joined, body])


def brass(f, tt, bend=0.0):
    ph = 2 * np.pi * np.cumsum(f * (1 + bend * np.clip(tt / 0.2, 0, 1))) / SR
    return sum(np.sin(k * ph) / k ** 0.8 for k in range(1, 9))


def note_env(tt, at, dur, a=0.03, r=0.12):
    x = np.clip((tt - at) / a, 0, 1) * np.clip((at + dur - tt) / r, 0, 1)
    return x * (tt >= at)


# ---------------------------------------------------------------------- the orders
def hold(seed):   # Hold the Gate: two low horn notes held, a drum under them
    r = np.random.default_rng(seed); tt = t(1.3); n = len(tt)
    f = 110 * (1 + 0.006 * r.uniform(-1, 1))
    x = brass(f, tt) * note_env(tt, 0.0, 0.55) + brass(f, tt) * note_env(tt, 0.6, 0.65, r=0.3)
    drum = sum(tone(62, np.clip(tt - at, 0, None)) * np.exp(-np.clip(tt - at, 0, None) * 14) * (tt >= at) for at in (0.0, 0.6))
    return norm(x * 0.6 + drum * 0.9 + lp(r.normal(0, 1, n), 300) * 0.04)


def charge(seed):   # Charge: three quick rising horn notes
    r = np.random.default_rng(seed); tt = t(1.0); n = len(tt)
    base = 147 * (1 + 0.006 * r.uniform(-1, 1))
    x = np.zeros(n)
    for i, (m, at, d) in enumerate(((1.0, 0.0, 0.16), (1.26, 0.18, 0.16), (1.5, 0.36, 0.6))):
        x += brass(base * m, tt, 0.02) * note_env(tt, at, d, r=0.06 if i < 2 else 0.35)
    return norm(x + bp(r.normal(0, 1, n), 300, 1500) * 0.03)


def fallback(seed):   # Fall Back: two falling notes, softer
    r = np.random.default_rng(seed); tt = t(1.1); n = len(tt)
    base = 165 * (1 + 0.006 * r.uniform(-1, 1))
    x = brass(base, tt) * note_env(tt, 0.0, 0.35) + brass(base * 0.75, tt) * note_env(tt, 0.4, 0.6, r=0.4)
    return norm(lp(x, 2200) + lp(r.normal(0, 1, n), 400) * 0.03)


def hum(seed):   # an overcharged tower: a rising electric-brass drone with a pulse
    r = np.random.default_rng(seed); tt = t(2.6); n = len(tt)
    f = 70 * (1 + 0.5 * np.clip(tt / 0.5, 0, 1))
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.3 * np.sin(3.01 * ph) + 0.15 * np.sign(np.sin(4 * ph))
    pulse = 0.75 + 0.25 * np.sin(2 * np.pi * 6 * tt)
    shape = np.clip(tt / 0.15, 0, 1) * np.clip((2.6 - tt) / 0.5, 0, 1)
    return norm(lp(x, 2400) * pulse * shape + bp(r.normal(0, 1, n), 3000, 8000) * 0.05 * shape)


def alarm(seed):   # the breach: a hurried alarm bell, struck four times
    r = np.random.default_rng(seed); tt = t(1.6); n = len(tt)
    x = np.zeros(n)
    for i in range(4):
        at = i * 0.22; m = tt >= at
        x[m] += bell(620 * (1 + 0.004 * r.uniform(-1, 1)), tt[m] - at, 3.0) * (1.0 - 0.1 * i)
    return norm(x)


def prestige(seed):   # rebuilt stronger: a bright chord of horns and bells swelling up
    r = np.random.default_rng(seed); tt = t(2.6); n = len(tt)
    x = np.zeros(n)
    for m, at in ((1.0, 0.0), (1.25, 0.12), (1.5, 0.24), (2.0, 0.42)):
        x += brass(131 * m, tt) * note_env(tt, at, 2.2 - at, a=0.15, r=0.8) * 0.5
        x += bell(523 * m, np.clip(tt - at, 0, None), 1.5) * 0.25 * (tt >= at)
    return norm(x + lp(r.normal(0, 1, n), 200) * np.exp(-tt * 2) * 0.2)


# ---------------------------------------------------------------------- the ambience loops (8 s each)
L = 8.0 + 1.0   # one extra second, cross-faded into the start


def amb_wind(seed):   # wind past the battlements, timbers creaking now and then
    r = np.random.default_rng(seed); tt = t(L); n = len(tt)
    gust = 0.6 + 0.4 * np.sin(2 * np.pi * tt / 4.5 + 1.0) * np.sin(2 * np.pi * tt / 2.9)
    wind = bp(r.normal(0, 1, n), 180, 900) * gust + bp(r.normal(0, 1, n), 900, 2600) * gust ** 2 * 0.3
    creak = np.zeros(n)
    for _ in range(3):
        at = r.uniform(0.3, L - 1.4); d = r.uniform(0.5, 0.9); m = (tt >= at) & (tt < at + d); k = tt[m] - at
        f = r.uniform(170, 260) * (1 + 0.15 * np.sin(np.pi * k / d))
        creak[m] += bp(np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR)) * r.uniform(0.6, 1.0, m.sum()), 300, 1800) * np.sin(np.pi * k / d) * 0.25
    return seamless(wind + creak)


def amb_works(seed):   # hammers and chisels on stone, somewhere inside the walls
    r = np.random.default_rng(seed); tt = t(L); n = len(tt)
    x = lp(r.normal(0, 1, n), 250) * 0.05
    at = 0.2
    while at < L - 0.3:
        m = tt >= at; k = tt[m] - at
        if r.random() < 0.65:   # a hammer on a chisel
            x[m] += (bell(r.uniform(1900, 2600), k, 22) * 0.35 + tone(r.uniform(140, 200), k) * np.exp(-k * 30) * 0.6
                     + bp(r.normal(0, 1, m.sum()), 2000, 7000) * np.exp(-k * 60) * 0.4)
        else:   # a mallet on timber
            x[m] += tone(r.uniform(90, 130), k) * np.exp(-k * 25) * 0.8 + lp(r.normal(0, 1, m.sum()), 800) * np.exp(-k * 40) * 0.5
        at += r.choice([0.42, 0.45, 0.5, 0.9, 1.3])
    return seamless(lp(x, 6000))


def amb_forge(seed):   # a forge's breathing roar and an anvil far off
    r = np.random.default_rng(seed); tt = t(L); n = len(tt)
    breath = 0.55 + 0.45 * np.clip(np.sin(2 * np.pi * tt / 3.0), 0, None) ** 2
    roar = lp(r.normal(0, 1, n), 500) * breath + bp(r.normal(0, 1, n), 1500, 6000) * (r.random(n) < 0.004) * 2
    anvil = np.zeros(n)
    for at in np.arange(0.7, L - 0.5, 0.95):
        if r.random() < 0.2: continue
        m = tt >= at; k = tt[m] - at
        anvil[m] += bell(r.uniform(1150, 1250), k, 9) * 0.25
    return seamless(roar * 0.8 + anvil)


def amb_bell(seed):   # a deep bell in the distance, once a loop
    r = np.random.default_rng(seed); tt = t(L); n = len(tt)
    at = 1.0; m = tt >= at; k = tt[m] - at
    x = lp(r.normal(0, 1, n), 150) * 0.03
    x[m] += lp(bell(98, k, 0.55) + bell(196.5, k, 0.9) * 0.3, 1400)
    return seamless(x, 1.0)


SHORT = {"fort_hold": hold, "fort_charge": charge, "fort_fallback": fallback, "fort_hum": hum, "fort_alarm": alarm, "fort_prestige": prestige}
LOOPS = {"fort_amb_wind": amb_wind, "fort_amb_works": amb_works, "fort_amb_forge": amb_forge, "fort_amb_bell": amb_bell}

made = {}
for name, fn in SHORT.items():
    made[name] = []
    for i in range(1, 3):
        encode(f"{name}_{i}", fn(300 * i + len(name)))
        made[name].append(f"iron_spell_grimoire:sfx/{name}_{i}")
for name, fn in LOOPS.items():
    encode(name, fn(77 + len(name)), fade=False)
    made[name] = [name]
sj = os.path.join(OUT, "..", "..", "sounds.json")
d = json.load(open(sj))
for name, files in made.items():
    if name in LOOPS: d["sfx." + name] = {"sounds": [{"name": "iron_spell_grimoire:sfx/" + name, "stream": False}]}
    else: d["sfx." + name] = {"sounds": [{"name": f} for f in files]}
json.dump(d, open(sj, "w"), indent=2)
print("architect sounds (2) written:", len(made))
