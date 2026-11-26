"""The G wheel's icons for the Grimoire of Ill Omen's tree (original designs, blue on black like its pages):
  omen_signs   Read the Signs - an open eye over three marks
  omen_cross   Cross My Path  - a line of black prints
  omen_penny   Bad Penny      - a coin, cracked, an eye in it
  omen_notice  Final Notice   - a page with a seal stamped on it
python3 tools/omen_tree_icons.py"""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from icons import make

IO = ((30, 36, 70), (4, 4, 12), (110, 150, 255), (220, 230, 255))   # the Omen pages' blue on black


def icons():
    make('omen_signs', *IO, [('m', 'e', 2, 4, 13, 10), ('d', 'E', 5, 5, 10, 9), ('a', 'p', 7, 7), ('w', 'p', 8, 6),
                             ('a', 'l', 3, 13, 4, 12), ('a', 'l', 7, 13, 8, 12), ('a', 'l', 11, 13, 12, 12)])
    foot = lambda x, y: [('m', 'E', x, y, x + 1, y + 2), ('m', 'p', x, y - 1)]
    make('omen_cross', *IO, foot(2, 11) + foot(5, 9) + foot(8, 7) + foot(11, 5) + [('a', 'l', 1, 14, 14, 3)])
    make('omen_penny', *IO, [('m', 'E', 3, 3, 12, 12), ('d', 'E', 5, 5, 10, 10), ('a', 'p', 7, 7), ('w', 'p', 6, 6),
                             ('d', 'l', 4, 4, 7, 8), ('d', 'l', 7, 8, 6, 12)])
    make('omen_notice', *IO, [('m', 'r', 3, 2, 12, 13), ('d', 'l', 5, 4, 10, 4), ('d', 'l', 5, 6, 10, 6), ('a', 'E', 7, 8, 12, 13), ('w', 'p', 9, 10)])


if __name__ == '__main__':
    icons()
    print('omen tree icons written')
