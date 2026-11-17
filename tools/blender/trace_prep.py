"""Turn a concept picture of a weapon (lying point-right on a plain background) into the data for a 3D model that
matches it exactly: its outline traced from the picture, its surface puffed out from that outline (thin and sharp at
the blade, thicker and rounded at the grip and guard), and the picture itself as its texture on both faces - plus a
second texture holding only its red parts, for a glowing layer laid just over them.

Run with ordinary Python (needs opencv-python, numpy, scipy); tools/blender/trace_build.py then builds the mesh in
Blender from what this writes.

  python3 tools/blender/trace_prep.py SPEC.json OUT_DIR

SPEC: {"image": path, "name": "rosewrath", "length": 36 (model px, pommel to point),
       "grip": [x0, x1] (the grip's span along the picture, as fractions of the weapon's length),
       "thick": {"blade": 0.55, "guard": 0.95, "grip": 1.05} (half-thickness caps, model px),
       "guard": [x0, x1], "spacing": 16 (interior point spacing, upscaled picture px)}
"""
import json, math, os, sys
import cv2
import numpy as np
from scipy.spatial import Delaunay

S = 4   # the picture is upscaled this much before tracing


def segment(img):
    big = cv2.resize(img, None, fx=S, fy=S, interpolation=cv2.INTER_CUBIC)
    g = big.astype(int)
    c = g[2:12, 2:12].reshape(-1, 3).mean(0)            # the background colour, from a corner
    near = (np.abs(g - c).max(2) < 20)
    num, lab = cv2.connectedComponents(near.astype(np.uint8))
    border = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    knife = (~np.isin(lab, list(border))).astype(np.uint8) * 255
    knife = cv2.morphologyEx(knife, cv2.MORPH_OPEN, np.ones((5, 5), np.uint8))
    num, lab, stats, _ = cv2.connectedComponentsWithStats(knife)
    i = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
    knife = np.where(lab == i, 255, 0).astype(np.uint8)
    knife = cv2.morphologyEx(knife, cv2.MORPH_CLOSE, np.ones((7, 7), np.uint8))
    return big, knife


def main(spec_path, out):
    spec = json.load(open(spec_path))
    os.makedirs(out, exist_ok=True)
    img = cv2.imread(spec["image"])
    big, mask = segment(img)
    ys, xs = np.nonzero(mask)
    x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
    pad = 4
    x0, y0, x1, y1 = max(0, x0 - pad), max(0, y0 - pad), min(mask.shape[1] - 1, x1 + pad), min(mask.shape[0] - 1, y1 + pad)
    big, mask = big[y0:y1 + 1, x0:x1 + 1], mask[y0:y1 + 1, x0:x1 + 1]
    H, W = mask.shape
    k = spec["length"] / W                                   # model px per (upscaled) picture px
    # the outline
    smooth = cv2.threshold(cv2.GaussianBlur(mask, (7, 7), 0), 127, 255, cv2.THRESH_BINARY)[1]   # no ragged pixel steps
    cs, _ = cv2.findContours(smooth, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    cont = max(cs, key=cv2.contourArea)
    cont = cv2.approxPolyDP(cont, 2.4, True)[:, 0, :].astype(float)
    # points inside it, on a grid, kept away from the outline
    dist = cv2.distanceTransform(mask, cv2.DIST_L2, 5)
    sp = spec.get("spacing", 16)
    inner = [(x, y) for y in range(sp // 2, H, sp) for x in range(sp // 2, W, sp) if dist[y, x] > sp * 0.45]
    # a ring of points just inside the outline, so the surface rises smoothly from the edge
    ring = []
    for i in range(len(cont)):
        a, b = cont[i], cont[(i + 1) % len(cont)]
        n = max(1, int(np.linalg.norm(b - a) / (sp * 0.6)))
        for j in range(n):
            p = a + (b - a) * j / n
            ring.append(p)
    # the red: the rose (the biggest round red blob) and the cracks
    b_, g_, r_ = [big[:, :, i].astype(int) for i in range(3)]
    red = ((r_ > 150) & (r_ > g_ * 1.8) & (r_ > b_ * 1.8)).astype(np.uint8) * (mask > 0)
    solid = cv2.morphologyEx(red, cv2.MORPH_CLOSE, np.ones((15, 15), np.uint8))   # the rose's petal lines split it: close them up
    n_, lab_, st_, cen_ = cv2.connectedComponentsWithStats(solid)
    rose = None
    best = 0
    for i in range(1, n_):
        w_, h_, area = st_[i, cv2.CC_STAT_WIDTH], st_[i, cv2.CC_STAT_HEIGHT], st_[i, cv2.CC_STAT_AREA]
        roundness = area / max(1, w_ * h_)
        if 0.6 < w_ / max(1, h_) < 1.6 and roundness > 0.5 and area > best: best, rose = area, (cen_[i][0], cen_[i][1], 0.5 * (w_ + h_) / 2)
    if rose: print("rose at", [round(v) for v in rose])
    extra = []
    if rose:   # dense points over the rose, so its dome is smooth
        rx, ry, rr = rose
        for y in range(int(ry - rr), int(ry + rr) + 1, max(4, sp // 3)):
            for x in range(int(rx - rr), int(rx + rr) + 1, max(4, sp // 3)):
                if (x - rx) ** 2 + (y - ry) ** 2 < (rr * 1.05) ** 2 and 0 <= x < W and 0 <= y < H and dist[y, x] > 3: extra.append((x, y))
    pts = np.array(list(cont) + [p for p in inner] + extra, dtype=float)
    nb = len(cont)
    tri = Delaunay(pts)
    keep = []
    for t in tri.simplices:
        P = pts[t]
        ok = True
        for q in (P.mean(0), (P[0] + P[1]) / 2, (P[1] + P[2]) / 2, (P[2] + P[0]) / 2):
            xi, yi = int(round(q[0])), int(round(q[1]))
            if not (0 <= xi < W and 0 <= yi < H) or mask[yi, xi] == 0: ok = False; break
        if ok and max(np.linalg.norm(P[0] - P[1]), np.linalg.norm(P[1] - P[2]), np.linalg.norm(P[2] - P[0])) > sp * 3.2: ok = False   # no long slivers
        if ok: keep.append([int(i) for i in t])
    # how thick it is at each point: rising from 0 at the outline, capped per part (blade / guard / grip)
    th = spec["thick"]
    def cap(xf):
        g0, g1 = spec["grip"]; d0, d1 = spec["guard"]
        def ramp(a, b, w=0.025): return min(1.0, max(0.0, (xf - a) / w)) * min(1.0, max(0.0, (b - xf) / w))
        c = th["blade"]
        c = c + (th["guard"] - c) * ramp(d0, d1)
        c = c + (th["grip"] - c) * ramp(g0 - 0.2, g1)     # the grip and everything behind it (the pommel)
        return c
    # surface relief from the art: its dark lines sink a little, its highlights rise
    luma = cv2.cvtColor(big, cv2.COLOR_BGR2GRAY).astype(np.float32) / 255
    luma = cv2.GaussianBlur(luma, (0, 0), 2.0)
    rel = luma - cv2.GaussianBlur(luma, (0, 0), 14.0)
    blade_from = spec["guard"][1]
    heights = []
    for i, (x, y) in enumerate(pts):
        if i < nb: heights.append(0.0); continue
        xi, yi = int(round(x)), int(round(y))
        d = float(dist[yi, xi]) * k
        xf = x / W
        c = cap(xf)
        rounded = min(c, c * math.sqrt(d / max(1e-3, c * 1.6)))       # the grip, guard and pommel: rounded
        wedge = min(spec.get("ridge", 0.8), d * spec.get("bevel", 0.34))   # the blade: a V - sharp edges, a ridge down the middle
        f = min(1.0, max(0.0, (xf - blade_from) / 0.04))
        h = rounded * (1 - f) + wedge * f
        if d > 0.35: h += spec.get("relief", 0.9) * float(rel[yi, xi]) * min(1.0, d / 1.0)
        if red[yi, xi]: h += 0.1                                         # the cracks stand a touch proud
        if rose:                                                         # the rose swells up out of the blade
            rr_ = math.hypot(x - rose[0], y - rose[1]) / (rose[2] * 1.05)
            if rr_ < 1: h += spec.get("rose", 1.1) * math.sqrt(1 - rr_ * rr_)
        heights.append(max(0.02, h))
    # where along the picture the grip's middle is: the model's origin
    gx = (spec["grip"][0] + spec["grip"][1]) / 2 * W
    gy = H / 2
    verts = [((gy - y) * k, h, (x - gx) * k) for (x, y), h in zip(pts, heights)]   # (X across, Y half-thickness, Z along)
    uvs = [(x / W, 1 - y / H) for x, y in pts]
    # which triangles carry red (they get the glowing layer)
    red_tris = []
    for ti, t in enumerate(keep):
        P = pts[t].astype(int)
        xa, xb, ya, yb = P[:, 0].min(), P[:, 0].max() + 1, P[:, 1].min(), P[:, 1].max() + 1
        if red[ya:yb, xa:xb].any(): red_tris.append(ti)
    # the textures: the picture (its colours bled out past the outline, so the edges don't pick up background),
    # and a copy holding only the red
    tex = big.copy()
    inv = (mask == 0).astype(np.uint8)
    tex = cv2.inpaint(tex, inv, 6, cv2.INPAINT_TELEA)
    TW = 1024
    TH = max(16, int(round(H * TW / W / 4)) * 4)
    tex_s = cv2.resize(tex, (TW, TH), interpolation=cv2.INTER_AREA)
    cv2.imwrite(os.path.join(out, spec["name"] + ".png"), tex_s)
    glow = np.zeros((H, W, 4), np.uint8)
    redd = cv2.dilate(red, np.ones((3, 3), np.uint8))
    glow[:, :, :3] = tex                                   # the picture's own reds (and its colours round them, for clean edges)
    glow_s = cv2.resize(glow, (TW, TH), interpolation=cv2.INTER_AREA)
    a = cv2.resize(redd * 255, (TW, TH), interpolation=cv2.INTER_AREA)
    glow_s[:, :, 3] = np.where(a > 110, 255, 0)
    glow_s[:, :, :3][glow_s[:, :, 3] == 0] = 0             # nothing under the see-through parts: a far smaller file
    cv2.imwrite(os.path.join(out, spec["name"] + "_glow.png"), glow_s)
    json.dump({"verts": verts, "uvs": uvs, "tris": keep, "boundary": nb, "red_tris": red_tris,
               "length": spec["length"], "name": spec["name"]}, open(os.path.join(out, "mesh.json"), "w"))
    print("points", len(pts), "triangles a side", len(keep), "red", len(red_tris), "texture", TW, "x", TH)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
