"""Self-made sounds (synthesised here, no samples): a familiar's cast, a board note being pinned/torn, and the
Cinder Sovereign's footsteps. Writes ogg files into the mod's sounds/sfx folder.  python3 tools/sfx_synth2.py"""
import numpy as np, subprocess, os, tempfile
from scipy.io import wavfile
from scipy.signal import butter, lfilter

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/iron_spell_grimoire/sounds/sfx")

def t(d): return np.arange(int(SR * d)) / SR
def env(n, a, r, curve=4.0):
    e = np.ones(n); na = max(1, int(a * SR)); e[:na] = np.linspace(0, 1, na)
    e[na:] = np.exp(-curve * np.linspace(0, 1, n - na) * (1 / max(r, 1e-3)) * (n - na) / SR)
    return e
def bp(x, lo, hi, o=2):
    b, a = butter(o, [lo / (SR / 2), hi / (SR / 2)], "band"); return lfilter(b, a, x)
def lp(x, f, o=2):
    b, a = butter(o, f / (SR / 2)); return lfilter(b, a, x)
def write(name, x):
    x = x / (np.max(np.abs(x)) + 1e-9) * 0.85
    fade = int(0.01 * SR); x[-fade:] *= np.linspace(1, 0, fade)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f: path = f.name
    wavfile.write(path, SR, (x * 32767).astype(np.int16))
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "4", os.path.join(OUT, name + ".ogg")], check=True)
    os.remove(path)

rng = np.random.default_rng(11)

# familiar cast: a rising, shimmering two-voice chirp with a soft breathy tail
for i, (f0, f1) in enumerate([(520, 1240), (640, 1480), (460, 1100)], 1):
    tt = t(0.55); n = len(tt)
    sweep = f0 * (f1 / f0) ** (np.clip(tt / 0.25, 0, 1))
    ph = 2 * np.pi * np.cumsum(sweep) / SR
    voice = np.sin(ph) + 0.4 * np.sin(2 * ph + 0.3) + 0.25 * np.sin(1.5 * ph)
    trem = 1 + 0.35 * np.sin(2 * np.pi * 22 * tt)
    breath = bp(rng.normal(0, 1, n), 2500, 7000) * 0.35
    write(f"familiar_{i}", (voice * trem + breath) * env(n, 0.02, 0.45))

# board note: a paper flap (filtered noise burst) plus a wooden pin tap
for i in range(1, 4):
    n = int(0.35 * SR)
    paper = bp(rng.normal(0, 1, n), 1200 + 300 * i, 6000) * env(n, 0.005, 0.12, 6)
    flutter = 1 + 0.6 * np.sin(2 * np.pi * (35 + 8 * i) * t(0.35))
    tap = np.zeros(n); k = int((0.09 + 0.02 * i) * SR); m = int(0.06 * SR)
    tt = t(0.06); tap[k:k + m] = (np.sin(2 * np.pi * 900 * tt) * 0.6 + np.sin(2 * np.pi * 1730 * tt) * 0.3) * np.exp(-tt * 70)
    write(f"board_note_{i}", paper * flutter + tap * 1.4)

# Sovereign footstep: a deep thud, a gravel crunch and a low ember hiss
for i in range(1, 5):
    tt = t(0.9); n = len(tt)
    f = 48 + 6 * i
    thud = np.sin(2 * np.pi * (f * np.exp(-tt * 3) + 22) * tt) * np.exp(-tt * 7)
    crunch = lp(rng.normal(0, 1, n), 900) * np.exp(-tt * 14) * 0.9
    hiss = bp(rng.normal(0, 1, n), 3000, 8000) * np.exp(-tt * 4) * 0.12
    write(f"sovereign_step_{i}", lp(thud * 1.6 + crunch, 2500) + hiss)
print("ok")
