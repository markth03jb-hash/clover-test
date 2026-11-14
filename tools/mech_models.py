"""
The Mech Grimoire's GeckoLib models, made in code so they can be tuned and rebuilt:

  mech_titan  - the hard-light war machine that stands round its pilot (Pilot). One bone per buildable part, so the
                game hides the parts you haven't built: arm_l/arm_r (Arms) with fist_l/fist_r (Fists) on them,
                thrusters (Thrusters), leg_l/leg_r (Legs), plating (Plating), launcher (Launchers), core (Core),
                cannon (Cannon). The frame - hips, spine, head and visor - is always there.
                Animations: assemble (printed from the feet up), idle, walk, punch.
  mech_fist   - one great clenched fist (Rocket Fist, and Titan Buster scaled huge); the forearm bone is hidden for
                the rocket fist. Animations: clench, idle.

Made in Blockbench's entity space with the feet at y = -24 (the player model's origin is 24 px above the feet;
client/MechModels turns it the right way up), the front facing north (-Z).

The texture is hard light: see-through blue hex plating with bright seams, a dark frame, hazard stripes, an orange
visor/core glow and a blue thruster glow - drawn emissive and translucent in game. Every face maps a whole 32x32
tile, so each cube is wrapped in one material.

Run from the project root: python3 tools/mech_models.py
"""
import json
import math
import os
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "iron_spell_grimoire")
GEO = os.path.join(ROOT, "geo")
ANIM = os.path.join(ROOT, "animations")
TEX = os.path.join(ROOT, "textures", "geo")

# ---------------------------------------------------------------------------------------------------- the texture
T = 32
TILES = {   # material -> tile (column, row) in the 128x128 sheet
    "plate": (0, 0), "frame": (1, 0), "hazard": (2, 0), "visor": (3, 0),
    "thrust": (0, 1), "armor": (1, 1), "core": (2, 1), "joint": (3, 1),
    "knuckle": (0, 2), "barrel": (1, 2),
}


def hexgrid(d, x0, y0, r, col, w=1):
    """Hexagon outlines tiling a 32x32 tile at (x0, y0)."""
    hw = math.sqrt(3) * r
    for row in range(-1, int(T / (1.5 * r)) + 2):
        for c in range(-1, int(T / hw) + 2):
            cx = c * hw + (hw / 2 if row % 2 else 0)
            cy = row * 1.5 * r
            pts = [(x0 + cx + r * math.cos(math.radians(60 * k + 30)), y0 + cy + r * math.sin(math.radians(60 * k + 30))) for k in range(7)]
            d.line(pts, fill=col, width=w)


def make_texture(path):
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    def tile(name):
        c, r = TILES[name]
        return c * T, r * T

    def clip_tile(name, draw):
        """Draw into one tile only (so hex lines don't spill into the next)."""
        x0, y0 = tile(name)
        t = Image.new("RGBA", (T, T), (0, 0, 0, 0))
        draw(ImageDraw.Draw(t))
        img.paste(t, (x0, y0), t)

    # plate: see-through blue with a hex grid and a bright rim
    def plate(dd):
        dd.rectangle([0, 0, T - 1, T - 1], fill=(40, 110, 190, 120))
        hexgrid(dd, 0, 0, 5, (110, 210, 255, 210))
        dd.rectangle([0, 0, T - 1, T - 1], outline=(190, 240, 255, 255), width=2)
        dd.rectangle([3, 3, T - 4, T - 4], outline=(120, 200, 255, 140), width=1)
    clip_tile("plate", plate)

    # frame: a dark gunmetal skeleton with thin blue seams and bolts
    def frame(dd):
        dd.rectangle([0, 0, T - 1, T - 1], fill=(26, 34, 48, 245))
        dd.rectangle([0, 0, T - 1, T - 1], outline=(90, 170, 240, 255), width=1)
        dd.line([(0, T // 2), (T - 1, T // 2)], fill=(60, 120, 190, 200), width=1)
        for bx, by in ((4, 4), (T - 5, 4), (4, T - 5), (T - 5, T - 5)):
            dd.ellipse([bx - 1, by - 1, bx + 1, by + 1], fill=(160, 210, 255, 255))
    clip_tile("frame", frame)

    # hazard: yellow and black stripes
    def hazard(dd):
        dd.rectangle([0, 0, T - 1, T - 1], fill=(24, 24, 30, 255))
        for k in range(-T, 2 * T, 8):
            dd.polygon([(k, 0), (k + 4, 0), (k + 4 - T, T), (k - T, T)], fill=(255, 196, 40, 255))
        dd.rectangle([0, 0, T - 1, T - 1], outline=(255, 230, 140, 255), width=1)
    clip_tile("hazard", hazard)

    # visor: orange glow, hot white in the middle
    def visor(dd):
        for y in range(T):
            k = 1 - abs(y - T / 2) / (T / 2)
            col = (255, int(120 + 110 * k), int(40 + 160 * k ** 3), 255)
            dd.line([(0, y), (T - 1, y)], fill=col)
    clip_tile("visor", visor)

    # thruster: blue-white glow
    def thrust(dd):
        for y in range(T):
            k = y / (T - 1)
            dd.line([(0, y), (T - 1, y)], fill=(int(120 + 120 * k), int(190 + 60 * k), 255, 255))
        dd.rectangle([0, 0, T - 1, T - 1], outline=(30, 60, 110, 255), width=2)
    clip_tile("thrust", thrust)

    # armor: denser blue plate with panel lines (the torso)
    def armor(dd):
        dd.rectangle([0, 0, T - 1, T - 1], fill=(35, 80, 150, 165))
        hexgrid(dd, 0, 0, 3, (90, 180, 245, 150))
        for x in (10, 21):
            dd.line([(x, 0), (x, T - 1)], fill=(170, 235, 255, 230), width=1)
        dd.rectangle([0, 0, T - 1, T - 1], outline=(200, 245, 255, 255), width=2)
    clip_tile("armor", armor)

    # core: a ring of orange round a white heart
    def core(dd):
        dd.rectangle([0, 0, T - 1, T - 1], fill=(40, 20, 10, 255))
        for r in range(15, 0, -1):
            k = 1 - r / 15
            dd.ellipse([16 - r, 16 - r, 16 + r, 16 + r], fill=(255, int(110 + 140 * k), int(30 + 220 * k ** 2), 255))
        dd.ellipse([3, 3, T - 4, T - 4], outline=(255, 230, 160, 255), width=2)
    clip_tile("core", core)

    # joint: dark with a glowing blue ring (elbows, knees, hips)
    def joint(dd):
        dd.rectangle([0, 0, T - 1, T - 1], fill=(18, 24, 34, 255))
        dd.ellipse([6, 6, T - 7, T - 7], outline=(120, 210, 255, 255), width=3)
    clip_tile("joint", joint)

    # knuckle: bright plate edged in hazard yellow
    def knuckle(dd):
        dd.rectangle([0, 0, T - 1, T - 1], fill=(60, 140, 220, 190))
        hexgrid(dd, 0, 0, 4, (170, 235, 255, 230))
        dd.rectangle([0, 0, T - 1, T - 1], outline=(255, 200, 60, 255), width=3)
    clip_tile("knuckle", knuckle)

    # barrel: steel-blue rings
    def barrel(dd):
        dd.rectangle([0, 0, T - 1, T - 1], fill=(40, 60, 90, 235))
        for y in range(2, T, 6):
            dd.line([(0, y), (T - 1, y)], fill=(130, 210, 255, 255), width=2)
    clip_tile("barrel", barrel)

    img.save(path)


# ---------------------------------------------------------------------------------------------------- geometry
def cube(origin, size, mat, inflate=0.0):
    c, r = TILES[mat]
    face = {"uv": [c * T, r * T], "uv_size": [T, T]}
    cb = {"origin": [round(v, 3) for v in origin], "size": [round(v, 3) for v in size],
          "uv": {f: dict(face) for f in ("north", "south", "east", "west", "up", "down")}}
    if inflate:
        cb["inflate"] = inflate
    return cb


def bone(name, parent, pivot, cubes, rotation=None):
    b = {"name": name, "pivot": [round(v, 3) for v in pivot], "cubes": cubes}
    if parent:
        b["parent"] = parent
    if rotation:
        b["rotation"] = rotation
    return b


def mirror_x(cubes):
    out = []
    for c in cubes:
        c = json.loads(json.dumps(c))
        c["origin"][0] = -(c["origin"][0] + c["size"][0])
        out.append(c)
    return out


def titan():
    bones = [bone("root", None, [0, -24, 0], [])]
    # ---- the frame: hips, spine, neck and head (always there)
    bones.append(bone("frame", "root", [0, -24, 0], [
        cube([-9, -3, -5], [18, 5, 10], "frame"),            # hips
        cube([-3, 2, -2], [6, 18, 5], "frame"),              # spine
        cube([-7, 1, -4], [3, 3, 3], "joint"),                # hip joints
        cube([4, 1, -4], [3, 3, 3], "joint"),
        cube([-13, 16, -3], [4, 4, 4], "joint"),              # shoulder sockets
        cube([9, 16, -3], [4, 4, 4], "joint"),
    ]))
    bones.append(bone("head", "frame", [0, 20, 0], [
        cube([-5, 20, -6], [10, 8, 11], "plate"),
        cube([-6, 26, -7], [12, 3, 13], "frame"),           # brow
        cube([-4.5, 22.5, -6.8], [9, 2.5, 1], "visor"),      # the visor slit
        cube([-1, 29, -3], [2, 4, 1], "hazard"),             # antenna
    ]))
    # ---- plating: the torso shell, back plate and a skirt
    bones.append(bone("plating", "frame", [0, 10, 0], [
        cube([-11, 4, -7], [22, 16, 14], "armor"),
        cube([-9.5, 9, -8], [19, 9, 1], "plate"),            # breastplate
        cube([-11, 19, -7.5], [22, 2, 15], "hazard"),        # collar trim
        cube([-10, -4, -6], [8, 6, 1], "plate"),             # skirt plates
        cube([2, -4, -6], [8, 6, 1], "plate"),
    ]))
    bones.append(bone("core", "frame", [0, 13, -8], [
        cube([-3, 10, -9.5], [6, 6, 2], "core"),
    ]))
    # ---- arms (Arms) with fists (Fists) on them
    arm_l = [
        cube([10, 14, -6], [9, 8, 12], "armor"),             # pauldron
        cube([10.5, 21, -6.5], [8, 1.5, 13], "hazard"),
        cube([12, 4, -3.5], [6, 11, 7], "plate"),            # upper arm
        cube([12.5, 1.5, -3], [5, 3, 6], "joint"),           # elbow
        cube([11.5, -8, -4], [7, 10, 8], "plate"),           # forearm
        cube([11, -6, -4.5], [8, 2, 9], "frame"),
    ]
    fist_l = [
        cube([11, -15, -5], [8, 7, 9], "knuckle"),
        cube([10.5, -10, -5.5], [9, 2, 10], "frame"),
    ]
    bones.append(bone("arm_l", "frame", [15, 18, 0], arm_l))
    bones.append(bone("fist_l", "arm_l", [15, -8, 0], fist_l))
    bones.append(bone("arm_r", "frame", [-15, 18, 0], mirror_x(arm_l)))
    bones.append(bone("fist_r", "arm_r", [-15, -8, 0], mirror_x(fist_l)))
    # ---- legs (Legs)
    leg_l = [
        cube([3, -12, -4], [8, 10, 8], "plate"),             # thigh
        cube([3.5, -14, -4.5], [7, 3, 9], "joint"),          # knee
        cube([3.5, -21, -3.5], [7, 8, 7], "plate"),          # shin
        cube([3.5, -18, -4.2], [7, 2, 0.7], "hazard"),
        cube([2.5, -24, -7], [9, 3, 11], "frame"),           # foot
        cube([2.5, -22, -7.6], [9, 1, 1], "hazard"),
    ]
    bones.append(bone("leg_l", "frame", [7, -2, 0], leg_l))
    bones.append(bone("leg_r", "frame", [-7, -2, 0], mirror_x(leg_l)))
    # ---- thrusters (Thrusters): a pack and two fins on the back
    bones.append(bone("thrusters", "frame", [0, 12, 7], [
        cube([-7, 7, 5], [14, 11, 5], "frame"),
        cube([-6.5, 3, 6], [5, 4, 4], "thrust"),
        cube([1.5, 3, 6], [5, 4, 4], "thrust"),
        cube([-17, 15, 7], [10, 2, 3], "plate"),
        cube([7, 15, 7], [10, 2, 3], "plate"),
        cube([-17, 13, 8], [2, 2, 2], "thrust"),
        cube([15, 13, 8], [2, 2, 2], "thrust"),
    ]))
    # ---- launchers (Launchers): a missile pod on the left shoulder
    bones.append(bone("launcher", "frame", [14, 22, 0], [
        cube([10, 22.5, -6], [9, 7, 11], "frame"),
        cube([10.5, 23, -6.6], [8, 6, 1], "visor"),           # the tubes, glowing
        cube([10, 29.5, -6], [9, 1, 11], "hazard"),
    ]))
    # ---- cannon (Cannon): a long barrel over the right shoulder
    bones.append(bone("cannon", "frame", [-14, 23, 0], [
        cube([-19, 21.5, -3], [9, 7, 9], "frame"),
        cube([-16.5, 23, -21], [4, 4, 19], "barrel"),
        cube([-17, 22.5, -23], [5, 5, 2], "thrust"),          # the muzzle, glowing
        cube([-19.2, 21.5, 3], [9.4, 7, 1], "hazard"),
    ]))
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.mech_titan", "texture_width": 128, "texture_height": 128,
                        "visible_bounds_width": 5, "visible_bounds_height": 5, "visible_bounds_offset": [0, 1, 0]},
        "bones": bones}]}


def fist():
    # a fist pointing north (-Z): the knuckles at the front, the wrist and a long forearm behind
    bones = [bone("root", None, [0, 0, 0], [])]
    bones.append(bone("palm", "root", [0, 0, 0], [
        cube([-6, -6, -4], [12, 12, 10], "plate"),
        cube([-6.5, -6.5, 0], [13, 13, 2], "hazard"),
        cube([-6.5, 5.5, -4], [13, 1.5, 8], "armor"),
    ]))
    for i in range(4):   # four fingers, curled down in front
        x = -6 + i * 3.05
        bones.append(bone(f"finger{i}", "palm", [x + 1.3, 5, -4], [
            cube([x, -1, -9], [2.7, 6, 5], "knuckle"),
        ]))
    bones.append(bone("thumb", "palm", [6, -2, -2], [
        cube([5.5, -5, -7], [3, 5, 5], "knuckle"),
    ]))
    bones.append(bone("wrist", "root", [0, 0, 6], [
        cube([-5, -5, 6], [10, 10, 4], "joint"),
        cube([-3, -3, 10], [6, 6, 2], "thrust"),             # the jet nozzle
    ]))
    bones.append(bone("forearm", "root", [0, 0, 10], [
        cube([-5.5, -5.5, 10], [11, 11, 18], "armor"),
        cube([-6, -6, 14], [12, 12, 2], "hazard"),
        cube([-6, -6, 24], [12, 12, 2], "frame"),
        cube([-7, 2, 16], [3, 3, 8], "thrust"),
        cube([4, 2, 16], [3, 3, 8], "thrust"),
    ]))
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.mech_fist", "texture_width": 128, "texture_height": 128,
                        "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 0, 0]},
        "bones": bones}]}


# ---------------------------------------------------------------------------------------------------- animations
def kf(d):
    return {f"{t:.4f}": v for t, v in sorted(d.items())}


def titan_anims():
    A = {}
    # assemble: each part is printed in from nothing, the feet first, the head last, with a little overshoot
    order = [("leg_l", 0.0), ("leg_r", 0.05), ("frame", 0.12), ("plating", 0.28), ("core", 0.36),
             ("arm_l", 0.42), ("arm_r", 0.47), ("fist_l", 0.55), ("fist_r", 0.58), ("thrusters", 0.6),
             ("launcher", 0.66), ("cannon", 0.7), ("head", 0.78)]
    bones = {}
    for name, t0 in order:
        bones[name] = {"scale": kf({0.0: [0, 0, 0], t0: [0, 0, 0], t0 + 0.12: [1.12, 1.12, 1.12], t0 + 0.2: [1, 1, 1], 1.0: [1, 1, 1]})}
    bones["root"] = {"position": kf({0.0: [0, -2, 0], 0.5: [0, 0, 0], 1.0: [0, 0, 0]})}
    A["animation.mech_titan.assemble"] = {"loop": False, "animation_length": 1.0, "bones": bones}
    # idle: it breathes - the body bobs, the head turns a little, the fins flex
    A["animation.mech_titan.idle"] = {"loop": True, "animation_length": 3.0, "bones": {
        "root": {"position": kf({0.0: [0, 0, 0], 1.5: [0, 0.4, 0], 3.0: [0, 0, 0]})},
        "head": {"rotation": kf({0.0: [0, 0, 0], 1.0: [0, 6, 0], 2.0: [0, -6, 0], 3.0: [0, 0, 0]})},
        "arm_l": {"rotation": kf({0.0: [0, 0, -3], 1.5: [0, 0, -6], 3.0: [0, 0, -3]})},
        "arm_r": {"rotation": kf({0.0: [0, 0, 3], 1.5: [0, 0, 6], 3.0: [0, 0, 3]})},
        "thrusters": {"rotation": kf({0.0: [0, 0, 0], 1.5: [4, 0, 0], 3.0: [0, 0, 0]})},
    }}
    # walk: heavy strides, arms swinging against the legs
    A["animation.mech_titan.walk"] = {"loop": True, "animation_length": 1.2, "bones": {
        "leg_l": {"rotation": kf({0.0: [22, 0, 0], 0.6: [-22, 0, 0], 1.2: [22, 0, 0]})},
        "leg_r": {"rotation": kf({0.0: [-22, 0, 0], 0.6: [22, 0, 0], 1.2: [-22, 0, 0]})},
        "arm_l": {"rotation": kf({0.0: [-16, 0, -4], 0.6: [16, 0, -4], 1.2: [-16, 0, -4]})},
        "arm_r": {"rotation": kf({0.0: [16, 0, 4], 0.6: [-16, 0, 4], 1.2: [16, 0, 4]})},
        "root": {"position": kf({0.0: [0, 0, 0], 0.3: [0, 0.8, 0], 0.6: [0, 0, 0], 0.9: [0, 0.8, 0], 1.2: [0, 0, 0]})},
    }}
    # punch: the right arm drives forward and back (played while the pilot swings)
    A["animation.mech_titan.punch"] = {"loop": True, "animation_length": 0.5, "bones": {
        "arm_r": {"rotation": kf({0.0: [0, 0, 4], 0.12: [-95, -10, 4], 0.25: [-85, -8, 4], 0.5: [0, 0, 4]})},
        "frame": {"rotation": kf({0.0: [0, 0, 0], 0.12: [0, -12, 0], 0.5: [0, 0, 0]})},
    }}
    return {"format_version": "1.8.0", "animations": A}


def fist_anims():
    A = {}
    curl = {}
    for i in range(4):
        d = 0.03 * i
        curl[f"finger{i}"] = {"rotation": kf({0.0: [70, 0, 0], 0.08 + d: [70, 0, 0], 0.22 + d: [-12, 0, 0], 0.3 + d: [0, 0, 0]})}
    curl["thumb"] = {"rotation": kf({0.0: [0, 50, 0], 0.15: [0, 50, 0], 0.3: [0, 0, 0]})}
    curl["root"] = {"scale": kf({0.0: [0.6, 0.6, 0.6], 0.15: [1.08, 1.08, 1.08], 0.3: [1, 1, 1]})}
    A["animation.mech_fist.clench"] = {"loop": False, "animation_length": 0.4, "bones": curl}
    A["animation.mech_fist.idle"] = {"loop": True, "animation_length": 0.6, "bones": {
        "palm": {"position": kf({0.0: [0, 0, 0], 0.3: [0, 0, -0.3], 0.6: [0, 0, 0]})},
    }}
    return {"format_version": "1.8.0", "animations": A}


def main():
    for d in (GEO, ANIM, TEX):
        os.makedirs(d, exist_ok=True)
    json.dump(titan(), open(os.path.join(GEO, "mech_titan.geo.json"), "w"), indent=1)
    json.dump(fist(), open(os.path.join(GEO, "mech_fist.geo.json"), "w"), indent=1)
    json.dump(titan_anims(), open(os.path.join(ANIM, "mech_titan.animation.json"), "w"), indent=1)
    json.dump(fist_anims(), open(os.path.join(ANIM, "mech_fist.animation.json"), "w"), indent=1)
    make_texture(os.path.join(TEX, "mech_titan.png"))
    make_texture(os.path.join(TEX, "mech_fist.png"))
    print("mech models written")


if __name__ == "__main__":
    main()
