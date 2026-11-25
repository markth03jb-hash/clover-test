"""Self-made sounds for the Grimoire of the Bound Demon (synthesised here, no samples): the demon's whisper, the
takeover's lurch, the katana drawn and its cuts, a parry's ring, the bond's chime, a sinner marked, salt crystallising,
the seraph's waking choir and the trumpet's call.
python3 tools/sfx_bound.py"""
import json, os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(__file__))
from sfx_rivals import t, bp, lp, hp, tone, norm, at, bell, glass, note_env, encode, OUT
SR = 44100


def rnd(s, n): return np.random.default_rng(s).normal(0, 1, n)


def bd_whisper(s):   # breathy noise shaped like a voice, low, close
    tt = t(1.4); n = len(tt)
    env = note_env(tt, 0, 1.3, a=0.3, r=0.6) * (0.6 + 0.4 * np.sin(2 * np.pi * 3.3 * tt))
    return norm(bp(rnd(s, n), 300, 2200) * env + tone(70, tt) * env * 0.25)
def bd_takeover(s):   # a heartbeat slammed, a growl under it
    tt = t(1.6); n = len(tt); x = np.zeros(n)
    for a0 in (0.0, 0.18):
        k, m = at(tt, a0); x += tone(50, k) * np.exp(-k * 14) * m
    growl = lp(rnd(s, n), 260) * (0.6 + 0.4 * np.sign(np.sin(2 * np.pi * 31 * tt))) * note_env(tt, 0.2, 1.5, a=0.2, r=0.6)
    return norm(x + growl * 0.8)
def bd_draw(s):   # steel sliding from a scabbard
    tt = t(0.8); n = len(tt)
    slide = bp(rnd(s, n), 3000, 9000) * note_env(tt, 0, 0.45, a=0.05, r=0.15)
    k, m = at(tt, 0.45)
    return norm(slide * 0.6 + glass(2800, k, 7) * m * 0.5)
def bd_cut(s):   # a fast blade through air
    tt = t(0.45); n = len(tt)
    return norm(bp(rnd(s, n), 1500, 7000) * np.exp(-((tt - 0.1) / 0.05) ** 2) + bp(rnd(s + 1, n), 400, 1500) * np.exp(-tt * 20) * 0.3)
def bd_parry(s):   # two blades meeting
    tt = t(0.9)
    return norm(glass(2100, tt, 5) * 0.6 + glass(3150, tt, 7) * 0.4 + hp(rnd(s, len(tt)), 4000) * np.exp(-tt * 80) * 0.5)
def bd_bond(s):   # a warm two-note chime
    tt = t(1.2); k, m = at(tt, 0.15)
    return norm(bell(523, tt, 2.5) * 0.5 + bell(659, k, 2.5) * m * 0.5)
def bd_mark(s):   # a low toll and a hiss
    tt = t(1.3); n = len(tt)
    return norm(bell(98, tt, 1.6) * 0.8 + bp(rnd(s, n), 2000, 6000) * np.exp(-tt * 6) * 0.2)
def bd_salt(s):   # crystals forming: many tiny bright ticks
    tt = t(0.9); n = len(tt); x = np.zeros(n); r = np.random.default_rng(s)
    for _ in range(30):
        k, m = at(tt, r.uniform(0, 0.6)); x += glass(r.uniform(3500, 7000), k, 40) * m * r.uniform(0.2, 0.5)
    return norm(x)
def bd_seraph(s):   # a high, pale choir swelling, a single bell inside it
    tt = t(3.0); r = np.random.default_rng(s)
    sw = np.clip(tt / 1.4, 0, 1) ** 1.5 * np.clip((2.9 - tt) / 0.9, 0, 1)
    choir = sum(np.sin(2 * np.pi * f * tt + r.uniform(0, 6)) * (1 + 0.005 * np.sin(2 * np.pi * 5 * tt)) for f in (440, 554, 659, 880, 1108))
    k, m = at(tt, 1.2)
    return norm(choir * sw * 0.3 + bell(880, k, 1.2) * m * 0.5)
def bd_trumpet(s):   # a brass call, rising
    tt = t(2.0)
    f = 233 * (1 + 0.5 * np.clip((tt - 0.6) / 0.2, 0, 1))
    ph = 2 * np.pi * np.cumsum(f) / SR
    brass = sum(np.sin(k * ph) / k for k in range(1, 8)) * note_env(tt, 0.05, 1.9, a=0.08, r=0.5)
    return norm(lp(brass, 3500))


SOUNDS = {n: f for n, f in globals().items() if callable(f) and n.startswith('bd_')}
if __name__ == "__main__":
    sj = os.path.join(OUT, "..", "..", "sounds.json")
    d = json.load(open(sj))
    for name, fn in SOUNDS.items():
        files = []
        for i in range(1, 3):
            encode(f"{name}_{i}", fn(300 * i + len(name)))
            files.append(f"iron_spell_grimoire:sfx/{name}_{i}")
        d["sfx." + name] = {"sounds": [{"name": f} for f in files]}
    json.dump(d, open(sj, "w"), indent=2)
    print("bound sounds written:", len(SOUNDS))
