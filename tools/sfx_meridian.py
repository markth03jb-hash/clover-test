"""Self-made sounds for the Meridian Grimoire (synthesised here, no samples):
the Hearth Star's warm hum, Haughty Flare's swell and blast, the Noonwave's roar, Dayreaver's heavy swing and its burning
bite, Zenith Unbound's rising choir and bell, Borrowed Noon's heartbeat climbing into it, the night kit's small sounds
(a lamp's chime, warm hands, a coal's crackle), dawn's chime and Pride's tick.
python3 tools/sfx_meridian.py"""
import json, os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(__file__))
from sfx_rivals import t, bp, lp, hp, tone, norm, at, bell, glass, brass, note_env, encode, OUT


def star(seed):   # a small sun forming: a warm swelling hum with a crackle in it
    r = np.random.default_rng(seed); tt = t(1.4); n = len(tt)
    f = 110 * (1 + 0.5 * np.clip(tt / 0.6, 0, 1))
    ph = 2 * np.pi * np.cumsum(f) / SR_
    hum = (np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.25 * np.sin(3.01 * ph)) * note_env(tt, 0, 1.35, a=0.3, r=0.5)
    crackle = hp(r.normal(0, 1, n), 2500) * (r.random(n) < 0.01) * 2 * note_env(tt, 0.1, 1.2, a=0.2, r=0.4)
    return norm(lp(hum, 1800) + crackle * 0.4)


def flare(seed):   # Haughty Flare: a fast swell, then a deep blast with a long hiss of heat
    r = np.random.default_rng(seed); tt = t(2.6); n = len(tt)
    sw = np.clip(tt / 0.9, 0, 1) ** 2 * (tt < 0.95)
    pad = sum(np.sin(2 * np.pi * f * tt) for f in (220, 277, 330, 440)) * sw * 0.3
    k, m = at(tt, 0.95)
    boom = (np.sin(2 * np.pi * np.cumsum(45 * (1 + 1.5 * np.exp(-k * 8))) / SR_) * np.exp(-k * 2.5) + lp(r.normal(0, 1, n), 600) * np.exp(-k * 3)) * m
    hiss = bp(r.normal(0, 1, n), 2500, 9000) * np.exp(-k * 1.6) * m * 0.35
    return norm(pad + boom + hiss)


def wave(seed):   # Noonwave: a roaring push of hot air
    r = np.random.default_rng(seed); tt = t(1.4); n = len(tt)
    env = np.clip(tt / 0.08, 0, 1) * np.exp(-tt * 2.8)
    roar = lp(r.normal(0, 1, n), 900) * env + bp(r.normal(0, 1, n), 900, 3500) * env * 0.5
    return norm(roar + tone(70, tt) * env * 0.6)


def reaver(seed):   # Dayreaver's swing: a heavy, slow whoosh with a bright ring at its edge
    r = np.random.default_rng(seed); tt = t(1.0); n = len(tt)
    w = bp(r.normal(0, 1, n), 200, 1200) * np.exp(-((tt - 0.28) / 0.16) ** 2)
    k, m = at(tt, 0.3)
    ring = bell(740, k, 6) * m * 0.25
    return norm(w + ring)


def bite(seed):   # Dayreaver's bite while the sun is up: a thud and a sizzle
    r = np.random.default_rng(seed); tt = t(0.9); n = len(tt)
    thud = tone(80, tt) * np.exp(-tt * 18) + lp(r.normal(0, 1, n), 700) * np.exp(-tt * 30)
    sizzle = bp(r.normal(0, 1, n), 3000, 9000) * np.exp(-tt * 4) * (0.6 + 0.4 * np.sin(2 * np.pi * 37 * tt)) * 0.5
    return norm(thud + sizzle)


def zenith(seed):   # Zenith Unbound: a choir rising to a blaze, a great bell at the top
    r = np.random.default_rng(seed); tt = t(3.4); n = len(tt)
    sw = np.clip(tt / 2.2, 0, 1) ** 1.5
    choir = sum(np.sin(2 * np.pi * f * tt + r.uniform(0, 6)) * (1 + 0.004 * np.sin(2 * np.pi * 5.5 * tt)) for f in (196, 247, 294, 392, 494, 587))
    k, m = at(tt, 2.25)
    toll = (bell(196, k, 0.9) + bell(392, k, 1.3) * 0.4) * m
    return norm(choir * sw * np.clip((3.3 - tt) / 0.8, 0, 1) * 0.3 + toll * 0.8 + bp(r.normal(0, 1, n), 2000, 7000) * sw * 0.04)


def borrow(seed):   # Borrowed Noon: a heartbeat that quickens into the blaze
    r = np.random.default_rng(seed); tt = t(3.0); n = len(tt)
    x = np.zeros(n); a0 = 0.0; gap = 0.7
    while a0 < 2.0:
        for d in (0.0, 0.16):
            k, m = at(tt, a0 + d)
            x += tone(55, k) * np.exp(-k * 20) * m * (1.0 if d == 0 else 0.7)
        a0 += gap; gap *= 0.78
    k, m = at(tt, 2.05)
    blaze = (lp(r.normal(0, 1, n), 1400) * np.exp(-k * 2) + sum(np.sin(2 * np.pi * f * k) for f in (220, 330, 440)) * np.exp(-k * 1.5) * 0.3) * m
    return norm(x + blaze * 0.8)


def lamp(seed):   # Lamplight: a soft small chime and a puff
    r = np.random.default_rng(seed); tt = t(0.9); n = len(tt)
    return norm(bell(1046, tt, 4) * 0.6 + bp(r.normal(0, 1, n), 1500, 5000) * np.exp(-tt * 25) * 0.3)


def warm(seed):   # Warm Hands: a low, kind swell
    tt = t(1.4)
    return norm(sum(np.sin(2 * np.pi * f * tt) for f in (262, 330, 392)) * note_env(tt, 0, 1.35, a=0.35, r=0.7))


def coal(seed):   # Last Coal: an ember's glow and crackle
    r = np.random.default_rng(seed); tt = t(1.6); n = len(tt)
    glow = lp(r.normal(0, 1, n), 300) * note_env(tt, 0, 1.5, a=0.3, r=0.6)
    crack = np.zeros(n)
    for _ in range(14):
        k, m = at(tt, r.uniform(0.05, 1.4))
        crack += hp(r.normal(0, 1, n), 2000) * np.exp(-k * 90) * m * r.uniform(0.3, 1)
    return norm(glow + crack * 0.6)


def dawn(seed):   # dawn: three rising notes, bright
    tt = t(1.8); x = np.zeros(len(tt))
    for i, f in enumerate((523, 659, 784)):
        k, m = at(tt, i * 0.18); x += bell(f, k, 2) * m * 0.5
    return norm(x)


def pride(seed):   # a Pride stack: a short bright tick
    tt = t(0.25)
    return norm(bell(1568, tt, 14))


SR_ = 44100
SOUNDS = {"meridian_star": star, "meridian_flare": flare, "meridian_wave": wave, "meridian_reaver": reaver, "meridian_bite": bite,
          "meridian_zenith": zenith, "meridian_borrow": borrow, "meridian_lamp": lamp, "meridian_warm": warm, "meridian_coal": coal,
          "meridian_dawn": dawn, "meridian_pride": pride}

if __name__ == "__main__":
    sj = os.path.join(OUT, "..", "..", "sounds.json")
    d = json.load(open(sj))
    for name, fn in SOUNDS.items():
        files = []
        for i in range(1, 3):
            encode(f"{name}_{i}", fn(700 * i + len(name)))
            files.append(f"iron_spell_grimoire:sfx/{name}_{i}")
        d["sfx." + name] = {"sounds": [{"name": f} for f in files]}
    json.dump(d, open(sj, "w"), indent=2)
    print("meridian sounds written:", len(SOUNDS))
