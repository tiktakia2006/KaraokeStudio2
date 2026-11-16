import re, pathlib
ROOT = pathlib.Path(__file__).resolve().parents[2]
files = sorted((ROOT/"app/src").rglob("*.kt"))
texts = {f: f.read_text() for f in files}

def strip(t):
    t = re.sub(r'/\*.*?\*/', '', t, flags=re.S)
    t = re.sub(r'//[^\n]*', '', t)
    t = re.sub(r'"""(.*?)"""', '""', t, flags=re.S)
    t = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', t)
    return t

known = {}
for t in texts.values():
    for imp in re.findall(r'^import\s+([\w.]+)\s*$', t, re.M):
        known.setdefault(imp.rsplit('.', 1)[1], set()).add(imp)

declared_here = {}
for f, t in texts.items():
    declared_here[f] = set(re.findall(r'\b(?:class|object|interface|fun|val|var|typealias)\s+(?:<[^>]+>\s+)?(?:[\w<>?.]+\.)?(\w+)', t))
declared_all = set().union(*declared_here.values())

problems = []
for f, t in texts.items():
    code = strip(t)
    imports = set(re.findall(r'^import\s+([\w.*]+)', t, re.M))
    simple = {i.rsplit('.', 1)[1] for i in imports}
    wild = {i[:-2] for i in imports if i.endswith('.*')}
    pkg = re.search(r'^package\s+([\w.]+)', t, re.M).group(1)
    for name in set(re.findall(r'\b([A-Za-z_]\w*)\b', code)):
        if name not in known or len(known[name]) != 1: continue
        full = next(iter(known[name]))
        if full.startswith('com.example.karaokestudio'): continue
        if name in simple or full.rsplit('.', 1)[0] in wild: continue
        if name in declared_here[f]: continue
        if name == 'catch':
            if not re.search(r'\.catch\s*\{', code): continue
            if re.search(r'\.catch\s*\{', code) and name not in simple:
                problems.append(f"{f.relative_to(ROOT)}: Flow.catch без імпорту")
            continue
        is_type = name[0].isupper()
        used_as_call = re.search(r'(?<![\w.])' + re.escape(name) + r'\s*[(<{]', code) or re.search(r'\.' + re.escape(name) + r'\b', code) and not is_type
        used_as_type = is_type and re.search(r'(?<![\w.])' + re.escape(name) + r'\b', code)
        if (is_type and used_as_type) or (not is_type and re.search(r'(?<![\w.])' + re.escape(name) + r'\s*\(', code)):
            problems.append(f"{f.relative_to(ROOT)}: '{name}' використано без імпорту ({full})")
    needs_get = re.search(r'\b(?:val|var)\s+\w+\s+by\s+(?:remember|rememberSaveable|mutableStateOf|\w+(?:\.\w+)*\.collectAs\w+)', code)
    needs_set = re.search(r'\bvar\s+\w+\s+by\s+(?:remember|rememberSaveable|mutableStateOf)', code)
    if needs_get and 'androidx.compose.runtime.getValue' not in imports and 'androidx.compose.runtime.*' not in imports:
        problems.append(f"{f.relative_to(ROOT)}: делегат 'by' без import androidx.compose.runtime.getValue")
    if needs_set and 'androidx.compose.runtime.setValue' not in imports and 'androidx.compose.runtime.*' not in imports:
        problems.append(f"{f.relative_to(ROOT)}: 'var ... by' без import androidx.compose.runtime.setValue")

print("Файлів:", len(files), "| відомих символів:", len(known))
print("ПРОБЛЕМ:", len(problems)); [print("  -", p) for p in sorted(set(problems))]
