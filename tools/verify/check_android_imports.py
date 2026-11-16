import pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src"

def strip(text):
    out, i, n = [], 0, len(text)
    while i < n:
        if text.startswith("//", i):
            while i < n and text[i] != "\n": i += 1
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2); i = n if j < 0 else j + 2
        elif text.startswith('"""', i):
            j = text.find('"""', i + 3); i = n if j < 0 else j + 3
            out.append('""')
        elif text[i] == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            out.append('""'); i = j + 1
        elif text[i] == "'" and i + 2 < n and (text[i + 2] == "'" or text[i + 1] == "\\"):
            j = text.find("'", i + 2 if text[i + 1] != "\\" else i + 3); out.append("''"); i = j + 1
        else:
            out.append(text[i]); i += 1
    return "".join(out)

files = sorted(SRC.rglob("*.kt"))
texts = {f: f.read_text() for f in files}
known = {}
for f, t in texts.items():
    for m in re.finditer(r"^import\s+([\w.]+)$", t, re.M):
        full = m.group(1)
        name = full.rsplit(".", 1)[1]
        if full.startswith("com.example.karaokestudio"):
            continue
        known.setdefault(name, set()).add(full)

declared = set()
for t in texts.values():
    for m in re.finditer(r"^(?:private |internal |public )?(?:data |sealed |enum |abstract |open |value )?(?:class|object|interface|fun|val|var|typealias)\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)", t, re.M):
        declared.add(m.group(1))

LOWER_CALL = re.compile(r"[A-Za-z_]\w*")
problems = []
for f, t in texts.items():
    imports = set(re.findall(r"^import\s+[\w.]+\.(\w+)$", t, re.M))
    wildcard = set(re.findall(r"^import\s+([\w.]+)\.\*$", t, re.M))
    body = strip(re.sub(r"^(import|package) .*$", "", t, flags=re.M))
    local_decl = set(re.findall(r"\b(?:class|object|interface|fun|val|var)\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)", body))
    params = set(re.findall(r"(\w+)\s*:", body))
    for name, fulls in known.items():
        if name in imports or name in local_decl or name in params:
            continue
        if any(name == w.split(".")[-1] for w in wildcard):
            continue
        if name[0].isupper():
            pattern = r"(?<![\w.])" + re.escape(name) + r"(?=\s*[(<.{:)\],]|\s*$|\s+[a-z])"
            hit = re.search(pattern, body)
            if hit and name not in {"Unit", "String", "Int", "Long", "Boolean", "Float", "Double", "Any"}:
                if name in ("R",) : continue
                problems.append((f.relative_to(ROOT), name, sorted(fulls)[0]))
        else:
            pattern = r"(?<![\w])\.?" + re.escape(name) + r"\s*\("
            if name in ("dp", "sp"):
                pattern = r"\d\.?" + name + r"\b|\)\." + name + r"\b|\w\." + name + r"\b"
            m = re.search(pattern, body)
            if m and (name in ("dp", "sp") or m.group(0).startswith(".") or True):
                if name in ("dp", "sp") or m.group(0).lstrip().startswith("."):
                    problems.append((f.relative_to(ROOT), name, sorted(fulls)[0]))
for p in sorted(set(problems)):
    print(f"{p[0]}: не імпортовано {p[1]} (звичайно {p[2]})")
print("ПРОБЛЕМ:", len(set(problems)))
sys.exit(1 if problems else 0)
