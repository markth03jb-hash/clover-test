"""The balance survey: reads the BAL lines a full game-test run writes (one per grimoire page: what it dealt to the
test dummy, its mana cost, its cooldown, its school) and works out, per page and per book, damage per second of
cooldown and damage per 10 mana, and flags the outliers - pages and books far above or below the rest of their kind.
Ultimates (cooldown 60 s or more) are judged against ultimates. Pages that dealt no damage (buffs, shields,
conditional hits) and books with none measured are left out of the ranking.

  python3 tools/balance.py REPORT [out.json]

Prints a summary; with out.json, writes everything the balance page needs."""
import json, statistics, sys


# Pages whose page-level cost isn't the real one: the real (mana, cooldown ticks), or None when the page's damage isn't
# its own (Twin Arts casts the chosen art, each with its own cost; Last Coal is a shield, and what the test saw was the
# Hearth Star still burning).
REAL_COST = {'siege_mode': (120, 3600)}
NOT_OWN_DAMAGE = {'twin_arts', 'last_coal'}


def load(path):
    rows = []
    for line in open(path, encoding='utf-8'):
        if not line.startswith('BAL |'): continue
        _, sid, book, dmg, mana, cd, school = [x.strip() for x in line.split('|')]
        dmg, mana, cd = float(dmg), int(mana), int(cd)
        if sid in REAL_COST: mana, cd = REAL_COST[sid]
        if sid in NOT_OWN_DAMAGE: dmg = 0.0
        secs = max(cd / 20.0, 1.0)
        rows.append({'spell': sid, 'book': book.replace('_grimoire', '').replace('_', ' '), 'school': school, 'damage': round(dmg, 1),
                     'mana': mana, 'cooldown': round(cd / 20.0, 1), 'dps': round(dmg / secs, 2), 'per10mana': round(dmg * 10 / mana, 2) if mana else 0.0,
                     'ultimate': cd >= 1200})
    return rows


def flag(rows):
    for tier in (False, True):
        hit = [r for r in rows if r['ultimate'] == tier and r['damage'] > 0]
        if len(hit) < 4: continue
        med = statistics.median(r['dps'] for r in hit)
        for r in hit:
            r['vs_median'] = round(r['dps'] / med, 2) if med else 0
            r['flag'] = 'high' if r['dps'] > med * 3 else 'low' if r['dps'] < med / 3 else ''
    for r in rows: r.setdefault('vs_median', 0); r.setdefault('flag', '')


def books(rows):
    out = {}
    for r in rows:
        b = out.setdefault(r['book'], {'book': r['book'], 'pages': 0, 'damaging': 0, 'total_damage': 0.0, 'dps_sum': 0.0, 'best': ('', 0.0)})
        b['pages'] += 1
        if r['damage'] > 0:
            b['damaging'] += 1; b['total_damage'] += r['damage']; b['dps_sum'] += r['dps']
            if r['dps'] > b['best'][1]: b['best'] = (r['spell'], r['dps'])
    lst = list(out.values())
    med = statistics.median(b['dps_sum'] for b in lst if b['dps_sum'] > 0) if any(b['dps_sum'] > 0 for b in lst) else 1
    for b in lst:
        b['total_damage'] = round(b['total_damage'], 1); b['dps_sum'] = round(b['dps_sum'], 2)
        b['vs_median'] = round(b['dps_sum'] / med, 2) if med else 0
        b['flag'] = 'high' if b['dps_sum'] > med * 2 else 'low' if b['dps_sum'] < med / 2 else ''
        b['best'] = b['best'][0]
    ranked = sorted([b for b in lst if b['dps_sum'] > 0], key=lambda b: -b['dps_sum'])
    unmeasured = sorted(b['book'] for b in lst if b['dps_sum'] == 0)
    return ranked, unmeasured


if __name__ == '__main__':
    rows = load(sys.argv[1])
    flag(rows)
    bk, unmeasured = books(rows)
    print(len(rows), 'pages,', len(bk), 'ranked books;', sum(1 for r in rows if r['damage'] <= 0), 'pages dealt no damage and are left out of the ranking')
    if unmeasured: print('books with no measured damage (not ranked):', ', '.join(unmeasured))
    print('books, strongest first (sum of damage per second of cooldown):')
    for b in bk: print('  %-14s %6.2f  x%.2f %s' % (b['book'], b['dps_sum'], b['vs_median'], b['flag']))
    print('outlier pages:')
    for r in sorted(rows, key=lambda r: -r['vs_median']):
        if r['flag']: print('  %-24s %-12s dmg %6.1f cd %5.1fs  %5.2f/s  x%.2f %s%s' % (r['spell'], r['book'], r['damage'], r['cooldown'], r['dps'], r['vs_median'], r['flag'], ' (ultimate)' if r['ultimate'] else ''))
    if len(sys.argv) > 2: json.dump({'pages': rows, 'books': bk, 'unmeasured': unmeasured}, open(sys.argv[2], 'w'), indent=1)
