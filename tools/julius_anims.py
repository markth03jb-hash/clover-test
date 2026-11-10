"""Julius Novachrono's own cast animations, added to player_animation/arcane_animations.json (idempotent: replaces them).
Conventions (from the other animations): arm x- = raise forward/up, right_arm z+ / left_arm z- = out to the side,
torso x+ = bow forward, body position y+ = rise. Every move has one clear snap: the tick its magic goes off."""
import json, os
P = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/iron_spell_grimoire/player_animation/arcane_animations.json')
E_ = 'easeInOutSine'

def ch(keys, ease=E_):
    out = {}
    for i, (t, v) in enumerate(keys):
        k = {'vector': list(v)}
        if i: k['easing'] = ease
        out[f'{t:g}'] = k
    return out

def anim(length, **bones):
    b = {}
    for name, chans in bones.items():
        b[name] = {c: ch(k) for c, k in chans.items()}
    return {'animation_length': length, 'bones': b}

Z = (0, 0, 0)
A = {}
# 1 Clock Hand Bolt - the right hand draws back across the chest, then flicks two fingers out (snap at 0.3 s)
A['julius_hand_bolt'] = anim(0.75,
    right_arm=dict(rotation=[(0, Z), (0.15, (-62, 42, -18)), (0.3, (-96, -8, 0)), (0.5, (-90, -6, 0)), (0.75, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.15, (-10, 0, -10)), (0.3, (-18, 0, -22)), (0.75, Z)]),
    torso=dict(rotation=[(0, Z), (0.15, (0, 26, 0)), (0.3, (6, -16, 0)), (0.5, (4, -12, 0)), (0.75, Z)]),
    body=dict(position=[(0, Z), (0.15, (0, -0.3, 0)), (0.3, (0, 0, 0))]))
# 2 Time Stop Point - left palm thrust out, right hand snaps beside the face (snap at 0.35 s), held
A['julius_stop_point'] = anim(0.95,
    left_arm=dict(rotation=[(0, Z), (0.2, (-70, 12, -10)), (0.35, (-93, 0, 0)), (0.75, (-92, 0, 0)), (0.95, Z)]),
    right_arm=dict(rotation=[(0, Z), (0.2, (-100, -20, 30)), (0.35, (-128, -36, 30)), (0.75, (-126, -34, 30)), (0.95, Z)]),
    torso=dict(rotation=[(0, Z), (0.2, (0, 12, 0)), (0.35, (-4, -10, 0)), (0.75, (-4, -10, 0)), (0.95, Z)]),
    right_leg=dict(rotation=[(0, Z), (0.35, (-12, 0, 4)), (0.75, (-12, 0, 4)), (0.95, Z)]),
    left_leg=dict(rotation=[(0, Z), (0.35, (8, 0, -4)), (0.75, (8, 0, -4)), (0.95, Z)]))
# 3 Time Skip - a low lean, arms swept back, gone (snap at 0.15 s), then upright where you land
A['julius_time_skip'] = anim(0.6,
    torso=dict(rotation=[(0, Z), (0.15, (28, 0, 0)), (0.35, (14, 0, 0)), (0.6, Z)]),
    right_arm=dict(rotation=[(0, Z), (0.15, (42, 0, 12)), (0.35, (20, 0, 8)), (0.6, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.15, (42, 0, -12)), (0.35, (20, 0, -8)), (0.6, Z)]),
    right_leg=dict(rotation=[(0, Z), (0.15, (-30, 0, 0)), (0.35, (-10, 0, 0)), (0.6, Z)]),
    left_leg=dict(rotation=[(0, Z), (0.15, (24, 0, 0)), (0.35, (8, 0, 0)), (0.6, Z)]),
    body=dict(position=[(0, Z), (0.1, (0, -1.4, 0)), (0.35, (0, -0.4, 0)), (0.6, Z)]))
# 4 Time Recovery - the right hand winds a circle backwards above the head, then sweeps down (snap at 0.55 s)
A['julius_time_recovery'] = anim(1.1,
    right_arm=dict(rotation=[(0, Z), (0.15, (-150, 0, 34)), (0.27, (-176, 0, 6)), (0.39, (-152, 0, -26)), (0.47, (-132, 0, 4)),
                             (0.55, (-30, 0, 22)), (0.85, (-26, 0, 18)), (1.1, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.3, (-44, 30, -6)), (0.85, (-46, 32, -6)), (1.1, Z)]),
    torso=dict(rotation=[(0, Z), (0.3, (-12, 0, 0)), (0.55, (10, 0, 0)), (0.85, (6, 0, 0)), (1.1, Z)]),
    body=dict(position=[(0, Z), (0.3, (0, 0.8, 0)), (0.55, (0, -0.3, 0)), (1.1, Z)]))
# 5 Chrono Acceleration - arms crossed at the chest, flung out wide (snap at 0.35 s)
A['julius_acceleration'] = anim(0.9,
    right_arm=dict(rotation=[(0, Z), (0.2, (-74, -42, 0)), (0.35, (-14, 0, 104)), (0.65, (-10, 0, 98)), (0.9, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.2, (-74, 42, 0)), (0.35, (-14, 0, -104)), (0.65, (-10, 0, -98)), (0.9, Z)]),
    torso=dict(rotation=[(0, Z), (0.2, (14, 0, 0)), (0.35, (-14, 0, 0)), (0.65, (-10, 0, 0)), (0.9, Z)]),
    body=dict(position=[(0, Z), (0.2, (0, -0.6, 0)), (0.35, (0, 1.2, 0)), (0.65, (0, 0.8, 0)), (0.9, Z)]))
# 6 Ticking Snare - the right palm rises, then slams down toward the ground (snap at 0.35 s)
A['julius_ticking_snare'] = anim(0.85,
    right_arm=dict(rotation=[(0, Z), (0.2, (-165, 0, 10)), (0.35, (-42, 0, 6)), (0.6, (-38, 0, 6)), (0.85, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.2, (-20, 0, -30)), (0.35, (10, 0, -24)), (0.85, Z)]),
    torso=dict(rotation=[(0, Z), (0.2, (-10, 0, 0)), (0.35, (32, 0, 0)), (0.6, (28, 0, 0)), (0.85, Z)]),
    right_leg=dict(rotation=[(0, Z), (0.35, (-26, 0, 0)), (0.6, (-26, 0, 0)), (0.85, Z)]),
    left_leg=dict(rotation=[(0, Z), (0.35, (16, 0, 0)), (0.6, (16, 0, 0)), (0.85, Z)]),
    body=dict(position=[(0, Z), (0.2, (0, 0.4, 0)), (0.35, (0, -1.6, 0)), (0.6, (0, -1.4, 0)), (0.85, Z)]))
# 7 Time Theft - reach out (0.25 s), close the fist and drag it back to the hip (snap at 0.5 s)
A['julius_time_theft'] = anim(1.0,
    right_arm=dict(rotation=[(0, Z), (0.25, (-98, 0, 0)), (0.36, (-96, 0, 0)), (0.5, (-22, 18, 14)), (0.8, (-18, 16, 12)), (1.0, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.25, (-20, 0, -18)), (0.5, (-36, -20, -10)), (1.0, Z)]),
    torso=dict(rotation=[(0, Z), (0.25, (12, -18, 0)), (0.5, (-8, 22, 0)), (0.8, (-6, 18, 0)), (1.0, Z)]),
    body=dict(position=[(0, Z), (0.25, (0, -0.4, -1)), (0.5, (0, 0, 1)), (1.0, Z)]))
# 8 Hourglass Barrage - the right hand raised like a conductor's, beating time down: five beats from 0.4 s
A['julius_hourglass_barrage'] = anim(1.4,
    right_arm=dict(rotation=[(0, Z), (0.25, (-172, 0, 10)), (0.4, (-118, 0, 10)), (0.55, (-164, 0, 10)), (0.7, (-118, 0, 10)),
                             (0.85, (-164, 0, 10)), (1.0, (-112, 0, 10)), (1.15, (-150, 0, 10)), (1.4, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.25, (-60, 26, -8)), (1.15, (-58, 26, -8)), (1.4, Z)]),
    torso=dict(rotation=[(0, Z), (0.25, (-10, 0, 0)), (0.4, (4, 0, 0)), (0.55, (-6, 0, 0)), (0.7, (4, 0, 0)), (0.85, (-6, 0, 0)), (1.0, (6, 0, 0)), (1.4, Z)]))
# 9 Rewind Ward - the body becomes a clock: right arm straight up, left arm out at three o'clock (snap at 0.3 s)
A['julius_rewind_ward'] = anim(0.95,
    right_arm=dict(rotation=[(0, Z), (0.15, (-90, 0, 30)), (0.3, (-180, 0, 0)), (0.7, (-178, 0, 0)), (0.95, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.15, (-30, 0, -40)), (0.3, (0, 0, -96)), (0.7, (0, 0, -94)), (0.95, Z)]),
    torso=dict(rotation=[(0, Z), (0.3, (-6, 0, 0)), (0.7, (-6, 0, 0)), (0.95, Z)]),
    body=dict(position=[(0, Z), (0.3, (0, 0.6, 0)), (0.7, (0, 0.6, 0)), (0.95, Z)]))
# 10 Echo of the Past - a step back and a sweep of both arms across the body, leaving yourself behind (snap at 0.45 s)
A['julius_echo'] = anim(1.0,
    right_arm=dict(rotation=[(0, Z), (0.2, (-88, 62, 0)), (0.45, (-86, -58, 0)), (0.7, (-60, -40, 0)), (1.0, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.2, (-70, 70, 0)), (0.45, (-70, -50, 0)), (0.7, (-40, -30, 0)), (1.0, Z)]),
    torso=dict(rotation=[(0, Z), (0.2, (0, 34, 0)), (0.45, (0, -34, 0)), (0.7, (0, -20, 0)), (1.0, Z)]),
    body=dict(position=[(0, Z), (0.2, (0, 0, 1.2)), (0.45, (0, 0, -0.8)), (1.0, Z)]))
# 11 Chronostasis Field - rise with arms opening wide and up, chest lifted, held (snap at 0.6 s)
A['julius_chronostasis'] = anim(1.4,
    right_arm=dict(rotation=[(0, Z), (0.3, (-40, 0, 40)), (0.6, (-24, 0, 132)), (1.1, (-22, 0, 128)), (1.4, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.3, (-40, 0, -40)), (0.6, (-24, 0, -132)), (1.1, (-22, 0, -128)), (1.4, Z)]),
    torso=dict(rotation=[(0, Z), (0.3, (10, 0, 0)), (0.6, (-14, 0, 0)), (1.1, (-12, 0, 0)), (1.4, Z)]),
    right_leg=dict(rotation=[(0, Z), (0.6, (6, 0, 8)), (1.1, (6, 0, 8)), (1.4, Z)]),
    left_leg=dict(rotation=[(0, Z), (0.6, (-6, 0, -8)), (1.1, (-6, 0, -8)), (1.4, Z)]),
    body=dict(position=[(0, Z), (0.3, (0, -0.6, 0)), (0.6, (0, 1.8, 0)), (1.1, (0, 2.0, 0)), (1.4, Z)]))
# 12 Zenith Hour - both arms rise overhead (0.5 s), then the right drops to point at the world: time stops (snap at 0.9 s)
A['julius_zenith_hour'] = anim(1.8,
    right_arm=dict(rotation=[(0, Z), (0.5, (-172, 0, 18)), (0.78, (-176, 0, 14)), (0.9, (-92, -4, 0)), (1.5, (-90, -4, 0)), (1.8, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.5, (-172, 0, -18)), (0.9, (-170, 0, -22)), (1.5, (-168, 0, -22)), (1.8, Z)]),
    torso=dict(rotation=[(0, Z), (0.5, (-16, 0, 0)), (0.78, (-18, 0, 0)), (0.9, (8, -8, 0)), (1.5, (6, -8, 0)), (1.8, Z)]),
    right_leg=dict(rotation=[(0, Z), (0.5, (8, 0, 6)), (1.5, (8, 0, 6)), (1.8, Z)]),
    left_leg=dict(rotation=[(0, Z), (0.5, (-8, 0, -6)), (1.5, (-8, 0, -6)), (1.8, Z)]),
    body=dict(position=[(0, Z), (0.5, (0, 2.4, 0)), (0.78, (0, 2.8, 0)), (0.9, (0, 2.0, 0)), (1.5, (0, 2.0, 0)), (1.8, Z)]))
# Surpassing Limits: arms crossed at the chest while rising, then thrown open to the sky
A['limit_time'] = anim(1.6,
    right_arm=dict(rotation=[(0, Z), (0.5, (-76, -40, 0)), (0.9, (-80, -44, 0)), (1.1, (-150, 0, 60)), (1.4, (-148, 0, 58)), (1.6, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.5, (-76, 40, 0)), (0.9, (-80, 44, 0)), (1.1, (-150, 0, -60)), (1.4, (-148, 0, -58)), (1.6, Z)]),
    torso=dict(rotation=[(0, Z), (0.5, (12, 0, 0)), (0.9, (14, 0, 0)), (1.1, (-14, 0, 0)), (1.6, Z)]),
    body=dict(position=[(0, Z), (0.5, (0, 1.0, 0)), (0.9, (0, 1.6, 0)), (1.1, (0, 2.6, 0)), (1.4, (0, 2.4, 0)), (1.6, Z)]))

d = json.load(open(P))
d['animations'].update(A)
json.dump(d, open(P, 'w'), indent=1)
print('animations', len(d['animations']))
