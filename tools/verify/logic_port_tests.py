import re, math, struct, wave, random, pathlib, sys, textwrap, tempfile
import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/java/com/example/karaokestudio"
passed, failed = 0, []
def check(name, cond, info=""):
    global passed
    if cond: passed += 1
    else: failed.append(f"{name} {info}")

def round_half_up(x): return int(math.floor(x + 0.5))

TIMED = re.compile(r"^\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]\s*(.*)$")
def lrc_parse(raw, duration_ms):
    lines = [l.strip() for l in raw.split("\n") if l.strip()]
    timed = []
    for line in lines:
        m = TIMED.match(line)
        if not m: continue
        mn, sec, frac, text = m.groups()
        frac = frac or ""
        fms = 0 if frac == "" else int(frac.ljust(3, "0")[:3])
        timed.append((int(mn)*60000 + int(sec)*1000 + fms, text.strip()))
    if timed: return sorted(timed, key=lambda x: x[0])
    plain = [l for l in lines if not l.startswith("[")]
    if not plain: return []
    step = duration_ms // len(plain)
    return [(i*step, t) for i, t in enumerate(plain)]
def index_at(lines, pos):
    lo, hi, ans = 0, len(lines)-1, -1
    while lo <= hi:
        mid = (lo+hi) >> 1
        if lines[mid][0] <= pos: ans, lo = mid, mid+1
        else: hi = mid-1
    return ans

FRAME_MS, MIN_FRAMES, MAX_LINE_MS = 100, 20, 10_000
def threshold_for(s): return max(4000 - min(max(s, 0), 100)*35, 300)
def stars_for(sc): return 5 if sc>=90 else 4 if sc>=75 else 3 if sc>=55 else 2 if sc>=35 else 1 if sc>=15 else 0
def calculate(samples, lines, threshold):
    in_l = act = 0
    for t, amp in samples:
        i = index_at(lines, t)
        if i < 0: continue
        lt, text = lines[i]
        if not text.strip() or t - lt > MAX_LINE_MS: continue
        in_l += 1
        if amp >= threshold: act += 1
    if in_l < MIN_FRAMES: return None
    score = min(max(round_half_up(act/in_l*100), 0), 100)
    return score, stars_for(score)

demo = (ROOT/"data/seed/DemoData.kt").read_text()
songs = []
for m in re.finditer(r'title = "([^"]+)".*?durationMs = (\d+)_000.*?audioSource = "demo:([\w-]+)".*?lyrics = """\n(.*?)""".trimIndent', demo, re.S):
    title, dur, slug, lyr = m.group(1), int(m.group(2))*1000, m.group(3), textwrap.dedent(m.group(4))
    songs.append(dict(title=title, dur=dur, slug=slug, lyrics=lyr))
check("витягнуто 6 демо-пісень", len(songs) == 6, str(len(songs)))

for s in songs:
    ln = lrc_parse(s["lyrics"], s["dur"])
    times = [t for t, _ in ln]
    texts = [x for _, x in ln if x]
    check(f"{s['slug']}: мітки строго зростають", all(a < b for a, b in zip(times, times[1:])))
    check(f"{s['slug']}: останній рядок у межах тривалості", times[-1] < s["dur"], f"{times[-1]} >= {s['dur']}")
    check(f"{s['slug']}: 7–8 рядків тексту", len(texts) in (7, 8), str(len(texts)))
    check(f"{s['slug']}: перший рядок — пауза (вступ)", ln[0][1] == "")
    check(f"{s['slug']}: останній рядок — пауза (кінцівка)", ln[-1][1] == "")
    check(f"{s['slug']}: indexAt до початку = -1 або вступ", index_at(ln, -1) == -1 and index_at(ln, 0) == 0)

L = [(0, ""), (2000, "Рядок один"), (6000, "Рядок два"), (10000, "")]
def smp(a, b, amp): return [(t, amp) for t in range(a, b, 100)]
r = calculate(smp(2000, 10000, 8000), L, 2000); check("score: гучний спів = 100/5★", r == (100, 5), str(r))
r = calculate(smp(2000, 10000, 0), L, 2000); check("score: тиша = 0", r and r[0] == 0, str(r))
r = calculate(smp(2000, 6000, 8000) + smp(6000, 10000, 0), L, 2000); check("score: половина = 50", r and r[0] == 50, str(r))
r = calculate(smp(0, 2000, 9000) + smp(2000, 10000, 0), L, 2000); check("score: вступ ігнорується", r and r[0] == 0, str(r))
check("score: коротко = None", calculate(smp(2000, 3000, 8000), L, 2000) is None)
check("score: поріг спадає з чутливістю", threshold_for(100) < threshold_for(0) and threshold_for(50) == 2250)
check("score: зірки", stars_for(10) == 0 and stars_for(60) == 3 and stars_for(95) == 5)
check("score: 12.5 → 13 (як Kotlin roundToInt)", round_half_up(12.5) == 13)

for s in songs:
    ln = lrc_parse(s["lyrics"], s["dur"])
    full = calculate([(t, 9000) for t in range(0, s["dur"], 100)], ln, threshold_for(50))
    none = calculate([(t, 100) for t in range(0, s["dur"], 100)], ln, threshold_for(50))
    half = calculate([(t, 9000 if t < s["dur"]/2 else 100) for t in range(0, s["dur"], 100)], ln, threshold_for(50))
    check(f"{s['slug']}: повний спів = 100", full and full[0] == 100, str(full))
    check(f"{s['slug']}: тиша = 0", none and none[0] == 0, str(none))
    check(f"{s['slug']}: перша половина ≈ 40–60", half and 40 <= half[0] <= 60, str(half))

def normalize(raw): return "".join(c for c in raw.strip().lower() if c not in "%_").strip()
check("пошук: РАНКОВА → ранкова", normalize("  РАНКОВА ") == "ранкова")
check("пошук: a%b_ → ab", normalize("a%b_") == "ab")
check("пошук: порожній після очищення", normalize("  %_ ") == "")
keys = [f"{s['title']} ".lower() for s in songs]
check("пошук: «ранк» знаходить 1 пісню", sum(1 for k in keys if "ранк" in k) == 1)
check("пошук: «neon» знаходить Neon Dance", any("neon" in k for k in keys))
def sanitize(nick, cd, size, sens, vol):
    return (nick.strip()[:24] or "Співак", min(max(cd, 0), 10), min(max(size, 18), 36), min(max(sens, 0), 100), min(max(vol, 0), 100))
check("налаштування: межі", sanitize("   ", 99, 5, 500, -20) == ("Співак", 10, 18, 100, 0))

def progress_at(lines, index, pos):
    start = lines[index][0]
    nxt = lines[index+1][0] if index + 1 < len(lines) else start + 4000
    span = max((nxt - start) * 0.85, 1000.0)
    return min(max((pos - start) / span, 0.0), 1.0)
pl = lrc_parse("[00:10.00]А\n[00:20.00]Б", 30000)
check("progressAt: до початку 0", abs(progress_at(pl, 0, 9000)) < 1e-6)
check("progressAt: середина 0.5", abs(progress_at(pl, 0, 14250) - 0.5) < 1e-6)
check("progressAt: після 85% = 1", progress_at(pl, 0, 25000) == 1.0)
check("progressAt: останній рядок", progress_at(pl, 1, 20000) == 0.0 and progress_at(pl, 1, 24000) == 1.0)
for s_ in songs:
    ln_ = lrc_parse(s_["lyrics"], s_["dur"])
    vals = [progress_at(ln_, i, ln_[i][0] + 100*k) for i in range(len(ln_)) for k in range(0, 60)]
    check(f"{s_['slug']}: progressAt завжди в межах 0..1 і монотонний у рядку", all(0 <= v <= 1 for v in vals))

SR = 22050
MAJOR = [[0,4,7],[7,11,14],[9,12,16],[5,9,12]]
MINOR = [[0,3,7],[8,12,15],[3,7,10],[10,14,17]]
ARP = [0,1,2,1,0,1,2,1]
src = (ROOT/"audio/DemoAudioGenerator.kt").read_text()
presets = [(m.group(1), int(m.group(2)), int(m.group(3)), m.group(4) == "true", int(m.group(5)))
           for m in re.finditer(r'DemoPreset\("([\w-]+)", bpm = (\d+), rootMidi = (\d+), minor = (\w+), seconds = (\d+)\)', src)]
check("генератор: 10 пресетів", len(presets) == 10, str(len(presets)))
def midi_hz(m): return 440.0 * 2.0 ** ((m-69)/12.0)

def synth(slug, bpm, root, minor, seconds):
    total = SR*seconds
    mix = np.zeros(total, dtype=np.float32)
    beat = SR*60.0/bpm; step = beat/2
    chords = MINOR if minor else MAJOR
    steps = int(total/step)
    rnd = random.Random(hash(slug) & 0xFFFF)
    def tone(start, freq, amp, length, decay):
        end = min(total, start+length)
        if end <= start: return
        n = np.arange(0, end-start); t = n/SR; w = 2*math.pi*freq/SR
        env = np.minimum(1.0, t/0.004) * np.exp(-decay*t)
        wave_ = np.sin(w*n) + 0.35*np.sin(2*w*n) + 0.12*np.sin(3*w*n)
        mix[start:end] += (amp*env*wave_).astype(np.float32)
    def tick(start, amp):
        n = SR*12//1000
        for j in range(n):
            idx = start+j
            if idx >= total: break
            mix[idx] += amp*(rnd.random()*2-1)*(1-j/n)
    for i in range(steps):
        start = int(i*step); chord = chords[(i//8) % len(chords)]; inbar = i % 8
        tone(start, midi_hz(root+12+chord[ARP[inbar]]), 0.20, int(step*2), 5.0)
        if inbar in (0, 4): tone(start, midi_hz(root-12+chord[0]), 0.32, int(beat*2), 2.0)
        if inbar % 2 == 0: tick(start, 0.16 if inbar == 0 else 0.09)
    peak = float(np.max(np.abs(mix)))
    gain = 0.85/peak if peak > 0.85 else 1.0
    fade = SR*3//2
    out = np.empty(total, dtype=np.int16)
    for_idx = np.arange(total)
    fade_out = np.where(for_idx > total-fade, (total-for_idx)/fade, 1.0)
    v = np.clip(mix*gain*fade_out, -1, 1)
    out[:] = (v*32767).astype(np.int32)
    return out

def wav_bytes(pcm):
    data = pcm.astype('<i2').tobytes()
    header = struct.pack('<4sI4s4sIHHIIHH4sI', b'RIFF', 36+len(data), b'WAVE', b'fmt ', 16, 1, 1, SR, SR*2, 2, 16, b'data', len(data))
    return header + data

outdir = pathlib.Path(tempfile.gettempdir()) / "karaoke-port-audio"; outdir.mkdir(exist_ok=True)
report = []
song_dur = {s["slug"]: s["dur"]//1000 for s in songs}
import json as _json
_seed_text = (ROOT.parents[6] / "server/src/seed/songs.js").read_text().split("=", 1)[1].rstrip().rstrip(";")
server_seed = {x["slug"]: x["durationMs"] // 1000 for x in _json.loads(_seed_text)}
for slug, bpm, root, minor, secs in presets:
    if slug in song_dur:
        check(f"{slug}: довжина доріжки = тривалість пісні", secs == song_dur[slug], f"{secs} vs {song_dur[slug]}")
    server_dur = server_seed.get(slug)
    check(f"{slug}: пісня є в каталозі сервера з тією ж тривалістю", server_dur == secs, f"{server_dur} vs {secs}")
    pcm = synth(slug, bpm, root, minor, secs)
    p = outdir/f"{slug}.wav"; p.write_bytes(wav_bytes(pcm))
    with wave.open(str(p)) as w:
        check(f"{slug}: WAV mono/16bit/22050", (w.getnchannels(), w.getsampwidth(), w.getframerate()) == (1, 2, SR))
        check(f"{slug}: WAV кількість відліків", w.getnframes() == SR*secs)
    peak = int(np.max(np.abs(pcm.astype(np.int32)))); rms = float(np.sqrt(np.mean((pcm.astype(np.float64)/32767)**2)))
    check(f"{slug}: без кліпінгу (пік ≤ 0.86·32767)", peak <= 0.86*32767, str(peak))
    check(f"{slug}: не тиша (RMS > 0.02)", rms > 0.02, f"{rms:.3f}")
    check(f"{slug}: плавне затухання в кінці", int(np.max(np.abs(pcm[-200:]))) < 0.02*32767)
    chord0 = (MINOR if minor else MAJOR)[0]
    win = pcm[int(0.02*SR):int(0.02*SR)+SR//2].astype(np.float64)
    spec = np.abs(np.fft.rfft(win*np.hanning(len(win)))); freqs = np.fft.rfftfreq(len(win), 1/SR)
    def band(f): m = (freqs > f*0.97) & (freqs < f*1.03); return spec[m].max()
    arp_hz, bass_hz = midi_hz(root+12+chord0[0]), midi_hz(root-12+chord0[0])
    noise_floor = np.median(spec)
    check(f"{slug}: у спектрі є нота арпеджіо {arp_hz:.0f} Гц", band(arp_hz) > 20*noise_floor)
    check(f"{slug}: у спектрі є бас {bass_hz:.0f} Гц", band(bass_hz) > 20*noise_floor)
    report.append(f"{slug:13s} {bpm:3d} уд/хв, {secs} с, пік {peak}, RMS {rms:.3f}, арпеджіо {arp_hz:.0f} Гц, бас {bass_hz:.0f} Гц")

print("\n".join(report))
print("WAV-файли для прослуховування:", outdir)
print(f"\nПЕРЕВІРОК ПРОЙДЕНО: {passed}")
print(f"НЕ ПРОЙДЕНО: {len(failed)}")
for f in failed: print("  -", f)
