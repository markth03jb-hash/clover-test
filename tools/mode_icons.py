"""Icons for mode timers that aren't pages of their own (the HUD shows a mode's spell icon by its id): Borrowed Noon's
debt, Recoil Plating, Untouchable, Punish Ready, and the Bound Demon's takeover, warning, pact, afterglow, last stand
and parry. python3 tools/mode_icons.py"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from icons import make
from trio_assets import VO, SO
from bound_assets import BO
SUN = ((70, 46, 14), (14, 8, 2), (255, 190, 70), (255, 245, 210))

make("borrowed_noon", *SUN, [("m", "E", 4, 4, 11, 11), ("d", "r", 7, 3, 14, 12), ("a", "l", 7, 2, 7, 13), ("w", "p", 5, 7)])
make("recoil_plating", *VO, [("m", "r", 4, 3, 11, 12), ("d", "r", 5, 4, 10, 11), ("a", "l", 2, 7, 4, 7), ("a", "l", 11, 7, 13, 7), ("w", "p", 7, 7)])
make("untouchable", *SO, [("m", "e", 2, 2, 13, 13), ("a", "e", 5, 5, 10, 10), ("w", "p", 7, 7), ("a", "l", 1, 7, 3, 7), ("a", "l", 12, 7, 14, 7)])
make("punish_ready", *SO, [("a", "l", 3, 12, 12, 3), ("w", "l", 10, 3, 12, 3), ("w", "l", 12, 3, 12, 5), ("m", "E", 2, 2, 5, 5)])
make("bd_takeover", *BO, [("a", "E", 2, 4, 13, 11), ("d", "E", 5, 5, 10, 10), ("m", "E", 6, 6, 9, 9), ("w", "p", 7, 7), ("a", "l", 1, 13, 14, 13)])
make("bd_warning", *BO, [("m", "e", 2, 4, 13, 11), ("a", "l", 7, 1, 7, 8), ("a", "p", 7, 10), ("w", "p", 7, 4)])
make("bd_symbiosis", *BO, [("a", "E", 1, 5, 7, 10), ("w", "E", 8, 5, 14, 10), ("d", "p", 4, 7), ("m", "p", 11, 7), ("w", "l", 7, 7, 8, 7)])
make("bd_afterglow", *BO, [("m", "e", 3, 3, 12, 12), ("a", "a", 1, 1, 14, 14, 200, 340), ("w", "p", 7, 7)])
make("bd_last_stand", *BO, [("w", "E", 2, 5, 6, 9), ("w", "E", 9, 5, 13, 9), ("a", "r", 2, 11, 13, 12), ("m", "l", 7, 2, 7, 13)])
make("bd_parry", *BO, [("w", "l", 2, 12, 12, 2), ("a", "l", 3, 2, 13, 12), ("m", "E", 6, 6, 9, 9)])
print("mode icons written")
