import re, pathlib
ROOT = pathlib.Path(__file__).resolve().parents[2]
code = {f: f.read_text() for f in (ROOT/"app/src").rglob("*.kt")}
all_code = "\n".join(code.values())
problems = []

for name in ("05-class-data-layer", "06-class-karaoke-logic", "10-class-network-sync"):
    text = (ROOT/"docs/diagrams"/f"{name}.mermaid").read_text()
    for m in re.finditer(r"^\s*class (\w+)\s*(\{)?", text, re.M):
        cls = m.group(1)
        if not re.search(rf"\b(class|interface|object)\s+{cls}\b", all_code):
            problems.append(f"{name}: клас {cls} не знайдено в коді")
    for blk in re.finditer(r"class (\w+) \{(.*?)\n    \}", text, re.S):
        cls, body = blk.group(1), blk.group(2)
        for line in body.splitlines():
            line = line.strip()
            if not line or line.startswith("<<"): continue
            if "(" in line:
                meth = re.search(r"([+\-]?)(\w+)\(", line).group(2)
                if not re.search(rf"\bfun\s+(?:\w+\.)?{meth}\b", all_code):
                    problems.append(f"{name}: метод {cls}.{meth}() не знайдено")
            else:
                tokens = line.lstrip("+-").split()
                field = tokens[-1] if tokens else ""
                if field and not re.search(rf"\b{field}\b", all_code):
                    problems.append(f"{name}: поле/значення {cls}.{field} не знайдено")

er = (ROOT/"docs/diagrams/07-er-model.mermaid").read_text()
tables = set(re.findall(r'tableName = "(\w+)"', all_code))
for t in re.findall(r"^    (\w+) \{", er, re.M):
    if t.lower() not in tables: problems.append(f"ER: таблиця {t} не збігається з @Entity")
for col in re.findall(r"^        (?:INTEGER|TEXT) (\w+)", er, re.M):
    if not re.search(rf"\bval {col}\b", all_code): problems.append(f"ER: колонка {col} не знайдена в сутностях")

screen = (ROOT/"app/src/main/java/com/example/karaokestudio/ui/navigation/Screen.kt").read_text()
routes = set(re.findall(r'Screen\("([^"]+)"\)', screen))
routes = {r.replace("{$ARG_SONG_ID}", "{songId}").replace("{$ARG_PERFORMANCE_ID}", "{performanceId}") for r in routes}
doc_routes = set(re.findall(r"\| `([a-z-]+(?:/\{\w+\})?)` \| (?:вкладка|повноекранний)", (ROOT/"docs/02-menu-structure.md").read_text()))
if routes != doc_routes: problems.append(f"Маршрути: код {sorted(routes)} != документ {sorted(doc_routes)}")

prov = (ROOT/"app/src/main/java/com/example/karaokestudio/ui/AppViewModelProvider.kt").read_text()
in_factory = set(re.findall(r"\b(\w+ViewModel)\(", prov))
declared_vm = set(re.findall(r"^class (\w+ViewModel)", all_code, re.M))
used_in_screens = set()
for f, t in code.items():
    if f.name.endswith("Screen.kt"):
        used_in_screens |= set(re.findall(r"viewModel: (\w+ViewModel) = viewModel\(factory = AppViewModelProvider\.Factory\)", t))
if in_factory != declared_vm: problems.append(f"Фабрика {sorted(declared_vm - in_factory)} / зайві {sorted(in_factory - declared_vm)}")
if used_in_screens != declared_vm: problems.append(f"Екрани без VM: {sorted(declared_vm - used_in_screens)}")

demo = (ROOT/"app/src/main/java/com/example/karaokestudio/data/seed/DemoData.kt").read_text()
n_songs = len(re.findall(r"^        Song\(", demo, re.M)); n_perf = len(re.findall(r"DemoPerformance\(\d", demo))
if (n_songs, n_perf) != (6, 8): problems.append(f"Демо-дані: {n_songs} пісень, {n_perf} виконань (в доках 6 і 8)")
presets = len(re.findall(r'DemoPreset\("', all_code)); slugs_in_songs = set(re.findall(r'audioSource = "demo:([\w-]+)"', demo))
slugs_in_gen = set(re.findall(r'DemoPreset\("([\w-]+)"', all_code))
if not slugs_in_songs <= slugs_in_gen: problems.append(f"Доріжки: пісні {slugs_in_songs} vs генератор {slugs_in_gen}")
import json as _json
_seed = _json.loads((ROOT/"server/src/seed/songs.js").read_text().split("=", 1)[1].rstrip().rstrip(";"))
if {x["slug"] for x in _seed} != slugs_in_gen: problems.append("Каталог сервера не збігається з пресетами генератора")
for m in re.finditer(r'durationMs = (\d+)_000.*?audioSource = "demo:([\w-]+)".*?lyrics = """(.*?)""".trimIndent', demo, re.S):
    dur, slug, lyr = int(m.group(1)), m.group(2), m.group(3)
    sec = int(re.search(rf'DemoPreset\("{slug}", bpm = \d+, rootMidi = \d+, minor = \w+, seconds = (\d+)', all_code).group(1))
    last = max(int(a)*60+int(b) for a, b in re.findall(r"\[(\d+):(\d+)\.\d+\]", lyr))
    if dur != sec: problems.append(f"{slug}: тривалість пісні {dur} c != доріжка {sec} c")
    if last >= dur: problems.append(f"{slug}: остання мітка {last} c не менша за тривалість {dur} c")

print("Перевірок виконано: класи/методи/поля діаграм, ER, маршрути, фабрика VM, демо-дані")
print("ПРОБЛЕМ:", len(problems)); [print("  -", p) for p in problems]
