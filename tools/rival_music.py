"""Music for the four rivals, synthesised from scratch with the same instruments as tools/boss3_music.py (original,
free to use): one seamless loop each.
 Seren Vale    - a quick duel in 6/8 feel: bright glass chimes over plucked strings, light drums.
 Brannoch Tor  - a slow march: war drums, a low brass-like growl, a stately string line.
 the Bellmars  - two melodies a third apart answering each other over a running bass.
 Mira Hollowell- a sly, swinging walk: pizzicato bass, coin-bright chimes, a crooked waltz turn.
python3 tools/rival_music.py src/main/resources/assets/iron_spell_grimoire/sounds/music"""
import os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
src = open(os.path.join(HERE, "boss3_music.py")).read()
exec(src[src.index("import numpy"):src.index("# Licht:")])   # the shared instruments (drone, chime, war, bass, strings...)
OUT = sys.argv[1] if len(sys.argv) > 1 else "."

# Seren: bright and quick
seren = track(150, 16, [drone(0.12, D - 12),
                        kicks(0.32, [1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0], [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0]),
                        chime(0.5, [7, 9, 11, 9, 7, 4, 6, 7, 9, 11, 12, 11, 9, 7, 6, 4], 0.5),
                        ostinato(0.1, [0, 4, 7, 4, 2, 4, 7, 9]),
                        strings(0.2, [[0, 4, 7], [-3, 0, 4], [-5, -1, 2], [-7, -3, 0]], 2, 1400)])
write("rival_seren_1", seren)

# Brannoch: a march
bran = track(92, 16, [drone(0.22, D - 24),
                      war(0.55, [1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 1, 0, 1, 0, 0, 0]),
                      growl(0.25, [0, 0, 5, 3]),
                      bass(0.28, [0, 0, 5, 3], 1.0),
                      strings(0.3, [[0, 3, 7], [0, 3, 7], [5, 8, 12], [3, 7, 10]], 4, 1000),
                      stabs(0.18, [0], [0, 7, 12])])
write("rival_brannoch_1", bran)

# the Bellmars: question and answer
twins = track(128, 16, [drone(0.14),
                        kicks(0.4, [1, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 0], [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0]),
                        bass(0.28, [0, 3, 5, 4], 0.5),
                        chime(0.45, [4, None, 5, None, 7, None, 5, 4, None, None, None, None, 2, None, 4, None], 0.5),
                        bells(0.4, [None, None, None, None, None, None, None, None, 2, None, 3, None, 5, None, 3, 2], 0.5),
                        strings(0.22, [[0, 3, 7], [3, 7, 10], [5, 8, 12], [4, 7, 11]], 2, 1200)])
write("rival_twins_1", twins)

# Mira: a sly swing
mira = track(112, 16, [drone(0.1),
                       kicks(0.3, [1, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0], [0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0]),
                       bass(0.3, [0, 5, 3, 6], 0.5),
                       chime(0.45, [0, None, 2, 3, None, 6, 5, None, 3, None, 2, 0, None, 6, None, 5], 0.5),
                       ostinato(0.07, [0, 3, 6, 3, 5, 3, 2, 3]),
                       strings(0.18, [[0, 3, 6], [5, 8, 12], [3, 6, 10], [6, 10, 13]], 2, 900)])
write("rival_mira_1", mira)
print("rival music written")
