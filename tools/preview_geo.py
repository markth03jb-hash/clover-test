"""
Preview a GeckoLib model's animations without the game: reads geo/<name>.geo.json, animations/<name>.animation.json
and textures/geo/<name>.png and draws each animation as frames (flat-shaded cubes, each in its texture tile's average
colour), from a three-quarter front view and from the side, into an animated GIF - in a few seconds.

It follows GeckoLib 4's conventions so what you see is what the game does:
  - a bone's static rotation and its animated rotation: x and y are negated, z is kept (Bedrock -> GeckoLib)
  - animated position: x is negated
  - each bone turns about its pivot in the order Z, then Y, then X, after moving by its animated position, inside
    its parent's transform; scale is about the pivot too
  - keyframes are interpolated linearly; before the first / after the last the end value holds

Usage (from the project root):
  python3 tools/preview_geo.py mech_titan assemble idle walk punch -o /tmp/mech.gif
  python3 tools/preview_geo.py mech_fist clench idle -o /tmp/fist.gif
Options: --fps 20, --size 360 (each view), --hide bone1,bone2 (as the game hides parts).
"""
import argparse
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "iron_spell_grimoire")


def rot_xyz(rx, ry, rz):
    """GeckoLib order: rotate Z, then Y, then X (each in radians), as a 3x3 matrix applied to column vectors."""
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx   # poseStack.mulPose(Z); mulPose(Y); mulPose(X) => M = Rz*Ry*Rx


def sample(track, t, default):
    if not track:
        return default
    keys = sorted((float(k), v) for k, v in track.items())
    if t <= keys[0][0]:
        return keys[0][1]
    if t >= keys[-1][0]:
        return keys[-1][1]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            k = 0 if t1 == t0 else (t - t0) / (t1 - t0)
            return [a + (b - a) * k for a, b in zip(v0, v1)]
    return default


def mat4(R=None, T=None, S=None):
    M = np.eye(4)
    if R is not None: M[:3, :3] = R
    if S is not None: M[:3, :3] = M[:3, :3] @ np.diag(S)
    if T is not None: M[:3, 3] = T
    return M


def tr(v):
    return mat4(T=np.array(v, dtype=float))


def bone_matrices(bones, anim, t):
    """World matrix of each bone at time t (model space, pixels)."""
    by = {b["name"]: b for b in bones}
    out = {}

    def get(name):
        if name in out:
            return out[name]
        b = by[name]
        parent = get(b["parent"]) if b.get("parent") else np.eye(4)
        piv = np.array(b.get("pivot", [0, 0, 0]), dtype=float)
        base = b.get("rotation", [0, 0, 0])
        tracks = (anim or {}).get("bones", {}).get(name, {})
        ar = sample(tracks.get("rotation"), t, [0, 0, 0])
        ap = sample(tracks.get("position"), t, [0, 0, 0])
        sc = sample(tracks.get("scale"), t, [1, 1, 1])
        if isinstance(sc, (int, float)): sc = [sc] * 3
        rx = math.radians(-(base[0] + ar[0]))
        ry = math.radians(-(base[1] + ar[1]))
        rz = math.radians(base[2] + ar[2])
        local = tr([-ap[0], ap[1], ap[2]]) @ tr(piv) @ mat4(R=rot_xyz(rx, ry, rz)) @ mat4(S=np.array(sc, dtype=float)) @ tr(-piv)
        out[name] = parent @ local
        return out[name]

    for b in bones:
        get(b["name"])
    return out


def cube_faces(c):
    x, y, z = c["origin"]
    w, h, d = c["size"]
    inf = c.get("inflate", 0)
    x, y, z, w, h, d = x - inf, y - inf, z - inf, w + 2 * inf, h + 2 * inf, d + 2 * inf
    v = np.array([[x + w * (i & 1), y + h * ((i >> 1) & 1), z + d * ((i >> 2) & 1)] for i in range(8)], dtype=float)
    if "rotation" in c:   # a cube's own rotation about its pivot
        piv = np.array(c["pivot"], dtype=float)
        r = c["rotation"]
        R = rot_xyz(math.radians(-r[0]), math.radians(-r[1]), math.radians(r[2]))
        v = (v - piv) @ R.T + piv
    faces = {"west": [0, 4, 6, 2], "east": [1, 3, 7, 5], "down": [0, 1, 5, 4], "up": [2, 6, 7, 3], "north": [0, 2, 3, 1], "south": [4, 5, 7, 6]}
    return v, faces


def render(geo, anim, t, tex, size, yaw_deg, hide, label):
    bones = geo["bones"]
    mats = bone_matrices(bones, anim, t)
    hidden = set()
    by = {b["name"]: b for b in bones}
    for b in bones:   # a hidden bone hides its children
        n = b["name"]
        while n:
            if n in hide: hidden.add(b["name"]); break
            n = by[n].get("parent")
    yaw = math.radians(yaw_deg)
    view = np.array([[math.cos(yaw), 0, math.sin(yaw)], [0, 1, 0], [-math.sin(yaw), 0, math.cos(yaw)]])
    tilt = math.radians(12)
    view = np.array([[1, 0, 0], [0, math.cos(tilt), -math.sin(tilt)], [0, math.sin(tilt), math.cos(tilt)]]) @ view
    light = np.array([0.4, 0.8, -0.45]); light /= np.linalg.norm(light)
    polys = []
    for b in bones:
        if b["name"] in hidden: continue
        M = mats[b["name"]]
        if np.allclose(M[:3, :3], 0): continue
        for c in b.get("cubes", []):
            v, faces = cube_faces(c)
            vw = (np.c_[v, np.ones(8)] @ M.T)[:, :3]
            vv = vw @ view.T
            for fname, idx in faces.items():
                p = vv[idx]
                n = np.cross(p[1] - p[0], p[2] - p[0])
                if np.linalg.norm(n) < 1e-9: continue
                n /= np.linalg.norm(n)
                uv = c["uv"][fname]["uv"] if isinstance(c["uv"], dict) else c["uv"]
                col = tex.get(tuple(uv), (150, 150, 150))
                nw = np.cross(vw[idx][1] - vw[idx][0], vw[idx][2] - vw[idx][0]); nw /= max(1e-9, np.linalg.norm(nw))
                shade = 0.55 + 0.45 * abs(float(nw @ light))
                polys.append((p[:, 2].mean(), p, tuple(int(ch * shade) for ch in col)))
    img = Image.new("RGB", (size, size), (168, 198, 230))
    d = ImageDraw.Draw(img)
    sc = size / 90.0
    cx, cy = size / 2, size * 0.78
    gy = -24
    for gx in range(-40, 41, 8):   # the ground grid at the feet
        a = np.array([[gx, gy, -40], [gx, gy, 40]]) @ view.T
        b = np.array([[-40, gy, gx], [40, gy, gx]]) @ view.T
        for seg in (a, b):
            d.line([(cx + seg[0][0] * sc, cy - (seg[0][1] - gy) * sc), (cx + seg[1][0] * sc, cy - (seg[1][1] - gy) * sc)], fill=(120, 160, 110))
    for _, p, col in sorted(polys, key=lambda q: -q[0]):   # far to near
        d.polygon([(cx + q[0] * sc, cy - (q[1] - gy) * sc) for q in p], fill=col, outline=(20, 20, 20))
    d.text((6, 6), label, fill=(20, 20, 40))
    return img


def tile_colours(tex_path, geo):
    """Average colour of every UV tile the cubes use."""
    img = Image.open(tex_path).convert("RGBA")
    W = geo["description"]["texture_width"]
    s = img.width / W
    out = {}
    for b in geo["bones"]:
        for c in b.get("cubes", []):
            faces = c["uv"].values() if isinstance(c["uv"], dict) else [{"uv": c["uv"], "uv_size": c["size"][:2]}]
            for f in faces:
                u, v = f["uv"]; w, h = f.get("uv_size", [8, 8])
                key = (u, v)
                if key in out: continue
                crop = img.crop((int(u * s), int(v * s), int((u + abs(w)) * s), int((v + abs(h)) * s)))
                px = [p for p in crop.getdata() if p[3] > 0] or [(150, 150, 150, 255)]
                out[key] = tuple(int(sum(p[i] for p in px) / len(px)) for i in range(3))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("model")
    ap.add_argument("anims", nargs="+")
    ap.add_argument("-o", "--out", required=True)
    ap.add_argument("--fps", type=int, default=20)
    ap.add_argument("--size", type=int, default=360)
    ap.add_argument("--hide", default="")
    ap.add_argument("--loops", type=int, default=2, help="times to play each looping animation")
    a = ap.parse_args()
    geo = json.load(open(os.path.join(ROOT, "geo", a.model + ".geo.json")))["minecraft:geometry"][0]
    anims = json.load(open(os.path.join(ROOT, "animations", a.model + ".animation.json")))["animations"]
    tex = tile_colours(os.path.join(ROOT, "textures", "geo", a.model + ".png"), geo)
    hide = set(x for x in a.hide.split(",") if x)
    frames = []
    for name in a.anims:
        an = anims["animation.%s.%s" % (a.model, name)]
        length = float(an.get("animation_length", 1.0))
        total = length * (a.loops if an.get("loop") else 1) + (0.3 if not an.get("loop") else 0)
        n = max(1, int(total * a.fps))
        for i in range(n):
            t = (i / a.fps) % length if an.get("loop") else min(length, i / a.fps)
            front = render(geo, an, t, tex, a.size, 35, hide, "%s  %.2fs  (front 3/4)" % (name, t))
            side = render(geo, an, t, tex, a.size, 90, hide, "%s  (side)" % name)
            f = Image.new("RGB", (a.size * 2, a.size))
            f.paste(front, (0, 0)); f.paste(side, (a.size, 0))
            frames.append(f)
    frames[0].save(a.out, save_all=True, append_images=frames[1:], duration=int(1000 / a.fps), loop=0)
    print("wrote %d frames to %s" % (len(frames), a.out))


if __name__ == "__main__":
    main()
