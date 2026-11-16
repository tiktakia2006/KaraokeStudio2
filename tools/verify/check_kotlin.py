import re, sys, pathlib, subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src"
errors, warns = [], []

for p in list(SRC.rglob("*.xml")):
    r = subprocess.run(["xmllint", "--noout", str(p)], capture_output=True, text=True)
    if r.returncode != 0:
        errors.append(f"XML: {p}: {r.stderr.strip()}")

def check_balance(text, name):
    stack, modes, tdepth = [], ['code'], []
    i, n = 0, len(text)
    pairs = {')': '(', ']': '[', '}': '{'}
    while i < n:
        mode = modes[-1]; c = text[i]
        if mode in ('code', 'tmpl'):
            if text.startswith('//', i):
                j = text.find('\n', i); i = n if j < 0 else j; continue
            if text.startswith('/*', i):
                j = text.find('*/', i + 2)
                if j < 0: return f"{name}: незакритий /*"
                i = j + 2; continue
            if text.startswith('"""', i):
                modes.append('raw'); i += 3; continue
            if c == '"':
                modes.append('str'); i += 1; continue
            if c == "'":
                if text[i+1] == '\\':
                    j = text.find("'", i + 2); i = j + 1
                else:
                    i += 3
                continue
            if c in '([{':
                stack.append((c, i))
                if mode == 'tmpl' and c == '{': tdepth[-1] += 1
            elif c in ')]}':
                if mode == 'tmpl' and c == '}' and tdepth[-1] == 0:
                    modes.pop(); tdepth.pop(); i += 1; continue
                if not stack or stack[-1][0] != pairs[c]:
                    line = text.count('\n', 0, i) + 1
                    return f"{name}: зайва/невідповідна '{c}' у рядку {line}"
                stack.pop()
                if mode == 'tmpl' and c == '}': tdepth[-1] -= 1
        elif mode == 'str':
            if c == '\\': i += 2; continue
            if c == '"': modes.pop()
            elif text.startswith('${', i):
                modes.append('tmpl'); tdepth.append(0); i += 2; continue
            elif c == '\n':
                line = text.count('\n', 0, i) + 1
                return f"{name}: незакритий рядок біля рядка {line}"
        elif mode == 'raw':
            if text.startswith('"""', i):
                modes.pop(); i += 3
                while i < n and text[i] == '"': i += 1
                continue
            if text.startswith('${', i):
                modes.append('tmpl'); tdepth.append(0); i += 2; continue
        i += 1
    if stack:
        line = text.count('\n', 0, stack[-1][1]) + 1
        return f"{name}: незакрита '{stack[-1][0]}' з рядка {line}"
    if len(modes) != 1: return f"{name}: незакритий рядок/шаблон"
    return None

kt_files = sorted(SRC.rglob("*.kt"))
decl_re = re.compile(
    r'^(?:@\w+(?:\([^)]*\))?\s+)*(?:public\s+|internal\s+)?(?:(?:data|sealed|enum|abstract|open|annotation|fun|const|inline)\s+)*'
    r'(class|object|interface|fun|val|var|typealias)\s+(?:<[^>]+>\s+)?(?:[\w<>?.]+\.)?(\w+)', re.M)

pkg_of, declared, texts = {}, {}, {}
for f in kt_files:
    t = f.read_text(); texts[f] = t
    e = check_balance(t, f.relative_to(ROOT))
    if e: errors.append("Баланс: " + e)
    m = re.search(r'^package\s+([\w.]+)', t, re.M)
    if not m: errors.append(f"Немає package: {f}"); continue
    pkg = m.group(1); pkg_of[f] = pkg
    rel = f.relative_to(SRC).parent.parts
    expected = ".".join(rel[2:]) if len(rel) > 2 and rel[1] == "java" else None
    exp_pkg = ".".join(rel[2:]) if rel[0] in ("main","test","androidTest") else None
    if exp_pkg and pkg != exp_pkg:
        errors.append(f"package {pkg} не збігається з каталогом {exp_pkg}: {f.name}")
    for line in t.splitlines():
        if line and not line[0].isspace():
            mm = decl_re.match(line)
            if mm and not re.match(r'^(?:private)\s', line) and 'private ' not in line.split(mm.group(1))[0]:
                declared.setdefault(mm.group(2), set()).add(pkg)

for f, t in texts.items():
    for imp in re.findall(r'^import\s+(com\.example\.karaokestudio\.[\w.]+)', t, re.M):
        pkg, _, name = imp.rpartition('.')
        if imp == 'com.example.karaokestudio.BuildConfig': continue
        if name not in declared or pkg not in declared[name]:
            outer_pkg, _, outer = pkg.rpartition('.')
            if outer in declared and outer_pkg in declared[outer]: continue
            errors.append(f"Імпорт не знайдено: {imp} у {f.name}")

def strip(t):
    t = re.sub(r'/\*.*?\*/', '', t, flags=re.S)
    t = re.sub(r'//[^\n]*', '', t)
    t = re.sub(r'"""(.*?)"""', '""', t, flags=re.S)
    t = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', t)
    return t
for f, t in texts.items():
    code = strip(t); pkg = pkg_of[f]
    imports = set(re.findall(r'^import\s+([\w.]+(?:\.\*)?)', t, re.M))
    used = set(re.findall(r'\b([A-Za-z_]\w*)\b', code))
    for name in used:
        pk = declared.get(name)
        if not pk or pkg in pk: continue
        ok = any(i.endswith('.' + name) or i == f"{p}.*" for i in imports for p in pk)
        if not ok:
            if name[0].islower() and not re.search(r'\b' + name + r'\s*\(', code): continue
            warns.append(f"{f.name}: '{name}' з пакетів {sorted(pk)} без імпорту?")

print("Файлів Kotlin:", len(kt_files))
print("ПОМИЛКИ:", len(errors)); [print("  ", e) for e in errors]
print("ПОПЕРЕДЖЕННЯ:", len(warns)); [print("  ", w) for w in sorted(set(warns))]
