"""Self-made sounds for elite mobs (server/EliteTraits), synthesised here with no samples.
python3 tools/sfx_elite.py   (writes the .ogg files and their sounds.json entries; touches nothing else)"""
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



def noise(r, n): return r.normal(0, 1, n)
def env(tt, a, d): return np.clip(tt / a, 0, 1) * np.exp(-np.clip(tt - a, 0, None) * d)


def crack(seed):   # a stone plate cracking off
    r = np.random.default_rng(seed); tt = t(0.6); n = len(tt)
    x = bp(noise(r, n), 800, 5000) * np.exp(-tt * 18) + tone(r.uniform(140, 190), tt) * np.exp(-tt * 25) * 0.6
    for _ in range(5):
        at = r.uniform(0.03, 0.35); m = tt >= at; k = tt[m] - at
        x[m] += bp(noise(r, m.sum()), 1500, 7000) * np.exp(-k * 60) * 0.5
    return norm(x)

def windup(seed):   # a rising, straining hum
    r = np.random.default_rng(seed); tt = t(1.0); n = len(tt)
    f = 80 + 160 * tt ** 1.5
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = (np.sin(ph) + 0.4 * np.sin(2 * ph) + 0.2 * np.sign(np.sin(3 * ph))) * np.clip(tt / 0.8, 0, 1)
    return norm(lp(x, 2000) + bp(noise(r, n), 300, 1200) * tt * 0.3)

def slam(seed):   # a heavy ground slam
    r = np.random.default_rng(seed); tt = t(1.0); n = len(tt)
    x = tone(48, tt) * np.exp(-tt * 6) + lp(noise(r, n), 400) * np.exp(-tt * 9) * 1.2 + bp(noise(r, n), 1000, 4000) * np.exp(-tt * 30) * 0.5
    return norm(x)

def orb(seed):   # a warbling, hollow orb
    r = np.random.default_rng(seed); tt = t(0.9); n = len(tt)
    f = 330 * (1 + 0.08 * np.sin(2 * np.pi * 7 * tt))
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = (np.sin(ph) + 0.5 * np.sin(1.5 * ph)) * env(tt, 0.05, 2.5)
    return norm(x + bp(noise(r, n), 2000, 6000) * env(tt, 0.01, 8) * 0.2)

def dagger(seed):   # a thrown blade's whistle
    r = np.random.default_rng(seed); tt = t(0.45); n = len(tt)
    x = bp(noise(r, n), 2500, 7000) * np.sin(np.pi * np.clip(tt / 0.45, 0, 1)) + tone(1800 - 900 * tt, tt) * 0.15 * np.exp(-tt * 6)
    return norm(x)

def leech(seed):   # a wet, draining slurp
    r = np.random.default_rng(seed); tt = t(0.8); n = len(tt)
    f = 600 - 450 * tt
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(ph) * (0.6 + 0.4 * np.sin(2 * np.pi * 18 * tt)) * env(tt, 0.05, 3) + lp(noise(r, n), 900) * env(tt, 0.02, 4) * 0.4
    return norm(x)

def disrupt(seed):   # a crackling zap that cuts out
    r = np.random.default_rng(seed); tt = t(0.5); n = len(tt)
    x = bp(noise(r, n), 1500, 9000) * (r.random(n) < 0.25) * np.exp(-tt * 7) + np.sign(np.sin(2 * np.pi * 120 * tt)) * 0.3 * np.exp(-tt * 10)
    return norm(x)

def reflect(seed):   # a bright glassy ping
    r = np.random.default_rng(seed); tt = t(0.9); n = len(tt)
    return norm(bell(r.uniform(1300, 1500), tt, 3) + bell(2100, tt, 4) * 0.4)

def chain(seed):   # rattling links snapping taut
    r = np.random.default_rng(seed); tt = t(0.8); n = len(tt)
    x = np.zeros(n)
    at = 0.0
    while at < 0.55:
        m = tt >= at; k = tt[m] - at
        x[m] += bell(r.uniform(2400, 3600), k, 30) * 0.4 + bp(noise(r, m.sum()), 3000, 9000) * np.exp(-k * 80) * 0.4
        at += r.uniform(0.02, 0.06)
    m = tt >= 0.55; k = tt[m] - 0.55
    x[m] += tone(120, k) * np.exp(-k * 20) + bell(1800, k, 12) * 0.5
    return norm(x)

def dash(seed):   # a rushing whoosh
    r = np.random.default_rng(seed); tt = t(0.6); n = len(tt)
    x = bp(noise(r, n), 300, 2500) * np.sin(np.pi * np.clip(tt / 0.6, 0, 1)) ** 2 + lp(noise(r, n), 200) * np.exp(-tt * 8) * 0.5
    return norm(x)

def split(seed):   # something wet tearing in two
    r = np.random.default_rng(seed); tt = t(0.7); n = len(tt)
    x = lp(noise(r, n), 1500) * env(tt, 0.02, 7) + tone(220 - 120 * tt, tt) * env(tt, 0.01, 6) * 0.6
    return norm(x)

def tick(seed):   # a fuse ticking faster
    r = np.random.default_rng(seed); tt = t(1.5); n = len(tt)
    x = np.zeros(n); at = 0.0; gap = 0.3
    while at < 1.45:
        m = tt >= at; k = tt[m] - at
        x[m] += bell(1600, k, 40) * 0.6 + bp(noise(r, m.sum()), 3000, 8000) * np.exp(-k * 120) * 0.3
        at += gap; gap = max(0.07, gap * 0.75)
    return norm(x)

def boom(seed):   # a burst of force
    r = np.random.default_rng(seed); tt = t(1.4); n = len(tt)
    x = lp(noise(r, n), 700) * np.exp(-tt * 4) * 1.4 + tone(40, tt) * np.exp(-tt * 5) + bp(noise(r, n), 1500, 6000) * np.exp(-tt * 20) * 0.4
    return norm(x)

def bite(seed):   # a wooden lid snapping shut, with a growl
    r = np.random.default_rng(seed); tt = t(0.7); n = len(tt)
    x = bp(noise(r, n), 500, 3000) * np.exp(-tt * 40) * 1.2 + tone(90, tt) * np.exp(-tt * 18)
    g = (np.sign(np.sin(2 * np.pi * 70 * tt)) * 0.5 + lp(noise(r, n), 400)) * env(tt, 0.05, 6) * 0.4
    return norm(x + lp(g, 800))

def chest(seed):   # a lid creaking open, coins
    r = np.random.default_rng(seed); tt = t(1.1); n = len(tt)
    f = 180 + 80 * np.sin(np.pi * np.clip(tt / 0.6, 0, 1))
    creak = bp(np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR)) * r.uniform(0.6, 1, n), 300, 1800) * (tt < 0.6) * 0.4
    x = creak.copy()
    for k0 in range(6):
        at = 0.55 + k0 * r.uniform(0.04, 0.09); m = tt >= at; k = tt[m] - at
        x[m] += bell(r.uniform(2800, 4200), k, 18) * 0.35
    return norm(x)

def wail(seed):   # a thin, falling ghostly wail
    r = np.random.default_rng(seed); tt = t(1.4); n = len(tt)
    f = 700 - 260 * tt + 25 * np.sin(2 * np.pi * 5 * tt)
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = (np.sin(ph) + 0.3 * np.sin(2 * ph)) * env(tt, 0.2, 1.5) + bp(noise(r, n), 600, 2500) * env(tt, 0.2, 2) * 0.3
    return norm(lp(x, 3000))

def rise(seed):   # a gasp and a roar back to life
    r = np.random.default_rng(seed); tt = t(1.2); n = len(tt)
    f = 90 + 70 * np.clip(tt / 0.5, 0, 1)
    ph = 2 * np.pi * np.cumsum(f) / SR
    g = (np.sign(np.sin(ph)) * 0.5 + np.sin(2 * ph)) * env(tt, 0.25, 2.5)
    x = lp(g, 1500) + bp(noise(r, n), 300, 1500) * env(tt, 0.05, 6) * 0.5 + bell(520, tt, 2) * 0.25
    return norm(x)

def portal(seed):   # a tearing, swirling rift
    r = np.random.default_rng(seed); tt = t(1.6); n = len(tt)
    sw = 0.5 + 0.5 * np.sin(2 * np.pi * (2 + 4 * tt) * tt)
    x = bp(noise(r, n), 200, 1800) * sw * env(tt, 0.1, 1.5) + tone(60, tt) * env(tt, 0.05, 1.8) * 0.6
    x += bp(noise(r, n), 2500, 8000) * np.exp(-tt * 12) * 0.5
    return norm(x)


SOUNDS = {"elite_crack": crack, "elite_windup": windup, "elite_slam": slam, "elite_orb": orb, "elite_dagger": dagger, "elite_leech": leech,
          "elite_disrupt": disrupt, "elite_reflect": reflect, "elite_chain": chain, "elite_dash": dash, "elite_split": split, "elite_tick": tick,
          "elite_boom": boom, "elite_bite": bite, "elite_chest": chest, "elite_wail": wail, "elite_rise": rise, "elite_portal": portal}

if __name__ == "__main__":
    sj = os.path.join(OUT, "..", "..", "sounds.json")
    d = json.load(open(sj))
    for name, fn in SOUNDS.items():
        files = []
        for i in range(1, 3):
            encode(f"{name}_{i}", fn(500 * i + len(name)))
            files.append({"name": f"iron_spell_grimoire:sfx/{name}_{i}"})
        d["sfx." + name] = {"sounds": files}
    json.dump(d, open(sj, "w"), indent=2)
    print("elite sounds written:", len(SOUNDS))
