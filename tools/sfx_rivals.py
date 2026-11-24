"""Self-made sounds for the four rivals (synthesised here, no samples):
Seren Vale - glass stepping, a fan of light blades, a ringing parry, White Noon's swell;
Brannoch Tor - a war horn, a shield wall locking, a greatsword cleave, a rampart slamming down;
the Bellmars - their bell, the swap, a bond shot, grief;
Mira Hollowell - a coin volley, a deal offered (quill and chime), a deal stamped, foreclosure;
and the shared ones: a rival yielding, the arena ring.
python3 tools/sfx_rivals.py   (writes the .ogg files and adds their sounds.json entries; touches nothing else)"""
import json, os, subprocess, tempfile
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, lfilter

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/iron_spell_grimoire/sounds/sfx")


def t(d): return np.arange(int(SR * d)) / SR
def bp(x, lo, hi, o=2): b, a = butter(o, [lo / (SR / 2), hi / (SR / 2)], "band"); return lfilter(b, a, x)
def lp(x, f, o=2): b, a = butter(o, f / (SR / 2)); return lfilter(b, a, x)
def hp(x, f, o=2): b, a = butter(o, f / (SR / 2), "high"); return lfilter(b, a, x)
def tone(f, tt, ph=0.0): return np.sin(2 * np.pi * f * tt + ph)
def norm(x, peak=0.9): m = np.max(np.abs(x)) or 1; return x / m * peak
def at(tt, a): k = np.clip(tt - a, 0, None); return k, (tt >= a)
def bell(f, tt, decay):
    return sum(a * tone(f * m, tt) * np.exp(-tt * decay * d) for m, a, d in ((1, 1.0, 1.0), (2.0, 0.5, 1.6), (2.76, 0.35, 2.2), (5.4, 0.15, 3.5)))
def glass(f, tt, decay):   # brighter, inharmonic partials: glass more than metal
    return sum(a * tone(f * m, tt) * np.exp(-tt * decay * d) for m, a, d in ((1, 1.0, 1.0), (2.32, 0.6, 1.4), (4.25, 0.4, 2.0), (6.63, 0.25, 2.8)))
def brass(f, tt, bend=0.0):
    ph = 2 * np.pi * np.cumsum(f * (1 + bend * np.clip(tt / 0.2, 0, 1))) / SR
    return sum(np.sin(k * ph) / k ** 0.8 for k in range(1, 9))
def note_env(tt, a0, dur, a=0.03, r=0.12):
    return np.clip((tt - a0) / a, 0, 1) * np.clip((a0 + dur - tt) / r, 0, 1) * (tt >= a0)


def encode(name, x):
    x = x / (np.max(np.abs(x)) + 1e-9) * 0.85
    f = int(0.01 * SR); x[-f:] *= np.linspace(1, 0, f)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as fh: path = fh.name
    wavfile.write(path, SR, (x * 32767).astype(np.int16))
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "4", os.path.join(OUT, name + ".ogg")], check=True)
    os.remove(path)


# ---------------------------------------------------------------------- Seren Vale
def glass_step(seed):   # a step through light: a quick upward shimmer, then a tinkle where she lands
    r = np.random.default_rng(seed); tt = t(0.8); n = len(tt)
    f = 900 * np.exp(np.clip(tt / 0.18, 0, 1) * 1.1)
    sweep = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 9)
    k, m = at(tt, 0.16)
    land = glass(r.uniform(2100, 2400), k, 7) * m * 0.6
    air = bp(r.normal(0, 1, n), 3000, 9000) * np.exp(-tt * 14) * 0.3
    return norm(sweep * 0.5 + land + air)


def blade_fan(seed):   # Spectrum Blades: several bright whistles fanning out, a chord of glass
    r = np.random.default_rng(seed); tt = t(1.0); n = len(tt)
    x = np.zeros(n)
    for i, m_ in enumerate((1.0, 1.26, 1.5, 1.88)):
        k, m = at(tt, i * 0.04)
        x += glass(620 * m_ * (1 + 0.003 * r.uniform(-1, 1)), k, 4.5) * m * 0.4
    whoosh = bp(r.normal(0, 1, n), 1500, 6000) * np.exp(-((tt - 0.08) / 0.07) ** 2) * 0.8
    return norm(x + whoosh)


def parry(seed):   # Mirror Parry: a hard ring of steel on glass, long tail
    r = np.random.default_rng(seed); tt = t(1.4); n = len(tt)
    click = hp(r.normal(0, 1, n), 2500) * np.exp(-tt * 120)
    ring = glass(1480 * (1 + 0.004 * r.uniform(-1, 1)), tt, 2.2) + bell(740, tt, 3.5) * 0.4
    return norm(click * 0.7 + ring)


def noon(seed):   # White Noon: a bright swell climbing to a choir-like chord, then a crack of light
    r = np.random.default_rng(seed); tt = t(2.6); n = len(tt)
    sw = np.clip(tt / 1.9, 0, 1) ** 2
    pad = sum(np.sin(2 * np.pi * f * tt + r.uniform(0, 6)) * (1 + 0.003 * np.sin(2 * np.pi * 5 * tt)) for f in (440, 554, 659, 880, 1108))
    k, m = at(tt, 1.95)
    crack = (hp(r.normal(0, 1, n), 1800) * np.exp(-k * 18) + glass(2600, k, 4)) * m
    return norm(pad * sw * np.clip((2.4 - tt) / 0.5, 0, 1) * 0.35 + crack * 0.8)


# ---------------------------------------------------------------------- Brannoch Tor
def warhorn(seed):   # one long, low, rough horn with a lift at the end
    r = np.random.default_rng(seed); tt = t(2.2); n = len(tt)
    f = 98 * (1 + 0.005 * r.uniform(-1, 1)) * (1 + 0.06 * np.clip((tt - 1.3) / 0.4, 0, 1))
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = sum(np.sin(k * ph) / k ** 0.7 for k in range(1, 12)) * note_env(tt, 0.0, 2.1, a=0.18, r=0.5)
    rasp = bp(r.normal(0, 1, n), 400, 1600) * note_env(tt, 0, 2.1, a=0.2, r=0.5) * 0.08
    return norm(lp(x, 1800) + rasp)


def shieldwall(seed):   # shields locking along a line: quick heavy clanks
    r = np.random.default_rng(seed); tt = t(1.2); n = len(tt)
    x = np.zeros(n)
    for i in range(5):
        k, m = at(tt, i * 0.11 + r.uniform(0, 0.02))
        x += (bell(r.uniform(310, 380), k, 14) * 0.6 + tone(r.uniform(80, 110), k) * np.exp(-k * 30) + bp(r.normal(0, 1, n), 1200, 5000) * np.exp(-k * 70) * 0.5) * m
    return norm(x)


def cleave(seed):   # a greatsword's heavy sweep and bite
    r = np.random.default_rng(seed); tt = t(0.9); n = len(tt)
    w = bp(r.normal(0, 1, n), 250, 1600) * np.exp(-((tt - 0.2) / 0.12) ** 2)
    k, m = at(tt, 0.3)
    hit = (tone(70, k) * np.exp(-k * 12) + hp(r.normal(0, 1, n), 1500) * np.exp(-k * 40) * 0.6 + bell(520, k, 10) * 0.3) * m
    return norm(w + hit)


def rampart(seed):   # a ghost-rampart slamming down: a deep boom and stone grinding
    r = np.random.default_rng(seed); tt = t(1.6); n = len(tt)
    f = 55 * (1 + 0.8 * np.exp(-tt * 9))
    boom = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 3.2)
    grind = lp(r.normal(0, 1, n), 700) * np.exp(-tt * 2.5) * (0.6 + 0.4 * np.sin(2 * np.pi * 13 * tt))
    return norm(boom + grind * 0.5)


# ---------------------------------------------------------------------- the Bellmar twins
def twin_bell(seed):   # their bell: one clear hand-bell, a twin a fifth above
    r = np.random.default_rng(seed); tt = t(1.6)
    f = 784 * (1 + 0.004 * r.uniform(-1, 1))
    k, m = at(tt, 0.06)
    return norm(bell(f, tt, 2.0) + bell(f * 1.5, k, 2.4) * m * 0.6)


def swap(seed):   # trading places: a reversed chime sucked in, then out
    r = np.random.default_rng(seed); tt = t(0.9); n = len(tt)
    rev = bell(1046, tt[::-1], 3.0) * np.clip(tt / 0.35, 0, 1) * (tt < 0.4)
    k, m = at(tt, 0.4)
    out = bell(1046 * 1.5, k, 3.0) * m
    air = bp(r.normal(0, 1, n), 2000, 7000) * np.exp(-((tt - 0.4) / 0.05) ** 2) * 0.6
    return norm(rev * 0.6 + out * 0.7 + air)


def bond(seed):   # Bond Shot: a pinging bolt that splits as it passes through
    r = np.random.default_rng(seed); tt = t(1.0); n = len(tt)
    f = 1400 * np.exp(-tt * 1.6)
    bolt = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 3)
    k, m = at(tt, 0.32)
    split = sum(bell(880 * q, k, 5) for q in (1.0, 1.26, 1.5)) * m * 0.4
    return norm(bolt * 0.6 + split)


def grief(seed):   # grief: a cracked bell, low and falling, a breath under it
    r = np.random.default_rng(seed); tt = t(2.4); n = len(tt)
    f = 392 * (1 - 0.06 * np.clip(tt / 1.5, 0, 1))
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = (np.sin(ph) + 0.5 * np.sin(2.01 * ph) + 0.4 * np.sin(2.76 * ph) * (1 + 0.5 * np.sin(2 * np.pi * 7 * tt))) * np.exp(-tt * 1.2)
    breath = bp(r.normal(0, 1, n), 300, 1200) * note_env(tt, 0.1, 1.6, a=0.5, r=0.8) * 0.15
    return norm(x + breath)


# ---------------------------------------------------------------------- Mira Hollowell
def coins(seed):   # a coin volley: a handful of coins flicked, ringing as they fly
    r = np.random.default_rng(seed); tt = t(0.9); n = len(tt)
    x = np.zeros(n)
    for i in range(7):
        k, m = at(tt, r.uniform(0, 0.25))
        x += bell(r.uniform(2600, 3600), k, 16) * m * r.uniform(0.3, 0.6)
    flick = bp(r.normal(0, 1, n), 3000, 9000) * np.exp(-tt * 30) * 0.4
    return norm(x + flick)


def deal(seed):   # a deal offered: a quill scratch and a soft two-note chime
    r = np.random.default_rng(seed); tt = t(1.3); n = len(tt)
    scratch = bp(r.normal(0, 1, n), 2500, 7000) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 9 * tt))) * note_env(tt, 0, 0.45, a=0.02, r=0.1) * 0.5
    k1, m1 = at(tt, 0.5); k2, m2 = at(tt, 0.68)
    chime = bell(988, k1, 3) * m1 * 0.5 + bell(1318, k2, 2.6) * m2 * 0.5
    return norm(scratch + chime)


def stamp(seed):   # a deal stamped: a wooden thump and a coin dropped on the ledger
    r = np.random.default_rng(seed); tt = t(1.0); n = len(tt)
    thump = tone(110, tt) * np.exp(-tt * 28) + lp(r.normal(0, 1, n), 900) * np.exp(-tt * 45) * 0.6
    k, m = at(tt, 0.18)
    coin = bell(3100, k, 7) * m * 0.4
    return norm(thump + coin)


def foreclose(seed):   # foreclosure: a heavy book slamming shut, coins pouring, a low toll
    r = np.random.default_rng(seed); tt = t(2.0); n = len(tt)
    slam = (tone(60, tt) * np.exp(-tt * 10) + lp(r.normal(0, 1, n), 1200) * np.exp(-tt * 20)) * 1.0
    pour = np.zeros(n)
    for _ in range(26):
        k, m = at(tt, r.uniform(0.1, 1.3))
        pour += bell(r.uniform(2400, 3800), k, 20) * m * 0.12
    k, m = at(tt, 0.05)
    toll = bell(147, k, 1.1) * m * 0.5
    return norm(slam + pour + toll)


# ---------------------------------------------------------------------- shared
def yield_(seed):   # a rival yields: a falling three-note phrase, gentle
    r = np.random.default_rng(seed); tt = t(2.0); n = len(tt)
    x = np.zeros(n)
    for i, f in enumerate((659, 587, 494)):
        k, m = at(tt, i * 0.28)
        x += bell(f, k, 2.2) * m * 0.5
    return norm(x + lp(r.normal(0, 1, n), 300) * np.exp(-tt * 2) * 0.05)


def arena(seed):   # the arena ring closing: a rising hum that settles
    r = np.random.default_rng(seed); tt = t(1.8); n = len(tt)
    f = 110 * (1 + 0.5 * np.clip(tt / 0.8, 0, 1))
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = (np.sin(ph) + 0.4 * np.sin(2 * ph) + 0.2 * np.sin(3.01 * ph)) * note_env(tt, 0, 1.75, a=0.2, r=0.6)
    return norm(lp(x, 1600) + bp(r.normal(0, 1, n), 2000, 6000) * note_env(tt, 0, 1.75, a=0.3, r=0.6) * 0.05)


SOUNDS = {"rival_glass_step": glass_step, "rival_blade_fan": blade_fan, "rival_parry": parry, "rival_noon": noon,
          "rival_horn": warhorn, "rival_shieldwall": shieldwall, "rival_cleave": cleave, "rival_rampart": rampart,
          "rival_bell": twin_bell, "rival_swap": swap, "rival_bond": bond, "rival_grief": grief,
          "rival_coins": coins, "rival_deal": deal, "rival_stamp": stamp, "rival_foreclose": foreclose,
          "rival_yield": yield_, "rival_arena": arena}

if __name__ == "__main__":
    sj = os.path.join(OUT, "..", "..", "sounds.json")
    d = json.load(open(sj))
    for name, fn in SOUNDS.items():
        files = []
        for i in range(1, 3):
            encode(f"{name}_{i}", fn(500 * i + len(name)))
            files.append(f"iron_spell_grimoire:sfx/{name}_{i}")
        d["sfx." + name] = {"sounds": [{"name": f} for f in files]}
    json.dump(d, open(sj, "w"), indent=2)
    print("rival sounds written:", len(SOUNDS))
