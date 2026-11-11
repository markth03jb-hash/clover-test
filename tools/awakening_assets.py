"""Grimoire Awakening: the awakened spells' cast animations, spell icons and lang (first batch: Black, Dark, Wind, Light)."""
import json, os, sys, math
sys.path.insert(0, os.path.dirname(__file__))
from icons import make
R = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/iron_spell_grimoire')
E_ = 'easeInOutSine'
def ch(keys):
    out = {}
    for i, (t, v) in enumerate(keys):
        k = {'vector': list(v)}
        if i: k['easing'] = E_
        out[f'{t:g}'] = k
    return out
def anim(length, **bones):
    return {'animation_length': length, 'bones': {n: {c: ch(k) for c, k in cs.items()} for n, cs in bones.items()}}
Z = (0, 0, 0)
A = {}
# Absolute Zetten - a low, still stance with the blade held down at the side (1 s), then one flat cut across
A['awakened_zetten'] = anim(1.6,
    right_arm=dict(rotation=[(0, Z), (0.2, (-20, 20, 30)), (0.95, (-22, 22, 30)), (1.02, (-95, 70, 10)), (1.1, (-92, -80, -10)), (1.35, (-88, -78, -10)), (1.6, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.2, (-30, 0, -20)), (0.95, (-30, 0, -20)), (1.1, (-20, 0, -40)), (1.6, Z)]),
    torso=dict(rotation=[(0, Z), (0.2, (18, 25, 0)), (0.95, (18, 26, 0)), (1.1, (8, -40, 0)), (1.35, (6, -38, 0)), (1.6, Z)]),
    body=dict(position=[(0, Z), (0.2, (0, -0.8, 0)), (0.95, (0, -0.8, 0)), (1.1, (0, -0.4, 0)), (1.6, Z)]))
# Moon-Severing Iai - hand on the hilt at the left hip, crouched while the moon swells (1.5 s), then the draw
A['awakened_iai'] = anim(2.1,
    right_arm=dict(rotation=[(0, Z), (0.3, (-40, 50, -20)), (1.45, (-42, 52, -20)), (1.55, (-90, -60, 0)), (1.65, (-90, -110, 10)), (1.9, (-86, -105, 8)), (2.1, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.3, (-20, -10, -10)), (1.45, (-22, -10, -10)), (1.6, (-30, 0, -50)), (2.1, Z)]),
    torso=dict(rotation=[(0, Z), (0.3, (20, 30, 0)), (1.45, (22, 32, 0)), (1.6, (4, -60, 0)), (1.9, (2, -55, 0)), (2.1, Z)]),
    body=dict(position=[(0, Z), (0.3, (0, -1.0, 0)), (1.45, (0, -1.0, 0)), (1.6, (0, -0.3, 0)), (2.1, Z)]))
# Never-Never Land - the right hand thrown up to the sky, then both arms spread to raise the dome
A['awakened_star'] = anim(1.6,
    right_arm=dict(rotation=[(0, Z), (0.3, (-175, 0, 10)), (0.7, (-176, 0, 8)), (1.1, (-110, 0, 70)), (1.35, (-108, 0, 68)), (1.6, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.3, (-30, 0, -20)), (0.7, (-40, 0, -30)), (1.1, (-110, 0, -70)), (1.35, (-108, 0, -68)), (1.6, Z)]),
    torso=dict(rotation=[(0, Z), (0.3, (-12, 0, 0)), (1.1, (-16, 0, 0)), (1.6, Z)]))
# Lamp of Avior - light drawn in between the hands at the chest (1.1 s), then thrown forward with the right hand
A['awakened_lamp'] = anim(1.7,
    right_arm=dict(rotation=[(0, Z), (0.3, (-60, -30, 0)), (1.05, (-62, -32, 0)), (1.2, (-95, 0, 0)), (1.45, (-92, 0, 0)), (1.7, Z)]),
    left_arm=dict(rotation=[(0, Z), (0.3, (-60, 30, 0)), (1.05, (-62, 32, 0)), (1.2, (-30, 0, -30)), (1.7, Z)]),
    torso=dict(rotation=[(0, Z), (0.3, (6, 0, 0)), (1.05, (8, 0, 0)), (1.2, (-6, -16, 0)), (1.7, Z)]))
P = os.path.join(R, 'player_animation/arcane_animations.json')
d = json.load(open(P)); d['animations'].update(A); json.dump(d, open(P, 'w'), indent=1)

# ------------------------------------------------------------------ icons
make('absolute_zetten', (46, 12, 14), (4, 2, 3), (235, 235, 240), (190, 30, 40),
     [('m', 'l', 1, 12, 14, 3), ('w', 'l', 2, 12, 14, 4), ('a', 'l', 4, 9, 3, 7), ('a', 'l', 7, 8, 8, 6), ('a', 'l', 10, 6, 9, 4), ('a', 'l', 5, 10, 6, 12), ('a', 'l', 9, 8, 11, 10),
      ('d', 'l', 0, 15, 15, 15), ('m', 'R', 0, 0, 15, 15)])
make('moon_severing_iai', (40, 16, 60), (6, 2, 10), (245, 240, 255), (150, 80, 220),
     [('d', 'E', 3, 1, 12, 10), ('a', 'e', 3, 1, 12, 10), ('m', 'a', 3, 1, 12, 10, 200, 340), ('m', 'l', 0, 11, 15, 9), ('w', 'l', 0, 12, 15, 10),
      ('a', 'l', 2, 13, 5, 15), ('a', 'l', 9, 13, 11, 15), ('a', 'p', 13, 13)])
make('never_never_land', (20, 50, 70), (2, 6, 14), (255, 230, 140), (110, 230, 200),
     [('a', 'a', 1, 2, 14, 26, 180, 360), ('a', 'l', 1, 14, 14, 14)] + sum([[('w', 'p', x, y), ('m', 'p', x - 1, y), ('m', 'p', x + 1, y), ('m', 'p', x, y - 1), ('m', 'p', x, y + 1)]
                                                                          for (x, y) in [(3, 9), (7, 4), (12, 8), (9, 11), (5, 12)]], [])
     + [('m', 'l', 3, 9, 7, 4), ('m', 'l', 7, 4, 12, 8), ('m', 'l', 12, 8, 9, 11)])
make('lamp_of_avior', (60, 48, 14), (8, 6, 2), (255, 214, 96), (255, 250, 220),
     [('m', 'l', 7, 0, 7, 15), ('m', 'l', 0, 7, 15, 7), ('a', 'l', 3, 3, 11, 11), ('a', 'l', 3, 11, 11, 3), ('m', 'E', 5, 5, 10, 10), ('w', 'E', 6, 6, 9, 9),
      ('a', 'e', 2, 2, 13, 13)])

# ------------------------------------------------------------------ lang
L = os.path.join(R, 'lang/en_us.json')
lang = json.load(open(L))
lang.update({
    'spell.iron_spell_grimoire.absolute_zetten': 'Demon-Destroyer: Absolute Zetten',
    'spell.iron_spell_grimoire.absolute_zetten.guide': 'AWAKENED. Settle into a still stance for a second: a blow that lands on you in it is turned aside and answered at once, and magic flying at you is cut out of the air. Then one cut through everything ahead for 18 blocks - and a beat later the world catches up and splits along the line.',
    'spell.iron_spell_grimoire.moon_severing_iai': 'Dark Cloaked Mana Zone: Moon-Severing Iai',
    'spell.iron_spell_grimoire.moon_severing_iai.guide': 'AWAKENED. A black moon rises over you and drags at everything within 16 blocks while you hold your blade at your hip. Then the draw: one ring of darkness cut round you in every direction, and the moon breaks and rains its shards down.',
    'spell.iron_spell_grimoire.never_never_land': 'Star Magic: Never-Never Land',
    'spell.iron_spell_grimoire.never_never_land.guide': 'AWAKENED. Star and Wind magic together: a dome of stars 16 blocks across for ten seconds. Foes inside are slowed, you and your friends cast 20% stronger, and the stars fire beams of starlight at whoever is inside.',
    'spell.iron_spell_grimoire.lamp_of_avior': 'Light Magic: Lamp of Avior',
    'spell.iron_spell_grimoire.lamp_of_avior.guide': 'AWAKENED. Light gathers round you from every side, then you throw it as one star-bright lamp. Where it lands it bursts - an explosion of light strong enough to stop a tornado, a star\'s cross flaring over it.',
})
json.dump(lang, open(L, 'w'), indent=2, ensure_ascii=False)
