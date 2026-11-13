"""Self-made sounds for the Hunter, Void, Gambler, Tarot, Abyss, Mech and Weather Grimoires (synthesised here, no
samples). Writes the ogg files and the sounds.json entries.  python3 tools/sfx_eight_grimoires.py"""
import numpy as np, json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from sfx_synth2 import SR, t, env, bp, lp, write, OUT


def noise(r, n): return r.normal(0, 1, n)


def tone(f, tt, ph=0.0): return np.sin(2 * np.pi * f * tt + ph)


def clicks(r, n, count, lo, hi, decay, spread=(0.0, 1.0)):
    x = np.zeros(n)
    for _ in range(count):
        at = int(r.uniform(*spread) * (n - 4000)); m = 3000; tt = np.arange(m) / SR
        f = r.uniform(lo, hi)
        x[at:at + m] += (tone(f, tt) + 0.5 * tone(f * 2.4, tt)) * np.exp(-tt * decay) * r.uniform(0.3, 1)
    return x


# ------------------------------------------------------------------ Hunter
def saw(seed, dur=0.55):   # a toothed blade ripping through the air: a rasping buzz under a whoosh
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    rasp = np.sign(np.sin(2 * np.pi * r.uniform(70, 95) * tt)) * bp(noise(r, n), 800, 3000) * 0.6
    whoosh = bp(noise(r, n), 400, 2500) * np.sin(np.pi * np.clip(tt / dur, 0, 1)) ** 2
    return (rasp + whoosh) * env(n, 0.02, 0.45, 3)


def trick(seed, dur=0.5):   # a heavy mechanism snapping open: two clacks and a sliding scrape
    r = np.random.default_rng(seed); n = int(SR * dur)
    x = clicks(r, n, 2, 900, 1600, 60, (0.0, 0.35)) * 1.4
    scrape = bp(noise(r, n), 2000, 5000) * env(n, 0.05, 0.25, 4) * 0.4
    thud = lp(noise(r, n), 220) * env(n, 0.002, 0.15, 6) * 2
    return x + scrape + thud


def gunshot(seed, dur=0.9):   # a black-powder crack and a long rolling tail
    r = np.random.default_rng(seed); n = int(SR * dur)
    crack = bp(noise(r, n), 1200, 9000) * env(n, 0.0005, 0.05, 8) * 2
    boom = lp(noise(r, n), 400) * env(n, 0.001, 0.5, 4) * 1.6
    tail = bp(noise(r, n), 200, 1500) * env(n, 0.05, 0.8, 3) * 0.3
    return crack + boom + tail


def quickstep(seed, dur=0.45):   # a soft breathy rush of mist
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    return bp(noise(r, n), 600 + 2000 * r.random(), 6000) * np.sin(np.pi * tt / dur) ** 3 * 0.8


# ------------------------------------------------------------------ Void
def erase(seed, dur=0.7):   # sound running backwards into nothing: a reversed swell cut dead
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    swell = bp(noise(r, n), 300, 6000) * (tt / dur) ** 3
    sub = tone(55, tt) * (tt / dur) ** 2 * 0.6
    x = swell + sub
    x[int(n * 0.92):] = 0
    return x


# ------------------------------------------------------------------ Gambler
def coin(seed, dur=0.7):   # a flicked coin: a bright ping that wobbles as it spins
    r = np.random.default_rng(seed); tt = t(dur); f = r.uniform(3200, 4200)
    wob = 1 + 0.5 * np.sin(2 * np.pi * (14 + 30 * tt) * tt)
    return (tone(f, tt) + 0.6 * tone(f * 1.52, tt) + 0.3 * tone(f * 2.4, tt)) * np.exp(-tt * 4) * wob * 0.4


def roulette(seed, dur=1.6):   # the ball rattling round, slowing, dropping into a slot
    r = np.random.default_rng(seed); n = int(SR * dur); x = np.zeros(n)
    tt0 = 0.0; gap = 0.04
    while tt0 < dur - 0.2:
        at = int(tt0 * SR); m = 1500; tt = np.arange(m) / SR
        x[at:at + m] += tone(r.uniform(2500, 3500), tt) * np.exp(-tt * 120) * 0.5
        tt0 += gap; gap *= 1.07
    at = int((dur - 0.2) * SR); m = n - at; tt = np.arange(m) / SR
    x[at:] += tone(1800, tt) * np.exp(-tt * 30) * 0.8
    return x + bp(noise(r, n), 300, 1200) * env(n, 0.05, 1.2, 2) * 0.15


def slot(seed, dur=1.0):   # reels whirring and three clunks as they stop
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    whir = bp(noise(r, n), 500, 2000) * (1 + 0.6 * np.sin(2 * np.pi * 30 * tt)) * np.clip(1 - tt / dur, 0, 1) * 0.4
    x = whir
    for k in range(3):
        at = int((0.3 + 0.22 * k) * SR); m = 4000; ts = np.arange(m) / SR
        x[at:at + m] += (lp(noise(r, m), 600) * 2 + tone(700 + 120 * k, ts)) * np.exp(-ts * 35) * 0.8
    return x


def dice(seed, dur=0.8):   # dice tumbling on a table
    r = np.random.default_rng(seed); n = int(SR * dur)
    return clicks(r, n, 14, 1500, 3000, 90, (0.0, 0.8)) + lp(noise(r, n), 300) * env(n, 0.001, 0.3, 6) * 0.5


def jackpot(seed, dur=1.6):   # a bright rising arpeggio and a shower of coins
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    x = np.zeros(n)
    for k, f in enumerate((523, 659, 784, 1047, 1319)):
        at = int(k * 0.09 * SR); m = n - at; ts = np.arange(m) / SR
        x[at:] += (tone(f, ts) + 0.3 * tone(f * 2, ts)) * np.exp(-ts * 3) * 0.3
    return x + clicks(r, n, 30, 3000, 4500, 50, (0.3, 1.0)) * 0.4


# ------------------------------------------------------------------ Tarot
def card(seed, dur=0.6):   # a card snapped over, and a soft bell for its meaning
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    flick = bp(noise(r, n), 2500, 8000) * env(n, 0.001, 0.06, 6) * 1.2
    f = r.choice([880, 988, 1175])
    bell = (tone(f, tt) + 0.4 * tone(f * 2.76, tt)) * np.exp(-tt * 4) * 0.35 * (tt > 0.06)
    return flick + bell


# ------------------------------------------------------------------ Abyss
def current(seed, dur=1.1):   # a heavy surge of water rolling past
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    return lp(noise(r, n), 500 + 300 * np.sin(tt * 3).mean()) * np.sin(np.pi * tt / dur) ** 1.5 * 1.4 + bp(noise(r, n), 800, 3000) * np.sin(np.pi * tt / dur) ** 4 * 0.3


def deep_boom(seed, dur=1.4):   # a muffled underwater detonation
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    return (tone(38 + 8 * r.random(), tt) * 1.2 + lp(noise(r, n), 160) * 2) * env(n, 0.01, 1.1, 3)


def jaws(seed, dur=0.5):   # something enormous snapping shut
    r = np.random.default_rng(seed); n = int(SR * dur)
    snap = lp(noise(r, n), 900) * env(n, 0.001, 0.08, 7) * 2.5
    crunch = bp(noise(r, n), 600, 2500) * env(n, 0.005, 0.2, 5) * 0.6
    return snap + crunch + clicks(r, n, 4, 300, 700, 50, (0.0, 0.15))


def leviathan(seed, dur=2.2):   # a vast low groan with a crashing wake
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    f = 48 + 20 * np.sin(np.pi * tt / dur)
    groan = np.sin(2 * np.pi * np.cumsum(f) / SR) * (1 + 0.3 * np.sin(2 * np.pi * 5 * tt)) * np.sin(np.pi * tt / dur) * 0.9
    wake = lp(noise(r, n), 900) * env(n, 0.3, 1.6, 2) * 0.8
    return groan + wake


def lure_hum(seed, dur=1.2):   # a cold, glassy hum, wavering
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt); f = r.uniform(300, 380)
    return (tone(f, tt) + 0.5 * tone(f * 1.5, tt) + 0.25 * tone(f * 3.01, tt)) * (0.7 + 0.3 * np.sin(2 * np.pi * 4 * tt)) * env(n, 0.2, 1.0, 2) * 0.4


def splash_deep(seed, dur=0.9):   # plunging into black water
    r = np.random.default_rng(seed); n = int(SR * dur)
    x = lp(noise(r, n), 1500) * env(n, 0.003, 0.3, 5) * 1.4
    gl = np.zeros(n)
    for _ in range(10):
        at = r.integers(int(0.1 * SR), n - 3000); m = 2500; ts = np.arange(m) / SR; f = r.uniform(300, 900)
        gl[at:at + m] += np.sin(2 * np.pi * np.linspace(f, f * 1.8, m) * ts) * np.exp(-ts * 30) * 0.3
    return x + gl


def tentacle(seed, dur=0.8):   # wet, slithering squeeze
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    am = (0.5 + 0.5 * np.sin(2 * np.pi * (6 + 4 * tt) * tt)) ** 2
    return lp(noise(r, n), 700) * am * env(n, 0.05, 0.7, 2) * 1.2 + bp(noise(r, n), 1500, 4000) * am * 0.15


# ------------------------------------------------------------------ Mech
def clank(seed, dur=0.6):   # a heavy metal plate striking
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    fs = r.uniform(300, 500) * np.array([1, 2.3, 3.9, 5.4])
    x = sum(tone(f, tt) * np.exp(-tt * (6 + 3 * k)) / (k + 1) for k, f in enumerate(fs))
    return x * 0.6 + lp(noise(r, n), 600) * env(n, 0.001, 0.05, 6)


def missile(seed, dur=1.0):   # a launch thump and a hissing rocket
    r = np.random.default_rng(seed); n = int(SR * dur)
    thump = lp(noise(r, n), 250) * env(n, 0.002, 0.1, 6) * 2
    hiss = bp(noise(r, n), 1500, 7000) * env(n, 0.02, 0.9, 2) * 0.6
    return thump + hiss


def railgun(seed, dur=1.2):   # a rising electric whine and a hard crack
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    f = 300 + 2600 * np.clip(tt / 0.25, 0, 1)
    whine = np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR)) * 0.15 * (tt < 0.25)
    crack = bp(noise(r, n), 1000, 10000) * env(n, 0.0005, 0.1, 6) * (tt >= 0.25) * 2
    boom = lp(noise(r, n), 300) * np.exp(-np.clip(tt - 0.25, 0, None) * 4) * (tt >= 0.25) * 1.2
    return whine + crack + boom


def rivet(seed, dur=0.25):   # a pneumatic rivet punch
    r = np.random.default_rng(seed); n = int(SR * dur)
    return bp(noise(r, n), 1500, 6000) * env(n, 0.0005, 0.03, 8) * 1.5 + clicks(r, n, 1, 2500, 3500, 70, (0.0, 0.05))


def rocket(seed, dur=1.0):   # a roaring thrust
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    roar = lp(noise(r, n), 900) * (1 + 0.3 * np.sin(2 * np.pi * 40 * tt)) * env(n, 0.03, 0.8, 2) * 1.2
    return roar + bp(noise(r, n), 3000, 8000) * env(n, 0.01, 0.3, 3) * 0.3


def servo(seed, dur=0.7):   # a motor whirring up and settling
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    f = 120 + 260 * np.sin(np.pi * np.clip(tt / dur, 0, 1))
    whir = np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR)) * 0.2 + np.sin(2 * np.pi * np.cumsum(f * 3) / SR) * 0.2
    return lp(whir, 2500) * env(n, 0.03, 0.6, 2) + clicks(r, n, 1, 1500, 2000, 60, (0.85, 0.9))


def stomp(seed, dur=0.9):   # a piston slam into the ground
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    hiss = bp(noise(r, n), 2000, 7000) * env(n, 0.002, 0.15, 5) * 0.4
    slam = (lp(noise(r, n), 200) * 2.5 + tone(45, tt)) * env(n, 0.002, 0.6, 4)
    return hiss + slam + clank(seed + 1, dur) * 0.3


# ------------------------------------------------------------------ Weather
def hail(seed, dur=1.2):   # hailstones pattering on the ground
    r = np.random.default_rng(seed); n = int(SR * dur)
    return clicks(r, n, 60, 1800, 5000, 160, (0.0, 1.0)) * 0.5 + bp(noise(r, n), 2000, 6000) * 0.05


def rain(seed, dur=1.6):   # a soft downpour
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    hush = bp(noise(r, n), 1200, 8000) * np.minimum(1, tt / 0.3) * np.minimum(1, (dur - tt) / 0.3) * 0.5
    return hush + clicks(r, n, 40, 2500, 6000, 220, (0.0, 1.0)) * 0.2


def sun_chime(seed, dur=1.2):   # a warm, bright chord
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    x = sum((tone(f, tt, r.uniform(0, 6)) * np.exp(-tt * (1.5 + k * 0.5))) for k, f in enumerate((523, 659, 784, 1047)))
    return x * 0.3 * env(n, 0.02, 1.1, 2)


def wind(seed, dur=1.4):   # a gust rising and falling
    r = np.random.default_rng(seed); tt = t(dur); n = len(tt)
    g = np.sin(np.pi * tt / dur) ** 2
    lo = 300 + 400 * r.random()
    return bp(noise(r, n), lo, lo * 5) * g * (1 + 0.4 * np.sin(2 * np.pi * 1.5 * tt)) * 0.9


made = {}
for name, fn in (("saw", saw), ("trick", trick), ("gunshot", gunshot), ("quickstep", quickstep), ("erase", erase),
                 ("coin", coin), ("roulette", roulette), ("slot", slot), ("dice", dice), ("jackpot", jackpot), ("card", card),
                 ("current", current), ("deep_boom", deep_boom), ("jaws", jaws), ("leviathan", leviathan), ("lure_hum", lure_hum),
                 ("splash_deep", splash_deep), ("tentacle", tentacle),
                 ("clank", clank), ("missile", missile), ("railgun", railgun), ("rivet", rivet), ("rocket", rocket), ("servo", servo), ("stomp", stomp),
                 ("hail", hail), ("rain", rain), ("sun_chime", sun_chime), ("wind", wind)):
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
