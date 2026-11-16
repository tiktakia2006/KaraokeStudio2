import re, pathlib
ROOT = pathlib.Path(__file__).resolve().parents[2]
files = sorted((ROOT/"app/src").rglob("*.kt"))

def strip(t):
    t = re.sub(r'/\*.*?\*/', lambda m: ' ' * len(m.group()), t, flags=re.S)
    t = re.sub(r'//[^\n]*', lambda m: ' ' * len(m.group()), t)
    t = re.sub(r'"""(.*?)"""', lambda m: '""' + ' ' * (len(m.group()) - 2), t, flags=re.S)
    t = re.sub(r'"(?:\\.|[^"\\\n])*"', lambda m: '""' + ' ' * (len(m.group()) - 2), t)
    t = re.sub(r"'(?:\\.|[^'\\\n])'", "'x'", t)
    return t

def match_paren(s, i):
    depth = 0
    for j in range(i, len(s)):
        c = s[j]
        if c in '([{': depth += 1
        elif c in ')]}':
            depth -= 1
            if depth == 0: return j
    return -1

def split_top(s):
    parts, depth, cur = [], 0, ''
    for c in s:
        if c in '([{': depth += 1
        if c in ')]}': depth -= 1
        if c == ',' and depth == 0:
            parts.append(cur); cur = ''
        else:
            cur += c
    if cur.strip(): parts.append(cur)
    return parts

def has_default(p):
    depth = 0
    for k, c in enumerate(p):
        if c in '([{': depth += 1
        if c in ')]}': depth -= 1
        if c == '=' and depth == 0 and p[k-1] not in '=!<>' and (k+1 >= len(p) or p[k+1] != '='): return True
    return False

decl = {}
for f in files:
    code = strip(f.read_text())
    for m in re.finditer(r'\b(?:fun\s+(?:<[^>]*>\s*)?(?:[\w<>?,. ]+\.)?(\w+)\s*\(|(?:data\s+|sealed\s+|enum\s+)?class\s+(\w+)\s*(?:<[^>]*>\s*)?(?:private\s+|internal\s+)?(?:constructor\s*)?\()', code):
        name = m.group(1) or m.group(2)
        start = m.end() - 1
        end = match_paren(code, start)
        if end < 0: continue
        params = []
        for raw in split_top(code[start+1:end]):
            raw = raw.strip()
            if not raw: continue
            raw = re.sub(r'@\w+(?:\([^)]*\))?\s*', '', raw)
            nm = re.match(r'(?:(?:private|public|internal|protected|override|vararg|noinline|crossinline|val|var)\s+)*(\w+)\s*:', raw)
            if not nm: continue
            params.append((nm.group(1), has_default(raw), 'vararg' in raw.split(':')[0]))
        decl.setdefault(name, []).append((params, f))

problems, checked = [], 0
for f in files:
    code = strip(f.read_text())
    for name, lst in decl.items():
        if len(lst) != 1 or name in ('get', 'set'): continue
        params = lst[0][0]
        for m in re.finditer(r'(?<![\w.])' + re.escape(name) + r'\s*\(', code):
            before = code[max(0, m.start()-40):m.start()]
            if re.search(r'\b(fun|class)\s+(?:<[^>]*>\s*)?(?:[\w<>?,. ]+\.)?$', before): continue
            start = m.end() - 1
            end = match_paren(code, start)
            if end < 0: continue
            args = [a.strip() for a in split_top(code[start+1:end]) if a.strip()]
            trailing = bool(re.match(r'\s*\{', code[end+1:end+40]))
            if code[max(0, m.start()-4):m.start()].rstrip().endswith(':'): trailing = False
            pnames = [p[0] for p in params]
            named, positional = [], 0
            for a in args:
                nm = re.match(r'(\w+)\s*=(?!=)', a)
                if nm: named.append(nm.group(1))
                else: positional += 1
            checked += 1
            where = f"{f.relative_to(ROOT)}:{code.count(chr(10), 0, m.start())+1}"
            vararg = any(p[2] for p in params)
            for n in named:
                if n not in pnames: problems.append(f"{where}: {name}(...) — немає параметра '{n}' (є: {', '.join(pnames)})")
            total = positional + len(named) + (1 if trailing else 0)
            if total > len(params) and not vararg: problems.append(f"{where}: {name}(...) — забагато аргументів ({total} > {len(params)})")
            provided = set(named)
            for i in range(min(positional, len(params))): provided.add(params[i][0])
            if trailing and params: provided.add(params[-1][0])
            missing = [p[0] for p in params if not p[1] and not p[2] and p[0] not in provided]
            if missing: problems.append(f"{where}: {name}(...) — не передано обов'язкові параметри: {', '.join(missing)}")

print(f"Перевірено викликів: {checked}; оголошень у проєкті: {len(decl)}")
print("ПРОБЛЕМ:", len(problems)); [print("  -", p) for p in sorted(set(problems))]
