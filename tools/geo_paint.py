"""
Proper textures for code-made GeckoLib models: give every cube face its own area of the texture sheet at a steady
pixel density (instead of one shared tile squashed onto every face), paint each face by its material - lit from above,
bevelled edges, grain, engraved lines, rivets, cloth folds - and render a textured, lit preview of the result.

  layout(bones, density)        -> assigns per-face uv to every cube; returns the sheet size and the list of faces
  paint(faces, size, painters)  -> the colour sheet and the glow-mask sheet
  render(geo, anim, t, sheet, glow, size, yaw) -> a textured preview image (used by tools/preview_tex.py)

Face orientation (GeckoLib/Bedrock per-face uv): on the four side faces the top of the face is at the top of its
texture area; up/down faces are seen from above/below.
"""
import math
import random

import numpy as np
from PIL import Image, ImageDraw

FACE_DIMS = {"north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2)}


def layout(bones, density=4, sheet=1024, pad=1):
    """Shelf-pack every face of every cube; writes the uv into the cubes. Returns (sheet, faces)."""
    faces = []
    for b in bones:
        for c in b.get("cubes", []):
            mat = c.pop("mat", c.get("_mat"))
            c["_mat"] = mat
            sz = c["size"]
            for f, (i, j) in FACE_DIMS.items():
                w = max(1, int(math.ceil(sz[i] * density)))
                h = max(1, int(math.ceil(sz[j] * density)))
                faces.append({"cube": c, "face": f, "w": w, "h": h, "mat": mat, "size": sz})
    order = sorted(range(len(faces)), key=lambda k: -faces[k]["h"])
    x = y = row = 0
    for k in order:
        fc = faces[k]
        if x + fc["w"] + pad > sheet:
            x = 0; y += row + pad; row = 0
        fc["x"], fc["y"] = x, y
        x += fc["w"] + pad
        row = max(row, fc["h"])
    if y + row > sheet:
        raise ValueError("texture sheet too small: need %d rows" % (y + row))
    for fc in faces:
        uv = fc["cube"].setdefault("uv", {})
        if not isinstance(uv, dict) or "uv" in uv: fc["cube"]["uv"] = uv = {}
        uv[fc["face"]] = {"uv": [fc["x"], fc["y"]], "uv_size": [fc["w"], fc["h"]]}
    return sheet, faces


def clean(bones):
    for b in bones:
        for c in b.get("cubes", []):
            c.pop("_mat", None)


# ---------------------------------------------------------------------------------------------------- painting helpers
def lit(base, k):
    return tuple(max(0, min(255, int(c * k))) for c in base)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def paint(faces, size, painters, seed=5):
    """painters: material -> fn(img_px, glow_px, x, y, w, h, face, rnd). Returns (colour, glow) images."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px, gx = img.load(), glow.load()
    for n, fc in enumerate(faces):
        rnd = random.Random(seed * 1000 + n)
        fn = painters[fc["mat"]]
        if getattr(fn, "wants_face", False): fn(px, gx, fc["x"], fc["y"], fc["w"], fc["h"], fc["face"], rnd, fc)
        else: fn(px, gx, fc["x"], fc["y"], fc["w"], fc["h"], fc["face"], rnd)
    return img, glow


def _filigree(px, gx, x0, y0, w, h, col, glow_col, rnd):
    """An etched, mirrored curl ornament in the middle of a big plate: two spirals unwinding from a centre line."""
    cx, cy = w / 2, h / 2
    R = min(w, h) * 0.36
    pts = []
    for k in range(60):
        t = k / 59
        ang = t * 4.2
        r = R * (1 - t * 0.85)
        pts.append((cx + 0.15 * R + r * math.cos(ang) * 0.9, cy - R * 0.2 + r * math.sin(ang) * 0.7))
    for side in (1, -1):
        for (x, y) in pts:
            xx = int(cx + side * (x - cx)); yy = int(y)
            if 1 <= xx < w - 1 and 1 <= yy < h - 1:
                px[x0 + xx, y0 + yy] = col + (255,)
                if rnd.random() < 0.35: gx[x0 + xx, y0 + yy] = glow_col + (255,)
    for y in range(int(cy - R), int(cy + R)):   # the centre line
        if 1 <= y < h - 1: px[x0 + int(cx), y0 + y] = col + (255,)


def plate_painter(base, seam, glow_col, engrave=True, ridges=False, rivets=True, ornate=False):
    """Polished dark plate: brighter at the top, a bevel round the edge, fine brushed grain, an engraved seam line
    inset from the edge (glowing) on the bigger faces, and rivets in the corners."""
    def p(px, gx, x0, y0, w, h, face, rnd):
        top = face == "up"; bot = face == "down"
        for y in range(h):
            for x in range(w):
                k = 1.0 if top else 0.55 if bot else 1.15 - 0.45 * (y / max(1, h - 1))
                k *= 0.94 + 0.06 * rnd.random()
                if (x * 7 + y * 3) % 11 == 0: k *= 1.06   # brushed grain
                c = lit(base, k)
                if y == 0 or x == 0: c = lit(base, k * 1.7)          # bevel: lit top and left edge
                if y == h - 1 or x == w - 1: c = lit(base, k * 0.45)   # shadowed bottom and right edge
                if ridges and w >= 6 and (x % 6 == 3): c = lit(base, k * 1.4)
                px[x0 + x, y0 + y] = c + (255,)
        if engrave and w >= 44 and h >= 44 and not bot and not top:
            i = 4 if min(w, h) < 60 else 6
            for x in range(i, w - i):
                for yy in (i, h - 1 - i):
                    px[x0 + x, y0 + yy] = seam + (255,); gx[x0 + x, y0 + yy] = glow_col + (255,)
            for y in range(i, h - i):
                for xx in (i, w - 1 - i):
                    px[x0 + xx, y0 + y] = seam + (255,); gx[x0 + xx, y0 + y] = glow_col + (255,)
        for _ in range((w * h) // 90):   # scratches and worn edges
            sx, sy, L = rnd.randrange(w), rnd.randrange(h), rnd.randint(2, 5)
            for k in range(L):
                xx, yy = sx + k, sy + k // 2
                if 0 <= xx < w and 0 <= yy < h: px[x0 + xx, y0 + yy] = lit(base, 1.8) + (255,)
        for x in range(w):
            if rnd.random() < 0.25: px[x0 + x, y0] = lit(base, 2.4) + (255,)   # bright worn top edge
        if ornate and w >= 36 and h >= 36 and not (top or bot):
            _filigree(px, gx, x0, y0, w, h, lit(base, 2.2), glow_col, rnd)
        if rivets and w >= 28 and h >= 28 and not (top or bot):
            for (rx, ry) in ((1, 1), (w - 3, 1), (1, h - 3), (w - 3, h - 3)):
                for dx in range(2):
                    for dy in range(2):
                        px[x0 + rx + dx, y0 + ry + dy] = (lit(base, 2.4) if dx == 0 and dy == 0 else lit(base, 0.5)) + (255,)
    return p


def glow_painter(col, bright):
    def p(px, gx, x0, y0, w, h, face, rnd):
        for y in range(h):
            for x in range(w):
                k = 0.85 + 0.15 * rnd.random()
                px[x0 + x, y0 + y] = lit(col, k) + (255,); gx[x0 + x, y0 + y] = bright + (255,)
    return p


def mail_painter(base):
    def p(px, gx, x0, y0, w, h, face, rnd):
        for y in range(h):
            for x in range(w):
                ring = ((x + (y // 2 % 2)) % 2 == 0) and (y % 2 == 0)
                k = (1.0 - 0.4 * y / max(1, h)) * (1.5 if ring else 0.7)
                px[x0 + x, y0 + y] = lit(base, k) + (255,)
    return p


def cloth_painter(base, fray=True):
    """Cloth falling in folds: light and dark bands across, darker toward the hem, the hem torn unevenly."""
    def p(px, gx, x0, y0, w, h, face, rnd):
        cut = [h - rnd.randint(0, max(1, h // 8)) for _ in range(w)]
        for y in range(h):
            for x in range(w):
                fold = 0.75 + 0.3 * math.sin(x * 1.1 + rnd.random() * 0.4)
                k = fold * (1.15 - 0.5 * y / max(1, h - 1))
                a = 255
                if fray and face in ("north", "south", "east", "west") and y >= cut[x]: a = 0
                px[x0 + x, y0 + y] = lit(base, k) + (a,)
    return p


def blade_painter(steel, fuller, fuller_glow):
    """Dark steel, the edges catching the light, a glowing groove down the middle of the long side."""
    def p(px, gx, x0, y0, w, h, face, rnd):
        along_x = w > h
        L, Wd = (w, h) if along_x else (h, w)
        for y in range(h):
            for x in range(w):
                a = (y if along_x else x) / max(1, Wd - 1)   # across the blade 0..1
                k = 0.9 + 0.25 * abs(a - 0.5) * 2
                c = lit(steel, k)
                if a < 0.12 or a > 0.88: c = mix(c, (190, 200, 225), 0.7)   # the edges
                if 0.38 <= a <= 0.62 and face in ("north", "south", "east", "west") and Wd >= 4:
                    c = fuller; gx[x0 + x, y0 + y] = fuller_glow + (255,)
                px[x0 + x, y0 + y] = c + (255,)
    return p


def grip_painter(base, band):
    def p(px, gx, x0, y0, w, h, face, rnd):
        for y in range(h):
            for x in range(w):
                c = band if (x + y) % 6 < 2 else lit(base, 0.9 + 0.2 * rnd.random())
                px[x0 + x, y0 + y] = c + (255,)
    return p


def visor_painter(base, slit, slit_glow):
    """A dark face plate with a Y-shaped slit burning through it."""
    def p(px, gx, x0, y0, w, h, face, rnd):
        for y in range(h):
            for x in range(w):
                px[x0 + x, y0 + y] = lit(base, 1.1 - 0.4 * y / max(1, h)) + (255,)
        if face != "north": return
        cx = w / 2
        th = max(1, w // 8)
        for y in range(h):
            fy = y / max(1, h - 1)
            pts = []
            if fy > 0.42: pts.append(cx)                                     # the stem
            else:                                                            # the two arms rising out
                off = (0.42 - fy) / 0.42 * (w * 0.38)
                pts += [cx - off, cx + off]
            for xc in pts:
                for x in range(int(xc - th), int(xc + th) + 1):
                    if 0 <= x < w and 0.12 < fy < 0.95:
                        px[x0 + x, y0 + y] = slit + (255,); gx[x0 + x, y0 + y] = slit_glow + (255,)
    return p


def flameblade_painter(blue, dark, glow_col, seed=3):
    """A glowing pale-blue blade with dark, smoky veins winding along it like flames licking up from the guard -
    thick near the guard, splitting and thinning toward the point. Only the broad faces carry the veins."""
    def p(px, gx, x0, y0, w, h, face, rnd, fc=None):
        along_x = w > h
        L, Wd = (w, h) if along_x else (h, w)
        broad = face in ("north", "south") and Wd >= 2
        g0, g1 = (fc.get("t0", 0.0), fc.get("t1", 1.0)) if fc else (0.0, 1.0)   # where this piece sits along the whole blade
        r2 = random.Random(seed + (1 if face == "south" else 0))
        ph = [r2.uniform(0, 6.28) for _ in range(3)]
        for j in range(Wd):
            for i in range(L):
                u, a = i / max(1, L - 1), j / max(1, Wd - 1)   # u along (0 = guard end when not along_x: texture top is the far end)
                k = 0.88 + 0.18 * (1 - abs(a - 0.5) * 2)
                c = lit(blue, k)
                gl = glow_col
                if broad:
                    t = g0 + (g1 - g0) * (1 - u if not along_x else u)   # 0 at the guard, 1 at the point, over the whole blade
                    dark_here = False
                    # one bold vein winding up the middle, thinning toward the point, and two thinner branches off it
                    centre = 0.5 + 0.16 * math.sin(t * 7 + ph[0]) + 0.05 * math.sin(t * 19 + ph[1])
                    if t < 0.85 and abs(a - centre) < 0.2 * (1 - t / 0.85) + 0.045: dark_here = True
                    for n, side in ((1, -1), (2, 1)):
                        t0 = 0.12 + 0.18 * n
                        if t0 < t < t0 + 0.28:
                            k2 = (t - t0) / 0.28
                            bc = centre + side * 0.3 * k2 + 0.04 * math.sin(t * 25 + ph[n])
                            if abs(a - bc) < 0.09 * (1 - k2) + 0.035: dark_here = True
                    if dark_here: c = dark; gl = None
                if a < 0.08 or a > 0.92: c = mix(c, (210, 235, 255), 0.6)   # the edges, brighter
                xx, yy = (i, j) if along_x else (j, i)
                px[x0 + xx, y0 + yy] = c + (255,)
                if gl: gx[x0 + xx, y0 + yy] = gl + (255,)
    p.wants_face = True
    return p


def eyes_painter(base, slit, slit_glow):
    """A dark brow plate with two slanted eye slits burning through it, angled down toward the middle (a glare)."""
    def p(px, gx, x0, y0, w, h, face, rnd):
        for y in range(h):
            for x in range(w):
                px[x0 + x, y0 + y] = lit(base, 1.15 - 0.4 * y / max(1, h)) + (255,)
        if face != "north": return
        for side in (-1, 1):
            for k in range(int(w * 0.36)):
                fx = k / max(1, w * 0.36)
                x = int(w / 2 + side * (w * 0.08 + k))
                yc = h * (0.62 - 0.35 * fx)   # higher at the outer end
                for y in range(int(yc - h * 0.16), int(yc + h * 0.16) + 1):
                    if 0 <= x < w and 0 <= y < h:
                        px[x0 + x, y0 + y] = slit + (255,); gx[x0 + x, y0 + y] = slit_glow + (255,)
    return p


def rune_painter(base, line, glow_col):
    """A dark disc with a glowing rune carved in it: a ring and a cross of spokes."""
    def p(px, gx, x0, y0, w, h, face, rnd):
        cx, cy, R = (w - 1) / 2, (h - 1) / 2, min(w, h) / 2 - 1
        for y in range(h):
            for x in range(w):
                d = math.hypot(x - cx, y - cy)
                c = lit(base, 1.1 - 0.3 * d / max(1, R))
                on = abs(d - R * 0.78) < max(1, R * 0.12) or (d < R * 0.6 and (abs(x - cx) < 1 or abs(y - cy) < 1)) or d < R * 0.15
                if on and face in ("east", "west"):
                    c = line; gx[x0 + x, y0 + y] = glow_col + (255,)
                px[x0 + x, y0 + y] = c + (255,)
    return p


def smoke_painter(dark, tint, glow_col):
    """Shadow smoke: black at the root, a blue tint toward the tip, breaking up into dithered wisps as it fades."""
    def p(px, gx, x0, y0, w, h, face, rnd):
        for y in range(h):
            for x in range(w):
                t = 1 - y / max(1, h - 1)   # 0 at the root (bottom of the texture), 1 at the tip
                keep = (1 - t) ** 0.5 + 0.3 * math.sin(x * 1.7 + y * 0.6) * t
                if rnd.random() > keep: continue
                c = mix(dark, tint, t * 0.8)
                px[x0 + x, y0 + y] = c + (255,)
                if t > 0.55 and rnd.random() < 0.2: gx[x0 + x, y0 + y] = glow_col + (255,)
    return p


# ---------------------------------------------------------------------------------------------------- textured preview
def rot_xyz(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]]); Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]); Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def _persp_coeffs(dst, src):
    """Coefficients for PIL's PERSPECTIVE transform mapping output points dst to input points src."""
    A, B = [], []
    for (x, y), (u, v) in zip(dst, src):
        A.append([x, y, 1, 0, 0, 0, -u * x, -u * y]); B.append(u)
        A.append([0, 0, 0, x, y, 1, -v * x, -v * y]); B.append(v)
    return np.linalg.solve(np.array(A, float), np.array(B, float)).tolist()


# corners of each face in cube space (u along the texture's x, v down the texture's y), matching Bedrock's mapping
def _face_corners(x, y, z, w, h, d):
    return {   # each: [top-left, top-right, bottom-right, bottom-left] as seen on the texture
        "north": [(x + w, y + h, z), (x, y + h, z), (x, y, z), (x + w, y, z)],
        "south": [(x, y + h, z + d), (x + w, y + h, z + d), (x + w, y, z + d), (x, y, z + d)],
        "east":  [(x, y + h, z), (x, y + h, z + d), (x, y, z + d), (x, y, z)],
        "west":  [(x + w, y + h, z + d), (x + w, y + h, z), (x + w, y, z), (x + w, y, z + d)],
        "up":    [(x + w, y + h, z), (x, y + h, z), (x, y + h, z + d), (x + w, y + h, z + d)],
        "down":  [(x + w, y, z + d), (x, y, z + d), (x, y, z), (x + w, y, z)],
    }


def render(geo, anim, t, sheet, glow, size, yaw_deg, label="", ground=0.0, scale_div=70.0, sample=None):
    import preview_geo as pg
    bones = geo["bones"]
    mats = pg.bone_matrices(bones, anim, t)
    yaw = math.radians(yaw_deg)
    view = np.array([[math.cos(yaw), 0, math.sin(yaw)], [0, 1, 0], [-math.sin(yaw), 0, math.cos(yaw)]])
    tilt = math.radians(10)
    view = np.array([[1, 0, 0], [0, math.cos(tilt), -math.sin(tilt)], [0, math.sin(tilt), math.cos(tilt)]]) @ view
    light = np.array([0.35, 0.85, -0.4]); light /= np.linalg.norm(light)
    sc = size / scale_div
    cx, cy = size / 2, size * 0.86
    S = sheet.convert("RGBA"); G = glow.convert("RGBA")
    polys = []
    for b in bones:
        M = mats[b["name"]]
        for c in b.get("cubes", []):
            (x, y, z), (w, h, d) = c["origin"], c["size"]
            inf = c.get("inflate", 0)
            x, y, z, w, h, d = x - inf, y - inf, z - inf, w + 2 * inf, h + 2 * inf, d + 2 * inf
            R = None
            if "rotation" in c:
                r = c["rotation"]; R = rot_xyz(math.radians(-r[0]), math.radians(-r[1]), math.radians(r[2])); piv = np.array(c["pivot"], float)
            for f, corners in _face_corners(x, y, z, w, h, d).items():
                v = np.array(corners, float)
                v[:, 0] = -v[:, 0] if False else v[:, 0]
                if R is not None: v = (v - piv) @ R.T + piv
                vw = (np.c_[v, np.ones(4)] @ M.T)[:, :3]
                nrm = np.cross(vw[1] - vw[0], vw[3] - vw[0])
                if np.linalg.norm(nrm) < 1e-9: continue
                nrm /= np.linalg.norm(nrm)
                vv = vw @ view.T
                if (np.cross(vv[1] - vv[0], vv[3] - vv[0]))[2] < 0: continue   # facing away
                uv = c["uv"][f]
                shade = 0.45 + 0.55 * max(0.0, float(-nrm @ -light)) if True else 1
                shade = 0.5 + 0.5 * abs(float(nrm @ light))
                pts2 = [(cx + q[0] * sc, cy - (q[1] - ground) * sc) for q in vv]
                polys.append((vv[:, 2].mean(), pts2, uv, shade))
    img = Image.new("RGB", (size, size), (34, 38, 52))
    dr = ImageDraw.Draw(img)
    for gx in range(-40, 41, 8):
        a = np.array([[gx, ground, -40], [gx, ground, 40]]) @ view.T; bb = np.array([[-40, ground, gx], [40, ground, gx]]) @ view.T
        for seg in (a, bb):
            dr.line([(cx + seg[0][0] * sc, cy - (seg[0][1] - ground) * sc), (cx + seg[1][0] * sc, cy - (seg[1][1] - ground) * sc)], fill=(54, 60, 78))
    for _, pts2, uv, shade in sorted(polys, key=lambda q: -q[0]):
        (u0, v0), (uw, vh) = uv["uv"], uv["uv_size"]
        src = [(u0, v0), (u0 + uw, v0), (u0 + uw, v0 + vh), (u0, v0 + vh)]
        xs = [p[0] for p in pts2]; ys = [p[1] for p in pts2]
        bx0, by0, bx1, by1 = int(min(xs)) - 1, int(min(ys)) - 1, int(max(xs)) + 2, int(max(ys)) + 2
        if bx1 - bx0 < 1 or by1 - by0 < 1: continue
        local = [(p[0] - bx0, p[1] - by0) for p in pts2]
        try:
            co = _persp_coeffs(local, src)
        except np.linalg.LinAlgError:
            continue
        tile = S.transform((bx1 - bx0, by1 - by0), Image.PERSPECTIVE, co, Image.NEAREST)
        gtile = G.transform((bx1 - bx0, by1 - by0), Image.PERSPECTIVE, co, Image.NEAREST)
        arr = np.asarray(tile).astype(float)
        garr = np.asarray(gtile).astype(float)
        lit_rgb = arr[..., :3] * shade
        lit_rgb = np.where(garr[..., 3:4] > 0, np.maximum(lit_rgb, garr[..., :3]), lit_rgb)   # glowing parts ignore the light
        mask = Image.new("L", tile.size, 0)
        ImageDraw.Draw(mask).polygon(local, fill=255)
        m = (np.asarray(mask) > 0) & (arr[..., 3] > 8)
        out = Image.fromarray(np.clip(lit_rgb, 0, 255).astype(np.uint8))
        img.paste(out, (bx0, by0), Image.fromarray((m * 255).astype(np.uint8)))
    if label: ImageDraw.Draw(img).text((6, 6), label, fill=(200, 210, 230))
    return img
