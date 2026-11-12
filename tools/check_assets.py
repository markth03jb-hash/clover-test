"""The mod's rule and asset checker - run before every build (the Build jar workflow runs it first, and a failure stops
the build). It reads the source and resources only; nothing is changed.

  python3 tools/check_assets.py            all checks; exit code 1 if any ERROR
  python3 tools/check_assets.py --warn     also list the WARNINGs in full

Checks
  Rules (the project's standing rules)
    - no vanilla particles (ParticleTypes / addParticle / sendParticles)
    - no status effects given (new MobEffectInstance / addEffect / MobEffects.) - use attribute modifiers
    - spells and effects don't place or break blocks (setBlock / destroyBlock / removeBlock outside the
      allowed world-building files: rifts, raids' spoils, the village boards)
  Assets
    - every texture / model / geo / animation path named in the code exists
    - every sound in sounds.json has its .ogg; every sound the code registers is in sounds.json and has a subtitle
    - every item, block and entity has its lang name; every item has a model; every block a blockstate
    - every Component.translatable key used in the code is in en_us.json
    - every texture an item/block model points at exists
    - GeckoLib: every animation the code plays exists in its animation file, and every bone an animation moves
      is in the model; bones the renderers look up by name exist
    - every .ogg is used by sounds.json (WARNING), every lang key is valid JSON (the file parses)
"""
import json, os, re, sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
JAVA = os.path.join(ROOT, 'src/main/java')
RES = os.path.join(ROOT, 'src/main/resources/assets/iron_spell_grimoire')
NS = 'iron_spell_grimoire'

# world-building that is allowed to change blocks (not spells): the rift dungeons, a raid's spoils chest, the boards
WORLD_OK = ('server/Rifts.java', 'server/ElfRaids.java', 'server/VillageBoards.java', 'block/', 'server/GameTests.java')   # the tests put back what a spell changed

errors, warnings = [], []
def err(where, msg): errors.append('%s: %s' % (where, msg))
def warn(where, msg): warnings.append('%s: %s' % (where, msg))

sources = {}
for d, _, fs in os.walk(JAVA):
    for f in fs:
        if f.endswith('.java'):
            p = os.path.join(d, f)
            rel = os.path.relpath(p, os.path.join(JAVA, 'com/ironspellgrimoire'))
            sources[rel] = open(p, encoding='utf-8').read()

def strip_comments(s):
    s = re.sub(r'/\*.*?\*/', lambda m: '\n' * m.group(0).count('\n'), s, flags=re.S)
    return re.sub(r'//[^\n]*', '', s)
code = {k: strip_comments(v) for k, v in sources.items()}

def line_of(src, idx): return src.count('\n', 0, idx) + 1

# ------------------------------------------------------------------ rules
RULES = [
    (r'\bParticleTypes\.|\.addParticle\(|\.sendParticles\(', 'vanilla particles (use the mod\'s own FxSend effects)', None),
    (r'new\s+MobEffectInstance\(|\.addEffect\(|\bMobEffects\.', 'a status effect (use TimedModifiers attribute modifiers)', None),
    (r'\.(setBlock|setBlockAndUpdate|destroyBlock|removeBlock)\(', 'changes blocks (spells and effects must not change the world)', WORLD_OK),
]
for rel, s in code.items():
    if rel.startswith('mixin/'): continue
    for pat, what, ok in RULES:
        if ok and rel.startswith(ok): continue
        for m in re.finditer(pat, s):
            err('%s:%d' % (rel, line_of(s, m.start())), 'rule: ' + what)

# ------------------------------------------------------------------ resource paths named in code
def exists(path): return os.path.exists(os.path.join(RES, path))
PATH_RE = re.compile(r'(?:new ResourceLocation|ResourceLocation\.fromNamespaceAndPath)\(\s*"%s"\s*,\s*"([^"]+)"\s*\)|"%s:([^"]+)"' % (NS, NS))
for rel, s in code.items():
    for m in PATH_RE.finditer(s):
        p = m.group(1) or m.group(2)
        if '/' not in p or not re.search(r'\.(png|json|ogg)$', p): continue   # registry ids, not files
        if not exists(p): err('%s:%d' % (rel, line_of(s, m.start())), 'missing file assets/%s/%s' % (NS, p))
    for m in re.finditer(r'"(textures/[a-z0-9_/]+)"\s*\+', s):   # a path built at runtime: at least the folder must exist
        folder = m.group(1).rsplit('/', 1)[0] if not m.group(1).endswith('/') else m.group(1).rstrip('/')
        if not os.path.isdir(os.path.join(RES, folder)): err('%s:%d' % (rel, line_of(s, m.start())), 'missing folder assets/%s/%s' % (NS, folder))

# ------------------------------------------------------------------ lang
try:
    lang = json.load(open(os.path.join(RES, 'lang/en_us.json'), encoding='utf-8'))
except Exception as e:
    lang = {}; err('lang/en_us.json', 'does not parse: %s' % e)

for rel, s in code.items():
    for m in re.finditer(r'Component\.translatable\(\s*"([^"]+)"', s):
        k = m.group(1)
        if k.endswith('.'):   # a prefix with the rest added at runtime: some key must start with it
            if not any(x.startswith(k) for x in lang): err('%s:%d' % (rel, line_of(s, m.start())), 'no lang key starts with "%s"' % k)
            continue
        if (k.startswith(('item.%s.' % NS, 'block.%s.' % NS, 'entity.%s.' % NS, 'key.', 'subtitles.%s' % NS, 'spell.%s' % NS, NS))
                or k.split('.')[1:2] == [NS]) and k not in lang:
            err('%s:%d' % (rel, line_of(s, m.start())), 'lang key "%s" missing from en_us.json' % k)
    for m in re.finditer(r'new KeyMapping\(\s*"([^"]+)"[^;]*?"(key\.categories\.[^"]+)"', s, flags=re.S):
        for k in m.groups():
            if k not in lang: err(rel, 'key binding name "%s" missing from en_us.json' % k)

# ------------------------------------------------------------------ registries
reg = code.get('registry/AddonItemRegistry.java', '')
items = re.findall(r'ITEMS\.register\(\s*"([a-z0-9_]+)"', reg)
blocks = []
for rel, s in code.items():
    if rel.startswith('registry/'):
        blocks += re.findall(r'BLOCKS\.register\(\s*"([a-z0-9_]+)"', s)
        if rel != 'registry/AddonItemRegistry.java': items += re.findall(r'ITEMS\.register\(\s*"([a-z0-9_]+)"', s)
entities = re.findall(r'ENTITIES\.register\(\s*"([a-z0-9_]+)"', code.get('registry/AddonEntities.java', ''))
for i in sorted(set(items)):
    if 'item.%s.%s' % (NS, i) not in lang and 'block.%s.%s' % (NS, i) not in lang: err('item ' + i, 'no lang name (item.%s.%s)' % (NS, i))
    if not exists('models/item/%s.json' % i): err('item ' + i, 'no item model (models/item/%s.json)' % i)
for b in sorted(set(blocks)):
    if 'block.%s.%s' % (NS, b) not in lang: warn('block ' + b, 'no lang name')
    if not exists('blockstates/%s.json' % b): err('block ' + b, 'no blockstate (blockstates/%s.json)' % b)
for e in sorted(set(entities)):
    if 'entity.%s.%s' % (NS, e) not in lang: err('entity ' + e, 'no lang name (entity.%s.%s)' % (NS, e))

# ------------------------------------------------------------------ models -> textures, blockstates -> models
def refs(path):
    out = []
    j = json.load(open(path, encoding='utf-8'))
    def walk(o):
        if isinstance(o, dict):
            for k, v in o.items():
                if k in ('model', 'parent') and isinstance(v, str): out.append(('models', v))
                elif k == 'textures' and isinstance(v, dict): out.extend(('textures', t) for t in v.values() if isinstance(t, str))
                else: walk(v)
        elif isinstance(o, list):
            for v in o: walk(v)
    walk(j); return out
for sub in ('models', 'blockstates'):
    for d, _, fs in os.walk(os.path.join(RES, sub)):
        for f in fs:
            if not f.endswith('.json'): continue
            p = os.path.join(d, f); rel = os.path.relpath(p, RES)
            try: rs = refs(p)
            except Exception as e: err(rel, 'does not parse: %s' % e); continue
            for kind, v in rs:
                if v.startswith('#') or (':' in v and not v.startswith(NS + ':')): continue
                v = v.split(':', 1)[1] if ':' in v else v
                if ':' not in v and kind == 'models' and not v.startswith(('item/', 'block/')): continue
                path = '%s/%s.%s' % (kind, v, 'png' if kind == 'textures' else 'json')
                if not exists(path):
                    # vanilla parents like "item/generated" or "block/cube_all" live in minecraft's namespace
                    if kind == 'models' and v in ('item/generated', 'item/handheld', 'item/template_spawn_egg', 'block/cube_all', 'block/block'): continue
                    if kind == 'models' and not os.path.exists(os.path.join(RES, 'models')): continue
                    (err if kind == 'textures' else warn)(rel, 'points at missing %s' % path)

# ------------------------------------------------------------------ sounds
try:
    sj = json.load(open(os.path.join(RES, 'sounds.json'), encoding='utf-8'))
except Exception as e:
    sj = {}; err('sounds.json', 'does not parse: %s' % e)
used_ogg = set()
for ev, d in sj.items():
    for snd in d.get('sounds', []):
        n = snd['name'] if isinstance(snd, dict) else snd
        if isinstance(snd, dict) and snd.get('type') == 'event': continue
        ns, _, path = n.partition(':') if ':' in n else (NS, ':', n)
        if ns != NS: continue
        used_ogg.add(path)
        if not exists('sounds/%s.ogg' % path): err('sounds.json "%s"' % ev, 'missing sounds/%s.ogg' % path)
    sub = d.get('subtitle')
    if sub and sub not in lang: warn('sounds.json "%s"' % ev, 'subtitle key %s not in en_us.json' % sub)
snd = code.get('registry/AddonSounds.java', '')
registered = set(re.findall(r'register\(\s*"([a-z0-9_.]+)"', snd))
m = re.search(r'new String\[\]\{([^}]*)\}', snd)
if m: registered |= {'sfx.' + k for k in re.findall(r'"([a-z0-9_]+)"', m.group(1))}
for r in sorted(registered):
    if r not in sj: err('AddonSounds', 'sound "%s" registered but not in sounds.json' % r)
for d, _, fs in os.walk(os.path.join(RES, 'sounds')):
    for f in fs:
        if f.endswith('.ogg'):
            p = os.path.relpath(os.path.join(d, f), os.path.join(RES, 'sounds'))[:-4].replace(os.sep, '/')
            if p not in used_ogg: warn('sounds/%s.ogg' % p, 'not used by sounds.json')

# ------------------------------------------------------------------ GeckoLib: animations, bones
geo_bones, anim_names, anim_bones = {}, {}, {}
for f in os.listdir(os.path.join(RES, 'geo')) if os.path.isdir(os.path.join(RES, 'geo')) else []:
    g = json.load(open(os.path.join(RES, 'geo', f)))
    geo_bones[f.split('.')[0]] = {b['name'] for b in g['minecraft:geometry'][0]['bones']}
for f in os.listdir(os.path.join(RES, 'animations')) if os.path.isdir(os.path.join(RES, 'animations')) else []:
    if not f.endswith('.animation.json'): continue
    a = json.load(open(os.path.join(RES, 'animations', f)))
    name = f.split('.')[0]
    anim_names[name] = set(a['animations'])
    for an, body in a['animations'].items():
        for bn in body.get('bones', {}):
            anim_bones.setdefault(name, set()).add((an, bn))
for name, bones in anim_bones.items():
    if name not in geo_bones: continue
    for an, bn in sorted(bones):
        if bn not in geo_bones[name]: err('animations/%s' % name, '%s moves bone "%s", which the model doesn\'t have' % (an, bn))
for rel, s in code.items():
    m = re.search(r'"animation\.([a-z0-9_]+)\."', s)
    if not m: continue
    name = m.group(1)
    if name not in anim_names: err(rel, 'plays animations of "%s" but animations/%s.animation.json is missing' % (name, name)); continue
    used = set(re.findall(r'\bA\s*\+\s*"([a-z0-9_]+)"', s))
    for arr in re.findall(r'(?:MOVES?|ANIMS)\s*=\s*\{([^}]*)\}', s):
        used |= set(re.findall(r'"([a-z0-9_]+)"', arr))
    used |= set(re.findall(r'(?:play|triggerAnim\("main",)\s*\(?\s*"([a-z0-9_]+)"', s))
    for u in sorted(used):
        if 'animation.%s.%s' % (name, u) not in anim_names[name]:
            err(rel, 'animation "%s" not in animations/%s.animation.json' % (u, name))
all_anims = {n.rsplit('.', 1)[1] for ns in anim_names.values() for n in ns}
for rel, s in code.items():   # a boss's fight code playing its moves by name
    for m in re.finditer(r'\.play\(\s*"([a-z0-9_]+)"\s*\)', s):
        if m.group(1) not in all_anims: err('%s:%d' % (rel, line_of(s, m.start())), 'plays animation "%s", which no animation file has' % m.group(1))
for rel, s in code.items():
    m = re.search(r'"geo/([a-z0-9_]+)\.geo\.json"', s)
    if not m or m.group(1) not in geo_bones: continue
    for b in set(re.findall(r'getBone\(\s*"([a-z0-9_]+)"', s)):
        if b not in geo_bones[m.group(1)]: err(rel, 'looks up bone "%s", which geo/%s.geo.json doesn\'t have' % (b, m.group(1)))

# ------------------------------------------------------------------ report
full = '--warn' in sys.argv
for e in errors: print('ERROR   ' + e)
if full:
    for w in warnings: print('WARNING ' + w)
print('\n%d error(s), %d warning(s)%s' % (len(errors), len(warnings), '' if full or not warnings else '  (--warn to list the warnings)'))
sys.exit(1 if errors else 0)
