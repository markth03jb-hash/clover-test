"""Self-made sounds for the Vector, Quiet Fuse and Foreseen grimoires and their trees (synthesised here, no samples).
python3 tools/sfx_trio.py"""
import json, os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(__file__))
from sfx_rivals import t, bp, lp, hp, tone, norm, at, bell, glass, note_env, encode, OUT

SR = 44100


def sweep(f0, f1, tt, k=1.0):
    f = f0 + (f1 - f0) * np.clip(tt / max(1e-3, tt[-1] * k), 0, 1)
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def rnd(seed, n): return np.random.default_rng(seed).normal(0, 1, n)


# ---- the tree
def tree_learn(s):   # a node learned: two bright rising notes
    tt = t(0.9); k, m = at(tt, 0.1)
    return norm(bell(784, tt, 5) * 0.6 + bell(1175, k, 4) * m * 0.6)

# ---- Vector: clean, airy, a little metallic
def vec_reflect(s):   # a ping and a reversed whoosh
    tt = t(0.5); n = len(tt)
    return norm(glass(2400, tt, 18) * 0.6 + bp(rnd(s, n), 1500, 6000) * np.exp(-((tt - 0.12) / 0.06) ** 2) * 0.5)
def vec_slam(s):
    tt = t(0.9); n = len(tt)
    return norm(tone(60, tt) * np.exp(-tt * 9) + lp(rnd(s, n), 700) * np.exp(-tt * 14) + bp(rnd(s + 1, n), 2000, 8000) * np.exp(-tt * 20) * 0.3)
def vec_redirect(s):
    tt = t(0.7); n = len(tt)
    return norm(bp(rnd(s, n), 400, 3000) * np.exp(-((tt - 0.2) / 0.12) ** 2) + sweep(300, 900, tt, 0.4) * np.exp(-tt * 5) * 0.4)
def vec_step(s):
    tt = t(0.35); n = len(tt)
    return norm(bp(rnd(s, n), 800, 4000) * np.exp(-tt * 18) + tone(140, tt) * np.exp(-tt * 25) * 0.6)
def vec_return(s):
    tt = t(1.1)
    return norm(sweep(1600, 400, tt) * note_env(tt, 0, 1.0, a=0.05, r=0.6) * 0.5 + bell(880, tt, 3) * 0.4)
def vec_gust(s):
    tt = t(1.0); n = len(tt)
    env = np.clip(tt / 0.05, 0, 1) * np.exp(-tt * 3.5)
    return norm(bp(rnd(s, n), 300, 2500) * env + lp(rnd(s + 2, n), 300) * env * 0.6)
def vec_pulse(s):
    tt = t(0.6)
    return norm(tone(90, tt) * np.exp(-tt * 10) + sweep(200, 60, tt, 0.3) * np.exp(-tt * 6) * 0.5)
def vec_wind(s):
    tt = t(1.8); n = len(tt)
    wob = 0.6 + 0.4 * np.sin(2 * np.pi * 5 * tt)
    return norm(bp(rnd(s, n), 300, 1500) * note_env(tt, 0, 1.7, a=0.3, r=0.6) * wob)
def vec_charge(s):
    tt = t(2.0)
    return norm(sweep(200, 1400, tt, 0.95) * np.clip(tt / 1.9, 0, 1) ** 2 * 0.6 + hp(rnd(s, len(tt)), 3000) * np.clip(tt / 1.9, 0, 1) * 0.1)
def vec_plasma(s):
    tt = t(1.4); n = len(tt)
    return norm(tone(50, tt) * np.exp(-tt * 4) + lp(rnd(s, n), 900) * np.exp(-tt * 5) + bp(rnd(s + 3, n), 3000, 9000) * np.exp(-tt * 3) * 0.4)
def vec_absolute(s):
    tt = t(2.6); n = len(tt)
    k, m = at(tt, 0.9)
    return norm(sweep(80, 800, tt, 0.35) * (tt < 0.95) * np.clip(tt / 0.9, 0, 1) * 0.5 + (tone(55, k) * np.exp(-k * 2) + bp(rnd(s, n), 500, 4000) * np.exp(-k * 2.5) * 0.5) * m)
def vec_lance(s):
    tt = t(0.8); n = len(tt)
    return norm(bp(rnd(s, n), 1500, 7000) * np.exp(-tt * 7) + sweep(2000, 500, tt, 0.5) * np.exp(-tt * 6) * 0.4)

# ---- Quiet Fuse: soft clicks, a fizz, a muffled boom
def fuse_arm(s):   # a quiet click and a tiny fizz
    tt = t(0.4); n = len(tt)
    return norm(hp(rnd(s, n), 3000) * np.exp(-tt * 120) + bp(rnd(s + 1, n), 4000, 9000) * note_env(tt, 0.03, 0.35, a=0.02, r=0.2) * 0.25)
def fuse_blast(s):
    tt = t(1.3); n = len(tt)
    return norm(tone(48, tt) * np.exp(-tt * 3.5) + lp(rnd(s, n), 600) * np.exp(-tt * 4) + bp(rnd(s + 1, n), 1500, 6000) * np.exp(-tt * 9) * 0.4)
def fuse_click(s):   # a switch thrown
    tt = t(0.3); n = len(tt)
    k, m = at(tt, 0.06)
    return norm(hp(rnd(s, n), 2500) * np.exp(-tt * 200) + hp(rnd(s + 1, n), 2500) * np.exp(-k * 200) * m * 0.8)
def fuse_seeker(s):   # a little motor's whine
    tt = t(1.0)
    return norm((sweep(300, 500, tt) + 0.4 * np.sign(np.sin(2 * np.pi * 37 * tt)) * 0.3) * note_env(tt, 0, 0.95, a=0.05, r=0.3) * 0.6)
def fuse_bubble(s):
    tt = t(0.8); x = np.zeros(len(tt)); r = np.random.default_rng(s)
    for a0 in (0.0, 0.12, 0.27):
        k, m = at(tt, a0); x += np.sin(2 * np.pi * np.cumsum(r.uniform(500, 800) * (1 + k * 3)) / SR) * np.exp(-k * 18) * m
    return norm(x)
def fuse_coin(s):
    tt = t(0.8)
    return norm(glass(3200, tt, 6) * 0.6 + glass(4300, tt, 8) * 0.4)
def fuse_loop(s):   # a clock running backwards into a boom
    tt = t(2.4); n = len(tt); x = np.zeros(n)
    for i in range(8):
        k, m = at(tt, 0.15 * i); x += hp(rnd(s + i, n), 3000) * np.exp(-k * 150) * m * (0.4 + 0.08 * i)
    k, m = at(tt, 1.3)
    return norm(x + (tone(45, k) * np.exp(-k * 3) + lp(rnd(s, n), 500) * np.exp(-k * 3.5)) * m)

# ---- Foreseen: glassy, cool, a little ghostly
def fore_warn(s):
    tt = t(0.5)
    return norm(glass(1800, tt, 10) * 0.5 + glass(1800 * 1.5, tt, 12) * 0.3)
def fore_dodge(s):
    tt = t(0.5); n = len(tt)
    return norm(bp(rnd(s, n), 2000, 7000) * np.exp(-((tt - 0.08) / 0.05) ** 2) + bell(1320, tt, 9) * 0.3)
def fore_step(s):
    tt = t(0.5); n = len(tt)
    return norm(bp(rnd(s, n), 1000, 6000) * np.exp(-tt * 12) + sweep(800, 1600, tt, 0.3) * np.exp(-tt * 10) * 0.3)
def fore_read(s):
    tt = t(1.3)
    return norm(sum(bell(f, tt, 2.2) for f in (660, 990, 1320)) * note_env(tt, 0, 1.25, a=0.1, r=0.8))
def fore_punish(s):
    tt = t(0.6); n = len(tt)
    return norm(bp(rnd(s, n), 1500, 7000) * np.exp(-((tt - 0.07) / 0.04) ** 2) + glass(2600, tt, 10) * 0.4)
def fore_double(s):
    tt = t(1.4)
    return norm(sum(np.sin(2 * np.pi * f * tt) for f in (440, 554, 660)) * note_env(tt, 0, 1.3, a=0.4, r=0.6) * 0.4)
def fore_rewind(s):   # a sound played backwards
    tt = t(1.0)
    rev = bell(880, tt, 3)[::-1] * np.clip(tt / 0.8, 0, 1)
    return norm(rev)
def fore_strike(s):
    tt = t(0.9); n = len(tt)
    return norm(bp(rnd(s, n), 2500, 9000) * np.exp(-tt * 20) + tone(80, tt) * np.exp(-tt * 12) + glass(1500, tt, 6) * 0.3)
def fore_rewrite(s):
    tt = t(2.2)
    rev = sum(bell(f, tt, 1.5) for f in (440, 660, 880))[::-1] * np.clip(tt / 1.2, 0, 1) * (tt < 1.3)
    k, m = at(tt, 1.3)
    return norm(rev * 0.6 + bell(220, k, 1.4) * m * 0.7)


SOUNDS = {n: f for n, f in globals().items() if callable(f) and (n.startswith(('vec_', 'fuse_', 'fore_')) or n == 'tree_learn') and n not in ('sweep',)}

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
    print("trio sounds written:", len(SOUNDS))
