"""Textured, lit preview of a GeckoLib model (its real texture and glow mask), front three-quarter and side, at a few
moments of its animations. python3 tools/preview_tex.py shadow_knight idle:0 slash:0.3 -o out.png [--gif anim]"""
import argparse, json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
import geo_paint as gp
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "iron_spell_grimoire")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("model"); ap.add_argument("shots", nargs="+"); ap.add_argument("-o", "--out", required=True)
    ap.add_argument("--size", type=int, default=460); ap.add_argument("--gif", default="")
    a = ap.parse_args()
    geo = json.load(open(os.path.join(ROOT, "geo", a.model + ".geo.json")))["minecraft:geometry"][0]
    anims = json.load(open(os.path.join(ROOT, "animations", a.model + ".animation.json")))["animations"]
    sheet = Image.open(os.path.join(ROOT, "textures", "geo", a.model + ".png"))
    glowp = os.path.join(ROOT, "textures", "geo", a.model + "_glowmask.png")
    glow = Image.open(glowp) if os.path.exists(glowp) else Image.new("RGBA", sheet.size)
    frames = []
    for shot in a.shots:
        name, t = shot.split(":")
        an = anims["animation.%s.%s" % (a.model, name)]
        row = Image.new("RGB", (a.size * 3, a.size))
        for i, yaw in enumerate((30, 150, 90)):
            row.paste(gp.render(geo, an, float(t), sheet, glow, a.size, yaw, "%s %ss" % (name, t) if i == 0 else ("back" if i == 1 else "side")), (i * a.size, 0))
        frames.append(row)
    out = Image.new("RGB", (a.size * 3, a.size * len(frames)))
    for i, f in enumerate(frames): out.paste(f, (0, i * a.size))
    out.save(a.out)
    if a.gif:
        an = anims["animation.%s.%s" % (a.model, a.gif)]
        L = float(an.get("animation_length", 1)); n = int(L * 15)
        fr = [gp.render(geo, an, k / 15.0, sheet, glow, a.size, 30, a.gif) for k in range(n)]
        fr[0].save(a.out.replace(".png", "_" + a.gif + ".gif"), save_all=True, append_images=fr[1:], duration=66, loop=0)
    print("wrote", a.out)


if __name__ == "__main__":
    main()
