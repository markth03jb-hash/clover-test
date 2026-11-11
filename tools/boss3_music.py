"""Boss music for Licht, Rades and Catherine (adapted from boss_music.py), synthesised from scratch (original, free to use): three seamless loops, one per phase.
 1 - a slow dread: a low drone, a heartbeat drum, a cold bell melody in D phrygian.
 2 - faster and tighter: driving toms, a pulsing bass, tense strings sliding a semitone.
 3 - all out: heavy drums, a dark choir pad, tritone stabs, a racing ostinato."""
import numpy as np, subprocess, os, sys
SR = 32000
OUT = sys.argv[1] if len(sys.argv) > 1 else '.'
rng = np.random.default_rng(7)

def t_(n): return np.arange(n) / SR
def env(n, a=0.005, d=0.3, s=0.0, r=0.05, sus_len=None):
    t = t_(n); e = np.ones(n)
    ai = int(a * SR); e[:ai] = np.linspace(0, 1, ai) if ai > 0 else 1
    e[ai:] = np.exp(-(t[ai:] - a) / max(d, 1e-4)) * (1 - s) + s
    ri = int(r * SR); e[-ri:] *= np.linspace(1, 0, ri)
    return e
def note(f): return 440.0 * 2 ** ((f - 69) / 12)
def sine(f, n, ph=0): return np.sin(2 * np.pi * f * t_(n) + ph)
def saw(f, n):
    t = t_(n); x = (f * t) % 1.0
    return 2 * x - 1
def lowpass(x, cut):
    a = np.exp(-2 * np.pi * cut / SR); y = np.zeros_like(x); acc = 0.0
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc; y[i] = acc
    return y
def lp_fast(x, cut):   # one-pole lowpass via cumulative trick (block-wise exact enough)
    a = np.exp(-2 * np.pi * cut / SR)
    from scipy.signal import lfilter
    return lfilter([1 - a], [1, -a], x)

def kick(n, f0=110, f1=40, d=0.35):
    t = t_(n); f = f1 + (f0 - f1) * np.exp(-t / 0.05)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.exp(-t / d)
def tom(n, f0=150, d=0.25):
    t = t_(n); f = f0 * (0.6 + 0.4 * np.exp(-t / 0.08))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / d) + rng.standard_normal(n) * np.exp(-t / 0.02) * 0.2
def snare(n):
    t = t_(n); return (rng.standard_normal(n) * 0.6 + np.sin(2 * np.pi * 190 * t) * 0.4) * np.exp(-t / 0.09)
def bell(f, n):
    t = t_(n)
    return (sine(f, n) + 0.5 * sine(f * 2.76, n) + 0.25 * sine(f * 5.4, n)) * np.exp(-t / 1.2) * 0.4
def pad(fs, n, bright=600):
    x = sum(saw(f * (1 + d), n) for f in fs for d in (-0.004, 0, 0.005))
    return lp_fast(x, bright) / (len(fs) * 3)

def place(buf, x, at):
    i = int(at * SR)
    if i >= len(buf): return
    j = min(len(buf), i + len(x)); buf[i:j] += x[:j - i]

def track(bpm, bars, parts):
    beat = 60.0 / bpm; L = int(bars * 4 * beat * SR)
    buf = np.zeros(L + SR * 3)   # tail wraps back to the start so the loop is seamless
    for p in parts: p(buf, beat, bars)
    tail = buf[L:]; buf = buf[:L]; buf[:len(tail)] += tail
    buf = np.tanh(buf * 1.2) * 0.85
    return buf

D = 52   # E3
PHRY = [0, 2, 3, 5, 7, 8, 10]   # E aeolian

def drone(level, root=D - 12):
    def f(buf, beat, bars):
        n = len(buf) - SR * 3
        x = (sine(note(root), n) + 0.6 * sine(note(root) * 1.5, n) + 0.3 * sine(note(root) * 2.003, n)) * level
        x *= 0.75 + 0.25 * np.sin(2 * np.pi * 0.07 * t_(n))
        buf[:n] += x
    return f

def heartbeat(level):
    def f(buf, beat, bars):
        for b in range(bars * 4):
            if b % 2 == 0:
                place(buf, kick(int(0.5 * SR)) * level, b * beat)
                place(buf, kick(int(0.5 * SR), 90, 35) * level * 0.6, b * beat + beat * 0.3)
    return f

def bells(level, seq, every):
    def f(buf, beat, bars):
        steps = int(bars * 4 / every)
        for i in range(steps):
            deg = seq[i % len(seq)]
            if deg is None: continue
            place(buf, bell(note(D + 12 + PHRY[deg % 7] + 12 * (deg // 7)), int(2.5 * SR)) * level, i * every * beat)
    return f

def toms(level, pattern):
    def f(buf, beat, bars):
        for b in range(bars):
            for k, hit in enumerate(pattern):
                if hit: place(buf, tom(int(0.6 * SR), 120 + 40 * (k % 3)) * level * hit, (b * 4 + k * 0.5) * beat)
    return f

def kicks(level, pattern, snares=None):
    def f(buf, beat, bars):
        for b in range(bars):
            for k, hit in enumerate(pattern):
                if hit: place(buf, kick(int(0.4 * SR), 120, 45, 0.25) * level * hit, (b * 4 + k * 0.25) * beat)
            if snares:
                for k, hit in enumerate(snares):
                    if hit: place(buf, snare(int(0.3 * SR)) * level * 0.5 * hit, (b * 4 + k * 0.25) * beat)
    return f

def bass(level, roots, sub=0.5):
    def f(buf, beat, bars):
        for b in range(bars * 4):
            for s in range(int(1 / sub)):
                r = roots[(b // 4) % len(roots)]
                n = int(sub * beat * SR)
                x = lp_fast(saw(note(D - 24 + r), n), 300) * env(n, 0.005, sub * beat * 0.7, 0.2, 0.02)
                place(buf, x * level, (b + s * sub) * beat)
    return f

def strings(level, chords, bars_per=2, bright=900):
    def f(buf, beat, bars):
        for i in range(bars // bars_per):
            ch = chords[i % len(chords)]
            n = int(bars_per * 4 * beat * SR)
            x = pad([note(D + c) for c in ch], n, bright) * env(n, 0.6, 99, 1.0, 0.4)
            place(buf, x * level, i * bars_per * 4 * beat)
    return f

def ostinato(level, seq, sub=0.25):
    def f(buf, beat, bars):
        steps = int(bars * 4 / sub)
        for i in range(steps):
            deg = seq[i % len(seq)]
            n = int(sub * beat * SR * 0.95)
            x = lp_fast(saw(note(D + PHRY[deg % 7] + 12 * (deg // 7)), n), 1800) * env(n, 0.003, sub * beat * 0.5, 0.0, 0.01)
            place(buf, x * level, i * sub * beat)
    return f

def stabs(level, at_beats, chord):
    def f(buf, beat, bars):
        for b in range(bars):
            for a in at_beats:
                n = int(0.5 * SR)
                x = pad([note(D + c) for c in chord], n, 2500) * env(n, 0.003, 0.25, 0.0, 0.05)
                place(buf, x * level * 2.5, (b * 4 + a) * beat)
    return f

def write(name, x):
    raw = os.path.join(OUT, name + '.raw')
    (np.clip(x, -1, 1) * 32767).astype('<i2').tofile(raw)
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 's16le', '-ar', str(SR), '-ac', '1', '-i', raw, '-c:a', 'libvorbis', '-q:a', '3',
                    os.path.join(OUT, name + '.ogg')], check=True)
    os.remove(raw)


def growl(level, roots):
    """A low, rough beast growl under the bar: detuned saws, heavily filtered, swelling."""
    def f(buf, beat, bars):
        for b in range(bars):
            r = roots[b % len(roots)]
            n = int(4 * beat * SR)
            x = (saw(note(D - 24 + r), n) + saw(note(D - 24 + r) * 1.01, n) + saw(note(D - 36 + r), n)) / 3
            x = lp_fast(x, 220) * env(n, 0.8 * beat, 99, 1.0, 0.3 * beat)
            place(buf, x * level, b * 4 * beat)
    return f

def war(level, pattern):
    """Big war drums: low toms in a stomping pattern (16 steps)."""
    def f(buf, beat, bars):
        for b in range(bars):
            for k, hit in enumerate(pattern):
                if hit: place(buf, (tom(int(0.7 * SR), 70 + 25 * (k % 2), 0.4) + kick(int(0.7 * SR), 90, 38, 0.3) * 0.6) * level * hit, (b * 4 + k * 0.25) * beat)
    return f


def chime(level, seq, every):
    def f(buf, beat, bars):
        steps = int(bars * 4 / every)
        for i in range(steps):
            deg = seq[i % len(seq)]
            if deg is None: continue
            n = int(2 * SR)
            fr = note(D + 12 + PHRY[deg % 7] + 12 * (deg // 7))
            place(buf, (sine(fr, n) + 0.3 * sine(fr * 2, n) + 0.15 * sine(fr * 4.2, n)) * np.exp(-t_(n) / 0.9) * 0.4 * level, i * every * beat)
    return f

# Licht: noble and sad, then driving, then dark (E aeolian, brighter chimes)
l1 = track(80, 16, [drone(0.22), chime(0.55, [0, 2, 4, None, 3, 2, 0, None, 4, 5, 7, None, 5, 4, 2, None], 1), strings(0.28, [[0, 3, 7], [-4, 0, 3], [-2, 2, 5], [0, 3, 7]], 4, 900)])
write('licht_1', l1)
l2 = track(122, 16, [drone(0.18), war(0.5, [1, 0, 0, 1, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 0]), bass(0.3, [0, -4, -2, 0]),
                     chime(0.35, [7, 5, 4, 2], 1), strings(0.3, [[0, 3, 7], [-4, 0, 3], [-2, 2, 5], [-5, -1, 2]], 2, 1400), ostinato(0.12, [0, 2, 3, 4, 3, 2, 0, -1])])
write('licht_2', l2)
l3 = track(140, 16, [drone(0.22, D - 24), kicks(0.55, [1, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 0], [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1]),
                     bass(0.3, [0, 1, 0, 6], 0.25), growl(0.22, [0, 6, 0, 1]), strings(0.34, [[0, 3, 6, 12], [1, 4, 8, 13], [0, 3, 6, 12], [-1, 3, 6, 11]], 2, 1500),
                     stabs(0.3, [0, 2.5], [0, 6, 12])])
write('licht_3', l3)
# Rades: shuffling dead, hollow knocks
r = track(100, 16, [drone(0.25), war(0.45, [1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0]), growl(0.35, [0, 0, 1, 0]),
                    strings(0.22, [[0, 1, 6], [0, 3, 6], [-1, 3, 6], [0, 1, 6]], 4, 700), ostinato(0.08, [0, 1, 0, 6, 0, 1, 0, 5])])
write('rades_1', r)
# Catherine: a crooked witch's waltz in 3
c = track(132, 16, [drone(0.18), kicks(0.4, [1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0]),
                    chime(0.4, [0, 3, 6, 5, 3, 1, 0, None, 7, 6, 3, 1, 0, None, 1, 3], 0.5), bass(0.25, [0, 1, 0, 6], 0.5),
                    strings(0.2, [[0, 3, 6], [1, 4, 8], [0, 3, 6], [6, 9, 12]], 2, 1000)])
write('catherine_1', c)
print('ok')
