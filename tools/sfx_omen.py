"""Self-made sounds for the Grimoire of Ill Omen (synthesised here, no samples): a footprint's hollow tick, the
calamities (a falling weight's whistle and thud, a stumble's scuff, a blow rebounding, a stray shard's whizz, needle rain),
a token's dark chime, the Afterimage's breath, a domino run of clacks, Stillness's hush, the Ledger's page-slam and toll,
and The Law Itself's rising drone and bells.
python3 tools/sfx_omen.py"""
import json, os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(__file__))
from sfx_rivals import t, bp, lp, hp, tone, norm, at, bell, glass, note_env, encode, OUT

SR = 44100


def step(seed):   # a footprint taking: a hollow, slightly wrong tick
    r = np.random.default_rng(seed); tt = t(0.5); n = len(tt)
    return norm(bell(196 * 1.06, tt, 9) * 0.6 + bp(r.normal(0, 1, n), 300, 1400) * np.exp(-tt * 40))


def fall(seed):   # a weight falling: a falling whistle, then a heavy thud
    r = np.random.default_rng(seed); tt = t(1.3); n = len(tt)
    f = 1800 * np.exp(-tt * 2.6)
    whistle = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.clip((0.55 - tt) / 0.1, 0, 1) * np.clip(tt / 0.1, 0, 1) * 0.35
    k, m = at(tt, 0.55)
    thud = (tone(55, k) * np.exp(-k * 9) + lp(r.normal(0, 1, n), 500) * np.exp(-k * 14)) * m
    return norm(whistle + thud)


def trip(seed):   # a stumble: a scuff and a body hitting the ground
    r = np.random.default_rng(seed); tt = t(0.9); n = len(tt)
    scuff = bp(r.normal(0, 1, n), 800, 4000) * np.exp(-((tt - 0.08) / 0.06) ** 2)
    k, m = at(tt, 0.3)
    return norm(scuff * 0.7 + (tone(75, k) * np.exp(-k * 16) + lp(r.normal(0, 1, n), 400) * np.exp(-k * 22)) * m)


def rebound(seed):   # a blow turned back: a reversed ring snapping shut
    tt = t(0.8)
    rev = bell(660, tt[::-1], 4) * np.clip(tt / 0.5, 0, 1) * (tt < 0.55)
    k, m = at(tt, 0.55)
    return norm(rev * 0.7 + bell(330, k, 12) * m)


def shard(seed):   # a stray shard: a quick whizz and a small strike
    r = np.random.default_rng(seed); tt = t(0.6); n = len(tt)
    whizz = bp(r.normal(0, 1, n), 2500, 8000) * np.exp(-((tt - 0.12) / 0.05) ** 2)
    k, m = at(tt, 0.18)
    return norm(whizz + glass(2200, k, 14) * m * 0.5)


def needles(seed):   # needle rain: a patter of thin, bright strikes
    r = np.random.default_rng(seed); tt = t(1.6); n = len(tt)
    x = np.zeros(n)
    for _ in range(40):
        k, m = at(tt, r.uniform(0, 1.3))
        x += glass(r.uniform(3000, 5200), k, 30) * m * r.uniform(0.2, 0.5)
    return norm(x + bp(r.normal(0, 1, n), 4000, 9000) * note_env(tt, 0, 1.5, a=0.2, r=0.4) * 0.15)


def token(seed):   # a laden token: a dark, slightly detuned chime
    tt = t(1.2)
    return norm(bell(523, tt, 2.5) + bell(523 * 1.03, tt, 2.8) * 0.6)


def afterimage(seed):   # the Afterimage: a breath drawn in, low and close
    r = np.random.default_rng(seed); tt = t(1.4); n = len(tt)
    breath = bp(r.normal(0, 1, n), 250, 1200) * note_env(tt, 0, 1.3, a=0.6, r=0.5)
    return norm(breath + tone(98, tt) * note_env(tt, 0.2, 1.1, a=0.4, r=0.5) * 0.3)


def domino(seed):   # a domino run: quick clacks, speeding up
    r = np.random.default_rng(seed); tt = t(1.4); n = len(tt)
    x = np.zeros(n); a0 = 0.0; gap = 0.14
    while a0 < 1.2:
        k, m = at(tt, a0); x += (bell(r.uniform(1400, 1800), k, 40) + hp(r.normal(0, 1, n), 2000) * np.exp(-k * 120)) * m
        a0 += gap; gap = max(0.045, gap * 0.88)
    return norm(x)


def still(seed):   # Stillness: a deep hush, the air going quiet
    r = np.random.default_rng(seed); tt = t(1.8); n = len(tt)
    return norm(lp(r.normal(0, 1, n), 200) * note_env(tt, 0, 1.7, a=0.2, r=1.2) + tone(55, tt) * note_env(tt, 0, 1.7, a=0.3, r=1.0) * 0.4)


def ledger(seed):   # Karma's Ledger: a book slammed shut, a low bell under it
    r = np.random.default_rng(seed); tt = t(2.0); n = len(tt)
    slam = tone(70, tt) * np.exp(-tt * 12) + lp(r.normal(0, 1, n), 1100) * np.exp(-tt * 25)
    k, m = at(tt, 0.05)
    return norm(slam + bell(110, k, 1.1) * m * 0.6)


def law(seed):   # The Law Itself: a low drone that rises, three tolling bells
    r = np.random.default_rng(seed); tt = t(3.2); n = len(tt)
    f = 55 * (1 + 0.5 * np.clip(tt / 2.5, 0, 1))
    ph = 2 * np.pi * np.cumsum(f) / SR
    drone = (np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.3 * np.sin(3.02 * ph)) * note_env(tt, 0, 3.1, a=0.6, r=0.6)
    bells = sum(bell(147 * q, np.clip(tt - a0, 0, None), 1.3) * (tt >= a0) for q, a0 in ((1.0, 0.4), (1.19, 1.2), (0.84, 2.0)))
    return norm(lp(drone, 1200) * 0.6 + bells * 0.6)


SOUNDS = {"omen_step": step, "omen_fall": fall, "omen_trip": trip, "omen_rebound": rebound, "omen_shard": shard, "omen_needles": needles,
          "omen_token": token, "omen_afterimage": afterimage, "omen_domino": domino, "omen_still": still, "omen_ledger": ledger, "omen_law": law}

if __name__ == "__main__":
    sj = os.path.join(OUT, "..", "..", "sounds.json")
    d = json.load(open(sj))
    for name, fn in SOUNDS.items():
        files = []
        for i in range(1, 3):
            encode(f"{name}_{i}", fn(900 * i + len(name)))
            files.append(f"iron_spell_grimoire:sfx/{name}_{i}")
        d["sfx." + name] = {"sounds": [{"name": f} for f in files]}
    json.dump(d, open(sj, "w"), indent=2)
    print("omen sounds written:", len(SOUNDS))
